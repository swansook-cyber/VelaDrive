from __future__ import annotations

import argparse
from pathlib import Path

from .importer import (
    GeoJsonBoundary,
    import_elements,
    load_elements,
    sha256_file,
    source_data_timestamp,
    write_import_outputs,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Convert a local OSM extract into the Vela POI schema."
    )
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--boundary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--quarantine", type=Path, required=True)
    parser.add_argument("--snapshot-date", required=True, help="ISO date: YYYY-MM-DD")
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--boundary-name", required=True)
    parser.add_argument("--boundary-reference", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    input_sha256 = sha256_file(args.input)
    boundary_sha256 = sha256_file(args.boundary)
    result = import_elements(
        load_elements(args.input),
        boundary=GeoJsonBoundary.from_path(args.boundary),
        snapshot_date=args.snapshot_date,
        input_sha256=input_sha256,
        boundary_sha256=boundary_sha256,
        source_url=args.source_url,
        boundary_name=args.boundary_name,
        boundary_reference=args.boundary_reference,
        input_format=args.input.suffix.lower().lstrip("."),
        source_data_timestamp=source_data_timestamp(args.input),
    )
    output_sha256 = write_import_outputs(
        result,
        output_path=args.output,
        report_path=args.report,
        quarantine_path=args.quarantine,
    )
    print(f"Generated {len(result.dataset['pois'])} POIs")
    print(f"Rejected {result.report['rejectedCount']} records")
    print(f"SHA-256 {output_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

