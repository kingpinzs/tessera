# Phase-doc review round 3 (last round): triage (2026-09-17)
Inputs: review/2026-09-17-phases-r3-opus-design.md (reviewer 1: R3D-01…R3D-10, R3-m1…R3-m7) and
review/2026-09-17-phases-r3-opus-test.md (reviewer 2: R3T-B1…R3T-B8, R3T-M1…R3T-M14, R3T-m1…R3T-m16, plus its §5 RV9 table).
Both reviewers' round-2 status tables were read; the only round-2 item left open (R2D-06, phase 09 part) is closed by R3D-03 / R3T-B8.

Dispositions: OWNER = Jeremy decides WHAT; the owner-dependent part is not applied and is recorded "OWNER, pending" in the phase doc,
while the parts that hold whatever he answers are applied · AGENT = applied to PLAN.md / INDEX.md / the phase docs under P1–P5, A4,
Q12, RV9, RV11–RV13, Rule 4 and Rule 16 · REJECTED = not applied, with the reason.

Counts (55 findings): AGENT 51 · OWNER 4 (pending, agent parts applied) · REJECTED 0.

## OWNER (all ruled 2026-09-17, see review/2026-09-17-phases-r3-owner.md; asked one at a time; the answer changes only the named clauses)
| ID(s) | Question | Clauses waiting on it |
|---|---|---|
| R3D-02 / R3T-B7 | Place reminders need to know where "home" is, and with no internet and no Google the phone can't turn a typed address into a map position. (a) You save a place by being there: "Save this spot as Home" on a Places page, or by voice. (b) Typed addresses too, looked up once online through a non-Google service (OpenStreetMap), which adds an internet use to A11. (c) Other / let me clarify. | 03 reminder-kinds line ("how a place is saved"), place card's unknown-place route, build task 12's place-saving route, E13's save step, edge "a spoken place with no saved place" |
| R3T-B6 (and R3-m6's direction part) | "Next time I talk to Mom" should fire when: (a) any answered call or any text between you and her, either direction; (b) only when you call or text her; (c) other / let me clarify. Agent default if unanswered: (b), the wording already recorded. | 03 reminder-kinds line (direction), E14's expected result per path, P8, edge "a call or text in a direction the ruling does not count" |

## Round-3 findings
| ID | Disposition | Applied fix (one line) |
|---|---|---|
| R3D-01 | AGENT | 03: new saved-reminder card line from R7 §3.8.1 (title / caption / lightbulb row / hyphen subline, ± 1.1 epx, lightbulb on every saved card); E7 measures it; PLAN R7 row adds §3.8. |
| R3D-02 | OWNER, ruled C 2026-09-17 and applied (review/2026-09-17-phases-r3-owner.md) | Place source asked (table above). Applied: "a Notebook place" dropped (Rule 16), "a saved, named place" wording; alerts re-registered at boot / package replace / process start; E13 mechanics (throttle, location on, background grant, 60 s limit, restore). |
| R3D-03 | AGENT | 08: locked AI mechanism (no personal context in the prompt; calendar read and contact lookup stay callable and the action-layer gate refuses them with "Unlock to continue"; diagnostics list providers and refused actions); E2 rewritten on calendar + contact via the not-understood handler with the Unlock sequence. 09: locked read-side line (providers, index, recall, context hooks off; memory-recall action gated) + E11 + edge. |
| R3D-04 | AGENT | 01: bar rule for every shell-owned screen (Start, app list, Settings, Weather; 03 / 06 / 09 cite it; measured exceptions kept), keyboard-over-drawn-nav-bar UNVERIFIED as X25 / H34, E19 + P7 extended. 03 session line, 07 and 09 cite it. 06: bars line (lock-screen nav variant over the keyguard, inactive keys, H23), E20, P7. |
| R3D-05 | AGENT | 07: burn-in cycle centred on the measured position (position 0 = digit top 0.646 H, ± d = 0.142 H, block top = digit top, every glance starts at position 0); E3 reads position 0 with the ± 0.016 H tolerance stated. |
| R3D-06 | AGENT | 02: add-to-folder line (drop on a folder tile after the same dwell adds it; band drop; no nesting; approximation H23, gesture from R6 §1.6.2's user-guide quote); E3 3rd / 4th member and 3 + 1 face; E8 folder paths; edges. |
| R3D-07 | AGENT | 03: place and person cards and spoken wording as P4 designs extending the measured time card (H22 place, H29 person), no recurrence, photo kept, unknown contact reply, R7 §3.5.11 cited LOW; E13 / E14 check card dump and reply text. |
| R3D-08 | AGENT | 03: at "When would you like to be reminded?", "whenever" / "no time" / Remind with empty time stores a Whenever reminder (H16 extended); E7 checks both routes; E15 setup names the route. |
| R3D-09 | AGENT | 05: &123 tap / slide past touch slop before the long-press timeout = slide-to-type / hold still to the timeout = one-handed popup (timeout approximation); stand-ins for alternates popup, docked 0.80-width layout, caps-lock indicator, 300-ms double-tap, 1100-ms double-space, learning threshold; §2.1.17 cited for &123 only; E12 drives each path with `UiDevice`. |
| R3D-10 | AGENT | Not an owner issue (caller ruling): dated correction note added to r2-triage's R2D-13 row (the question as asked had A = all three kinds; r2-owner.md is correct); ruling unchanged. |
| R3-m1 | AGENT | 03 H3 → "H5–H30"; 05 H2, E3 and the R6 line → "H3–H23". |
| R3-m2 | AGENT | 05: "switching languages if more than English is ever added" edge deleted. |
| R3-m3 | AGENT | PLAN status line (round-2 owner questions ruled, R7 applied to 03, round 3 applied); INDEX R7 row gates 03, 04, 06, 09; INDEX step 5-6 and reviewers line name round 3. |
| R3-m4 | AGENT | 04: interview item 10 asks the locked toggle-command question (a direct / b "Unlock to continue" / c clarify) at the phase 04 interview. |
| R3-m5 | AGENT | 06: settings-pages approximation line (Phone Text reply + Blocked calls; Messaging About + Related Settings from R7 §2.7.1 minus A11-excluded parts; Blocked messages), phase 01 Settings-hub style, H24; build task 6. |
| R3-m6 | OWNER, ruled A 2026-09-17 and applied (direction) · AGENT (permissions) | Direction asked with R3T-B6. Applied: `READ_CALL_LOG` / `READ_SMS` hard-restricted, allow-listed by plain `adb install` (never `--restrict-permissions`), SMS-read checklist row, call counts only after hang-up. |
| R3-m7 | AGENT | 07: Goal limited to secure locks; no-lock line (swipe keyguard: touch wakes to it; lock type None: touch ends glance and shows Start / last app, H12); E1 checks both; edge updated. |
| R3T-B1 | AGENT | 04: input-paths line (injected keys skip the accessibility filter; emulator volume keys via `adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0` then `…:0 EV_SYN:0:0`; action-center overlay receives touches directly); E3 and E4(g) use it. |
| R3T-B2 | AGENT | 04: offset split (drag 3.3–8.7 epx, after release 0.062 × remaining travel), approximation Y13 / H16 for the drag shape and early releases; E4(a) checks 2.2–9.8 epx, E4(b) checks 0.060–0.064 × remaining travel in the 78 % settle frames. |
| R3T-B3 | AGENT | 04: one tap-close timeline (handle gone → edge moving within 50 ms → background gone 216 ± 17 ms after the handle, c73; c245's 183 ms is the wipe's own length); E4(d) uses it. |
| R3T-B4 | AGENT | Same fix as R3D-04 (06 bars line, E20, P7; incoming-call lock-screen nav variant H23; over-keyguard fallback measures from window edges). |
| R3T-B5 | AGENT | 06: trust boundary = the shell app's uid (all APK code is ours; same-process components are indistinguishable); other uids refused and logged; in-app callers covered by adversarial review, no test verb; P5 rewritten to the test-APK refusal only; edge updated. |
| R3T-B6 | OWNER, ruled A 2026-09-17 and applied | Direction asked (table above). Applied: E14 runs all four paths (incoming call answered with `KEYCODE_CALL` and ended with `gsm cancel`, outgoing call via "call <contact>" + `gsm accept` if dialling + `gsm cancel`, incoming SMS, outgoing text), notification within 5 s of hang-up / provider write, unanswered and other-number calls fire nothing; call-log write at hang-up recorded; P11 / P12 → P7 / P8. |
| R3T-B7 | OWNER, ruled C 2026-09-17 and applied | Same question and applied parts as R3D-02. |
| R3T-B8 | AGENT | Same fix as R3D-03 (08 E2 on existing personal data via the not-understood handler, diagnostics not "runtime log", Unlock sequence; 09 E11). |
| R3T-M1 | AGENT | PLAN RV12 clock restore (set back with `date -u` from the host, `auto_time 1`, `adb unroot`, `wait-for-device`, `adb shell date +%s` within 2 s); 01 E9 and E12, 07 E3 and E10, 09 E3 use it; 01 E13 records and restores its Settings values. |
| R3T-M2 | AGENT | 03 E15 and 09 E9: precondition is the shell's own theme on Dark (Settings > Start dump); `cmd uimode` dropped. |
| R3T-M3 | AGENT | 04 P6 and 06 P8 added (phone screenrecord for ≤ 17 ms tolerances); 03 P5 extended with E15's pane, fill-hold, black and row-tap timings. |
| R3T-M4 | AGENT | 03: Settings keeps W10M's 172-epx position, Feedback's slot (cap top 124 epx above the bottom) stays empty (H23 widened); "anchored just above the text box" removed; E15 checks the empty slot. |
| R3T-M5 | AGENT | Same fix as R3D-08. |
| R3T-M6 | AGENT | 03 mechanics line records the 30-min background throttle; E13 sets `location_background_throttle_proximity_alert_interval_ms 5000`, enables location with `cmd location set-location-enabled true`, checks the background grant, passes within 60 s and restores; P7 keeps real latency. |
| R3T-M7 | AGENT | 03: only the card on screen is pending (a new request replaces it, closing drops it; H12); E10 runs gated commands in sequence, then taps Unlock on the last card, confirms the bouncer, enters the PIN and checks only that request ran. |
| R3T-M8 | AGENT | 04: open % = handle bottom ÷ screen height in Decisions; E4(b) measures the settle at 60 % and 78 %; Y6 bracketed at 40 % (closes) and 60 % (opens) as a stand-in check, judged in H9. |
| R3T-M9 | AGENT | 04: A20 named as the two-slider, media-playing state; E5 sets that state (Auxio playing, chevron expanded) before the 274-epx checks and checks the one-slider 100.4 epx separately. |
| R3T-M10 | AGENT | 06: Slide up and list swipe count distance / travel only (flings ignored); list delete writes the provider when the Deleted bar hides, Undo cancels; E12 and E16 give travel and 1500-ms durations; E16 states N / N / N − k. |
| R3T-M11 | AGENT | 06 E18: network on with `-tcpdump`, the shell uid's `dumpsys netstats` byte counters unchanged plus no openstreetmap.org DNS query (see notes); each MMS part written by a Send tap into the outbox or failed box. |
| R3T-M12 | AGENT | 06 E10: `grep -i resumed`. |
| R3T-M13 | AGENT | 09: E5 says the recorded helper pid is empty on the AVD; new P3 checks a running helper's recorded pid against the caller pid and `ps -A`; the pid Decision names P3. |
| R3T-M14 | AGENT | 05 E1: `ime list -a -s` before enabling, `ime list -s` after. |
| R3T-m1 | AGENT | 03: P11 / P12 renumbered P7 / P8. |
| R3T-m2 | AGENT | Same fix as R3-m1. |
| R3T-m3 | AGENT | Same fix as R3D-09 (values, stand-ins, `UiDevice` timing) and R3-m2 (edge deleted). |
| R3T-m4 | AGENT | 03: 250 ± 17 ms tagged as N1's from-Notebook open with the 83 ms / ≈300 ms spread noted, the build's value from inside it. |
| R3T-m5 | AGENT | 09 ≡ pane line: once Notebook is ADDed, 03 E15's Reminders cap top 119.5 epx is superseded (third position, checked by 09 E10). |
| R3T-m6 | AGENT | 03 mechanics line: alarms and proximity alerts re-registered at process start, boot and package replace; E6 checks a reminder alarm returns after force-stop and reboot; edges; 09 E8 says force-stop kills `:llm` and the next request restarts it. |
| R3T-m7 | AGENT | 06 E12: action-row tolerance ± 3 % of distances (R7 §1.5.5 P4 camera). |
| R3T-m8 | AGENT | 06: "Attaching..." shows at least 300 ms (approximation, H18 extended); E17 reads it from screenrecord frames. |
| R3T-m9 | AGENT | 03 E2 table and 06 E18 state the slot assignment step (Settings > Start, dump). |
| R3T-m10 | AGENT | 07 E3 sets the clock to 09:05 with E10's commands and restores per RV12. |
| R3T-m11 | AGENT | Same fix as R3D-03 (08 E2 reads diagnostics and has the Unlock step). |
| R3T-m12 | AGENT | 09 E7: locked "how far is the moon" answered first, then "No, I meant the sun" refused. |
| R3T-m13 | AGENT | 03 Scope checklist rows add calendar read, background location, call log and SMS read; build task 12 adds the SMS-read row. |
| R3T-m14 | AGENT | Same fix as R3-m3. |
| R3T-m15 | AGENT | Same fix as R3D-10 (correction note, not a re-ask). |
| R3T-m16 | AGENT | 06 E13: page gone 5.0 ± 0.05 s after "Call dropped" (R7 read 4.97 s); new diagnostics ADD line for `getVoiceMailNumber()`. |

## Test review §5 RV9 table (untagged values; no finding IDs)
| File:line (as reviewed) | Disposition | Applied fix |
|---|---|---|
| phase-03:64, :94 place / person fields | AGENT | R3D-07 cards (H22, H29). |
| phase-03:64 Places page | OWNER, ruled C 2026-09-17 and applied | R3D-02 / R3T-B7. |
| phase-03:65 accent-fill hold | AGENT | 350 ± 17 ms touch → black (R7 §3.1.12, N2; N1 read 500 ms), H23; E15 and P5. |
| phase-03:66 row tap delay | AGENT | 550 ± 17 ms (R7 §3.2.6); E15 and P5. |
| phase-03:60 role-notice page | AGENT | Approximation card (persona, title, "Set as default"), H30. |
| phase-03:65 pane settle | AGENT | R3T-m4. |
| phase-09:44 Notebook row tap | AGENT | Fill ≈117 ms, title cleared ≈183 ms, page ≈617 ms after touch (R7 §3.2.5); E10. |
| phase-05:42 behaviour stand-ins | AGENT | R3D-09. |
| phase-06:37, :47 settings pages | AGENT | R3-m5 (H24). |
| phase-06:39 Details page | AGENT | Approximation (20-epx title, History line-2 style, 74-epx edge), H3. |
| phase-06:50 attach flyout dark fill | AGENT | R7 §3.6.2's dark menu fill (40,40,40) and border (71,76,70), H21. |
| phase-06:36, :44 bars and lock-screen nav variant | AGENT | R3D-04 / R3T-B4 (H23). |
| phase-08:29, task 1 download UI | AGENT | P4 design line on Cortana's Settings page, H4. |
| phase-04:31 offset during drag | AGENT | R3T-B2 (Y13, H16). |

## Notes for the next agent
- Deviations from the given resolutions or a reviewer's literal fix, with reasons:
  - Glance and the bar rule: glance hides Samsung's bars but draws neither W10M bar. W10M's glance was non-interactive, and a drawn Back or Windows key would contradict phase 07's rule that every touch lands on the keyguard. Recorded in phase 01's bar rule and phase 07.
  - R3D-03: the gated personal-data actions in phase 08 are calendar read and contact lookup only. Phase 08's Scope rules out new actions, so no message-reading action exists and messages never reach the model; the ruling's "never reads messages" holds trivially.
  - R3D-07: the place and person cards are tagged P4 design (the given resolution), not approximation (reviewer 1), and the text notes that W10M had these reminders but no footage shows their cards.
  - R3T-B1: volume down and up are sent as two `event send` calls, each ending in `EV_SYN:0:0`, not one combined call.
  - R3T-M11: "no DNS or TCP to any host" would fail on the AOSP image's own background traffic, so the check uses the shell uid's `dumpsys netstats` counters plus the capture's DNS for openstreetmap.org. Not certain: the `--poll --uid` flags, so `dumpsys netstats detail` is named as the fallback.
  - R3-m5: the settings pages use phase 01's Settings hub style, not Cortana's (14,19,13) page look, since they belong to Phone and Messaging.
- New UNVERIFIED items for build start: the keyboard's placement over a drawn nav bar while the system nav bar is hidden (01 X25), hiding system bars over the keyguard for the incoming call (06), `adb emu gsm accept` for an outgoing call (03 E14, falls back to P8), `cmd location set-location-enabled` and `dumpsys netstats --poll --uid` verbs.
