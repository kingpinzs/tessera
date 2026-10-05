# Phase 17 build brief — Movies & TV (build tasks 7, 11, 12, 13, 14, its half of 15, the insecure-server prompt of 17)

Read `docs/plan/prompts/phase-17-build-common.md` first: its rules bind you.

- Worktree: `/home/jeremyking/projects/metro-launcher-p17/.claude/worktrees/video`, branch `phase-17-video`, cut from
  `phase-17` at eb725c76 (build tasks 0, 1, 3, 8 and 17's core — the network security config, `FixedEndpoints`,
  `buildConfig = true` — are in).
- Scratch files: prefix them `video-` in `/tmp/claude-1000/-home-jeremyking/b5b8c63b-5d38-49e9-84f3-de917a96acb2/scratchpad/`.
- The other builders: Photos (`.claude/worktrees/photos`: `photos/`) and Camera (`.claude/worktrees/camera`: `camera/`,
  the native build, `app/build.gradle.kts`, `gradle/libs.versions.toml`, CI). You do not touch their packages or
  resources, and you do not edit `app/build.gradle.kts` or the version catalog (everything you need is in the build:
  Media3 1.9.0's exoplayer and session; `BuildConfig.DEBUG` exists). `settings.gradle.kts`: only the one line that adds
  `testapps/qa-flix`. `AndroidManifest.xml`: keep any edit to Movies & TV's own elements.

## Yours
The `video/` package (VideoActivity, VideoApp, PlayerActivity, PlayerScreen, VideoDumpService and everything you add,
with `video/handoff/` for `StreamingHandoff`), the credential store (`video/` or a small `net/CredentialStore.kt` —
BS-3 names its alias and file), `res/values/video.xml`, `res/xml/shortcuts_video.xml`, your glyphs in
`brand/Glyph.kt`, `testapps/qa-flix` (the "Watch on" fixture APK, in the form of `testapps/tileclient-a`),
`docs/plan/qa/phase-17/scripts/catalogue_server.py` and `docs/plan/qa/phase-17/fixtures/` (the catalogue's recorded
JSON and posters, the Jellyfin seed script — the lead's gate rows will use them, so write them to the doc's Fixtures
paragraph), tests under `app/src/test/kotlin/app/tileshell/video/`, and `docs/plan/qa/phase-17/dev-video/`.

## Read (fully, before code)
1. The phase doc, all of it. Your clauses: Scope's Movies & TV bullet and Out; Decisions — the build-start line (BS-3,
   BS-4, BS-5 and the Jellyfin 12 corrections), Q-17-1 (a) and the agent line "the TMDB read token is a setting on the
   phone", Q-D with its note (r3 D16), Q-B, Q-A and its notes, Q3b, Q4, "the video player owns its own ExoPlayer"
   (with r3 D16's audio attributes), "processes", "harness contracts", "bars", T17-1 in full (the interface, HTTP,
   Offline, the VIEW schemes, the pane, Trust, Credential hygiene, the catalogue source, the media server, Diagnostics),
   C-16 (network security: you build (5) the insecure-server prompt and the `[video]` lines; (1)–(3) are in), T17-9 /
   C-9 (Media server is a DYNAMIC shortcut; `setActivity`), C-5, T17-15 (Wikidata), T17-16 / T17-17 and the pass-2 line
   (r3 D5), r3 D6 (the helpers), r3 D13 (`setPackage`), r3 D16 / V5 (a `video` session lands on no tile), r3 V15;
   Approximations Y5, Y6 (the pane, the cut to the player, the controls' fade), Y7, Y8, Y9, Y10; Build tasks 7, 11, 12,
   13, 14, 15, 17; the Acceptance preamble and its Fixtures paragraph; rows E11, E12, E13, E14, E15 (your half), E18 (the
   `[video]` lines), E19 (Movies & TV), E20, E21, E22, E23 (your half); P3, P4, P7, P13, P14, P15; H3, H7, H10, H11, H12,
   H19; the Edge cases that name videos, the catalogue, "Watch on", the media server, offline, an APK update mid-play,
   liveness.
2. `docs/plan/r11/movies-tv.md` and `docs/plan/r11/movies-tv-pass2.md` (its §4 governs where it corrects).
3. `docs/plan/qa/phase-17/BUILDSTART/README.md`: BS-3, BS-4 (the services table with its Wikidata properties, the
   SPARQL query and response shape, the query service's limits), BS-5 (TMDB's endpoints, attribution, 401 body;
   Jellyfin 12.1's API — `GET /Items?userId=`, `Authorization: MediaBrowser Token="…"`, `ApiKey=` on a stream URL, the
   REST seeding sequence and the pinned image).
4. Code you extend or reuse: `video/` (the shells), `net/FixedEndpoints.kt` (the https base URLs you build requests
   from: `TMDB_API`, `TMDB_IMAGES`, `WIKIDATA_QUERY`) and the two `network_security_config.xml` files (do not edit them;
   a host you would need to add is a report item), `recorder/RecorderPlayer.kt:27-34` (the audio-attributes and
   `setId` form), `music/MusicService.kt` and `feeds/MusicFeed.kt:159-171` with `tiles/engine/TileRouting.kt:44-51` (why
   the session id must be `video`; you change none of them), `weather/WeatherProvider.kt:57` and `weather/WeatherFeed.kt`
   (HttpURLConnection, the AtomicFile cache), `qa/phase-01/scripts/prefs_edit.py` (how a QA pref is written — find which
   SharedPreferences file it edits and read your three debug-only prefs from there), `people/PeopleSettingsPages.kt`
   and `settings/SettingsWidgets.kt` (a settings page; R3 C1 rows), `ui/components/OutlinedField.kt`,
   `ui/components/ModalOverlay.kt` (the ≡ pane is an overlay with NO scrim), `ui/MotionClock.kt`,
   `clock/ClockWidgets.kt`, `testapps/tileclient-a` and `settings.gradle.kts`.

## Build (each clause as its Decisions line specifies it; commit after each lettered part is verified)

### A. The player (build task 7) — `.video.PlayerActivity`, process `:video`
1. ExoPlayer on a SurfaceView (tag `video_surface`), its own Media3 MediaSession built with `setId("video")`,
   USAGE_MEDIA + AUDIO_CONTENT_TYPE_MOVIE, `handleAudioFocus = true` (a video pauses Music; nothing resumes by itself);
   released on stop so `dumpsys media_session` shows no orphan session after the activity ends or the APK is updated.
2. Sources (pure rule, JVM-tested): `content`, `http`, `https`, and `file` only for the shell's own files; anything else
   → "Can't play this address", `[video] unsupported scheme=<s>`, no ExoPlayer source created. `[video] playing <id>` for
   a MediaStore item and `[video] playing scheme=<s>` for every source; `http(s)` through `DefaultHttpDataSource`;
   unreachable → "Can't reach this video", `[video] cannot reach <host[:port]>`; an HTTP error → the error state and
   `[video] cannot decode <status>`; an undecodable or empty file → "can't play this file" and `[video] cannot decode
   <name>`, the player still resumed, never a crash. A logged URL never carries its query string (C-32).
3. The chrome per Y5 exactly (the 120-epx scrim, the 2-epx track at nav − 93 from x 12 to 348, the Ø-22 hollow accent
   thumb, the elapsed and REMAINING time labels, the transport back 10 · play / pause · forward 30 · full screen · "•••",
   CC at 36 only when the file has a text track or an `.srt` sits beside it, with the row re-centred without it), tags
   for every node E11 and E19 read (the track and its bounds, the thumb, both labels, each transport button, the menu's
   items); a tap on the track seeks to that fraction; the controls fade in ≈200 ms, hold ≈3.2 s, fade out over 367–400
   ms (`[motion] controls_fade …`); no status bar, no header, the nav bar drawn; library → player is a cut.
4. The "•••" menu: Cast to device (`android.settings.CAST_SETTINGS` resolved at tap; no cast library), Zoom to fill (fit
   by default), Repeat, Autoplay (the next video of the same My videos group at the end). Aspect and rotation from the
   file's metadata (the activity is not portrait-locked); subtitles toggle; two audio tracks → the first plays.

### B. My videos and the hub's frame (build tasks 7 and 14)
1. The library pages' chrome per E19 (phase 01's status bar is drawn by the frame; the 48-epx #171717 header with ≡ at
   x 24, the title at x 60.25, search at W − 24) and the ≡ pane per T17-16 (256 epx, #171717, no scrim, 48-epx rows,
   the current row accent with its 4 × 48 bar; rows My videos / Browse / Media server — the last only while a server
   is set up — and the bottom group → settings), tags `hub_pane:<myvideos|browse|mediaserver|settings>` (the current one
   `selected = true`), the pane's open / close motions through MotionClock with Y6's values.
