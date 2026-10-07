from __future__ import annotations

import argparse
import json
import math
import unicodedata
from datetime import date
from pathlib import Path
from typing import Any, Iterable, Mapping

EARTH_RADIUS_METERS = 6_371_000.0
EXACT_NAME_DEDUPE_METERS = 25.0
NEAR_CURATED_REVIEW_METERS = 100.0


def normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).split()).casefold()


def distance_meters(a: Mapping[str, Any], b: Mapping[str, Any]) -> float:
    lat1 = math.radians(float(a["latitude"]))
    lat2 = math.radians(float(b["latitude"]))
    dlat = math.radians(float(b["latitude"]) - float(a["latitude"]))
    dlon = math.radians(float(b["longitude"]) - float(a["longitude"]))
    hav = (
        math.sin(dlat / 2) ** 2
        + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    )
    return EARTH_RADIUS_METERS * 2 * math.atan2(math.sqrt(hav), math.sqrt(1 - hav))


def load_dataset(path: Path) -> dict[str, Any]:
    root = json.loads(path.read_text(encoding="utf-8"))
    if root.get("schemaVersion") != 1 or not isinstance(root.get("pois"), list):
        raise ValueError(f"Invalid Vela POI dataset: {path}")
    return root


def deterministic_write(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def merge(osm: dict[str, Any], curated: dict[str, Any], snapshot_date: str) -> tuple[dict[str, Any], dict[str, Any]]:
    date.fromisoformat(snapshot_date)
    osm_pois = [p for p in osm["pois"] if p.get("source") == "OPENSTREETMAP"]
    curated_pois = [p for p in curated["pois"] if p.get("source") == "VELA_CURATED"]

    ids: set[str] = set()
    output: list[dict[str, Any]] = []
    for poi in curated_pois:
        poi_id = str(poi.get("id", "")).strip()
        if not poi_id or poi_id in ids:
            raise ValueError(f"Duplicate/blank curated id: {poi_id!r}")
        ids.add(poi_id)
        output.append(poi)

    deduped_against_curated: list[dict[str, Any]] = []
    near_curated_review: list[dict[str, Any]] = []

    curated_by_category: dict[str, list[dict[str, Any]]] = {}
    for poi in curated_pois:
        curated_by_category.setdefault(str(poi.get("category")), []).append(poi)

    for poi in osm_pois:
        poi_id = str(poi.get("id", "")).strip()
        if not poi_id or poi_id in ids:
            continue
        matches = curated_by_category.get(str(poi.get("category")), [])
        exact = None
        nearest = None
        nearest_distance = float("inf")
        for candidate in matches:
            d = distance_meters(candidate, poi)
            if d < nearest_distance:
                nearest_distance = d
                nearest = candidate
            if (
                d <= EXACT_NAME_DEDUPE_METERS
                and normalize(str(candidate.get("name", ""))) == normalize(str(poi.get("name", "")))
            ):
                exact = candidate
                break
        if exact is not None:
            deduped_against_curated.append(
                {
                    "osmId": poi_id,
                    "curatedId": exact.get("id"),
                    "name": poi.get("name"),
                    "distanceMeters": round(distance_meters(exact, poi), 1),
                }
            )
            continue
        if nearest is not None and nearest_distance <= NEAR_CURATED_REVIEW_METERS:
            near_curated_review.append(
                {
                    "osmId": poi_id,
                    "osmName": poi.get("name"),
                    "curatedId": nearest.get("id"),
                    "curatedName": nearest.get("name"),
                    "category": poi.get("category"),
                    "distanceMeters": round(nearest_distance, 1),
                }
            )
        ids.add(poi_id)
        output.append(poi)

    output.sort(
        key=lambda p: (
            0 if p.get("source") == "VELA_CURATED" else 1,
            str(p.get("category", "")),
            normalize(str(p.get("name", ""))),
            str(p.get("id", "")),
        )
    )
    category_counts: dict[str, int] = {}
    source_counts: dict[str, int] = {}
    for poi in output:
        category = str(poi.get("category"))
        source = str(poi.get("source"))
        category_counts[category] = category_counts.get(category, 0) + 1
        source_counts[source] = source_counts.get(source, 0) + 1

    dataset = {
        "schemaVersion": 1,
        "datasetVersion": f"thailand-{snapshot_date}-osm+curated-v1",
        "updatedAt": snapshot_date,
        "attribution": "© OpenStreetMap contributors",
        "license": "Open Data Commons Open Database License (ODbL) 1.0",
        "licenseUrl": "https://www.openstreetmap.org/copyright",
        "provenance": {
            "osmDatasetVersion": osm.get("datasetVersion"),
            "curatedDatasetVersion": curated.get("datasetVersion"),
            "mergeRule": "VELA_CURATED first; exact normalized name + same category within 25m dedupes OSM",
        },
        "pois": output,
    }
    report = {
        "datasetVersion": dataset["datasetVersion"],
        "totalPois": len(output),
        "sourceCounts": dict(sorted(source_counts.items())),
        "categoryCounts": dict(sorted(category_counts.items())),
        "osmInputPois": len(osm_pois),
        "curatedInputPois": len(curated_pois),
        "dedupedAgainstCurated": len(deduped_against_curated),
        "dedupedExamples": deduped_against_curated[:100],
        "nearCuratedReviewCount": len(near_curated_review),
        "nearCuratedReview": near_curated_review[:500],
    }
    return dataset, report


def main() -> int:
    parser = argparse.ArgumentParser(description="Merge nationwide OSM POIs with Vela curated POIs.")
    parser.add_argument("--osm", type=Path, required=True)
    parser.add_argument("--curated", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--snapshot-date", required=True)
    args = parser.parse_args()

    dataset, report = merge(
        load_dataset(args.osm),
        load_dataset(args.curated),
        args.snapshot_date,
    )
    deterministic_write(args.output, dataset)
    deterministic_write(args.report, report)
    print(f"Production POIs: {report['totalPois']}")
    print(json.dumps(report["sourceCounts"], ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
