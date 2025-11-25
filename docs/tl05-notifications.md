# TL-05 — Push / Relay Notifications

Last updated: 2025-11-25

## Goals

- Receive highlight / DM alerts even when the Android app is backgrounded or killed.
- Keep parity with The Lounge `webpush` plugin by letting the Lounge server act as the source of truth for highlight detection.
- Avoid shipping our own always-on Android service; leverage Firebase Cloud Messaging (FCM) for delivery.

## Architecture overview

1. **Token registration**
   - Integrate Firebase Cloud Messaging in the Android app (add `com.google.firebase:firebase-messaging`).
   - On launch, fetch the FCM registration token and POST it to a configurable relay endpoint along with the user’s Lounge credentials/profile ID.
   - Store relay URL + auth token in DataStore (new settings section).

2. **Relay service**
   - Minimal companion service (could be a Cloud Function, tiny Node app, or extension of The Lounge `webpush` plugin) that receives `POST /register` with `{ deviceId, fcmToken, userId }`.
   - Relay stores mapping of Lounge user → FCM token(s).
   - When The Lounge emits a highlight/DM (existing `webpush` hook already called in `server/plugins/webpush.ts`), extend it to call the relay with the payload (`title`, `body`, `channel`, etc.).

3. **Push payload**
   - Relay calls FCM `send` API with a `data` payload containing the channel, nick, snippet, and optional deep-link target.
   - Android app’s `FirebaseMessagingService` receives the payload, builds a notification (reusing `NotificationHelper`), and optionally stores it in the mentions list.

4. **Security considerations**
   - Relay endpoints secured with API keys or signed JWT from the app.
   - Allow opt-out in settings; never send tokens to unknown servers without user consent.

5. **Failure modes**
   - If relay unreachable, fall back to current local notification path when the app is foregrounded.
   - Token refresh events must re-register with the relay.

## Implementation steps

1. Add Firebase BOM + messaging dependency, plus `google-services.json` support (Gradle plugin and config).
2. Create `PushSettings` UI (toggle + relay URL/API key inputs) persisted via DataStore.
3. Build `PushRegistrar` that:
   - Listens for config changes.
   - Requests `FirebaseMessaging.getInstance().token` and posts to relay.
   - Handles token refresh via a custom `FirebaseMessagingService` override.
4. Extend `NotificationHelper` to accept remote payloads (channel, title, body, deep-link intent).
5. Document relay expectations + sample implementation (extend TL `webpush` plugin or provide standalone Node script).
6. Testing plan:
   - Use Firebase console to send test messages, ensure notifications appear when app is backgrounded/terminated.
   - Manual integration test with The Lounge relay once implemented.

## Open questions

- Should the relay live inside this repo (e.g., `/server/push-relay` folder) or stay external? (Leaning external but doc should reference it.)
- Do we also want to trigger push notifications directly from the Android client when it detects highlights locally? (Would require background service; not planned.)
- How do we deduplicate remote vs local notifications when the app is foregrounded? (Likely by checking `AppForeground.isForeground` before showing remote notifications.)
