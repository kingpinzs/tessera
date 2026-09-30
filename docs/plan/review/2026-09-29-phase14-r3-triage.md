# Phase 14 — review round 3 triage (the last round; 2026-09-29)

Reviewers: Fable (design / correctness, `2026-09-29-phase14-r3-design.md`: BLOCKING 3 · SHOULD-FIX 6 · NOTE 4) and a second
Fable (testability / evidence, `2026-09-29-phase14-r3-testability.md`: BLOCKING 6 · SHOULD-FIX 10 · NOTE 7). Roster: fable +
fable (codex out both ways — MCP not loaded; CLI "You've hit your usage limit ... try again at Oct 3rd, 2026 4:48 PM", rc=1).
Brief: `2026-09-29-phase14-r3-brief.md`. Both reviewers worked read-only and returned their reviews as text; the lead saved them.

Overlaps: D3 = V1 (`KEYCODE_HOME` from the pod bay), D2 = V8 ("open the pod" is phase 03's `OpenApp`), D13 ≈ V2 (voice rows
without host audio), D9 ≈ V12 (E4's launch-failure sub-rows), D4 ≈ V7 (Now playing's feed state and fixture), D5 ≈ V23
(Agenda), D6 ≈ V4 / V18 (Weather), D7 ≈ V14 (backdrop key and E14), D10 = V20 (stale cites, the wizard-gate text).

**Lead's own check before accepting** (read on main df527165, not taken from the reviews): `StartActivity.kt:491-503` — Tess's
session is a window over Start, Start is never paused and gets only its window focus back (D1); `CommandMatcher.kt:86` — "open
…" falls to `OpenApp` (D2 / V8); `qa/phase-02/README.md:73-75` — `KEYCODE_HOME` is not re-delivered to the resumed home
activity (D3 / V1); `qa/phase-03/scripts/lib.sh:287-290` — phase 03's `ensure_start` is `KEYCODE_HOME` + sleep (V1);
`lib.sh:155-159` — `diag` reads the notification listener's dump, and `qa/phase-12/scripts/p12.sh:21,48` has `start_dump` /
`start_slice` (V3); `weather/WeatherLocation.kt:31-33` — COARSE **or** FINE (V4); `provision.sh:46` — `locksettings
set-disabled true` (V5); `qa/phase-13/scripts/reminders_fixture.sh:47,63,128` — the typed reminder fixture (V6);
`TileContent.kt:107` — `Transport { PLAY_PAUSE, STOP, NEXT }` and `MusicFeed.kt:45-48` private state (D4 / V7); the manifest
gives `CortanaSessionService` no `android:process`, so the session and `StartActivity` share the main process (D1's
in-process route holds).

No finding reopens one of Jeremy's dated rulings (Q1 B; R10-Q5's name and easter egg; the scope add). Every mechanism the
reviewers re-cut was an agent call. **One question goes to Jeremy (Q-R3-1)**: how the voice rows run now that QA never touches
the host's audio — it decides what E6 / E8 / E9 / E13 prove on the AVD and whether a spoken row can hold the gate open.

## Rulings

Every row is ACCEPTED unless it says otherwise; "apply" means the reviewer's fix text, adapted only to fit the doc's line.
Rows marked **Q-R3-1** wait for Jeremy's answer.

