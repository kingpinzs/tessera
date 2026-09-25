---
phase: 13
slug: fluent-materials
status: FINAL
depends-on: [01, 02, 03, 10, 11]   # uses phase 15's `lib.sh` `record` and `ui/MotionClock.kt`, merged on main (PR #1), not its gate (T13-26, T13-27); the in-app surfaces it dresses exist (app list, H21 band, Music menus, Tess's pane and menu) and phase 11's satellites take its lights; it lands BEFORE 12 (T12-3: order 11 -> 13 -> 12 -> 14, presets write this phase's transparencyEffects) and BEFORE 04, which ADDs the cross-window source
---

# Phase 13 — Fluent materials: acrylic and light on the transient surfaces

## Goal
One permanent in-app materials engine draws Fluent acrylic — a blurred copy of what is BEHIND a surface, tinted, with a
2 % noise — on the shell's transient surfaces (the app-list backdrop, the app-list and Music hold menus, Tess's ≡ pane and
reminder menu; phase 11's burst has no backdrop, T11-8), and Reveal lights on press — a border ring and a radial light under the
finger (Q3 B) — for the pressed items of the menus and the ≡ pane and phase 11's satellites (Q3 B's list; the app list's rows
keep X19's 15 % white; T13-28). Blur is a device
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
built) and a live per-frame layer for surfaces over live content (menus, the pane; not the burst, T11-8); the on / off rule
(setting, battery saver, low-RAM) with its diagnostics line; the "Transparency effects" toggle in Settings > Start + theme
(per interview Q2); the surface table below — the COMPLETE list of this phase's surfaces (T13-19) — applied to its surfaces,
each keeping its measured fill in its measured setup; Reveal lights on press for items on transient surfaces and phase 11's satellites (Q3 B: the border ring
plus the radial light, T13-1); the app-list backdrop's un-blurred
W10M form as its fallback (R3 A18); test tags; drivers under `qa/phase-13/scripts/` with `lib.sh` symlinked.
**Out (explicitly):** cross-window blur-behind on overlay windows (`WindowManager.LayoutParams.setBlurBehindRadius` /
`FLAG_BLUR_BEHIND`, gated by `WindowManager.isCrossWindowBlurEnabled()`): phase 04 ADDs it as a third backdrop source using
THIS engine's material, table and fallback, after R4's blur probe (INDEX R4 row); its E4(a) / E5 colour rows are
re-specified from this doc's table before its interview (R10 triage). Fluent depth, shadows and connected animations (not
ruled). Start tiles (the Q6 press setting stands; no light on tiles, R10 design finding 4). Non-transient pages: Settings,
Weather, the Music collection, Tess's black page and her text box ((46–48) fill, a fixed bar). Reveal on pointer hover
(no pointer). Any blur library (haze or other: a new dependency needing an R2-style check; the platform has the API). A
second material for the light theme (the same material with the light fills). Hooks for later phases. The other transient
overlays keep their fills (T13-19): the pin confirmation band (H22), the app-list and Music jump grids (X8), Cortana's combo
drop-down (`OptionList`, H25, which shares `CortanaUi.MENU_FILL`), and the name boxes and pickers.

