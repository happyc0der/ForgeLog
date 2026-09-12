# Play Console text for ForgeLog

Everything the Console asks for, drafted. Nothing here has been submitted.

Character limits are Play's, and each draft is within its limit; the count is given so an edit can
be checked against it.

---

## Store listing

### App name (30 max)

```
ForgeLog: Offline Gym Log
```
*25 characters.*

### Short description (80 max)

```
Build programs, log sets fast, and keep every workout on your own phone.
```
*72 characters.*

### Full description (4000 max)

```
ForgeLog is a training log for people who already know what they are doing in the gym and want a
fast, honest record of it — with nothing in the way.

It works completely offline. There is no account to make, no server to sync with, and no way for
your training to leave your phone unless you export it yourself. The app cannot even reach the
network: it does not request internet access.

BUILD YOUR PROGRAM
Set up programs and training days, with per-exercise targets — planned sets, a rep range, weight,
duration and rest. Duplicate a day to make a variation. Drag to reorder. Keep your own pointers on
each lift so the cues you care about are in front of you when you log it.

RUN THE WORKOUT
Before you start, see what you actually did last time, and adjust today's targets without touching
the program. Reorder, skip or add lifts on the fly. When every planned set hit the top of its rep
range last time, ForgeLog says so and suggests the next weight — one tap makes it today's target.

LOG FAST
One exercise open at a time. Fields appear when they are relevant and hide when they are not. Sets
prefill from the set before, or from the plan. Everything saves as you type, and a workout in
progress survives a restart.

REST TIMER THAT ACTUALLY WORKS
The countdown keeps running wherever you go in the app, counts down on your lock screen in the
workout notification, comes back exactly as you left it — paused, skipped or extended — if Android
closes the app, and buzzes on time even with the phone face down and asleep.

REVIEW
History is searchable and filterable, and every logged session can be corrected afterwards, repeated,
or promoted into a program day. Analytics shows week-over-week comparison, volume by day, sets by
category, heaviest-set and estimated-1RM trends, and your personal bests. Missing data is shown as
missing rather than as zero.

YOUR DATA IS YOURS
A full JSON backup you can restore onto any device, plus CSV export for a spreadsheet — both through
your phone's own file picker, so you choose where they go. Automatic cloud backup is switched off on
purpose. A delete-everything option that makes you type the word first.

Free, with no ads, no analytics, no tracking, and no in-app purchases.
```
*2,228 characters.*

---

## Data safety form

Answer **"No"** to data collection and sharing. ForgeLog declares no `INTERNET` permission, so it
has no means of transmitting anything.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A — nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | **Yes** — Settings → Delete all data, and uninstalling |

If the form insists on a justification for "no data collected": *All data the user enters is written
to the app's private storage on the device. The app declares no internet permission and makes no
network requests. Data leaves the device only when the user explicitly exports a backup or CSV
through the system file picker, choosing the destination themselves.*

---

## Foreground service declaration

Play asks why the `specialUse` type was chosen over the defined types.

**Type declared:** `specialUse` (`android.permission.FOREGROUND_SERVICE_SPECIAL_USE`)

**Justification:**

```
ForgeLog is an offline gym training log. While the user is logging a workout, a foreground service
keeps that session's clock and rest countdown running and shows an ongoing notification with the
elapsed time, so the user can put the phone down or switch apps between sets and return to the same
workout.

None of the defined foreground service types fit. The service tracks no location, plays no media,
transfers no data, makes no calls and does no device management. The "health" type is the closest by
name, but it requires the app to hold HIGH_SAMPLING_RATE_SENSORS or a runtime sensor permission such
as ACTIVITY_RECOGNITION or BODY_SENSORS. ForgeLog reads no sensors and no health data whatsoever —
the user types in what they lifted — so requesting a sensor permission it would never use would be
misleading to the user and contrary to the app's stated purpose of collecting nothing.

The service runs only while a workout is in progress, is started by the user's own action of
beginning a session, and stops as soon as that session is finished or abandoned.
```

The same explanation is already in the manifest, as Play requires:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="Shows a live timer for an in-progress workout so the user can return to logging after leaving the app." />
```

---

## Other Console answers

| Field | Answer |
| --- | --- |
| App category | Health & Fitness |
| Tags | Workout, Fitness, Training |
| Free or paid | Free |
| Contains ads | No |
| In-app purchases | No |
| Content rating questionnaire | No objectionable content in any category — expect "Everyone" |
| Target audience | 18+ (or 13+); not directed at children |
| Privacy policy URL | The hosted copy of PRIVACY.md — see below |
| Data deletion | Covered in the Data safety form above; no account exists to delete |

### Permissions that need no declaration form

All five are install-time or ordinary runtime permissions with no Play declaration requirement:
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (declared above), `POST_NOTIFICATIONS`,
`VIBRATE`, `WAKE_LOCK`.

There is deliberately **no** `USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM`: exact alarms are a
restricted permission, and the rest countdown holds a wake lock for the length of a rest instead.

---

## Hosting the privacy policy

Play requires a publicly reachable URL, and this repository is private, so the policy has to be
published somewhere else. Any of these works:

1. **A separate public repository with GitHub Pages.** Create a public repo, copy `PRIVACY.md` in as
   `index.md`, turn on Pages in its settings, and use the resulting
   `https://<user>.github.io/<repo>/` URL. Free, durable, and version-controlled.
2. **A public GitHub Gist.** Quickest, but the URL is less tidy and easier to lose track of.
3. **Any static host** you already use.

Whichever you choose, replace **[your contact email]** in `PRIVACY.md` before publishing, and paste
the final URL into the Console's Privacy policy field and into the Data safety form.

---

## Before submitting

- [ ] Create a release keystore and `keystore.properties` (never committed — both are gitignored)
- [ ] `./gradlew bundleRelease` for the AAB Play wants
- [ ] Install `./gradlew installReleaseCheck` and run one workout end to end, including an export
      and a restore, on the shrunk build
- [ ] Host the privacy policy and put the URL in the Console
- [ ] Screenshots: phone screenshots are required; Home, the logger mid-workout, History and
      Analytics show it best
- [ ] Feature graphic (1024×500) and the 512×512 icon
