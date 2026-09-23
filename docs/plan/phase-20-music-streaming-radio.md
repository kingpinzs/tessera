---
phase: 20
slug: music-streaming-radio
status: DRAFT   # written 2026-09-23 from PLAN.md's A11 amendment; interview pending; ADDS to phase 10's FINAL part, never rebuilds it
depends-on: [03, 10, 17]
---

# Phase 20 — Music streaming and Radio

## Goal
Phase 10's Music app (the built, FINAL Groove-style player) gains the two things its Q1 note said A11 had taken off the
table: a **streaming side** and **Radio**. When this phase is done, the Music app can reach music that is not on the phone —
by whichever forms the interview rules: internet radio stations played in the shell's own `MusicService`, a music catalogue
whose titles hand off to the streaming apps the user already has (the same hand-off phase 17 builds for Movies & TV, reused
rather than rebuilt), and the user's own media server's music library — and the Music tile, the notification transport, the
sleep timer and equaliser, and Tess follow whichever of those sounds through the shell's player. Everything that phase 10
built keeps working exactly as verified (MUSIC6–10, MUSIC17); every change to its part is a recorded ADD in the INDEX Change
Log (Hard Rule 16). Offline stays preferred (P6, A11 as amended): what can be cached is cached and browsable with no network,
and the local library never needs one.

## Scope
**In:**
- Radio, per Q2 / Q3: a station directory (browse, search, favourites), the stations played as live items through
  `MusicService`, with the live-stream forms of the now-playing screen, the tile face and the `•••` menu that a stream with no
  end needs; the directory cached for offline browsing; reconnect, metered-data and captive-portal behaviour.
