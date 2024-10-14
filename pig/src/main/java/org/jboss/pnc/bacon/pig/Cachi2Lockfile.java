package org.jboss.pnc.bacon.pig;

import org.jboss.pnc.bacon.pig.impl.addons.prefetch.cachi2.Cachi2LockfileGenerator;
import org.jboss.pnc.bacon.pnc.common.ClientCreator;
import org.jboss.pnc.client.ArtifactClient;
import picocli.CommandLine;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "cachi2lockfile",
        description = "Generates a Cachi2 lock file for a given Maven repository ZIP file.")
public class Cachi2Lockfile implements Callable<Integer> {

    private static final ClientCreator<ArtifactClient> CREATOR = new ClientCreator<>(ArtifactClient::new);

    @CommandLine.Parameters(description = "Comma-separated paths to Maven repositories (ZIPs or directories)")
    private List<File> repositories = List.of();

    @Override
    public Integer call() throws Exception {
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("Maven repository location was not provided");
        }
        var generator = Cachi2LockfileGenerator.newInstance();
        for (var path : repositories) {
            if (!path.exists()) {
                throw new IllegalArgumentException(path + " does not exist");
            }
            generator.addMavenRepository(path.toPath());
        }
        generator.generate();
        return 0;
    }
}
