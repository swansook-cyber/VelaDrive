# VelaDrive GPI import audit — 2026-10-07

## Parsed inputs

- `Thailand Speed Alert 18.gpi`: 55 POIs parsed.
- `speed17.gpi`: 159 POIs parsed; 1 exact duplicate coordinate removed.
- Cross-file merge: 53 of 55 Thailand Speed Alert points matched speed17 within 20 m; 2 were unique.
- Final speed-camera dataset: 160 unique points.
- `Isuzu.gpi`: 289 dealer POIs parsed with coordinates; address/phone retained where present.
- `Railway.gpi`: not decoded. It is a Garmin `GRMREC01` file whose body does not expose normal `0x80002` POI records. Treat as compressed/encrypted/locked source; do not guess coordinates.

## Safety normalization

- Garmin semicircle coordinates are converted to WGS84 degrees.
- Single directions are mapped NB=0°, EB=90°, SB=180°, WB=270°. Bidirectional/ambiguous values are left `null`.
- Garmin alert-speed values (for example ~105 km/h) are **not** written to `speed_limit`; they are source alert thresholds, not verified posted legal limits.
- Vela warning distance is normalized to 700 m. Original Garmin proximity is retained as `source_proximity_m`.
- Imported data confidence is 0.7 and should be treated as unverified legacy source data.

## Railway

No railway records were imported from `Railway.gpi`. A replacement unencrypted CSV/GPX/source dataset is required before railway alerts can be populated safely.