The captured evidence does not support passing the gate. I found seven blocking evidence gaps. I read the code and retained runs, used only host-side checks, and did not run the emulator or write files.

Paths below are relative to `docs/plan/qa/phase-12/` unless otherwise stated.

| Acceptance row | Verdict | Decisive evidence |
|---|---|---|
| E1 | **PASS** | `E1/E1.txt:19,25,34,36`: core-held/finished diagnostics, no wizard, and no marker for the no-marker provision. |
| E2 | **PASS** | `E2/E2.txt:69–73`: exact nineteen-step order, presets at Step 20, nineteen Not-now records; preceding assertions compare every why-line and progress caption. |
| E3 | **PASS** | `E3/E3.txt:117–121,131–155`: nineteen grants accounted for, required diagnostic order, picture oracle, zero assignments, both checklists green. |
| E4 | **PASS** | `E4/E4.txt:29–48`: force-stop and reboot restart at notifications, omit granted Photos, and walk eighteen remaining steps. |
| E5 | **PASS** | `E5/E5.txt:24,31,36,40,42,51–55`: Skip/Done markers, persistence, revoke-later behavior and clearing data. |
| E6 | **PASS** | `E6/E6.txt:23–36,41–51`: denial behavior, app-info fallback and return to the same Photos/microphone step. |
| E7 | **PASS** | `E7/E7.txt:23–36`: revoked row appended once, live grant advances, no duplicate run or Photos step. |
| E8 | **PASS** | `E8/E8.txt:17–39,45–51`: key behavior, Tess return, bar measurements and deferred pin band. |
| E9 | **FAIL** | `E9/E9.txt:13`: `FAIL phase-02-E1 passes unchanged`. The pre-existing failure is separately recorded as L12-1; the seeding assertion also has the coverage gap in B2. |
| E10 | **FAIL** | `E10/E10.txt:42,52,57`: every screenrecord rejected; compositor corroboration also fails at 24.3 and 22.0 ms. |
| E11 | **NOT PROVEN** | `E11/E11.txt:365` checks only `theme_preset=custom` for the effects toggle; `scripts/e11.sh:43–58` omits that toggle on the wizard surface. Required seeding checks are absent. |
| E12 | **PASS** | `E12/E12.txt:26–61,65–79`: reproducible asset hashes, all six APK assets, dimensions/size budgets, positive rebuilds and readable negative slices. |
| E13 | **NOT PROVEN** | `E13/E13.txt:39` proves remembering **Hero**, whereas the wizard-side requirement is remembering **Streaks**. Required process-continuity and seeding assertions are also absent. |
| E14 | **PASS** | `E14/E14.txt:16–36`, `E14_FULL_SCREEN_ALARMS/E14_FULL_SCREEN_ALARMS.txt:16–36`, and `E14_OVERLAY/E14_OVERLAY.txt:16–36`: all three parts for all three required instances. |

Every edge case is assessed below; compound bullets are split where their outcomes differ.

