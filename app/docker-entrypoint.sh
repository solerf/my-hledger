#!/usr/bin/env bash
# Container entrypoint for the hledger-app image.
#
#   hledger-web     -> 127.0.0.1:5000  (data gateway, --serve-api)
#   my-hledger-app  -> 0.0.0.0:8081    (Go viewer; serves frontend assets)
#
# HLEDGER_JOURNAL must be set at `docker run` time and point to a file under
# /opt/hledger_data (the bind-mounted host data dir).

set -euo pipefail

: "${HLEDGER_JOURNAL:?HLEDGER_JOURNAL is required (e.g. /opt/hledger_data/2026.hledger.journal)}"
: "${HLEDGER_WEB_URL:=http://localhost:5000}"
: "${APP_ASSETS_DIR:=/opt/app/assets}"

if [[ ! -f "$HLEDGER_JOURNAL" ]]; then
  echo "error: HLEDGER_JOURNAL=$HLEDGER_JOURNAL not found inside the container" >&2
  exit 1
fi

echo ">>> starting hledger-web on 127.0.0.1:5000  (journal: $HLEDGER_JOURNAL)"
hledger-web --serve-api \
  -f "$HLEDGER_JOURNAL" \
  --host 127.0.0.1 --port 5000 &
hledger_web_pid=$!

trap 'kill "$hledger_web_pid" 2>/dev/null || true' EXIT INT TERM

echo ">>> starting app on 0.0.0.0:8081  (assets=$APP_ASSETS_DIR, hledger-web=$HLEDGER_WEB_URL)"
exec env \
  APP_ASSETS_DIR="$APP_ASSETS_DIR" \
  HLEDGER_WEB_URL="$HLEDGER_WEB_URL" \
  my-hledger-app
