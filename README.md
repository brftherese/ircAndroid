# IRC Android Client

> Offline-first (history, highlights, and settings remain available without a live connection) IRC for Android with Jetpack Compose, Room scrollback, and feature parity goals inspired by The Lounge.

## About

IRC Android is a Kotlin/Compose client that keeps conversations available offline, survives process death with a Room-backed scrollback cache, and layers modern conveniences (multi-network profiles, mentions drawer, link previews, moderation tools, highlight-aware notifications) on top of a lightweight IRC socket implementation. The long-term roadmap tracks The Lounge’s capabilities so mobile users can expect the same power tools.

## Current Status

- Latest release: `v1.2.0` (signed APK + checksum on GitHub Releases).
- GitHub Actions workflow builds every tagged release with Temurin 17, decodes the signing keystore from repository secrets, and publishes the package automatically.
- Roadmap progress and parity notes live in `TODO.md` and `docs/`.

## Highlights

- Multi-network profiles with SASL settings, quiet hours, and per-buffer mute controls.
- Room-powered scrollback (30 days / 5k events) plus CHATHISTORY replay to fill gaps after reconnects.
- Mentions drawer, unread/highlight chips, quiet hours, and Android 13+ notification permission handling.
- Rich slash command support, inline moderation menus, link previews, search, and dismissable service buffers.
- Force-light/e-ink mode, adjustable font scaling, and compact layout for tablets or glare-heavy screens.

## Downloads & Packages

- **Package ID:** `com.example.ircclient`.
- **Signed release APK:** Download `app-release.apk` and `app-release.apk.sha256` from the latest GitHub Release—each is built and signed by GitHub Actions.

## Quickstart

### Requirements

- Temurin/OpenJDK 17 with `jlink` available (`JAVA_HOME` should point at that install).
- Android SDK Platform/Build Tools 34 (installed automatically when using the Gradle wrapper + workflow).
- Android Studio Giraffe+ (optional) or just the Gradle wrapper included in the repo.

### Build from Source

```bash
git clone https://github.com/brftherese/ircAndroid.git
cd ircAndroid
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Need a local network to test against? Use `dev/test-irc/` (miniircd + ergo configs) and follow `dev/test-irc/README.md` to launch a throwaway IRC stack in Docker.

Looking for end-user guidance? See the full [User Guide](docs/wiki/user-guide.md) for installation, connection setup, notifications, and troubleshooting tips.

## Documentation

- `docs/wiki/user-guide.md`: End-user setup and usage guide (installing, connecting, highlights, troubleshooting).
- `RELEASING.md`: Signing, versioning, and GitHub Actions release process.
- `CHANGELOG.md`: Release-by-release history.
- `TODO.md`: Parity roadmap and outstanding work.
- `docs/thelounge-parity.md`: Detailed feature comparison with The Lounge.
- `docs/tl01-scrollback.md`, `docs/tl05-notifications.md`: Deep dives into storage and notifications.

## Roadmap & Contributions

Track upcoming work in `TODO.md` (organized by parity priority). Contributions are welcome—open an issue describing the change, keep commits small, and run `./gradlew testDebugUnitTest` plus `./gradlew lint` before opening a PR. Documentation updates and parity notes are just as valuable as code.

## License

This project is provided as-is for demo purposes.
