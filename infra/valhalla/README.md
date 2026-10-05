# Vela Drive — Valhalla on Home Hub

This deployment uses the official `ghcr.io/valhalla/valhalla-scripted` image and the Thailand OSM extract from Geofabrik.

## First start

```bash
mkdir -p /opt/VelaDrive/infra/valhalla/data
cd /opt/VelaDrive/infra/valhalla
docker compose up -d
docker compose logs -f valhalla
```

The first start downloads Thailand OSM data and builds the graph. Keep `server_threads=1` initially to avoid memory pressure on the Home Hub. After the graph exists, normal serving is much lighter than the build.

## Smoke test

```bash
curl -s http://127.0.0.1:8002/status
```

Then test a route:

```bash
curl -s -X POST http://127.0.0.1:8002/route \
  -H 'Content-Type: application/json' \
  -d '{
    "locations":[
      {"lat":8.0863,"lon":98.9063},
      {"lat":8.0590,"lon":98.9167}
    ],
    "costing":"auto",
    "units":"kilometers"
  }'
```

## Android endpoint

For local/dev builds, set this Gradle property:

```properties
VELA_VALHALLA_BASE_URL=https://<your-routing-host>
```

The app appends `/route` itself.

Until the Vela endpoint is deployed, the Android project defaults to the public FOSSGIS Valhalla server for development smoke tests only. Do not treat that public server as the production backend.

## Update policy

Do not rebuild every night. For personal use, a weekly or monthly Thailand update is sufficient initially. Keep the last known-good `data` directory before any forced rebuild.