| Edge case | Verdict | Decisive evidence |
|---|---|---|
| Revoke a core grant while a later step shows | **PASS** | `E7/E7.txt:23–26,33`: same run, increased count, revoked step revisited once. |
| Grant the current step externally | **PASS** | `E7/E7.txt:28–30,36`: advances and does not ask again. |
| Grant while the wizard is not showing | **PASS** | `app/src/test/kotlin/app/tileshell/onboarding/SetupWizardTest.kt:217`, corroborated by E4’s rebuilt run excluding Photos. |
| Force-stop mid-run | **PASS** | `E4/E4.txt:29–37`. |
| Reboot mid-run | **PASS** | `E4/E4.txt:39–48`. |
| Update install mid-run | **PASS** | `EDGE_INSTALL_R/EDGE_INSTALL_R.txt:19–24`: successful install, correct rebuilt count, no marker, Photos omitted. |
| Low-memory kill with permission dialog open | **NOT PROVEN** | `EDGE_LMK/EDGE_LMK.txt:20`: PID remains `28323 / 28323`; no process death occurred. |
| Rotation without recreation | **PASS** | `EDGE_ROTATION/EDGE_ROTATION.txt:19–22`: same step/process and readable ring without recreation. |
| Runtime dialog dismissed by Back | **PASS** | `EDGE_DISMISS/EDGE_DISMISS.txt:20–23`: returned to Start, step stays, Not now advances. |
| Runtime dialog dismissed by outside tap | **NOT PROVEN** | `EDGE_DISMISS/EDGE_DISMISS.txt:32–33`: dialog remains open; driver substitutes Back. |
| Two rapid action taps | **PASS** | `EDGE_DOUBLE_TAP/EDGE_DOUBLE_TAP.txt:20–21`: one dialog and unchanged PID. |
| Partial Photos grant | **PASS** | `EDGE_PARTIAL_PHOTOS/EDGE_PARTIAL_PHOTOS.txt:20–23` and saved checklist dump: advance, Partial diagnostic and checklist state. |
| FINE-only background location | **PASS** | `EDGE_PARTIAL_BGLOC/EDGE_PARTIAL_BGLOC.txt:19–22`: stays Partial; Not now advances. |
| Read-only calendar | **PASS** | `EDGE_PARTIAL_CALENDAR/EDGE_PARTIAL_CALENDAR.txt:20–23`. |
| Both location permissions, device location off | **PASS** | `EDGE_LOCATION_OFF/EDGE_LOCATION_OFF.txt:14–20`: does not independently summon wizard; remains Partial inside a run. |
| Keyboard picker dismissed | **PASS** | `EDGE_KB_DISMISS/EDGE_KB_DISMISS.txt:13–16`. |
| Keyboard deselected by force-stop after finishing | **PASS** | `E1/E1.txt:22–28`: no wizard; missing checklist row remains available. |
| Home opened “Just once”; another launcher chosen | **NOT PROVEN** | `scripts/edge.sh:240,268`: explicit activity launches replace the chooser route and reopen Start before checking retention. |
| Tess session over the wizard | **PASS** | `E8/E8.txt:28–29`. |
| Secondary-pin band pending | **PASS** | `E8/E8.txt:45–51`: hidden until Start becomes visible. |
| Phase-14 pod-bay request pending | **NOT PROVEN** | Phase doc’s explicit future dependency; no implemented phase-14 event is exercised. Deferred scope, not an additional blocker here. |
| Burst cannot open under wizard | **PASS** | `app/src/main/kotlin/app/tileshell/StartActivity.kt:222`: wizard branch excludes `StartPivot` and its burst layer. |
| Existing Light theme/background | **PASS** | `EDGE_LIGHT/EDGE_LIGHT.txt:17` and `EDGE_LIGHT/wizard-light.png`: white wizard background. |
| Previously customized items | **PASS** | `EDGE_CUSTOM_BEFORE/EDGE_CUSTOM_BEFORE.txt:15–16`: Custom selected; Done preserves items. |
| Preset under battery saver, then saver off | **PASS** | `EDGE_BATTERY_SAVER/EDGE_BATTERY_SAVER.txt:13–19`: saver verified, effects preference retained, acrylic follows saver override and restoration. |
| Undecodable preset picture | **PASS** | `EDGE_NO_PICTURE/EDGE_NO_PICTURE.txt:12–17`: fault asset removal verified, fallback diagnostic/background verified, normal APK restored. |
| Custom snapshot restores picture, values and grant | **PASS** | `E11/E11.txt` snapshot assertions and `:410–420`: A retained/restored/rendered, then released while B remains retained. |
| Choose picture after a preset makes it Custom | **NOT PROVEN** | `scripts/e11.sh:215,225`: B is chosen after restoring Custom; this does not test conversion from a selected preset. |
| Remove picture after a preset makes it Custom | **NOT PROVEN** | No corresponding action/assertion in `scripts/e11.sh`; the Remove-picture control is never exercised. |
| Effects toggle after preset preserves every other key | **NOT PROVEN** | `E11/E11.txt:365`: only Custom identity checked; no unchanged-key comparison. |
| Work/private profile present | **PASS** | `EDGE_PROFILE/EDGE_PROFILE.txt:13,18`: profile user 10 recorded; full step list equals E2. |
| Keyguard | **PASS** | `EDGE_KEYGUARD/EDGE_KEYGUARD.txt:15–18`: system keyguard shown, wizard absent, wizard returns after unlock. |
| Later core row added to finished install | **PASS** | E5’s revoked-later case and all E14 finished-install subrows, as the doc’s designated proxy requires. |

