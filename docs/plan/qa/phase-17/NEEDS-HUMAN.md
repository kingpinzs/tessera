# Phase 17 (Photos, Camera, Movies & TV) — Jeremy's sign-off checklist

**STATUS (2026-11-01): BUILT; TESTING DEFERRED BY YOUR WORD.** You ruled on 2026-10-30 "lets keep building and only do
red tests and wait tell everything is 100% complete and ready to ship then do the full hours of testing": every device
row, phone row and sign-off below waits for the ONE full testing pass after every phase is built. Phase 17's resume
built D1, D2, D3 and ledger L20-1 (each red test first, one adversarial review, one fix round) and merged them on
`phase-17` (d953a99cb). The gate's two reviewers were dropped by your Q-17-R1 "(a)". Earlier: the emulator rows passed
on the gate build `app-debug.apk` md5 95b543037345b851 (app code bb154e06), kept at `GATE-BUILD/app-debug-95b54303.apk`.

The phase goes `done` when every row below is signed off: reply with the ids that pass, and what you saw for any that
do not.

**The phone build does not exist yet.** CI builds only on a push to `main` (or a `v*` tag, or started by hand).
Nothing of phase 17 is pushed. The phone rows (P0–P16) and every row marked "on the phone" wait for your push and the
CI build on the repository's "latest" release page. The workflow file changed in this phase (it takes the release APK
by path and fails if that APK is debuggable); that change has never run.

Everything in the phone rows is done on the phone alone — nothing over a cable, adb or a PC. Where a row needs a
value, it is read on a page of the shell (Start settings > Diagnostics, or its probe page) and pasted back.

## Decisions that are yours (asked one at a time in chat; recorded here when answered)

| id | the question | your ruling, and what was done |
|---|---|---|
| D1 | "Open with" from other apps on a shared-library picture or video, and the Camera saving into a library entry the asking app made: refused as first built | **(a), 2026-10-31: built.** Admitted when the launch carries the grant flag and the shell holds the platform's grant for that exact item (`media/UriAccess.kt`, `launchGrant`). Its review found a gap no fix inside the app can close; **you accepted it, "(a)", 2026-11-01** — see "Accepted residuals". Record: `review/2026-10-31-phase17-d1-build.md` |
| D2 | Block screenshots of the TMDB key and media-server password pages; clear the clipboard after a paste | **(a), 2026-10-31: built** for Movies & TV and Music's sign-in (`net/CredentialScreen.kt`). Record: `review/2026-10-31-phase17-d2-build.md` |
| D3 | Move the viewer other apps open out of the launcher's main process | **(a), 2026-10-31: built** — it runs in `:photosedit`; see "Accepted residuals" for what that does and does not protect. Record: `review/2026-10-31-phase17-d3-build.md` |
| D4 | The Camera's go-between leg (X (contacts), `scripts/e9.sh:426`) needs a test-app change | **2026-10-31 "red tests only full suite test after every single phase is done": not built now** — the test-app change and the leg go to the final pass (below) |
| L20-1 | (ledger) `ServerRules.isPrivate` misread `0.0.0.0`, `::` and IPv6 forms | **(a), 2026-10-31: fixed**, plus ASCII-only digits for an address and a port. Record: `review/2026-10-31-phase17-l20-1.md` |

## Accepted residuals (yours to know; nothing to sign)

- **D1's gap (accepted 2026-11-01):** an app with no permissions can make the viewer show (read-only), or the player
  play, a library item it cannot read, by a start path that skips Android's grant check (`startNextMatchingActivity`),
  whenever the shell already holds a temporary grant for that exact item from elsewhere: another open viewer / player /
  camera screen of it, a picture in a posted notification (once notification access is on), an unsaved photo-picker
  result, a shortcut icon, another app's grant. It is shown inside that app's screen; nothing goes back to it unless
  you tap Share and pick it. Camera saves: the same, much narrower. Read from Android 16's source; not run on a device.
