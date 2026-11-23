#!/usr/bin/env python3
"""Integrate location glue and keep CN1's generated app-shell cache coherent."""
import hashlib, json, re, sys
from pathlib import Path
web = Path(sys.argv[1])
index = web / 'index.html'
source = index.read_text()
assert '<head>' in source
index.write_text(source.replace('<head>', '<head><script src="account-link.js"></script>', 1))
worker = web / 'sw.js'
source = worker.read_text()
pattern = r'self\.cn1Pwa = (\{.*?\});'
match = re.search(pattern, source, re.S)
if not match:
    raise SystemExit('Pinned CN1 worker metadata missing; refusing an incoherent app-shell cache')
metadata = json.loads(match.group(1))
metadata['assets'] = sorted(set(metadata['assets'] + ['account-link.js']))
digest = hashlib.sha256()
for asset in metadata['assets']:
    digest.update(asset.encode())
    digest.update((web / asset).read_bytes())
metadata['version'] = digest.hexdigest()
worker.write_text(source[:match.start(1)] + json.dumps(metadata, indent=2) + source[match.end(1):])
