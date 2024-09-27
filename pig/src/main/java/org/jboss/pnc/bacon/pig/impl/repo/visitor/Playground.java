package org.jboss.pnc.bacon.pig.impl.repo.visitor;

import java.nio.file.Path;

public class Playground {

    public static void main(String[] args) throws Exception {

        VisitableArtifactRepository
                // .of(Path.of("/home/aloubyansky/Downloads/eap/jboss-eap-8.0.4.GA-CR1-maven-repository.zip"))
                .of(Path.of("/home/aloubyansky/Downloads/eap/jboss-eap-8.0.4.GA-maven-repository"))
                .visit(visit -> {
                    System.out.println(visit.getGav().toGapvc() + " " + visit.getChecksums());
                });
    }
}
