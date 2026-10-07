from __future__ import annotations

import hashlib
import json
import math
import re
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence


OSM_ATTRIBUTION = "© OpenStreetMap contributors"
OSM_LICENSE = "Open Data Commons Open Database License (ODbL) 1.0"
OSM_LICENSE_URL = "https://www.openstreetmap.org/copyright"
OSM_ELEMENT_URL = "https://www.openstreetmap.org/{element_type}/{element_id}"
SUPPORTED_SCHEMA_VERSION = 1
IMPORTER_RULES_VERSION = 3
DEDUPLICATION_DISTANCE_METERS = 10.0
EARTH_RADIUS_METERS = 6_371_000.0

ELEMENT_TYPE_ORDER = {"node": 0, "way": 1, "relation": 2}
CLASSIFICATION_TAG_KEYS = (
    "amenity",
    "healthcare",
    "tourism",
    "shop",
    "craft",
    "aeroway",
    "man_made",
    "route",
    "office",
    "religion",
    "diet:halal",
    "halal",
    "cuisine",
    "brand",
    "operator",
)
NAME_TAG_KEYS = ("name:th", "name", "name:en", "alt_name", "short_name")


@dataclass(frozen=True)
class OsmElement:
    element_type: str
    element_id: int
    latitude: float | None
    longitude: float | None
    tags: Mapping[str, str]

    @property
    def source_reference(self) -> str:
        return f"{self.element_type}/{self.element_id}"


@dataclass
class ImportResult:
    dataset: dict[str, Any]
    report: dict[str, Any]
    quarantine: list[dict[str, Any]]


@dataclass
class ImportCounters:
    input_elements: int = 0
    skipped_unmapped: int = 0
    rejected: int = 0
    deduplicated: int = 0
    rejection_reasons: dict[str, int] = field(default_factory=dict)

    def reject(self, reason: str) -> None:
        self.rejected += 1
        self.rejection_reasons[reason] = self.rejection_reasons.get(reason, 0) + 1


class GeoJsonBoundary:
    def __init__(self, geojson: Mapping[str, Any]) -> None:
        geometry = geojson.get("geometry", geojson)
        geometry_type = geometry.get("type")
        coordinates = geometry.get("coordinates")
        if geometry_type == "Polygon":
            polygons = [coordinates]
        elif geometry_type == "MultiPolygon":
            polygons = coordinates
        else:
            raise ValueError("Boundary must be a GeoJSON Polygon or MultiPolygon")
        if not polygons:
            raise ValueError("Boundary contains no polygons")
        self._polygons = polygons

    @classmethod
    def from_path(cls, path: Path) -> "GeoJsonBoundary":
        with path.open("r", encoding="utf-8", errors="strict") as handle:
            return cls(json.load(handle))

    def contains(self, latitude: float, longitude: float) -> bool:
        point = (longitude, latitude)
        for polygon in self._polygons:
            if not polygon or not _point_in_ring(point, polygon[0]):
                continue
            if any(_point_in_ring(point, hole) for hole in polygon[1:]):
                continue
            return True
        return False


def _point_in_ring(point: tuple[float, float], ring: Sequence[Sequence[float]]) -> bool:
    x, y = point
    inside = False
    if len(ring) < 3:
        return False
    previous_x, previous_y = ring[-1][0], ring[-1][1]
    for coordinate in ring:
        current_x, current_y = coordinate[0], coordinate[1]
        if _point_on_segment(x, y, previous_x, previous_y, current_x, current_y):
            return True
        intersects = ((current_y > y) != (previous_y > y)) and (
            x
            < (previous_x - current_x)
            * (y - current_y)
            / ((previous_y - current_y) or float("inf"))
            + current_x
        )
        if intersects:
            inside = not inside
        previous_x, previous_y = current_x, current_y
    return inside