The blocking findings are:

1. **BLOCKING B1 — E10 lacks the required motion corroboration.**  
   [e10.sh:63](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e10.sh:63) turns rejected screenrecords into RECORDs; [e10.sh:97](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e10.sh:97) excludes the first compositor gap from its threshold check. A product that logs regular frame production while presenting a visibly stalled transition escapes the primary assertions. The captured first gaps are approximately 50 ms, and even the remaining gaps fail twice. Restore the required corroboration assertion and obtain a qualifying capture after resolving the presentation problem, or obtain an explicit acceptance-criterion change. README disclosure does not make this a pass.

2. **BLOCKING B2 — Required seeding assertions do not cover the relevant process lifetimes.**  
   [presets_lib.sh:105](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/presets_lib.sh:105), [e11.sh:61](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e11.sh:61), and [e13.sh:100](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e13.sh:100) restore layouts without asserting zero subsequent `assignSlotOnce … -> assigned`. [e9.sh:128](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e9.sh:128) checks the final process’s ring after children have restarted the app. An assignment in an earlier process can disappear before that assertion. The saved phase-02 `e1_diag.txt` is useful corroboration, but the driver does not check it. Capture and validate each restore’s ring before another restart; assert readable headers and zero assignments in each interval.

3. **BLOCKING B3 — E11’s Custom checks omit required mutations.**  
   [e11.sh:56](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e11.sh:56) finishes wizard-side Custom testing after changing accent. [e11.sh:99](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e11.sh:99) checks only Custom identity after toggling effects in Settings. A toggle that marks Custom but does nothing—or resets another item—passes. A broken Remove-picture handler also passes. Exercise the effects toggle on both surfaces, assert its changed value/selection and every other unchanged item, and exercise Choose/Remove directly from a selected preset.

4. **BLOCKING B4 — E13 substitutes a weaker wizard-side variant sequence and omits PID checks.**  
   [e13.sh:65](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e13.sh:65) tests Default→original while Hero was already selected. The Streaks trial ends at Done without Default→original→Hero. A wizard-only regression resetting remembered Streaks to Hero passes. No E13 PID assertion establishes the required live captures either. Complete the prescribed sequence on both surfaces and assert unchanged Start PID around each live capture.

5. **BLOCKING B5 — The LMK case never experiences process death.**  
   [edge.sh:53](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:53) records the unchanged PID, then accepts any nonempty wizard step as “re-derives.” A product relying entirely on the surviving in-memory permission callback passes. Require evidence that the original process died, a new PID after returning, no finished marker, and the exact live-state-derived remaining steps.

6. **BLOCKING B6 — Outside-tap dismissal is replaced by Back.**  
   [edge.sh:94](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:94) detects failure to dismiss, sends Back, then emits outside-case PASS lines. A defect specific to an actual outside cancellation would be missed. Keep this case NOT PROVEN until exercised on a supporting configuration, or explicitly resolve its applicability in the acceptance criteria; report the fallback solely as Back coverage.

7. **BLOCKING B7 — The “Just once” case does not test its required route or uninterrupted return.**  
   [edge.sh:240](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:240) uses an explicit activity launch. [edge.sh:268](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:268) launches it again after selecting another launcher. A product that incorrectly leaves/finishes the wizard on that return can pass after reopening. Enter through the actual chooser’s Just-once action and inspect the immediate return from choosing the other launcher, before another launch or Home event.

