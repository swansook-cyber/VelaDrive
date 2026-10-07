from __future__ import annotations

import argparse
import json
import math
import re
import unicodedata
from collections import Counter
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


def _norm(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).split()).casefold()


def _slug(value: str) -> str:
    asciiish = re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")
    return asciiish or re.sub(r"[^0-9]+", "-", value).strip("-") or "province"


def _point_on_segment(x: float, y: float, x1: float, y1: float, x2: float, y2: float) -> bool:
    cross = (y - y1) * (x2 - x1) - (x - x1) * (y2 - y1)
    if abs(cross) > 1e-10:
        return False
    return min(x1, x2) - 1e-10 <= x <= max(x1, x2) + 1e-10 and min(y1, y2) - 1e-10 <= y <= max(y1, y2) + 1e-10


def _point_in_ring(point: tuple[float, float], ring: Sequence[Sequence[float]]) -> bool:
    x, y = point
    if len(ring) < 3:
        return False
    inside = False
    px, py = ring[-1][0], ring[-1][1]
    for coord in ring:
        cx, cy = coord[0], coord[1]
        if _point_on_segment(x, y, px, py, cx, cy):
            return True
        intersects = ((cy > y) != (py > y)) and (
            x < (px - cx) * (y - cy) / ((py - cy) or float("inf")) + cx
        )
        if intersects:
            inside = not inside
        px, py = cx, cy
    return inside


def _polygons(geometry: Mapping[str, Any]) -> list[Any]:
    kind = geometry.get("type")
    coordinates = geometry.get("coordinates")
    if kind == "Polygon":
        return [coordinates]
    if kind == "MultiPolygon":
        return list(coordinates or [])
    return []


def _bbox(polygons: Iterable[Any]) -> tuple[float, float, float, float]:
    xs: list[float] = []
    ys: list[float] = []
    for polygon in polygons:
        for ring in polygon or []:
            for x, y, *_ in ring:
                xs.append(float(x))
                ys.append(float(y))
    if not xs:
        raise ValueError("Province geometry has no coordinates")
    return min(xs), min(ys), max(xs), max(ys)


def _contains(polygons: Sequence[Any], bbox: tuple[float, float, float, float], lat: float, lon: float) -> bool:
    min_lon, min_lat, max_lon, max_lat = bbox
    if not (min_lon <= lon <= max_lon and min_lat <= lat <= max_lat):
        return False
    point = (lon, lat)
    for polygon in polygons:
        if not polygon or not _point_in_ring(point, polygon[0]):
            continue
        if any(_point_in_ring(point, hole) for hole in polygon[1:]):
            continue
        return True
    return False


def _tags(properties: Mapping[str, Any]) -> Mapping[str, Any]:
    nested = properties.get("tags")
    if isinstance(nested, Mapping):
        return nested
    return properties


def _thai_name(tags: Mapping[str, Any]) -> str:
    return str(tags.get("name:th") or tags.get("name") or "").strip()


def _english_name(tags: Mapping[str, Any]) -> str:
    return str(tags.get("name:en") or tags.get("name") or "").strip()


def _province_code(tags: Mapping[str, Any], fallback_name: str) -> str:
    iso = str(tags.get("ISO3166-2") or tags.get("iso3166-2") or "").strip()
    if iso.startswith("TH-"):
        return iso[3:].casefold()
    return _slug(_english_name(tags) or fallback_name)


