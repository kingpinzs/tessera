---
phase: 11
slug: tile-quick-actions
status: FINAL   # 2026-09-23 after round 3 (review/2026-09-23-phase11-r3-triage.md); changes after FINAL go through INDEX.md Change Log
depends-on: [01, 02, 03, 05, 10]   # 03: lib.sh, shell:cortana, E5 re-run; 05: the dump route; 10: Music's shortcuts (T11-42)
---

# Phase 11 — Tile quick actions: the burst of App Shortcut satellites at the 783-ms hold

## Goal
Holding a Start tile for 783 ms enters edit mode exactly as phase 02 built and measured it (R6 §1.1.1: the contraction, the dim,
the held tile's discs) and, in the same frame, up to four "satellite" tiles spring outward from the held tile, one per Android App
Shortcut the held app publishes (`LauncherApps.getShortcuts`, which the HOME app may call). Letting go leaves both on screen: a tap
on a satellite runs that shortcut, a drag moves the tile and closes the burst, a tap elsewhere closes the burst and leaves edit mode
on. The satellites track the held tile's `boundsInRoot` every frame, so they ride the entry contraction and a scroll (a resize
closes the burst, Q2 A — T11-17).
Nothing about the hold, the drag or any phase 02 number changes; the burst is ADDED on top. A tile whose app publishes no shortcut
(a folder, a shell tile with none) gets edit mode and no burst, and says why in diagnostics.

