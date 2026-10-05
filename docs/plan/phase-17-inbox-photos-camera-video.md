---
phase: 17
slug: inbox-photos-camera-video
status: FINAL   # FINAL 2026-10-05 10:12 (Jeremy: "(a)") after review round 3 (the last; opus + opus) and Q-17-1 / Q-17-2 / Q-17-3; changes from here only by a dated INDEX Change Log entry. History: split 2026-09-22; interview DONE 2026-09-23; review triage round 1 applied 2026-09-23; round 2 applied 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md): Q-B answered (Jellyfin); Q-A re-asked as Q-A2 (the repo and its Release APKs are public) and answered 2026-09-23 (B: the TMDB key only in builds made on Jeremy's PC; superseded 2026-10-05 by Q-17-1 (a)); Q-D (plain-http media streams) answered 2026-09-23 (A) — affected text marked "Q-A2: B" / "Q-D: A"; r11/photos.md, r11/camera.md, r11/movies-tv.md landed and applied (T17-16; E19 written); round 3 (opus + opus) 2026-10-04, triage review/2026-10-04-phase17-r3-triage.md; its three questions answered 2026-10-05 (Q-17-1 (a), Q-17-2 (b), Q-17-3 (a)) and their "A after Q" rows applied
depends-on: [01, 02, 03, 10, 11, 12, 15, 16]   # 15 for the live-tile routing fix (its build task 0, triage C-1); 16 for the assignSlotOnce guard (its build task 1, triage C-2); 10 for MusicFeed / Media3; 11 for the E23 bursts; 12 for the E25 template and C-15's marker (C-23)
---

# Phase 17 — W10M inbox apps III: Photos, Camera and Movies & TV

## Goal
Three Windows 10 Mobile inbox apps live inside the shell APK, each an app in the app list like phase 10's
Music: **Photos** (what the Photos tile opens; the full W10M Photos with its editor and video trimming),
**Camera** (what the bottom-row Camera tile and Tess's "take a photo" open; every Windows Camera mode the phone
can do) and **Movies & TV** (the hub: the phone's own videos and the one player every video in the shell plays
through, plus an online catalogue of films and shows, "Watch on <service>" hand-offs into the streaming apps on
the phone, and the user's own media server). The PHOTOS and CAMERA slots are seeded to the shell's apps once,
the way phase 10 seeded MUSIC, ~~and never over a choice the user made (phase 16's guard)~~ SUPERSEDED 2026-10-05 by
Q-17-3 (a) — even over a hand pick, once, as phase 16's Calendar and People did (`takeOver`). Every visual and motion
value is measured by R11 or recorded as a tagged approximation with a NEEDS-HUMAN row. Movies & TV's online
half is the one part here that uses the internet, offline preferred (A11 as amended 2026-09-23); Photos and
Camera use none. This phase also builds the streaming hand-off (`StreamingHandoff`, Decisions) that phase 20
(Music streaming) and the TV-channels phase reuse.
~~Nothing here uses the internet (A11).~~ SUPERSEDED 2026-09-23 by the A11 amendment (PLAN.md) and T17-1.

## Scope
**In:**
- Photos: a library of the phone's images and videos together (Q4 A) read from MediaStore and watched for
  changes the way PhotosFeed watches images; a collection pivot (by date) and albums (MediaStore buckets); a
  full-screen viewer (swipe, zoom); share, delete, "set as" (the shell's Start background; Android's lock-screen
  wallpaper), slideshow; the full editor (Q1 C: crop, rotate, straighten, auto-enhance, light and colour, filters,
  red-eye, saved as a copy) and video trimming (saved as a copy) — straighten, light and colour, filters, red-eye and the
  trim screen are P4 designs with NEEDS-HUMAN rows (W10M's inbox editor did crop / rotate / auto-enhance only, r11/photos.md
  1.7.3–1.7.4; ~~the trim screen has one LOW still, 1.11~~ SUPERSEDED 2026-10-04 by r3 D4: R11's 1.11 still is the
  Add-ins frame picker, not trim; the trim screen takes the Nov-2015 form measured LOW in r11/photos-pass2.md §1 / §4.1;
  T17-16); the app's own empty / denied / partial-access
  states naming the Setup checklist; it takes the PHOTOS slot once (marker `slot:photos:v1`) and appears in the
  app list. A video tapped in Photos opens the shared player (Q4 A, one player surface).
- Camera: still and video capture on the back and front cameras with flash, timer, grid, tap-to-focus and
  zoom; every Windows Camera mode (Q2 C): the Lumia pro dial (W10M's five-arc dial, measured — R11 camera.md 1.4),
  panorama, slow motion and Living Images, each
  shown only where the phone's camera can do it (Decisions); files saved through MediaStore into DCIM/Camera so
  the Photos tile, the Photos app and every other gallery see them; the capture-intent contract (Q5 A: it answers
  `IMAGE_CAPTURE` and `VIDEO_CAPTURE` and returns the result to the caller, with the guards in Decisions; since Android 11
  it is reached by an app that names the shell or targets API 29 or lower, Q-17-2 (b)); it
  takes the CAMERA slot once (marker `slot:camera:v1`); its own `android:process` (`:camera`).
- Movies & TV (Q3b A), navigated by W10M's ≡ pane, not pivots (r11/movies-tv.md 1.3; T17-16): the local half — a My
  videos page (the phone's videos as W10M's 112-epx tile grid grouped by folder, captioned with the file name; W10M's tiles
  carried no duration, 1.4.11) and one player screen (transport, scrubber, aspect and rotation handling, subtitles when
  the file carries a text track); it answers `ACTION_VIEW video/*` for `content://` and `http(s)://` sources (plain `http`
  Q-D: A, Decisions C-16); it takes audio focus so
  Music pauses; a media session for headset and Bluetooth buttons that never lands on a tile; the same player
  screen is what a video tapped in Photos or Files plays in; its own process (`:video`). The online half — a
  Browse page over a public film database (TMDB — Q-A: A; ~~the key only in builds made on Jeremy's PC — Q-A2: B~~ SUPERSEDED 2026-10-05
  by Q-17-1 (a) — the owner's read token pasted once into Movies & TV's settings, stored encrypted on the phone), per title
  the streaming apps on the phone that have it with "Watch on <service>" opening that app at the title where public data
  gives the service's own id and at its search otherwise (T17-15), and a Media server page (Jellyfin only — Q-B) when one
  is set up; a catalogue cache so the hub works offline; the `StreamingHandoff` interface phases 20 and 21 reuse; the
  network security config every network use of the shell runs under (Decisions C-16).
- Slot seeding through phase 16's `assignSlotOnce` ~~guard~~ with its `takeOver` (SUPERSEDED 2026-10-05 by Q-17-3 (a)), and the re-cut of the rows that touched these slots
  (phase 01 E4; phase 03 E2, E10); the re-run of phase 10 E10 / E13 on this build under phase 15's live-tile
  routing fix (E16). ~~and the fix for the package-keyed live-tile fallback (Decisions) with its re-run of
  phase 10 E10 / E13~~ — the fix itself moved to phase 15 (C-1, 2026-09-23); its rule text stays in Decisions
  as the specification E16 checks.
- Settings + checklist rows: Camera (CAMERA permission), Videos (READ_MEDIA_VIDEO); the Photos row exists
  (phase 01) ~~and gains the video half~~ SUPERSEDED 2026-10-04 by r3 V20: the Photos row stays READ_MEDIA_IMAGES /
  READ_MEDIA_VISUAL_USER_SELECTED (`onboarding/Checklist.kt:170`); READ_MEDIA_VIDEO is the Videos row alone; each row is also a setup-wizard step with its why line (phase 12 Q1 / Q2);
  each app offers its grant where its empty state is (phase 10 task 10).
- Static App Shortcuts for each app's top-level screens and one dynamic shortcut ("Media server") under the
  phase 11 Q1 standing rule as amended by C-9 (Decisions); the phase baseline file `qa/phase-17/baseline_layout.json`
  (C-3); diagnostics lines for every silent-empty state; ADDs to the exported-components allow-list
  (qa/phase-03/exported-allowlist.txt); the app-list regression; the APK budget check with this phase's delta.
**Out (explicitly):** OneDrive, cloud albums, purchased content, a Store (PLAN.md 2026-09-22: Microsoft's cloud
products and a Store are out; offline preferred, A11 as amended 2026-09-23); ~~streaming or any http source
(A11, R10-Q4: an `http://` VIEW is refused)~~ SUPERSEDED 2026-09-23 by T17-1 — the hub streams from a media server
and plays `http(s)://` VIEWs; building any streaming service's own client (no Netflix / Prime / Disney+ playback
inside the shell, no scraping of a service's catalogue, no DRM, sign-in or paywall bypass — the user signs in
inside each service's own app; phase 20's Out says the same for music); the TV-channels app (PLAN.md 2026-09-23
scope add; R13 first, then its own phase); replacing Samsung's side-key double-press camera launch (the shell
cannot set it — Decisions); the lock-screen camera; Samsung's extra lenses through Samsung's SDK (not public
Camera2 — P1 records what the S25U exposes); Files (phase 18); the pod bay's pods; new Tess commands ("show my
photos" is not in the ruled list, phase 03 Decisions); any interim "viewer-only" Photos, "player-only" video or
"local-only" Movies & TV build (Hard Rule 16 — the interview ruled the fuller forms).

## Decisions
- 2026-10-05 09:05: Review question Q-17-3 (round 3, D15: phase 16's Q-16-1 had Calendar and People take their slots once
  even over a hand pick; this doc seeded PHOTOS and CAMERA without it, so on the owner's phone, where both slots are
  very likely hand-pointed at Samsung's apps, the tiles and "take a photo" would stay on Samsung's) — **the shell's Photos
  and Camera take the PHOTOS and CAMERA slots once, on the update that brings them, even over a hand pick, as Calendar and
  People did** (Jeremy: "(a)"). Task 3 seeds both markers with `takeOver = true` (`tiles/LayoutStore.kt`
  `assignSlotOnce(…, takeOver)`; the pass list that today admits only Calendar's and People's markers gains
  `slot:photos:v1` and `slot:camera:v1`); the line names what was replaced (`-> assigned, replaced user's <earlier>`), so
  the earlier app can be found again and the slot pointed back in Settings > Tile apps; a later re-point by hand is kept
  (the marker has run). E1's upgrade half expects the takeover line, not `kept user's`; a phone row in phase 16 P0's form
  records both slots' lines after the update and that pointing one back holds across a restart and the next update.
  Tess's "take a photo" follows the CAMERA slot, so it opens the shell's Camera after the update.
- 2026-10-05 09:04: Review question Q-17-2 (round 3, V3 (a) / D1: since Android 11 an implicit `IMAGE_CAPTURE` /
  `VIDEO_CAPTURE` from a caller targeting API 30+ resolves only to preinstalled system cameras, so Q5 A's "one of
  Android's camera choices beside Samsung Camera" reaches almost no app; and the capture answer is the phase's riskiest
  surface) — **keep the capture-intent answer as Q5 A ruled, with round 3's fixes** (Jeremy: "(b)"). What it is now, said
  plainly: the shell's Camera answers a capture request from an app that names it (`setPackage("app.tileshell")` or its
  component) or that targets API 29 or lower; Android's own resolver does not offer it to other apps, and nothing in this
  phase tries to change that. The fixes applied with round 3's "A after Q-17-2" rows: D1's three-condition output guard
  (started for a result; the intent's own ClipData holds the same URI with FLAG_GRANT_WRITE_URI_PERMISSION; not one of
  the shell's own authorities) or RESULT_CANCELED with `[camera] refused output: no grant`; the no-output contract
  (IMAGE_CAPTURE without EXTRA_OUTPUT returns a thumbnail Bitmap in `data` and writes no file; VIDEO_CAPTURE without it
  saves to DCIM/Camera and returns that row's URI with a read grant); E9 per V3 (b)–(d) (qa-capture names the shell's
  package; the resolver clause becomes a recorded `query-activities` fact; the fixture logs its own output's
  `exists=` / `size=`; a GPS control capture; the display_photo negative). The helper is `.camera.CaptureActivity` in
  `:camera` (D6), not a catalog entry. Q5 A's line is not edited; this entry corrects the premise it stated.
- 2026-10-05 09:01: Review question Q-17-1 (round 3, D2 / V15: as ruled under Q-A2 B, film search could never work on the
  phone — the phone installs only CI builds, a PC build is versionCode 1 and is refused over them, and no route gets a PC
  build onto the phone without a cable) — **the owner pastes his TMDB read token once into Movies & TV's settings on the
  phone; it is stored encrypted on the device (an Android Keystore key) and is never in any build, the repo, CI or a log**
  (Jeremy: "(a)"). SUPERSEDES Q-A2 B's "the key is built in only in builds made on Jeremy's PC" and its recorded
  consequence; Q-A2's purpose stands — no key in the public repo or in any APK. Consequences, applied with round 3's
  "A after Q-17-1" rows: no TMDB field in BuildConfig (D14 keeps `buildConfig = true` for the `BuildConfig.DEBUG` gates
  only); with no token saved, Browse says film search needs a key and links to the setting (diagnostics: "catalogue: no
  TMDB key saved"); a bad token is reported from TMDB's 401 and the token can be replaced or removed; P13, P15, H10 and H11
  run on the CI build the owner installs, after he pastes the token; the AVD's catalogue rows type a dummy token into the
  same setting against the local stand-in. The stored token is trust-touching (a credential): it joins the adversarial
  review's list, with the Jellyfin token.
- 2026-10-05 (agent, r3 triage D2, V15, D14 — Q-17-1 (a) applied): **the TMDB read token is a setting on the phone, not a
  build input.** Movies & TV's settings page has a TMDB key setting: enter (the owner pastes his read token once), replace,
  remove. The token is kept in the credential store the Jellyfin token uses (Decisions "Trust": a non-exportable Android
  Keystore AES-256-GCM key, the file in the app's private files dir written temp-and-rename and re-read on use, alias and
  file named at BS-3), and is never in a build, the repo, CI, a diagnostics line, logcat or an evidence file (C-32). No
  token saved → Browse says film search needs a key, with a link to the setting, `[video] catalogue: no TMDB key saved`,
  and no request is made; a token TMDB refuses (HTTP 401) → Browse says the saved key was refused, with the same link to
  replace or remove it, `[video] catalogue "<q>": error 401`, the cache still shown (build task 12). Struck with it:
  `BuildConfig.TMDB_READ_TOKEN`, the Gradle property `tmdb.readToken`, the local.properties read, the QA and CI-form APK
  pair, and "Film search is off in this build" (Trust, Credential hygiene, the catalogue source, tasks 12 and 16, the
  acceptance preamble, Fixtures, E17, E18, E20, P15). `app/build.gradle.kts` still gains `buildFeatures { buildConfig =
  true }` (r3 D14: its `buildFeatures` holds `compose` and `aidl` only, `app/build.gradle.kts:77`, and AGP 8 generates
  `BuildConfig` only with the flag, so the `BuildConfig.DEBUG` gates of the `qa_catalogue_base`, `qa_wikidata_base` and
  `qa_server_base` prefs need it). Q-A2's recorded consequence was also false as built (r3 D2): a build made on the PC is
  versionCode 1 (`app/build.gradle.kts:27`, `TESSERA_VERSION_CODE` or 1; CI sets the run number,
  `.github/workflows/apk.yml:93`), which Android refuses over an installed CI build; under Q-17-1 no such build is needed.
  Every phone row runs on the CI build the phone installs; P13, P15, H10 and H11 after the owner pastes his key (r3 D2,
  V15). On the AVD a row that needs the catalogue types the dummy token `qa-dummy-token` into the same setting against
  `catalogue_server.py` and removes it at its end (RV12); E22's plaintext check and leak scan cover it beside the Jellyfin
  token. The TMDB key setting and its store join the adversarial review of the credential path (Decisions "Trust", build
  task 13's GATE).
- 2026-10-05 (agent, r3 triage D1, V3 (b)(c)(d) — Q-17-2 (b) applied): **the capture helper's output guard and its
  no-output contract.** They replace T17-4's "writes only through `contentResolver.openOutputStream(uri)` under the
  caller's URI grant" — `openOutputStream` runs as the shell, so it succeeds wherever the shell can write: contacts and
  calendars today (`WRITE_CONTACTS`, `WRITE_CALENDAR`, `AndroidManifest.xml:47`, `:49`; a raw contact's `display_photo` URI
  is writable by URI), All-files access after phase 18 — and its "an intent capture leaves NO copy in DCIM", which
  VIDEO_CAPTURE's javadoc contradicts for a request with no EXTRA_OUTPUT. `.camera.CaptureActivity` (`:camera`) accepts a
  `content://` EXTRA_OUTPUT only when all three hold: (a) it was started for a result (`callingPackage != null`); (b) the
  intent's ClipData holds that same URI and its flags include FLAG_GRANT_WRITE_URI_PERMISSION, which Android checks against
  the caller's own access when the activity starts (Android builds that ClipData itself only when the caller set none,
  `Intent.migrateExtraStreamToClipData`, so a caller that sets its own without the flag is never checked); (c) the
  authority is not one of the shell's own. Otherwise it returns RESULT_CANCELED with `[camera] refused output: no grant`
  and writes nothing. A `file://` EXTRA_OUTPUT is refused with `[camera] refused output scheme=file` (reachable only from a
  caller that relaxed its own StrictMode VmPolicy or targets API 23 or lower; any other caller's platform throws
  FileUriExposedException first, r3 V3 (b)). With
  no EXTRA_OUTPUT: IMAGE_CAPTURE returns a thumbnail Bitmap in `data` and writes no file; VIDEO_CAPTURE saves to
  DCIM/Camera through `media/MediaWrites` and returns that row's URI with a read grant. A capture to an accepted
  EXTRA_OUTPUT leaves no copy in DCIM, and its EXIF location is stripped. `qa-capture` names the shell's package
  (`setPackage("app.tileshell")`, Android's route to a third-party camera since Android 11), and E9 proves each leg: the
  resolver clause as a recorded `cmd package query-activities` fact, the fixture's own `exists=` / `size=` lines, a GPS
  control capture under `adb emu geo fix`, and the display_photo negative. The guard stays in the T17-4 adversarial review.
- 2026-09-23: Review question Q-D — plain http is allowed for MEDIA only (Jeremy: "(a)"): radio stream URLs and the user's
  Jellyfin server may use http; the shell's own fixed endpoints (TMDB, radio-browser's directory, MusicBrainz / Cover Art
  Archive, weather) stay https-only, enforced by Android's network security config (cleartext denied by default, permitted
  only on the media playback path); a media-server sign-in over http to an address outside the home network (not a private /
  link-local range) asks first. A trust change: the network security config and the per-path cleartext rule get the
  adversarial review the project requires before done (build-prompt trust list, C-16). Every row and task written "under A"
  is the ruled form; the B and C branches are not built.
  - Note (2026-10-05, r3 D16): Android's network security config scopes cleartext by host, not by code path; the ruled
    form is C-16 (1)–(2) — the shell's fixed hosts are denied cleartext by domain-config, other hosts are permitted at the
    base.
