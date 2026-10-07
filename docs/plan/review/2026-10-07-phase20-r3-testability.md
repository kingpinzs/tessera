# Phase 20 r3 — Reviewer 2 (testability / evidence integrity)

Read-only; nothing run on the AVD, no gradle, no audio, no files written.

## V1 — BLOCKING — L422–685: the Acceptance section cannot run as written and must be re-cut

Evidence that it is unrunnable today, not just too long:
- E16 runs through `audio.sh` `say` (L599); the host route is banned. `lib.sh:338` `type_request` is the typed path; `qa/phase-03/scripts/j5.sh:25,34,44` already uses it, and `qa/phase-17/scripts/e10.sh:102-117` types over the keyguard.
- L439 names `lib.sh egress_guard_on`; it lives in `qa/phase-17/scripts/p17.sh:221`, revokes location and bounces `adb root`.
- L449 `lib.sh apk match` is `apk_matches` (`lib.sh:380`). `absent_in` is `p17.sh:81` / `p18.sh:99`, not `lib.sh`.
- L592 reads the token with `GET /Sessions`; the built method is `jellyfin_fixture.sh token-of` (fixtures/jellyfin/README.md item 2: the REST device list carries no token).
- L417, L472 "three MP3s added to `qa/phase-17/fixtures/jellyfin/`": that directory holds no media (README: "Nothing of the server's is kept in the repo"), and `up` seeds only a Movies folder with `qa-steps.mp4`.
- E3b's B / C expectations (L511-514) and the `.pls` set are moot: Q-D A is built (`res/xml/network_security_config.xml` base `cleartextTrafficPermitted="true"`), and addendum §7.7 retires the resolver.

**Replacement preamble.** Rows run ONCE on the pushed debug APK on `emulator-5554`. Start: `layout_restore qa/phase-20/baseline_layout.json` (from `qa/phase-18/baseline_layout.json`), then `ensure_start`. After any launch of another app: `am force-stop app.tileshell` + Home, then re-select the keyboard (`p18.sh` `baseline_start`). Ring reads use `ring_mark` / `ring_since`; absence uses `absent_in` on a slice proven readable. Fixture bases are the debug prefs. No `say`, no `audio.sh`: Tess is typed with `type_request`. No egress guard. Evidence is never committed.

### (1) Device rows — seven

- **R1 Browse radio, favourite a station, come back offline.** Fresh data + prefs; open Music; go to radio; search "jazz"; hold QA Jazz One → add to favourites; airplane on; force-stop; reopen radio. Pass: (a) five `music_pivot_header:*` with radio last, its bounds inside 0–1080; (b) the fixture stations are listed and the search shows only the jazz rows; (c) the hold menu has add to favourites and no pin entry; (d) offline, QA Jazz One is first under favourites and the cached rows are still listed. Host side: every fixture request carries `User-Agent: Tessera/`.
- **R2 Play a station.** Tap QA Jazz One; wait 30 s; open `•••` → sleep; Back; Home. Pass: (a) session PLAYING, album "QA Jazz One", title "QA Song 1" then "QA Song 2" (read once at 30 s, no ± timing); (b) `nowplaying_live_caption` = "LIVE", `nowplaying_scrubber` and `nowplaying_total` absent, `music_menu_sleep:eot` absent; (c) the Music tile shows "QA Song 2", `tile_control:<id>:PAUSE` pauses it, Photos and Camera tile bounds unchanged; (d) the fixture logged exactly one `GET /json/url/<uuid>` (addendum §7.4). Then tap QA File (`url_resolved` = `file:///sdcard/Music/x.mp3`): "can't play this station", session metadata unchanged, `[music] stream: unsupported scheme=file`.
- **R3 The network changes mid-station.** Station playing; `cmd netpolicy set metered-network <id> true`; airplane on; 20 s; airplane off. Pass: (a) `nowplaying_metered` reads "Streaming over mobile data" and it still plays; (b) `nowplaying_track` reads "Reconnecting…" plus `[music] stream: lost, retrying`; (c) playing again with no tap, `[music] stream: reconnected after`. Restore.
- **R4 Lock the screen.** Station playing; `KEYCODE_SLEEP` 60 s; `wake_device`; repeat with a local MP3 (`music_fixtures`). Pass: the player's AudioFlinger track is active at 60 s both times (music17.sh's awk); for the local track `nowplaying_scrubber` and `nowplaying_total` are back.
- **R5 Ask Tess (typed).** `type_request` "play jazz radio"; favourite QA News One, play it, stop, "play radio"; "play zzqx radio"; set a PIN, sleep / wake (e10.sh K-leg), "play jazz radio"; clear the PIN; then `j5.sh`. Pass: (a) reply "Playing QA Jazz One." and that station playing, `[music] search "jazz radio": station QA Jazz One`; (b) "play radio" → QA News One; (c) the miss reply, session unchanged; (d) over the keyguard it plays with `isKeyguardShowing=true` (addendum §8 lists this foreground-service start as unverified — it must stay on the device); (e) j5.sh 8/8.
- **R6 Find a song and listen elsewhere.** Search "qa artist"; open QA Song A; install the stub; tap "Listen on QA Tunes"; C-6; `type_request` "listen to qa artist on qa tunes"; uninstall. Pass: (a) three `catalogue_row:` with the three titles and artwork drawn; (b) no QA Tunes entry before the install, one after; (c) the stub is resumed and its `TileShellQa` line shows the URI with title and artist, `[music] handoff: qa-tunes "QA Song A" ->`, the shell's session unchanged; (d) the typed phrase opens the same stub.
- **R7 Play from the home server.** `jellyfin_fixture.sh up` with a Music folder; sign in (qa / qa-password) at Music's server entry; open albums / artists / songs; tap a song; then tap QA Jazz One. Pass: (a) `[music] server 10.0.2.2:8096: connected`, the three groupings listed; (b) plays with `nowplaying_total` = the file length ± 1 s and a normal scrubber, the line `stream: connected http://10.0.2.2:8096/Audio/<id>/stream codec=mp3` holding no `?`; (c) `leak_scan.sh --logcat --path qa/phase-20 -- "$(token-of)" qa-password` exits 0; (d) the radio fixture's log shows no `Authorization` header and no `ApiKey` on any request (addendum §7.11).

