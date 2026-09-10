# Upstream Modello Reactor Integration

This document outlines how to integrate `modello-ant-task` into the upstream [Modello](https://github.com/codehaus-plexus/modello) multi-module reactor.

The automated transformation is handled by `modello_integrate.py`.

## Option 1: Integrate Directly into an Upstream Modello Clone (Recommended)

When working inside a clone of `codehaus-plexus/modello`:

1. **Import `modello-ant-task` into the reactor directory tree:**

   ```bash
   # From the root of the cloned upstream modello repository
   git remote add ant-task https://github.com/fridrich/modello-ant-task.git
   git fetch ant-task

   # Merge as a subdirectory preserving commit history
   git subtree add --prefix=modello-ant-task ant-task main
   ```
2. **Run the integration script:**

   ```bash
   cd modello-ant-task
   python3 modello_integrate.py --update-parent
   ```

   This executes:
   - Generic path move: `com/github/fridrich/...` -> `org/codehaus/modello/...` via `git mv`.
   - Dynamic package computation: updates all Java files to `package org.codehaus.modello.ant;`.
   - Generic reference update across `antlib.xml`, `README.md`, etc.
   - XPath mutation of `pom.xml`: inherits from `org.codehaus.modello:modello` parent, auto-detects parent version from `../pom.xml`, and removes redundant versions and properties.
   - Parent POM update: registers `<module>modello-ant-task</module>` in `../pom.xml`.
   - Spotless formatting: executes `mvn spotless:apply` to conform to upstream formatting rules.

3. **Verify the reactor build:**

   ```bash
   cd ..
   mvn clean verify -pl modello-ant-task
   ```
4. **Submit Pull Request:**

   Commit the changes and open a PR against `codehaus-plexus/modello`.

## Option 2: Prepare an Integration Branch in This Repository

If upstream prefers reviewing the refactored standalone repository before importing:

1. **Create an integration branch:**

   ```bash
   git checkout -b reactor-integration
   python3 modello_integrate.py --parent-version 2.8.2-SNAPSHOT
   ```
2. **Verify tests pass:**

   ```bash
   mvn clean test
   ```
3. **Inspect the git status and diff:**

   ```bash
   git status
   git diff
   ```

## Script Options Reference

`modello_integrate.py` supports the following arguments:

|           Flag           |                                                           Description                                                            |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `--dry-run`              | Preview planned directory moves, package changes, and POM modifications without altering files.                                  |
| `--parent-version <ver>` | Explicitly set the `org.codehaus.modello:modello` parent version (default: auto-detected from `../pom.xml` or `2.8.2-SNAPSHOT`). |
| `--update-parent`        | Register `<module>modello-ant-task</module>` in `../pom.xml` if found.                                                           |
| `--no-git`               | Use standard filesystem operations instead of `git mv` (for non-git environments or tarball imports).                            |
| `--no-spotless`          | Skip running `mvn spotless:apply` after modifications.                                                                           |
| `--repo-dir <path>`      | Repository root directory (default: current script directory).                                                                   |

