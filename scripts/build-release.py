#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Check, sign and verify a release using the existing private local key."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', type=Path, help='Also save live output to this file (overwritten)')
    parser.add_argument('--signing-directory', type=Path, default=Path.home() / '.local/share/m57/signing')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    status_command = ['git', 'status', '--porcelain', '--untracked-files=all', '--', '.']
    log = None
    if args.log:
        location = args.log.resolve()
        if location.is_relative_to(root):
            relative = location.relative_to(root).as_posix()
            tracked = subprocess.run(['git', 'ls-files', '--error-unmatch', '--', relative], cwd=root,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if tracked.returncode == 0:
                raise SystemExit('Choose a log file outside tracked source files.')
            status_command.append(':(exclude,literal)' + relative)
        args.log.parent.mkdir(parents=True, exist_ok=True)
        log = args.log.open('w')
    def say(line):
        print(line, flush=True)
        if log:
            log.write(line + '\n'); log.flush()
    env = os.environ.copy()
    toolchain = Path.home() / '.local/share/m57/toolchain'
    env.setdefault('JAVA_HOME', str(toolchain / 'jdk-17'))
    env.setdefault('ANDROID_HOME', str(toolchain / 'android-sdk'))
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    def run(command, capture=False):
        if capture:
            return subprocess.check_output(command, cwd=root, env=env, text=True).strip()
        with subprocess.Popen(command, cwd=root, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as child:
            for line in child.stdout:
                say(line.rstrip('\n'))
            status = child.wait()
            if status:
                raise subprocess.CalledProcessError(status, command)
    try:
        if run(status_command, capture=True):
            raise SystemExit('Commit source changes first so the signed APK has exact corresponding source.')
        commit = run(['git', 'rev-parse', 'HEAD'], capture=True)
        build = (root / 'app/build.gradle.kts').read_text()
        version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
        run([sys.executable, 'scripts/check-release.py', 'v' + version])
        try:
            private = json.loads((args.signing_directory / 'credentials.json').read_text())
            expected = (args.signing_directory / 'signer-sha256.txt').read_text().strip()
            env.update(M57_KEYSTORE=private['keystore'], M57_STORE_PASSWORD=private['storePassword'],
                M57_KEY_ALIAS=private['alias'], M57_KEY_PASSWORD=private['keyPassword'], M57_SIGNER_SHA256=expected,
                M57_SOURCE_REF=commit)
        except (OSError, ValueError, KeyError):
            raise SystemExit('Private signing setup is missing/incomplete. No replacement key was generated.')
        run([sys.executable, 'scripts/signing-material.py'])
        say('Running debug/release tests, lint and signed APK builds…')
        run(['./gradlew', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug',
            ':app:testReleaseUnitTest', ':app:lintRelease', ':app:assembleRelease',
            '--no-daemon', '--max-workers=2', '--console=plain'])
        if run(['git', 'rev-parse', 'HEAD'], capture=True) != commit or run(status_command, capture=True):
            raise SystemExit('Source changed during the build; rerun after committing it.')
        apk = root / 'app/build/outputs/apk/release/app-release.apk'
        if not apk.is_file():
            raise SystemExit('Signed release APK is missing; refusing to use an unsigned or older artifact.')
        signer = Path(env['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner'
        certificate = run([str(signer), 'verify', '--verbose', '--print-certs', str(apk)], capture=True)
        # Check before publishing any artifact into dist.
        import tempfile
        with tempfile.TemporaryDirectory(prefix='m57-signature-') as stage:
            check = Path(stage) / 'SIGNING.txt'; check.write_text(certificate + '\n')
            run([sys.executable, 'scripts/check-signer.py', str(check), expected])
        output = root / 'dist' / ('v' + version)
        output.mkdir(parents=True, exist_ok=True)
        binary = output / f'm57-v{version}.apk'
        source = output / f'm57-v{version}-source.tar.gz'
        shutil.copyfile(apk, binary)
        run(['git', 'archive', '--format=tar.gz', f'--prefix=m57-v{version}/', '-o', str(source), commit])
        (output / 'SIGNING.txt').write_text(certificate + '\n')
        (output / 'BUILD.txt').write_text(f'M57 {version}\nSource commit: {commit}\nSource: https://github.com/akshay-abraham/m57/tree/{commit}\n')
        for name in ('LICENSE', 'NOTICE', 'LICENSING.md'):
            shutil.copyfile(root / name, output / name)
        shutil.copytree(root / 'LICENSES', output / 'LICENSES', dirs_exist_ok=True)
        (output / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in (binary, source)))
        say(f'Signed APK verified and ready: {binary}')
        say(f'Exact source, checksums and certificate: {output}')
        say('The existing debug-signed installation requires a deliberate transition; no phone data was changed.')
    except subprocess.CalledProcessError as error:
        say(f'Command failed with exit code {error.returncode}. No verified release was produced.')
        return error.returncode
    except (OSError, ValueError, SystemExit) as error:
        say(str(error))
        return 1
    finally:
        if log: log.close()
    return 0

if __name__ == '__main__':
    sys.exit(main())
