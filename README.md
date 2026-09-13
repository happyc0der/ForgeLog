# ForgeLog

A personal, fully offline Android app for building gym programs, running workouts from them, logging sets quickly, and comparing sessions over time.

No accounts, no backend, no analytics, no ads. Your training data lives on your phone and leaves it only if you export it yourself.

## What it does

- **Build programs** — programs, days, and per-exercise targets (planned sets, rep range, weight, duration, rest, pointers, notes). Duplicate a day to make a variation; drag to reorder.
- **Exercise library** — search and filter by category, custom exercises, a how-to link that opens in the browser, and training pointers shown every time you log the lift.
- **Run a workout** — pre-workout panel showing what you actually did last time, targets you can adjust for today without touching the program, reorder/skip/add on the fly, and a rest countdown that keeps running wherever you go in the app, counts down on the lock screen in the workout notification, comes back as you left it -- paused, skipped or extended -- if Android closes the app, and buzzes on time even with the phone asleep face down (it holds the CPU awake for the length of the rest, and lets go the moment it is over). Rest is recorded against the set it followed, and a timed set's hold is not counted as rest.
- **Progression hints** — when every planned set reached the top of its rep range last time, the planner and logger say so and suggest the next weight: +5 lb for upper-body lifts, +5–10 lb for legs (2.5 / 2.5–5 kg). One tap in the planner makes it today's target; the program itself is left alone.
- **Log fast** — one exercise expanded at a time, fields hidden when they are not relevant and revealable when they are, prefill from the last set or last session, autosave on every change, and an in-progress session that survives a restart.
- **Review** — Home shows the last workout and the week so far; History is searchable and filterable, with a full read-only log you can correct; sessions can be repeated or promoted into a program day.
- **Analytics** — week-over-week comparison, volume by day, sets by category, heaviest-set and estimated-1RM trends, and personal bests. Missing data is marked as missing rather than shown as zero.
- **Own your data** — JSON backup and restore plus CSV export, all through the Storage Access Framework, and a delete-everything flow that makes you type the word.

Nothing here needs a network. The app declares no `INTERNET` permission.

What that means for your data is written out in [PRIVACY.md](PRIVACY.md); the Play Console text
is drafted in [docs/play-console.md](docs/play-console.md).

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

## Run on a physical phone

Any phone on Android 8.0 or newer will do. Enable Developer options and USB debugging, plug it in,
and `./gradlew installDebug`.

The steps below are for MIUI/HyperOS specifically, which needs more than most; the layout notes are
for the display it was developed against, a 6.67" 1080×2400 20:9 panel in portrait.

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

To try things without touching your real history, install the QA build beside it: `./gradlew installQa` gives "ForgeLog QA", with an amber icon and its own database. It is the only build with Settings → *Load sample data*, which writes a sample program and twelve weeks of sessions.

## Build a debug APK

```bash
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and install it directly if you would rather not use ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

784 tests, all runnable on a laptop with no device attached. Room runs under Robolectric, so the DAO queries, the migrations and the backup round-trip are genuinely executed rather than mocked — and so do the Compose UI tests, which drive the real screens.

The UI tests live in `app/src/sharedTest/` and run twice from one source: on the JVM with `testDebugUnitTest`, and on a real Android runtime with any of the device tasks below. `app/src/androidTest/` holds the few that genuinely need a device — `AndroidDocumentStoreTest` exercises the `ContentResolver` a backup's bytes travel through, which has no equivalent on a desktop JVM.

```bash
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew pixelApi36DebugAndroidTest   # the same UI tests on an emulator the build boots itself
./gradlew pixelApi26DebugAndroidTest   # and on Android 8, the oldest release the app supports
./gradlew connectedDebugAndroidTest    # or on an attached phone -- not the one you train with:
                                       # it runs against the debug app and can wipe its data
./gradlew assembleDebug testDebugUnitTest   # what to run after every change
```

The two emulator tasks download their own system image on first use and need no phone, which makes
them the default way to run the instrumented suite. Android 8 is opted into from
`gradle.properties`: AGP discourages it because old images are slow, and it is still the only way to
run on the minimum the app claims.

## Backup and restore

Everything goes through Android's Storage Access Framework, so ForgeLog holds no storage permission and only ever reads or writes a file you picked yourself.

**Export a full backup** — Settings → *Export everything (JSON)*. Choose any location (Drive, Downloads, an SD card). The file contains every exercise, program, day, session, logged set and note, and is named `forgelog-backup-<date>.json`.

**Restore** — Settings → *Restore from JSON*. The file is validated in full before anything is written: wrong format version, dangling references, unknown enum values, duplicate ids and empty documents are all refused, and a refused file leaves your existing data untouched. A successful restore **replaces** everything currently in the app, so export first if the current data matters.

**Export sets to CSV** — Settings → pick a range, then *Export sets (CSV)*. One row per logged set, RFC 4180 quoted, ready for a spreadsheet.

**Delete everything** — Settings → *Delete all data*, which requires typing `DELETE`. There is no copy unless you exported one.

Worth doing once before you start logging anything you care about: export, then restore, and confirm your data comes back. That round-trip is also covered by automated tests (`BackupRoundTripTest`).

Android's own auto-backup is configured to match: **nothing goes to Google's cloud**, so the database is never copied to their servers. A direct phone-to-phone transfer when setting up a new device *is* allowed, since that never leaves your possession. Either way, your own export is the copy to rely on.

## Release builds

Release builds are shrunk and obfuscated by R8. `app/proguard-rules.pro` keeps what
kotlinx-serialization reaches reflectively, so exports and imports survive shrinking, and the route
classes keep their names. Signing is picked up from a gitignored `keystore.properties` at the repo
root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it the release variant builds
unsigned.

Before publishing a release build, run the backup round-trip on it by hand — export, reinstall,
import. R8 breaking reflective serialization is the classic failure, and it does not show up in a
debug build. [`docs/release-checks.md`](docs/release-checks.md) sets that out step by step, along
with the rest of what a release needs and no test can cover.

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
