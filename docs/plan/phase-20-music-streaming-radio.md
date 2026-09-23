---
phase: 20
slug: music-streaming-radio
status: DRAFT   # 2026-09-23; interview DONE 2026-09-23 (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A + Pandora); review triage round 2 applied 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md); Q-D: A (plain-http streams, with phase 17); ADDS to phase 10's FINAL part, never rebuilds it
depends-on: [03, 10, 12, 15, 17]   # C-23 / T20-7: 15 for its build task 0 (shell-session routing by tag), which "must be in first"; 12 for C-4's `pm clear` → `provision.sh` form and C-15; 17 for StreamingHandoff, the network security config (C-16) and the Jellyfin fixture; 11 is not needed (T20-3: no shortcut is declared)
---

# Phase 20 — Music streaming and Radio

## Goal
Phase 10's Music app (the built, FINAL Groove-style player) gains the two things its Q1 note said A11 had taken off the
table: a **streaming side** and **Radio**. When this phase is done, the Music app can reach music that is not on the phone in
the forms the interview ruled (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A): internet radio stations from radio-browser.info in a fifth
"radio" pivot, played in the shell's own `MusicService`; a MusicBrainz catalogue whose titles hand off to the music apps the
user already has, each opened on its own search for the title (the hand-off phase 17 builds for Movies & TV, reused rather
than rebuilt); and the user's Jellyfin server's music library played in `MusicService` — and the Music tile, the notification
transport, the sleep timer and equaliser, and Tess follow whatever sounds through the shell's player. Everything that phase 10
built keeps working exactly as verified (MUSIC6–10, MUSIC17); every change to its part is a recorded ADD in the INDEX Change
Log (Hard Rule 16). Offline stays preferred (P6, A11 as amended): what can be cached is cached and browsable with no network,
and the local library never needs one.

## Scope
**In:**
- Radio (Q2 A, Q3 A): a radio-browser.info station directory (browse, search, genre / country, favourites) in a fifth Music
  pivot, the stations played as live items through `MusicService`, with the live-stream forms of the now-playing screen, the
  tile face and the `•••` menu that a stream with no end needs; the directory cached for offline browsing; reconnect,
  metered-data and captive-portal behaviour; station and playlist URLs accepted only as `http` / `https` (T20-5).
- Streaming (Q1 C): the music analogue of phase 17's hand-off — a MusicBrainz catalogue lookup (Cover Art Archive artwork) and
  "Listen on <service>" opening each installed service's own search for the title (Q1 Decision), the user signed in inside
  that app; Pandora with its recorded search form, any other installed music app a plain open (Q5 follow-up); and the user's
  Jellyfin server's music (phase 17's connection) browsed as albums / artists / songs and played in `MusicService`.
