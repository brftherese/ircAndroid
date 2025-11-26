# The Lounge Parity Backlog

The Android client should eventually match the end-user abilities exposed by The Lounge (`/home/ftherese/thelounge`). The items below reflect the gaps identified on 2025-11-22.

## P0 - Core session parity

- [x] Multi-network profiles: allow adding, editing, and switching between multiple server configs instead of the single `SavedConfig` record in `Prefs.kt`. (`NetworkProfilesStore.kt` + Compose profile picker landed 2025-11-23.)
- [x] Always-on connections: move `IrcClient` into a foreground service with notification controls so the app remains connected when the UI process dies, mirroring The Lounge's persistent Node.js backend. (`ConnectionService` + session notification landed 2025-11-25.)
- [x] Durable history: replace the in-memory `channelEvents` map in `MainActivity.kt` with a Room/SQLCipher store so scrollback survives process death and can grow to `maxHistory`-like limits. (`ScrollbackStore` + Room/KSP integration landed 2025-11-25 with 30-day/5k-row retention.)
- [x] Replay + sync: on reconnect, fetch playback via `CHATHISTORY` if the server offers it so buffers pick up anything we missed while offline. (`MainActivity` now requests CHATHISTORY for each joined channel once per reconnect using the last persisted timestamp.)
- [ ] Capability negotiation parity: add support for `monitor`, `away-notify`, `account-tag`, `message-tags`, `setname`, and webirc/STS toggles exposed in `defaults/config.js`.
- [ ] SASL + NickServ UX: surface dedicated flows for logging in/registering, and store encrypted credentials, matching the guided actions The Lounge exposes in its connect dialog. _(Full-screen settings sheet with Identify/Register buttons landed 2025-11-25; encrypted-at-rest storage still open.)_

## P1 - UX + power tools

- [x] Multi-buffer navigation: implement a proper sidebar (channels, queries, networks, statuses) with unread/highlight counts similar to `client/components/Sidebar.vue`. (Compose sidebar + AssistChips landed 2025-11-23.)
- [x] Mentions drawer + new message markers: dedicated view of highlights (see `client/components/Mentions.vue`) and global unread separator markers. (Mentions sheet + badges + marker logic landed 2025-11-24.)
- [x] Orientation resilience: keep the active session, buffer list, and composer input intact across rotation so a landscape switch doesn’t drop you back to Status or force a reconnect.
- [x] Dismissable service/PM chips: add inline close controls to Auth/ChanServ/PM buffers so the chip row stays focused on the channels you care about.
- [ ] Rich link previews + media viewer: port The Lounge `prefetch`/`ImageViewer` flow so URLs unfurl (with caching and size limits) and inline image/video modals are available on Android. (Link previews implemented; media viewer + caching still open.)
- [ ] File uploads: add share sheet + upload handling to parity with `fileUpload` support on the server side.
- [x] Context menus + moderation tools: long-press actions for kick/ban/voice/op, mirroring `ContextMenu.vue`. _(User list + chat row menus with privilege-aware enablement landed 2025-11-23.)_
- [ ] Advanced commands UI: expose helpers for `/ignore`, `/query`, `/topic`, `/notice`, `/invite`, `/away`, `/whois` with autocomplete and validation just like the desktop client. _(In progress – /notice, /invite, /away, `/ignore`, smarter `/topic`, `/query`, and improved `/whois` feedback landed on 2025-11-22/23; remaining work: contextual validation + UI affordances for power users.)_

## P2 - Personalization + integrations

- [ ] Theme system: support multiple color schemes + background images akin to The Lounge theme packages. _(Force-light e-ink theme override shipped 2025-11-25 as the first accessibility preset; need additional palettes + customization.)_
- [ ] Notification profiles: offer per-buffer mute/notify settings, quiet-hours presets, and Android notification channel management per network.
- [ ] Plugin hooks: design extension points so future plugins (e.g., linkifying, URL scrapers) can run locally, similar to `server/plugins/`.
- [ ] Settings sync: optional pairing with a self-hosted The Lounge instance to import/export configurations and logs.
- [ ] Accessibility polish: keyboard support (hardware), screen reader labels, and adjustable font leading comparable to the responsive Vue client.

Track progress by checking items off in this file after each commit.
