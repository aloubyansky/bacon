package org.jboss.pnc.bacon.pig.impl.addons.prefetch.cachi2;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.fs.util.ZipUtils;
import org.jboss.pnc.bacon.pig.impl.repo.RepoDescriptor;
import org.jboss.pnc.bacon.pig.impl.utils.GAV;
import org.jboss.pnc.bacon.pnc.client.PncClientHelper;
import org.jboss.pnc.client.ArtifactClient;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.pnc.bacon.pig.impl.addons.prefetch.YamlUtil.initYamlMapper;

public class Cachi2LockfileGenerator {

    private static final Logger log = LoggerFactory.getLogger(Cachi2LockfileGenerator.class);

    private static final String MAVEN_RESPOSITORY_DIR = "maven-repository/";
    private static final String FORMAT_BASE = "[%s/%s %.1f%%] ";
    private static final String logPrefix = "Cachi2 lockfile added ";
    public static final String DEFAULT_OUTPUT_FILENAME = "cachi2lockfile.yaml";
    // public static final String DEFAULT_REPOSITORY_URL =
    // "https://indy.psi.redhat.com/api/content/maven/group/static/";
    public static final String DEFAULT_REPOSITORY_URL = "https://indy.corp.redhat.com/api/content/maven/remote/central";

    public static Cachi2LockfileGenerator newInstance() {
        return new Cachi2LockfileGenerator();
    }

    private Path outputDir;
    private String outputFileName;
    private Path outputFile;
    private Collection<GAV> artifacts;
    private Path mavenRepo;
    private String defaultRepositoryUrl = DEFAULT_REPOSITORY_URL;
    private Collection<GAV> artifactsMissingBuilds = new ConcurrentLinkedDeque<>();
    private Collection<GAV> artifactsMissingIds = new ConcurrentLinkedDeque<>();

    /**
     * Set the output directory. If not set, the default value will be the current user directory. The output file will
     * be created in the output directory with the name configured with {@link #setOutputFileName(String)} or its
     * default value {@link #DEFAULT_OUTPUT_FILENAME} unless the target output file was configured with
     * {@link #setOutputFile(Path)}, in which case the output directory value and the file name will be ignored.
     *
     * @param outputDir output directory
     * @return this instance
     */
    public Cachi2LockfileGenerator setOutputDirectory(Path outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    /**
     * Set the output file name. The output file will be created in the output directory with the name configured with
     * the configured file name or its default value {@link #DEFAULT_OUTPUT_FILENAME} unless the target output file was
     * configured with {@link #setOutputFile(Path)}, in which case the output directory value and the file name will be
     * ignored.
     *
     * @param outputFileName output file name
     * @return this instance
     */
    public Cachi2LockfileGenerator setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
        return this;
    }

