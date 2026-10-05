#!/usr/bin/env bash
set -euo pipefail

ROOT="${VELADRIVE_VALHALLA_ROOT:-/opt/veladrive/valhalla}"
cd "$ROOT"

stamp="$(date +%Y%m%d-%H%M%S)"
backup="$ROOT/data-backup-$stamp"

docker compose down

if [ -d "$ROOT/data" ]; then
  mv "$ROOT/data" "$backup"
fi

cleanup_backup() {
  if [ -d "$backup" ]; then
    rm -rf "$backup"
  fi
}

rollback() {
  echo "Valhalla rebuild failed; restoring previous data." >&2
  docker compose down || true
  rm -rf "$ROOT/data"
  if [ -d "$backup" ]; then
    mv "$backup" "$ROOT/data"
  fi
  docker compose up -d
}
trap rollback ERR

mkdir -p "$ROOT/data"

# Force the scripted image to refresh the Thailand PBF and rebuild graph tiles once.
VALHALLA_FORCE_REBUILD=True docker compose up -d

echo "Waiting for Valhalla status endpoint..."
for _ in $(seq 1 180); do
  if curl -fsS http://127.0.0.1:8002/status >/dev/null 2>&1; then
    echo "Valhalla is healthy."
    cleanup_backup
    exit 0
  fi
  sleep 10
done

echo "Timed out waiting for Valhalla." >&2
exit 1
