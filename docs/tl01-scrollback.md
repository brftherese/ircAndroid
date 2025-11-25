# TL-01 — Local Scrollback Persistence

Last updated: 2025-11-25

## Goals

- Mirror The Lounge `messageStorage` parity by keeping chat/event history on-device so reconnects and cold starts retain recent context.
- Persist all `UiEvent` types (chat, notices, joins/parts, system numerics, etc.) per buffer.
- Keep storage bounded (time- and count-based) to avoid unbounded growth while covering typical daily usage.

## Implementation summary

- **Storage stack:** `ChatDatabase` (`Room`) with `buffer_events` table and DAO in `app/src/main/java/com/example/ircclient/persistence/`.
- **Serialization:** `PersistedEventMapper` writes compact JSON payloads per `UiEvent` subtype so schema stays stable even as new fields are added.
- **Repository:** `ScrollbackStore` wraps the DAO, enforces 30-day / 5k-row retention, and exposes `append`, `appendAll`, `loadRecent`, and `loadAll` helpers.
- **UI integration:**
   - `MainActivity` instantiates a singleton `ScrollbackStore`, seeds buffers in `MainScreen` via `scrollbackStore.loadAll(SCROLLBACK_SEED_LIMIT)`, and persists every event inside `appendEvent`.
   - Status/system messages now flow through `appendEvent`, so even `/whois` help text and “connect” banners survive restarts.
   - Query buffers restored from disk automatically repopulate the queries list and buffer metadata.
- **Testing:** `JAVA_HOME=/home/ftherese/.jdks/jdk-21.0.9+10 ./gradlew assembleDebug` confirms the new Room dependencies compile; manual smoke test (connect → chat → kill app → relaunch) verifies history rehydrates.

## Proposed architecture (reference)

1. **Room database**
   - New database `ChatDatabase` with a single table `buffer_events`.
   - `BufferEventEntity` columns:
     - `id` (PK, auto)
     - `buffer_key` (`String`, e.g., `#thelounge` or `status`)
     - `event_type` (`String`, enum representing `UiEvent` subclass)
     - `payload` (`String`, compact JSON for type-specific fields)
     - `time` (`Long` ms)
   - Index on `(buffer_key, time DESC)` for fast recent queries; index on `time` for pruning.

2. **Serialization**
   - Helper `PersistedEventMapper` to convert between `UiEvent` and `(eventType, payload)`.
   - Payload format: small JSON via Moshi/Kotlin serialization (no new dependencies if we hand-roll) storing just the fields the subtype needs.

3. **DAO surface**
   - `insert(events: List<BufferEventEntity>)`
   - `latestForBuffer(buffer: String, limit: Int): List<BufferEventEntity>`
   - `deleteOlderThan(cutoff: Long)`
   - `trimToMax(buffer: String, maxRows: Int)` (DELETE where id not in newest N).

4. **Repository**
   - `ScrollbackStore` facade with suspend functions to:
     - Save every event as it streams from `IrcClient`.
     - Load cached events during app start / on buffer activation.
     - Run pruning in the background (e.g., once a day or after inserts) enforcing:
       - **Time window:** keep 30 days of history.
       - **Count per buffer:** cap at 5000 rows each.

5. **UI integration**
   - On `MainScreen` launch, load persisted events for the active buffer(s) and seed `channelEvents` before live data arrives.
   - When connecting to a new channel, prefetch cached rows and append them to `channelEvents[buffer]` prior to showing the spinner.
   - When new `UiEvent`s arrive (and after dedupe logic), append to both in-memory list and `ScrollbackStore`.

6. **Threading**
   - Reuse `lifecycleScope`/`rememberCoroutineScope` to call DAO on `Dispatchers.IO` (Room handles this automatically when using suspend DAOs).
   - For bulk inserts (e.g., initial backlog), wrap in `withContext(Dispatchers.IO)`.

7. **Testing / verification**
   - Unit test `PersistedEventMapper` to guarantee lossless conversions.
   - Instrumented test or local unit test verifying Room DAO (using `Room.inMemoryDatabaseBuilder`).
   - Manual test plan: connect, send chat, kill app, relaunch → history present.

## Open questions

- Should we expose a settings toggle for cache size or retention? (Future TL item.)
- Do we need to persist buffer topic state separately? (Not yet; topics already re-sent on join but could be memoized later.)
- Would proto-based serialization outperform JSON? (Probably unnecessary given small payloads.)

## Follow-ups

1. Add unit tests for `PersistedEventMapper` (round-trip coverage for every `UiEvent`).
2. Add integration tests for `ScrollbackStore` using `Room.inMemoryDatabaseBuilder` to prove retention/pruning.
3. Expose a Settings toggle for “Keep local history” and make the 30-day cap configurable per profile.
4. Consider persisting buffer topics and membership snapshots so UI can render state instantly after relaunch.
5. Explore syncing this scrollback cache with a future TL server endpoint so mobile + web stay aligned.
