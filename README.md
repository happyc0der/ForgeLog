# ForgeLog

A personal, fully offline Android app for building gym programs, running workouts from them, logging sets quickly, and comparing sessions over time.

No accounts, no backend, no analytics, no ads. Your training data lives on your phone and leaves it only if you export it yourself.

## Status

Honest state of the app, so nobody goes looking for a screen that isn't there yet.

| Area | State |
|---|---|
| Programs, days, exercise configuration | Working |
| Exercise library | Working |
| Start-workout planner with previous-session panel | Working |
| Active workout logger + foreground timer notification | Working |
| Home dashboard | Working |
| History | Working |
| Analytics | Working |
| Backup / export / import | Working |
| Settings | Working |

## Prerequisites

- **Android Studio** (a version that supports AGP 9.3.x — Narwhal or newer)
- **JDK 17+** — the Gradle toolchain resolver fetches what the build needs
- **Android SDK platform 37** and build tools, installed through the SDK Manager
- A physical Android phone on **Android 8.0 (API 26) or newer**

No `local.properties` is committed; Android Studio writes it on first open. If you build from the command line without ever opening the IDE, create it yourself:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

## Open in Android Studio

1. **File → Open** and pick the repository root (the folder with `settings.gradle.kts`).
2. Let Gradle sync finish. The version catalog is `gradle/libs.versions.toml` — add dependencies there, not inline.
3. Select the `app` run configuration.

## Run on a physical phone (Redmi Note 12 Pro)

The app is tuned for a 6.67" 1080×2400 20:9 display in portrait.

1. On the phone: **Settings → About phone → MIUI version**, tap it 7 times to unlock Developer options.
2. **Settings → Additional settings → Developer options**, then enable:
   - **USB debugging**
   - **Install via USB**
   - **USB debugging (Security settings)** — MIUI needs this one to install over USB, and it requires being signed into a Mi account
3. Connect the phone by USB and set the USB mode to **File transfer (MTP)**. Charging-only mode will not expose ADB.
4. Accept the "Allow USB debugging?" RSA prompt on the phone.
5. Confirm the device is visible, then install:

```bash
adb devices
./gradlew installDebug
```

If `adb devices` shows `unauthorized`, revoke USB debugging authorisations in Developer options and reconnect.

## Build a debug APK

```bash
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and install it directly if you would rather not use ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew connectedDebugAndroidTest    # instrumented tests, needs a connected device
./gradlew assembleDebug testDebugUnitTest   # what to run after every change
```

## Backup and restore

Everything goes through Android's Storage Access Framework, so ForgeLog holds no storage permission and only ever reads or writes a file you picked yourself.

**Export a full backup** — Settings → *Export everything (JSON)*. Choose any location (Drive, Downloads, an SD card). The file contains every exercise, program, day, session, logged set and note, and is named `forgelog-backup-<date>.json`.

**Restore** — Settings → *Restore from JSON*. The file is validated in full before anything is written: wrong format version, dangling references, unknown enum values, duplicate ids and empty documents are all refused, and a refused file leaves your existing data untouched. A successful restore **replaces** everything currently in the app, so export first if the current data matters.

**Export sets to CSV** — Settings → pick a range, then *Export sets (CSV)*. One row per logged set, RFC 4180 quoted, ready for a spreadsheet.

**Delete everything** — Settings → *Delete all data*, which requires typing `DELETE`. There is no copy unless you exported one.

Worth doing once before you start logging anything you care about: export, then restore, and confirm your data comes back. That round-trip is also covered by automated tests (`BackupRoundTripTest`).

Note that `android:allowBackup="true"` is still set in the manifest, so Google's auto-backup may also include the database. That is not something to rely on, and it is under review.

## Architecture

Single Gradle module, MVVM with a repository layer.

```
ui/ (Compose screens + ViewModels)  →  domain/repository (interfaces)  →  data/repository  →  Room DAOs
                                       domain/ (pure Kotlin rules, no Android imports)
```

Room stores every timestamp as UTC epoch millis; the UI renders them in the device zone. Session rows keep snapshots of exercise name, pointers, and how-to URL so editing an exercise later never rewrites past workouts. Schema JSONs under `app/schemas/` are a committed migration contract, and there is deliberately no destructive-migration fallback — a missing migration fails loudly instead of silently wiping history.

Project rules for contributors and AI assistants: [`.cursor/rules/forgelog.mdc`](.cursor/rules/forgelog.mdc), imported by [`CLAUDE.md`](CLAUDE.md).

## Privacy

All data stays on this device unless you choose to export it. The app requests no internet permission, contains no analytics or crash reporting, and makes no network calls.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