2. My videos: MediaStore videos (READ_MEDIA_VIDEO; the empty state names the Setup checklist and offers the grant, the
   Videos row) with a ContentObserver, as Y5's 112-epx tile grid, a group per folder with its accent header, the file
   name without extension as the caption and NO duration; tags `video_tile:<id>` and a caption node per tile; `[video]
   library: <n>`; a tap starts PlayerActivity by explicit component.
3. Movies & TV's settings page: the TMDB key setting (C.1), the media server entry (D), About with TMDB's attribution
   text and logo and JustWatch's credit (BS-5's wording; the logo unmodified — say where the file came from).

### C. The catalogue and "Watch on" (build tasks 11, 12, 14)
1. The credential store per BS-3 (Android Keystore AES-256-GCM, alias `tessera_credentials_v1`; file
   `files/credentials_v1.json`, temp-and-rename, re-read on every use; set / get / remove by name; a value that fails to
   decrypt reads as absent with a line that holds no secret). TRUST-TOUCHING: no token in any line, URL or exception
   text. JVM-test what can be (the file format and the temp-and-rename rule behind a cipher interface); the Keystore
   half is proven on the device (a set value survives a force-stop; the file holds no plaintext).
2. The TMDB key setting: enter (typed or pasted), replace, remove. No key → Browse says film search needs a key with a
   link to the setting, `[video] catalogue: no TMDB key saved`, NO request. A 401 → Browse says the saved key was
   refused with the same link, `[video] catalogue "<q>": error 401`, the cache still shown.
