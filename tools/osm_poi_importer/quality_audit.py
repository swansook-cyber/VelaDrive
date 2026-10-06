from __future__ import annotations

import argparse
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from .importer import (
    CLASSIFICATION_TAG_KEYS,
    GeoJsonBoundary,
    OsmElement,
    _address,
    _distance_meters,
    _names,
    _normalized_name,
    _valid_coordinates,
    classify,
    deterministic_json_bytes,
    load_elements,
)


SAMPLE_CATEGORIES = (
    "RESTAURANT",
    "HOTEL",
    "TOURISM",
    "FERRY",
    "HALAL_RESTAURANT",
    "FUEL",
    "HOSPITAL",
    "MOSQUE",
)
DISTANCE_THRESHOLDS_METERS = (10.0, 25.0, 50.0)
SAFE_UNNAMED_FIELDS = ("official_name", "short_name", "brand", "operator")


class UnionFind:
    def __init__(self, size: int) -> None:
        self.parent = list(range(size))
        self.rank = [0] * size

    def find(self, item: int) -> int:
        while self.parent[item] != item:
            self.parent[item] = self.parent[self.parent[item]]
            item = self.parent[item]
        return item

    def union(self, first: int, second: int) -> None:
        first_root = self.find(first)
        second_root = self.find(second)
        if first_root == second_root:
            return
        if self.rank[first_root] < self.rank[second_root]:
            first_root, second_root = second_root, first_root
        self.parent[second_root] = first_root
        if self.rank[first_root] == self.rank[second_root]:
            self.rank[first_root] += 1


def _load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8", errors="strict") as handle:
        return json.load(handle)


def _source_index(elements: Iterable[OsmElement]) -> dict[str, OsmElement]:
    return {element.source_reference: element for element in elements}


def _element_type_counts(records: Sequence[Mapping[str, Any]]) -> dict[str, int]:
    counts = Counter(record["sourceReference"].split("/", 1)[0] for record in records)
    return dict(sorted(counts.items()))


def _missing_language_counts(
    records: Sequence[Mapping[str, Any]],
    sources: Mapping[str, OsmElement],
) -> dict[str, int]:
    missing_thai = 0
    missing_english = 0
    for record in records:
        tags = sources[record["sourceReference"]].tags
        if not tags.get("name:th", "").strip():
            missing_thai += 1
        if not tags.get("name:en", "").strip():
            missing_english += 1
    return {
        "missingThaiNameTag": missing_thai,
        "missingEnglishNameTag": missing_english,
    }


