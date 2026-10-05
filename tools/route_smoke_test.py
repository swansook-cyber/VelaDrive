#!/usr/bin/env python3
"""Minimal Valhalla route smoke test for Vela Drive."""

from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path

ROUTES = [
    {
        "name": "Ao Nang → Krabi Town",
        "origin": {"lat": 8.0458021, "lon": 98.8103485},
        "destination": {"lat": 8.0635, "lon": 98.9162},
    },
    {
        "name": "Krabi Town → Krabi Airport",
        "origin": {"lat": 8.0635, "lon": 98.9162},
        "destination": {"lat": 8.100278, "lon": 98.985278},
    },
]


def request_route(endpoint: str, item: dict, timeout: int) -> dict:
    payload = {
        "locations": [item["origin"], item["destination"]],
        "costing": "auto",
        "units": "kilometers",
        "language": "en-US",
        "turn_lanes": True,
    }
    request = urllib.request.Request(
        endpoint.rstrip("/") + "/route",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "User-Agent": "VelaDrive-RouteSmoke/0.1",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def validate(item: dict, response: dict) -> dict:
    trip = response.get("trip")
    if not isinstance(trip, dict):
        raise RuntimeError("missing trip")

    summary = trip.get("summary")
    if not isinstance(summary, dict):
        raise RuntimeError("missing summary")

    legs = trip.get("legs")
    if not isinstance(legs, list) or not legs:
        raise RuntimeError("missing legs")

    shape = legs[0].get("shape")
    maneuvers = legs[0].get("maneuvers")
    if not shape:
        raise RuntimeError("missing route shape")
    if not isinstance(maneuvers, list) or len(maneuvers) < 2:
        raise RuntimeError("not enough maneuvers")

    return {
        "name": item["name"],
        "ok": True,
        "distance_km": summary.get("length"),
        "duration_seconds": summary.get("time"),
        "maneuver_count": len(maneuvers),
        "has_shape": True,
    }


def self_test() -> None:
    sample = {
        "trip": {
            "summary": {"length": 5.0, "time": 600},
            "legs": [
                {
                    "shape": "abc",
                    "maneuvers": [
                        {"type": 1, "instruction": "Start"},
                        {"type": 4, "instruction": "Arrive"},
                    ],
                }
            ],
        }
    }
    result = validate(ROUTES[0], sample)
    assert result["ok"] is True
    assert result["maneuver_count"] == 2


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", default="http://127.0.0.1:8002")
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        print("route smoke self-test: PASS")
        return 0

    results = []
    success = True

    for item in ROUTES:
        try:
            response = request_route(args.endpoint, item, args.timeout)
            result = validate(item, response)
        except Exception as exc:
            result = {
                "name": item["name"],
                "ok": False,
                "error": str(exc),
            }
            success = False
        results.append(result)

    report = {
        "endpoint": args.endpoint,
        "routes": results,
        "all_ok": success,
    }

    output = json.dumps(report, indent=2, ensure_ascii=False)
    print(output)

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output + "\n", encoding="utf-8")

    return 0 if success else 2


if __name__ == "__main__":
    raise SystemExit(main())
