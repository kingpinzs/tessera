---
phase: 20
slug: music-streaming-radio
status: DRAFT   # 2026-09-23; interview DONE 2026-09-23 (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A + Pandora); review triage round 2 applied 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md); Q-D: A (plain-http streams, with phase 17); ADDS to phase 10's FINAL part, never rebuilds it; round 3 applied 2026-10-07 (review/2026-10-07-phase20-r3-triage.md; r11/music-radio-addendum-2026-10-07.md); Q-20-1 answered 2026-10-07 (offline browsing is not a goal)
depends-on: [03, 10, 12, 15, 17, 18]   # C-23 / T20-7: 15 for its build task 0 (shell-session routing by tag), which "must be in first"; 12 for C-4's `pm clear` → `provision.sh` form and C-15; 17 for StreamingHandoff, the network security config (C-16) and the Jellyfin fixture; 11 is not needed (T20-3: no shortcut is declared); 18 added 2026-10-07 (r3 D1): the station / server item rule is an ADD to phase 18's `MusicItemRule` (ledger L18-1) and rewrites clauses of its `UriAccessWiringScanTest`, and the harness reuses `p18.sh`'s `baseline_start` / `absent_in` and `qa/phase-18/baseline_layout.json` — NOT for phase 18's `QaBases` constants or `MusicPlayExtra`, which this phase does not touch
---

# Phase 20 — Music streaming and Radio

## Goal
Phase 10's Music app (the built, FINAL Groove-style player) gains the two things its Q1 note said A11 had taken off the
table: a **streaming side** and **Radio**. When this phase is done, the Music app can reach music that is not on the phone in
the forms the interview ruled (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A): internet radio stations from radio-browser.info in a fifth
"radio" pivot, played in the shell's own `MusicService`; a MusicBrainz catalogue whose titles hand off to the music apps the
user already has, each opened on its own search for the title (the hand-off phase 17 builds for Movies & TV, ~~reused rather
than rebuilt~~ SUPERSEDED 2026-10-07 by r3 D3: reused with the named ADDs — `MusicServicesTable`, `HandoffPlan.PlayFromSearch`,
`StreamingHandoff.openPlans` and the music discovery, Decisions "the music hand-off"); and
the user's Jellyfin server's music library played in `MusicService` — and the Music tile, the notification
transport, the sleep timer and equaliser, and Tess follow whatever sounds through the shell's player. Everything that phase 10
built keeps working exactly as verified (MUSIC6–10, MUSIC17); every change to its part is a recorded ADD in the INDEX Change
Log (Hard Rule 16). Offline stays preferred (P6, A11 as amended): what can be cached is cached and browsable with no network,
and the local library never needs one.

## Scope
**In:**
- Radio (Q2 A, Q3 A): a radio-browser.info station directory (browse, search, genre / country, favourites) in a fifth Music
  pivot, the stations played as live items through `MusicService`, with the live-stream forms of the now-playing screen, the
  tile face and the `•••` menu that a stream with no end needs; the whole directory cached on the phone (Q-20-1; offline browsing is not a goal); reconnect,
  metered-data and captive-portal behaviour; station ~~and playlist~~ URLs accepted only as `http` / `https` (T20-5;
  SUPERSEDED 2026-10-07 by r3 D11: no playlist is fetched or parsed — the directory's `url_resolved` is played, and a
  leftover `.pls` / `.m3u` / `.asx` is "can't play this station"); HLS stations play (r3 D10).
- Streaming (Q1 C): the music analogue of phase 17's hand-off — a MusicBrainz catalogue lookup (Cover Art Archive artwork) and
  "Listen on <service>" opening each installed service's own search for the title (Q1 Decision), the user signed in inside
  that app; Pandora with its recorded search form, any other installed music app a plain open (Q5 follow-up; r3 D3,
  2026-10-07: six more services carry a recorded web-search link in `MusicServicesTable`, the plain open stays the last
  fallback for every app); and the user's
  Jellyfin server's music (phase 17's connection) browsed as albums / artists / songs and played in `MusicService`.
- Tess commands for the above (Q5 A): station, genre and "play radio" phrases resolved by EXTENDING J5's `MusicSearch` (T20-2;
  r3 D6, 2026-10-07: the one resolver `MusicSearch.resolve(query, library, stations)`, also an ADD to phase 03's
  `ActionLayer.playMusic`), and "listen to <x> on <app>" hand-offs (an ADD to phase 03's matcher; r3 D7: a new
  `Request.ListenOn`, which asks for an unlock on a locked phone).
- The radio pivot inside Music (Q3 A) with no new App Shortcut (T20-3: Music keeps phase 11's four) and no setting (Q4 A);
  diagnostics lines for every silent state, ~~the re-runs of the phase 10 rows this touches,~~ (SUPERSEDED 2026-10-07 by r3
  V1: dropped — phase 10's rows are not re-run; A2 and A4 read what this phase changes) the exported-components and
  APK-budget checks.
**Out (explicitly):** the TV-channels app (PLAN.md 2026-09-23 scope add; R13 first, then its own phase); building any
service's own client — no Spotify / YouTube Music / Apple Music / Tidal / Deezer playback inside the shell, no scraping of a
service's catalogue; bypassing any service's DRM, sign-in or paywall; ripping, recording, caching or time-shifting a stream to
a file (the Voice Recorder records the microphone, not the mixer; nothing here writes audio); an FM tuner (W10M's separate FM
Radio app needed a receiver the S25 Ultra does not expose; ~~P11 records the device facts~~ SUPERSEDED 2026-10-07 by r3 V1:
the process-start line `[music] radio: fm feature=<bool>`, read on the Diagnostics page in P1, records it);
a music store; Microsoft's cloud
services; any interim "list-only" Radio or "shortcuts-only" streaming build (Hard Rule 16; the interview ruled the fuller
forms); a Plex client (phase 17 Q-B: Jellyfin only); pinning a station to Start (T20-4); a "Radio" App Shortcut (T20-3); any
change to phase 10's measured now-playing geometry for local tracks (R8, H-M1 signed off).

## Decisions
- 2026-10-07 09:25: **Q-20-1 ANSWERED — browsing stations offline is not a goal** (Jeremy, asked how much of the directory lives
  on the phone, first "will the stations work offline?" — no, a station is a live stream — then: "why would it matter
  since you can't paly any of them offline so having a list offline does not help anyone it can cache the whole thing but
  no reason to be offline since you have to be online to listen to it"). CORRECTED 2026-10-07 09:27 (Jeremy, to the lead's first reading, which kept only the 2,000 most popular: "I said all stations to be cached"): **the WHOLE directory is cached on the phone.** Radio is an ONLINE feature. This supersedes the "browsable offline" half of Q4 A and the last sentence of Q2 A below for the
  station directory (the rest of both stands: internet radio, favourites, play on any network, the mobile-data line, no
  setting). What is built: every working station of the directory (about 53,000; about 20 MB as the 11 slim fields), the genres and the countries are kept on the phone, so search, genre, country and Tess all run over the whole directory by code with no round trip and no second "online search" path exists. Agent calls on how (r11/music-radio-addendum-2026-10-07.md §1; he may overrule): it is fetched in pages of 2,000 ordered by popularity, so the first page is listed at once and the rest fills in behind it; it is refreshed when older than 7 days on an unmetered network and on demand on any network (a full refresh is 60–85 MB on the wire — never by itself on mobile data); a failed or half-finished refresh keeps the previous cache whole. With no network the pivot shows favourites and whatever
  the cache holds under the line "No connection — stations need the internet"; nothing is designed, drawn or tested for
  browsing offline: A1 has no offline leg, and H2 no longer asks him to accept an offline arrangement.
- 2026-10-07 (round 3 triage, the last round; review/2026-10-07-phase20-r3-triage.md): 25 review findings (D1–D17, V1–V8) and
  the fifteen items of r11/music-radio-addendum-2026-10-07.md §7 are applied, none rejected. **Trust:** D1 (station / server
  items come from two builders only, a stranger's search stays library-only), D2 (logos fetched by the shell, never
  `artworkUri`), D9 (`RadioText.shown` on every directory string and StreamTitle), D11 / D12 (no playlist is fetched;
  cross-protocol redirects stay refused; `StationUrl.accept` refuses private hosts), D4 (server tracks carry no token), D13
  (`CoverArt.mayFollow`). **Behaviour:** D3 + V3 (the music hand-off's named ADDs to phase 17's part), D5 + V7 (what "the
  directory" is), D6 + V2 + V5 (ONE resolver, `MusicSearch.resolve(query, library, stations)`), D7 (`Request.ListenOn`), D8
  (one reconnect clock), D10 + V6 (HLS is added), D14 (captive portal only), D15 (queues), D17 (catalogue values). **Stale
  text:** D16 + V8. **Tests:** V1 re-cuts Acceptance under "Build, push, then ONE round of testing" (Jeremy, 2026-10-07) —
  device rows A1–A7, three static checks, the JVM table carried by the build tasks, phone rows P1–P4, NEEDS-HUMAN H1–H5; V4
  rewrites the fixtures (build task 11). **Told to Jeremy, not asked (five):** "play <name>" keeps finding his own music
  first, and a station is found by name only when it is a favourite or he says "radio"; "listen to <x> on <app>" asks for an
  unlock when the phone is locked; HLS stations play (one more 224 KB library) and a station that hops between https and
  http by redirect does not; station logos show for favourites and the playing station only; the shell tells radio-browser
  when a station is started (their click counter). **Test placement, the lead's four calls (he may overrule):** Data Saver —
  phone only, one listen in P1, the AOSP rule cited and not asserted on the AVD; Pandora — the JVM plan test carries the form
  and P3 proves the installed app; crossfade between two server tracks — P1's listening plus the existing crossfade unit
  tests, no device row; no FM receiver — one process-start line `[music] radio: fm feature=<bool>` read on the Diagnostics
  page in P1. **Q-20-1** (asked 2026-10-07) is answered in the entry above: the whole directory is cached; offline browsing is not a goal. The entries below keep their dates; each amended one says which r3 id amended it, and the new agent entries are at
  the end of Decisions.
- 2026-09-23: Review question Q-D — plain http is allowed for MEDIA only (Jeremy: "(a)"): radio stream URLs and the user's
  Jellyfin server may use http; the shell's own fixed endpoints (TMDB, radio-browser's directory, MusicBrainz / Cover Art
  Archive, weather) stay https-only, enforced by Android's network security config (cleartext denied by default, permitted
  only on the media playback path); a media-server sign-in over http to an address outside the home network (not a private /
  link-local range) asks first. A trust change: the network security config and the per-path cleartext rule get the
  adversarial review the project requires before done (build-prompt trust list, C-16). Every row and task written "under A"
  is the ruled form; the B and C branches are not built.
- 2026-09-23: Interview Q5 follow-up — the hand-off table's first full entry is Pandora (Jeremy: "(c)"; asked which music
  apps he uses). Pandora gets its in-app search deep link (the exact URI form is verified against the installed app at build
  start and recorded; if Pandora exposes no search link, "Listen on Pandora" opens Pandora and the diagnostics say the search
  could not be passed) and a phone row on the S25 Ultra. Every other installed music app gets a plain open hand-off.
  - Note 2026-10-07 (r3 D3; r11/music-radio-addendum-2026-10-07.md §3, §7.14): Pandora's form is recorded now, not at build
    start — `pandorav8://search/<urlencoded query>/all`, then `https://www.pandora.com/search/<q>/all`, then
    `MEDIA_PLAY_FROM_SEARCH`, then the plain open; "search not passed" is the last fallback only. Whether the installed
    app lands on results is the phone's to prove (P3; addendum §8).
- 2026-09-23: Interview Q5 — Tess gets radio and streaming phrases (Jeremy, to the question's Spotify example: "I dont ever use
  spotify but I do use other apps"; read as A with the hand-off naming any installed music app, Jeremy can overrule): "play
  <station>", "play <genre> radio", "play radio" (the last favourite), resolved by code against the cached directory (P6) and
  played through the session path phase 03 uses over the keyguard; and "listen to <x> on <app>" as a hand-off to that
  installed music app through phase 17's StreamingHandoff. The hand-off table is seeded with the music apps Jeremy uses (asked
  2026-09-23; his list lands here), each with its in-app search deep link and a phone row; any other installed music app gets
  a plain open hand-off. Spotify is not a priority entry.
  - Note 2026-09-23 (r2 triage T20-7): "his list lands here" is answered — his answer: Pandora (the Q5 follow-up line above);
    no further list is a FINAL gate. ~~Pandora's row is E13b (stub) and P9 (the S25U, T20-8); the plain open is E13b's third stub.~~
    SUPERSEDED 2026-10-07 by r3 V1: Pandora's form is the JVM plan test (`MusicServicesTable.plan`) and P3 on the phone; the
    plain open is A6's.
  - Note 2026-09-23 (r2 triage T20-9): "play radio" (the last favourite) means the favourite played most recently; with none
    played yet, the first favourite. ~~E16 proves both halves.~~ SUPERSEDED 2026-10-07 by r3 V1 / V5: JVM
    (`MusicSearch.resolve`, `RadioFavourites.lastPlayed`) and A5; with NO favourite at all, "play radio" is an ordinary
    search for "radio" (r3 D6, agent entry at the end of Decisions).
  - Note 2026-10-07 (r3 D7): "listen to <x> on <app>" starts another app's activity, so on a locked phone it shows the unlock
    card first (phase 03's PQ3, and this question's own option B text); the station phrases still play over the keyguard.
- 2026-09-23: Interview Q4 — browse offline, play on any network (Jeremy: "(a)"): the station directory and favourites are
  cached and browsable offline; a station plays on Wi-Fi or mobile data, with a "Streaming over mobile data" line while the
  network is metered; no new setting.
- 2026-09-23: Interview Q3 — Radio is a fifth pivot in Music, played by MusicService (Jeremy: "(a) unless there is a way to
  build an app that uses the phones antanas to pick up fm radio"). Jeremy's condition was checked and does not hold, so A
  stands: the S25 Ultra's Snapdragon SoC may carry FM silicon but Samsung has not enabled FM on the S25 series (no FM app;
  NextRadio does not work — Samsung Community and Best Buy Q&A, 2025), and Android's broadcast-radio stack (RadioManager /
  the Broadcast Radio HAL) is reachable only by system-privileged apps, which the shell cannot be on a locked One UI 8
  phone (source.android.com, automotive broadcast radio). Sources: https://eu.community.samsung.com/t5/galaxy-s25-series/nextradio-en-galaxy-s25/td-p/11681296 ,
  https://www.bestbuy.com/site/questions/samsung-galaxy-s25-ultra-512gb-unlocked-titanium-black/6612728/question/87a0770c-8711-3503-8a7a-ca5c6460f745 ,
  https://source.android.com/docs/automotive/broadcast-radio
