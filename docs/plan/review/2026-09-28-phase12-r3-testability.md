All phase-doc line references below are to `docs/plan/phase-12-setup-wizard.md`. Review performed read-only; no adb commands executed.

**V1 — BLOCKING — E12 rejects Jeremy’s selected inputs and names nonexistent files**

**Doc:** Lines 692–694 require `art/themes/{hal,soft,lumia,midnight}.png` to be “each ≥ 1024 px wide.” Build task 5 repeats the unsuffixed filenames at line 524. The dated selection at lines 60–70 instead names `hal-a`, `lumia-b`, `midnight-b`, `soft-c` and records the smaller dimensions and JPEG encoding.

**Evidence:** Read-only decoding of the actual files returned:

```text
art/themes/hal-a.png       PNG   941 × 1672
art/themes/lumia-b.png     PNG   941 × 1672
art/themes/midnight-b.png  PNG   941 × 1672
art/themes/soft-c.png      JPEG 1536 × 2752
```

None of the four unsuffixed E12 paths exists. `docs/plan/theme-art-brief.md:48` also retains the superseded “portrait PNG, at least 1024 px wide” requirement. E12 therefore fails before testing a correctly built preset.

**Smallest fix:** Replace E12’s input clause and task 5’s input path with:

> Inputs are exactly `hal-a.png`, `lumia-b.png`, `midnight-b.png` and `soft-c.png`, mapped to HAL, Lumia, Midnight and Soft respectively. Decode by file contents. Assert the first three decode as 941 × 1672 PNG and Soft as 1536 × 2752 JPEG; record source hashes. These selected inputs supersede the brief’s minimum-width/PNG requirement. Assert each generated WebP is 1872 × 4056. H8 also judges the shipped crops and enlargement sharpness, including Lumia’s softened edges.

Annotate the brief’s line 48 with the dated exception; do not reopen Jeremy’s selection.

**V2 — BLOCKING — E12/E13 can pass an APK that violates the every-build shipping ruling**

**Doc:** Lines 54–58 say every build, including public CI, bundles both img0 pictures. E12 nevertheless checks “whichever the build carries” at lines 694–695. E13 chooses success expectations from asset presence at lines 708–717, accepting no Hero and skipping the streaks interaction when its asset is absent.

**Evidence:** `.github/workflows/apk.yml:89–94` builds the selected variant; lines 101–106 select and copy the resulting APK for publication. Nothing in those steps verifies the prescribed preset assets. Task 5, lines 524–527, assigns a host script but does not require its execution by the normal Gradle/CI build. Under the current E13 branch, forgetting that integration produces an accepted no-picture build.

**Smallest fix:** Add to task 5:

> Normal debug and release assembly, including `apk.yml`, runs the picture-generation task and fails if any of the six prescribed output assets is missing. Both R12 inputs are required for these builds.

Replace the ordinary E12/E13 asset-presence branches with:

> Assert the APK contains exactly the six prescribed preset pictures: HAL, Soft, Lumia, Midnight, W10M Hero and W10M streaks. Both original variants must pass their UI, preference and pixel assertions on both surfaces. Missing either is FAIL.

Retain the no-picture behavior only in an explicitly identified fault-injection sub-row. Give that APK a separate evidence identity, verify the intended asset removal, and restore the normal APK afterward. The fault-injection result must not satisfy the shipping gate.

**V3 — BLOCKING — The acceptance fixtures still assume nine Setup grants after Clock added two**

**Doc:** Lines 553–558 omit the Clock appops from the E2 reset. E1(b), lines 574–582, omits their grants. E2, lines 585–589, requires exactly seventeen missing ids and “Step 1 of 18.” E3 omits their real Settings flows and concludes “all nine Setup rows” at line 616. Lines 730–733 acknowledge that both Clock steps are built and tested here.

**Evidence:** `app/src/main/kotlin/app/tileshell/onboarding/Checklist.kt:135–139` includes:

```text
ChecklistRow("full_screen_alarms", ...)
ChecklistRow("overlay", ...)
```

They occur after `keyboard_selected`, before the observation-only listener row. `docs/plan/qa/phase-03/scripts/provision.sh:52–53` already grants both:

