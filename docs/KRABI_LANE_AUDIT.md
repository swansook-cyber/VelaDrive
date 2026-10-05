# Krabi / Ao Nang Lane Coverage Audit

This audit exists because Vela Drive must not assume lane data is available at every Thai junction.

The test routes use public place coordinates around Krabi and Ao Nang and query Valhalla with turn_lanes=true.

## Run on the Home Hub

After Valhalla is healthy:

    cd /path/to/VelaDrive
    python3 tools/lane_audit.py \
      --endpoint http://127.0.0.1:8002 \
      --json-out reports/krabi-lanes.json \
      --md-out reports/krabi-lanes.md

The four initial samples are:

- Ao Nang → Krabi Town
- Ao Nang → Krabi Airport
- Krabi Town → Krabi Airport
- Noppharat Thara → Krabi Town

These are deliberately ordinary driving routes rather than hand-picked intersections.

## Decision rule

Vela Drive always keeps current maneuver + next maneuver as the primary guidance model.

Lane guidance is shown only when Valhalla returns lane data. The audit classifies sampled coverage as:

- strong: 60% or more of guidance-relevant maneuvers have lane data
- useful: 30–59.9%
- limited: 10–29.9%
- sparse: below 10%

Even a strong Krabi result does not imply nationwide Thailand coverage.

## Why this is separate from Ferrostar

Valhalla's native response can expose per-maneuver lane bitmasks when turn_lanes=true. Vela Drive parses and normalizes that data itself so the UI is not dependent on a navigation SDK's banner/lane interpretation.

## CI sanity check

    python3 tools/lane_audit.py --self-test

This validates counting and classification without making a network call.