- Streaming, per Q1: the music analogue of phase 17's hand-off — a catalogue lookup and "Listen on <service>" opening the
  installed service's app at the title, the user signed in inside that app; and, if ruled, the user's media server's music
  (Jellyfin or Plex, phase 17's connection) browsed as albums / artists / songs and played in `MusicService`.
- Tess commands for the above, per Q5 (ADDs to phase 03's matcher and to `MusicService`'s session callback).
- Where Radio lives in the app (a pivot in Music or its own app-list entry, Q3), the Radio App Shortcut under phase 11's Q1
  standing rule, Settings / checklist rows if Q4 adds a setting, diagnostics lines for every silent state, the re-runs of the
  phase 10 rows this touches, the exported-components and APK-budget checks.
**Out (explicitly):** the TV-channels app (PLAN.md 2026-09-23 scope add; R13 first, then its own phase); building any
service's own client — no Spotify / YouTube Music / Apple Music / Tidal / Deezer playback inside the shell, no scraping of a
service's catalogue; bypassing any service's DRM, sign-in or paywall; ripping, recording, caching or time-shifting a stream to
a file (the Voice Recorder records the microphone, not the mixer; nothing here writes audio); an FM tuner (W10M's separate FM
Radio app needed a receiver the S25 Ultra does not expose); a music store; Microsoft's cloud services; any interim "list-only"
Radio or "shortcuts-only" streaming build if the interview rules a fuller form (Hard Rule 16); any change to phase 10's
measured now-playing geometry for local tracks (R8, H-M1 signed off).

## Decisions
- 2026-09-23: Interview Q5 — Tess gets radio and streaming phrases (Jeremy, to the question's Spotify example: "I dont ever use
  spotify but I do use other apps"; read as A with the hand-off naming any installed music app, Jeremy can overrule): "play
  <station>", "play <genre> radio", "play radio" (the last favourite), resolved by code against the cached directory (P6) and
  played through the session path phase 03 uses over the keyguard; and "listen to <x> on <app>" as a hand-off to that
  installed music app through phase 17's StreamingHandoff. The hand-off table is seeded with the music apps Jeremy uses (asked
  2026-09-23; his list lands here), each with its in-app search deep link and a phone row; any other installed music app gets
  a plain open hand-off. Spotify is not a priority entry.
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
  are Q5's. **Observed in code, 2026-09-23:** `ActionLayer.playMusic` sends `transportControls.playFromSearch(query)` to the
  active music controller, and `MusicService`'s `callback` overrides only `onConnect` / `onCustomCommand` — it does not
  override `MediaSession.Callback.onSetMediaItems`, which is where a legacy `playFromSearch` arrives in Media3 (the item
  carries `MediaItem.RequestMetadata.searchQuery` and no URI). So "play <song>" against the shell's own player resolves
  nothing today. That is a phase 03 / phase 10 integration gap found here, flagged to the lead; if Q5 rules Tess radio
  commands in, the one override that answers them resolves library queries too (build task 7) and is recorded as an ADD to
  phase 10's part closing that gap. If Q5 rules them out, the gap is reported and left to the lead, not silently absorbed.
- 2026-09-22: **phase 11 Q1 standing rule**: Music declares Songs / Albums / Artists / Playlists as static App Shortcuts. A
  Radio pivot (Q3) adds "Radio" to that list — an ADD to phase 11's part, Change Log when built.
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
- 2026-09-23 (agent): **the station directory** (only if Q2 rules internet radio): radio-browser.info — an open, community
  directory with a free JSON API, no key and no Google. Its published etiquette (resolve a mirror through DNS rather than
  hard-coding one; send a User-Agent naming the app) is followed and re-read at build start, with the terms, and recorded
  here per P5. Fetched with `HttpURLConnection` the way `OpenMeteoProvider.fetch` does — no HTTP library is added. Cached to
  the app's private files directory with `AtomicFile`, the shape `WeatherFeed` uses for its report, so the browse pages and
  search work with no network; favourites are the user's own file written with a temp file and a rename like
  `PlaylistStore`'s music_playlists.json. File names are recorded here at build start. The cache is refreshed on open when
  older than a day and on demand; a fetch failure keeps the cache and says so (`[music] radio: directory offline, cache from
  <date>`), never an empty list.
- 2026-09-23 (agent): **the music catalogue** (only if Q1 rules a catalogue): the music analogue of phase 17's "public film
  database", chosen at build start with the same licence / terms check and recorded here (P5: not a Google API; MusicBrainz —
  open data, no key — is the first candidate, with Cover Art Archive for artwork; its 1-request-per-second etiquette is
  honoured). Catalogue rows are cached per search for the session only; a title's "Listen on <service>" list comes from
  phase 17's installed-app discovery. Which services appear is never a fixed list: every installed app answering the
  service's own deep-link form recorded at build start, or Android's `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` with
  `EXTRA_MEDIA_FOCUS`, is offered, discovered at run time through `PackageManager` the way phase 17 does it. Deep-link forms
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
  foreground service keeps its stream (the platform exempts foreground services), and the line says so. Q4 decides whether a
  Wi-Fi-only setting exists on top of this.
- 2026-09-23 (agent): **captive portal.** A network Android has flagged (`NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL`,
  or a validated-false default network) makes a station tap show "Sign in to this Wi-Fi network first" and starts no stream;
  the sign-in itself is Android's own captive-portal notification — the shell opens no page of its own (Android limits) —
  and the directory falls back to its cache.