def _point_on_segment(
    x: float,
    y: float,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
) -> bool:
    cross = (y - y1) * (x2 - x1) - (x - x1) * (y2 - y1)
    if abs(cross) > 1e-10:
        return False
    return min(x1, x2) - 1e-10 <= x <= max(x1, x2) + 1e-10 and min(
        y1, y2
    ) - 1e-10 <= y <= max(y1, y2) + 1e-10


def load_elements(input_path: Path) -> list[OsmElement]:
    suffixes = [suffix.lower() for suffix in input_path.suffixes]
    if suffixes[-2:] == [".osm", ".pbf"] or input_path.suffix.lower() == ".pbf":
        return _load_pbf(input_path)
    if input_path.suffix.lower() == ".json":
        return _load_overpass_json(input_path)
    if input_path.suffix.lower() in {".osm", ".xml"}:
        return _load_osm_xml(input_path)
    raise ValueError(f"Unsupported OSM input format: {input_path.name}")


def _load_overpass_json(path: Path) -> list[OsmElement]:
    try:
        with path.open("r", encoding="utf-8", errors="strict") as handle:
            root = json.load(handle)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Malformed Overpass JSON or UTF-8: {error}") from error
    elements = root.get("elements")
    if not isinstance(elements, list):
        raise ValueError("Overpass JSON must contain an elements array")
    parsed: list[OsmElement] = []
    for item in elements:
        element_type = item.get("type")
        element_id = item.get("id")
        if element_type not in ELEMENT_TYPE_ORDER or not isinstance(element_id, int):
            continue
        latitude, longitude = _json_coordinates(item)
        parsed.append(
            OsmElement(
                element_type=element_type,
                element_id=element_id,
                latitude=latitude,
                longitude=longitude,
                tags=_clean_tags(item.get("tags", {})),
            )
        )
    return parsed


def _json_coordinates(item: Mapping[str, Any]) -> tuple[float | None, float | None]:
    if item.get("type") == "node":
        return _as_float(item.get("lat")), _as_float(item.get("lon"))
    center = item.get("center")
    if isinstance(center, Mapping):
        return _as_float(center.get("lat")), _as_float(center.get("lon"))
    geometry = item.get("geometry")
    if isinstance(geometry, list):
        coordinates = [
            (_as_float(point.get("lat")), _as_float(point.get("lon")))
            for point in geometry
            if isinstance(point, Mapping)
        ]
        return _representative_point(coordinates)
    return None, None


def _load_osm_xml(path: Path) -> list[OsmElement]:
    node_coordinates: dict[int, tuple[float, float]] = {}
    way_centers: dict[int, tuple[float, float]] = {}
    parsed: list[OsmElement] = []
    try:
        iterator = ET.iterparse(path, events=("end",))
        for _, element in iterator:
            kind = _local_name(element.tag)
            if kind == "node":
                element_id = int(element.attrib["id"])
                latitude = float(element.attrib["lat"])
                longitude = float(element.attrib["lon"])
                node_coordinates[element_id] = (latitude, longitude)
                tags = _xml_tags(element)
                if tags:
                    parsed.append(OsmElement("node", element_id, latitude, longitude, tags))
                element.clear()
            elif kind == "way":
                element_id = int(element.attrib["id"])
                references = [
                    int(child.attrib["ref"])
                    for child in element
                    if _local_name(child.tag) == "nd" and "ref" in child.attrib
                ]
                center = _representative_point(
                    [node_coordinates.get(reference, (None, None)) for reference in references]
                )
                if center[0] is not None and center[1] is not None:
                    way_centers[element_id] = (center[0], center[1])
                tags = _xml_tags(element)
                if tags:
                    parsed.append(OsmElement("way", element_id, center[0], center[1], tags))
                element.clear()
            elif kind == "relation":
                element_id = int(element.attrib["id"])
                member_points: list[tuple[float | None, float | None]] = []
                for child in element:
                    if _local_name(child.tag) != "member" or "ref" not in child.attrib:
                        continue
                    reference = int(child.attrib["ref"])
                    if child.attrib.get("type") == "node":
                        member_points.append(node_coordinates.get(reference, (None, None)))
                    elif child.attrib.get("type") == "way":
                        member_points.append(way_centers.get(reference, (None, None)))
                center = _representative_point(member_points)
                tags = _xml_tags(element)
                if tags:
                    parsed.append(
                        OsmElement("relation", element_id, center[0], center[1], tags)
                    )
                element.clear()
    except (ET.ParseError, UnicodeDecodeError) as error:
        raise ValueError(f"Malformed OSM XML or UTF-8: {error}") from error
    return parsed


