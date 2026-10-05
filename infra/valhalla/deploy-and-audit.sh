#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VALHALLA_ROOT="${VELADRIVE_VALHALLA_ROOT:-/opt/veladrive/valhalla}"
REPORT_DIR="${VELADRIVE_REPORT_DIR:-$REPO_ROOT/reports}"
ENDPOINT="${VELADRIVE_VALHALLA_ENDPOINT:-http://127.0.0.1:8002}"
WAIT_SECONDS="${VELADRIVE_WAIT_SECONDS:-3600}"
SLEEP_SECONDS=10

log() {
  printf '[Vela Drive] %s\n' "$*"
}

log "Checking Docker..."
docker --version >/dev/null
docker compose version >/dev/null

log "Installing/updating Valhalla configuration..."
"$REPO_ROOT/infra/valhalla/setup-home-hub.sh"

log "Waiting for Valhalla health at $ENDPOINT/status ..."
deadline=$(( $(date +%s) + WAIT_SECONDS ))

while true; do
  if curl -fsS "$ENDPOINT/status" >/dev/null 2>&1; then
    break
  fi
  now=$(date +%s)
  if [ "$now" -ge "$deadline" ]; then
    log "Valhalla did not become healthy before timeout."
    log "Recent logs:"
    (cd "$VALHALLA_ROOT" && docker compose logs --tail=120 valhalla) || true
    exit 1
  fi
  sleep "$SLEEP_SECONDS"
done

log "Valhalla is healthy."
mkdir -p "$REPORT_DIR"

log "Running Krabi / Ao Nang lane coverage audit..."
python3 "$REPO_ROOT/tools/lane_audit.py" \
  --endpoint "$ENDPOINT" \
  --json-out "$REPORT_DIR/krabi-lanes.json" \
  --md-out "$REPORT_DIR/krabi-lanes.md"

log "Running route smoke test..."
python3 "$REPO_ROOT/tools/route_smoke_test.py" \
  --endpoint "$ENDPOINT" \
  --json-out "$REPORT_DIR/route-smoke.json"

log "Done."
log "Reports:"
log "  $REPORT_DIR/krabi-lanes.md"
log "  $REPORT_DIR/krabi-lanes.json"
log "  $REPORT_DIR/route-smoke.json"
