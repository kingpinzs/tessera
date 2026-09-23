---
phase: 13
slug: fluent-materials
status: DRAFT
depends-on: [01, 02, 03, 10, 11]   # the in-app surfaces it dresses exist (app list, H21 band, Music menus, Tess's pane and menu, the burst); it lands BEFORE 04, which ADDs the cross-window source
---

# Phase 13 — Fluent materials: acrylic and light on the transient surfaces

## Goal
One permanent in-app materials engine draws Fluent acrylic — a blurred copy of what is BEHIND a surface, tinted, with a
2 % noise — on the shell's transient surfaces (the app-list backdrop, the app-list and Music hold menus, Tess's ≡ pane and
reminder menu, phase 11's burst backdrop), and a Reveal light on press for the items on those surfaces. Blur is a device
and power state: where the shell decides acrylic is off (the Transparency effects setting, battery saver, a low-RAM device)
every surface draws the MEASURED solid W10M fill it draws today, so there is exactly one form of each surface and its
fallback is the original, not a v1 (Rule 16). Every measured colour and motion value on the FINAL surfaces keeps its
number in its own row's setup. Phase 04 builds its action center and volume panel on this engine and ADDs the one source
this phase cannot have — cross-window blur-behind for overlay windows — gated by R4's blur probe on the S25 Ultra. Fluent
is not a W10M original: every acrylic value is an approximation from Microsoft's Fluent documentation, judged in
NEEDS-HUMAN "accept" rows.

## Scope
**In:** the material (tint colour, tint opacity, Gaussian blur radius, noise) and its one fallback rule; two in-app backdrop
sources — a pre-blurred static copy of the Start background image for pager pages (the app list; phase 14's pod bay when
built) and a live per-frame layer for surfaces over live content (menus, the pane, the burst); the on / off rule
(setting, battery saver, low-RAM) with its diagnostics line; the "Transparency effects" toggle in Settings > Start + theme
(per interview Q2); the surface table below applied to every existing transient surface, each keeping its measured fill
in its measured setup; Reveal light on press for items on transient surfaces (per Q3); the app-list backdrop's un-blurred
W10M form as its fallback (R3 A18); test tags; drivers under `qa/phase-13/scripts/` with `lib.sh` symlinked.
**Out (explicitly):** cross-window blur-behind on overlay windows (`WindowManager.LayoutParams.setBlurBehindRadius` /
`FLAG_BLUR_BEHIND`, gated by `WindowManager.isCrossWindowBlurEnabled()`): phase 04 ADDs it as a third backdrop source using
THIS engine's material, table and fallback, after R4's blur probe (INDEX R4 row); its E4(a) / E5 colour rows are
re-specified from this doc's table before its interview (R10 triage). Fluent depth, shadows and connected animations (not
ruled). Start tiles (the Q6 press setting stands; no light on tiles, R10 design finding 4). Non-transient pages: Settings,
Weather, the Music collection, Tess's black page and her text box ((46–48) fill, a fixed bar). Reveal on pointer hover
(no pointer). Any blur library (haze or other: a new dependency needing an R2-style check; the platform has the API). A
second material for the light theme (the same material with the light fills). Hooks for later phases.

## Decisions
- 2026-09-22 R10-Q1 Design direction (Jeremy: "B"), quoted from PLAN.md: **B. W10M plus Fluent materials.** "Square tiles,
  Metro type and every measured geometry stay; acrylic and light effects arrive on the TRANSIENT surfaces — action center,
  volume panel, menus, the app-list backdrop. Fluent is what desktop Windows 10 received in 2017, right after W10M's last
  build. Mechanism note from R10: real acrylic is a blurred BACKDROP (a blurred copy of the layer behind, in-app;
  cross-window blur-behind for overlay windows, API 31+ and device-dependent) — the secondary spec's modifier blurred the
  element itself and is not used."
