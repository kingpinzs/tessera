# Phase 20 — round 3 triage (2026-10-07 09:01; the last round, cap 3)

Reviews: `2026-10-07-phase20-r3-design.md` (opus; BLOCKING 7 · SHOULD-FIX 8 · NOTE 2) and
`2026-10-07-phase20-r3-testability.md` (opus; BLOCKING 3 · SHOULD-FIX 4 · NOTE 1). Research:
`../r11/music-radio-addendum-2026-10-07.md` (§7, 15 contradictions). Never codex, never Fable.

**Outcome: 25 findings + the addendum's 15 items → all accepted as agent fixes (0 rejected), ONE question for Jeremy
(Q-20-1), four ASK flags resolved by the lead as test placement (his 2026-10-07 rule; told to him, not asked).**

## Agent fixes (applied to the doc by an Opus doc writer; the lead reads the diff)

| id | what | where it lands |
|---|---|---|
| D1 | Station / server items are built only in two builders with `station:<uuid>` / `server:<id>` media ids; taps go through the shell's own controller; a stranger's search stays library-only (`RadioSearchRule.stationsFor`); `UriAccessWiringScanTest`'s pinned clauses are rewritten in the same commit and that diff is in the adversarial review; no resumption after process death | Decisions (new, trust); tasks 3, 7 |
| D2 | A station item never sets `artworkUri`; the shell fetches the logo (http/https, ≤ 512 KiB, decoded ≤ 512 px) as `artworkData`, only for favourites and the playing station; browse rows draw the placeholder | Decisions (T20-5 amended); task 3; H2 |
| D3 | The music hand-off's ADDs to phase 17's part are named: `MusicServicesTable` (Pandora `pandorav8://search/%s/all` → `https://www.pandora.com/search/%s/all`; YT Music, Amazon, Apple, Deezer, SoundCloud web-search links; Tidal none), `HandoffPlan.PlayFromSearch` (QUERY + EXTRA_MEDIA_FOCUS), `StreamingHandoff.openPlans(…, tag="music")`, discovery = installed table rows ∪ `CATEGORY_APP_MUSIC` launchers minus the shell | Decisions; task 8 |
| V3 | Discovery excludes `app.tileshell`; a local player (Auxio on the baseline) is a plain-open entry; no row asserts an empty list | with D3 |
| D4 + §7.11 | Server tracks play `/Audio/<id>/stream?static=true` with NO token; listing by header; the 401 fallback is phase 17's resolver with `STREAM_PATH` widened; ADDs `ServerRules.musicPath` / `parseTracks` / `audioStreamUrl`, `MediaServer.music()`, a `tag` argument | Decisions (C-32 entry amended); task 8 |
| D5 + §7.3–7.5 | The directory: what is fetched, cached, capped, searched; mirrors; the click call; `VideoHttp.get` — **the scope itself is Q-20-1; written with the lean (A) and marked pending** | Decisions ("the station directory" replaced); task 5 |
| D6 + V2 + V5 | ONE resolver `MusicSearch.resolve(query, library, stations)` used by `ActionLayer.playMusic` (ADD to phase 03's part) and the session; precedence rules (1)–(3) so "play <song>" keeps the library first and the directory is contains-matched only for a query with "radio"; "play radio" with no favourite is an ordinary search; no station phrase in `CommandMatcher` | Decisions (T20-2 / T20-9 amended); task 7 |
| D7 | New `Request.ListenOn(query, app)`; not allowed while locked (unlock card — PQ3, as Q5's own option text said); app resolved by code against discovery labels | Decisions; task 7 |
| D8 + §7.10 | One reconnect clock from the first load error; re-prepare at 2/4/8/16/30 s; give up at T0 + 60 s; the service's own partial wake lock for the window; the two texts ride a session extra | Decisions ("reconnect" replaced); task 3 |
| D9 + §7.2 | Station item sets `albumTitle` and `artist` = station name, never `title`; the player wrapper supplies the station name for a null title; every directory string and StreamTitle passes `RadioText.shown` before the tile, notification, a line or Tess's reply | Decisions ("live metadata" replaced; trust); task 3 |
| D10 + V6 + §7.6 | `media3-exoplayer-hls` IS added; `hls=1` rows get the m3u8 MIME type; live = the item's mark alone (`MusicLive.isLive(mediaId)`); `CrossfadeFader.considerPreparing` returns for a live item; `unsupported hls` struck | Decisions; tasks 1, 3, 4 |
| D11 + §7.7 | No playlist is fetched or parsed: play `url_resolved` (else `url`); a leftover `.pls` / `.m3u` / `.asx` → `unsupported playlist` | Decisions (T20-5 amended); task 3 |
| D12 + §7.9 | Cross-protocol redirects stay refused (Media3's default, as the video player): `stream: redirect refused`; `StationUrl.accept` also refuses empty / loopback / link-local / private IP-literal hosts | Decisions (T20-5 amended; trust) |
| D13 | Cover art follows ≤ 3 redirects by hand, only https to `archive.org` / `*.archive.org` (`CoverArt.mayFollow`) | Decisions ("the music catalogue"); task 8 |
| D14 | Only `NET_CAPABILITY_CAPTIVE_PORTAL` shows the sign-in line; not-validated plays into the reconnect path; server tracks exempt (`StreamGate.decide`) | Decisions ("captive portal" replaced) |
| D15 | A tapped favourite's queue is the favourites; a non-favourite's is that station alone; a station play always replaces the queue; no mixed queues | Decisions; task 3 |
| D16 + V8 + §7.1, 7.8, 7.12, 7.13, 7.15 | Stale text: Media3 1.9.0; `WAKE_LOCK` held (task 2 = `setWakeMode` only); line cites; the network config is built (A) — the B / C text struck; `QaBases` constants; Data Saver wording; `SearchManager.QUERY` | throughout |
| D17 | Catalogue: `recording` search, `limit=25`, submit-only, one request in flight, art `releases[0].id` at `front-250`, 503 → no automatic retry (`RateSpacing.waitMs`) | Decisions; task 8 |
| §7.14 | Pandora's form recorded now; "search not passed" is the last fallback | with D3 |
| V1 | **Acceptance re-cut under the 2026-10-07 rule:** seven device rows (A1–A7 in the doc; the review's R1–R7), three static checks, the JVM test list (the review's list ∪ Reviewer 1's pure rules), the dropped list; four phone rows; five NEEDS-HUMAN rows; the seven diagnostics patterns still asserted on the device | Acceptance, Edge cases, QA evidence — replaced whole |
| V4 | Build task 11 rewritten to the surviving fixtures: `radio_fixture.py` (:8080, the real API shape incl. `/json/url/<uuid>`), `catalogue_server.py` (:8081), phase 17's Jellyfin fixture + a Music folder ADD, `testapps/qa-tunes` with a DEBUG-only table row | task 11 |
| V7 | A1's offline pass is written against the directory sentence of D5 / Q-20-1 | Acceptance |

## The four ASK flags — lead's calls (test placement; his 2026-10-07 rule says few rows; he may overrule)
- **Data Saver (was E12):** phone only — P1 includes one listen with Data Saver on. The AOSP rule is cited, not asserted on the AVD.
- **Pandora (was E13b's stub):** the JVM plan test carries the form; P3 on the phone proves the installed app (the only place it can be proven).
- **Crossfade between two server tracks (Q1 names crossfade):** P1's listening on the phone plus the existing crossfade unit tests; no device row.
- **No FM receiver (was P11, adb):** one process-start line `[music] radio: fm feature=<bool>` (`hasSystemFeature("android.hardware.broadcastradio")`), read on the Diagnostics page in P1.

## Told to Jeremy, not asked (agent calls a person will notice)
- "play <name>" keeps finding his own music first; a station is found by name only when it is a favourite or he says "radio".
- "listen to <x> on <app>" asks for an unlock when the phone is locked (it opens another app — phase 03's rule).
- HLS stations play (one more 224 KB library); a station that hops between https and http by redirect does not.
- Station logos show for favourites and the playing station only; other rows show the placeholder.
- The shell tells radio-browser when a station is started (their click counter; they ask every app to).

## Question for Jeremy
**Q-20-1 — how much of the station directory lives on the phone.** His Q2 / Q4 rulings say the directory "is cached and
browsable offline". The research measured it: about 53,000 working stations; the top 2,000 are 0.8 MB slim; the whole
directory about 20 MB slim, 50+ requests, re-fetched daily from one volunteer-run server.
- A (lean). The 2,000 most popular + genres and countries on the phone; when online, search also asks the full directory.
- B. The whole directory on the phone (~20 MB, refreshed in pages), so every station is searchable offline.
- C. The 2,000 most popular only; no online search.
- D. Other / let me clarify.

## Round-1 / round-2 items
Checked and correct (both reviewers): T20-1, T20-3, T20-4, T20-6, T20-7, T20-9, T20-10, T20-11, T20-12, T20-14, T20-15,
T20-16, T20-17, C-6, C-16's gate, C-20, C-23, C-25, C-26. Amended by this round: T20-2 (D1, D6), T20-5 (D2, D11, D12),
T20-8 (D3), T20-10's name source (D5), T20-13 (D4). Superseded by the re-cut: C-29, C-30, C-31 as used here.
