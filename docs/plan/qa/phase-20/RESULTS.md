# Phase 20 — the ONE round of testing: results

Run 2026-10-07 17:06–17:39 MDT on `emulator-5554` (AVD tileshell_fhd, 1080×2340 @ 450 dpi, API 36; the emulator was
already up, started with `QEMU_AUDIO_DRV=none`; it was never restarted and never crashed). Tess was typed; the host's
audio and microphone were not touched; the device's media stream was at volume 0.

**Verdict: A1–A7 all pass (28 of 28 lettered conditions); the three static checks pass; no product defect found.**
Four rows needed a re-run for a DRIVER fault (below); A4, A5 and A7 passed on their only run. No row that passed was
run again. No row reached the live radio-browser, MusicBrainz or Cover Art servers (every row proved the three `qa_*`
prefs from the file before Music opened; A1 ran its clear → provision → prefs step with airplane mode on).

## The build under test

| | |
|---|---|
| debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 359,354,356 B, md5 `dfa1169a0725c858`, sha256 `b16ed123fa2a7620…` |
| built with | `./gradlew :app:assembleDebug :testapps:qa-tunes:assembleDebug -Ptmdb.readToken=qa-dummy-token` — rc 0 (`static/build-debug.txt`, `.rc`) |
| on the device | `apk_matches` = `yes (dfa1169a0725c858)` in every row's stamp and again at the end (`static/end-state.txt`) |
| source | the pushed build `62eea325`. HEAD was `ae608d32` while the rows ran: one later commit that changes only `docs/plan/INDEX.md` and `.claude-build-state.md` (`git diff --stat 62eea325 HEAD`), nothing under `app/` |

## Rows

Evidence paths are under `docs/plan/qa/phase-20/`. Where a row was re-run, the passing run's folder is cited and the first
run's folder is kept beside it.

