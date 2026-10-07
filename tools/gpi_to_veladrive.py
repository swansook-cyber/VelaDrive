#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import re
import struct
from pathlib import Path


class GPIParser:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0
        self.codepage = 1252
        self.group = ""
        self.category = ""
        self.pois: list[dict] = []
        self.magic = ""

    def read(self, n: int) -> bytes:
        if self.pos + n > len(self.data):
            raise EOFError
        b = self.data[self.pos:self.pos+n]
        self.pos += n
        return b

    def u8(self): return self.read(1)[0]
    def i16(self): return struct.unpack("<h", self.read(2))[0]
    def u16(self): return struct.unpack("<H", self.read(2))[0]
    def i32(self): return struct.unpack("<i", self.read(4))[0]
    def u32(self): return struct.unpack("<I", self.read(4))[0]

    def decode(self, b: bytes) -> str:
        enc = {65001: "utf-8", 874: "cp874", 1252: "cp1252"}.get(self.codepage, "latin1")
        return b.decode(enc, errors="replace").strip()

    def read_lc_string(self):
        lc = self.read(2).decode("ascii", errors="replace")
        n = self.u16()
        return lc, self.decode(self.read(n)), n

    def read_string(self) -> str:
        length = self.u16()
        if length <= 0:
            return ""
        first = self.u8()
        if first == 0:
            if self.u8() != 0:
                return ""
            lc1, s1, n1 = self.read_lc_string()
            used = n1 + 4
            if used < length:
                lc2, s2, _ = self.read_lc_string()
                if lc1 == "EN": return s1
                if lc2 == "EN": return s2
            return s1
        return self.decode(bytes([first]) + (self.read(length - 1) if length > 1 else b""))

    @staticmethod
    def semicircle_to_deg(value: int) -> float:
        return value * (180.0 / (2 ** 31))

    def read_header(self) -> dict:
        first = self.i32()
        if first != 0:
            self.i32()
        d2 = self.i32()
        self.magic = self.read(8).decode("ascii", errors="replace")
        if not self.magic.startswith("GRMREC"):
            raise ValueError("Not a Garmin GPI file")
        created = self.i32()
        self.i16()
        source_len = self.i16()
        source = self.read(source_len).decode("ascii", errors="replace")
        variant = self.i32()
        self.i32()
        if variant == 15:
            self.read(17)
        if self.read(3) != b"POI":
            raise ValueError("Unsupported GPI header")
        self.read(3)
        marker = self.read(2)
        self.codepage = self.u16()
        self.i16()
        return {"magic": self.magic, "d2": d2, "created": created, "source": source,
                "marker": marker.decode("ascii", errors="replace"), "codepage": self.codepage,
                "header_end": self.pos}

    def parse_tag(self, tag: int, waypoint: dict | None, parent_end: int) -> bool:
        if self.pos + 4 > len(self.data):
            return False
        raw_size = self.u32()
        start = self.pos
        size = raw_size + 4 if 0x80000 <= tag <= 0x800FF else raw_size
        end = min(start + size, parent_end, len(self.data))
        try:
            if tag in (0x2, 0x80002):
                self.parse_poi(size, tag, parent_end)
                return True
            if tag == 0x80008:
                self.parse_poi_list(size, parent_end)
                return True
            if tag in (0x9, 0x80009):
                self.parse_group(size, tag, parent_end)
                return True
            if tag in (0x3, 0x80003) and waypoint is not None and self.pos + 4 <= end:
                distance = self.i16()
                speed100 = self.i16()
                waypoint["proximity_m"] = distance if distance > 0 else None
                waypoint["alert_speed_kph"] = round((speed100 / 100.0) * 3.6, 3) if speed100 > 0 else None
            elif tag == 0xA and waypoint is not None:
                waypoint["description"] = self.read_string()
            elif tag == 0xE and waypoint is not None and self.pos < end:
                mask = self.u8()
                if mask in (1, 5, 0x32):
                    text = self.read_string()
                    if waypoint.get("description"):
                        waypoint["notes"] = text
                    else:
                        waypoint["description"] = text
            elif tag in (0xB, 0x8000B) and waypoint is not None:
                if tag == 0x8000B and self.pos + 4 <= end:
                    self.i32()
                if self.pos + 2 <= end:
                    mask = self.u16()
                    for bit, key in [(1, "city"), (2, "country"), (4, "state"), (8, "postal_code"), (16, "address")]:
                        if mask & bit and self.pos < end:
                            waypoint[key] = self.read_string()
            elif tag == 0xC and waypoint is not None and self.pos + 2 <= end:
                mask = self.u16()
                for bit, key in [(1, "phone"), (2, "phone2"), (4, "fax"), (8, "email"), (16, "link")]:
                    if mask & bit and self.pos < end:
                        waypoint[key] = self.read_string()
            elif tag == 0x8000C and waypoint is not None and self.pos + 6 <= end:
                self.i32()
                mask = self.u16()
                if mask & 1 and self.pos < end:
                    waypoint["phone"] = self.read_string()
        except (EOFError, UnicodeError, struct.error):
            pass
        self.pos = end
        return True

    def parse_poi(self, size: int, tag: int, parent_end: int):
        if tag == 0x80002:
            self.i32()
        base = self.pos
        lat = self.semicircle_to_deg(self.i32())
        lon = self.semicircle_to_deg(self.i32())
        self.i16()
        self.u8()
        wpt = {"name": self.read_string(), "lat": lat, "lon": lon, "group": self.group,
               "description": "", "notes": "", "proximity_m": None, "alert_speed_kph": None,
               "phone": None, "phone2": None, "fax": None, "email": None, "link": None,
               "city": None, "country": None, "state": None, "postal_code": None, "address": None}
        end = min(base + size - 4, parent_end, len(self.data))
        while self.pos + 8 <= end:
            if not self.parse_tag(self.u32(), wpt, end):
                break
        self.pos = end
        self.pois.append(wpt)

    def parse_poi_list(self, size: int, parent_end: int):
        base = self.pos
        end = min(base + size - 4, parent_end, len(self.data))
        if self.pos + 23 <= end:
            self.i32(); self.read(16); self.read(3); self.read(4)
        while self.pos + 8 <= end:
            if not self.parse_tag(self.u32(), None, end):
                break
        self.pos = end

    def parse_group(self, size: int, tag: int, parent_end: int):
        base = self.pos
        end = min(base + size, parent_end, len(self.data))
        if tag == 0x80009:
            self.i32()
        old = self.group
        try:
            self.group = self.read_string()
        except Exception:
            self.group = old
        while self.pos + 8 <= end:
            if not self.parse_tag(self.u32(), None, end):
                break
        self.pos = end
        self.group = old

    def parse(self):
        header = self.read_header()
        while self.pos + 8 <= len(self.data):
            if not self.parse_tag(self.u32(), None, len(self.data)):
                break
        return header, self.pois


