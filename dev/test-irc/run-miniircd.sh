#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

PORT="${PORT:-6667}"
LISTEN_IP="${LISTEN_IP:-0.0.0.0}"

if [[ ! -d .venv ]]; then
  python3 -m venv .venv
fi

source ./.venv/bin/activate
python -m pip install --upgrade pip >/dev/null 2>&1 || true
python -m pip install -q miniircd

mkdir -p chanlogs

MOTD_FILE="$(pwd)/motd.txt"
if [[ ! -f "$MOTD_FILE" ]]; then
  cat > "$MOTD_FILE" << 'EOF'
Welcome to the Local Test IRC server (miniircd)

This server is for local development/testing only.
Try joining #test after connecting.
EOF
fi

echo "Starting miniircd on ${LISTEN_IP}:${PORT}..."
.venv/bin/miniircd --daemon --ports "$PORT" --listen "$LISTEN_IP" --motd "$MOTD_FILE" --channel-log-dir "$(pwd)/chanlogs"

IP=$(hostname -I | awk '{print $1}')
echo "miniircd started. Connect your app to ${IP}:${PORT} (TLS off)."
echo "To stop: pkill -f miniircd"
