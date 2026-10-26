#!/usr/bin/env python3
"""Compare apksigner's public certificate digest with the established identity."""
import re
import sys
from pathlib import Path

def digest(value):
    result = value.strip().lower().replace(":", "")
    if not re.fullmatch(r"[a-f0-9]{64}", result):
        raise SystemExit("Expected signing certificate must be a SHA-256 digest")
    return result

output = Path(sys.argv[1]).read_text()
found = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([A-Fa-f0-9:]+)", output)
if len(found) != 1 or digest(found[0]) != digest(sys.argv[2]):
    raise SystemExit("APK signing certificate does not match the configured identity")
print("APK signing identity verified")
