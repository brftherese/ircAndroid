# The Lounge Parity Notes

Last updated: 2025-11-25

## Snapshot (Nov 25, 2025)

### Android Client Highlights

- `app/src/main/java/com/example/ircclient/MainActivity.kt` + Compose UI: multi-buffer chat, sidebar, mentions drawer, slash commands, moderation menus, search, quiet hours, notifications (summarized in `README.md`).
- Persistence uses DataStore helpers in `Prefs.kt`, `MentionsStore.kt`, and `NetworkProfilesStore.kt` for settings, highlights, and saved network profiles.
- Local scrollback mirror powered by Room (`ChatDatabase`, `ScrollbackStore`) keeps per-buffer history with 30-day/5k-row caps between launches.
- Full-screen settings sheet groups Identity, Highlights/Ignores, NickServ/SASL, Connection, Quiet hours, and Text size controls with helper text and NickServ Identify/Register actions so guidance matches The Lounge notifications settings UX.
- Orientation changes no longer nuke the session UI—connection form values, the active buffer, joined channel list, and composer text all survive rotation via `rememberSaveable` helpers.
- When reconnecting to channels that advertise `draft/chathistory`, the client now issues `CHATHISTORY AFTER …` requests using the last persisted timestamp so missed messages are replayed automatically.
- Link previews implemented client-side in `LinkPreview.kt` with Compose cards rendered in chat rows.
- Notifications + highlight badges via `NotificationHelper.kt`, `AppForeground.kt`, and the persisted mentions store.
- Saved network profiles selectable in the connection form (profile picker Compose UI).
- `ConnectionService.kt` keeps the socket in a foreground service backed by a persistent notification, so sessions remain alive even when the activity is backgrounded or reclaimed.

### The Lounge (Upstream) Highlights

- Web client built with Vue components under `thelounge/client/components` plus Pinia-like stores in `client/js/`.
- Node.js server (`thelounge/server/server.ts`, `clientManager.ts`, `storageCleaner.ts`) keeps IRC sessions alive, handles auth, logging, and background jobs.
- Plugin + feature toggles under `thelounge/server/plugins` (web push, uploader, message storage, auth, STS, etc.).
- Configurable behavior via `defaults/config.js` (public/private mode, HTTPS, prefetch policies, lockNetwork, WEBIRC, identd, storage policy, theme, file uploads, default networks, etc.).
- Shared protocol helpers in `thelounge/shared/` and comprehensive tests in `thelounge/test/`.

## Feature Parity Matrix

| Area | The Lounge reference | Android status | Notes / Action |
| --- | --- | --- | --- |
| Mentions drawer & badges | `client/components/Mentions.vue`, `client/js/store/mentions.js` | ✅ Implemented (`MentionsStore.kt`, Compose drawer) | Persistence + UI match upstream behavior, including badges and clear action. |
| Buffer unread/markers | `client/components/Buffer.vue`, store marker helpers | ✅ Implemented (scroll markers + dividers) | `pendingScrollTime`/`lastRead` logic replicates markers/new message divider behavior. |
| Link previews | `client/js/plugins/preview.js`, `client/components/Message.vue` | ✅ Implemented (`LinkPreview.kt`, Compose cards) | Currently first-URL only; lacks caching/policy toggles. |
| Multi-network profiles | `client/components/NetworkForm.vue` + server connection handling | ⚠️ Partial (`NetworkProfilesStore.kt`) | Profiles saved/switchable, but only one active network connection at a time; no simultaneous multi-network buffers. |
| Always-on host & auth | `server/server.ts`, `server/clientManager.ts`, plugins `auth/*` | ⚠️ Partial (`ConnectionService.kt`) | Foreground service keeps a single-user session alive with notification controls, but we still lack multi-user auth and a true daemon like The Lounge server. |
| Server-side history/logging | `server/plugins/messageStorage/*`, `storageCleaner.ts` | ⚠️ Partial | Local Room cache mirrors TL scrollback, and we now request `CHATHISTORY` playback on reconnect, but there is still no multi-device sync or true server-side storage yet. |
| Web push / offline alerts | `server/plugins/webpush.ts`, client `Windows/Notifications.vue` | ❌ Missing | Android has local notifications only; no push relay or sync to other devices. |
| File uploads / media proxy | `server/plugins/uploader.ts`, `client/components/ChatInput.vue` | ❌ Missing | No upload UI, storage, or proxy handling in Android app. |
| Prefetch policies & caching | `defaults/config.js` (`prefetch*` keys), `server/plugins/storage.ts` | ⚠️ Partial | Client fetches previews without cached storage or user-configurable caps. |
| Themes & plugin ecosystem | `client/components/Settings/Appearance.vue`, `server/plugins/*` | ❌ Missing | Single Material theme, no plugin/theme APIs. |
| Advanced network plumbing | `defaults/config.js` (WEBIRC, identd, lockNetwork, reverseProxy) | ❌ Missing | UI only exposes basic server/TLS/SASL/channels configuration. |

