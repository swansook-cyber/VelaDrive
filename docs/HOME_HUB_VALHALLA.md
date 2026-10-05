# Vela Drive — Home Hub Valhalla

Vela Drive uses Valhalla as its primary routing backend. The Android app is intentionally configured through `VELA_VALHALLA_BASE_URL` so routing is not tied to a paid vendor.

## Recommended Home Hub deployment

The deployment lives in `infra/valhalla`.

On the Debian Home Hub:

```bash
git clone https://github.com/swansook-cyber/VelaDrive.git
cd VelaDrive
chmod +x infra/valhalla/*.sh
./infra/valhalla/setup-home-hub.sh
```

Default runtime directory:

```text
/opt/veladrive/valhalla
```

The container listens only on loopback:

```text
127.0.0.1:8002
```

This is deliberate. Do not expose port 8002 directly to the public internet. Put the Vela API/tunnel in front of it when remote Android access is enabled.

## First build

On first startup the container downloads:

```text
https://download.geofabrik.de/asia/thailand-latest.osm.pbf
```

and builds Thailand routing graph data. Watch it with:

```bash
cd /opt/veladrive/valhalla
docker compose logs -f valhalla
```

Check service health:

```bash
curl -fsS http://127.0.0.1:8002/status
```

## Android endpoint

For a local/dev build, put this in `~/.gradle/gradle.properties` or pass it on the command line:

```properties
VELA_VALHALLA_BASE_URL=https://<your-private-vela-route-host>
```

Do not commit private tunnel hostnames, tokens, or credentials into the repository.

## Updating Thailand routing data

Run:

```bash
./infra/valhalla/rebuild-thailand.sh
```

The script preserves the previous data directory and restores it if the replacement does not become healthy. This is intentionally conservative; routing must keep a last-known-good dataset.

## Resource policy

The server is capped at two Valhalla server threads in V1. If the first Thailand graph build causes memory pressure, reduce build concurrency before increasing it. Storage is not expected to be the limiting factor; graph building is the heavier stage.

## Development fallback

The Android Gradle config currently defaults to the public OpenStreetMap.de Valhalla instance only so CI/dev builds can exercise route preview before the Home Hub endpoint is published. This is not the intended production backend and must not be treated as an availability guarantee.
