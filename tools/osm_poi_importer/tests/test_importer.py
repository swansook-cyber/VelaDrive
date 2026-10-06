import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.osm_poi_importer.importer import (
    GeoJsonBoundary,
    OsmElement,
    classify,
    deterministic_json_bytes,
    import_elements,
    load_elements,
)


BOUNDARY = GeoJsonBoundary(
    {
        "type": "Polygon",
        "coordinates": [[[99.0, 7.0], [101.0, 7.0], [101.0, 9.0], [99.0, 9.0], [99.0, 7.0]]],
    }
)


def element(element_id=1, *, tags=None, latitude=8.0, longitude=100.0, kind="node"):
    return OsmElement(kind, element_id, latitude, longitude, tags or {})


def run_import(*elements):
    return import_elements(
        elements,
        boundary=BOUNDARY,
        snapshot_date="2026-10-06",
        input_sha256="a" * 64,
        boundary_sha256="b" * 64,
        source_url="https://example.test/osm",
        boundary_name="TEST ONLY",
        boundary_reference="relation/1",
        input_format="json",
    )


class ImporterTests(unittest.TestCase):
    def test_thai_name_is_primary_and_english_name_is_alias(self):
        result = run_import(
            element(tags={"amenity": "hospital", "name": "Krabi Test Hospital", "name:th": "โรงพยาบาลทดสอบ", "name:en": "Test Hospital"})
        )
        poi = result.dataset["pois"][0]
        self.assertEqual("โรงพยาบาลทดสอบ", poi["name"])
        self.assertEqual(["Krabi Test Hospital", "Test Hospital"], poi["alternateNames"])

    def test_category_mapping(self):
        self.assertEqual("FUEL", classify({"amenity": "fuel"}))
        self.assertEqual("TIRE_SERVICE", classify({"shop": "tyres"}))
        self.assertEqual("MOSQUE", classify({"amenity": "place_of_worship", "religion": "muslim"}))
        self.assertEqual("FERRY", classify({"amenity": "ferry_terminal"}))
        self.assertEqual("PIER", classify({"man_made": "pier"}))
        self.assertIsNone(classify({"route": "ferry"}))
        self.assertIsNone(classify({"shop": "yes"}))

    def test_explicit_halal_classification(self):
        self.assertEqual("HALAL_RESTAURANT", classify({"amenity": "restaurant", "diet:halal": "yes"}))
        self.assertEqual("HALAL_RESTAURANT", classify({"amenity": "restaurant", "diet:halal": "only"}))
        self.assertEqual("HALAL_RESTAURANT", classify({"amenity": "cafe", "cuisine": "thai;halal"}))

    def test_halal_is_not_inferred_from_name(self):
        tags = {"amenity": "restaurant", "name": "Muslim Halal Restaurant Name"}
        self.assertEqual("RESTAURANT", classify(tags))

    def test_invalid_and_outside_coordinates_are_quarantined(self):
        result = run_import(
            element(1, tags={"amenity": "fuel", "name": "Invalid"}, latitude=91.0),
            element(2, tags={"amenity": "fuel", "name": "Outside"}, latitude=10.0),
        )
        self.assertEqual(2, result.report["rejectedCount"])
        self.assertEqual({"invalid_coordinates": 1, "outside_boundary": 1}, result.report["rejectionReasons"])

    def test_stable_osm_ids_and_provenance_are_retained(self):
        result = run_import(element(123, kind="way", tags={"amenity": "bank", "name": "Test Bank"}))
        poi = result.dataset["pois"][0]
        self.assertEqual("osm:way:123", poi["id"])
        self.assertEqual("way/123", poi["sourceReference"])
        self.assertEqual("OPENSTREETMAP", poi["source"])
        self.assertEqual("https://www.openstreetmap.org/way/123", poi["sourceUrl"])
        self.assertEqual({"amenity": "bank"}, poi["sourceTags"])
        self.assertEqual("© OpenStreetMap contributors", result.dataset["attribution"])

    def test_source_snapshot_timestamp_is_retained(self):
        result = import_elements(
            [element(tags={"amenity": "atm", "name": "Test ATM"})],
            boundary=BOUNDARY,
            snapshot_date="2026-10-06",
            input_sha256="a" * 64,
            boundary_sha256="b" * 64,
            source_url="https://example.test/osm",
            boundary_name="TEST ONLY",
            boundary_reference="relation/1",
            input_format="json",
            source_data_timestamp="2026-10-06T08:44:35Z",
        )
        self.assertEqual(
            "2026-10-06T08:44:35Z",
            result.dataset["provenance"]["sourceDataTimestamp"],
        )
        self.assertEqual(
            "2026-10-06T08:44:35Z",
            result.report["sourceDataTimestamp"],
        )

    def test_deduplication_keeps_canonical_record_and_alias(self):
        result = run_import(
            element(1, tags={"amenity": "fuel", "name": "Test Fuel", "name:en": "English Fuel"}),
            element(2, kind="way", latitude=8.00005, tags={"amenity": "fuel", "name": "test   fuel", "name:th": "Test Fuel", "alt_name": "Alternate Fuel"}),
        )
        self.assertEqual(1, len(result.dataset["pois"]))
        self.assertEqual(1, result.report["deduplicatedCount"])
        self.assertIn("Alternate Fuel", result.dataset["pois"][0]["alternateNames"])

    def test_same_normalized_name_more_than_ten_metres_apart_is_not_merged(self):
        result = run_import(
            element(1, tags={"amenity": "fuel", "name": "Test Fuel"}),
            element(
                2,
                kind="way",
                latitude=8.000135,
                tags={"amenity": "fuel", "name": "test   fuel"},
            ),
        )
        self.assertEqual(2, len(result.dataset["pois"]))
        self.assertEqual(0, result.report["deduplicatedCount"])

    def test_duplicate_source_id_is_quarantined(self):
        duplicate = element(1, tags={"amenity": "fuel", "name": "Test Fuel"})
        result = run_import(duplicate, duplicate)
        self.assertEqual(1, len(result.dataset["pois"]))
        self.assertEqual({"duplicate_source_id": 1}, result.report["rejectionReasons"])

    def test_deterministic_output(self):
        first = run_import(element(2, tags={"amenity": "atm", "name": "ATM B"}), element(1, tags={"amenity": "atm", "name": "ATM A"}))
        second = run_import(element(1, tags={"amenity": "atm", "name": "ATM A"}), element(2, tags={"amenity": "atm", "name": "ATM B"}))
        first_bytes = deterministic_json_bytes(first.dataset)
        second_bytes = deterministic_json_bytes(second.dataset)
        self.assertEqual(first_bytes, second_bytes)
        self.assertEqual(hashlib.sha256(first_bytes).hexdigest(), hashlib.sha256(second_bytes).hexdigest())
        self.assertIn("-v2-", first.dataset["datasetVersion"])

    def test_overpass_node_way_and_relation_coordinates(self):
        payload = {
            "elements": [
                {"type": "node", "id": 1, "lat": 8.0, "lon": 100.0, "tags": {"amenity": "atm", "name": "N"}},
                {"type": "way", "id": 2, "center": {"lat": 8.1, "lon": 100.1}, "tags": {"amenity": "bank", "name": "W"}},
                {"type": "relation", "id": 3, "center": {"lat": 8.2, "lon": 100.2}, "tags": {"tourism": "hotel", "name": "R"}},
            ]
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            loaded = load_elements(path)
        self.assertEqual(["node", "way", "relation"], [item.element_type for item in loaded])
        self.assertEqual((8.2, 100.2), (loaded[2].latitude, loaded[2].longitude))

    def test_osm_xml_nodes_ways_and_relations_use_representative_points(self):
        xml = """<?xml version="1.0" encoding="UTF-8"?>
        <osm version="0.6">
          <node id="1" lat="8.0" lon="100.0">
            <tag k="amenity" v="atm"/><tag k="name" v="Node ATM"/>
          </node>
          <node id="2" lat="8.2" lon="100.2"/>
          <way id="3">
            <nd ref="1"/><nd ref="2"/>
            <tag k="amenity" v="bank"/><tag k="name" v="Way Bank"/>
          </way>
          <relation id="4">
            <member type="way" ref="3" role="outer"/>
            <tag k="tourism" v="hotel"/><tag k="name" v="Relation Hotel"/>
          </relation>
        </osm>"""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input.osm"
            path.write_text(xml, encoding="utf-8")
            loaded = load_elements(path)
        self.assertEqual(["node", "way", "relation"], [item.element_type for item in loaded])
        self.assertAlmostEqual(8.1, loaded[1].latitude)
        self.assertAlmostEqual(100.1, loaded[2].longitude)

    def test_malformed_utf8_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input.json"
            path.write_bytes(b'{"elements": ["\xff"]}')
            with self.assertRaisesRegex(ValueError, "Malformed Overpass JSON or UTF-8"):
                load_elements(path)


if __name__ == "__main__":
    unittest.main()
