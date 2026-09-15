# Getting ForgeLog onto Google Play

The order to do things in. `docs/release-checks.md` is what to verify with a phone in your hand and
`docs/play-console.md` is the text the Console asks for — this is the sequence that uses both, so
nothing is done twice or in the wrong order.

Tick as you go. Nothing here can be done for you: it all needs your account, your keystore or your
password.

---

## Already done

- [x] Privacy policy written and **live** at <https://happyc0der.github.io/ForgeLog/privacy-policy>
- [x] Store listing copy, Data safety answers and the `specialUse` foreground-service
      justification — all drafted in `docs/play-console.md`, within Play's character limits
- [x] The backup round trip, on the shrunk build: export, delete everything, restore, and a
      non-backup file refused. See `docs/release-checks.md` section A
- [x] The shrunk build driven on a real phone — every screen opens, the set editor survives rotation
- [x] 830 unit tests, 38 instrumented on each of API 26 and API 36, lint clean on debug and release

---

## 1. Finish the repo side — done

- [x] ~~Replace `[your contact email]` in `PRIVACY.md`~~
- [x] ~~Create the release keystore~~ — `forgelog-release.jks`, RSA 4096, gitignored
- [x] ~~Create `keystore.properties`~~ — gitignored, and `assembleRelease` now signs with your key
      rather than producing an unsigned APK
- [x] ~~Build the bundle~~ — `./gradlew bundleRelease`, 5.2 MB at
      `app/build/outputs/bundle/release/app-release.aab`
- [x] ~~Publish a GitHub release~~ — v1.0, with the signed 2.3 MB APK attached, which is what the
      README's install link points at

Keep the keystore and its password somewhere you cannot lose them. Losing either means never being
able to update the app again — not on Play, not over a sideloaded install.

## 2. Make the store assets

- [ ] **Phone screenshots** — required. Home, the logger mid-workout, History and Analytics show it
      best
- [ ] **Feature graphic**, 1024×500
- [ ] **App icon**, 512×512

## 3. Set the app up in the Console

- [ ] **Register the developer account** — one-time $25 fee. You accept the agreements yourself
- [ ] **Create the app**, and see the signing-key decision below before you do
- [ ] **Store listing** — copy straight from `docs/play-console.md`: app name, short description,
      full description, category, tags
- [ ] **Privacy policy field** → the Pages URL above
- [ ] **Data safety form** — "No" to collection and sharing; the justification wording is drafted,
      and the same URL goes in here too
- [ ] **Foreground service declaration** — the `specialUse` justification is drafted, and already
      matches the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` in the manifest, which Play cross-checks
- [ ] **Content rating questionnaire**, target audience, ads: no, in-app purchases: no

## 4. Test before production, in this order

- [ ] **Internal testing first.** Upload the AAB here, not to production
- [ ] **Read the pre-launch report.** Play runs the app across real devices and API levels and
      reports crashes, ANRs and accessibility findings. It is free, and it is the only answer
      available to "does this work on phones neither of us owns". Pay attention to anything about
      the foreground service — the rest timer is what OEM battery management is most likely to break
- [ ] **Closed testing**, if it applies to you — see the gate below
- [ ] **Apply for production access**, then release

---

## Getting it to yourself and a few friends, with updates that just arrive

Three routes. The first is the one to take, because it does this job *and* the production gate above
at the same time.

### Play internal testing — recommended

Up to 100 testers, added by email address or reached by a share link. They install from the Play
Store as normal and **updates arrive automatically**, the same as any other app. Nothing to sideload,
no "unknown sources" prompt, no reinstalling.

Shipping a bug fix is then:

1. Bump `versionCode` in `app/build.gradle.kts` (`versionName` too if it means something to you)
2. `./gradlew bundleRelease`
3. Upload the AAB to the internal testing track
4. Your friends' phones update on their own

The catch is that it needs the developer account ($25) and the app created in the Console. Since you
want production eventually, that is work you were doing anyway — and the days your friends spend on
it count toward the 12-testers-for-14-days gate. Closed testing works the same way with a larger,
more formal tester list if you need the numbers.

### GitHub Releases — no Play account needed

Attach the signed APK to a release in this repository. Anyone can download and install it, and the
repo is already public so the link works for everyone.

- Build with `./gradlew assembleRelease` — the APK is universal, all four ABIs, about 2.3 MB
- Attach it to a **Release**, do not commit it to the repo
- Whoever installs it has to allow "install unknown apps" for their browser, once

Updating means sending them a new link each time and them installing it by hand. They can skip that
by using **Obtainium**, which watches a GitHub repository's releases and updates the app for them —
worth mentioning to anyone who will be on this for a while.

### Firebase App Distribution — the middle ground

Made for exactly this: testers get an email when a build is ready and install from a link, without
the Console's review steps or the $25 fee. It needs a Firebase project, and it is one more system to
run. Worth it only if you want tester distribution without a Play account.

### Whichever you pick, two rules

**Always the same signing key.** An APK signed with a different key will not install over an
existing one — Android refuses, and the only way through is uninstalling, which deletes their
training. This is also why the signing-key decision below matters before you create the Play app: a
friend who sideloaded from GitHub cannot later update through Play unless the keys match.

**Always a higher `versionCode`.** Android will not install an APK whose `versionCode` is not greater
than the installed one, and Play rejects the bundle outright.

And tell people to **export a backup before updating**, at least while the app is new. There is no
cloud copy by design, and it costs them ten seconds.

---

## Two things that will catch you out

**The signing-key decision, which has to be made before you create the app.** Play App Signing is
mandatory for new apps. By default Google generates the app signing key, which means builds from
Play are signed with a *different* key than anything you sideload — so anyone who installed an APK
from GitHub cannot update through Play without uninstalling first, losing their training. If you
want both routes to match, upload your own key as the app signing key when you create the app.

**The testing gate.** A *personal* developer account created after November 2023 has to run a closed
test with at least 12 testers opted in continuously for 14 days before it can even apply for
production access. That makes the realistic timeline a few weeks rather than a day. Confirm what
applies to your account in the Console, since these rules move.

And one small thing: `versionCode` is `1`. Every upload after the first needs it incremented, or
Play rejects the bundle.
