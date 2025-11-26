# IRC Android Client

An offline-first Android IRC client written in Kotlin with Jetpack Compose. It now includes multi-buffer chat, notifications, highlights, search, Room-backed scrollback, and persistent preferences on top of the core IRC socket implementation.

## Key Features

- Configurable server/port/TLS/nick/user plus SASL credentials and auto-join channel list
- Multi-buffer sidebar for status, channels, and direct messages with unread/highlight counts (highlight badges survive restarts via the mentions store) and inline “×” controls to dismiss service/PM buffers like Auth or ChanServ until they see activity again
- Dedicated mentions drawer with a top-bar badge that aggregates highlights across buffers and persists the last 100 entries
- Full-screen advanced settings sheet that explains highlight rules, ignore lists, NickServ Identify/Register actions, TLS/appearance toggles, quiet hours, and font scaling in grouped sections
- Force-light (e-ink friendly) theme override so text stays high-contrast even when the device is in dark mode, perfect for e-readers or washed-out LCDs
- NickServ helpers, slash commands (`/join`, `/msg`, `/me`, `/raw`, `/search`, etc.), and smart autocomplete suggestions
- Highlight detection with quiet hours, mute controls, and local notifications (Android 13+ runtime permission aware)
- Rich link previews for HTTP/HTTPS URLs using a safe, size-limited client-side fetcher inspired by The Lounge preview plugin
- Multiple saved network profiles so you can jump between servers/accounts without retyping credentials
- Room-powered scrollback cache that restores the most recent 30 days / 5k events per buffer instantly on launch and survives process restarts
- Automatic history replay whenever the server advertises `draft/chathistory`: on reconnect the client issues `CHATHISTORY AFTER …` per joined channel so anything that happened while you were offline is backfilled immediately.
- Contextual moderation menus: long-press members or chat messages to op/deop/voice/kick/ban when your channel mode allows it
- Per-channel search, message history, day dividers, and “new messages” markers
- Persistent settings via DataStore plus optional compact layout and adjustable font scaling
- Foreground service + notification controls keep the IRC session alive even when the UI is backgrounded, with a quick action to disconnect from the status shade
- Orientation-safe UI: rotating between portrait and landscape no longer severs the session or clears the buffer list, so you can keep chatting uninterrupted

## Project Structure

- `app/src/main/java/com/example/ircclient/IrcClient.kt`: Simple IRC socket client (plain TCP or TLS)
- `app/src/main/java/com/example/ircclient/MainActivity.kt`: Activity + Compose UI, state management, dialogs, commands
- `app/src/main/java/com/example/ircclient/ui/theme/Theme.kt`: Simple Material3 theme wrapper
- `app/src/main/AndroidManifest.xml`: Declares `INTERNET` permission and launcher activity

## Requirements

- Android Studio (Giraffe+ recommended) or Gradle 8.5+
- Android SDK: compile/target SDK 34, min SDK 24
- Temurin/OpenJDK 17 with `jlink` available (set `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` globally so Gradle and Android Studio both launch with the same toolchain)

## Build Troubleshooting

- If Gradle exits with `Execution failed for JdkImageTransform` / `androidJdkImage`, it means the build cannot find a working `jlink` binary. The fix that consistently works in WSL and Linux is to install the full OpenJDK 17 toolchain (`sudo apt install openjdk-17-jdk openjdk-17-jre`) because the headless packages omit `jlink` ([Stack Overflow, Dimitri Petrov, May 2024](https://stackoverflow.com/a/78493625)).
- Point Gradle at that toolchain by exporting `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (or configuring Android Studio ➜ Build Tools ➜ Gradle ➜ Gradle JDK to the same path). The repo also sets `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` in `gradle.properties` so IDE-driven builds pick up the right toolchain automatically.
- If the issue persists after installing JDK 17, re-run `./gradlew --stop && ./gradlew clean assembleDebug` to flush any daemons that were still using the old JDK. As a last resort, downgrade AGP to 8.4.2; multiple reports confirm newer AGP releases exercise the `androidJdkImage` transform more aggressively ([Stack Overflow question 69619829](https://stackoverflow.com/questions/69619829/could-not-resolve-all-files-for-configuration-appandroidjdkimage)).

## Getting Started

Open the project in Android Studio and let it sync. Build and run on a device/emulator with network access. The repository already includes a Gradle wrapper, so no additional setup is required.

Alternatively, from a terminal:

```bash
cd ircAndroid
./gradlew assembleDebug
```

Then install the APK from `app/build/outputs/apk/debug/` onto a device.

## Usage

### Connecting

1. Enter server (e.g., `irc.libera.chat`) and port (`6697` for TLS or `6667` for plain).
2. Toggle TLS, pick a nick/user/real name, and configure the primary/auto-join channels.
3. (Optional) Add SASL credentials, highlight/ignore lists, and quiet hours in **Advanced settings**.
4. Tap **Connect**. The sidebar shows Status + joined channels/direct messages with unread badges.
5. Use the **Network profile** picker to switch between saved configurations or open the profile manager to add/rename/delete entries. Each profile remembers all of the form + settings dialog values.

### Chatting & Commands

- Composer accepts plain text or slash commands. Examples:
  - `/join #channel`, `/part`, `/me`, `/msg nick text`, `/raw PRIVMSG ...`, `/whois nick`
  - `/search keyword` opens a dialog scoped to the active buffer
- Suggestions drop-down appears when typing `/` commands or `@nick` mentions.
- Long-press a username in the Users list or a chat row to open moderation actions (op/deop/voice/kick/ban); options stay disabled unless your nick currently has the right mode.
- Buffers can be muted, marked read, and switched quickly via the sidebar or AssistChip shortcuts.
- Highlighted mentions per buffer appear as a small red badge on each chip; counts persist thanks to the mentions drawer history until you clear/dismiss them.
- Sharing a URL automatically spawns a preview card (title + description + host). Tapping the card opens the link; previews are fetched on-device with strict size/time limits to avoid loading unsafe content.
- Close service or PM chips by tapping the tiny “×” so Auth/ChanServ clutter disappears until a new message arrives.

### Mentions Drawer

- Tap the @ badge in the top app bar to open the Mentions drawer. It lists recent highlights (channel mentions or direct messages) newest-first.
- Selecting an entry jumps straight to the originating buffer and marks that mention as read while keeping the history for later review.
- Use **Clear** in the dialog to wipe the stored history; otherwise, up to 100 mentions persist between launches via DataStore, matching The Lounge’s behavior.

### Notifications & Quiet Hours

- Highlight detection triggers notifications for direct messages or configured highlight terms.
- Quiet hours can be enabled with a start/end hour window to suppress alerts.
- Android 13+ prompts for the notification permission on first launch.

### Persistence

- All connection + appearance settings are saved via DataStore and restored on launch.
- Room (`ChatDatabase`, `ScrollbackStore`, `PersistedEventMapper`) mirrors The Lounge `messageStorage` behavior locally, so each buffer seeds its last ~5k events (30-day retention) immediately after reconnecting.
- Font scaling and compact layout toggle help adapt to phones/tablets.

## Notes

- Some networks require TLS or SASL auth; both are supported but advanced account workflows (e.g., NickServ registration) still rely on manual commands.
- The client handles reconnect, names tracking, highlights, and notifications locally. Further polish (themes, plugin hooks, etc.) can be layered on.
- Logcat still mirrors raw protocol lines for debugging.

## License

This project is provided as-is for demo purposes.
