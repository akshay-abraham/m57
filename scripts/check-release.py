#!/usr/bin/env python3
"""Validate metadata before accessing private signing material."""
import re
import sys
from pathlib import Path

tag = sys.argv[1]
if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", tag):
    raise SystemExit("Use a version tag such as v0.6.0")
build = Path("app/build.gradle.kts").read_text()
version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
if tag != f"v{version}":
    raise SystemExit("Tag must match Android versionName")
notes = Path(f"docs/releases/{version}.md")
if not notes.is_file() or not notes.read_text().strip():
    raise SystemExit("Versioned release notes are required")
print(f"Release metadata checked: {tag}")
