from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_poly(path: Path) -> dict:
    lines = [line.rstrip() for line in path.read_text(encoding="utf-8").splitlines()]
    rings: list[tuple[bool, list[list[float]]]] = []
    current: list[list[float]] | None = None
    current_hole = False

    for raw in lines[1:]:
        stripped = raw.strip()
        if not stripped:
            continue
        if stripped == "END":
            if current is not None:
                if len(current) < 4:
                    raise ValueError("Polygon ring has fewer than 4 coordinates")
                if current[0] != current[-1]:
                    current.append(current[0])
                rings.append((current_hole, current))
                current = None
            else:
                break
            continue
        if current is None:
            current_hole = stripped.startswith("!")
            current = []
            continue
        parts = stripped.split()
        if len(parts) != 2:
            raise ValueError(f"Invalid .poly coordinate: {raw!r}")
        lon, lat = map(float, parts)
        current.append([lon, lat])

    polygons: list[list[list[list[float]]]] = []
    for is_hole, ring in rings:
        if not is_hole:
            polygons.append([ring])
        else:
            if not polygons:
                raise ValueError("Hole encountered before outer polygon")
            polygons[-1].append(ring)

    if not polygons:
        raise ValueError("No polygons found")

    geometry = (
        {"type": "Polygon", "coordinates": polygons[0]}
        if len(polygons) == 1
        else {"type": "MultiPolygon", "coordinates": polygons}
    )
    return {
        "type": "Feature",
        "properties": {
            "source": "Geofabrik",
            "sourceReference": "geofabrik/asia/thailand.poly",
        },
        "geometry": geometry,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert Osmium .poly boundary to GeoJSON.")
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    feature = parse_poly(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(feature, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
