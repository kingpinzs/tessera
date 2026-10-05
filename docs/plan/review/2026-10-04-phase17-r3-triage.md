# Phase 17 — round 3 triage (the last round; 2026-10-04)

Reviews: `2026-10-04-phase17-r3-design.md` (Reviewer 1, opus: BLOCKING 2 · SHOULD-FIX 13 · NOTE 1) and
`2026-10-04-phase17-r3-testability.md` (Reviewer 2, opus: BLOCKING 8 · SHOULD-FIX 13 · NOTE 1). The codex run that started
before the owner ruled codex out is not used. Facts the lead re-checked before triage: `app/build.gradle.kts:27`
(`versionCode` = `TESSERA_VERSION_CODE` or 1) and `.github/workflows/apk.yml` (CI sets it to the run number) for D2;
`tiles/LayoutStore.kt:125-133` (`takeOver`, Calendar and People only) for D15; `r11/movies-tv-pass2.md:88-89` for D5's
menu and letterbox; Android 11's behaviour change for implicit capture intents (only preinstalled system cameras answer
an implicit `IMAGE_CAPTURE` / `VIDEO_CAPTURE` from a caller targeting API 30+) for V3 (a).

Classes: **Q** = a question for Jeremy (a ruling is contradicted or impossible); **A** = an agent fix, applied to the doc
as written in the review unless the action says otherwise; **A after Q** = applied once the named question is answered.

## Questions for Jeremy (asked one at a time, in this order)

| Q | From | What it decides |
|---|---|---|
| Q-17-1 | D2, V15 | How film search reaches the phone. Q-A2 B keeps the TMDB key out of the public CI build, but the phone installs only CI builds: a PC build is versionCode 1, which the phone refuses over a CI build, and there is no route onto the phone without a cable. As written, film search can never work on his phone. |
| Q-17-2 | V3 (a), D1 | What the capture-intent answer is for. Q5 A says the shell's Camera is one of the camera choices other apps get; since Android 11 an implicit capture request from a modern app reaches only the phone's preinstalled camera, so almost no app can reach the shell's. It is also the riskiest surface in the phase (D1). |
| Q-17-3 | D15 | Whether the shell's Photos and Camera take their slots once over a hand pick, as Calendar and People did (Q-16-1, his "(A)"). As written they do not, so on his phone the tiles and "take a photo" stay on Samsung's apps. |

## Agent fixes