| Id | Sev | Ruling / exact change |
|---|---|---|
| D1 | BLOCKING | Apply D1's Route paragraph as written: the action only records the request and closes; the session's close starts the HOME intent with the `POD_BAY_CHECK` signal extra (only when a request is pending), then hides; `StartActivity` consumes in ONE place, the `homeEvents` collector, on `PodBayCheck` emitted by `onNewIntent` (with the extra), `onWindowFocusChanged(true)` and `onWizardEnded()`; held while the wizard shows; nothing consumed in `onResume`. Build task 5 and the mid-line edge case re-cut as D1 says. The build-start check (does a HOME intent from the session reach `onNewIntent` under a resumed Start) is recorded; the focus-back path makes the rows independent of it. |
| D2 = V8 | BLOCKING | Apply: "open the pod" / "open the bay" are phase 03's `OpenApp` ("I don't see an app called the pod."), never the pod bay and never not-understood; the `OpenApp` absence clause is scoped to the four pod-bay utterances. Decisions l.102-103 unchanged. |
| D3 = V1 | BLOCKING | Apply both: E1's Home sub-row uses `tap_node nav_windows` (T11-16) and asserts `[podbay] closed by home` + `home: page START, scrolled to top`; new phone row P5 = `KEYCODE_HOME` from the pod bay (P4 is the struck widget row); Decisions l.134-135 names both routes into `homeEvents`. Harness line: phase 03's `lib.sh` `ensure_start` and phase 11's `q.sh` `ensure_start_page` get the same fix as `gestures.sh` (`KEYCODE_HOME`, then `KEYCODE_BACK` while `app_list` / `pod_bay` shows, then assert `start_page` alone); build task 6 lists all three; E13's `ensure_start` row runs against all three. |
| D13 ≈ V2 | BLOCKING | **Q-R3-1.** Agreed by both, independent of the answer: bare `say` is never used — a spoken step is `speak.sh <id> <settle>` with rc asserted 0 (`speak.sh` taps the mic; `KEYCODE_ASSIST` opens HOME mode, not listening); every non-audio assertion (match line, `reply_since`, the `speaking done` → `opened` `wall=` order, session window, dump) runs on a typed leg (`type_request` after `KEYCODE_ASSIST`); a JVM test asserts `CommandMatcher.hotwords()` holds the three phrases; E13's phase 03 E3 re-run is typed; E8's typed leg needs the text box over the keyguard (checked at build start; if absent, a JVM test on `LockGate.allowedWhileLocked` / `restate` plus the `[cortana] locked: … gated` line on the typed request). What Q-R3-1 decides: whether the spoken sub-rows (E6v / E8v / E9v, the reply RMS, C-30) are gate rows, opportunistic rows, or phone-only. **RULED 2026-09-29, Q-R3-1a (a)** (Jeremy: "(a)", after "dont change the system audio but you can use it now"): the spoken rows run on the emulator's own gRPC audio (injectAudio in, streamAudio out), never the host's; a build-start spike proves it; if it fails, typed-only plus P1 / H13. Applied to the doc (Audio route + spike paragraphs, E6 / E8 / E9 / E13, build task 6, the hotwords JVM test); V16 applied with it. |
| V3 | BLOCKING | Apply: E17 reads every ring line through phase 12's `start_slice` (`p12.sh`), a missing header fails the row; the preamble names that read for rows with notification access revoked; build task 6 sources `p12.sh`. |
| V4 | BLOCKING | Apply with D6: revoke FINE and COARSE (BACKGROUND cannot outlive FINE), `ring_save` before any revoke, restore all three and assert `granted=true`; the same `ring_save` note on the READ_CALENDAR sub-row. |
| V5 | BLOCKING | Apply: E8 and E11 clear `locksettings set-disabled` before setting the PIN and assert `isKeyguardShowing=true`; Tess over the keyguard by `e10.sh`'s `lock_and_open`; the card is `cortana_card:unlock`, the button `cortana_card_button:unlock`; restore in an EXIT trap (`clear --old 1234`, `set-disabled true`, wake, `wm dismiss-keyguard`). |
| V6 | BLOCKING | Apply: E3's Reminders fixture is phase 13's typed `reminders_fixture.sh` (`make_typed_reminder` ×2 + the Whenever one through the page), expected titles and sublines written by hand from `ReminderText.listSubline`'s forms; `reminders_restore` → the empty line. E4's "a reminder present" cites the same. |
| D4 ≈ V7 | SHOULD-FIX | Apply both: the ADD to `MusicFeed` (`now: StateFlow<NowPlaying?>`, null when no routed session with metadata — unlike the tile, which keeps the last track idle), `Transport.PREVIOUS` with its `send` branch (tile controls unchanged) — build task 3; E3's Now playing uses phase 10's `music_lib.sh` fixtures (six tagged tracks) so NEXT and PREVIOUS have tracks, expected titles written by hand from the fixture tags, the stop by `cmd media_session dispatch stop`. |
| D5 ≈ V23 | SHOULD-FIX | Apply both: the ADD to `CalendarFeed` (`agenda` state, a second Instances query to local end of tomorrow, all-day day read in UTC, cap 6) — build task 3; E3's Agenda on `edge_calendar.sh`'s local "qa" calendar with a tomorrow 09:30 insert and hand-written expected texts. |
| D6 | SHOULD-FIX | Apply: problems win over a cached report (`NO_PERMISSION` / `LOCATION_OFF` → the location line), `empty: no report` for no report, condition text from `WmoCodes.word`, degrees from `WeatherFormat.degrees`; ADD `State.stale` written by `check()` — build task 3. |
| V18 | NOTE | Apply: cite `item2.sh:14-21` (push) and `e9_e12_clock.sh:19-24` (clock jump); `ring_save` before the push, MARK after the jump, the Updated row within 180 s with h:mm from the fixture's `fetchedAtMs` on the host; restore `auto_time 1`, clock, `adb unroot`. |
| D7 ≈ V14 | SHOULD-FIX | Apply both: ADD `FluentSurface.POD_BAY` and a surface parameter on `AppListBackdrop`; the pod bay passes the same key arguments as the app list (A-N4); `visible` by the app list's ≥ 50 % rule — build task 2. E14: the strip by `dumpq.py clear_rows` / `checker_rows` (phase 13 E2's method) with no media session; the A-N4 check (zero `static backdrop rebuilt` lines across Start ↔ app list ↔ pod bay swipes); acrylic off by `set_pref transparency_effects` with `ring_save`; restore. |
| D8 | SHOULD-FIX | Apply: pod launches call `launchApp(target, bounds, null)` as the app list does — no Start exit, phase 01's entrance on return, nothing promoted; targets named; H11 re-worded to that; the "Start exit already playing" edge case struck. |
| D9 ≈ V12 | SHOULD-FIX | Apply one form, both assertions: unassigned MUSIC slot → `slot_picker` in the dump AND `[podbay] launch nowplaying failed: slot unassigned`, Back closes the picker; no assistant role → `CortanaRoleNoticeActivity` resumed AND `[podbay] launch reminders failed: no session: not the assistant` (plus phase 03's role-notice line), Back + Home; restore re-adds the role and verifies `VoiceInteractionService ready` + `get-role-holders`. |
| V9 | SHOULD-FIX | Apply: E7 starts from Start with no `pod_bay` asserted, opens Tess (`KEYCODE_ASSIST` → `cortana_session`) before `type_request`, asserts `[podbay] opened by voice (doors)` in the slice. |
| V10 | SHOULD-FIX | Apply: E12's edge swipe runs with DeskClock seeded (`topResumedActivity` = DeskClock after it); the no-history branch is struck; overlay restore read back with `cmd overlay list`. |
| V11 | SHOULD-FIX | Apply: E1 seeds DeskClock first, asserts `topResumedActivity` on the Back sub-row, then C-6 before the Home sub-row. |
| V13 | SHOULD-FIX | Apply: E16 lists its patterns literally (the reviewer's list). `opened by settings` is struck from the Decisions list (nothing opens the pod bay from Settings). `launch <id> failed: ActivityNotFoundException` stays in the code as a guard and is marked "written, exercised by no row — recorded (C-26)". E1 asserts `closed by swipe`. |
| V15 | SHOULD-FIX | Apply: every per-pod assertion follows `scroll_to_node <dump> pod:<id>`; E4 counts `pod_row:<id>:*` in that dump; E5 the same for `pod:reminders`. |
| V16 | SHOULD-FIX | Apply to every spoken step that remains after Q-R3-1: `speak.sh` rc 0, and the `speech_dump` slice holds `asr: levels … (heard=true)` and no `asr: no speech in the capture`. |
| D10 = V20 | NOTE | Apply: the stale line numbers (`home: page 0` :166, `beyondViewportPageCount` :278, pin band :316-319, `gestures.sh:28-49`); T14-7's text re-cut — the pager is not composed while the wizard shows (`StartActivity.kt:222-230`), Home is a no-op under it (`:150-152`), phase 12's `setStartVisible` gate is built (`:484`, `:516`), and the request is released by D1's `onWizardEnded()` `PodBayCheck`. |
| D11 | NOTE | Apply: build task 1 names the literal indices to re-cut (`pageCount` :141, the Home collector :160, Back :180-182, `index == 0` :282, the app list's `canFocus` :292, the home line :166). |
| D12 | NOTE | Apply: "Process, permissions, surface" gains the four surfaces for the adversarial review (the `showSession` destination extra validated as an enum name like `EXTRA_MODE`; the HOME intent's `POD_BAY_CHECK` signal; `SettingsActivity.EXTRA_PAGE = POD_BAY`; the in-process `PodBayRequests`). The gate runs the adversarial review on them. |
| V17 | NOTE | Apply: the Harness line names the known hits (`gestures.sh:44`, `q.sh:82`, `l13_2_row.sh:43` still correct, `p13.sh:67-72` computed coordinates; home-line greps `regress.sh:120`, `L13-3-investigation/back_interrupt.sh:29`) and the re-grep matches computed operands. |
| V19 | NOTE | Apply: E4 scrolls to `checklist:calendar:missing`; tag `settings_page_pod_bay` on the Settings page root; E5 leaves Settings and swipes before reading `pod:*`. |
| V21 | NOTE | Apply: E10's pod-hold sub-row adds "no `quick_burst`"; the folder-name sub-row asserts no `page=APP_LIST` / `page=POD_BAY` in a slice from a MARK before the step. |
| V22 | NOTE | Apply: E13's "now with the pod-bay phrase" is struck (E8 covers the locked phrase; phase 03's `e10.sh` is not edited). |

After the edits: the doc is still DRAFT, so no Change Log line is needed beyond INDEX's Stage A entry. Round 3 was the last
review round. Q-R3-1 is answered (via Q-R3-1a (a)) and applied; the doc goes FINAL on Jeremy's word.