**Static checks (one command each, not rows):** APK ≤ 629,145,600 B; exported components = `qa/phase-03/exported-allowlist.txt`; `aapt2 dump xmltree` of the release config holds no `10.0.2.2`.

**GATE (unchanged, C-16):** one adversarial Opus pass, one fix round, over the station path.

**Fixtures that survive (three servers, one stub):**
- `radio_fixture.py` at :8080 — directory and stream in one. The real API shape (`limit` / `offset`, `/json/tags`, `/json/countries`, `/json/url/<uuid>`), four stations (QA Jazz One with ICY and a StreamTitle switch at 20 s, QA Jazz Two, QA News One, QA File), a request log with headers.
- `catalogue_server.py` at :8081 — one search answer, two PNGs.
- Phase 17's Jellyfin container, plus a Music folder ADD to `jellyfin_fixture.sh up` with two generated MP3s.
- `testapps/qa-tunes` — `CATEGORY_APP_MUSIC`, logs its intent, with a DEBUG-only table row carrying a search form (precedent `ServicesTable.kt:53-55,104-112`).

**Fixtures that go:** `portal_server.py`; the `/edge/` playlists and `content://` station; `/huge/`; `/aac`, `/stops`, `/switch`, the HLS station; `/500`; the pandorastub; the plain stub; the tone-playing stub; `utterances.py` ids (L391-392); the egress guard.

### (2) JVM tests (pure function → cases)

- `StationUrl.accept(url)` — http and https accepted; file, content, asset, rawresource, data, empty, and mixed case such as `FILE:` refused. Leftover `.pls` / `.m3u` → "can't play" (replaces the resolver).
- `StationRedirect.allow(from, to)` — the ruled answer to addendum §7.9: https → http, http → a fixed host, any → non-http.
- `RadioMirror.pick(answer, seed)` — one entry, several, empty, next on failure (§7.5).
- `RadioDirectory.request(page)` / `parse(bytes, cap)` — explicit `limit` and `hidebroken=true`; over the byte cap → too large with the cache kept; empty `url_resolved` skipped; hostile names sanitised.
- `RadioSearch` — name, genre, country over the cached set; favourites first.
- `Favourites` — add / remove; last-played stamp; "play radio" = most recently played, else first, else the zero-favourites answer (V5).
- `MusicSearch.resolve(query, library, radio)` — station before library; "jazz radio" → genre; a library song titled "Radio" versus "play radio"; miss.
- `MusicItemRule` / the service's search branch — a station or server item from the shell's uid is kept; a stranger's URI item is still rebuilt or dropped; a stranger's SEARCH resolving to a station gets the answer Reviewer 1's finding rules.
- `MusicQueueStart.search` — a station queue of favourites starts at the match.
- `LiveForm.isLive(durationMs, marked)` and `moreEntries` — end-of-track omitted when live; armed end-of-track cleared when the item goes live.
- `Reconnect.schedule()` — 2 / 4 / 8 / 16 / 30 s, gives up at 60 s; `lost` is emitted on the first load error (§7.10).
- `NetworkGate.decide(metered, captive, validated, dataSaver)` — play with the line / refuse with "Sign in to this Wi-Fi network first" / play.
- `StreamLine.url(u)` — query and userinfo stripped; every `[music]` line builder.
- `LiveMetadata.merge(station, icyTitle)` — null title → station name; `albumTitle` kept (§7.2).
- `CatalogueRate.nextAt(now, last)` — ≥ 1000 ms apart; 503 handling.
- `Catalogue.parse` and the cover-art URL (`/release/<mbid>/front-250`).
- `MusicHandoff.plan(service, title, artist)` — Pandora `pandorav8://search/<enc>/all`, then `https://www.pandora.com/search/<q>/all`, then plain open; `MEDIA_PLAY_FROM_SEARCH` carries `SearchManager.QUERY` and `EXTRA_MEDIA_FOCUS` (§7.13).
- Discovery excludes `app.tileshell` (V3).
- `"listen to <x> on <app>"` in `CommandMatcherTest`, without stealing "play …".
- `QaBases` — the three new prefs honoured only when DEBUG; an override bypasses the mirror lookup and also carries the click call.
- `ServerAuth.headersFor(host, serverHost)` — the token only for the server's host.
- Jellyfin music listing parse; server items carry `durationMs`.
- The user-agent string.
- `FixedEndpointsTest` as built — carries E3b's three `false` lines.
- Logo decode bounds and byte cap.

