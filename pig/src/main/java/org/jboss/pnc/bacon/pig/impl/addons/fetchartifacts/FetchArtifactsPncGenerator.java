package org.jboss.pnc.bacon.pig.impl.addons.fetchartifacts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

public class FetchArtifactsPncGenerator extends AddOn {

    private static final Logger log = LoggerFactory.getLogger(FetchArtifactsPncGenerator.class);

    private static final String MAVEN_RESPOSITORY_DIR = "maven-repository/";

    private static ObjectMapper initYamlMapper() {
        return new ObjectMapper(new YAMLFactory()).enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public FetchArtifactsPncGenerator(
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
            log.info("DONE in " + (System.currentTimeMillis() - start));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("SAVING " + extrasPath);
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(extrasPath).resolve("fetch-artifacts-pnc.yaml"))) {
            initYamlMapper().writeValue(writer, builder.build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                .getAll(null, null, null, Optional.empty(), Optional.ofNullable(query.toString()));
        if (result.size() == 0) {
            log.error("FAILED to obtain info for " + gav.toGapvc() + " with query " + query);
        }
        for (var a : result.getAll()) {
            builder.add(a.getBuild().getId(), a.getId(), MAVEN_RESPOSITORY_DIR + a.getDeployPath());
        }
    }
}
