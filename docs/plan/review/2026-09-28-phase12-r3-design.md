# Phase 12 — round 3 review, Reviewer 1 (design / correctness), 2026-09-28

Doc reviewed: `docs/plan/phase-12-setup-wizard.md` (813 lines, the 2026-09-28 Decisions in). Read-only; every code cite below is
against today's tree (HEAD d06e6e3e). Line numbers are the doc's unless a file is named. Checked on the host with PIL / `identify`:
the four picked pictures, R12's two img0 files, a full 1872 × 4056 WebP encode of all six (sizes in D10), a k-means of soft-c and
a hue read of midnight-b (both reproduce the 2026-09-28 accent methods as recorded), the C2PA chunk claim (l.68-69: true — `caBX`
in the three PNGs, APP11 in soft-c), and `grep WallpaperManager` over `app/src/main/kotlin` (none: no lock-screen side effect is
possible, l.48-51 holds).

## Findings

### D1 — BLOCKING — E12's host-side check names files that do not exist and a width three of the picked files fail
- Doc: l.692 "on the host, `art/themes/{hal,soft,lumia,midnight}.png` are each ≥ 1024 px wide (`identify`)"; build task 5 l.524
  "crops and scales `art/themes/<name>.png`".
- Evidence: `ls art/themes/` holds `hal-a.png` … `soft-c.png` and no `hal.png`; the doc's own Decisions (l.60-63, l.69-70) record
  the picked files as 941 px wide. PIL / `file`: `hal-a.png`, `lumia-b.png`, `midnight-b.png` = PNG 941 × 1672; `soft-c.png` =
  JPEG (JFIF) 1536 × 2752 under a .png name; `theme-art-brief.md:48` sets the 1024 floor. As written the row cannot pass on the
  inputs Jeremy picked.
- Fix: E12's first clause → "on the host, the four picked sources (Decisions 2026-09-28) read by `identify`: `art/themes/hal-a.png`,
  `lumia-b.png`, `midnight-b.png` = PNG 941 × 1672 and `soft-c.png` = JPEG 1536 × 2752 (JPEG data under a .png name; the brief's
  1024-px floor is waived for the three 941-px files by Jeremy's pick — recorded, not asserted), and the host script logs each
  file's scale factor (≈ 2.43× for the three, ≈ 1.47× for soft-c, ≈ 2.64× / ≈ 2.11× for the hero / streaks img0)". Build task 5:
  "crops and scales the four picked variants `art/themes/hal-a.png`, `lumia-b.png`, `midnight-b.png`, `soft-c.png` (soft-c read as
  JPEG)".

### D2 — BLOCKING — E12's `[fluent] static backdrop rebuilt for <uri>` cannot follow "each preset tap that changes the picture"
- Doc: l.696-698 "each preset tap that changes the picture is followed by `[fluent] static backdrop rebuilt for <uri>` naming that
  preset's URI, read from the ring slice after a MARK taken just before that tap".
- Evidence: the layer is built only from the app-list page's backdrop — `ui/fluent/StaticBackdrop.kt:195-200`
  (`if (!state.on) StaticBackdrop.drop() else if (backgroundUri != null && size.width > 0 …) StaticBackdrop.ensure(…)`), composed
  by `applist/AppListPage.kt:290`, i.e. inside the pager's page 1. (i) Under T12-6 (l.367-370) the pager is NOT composed while the
  wizard shows, so on surface (a) no `[fluent]` line can follow a tap. (ii) `Fluent.kt:117-126` turns acrylic off for
  `!transparencyEffects` (`reason=setting`), and the table sets `transparency_effects` false for the original (l.334) and Midnight
  (l.338): for those two the code calls `drop()`, never `ensure()`, so no `rebuilt` line exists (`StaticBackdrop.kt:93-96` is only
  reached on a build). A builder who forces a rebuild to satisfy the row undoes phase 13's "static layer only while acrylic is on"
  (INDEX row 13, commit 3448c92f).