## Decisions
- 2026-09-23: Interview Q3 — border light AND a radial light under the finger (Jeremy: "(b)"). On the transient surfaces'
  pressed items (menu rows, pane items, phase 11's satellites): Fluent's border light on the item's edge plus a soft radial light
  centred on the touch point inside the item, following the finger while it is down. Start tiles get neither; their press
  feedback stays the ruled phase 01 setting. Both lights obey the Transparency effects switch and battery saver (Q2). Values are
  P4 (Fluent's pointer Reveal adapted to touch), judged in a NEEDS-HUMAN row.
- 2026-09-23: Interview Q2 — a switch plus battery saver (Jeremy: "(a)"). "Transparency effects" in Settings > Start + theme,
  default On; while battery saver is on, acrylic is off whatever the switch says; either way every surface draws its measured
  solid W10M fill. Agent reading, cross-phase: the switch is one of the "everything visual" items a phase 12 theme preset sets
  (phase 12 Q4), so e.g. the Midnight preset can turn it off; phase 12 carries that line.
- 2026-09-23: Interview Q1 — both: measured colours are kept (Jeremy: "(a)"). Each measured surface keeps its captured colour
  over the backdrop it was captured on (every FINAL row keeps its number in its own setup); only what shows through changes — the
  blurred picture behind. A surface whose captured fill is too dark for anything to show through (phase 04's black action center
  over a bright app) stays solid, and the build lists every such surface. Phase 04's own queue item 11 (captured black vs acrylic
  vs re-measure) is answered by this rule unless Jeremy re-opens it there.
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
  (`StartTheme.backgroundUri`, decoded as `StartPage.rememberBackground` decodes it — through a `BackgroundDecoder` object both
  call, because that function is `private` today, `start/StartPage.kt:1148`; an ADD to phase 01's part, T13-5) is blurred once per
  image / theme change, off the main thread, at screen resolution, and cached. Geometry and kernel (T13-18): the picture is placed
  `ContentScale.Crop` to the app-list page's own size at scale 1, with no parallax and no 1.3× (those are Start's own, X15,
  `StartPage.kt:565-577`), and blurred by HWUI's own blur — the cropped picture drawn into a `RenderNode` with
  `RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)`, rendered once by a `HardwareRenderer` into an `ImageReader` surface
  off the main thread and kept as the cached bitmap — never a CPU box or stack blur, so both sources share one σ; `acrylic_expect.py`
  maps the fixture the same way (Crop to the page's dump bounds). When (T13-15): the layer is built when the image is set and at
  process start — the pager composes the app-list page beside Start (`StartActivity.kt:170`, `beyondViewportPageCount = 1`), so the
  layer exists before the first swipe and no unblurred frame shows — and kept until the image changes or is removed. Used where what lies behind the surface IS the
  wallpaper — the app-list pager page (R3 A18: "app-list background shows the Start wallpaper through it") and, when built, phase
  14's pod bay page. (b) Live (mechanism re-cut 2026-09-25 by T13-11): the content under a surface is recorded into a
  `rememberGraphicsLayer()` layer L (`androidx.compose.ui.graphics.layer.GraphicsLayer`, `record` / `drawLayer`, Compose BOM
  2026.06.01) that carries NO effect. L is recorded from ONE child holding the page's background and content, a sibling drawn
  BELOW the surface — at each call site the background moves from the surface's parent into that child (the surface sits inside
  the node that draws the page background today: `AppListPage.kt:257` / `:291`, `MusicCollectionPage.kt:191` / `:286`,
  `RemindersPage.kt:334` / `:408-414`, `CortanaSessionRoot.kt:74` / `:100-107`), so the surface is never inside what L records —
  and the page draws L to show itself, unchanged. The surface owns a second layer S whose bounds are the surface's bounds grown by
  3σ on every side (clamped to the window); S has `renderEffect = BlurEffect(r, r, TileMode.Clamp)` (platform `RenderEffect`, API
  31+; minSdk 34) and records L translated by −S's origin; the surface draws S clipped to its own bounds, then the tint, then the
  noise. Every surface pixel is therefore blurred from the real backdrop around it; only the window edge is clamped. No bitmap copy
  of L exists anywhere in the live source (no `toImageBitmap`, no `Bitmap`), so what S blurs is always what the page shows —
  liveness is structural (T13-24; E5's live sub-step proves it). An effect on L would blur the page itself, and an effect on a
  surface-sized layer would blur only the pixels inside the surface (T13-11). Used for surfaces over live pages — the app-list hold menu (`applist/AppListMenu.kt`
  `PinToStartMenu`), the Music hold menus (`music/MusicCollectionPage.kt` `MenuState`), Tess's ≡ pane
  (`cortana/ui/CortanaNavPane.kt`) and reminder long-press menu (`cortana/ui/RemindersPage.kt`, `CortanaUi.MENU_FILL`), ~~and phase
  11's burst backdrop if its doc names one~~ (DROPPED 2026-09-23 by T11-8: phase 11 names none — edit mode's dim is the burst's
  backdrop; the satellites are tiles and stay opaque). The live source records only while its surface shows. Its cost is one
  recorded layer per frame at the display size while a menu is open; E9 records it on the AVD and P2 bounds it on the phone. ~~Over
  flipping tiles (the burst) the screen never idles: RV13's `UiDevice` route applies to dumps, and the burst's own diagnostics line
  carries its bounds (R10 testability 31).~~ (DROPPED 2026-09-23 by T11-8: no live source runs over the burst.)
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
  | App-list backdrop (the app-list pager page, `app_list`) | 01; A18 MEDIUM | static wallpaper | wallpaper through a dark layer, sample (0,10,23) | wallpaper (unknown pixel) | theme background (0,0,0) | wallpaper under the tint, unblurred (A18's form); no image → theme background |
  | App-list hold menu band (`applist_menu`) | 02 H21 | live | theme background (0,0,0) | the app list (black in H21's setup) | (0,0,0) | (0,0,0) |
  | Music menus (`MusicMenu`'s band, `music_menu`: the collection's hold menus and now-playing's `•••` band, `MusicNowPlaying.kt:235`; T13-19) | 10 MUSIC8 | live | theme background | the page under each (black) | (0,0,0) | theme background |
  | Tess's ≡ pane (`cortana_pane`) | 03 E15; R7 §3.1.6 | live | (14,19,13) | Cortana's Home page (0,0,0), R6 §3.1.14 | (18,24,16) | (14,19,13) |
  | Reminder long-press menu (`CortanaUi.MENU_FILL`) | 03 E15; R7 §3.6.2 | live | (40,40,40), border (71,76,70) opaque | Reminders page (14,19,13) | (47,45,47) | (40,40,40) |
  | ~~Burst backdrop (phase 11), if its doc names one~~ DROPPED 2026-09-23 (T11-8: phase 11 names none) | ~~11~~ | ~~live~~ | ~~theme background~~ | ~~edit-mode Start~~ | ~~(0,0,0)~~ | ~~theme background~~ |
  | Pod bay page (phase 14, when built; tag `acrylic:pod_bay`) — re-cut 2026-09-23 by T14-11 (was "its own P4 fill") | 14 | static wallpaper | as the app-list backdrop | wallpaper | (0,0,0) | wallpaper under the tint, unblurred (A18's form); no picture → theme background |
  | Action center, volume panel (phase 04) | 04 A19 / A20; R7 §4 | cross-window (04's ADD) | (0,0,0) / (55,55,55) | the app below | phase 04's interview | as captured |

  Over the ≡ pane's other pages ((14,19,13), R7 §3.5) the pane reads (17,23,15): a 3-level shift the E15 tolerance note
  in E5 records. Light theme: T = the light fill by the same derivation (phase 01 X9 / phase 02 §1.1.6 approximations).
- 2026-09-22 (agent): **On / off rule** (Fluent's own: acrylic is disabled when Transparency effects are off, under Battery
  Saver and on low-end hardware). `acrylic = transparencyEffects ∧ ¬PowerManager.isPowerSaveMode() ∧
  ¬ActivityManager.isLowRamDevice()`, followed live through `ACTION_POWER_SAVE_MODE_CHANGED` and the setting's flow, so a
  menu that is open when battery saver turns on redraws to its fallback in the next frame. In-app acrylic does NOT follow
  `isCrossWindowBlurEnabled()` (that gates only the cross-window source in phase 04): a phone whose One UI blur reports
  false still gets in-app acrylic. Verified on the AVD 2026-09-22 (before `wake_device` forced AC power at every row,
  `qa/phase-03/scripts/lib.sh:47-56`): `cmd power set-mode 1` sets `low_power=1` and the platform flips `mBlurEnabled` to false;
  `set-mode 0` restores both — SUPERSEDED 2026-09-23 by C-18: AOSP refuses low-power mode while powered, and the AVD now reads
  `mIsPowered=true`. Build-start probe, result recorded here when built: "after `wake_device`: `cmd power set-mode 1`; `settings
  get global low_power` → (expected) 0; `dumpsys battery unplug`; `cmd power set-mode 1` → 1" (if it reads 1 on AC, C-18 drops to a
  NOTE). Every row turns battery saver on and off only through `lib.sh` `battery_saver_on` / `battery_saver_off` (build task 7).
  Still valid from the 2026-09-22 probe: `settings put global disable_window_blurs 1` flips
  `mBlurEnabled` alone (the cross-window switch; `settings delete global disable_window_blurs` restores); `wm disable-blur
  1` exists on API 36 but throws `SecurityException: Package android does not belong to 2000` from the adb shell, so no row
  uses it. `ro.config.low_ram` is unset on the AVD; the low-RAM branch is a JVM test on the rule, not a device row.
- 2026-09-22 (agent): **"Transparency effects" setting** (per Q2; lean A): `StartTheme` gains `transparencyEffects:
  Boolean = true` (prefs key `transparency_effects`), one `ToggleRow` "Transparency effects" (Windows 10's own wording,
  Settings > Personalization > Colors) under a "Effects" header on Start + theme, tag `theme_transparency_effects`; H5.
- 2026-09-22 (agent, R10 design finding 4): SUPERSEDED 2026-09-23 by T13-1 (last Decisions lines; Q3 was ruled B, border light
  plus a radial light): **Reveal on touch** (per Q3; lean A). Fluent's Reveal is pointer-hover; on
  touch it shows the pressed element's border light. Rule: the light applies ONLY to items on transient surfaces (menu items,
  pane items, satellites) and never to Start tiles (Q6) or to list rows on non-transient pages (X19's 15 %
  white stays). Form: a 1-epx border ring on the pressed item, white at 30 % over whatever fill is under it, present from
  touch-down to touch-up with no motion (approximation, H4). The item's interior keeps its measured pressed fill, so E15's
  colour samples (taken inside the item) are unaffected; E7 samples the ring and the interior separately.
- 2026-09-22 (agent): **Motion is untouched.** The pane's 250 ± 17 ms slide (R7 §3.1.10), the reminder menu's 233 ms grow
  (R7 §3.6.4), the pivot's 250 ms settle (X13) and the app-list menu's appearance keep their numbers; E8 re-measures them
  with acrylic on. Acrylic adds no animation of its own (Fluent's acrylic has none).
- 2026-09-22 (agent): **Pivot backdrop rule (approximation, H3).** The blurred wallpaper is drawn by the app-list page and
  moves with it during the pivot swipe, exactly as the unblurred wallpaper moves with Start's page today; whether W10M's
  wallpaper stayed fixed under the pivot was never measured (S1 / S2 contain the swipe; A18 sampled the colour only).
- 2026-09-22 (agent): **Diagnostics** (R10 testability 25): `[fluent] acrylic=on|off reason=<the first false term, in the order
  low-ram, battery-saver, setting>|none`, written at process start and whenever the value OR the reason changes (T13-20: with the
  switch off under battery saver the reason is `battery-saver`, which phase 12's preset edge case expects); `[fluent] <surface> source=static|live tint=(r,g,b) alpha=0.8 blur=<r>epx`
  each time a surface is shown; `[fluent] static backdrop rebuilt for <uri> in <ms> ms` per rebuild. Added 2026-09-23:
  `[fluent] <surface> form=acrylic|fallback` each time a shown surface changes form (T13-9); `[fluent] static backdrop failed for
  <uri>: <why> (fallback)` when the static layer cannot be built — file gone, decode failure, grant revoked (T13-10). **Test tags:**
  `acrylic:<surface>` on each acrylic backdrop node (`acrylic:applist`, `acrylic:applist_menu`, `acrylic:music_menu`,
  `acrylic:cortana_pane`, `acrylic:reminder_menu`, ~~`acrylic:burst`~~ — dropped 2026-09-23, T11-8; `acrylic:pod_bay`, built by phase
  14, T14-11), so a row reads the surface's bounds from the dump.
- 2026-09-22 (agent): **Memory and threads.** The static layer is one screen-sized bitmap (1080 × 2340 × 4 ≈ 10 MB at
  FHD+, ≈ 18 MB at QHD+), built on `Dispatchers.IO` from the already-sampled background decode and dropped when the image is
  removed; the layer is built with Start (T13-15), so it is resident while Start shows; the launcher process is bounded in P2
  against phase 03 P4's baseline + 30 MB and E10 isolates the layer's own cost (T13-16). The live source allocates its
  layer only while a surface shows.
- 2026-09-22 (agent): **Build-start checks, results recorded here when built:** HWUI's radius → σ formula on API 36 (a
  blurred ISOLATED step — a half-black / half-white layer — its 10–90 % width, expected 2.563·σ; T13-12); that the static source's
  offscreen `HardwareRenderer` route gives the same width as an on-screen layer (T13-18); that Tess's ≡ pane opens while she
  listens and her persona keeps pulsing under it (E5's live sub-step, T13-24; if it cannot, a Change Log line re-cuts that
  sub-step's backdrop before the gate); `GraphicsLayer.renderEffect` availability on the pinned BOM; that
  a `screencap` of an in-app RenderEffect blur contains the blur (it is the app's own frame, so it must); `isLowRamDevice()`
  on the AVD (false); the per-frame cost of the live source on the AVD (`dumpsys gfxinfo`).
- 2026-09-22 (agent): **Two kinds of NEEDS-HUMAN rows** (R10 testability 26): every acrylic and Reveal row is an "accept
  this Fluent design" row; H3 alone is a fidelity row (against R3 A18, MEDIUM); the measured fills' own rows in phases 02,
  03 and 10 stay fidelity rows and are re-run unchanged in E11.
- 2026-09-22 (agent): **No new exported component, no new permission, no network, no asset.** Phase 03 E5's allow-list is
  unchanged (E11).
- 2026-09-23 (agent, review triage T13-1): **the two lights on touch, Q3 B, with values** (P4; H4 extended). On a pressed item of
  a transient surface (menu rows, pane items, phase 11's satellites — T11-7):
  (1) the 1-epx border ring, white at 30 % over whatever fill is under it, unchanged from the 2026-09-22 form; (2) a radial light —
  a white radial gradient centred on the touch point, radius r = 40 epx (120 px on this AVD at 3 px/epx; it scales with px/epx like
  every RV10 value), alpha 0.10 at the centre falling linearly to 0 at r, clipped to the item, redrawn on every MOVE so it follows
  the finger, gone in the first frame after UP, no motion of its own. A press ends at UP, at cancel, or when the pointer leaves the
  item, and both lights go with it (agent call 2026-09-25, T13-13: the lights are "on pressed items", read with Compose's own press
  semantics). The lights draw over the item's held look AS BUILT: the reminder menu's items draw nothing while held (their
  (79,84,80) is drawn only after a choice, R7 §3.6.6), the current ≡ pane item keeps its accent, a satellite keeps its rest face, and
  the app-list and Music menu items draw their `ROW_PRESS_ALPHA` 0.15 white; nothing in phases 03 / 02 / 10 / 11 changes its pressed
  behaviour for this phase (Q1 A). Both obey the on / off rule: acrylic off → neither. Start
  tiles and plain pages get neither. **Sample rule** (stated once; cited by E7, T13-13): every measured
  pressed-fill sample, and every ring sample, is taken ≥ r + 2 epx from the touch point or after UP, so the measured pressed fills
  (R7 §3.6.3 (79,84,80), §3.1.6 (63,68,64)) keep their numbers. Reason: Q3 B names the light but no value, and a light that falls
  to 0 at a stated radius is both buildable and testable while keeping every measured fill measurable (Q1 A)
- 2026-09-23 (agent, review triage T12-3): build order **11 → 13 → 12 → 14**; phase 12's depends-on gains 13. This phase builds
  BEFORE phase 12, so `StartTheme.transparencyEffects` (this phase's field; `prefs/ShellSettings.kt:17-24` has none today) exists
  when phase 12's presets write it — the Midnight preset and the original W10M preset turn it off. This phase does not depend on 12.
  Reason: phase 12 builds its presets "in their final form" (Rule 16), which needs this field; the swap costs nothing here
- 2026-09-23 (agent, r2 triage T14-11): the pod bay page takes the app-list backdrop's material exactly — static source, T = (0,0,0),
  α 0.8, blur 30 epx, fallback the wallpaper under the tint unblurred (A18's form), no picture → theme background — with tag
  `acrylic:pod_bay` and line `[fluent] pod_bay source=static tint=(0,0,0) alpha=0.8 blur=30epx`; the surface table's row is re-cut to
  match and phase 14 E14 measures it with E2's method. Reason: phase 14's Decision ("the same material — phase 13's app-list backdrop,
  same engine, same parameters") is the explicit one, H10 is written for it, and two sibling pager pages share one material
- 2026-09-25 (agent, Stage A round 3, the last; triage review/2026-09-25-phase13-r3-triage.md): reviewers opus (design) + codex
  CLI (testability) — the Fable reviewer hit the Fable limit (HTTP 429) and Jeremy ruled (b), Opus for Reviewer 1; 29 findings
  merged into T13-11 … T13-29, all applied here, none a question for Jeremy. Agent calls he can overrule: the live source's two
  layers (T13-11); a press ends when the finger leaves the item, and the lights draw over each item's held look as built (T13-13);
  the app list's picture at scale 1 with HWUI's blur for the static copy (T13-18); now-playing's `•••` band is acrylic because it
  shares `MusicMenu`, while the pin band, jump grids, combo drop-down, name boxes and pickers stay solid (T13-19); `reason=`
  precedence (T13-20); the live proof over Tess's listening persona (T13-24). T11-31 (carried from phase 11) is applied in E7.

## Interview queue (Stage A step 4)
Load-bearing first. Each answer lands in Decisions, dated.

1. ~~Acrylic vs measured surfaces~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Acrylic against the surfaces that were already built and measured** (the reminder menu (40,40,40), Tess's ≡ pane
   (14,19,13), phase 02's H21 band, phase 10's hold menus, phase 04's "as captured" scrim and panel colours) — which wins?
   A. Both: each measured surface keeps its captured colour over the backdrop it was captured on, so every FINAL row keeps
   its number in its own setup, and only what shows THROUGH changes — the blurred picture behind it. A surface whose captured
   fill is too dark for that (phase 04's black action center over a bright app) stays solid and is listed. (lean)
   B. The measurements win: acrylic only where nothing was measured — the app-list backdrop, the burst, the pod bay and
   new menus; every measured surface stays solid, and "menus" in the ruling means the new ones.
   C. Fluent wins: the measured fills become tints at Fluent's opacities as they are, and the affected FINAL rows (phase 03
   E15, phase 02 H21, phase 10 MUSIC8, phase 04 E4(a) / E5) are re-cut to measure the tint over black.
   D. Other / let me clarify.
2. ~~Switch and battery saver~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **The switch and battery saver.**
   A. A "Transparency effects" toggle in Settings > Start + theme (Windows 10's own setting), default On, and battery saver
   turns acrylic off while it is on — Windows' rule; the solid W10M fills return in both cases. (lean)
   B. No toggle: acrylic whenever the phone can draw it; battery saver still turns it off.
   C. A toggle only; battery saver is ignored.
   D. Other / let me clarify.
3. ~~Light on touch~~ RULED 2026-09-23: B (see Decisions). Original question kept below.
   **What "light" does on a phone with no pointer.**
   A. A border light on the pressed item of a transient surface (menu rows, pane items, satellites) — Fluent's own touch
   form; nothing on Start tiles, whose press feedback is the ruled setting. (lean)
   B. The border light plus a soft radial light under the finger inside the pressed item.
   C. No light at all — acrylic only.
   D. Other / let me clarify.

## Build tasks
1. Engine core (`ui/fluent/`): the material (tint, α, blur radius in epx, noise), the tint derivation from (F, B_m), the
   on / off rule with its two listeners, the diagnostics lines; JVM tests for the derivation (each table row, the negative
   branch), the rule and its reason precedence on all 8 combinations of setting × battery saver × low-RAM (T13-20), and the
   noise's range.
2. Static source: the pre-blurred Start background — cropped to the app-list page at scale 1 with no parallax, blurred by HWUI
   (`RenderNode` + `RenderEffect.createBlurEffect`, rendered by a `HardwareRenderer` into an `ImageReader`, T13-18) — built off the
   main thread when the image is set and at process start (T13-15) and again per image / theme change, cached, freed on removal; the app-list page draws it under its rows with the theme-background tint; the un-blurred fallback form; a build
   that fails falls back and logs `[fluent] static backdrop failed for <uri>: <why> (fallback)` (T13-10). The
   decode goes through a `BackgroundDecoder` object that phase 01's `StartPage` and this source both call — `StartPage
   .rememberBackground` is `private` today (`start/StartPage.kt:1148`) — an ADD to phase 01's part, in task 7's Change Log line
   (T13-5).
3. Live source (T13-11): at each call site the page's background and content move into one backdrop child drawn below the
   surface; that child records itself into layer L (no effect) and draws L; the surface's layer S (its bounds + 3σ each side,
   clamped to the window, `BlurEffect(r, r, TileMode.Clamp)`) records L translated and is drawn clipped to the surface; one `Acrylic`
   composable that takes a surface name, its tint and its fallback fill and draws either form, logging `[fluent] <surface>
   form=acrylic|fallback` when a shown surface changes form (T13-9).
4. Apply the table: the app-list hold menu band, `MusicMenu` (both call sites: the collection's hold menus and now-playing's
   `•••`, T13-19), Tess's ≡ pane and reminder menu ~~, phase 11's burst
   backdrop if named~~ (dropped 2026-09-23, T11-8) — each keeping its measured fill in its measured setup; tags `acrylic:<surface>`.
5. The two lights on touch (Q3 B, T13-1): the 1-epx border ring and the radial light (r = 40 epx, alpha 0.10 → 0, following
   MOVE, gone after UP, cancel or the pointer leaving the item — T13-13) on the menu and pane items and phase 11's satellites,
   never on tiles or plain pages; both off with acrylic. Each item's press is observed without consuming it and without changing
   what the item draws or does today. Satellites are draw-only (`QuickBurst.kt:223-226`): the satellite's press point is set on
   `QuickBurstState` by `EditGestures`' `Hit.Satellite` branch (DOWN, each MOVE, cleared at UP, cancel or leaving the satellite) and
   drawn by `QuickBurstLayer` (T13-25).
6. Settings > Start + theme: the "Transparency effects" toggle (per Q2) and the setting's flow; the Diagnostics page shows
   the `[fluent]` lines like any other.
7. Build-start checks (Decisions) recorded; drivers under `qa/phase-13/scripts/` with `lib.sh` symlinked; the checkerboard
   fixture (4 squares across, T13-12) and the edge-spread / variance measurement script; `qa/phase-13/scripts/reminders_fixture.sh`
   (E3, T13-21) and the split photo it pushes; `qa/phase-13/scripts/acrylic_expect.py` (T13-2); the
   `[motion]` lines E8 reads for the pane slide, the reminder menu grow, the pivot settle and the app-list menu's appearance,
   added here as logging-only ADDs where their phases do not log them yet (C-5), each carrying `frames=<n> maxGapMs=<ms>` (C-31),
   written through phase 15's `ui/MotionClock.kt` (`animate` / `jump`), one format (T13-27);
   two `lib.sh` additions owned here (13 is their first user in the Build order): `battery_saver_on` (`adb shell dumpsys battery
   unplug`; save `screen_off_timeout` and raise it to 1800000 so the screen stays on once stay-on-while-plugged stops; export
   `BS_MARK=$(adb shell date +%s%3N)`; `adb shell cmd power set-mode 1`; ASSERT `settings get global low_power` = 1 — a
   precondition that fails loudly) and `battery_saver_off`
   (`set-mode 0`, restore the timeout, `wake_device`) (C-18); `record <name> <value>` and `row_end`'s recorded-only line (C-26)
   are phase 15's and already in `lib.sh` (`:388-395`, `:140-145`; T15-21 / C-33) — this phase builds nothing for them (T13-26);
   INDEX Change Log lines for phases 01
   (app-list backdrop form, the `BackgroundDecoder`), 02, 03 and 10 (their surfaces now acrylic, numbers unchanged; the
   `[motion]` lines) and 11 (satellites draw the two lights; the press point in `EditGestures`, T13-25) when built.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 on the shell's own
clock (below); dumps follow RV13. **Seeding (C-3):** rows that read Start's grid start with `layout_restore
qa/phase-02/baseline_layout.json` (`qa/phase-02/scripts/layout.sh`) — this phase adds no `addedOnce` marker and pins no fixture,
so phase 02's file, with its markers and hand-set sizes, is complete for this build — and assert zero `assignSlotOnce … ->
assigned` lines after it; the one exception is E7(c), which restores phase 11's own `qa/phase-11/baseline_layout.json` with
phase 11's fixture APKs installed (its E5 setup: the tileclient-a tile), and puts phase 02's file back after it (T13-13). **Motion clock (C-5):** every motion the shell animates logs its own clock from `withFrameNanos`
(`[motion] <name> t0=<uptime> settle=<ms> frames=<n> maxGapMs=<ms>`, written by phase 15's `ui/MotionClock.kt`, T13-27) and the row asserts the logged numbers against RV11's
tolerance, and `maxGapMs` ≤ 33.4 ms (2 vsync, C-31); a screenrecord
corroborates under phase 05's frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion) and is never the primary
clock. **Ring reads (C-20):** every ring assertion reads `ring_since` from a MARK taken immediately before the step's action (after
any clock jump, so the MARK is on the new clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`
(helpers: phase 11's build task 7). **Wake (C-25):** after any `adb reboot` (boot-completed poll), `dumpsys battery unplug` or
`KEYCODE_SLEEP` step, the driver calls `wake_device` and asserts it printed `Awake` before the next tap — except inside
`battery_saver_on` … `battery_saver_off`, where the helper's raised `screen_off_timeout` keeps the screen on and `wake_device` (which
resets the battery and so ends battery saver) runs in `battery_saver_off`. **Expected acrylic values (T13-2, T13-11):** wherever a row expects "0.8·T + 0.2·B", B = GaussianBlur_host(σ = 0.57735·r + 0.5 px)
of the WHOLE backdrop capture, edge mode `nearest` (TileMode.Clamp at the window edge), read at the same pixel, computed on the host
by `qa/phase-13/scripts/acrylic_expect.py` from the pulled fixture mapped as the page maps it (static source: Crop to the page's dump
bounds, T13-18) or from a capture of the same screen with the surface closed or acrylic OFF (E1's control) — never read from
the capture under judgement. **Acrylic-on colour reads (T13-17):** every acrylic-on colour clause compares the MEAN of a ≥ 10 × 10
px patch, or for an edge profile the column mean of ≥ 8 strip rows — never one pixel, since the material's ± 5 noise is not
reproduced on the host; single pixels are compared only with acrylic off, on opaque fills, or against the same pixel of a
reference capture (E7). A clause that promises a measured fill over its measured backdrop (Q1 A: E3's (40,40,40), E5's (14,19,13)
and (17,23,15), E6's flat patch) places its patch where the oracle's B is within ± 1 of B_m over the whole patch, and asserts that
first as a precondition (no such patch → the row fails loudly, not the product). **Recorded rows (C-13, re-cut 2026-09-23 by
C-26):** a recorded clause uses `lib.sh` `record`, never an assert, so it is never counted as a PASS or a FAIL; a row with only
recorded clauses ends `<row>: recorded only (<n> facts)` (phase 15's `lib.sh` helper, T13-26).
AVD tileshell_fhd (1080×2340 @ 450 dpi, 3 px/epx, AOSP API 36, no Google; cross-window blur enabled:
`supports_background_blur=1`, `mBlurEnabled=true`). "Diagnostics" is read with phase 01's command. **Controls:** acrylic
OFF = `battery_saver_on` (`lib.sh`, C-18: it unplugs the simulated battery, because `wake_device` forces AC power and AOSP refuses
low-power mode while powered, and it asserts `low_power` = 1 before the row goes on; restore `battery_saver_off`, then assert
`settings get global low_power` = 0) or the toggle off (tap `theme_transparency_effects`, restore to On); `settings put global disable_window_blurs 1` is NOT a
control for this phase (cross-window only; E1 records that) and `wm disable-blur` is not usable from adb (Decisions).
**Checkerboard fixture (re-cut 2026-09-25 by T13-12):** a 4-square-across black / white PNG at the display size (270-px squares
at FHD+, 180 px after `wm size 720x1560`), pushed to
`/sdcard/Android/data/app.tileshell/files/qa/checker.png` and set as the Start background by rewriting
`shared_prefs/start_theme.xml` key `background` to its `file://` URI through `run-as app.tileshell` with phase 01's
`prefs_edit.py` while the shell is stopped — the route `qa/phase-01/scripts/item4.sh` uses for the frame photo; restore by
removing the key. **Edge spread** = the 10–90 % width, in px, of the step between the values at the two adjacent square CENTRES
on the same row (the row's own plateaus, not 0 and 255), read on the column mean of ≥ 8 strip rows clear of text; expected
blurred width = 2.563 · σ with σ = 0.57735 · r + 0.5 and r = 30 epx · px/epx (r = 90 px, σ = 52.46 px, width ≈ 134.5 px at
3 px/epx), pass = within ± 20 %; "sharp" = width ≤ 2 px. (Host check 2026-09-25: 4-across gives 131 px at FHD+ and 87 px at 720
wide, both inside their bands at every strip offset; the former 8-across gave 88 px and could not pass.)
**Emulator:**
- E1 The rule and its controls, both directions, every step read from `ring_since` a MARK (`adb shell date +%s%3N`) taken just
  before its action, so no step is satisfied by an earlier step's line (C-20): after provisioning, diagnostics carry `[fluent]
  acrylic=on reason=none`; `battery_saver_on` (C-18; its `low_power` = 1 assertion is the precondition; its `BS_MARK` is taken
  immediately before its `cmd power set-mode 1`, T13-9) → the slice from `BS_MARK` holds `[fluent] acrylic=off
  reason=battery-saver` with `wall=` − `BS_MARK` ≤ 1000 and the app-list backdrop (E2's fixture,
  app list showing) reads the un-blurred form (edges sharp, pixels = 0.2 × checker ± 3); MARK, `battery_saver_off` → the slice holds
  `acrylic=on` and the blurred form again; MARK, toggle off → `acrylic=off reason=setting` with `wall=` − MARK ≤ 1000, same look as
  under battery saver; MARK, toggle on → `acrylic=on`; MARK, `settings put global disable_window_blurs 1` → the slice holds no
  `acrylic=off` line and the app list stays blurred (the switch gates
  cross-window blur only; recorded for phase 04); `settings delete global disable_window_blurs`. Preset sub-row (T13-4; written
  now, run by phase 12's build session as one of the specific tests its presets touch, because phase 12 builds after this
  phase, T12-3, T13-23): MARK, tap `preset:Midnight` (phase 12's presets
  page) → the slice holds `[fluent] acrylic=off reason=setting` and `run-as app.tileshell cat shared_prefs/start_theme.xml` holds
  `transparency_effects` false; MARK, `preset:Default` → the slice holds `acrylic=on reason=none`.
- E2 The backdrop, not the element, is blurred (static source, app list): checkerboard set, swipe to the app list, screencap.
  In the app-list region between the drawn bars, in a horizontal strip through a letter-group gap (no text; strip found from
  the dump's `applist_*` bounds), the edge spread is the blurred width and the pixel values (column means) are 0.2 × (blurred checker) ± 3
  (T = black, α = 0.8: white-square centres ≈ 50, black ≈ 1; T13-12); in the same capture the "A" letter header's
  glyph edge and an app-name row's text edge are sharp; a screencap of Start (page 0) shows the checker's edges sharp (the
  wallpaper is not blurred in place); `acrylic:applist` is in the app-list dump with the page's bounds. Acrylic off (E1's
  control): edges sharp, pixels = 0.2 × checker ± 3, `acrylic:applist` still present (the same surface, its fallback form).
  A second pass at `wm size 720x1560` (2 px/epx): the blurred width scales to r = 60 px's value ± 20 %; `wm size reset`.
- E3 The live source keeps the measured fill in its own setup and blurs a bright backdrop (reminder menu). Setup (T13-21):
  Dark theme; `qa/phase-13/scripts/reminders_fixture.sh` (not e15.sh, which makes two reminders and no photo) makes, through the
  product path with `lib.sh` `type_request` and `type_request yes`, "remind me tomorrow at 9 am to QA13 tomorrow" and "remind me to
  QA13 photo" (Whenever), then attaches to the Whenever one, on its page's "Add a photo", through the system photo picker, a
  generated 1080 × 1080 black / white split PNG pushed to `/sdcard/Pictures/qa13-split.png` and media-scanned (phase 11 EDGE's X5
  picker route); it asserts both rows and the photo node before any sample. Long-press the tomorrow row: the menu's interior, a
  10 × 10 px patch clear of text placed by the measured-fill precondition (preamble), reads (40,40,40) ± 2 and its border (71,76,70)
  ± 2 — E15's values unchanged. Then long-press the photo row at the photo's horizontal centre, 10 px above its bottom, so the menu
  overlaps the photo (dump bounds of `acrylic:reminder_menu` intersect the photo's node, asserted before sampling): a profile across
  the photo's black / white edge inside the overlap (column means) equals 0.8·(47,45,47) + 0.2·B ± 4 (B from `acrylic_expect.py`
  over the Reminders page captured after the menu is dismissed by a tap outside it, with the held row's (63,68,64) asserted in that
  capture — never a capture from before the long-press; T13-2, T13-17), while the same edge outside the menu is sharp; acrylic off:
  the overlap reads (40,40,40) ± 2. Restore: delete both reminders (their menus' Delete) and `rm` the pushed picture.
- E4 H21 band and the Music menus: checkerboard set, app list, long-press a row (`input swipe x y x y 1000`): inside
  `applist_menu` clear of its item text the patch means and column-mean profiles equal 0.8·(0,0,0) + 0.2·B = 0.2·B ± 4 (B from
  `acrylic_expect.py` over the app list captured with the menu closed — itself acrylic, so the band blurs it once more, per the edge
  case below; T13-2, T13-12), and the band's item text is sharp; acrylic off: (0,0,0) ± 1 (H21's band as built). Music (re-cut
  2026-09-25 by T13-14: album rows take no hold and song rows carry no art): on the playlists pivot, with three playlists made by
  MUSIC8's steps, hold the first playlist row → `music_menu` hangs over the next row's accent square (the square's dump bounds
  intersect `music_menu`'s, asserted); under the band a profile across that square's edge equals 0.2·B ± 4 (B from
  `acrylic_expect.py` over the pivot captured with the menu closed); the held row's own square, outside the band, is sharp; acrylic
  off: the theme background ± 1; delete the three playlists. Now-playing's `•••` band (T13-19): MUSIC7's setup, open `•••`:
  `acrylic:music_menu` is in the dump with `music_menu`'s bounds, the slice holds `[fluent] music_menu source=live tint=(0,0,0)
  alpha=0.8 blur=30epx`, the band's patch means equal 0.2·B ± 4 (B over now-playing with the band closed); acrylic off: the theme
  background ± 1. Restore the background key.
- E5 The ≡ pane: E15's pane measurement setup (opened over Cortana's Home page) reads (14,19,13) ± 2 inside the pane clear of text,
  in a patch placed by the measured-fill precondition (the idle persona ring lies under the pane, so not every patch qualifies;
  T13-17); opened over the Reminders page with rows, a patch over a row's white title reads 0.8·(18,24,16) + 0.2·B ± 4 (B from
  `acrylic_expect.py` over the Reminders page captured with the pane closed, T13-2) and never (14,19,13); the pane over the
  Reminders page's empty area reads (17,23,15) ± 2 (the table's note; precondition patch); acrylic off: (14,19,13) ± 1 everywhere.
  Live sub-step (T13-24): Tess listening on Home (`cortana_assist`, no utterance; the persona pulses, period 1.04 s, R6 §3.1, and its
  bounds lie under the 256-epx pane — both asserted), tap the ≡ (`cortana_menu_button`): two captures 200 ms apart differ inside
  the pane over the persona (patch-mean |Δ| ≥ 2 levels in at least one channel), so the backdrop is re-recorded, not frozen at open;
  acrylic off, the same two captures are identical inside the pane (± 0).
- E6 Noise: in a 40 × 40 px patch of any acrylic surface over a flat backdrop (E5's pane over the Home page's black, the patch
  placed by the measured-fill precondition, clear of the persona and of text; T13-17), the
  per-pixel standard deviation is between 1.5 and 4 levels and no two adjacent frames differ in the patch (deterministic
  noise: two screencaps 1 s apart are pixel-identical); acrylic off: standard deviation 0.
- E7 The two lights on touch (Q3 B; T13-1's values and sample rule; re-cut 2026-09-25 by T13-13 to the held looks the built items
  have, with T11-31 applied). For each item: capture U (surface open, item unpressed); `input motionevent DOWN` at the stated
  touch point, capture D; `MOVE` +60 px (0.5 r) in x, still inside the item, capture M; `MOVE` off the item, capture A; `UP` outside
  the item, which runs nothing (asserted per item). P_p = pixel p's held look without the lights: U_p for items that draw nothing
  while held, U_p + 0.15·(255 − U_p) for the app-list and Music menu items (their `ROW_PRESS_ALPHA` fill); single pixels are
  compared against these same-pixel references (the preamble's E7 exception), so the material's deterministic noise cancels. In
  D: the 3-px ring, where it lies ≥ r + 2 epx (126 px) from the touch point, reads P + 0.30·(255 − P) ± 4; the touch point
  P + 0.10·(255 − P) ± 4; the stated 0.5 r point P + 0.05·(255 − P) ± 4; the stated far point (≥ r + 2 epx away, clear of text and
  ring) P ± 2. In M: the new touch point reads P + 0.10·(255 − P) ± 4 and the old one P + 0.05·(255 − P) ± 4 (the light followed
  the finger). In A: every sampled pixel reads P ± 2 (the press ended when the pointer left the item). Items:
  (a) reminder menu (E3's fixture; long-press the tomorrow row): touch the `reminder_menu_complete` item on its centre line at x =
  the item's centre (clear of its text), far point 200 px right of it; the built item draws nothing while held — its (79,84,80) is
  drawn only after a choice (R7 §3.6.6) — so P = U; after `UP` the reminder is neither completed nor deleted (its row is still in
  the dump). (b) ≡ pane over Home: touch `cortana_pane_item_home` at its centre; it is the current item, so the pane stays open
  (R7 §3.1.11) and P = U = the accent (any other item closes the pane at touch-down, `CortanaModel.kt:448-453`); after `UP`
  `cortana_pane` is still in the dump. (c) Phase 11's satellite (the Seeding exception: phase 11's baseline and fixtures): phase 11
  E5's press-feedback sub-step and E7's corner-case held capture, their phase-13 clauses exactly — touch (left + 10, top + 10) px of
  `quick_sat:0`'s `rest=` bounds, never its centre (T13-8); the 0.5 r point (left + 70, top + 10) px along the top edge, outside
  the centred glyph box and clear of the ring (T11-31); the far point 12 px inside the bottom-right corner (≈ 200 px away); the ring
  on the right and bottom edges; P = U (rest pixels; no Q6 style under `press_tilt` or `press_p4`, T11-7, the sub-step run once
  under each and ending on `press_none`); the MOVE is +60 px along the top edge, so M's new touch point IS the (left + 70,
  top + 10) point; `MOVE` off every satellite's square and label, then `UP` → nothing runs and the burst stays; the held tile and
  `quick_sat:1..3` equal U ± 1. (d) `applist_menu_pin` (E4's app-list setup): touch at the band's horizontal centre on the item's
  centre line; after `UP` the app is not pinned (no `pin to Start` line in the slice). (e) `music_menu_new` (E4's playlists setup):
  touch at the band's horizontal centre on the item's centre line; after `UP` no playlist was made (the pivot's playlist count is
  unchanged). Acrylic off (E1's control), the same five presses: D = P ± 2 at every sampled pixel, the touch point included (one
  tolerance with phase 11 E5, T11-31). Controls unchanged: a Start tile pressed with the press style None shows zero pixel change
  in the tile region (phase 01 E10's check); a Settings row pressed shows X19's flat 15 % white and neither light (a 10-px patch on
  the row's edge equals its interior).
- E8 Measured motion holds with acrylic on, on the shell's `[motion]` clock (C-5; T13-7): `[motion] cortana_pane` reads settle
  250 ± 17 ms after its t0 (R7 §3.1.10), `[motion] reminder_menu` settle 233 ms + one frame after its half-height first frame
  (R7 §3.6.4), `[motion] pivot` settle 250 ms ± one frame (X13), and `[motion] applist_menu`, written by `MotionClock.jump` from the hold's 783-ms uptime to the band's first frame, settle
  ≤ 33.4 ms (its first frame is at full height; no motion added; T13-27); every one of those lines reads `maxGapMs` ≤ 33.4 ms (C-31), each read from the ring slice after a MARK
  taken just before its open / swipe (C-20). A 60-fps screenrecord of each (`show_touches 1`, restored to 0) corroborates under phase 05's
  frame-spacing rule and is not the clock.
- E9 Frame cost on the AVD, recorded not gated (C-13 through `lib.sh` `record`, C-26 — never an assert; the row ends `E9: recorded
  only (<n> facts)`): `dumpsys gfxinfo app.tileshell reset`;
  open and close the reminder menu 20 times and swipe Start ↔ app list 20 times with live tiles flipping; `dumpsys gfxinfo
  app.tileshell` janky-frame % and the 99th percentile each written with `record` (host GPU; the phone's P2 is the bounded row).
- E10 Persistence and memory: the toggle survives `am force-stop` (Settings dump after reopen shows its state). Memory (re-cut
  2026-09-25 by T13-16; the pager precomposes the neighbour page and the static layer is built with Start, T13-15): three states in
  ONE process, never force-stopped between them, both pages warmed in each (swipe to the app list and back), each figure the median
  of three `dumpsys meminfo app.tileshell` total-PSS samples 1 s apart — A: no picture; B: the checkerboard set with acrylic OFF
  (the toggle); C: acrylic ON, after its `static backdrop rebuilt` line. C − B ≤ 14 MB (one screen layer + noise); after "Remove
  picture" (`theme_background_remove`) and the same warm-up, PSS is within 2 MB of A.
- E11 Regression, the specific sub-steps this phase's changes touch (the 2026-09-25 QA ruling; re-cut by T13-23): phase 03
  E15's pane and reminder-menu fill, geometry and selection sub-steps — run the first time if necessary, since INDEX row 03 lists
  E15 NOT RUN, so its driver `qa/phase-03/scripts/e15.sh` is a dependency of E11 (T13-3) — with their numbers; phase 02 E2's
  hold-menu display and Pin action; phase 10 MUSIC8's menu open, selection and dismissal; phase 01 E12's swipe / search / jump-grid
  sub-steps and E19's app-list bar checks; each passing with its own numbers on this build (the Q1 A promise), affected motion on
  the shell's clock (E8); `qa/phase-03/scripts/exported.py` against `qa/phase-03/exported-allowlist.txt` reports no new exported
  component. Whole gates stay Jeremy's end-of-project run.
- E12 Diagnostics (re-cut 2026-09-25 by T13-22): from the SAVED, action-scoped `ring_since MARK` slices of E1–E5's runs on the same
  APK (saved before every force-stop, fixture rewrite or layout restore; the ring is per process), each surface's show action
  holds its own `[fluent] <surface> source=… tint=… alpha=0.8 blur=30epx` line, and E1's reasons appear in its saved step order; a
  missing slice fails the row, and nothing is read from an unsliced ring or an earlier build's evidence.
- E13 Static backdrop failure (T13-10): with the checkerboard set as the Start background (the fixture route above), `adb shell rm
  /sdcard/Android/data/app.tileshell/files/qa/checker.png`, `ring_save`, MARK, `am force-stop app.tileshell` + Home (so no cached
  layer survives; the new process builds the layer at start, T13-15, so its failure may be stamped before the swipe), swipe to the
  app list: the new process's slice from MARK holds `[fluent] static backdrop failed for <the checker's file:// URI>: <why> (fallback)` and no
  `static backdrop rebuilt` line, and the app-list region reads the fallback form with no picture — the solid theme background
  (0,0,0) ± 2 in E2's strip, no checker edge anywhere in it (after the restart no decode of the picture exists anywhere, so this is
  the no-image fallback; without the restart the static source may still build from Start's in-memory decode, which would make the
  row unable to fail); restore by removing the `background` key (the fixture's restore).

**Phone-only (S25 Ultra):**
- P1 R4's cross-window blur results (INDEX R4 row), attached as information for phase 04 when they exist; phase 13 has no
  `reason=unsupported` branch and waits for nothing on the phone (T13-29).
- P2 Frame pacing and memory on the 1440 × 3120 panel: E9's script with `dumpsys display` showing the same mode before and
  after; janky ≤ 5 % and 99th percentile ≤ 2 vsync periods (phase 01 P4's thresholds); launcher PSS ≤ phase 03 P4's baseline
  + 30 MB with the app-list layer built.
- P3 Samsung Power saving on and off turns acrylic off and on (`[fluent]` lines), run over Wireless debugging with the phone
  unplugged, or with the charging state and One UI's behaviour written with `record` (C-18: a phone on USB is powered, as the AVD
  is), and One UI's Accessibility > Visibility
  enhancements > "Reduce transparency and blur" is probed: `settings list global/system/secure` diffed across the toggle;
  if a key changes, it is recorded here and H8 asks whether the shell should follow it (an ADD to the rule); if none does,
  recorded as not readable.
- P4 In-app acrylic looks as on the AVD (screencaps of E2–E5's surfaces attached for H1).

**NEEDS-HUMAN** ("accept" rows unless marked):
- H1 The acrylic look on every surface in the table (Fluent design, no W10M original).
- H2 Blur radius 30 epx, tint opacity 0.8, 2 % noise, no exclusion layer (approximations from Fluent's desktop recipe).
- H3 Fidelity row (R3 A18, MEDIUM): the app list's wallpaper-through-a-dark-layer form — blurred with acrylic on, unblurred
  off — against A18's sample; and the wallpaper moving with the pivot page (approximation).
- H4 The two lights on press (Q3 B; approximation of Fluent's touch Reveal): the 1-epx border ring and the radial light —
  r = 40 epx, alpha 0.10 falling to 0, following the finger — on menu rows, pane items and phase 11's satellites; and that tiles
  and plain pages have neither.
- H5 The "Transparency effects" toggle: wording, placement, default On.
- H6 Acrylic turning off under battery saver (Windows' rule).
- H7 Any surface the derivation leaves solid (none in this phase; phase 04's action center is its own interview).
- H8 Whether the shell should follow One UI's "Reduce transparency and blur" (P3, phone).
- H9 The feel with acrylic on the phone (P2's numbers, P4's captures).

## Edge cases
- Battery saver toggled while a menu is open (`battery_saver_on`, C-18, with the reminder menu showing): the surface redraws to its
  fallback in the next frame — in the slice from the helper's `BS_MARK`, the `[fluent] reminder_menu form=fallback` line's `wall=`
  − the `[fluent] acrylic=off reason=battery-saver` line's `wall=` ≤ 34 ms (2 frames; T13-9), a screencap reading (40,40,40) only
  corroborating (a host screencap cannot be placed within 100 ms); toggled back (MARK, `battery_saver_off`): the slice holds
  `[fluent] reminder_menu form=acrylic` and the menu is still open (`acrylic:reminder_menu` in the dump).
- The Start background's file deleted under the shell: the fallback and `[fluent] static backdrop failed for <uri>: <why>
  (fallback)` (E13, T13-10).
- The Transparency toggle changed while the app list shows (Settings opened over it and Back): the backdrop switches form.
- Start background removed or changed while the app list shows: the static layer is rebuilt or dropped (`[fluent] static
  backdrop rebuilt` / no line and the theme background shown), no stale blur (screencap).
- A huge background image (8000 × 8000 push): the pre-blur runs on the sampled decode at screen size — launcher pid unchanged,
  PSS within E10's C bound (C − B ≤ 14 MB).
- No background image set: solid theme background on the app list, no static layer (E10's memory figure unchanged).
- Light theme with a bright wallpaper: the app list stays readable (tint white at 0.8; screencap for H1).
- Edit mode: the pivot is locked, so the app list cannot show; entering and leaving edit mode does not rebuild the static
  layer (no `rebuilt` line).
- ~~The live source over flipping tiles (the burst, phase 11): dumps through RV13's route; the layer records every frame —
  E9's numbers cover it.~~ Dropped 2026-09-23 (T11-8): no live source runs over the burst.
- A surface over another acrylic surface (the H21 band over the acrylic app list): the live layer records the app list
  including its blurred backdrop, and the band blurs that again — 0.8·T + 0.2·blur(app list), exactly as E4's B computes; no
  special path (T13-12).
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
- A rapid open / close of a menu (10 holds in 15 s): no leaked live layers (PSS, measured as E10's C, returns to within 2 MB of C).
- The app list with 300+ packages (phase 01's edge): the backdrop is one layer whatever the list's length; scroll frame times
  in E9's capture.

## QA evidence
