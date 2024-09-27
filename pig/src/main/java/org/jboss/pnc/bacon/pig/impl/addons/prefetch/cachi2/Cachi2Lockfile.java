package org.jboss.pnc.bacon.pig.impl.addons.prefetch.cachi2;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class Cachi2Lockfile {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class Cachi2Artifact {

        private String purl;
        private String target;
        private Map<String, String> hashes = new TreeMap<>();
        private Map<String, String> metadata = new TreeMap<>();

        public String getPurl() {
            return purl;
        }

        public void setPurl(String purl) {
            this.purl = purl;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public Map<String, String> getHashes() {
            return hashes;
        }

        public void setHashes(Map<String, String> hashes) {
            this.hashes = hashes;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    private Map<String, String> metadata = new TreeMap<>();
    private List<Cachi2Artifact> content = new ArrayList<>();

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<Cachi2Artifact> getContent() {
        return content;
    }

    public void addArtifact(Cachi2Artifact artifact) {
        content.add(artifact);
    }

    public void setContent(List<Cachi2Artifact> content) {
        this.content = content;
    }
}