def load_provinces(path: Path) -> list[dict[str, Any]]:
    root = json.loads(path.read_text(encoding="utf-8"))
    features = root.get("features", []) if isinstance(root, Mapping) else []
    provinces: list[dict[str, Any]] = []
    for feature in features:
        if not isinstance(feature, Mapping):
            continue
        props = feature.get("properties") or {}
        tags = _tags(props if isinstance(props, Mapping) else {})
        if str(tags.get("admin_level", "")).strip() != "4":
            continue
        geometry = feature.get("geometry")
        if not isinstance(geometry, Mapping):
            continue
        polygons = _polygons(geometry)
        if not polygons:
            continue
        name_th = _thai_name(tags)
        name_en = _english_name(tags)
        if not name_th and not name_en:
            continue
        display = name_th or name_en
        provinces.append(
            {
                "code": _province_code(tags, display),
                "nameTh": name_th or display,
                "nameEn": name_en or display,
                "polygons": polygons,
                "bbox": _bbox(polygons),
            }
        )

    deduped: dict[str, dict[str, Any]] = {}
    for province in provinces:
        deduped[province["code"]] = province
    return sorted(deduped.values(), key=lambda item: _norm(item["nameTh"]))


def main() -> int:
    parser = argparse.ArgumentParser(description="Split Vela Thailand POIs into province asset shards.")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--provinces", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--index", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--expected-provinces", type=int, default=77)
    args = parser.parse_args()

    dataset = json.loads(args.dataset.read_text(encoding="utf-8"))
    provinces = load_provinces(args.provinces)
    if len(provinces) != args.expected_provinces:
        raise RuntimeError(f"Expected {args.expected_provinces} provinces, got {len(provinces)}")

    shards: dict[str, list[dict[str, Any]]] = {p["code"]: [] for p in provinces}
    unmatched: list[dict[str, Any]] = []

    for poi in dataset.get("pois", []):
        lat = float(poi["latitude"])
        lon = float(poi["longitude"])
        matches = [
            province
            for province in provinces
            if _contains(province["polygons"], province["bbox"], lat, lon)
        ]
        if len(matches) != 1:
            unmatched.append(
                {
                    "id": poi.get("id"),
                    "name": poi.get("name"),
                    "latitude": lat,
                    "longitude": lon,
                    "matchCount": len(matches),
                }
            )
            continue
        province = matches[0]
        item = dict(poi)
        item["province"] = province["nameTh"]
        shards[province["code"]].append(item)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    index_items: list[dict[str, Any]] = []
    total_written = 0
    for province in provinces:
        code = province["code"]
        records = sorted(
            shards[code],
            key=lambda poi: (
                str(poi.get("category", "")),
                _norm(str(poi.get("name", ""))),
                str(poi.get("id", "")),
            ),
        )
        total_written += len(records)
        filename = f"{code}.json"
        payload = {
            "schemaVersion": dataset.get("schemaVersion", 1),
            "datasetVersion": dataset.get("datasetVersion"),
            "updatedAt": dataset.get("updatedAt"),
            "provinceCode": code,
            "provinceNameTh": province["nameTh"],
            "provinceNameEn": province["nameEn"],
            "pois": records,
        }
        (args.output_dir / filename).write_text(
            json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n",
            encoding="utf-8",
        )
        min_lon, min_lat, max_lon, max_lat = province["bbox"]
        index_items.append(
            {
                "code": code,
                "nameTh": province["nameTh"],
                "nameEn": province["nameEn"],
                "asset": f"poi_provinces/{filename}",
                "count": len(records),
                "bbox": [min_lon, min_lat, max_lon, max_lat],
            }
        )

    index_payload = {
        "schemaVersion": 1,
        "datasetVersion": dataset.get("datasetVersion"),
        "updatedAt": dataset.get("updatedAt"),
        "provinceCount": len(index_items),
        "totalPois": total_written,
        "provinces": index_items,
    }
    args.index.parent.mkdir(parents=True, exist_ok=True)
    args.index.write_text(
        json.dumps(index_payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )

    report = {
        "provinceCount": len(index_items),
        "inputPois": len(dataset.get("pois", [])),
        "writtenPois": total_written,
        "unmatchedPois": len(unmatched),
        "largestProvinces": sorted(
            ({"code": p["code"], "nameTh": p["nameTh"], "count": len(shards[p["code"]])} for p in provinces),
            key=lambda item: item["count"],
            reverse=True,
        )[:10],
        "unmatchedSample": unmatched[:100],
    }
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
