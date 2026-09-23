# Phases 11–19 DRAFT docs — Reviewer 2 (Fable, testability and evidence lens), 2026-09-23

Read-only review of docs/plan/phase-11 … phase-19 (DRAFT, interviews DONE 2026-09-23) against PLAN.md (RV9–RV13,
P1–P6, the 2026-09-23 A11 amendment), INDEX.md (R11 / R12 / R13 pending, the Change Log), the R10 testability review
and triage (2026-09-22), qa/JEREMY-QA.md (the driver lessons of 2026-09-22), the phase 03 driver floor
(qa/phase-03/scripts/lib.sh) and the phase 05 method notes (qa/phase-05/README.md). Design and correctness are
Reviewer 1's lens and are not repeated. Default verdict where a Decision has no row: not proven.

Facts checked on disk today (no AVD was running; nothing below is a device probe):

- `Diagnostics.dump` prints `wall=<ms>` on every ring line and SpeechClient writes into the same ring, so a row may
  order a `[podbay]` line against a `[speech]` line by the ring's own clock (phase 14 E6 is feasible as written).
- `StartActivity.kt:149` already sets `beyondViewportPageCount = 1`, and phase 02's `gestures.sh` `ensure_start`
  asserts `start_page` present and `app_list` ABSENT on that build — so a composed-but-off-screen pager page is
  absent from `uiautomator dump` today, and phase 14's "no `pod_bay`" assertions can stand (recorded as a build-start
  check, finding 25).
- `qa/phase-02/baseline_layout.json` carries `addedOnce = [phase03:cortana, folder:games:v1, folder:office:v1,
  slot:music:v1]` and a `manualSizes` key. None of phases 16 / 17's new markers (`slot:calendar:v1`, `slot:people:v1`,
  `slot:photos:v1`, `slot:camera:v1`) is in it (finding 56).
- `EditGestures.kt:329`: the promoted tile is hit-tested "like any other" since J4 (JEREMY-QA, 2026-09-22). Phase 11's
  Decision "today a hold there enters no edit mode" predates that fix (finding 2).
- `LayoutStore.assignSlotOnce` (lines 127–139) has no guard for an existing explicit assignment — phase 16's finding is
  confirmed; its E1 upgrade pass is the right row.
- The manifest holds neither `CAMERA`, `WRITE_CONTACTS`, `READ_MEDIA_VIDEO`, `MANAGE_EXTERNAL_STORAGE`,
  `USE_FULL_SCREEN_INTENT` nor `FOREGROUND_SERVICE_MICROPHONE` today, as the docs say; `INTERNET` is already held.
- docs/plan/r11-inbox-apps.md, r12-w10m-stock-theme.md and r13-tv-channels.md do not exist; every "written once
  R11 lands" row is a placeholder, as the docs state.
- `qa/phase-03/scripts/provision.sh` grants HOME / ASSISTANT / SMS roles and installs with `-g`; it does not grant
  notification access, usage access or the keyboard (phase 12 says so and plans the change).

Severity: **BLOCKING** = a Decision that no row can prove, or a row that cannot fail / fails by design;
**SHOULD-FIX** = a row exists but cannot be driven, cannot fail on the AVD as written, or misses a lesson already
paid for; **NOTE** = record it so the next agent does not rediscover it.

Counts: 12 BLOCKING · 27 SHOULD-FIX · 21 NOTE (60 findings).

---

## Phase 11 — tile quick actions

1. **SHOULD-FIX — Interview Q1 (the shell's own tiles burst) has no row, and E3 / E10 still carry the pre-ruling
   wording.** Decisions: "Music declares Songs / Albums / Artists / Playlists and Start settings declares Start + theme /
   Tile apps / Setup / Diagnostics … Weather … and Tess … declare none". E3: "the Weather tile (shell, no shortcuts
   unless Q1 says otherwise)"; E10: "(per Q1) Music's satellites or `no shortcuts`". Nothing asserts the ids, the
   order, or that a satellite opens the named page. Fix — new row E15: hold the MUSIC slot tile → `quick_sat_label:0..3`
   = "Songs", "Albums", "Artists", "Playlists" (ranks 0–3), `[quick] shortcuts for app.tileshell/0: 4 (4 shown: …)`;
   `tap_node quick_sat:1` → `MusicActivity` resumed with the Albums pivot selected (the dump's pivot tag with
   `selected="true"`; name the tag). Hold `shell:settings` → "Start + theme", "Tile apps", "Setup", "Diagnostics";
   tap satellite 3 → `SettingsActivity` resumed with `settings_diagnostics` in the dump. Hold the Weather tile and the
   Tess tile → `edit_disc:*` present, no `quick_burst`, `[quick] no burst on <id>: no shortcuts`. Rewrite E3's Weather
   clause and E10's "(per Q1)" to the ruling.

2. **SHOULD-FIX — The promoted tile: the Decision and the edge case are stale since J4, and E5 holds a tile E4 has
   just promoted.** Decisions: "the promoted row … is none of them, so today a hold there enters no edit mode and
   therefore opens no burst"; edge cases: "the promoted tile (no edit mode today, so no burst; recorded)". JEREMY-QA J4
   (fixed 2026-09-22): "the promoted tile is hit-tested at its fixed place; edit mode already suspends the promotion, so
   the held tile shows in its grid place." E4 ends with "the fixture tile in `recent_app_row`", then E5 begins "each
   from a fresh burst on the fixture tile" — a hold on a promoted tile whose grid cell is elsewhere, with no expected
   satellite bounds. Fix: (a) rewrite the Decision to J4's behaviour and make it a row — hold the promoted fixture
   tile → edit mode, `recent_app_row` absent, the burst rests around the tile's GRID cell (satellite bounds relative to
   the tile's unpromoted bounds equal E7's corner case ± 1 px), `[quick] burst on <id>: 4 satellites`; (b) every row
   after a launch starts with `am force-stop app.tileshell` + Home (RecentApp is in memory only: recent0922.sh
   lines 19–24) so "the fixture tile" is the grid tile the row expects.