def _duplicate_names(records: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    groups: dict[str, list[Mapping[str, Any]]] = defaultdict(list)
    for record in records:
        groups[_looking_name(record["name"])].append(record)
    duplicate_groups = [group for group in groups.values() if len(group) > 1]
    duplicate_groups.sort(key=lambda group: (-len(group), _looking_name(group[0]["name"])))
    return {
        "definition": "NFKC + case-fold + punctuation/whitespace normalization",
        "groupCount": len(duplicate_groups),
        "recordCount": sum(len(group) for group in duplicate_groups),
        "largestGroupSize": max((len(group) for group in duplicate_groups), default=0),
        "examples": [
            {
                "normalizedName": _looking_name(group[0]["name"]),
                "count": len(group),
                "names": sorted({record["name"] for record in group}),
                "sourceReferences": [record["sourceReference"] for record in group[:20]],
                "categories": sorted({record["category"] for record in group}),
            }
            for group in duplicate_groups[:25]
        ],
    }


def _looking_name(value: str) -> str:
    normalized = _normalized_name(value)
    return " ".join(re.sub(r"[^\w\u0E00-\u0E7F]+", " ", normalized).split())


def _pair_distances_within(
    records: Sequence[Mapping[str, Any]],
    maximum_meters: float,
) -> list[tuple[int, int, float]]:
    pairs: list[tuple[int, int, float]] = []
    maximum_latitude_delta = maximum_meters / 110_574.0
    for first_index, first in enumerate(records):
        latitude = float(first["latitude"])
        longitude_delta_limit = maximum_meters / max(
            1.0,
            111_320.0 * math.cos(math.radians(latitude)),
        )
        for second_index in range(first_index + 1, len(records)):
            second = records[second_index]
            if abs(latitude - float(second["latitude"])) > maximum_latitude_delta:
                continue
            if abs(float(first["longitude"]) - float(second["longitude"])) > longitude_delta_limit:
                continue
            distance = _distance_meters(first, second)
            if distance <= maximum_meters:
                pairs.append((first_index, second_index, distance))
    return pairs


def _cluster_statistics(
    records: Sequence[Mapping[str, Any]],
    pairs: Sequence[tuple[int, int, float]],
    threshold: float,
) -> dict[str, Any]:
    union_find = UnionFind(len(records))
    accepted_pairs = [(first, second, distance) for first, second, distance in pairs if distance <= threshold]
    for first, second, _ in accepted_pairs:
        union_find.union(first, second)
    components: dict[int, list[int]] = defaultdict(list)
    for index in range(len(records)):
        components[union_find.find(index)].append(index)
    clusters = [members for members in components.values() if len(members) > 1]
    clusters.sort(key=lambda members: (-len(members), records[members[0]]["sourceReference"]))
    return {
        "thresholdMeters": int(threshold),
        "pairCount": len(accepted_pairs),
        "clusterCount": len(clusters),
        "recordsInClusters": sum(len(cluster) for cluster in clusters),
        "largestClusterSize": max((len(cluster) for cluster in clusters), default=0),
        "examples": [
            [
                {
                    "name": records[index]["name"],
                    "category": records[index]["category"],
                    "sourceReference": records[index]["sourceReference"],
                }
                for index in cluster[:10]
            ]
            for cluster in clusters[:10]
        ],
    }


def _pre_dedup_candidates(
    elements: Sequence[OsmElement],
    boundary: GeoJsonBoundary,
) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    seen: set[str] = set()
    for element in sorted(elements, key=lambda item: (item.element_type, item.element_id)):
        category = classify(element.tags)
        if category is None or element.source_reference in seen:
            continue
        seen.add(element.source_reference)
        if not _valid_coordinates(element.latitude, element.longitude):
            continue
        assert element.latitude is not None and element.longitude is not None
        if not boundary.contains(element.latitude, element.longitude):
            continue
        name, aliases = _names(element.tags)
        if not name:
            continue
        candidates.append(
            {
                "name": name,
                "alternateNames": aliases,
                "latitude": element.latitude,
                "longitude": element.longitude,
                "category": category,
                "sourceReference": element.source_reference,
            }
        )
    return candidates


def _deduplication_estimates(candidates: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[str, list[Mapping[str, Any]]] = defaultdict(list)
    for candidate in candidates:
        groups[_normalized_name(candidate["name"])].append(candidate)
    results = []
    for threshold in DISTANCE_THRESHOLDS_METERS:
        merge_count = 0
        pair_count = 0
        affected_groups = 0
        examples: list[dict[str, Any]] = []
        for group in groups.values():
            if len(group) < 2:
                continue
            pairs = _pair_distances_within(group, threshold)
            if not pairs:
                continue
            pair_count += len(pairs)
            union_find = UnionFind(len(group))
            for first, second, _ in pairs:
                union_find.union(first, second)
            components: dict[int, list[int]] = defaultdict(list)
            for index in range(len(group)):
                components[union_find.find(index)].append(index)
            merged_components = [component for component in components.values() if len(component) > 1]
            affected_groups += len(merged_components)
            merge_count += sum(len(component) - 1 for component in merged_components)
            for component in merged_components:
                if len(examples) >= 20:
                    break
                examples.append(
                    {
                        "name": group[component[0]]["name"],
                        "records": [group[index]["sourceReference"] for index in component],
                    }
                )
        results.append(
            {
                "thresholdMeters": int(threshold),
                "estimatedMergedRecords": merge_count,
                "nearDuplicatePairCount": pair_count,
                "affectedClusters": affected_groups,
                "examples": examples,
            }
        )
    return results


def _sample_records(records: Sequence[Mapping[str, Any]], count: int) -> list[Mapping[str, Any]]:
    ordered = sorted(records, key=lambda record: record["sourceReference"])
    if len(ordered) <= count:
        return ordered
    indexes = [round(index * (len(ordered) - 1) / (count - 1)) for index in range(count)]
    return [ordered[index] for index in indexes]


def _validate_sample(
    record: Mapping[str, Any],
    sources: Mapping[str, OsmElement],
) -> dict[str, Any]:
    source = sources.get(record["sourceReference"])
    mismatches: list[str] = []
    if source is None:
        mismatches.append("missing_source_element")
        return {"passed": False, "mismatches": mismatches}
    name, _ = _names(source.tags)
    if record["id"] != f"osm:{source.element_type}:{source.element_id}":
        mismatches.append("id")
    if record["name"] != name:
        mismatches.append("name")
    if record["category"] != classify(source.tags):
        mismatches.append("category")
    if abs(float(record["latitude"]) - float(source.latitude or 0.0)) > 0.00000011:
        mismatches.append("latitude")
    if abs(float(record["longitude"]) - float(source.longitude or 0.0)) > 0.00000011:
        mismatches.append("longitude")
    expected_tags = {
        key: source.tags[key] for key in CLASSIFICATION_TAG_KEYS if source.tags.get(key)
    }
    if record.get("sourceTags", {}) != expected_tags:
        mismatches.append("sourceTags")
    expected_optional = {
        "address": _address(source.tags),
        "phone": source.tags.get("contact:phone") or source.tags.get("phone"),
        "province": source.tags.get("addr:province"),
        "district": source.tags.get("addr:district"),
    }
    for field, expected in expected_optional.items():
        if record.get(field) != expected:
            mismatches.append(field)
    return {"passed": not mismatches, "mismatches": mismatches}


def _samples_by_category(
    records: Sequence[Mapping[str, Any]],
    sources: Mapping[str, OsmElement],
) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for category in SAMPLE_CATEGORIES:
        category_records = [record for record in records if record["category"] == category]
        samples = []
        for record in _sample_records(category_records, 10):
            source = sources[record["sourceReference"]]
            samples.append(
                {
                    "id": record["id"],
                    "name": record["name"],
                    "latitude": record["latitude"],
                    "longitude": record["longitude"],
                    "sourceReference": record["sourceReference"],
                    "sourceTags": record.get("sourceTags", {}),
                    "sourceNameTags": {
                        key: source.tags[key]
                        for key in ("name", "name:th", "name:en", "alt_name", "short_name")
                        if source.tags.get(key)
                    },
                    "validation": _validate_sample(record, sources),
                }
            )
        result[category] = {
            "categoryCount": len(category_records),
            "sampleCount": len(samples),
            "validatedCount": sum(sample["validation"]["passed"] for sample in samples),
            "samples": samples,
        }
    return result


def _unnamed_analysis(
    quarantine: Sequence[Mapping[str, Any]],
    sources: Mapping[str, OsmElement],
) -> dict[str, Any]:
    unnamed = [record for record in quarantine if record.get("reason") == "blank_name"]
    eligible: list[dict[str, Any]] = []
    field_counts = Counter()
    for record in unnamed:
        source = sources.get(record["sourceReference"])
        if source is None:
            continue
        candidates = {
            field: source.tags[field].strip()
            for field in SAFE_UNNAMED_FIELDS
            if source.tags.get(field, "").strip()
        }
        for field in candidates:
            field_counts[field] += 1
        if candidates:
            selected_field = next(field for field in SAFE_UNNAMED_FIELDS if field in candidates)
            eligible.append(
                {
                    "sourceReference": source.source_reference,
                    "category": classify(source.tags),
                    "candidateField": selected_field,
                    "candidateValue": candidates[selected_field],
                    "allStructuredCandidates": candidates,
                }
            )
    eligible.sort(key=lambda item: item["sourceReference"])
    return {
        "blankNameQuarantineCount": len(unnamed),
        "withStructuredDisplayNameCandidate": len(eligible),
        "withoutStructuredDisplayNameCandidate": len(unnamed) - len(eligible),
        "candidateFieldOccurrences": dict(sorted(field_counts.items())),
        "examples": eligible[:30],
        "note": "Candidates are reported only; none are promoted automatically.",
    }


def _category_by_district(records: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    counts: dict[str, Counter[str]] = defaultdict(Counter)
    for record in records:
        district = record.get("district")
        if district:
            counts[district][record["category"]] += 1
    return {
        "recordsWithDistrict": sum(sum(category.values()) for category in counts.values()),
        "districts": {
            district: dict(sorted(category_counts.items()))
            for district, category_counts in sorted(counts.items())
        },
    }


def _mapping_signatures(records: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    signatures: dict[str, Counter[str]] = defaultdict(Counter)
    for record in records:
        signature = ";".join(
            f"{key}={value}" for key, value in sorted(record.get("sourceTags", {}).items())
        )
        signatures[record["category"]][signature] += 1
    return {
        category: dict(sorted(values.items(), key=lambda item: (-item[1], item[0])))
        for category, values in sorted(signatures.items())
    }


def build_audit(
    dataset: Mapping[str, Any],
    elements: Sequence[OsmElement],
    quarantine: Sequence[Mapping[str, Any]],
    boundary: GeoJsonBoundary,
) -> dict[str, Any]:
    records = dataset["pois"]
    sources = _source_index(elements)
    nearby_pairs = _pair_distances_within(records, max(DISTANCE_THRESHOLDS_METERS))
    samples = _samples_by_category(records, sources)
    return {
        "datasetVersion": dataset["datasetVersion"],
        "poiCount": len(records),
        "missingAddress": sum(not record.get("address") for record in records),
        "missingPhone": sum(not record.get("phone") for record in records),
        **_missing_language_counts(records, sources),
        "elementTypes": _element_type_counts(records),
        "verifiedStatus": dict(
            sorted(Counter("verified" if record.get("verified") else "unverified" for record in records).items())
        ),
        "duplicateLookingNames": _duplicate_names(records),
        "coordinateClusters": [
            _cluster_statistics(records, nearby_pairs, threshold)
            for threshold in DISTANCE_THRESHOLDS_METERS
        ],
        "deduplicationThresholdEstimates": _deduplication_estimates(
            _pre_dedup_candidates(elements, boundary)
        ),
        "categoriesByDistrict": _category_by_district(records),
        "mappingSignatures": _mapping_signatures(records),
        "sampleAudit": samples,
        "sampleValidation": {
            "sampled": sum(value["sampleCount"] for value in samples.values()),
            "passed": sum(value["validatedCount"] for value in samples.values()),
        },
        "unnamedRecords": _unnamed_analysis(quarantine, sources),
    }


def _markdown(audit: Mapping[str, Any]) -> str:
    lines = [
        "# Krabi OSM POI quality audit",
        "",
        f"Dataset: `{audit['datasetVersion']}`",
        "",
        "## Summary",
        "",
        f"- POIs: {audit['poiCount']}",
        f"- Missing address: {audit['missingAddress']}",
        f"- Missing phone: {audit['missingPhone']}",
        f"- Missing `name:th`: {audit['missingThaiNameTag']}",
        f"- Missing `name:en`: {audit['missingEnglishNameTag']}",
        f"- Element types: {audit['elementTypes']}",
        f"- Verified status: {audit['verifiedStatus']}",
        f"- Sample validation: {audit['sampleValidation']['passed']}/{audit['sampleValidation']['sampled']} passed",
        "",
        "## Coordinate clusters",
        "",
        "| Distance | Pairs | Clusters | Records in clusters | Largest cluster |",
        "| ---: | ---: | ---: | ---: | ---: |",
    ]
    for item in audit["coordinateClusters"]:
        lines.append(
            f"| {item['thresholdMeters']}m | {item['pairCount']} | {item['clusterCount']} | "
            f"{item['recordsInClusters']} | {item['largestClusterSize']} |"
        )
    lines.extend(
        [
            "",
            "## Deduplication estimates",
            "",
            "| Distance | Estimated merged records | Near-duplicate pairs | Affected clusters |",
            "| ---: | ---: | ---: | ---: |",
        ]
    )
    for item in audit["deduplicationThresholdEstimates"]:
        lines.append(
            f"| {item['thresholdMeters']}m | {item['estimatedMergedRecords']} | "
            f"{item['nearDuplicatePairCount']} | {item['affectedClusters']} |"
        )
    unnamed = audit["unnamedRecords"]
    lines.extend(
        [
            "",
            "## Unnamed records",
            "",
            f"- Blank-name quarantined: {unnamed['blankNameQuarantineCount']}",
            f"- Structured display-name candidates: {unnamed['withStructuredDisplayNameCandidate']}",
            f"- No structured candidate: {unnamed['withoutStructuredDisplayNameCandidate']}",
            f"- Candidate fields: {unnamed['candidateFieldOccurrences']}",
            "",
            "## Audited samples",
            "",
        ]
    )
    for category, value in audit["sampleAudit"].items():
        lines.extend(
            [
                f"### {category}",
                "",
                "| Name | OSM reference | Tags | Validation |",
                "| --- | --- | --- | --- |",
            ]
        )
        for sample in value["samples"]:
            tags = ", ".join(
                f"{key}={tag_value}" for key, tag_value in sample["sourceTags"].items()
            )
            validation = "PASS" if sample["validation"]["passed"] else "FAIL"
            safe_name = str(sample["name"]).replace("|", "\\|")
            lines.append(
                f"| {safe_name} | `{sample['sourceReference']}` | {tags} | {validation} |"
            )
        lines.append("")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit a generated Vela OSM POI dataset.")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--boundary", type=Path, required=True)
    parser.add_argument("--quarantine", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    args = parser.parse_args()
    audit = build_audit(
        _load_json(args.dataset),
        load_elements(args.source),
        _load_json(args.quarantine),
        GeoJsonBoundary.from_path(args.boundary),
    )
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_bytes(deterministic_json_bytes(audit))
    args.markdown_output.write_text(_markdown(audit), encoding="utf-8")
    print(f"Audited {audit['poiCount']} POIs")
    print(
        f"Validated {audit['sampleValidation']['passed']}/"
        f"{audit['sampleValidation']['sampled']} samples"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
