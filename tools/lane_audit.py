#!/usr/bin/env python3
"""Audit Valhalla turn-lane coverage for Vela Drive routes.

Uses Python stdlib only so it can run directly on the Debian Home Hub.
"""

from __future__ import annotations

import argparse
import json
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


AUDIT_PROFILES: dict[str, dict[str, Any]] = {
    "krabi": {
        "area": "Krabi / Ao Nang",
        "routes": [
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
        ],
    },
    "hatyai": {
        "area": "Hat Yai",
        "routes": [
            AuditRoute(
                "Hat Yai Railway Station → Central Hat Yai",
                Point("Hat Yai Railway Station", 7.0040, 100.4670),
                Point("Central Hat Yai", 6.9898, 100.4821),
            ),
            AuditRoute(
                "Central Hat Yai → Prince of Songkla University",
                Point("Central Hat Yai", 6.9898, 100.4821),
                Point("Prince of Songkla University", 7.0067, 100.4988),
            ),
            AuditRoute(
                "Hat Yai City Center → Hat Yai Airport",
                Point("Hat Yai City Center", 7.0084, 100.4747),
                Point("Hat Yai Airport", 6.9332, 100.3929),
            ),
            AuditRoute(
                "Kim Yong Market → Khlong Hae Floating Market",
                Point("Kim Yong Market", 7.0060, 100.4702),
                Point("Khlong Hae Floating Market", 7.0458, 100.4747),
            ),
        ],
    },
    "phuket": {
        "area": "Phuket",
        "routes": [
            AuditRoute(
                "Phuket Old Town → Central Phuket",
                Point("Phuket Old Town", 7.8840, 98.3890),
                Point("Central Phuket", 7.8922, 98.3674),
            ),
            AuditRoute(
                "Central Phuket → Patong",
                Point("Central Phuket", 7.8922, 98.3674),
                Point("Patong", 7.8966, 98.2966),
            ),
            AuditRoute(
                "Phuket Old Town → Phuket Airport",
                Point("Phuket Old Town", 7.8840, 98.3890),
                Point("Phuket Airport", 8.1132, 98.3169),
            ),
            AuditRoute(
                "Chalong Circle → Phuket Old Town",
                Point("Chalong Circle", 7.8222, 98.3426),
                Point("Phuket Old Town", 7.8840, 98.3890),
            ),
        ],
    },
    "bangkok": {
        "area": "Bangkok",
        "routes": [
            AuditRoute(
                "Victory Monument → Siam",
                Point("Victory Monument", 13.7649, 100.5383),
                Point("Siam", 13.7466, 100.5347),
            ),
            AuditRoute(
                "Siam → Asok",
                Point("Siam", 13.7466, 100.5347),
                Point("Asok", 13.7370, 100.5604),
            ),
            AuditRoute(
                "Chatuchak → Victory Monument",
                Point("Chatuchak", 13.7998, 100.5501),
                Point("Victory Monument", 13.7649, 100.5383),
            ),
            AuditRoute(
                "Rama IX → Asok",
                Point("Rama IX", 13.7570, 100.5658),
                Point("Asok", 13.7370, 100.5604),
            ),
        ],
    },
}


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
            "User-Agent": "VelaDrive-LaneAudit/0.2",
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
    relevant = [
        m for m in maneuvers
        if int(m.get("type", 0)) not in {0, 1, 2, 3, 4, 5, 6}
    ]

    denominator = len(relevant)
    raw_coverage = (len(lane_maneuvers) / denominator * 100.0) if denominator else 0.0
    usable_coverage = (len(valid_maneuvers) / denominator * 100.0) if denominator else 0.0

    return {
        "maneuvers_total": len(maneuvers),
        "maneuvers_guidance_relevant": denominator,
        "maneuvers_with_lanes": len(lane_maneuvers),
        "maneuvers_with_active_lane": len(active_maneuvers),
        "maneuvers_with_valid_or_active_lane": len(valid_maneuvers),
        "lane_coverage_percent": round(raw_coverage, 1),
        "usable_lane_coverage_percent": round(usable_coverage, 1),
        "examples": [
            {
                "instruction": maneuver.get("instruction"),
                "type": maneuver.get("type"),
                "lanes": maneuver.get("lanes"),
            }
            for maneuver in lane_maneuvers[:5]
        ],
    }


def verdict(percent: float) -> str:
    if percent >= 60:
        return "strong"
    if percent >= 30:
        return "useful"
    if percent >= 10:
        return "limited"
    return "sparse"


