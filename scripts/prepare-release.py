#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Prepare a newer version and notes; never changes or replaces signing keys."""
import argparse
import re
from pathlib import Path

def prepare(root, version, notes):
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
        raise ValueError('Use a version such as 0.7.0, without a v prefix')
    build_file = root / 'app/build.gradle.kts'
    build = build_file.read_text()
    current = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', build).group(1))
    if tuple(map(int, version.split('.'))) <= tuple(map(int, current.split('.'))):
        raise ValueError('The new version must be greater than the current version')
    if code >= 2100000000:
        raise ValueError('Android versionCode has reached its limit')
    destination = root / f'docs/releases/{version}.md'
    if destination.exists():
        raise ValueError('Release notes already exist; refusing to overwrite them')
    if not notes.strip():
        notes = 'Updates and fixes since the previous release. The generated GitHub changelog lists the included changes.'
    if len(notes) > 20000:
        raise ValueError('Keep release notes under 20,000 characters')
    updated = re.sub(r'(versionName\s*=\s*)"[^"]+"', lambda m: f'{m[1]}"{version}"', build, count=1)
    updated = re.sub(r'(versionCode\s*=\s*)\d+', lambda m: f'{m[1]}{code + 1}', updated, count=1)
    body = f'M57 {version}\n\n{notes.strip()}\n\nThis prerelease includes a signed APK, its exact source, checksums and signing-certificate details. Device acceptance is tracked separately.\n'
    destination.write_text(body)
    build_file.write_text(updated)
    return code + 1

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version')
    parser.add_argument('--notes-file', type=Path)
    args = parser.parse_args()
    try:
        code = prepare(Path(__file__).resolve().parent.parent, args.version,
            args.notes_file.read_text() if args.notes_file else '')
    except (ValueError, OSError) as error:
        raise SystemExit(str(error))
    print(f'Prepared M57 {args.version}, Android version code {code}')