3. **SHOULD-FIX — Fixture tiles are pinned by hand per row instead of seeded through the verified restore.**
   Acceptance preamble: "tileclient-a and tileclient-b installed and pinned to Start from the app list (phase 02 E2's
   route)". JEREMY-QA P02 lesson (3): "baseline_layout.json marks its sizes hand-set so the use-based auto-sizer …
   cannot reshape the test layout"; lesson (1): restores go through `layout_restore`, which "brings Home back and
   compares what is on disk after the shell loaded it". A UI pin leaves the tile's size to the auto-sizer and its
   cell wherever the grid ends, so E7's "a medium tile in the grid's middle" has no fixed cell. Fix: a
   `qa/phase-11/baseline_layout.json` derived from phase 02's, with the two fixture tiles at named cells and
   `manualSizes` set, every `addedOnce` marker of the build under test, restored with `layout_restore` at each row's
   start; E7's variants (bottom row, wide at the right edge, band member, 1.5 screens tall) are separate baselines
   named in the row.

4. **SHOULD-FIX — E1 / E3 / E5 / E7 read `quick_sat:*` from `uiautomator dump` over live tiles.** Decisions already
   say the `[quick] satellite i rest=` lines are "the second source RV13 needs under flipping tiles", but the rows read
   the dump first and do not say which route they use; a default Start has Weather, Photos, Calendar and Music tiles
   flipping, and RV13's dump fails three times on a screen that never idles. Fix: the harness line names the route —
   phase 05's gesture-driver `UiDevice.dumpWindowHierarchy` with `Configurator.setWaitForIdleTimeout(0)` (the
   instrumentation that now exists, qa/phase-05/README.md) — or the baseline for these rows carries no live tile;
   and each bounds assertion takes the `rest=` line as primary with the dump as corroboration.

