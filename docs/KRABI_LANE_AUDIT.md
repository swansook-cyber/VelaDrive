# Thailand Lane Coverage Audit

Vela Drive does not assume that OSM turn-lane data is available everywhere. The audit tool measures real Valhalla lane coverage on ordinary driving routes.

## City profiles

Current profiles:

- Krabi / Ao Nang
- Hat Yai
- Phuket
- Bangkok

Each profile contains four representative routes. They are for coverage sampling, not benchmark timing.

## Run on the Home Hub

Single city:

    python3 tools/lane_audit.py       --endpoint http://127.0.0.1:8002       --city hatyai       --md-out reports/hatyai-lanes.md

Compare large cities:

    python3 tools/lane_audit.py       --endpoint http://127.0.0.1:8002       --city hatyai       --city phuket       --city bangkok       --json-out reports/major-cities-lanes.json       --md-out reports/major-cities-lanes.md

All profiles including Krabi:

    python3 tools/lane_audit.py       --endpoint http://127.0.0.1:8002       --all       --json-out reports/thailand-lanes.json       --md-out reports/thailand-lanes.md

## Interpretation

- strong: 60% or more of guidance-relevant maneuvers have lane data
- useful: 30–59.9%
- limited: 10–29.9%
- sparse: below 10%

Vela Drive always keeps current + next maneuver as the baseline. Lane guidance appears only when Valhalla returns reliable data.

A sparse result in Krabi does not imply sparse coverage in Bangkok, Phuket, or Hat Yai. This audit exists specifically to avoid that incorrect conclusion.

## CI

The tool has an offline self-test:

    python3 tools/lane_audit.py --self-test

This validates counting, classifications, and city profile registration without calling Valhalla.
