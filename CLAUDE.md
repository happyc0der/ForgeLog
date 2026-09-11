# ForgeLog

Personal-use, offline, native Android gym workout tracker. Single `app` module.

## Project rules

The project rules are maintained in one place and imported here so Cursor and Claude Code read the same text:

@.cursor/rules/forgelog.mdc

Treat that file as authoritative. If a rule there and something in this file ever disagree, the rule file wins and this file should be corrected.

## Orientation

```
app/src/main/java/dev/happyc0der/forgelog/
├── data/
│   ├── local/        Room: ForgeLogDatabase, migrations, entities, DAOs, relations, converters
│   ├── mapper/       EntityMappers.kt — entity ↔ domain, and where relation lists get sorted
│   ├── repository/   repository implementations
│   └── settings/     DataStore-backed settings
├── di/               Hilt modules: DatabaseModule, RepositoryModule, DispatchersModule, WorkoutModule
├── domain/
│   ├── model/        pure data classes + enums with storageValue/fromStorage
│   ├── repository/   repository interfaces
│   ├── library/      exercise-library rules (HowToUrl, delete policy, copy naming)
│   ├── time/         TimeProvider, ZoneProvider, WeekBoundary
│   └── workout/      volume, estimated 1RM, previous-workout matching, rest, set prefill/visibility
├── ui/               one package per feature; each has Screen + ViewModel
└── workout/          WorkoutForegroundService (live session timer notification),
                      RestTimerController (the rest countdown, app-wide -- not in the logger's
                      ViewModel, which dies when the logger is left), RestAlarm (the exact alarm and
                      receiver that buzz at rest end even with the phone asleep)
```

Reuse before writing new: `VolumeCalculator`, `EstimatedOneRepMax`, `PreviousWorkoutMatcher`, `SessionRest`, `SetPrefill`, `SetFieldVisibility`, `DurationInput`, `formatElapsed`/`formatSeconds`, `WeekBoundary`, `ConfirmDialog`, `EmptyState`/`LoadingState`/`ErrorState`, `TextInputDialog`.

## Commands

```bash
./gradlew assembleDebug testDebugUnitTest   # build + unit tests — run after every phase
./gradlew installDebug                      # install on the connected phone
./gradlew connectedDebugAndroidTest         # the same UI tests on a device (needs one attached)
```

Compose UI tests live in `app/src/sharedTest/`, which is compiled into both the unit-test and
instrumented source sets. They use `AndroidJUnit4`, so the same file runs under Robolectric on the
JVM and on a real device — write UI tests there, not in `androidTest`, unless something genuinely
needs a device.

## Room

Database version and migrations live in `data/local/ForgeLogDatabase.kt` and `data/local/ForgeLogMigrations.kt`. Schema JSONs under `app/schemas/` are a committed contract — add new ones, never regenerate or edit old ones. There is no destructive-migration fallback, by design: a missing migration must fail loudly rather than wipe training history.