- **D3's limit:** `:photosedit` runs as the same app identity as the launcher. A hostile picture can now only crash the
  viewer's process, not Start; it is no boundary against code that takes over the decoder. Photos' own library and
  viewer still read every library picture in the launcher's process.
- **D2's limit:** your keyboard's own clipboard history (Gboard, Samsung Keyboard) keeps a pasted token; the shell
  cannot clear it. A keyboard commit of two or more characters while the clipboard holds text costs one clipboard read
  (it may raise Android's "pasted" notice once).
- **L20-1:** an IPv4-compatible address (`::10.0.0.1`) is trusted by its IPv4 part, though packets go to the reserved
  IPv6 address — the ruling's wording.

## Owed to the ONE final testing pass (nothing below runs before every phase is built)

- Phase 17's owed rows from before (Q-17-R1 (a)): triage E7 100/1 (leg F, 2× scene at 1.70 ×) and EDGE_FRAME_DELETED
  15/5; run EDGE_CAMERA's KILLWRITE, REVOKE, SCREENOFF, CALL, REPOINT; TRUST_PHOTOS' and TRUST_VIDEO's shared-identity
  legs; the liveness driver's nine-line check; `e18.py`'s join; the tables rewritten from the folders.
- Every row whose code D1, D2, D3 or L20-1 changed (the build records list the legs: D1 V1–V7, P1–P2, C1–C4 and the
  gap leg; D2's screenshot / Recents / paste legs in both apps; D3's nine; the sign-in ask for the L20-1 spellings).
- D3 moves the viewer's lines to the `:photosedit` ring: the drivers that read them from the launcher's ring must change
  first — phase 17's `scripts/trust_photos.sh`, `scripts/e4.sh`, `scripts/rows_photos.md`; phase 18's `scripts/e6.sh`,
  `TASK4-smoke/scripts/a1-view.sh`, `BUILD-NOTES.md`. Rows matching whole lines gain D1's `launch grant read: <word>`
  and ` launchGrant=<word>`.
- D4: the test-app change (qa-capture-fwd declares WRITE_CONTACTS and takes a URI extra) and the X (contacts) leg.
- Suspected, for that pass: Android's `HttpURLConnection` may throw `AssertionError` on an IPv4-mapped address, which
  `VideoCalls.guarded` (catches `Exception` only) would let crash the player's process.

## Readings of the spec that I made and you may overrule (each is in INDEX.md's Change Log)

| id | what the spec said | what the emulator showed, and what the row asserts |
|---|---|---|
| R1 | the player's picture is the fixture's colour within ± 8 per channel | the emulator's video output lifts dark values by 14 to 17; the rows assert "nearest of the fixture's ten colours, and within ± 20" |
| R2 | a 4K video is beyond the emulator and shows the error page | the emulator plays it; the row asserts it plays without a crash; the error page is proven by a truncated file, an empty file and a missing address |
| R3 | a go-between forwarding a capture request for the receiving app's own file is refused | it is accepted, correctly: the go-between already holds the right to write that file |
| R4 | a spoiled saved-server entry "shows Sign in again" on the Media server page | the entry is removed at the app's start, the page falls back to My videos, and the sign-in form is under Settings > Media server |
| R5 | the catalogue test server on port 8090 | that port is taken on this PC; the fixture uses 8091 |
| R6 | on Android 14 another app's request to view or play an item | refused unless the asking app shares its identity; Android 15 and later (your phone) use the platform's own answer. Since D1 (2026-10-31): a library item is also admitted, on every version, when the launch carries the grant flag and the shell holds the grant (with the accepted residual above) |

## Told, not asked

- Media3 was raised from 1.8.0 to 1.9.0 (the new camera library needs it).
- Not built in this phase: the player's subtitle flyout, a play button on the trim screen, Photos' Select / Favorite /
  settings page, the Camera settings rows "Press and hold camera button" and "Focus light".
- `r4probe-debug.apk` is still published by CI.
- The Jellyfin test image: none on this PC on 2026-10-21 (`docker images | grep -ic jellyfin` = 0) and none pulled by
  the resume; the final pass pulls it and removes it after.
- One emulator-only oddity left behind: Android remembers the shell's player as its last choice for opening an mp4,
  so its chooser opens in the "Open with Tessera" form. Clearing it would risk the default-home choice.

## Look and feel — accept or reject (emulator screenshots under `qa/phase-17/`, or on the phone)

| id | kind | what you are judging |
|---|---|---|
| H1 | fidelity | Photos against the W10M captures: collection, albums, viewer, on the phone |
| H2 | fidelity | Camera against the W10M captures where measured: the viewfinder, the pro dial, the panorama page, the bars, the settings page |
| H3 | fidelity | the player, the My videos page and the ≡ pane against the W10M captures |
| H4 | accept | the motions W10M footage did not measure: the viewer opening and closing, the chevron's expand and collapse, the dial's value change, the camera launch, the slideshow step |
| H5 | accept | photo and video quality from the shell's Camera on the S25 Ultra (on the phone) |
| H6 | accept | any size or position the W10M captures did not measure |
| H7 | accept | the video app's name: "Movies & TV" (en-US), "Films & TV" (en-GB) |
| H8 | accept | what the Music tile shows: Music's face only; a playing video appears on no tile |
| H9 | accept | pinch-zoom and double-tap feel in the viewer and the viewfinder (on the phone) |
| H10 | accept | the Browse page and the title page with the TMDB attribution at the foot, and the TMDB key setting with its no-key and refused-key pages (on the phone, after you paste your key) |
| H11 | accept | the "Watch on" rows and their order when several services are installed; many titles open the service's search, not the title (on the phone) |
| H12 | accept | the media-server sign-in page, the Media server library and the title rows |
| H13a | fidelity | the editor's crop, rotate and auto-enhance in the bottom strip, on real photos |
| H13b | accept | the four added editor tools (straighten, light and colour, filters, red-eye — the colour values are my picks) and the video-trim screen |
| H14 | accept | panorama stitch quality (on the phone) |
| H15 | accept | the pro dial's controls and ranges as the S25 Ultra offers them (on the phone) |
| H16 | accept | Living Images: the glyph, hold-to-play, the 1-second clip (on the phone) |
| H17 | accept | the Photos version choice: the final W10M release's collection, the 2015 form elsewhere |
| H18 | accept | the Camera version choice: the 2016+ portrait viewfinder with the 2015 pro dial |
| H19 | accept | the Movies & TV version choice: the 10586 layout with the later −10 / +30 skip buttons and "•••" menu |
| H20 | accept | the accept / retake bar another app sees when it asks the Camera for a photo |

## Phone rows — on the S25 Ultra only, after the CI build exists

| id | do this | pass when |
|---|---|---|
| P0 | right after installing the build, before anything else: tap the Photos tile and the Camera tile, ask Tess "take a photo"; open Start settings > Diagnostics BEFORE restarting and paste the two `assignSlotOnce slot:photos:v1 …` / `slot:camera:v1 …` lines; then Start settings > Tile apps > Camera > Samsung Camera; restart; look again after the next update | both tiles open the shell's apps and "take a photo" opens the shell's Camera; each line reads `-> assigned` or `-> assigned, replaced user's <app>`; after pointing Camera back, the tile and Tess open Samsung Camera, and still do after the restart and the next update |
| P1 | Camera: front camera, flash, HDR / Night, zoom across lenses, each mode (Photo, Video, Pro, Panorama, Slow motion, Living Images); a video with Microphone granted, and one with it revoked; long-press the Camera icon; share a screenshot of the viewfinder; paste the probe page's camera list | each mode saves (a `[camera] saved …` line on Diagnostics and the picture in Photos); the switch-camera button is top-right; the granted take has sound, the revoked one is silent with `[camera] video sound: off (no microphone permission)`; the long-press shows the shortcuts |
| P2 | Settings > Advanced features > Side key > double press | recorded: whether "Open app" offers the shell's Camera |
| P3 | open your own recordings in Photos and the player: HEIC stills, HEVC / HDR10+ / 4K60 videos, a Samsung Motion Photo; paste each one's Diagnostics line and probe-page facts | recorded: each shows or plays |
| P4 | in Samsung Gallery and My Files, "Open with" on a picture and on a video | recorded: which apps the sheet lists, whether the shell can be the default, and (D1, built) whether the shell SHOWS the item — paste the `[photosapp] launch grant read: …` / `[video] launch grant read: …` line; none means that app sends no grant flag and the item is refused |
| P5 | pinch in the viewer and in the viewfinder | it zooms; feel judged in H9 |
| P6 | fling the collection end to end with your full camera roll; paste the `[photosapp] library: …` line | recorded: the feel. This is the only check of the per-tile Living Image read on real JPEGs: the emulator's 3,000 test pictures are PNGs, which skip it |
| P7 | read the probe page's memory figure for `:camera`, `:video` and `:photosedit` during capture / playback / an edit, and after closing each | the figure drops, or the process is gone, after closing |
| P8 | with Camera and Videos access revoked in Android's settings: the two checklist rows, and the two setup-wizard steps | each row opens One UI's dialog and turns granted |
| P9 | Pro: ISO 800, 1/500 s, white balance Cloudy, manual focus at the near stop; take a photo; read its EXIF on the probe page | ISO exact, exposure within one stop, white balance Manual; a control the phone does not offer is hidden with its reason on Diagnostics |
| P10 | Panorama: sweep across a room; read the widths on the probe page | one JPEG wider than the sensor's width, in DCIM/Camera |
| P11 | Slow motion: a 5-second take; read the probe page | frame rate 30, duration (captured fps ÷ 30) × 5 s ± 0.5 s, no audio track; it plays slowed |
| P12 | Living Images: take a still; look in Photos and in Samsung Gallery | `[camera] saved …` on Diagnostics, the probe page's Motion Photo reading 1, Photos plays the clip on hold; recorded: whether Samsung Gallery shows it as a Motion Photo |
| P13 | with your TMDB key pasted: "Watch on <service>" from a title, for every installed service; paste each `[video] watch-on …` line | that app comes to the front; recorded per service: title, search or home |
| P14 | your own Jellyfin server, when it is back up: sign in, a wrong password, the server switched off; paste the `[video] server …` lines | connected / unauthorised / unreachable each show; no token appears in anything you paste back |
| P15 | before saving a key: open Browse; then paste your TMDB key in Movies & TV's settings; search "Blade Runner" | first: "film search needs a key" with its link and `[video] catalogue: no TMDB key saved`; then more than 0 results and a title page with artwork and the attribution; no key value appears in anything you paste back |
| P16 | open a 200-MP photo from Samsung Camera in Photos, zoom, auto-enhance, Save a copy | `[photosapp] edit … -> <uri>` on Diagnostics, the copy's size equals the original's, and the launcher did not restart |
| P17 | (added 2026-10-06 after the gate review) after P15, with the key saved: restart the phone, unlock it, open Movies & TV > settings > TMDB key; then Start settings > Diagnostics | the setting still says a key is saved; paste the `[app] user unlocked: launcher start-up` line if Diagnostics holds one, and the `[net] cleartext permitted …` lines after it (nine, each `false`). On the emulator the saved key survived a restart, but only with no screen lock set |
| P18 | (added 2026-10-06) with your media server signed in (P14): restart the phone, unlock it, open Movies & TV | the server is still signed in. The emulator carried only the TMDB key across a restart, not a signed-in server |
