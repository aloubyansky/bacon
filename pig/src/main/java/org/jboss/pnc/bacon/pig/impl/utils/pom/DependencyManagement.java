package org.jboss.pnc.bacon.pig.impl.utils.pom;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * @author Paul Gallagher, pgallagh@redhat <br>
 *         Date: 12/10/2018
 */
public class DependencyManagement {
    List<Dependency> dependencies = new ArrayList<>();

    @XmlElementWrapper
    @XmlElement(name = "dependency")
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void addDependency(Dependency dependency) {
        dependencies.add(dependency);
    }
}