def scan_extended_pois(path: Path, codepage: int, group: str) -> list[dict]:
    data = path.read_bytes()
    signature = struct.pack("<I", 0x80002)
    results = []
    for m in re.finditer(re.escape(signature), data):
        off = m.start()
        try:
            p = GPIParser(data); p.codepage = codepage; p.pos = off
            if p.u32() != 0x80002: continue
            raw = p.u32(); end = min(off + raw + 12, len(data))
            p.i32()
            lat = p.semicircle_to_deg(p.i32()); lon = p.semicircle_to_deg(p.i32())
            p.i16(); p.u8()
            wpt = {"name": p.read_string(), "lat": lat, "lon": lon, "group": group,
                   "description": "", "notes": "", "proximity_m": None, "alert_speed_kph": None,
                   "phone": None, "phone2": None, "fax": None, "email": None, "link": None,
                   "city": None, "country": None, "state": None, "postal_code": None, "address": None}
            while p.pos + 8 <= end:
                p.parse_tag(p.u32(), wpt, end)
            if 5 <= lat <= 21 and 97 <= lon <= 106 and wpt["name"]:
                results.append(wpt)
        except Exception:
            continue
    return results


def haversine(a: dict, b: dict) -> float:
    r = 6371000.0
    p1 = math.radians(a["lat"]); p2 = math.radians(b["lat"])
    dp = math.radians(b["lat"] - a["lat"]); dl = math.radians(b["lon"] - a["lon"])
    h = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*r*math.asin(math.sqrt(h))


def direction_degrees(description: str | None):
    if not description: return None
    m = re.match(r"^\(([^)]+)\)", description)
    if not m: return None
    return {"NB": 0.0, "EB": 90.0, "SB": 180.0, "WB": 270.0}.get(m.group(1).strip().upper())


