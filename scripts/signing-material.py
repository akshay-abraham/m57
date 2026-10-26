#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""Validate the configured private key and public identity without displaying passwords."""
import argparse
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

def verify_environment():
    names = ('M57_KEYSTORE', 'M57_STORE_PASSWORD', 'M57_KEY_ALIAS', 'M57_KEY_PASSWORD', 'M57_SIGNER_SHA256')
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing signing settings: ' + ', '.join(missing))
    expected = os.environ['M57_SIGNER_SHA256'].strip().lower().replace(':', '')
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise SystemExit('M57_SIGNER_SHA256 must contain the expected certificate SHA-256')
    keytool = str(Path(os.environ['JAVA_HOME']) / 'bin/keytool') if os.environ.get('JAVA_HOME') else 'keytool'
    result = subprocess.run([keytool, '-exportcert', '-keystore', os.environ['M57_KEYSTORE'],
        '-alias', os.environ['M57_KEY_ALIAS'], '-storepass:env', 'M57_STORE_PASSWORD'], capture_output=True)
    if result.returncode:
        raise SystemExit('Cannot open the configured keystore/alias; check the private signing settings')
    if hashlib.sha256(result.stdout).hexdigest() != expected:
        raise SystemExit('Signing key differs from the established M57 certificate; refusing to build')
    print('Established M57 signing certificate verified', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    verify_environment()