- Fix: replace the clause with "with phase 13 present (T12-3), on surface (b) for HAL, Soft and Lumia — the presets that change the
  picture and leave `transparency_effects` true — after the tap, Home and a swipe to the app list (the layer is built when the
  app-list page composes with its size and only while acrylic is on, `ui/fluent/StaticBackdrop.kt:195-200`,
  `applist/AppListPage.kt:290`), the slice from the MARK before the tap holds `[fluent] static backdrop rebuilt for
  android.resource://app.tileshell/drawable/preset_<name> in <ms> ms`; for the original and Midnight the same steps yield no
  `rebuilt` line and `[fluent] acrylic=off reason=setting` (phase 13 E1's sub-row, T13-4); on surface (a) no `[fluent]` line is
  asserted (the pager is not composed under the wizard, T12-6)".

### D3 — BLOCKING — `tess_ring` and the E11 / E13 ring-colour reads measure a colour the built persona never draws
- Doc: table column l.331-338 "`tess_lens` / `tess_ring`" = "HAL lens (`Brand.LENS_*`) / the accent" (Default, HAL), "the lens on
  Cobalt's hue line / Cobalt" (original), "… / the accent" (Soft, Lumia, Midnight); l.355 "phase 03's persona reads `tess_lens` /
  `tess_ring`"; l.404 "`tess_ring` is the accent"; E11 l.676-678 "its ring colour = the preset's `tess_ring` ± 8 per channel
  (T12-19)"; E13 l.707-708 "the ring colour = Cobalt ± 8 (Q9 A)".
- Evidence: every ring the persona draws is lens-painted — `cortana/ui/Lens.kt:70-71` `tone(hal, accent, reveal) = lerp(hal,
  accent, reveal)`, `:100-120` `drawLensRing` = `LENS_GLOW` → `LENS_RIM` at reveal 0, `Persona.kt:223-232` (idle ring),
  `:235-243` (thinking ring), `:278-287`. The `accent` parameter is only reached at reveal = 1, the three-tap easter egg
  (`Lens.kt:26-40`, `TAPS_TO_REVEAL = 3`, `REVEAL_MS = 5000`), and it is already `LocalShellColors.current.accent`
  (`CortanaSessionRoot.kt:134,198`) — which every preset sets. So on the Default preset (`tess_lens` = HAL, `tess_ring` = Default
  Blue) no persona pixel is blue and E11's ring assertion fails; the same on all six, and E13's "ring colour = Cobalt". A builder
  who paints the idle ring in `tess_ring` to make the row pass changes phase 03's lens paint (Jeremy 2026-09-21, `Lens.kt:11-19`).
- Fix (smallest): strike `tess_ring`. The persona's ring IS the lens, and the one accent-coloured form (the reveal) already follows
  `accent`. Table column → "`tess_lens`" with the ring half of each cell removed; l.328 and l.355 → "`tess_lens`" only ("phase
  03's persona reads `tess_lens` from `ShellSettings`"); l.404 → "`tess_ring` is not a key: the reveal already follows `accent`
  (`Lens.kt:71`, `CortanaSessionRoot.kt:134`)"; build task 3 likewise; E11 l.676-678 → "`persona.py` colour search finds the lens
  on the preset's `tess_lens` hue (…); the ring-colour sample of T12-19 is struck — the persona's ring is lens-painted
  (`Lens.kt:100-120`)"; E13 l.707-708 → "`persona.py` finds the lens on Cobalt's hue line (Q9 A)". (If the lead keeps the key,
  it must be defined as "the colour the lens opens into on the three-tap reveal" and the read re-cut to: three taps inside
  1.5 s on the large persona, screencap 400 ms later, disc colour = `tess_ring` ± 8 — a different row.)

### D4 — SHOULD-FIX — the Setup rows' action lambdas live inside `ChecklistPage()`; the refactor the wizard needs is not named
- Doc: l.29 "steps derived from `ChecklistPage`'s row list"; l.176-177 "one accent button … firing the row's own `action`
  lambda"; l.171-172 "Phase 01's `ChecklistPage` is unchanged in behaviour (an ADD to the data class …)".
- Evidence: `onboarding/Checklist.kt:88-89` — `requestRole` / `requestPermissions` are `rememberLauncherForActivityResult` inside
  the composable; `:92-144` — `val rows = listOf(…)` is a local of `ChecklistPage()` closing over them. Nothing outside the
  composable can obtain the rows or their actions; `CortanaChecklist.rows(context)` (`CortanaChecklist.kt:33`) is already a plain
  function, `Checklist` (`:46-76`) is not.
- Fix: build task 1 gains "pull `ChecklistPage`'s `rows` into `Checklist.rows(context, requestRole: (Intent) -> Unit,
  requestPermissions: (Array<String>) -> Unit): List<ChecklistRow>` — same ids, order and actions; `ChecklistPage` calls it with
  its own launchers, the wizard with launchers registered in its host composable inside `StartActivity` (an ADD to phase 01's
  part, INDEX Change Log when built)". E9 adds phase 01 E14 (`disallow_listener` flips the checklist row) beside phase 01 E2, so
  the moved list is re-proved on the page that owns it.

### D5 — SHOULD-FIX — nothing on either row type lets the wizard tell a grant row from an observation row
- Doc: l.160-165 "the checklist rows whose state is MISSING or PARTIAL and which have a grant action … `samsung_badges`,
  `legacy_badges` … `listener` … are observations, never steps"; l.307-310 (Tess's five observation rows); l.30 "no third list".
- Evidence: `Checklist.kt:141-143` — `listener` has a non-empty action (`ACTION_NOTIFICATION_LISTENER_SETTINGS`), exactly like
  `notifications` (`:96-99`); only `samsung_badges` / `legacy_badges` have `{}` (`:118`, `:121-122`). `CortanaRow.permissions`
  (`CortanaChecklist.kt:28`) is empty for `assistant` (a step, `:37-41`) and for `exact_alarms`, `models`, `service`,
  `speech_process`, `person_triggers` (`:56-61`, `:80-117`). The planned `ChecklistRow.permissions` ADD (l.169-170) separates
  none of these; a builder must invent the split, and an id list inside the wizard is the "third list" l.30 forbids.
- Fix: l.169-172 → "`ChecklistRow` gains `permissions: List<String>` …; BOTH row types gain `grant: Boolean` — true for the eleven
  Setup rows and Tess's nine (the wizard's step filter), false for `samsung_badges`, `legacy_badges`, `listener`,
  `exact_alarms`, `models`, `service`, `speech_process`, `person_triggers` — and `partialIsDone`". Build task 1 lists `grant`;
  its JVM "namespacing" test also asserts the eight observation rows are never steps.

### D6 — SHOULD-FIX — "eighteen" grant rows is stale by two, and E1(b) / the E2 state leave those two rows to whatever `pm clear` does
- Doc: l.156, l.242, l.322, l.492 "eighteen"; E1(b) l.574-579 (the adb-alone grant list); the E2 state l.553-558; E2 l.585-589
  "exactly these seventeen ids"; l.548-551 "no row depends on what `pm clear` resets".
- Evidence: `Checklist.kt:135-140` — phase 15's `full_screen_alarms` and `overlay` are Setup rows with a grant action, so the
  grant rows number 11 + 9 = 20 (the doc's own why table has both, l.273-274). E1(b) grants "from adb alone" without
  `appops set app.tileshell USE_FULL_SCREEN_INTENT allow` / `SYSTEM_ALERT_WINDOW allow` (the two lines `provision.sh:52-53`
  runs). The E2 state revokes explicitly but never grants them, and E2's seventeen-id list holds only if `overlay`
  (`Settings.canDrawOverlays`, `Checklist.kt:138`, an app-op) survives `pm clear` — exactly the dependence l.548-551 rules out.
  (`full_screen_alarms` reads `canUseFullScreenIntent`, held by default with the manifest permission — phase 15 l.222 — so it is
  green either way.)
- Fix: "eighteen" → "twenty (the eleven Setup rows with a grant action, `Checklist.kt:93-140`, phase 15's `full_screen_alarms`
  and `overlay` included, and Tess's nine)" at l.156, 242, 322, 492. E1(b) adds "`appops set app.tileshell
  USE_FULL_SCREEN_INTENT allow`, `appops set app.tileshell SYSTEM_ALERT_WINDOW allow` (phase 15's two, `provision.sh:52-53`)".
  The E2 state adds, after `pm clear`: "`appops set app.tileshell USE_FULL_SCREEN_INTENT allow` and `… SYSTEM_ALERT_WINDOW
  allow`, so `setup:full_screen_alarms` and `setup:overlay` are held and the seventeen-id list stands whatever `pm clear`
  resets". E2's "(17 + the presets page = 18)" is then right as written.

### D7 — SHOULD-FIX — `tess_lens`'s stored values and the "on the accent's hue line" derivation are undefined
- Doc: table cells l.333-338 ("HAL lens (`Brand.LENS_*`)", "the lens on Cobalt's hue line", "the lens on the accent's hue line",
  "HAL lens dimmed (each tone × 0.5 on its hue line)"); l.348 the "Tess's look" row offers "HAL red or the accent" (two choices
  for three lenses); E11 l.667-670 reads `tess_lens` back from `start_theme.xml` as "that preset's row of the table" — there is
  no value to compare.
- Evidence: the four tones are literals (`brand/Brand.kt:45-48`) painted as fixed gradient stops (`Lens.kt:78-94`); no tint
  function exists, and the tones do not sit on one exact line (rim (138,16,8) → iris (216,24,16) → glow (255,45,28)), so "on the
  accent's hue line" has more than one reading. `persona.py:44-47,70-76` searches a red-specific line (`r > g > b`,
  `LENS = 8A1008`), so the driver needs the derived rim tone as an input (Reviewer 2's floor).
- Fix: after the table: "`tess_lens` is a string ∈ {`hal`, `accent`, `hal_dim`}: `hal` = `Brand.LENS_*` as built; `accent` =
  `LENS_RIM`, `LENS_IRIS` and `LENS_GLOW` each re-hued to the current accent's HSV hue with its own S and V kept (`LENS_CORE`
  unchanged — the specular), so the lens follows the accent (Cobalt in the original, Q9 A) and changes with it; `hal_dim` =
  `Brand.LENS_*` with R, G, B each × 0.5 (`LENS_CORE` unchanged). The 'Tess's look' row offers `hal` and `accent`; `hal_dim` is
  Midnight's." Table cells → `hal` (Default, HAL), `accent` (original, Soft, Lumia), `hal_dim` (Midnight). E11 compares the
  string, and its `persona.py` call passes the preset's rim tone.

### D8 — SHOULD-FIX — "any single item change" must be limited to the preset's keys; `ShellSettings.update()` rewrites every key
- Doc: l.340-342 "becomes `custom` on any single item change (phase 13's Transparency effects switch included)"; E11 l.680-683
  "every OTHER item key unchanged".
- Evidence: `prefs/ShellSettings.kt:76-93` — one `update()` path writes `columns`, `profiles`, `autosize`, `photos_slideshow`,
  `photo_frame` beside the theme keys; `StartThemePage.kt:107-108,114-124` write them from Settings, and phase 01's auto-size and
  item 4 write `autosize` / `photo_frame` from elsewhere. "Flip on any write" turns a preset Custom when a tile auto-sizes or a
  frame photo is picked; "flip on any theme key" is the intended rule, but the doc never lists the keys.
- Fix: l.340 → "becomes `custom` when a write changes any of the preset's keys — `accent`, `theme`, `background`,
  `transparency`, `press`, `transparency_effects`, `tess_lens`, `keyboard_palette` (`theme_preset_variant` excepted, T12-10) —
  and never on `columns`, `profiles`, `autosize`, `photos_slideshow` or `photo_frame`". E11's Custom sub-row adds one control:
  "toggle `theme_show_more_tiles` after a preset → `theme_preset` unchanged".

### D9 — SHOULD-FIX — the Custom snapshot has no named store
- Doc: l.407-412 "the full item set … is saved as the Custom snapshot"; E11 l.684-689 reads `start_theme.xml` and asserts "every
  item key equals the saved pre-HAL state".
- Evidence: `ShellSettings.kt:58` is the only theme prefs file and `update()` rewrites it whole; the doc does not say whether the
  snapshot's keys land in `start_theme.xml` (where E11's whole-file comparison would then see extra keys and every `update()`
  would have to carry them) or in their own file.
- Fix: l.409 → "… is saved as the Custom snapshot in its own `SharedPreferences` file `theme_custom` (the same key names, plus
  `saved_at`), never in `start_theme.xml`, so `start_theme.xml` holds only the live set; `pm clear` wipes it, `adb install -r`
  keeps it". Build task 3 names the file; E11's snapshot sub-row adds "`run-as app.tileshell ls shared_prefs` shows
  `theme_custom.xml` after the HAL tap and not before".

### D10 — SHOULD-FIX — the WebP encoding is unspecified, and the ≤ 8 MB / < 2.5 MB budget holds only for lossy WebP
- Doc: l.350-353 "bundled WebP … the pictures add ≤ 8 MB to the APK in total and none is ≥ 2.5 MB (E12)"; l.526-527; l.695-696;
  l.70 "the host script reads them as JPEG and writes PNG" (contradicts "bundled WebP"); no crop anchor is stated.
- Evidence (PIL, centre crop to 1872 × 4056, WebP `method=4`): lossless = hal 0.71, lumia 1.19, midnight 0.66, soft 4.74, hero
  2.50, streaks 2.68 MB — total 12.48 MB, three files at or over 2.5 MB; lossy q90 = 0.06 / 0.09 / 0.04 / 0.12 / 0.19 / 0.18 MB,
  total 0.68 MB (q85: 0.38 MB). A builder who picks lossless — the natural reading of "bundled picture" — fails E12 three times.
  The crop is height-fit: the 941-px files scale ≈ 2.43× and lose ≈ 411 px of width (18 %); where that width is trimmed changes
  what shows under the tiles.
- Fix: l.350-351 → "→ centre-cropped on the host to 1872 × 4056 (scaled to fill the height, the width trimmed equally on both
  sides) → LOSSY WebP, quality 90 (`cwebp -q 90` or PIL `quality=90`), no metadata (the re-encode drops the C2PA chunks)";
  l.70 "writes PNG" → "writes WebP"; build task 5 repeats "lossy, q 90, centre crop". E12's budget clause stands (measured
  total ≈ 0.7 MB).

### D11 — SHOULD-FIX — ruling (a) holds only if the WebPs reach the CI build; the doc says "read … at build time" without saying how
- Doc: l.54-59 "every build — the public CI APK … included — bundles R12's two img0 pictures … read by build task 5's host script
  from docs/plan/r12/"; l.304-305 "the host script (build task 5) reads them into the A10 branding module at build time";
  l.524-527 "read at build time wherever they are present".
- Evidence: `.github/workflows/apk.yml:41-45` runs `tools/fetch-speech.sh` by name and nothing else before `gradlew assemble`
  (`:97-102`); a `tools/` script that is not wired into apk.yml or Gradle never runs on the runner. Then the CI APK lacks every
  preset picture, E12 / E13 take the no-picture branch on the phone build, and the ruling is violated with no row failing on the
  dev box.
- Fix: build task 5 → "the script's outputs are COMMITTED at `app/src/main/res/drawable-nodpi/preset_{hal,soft,lumia,midnight,
  w10m_hero,w10m_streaks}.webp` (six files, ≈ 0.7 MB in all, D10); the script is run by hand and re-run when a source changes;
  apk.yml is unchanged (nothing to fetch)". The 2026-09-28 ruling line adds "shipped as committed WebP outputs of the host script,
  so the CI runner builds them in without running it". E12 adds "each committed WebP's sha256 equals the script's output on the
  same sources (the script run to a temp dir and compared)". "never assumed committed" (l.304, l.526) then refers to the SOURCES
  only, which are tracked (git ls-files: `docs/plan/r12/img0_*.jpg`, the four picked `art/themes/` files).

### D12 — SHOULD-FIX — Tess's runtime steps reach the app-info page BEFORE the wizard's "Open app info" relabel; E6's Tess half describes the Setup-row order
- Doc: l.186-189 ("the button relabels to 'Open app info' and starts `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` … the rule
  `CortanaPermissionActivity` … already follow[s]"); l.310-313 (T12-1 (a): walked through `CortanaPermissionActivity`'s existing
  paths); E6 l.634-635 "The same on `tess:microphone` (`[wizard] step tess:microphone: blocked (app info)`)".
- Evidence: `cortana/CortanaPermissionActivity.kt:27,33-39` — on a result holding a permission Android will no longer ask for, the
  activity itself opens the app-info page (`[cortana] permission blocked by Android (no prompt shown): …; opening app info`) and
  finishes; the wizard sees only the resume afterwards. So on `tess:microphone` the second deny lands the user in app info with
  no tap; the relabel appears on the return. A driver following E6's Setup-row script ("the button's text becomes 'Open app
  info' … tapping it resumes `com.android.settings`") asserts the wrong order for the Tess half.
- Fix: the Auto-advance line adds "For a `tess:` runtime step the blocked branch runs twice by construction:
  `CortanaPermissionActivity` opens the app-info page itself on the blocked result (`:33-38`) and, on the return, the wizard
  relabels the button as above." E6's last sentence → "On `tess:microphone` the second deny opens the app-info page at once
  (`CortanaPermissionActivity.kt:33-38`; the slice holds `[cortana] permission blocked by Android (no prompt shown):
  [android.permission.RECORD_AUDIO]; opening app info`); Back → the step shows 'Open app info' and `[wizard] step
  tess:microphone: blocked (app info)`; tapping it resumes app info again; Back returns to the step."

### D13 — NOTE — stale file:line cites and two stale facts (the code moved under phases 13 and 15)
- l.329, l.674 `start/StartPage.kt:413` → `:429` (`val tileAlpha = if (background != null) 1f - theme.transparency * 0.8f else
  1f`; the formula and every α in the table are right).
- l.505 `StartActivity.kt:187-190` → `:253-256` (`SecondaryTiles.pending` / `SecondaryPinPrompt`, drawn ungated).
- l.356 `AndroidManifest.xml:261` → `:323` (`.ime.KeyboardConfigProvider`).
- l.327 `prefs/ShellSettings.kt:72-82` → `:63-73` (read) / `:79-89` (write); l.364 "`prefs/ShellSettings.kt:17-24` has none" →
  "(`:53`, `transparencyEffects`, in since phase 13 was built)".
- l.315 `onboarding/Checklist.kt:105` → `:108`; l.308 `CortanaChecklist.kt:37-122` → `:37-118`; l.242 `provision.sh:35,50-51` →
  `:35,58-59` (`:50-51` are now phase 15's comment; its appops lines are `:52-53`).
- l.46 "the shell's only launcher icons are Start settings, Music and Weather" → six since phase 15 (Music, Clock, Weather,
  Settings, Recorder, Calculator — the manifest's LAUNCHER filters); the sentence's point (Start is reached through the Home
  role) stands.
- l.220 / l.658 `[motion] wizard_page t0=… settle=… frames=… maxGapMs=…` — the built line also carries `peak=` and `overshoot=`
  between (`ui/MotionTrace.kt:32-46`); say "fields read by key".

### D14 — NOTE — phase 04's "overlay" row (l.44, l.290) now collides with phase 15's `overlay` row
- Doc: l.44-45 "Phase 04's rows (accessibility, overlay, Wireless debugging pairing, helper): phase 04 ADDs them"; l.289-290
  "phase 04's accessibility, overlay and helper pairing".
- Evidence: `Checklist.kt:138-140` — `overlay` (`Settings.canDrawOverlays`, `ACTION_MANAGE_OVERLAY_PERMISSION`) exists since
  phase 15 (Q-E A; this doc l.73-76 and l.274). A second `overlay` row would duplicate the id and the tag `wizard_step:setup:overlay`.
- Fix: both lines → "phase 04's accessibility and helper pairing (its overlay need is met by phase 15's `overlay` row,
  `Checklist.kt:138`; phase 04 adds no second overlay row)".

### D15 — NOTE — T12-12's grant release needs its scope, and the trust surfaces for the adversarial review named
- Doc: l.409-411 "a newer snapshot replaces the older and releases a grant no longer referenced"; l.255-256 "No new exported
  component, no new permission, no network".
- Evidence: `StartThemePage.kt:50,58` take persistable grants for BOTH `background` and `photo_frame` from the same picker (one
  URI can back both); nothing today releases a grant (grep: no `releasePersistableUriPermission`). Releasing a grant that
  `photo_frame` still uses strands the frame photo; `releasePersistableUriPermission` on an `android.resource://` URI (a preset
  picture) throws, no grant having been taken. The manifest gains nothing (confirmed: every exported component is pre-existing).
