# IRC Android User Guide

This wiki-style guide walks through installing the app, connecting to a network, and using the key features that differentiate IRC Android from a basic client.

## 1. Installing the App

1. Visit the latest [GitHub Release](https://github.com/brftherese/ircAndroid/releases) and download `app-release.apk` plus the matching `app-release.apk.sha256`.
2. (Optional) Verify integrity by running `sha256sum app-release.apk` locally and comparing it to the `.sha256` file.
3. On your Android device, enable "Install unknown apps" for your browser/file manager, then open the APK to install.
4. Future updates will appear on the Releases page; install newer APKs the same way. Your saved profiles, highlights, and scrollback remain intact between installs.

## 2. First Launch & Connection Setup

1. Open the app and tap **Add network profile**.
2. Fill in:
   - **Server/Port** (e.g., `irc.libera.chat` / `6697` for TLS, `6667` for plain).
   - **Nickname/User/Real name**.
   - **Auto-join channels** (comma-separated list) if desired.
3. Toggle **TLS** when connecting to secure ports.
4. Optional advanced fields:
   - **SASL username/password** for auth-only networks.
   - **Highlight terms** (comma-separated words that trigger notifications).
   - **Quiet hours** to mute notifications between specific times.
5. Press **Save profile**. Profiles keep every setting—including advanced options and UI preferences—so you can jump between servers quickly.
6. Tap **Connect**. The Status buffer reports handshakes and JOINs; the sidebar lists all open channels/queries.

## 3. Navigating the Interface

- **Sidebar chips**: Swipe in from the left edge or tap the hamburger icon to open the buffer list. Badges show unread counts (gray) and highlights (red).
- **Mentions drawer**: Tap the @ badge in the top bar to review the last 100 highlights across all buffers. Tapping an entry jumps to that buffer and marks the mention read.
- **Composer**: Type plain text or slash commands (`/join`, `/part`, `/me`, `/msg`, `/whois`, `/search`). Autocomplete appears for `/` commands and `@nick` mentions.
- **Context menus**: Long-press a username in the user list or a message bubble to access moderation tools such as op/deop, voice, kick, or ban. Options enable only if your nick has the necessary channel mode.
- **Dismiss service buffers**: Tap the small × on Auth, NickServ, or PM chips to hide them until new activity arrives.

## 4. Offline & Scrollback Behavior

- All scrollback is cached locally with Room. Up to ~5,000 events (≈30 days) per buffer load instantly even without a network connection.
- Mentions and notification history persist via DataStore. If the device loses connectivity, you can still read history, review highlights, and tweak profiles. Messages queueing while offline is not supported yet; the composer disables sending until the socket reconnects.

## 5. Notifications & Quiet Hours

- Grant the notification permission on Android 13+ when prompted.
- Highlights trigger notifications for direct messages or configured highlight terms. Tap a notification to jump straight into the relevant buffer.
- Configure **Quiet hours** in the advanced settings sheet to silence alerts during specific times (e.g., 23:00–07:00). The mentions drawer still records highlights silently during that window.

## 6. Appearance & Accessibility

- **Force light (e-ink) mode** keeps high contrast regardless of system theme—ideal for e-readers or outdoor use.
- Adjust **Font scale** and **Compact layout** to fit more content on large phones/tablets.
- More theme presets are tracked in `TODO.md` under personalization.

## 7. Troubleshooting

| Symptom | Fix |
| --- | --- |
| Cannot install APK | Ensure "Install unknown apps" is enabled for your browser/file manager. |
| TLS errors or disconnects | Verify the server/port combination and toggle TLS accordingly. Try a different network to rule out captive portals. |
| No highlights/notifications | Confirm highlight terms are set, notification permission granted, and quiet hours are not active. |
| Room history missing | Check device storage permissions and ensure you stayed connected long enough for messages to sync. Reconnecting forces a 30-day backfill via `CHATHISTORY` when supported. |
| Commands fail | Use `/raw` to send manual IRC commands for debugging and monitor the Status buffer for error numerics. |

## 8. Feedback & Support

- File issues or feature requests on GitHub with logs/steps to reproduce.
- The roadmap in `TODO.md` shows which capabilities are being prioritized next. Contributions—docs, bug fixes, or UX polish—are welcome via pull requests.