## Scope
**In:** the burst (state, geometry, motion, drawing) in Start's own composition root; the shortcut source (query, selection rule,
profile and host checks, change callback); satellite taps (`startShortcut`) and every dismissal; the touch-slop fork between
hold-and-release and hold-and-drag; the shell's own apps' static shortcut declarations (per interview Q1); diagnostics lines and
test tags; the QA fixture APKs (tileclient-a / tileclient-b gain shortcuts) and the phase 02 regression re-runs this change owes.
**Out (explicitly):** the hold threshold, the drag, the discs and every R6 §1 value (phase 02, FINAL, unchanged); the app list's and
Music's hold menus (they keep hold-and-release = menu on the same `Edit.HOLD_MS`); an "Edit" satellite (gone with the re-ruling);
pinned shortcuts (the shell pins none); shortcuts on the bottom tile row's or the promoted tile's OWN rows beyond what the hold
already does there; Fluent acrylic on the burst (it has no backdrop: edit mode's dim is it) — the satellites' touch lights are
phase 13's ADD, not built here (T11-7, Decisions); pen hover or a
swipe-on-tile trigger (R10 item 10's alternatives, superseded by R10-Q3); no network (shortcuts are local; offline preferred, A11
as amended 2026-09-23 — re-worded 2026-09-23 by r2 triage T11-11).

## Decisions
- 2026-09-22: Interview Q2 — a disc is not "elsewhere" (Jeremy: "A"). While the burst is open, one tap on the held tile's unpin
  or resize disc acts at once (unpin removes the tile and the burst with it; resize changes the size) and the burst closes. This
  confirms the agent reading already written in the "Taps and events while a burst is open" line below; H6 judges the feel.
- 2026-09-22: Interview Q1 — the shell's own tiles burst (Jeremy: "A"). Music declares Songs / Albums / Artists / Playlists
  and Start settings declares Start + theme / Tile apps / Setup / Diagnostics as static App Shortcuts, each opening that page
  through an intent extra (ADDs to phases 10 and 01, each recorded in the INDEX Change Log when built). STANDING RULE: every
  shell app to come (phases 15-19) declares its own top-level screens the same way, and each of those phase docs carries the
  rule. Weather (one screen) and Tess (pages inside the voice session) declare none and get edit mode with no burst, logged.
- 2026-09-22: Scope add (Jeremy, PLAN.md): tile quick actions from Android App Shortcuts are in. The motion is Jeremy's ruling,
  verbatim: "boundsInRoot coordinate tracking" and "Spring animation (dampingRatio = 0.65f) shooting 4 satellite action tiles
  outward." No W10M original exists (the secondary spec's "MixView" was a cancelled concept and the name is unverified, R10 item 10),
  so these values are Jeremy's own, not a measurement, and no NEEDS-HUMAN row can be closed against footage (Jeremy)
- 2026-09-22: R10-Q3 re-ruled (Jeremy: "lets go with A"), superseding the first R10-Q3 entry, which was put to him on a wrong premise
  (that a hold enters edit mode on release; R6 §1.1.1 and the built `EditGestures` enter it at the 783-ms timeout with the finger
  down). PLAN.md's wording governs: "at the 783-ms hold, edit mode starts EXACTLY as measured (R6 §1.1.1: the contraction, the dim,
  the held tile's discs) and the burst of quick actions appears around the held tile at the same moment. Letting go leaves both: a
  tap on a satellite runs it, a drag moves the tile (the burst closes), a tap elsewhere closes the burst and edit mode stays. All
  four satellites are App Shortcuts; there is no 'Edit' satellite, because edit mode has already begun. Every measured R6 value and
  every phase 02 proof stands — the burst is ADDED on top of the hold, nothing about the hold changes." (Jeremy)
- 2026-09-22: Data source (R10 item 10, carried into the scope add): the actions are Android App Shortcuts read with
  `LauncherApps.getShortcuts`, which only a shortcut host may call — the HOME role holder and (found 2026-09-23 at build) the
  assistant / voice-interaction holder too; the shell is both. Launch is `LauncherApps.startShortcut`.
  No network (shortcuts are local; offline preferred, A11 as amended 2026-09-23 — re-worded 2026-09-23 by T11-11) (agent, R10).
  Added 2026-09-23 (agent, r3 triage T11-32): the source runs OFF the main thread (`Dispatchers.IO`, icons decoded there too). It
  starts at the DOWN of every press on a tile outside edit mode (the hold race, `EditGestures.kt:74-79`) and is cancelled when the
  press ends before 783 ms; at 783 ms edit mode enters without waiting for it; the burst opens in the first frame its result is in
  hand (normally the entry's first changed frame) and `[quick] motion open`'s t0 is that frame. Reason: binder calls and icon
  decodes on the frame phase 02 E7 measures would move that measured frame
- 2026-09-22: Phase 02 is amended by this ruling and the INDEX Change Log line is already written (2026-09-22, "PHASE 02 IS AMENDED
  BY R10-Q3"): the hold is unchanged, phase 02's E1 / E7 / E8 keep their meaning and numbers, and only the recorded rationale "Start
  tiles have NO long-press menu" goes stale. This phase owns the burst's rows and must land before phase 02's end-of-build re-run
  (R10 triage, agent)
- 2026-09-22: Order (R10 triage, agent): first of the 2026-09-22 additions, because it changes shipped gesture code that phase 02's
  deferred re-runs will re-verify, and its fixture is cheap
- 2026-09-22: Where the burst lives (agent; design 21 and the phase 02 gate's lesson): the satellites are drawn in `StartPage`'s own
  root (the `start_page` Box), above the grid, the promoted row, the bottom tile row and an expanded band — never in a Popup or
  Dialog, whose window root would make `boundsInRoot` a different coordinate system. The layer is DRAW-ONLY. Touches are decided
  where every edit-mode touch is already decided: `EditGestures.hitTest` gains a satellite hit, tested before the discs while a
  burst is open, and acts on the release like a disc (`waitForUpConsuming`, then act only if the finger lifted on the same
  satellite). This mirrors `TileControls`' rule — decide once, in the handler that owns the tile's touches — and avoids the sibling
  layer that swallowed tile taps in phase 02's gate. A satellite's hit rectangle is its square plus its label's box, so a tap on
  the label counts as the satellite (added 2026-09-23, r3 triage T11-26)
- 2026-09-22: Tracking (Jeremy's "boundsInRoot coordinate tracking"; agent mechanics): the held tile reports its `boundsInRoot` from
  `onGloballyPositioned` every frame it moves, and each satellite's rest rectangle is computed from those bounds, so the entry
  contraction (R6 §1.1.3), an edit-mode scroll and the promotion drop all carry the burst with no second code path (a resize
  closes the burst, Q2 A — re-cut 2026-09-23 by r3 triage T11-17). Added 2026-09-23 (T11-41): the tracked rectangle is the held
  tile's slot as `StartPage` places it, read OUTSIDE `TileView`'s own face layer, so a live flip or a press never moves a
  satellite; when the tile's node leaves the composition (a drag starts, an unpin, a removal) the close runs back to its last
  reported centre; the one-frame trail of bounds copied from `onGloballyPositioned` is accepted (H1). Build-start check, recorded here: that a held tile's
  `boundsInRoot()` under the contraction's `graphicsLayer` equals its drawn bounds (the dump's, which phase 02's gate showed carry
  the transform); if it does not, the satellites anchor to the drawn bounds computed with the same numbers `StartPage` draws with,
  as `qa/phase-02/scripts/gestures.sh` `edit_point` already does
- 2026-09-22: Satellite (P4 design, agent; H3): a square one small-tile unit on a side (R3 A1; 164 px on this AVD, as RECENT0922
  measured) with the tile fill Start tiles use (accent plus the X5 transparency setting), the shortcut's icon centred at the small
  tile's glyph size and drawn by phase 01's glyph rule extended to shortcuts (H38: the monochrome layer of an adaptive icon tinted
  white where `getShortcutIconDrawable` returns one, else the full-colour icon), and the shortcut's `getShortLabel()` in the 12-epx
  caption class, in the theme's text colour (`ShellColors.text`: white on Dark, black on Light — the label sits on the dimmed
  page, not on the tile, as the discs take the theme foreground, R6 §1.2.4; re-cut 2026-09-23 from "white" by r3 triage T11-25),
  one line ellipsised at the satellite's width plus HALF a gutter each side (so neighbours one gutter apart in the line
  arrangement never overlap; T11-26), drawn OUTSIDE the satellite on its
  outer side (above the top pair, below the bottom pair; above a line above the tile, below a line below it — T11-27) so no label
  lands on the held tile.
  SUPERSEDED 2026-09-23 by T11-7 (last Decisions lines): "A satellite's pressed look follows the Q6 press setting like any Start
  tile; whether Fluent's touch light is ADDed to satellites is phase 13's decision (design 4)." The burst has no backdrop of its
  own: edit mode's measured dim (R6 §1.1.5–1.1.6) is the backdrop,
  and no acrylic applies (satellites are square tiles, which R10-Q1 keeps as measured)
- 2026-09-22: Arrangement (P4 design, agent; H4): CORNER for grid tiles and expanded-band members — satellite i sits diagonally
  outside corner i in the order top-left, top-right, bottom-left, bottom-right, its inner corner 16 epx out from the tile's corner on
  both axes; 16 epx is chosen so the two 31-epx edit discs centred on the right-hand corners (R6 §1.2.1–1.2.3, radius 15.5 epx) never
  overlap a satellite. LINE for tiles in the bottom tile row — four satellites in one row above the tile, centred on it, one grid
  gutter above its top edge and one gutter apart, labels above. Clamping: a satellite whose rest rectangle plus label would leave
  the page area (above the bottom edge of phase 01's drawn status bar, `BarMetrics.STATUS_EPX` — C-17, not a literal 28; below the
  bottom tile row's top minus one gutter; past the grid's left or right margin, `StartGrid.leftMarginPx` / `rightMarginPx` —
  re-cut 2026-09-23 from "the screen's left or right edge" by r3 triage T11-27) is moved inward along the offending axis until it
  fits, and may then overlap dimmed neighbours (the
  burst draws above them) but never the held tile; when a corner satellite cannot avoid the held tile (a tile at the grid's top-left corner; a WIDE
  tile in the 2-column grid's first row — corrected 2026-09-23 at build: a full-width WIDE tile further down keeps a clamped
  corner, its satellites slid in to the margins above and below it, never on it; INDEX Change Log), the whole burst takes the line arrangement above the tile, or below it
  when above does not fit (a line below draws its labels below the satellites). Fewer than four shortcuts fill the slots in that
  same order, first k (H5). Added 2026-09-23 (agent, T11-27): the arrangement and its clamping are decided ONCE, when the burst
  opens, against the held tile's edit-mode rest rectangle (its centre contracted to 0.90 about R6 §1.1.3's fixed point, its size
  unchanged — the numbers `StartPage` draws with); afterwards each satellite keeps its offset from the tracked tile and rides it
  through the contraction and a scroll, and the page edge and the drawn bars cover a satellite carried past them as they cover the
  tile — a burst never re-clamps or switches arrangement mid-motion (it would jump with no spring). A held grid or band tile whose rest
  rectangle lies wholly outside the page area opens no burst (`off screen`; a bottom-row tile is always on screen — it lies below
  the clamp area by design, found by E7 at build 2026-09-23); since J4 only the promoted tile's grid cell can be
  there
- 2026-09-22: Motion (Jeremy: dampingRatio 0.65, four satellites; stiffness and the rest agent, H2): each satellite's centre starts
  at the held tile's centre and moves to its rest centre on one Compose `spring(dampingRatio = 0.65f, stiffness = 1500f)`
  (`Spring.StiffnessMedium`) driving the travel fraction 0 → 1; scale 0.5 → 1 and alpha 0 → 1 ride the first half of the travel
  (alpha = min(1, 2·progress)); all four start in the first changed frame of the entry (R6 §1.1.1) — precisely, the first frame the shortcut source's result is
  in hand, which is normally that frame (T11-32) — no stagger. Why medium: R6 §1.1.8's
  entry settles in 417 ms, and a satellite still bouncing after the grid has stopped reads as a second event; StiffnessLow (200)
  peaks at ≈290 ms, after the grid settles; StiffnessHigh (10000) peaks in ≈41 ms, under three frames, which RV11 cannot measure here.
  The fingerprint a recording can check (testability 6): first overshoot exp(−πζ/√(1−ζ²)) ≈ 6.8 % of the travel, peaking π/ωd ≈ 107 ms
  after the first frame (ωd = √k·√(1−ζ²) ≈ 29.4 rad/s), and settled — every later frame within 1 px of rest — at ≈ 249 ms for the
  fixture MEDIUM tile's ≈ 427-px diagonal travel (a longer travel settles later: WIDE ≈ 257 ms; re-cut 2026-09-23 from "within 1 px
  of rest by 250 ms" by r3 triage T11-29, since the spring passes through rest at 77 and 184 ms before it settles). Close: the same
  spring run back to the tile's centre with alpha reaching 0 at the halfway point — the travel fraction passes 0.5 at 36.0 ms, so the
  satellites are gone ≈ 36 ms after the close starts (T11-29); on a satellite launch the burst vanishes in the Start exit's first frame (H10).
  The values stand; since 2026-09-23 the clock that measures them is the shell's own `[quick] motion` line, never a screenrecord
  (T11-5, last Decisions lines), and that line also reports `frames=<n> maxGapMs=<ms>` so jank fails on the same clock (C-31)
- 2026-09-22: SUPERSEDED 2026-09-23 in its query by the per-activity line (last Decisions line; r2 triage T11-12): the query also
  carries `setActivity(the tile's component)`; the order, the `isEnabled` filter and the cap of four below stand.
  Selection rule (agent, Android's own launcher convention; testability 7): query `ShortcutQuery().setPackage(pkg)
  .setQueryFlags(FLAG_MATCH_MANIFEST or FLAG_MATCH_DYNAMIC)` under the tile's own `AppEntry.user`; keep `isEnabled`; order manifest
  before dynamic, then `getRank` ascending, then id; take the first four. Pinned shortcuts are not queried (the shell pins none).
  The rule is a pure function with JVM tests, and E3 asserts the EXACT ids it predicts. Since 2026-09-23 "manifest before dynamic"
  is a stated choice, not an assumption: a screen that exists only conditionally is a dynamic shortcut and ranks after the fixed
  ones (C-9, last Decisions lines)
- 2026-09-22: No burst, with the reason in diagnostics (agent; the re-ruling removed the "Edit alone" fallback): a folder tile; an
  app with no enabled shortcut; the shell not the HOME holder (`hasShortcutHostPermission()` false — checked before the query, so
  the `SecurityException` never fires); a profile in quiet mode or a locked private space (`AppCatalog.isQuietMode`); any exception
  from the query. In every case edit mode enters as measured and one line `[quick] no burst on <tileId>: <reason>` is written.
  The reasons, exactly (added 2026-09-23 by r3 triage T11-21 / T11-22 / T11-27): `folder` · `no app` (an Unassigned slot tile) ·
  `secondary tile` · `off screen` · `not the shortcut host` · `profile quiet` (a quiet work profile and a locked private space
  alike) · `no shortcuts` · `query failed <exception>`
- 2026-09-22: Work and private profiles (agent; testability 9): the query and the launch both use the tile's `AppEntry.user`, so a
  work-profile tile bursts with its profile's shortcuts and `startShortcut` runs them as that user; E9 proves it on a managed
  profile created for the row, because an empty list under the wrong user is indistinguishable from "no shortcuts"
- 2026-09-22: Satellite tap (agent; H7; re-cut 2026-09-23 by r3 triage T11-24 to the built launch path, `StartActivity.kt:244-282`):
  the burst closes (`launch`, vanishing in the Start exit's first frame), edit mode exits with R6 §1.5's exit, and the Start exit
  (R3 A11) plays as for a tile tap with `exitTappedId` = the held tile's id. `startShortcut(pkg, id, satelliteBounds, <launchApp's
  options bundle, the N-09 splash request>, user)` runs where a tile's launch runs — in `pendingLaunch`, after the exit's last
  frame — with `launchApp`'s bookkeeping: `UseCounts.record(heldTile)`, `pendingRecent` = the held tile's key (so
  `RecentApp.opened` applies in onResume, never at tap time: the tile must not leave the grid under the exit) and
  `returningFromLaunch`; on return Start is not in edit mode and the held tile is promoted. A shortcut removed, disabled or
  unstartable between open and tap (`ActivityNotFoundException` / `IllegalStateException` / `SecurityException`) launches nothing,
  logs `[quick] tap satellite <i> <pkg>/<id>: startShortcut failed <exception>`, clears `returningFromLaunch` and plays Start's
  entrance at once (as onResume does), so Start is never left on the exit's last frame and is not in edit mode.
  `LauncherApps.Callback.onShortcutsChanged` for the held package while a burst is open closes it (`[quick] burst closed: shortcuts
  changed`, or `removed` when the package is no longer installed — `AppCatalog.packageState` GONE, T11-17); the one callback
  lives in `AppCatalog`
- 2026-09-22: Taps and events while a burst is open (Jeremy's rule; agent reading of "elsewhere", H6): a satellite (or its label,
  T11-26) → runs it; a disc → the disc acts (unpin removes the tile and the burst with it, `unpin`; resize cycles the size, `resize`)
  and the burst closes — a disc is ON the held tile, so it is not read as "elsewhere" (Q2 A; re-cut 2026-09-23 by r3 triage T11-17,
  which struck "the satellites follow the new corners": Q2 A closes the burst on a resize); any other tap (another tile, the held
  tile, empty space, the folder-name strip) → closes the burst and does nothing else: edit mode stays, the selection stays, and the
  next such tap does what R6 §1.5.1 says; Back → closes the burst only, the next Back exits edit mode (R6 §4.1.7, H9 in phase 02);
  Home → `StartActivity`'s existing exit closes both — the HOME intent (`onNewIntent`) and the drawn Windows key (`nav_windows`)
  both reach the one `homeEvents` collector (`StartActivity.kt:112-122`, `:198-201`; T11-16); a press on empty space that moves
  scrolls Start with the burst tracking the tile; a press on the held tile that moves drags it and closes the burst (`[quick] burst
  closed: drag`); Start stopping (screen off, an incoming call, any window in front) closes the burst (`stop`); the held tile
  leaving the layout (its app uninstalled) closes it (`removed`); a live flip under the burst changes nothing. Close reasons,
  exactly: `tap elsewhere` · `drag` · `back` · `home` · `stop` · `unpin` · `resize` · `removed` · `launch` · `shortcuts changed`
- 2026-09-22: The Music tile's transport controls (agent; design 28): a hold on a control opens no burst and enters no edit mode —
  `TileControls` decides once that the control owns the press, and a hold-and-release on it fires the control as a tap does.
  Answered from the code 2026-09-23 (r3 triage T11-38; it was a build-start check): today a hold on a control DOES enter edit mode —
  `TileView.kt:304-308` consumes only the control's DOWN and UP, and `EditGestures`' not-in-edit branch (`:69-79`) never looks at the
  DOWN's consumption, so the hold race times out and `edit.enter` fires (and `TileView`'s `pointerInput`, keyed on `interactive`,
  restarts, losing the control's release). That is a phase 01/02 defect, fixed at the producer here: the not-in-edit branch returns
  when `down.isConsumed` (a DOWN the tile's own handler already took), recorded in the INDEX Change Log for phase 02 when built
- 2026-09-22: The promoted tile (agent; testability 10; re-cut 2026-09-23 by T11-2 — the split-time text said a hold on
  `recent_app_row` entered no edit mode, which was true only until J4, qa/JEREMY-QA.md): since J4 `EditGestures.hitTest` tests the
  promoted tile at its fixed place "like any other" tile (`start/EditGestures.kt:326-333`), and edit mode suspends the promotion,
  so a hold on the promoted tile enters edit mode, `recent_app_row` goes, the held tile shows in its own grid cell, and the burst
  opens there — the satellites track the tile to its grid cell through the tracking ruling, with no second code path. E16 proves
  it. Nothing here changes phase 01/02's part
- 2026-09-22: Touch-slop fork (agent; testability 11): the spatial fork is bracketed as the time fork is — a wobble under
  `ViewConfiguration.touchSlop` (8 dp, ≈22 px at 450 dpi) keeps the burst; a move past it is a drag and closes it. E2 drives both
- 2026-09-22: The app list's and Music's holds are unchanged (agent; testability 29): `AppListMenu` and `MusicCollectionPage` keep
  hold-and-release = menu on the same `Edit.HOLD_MS`; E11 is the control row so the re-cut cannot leak
- 2026-09-22: Harness contracts (agent; testability 24, 25, 31, 33, 34): the burst is inside `StartActivity`'s root, which already
  sets `testTagsAsResourceId`; test tags `quick_burst` (present only while open), `quick_sat:<i>` (0–3) on each satellite and
  `quick_sat_label:<i>` on the node that carries its label text; diagnostics `[quick] shortcuts for <pkg>/<activity>/<userId>: n (k
  shown: id,id,…)` (re-cut 2026-09-23 by T11-12 from `<pkg>/<userId>`, which read the same for two tiles of one package; `<pkg>/<activity>`
  is the tile component's `ComponentName.flattenToShortString()` — `app.tileshell/.music.MusicActivity`, and the full class where it lies
  outside the package, e.g. `app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity`), `[quick] burst on <tileId>: k
  satellites`, `[quick] no burst on <tileId>: <reason>`, `[quick] satellite <i> rest=[l,t,r,b]`
  (the second source RV13 needs under flipping tiles; written once per satellite each time the burst comes to rest — the first
  frame where the open spring has settled AND the held tile's tracked bounds equal the previous frame's — and again at each
  scroll's end, never per frame: r3 triage T11-28), `[quick] satellite <i> icon failed <pkg>/<id>: <why>` (added 2026-09-23,
  T11-14; `<why>` is `null drawable` when `getShortcutIconDrawable` returns null — as LauncherApps does for an icon URI that
  resolves to nothing — or the exception it threw, T11-33), `[quick] tap satellite <i> <pkg>/<id>: startShortcut ok|failed <exception>`,
  `[quick] burst closed: tap elsewhere|drag|back|home|stop|unpin|resize|removed|launch|shortcuts changed` (`resize`, `removed`
  added by T11-17). Rows are adb-driven on phase 03's
  `lib.sh` (symlinked as phase 01 did), evidence under `qa/phase-11/`. Added 2026-09-23: `[quick] motion open|close …` (T11-5), with
  `frames=<n> maxGapMs=<ms>` (C-31); the dump route, the seeding baseline, the force-stop-after-launch rule and the ring-slice rule
  live in the Acceptance preamble (T11-4, T11-3, C-6, C-20)
- 2026-09-22: Process, permissions, surface (agent; design 27): everything runs in the launcher process; no new permission; no new
  exported component, so phase 03 E5's allow-list is unchanged and its row is re-run as is
- 2026-09-22: NEEDS-HUMAN rows here are ACCEPT rows (testability 26): P4 design or Jeremy's own values, none judged against footage
- 2026-09-22: QA fixtures (agent; testability 8): `testapps/tileclient-a` gains `res/xml/shortcuts.xml` with FIVE static shortcuts
  (ids `qa_one`…`qa_five`, ranks 0–4, short labels "One"…"Five", each with one `android:icon`: an adaptive icon with a monochrome
  layer, so the glyph rule's monochrome branch runs — T11-31 / T11-33) all targeting a new `ShortcutActivity`
  (`app.tileshell.testclient.ShortcutActivity`, in the shared `testapps/common` sources and declared in tileclient-a's manifest;
  T11-47) that shows the `qa_id` extra it received in a TextView with the resource id `@+id/shortcut_id` (dump:
  `resource-id="app.tileshell.testclient.a:id/shortcut_id"`), so the row reads WHICH shortcut ran (`dumpsys activity`
  prints only "(has extras)", the phase 02 E5 lesson). Added 2026-09-23 (T11-13): tileclient-a declares `res/xml/shortcuts.xml` in
  the `<meta-data android:name="android.app.shortcuts">` of its LAUNCHER activity, `app.tileshell.testclient.VerbActivity`
  (`testapps/tileclient-a/src/main/AndroidManifest.xml:20-28`, the component `baseline_layout.json` pins); `ShortcutActivity` is only
  the shortcuts' target — under the per-activity query a declaration on `ShortcutActivity` yields 0 and E1 fails on a correct shell.
  `testapps/tileclient-b` publishes ONE dynamic shortcut (`qa_dyn`, "Dyn")
  through `ShortcutManager` on launch and disables it when started with the `disable` verb. Added 2026-09-23 (T11-14): started with
  the `badicon` verb it also publishes a second dynamic shortcut `qa_noicon` ("No icon", rank 1) whose icon is
  `Icon.createWithContentUri` of a URI that resolves to nothing (`content://app.tileshell.testclient.b.images/missing.png`), and the
  `reset` verb republishes `qa_dyn` alone — so E3's icon sub-step produces the icon-failed line while E3's and E8's one-shortcut
  states stay as written. Added 2026-09-23 by r3 triage: (T11-30) the three test APKs compile ONE shared `VerbActivity`, so the
  shortcut verbs run only in `app.tileshell.testclient.b` (a `packageName` check) and tileclient-a publishes no dynamic shortcut —
  otherwise a tileclient-a launch would attach `qa_dyn` to tileclient-a's launcher activity and E1 would read 6; `qa_dyn`
  (`Icon.createWithResource`, T11-33) is published by the `reset` verb, which Seeding runs once. (T11-15) tileclient-b declares an
  exported, UI-less `app.tileshell.testclient.ShortcutVerbReceiver` (tileclient-b's manifest only) that runs the same shortcut verbs
  — `disable` (`disableShortcuts(qa_dyn)`), `badicon`, `deadtarget`, `reset` — from `adb shell am broadcast -n
  app.tileshell.testclient.b/app.tileshell.testclient.ShortcutVerbReceiver --es verb <verb>`, so a verb sent with a burst open
  brings no window over Start. (T11-21) `deadtarget` adds `qa_dead` ("Dead", rank 1), whose intent is `ACTION_VIEW` to the
  explicit component `app.tileshell.testclient.b/app.tileshell.testclient.Missing`, declared nowhere, so `startShortcut` throws
  `ActivityNotFoundException`; `reset` removes it. AOSP's Open Camera / Auxio fixtures
  (when the stash is present) are extra evidence, never the only evidence. Since 2026-09-23 the two fixture tiles reach Start
  through `qa/phase-11/baseline_layout.json` and `layout_restore`, not a pin per row (T11-3, Acceptance preamble)
- 2026-09-23 (agent, review triage T11-5; C-5): the shell-logged motion clock is the standing rule. The burst logs `[quick] motion
  open <tileId>: t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` and `[quick] motion close <tileId>: t0=<uptime> alpha0=<ms>
  settle=<ms>` from `withFrameNanos` (t0 = the frame the four satellites first draw; peak / overshoot from the spring's own
  progress; settle = first frame within 1 px of rest, or gone); E6 asserts those numbers against RV11's tolerance, and a
  screenrecord only corroborates under phase 05's frame-spacing rule. Reason: the P02 lesson (qa/JEREMY-QA.md) — a variable-rate
  screenrecord cannot be the clock; the frame maths above already hold, only t's source changes
- 2026-09-23 (agent, review triage T11-7): a satellite takes phase 13's two lights (the 1-epx border ring and the radial light
  under the finger) and NOT the Q6 press style. Reason: a satellite is a transient-surface item — Jeremy's phase 13 Q3 B names
  "satellites" in the pressed-item set — while Q6 governs Start tiles, and one feedback reads as one thing, never two. So on this
  phase's build a pressed satellite shows no press feedback at all; from phase 13's build on it shows the two lights; with
  Transparency effects off or under battery saver it shows none (phase 13's lights obey that switch; W10M final's press default is
  None anyway). Satellites stay in phase 13's Reveal list. E5 / E7 carry the DOWN capture, H3 the look
- 2026-09-23 (agent, review triage T11-10): phase 19 KEEPS this doc's Q1 set for the Settings tile's satellites — Start + theme /
  Tile apps / Setup / Diagnostics — and phase 19's agent line naming its categories (System, Personalisation, Network & wireless,
  Apps) is withdrawn. Reason: the four are Jeremy's ruling and the pages people open; a satellite that lands on a category is one
  tap short of them. The intent extras keep opening those pages after phase 19 re-homes them under its categories (its E2 asserts
  `settings_start_theme` and the rest there), and phase 19 carries an H row so Jeremy can swap them for categories on the phone
- 2026-09-23 (agent, review triage C-9): the Q1 standing rule is amended in its mechanism only — shell apps declare STATIC App
  Shortcuts (`shortcuts.xml`) for their fixed top-level screens and DYNAMIC ones (`ShortcutManager.setDynamicShortcuts`, published
  and removed by the app) for screens that exist only conditionally (phase 17's Media server while a server is set up, phase 18's SD
  card while one is mounted). The selection rule's "manifest before dynamic" is therefore a stated choice: a conditional screen
  ranks after the fixed ones, so the four satellites a user learns stay where they were. Reason: a static shortcut to a screen that
  is not there would launch into nothing; phases 17 / 18 say "dynamic" and carry the rows (their T17-9 / T18-6). Added 2026-09-23
  (r2 triage C-21): every dynamic shortcut calls `ShortcutInfo.Builder.setActivity(<its app's launcher activity>)`. One published
  without it attaches to the package's first MAIN / LAUNCHER activity, which in this APK is `MusicActivity`
  (`AndroidManifest.xml:111-120`), so under the per-activity query 17's Media server / Panorama / Slow motion and 18's SD card would
  burst on the Music tile; 17 build task 15 and 18 build task 11 say so, and their E23 / E2 assert the Music tile's burst unchanged

- 2026-09-23 (agent, lead, resolving the writer's OPEN on E15): the selection query is per ACTIVITY, not per package —
  ShortcutQuery().setPackage(pkg).setActivity(the tile's component) — because Music and Start settings are both app.tileshell and
  a package-wide query gave both tiles the first four of all eight shell shortcuts. Each shell app declares its static shortcuts
  on its own launcher activity (MusicActivity's meta-data for Music's four, SettingsActivity's for Start settings' four), which is
  also Android's own model; a third-party app with several launcher activities gets each tile's own shortcuts the same way. The
  "manifest before dynamic" order and the four-satellite cap are unchanged. E15 asserts each tile's four are its own (Music's
  pivots on the Music tile, the settings pages on the Start settings tile) and none of the other's.
- 2026-09-23 (agent, r3 triage T11-22): what each tile kind queries (`TileTarget`, `start/StartPage.kt:94-102`). `App` → its
  AppEntry's component and user (a slot tile resolves to its app first). `Shell` → a fixed component under the shell's own user
  (`Process.myUserHandle()`), the activity its launch opens (`StartActivity.kt:275-279`): `shell:weather` →
  `app.tileshell/.weather.WeatherActivity`, `shell:settings` → `app.tileshell/.settings.SettingsActivity`; `shell:cortana` opens a
  voice session, not an activity, so it is not queried and logs `no shortcuts`. `Unassigned` → `no app`. `Secondary` → `secondary
  tile`, no burst: a secondary tile stands for one item inside its app, and the app's own shortcuts are not that item's actions.
  `Folder` → `folder`. Never a package-wide fallback (it would burst Tess with Music's shortcuts)
- 2026-09-23 (agent, r3 triage T11-23): no hold timer is added to edit mode. Jeremy's R10-Q3 ruling puts the burst at THE hold that
  starts edit mode ("nothing about the hold changes"), and phase 02's edit mode has none (`EditGestures.kt:97-111`: an edit-mode
  press settles only on a move or a release). So in edit mode a still press of any length on another tile is a tap on release
  (with a burst open it only closes the burst; without one it moves the selection, R6 §1.5.1), a still press on the held tile is a
  tap (closes an open burst; with none, exits edit mode, §1.5.1(a)), and a press that moves drags. The split-time edge case "a hold
  in edit mode on another tile … opens that tile's burst" is withdrawn; E5 carries the negative
- 2026-09-23 (agent, r3 triage T11-39): the FINAL parts this phase edits, each with an INDEX Change Log line when built:
  `EditGestures` / `StartPage` / `StartEditState` (phase 02; the 2026-09-22 "PHASE 02 IS AMENDED BY R10-Q3" line, plus the
  consumed-DOWN fix, T11-38); `StartActivity`'s Back collector and launch path (phases 01 / 02); `AppCatalog`'s LauncherApps
  callback gains `onShortcutsChanged` (phase 02 task 5); `SettingsActivity`'s manifest meta-data (phase 01 — its `EXTRA_PAGE`
  already exists, `settings/SettingsActivity.kt:58`, and is reused, no new extra); `MusicActivity`'s pivot extra and the
  `music_pivot_header` `selected` semantics (phase 10)

## Interview queue (Stage A step 4)
1. ~~Do the shell's own tiles burst?~~ RULED 2026-09-22: A (see Decisions). Original question kept below.
   Do the shell's own tiles burst? Music (phase 10) and Start settings (phase 01) can declare static shortcuts to their own
   screens; Weather has one screen and Tess's pages live in the voice session, so neither can. Without a ruling every shell tile
   gets edit mode and no burst — on exactly the tiles the shell controls (design 22).
   A. Yes: Music declares Songs / Albums / Artists / Playlists and Start settings declares Start + theme / Tile apps / Setup /
      Diagnostics (each an ADD: an intent extra that opens that page, INDEX Change Log for phases 01 and 10), and every inbox app to
      come (phases 15–19) declares its own top-level screens the same way — a standing rule (lean)
   B. No: the shell's tiles open no burst; only third-party apps' tiles do
   C. Only the inbox apps to come declare shortcuts; Music and Start settings stay as they are
   D. Other / let me clarify

2. ~~Disc taps during a burst~~ RULED 2026-09-22: A (see Decisions). Original question kept below.
   (added 2026-09-22 by the lead, from the writer's flag: Jeremy's R10-Q3 wording "a tap elsewhere closes the burst" could
   make the discs cost two taps) A tap on the held tile's unpin or resize disc while the burst is open:
   A. One tap: the disc acts at once (unpin removes the tile, resize changes its size) and the burst closes (lean)
   B. Two taps: the first tap only closes the burst; the disc acts on the next tap
   C. The discs are hidden while the burst is open and appear once it closes
   D. Other / let me clarify

## Build tasks
1. Shortcut source: `LauncherApps` query under the tile's user, per tile kind (T11-22), off the main thread from the DOWN (T11-32),
   the selection rule (pure, JVM-tested, incl. the `isEnabled` filter T11-15 cannot drive on the device), the host /
   quiet-profile / folder / unassigned / secondary / off-screen checks with their diagnostics reasons, `query failed` proved by a
   JVM test with a throwing fake (T11-21), icon loading through the extended glyph rule (a null drawable is a failed icon, T11-33),
   `onShortcutsChanged` handling (`shortcuts changed` / `removed`)
2. Burst state on `StartEditState` (open, satellites, rest geometry, close reason) and the `EditGestures` changes: satellite hit
   (square plus label) before the discs, the tap-elsewhere / disc / drag rules above, the slop fork unchanged, the consumed-DOWN
   fix (T11-38); Back in `StartActivity`'s `backEvents` (a first branch: a burst open → close the burst only; T11-39); Home and
   Start stopping close it
3. Burst layer in `StartPage`: `boundsInRoot` tracking of the held tile's slot (T11-41), the corner and line arrangements with
   clamping decided at open (T11-27), the spring motion (open and close) with the `[quick] motion` lines, drawing (fill, icon,
   label in the theme's text colour), test tags, the `[quick] satellite i rest=` lines (T11-28)
4. Satellite launch: `startShortcut` with the satellite's bounds from `pendingLaunch` after the Start exit, `launchApp`'s
   options and bookkeeping (`UseCounts`, `pendingRecent`, `returningFromLaunch`), the failure path that plays the entrance (T11-24)
   — the failure path also JVM-tested with a throwing fake
5. Shell apps' static shortcuts per Q1 (Music pivots, Start settings pages, and the intent extras they need — recorded as ADDs in
   the INDEX Change Log for phases 01 and 10), each set declared on its own launcher activity (per-activity query, T11-12): ids
   `songs`, `albums`, `artists`, `playlists` on `MusicActivity`, and `start_theme`, `tile_apps`, `checklist`, `diagnostics` on
   `SettingsActivity` (the `SettingsPage` names, `settings/SettingsActivity.kt:42`, carried in the existing `EXTRA_PAGE` "page"
   extra — no new extra, T11-39), plus `selected` semantics on Music's pivot headers (`music_pivot_header:<pivot>`,
   `music/MusicCollectionPage.kt:355`, which carry none today) so E15 can read which pivot a shortcut landed on — part of the
   phase 10 ADD