- Fix: l.411 → "releases a `content://` grant the shell took (`takePersistableUriPermission`, `StartThemePage.kt:50`) that
  neither `background`, `photo_frame` nor the snapshot references any more; never on an `android.resource://` URI". l.255-256
  adds "Surfaces for the adversarial review: that grant release; the `background` key accepting `android.resource://` URIs
  (opened by `BackgroundDecoder.kt:39` through `openInputStream` — the shell's own resources); `qa/phase-12/fixtures/
  setup_wizard.xml` written by `run-as` (debug builds, QA only)."

### D16 — NOTE — `tess:background_location` "granted" also needs the device location toggle, which no step can turn on
- Doc: l.313 (`tess:background_location`, button "Allow all the time"), l.318-320 (its PARTIAL rule), E3 l.605-608.
- Evidence: `CortanaChecklist.kt:62-71` — GRANTED = `PlaceTriggers.granted && PlaceTriggers.locationEnabled`;
  `PlaceTriggers.kt:79-80` = `LocationManager.isLocationEnabled`. The permission page cannot enable location; with it off the row
  is MISSING (not PARTIAL: `:66` needs FINE held for PARTIAL, and then reads PARTIAL) and the step never advances on its own. On
  the provisioned AVD the row reads granted (phase 03 evidence: `cortana_check:background_location:granted`), so E1 / E3 stand;
  on the phone (P1) it is a real case the doc does not name.
- Fix: Edge cases gain "`tess:background_location` with both permissions held and device location off (`adb shell cmd location
  set-location-enabled false`): the row is MISSING (`CortanaChecklist.kt:65`), the step stays after 'Allow all the time', 'Not
  now' advances (`[wizard] step tess:background_location: not now`); restore `… true`; P1 records the phone's location state".

## Round-1 / round-2 items checked and found correctly applied
T12-1 (a)–(d) (rows, `partialIsDone`, namespacing, order; the observation split only lacks its flag, D5) · T12-2 (table, live
apply, pictures, ADDs, tags, diagnostics, E11–E13, H6–H9, edges; gaps in D7 / D8 / D10) · T12-3 (depends-on `[01, 03, 05, 10,
13]`; INDEX Build order line l.45 has 11 → 13 → 12 → 14) · T12-4 (why table, `wizard_why`, E2 / E3 / E14) · T12-5 (E14
template) · T12-6 (pager not composed; E2's "no `start_page`") · T12-7 (Brand strings; no-picture form and its line) · T12-8
(z-order line) · T12-9 (re-cut to Q7–Q9) · T12-10 (one id + `theme_preset_variant`, chips, lines, E13) · T12-11 (HAL Red; RGB
distance to `LENS_IRIS` re-computed 25.8) · T12-12 (snapshot; store and release scope in D9 / D15) · T12-13 (both gates recorded
met; the R12 ruling in Decisions) · T12-14 (Start-visible gate in task 2; E8 pin-band sub-row) · T12-15 (a) (Change Log line in
task 3), (b) (img5 reason) · T12-16 (gutter-pair rule in E3 / E11 / E13) · T12-17 (action-failed line + fallback + JVM test) ·
T12-18 (E2 extension) · T12-19 (keyboard numbers: DARK (48,48,48) = `KeyboardView.kt:62`, LIGHT #E6E6E6 = `Palette.kt:30`; the
ring sample falls with D3) · C-3 · C-4 (a)–(e) (phase 15's lines are in `provision.sh:52-53`; E14's phase-15 instances run here)
· C-5 / C-31 · C-15 (one mechanism; precedence; E1(a); E14 three parts; E11 control) · C-17 (`BarMetrics.STATUS_EPX`, l.646) ·
C-18 · C-20 · C-25 · C-26 · C-34. The 2026-09-28 accent picks reproduce as recorded: soft-c's k = 4 clusters read (245,221,235)
/ (227,218,240) / (210,229,248), each RGB-nearest to Purple Shadow; midnight-b's non-black clusters sit at 225–249° and Purple
Shadow Dark is the saturated swatch at 241°, RGB-nearest being Storm; Lumia's Seafoam is 46.6 from #1BA1E2 (Cool Blue Bright
47.3). The transparency → α column matches `StartPage.kt:429` for all six rows.

BLOCKING: 3 · SHOULD-FIX: 9 · NOTE: 4