### (3) Dropped (neither on the device nor in a JVM test)

- E1 header-strip geometry and `[motion]` numbers; MUSIC6 / 7 / 17 re-runs — phase 10's rows; the change is one more header; H2 sees it.
- E6 the 15-minute sleep on a stream — the timer pauses the player whatever the item; `SleepTimerTest` exists.
- E7 the equaliser on a stream — the effect is bound to the audio session, not the item.
- E8 the gave-up pass (120 s) and its ± 5 s clocks; E9 throttled network; E11 stop / codec switch — same reconnect code as R3; the schedule is on the JVM.
- E10 captive portal on the AVD — the decision is on the JVM; P4 when one is at hand.
- E12 Data Saver — **ASK**. Addendum §7.12 says the exemption is not vendor-documented. After the cut it is checked only on the phone (P1). Q4 A's "plays on any network" rests on it. Lean: phone-only.
- E13 etiquette timing, offline, 500 and connect-error pages — JVM rate rule and parse; the error texts go unasserted.
- E13b the Pandora stub — a stub under another package is never targeted: `StreamingHandoff.kt:98-99` sets the service's package. **ASK**: Q5's Pandora entry has only the JVM plan test and P3 (addendum §8: the phone alone can prove it). Lean: accept.
- E13b stale tap; E15 audio focus — focus handling is untouched; P2 covers a call.
- E14 crossfade between two server tracks, wrong password, `docker stop` — **ASK** for the crossfade only. Q1 names crossfade for server music and no check remains. Lean: add it to P1's listening.
- E14 unauthorised / unreachable lines — phase 17's client, already gated there.
- E19 the coverage row; C-29's 0-packet guard — the overrides and the click base are JVM-tested. Residual risk: a debug build touching the live directory once.
- E2 offline first open — an empty-state text; the JVM carries the state.
- E18's `dumpsys shortcut` clause — nothing declares a shortcut.
- Edge: APK update mid-stream, reboot liveness, MUSIC slot re-pointed, a StreamTitle every second — unasserted. Phase 10's rules and `MusicFeed`'s throttle are unchanged.

### Phone rows (phone-only: he does it and reads it there; four)

- **P1 Radio for real** (was P3, P6, P7, P10). The CI build. Open radio; search jazz; play; lock the phone for 30 minutes on Wi-Fi, then on mobile data, once with Data Saver on. Report: still playing each time; the mobile-data line seen; from Settings > Diagnostics the `radio: directory fetched <n>` line and one `stream: connected http://…` line (an http station played on the release config); data used and battery from Android's own app-usage screens (H5).
- **P2 Buttons and calls** (P4, E15). Headset or Bluetooth pause / play, next / previous step favourites; a call pauses and nothing resumes by itself.
- **P3 Listen on** (P1, P2, P9). A title → Listen on Pandora, and each other installed app. Per app: landed on its search for the title, or just opened; the `[music] handoff:` line; a song playing there shows only on that app's pinned tile.
- **P4 Tess by voice** (P8), unlocked and over the lock screen. P5 folds in here as "when a portal is at hand", recorded.
- **P11 cannot be a phone row** (`adb shell cmd package`, L674-676). **ASK**: Q3 A is conditional on "no FM". Lean: one process-start line `[music] radio: fm feature=<hasSystemFeature("android.hardware.broadcastradio")>`, read on the Diagnostics page; otherwise the cited sources stand alone.

### NEEDS-HUMAN (five)

