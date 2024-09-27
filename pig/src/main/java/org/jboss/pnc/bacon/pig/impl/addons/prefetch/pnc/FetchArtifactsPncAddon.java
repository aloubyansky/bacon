package org.jboss.pnc.bacon.pig.impl.addons.prefetch.pnc;

import io.quarkus.fs.util.ZipUtils;
import org.jboss.pnc.bacon.pig.impl.PigContext;
import org.jboss.pnc.bacon.pig.impl.addons.AddOn;
import org.jboss.pnc.bacon.pig.impl.config.PigConfiguration;
import org.jboss.pnc.bacon.pig.impl.pnc.PncBuild;
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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Phaser;

import static org.jboss.pnc.bacon.pig.impl.addons.prefetch.YamlUtil.initYamlMapper;

public class FetchArtifactsPncAddon extends AddOn {

    private static final Logger log = LoggerFactory.getLogger(FetchArtifactsPncAddon.class);

    private static final String MAVEN_RESPOSITORY_DIR = "maven-repository/";

    public FetchArtifactsPncAddon(
            PigConfiguration pigConfiguration,
            Map<String, PncBuild> builds,
            String releasePath,
            String extrasPath) {
        super(pigConfiguration, builds, releasePath, extrasPath);
    }

    @Override
    public String getName() {
        return "fetchArtifactsPncYaml";
    }

    @Override
    public void trigger() {
        var start = System.currentTimeMillis();
        var repoPath = PigContext.get().getRepositoryData().getRepositoryPath();
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException(repoPath + " does not exist");
        }

        final FetchArtifactsPnc.Builder builder = FetchArtifactsPnc.builder();
        try (FileSystem fs = ZipUtils.newZip(repoPath);
                ArtifactClient artifactClient = new ArtifactClient(PncClientHelper.getPncConfiguration(true))) {
            final Phaser phaser = new Phaser(1);
            final Collection<Exception> errors = new ConcurrentLinkedDeque<>();
            for (Path root : fs.getRootDirectories()) {
                for (var gav : RepoDescriptor.listArtifacts(root)) {
                    log.info(gav.toGapvc());
                    phaser.register();
                    CompletableFuture.runAsync(() -> {
                        try {
                            addArtifact(artifactClient, gav, builder);
                        } catch (Exception e) {
                            errors.add(e);
                        } finally {
                            phaser.arriveAndDeregister();
                        }
                    });
                }
            }

            phaser.arriveAndAwaitAdvance();
            assertNoErrors(errors);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Path targetFile = Path.of(extrasPath).resolve("fetch-artifacts-pnc.yaml");
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile)) {
            initYamlMapper().writeValue(writer, builder.build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Generated " + targetFile + " in " + (System.currentTimeMillis() - start) + "ms");
    }

    private void assertNoErrors(Collection<Exception> errors) {
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
                    if (e.getClassName().contains("org.jboss.pnc.bacon.pig.impl.addons")) {
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

    private static void addArtifact(ArtifactClient artifactClient, GAV gav, FetchArtifactsPnc.Builder builder)
            throws RemoteResourceException {
        var query = new StringBuilder();
        query.append("identifier==")
                .append(gav.getGroupId())
                .append(":")
                .append(gav.getArtifactId())
                .append(":")
                .append(gav.getPackaging())
                .append(":")
                .append(gav.getVersion());
        if (gav.getClassifier() != null) {
            query.append(":").append(gav.getClassifier());
        }

        final RemoteCollection<Artifact> result = artifactClient
                .getAll(null, null, null, Optional.empty(), Optional.of(query.toString()));
        if (result.size() == 0) {
            throw new RuntimeException(
                    "Failed to obtain build information for " + gav.toGapvc() + " with query " + query);
        }
        for (var a : result.getAll()) {
            if (a.getBuild() == null) {
                throw new RuntimeException("No build info for " + gav.toGapvc());
            }
            builder.add(a.getBuild().getId(), a.getId(), MAVEN_RESPOSITORY_DIR + a.getDeployPath());
        }
    }
}
