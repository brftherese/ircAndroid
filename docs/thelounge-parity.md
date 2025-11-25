# The Lounge Parity Notes

## Mentions Drawer

- **Upstream reference:** `client/components/Mentions.vue`, `client/js/store/mentions.js`
- **Store strategy:** The Lounge keeps a global `mentions` array with `{target, channel, msg, time}`. Entries are appended when `isHighlight` or DM, pruned to max length, and stored in LocalStorage for persistence.
- **UI strategy:** `Mentions.vue` shows a list grouped by buffer, with tap-to-jump and a “Clear” button that empties the store. The Mentions icon displays a badge with unread count.
- **Android plan:** Mirror the store by maintaining `MutableStateList<HighlightEntry>` in `MainActivity`, backed by DataStore (or simple JSON file) for persistence. Reuse `shouldHighlight` logic so notifications, drawer, and highlight counts share one decision point. Compose UI: floating sheet/modal similar to `SearchResultsDialog` but with mention metadata and jump-to-buffer behavior.
- **Status:** Landed in `MainActivity.kt` (state + dialog), `ConnectedTopBar.kt` (badge trigger), and `MentionsStore.kt` (JSON persistence capped at 100 entries). Jumping dismisses individual mentions while keeping history until cleared.
- **Bonus:** Buffer chips now render highlight badges using the persisted mentions list so counts survive restarts until you clear/dismiss the entries, matching The Lounge’s emphasis on unread highlights.

## Buffer Markers & Unread

- **Upstream reference:** `client/js/store.js` (buffer model), `client/components/Buffer.vue`, marker helpers.
- **Store strategy:** The Lounge tracks `buffer.highlight`, `buffer.unread`, and `buffer.marker` (timestamp/line pointer). They reset counts when the buffer becomes active and insert a “new messages” divider where marker crosses.
- **UI strategy:** Markers render as `div.new-messages` segments; highlight counts appear next to buffer names.
- **Android plan:** Generalize our `BufferMeta.firstUnreadTime` into a structured marker object (timestamp + event index). When receiving chat events, set marker if buffer inactive; on activation, clear counts and marker. Use the same marker info to render `NewMessagesDivider` and to align mention jumps to the exact event.
- **Status:** `pendingScrollTime` plus `lastRead` bookkeeping now ensure mention jumps land on the exact event, list state scrolls to the stored index, and buffer counts/markers reset immediately whenever a buffer becomes active.

## Rich Link Previews / Prefetch

- **Upstream reference:** `client/js/plugins/preview.js`, `client/components/Message.vue`.
- **Store strategy:** The Lounge server prefetches metadata and emits `msg.previews`. Client caches them per message.
- **Android plan:** Either implement a lightweight HTTP prefetch in the app (respecting size limits) or rely on a bridge API. Mirror their throttling and content-type filtering to avoid loading unsafe content.
- **Status:** Prototype landed with `LinkPreview.kt` (size/time-limited HTML fetcher) and Compose cards in `ChatRow`. First URL in each message renders a preview with retry handling, mirroring The Lounge’s preview cards without server assistance.

## Multi-Network Profiles

- **Upstream reference:** `client/components/NetworkForm.vue`, server config handling.
- **Strategy:** They treat each connection as a separate “network” with independent buffers stored server-side, plus profile switchers.
- **Android plan:** Expand `SavedConfig` into a list stored via Room/DataStore, provide UI for adding/editing networks, and spin up separate `IrcClient` instances (or sequential connect) per network.
- **Status:** Added client-side profile store (`NetworkProfilesStore.kt`) with JSON DataStore persistence, Compose picker on the connection form, and a manager dialog for add/rename/delete. We still connect to one network at a time, but swapping between saved configs is now parity-friendly.

## Parity Gaps (Nov 25, 2025 Audit)

1. **Always-on multi-user host** — The Lounge’s Node.js daemon (`server/server.ts`, `server/clientManager.ts`) supports private/public deployments, authentication, and keeps IRC connections alive even when users disconnect. Our Android client is a single-user foreground app, so there is no background daemon, account login, or server-side resume/log-out workflow.
2. **Server-side history + log storage** — Upstream persists scrollback via the `server/plugins/messageStorage/*` (SQLite/text) and exposes cleanup policies (`storageCleaner.ts`). We currently rely purely on in-memory Compose lists; closing the app drops history beyond mentions.
3. **Web push + notification relays** — Files like `server/plugins/webpush.ts` integrate with browsers for push notifications and offline alerts. Android-only local notifications fire for highlights, but we lack any push subscription or relay for other devices.
4. **File upload + media proxy** — TL ships uploader hooks (`server/plugins/uploader.ts`) and client UI (drag-and-drop in `client/components/ChatInput.vue`, previews in `client/components/Message.vue`). Our app has no upload feature, no proxying of HTTP assets, and no storage/quota UI.
5. **Prefetch storage + media policies** — Beyond simple OpenGraph fetches, TL can cache thumbnails via `prefetchStorage` and enforce size caps/config toggles (see `defaults/config.js`’s `prefetch*` keys). Android currently fetches previews client-side without caching, proxying, or user settings.
6. **Plugin/theme ecosystem** — The Lounge exposes hooks under `server/plugins/*` and supports installable client themes (`client/components/Settings/Appearance.vue`). We have a fixed Material theme and no extension points for custom scripts/features.
7. **Advanced network plumbing** — WEBIRC, identd/oidentd, multi-network binding, and `lockNetwork`/`reverseProxy` controls in `defaults/config.js` are not represented in the Android UI. We only expose basic TLS, sasl, and channel options.

---

These notes should expand as we tackle each backlog item—always cite the peer files in The Lounge and capture the data flow we’re mirroring before coding.