- 2026-09-22 (agent, R10 plan review; PLAN.md "Agent calls", triage): **blur is a device capability and its fallback is the
  MEASURED solid W10M fill** — one permanent form; R4's phone list gains the cross-window blur probe (`getprop
  ro.surface_flinger.supports_background_blur`, `dumpsys window | grep mBlurEnabled`, both again under power saving, and a
  `FLAG_BLUR_BEHIND` probe overlay; INDEX R4 row); this engine lands BEFORE phase 04 so 04 builds on it.
- 2026-09-22 (agent, R10 item 3 / design finding 19): **the secondary spec's `graphicsLayer` render-effect code is wrong**
  — it blurs the element's own content — and is not used. No blur, RenderEffect or cross-window call exists in
  `app/src/main/kotlin` today (checked 2026-09-22).
- 2026-09-22 (agent): **Two in-app backdrop sources, one material.** (a) Static: the Start background image
  (`StartTheme.backgroundUri`, decoded as `StartPage.rememberBackground` decodes it) is blurred once per image / theme
  change, off the main thread, at screen resolution, and cached; used where what lies behind the surface IS the wallpaper —
  the app-list pager page (R3 A18: "app-list background shows the Start wallpaper through it") and, when built, phase 14's
  pod bay page. (b) Live: the content under a surface is recorded each frame into a `rememberGraphicsLayer()` layer
  (`androidx.compose.ui.graphics.layer.GraphicsLayer`, `record` / `drawLayer`, Compose BOM 2026.06.01) and the surface draws
  that layer clipped to its own bounds with `renderEffect = BlurEffect(r, r, TileMode.Clamp)` (platform `RenderEffect`,
  API 31+; minSdk 34), then the tint and noise over it; used for surfaces over live pages — the app-list hold menu
  (`applist/AppListMenu.kt` `PinToStartMenu`), the Music hold menus (`music/MusicCollectionPage.kt` `MenuState`), Tess's
  ≡ pane (`cortana/ui/CortanaNavPane.kt`) and reminder long-press menu (`cortana/ui/RemindersPage.kt`, `CortanaUi.MENU_FILL`),
  and phase 11's burst backdrop if its doc names one (the satellites are tiles and stay opaque). The live source records
  only while its surface shows. Its cost is one recorded layer per frame at the display size while a menu is open; E9
  records it on the AVD and P2 bounds it on the phone. Over flipping tiles (the burst) the screen never idles: RV13's
  `UiDevice` route applies to dumps, and the burst's own diagnostics line carries its bounds (R10 testability 31).
- 2026-09-22 (agent): **Cross-window is phase 04's ADD**, recorded here so both build one material: an overlay window
  cannot read the pixels behind it (Android limits, PLAN.md), so its backdrop is `setBlurBehindRadius` + `FLAG_BLUR_BEHIND`
  where `isCrossWindowBlurEnabled()` is true, followed through `addCrossWindowBlurEnabledListener`; false → the same
  fallback rule as here (`[fluent] … reason=unsupported`). The AVD supports it (R10 testability review:
  `supports_background_blur=1`, `mBlurEnabled=true`); the S25U is unknown until R4. Nothing in this phase waits on the phone.
- 2026-09-22 (agent): **The material, from Microsoft's Fluent acrylic documentation (RV9 source 2; approximations, H2):**
  Gaussian blur radius 30 epx (Fluent's 30 effective px; 90 px at FHD+, 60 px at HD+, 120 px at QHD+ — the radius scales
  with px/epx like every RV10 value; HWUI's radius → sigma conversion, σ = 0.57735·r + 0.5 px, is confirmed at build start
  and recorded here), a tint colour T at tint opacity α = 0.8 (Fluent's in-app default), and a 2 % noise (per-pixel value
  noise of ± 5 levels from a hash of the pixel position, deterministic, no asset, no allocation). Draw order: blurred
  backdrop → tint at α → noise. Fluent's exclusion / luminosity blend layer is left out (its effect at this opacity is
  below one level on dark fills; H2). Light theme: the same material with the light-theme fills as tints.
- 2026-09-22 (agent): **One fallback rule.** With acrylic off, a surface draws its FILL — the colour it draws today — at
  opacity 1 and nothing else: no blur, no tint math, no noise. For the app list the fill is not a solid: R3 A18 (MEDIUM)
  measured W10M's app-list background as the Start wallpaper showing through a dark layer (sample (0,10,23) on S1/S2),
  while the built app list draws the solid theme background (`StartActivity`'s page 1 draws no wallpaper; the window
  background is black). So the app list's fallback IS the measured W10M form — the wallpaper under the tint, unblurred —
  which also closes a phase 01 fidelity gap (an INDEX Change Log line for phase 01 when built, H3). With no background
  image set the app list draws the solid theme background, and no static layer is allocated.
- 2026-09-22 (agent): **Tint derivation keeps every measured fill in its own row** (the design for interview Q1's lean, A).
  A surface whose fill F was measured over a captured backdrop B_m gets T = (F − (1 − α)·B_m) / α, so that over B_m the
  material reads exactly F and only what shows THROUGH changes elsewhere. Where any channel of T would fall below 0 (a
  captured fill darker than 20 % of its captured backdrop) the surface cannot be acrylic at this α and stays solid; none of
  this phase's surfaces does (table). The measured PRESSED fills (R7 §3.6.3 (79,84,80), R7 §3.1.6 (63,68,64)) are drawn as
  opaque fills over the pressed item, as captured. Phase 04's A19 "background (0,0,0) as captured" over a non-black app
  cannot satisfy the derivation and is a phase 04 interview item (conflict recorded in the final report).
- 2026-09-22 (agent): **The surface table.** α = 0.8 throughout; B_m is the backdrop the E-row measures the surface over.

  | Surface | Owner / row | Source | F as built or measured | B_m | T (dark) | Fallback |
  |---|---|---|---|---|---|---|
  | App-list backdrop (`start_page`'s pager page 1) | 01; A18 MEDIUM | static wallpaper | wallpaper through a dark layer, sample (0,10,23) | wallpaper (unknown pixel) | theme background (0,0,0) | wallpaper under the tint, unblurred (A18's form); no image → theme background |
  | App-list hold menu band (`applist_menu`) | 02 H21 | live | theme background (0,0,0) | the app list (black in H21's setup) | (0,0,0) | (0,0,0) |
  | Music hold menus (`music_menu_*` band) | 10 MUSIC8 | live | theme background | the collection page | (0,0,0) | theme background |
  | Tess's ≡ pane (`cortana_pane`) | 03 E15; R7 §3.1.6 | live | (14,19,13) | Cortana's Home page (0,0,0), R6 §3.1.14 | (18,24,16) | (14,19,13) |
  | Reminder long-press menu (`CortanaUi.MENU_FILL`) | 03 E15; R7 §3.6.2 | live | (40,40,40), border (71,76,70) opaque | Reminders page (14,19,13) | (47,45,47) | (40,40,40) |
  | Burst backdrop (phase 11), if its doc names one | 11 | live | theme background | edit-mode Start | (0,0,0) | theme background |
  | Pod bay page (phase 14, when built) | 14 | static wallpaper | its own P4 fill | wallpaper | its fill | its fill |
  | Action center, volume panel (phase 04) | 04 A19 / A20; R7 §4 | cross-window (04's ADD) | (0,0,0) / (55,55,55) | the app below | phase 04's interview | as captured |

  Over the ≡ pane's other pages ((14,19,13), R7 §3.5) the pane reads (17,23,15): a 3-level shift the E15 tolerance note
  in E5 records. Light theme: T = the light fill by the same derivation (phase 01 X9 / phase 02 §1.1.6 approximations).
- 2026-09-22 (agent): **On / off rule** (Fluent's own: acrylic is disabled when Transparency effects are off, under Battery
  Saver and on low-end hardware). `acrylic = transparencyEffects ∧ ¬PowerManager.isPowerSaveMode() ∧
  ¬ActivityManager.isLowRamDevice()`, followed live through `ACTION_POWER_SAVE_MODE_CHANGED` and the setting's flow, so a
  menu that is open when battery saver turns on redraws to its fallback in the next frame. In-app acrylic does NOT follow
  `isCrossWindowBlurEnabled()` (that gates only the cross-window source in phase 04): a phone whose One UI blur reports
  false still gets in-app acrylic. Verified on the AVD 2026-09-22: `cmd power set-mode 1` sets `low_power=1` and the
  platform flips `mBlurEnabled` to false; `set-mode 0` restores both; `settings put global disable_window_blurs 1` flips
  `mBlurEnabled` alone (the cross-window switch; `settings delete global disable_window_blurs` restores); `wm disable-blur
  1` exists on API 36 but throws `SecurityException: Package android does not belong to 2000` from the adb shell, so no row
  uses it. `ro.config.low_ram` is unset on the AVD; the low-RAM branch is a JVM test on the rule, not a device row.
- 2026-09-22 (agent): **"Transparency effects" setting** (per Q2; lean A): `StartTheme` gains `transparencyEffects:
  Boolean = true` (prefs key `transparency_effects`), one `ToggleRow` "Transparency effects" (Windows 10's own wording,
  Settings > Personalization > Colors) under a "Effects" header on Start + theme, tag `theme_transparency_effects`; H5.
- 2026-09-22 (agent, R10 design finding 4): **Reveal on touch** (per Q3; lean A). Fluent's Reveal is pointer-hover; on
  touch it shows the pressed element's border light. Rule: the light applies ONLY to items on transient surfaces (menu items,
  pane items, satellites, pod cards later) and never to Start tiles (Q6) or to list rows on non-transient pages (X19's 15 %
  white stays). Form: a 1-epx border ring on the pressed item, white at 30 % over whatever fill is under it, present from
  touch-down to touch-up with no motion (approximation, H4). The item's interior keeps its measured pressed fill, so E15's
  colour samples (taken inside the item) are unaffected; E7 samples the ring and the interior separately.
- 2026-09-22 (agent): **Motion is untouched.** The pane's 250 ± 17 ms slide (R7 §3.1.10), the reminder menu's 233 ms grow
  (R7 §3.6.4), the pivot's 250 ms settle (X13) and the app-list menu's appearance keep their numbers; E8 re-measures them
  with acrylic on. Acrylic adds no animation of its own (Fluent's acrylic has none).
- 2026-09-22 (agent): **Pivot backdrop rule (approximation, H3).** The blurred wallpaper is drawn by the app-list page and
  moves with it during the pivot swipe, exactly as the unblurred wallpaper moves with Start's page today; whether W10M's
  wallpaper stayed fixed under the pivot was never measured (S1 / S2 contain the swipe; A18 sampled the colour only).
- 2026-09-22 (agent): **Diagnostics** (R10 testability 25): `[fluent] acrylic=on|off reason=setting|battery-saver|low-ram|
  none` on every change and at process start; `[fluent] <surface> source=static|live tint=(r,g,b) alpha=0.8 blur=<r>epx`
  each time a surface is shown; `[fluent] static backdrop rebuilt for <uri> in <ms> ms` per rebuild. **Test tags:**
  `acrylic:<surface>` on each acrylic backdrop node (`acrylic:applist`, `acrylic:applist_menu`, `acrylic:music_menu`,
  `acrylic:cortana_pane`, `acrylic:reminder_menu`, `acrylic:burst`), so a row reads the surface's bounds from the dump.
- 2026-09-22 (agent): **Memory and threads.** The static layer is one screen-sized bitmap (1080 × 2340 × 4 ≈ 10 MB at
  FHD+, ≈ 18 MB at QHD+), built on `Dispatchers.IO` from the already-sampled background decode and dropped when the image is
  removed; the launcher process is bounded in P2 against phase 03 P4's baseline + 30 MB. The live source allocates its
  layer only while a surface shows.
- 2026-09-22 (agent): **Build-start checks, results recorded here when built:** HWUI's radius → σ formula on API 36 (a
  blurred step edge's 10–90 % width, expected 2.563·σ); `GraphicsLayer.renderEffect` availability on the pinned BOM; that
  a `screencap` of an in-app RenderEffect blur contains the blur (it is the app's own frame, so it must); `isLowRamDevice()`
  on the AVD (false); the per-frame cost of the live source on the AVD (`dumpsys gfxinfo`).
- 2026-09-22 (agent): **Two kinds of NEEDS-HUMAN rows** (R10 testability 26): every acrylic and Reveal row is an "accept
  this Fluent design" row; H3 alone is a fidelity row (against R3 A18, MEDIUM); the measured fills' own rows in phases 02,
  03 and 10 stay fidelity rows and are re-run unchanged in E11.
- 2026-09-22 (agent): **No new exported component, no new permission, no network, no asset.** Phase 03 E5's allow-list is
  unchanged (E11).

## Interview queue (Stage A step 4)
Load-bearing first. Each answer lands in Decisions, dated.

1. **Acrylic against the surfaces that were already built and measured** (the reminder menu (40,40,40), Tess's ≡ pane
   (14,19,13), phase 02's H21 band, phase 10's hold menus, phase 04's "as captured" scrim and panel colours) — which wins?
   A. Both: each measured surface keeps its captured colour over the backdrop it was captured on, so every FINAL row keeps
   its number in its own setup, and only what shows THROUGH changes — the blurred picture behind it. A surface whose captured
   fill is too dark for that (phase 04's black action center over a bright app) stays solid and is listed. (lean)
   B. The measurements win: acrylic only where nothing was measured — the app-list backdrop, the burst, the pod bay and
   new menus; every measured surface stays solid, and "menus" in the ruling means the new ones.
   C. Fluent wins: the measured fills become tints at Fluent's opacities as they are, and the affected FINAL rows (phase 03
   E15, phase 02 H21, phase 10 MUSIC8, phase 04 E4(a) / E5) are re-cut to measure the tint over black.
   D. Other / let me clarify.
2. **The switch and battery saver.**
   A. A "Transparency effects" toggle in Settings > Start + theme (Windows 10's own setting), default On, and battery saver
   turns acrylic off while it is on — Windows' rule; the solid W10M fills return in both cases. (lean)
   B. No toggle: acrylic whenever the phone can draw it; battery saver still turns it off.
   C. A toggle only; battery saver is ignored.
   D. Other / let me clarify.
3. **What "light" does on a phone with no pointer.**
   A. A border light on the pressed item of a transient surface (menu rows, pane items, satellites) — Fluent's own touch
   form; nothing on Start tiles, whose press feedback is the ruled setting. (lean)
   B. The border light plus a soft radial light under the finger inside the pressed item.
   C. No light at all — acrylic only.
   D. Other / let me clarify.

## Build tasks
1. Engine core (`ui/fluent/`): the material (tint, α, blur radius in epx, noise), the tint derivation from (F, B_m), the
   on / off rule with its two listeners, the diagnostics lines; JVM tests for the derivation (each table row, the negative
   branch), the rule (setting × battery saver × low-RAM), and the noise's range.
2. Static source: the pre-blurred Start background, built off the main thread once per image / theme change, cached, freed
   on removal; the app-list page draws it under its rows with the theme-background tint; the un-blurred fallback form.
3. Live source: the `GraphicsLayer` record of the content under a surface and the clipped blurred draw; one `Acrylic`
   composable that takes a surface name, its tint and its fallback fill and draws either form.
4. Apply the table: the app-list hold menu band, the Music hold menus, Tess's ≡ pane and reminder menu, phase 11's burst
   backdrop if named — each keeping its measured fill in its measured setup; tags `acrylic:<surface>`.
5. Reveal on touch on transient-surface items (per Q3): the 1-epx border ring at press, never on tiles or plain pages.
6. Settings > Start + theme: the "Transparency effects" toggle (per Q2) and the setting's flow; the Diagnostics page shows
   the `[fluent]` lines like any other.
7. Build-start checks (Decisions) recorded; drivers under `qa/phase-13/scripts/` with `lib.sh` symlinked; the checkerboard
   fixture and the edge-spread / variance measurement script; INDEX Change Log lines for phases 01 (app-list backdrop
   form), 02, 03 and 10 (their surfaces now acrylic, numbers unchanged) when built.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps follow RV13.
AVD tileshell_fhd (1080×2340 @ 450 dpi, 3 px/epx, AOSP API 36, no Google; cross-window blur enabled:
`supports_background_blur=1`, `mBlurEnabled=true`). "Diagnostics" is read with phase 01's command. **Controls:** acrylic
OFF = `adb shell cmd power set-mode 1` (battery saver; restore `set-mode 0` and assert `settings get global low_power` = 0)
or the toggle off (tap `theme_transparency_effects`, restore to On); `settings put global disable_window_blurs 1` is NOT a
control for this phase (cross-window only; E1 records that) and `wm disable-blur` is not usable from adb (Decisions).
**Checkerboard fixture:** an 8-square-across black / white PNG at the display size, pushed to
`/sdcard/Android/data/app.tileshell/files/qa/checker.png` and set as the Start background by rewriting
`shared_prefs/start_theme.xml` key `background` to its `file://` URI through `run-as app.tileshell` with phase 01's
`prefs_edit.py` while the shell is stopped — the route `qa/phase-01/scripts/item4.sh` uses for the frame photo; restore by
removing the key. **Edge spread** = the 10–90 % width, in px, of the intensity step across a checkerboard boundary read
along a row of pixels; expected blurred width = 2.563 · σ with σ = 0.57735 · r + 0.5 and r = 30 epx · px/epx (≈ 135 px at
3 px/epx), pass = within ± 20 %; "sharp" = width ≤ 2 px.
**Emulator:**
- E1 The rule and its controls, both directions: after provisioning, diagnostics carry `[fluent] acrylic=on reason=none`;
  `cmd power set-mode 1` → within 1 s `[fluent] acrylic=off reason=battery-saver` and the app-list backdrop (E2's fixture,
  app list showing) reads the un-blurred form (edges sharp, pixels = 0.2 × checker ± 3); `set-mode 0` → `acrylic=on` and the
  blurred form again; toggle off → `acrylic=off reason=setting`, same look as under battery saver; toggle on; with
  `settings put global disable_window_blurs 1` the line stays `acrylic=on` and the app list stays blurred (the switch gates
  cross-window blur only; recorded for phase 04); `settings delete global disable_window_blurs`.
