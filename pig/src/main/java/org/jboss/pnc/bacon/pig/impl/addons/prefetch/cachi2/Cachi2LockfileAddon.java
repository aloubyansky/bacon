package org.jboss.pnc.bacon.pig.impl.addons.prefetch.cachi2;

import org.jboss.pnc.bacon.pig.impl.PigContext;
import org.jboss.pnc.bacon.pig.impl.addons.AddOn;
import org.jboss.pnc.bacon.pig.impl.config.PigConfiguration;
import org.jboss.pnc.bacon.pig.impl.pnc.PncBuild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Cachi2LockfileAddon extends AddOn {

    private static final Logger log = LoggerFactory.getLogger(Cachi2LockfileAddon.class);

    /**
     * Output file name
     */
    private static final String PARAM_FILENAME = "filename";

    /**
     * Default repository URL for artifacts not recognized by PNC
     */
    private static final String PARAM_DEFAULT_REPO_URL = "default-repository-url";

    public Cachi2LockfileAddon(
            PigConfiguration pigConfiguration,
            Map<String, PncBuild> builds,
            String releasePath,
            String extrasPath) {
        super(pigConfiguration, builds, releasePath, extrasPath);
    }

    @Override
    public String getName() {
        return "cachi2LockFile";
    }

    @Override
    public void trigger() {
        var repoPath = PigContext.get().getRepositoryData().getRepositoryPath();
        if (!Files.exists(repoPath)) {
            throw new IllegalArgumentException(repoPath + " does not exist");
        }

        Cachi2LockfileGenerator cachi2Lockfile = Cachi2LockfileGenerator.newInstance()
                .setOutputDirectory(Path.of(extrasPath))
                .addMavenRepository(repoPath);

        setParams(cachi2Lockfile);

        cachi2Lockfile.generate();
    }

    private void setParams(Cachi2LockfileGenerator cachi2Lockfile) {
        var params = getAddOnConfiguration();
        if (params != null) {
            setFilename(cachi2Lockfile, params);
            setDefaultRepositoryUrl(cachi2Lockfile, params);
        }
    }

    private void setFilename(Cachi2LockfileGenerator cachi2Lockfile, Map<String, ?> params) {
        var value = params.get(PARAM_FILENAME);
        if (value != null) {
            cachi2Lockfile.setOutputFileName(value.toString());
        }
    }

    private void setDefaultRepositoryUrl(Cachi2LockfileGenerator cachi2Lockfile, Map<String, ?> params) {
        var value = params.get(PARAM_DEFAULT_REPO_URL);
        if (value != null) {
            cachi2Lockfile.setDefaultMavenRepositoryUrl(value.toString());
        }
    }
}
