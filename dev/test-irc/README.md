# Local Test IRC Server Options

This folder provides two easy ways to run a private IRC server on your LAN so the Android client can connect without risking bans on public networks.

Option A (fastest): Python miniircd (no TLS, no SASL)

- Pros: 1-command setup, great for connectivity/reconnect/UI testing
- Cons: No SASL, minimal IRCv3 support

Option B (full features): Ergo IRCd in Docker (modern IRCv3 + SASL)

- Pros: SASL, server-time, echo-message, stable
- Cons: Requires Docker; simple config needed

---

## A) Run miniircd (no TLS)

Prereqs: Python 3.8+, pip.

Commands:

```bash
cd "$(git rev-parse --show-toplevel)"/ircAndroid/dev/test-irc
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip miniircd
# Start the server on port 6667, listening on all interfaces
miniircd --ports 6667 --listen 0.0.0.0 --motd "Local test IRC"
```

Notes:

- If the port is in use, pick another (e.g., 6669) and update the app accordingly.
- On some distros, use `python -m venv` instead of `python3`.

Connect from the Android app:

- Server: your PC’s LAN IP (e.g., 192.168.1.50)
- Port: 6667
- TLS: Off
- Nick: any
- Channel: `#test`

Firewall (Linux):

```bash
sudo ufw allow 6667/tcp || true
```

Stop the server: press Ctrl+C in that terminal.

---

## B) Run Ergo IRCd (Docker)

Prereqs: Docker (and optionally docker compose).

1) Review/edit `ergo.yaml` (defaults are usable locally; TLS is disabled for simplicity).
2) Start with compose:

```bash
cd "$(git rev-parse --show-toplevel)"/ircAndroid/dev/test-irc
docker compose up -d
```

This maps port 6667 on the host. To see logs:

```bash
docker compose logs -f
```

Connect from the Android app:

- Server: your PC’s LAN IP
- Port: 6667
- TLS: Off (unless you add certs to ergo and enable a 6697 listener)
- SASL: Leave blank unless you add accounts/certs

Stop/remove:

```bash
docker compose down
```

---

## Find your LAN IP

```bash
hostname -I | awk '{print $1}'
```
 
Use that IP in the Android app as the server address.

---

## Tip: Avoid reconnect bans

- Keep reconnect backoff enabled in the app.
- Use the local server while iterating; only switch to public networks once stable.
- If you hit a rate-limit, wait out the cooldown before trying again.