def run_profile(
    endpoint: str,
    timeout: int,
    profile_name: str,
) -> dict[str, Any]:
    profile = AUDIT_PROFILES[profile_name]
    results = []
    raw_numerator = 0
    usable_numerator = 0
    denominator = 0

    for route in profile["routes"]:
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
            raw_numerator += summary["maneuvers_with_lanes"]
            usable_numerator += summary["maneuvers_with_valid_or_active_lane"]
            denominator += summary["maneuvers_guidance_relevant"]
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError) as exc:
            item["ok"] = False
            item["error"] = str(exc)
        results.append(item)

    raw_overall = (raw_numerator / denominator * 100.0) if denominator else 0.0
    usable_overall = (usable_numerator / denominator * 100.0) if denominator else 0.0
    return {
        "profile": profile_name,
        "endpoint": endpoint,
        "area": profile["area"],
        "overall_lane_coverage_percent": round(raw_overall, 1),
        "usable_lane_coverage_percent": round(usable_overall, 1),
        "verdict": verdict(usable_overall),
        "routes": results,
    }


def run_profiles(
    endpoint: str,
    timeout: int,
    profile_names: list[str],
) -> dict[str, Any]:
    reports = [run_profile(endpoint, timeout, name) for name in profile_names]
    total_lanes = sum(
        item["maneuvers_with_lanes"]
        for report in reports
        for item in report["routes"]
        if item.get("ok")
    )
    total_relevant = sum(
        item["maneuvers_guidance_relevant"]
        for report in reports
        for item in report["routes"]
        if item.get("ok")
    )
    total_usable = sum(
        item["maneuvers_with_valid_or_active_lane"]
        for report in reports
        for item in report["routes"]
        if item.get("ok")
    )
    raw_overall = (total_lanes / total_relevant * 100.0) if total_relevant else 0.0
    usable_overall = (total_usable / total_relevant * 100.0) if total_relevant else 0.0

    return {
        "endpoint": endpoint,
        "profiles": reports,
        "overall_lane_coverage_percent": round(raw_overall, 1),
        "usable_lane_coverage_percent": round(usable_overall, 1),
        "verdict": verdict(usable_overall),
    }


def markdown(report: dict[str, Any]) -> str:
    profiles = report.get("profiles", [report])
    lines = [
        "# Vela Drive Thailand Lane Coverage Audit",
        "",
        f"- Endpoint: {report['endpoint']}",
        f"- Raw lane coverage: **{report.get('overall_lane_coverage_percent', 0.0)}%**",
        f"- Usable lane guidance: **{report.get('usable_lane_coverage_percent', 0.0)}%**",
        f"- Verdict (based on usable guidance): **{report.get('verdict', 'sparse')}**",
        "",
    ]

    for profile in profiles:
        lines += [
            f"## {profile['area']}",
            "",
            f"- Raw lane coverage: **{profile['overall_lane_coverage_percent']}%**",
            f"- Usable lane guidance: **{profile['usable_lane_coverage_percent']}%**",
            f"- Verdict: **{profile['verdict']}**",
            "",
            "| Route | Relevant | Raw lanes | Raw % | Usable | Usable % |",
            "| --- | ---: | ---: | ---: | ---: | ---: |",
        ]
        for item in profile["routes"]:
            if not item.get("ok"):
                lines.append(f"| {item['route']} | error | error | error | error |")
                continue
            lines.append(
                "| {route} | {rel} | {lanes} | {coverage}% | {usable} | {usable_pct}% |".format(
                    route=item["route"],
                    rel=item["maneuvers_guidance_relevant"],
                    lanes=item["maneuvers_with_lanes"],
                    coverage=item["lane_coverage_percent"],
                    usable=item["maneuvers_with_valid_or_active_lane"],
                    usable_pct=item["usable_lane_coverage_percent"],
                )
            )
        lines.append("")

    lines += [
        "Interpretation:",
        "- strong (>=60%): lane guidance can be prominent on the sampled routes.",
        "- useful (30–59.9%): show lanes prominently when available, but never depend on them.",
        "- limited (10–29.9%): lane guidance is supplemental.",
        "- sparse (<10%): current + next maneuver remains the primary driving aid.",
        "",
        "This is a sampled OSM/Valhalla audit, not a nationwide coverage claim.",
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
    assert result["usable_lane_coverage_percent"] == 50.0
    assert verdict(50.0) == "useful"
    assert set(AUDIT_PROFILES) == {"krabi", "hatyai", "phuket", "bangkok"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--endpoint",
        default="http://127.0.0.1:8002",
        help="Valhalla base URL (default: local Home Hub)",
    )
    parser.add_argument(
        "--city",
        action="append",
        choices=sorted(AUDIT_PROFILES),
        help="Audit one or more city profiles. Repeat flag to compare cities.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Audit all city profiles.",
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

    if args.all:
        profile_names = list(AUDIT_PROFILES)
    elif args.city:
        profile_names = args.city
    else:
        profile_names = ["krabi"]

    report = run_profiles(args.endpoint, args.timeout, profile_names)
    output = json.dumps(report, indent=2, ensure_ascii=False)
    print(output)

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output + "\n", encoding="utf-8")

    if args.md_out:
        args.md_out.parent.mkdir(parents=True, exist_ok=True)
        args.md_out.write_text(markdown(report), encoding="utf-8")

    ok = all(
        item.get("ok")
        for profile in report["profiles"]
        for item in profile["routes"]
    )
    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