3. `catalogue.search` / `lookup` over TMDB v3 (`Authorization: Bearer <token>`, never `api_key`), image URLs from
   `/3/configuration`'s `images.secure_base_url`, the 7-day cache in `files/video_catalogue/` (an entry's age is its
   file's mtime; a stale entry is re-fetched: `: <n> (refreshed)`), offline → the cache with "You're offline — showing
   what was saved" and `[video] catalogue "<q>": offline`; `/500` → "The catalogue isn't answering" and `error 500`; a
   stopped server → `error connect`; 429 → "The catalogue is busy, try again in a minute" and `error 429`; no artwork →
   the poster placeholder; no result → the empty line. The response parsing is pure and JVM-tested. The QA redirect:
   the pref `qa_catalogue_base` (and `qa_wikidata_base`, `qa_server_base`) honoured ONLY when `BuildConfig.DEBUG`.
4. Browse per Y8 (R3 C1's search box, section rows "title + accent Show all", one strip of 112 × 160 posters on a
   120-epx pitch per section; the title page per 1.7.10 with its overview and the "Watch on" rows), tags
   `hub_result:<id>` with its title and year nodes and an image node, `hub_title`, `hub_attribution` at Browse's foot.
5. `StreamingHandoff` (`app.tileshell.video.handoff`; a plain object, table logic JVM-tested): BS-4's table as written
   in the README (Jellyfin's app left out; Paramount+, Peacock, YouTube and Plex search-only; a Disney+ row with no id
   launches the app), `installedServices()` resolved at call time, the Wikidata lookup by TMDB id (BS-4's query,
   `User-Agent` set, one query at a time, the answer cached with the title page, 429 honoured), `openTitle` with
   `setPackage(<service package>)` on both forms, ActivityNotFoundException → the search form → `not installed`; the
   lines `[video] watch-on <service> "<title>": id <found|none> (wikidata)` and `[video] watch-on <service> "<title>" ->
   <intent> | not installed`; the row set per Y9; "Not on this phone" when no installed service has the title; "That
   app isn't installed any more" when the service vanished between the draw and the tap.
6. `testapps/qa-flix` (package `app.tileshell.testclient.qaflix`): intent filters for `https://qa-flix.test/title/<id>`
   and `https://qa-flix.test/search?q=<title>`, showing the received URI in a TextView with resource id `qa_flix_uri`;
   the table gains the QA-Flix fixture service ONLY in debug builds, with its fixture Wikidata property.
