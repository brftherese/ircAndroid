# Releasing IRC Android Client

This guide covers building a signed release APK/App Bundle and publishing to Google Play.

## Prereqs

- Temurin/OpenJDK 17 with `jlink` available (the GitHub Actions workflow uses Temurin 17). Set `JAVA_HOME` accordingly, e.g.:

```bash
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
```

- Android SDK installed (`$HOME/Android/Sdk`) and `platform-tools` on your PATH
- Keystore at `keystore/ircclient.keystore` with passwords in `local.properties`
- App version updated in `app/build.gradle.kts` (bump `versionCode`, adjust `versionName`)

## Build artifacts

- Release APK:

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

- Play App Bundle (recommended for Play Console):

```bash
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

## Local install options

- Install release APK on a connected device:

```bash
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
adb install -r app/build/outputs/apk/release/app-release.apk
```

- Install AAB to a device using bundletool (optional):

```bash
# Download bundletool
curl -L -o bundletool.jar https://github.com/google/bundletool/releases/download/1.17.2/bundletool-all-1.17.2.jar
# Build an .apks archive signed with the existing keystore
PW=$(grep '^RELEASE_STORE_PASSWORD=' local.properties | cut -d= -f2)
java -jar bundletool.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app/release.apks \
  --ks=keystore/ircclient.keystore \
  --ks-pass=pass:$PW \
  --ks-key-alias=$(grep '^RELEASE_KEY_ALIAS=' local.properties | cut -d= -f2) \
  --key-pass=pass:$PW \
  --connected-device
# Install to the connected device
java -jar bundletool.jar install-apks --apks=app/release.apks
```

## Verification

Use these steps to quickly verify a release build on device.

1. Connect a device

- USB debugging: Enable Developer Options and USB debugging on the phone, then:

```bash
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
adb devices -l
```

- Wireless debugging (Android 11+): On the phone enable Wireless debugging, then pair or connect:

```bash
# If using a pairing code from phone (Pair device)
adb pair <phone-ip>:<pairing-port>
# Then connect
adb connect <phone-ip>:<adb-port>
adb devices -l
```

1. Install and launch

```bash
adb install -r -d app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.example.ircclient/.MainActivity
```

1. Sanity checks in-app

- Defaults: server `irc.libera.chat`, port `6697`, TLS on, channel `#android`.
- Connect: Tap Connect; you should see messages populate after connection.
- Auto-join: After welcome (001) or end-of-MOTD (376), the app auto-JOINs the default channel.
- Send: Post a message in the composer; it should appear in-channel without "no external messages" errors.

1. Logcat (optional)

```bash
adb logcat -s IrcClient AndroidRuntime
```

- Look for: "Connecting…", server numerics (001/376), "Auto-joining", inbound PRIVMSG, and PING/PONG handling.

## Troubleshooting

- No devices in `adb devices`: Reconnect cable, switch USB mode to File Transfer, or use Wireless debugging.
- `INSTALL_FAILED_OLDER_SDK`: Ensure device API >= 24.
- Connection timeout: Verify host/port/TLS. Default is TLS 6697. Check network/firewall.
- TLS handshake issues: Ensure correct SNI/host; try mobile data vs Wi‑Fi.
- "no external messages": Wait for JOIN to complete; the app auto-joins after 001/376.

## Google Play upload

1. Create an app in Play Console (package: `com.example.ircclient`)
2. Upload `app-release.aab` to an internal testing track
3. Fill release notes, roll out to testers
4. After validation, promote to closed/open or production

## Versioning

- Increment `versionCode` for every release; `versionName` is a human-readable string
- Location: `app/build.gradle.kts` under `defaultConfig`

## Keystore safety

- Backup `keystore/ircclient.keystore` securely; losing it prevents updates to the app
- Do not commit the keystore or passwords; `local.properties` is git-ignored

## GitHub Actions automation

We ship artifacts automatically via `.github/workflows/release.yml`.

### Required secrets

Add the following repository secrets so the workflow can sign builds:

- `RELEASE_KEYSTORE_B64`: Base64-encoded contents of `keystore/ircclient.keystore`
- `RELEASE_STORE_PASSWORD`: Password for the keystore
- `RELEASE_KEY_ALIAS`: Alias defined inside the keystore
- `RELEASE_KEY_PASSWORD`: Password for the alias

### Triggering a release

1. Bump `versionCode`/`versionName`, update changelog/docs, and commit.
2. Either push a tag (`git tag v1.0.3 && git push origin main --tags`) or run the workflow manually.

- GitHub → Actions → **Release APK** → **Run workflow** → enter tag (e.g., `v1.0.3`).

Then the workflow will:

- Set up JDK 21 + Android SDK
- Install platform tools/build-tools via an explicit `sdkmanager` step so each package is requested separately
- Recreate `local.properties` using the secrets and decode the keystore
- Run `./gradlew assembleRelease`
- Upload the APK and SHA256 checksum as workflow artifacts and as GitHub release assets (creating/updating the release for the tag)

Use `workflow_dispatch` when you want to rebuild an existing tag (e.g., after fixing secrets) without pushing a new commit.

> **Note:** The keystore decode step now always runs so a missing/blank `RELEASE_KEYSTORE_B64` secret fails fast instead of silently skipping the signing setup. Double-check the secret before triggering the workflow.
