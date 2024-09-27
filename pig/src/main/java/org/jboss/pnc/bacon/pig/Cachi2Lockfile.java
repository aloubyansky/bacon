package org.jboss.pnc.bacon.pig;

import org.jboss.pnc.bacon.pig.impl.addons.prefetch.cachi2.Cachi2LockfileGenerator;
import picocli.CommandLine;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "cachi2lockfile",
        description = "Generates a Cachi2 lock file for a given Maven repository ZIP file.")
public class Cachi2Lockfile implements Callable<Integer> {

    @CommandLine.Parameters(description = "Comma-separated paths to Maven repositories (ZIPs or directories)")
    private List<File> repositories = List.of();

    @CommandLine.Option(
            names = "--output",
            description = "Target output file. If not provided, defaults to 'cachi2lockfile.yaml'")
    private File output;

    @Override
    public Integer call() {
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("Maven repository location was not provided");
        }
        var generator = Cachi2LockfileGenerator.newInstance();
        if (output != null) {
            generator.setOutputFile(output.toPath());
        }
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