6. Build-start checks recorded in Decisions: ~~the consumed-DOWN hold on a transport control~~ (answered from the code, a defect
   fixed in task 2, T11-38); ~~the hold on the promoted tile~~ (settled by J4, T11-2; E16 proves it); `boundsInRoot` under the
   contraction
7. QA: the two fixture APKs' shortcuts (tileclient-a's on its launcher activity, T11-13; tileclient-b's `badicon` / `reset` verbs,
   T11-14; `ShortcutActivity` with `@+id/shortcut_id`, T11-47; the b-only publication and the `ShortcutVerbReceiver` with
   `disable` / `badicon` / `deadtarget` / `reset`, T11-15 / T11-21 / T11-30); `qa/phase-11/baseline_layout.json`, its five variant
   baselines (T11-35) and
   `baseline_layout-pre-11.json` (T11-3, C-3); the `[quick] motion` assertions (T11-5, C-31); `lib.sh`'s ring helpers, owned here as
   the first phase in the Build order (C-20): `ring_mark` (`adb shell date +%s%3N`), `ring_since <mark> [launcher|speech|<service
   component>]` (`qa/phase-03/scripts/j7.sh:13-15`'s `wall=` filter as one function), `reply_since <mark>` (the first `text=` reply after
   the mark, empty if none), `row_begin` setting `ROW_MARK=$(ring_mark)`, `ring_save [launcher|speech|<service component>]`
   appending `ring_since $ROW_MARK` of that ring to `<row>/ring-<name>.txt` (called before every in-row `am force-stop
   app.tileshell`, `layout_restore` and `pm clear`, which empty the ring; duplicates across calls are harmless), and `row_end`
   calling `ring_save` for each ring the row names, last (T11-21); the managed-profile and private-space rows; the phase 02
   regression re-runs (E1 / E7's bracket and geometry sub-rows / E8, `regress.sh`) and the two control rows; E15 / E16; evidence
   index `qa/phase-11/README.md`

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 on the shell's own clock
(below); `uiautomator dump` follows RV13 except where the dump route below applies; "diagnostics" is read with phase 01's command.
Every E row runs on the AOSP AVD `tileshell_fhd` (1080×2340 @ 450 dpi, no Google) through adb on phase 03's driver floor
(`qa/phase-03/scripts/lib.sh`), with `qa/phase-02/scripts/gestures.sh` for the one-stream gestures.
**Seeding (T11-3, C-3).** tileclient-a and tileclient-b are installed from their debug APKs, then tileclient-b is started once
with the `reset` verb (it publishes `qa_dyn`; a dynamic shortcut exists only after its app has run — T11-30) followed by C-6's
force-stop + Home, and every row starts with
`layout_restore qa/phase-11/baseline_layout.json` (`qa/phase-02/scripts/layout.sh`: it stops the shell, writes, stops it again,
brings Home back and compares what is on disk after the shell loaded it) — fixtures are never pinned by hand per row. That file is
derived from `qa/phase-02/baseline_layout.json` and carries: every `addedOnce` marker of the build under test (today
`phase03:cortana`, `folder:games:v1`, `folder:office:v1`, `slot:music:v1`; this phase adds none), its `slots`, and `manualSizes`
for EVERY tile so the use-based auto-sizer cannot reshape it; tileclient-a MEDIUM (`app:app.tileshell.testclient.a/<its launcher
activity>:0`) in the MIDDLE column of the grid's second row, clear of both screen edges, the status bar and the bottom row — order
`slot:PEOPLE`, `slot:BROWSER`, `slot:MAIL`, `slot:PHOTOS` (MEDIUM), tileclient-a (MEDIUM), tileclient-b (SMALL), `slot:CALENDAR`
(WIDE), then the rest; first fit puts the fixture at units 2–3 of row 2, x 365–708 / y 439–782 at rest on this AVD, where its
212-px corner reach clears both edges (re-cut 2026-09-23 by r3 triage T11-18 from "right after `slot:CALENDAR`", which packs at
units 4–5, the right edge; E1 asserts the cell first, so a mis-seeded file fails loudly) — the Tess tile `shell:cortana` MEDIUM
(E15; placed at the end, away from the fixture's burst), an Unassigned slot tile (one the AVD resolves to no app, named in
`qa/phase-11/README.md`; E3, T11-22), and a two-tile folder `folder:qa` holding `slot:STORE` and `slot:MAPS` (E3, E7's
band). E7's variants are named baselines beside it — `baseline_layout-bottomrow.json` (tileclient-a in the bottom row),
`baseline_layout-wide.json` (tileclient-a WIDE at the grid's right edge; the row turns show more tiles off in Start + theme first,
since that is a pref, not part of any layout file — T11-35), `baseline_layout-band.json` (tileclient-a inside `folder:qa`),
`baseline_layout-tall.json` (the main file with every tile AFTER the fixture set made WIDE — `make_tall.py`'s rule applied only to
those keys — so the fixture keeps its cell and Start scrolls; T11-18), `baseline_layout-topleft.json` (tileclient-a FIRST in the
order, at the grid's top-left; T11-35) —
and phase 02's file is kept as `qa/phase-11/baseline_layout-pre-11.json`. In the band and bottom-row variants the fixture's dump
id is `tile:member:<key>` / `tile:dock:<key>` (`StartPage.kt:152`'s `idPrefix`; T11-35). After every restore the ring holds zero `[layout]
assignSlotOnce … -> assigned` lines and the restored `addedOnce` equals the file's (E13 asserts both). "The fixture tile" below is
tileclient-a's tile (`tile:<key>` in the dump).
**Launches (C-6).** After any step that launches an app (E2's short press, E4, E9, E15, E16), `ring_save; am force-stop
app.tileshell` + Home before the next grid assertion: RecentApp is in memory only (`qa/phase-01/scripts/recent0922.sh:19-21`), so a
launched app is promoted above the bottom row and "the fixture tile" would no longer be the grid tile the next step expects; the
`ring_save` keeps the ring lines the force-stop is about to wipe (T11-21).
**Dump route (T11-4, C-10).** A default Start has Weather, Photos, Calendar and Music tiles flipping and never idles, so every dump
of a burst is taken through phase 05's gesture-driver `UiDevice.dumpWindowHierarchy` with `Configurator.setWaitForIdleTimeout(0)`
(qa/phase-05/README.md); every satellite bounds assertion takes the `[quick] satellite i rest=` line as primary and the dump as
corroboration.
**Motion clock (C-5).** Every motion the shell animates logs its own clock from `withFrameNanos` — here `[quick] motion open|close
…` (Decisions, T11-5) — and the row asserts the logged numbers against RV11's tolerance; a screenrecord corroborates under phase 05's
frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion, else retaken) and is never the primary clock. The line also
carries `frames=<n> maxGapMs=<ms>` (consecutive frame-time gaps inside the motion) and the row asserts `maxGapMs` ≤ 33.4 ms (2 vsync)
beside the numbers (C-31).
**Ring reads (C-20).** Every ring assertion reads `ring_since` from a MARK taken immediately before the step's action (after any
clock jump, so the MARK is on the new clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`. The
helpers are this phase's build task 7.
**Wake (C-25).** After any `adb reboot` (boot-completed poll), `dumpsys battery unplug` or `KEYCODE_SLEEP` step, the driver calls
`wake_device` (`qa/phase-03/scripts/lib.sh:47-56`) and asserts it printed `Awake` before the next tap.
**Emulator:**
- E1 Burst at the hold: first a rest dump asserts the fixture tile's cell (x 365–708, y 439–782 ± 1 px, T11-18); `adb shell input
  motionevent DOWN x y` on the fixture tile's centre, sleep 1.0 with the finger still down,
  dump: `quick_burst` present, `quick_sat:0..3` present, `quick_sat_label:0..3` texts are "One", "Two", "Three", "Four" (ranks 0–3;
  "Five" absent), `edit_disc:unpin` and `edit_disc:resize` present, `dim:*` present, `adb shell dumpsys activity activities` still
  shows `StartActivity` resumed; the ring slice from a MARK before the DOWN has `[quick] shortcuts for
  app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity/0: 5 (4 shown: qa_one,qa_two,qa_three,qa_four)` (T11-12) and
  `[quick] burst on <id>: 4 satellites`; `UP` → a second dump passes `qa/phase-02/scripts/dumpdiff.py` (IDENTICAL tile
  rectangles) and its `quick_sat:*`, `quick_sat_label:*` and `edit_disc:*` bounds equal the first dump's ± 1 px with equal label
  texts — never a byte comparison, the flipping tiles change the XML (T11-44) — so the burst and edit mode both remain; the held
  dump is saved as the reference E16 compares with; screencap saved
- E2 The fork, bracketed in time and in space (RV11; testability 11): `adb shell input swipe x y x y 740` → the press acts as a tap
  (the fixture app launches, no `quick_burst`, no `edit_disc:*`, and the ring slice from a MARK before the swipe has no `[quick]
  burst on` and no `[edit] hold` line — T11-45), then C-6; `adb shell input swipe x y x y 830` → `quick_burst` present, edit
  mode on, nothing launched (this is phase 02 E7's 830-ms line with the burst added); `DOWN; sleep 0.9; MOVE +4 px; UP` → `quick_burst`
  still present and the tile's bounds unchanged; `DOWN; sleep 0.9; glide +40 px` → a dump (dump route) taken BEFORE the `UP` has no
  `quick_burst` and the fixture drawn ≥ 30 px from its E1 held bounds (the dragged tile follows the finger), the slice has `[edit]
  drag start <fixture id>` and `[quick] burst closed: drag`; after `UP` the fixture is back in its own cell (E1's held bounds ± 1 px:
  a 40-px drag never leaves the tile's own cell, `EditGestures.kt:207-223`; re-cut 2026-09-23 by r3 triage T11-19 from "the tile
  moved (bounds diff)" after the `UP`)
- E3 Selection rule and counts, three tile kinds: the fixture tile → the four ids above in the order top-left, top-right,
  bottom-left, bottom-right (satellite i's label = rank i's label); tileclient-b's tile (after `reset` + C-6, so the sub-step never
  depends on an earlier row, T11-30) → one satellite at top-left (`quick_sat:0`
  present, `quick_sat:1..3` absent, `[quick] shortcuts for app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity/0: 1 (1
  shown: qa_dyn)`, T11-12). Icon failure (T11-14): `am start` tileclient-b with the `badicon` verb, C-6's force-stop + Home, MARK,
  hold its tile → `quick_sat:0..1` present, `quick_sat:1`'s interior clear of its label equals the satellite fill ± 2 (no glyph drawn)
  while `quick_sat:0`'s interior is NOT uniform fill (a glyph is drawn, T11-33),
  the slice holds `[quick] satellite 1 icon failed app.tileshell.testclient.b/qa_noicon: null drawable` (or `: <exception>`), and `tap_node quick_sat:1` logs
  `[quick] tap satellite 1 app.tileshell.testclient.b/qa_noicon: startShortcut ok` (the satellite still runs); then the `reset` verb
  and C-6. The Weather tile (shell; Q1 A: Weather declares
  none) and the folder tile `folder:qa` → no `quick_burst`, edit mode on, `[quick] no burst on <id>: no shortcuts` / `: folder`
  (the Weather hold's slice also has `[quick] shortcuts for app.tileshell/.weather.WeatherActivity/0: 0 (0 shown)`, T11-22). The
  seeded Unassigned slot tile → `: no app`; a secondary tile created by phase 02 E5's route (tileclient-a's
  `secondary.requestCreate`, accepted) → `: secondary tile`; each with edit mode on and no `quick_burst` (T11-22).
  When the fixture stash is present: Open Camera's five manifest shortcuts give exactly the four ids the rule predicts (`dumpsys
  shortcut` lists their ranks)
- E4 The right shortcut launches: with the burst open, `tap_node quick_sat:2` → `dumpsys activity activities` shows
  `app.tileshell.testclient.a/app.tileshell.testclient.ShortcutActivity` resumed and the dump's
  `resource-id="app.tileshell.testclient.a:id/shortcut_id"` text reads `qa_three` (T11-47); the `[quick] tap satellite 2
  …: startShortcut ok` and `[quick] burst closed: launch` lines, and the `ok` line's `wall=` time is ≥ `Motion.EXIT_TOTAL_MS` (250 ms) after the `launch` line's
  (`startShortcut` runs from `pendingLaunch`, after the Start exit's last frame, T11-24); a screenrecord shows the Start exit (R3 A11, phase 01 E10's method) from the release; Home → Start with no
  `edit_disc:*`, no `quick_burst`, and the fixture tile in `recent_app_row` (RECENT0922's method); then C-6 (`ring_save; am
  force-stop app.tileshell` + Home) so the next hold is on the grid tile again; the same for satellites 0, 1 and 3
- E5 Taps while open, each from a fresh burst on the fixture tile in its grid cell (after `layout_restore` and C-6's force-stop —
  never from E4's promoted tile), each with a MARK before its action (C-20): tap empty grid space → `quick_burst` absent,
  `edit_disc:*` present, the held tile unchanged (`[quick] burst closed: tap elsewhere`); a second tap on empty space → edit mode
  exits (R6 §1.5.1). Tap another tile — `tile:shell:cortana`, after asserting its centre lies outside every `quick_sat:*` and
  `quick_sat_label:*` rectangle (satellites may overlap neighbours by design) → burst gone, selection unchanged (discs still on the
  fixture tile). Tap the held tile's centre → `quick_burst` absent, `edit_disc:*` still on it, `[quick] burst closed: tap
  elsewhere`, and NO `[edit] tap on the held tile` line (today's handling would exit edit mode). On `baseline_layout-band.json` with
  `folder:qa` expanded and the burst on a member: tap `folder_name_placeholder:qa` → burst closed, no `folder_name_box`.
  `tap_node quick_sat_label:0` → satellite 0 runs (`shortcut_id` = `qa_one`), then C-6 (T11-26). `KEYCODE_BACK` → burst gone
  (`back`), edit mode on; a second `KEYCODE_BACK` → edit mode off. Tap `edit_disc:resize` → the tile's size cycles (`[edit] resize
  <id> MEDIUM -> SMALL`, phase 02's `TileSize.next()` — corrected 2026-09-23 at build from `-> WIDE`, INDEX Change Log),
  `quick_burst` absent, `edit_disc:*` on the tile's new corners, edit mode on, and the slice has `[quick]
  burst closed: resize` (Q2 A; re-cut 2026-09-23 by r3 triage T11-17 from "`quick_burst` present … at the new corners"). Tap
  `edit_disc:unpin` → the tile and the burst are gone (`unpin`). Tap the drawn Windows key (`tap_node nav_windows`; this AVD does
  not re-deliver the HOME intent to the resumed home activity, phase 02 `qa/phase-02/README.md:73-75`, so `KEYCODE_HOME` is phone
  row P6 — T11-16) → Start's page (`start_page` in the dump; not "page 0" — phase 14 moves Start to pager index 1, T11-9), no
  burst, no edit mode, and the slice has `[quick] burst closed: home` and a `[start] home:` line. `input keyevent KEYCODE_SLEEP`,
  then `wake_device` → `Awake` (C-25) → no `quick_burst`, and the slice has `[quick] burst closed: stop` (T11-21). A drag on empty
  space on `baseline_layout-tall.json` (T11-18) → Start scrolls — the row first asserts the tile's Δy ≥ 60 px, so a Start that
  cannot scroll fails — and every satellite's bounds move by the tile's Δy ± 1 px (dump before / after), with fresh `[quick]
  satellite i rest=` lines at the scroll's end (T11-28). In edit mode with NO burst open, `DOWN` on `tile:shell:settings`, sleep
  1.0, then a dump with the finger still down → no `quick_burst` and no `[quick] burst on` line; `UP` → the discs move to it
  (the no-hold-in-edit-mode rule, T11-23). Press feedback (T11-7; sample points T13-8, T11-31): `input motionevent DOWN` at
  (left + 10, top + 10) px of `quick_sat:0`'s `rest=` bounds — never its centre: no pixel of a 164-px satellite lies ≥ 126 px
  (phase 13's r + 2 epx) from its centre — screencap while held, then `MOVE`
  off the satellite and `UP` (nothing runs, burst stays), once with `press_tilt` and once with `press_p4` selected in Start + theme,
  and the sub-step ends by re-selecting `press_none` (RV12; T11-34):
  on this phase's build the satellite's pixels in the held capture equal its rest pixels ± 1 (no Q6 style); from phase 13's build
  on (re-run at phase 13's gate, its E7 satellite sub-row), with F = the satellite's rest fill: the ring on the satellite's right and
  bottom edges, ≥ 126 px from the touch point, reads F + 0.30·(255 − F) ± 4; the touch-point pixel reads F + 0.10·(255 − F) ± 4; the
  pixel 60 px from it along the satellite's TOP edge, (left + 70, top + 10) px — outside the centred glyph box and clear of the
  3-px ring — reads F + 0.05·(255 − F) ± 4 (re-cut 2026-09-23 by T11-31 from "along the diagonal", which lands on the glyph); the
  interior pixel 12 px inside the bottom-right corner (≈ 200 px away) reads F ± 2; with Transparency effects off every one of
  those pixels equals the rest pixels ± 2 (one tolerance with phase 13 E7, T11-31)
- E6 Motion, on the shell's clock (C-5, T11-5): open a burst on the fixture tile on a WARM process — one warm-up hold first
  (added 2026-09-23 at build: the first hold after the shell starts janks in phase 02's own edit-mode entry on the AVD's debug
  build, a burst or not, so it is recorded, not asserted; P2 reads the cold open on the phone's release build); the `[quick] motion open <tileId>` line reads peak
  = 107 ± 17 ms, overshoot = 6.8 ± 2 % of the travel and settle ≤ 250 + 17 ms (settle = the first frame from which every later
  frame is within 1 px of rest, ≈ 249 ms for the fixture's ≈ 427-px travel, plus RV11's one frame — re-cut 2026-09-23 by r3 triage
  T11-29 from "≤ 250 ms"; re-cut 2026-09-24 by the gate's evidence review G-E6-2 to ≤ 249.1 ms + that open's `maxGapMs` (+1), since
  the settle frame is the first frame at or after 249.1 ms and C-31 already allows one dropped frame; EVERY warm open is asserted,
  not only the recorded one), and one line (one t0) covers all four satellites, its t0 within one frame of the edit entry's first
  frame on the same clock (`[edit] entry first frame at uptime=`; G-E6-4, C-5) — asserted on five warm opens with NOTHING recording
  (re-cut 2026-09-24: a warm open recorded under screenrecord logged an 83-ms gap on the shell's own clock, the recorder perturbing
  what it corroborates); tap elsewhere: `[quick] motion close <tileId>` reads
  alpha0 = 36 ± 17 ms (the travel fraction passes 0.5 at 36.0 ms) and settle = alpha0 ± 17 ms (the satellites are gone at alpha 0),
  so a fade that ends on arrival fails (T11-29, from the split-time "alpha0 ≤ 100 ms, settle ≤ 250 ms", which no close could
  fail); both lines read `maxGapMs` ≤ 33.4 ms (C-31), each from the ring slice after a MARK taken just before its hold / tap (C-20).
  Corroboration: a screenrecord of a SEPARATE open, its shell-clock numbers noted, not asserted (540x1170: at full size the AVD's encoder drops frames the app's own clock does
  not) passes phase 05's frame-spacing rule (source frames ≤ 18.2 ms apart during the motion, else retaken); its satellite-pixel
  lag is printed, not asserted (re-cut 2026-09-24, G-E6-4: the pixel detector counted the neighbours' contraction and could not
  fail — the alignment is asserted on the shell's clock above); the capture is not the clock for any number above. P2 repeats the open
  line on the phone
- E7 Geometry, `rest=` lines primary and the dump route's bounds as corroboration. In EVERY case below no `quick_sat:i` or
  `quick_sat_label:i` rectangle intersects the held tile's drawn bounds (T11-27), and every `[quick] satellite i rest=` line equals
  the dump's bounds ± 1 px. Corner arrangement (the fixture MEDIUM in the grid's middle column, `baseline_layout.json`): each
  satellite 164 ± 3 px square, its inner corner 48 ± 3 px (16 epx) from the tile's corner on both axes, labels above the top pair
  and below the bottom pair (bounds). `baseline_layout-bottomrow.json` (the fixture is `tile:dock:<key>`) → the line arrangement:
  four satellites in one row above the bottom row, their bottoms one gutter above the row's top — the smallest top of the
  `tile:dock:*` nodes, never `bottom_tile_row`'s own node, which spans the page (T11-35) — above the drawn nav bar.
  `baseline_layout-topleft.json` (the fixture first, at the grid's top-left; replaces the split-time `slot:PEOPLE` case, whose
  Contacts app publishes one shortcut — T11-35) → the corner cannot clear the status bar or the held tile, so four satellites in a
  line BELOW the tile with their labels below them, every one inside the page area (below phase 01's drawn status bar,
  `BarMetrics.STATUS_EPX` epx — C-17 — above the bottom row's top minus one gutter, inside the grid margins). Wide: turn
  `theme_show_more_tiles` off in Start + theme, `layout_restore baseline_layout-wide.json`, assert the fixture spans the grid's
  width, hold → the line arrangement; turn the setting back on after (RV12). `baseline_layout-band.json` with `folder:qa` expanded
  (the fixture is `tile:member:<key>`) → corner arrangement drawn above `folder_band_top:*` / `folder_band_bottom:*`.
  `baseline_layout-tall.json` → scroll Start with a slow drag (`input swipe 540 1700 540 1580 1500`, no fling), read the scroll
  from the fixture's dump bounds (Δy 60–120 px), hold the fixture → every `rest=` rectangle relative to the fixture's drawn bounds
  equals the corner case ± 1 px (re-cut 2026-09-23 by T11-18 from "scrolled 1.5 screens", which the variant cannot scroll). From
  phase 13's build on, the corner-case capture repeated with `quick_sat:0` held down at (left + 10, top + 10) px shows E5's four
  samples at their values (T13-8, T11-31), and the held tile and `quick_sat:1..3` equal the unpressed capture ± 1
  (the lights stay inside the pressed satellite, T11-7; neighbours that flip are not compared)
- E8 No-burst and close cases driven by the fixture (re-cut 2026-09-23 by r3 triage T11-15 / T11-21: Android deletes a disabled
  unpinned dynamic shortcut, so the `isEnabled` filter is proved by the selection rule's JVM test, not here; every verb sent with a
  burst open goes through `ShortcutVerbReceiver`, so no window comes over Start): (a) `adb shell cmd role remove-role-holder
  android.app.role.HOME app.tileshell` and the same for `android.app.role.ASSISTANT` (the assistant holder is a shortcut host too:
  HOME alone left the query working — corrected 2026-09-23 at build) → hold → edit mode, no burst, `[quick] no burst …: not the shortcut host`; restore both with
  `add-role-holder` and `cmd package set-home-activity`; (b) `reset` + C-6, then `adb shell am broadcast -n
  app.tileshell.testclient.b/app.tileshell.testclient.ShortcutVerbReceiver --es verb disable`, MARK, hold its tile → no burst, the
  slice has `[quick] shortcuts for app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity/0: 0 (0 shown)` and `[quick] no
  burst on <id>: no shortcuts`, and the saved `adb shell cmd shortcut get-shortcuts --flags 9 app.tileshell.testclient.b` lists
  nothing; then `reset` + C-6, open a burst on the tile, broadcast `disable` → `StartActivity` still resumed, no `quick_burst`, edit
  mode on, and the slice has `[quick] burst closed: shortcuts changed`; `reset`; (c) `adb uninstall` tileclient-b while its burst
  is open → the tile and the burst go (phase 02 E6) and the slice has exactly one `[quick] burst closed: removed` line; restore:
  `adb install` tileclient-b's debug APK, `reset` + C-6, `layout_restore` (T11-15); (d) the `deadtarget` verb (broadcast), MARK,
  hold tileclient-b's tile, `tap_node quick_sat:1` → nothing new resumed, Start back on `start_page` with no edit mode, a `start
  entrance` motion line, and the slice has `[quick] tap satellite 1 app.tileshell.testclient.b/qa_dead: startShortcut failed
  android.content.ActivityNotFoundException` (T11-21 / T11-24); `reset` + C-6
- E9 Managed profile and private space (re-cut 2026-09-23 by r3 triage T11-20: `pm set-quiet-mode` does not exist on this AVD and
  `am stop-user` sets no quiet mode; phase 01 E18 created both profile kinds here): `adb shell pm create-user --profileOf 0 --managed
  qa_work`, `adb shell am start-user <id>`, `adb shell pm install-existing --user <id> app.tileshell.testclient.a`, pin its work
  tile from the app list's work group (phase 02 E2), hold → four satellites, `[quick] shortcuts for
  app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity/<id>: 5 (4 shown: …)` (T11-12), `tap_node quick_sat:0` →
  `dumpsys activity activities` shows `app.tileshell.testclient.ShortcutActivity` resumed as `u<id>` with `shortcut_id` = `qa_one`
  (T11-47); C-6. Open the app list and `tap_node profile_lock:work` (phase 01 E18's route), assert `adb shell dumpsys user` shows
  `QUIET_MODE` for <id>, Home, assert the work tile is still in the dump, hold → no burst, `[quick] no burst on <id>: profile
  quiet`; `profile_unlock:work` → hold → four satellites again. Private space: `adb shell pm create-user --profileOf 0 --user-type
  android.os.usertype.profile.PRIVATE qa_private`, `am start-user <p>`, `pm install-existing --user <p>
  app.tileshell.testclient.a`, pin its tile from the private group, `tap_node profile_lock:private` → hold → `[quick] no burst on
  <id>: profile quiet`. Restore: `pm remove-user` for both
- E10 Transport controls: with phase 01 E8's playback fixture playing and the Music tile grown
  (`tile_control:slot:MUSIC:PLAY_PAUSE` in the dump, T11-46), `adb shell input swipe cx cy cx cy 1000` on the play/pause control →
  no `quick_burst`, no `edit_disc:*`, `[tile_control] tile=slot:MUSIC PLAY_PAUSE (no launch)` logged and `dumpsys media_session`
  shows PAUSED (this fails on today's code, T11-38); the same hold on the tile's art above the strip → edit mode and Music's four
  satellites (Q1 A; E15's labels)
- E11 Control rows (testability 29): `adb shell input swipe x y x y 1200` on an app-list row → `applist_menu` present, `quick_burst`
  absent; the same on a Music collection track → `music_menu` present, `quick_burst` absent
- E12 Gesture navigation: `adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural`; `layout_restore
  baseline_layout-bottomrow.json` and hold the fixture tile in the row (`tile:dock:<key>`; the baseline's own row tiles are AVD apps
  whose shortcuts are not seeded — T11-35) → the line arrangement above it, every satellite's bottom above the gesture area, and a
  satellite tap resumes `ShortcutActivity` with the tapped id; restore with `cmd overlay disable`
- E13 Regression on the same build: phase 02 E1 (hold + drag as one gesture: no `quick_burst` during the drag, `[quick] burst closed:
  drag`), E7's bracket and geometry sub-rows (unchanged; its 830-ms line now also asserts `quick_burst` — phase 02 E7 holds `slot:PEOPLE`,
  the AVD's Contacts, which publishes ONE manifest shortcut, so the row first saves `dumpsys shortcut` for `com.android.contacts`
  and expects `quick_sat:0` only, T11-48) — its motion sub-rows stay
  open until C-5's `[motion]` clock reaches phase 02 at the end-of-build pass (INDEX row 02: E7 7/10 open on exactly those) and are
  not part of this gate (T11-6) — E8 (both drag paths unchanged) and `regress.sh` with every assertion passing (12/12 on the
  2026-09-22 suite, qa/JEREMY-QA.md P02); after each of those drivers' `layout_restore`, zero `assignSlotOnce … -> assigned` lines and
  `addedOnce` equal to the file's (C-3); phase 01 E10 press styles (a short press shows the style and no burst); phase 03 E5's
  exported allow-list unchanged
- E14 Every diagnostics line in Decisions is asserted by at least one row in this list (E1–E16), each `[quick] no burst` reason
  and each `[quick] burst closed` reason in Decisions' literal lists appears at least once — read from the union of
  `qa/phase-11/*/ring-*.txt` saved by this build's run (the rows whose log's APK id matches; `ring_save` runs before every in-row
  force-stop, restore or `pm clear`, T11-21), never from one final ring, which every `am force-stop` / `pm clear` /
  `layout_restore` resets (`diag/Diagnostics.kt:14-26`; C-20); each pattern at least once. The one exception is `no burst …: query
  failed <exception>`, which no AVD step can provoke: it is proved by the shortcut source's JVM test with a throwing fake (build
  task 1), and this row names that test and quotes its passing output. Where each comes from: `folder` / `no app` / `secondary
  tile` / `no shortcuts` E3, `off screen` E16, `not the shortcut host` E8 (a), `profile quiet` E9; closes: `tap elsewhere` /
  `back` / `home` / `stop` / `unpin` / `resize` E5, `drag` E2, `launch` E4, `shortcuts changed` E8 (b), `removed` E8 (c);
  `startShortcut failed` E8 (d); `icon failed` E3
- E15 The shell's own tiles (Q1 A; T11-1): hold `tile:slot:MUSIC` (the MUSIC slot is `app.tileshell/.music.MusicActivity` in the
  baseline's `slots`) → `quick_sat_label:0..3` texts are "Songs", "Albums", "Artists", "Playlists" (ranks 0–3) and the ring slice
  from a MARK before the hold has `[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown:
  songs,albums,artists,playlists)` and no `[quick] shortcuts for` line naming a Start settings id (T11-12); `tap_node quick_sat:1` → `dumpsys activity activities` shows
  `MusicActivity` resumed and the dump's `music_pivot_header:albums` carries `selected="true"` (the other three `selected="false"`;
  build task 5's semantics); C-6. Hold `tile:shell:settings` → labels "Start + theme", "Tile apps", "Setup", "Diagnostics" and the
  slice from a MARK before the hold has `[quick] shortcuts for app.tileshell/.settings.SettingsActivity/0: 4 (4 shown:
  start_theme,tile_apps,checklist,diagnostics)` and no line naming a Music id; `tap_node
  quick_sat:3` → `SettingsActivity` resumed on the Diagnostics page: the first `text=` among the `page_header` node's descendants reads `DIAGNOSTICS` (PageHeader draws `title.uppercase()` on a child,
  `settings/SettingsWidgets.kt:52-55`; T11-37) and the hub item
  `settings_diagnostics` is ABSENT (it is the hub's row, `settings/SettingsActivity.kt:142`, so its presence would mean the extra was
  ignored); C-6. Hold `tile:shell:weather` and `tile:shell:cortana` → `edit_disc:*` present, no `quick_burst`, `[quick] no burst on
  <id>: no shortcuts` for each. ~~(OPEN (resolved 2026-09-23 by the lead: per-activity query, see Decisions), found 2026-09-23 while
  applying T11-1, not decided here: Music and Start settings are both `app.tileshell`, and the selection rule queries by package only,
  so as written each of the two tiles would get the first four of all eight shell shortcuts; the counts above hold only if the query
  is also filtered by the tile's activity, `ShortcutQuery.setActivity` — a call for the lead)~~ struck 2026-09-23 by T11-12 (resolved:
  the per-activity query, last Decisions line; the activity-keyed lines above make the row failable)
- E16 The promoted tile (T11-2; J4): restore the baseline, tap the fixture tile (it launches), Home (no force-stop): the fixture tile
  is in `recent_app_row`. `DOWN` on the promoted tile's centre, sleep 1.0: `recent_app_row` absent, `edit_disc:*` present, the
  fixture tile at the bounds E1's HELD dump gives it (edit mode, its grid cell) ± 1 px — never the rest dump's, the grid contracts
  in edit mode (T11-36) — `[quick] burst on <id>: 4 satellites`, and every satellite's `rest=` bounds relative to those bounds equal
  E7's corner case ± 1 px, with no satellite or label on the held tile; `UP`; then C-6. Off screen (T11-27): `layout_restore
  baseline_layout-tall.json`, tap the fixture (it launches), Home, scroll Start until the fixture's grid cell is wholly outside the
  page area while the promoted tile shows, hold the promoted tile → edit mode, no `quick_burst`, and the slice has `[quick] no burst
  on <id>: off screen`; C-6

**Phone-only:** P1 Samsung's own apps' App Shortcuts (Camera, Messages, Clock, Gallery) reach a third-party HOME holder on One UI 8:
`dumpsys shortcut` there and a hold on each tile, recorded; P2 the overshoot peak time (107 ± 17 ms) and settle read from the phone's
own `[quick] motion open` line (the same clock as E6, C-5), a phone screenrecord only corroborating (RV11's ≤ 17-ms rule, phase 01
P10's capture); P3 Samsung shortcut icons through the glyph rule (which ones carry a monochrome layer),
screenshots for H3; ~~P4 the managed-profile row if the AVD refused it~~ (struck 2026-09-23 by r3 triage T11-20: phase 01 E18
created a managed profile on this AVD, and E9 now runs there); P5 One UI's gesture area against the bottom-row burst; P6 system
Home (`KEYCODE_HOME`, the intent path this AVD never re-delivers to a resumed Start) with a burst open → no burst, no edit mode,
`[quick] burst closed: home` (T11-16)

**NEEDS-HUMAN (all accept rows):** H1 the burst as a whole — Jeremy's own values (dampingRatio 0.65, `boundsInRoot` tracking) as
built; H2 stiffness 1500 and the resulting 107-ms peak / ≈ 249-ms settle and ≈ 36-ms close (agent pick); H3 the satellite: small-tile
square, the icon rule, the label outside on its outer side in the theme's text colour, judged in both themes (P4; T11-25), and its
press feedback — phase 13's two lights only, never the Q6 style, none with
Transparency effects off (T11-7; judged once phase 13 is built); H4 the corner arrangement's 16-epx stand-off, the line arrangement (above, or below with its labels below)
and the clamping decided once at open (P4; T11-27);
H5 fewer than four filling top-left first (P4); H6 a disc tap acting and closing the burst (Q2 A, a resize included) — the reading of "elsewhere" (agent);
H7 a satellite launch leaving edit mode and promoting the tile (agent); H8 the shell apps' shortcut sets per Q1 (E15); H9 accept any
approximation not covered by H2–H11; H10 the close motion (spring back, alpha out) and the launch vanishing in the exit's first frame
(P4); H11 Samsung shortcut icons as satellites (phone, P3)

## Edge cases
- A hold on a Music tile's transport control (no burst, no edit mode, the control fires on release); a hold on the tile's art (edit
  mode and Music's four satellites, Q1 A; E15); a hold during a live flip (the satellites stay still; the flip continues —
  T11-41); a hold while Start is flinging (the scroll consumes the press: no hold, no burst — phase 02's rule); a press in edit mode,
  with or without a burst open, has no hold timer (re-cut 2026-09-23 by r3 triage T11-23): a still press of any length on another
  tile is a tap on release (with a burst open it only closes the burst; without one it moves the selection), a still press on the
  held tile is a tap (closes an open burst; with none, exits edit mode), a press that moves drags; only the hold that enters edit
  mode opens a burst (E5 carries the negative)
- A shortcut disabled, removed or its app uninstalled between open and tap; `onShortcutsChanged` while open (closes); a work-profile
  tile with the profile quiet, then unquieted (bursts again); a private-space tile while the space is locked (`profile quiet`, E9)
- The burst open at screen-off, Home, an incoming call, the keyguard, a notification-listener restart (closes; edit mode does what
  phase 02 built); process death with a burst open (no state persists; Start restarts plain)
- A second finger while a burst is open (ignored, as phase 02 ignores it); a tap on a satellite that slides off before release
  (nothing runs, burst stays); a tap on a satellite's label (counts as the satellite)
- A folder tile (no burst); a tile inside an expanded band (corner arrangement above the rules); the bottom-row tiles (line
  arrangement; a full row of six — each satellite row clamped inside the grid margins); the promoted tile (since J4: edit mode, the
  promotion suspended, the burst around the tile's grid cell; E16, T11-2); a WIDE tile in the 2-column grid's first row (line
  arrangement; further down it keeps a clamped corner, corrected at build); the grid's top-left tile (clamped)
- Show more tiles toggled (it lives on Start + theme, another activity, so Start stops first: the burst closes with `stop`, and edit
  mode is as phase 02 left it — re-cut 2026-09-23 by T11-40 from "the reflow closes edit mode per phase 02", which phase 02 has no
  rule for); a resize with the burst open (the tile resizes and the burst closes, `resize`, Q2 A — T11-17); unpin of the last tile
  in a two-tile folder with the burst open (the folder dissolves, phase 02 H19; the burst closes)
- The shell not the HOME holder (no burst, reason logged); `getShortcuts` throwing for any other reason (caught, `query failed
  <exception>` logged; the JVM test, E14); the held tile's cell wholly off the page (the promoted tile since J4: no burst, `off
  screen`, E16); an Unassigned slot tile (`no app`) and a secondary tile (`secondary tile`), E3
- An app publishing more than four shortcuts with equal ranks (ties by id, deterministic); shortcuts with no short label (the id is
  never shown — the label line is empty and the satellite still runs); very long labels (ellipsised); an icon that fails to load
  (the satellite draws its fill with no glyph and still runs; `[quick] satellite <i> icon failed <pkg>/<id>: <why>`, T11-14, a null
  drawable included, T11-33; E3's `badicon` sub-step); a shortcut whose target cannot start (nothing launches, `startShortcut
  failed`, Start plays its entrance and is not left on the exit's last frame; E8 (d), T11-24)
- Theme light / dark (the satellite fill is the accent, as on a tile; its label takes the theme's text colour, black on Light —
  T11-25); the X5 transparency slider at 0 % and 100 %
- RV10: `wm size` / `wm density` / `font_scale` changes leave satellite bounds in epx unchanged (phase 01 E3's method, one dump each)

## QA evidence
