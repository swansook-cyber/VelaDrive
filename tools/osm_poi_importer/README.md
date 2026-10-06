# Vela OpenStreetMap POI importer

This tool converts a local OpenStreetMap extract into the Vela POI JSON schema. It supports Overpass JSON and streaming `.osm` XML with Python's standard library. `.osm.pbf` is supported when the optional `osmium` Python package is installed.

Every output POI retains its OSM element type/ID, source URL, category-driving tags, dataset version, and generation date. Ways use a representative point derived from their nodes; Overpass ways and relations use `out center` coordinates. XML/PBF relations use available member representative points.

Stable OSM IDs are deduplicated first. The fallback normalized-name/proximity rule uses a conservative 10 m threshold; 25 m and 50 m are quality-review bands only. Generic `route=ferry` geometry is retained in source snapshots but is not exposed as a searchable destination because its representative point may be offshore or mid-route.

## Krabi pilot

The checked-in query targets OSM administrative relation `1908779` (Krabi Province). The pilot's exact input snapshot and OSM-derived boundary are retained under `source/krabi` so its output is reproducible. Refresh them explicitly with:

```powershell
python -m tools.osm_poi_importer.fetch_boundary `
  --query "Krabi Province, Thailand" `
  --osm-relation-id 1908779 `
  --output tools/osm_poi_importer/source/krabi/krabi-boundary.geojson

python -m tools.osm_poi_importer.fetch_overpass `
  --query tools/osm_poi_importer/queries/krabi.overpassql `
  --output tools/osm_poi_importer/source/krabi/krabi-pois.overpass.json
```

Generate the review-only pilot. Set `--snapshot-date` explicitly so the same source files and arguments always produce identical bytes:

```powershell
python -m tools.osm_poi_importer.cli `
  --input tools/osm_poi_importer/source/krabi/krabi-pois.overpass.json `
  --boundary tools/osm_poi_importer/source/krabi/krabi-boundary.geojson `
  --snapshot-date 2026-10-06 `
  --source-url "https://overpass-api.de/api/interpreter" `
  --boundary-name "Krabi Province, Thailand" `
  --boundary-reference "relation/1908779" `
  --output tools/osm_poi_importer/generated/krabi/vela_pois_krabi.json `
  --report tools/osm_poi_importer/generated/krabi/import_report.json `
  --quarantine tools/osm_poi_importer/generated/krabi/quarantine.json
```

The production file `app/src/main/assets/vela_pois.json` is intentionally not an output target.

## License and provenance

Source data is **© OpenStreetMap contributors** and licensed under the [Open Data Commons Open Database License (ODbL)](https://www.openstreetmap.org/copyright). Vela must retain appropriate OpenStreetMap attribution and comply with ODbL when distributing an OSM-derived database.

No Garmin, Foursquare, Google Maps, JCV, GPI, IMG, or other proprietary-derived POI data is accepted or used by this pipeline.

