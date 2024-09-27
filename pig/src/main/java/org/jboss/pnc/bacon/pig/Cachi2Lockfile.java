package org.jboss.pnc.bacon.pig;

import org.jboss.pnc.bacon.pig.impl.addons.prefetch.cachi2.Cachi2LockfileGenerator;
import org.jboss.pnc.bacon.pnc.common.ClientCreator;
import org.jboss.pnc.client.ArtifactClient;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "cachi2lockfile",
        description = "Generates a Cachi2 lock file for a given Maven repository ZIP file.")
public class Cachi2Lockfile implements Callable<Integer> {

    private static final ClientCreator<ArtifactClient> CREATOR = new ClientCreator<>(ArtifactClient::new);

    @CommandLine.Parameters(description = "Path to a Maven repository ZIP or a directory")
    private File mavenRepo;

    @Override
    public Integer call() throws Exception {
        System.out.println("Cachi2 lockfile for " + mavenRepo);
        if (mavenRepo == null || !mavenRepo.exists()) {
            throw new IllegalArgumentException("Invalid Maven repository location " + mavenRepo);
        }
        Cachi2LockfileGenerator.newInstance().setMavenRepository(mavenRepo.toPath()).generate();
        return 0;
    }
}