- 2026-09-23 (agent): **the Radio pivot** (if Q3 keeps Radio inside Music): `MusicPivot` gains a fifth entry titled "radio"
  (R3's lowercase pivot form, as the four built ones), `MusicCollection.page` gains its case, and the header strip's
  scroll-no-further-than-needed rule (phase 10 task 6 amendment, MUSIC7) already covers a fifth header — E1 re-measures it. Its
  list rows are the app list's (R3 C2 / R6 §5.1.4) as the other pivots are; favourites first under a "favourites" letter-less
  group, then a browse row set (search, by genre, by country) — the arrangement is a **P4 design** (Groove's Radio pivot held
  artist stations, not a directory; no measurement exists), H2. A hold on a station offers add-to / remove-from favourites and
  pin-to-Start (the phase 11 burst carries it once phase 11 is in).
- 2026-09-23 (agent): **harness contracts.** Every new node a row reads carries its own test tag under the
  `testTagsAsResourceId` root `MusicActivity` already sets (phase 10's `music_pri:` / `music_sub:` lesson); every silent
  state writes a `[music]` diagnostics line (wording in E19). The tags the rows below name that do not exist today
  (`radio_row:`, `radio_fav:`, `nowplaying_live`, `nowplaying_live_caption`, `nowplaying_metered`, `catalogue_row:`,
  `handoff_service:`) are this phase's own, added by the task that draws each node. Drivers: qa/phase-20/scripts/lib.sh → symlink to
  qa/phase-03/scripts/lib.sh, plus qa/phase-01/scripts/music_lib.sh (its `music_fixtures`, `music_open`, `goto_pivot`);
  evidence under qa/phase-20/. The directory and catalogue base URLs are debug-build `BuildConfig` fields defaulting to the
  real endpoints in every build type; the release APK cannot be redirected. That is a fixture route for a data source, not a
  request-injection path (phase 03's rule pins Cortana's inputs; nothing here takes a Cortana request).
- 2026-09-23 (agent): **the two kinds of NEEDS-HUMAN row are labelled** as phase 17 labels them: *fidelity* (matches a
  measurement, judged on the phone) and *accept* (a P4 design or an approximation; Jeremy accepts or overrules).
  qa/phase-20/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.

### Approximations (each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Live now-playing form: live bar, "LIVE" caption, omitted end-of-track entry | P4 design, no source | R8's screen with the scrubber and labels replaced as described | H1 |
| Y2 | Radio pivot rows and the favourites / browse arrangement | P4 design, no source | app-list rows (R3 C2 / R6 §5.1.4); MusicMetrics for the header | H2 |
| Y3 | Metered-data line, wording and placement | P4 design | one caption line under the metadata block | H3 |
| Y4 | Reconnect window and back-off (60 s; 2/4/8/16/30 s) | agent pick | as stated | H4 |
| Y5 | Catalogue and "Listen on" pages (if Q1 rules a catalogue) | phase 17's hand-off pages, R11 §Movies & TV pending | phase 17's own Y rows | H5 |
| Y6 | Tess reply wording for stations ("Playing <station>.") | phase 03's reply form | as stated | H7 |

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
re-verified early, and so nothing conditional is started before its question is ruled. Tasks marked *(Qn)* are built in the
form the interview rules, or not at all.

1. **Build-start checks, recorded in Decisions.** Media3 1.8.0's ICY field mapping (`IcyHeaders` / `IcyInfo` into
   `MediaMetadata`); the directory's terms, mirror etiquette and User-Agent rule; the catalogue source's terms and rate limit
   (Q1); each service's deep-link form and whether it answers `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` (Q1); the count of HLS
   stations in the ruled directory (whether `media3-exoplayer-hls` is added); the `cmd netpolicy` metered-network id form on
   the AVD (E12); the `BuildConfig` field names for the fixture endpoints; the cache and favourites file names.
2. **The shared player's wake mode** (ADD to phase 10's part, Change Log): `WAKE_LOCK` in the manifest,
   `setWakeMode(C.WAKE_MODE_NETWORK)` on the `ExoPlayer.Builder` in `MusicService.onCreate`. Re-runs MUSIC7 and MUSIC17 on the
   same build (nothing else in the service changed yet) and adds E20's screen-off half.
3. **Station playback** *(Q2 A or C, Q3 A or B)*: a station `MediaItem` built beside `MusicService.mediaItem(track)` — the
   stream URL as its URI, the station name as `albumTitle`, the station logo as `artworkUri`, no `durationMs`; a play path
   beside `MusicPlayer.play(queue, startIndex)` (which takes `Track`s) that sets a station queue (the favourites in order, so
   next / previous step through them like presets); live metadata as Decisions; reconnect as Decisions; the metered and
   captive-portal states; the `[music] stream:` lines. Crossfade needs no change (`considerPreparing` returns on
   `C.TIME_UNSET`); the sleep timer's minute choices work as built; end-of-track is hidden by task 4.