| row | letter | result | the real line | evidence |
|---|---|---|---|---|
| A1 | a | PASS | headers `[albums artists songs playlists]` + `[artists songs playlists radio]` = 5, radio last, its bounds `849 157 1068 301` | A1-rerun1/open.xml, radio.xml, 02-radio-pivot.png |
| A1 | b | PASS | `[music] radio: directory fetched 4 stations`; fixture: `1 GET /json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=0`; search rows `QA Jazz One / QA Jazz Two` only | A1-rerun1/slice-open.txt, stations-requests.txt, search.xml, 03-search-jazz.png |
| A1 | c | PASS | menu entries = `music_menu_fav_add` alone ("Add to favourites"); no text on screen mentions pin | A1-rerun1/menu.xml, 04-hold-menu.png |
| A1 | d | PASS | after the reopen: `radio_group:favourites radio_fav:1111… radio_group:streaming radio_group:stations radio_row:1111… 2222… 3333… 4444…` | A1-rerun1/reopen.xml, 06-reopen-radio.png |
| A1 | e | PASS | 3 requests, 0 without `User-Agent: Tessera/` (`Tessera/0.1.0 (personal launcher; Music)`) | A1-rerun1/fixture-logs/radio.log, radio-requests.txt |
| A2 | a | PASS | session at 30 s `PLAYING \| QA Song 2, QA Jazz One, QA Jazz One` ("QA Song 1" from t+2 s to the switch); `[music] stream: connected http://10.0.2.2:8092/stream/jazz-one codec=mp3` | A2-rerun1/title-timeline.txt, session-30s.txt, slice-play.txt |
| A2 | b | PASS | `nowplaying_live_caption` = `LIVE`; scrubber / total `no no`; sleep list `music_menu_sleep:15 :30 :45 :60`, no `:eot` | A2-rerun1/np30.xml, sleep.xml, 01-nowplaying-30s.png, 03-sleep-list.png |
| A2 | c | PASS | tile texts `QA Song 2 \| QA Jazz One`; after the control `PAUSED \| QA Song 2, …`; Photos / Camera bounds `721 439 1064 782 / 721 1935 1064 2182` before, while playing and after | A2-rerun1/start-before.xml, start-playing.xml, start-paused.xml, 04-start-playing.png |
| A2 | d | PASS | `1 GET /json/url/11111111-1111-4111-8111-111111111111` — one, and still one at the end of the row | A2-rerun1/click-calls.txt, click-calls-end.txt |
| A2 | e | PASS | row reads `can't play this station`; session `PAUSED \| QA Song 2, …` before = after; `[music] stream: unsupported scheme=file` | A2-rerun1/file1.xml, slice-file.txt, 06-qa-file-refused.png |
| A3 | a | PASS | override read back `AndroidWifi;true`; t+15 s `QA Song 1 \| Streaming over mobile data \| PLAYING` | A3-rerun1/metered-15.xml, 01-metered.png |
| A3 | b | PASS | airplane t+6 / 12 / 18 s: `Reconnecting… \| \| BUFFERING`, `BUFFERING`, `ERROR`; `[music] stream: lost, retrying` | A3-rerun1/airplane-12.xml, slice-airplane.txt, 02-reconnecting.png |
| A3 | c | PASS | back t+5 s `QA Song 1 \| Streaming over mobile data \| PLAYING`, no tap; `[music] stream: reconnected after 18058 ms` | A3-rerun1/back-5.xml, slice-airplane.txt, 03-playing-again.png |
| A4 | a | PASS | station off+60 s `tracks=1 937 Asleep PLAYING \| QA Song 2, …`; local off+60 s `tracks=1 937 Asleep PLAYING \| 4 Minute Warning, Radiohead, The King of Limbs` | A4/A4.txt, flinger-station-60s.txt, flinger-local-60s.txt |
| A4 | b | PASS | local track `4 Minute Warning / 1:12 / 1:30`; scrubber and total `yes yes`, no live caption | A4/local-after.xml, 03-local-after-wake.png |
| A5 | a | PASS | reply `Playing QA Jazz One.`; `PLAYING \| QA Song 1, QA Jazz One, …`; `[music] search "jazz radio": station QA Jazz One` | A5/slice-01-jazz.txt, session-01-jazz.txt |
| A5 | b | PASS | "play radio" → `Playing QA News One.` / `PLAYING \| QA News One, …`; `[music] search "radio": station QA News One` | A5/slice-03-play-radio.txt, 02-favourites.png |
| A5 | c | PASS | `I couldn't find zzqx radio in your music.`; session `PLAYING \| QA News One, …` before = after | A5/slice-04-miss.txt |
| A5 | d | PASS | `isKeyguardShowing=true / PLAYING \| QA Song 1, QA Jazz One, …` (paused before the ask); reply `Playing QA Jazz One.` | A5/K-typed.xml, slice-keyguard.txt, 06-keyguard-playing.png |
| A5 | e | PASS | `J5: 8 passed, 0 failed`, rc 0 | A5/j5.out, J5.txt |
| A6 | a | PASS | three rows `QA Song A / QA Song B / QA Song C`; art nodes filled `(200 60 40) (40 90 200) (200 60 40)` = the fixture's covers; 4 catalogue requests, 0 without `Tessera/` | A6-rerun1/results.xml, 01-catalogue-results.png, catalogue-requests.txt |
| A6 | b | PASS | before: `handoff_service:org.oxycblt.auxio …gramophone` (no qa-tunes); after: `handoff_service:qa-tunes` once, "Listen on QA Tunes" | A6-rerun1/title-before.xml, title-after.xml |
| A6 | c | PASS | top `app.tileshell.testclient.qatunes/.MainActivity`; stub: `action=android.intent.action.VIEW data=https://qa-tunes.test/search?q=QA+Song+A+QA+Artist`; `[music] handoff: qa-tunes "QA Song A" -> https://qa-tunes.test/search?q=QA+Song+A+QA+Artist`; session `NONE \|` before = after | A6-rerun1/stub-lines-tap.txt, slice-handoff.txt, 04-stub-resumed.png |
| A6 | d | PASS | reply `Opening QA Tunes.`; top the same stub; stub: `data=https://qa-tunes.test/search?q=qa+artist` | A6-rerun1/stub-lines-typed.txt, slice-typed.txt |
| A7 | a | PASS | `[music] server 10.0.2.2:8096: connected`; `server_album:07e8… (QA Server Album)`, `server_artist:QA Server Artist`, two `server_song:` | A7/slice-signin.txt, srv2.xml, artists.xml, songs.xml |
| A7 | b | PASS | `PLAYING \| QA Track One, QA Server Artist, QA Server Album`; total `0:30` vs 30.000 s; `[music] stream: connected http://10.0.2.2:8096/Audio/07e8f1d6bf6aec8f14dab70db2190eb1/stream codec=mp3` (no `?`) | A7/np.xml, slice-song.txt, 05-server-song-playing.png |
| A7 | c | PASS | `leak_scan.sh` rc 0: every secret `clean` in qa/phase-20 and in `adb logcat -d` (12985 lines); the token read was 32 characters | A7/leak-scan.out, leak-scan.rc |
| A7 | d | PASS | 2 radio requests after the sign-in, 0 with `Authorization`, 0 with an ApiKey; header names seen: `Accept-Encoding, Connection, Host, Icy-MetaData, User-Agent` | A7/fixture-logs/radio.log, radio-requests.txt |

