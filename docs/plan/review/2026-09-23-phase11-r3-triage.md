# Phase 11 — round 3 triage (Stage A steps 5–6, the LAST round; 2026-09-23)

Inputs: review/2026-09-23-phase11-r3-design.md (Reviewer 1, design / correctness, ids `R3D-1`–`R3D-19`) and
review/2026-09-23-phase11-r3-testability.md (Reviewer 2, testability / evidence, ids `R3T-1`–`R3T-21`), both against
the brief review/2026-09-23-phase11-r3-brief.md. Roster: **opus + opus** — Fable reached its limit (HTTP 429, request
req_011CfMGHFajJmUyzSFBp6vza) and the codex MCP is not loaded, codex CLI rc=1 "usage limit … try again at 10:50 PM";
Jeremy's standing approval of Opus when Fable is out (INDEX `reviewers:` line, 2026-09-16) applies.

Ids continue round 2's (`T11-15` onward). Class rule as round 1/2: a mechanical re-cut, or a missing row with an obvious
form → DOC-UPDATE; a fix that needs a design choice → AGENT-CALL (one line of reason; listed in §2 for Jeremy to
overrule); a change to WHAT is built with no ruling covering it → QUESTION; wrong → REJECT.

**Checked by the lead before ruling (not taken on the reviewers' word):** `qa/phase-02/README.md:73-75` and
`qa/phase-02/scripts/gestures.sh:28-32` (Home is not re-delivered to a resumed Start on this AVD); `StartActivity.kt:112-122`
(`homeEvents` collector), `:198-201` (drawn Windows key → `homeEvents.tryEmit(true)`), `:127-130` (Back), `:207-217` (exit,
then `pendingLaunch`), `:244-282` (`onTileTap` / `launchApp`: options bundle, `UseCounts`, `pendingRecent`), `:329-336`
(onResume entrance); `testapps/tileclient-b/src/main/AndroidManifest.xml` (one activity + one provider, no receiver) and
`testapps/tileclient-b/build.gradle.kts:24-27` (the shared `testapps/common` sources); `qa/phase-02/baseline_layout.json`
(order PEOPLE, BROWSER, MAIL, CALENDAR WIDE, PHOTOS, weather WIDE, STORE, MAPS, MUSIC, settings — so the tile after
Calendar packs at units 4–5, the right edge); `qa/phase-01/E18/E18.txt:32,38,54-55,64` (quiet mode is set on this AVD
through `profile_lock:work` / `profile_lock:private`, and a private space was created here); `start/TileView.kt:292-317`
(the tile consumes the DOWN only on a control hit, so a parent `down.isConsumed` check touches nothing else);
`start/EditGestures.kt:69-87` (not-in-edit branch never looks at the DOWN's consumption), `:97-111` (edit-mode presses
have no hold timer), `:207-223` (a drag inside the tile's own cell commits nothing).

**Counts: 34 merged items — 7 BLOCKING · 16 SHOULD-FIX · 11 NOTE. Classes: 28 DOC-UPDATE · 5 AGENT-CALL · 0 QUESTION ·
1 PARTIAL (T11-43: the INDEX line is outside this session's INDEX edit rights).** Every BLOCKING is resolved by a doc
update; no fork needs Jeremy.

## 1. Merged findings and rulings

| Id | Sev | Sources | Class | Ruling (applied to phase-11-tile-quick-actions.md) |
|---|---|---|---|---|
| T11-15 | BLOCKING | R3D-1, R3T-5 | DOC-UPDATE | E8 (b)/(c) rewritten. Android DELETES a disabled unpinned dynamic shortcut, so `1 (0 shown)` is impossible: (b) asserts `0 (0 shown)` + `no shortcuts`, and the `isEnabled` filter is proved by the selection rule's JVM test. The verb that closes an open burst is sent by an exported, UI-less `ShortcutVerbReceiver` declared only in tileclient-b (`am broadcast -n`), so Start never stops first; the row asserts `StartActivity` still resumed and `burst closed: shortcuts changed`. (c) restores: reinstall, `reset`, `layout_restore`. |
| T11-16 | BLOCKING | R3D-2, R3T-2 | DOC-UPDATE | E5's Home step taps the drawn Windows key (`nav_windows`), which feeds the same `homeEvents` collector as the HOME intent; phone row P6 drives `KEYCODE_HOME`. Decisions' Home line names both routes. |
| T11-17 | BLOCKING | R3T-1 | DOC-UPDATE (Jeremy's Q2 A applied) | Q2 A: "resize changes its size) and the burst closes". E5's resize sub-step, the Tracking line's "a resize … the satellites re-anchor", the Taps line's "the satellites follow the new corners" and the resize edge case are re-cut: a resize tap resizes and closes the burst (`resize`). Close reasons gain `resize` and `removed` (the held tile left the layout, or `onShortcutsChanged` for a package that is no longer installed). |
| T11-18 | BLOCKING | R3T-3 | DOC-UPDATE | Seeding places tileclient-a MEDIUM in the middle column of the grid's second row (after `slot:PHOTOS`, which moves ahead of Calendar), clear of both edges, the status bar and the bottom row; E1 asserts the fixture's cell first. The tall variant makes only the tiles after the fixture set WIDE, and E7 scrolls it by a slow drag of 60–120 px, not "1.5 screens". |
| T11-19 | BLOCKING | R3T-4 | DOC-UPDATE | E2's 40-px glide is asserted BEFORE the `UP` (the dragged tile follows the finger; `[edit] drag start`; `burst closed: drag`); after `UP` the tile is back in its own cell. |
| T11-20 | BLOCKING | R3T-6 | DOC-UPDATE | E9's quiet mode uses phase 01 E18's route (`profile_lock:work`, `dumpsys user` QUIET_MODE), then unquiet bursts again; a private-space sub-step (`profile_lock:private`) gives the same `profile quiet` reason. The "AVD refuses a managed profile → P4" branch and P4 are struck (E18 created one on this AVD). |
| T11-21 | BLOCKING | R3T-7 (+ R3D-5's reason half) | DOC-UPDATE | E14 made passable: (1) `row_begin` sets `ROW_MARK`; `ring_save` appends the slice since it to `<row>/ring-launcher.txt`, and every in-row `am force-stop app.tileshell`, `layout_restore` or `pm clear` is preceded by `ring_save` (C-6's step now reads `ring_save; am force-stop app.tileshell; Home`); (2) the `no burst` and `burst closed` literals are listed in Decisions; (3) E5 gains `KEYCODE_SLEEP` → `stop`; (4) E8 gains (d), a dynamic shortcut whose intent names a component that does not exist → `startShortcut failed android.content.ActivityNotFoundException`; (5) `query failed` alone is proved by the shortcut source's JVM test. |
| T11-22 | SHOULD-FIX | R3D-5, R3T-14 | AGENT-CALL | What each tile kind queries. App → its AppEntry's component and user. Shell → fixed components under the shell's own user: `shell:weather` → `app.tileshell/.weather.WeatherActivity`, `shell:settings` → `app.tileshell/.settings.SettingsActivity`; `shell:cortana` opens no activity and is not queried (`no shortcuts`). Unassigned slot → `no app`. Secondary tile → `secondary tile`, no burst. Reason for the last: a secondary tile stands for one item inside its app, and the app's own shortcuts are not that item's actions. E3 gains an unassigned and a secondary tile. |
| T11-23 | SHOULD-FIX | R3D-3, R3T-16 | AGENT-CALL | The edge case "a hold in edit mode on another tile … opens that tile's burst" is struck: phase 02's edit mode has no hold timer (`EditGestures.kt:97-111`), and Jeremy's R10-Q3 ruling puts the burst at THE hold that starts edit mode, with "nothing about the hold changes". A still press in edit mode is a tap on release. E5 gains the negative (a 1.0-s still press on another tile in edit mode opens no burst and moves the selection on release), so the strike is failable. |
| T11-24 | SHOULD-FIX | R3D-4 | DOC-UPDATE | The satellite launch runs where a tile's launch runs: `pendingLaunch` after the Start exit's last frame, with `launchApp`'s options bundle, `UseCounts.record`, `pendingRecent` (applied in onResume, never at tap time) and `returningFromLaunch`; on failure Start plays its entrance at once, so it is never left on the exit's last frame. |
| T11-25 | SHOULD-FIX | R3D-6 | AGENT-CALL | The label takes the theme's text colour (white on Dark, black on Light), because it sits on the dimmed page and not on the tile, as the discs take the theme foreground (R6 §1.2.4); white on the light theme's dimmed page is ≈1.3:1. H3 judges it in both themes. |
| T11-26 | SHOULD-FIX | R3D-7 | DOC-UPDATE | A label's box is the satellite's width plus HALF a gutter each side, so line neighbours never overlap; a satellite's hit rectangle is its square plus its label's box (the edge case "a tap on a satellite's label counts" gets a Decision). |
| T11-27 | SHOULD-FIX | R3D-8, R3T-11 (d) | AGENT-CALL | The arrangement and clamping are decided ONCE, when the burst opens, against the held tile's edit-mode rest rectangle (centre contracted to 0.90 about the fixed point, size unchanged — the numbers StartPage draws with); afterwards each satellite keeps its offset from the tracked tile and rides it (contraction, scroll), covered by the page edge and bars like the tile. The horizontal bound is the grid margins. A held tile whose rest rectangle lies wholly outside the page area opens no burst (`off screen`; only the promoted tile's cell can, since J4). A line BELOW the tile draws its labels below the satellites (the outer side). Every E7 case and E16 assert that no satellite or label rectangle intersects the held tile. Reason: a burst that re-clamps or switches arrangement mid-scroll jumps with no spring. |
| T11-28 | SHOULD-FIX | R3D-9 | DOC-UPDATE | `[quick] satellite <i> rest=` is written each time the burst comes to rest — the first frame where the open spring has settled AND the tracked bounds equal the previous frame's — and again at each scroll's end; never per frame. |
| T11-29 | SHOULD-FIX | R3D-10, R3T-13 | DOC-UPDATE | `settle` = the first frame from which every later frame of the motion is within 1 px of rest (written when the motion ends). For the fixture's ≈427-px diagonal travel that is ≈249 ms; E6 asserts settle ≤ 250 + 17 ms (RV11's one frame). Close: the travel fraction passes 0.5 at 36.0 ms, so `alpha0` = 36 ± 17 ms and the close `settle` = `alpha0` ± 1 frame (the satellites are gone at alpha 0). |
| T11-30 | SHOULD-FIX | R3D-11, R3T-8 | DOC-UPDATE | tileclient-b's dynamic shortcuts are published only in `app.tileshell.testclient.b` (the shared `VerbActivity` checks its package; tileclient-a publishes none), and Seeding runs tileclient-b's `reset` once after install, then C-6, so `qa_dyn` exists before any hold. |
| T11-31 | SHOULD-FIX | R3D-12 | DOC-UPDATE (+ carried to phase 13) | The phase-13 "5 %" sample moves to (left + 70, top + 10) px, along the top edge, outside the centred glyph box; one Transparency-off tolerance (± 2). tileclient-a's five shortcuts declare one adaptive icon with a monochrome layer. **Carried:** phase 13 E7's satellite sub-row takes the same sample point and tolerance at phase 13's round 3 (not edited here: this session's scope is phase 11). |
| T11-32 | SHOULD-FIX | R3D-13 | AGENT-CALL | The shortcut source runs off the main thread (`Dispatchers.IO`, icons decoded there), started at the DOWN of every press on a tile outside edit mode and cancelled if the press ends before 783 ms; edit mode enters at 783 ms without waiting; the burst opens in the first frame its result is in hand (normally the entry's first changed frame), and `[quick] motion open` t0 is that frame. Reason: binder calls and icon decodes on the frame phase 02 E7 measures would move that measured frame. |
| T11-33 | SHOULD-FIX | R3T-9 | DOC-UPDATE | A null drawable or an exception from `getShortcutIconDrawable` both log `[quick] satellite <i> icon failed <pkg>/<id>: null drawable` / `: <exception>` (LauncherApps returns null for the missing URI). Every fixture shortcut but `qa_noicon` carries an icon; E3 also asserts `quick_sat:0` DOES draw a glyph. |
| T11-34 | SHOULD-FIX | R3T-10 | DOC-UPDATE | E5 gains: tap on the held tile (burst closes, no `[edit] tap on the held tile`), tap on the folder-name strip (band variant; burst closes, no `folder_name_box`), `tap_node quick_sat_label:0` runs satellite 0, "another tile" = `tile:shell:cortana` after asserting its centre is outside every satellite, the scroll sub-step on the tall baseline with Δy ≥ 60 px asserted first, and the press sub-step ends by re-selecting `press_none` (RV12). |
| T11-35 | SHOULD-FIX | R3T-11 (a, b, c, e, f) | DOC-UPDATE | E7: the wide case turns `theme_show_more_tiles` off before its restore and on after; the bottom row's top = the smallest top of the `tile:dock:*` nodes; a fifth variant `baseline_layout-topleft.json` (fixture first) replaces the `slot:PEOPLE` case (Contacts publishes one shortcut) and expects four satellites in a line BELOW the tile; dump ids `tile:member:<key>` / `tile:dock:<key>` in the band / row variants; E12 holds the fixture in `baseline_layout-bottomrow.json`. Build task 7: five variants. |
| T11-36 | SHOULD-FIX | R3T-12 | DOC-UPDATE | E16 compares the promoted-then-held tile with E1's HELD dump (edit mode), not a rest dump; plus the `off screen` sub-row (T11-27) on the tall baseline. |
| T11-37 | SHOULD-FIX | R3T-15 | DOC-UPDATE | E15 reads `DIAGNOSTICS` as the first `text=` in `page_header`'s subtree (PageHeader draws `title.uppercase()` on a child). |
| T11-38 | NOTE | R3D-14 | DOC-UPDATE | The consumed-DOWN check is answered from the code: today a hold on a transport control enters edit mode (`TileView.kt:304-308` consumes only the control's DOWN / UP; `EditGestures.kt:69-79` never checks). Fixed at the producer here: `EditGestures`' not-in-edit branch returns when `down.isConsumed`; INDEX Change Log for phase 02 when built. Build task 6 drops the check. |
| T11-39 | NOTE | R3D-15 | DOC-UPDATE | Build task 2 names Back in `StartActivity`'s `backEvents`; build task 5 uses the existing `SettingsActivity.EXTRA_PAGE` ("page"); a Decisions line lists the FINAL parts this phase edits, each with its INDEX Change Log line when built. |
| T11-40 | NOTE | R3D-16 | DOC-UPDATE | Show-more-tiles edge case: the toggle lives on Start + theme, so Start stops first and the burst closes with `stop`. |
| T11-41 | NOTE | R3D-17 | DOC-UPDATE | The tracked rectangle is the held tile's slot as StartPage places it, outside TileView's own face layer: a flip or a press never moves a satellite (edge case re-cut); when the tile's node leaves the composition the close runs back to its last reported centre; the one-frame trail of `onGloballyPositioned` is accepted (H1). |
| T11-42 | NOTE | R3D-18 | DOC-UPDATE | `depends-on: [01, 02, 03, 05, 10]` (C-23's rule; all precede 11 in the Build order). |
| T11-43 | NOTE | R3D-19 | PARTIAL | INDEX.md's 2026-09-22 R10-Q3 Change Log line says "a tap elsewhere closes it and leaves edit mode"; the ruling (PLAN.md) is "edit mode stays". This session may edit only row 11 and its own Change Log lines, so the clarification rides in phase 11's FINAL Change Log line; the lead may re-word the old line. |
| T11-44 | NOTE | R3T-17 | DOC-UPDATE | E1's second dump is compared with `dumpdiff.py` plus the `quick_*` / `edit_disc:*` bounds ± 1 px and equal label texts, not byte-identical. |
| T11-45 | NOTE | R3T-18 | DOC-UPDATE | E2's 740-ms negative also reads the ring slice: no `[quick] burst on`, no `[edit] hold`. |
| T11-46 | NOTE | R3T-19 | DOC-UPDATE | E10 names `tile_control:slot:MUSIC:PLAY_PAUSE` and `[tile_control] tile=slot:MUSIC PLAY_PAUSE (no launch)`. |
| T11-47 | NOTE | R3T-20 | DOC-UPDATE | The target is `app.tileshell.testclient.ShortcutActivity` (shared sources) with a TextView `@+id/shortcut_id`; E4 / E9 read `resource-id="app.tileshell.testclient.a:id/shortcut_id"`. |
| T11-48 | NOTE | R3T-21 | DOC-UPDATE | E13's 830-ms line holds `slot:PEOPLE` (Contacts), which publishes one manifest shortcut: the row saves `dumpsys shortcut` for `com.android.contacts` and expects `quick_sat:0` only. |

## 2. Agent calls Jeremy can see and overrule (not questions)

- **T11-22** — a secondary tile opens no burst (`secondary tile`).
- **T11-23** — no hold timer is added to edit mode: only the hold that starts edit mode opens a burst.
- **T11-25** — the satellite label is black on the Light theme (the theme's text colour), white on Dark.
- **T11-27** — the burst's arrangement is fixed when it opens and then rides the tile; a held tile whose cell is off the page
  opens no burst; a line below the tile puts its labels below.
- **T11-32** — the shortcuts are fetched in the background from the finger's first touch, so the 783-ms hold is not slowed.

## 3. Verdict

With the rows above applied, neither lens has an open BLOCKING item. This was round 3, the cap: the doc goes FINAL; later
changes go through INDEX's Change Log.
