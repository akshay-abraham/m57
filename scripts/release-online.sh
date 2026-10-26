#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Lightweight launcher: all Android compilation happens on GitHub's runners.
set -euo pipefail
if [[ $# -ne 1 || "$1" == --help || "$1" == -h ]]; then
    echo 'Usage: bash scripts/release-online.sh VERSION'
    echo 'Example: bash scripts/release-online.sh 0.6.3'
    echo 'Pushes committed source, configures the saved signing key and starts GitHub Actions.'
    [[ $# -eq 1 && ( "$1" == --help || "$1" == -h ) ]] && exit 0
    exit 2
fi
version=$1
python3 - "$version" <<'PY'
import re, sys
if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', sys.argv[1]):
    raise SystemExit('Use a version such as 0.6.3, without a v prefix.')
PY
cd -- "$(dirname -- "$0")/.."
if [[ -n "$(git status --porcelain --untracked-files=all)" ]]; then
    echo 'Commit source changes before starting a release.' >&2
    exit 1
fi
repository='akshay-abraham/m57'
git push origin main
python3 scripts/upload-signing-secrets.py --repository "$repository"
gh workflow run prepare-release.yml --repo "$repository" --ref main -f "version=$version"
echo "GitHub is preparing M57 $version. All tests, builds, signing and publication run online."
echo "Progress: https://github.com/$repository/actions"