- E2 The backdrop, not the element, is blurred (static source, app list): checkerboard set, swipe to the app list, screencap.
  In the app-list region between the drawn bars, in a horizontal strip through a letter-group gap (no text; strip found from
  the dump's `applist_*` bounds), the edge spread is the blurred width and the pixel values are 0.2 × (blurred checker) ± 3
  (T = black, α = 0.8: white squares read ≈ 51 far from an edge, black ≈ 0); in the same capture the "A" letter header's
  glyph edge and an app-name row's text edge are sharp; a screencap of Start (page 0) shows the checker's edges sharp (the
  wallpaper is not blurred in place); `acrylic:applist` is in the app-list dump with the page's bounds. Acrylic off (E1's
  control): edges sharp, pixels = 0.2 × checker ± 3, `acrylic:applist` still present (the same surface, its fallback form).
  A second pass at `wm size 720x1560` (2 px/epx): the blurred width scales to r = 60 px's value ± 20 %; `wm size reset`.
- E3 The live source keeps the measured fill in its own setup and blurs a bright backdrop (reminder menu): E15's setup
  (Dark theme; a reminder with a photo, one for tomorrow, one Whenever), long-press the tomorrow row: the menu's interior,
  sampled in a 10 × 10 px patch clear of text over the page fill, reads (40,40,40) ± 2 and its border (71,76,70) ± 2 —
  E15's values unchanged; then long-press the photo row so the menu overlaps the photo (dump bounds of `acrylic:reminder_menu`
  intersect the photo's node): inside the overlap the pixels equal 0.8·(47,45,47) + 0.2·(blurred photo) ± 4 and the photo's
  edge under the menu has the blurred width, while the same edge outside the menu is sharp; acrylic off: the overlap reads
  (40,40,40) ± 2. Delete the reminders (E15's own steps).
- E4 H21 band and Music hold menus: checkerboard set, app list, long-press a row (`input swipe x y x y 1000`): inside
  `applist_menu` clear of its item text the pixels equal 0.2 × (blurred backdrop) ± 4 with the blurred edge width, and the
  band's item text is sharp; acrylic off: (0,0,0) ± 1 (H21's band as built). Music: MUSIC6's fixtures, albums pivot,
  long-press a track so `music_menu_*` overlaps album art: art under the band blurred, art beside it sharp; off: the theme
  background ± 1. Restore the background key.
