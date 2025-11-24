# The Lounge Parity Notes

## Mentions Drawer

- **Upstream reference:** `client/components/Mentions.vue`, `client/js/store/mentions.js`
- **Store strategy:** The Lounge keeps a global `mentions` array with `{target, channel, msg, time}`. Entries are appended when `isHighlight` or DM, pruned to max length, and stored in LocalStorage for persistence.
- **UI strategy:** `Mentions.vue` shows a list grouped by buffer, with tap-to-jump and a “Clear” button that empties the store. The Mentions icon displays a badge with unread count.
- **Android plan:** Mirror the store by maintaining `MutableStateList<HighlightEntry>` in `MainActivity`, backed by DataStore (or simple JSON file) for persistence. Reuse `shouldHighlight` logic so notifications, drawer, and highlight counts share one decision point. Compose UI: floating sheet/modal similar to `SearchResultsDialog` but with mention metadata and jump-to-buffer behavior.
- **Status:** Landed in `MainActivity.kt` (state + dialog), `ConnectedTopBar.kt` (badge trigger), and `MentionsStore.kt` (JSON persistence capped at 100 entries). Jumping dismisses individual mentions while keeping history until cleared.

## Buffer Markers & Unread

- **Upstream reference:** `client/js/store.js` (buffer model), `client/components/Buffer.vue`, marker helpers.
- **Store strategy:** The Lounge tracks `buffer.highlight`, `buffer.unread`, and `buffer.marker` (timestamp/line pointer). They reset counts when the buffer becomes active and insert a “new messages” divider where marker crosses.
- **UI strategy:** Markers render as `div.new-messages` segments; highlight counts appear next to buffer names.
- **Android plan:** Generalize our `BufferMeta.firstUnreadTime` into a structured marker object (timestamp + event index). When receiving chat events, set marker if buffer inactive; on activation, clear counts and marker. Use the same marker info to render `NewMessagesDivider` and to align mention jumps to the exact event.

## Rich Link Previews / Prefetch

- **Upstream reference:** `client/js/plugins/preview.js`, `client/components/Message.vue`.
- **Store strategy:** The Lounge server prefetches metadata and emits `msg.previews`. Client caches them per message.
- **Android plan:** Either implement a lightweight HTTP prefetch in the app (respecting size limits) or rely on a bridge API. Mirror their throttling and content-type filtering to avoid loading unsafe content.

## Multi-Network Profiles

- **Upstream reference:** `client/components/NetworkForm.vue`, server config handling.
- **Strategy:** They treat each connection as a separate “network” with independent buffers stored server-side, plus profile switchers.
- **Android plan:** Expand `SavedConfig` into a list stored via Room/DataStore, provide UI for adding/editing networks, and spin up separate `IrcClient` instances (or sequential connect) per network.

---

These notes should expand as we tackle each backlog item—always cite the peer files in The Lounge and capture the data flow we’re mirroring before coding.