Additional findings and evidence-integrity conclusions:

- **NOTE — E9’s existing product failure is accurately disclosed.** `E9/E9.txt:13`, the retained baseline run on `97c19ef4`, and `docs/plan/INDEX.md:106` agree about the edit-mode failure. I retain **FAIL** for the row, but do not count that pre-existing L12-1 issue as an additional phase-12 blocker under the recorded scope ruling. Preserve that distinction in the ledger; do not relabel E9 wholly passing.

- **NOTE — Earlier-build evidence remains usable for unaffected behavior.** `README.md:17–22` matches the row-header APK identifiers. The app diff `907a8725..0c8fe8f4` is limited to motion settling, legacy-Custom interpretation and fail-safe hardening. It supports retaining the unaffected earlier rows. It does not repair E10 corroboration or fill omitted checks. Jeremy’s `INDEX.md:170` ruling does not require a whole-gate rerun.

- **NOTE — Most README readings are honest adaptations.** `README.md:65–80` correctly explains checked/selected semantics, six named presets excluding Custom, E5’s temporary assistant role, E8’s usable key-test state and E9’s replayed rows. The lens disclosure at `:70` is also honest: the idle capture measures glow/mid/rim, **not an iris**. Describe that result accordingly. E10’s disclosure at `:82` is candid about the gap, but cannot satisfy the missing corroboration.

- **NOTE — Retained-run explanations mostly match their captures.** The E1 provision path; E2 appops/quoting; E3 confirmation, SPA, assistant-page and role-granted-row handling; E14 task-front issue; E11 ring parsing, snapshot fixture, default comparison, grant reader, indentation, SIGPIPE and interrupted runs; E12 ring parsing; E13 fixture disruption; and the retained edge-driver corrections are consistent with their logs and subsequent scripts. The important exceptions are E9’s genuine baseline failure and E10’s unresolved presentation/corroboration failures. E10 run 1 also contains the genuine 234-ms product issue fixed by `e5b9cc7d`; run 3’s empty container-layer read is a harness fault. Clarify `README.md:61` so retained runs are not collectively characterized as driver/harness faults.

- **NOTE — No demonstrated empty-read pass in the retained grant result, but the helper is unsafe.** [grants.sh:35](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/grants.sh:35) exits successfully on malformed/empty XML. Current A/B artifacts contain real, distinct URIs, and the same final read positively retains B, so A’s absence is not vacuous here. Make malformed/unavailable reads fail and assert nonempty fixture URIs before membership tests.

- **NOTE — Preserve reproducible driver/test provenance.** Current row headers name driver/harness blobs and matching installed APKs; several historical E-row driver hashes are not retrievable as local Git objects. Archive historical driver sources and a manifest covering sourced helpers. Also, `app/build/test-results/testDebugUnitTest/TEST-app.tileshell.prefs.ThemePresetsTest.xml:2` currently records one B1 failure timestamped before `0c8fe8f4`. That does not establish a defect in current code, but it does not substantiate README’s final 49/49 claim. Archive the successful final result separately from mutation/earlier results.

- **NOTE — Phone handoff is phone-only but incomplete for P1.** [NEEDS-HUMAN.md:21](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/NEEDS-HUMAN.md:21) substitutes an existing-install/missing-permissions path for the specified fresh route and omits the Home route, device-location state and requested screenshots. At `:33`, wizard/theme diagnostics alone cannot establish every resumed One UI page. Add phone-side page observations/screenshots, location state, fallback details, and an explicit resolution of the fresh-install deviation. P2 asks for the right persistence checks; H1–H9 cover the intended judgments. Keep delivery, capture and paste-back entirely on the phone.

