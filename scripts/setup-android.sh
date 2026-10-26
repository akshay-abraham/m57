#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# User-local Linux x86_64 toolchain; does not replace the system Java installation.
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
command -v python3 >/dev/null || { echo 'Install python3 first.' >&2; exit 1; }

python3 - <<'PY'
import hashlib
import json
import platform
import re
import shlex
import shutil
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

if platform.system() != 'Linux' or platform.machine() != 'x86_64':
    raise SystemExit('This setup script targets Linux x86_64.')

root = Path.home() / '.local/share/m57/toolchain'
cache = Path.home() / '.cache/m57-toolchain'
root.mkdir(parents=True, exist_ok=True)
cache.mkdir(parents=True, exist_ok=True)
jdk = root / 'jdk-17'
sdk = root / 'android-sdk'

def request(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'M57-toolchain-setup'}), timeout=60)

def verified_download(url, filename, expected):
    if not re.fullmatch(r'[0-9a-fA-F]{64}', expected):
        raise SystemExit('Missing or invalid download checksum')
    target = cache / filename
    def matches(path):
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        return digest.hexdigest() == expected.lower()
    if target.exists() and matches(target):
        return target
    print(f'Downloading {filename}...', flush=True)
    partial = target.with_suffix(target.suffix + '.partial')
    with request(url) as source, partial.open('wb') as output:
        shutil.copyfileobj(source, output, 1024 * 1024)
    if not matches(partial):
        partial.unlink()
        raise SystemExit(f'Checksum verification failed: {filename}')
    partial.replace(target)
    return target

if not (jdk / 'bin/javac').is_file():
    if jdk.exists():
        raise SystemExit(f'Incomplete JDK directory; inspect it before retrying: {jdk}')
    api = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&os=linux&vendor=eclipse'
    with request(api) as response:
        package = json.load(response)[0]['binary']['package']
    archive = verified_download(package['link'], 'temurin-17-linux-x64.tar.gz', package['checksum'])
    with tempfile.TemporaryDirectory(dir=root) as stage:
        with tarfile.open(archive) as source:
            source.extractall(stage, filter='data')
        candidates = [item for item in Path(stage).iterdir() if (item / 'bin/javac').is_file()]
        if len(candidates) != 1:
            raise SystemExit('Unexpected JDK archive layout')
        shutil.move(str(candidates[0]), jdk)

tools = sdk / 'cmdline-tools/latest'
if not (tools / 'bin/sdkmanager').is_file():
    if tools.exists():
        raise SystemExit(f'Incomplete command-line tools directory; inspect it first: {tools}')
    archive = verified_download(
        'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip',
        'commandlinetools-linux-15859902_latest.zip',
        '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583',
    )
    tools.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=root) as stage:
        with zipfile.ZipFile(archive) as source:
            source.extractall(stage)
        shutil.move(str(Path(stage) / 'cmdline-tools'), tools)
    for executable in (tools / 'bin').iterdir():
        if executable.is_file():
            executable.chmod(0o755)

environment = Path.home() / '.config/m57/android-env.sh'
environment.parent.mkdir(parents=True, exist_ok=True)
environment.write_text(
    '# M57 local Android toolchain; source this file before using Gradle.\n'
    f'export JAVA_HOME={shlex.quote(str(jdk))}\n'
    f'export ANDROID_HOME={shlex.quote(str(sdk))}\n'
    'export ANDROID_SDK_ROOT="$ANDROID_HOME"\n'
    'export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"\n'
)
print(f'Toolchain environment saved to {environment}', flush=True)
PY

source "$HOME/.config/m57/android-env.sh"
java -version
javac -version
echo 'Review and accept the Android SDK licenses to continue.'
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
./gradlew --version --no-daemon
echo 'Setup complete. In your terminal, run: source ~/.config/m57/android-env.sh'
