"""Fetch pinned published optional APIs/runtime jars; no external code or assets enter our artifact."""
import hashlib
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ("create_bb-1.0.8-1.21.1.jar", "https://mediafilez.forgecdn.net/files/8723/463/create_bb-1.0.8-1.21.1.jar",
     "36a1cf8eaef08254085f9190b6a3130dd787747ee30fb8eeb606d6a6e0f9bf4f"),
    ("electroenergetics-1.21.1-1.1.4.jar", "https://mediafilez.forgecdn.net/files/9173/792/electroenergetics-1.21.1-1.1.4.jar",
     "2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f"),
]

if __name__ == "__main__":
    for name, url, checksum in FILES:
        dest = ROOT / "libs" / name
        data = dest.read_bytes() if dest.exists() else urllib.request.urlopen(url).read()
        if hashlib.sha256(data).hexdigest() != checksum:
            raise RuntimeError(f"Pinned release checksum mismatch: {name}")
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
        print(f"Verified {name} SHA-256 {checksum}")
