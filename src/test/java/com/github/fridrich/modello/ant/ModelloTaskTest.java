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
            writer.write(
                "<model xmlns=\"http://codehaus-plexus.github.io/MODELLO/2.0.0\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://codehaus-plexus.github.io/MODELLO/2.0.0 " +
                "https://codehaus-plexus.github.io/modello/xsd/modello-2.0.0.xsd\">\n" +
                "  <id>test-model</id>\n" +
                "  <name>TestModel</name>\n" +
                "  <defaults>\n" +
                "    <default>\n" +
                "      <key>package</key>\n" +
                "      <value>com.example.test</value>\n" +
                "    </default>\n" +
                "  </defaults>\n" +
                "  <classes>\n" +
                "    <class rootElement=\"true\">\n" +
                "      <name>TestClass</name>\n" +
                "      <version>1.0.0+</version>\n" +
                "      <fields>\n" +
                "        <field>\n" +
                "          <name>id</name>\n" +
                "          <version>1.0.0+</version>\n" +
                "          <type>String</type>\n" +
                "        </field>\n" +
                "      </fields>\n" +
                "    </class>\n" +
                "  </classes>\n" +
                "</model>\n"
            );
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
}