GATE: FAIL (7 blocking)
300,585
The captured evidence does not support passing the gate. I found seven blocking evidence gaps. I read the code and retained runs, used only host-side checks, and did not run the emulator or write files.

Paths below are relative to `docs/plan/qa/phase-12/` unless otherwise stated.

| Acceptance row | Verdict | Decisive evidence |
|---|---|---|
| E1 | **PASS** | `E1/E1.txt:19,25,34,36`: core-held/finished diagnostics, no wizard, and no marker for the no-marker provision. |
| E2 | **PASS** | `E2/E2.txt:69–73`: exact nineteen-step order, presets at Step 20, nineteen Not-now records; preceding assertions compare every why-line and progress caption. |
| E3 | **PASS** | `E3/E3.txt:117–121,131–155`: nineteen grants accounted for, required diagnostic order, picture oracle, zero assignments, both checklists green. |
| E4 | **PASS** | `E4/E4.txt:29–48`: force-stop and reboot restart at notifications, omit granted Photos, and walk eighteen remaining steps. |
| E5 | **PASS** | `E5/E5.txt:24,31,36,40,42,51–55`: Skip/Done markers, persistence, revoke-later behavior and clearing data. |
| E6 | **PASS** | `E6/E6.txt:23–36,41–51`: denial behavior, app-info fallback and return to the same Photos/microphone step. |
| E7 | **PASS** | `E7/E7.txt:23–36`: revoked row appended once, live grant advances, no duplicate run or Photos step. |
| E8 | **PASS** | `E8/E8.txt:17–39,45–51`: key behavior, Tess return, bar measurements and deferred pin band. |
| E9 | **FAIL** | `E9/E9.txt:13`: `FAIL phase-02-E1 passes unchanged`. The pre-existing failure is separately recorded as L12-1; the seeding assertion also has the coverage gap in B2. |
| E10 | **FAIL** | `E10/E10.txt:42,52,57`: every screenrecord rejected; compositor corroboration also fails at 24.3 and 22.0 ms. |
| E11 | **NOT PROVEN** | `E11/E11.txt:365` checks only `theme_preset=custom` for the effects toggle; `scripts/e11.sh:43–58` omits that toggle on the wizard surface. Required seeding checks are absent. |
| E12 | **PASS** | `E12/E12.txt:26–61,65–79`: reproducible asset hashes, all six APK assets, dimensions/size budgets, positive rebuilds and readable negative slices. |
| E13 | **NOT PROVEN** | `E13/E13.txt:39` proves remembering **Hero**, whereas the wizard-side requirement is remembering **Streaks**. Required process-continuity and seeding assertions are also absent. |
| E14 | **PASS** | `E14/E14.txt:16–36`, `E14_FULL_SCREEN_ALARMS/E14_FULL_SCREEN_ALARMS.txt:16–36`, and `E14_OVERLAY/E14_OVERLAY.txt:16–36`: all three parts for all three required instances. |

Every edge case is assessed below; compound bullets are split where their outcomes differ.

