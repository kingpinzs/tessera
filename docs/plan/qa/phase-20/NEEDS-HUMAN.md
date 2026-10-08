# Phase 20 (Music streaming and Radio) — Jeremy's sign-off checklist

**STATUS: D1 answered 2026-10-08 ("(a)"); its fix is being built. The phone and sign-off rows wait for that build.** Written 2026-10-07 22:56.

- The build was pushed first (`origin/phase-20` = 62eea325), then the ONE round of testing ran on it.
- Emulator rows A1–A7: all pass, 28 of 28 lettered conditions (`RESULTS.md` beside this file). The three static
  checks pass (APK 359,354,356 B; exported components 27 of 27 on the allow-list; no `10.0.2.2` in the release
  network config).
- The one adversarial review (`docs/plan/review/2026-10-07-phase20-trust-review.md`): no HIGH, 3 MEDIUM, 10 LOW,
  3 older-phase items. The one fix round fixed 2 MEDIUM and 6 LOW, each with a test that fails without the fix.
  Whole unit suite after the fixes: 2,341 tests, 0 failures.
- **The fix round came AFTER the rows.** Under the one-round rule no row was run again, so the fixes are covered by
  unit tests only. The two places they touch a screen are in H1 / H2 below (the station logo).
- The phase goes `done` when every row below is signed off: reply with the ids that pass, and what you saw for any
  that do not.

**The phone build does not exist yet.** CI builds only on a push to `main`. Merge the `phase-20` pull request on
GitHub, wait for the build on the repository's "latest" release page, install it, then do the phone rows.
Everything in the phone rows is done on the phone alone — nothing over a cable, adb or a PC. Where a row needs a
value, read it on Start settings > Diagnostics and paste it back.

## The decision that is yours

| id | the question | my lean |
|---|---|---|
| D1 | **A station entry can make the phone send a request into your home network (review finding R20-1, MEDIUM).** The plan's rule reads only the text of a station's address. A station in the community directory can use a name that resolves to a home address (`192.168.1.1.nip.io`, `nas`, `router.lan`), or a public address that redirects there. The phone then sends one request, with a path the station chose, to a device on your Wi-Fi. Nothing comes back to the station and no password leaves, but some routers and smart devices act on such a request. The plan states this gap on purpose (r3 D12). Choices: **A** close it fully — check the real address when connecting, redirects included (likely adds one Media3 module; needs one more push and a re-run of rows A2 and A3); **B** close names only — a redirect still gets through; **C** leave it as planned and accept the risk. Asked in chat 2026-10-07; your reply was "push all changes", which I did not read as an answer. **ANSWERED 2026-10-08 08:16: "(a)" — close it fully. Being built; then one more push, then rows A2 and A3 run once more.** | A |

## Phone rows (the CI build, on the S25 Ultra)

| id | what you do | what to report |
|---|---|---|
| P1 | **Radio for real.** Open Music > radio. Search "jazz", play a station. Lock the phone for 30 minutes on Wi-Fi; again on mobile data; once more with Data Saver on. With the home server signed in and crossfade set, listen across the change between two server tracks. | Still playing each time. The "Streaming over mobile data" line seen. The crossfade heard. From Settings > Diagnostics: the `radio: directory fetched <n>` line (n should be 40,000 or more), one `stream: connected http://…` line (a plain-http station played), and the `radio: fm feature=<bool>` line. Data used and battery, from Android's own app-usage screens. |
| P2 | **Buttons and calls.** Headset or Bluetooth pause / play. Next / previous. Take a call during a station. | Pause / play work. Next / previous step through your favourites. The call pauses the station and nothing resumes by itself. |
| P3 | **Listen on.** Music > radio > find a song. Search a song, open it, tap "Listen on Pandora", then each other app listed. | Per app: it landed on its search for the song, or it only opened. The `[music] handoff:` line for each. A song playing in that app shows only on that app's own pinned tile. |
| P4 | **Tess by voice**, unlocked and over the lock screen: "play jazz radio", "play radio", "listen to <song> on Pandora". If you are ever on a Wi-Fi with a sign-in page, tap a station there. | The station plays, also over the lock screen. "Listen to … on …" asks for an unlock when locked. On a sign-in Wi-Fi: "Sign in to this Wi-Fi network first". |

## Sign-off rows (all *accept*: a design or a pick with no Windows 10 Mobile source — accept it or overrule it)

| id | what to look at | where | what was built |
|---|---|---|---|
| H1 | The live now-playing screen and the mobile-data line. | Phone, P1. Emulator pictures: `A2-rerun1/01-nowplaying-30s.png`, `A3-rerun1/01-metered.png`, `A3-rerun1/02-reconnecting.png`. | The scrubber is a full-width bar with no thumb; "LIVE" stands where the elapsed time was; the sleep list has no "end of track". A station's second line shows its name once. "Streaming over mobile data" is one small line above the bar. The art is the station's logo, fetched by the shell — **changed by the fix round, not re-run: check a logo shows.** |
| H2 | The radio pivot. | Phone, P1. Pictures: `A1-rerun1/02-radio-pivot.png`, `06-reopen-radio.png`. | Order: a note line, favourites, then search stations / by genre / by country / refresh stations, then "streaming" (find a song, home server), then all stations by popularity, 100 at a time. A hold on a station offers only add to / remove from favourites. Logos show on favourites and the playing station only — **the favourite logos are on the changed path too.** The light theme was not looked at. |
| H3 | The song search and "Listen on" pages, and the order of the apps. | Phone, P3. Pictures: `A6-rerun1/01-catalogue-results.png`, `03-title-after-install.png`. | "find a song" is a row on the radio pivot. The search runs when you press Search, not as you type. Apps are listed with Pandora first, then the other recorded services, then any other music app by name. |
| H4 | The home server's music. | Phone, P1. Pictures: `A7/02-server-albums.png` … `05-server-song-playing.png`. | "home server" is a row on the radio pivot. Sign-in is Movies & TV's form (one server, one sign-in for both). The view is the server's name over albums / artists / songs. The listing is kept while Music is open, not saved to the phone. |
| H5 | Recorded picks: how reconnecting feels, Tess's wording, data and battery. | Phone, P1 and P4. | A dropped station retries by itself for 60 seconds (at 2, 4, 8, 16, 30 s), then stops with "This station isn't answering"; a station whose server closes the stream is retried the same way. While it retries, the Music tile can flip between its idle and playing faces, keeping the title. Tess says "Playing <station>." |

## Things you should know (no row; say if you want any changed)

- **Not seen anywhere yet:** an HLS station playing on a device (the plan's fixtures have none); P1 on the real
  directory is the first time one can play.
- **Added during the build, not in the plan:** the Music player now refuses anything a station's playlist names that
  is a local file or a private address (phase 17 left a test that demanded this check the day HLS was added).
- **Recorded, not fixed (review, LOW):** "listen to … on youtube" can pick an app merely named "YouTube" ahead of
  YouTube Music; another app can raise radio-browser's click counter through Music's session; an HLS playlist can
  name a file already in your own queue (nothing leaves the phone); stacked accent marks in a station name are kept.
- **Older phases' parts, on the Blocked-on ledger, not fixed:** `ServerRules.isPrivate` reads a few unusual address
  spellings as public (L20-1; no known effect in use).
- **The station stream itself** is requested with Android's default User-Agent; the directory and catalogue requests
  carry `Tessera/`, as the plan asks.
- **Twice during the build** a debug build fetched about two pages from the live radio-browser directory before the
  test settings took effect. Nothing was kept. No test row reached a live server.
- **Fixture ports** on this PC are 8092 and 8093 (other containers hold 8080 and 8081).
