# What to check before releasing ForgeLog

The checks a release needs that no test can make. `docs/play-console.md` has the text the Console
asks for; this has the things to do with a phone in your hand.

The backup round trip is done — the one that mattered most, and it passed. So is everything on the
repository side: the contact address, the keystore, a signed APK and bundle, and a GitHub release.
What is left needs the Play Console or a graphics editor. What a test *can* cover already is: 830
unit tests, 38 instrumented on each of API 26 and API 36, lint clean on debug and release. What
follows is what those cannot reach.

Verified on a device already, so it is not repeated below: the first-run experience, the full
program → workout → summary loop on the shrunk build, process death mid-workout, saved state under
"don't keep activities", rotation during a live rest, deleting a program day without losing the
sessions logged from it, and that the release APK ships five permissions, no `INTERNET`, no QA code
and is not debuggable.

Also verified on the shrunk build, since R8 is the one thing the tests cannot reach: every screen
opens without a crash — Home, Programs, a program's detail, the day builder, the exercise library,
the exercise editor, History, a session's detail, Analytics and Settings — with the process id
unchanged throughout, which is the reliable signal, since a crash restarts the process. And the set
editor survives repeated rotation with its dialog up, which is the sharpest test available: it
restores eleven `rememberSaveable` fields, two of them through `ExerciseUnit.valueOf` and
`SetType.valueOf`, and an enum whose constant names R8 had renamed would throw there and nowhere
else. The mapping file says why it holds: R8 renames the enum's static *fields* (`LB` becomes `f`)
but leaves the name strings the constructor is handed, which is what `valueOf` matches on.

What that leaves unchecked in the shrunk build is the backup round trip, because it goes through the
system file picker — which is section A below, and still the thing most worth doing.

---

## A. Run these on a phone

### The backup round trip

**Done, on the shrunk build, on 14 September — and it passed.** Export, delete everything, restore:
the programs came back with their day counts and completion counts unchanged, and a session spot-
checked afterwards held the same duration, volume, set count and exercise count as before. A photo
offered at the restore step was refused with a sentence, with the data left alone.

Kept here because it has to be redone whenever the backup format or the restore path changes. Export
and restore is the app's only recovery path, and it goes through the system file picker, which the
automated checks cannot drive.

Everything either side of the picker is covered: the serializer, the wipe-and-replace transaction,
the `ContentResolver` that carries the bytes (seven device tests), the JSON key names pinned against
a backup file typed out by hand as version 1.0 wrote it, the R8 keep rules read back out of the
release dex, and some 1,500 deliberately corrupted files proving the importer refuses rather than
crashes. The picker is the seam.

Do it on the **QA** build, so real training is never the thing at risk. If you do run it against the
real app, export first.

```bash
./gradlew installReleaseCheck
```

- [x] Settings → **Export JSON** → choose a location. Confirm the file is there and is not
      near-empty.
- [x] Settings → **Delete all data**, typing `DELETE` to confirm.
- [x] Settings → **Restore from JSON** → pick that file. Confirm the message's session and set
      counts match what was there, and that History and Analytics show the same totals as before.
- [x] At the Restore step, pick **a file the app did not write** — a photo will do. Confirm it is
      refused with a sentence that says what is wrong, not a crash.

### Know what a damaged database looks like

Nothing to do here, and nothing to provoke on your own phone — this is covered by tests and was
checked end to end on an emulator. It is listed so the message is recognisable if it ever appears.

SQLite deletes a database it cannot open and Room builds an empty one in its place, so the app used
to come up looking freshly installed with the history silently gone. It now keeps the unreadable
file beside the new one as `forgelog.db.unreadable-<millis>` and says so on Home until dismissed,
pointing at Settings → Restore. If you ever see that card, the backup from section A is the thing
that saves you.

### The build people will actually install

- [ ] The **release** build installs on your own phone, not just the emulator. MIUI refused a new
      package id over USB earlier in this project (`INSTALL_FAILED_USER_RESTRICTED`), which is why
      the `releaseCheck` build carries a `.qa` suffix — worth knowing the real thing goes on.
- [ ] **One full workout on it**, with the phone locked through a rest: the notification's
      countdown runs on the lock screen, and the rest alert vibrates when it reaches zero.
- [ ] **A first run on an empty install** — and your own read on the first-workout summary, where a
      first-ever log counts as a record in each applicable kind. That is deliberate (see section E);
      this is your chance to look at it with fresh eyes.

---

## B. Only you can do these

- [x] ~~Put a real contact address in `PRIVACY.md`~~ — done
- [x] ~~Host the privacy policy~~ — done, GitHub Pages serves it from this repository at
      <https://happyc0der.github.io/ForgeLog/privacy-policy>
- [x] ~~Create the release keystore and `keystore.properties`~~ — done, and `assembleRelease` now
      produces a signed APK. Both files are gitignored; neither has ever been committed
- [x] ~~`./gradlew bundleRelease`~~ — done, 5.2 MB
- [ ] Paste the privacy policy URL into the Console's Privacy policy field and into the Data safety
      form
- [ ] Screenshots, the 1024×500 feature graphic, the 512×512 icon
- [ ] The Data safety form and the foreground-service declaration — both already drafted in
      `docs/play-console.md`

---

## C. Let Play test it on hardware neither of us has

- [ ] Upload the AAB to **internal testing** before going live. Play then produces a **pre-launch
      report**: your app run on a spread of real devices and API levels, with any crashes, ANRs and
      accessibility findings listed. It reaches OEM skins and hardware that no emulator here covers,
      it costs nothing, and it is the best coverage available at this point.

---

## D. Worth doing, but do not hold the release for them

- [ ] A long soak — a real 60–90 minute session with the phone locked, checking the notification and
      the rest alerts stay right across doze
- [ ] Managed devices at other API levels. Cheap to add, but 26 and 36 already bracket the range,
      and the app's only API-conditional branch is the Android 13 notification permission, which
      both ends exercise as they are
- [ ] Tablet, landscape at the largest font, and right-to-left layout. `supportsRtl` is declared but
      there are no right-to-left translations, so this is cosmetic
- [ ] **More of your own training logged in it.** Genuinely the highest-yield bug finder left

---

## E. Known, deliberate, and not to be "fixed"

Listed so they are not mistaken for bugs later.

- **A first-ever log of a lift is a record in each applicable kind**, each labelled "First time
  logged", so a first session can show several at once. Deliberate — `PersonalRecords` explains the
  reasoning: a first entry is a record, but calling it an improvement would be a lie, and the UI has
  to be able to tell the difference.
- **Duplicating a program twice gives two programs named "PPL (copy)"**, and duplicating a day
  chains to "Push Day B copy B". Duplicate names are allowed throughout the app by design.
- **Samoa's skipped day** — 30 December 2011, which the country passed over crossing the date line —
  renders as one empty bar in that week's volume chart. That is the correct answer: nothing can fall
  inside a day that did not happen, and the week still tiles exactly. Pinned by a test.