- 2026-09-23: Review question Q-A2 (Q-A re-asked with the fact that the repo and every CI release APK are PUBLIC) — the TMDB key
  is built in ONLY in builds made on Jeremy's PC (Jeremy: "(b)"). The build reads tmdb.readToken from the gitignored
  local.properties into BuildConfig when it is present; the CI workflow never has it (no Actions secret), so the public "latest"
  download has no key and its Browse pivot says plainly that film search is off in this build (diagnostics: "catalogue: no
  TMDB key in this build"). SUPERSEDES the Q-A line's "CI reads it from a GitHub Actions secret". Consequence recorded for
  Jeremy: film search on the phone needs a build made on the PC and installed from there (release-signed with the same key as
  CI, phase 01's rule, so either can update the other; installing a later CI build over it drops the key again).
- 2026-09-23: Review question Q-B — the media server is Jellyfin (Jeremy: "(a) but I dont have it yet but I do have a server
  that I need to get back up and running"). The hub's media-server part is a Jellyfin client only: no Plex code, no plex.tv
  sign-in. It is built and proven on the AVD against a Jellyfin container on the host (E22); Jeremy does not run Jellyfin yet,
  so the phone row P14 waits on his home server coming back up with Jellyfin installed, and until a server is added the Media
  server pivot and its dynamic shortcut do not appear (nothing half-configured shows).
- 2026-09-23: Review question Q-A — the film database is TMDB with Jeremy's personal key (Jeremy: "(a) ... personal use").
  The key is NEVER in the repo: it lives in the gitignored local.properties (tmdb.readToken, tmdb.apiKey) and reaches the app
  as a BuildConfig field at build time; the CI build that makes Jeremy's phone APKs reads it from a GitHub Actions secret
  written into local.properties during the build (Jeremy adds the secret when phase 17 is built). A build without the key has
  no catalogue: the Browse pivot says so plainly and the diagnostics log "catalogue: no TMDB key in this build" — never a
  silent empty page. Calls use the v3 API with the read token as a bearer header. TMDB's attribution ("This product uses the
  TMDB API but is not endorsed or certified by TMDB", with its logo) shows in Movies & TV's About, and the where-to-watch
  data (TMDB's watch providers, sourced from JustWatch) carries JustWatch's credit where it is shown. The key was checked
  live on 2026-09-23: /3/configuration answered 200.
  - Note 2026-09-23 (r2 triage T17-13 / T17-14, applied under Q-A2: B above): the CI-secret route is superseded (Q-A2). One
    credential only — `tmdb.readToken`, sent as `Authorization: Bearer`; `tmdb.apiKey` and the `api_key` query parameter are
    used nowhere (the property may stay in local.properties, unread). ~~The build reads the Gradle property `-Ptmdb.readToken`
    first and local.properties second (build task 12), so a QA build can carry a dummy token and a CI-form build none.~~
    SUPERSEDED 2026-10-05 by Q-17-1 (a) — no build reads a token; it is the TMDB key setting on the phone (build task 12).
    Never logged: the catalogue lines carry the query and the status only. The one no-key form (T17-14): ~~the diagnostics
    line `[video] catalogue: no TMDB key in this build` and the page line "Film search is off in this build" (the Q-A2
    ruling's words)~~ SUPERSEDED 2026-10-05 by Q-17-1 (a) — the diagnostics line `[video] catalogue: no TMDB key saved` and
    Browse saying film search needs a key, with a link to the setting. The one attribution placement (agent, T17-14): Movies & TV's About (this ruling) AND the Browse page's foot
    (`hub_attribution`), since TMDB's terms ask for it where the data shows; E20 asserts it on Browse.
- 2026-09-23: Interview Q5 — the shell's Camera answers IMAGE_CAPTURE and VIDEO_CAPTURE and returns the result to the caller
  (Jeremy: "(a)"), so it is one of Android's camera choices beside Samsung Camera.
- 2026-09-23: The App Shortcuts under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Photos — Collection, Albums;
  Camera — Photo, Video, Panorama, Slow motion (a mode the phone cannot do is left out, as in Q2); Movies & TV — My videos,
  Browse, Media server (the last only when one is set up). [Amended 2026-09-23 by C-9 / T17-9: "Media server" and Camera's
  Panorama / Slow motion are DYNAMIC shortcuts, published only while their screen exists; the rest are static — the agent
  lines at the end of Decisions.]
- 2026-09-23: Interview Q4 — Photos shows photos and videos together (Jeremy: "(a)"); tapping a video opens the one shared player
  screen, the same one Movies & TV uses (one player surface, Hard Rule 16).
- 2026-09-23: Interview Q3b — Movies & TV is the full hub (Jeremy: "(a)"): local videos; an online catalogue of films and shows
  from a public film database; per title, which streaming apps on the phone have it, with "Watch on <service>" opening that app
  at the title (the user still signs in inside each service's own app); and the user's own media server (Jellyfin or Plex) when
  there is one. The same answer asked for a TV-channels experience and said it "could be a seprate app thing": that is recorded
  as its own scope add (PLAN.md 2026-09-23) and its own phase, not part of this one. This phase builds the streaming hand-off
  (catalogue lookup and "open this title in that app") that the channels app and Music's streaming side reuse.
- 2026-09-23: The video app is Movies & TV WITH its online half (Jeremy: "I want the movies & TV. microsoft has cancled theres
  so I need a way to fake it using streaming services and other methods pluse anything local", then the A11 amendment in
  PLAN.md). Q3 as first written assumed A11 kept it local and is superseded; its shape is re-asked (Q3b).
- 2026-09-23: Interview Q2 — every Windows Camera mode (Jeremy: "(c)"): photo and video with automatic everything, flash /
  timer / grid / front-back / zoom, the Lumia pro dial (manual focus, ISO, shutter, white balance, exposure), panorama, slow
  motion and Living Images. Agent notes: a mode the phone's camera cannot do (Camera2 reports no high-speed sessions, no manual
  sensor control) is not shown, and the diagnostics say why — no mode that silently fails; the AVD's virtual camera cannot
  prove slow motion or manual sensor control, so those rows are phone-only (P rows); panorama stitching must be code the shell
  may ship (licence checked at build start, P5), and is the largest single task here.
- 2026-09-23: Interview Q1 — Photos is the full W10M Photos (Jeremy: "(c)"): collection, albums, viewer, share / delete / set
  as, slideshow, the editor (crop, rotate, straighten, auto-enhance, light and colour, filters, red-eye, saved as a copy) AND
  video trimming (saved as a copy, the original kept). One form, built complete (Hard Rule 16).
  - Note 2026-09-23 (r2 triage T17-16): the ruling stands and is built as ruled; only its reading as W10M fidelity is
    corrected. W10M's inbox editor was "Crop, Rotate, Auto-enhance" (r11/photos.md 1.7.3–1.7.4; filters and the rest lived in
    Lumia Creative Studio, a separate app), so straighten, light and colour, filters and red-eye are P4 additions judged by
    H13b [accept]; ~~the trim screen has one LOW still (1.11) and is a P4 design (Y13, H13b)~~ SUPERSEDED 2026-10-04 by r3
    D4: 1.11 is the frame picker; the trim screen is the Nov-2015 form measured LOW in r11/photos-pass2.md §1 (Y13, H13b).
- 2026-09-22: From phase 11 interview Q1 (Jeremy: "A"), a standing rule for every shell app: this phase's apps declare their
  own top-level screens as static App Shortcuts, so a hold on their tiles bursts those screens (phase 11). Which screens each app
  declares is settled at this phase's own interview; a build task and an acceptance row carry it.
- 2026-09-22 Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out
  thing at a glance thing"): Photos, Camera and "a video player (Movies & TV's role)" are in, "each an app in
  the shell APK like Music" (PLAN.md, 2026-09-22 scope add). Read as A8 reads the feature list: everything
  buildable is in; build order is what gets decided.
- 2026-09-22 R10-Q4 (Jeremy: "(a)"): A11 stands as written. So no cloud pivot, no online catalogue, no
  streaming: the video player refuses network URIs and plays only what is on the phone.
  **SUPERSEDED 2026-09-23 by the A11 amendment (PLAN.md) and T17-1** — kept for history. R10-Q4's ruling that the shell builds
  no Mail, browser or Maps still stands (PLAN.md 2026-09-23 clarifies it); only its "no streaming" reading of A11 is withdrawn.
- 2026-09-22 (agent, R10 triage): **the shell's own apps take their slots once, following phase 10 Q5's Music
  precedent** (`LayoutStore.assignSlotOnce`, one marker per slot, "never over a user's explicit choice"), and
  Tess's actions target the shell's app when it holds the slot. Verified in code: `SlotResolver.resolve`
  auto-assigns a category only with exactly one handler or a preferred one; on the AVD `STILL_IMAGE_CAMERA`
  already has three handlers and `APP_GALLERY` two (`cmd package query-activities`, 2026-09-22), so without the
  seed both tiles would read "Tap to choose". Markers `slot:photos:v1` and `slot:camera:v1`.
  ~~A slot the user re-pointed before this build installs is left alone (the marker path only ADDs to `explicitSlots` when the
  marker has not run — an assignment made by hand is not one the marker made, and the upgrade row E1 proves it).~~
  SUPERSEDED 2026-09-23 by T17-5 / C-2 — WRONG by the code: `app/src/main/kotlin/app/tileshell/tiles/LayoutStore.kt:127-139`
  checks only the marker and then writes `explicitSlots + (slot to component)`, which REPLACES an existing hand assignment.
  The corrected rule: **phase 16's guard (its build task 1, built before this phase) keeps a hand assignment and logs
  `assignSlotOnce <marker> <slot> -> kept user's <component>`~~; the seed here runs under it~~.** SUPERSEDED in part
  2026-10-05 by Q-17-3 (a): these two seeds pass `takeOver`, as phase 16's Calendar and People do, and take their slots
  once even over a hand pick (`-> assigned, replaced user's <earlier>`). On Jeremy's phone PHOTOS and
  CAMERA are very likely hand-assigned (Samsung Gallery / Samsung Camera), which is exactly the case E1's upgrade half now
  proves (and P0 on the phone). Tess needs no code change for "take a photo": `ActionLayer.takePhoto()` is `launchSlot(Slot.CAMERA, …)`,
  which resolves through the same `SlotResolver`; what changes is the OBSERVABLE. Re-run rows: **phase 01 E4**
  (Photos and Camera stop being 2+-handler negatives — Mail and Store keep that proof), **phase 03 E2** (the
  "take a photo" line's resumed activity becomes `app.tileshell/.camera.CameraActivity`), **phase 03 E10**
  (expectation unchanged — "take a photo" still shows "Unlock to continue" — re-run because the resolved
  component changed). Recorded in the INDEX Change Log when built.
- 2026-09-22 (agent, verified in code): **the package-keyed live-tile fallback must be fixed before the seed,
  or Music's face lands on Photos and Camera.** [MOVED 2026-09-23 to phase 15 as its build task 0 (triage C-1): 15 is the
  first inbox phase to build and meets the same fallback; the rule below stays here as the specification, the build is 15's,
  and E16 re-runs phase 10 E10 / E13 on THIS build under it.] `StartPage.kt:150` and `:230` give a slot tile the content
  published under `LiveTileEngine.packageKey(<resolved app's package>)`, and `MusicFeed.publish` publishes the
  now-playing face under the session OWNER's package (`c.packageName`) and grows every tile standing for that
  package (`ActiveTiles.setPackage`). Today the only slot resolving to `app.tileshell` is MUSIC. After this
  phase PHOTOS and CAMERA resolve to it too (phases 15 / 16 add ALARMS-class and PEOPLE), so a playing track
  would put its face on the Photos tile and grow the Camera row tile. Rule (one permanent form, Rule 16): a
  session owned by the shell's own package is attributed to the in-APK app that owns it, read from the session's
  tag (`MediaController.getTag()`, API 29; Media3 `MediaSession.Builder.setId` sets it): a `music` session goes
  to the MUSIC slot's tile (phase 10 Q4 read at app granularity — "the tile of the app that owns the session",
  and Music is that app), a `video` session goes nowhere (Q4: an app with no tile shows nowhere; W10M's
  Movies & TV tile carried no now-playing). The same routing covers `ActiveTiles.setPackage`. This is an ADD to
  phase 10's part (MusicFeed) and to phase 01's (StartPage fallback), recorded in the INDEX Change Log when
  built; E16 re-runs phase 10 E10 / E13 under it. Flagged to the lead for phases 15 / 16, which meet the same
  fallback.
- 2026-09-22 (agent): **R11 gates FINAL.** Every visual and motion value in this doc is either "from R11
  §Photos / §Camera / §Movies & TV" (`docs/plan/r11/photos.md`, `docs/plan/r11/camera.md`, `docs/plan/r11/movies-tv.md`,
  landed 2026-09-23 and applied by T17-16 / T17-17 below; ~~pending … none written as of 2026-09-23 — C-12~~ SUPERSEDED
  2026-09-23 by T17-16) or a value this build already
  measured: phase 01's drawn status bar (`BarMetrics.STATUS_EPX`, `app/src/main/kotlin/app/tileshell/bars/SystemBars.kt:77-80`;
  its value is open at phase 01 against R11's 24 epx — C-17; Photos and Camera hide it on every page, the Movies & TV player
  hides it, its library pages draw it), list rows and letter groups (R3 C2 / R6 §5.1.4), the pivot header and
  its 250 ms settle (phase 10 task 6's MusicMetrics, P4 for the header), the tap-launch Start exit (R3 A11),
  Settings-page rows for each app's settings (R3 C1). Anything else is an approximation (Y rows below) with its
  own NEEDS-HUMAN row (RV9 / Q10). ~~R11 measured no motion for any of the three apps (no 60-fps source; r11/photos.md §4,
  camera.md §4, movies-tv.md §4), so every motion stays a tagged approximation (Y6, H4).~~ SUPERSEDED 2026-10-04 by r3
  D3–D5 / V16–V18: the pass-2 addenda (r11/camera-pass2.md §5, photos-pass2.md §5, movies-tv-pass2.md §5) bound most
  motions from 30-fps sources (± 33 ms); Y6 carries each measured bound and H4 keeps the motions still unmeasured. R11 measured
  Movies & TV's LOCAL half (My videos, the player; the 10586 build at native resolution); the Browse / Watch on / Media
  server screens were Microsoft's Store half or never existed and are P4 designs with accept rows, borrowing R11's
  store-half forms (1.5, 1.7.5, 1.7.10) where they apply (Y8).
- 2026-09-22 (agent): **the two kinds of NEEDS-HUMAN row are labelled** (R10 testability 26): *fidelity* rows
  ("matches R11 §x within tolerance", judged on the phone) and *accept* rows (a P4 design or an approximation
  with no footage; Jeremy accepts or overrules). qa/phase-17/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's
  shape and carries the kind in its "why it is his call" column.
- 2026-09-22 (agent): **the Photos tile already exists (phase 01; INDEX Change Log 2026-09-22 items 3 and 4:
  slideshow and picture frame).** The app is what the tile OPENS; PhotosFeed keeps publishing the tile's faces
  and is not replaced. The app has its own library index (images + videos, grouped, paged) because the tile's
  reader (the newest N as faces) is a different shape from a gallery, so it is not a second copy of one thing.
  Both watch `MediaStore` with a ContentObserver.
- 2026-09-22 (agent, P5 stated reason): **the camera library is CameraX** (androidx.camera, Apache-2.0). It is
  a Jetpack library, not a Google service — the same class as Media3, accepted in phase 10 Q8 — and it needs no
  Play services and makes no network request. Reason it is used over raw Camera2: its device-quirk layer
  (Samsung preview/rotation quirks are the documented cases), its video-capture pipeline, and its Extensions
  API, which is the ONLY public route to Samsung's HDR / Night / Bokeh vendor extensions (the AVD ships
  `com.android.cameraextensions`, `pm list packages` 2026-09-22). The pro dial (Q2 B) reaches manual controls
  through CameraX's Camera2 interop, so raw Camera2 is not needed for it either. Recorded per P5.
- 2026-09-22 (agent): **the AVD has one camera, facing back, and no front camera.** `dumpsys media.camera`
  2026-09-22: "Number of camera devices: 1", device "1", "Facing: Back"; config.ini `hw.camera.back=emulated`,
  `hw.camera.front=none`. Capture rows run on the virtual back camera; every front-camera, flash, HDR, zoom
  and quality row is phone-only (P1) with an accept H-row for image quality.
- 2026-09-22 (agent): **Samsung's side-key double press cannot be pointed at this camera by the shell.** The
  quick-launch is One UI's own setting with no public action; whether One UI's Side key page offers "Open app"
  for a third-party camera is recorded by P2, not assumed. The doc states the limit (PLAN "Android limits")
  rather than planning around it.
- 2026-09-22 (agent): **the video player owns its own ExoPlayer and its own media session, inside the video
  activity, and MusicService is untouched.** Media3 is already a dependency (phase 10 Q8), so no second media
  library ships (Rule 16 / RV2). A video needs a Surface bound to its activity and must not share Music's queue;
  the session exists for headset / Bluetooth buttons and for `dumpsys media_session` to show it, and it is the
  `video` session the routing rule above ignores. Audio focus is requested with ~~the same attributes phase 10
  uses~~ SUPERSEDED 2026-10-04 by r3 D16: USAGE_MEDIA + AUDIO_CONTENT_TYPE_MOVIE, handleAudioFocus true, the Media3 session
  built with `setId("video")` (the form of `recorder/RecorderPlayer.kt:27-34`), so a video pauses Music and Music does not
  resume by itself (phase 10 E9's rule).
- 2026-09-22 (agent): **Photos writes through `MediaStore.createWriteRequest` / `createDeleteRequest`** (the
  gallery API since API 30: one system consent dialog per batch, drawn by MediaProvider), which works with the
  permissions this phase holds. Phase 18's All-files access does not change Photos' code path, so there is one
  permanent form; whether the platform skips the dialog when that access is held is recorded in phase 18's rows,
  not assumed here. Edits are saved as a COPY ~~next to the original~~ (the original is never rewritten) — SUPERSEDED
  in part 2026-10-04 by r3 D7: MediaProvider takes a new image row only under DCIM/ or Pictures/ and a video row only under
  DCIM/, Movies/ or Pictures/, so the copy goes to the original's folder when that is allowed, else to Pictures/ or
  Movies/, through `media/MediaWrites` (the agent line of 2026-10-04).
- 2026-09-22 (agent): **processes.** Camera runs in `:camera` and the video player in `:video` (R10
  testability 27: capture and decode must not be able to take Start down), as `:speech` and `:ime` do;
  `ShellApp.onCreate` already returns early outside the main process (INDEX Change Log 2026-09-22, phase 05
  item 1), so neither process starts the feeds. Each has its own diagnostics ring with a dump path, read the way
  phase 05 reads the `:ime` ring (the `Diagnostics` ring is one in-memory deque per process,
  `app/src/main/kotlin/app/tileshell/diag/Diagnostics.kt:14-27`); ~~the path is named in this doc at build start (BS-7)~~
  SUPERSEDED 2026-10-04 by r3 D16 — phase 15's form, named now: each process's ring is printed by a `Service.dump()`
  running in that process (`recorder/RecorderService.kt:228-249`, read with `adb shell dumpsys activity service
  app.tileshell/<that service>`), a service its activities bind while they live, which also answers Settings >
  Diagnostics the way `SpeechClient.status()` answers it for `:speech` (`settings/DiagnosticsPage.kt:53`).
  ~~Photos runs in the main process.~~ SUPERSEDED 2026-10-04 by r3 D8: Photos' library and viewer run in the main process;
  the editor and trim run as `.photos.EditActivity` in `:photosedit`, a fourth process with its own ring and dump service.
  The hub's online code (`StreamingHandoff`, the catalogue cache, the media-server client) runs in `:video` with the
  player and the "Watch on" intent is started from there; `StreamingHandoff` itself is a plain object with no process of its
  own, so phase 20's Music calls it from the main process, and its stores (cache, server token, the TMDB key — Q-17-1 (a)) are files written
  temp-and-rename and re-read on use, so two processes never hold stale copies.
- 2026-09-22 (agent): **harness contracts every new window signs** (R10 testability 24, 25): the root of each
  activity's content sets `Modifier.semantics { testTagsAsResourceId = true }` as MusicActivity.kt and
  WeatherActivity.kt do, and the node that CARRIES each text a row reads has its own tag (phase 10's
  `music_pri:` / `music_sub:` lesson); every silent-empty state writes a diagnostics line the row asserts as well
  as the screen (`[photosapp]`, `[camera]`, `[video]` tags, wording in E18). Drivers symlink phase 03's
  qa/phase-03/scripts/lib.sh (INDEX 2026-09-22: phase 01 did the same); evidence lives under qa/phase-17/.
- 2026-09-22 (agent): **app list.** Three new launcher entries: "Photos", "Camera", and the video app whose
  label comes from the A10 branding module (W10M's own "Movies & TV" by default, swappable like every Microsoft
  name). They are excluded from the hold menu's Uninstall by `AppUninstall.canUninstall`'s self-package rule
  already (INDEX 2026-09-22 UNINSTALL; `app/src/main/kotlin/app/tileshell/applist/AppUninstall.kt:37-44`), and E2 proves
  it rather than assumes it. The app-list regression
  (phase 02's regress.sh pattern, R10 testability 35) runs once for this phase: the entries appear under their
  letters with no "New" caption (X14 reads the shell's firstInstallTime).
- 2026-09-22 (agent): **APK budget.** Phase 03 Decisions set ≤ 600 MB; the debug build measured 334,447,554
  bytes on 2026-09-22. ~~CameraX is the only new dependency; E17 checks the size after this phase.~~ SUPERSEDED
  2026-09-23 by T17-2: the new dependencies are CameraX (camera-core, camera-camera2, camera-lifecycle, camera-video,
  camera-view, camera-extensions; Apache-2.0), Media3 Transformer (`media3-transformer`, a module of the library already in
  the build, Apache-2.0 — video trim) and OpenCV (Apache-2.0 since 4.5; the `stitching` module for panorama; arm64-v8a only,
  phone build). E17 checks the total and the OpenCV delta (≤ 20 MB) against the pre-17 APK.
- 2026-09-22 (agent): **bars.** All three apps are shell-owned screens under phase 01's bar rule: Samsung's
  bars hidden (`hideSystemBars()`), the drawn W10M status bar and nav bar. ~~The viewfinder and the full-screen
  viewer / player draw the nav bar and hide the status bar … a candidate until R11 §Camera / §Photos confirms it (Y7).~~
  SUPERSEDED 2026-09-23 by T17-16 (R11 measured; Y7 closed): **every Photos page** (collection, albums, viewer, editor,
  settings) and **every Camera page** hide the status bar and draw the nav bar (r11/photos.md 1.1.1, camera.md 1.1.1, HIGH);
  **Movies & TV's library pages** (the ≡-pane pages, My videos, Browse, Media server, settings) draw phase 01's status bar
  (C-17) over the 48-epx #171717 header, one seamless band (movies-tv.md 1.1.1–1.1.2); **the player** hides the status bar
  and the header and draws the nav bar (1.6.1).
- 2026-09-23 (agent, review triage C-1): **the live-tile routing fix is phase 15's build task 0, not this phase's build
  task 2.** Reason: the inbox order stays 15 → 16 → 17, 15 meets the same package-keyed fallback (`feeds/MusicFeed.kt:159-171`
  publishes under the session owner's package and grows every tile of that package through `tiles/ActiveTiles.kt:51`;
  `start/StartPage.kt:150,173,230,232` hand a slot tile that package's content), and Rule 16 puts a part in the first phase
  that needs it. This phase depends on 15 and 16; its build task 2 becomes "E16 re-run on this build"; the routing rule
  above is the specification E16 checks.
- 2026-09-23 (agent, review triage C-2): **the `assignSlotOnce` guard stays phase 16's build task 1** (16 is the first
  seeder in the order); this phase seeds under it, corrects its wrong "left alone" claim (T17-5, above) and re-cuts E1's
  upgrade half to the case that matters: a hand assignment made on the pre-17 build ~~survives the 17 install~~ (SUPERSEDED
  2026-10-05 by Q-17-3 (a)) is replaced once by the 17 install with the take-over line, and a re-point made after it
  survives the next install. The Change Log
  line for phase 01 stays with 16.
- 2026-09-23 (agent, review triage T17-1): **Movies & TV online — the design below the Q3b ruling.** Everything here is the
  agent's under P3 except the two forks §2 of the triage puts to Jeremy (Q-A the film database and its key; Q-B the media
  server), ~~which are written conditionally below, marked "pending Q-A" / "pending Q-B" …~~ SUPERSEDED 2026-09-23 by
  T17-14: both are answered (Q-B Jellyfin; Q-A TMDB, its key route re-asked and ruled as Q-A2: B), and every block below is
  the one ruled form. Not a split: phases
  20 (Music streaming) and 21 (TV channels) hang on one named interface, and a "17b" would only move the gate.
  - **The interface, `StreamingHandoff`** (package `app.tileshell.video.handoff`; a plain Kotlin object whose table logic has
    JVM tests; phases 20 / 21 call it and build no second one): `installedServices()` — a per-service table (id, label,
    package, the deep-link form for a title, the web URL form, the search URL form) resolved against
    `PackageManager.queryIntentActivities` at call time (never cached across launches: an app installed a minute ago
    appears); `openTitle(service, title): Intent` — the service's title deep link where ~~the catalogue yields~~ Wikidata
    yields (T17-15, the agent line at the end of Decisions) that service's
    own id for the title, else the service's search URL with the title's name and year (the caller starts the intent;
    both intents carry `setPackage(<service package>)`, and an ActivityNotFoundException falls back to the search intent,
    then logs `not installed` — r3 D13);
    `catalogue.search(query)` / `catalogue.lookup(id)` — TMDB (Q-A); `mediaServer` — `connect(host, user, password)`,
    `library()`, `streamUrl(item)` — the Jellyfin client (Q-B). The services table's forms (Netflix, Prime Video, Disney+, Hulu, Max,
    Apple TV, Paramount+, Peacock, Plex, Jellyfin, YouTube — the last is the user's own app answering an intent, not a Google
    dependency of the shell, P5) cannot be verified from this doc: they are recorded at build start with their verification
    (BS-4), the way phase 20 records its forms; the only forms this doc FIXES are the QA-Flix fixture's.
  - **HTTP.** The triage's "OkHttp is transitive through Media3" is WRONG and is not written: `./gradlew :app:dependencies
    --configuration debugRuntimeClasspath --offline` (2026-09-23, media3 1.8.0 per `gradle/libs.versions.toml:10`) lists no
    `okhttp` at all, and `media3-exoplayer-1.8.0.pom` depends on media3 modules and androidx only (`media3-datasource-okhttp` is
    a separate module the build does not pull). The catalogue and the media-server API are fetched with `HttpURLConnection`, as
    `weather/WeatherProvider.kt:57` and `cortana/PlaceSaver.kt:102` already do; streams play through ExoPlayer's
    `DefaultHttpDataSource` (also `HttpURLConnection`-based). **No HTTP library is added.** `INTERNET` and
    `ACCESS_NETWORK_STATE` are already held (`app/src/main/AndroidManifest.xml:32-33`). Every connection runs under the
    network security config this phase creates (the C-16 Decision below): `targetSdk = 36` (`app/build.gradle.kts:22`) with no
    `usesCleartextTraffic` / `networkSecurityConfig` today means Android refuses every `http://` connection.
  - **Offline.** A 7-day catalogue cache in the app's files dir (`video_catalogue/`: search results and title pages as JSON,
    artwork through the image cache), `AtomicFile` per entry as `WeatherFeed` keeps its report. No network → the Browse page
    shows what was cached with one line "You're offline — showing what was saved" and the My videos page is untouched; nothing
    here ever blocks the local half. A11 as amended: offline preferred, the internet where the feature needs it.
  - **External `http(s)://` VIEW from another app now PLAYS in the shared player** (`[video] playing scheme=https`): the A11
    refusal is gone, the R10-Q4 line above is marked SUPERSEDED, E13 is re-cut. Schemes the player takes: `content`, `file`
    (the shell's own files only — a `file://` from another app never arrives, the platform refuses it on the sender), `http`
    (Q-D: A: A plays it, B and C refuse it with `[video] cleartext refused <host>` — the C-16 Decision), `https`. Any
    other scheme (`rtsp`, `ftp`, `smb`) shows "Can't play this address" with `[video] unsupported scheme=<s>` (the old
    `[video] refused scheme=<s>` line is retired; E18).
  - ~~**Pivots.** My videos / Browse / Media server. …~~ SUPERSEDED 2026-09-23 by T17-16: **the ≡ pane, not pivots**
    (r11/movies-tv.md 1.3; W10M's phone app used the pane in 2015 and 2017). Pane rows My videos / Browse / Media server (the
    last only when a server is set up; the dynamic shortcut mirrors it), the pane's bottom group → the app's settings page;
    tags `hub_pane:<myvideos|browse|mediaserver|settings>` replace `hub_pivot:<id>`, and "pivot" in the rulings above reads
    "page" for Movies & TV. R11 measures My videos and the player; Browse (a search box, section rows and poster strips, the
    title page with its "Watch on" rows), the "Watch on" row and the server sign-in and library pages are P4 designs
    (Y8–Y10, H10–H12) that borrow R11's store-half forms (Y8).
  - **Trust.** The media-server token is a credential the shell keeps; ~~(and, under Q-A C, the catalogue key)~~ ~~SUPERSEDED
    2026-09-23 by Q-A2: B — the TMDB read token is a `BuildConfig` field of builds made on Jeremy's PC only, never in the
    repo, a CI build or a published APK (build task 12).~~ SUPERSEDED 2026-10-05 by Q-17-1 (a) — so is the TMDB read token,
    pasted once into Movies & TV's settings and kept in the same store, never in a build, the repo, CI or a log (build task
    12). The triage named
    `EncryptedSharedPreferences` (androidx.security:security-crypto): it is not in the build (no `security` entry in
    `app/build.gradle.kts` or the resolved classpath, 2026-09-23) and the library is deprecated — its release notes, 1.1.0-alpha07
    (2025-04-09) and the 1.1.0 stable (2025-07-30): "Deprecated all APIs in favour of existing platform APIs and direct use of
    Android Keystore" (developer.android.com/jetpack/androidx/releases/security, read 2026-09-23). So the token is encrypted
    with an Android Keystore AES-256-GCM key (non-exportable, alias named at build start, BS-3) and stored in the app's private
    files dir; the password is never stored; no new dependency. The sign-in / token path (and, since Q-17-1 (a), the TMDB
    key setting and its stored token) gets the adversarial review the
    project requires for trust changes (the rule phase 04 states for its helper) as a gate before the phase is `done`.
  - **Credential hygiene (C-32; phase 20 cites this line).** No credential — the TMDB read token, the Jellyfin token, the
    fixture passwords — is ever written to a diagnostics line, logcat, a logged URL (stream URLs are logged with the query
    string removed: Jellyfin puts its token there) or an evidence file. ~~No QA build carries Jeremy's real key: every QA debug
    APK from this phase on is assembled with `./gradlew :app:assembleDebug -Ptmdb.readToken=qa-dummy-token` (the Gradle
    property wins over local.properties, build task 12), and a CI-form APK with `-Ptmdb.readToken=` (empty).~~ SUPERSEDED
    2026-10-05 by Q-17-1 (a) — no build carries any TMDB token: the owner's lives only in his phone's credential store, and
    an AVD row types the dummy `qa-dummy-token` into the same setting. Every row that
    touches a credential ends with the leak scan `qa/phase-17/scripts/leak_scan.sh <paths…>` (T17-13): it reads ~~every
    `tmdb.*` value from local.properties without echoing it, plus the tokens and passwords the row names~~ the tokens and
    passwords the row names (the typed TMDB token among them) plus every `tmdb.*` value local.properties still holds (the
    build reads none since Q-17-1 (a); one left there is scanned for, so a build or a log that picked it up fails), without
    echoing any, and finds zero
    matches in `qa/phase-NN/**`, the row's saved ring slices (`ring-*.txt`, C-20) and `adb logcat -d` (gated, a match fails
    the row and prints only the file name).
  - ~~**Pending Q-A — the catalogue source.** Written for all three forms …~~ SUPERSEDED 2026-09-23 by T17-13 / T17-14 (Q-A
    TMDB, Q-A2: B). **The catalogue source (TMDB).** TMDB's v3 API with the read token as `Authorization: Bearer <token>`
    (no `api_key` parameter anywhere); the token is ~~`BuildConfig.TMDB_READ_TOKEN`, filled from the Gradle property
    `tmdb.readToken` first and the gitignored local.properties second, empty when neither has it — so a build made on
    Jeremy's PC has it and a CI build (no local.properties, no secret) does not. An empty token → the one no-key form: the
    Browse page shows "Film search is off in this build", `[video] catalogue: no TMDB key in this build`, and no request is
    made~~ (SUPERSEDED 2026-10-05 by Q-17-1 (a)) the one saved in Movies & TV's TMDB key setting (Trust's store), read on
    use; none saved → the one no-key form: Browse says film search needs a key, with a link to the setting, `[video]
    catalogue: no TMDB key saved`, and no request is made; a key TMDB refuses (401) → the bad-key form (build task 12)
    (the hub's local half unaffected). TMDB's attribution ("This product uses the TMDB API but is not endorsed or
    certified by TMDB", with its logo) shows in Movies & TV's About and at the Browse page's foot (`hub_attribution`);
    "where to watch" comes from `/watch/providers` (JustWatch data under TMDB's terms, credited "JustWatch" where shown); a
    title's per-service ids come from Wikidata (T17-15). Image URLs are built from `/3/configuration`'s
    `images.secure_base_url`. Rate limits and terms are re-read at build start (BS-5) and recorded here.
  - ~~**Pending Q-B — the media server.** … **B (Plex)** … **C (both)** …~~ SUPERSEDED 2026-09-23 by T17-14 (Q-B:
    Jellyfin only; BS-6 struck). **The media server (Jellyfin).** REST — `POST /Users/AuthenticateByName` → `AccessToken`,
    sent as `Authorization: MediaBrowser Token="…"`; `/Users/{id}/Items` for the library; `/Videos/{id}/stream` (direct
    play) — the client is thin, no SDK, no Plex code. Transcode versus direct play is a build-time call recorded here. A
    server at a plain `http://` address works per Q-D A (the C-16 Decision), and a sign-in to a non-private `http://`
    address asks first.
  - **Diagnostics** (E18): `[video] catalogue "<q>": <n> | offline | error <code>`, ~~`[video] catalogue: no TMDB key in this
    build`~~ `[video] catalogue: no TMDB key saved` (SUPERSEDED 2026-10-05 by Q-17-1 (a)), `[video] watch-on <service> "<title>" -> <intent> | not installed`, `[video] watch-on <service> "<title>": id
    <found|none> (wikidata)`, `[video] server <host>: connected | unreachable | unauthorised | cleartext refused | insecure,
    asked`, `[video] server token cleared`, `[video] playing scheme=<s>`, `[video] unsupported scheme=<s>`, `[video]
    cleartext refused <host>`, `[video] cannot reach <host>`.
  - **Stale lines struck** (T17-1): the header's "interview pending", Goal "Nothing here uses the internet (A11)", Scope Out's
    "streaming or any http source", Build task 7's "explicit refusal for non-file schemes", E13, E18's `refused scheme`, Y5 /
    H3's "the player only".
- 2026-09-23 (r2 triage C-16, a doc update; the base policy Q-D: A): **network security — one config, https-only fixed
  endpoints, a debug-only fixture exception, a GATE.** `targetSdk = 36` (`app/build.gradle.kts:22`) and a manifest with no
  `usesCleartextTraffic` / `networkSecurityConfig` (`app/src/main/res/xml/` holds only `method.xml`,
  `recognition_service.xml`, `voice_interaction_service.xml`) mean Android refuses every `http://` connection today: about
  one in three popular radio stations (the triage's radio-browser probe), a home Jellyfin at `http://host:8096`, `http://`
  VIEWs and every `http://10.0.2.2` fixture. This phase (the first builder) creates `app/src/main/res/xml/network_security_config.xml`
  and the manifest's `android:networkSecurityConfig` (build task 17); phase 20 reuses it and adds no second config.
  (1) **Fixed endpoints are https-only by the platform:** a `<domain-config cleartextTrafficPermitted="false">`
  (`includeSubdomains="true"`) listing every host the shell itself calls — `api.open-meteo.com`,
  `nominatim.openstreetmap.org` (the only two in `app/src/main/kotlin` today), `api.themoviedb.org`, `image.tmdb.org`,
  `query.wikidata.org` (T17-15), `api.radio-browser.info`, `musicbrainz.org`, `coverartarchive.org` (+ its redirect host,
  recorded at build start), and phase 08's model host when it exists; a `FixedEndpoints` constant set with a JVM test that
  every URL in it is `https://`; at process start the shell logs `[net] cleartext permitted for <host>: <bool>`
  (`NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)`) for each fixed host — rows assert `false`.
  (2) **Base config — Q-D: A** (the triage's lean first): **A (lean)** cleartext permitted at the base (station and
  server hosts cannot be listed), (1) holding; **B** base permitted plus a code guard in the media data-source factory that
  refuses `http://` except to a private address (RFC 1918, link-local, `.local`) for the media-server client — `http://`
  stations and VIEWs are refused (`[video] cleartext refused <host>`, phase 20's `[music] stream: cleartext refused`); **C**
  base not permitted — the platform refuses every `http://` (the same lines, from the caught
  `CleartextNotPermittedException` / `UnknownServiceException`).
  (3) **Debug-only QA exception:** `app/src/debug/res/xml/network_security_config.xml` (the debug source set's copy
  replaces main's) adds `<domain-config cleartextTrafficPermitted="true"><domain>10.0.2.2</domain></domain-config>`, and B's
  code guard honours the same host only when `BuildConfig.DEBUG`, so host fixtures run under every answer; E17 checks the
  RELEASE APK's config holds no `10.0.2.2` (`aapt2 dump xmltree --file res/xml/network_security_config.xml <release apk>`).
  (4) **Schemes:** the player's VIEW list (T17-1 above) and phase 20's T20-5 http / https allow-list for stations.
  (5) **Credentials:** under A, "Add a server" at a non-private `http://` address asks first — "This server isn't secure —
  your password would be sent unencrypted" — and sends nothing until the user taps Continue (`[video] server <host>:
  insecure, asked`); under B it is refused, under C the platform refuses (`[video] server <host>: cleartext refused`);
  tokens never appear in a logged URL (C-32).
  (6) **Rows:** E13 (an `http://` VIEW plays under A / is refused with its line under B and C; the fixed-host `[net]` lines
  read `false`), E22 (the insecure-server prompt or refusal), E17 (the release-config check).
  (7) **GATE, specified for every branch:** the adversarial review (team-review, adversarial mode) recorded under
  `qa/phase-17/` before this phase is `done` — under A of the base-permitted config, the fixed-host domain-config with its
  JVM test and the insecure-server prompt; under B the same plus the private-address guard (its JVM test accepting RFC 1918 /
  link-local / `.local` and refusing a public address, a public name and a global IPv6 address) and the debug-only bypass of
  it; under C of the config, the debug-only exception and the release check. Phase 20 re-runs it on its station path as its
  own gate.
- 2026-09-23 (agent, review triage T17-2): **Q2 C per mode.** **Pro dial** through `Camera2CameraControl` (CameraX's
  Camera2 interop), each control gated by `CameraCharacteristics` — ~~manual exposure needs `CONTROL_AE_AVAILABLE_MODES` to
  contain OFF and `REQUEST_AVAILABLE_CAPABILITIES` MANUAL_SENSOR (ISO within `SENSOR_INFO_SENSITIVITY_RANGE`, shutter within
  `SENSOR_INFO_EXPOSURE_TIME_RANGE`),~~ manual focus needs `LENS_INFO_MINIMUM_FOCUS_DISTANCE` > 0, ~~white balance needs
  `CONTROL_AWB_AVAILABLE_MODES` to contain OFF~~ (the exposure, ISO, shutter and white-balance gates SUPERSEDED 2026-10-04
  by r3 D10 — the agent line of that date); a missing control is hidden with `[camera] mode pro.<control>: unavailable
  (<reason>)`. **Panorama** = a NEW dependency, OpenCV (Apache-2.0) `Stitcher`, the `stitching` module only, `arm64-v8a` only in
  the phone build; on the AVD (x86_64) the mode is hidden with `[camera] mode panorama: unavailable (no native library for
  x86_64)`, which is the row that proves the hidden path. This corrects "CameraX is the only new dependency" and adds an APK
  delta bound (OpenCV ≤ 20 MB) to E17; the licence check is build-start check **BS-1** (the Q2 Decision's "licence checked at
  build start, P5" means BS-1 — P5 is the pinch-zoom row; the ruling's line is left as written). **Slow motion** = a Camera2
  constrained high-speed session where `REQUEST_AVAILABLE_CAPABILITIES` lists CONSTRAINED_HIGH_SPEED_VIDEO, else hidden
  (`[camera] mode slowmo: unavailable (no high-speed session)`); whether the pinned CameraX version reaches it through interop
  or that one mode drops to raw Camera2 is **BS-2**, recorded here. **Living Images** = the Motion Photo container (one JPEG
  with a trailing MP4 and XMP `Camera:MotionPhoto=1`, `MotionPhotoPresentationTimestampUs`, `Container:Directory` item
  lengths — one file, no sidecar) so Samsung Gallery reads it too; Photos shows a glyph on the still and plays the clip on a
  hold. Build tasks 6a–6d; E7's mode-availability row proves the list both directions on the AVD; P9–P12 prove the modes on
  the phone ~~with file-level observables (EXIF, width, `ffprobe` frame rate, the XMP)~~ — SUPERSEDED 2026-10-04 by r3 V15:
  on the phone alone, the file facts read through the probe page (the agent line of 2026-10-04), and Living Images' file
  checks made on the AVD (E7).
- 2026-09-23 (agent, review triage T17-3 — the editor's transforms, so E6 can be host-computed): filters, light, colour
  and auto-enhance are fixed 4 × 5 colour matrices, one per tool setting, whose numbers are ~~recorded here at build start~~
  written into this Decisions section before task 5 starts (SUPERSEDED in part 2026-10-04 by r3 V12) (the look is H13's
  call); `qa/phase-17/scripts/edit_expect.py` ~~applies the same matrix~~ reads the matrices from this doc (r3 V12) and
  applies them on the host to each flat-colour fixture and E6 compares ± 4 per channel; red-eye halves the red channel inside detected discs (a red-dominant blob, `r >
  1.5·max(g, b)`, ≥ 6 px across) and changes nothing outside; straighten rotates by the chosen angle and centre-crops to the
  largest axis-aligned rectangle (output dimensions host-computed); crop and rotate as before; video trim through Media3
  Transformer (no new library). Every edit is a copy (IS_PENDING then cleared); an edited HEIC saves as JPEG.
- 2026-09-23 (agent, review triage T17-4 — capture-intent guards, a trust change): the Camera accepts a `content://`
  `EXTRA_OUTPUT` only and refuses `file://` (`[camera] refused output scheme=file`, RESULT_CANCELED — E9's negative); it
  writes ~~only through `contentResolver.openOutputStream(uri)` under the caller's URI grant,~~ (SUPERSEDED 2026-10-05 by
  r3 D1: `openOutputStream` runs as the shell and writes wherever the shell can) only to a URI the three-condition output
  guard accepts (the agent line of 2026-10-05), never by path (`:camera` holds the
  shell's own READ_MEDIA_* grants and, after phase 18, All-files access, so a path write would let any caller aim it at
  shared storage); EXIF location is stripped for a third-party caller (Android's own camera does); ~~an intent capture leaves
  NO copy in DCIM~~ (SUPERSEDED 2026-10-05 by r3 D1) a capture to an accepted EXTRA_OUTPUT leaves no copy in DCIM, and a
  request with no EXTRA_OUTPUT follows the no-output contract (the same line); RESULT_CANCELED leaves no `is_pending` row (E9). Adversarial review at build, the project's rule for trust
  changes, before `done`.
- 2026-09-23 (agent, review triage T17-9 / C-9): **"Media server" is a DYNAMIC App Shortcut** (`ShortcutManager
  .setDynamicShortcuts`), published when a server is set up and removed when it is not; My videos and Browse are static.
  Reason: phase 11's selection rule takes manifest shortcuts before dynamic ones, so a conditional screen ranks after the
  fixed ones by a stated choice, and a shortcut to a page that does not exist is never shown. E23 proves it (`dumpsys
  shortcut` before / after set-up; a burst on a pinned Movies & TV tile shows 2 then 3 satellites). **Every dynamic shortcut
  calls `ShortcutInfo.Builder.setActivity(<its app's launcher activity>)`** (C-21: without it a dynamic shortcut attaches to
  the package's first MAIN / LAUNCHER activity, `MusicActivity`, `app/src/main/AndroidManifest.xml:129-144` (cite fixed
  2026-10-04, r3 D16; it is still the first), and would
  burst on the Music tile under phase 11's per-activity query) — `video_mediaserver` → `VideoActivity`.
- 2026-09-23 (agent, review triage C-5): **motion is timed by the shell's own clock.** Every motion here (viewer open /
  close, photo swipe, camera mode switch, the player's controls fade, the slideshow step) logs `[motion] <name> t0=<uptime>
  peak=<ms> overshoot=<%> settle=<ms> frames=<n> maxGapMs=<ms>` (the last two C-31) from `withFrameNanos`; the row asserts
  the logged numbers against RV11's tolerance and `maxGapMs` ≤ 33.4 ms (2 vsync); a
  screenrecord corroborates under phase 05's frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion) and is
  never the primary clock. Reason: the P02 lesson — variable-rate screenrecord cannot be the clock. Y6 and E5 follow.
- 2026-09-23 (agent, review triage C-9 applied to Camera): **Camera's Panorama and Slow motion shortcuts are DYNAMIC too**,
  published by CameraActivity on each start only when E7's capability gates admit the mode (ranks 2–3, after the static Photo
  and Video) and removed when they do not; Photos' two and Movies & TV's My videos / Browse stay static. Reason: the
  2026-09-23 shortcut line leaves out a mode the phone cannot do, and a manifest shortcut cannot be withdrawn at run time —
  `ShortcutManager.disableShortcuts` throws `IllegalArgumentException` for immutable (manifest) shortcuts (AOSP
  `core/java/android/content/pm/ShortcutManager.java`, its javadoc, read 2026-09-23) — so a static entry would burst a mode
  the viewfinder then hides. Both call `setActivity(CameraActivity)` (C-21).
- 2026-09-23 (review triage C-4, a doc update): **this phase's two grants join the setup wizard.** Camera (`CAMERA`) and Videos
  (`READ_MEDIA_VIDEO`) are Setup checklist rows and therefore wizard steps (phase 12 Q1 / Q2, the `wizard_step:setup:<id>`
  namespace of phase 12 T12-1) with the why lines in build task 8; `qa/phase-03/scripts/provision.sh` gains their `pm grant`
  lines so a wiped device skips the wizard; E25 is phase 12 E14's template for them. Phase 12's rule, restated: a finished or
  skipped wizard is never re-summoned on that install — a grant this phase adds goes red on the Setup checklist instead.
  (C-15, 2026-09-23: `provision.sh` also writes the wizard's finished marker, phase 12's lead Decision; `PROVISION_FINISH_WIZARD=0`
  skips it, and E25 is the three-part form.)
- 2026-09-23 (agent, r2 triage T17-15): **"Watch on <service>" resolves each service's own title id from Wikidata.** TMDB's
  `/watch/providers` returns provider names, logos and one TMDB link — never a service's own title id — so `openTitle`'s
  deep-link branch would almost never run and Q3b A's "opening that app at the title" would mostly land on search.
  `StreamingHandoff` looks the title up by its TMDB id through the keyless Wikidata Query Service (`query.wikidata.org`, its
  streaming-service title-id properties, e.g. "Netflix ID"; the property list and each one's coverage recorded at build
  start with BS-4); a found id → that service's title deep link, none → the service's search URL with title and year
  (`[video] watch-on <service> "<title>": id <found|none> (wikidata)`). `query.wikidata.org` joins the fixed https-only
  endpoints (C-16); QA points it at `catalogue_server.py` through the debug-only pref `qa_wikidata_base`, answering a
  Wikidata-shaped query for QA-Flix's fixture property; E21 asserts both branches; H11 tells Jeremy many titles will land on
  the service's search. Reason: it keeps Jeremy's ruled behaviour wherever public data allows, with no key and an honest
  fallback.
- 2026-09-23 (agent, r2 triage T17-16): **R11 applied; Movies & TV takes W10M's ≡ pane, not pivots.** The pane
  (r11/movies-tv.md 1.3: a 256-epx overlay with no scrim, #171717, 48-epx rows from the 72-epx chrome bottom, glyph cx 24,
  label x 48, the current row's label and glyph in accent with a 4 × 48-epx accent bar at x 0; the bottom group → the app's
  settings page) holds My videos / Browse / Media server (the last only when set up); `hub_pane:<id>` replaces
  `hub_pivot:<id>`; the library is the 112-epx tile grid, 2 across at 360 epx, with the 2-line clipped caption (1.4); the
  player's scrubber is its own (1.6.6–1.6.10), not Groove's; Browse borrows 1.5.2 / 1.5.3 / 1.7.5 / 1.7.10 ~~with 2 posters
  across and 12-epx gutters~~ (SUPERSEDED 2026-10-04 by r3 D5: one strip of 112 × 160 posters on a 120 pitch, Y8). Photos:
  3 columns at 360 epx, the status bar hidden on every page, the measured viewer chrome and menus, ~~the editor as a panel
  (LOW)~~ (SUPERSEDED 2026-10-04 by r3 D4: the 48-epx bottom strip, Y11), P4 marks on four editor tools and the trim screen
  (H13a / H13b). Camera: no mode strip in any version, the measured five-arc pro dial, the settings page per 1.7 / §2 minus
  Lenses, OneDrive and "Related settings", the capture-intent accept / retake UI a P4 design (H20). E19 is written now; ~~all
  motion stays UNMEASURED (Y6, H4)~~ (SUPERSEDED 2026-10-04 by r3 D3–D5: the pass-2 bounds, Y6). Reason: fidelity (A4) — W10M's phone app used the pane in 2015 and 2017 (H3 judges it); every other value is R11's
  measurement or its tagged approximation.
- 2026-09-23 (agent, r2 triage T17-17): **three version choices on R8 H-M1's precedent — build the governing / later form,
  a NEEDS-HUMAN row judges it.** Photos: V-2016+ for the collection (the final release's app; one native capture, G1) and
  V-2015 geometry for the pages V-2016+ has no capture of (R11's lean) — H17 (the viewer, editor and trim now have
  V-2016+ / production captures and take those forms under this same rule — r3 D4, the agent line of 2026-10-04). Camera:
  the V-2017 form (72-epx shutter disc, 32-epx mode discs at ±60 epx~~, the top toggle capsule, settings and camera-roll
  corner cells) rotated into portrait from the one landscape capture, K11, as a tagged approximation — the final portrait
  viewfinder is uncaptured (camera.md UNMEASURED-1) — with a fidelity note against K11,~~), keeping the measured V-2015 pro
  dial — H18 (SUPERSEDED in part 2026-10-04 by r3 D3: the portrait viewfinder is measured, MEDIUM, in
  r11/camera-pass2.md §1 / §4 — the vertical capsule on the right edge, settings bottom-right, switch top-right, roll
  bottom-left — so Y3 is MEASURED under H2 and H18 judges only the version choice). Movies & TV:
  the 10586 geometry for everything measured plus the ~~2017~~ −10 / +30 skips and the ~~2017 "•••" menu (cast / zoom to
  fill / repeat)~~ "•••" menu (SUPERSEDED 2026-10-04 by r3 D5: the skip row shipped in v3.6.1867, 2016-03-30, on 10586; the
  menu is Dec 2016's Cast / Zoom to fill / Repeat / Autoplay — r11/movies-tv-pass2.md §4) (R11's lean) — H19. All three [accept], judged on the phone. Reason: R8 H-M1 built Groove's governing form under the same
  split, and each choice is user-visible (the triage's §2b lists them for Jeremy).
- 2026-09-23 (agent, r2 triage T17-19): **no `FOREGROUND_SERVICE_CAMERA`.** Build task 1 drops it: recording stops on
  screen-off and CameraX runs in the activity's lifecycle, so no camera foreground service exists. Reason: no permission
  without a user.
- 2026-09-23 (agent, r2 triage C-17): **the status bar is cited, never hard-coded.** R3 C4 read 28 epx on Start; every in-app
  measurement since reads 24 (Movies & TV's library, r11/movies-tv.md 1.1.1, among them). This doc cites phase 01's drawn
  status bar (`BarMetrics.STATUS_EPX`, 28 today, `app/src/main/kotlin/app/tileshell/bars/SystemBars.kt:77-80`), so phase
  01's re-measure (INDEX research row "R3 C4 re-check") needs no edit here. Reason: re-measure before anyone rules; stop
  hard-coding 28 now.
- 2026-10-04 (agent, r3 triage D3, D4, D5, V16, V17, V18): **the R11 pass-2 addenda applied** (`r11/camera-pass2.md`,
  `r11/photos-pass2.md`, `r11/movies-tv-pass2.md`, 2026-10-02; where an addendum corrects its section, its §4 governs).
  Under T17-17's own rule (build the governing / later form): Camera's portrait viewfinder is now measured (MEDIUM: a 2016
  portrait screen recording cross-checked against native landscape captures), so Y3 is MEASURED under H2 and H18 judges only
  the version choice; Photos' viewer, editor and trim take their V-2016+ / production forms (the viewer bar Share · Edit ·
  Delete · More at 218 / 150 / 82 / 24 on black under the #171717 header; the editor's 48-epx bottom tool strip with the Edit
  sheet on the screen bottom; the Nov-2015 trim with its top bar, because R11's 1.11 still is the Add-ins frame picker), and
  H13a becomes *fidelity*; Movies & TV's player is the skip-button version (v3.6.1867, 2016-03-30, on 10586) with the
  Dec-2016 "•••" menu, the remaining-time label and letterbox by default. The addenda bound most motions from 30-fps
  sources, so Y6 carries the measured bounds (± 33 ms) and H4 keeps what is still unmeasured. E19 checks LOW values for
  order and presence only. Values in Y2, Y3, Y5, Y6, Y8, Y11, Y12, Y13 and E19; the struck stand-ins are listed above the
  Approximations table.
- 2026-10-04 (agent, r3 triage D6): **the three apps follow the in-shell app pattern as built; other apps reach helper
  activities, not the apps.** `PhotosActivity`, `CameraActivity` (the CAMERA slot) and `VideoActivity` (the hub) are
  `singleTask` with `onNewIntent` and their own task affinity, phase 15 / 16's form (`AndroidManifest.xml:152-165`,
  `:501-533`). Other apps' entry points are helpers in standard launch mode, not catalog entries (L14-2):
  `.photos.ViewerActivity` (VIEW image/*), `.video.PlayerActivity` (VIEW video/*, and the target of Photos' and the hub's
  explicit launches; `:video`) and `.camera.CaptureActivity` (IMAGE_CAPTURE / VIDEO_CAPTURE; `:camera`) — the last kept:
  the capture answer stays (Q-17-2 (b), 2026-10-05). Reason: a launch from Photos, Files or a capture caller runs in the caller's
  task, and BackHistory counts every catalog class (`start/BackHistory.kt:40-41`, `:64-65`) and resolves it to that app's
  launcher entry (`:83-84`), so with the intents on the launcher activities Back on Start opened Movies & TV's My videos
  after a video watched from Photos, and the shell's Camera after a capture for another app. Shortcuts: one file per app,
  declared on its own activity (`shortcuts_photos.xml`, `shortcuts_camera.xml`, `shortcuts_video.xml`, in
  `shortcuts_people.xml`'s VIEW + `page` / `mode` extra form), as every shell app does (`@xml/shortcuts_music`,
  manifest:143), so phase 11's per-activity query finds them. `startFeeds` has no process guard (`ShellApp.kt:217-229`), so
  after an in-place grant only `PhotosActivity` (main process) calls `startFeeds("photos grant")`; the `:camera`, `:video`
  and `:photosedit` activities never call it. Task 1, task 15, E3, E11, E13.
- 2026-10-04 (agent, r3 triage D7): **one MediaStore write layer.** `app.tileshell.media.MediaWrites`, owned by this phase
  and used by Photos and Camera, sits behind a `MediaStorePort` (insertPending / openWrite / publish / abandon /
  deleteRequest / readBack) with the real port and a recording fake — phase 16's standard (`ContactsPort` in
  `people/PeopleWrites.kt`, its read-back at `:119`). JVM tests prove: (1) no op opens an existing row for writing, except a
  capture caller's URI that the r3 D1 output guard accepts (Q-17-2 (b)); (2) every insert goes pending → written → published, and a failed write
  abandons its own pending row; (3) the cleanup deletes only rows whose OWNER_PACKAGE_NAME is the shell. Every publish is
  read back (row present, IS_PENDING 0, SIZE > 0, MIME, RELATIVE_PATH), or the op fails with its `failed:` line. Copy
  placement: the original's RELATIVE_PATH when MediaProvider allows that folder for the type (images DCIM/ or Pictures/;
  videos DCIM/, Movies/ or Pictures/), else Pictures/ or Movies/, and the op's line says where. Output formats: edited
  stills are JPEG at quality 95 (PNG when the original is PNG), saved upright with EXIF Orientation 1 and DATE_TAKEN /
  DateTimeOriginal copied from the original; trims are MP4 (H.264 / AAC) keeping the rotation and DATE_TAKEN.
- 2026-10-04 (agent, r3 triage D8): **full-resolution photos stay out of the launcher's memory.** The S25U's main sensor is
  200 MP: one ARGB decode is about 800 MB, over the app's memory limit, and a Canvas refuses a bitmap over 100 MB. The
  viewer decodes at screen size (ImageDecoder's target size) and re-decodes the zoomed region with BitmapRegionDecoder; the
  editor works on a ≤ 4096-px preview and renders the saved copy tile by tile; the editor and trim run as
  `.photos.EditActivity` in `:photosedit` (R10 testability 27's reason, which already moved capture and decode out of the
  launcher's process). P16 proves it on the phone.
- 2026-10-04 (agent, r3 triage D9): **"Set as Start background" copies the picture.** A MediaStore URI that Photos found by
  its own query has no grant to persist (`takePersistableUriPermission` throws, and `settings/ThemePresetsUi.kt:235`'s
  `runCatching` hides it), so the shell decodes the image at screen size, writes it to `files/backgrounds/<id>.jpg`, points
  `backgroundUri` at that file and logs `[photosapp] set as background <id> -> <file>`; the background survives a revoked
  or partial photo grant (E5).
- 2026-10-04 (agent, r3 triage D10): **the pro dial's gates** (they replace T17-2's exposure, ISO / shutter and
  white-balance gates; manual focus is unchanged): exposure (EV compensation, which works with auto-exposure on) is shown
  when `CONTROL_AE_COMPENSATION_RANGE` ≠ [0, 0]; ISO and shutter need AE OFF + MANUAL_SENSOR (ISO within
  `SENSOR_INFO_SENSITIVITY_RANGE`, shutter within `SENSOR_INFO_EXPOSURE_TIME_RANGE`), and when only one is set the other is
  held at the last AE result (SENSOR_SENSITIVITY / SENSOR_EXPOSURE_TIME), EV disabled while either is manual; each
  white-balance preset is shown only if `CONTROL_AWB_AVAILABLE_MODES` lists it. E7 derives its expected list from the same
  keys. Reason: in Camera2 AE OFF makes ISO and shutter manual together, and AWB OFF means manual colour gains
  (MANUAL_POST_PROCESSING), a gate that would hide presets that work.
- 2026-10-04 (agent, r3 triage D11): **slow motion looks slow in every player.** The take is captured at the session's high
  rate and encoded at 30 fps, so the file itself plays slowed (the capture-rate-above-playback-rate model of MediaRecorder's
  `setCaptureRate` / `setVideoFrameRate`); it has no audio track; `[camera] slowmo <n> fps captured, encoded at 30 -> <uri>`.
  Task 6c, P11.
- 2026-10-04 (agent, r3 triage D12, V4): **video sound needs RECORD_AUDIO.** The shell already holds it for Tess
  (`AndroidManifest.xml:39`; the Setup row "microphone", `CortanaChecklist.kt:47`). A video records sound only while it is
  held; otherwise the take is silent, with `[camera] video sound: off (no microphone permission)` and a one-line hint naming
  the Microphone row. No AVD row or edge sub-step uses the microphone or the host's audio (no `audio.sh`, no `pactl`): one
  that records video revokes RECORD_AUDIO for its span and grants it back, and asserts one video stream and no audio stream;
  the sound itself is a phone row (P1).
- 2026-10-04 (agent, r3 triage D13): **"Watch on" names the service's package.** Since Android 12 an http(s) VIEW with no
  package opens an app only for a verified domain, else the default browser, and `qa-flix.test` cannot be verified; so
  `openTitle` sets `setPackage(<service package>)` on the title link and the search link, and on ActivityNotFoundException
  falls back to the search link, then logs `not installed` (task 11, E21).
- 2026-10-04 (agent, r3 triage D16, V5): **a `video` session lands on no tile and in no pod.** MusicFeed drops a shell
  session that routes to no tile (`feeds/MusicFeed.kt:161-167`) and logs `[music] session app.tileshell id=video -> none`
  (`:164`; `TileRouting.kt:44-51`), so a video never shows in the pod bay's Now playing pod either. The player's Media3
  session is built with `setId("video")` (task 7), and E11 asserts the line while the video plays.
- 2026-10-04 (agent, r3 triage V12): **the editor's matrices are fixed before the editor is built.** The 4 × 5 matrix of
  every filter, light, colour and auto-enhance setting is written into this Decisions section before task 5 starts, and
  `edit_expect.py` reads them from this doc, so a build cannot grade itself with its own numbers; each tool's copy of a
  fixture differs from the original by ≥ 16 on at least one channel (E6 fails an identity matrix); "Save a copy" re-reads
  the original at full resolution (D8's tiled render), so a file removed after the tool is applied and before Save fails
  the save (E6's failure leg).
- 2026-10-04 (agent, r3 triage V15): **phone rows are done on the phone alone** (INDEX R4: no PC or USB step; phase 16's
  form). A value a P row needs is read on a page of the shell — Settings > Diagnostics, which also shows the `:camera`,
  `:video` and `:photosedit` rings through their dump services (Decisions, processes), or the app's own page — and pasted
  back. File facts (EXIF, XMP, tracks, frame rate, dimensions, size) are read on the AVD where the AVD can make the file
  (E6b, E7, E8); where it cannot (the front camera, 200 MP, real sensors, the S25U's own recordings) they are read on the
  phone through a **probe page** under Settings > Diagnostics: for a picked MediaStore item it shows ExifInterface's tags,
  the Motion Photo XMP, MediaExtractor's track list with each track's MIME and frame rate, the duration, width × height and
  size; it also lists CameraManager's camera ids with the characteristics E7 reads, and the shell's own processes with each
  one's PSS (`ActivityManager.getProcessMemoryInfo`) — every value as text the Diagnostics copy carries. No P row uses
  adb, `exiftool` or `ffprobe`. Every P row runs on the CI build the phone installs (Q-17-1 (a): no build carries the TMDB
  key); P13, P15, H10 and H11 after the owner pastes his key into Movies & TV's settings.
- 2026-10-04 (agent, r3 triage V20): **the Photos checklist row stays READ_MEDIA_IMAGES / READ_MEDIA_VISUAL_USER_SELECTED**
  (`onboarding/Checklist.kt:170`); READ_MEDIA_VIDEO is the Videos row alone, so this phase adds exactly two wizard steps
  (E25 (a)'s "Step 1 of 3").

### Approximations (R11 applied 2026-09-23, T17-16 / T17-17; each row has an H-row)
"Value used" is R11's measurement where the status says MEASURED, else the tagged approximation. Each pre-R11 stand-in is
SUPERSEDED 2026-09-23 by T17-16 / T17-17 (4-across grid, 48-epx viewer header class, the top control row and horizontal mode
strip, the quarter-arc dial from the shutter, Groove's scrubber with ±10 s and app-list library rows, the 3-across 2-epx
poster grid, the 48-epx editor tool strip). The R11 pass-2 values (applied 2026-10-04, r3 D3–D5 / V16–V18) SUPERSEDE these
2026-09-23 values: Y2's Share · Favorite · Edit · Delete · More bar at 286 / 218 / 150 / 82 / 24 on #171717; Y3's rotated
K11 approximation (the #666666 shutter, the capsule along the top, settings top-right, the switch top-left, the preview
centred above the nav bar, grid and Living Images in the capsule); Y5's total-time label, Ø 24 thumb, INFERRED transport
centres 84 / 132 / 180 / 228 / 276 and three-item menu; Y6's fade-in-from-black viewer, 290-ms swipe settle, 133-ms pane,
3-s auto-hide and mode-switch cut; Y8's 112 × 168 posters on a 124 pitch, 2 across; Y11's F2 panel; Y12's ≈168-epx band
centred at ≈427; Y13's centred mm:ss.cc readout and R8 hollow-ring handles.

| # | Value | Status | Value used | H-row |
|---|---|---|---|---|
| Y1 | Photos collection: pivot header, month headers, grid, video tiles, album tiles | MEASURED (r11/photos.md 1.1–1.4); version per T17-17 | V-2016+ collection form (mixed-case ≈28-epx pivot titles Collection / Albums on the black page, no header band, no underline; ≈15-epx accent month header; day row date left 12, count right-aligned 12 from the right, #999999) over the V-2015 360-epx grid: 3 columns of 111-epx squares, 2-epx gutters, left 11 / right 12 epx (HIGH; V-2016+ at 360 uncaptured); video tiles carry a ≈36-epx dark disc with an outline play triangle (1.3.14); albums as V-2015's 60-epx tiles, 2 columns of 162 epx with 12-epx margins and gutter (1.4.1) | H1, H17 |
| Y2 | Photos viewer chrome and app bars | MEASURED (1.6, V-2015, HIGH); the viewer bar V-2016+ per T17-17 (r11/photos-pass2.md §1 1.6.5–1.6.6, §4.3–4.4, LOW ×3; r3 D4) | 50-epx #171717 date header (15-epx long date at x ≈25); photo fitted to width, centred on the whole screen; 48-epx BLACK app bar with Share · Edit · Delete · More at 218 / 150 / 82 / 24 epx from the right (MDL2 E72D, E70F, E74D, E712; no Favorite); "•••" expands the bar to 60 epx with labels; overflow Slideshow / Set as / — / File information (#2B2B2B, 44-epx pitch); library app bar 48 epx #1F1F1F; en-US strings (m/d dates) | H1 |
| Y3 | Camera viewfinder chrome | MEASURED (MEDIUM; r11/camera-pass2.md §1, §4 governing — r3 D3; was APPROXIMATION: V-2017 rotated into portrait, T17-17) | photo preview 4:3 fitted to width, centred on the full screen (V-2016+, camera-pass2 §1); shutter a 72-epx disc at W/2, centre 56 epx above the nav-bar top: white fill to r 31.9, a #2B2B2B ring at r 32–34, a white ring to r 36.1 (§4.1); the other capture modes the phone admits as 32-epx dark discs at W/2 ± 60, centres 36 above the nav-bar top, the right disc the next mode and the left the previous in the cycle photo → video → panorama; the quick-settings capsule vertical on the right edge, 2 → 45.5 epx from it, centred on the screen centre, glyph centres at H/2 − 66 / − 22 / + 22 / + 66, 24 from the right — photo: flash, HDR, timer, chevron; video: video light, slow motion, chevron (3 items at − 43.7 / + 0.5 / + 45.1); panorama: no capsule — the chevron expanding the manual controls (camera-pass2 §1 "Expanded capsule"); HDR through CameraX Extensions, hidden with `[camera] mode hdr: unavailable` where the phone lacks it; timer 2 s / 5 s / off ("2-second timer", "5-second timer", "Timer off"), and Time lapse (the countdown restarts after each capture until the shutter is pressed; camera-pass2 §1); settings a 14-epx gear in a 36-epx disc bottom-right, 24 from the right, 27.9 above the nav-bar top; camera switch top-right, 24 from the right, 28 from the top; camera roll a 36-epx square bottom-left, centre 24.7 from the left, 28.0 above the nav-bar top; grid ("Framing grid") and Living Images ("Capture living images") on the settings page (camera.md 1.8); NO horizontal mode strip (no version had one) | H2, H18 |
| Y4 | Pro dial (Q2 C) | MEASURED (1.4, V-2015, HIGH) | five concentric arcs centred on the nav-bar top at W/2, radii 130.5 + 65·k epx (130.5 / 195.4 / 260.3 / 325.3 / 390.2), inner → outer exposure · shutter · ISO · focus · white balance, ≈1-epx light-grey stroke; icons on their ring at the left (x ≈52–56), the exposure icon on the innermost ring's top; value labels (≈15-epx #656565) centred at W/2, 30.3 epx above each ring's top; the shutter rises to 74.75 epx above the nav bar top in the five-ring view (opened by sliding the shutter left, 1.4.9); one control alone = one 130.25-epx arc, its icon at 139° (1.4.10) | H2, H15 |
| Y5 | Player transport and scrubber; the My videos page | MEASURED (movies-tv.md 1.4, 1.6, 10586; the skip-button version v3.6.1867 and the Dec-2016 menu per r11/movies-tv-pass2.md §1 / §4 — r3 D5, V18) | a 120-epx flat scrim (≈60 % black) above the nav bar; 2-epx track at nav − 93, x 12 → 348; accent hollow ring thumb Ø 22, stroke 2.0, its centre 1.2 epx below the track's centre (thumb-travel rule of 1.6.8); played portion accent, 2 epx, from the track's left to the thumb, the unplayed track white at 25 % (1.6.7, MEASURED, HIGH); time labels below the track, baseline nav − 67.6, in the locale's format ("0:17:34" on en-US): elapsed at x 12.5, the REMAINING time ending at 345.5 (1.6.11, HIGH); transport centres CC 36 (only when the file has a text track; the row re-centres without it), back 10 W/2 − 48, play / pause W/2, forward 30 W/2 + 48, full screen W − 84, "•••" W − 36 (1.6.12, HIGH), row centre nav − 39.8, glyphs 18 epx tall; "•••" menu Cast to device / Zoom to fill / Repeat / Autoplay (1.7.8, Dec 2016); playback letterboxed (fit) by default, Zoom to fill switching to fill; the subtitle flyout of 1.6.16. My videos: 112 × 112 tiles on a 124-epx pitch from x 12, first row top chrome + 48, 2 columns at 360 epx, an accent 15-epx group header per folder at x 12, caption = the file name without extension (15 epx, max 2 lines, clipped at tile left + 100), no per-tile metadata | H3, H19 |
| Y6 | Motion: viewer open / close, photo swipe, ≡ pane, library → player, player controls show / hide, camera mode switch, capture feedback, dial open, slideshow step | MEASURED where an R11 pass-2 addendum bounds it (camera-pass2.md §5, photos-pass2.md §5, movies-tv-pass2.md §5; 30-fps sources, ± 33 ms — r3 D3–D5, V16–V18); the rest UNMEASURED; timed by the `[motion]` clock (C-5, C-31) | viewer open / close: the photo expands from its thumbnail and shrinks back to it (photos-pass2 §4.7), ease-out, settle 250 ms (no duration measured — still an approximation, H4); photo swipe finger-tracked with a 20-epx gap between photos, the whole fling ≤ 234 ms (photos-pass2 §5); ≡ pane open ≈78 % of its travel at 100 ms, settled at 167–200 ms, no scrim, close in 67–100 ms (movies-tv-pass2 §5); library → player a hard cut (≤ 33 ms); player controls fade in ≈200 ms (167–300), hold ≈3.2 s, then fade out over 367–400 ms; camera mode switch a ≈300-ms slide along the short axis (out ≈150 ms, a black gap, in ≈150 ms; camera-pass2 §5); capture feedback: the preview cuts to black for ≈167 ms while the chrome stays; dial open: a ≈333-ms sweep after the flick, the labels fading in over ≈100 ms; slideshow step 5 s with a 250-ms settle (agent picks). Still unmeasured (H4): the viewer open / close duration, the chevron's expand / collapse, the dial's value change, the camera launch, the slideshow step | H4 |
| Y7 | Which bars each page draws | CLOSED — MEASURED (photos.md 1.1.1, camera.md 1.1.1, movies-tv.md 1.1.1 / 1.6.1) | Photos and Camera: status bar hidden on every page, nav bar drawn; Movies & TV: library pages draw phase 01's status bar (C-17), the player hides it | H1–H3 |
| Y8 | Browse page: search box, sections, the title page | P4 design borrowing R11's store-half forms (movies-tv.md 1.5.2 / 1.5.3 / 1.7.5 / 1.7.10); the posters MEASURED (movies-tv-pass2.md 1.5.5, MEDIUM — r3 D5) | R3 C1's search box; section rows "title + accent Show all"; each section one horizontally scrolling strip of posters 112 × 160 epx (a 1.2-epx #1C1C1C border included) on a 120-epx pitch, 8-epx gutter, clipped at the right margin; the title page per 1.7.10 (art at the left, title, year • genre, description with More, then the "Watch on" rows) | H10 |
| Y9 | The "Watch on <service>" row set on a title page | P4 design | app-list rows (R3 C2) with the service's icon at the glyph position and "Watch on <service>" as the row text (the "Search on" wording belonged to Q-A's struck keyless form) | H11 |
| Y10 | Media server: sign-in page, "Add a server" entry, the library and title rows | P4 design | R6 §3.4.2's field and button geometry for the sign-in; the library and title rows in the My videos tile-grid form (Y5) | H12 |
| Y11 | The editor's tool UI | LOW (r11/photos-pass2.md §1 "The editor on a phone", §4.2, §4.9 — the production phone editor, T17-17's rule; r3 D4) + P4 for the four added tools | full screen; a 48-epx bottom tool strip, fill (15,15,15), Crop · Enhance · Rotate · Save · More at 286 / 218 / 150 / 82 / 24 epx from the right, Save being "Save a copy" (plain Save in More); the Edit sheet anchored to the screen bottom (§4.9); the four added tools (straighten, light and colour, filters, red-eye) reached from the strip, their controls sliders per R3 C1 / R6 §3.4.2 (P4) | H13a, H13b |
| Y12 | Panorama capture UI (guide, progress) | MEDIUM (camera.md 1.5, V-2016; camera-pass2.md §1 1.5.3 — r3 D3) | a full-width translucent guide band ≈ 0.35·W tall (126 epx at 360), centred on the screen centre, the captured strip in a white-outlined frame at the left, a white arrow → on a thin centre line; portrait, sweep left → right; progress as the frame's growing width (UNMEASURED-5) | H2 (the UI), H14 (stitch quality) |
| Y13 | Video trim screen | LOW (r11/photos-pass2.md §1 "Video trim", Nov 2015, landscape; R11's 1.11 still is the frame picker, §4.1 — r3 D4) | a command bar at the TOP (#1F1F1F, 48 epx; Save a copy · Cancel · More at 150 / 82 / 24 from the right); the track near the bottom over a scrim band, ≈3 epx, its selected part white; two filled white handle discs (≈18.4 epx) on the track, a hollow ring thumb under the active handle; the range's start and end times at the track's two ends; NO centred time readout; portrait in the same structure (V3) | H13b |
| Y14 | Capture-intent accept / retake UI | P4 (camera.md UNMEASURED-7) | the Rich Capture editor's app-bar pattern (photos.md 1.7.5): a transparent bar with Accept (MDL2 CheckMark E73E) at 82, Retake at 150 and More at 24 epx from the right | H20 |

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3) and are not asked.

1. ~~Q1 — what Photos is~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q1 — what Photos IS.** W10M's Photos had a collection, albums, a viewer, share / delete / set-as, a
   slideshow and an editor. Which form is built (one form only, Hard Rule 16)?
   A. Viewer + collection + albums + share / delete / set as / slideshow — no editing.
   B. A plus W10M Photos' editor: crop, rotate, straighten, auto-enhance, light and colour, filters, red-eye,
   saved as a copy. (lean — phase 10 Q7's "EVERYTHING there is only one version built" precedent)
   C. B plus video trimming (W10M Photos could trim a video).
   D. Other / let me clarify.
2. ~~Q2 — Camera modes~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q2 — Camera modes.** Windows Camera on the final release had photo and video with automatic everything,
   plus the Lumia pro dial (manual focus, ISO, shutter, white balance, exposure), and on some phones panorama,
   slow motion and Living Images.
   A. Photo and video, automatic, with flash / timer / grid / front-back / zoom.
   B. A plus the pro dial. (lean — the dial is the part people remember)
   C. B plus panorama, slow motion and Living Images.
   D. Other / let me clarify.
3. ~~Q3 as first written~~ SUPERSEDED 2026-09-23 by the A11 amendment (see Decisions); re-asked as Q3b below.
   **Q3 — what the video app IS.** Movies & TV's local half was a Videos library plus the player; its store
   half is out (A11).
   A. A Videos library (all videos, by folder) plus the player. (lean)
   B. The player only, reached from Photos, Files and "open with" — no library page, no app-list entry.
   C. A plus the player also handling audio-only files as a second Music entry point.
   D. Other / let me clarify.
3b. ~~What Movies & TV is~~ RULED 2026-09-23: A, and a TV-channels app split out (see Decisions). Original question kept below.
   (added 2026-09-23) **What Movies & TV is, now that it may go online.**
   A. A W10M-style hub: your local videos, plus an online catalogue of films and shows (info and artwork from a public film
      database) showing where each title can be watched among the streaming apps on the phone, with "Watch on <service>"
      opening that app at the title; plus your own media server (Jellyfin or Plex) if you have one (lean)
   B. Local library and player, plus a "Streaming" pivot of shortcuts into the streaming apps you have (no catalogue, no search)
   C. A without the media-server part
   D. Other / let me clarify
4. ~~Q4 — videos in Photos~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q4 — videos in Photos.** W10M's Photos showed videos in its collection and played them in place.
   A. Photos shows photos and videos together; tapping a video opens the shared player screen. (lean)
   B. Photos shows photos only; videos live only in the video app.
   C. Photos shows both but plays videos inline in the viewer (a second player surface).
   D. Other / let me clarify.
5. ~~Q5 — capture intents~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q5 — the capture-intent contract.** Other apps ask for a photo with `IMAGE_CAPTURE` / `VIDEO_CAPTURE`;
   answering them makes the shell's Camera one of the choices (with Samsung Camera) and lets a caller get its
   picture from it.
   A. Yes: the shell's Camera answers both and returns the result to the caller. (lean — P2, fewer seams)
   B. No: it is a launcher app only; other apps keep using Samsung Camera.
   C. Yes for IMAGE_CAPTURE only.
   D. Other / let me clarify.

## Build tasks
0. **Build-start checks, recorded in Decisions.** BS-1 the OpenCV licence (Apache-2.0, ≥ 4.5) and the trimmed module set
   for `Stitcher`, with the arm64 `.so` size; BS-2 whether the pinned CameraX version reaches a constrained high-speed
   session through interop, else raw Camera2 for slow motion only; BS-3 the credential store's Keystore alias and file
   name (Decisions "Trust": AES-256-GCM, no security-crypto; it holds the Jellyfin token and, since Q-17-1 (a), the TMDB
   read token); BS-4 each streaming service's deep-link, web and search URL
   forms, verified by starting each on a phone with the app installed, recorded with the verification, and the Wikidata
   streaming-service title-id properties with each one's coverage (T17-15); BS-5 the catalogue
   source's terms, attribution text and rate limit (TMDB's terms), the media server's API version (Jellyfin) and the
   fixture image pinned as `jellyfin/jellyfin:<version>@sha256:<digest>` (T17-21); ~~BS-6 whether an unclaimed Plex
   container answers its library API …~~ SUPERSEDED 2026-09-23 by T17-14 (Q-B: Jellyfin only); ~~BS-7 the `:camera` and
   `:video` diagnostics dump paths~~ SUPERSEDED 2026-10-04 by r3 D16: named now, phase 15's `Service.dump()` form (Decisions,
   processes).
1. **App identities.** Three launcher activities inside the APK: ~~`PhotosActivity` (LAUNCHER + APP_GALLERY +
   VIEW image/*), `CameraActivity` (LAUNCHER + STILL_IMAGE_CAMERA + VIDEO_CAMERA; IMAGE_CAPTURE + VIDEO_CAPTURE
   with the T17-4 guards; `android:process=":camera"`), `VideoActivity` (LAUNCHER + VIEW video/* for `content` and
   `http(s)` data — plain `http` played or refused per Q-D, task 17; `:video`); labels, task affinities and portrait lock as
   MusicActivity;~~ SUPERSEDED 2026-10-04 by r3 D6 (MusicActivity is not singleTask, manifest:129-144):
   `PhotosActivity` (LAUNCHER + APP_GALLERY), `CameraActivity` (LAUNCHER + STILL_IMAGE_CAMERA + VIDEO_CAMERA, the slot;
   `android:process=":camera"`) and `VideoActivity` (LAUNCHER, the hub; `:video`), each `singleTask` with `onNewIntent` and
   its own task affinity, phase 15 / 16's form (manifest:152-165, :501-533), labels and portrait lock as MusicActivity; and
   the helpers other apps reach, in standard launch mode and not catalog entries (L14-2): `.photos.ViewerActivity` (VIEW
   image/*; main process), `.video.PlayerActivity` (VIEW video/* for `content` and `http(s)` data — plain `http` plays, Q-D
   A, task 17 — and the target of Photos' and the hub's explicit launches; `:video`), `.photos.EditActivity` (the editor
   and trim, explicit only; `:photosedit`, r3 D8) and `.camera.CaptureActivity` (IMAGE_CAPTURE + VIDEO_CAPTURE with the
   T17-4 guards as r3 D1 replaces them — the output guard and the no-output contract, the agent line of 2026-10-05;
   `:camera`; kept by Q-17-2 (b)); a dump service per non-main process (Decisions, processes); after an in-place
   grant only `PhotosActivity` calls `startFeeds("photos grant")` (r3 D6); entries ADDed to
   qa/phase-03/exported-allowlist.txt with their reasons; each root sets `testTagsAsResourceId`. Permissions: CAMERA,
   READ_MEDIA_VIDEO, and the install-time `SET_WALLPAPER` (Photos' "set as lock-screen wallpaper", `WallpaperManager`
   FLAG_LOCK — this phase is its first user and ADDs it; phase 19 reuses it, T17-18). ~~FOREGROUND_SERVICE_CAMERA (video
   recording continues through a screen-off only until the file is finalised …)~~ SUPERSEDED 2026-09-23 by T17-19: no
   camera foreground service exists (the recorder stops on screen-off; CameraX runs in the activity's lifecycle), so the
   permission is not declared. Uninstall exclusion verified (E2).
2. **E16 re-run on this build** (was "The routing fix" — moved to phase 15 (C-1): it is phase 15's build task 0 since
   2026-09-23). Phase 15's tag-keyed routing is in the build this phase starts from (depends-on); this task re-runs phase 10
   E10 / E13 as E16 once the seed (task 3) is in, so the seed never puts Music's face on Photos or the Camera row tile. No
   code here. (Phase 20's references to "phase 17's build task 2" now mean phase 15's build task 0.)
3. **Slot seeding ~~under the guard~~ with take-over (SUPERSEDED 2026-10-05 by Q-17-3 (a)).**
   `assignSlotOnce("slot:photos:v1", Slot.PHOTOS, PhotosActivity, takeOver = true)` and
   `assignSlotOnce("slot:camera:v1", Slot.CAMERA, CameraActivity, takeOver = true)` where phase 10 runs the Music seed
   (`ShellApp.claimMusicSlot`, `ShellApp.kt:152-158`; cite fixed 2026-10-04, r3 D16), in the form of phase 16's Calendar and
   People seeds (`ShellApp.claimCalendarAndPeopleSlots`, `ShellApp.kt:169-173`); the pass list that today admits only
   Calendar's and People's markers ("Only those two markers pass it", `tiles/LayoutStore.kt:125-133`; the same sentence in
   `tiles/SlotSeed.kt:12`) gains `slot:photos:v1` and `slot:camera:v1`; precondition: phase 16's build task 1 (the guard
   and `takeOver`) is in (depends-on). The
   diagnostics line ~~`[layout] assignSlotOnce … -> assigned | already run | kept user's <component>`~~ `[layout]
   assignSlotOnce … -> assigned | assigned, replaced user's <earlier> | already run` (`tiles/LayoutStore.kt:148-154`;
   `kept user's` never comes from these two markers) is what E1 reads. A hand re-point after the update is kept (the marker has run).
4. **Photos library and collection.** Images and videos together (Q4 A) from MediaStore with a ContentObserver;
   grouped by month with day rows (collection) and by bucket (albums), drawn per Y1 (3 columns at 360 epx, the video-tile
   disc, the 60-epx album tiles, no status bar); paged so thousands of rows stay responsive; the empty,
   denied and partial states (`READ_MEDIA_VISUAL_USER_SELECTED`, phase 01's edge-case rule) each with their
   line and the grant link where the empty state is (phase 10 task 10's pattern); a video row opens ~~VideoActivity~~
   `.video.PlayerActivity` (r3 D6) by explicit component (one player surface).
5. **Photos viewer, actions and the editor.** Full-screen viewer (swipe, pinch zoom, double-tap; decoded at screen size
   through ImageDecoder's target size and the zoomed region re-decoded with BitmapRegionDecoder — r3 D8), share
   (ACTION_SEND with the content URI), delete (`createDeleteRequest`), set as Start background (the shell's own theme setting,
   ~~the same persisted grant StartThemePage takes~~ SUPERSEDED 2026-10-04 by r3 D9: a screen-size copy at
   `files/backgrounds/<id>.jpg`, `[photosapp] set as background <id> -> <file>`) and as lock-screen wallpaper
   (`WallpaperManager`, FLAG_LOCK), slideshow (`[photosapp] slideshow next <id>` per step, `[motion] slideshow_step …`), and
   the full editor (Q1 C — crop, rotate, straighten, auto-enhance, light and colour, filters, red-eye, with the transforms in
   Decisions, the matrices written there before this task starts — r3 V12) plus video trim through Media3 Transformer, in
   `.photos.EditActivity` (`:photosedit`; the editor on a ≤ 4096-px preview, the saved copy rendered tile by tile from the
   original re-read at full resolution — r3 D8, V12), every result a copy through ~~`MediaStore` (IS_PENDING then
   cleared)~~ `media/MediaWrites` (pending → written → published, read back, the copy's folder and format per r3 D7). The
   viewer's chrome, app bar and overflow per Y2; the editor as ~~Y11's panel~~ Y11's bottom strip (r3 D4), the trim screen
   per Y13 (the four added tools are P4 and the trim screen LOW, H13b). Failures are never silent: a refused delete consent logs `[photosapp] delete <id>: refused by
   user`, a refused or failed edit / trim write `[photosapp] edit <tool> failed: <why>` / `[photosapp] trim <id> failed:
   <why>` (T17-23).
6. **Camera, the automatic modes.** CameraX preview / capture / video; front / back, flash, HDR (CameraX Extensions,
   hidden with `[camera] mode hdr: unavailable` where the phone lacks it — r3 D3), timer 2 s / 5 s / off with Time lapse
   (`[camera] timer <n>s -> shutter`; Y3), grid, tap-to-focus (`[camera] focus at x,y: <state>`), pinch zoom; video sound
   only while RECORD_AUDIO is held, else a silent take with `[camera] video sound: off (no microphone permission)` and a
   one-line hint naming the Microphone row (r3 D12); files written into DCIM/Camera through ~~MediaStore with IS_PENDING
   and cleared on completion~~ `media/MediaWrites` (pending → written → published, read back — r3 D7) (never a bare file
   write); the capture-intent form in `.camera.CaptureActivity` (Q5 A, kept by Q-17-2 (b): returns
   RESULT_OK with the image at EXTRA_OUTPUT, or RESULT_CANCELED on Back; the T17-4 guards as r3 D1 replaces them — the
   three-condition output guard with `[camera] refused output: no grant`, and the no-output contract (IMAGE_CAPTURE: a
   thumbnail Bitmap in `data`, no file; VIDEO_CAPTURE: a DCIM/Camera row through `media/MediaWrites`, its URI returned with a
   read grant), the agent line of 2026-10-05 — whose adversarial review is a GATE
   recorded under qa/phase-17/ before `done`) with its accept / retake bar (Y14, P4, H20); "camera in use" and "no camera"
   states with their lines; the viewfinder chrome per Y3 (the ~~V-2017~~ shutter disc, mode discs and ~~top capsule~~
   vertical right-edge capsule, settings, switch and roll as measured — SUPERSEDED in part 2026-10-04 by r3 D3; grid and
   Living Images on the settings page — ~~the mode strip~~ SUPERSEDED 2026-09-23 by T17-16 / T17-17: no version had one),
   every mode entry gated by capability, each hidden
   mode logging `[camera] mode <x>: unavailable (<reason>)`; the settings page per r11/camera.md 1.7 / §2 minus Lenses,
   OneDrive and "Related settings" (no Android meaning); the `:camera` dump service (Decisions, processes; r3 D16).
   6a. **Pro dial** — `Camera2CameraControl` interop; the capability gates in Decisions (r3 D10); the measured five-arc geometry
   and the single-control arc (Y4); EXIF carries ISO, exposure time and white balance so P9 can read them back.
   6b. **Panorama** — OpenCV `Stitcher` over a burst of frames captured while the guide line is followed; output width > the
   sensor width, EXIF present; the arm64-only native library (BS-1); hidden on x86_64 with its reason.
   6c. **Slow motion** — the constrained high-speed session (BS-2 route) at the highest `getHighSpeedVideoFpsRanges` entry;
   ~~the file's `r_frame_rate` ≥ 120~~ SUPERSEDED 2026-10-04 by r3 D11: captured at that rate and encoded at 30 fps, so the
   file plays slowed everywhere, with no audio track, logging `[camera] slowmo <n> fps captured, encoded at 30 -> <uri>`;
   hidden where the capability is absent.
   6d. **Living Images** — a 1-s clip buffered before the shutter and written with the still as one Motion Photo file
   (Decisions); switched on the settings page ("Capture living images", r3 D3); Photos' glyph and hold-to-play.
7. **Movies & TV, the local half.** The My videos page (MediaStore videos as Y5's tile grid, a group per folder, the file
   name as the caption; ~~with duration~~ SUPERSEDED 2026-09-23 by T17-16: W10M's tiles carried none); the player
   (`.video.PlayerActivity`, r3 D6): ExoPlayer on a SurfaceView, its own Media3 MediaSession built with `setId("video")`
   and the audio attributes in Decisions (r3 V5, D16), audio focus, Y5's scrubber and transport (~~±10 s~~
   SUPERSEDED 2026-09-23 by T17-17: back 10 / forward 30 s; full screen and the conditional CC per Y5, r3 D5) and its "•••"
   menu — Cast to device (Android's own cast route, `android.settings.CAST_SETTINGS` resolved at tap, no cast library — P5,
   r11/movies-tv.md UNMEASURED-8), Zoom to fill (the fill ↔ fit toggle; letterboxed (fit) by default, r3 D5), Repeat,
   Autoplay (r3 D5: the Dec-2016 menu; when on, the next video of the same My videos group starts at the end — the
   agent's reading, judged by H19) —,
   aspect and rotation from the file's metadata, subtitle tracks when present, an error state for files the
   device cannot decode, `http(s)` sources through `DefaultHttpDataSource` with the "cannot reach" and 404 states, plain
   `http` played or refused per Q-D (task 17)
   (~~an explicit refusal for non-file schemes (A11) with its line~~ SUPERSEDED 2026-09-23 by T17-1; `[video] unsupported
   scheme=<s>` for `rtsp` / `ftp` / `smb` only).
8. **Settings + checklist rows + wizard steps.** Camera row (CAMERA, id `camera`), Videos row (READ_MEDIA_VIDEO, id
   `videos`), ~~the Photos row's video half~~ (SUPERSEDED 2026-10-04 by r3 V20: the Photos row is unchanged); each app's
   empty state links to its grant; "don't ask again" opens the app's
   settings page (phase 10 task 10's pattern); each new row is also a wizard step (phase 12 Q2's rule) with its why line —
   `camera`: "Camera takes your photos and videos. Without it the Camera tile can't open the shell's camera."; `videos`:
   "Movies & TV and Photos show the videos on this phone. Without it they show none." — tags `wizard_step:setup:camera`,
   `wizard_step:setup:videos` (phase 12 T12-1's namespacing); E25.
9. **Re-runs and regressions.** Phase 01 E4 (Photos and Camera), phase 03 E2 / E10, phase 10 E10 / E13 (E16, under
   phase 15's fix), phase 12 E1 on this build (C-4 c), the app-list regression (phase 02 regress.sh pattern), the
   exported allow-list (phase 03 E5's method), the baseline regression (E24) and the APK size (E17).
10. **R11 values applied** (done in this doc 2026-09-23, T17-16 / T17-17): every Y row R11 measures carries the measured
    value, the rest are tagged approximations with H rows, and E19 is written, so R11 no longer holds the doc from FINAL
    (RV9); the build draws those values and E19 proves them. ~~Once … land, every Y row … is replaced~~ SUPERSEDED 2026-09-23.
11. **`StreamingHandoff` and the services table.** The interface in Decisions (T17-1); the per-service table filled from
    BS-4; `installedServices()` over `queryIntentActivities`; `openTitle` building the deep link from the service's own
    title id resolved through Wikidata by the TMDB id (T17-15; `[video] watch-on … id <found|none> (wikidata)`), else the
    search URL, both with `setPackage(<service package>)` and ActivityNotFoundException → the search link → `not installed`
    (r3 D13); the Wikidata base redirected for QA by the debug-only pref `qa_wikidata_base` (task 12's rule); the
    QA-Flix fixture APK (`testapps/qa-flix`, phase 02's client-library test-APK pattern) declaring the table's intent-filter
    forms for a fake service (`https://qa-flix.test/title/<id>`, `https://qa-flix.test/search?q=<title>`) and showing the
    received URI in a TextView tagged `qa_flix_uri`; JVM tests for the table logic and the Wikidata response parsing.
12. **The catalogue (TMDB — Q-A; the key route ~~Q-A2: B~~ Q-17-1 (a)).** ~~`app/build.gradle.kts` fills
    `BuildConfig.TMDB_READ_TOKEN` from the Gradle property `tmdb.readToken` first and the gitignored local.properties second,
    empty when neither has it (it reads nothing else; `tmdb.apiKey` is unused), so a build made on Jeremy's PC carries the
    token and a CI build — no local.properties, no Actions secret, `.github/workflows/apk.yml` unchanged — carries none;~~
    SUPERSEDED 2026-10-05 by Q-17-1 (a) (r3 D2, D14): no build carries the token — no TMDB `BuildConfig` field, no Gradle
    property, no local.properties read, `.github/workflows/apk.yml` unchanged; `app/build.gradle.kts` adds `buildFeatures {
    buildConfig = true }` for the `BuildConfig.DEBUG` gates (this task's `qa_catalogue_base`, task 11's `qa_wikidata_base`,
    task 13's `qa_server_base`; its `buildFeatures` holds `compose` and `aidl` only today, `app/build.gradle.kts:77`, and
    AGP 8 generates `BuildConfig` only with the flag). **The TMDB key setting** on Movies & TV's settings page: enter (the
    owner pastes his read token once), replace, remove; the token kept in the credential store (Decisions "Trust", BS-3:
    the Android Keystore AES-256-GCM key), read on use, never in a diagnostics line, a log, a URL or an evidence file
    (C-32). **No key saved** → Browse says film search needs a key, with a link to the setting, `[video] catalogue: no TMDB
    key saved`, and no request. **A key TMDB refuses** (HTTP 401) → Browse says the saved key was refused, with the same
    link to replace or remove it, `[video] catalogue "<q>": error 401`, the cache still shown. Calls send
    `Authorization: Bearer <token>` and no `api_key`; `catalogue.search` / `lookup`; the 7-day cache; image URLs from
    `/3/configuration`'s `images.secure_base_url`; the attribution in About and at the Browse page's foot
    (`hub_attribution`); the one no-key form ~~("Film search is off in this build", `[video] catalogue: no TMDB key in this
    build`, no request)~~ (above; SUPERSEDED 2026-10-05 by Q-17-1 (a)); catalogue lines carry the query and status only (C-32); the base-URL redirect for QA — a pref
    `qa_catalogue_base` written with `qa/phase-01/scripts/prefs_edit.py`, honoured ONLY when `BuildConfig.DEBUG` (the
    release APK cannot be redirected; phase 20's rule), which also carries the image base because the fixture's
    `/3/configuration` answers it (T17-20).
13. **The media server (Jellyfin — Q-B).** A Jellyfin client; the "Add a server" page (host, user, password),
    the token store (BS-3), the library listed per Y10, direct play through the shared
    player, the connected / unreachable / unauthorised states; plain `http://` servers per Q-D (task 17) with, under Q-D A,
    the insecure-server prompt before a sign-in to a non-private address; stream URLs logged with the query string removed
    (C-32); removing the server deletes the token (`[video] server token cleared`) and the dynamic shortcut; the same
    `qa_server_base` QA pref route as task 12 (debug builds only). GATE: the adversarial review of the sign-in / credential
    path (Decisions "Trust") is recorded under qa/phase-17/ before the phase is `done`.
14. **The hub.** The ≡ pane (T17-16: My videos / Browse / Media server rows, the Media server row and its dynamic shortcut
    appearing on set-up, the bottom group → settings; ~~the three pivots~~ SUPERSEDED 2026-09-23 by T17-16); the Browse
    page's search, sections and title page (Y8); the "Watch on" rows from `installedServices()`; the offline line; every
    `[video]` line in E18.
15. **App Shortcuts (phase 11 Q1's standing rule; C-8 / C-9).** Static ~~`res/xml/shortcuts.xml`~~ entries in one file per
    app, each declared as meta-data on its own activity — `shortcuts_photos.xml` on PhotosActivity, `shortcuts_camera.xml`
    on CameraActivity, `shortcuts_video.xml` on VideoActivity, in `shortcuts_people.xml`'s VIEW + `page` / `mode` extra form
    (SUPERSEDED in part 2026-10-04 by r3 D6: phase 11's per-activity query reads each activity's own file): Photos —
    `photos_collection`, `photos_albums` (ranks 0–1, targeting PhotosActivity with the page extra); Camera —
    `camera_photo`, `camera_video` (ranks 0–1, targeting CameraActivity with the mode extra) static and `camera_panorama`,
    `camera_slowmo` DYNAMIC (ranks 2–3, published by CameraActivity on start only where the mode's capability gate admits it,
    removed otherwise — a manifest shortcut cannot be disabled, Decisions); Movies & TV — `video_myvideos`, `video_browse`
    (ranks 0–1) static and `video_mediaserver` dynamic (rank 2, published on set-up, removed on sign-out). Every dynamic
    shortcut calls `ShortcutInfo.Builder.setActivity(...)` — `CameraActivity` for the two Camera modes, `VideoActivity` for
    Media server — so it bursts on its own app's tile and never on Music's (C-21). E23.
16. **Harness.** `qa/phase-17/baseline_layout.json` derived from `qa/phase-16/baseline_layout.json` (+ `slot:photos:v1`,
    `slot:camera:v1` in `addedOnce`, `slots` PHOTOS → PhotosActivity and CAMERA → CameraActivity, a pinned Movies & TV tile
    for E23, `manualSizes` for every tile), the previous file kept as `qa/phase-17/baseline_layout-pre-17.json` (C-3);
    `qa/phase-03/scripts/provision.sh` gains `adb shell pm grant app.tileshell android.permission.CAMERA` and `… android.permission.READ_MEDIA_VIDEO` (install
    `-g` at `provision.sh:35` grants them on a fresh install; the explicit lines re-grant after a `pm clear`, which resets
    runtime grants — C-4 a); the pre-17 APK at `qa/phase-17/upgrade/<tag>.apk` with its git tag in the log (T16-7's form;
    installed through `TILESHELL_APK`, phase 16's build task 8 — C-19);
    the fixture servers and APKs under Fixtures; drivers in `qa/phase-17/scripts/`. Also: **`lib.sh` `egress_guard_on` /
    `egress_guard_off`** (C-29; this task owns them): `adb root`; ~~`UID=$(adb shell pm list packages -U app.tileshell | sed
    's/.*uid://')`~~ SUPERSEDED 2026-10-04 by r3 V1 (bash's `UID` is readonly, so the old line kept the host's uid 1000 —
    Android's `system` — and `pm list packages` matches substrings and carries `\r`):
    `APP_UID="$(adb shell cmd package list packages -U app.tileshell | tr -d '\r' | sed -n
    's/^package:app.tileshell uid://p' | head -1)"` (p16.sh:116's form), asserted numeric and ≥ 10000; `egress_guard_on`
    first runs `pm revoke app.tileshell` on ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION, so the launcher's own weather
    refresh on every main-process start (`WeatherFeed.kt:98`) ends before any request (`WeatherFeed.kt:162-167`; r3 V2);
    `adb shell iptables -I OUTPUT -m owner --uid-owner "$APP_UID" ! -d 10.0.2.2 -j REJECT`; at row end `iptables -L
    OUTPUT -v -n` shows 0 packets on that rule (gated), then the rule is deleted, `egress_guard_off` grants both location
    permissions back, and `adb unroot`; **`qa/phase-17/scripts/leak_scan.sh`**
    (T17-13, C-32: the tokens / passwords passed to it — on the AVD the typed TMDB token `qa-dummy-token`, the Jellyfin
    token, `qa-password` — plus every `tmdb.*` value local.properties still holds (the build reads none since Q-17-1 (a)),
    read without echoing any; a match fails and prints only the file name; it never scans the drivers' own source —
    `qa/phase-17/scripts/` and `qa/phase-17/fixtures/` are excluded, since they hold `qa-dummy-token` and `qa-password` as
    literals by design — so a match is always in evidence, app data or a build output); ~~the QA APK assembled with
    `-Ptmdb.readToken=qa-dummy-token` and the CI-form APK with `-Ptmdb.readToken=` (C-32)~~ SUPERSEDED 2026-10-05 by Q-17-1
    (a) — one debug APK with no TMDB token in it; a row that needs the catalogue types `qa-dummy-token` into Movies & TV's
    TMDB key setting (task 12) and removes it at its end (RV12). Added 2026-10-04 by round 3: **(a) `qa/phase-17/scripts/p17.sh`** (r3
    V7, V9), sourced by every driver after `lib.sh`: `absent_in`, `gdump`, `top_activity`, a `c6` that runs `ring_save`
    for every ring in `RINGS` (launcher, camera, video, photosedit) before the force-stop, and `media_up <names>` /
    `media_down` — `media_up` takes a census of the image and video ids, pushes only the named files, `touch -m -t`s each
    so that qa-photo-i is i minutes older than qa-photo-0 (and every other pushed file has its own minute), and scans;
    `media_down` removes everything the row pushed plus every row with `owner_package_name=app.tileshell` and `date_added`
    ≥ MARK / 1000, scans, and asserts the counts equal the census. **(b) Fixtures** (r3 V9, V14): `make_photos.py` gains
    the editor fixtures (`qa-redeye.png`, `qa-line.png`; it makes only qa-photo-0..5 today, `make_photos.py:28-30`);
    `qa/phase-17/scripts/make_videos.sh` names ten RGB values, colour 0 … colour 9, and writes colour k on [k, k + 1) s,
    0-based, with `-g 25`; the edge fixtures (a 4K video, a video with two audio tracks, an `.srt` beside `qa-steps.mp4`, a
    DNG and a HEIC still — ffmpeg 4.4 cannot write HEIC, so its source is recorded beside it); `catalogue_server.py` gains
    `/429` and `/401`. **(c) E18's** `qa/phase-17/E18/producers.tsv` / `notrun.tsv` (r3 V13). **(d) The edge cases'**
    `qa/phase-17/scripts/edge.sh` and `edge_index.tsv` (r3 V14).
17. **Network security (C-16; the base policy Q-D: A).** `app/src/main/res/xml/network_security_config.xml` and the
    manifest's `android:networkSecurityConfig`: the fixed hosts' `cleartextTrafficPermitted="false"` domain-config, the base
    config per Q-D (A permitted; B permitted plus the media data-source factory's private-address guard; C not permitted),
    the `FixedEndpoints` set and its JVM test, the process-start `[net] cleartext permitted for <host>: <bool>` lines, the
    debug-only `app/src/debug/res/xml/network_security_config.xml` with the `10.0.2.2` exception (and B's guard honouring it
    only when `BuildConfig.DEBUG`), the `[video] cleartext refused <host>` line, the insecure-server prompt (A). GATE: the
    C-16 adversarial review for the branch built, recorded under qa/phase-17/ before `done` (Decisions C-16 (7)). Phase 20
    reuses this file.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 and take their clock
from the shell's `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`; C-5), a
screenrecord corroborating under phase 05's frame-spacing rule and never the primary clock; the `[motion]` line also carries
`frames=<n> maxGapMs=<ms>`, and every motion row asserts `maxGapMs` ≤ 33.4 ms (2 vsync) beside its numbers (C-31). Every
ring assertion reads `ring_since` from a MARK taken immediately before the step's action (after any clock jump, so the MARK
is on the new clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`; `row_end` saves each
ring the row names to `<row>/ring-<name>.txt` (C-20; `lib.sh` `ring_mark` / `ring_since` / `reply_since`, built by phase
11's build task 7) — this phase's ~~three~~ four rings are the launcher's, `:camera`'s, `:video`'s and `:photosedit`'s (r3
D8; their dump services, Decisions "processes", r3 D16). After any `adb reboot`
(boot-completed poll), `dumpsys battery unplug` or `KEYCODE_SLEEP` step, the driver calls `wake_device` and asserts it
printed `Awake` before the next tap (C-25). ~~Every QA APK is assembled with `-Ptmdb.readToken=qa-dummy-token` and never
carries Jeremy's key (C-32);~~ SUPERSEDED 2026-10-05 by Q-17-1 (a): no APK carries a TMDB token; a row that needs the
catalogue types the dummy token `qa-dummy-token` into Movies & TV's TMDB key setting (task 12) and removes it at its end
(RV12); rows that touch a credential end with `qa/phase-17/scripts/leak_scan.sh`. E13 and E20–E22 run
inside `lib.sh` `egress_guard_on` / `egress_guard_off` (C-29): zero packets from the app's uid to any address but 10.0.2.2,
gated; offline sub-rows keep airplane mode; after each restart inside the guard the launcher slice holds `[weather] refresh
ended without new data: no coordinates` and `absent_in` `[weather] fetch provider=` (the location grants revoked by the
guard, r3 V2). Dumps of the viewfinder and the player — screens that never idle — go through phase 05's gesture-driver
`UiDevice.dumpWindowHierarchy` with `Configurator.setWaitForIdleTimeout(0)` (qa/phase-05/README.md), not `uiautomator dump`;
every other dump follows RV13 (C-10). Every row that launches an app does `adb shell am force-stop app.tileshell` + Home
(`c6` + `ensure_start` since r3 V7, the floor below) before its next assertion on Start's grid, because the promoted tile lives in memory only
(`qa/phase-01/scripts/recent0922.sh:19-21`; C-6). Every row starts from `qa/phase-17/baseline_layout.json` through `layout_restore`
(`qa/phase-02/scripts/layout.sh:18`), which verifies after the shell reloads (C-3). A row that wipes the app reads "`pm clear`
→ `provision.sh` → Home" (C-4 d) — without `provision.sh` the wizard (phase 12) is on top; and phase 12's rule holds here in
one sentence: a finished or skipped wizard is never re-summoned on that install — a grant this phase adds goes red on the
Setup checklist instead (phase 12 H4; C-4 e). ~~Rows or sub-rows that only RECORD a fact end their PASS/FAIL line with
"RECORDED" so `lib.sh` `row_end` never counts them as a pass (C-13).~~ SUPERSEDED 2026-09-23 by C-26: a recorded clause uses
`lib.sh` `record <name> <value>`, never an assert; a row with only recorded facts ends `<row>: recorded only (<n> facts)`
with exit 0 (helper built by phase 13's build task 7); "RECORDED" below means that. Harness: qa/phase-17/scripts/lib.sh → symlink to
qa/phase-03/scripts/lib.sh; every row stamps its driver blob and the installed APK. Device: the AOSP AVD tileshell_fhd
(1080×2340 @ 450 dpi, API 36, no Google, `sdk_phone64_x86_64` userdebug). "Diagnostics" is read with phase 01's command; the
`:camera`, `:video` and `:photosedit` rings with their dump services (Decisions, processes; r3 D16).
**Round 3 floor (2026-10-04; r3 V7, V9, D12 / V4):** drivers source `lib.sh`, then `qa/phase-17/scripts/p17.sh` (task 16:
`absent_in`, `gdump`, `top_activity`, and a `c6` that runs `ring_save` for every ring in `RINGS` before the force-stop, so
the `:camera`, `:video` and `:photosedit` rings are saved before they die). Every "Home" is `c6` + `ensure_start`
(`lib.sh:295-314`; T11-16 — a right swipe on Start opens the pod bay). Every absence is `absent_in`. Every no-node check
first asserts its page's tag (`start_page`, `hub_title`, the Photos page's `photos_pivot:collection`), so an absence on the
wrong page cannot pass. Media rows bring their files with `media_up <names>` and end with `media_down`, and a count is
the census plus the row's own fixtures (RV12). No row or edge sub-step uses the microphone or the host's audio (no
`audio.sh`, no `pactl`): one that records video revokes `RECORD_AUDIO` for its span (`pm revoke app.tileshell
android.permission.RECORD_AUDIO`) and grants it back at its end.

**Fixtures.** Images: docs/plan/qa/phase-01/scripts/make_photos.py (six flat-colour PNGs, known RGB) pushed
into `/sdcard/DCIM/Camera` and `/sdcard/Pictures/QA-Album`, then `adb shell content call --uri
content://media/external/file --method scan_volume --arg external_primary` (phase 01 E6's command); counts
read with `adb shell content query --uri content://media/external/images/media --projection
_id:_display_name:width:height:relative_path`; for the editor rows also `qa-redeye.png` (a grey field with one pure-red
disc, 40 px across, at a known centre) and `qa-line.png` (a black line at 10° on white), made by the same script (added to
it by task 16, r3 V9). Pushed with `media_up`, whose mtimes make qa-photo-i i minutes older than qa-photo-0, so "newest
first" and "next" are defined (r3 V9). Videos:
made on the host with ffmpeg 4.4 (`/usr/bin/ffmpeg`, 2026-09-22) from `lavfi color=` sources concatenated so the frame is
one solid colour per second for 10 s — by `qa/phase-17/scripts/make_videos.sh`, which names the ten RGB values (colour 0 …
colour 9) and writes colour k on [k, k + 1) s, 0-based, with `-g 25` (r3 V9) — (`qa-steps.mp4`, h264; also `qa-steps.webm` vp9, `qa-steps-hevc.mp4`, `qa-rot90.mp4`
with a 90° rotation tag, `qa-audio-only.mp4`, `qa-truncated.mp4` = `head -c 100000`, `qa-empty.mp4` = 0 bytes; for the edge
cases also a 4K video, a video with two audio tracks, `qa-steps.srt` beside `qa-steps.mp4`, and a DNG and a HEIC still
whose source is recorded beside them, ffmpeg 4.4 being unable to write HEIC — r3 V14) pushed into
`/sdcard/Movies`, scanned the same way, counted with `--uri content://media/external/video/media --projection
_id:_display_name:duration:relative_path` (the AVD had 0 videos and 6 images on 2026-09-22). Decoders on the
AVD: h264 / hevc / vp8 / vp9 (`c2.goldfish.*` in /vendor/etc/media_codecs.xml, 2026-09-22). **Network fixtures** (the AVD
reaches the host at 10.0.2.2; no row hits the live internet — the real services and the real catalogue are P rows):
`qa/phase-17/scripts/catalogue_server.py` (`python3 -m http.server`-class, port 8090) serving recorded JSON in TMDB's
shape for the query "Blade Runner" (three results hand-listed in E20), `/3/configuration` with `images.secure_base_url =
"http://10.0.2.2:8090/img/"` and the poster PNGs under `/img/` (T17-20), a Wikidata-shaped answer for QA-Flix's fixture
property (T17-15; the base set through the `qa_wikidata_base` pref) and `qa-steps.mp4` for E13, with `/500`, `/404`,
`/429` and `/401` endpoints (the last two for the edge cases, r3 V14); it checks every request's `Authorization` header and logs only `bearer ok` / `bearer missing` and whether
an `api_key` parameter was present — never a header or parameter value (T17-13); the hub's base URL set through the
`qa_catalogue_base` pref via `qa/phase-01/scripts/prefs_edit.py`
(debug builds only); `testapps/qa-flix` (the "Watch on" fixture APK, task 11) and `testapps/qa-capture` (the capture-intent
caller APK: starts IMAGE_CAPTURE / VIDEO_CAPTURE with `EXTRA_OUTPUT` aimed at its own cache through its FileProvider, once
with a `file://` output, and logs the result code, file size and md5 to the `TileShellQa` logcat tag; it also receives
ACTION_SEND `image/*` and logs the action, the URI and the md5 of the stream it reads — r3 V10; since r3 V3 / D1
(Q-17-2 (b)): every capture request names the shell (`setPackage("app.tileshell")`); its `file://` leg first relaxes its
own StrictMode VmPolicy, so the platform's FileUriExposedException does not stop it on the sender; after each result it
logs `exists=<bool> size=<n>` for its own output path; its legs with no EXTRA_OUTPUT log the `data` Bitmap's width ×
height (IMAGE_CAPTURE) or the returned URI with `exists=` / `size=` read through it (VIDEO_CAPTURE); and one leg sets its
own ClipData with no grant flag and aims EXTRA_OUTPUT at a URI the driver passes — E9's display_photo negative); a media-server
container on the host (Jellyfin, Q-B: `jellyfin/jellyfin:<version>@sha256:<digest>` pinned at BS-5, the same digest in every
run's log (T17-21), at `10.0.2.2:8096` with `qa/phase-17/fixtures/jellyfin/`
as its config and `qa-steps.mp4` in its library, its first-run wizard pre-seeded in that config dir, a fixture admin whose
token the driver uses for ~~`GET /Sessions`~~ `GET /Devices` (r3 V8: SessionInfoDto carries no AccessToken; DeviceInfoDto
does)). ~~Plex `plexinc/pms-docker` at `10.0.2.2:32400` … BS-6 …~~ SUPERSEDED
2026-09-23 by T17-14 (Q-B: Jellyfin only). QA APKs: ~~the QA debug APK (`-Ptmdb.readToken=qa-dummy-token`) and, for E20's
no-key half and E17's leak check, the CI-form APK (`-Ptmdb.readToken=`), each~~ (SUPERSEDED 2026-10-05 by Q-17-1 (a): no
build carries a TMDB token, so there is no QA / CI-form pair; the dummy token is typed into the setting and E20's no-key
half is the setting left empty) the debug APK, and the release APK for E17's checks, each with its APK id in the row log
(C-32).
Layout: `qa/phase-17/baseline_layout.json`.
Upgrade: `qa/phase-17/upgrade/<tag>.apk`, the last pre-17 build.

**Emulator:**
- E1 **Slot seeding (phase 01 E4 re-cut) and the upgrade.** `adb shell pm clear app.tileshell` → `provision.sh` → Home
  (C-4 d; the wizard is provisioned away), open Start: diagnostics show
  `[layout] assignSlotOnce slot:photos:v1 PHOTOS -> app.tileshell/.photos.PhotosActivity -> assigned` and the
  `slot:camera:v1 CAMERA` line; the dump's `tile:slot:PHOTOS` tap resumes PhotosActivity and `dock:slot:CAMERA`
  resumes CameraActivity (`adb shell dumpsys activity activities`, topResumedActivity; after each launch `am force-stop
  app.tileshell` + Home before the next grid read, C-6); with three
  `STILL_IMAGE_CAMERA` fixture handlers and two `APP_GALLERY` ones still installed (`cmd package
  query-activities`, phase 01 E4's before/after lists), so the seed and not a one-handler auto-assignment did it.
  **Upgrade half (re-cut 2026-09-23, T17-5 / C-2; the install route re-cut by C-19):** ~~install
  `qa/phase-17/upgrade/<tag>.apk` (the pre-17 build, git tag in the log), `provision.sh`, …~~ SUPERSEDED 2026-09-23 by C-19
  (`provision.sh` always installed the current build, `qa/phase-03/scripts/lib.sh:24`, `provision.sh:35`, so the leg never
  upgraded): `adb uninstall app.tileshell`; `TILESHELL_APK=qa/phase-17/upgrade/<tag>.apk qa/phase-03/scripts/provision.sh`
  (the old build, every grant, the wizard marker — the two grant lines this phase adds fail harmlessly on the old build,
  whose manifest lacks them; `provision.sh:18` has no `set -e`); re-point PHOTOS to Aves (`deckers.thibault.aves.libre`, the
  phase 01 E4b fixture) through Settings > Tile apps (phase 01 E4b's flow) and leave CAMERA untouched; `adb install -r
  app/build/outputs/apk/debug/app-debug.apk` (NOT `provision.sh`, so the upgrade path runs) — precondition asserted: the
  install succeeds (same debug key; `INSTALL_FAILED_UPDATE_INCOMPATIBLE` fails the row loudly), and the row log records both
  APK ids (`apk match: NO` on the first leg is expected and noted) —, Home: diagnostics
  read ~~`[layout] assignSlotOnce slot:photos:v1 PHOTOS -> kept user's deckers.thibault.aves.libre/…` (phase 16 task 1's line
  form, as its E1 asserts for PEOPLE)~~ (SUPERSEDED 2026-10-05 by Q-17-3 (a): the seed takes the slot once over a hand
  pick) `[layout] assignSlotOnce slot:photos:v1 PHOTOS -> app.tileshell/.photos.PhotosActivity -> assigned, replaced
  user's deckers.thibault.aves.libre/…` (the take-over form, `tiles/LayoutStore.kt:148-154`, as phase 16's E1 asserts for
  PEOPLE) and `slot:camera:v1 CAMERA -> app.tileshell/.camera.CameraActivity -> assigned` with no `replaced`; the PHOTOS
  tile's tap ~~still resumes Aves~~ resumes PhotosActivity, `dock:slot:CAMERA` resumes CameraActivity. **Pointing it back
  sticks:** PHOTOS re-pointed to Aves through Settings > Tile apps again, a MARK, then a second `adb install -r` of the
  same APK → the slice from the MARK reads `-> already run` for both and no `-> assigned` line (`absent_in`), and the
  PHOTOS tile's tap resumes Aves (the marker has run). Phase 01 E4's 2+-handler negative is re-run on Mail and Store only. ~~re-point PHOTOS to Aves
  through Settings > Tile apps, `adb install -r` the same APK, reopen Start: the assignment is still Aves and the line reads
  `-> already run`~~ SUPERSEDED 2026-09-23 (that tested the marker path, not a pre-existing choice).
- E2 **App list.** The three entries appear under P, C and M (dump `applist_row:` tags), each hold menu offers
  Pin to Start and NOT Uninstall (qa/phase-01/UNINSTALL's method, both halves), no "New" caption (phase 01 E12's
  caption geometry rule), and phase 02's regress.sh pattern passes: A-Z groups and jump grid unchanged apart
  from the three rows, a grid-tile tap still launches, the pivot still swipes.
- E3 **Photos library and the video hand-off.** `media_up` six images in two folders (fixtures; r3 V9): the collection lists
  the census's images plus the six `photos_item:<id>` rows, the six newest first in their `media_up` order under one month
  header; the albums pivot lists `Camera` and `QA-Album` with counts census + 3 each; `adb shell rm
  /sdcard/Pictures/QA-Album/qa-photo-5.png` + the scan → the row disappears with no restart (the observer; diagnostics
  `[photosapp] library: images=<census + 5> …`). `adb shell pm revoke app.tileshell
  android.permission.READ_MEDIA_IMAGES` → the page says it cannot read the pictures, names the Setup checklist,
  offers the grant (dump text), line `[photosapp] access=DENIED`; `pm grant … READ_MEDIA_VISUAL_USER_SELECTED`
  with IMAGES still revoked → ~~the subset shows and the line says `access=PARTIAL`~~ (SUPERSEDED 2026-10-04 by r3 V11: the
  grant selects nothing) `[photosapp] library: images=0 … access=PARTIAL` and the partial state's line with its link; the
  link opens Android's selected-photos picker, qa-photo-1 and qa-photo-2 are tapped, then Allow → exactly those two
  `photos_item:` nodes and `images=2`; restore `pm grant … READ_MEDIA_IMAGES`. The collection's grid is 3 columns (three `photos_item:` nodes share a row's top ± 1 px, Y1). The two
  `qa-steps` videos count in the collection with W10M's video overlay (the ≈36-epx disc with a play triangle, Y1 —
  ~~a duration glyph~~ SUPERSEDED 2026-09-23 by T17-16) (Q4 A); **tap the
  `qa-steps.mp4` row → `dumpsys activity activities` topResumedActivity = ~~`app.tileshell/.video.VideoActivity`~~
  `app.tileshell/.video.PlayerActivity` (r3 D6), `[video] playing <id>` in the `:video` ring, and Photos' own dump (taken
  before the tap and after Back, `photos_pivot:collection` asserted in it first — r3 V7) holds no player node
  (`video_surface` absent) — one player surface (T17-7).** Then Home (`ensure_start`) and Back on Start → the launcher
  slice holds `[back] … -> app.tileshell/.photos.PhotosActivity` (r3 D6: the player is a helper, not a catalog app).
  `media_down` (RV12).
- E4 **Viewer.** `media_up` the six images (r3 V9). Tap `qa-photo-0` → full-screen viewer, expanding from its thumbnail
  (Y6, r3 D4); `[motion] viewer_open t0=<uptime> settle=<ms>` with settle = 250 ± 17 ms (Y6, RV11; an approximation, H4); `screencap` centre pixel = (220,40,40) ± 4 per
  channel; `adb shell input swipe 900 1170 180 1170 200` → next image, centre pixel = (40,180,80), `[motion] photo_swipe …`;
  Back returns to the collection at the same scroll position (dump), `[motion] viewer_close …`. Pinch zoom cannot be
  injected through `adb shell input` (single pointer), so ~~zoom is P5 / H9~~ pinch zoom is P5 / H9; double-tap is driven
  here with phase 05's gesture driver `script` op (r3 V22): a double-tap at the screen centre → the viewer's image node
  (`gdump`) is wider than the screen. `media_down` (RV12).
- E5 **Photos actions.** `media_up` the six images (r3 V9). Share → ~~`dumpsys activity activities` shows the system
  chooser (`com.android.intentresolver`) resumed with `ACTION_SEND` and a `content://media/…` stream~~ (SUPERSEDED
  2026-10-04 by r3 V10: the chooser prints its inner intent only as "(has extras)") `top_activity` is the system chooser
  (`com.android.intentresolver`); `testapps/qa-capture` picked in it → its `TileShellQa` line reads action
  `android.intent.action.SEND`, a `content://media/…` URI and the stream's md5 equal to the host md5 of the shared
  fixture; delete → the
  MediaProvider consent dialog is on top (`dumpsys window` / dump shows
  `com.android.providers.media.module`); **Deny** first (the dialog's Deny button tapped by its dump bounds) → the `content
  query` count unchanged, the row still in the collection and `[photosapp] delete <id>: refused by user` (T17-23); delete
  again and accept → the count drops by one and the row leaves
  the collection; set as Start background → Start's `screencap` at a tile-free point equals the fixture colour and the
  launcher slice holds `[photosapp] set as background <id> -> <file>` with the file under `files/backgrounds/`; `pm revoke
  app.tileshell android.permission.READ_MEDIA_IMAGES`, `c6` + `ensure_start` → Start's pixel unchanged (the copy needs no
  grant — r3 D9); `pm grant` it back;
  set as lock screen → `adb shell dumpsys wallpaper` shows the lock wallpaper id changed, then cleared by the
  row's restore; **slideshow: three consecutive `[photosapp] slideshow next <id>` lines whose `wall=` gaps equal the Y6
  interval (5 s) ± 100 ms and whose ids follow the collection order, each followed by `[motion] slideshow_step …` with
  settle = 250 ± 17 ms; a screencap after the second line reads that image's colour (T17-8, C-5).** ~~(screencap pixels at
  t, t+interval)~~ SUPERSEDED 2026-09-23 (host-clock timing). `media_down` (RV12).
- E6 **Editing (Q1 C — one host-checkable assertion per tool; T17-3).** `media_up` the photo and editor fixtures (r3 V9).
  Each tool on the flat-colour fixtures, the expected
  values from `qa/phase-17/scripts/edit_expect.py` (the transforms in Decisions; it reads the matrices from this doc — r3
  V12), every result a new MediaStore row (count +1) in the original's folder (both fixture folders are allowed — r3 D7)
  with the original row unchanged (same _id, same size) and the originals' md5 (`adb shell md5sum`) unchanged: crop
  `qa-photo-1` to its centre half → the copy's `width:height` = 320×240; rotate → a copy whose `width:height` are swapped;
  straighten `qa-line.png` by −10° → the copy's dimensions equal the host-computed centre crop ± 1 px and the line runs
  horizontal (the top and bottom rows of a 20-px-tall band about the line's centre read white); auto-enhance, light (+ one
  step), colour (+ one step) and each filter on `qa-photo-0..2` → the copy's centre pixel equals the matrix result ± 4 per
  channel and differs from the original's by ≥ 16 on at least one channel (an identity matrix fails — r3 V12); red-eye on
  `qa-redeye.png` → inside the disc the red channel is ≤ half its original value, a pixel 30 px outside
  is unchanged ± 1. Every copy's `is_pending` is 0 within 2 s and `[photosapp] edit <tool> -> <uri>` is in the
  `:photosedit` ring (r3 D8). The failure line: ~~with `qa-photo-2` edited after `adb shell rm` of its file (the write
  request fails)~~ (SUPERSEDED 2026-10-04 by r3 V12: an `rm` before the decode proves nothing once the original is decoded)
  a tool applied to `qa-photo-2`, then `adb shell rm` of its file, then Save (Save a copy re-reads the original at full
  resolution, so the save fails) → no new row and `[photosapp] edit <tool> failed: <why>` (T17-23). `media_down` (RV12).
- E6b **Video trim (Q1 C).** `media_up qa-steps.mp4 qa-truncated.mp4` (r3 V9). Trim `qa-steps.mp4` to 2–5 s → a new video
  row; `adb pull` it: `ffprobe -show_format` duration
  3.0 ± 0.1 s, the first frame (`ffmpeg -frames:v 1`) = colour ~~3~~ 2 (0-based, colour k on [k, k + 1) s — r3 V9) ± 8 per
  channel (E11's pixel rule), one video and one
  audio stream; the original's md5 unchanged; `[photosapp] trim <id> 2000..5000 -> <uri>` (`:photosedit` ring). Trimming
  `qa-truncated.mp4` (undecodable past its first bytes) → no new row and `[photosapp] trim <id> failed: <why>` (T17-23).
  `media_down` (RV12).
- E7 **Still capture on the virtual back camera, the automatic controls and mode availability.** Open CameraActivity
  (`dumpsys media.camera` shows device 1 opened by `app.tileshell:camera`, one device on this AVD, no front camera —
  Decisions), tap `camera_shutter` → images count +1 with `relative_path` `DCIM/Camera/` and `width:height` equal to the
  chosen resolution (`is_pending` 0 within 2 s); the Photos tile's `[photos] refresh (mediastore change)` line follows within
  2 s of the row's `date_added` (phase 01's threshold); the Camera row tile's bounds and face are unchanged (after `am
  force-stop` + Home, C-6). **Timer (re-cut 2026-09-23, T17-22; re-cut 2026-10-04 by r3 D3 / V16 — W10M offered only
  2 s, 5 s and off):** ~~set 3 s~~ set 2 s with Time lapse off; MARK = `adb shell date +%s%3N` immediately
  before the `input tap` on the shutter; the `:camera` ring (`ring_since` MARK) holds ~~`[camera] timer 3s -> shutter`~~
  `[camera] timer 2s -> shutter` and `[camera] saved <uri> …` with `saved.wall − MARK` ~~≥ 3000 and ≤ 4500~~ ≥ 2000 and
  ≤ 3500, and exactly one new row; the new row's `date_added` only corroborates (≥ MARK / 1000 + 1). ~~the new row's `date_added` ≥ the tap's uptime + 3 s …~~ SUPERSEDED 2026-09-23 by T17-22 (mixed
  clocks at 1-s resolution). **Grid:** grid on → a
  screencap of the viewfinder shows two vertical and two horizontal lines at 1/3 and 2/3 of the preview's bounds ± 2 px
  (a pixel column scan for the line colour). **Tap-to-focus:** `dumpsys media.camera` read first for the virtual camera's
  autofocus support; a tap at a known point → `[camera] focus at <x>,<y>: <state>` where state is `locked` if supported and
  `unsupported` otherwise (either is the row's expected value, chosen from the dump — not a skip). **Zoom:** set 2× → the
  virtual camera's scene has a known object (the emulated camera's coloured test pattern); its measured width in the capture
  is 2.0 ± 0.1 × the 1× width — when `dumpsys media.camera` reports a max digital zoom of 1.0 the zoom control is absent and
  `[camera] mode zoom: unavailable (max zoom 1.0)` is the expected value instead (chosen from the dump, not a skip). **Mode
  availability, both directions:** the driver reads `INFO_SUPPORTED_HARDWARE_LEVEL`,
  `REQUEST_AVAILABLE_CAPABILITIES`, `CONTROL_AE_AVAILABLE_MODES`, `CONTROL_AE_COMPENSATION_RANGE` (r3 D10),
  `LENS_INFO_MINIMUM_FOCUS_DISTANCE` and
  `CONTROL_AWB_AVAILABLE_MODES` from `dumpsys media.camera`, derives the expected mode list (Photo and Video always; Pro
  dial per the Decisions gates — each control and each white-balance preset from the same keys, r3 D10; HDR iff CameraX
  Extensions report it, r3 D3; Slow motion iff CONSTRAINED_HIGH_SPEED_VIDEO; Panorama never on x86_64; Living Images
  always), and asserts the dump's `camera_mode:<id>` nodes equal it exactly and every absent mode has its `[camera] mode
  <x>: unavailable (<reason>)` line — so a mode wrongly shown and a mode wrongly hidden both fail. **Living Images on the
  AVD (r3 V14):** "Capture living images" on in settings, one still → one file in DCIM/Camera; `adb pull` it: its bytes
  end with an MP4 (`tail -c 1M | grep -c ftyp` ≥ 1) and its XMP (`exiftool -XMP-GCamera:MotionPhoto` or `strings | grep
  MotionPhoto`) reads 1 with a `Container:Directory` item length equal to the trailing MP4's size; the setting off after.
- E8 **Video capture.** `pm revoke app.tileshell android.permission.RECORD_AUDIO` for the row (r3 D12 / V4). Tap
  `camera_record`, wait 5 s, tap again → videos count +1, `duration` ≥ 4500,
  `relative_path` `DCIM/Camera/`; `adb pull` it and `ffprobe -show_streams` shows one video ~~and one audio
  stream (the AVD microphone through qa/phase-03/scripts/audio.sh's null-sink route)~~ stream and NO audio stream, the
  `:camera` slice holds `[camera] video sound: off (no microphone permission)` and the hint naming the Microphone row is
  on screen (dump) — SUPERSEDED 2026-10-04 by r3 D12 / V4 (no row uses the microphone or the host's audio; the sound
  itself is P1's); `pm grant` RECORD_AUDIO back.
- E9 **Capture-intent contract and its guards (T17-4, T17-6; r3 D1, V3 — kept by Q-17-2 (b)).** `media_up` with no
  files (the census only, r3 V9). ~~`adb shell am start -a
  android.media.action.IMAGE_CAPTURE` → the resolver lists `app.tileshell` beside `com.android.camera2`, Open Camera and
  Fossify Camera (dump);~~ SUPERSEDED 2026-10-05 by r3 V3 (a) / Q-17-2 (b) (since Android 11 an implicit capture request
  from a caller targeting API 30+ reaches only preinstalled cameras): `record` what `cmd package query-activities -a
  android.media.action.IMAGE_CAPTURE` and `… -a android.media.action.VIDEO_CAPTURE` list (`app.tileshell/.camera.CaptureActivity`
  expected among them); `testapps/qa-capture` names the shell (`setPackage("app.tileshell")`, r3 V3) and starts
  IMAGE_CAPTURE with a `content://` `EXTRA_OUTPUT` aimed at its own cache (no ClipData of its own, so Android adds the grant
  and checks it): ~~choosing the shell's Camera,~~ `top_activity` = `app.tileshell/.camera.CaptureActivity`, tapping the
  shutter and then Done gives RESULT_OK with a decodable JPEG at that URI (`adb
  shell run-as` is not available on a release build, so the fixture logs the file's size and md5, as
  `exists=true size=<n>` for its own output path — r3 V3 (c); the driver reads them with
  `adb logcat -d -s TileShellQa`), the JPEG carries no GPS EXIF (`exiftool -GPS*` on the pulled file is empty, the pulled
  file's md5 first asserted equal to the logged one so an unread file cannot pass), and the
  images collection count is UNCHANGED (no DCIM copy); the same with VIDEO_CAPTURE (RECORD_AUDIO revoked for the video
  legs, the floor) → RESULT_OK with an mp4 `ffprobe` decodes
  at that URI; **no EXTRA_OUTPUT (r3 D1):** IMAGE_CAPTURE → RESULT_OK, the fixture logs the `data` Bitmap's width × height
  and the images count is unchanged (no file); VIDEO_CAPTURE → RESULT_OK, the videos count +1 with `relative_path`
  `DCIM/Camera/`, and the fixture logs the returned URI with `exists=true size=<n>` read through its grant; Back on either
  capture form gives RESULT_CANCELED and leaves no `is_pending` row in MediaStore; the fixture's
  `file://` output form (its own StrictMode VmPolicy relaxed first, r3 V3 (b)) → RESULT_CANCELED at once, `[camera]
  refused output scheme=file`, ~~no file written at that path (`adb shell ls` fails)~~ the fixture logs `exists=false` for
  that path (SUPERSEDED 2026-10-05 by r3 V3 (c): `ls` of the fixture's private cache always fails); **the display_photo
  negative (r3 D1):** a fixture contact inserted for the row (removed at its end, RV12) and its `photo_file_id` read
  (`content query --uri content://com.android.contacts/contacts --projection _id:photo_file_id`); qa-capture sets its own
  ClipData with no grant flag and aims EXTRA_OUTPUT at that contact's `display_photo` URI → RESULT_CANCELED, `[camera]
  refused output: no grant` in the `:camera` slice, and the contact's `photo_file_id` unchanged; **GPS control (r3 V3
  (d)):** with `adb emu geo fix -122.08 37.42` set, a capture from the Camera's own shutter (`camera_shutter` in
  CameraActivity) carries GPSLatitude (`exiftool -GPSLatitude` on its pulled DCIM/Camera file), so the no-GPS check above
  can fail. `media_down` (RV12) removes the control's row and the no-output video's.
- E10 **Tess (phase 03 E2 / E10 re-run).** "Take a photo" ~~through phase 03's audio route
  (qa/phase-03/scripts/audio.sh, utterances.py)~~ typed with `type_request` (SUPERSEDED 2026-10-04 by r3 V4: no row uses
  the microphone or the host's audio): `dumpsys activity activities` topResumedActivity (`top_activity`) =
  `app.tileshell/.camera.CameraActivity` and the reply "Opening the camera." in diagnostics (E2's row with the
  new component); `c6` + `ensure_start` (C-6); the keyguard set up as `qa/phase-03/scripts/e10.sh:51-55` does
  (`locksettings set-disabled false`, then ~~`adb shell locksettings set-pin 1234`~~ `set-pin 1234`, `get-disabled`
  asserted false — this AVD otherwise shows no keyguard) and Cortana over the keyguard (E10's setup), "take a photo" typed
  with `type_request` (the text box draws over the keyguard: `cortana/ui/CortanaSessionRoot.kt:181-190` sits outside `if
  (!state.locked)`) shows "Unlock to continue" and no CameraActivity starts (expectation unchanged); restore
  `locksettings clear --old 1234` and `locksettings set-disabled true`.
- E11 **My videos and the player.** `qa-steps.mp4` shown on the My videos page (`hub_pane:myvideos` selected) as a
  `video_tile:<id>` whose caption node reads "qa-steps" (the file name without extension) and carries no duration (Y5, R11
  1.4.8 / 1.4.11) ~~listed under the My videos pivot with duration `0:10` (dump `video_row:` text)~~ SUPERSEDED 2026-09-23 by
  T17-16; a MARK, tap → plays in `app.tileshell/.video.PlayerActivity` (`top_activity`; r3 D6); `screencap` at t = ~~3 s~~
  3.5 s (r3 V9: mid-colour, not on a boundary; timed from the `[video] playing` line) has the centre pixel = colour 3 ± 8 per
  channel; a tap on the scrubber at 70 % of the measured track (x 12 → 348 epx, so 247.2 epx = 741.6 px; T17-16) seeks to
  colour 7 ± 1 s; pause holds the pixel
  for 2 s; the controls fade after ~~3 s~~ ≈3.2 s, fading out over 367–400 ms (`[motion] controls_fade t0=… settle=…`, Y6, r3
  D5); `adb shell dumpsys media_session`
  shows an active session for `app.tileshell` (the video's); ~~the dump of Start afterwards (after `am force-stop` + Home)
  shows NO tile carrying a now-playing face and `[music]` logs no publish for it~~ (SUPERSEDED 2026-10-04 by r3 V5: after
  the force-stop the session is gone, so that held whatever the routing did) while the video plays, the launcher slice
  from the MARK taken before play holds `[music] session app.tileshell id=video -> none` (`feeds/MusicFeed.kt:164`,
  `TileRouting.kt:44-51`; r3 D16) and `absent_in "id=video -> cmp:"` (Decisions: a `video` session lands on no tile and in
  no pod).
- E12 **Audio focus and media keys.** Play a track in the shell's Music (qa/phase-01/MUSIC6's fixtures), open
  the video: `dumpsys media_session` shows Music's session PAUSED and the video's PLAYING; `adb shell input
  keyevent KEYCODE_MEDIA_PAUSE` pauses the VIDEO (the last active session), not Music; Back out of the video →
  Music stays paused (phase 10 E9: nothing resumes by itself).
- E13 **VIEW contract, network sources and their failure states (re-cut 2026-09-23, T17-1).** `adb shell am start -a
  android.intent.action.VIEW -d content://media/external/video/media/<id> -t video/mp4` → the resolver lists the shell's
  player beside Aves and Fossify Gallery (dump); `adb shell am start -n app.tileshell/.video.PlayerActivity -a
  android.intent.action.VIEW -d http://10.0.2.2:8090/qa-steps.mp4 -t video/mp4` (component was `.video.VideoActivity`,
  SUPERSEDED 2026-10-04 by r3 D6; catalogue_server.py serving the file) → it
  PLAYS under E11's pixel rule with `[video] playing scheme=http`; `cmd connectivity airplane-mode enable` and the same
  intent → "Can't reach this video" (dump text), `[video] cannot reach 10.0.2.2:8090`, the player still resumed, no crash;
  `airplane-mode disable` (RV12); `…/404` → the error state and `[video] cannot decode 404` (the server's status in the line);
  `-d rtsp://10.0.2.2/x` → "Can't play this address", `[video] unsupported scheme=rtsp`, no ExoPlayer source created.
  ~~`http://127.0.0.1:1/x.mp4` → "Only videos on this phone play here", `[video] refused scheme=http`~~ SUPERSEDED 2026-09-23.
  **Cleartext (C-16; the expected branch Q-D: A).** The `10.0.2.2` play above holds under every answer (the debug
  config's exception). A host the exception does not cover: the sub-step inserts its own counted rule above the egress
  guard's (`iptables -I OUTPUT -m owner --uid-owner "$APP_UID" -d 10.0.2.3 -p tcp --dport 8090 -j REJECT`, so the guard's
  counter stays 0; `$APP_UID` per task 16, r3 V1) and starts the same VIEW with `-d http://10.0.2.3:8090/qa-steps.mp4` →
  ~~under **A**~~ the platform lets the attempt through: that rule counts ≥ 1 packet and the line is `[video] cannot reach
  10.0.2.3:8090`; ~~under **B** and **C** it counts 0 packets, the page says "This video's address isn't secure" and the
  line is `[video] cleartext refused 10.0.2.3`~~ SUPERSEDED 2026-10-04 by r3 V6: Q-D A is the only form built, so
  `[video] cleartext refused 10.0.2.3` or a 0-packet count on that rule FAILS the row; the rule is deleted. The fixed hosts: `am force-stop app.tileshell`, MARK, Home → the launcher ring holds `[net] cleartext
  permitted for <host>: false` for every host in `FixedEndpoints` (and the JVM test of task 17 passes) — a fixed host read
  `true` fails the row under every answer.
- E14 **Odd files.** `qa-steps.webm` (vp9) and `qa-steps-hevc.mp4` play (pixel rule as E11) — these two sub-rows are
  RECORDED against the AVD's decoder list read from /vendor/etc/media_codecs.xml at run time (a decoder the AVD lacks is a
  recorded fact, not a failure; C-26's `record`); `qa-rot90.mp4` draws portrait (the coloured area's aspect from `screencap` is taller
  than wide); `qa-audio-only.mp4` plays with a black frame and takes focus; `qa-truncated.mp4` and `qa-empty.mp4` show the
  error state ("can't play this file") with the player still resumed and `adb logcat -d -T '<MARK as s.mmm>' -s
  AndroidRuntime` (scoped to the row from its MARK, logcat's epoch form — r3 V22) empty of `app.tileshell` — these are gated.
- E15 **Permissions, checklist and processes.** `pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO`
  → the video app's empty state names the checklist and the "Videos" row is red (dump: `checklist:videos:missing` — "red"
  means that tag, r3 V22); `pm revoke … CAMERA` → the Camera app shows the grant page and the "Camera" row is red
  (`checklist:camera:missing`); granting through Android's real dialog from
  the app (phase 10 MUSIC10's method) turns both green; `adb shell pidof app.tileshell:camera` exists while the
  viewfinder is open, and after `adb root; kill -9 <pid>; adb unroot` the launcher pid is unchanged and Start
  keeps updating (phase 03 E12's method); the same for `:video`.
- E16 **Routing (phase 10 E10 / E13 re-run on this build, under phase 15's build task 0 — C-1).** Play a track in the shell's
  Music: the MUSIC slot tile grows and shows the face and its controls drive playback (E13's three findings); the PHOTOS
  tile and the `dock:slot:CAMERA` tile have unchanged bounds and no now-playing tags in the dump (`gdump`, with
  `start_page` asserted in it first — r3 V7); playing in Auxio (a pinned
  tile) still moves the face to Auxio's tile (E11). After a phase 03 reminder fires, neither the Photos tile nor the Camera
  row tile shows a badge from the shell's own notification.
- E17 **Budget and surface.** `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes
  (600 MB, phase 03 Decisions); the OpenCV delta — `unzip -l app-debug.apk 'lib/arm64-v8a/libopencv*'` summed — ≤ 20,971,520
  bytes (20 MB, T17-2), with ≥ 1 `lib/arm64-v8a/libopencv*.so` entry and none under `lib/x86_64` (so an APK without
  OpenCV cannot pass the bound — r3 V19), and the whole-phase delta against `qa/phase-17/upgrade/<tag>.apk` recorded; `adb shell dumpsys package
  app.tileshell` exported components equal qa/phase-03/exported-allowlist.txt plus this phase's ADDs, exactly (phase 03 E5's
  method); `aapt2 dump permissions` on the APK lists `android.permission.SET_WALLPAPER` and no
  `android.permission.FOREGROUND_SERVICE_CAMERA` (T17-18, T17-19). **Network config (C-16 (3)):** `./gradlew
  :app:assembleRelease` ~~`-Ptmdb.readToken=`~~ (unsigned is enough; the property SUPERSEDED 2026-10-05 by Q-17-1 (a)),
  then `aapt2 dump xmltree --file res/xml/network_security_config.xml <release apk>` holds no `10.0.2.2` and lists every `FixedEndpoints` host under
  `cleartextTrafficPermitted=false`; the same dump of the debug APK holds the `10.0.2.2` exception (the control that proves
  the check reads the right file). ~~**No key in a CI-form build (Q-A2: B):** the CI-form APK (`-Ptmdb.readToken=`) and the
  release APK above are unzipped and `qa/phase-17/scripts/leak_scan.sh` over both finds none of local.properties' `tmdb.*`
  values (a build that fell back to local.properties despite the empty property fails here).~~ SUPERSEDED 2026-10-05 by
  Q-17-1 (a). **No TMDB token in any build (Q-17-1 (a); r3 D14):** `app/build.gradle.kts` reads no `tmdb.` property
  (`grep -c 'tmdb\.'` = 0) and holds `buildConfig = true`; the generated `BuildConfig` sources
  (`app/build/generated/source/buildConfig/`) hold `DEBUG` and no TMDB field; the debug APK and the release APK above are
  unzipped and `qa/phase-17/scripts/leak_scan.sh` over both finds none of the `tmdb.*` values local.properties still holds.
- E18 **Diagnostics.** Each of these lines exists when its state does, and the rows above assert the line as
  well as the screen: `[photosapp] library: images=<n> videos=<n> access=<GRANTED|PARTIAL|DENIED>`, `[photosapp] slideshow
  next <id>`, `[photosapp] edit <tool> -> <uri>`, `[photosapp] trim <id> <from>..<to> -> <uri>`, `[photosapp] delete <id>:
  refused by user`, `[photosapp] edit <tool> failed: <why>`, `[photosapp] trim <id> failed: <why>` (T17-23),
  `[camera] devices=<n> front=<present|absent>`, `[camera] busy: <reason>`, `[camera] saved <uri> <w>x<h>`, `[camera] timer
  <n>s -> shutter`, `[camera] focus at <x>,<y>: <state>`, `[camera] mode <x>: unavailable (<reason>)`, `[camera] refused
  output scheme=<s>`, `[video] library: <n>`, `[video] playing <id>`, `[video] playing scheme=<s>`, `[video] cannot decode
  <name>`, `[video] cannot reach <host>`, `[video] unsupported scheme=<s>`, ~~`[video] cleartext refused <host>` (under Q-D B /
  C)~~, `[video] catalogue "<q>": <n> | offline | error <code>` (and its `<n> (refreshed)` form, E20), ~~`[video] catalogue: no TMDB key in this build`~~ `[video] catalogue: no TMDB
  key saved` (SUPERSEDED 2026-10-05 by Q-17-1 (a)), `[video]
  watch-on <service> "<title>" -> <intent> | not installed`, `[video] watch-on <service> "<title>": id <found|none>
  (wikidata)`, `[video] server <host>: connected | unreachable | unauthorised | insecure, asked` (its ~~`cleartext
  refused`~~ alternative struck, r3 V6),
  `[video] server token cleared`, `[video] shortcut mediaserver published | removed`, `[net] cleartext permitted for <host>:
  <bool>`, added 2026-10-04 by round 3: `[photosapp] set as background <id> -> <file>` (D9), `[camera] video sound: off (no
  microphone permission)` (D12), `[camera] mode hdr: unavailable` (D3, one form of the `mode <x>` line), `[camera] slowmo <n>
  fps captured, encoded at 30 -> <uri>` (D11), `[music] session app.tileshell id=video -> none` (D16, V5), added
  2026-10-05: `[camera] refused output: no grant` (D1, Q-17-2 (b); E9), and every
  `[motion] <name> …` line the rows time. ~~`[video] refused scheme=<s>`~~ SUPERSEDED 2026-09-23 by T17-1;
  ~~`| no key`~~ SUPERSEDED 2026-09-23 by T17-14 (the one no-key line); the two `cleartext refused` forms SUPERSEDED
  2026-10-04 by r3 V6 (Q-D A is the only form built; E13 and E22 fail on them). **How it is read (C-20; phase 16's form
  since r3 V13):** grep the union of
  `qa/phase-17/*/ring-*.txt` from this build's run (the rows' APK id matching — the launcher, `:camera`, `:video` and
  `:photosedit` slices, the edge sub-steps' included) for each alternative of each line (each `|` choice separately).
  `qa/phase-17/E18/producers.tsv` names, for each alternative, the row or edge sub-step that produces it, and an
  alternative it names that no slice holds fails the row. `qa/phase-17/E18/notrun.tsv` lists the alternatives no AVD row
  can produce, each with its reason and its JVM test or P row — as written today: `[camera] devices=<n> front=present`
  (the AVD has no front camera; P1), `[camera] slowmo …` (no high-speed session on the AVD; P11). An alternative in
  neither file fails the row. ~~each pattern at least once; a pattern absent from every slice fails the row~~ (was "the
  launcher, `:camera` and `:video` slices, each pattern at least once", which no AVD row could pass for `front=present`,
  `[camera] busy` or `[video] library` — r3 V13).
- E19 **Geometry and motion against R11 (written 2026-09-23, T17-16 / T17-17).** Dumps (px ÷ 3 = epx) and screencaps on
  the AVD; tolerances: ± 1 epx for R11 HIGH values, ± 2 epx for MEDIUM, ~~± 3 epx for the INFERRED / rotated approximations
  (Y3, Y5's transport centres),~~ (SUPERSEDED 2026-10-04 by r3 D3 / D5: Y3 is MEDIUM and Y5's transport HIGH now) order and
  presence only for LOW; colours ± 4 per channel; motion bounds from a pass-2 addendum ± 33 ms (30-fps sources). **Photos**
  (Y1, Y2, Y7, Y11, Y13): on the
  collection, albums, viewer and editor no drawn status-bar node and the first chrome row's top at 0; collection grid 3
  columns, 111-epx squares, 2-epx gutters, left 11 / right 12; pivot titles "Collection" / "Albums" mixed case with no
  header band behind them (the pixel above and below the titles = page black); a video tile's disc 36 ± 2 epx; album tiles
  60 epx tall, 162 wide, 12-epx margins and gutter; viewer date header 0 → 50 epx, #171717; viewer app bar 48 epx,
  ~~#171717, glyph centres 286 / 218 / 150 / 82 / 24 epx from the right~~ (SUPERSEDED 2026-10-04 by r3 D4 / V17) black,
  Share · Edit · Delete · More from left to right with no Favorite (LOW: order and presence; 218 / 150 / 82 / 24 from the
  right in r11/photos-pass2.md); the photo's vertical centre = the screen's centre;
  the overflow's items Slideshow / Set as / File information at a 44-epx pitch; the editor's 48-epx bottom tool strip with
  Crop · Enhance · Rotate · Save · More in that order (LOW); the trim screen's command bar at the top (Save a copy · Cancel ·
  More), two handle nodes on the track, a time-label node at each end of the track and no centred time-readout node (LOW).
  **Camera** (Y3, Y4, Y7, settings): no
  drawn status-bar node; nav bar 48 epx; the photo preview 4:3 fitted to width and centred ~~above the nav bar~~ on the full
  screen (its vertical centre H/2 ± 2 epx — r3 V16); shutter disc
  72 epx at W/2, centre 56 epx above the nav bar top, white fill with a #2B2B2B ring at r 32–34 (V16); the mode discs 32 epx
  at ±60 epx, centres 36 above; the capsule vertical on the right edge, 2 → 45.5 epx from it, its glyph centres 24 from the
  right at H/2 − 66 / − 22 / + 22 / + 66 (four items) or − 43.7 / + 0.5 / + 45.1 (three), in the Y3 order for the mode
  shown, no capsule node in panorama; settings disc 24 from the right and 27.9 above the nav-bar top; camera roll centre
  24.7 from the left and 28 above the nav-bar top (all ± 2, MEDIUM); the camera switch's place is P1's (the AVD has no front
  camera); no horizontal mode strip node; settings page — header cap 16.5–16.75 epx at x 12, combo boxes 32 epx tall with a 2-epx border and the
  chevron 22.25 epx from the right, label → label pitch 80 epx, an open list's items at a 44-epx pitch with the current one
  accent-filled, the "Framing grid" and "Capture living images" rows present (r3 D3), and no Lenses, OneDrive or "Related
  settings" row. **Pro dial** (Y4), wherever E7's derived list shows the
  Pro dial (on the AVD this is read from E7; when the AVD's camera lacks the capabilities the sub-row runs on the phone as
  part of P9 — never skipped silently, the row logs which): five arcs whose fitted centre is (W/2, nav top) ± 1 epx and
  radii 130.5 / 195.4 / 260.3 / 325.3 / 390.2 ± 1 epx, inner → outer exposure / shutter / ISO / focus / WB (each ring's
  icon node on it), value labels centred at W/2 30.3 ± 1 epx above each ring's top, the shutter at 74.75 epx above the nav
  top in the five-ring view; one control alone = one arc of r 130.25 ± 1 epx. **Movies & TV** (Y5, Y7, the pane): library
  pages draw phase 01's status bar (C-17) and a 48-epx #171717 header, ≡ at x 24, the title at x 60.25, search at W − 24;
  the pane 256 epx wide from the chrome bottom, #171717, no scrim (the page pixels right of the pane unchanged, as R11 1.3.1
  measured), rows 48 epx, glyph cx 24, label x 48, the current row's 4 × 48-epx accent bar at x 0; My videos tiles 112 ×
  112 epx on a 124-epx pitch from x 12, first row top = chrome bottom + 48, 2 per row, captions ≤ 2 lines clipped at tile
  left + 100 epx; the player: no status bar and no header, nav bar drawn, the scrim band 120 epx above the nav bar, the
  track 2 epx at nav − 93 from x 12 to 348, ~~the thumb an accent ring Ø 24, HH:MM:SS labels below the track (elapsed left at
  12.5, total right-aligned at 345.5), the transport in the order captions · back 10 · play / pause · forward 30 · "•••" with
  centres 84 / 132 / 180 / 228 / 276 epx on a row centred at nav − 40~~ (SUPERSEDED 2026-10-04 by r3 D5 / V18) the thumb an
  accent ring Ø 22 with a 2-epx stroke, its centre 1.2 epx below the track's; the played portion accent and the unplayed
  track white at 25 %; time labels below the track, baseline nav − 67.6, in the locale's format (H:MM:SS on en-US), elapsed
  left at 12.5 and the REMAINING time right-aligned at 345.5 (its value = duration − position, ± 1 s); transport centres
  back 10 W/2 − 48, play / pause W/2, forward 30 W/2 + 48, full screen W − 84, "•••" W − 36 (± 1, HIGH) on a row centred
  at nav − 39.8, with no CC node for `qa-steps.mp4` alone (no text track) and CC at 36 once `media_up` also brings
  `qa-steps.srt` beside it; the
  "•••" menu's items Cast to device / Zoom to fill / Repeat / Autoplay in that order; the video letterboxed (fit) by
  default. **Motion:** every motion Y6 lists logs its `[motion]` line and `maxGapMs` ≤ 33.4 ms (C-31). ~~with the
  approximation's numbers (viewer open settle 250 ± 17 ms; photo swipe settle 290 ± 17 ms after release; the pane slide
  133 ± 17 ms; controls fade-in 200 ± 17 ms; the mode switch a single frame)~~ SUPERSEDED 2026-10-04 by r3 D3–D5 /
  V16–V18. Measured bounds, ± 33 ms: the photo swipe's whole fling ≤ 234 ms, the 20-epx gap between photos read from the
  corroborating screenrecord's mid-swipe frame (LOW: presence); the pane open settled at 167–200 ms, its close 67–100 ms;
  library → player a cut (no `[motion]` line for it, no in-between frame in the screenrecord); controls fade-in 167–300
  ms, hold ≈3.2 s (3.17–3.32), fade-out 367–400 ms; the camera mode switch a slide of 300 ms (out and in each ≈150 ms,
  a black gap between); capture feedback black for 167 ms with the chrome unchanged; the dial-open sweep 333 ms (where
  E7's list shows the dial, else on the phone with P9). Approximations, judged by H4: viewer open / close settle 250 ±
  17 ms (the expand-from-thumbnail form); the slideshow step's 250-ms settle. ~~Written once those sections land …~~
  SUPERSEDED 2026-09-23 by T17-16.
- E20 **Catalogue (TMDB — Q-A; the key route ~~Q-A2: B~~ Q-17-1 (a); host fixture, never the live network, inside the
  egress guard — C-29).** ~~On the QA APK (assembled with `-Ptmdb.readToken=qa-dummy-token`, C-32):~~ (SUPERSEDED
  2026-10-05 by Q-17-1 (a): no APK carries a token) `prefs_edit.py` sets
  `qa_catalogue_base` to `http://10.0.2.2:8090/` (read back); **no key saved (Q-17-1 (a); r3 V21's offset form):** MARK,
  the fixture log's line count taken on the host as the offset, open Browse (`hub_pane:browse`) → the page says film
  search needs a key, with the link to the setting (dump), `[video] catalogue: no TMDB key saved` in the `:video` slice,
  and NO request line in the fixture's log after the offset; then the driver's own control request (`curl` to the fixture
  from the host) must appear after the offset — the proof that the log read is the live one; **enter:** the link opens
  Movies & TV's settings page at its TMDB key setting (dump), `qa-dummy-token` is typed there (`input text`) and saved;
  back on Browse, search "Blade Runner" → exactly three
  `hub_result:<id>` rows whose title / year texts equal the fixture JSON hand-listed here — "Blade Runner" 1982, "Blade
  Runner 2049" 2017, "Blade Runner: Black Lotus" 2021 — with artwork loaded (each row's image node has non-zero bounds and the
  pulled fixture PNG's colour at its centre ± 4), and the fixture's log shows the `/3/configuration` request and one
  `/img/…` request per poster (T17-20), `[video] catalogue "Blade Runner": 3`; every request in the fixture's log reads
  `bearer ok` with no `api_key` parameter (T17-13); the Browse page's foot `hub_attribution` reads TMDB's text (T17-14);
  open the first → the title page (`hub_title`) with its overview text from the fixture and the "Watch on" list from the
  fixture's providers (E21). ~~**No key — the CI form (Q-A2: B):** `adb install -r` the CI-form APK (`-Ptmdb.readToken=`),
  MARK,~~ ~~open Browse → "Film search is off in this build" (dump), `[video] catalogue: no TMDB key in this build` in the
  `:video` slice, and NO request line in the fixture's log from the MARK to the row's end~~ (SUPERSEDED 2026-10-04 by r3
  V21: mixed clocks, and a log never proven readable) ~~the `qa_catalogue_base` pref read back as the fixture's URL (it
  survives `install -r`), the fixture log's line count taken on the host as the offset, open Browse → "Film search is off
  in this build" (dump), `[video] catalogue: no TMDB key in this build` in the `:video` slice, and NO request line in the
  fixture's log after the offset; then the driver's own control request (`curl` to the fixture from the host) must appear
  after the offset — the proof that the log read is the live one; the QA APK re-installed after
  (both APK ids in the row log).~~ SUPERSEDED 2026-10-05 by Q-17-1 (a): there is no CI-form APK; the no-key leg is the
  setting left empty, at the row's start, in the same offset form. ~~under Q-A A / C … under Q-A B … the `api_key` request log … `tmdb.apiKey` empty … under
  Q-A C … `input text qa-key` …~~ SUPERSEDED 2026-09-23 by T17-13 / T17-14 (a bearer-token build never sends `api_key`, so the
  old assertion failed a correct build and put Jeremy's key in the fixture's log; the keyless and typed-key forms are dead).
  Offline: `cmd connectivity airplane-mode enable`, force-stop and reopen → the same three rows from the cache with the line
  "You're offline — showing what was saved" (dump) and `[video] catalogue "Blade Runner": offline`, the My videos page still
  shows `qa-steps.mp4`; a fresh install offline (`qa-dummy-token` typed into the setting again: a fresh install holds no
  key, Q-17-1 (a)) → the Browse page's empty state names the cause, not a blank; `airplane-mode
  disable` (RV12). Errors: base URL at `/500` → "The catalogue isn't answering" and `[video] catalogue "Blade Runner": error
  500`; a stopped fixture server → `error connect`. The cache older than 7 days (a cache entry's age is its file's mtime —
  r3 V22; the mtime moved back with `touch -d` under `adb root`, `adb unroot` after) → re-fetched, `[video] catalogue … : 3 (refreshed)`.
  **Remove (Q-17-1 (a); RV12, the row ends with no key saved):** the key removed in the setting, a MARK, open Browse → the
  no-key page again and `[video] catalogue: no TMDB key saved` in the slice. (Replace is the 401 edge sub-step's.) **Leak
  scan (gated, T17-13 / C-32):** `qa/phase-17/scripts/leak_scan.sh qa-dummy-token` over `qa/phase-17/**`, E20's saved
  launcher and `:video` ring slices and `adb logcat -d` finds ~~none of local.properties' `tmdb.*` values~~ neither the
  typed token nor any `tmdb.*` value local.properties still holds (SUPERSEDED 2026-10-05 by Q-17-1 (a)).
- E21 **"Watch on" (the QA-Flix fixture; the title id through Wikidata — T17-15; inside the egress guard).**
  `qa-dummy-token` saved in Movies & TV's TMDB key setting first, as E20 enters it, and removed at the row's end (Q-17-1
  (a); RV12); `prefs_edit.py` sets `qa_wikidata_base` to the fixture as well. With `testapps/qa-flix` NOT installed, a title's page
  (`hub_title` asserted first — r3 V7) shows no "Watch on QA-Flix" row and `[video] watch-on qa-flix "Blade Runner 2049" -> not installed` when the fixture
  catalogue names it; `adb install` the fixture → the row appears on the next draw (the discovery is live); **id branch**
  ("Blade Runner 2049", whose fixture Wikidata answer holds a QA-Flix id): tap →
  the fixture's log shows the Wikidata-shaped query keyed on the title's TMDB id, `dumpsys activity activities` shows
  `app.tileshell.testclient.qaflix/.MainActivity` resumed and its `qa_flix_uri` text equals `https://qa-flix.test/title/
  <that id>` exactly, `[video] watch-on qa-flix "Blade Runner 2049": id found (wikidata)` and `[video] watch-on qa-flix
  "Blade Runner 2049" -> https://qa-flix.test/title/…` with the intent's package `app.tileshell.testclient.qaflix` (r3 D13:
  without it Android 12+ opens an unverified domain in the browser); **search branch:** a title whose Wikidata
  answer holds no QA-Flix id ("Blade Runner" 1982 in the fixture) → `id none (wikidata)` and the search form
  `https://qa-flix.test/search?q=Blade+Runner+1982` reaches the fixture exactly; a title the fixture catalogue marks as on
  no service → the page reads "Not on this phone" (dump) and no row; uninstall the fixture → the row is gone. ~~**under Q-A
  B** … "Search on QA-Flix" …~~ SUPERSEDED 2026-09-23 by T17-14. Every service's real deep link is P13, never asserted here.
- E22 **Media server (Jellyfin — Q-B; the pinned container on the host, its digest in the row log — T17-21; inside the
  egress guard).** `qa-dummy-token` saved in Movies & TV's TMDB key setting first, as E20 enters it, so the credential
  store holds both tokens (Q-17-1 (a); removed at the row's end, RV12). "Add a server" with host `10.0.2.2:8096`, user and password → `[video] server 10.0.2.2:8096: connected`,
  the Media server row appears in the pane (`hub_pane:mediaserver`) and its page lists `qa-steps.mp4`, tap → it plays under
  E11's pixel rule with `[video] playing scheme=http` (10.0.2.2 is the debug config's exception, so under every Q-D answer);
  `am force-stop` and reopen → still connected (the token persisted); the credential store holds no plaintext: the driver
  reads the issued token from the server (~~`GET /Sessions` with the fixture admin's token, the session whose `Client` is the
  shell~~ SUPERSEDED 2026-10-04 by r3 V8: SessionInfoDto has no AccessToken, and an empty `$TOKEN` makes `grep -rlF ""`
  match every file — `GET /Devices` with the fixture admin's token, the item whose `AppName` is the shell's client, its
  `AccessToken`, asserted non-empty before the scan), then `adb root`, `adb shell grep -rlF "$TOKEN" /data/data/app.tileshell/` and `adb shell grep -rl qa-password
  /data/data/app.tileshell/` (the fixture account's password) both list no file, and so does `adb shell grep -rlF
  qa-dummy-token /data/data/app.tileshell/` (the TMDB token, Q-17-1 (a)), `adb unroot` — a store that wrote any of them in
  the clear fails here; wrong password →
  "That password isn't right" and `[video] server 10.0.2.2:8096: unauthorised`, no pane row (`hub_pane:myvideos` asserted
  in the same dump first — r3 V7); `docker stop` the container →
  the page shows "Can't reach your media server" and `[video] server …: unreachable`, the My videos page untouched, no crash;
  `docker start` → reconnects on the next open. **Insecure address (C-16 (5); expected branch Q-D: A):** "Add a server"
  at `http://192.0.2.10:8096` (a documentation address, not private) → ~~under **A**~~ the page asks "This server isn't secure —
  your password would be sent unencrypted" with `[video] server 192.0.2.10:8096: insecure, asked`, and Cancel sends nothing
  (no `connected` / `unreachable` line after it, `absent_in`; the egress guard's counter stays 0); ~~under **B** and **C** it
  is refused before any request with `[video] server 192.0.2.10:8096: cleartext refused`~~ SUPERSEDED 2026-10-04 by r3 V6:
  Q-D A is the only form built, so a `cleartext refused` line FAILS the row. **Remove the server** → `[video] server token
  cleared`, the pane row and the dynamic shortcut gone (E23), and the exact-token grep above → no file (T17-23). **Leak scan
  (C-32):** `qa/phase-17/scripts/leak_scan.sh "$TOKEN" qa-password qa-dummy-token` (the TMDB token too, Q-17-1 (a)) over
  `qa/phase-17/**`, the row's ring slices (the diagnostics rings) and `adb logcat -d` finds none; the stream URL in the `:video` slice carries no query string. ~~`adb shell grep -l -E
  '[0-9a-f]{32}' …`~~ SUPERSEDED 2026-09-23 by T17-21 (it matched any 32-hex value any feature stores); ~~**Under Q-B B / C
  (Plex at `10.0.2.2:32400`)** … **Under Q-B B** …~~ SUPERSEDED 2026-09-23 by T17-14 (Jellyfin only).
- E23 **App Shortcuts (phase 11 Q1's standing rule; C-8 / C-9).** The three apps' tiles pinned through
  `qa/phase-17/baseline_layout.json`; hold the Photos tile → `quick_sat_label:0..1` = "Collection", "Albums" in rank order
  (phase 11 E3's method), `quick_sat:2..3` absent, `[quick] shortcuts for app.tileshell/.photos.PhotosActivity/0: 2 (2
  shown: photos_collection,photos_albums)` (the activity-keyed line, T11-12); tap each → PhotosActivity resumed with that
  pivot's header `selected="true"` (`photos_pivot:collection` /
  `:albums`), and `dumpsys shortcut` lists `photos_collection` rank 0 and `photos_albums` rank 1 as manifest shortcuts; hold
  the Camera tile → "Photo", "Video", then the dynamic modes E7's availability row predicts for this device, in rank order
  (on the AVD "Photo", "Video" only — panorama is never on x86_64 and slow motion only with CONSTRAINED_HIGH_SPEED_VIDEO),
  `[quick] shortcuts for app.tileshell/.camera.CameraActivity/0: 2 (2 shown: camera_photo,camera_video)` on the AVD,
  `dumpsys shortcut` lists `camera_photo` / `camera_video` as manifest ranks 0–1 and exactly the predicted dynamic ids (none on
  the AVD: `camera_panorama` / `camera_slowmo` absent), tap each → CameraActivity on that mode (`camera_mode:<id>` selected);
  hold the Movies & TV tile with no server → "My videos", "Browse" (2 satellites), `[quick] shortcuts for
  app.tileshell/.video.VideoActivity/0: 2 (2 shown: video_myvideos,video_browse)`, `dumpsys shortcut` lists `video_myvideos`
  rank 0, `video_browse` rank 1 and no `video_mediaserver`; tap each → VideoActivity with that pane row current
  (`hub_pane:myvideos` / `:browse`); after ~~E22's set-up~~ this row's own server set-up (E22's container and "Add a server"
  steps, done here inside the egress guard, so the row depends on no other row — r3 V22) → 3
  satellites with "Media server" last, `… VideoActivity/0: 3 (3 shown: video_myvideos,video_browse,video_mediaserver)`,
  `dumpsys shortcut` lists `video_mediaserver` as a dynamic shortcut whose activity is `.video.VideoActivity`, `[video]
  shortcut mediaserver published`; then hold the Music tile → `[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4
  (4 shown: songs,albums,artists,playlists)` with no dynamic id in it (C-21: a dynamic shortcut without `setActivity` would
  land here); remove the server → back to 2 and `… removed`. `am force-stop` + Home between holds (C-6; `c6` +
  `ensure_start`, r3 V7).
- E24 **Baseline and seeds (C-3).** `layout_restore` from `qa/phase-17/baseline_layout.json` then Home (`ensure_start`): the
  ring holds ZERO `assignSlotOnce … -> assigned` lines (`absent_in`, r3 V7) and an `-> already run` line for every
  `assignSlotOnce` marker the baseline holds (r3 V7; five as the list below is written today: `slot:music:v1`,
  `slot:calendar:v1`, `slot:people:v1`, `slot:photos:v1`, `slot:camera:v1`), the restored file's `addedOnce` equals the baseline file's exactly (as written today:
  phase 02's `phase03:cortana, folder:games:v1, folder:office:v1, slot:music:v1`, phase 16's `slot:calendar:v1,
  slot:people:v1`, this phase's `slot:photos:v1, slot:camera:v1`, plus any marker phases 11–15 add, which their own
  baselines carry into phase 16's) and
  `slots` holds PHOTOS → PhotosActivity and CAMERA → CameraActivity; the same run against
  `qa/phase-17/baseline_layout-pre-17.json` shows exactly two `-> assigned` lines (the negative that proves the assertion can
  fail).
- E25 **Wizard steps added (phase 12 E14's template, C-4 b; the three-part form, C-15).** (a) `pm clear app.tileshell` →
  `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` → `adb shell pm revoke app.tileshell
  android.permission.CAMERA` and `… READ_MEDIA_VIDEO` → Home: `wizard_step:setup:camera` and `wizard_step:setup:videos`
  present, each with its `wizard_why` text equal to task 8's line, in the Setup rows' order, `wizard_progress` reads "Step 1
  of 3" (two steps and the presets page); `pm grant` both from adb and resume → both steps gone and `wizard_presets` shows.
  (b) `pm clear` → `provision.sh` (the marker written) → Home → no `wizard_page` and, after a MARK and a resume (`am start`
  the Settings hub, Home), `[wizard] not shown: core held` in the slice (phase 12
  E1 re-run on this build, C-4 c). (c) The finished-install rule: with the marker set (after (b)), revoke both → a MARK, a
  resume as in (b) → no
  `wizard_page`, `[wizard] not shown: finished` in the slice, the "Camera" and "Videos" checklist rows
  `missing`; `pm grant` both (RV12). ((b) and (c) take phase 12 E14's MARK and resume — r3 V22.)
  ~~`pm clear` + `provision.sh` minus this phase's two grants … `pm grant` both from adb → absent, the wizard not shown~~
  SUPERSEDED 2026-09-23 by C-15 (provisioning now writes the finished marker, so that form could never show a step).

**Phone-only (S25 Ultra):** every step is done on the phone alone — nothing over adb, USB or a PC (INDEX R4; r3 V15,
phase 16's form); a value a row needs is read on a page of the shell (Settings > Diagnostics with the `:camera`, `:video`
and `:photosedit` rings, the probe page, or the app's own page) and pasted back; no row uses adb, `exiftool` or
`ffprobe` (Decisions, r3 V15). Every row runs on the CI build the phone installs from the repository's "latest" release
page (Q-17-1 (a): no build carries the TMDB key, so there is no other build to install); P13 and P15 (and H10, H11) after
Jeremy pastes his TMDB read token into Movies & TV's settings.
- P0 **The PHOTOS and CAMERA slots after the update (Q-17-3 (a); phase 16 P0's form; added 2026-10-05):** right after
  installing the build that brings Photos and Camera, before anything else: tap the Photos tile and the Camera tile, and
  ask Tess "take a photo"; open Settings > Diagnostics BEFORE restarting the phone and paste the two `assignSlotOnce
  slot:photos:v1 …` / `slot:camera:v1 …` lines; then Settings > Tile apps → Camera → Samsung Camera; restart the phone;
  later, after the next update of the shell, look again → both tiles open the shell's apps and "take a photo" opens the
  shell's Camera; each line reads `-> assigned, replaced user's <app>` (where a slot had been picked by hand) or plain
  `-> assigned`; after pointing Camera back, the Camera tile and "take a photo" open Samsung Camera and still do after
  the restart and after the next update. Each slot is left on the app Jeremy wants.
- P1 Front camera, flash, HDR / Night (CameraX Extensions as the S25U exposes them), zoom across lenses, and the full
  mode list in Y3's discs and capsule (Photo, Video, Pro dial, Panorama, Slow motion, Living Images — T17-12; ~~the mode
  strip~~ SUPERSEDED 2026-09-23 by T17-16): ~~`adb shell dumpsys
  media.camera` lists~~ (SUPERSEDED 2026-10-04 by r3 V15) the probe page lists the ids the public API exposes (logical
  multi-camera vs separate ids) and the capabilities E7's
  availability row reads, so the expected list is derived on the phone as on the AVD; each mode captures (~~E7's
  assertions~~ the `[camera] saved <uri> <w>x<h>` line on Diagnostics and the picture in Photos), image quality judged in
  H5; the camera switch top-right, 24 from the right and 28 from the top ± 2 epx (camera-pass2 1.6.6; the AVD has no front
  camera — r3 D3), read on a phone screenshot Jeremy shares; video sound (r3 D12): with Microphone granted a take has one
  audio track (probe page) and its sound plays back, and with Microphone revoked in Android's settings the take is silent,
  `[camera] video sound: off (no microphone permission)` is on Diagnostics and the hint names the Microphone row; E23's
  Camera half re-run on the phone (the satellites seen, the `[quick] shortcuts for …CameraActivity/0: …` line read on
  Diagnostics), where `camera_panorama` (arm64) and `camera_slowmo` (if the S25U lists the capability) are the dynamic
  shortcuts it predicts.
- P2 Side key double press: whether One UI's Side key page offers "Open app" for the shell's Camera; recorded
  either way; the shell cannot set it (Decisions). RECORDED.
- P3 The S25U's own recordings in Photos and the player: HEIC stills, HEVC / HDR10+ / 4K60 videos, Samsung
  Motion Photos (shown as stills; a Samsung Motion Photo's clip plays on hold as a Living Image if its XMP is the Motion
  Photo form — recorded); ~~decode and playback asserted with E11's pixel rule where a fixture allows~~ (SUPERSEDED
  2026-10-04 by r3 V15) each seen to show or play, with its `[video] playing <id>` or `[photosapp]` line and its probe-page
  facts (codec, size, tracks) pasted back. RECORDED.
- P4 One UI's "Open with" for `VIEW video/*` and `VIEW image/*`: which apps the sheet lists and whether the shell
  can be set as default (~~`am start` with a MediaStore URI, the resolver captured~~ opened from Samsung Gallery and My
  Files on the phone, the sheet's list noted — r3 V15). RECORDED.
- P5 Pinch zoom ~~and double-tap~~ in the viewer and the viewfinder (no multi-touch injection on the AVD; the viewer's
  double-tap is E4's since r3 V22).
- P6 Performance with Samsung Camera's DCIM at thousands of items: ~~`dumpsys gfxinfo app.tileshell reset` /
  print during a scripted collection scroll, phase 01 P4's method and thresholds~~ (SUPERSEDED 2026-10-04 by r3 V15:
  `dumpsys gfxinfo` needs adb) the collection flung end to end by hand, `[photosapp] library: images=<n> …` read on
  Diagnostics, and the feel `record`ed; the frame-time thresholds are the AVD's (Edge cases, 3,000 fixtures, phase 01
  P4's thresholds). RECORDED.
- P7 ~~`dumpsys meminfo app.tileshell:camera` and `:video`~~ The probe page's PSS for `app.tileshell:camera`, `:video`
  and `:photosedit` (r3 V15) during capture / playback / an edit and after close (released, or the process gone).
- P8 The Camera and Videos checklist rows through One UI's permission dialogs (phase 01 P2's flow), and the two wizard
  steps on a phone with those grants revoked.
- P9 **Pro dial (T17-2):** set ISO 800, 1/500 s, white balance Cloudy, manual focus at the near stop → the JPEG's EXIF
  (~~`exiftool -ISO -ExposureTime -WhiteBalance -SubjectDistance` on the pulled file~~ ISO, ExposureTime, WhiteBalance and
  SubjectDistance on the probe page — r3 V15) reads back those values (ISO exact,
  exposure ± one stop as the sensor quantises, WB = Manual); the controls shown are the ones r3 D10's gates admit for the
  S25U's characteristics (probe page), and a control they do not admit is hidden with its reason line on Diagnostics.
- P10 **Panorama:** a sweep across a room → one JPEG whose width > `SENSOR_INFO_PIXEL_ARRAY_SIZE`'s width (~~EXIF
  `ImageWidth` vs `dumpsys media.camera`~~ both on the probe page — r3 V15), EXIF present, in DCIM/Camera; the stitch look
  judged in H14.
- P11 **Slow motion:** a 5-s take → ~~`ffprobe -show_streams` `r_frame_rate` ≥ 120 and the file plays in the shared player
  at normal speed (E11's pixel rule against a known scene change)~~ (SUPERSEDED 2026-10-04 by r3 D11, V15: a file with
  real-time timestamps plays in real time everywhere) on the probe page the video track's frame rate is 30, the duration
  is (fps ÷ 30) × 5 s ± 0.5 s with fps from the `[camera] slowmo <n> fps captured …` line, and there is no audio track;
  the take plays slowed in the shared player (seen).
- P12 **Living Images:** a still → ~~one file in DCIM/Camera whose bytes end with an MP4 (`tail -c 1M | grep -c ftyp` ≥ 1)
  and whose XMP (`exiftool -XMP-GCamera:MotionPhoto` or `strings | grep MotionPhoto`) reads 1 with a `Container:Directory`
  item length equal to the trailing MP4's size~~ (SUPERSEDED 2026-10-04 by r3 V14 / V15: the file checks are E7's, on the
  AVD) `[camera] saved <uri> …` on Diagnostics and the probe page's Motion Photo XMP reading 1; Photos shows the glyph and
  plays the clip on hold; Samsung Gallery shows it as a Motion Photo — RECORDED.
- P13 **Real streaming services** (the S25U with Jeremy signed in; P5: each is his own app answering an intent; the CI
  build the phone installs, with Jeremy's TMDB read token pasted into Movies & TV's settings — Q-17-1 (a)): for every
  installed service in the table, "Watch on <service>" from a title → ~~`dumpsys activity activities` shows that app resumed
  and a screencap records~~ (SUPERSEDED 2026-10-04 by r3 V15) that app comes to the front, the `[video] watch-on
  <service> … -> <intent>` line is read on Diagnostics, and Jeremy notes whether it landed on the TITLE, on its SEARCH
  results or on its HOME; a signed-out service shows
  its own sign-in and nothing of ours steps around it. RECORDED per service (BS-4's forms are re-verified here).
- P14 **Jeremy's own media server** (Jellyfin — Q-B; waits on Jeremy's home server coming back up with Jellyfin on it): E22's
  assertions that read on the phone (the `[video] server …` lines on Diagnostics, the pane row, playback; the credential
  store's check stays E22's — r3 V15) against the real server on his network, including a wrong password and the server
  switched off; a plain
  `http://` server on his LAN connects ~~under Q-D A / B and is refused with its line under C (Q-D: A)~~ (Q-D A, the only
  form built; SUPERSEDED 2026-10-04 by r3 V6). ~~under Q-B B / C,
  Plex's plex.tv PIN sign-in on the phone~~ SUPERSEDED 2026-09-23 by T17-14. RECORDED for the landing form, gated for
  connected / unauthorised / unreachable; the leak scan (C-32) over P14's pasted-back evidence with the issued token.
- P15 **The real catalogue** (TMDB — Q-A; network on; ~~a build made on Jeremy's PC, the only kind that carries the key —
  Q-A2: B~~ SUPERSEDED 2026-10-05 by Q-17-1 (a) — the CI build the phone installs): before any key is saved, Browse says
  film search needs a key, with the link to the setting, and Diagnostics holds `[video] catalogue: no TMDB key saved`;
  through that link Jeremy pastes his TMDB read token into Movies & TV's settings once; then search "Blade Runner" → > 0
  results and the first
  title page draws with artwork and `hub_attribution`; a failure is recorded with the HTTP status (`[video] catalogue …`
  on Diagnostics; a 401 shows the bad-key form, build task 12), never hidden; the leak scan (T17-13, C-32) over P15's
  pasted-back evidence (the Diagnostics copy; ~~and
  `adb logcat -d`~~ no adb on the phone — r3 V15) finds none of the `tmdb.*` values local.properties still holds (gated;
  with no such value there the scan has nothing to look for and is `record`ed as not run, never a pass). ~~The public CI
  build on the phone shows "Film search is off in this build" (the Q-A2 consequence, RECORDED)~~ SUPERSEDED 2026-10-05 by
  Q-17-1 (a) — the no-key step above. RECORDED
  (network-dependent by nature) apart from the leak scan.
- P16 **A 200-MP photo (r3 D8; added 2026-10-04):** a photo taken in Samsung Camera's 200-MP mode opens in Photos' viewer
  and zooms (the region decode), then auto-enhance and "Save a copy" in the editor → `[photosapp] edit … -> <uri>` in the
  `:photosedit` ring on Diagnostics, the copy's width × height equal to the original's (probe page), and the launcher's
  pid unchanged — read as the launcher ring on Diagnostics still holding a line written before the edit (the ring is
  in-memory per process, `diag/Diagnostics.kt:14-27`, so a restart empties it).

**NEEDS-HUMAN:** H1 *fidelity* — Photos matches r11/photos.md and r11/photos-pass2.md on the phone (Y1, Y2, Y7 — the
MEASURED values); H2
*fidelity* — Camera matches r11/camera.md and r11/camera-pass2.md where measured (the viewfinder, Y3; the pro dial, Y4; the
panorama UI, Y12; the bars; the settings page; ~~the rotated V-2017 viewfinder is H18's~~ — r3 D3); H3 *fidelity* — the
player, the My videos page and the ≡ pane match r11/movies-tv.md and r11/movies-tv-pass2.md (the hub's online screens are
H10–H12); H4 *accept* — motion approximations (Y6; ~~R11 measured none for these apps~~ what the pass-2 addenda leave
unmeasured: the viewer open / close duration, the chevron's expand / collapse, the dial's value change, the camera
launch, the slideshow step — r3 D3–D5); H5 *accept* —
capture image and video quality on the S25U (no metric); H6 *accept* — any Y1–Y5 / Y7 value R11 does not measure; H7
*accept* — the video app's label from the branding module (W10M's en-US "Movies & TV", en-GB "Films & TV"); H8 *accept* —
the shell-package routing rule (Music's face on the Music tile only; a video on no tile), re-judged on
this build with Photos and Camera seeded (the rule is built by phase 15's task 0, C-1); H9 *accept*
— pinch-zoom and double-tap feel (P5); H10 *accept* — the Browse page and the title page (Y8, P4 design) with the
attribution at its foot, and the TMDB key setting with its no-key and refused-key pages (Q-17-1 (a)); ~~the Browse pivot … under Q-A A / C~~ SUPERSEDED 2026-09-23 by T17-14 / T17-16; H11 *accept* — the
"Watch on" row set and its order when several services are installed (Y9), knowing that many titles will open the
service's search rather than the title (Wikidata knows only some services' title ids, T17-15); H10 and H11 are judged on
the CI build the phone installs, after Jeremy pastes his TMDB read token into Movies & TV's settings (Q-17-1 (a)); H12 *accept* — the
media-server sign-in page AND the Media server library and title rows (Y10, P4; T17-16 / #29); ~~H13 *accept* — the
editor's tool UI and each tool's look …~~ SUPERSEDED 2026-09-23 by T17-16: H13a ~~*accept*~~ *fidelity* (r3 D4 / V17) —
crop / rotate / auto-enhance (W10M's own inbox tools) in ~~the Y11 panel~~ Y11's bottom strip on real photos, against
r11/photos-pass2.md (LOW: order and presence); H13b *accept* — the four P4 additions (straighten, light and
colour, filters, red-eye; the matrices are the agent's picks) ~~and the video-trim screen (Y13), P4 designs~~ (P4 designs)
and the video-trim screen (Y13, LOW from the Nov-2015 captures — r3 D4); H14 *accept* —
~~the panorama UI and~~ stitch quality (P10; the panorama UI is MEASURED, Y12, and judged under H2 — r3 D3); H15 *accept* —
the pro dial's controls and ranges as the S25U exposes them
(Y4, P9); H16 *accept* — Living Images: the glyph, the hold-to-play and the 1-s clip length (P12); H17 *accept* — the Photos
version choice (V-2016+ collection, V-2015 elsewhere; T17-17); H18 *accept* — the Camera version choice (~~the V-2017
viewfinder rotated into portrait, a tagged approximation against K11,~~ the V-2016+ portrait viewfinder, measured in Y3
under H2, with the V-2015 pro dial; T17-17; reduced to the version choice 2026-10-04 by r3 D3); H19 *accept* —
the Movies & TV version choice (10586 geometry with the ~~2017~~ v3.6.1867 −10 / +30 skips and the ~~"•••" menu~~ Dec-2016
"•••" menu — r3 D5; T17-17); H20 *accept* — the
capture-intent accept / retake bar (Y14, P4).

## Edge cases
Indexed since 2026-10-04 (r3 V14, phase 16's EDGE form): `qa/phase-17/scripts/edge_index.tsv` maps EVERY bullet below to an
`edge_<ID>` sub-step of `qa/phase-17/scripts/edge.sh` (each from its own MARK with its own restore), another row, a P row
or a JVM test, and a bullet with no entry fails; the fixtures they need are task 16's (the 4K and two-audio-track videos,
`qa-steps.srt`, the DNG and HEIC stills, `catalogue_server.py`'s `/429` and `/401`).
- Thousands of photos: 3,000 fixtures generated with make_photos.py's method in a loop, pushed and scanned;
  `am start -W` of PhotosActivity reports TotalTime < 2000 ms; the collection scroll meets phase 01 P4's
  gfxinfo thresholds on the AVD; PhotosFeed's tile lines are unaffected.
- HEIC and RAW: a HEIC fixture (from the S25U; ffmpeg 4.4 cannot write one) decodes through the platform HEIF
  decoder or shows a placeholder, never a crash; a DNG shows its embedded preview or a placeholder; an edited HEIC is saved
  as a JPEG copy (T17-3) and the row says so in `[photosapp] edit … -> <uri>` (a `.jpg` name).
- A MediaStore row whose file is gone (`adb shell rm` without a scan): the thumbnail is a placeholder, the
  viewer shows the error state, and the next scan removes the row.
- Videos: a 4K fixture above the AVD decoder's capability → error state, no crash; a file with no audio track
  still takes audio focus; a file with two audio tracks plays the first; subtitles (`.srt` beside the file or an
  embedded text track) toggle.
- Storage full while recording: `lib.sh` `fill_volume 3000000` (C-27: phase 15's root route to `/data/media/0/fill.bin`,
  asserting `adb shell df /sdcard` free ≤ leave + 5 MB after the fill — a fill that did not take fails loudly; ~~`adb shell
  fallocate … /sdcard/fill.bin`~~ SUPERSEDED 2026-09-23 by C-27, the FUSE route was never checked), start recording (with
  RECORD_AUDIO revoked for the step, the preamble's rule — r3 V4) → the recorder stops with "storage full", the partial file is finalised (`is_pending` 0) and
  playable or removed, never left pending; restore with `unfill_volume`.
- Camera in use by another app: Open Camera in the foreground, then ours via `am start` → Android's camera
  service gives the foreground app the device; ours shows "camera in use" only while the other app is in front
  and re-opens on resume (`[camera] busy`, then `[camera] devices=1`); the reverse order evicts ours cleanly.
- Permission revoked mid-session: `pm revoke … CAMERA` with the viewfinder open → the platform restarts the
  process; the next open shows the grant page; `pm revoke … READ_MEDIA_IMAGES` with the viewer open → the same
  restart and the checklist state on return.
- Screen off or an incoming call mid-recording (`input keyevent KEYCODE_SLEEP`; `adb emu gsm call 5551234`; RECORD_AUDIO
  revoked for the step on the AVD — r3 V4):
  the recording stops and is finalised; the file plays (after the `KEYCODE_SLEEP`, `wake_device` and its `Awake` assert
  before the next tap — C-25). The same mid-panorama and mid-slow-motion on the phone (P): the
  partial capture is discarded with `[camera] mode <x>: cancelled (<reason>)`, no pending row.
- A `:camera`, `:video` or `:photosedit` (r3 D8) process killed by the system mid-write: on the next start any `is_pending=1` row of
  the shell's is cleaned (`content query --projection _id:is_pending` shows none).
- The picture-frame photo deleted in Photos: PhotosFeed logs `main photo unreadable` and the tile falls back
  (phase 01 item 4's rule).
- PHOTOS or CAMERA re-pointed to another app: the shell's apps stay in the app list and keep working; Tess's
  "take a photo" then opens that app (slot resolution, phase 03 edge case "slot app unassigned" still holds).
- APK updated (`adb install -r`) while a video plays: the player stops cleanly and `dumpsys media_session`
  shows no orphan session.
- A capture-intent caller cancelled or killed mid-capture: no orphan file (E9's negative); a caller that passes a
  `content://` URI it has no write grant for → RESULT_CANCELED and `[camera] refused output: no grant` (never a path write;
  the three-condition output guard, r3 D1 — E9's display_photo negative).
- A capture-intent caller that asks for the front camera on the AVD (`EXTRA_USE_FRONT_CAMERA`): the back camera answers with
  `[camera] devices=1 front=absent`, never an error.
- USB OTG drive holding a video (P): plays while attached; pulled mid-play → error state, no crash. The S25U
  has no SD slot.
- Catalogue (`qa-dummy-token` saved in the TMDB key setting first, as E20 enters it, and removed after — Q-17-1 (a)): a
  rate-limited source (429) → "The catalogue is busy, try again in a minute" and `[video] catalogue … : error
  429` with the cache shown; a title with no artwork → the poster placeholder; a query with no result → the empty line, not
  a blank grid; the token revoked on TMDB's side (401, the base at `catalogue_server.py`'s `/401`) → ~~"The catalogue isn't
  answering" with~~ (SUPERSEDED 2026-10-05 by Q-17-1 (a)) the bad-key form — Browse says the saved key was refused, with the
  link to the setting (build task 12) — with `[video] catalogue … : error
  401` and the cache shown (~~the build has a token, so not the no-key form; Q-A2: B~~ a key is saved, so not the no-key
  form), never a crash; **replace:** the link opens the setting, the key replaced there (`qa-dummy-token` typed over it)
  and the base restored → the next search answers with `bearer ok` in the fixture's log. ~~the key revoked (401,
  Q-A A / C) → the "no key" state~~ SUPERSEDED 2026-09-23 by T17-14.
- "Watch on": the service uninstalled between the page draw and the tap → "That app isn't installed any more" (the entry
  gone on the next draw); the deep link answered by the service's home rather than the title → recorded by P13 as the
  service's behaviour, the entry stays (the app IS installed); a service that answers no intent at all
  (`ActivityNotFoundException`) → the search URL is tried, then the line says `not installed`.
- Media server: the token invalid after a password change on the server → `unauthorised` and the sign-in page again with
  the host prefilled; the server on another subnet or the phone on mobile data → `unreachable` with the My videos page intact;
  the server removed from the app → the token is deleted from the store (`[video] server token cleared`) and the dynamic
  shortcut removed (E23).
- Offline preferred: with no network the hub never blocks the My videos page or the player; every network call has a 10-s
  timeout and runs off the main thread (`StrictMode` in the debug build catches a violation as a crash — the row's
  `AndroidRuntime` check covers it).
- Liveness (N-01): reboot and Device care leave the seeds, the checklist rows, the observers, the catalogue cache, the
  server token and the TMDB key (Q-17-1 (a)) intact (after `adb reboot` and the boot-completed poll the driver calls `wake_device` and asserts `Awake`
  before the first tap — C-25).

## QA evidence
_None yet._