Each run's full output is `<folder>/run.txt`, its exit code `<folder>/rc.txt`, its stamped log `<folder>/<row>.txt`.

## Static checks (once each; `static/`)

| check | result | number |
|---|---|---|
| debug APK ≤ 629,145,600 B | PASS | `stat -c%s` = 359,354,356 (`static/size.txt`) |
| exported components = `qa/phase-03/exported-allowlist.txt` exactly | PASS | `qa/phase-03/scripts/exported.py` (phase 18 E10's derivation) rc 0: "27 exported in app.tileshell, 27 on the allow-list"; 0 "EXPORTED BUT NOT ALLOWED", 0 "ON THE LIST BUT NOT EXPORTED" (`static/exported.txt`) |
| release network security config holds no `10.0.2.2` | PASS | `./gradlew :app:assembleRelease -Ptmdb.readToken=` rc 0 (344,577,580 B); `aapt2 dump xmltree --file res/8G.xml` (the release build's name for `xml/network_security_config`, from `aapt2 dump resources`): 0 occurrences. Control: the debug APK's same file holds 1 (`static/release-nsc-xmltree.txt`, `debug-nsc-xmltree.txt`) |

## Driver faults fixed (each: only that row was re-run; the first run's folder is kept)

| row | the fault | the fix | re-run |
|---|---|---|---|
| A1 | the driver typed "jazz" and never pressed the keyboard's Search key; the search runs on that key (`MusicRadioPages.kt` RadioSearch, `imeAction = Search`), so no rows came, nothing was held, no favourite was made (b, c, d failed) | `KEYCODE_ENTER` after the text | A1-rerun1: 5 / 0 |
| A1 | `p20_end` counted its letters inside `{ … } \| tee` (a subshell), so the first run's exit code was 0 with three letters failed — **`A1/rc.txt` says 0 and is wrong**; its `run.txt` says `2 passed, 3 failed` | counted in the driver's own shell | (same re-run) |
| A2 | the session-state reader matched the `P` of `state=PlaybackState` as well as `PLAYING`, so two comparisons failed against `P⏎PLAYING` / `P⏎PAUSED` (a, c) | read the word followed by `(` | A2-rerun1: 5 / 0 |
| A3 | `cmd netpolicy list wifi-networks` prints the AVD's SSID on two lines; the driver compared both with one value (set-up, a, restore failed; the device state was right each time) | distinct lines | A3-rerun1: 3 / 0 |
| A6 | "artwork drawn" asked for more than 8 distinct colours in each art node; the fixture's covers are flat colours, and they WERE drawn (a failed) | the node's colour must be one of the fixture's two | A6-rerun1: 4 / 0 |

## How three conditions were read (driver readings, for the lead)

- **A1 a.** uiautomator lists only the headers on screen (the strip is clipped and scrolls no further than needed), so no
  single dump holds five. The five are the union of the first pivot's dump and the radio pivot's; radio's bounds are read
  on the radio pivot.
- **A2 c.** The doc writes `tile_control:<id>:PAUSE`; the built tag is `tile_control:slot:MUSIC:PLAY_PAUSE`, described
  "Pause" while playing (`start/TileView.kt:589`, phase 10's tag form). The driver tapped that one. A doc wording, not a
  defect.
- **A3 b / c** are read from the slice that starts when airplane mode goes on, so they are about that loss. In both A3
  runs step 1 (the metered override) did not interrupt the station at all on this boot: no `stream:` line in its slice.

## Observations (no row fails on them)

- **The stream's own request carries Android's default User-Agent**, not `Tessera/`: `GET /stream/jazz-one | UA: Dalvik/2.1.0
  (Linux; U; Android 16; …)` (A2-rerun1/radio-requests.txt; the directory and click calls carry `Tessera/0.1.0`). A1 e
  passes because A1 plays nothing; if "every fixture request" is meant to cover the stream too, this is the gap
  (likely cause, not traced: the Music player's Media3 http source is given no user agent).
- **The session reads ERROR during a reconnect that then succeeds**: A3 at airplane t+18 s `Reconnecting… | | ERROR`, then
  PLAYING 5 s after the network returned (`reconnected after 18058 ms`). The build notes describe ERROR only after the
  60 s give-up.
- A7 leaves a saved home server in the shell (it points at the removed fixture); A1 and A5 leave QA Jazz One and
  QA News One as radio favourites.
- Every row found the default keyboard at `com.android.inputmethod.latin/.LatinIME` after the baseline restore's
  force-stop and set the shell's keyboard for its run; it is LatinIME again at the end.

## For a human's eye in the screenshots (H1–H4)

`A2-rerun1/01-nowplaying-30s.png` (the live form), `A3-rerun1/01-metered.png` and `02-reconnecting.png` (the metered line;
"Reconnecting…" with the play mark showing), `A1-rerun1/02-radio-pivot.png` and `06-reopen-radio.png` (the radio pivot,
the favourite first), `A6-rerun1/01-catalogue-results.png` and `03-title-after-install.png` (catalogue, Listen on),
`A7/02-server-albums.png` – `05-server-song-playing.png` (the server view), `A5/06-keyguard-playing.png` (Tess over the
keyguard).

## End state (`static/end-state.txt`, 17:39 MDT)

Fixtures down (nothing listens on 8092 / 8093 / 8096); no Jellyfin container; QA Tunes uninstalled; netpolicy
`AndroidWifi;none`, Wi-Fi NOT_METERED; airplane mode off; no PIN (`locksettings get-disabled` = true), no keyguard; the
phone awake on Start; the tested debug build installed (`apk_matches` yes); the three `qa_*` prefs in place, pointing at
the stopped fixtures; no shell crash in logcat.

## Re-run after D1 (2026-10-08)

Run 2026-10-08 10:51–11:02 MDT on `emulator-5554` (AVD tileshell_fhd; already up, not restarted, no crash). After the
owner's ruling on R20-1 the Music player's http stack was replaced (`music/MusicHttp.kt`, Media3's OkHttp data source);
the three rows that exercise it — A2, A3, A7 — were run ONCE each on the pushed build. No other row was run. The earlier
runs' folders are untouched. Host audio and microphone not touched; the device's media stream at volume 0.

**Verdict: A2, A3 and A7 pass (12 of 12 lettered conditions); no product failure.** A7 needed one re-run for a driver
fault (below). No row reached a live server: each proved the three `qa_*` prefs from the file before Music opened.

### The build under test

| | |
|---|---|
| debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 358,600,865 B, md5 `7f2e21c8fbfc21a2159972026c6c194b`, sha256 `9042a8d20218285c…` |
| built with | `./gradlew :app:assembleDebug -Ptmdb.readToken=qa-dummy-token` — rc 0, every task up to date against HEAD's tree (`static-d1/build-debug.txt`, `.rc`) |
| source | HEAD = `origin/phase-20` = `5c22c612`, nothing changed under `app/` in the worktree |
| on the device | `adb install -r` rc 0 (`static-d1/install.txt`); `apk_matches` = `yes (7f2e21c8fbfc21a2)` in every row's stamp and at the end (`static-d1/apk-stamp.txt`, `end-state.txt`) |

### Rows

| row | letter | result | the real line | evidence |
|---|---|---|---|---|
| A2 | a | PASS | session at 30 s `PLAYING \| QA Song 2, QA Jazz One, QA Jazz One` ("QA Song 1" from t+1 s to t+24 s, "QA Song 2" from t+26 s); `[music] stream: connected http://10.0.2.2:8092/stream/jazz-one codec=mp3` | A2-d1/title-timeline.txt, session-30s.txt, slice-play.txt |
| A2 | b | PASS | `nowplaying_live_caption` = `LIVE`; scrubber / total `no no`; sleep list `music_menu_sleep:15 :30 :45 :60`, no `:eot` | A2-d1/np30.xml, sleep.xml, 01-nowplaying-30s.png |
| A2 | c | PASS | tile texts `QA Song 2 \| QA Jazz One`; after the control `PAUSED \| QA Song 2, QA Jazz One, QA Jazz One`; Photos / Camera bounds unchanged | A2-d1/start-playing.xml, start-paused.xml, 04-start-playing.png |
| A2 | d | PASS | `1 GET /json/url/11111111-1111-4111-8111-111111111111` — one, and still one at the end of the row | A2-d1/click-calls.txt, click-calls-end.txt |
| A2 | e | PASS | row reads `can't play this station`; session `PAUSED \| QA Song 2, …` before = after; `[music] stream: unsupported scheme=file` | A2-d1/file1.xml, slice-file.txt, 06-qa-file-refused.png |
| A3 | a | PASS | override read back `AndroidWifi;true`; t+15 s `QA Song 1 \| Streaming over mobile data \| PLAYING` | A3-d1/metered-15.xml, 01-metered.png |
| A3 | b | PASS | airplane t+6 / 12 / 18 s: `Reconnecting… \| \| BUFFERING`, `BUFFERING`, `ERROR`; `[music] stream: lost, retrying` | A3-d1/airplane-12.xml, slice-airplane.txt, 02-reconnecting.png |
| A3 | c | PASS | back t+5 s `QA Song 1 \| Streaming over mobile data \| PLAYING`, no tap; `[music] stream: reconnected after 18067 ms` | A3-d1/back-5.xml, slice-airplane.txt, 03-playing-again.png |
| A7 | a | PASS | `[music] server 10.0.2.2:8096: connected`; `server_album:07e8… (QA Server Album)`, `server_artist:QA Server Artist`, two `server_song:` | A7-d1-rerun1/slice-signin.txt, srv2.xml, artists.xml, songs.xml |
| A7 | b | PASS | `PLAYING \| QA Track One, QA Server Artist, QA Server Album`; elapsed / total `0:06 / 0:30` vs 30.000 s; scrubber present, no live caption; `[music] stream: connected http://10.0.2.2:8096/Audio/07e8f1d6bf6aec8f14dab70db2190eb1/stream codec=mp3` (no `?`) | A7-d1-rerun1/np.xml, slice-song.txt, 05-server-song-playing.png |
| A7 | c | PASS | `leak_scan.sh` rc 0: every secret `clean` in qa/phase-20 and in `adb logcat -d` (135016 lines); the token read was 32 characters | A7-d1-rerun1/leak-scan.out, leak-scan.rc |
| A7 | d | PASS | `PLAYING \| QA Song 1, QA Jazz One, QA Jazz One` after the server's song; 2 radio requests after the sign-in, 0 with `Authorization`, 0 with an ApiKey; header names seen: `Accept-Encoding, Connection, Host, Icy-MetaData, User-Agent` | A7-d1-rerun1/fixture-logs/radio.log, radio-requests.txt, session-station.txt |

Each run's full output is `<folder>/run.txt`, its exit code `<folder>/rc.txt` (A2-d1 0, A3-d1 0, A7-d1 1, A7-d1-rerun1 0).
A3's restore checks passed inside the row (netpolicy `AndroidWifi;none`, Wi-Fi `NOT_METERED`, airplane mode off).

### Driver fault fixed (only that row was re-run; the first run's folder is kept)

| row | the fault | the fix | re-run |
|---|---|---|---|
| A7 | the shell still held the server saved by the 10-07 A7 run, so the form opened as "Sign in again" with the address and user name filled in; the driver typed after them (`http://10.0.2.2:809610.0.2.2:8096`, `qaqa`) and the form answered "That isn't a server address". No sign-in was attempted, so a, b and c failed on the driver's input (d passed). A7-d1/srv0.xml, srv1.xml, srv-step.xml | `scripts/a7.sh`: a field that already reads what the row would type is left alone, any other content is deleted first; the form's opening state is recorded and the address / user name are asserted once each before Connect | A7-d1-rerun1: 4 / 0 |

### Observations (not pass conditions)

- **The stream request on the new stack** (A2-d1/fixture-logs/radio.log.jsonl, request 2 `GET /stream/jazz-one`):
  `User-Agent: Dalvik/2.1.0 (Linux; U; Android 16; Android SDK built for x86_64 Build/BE2A.250530.026.D1)` and
  `Icy-MetaData: 1` IS on it. Full header list in order: `Icy-MetaData, User-Agent, Accept-Encoding: identity, Host,
  Connection: Keep-Alive`. The agent is the platform's, set on purpose (`MusicService.kt:135`
  `.setUserAgent(MusicHttp.platformAgent())`, `MusicHttp.kt:202`); the click call still carries `Tessera/0.1.0
  (personal launcher; Music)`. The header ORDER differs from the 10-07 build's stream request (there `Accept-Encoding`
  came before `User-Agent`), which is consistent with the request now coming from a different http client.
- A3 again read the session as `ERROR` at airplane t+18 s and `PLAYING` 5 s after the network returned — the same as on
  10-07, not new with D1. Step 1 (the metered override) again did not interrupt the station (no `stream:` line).
- A7 b's "normal scrubber" is read from the page (scrubber and total present, total = the file's length); the Jellyfin
  server's own request log was not read, so range requests are shown by the result, not by a logged `Range` header.

### End state (`static-d1/end-state.txt`, 11:01 MDT)

Fixtures down (nothing listens on 8092 / 8093 / 8096); no Jellyfin container; netpolicy `AndroidWifi;none`, Wi-Fi
NOT_METERED; airplane mode off; no PIN (`locksettings get-disabled` = true), no keyguard; the phone awake on Start
(`app.tileshell/.StartActivity`); the tested build installed (`apk_matches` yes); the three `qa_*` prefs in place,
pointing at the stopped fixtures; default keyboard LatinIME; no shell crash in logcat. A home server stays saved in the
shell (it points at the removed fixture). Nothing committed, nothing pushed; the only tracked file changed besides this
one is `scripts/a7.sh`.