| Edge case | Verdict | Decisive evidence |
|---|---|---|
| Revoke a core grant while a later step shows | **PASS** | `E7/E7.txt:23–26,33`: same run, increased count, revoked step revisited once. |
| Grant the current step externally | **PASS** | `E7/E7.txt:28–30,36`: advances and does not ask again. |
| Grant while the wizard is not showing | **PASS** | `app/src/test/kotlin/app/tileshell/onboarding/SetupWizardTest.kt:217`, corroborated by E4’s rebuilt run excluding Photos. |
| Force-stop mid-run | **PASS** | `E4/E4.txt:29–37`. |
| Reboot mid-run | **PASS** | `E4/E4.txt:39–48`. |
| Update install mid-run | **PASS** | `EDGE_INSTALL_R/EDGE_INSTALL_R.txt:19–24`: successful install, correct rebuilt count, no marker, Photos omitted. |
| Low-memory kill with permission dialog open | **NOT PROVEN** | `EDGE_LMK/EDGE_LMK.txt:20`: PID remains `28323 / 28323`; no process death occurred. |
| Rotation without recreation | **PASS** | `EDGE_ROTATION/EDGE_ROTATION.txt:19–22`: same step/process and readable ring without recreation. |
| Runtime dialog dismissed by Back | **PASS** | `EDGE_DISMISS/EDGE_DISMISS.txt:20–23`: returned to Start, step stays, Not now advances. |
| Runtime dialog dismissed by outside tap | **NOT PROVEN** | `EDGE_DISMISS/EDGE_DISMISS.txt:32–33`: dialog remains open; driver substitutes Back. |
| Two rapid action taps | **PASS** | `EDGE_DOUBLE_TAP/EDGE_DOUBLE_TAP.txt:20–21`: one dialog and unchanged PID. |
| Partial Photos grant | **PASS** | `EDGE_PARTIAL_PHOTOS/EDGE_PARTIAL_PHOTOS.txt:20–23` and saved checklist dump: advance, Partial diagnostic and checklist state. |
| FINE-only background location | **PASS** | `EDGE_PARTIAL_BGLOC/EDGE_PARTIAL_BGLOC.txt:19–22`: stays Partial; Not now advances. |
| Read-only calendar | **PASS** | `EDGE_PARTIAL_CALENDAR/EDGE_PARTIAL_CALENDAR.txt:20–23`. |
| Both location permissions, device location off | **PASS** | `EDGE_LOCATION_OFF/EDGE_LOCATION_OFF.txt:14–20`: does not independently summon wizard; remains Partial inside a run. |
| Keyboard picker dismissed | **PASS** | `EDGE_KB_DISMISS/EDGE_KB_DISMISS.txt:13–16`. |
| Keyboard deselected by force-stop after finishing | **PASS** | `E1/E1.txt:22–28`: no wizard; missing checklist row remains available. |
| Home opened “Just once”; another launcher chosen | **NOT PROVEN** | `scripts/edge.sh:240,268`: explicit activity launches replace the chooser route and reopen Start before checking retention. |
| Tess session over the wizard | **PASS** | `E8/E8.txt:28–29`. |
| Secondary-pin band pending | **PASS** | `E8/E8.txt:45–51`: hidden until Start becomes visible. |
| Phase-14 pod-bay request pending | **NOT PROVEN** | Phase doc’s explicit future dependency; no implemented phase-14 event is exercised. Deferred scope, not an additional blocker here. |
| Burst cannot open under wizard | **PASS** | `app/src/main/kotlin/app/tileshell/StartActivity.kt:222`: wizard branch excludes `StartPivot` and its burst layer. |
| Existing Light theme/background | **PASS** | `EDGE_LIGHT/EDGE_LIGHT.txt:17` and `EDGE_LIGHT/wizard-light.png`: white wizard background. |
| Previously customized items | **PASS** | `EDGE_CUSTOM_BEFORE/EDGE_CUSTOM_BEFORE.txt:15–16`: Custom selected; Done preserves items. |
| Preset under battery saver, then saver off | **PASS** | `EDGE_BATTERY_SAVER/EDGE_BATTERY_SAVER.txt:13–19`: saver verified, effects preference retained, acrylic follows saver override and restoration. |
| Undecodable preset picture | **PASS** | `EDGE_NO_PICTURE/EDGE_NO_PICTURE.txt:12–17`: fault asset removal verified, fallback diagnostic/background verified, normal APK restored. |
| Custom snapshot restores picture, values and grant | **PASS** | `E11/E11.txt` snapshot assertions and `:410–420`: A retained/restored/rendered, then released while B remains retained. |
| Choose picture after a preset makes it Custom | **NOT PROVEN** | `scripts/e11.sh:215,225`: B is chosen after restoring Custom; this does not test conversion from a selected preset. |
| Remove picture after a preset makes it Custom | **NOT PROVEN** | No corresponding action/assertion in `scripts/e11.sh`; the Remove-picture control is never exercised. |
| Effects toggle after preset preserves every other key | **NOT PROVEN** | `E11/E11.txt:365`: only Custom identity checked; no unchanged-key comparison. |
| Work/private profile present | **PASS** | `EDGE_PROFILE/EDGE_PROFILE.txt:13,18`: profile user 10 recorded; full step list equals E2. |
| Keyguard | **PASS** | `EDGE_KEYGUARD/EDGE_KEYGUARD.txt:15–18`: system keyguard shown, wizard absent, wizard returns after unlock. |
| Later core row added to finished install | **PASS** | E5’s revoked-later case and all E14 finished-install subrows, as the doc’s designated proxy requires. |

