# Vela Drive

Personal Android navigation focused on clearer urban driving guidance.

**Principle:** Search like Google. Drive with Vela.

## Status
V0.1 scaffold. Search launcher + Android share target + local destination parsing + provider interfaces are in place. Routing/navigation SDKs are intentionally not enabled until the compatibility spike is complete.

See `docs/V1_SPEC.md`.

## Planned stack
- Kotlin / Jetpack Compose
- Google Maps URL for destination search (no Places API)
- Valhalla self-hosted routing
- Ferrostar Core candidate
- MapLibre map renderer
- ELEMNT Cartographer / PMTiles candidate basemap
- Longdo optional traffic provider

## Local target
Suggested Windows working directory: `D:\\Project AI\\VelaDrive`