```text
appops set app.tileshell USE_FULL_SCREEN_INTENT allow
appops set app.tileshell SYSTEM_ALERT_WINDOW allow
```

Consequently E1(a) and E1(b) establish different grant sets, and E2’s exact count depends on appops it never controls. T12-18’s extension rule has not been applied to today’s build.

**Smallest fix:** Update the current contract throughout:

> Core comprises twenty grant rows: eleven Setup rows and nine Tess rows. E2 explicitly sets `USE_FULL_SCREEN_INTENT` and `SYSTEM_ALERT_WINDOW` to `ignore` and asserts both checklist predicates are missing before launch. Its hand-written sequence inserts `setup:full_screen_alarms`, then `setup:overlay`, immediately after `setup:keyboard_selected`; with Home held, there are nineteen missing rows plus presets, so the first caption is “Step 1 of 20.”

Add both `allow` commands to E1(b). Add both actual Settings-page grant flows to E3, asserting their appop/checklist state and auto-advance, and change its final assertion to eleven Setup grants. Keep E14’s two dedicated Clock instances.

**V4 — BLOCKING — Missing-notification tests lose their diagnostics read path**

**Doc:** Lines 214–218 prescribe the notification listener’s service dump. Lines 535–537 require action-scoped ring assertions. E2 explicitly revokes notification access at lines 553–555; E2, E5, E6, E7 and E10 then require wizard diagnostics while that access can remain absent.

**Evidence:** `docs/plan/qa/phase-03/scripts/lib.sh:155–158` implements `diag()` solely as:

```text
dumpsys activity service $PKG/.feeds.TileNotificationListener
```

`ring_since` delegates its launcher read to that function at lines 183–188. The actual ring export is `TileNotificationListener.kt:91–94`, which calls `Diagnostics.dump(writer)`. `Diagnostics.kt:19–25` keeps entries only in process memory, and lines 10–12 explicitly say nothing is written to logcat.

Android’s service-dump mechanism invokes an existing service instance; it does not instantiate an unbound notification listener. After the clean reset with notification access disabled, the named endpoint is therefore not a dependable way to read the running launcher’s ring. Empty output can also make negative assertions vacuously pass.

**Smallest fix:** Add to build tasks 2 and 4:

> `StartActivity.dump(...)` exposes the existing launcher `Diagnostics.dump` through the already-existing activity, without a new exported component. Add a phase-12 ring helper using `dumpsys activity app.tileshell/.StartActivity`, preserving `wall=` slicing and saved evidence. Every wizard ring read first asserts the diagnostics header is present; empty/unavailable output is FAIL, including for absence checks. Use this endpoint throughout missing-grant rows.

Include the dump override in the phase’s adversarial review surface.

**V5 — BLOCKING — E3 skips the foreground-location upgrade its own fixture requires**

**Doc:** E2 revokes both coarse and fine location at lines 556–558. E3 grants Setup Location at lines 597–599, then describes `tess:background_location` as opening the “Allow all the time” page directly at lines 605–608.

**Evidence:** `onboarding/Checklist.kt:111–112` requests only:

```text
arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
```

`cortana/CortanaPermissionActivity.kt:60–69` explicitly branches on FINE: if held, it requests BACKGROUND; otherwise it removes BACKGROUND and requests the foreground permission first. `CortanaChecklist.kt:65–67` then reports FINE-only access as PARTIAL.

Thus E3’s own preceding actions do not establish the precondition for its asserted background page.

**Smallest fix:** Replace that E3 clause with:

> At `tess:background_location`, first assert FINE and BACKGROUND are absent. Tap Allow all the time and complete the foreground precise-location request. On return assert FINE granted, BACKGROUND absent, the same wizard step still present, and its `partial` line. Tap the action again, grant “Allow all the time” through the app’s location page, then assert BACKGROUND granted, the checklist row GRANTED and the wizard advanced.

Task 2 must explicitly own any request adaptation needed for that foreground upgrade on the target API; it must not bypass this leg with `pm grant`.

**V6 — BLOCKING — E11/E13’s wizard-side measurements have no runnable transition to Start or Tess**

