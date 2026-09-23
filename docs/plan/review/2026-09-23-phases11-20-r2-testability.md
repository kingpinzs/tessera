# Phases 11–20 DRAFT docs — Reviewer 2, round 2 (testability / evidence lens), 2026-09-23

**BLOCKING remains: YES — 8** (#5, #6 phase 12; #17 phase 15; #26 phase 17; #38 phase 20; #52, #53, #54 cross-phase).
**Counts: 8 BLOCKING · 37 SHOULD-FIX · 19 NOTE (64 findings, numbered globally 1–64).**

Reviewer: Opus, replacing Codex for round 2 (Reviewer 1 covers design/correctness; nothing here re-argues a design unless a
row cannot be run, cannot fail, or fails by design). Inputs read in full: phase-11 … phase-20; PLAN.md (P1–P6, RV9–RV13,
the 2026-09-23 rulings); INDEX.md; qa/JEREMY-QA.md (J1–J7, P02, ENV); review/2026-09-23-phases11-19-{testability,triage}.md;
r12-w10m-stock-theme.md; r11-inbox-apps.md and r11/*.md; qa/phase-03/scripts/{lib.sh,provision.sh,j7.sh};
qa/phase-02/scripts/layout.sh. Code read to check row claims: `diag/Diagnostics.kt`, `cortana/speech/SpeechService.kt`,
`cortana/speech/SpeechClient.kt`, `prefs/ShellSettings.kt`, `start/StartPage.kt:412-413`, `music/MusicService.kt:151-187`.
Device read (read-only, no state changed): `dumpsys power` on emulator-5554 → `mIsPowered=true`, `dumpsys battery` →
`AC powered: true`, `settings get global low_power` → 0.

Severity: **BLOCKING** = a dated Decision no row can prove, or a row that fails by design / cannot fail when run as the doc
and its preamble prescribe; **SHOULD-FIX** = a row exists but misses a lesson, reads the wrong source, or leaves a
failure mode unasserted; **NOTE** = wording, hygiene, or a cheap strengthening.

---

## 0. Round-1 testability items — resolution check

Every round-1 R2 BLOCKING (#7, #8, #14, #24, #31, #35, #36, #41, #42, #45, #50, #51) and SHOULD-FIX (#1–#5, #9, #10, #15,
#19–#21, #25–#28, #37–#39, #43, #44, #46, #47, #52–#55; #32 was rejected) was checked against today's rows.

| Round-1 item (triage id) | Status in the docs today |
|---|---|
| #1–#5 (T11-1…T11-5) | Resolved (11 E15, E16, Seeding, Dump route, `[quick] motion`). |
| #7 (T12-1) | Resolved in E1(b) / E2 / E3 — but E1(a) is now broken by the lead's provisioning Decision → **#5**. |
| #8 (T12-2) | **Partly unresolved**: E11 / E12 exist; E13 is still conditional on Q7–Q9, which are now answered, and contradicts Q8 → **#6**. |
| #9 (T12-4) | Resolved (`wizard_why` table, E2 / E3). |
| #10, #51 (T12-5, C-4) | **Regressed**: the E14 template and every instance (15 E28, 16 E26, 17 E25, 18 E15, 19 E16) fail by design under the 2026-09-23 lead Decision that `provision.sh` finishes the wizard → **#5**. |
| #14, #15 (T13-1, T13-2) | Resolved (E7 re-cut, `acrylic_expect.py`) — the satellite sub-row is geometrically impossible → **#10**. |
| #19–#21 (T14-1…T14-3) | Resolved. |
| #24–#28 (T15-2, T15-3, T15-7…T15-9) | Resolved in rows (E26, E14, E12, E6 / E7); E26's negatives fail without ring slicing → **#52**. |
| #31 (T16-1) | Resolved (E22–E24). |
| #35 (T17-1) | Resolved in shape (E20–E22) but E20 asserts the wrong key mechanism and routes the real TMDB key into a fixture log → **#26**. |
| #36–#39 (T17-2, T17-3, T17-6…T17-8) | Resolved (E6, E6b, E7 modes, E9, E3, E5, P9–P12). |
| #41–#44 (T18-1…T18-3, T18-6) | Resolved (E4b, E13, E14, E15, E2). |
| #45 (T19-1) | Resolved in rows (E9 five sub-rows) — the battery-saver sub-row cannot pass on the AC-powered AVD → **#53**. |
| #46, #47 (T19-2, T19-3) | Resolved (E1 / E8 Apps rows; E2 label assertions). |
| #50 (C-3) | Resolved for 11, 15, 16, 17; **not** for 18 (baseline spec omits `slots`), 19 and 20 (no baseline named) → **#57**. |
| #52 (C-8) | Resolved (15 E27, 16 E25, 17 E23, 18 E16 / E2, 19 E17). |
| #53 (C-5) | Resolved (all preambles); the logged clock self-reports → **#61** (NOTE). |
| #54 (C-6) | Resolved in 11, 14–19; missing in the new phase 20 → **#46**. |
| #55 (C-10) | Resolved (11, 15, 16, 17). |

---

## Phase 11 — tile quick actions

**1. NOTE — Decisions "Selection rule" (l.105-110) and E15 (l.353-356): stale text beside the per-activity ruling; the
`[quick] shortcuts` line cannot tell two tiles of one package apart.** The lead's 2026-09-23 line (l.189-195) moves the
query to `setActivity(the tile's component)`, but the Selection-rule line still reads
"query `ShortcutQuery().setPackage(pkg) .setQueryFlags(FLAG_MATCH_MANIFEST or FLAG_MATCH_DYNAMIC)`" and E15 still carries
"(OPEN (resolved 2026-09-23 by the lead: per-activity query, see Decisions), found 2026-09-23 while applying T11-1 …)".
The line format `[quick] shortcuts for <pkg>/<userId>` is identical for the Music and Start-settings tiles, so E15's ring
assertion is satisfied by either tile's query. *Fix:* mark l.105 "SUPERSEDED 2026-09-23 by the per-activity line"; strike
E15's OPEN parenthetical; change the line to `[quick] shortcuts for <pkg>/<activity>/<userId>: n (k shown: ids)` and make
E15 assert `app.tileshell/.music.MusicActivity/0: 4 (4 shown: songs,albums,artists,playlists)` and
`app.tileshell/.settings.SettingsActivity/0: 4 (4 shown: start_theme,tile_apps,checklist,diagnostics)` (ids as build
task 5 names them); update E1 / E3 / E9's expected lines to the same form.

**2. NOTE — Decisions "QA fixtures" (l.158-164): the fixture does not say where its shortcuts hang.** Under the
per-activity query, a static `shortcuts.xml` found by `setActivity(<pinned component>)` must be declared in the
`<meta-data android.app.shortcuts>` of the launcher activity the tile pins; declared on `ShortcutActivity` it yields 0 and
E1 fails on a correct shell. *Fix:* "tileclient-a declares `res/xml/shortcuts.xml` on its LAUNCHER activity (the component
`baseline_layout.json` pins); `ShortcutActivity` is only the shortcuts' target."

**3. NOTE — Edge cases (l.398-399): "an icon that fails to load (… still runs; logged)" names no line.** *Fix:* add
`[quick] satellite <i> icon failed <pkg>/<id>: <why>` to Harness contracts; E14's coverage then includes it (a tileclient-b
dynamic shortcut built with an `Icon.createWithContentUri` to a missing URI produces it).

## Phase 12 — setup wizard

**4. SHOULD-FIX — E3 (l.490-491), E11 (l.538-541), E13 (l.557-558): the pixel rule samples the picture at two different
pixels.** "`e13_pixels.py` … reads the `slot:PEOPLE` tile band T and the gutter G beside it, and T = 0.72 · accent + 0.28 ·
G ± 4". The formula holds only if the picture under T equals the picture under G. Jeremy's AI pictures (a "calm middle",
not a flat one) and the stock img0 are not flat, and Start's 1.3× parallax moves the picture under both, so a correct build
can miss ± 4. *Fix (all three rows):* "G = the mean of the two gutter pixels at the same y immediately left and right of
the band (≤ 12 px from T's column); the sample row is chosen where those two gutters differ by ≤ 3 levels per channel (the
picture is locally flat), searching down the band in 8-px steps; the chosen y and both gutter values are written to the
log." Keep "fails if the picture is missing (then α = 1)" as the second assertion.

**5. BLOCKING — the 2026-09-23 lead Decision on provisioning (l.326-331) breaks E1(a), the E14 template and its five
instances; the two "not shown" lines have no precedence.** The Decision: "QA provisioning FINISHES the wizard once … (provision.sh
taps through, or writes the same done marker the finish writes) … so am force-stop app.tileshell, which makes Android
deselect the keyboard …, sends 'Keyboard selected' to the checklist instead of summoning the wizard … Rows that test the
wizard itself clear the marker first and restore it after (RV12); E1(a) runs on a fresh pm clear." Consequences as written:
(a) E1(a) (l.451-453): "provisioned AVD, `adb shell am force-stop app.tileshell`, … diagnostics carry `[wizard] not shown:
core held`" — after the force-stop the keyboard is deselected, so a core row is MISSING and the only true line is
`not shown: finished`; the row fails on a correct build. (b) E14 (l.563-567): "`pm clear` → `provision.sh` → revoke <grant>
(its adb form) → Home: the dump has `wizard_step:<ns>:<id>`" — `provision.sh` now writes the marker, so the revoked grant
goes to the checklist and no wizard appears; the template fails by design, and so do 15 E28, 16 E26, 17 E25, 18 E15,
19 E16, which copy it. Clearing the marker "first" needs the process stopped (SharedPreferences is cached in-process),
which deselects the keyboard and changes E14's "Step 1 of 2" to "Step 1 of 3". (c) Decisions "Diagnostics" (l.181-183)
lists `[wizard] not shown: core held` and `[wizard] not shown: finished` with no rule for a device that is both (every
provisioned AVD), yet 15 E28 / 16 E26 assert `core held` after `pm clear → provision.sh → Home`. (d) The Goal (l.18-20)
still says "no existing QA driver needs a bypass: the harness provisions the grants, and the wizard has nothing to do".
*Fix:* (1) Decisions: "`provision.sh` writes the marker with the shell stopped, `layout_restore`'s push-then-cat form —
a host file `qa/phase-12/fixtures/setup_wizard.xml` (`<map><boolean name="finished" value="true" /></map>`), `adb shell am
force-stop app.tileshell`, `adb push` it to `/data/local/tmp/`, `adb shell 'run-as app.tileshell sh -c "mkdir -p
shared_prefs && cat /data/local/tmp/setup_wizard.xml > shared_prefs/setup_wizard.xml"'` — then `ime set
app.tileshell/.ime.KeyboardService` (the stop deselected it), then Home; `PROVISION_FINISH_WIZARD=0` skips the marker." One
mechanism, not "taps through, or writes". (2) Precedence: "`not shown: core held` when every core row is held
(marker or not); else `not shown: finished` when the marker is set; else the run shows." (3) E1(a): "provisioned AVD (marker
written by provision.sh): Home → `start_page`, no `wizard_page`, `[wizard] not shown: core held`; then `am force-stop` +
Home → still no `wizard_page`, `[wizard] not shown: finished`, `settings get secure default_input_method` ≠ the shell's and
`checklist:keyboard_selected:missing` on the Setup page (the case the marker exists for)". E1(b) stays the no-marker route
(`core held`, no `setup_wizard.xml`). (4) E14: "`pm clear` → `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh`
→ revoke <grant> → Home → …; end with `pm clear` → `provision.sh` → Home." The same edit in 15 E28, 16 E26, 17 E25,
18 E15, 19 E16. (5) Goal: "the harness provisions the grants and finishes the wizard as a user would (Decisions)".

**6. BLOCKING — Q7–Q9 are answered (l.49-57) but the preset table, the T12-9 line and E13 are still conditional, and Q8's
ruling has no row.** T12-9 (l.253-257): "The three choices R12 raised are this doc's queue Q7–Q9, UNANSWERED at this
writing, so the preset table and E13 are written conditionally". The table's original-preset row still reads "Cobalt
#3E65FF …, by Q7: A (lean) … B … C", "img0 by Q8: A (lean) the Hero …; B …; C both, as two variants", "by Q9: A (lean) …".
E13 (l.559-562): "Q8 → `background` names the chosen img0's asset (C: two presets, one per picture, each asserted) … Until
Q7–Q9 are answered this row is not runnable in full". Q8 was ruled "(c)" with "The preset list shows one entry with the two
pictures as its variants (agent: not two separate presets, so the list stays six)" — E13 asserts the opposite, and no tag,
key, diagnostics line or H row exists for choosing a variant. E3 (l.489) still hedges HAL's accent "(… if Q7 is answered
C)". T12-7 (l.319-324) says the stock pictures may be absent from a build ("no picture — still a preset … logs `[theme]
preset Windows 10 Mobile (original) applied: no picture`"), and E13's α-0.40 pixel assertion fails on exactly that build.
*Fix:* (1) Table row: accent `Cobalt` #3E65FF as the 49th swatch; picture = variant `hero` (`img0_w10m_1607-1709.jpg`,
default) or `streaks` (`img0_w10m_1507-1511.jpg`); lens tinted on Cobalt's hue line / ring Cobalt. HAL's row: say whether
#D81810 is a 50th swatch or a preset-only value (Q7 ruled Cobalt only). (2) T12-9: "Q7–Q9 answered 2026-09-23 (A, C, A)".
(3) Decisions + tags: `theme_preset_variant` key (`hero` | `streaks`), tags `preset_variant:hero` / `preset_variant:streaks`
under the original preset's entry on both surfaces, line `[theme] preset w10m variant <v> applied`. (4) E13 re-cut: "tap
`preset:Windows 10 Mobile (original)` → `theme_preset` = `w10m`, `theme_preset_variant` = `hero`, `accent` = 4282279423,
`accent:Cobalt` present, `selected="true"`, and the accent grid holds 49 swatches on both the wizard page and Start + theme
(count of `accent:*` nodes); `persona.py` finds the lens on Cobalt's hue; `wizard_presets` still holds exactly six
`preset:*` entries; tap `preset_variant:streaks` → `background` names the streaks asset, `[theme] preset w10m variant streaks
applied`, `theme_preset` still `w10m`. **Picture branch, chosen from the APK, not skipped:** if `unzip -l <apk>` lists the
img0 asset → T = 0.40 · accent + 0.60 · G ± 4 (#4's sampling); if not → `[theme] preset Windows 10 Mobile (original)
applied: no picture` and T = accent ± 2." Strike "not runnable in full". (5) H9 gains "the two picture variants and how
the variant is chosen".

**7. SHOULD-FIX — Diagnostics (l.181-187): a step whose grant action cannot start is silent.** Every step fires an
`action` lambda that starts a Settings / permission-controller intent; `ACTION_VOICE_INPUT_SETTINGS`,
`MANAGE_APP_USE_FULL_SCREEN_INTENT` and the later phases' pages are exactly the kind One UI resolves differently (phase 19's
table: three actions resolve to NONE even on AOSP). An `ActivityNotFoundException` leaves the page unchanged with no line.
*Fix:* add `[wizard] step <ns>:<id>: action failed <intent action>: <exception>`, the step's button then relabels to "Open
Android settings" (Android's Settings home, phase 19's fallback form); a JVM test drives the step runner with an
unresolvable intent; P1 records any One UI step that hits it.

**8. NOTE — E2 (l.461-470): the hard-coded 17-id list changes with every later phase.** Phases 15, 17, 18 and 19 add
`setup:` steps (full_screen_alarms; camera, videos; files; write_settings, dnd_access) and 16 widens `tess:contacts`; at
the end-of-build pass E2's "Step 1 of 18" and its ORDER fail. *Fix:* "E2's id list is extended by each later phase's C-4
line (the id at its `Checklist.kt` position) and E2 is re-run with the full list at the end-of-build pass."

**9. NOTE — E11 (l.543-544): "DARK: R6's keys, luminance ≈ 48" is not an expected value; `tess_ring` is never read.**
*Fix:* write the RGB (phase 05 E3's key fill value) ± 4 for DARK and #E6E6E6 ± 4 for LIGHT, and add a ring sample
(`persona.py` ring colour = the preset's `tess_ring` ± 8).

## Phase 13 — Fluent materials

**10. SHOULD-FIX — E7 satellite sub-row (l.309-313) and phase 11 E5 / E7 sub-rows: impossible under the sample rule.**
The rule (l.177-179): "every measured pressed-fill sample, and every ring sample, is taken ≥ r + 2 epx from the touch
point" with r = 40 epx = 120 px, so ≥ 126 px. A satellite is 164 px square (phase 11 l.74-75); pressed at its centre, no
pixel of it lies more than 82·√2 ≈ 116 px away, so neither the ring sample nor the rest-fill sample exists and the light
covers the whole satellite. *Fix (13 E7 and 11 E5 / E7):* "press the satellite at (left + 10, top + 10); sample the ring
on its right and bottom edges ≥ 126 px from that point, the touch-point pixel for F + 0.10·(255 − F), the pixel 60 px
along the diagonal for F + 0.05·(255 − F), and the interior 12 px inside the bottom-right corner (≈ 200 px away) for the
rest fill ± 2."

**11. SHOULD-FIX — E1 (l.266) "within 1 s" and Edge cases (l.363-364) "screencap ≤ 100 ms later": host-clock timing.** A
host `adb screencap` cannot be placed within 100 ms, and "within 1 s" measured by host polling mixes clocks (the P02 /
J-series lesson). *Fix:* "`MARK=$(adb shell date +%s%3N)` immediately before `cmd power set-mode 1` / the toggle tap; the
`[fluent] acrylic=off …` line's `wall=` − MARK ≤ 1000"; the edge case asserts the `[fluent] reminder_menu …` redraw line's
`wall=` − the `acrylic=off` line's `wall=` ≤ 2 frames (34 ms), the screencap only corroborating.

**12. SHOULD-FIX — Diagnostics (l.152-154): a failed static-backdrop build is silent.** Only success logs (`[fluent] static
backdrop rebuilt for <uri> in <ms> ms`). A background URI whose file was deleted, a decode OOM or a revoked persisted grant
leaves the app list on its fallback with no reason. *Fix:* add `[fluent] static backdrop failed for <uri>: <why> (fallback)`;
edge-case row: set the checkerboard, `adb shell rm` it, open the app list → the fallback form (E2's "acrylic off" pixels)
and the line.

## Phase 14 — pod bay

**13. SHOULD-FIX — Decisions "Route to Start" T14-7 (l.119-122) is proven only by an edge-case sentence (l.328-329).**
"assert no `[podbay] opened` until `[wizard] finished` or `[wizard] skip`, then the line" sits in Edge cases, which no row
runs. Phase 12 builds before 14 (order 11 → 13 → 12 → 14), so it is runnable. *Fix:* add **E17 Pod-bay request under the
wizard:** "`pm clear` → `PROVISION_FINISH_WIZARD=0 provision.sh` → `cmd notification disallow_listener …` → Home
(`wizard_page`); `nav_search` opens Tess (phase 12 E8), `type_request "open the pod bay"` → reply 'Opening the pod bay.',
session closed; from a MARK, the ring holds no `[podbay] opened` and no `[start] page=POD_BAY` and the dump still shows
`wizard_page`; tap `wizard_skip` → `[wizard] skip` then `[podbay] opened by voice` with `wall=` after it and `pod_bay` in
the dump. Restore `pm clear` → `provision.sh` → Home."

**14. SHOULD-FIX — E14 (l.281-282): no expected values, no tag.** Phase 13's surface table (l.121) gives the pod bay "its
own P4 fill | wallpaper | its fill | its fill", phase 14 (l.65-67) says "the same material — phase 13's app-list backdrop,
same engine, same parameters", and neither doc gives the pod bay an `acrylic:` tag or a `[fluent] pod_bay …` line. E14
"cited by that row's number when phase 13 is FINAL" cannot state a pass. *Fix:* settle the fill in one place (the
app-list's T = theme background (0,0,0), per phase 14's Decision, and phase 13's table row edited to match); tag
`acrylic:pod_bay`; E14: "phase 13 E2's method on the pod bay: in a strip between two pods, edge spread = the blurred width
± 20 % and pixels = 0.2 × blurred checker ± 3; pod text edges sharp; acrylic off → 0.2 × checker ± 3 unblurred;
`[fluent] pod_bay source=static tint=(0,0,0) alpha=0.8 blur=30epx`."

**15. SHOULD-FIX — Diagnostics (l.157-158): a pod launch that fails is silent.** `[podbay] launch <id> -> <component>` logs
only success. The Now-playing title tap with the MUSIC slot unassigned, and the Reminders tap when `CortanaService.open`
cannot show a session (the ASSISTANT role removed — `showSession` returns without a window), do nothing visible and log
nothing. *Fix:* `[podbay] launch <id> failed: <why>` (`slot unassigned` | `no session: not the assistant` |
`ActivityNotFoundException`); E4 gains two sub-rows: a baseline variant with `slots.MUSIC` removed → title tap → the slot
picker or the line, never nothing; `cmd role remove-role-holder android.app.role.ASSISTANT app.tileshell` → Reminders tap →
the line; restore the role.

**16. NOTE — E6 (l.247-249) names "the `SpeakingDone` line"; the launcher ring's literal is different.** The line the
launcher ring holds is `[speech] speaking done <utteranceId> cancelled=false` (`cortana/speech/SpeechClient.kt:107`);
`SpeakingDone` is a Kotlin event name. *Fix:* write the literal and "both lines read from the launcher ring (`diag`), sliced
from the MARK taken before `say`".

## Phase 15 — Alarms & Clock, Calculator, Voice Recorder

**17. BLOCKING — the wrong ring: E18's `[speech]` lines live in `:speech`'s ring, and no dump route exists for
`:recorder`'s.** The preamble (l.433) says "'diagnostics' is read with phase 01's command" (the launcher ring,
`lib.sh` `diag`, TileNotificationListener's dump). E18 (l.580-581): "within 2 s of the kill the ring holds `[speech] a client
died` then `[speech] microphone released: owner died` (`cortana/speech/SpeechService.kt:101,326`)" — those two
`Diagnostics.add` calls run inside `SpeechService`, i.e. in the `:speech` process, whose ring is read with `speech_dump`
(`lib.sh:147-149`); `Diagnostics` is a per-process `object` (`diag/Diagnostics.kt:14-26`). Read as prescribed, E18's
microphone-release assertion can never pass. Likewise every `[recorder] start / stop / recovered / paused / storage floor`
line is written by the recorder service in `:recorder` (Decisions "Voice Recorder mechanics", l.191-196), so E14, E18,
E19 and E22 grep a ring that never holds them — the J7 run-4 fault ("the speech process's 'asr: final' line lives in its
own ring, which run 4 wrongly grepped here", `j7.sh:30-32`). *Fix:* preamble: "Three rings: the launcher's (`diag`), the
`:speech` process's (`speech_dump`), and the `:recorder` process's — `adb shell dumpsys activity service
app.tileshell/.recorder.RecorderService` (the service's `dump()` prints its process's `Diagnostics`, as
`SpeechService.kt:219` does; build task 7). Each `[recorder]` line is listed with its ring; a line needed after the
recorder process died (`recovered`) is written by the next recorder start and read from that ring." E18: "the `:speech` ring
(`speech_dump`, sliced from a MARK taken just before the `kill -9`) holds `a client died` then `microphone released: owner
died`, both `wall=` − MARK ≤ 2000". E22 greps all three rings' saved slices (#52).

**18. SHOULD-FIX — E9 (l.509-511): the DeskClock negative cannot fail.** "the DeskClock fixture's own list is unchanged
(`content query --uri content://com.android.deskclock/alarms` count before = after)". DeskClock's `ClockProvider` is not
exported on AOSP (`android:exported="false"`; one query at build start confirms it), so the shell uid gets a permission
error both times and "0 = 0" passes whatever happened. *Fix:* "`adb
shell dumpsys alarm | grep -c 'com.android.deskclock'` before = after, AND the query form is dropped; plus 'no
`com.android.deskclock` activity resumed at any point' (`dumpsys activity activities`, already in the row)." If the build
keeps a provider read, assert first that the query printed rows (`grep -c '^Row:'` ≥ 1 after creating one DeskClock alarm
through its own UI).

**19. SHOULD-FIX — Diagnostics (l.256): `[recorder] list: n recordings (m by other apps)` hides a silent empty.** Listing
another app's `IS_RECORDING` files needs READ_MEDIA_AUDIO (the Setup `music` row); with it revoked, the shell still sees its
own files (owner access) and m reads 0 with no reason — indistinguishable from "no other recordings". *Fix:* the line gains
`(other apps: hidden, READ_MEDIA_AUDIO denied)` in that state and the list shows one line naming the Music grant; E21
sub-row: with E14's other.m4a present, `pm revoke app.tileshell android.permission.READ_MEDIA_AUDIO` → other.m4a absent,
the take present, the line and the notice; `pm grant` restores it.

**20. NOTE — E8 (l.500-501): the netstats check runs under airplane mode, so it cannot fail.** *Fix:* keep the offline pass
(proves the feature works offline) and add a pass with the network ON asserting the shell uid's byte counters in `dumpsys
netstats --uid` unchanged across the search and add (proves the world clock makes no request).

## Phase 16 — Calendar and People

**21. SHOULD-FIX — E18 (l.576-579): the asserted denied-read line is not defined.** "diagnostics `calendars: none` is NOT
logged — a denied read logs `read=false`". `read=<bool>` exists only in `[people] list: n contacts read=<bool>
write=<bool>` (l.245); the `[calendar] calendars:` line (l.243) has no denied form, so the READ_CALENDAR-revoked state is a
silent empty in the ring. *Fix:* add `[calendar] calendars: denied (READ_CALENDAR)` to Harness contracts and E18 asserts it
(and its absence after `pm grant`).

**22. SHOULD-FIX — E7 (l.494-495) times with a screenrecord; E4 (l.472-474) with host polling.** "renders … within 3 s of
the page change (screenrecord frame count)" — the variable-rate screenrecord is not a clock (P02, C-5); "within 2 s with no
restart" by repeated dumps is host time. *Fix:* the existing `[calendar] view <name> <from>..<to>: n instances` line gains
`in <ms> ms` (query + first composed frame, from `withFrameNanos`); E7 asserts that ≤ 3000 and the line's `wall=` − the
swipe's device MARK ≤ 3000; E4 asserts the `view …` line that lists "Dentist" has `wall=` − the insert's MARK ≤ 2000.

**23. NOTE — E16 (l.564-566) → P6 (l.661): the policy negative is proven nowhere.** "if none can be installed on this AVD,
the row records it and the negative moves to P6", and P6 is "recorded as not testable on the phone". *Fix:* name the
fixture: TestDPC (googlesamples/android-testdpc, Apache-2.0, no Play services) in `~/android-fixtures/`, provisioned as
profile owner with `dpm set-profile-owner --user <id> com.afwsamples.testdpc/.DeviceAdminReceiver`; the negative stays on
the AVD.

**24. NOTE — Edge cases (l.707-708, 739-741): two failure states log nothing.** The Birthdays calendar that cannot be
created (provider refuses) and "a photo that fails to decode (skipped, logged)" have no defined lines. *Fix:* `[calendar]
birthdays calendar could not be created: <err>` (LocalCalendar's form) and `[people] tile: photo <lookup> skipped: <why>`.

## Phase 17 — Photos, Camera, Movies & TV

**25. SHOULD-FIX — stale conditionals across the doc (Q-A = A and Q-B = A are ruled, l.73-86).** Header (l.4): "two
questions to Jeremy still open: Q-A, Q-B"; Decisions l.249-253 "written conditionally below, marked 'pending Q-A' / 'pending
Q-B'"; l.292-308 "**Pending Q-A — the catalogue source.** Written for all three forms … **B (keyless)** … **C (TMDB, key
pasted into Settings)**" and "**Pending Q-B** … **B (Plex)** … **C (both)**"; BS-6; the Fixtures' Plex container
(l.570-571); E20 "under Q-A A / C … under Q-A B … under Q-A C" (l.732-739); E22 "NOT APPLICABLE … Under Q-B B / C" (l.764-766);
H10 "under Q-A A / C"; P14 "under Q-B B / C". A row with dead branches invites an agent to build or run the wrong one.
*Fix:* collapse every block to the ruled form (TMDB with the personal read token; Jellyfin only), delete BS-6, the Plex
fixture and every B / C branch, and fix the header.

**26. BLOCKING — Decision Q-A (l.78-86) vs E20 (l.733-736): the row asserts the wrong mechanism and routes Jeremy's real key
into a fixture log.** The Decision: the key "lives in the gitignored local.properties (tmdb.readToken, tmdb.apiKey) and
reaches the app as a BuildConfig field … Calls use the v3 API with the read token as a bearer header." E20: "**under Q-A A**
the fixture's request log (`catalogue_server.py` prints each request line) shows a non-empty `api_key` parameter on the
search (its value redacted in the evidence — it is Jeremy's key)". (a) With a bearer header there is no `api_key` query
parameter, so the row fails on a build that follows the Decision (and the "pending Q-A" block, l.293, still says
`tmdb.apiKey` → `BuildConfig.TMDB_KEY`). (b) E20 runs on the debug build, which on Jeremy's machine carries the real key
from local.properties; `python3 -m http.server`-class servers print the request line (and a header check would print the
header), so the real key lands in the fixture's log, and "redacted in the evidence" is a manual step after the fact —
against "the key never appears in any fixture, log or evidence file". (c) No row proves the key never reaches the
diagnostics ring or logcat. *Fix:* E20's key half: "the QA APK is assembled with a dummy token, `./gradlew assembleDebug
-Ptmdb.readToken=qa-dummy-token -Ptmdb.apiKey=qa-dummy-key` (the Gradle property overrides local.properties; build task 12
reads the property first); `catalogue_server.py` asserts every request carries `Authorization: Bearer qa-dummy-token` and no
`api_key` query parameter, and logs only 'bearer ok' / 'bearer missing'; an APK assembled with `-Ptmdb.readToken=` (empty)
shows 'No catalogue key in this build', `[video] catalogue: no key` and makes NO request. Leak scan, gated: a script that
reads the real values from local.properties without echoing them (`grep -Fqf <(sed -n 's/^tmdb\.[a-zA-Z]*=//p'
local.properties) …`) finds zero matches in `qa/phase-17/**`, in the saved `:video` and launcher ring slices, and in `adb
logcat -d`; the same scan runs over P15's evidence." Decisions "Q-A": one property name set (`tmdb.readToken` for the bearer;
state whether `tmdb.apiKey` is used at all), and "never logged: the catalogue lines carry the query and status only".

**27. SHOULD-FIX — E20 (l.729-731): artwork has no fixture route, and nothing proves the AVD stayed off the internet.**
"with artwork loaded (each row's image node has non-zero bounds and the pulled fixture PNG's colour at its centre ± 4)".
TMDB image URLs are built from `/3/configuration`'s `images.secure_base_url` (image.tmdb.org), which `qa_catalogue_base`
does not redirect; the AVD would fetch live artwork (or fail) while the row claims "never the live network". *Fix:*
`catalogue_server.py` serves `/3/configuration` with `images.secure_base_url = "http://10.0.2.2:8090/img/"` and the poster
PNGs under `/img/`; the row asserts the fixture logged `/img/…` requests; and the egress guard of #58 runs around E13,
E20–E22 (zero packets from the app uid to any address but 10.0.2.2).

**28. SHOULD-FIX — E22 (l.758-761): the plaintext check can fail on a correct build; the server is unpinned.** "`adb shell
grep -l -E '[0-9a-f]{32}' /data/data/app.tileshell/shared_prefs/* <the BS-3 store file>` … list no file" matches any 32-hex
value any feature stores (hashes, ids), and "Jellyfin `jellyfin/jellyfin`" floats to `latest`, whose auth header form can
change between releases. *Fix:* the driver reads the app's issued token from the server (`GET /Sessions` with the fixture
admin's token, the session whose `Client` is the shell) and greps for that exact string (`grep -rlF "$TOKEN"
/data/data/app.tileshell/`) → no file; the fixture is `jellyfin/jellyfin:<version>@sha256:<digest>` recorded at BS-5, the
same digest in every run's log.

**29. SHOULD-FIX — NEEDS-HUMAN: the Jellyfin library view and two R11 findings have no row.** H12 is "the media-server
sign-in page (Y10)" only; the Media server pivot's library (folders / titles "in the hub's idiom", build task 13) is a P4
design with no H row. r11/movies-tv.md adds "**G1 — version choice (new NEEDS-HUMAN, accept)**" (10586's Films / TV /
Videos app vs later builds) and "the phone … used the **≡ pane** … Either keep the pivots as a stated P4 departure, or use
the pane". *Fix:* H12 → "the sign-in page AND the Media server pivot's library and title rows (Y10, P4)"; add H17 *accept*
the version choice (G1) and H18 *accept* pivots instead of W10M's ≡ pane (or re-cut to the pane).

**30. SHOULD-FIX — E7 "Timer" (l.639-640): mixed clocks at 1-s resolution.** "the new row's `date_added` ≥ the tap's uptime +
3 s, both read from the shell's ring (`wall=`)" — `date_added` is MediaStore's wall-clock whole seconds, "the tap's uptime"
is neither in the ring nor on the same clock, and a 1-s floor can pass a 2.1-s shutter or fail a 3.0-s one. *Fix:* "MARK =
device ms before the `input tap`; the `:camera` ring's `[camera] timer 3s -> shutter` and `[camera] saved <uri> …` lines
satisfy `saved.wall − MARK` ≥ 3000 and ≤ 3000 + 1500; `date_added` only corroborates (≥ MARK/1000 + 2)".

**31. SHOULD-FIX — E18 (l.714-723): failure states with no line.** The MediaProvider delete consent refused (the user taps
Deny), an edit / trim whose `createWriteRequest` is refused or whose write fails, and "`[video] server token cleared`" (edge
case l.897, not in E18's list) all end silently in the ring. *Fix:* add `[photosapp] delete <id>: refused by user`,
`[photosapp] edit <tool> failed: <why>` / `trim … failed: <why>`, and list `[video] server token cleared`; E5 gains the Deny
branch (dialog Deny by bounds → count unchanged, the line), E22's "remove the server" step asserts the token line and #28's
grep for the old token → no file.

## Phase 18 — Files

**32. SHOULD-FIX — NEEDS-HUMAN: the zip design, "sort by type" and Recent's form are P4 with no accept row.**
r11/files.md: "**Zip (Q2 C) and the Recycle Bin (Q3 C) are P4 in full** (1.12.7, 1.12.8): no W10M screen to measure";
"**Sort by type (E3) is an addition.** W10M sorted by Name, Size and Date only (1.4.5)"; Recent was "recently accessed or
downloaded files", not a MediaStore modified-date list capped at 100; W10M had "no free-space line". H2 covers the bin only.
*Fix:* add H7 *accept* the zip experience (a zip opened as a folder, extract destination naming, the "password-protected" /
"can't be opened" / "storage removed" wording, create naming); H8 *accept* sort by type (P4 addition) — or drop "type" from
E3; H9 *accept* Recent as "recently changed, newest first, 100 max" and the root page's free-space line (P4).

**33. NOTE — E12 (l.375-383) / E13 (l.388-389): a corrupt zip has no line.** "`qa-corrupt.zip` → 'This zip can't be opened'
(dump text), no crash" — the ring stays silent. *Fix:* `[files] zip open <path>: failed <why>`; E13 asserts it.

## Phase 19 — Settings front

**34. SHOULD-FIX — R11 settings-front landed with 60-fps HIGH motion and geometry that contradicts E1.**
r11/settings-front.md §6 (60-fps, S1 + S2): "6.2 Page exit | **cut** … HIGH"; "6.3 Page entrance | the new page slides up and
fades in … 90 % by ≈ 200 ms, settled by ≈ 250–300 ms … HIGH"; its gaps: "Y4 (page transition) is now measured (6.2–6.3)";
"E1 values to update: box height 32.7 epx (R3 C1's 31 was 720p), text x 56–57, category page rows 48 epx at label x 44.5."
E1 (l.349-351) asserts "31 ± 2 epx tall …; text at x = 55 epx" — a build that follows R11 fails E1 (32.7 is inside ± 2, x
56.5 is not at "55"), and Y4 / H5 / E15 still say "UNMEASURED … written once that section lands". Note for the lead: this is
the one inbox surface where R11 DID find a 60-fps source — the brief's "no inbox-app motion is measured" does not hold for
phase 19. *Fix:* E1 takes R11's values with R11's tolerances (box 32.7, text x 56–57, category rows 48 epx at x 44.5, and
§2.5's focused-box rest state per the [accept] row R11 asks for); Y1 / Y4 closed; E15 written now: "`[motion] settings_page
t0=… first_offset_epx=<n> settle=<ms>` — first frame offset 9.8 ± 1.2 epx, 90 % of travel by 200 ± 17 ms, settle 250–300
ms (+ one frame); the old page gone in one frame (no exit motion)"; H1 [fidelity] covers it; H5 narrows to toggle / combo
motion (U2, U3).

**35. NOTE — reads with a revoked grant are silent.** Data usage (`NetworkStatsManager`) and per-app storage
(`StorageStatsManager`) both need usage access; revoked, the pages show zeros. *Fix:* `[settingsfront] read <data_usage |
storage>: denied (usage access)` and the page's line naming the checklist row; E-row sub-step with `appops set
app.tileshell GET_USAGE_STATS ignore`.

## Phase 20 — Music streaming and Radio (new, unreviewed in round 1)

**36. SHOULD-FIX — the doc is still written for an unasked interview.** Header (l.4): "interview pending"; Decisions "(only
if Q2 rules internet radio)", "(only if Q1 rules a catalogue)", "(if Q3 keeps Radio inside Music)", "Q4 decides whether a
Wi-Fi-only setting exists"; build tasks "*(Q3)*: A — … B — … C —" (l.287-291) and task 9 "*(Q4 B)*" (Q4 ruled A: no
setting); E1's "*(Q3 B)*" and "*(Q3 C)*" branches (l.334-338); E12's "*(Q4 B)*" / "*(Q4 C)*" (l.386-388); H3 "under Q4 B",
H9 "under Q3 B"; E18 "(only with Q3 B)". *Fix:* header "interview DONE 2026-09-23 (Q1 C, Q2 A, Q3 A, Q4 A, Q5 A +
Pandora)"; strike task 6 B / C, task 9, E1's B / C, E12's B / C, the H3 / H9 clauses; E18's exported list is "unchanged".

**37. SHOULD-FIX — Decisions l.105-112 and build task 7 are stale since J5; E16 must cite J5's row.** "So 'play <song>'
against the shell's own player resolves nothing today" / "`MusicService`'s callback gains `onSetMediaItems` resolving
`RequestMetadata.searchQuery`". J5 (qa/JEREMY-QA.md, "fixed", commit 373106a) added `MusicSearch` and `onSetMediaItems`
(`music/MusicService.kt:151-187`) with "I couldn't find X in your music" on a miss. E16's "'play <MUSIC6 fixture title>' →
that local track plays (the closed gap)" is now a regression, not new behaviour. *Fix:* the Decision reads "closed by J5
(2026-09-23); this phase EXTENDS `MusicSearch` with stations (favourites, then the cached directory by name / genre) ahead of
the library"; task 7 accordingly; E16 cites "qa/phase-03/scripts/j5.sh re-run on this build, 8/8" and adds the station miss:
"'play zzqx radio' → no station, the J5 miss reply naming it, `[music] search: \"zzqx radio\" -> none`, session unchanged".

**38. BLOCKING — Decision "Q5 follow-up — Pandora" (l.42-45) has no row, and "any other installed music app gets a plain
open hand-off" (l.44-45, 50-52) has none either.** The Decision: "Pandora gets its in-app search deep link (… if Pandora
exposes no search link, 'Listen on Pandora' opens Pandora and the diagnostics say the search could not be passed) and a phone
row on the S25 Ultra." The only phone row is P1: "Real services installed (Spotify, YouTube Music, whatever the S25U has)" —
no Pandora, no search-form assertion, and the "could not be passed" line is not in E19's list. E13's stub "declares each
service's deep-link form" but no row covers an installed music app with NO recorded form (the "plain open" rule). *Fix:*
**E13b** "a second stub package (`app.tileshell.testclient.pandorastub`) declaring the Pandora search form recorded at build
start (task 1) → 'Listen on Pandora' reaches it with the title and artist in the URI (`TileShellQa` log), `[music] handoff:
pandora \"<title>\" -> <uri>`; a third stub that is only `CATEGORY_APP_MUSIC` with no recorded form → 'Listen on <stub>'
opens its launch activity, `[music] handoff: <app> \"<title>\" -> open (no search link)`." **P9** "Pandora on the S25U:
'Listen on Pandora' → `com.pandora.android` resumed on its search results for the title (screencap), or — if task 1
recorded no search link — Pandora opened and `[music] handoff: pandora: search not passed`; gated on the recorded form,
RECORDED for the landing." E19 gains both lines. Tess half: "listen to qa artist on <plain stub>" → the plain open.

**39. SHOULD-FIX — E16 (l.404) contradicts the Q5 ruling on "play radio".** Ruling (l.47-48, and Q5 A l.255-256): "'play
radio' (the last favourite)". E16: "'play radio' → the first favourite". *Fix:* define it in Decisions ("the favourite
played most recently; with none played yet, the first favourite") and E16: "two favourites, play the second, stop; 'play
radio' → the second (`[music] search: \"radio\" -> station <id>`); after `pm clear` → `provision.sh` → re-favourite both,
nothing played → the first".

**40. SHOULD-FIX — E21 (l.425-428) hits the live internet from the AVD.** "The real directory (network on, the build with
real defaults). The radio page fetches from the real mirror … n > 1000". Network rows run against host fixtures; the live
directory is a phone fact and flaky by nature. *Fix:* move it to **P10** (the S25U, a build with the real defaults),
RECORDED with the HTTP status and mirror, plus a JVM test of the mirror-resolution rule (DNS SRV/A lookup of
`all.api.radio-browser.info` → a host list → one picked) so the "resolve a mirror through DNS" Decision (l.141-142) has a
failable check that needs no network.

**41. SHOULD-FIX — the music catalogue has no named fixture, no hand-written expected rows, no offline / error rows and no
etiquette rows.** Fixtures (l.321-323) name directory_server.py and icy_server.py only; task 11 says "catalogue fixtures for
Q1"; E13: "Search 'qa artist' against the fixture catalogue → rows with title, artist and artwork" — expected values are
not written in the row (the T14-2 lesson), Cover Art Archive has no redirect, no row covers airplane / 500 / stopped server,
and the Decisions' etiquette ("send a User-Agent naming the app"; MusicBrainz "1-request-per-second etiquette is honoured")
is unasserted. *Fix:* `qa/phase-20/scripts/catalogue_server.py` at `10.0.2.2:8081` serving MusicBrainz `/ws/2/` JSON and
`/coverart/` PNGs (the Cover Art base redirected by the same debug route as the directory); E13: "exactly three
`catalogue_row:` rows — 'QA Song A' / 'QA Artist' / 'QA Album', … (hand-listed) — artwork = the fixture PNG's colour ± 4;
airplane → `[music] catalogue \"qa artist\": offline` and the page's offline line; `/500` → `error 500`; server stopped →
`error connect`; the fixture log shows `User-Agent: Tessera/<version> (…)` on every request (directory and catalogue) and
five searches typed back-to-back arrive ≥ 1.0 s apart". E19 gains the `[music] catalogue …` forms.

**42. SHOULD-FIX — E14 (l.395-398) says phase 17 has no fixture yet; it does, and the music side needs its own data and
lines.** "Phase 17's media-server fixture (its method, once phase 17 writes it — until then this row is not runnable and
says so)" — phase 17 E22 / Fixtures (l.568-570) define it; that library holds only `qa-steps.mp4`. *Fix:* E14 cites phase 17
E22's container (pinned digest, #28) with a Music library of three MUSIC6-style MP3s (known durations) in
`qa/phase-17/fixtures/jellyfin/`; adds wrong password → `[music] server 10.0.2.2:8096: unauthorised`, `docker stop` →
`unreachable` with the local pivots intact (the music side's lines live in the MAIN ring — `MusicService` is main-process —
so they are `[music] server …`, not phase 17's `:video`-ring `[video] server …`).

**43. SHOULD-FIX — E19 (l.416): `stream: connected <url>` would log the Jellyfin token.** Jellyfin stream URLs carry the
token as a query parameter (`/Audio/<id>/stream?…&api_key=<token>`); the diagnostics ring is readable through `dumpsys` and
on the Settings Diagnostics page, and row evidence saves it — a credential in a log (phase 17's "Trust"). *Fix:* "stream URLs
are logged with the query string removed (`stream: connected http://10.0.2.2:8096/Audio/<id>/stream codec=mp3`)"; E14
asserts the issued token (read as in #28) is absent from the saved ring slices, logcat and `qa/phase-20/**`.

**44. SHOULD-FIX — NEEDS-HUMAN: H5 is the wrong kind, and the Jellyfin music view has no row.** H5: "*fidelity* — the
catalogue and 'Listen on' pages against R11 §Movies & TV's forms" — r11/movies-tv.md measured no Browse / Store half
("phase 17's Browse pivot (Y8, a P4 design) fills that role"), so nothing can close a fidelity row. The server's music
listed "as albums / artists / songs in the Music idiom" (task 8) has no H row. *Fix:* H5 → *accept* (P4, phase 17's Y8 / Y9
forms); add H10 *accept* the media-server music view (entry point, grouping, the server name line).

**45. SHOULD-FIX — E4 (l.352-353) and E8 (l.368-370): host-clock timing.** "within 5 s of the fixture's switch (icy_server.py
logs its switch time)" compares a host timestamp with a device observation; "within 5 s the title line reads
'Reconnecting…'" and "playing again within 10 s" are host polling. *Fix:* icy_server.py switches the StreamTitle 20 s after
each connection opens, so E4 asserts "the `[music] now playing … title=QA Song 2` line's `wall=` − the `stream: connected`
line's `wall=` = 20 000 ± 5 000"; E8: "MARK before `airplane-mode enable`; `stream: lost, retrying` `wall=` − MARK ≤ 5000;
MARK2 before `disable`; `stream: reconnected after N ms` with its `wall=` − MARK2 ≤ 10 000".

**46. SHOULD-FIX — the Acceptance preamble lacks the round-1 harness rules and forces a second APK.** No Seeding line (E4 /
E17 read the Music, Photos and Camera tiles and "Auxio playing (a pinned tile)"), no C-6 force-stop after E13's stub launch,
no C-4 form for E2's "Fresh install", and the fixture route (l.321-323: "A debug APK with the directory and catalogue
`BuildConfig` fields pointed at `http://10.0.2.2:8080/` … and, for E21 only, a build with the real defaults") makes phase
20's rows run on a different APK from every other row, while phase 17 uses a debug-only runtime pref on one APK. *Fix:*
preamble: "Seeding: `layout_restore qa/phase-20/baseline_layout.json` (derived from phase 19's newest, MUSIC / PHOTOS /
CAMERA slots, Auxio pinned, `manualSizes` for every tile; #57); after E13's / E16's stub launches `am force-stop
app.tileshell` + Home (C-6); 'fresh install' = `pm clear` → `provision.sh` → Home (C-4); the directory, catalogue and
Cover Art bases are debug-only prefs (`qa_radio_base`, `qa_music_catalogue_base`) written with `prefs_edit.py`, phase 17's
route, so one debug APK serves every row and the evidence stamp matches."

**47. SHOULD-FIX — E19 (l.414-418): silent states named in Decisions / Edge cases have no line.** Missing: the catalogue's
lines (#41); "the armed timer is cleared with a diagnostics line when the item goes live" (l.465-466); "`stream: unsupported
playlist`" (l.459); "`handoff: <service> did not open at the title`" (l.473); the offline first-open empty state (l.455-456,
no cache yet); Pandora's "search not passed" (#38). *Fix:* add `[music] sleep: end-of-track cleared (live item)`, `[music]
stream: unsupported playlist`, `[music] handoff: <service> did not open at the title`, `[music] radio: no cache yet
(offline)`, and #38 / #41's lines to E19; each asserted by the row that produces the state (E5, E3 with a `.pls` station in
directory_server.py, E13, E2's fresh-install-offline half).

**48. NOTE — Decision Q3 (l.56-63): the FM negative rests on web sources only.** *Fix:* P11 on the S25U, RECORDED: `adb
shell cmd package has-feature android.hardware.broadcastradio` → false; `adb shell service list | grep -ci broadcastradio` →
0; `adb shell pm list packages | grep -i fm` → none — device evidence for a negative claim (a source check, not a web
summary).

**49. NOTE — depends-on (l.5) omits 15; E16's utterances are unnamed.** The Decision (l.92-93) says phase 15's build task 0
"must be in first", yet depends-on is `[03, 10, 17]` (15 only transitively). E16's phrases ("play jazz radio", "play QA News
One", "play radio", "listen to qa artist on <stub>") have no ids in `utterances.py`. *Fix:* depends-on `[03, 10, 15, 17]`;
name ids (`radio_genre_jazz`, `radio_station_news1`, `radio_last`, `handoff_listen_stub`) built through `utterances.py build`
(padded, #60).

---

## Cross-phase

**50. SHOULD-FIX — R11 has landed (INDEX: "DONE 2026-09-23 (all ten sections)") and none of 15–19 applied it; several rows
now contradict the measurement.** Every doc still says pending — 15 header "pending on disk 2026-09-23", 16 header
"r11/src/calendar/ and r11/src/people/ are still empty", 17 Y table "r11/photos.md pending", 18 "not written as of
2026-09-23", 19 "pending on disk" — and every geometry row reads "once R11 lands". Contradictions a correct build would hit:
**15** — clock.md G1 "W10M had **no full-screen ring page** … ring as a **toast pinned to the top**" (H1 [fidelity] "the ring
page" can never close; H5 must become a P4 accept); G4 "Tapping a tab does **not slide**" vs E10's "pivot settle 250 ± 17
ms"; G3 tab labels ("World Clock"); calculator.md 4 "The label is **'Weight and Mass'**, not 'Weight'" vs E12's exact list;
calculator.md 5 "not in Programmer" for history; clock.md G13 "P6's phone re-measure has nothing from R11 to re-measure".
**16** — people.md §11 "**The contact-row stand-in is wrong** … a **50-epx row with a 32-epx avatar**" vs E10's "rows at
70.5 ± 1.2 epx with the 48-epx avatar"; "a **72-epx cell grid whose column count follows screen width** (4 columns on 360
epx)" vs E20's "5 columns, ≈50 ± 4 epx pitch"; calendar.md: day view, event page, editor, reminder toast, settings and all
motion UNMEASURED (H1 closes only for Agenda / month header; H6 / H7 / H11 stay accept). **17** — photos / camera /
movies-tv: all motion UNMEASURED (Y6 / H4 accept, as written); movies-tv G1 and the ≡ pane (#29). **18** — files.md "**Y1
(root page) — the stand-in is the wrong shape** … the measured form is the **≡ pane**"; "Rows are **64-epx two-line rows**
(1.5), not the 44-epx app-list rows"; type sort, zip, Recent, free-space line P4 (#32). **19** — #34. *Fix:* one pass per doc
(build tasks "R11 values applied" can run now): replace "(pending)" with the section's values and tolerances, re-cut the
contradicted rows above to R11's numbers, re-kind each H row ([fidelity] only where R11 has a HIGH / MEDIUM value, [accept]
for UNMEASURED and P4), write 15 E10 / E13 / E24, 16 E19 / E20, 17 E19, 18 E11, 19 E15, and strike 15 P6.

**51. SHOULD-FIX — reboots and screen-offs mid-row do not re-wake the AVD.** The ENV lesson: the AVD "came back … asleep
('power_button'); rows then tapped a dark screen". `wake_device` runs only in `row_begin` (`lib.sh:47-56, 79`). Rows that
reboot and then tap: 12 E4 ("`adb reboot` + … `wm dismiss-keyguard`"), 15 E6 (three reboots), 15 E7, 16 E1 (E4b's reboot
form), 16 E6, the edge-case reboot rows in 15 / 16 / 17 / 18 / 20. *Fix:* each doc's preamble: "after any `adb reboot`
(boot-completed poll) or `dumpsys battery unplug` / `KEYCODE_SLEEP` step, the driver calls `wake_device` (AC power, stay-on,
wake, dismiss keyguard) and asserts it printed `Awake` before the next tap"; the listed rows name the call.

**52. BLOCKING — ring slicing by `wall=` is applied nowhere; several rows fail by design or pass on stale lines, and every
coverage row greps a ring that resets.** The lesson (qa/phase-03/scripts/j7.sh:14-15): "The ring is a fixed-size buffer:
once full, its line count stops growing, so 'lines after N' is empty (run 3). Slice by the ring's own wall-clock stamps
instead." No doc's preamble says how a row reads "diagnostics" beyond "phase 01's command" (the whole ring), and `lib.sh`
still offers only count-based helpers (`replies_since() { # marker-line-count`, `tail -n`, l.264-267; `reply_text` = the last
`text=` line of the whole ring, l.259-262). Read literally: **15 E26** "no `[calc] tess` line for any of the three" comes after
the same row's positive steps wrote `[calc] tess` lines → fails on a correct build; **13 E1** the second "`acrylic=on`"
(after `set-mode 0`) is satisfied by the first line, and the Midnight sub-row's `acrylic=off reason=setting` by the earlier
toggle step → cannot fail; **12 E11** surface (b)'s `[theme] preset <name> applied` is satisfied by surface (a)'s (same
process, no restart) and **12 E12**'s per-preset `[fluent] static backdrop rebuilt` likewise; **14 E7** (a separate row, no
restart after E6) `reply_text` returns E6's identical doors line if the typed request produced no reply; **14 E8** "the doors
line is spoken" after unlock, same. And the coverage rows — 11 E14, 14 E16, 15 E22 ("appears in the ring at least once across
E0–E21 and E26–E28"), 16 E21, 17 E18, 18 E12, 19 E12, 20 E19 — grep one final ring, but `Diagnostics` is an in-memory deque
(`diag/Diagnostics.kt:15-26`) cleared by every `am force-stop` / `pm clear` / `layout_restore`, so only the last row's lines
survive → they fail by design. *Fix:* (1) `lib.sh` gains `ring_mark()` (`adb shell date +%s%3N`), `ring_since <mark>
[launcher|speech|<service component>]` (j7.sh's `wall=` filter, one function), `reply_since <mark>` (the first `text=` reply
after the mark, empty if none), and `row_end` saves `ring_since $ROW_MARK` of each ring the row names to
`<row>/ring-<name>.txt`. (2) One line in every 11–20 preamble: "Every ring assertion reads `ring_since` from a MARK taken
immediately before the step's action (after any clock jump, so the MARK is on the new clock); absence assertions read the
same slice; `reply_text` is `reply_since <MARK>`." (3) Coverage rows: "grep the union of `qa/phase-NN/*/ring-*.txt` from
this build's run (the row log's APK id matching), each pattern at least once". (4) 15 E26: MARK before each negative.

**53. BLOCKING — battery saver cannot be switched on while the AVD is on AC power, which `wake_device` now forces at every
row.** `wake_device` (`lib.sh:48-56`): "`adb shell dumpsys battery reset` … `adb emu power ac on` … `adb emu power status
charging` … `svc power stayon true`"; the AVD today: `mIsPowered=true`, `AC powered: true`. AOSP refuses low-power mode
while powered (`PowerManagerService.setLowPowerModeInternal` returns false when `mIsPowered`, and the battery-saver state
machine turns it off on plug-in — the reason phase 03's Doze step already runs `dumpsys battery unplug`, `edge.sh:101`), so
`cmd power set-mode 1` leaves `low_power` 0; the build-start probe below settles it on this image before any row relies on
either answer (if it shows `low_power` 1 on AC, this finding drops to a NOTE). Phase 13's "Verified on the AVD
2026-09-22: `cmd power set-mode 1` sets `low_power=1`" (l.131-132) predates the 2026-09-23 wake change. Affected: 13 E1
("`cmd power set-mode 1` → within 1 s `[fluent] acrylic=off reason=battery-saver`") and its Controls line ("acrylic OFF =
`adb shell cmd power set-mode 1` … restore `set-mode 0` and assert `settings get global low_power` = 0", which passes
trivially), every 13 row that uses that control, 12's "A preset applied under battery saver" edge case, 19 E9 ("battery
saver on → `settings get global low_power` = 1"), and on the phone 19 P3 / 13 P3 when the S25U is on USB. *Fix:* build-start
probe recorded in phase 13's Decisions, replacing the 09-22 line: "after `wake_device`: `cmd power set-mode 1`; `settings
get global low_power` → (expected) 0; `dumpsys battery unplug`; `cmd power set-mode 1` → 1". `lib.sh` gains
`battery_saver_on` = `adb shell dumpsys battery unplug; adb shell settings put system screen_off_timeout 1800000` (stay-on
stops when "unplugged"; the old value saved) `; adb shell cmd power set-mode 1`, then **assert** `low_power` = 1 (a
precondition that fails loudly), and `battery_saver_off` = `set-mode 0`, restore `screen_off_timeout`, `wake_device`
(which resets the battery). 13's Controls line, 13 E1, 12's edge case and 19 E9 call them; 19 P3 / 13 P3 run over Wireless
debugging with the phone unplugged, or record the charging state and One UI's behaviour (RECORDED).

**54. BLOCKING — the upgrade legs cannot upgrade: `provision.sh` always installs the current build.** `lib.sh:24`:
`APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"` (no override); `provision.sh:35`: `adb install -r -g "$APK"`.
**17 E1** (l.583-588): "install `qa/phase-17/upgrade/<tag>.apk` …, `provision.sh`, re-point PHOTOS to Aves …; `adb install -r`
the phase-17 APK" — `provision.sh` replaces the old build with the new one, whose first start runs the PHOTOS seed
(`-> assigned`); the hand assignment is then made on the NEW build, and the final install logs `-> already run`, never the
asserted `kept user's` → fails by design. **16 E1** (l.438-443) avoids `provision.sh` ("the HOME / ASSISTANT roles and home
activity as `provision.sh:50-54` sets them") — but the pre-16 build (built after phase 12, order 11 → 13 → 12 → 14 → 15)
has the wizard, the marker is gone after `adb uninstall`, and notification access, usage access and the keyboard are not
granted, so Home shows `wizard_page` and "the PEOPLE tile still reads the image's Contacts" is not in the dump → fails by
design. *Fix:* `lib.sh`: `APK="${TILESHELL_APK:-$REPO/app/build/outputs/apk/debug/app-debug.apk}"`. Both legs: "`adb
uninstall app.tileshell`; `TILESHELL_APK=qa/phase-NN/upgrade/<tag>.apk qa/phase-03/scripts/provision.sh` (the old build,
every grant, the wizard finished); hand-assign through Settings > Tile apps; `adb install -r
app/build/outputs/apk/debug/app-debug.apk` (NOT provision.sh, so the upgrade path runs); Home." Precondition assert: the
install -r succeeds (same debug signing key; `INSTALL_FAILED_UPDATE_INCOMPATIBLE` fails the row loudly). The row log records
both APK ids; `apk match: NO` on the first leg is expected and noted.

**55. SHOULD-FIX — "RECORDED" (C-13) has no mechanism in `lib.sh`.** The docs (13 E9, 15 E21 / P1, 16 E6 / E12 / P1 / P3,
17 E14 / P2–P4 / P12–P15, 18 P5, 19 P1 / P4) say a recorded clause "ends its PASS/FAIL line with 'RECORDED', so `lib.sh`
`row_end` never counts it as a pass". `_verdict` (`lib.sh:87-91`) increments PASS or FAIL by the verdict word whatever the
detail says, and `row_end` (l.125-137) fails a row with zero assertions ("A row that asserted nothing is a row that tested
nothing") — so 13 E9 (recorded, not gated) either counts passes or fails by design. *Fix:* `lib.sh` gains `record <name>
<value>` (prints `RECORD <name> <value>`, increments a RECORDED counter, never PASS / FAIL) and `row_end` reports "`<row>:
recorded only (<n> facts)`" with exit 0 when PASS + FAIL = 0 and RECORDED > 0; C-13's line in each preamble becomes "a
recorded clause uses `lib.sh` `record`, never an assert".

**56. SHOULD-FIX — "fill the volume" preconditions are never checked, by two different routes.** 15 E19: "`adb root`,
`fallocate -l $(( free_bytes - 60*1024*1024 )) /data/media/0/fill.bin`"; 17 edge (l.862): "`adb shell fallocate -l …
/sdcard/fill.bin`" (no root, through FUSE); 18 E4b "fill with `fallocate` (phase 17's edge-case command)" and E13 "with the
volume filled to leave < 3 GB". If `fallocate` through the FUSE `/sdcard` is refused or partial, 18 E4b's "it still moves to
the bin" and 18 E13's refusal run on a volume that is not full — a vacuous pass or a spurious fail. *Fix:* one helper
`fill_volume <leave_bytes>` (15 E19's root route to `/data/media/0/fill.bin`) that asserts `adb shell df /sdcard` free ≤
leave + 5 MB after the fill, and `unfill_volume`; 15 E19, 17's edge case, 18 E4b / E13 and the copy-onto-full edge call it.

**57. SHOULD-FIX — the baseline chain: 18 omits `slots`, 19 and 20 name no baseline, 15 may inherit fixture tiles.** The
lesson: baselines need "addedOnce markers, hand-set sizes AND the slots their markers imply (a MUSIC slot was missing)".
18 build task 12 (l.269-271): "derived from `qa/phase-17/baseline_layout.json` with a Files tile pinned …, `manualSizes` set,
every `addedOnce` marker of the build" — no `slots`. 19 E17: "The Settings tile (`shell:settings`, on the baseline layout)"
— which file is not said; phase 02's file on a post-17 build lacks `slot:calendar:v1` … `slot:camera:v1`, so the shell
re-seeds on load and `layout_restore`'s `slots` comparison (`layout.sh:36-38`) fails three times. 20 has no seeding line
(#46). 15 build task 8 derives from "phase 11's once it exists" — phase 11's file pins `tileclient-a` / `-b`, which
`provision.sh` does not install (they are repo testapps, not in `~/android-fixtures`), so a restore on a freshly
provisioned AVD drops those tiles and fails. *Fix:* 18: "`addedOnce`, `slots` (MUSIC, CALENDAR, PEOPLE, PHOTOS, CAMERA) and
`manualSizes` kept from phase 17's"; 19: "every row that reads Start restores `qa/phase-19/baseline_layout.json` (phase
18's, unchanged: this phase adds no marker) and asserts zero `-> assigned`"; 20: #46; 15: "derived from phase 02's file (plus
any marker 11–14 add — none), never carrying phase 11's fixture tiles, which only phase 11's rows install".

**58. SHOULD-FIX — no row proves the AVD network rows stayed off the live internet.** 17 E13 / E20–E22 and 20 E2–E16
assert fixture responses, but a request that also went to a real host (artwork, #27; a hard-coded fallback URL; a mirror
lookup) would pass unseen. *Fix:* a shared guard for those rows, recorded in both preambles: "`adb root; UID=$(adb shell pm
list packages -U app.tileshell | sed 's/.*uid://'); adb shell iptables -I OUTPUT -m owner --uid-owner $UID ! -d 10.0.2.2
-j REJECT`; at row end `iptables -L OUTPUT -v -n` shows 0 packets on that rule (gated), then the rule is deleted and `adb
unroot`". Offline sub-rows keep airplane mode.

**59. NOTE — phase 12 E14's template is cited by five docs; after #5's fix each instance must say `PROVISION_FINISH_WIZARD=0`.**
Listed so the triage applies it once: 15 E28, 16 E26, 17 E25, 18 E15, 19 E16 (and 16 E26 / 15 E28's `[wizard] not shown:
core held` step stays valid under #5's precedence rule).

**60. NOTE — voice rows should record the recogniser's own verdict.** The J7 lesson: the silence gate is relative to each
capture (`SherpaAsr.kt:61-86`), and a dropped capture ends in "I didn't catch that", which a negative row ("→ the
not-understood handler") could misread. New utterances (14's five, 15 E26's six, 20's #49) are built through
`utterances.py build` (0.4-s lead, 3.0-s tail, `utterances.py:117-125`), which gives the gate its floor. *Fix:* each `say`
step also saves `speech_dump` sliced from its MARK and asserts the `:speech` ring's final line for that capture is not "asr:
no speech" (a gate drop is then a FAIL with its own reason, not a matcher miss).

**61. NOTE — the shell-logged motion clock self-reports its own animation.** C-5's `[motion] <name> t0 peak overshoot settle`
is computed from the spring's / tween's own progress in `withFrameNanos`, so a janky run with dropped frames still reports
the programmed numbers, and the screenrecord that used to show jank is variable-rate. *Fix:* the line gains `frames=<n>
maxGapMs=<ms>` (consecutive frame-time gaps inside the motion); rows assert `maxGapMs` ≤ 2 vsync (33.4 ms) alongside the
numbers — jank then fails the row on the shell's clock.

**62. NOTE — rows asserting the drawn status bar at 28 epx inherit a value R11 measured as 24.** r11/settings-front.md, clock
G5, people.md P0.2: "the measured W10M status bar is 24 epx … Raise it at phase 01". Rows citing "28 epx (R3 C4)": 12 E8, 15
E10 / E13 / E23 / E24, 16 E19 / E20, 17 (R11 note), 18, 19 E2. *Fix:* no re-cut here; each such assertion reads "the drawn
status bar at `SystemBars.STATUS_EPX` (phase 01's value, open at phase 01 against R11's 24 epx)" so the rows follow phase
01's decision instead of hard-coding 28.

**63. NOTE — the TMDB and Jellyfin credentials need one hygiene rule shared by 17 and 20.** #26 and #43 are two instances.
*Fix:* one Decision (phase 17 "Trust", cited by 20): "no credential (TMDB token, Jellyfin token, the fixture passwords) is
ever written to a diagnostics line, logcat, a URL that is logged, or an evidence file; every row that touches one ends with
the leak scan of #26 over `qa/phase-NN/**`, the saved ring slices and `logcat -d`."

**64. NOTE — phase 12's "Default" preset is the out-of-box control that every other row assumes.** 12 E11 saves
`start_theme.xml` after `pm clear → provision.sh` as "the control"; with #5, provisioning also writes the wizard marker but
must not apply a preset. *Fix:* E11's control step asserts `theme_preset` is absent or `default` right after provisioning,
so a provisioning change that applies a preset is caught.
