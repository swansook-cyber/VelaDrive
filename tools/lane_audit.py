#!/usr/bin/env python3
"""Audit Valhalla turn-lane coverage for Vela Drive routes.

Uses Python stdlib only so it can run directly on the Debian Home Hub.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Point:
    name: str
    lat: float
    lon: float


@dataclass(frozen=True)
class AuditRoute:
    name: str
    origin: Point
    destination: Point


KRABI_ROUTES = [
    AuditRoute(
        "Ao Nang → Krabi Town",
        Point("Ao Nang", 8.0458021, 98.8103485),
        Point("Krabi Town", 8.0635, 98.9162),
    ),
    AuditRoute(
        "Ao Nang → Krabi Airport",
        Point("Ao Nang", 8.0458021, 98.8103485),
        Point("Krabi Airport", 8.100278, 98.985278),
    ),
    AuditRoute(
        "Krabi Town → Krabi Airport",
        Point("Krabi Town", 8.0635, 98.9162),
        Point("Krabi Airport", 8.100278, 98.985278),
    ),
    AuditRoute(
        "Noppharat Thara → Krabi Town",
        Point("Noppharat Thara", 8.04192, 98.81142),
        Point("Krabi Town", 8.0635, 98.9162),
    ),
]


def route_payload(route: AuditRoute) -> dict[str, Any]:
    return {
        "locations": [
            {"lat": route.origin.lat, "lon": route.origin.lon},
            {"lat": route.destination.lat, "lon": route.destination.lon},
        ],
        "costing": "auto",
        "units": "kilometers",
        "language": "en-US",
        "turn_lanes": True,
    }


def post_route(endpoint: str, route: AuditRoute, timeout: int) -> dict[str, Any]:
    url = endpoint.rstrip("/") + "/route"
    body = json.dumps(route_payload(route)).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "User-Agent": "VelaDrive-LaneAudit/0.1",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def lane_summary(response: dict[str, Any]) -> dict[str, Any]:
    legs = response.get("trip", {}).get("legs", [])
    maneuvers: list[dict[str, Any]] = []
    for leg in legs:
        maneuvers.extend(leg.get("maneuvers", []))

    lane_maneuvers = [m for m in maneuvers if m.get("lanes")]
    active_maneuvers = [
        m for m in lane_maneuvers
        if any(int(lane.get("active", 0) or 0) != 0 for lane in m.get("lanes", []))
    ]
    valid_maneuvers = [
        m for m in lane_maneuvers
        if any(
            int(lane.get("active", 0) or 0) != 0
            or int(lane.get("valid", 0) or 0) != 0
            for lane in m.get("lanes", [])
        )
    ]

    navigable = [
        m for m in maneuvers
        if int(m.get("type", 0)) not in {0, 1, 2, 3, 4, 5, 6}
    ]

    denominator = len(navigable)
    coverage = (len(lane_maneuvers) / denominator * 100.0) if denominator else 0.0

    examples = []
    for maneuver in lane_maneuvers[:5]:
        examples.append(
            {
                "instruction": maneuver.get("instruction"),
                "type": maneuver.get("type"),
                "lanes": maneuver.get("lanes"),
            }
        )

    return {
        "maneuvers_total": len(maneuvers),
        "maneuvers_guidance_relevant": denominator,
        "maneuvers_with_lanes": len(lane_maneuvers),
        "maneuvers_with_active_lane": len(active_maneuvers),
        "maneuvers_with_valid_or_active_lane": len(valid_maneuvers),
        "lane_coverage_percent": round(coverage, 1),
        "examples": examples,
    }


def verdict(percent: float) -> str:
    if percent >= 60:
        return "strong"
    if percent >= 30:
        return "useful"
    if percent >= 10:
        return "limited"
    return "sparse"


def run_audit(endpoint: str, timeout: int) -> dict[str, Any]:
    results = []
    weighted_numerator = 0
    weighted_denominator = 0

    for route in KRABI_ROUTES:
        item: dict[str, Any] = {
            "route": route.name,
            "origin": asdict(route.origin),
            "destination": asdict(route.destination),
        }
        try:
            response = post_route(endpoint, route, timeout)
            summary = lane_summary(response)
            item["ok"] = True
            item.update(summary)
            weighted_numerator += summary["maneuvers_with_lanes"]
            weighted_denominator += summary["maneuvers_guidance_relevant"]
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError) as exc:
            item["ok"] = False
            item["error"] = str(exc)
        results.append(item)

    overall = (
        weighted_numerator / weighted_denominator * 100.0
        if weighted_denominator
        else 0.0
    )

    return {
        "endpoint": endpoint,
        "area": "Krabi / Ao Nang",
        "overall_lane_coverage_percent": round(overall, 1),
        "verdict": verdict(overall),
        "routes": results,
    }


def markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Vela Drive Lane Coverage Audit",
        "",
        f"- Endpoint: {report['endpoint']}",
        f"- Area: {report['area']}",
        f"- Overall lane coverage: **{report['overall_lane_coverage_percent']}%**",
        f"- Verdict: **{report['verdict']}**",
        "",
        "| Route | Relevant maneuvers | With lanes | Coverage | Active |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]

    for item in report["routes"]:
        if not item.get("ok"):
            lines.append(f"| {item['route']} | error | error | error | error |")
            continue
        lines.append(
            "| {route} | {rel} | {lanes} | {coverage}% | {active} |".format(
                route=item["route"],
                rel=item["maneuvers_guidance_relevant"],
                lanes=item["maneuvers_with_lanes"],
                coverage=item["lane_coverage_percent"],
                active=item["maneuvers_with_active_lane"],
            )
        )

    lines += [
        "",
        "Interpretation for V1:",
        "- strong (>=60%): lane guidance can be a major Vela Drive feature on tested routes.",
        "- useful (30–59.9%): show lanes prominently when available, but never depend on them.",
        "- limited (10–29.9%): lane guidance is supplemental only.",
        "- sparse (<10%): current + next maneuver remains the primary urban-driving aid.",
        "",
        "The audit only measures OSM/Valhalla data on the sampled routes. It does not claim nationwide coverage.",
    ]
    return "\n".join(lines) + "\n"


def self_test() -> None:
    sample = {
        "trip": {
            "legs": [
                {
                    "maneuvers": [
                        {"type": 1, "instruction": "Start"},
                        {
                            "type": 15,
                            "instruction": "Turn left",
                            "lanes": [
                                {"directions": 8, "active": 8},
                                {"directions": 10, "valid": 8},
                            ],
                        },
                        {"type": 10, "instruction": "Turn right"},
                        {"type": 4, "instruction": "Arrive"},
                    ]
                }
            ]
        }
    }
    result = lane_summary(sample)
    assert result["maneuvers_guidance_relevant"] == 2
    assert result["maneuvers_with_lanes"] == 1
    assert result["maneuvers_with_active_lane"] == 1
    assert result["lane_coverage_percent"] == 50.0
    assert verdict(50.0) == "useful"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--endpoint",
        default="http://127.0.0.1:8002",
        help="Valhalla base URL (default: local Home Hub)",
    )
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--md-out", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        print("lane audit self-test: PASS")
        return 0

    report = run_audit(args.endpoint, args.timeout)
    output = json.dumps(report, indent=2, ensure_ascii=False)
    print(output)

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output + "\n", encoding="utf-8")

    if args.md_out:
        args.md_out.parent.mkdir(parents=True, exist_ok=True)
        args.md_out.write_text(markdown(report), encoding="utf-8")

    return 0 if all(item.get("ok") for item in report["routes"]) else 2


if __name__ == "__main__":
    raise SystemExit(main())