- E5 The ≡ pane: E15's pane measurement setup (opened over Cortana's Home page) reads (14,19,13) ± 2 inside the pane clear
  of text; opened over the Reminders page with rows, a patch over a row's white title reads lighter than the page fill by
  0.2 × (blurred title) and never (14,19,13), and the title's edge under the pane has the blurred width; the pane over the
  Reminders page's empty area reads (17,23,15) ± 2 (the table's note); acrylic off: (14,19,13) ± 1 everywhere.
- E6 Noise: in a 40 × 40 px patch of any acrylic surface over a flat backdrop (E5's pane over the Home page's black), the
  per-pixel standard deviation is between 1.5 and 4 levels and no two adjacent frames differ in the patch (deterministic
  noise: two screencaps 1 s apart are pixel-identical); acrylic off: standard deviation 0.
- E7 Reveal on touch: `input motionevent DOWN` at a reminder-menu item's centre, screencap while held, `UP`: the item's
  1-epx (3 px) border ring reads the pressed fill lightened by 30 % white ± 4 (Q3 A), the item's interior 2 epx inside the
  ring reads R7 §3.6.3's (79,84,80) ± 2, the ring is gone in the first frame after `UP`; the same on a `cortana_pane` item
  ((63,68,64) interior); a Start tile pressed with the press style None shows zero pixel change in the tile region (phase
  01 E10's check); a Settings row pressed shows X19's flat 15 % white and no ring (a 10-px patch on the row's edge equals its
  interior).
- E8 Measured motion holds with acrylic on (RV11, 60-fps screenrecord, `show_touches 1` restored to 0): the pane settles
  250 ± 17 ms after its first frame (R7 §3.1.10), the reminder menu settles 233 ms (+ one frame) after its half-height first
  frame (R7 §3.6.4), the pivot settles in 250 ms ± one frame (X13), and the app-list menu's first frame is at full height
  (no motion added).
- E9 Frame cost on the AVD, recorded not gated: `dumpsys gfxinfo app.tileshell reset`; open and close the reminder menu 20
  times and swipe Start ↔ app list 20 times with live tiles flipping; `dumpsys gfxinfo app.tileshell` janky-frame % and the
  99th percentile recorded in the row (host GPU; the phone's P2 is the bounded row).
- E10 Persistence and memory: the toggle survives `am force-stop` (Settings dump after reopen shows its state); with the
  checkerboard set, `dumpsys meminfo app.tileshell` total PSS on Start vs on the app list differs by ≤ 14 MB (one screen
  layer + noise), and after "Remove picture" (`theme_background_remove`) returns to within 2 MB of the Start figure.
- E11 Regression: phase 03 E15, phase 02 E2, phase 10 MUSIC8, phase 01 E12 and E19 re-run unchanged on this build and pass
  with their numbers (the Q1 A promise); `qa/phase-03/scripts/exported.py` against `qa/phase-03/exported-allowlist.txt`
  reports no new exported component.
- E12 Diagnostics: every surface shown in E2–E5 has its `[fluent] <surface> source=… tint=… alpha=0.8 blur=30epx` line,
  and the E1 reasons appear in order.

**Phone-only (S25 Ultra):**
- P1 R4's blur probe result attached (INDEX R4 row: `supports_background_blur`, `mBlurEnabled`, both under power saving,
  the `FLAG_BLUR_BEHIND` probe) — phase 04's gate, recorded here so this phase's `reason=unsupported` branch is known to
  be reachable or not on the phone.
- P2 Frame pacing and memory on the 1440 × 3120 panel: E9's script with `dumpsys display` showing the same mode before and
  after; janky ≤ 5 % and 99th percentile ≤ 2 vsync periods (phase 01 P4's thresholds); launcher PSS ≤ phase 03 P4's baseline
  + 30 MB with the app-list layer built.
- P3 Samsung Power saving on and off turns acrylic off and on (`[fluent]` lines), and One UI's Accessibility > Visibility
  enhancements > "Reduce transparency and blur" is probed: `settings list global/system/secure` diffed across the toggle;
  if a key changes, it is recorded here and H8 asks whether the shell should follow it (an ADD to the rule); if none does,
  recorded as not readable.
- P4 In-app acrylic looks as on the AVD (screencaps of E2–E5's surfaces attached for H1).

**NEEDS-HUMAN** ("accept" rows unless marked):
- H1 The acrylic look on every surface in the table (Fluent design, no W10M original).
- H2 Blur radius 30 epx, tint opacity 0.8, 2 % noise, no exclusion layer (approximations from Fluent's desktop recipe).
- H3 Fidelity row (R3 A18, MEDIUM): the app list's wallpaper-through-a-dark-layer form — blurred with acrylic on, unblurred
  off — against A18's sample; and the wallpaper moving with the pivot page (approximation).
- H4 The border light on press (approximation of Fluent's touch Reveal), and that tiles and plain pages have none.
- H5 The "Transparency effects" toggle: wording, placement, default On.
- H6 Acrylic turning off under battery saver (Windows' rule).
- H7 Any surface the derivation leaves solid (none in this phase; phase 04's action center is its own interview).
- H8 Whether the shell should follow One UI's "Reduce transparency and blur" (P3, phone).
- H9 The feel with acrylic on the phone (P2's numbers, P4's captures).

## Edge cases
- Battery saver toggled while a menu is open (`cmd power set-mode 1` with the reminder menu showing): the surface shows its
  fallback in the next frame (screencap ≤ 100 ms later reads (40,40,40)); toggled back: acrylic returns without closing the
  menu.
- The Transparency toggle changed while the app list shows (Settings opened over it and Back): the backdrop switches form.
- Start background removed or changed while the app list shows: the static layer is rebuilt or dropped (`[fluent] static
  backdrop rebuilt` / no line and the theme background shown), no stale blur (screencap).
- A huge background image (8000 × 8000 push): the pre-blur runs on the sampled decode at screen size — launcher pid unchanged,
  PSS within E10's bound.
- No background image set: solid theme background on the app list, no static layer (E10's memory figure unchanged).
- Light theme with a bright wallpaper: the app list stays readable (tint white at 0.8; screencap for H1).
- Edit mode: the pivot is locked, so the app list cannot show; entering and leaving edit mode does not rebuild the static
  layer (no `rebuilt` line).
- The live source over flipping tiles (the burst, phase 11): dumps through RV13's route; the layer records every frame —
  E9's numbers cover it.
- A surface over another acrylic surface (the H21 band over the acrylic app list): the live layer records the app list
  including its backdrop; the band's result is 0.2 × that, never a second blur of the wallpaper beyond the first (E4).
- Two transient surfaces at once (the pin band over Start while the burst is open): both draw; recorded in a screencap.
- Screen off while a menu shows, then wake: the menu is drawn blurred on resume, not black (screencap).
- Process death while a menu shows: nothing to restore but the toggle (E10).
- RV10: `wm size 720x1560` and `1440x3120`, `wm density 420` / `560`, `font_scale 1.3`: the radius follows px/epx and the
  surfaces' bounds follow phase 01 E3's rule; restore all.
- `disable_window_blurs 1` (the developer "Disable window blurs" toggle): in-app acrylic unaffected (E1); it is phase 04's
  overlays that go to their fallback — recorded so the two phases are not confused.
- Low-RAM device: the rule's branch (JVM test); cannot be produced on this AVD (`ro.config.low_ram` is a build property).
- Accessibility: `animator_duration_scale 0` (Remove animations) — acrylic is not an animation and stays; `high_text_contrast_enabled 1`
  — the platform's text outlines draw over the acrylic, nothing breaks (screencap); `accessibility_display_inversion_enabled 1`
  — the material inverts with the screen (screencap); all restored.
- A rapid open / close of a menu (10 holds in 15 s): no leaked live layers (E10's PSS after the burst returns to within 2 MB).
- The app list with 300+ packages (phase 01's edge): the backdrop is one layer whatever the list's length; scroll frame times
  in E9's capture.

## QA evidence
