# Changelog

All notable changes to this project are documented in this file. The format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project aims to follow semantic versioning once the API stabilizes.

## [Unreleased]

### Planned

-

### Pending Changes

- _Nothing yet._

## [1.3.0] - 2025-11-26

### Changes

- Link preview cards now render OpenGraph thumbnails and open a full-screen, pinch-to-zoom media viewer when tapped.
- Settings gained a Link previews section with an enable/disable toggle and a cache clear action for the new preview store.
- Link preview fetches are now backed by a 10-minute LRU cache so repeated URLs render instantly without hammering the remote host.

## [1.2.0] - 2025-11-26

### Added

- Multi-network profile picker with persisted preferences, SASL credentials, and quiet-hour/notification controls.
- Room-backed scrollback with 30-day and ~5k event retention, plus automatic `CHATHISTORY` replay on reconnect to fill offline gaps.
- Mentions drawer, unread/highlight chip badges, contextual moderation menus, and link preview fetching inspired by The Lounge UX.
- Force-light (e-ink friendly) theme toggle and adjustable typography controls to keep the UI legible on washed-out displays.
- GitHub Actions "Release APK" workflow that installs SDK tooling, decodes the signing keystore from secrets, publishes signed APKs, and attaches SHA256 checksums to GitHub Releases.

### Changed

- README reorganized to surface About, downloads/packages, documentation, and roadmap information without overwhelming the landing page.

### Fixed

- Removed the hard-coded `org.gradle.java.home` override from `gradle.properties` so GitHub-hosted runners rely on the configured Temurin installation instead of a missing path.