The blocking findings are:

1. **BLOCKING B1 — E10 lacks the required motion corroboration.**  
   [e10.sh:63](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e10.sh:63) turns rejected screenrecords into RECORDs; [e10.sh:97](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e10.sh:97) excludes the first compositor gap from its threshold check. A product that logs regular frame production while presenting a visibly stalled transition escapes the primary assertions. The captured first gaps are approximately 50 ms, and even the remaining gaps fail twice. Restore the required corroboration assertion and obtain a qualifying capture after resolving the presentation problem, or obtain an explicit acceptance-criterion change. README disclosure does not make this a pass.

2. **BLOCKING B2 — Required seeding assertions do not cover the relevant process lifetimes.**  
   [presets_lib.sh:105](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/presets_lib.sh:105), [e11.sh:61](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e11.sh:61), and [e13.sh:100](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e13.sh:100) restore layouts without asserting zero subsequent `assignSlotOnce … -> assigned`. [e9.sh:128](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e9.sh:128) checks the final process’s ring after children have restarted the app. An assignment in an earlier process can disappear before that assertion. The saved phase-02 `e1_diag.txt` is useful corroboration, but the driver does not check it. Capture and validate each restore’s ring before another restart; assert readable headers and zero assignments in each interval.

3. **BLOCKING B3 — E11’s Custom checks omit required mutations.**  
   [e11.sh:56](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e11.sh:56) finishes wizard-side Custom testing after changing accent. [e11.sh:99](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e11.sh:99) checks only Custom identity after toggling effects in Settings. A toggle that marks Custom but does nothing—or resets another item—passes. A broken Remove-picture handler also passes. Exercise the effects toggle on both surfaces, assert its changed value/selection and every other unchanged item, and exercise Choose/Remove directly from a selected preset.

4. **BLOCKING B4 — E13 substitutes a weaker wizard-side variant sequence and omits PID checks.**  
   [e13.sh:65](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/e13.sh:65) tests Default→original while Hero was already selected. The Streaks trial ends at Done without Default→original→Hero. A wizard-only regression resetting remembered Streaks to Hero passes. No E13 PID assertion establishes the required live captures either. Complete the prescribed sequence on both surfaces and assert unchanged Start PID around each live capture.

5. **BLOCKING B5 — The LMK case never experiences process death.**  
   [edge.sh:53](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:53) records the unchanged PID, then accepts any nonempty wizard step as “re-derives.” A product relying entirely on the surviving in-memory permission callback passes. Require evidence that the original process died, a new PID after returning, no finished marker, and the exact live-state-derived remaining steps.

6. **BLOCKING B6 — Outside-tap dismissal is replaced by Back.**  
   [edge.sh:94](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:94) detects failure to dismiss, sends Back, then emits outside-case PASS lines. A defect specific to an actual outside cancellation would be missed. Keep this case NOT PROVEN until exercised on a supporting configuration, or explicitly resolve its applicability in the acceptance criteria; report the fallback solely as Back coverage.

7. **BLOCKING B7 — The “Just once” case does not test its required route or uninterrupted return.**  
   [edge.sh:240](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:240) uses an explicit activity launch. [edge.sh:268](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/edge.sh:268) launches it again after selecting another launcher. A product that incorrectly leaves/finishes the wizard on that return can pass after reopening. Enter through the actual chooser’s Just-once action and inspect the immediate return from choosing the other launcher, before another launch or Home event.