    /**
     * Sets the output file. If an output file is configured then values set with {@link #setOutputDirectory(Path)} and
     * {@link #setOutputFileName(String)} will be ignored.
     *
     * @param outputFile output file
     * @return this instance
     */
    public Cachi2LockfileGenerator setOutputFile(Path outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    /**
     * A collection of artifacts to generate the lock file for. If a collection of artifacts is configured with this
     * method, a value set with {@link #setMavenRepository(Path)} will be ignored.
     *
     * @param artifacts collection of artifacts
     * @return this instance
     */
    public Cachi2LockfileGenerator setArtifacts(Collection<GAV> artifacts) {
        this.artifacts = artifacts;
        return this;
    }

    /**
     * Path to a local Maven repository to generate a lock file for. The path can point to a directory or a ZIP file. In
     * case {@link #setArtifacts(Collection)} is also called, the value of the Maven repository path will be ignored.
     *
     * @param mavenRepo path to a local Maven repository
     * @return this instance
     */
    public Cachi2LockfileGenerator setMavenRepository(Path mavenRepo) {
        this.mavenRepo = mavenRepo;
        return this;
    }

    /**
     * Sets the default Maven repository URL, which will be used in case PNC information is not available for an
     * artifact.
     *
     * @param defaultMavenRepositoryUrl default Maven repository URL for artifacts
     * @return this instance
     */
    public Cachi2LockfileGenerator setDefaultMavenRepositoryUrl(String defaultMavenRepositoryUrl) {
        this.defaultRepositoryUrl = defaultMavenRepositoryUrl;
        return this;
    }

    private Path getOutputFile() {
        if (outputFile != null) {
            return outputFile;
        }

        return (outputDir == null ? Path.of("") : outputDir)
                .resolve(outputFileName == null ? DEFAULT_OUTPUT_FILENAME : outputFileName);
    }

    private Collection<GAV> getArtifacts() {
        if (artifacts != null) {
            return artifacts;
        }
        if (mavenRepo == null) {
            throw new IllegalArgumentException("Neither artifacts nor Maven repository location was configured");
        }
        return getGavs(mavenRepo);
    }

    private static Collection<GAV> getGavs(Path repoPath) {
        Collection<GAV> gavCollection = null;
        try (FileSystem fs = ZipUtils.newZip(repoPath)) {
            for (Path root : fs.getRootDirectories()) {
                if (gavCollection == null) {
                    gavCollection = RepoDescriptor.listArtifacts(root);
                } else {
                    gavCollection = new ArrayList<>(gavCollection);
                    gavCollection.addAll(RepoDescriptor.listArtifacts(root));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return gavCollection;
    }

    public void generate() {
        generateLockfile(getArtifacts());
    }

    private void generateLockfile(Collection<GAV> artifacts) {

        log.info("Generating Cachi2 lockfile");
        Cachi2Lockfile lockfile = new Cachi2Lockfile();

        var start = System.currentTimeMillis();
        final Phaser phaser = new Phaser(1);
        final Collection<Exception> errors = new ConcurrentLinkedDeque<>();
        final Collection<Cachi2Lockfile.Cachi2Artifact> cachi2Artifacts = new ConcurrentLinkedDeque<>();
        try (ArtifactClient artifactClient = new ArtifactClient(PncClientHelper.getPncConfiguration(true))) {
            final AtomicInteger artifactCounter = new AtomicInteger();
            for (var a : artifacts) {
                phaser.register();
                CompletableFuture.runAsync(() -> {
                    try {
                        Cachi2Lockfile.Cachi2Artifact ca = new Cachi2Lockfile.Cachi2Artifact();
                        ca.setTarget(MAVEN_RESPOSITORY_DIR + a.toUri());
                        addArtifact(artifactClient, a, ca);
                        cachi2Artifacts.add(ca);
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        phaser.arriveAndDeregister();
                    }
                    logArtifact(artifacts, a, artifactCounter);
                });
            }
            phaser.arriveAndAwaitAdvance();
        }
        assertNoErrors(errors);

        var arr = cachi2Artifacts.toArray(new Cachi2Lockfile.Cachi2Artifact[0]);
        Arrays.sort(arr, Comparator.comparing(Cachi2Lockfile.Cachi2Artifact::getPurl));
        lockfile.setContent(List.of(arr));

        Path lockfileYaml = getOutputFile();
        try (BufferedWriter writer = Files.newBufferedWriter(lockfileYaml)) {
            initYamlMapper().writeValue(writer, lockfile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Generated Cachi2 lockfile {} in {}ms", lockfileYaml, System.currentTimeMillis() - start);
    }

    private void logArtifact(Collection<GAV> artifacts, GAV a, AtomicInteger artifactCounter) {
        var sb = new StringBuilder(180);
        var formatter = new Formatter(sb);
        var artifactIndex = artifactCounter.incrementAndGet();
        final double percents = ((double) artifactIndex * 100) / artifacts.size();
        formatter.format(FORMAT_BASE, artifactIndex, artifacts.size(), percents);
        sb.append(logPrefix).append(a.toGapvc());
        log.info(sb.toString());
    }

    private void addArtifact(ArtifactClient artifactClient, GAV artifact, Cachi2Lockfile.Cachi2Artifact cachi2Artifact)
            throws RemoteResourceException {
        var query = new StringBuilder();
        query.append("identifier==")
                .append(artifact.getGroupId())
                .append(":")
                .append(artifact.getArtifactId())
                .append(":")
                .append(artifact.getPackaging())
                .append(":")
                .append(artifact.getVersion());
        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            query.append(":").append(artifact.getClassifier());
        }

        final RemoteCollection<org.jboss.pnc.dto.Artifact> result = artifactClient
                .getAll(null, null, null, Optional.empty(), Optional.of(query.toString()));
        if (result.size() == 0) {
            log.warn("PNC did not recognize {}", artifact);
            addNonPncArtifact(artifact, cachi2Artifact);
            return;
        }
        final Map<String, String> metadata = cachi2Artifact.getMetadata();
        final Map<String, String> hashes = cachi2Artifact.getHashes();
        metadata.put("pnc_type", "artifact");
        for (var a : result.getAll()) {
            cachi2Artifact.setPurl(getPurl(artifact, getRepositoryUrl(a)).toString());
            metadata.put("pnc_artifact_id", a.getId());
            if (a.getBuild() != null) {
                metadata.put("pnc_build_id", a.getBuild().getId());
            } else {
                log.warn("PNC build information is missing for {}", artifact);
            }
            if (a.getMd5() != null) {
                hashes.put("md5", a.getMd5());
            }
            if (a.getSha1() != null) {
                hashes.put("sha1", a.getSha1());
            }
            if (a.getSha256() != null) {
                hashes.put("sha256", a.getSha256());
            }
        }
    }

    private void addNonPncArtifact(GAV artifact, Cachi2Lockfile.Cachi2Artifact cachi2Artifact) {
        cachi2Artifact.setPurl(getPurl(artifact, defaultRepositoryUrl).toString());
    }

    private static String getRepositoryUrl(Artifact artifact) {
        var publicUrl = artifact.getPublicUrl();
        if (publicUrl == null) {
            return null;
        }
        final String repoPath = artifact.getTargetRepository() == null ? null
                : artifact.getTargetRepository().getRepositoryPath();
        if (repoPath != null) {
            var i = publicUrl.indexOf(repoPath);
            if (i > 0) {
                return publicUrl.substring(0, i + repoPath.length());
            }
        }
        return null;
    }

    private static PackageURL getPurl(GAV artifact, String repositoryUrl) {
        TreeMap<String, String> qualifiers = new TreeMap<>();
        qualifiers.put("type", artifact.getPackaging());
        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            qualifiers.put("classifier", artifact.getClassifier());
        }
        if (repositoryUrl != null) {
            qualifiers.put("repository_url", repositoryUrl);
        }
        try {
            return new PackageURL(
                    "maven",
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    qualifiers,
                    null);
        } catch (MalformedPackageURLException e) {
            throw new RuntimeException("Failed to generate Purl for " + artifact, e);
        }
    }

    private static void assertNoErrors(Collection<Exception> errors) {
        if (!errors.isEmpty()) {
            var sb = new StringBuilder("The following errors were encountered while querying for artifact info:");
            log.error(sb.toString());
            var i = 1;
            for (var error : errors) {
                var prefix = i++ + ")";
                log.error(prefix, error);
                sb.append(System.lineSeparator()).append(prefix).append(" ").append(error.getLocalizedMessage());
                for (var e : error.getStackTrace()) {
                    sb.append(System.lineSeparator());
                    for (int j = 0; j < prefix.length(); ++j) {
                        sb.append(" ");
                    }
                    sb.append("at ").append(e);
                    if (e.getClassName().contains(Cachi2LockfileGenerator.class.getName())) {
                        sb.append(System.lineSeparator());
                        for (int j = 0; j < prefix.length(); ++j) {
                            sb.append(" ");
                        }
                        sb.append("...");
                        break;
                    }
                }
            }
            throw new RuntimeException(sb.toString());
        }
    }
}