def build_speed(speed17: list[dict], alert18: list[dict]) -> list[dict]:
    unique = []
    seen = set()
    for p in speed17:
        key = (round(p["lat"], 6), round(p["lon"], 6))
        if key not in seen:
            seen.add(key); unique.append(p)
    merged = [{"base": p, "name": p["name"], "description": p.get("description") or None,
               "direction": None, "source_alert_speed_kmh": None,
               "source_proximity_m": p.get("proximity_m"), "source_files": ["speed17.gpi"]}
              for p in unique]
    for p in alert18:
        distances = [(haversine(p, row["base"]), i) for i, row in enumerate(merged)]
        distance, idx = min(distances, key=lambda x: x[0])
        row = merged[idx] if distance <= 20 else None
        if row is None:
            row = {"base": p, "name": p["name"], "description": p.get("description") or None,
                   "direction": direction_degrees(p.get("description")),
                   "source_alert_speed_kmh": p.get("alert_speed_kph"),
                   "source_proximity_m": p.get("proximity_m"), "source_files": ["Thailand Speed Alert 18.gpi"]}
            merged.append(row)
        else:
            row.update(name=p["name"], description=p.get("description") or None,
                       direction=direction_degrees(p.get("description")),
                       source_alert_speed_kmh=p.get("alert_speed_kph"),
                       source_proximity_m=p.get("proximity_m"))
            row["source_files"].append("Thailand Speed Alert 18.gpi")
    out = []
    for i, row in enumerate(merged, 1):
        p = row["base"]
        out.append({
            "id": f"gpi-speed-{i:04d}", "type": "SPEED_CAMERA",
            "lat": round(p["lat"], 7), "lon": round(p["lon"], 7),
            "direction": row["direction"], "speed_limit": None,
            "warning_distance": 700.0, "source": "GPI_IMPORT_2026-10-07",
            "confidence": 0.7,
            "label": row["name"] if row["name"] != "กล้องจับความเร็ว" else None,
            "source_files": row["source_files"], "source_description": row["description"],
            "source_alert_speed_kmh": row["source_alert_speed_kmh"],
            "source_proximity_m": row["source_proximity_m"]
        })
    return out


def build_isuzu(pois: list[dict]) -> dict:
    rows = []
    for i, p in enumerate(pois, 1):
        rows.append({
            "id": f"gpi-isuzu-{i:04d}", "name": p["name"],
            "latitude": round(p["lat"], 7), "longitude": round(p["lon"], 7),
            "address": p.get("address"), "alternateNames": ["Isuzu", "อีซูซุ"],
            "category": "AUTO_SERVICE", "phone": p.get("phone"),
            "province": None, "district": None, "source": "VELA_CURATED",
            "sourceReference": "Isuzu.gpi", "sourceUrl": None,
            "sourceTags": {"brand": "Isuzu", "poi_type": "dealer", "import": "garmin_gpi"},
            "verified": False, "updatedAt": "2026-10-07"
        })
    return {"schemaVersion": 1, "datasetVersion": "2026-10-07-gpi-isuzu-v1",
            "updatedAt": "2026-10-07", "pois": rows}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--speed18", type=Path, required=True)
    ap.add_argument("--speed17", type=Path, required=True)
    ap.add_argument("--railway", type=Path, required=True)
    ap.add_argument("--isuzu", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    h18, p18 = GPIParser(args.speed18.read_bytes()).parse()
    h17, p17 = GPIParser(args.speed17.read_bytes()).parse()
    hr, railway = GPIParser(args.railway.read_bytes()).parse()
    hi = GPIParser(args.isuzu.read_bytes()); hi.read_header()
    isuzu = scan_extended_pois(args.isuzu, 65001, "Isuzu Dealers")

    safety = build_speed(p17, p18)
    poi_root = build_isuzu(isuzu)
    (args.out / "vela_safety_alerts.json").write_text(json.dumps(safety, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.out / "vela_pois.json").write_text(json.dumps(poi_root, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report = {
        "speed18": len(p18), "speed17": len(p17), "safety_final": len(safety),
        "isuzu": len(isuzu), "railway": len(railway), "railway_header": hr,
        "railway_decoded": bool(railway)
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not railway:
        print("Railway.gpi: no standard POI records decoded; do not fabricate railway coordinates.")

if __name__ == "__main__":
    main()