| Finding(s) | Sev | Class | Action |
|---|---|---|---|
| D1 | BLOCKING | A after Q-17-2 | If the capture answer stays in any form: the three-condition output guard, the no-output contract (thumbnail in `data` for images; DCIM row for video), the refusal line, the display_photo negative in E9. |
| V3 (b)(c)(d) | BLOCKING | A after Q-17-2 | qa-capture uses `setPackage("app.tileshell")`; its file:// leg relaxes its own VmPolicy; it logs `exists=/size=` for its own output; the resolver clause becomes a recorded `query-activities` fact; a GPS control capture with `geo fix`. |
| D2, V15 (CI half) | BLOCKING | A after Q-17-1 | L91-92's consequence corrected (versionCode); P13, P15, H10, H11 name the build they run on per the answer. |
| V15 (phone half) | SHOULD-FIX | A | Every P row is done on the phone alone: values read on the shell's own pages or Diagnostics and pasted back; file analysis (EXIF, ffprobe, sizes) moves to AVD rows, or to the phone through an on-phone probe page only where the AVD cannot (front camera, 200-MP, real sensors). No adb, exiftool or ffprobe in any P row. |
| D3, V16 | SHOULD-FIX | A | Camera to `r11/camera-pass2.md` (its §4 governs): white shutter with #2B2B2B ring; vertical capsule on the right edge (photo: flash, HDR, timer, chevron; video: light, slow motion, chevron; panorama none); settings bottom-right; switch top-right; roll bottom-left; preview centred on the full screen; mode discs cycle; grid and Living Images on the settings page; HDR via CameraX Extensions with its `unavailable` line; mode switch a ≈300-ms slide; capture feedback black ≈167 ms; dial-open sweep ≈333 ms; timer 2 s / 5 s / off (E7 uses 2 s, Time lapse off, 2000–3500 ms, one new row). Bounds ± 33 ms (30-fps sources). Y3 MEASURED (MEDIUM) under H2; H18 reduced to the version choice; Y12's band 0.35·W centred; H14 stitch quality only; H4 keeps the chevron, dial value change and launch. The camera-switch geometry is a phone row (no front camera on the AVD). |
| D4, V17 | SHOULD-FIX | A | Photos to `r11/photos-pass2.md`: under T17-17's rule the viewer, editor and trim take their V-2016+ forms — viewer bar Share · Edit · Delete · More at 218 / 150 / 82 / 24 on black, header #171717; editor a 48-epx bottom strip with the Edit sheet at the screen bottom; trim a top bar, two white handles, range times at the track ends, no centred readout; viewer open / close expands from and shrinks to its thumbnail (250 ms stays an approximation, H4); swipe gap 20 epx, fling ≤ 234 ms. LOW values are checked for order and presence only. H13a becomes fidelity; H1, H13b, H17 stay. |
| D5, V18 | SHOULD-FIX | A | Movies & TV to `r11/movies-tv-pass2.md`: right label = remaining time, locale format; thumb Ø 22, stroke 2, centre 1.2 below the track; transport CC 36 (only with a text track; the row re-centres without it), back-10 W/2 − 48, play W/2, forward-30 W/2 + 48, full screen W − 84, "•••" W − 36; "•••" menu Cast / Zoom to fill / Repeat / Autoplay; default letterbox; posters 112 × 160 on a 120 pitch, one horizontal strip; pane ≈78 % at 100 ms, settled 167–200 ms, close 67–100 ms; library → player a cut ≤ 33 ms; controls hold ≈3.2 s, fade 367–400 ms. Y5's played colour MEASURED. H3, H19, H4 stay for what is still unmeasured. |
| D6 | SHOULD-FIX | A | Task 1: the three apps `singleTask` with `onNewIntent` and their own affinity (phase 15 / 16 form); helpers `.camera.CaptureActivity` (only if Q-17-2 keeps it), `.video.PlayerActivity`, `.photos.ViewerActivity`, standard launch mode, not catalog entries (L14-2); per-activity shortcut files; only `PhotosActivity` calls `startFeeds`. E3 / E11 / E13 components; E3's Back-on-Start line. |
| D7 | SHOULD-FIX | A | New Decision: one MediaStore write layer `media/MediaWrites` behind `MediaStorePort` with a recording fake, JVM tests (no op opens an existing row except a granted capture URI; pending → written → published; a failed write abandons its own row; cleanup only rows the shell owns), read-back of every publish, copy placement (original folder when allowed, else Pictures/ or Movies/, said in the line), output formats (JPEG q95 or PNG for PNG, upright with Orientation 1, dates copied; trims MP4 H.264 / AAC keeping rotation and date). |
| D8 | SHOULD-FIX | A | Screen-size decode plus region decode in the viewer; editor on a ≤ 4096-px preview, saved copy rendered tile by tile; editor and trim in `.photos.EditActivity` in `:photosedit`; a phone row for a 200-MP edit with the launcher's pid unchanged. |
| D9 | SHOULD-FIX | A | "Set as Start background" copies a screen-size image to `files/backgrounds/<id>.jpg`; its line; E5's revoke check. |
| D10 | SHOULD-FIX | A | Dial gates: EV by `CONTROL_AE_COMPENSATION_RANGE`; ISO and shutter by AE OFF + MANUAL_SENSOR (the other held at the last AE result; EV off while either is manual); WB presets by `CONTROL_AWB_AVAILABLE_MODES`. E7 derives its expected list from the same keys. |
| D11 | SHOULD-FIX | A | Slow motion: captured at the session's high rate, encoded at 30 fps so every player shows it slowed; no audio. P11 rewritten to that (read on the phone per V15). |
| D12, V4 | BLOCKING | A | Video records sound only while RECORD_AUDIO is held, else silent with its line and a hint naming the Microphone row. AVD rows revoke RECORD_AUDIO for the row and grant it back; assert one video stream, no audio stream; the sound itself is a phone row. E10 types "take a photo" (`type_request`) over the keyguard set up as e10.sh does. No `audio.sh`, no `pactl`, no microphone in any AVD row. |
| D13 | SHOULD-FIX | A | `openTitle` sets the service's package on the title and search links; ActivityNotFoundException → search → `not installed`. |
| D14 | SHOULD-FIX | A after Q-17-1 | `buildConfig = true` for the `BuildConfig.DEBUG` gates whatever the answer; the TMDB field and its Gradle-property precedence only if the key stays a build input. |
| D15 | SHOULD-FIX | A after Q-17-3 | Task 3 and E1 per the answer; a phone row for the PHOTOS / CAMERA slots after the update, in phase 16 P0's form. |
| D16 | NOTE | A | The six stale or loose lines as given; the pod-bay line (video never shows in Now playing) and E11's `id=video -> none`. The frontmatter's "waits on Q-D" goes (Q-D was answered A on 2026-09-23). |
| V1 | BLOCKING | A | `APP_UID` from `cmd package list packages -U`, exact-match, `\r` stripped, numeric and ≥ 10000. |
| V2 | BLOCKING | A | `egress_guard_on` revokes the location permissions first and the row asserts the weather refresh ended without a request; `egress_guard_off` grants them back. |
| V5 | BLOCKING | A | E11 asserts the routing line while the video plays, from a MARK before play, plus `absent_in "id=video -> cmp:"`; task 7 states the Media3 session id `video`. |
| V6 | BLOCKING | A | Q-D A is the only form built: strike every B / C branch in E13, E18, E22, P14; `cleartext refused` or 0 packets to 10.0.2.3 fails the row. |
| V7 | BLOCKING | A | The preamble as given: `p17.sh` (task 16) with `absent_in`, `gdump`, `top_activity`, a `c6` that saves every ring in RINGS; every Home is `c6` + `ensure_start`; every absence `absent_in`; every no-node check proves its page first; E24 asserts `-> already run` for all six markers. |
| V8 | BLOCKING | A | E22's token from `GET /Devices` (fixture admin's token; the item whose AppName is the shell's client), asserted non-empty before the leak scan. |
| V9 | SHOULD-FIX | A | `media_up` / `media_down` with a census and restore check; deterministic mtimes; the editor fixtures in `make_photos.py`; `make_videos.sh`'s ten named colours, colour k on [k, k+1) s, 0-based, `-g 25`; E11 at 3.5 s; E6b's first frame colour 2. |
| V10 | SHOULD-FIX | A | qa-capture receives ACTION_SEND and logs action, URI and stream md5; E5 asserts them. |
| V11 | SHOULD-FIX | A | E3's partial-access leg through Android's own picker: two named photos selected, exactly those shown. |
| V12 | SHOULD-FIX | A | The editor's matrices written into Decisions before task 5; `edit_expect.py` reads them from the doc; each tool's copy differs from the original by ≥ 16 on a channel; Save re-reads the original at full resolution; the `rm` after the tool and before Save. |
| V13 | SHOULD-FIX | A | E18 in phase 16's form: `producers.tsv` and `notrun.tsv`, each not-run pattern with its reason and its JVM test or P row. |
| V14 | SHOULD-FIX | A | `edge_index.tsv` mapping every edge bullet to a driver or a P row; `/429` and `/401` on the catalogue stand-in; the missing fixtures (4K, two audio tracks, `.srt`, DNG, HEIC); an E7 sub-row that captures one Living Image on the AVD and checks its file. |
| V19 | SHOULD-FIX | A | E17 also asserts ≥ 1 `lib/arm64-v8a/libopencv*.so` and none under `lib/x86_64`. |
| V20 | SHOULD-FIX | A | The Photos row stays READ_MEDIA_IMAGES / USER_SELECTED; READ_MEDIA_VIDEO is the Videos row alone. |
| V21 | SHOULD-FIX | A | E20 counts fixture-log lines from a host offset and ends with a control request that must appear. |
| V22 | NOTE | A | As given. |

Totals: 3 questions; 30 agent-fix rows (8 of them waiting on an answer); 0 rejected. Every BLOCKING finding is either an
agent fix or part of a question.