**Doc:** E11(a), lines 662–663, reaches presets from the E2 state by “Not now” throughout. Lines 671–678 then require Start pixels after `layout_restore` and a Tess capture through `KEYCODE_ASSIST`. E13(a) inherits that setup at lines 699–707. Lines 367–369 require the pager to remain uncomposed while the wizard shows.

**Evidence:** E2 removes ASSISTANT and leaves grants missing. `CortanaService.kt:74–84` refuses to show the session when the role is absent and opens the role notice instead.

`docs/plan/qa/phase-02/scripts/layout.sh:21–26` force-stops the shell twice and presses Home. Under the specified persistence rule at lines 194–198, that restarts an unfinished skipped-through run at its first missing grant. It cannot reveal `start_page`. On the Settings-side pass, the same post-tap restart also permits a broken “apply only after restart” implementation to pass a test claiming live application.

**Smallest fix:** Replace the visual-measurement setup with:

> Seed the layout before the preset action, never between the action and its first visual assertions. For each wizard-side preset/variant trial, provision all grants without the finished marker, revoke Usage access, launch the wizard and choose Not now to reach presets. Assert ASSISTANT is held. Tap the preset, save its immediate preferences and ring slice, then tap Done and capture Start and Tess without force-stop, reinstall or preference injection. Start’s PID must remain unchanged. Recreate this fixture for the next wizard-side trial.
>
> For the Settings-side pass, seed once before the pass and return to the existing Start after each preset tap without restarting it. Perform restart persistence checks only after the live-application assertions.

Use `kb_end` after keyboard probes, and retain the specified final provisioning restore.

**V7 — BLOCKING — The gutter-pair rule is geometrically impossible on the target AVD**

**Doc:** Lines 543–547 require both exterior gutter pixels to be “≤ 12 px from T’s column.” E3, E11 and E13 rely on this rule.

**Evidence:** `docs/plan/qa/phase-02/baseline_layout.json:1` makes People a medium tile. `ui/tokens/StartGrid.kt:9–14,32` yields a 342.75-pixel-wide medium tile at 1080 pixels and three columns. No column inside that tile can be within twelve pixels of both exterior gutters.

The named helper does not implement the proposed rule either: `docs/plan/qa/phase-01/scripts/e13_pixels.py:13–18` measures a broad top-band median and one gutter near the tile’s vertical centre. Matching two distant gutters would also not establish the picture colour hidden beneath a nonuniform tile interior.

**Smallest fix:** Replace the gutter-pair rule and assign its implementation to task 4:

> Add a host pixel oracle that decodes the shipped WebP and independently maps it to Start’s measured viewport with centred Crop, centred 1.3× scaling and scroll zero. Validate that mapping against multiple visible gutter patches. At each selected glyph-free tile patch, use the oracle’s picture pixels at the same coordinates as B and assert `T = α·accent + (1−α)·B ±4`; use literal preset expectations from this spec, never the application’s computed alpha. Require usable patches and sufficient picture/accent separation; missing samples are FAIL. Preserve the opaque/missing-picture negative control.

The checked rendering geometry is `StartPage.kt:563–570`: `ContentScale.Crop`, `translationY = -scroll.value * 0.25f`, and scale 1.3. This replacement corrects T12-16’s applied measurement text, rather than reopening its requirement for independent picture evidence.

**V8 — BLOCKING — `persona.py` cannot perform the colour assertions assigned to it**

**Doc:** E11, lines 675–678, and E13, lines 707–708, require `persona.py` to find arbitrary preset lens hues and return ring colour within eight levels. No task assigns that helper extension.

**Evidence:** `docs/plan/qa/phase-03/scripts/persona.py:44` hardcodes `LENS = (0x8A, 0x10, 0x08)`. Lines 69–77 reject pixels unless `r > g > b`, excluding the proposed Cobalt, Seafoam and purple hues. Lines 198–211 expect a directory of `f_*.png` frames and at least ten detected frames, not the single screencap prescribed here. Its outputs at lines 229–277 are geometry and motion measurements, not ring RGB.

Additionally, a hue-only assertion cannot distinguish Midnight’s half-brightness red from the undimmed HAL red.