## Detailed Porting Notes

### Mentions Drawer

- **Upstream reference:** `client/components/Mentions.vue`, `client/js/store/mentions.js`
- **Store strategy:** The Lounge keeps a global `mentions` array with `{target, channel, msg, time}`. Entries are appended when `isHighlight` or DM, pruned to max length, and stored in LocalStorage for persistence.
- **UI strategy:** `Mentions.vue` shows a list grouped by buffer, with tap-to-jump and a “Clear” button that empties the store. The Mentions icon displays a badge with unread count.
- **Android plan:** Mirror the store by maintaining `MutableStateList<HighlightEntry>` in `MainActivity`, backed by DataStore for persistence. Reuse `shouldHighlight` logic so notifications, drawer, and highlight counts share one decision point. Compose UI: floating sheet/modal similar to `SearchResultsDialog` but with mention metadata and jump-to-buffer behavior.
- **Status:** Landed in `MainActivity.kt` (state + dialog), `ConnectedTopBar.kt` (badge trigger), and `MentionsStore.kt` (JSON persistence capped at 100 entries). Jumping dismisses individual mentions while keeping history until cleared.
- **Bonus:** Buffer chips now render highlight badges using the persisted mentions list so counts survive restarts until you clear/dismiss the entries, matching The Lounge’s emphasis on unread highlights.

### Buffer Markers & Unread

- **Upstream reference:** `client/js/store.js` (buffer model), `client/components/Buffer.vue`, marker helpers.
- **Store strategy:** The Lounge tracks `buffer.highlight`, `buffer.unread`, and `buffer.marker` (timestamp/line pointer). They reset counts when the buffer becomes active and insert a “new messages” divider where marker crosses.
- **UI strategy:** Markers render as `div.new-messages` segments; highlight counts appear next to buffer names.
- **Android plan:** Generalize our `BufferMeta.firstUnreadTime` into a structured marker object (timestamp + event index). When receiving chat events, set marker if buffer inactive; on activation, clear counts and marker. Use the same marker info to render `NewMessagesDivider` and to align mention jumps to the exact event.
- **Status:** `pendingScrollTime` plus `lastRead` bookkeeping now ensure mention jumps land on the exact event, list state scrolls to the stored index, and buffer counts/markers reset immediately whenever a buffer becomes active.

### Rich Link Previews / Prefetch

- **Upstream reference:** `client/js/plugins/preview.js`, `client/components/Message.vue`.
- **Store strategy:** The Lounge server prefetches metadata and emits `msg.previews`. Client caches them per message.
- **Android plan:** Implement a lightweight HTTP prefetch in the app (respecting size limits) or rely on a bridge API. Mirror their throttling and content-type filtering to avoid loading unsafe content.
- **Status:** Prototype landed with `LinkPreview.kt` (size/time-limited HTML fetcher) and Compose cards in `ChatRow`. First URL in each message renders a preview with retry handling, mirroring The Lounge’s preview cards without server assistance.

### Multi-Network Profiles

- **Upstream reference:** `client/components/NetworkForm.vue`, server config handling.
- **Strategy:** They treat each connection as a separate “network” with independent buffers stored server-side, plus profile switchers.
- **Android plan:** Expand `SavedConfig` into a list stored via DataStore, provide UI for adding/editing networks, and spin up separate `IrcClient` instances (or sequential connect) per network.
- **Status:** Added client-side profile store (`NetworkProfilesStore.kt`) with JSON DataStore persistence, Compose picker on the connection form, and a manager dialog for add/rename/delete. We still connect to one network at a time, but swapping between saved configs is now parity-friendly.

### History Replay & CHATHISTORY

- **Upstream reference:** Lounge servers emit playback via `CHATHISTORY`/`znc.in/playback` so reconnecting clients instantly backfill buffers.
- **Android plan:** Detect the `draft/chathistory` (or `chathistory`) capability during CAP negotiation, store the last persisted timestamp per buffer, and issue `CHATHISTORY AFTER <channel> <timestamp> <limit>` once per reconnect.
- **Status:** `IrcClient` now requests the capability and exposes `chathistoryEnabled`. `MainActivity` tracks joined channels and, after reconnect, asks for up to 200 events per channel using the last timestamp from Room if available, or falls back to `LATEST` for brand-new buffers. This keeps scrollback aligned with The Lounge’s auto-playback behavior whenever the server supports it.