7. `catalogue_server.py` (port 8090) per the Fixtures paragraph: TMDB-shaped JSON for "Blade Runner" (exactly "Blade
   Runner" 1982, "Blade Runner 2049" 2017, "Blade Runner: Black Lotus" 2021), `/3/configuration` with the image base at
   `http://10.0.2.2:8090/img/`, poster PNGs of known flat colours, the watch providers naming QA-Flix, the
   Wikidata-shaped answer (2049 has a QA-Flix id, 1982 has none), a title on no service, `/500`, `/404`, `/429`, `/401`,
   and a video file route for E13; it logs per request only the path, `bearer ok` / `bearer missing` and whether an
   `api_key` parameter was present — never a header or parameter value.

### D. The media server (build task 13 and C-16 (5); TRUST-TOUCHING)
A thin Jellyfin 12.1 client with no SDK: "Add a server" (host, user, password; the password is never stored), the
token in the credential store, the library per Y10 (`GET /Items?userId=…`), direct play through PlayerActivity
(`/Videos/<id>/stream?static=true` with `ApiKey=` — logged with the query string removed), the states `[video] server
<host>: connected | unreachable | unauthorised` ("That password isn't right"; "Can't reach your media server"; after an
invalid token the sign-in page again with the host prefilled), the Media server pane row and the DYNAMIC shortcut
`video_mediaserver` (`setActivity(ComponentName(VideoActivity))`, `[video] shortcut mediaserver published | removed`)
appearing on set-up and gone on removal (`[video] server token cleared`). A sign-in to a plain `http://` address that
is not private (the rule pure and JVM-tested: RFC 1918, link-local, loopback, `.local`, and the emulator host 10.0.2.2
are private; a public name, a public IPv4 and a global IPv6 are not) asks first — "This server isn't secure — your
password would be sent unencrypted" — logs `[video] server <host>: insecure, asked`, and sends NOTHING until Continue.
The fixture: a script under `docs/plan/qa/phase-17/fixtures/jellyfin/` that starts the pinned image (BS-5's digest) on
port 8096 with an empty config volume and a media folder holding a 10-s test video, seeds it over REST (BS-5's
sequence — it has not been run yet: run it, fix it, record what differs), and stops and removes the container; the
fixture account's password is `qa-password`. Docker is on the host; you may pull and run that one image and must stop
and remove the container (by its recorded id) when your session ends.

### E. App Shortcuts (build task 15, your half)
`video_myvideos` and `video_browse` are static (the file exists; make each open its page with its pane row current);
`video_mediaserver` is D's dynamic one.

### Not yours
The network security config and `FixedEndpoints` (in). The Settings > Diagnostics probe page. `egress_guard`,
`leak_scan.sh`, the gate's drivers. No host-audio step: a video's sound is never checked by ear or by the host.

## Proof you owe (dev proof, on the emulator, restoring what you change)
A 10-s one-colour-per-second fixture (`ffmpeg -f lavfi`, h264, `-g 25`) on My videos with its caption and no duration;
the player's centre pixel at 3.5 s, a 70 % seek, pause holding the pixel, the fade's `[motion]` line, the `video`
session in `dumpsys media_session` and `[music] session app.tileshell id=video -> none` in the launcher's ring while it
plays; Music paused by the video and not resumed after Back; `http://10.0.2.2:8090/…` playing with `scheme=http`,
airplane mode's "Can't reach", `/404`, `rtsp://`; a truncated and an empty file; E19's Movies & TV geometry from a dump
(px ÷ 3 = epx); the no-key page with NO request in the fixture's log, the key entered, the three results with artwork,
`bearer ok` on every request, the offline cache, `/500`, the refresh after the mtime is moved back, the key removed;
both "Watch on" branches reaching qa-flix with the exact URI and the uninstalled / not-installed forms; the media
server connected, playing, persisted across a force-stop, the wrong password, the stopped container, the insecure
prompt with Cancel sending nothing, removal clearing the token and the shortcut; `adb root; grep -rlF <token | qa-password
| the typed TMDB token> /data/data/app.tileshell/; adb unroot` listing no file (the one allowed use of root, undone at
once); `:video` killed with the launcher's pid unchanged; no token in any saved ring slice or in `adb logcat -d`.
