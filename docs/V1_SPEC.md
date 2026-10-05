# Vela Drive V1 Technical Specification

## Product goal
Make urban turn-by-turn guidance clearer than a conventional map app, while preserving Google Maps as the zero-cost destination search surface.

## V1 user flow
1. Open Vela Drive.
2. Tap **ค้นหาด้วย Google Maps**.
3. Google Maps opens via official Maps URL; no Google Maps Platform API key is required.
4. User finds the destination and shares it to Vela Drive.
5. Vela Drive resolves coordinates from the shared payload.
6. Route provider calculates the route.
7. Drive Mode presents current + next maneuver, early preparation guidance, lane guidance when reliable data exists, Thai voice prompts, and rerouting.

## Locked architecture
- Android: Kotlin + Jetpack Compose.
- Destination search: Google Maps app via Maps URL, no Places API.
- Destination import: Android ACTION_SEND text/plain + DestinationResolver.
- Routing primary: self-hosted Valhalla using Thailand OSM data.
- Navigation state candidate: Ferrostar Core 0.57.x; validate before locking.
- Map renderer: MapLibre.
- Basemap candidate: ELEMNT Cartographer/PMTiles during prototype; later self-host Thailand PMTiles.
- Traffic: Longdo as optional provider; Valhalla remains the no-traffic fallback.
- Guidance UI: custom Vela Guidance layer, not vendor default UI.

## Non-negotiable V1 guidance
- Current maneuver.
- Next maneuver simultaneously visible when useful.
- Closely spaced junction handling: “ผ่านแยกนี้ / แยกถัดไป...” style guidance.
- Early turn preparation.
- Lane guidance only when source data exists and is considered valid.
- Thai TTS.
- Automatic rerouting.
- Clear fallback when live traffic is unavailable.

## Explicitly out of scope for first road test
- Crowdsourced traffic.
- Android Auto.
- Full offline on-device routing.
- Building a Google-equivalent POI database.
- Reverse engineering Google traffic or government private APIs.

## Traffic strategy
Longdo is behind an interface and must never be required for navigation. If quota, key, network, or provider availability fails, route calculation falls back to Valhalla.

## Validation gates before V1 road test
1. Resolve representative Google Maps shares: full URL, maps.app.goo.gl short URL, coordinates, geo URI.
2. Valhalla Thailand route smoke tests.
3. Ferrostar + MapLibre dependency compatibility.
4. Lane extraction from Valhalla `intersections[].lanes` and custom UI rendering.
5. Rerouting after deliberate route deviation.
6. Thai TTS timing and duplicate-prompt suppression.
7. Longdo quota/key behavior and graceful fallback.