## Parity Gaps & Opportunities (Nov 25, 2025 Audit)

1. **Always-on multi-user host** — The Lounge’s Node.js daemon (`server/server.ts`, `server/clientManager.ts`) supports private/public deployments, authentication, and keeps IRC connections alive even when users disconnect. `ConnectionService.kt` now mirrors the “always connected” behavior for a single user via a foreground service + persistent notification, but there is still no multi-user auth, shared daemon, or server-side resume/log-out workflow.
2. **Server-side history + log storage** — Upstream persists scrollback via the `server/plugins/messageStorage/*` (SQLite/text) and exposes cleanup policies (`storageCleaner.ts`). Our local Room cache mirrors 30-day / 5k-row history per buffer and we now consume `CHATHISTORY` to fill gaps after reconnects, but there is still no multi-device sync or server-authoritative log. Action: explore syncing the Room store with a Lounge backend for a single source of truth.
3. **Web push + notification relays** — Files like `server/plugins/webpush.ts` integrate with browsers for push notifications and offline alerts. Android-only local notifications fire for highlights, but we lack any push subscription or relay for other devices. Action: decide whether to integrate with Firebase Cloud Messaging or bridge to a Lounge host.
4. **File upload + media proxy** — TL ships uploader hooks (`server/plugins/uploader.ts`) and client UI (drag-and-drop in `client/components/ChatInput.vue`, previews in `client/components/Message.vue`). Our app has no upload feature, no proxying of HTTP assets, and no storage/quota UI. Action: spec an Android share-sheet upload flow targeting a configurable Lounge uploader endpoint.
5. **Prefetch storage + media policies** — Beyond simple OpenGraph fetches, TL can cache thumbnails via `prefetchStorage` and enforce size caps/config toggles (see `defaults/config.js`’s `prefetch*` keys). Android currently fetches previews client-side without caching, proxying, or user settings. Action: expose preview toggles in Settings and cache responses per URL with eviction.
6. **Plugin/theme ecosystem** — The Lounge exposes hooks under `server/plugins/*` and supports installable client themes (`client/components/Settings/Appearance.vue`). We have a fixed Material theme and no extension points for custom scripts/features. Action: introduce theme presets (light/dark/high contrast) and consider a plugin surface for automation scripts.
7. **Advanced network plumbing** — WEBIRC, identd/oidentd, multi-network binding, and `lockNetwork`/`reverseProxy` controls in `defaults/config.js` are not represented in the Android UI. We only expose basic TLS, SASL, and channel options. Action: audit `defaults/config.js` options to determine which are feasible on-device (WEBIRC credentials, rejectUnauthorized toggle, etc.).
8. **User/account management** — Lounge private mode supports multi-user credentials, password resets, and CLI management (`server/command-line`). Android lacks any notion of local user profiles beyond saved network configs. Action: decide whether to add multiple “persona” profiles or rely entirely on server auth.

## Backlog / TODO Tracker

| ID | Item | Status | Notes |
| --- | --- | --- | --- |
| TL-01 | Persist scrollback locally (Room/SQLDelight) to mirror `messageStorage` behavior | ✅ Done | Room-based `ScrollbackStore` seeds buffers on launch, persists every `UiEvent`, and prunes to 30 days / 5k rows per buffer. |
| TL-02 | Multi-network simultaneous sessions (one per saved profile) | Todo | Requires refactoring `IrcClient.kt` to handle multiple sockets and UI tabs per network. |
| TL-03 | Configurable link preview policy (enable/disable, size/time caps) | Todo | Hook settings screen into `LinkPreview.kt`, align with `defaults/config.js` controls. |
| TL-04 | File upload + share-sheet integration | Todo | Needs API contract with Lounge uploader plugin; consider fallback if endpoint unavailable. |
| TL-05 | Push/relay notifications | In progress | Plan: integrate FCM token registration + relay endpoint so Lounge `webpush` plugin can fan-out highlight payloads even when app is backgrounded. |
| TL-06 | Theme presets + appearance settings | Todo | Compose Material 3 dynamic colors + manual overrides akin to `client/components/Settings/Appearance.vue`. |
| TL-07 | Advanced connection options (WEBIRC, rejectUnauthorized, reconnect policy) | Todo | UI work + validation referencing `defaults/config.js`. |
| TL-08 | In-app documentation + onboarding | In progress | Convert README highlights into an in-app help sheet; keep parity doc updated. |
| TL-09 | Automation around Live Edit / dev workflow | Blocked | Requires Android Studio setup off-device; document manual steps (see prior instructions). |

Keep this table updated whenever we start/finish a parity task so it remains a reliable consultation point during ongoing iterations.