def _load_pbf(path: Path) -> list[OsmElement]:
    try:
        import osmium  # type: ignore[import-not-found]
    except ImportError as error:
        raise RuntimeError(
            ".osm.pbf input requires the optional 'osmium' Python package"
        ) from error

    class Handler(osmium.SimpleHandler):  # type: ignore[misc, name-defined]
        def __init__(self) -> None:
            super().__init__()
            self.elements: list[OsmElement] = []
            self.node_coordinates: dict[int, tuple[float, float]] = {}
            self.way_centers: dict[int, tuple[float, float]] = {}

        def node(self, node: Any) -> None:
            if node.location.valid():
                point = (float(node.location.lat), float(node.location.lon))
                self.node_coordinates[int(node.id)] = point
            else:
                point = (None, None)
            tags = _clean_tags(dict(node.tags))
            if tags and classify(tags) is not None:
                self.elements.append(OsmElement("node", int(node.id), *point, tags))

        def way(self, way: Any) -> None:
            points = [
                (float(node.lat), float(node.lon))
                for node in way.nodes
                if node.location.valid()
            ]
            center = _representative_point(points)
            if center[0] is not None and center[1] is not None:
                self.way_centers[int(way.id)] = (center[0], center[1])
            tags = _clean_tags(dict(way.tags))
            if tags and classify(tags) is not None:
                self.elements.append(OsmElement("way", int(way.id), *center, tags))

        def relation(self, relation: Any) -> None:
            points: list[tuple[float | None, float | None]] = []
            for member in relation.members:
                if member.type == "n":
                    points.append(self.node_coordinates.get(int(member.ref), (None, None)))
                elif member.type == "w":
                    points.append(self.way_centers.get(int(member.ref), (None, None)))
            center = _representative_point(points)
            tags = _clean_tags(dict(relation.tags))
            if tags and classify(tags) is not None:
                self.elements.append(OsmElement("relation", int(relation.id), *center, tags))

    handler = Handler()
    handler.apply_file(str(path), locations=True, idx="flex_mem")
    return handler.elements


def _xml_tags(element: ET.Element) -> dict[str, str]:
    return _clean_tags(
        {
            child.attrib["k"]: child.attrib["v"]
            for child in element
            if _local_name(child.tag) == "tag"
            and "k" in child.attrib
            and "v" in child.attrib
        }
    )


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _clean_tags(tags: Mapping[str, Any]) -> dict[str, str]:
    return {
        str(key).strip(): str(value).strip()
        for key, value in tags.items()
        if str(key).strip() and str(value).strip()
    }


