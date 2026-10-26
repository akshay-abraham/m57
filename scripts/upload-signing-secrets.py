#!/usr/bin/env python3
"""Send local signing material to the explicitly named GitHub repo via gh stdin."""
import argparse
import base64
import json
import os
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--repository', required=True, help='owner/repo that should receive the private key')
parser.add_argument('--directory', type=Path, default=Path.home() / '.local/share/m57/signing')
args = parser.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
    raise SystemExit('Use a GitHub owner/repo')
config = json.loads((args.directory / 'credentials.json').read_text())
key = Path(config['keystore']).read_bytes()
fingerprint = (args.directory / 'signer-sha256.txt').read_text().strip()
if not re.fullmatch(r'[a-fA-F0-9]{64}', fingerprint):
    raise SystemExit('Expected certificate SHA-256 is missing or malformed')
# Verify the target is accessible before sending any private material.
subprocess.run(['gh', 'repo', 'view', args.repository, '--json', 'nameWithOwner', '--jq', '.nameWithOwner'], check=True)
values = {
    'M57_KEYSTORE_BASE64': base64.b64encode(key).decode('ascii'),
    'M57_STORE_PASSWORD': config['storePassword'],
    'M57_KEY_ALIAS': config['alias'],
    'M57_KEY_PASSWORD': config['keyPassword'],
    'M57_SIGNER_SHA256': fingerprint,
}
env = os.environ.copy()
env.update({name: value for name, value in values.items() if name != 'M57_KEYSTORE_BASE64'})
env['M57_KEYSTORE'] = config['keystore']
subprocess.run(['python3', str(Path(__file__).with_name('signing-material.py'))], env=env, check=True)
for name, value in values.items():
    subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repository], input=value.encode('utf-8'), check=True)
    print(f'Configured {name}')
