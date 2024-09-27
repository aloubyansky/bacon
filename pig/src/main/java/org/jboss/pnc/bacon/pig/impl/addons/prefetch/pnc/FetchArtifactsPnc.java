package org.jboss.pnc.bacon.pig.impl.addons.prefetch.pnc;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class FetchArtifactsPnc {

    static class Builder {

        private final Map<String, Collection<FetchArtifact>> artifacts = new ConcurrentHashMap<>();

        private Builder() {
        }

        public void add(String buildId, String artifactId, String target) {
            artifacts.computeIfAbsent(buildId, k -> new ConcurrentLinkedDeque<>())
                    .add(new FetchArtifact(artifactId, target));
        }

        public FetchArtifactsPnc build() {
            var result = new FetchArtifactsPnc();
            result.setBuilds(getBuilds());
            return result;
        }

        private Collection<FetchBuild> getBuilds() {
            if (artifacts.isEmpty()) {
                return List.of();
            }
            var builds = new ArrayList<FetchBuild>(artifacts.size());
            for (var buildEntry : artifacts.entrySet()) {
                var artifactList = new ArrayList<>(buildEntry.getValue());
                Collections.sort(artifactList);
                builds.add(new FetchBuild(buildEntry.getKey(), artifactList));
            }
            Collections.sort(builds);
            return builds;
        }
    }

    public static class FetchBuild implements Comparable<FetchBuild> {

        private String id;
        private Collection<FetchArtifact> artifacts;

        public FetchBuild() {
        }

        public FetchBuild(String id, Collection<FetchArtifact> artifacts) {
            this.id = Objects.requireNonNull(id);
            this.artifacts = Objects.requireNonNull(artifacts);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Collection<FetchArtifact> getArtifacts() {
            return artifacts;
        }

        public void setArtifacts(Collection<FetchArtifact> artifacts) {
            this.artifacts = artifacts;
        }

        @Override
        public int compareTo(FetchBuild o) {
            return id.compareTo(o.id);
        }
    }

    public static class FetchArtifact implements Comparable<FetchArtifact> {

        private String id;
        private String target;

        public FetchArtifact() {
        }

        public FetchArtifact(String id, String target) {
            this.id = Objects.requireNonNull(id);
            this.target = Objects.requireNonNull(target);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        @Override
        public int compareTo(FetchArtifact o) {
            final int i = id.compareTo(o.id);
            if (i != 0) {
                return i;
            }
            return target.compareTo(o.target);
        }
    }

    static Builder builder() {
        return new Builder();
    }

    private Collection<FetchBuild> builds;

    public Collection<FetchBuild> getBuilds() {
        return builds;
    }

    public void setBuilds(Collection<FetchBuild> builds) {
        this.builds = builds;
    }
}