- Tess commands for the above (Q5 A): station, genre and "play radio" phrases resolved by EXTENDING J5's `MusicSearch` (T20-2),
  and "listen to <x> on <app>" hand-offs (an ADD to phase 03's matcher).
- The radio pivot inside Music (Q3 A) with no new App Shortcut (T20-3: Music keeps phase 11's four) and no setting (Q4 A);
  diagnostics lines for every silent state, the re-runs of the phase 10 rows this touches, the exported-components and
  APK-budget checks.
**Out (explicitly):** the TV-channels app (PLAN.md 2026-09-23 scope add; R13 first, then its own phase); building any
service's own client — no Spotify / YouTube Music / Apple Music / Tidal / Deezer playback inside the shell, no scraping of a
service's catalogue; bypassing any service's DRM, sign-in or paywall; ripping, recording, caching or time-shifting a stream to
a file (the Voice Recorder records the microphone, not the mixer; nothing here writes audio); an FM tuner (W10M's separate FM
Radio app needed a receiver the S25 Ultra does not expose; P11 records the device facts); a music store; Microsoft's cloud
services; any interim "list-only" Radio or "shortcuts-only" streaming build (Hard Rule 16; the interview ruled the fuller
forms); a Plex client (phase 17 Q-B: Jellyfin only); pinning a station to Start (T20-4); a "Radio" App Shortcut (T20-3); any
change to phase 10's measured now-playing geometry for local tracks (R8, H-M1 signed off).

## Decisions
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
- 2026-09-23: Interview Q5 — Tess gets radio and streaming phrases (Jeremy, to the question's Spotify example: "I dont ever use
  spotify but I do use other apps"; read as A with the hand-off naming any installed music app, Jeremy can overrule): "play
  <station>", "play <genre> radio", "play radio" (the last favourite), resolved by code against the cached directory (P6) and
  played through the session path phase 03 uses over the keyguard; and "listen to <x> on <app>" as a hand-off to that
  installed music app through phase 17's StreamingHandoff. The hand-off table is seeded with the music apps Jeremy uses (asked
  2026-09-23; his list lands here), each with its in-app search deep link and a phone row; any other installed music app gets
  a plain open hand-off. Spotify is not a priority entry.
  - Note 2026-09-23 (r2 triage T20-7): "his list lands here" is answered — his answer: Pandora (the Q5 follow-up line above);
    no further list is a FINAL gate. Pandora's row is E13b (stub) and P9 (the S25U, T20-8); the plain open is E13b's third stub.
  - Note 2026-09-23 (r2 triage T20-9): "play radio" (the last favourite) means the favourite played most recently; with none
    played yet, the first favourite. E16 proves both halves.
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
  that the channels app and Music's streaming side reuse." So this phase builds NO second hand-off: the installed-app
  discovery, the "open at a title" call and the media-server connection are phase 17's, and depends-on carries 17.
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
  session callback answers searches in `onSetMediaItems` (`app/src/main/kotlin/app/tileshell/music/MusicService.kt:155-187`;
  a miss logs `[music] search "<q>": nothing in the library` at `:166` and fails instead of clearing the queue, a hit logs
  `[music] search "<q>": <kind> <label>, n track(s)` at `:169`). This phase EXTENDS `MusicSearch` with stations — favourites,
  then the cached directory by name / genre — ahead of the library, inside that existing callback (build task 7); no new
  override, and J5's line form is the one this phase's lines follow.
- 2026-09-22: **phase 11 Q1 standing rule**: Music declares Songs / Albums / Artists / Playlists as static App Shortcuts. ~~A
  Radio pivot (Q3) adds "Radio" to that list — an ADD to phase 11's part, Change Log when built.~~ SUPERSEDED 2026-09-23 by
  T20-3 (agent line at the end of Decisions): no "Radio" shortcut is declared; Music's four stay as phase 11 Q1 ruled them.
- 2026-09-23 (agent): **one engine, the same service.** Whatever sounds through the shell sounds through `MusicService`'s
  ExoPlayer (Media3 1.8.0, `gradle/libs.versions.toml`), the session `MusicPlayer` already connects to, the `KnownDurationPlayer`
  it wraps and the `CrossfadeFader` beside it. No second player, no second session (RV2 / Hard Rule 16). Progressive HTTP
  audio (MP3, AAC, Ogg — what most internet stations send) plays through the `DefaultMediaSourceFactory` the service already
  builds. HLS stations need `media3-exoplayer-hls`, the same Apache-2.0 library's own module and an ADD to
  `app/build.gradle.kts`, not a second engine; whether the ruled directory has enough HLS stations to justify it is a
  build-start count, recorded here.
- 2026-09-23 (agent): **a live item has no length, and the built code already says what that does.** `KnownDurationPlayer`
  falls back to the item's `mediaMetadata.durationMs` when the extractor reports `C.TIME_UNSET`; a station item has none, so
  `MusicPlayer.durationMs` reads 0. Then `thumbCentreFraction` (MusicNowPlaying.kt) pins the thumb to the left stop,
  `clockText(0)` draws "0:00" as the total, `seekFromTouch` seeks to 0, and the elapsed label counts up from the connect.
  `CrossfadeFader.considerPreparing` returns on `C.TIME_UNSET`, so no fade is ever attempted out of a stream — nothing to
  build there. The sleep timer's `SleepTimer.END_OF_TRACK` arms ExoPlayer's `pauseAtEndOfMediaItems`, which a stream never
  reaches. So a live item needs its own now-playing form — a **P4 design** (R8 measured Groove's now-playing for tracks;
  Groove's Radio played tracks, not live streams, so no measurement exists): the scrubber is drawn as a full-width live bar with
  no thumb and no seek, the two time labels are replaced by a single "LIVE" caption at the elapsed label's position, and the
  `•••` sleep list omits its end-of-track entry (`music_menu_sleep:eot`) while a live item plays; the minute choices stay.
  Everything else on the screen — chrome, art (the station's logo where the directory has one, else the album-art placeholder),
  the two-line metadata block, the transport row, the chevron and the queue — is R8's as built. NEEDS-HUMAN H1. E5 proves it.
- 2026-09-23 (agent): **live metadata.** Media3 folds a stream's ICY data into the player's `MediaMetadata` (`IcyHeaders` for
  the station name and genre, `IcyInfo` for the in-stream StreamTitle; `Player.Listener.onMediaMetadataChanged`). The service
  keeps the station name as the album line and lets the StreamTitle become the title, so `MusicFeed` — which reads
  `METADATA_KEY_TITLE` / `ARTIST` / `ALBUM` off the session and republishes on any change (`MusicRules.republish`: `last !=
  next`) — shows the song now on air on the Music tile with no change to `MusicFeed`. The exact field mapping Media3 1.8.0
  applies is verified at build start and recorded here; E4 proves the observable.
- 2026-09-23 (agent): **the station directory** (Q2 A): radio-browser.info — an open, community
  directory with a free JSON API, no key and no Google. Its published etiquette (resolve a mirror through DNS rather than
  hard-coding one; send a User-Agent naming the app, `Tessera/<version> (…)`, on every request) is followed and re-read at build
  start, with the terms, and recorded here per P5; the mirror rule (a DNS lookup of `all.api.radio-browser.info` → a host list
  → one picked) is a pure function with a JVM test fed a fake DNS answer (T20-10), so it has a failable check with no network. Fetched with `HttpURLConnection` the way `OpenMeteoProvider.fetch` does — no HTTP library is added. Cached to
  the app's private files directory with `AtomicFile`, the shape `WeatherFeed` uses for its report, so the browse pages and
  search work with no network; favourites are the user's own file written with a temp file and a rename like
  `PlaylistStore`'s music_playlists.json. File names are recorded here at build start. The cache is refreshed on open when
  older than a day and on demand; a fetch failure keeps the cache and says so (`[music] radio: directory offline, cache from
  <date>`), never an empty list.
- 2026-09-23 (agent): **the music catalogue** (Q1 C; the database is the Q1 Decision's agent call): MusicBrainz (open data,
  no key) with the Cover Art Archive for artwork, the terms re-read at build start and recorded here (P5: not a Google API);
  MusicBrainz's 1-request-per-second etiquette is honoured (requests spaced ≥ 1.0 s) and every request carries the
  `Tessera/<version> (…)` User-Agent (E13 asserts both). Catalogue rows are cached per search for the session only; a failed
  search logs `[music] catalogue "<q>": offline | error <code> | error connect` and the page says so. A title's "Listen on
  <service>" list comes from phase 17's installed-app discovery and opens each service's own search for the title (Q1): Pandora
  through its search form recorded at build start (Q5 follow-up), any other installed music app (`CATEGORY_APP_MUSIC`) with no
  recorded form as a plain open (`[music] handoff: <app> "<title>" -> open (no search link)`), and Android's
  `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` with `EXTRA_MEDIA_FOCUS` tried where a service answers it. Which services
  appear is never a fixed list; discovery runs at call time through `PackageManager` the way phase 17 does it. Search forms
  change and this doc cannot verify them, so they are recorded at build start with their verification, not here.
- 2026-09-23 (agent): **permissions.** `INTERNET` and `ACCESS_NETWORK_STATE` are already held (AndroidManifest.xml lines 32–33,
  Weather's), `FOREGROUND_SERVICE_MEDIA_PLAYBACK` too. A stream through a screen-off needs the player's wake mode:
  `ExoPlayer.Builder.setWakeMode(C.WAKE_MODE_NETWORK)` (a wake lock plus a Wi-Fi lock while playing), which needs the
  install-time `WAKE_LOCK` permission — an ADD to the manifest and to the shared player, so a local track gets the same
  wake mode. The INDEX records that phase 10 still owes its screen-off row because the player ran "without WAKE_MODE_LOCAL";
  this ADD closes that as well and is recorded in the Change Log for phase 10's part. No new checklist row: install-time
  permissions have none (phase 03's `USE_EXACT_ALARM` precedent).
- 2026-09-23 (agent): **reconnect.** ExoPlayer's `DefaultLoadErrorHandlingPolicy` retries a failing load itself; once it gives
  up, the service retries the station for a bounded window — 60 s, backing off 2 / 4 / 8 / 16 / 30 s (agent pick, H4) —
  showing "Reconnecting…" as the title line, then stops with "This station isn't answering" and a diagnostics line. A network
  that returns inside the window resumes the stream with no tap, because from the listener's view it never stopped; after the
  window a tap is needed. This is not phase 10 E9's "nothing resumes by itself" — that rule is about audio focus taken by
  another app, which still holds (E15).
- 2026-09-23 (agent, P4 design, H3): **metered data.** A stream is a continuous download. When the active network is metered
  (`ConnectivityManager.isActiveNetworkMetered`) the now-playing screen shows one line under the metadata, "Streaming over
  mobile data", and plays — the person pressed play. With Android's Data Saver on (`getRestrictBackgroundStatus`), the
  foreground service keeps its stream (the platform exempts foreground services), and the line says so. No Wi-Fi-only
  setting exists on top of this (Q4 A).
- 2026-09-23 (agent): **captive portal.** A network Android has flagged (`NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL`,
  or a validated-false default network) makes a station tap show "Sign in to this Wi-Fi network first" and starts no stream;
  the sign-in itself is Android's own captive-portal notification — the shell opens no page of its own (Android limits) —
  and the directory falls back to its cache.
- 2026-09-23 (agent): **the Radio pivot** (Q3 A): `MusicPivot` gains a fifth entry titled "radio"
  (R3's lowercase pivot form, as the four built ones), `MusicCollection.page` gains its case, and the header strip's
  scroll-no-further-than-needed rule (phase 10 task 6 amendment, MUSIC7) already covers a fifth header — E1 re-measures it. Its
  list rows are the app list's (R3 C2 / R6 §5.1.4) as the other pivots are; favourites first under a "favourites" letter-less
  group, then a browse row set (search, by genre, by country) — the arrangement is a **P4 design** (Groove's Radio pivot held
  artist stations, not a directory; no measurement exists), H2. A hold on a station offers add to / remove from favourites
  only. ~~and pin-to-Start (the phase 11 burst carries it once phase 11 is in)~~ SUPERSEDED 2026-09-23 by T20-4 (agent line at
  the end of Decisions).
- 2026-09-23 (agent): **harness contracts.** Every new node a row reads carries its own test tag under the
  `testTagsAsResourceId` root `MusicActivity` already sets (phase 10's `music_pri:` / `music_sub:` lesson); every silent
  state writes a `[music]` diagnostics line (wording in E19). The tags the rows below name that do not exist today
  (`radio_row:`, `radio_fav:`, `nowplaying_live`, `nowplaying_live_caption`, `nowplaying_metered`, `catalogue_row:`,
  `handoff_service:`) are this phase's own, added by the task that draws each node. Drivers: qa/phase-20/scripts/lib.sh → symlink to
  qa/phase-03/scripts/lib.sh, plus qa/phase-01/scripts/music_lib.sh (its `music_fixtures`, `music_open`, `goto_pivot`);
  evidence under qa/phase-20/. ~~The directory and catalogue base URLs are debug-build `BuildConfig` fields defaulting to the
  real endpoints in every build type~~ SUPERSEDED 2026-09-23 by T20-6: the directory, catalogue and Cover Art bases are
  debug-only prefs (`qa_radio_base`, `qa_music_catalogue_base`, `qa_coverart_base`, written with
  `qa/phase-01/scripts/prefs_edit.py`) honoured only when `BuildConfig.DEBUG`, phase 17's route (its task 12), so one debug APK
  serves every row; the release APK cannot be redirected. That is a fixture route for a data source, not a
  request-injection path (phase 03's rule pins Cortana's inputs; nothing here takes a Cortana request).
- 2026-09-23 (agent): **the two kinds of NEEDS-HUMAN row are labelled** as phase 17 labels them: *fidelity* (matches a
  measurement, judged on the phone) and *accept* (a P4 design or an approximation; Jeremy accepts or overrules).
  qa/phase-20/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.
- 2026-09-23 (review triage T20-5, a doc update; trust): **untrusted URLs never reach ExoPlayer's other schemes.** The
  service's `DefaultDataSource` also opens `file://`, `content://`, `asset://`, `rawresource://` and `data:`, and station URLs
  come from a community directory. So a station URL and a `.pls` / `.m3u` entry are accepted only as `http` / `https`
  (anything else → `[music] stream: unsupported scheme=<s>`, the station listed with "can't play this station"; a JVM test on
  the resolver covers each refused scheme and both accepted ones); a playlist entry is resolved only if http / https, else
  `[music] stream: unsupported playlist`; the directory response is capped (the cap recorded here at build start; larger →
  `[music] radio: directory too large`, the cache kept); station logos are decoded at bounds with a byte cap (arbitrary hosts).
  All of it sits under C-16's adversarial review (below).
- 2026-09-23 (review triage C-16, a doc update; trust — the policy is **Q-D: A**): **plain-http streams.** `targetSdk =
  36` (`app/build.gradle.kts:22`) and no `usesCleartextTraffic` / `networkSecurityConfig` today (`app/src/main/AndroidManifest.xml`;
  `app/src/main/res/xml/` holds only `method.xml`, `recognition_service.xml`, `voice_interaction_service.xml`), so every
  `http://` connection is refused — about one in three popular radio-browser.info stations (triage probe: 700 of the 2,000
  most-clicked, 35.0 %). Phase 17's build task 17 "Network security (C-16)" creates `app/src/main/res/xml/network_security_config.xml`
  and the manifest's `android:networkSecurityConfig`; this phase creates NO second config and adds nothing to it: 17's
  `<domain-config cleartextTrafficPermitted="false">` (includeSubdomains) already lists `api.radio-browser.info` (its mirrors
  are subdomains), `musicbrainz.org`, `coverartarchive.org` and its redirect host, and its `FixedEndpoints` JVM test and
  process-start `[net] cleartext permitted for <host>: <bool>` lines cover them. The debug-only QA exception
  (`app/src/debug/res/xml/network_security_config.xml`, `10.0.2.2` cleartext-permitted; B's code guard honours it only when
  `BuildConfig.DEBUG`) lets the host fixtures run under every answer. **Pending Q-D** (lean first): **A (lean)** cleartext
  permitted at the base config (station hosts cannot be listed), the fixed endpoints still https-only by the domain-config →
  http stations play; **B** base permitted plus phase 17's code guard in the media data-source factory, which refuses `http://`
  except to private addresses (RFC 1918, link-local, `.local`) for the media-server client — a station over http is refused
  with `[music] stream: cleartext refused` and never connects; **C** base not permitted → the platform refuses every http
  station (`[music] stream: cleartext refused`). Jellyfin music over http: A / B (a private address) plays; C only through the
  debug exception (the release APK refuses it — stated, not hidden). Rows: E3b (below). **GATE, every branch:** this phase
  re-runs the adversarial review (team-review, adversarial mode) on its station path — T20-5's scheme allow-list and caps, the
  Q-D branch as built (A: http stations over the base permission; B: the station refusal and the reused private-address guard;
  C: the config alone) and T20-13's query-stripped logging — recorded under qa/phase-20/ before `done`, as phase 17's GATE is
  under qa/phase-17/.
- 2026-09-23 (review triage C-32 / T20-12 / T20-13, a doc update; trust): **credentials.** Phase 17's Decisions "Trust" →
  "Credential hygiene (C-32)" governs here: no credential (the Jellyfin token, the fixture passwords, the TMDB read token) is ever written to a
  diagnostics line, logcat, a logged URL or an evidence file, and every row that touches one ends with
  `qa/phase-17/scripts/leak_scan.sh` over `qa/phase-20/**`, the saved ring slices and `adb logcat -d`. Jellyfin stream URLs
  carry the token as a query parameter, so every stream URL is logged with its query string removed (`stream: connected
  http://10.0.2.2:8096/Audio/<id>/stream codec=mp3`). The music side's server lines are `[music] server <host>: connected |
  unreachable | unauthorised` in the MAIN ring (`MusicService` is main-process), not phase 17's `:video`-ring `[video] server …`.
- 2026-09-23 (agent, r2 triage T20-3): **Music keeps Jeremy's four App Shortcuts (Songs / Albums / Artists / Playlists) and
  declares no "Radio" shortcut**; the phase-11 Decision's "A Radio pivot adds 'Radio' to that list" and E1's shortcut clause are
  struck; H9 → [accept] "Music's satellites stay the four; Jeremy can swap Playlists for Radio on the phone". Reason: phase 11 Q1
  named the four and the four-satellite cap is his ruling too (round 1's T11-10 precedent: keep the ruled four, an H row to
  swap); a rank-4 static shortcut never bursts and is dead weight.
- 2026-09-23 (agent, r2 triage T20-4): **no pin-a-station-to-Start**; a hold on a station offers add to / remove from
  favourites only. Reason: a pinned station is a new tile kind nobody designed or asked for; favourites and the Music tile's
  live face cover the need, and Rule 16 forbids a half form.
- 2026-09-23 (agent, r2 triage T20-6): **the fixture route is debug-only prefs** — `qa_radio_base`, `qa_music_catalogue_base`,
  `qa_coverart_base`, written with `qa/phase-01/scripts/prefs_edit.py` and honoured only when `BuildConfig.DEBUG` — replacing
  the "debug APK with BuildConfig fields pointed at 10.0.2.2 … and a build with the real defaults" route; the Acceptance
  preamble gains Seeding (from `qa/phase-18/baseline_layout.json`, the newest on disk at build under C-14's order), C-6, C-4,
  C-5 / C-31, C-20, C-25 and C-26. Reason: one debug APK serves every row so the evidence's APK stamp matches (`lib.sh` `apk
  match`), and it is phase 17's route (task 12).

### Approximations (each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Live now-playing form: live bar, "LIVE" caption, omitted end-of-track entry | P4 design, no source | R8's screen with the scrubber and labels replaced as described | H1 |
| Y2 | Radio pivot rows and the favourites / browse arrangement | P4 design, no source | app-list rows (R3 C2 / R6 §5.1.4); MusicMetrics for the header | H2 |
| Y3 | Metered-data line, wording and placement | P4 design | one caption line under the metadata block | H3 |
| Y4 | Reconnect window and back-off (60 s; 2/4/8/16/30 s) | agent pick | as stated | H4 |
| Y5 | Catalogue and "Listen on" pages (Q1 C) | P4 design: phase 17's Y8 / Y9 forms (R11 measured no Browse / Store half — T20-7, T20-14) | phase 17's Y8 / Y9 forms in the Music idiom | H5 |
| Y6 | Tess reply wording for stations ("Playing <station>.") | phase 03's reply form | as stated | H7 |
| Y7 | The Jellyfin music view: its entry point, albums / artists / songs grouping, the server-name line (Q1 C) | P4 design, no source | the four built pivots' rows (R3 C2 / R6 §5.1.4) under a server header | H10 |

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
re-verified early. Every task is built in the form the interview ruled (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A); the Q-D branch of
C-16 is the one conditional part (Q-D: A).

1. **Build-start checks, recorded in Decisions.** Media3 1.8.0's ICY field mapping (`IcyHeaders` / `IcyInfo` into
   `MediaMetadata`); the directory's terms, mirror etiquette and User-Agent rule; MusicBrainz's and the Cover Art Archive's
   terms and rate limit, and the Cover Art Archive's redirect host (for phase 17's fixed-host list); Pandora's in-app search form
   verified against the installed app (T20-8; none found → the plain open and `search not passed`), and each service's
   answer to `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`; the count of HLS stations in the directory (whether
   `media3-exoplayer-hls` is added); the directory response cap (T20-5); the `cmd netpolicy` metered-network id form on the
   AVD (E12); the cache and favourites file names. ~~the `BuildConfig` field names for the fixture endpoints~~ SUPERSEDED
   2026-09-23 by T20-6 (debug-only prefs).
2. **The shared player's wake mode** (ADD to phase 10's part, Change Log): `WAKE_LOCK` in the manifest,
   `setWakeMode(C.WAKE_MODE_NETWORK)` on the `ExoPlayer.Builder` in `MusicService.onCreate`. Re-runs MUSIC7 and MUSIC17 on the
   same build (nothing else in the service changed yet) and adds E20's screen-off half.
3. **Station playback** (Q2 A, Q3 A): a station `MediaItem` built beside `MusicService.mediaItem(track)` — the
   stream URL as its URI, the station name as `albumTitle`, the station logo as `artworkUri`, no `durationMs`; a play path
   beside `MusicPlayer.play(queue, startIndex)` (which takes `Track`s) that sets a station queue (the favourites in order, so
   next / previous step through them like presets); live metadata as Decisions; reconnect as Decisions; the metered and
   captive-portal states; the `[music] stream:` lines, every URL logged with its query string removed (T20-13); the T20-5
   scheme allow-list (`http` / `https` only, JVM-tested resolver; `.pls` / `.m3u` entries likewise) and the logo byte cap;
   the Q-D branch of C-16 on the station path as ruled (Q-D: A: A plays http stations, B / C log `[music] stream:
   cleartext refused`), under phase 17's single network security config. Crossfade needs no change (`considerPreparing`
   returns on `C.TIME_UNSET`); the sleep timer's minute choices work as built; end-of-track is hidden by task 4, and an
   armed end-of-track timer is cleared when the item goes live (`[music] sleep: end-of-track cleared (live item)`). GATE:
   the station-path adversarial review of C-16 (Decisions), recorded under qa/phase-20/ before `done`.
4. **The live now-playing form** (Y1, P4): in `NowPlayingPage`, when the session's item is live (`MusicPlayer.durationMs`
   0 and the item marked live), the scrubber draws as the live bar with no `pointerInput`, the labels become "LIVE", and
   `moreEntries` omits `SleepTimer.Choice.END_OF_TRACK`; a local track keeps every measured value (MUSIC7 re-run). Tags:
   `nowplaying_live`, `nowplaying_live_caption`.
5. **The directory** (Q2 A): fetch (mirror through DNS, the JVM-tested rule of T20-10; the `Tessera/<version> (…)`
   User-Agent), parse, the response cap (`[music] radio: directory too large`), cache (`AtomicFile`), refresh rules, search /
   genre / country, favourites store with a last-played stamp (for "play radio", T20-9); the offline first-open state (`[music]
   radio: no cache yet (offline)`); pure grouping and search on the JVM (as `MusicGrouping` / `MusicCollection` are), the
   platform part thin.
6. **Radio in the app** (Q3 A): the fifth pivot (`MusicPivot.RADIO`, `MusicCollection.page`, rows, the hold menu with add to /
   remove from favourites only — T20-4, the header-strip re-measure). No App Shortcut is declared (T20-3). ~~and the "Radio"
   App Shortcut (ADD to phase 11's part); B — a launcher activity …; C — a RADIO slot …~~ SUPERSEDED 2026-09-23 by T20-1 /
   T20-3 (Q3 ruled A).
7. **Tess** (Q5 A): phrases in `CommandMatcher` (ADD to phase 03's part) mapped to `Request.PlayMusic`'s query, `LockGate`
   treating them as phase 03 treats music; the station resolution goes into J5's `MusicSearch`
   (`app/src/main/kotlin/app/tileshell/music/MusicSearch.kt:14`) inside the existing `onSetMediaItems`
   (`app/src/main/kotlin/app/tileshell/music/MusicService.kt:155-187`) — "play radio" (the most recently played favourite, else
   the first), then favourites and the cached directory by name / genre, then the library's songs / artists / albums as J5
   built; no new override (T20-2); a hit logs `[music] search "<q>": station <name>` beside J5's `: <kind> <label>, n track(s)`
   (`:169`), a miss keeps J5's `: nothing in the library` (`:166`) and J5's miss reply. "listen to <x> on <app>" hands off
   through phase 17's `StreamingHandoff` (task 8). No LLM in the path (P6). Utterances for E16 built through `utterances.py
   build` (T20-7): `radio_genre_jazz`, `radio_station_news1`, `radio_last`, `radio_miss`, `handoff_listen_stub`.
8. **Streaming side** (Q1 C): the catalogue search page and the title page in the Music app (MusicBrainz, the Cover Art
   Archive, ≥ 1.0 s between requests, the User-Agent, the `[music] catalogue …` lines), calling phase 17's hand-off for the
   "Listen on <service>" list, each service opened on its own search for the title — Pandora by its recorded form, a plain
   open for a music app with none (`[music] handoff: <app> "<title>" -> open (no search link)`), `did not open at the title`
   logged when the service lands elsewhere; the Jellyfin music through phase 17's connection, listed as albums / artists /
   songs in the Music idiom (Y7, H10), its tracks played in `MusicService` as network items with a known duration (so the
   built now-playing form applies, not the live one; crossfade and end-of-track work), stream URLs logged with the query
   string removed and `[music] server …` lines in the main ring (T20-12 / T20-13). Transcoding versus direct play is a
   build-time call recorded here.
9. ~~**Settings + checklist** *(Q4 B)*: the "Stream only on Wi-Fi" setting …~~ SUPERSEDED 2026-09-23 by T20-1: Q4 ruled A — no
   setting and no permission row (Decisions).
10. **Re-runs and regressions.** MUSIC6 (pivot count and strip), MUSIC7 (`•••` with a local track), MUSIC17 (a station in
    the queue never fades), phase 10 E10 / E13 with a station (E17 here), `qa/phase-03/scripts/j5.sh` (J5's 8/8, E16), the
    exported allow-list (phase 03 E5's method; exactly the allow-list, no ADD) and the APK budget (E18).
11. **QA fixtures and harness.** qa/phase-20/scripts/icy_server.py (host HTTP server: a looping MP3 made as MUSIC6's fixtures
    were, sent with `icy-name`, `icy-metaint` and a StreamTitle that switches 20 s after each connection opens (T20-15);
    endpoints for AAC, stop-after-N-seconds and a codec switch on reconnect); directory_server.py (the directory API's JSON
    shape with the six fixture stations; the `/edge/` set — two `.pls` stations, a `file://` and a `content://` station, and
    the Q-D refusal station `http://10.0.2.3:8000/refuse`; `/huge/` above the T20-5 cap; logging each request's User-Agent); `qa/phase-20/scripts/catalogue_server.py` at `10.0.2.2:8081` (MusicBrainz `/ws/2/` JSON
    for "qa artist", `/coverart/` PNGs, `/500`, logging each request's time and User-Agent — T20-11); portal_server.py (returns
    200 with a page for Android's captive-portal probe); three stub APKs — the hand-off stub (same kind as phase 17 E9's
    fixture: declares `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` and a search form, logs every received intent to the `TileShellQa`
    logcat tag, can play a tone with `USAGE_MEDIA` for E15), `app.tileshell.testclient.pandorastub` (declares Pandora's search
    form recorded by task 1) and a plain `CATEGORY_APP_MUSIC` stub with no recorded form (T20-8); three MUSIC6-style MP3s of
    known durations added to `qa/phase-17/fixtures/jellyfin/`'s music library (T20-12); `qa/phase-20/baseline_layout.json`
    derived from `qa/phase-18/baseline_layout.json` (the newest on disk at build under C-14's order) with the MUSIC / PHOTOS /
    CAMERA slots, Auxio pinned and `manualSizes` for every tile, the previous file kept as
    `qa/phase-20/baseline_layout-pre-20.json` (C-3's form; this phase adds no marker, it pins a fixture — T20-6).

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); dumps follow RV13; motion rows follow RV11 and
take their clock from the shell's `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`), a
screenrecord corroborating under phase 05's frame-spacing rule and never the primary clock (C-5); the `[motion]` line also
carries `frames=<n> maxGapMs=<ms>`, and every motion row asserts `maxGapMs` ≤ 33.4 ms (2 vsync) beside its numbers (C-31).
**Seeding (T20-6, C-3):** every row starts from `layout_restore qa/phase-20/baseline_layout.json`
(`qa/phase-02/scripts/layout.sh:18`; derived from `qa/phase-18/baseline_layout.json`, the newest on disk at build under C-14's
order, with the MUSIC / PHOTOS / CAMERA slots, Auxio pinned and `manualSizes` for every tile), and after the restore the ring
holds zero `assignSlotOnce … -> assigned` lines. After any row launches another app (E13, E13b, E16's stub launches), `adb shell
am force-stop app.tileshell` + Home before the next assertion on Start's grid (C-6). "Fresh install" = `pm clear
app.tileshell` → `qa/phase-03/scripts/provision.sh` → Home (C-4), then the fixture prefs written again. Every ring
assertion reads `ring_since` from a MARK taken immediately before the step's action (after any clock jump, so the MARK is on
the new clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`; `row_end` saves each ring the
row names to `<row>/ring-<name>.txt` (C-20; `lib.sh` `ring_mark` / `ring_since` / `reply_since`, built by phase 11's build
task 7). After any `adb reboot` (boot-completed poll), `dumpsys battery unplug` or `KEYCODE_SLEEP` step, the driver calls
`wake_device` and asserts it printed `Awake` before the next tap (C-25). A recorded clause uses `lib.sh` `record <name>
<value>`, never an assert; a row with only recorded facts ends `<row>: recorded only (<n> facts)` with exit 0 (C-26; helper
built by phase 13's build task 7). **Egress guard (C-29):** E2–E16 run between `lib.sh` `egress_guard_on` and
`egress_guard_off` (built by phase 17's build task 16: `adb root`; the app uid from `pm list packages -U app.tileshell`;
`iptables -I OUTPUT -m owner --uid-owner $UID ! -d 10.0.2.2 -j REJECT`; at row end `iptables -L OUTPUT -v -n` shows 0 packets
on that rule — gated — then the rule is deleted and `adb unroot`), so no AVD row reaches the live internet; offline sub-rows
keep airplane mode; E3b replaces the 0-packet assertion with its Q-D branch expectation on the same counter (below). **Voice rows (C-30):** each `say` in E16 saves
`speech_dump` sliced from its MARK and asserts the `:speech` ring's final line for that capture is not "asr: no speech".
**Credentials (C-32):** the one debug APK is assembled with `-Ptmdb.readToken=qa-dummy-token`, so no QA build carries Jeremy's
TMDB key (phase 17's rule under its Q-A2: B ruling); a row that touches the Jellyfin token or a fixture password ends with `qa/phase-17/scripts/leak_scan.sh`
over `qa/phase-20/**`, the saved ring slices and `adb logcat -d` (phase 17 Decisions "Trust", credential hygiene).
Harness: qa/phase-20/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh, plus qa/phase-01/scripts/music_lib.sh; every row
stamps its driver blob and the installed APK (one debug APK for every row, `lib.sh` `apk match`). Device: the AOSP AVD
tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google, `sdk_phone64_x86_64` userdebug), with **no streaming app installed** —
real services are P rows. "Diagnostics" is the launcher ring (phase 01's command, sliced as above); `[music]` lines are the
ones asserted.

**Fixtures.** The AVD reaches the host at 10.0.2.2. ~~A debug APK with the directory and catalogue `BuildConfig` fields
pointed at `http://10.0.2.2:8080/` … and, for E21 only, a build with the real defaults.~~ SUPERSEDED 2026-09-23 by T20-6: the
one debug APK, redirected by the debug-only prefs `qa_radio_base` = `http://10.0.2.2:8080/`, `qa_music_catalogue_base` =
`http://10.0.2.2:8081/ws/2/` and `qa_coverart_base` = `http://10.0.2.2:8081/coverart/`, written with
`qa/phase-01/scripts/prefs_edit.py` (honoured only when `BuildConfig.DEBUG`; the live directory is P10). directory_server.py
(port 8080): six stations — two "jazz", two "news", one "rock", one HLS — with `url_resolved` values on icy_server.py at
`http://10.0.2.2:8000/<name>`; a second set under `http://10.0.2.2:8080/edge/`, written into `qa_radio_base` only by E3 / E3b:
"QA Playlist" (a `.pls` whose first entry is `http://10.0.2.2:8000/jazz1`), "QA Bad Playlist" (a `.pls` whose only entry is
`file:///sdcard/Music/x.mp3`), "QA File" (`file:///sdcard/Music/x.mp3`), "QA Content" (`content://media/external/audio/media/1`)
and "QA Refuse" (`http://10.0.2.3:8000/refuse`, not the debug-excepted host); `/huge/` serves a directory larger than the T20-5
cap; every request's User-Agent is logged. icy_server.py (port 8000) streams a 90-second 440 Hz MP3 (ffmpeg, `lavfi sine`, the
method behind qa/phase-01/MUSIC6-fixtures) in a loop, `icy-name: QA Jazz One`, `icy-metaint: 16000`, StreamTitle "QA Song 1",
then "QA Song 2" 20 s after each connection opens (T20-15), and so on; `/aac` sends AAC; `/stops` closes after 20 s; `/switch`
serves MP3 on the first connect and AAC on the next. catalogue_server.py (port 8081, T20-11): MusicBrainz `/ws/2/` JSON for
"qa artist" holding exactly three recordings — "QA Song A" / "QA Artist" / "QA Album", "QA Song B" / "QA Artist" / "QA Album",
"QA Song C" / "QA Artist" / "QA Second Album" — and `/coverart/` 250×250 flat PNGs, (200,60,60) for QA Album and (60,60,200)
for QA Second Album; `/500` answers 500; it logs each request's arrival time (ms) and User-Agent. Jellyfin: phase 17 E22's
container `jellyfin/jellyfin:<version>@sha256:<digest>` (pinned at phase 17's BS-5, the digest in every run's log) at
`10.0.2.2:8096`, config `qa/phase-17/fixtures/jellyfin/`, whose music library holds three MUSIC6-style MP3s of known
durations (T20-12). Audio is asserted the way MUSIC17 asserted it: `adb shell dumpsys media.audio_flinger` tracks in state A
on the player's own audio session (the `playback service started (audio session N …)` line names it). The three stub APKs
(task 11) are installed only for the rows that say so and uninstalled by them. Rows that need the six local MP3s call
`music_fixtures`.

**Emulator:**
- E1 **Radio in the app** (Q3 A). `music_open`; the dump shows five `music_pivot_header:*` nodes with "radio" last; on
  songs the strip is as MUSIC7 asserted (playlists' right edge at 1080 px of a 1080-px screen); `goto_pivot radio` → the
  radio header whole, `[music] pivot settled on radio` (its `[motion]` line within MUSIC7's settle and `maxGapMs` ≤ 33.4, C-31);
  Back returns to songs at exactly MUSIC7's geometry. No Radio shortcut (T20-3): `dumpsys shortcut` lists Music's four
  manifest shortcuts (`songs`, `albums`, `artists`, `playlists`) and no radio id. ~~*(Q3 B)* … *(Q3 C)* … The Radio App
  Shortcut *(Q3 A / B)* …~~ SUPERSEDED 2026-09-23 by T20-1 / T20-3 (Q3 ruled A; no Radio shortcut).
- E2 **Directory, cache, favourites** (Q2 A). Fresh install (C-4) with the network on: the radio page lists the six fixture
  stations (`radio_row:<id>` tags, `music_pri:` / `music_sub:` text = name and genre), `[music] radio: directory fetched 6
  stations`, and directory_server.py's log shows `User-Agent: Tessera/<version> (…)` on every request (T20-11); search "jazz"
  → 2 rows; by genre "news" → 2; `cmd connectivity airplane-mode enable` → force-stop and reopen → the same six rows from
  the cache and `[music] radio: directory offline, cache from <date>`; hold a station → the menu offers add to favourites and
  NO Pin to Start entry (dump text; T20-4) → add; `am force-stop` and reopen → it is first under favourites
  (`radio_fav:<id>`); restore airplane mode. **Offline first open (T20-16):** a second fresh install with airplane mode on
  before the first open → the radio pivot's empty state "No connection yet — stations will appear when there is one" (dump
  text), no `radio_row:`, and `[music] radio: no cache yet (offline)`; restore airplane mode.
- E3 **A station plays in `MusicService`**. Tap "QA Jazz One" → `dumpsys media_session` shows `app.tileshell` PLAYING with
  metadata title "QA Song 1" and album "QA Jazz One"; AudioFlinger shows one track in state A on the player's session;
  `[music] stream: connected http://10.0.2.2:8000/jazz1 codec=mp3`; the now-playing screen is on top (`nowplaying_root`);
  Back → the collection, still playing (phase 10 task 7's rule). `/aac` plays the same way with `codec=aac`; the HLS station
  plays only if `media3-exoplayer-hls` was added (task 1's count), otherwise it is listed with "can't play this station" and
  `[music] stream: unsupported hls` (no crash; `logcat -d -s AndroidRuntime` empty of `app.tileshell`). **Playlists and schemes
  (T20-5, T20-16):** with `qa_radio_base` on `/edge/`: "QA Playlist" → resolved to its first entry and playing (`stream:
  connected http://10.0.2.2:8000/jazz1 codec=mp3`); "QA Bad Playlist" → `[music] stream: unsupported playlist`; "QA File" →
  `[music] stream: unsupported scheme=file`; "QA Content" → `[music] stream: unsupported scheme=content`; each of the last three
  listed with "can't play this station", no `stream: connected` line after its MARK and the session's metadata unchanged;
  `qa_radio_base` on `/huge/` with the six-station cache present → `[music] radio: directory too large` and the six cached rows
  still listed. Restore `qa_radio_base`.
- E3b **Plain http (C-16; Q-D: A).** After `am force-stop` + Home, from a MARK: the launcher ring holds `[net] cleartext
  permitted for api.radio-browser.info: false`, `… musicbrainz.org: false` and `… coverartarchive.org: false` (phase 17's
  fixed-host lines; a `true` fails the row under every answer). The fixture stations on `http://10.0.2.2:8000` play under every
  answer (E3; the debug-only exception). "QA Refuse" (`http://10.0.2.3:8000/refuse`, `qa_radio_base` on `/edge/`), this row's own
  guard (`egress_guard_on` before the tap; the rule's packet count read from `iptables -L OUTPUT -v -n` is the assertion, then
  the rule is deleted and `adb unroot`): **A (lean)** → no `cleartext refused` line, `[music] stream: lost, retrying`, and the
  rule's packet count > 0 (the connection was attempted and rejected by the guard); **B / C** → `[music] stream: cleartext
  refused`, no `lost, retrying`, and 0 packets (refused before any connect). The branch asserted is the one Q-D rules; the other
  branches' expectations stay written so the row fails on a build of the wrong branch. Release config (phase 17's row, re-run
  on this build): `./gradlew :app:assembleRelease -Ptmdb.readToken=` (C-32), then `aapt2 dump xmltree --file res/xml/network_security_config.xml
  app/build/outputs/apk/release/<apk>` holds no `10.0.2.2` and exactly the base-config value of the ruled branch.
- E4 **Live metadata and the tile** (device clock, T20-15). icy_server.py switches the StreamTitle 20 s after each connection
  opens; from a MARK before E3's tap, the `[music] now playing app.tileshell title=QA Song 2 …` line's `wall=` − the `[music]
  stream: connected …` line's `wall=` = 20 000 ± 5 000 ms; then the session's title reads "QA Song 2" (`dumpsys
  media_session`), the Music tile's face shows "QA Song 2" (the `tile_*` tags phase 10 E10 reads) and the album line stays
  "QA Jazz One". Pause from the tile's `tile_control:<id>:PAUSE` → PAUSED (phase 10 E13's method).
- E5 **The live form** (Y1). With a station playing: `nowplaying_live` present, `nowplaying_scrubber` absent, the caption
  reads "LIVE" (`nowplaying_live_caption`), `nowplaying_total` absent; `adb shell input tap` across the live bar's bounds
  changes nothing (`dumpsys media_session` position keeps advancing, no seek line); open `•••` → sleep → `music_menu_sleep:eot`
  absent, the four minute entries present; set crossfade 5 s (`music_menu_crossfade:5`), then a local track in the queue
  before the station and let it run out → `[music] crossfade: no fade after …` or `prepared` never followed by `fade
  started` into the station, AudioFlinger never two tracks of ours; play a local track → `nowplaying_scrubber` and
  `nowplaying_total` back, MUSIC7's geometry checks pass on the same build. **Armed end-of-track cleared (T20-16):** a local
  track playing with the station next in the queue, `•••` → sleep → end of track (`music_menu_sleep:eot`), then next → the
  station plays and, from a MARK before the tap, `[music] sleep: end-of-track cleared (live item)` is in the slice; the
  `•••` sleep list then shows no armed end-of-track entry.
- E6 **Sleep timer on a stream.** "In 15 minutes" with a station playing: still playing at 10 minutes (AudioFlinger), paused
  900 s ± 2 s after the `armed for 15 minute(s) at elapsed …` line (MUSIC9's method), the stream's track gone from
  AudioFlinger, the session PAUSED, the notification still present.
- E7 **Equaliser on a stream.** Choose a preset → AudioFlinger shows the Equalizer effect enabled on the same session as the
  streaming track (MUSIC9's method); Off disables it.
- E8 **Network off and back** (device clock, T20-15). Station playing; MARK, then `cmd connectivity airplane-mode enable` →
  `[music] stream: lost, retrying` with `wall=` − MARK ≤ 5000 and the title line "Reconnecting…" (`nowplaying_track` text);
  at 30 s MARK2, then `airplane-mode disable` → `[music] stream: reconnected after N ms` with `wall=` − MARK2 ≤ 10 000, no
  tap, AudioFlinger track back. Second pass: MARK3, airplane on for 120 s → in the slice from MARK3, `[music] stream: gave up
  after 60000 ms` whose `wall=` − that slice's `stream: lost, retrying` `wall=` = 60 000 ± 5 000, "This station isn't answering" (`nowplaying_track`), session PAUSED, no crash; airplane
  off → stays paused until a tap (RV12 restore).
- E9 **Flaky network.** `adb emu network speed gsm` and `adb emu network delay gprs` during E3 → the stream keeps playing or
  rebuffers (`[music] stream: buffering` then `playing`), never the gave-up state within 60 s, no crash; restore `network
  speed full`, `network delay none`.
- E10 **Captive portal.** `settings put global captive_portal_http_url http://10.0.2.2:8082/gen_204` and
  `captive_portal_https_url` likewise (portal_server.py answers 200 with a page), `cmd connectivity airplane-mode enable`
  then `disable` so the network re-validates → `dumpsys connectivity` shows the default network with CAPTIVE_PORTAL; a
  station tap shows "Sign in to this Wi-Fi network first" (dump text), no `stream: connected` line, the directory page shows
  the cache; restore both settings (`settings delete global …`) and re-validate.
- E11 **A station that stops or changes codec.** `/stops` → after 20 s the reconnect path of E8 runs against a server that
  answers again → playing within the window, `[music] stream: reconnected`; `/switch` → the reconnect lands on AAC and plays
  (`codec=aac` on the second `connected` line), AudioFlinger track present again.
- E12 **Metered data** (Q4 A). `cmd netpolicy set metered-network <id> true` (id per task 1) → the now-playing screen shows
  "Streaming over mobile data" (`nowplaying_metered` text), `[music] stream: metered`, and the station still plays; `cmd
  netpolicy set restrict-background true` → still playing (foreground service), the line says "Data Saver is on"; no setting
  or ask-once dialog exists (the `•••` menu has no Wi-Fi-only entry). ~~*(Q4 B)* … *(Q4 C)* …~~ SUPERSEDED 2026-09-23 by
  T20-1 (Q4 ruled A). Restore both netpolicy settings.
- E13 **Catalogue and the streaming hand-off** (Q1 C; T20-11). Search "qa artist" against catalogue_server.py → exactly three
  `catalogue_row:` rows in this order with these title / artist / album texts: "QA Song A" / "QA Artist" / "QA Album", "QA
  Song B" / "QA Artist" / "QA Album", "QA Song C" / "QA Artist" / "QA Second Album"; each row's artwork centre pixel =
  (200,60,60) ± 4 for QA Album and (60,60,200) ± 4 for QA Second Album; `[music] catalogue "qa artist": 3`; the fixture log
  shows `User-Agent: Tessera/<version> (…)` on every request (catalogue and cover art). **Etiquette:** "qa artist" submitted
  five times back-to-back (within 2 s) → the fixture log's five `/ws/2/` arrival times are each ≥ 1000 ms after the one
  before. **Failures:** `cmd connectivity airplane-mode enable` → the page's offline line (dump text) with no
  `catalogue_row:` and `[music] catalogue "qa artist": offline`, restore; `qa_music_catalogue_base` on `/500` → `[music]
  catalogue "qa artist": error 500` and the page says so; catalogue_server.py stopped → `error connect`; restart it and the
  base. **Hand-off:** open a title → NO "Listen on" entry with no stub installed (`handoff_service:` absent, `[music] handoff:
  no service installed for <title>`); `adb install` the hand-off stub → the entry appears on the next draw (the discovery is
  live, not cached at first open); tap → `adb logcat -d -s TileShellQa` shows the intent the stub received: its action, and a
  data URI or `EXTRA_MEDIA_FOCUS` query naming the title and artist; `[music] handoff: <stub> "<title>" -> <uri|intent>`;
  `dumpsys activity activities` shows the stub resumed; the shell started no player (`dumpsys media_session` unchanged);
  `am force-stop app.tileshell` + Home (C-6); uninstall the stub → the entry is gone.
- E13b **Pandora and the plain open (T20-8).** Install `app.tileshell.testclient.pandorastub` (declaring Pandora's search form
  recorded by task 1) and the plain `CATEGORY_APP_MUSIC` stub "QA Plain" (no recorded form); open "QA Song A" → "Listen on
  Pandora" and "Listen on QA Plain" both listed. Tap Pandora → the stub resumed and its `TileShellQa` line shows the recorded
  search URI carrying the title and artist, `[music] handoff: pandora "QA Song A" -> <uri>`; C-6. Tap QA Plain → its launch
  activity resumed (`TileShellQa` logs MAIN / LAUNCHER, no data), `[music] handoff: qaplain "QA Song A" -> open (no search
  link)`; C-6. **Stale tap (edge case):** with the title page drawn, `adb shell pm disable
  app.tileshell.testclient.pandorastub/<its search activity>` then tap Pandora → `[music] handoff: pandora did not open at the
  title` (an `ActivityNotFoundException`), then the plain open of the stub; the entry stays; `pm enable` restores. If task 1
  recorded NO Pandora search link, the pandorastub declares none and the row asserts the plain open and `[music] handoff:
  pandora: search not passed` instead — chosen from task 1's record, never skipped. Uninstall both stubs.
- E14 **Media server** (Q1 C; T20-12, T20-13, C-32). Phase 17 E22's container (the pinned digest logged) with three MUSIC6-style
  MP3s of known durations: the Music app's server entry signs in through phase 17's `mediaServer.connect` (one token store,
  shared with Movies & TV) → `[music] server 10.0.2.2:8096: connected` in the MAIN ring; the library lists under its entry as
  albums / artists / songs (Y7); a track plays in `MusicService` with E3's assertions, the line reading `stream: connected
  http://10.0.2.2:8096/Audio/<id>/stream codec=mp3` (no `?` and no query string), and a known total on the scrubber
  (`nowplaying_total` = its length ± 1 s); the tile shows its face; a 5-s crossfade between two server tracks fades (MUSIC17's
  counting). Wrong password → `[music] server 10.0.2.2:8096: unauthorised`, no library; `docker stop` → `[music] server
  10.0.2.2:8096: unreachable`, the four local pivots intact (`music_fixtures` rows listed); `docker start` → reconnects on the
  next open. **Token hygiene:** the issued token is read from the server (`GET /Sessions` with the fixture admin's token, the
  session whose `Client` is the shell — T17-21) and `qa/phase-17/scripts/leak_scan.sh` with it and the fixture password finds
  zero matches in the saved ring slices, `adb logcat -d` and `qa/phase-20/**` (T20-13, C-32). ~~(its method, once phase 17
  writes it — until then this row is not runnable and says so)~~ SUPERSEDED 2026-09-23 by T20-12.
- E15 **Audio focus against another app.** Station playing; the stub plays a tone with `USAGE_MEDIA` → `dumpsys audio` shows
  the stub holding focus, the shell's session PAUSED, and it does NOT resume when the stub stops (phase 10 E9's rule); the
  reverse: stub playing, a station tap → the stub logs `onAudioFocusChange LOSS` and the station plays.
- E16 **Tess** (Q5 A; T20-2, T20-7, T20-9, C-30). Through phase 03's audio route (qa/phase-03/scripts/audio.sh, `say` with ids
  built by `utterances.py build`; each `say` saves `speech_dump` from its MARK and asserts the `:speech` ring's final line for
  that capture is not "asr: no speech", C-30): `radio_genre_jazz` ("play jazz radio") → `dumpsys media_session` shows the
  shell's session playing a station whose directory genre is jazz, reply "Playing QA Jazz One." (`reply_since`), `[music]
  search "jazz radio": station QA Jazz One`; `radio_station_news1` ("play QA News One") → that station, `[music] search "QA
  News One": station QA News One`. **"play radio" (T20-9):** favourite QA Jazz One and QA News One, play QA News One, stop;
  `radio_last` ("play radio") → QA News One (`[music] search "radio": station QA News One`); then fresh install (C-4), the
  fixture prefs re-set, re-favourite both with nothing played → `radio_last` → QA Jazz One (the first favourite). **Miss:**
  `radio_miss` ("play zzqx radio") → J5's miss reply naming it ("I couldn't find zzqx radio in your music"), `[music] search
  "zzqx radio": nothing in the library` (J5's form, `app/src/main/kotlin/app/tileshell/music/MusicService.kt:166`), the session
  unchanged (`dumpsys media_session` state and metadata as before the MARK). **J5 regression:** `qa/phase-03/scripts/j5.sh`
  re-run on this build → 8/8 (replaces "play <MUSIC6 fixture title> → that local track plays (the closed gap)"). **Keyguard:**
  with `locksettings set-pin 1234` and Cortana over the keyguard (phase 03 E10's setup) `radio_genre_jazz` plays with no
  activity shown (`dumpsys window` keeps the keyguard); restore `locksettings clear --old 1234`. **Hand-off:**
  `handoff_listen_stub` ("listen to qa artist on QA Plain") with E13b's plain stub installed → the plain open (`TileShellQa`
  logs MAIN, `[music] handoff: qaplain "qa artist" -> open (no search link)`); `am force-stop app.tileshell` + Home (C-6);
  uninstall the stub.
- E17 **Tile rule with a station (phase 10 E10 / E13 re-run).** From the seeded baseline (zero `-> assigned`): the MUSIC slot
  tile grows, shows the station's face and its controls drive it; the PHOTOS tile and the Camera row tile have unchanged
  bounds (phase 17 E16's assertions); Auxio playing (the pinned baseline tile) still moves the face to Auxio's tile; `am
  force-stop` + Home before each grid read (C-6).
- E18 **Budget and surface.** `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes; `dumpsys package
  app.tileshell` exported components equal qa/phase-03/exported-allowlist.txt exactly, no ADD (phase 03 E5's method).
  ~~plus this phase's ADDs (only with Q3 B)~~ SUPERSEDED 2026-09-23 by T20-1.
- E19 **Diagnostics (coverage).** Each line exists when its state does and the row named beside it asserts it; this row greps
  the union of `qa/phase-20/*/ring-*.txt` from this build's run (the rows' APK id matching), each pattern at least once (C-20):
  `[music] radio: directory fetched <n> stations` (E2), `radio: directory offline, cache from <date>` (E2), `radio: no cache yet
  (offline)` (E2), `radio: directory too large` (E3), `stream: connected <url without query> codec=<c>` (E3, E14), `stream:
  buffering` / `stream: playing` (E9), `stream: lost, retrying`, `stream: reconnected after <ms> ms`, `stream: gave up after
  <ms> ms` (E8, E11), `stream: unsupported hls` (E3, when HLS is not added), `stream: unsupported scheme=<s>` and `stream:
  unsupported playlist` (E3), `stream: cleartext refused` (E3b, under Q-D B / C), `stream: captive portal` (E10), `stream:
  metered` (E12), `sleep: end-of-track cleared (live item)` (E5), `catalogue "<q>": <n> | offline | error <code> | error
  connect` (E13), `handoff: <service> "<title>" -> <uri|intent>` (E13, E13b), `handoff: <app> "<title>" -> open (no search
  link)` (E13b, E16), `handoff: <service> did not open at the title` (E13b), `handoff: pandora: search not passed` (E13b, when
  task 1 recorded no search link), `handoff: no service installed for <title>` (E13), `server <host>: connected | unreachable |
  unauthorised` (E14), and the `search "<q>": station <name>` / `: <kind> <label>, n track(s)` / `: nothing in the library` forms
  (E16; J5's, `app/src/main/kotlin/app/tileshell/music/MusicService.kt:166,169`). ~~`search: "<query>" -> <station|track|none>`~~
  SUPERSEDED 2026-09-23 by T20-2 (J5's line form).
- E20 **Screen off (the wake-mode ADD).** Station playing; `input keyevent KEYCODE_SLEEP` for 60 s → the AudioFlinger track
  is in state A throughout (sampled every 10 s), `dumpsys power` lists the player's wake lock and `dumpsys wifi` its Wi-Fi
  lock; `wake_device` and its `Awake` before the next step (C-25); the same for a local MP3 (phase 10's owed screen-off half,
  closed by task 2). Start being killed is phase 10 E7's
  row and is unchanged by this phase: `MusicService` runs in the main process (no `android:process` in the manifest), so
  that row's method — not `am kill`, which never kills a process holding a foreground service — is phase 10's to state
  when its gate runs it.
- ~~E21 **The real directory** (network on, the build with real defaults) …~~ SUPERSEDED 2026-09-23 by T20-10: moved to P10
  (the live directory is a phone fact; no AVD row reaches the live internet, C-29); the mirror rule's failable check is the
  JVM test of Decisions "the station directory" (a fake DNS answer for `all.api.radio-browser.info` → a host list → one picked).

**Phone-only:**
- P1 Real services installed (Pandora, YouTube Music, whatever the S25U has): each "Listen on <service>" opens that app on its
  own search for the title (Q1 Decision) — or, for a music app with no recorded form, its plain open (`dumpsys activity
  activities` + screencap), `record`ed per service (C-26); a signed-out service shows its own sign-in, and nothing of ours
  steps around it.
- P2 A title playing in a real service lands on THAT service's tile if pinned and nowhere otherwise (phase 10 E11 / E12 on the
  phone).
- P3 Thirty minutes of a station with the screen off, on Wi-Fi and on mobile data, One UI's power saving default: still playing
  at the end (AudioFlinger), the wake and Wi-Fi locks in `dumpsys power`, data used from `dumpsys netstats` `record`ed for H8.
- P4 Headset and Bluetooth buttons with a station (phase 10 E8 on the phone): pause / play; next / previous step through
  favourites.
- P5 A real captive portal (a café or hotel network) when one is at hand: the E10 message, then Android's own sign-in, then the
  station plays; `record`ed when it happens (C-26).
- P6 Battery per hour streaming, off USB (phase 07 P1's method), `record`ed for H8.
- P7 One UI's Data Saver and "metered" flag on the phone's own Wi-Fi settings: the E12 lines appear.
- P8 Tess's station phrases with the real microphone (phase 03 P1's method) and over the real keyguard with fingerprint
  unlock left untouched (phase 03 P6).
- P9 **Pandora on the S25U (T20-8).** Pandora installed, "Listen on Pandora" from "a title" page: if task 1 recorded a search
  form → `com.pandora.android` resumed on its search results for the title (screencap; the resumed activity and intent data
  from `dumpsys activity activities`) and `[music] handoff: pandora "<title>" -> <uri>`; if task 1 recorded none → Pandora opened
  and `[music] handoff: pandora: search not passed`. Gated on the recorded form (the line and the resumed package); the landing
  screen is `record`ed (C-26).
- P10 **The real directory (T20-10; was E21).** The S25U, network on, the build with the real defaults (no `qa_*` prefs): the
  radio page fetches from a real mirror — `[music] radio: directory fetched <n> stations` with n > 1000, a search for "jazz"
  returns > 0 rows, and the first result plays for 10 s (AudioFlinger track in state A); the HTTP status and the mirror used are
  `record`ed; a failure is recorded with both, never hidden.
- P11 **No FM receiver (T20-17; device evidence for the Q3 negative).** `adb shell cmd package has-feature
  android.hardware.broadcastradio` → false; `adb shell service list | grep -ci broadcastradio` → 0; `adb shell pm list
  packages | grep -i fm` → none; each `record`ed.

**NEEDS-HUMAN:** H1 *accept* — the live now-playing form (Y1); H2 *accept* — the radio pivot's arrangement (Y2); H3 *accept*
— the metered line (Y3) ~~and, under Q4 B, the ask-once dialog~~ (SUPERSEDED 2026-09-23 by T20-1); H4 *accept* — the reconnect
window (Y4); H5 *accept* — the catalogue and "Listen on" pages (Y5; P4, phase 17's Y8 / Y9 forms — R11 measured no Browse half,
so no fidelity row can close; T20-14) ~~*fidelity* … against R11 §Movies & TV's forms~~; H6 *accept* — the order and naming of
"Listen on" entries when several services are installed; H7 *accept* — Tess's reply wording (Y6); H8 *accept* — data and
battery per hour (P3 / P6); H9 *accept* — Music's satellites stay the four (Songs / Albums / Artists / Playlists); Jeremy can
swap Playlists for Radio on the phone (T20-3) ~~the "Radio" App Shortcut and, under Q3 B, the Radio tile's face~~; H10 *accept*
— the media-server music view: its entry point, the albums / artists / songs grouping and the server-name line (Y7; T20-14).

## Edge cases
- Network off, flaky or captive when the app opens: the directory shows its cache with the offline line; with no cache yet, an
  empty state that names the cause ("No connection yet — stations will appear when there is one"), not a blank pivot; a
  station tap with no network shows the E8 gave-up state at once, not after 60 s.
- A station that stops for good (server gone, 404, DNS failure) versus one that stops and returns: E8 / E11; a 30x redirect to
  another host (`DefaultHttpDataSource` cross-protocol redirects allowed, the final URL logged without its query string); a
  playlist file (.pls / .m3u) in place of a stream — resolved to its first http / https entry or refused with `stream:
  unsupported playlist` (E3, T20-5).
- A station that changes codec mid-connection (rare; a progressive source cannot follow it): treated as a stop, the reconnect
  lands on the new codec (E11's second half).
- A station sending no ICY metadata: the title line stays the station name; one sending a StreamTitle every second: the tile
  republishes at most as `MusicFeed` already throttles position ticks (the 3-s rule in phase 01's Change Log), never a flip
  storm.
- The sleep timer's end-of-track chosen for a local track, then the queue reaching a station: the armed timer is cleared with
  `[music] sleep: end-of-track cleared (live item)` when the item goes live, because it can never fire (E5).
- Crossfade set, a station followed by a local track in the queue: no fade out of the stream (the stream never ends by
  itself); next → the local track starts clean.
- The streaming app is not installed, or was uninstalled since the title page was drawn: the "Listen on" entry is gone on the
  next draw, and a stale tap shows "That app isn't installed any more" rather than a resolver error; the app is installed but
  signed out: its own sign-in shows, nothing of ours intervenes.
- A search link the service no longer answers (`ActivityNotFoundException` or the app opening on its home): logged as
  `handoff: <service> did not open at the title`, the entry stays (the app IS installed), H6 records it; the
  `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` form is tried second when the service answers it, else the plain open (E13b's stale
  tap proves the `ActivityNotFoundException` half).
- Audio focus: the other app takes focus and never gives it back (phase 10 E9); a call during a station (pause; no resume by
  itself); headphones out mid-stream (becoming-noisy pause, as built).
- Metered network appearing mid-stream (Wi-Fi drops to mobile data): the E12 line appears without stopping the stream (Q4 A).
  ~~under Q4 B / C the stream stops at the switch with the line saying why~~ SUPERSEDED 2026-09-23 by T20-1.
- A phone with no local audio at all and no network: the four built pivots' empty states unchanged (phase 10 E5); the radio
  pivot's offline empty state; the app never crashes on an empty everything.
- The MUSIC slot re-pointed to another player: the shell's Music app keeps its radio and streaming pages and keeps working;
  Tess's "play jazz radio" then goes to the slot app's session (phase 03's rule) and, if that app cannot answer it, says so.
- A media-server track whose server goes away mid-play (Q1 C): the E8 path, then `[music] server <host>: unreachable`; the
  server library cached as the directory is, so browsing survives the outage.
- Plain http (Q-D: A, C-16): under A an http station plays; under B / C it is listed but refused with `[music] stream:
  cleartext refused` and the page says "This station can't be played securely"; a station redirected from https to http is
  judged by its final URL under the same branch (E3b).
- APK updated (`adb install -r`) while a station plays: the service stops cleanly, no orphan session (phase 17's rule).
- Liveness (N-01): after `adb reboot` (boot-completed poll, then `wake_device` and its `Awake` before the next tap — C-25) and
  Device care, favourites and the cache are intact; after a reboot nothing plays by itself (phase 10: no timer read back off
  disk, nothing resumes). ~~and the setting~~ SUPERSEDED 2026-09-23 by T20-1 (no setting, Q4 A).

## QA evidence
_None yet._
