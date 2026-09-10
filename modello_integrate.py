#!/usr/bin/env python3
"""Transform the standalone modello-ant-task repository into an upstream Modello reactor module.

Generically performs:
1. Directory path movement:
   Finds any occurrence of the old group/organization path (e.g. com/github/fridrich/modello
   or com/github/fridrich) under source roots (src/**/java, src/**/resources) and moves
   the entire tree to org/codehaus/modello without listing individual files.
   Uses `git mv` when run within a git repository to preserve full file history.
2. Dynamic package updates:
   Recursively inspects all Java files, computes the package declaration from the file's
   actual filesystem location relative to its source root (src/**/java), and updates
   the package statement accordingly.
3. Generic reference updates:
   Recursively updates package, class, resource path, and groupId references across all
   source files, resources (antlib.xml), and documentation (README.md).
4. Targeted XPath POM conversion:
   Mutates the existing pom.xml in-place via targeted XPath additions and removals:
   - Updates <parent> coordinates to org.codehaus.modello:modello
   - Removes project-level <groupId> and <version> (inherited from parent)
   - Removes properties managed by the reactor parent
   - Removes redundant version tags from reactor-managed dependencies
   - Updates manifest entries (Automatic-Module-Name)
   - Runs `mvn spotless:apply` to format the resulting files cleanly
5. Optional reactor registration:
   - Registers <module>modello-ant-task</module> in parent pom.xml
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

OLD_GROUP_PATH_FULL = Path("com/github/fridrich/modello")
OLD_GROUP_PATH_BASE = Path("com/github/fridrich")
NEW_GROUP_PATH = Path("org/codehaus/modello")

POM_NS = {"pom": "http://maven.apache.org/POM/4.0.0"}


def is_git_repo(repo_dir: Path) -> bool:
    try:
        res = subprocess.run(
            ["git", "-C", str(repo_dir), "rev-parse", "--is-inside-work-tree"],
            capture_output=True,
            text=True,
            check=False,
        )
        return res.returncode == 0 and res.stdout.strip() == "true"
    except FileNotFoundError:
        return False


def detect_parent_version(repo_dir: Path, explicit_version: str | None = None) -> str:
    if explicit_version:
        return explicit_version

    parent_pom = repo_dir.parent / "pom.xml"
    if parent_pom.is_file():
        try:
            tree = ET.parse(parent_pom)
            root = tree.getroot()
            artifact_id = root.findtext("pom:artifactId", namespaces=POM_NS)
            if artifact_id == "modello":
                version = root.findtext("pom:version", namespaces=POM_NS)
                if not version:
                    parent_elem = root.find("pom:parent", namespaces=POM_NS)
                    if parent_elem is not None:
                        version = parent_elem.findtext("pom:version", namespaces=POM_NS)
                if version:
                    return version.strip()
        except Exception:
            pass

    current_pom = repo_dir / "pom.xml"
    if current_pom.is_file():
        content = current_pom.read_text(encoding="utf-8")
        m = re.search(r"<modello\.version>([^<]+)</modello\.version>", content)
        if m:
            return m.group(1).strip()
        m = re.search(r"<parent>\s*<groupId>org\.codehaus\.modello</groupId>\s*<artifactId>modello</artifactId>\s*<version>([^<]+)</version>", content)
        if m:
            return m.group(1).strip()

    return "2.8.2-SNAPSHOT"


def move_directory_generic(repo_dir: Path, src_dir: Path, dst_dir: Path, use_git: bool, dry_run: bool) -> None:
    print(f"Moving tree {src_dir.relative_to(repo_dir)} -> {dst_dir.relative_to(repo_dir)}")
    if dry_run:
        return

    dst_dir.parent.mkdir(parents=True, exist_ok=True)

    if not dst_dir.exists():
        moved = False
        if use_git:
            res = subprocess.run(
                ["git", "-C", str(repo_dir), "mv", str(src_dir.relative_to(repo_dir)), str(dst_dir.relative_to(repo_dir))],
                capture_output=True,
                text=True,
                check=False,
            )
            moved = res.returncode == 0
        if not moved:
            shutil.move(str(src_dir), str(dst_dir))
    else:
        # Destination directory already exists, move child items individually
        for item in list(src_dir.iterdir()):
            target = dst_dir / item.name
            moved = False
            if use_git:
                res = subprocess.run(
                    ["git", "-C", str(repo_dir), "mv", str(item.relative_to(repo_dir)), str(target.relative_to(repo_dir))],
                    capture_output=True,
                    text=True,
                    check=False,
                )
                moved = res.returncode == 0
            if not moved:
                shutil.move(str(item), str(target))
        try:
            src_dir.rmdir()
        except OSError:
            pass


def prune_empty_dirs(base_dir: Path, dry_run: bool) -> None:
    if not base_dir.is_dir():
        return
    for root, dirs, files in os.walk(base_dir, topdown=False):
        dir_path = Path(root)
        if dir_path == base_dir:
            continue
        if not any(dir_path.iterdir()):
            if not dry_run:
                try:
                    dir_path.rmdir()
                except OSError:
                    pass
    if not dry_run and not any(base_dir.iterdir()):
        try:
            base_dir.rmdir()
        except OSError:
            pass


def move_paths_generically(repo_dir: Path, use_git: bool, dry_run: bool) -> None:
    src_root = repo_dir / "src"
    if not src_root.is_dir():
        return

    source_roots: list[Path] = []
    for path in src_root.rglob("*"):
        if path.is_dir() and path.name in ("java", "resources"):
            source_roots.append(path)

    for root in source_roots:
        target_dir = root / NEW_GROUP_PATH
        full_old = root / OLD_GROUP_PATH_FULL
        base_old = root / OLD_GROUP_PATH_BASE

        if full_old.is_dir():
            move_directory_generic(repo_dir, full_old, target_dir, use_git, dry_run)
            prune_empty_dirs(root / "com", dry_run)
        elif base_old.is_dir():
            move_directory_generic(repo_dir, base_old, target_dir, use_git, dry_run)
            prune_empty_dirs(root / "com", dry_run)


def find_java_source_root(java_file: Path) -> Path | None:
    curr = java_file.parent
    while curr != curr.parent:
        if curr.name == "java" and curr.parent.name in ("main", "test"):
            return curr
        curr = curr.parent
    return None


def update_java_packages_and_references(repo_dir: Path, dry_run: bool) -> None:
    for java_file in (repo_dir / "src").rglob("*.java"):
        source_root = find_java_source_root(java_file)
        if not source_root:
            continue

        rel_dir = java_file.parent.relative_to(source_root)
        computed_package = ".".join(rel_dir.parts)

        content = java_file.read_text(encoding="utf-8")
        original = content

        content = re.sub(
            r"^\s*package\s+[^;]+;",
            f"package {computed_package};",
            content,
            count=1,
            flags=re.MULTILINE,
        )

        content = re.sub(r"\bcom\.github\.fridrich\.modello\b", "org.codehaus.modello", content)
        content = re.sub(r"\bcom\.github\.fridrich\b", "org.codehaus.modello", content)

        if content != original:
            print(f"Updated package in {java_file.relative_to(repo_dir)} -> {computed_package}")
            if not dry_run:
                java_file.write_text(content, encoding="utf-8")


def update_other_files_generically(repo_dir: Path, dry_run: bool) -> None:
    text_extensions = {".xml", ".md", ".txt", ".properties", ".yaml", ".yml", ".json"}
    files_to_check: list[Path] = []

    for path in (repo_dir / "src").rglob("*"):
        if path.is_file() and path.suffix in text_extensions and path.suffix != ".java":
            files_to_check.append(path)

    for doc in repo_dir.glob("*.md"):
        files_to_check.append(doc)

    replacements = [
        ("com/github/fridrich/modello", "org/codehaus/modello"),
        ("com.github.fridrich.modello", "org.codehaus.modello"),
        ("com/github/fridrich", "org/codehaus/modello"),
        ("com.github.fridrich", "org.codehaus.modello"),
    ]

    for file_path in files_to_check:
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        original = content
        for old_str, new_str in replacements:
            content = content.replace(old_str, new_str)

        if content != original:
            print(f"Updated references in {file_path.relative_to(repo_dir)}")
            if not dry_run:
                file_path.write_text(content, encoding="utf-8")


def transform_pom_xpath(repo_dir: Path, parent_version: str, dry_run: bool) -> None:
    pom_path = repo_dir / "pom.xml"
    if not pom_path.is_file():
        return

    content = pom_path.read_text(encoding="utf-8")
    project_idx = content.find("<project")
    header = content[:project_idx] if project_idx != -1 else ""

    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    tree = ET.parse(pom_path, parser=parser)
    root = tree.getroot()

    # 1. Update <parent>
    parent = root.find("pom:parent", POM_NS)
    if parent is not None:
        p_gid = parent.find("pom:groupId", POM_NS)
        if p_gid is not None:
            p_gid.text = "org.codehaus.modello"
        p_aid = parent.find("pom:artifactId", POM_NS)
        if p_aid is not None:
            p_aid.text = "modello"
        p_ver = parent.find("pom:version", POM_NS)
        if p_ver is not None:
            p_ver.text = parent_version
        rel_path = parent.find("pom:relativePath", POM_NS)
        if rel_path is not None:
            parent.remove(rel_path)

    # 2. Remove project-level <groupId> and <version> (inherited from parent)
    group_id = root.find("pom:groupId", POM_NS)
    if group_id is not None:
        root.remove(group_id)

    version = root.find("pom:version", POM_NS)
    if version is not None:
        root.remove(version)

    # 3. Remove properties managed by Modello reactor parent
    managed_props = {
        "maven.compiler.release",
        "maven.compiler.source",
        "maven.compiler.target",
        "project.build.sourceEncoding",
        "modello.version",
        "plexus.xml.version",
        "junit.version",
        "slf4j.version",
    }
    props = root.find("pom:properties", POM_NS)
    if props is not None:
        for p in list(props):
            tag_name = p.tag.split("}")[-1]
            if tag_name in managed_props:
                props.remove(p)

    # 4. Remove redundant versions from dependencies managed by reactor
    deps = root.find("pom:dependencies", POM_NS)
    if deps is not None:
        for dep in deps.findall("pom:dependency", POM_NS):
            gid = dep.findtext("pom:groupId", namespaces=POM_NS)
            aid = dep.findtext("pom:artifactId", namespaces=POM_NS)
            ver = dep.find("pom:version", POM_NS)
            if ver is not None:
                if gid == "org.codehaus.modello" or aid in ("plexus-xml", "slf4j-simple") or gid == "org.junit.jupiter":
                    dep.remove(ver)

    ET.register_namespace("", "http://maven.apache.org/POM/4.0.0")
    ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")
    xml_body = ET.tostring(root, encoding="utf-8").decode("utf-8")
    xml_body = re.sub(r"\bcom\.github\.fridrich\.modello\b", "org.codehaus.modello", xml_body)
    xml_body = re.sub(r"\bcom\.github\.fridrich\b", "org.codehaus.modello", xml_body)

    print("Transformed pom.xml via targeted XPath modifications")
    if not dry_run:
        pom_path.write_text(header + xml_body + "\n", encoding="utf-8")


def apply_spotless(repo_dir: Path, dry_run: bool) -> None:
    if dry_run:
        return
    try:
        print("Running spotless:apply on transformed project")
        subprocess.run(
            ["mvn", "spotless:apply", "-q"],
            cwd=str(repo_dir),
            check=False,
        )
    except FileNotFoundError:
        pass


def update_parent_reactor_pom(parent_pom: Path, module_name: str, dry_run: bool) -> None:
    if not parent_pom.is_file():
        return

    content = parent_pom.read_text(encoding="utf-8")
    module_entry = f"<module>{module_name}</module>"

    if module_entry in content:
        print(f"Parent pom.xml already contains {module_entry}.")
        return

    modules_pattern = r"(<modules>.*?)(\s*</modules>)"
    if re.search(modules_pattern, content, flags=re.DOTALL):
        content = re.sub(
            modules_pattern,
            rf"\1\n    {module_entry}\2",
            content,
            count=1,
            flags=re.DOTALL,
        )
        print(f"Added {module_entry} to parent pom.xml")
        if not dry_run:
            parent_pom.write_text(content, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Convert modello-ant-task to an upstream Modello reactor module."
    )
    parser.add_argument(
        "--repo-dir",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="Path to repository root (defaults to script's directory).",
    )
    parser.add_argument(
        "--parent-version",
        type=str,
        default=None,
        help="Modello parent reactor version (default: auto-detected from ../pom.xml or 2.8.2-SNAPSHOT).",
    )
    parser.add_argument(
        "--no-git",
        action="store_true",
        help="Do not use git mv, use standard filesystem operations.",
    )
    parser.add_argument(
        "--no-spotless",
        action="store_true",
        help="Do not run mvn spotless:apply after transformations.",
    )
    parser.add_argument(
        "--update-parent",
        action="store_true",
        help="Also register <module> in ../pom.xml if found.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show changes without modifying any files.",
    )

    args = parser.parse_args()
    repo_dir = args.repo_dir.resolve()

    if not (repo_dir / "pom.xml").is_file():
        print(f"Error: {repo_dir} does not contain a pom.xml", file=sys.stderr)
        return 1

    use_git = not args.no_git and is_git_repo(repo_dir)
    parent_version = detect_parent_version(repo_dir, args.parent_version)

    print(f"Repository:     {repo_dir}")
    print(f"Parent version: {parent_version}")
    print(f"Git operations: {'yes' if use_git else 'no'}")
    print(f"Dry run:        {'yes' if args.dry_run else 'no'}")
    print("-" * 50)

    # 1. Move directory trees generically
    move_paths_generically(repo_dir, use_git, args.dry_run)

    # 2. Update Java package declarations dynamically based on location
    update_java_packages_and_references(repo_dir, args.dry_run)

    # 3. Update references across non-Java files generically
    update_other_files_generically(repo_dir, args.dry_run)

    # 4. Transform pom.xml via targeted XPath modifications
    transform_pom_xpath(repo_dir, parent_version, args.dry_run)

    # 5. Apply Spotless to format everything cleanly
    if not args.no_spotless:
        apply_spotless(repo_dir, args.dry_run)

    # 6. Optionally update parent reactor pom.xml
    parent_pom = repo_dir.parent / "pom.xml"
    if args.update_parent and parent_pom.is_file():
        update_parent_reactor_pom(parent_pom, repo_dir.name, args.dry_run)

    print("-" * 50)
    print("Integration preparation complete.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
