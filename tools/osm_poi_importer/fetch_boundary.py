from __future__ import annotations

import argparse
import json
import urllib.parse
import urllib.request
from pathlib import Path


NOMINATIM_ENDPOINT = "https://nominatim.openstreetmap.org/search"
USER_AGENT = "VelaDrive-OSM-Importer/1.0 (local development)"


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch an OSM-derived GeoJSON boundary.")
    parser.add_argument("--query", required=True)
    parser.add_argument("--osm-relation-id", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    url = NOMINATIM_ENDPOINT + "?" + urllib.parse.urlencode(
        {
            "format": "jsonv2",
            "q": args.query,
            "polygon_geojson": 1,
            "limit": 5,
        }
    )
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        results = json.load(response)
    match = next(
        (
            result
            for result in results
            if result.get("osm_type") == "relation"
            and int(result.get("osm_id", -1)) == args.osm_relation_id
        ),
        None,
    )
    if match is None or "geojson" not in match:
        raise RuntimeError("Requested OSM relation boundary was not returned")
    feature = {
        "type": "Feature",
        "properties": {
            "name": match.get("display_name"),
            "source": "OpenStreetMap",
            "sourceReference": f"relation/{args.osm_relation_id}",
            "sourceUrl": f"https://www.openstreetmap.org/relation/{args.osm_relation_id}",
            "attribution": "© OpenStreetMap contributors",
            "license": "ODbL 1.0",
        },
        "geometry": match["geojson"],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(
            feature,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        + "\n",
        encoding="utf-8",
    )
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