def _as_float(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _representative_point(
    coordinates: Iterable[tuple[float | None, float | None]],
) -> tuple[float | None, float | None]:
    valid = [
        (latitude, longitude)
        for latitude, longitude in coordinates
        if latitude is not None
        and longitude is not None
        and math.isfinite(latitude)
        and math.isfinite(longitude)
    ]
    if not valid:
        return None, None
    return (
        sum(point[0] for point in valid) / len(valid),
        sum(point[1] for point in valid) / len(valid),
    )


def classify(tags: Mapping[str, str]) -> str | None:
    amenity = tags.get("amenity")
    tourism = tags.get("tourism")
    shop = tags.get("shop")

    if amenity in {"restaurant", "fast_food", "food_court", "cafe"}:
        return "HALAL_RESTAURANT" if _is_explicitly_halal(tags) else "RESTAURANT"
    if amenity == "fuel":
        return "FUEL"
    if amenity == "hospital" or tags.get("healthcare") == "hospital":
        return "HOSPITAL"
    if tourism in {"hotel", "hostel", "guest_house", "motel", "resort", "chalet"}:
        return "HOTEL"
    if amenity == "marketplace":
        return "MARKET"
    if shop in {"mall", "department_store"}:
        return "SHOPPING"
    if amenity == "police":
        return "POLICE"
    if tags.get("aeroway") in {"aerodrome", "terminal"}:
        return "AIRPORT"
    if amenity == "bus_station":
        return "BUS_TERMINAL"
    if tourism in {
        "attraction",
        "aquarium",
        "artwork",
        "gallery",
        "museum",
        "theme_park",
        "viewpoint",
        "zoo",
    }:
        return "TOURISM"
    if shop == "convenience":
        return "CONVENIENCE_STORE"
    if shop == "car_repair" or tags.get("craft") == "car_repair":
        return "AUTO_SERVICE"
    if shop == "tyres":
        return "TIRE_SERVICE"
    if amenity == "bank":
        return "BANK"
    if amenity == "atm":
        return "ATM"
    if amenity == "charging_station":
        return "EV_CHARGER"
    if tags.get("man_made") == "pier":
        return "PIER"
    if amenity == "ferry_terminal":
        return "FERRY"
    if amenity == "place_of_worship" and tags.get("religion") == "muslim":
        return "MOSQUE"
    if tags.get("office") == "government" or amenity in {"townhall", "courthouse"}:
        return "GOVERNMENT"
    return None


def _is_explicitly_halal(tags: Mapping[str, str]) -> bool:
    if tags.get("diet:halal", "").casefold() in {"yes", "only"}:
        return True
    if tags.get("halal", "").casefold() in {"yes", "only"}:
        return True
    cuisine_values = {
        value.strip().casefold()
        for value in re.split(r"[;,]", tags.get("cuisine", ""))
        if value.strip()
    }
    return "halal" in cuisine_values


def import_elements(
    elements: Iterable[OsmElement],
    *,
    boundary: GeoJsonBoundary | None,
    snapshot_date: str,
    input_sha256: str,
    boundary_sha256: str,
    source_url: str,
    boundary_name: str,
    boundary_reference: str,
    input_format: str,
    source_data_timestamp: str | None = None,
    dataset_slug: str = "krabi",
) -> ImportResult:
    date.fromisoformat(snapshot_date)
    counters = ImportCounters()
    quarantine: list[dict[str, Any]] = []
    candidates: list[dict[str, Any]] = []
    normalized_slug = re.sub(r"[^a-z0-9-]+", "-", dataset_slug.casefold()).strip("-")
    if not normalized_slug:
        raise ValueError("dataset_slug must contain at least one alphanumeric character")
    dataset_version = (
        f"osm-{normalized_slug}-{snapshot_date}-v{IMPORTER_RULES_VERSION}-{input_sha256[:12]}"
    )

    ordered_elements = sorted(
        elements,
        key=lambda element: (
            ELEMENT_TYPE_ORDER[element.element_type],
            element.element_id,
        ),
    )
    seen_source_references: set[str] = set()
    for element in ordered_elements:
        counters.input_elements += 1
        category = classify(element.tags)
        if category is None:
            counters.skipped_unmapped += 1
            continue
        if element.source_reference in seen_source_references:
            _quarantine(element, "duplicate_source_id", counters, quarantine)
            continue
        seen_source_references.add(element.source_reference)
        if not _valid_coordinates(element.latitude, element.longitude):
            _quarantine(element, "invalid_coordinates", counters, quarantine)
            continue
        assert element.latitude is not None and element.longitude is not None
        if boundary is not None and not boundary.contains(element.latitude, element.longitude):
            _quarantine(element, "outside_boundary", counters, quarantine)
            continue
        name, aliases = _names(element.tags)
        if not name:
            _quarantine(element, "blank_name", counters, quarantine)
            continue
        candidates.append(
            _poi_record(
                element,
                name=name,
                aliases=aliases,
                category=category,
                dataset_version=dataset_version,
                snapshot_date=snapshot_date,
            )
        )

    canonical: list[dict[str, Any]] = []
    for candidate in candidates:
        duplicate = next(
            (
                record
                for record in canonical
                if _normalized_name(record["name"]) == _normalized_name(candidate["name"])
                and _distance_meters(record, candidate) <= DEDUPLICATION_DISTANCE_METERS
            ),
            None,
        )
        if duplicate is None:
            canonical.append(candidate)
            continue
        counters.deduplicated += 1
        merged_aliases = _unique_text(
            duplicate.get("alternateNames", [])
            + [candidate["name"]]
            + candidate.get("alternateNames", [])
        )
        duplicate["alternateNames"] = [
            alias
            for alias in merged_aliases
            if _normalized_name(alias) != _normalized_name(duplicate["name"])
        ]

    canonical.sort(
        key=lambda record: (
            record["category"],
            _normalized_name(record["name"]),
            record["sourceReference"],
        )
    )
    category_counts: dict[str, int] = {}
    for record in canonical:
        category = record["category"]
        category_counts[category] = category_counts.get(category, 0) + 1

    provenance = {
        "source": "OpenStreetMap",
        "sourceUrl": source_url,
        "inputFormat": input_format,
        "inputSha256": input_sha256,
        "boundaryName": boundary_name,
        "boundaryReference": boundary_reference,
        "boundarySha256": boundary_sha256,
        "generator": f"VelaDrive OSM POI Importer v{IMPORTER_RULES_VERSION}",
        "containsProprietaryDerivedData": False,
    }
    if source_data_timestamp:
        provenance["sourceDataTimestamp"] = source_data_timestamp

    dataset = {
        "schemaVersion": SUPPORTED_SCHEMA_VERSION,
        "datasetVersion": dataset_version,
        "updatedAt": snapshot_date,
        "attribution": OSM_ATTRIBUTION,
        "license": OSM_LICENSE,
        "licenseUrl": OSM_LICENSE_URL,
        "provenance": provenance,
        "pois": canonical,
    }
    report = {
        "datasetVersion": dataset_version,
        "inputElements": counters.input_elements,
        "outputPois": len(canonical),
        "categoryCounts": dict(sorted(category_counts.items())),
        "rejectedCount": counters.rejected,
        "rejectionReasons": dict(sorted(counters.rejection_reasons.items())),
        "deduplicatedCount": counters.deduplicated,
        "skippedUnmappedCount": counters.skipped_unmapped,
        "attribution": OSM_ATTRIBUTION,
        "license": OSM_LICENSE,
        "licenseUrl": OSM_LICENSE_URL,
        "inputSha256": input_sha256,
        "boundarySha256": boundary_sha256,
    }
    if source_data_timestamp:
        report["sourceDataTimestamp"] = source_data_timestamp
    return ImportResult(dataset=dataset, report=report, quarantine=quarantine)


def _quarantine(
    element: OsmElement,
    reason: str,
    counters: ImportCounters,
    quarantine: list[dict[str, Any]],
) -> None:
    counters.reject(reason)
    quarantine.append(
        {
            "sourceReference": element.source_reference,
            "reason": reason,
            "latitude": element.latitude,
            "longitude": element.longitude,
        }
    )


def _valid_coordinates(latitude: float | None, longitude: float | None) -> bool:
    return (
        latitude is not None
        and longitude is not None
        and math.isfinite(latitude)
        and math.isfinite(longitude)
        and -90.0 <= latitude <= 90.0
        and -180.0 <= longitude <= 180.0
    )


def _names(tags: Mapping[str, str]) -> tuple[str | None, list[str]]:
    primary = tags.get("name:th") or tags.get("name") or tags.get("name:en")
    if not primary or not primary.strip():
        return None, []
    aliases: list[str] = []
    for key in NAME_TAG_KEYS:
        value = tags.get(key)
        if not value:
            continue
        aliases.extend(part.strip() for part in value.split(";") if part.strip())
    return primary.strip(), [
        alias
        for alias in _unique_text(aliases)
        if _normalized_name(alias) != _normalized_name(primary)
    ]


def _poi_record(
    element: OsmElement,
    *,
    name: str,
    aliases: list[str],
    category: str,
    dataset_version: str,
    snapshot_date: str,
) -> dict[str, Any]:
    tags = element.tags
    assert element.latitude is not None and element.longitude is not None
    record: dict[str, Any] = {
        "id": f"osm:{element.element_type}:{element.element_id}",
        "name": name,
        "alternateNames": aliases,
        "category": category,
        "latitude": round(element.latitude, 7),
        "longitude": round(element.longitude, 7),
        "source": "OPENSTREETMAP",
        "sourceReference": element.source_reference,
        "sourceUrl": OSM_ELEMENT_URL.format(
            element_type=element.element_type,
            element_id=element.element_id,
        ),
        "sourceTags": {
            key: tags[key] for key in CLASSIFICATION_TAG_KEYS if tags.get(key)
        },
        "verified": False,
        "updatedAt": snapshot_date,
        "datasetVersion": dataset_version,
    }
    optional_fields = {
        "address": _address(tags),
        "phone": tags.get("contact:phone") or tags.get("phone"),
        "province": tags.get("addr:province"),
        "district": tags.get("addr:district"),
    }
    for key, value in optional_fields.items():
        if value and value.strip():
            record[key] = value.strip()
    return record


def _address(tags: Mapping[str, str]) -> str | None:
    parts = [
        tags.get("addr:housenumber"),
        tags.get("addr:street"),
        tags.get("addr:subdistrict"),
        tags.get("addr:district"),
        tags.get("addr:province"),
        tags.get("addr:postcode"),
    ]
    clean = [part.strip() for part in parts if part and part.strip()]
    return ", ".join(clean) if clean else None


def _normalized_name(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).split()).casefold()