Additional findings and evidence-integrity conclusions:

- **NOTE — E9’s existing product failure is accurately disclosed.** `E9/E9.txt:13`, the retained baseline run on `97c19ef4`, and `docs/plan/INDEX.md:106` agree about the edit-mode failure. I retain **FAIL** for the row, but do not count that pre-existing L12-1 issue as an additional phase-12 blocker under the recorded scope ruling. Preserve that distinction in the ledger; do not relabel E9 wholly passing.

- **NOTE — Earlier-build evidence remains usable for unaffected behavior.** `README.md:17–22` matches the row-header APK identifiers. The app diff `907a8725..0c8fe8f4` is limited to motion settling, legacy-Custom interpretation and fail-safe hardening. It supports retaining the unaffected earlier rows. It does not repair E10 corroboration or fill omitted checks. Jeremy’s `INDEX.md:170` ruling does not require a whole-gate rerun.

- **NOTE — Most README readings are honest adaptations.** `README.md:65–80` correctly explains checked/selected semantics, six named presets excluding Custom, E5’s temporary assistant role, E8’s usable key-test state and E9’s replayed rows. The lens disclosure at `:70` is also honest: the idle capture measures glow/mid/rim, **not an iris**. Describe that result accordingly. E10’s disclosure at `:82` is candid about the gap, but cannot satisfy the missing corroboration.

- **NOTE — Retained-run explanations mostly match their captures.** The E1 provision path; E2 appops/quoting; E3 confirmation, SPA, assistant-page and role-granted-row handling; E14 task-front issue; E11 ring parsing, snapshot fixture, default comparison, grant reader, indentation, SIGPIPE and interrupted runs; E12 ring parsing; E13 fixture disruption; and the retained edge-driver corrections are consistent with their logs and subsequent scripts. The important exceptions are E9’s genuine baseline failure and E10’s unresolved presentation/corroboration failures. E10 run 1 also contains the genuine 234-ms product issue fixed by `e5b9cc7d`; run 3’s empty container-layer read is a harness fault. Clarify `README.md:61` so retained runs are not collectively characterized as driver/harness faults.

- **NOTE — No demonstrated empty-read pass in the retained grant result, but the helper is unsafe.** [grants.sh:35](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/scripts/grants.sh:35) exits successfully on malformed/empty XML. Current A/B artifacts contain real, distinct URIs, and the same final read positively retains B, so A’s absence is not vacuous here. Make malformed/unavailable reads fail and assert nonempty fixture URIs before membership tests.

- **NOTE — Preserve reproducible driver/test provenance.** Current row headers name driver/harness blobs and matching installed APKs; several historical E-row driver hashes are not retrievable as local Git objects. Archive historical driver sources and a manifest covering sourced helpers. Also, `app/build/test-results/testDebugUnitTest/TEST-app.tileshell.prefs.ThemePresetsTest.xml:2` currently records one B1 failure timestamped before `0c8fe8f4`. That does not establish a defect in current code, but it does not substantiate README’s final 49/49 claim. Archive the successful final result separately from mutation/earlier results.

- **NOTE — Phone handoff is phone-only but incomplete for P1.** [NEEDS-HUMAN.md:21](/home/jeremyking/projects/metro-launcher/docs/plan/qa/phase-12/NEEDS-HUMAN.md:21) substitutes an existing-install/missing-permissions path for the specified fresh route and omits the Home route, device-location state and requested screenshots. At `:33`, wizard/theme diagnostics alone cannot establish every resumed One UI page. Add phone-side page observations/screenshots, location state, fallback details, and an explicit resolution of the fresh-install deviation. P2 asks for the right persistence checks; H1–H9 cover the intended judgments. Keep delivery, capture and paste-back entirely on the phone.

GATE: FAIL (7 blocking)