**Smallest fix:** Add to task 4:

> Build a separate still-image preset-colour checker for E11/E13. It accepts independently specified expected lens hue and ring RGB, measures named persona regions, reports sampled values and sample counts, and fails on missing regions. It does not import application colour functions or derive expectations from application preferences. Add negative controls for the wrong hue, wrong ring colour and undimmed Midnight lens. Midnight additionally compares corresponding lens-tone intensity against HAL’s ×0.5 expectation.

Replace the `persona.py` references with that checker and explicitly name the UI state/capture needed for each lens and ring sample. Preserve the existing phase-03 motion helper’s interface.

**V9 — BLOCKING — E12 demands a static-backdrop rebuild when the specified implementation must not build one**

**Doc:** Lines 696–698 require each picture-changing preset tap to produce `[fluent] static backdrop rebuilt for <uri>`. The original and Midnight presets set `transparency_effects=false` at lines 334 and 338.

**Evidence:** `ui/fluent/StaticBackdrop.kt:193–200` states that the layer exists only while acrylic is on and implements:

```text
if (!state.on) {
    StaticBackdrop.drop()
} else if (backgroundUri != null && size.width > 0 && size.height > 0) {
    StaticBackdrop.ensure(...)
}
```

The rebuild log is emitted only after a successful build at lines 90–96. While the wizard’s pager is uncomposed, the app-list backdrop is not composed either. E12 therefore demands an event absent from both legitimate situations.

**Smallest fix:** Replace the final E12 clause with:

> After each preset is applied, reach Start/app list through the valid E11 transition. With battery saver off, require a rebuild for picture presets whose `transparency_effects` is true, followed by the app-list acrylic form. For Midnight and both original variants, require the saved switch false, the app-list fallback form and no rebuild in the action-scoped slice; corroborate the fallback with pixels. Establish acrylic on before testing its transition off, rather than requiring a repeated state-change line. Default requires no picture/backdrop. Run phase 13’s deferred Midnight → Default E1 sub-row here.

**V10 — SHOULD-FIX — Custom’s fixture neither matches the built helper nor tests persisted-grant retention**

**Doc:** Lines 407–411 require preservation of the user’s persisted URI grant and release when no longer referenced. E11, lines 684–688, instead injects “phase 13’s checkerboard fixture’s `file://` URI” directly into preferences. Lines 802–803 cite this sub-row as covering the picture “and grant.”

**Evidence:** `docs/plan/qa/phase-13/scripts/p13.sh:9–13` documents the actual MediaStore route and the observed unreadable app-data route. `push_picture`, lines 28–35, returns `content://media/...`; `set_checker`, lines 39–44, writes that URI. It does not establish a picker-owned persisted grant.

The real background picker calls `takePersistableUriPermission` in `settings/StartThemePage.kt:48–51`. Direct preference injection exercises none of that behavior. A build that releases the user’s grant on every preset tap can still pass the specified checker test.

**Smallest fix:** Correct the fixture reference to the actual `push_picture` MediaStore URI, and add this distinct E11 sub-row:

> Choose fixture A through the real picture picker, verify its persisted read grant, and set a custom item. Tap HAL, force-stop/relaunch, restore Custom and verify A decodes and renders with the custom values restored. Verify A’s persisted grant remains while the snapshot references it. Choose fixture B, replace the snapshot by tapping a preset, and assert A’s grant is released when neither active state nor snapshot references A, while B’s is retained. Use fixtures unavailable through a broad media permission so that missing persisted access cannot be masked. Restore grants, fixtures and the normal preset afterward.

Task 4 owns the grant-inspection evidence helper; task 3 owns the lifetime behavior.

Round-1/round-2 items checked and found correctly applied, accounting for subsequent dated supersessions: **T12-3, T12-4, T12-5, T12-6, T12-8, T12-9, T12-11, T12-14, T12-15, T12-17; C-3, C-5, C-14, C-15, C-17, C-18, C-20, C-25, C-26, C-31.** C-34’s explicit why-table and E14 ownership additions were also checked; V3 covers the remaining acceptance-fixture mismatch.

**BLOCKING: 9 · SHOULD-FIX: 1 · NOTE: 0**