- H1 the live now-playing form plus the metered line (Y1, Y3).
- H2 the radio pivot and what is browsable offline (Y2, addendum §7.3).
- H3 the catalogue and Listen-on pages and the entry order (Y5, old H6).
- H4 the server music view (Y7).
- H5 recorded facts: the reconnect feel, Tess's wording, data / battery (Y4, Y6, old H8).
- Old H9 goes: a ruling restated, nothing to judge.

### Diagnostics lines still asserted

`radio: directory fetched <n>` (R1, P1); `stream: connected <url> codec=` (R2, R7); `stream: unsupported scheme=` (R2); `stream: lost, retrying` / `reconnected after` (R3); `search "<q>": station <name>` (R5); `handoff: <svc> "<title>" ->` (R6, P3); `server <host>: connected` (R7). Every other line: its builder is JVM-tested, and it is not asserted on the device.

## V2 — BLOCKING — L384-390 (task 7), L601-603: Tess never reaches the callback the doc extends

`ActionLayer.kt:585-586` resolves the query itself, against the library alone, before any session call:
`MusicSearch.resolve(query, …MusicStore.library.value) ?: return answer("I couldn't find $query in your music.")`.
"play jazz radio" therefore answers the miss and sends nothing. `:594` says `"Playing ${match.label}."`, and `:590-591` plays `match.queue` as `Track`s when no session is up. `CommandMatcher.kt:91-93` already maps "play <rest>" to `PlayMusic(rest)`, so no station phrase is needed.

Fix, task 7: "`MusicSearch.resolve(query, library, radio)` gains `Kind.STATION` and is the ONE resolver. Both `ActionLayer.playMusic` (`:585`) and the service's search branch call it with the favourites and the cached directory. The no-session branch (`:590`) starts a station through the new station play path. The label for a station is its name. `CommandMatcher` gains only 'listen to <x> on <app>'."

## V3 — BLOCKING — L178-179, L450, L568: "no Listen on entry with no stub installed" cannot pass

The shell's own Music activity declares `CATEGORY_APP_MUSIC` (`AndroidManifest.xml:164`), and the baseline pins Auxio (L418), a music app. Auxio's published manifest declares the category as far as I know — not checked on the device; verify at build. Discovery by that category lists both.

Fix: "Discovery excludes `app.tileshell`. A local player is a plain-open entry like any other." R6 asserts the QA Tunes entry before and after the install, never an empty list.

## V4 — SHOULD-FIX — L407-420 (task 11), L454-476: rewrite to the surviving fixtures

Replace task 11 with V1's fixture list. Name the ADD to phase 17's fixture (`jellyfin_fixture.sh up` gains a Music `VirtualFolders` call and two generated MP3s) and the DEBUG-only QA Tunes table row. Delete the `/edge/` and `/huge/` text, the three-stub text and L391-392's utterance ids.

## V5 — SHOULD-FIX — L67-68, L387-388: "play radio" has two undefined cases

Zero favourites, and a library song titled "Radio". Add: "With no favourite, 'play radio' is an ordinary search for 'radio': stations by name, then the library. With a favourite it always means the favourite." Both are cases in the JVM list.

## V6 — SHOULD-FIX — L142-144, L353, L498: R2 needs a decided HLS answer

Addendum §7.6: 9.8 % of the top 2,000, a 224 KB module. Replace the "build-start count" with "`media3-exoplayer-hls` is added (ADD to `app/build.gradle.kts:119-122`); an HLS station shows no StreamTitle", and delete the `unsupported hls` conditional from E3 / E19.

## V7 — SHOULD-FIX — L163-172, L485-493: R1's offline pass depends on an unstated cache

Addendum §7.3: an unbounded call returns 1,000 rows; the full directory is 60–85 MB. Until the doc states what is fetched (my assumption: top-clicked N with an explicit `limit`, plus tags and countries, search online when connected), "the same rows offline" is undefined. R1 and `RadioDirectory.request` are written against that sentence; H2 accepts it.

## V8 — NOTE — L139, L161, L349, L184-190, L357

Media3 is 1.9.0 (`gradle/libs.versions.toml:12`). `WAKE_LOCK` is already at `AndroidManifest.xml:78`, so task 2's manifest ADD is only `setWakeMode`. L130-131's `MusicService.kt:155-187 / :166 / :169` are now `:180 / :191 / :194`.

## Round-1 / round-2 ids checked and correctly applied

T20-1, T20-3, T20-4, T20-6 (fits `QaBases`, `Catalogue.kt:24-26`), T20-9 (but see V5), T20-10, T20-14, C-6, C-20, C-25, C-26.

Superseded by this cut, not mis-applied: T20-5 (playlist half), T20-7 (utterance ids), T20-8, T20-11, T20-12, T20-13 (the query premise, addendum §7.11), T20-15, T20-16, C-29, C-30, C-31.

BLOCKING: 3 · SHOULD-FIX: 4 · NOTE: 1
