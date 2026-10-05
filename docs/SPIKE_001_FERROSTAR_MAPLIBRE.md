# Technical Spike 001 — Ferrostar + MapLibre + Valhalla

## Goal
Prove that the Vela Drive Android baseline can resolve and compile the selected navigation stack before integrating navigation state into the UI.

## Candidate versions
- AGP 9.1.1
- Gradle 9.3.1
- JDK 17
- Kotlin 2.3.20
- Ferrostar 0.57.0
- MapLibre Compose 0.13.0 (transitive through Ferrostar)
- OkHttp 5.3.2
- Compose BOM 2026.02.01

## Why Kotlin 2.3.20 in this spike
Ferrostar 0.57.0 is built with Kotlin 2.3.20. This branch deliberately aligns the consumer compiler to avoid newer Kotlin metadata being consumed by an older Kotlin compiler.

AGP remains at 9.1.1 because Vela Drive compiles against API 37. AGP 9.1.1 requires Gradle 9.3.1 and JDK 17.

## Gate A — dependency/build compatibility
The GitHub Actions job must pass:
- `:app:assembleDebug`
- `:app:lintDebug`

If this fails, do not merge the stack to main. Fix versions on the spike branch first.

## Gate B — Valhalla provider
After Gate A passes:
1. Add a thin Vela adapter around Ferrostar's Valhalla route provider.
2. Point it at a configurable self-hosted base URL rather than a Stadia key.
3. Verify route response for automobile costing.
4. Preserve raw Valhalla lane/intersection payload where necessary for Vela lane guidance.

## Gate C — map render
Render a minimal MapLibre navigation map with a public development style only for the spike. Production style/PMTiles remain separate configuration.

## Gate D — simulated drive
Use Ferrostar SimulatedLocationProvider to replay a test route before physical driving. Confirm:
- current maneuver progression
- reroute/deviation state
- spoken instruction observer can be attached
- map camera follows route without blocking custom guidance UI

## Decision rule
Only merge this spike after the build passes and no dependency requires a paid map/routing API.