4. **The live now-playing form** (Y1, P4): in `NowPlayingPage`, when the session's item is live (`MusicPlayer.durationMs`
   0 and the item marked live), the scrubber draws as the live bar with no `pointerInput`, the labels become "LIVE", and
   `moreEntries` omits `SleepTimer.Choice.END_OF_TRACK`; a local track keeps every measured value (MUSIC7 re-run). Tags:
   `nowplaying_live`, `nowplaying_live_caption`.
5. **The directory** *(Q2 A)*: fetch, parse, cache (`AtomicFile`), refresh rules, search / genre / country, favourites store;
   pure grouping and search on the JVM (as `MusicGrouping` / `MusicCollection` are), the platform part thin.
6. **Radio in the app** *(Q3)*: A — the fifth pivot (`MusicPivot.RADIO`, `MusicCollection.page`, rows, hold menu, the
   header-strip re-measure) and the "Radio" App Shortcut (ADD to phase 11's part); B — a launcher activity like
   `MusicActivity` sharing `MusicPlayer` / `MusicService`, its own tile, `AppUninstall.canUninstall` exclusion, the exported
   allow-list ADD, the phase 15 task 0 routing extension; C — a RADIO slot through `LayoutStore.assignSlotOnce` (marker
   `slot:radio:v1`, never over a user's choice — phase 16 task 1's fix applies) and an "open in <app>" hand-off.
7. **Tess** *(Q5)*: phrases in `CommandMatcher` (ADD to phase 03's part) mapped to `Request.PlayMusic`'s query or a new
   request kind, `LockGate` treating them as phase 03 treats music; `MusicService`'s callback gains `onSetMediaItems`
   resolving `RequestMetadata.searchQuery` deterministically — favourites and directory by name / genre first (only with
   task 5), then the library's songs / artists / playlists (closing the observed phase 03 / 10 gap; ADD to phase 10's part).
   No LLM in the path (P6).
8. **Streaming side** *(Q1 B or C)*: the catalogue search page and the title page in the Music app, calling phase 17's
   hand-off for the "Listen on <service>" list and the open-at-title call; *(Q1 C)* the media server's music through phase
   17's connection, listed as albums / artists / songs in the Music idiom, its tracks played in `MusicService` as network
   items with a known duration (so the built now-playing form applies, not the live one; crossfade and end-of-track work).
   Transcoding versus direct play is a build-time call recorded here.
9. **Settings + checklist** *(Q4 B)*: the "Stream only on Wi-Fi" setting where the playback settings live (the now-playing
   `•••`, phase 10 task 10's rule: no separate music settings page). No permission row (Decisions).
10. **Re-runs and regressions.** MUSIC6 (pivot count and strip), MUSIC7 (`•••` with a local track), MUSIC17 (a station in
    the queue never fades), phase 10 E10 / E13 with a station (E17 here), phase 03 E10's "play <song>" half (task 7), the
    exported allow-list (phase 03 E5's method; unchanged unless Q3 B) and the APK budget (E18).
11. **QA fixtures.** qa/phase-20/scripts/icy_server.py (host HTTP server: a looping MP3 made as MUSIC6's fixtures were, sent
    with `icy-name`, `icy-metaint` and a StreamTitle that changes every 20 s; endpoints for AAC, stop-after-N-seconds and a
    codec switch on reconnect), directory_server.py (the directory API's JSON shape with the fixture stations), catalogue
    fixtures for Q1, portal_server.py (returns 200 with a page for Android's captive-portal probe), and the stub hand-off
    APK (same kind as phase 17 E9's fixture: declares each service's deep-link form and `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`,
    logs every received intent to the `TileShellQa` logcat tag, and can play a tone with `USAGE_MEDIA` for E15).

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps follow RV13.
Harness: qa/phase-20/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh, plus qa/phase-01/scripts/music_lib.sh; every row
stamps its driver blob and the installed APK. Device: the AOSP AVD tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google,
`sdk_phone64_x86_64` userdebug), with **no streaming app installed** — real services are P rows. "Diagnostics" is read with
phase 01's command; `[music]` lines are the ones asserted.

**Fixtures.** The AVD reaches the host at 10.0.2.2. A debug APK with the directory and catalogue `BuildConfig` fields pointed at
`http://10.0.2.2:8080/` (directory_server.py: six stations — two "jazz", two "news", one "rock", one HLS — with
`url_resolved` values on icy_server.py at `http://10.0.2.2:8000/<name>`) and, for E21 only, a build with the real defaults.
icy_server.py streams a 90-second 440 Hz MP3 (ffmpeg, `lavfi sine`, the method behind qa/phase-01/MUSIC6-fixtures) in a loop,
`icy-name: QA Jazz One`, `icy-metaint: 16000`, StreamTitle "QA Song 1", "QA Song 2", … every 20 s; `/aac` sends AAC;
`/stops` closes after 20 s; `/switch` serves MP3 on the first connect and AAC on the next. Audio is asserted the way MUSIC17
asserted it: `adb shell dumpsys media.audio_flinger` tracks in state A on the player's own audio session (the
`playback service started (audio session N …)` line names it). The stub APK is installed only for the rows that say so and
uninstalled by them. Rows that need the six local MP3s call `music_fixtures`.

**Emulator:**
- E1 **Radio in the app** *(Q3 A)*. `music_open`; the dump shows five `music_pivot_header:*` nodes with "radio" last; on
  songs the strip is as MUSIC7 asserted (playlists' right edge at 1080 px of a 1080-px screen); `goto_pivot radio` → the
  radio header whole, `[music] pivot settled on radio`; Back returns to songs at exactly MUSIC7's geometry. *(Q3 B)*: the
  "Radio" entry appears under R in the app list (phase 02's regress.sh pattern, `applist_row:` tags), its hold menu offers Pin
  to Start and NOT Uninstall (qa/phase-01/UNINSTALL's method), `am start -n` on its component (named at build start) resumes
  it.
  *(Q3 C)*: `pm clear`, open Start → `[layout] assignSlotOnce slot:radio:v1 RADIO -> … -> assigned` (phase 17 E1's method,
  including the upgrade half). The Radio App Shortcut *(Q3 A / B)*: phase 11's shortcut-listing method shows "Radio" beside
  Songs / Albums / Artists / Playlists.
- E2 **Directory, cache, favourites** *(Q2 A)*. Fresh install: the radio page lists the six fixture stations (`radio_row:<id>`
  tags, `music_pri:` / `music_sub:` text = name and genre), `[music] radio: directory fetched 6 stations`; search "jazz"
  → 2 rows; by genre "news" → 2; `cmd connectivity airplane-mode enable` → force-stop and reopen → the same six rows from
  the cache and `[music] radio: directory offline, cache from <date>`; hold a station → add to favourites; `am force-stop`
  and reopen → it is first under favourites (`radio_fav:<id>`); restore airplane mode.
- E3 **A station plays in `MusicService`**. Tap "QA Jazz One" → `dumpsys media_session` shows `app.tileshell` PLAYING with
  metadata title "QA Song 1" and album "QA Jazz One"; AudioFlinger shows one track in state A on the player's session;
  `[music] stream: connected http://10.0.2.2:8000/jazz1 codec=mp3`; the now-playing screen is on top (`nowplaying_root`);
  Back → the collection, still playing (phase 10 task 7's rule). `/aac` plays the same way with `codec=aac`; the HLS station
  plays only if `media3-exoplayer-hls` was added (task 1's count), otherwise it is listed with "can't play this station" and
  `[music] stream: unsupported hls` (no crash; `logcat -d -s AndroidRuntime` empty of `app.tileshell`).
- E4 **Live metadata and the tile.** 25 s after E3's connect, the session's title reads "QA Song 2" (`dumpsys media_session`)
  within 5 s of the fixture's switch (icy_server.py logs its switch time); the Music tile's face shows "QA Song 2" (the
  `tile_*` tags phase 10 E10 reads) and `[music] now playing app.tileshell title=QA Song 2 …`; the album line stays "QA
  Jazz One". Pause from the tile's `tile_control:<id>:PAUSE` → PAUSED (phase 10 E13's method).
- E5 **The live form** (Y1). With a station playing: `nowplaying_live` present, `nowplaying_scrubber` absent, the caption
  reads "LIVE" (`nowplaying_live_caption`), `nowplaying_total` absent; `adb shell input tap` across the live bar's bounds
  changes nothing (`dumpsys media_session` position keeps advancing, no seek line); open `•••` → sleep → `music_menu_sleep:eot`
  absent, the four minute entries present; set crossfade 5 s (`music_menu_crossfade:5`), then a local track in the queue
  before the station and let it run out → `[music] crossfade: no fade after …` or `prepared` never followed by `fade
  started` into the station, AudioFlinger never two tracks of ours; play a local track → `nowplaying_scrubber` and
  `nowplaying_total` back, MUSIC7's geometry checks pass on the same build.
- E6 **Sleep timer on a stream.** "In 15 minutes" with a station playing: still playing at 10 minutes (AudioFlinger), paused
  900 s ± 2 s after the `armed for 15 minute(s) at elapsed …` line (MUSIC9's method), the stream's track gone from
  AudioFlinger, the session PAUSED, the notification still present.
- E7 **Equaliser on a stream.** Choose a preset → AudioFlinger shows the Equalizer effect enabled on the same session as the
  streaming track (MUSIC9's method); Off disables it.
- E8 **Network off and back.** Station playing; `cmd connectivity airplane-mode enable` → within 5 s the title line reads
  "Reconnecting…" (`nowplaying_track` text) and `[music] stream: lost, retrying`; `airplane-mode disable` at 30 s → playing
  again within 10 s with no tap (`[music] stream: reconnected after N ms`), AudioFlinger track back. Second pass: airplane on
  for 120 s → at ~60 s "This station isn't answering" (`nowplaying_track`), session PAUSED, `[music] stream: gave up after
  60000 ms`, no crash; airplane off → stays paused until a tap (RV12 restore).
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
- E12 **Metered data** *(Q4)*. `cmd netpolicy set metered-network <id> true` (id per task 1) → the now-playing screen shows
  "Streaming over mobile data" (`nowplaying_metered` text) and the station still plays; `cmd netpolicy set
  restrict-background true` → still playing (foreground service), the line says "Data Saver is on"; *(Q4 B)* with "Stream only
  on Wi-Fi" on, a station tap on the metered network asks once (dump) and does not start until confirmed; *(Q4 C)* the tap
  shows "Wi-Fi only" and starts nothing. Restore both netpolicy settings.
- E13 **Streaming hand-off** *(Q1 B / C)*. Search "qa artist" against the fixture catalogue → rows with title, artist and
  artwork (`catalogue_row:` tags); open a title → NO "Listen on" entry with no stub installed (`handoff_service:` absent,
  `[music] handoff: no service installed for <title>`); `adb install` the stub → the entry appears (the discovery is live, not
  cached at first open); tap → `adb logcat -d -s TileShellQa` shows the intent the stub received: its action, and a data URI
  or `EXTRA_MEDIA_FOCUS` query naming the title and artist; `dumpsys activity activities` shows the stub resumed; the shell
  started no player (`dumpsys media_session` unchanged); uninstall the stub → the entry is gone.
- E14 **Media server** *(Q1 C)*. Phase 17's media-server fixture (its method, once phase 17 writes it — until then this row is
  not runnable and says so): the server's music library lists under its entry as albums / artists / songs; a track plays in
  `MusicService` with E3's assertions and a known total on the scrubber (`nowplaying_total` = its length); the tile shows its
  face; a 5-s crossfade between two server tracks fades (MUSIC17's counting).
- E15 **Audio focus against another app.** Station playing; the stub plays a tone with `USAGE_MEDIA` → `dumpsys audio` shows
  the stub holding focus, the shell's session PAUSED, and it does NOT resume when the stub stops (phase 10 E9's rule); the
  reverse: stub playing, a station tap → the stub logs `onAudioFocusChange LOSS` and the station plays.
- E16 **Tess** *(Q5)*. Through phase 03's audio route (qa/phase-03/scripts/audio.sh, utterances.py): "play jazz radio" →
  `dumpsys media_session` shows the shell's session playing a station whose directory genre is jazz, reply "Playing QA Jazz
  One." (diagnostics); "play QA News One" → that station; "play radio" → the first favourite; "play <MUSIC6 fixture title>"
  → that local track plays (the closed gap); with `locksettings set-pin 1234` and Cortana over the keyguard (phase 03 E10's
  setup) "play jazz radio" plays with no activity shown (`dumpsys window` keeps the keyguard); *(Q1 B / C and Q5 A)* "listen
  to qa artist on <stub>" → the stub's intent as E13; restore `locksettings clear --old 1234`.
- E17 **Tile rule with a station (phase 10 E10 / E13 re-run).** The MUSIC slot tile grows, shows the station's face and its
  controls drive it; the PHOTOS tile and the Camera row tile have unchanged bounds (phase 17 E16's assertions); Auxio playing
  (a pinned tile) still moves the face to Auxio's tile.
- E18 **Budget and surface.** `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes; `dumpsys package
  app.tileshell` exported components equal qa/phase-03/exported-allowlist.txt plus this phase's ADDs (only with Q3 B),
  exactly (phase 03 E5's method).
- E19 **Diagnostics.** Each line exists when its state does and the rows above assert it: `[music] radio: directory fetched
  <n> stations`, `radio: directory offline, cache from <date>`, `stream: connected <url> codec=<c>`, `stream: buffering`,
  `stream: playing`, `stream: lost, retrying`, `stream: reconnected after <ms> ms`, `stream: gave up after <ms> ms`, `stream:
  unsupported <kind>`, `stream: captive portal`, `stream: metered`, `handoff: <service> <title>`, `handoff: no service
  installed for <title>`, `search: "<query>" -> <station|track|none>`.
- E20 **Screen off (the wake-mode ADD).** Station playing; `input keyevent KEYCODE_SLEEP` for 60 s → the AudioFlinger track
  is in state A throughout (sampled every 10 s), `dumpsys power` lists the player's wake lock and `dumpsys wifi` its Wi-Fi
  lock; the same for a local MP3 (phase 10's owed screen-off half, closed by task 2). Start being killed is phase 10 E7's
  row and is unchanged by this phase: `MusicService` runs in the main process (no `android:process` in the manifest), so
  that row's method — not `am kill`, which never kills a process holding a foreground service — is phase 10's to state
  when its gate runs it.
- E21 **The real directory** (network on, the build with real defaults). The radio page fetches from the real mirror: `[music]
  radio: directory fetched <n> stations` with n > 1000, a search for "jazz" returns > 0 rows, and the first result plays for
  10 s (AudioFlinger track in state A). Network-dependent by nature; a failure is recorded with the HTTP status and the mirror
  used, never hidden.

**Phone-only:**
- P1 Real services installed (Spotify, YouTube Music, whatever the S25U has): each "Listen on <service>" opens that app at the
  title (`dumpsys activity activities` + screencap), recorded per service; a signed-out service shows its own sign-in, and
  nothing of ours steps around it.
- P2 A title playing in a real service lands on THAT service's tile if pinned and nowhere otherwise (phase 10 E11 / E12 on the
  phone).
- P3 Thirty minutes of a station with the screen off, on Wi-Fi and on mobile data, One UI's power saving default: still playing
  at the end (AudioFlinger), the wake and Wi-Fi locks in `dumpsys power`, data used from `dumpsys netstats` recorded for H8.
- P4 Headset and Bluetooth buttons with a station (phase 10 E8 on the phone): pause / play; next / previous step through
  favourites.
- P5 A real captive portal (a café or hotel network) when one is at hand: the E10 message, then Android's own sign-in, then the
  station plays; recorded when it happens.
- P6 Battery per hour streaming, off USB (phase 07 P1's method), recorded for H8.
- P7 One UI's Data Saver and "metered" flag on the phone's own Wi-Fi settings: the E12 lines appear.
- P8 Tess's station phrases with the real microphone (phase 03 P1's method) and over the real keyguard with fingerprint
  unlock left untouched (phase 03 P6).

**NEEDS-HUMAN:** H1 *accept* — the live now-playing form (Y1); H2 *accept* — the radio pivot's arrangement (Y2); H3 *accept*
— the metered line (Y3) and, under Q4 B, the ask-once dialog; H4 *accept* — the reconnect window (Y4); H5 *fidelity* — the
catalogue and "Listen on" pages against R11 §Movies & TV's forms, on the phone (Y5); H6 *accept* — the order and naming of
"Listen on" entries when several services are installed; H7 *accept* — Tess's reply wording (Y6); H8 *accept* — data and
battery per hour (P3 / P6); H9 *accept* — the "Radio" App Shortcut and, under Q3 B, the Radio tile's face.

## Edge cases
- Network off, flaky or captive when the app opens: the directory shows its cache with the offline line; with no cache yet, an
  empty state that names the cause ("No connection yet — stations will appear when there is one"), not a blank pivot; a
  station tap with no network shows the E8 gave-up state at once, not after 60 s.
- A station that stops for good (server gone, 404, DNS failure) versus one that stops and returns: E8 / E11; a 30x redirect to
  another host (`DefaultHttpDataSource` cross-protocol redirects allowed, the final URL logged); a playlist file (.pls / .m3u)
  in place of a stream — resolved to its first entry or refused with `stream: unsupported playlist`.
- A station that changes codec mid-connection (rare; a progressive source cannot follow it): treated as a stop, the reconnect
  lands on the new codec (E11's second half).
- A station sending no ICY metadata: the title line stays the station name; one sending a StreamTitle every second: the tile
  republishes at most as `MusicFeed` already throttles position ticks (the 3-s rule in phase 01's Change Log), never a flip
  storm.
- The sleep timer's end-of-track chosen for a local track, then the queue reaching a station: the armed timer is cleared with
  a diagnostics line when the item goes live, because it can never fire.
- Crossfade set, a station followed by a local track in the queue: no fade out of the stream (the stream never ends by
  itself); next → the local track starts clean.
- The streaming app is not installed, or was uninstalled since the title page was drawn: the "Listen on" entry is gone on the
  next draw, and a stale tap shows "That app isn't installed any more" rather than a resolver error; the app is installed but
  signed out: its own sign-in shows, nothing of ours intervenes.
- A deep link the service no longer answers (`ActivityNotFoundException` or the app opening on its home): logged as
  `handoff: <service> did not open at the title`, the entry stays (the app IS installed), H6 records it; the
  `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` form is tried second when the service answers it.
- Audio focus: the other app takes focus and never gives it back (phase 10 E9); a call during a station (pause; no resume by
  itself); headphones out mid-stream (becoming-noisy pause, as built).
- Metered network appearing mid-stream (Wi-Fi drops to mobile data): the E12 line appears without stopping the stream; under
  Q4 B / C the stream stops at the switch with the line saying why.
- A phone with no local audio at all and no network: the four built pivots' empty states unchanged (phase 10 E5); the radio
  pivot's offline empty state; the app never crashes on an empty everything.
- The MUSIC slot re-pointed to another player: the shell's Music app keeps its radio and streaming pages and keeps working;
  Tess's "play jazz radio" then goes to the slot app's session (phase 03's rule) and, if that app cannot answer it, says so.
- A media-server track whose server goes away mid-play (Q1 C): the E8 path, then the stated error; the server library cached
  as the directory is, so browsing survives the outage.
- APK updated (`adb install -r`) while a station plays: the service stops cleanly, no orphan session (phase 17's rule).
- Liveness (N-01): reboot and Device care leave favourites, the cache and the setting intact; after a reboot nothing plays by
  itself (phase 10: no timer read back off disk, nothing resumes).

## QA evidence
_None yet._
