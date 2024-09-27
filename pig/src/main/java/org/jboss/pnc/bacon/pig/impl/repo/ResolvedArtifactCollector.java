package org.jboss.pnc.bacon.pig.impl.repo;

import io.quarkus.domino.RhVersionPattern;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.GAV;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects all the artifacts resolved by a Maven artifact resolver
 */
public class ResolvedArtifactCollector extends AbstractRepositoryListener {

    private final Map<GAV, ResolvedGav> resolvedArtifacts = new ConcurrentHashMap<>();

    @Override
    public void artifactResolved(RepositoryEvent event) {
        var a = event.getArtifact();
        var resolved = resolvedArtifacts
                .computeIfAbsent(new GAV(a.getGroupId(), a.getArtifactId(), a.getVersion()), ResolvedGav::new);
        resolved.addArtifact(a);
        if (ArtifactCoords.TYPE_JAR.equals(a.getExtension())) {
            if (a.getClassifier().isEmpty()) {
                resolved.setDefaultJarResolved();
            } else if (a.getClassifier().equals("sources")) {
                resolved.setFlag(ResolvedGav.SOURCES_RESOLVED);
            } else if (a.getClassifier().equals("javadoc")) {
                resolved.setFlag(ResolvedGav.JAVADOC_RESOLVED);
            }
        }
    }

    public Collection<ResolvedGav> getResolvedArtifacts() {
        return resolvedArtifacts.values();
    }

    public Collection<org.jboss.pnc.bacon.pig.impl.utils.GAV> getRedHatArtifacts() {
        Collection<org.jboss.pnc.bacon.pig.impl.utils.GAV> result = new ArrayList<>();
        for (var resolvedGav : getResolvedArtifacts()) {
            if (!RhVersionPattern.isRhVersion(resolvedGav.getGav().getVersion())) {
                continue;
            }
            for (var c : resolvedGav.getArtifacts()) {
                result.add(
                        new org.jboss.pnc.bacon.pig.impl.utils.GAV(
                                c.getGroupId(),
                                c.getArtifactId(),
                                c.getVersion(),
                                c.getExtension(),
                                c.getClassifier()));
            }
        }
        return result;
    }
}
