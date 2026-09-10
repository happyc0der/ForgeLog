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
| Home dashboard | Placeholder — see the build plan |
| History | Placeholder |
| Analytics | Placeholder |
| Settings | Placeholder |
| Backup / export / import | Not implemented yet |

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

**Not implemented yet.** Until the backup phase lands, training data exists only in the app's private Room database and there is no supported way to get it off the device or move it to another phone. Two consequences worth knowing now:

- Uninstalling the app, clearing its data, or changing its application id destroys the database.
- `android:allowBackup="true"` is currently set in the manifest, so Google's auto-backup may include the database. That is not a feature anyone should rely on, and it is under review.

When the feature lands it will use the Storage Access Framework: a JSON export of everything, a validated JSON import behind a confirmation, and a CSV export for a chosen date range — all user-initiated, all to a location you pick.

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