5. **SHOULD-FIX — E6 measures the peak (107 ± 17 ms), settle (250 ms) and alpha-out (≈100 ms) from screenrecord
   frames.** That is the P02 method problem verbatim ("motion timings from variable-rate screenrecord … a
   measurement-method problem"). See cross-phase finding 59 for the fix; here the concrete lines: the burst logs
   `[quick] motion open <tileId>: t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` and `[quick] motion close …` from
   its own frame clock, E6 asserts those numbers, and the screenrecord corroborates under phase 05's frame-spacing
   rule.

6. **NOTE — E13 asks phase 02 E7 to pass "every number unchanged"**; E7 is 7/10 open on exactly the motion timings
   (INDEX row 02). Scope the regression to E7's bracket and geometry sub-rows and say the motion sub-rows stay open
   until finding 59's method lands, or the row can never pass.

## Phase 12 — setup wizard

7. **BLOCKING — Interview Q2 (all of Tess's grants join the walk) has no row.** E1(b)'s adb grant list stops at
   phase 01 / 05's rows ("`pm grant` for READ_MEDIA_IMAGES / READ_MEDIA_AUDIO / READ_CALENDAR / ACCESS_COARSE_LOCATION,
   `ime enable` + `ime set`"); E2's N counts "the number of rows the checklist dump … lists" — the Setup checklist,
   not `CortanaChecklist`; E3 walks Notification access, Photos, Usage access, the two keyboard rows and the accent
   step, none of Tess's nine (assistant role, microphone, contacts, calendar write, texts, calls, location all the
   time, call log, read texts). After Q2 a `pm clear` state has all nine MISSING, so E3's "the dump shows the next
   step" lands on an unlisted step. Fix: E1(b) adds RECORD_AUDIO, READ_CONTACTS, WRITE_CALENDAR, SEND_SMS, READ_SMS,
   CALL_PHONE, READ_CALL_LOG, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION and `cmd role add-role-holder
   android.app.role.ASSISTANT app.tileshell`; E2's expected N is a hand-listed id set for that state (the Setup rows
   missing + the nine + 1); E3 gains the Tess steps in `CortanaChecklist` order with their observables — the ASSISTANT
   role sheet (`com.android.permissioncontroller`'s role dialog, tapped by bounds; `cmd role get-role-holders
   android.app.role.ASSISTANT` = app.tileshell), each runtime dialog, and "location all the time", which on API 30+
   is not a dialog but the app's location page (`Settings$AppLocationPermission…`; assert the resumed activity and
   `appops get app.tileshell ACCESS_BACKGROUND_LOCATION` = allow on return) — plus an ORDER assertion (Setup rows,
   then Tess's rows, accent last) and `[wizard] step assistant: granted` lines.

8. **BLOCKING — Interview Q3–Q6 (six theme presets applied live; the four AI pictures; R12) have no acceptance row,
   no edge case, no test tag, no diagnostics line, and no NEEDS-HUMAN row in the H list.** Evidence: E3's accent
   step is pre-Q3 ("the dump has `wizard_accent` … tap `accent:Red`"); the Test tags line ends at `wizard_done`;
   Decisions promise "NEEDS-HUMAN: labels readable over each picture … judged on the phone" but H1–H5 do not carry it.
   Fix — rows: **E11 presets** (the wizard step AND Settings > Start + theme, same assertions): `preset:<name>` for
   Default, Windows 10 Mobile (original), HAL, Soft, Lumia, Midnight present in that order; tap `preset:Midnight` →
   `run-as app.tileshell cat shared_prefs/start_theme.xml` holds the preset's accent, theme = dark, background = the
   bundled `midnight` URI, tile transparency, press style and `transparency_effects=false` (phase 13's key), Tess's
   ring / lens colour keys and the keyboard's colour keys (name the ADD keys in Decisions), a Start tile pixel and a
   tile-free background pixel read the preset's values ± 2 (phase 01 `e13_pixels.py`), `[wizard] preset Midnight` /
   `[theme] preset Midnight applied` line; tap `accent:Red` → `preset:Custom` selected and every OTHER key unchanged;
   tap `preset:Default` → every key equals the out-of-box values recorded from a `pm clear` state at the row's start
   (the control); `am force-stop` + Home → persisted. **E12 pictures**: the bundled assets' dimensions read from the
   APK (`unzip -p … | identify`) are portrait at 1440 × 3120 plus the 1.3× parallax headroom; the source files in
   `art/themes/` are ≥ 1024 px wide (a host check); with phase 13 present the `[fluent] static backdrop rebuilt for
   <uri>` line follows each preset. **E13** "Windows 10 Mobile (original)" — not runnable until R12 (say it as phase
   17's E19 does). **NEEDS-HUMAN**: H6 [accept] each preset's look; H7 [accept] labels readable over each picture in
   its theme, on the phone; H8 [accept] the four pictures themselves (an original design with no mark — no metric
   exists); H9 [fidelity] the original preset against R12. **Edge**: a preset applied under battery saver (phase 13's
   rule wins; the key is still written); a preset whose picture asset is missing (fallback + line); a preset applied
   over a user's own picture (state whether the user's picture is kept anywhere); Transparency effects toggled after a
   preset → Custom.

9. **SHOULD-FIX — Interview Q1's "why" lines: "the acceptance rows check that every step shows one", and none
   does.** There is no tag for the line (`wizard_body` is `ChecklistRow.detail`, the state text). Fix: tag
   `wizard_why`; a table of the why lines per row id in Decisions (the copy must be in the doc or a row can only assert
   "non-empty"); E2 / E3 assert `wizard_why` text equals the table's line for each step seen; H-row for the copy.

10. **SHOULD-FIX — The "wizard step added" pattern later phases must re-run is not written here.** Decisions (Q2's
    reading): "every later phase that adds a grant … adds its step to the wizard the same way each phase adds its
    checklist row". Phases 15–19 add seven grants (finding 57) and only phase 18 mentions its step. Fix: a named row
    template in this doc — "`pm clear` + revoke <grant> + Home → `wizard_step:<id>` present with its `wizard_why`;
    grant from adb → absent; `provision.sh` gains the grant; E1 re-run" — that each later doc cites by number.

11. **NOTE — E2's N is compared against the checklist dump**, which is the wizard's own source list (`Checklist.kt`),
    so a mis-derivation that affects both passes. Hand-list the expected ids (finding 7 does this).

12. **NOTE — E3's restore ("accent back to Default Blue through Settings")** no longer restores the theme file once
    presets write seven keys; say `preset:Default` or `pm clear` + `provision.sh`.

13. **NOTE — E10 (X7, 217 ms) from a "60-fps screenrecord"** — finding 59.

## Phase 13 — Fluent materials

14. **BLOCKING — Interview Q3 B (a radial light under the finger) has no values, no row, and E7 asserts Q3 A
    only; E7's interior sample sits where the new light is brightest.** Decisions record "border light AND a radial
    light … following the finger … Values are P4 … judged in a NEEDS-HUMAN row", but the "Reveal on touch (per Q3;
    lean A)" paragraph still specifies only "a 1-epx border ring". E7: "the item's interior 2 epx inside the ring reads
    R7 §3.6.3's (79,84,80) ± 2" — with a soft light centred on the touch point that pixel either changes (E7 fails)
    or the light is invisible (Q3 B is not built). Fix: Decisions state the light's form — radius r epx, peak white
    opacity, falloff, and that its centre follows MOVE events; E7 becomes: DOWN at the item's centre → the ring as
    before; the pixel at the touch point reads the pressed fill lightened by the peak ± 4; a pixel at 0.5 r reads
    between the two; a pixel beyond r (inside the item, clear of the ring) reads (79,84,80) ± 2 — and that is where
    every pressed-fill sample is taken from now on; `MOVE` +30 px → the bright centre moved (two captures); `UP` →
    gone in the next frame; acrylic off → no ring and no light. E11's regression of phase 03 E15 states the sample
    rule (≥ r from the touch point, or after UP) so E15's numbers hold. H4 names both lights.

15. **SHOULD-FIX — E3 / E4 / E5's expected values ("0.8·(47,45,47) + 0.2·(blurred photo)", "0.2 × (blurred
    backdrop)", "lighter … by 0.2 × (blurred title)") do not say who computes the blurred term.** Read from the
    capture under judgement, the row compares a value with itself. E2 avoids this (the checker's far-from-edge
    values are arithmetic). Fix: "expected = 0.8·T + 0.2·GaussianBlur_host(σ = 0.57735·r + 0.5 px)(fixture) at the
    same pixel", computed on the host from the pulled fixture photo / the app-list capture with acrylic OFF (E1's
    control), by a named script (`qa/phase-13/scripts/acrylic_expect.py`).

16. **NOTE — E11 re-runs "phase 03 E15 … unchanged … with their numbers"**; E15 has never run (INDEX row 03:
    "E13-E15 … NOT RUN"), so there is no prior pass and no driver to re-run. Write "run, first time if necessary, and
    pass", and list E15's driver as a dependency of this gate.

17. **NOTE — Q2's cross-line "a preset can turn acrylic off" is asserted in neither doc.** Finding 8's E11 covers
    the key; add one E1 sub-row here: after `preset:Midnight`, `[fluent] acrylic=off reason=setting`.

18. **NOTE — E8's four motion numbers from screenrecord** — finding 59.

## Phase 14 — pod bay

19. **SHOULD-FIX — Q1 ruled widgets OUT, but the doc keeps the live branches.** Scope Out: "unless interview Q1
    rules them in (then the interview adds the build task and rows named under Q1)"; build task 7 "Only if Q1 is A or
    C"; P4 "(only if Q1 is A or C)"; the last edge case "If widgets are in (Q1 A or C)". A future agent reads an open
    option. Strike them (one line "ruled out 2026-09-23" is enough).

20. **SHOULD-FIX — E3 Weather compares the pod's text with "the report's formatted values" without saying who
    formats.** If the expected strings come from the shell's `WeatherFormat` (or the tile), the pod is compared with
    itself. Fix: the row writes the expected strings for `weather_fixture.py`'s report by hand (place, "72°", the
    condition text, "H 80° L 60°", precipitation), as the Agenda sub-row already does.

21. **SHOULD-FIX — Launches from pods promote the app, and the next assertion on Start's grid meets a promoted
    tile.** E3 (Now playing → the MUSIC slot app), E9 (`am start` DeskClock, then Tess), E13 (phase 01 E2 / E12 /
    E20 re-runs) each change `recent_app_row` (JEREMY-QA E7: "the dark pass promoted People, so the light pass held a
    tile not in the grid"). Fix: after any launch the row does `am force-stop app.tileshell` + Home before the next
    grid assertion (RecentApp is in memory only), stated once in the harness line.

22. **NOTE — E6's ordering ("the `[podbay] opened by voice (doors)` line's wall time is after the speech ring's
    `SpeakingDone` line") is feasible** — the ring prints `wall=<ms>` on every line and both writers share it. Say
    the driver reads `wall=` from the ring, never the host clock.

23. **NOTE — Absence assertions ("`start_page` and no `pod_bay`", E1; "no `pod_bay`, no `app_list`", E10) rely on
    off-screen composed pages being absent from the dump.** `StartActivity.kt:149` already runs
    `beyondViewportPageCount = 1` and `gestures.sh` asserts `app_list` absent on Start, so it holds today; record it
    as the build-start check (a Compose upgrade could change it) and, as a second source, the `[start] page=<name>`
    line E1 already reads for Home.

## Phase 15 — Alarms & Clock, Calculator, Voice Recorder

24. **BLOCKING — Interview Q5 (Tess does arithmetic through the Calculator engine; standing principle P6) has no
    acceptance row, no utterance, no diagnostics line, no edge case and no H-row.** Decisions: "Spoken and typed
    ("15 % of 80 is 12."), an ADD to phase 03's matcher and action layer". E9 covers alarms and timers only; nothing
    proves the answer is computed by code (P6). Fix — E26: `say calc_percent` ("what's 15 percent of 80") → `[match]
    "…" -> Arithmetic(…)` and `[calc] tess "15 % of 80" -> 12`, `reply_text` = "15 % of 80 is 12." with phase 03's
    RMS rule; `type_request "what is 2 plus 2"` → "2 plus 2 is 4."; "what's 1 divided by 0" → "Cannot divide by
    zero." (the engine's string, spoken); over the keyguard → answered (state `LockGate.allowedWhileLocked` for it —
    no personal data); negatives: "what's the weather like" still matches Weather, "calculate my life" → the
    not-understood handler; expectations host-computed (calc-cases.tsv gains a `tess` column, never the app's
    engine); the utterance ids added to `utterances.py`; H17 [accept] the spoken forms and P6's wording. Edge: very
    large results (e-notation spoken?), "point" and "percent" as words, a unit conversion asked of Tess (Q4's
    converter through the same path, or not-understood — say which).

25. **SHOULD-FIX — Q2's second half (Voice Recorder lists EVERY `IS_RECORDING` file, whichever app made it) has no
    row, and E20 still hedges a ruled question.** E20: "does NOT list the recording under Q2 A (or DOES under B; the
    row asserts whichever was ruled)". Fix: E14 gains both directions — `adb push` a host-made 3-s .m4a to
    `/sdcard/Recordings/other.m4a` + scan (MediaStore sets `is_recording` for Recordings/ on API 31+; if the AVD
    does not, `content update … --bind is_recording:i:1`) → `rec_row:` lists it with `rec_duration:` 3 ± 0.5 s;
    a .m4a pushed to `/sdcard/Music` with `is_recording` 0 is NOT listed here and IS in Music's songs pivot; E20
    rewritten for A only, with a `[music] skipped recording <id>` line so the skip is visible (silent-empty rule).
    P-row: Samsung Voice Recorder's files on the S25U appear here.

26. **SHOULD-FIX — Q3's "survive uninstalling the shell" has no row.** Fix: after E14, `adb uninstall app.tileshell`
    → `adb shell ls /sdcard/Recordings` still lists the take and `content query … is_recording=1` still finds it;
    reinstall through `provision.sh` (RV12 restore).

27. **SHOULD-FIX — Q4 (the converter, every category but Currency): E12 asserts two conversions and nothing about
    the category set.** Fix: E12 asserts the converter's category list equals exactly [Volume, Length, Weight,
    Temperature, Energy, Area, Speed, Time, Power, Data, Pressure, Angle] (dump), "Currency" absent (the A11
    negative made explicit), and calc-cases.tsv gains ≥ 2 host-computed cases per category with Windows' rounding
    stated (e.g. 1 GB → 1024 MB or 1000 MB — R11 §Calculator or an approximation under H11).

28. **SHOULD-FIX — E6 / E7 read ticking screens (`timer_remaining:`, `stopwatch_elapsed`) with `uiautomator dump`,
    and E7 times "advanced by 10.0 ± 0.5 s" between two dumps on the host clock.** RV13: a screen that never idles
    fails the dump three times; JEREMY-QA P02 EDGE part 8: "judged by the dwell the shell logs … not the host
    clock" — two dumps take 1–3 s each. Fix: dump through phase 05's gesture driver (waitForIdleTimeout 0), or add
    `[stopwatch] elapsed=<ms> uptime=<ms>` / `[timer] remaining=<ms> uptime=<ms>` lines and assert Δelapsed =
    Δuptime ± 100 ms from the ring.

29. **NOTE — E4 asserts "vibrates" with no observable.** `dumpsys vibrator_manager` lists the shell's request on the
    AVD; cite it or drop the word.

30. **NOTE — E10 / E24 pivot settle (250 ± 17 ms) from screenrecord** — finding 59; and the App Shortcuts row —
    finding 58.

## Phase 16 — Calendar and People

31. **BLOCKING — Interview Q2's four rules promise rows that do not exist, and E9 exercises the one case Q2 does
    not forbid.** Decisions: "Acceptance rows prove each rule on the AVD with a local calendar and two
    sync-adapter-created account calendars (one allowed, one not): no write reaches the disallowed one by any path
    (app, Tess, Sync)". No such row: E3 / E4 use the local calendar only; E9 runs Tess "with the shell's local
    calendar and no fixture calendar" — but today's `ActionLayer.writableCalendarId` picks any CONTRIBUTOR-or-better
    calendar, so with an account calendar present it would write THERE, which is what rule 2 forbids; the "Calendar
    data (agent, pending Q2; written for its lean A)" paragraph still says "reads and writes `CalendarContract` —
    every calendar the provider holds". No `[calendar] sync …` diagnostics line, no edge case, no H-row for the
    Sync design. Fix — E22 Sync rules: insert two QA account calendars through the sync-adapter URI
    (`account_type:s:com.google`, "Personal" and "Work", both access 700); **rule 1**: both list under
    `cal_calendar_row:` and NEITHER appears in the editor's calendar picker (dump), and after an editor create, an
    edit and a Tess "add … to my calendar" (reply "Added to your calendar."), `content query …/events --where
    "calendar_id=<personal>"` and the Work query are both 0 while the local calendar holds the events (**rule 2**);
    **rule 4**: on a fresh install Sync's picker is EMPTY with its line (`[calendar] sync event=<id>: no calendar
    allowed`); enable Personal on the "Can sync to" page (`cal_sync_allow:<id>`) → the picker lists Personal only;
    **rule 3**: Sync → Personal holds ONE copy (title, dtstart, calendar_id), the local event's row reads "synced to
    Personal" (dump), Work still 0, `[calendar] sync event=<id> -> Personal: ok`; edit the local event, Sync again →
    the copy updated, still one row (no duplicate); a JVM negative that the guard refuses a disallowed id
    (`refused: not allowed`) — and the ring line asserted; Birthdays never in the picker (Q3). Edge: sync target
    deleted; local event deleted after a sync (is the copy kept? state it); the allowed list cleared after a sync.
    H-rows [accept]: the Sync picker, the "synced to" marker, the "Can sync to" page and its all-off default. P-row:
    on the S25U the copy appears in Google Calendar after its sync adapter runs; `dumpsys netstats --uid` unchanged
    for the shell.

32. **SHOULD-FIX — E12 names "the K-9 fixture" for the Mail slot.** The stash's mail fixture is FairEmail (R10
    testability facts, `dumpsys shortcut`). Name the APK that exists or the row cannot run.

33. **NOTE — E1's upgrade pass installs "the last pre-phase-16 build (its git tag recorded)"** — say where the APK is
    kept (`qa/phase-16/upgrade/<tag>.apk`) so the row is reproducible after the tag's build outputs are gone. The row
    itself is well formed (both directions, the Music negative, the query-activities control).

34. **NOTE — E11 (People tile, A9): the 7.7-s period is measurable on a variable-rate capture (frames appear on
    change), but the ≈333 / ≈583 ms slides need phase 05's frame-spacing rule and a `[people] tile event <n>
    t0=<uptime>` line as the clock** — finding 59.

## Phase 17 — Photos, Camera, Movies & TV

35. **BLOCKING — Interview Q3b (the online hub: catalogue, "Watch on <service>", media server) contradicts the
    doc's own Goal / Scope / E13 and has no row; nothing says how it is proven without accounts or with the network
    off.** Goal: "Nothing here uses the internet (A11)"; Scope Out: "streaming or any http source (A11, R10-Q4: an
    `http://` VIEW is refused)"; the 2026-09-22 R10-Q4 Decision: "no online catalogue, no streaming: the video player
    refuses network URIs"; E13: "`http://127.0.0.1:1/x.mp4` → … `[video] refused scheme=http`". Under Q3b the media
    server IS an http source, so E13 fails by design or the hub is built to satisfy E13. The catalogue's source, its
    key (TMDB needs one — where does it live in an APK?), and the provider-availability source are unnamed, so no
    assertion can be written. Fix: (a) strike the A11 lines and re-cut E13 to the hub's rule for an external
    `http://` VIEW (play it, or refuse with "open it in Movies & TV" — state which); (b) name the catalogue and
    availability sources (a keyless one — Wikidata / TVmaze — or say how a key ships); (c) **E20 catalogue** against
    a host fixture: the driver serves recorded JSON with `python3 -m http.server` at `10.0.2.2:<port>`, the hub's
    base URL set through a QA-only pref written with `prefs_edit.py` (or the same "server address" setting the media
    server needs); search "Blade Runner" → `hub_result:<id>` rows with the fixture's titles and years, artwork loaded,
    `[video] catalogue "Blade Runner": 3 results`; `cmd connectivity airplane-mode enable` → the offline line in the
    dump and `[video] catalogue … offline`, the local pivot still lists `qa-steps.mp4`; a 500 from the fixture →
    `error 500`; restore airplane mode (RV12: network on is baseline); (d) **E21 "Watch on"**: a QA fixture APK (phase
    02's test-APK pattern) declares the intent filters the doc lists per service (e.g. `https://www.netflix.com/title/
    <id>`) and shows the received URI in a TextView; tap "Watch on QA-Flix" → the fixture resumed with the exact URI
    (`dumpsys activity activities` + the TextView); a title with no installed provider → "Not on this phone" line;
    (e) **E22 media server**: a Jellyfin container on the host with `qa-steps.mp4` in its library, reached at
    `10.0.2.2:8096`; sign-in → the library lists it and it plays under E11's pixel rule; wrong password →
    `[video] server … unauthorised`; container stopped → `unreachable`; where the token is stored is a credential
    (the project's adversarial-review rule for trust changes applies; name the store); (f) P-rows: the real services
    on the S25U with Jeremy signed in — for each, whether the deep link lands on the title or the service's home,
    recorded; Jeremy's own server; (g) H-rows [accept]: the hub's layout, the "Watch on" row and the server sign-in
    page are P4 designs (R11 §Movies & TV can measure only the local half; the Store half was Microsoft's); (h)
    diagnostics lines added to E18: `[video] catalogue <q>: n | offline | error <code>`, `[video] watch-on <service>
    <title> -> <intent> | not installed`, `[video] server <host>: connected | unreachable | unauthorised`.

36. **BLOCKING — Interview Q1 C (the full editor + video trim) and Q2 C (every camera mode) have rows for a
    fraction of what they rule.** E6 covers crop and rotate only ("Crop `qa-photo-1` … rotate → a new row"); E7 / E8
    cover a still and a video. Nothing proves straighten, auto-enhance, light and colour, filters, red-eye, video
    trim, timer, grid, tap-to-focus, zoom, the pro dial, panorama, slow motion or Living Images — and the Decision's
    own promise ("a mode the phone's camera cannot do … is not shown, and the diagnostics say why — no mode that
    silently fails") has no row either. Fix — **E6** gains one host-checkable assertion per tool on the flat-colour
    fixtures: filters / light / colour / auto-enhance: a table of expected RGB per fixture colour per tool computed on
    the host from the tool's stated transform (the transform must be in Decisions), copy count +1, original md5
    unchanged; red-eye: a fixture with a pure-red disc → the disc's red channel down by ≥ 50 %, outside unchanged;
    straighten: a fixture with a line at a known angle → output dimensions / crop as stated; **E6b video trim**: trim
    `qa-steps.mp4` to 2–5 s → the copy's `ffprobe` duration 3.0 ± 0.1 s, first frame = colour 3 (E11's pixel rule),
    original md5 unchanged. **E7** gains: timer 3 s → `[camera] timer 3s -> shutter` and the JPEG's `date_added` ≥
    the tap's uptime + 3 s read from the shell's line (not the host clock); grid → a screencap row shows the two
    lines at 1/3 and 2/3; tap-to-focus → `[camera] focus at x,y: <state>` (the virtual camera's autofocus support
    read from `dumpsys media.camera` first); zoom 2× → the virtual camera's known scene object is twice as wide in
    the capture; **mode availability, both directions**: the row reads the AVD's hardware level and capabilities
    and asserts the mode list shown equals what they predict, each hidden mode with `[camera] mode <x>: unavailable
    (<reason>)`. **P-rows** (new): the pro dial with EXIF read-back from the JPEG (ISO, exposure time, white
    balance — an observable, not only "looks right"); panorama (output width > the sensor width, EXIF); slow motion
    (`ffprobe` r_frame_rate ≥ 120); Living Images (a still plus its companion clip in MediaStore — state the pairing
    rule). The Decision's "licence checked at build start, P5" points at the pinch-zoom row; number the licence
    check. H-rows: the editor's tool UI and the panorama UI are not in the Y table — add them.

37. **SHOULD-FIX — Q5 A: E9 asserts IMAGE_CAPTURE only.** Add: the fixture starts VIDEO_CAPTURE with `EXTRA_OUTPUT`
    → RESULT_OK with an mp4 `ffprobe` decodes at that URI; Back → RESULT_CANCELED and no `is_pending` row.

38. **SHOULD-FIX — Q4 A: E3 counts the videos but never proves the shared-player hand-off.** Add to E3: tap the
    video row in Photos → `VideoActivity` resumed (`dumpsys activity activities`), `[video] playing <id>`, and no
    player node inside Photos' own dump (one player surface, Hard Rule 16).

39. **SHOULD-FIX — E5's slideshow interval is timed from host-clock screencaps** ("screencap pixels at t,
    t+interval"). E11 does it right ("timed from the `[video] playing` line"). Make E5 read `[photosapp] slideshow
    next <id>` lines (add them) the same way.

40. **NOTE — E1 starts from `pm clear` + Start and asserts slot tiles** — finding 57 (the wizard will be on top).

## Phase 18 — Files

41. **BLOCKING — Interview Q3 C (a shell-owned Recycle Bin) is contradicted by E4 and has no row, and the bin's
    location and `.nomedia` rule are unstated.** E4: "delete `c.bin` after the confirmation (Q3 A) → `ls` fails" —
    the opposite of the ruling. Fix: Decisions state the bin path per volume (e.g. `<volume>/.tessera-bin/` with a
    `.nomedia` marker and the original-path record), then **E4b**: delete `c.bin` → `ls /sdcard/QA-Files/c.bin`
    fails, `ls <bin>/…/c.bin` succeeds, md5 equal, `[files] bin: c.bin -> <path>`; Restore → back at the original
    path with the same md5; Restore over an existing name → the clash dialog, each choice does what it says; Delete
    permanently → gone everywhere; Empty → the bin has no entries; a delete on the virtual public volume (E2's
    disk) lands in THAT volume's bin (`ls /storage/<UUID>/…`); a deleted photo leaves Photos and the tile within
    3 s (`.nomedia` + scan) and does not reappear; a file removed by another app (`adb shell rm`) is NOT in the bin
    (the negative); the bin's entries are absent from Recent. H-row [accept]: the bin page and wording (P4; H2's
    "delete rule" is now this). Edge: a delete on a full volume (a rename needs no space — assert it works with
    `fallocate` fill); the bin folder deleted by another app; `adb uninstall` leaves the bin's files.

42. **BLOCKING — Interview Q2 C (zip and a Recent view) has no row, and zip extraction is a path-traversal
    surface with no negative.** Fix — fixtures made on the host (python `zipfile`: known entries, plus `qa-bad.zip`
    holding an entry named `../../evil.txt`, plus `qa-corrupt.zip` = `head -c 500`); **E13 zip**: open `qa.zip` as a
    folder → `files_row:` lists its entries with sizes; extract → `ls` + md5 per entry; `qa-bad.zip` → the entry is
    refused with `[files] zip: refused entry ../../evil.txt` and `ls /sdcard/evil.txt` fails, the other entries
    extracted (both directions); `qa-corrupt.zip` → the error line, no crash; create a zip from a two-file selection
    → the pulled file's `unzip -l` lists exactly those names and `unzip -t` passes; a 200 MB zip runs through the
    foreground service with cancel (E4's pattern, no partial file). **E14 Recent**: fixtures `touch -d` at three
    dates plus one `adb push`ed file → after a scan the view lists them newest first by `date_modified` (exact order
    asserted); a file renamed in Files rises to the top within 3 s; a file not yet scanned and one in a `.nomedia`
    folder are absent — stated, so their absence is a rule and not a gap; the bin's entries absent. Diagnostics
    lines for both added to E12's list.

43. **SHOULD-FIX — Q1's "a setup-wizard step with its 'why' line" has no row.** Add finding 10's template: `pm
    clear` + `appops set app.tileshell MANAGE_EXTERNAL_STORAGE default` + Home → `wizard_step:files` with its
    `wizard_why`; `provision.sh` gains `appops set … allow`; phase 12 E1 re-run on this build.

44. **SHOULD-FIX — The App Shortcuts Decision includes "SD card while one is inserted" — a dynamic shortcut with
    no row.** E2's virtual-disk row gains: mount → `dumpsys shortcut` lists `sdcard` for app.tileshell; unmount →
    gone; a phase 11 burst on a pinned Files tile shows three satellites, then four (finding 58's method).

## Phase 19 — Settings front

45. **BLOCKING — Interview Q3 C (five new helper verbs) has no row in any doc, and the doc says the rows belong
    elsewhere.** Decisions: "battery saver, location, NFC, hotspot, automatic time. Each new verb is a TRUST change …
    proof on the S25 Ultra by R4's method, a named verb in the allow-list … and the adversarial review". E9 covers the
    four ruled toggles; the per-page table still reads "helper only per Q3" on Battery saver, NFC, Hotspot and
    Location; build task 4: "further verbs are phase 04 ADDs (its allow-list, its R4 proof, its review), not this
    phase's" — and phase 04's doc does not know about them. Fix: name the owner in both docs, then wherever they
    land: one row per verb with its observable, both directions and RV12 restore — battery saver `settings get
    global low_power` (on → off → on); location `settings get secure location_mode`; NFC `dumpsys nfc` — on the AVD
    there is no NFC hardware, so the verb reports `unsupported` and the row IS the fallback line (a negative, not a
    skip); hotspot `cmd wifi status` / `dumpsys wifi` soft-AP state; auto time `settings get global auto_time`
    (restore `1`, RV12); the allow-list negative (an unlisted verb string refused and logged); each row's state with
    the helper stopped; P3 extended to the five with R4's method on the S25U; the adversarial review recorded as a
    build-task gate, not a sentence.

46. **SHOULD-FIX — Q2 C's "Apps" category is absent from the per-page table, so E1 ("each category page lists
    exactly the table's pages") and E8 cannot be written for it.** Fix: table rows — Apps > Installed apps (owned:
    the app list's data with Uninstall through `REQUEST_DELETE_PACKAGES` → the package installer's dialog resumed;
    the self-package exclusion re-asserted), Apps > Default apps (link `MANAGE_DEFAULT_APPS_SETTINGS`), Apps > App
    permissions (link `APPLICATION_DETAILS_SETTINGS` with `package:` per app), each with its AOSP activity; E8 rows
    for them; H-row [accept] for a category with no W10M original (P4).

47. **SHOULD-FIX — Q1 A: E2's "Android's Settings entry in the app list per Q1 (dump)" cannot fail as written.**
    Fix: `applist_row:com.android.settings` label text equals "Android settings" and files under A (not S); the
    shell's entry under S reads "Settings"; search "settings" returns both with ours first; `aapt dump badging`
    label = "Settings"; strike the stale edge case "Two 'Settings' entries (Q1 C)".

48. **NOTE — E9 is listed under Emulator, but the helper is R4-gated on the phone.** Say whether the helper can be
    started on the AVD (Wireless-debugging pairing exists on the emulator?) or move E9 to P3 with an AVD-only
    "helper stopped" state row.

49. **NOTE — Y4 page and toggle motion (UNMEASURED)** — finding 59.

---

## Cross-phase

50. **BLOCKING — The baseline layout does not carry the new one-time markers, so every regression run these docs
    cite re-runs the seeds and rearranges Start (JEREMY-QA P02 lesson (2), again).** `qa/phase-02/baseline_layout.json`:
    `addedOnce = [phase03:cortana, folder:games:v1, folder:office:v1, slot:music:v1]`. Phase 16 adds `slot:calendar:v1`
    and `slot:people:v1`, phase 17 `slot:photos:v1` and `slot:camera:v1`; phase 11's fixture tiles and 15's pinned
    tiles are not in it either. Every `layout_restore` then re-runs `assignSlotOnce` and re-points the slots (the
    2026-09-22 fault exactly), and 11 E13, 12 E9, 13 E11, 14 E13, 15 E1, 16 E2, 17 E2, 18 E10 and 19 E13 inherit it.
    Fix: each phase that adds a marker updates the baseline (and `manualSizes`) in its harness build task, keeps the
    previous file as `baseline_layout-pre-<phase>.json`, and its regression row asserts that after `layout_restore`
    the ring holds ZERO `assignSlotOnce … -> assigned` lines and the restored `addedOnce` equals the file's.

51. **BLOCKING — Phase 12's wizard stands on every later `pm clear`, and phases 15–19 add grants without the
    provisioning line or the wizard-step row.** Phase 12: "`show ⇔ (some CORE row is MISSING) ∧ ¬finished`", Q1 made
    every grant row core, Q2's reading makes every later grant a step; `pm clear` resets runtime permissions
    (phase 12 says so). Phases 15 (`USE_FULL_SCREEN_INTENT` appop, the microphone row), 16 (`WRITE_CONTACTS`), 17
    (`CAMERA`, `READ_MEDIA_VIDEO`), 18 (`MANAGE_EXTERNAL_STORAGE`), 19 (`WRITE_SETTINGS`, DND access) each add a
    checklist row; phase 16 E1 and 17 E1 start "`pm clear app.tileshell`, Home" and assert Start's slot tiles — they
    will find `wizard_page`. Only phase 18 mentions its step; none names its `provision.sh` line. Fix per doc: (a) the
    `provision.sh` line(s) for its grant; (b) finding 10's "wizard step added" row with its why line; (c) phase 12 E1
    re-run on its build; (d) every `pm clear` row reads "`pm clear` → `provision.sh` (or the explicit grants) → Home".

52. **SHOULD-FIX — The phase 11 Q1 standing rule ("a build task and an acceptance row carry it") is carried by none
    of phases 15–19.** Each records its ids in Decisions ("The App Shortcuts under the phase 11 Q1 standing rule
    (agent …)") and has neither the build task nor the row. Fix per doc: build task "static `shortcuts.xml` with ids
    <…>, ranks 0–3, targeting <activity> with the page extra"; row "pin the app's tile (through the phase-11
    baseline); hold → `quick_sat_label:0..3` texts equal [names] in rank order (phase 11 E3's method); tap each → the
    app resumed on that screen (the page's own tag selected); `dumpsys shortcut` lists the ids with those ranks" —
    so the four-satellite limit and the order can fail.

53. **SHOULD-FIX — Motion rows across the nine docs take their clock from a variable-rate screenrecord.** 11 E6,
    12 E10, 13 E8, 15 E10 / E24, 16 E11 / E19, 17 E5 / Y6, 19 Y4. qa/phase-05/README.md: "The emulator's screenrecord
    is variable-rate. It emits a frame only when the screen changes, so frames-per-second over a second means
    nothing"; JEREMY-QA P02: phase 02 E7 is open on exactly this. Fix, one rule for all nine: every motion the shell
    animates logs its own clock (`[motion] <name> t0=<uptime> settle=<ms>`, a spring's peak and overshoot from its
    own progress, from `withFrameNanos`); the row asserts the logged numbers against RV11's tolerance; the
    screenrecord corroborates under phase 05's rule (source-frame spacing ≤ 18.2 ms DURING the motion, a ticker where
    nothing else changes) and is never the primary clock. Phase 11's Decisions already have the frame maths; only the
    source of t needs to change.

54. **SHOULD-FIX — Launches promote a tile, and the next assertion on Start's grid meets it.** 11 E4, 14 E3 / E9 /
    E13, 16 E1, 17 E1, 18 E6 / E10. RecentApp is in memory only (recent0922.sh lines 19–24), so `am force-stop
    app.tileshell` + Home clears it. Each doc's harness line says it once, and each row that launched something does
    it before its next grid assertion.

55. **SHOULD-FIX — Dumps of screens that never idle need the route named.** 11 (the burst over live tiles), 15
    (timer, stopwatch), 16 (the People tile cycling on Start), 17 (the viewfinder, the player). RV13's route now
    exists as phase 05's gesture-driver `UiDevice.dumpWindowHierarchy` with `waitForIdleTimeout(0)`; each doc's
    harness line should cite it instead of "dumps follow RV13".

56. **NOTE — Stale "pending / lean / if" wordings a future agent will read as open**: 11 E3 / E10 ("unless Q1 says
    otherwise", "(per Q1)"); 14 Scope, task 7, P4, the last edge case; 15 E9 "(pending Q1, written for its lean A)",
    E12 "(if in)", E20's "(or DOES under B)"; 16 Decisions "pending Q2 / Q3 / Q1; written for its lean A" (three
    paragraphs); 17 E6 "(Q1 B / C)", E3 "With Q4 A"; 18 E4 "(Q3 A)", the preamble "Written for Q1 A; re-cut if Q1
    rules otherwise"; 19's table "helper only per Q3" ×4, the edge case "(Q1 C)". One pass per doc replacing each
    with the ruling.

57. **NOTE — Research gates are pending and absent on disk** (R11 for 15–19, R12 for 12, R13 for the TV-channels
    doc). The docs say so and their "written once R11 lands" rows are honest placeholders; phase 12 should say the
    same for its original-W10M preset (finding 8's E13).

58. **NOTE — "Recorded, not gated" rows are labelled and fine**: 13 E9, 15 E21's last clause, 16 E12's resolver
    note, 17 E14 / P-rows. A row that only records must keep saying so in its PASS/FAIL line ("RECORDED") so the
    computed summary (lib.sh `row_end`) does not count it as a pass.

59. **NOTE — Every phase's `pm clear` and grant rows must end with `provision.sh`** (phase 12 says "Rows that end
    on a fresh state re-run `provision.sh` before the next row"; 15–19 do not) — RV12's restore for a wiped state.

60. **NOTE — The A11 amendment reaches only phase 17 here.** 15, 16 and 18 keep "nothing here reaches the internet
    (A11)", which is still true for those apps under "offline preferred"; only 17's lines conflict (finding 35).
    Phase 10's Music streaming and the TV-channels app are outside these nine docs (INDEX "not split").
