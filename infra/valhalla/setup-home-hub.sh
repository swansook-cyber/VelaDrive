#!/usr/bin/env bash
set -euo pipefail

ROOT="${VELADRIVE_VALHALLA_ROOT:-/opt/veladrive/valhalla}"

sudo mkdir -p "$ROOT/data"
sudo chown -R "$(id -u):$(id -g)" "$ROOT"

cp "$(dirname "$0")/compose.yaml" "$ROOT/compose.yaml"

cd "$ROOT"
docker compose pull
docker compose up -d

echo
echo "Valhalla is starting."
echo "First startup downloads Thailand OSM data and builds routing tiles."
echo "Watch progress with:"
echo "  cd $ROOT && docker compose logs -f valhalla"
echo
echo "After the build completes, test:"
echo "  curl -fsS http://127.0.0.1:8002/status"
