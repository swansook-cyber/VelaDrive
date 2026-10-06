from __future__ import annotations

import argparse
import urllib.parse
import urllib.request
from pathlib import Path


DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"
USER_AGENT = "VelaDrive-OSM-Importer/1.0 (local development)"


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch a checked-in Overpass query.")
    parser.add_argument("--query", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    args = parser.parse_args()
    query = args.query.read_text(encoding="utf-8", errors="strict")
    body = urllib.parse.urlencode({"data": query}).encode("utf-8")
    request = urllib.request.Request(
        args.endpoint,
        data=body,
        headers={"User-Agent": USER_AGENT},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=300) as response:
        payload = response.read()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

