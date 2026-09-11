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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.BuildException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class ModelloTaskTest {

    @TempDir
    Path tempDir;

    @Test
    public void testMissingAllRequired() {
        ModelloTask task = new ModelloTask();
        BuildException exception = assertThrows(BuildException.class, task::execute);
        assertEquals("version, outputDirectory, <model>, and <goal> are required.", exception.getMessage());
    }

    @Test
    public void testMissingVersion() {
        ModelloTask task = new ModelloTask();
        task.setOutputDirectory(tempDir.toFile());

        ModelloTask.ModelElement modelElement = new ModelloTask.ModelElement();
        modelElement.setFile(new File("dummy.mdo"));
        task.addConfiguredModel(modelElement);

        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        goalElement.setName("java");
        task.addConfiguredGoal(goalElement);

        BuildException exception = assertThrows(BuildException.class, task::execute);
        assertEquals("version, outputDirectory, <model>, and <goal> are required.", exception.getMessage());
    }

    @Test
    public void testMissingOutputDirectory() {
        ModelloTask task = new ModelloTask();
        task.setVersion("1.0.0");

        ModelloTask.ModelElement modelElement = new ModelloTask.ModelElement();
        modelElement.setFile(new File("dummy.mdo"));
        task.addConfiguredModel(modelElement);

        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        goalElement.setName("java");
        task.addConfiguredGoal(goalElement);

        BuildException exception = assertThrows(BuildException.class, task::execute);
        assertEquals("version, outputDirectory, <model>, and <goal> are required.", exception.getMessage());
    }

    @Test
    public void testMissingModel() {
        ModelloTask task = new ModelloTask();
        task.setVersion("1.0.0");
        task.setOutputDirectory(tempDir.toFile());

        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        goalElement.setName("java");
        task.addConfiguredGoal(goalElement);

        BuildException exception = assertThrows(BuildException.class, task::execute);
        assertEquals("version, outputDirectory, <model>, and <goal> are required.", exception.getMessage());
    }

    @Test
    public void testMissingGoal() {
        ModelloTask task = new ModelloTask();
        task.setVersion("1.0.0");
        task.setOutputDirectory(tempDir.toFile());

        ModelloTask.ModelElement modelElement = new ModelloTask.ModelElement();
        modelElement.setFile(new File("dummy.mdo"));
        task.addConfiguredModel(modelElement);

        BuildException exception = assertThrows(BuildException.class, task::execute);
        assertEquals("version, outputDirectory, <model>, and <goal> are required.", exception.getMessage());
    }

    @Test
    public void testSuccessfulGeneration() throws IOException {
        // Write a minimal Modello model file
        File modelFile = tempDir.resolve("test.mdo").toFile();
        try (FileWriter writer = new FileWriter(modelFile)) {
            writer.write("<model xmlns=\"http://codehaus-plexus.github.io/MODELLO/2.0.0\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"http://codehaus-plexus.github.io/MODELLO/2.0.0 "
                    + "https://codehaus-plexus.github.io/modello/xsd/modello-2.0.0.xsd\">\n"
                    + "  <id>test-model</id>\n"
                    + "  <name>TestModel</name>\n"
                    + "  <defaults>\n"
                    + "    <default>\n"
                    + "      <key>package</key>\n"
                    + "      <value>com.example.test</value>\n"
                    + "    </default>\n"
                    + "  </defaults>\n"
                    + "  <classes>\n"
                    + "    <class rootElement=\"true\">\n"
                    + "      <name>TestClass</name>\n"
                    + "      <version>1.0.0+</version>\n"
                    + "      <fields>\n"
                    + "        <field>\n"
                    + "          <name>id</name>\n"
                    + "          <version>1.0.0+</version>\n"
                    + "          <type>String</type>\n"
                    + "        </field>\n"
                    + "      </fields>\n"
                    + "    </class>\n"
                    + "  </classes>\n"
                    + "</model>\n");
        }

        ModelloTask task = new ModelloTask();
        task.setVersion("1.0.0");
        task.setOutputDirectory(tempDir.toFile());

        ModelloTask.ModelElement modelElement = new ModelloTask.ModelElement();
        modelElement.setFile(modelFile);
        task.addConfiguredModel(modelElement);

        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        goalElement.setName("java");
        task.addConfiguredGoal(goalElement);

        // Execute task
        task.execute();

        // Verify that the generated file exists
        Path generatedJavaFile = tempDir.resolve("com/example/test/TestClass.java");
        assertTrue(Files.exists(generatedJavaFile), "Generated Java file should exist: " + generatedJavaFile);
    }

    @Test
    public void testNullModelFile() {
        ModelloTask task = new ModelloTask();
        ModelloTask.ModelElement modelElement = new ModelloTask.ModelElement();
        BuildException exception = assertThrows(BuildException.class, () -> task.addConfiguredModel(modelElement));
        assertEquals("The 'file' attribute is required for <model>.", exception.getMessage());
    }

    @Test
    public void testNullGoalName() {
        ModelloTask task = new ModelloTask();
        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        BuildException exception = assertThrows(BuildException.class, () -> task.addConfiguredGoal(goalElement));
        assertEquals("The 'name' attribute is required for <goal>.", exception.getMessage());
    }

    @Test
    public void testEmptyGoalName() {
        ModelloTask task = new ModelloTask();
        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        goalElement.setName("  ");
        BuildException exception = assertThrows(BuildException.class, () -> task.addConfiguredGoal(goalElement));
        assertEquals("The 'name' attribute is required for <goal>.", exception.getMessage());
    }

    @Test
    public void testNullTemplateName() {
        ModelloTask task = new ModelloTask();
        ModelloTask.NameElement templateElement = new ModelloTask.NameElement();
        BuildException exception =
                assertThrows(BuildException.class, () -> task.addConfiguredTemplate(templateElement));
        assertEquals("The 'name' attribute is required for <template>.", exception.getMessage());
    }

    @Test
    public void testNullParamName() {
        ModelloTask task = new ModelloTask();
        ModelloTask.ParamElement paramElement = new ModelloTask.ParamElement();
        BuildException exception = assertThrows(BuildException.class, () -> task.addConfiguredParam(paramElement));
        assertEquals("The 'name' attribute is required for <param>.", exception.getMessage());
    }

    @Test
    public void testOutputDirectoryCreation() throws IOException {
        File modelFile = tempDir.resolve("test-mkdir.mdo").toFile();
        try (FileWriter writer = new FileWriter(modelFile)) {
            writer.write("<model xmlns=\"http://codehaus-plexus.github.io/MODELLO/2.0.0\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"http://codehaus-plexus.github.io/MODELLO/2.0.0 "
                    + "https://codehaus-plexus.github.io/modello/xsd/modello-2.0.0.xsd\">\n"
                    + "  <id>test-model</id>\n"
                    + "  <name>TestModel</name>\n"
                    + "  <defaults>\n"
                    + "    <default>\n"
                    + "      <key>package</key>\n"
                    + "      <value>com.example.test</value>\n"
                    + "    </default>\n"
                    + "  </defaults>\n"
                    + "  <classes>\n"
                    + "    <class rootElement=\"true\">\n"
                    + "      <name>MkdirClass</name>\n"
                    + "      <version>1.0.0+</version>\n"
                    + "    </class>\n"
                    + "  </classes>\n"
                    + "</model>\n");
        }

        File nonExistentDir = tempDir.resolve("non-existent-sub/output").toFile();
        assertFalse(nonExistentDir.exists());

        ModelloTask task = new ModelloTask();
        task.setVersion("1.0.0");
        task.setOutputDirectory(nonExistentDir);

        ModelloTask.ModelElement modelElement = new ModelloTask.ModelElement();
        modelElement.setFile(modelFile);
        task.addConfiguredModel(modelElement);

        ModelloTask.NameElement goalElement = new ModelloTask.NameElement();
        goalElement.setName("java");
        task.addConfiguredGoal(goalElement);

        task.execute();
        assertTrue(nonExistentDir.exists());
    }

    @Test
    public void testDomAsXpp3DefaultAndFalse() throws IOException {
        File modelFile = tempDir.resolve("test-dom.mdo").toFile();
        try (FileWriter writer = new FileWriter(modelFile)) {
            writer.write("<model xmlns=\"http://codehaus-plexus.github.io/MODELLO/2.0.0\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"http://codehaus-plexus.github.io/MODELLO/2.0.0 "
                    + "https://codehaus-plexus.github.io/modello/xsd/modello-2.0.0.xsd\">\n"
                    + "  <id>test-dom</id>\n"
                    + "  <name>TestDom</name>\n"
                    + "  <defaults>\n"
                    + "    <default>\n"
                    + "      <key>package</key>\n"
                    + "      <value>com.example.dom</value>\n"
                    + "    </default>\n"
                    + "  </defaults>\n"
                    + "  <classes>\n"
                    + "    <class rootElement=\"true\">\n"
                    + "      <name>DomClass</name>\n"
                    + "      <version>1.0.0+</version>\n"
                    + "      <fields>\n"
                    + "        <field>\n"
                    + "          <name>config</name>\n"
                    + "          <version>1.0.0+</version>\n"
                    + "          <type>DOM</type>\n"
                    + "        </field>\n"
                    + "      </fields>\n"
                    + "    </class>\n"
                    + "  </classes>\n"
                    + "</model>\n");
        }

        Path defaultOut = tempDir.resolve("default-out");
        ModelloTask taskDefault = new ModelloTask();
        taskDefault.setVersion("1.0.0");
        taskDefault.setOutputDirectory(defaultOut.toFile());
        ModelloTask.ModelElement m1 = new ModelloTask.ModelElement();
        m1.setFile(modelFile);
        taskDefault.addConfiguredModel(m1);
        ModelloTask.NameElement g1 = new ModelloTask.NameElement();
        g1.setName("java");
        taskDefault.addConfiguredGoal(g1);
        ModelloTask.NameElement g1Writer = new ModelloTask.NameElement();
        g1Writer.setName("xpp3-writer");
        taskDefault.addConfiguredGoal(g1Writer);
        taskDefault.execute();

        Path defaultWriter = defaultOut.resolve("com/example/dom/io/xpp3/TestDomXpp3Writer.java");
        assertTrue(Files.exists(defaultWriter));
        String defaultContent = new String(Files.readAllBytes(defaultWriter));
        assertTrue(defaultContent.contains("import org.codehaus.plexus.util.xml.Xpp3Dom;"));
        assertTrue(defaultContent.contains("((Xpp3Dom) domClass.getConfig())"));
        assertFalse(defaultContent.contains("org.w3c.dom.Element"));

        Path falseOut = tempDir.resolve("false-out");
        ModelloTask taskFalse = new ModelloTask();
        taskFalse.setVersion("1.0.0");
        taskFalse.setOutputDirectory(falseOut.toFile());
        taskFalse.setDomAsXpp3(false);
        ModelloTask.ModelElement m2 = new ModelloTask.ModelElement();
        m2.setFile(modelFile);
        taskFalse.addConfiguredModel(m2);
        ModelloTask.NameElement g2 = new ModelloTask.NameElement();
        g2.setName("java");
        taskFalse.addConfiguredGoal(g2);
        ModelloTask.NameElement g2Writer = new ModelloTask.NameElement();
        g2Writer.setName("xpp3-writer");
        taskFalse.addConfiguredGoal(g2Writer);
        taskFalse.execute();

        Path falseWriter = falseOut.resolve("com/example/dom/io/xpp3/TestDomXpp3Writer.java");
        assertTrue(Files.exists(falseWriter));
        String falseContent = new String(Files.readAllBytes(falseWriter));
        assertTrue(falseContent.contains("writeDom( (org.w3c.dom.Element)"));
        assertFalse(falseContent.contains("Xpp3Dom"));
    }

    @Test
    public void testPluralExceptions() throws IOException {
        File modelFile = tempDir.resolve("test-plural.mdo").toFile();
        try (FileWriter writer = new FileWriter(modelFile)) {
            writer.write(
                    "<model xmlns=\"http://codehaus-plexus.github.io/MODELLO/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                            + "  xsi:schemaLocation=\"http://codehaus-plexus.github.io/MODELLO/1.0.0 https://codehaus-plexus.github.io/modello/xsd/modello-1.0.0.xsd\">\n"
                            + "  <id>test-plural</id>\n"
                            + "  <name>TestPlural</name>\n"
                            + "  <defaults>\n"
                            + "    <default>\n"
                            + "      <key>package</key>\n"
                            + "      <value>com.example.test</value>\n"
                            + "    </default>\n"
                            + "  </defaults>\n"
                            + "  <classes>\n"
                            + "    <class rootElement=\"true\">\n"
                            + "      <name>Server</name>\n"
                            + "      <version>1.0.0+</version>\n"
                            + "      <fields>\n"
                            + "        <field>\n"
                            + "          <name>aliases</name>\n"
                            + "          <version>1.0.0+</version>\n"
                            + "          <association>\n"
                            + "            <type>String</type>\n"
                            + "            <multiplicity>*</multiplicity>\n"
                            + "          </association>\n"
                            + "        </field>\n"
                            + "      </fields>\n"
                            + "    </class>\n"
                            + "  </classes>\n"
                            + "</model>\n");
        }

        Path outDir = tempDir.resolve("plural-out");
        ModelloTask task = new ModelloTask();
        task.setVersion("1.0.0");
        task.setOutputDirectory(outDir.toFile());

        ModelloTask.ModelElement m = new ModelloTask.ModelElement();
        m.setFile(modelFile);
        task.addConfiguredModel(m);

        ModelloTask.NameElement g = new ModelloTask.NameElement();
        g.setName("java");
        task.addConfiguredGoal(g);

        ModelloTask.ParamElement pe = new ModelloTask.ParamElement();
        pe.setName("aliases");
        pe.setValue("alias");
        task.addConfiguredPluralException(pe);

        task.execute();

        Path generatedJava = outDir.resolve("com/example/test/Server.java");
        assertTrue(Files.exists(generatedJava));
        String content = new String(Files.readAllBytes(generatedJava));
        assertTrue(content.contains("addAlias("));
        assertFalse(content.contains("addAliase"));
    }
}