def _unique_text(values: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        normalized = _normalized_name(value)
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(value.strip())
    return result


def _distance_meters(first: Mapping[str, Any], second: Mapping[str, Any]) -> float:
    latitude1 = math.radians(float(first["latitude"]))
    latitude2 = math.radians(float(second["latitude"]))
    delta_latitude = math.radians(float(second["latitude"]) - float(first["latitude"]))
    delta_longitude = math.radians(float(second["longitude"]) - float(first["longitude"]))
    a = (
        math.sin(delta_latitude / 2) ** 2
        + math.cos(latitude1)
        * math.cos(latitude2)
        * math.sin(delta_longitude / 2) ** 2
    )
    return EARTH_RADIUS_METERS * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def source_data_timestamp(path: Path) -> str | None:
    if path.suffix.lower() != ".json":
        return None
    with path.open("r", encoding="utf-8", errors="strict") as handle:
        root = json.load(handle)
    osm3s = root.get("osm3s")
    if not isinstance(osm3s, Mapping):
        return None
    timestamp = osm3s.get("timestamp_osm_base")
    return str(timestamp).strip() if timestamp else None


def deterministic_json_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def write_import_outputs(
    result: ImportResult,
    *,
    output_path: Path,
    report_path: Path,
    quarantine_path: Path,
) -> str:
    output_bytes = deterministic_json_bytes(result.dataset)
    output_sha256 = hashlib.sha256(output_bytes).hexdigest()
    report = dict(result.report)
    report["outputFile"] = output_path.name
    report["outputSha256"] = output_sha256
    output_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    quarantine_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(output_bytes)
    report_path.write_bytes(deterministic_json_bytes(report))
    quarantine_path.write_bytes(deterministic_json_bytes(result.quarantine))
    return output_sha256

