# Modello Ant Task

The `modello-ant-task` is an Apache Ant task that integrates [Codehaus Modello](https://codehaus-plexus.github.io/modello/) into Ant build workflows. It allows you to generate Java classes, XML serializers/deserializers, XML Schemas (XSD), documentation, and other outputs from Modello model description (`.mdo`) files.

---

## Installation & Definition

To use the task in your Ant build file, define it using either the individual task definition or the preferred standard `antlib` resource.

### Option 1: Using Antlib (Recommended)

```xml
<typedef resource="com/github/fridrich/modello/ant/antlib.xml"
         classpathref="modello.classpath" />
```

### Option 2: Using Taskdef

```xml
<taskdef name="modello"
         classname="com.github.fridrich.modello.ant.ModelloTask"
         classpathref="modello.classpath" />
```

*Note: `modello.classpath` must contain the `modello-ant-task` JAR, `modello-core`, and any runtime Modello generator plugins you wish to use.*

---

## Task Reference

The `<modello>` task supports several attributes and nested elements to configure code generation.

### Attributes

| Attribute                | Type      | Description                                                                           | Required |  Default  |
|:-------------------------|:----------|:--------------------------------------------------------------------------------------|:--------:|:---------:|
| **`version`**            | `String`  | The model version to generate (e.g., `1.0.0`).                                        | **Yes**  |     —     |
| **`outputDirectory`**    | `File`    | The directory where the generated files will be written.                              | **Yes**  |     —     |
| **`javaSource`**         | `String`  | Target Java version for generated source code.                                        |    No    |    `8`    |
| **`encoding`**           | `String`  | Character encoding for generated files.                                               |    No    | `"utf-8"` |
| **`packageWithVersion`** | `boolean` | Whether to append the version to the package name (`"true"` / `"false"`).             |    No    |  `false`  |
| **`domAsXpp3`**          | `boolean` | Whether to generate DOM content as Xpp3Dom (`"true"`) or W3C DOM Element (`"false"`). |    No    |  `true`   |
| **`velocityBasedir`**    | `File`    | Base directory of template files (required only for Velocity-based goals).            |    No    |     —     |

### Nested Elements

#### `<model>`

Specifies the Modello model description file to read. At least one `<model>` element is required.
* **`file`** (`File`, Required): Path to the `.mdo` model file.

```xml
<model file="src/main/mdo/maven.mdo" />
```

#### `<goal>`

Specifies the Modello generator target to run. At least one `<goal>` element is required.
* **`name`** (`String`, Required): The generator plugin name (e.g., `java`, `xdoc`, `xsd`, `xpp3-reader`, `xpp3-writer`, `velocity`).

```xml
<goal name="java" />
```

#### `<template>`

Specifies a custom template file when running the Velocity-based generator (`velocity` goal).
* **`name`** (`String`, Required): Name or path of the template file relative to `velocityBasedir`.

```xml
<template name="model.vm" />
```

#### `<param>`

Specifies custom velocity parameters passed to the generator.
* **`name`** (`String`, Required): Parameter name.
* **`value`** (`String`, Required): Parameter value.

```xml
<param name="packageModelV4" value="org.example.model" />
```

---

## Examples

### Example 1: Standard Code Generation (Java & XSD)

This example generates Java sources and XML Schema definitions (XSD) from a `model.mdo` file into the target folder `target/generated-sources`.

```xml
<target name="generate-model">
    <mkdir dir="${basedir}/target/generated-sources" />

    <modello version="1.0.0"
             outputDirectory="${basedir}/target/generated-sources"
             javaSource="11">

        <!-- Path to the Modello model -->
        <model file="src/main/mdo/model.mdo" />

        <!-- Goals to execute -->
        <goal name="java" />
        <goal name="xsd" />
        <goal name="xpp3-reader" />
        <goal name="xpp3-writer" />
    </modello>
</target>
```

### Example 2: Velocity-Based Generation

This example demonstrates how to use the Velocity generator plugin to create files using a custom `.vm` template.

```xml
<target name="generate-velocity">
    <mkdir dir="${basedir}/target/generated-templates" />

    <modello version="2.0.0"
             outputDirectory="${basedir}/target/generated-templates"
             velocityBasedir="${basedir}/src/main/templates">

        <model file="src/main/mdo/model.mdo" />

        <!-- Execute Velocity generator -->
        <goal name="velocity" />

        <!-- Template specification -->
        <template name="custom-report.vm" />

        <!-- Parameters passed to the template context -->
        <param name="isMavenModel" value="true" />
        <param name="packageModel" value="org.example.api" />
    </modello>
</target>
```

