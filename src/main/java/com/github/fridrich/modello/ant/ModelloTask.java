/*
 * Copyright 2026 Fridrich Strba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.fridrich.modello.ant;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.codehaus.modello.Modello;
import org.codehaus.modello.ModelloParameterConstants;
import org.codehaus.plexus.util.xml.XmlStreamReader;

/*
<taskdef name="modello"
         classname="com.github.fridrich.modello.ant.ModelloTask"
         classpathref="maven.plugin.classpath" />

<target name="mdo" description="Generate sources from mdo files">
    <mkdir dir="${build.mdoOutputDir}"/>

    <modello version="4.1.0"
             outputDirectory="${build.mdoOutputDir}"
             velocityBasedir="${project.basedir}/../../src/mdo">

        <model file="src/main/mdo/maven.mdo" />

        <goal name="velocity" />
        <goal name="xdoc" />
        <goal name="xsd" />

        <template name="model.vm" />

        <param name="packageModelV4" value="org.apache.maven.api.model" />
        <param name="isMavenModel" value="true" />
    </modello>
</target>
*/

public class ModelloTask extends Task {
    private String version;
    private File velocityBasedir;
    private File outputDirectory;
    private String packageWithVersion = "false";
    private String javaSource = "8";

    private List<File> models = new ArrayList<>();
    private List<String> templates = new ArrayList<>();
    private List<String> goals = new ArrayList<>();
    private Map<String, String> velocityParams = new HashMap<>();

    // Attribute Setters
    public void setVersion(String version) {
        this.version = version;
    }

    public void setVelocityBasedir(File velocityBasedir) {
        this.velocityBasedir = velocityBasedir;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setPackageWithVersion(String packageWithVersion) {
        this.packageWithVersion = packageWithVersion;
    }

    public void setJavaSource(String javaSource) {
        this.javaSource = javaSource;
    }

    // Nested Elements Handlers
    public void addConfiguredModel(ModelElement m) {
        this.models.add(m.getFile());
    }

    public void addConfiguredTemplate(NameElement t) {
        this.templates.add(t.getName());
    }

    public void addConfiguredGoal(NameElement g) {
        this.goals.add(g.getName());
    }

    public void addConfiguredParam(ParamElement p) {
        this.velocityParams.put(p.getName(), p.getValue());
    }

    @Override
    public void execute() throws BuildException {
        if (version == null || outputDirectory == null || models.isEmpty() || goals.isEmpty()) {
            throw new BuildException("version, outputDirectory, <model>, and <goal> are required.");
        }

        try {
            Modello modello = new Modello();
            Map<String, Object> parameters = new HashMap<>();

            parameters.put(ModelloParameterConstants.OUTPUT_DIRECTORY, outputDirectory.getAbsolutePath());
            parameters.put(ModelloParameterConstants.VERSION, version);
            parameters.put(ModelloParameterConstants.PACKAGE_WITH_VERSION, packageWithVersion);
            parameters.put(ModelloParameterConstants.OUTPUT_JAVA_SOURCE, javaSource);
            parameters.put(ModelloParameterConstants.ENCODING, "utf-8");
            parameters.put(ModelloParameterConstants.DOM_AS_XPP3, "true");

            // Attach Velocity configs if provided
            if (velocityBasedir != null) {
                parameters.put("modello.velocity.basedir", velocityBasedir.getAbsolutePath());
                parameters.put("modello.velocity.templates", String.join(",", templates));
                parameters.put("modello.velocity.parameters", velocityParams);
            }

            for (File modelFile : models) {
                log("Generating sources for " + modelFile.getName());
                for (String goal : goals) {
                    try (XmlStreamReader reader = new XmlStreamReader(modelFile)) {
                        modello.generate(reader, goal, parameters);
                    }
                }
            }
        } catch (Exception e) {
            throw new BuildException("Modello generation failed: " + e.getMessage(), e);
        }
    }

    // Helper classes for Ant's nested XML elements
    public static class ModelElement {
        private File file;

        public void setFile(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }
    }

    public static class NameElement {
        private String name;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class ParamElement {
        private String name;
        private String value;

        public void setName(String name) {
            this.name = name;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