- 2026-09-23: Interview Q2 — Radio is internet radio (Jeremy: "(a)"): stations from the open radio-browser.info directory,
  browsed by genre / country / search, with favourites, played live in the shell's own player (MusicService). No account; the
  directory is cached so browsing works offline.
- 2026-09-23: Interview Q1 — Music's streaming side is phase 17's pattern plus the media server played in the shell's own
  player (Jeremy: "(c)"). A search over a public music database (artist / album / track, info and artwork) with "Listen on
  <service>" opening the installed service's app at the title, the user signed in there; AND the user's Jellyfin server's music
  (phase 17 Q-B: Jellyfin only) browsed as albums / artists / songs and played through MusicService, with the equaliser,
  sleep timer, crossfade and the Music tile. Agent call for the database (P5, P6, no key): MusicBrainz for the catalogue and
  the Cover Art Archive for artwork, both keyless and open; since neither says where a title streams, "Listen on <service>"
  opens each installed service's own search for the title (its search deep link), through phase 17's StreamingHandoff.
- 2026-09-23: **A11 AMENDED** (Jeremy, phase 17 interview: "oh I thought you meant build your own mail and browser which is a
  no BUT internet is fine but prefer offline so probubly (c)"; PLAN.md, INDEX Change Log). Option (c) was "open it for all
  media: Movies & TV, plus streaming and Radio for Music, which reopens phase 10's no-streaming ruling". The shell may use the
  internet where a feature needs it; offline is preferred wherever an offline way exists (with P6). This phase exists because
  of that entry; nothing in the built player changed on the day.
- 2026-09-22: **phase 10 Q1's exclusion no longer holds** (INDEX Change Log 2026-09-23): "'no cloud' (A11) takes Groove's Radio
  pivot and its streaming catalogue off the table by itself". Radio and streaming are a NEW part with this doc; phase 10's
  local library, its four pivots, its now-playing screen, playlists, sleep timer, equaliser and crossfade are the finished
  part this ADDs to.
- 2026-09-23: **phase 17 Q3b** (Jeremy: "(a)") built the pattern this phase reuses: "an online catalogue of films and shows
  from a public film database; per title, which streaming apps on the phone have it, with 'Watch on <service>' opening that
  app at the title (the user still signs in inside each service's own app); and the user's own media server (Jellyfin or
  Plex) when there is one … This phase builds the streaming hand-off (catalogue lookup and 'open this title in that app')
  that the channels app and Music's streaming side reuse." ~~So this phase builds NO second hand-off: the installed-app
  discovery, the "open at a title" call and the media-server connection are phase 17's, and depends-on carries 17.~~
  SUPERSEDED 2026-10-07 by r3 D3 / D4: phase 17's hand-off as built is a fixed VIDEO table keyed by TMDB id, and its server
  client lists and streams video only; this phase reuses its try-next loop, its connection and its token store, and ADDs
  the music parts by name (agent entries "the music hand-off" and "credentials" below). depends-on still carries 17.
  - Note 2026-09-23 (r2 triage T20-1): phase 17 Q-B ruled Jellyfin only (no Plex anywhere), and for music the hand-off opens
    each service's own search for the title (Q1 Decision above), since MusicBrainz says nothing about where a title streams.
- 2026-09-22: **phase 10 Q4, the tile rule, applies unchanged**: "the now-playing face belongs to THE TILE OF THE APP THAT
  OWNS THE SESSION". A station or a media-server track played in `MusicService` lands on the Music tile (`MusicFeed`
  publishes under `LiveTileEngine.packageKey(owner)` and grows it through `ActiveTiles.setPackage`); a title handed to
  Spotify lands on Spotify's tile if one is pinned and nowhere otherwise. No tile is borrowed. Phase 15's build task 0
  (routing a shell-owned session by its tag; moved there from phase 17 by the 2026-09-23 review triage, C-1) must be in first, so a station never puts its face on Photos or Camera.
- 2026-09-22: **phase 10 Q7 / Hard Rule 16** ("EVERYTHING there is only one version built"): this phase builds the final form
  of its part. Radio with no favourites, or a hand-off with no catalogue, is not a first version; if the interview rules the
  fuller form, that is what is built.
- 2026-09-16: **P5, no Google unless required.** The shell's own network uses here (a station directory, a music catalogue) are
  chosen among non-Google sources with a stated reason at build start. A hand-off to YouTube Music is the user's own app
  answering an intent, not a Google dependency of the shell, and it is never required: a service that is not installed
  simply does not appear.
- 2026-09-23: **P6, deterministic first.** Station search, genre and country filters, favourites, and the resolution of a Tess
  phrase to a station or a library item are computed by code against cached data; the LLM (phase 08) receives the resolved
  item, never the search.
- 2026-09-16: **phase 03 Commands ruling** ("play music" and "play <song / artist / playlist>" are Tess's) and its locked-phone
  rule (PQ3: music goes to a media session, never to an activity, so it works over the keyguard). Radio phrases are new and
  are Q5's. ~~**Observed in code, 2026-09-23:** `ActionLayer.playMusic` sends `transportControls.playFromSearch(query)` to the
  active music controller, and `MusicService`'s `callback` overrides only `onConnect` / `onCustomCommand` — it does not
  override `MediaSession.Callback.onSetMediaItems` … So "play <song>" against the shell's own player resolves nothing today …
  if Q5 rules Tess radio commands in, the one override that answers them resolves library queries too (build task 7).~~
  SUPERSEDED 2026-09-23 by T20-2: **closed by J5** (docs/plan/qa/JEREMY-QA.md J5, commit 373106a, J5.txt 8/8): `MusicSearch`
  (`app/src/main/kotlin/app/tileshell/music/MusicSearch.kt:14`) resolves song / artist / album by code, and `MusicService`'s
  session callback answers searches in `onSetMediaItems` (`app/src/main/kotlin/app/tileshell/music/MusicService.kt:180-201`;
  a miss logs `[music] search "<q>": nothing in the library` at `:191` and fails instead of clearing the queue, a hit logs
  `[music] search "<q>": <kind> <label>, n track(s)` at `:194`; the three cites re-read 2026-10-07, r3 D16). This phase
  EXTENDS `MusicSearch` with stations ~~— favourites,
  then the cached directory by name / genre — ahead of the library, inside that existing callback (build task 7); no new
  override,~~ SUPERSEDED 2026-10-07 by r3 D1 / D6 (agent entries "station and server items" and "one resolver" at the end of
  Decisions: the library keeps "play <song>", stations reach the session only from the shell's own uid, and the scan
  test's pinned clauses are rewritten), and J5's line form is the one this phase's lines follow.
- 2026-09-22: **phase 11 Q1 standing rule**: Music declares Songs / Albums / Artists / Playlists as static App Shortcuts. ~~A
  Radio pivot (Q3) adds "Radio" to that list — an ADD to phase 11's part, Change Log when built.~~ SUPERSEDED 2026-09-23 by
  T20-3 (agent line at the end of Decisions): no "Radio" shortcut is declared; Music's four stay as phase 11 Q1 ruled them.
- 2026-09-23 (agent): **one engine, the same service.** Whatever sounds through the shell sounds through `MusicService`'s
  ExoPlayer (Media3 ~~1.8.0~~ 1.9.0, `gradle/libs.versions.toml:12` — SUPERSEDED 2026-10-07 by r3 D16), the
  session `MusicPlayer` already connects to, the `KnownDurationPlayer`
  it wraps and the `CrossfadeFader` beside it. No second player, no second session (RV2 / Hard Rule 16). Progressive HTTP
  audio (MP3, AAC, Ogg — what most internet stations send) plays through the `DefaultMediaSourceFactory` the service already
  builds. HLS stations need `media3-exoplayer-hls`, the same Apache-2.0 library's own module and an ADD to
  `app/build.gradle.kts`, not a second engine; ~~whether the ruled directory has enough HLS stations to justify it is a
  build-start count, recorded here.~~ SUPERSEDED 2026-10-07 by r3 D10 (agent entry "HLS" at the end of Decisions): it IS
  added.
- 2026-09-23 (agent): **a live item has no length, and the built code already says what that does.** `KnownDurationPlayer`
  falls back to the item's `mediaMetadata.durationMs` when the extractor reports `C.TIME_UNSET`; a station item has none, so
  `MusicPlayer.durationMs` reads 0. Then `thumbCentreFraction` (MusicNowPlaying.kt) pins the thumb to the left stop,
  `clockText(0)` draws "0:00" as the total, `seekFromTouch` seeks to 0, and the elapsed label counts up from the connect.
  ~~`CrossfadeFader.considerPreparing` returns on `C.TIME_UNSET`, so no fade is ever attempted out of a stream — nothing to
  build there.~~ SUPERSEDED 2026-10-07 by r3 D10: an HLS live window reports a duration above 0, so "live" is the item's
  mark alone (`MusicLive.isLive(mediaId)`, true for a `station:` id) and `considerPreparing` gains "returns for a live
  item" (entry "HLS" at the end of Decisions).
  The sleep timer's `SleepTimer.END_OF_TRACK` arms ExoPlayer's `pauseAtEndOfMediaItems`, which a stream never
  reaches. So a live item needs its own now-playing form — a **P4 design** (R8 measured Groove's now-playing for tracks;
  Groove's Radio played tracks, not live streams, so no measurement exists): the scrubber is drawn as a full-width live bar with
  no thumb and no seek, the two time labels are replaced by a single "LIVE" caption at the elapsed label's position, and the
  `•••` sleep list omits its end-of-track entry (`music_menu_sleep:eot`) while a live item plays; the minute choices stay.
  Everything else on the screen — chrome, art (the station's logo where the directory has one, else the album-art placeholder),
  the two-line metadata block, the transport row, the chevron and the queue — is R8's as built. NEEDS-HUMAN H1. ~~E5 proves it.~~
  SUPERSEDED 2026-10-07 by r3 V1: A2 on the device; JVM `MusicLive.isLive`, `moreEntries`.
- 2026-09-23 (agent; amended 2026-10-07, trust): **live metadata.** Media3 1.9.0 maps a stream's ICY data as recorded in
  r11/music-radio-addendum-2026-10-07.md §4: `IcyHeaders` name → `MediaMetadata.station`, genre → `genre`; `IcyInfo`
  StreamTitle → `title` only; "MediaItem metadata is prioritized over metadata within the media"; an HLS station gives no
  StreamTitle. So: the station item sets `albumTitle` AND `artist` to the station name, never `title`. The session's
  player wrapper (`KnownDurationPlayer`) overrides `getMediaMetadata()`: for a `station:` item with a null title, title =
  the station name (pure `LiveMetadata.merge(stationName, icyTitle)`, JVM-tested). `MusicPlayer.readSession` falls back to
  `albumTitle` for a queue row with no title. Every directory string and StreamTitle passes pure `RadioText.shown(raw, max)`
  (`FilesIntents.lineSafe`'s rule, plus bidi / format characters; names ≤ 80, StreamTitle ≤ 120) before the tile, the
  notification, a diagnostics line or Tess's reply. `MusicFeed` is unchanged: it reads `METADATA_KEY_TITLE` / `ARTIST` /
  `ALBUM` off the session and republishes on any change, so the Music tile shows the song now on air. Checked by A2 (the
  title switch and the album line) and the two JVM rules. (r3 D9, addendum §7.2, 2026-10-07; the 2026-09-23 wording is in
  git, commit d6602757 and earlier)
- 2026-09-23 (agent; amended 2026-10-07): **the station directory** (Q2 A) **[Q-20-1 answered 2026-10-07: the entry at the top of Decisions — the WHOLE directory is cached; no offline promise]**:
  radio-browser.info — an open, community directory with a free JSON API, no key and no Google. Recorded facts
  (r11/music-radio-addendum-2026-10-07.md §1): data public domain, no stated rate limit, "can be used freely but without
  guarantee"; about 53,000 working stations (roughly 20 MB slim, 50+ pages), ONE mirror today; a list call with no `limit`
  returns 1,000 rows; the etiquette asks for a speaking User-Agent (`Tessera/<version> (…)`, pure `MusicNet.userAgent`), a
  DNS lookup rather than a fixed server, and a click call on every station start.
  **Fetched** (explicit limits, `hidebroken=true`): the whole directory as pages `/json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=<n>` (about 2.4 MB a page, about 27 pages, one request at a time, until a page returns fewer than 2,000 rows; at most 40 pages),
  `/json/tags?order=stationcount&reverse=true&limit=500`, `/json/countries`. **Cached** (`AtomicFile`,
  `radio_directory_v1.json`): 11 slim fields per station (~0.8 MB), the 500 tags, the countries; video-codec rows dropped
  at parse. **Caps:** any directory response over 4 MiB → `[music] radio: directory too large`, cache kept; cache file ≤ 32 MiB (written page by page to a temp file and renamed only when the last page is in, so a half-finished refresh never replaces a whole cache; the first-ever fetch shows each page as it lands). **Tess always (P6), and the pivot with no network:** search / genre / country run over the WHOLE cached directory plus favourites, results ordered by popularity and listed 100 at a time. There is no online search call (Q-20-1: everything is in the cache).
  **Favourites** (`radio_favourites.json`, ≤ 200; a temp file and a rename like `PlaylistStore`'s) store the full slim row
  plus `lastPlayedWall`; refreshed by uuid on directory refresh (`/json/stations/byuuid`). **Refresh:** on opening the
  radio pivot when the cache is older than 7 days and the network is unmetered, and on demand on any network; a fetch failure keeps the cache and says so (`[music]
  radio: directory offline, cache from <date>`), never an empty list. **Mirror:**
  `InetAddress.getAllByName("all.api.radio-browser.info")` → each address's canonical host name → pure
  `RadioMirrors.order(names, seed)` keeps only names ending `.api.radio-browser.info` (inside the https-only
  domain-config; `EndpointLiteralScanTest.kt:121` accepts subdomains), dedupes, shuffles, tries each in turn; an empty
  list → `all.api.radio-browser.info` (JVM-tested, T20-10; a debug `qa_radio_base` override bypasses the lookup). **Click:**
  `GET /json/url/<uuid>` on each user-initiated start (tap, next / previous, Tess), never on a reconnect. All through
  `VideoHttp.get(url, headers, maxBytes)` (`video/catalogue/Catalogue.kt:63`: capped, logs no URL), not a new
  `HttpURLConnection` path; no HTTP library is added. Jeremy's answer to Q-20-1 (top of Decisions): the whole directory is cached; no offline browsing is promised. (r3 D5 / V7, addendum §7.3–7.5, 2026-10-07; the 2026-09-23 wording is in git, commit d6602757 and earlier)
- 2026-09-23 (agent): **the music catalogue** (Q1 C; the database is the Q1 Decision's agent call): MusicBrainz (open data,
  no key) with the Cover Art Archive for artwork, ~~the terms re-read at build start and recorded here~~ (SUPERSEDED
  2026-10-07 by r3 D17: the terms are recorded in the amendment below) (P5: not a Google API);
  MusicBrainz's 1-request-per-second etiquette is honoured (requests spaced ≥ 1.0 s) and every request carries the
  `Tessera/<version> (…)` User-Agent ~~(E13 asserts both)~~ (SUPERSEDED 2026-10-07 by r3 V1: JVM `RateSpacing.waitMs` and
  `MusicNet.userAgent`; A6's fixture log shows the User-Agent). Catalogue rows are cached per search for the session only; a
  failed search logs `[music] catalogue "<q>": offline | error <code> | error connect` and the page says so. ~~A title's "Listen on
  <service>" list comes from phase 17's installed-app discovery and opens each service's own search for the title (Q1): Pandora
  through its search form recorded at build start (Q5 follow-up), any other installed music app (`CATEGORY_APP_MUSIC`) with no
  recorded form as a plain open (`[music] handoff: <app> "<title>" -> open (no search link)`), and Android's
  `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` with `EXTRA_MEDIA_FOCUS` tried where a service answers it. Which services
  appear is never a fixed list; discovery runs at call time through `PackageManager` the way phase 17 does it. Search forms
  change and this doc cannot verify them, so they are recorded at build start with their verification, not here.~~
  SUPERSEDED 2026-10-07 by r3 D3 (agent entry "the music hand-off" at the end of Decisions).
  **Amended 2026-10-07 (r3 D17; addendum §2):** the search is MusicBrainz `recording` (`/ws/2/recording?query=…&fmt=json`),
  `limit=25`, submit-only (no search-as-you-type), one request in flight; art is `releases[0].id` at
  `/release/<mbid>/front-250`; a 503 → `[music] catalogue "<q>": error 503` with no automatic retry; the spacing is pure
  `RateSpacing.waitMs(lastStartMs, nowMs)` and the URL / parse rules are pure `MusicCatalogue.searchUrl` / `.parse` /
  `.coverUrl` (all JVM-tested). Recorded terms: MusicBrainz core data CC0, on average 1 request per second per IP, a
  blank or library-default User-Agent is throttled; the Cover Art Archive states no rate limit and grants no licence on
  the images. **Amended 2026-10-07 (r3 D13; trust):** `VideoHttp` as built follows no redirect (`Catalogue.kt:79`) and the
  cover-art chain is 307 → `archive.org` → `*.archive.org`, so a cover-art fetch follows ≤ 3 redirects by hand, only when
  pure `CoverArt.mayFollow(location)` says https and host = `archive.org` or `*.archive.org`; no header carried; ≤
  `MAX_IMAGE_BYTES`.
- 2026-09-23 (agent): **permissions.** `INTERNET` and `ACCESS_NETWORK_STATE` are already held (AndroidManifest.xml lines 32–33,
  Weather's), `FOREGROUND_SERVICE_MEDIA_PLAYBACK` too. A stream through a screen-off needs the player's wake mode:
  `ExoPlayer.Builder.setWakeMode(C.WAKE_MODE_NETWORK)` (a wake lock plus a Wi-Fi lock while playing), ~~which needs the
  install-time `WAKE_LOCK` permission — an ADD to the manifest and to the shared player~~ SUPERSEDED 2026-10-07 by r3 D16 /
  V8: `WAKE_LOCK` is already held (`AndroidManifest.xml:78`, phase 15's alarm ring), so the ADD is `setWakeMode` on the
  shared player and nothing in the manifest — so a local track gets the same
  wake mode. The INDEX records that phase 10 still owes its screen-off row because the player ran "without WAKE_MODE_LOCAL";
  this ADD closes that as well and is recorded in the Change Log for phase 10's part. No new checklist row: install-time
  permissions have none (phase 03's `USE_EXACT_ALARM` precedent).
- 2026-09-23 (agent; amended 2026-10-07): **reconnect.** ExoPlayer retries a live progressive load itself six times at 0 / 1
  / 2 / 3 / 4 / 5 s (plus 8 s timeouts) before any player error, and `WAKE_MODE_NETWORK` holds its locks only while READY
  or BUFFERING (r11/music-radio-addendum-2026-10-07.md §4, §7.10). So there is ONE clock: T0 = the first load error of a
  live item (`AnalyticsListener.onLoadError`), which writes `[music] stream: lost, retrying`. On each `onPlayerError` the
  service re-prepares at 2 / 4 / 8 / 16 / 30 s; at T0 + 60 s it pauses and writes `[music] stream: gave up after 60000 ms`.
  From T0 until reconnect or give-up the service holds its own partial wake lock (65 s timeout). "Reconnecting…" and "This
  station isn't answering" ride session extras (`X_STREAM_STATE`), not metadata; the tile keeps the last title. Pure:
  `StreamRetry.next(sinceLostMs, attempt)` (JVM-tested). A network that returns inside the window resumes the stream with
  no tap (`[music] stream: reconnected after <ms> ms`); after the window a tap is needed. The window and back-off are an
  agent pick (Y4, H5). This is not phase 10's "nothing resumes by itself" — that rule is about audio focus taken by another
  app, which still holds (P2). (r3 D8, addendum §7.10, 2026-10-07; the 2026-09-23 wording is in git, commit d6602757 and
  earlier)
- 2026-09-23 (agent, P4 design, ~~H3~~ H1 — renumbered 2026-10-07 by r3 V1): **metered data.** A stream is a continuous
  download. When the active network is metered
  (`ConnectivityManager.isActiveNetworkMetered`) the now-playing screen shows one line under the metadata, "Streaming over
  mobile data", and plays — the person pressed play. With Android's Data Saver on (`getRestrictBackgroundStatus`), the
  foreground service keeps its stream ~~(the platform exempts foreground services)~~ (SUPERSEDED 2026-10-07 by r3 D16:
  developer.android.com says only that Data Saver "blocks background data usage"; the exemption is AOSP's rule — a
  process at or above bound-foreground-service state is allowed — r11/music-radio-addendum-2026-10-07.md §6, §7.12,
  MEDIUM. It is cited, not asserted on the AVD; P1's Data Saver listen on the phone is the proof), and the line says so.
  No Wi-Fi-only
  setting exists on top of this (Q4 A).
- 2026-09-23 (agent; amended 2026-10-07): **captive portal.** Only a network Android has flagged
  `NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL` makes a station tap show "Sign in to this Wi-Fi network first" and
  start no stream (`[music] stream: captive portal`). A network that is merely not validated (a just-joined Wi-Fi, a LAN
  with no internet) is NOT treated as captive: the station plays into the reconnect path. Server tracks are exempt from
  both (a home server on a LAN with no internet must play). Pure: `StreamGate.decide(hasNetwork, captive, metered,
  dataSaver, isStation)` (JVM-tested); it also gives the metered line and the no-network answer. The sign-in itself is
  Android's own captive-portal notification — the shell opens no page of its own (Android limits) — and the directory
  falls back to its cache. (r3 D14, 2026-10-07; the 2026-09-23 wording is in git, commit d6602757 and earlier)
- 2026-09-23 (agent): **the Radio pivot** (Q3 A): `MusicPivot` gains a fifth entry titled "radio"
  (R3's lowercase pivot form, as the four built ones), `MusicCollection.page` gains its case, and the header strip's
  scroll-no-further-than-needed rule (phase 10 task 6 amendment, MUSIC7) already covers a fifth header — ~~E1 re-measures it~~
  (SUPERSEDED 2026-10-07 by r3 V1: A1 asserts the five headers with radio last and its bounds on screen; the strip's
  geometry is phase 10's row and is not re-measured; H2 sees it). Browse rows draw the logo placeholder; a logo is
  fetched only for favourites and the playing station (r3 D2). Its
  list rows are the app list's (R3 C2 / R6 §5.1.4) as the other pivots are; favourites first under a "favourites" letter-less
  group, then a browse row set (search, by genre, by country) — the arrangement is a **P4 design** (Groove's Radio pivot held
  artist stations, not a directory; no measurement exists), H2. A hold on a station offers add to / remove from favourites
  only. ~~and pin-to-Start (the phase 11 burst carries it once phase 11 is in)~~ SUPERSEDED 2026-09-23 by T20-4 (agent line at
  the end of Decisions).
- 2026-09-23 (agent): **harness contracts.** Every new node a row reads carries its own test tag under the
  `testTagsAsResourceId` root `MusicActivity` already sets (phase 10's `music_pri:` / `music_sub:` lesson); every silent
  state writes a `[music]` diagnostics line ~~(wording in E19)~~ (SUPERSEDED 2026-10-07 by r3 V1: seven patterns are asserted
  on the device, Acceptance "Diagnostics lines asserted on the device"; every other line's builder is JVM-tested).
  The tags the rows below name that do not exist today
  (`radio_row:`, `radio_fav:`, `nowplaying_live`, `nowplaying_live_caption`, `nowplaying_metered`, `catalogue_row:`,
  `handoff_service:`) are this phase's own, added by the task that draws each node. Drivers: qa/phase-20/scripts/lib.sh → symlink to
  qa/phase-03/scripts/lib.sh, plus qa/phase-01/scripts/music_lib.sh (its `music_fixtures`, `music_open`, `goto_pivot`);
  evidence under qa/phase-20/. ~~The directory and catalogue base URLs are debug-build `BuildConfig` fields defaulting to the
  real endpoints in every build type~~ SUPERSEDED 2026-09-23 by T20-6: the directory, catalogue and Cover Art bases are
  debug-only prefs (`qa_radio_base`, `qa_music_catalogue_base`, `qa_coverart_base`, written with
  `qa/phase-01/scripts/prefs_edit.py`) honoured only when `BuildConfig.DEBUG`, phase 17's route (its task 12), so one debug APK
  serves every row; the release APK cannot be redirected. That is a fixture route for a data source, not a
  request-injection path (phase 03's rule pins Cortana's inputs; nothing here takes a Cortana request).
  **Amended 2026-10-07 (r3 D16):** the three prefs are constants beside phase 17's — `QaBases.RADIO` = `qa_radio_base`,
  `QaBases.MUSIC_CATALOGUE` = `qa_music_catalogue_base`, `QaBases.COVERART` = `qa_coverart_base`
  (`video/catalogue/Catalogue.kt:23-38`; prefs file `start_theme`) — read only through `QaBases.read`, which returns null
  in a release build. A set `qa_radio_base` bypasses the mirror lookup, carries the click call, and names the one host
  `StationUrl.accept` lets through although it is a private address (entry "untrusted URLs" below). Tess is typed in
  every device row (`lib.sh` `type_request`); no row uses the host audio route.
- 2026-09-23 (agent): **the two kinds of NEEDS-HUMAN row are labelled** as phase 17 labels them: *fidelity* (matches a
  measurement, judged on the phone) and *accept* (a P4 design or an approximation; Jeremy accepts or overrules).
  qa/phase-20/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.
- 2026-09-23 (review triage T20-5, a doc update; trust; amended 2026-10-07): **untrusted URLs never reach
  ExoPlayer's other schemes.** The
  service's `DefaultDataSource` also opens `file://`, `content://`, `asset://`, `rawresource://` and `data:`, and station URLs
  come from a community directory. ~~So a station URL and a `.pls` / `.m3u` entry are accepted only as `http` / `https`
  (anything else → `[music] stream: unsupported scheme=<s>`, the station listed with "can't play this station"; a JVM test on
  the resolver covers each refused scheme and both accepted ones); a playlist entry is resolved only if http / https, else
  `[music] stream: unsupported playlist`; the directory response is capped (the cap recorded here at build start; larger →
  `[music] radio: directory too large`, the cache kept); station logos are decoded at bounds with a byte cap (arbitrary hosts).~~
  SUPERSEDED 2026-10-07 by r3 D2 / D5 / D11 / D12, which amend the rule to:
  - **Scheme and host (D12):** pure `StationUrl.accept(url, qaHost)` accepts only `http` / `https` (anything else →
    `[music] stream: unsupported scheme=<s>`, the station listed with "can't play this station") and also refuses an empty
    host, `localhost`, and IP-literal loopback / link-local / RFC 1918 / unspecified hosts (reuse `ServerRules.isPrivate`;
    `[music] stream: unsupported host`); names that resolve privately are not checked — stated for the adversarial
    review. `qaHost` is the host of the debug-only `qa_radio_base` override (null in a release build) and is the one
    private host let through, so the fixtures at `10.0.2.2` play in a debug build and nowhere else.
  - **No playlist (D11; addendum §7.7):** the shell fetches and parses no playlist. The play URL is `url_resolved`, else
    `url` when that is empty; a path ending `.pls` / `.m3u` / `.asx` → `[music] stream: unsupported playlist`, "can't play
    this station". Pure `StationUrl.playable(urlResolved, url, hls)`.
  - **Redirects (D12; addendum §7.9):** Media3's default is kept (`setAllowCrossProtocolRedirects` unset = false, as
    `video/VideoPlayback.kt:40`): same-scheme redirects are followed (≤ 20); a cross-scheme one fails the load →
    `[music] stream: redirect refused`, no retry. An http redirect to a fixed host is refused by the platform config.
  - **Logos (D2):** a station item NEVER sets `artworkUri` (Media3's session would load it itself, from any scheme its
    data source opens, uncapped). The logo is fetched by the shell (http / https only via `StationUrl.accept`, ≤ 512 KiB,
    decoded at bounds ≤ 512 px like `MusicService.boundedArt`; pure `StationLogo.accept(bytes)` / `.sampleSize(w, h)`)
    and set as `artworkData`. Logos are fetched only for favourites and the playing station; browse rows draw the
    placeholder (no fan-out to thousands of hosts).
  - **Directory cap (D5):** 4 MiB per response (a 2,000-row page), 32 MiB for the cache file (entry "the station directory").
  Every rule named here is JVM-tested (Acceptance, the unit-test table).
  All of it sits under C-16's adversarial review (below).
- 2026-09-23 (review triage C-16, a doc update; trust — the policy is **Q-D: A**; amended 2026-10-07): **plain-http
  streams.** Q-D ruled A and phase 17 BUILT it: `app/src/main/res/xml/network_security_config.xml` permits cleartext at
  the base config (station hosts cannot be listed) and keeps the shell's fixed endpoints https-only by its
  `<domain-config cleartextTrafficPermitted="false">` (includeSubdomains), which already lists `api.radio-browser.info`
  (its mirrors are subdomains), `musicbrainz.org`, `coverartarchive.org` and `archive.org` (the Cover Art redirect host,
  with subdomains — r11/music-radio-addendum-2026-10-07.md §7.15: fine as built). So an http station plays — 33.7 % of
  the 2,000 most-clicked `url_resolved` values are http (addendum §1, §7.8) — and the directory, the catalogue and the
  artwork never travel in the clear. This phase creates NO second config and adds nothing to it; phase 17's
  `FixedEndpointsTest` (JVM, as built) and its process-start `[net] cleartext permitted for <host>: <bool>` lines cover
  the fixed hosts. The debug-only QA exception (`app/src/debug/res/xml/network_security_config.xml`, `10.0.2.2`
  cleartext-permitted) lets the host fixtures run; the release config holds no `10.0.2.2` (a static check). Jellyfin music
  over http plays (Q-D: A; the off-home-network ask is phase 17's, at sign-in). No branch of the policy is conditional
  any more and the line `[music] stream: cleartext refused` does not exist. **GATE (unchanged):** this phase re-runs the
  adversarial review (team-review, adversarial mode), ONE pass and one fix round, on its station path — the trust surface
  listed in build task 12 — recorded under qa/phase-20/ before `done`, as phase 17's GATE is under qa/phase-17/. (r3 D16,
  addendum §7.8 / §7.15, 2026-10-07; the 2026-09-23 wording — the three policy branches and their row — is in git, commit
  d6602757 and earlier)
- 2026-09-23 (review triage C-32 / T20-12 / T20-13, a doc update; trust; amended 2026-10-07): **credentials.** Phase 17's
  Decisions "Trust" → "Credential hygiene (C-32)" governs here: no credential (the Jellyfin token, the fixture passwords,
  the TMDB read token) is ever written to a diagnostics line, logcat, a logged URL or an evidence file, and the row that
  touches one (A7) ends with `qa/phase-17/scripts/leak_scan.sh` over `qa/phase-20/**`, the saved ring slices and `adb
  logcat -d`. Server tracks play `<base>/Audio/<id>/stream?static=true` with NO token (Jellyfin 12.1: no `[Authorize]` —
  r11/music-radio-addendum-2026-10-07.md §5, §7.11), so the shared player carries no credential and radio hosts can never
  receive one. Listing uses the `Authorization` header as built. If the fixture answers 401 at build start (build task 1),
  the fallback is phase 17's own route and nothing else: `STREAM_PATH` becomes `/(Videos|Audio)/…/stream` and
  `MusicService`'s data source is wrapped in `ResolvingDataSource(MediaServer.streamResolver)` (already
  same-server-scoped by `mayCarryToken`). URLs are logged through `ServerRules.withoutQuery` either way (`[music] stream:
  connected http://10.0.2.2:8096/Audio/<id>/stream codec=mp3`; pure `StreamLine.url` also removes userinfo). ADDs to
  phase 17's part (Change Log): `ServerRules.musicPath(userId)` (`includeItemTypes=Audio&fields=…`), `parseTracks` (Album,
  AlbumArtist, IndexNumber, `RunTimeTicks/10_000` → `durationMs`), `audioStreamUrl`; `MediaServer.music()`; a `tag`
  constructor argument (default `"video"`), so the music side's lines are `[music] server <host>: connected | unreachable
  | unauthorised` in the MAIN ring (`MusicService` is main-process), not phase 17's `:video`-ring `[video] server …`. The
  token store is phase 17's, cross-process by design (`net/CredentialStore.kt:26-29`). Direct play only; nothing is
  transcoded. (r3 D4, addendum §7.11, 2026-10-07; the 2026-09-23 wording — its premise that the stream URL carries the
  token in its query — is in git, commit d6602757 and earlier)
- 2026-09-23 (agent, r2 triage T20-3): **Music keeps Jeremy's four App Shortcuts (Songs / Albums / Artists / Playlists) and
  declares no "Radio" shortcut**; the phase-11 Decision's "A Radio pivot adds 'Radio' to that list" ~~and E1's shortcut clause are
  struck; H9 → [accept] "Music's satellites stay the four; Jeremy can swap Playlists for Radio on the phone"~~ is struck
  (SUPERSEDED 2026-10-07 by r3 V1: that row and that H-row are gone — a ruling restated leaves nothing to judge, and
  nothing here declares a shortcut). Reason: phase 11 Q1
  named the four and the four-satellite cap is his ruling too (round 1's T11-10 precedent: keep the ruled four, an H row to
  swap); a rank-4 static shortcut never bursts and is dead weight.
- 2026-09-23 (agent, r2 triage T20-4): **no pin-a-station-to-Start**; a hold on a station offers add to / remove from
  favourites only. Reason: a pinned station is a new tile kind nobody designed or asked for; favourites and the Music tile's
  live face cover the need, and Rule 16 forbids a half form.
- 2026-09-23 (agent, r2 triage T20-6): **the fixture route is debug-only prefs** — `qa_radio_base`, `qa_music_catalogue_base`,
  `qa_coverart_base`, written with `qa/phase-01/scripts/prefs_edit.py` and honoured only when `BuildConfig.DEBUG` — replacing
  the "debug APK with BuildConfig fields pointed at 10.0.2.2 … and a build with the real defaults" route; the Acceptance
  preamble gains Seeding (from `qa/phase-18/baseline_layout.json`, the newest on disk at build under C-14's order), C-6, ~~C-4,
  C-5 / C-31, C-20, C-25 and C-26~~ (SUPERSEDED 2026-10-07 by r3 V1: the re-cut preamble keeps the seeding, C-6 and the
  ring / absence helpers; no motion row is left). Reason: one debug APK serves every row so the evidence's APK stamp
  matches (`lib.sh` ~~`apk
  match`~~ `apk_matches`, `lib.sh:380` — r3 V1), and it is phase 17's route (task 12).

- 2026-10-07 (agent, r3 D1; trust): **station and server items.** `MusicService` is exported and phase 18's `MusicItemRule`
  (ledger L18-1) lets only the shell's own controller keep an item's URI; `UriAccessWiringScanTest.kt:326-343` pins the
  service to two `.setUri(` and one `setMediaItems(`, and the search branch (`MusicService.kt:187-196`) runs for ANY uid.
  So: station and server items are built ONLY in `music/radio/StationItem.kt` and `music/server/ServerTrackItem.kt` (media
  ids `station:<uuid>` / `server:<id>`, never a decimal, so `MusicItemRule.libraryId` cannot match), each after its URL
  rule accepts. Taps set them through the shell's own controller (`MusicPlayer.playStations`), which `MusicItemRule`
  keeps. `MusicSearch.resolve` gains a `stations` argument and `Match` a station form; the session passes stations ONLY
  when `controller.uid == Process.myUid()` (pure `RadioSearchRule.stationsFor(controllerUid, myUid)`); a stranger's search
  stays library-only. `UriAccessWiringScanTest`'s `setItemsForm` and `.setUri(` clauses are rewritten in the same commit,
  with a new clause that the two builders are the only other `.setUri(` under `music/`; that diff is part of the
  adversarial review. Resumption stays unanswered: after process death no station queue returns. An ADD to phase 18's
  part (Change Log). JVM: `MusicItemRule` (the `station:` / `server:` cases), `RadioSearchRule.stationsFor`, the scan test.
- 2026-10-07 (agent, r3 D15): **queues.** A tapped favourite's queue is the favourites in order (next / previous step
  through them like presets); a non-favourite's is that station alone (pure `RadioFavourites.queueFor(station,
  favourites)`). A station play always replaces the queue; nothing adds a station to a track queue, so no mixed queue has
  a UI path. `[music] sleep: end-of-track cleared (live item)` fires when a station queue replaces a track queue with
  end-of-track armed (pure `MusicLive.clearsEndOfTrack(armed, mediaId)`).
- 2026-10-07 (agent, r3 D10 / V6; r11/music-radio-addendum-2026-10-07.md §1, §4, §7.6): **HLS.** `media3-exoplayer-hls` is
  added (224 KB; 9.8 % of the top 2,000; Rule 16; an ADD to `app/build.gradle.kts:119-122`). A row with `hls=1` gets
  `setMimeType(APPLICATION_M3U8)`. The line `stream: unsupported hls` is struck. An HLS live window has a duration above
  0, so live is the item's mark alone — pure `MusicLive.isLive(mediaId)` — in the now-playing form (build task 4) and in
  `CrossfadeFader.considerPreparing` (`:132-133`), which gains "returns for a live item". An HLS station shows no
  StreamTitle (the station name stands, entry "live metadata").
- 2026-10-07 (agent, r3 D6 / V2 / V5; amends T20-2 and T20-9): **one resolver.** `CommandMatcher.kt:91-93` already maps every
  "play <rest>" to `PlayMusic(rest)`, so station phrases need NO matcher ADD; but `ActionLayer.kt:584-594` resolves
  against the library itself and answers the miss before any session is asked. So ONE pure `MusicSearch.resolve(query,
  library, stations)` is used by both `ActionLayer.playMusic` (ADD to phase 03's part) and the session's search branch:
  (1) query = "radio": the most recently played favourite, else the first favourite; with NO favourites, the library (a
  song called "Radio"), else the miss reply. (2) query ends " radio" or starts "radio ": favourites by exact name (with
  and without the word), cached directory exact name, cached tag = the rest (highest clickcount), cached name contains;
  then the library. (3) otherwise: favourite exact name → library exact (J5) → library contains → cached directory exact
  name. The directory is never contains-matched for a query without "radio". `ActionLayer.playMusic` starts a station
  match through `MusicPlayer.playStations` when no controller exists; the reply label for a station is its name
  ("Playing <station>.", Y6); a hit logs `[music] search "<q>": station <name>`, a miss keeps J5's line and reply. Read
  with V5's sentence: with no favourite, "play radio" is an ordinary search for "radio" under rule (1)'s second half.
- 2026-10-07 (agent, r3 D7): **"listen to <x> on <app>".** New `Request.ListenOn(query, app)`, matched from `listen to <x>
  on <app>` (last ' on ' splits) before the `play` rule; `LockGate.allowedWhileLocked` = false (unlock card — `LockGate.kt:32`
  allows `PlayMusic` only because it goes to a session; a hand-off starts an activity); `<app>` resolved by code against
  the hand-off's discovery labels (normalised exact, then prefix; pure `MusicServicesTable.byLabel(phrase, entries)`); no
  match → "I couldn't find <app> on this phone." ADDs to phase 03's part (Change Log).
- 2026-10-07 (agent, r3 D3 / V3; r11/music-radio-addendum-2026-10-07.md §3, §7.13, §7.14; amends T20-8): **the music
  hand-off.** Phase 17's `StreamingHandoff.installedServices` filters a fixed VIDEO table, its `TitleRef` is TMDB-keyed,
  `open` writes `[video]` lines and asks Wikidata, and `HandoffPlan` has no `MEDIA_PLAY_FROM_SEARCH` form. The ADDs to
  phase 17's part (Change Log): (a) `music/handoff/MusicServicesTable.kt` (pure): Pandora `pandorav8://search/%s/all` then
  `https://www.pandora.com/search/%s/all`; YouTube Music `https://music.youtube.com/search?q=`, Amazon Music
  `https://music.amazon.com/search/<q>`, Apple Music `https://music.apple.com/us/search?term=`, Deezer
  `https://www.deezer.com/search/<q>`, SoundCloud `https://soundcloud.com/search?q=`; Tidal none (its link answered 403).
  `plan(service, title, artist): List<HandoffPlan>` ordered search link → `PlayFromSearch(query)` → `Launch`. (b)
  `HandoffPlan.PlayFromSearch` carrying `SearchManager.QUERY` + `EXTRA_MEDIA_FOCUS="vnd.android.cursor.item/*"`, package
  set, no data URI. (c) `StreamingHandoff.openPlans(context, packageName, plans, tag="music", line)` reusing `intentFor`'s
  try-next loop. (d) Discovery = table rows installed ∪ `MAIN` + `CATEGORY_APP_MUSIC` launchers, minus the shell
  (`app.tileshell` declares the category itself, `AndroidManifest.xml:164`) and the table's packages (pure
  `MusicServicesTable.entries(installed, musicLaunchers, self)`), run at call time, never cached. A local player (Auxio on
  the baseline) is a plain-open entry like any other, so the list is not asserted empty anywhere. Lines: `[music] handoff:
  <service> "<title>" -> <uri|intent>`, `-> open (no search link)`, `<service> did not open at the title` (the next plan is
  tried), `<service>: search not passed` (the last fallback only). In-app landing for every service is UNVERIFIED until
  the phone (P3; addendum §8); `<queries>` is not needed (the shell holds `QUERY_ALL_PACKAGES`). A DEBUG-only table row
  for `testapps/qa-tunes` carries a search form (precedent `ServicesTable.kt:53-55,104-112`).

### Approximations (each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Live now-playing form: live bar, "LIVE" caption, omitted end-of-track entry | P4 design, no source | R8's screen with the scrubber and labels replaced as described | H1 |
| Y2 | Radio pivot rows and the favourites / browse arrangement (incl. logos only on favourites and the playing station) | P4 design, no source | app-list rows (R3 C2 / R6 §5.1.4); MusicMetrics for the header | H2 |
| Y3 | Metered-data line, wording and placement | P4 design | one caption line under the metadata block | H1 |
| Y4 | Reconnect window and back-off (60 s; 2/4/8/16/30 s) | agent pick | as stated | H5 |
| Y5 | Catalogue and "Listen on" pages (Q1 C), and the entry order | P4 design: phase 17's Y8 / Y9 forms (R11 measured no Browse / Store half — T20-7, T20-14) | phase 17's Y8 / Y9 forms in the Music idiom | H3 |
| Y6 | Tess reply wording for stations ("Playing <station>.") | phase 03's reply form | as stated | H5 |
| Y7 | The Jellyfin music view: its entry point, albums / artists / songs grouping, the server-name line (Q1 C) | P4 design, no source | the four built pivots' rows (R3 C2 / R6 §5.1.4) under a server header | H4 |

H-rows renumbered 2026-10-07 by r3 V1 (five rows, all *accept*): Y1 + Y3 → H1, Y2 → H2, Y5 → H3, Y7 → H4, Y4 + Y6 → H5.

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3) and are not asked.

1. ~~Q1 — streaming~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q1 — what "streaming" means for Music.** Phase 17 Q3b ruled Movies & TV a hub: catalogue, "Watch on <service>" hand-off,
   media server. Music's streaming side can take the same shape or less. Nothing here plays a streaming service's audio
   inside the shell (Out).
   A. Hand-off only: a "streaming" entry listing the music apps on the phone as shortcuts; their now-playing already lands on
      their own tiles (phase 10 Q4). No catalogue, no search.
   B. Phase 17's pattern for music: search an artist / album / track in a public music database (info and artwork), then
      "Listen on <service>" opens the installed service's app at that title, the user signed in there.
   C. B plus the user's own media server's music (Jellyfin or Plex, phase 17's connection) browsed as albums / artists / songs
      and PLAYED in the shell's own player — the one form where streamed music sounds through `MusicService`, with the
      equaliser, sleep timer, crossfade and the Music tile. (lean — the shape Q3b ruled for Movies & TV, one pattern reused;
      P2 fewer seams)
   D. Other / let me clarify.
2. ~~Q2 — Radio~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q2 — what Radio is now.** Groove's Radio was Microsoft's own artist-based streaming service and is gone with Groove; the
   word has to mean something the shell can actually do.
   A. Internet radio: stations from an open directory (radio-browser.info), browsed by genre / country / search, favourites,
      played live in the shell's own player. (lean — the only form that is Radio with no account, works with a cached list
      offline, and needs nothing Google; P5 / P6)
   B. A streaming service's radio by hand-off: "Start <artist> radio on <service>" — the service's own app plays its artist
      radio or mix; the shell keeps no directory and plays nothing itself.
   C. The media server's radio: Jellyfin's instant mix / Plex's station feature, played in the shell's player — only with Q1 C.
   D. Other / let me clarify.
3. ~~Q3 — where Radio lives~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q3 — where Radio lives and what plays it.** Groove kept Radio inside Music; W10M also shipped a separate FM Radio app on
   Lumias with a receiver (the S25 Ultra exposes none).
   A. A fifth pivot in Music, "radio", beside albums / artists / songs / playlists, played by `MusicService` — Groove's
      arrangement, and phase 10 Q1's own name for it. (lean — one app, one player, one tile; P2)
   B. Its own app in the app list, "Radio", with its own tile and App Shortcuts, still sounding through `MusicService` (so
      its face lands on the Radio tile under Q4's owner rule only if the routing treats it as its own app — a phase 15 task 0
      extension).
   C. Neither plays it: a RADIO slot chosen like the Music slot, handed to an installed radio app; the shell keeps the
      directory and favourites and opens the station there.
   D. Other / let me clarify.
4. ~~Q4 — offline and data~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q4 — offline and data.** A11 as amended prefers offline; a station is a continuous download.
   A. The directory and favourites are cached and browsable offline; a station plays on any network, with a "Streaming over
      mobile data" line when the network is metered; no new setting. (lean — P6, and the person pressed play)
   B. A plus a "Stream only on Wi-Fi" setting in the Music app, default off; on mobile data a station tap asks once.
   C. Wi-Fi only, always: on mobile data stations are listed but not playable.
   D. Other / let me clarify.
5. ~~Q5 — Tess~~ RULED 2026-09-23: A, for any installed music app (see Decisions). Original question kept below.
   **Q5 — Tess.** Phase 03 ruled "play music" and "play <song / artist / playlist>". Radio and streaming phrases are new.
   A. "play <station>", "play <genre> radio" and "play radio" (the last favourite), resolved by code against the cached
      directory (P6), spoken through the same session path phase 03 uses over the keyguard; plus "listen to <x> on
      <service>" as a hand-off when Q1 rules a catalogue. (lean — the ruled path already exists; P2)
   B. Station phrases only; no hand-off phrases (a hand-off opens another app, which PQ3 gates over the keyguard anyway).
   C. No new Tess phrases: the ruled list stands; Radio and streaming are tap-only.
   D. Other / let me clarify.

## Build tasks
Ordered so the part that touches phase 10's shipped player (the wake mode, the session callback) lands first and is
re-verified early. Every task is built in the form the interview ruled (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A); nothing is
conditional on Q-D any more (A is built, phase 17). Rewritten 2026-10-07 by r3 (the triage's "where it lands" column; the
2026-09-23 task text is in git, commit d6602757 and earlier). **Every task that owns a pure rule names it and says
"JVM-tested": the build session writes that test WITH the task**, with the cases in Acceptance's unit-test table; the
names here, in Decisions and in that table are the same names. No question is open (Q-20-1 answered 2026-10-07).
Order of work under the owner's rule (2026-10-07): build all twelve, push, then ONE round of testing (Acceptance).

1. **Build-start checks — only what still needs the device** (r11/music-radio-addendum-2026-10-07.md §8); each result is
   recorded in Decisions. (a) The Jellyfin 12.1 fixture answers `/Audio/<id>/stream?static=true` with 200 and no token
   (401 → the fallback written in Decisions "credentials", and that is recorded). (b) The `cmd netpolicy set
   metered-network <id> true` id form on the AVD (A3). (c) What `MusicFeed` shows for an item whose `title` is null (the
   wrapper of Decisions "live metadata" must cover it before A2). (d) Whether Auxio on the baseline declares
   `CATEGORY_APP_MUSIC` (r3 V3: A6 never asserts an empty "Listen on" list either way). Left to the rows, not checked
   here: playing a network item from Tess over the keyguard (A5 d); Data Saver, a real station's ICY title encoding,
   whether installed Pandora resolves `pandorav8://search/…/all`, which services answer `MEDIA_PLAY_FROM_SEARCH` (P1, P3).
   **Everything else the 2026-09-23 task deferred is now recorded fact:** Media3 1.9.0's ICY mapping (addendum §4);
   radio-browser's terms, mirror etiquette, User-Agent rule and click call (§1, §7.4, §7.5); MusicBrainz's and the Cover
   Art Archive's terms, rate limit and redirect host (§2, §7.15); Pandora's search form (§3, §7.14); Android's
   `MEDIA_PLAY_FROM_SEARCH` contract (§3, §7.13); the HLS share and the module's size (§1, §4, §7.6 — it is added); the
   directory caps (4 MiB a response, 32 MiB the cache file) and the file names `radio_directory_v1.json` /
   `radio_favourites.json` (Decisions "the station directory"); the fixture prefs (T20-6, `QaBases`).
2. **The shared player's wake mode** (ADD to phase 10's part, Change Log): `setWakeMode(C.WAKE_MODE_NETWORK)` on the
   `ExoPlayer.Builder` in `MusicService.onCreate`, and nothing else — `WAKE_LOCK` is already in the manifest
   (`AndroidManifest.xml:78`). No pure rule. Proved by A4 (a station and a local MP3 through 60 s of screen-off), which
   also closes phase 10's owed screen-off half.
3. **Station playback** (Q2 A, Q3 A; r3 D1, D2, D8, D9, D10, D11, D12, D14, D15). ADDs to phase 10's and phase 18's parts
   (Change Log). In this order:
   - **The item (trust, D1 / D9 / D10 / D11):** `music/radio/StationItem.kt` is the ONLY place a station `MediaItem` is
     built — media id `station:<uuid>`, the URI from `StationUrl.playable(urlResolved, url, hls)` after
     `StationUrl.accept(url, qaHost)` accepts it, `setMimeType(APPLICATION_M3U8)` for `hls=1`, `albumTitle` and `artist` =
     the station name through `RadioText.shown`, never `title`, never `artworkUri`, no `durationMs`. The `MusicItemRule`
     cases for `station:` / `server:` ids and the rewritten `UriAccessWiringScanTest` clauses land in the same commit.
     JVM-tested: `StationUrl.accept`, `StationUrl.playable`, `RadioText.shown`, `MusicItemRule`, `UriAccessWiringScanTest`.
   - **The play path (D1 / D15):** `MusicPlayer.playStations` sets a station queue through the shell's own controller;
     the queue is `RadioFavourites.queueFor(station, favourites)` and always replaces the queue; the click call
     (`GET /json/url/<uuid>`) goes out on each user-initiated start, never on a reconnect. No resumption after process
     death. JVM-tested: `RadioFavourites.queueFor`.
   - **Live metadata (D9):** the `KnownDurationPlayer` `getMediaMetadata()` override and `MusicPlayer.readSession`'s
     `albumTitle` fallback. JVM-tested: `LiveMetadata.merge`.
   - **Logos (trust, D2):** fetched by the shell for favourites and the playing station only, set as `artworkData`.
     JVM-tested: `StationLogo.accept`, `StationLogo.sampleSize`.
   - **Reconnect (D8):** the one clock from the first load error, the re-prepare schedule, the service's own partial wake
     lock for the window, the `X_STREAM_STATE` session extra carrying "Reconnecting…" / "This station isn't answering".
     JVM-tested: `StreamRetry.next`.
   - **Network states (D14, Q4 A):** the metered line, the captive-portal refusal, the no-network answer; server tracks
     exempt. JVM-tested: `StreamGate.decide`.
   - **Lines:** every `[music] stream:` / `sleep:` line comes from a pure builder (`StreamLine.*`), every URL through
     `StreamLine.url` (`ServerRules.withoutQuery` plus userinfo removed — T20-13). JVM-tested: `StreamLine`.
   - **Sleep timer:** the minute choices work as built; an armed end-of-track is cleared when a station queue replaces a
     track queue (`[music] sleep: end-of-track cleared (live item)`). JVM-tested: `MusicLive.clearsEndOfTrack`.
   Redirects need no code (Media3's default is kept, D12) beyond the `stream: redirect refused` line. The plain-http
   policy needs no code (phase 17's config, Q-D: A).
4. **The live now-playing form** (Y1, P4; r3 D10): `media3-exoplayer-hls` is added to `app/build.gradle.kts` (beside
   `:119-122`). In `NowPlayingPage`, when the session's item is live — `MusicLive.isLive(mediaId)`, the mark alone, never
   the duration — the scrubber draws as the live bar with no `pointerInput`, the labels become "LIVE", and `moreEntries`
   omits `SleepTimer.Choice.END_OF_TRACK`; a local track keeps every measured value. `CrossfadeFader.considerPreparing`
   returns for a live item. Tags: `nowplaying_live`, `nowplaying_live_caption`, `nowplaying_metered`. JVM-tested:
   `MusicLive.isLive`, `moreEntries` (the live case), the crossfade rule's live case (beside the existing crossfade unit
   tests).
5. **The directory** (Q2 A; r3 D5 — Q-20-1 answered 2026-10-07: the WHOLE directory is cached, no offline promise): the paged directory fetch, the tags and countries fetches (no online search call) exactly as Decisions "the station directory" states them, through `VideoHttp.get` with the
   `Tessera/<version> (…)` User-Agent; the mirror order; parse to the 11 slim fields with the 4 MiB response cap (`[music]
   radio: directory too large`) and the 32 MiB cache cap; the cache (`radio_directory_v1.json`, pages appended to a temp file, renamed when complete) and the 7-day (unmetered) /
   on-demand refresh (`[music] radio: directory fetched <n> stations`, `directory offline, cache from <date>`); search /
   genre / country over the whole cache plus favourites, by popularity, 100 at a time (53,000 rows: an in-memory index built off the main thread); the favourites store
   (`radio_favourites.json`, ≤ 200, the last-played stamp for "play radio", refreshed by uuid); the offline first-open
   state (`[music] radio: no cache yet (offline)`); the three `QaBases` constants (D16); and the process-start line
   `[music] radio: fm feature=<bool>` (`hasSystemFeature("android.hardware.broadcastradio")` — the lead's call for the Q3
   negative). The platform part stays thin. JVM-tested: `RadioMirrors.order`, `RadioDirectory.request`,
   `RadioDirectory.parse`, `RadioDirectory.search` / `.byTag` / `.byCountry`, `RadioFavourites` (add / remove /
   `lastPlayed`), `MusicNet.userAgent`, `QaBases` (the three new prefs).
6. **Radio in the app** (Q3 A): the fifth pivot (`MusicPivot.RADIO`, `MusicCollection.page`, rows, the hold menu with add to /
   remove from favourites only — T20-4); favourites first, then search / by genre / by country (Y2); browse rows draw the
   logo placeholder (D2); the offline and empty states. Tags: `radio_row:`, `radio_fav:`. JVM-tested:
   `MusicCollection.page` (the `RADIO` case, as the four built pivots are). No App Shortcut is declared (T20-3). ~~and the "Radio"
   App Shortcut (ADD to phase 11's part); B — a launcher activity …; C — a RADIO slot …~~ SUPERSEDED 2026-09-23 by T20-1 /
   T20-3 (Q3 ruled A).
7. **Tess** (Q5 A; r3 D1, D6, D7; ADDs to phase 03's part, Change Log). Station phrases need NO `CommandMatcher` ADD:
   "play <rest>" is already `PlayMusic(rest)` (`CommandMatcher.kt:91-93`) and `LockGate` already lets it through over the
   keyguard. (a) `MusicSearch.resolve(query, library, stations)` (`music/MusicSearch.kt`) with `Match`'s station form and
   the precedence rules (1)–(3) of Decisions "one resolver" — the ONE resolver. (b) `ActionLayer.playMusic`
   (`ActionLayer.kt:584-594`) calls it with the favourites and the cached directory, replies "Playing <station>." for a
   station, and starts a station match through `MusicPlayer.playStations` when no controller exists. (c) The session's
   search branch in `onSetMediaItems` (`MusicService.kt:180-201`) calls it with `RadioSearchRule.stationsFor(controller.uid,
   myUid)` — a stranger's search stays library-only — and starts a station match through the station builder; a hit logs
   `[music] search "<q>": station <name>` beside J5's `: <kind> <label>, n track(s)` (`:194`), a miss keeps J5's `: nothing
   in the library` (`:191`) and J5's miss reply. (d) `Request.ListenOn(query, app)`: the `CommandMatcher` rule for `listen
   to <x> on <app>` (last ' on ' splits; before the `play` rule), `LockGate.allowedWhileLocked` = false, the app resolved
   with `MusicServicesTable.byLabel`, then task 8's hand-off; no match → "I couldn't find <app> on this phone." No LLM in
   the path (P6); Tess is typed in the device rows (no utterance ids). JVM-tested: `MusicSearch.resolve`,
   `RadioSearchRule.stationsFor`, `MusicQueueStart.search` (a station queue starts at the match), `CommandMatcher` (the
   `listen to … on …` rule, without stealing "play …"), `LockGate.allowedWhileLocked` (`ListenOn` false, `PlayMusic`
   still true), `MusicServicesTable.byLabel`.
8. **Streaming side** (Q1 C; r3 D3, D4, D13, D17; ADDs to phase 17's part, Change Log).
   - **Catalogue:** the search page and the title page in the Music app — MusicBrainz `recording` search, `limit=25`,
     submit-only, one request in flight, ≥ 1.0 s between request starts, a 503 never retried by itself; art
     `releases[0].id` at `front-250`, its redirects followed by hand; the User-Agent; the `[music] catalogue …` lines.
     Tags: `catalogue_row:`. JVM-tested: `MusicCatalogue.searchUrl` / `.parse` / `.coverUrl`, `RateSpacing.waitMs`,
     `CoverArt.mayFollow`.
   - **Hand-off:** `MusicServicesTable` (the seven recorded services, Tidal with no link, the DEBUG-only QA Tunes row),
     `HandoffPlan.PlayFromSearch`, `StreamingHandoff.openPlans(…, tag="music")`, the discovery (never the shell itself);
     each plan tried in order, `did not open at the title` logged when one fails, the plain open last. Tags:
     `handoff_service:`. JVM-tested: `MusicServicesTable.plan`, `MusicServicesTable.entries`.
   - **The home server:** `ServerRules.musicPath` / `parseTracks` / `audioStreamUrl`, `MediaServer.music()` and its `tag`
     argument; the library listed as albums / artists / songs in the Music idiom (Y7, H4); tracks built ONLY in
     `music/server/ServerTrackItem.kt` (media id `server:<id>`, a known `durationMs`, so the built now-playing form
     applies, not the live one; crossfade and end-of-track work) and played direct (`?static=true`, no token, no
     transcoding); `[music] server …` lines in the main ring (T20-12 / T20-13). JVM-tested: `ServerRules.musicPath`,
     `ServerRules.parseTracks`, `ServerRules.audioStreamUrl`, `ServerRules.mayCarryToken` (the `/Audio/` cases).
9. ~~**Settings + checklist** *(Q4 B)*: the "Stream only on Wi-Fi" setting …~~ SUPERSEDED 2026-09-23 by T20-1: Q4 ruled A — no
   setting and no permission row (Decisions).
10. **Regressions and static checks** (r3 V1, D15). `qa/phase-03/scripts/j5.sh` (J5's 8/8, inside A5); phase 17's
    `FixedEndpointsTest` and the whole existing JVM suite green with this phase's tests added; the three static checks of
    Acceptance (the APK budget, the exported allow-list — exactly the allow-list, no ADD — and the release config holding
    no `10.0.2.2`). The 2026-09-23 re-runs of phase 10's MUSIC6 / MUSIC7 / MUSIC17 rows and of its tile rows are dropped
    (Acceptance, "Dropped, and why"): MUSIC17's "a station in the queue never fades" clause is struck with them, because
    no mixed queue can exist (D15); the tile's face and the unchanged neighbour tiles are read once, in A2.
11. **QA fixtures and harness** (r3 V4; three servers, one stub).
    - `qa/phase-20/scripts/radio_fixture.py` at `10.0.2.2:8080` — directory and stream in one. The real API shape
      (`/json/stations` with `order` / `limit` / `offset` — the fixture serves its stations two to a page, so paging is exercised, `/json/stations/byuuid`,
      `/json/tags`, `/json/countries`, `/json/url/<uuid>`), four stations — QA Jazz One (ICY, a StreamTitle that switches
      from "QA Song 1" to "QA Song 2" 20 s after each connection opens; the higher `clickcount` of the two jazz rows), QA
      Jazz Two, QA News One, QA File (`url_resolved` = `file:///sdcard/Music/x.mp3`) — a looping MP3 made as MUSIC6's
      fixtures were, and a request log with every request's headers.
    - `qa/phase-20/scripts/catalogue_server.py` at `10.0.2.2:8081` — one search answer ("qa artist": QA Song A / B / C by
      QA Artist), two PNGs for the cover art, a request log with headers.
    - Phase 17's Jellyfin container, plus a Music folder ADD to `jellyfin_fixture.sh up` (a Music `VirtualFolders` call and
      two generated MP3s of known length; an ADD to phase 17's fixture — nothing of the server's is kept in the repo).
    - `testapps/qa-tunes` — declares `CATEGORY_APP_MUSIC`, logs the intent it receives to the `TileShellQa` logcat tag,
      and has the DEBUG-only `MusicServicesTable` row carrying a search form.
    - Harness: `qa/phase-20/scripts/lib.sh` → symlink to `qa/phase-03/scripts/lib.sh`, plus
      `qa/phase-01/scripts/music_lib.sh`; `qa/phase-20/baseline_layout.json` derived from
      `qa/phase-18/baseline_layout.json` with the MUSIC / PHOTOS / CAMERA slots, Auxio pinned and `manualSizes` for every
      tile, the previous file kept as `qa/phase-20/baseline_layout-pre-20.json` (C-3's form; this phase adds no marker, it
      pins a fixture — T20-6). Tess is typed (`type_request`); the token is read with `jellyfin_fixture.sh token-of`.
    - Not built (r3 V1 / V4; the reasons are in Acceptance, "Fixtures"): a captive-portal server, playlist and
      `content://` stations, an over-the-cap directory, AAC / stopping / codec-switching / HLS streams, a 500 answer, a
      Pandora stand-in, a plain stub, a tone-playing stub, spoken-utterance ids, and the outbound-traffic guard.
12. **The adversarial review (C-16 gate), ONE pass, one fix round** (team-review, adversarial mode; Opus), recorded under
    qa/phase-20/ before `done`. The trust surface it reads:
    - D1 — the `UriAccessWiringScanTest` diff (the rewritten `setItemsForm` and `.setUri(` clauses, the new
      two-builders clause), and the stranger-controller rules: `MusicItemRule`'s `station:` / `server:` cases and
      `RadioSearchRule.stationsFor`;
    - D2 — logos: never `artworkUri`, the shell's own capped fetch and bounded decode (`StationLogo`);
    - D9 — `RadioText.shown` on every directory string and StreamTitle before the tile, the notification, a line or a
      reply;
    - D11 / D12 — `StationUrl.accept` (schemes; empty, loopback, link-local, private and unspecified hosts; the stated
      gap that names resolving privately are not checked), `StationUrl.playable` (no playlist is fetched), and the kept
      Media3 default that refuses cross-protocol redirects;
    - D4 — token scoping: no token on `/Audio/<id>/stream`, nothing on the shared player, and — if the 401 fallback was
      taken — `mayCarryToken` with the widened `STREAM_PATH`;
    - D13 — `CoverArt.mayFollow` and the by-hand redirect loop (https, `archive.org` and its subdomains, ≤ 3, no header);
    - the hand-off intent's contents (`HandoffPlan.PlayFromSearch`: the query and the focus extra, the package set, no
      data URI; the search links' encoding of a title and an artist);
    - the debug-only `QaBases` overrides (`RADIO`, `MUSIC_CATALOGUE`, `COVERART`): null in a release build, and the one
      private host `StationUrl.accept` lets through only when the override is set.

## Acceptance criteria
**The owner's rule: "Build, push, then ONE round of testing (Jeremy, 2026-10-07)."** All twelve build tasks are built with
their JVM tests, the build is pushed, and then every row below runs ONCE on the pushed build. A failing row is fixed and
that row alone is re-run; nothing else runs a second time. This section was re-cut whole on 2026-10-07 by r3 V1 (the lead's
calls in review/2026-10-07-phase20-r3-triage.md win where they differ from the reviewer's wording); the 2026-09-23 rows are
in git, commit d6602757 and earlier.

**Preamble.** Rows run ONCE on the pushed debug APK on `emulator-5554` (the AOSP AVD tileshell_fhd, 1080×2340 @ 450 dpi,
API 36, no Google; no streaming app installed — real services are phone rows). Start: `layout_restore
qa/phase-20/baseline_layout.json` (derived from `qa/phase-18/baseline_layout.json`), then `ensure_start`. After any launch
of another app: `am force-stop app.tileshell` + Home (C-6), then re-select the keyboard (`p18.sh` `baseline_start`). Ring
reads use `ring_mark` / `ring_since`; absence uses `absent_in` (`p18.sh`) on a slice proven readable. Fixture bases are the
debug prefs (`qa_radio_base` = `http://10.0.2.2:8080/`, `qa_music_catalogue_base` = `http://10.0.2.2:8081/ws/2/`,
`qa_coverart_base` = `http://10.0.2.2:8081/coverart/`, written with `qa/phase-01/scripts/prefs_edit.py`). Tess is typed with
`type_request` (`lib.sh:338`), over the keyguard as `qa/phase-17/scripts/e10.sh` does it; nothing is spoken and the host's
audio is never touched. No outbound-traffic guard. The one debug APK is assembled with `-Ptmdb.readToken=qa-dummy-token`
(C-32) and every row stamps it (`apk_matches`, `lib.sh:380`). Audio is asserted the way MUSIC17 asserted it (the player's
AudioFlinger track, `music17.sh`'s awk). "Diagnostics" is the launcher ring; `[music]` lines are the ones asserted.
Evidence is never committed.

### Device rows (A1–A7; the review's R1–R7)

**A1 — Browse radio, favourite a station, come back.**
1. Fresh data and prefs (`pm clear app.tileshell` → `qa/phase-03/scripts/provision.sh` → Home, the fixture prefs written).
2. Open Music; go to radio.
3. Search "jazz".
4. Hold QA Jazz One → add to favourites.
5. Force-stop; reopen radio (the network stays on — Q-20-1: no offline leg).

Pass:
- (a) five `music_pivot_header:*` nodes with radio last, its bounds inside 0–1080;
- (b) the fixture stations are listed (`radio_row:<id>`), `[music] radio: directory fetched 4 stations` is in the slice,
  the fixture's log shows the pages asked in order until the short page, and the search shows only the two jazz rows, each once;
- (c) the hold menu has add to favourites and no pin entry;
- (d) after the reopen QA Jazz One is first under favourites (`radio_fav:<id>`) and the stations are listed;
- (e) host side: every fixture request carries `User-Agent: Tessera/`.

**A2 — Play a station.**
1. Tap QA Jazz One; wait 30 s.
2. Open `•••` → sleep; Back; Home.
3. Pause from the Music tile.
4. Back in radio, tap QA File.

Pass:
- (a) session PLAYING, album "QA Jazz One", title "QA Song 1" then "QA Song 2" (read once at 30 s, no ± timing), and
  `[music] stream: connected http://10.0.2.2:8080/… codec=mp3`;
- (b) `nowplaying_live_caption` = "LIVE", `nowplaying_scrubber` and `nowplaying_total` absent, `music_menu_sleep:eot`
  absent;
- (c) the Music tile shows "QA Song 2", `tile_control:<id>:PAUSE` pauses it, and the Photos and Camera tile bounds are
  unchanged;
- (d) the fixture logged exactly one `GET /json/url/<uuid>` (the click call);
- (e) QA File: "can't play this station", the session's metadata unchanged, `[music] stream: unsupported scheme=file`.

**A3 — The network changes mid-station.**
1. Station playing; `cmd netpolicy set metered-network <id> true` (the id form from build task 1).
2. Airplane mode on; wait 20 s; airplane mode off.
3. Restore the netpolicy setting.

Pass:
- (a) `nowplaying_metered` reads "Streaming over mobile data" and it still plays;
- (b) `nowplaying_track` reads "Reconnecting…" and `[music] stream: lost, retrying` is in the slice;
- (c) playing again with no tap, and `[music] stream: reconnected after` is in the slice.

**A4 — Lock the screen.**
1. Station playing; `KEYCODE_SLEEP`; wait 60 s; `wake_device`.
2. The same with a local MP3 (`music_fixtures`).

Pass:
- (a) the player's AudioFlinger track is active at 60 s both times;
- (b) for the local track `nowplaying_scrubber` and `nowplaying_total` are back.

**A5 — Ask Tess (typed).**
1. `type_request` "play jazz radio".
2. Favourite QA News One, play it, stop; `type_request` "play radio".
3. `type_request` "play zzqx radio".
4. Set a PIN; sleep / wake (`e10.sh`'s K-leg); `type_request` "play jazz radio"; clear the PIN.
5. `qa/phase-03/scripts/j5.sh`.

Pass:
- (a) reply "Playing QA Jazz One." and that station playing, `[music] search "jazz radio": station QA Jazz One`;
- (b) "play radio" → QA News One (the favourite played most recently);
- (c) the miss reply ("I couldn't find zzqx radio in your music."), the session unchanged;
- (d) over the keyguard it plays with `isKeyguardShowing=true` (the addendum lists this foreground-service start as
  unverified — it must stay on the device);
- (e) `j5.sh` 8/8.

**A6 — Find a song and listen elsewhere.**
1. In Music's catalogue, search "qa artist"; open QA Song A.
2. Install `testapps/qa-tunes`; redraw the title page.
3. Tap "Listen on QA Tunes"; then `am force-stop app.tileshell` + Home (C-6).
4. `type_request` "listen to qa artist on qa tunes".
5. Uninstall the stub.

Pass:
- (a) three `catalogue_row:` rows with the three titles and their artwork drawn; the catalogue fixture's log shows
  `User-Agent: Tessera/` on every request;
- (b) no QA Tunes entry (`handoff_service:`) before the install, one after — whatever else is listed (Auxio, a plain
  open) is not asserted, and the list is never asserted empty;
- (c) the stub is resumed and its `TileShellQa` line shows the URI with the title and the artist; `[music] handoff:
  qa-tunes "QA Song A" ->` is in the slice; the shell's session is unchanged;
- (d) the typed phrase opens the same stub (the phone is unlocked; the locked answer is the JVM's,
  `LockGate.allowedWhileLocked`).

**A7 — Play from the home server.**
1. `jellyfin_fixture.sh up` with the Music folder.
2. Sign in (qa / qa-password) at Music's server entry.
3. Open albums / artists / songs; tap a song.
4. Tap QA Jazz One in radio.

Pass:
- (a) `[music] server 10.0.2.2:8096: connected`, and the three groupings listed;
- (b) the song plays with `nowplaying_total` = the file's length ± 1 s and a normal scrubber, the line `[music] stream:
  connected http://10.0.2.2:8096/Audio/<id>/stream codec=mp3` holding no `?`;
- (c) `leak_scan.sh --logcat --path qa/phase-20 -- "$(jellyfin_fixture.sh token-of)" qa-password` exits 0;
- (d) the radio fixture's log shows no `Authorization` header and no `ApiKey` on any request.

### Static checks (one command each, not rows)
- The APK is ≤ 629,145,600 B (`stat -c%s`).
- The exported components equal `qa/phase-03/exported-allowlist.txt` exactly (no ADD).
- `aapt2 dump xmltree` of the release APK's network security config holds no `10.0.2.2` (`./gradlew :app:assembleRelease
  -Ptmdb.readToken=`).

### GATE (unchanged, C-16)
One adversarial Opus pass, one fix round, over the station path — build task 12 lists the surface. Recorded under
qa/phase-20/ before `done`.

### Fixtures
**Survive (three servers, one stub; build task 11):**
- `radio_fixture.py` at :8080 — directory and stream in one. The real API shape (`limit` / `offset`, `/json/tags`,
  `/json/countries`, `/json/url/<uuid>`), four stations (QA Jazz One with ICY and a StreamTitle switch at 20 s, QA Jazz
  Two, QA News One, QA File), a request log with headers.
- `catalogue_server.py` at :8081 — one search answer, two PNGs.
- Phase 17's Jellyfin container, plus a Music folder ADD to `jellyfin_fixture.sh up` with two generated MP3s.
- `testapps/qa-tunes` — `CATEGORY_APP_MUSIC`, logs its intent, with a DEBUG-only table row carrying a search form
  (precedent `ServicesTable.kt:53-55,104-112`).

**Gone:** the captive-portal server; the playlist stations and the `content://` station; the over-the-cap directory; the
AAC, stopping, codec-switching and HLS streams; the 500 answer; the Pandora stand-in package; the plain stub; the
tone-playing stub; the spoken-utterance ids; the outbound-traffic guard.

### Unit tests (JVM) — carried by the build tasks
Each rule is a pure function with a JVM test written by the build task named beside it. One name per rule; the same
names are used in Decisions and Build tasks.

| rule (pure function) | cases | build task |
|---|---|---|
| `StationUrl.accept(url, qaHost)` | http and https accepted; file, content, asset, rawresource, data, empty, and mixed case such as `FILE:` refused; an empty host, `localhost`, IP-literal loopback / link-local / RFC 1918 / unspecified refused; the `qaHost` exception only when it is non-null | 3 |
| `StationUrl.playable(urlResolved, url, hls)` | `url_resolved` used; `url` when that is empty; both empty → refused; `hls=1` → the m3u8 MIME type; a path ending `.pls` / `.m3u` / `.asx` → `unsupported playlist` | 3 |
| `RadioText.shown(raw, max)` | control characters, newlines, bidi / format characters removed; a hostile name; names cut at 80, a StreamTitle at 120; empty → empty | 3 |
| `MusicItemRule` (the `station:` / `server:` cases) | from the shell's uid the item is kept; from a stranger it is dropped (never a library id); a stranger's URI item is still rebuilt or dropped as built | 3 |
| `UriAccessWiringScanTest` (rewritten clauses) | the `setItemsForm` and `.setUri(` clauses as rewritten; `StationItem.kt` and `ServerTrackItem.kt` are the only other `.setUri(` under `music/` | 3 |
| `RadioFavourites.queueFor(station, favourites)` | a favourite → the favourites in order, starting at it; a non-favourite → that station alone | 3 |
| `LiveMetadata.merge(stationName, icyTitle)` | a null or blank title → the station name; a StreamTitle → the title, `albumTitle` kept; an HLS item (never a StreamTitle) | 3 |
| `StationLogo.accept(bytes)` / `.sampleSize(w, h)` | over 512 KiB refused; decoded bounds ≤ 512 px; a non-http(s) logo URL never fetched | 3 |
| `StreamRetry.next(sinceLostMs, attempt)` | 2 / 4 / 8 / 16 / 30 s; gives up at 60 000 ms; `lost` is written on the first load error, once | 3 |
| `StreamGate.decide(hasNetwork, captive, metered, dataSaver, isStation)` | no network → the no-network answer at once; captive → refuse with "Sign in to this Wi-Fi network first"; not validated but not captive → play; metered → play with the line; Data Saver → play with its line; a server track → exempt from the captive and no-network refusals | 3 |
| `StreamLine` (every `[music] stream:` / `sleep:` line builder) and `StreamLine.url(u)` | each line's exact text; the query and userinfo stripped from every URL | 3 |
| `MusicLive.clearsEndOfTrack(armed, mediaId)` | armed + a `station:` id → cleared, the line written; not armed, or a track → nothing | 3 |
| `MusicLive.isLive(mediaId)` | `station:` → live whatever the duration (HLS reports one); `server:` and a library id → not live | 4 |
| `moreEntries` (the live case) | end-of-track omitted when live, present for a track; the minute choices in both | 4 |
| the crossfade rule's live case (`CrossfadeFader.considerPreparing`) | a live item never prepares a fade; a server track with a known length does | 4 |
| `RadioMirrors.order(names, seed)` | one entry, several, empty → `all.api.radio-browser.info`; names outside `.api.radio-browser.info` dropped; deduped; the next on failure | 5 |
| `RadioDirectory.request(what)` | the page URL for an offset (`order=clickcount`, `reverse=true`, `limit=2000`, `hidebroken=true`), stopping on a short page and at 40 pages; the tags and countries URLs; no search URL exists; the `byuuid` refresh; the click URL | 5 |
| `RadioDirectory.parse(bytes, cap)` | the 11 slim fields; over the byte cap → too large with the cache kept; video-codec rows dropped; an empty `url_resolved` kept for `StationUrl.playable`; hostile names through `RadioText.shown` | 5 |
| `RadioDirectory.search` / `.byTag` / `.byCountry` | name, genre, country over the whole cached set; favourites first; by popularity; 100 at a time | 5 |
| `RadioFavourites` (add / remove / `lastPlayed`) | add, remove, the ≤ 200 cap; the last-played stamp; most recently played, else the first, else none | 5 |
| `MusicNet.userAgent(version)` | `Tessera/<version> (…)`; never blank, never a library default | 5 |
| `QaBases` (`RADIO` / `MUSIC_CATALOGUE` / `COVERART`) | honoured only when DEBUG; a set radio override bypasses the mirror lookup and also carries the click call | 5 |
| `MusicCollection.page` (the `RADIO` case) | favourites first, then the browse rows; the empty and offline states | 6 |
| `MusicSearch.resolve(query, library, stations)` | rules (1)–(3): "radio" → the last-played favourite, else the first, else a library song titled "Radio", else the miss; "jazz radio" → the genre's highest-clickcount station; a favourite's exact name beats the library; "bloom" stays a library song although a cached station contains it; the cached directory exact name comes last without "radio"; a miss | 7 |
| `RadioSearchRule.stationsFor(controllerUid, myUid)` | the shell's uid → the stations; any other uid → none (a stranger's search resolving to a station name gets the library-only answer) | 7 |
| `MusicQueueStart.search` (a station match) | a station queue of favourites starts at the match | 7 |
| `CommandMatcher` (`listen to <x> on <app>`) | the last ' on ' splits; "play …" is not stolen; "listen to <x>" with no app is not this request | 7 |
| `LockGate.allowedWhileLocked` | `ListenOn` → false; `PlayMusic` still true | 7 |
| `MusicServicesTable.byLabel(phrase, entries)` | normalised exact, then prefix; no match | 7 |
| `MusicServicesTable.plan(service, title, artist)` | Pandora: `pandorav8://search/<enc>/all`, then `https://www.pandora.com/search/<q>/all`, then `PlayFromSearch`, then the plain open; each other recorded service's link; Tidal and an unknown app → `PlayFromSearch` then the plain open; `PlayFromSearch` carries `SearchManager.QUERY` and `EXTRA_MEDIA_FOCUS`, the package, no data URI | 8 |
| `MusicServicesTable.entries(installed, musicLaunchers, self)` | the installed table rows ∪ the `CATEGORY_APP_MUSIC` launchers; `app.tileshell` excluded; a table package not listed twice; the QA Tunes row only when DEBUG | 8 |
| `MusicCatalogue.searchUrl` / `.parse` / `.coverUrl` | `recording`, `limit=25`, the query encoded; title / artist / release parsed; `/release/<mbid>/front-250` from `releases[0].id`; no release → the placeholder; the outcome lines incl. `error 503` (never retried) | 8 |
| `RateSpacing.waitMs(lastStartMs, nowMs)` | starts ≥ 1000 ms apart; five back-to-back submits; a clock that went backwards | 8 |
| `CoverArt.mayFollow(location)` | https `archive.org` and `*.archive.org` followed; http, another host, `archive.org.evil.example`, a fourth hop refused | 8 |
| `ServerRules.musicPath` / `.parseTracks` / `.audioStreamUrl` | the listing path (`includeItemTypes=Audio`); album, album artist, index and `RunTimeTicks/10_000` → `durationMs`; the stream URL with `static=true` and no token | 8 |
| `ServerRules.mayCarryToken` (the `/Audio/` cases) | as built, `/Audio/…/stream` carries none; under the 401 fallback, only the saved server's `/(Videos\|Audio)/…/stream` does and a radio host never | 8 |
| `FixedEndpointsTest` (phase 17's, as built) | `api.radio-browser.info`, `musicbrainz.org`, `coverartarchive.org`, `archive.org` are https-only | 10 |

### Dropped, and why (neither on the device nor in a JVM test)
SUPERSEDED-rows note, 2026-10-07 (r3 V1): the ids in this list are the 2026-09-23 cut's own (its emulator rows E1–E21,
phone rows P1–P11 and H-rows H1–H10; in git, commit d6602757 and earlier) and are named nowhere else in this doc.
- Old E1's header-strip geometry and `[motion]` numbers; the MUSIC6 / 7 / 17 re-runs — phase 10's rows; the change is one
  more header; H2 sees it.
- Old E6, the 15-minute sleep on a stream — the timer pauses the player whatever the item; `SleepTimerTest` exists.
- Old E7, the equaliser on a stream — the effect is bound to the audio session, not the item.
- Old E8's gave-up pass (120 s) and its ± 5 s clocks; old E9, a throttled network; old E11, a stream that stops or
  switches codec — the same reconnect code as A3; the schedule is on the JVM (`StreamRetry.next`).
- Old E10, a captive portal on the AVD — the decision is on the JVM (`StreamGate.decide`); P4 when one is at hand.
- Old E12, Data Saver — **the lead's call: phone only.** P1 includes one listen with Data Saver on; the AOSP rule is
  cited (Decisions "metered data"), not asserted on the AVD. Q4 A's "plays on any network" rests on that listen.
- Old E13's etiquette timing, offline, 500 and connect-error pages — the JVM rate rule and parse; the error texts go
  unasserted.
- Old E13b, the Pandora stand-in — a stub under another package is never targeted (`StreamingHandoff.kt:98-99` sets the
  service's package). **The lead's call:** the JVM plan test (`MusicServicesTable.plan`) carries the form; P3 on the
  phone proves the installed app, the only place it can be proven.
- Old E13b's stale tap; old E15, audio focus — focus handling is untouched; P2 covers a call.
- Old E14's crossfade between two server tracks — **the lead's call:** P1's listening on the phone plus the existing
  crossfade unit tests; no device row. Its wrong-password and `docker stop` halves, and the unauthorised / unreachable
  lines — phase 17's client, already gated there.
- Old E19, the coverage row; C-29's 0-packet guard — the overrides and the click base are JVM-tested (`QaBases`).
  Residual risk: a debug build touching the live directory once.
- Old E2's offline first open — an empty-state text; the JVM carries the state (`MusicCollection.page`).
- Old E3b, the three policy branches — one policy is built (Q-D: A); `FixedEndpointsTest` carries its three `false`
  lines and the release config is a static check.
- Old E18's `dumpsys shortcut` clause — nothing declares a shortcut.
- Old P11, no FM receiver by `adb` — it cannot be a phone row. **The lead's call:** one process-start line `[music]
  radio: fm feature=<bool>` (`hasSystemFeature("android.hardware.broadcastradio")`), read on the Diagnostics page in P1;
  the cited sources of Q3 stand beside it.
- Old H9 — a ruling restated, nothing to judge.
- A cross-protocol redirect (`stream: redirect refused`) — Media3's kept default; only the line's builder is tested.
- Edge: an APK update mid-stream, reboot liveness, the MUSIC slot re-pointed, a StreamTitle every second — unasserted.
  Phase 10's rules and `MusicFeed`'s throttle are unchanged.

### Phone-only (P1–P4; Jeremy does it and reads it there)
- **P1 Radio for real.** The CI build. Open radio; search jazz; play; lock the phone for 30 minutes on Wi-Fi, then on
  mobile data, once with Data Saver on; and, with the home server signed in and crossfade set, listen across the change
  between two server tracks. Report: still playing each time; the mobile-data line seen; the crossfade heard; from
  Settings > Diagnostics the `radio: directory fetched <n>` line (n ≥ 40,000), one `stream: connected http://…` line (an
  http station played on the release config) and the `radio: fm feature=<bool>` line; data used and battery from
  Android's own app-usage screens (H5).
- **P2 Buttons and calls.** Headset or Bluetooth pause / play; next / previous step the favourites; a call pauses and
  nothing resumes by itself.
- **P3 Listen on.** A title → Listen on Pandora, and each other installed app. Per app: landed on its search for the
  title, or just opened; the `[music] handoff:` line; a song playing there shows only on that app's pinned tile.
- **P4 Tess by voice**, unlocked and over the lock screen. A real captive portal folds in here "when one is at hand",
  recorded.

### NEEDS-HUMAN (H1–H5)
- **H1** *accept* — the live now-playing form plus the metered line (Y1, Y3).
- **H2** *accept* — the radio pivot (Y2), with logos on favourites and the playing
  station only.
- **H3** *accept* — the catalogue and Listen-on pages and the entry order (Y5).
- **H4** *accept* — the server music view (Y7).
- **H5** *accept* — recorded facts: the reconnect feel, Tess's wording, data / battery (Y4, Y6).

qa/phase-20/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.

### Diagnostics lines asserted on the device
`[music] radio: directory fetched <n>` (A1, P1); `stream: connected <url> codec=` (A2, A7); `stream: unsupported scheme=`
(A2); `stream: lost, retrying` / `reconnected after` (A3); `search "<q>": station <name>` (A5); `handoff: <svc> "<title>"
->` (A6, P3); `server <host>: connected` (A7). Every other `[music]` line is not asserted on the device: its builder is
JVM-tested.

## Edge cases
Kept from 2026-09-23; on 2026-10-07 (r3 V1, D11, D12, D14, D15) each bullet gained where it is now checked — `(JVM:
<rule>)`, `(A<n>)`, `(P<n>)` or `(unasserted — <reason>)`.
- Network off, flaky or captive when the app opens: the directory shows its cache with the offline line; with no cache yet, an
  empty state that names the cause ("No connection yet — stations will appear when there is one"), not a blank pivot; a
  station tap with no network shows the gave-up state at once, not after 60 s. A network that is only not validated is
  not "captive" (r3 D14): the station plays into the reconnect path. (JVM: `StreamGate.decide`, `MusicCollection.page`;
  no device row for the pivot with no network — Q-20-1, 2026-10-07; P4 for a real portal)
- A station that stops for good (server gone, 404, DNS failure) versus one that stops and returns: the one reconnect clock
  (Decisions "reconnect"); a 30x redirect to another host is followed when the scheme is the same and ~~(`DefaultHttpDataSource`
  cross-protocol redirects allowed, the final URL logged without its query string)~~ SUPERSEDED 2026-10-07 by r3 D12:
  refused when it crosses between https and http (`stream: redirect refused`, no retry); a
  playlist file (.pls / .m3u / .asx) in place of a stream — ~~resolved to its first http / https entry or~~ (SUPERSEDED
  2026-10-07 by r3 D11: nothing is fetched or parsed) refused with `stream: unsupported playlist` (T20-5). (JVM:
  `StreamRetry.next`, `StationUrl.playable`; A3 for the return; the redirect is unasserted — Media3's kept default, only
  the line's builder is tested)
- A station that changes codec mid-connection (rare; a progressive source cannot follow it): treated as a stop, the reconnect
  lands on the new codec. (unasserted — the same reconnect code as A3; JVM: `StreamRetry.next`)
- A station sending no ICY metadata, and every HLS station: the title line stays the station name (JVM:
  `LiveMetadata.merge`); one sending a StreamTitle every second: the tile republishes at most as `MusicFeed` already
  throttles position ticks (the 3-s rule in phase 01's Change Log), never a flip storm. (unasserted — `MusicFeed`'s
  throttle is unchanged)
- The sleep timer's end-of-track chosen for a local track, then ~~the queue reaching a station~~ a station queue replacing the
  track queue (SUPERSEDED 2026-10-07 by r3 D15: no queue mixes tracks and stations): the armed timer is cleared with
  `[music] sleep: end-of-track cleared (live item)`, because it can never fire. (JVM: `MusicLive.clearsEndOfTrack`)
- Crossfade set: ~~a station followed by a local track in the queue~~ (SUPERSEDED 2026-10-07 by r3 D15: that queue cannot be
  built) a station queue holds only stations and no fade is prepared out of or into a live item, whatever duration an HLS
  window reports; playing a local track afterwards replaces the queue and starts clean. (JVM: the crossfade rule's live
  case, `MusicLive.isLive`)
- The streaming app is not installed, or was uninstalled since the title page was drawn: the "Listen on" entry is gone on the
  next draw, and a stale tap shows "That app isn't installed any more" rather than a resolver error; the app is installed but
  signed out: its own sign-in shows, nothing of ours intervenes. (A6 b for the entry following the install; P3 for a
  signed-out app; the stale tap is unasserted — its row went with the stand-in stubs)
- A search link the service no longer answers (`ActivityNotFoundException` or the app opening on its home): logged as
  `handoff: <service> did not open at the title`, the entry stays (the app IS installed), H3 records it; the
  `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` form is tried second, then the plain open. (JVM: `MusicServicesTable.plan` for the
  order; P3 for what each real app does)
- Audio focus: the other app takes focus and never gives it back (phase 10's rule); a call during a station (pause; no
  resume by itself); headphones out mid-stream (becoming-noisy pause, as built). (P2; unasserted on the AVD — focus
  handling is untouched)
- Metered network appearing mid-stream (Wi-Fi drops to mobile data): the metered line appears without stopping the stream
  (Q4 A). ~~under Q4 B / C the stream stops at the switch with the line saying why~~ SUPERSEDED 2026-09-23 by T20-1. (JVM:
  `StreamGate.decide`; A3 a; P1 on real mobile data and with Data Saver)
- A phone with no local audio at all and no network: the four built pivots' empty states unchanged (phase 10's row); the
  radio pivot's offline empty state; the app never crashes on an empty everything. (JVM: `MusicCollection.page`;
  unasserted on the device — an empty-state text)
- The MUSIC slot re-pointed to another player: the shell's Music app keeps its radio and streaming pages and keeps working;
  Tess's "play jazz radio" then goes to the slot app's session (phase 03's rule) and, if that app cannot answer it, says so.
  (unasserted — phase 10's rules are unchanged)
- A media-server track whose server goes away mid-play (Q1 C): Media3's own load retries and then the player error — the
  station reconnect clock is for live items only (r3 D8) — then `[music] server <host>: unreachable`; the
  server library cached as the directory is, so browsing survives the outage. (unasserted — phase 17's client, already gated there)
- Plain http (Q-D: A, C-16): an http station plays. ~~under B / C it is listed but refused with `[music] stream:
  cleartext refused` and the page says "This station can't be played securely"; a station redirected from https to http is
  judged by its final URL under the same branch~~ SUPERSEDED 2026-10-07 by r3 D12 / D16: one policy is built, and a station
  redirected between https and http is refused (`stream: redirect refused`). (static check for the release config; JVM:
  `FixedEndpointsTest`; P1 for an http station on the release build)
- APK updated (`adb install -r`) while a station plays: the service stops cleanly, no orphan session (phase 17's rule).
  (unasserted — phase 10's rules are unchanged)
- Liveness (N-01): after `adb reboot` and Device care, favourites and the cache are intact; after a reboot or a process
  death nothing plays by itself and no station queue returns (phase 10: no timer read back off disk, nothing resumes; r3
  D1: resumption stays unanswered). ~~and the setting~~ SUPERSEDED 2026-09-23 by T20-1 (no setting, Q4 A). (unasserted —
  phase 10's rules are unchanged; JVM: `RadioFavourites` for the store)

## QA evidence
_None yet._
