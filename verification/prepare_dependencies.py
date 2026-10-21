"""Install checksum-locked current development mods. Does not launch Minecraft.

Old candidate releases remain in ignored build/dependency-matrix, never run/mods.
Use gradlew copyDevMods afterwards to update the development directory safely.
"""
import argparse
import hashlib
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fetch(path, url, sha):
    data = path.read_bytes() if path.exists() else urllib.request.urlopen(url, timeout=60).read()
    assert hashlib.sha256(data).hexdigest() == sha, f'Release checksum mismatch: {path.name}'
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_bytes(data)
    print('Verified', path.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--with-map', action='store_true', help='Install the pinned optional Xaero test client')
    args = parser.parse_args()
    if args.with_map:
        row = json.loads((ROOT / 'verification/map-test-dependencies.json').read_text('utf-8'))
        fetch(ROOT / 'libs' / row['file'], row['url'], row['sha256'])
    rows = json.loads((ROOT / 'verification/dependency-releases.json').read_text('utf-8'))
    for mod in ['sable', 'bnb', 'cee', 'railways', 'hose', 'logistics', 'aero']:
        row = next(r for r in rows if r['mod'] == mod)
        fetch(ROOT / 'libs' / row['file'], row['url'], row['sha256'])
    mirror = json.loads((ROOT / 'verification/dependency-maven.json').read_text('utf-8'))
    for row in mirror:
        fetch(ROOT / 'libs/compat-maven' / row['path'], row['url'], row['sha256'])


if __name__ == '__main__':
    main()
