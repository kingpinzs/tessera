# Phase 14 QA — the pod bay: evidence index

Phase doc: `docs/plan/phase-14-pod-bay.md` (FINAL 2026-09-29). Device: AOSP AVD `tileshell_fhd`, **emulator-5554 only**
(every driver exports `ANDROID_SERIAL=emulator-5554`; emulator-5556 and the others on this host belong to other
projects). Drivers: `scripts/` on phase 03's floor (`scripts/lib.sh` and `scripts/within.py` are symlinks to phase 03's).

## The AVD's launch (since 2026-09-29 22:03)

```
QEMU_AUDIO_DRV=none ~/Android/Sdk/emulator/emulator -avd tileshell_fhd -no-snapshot-load
```

- **`QEMU_AUDIO_DRV=none`, no `-allow-host-audio`:** the emulator opens no connection to the host's PipeWire at all;
  its audio mixer runs on the `none` backend's timer, which is what `streamAudio` captures from. The guest keeps its
  microphone and speaker (hw.audioInput / hw.audioOutput stay yes).
- **No `-port`:** an AVD launched with an explicit `-port` starts no gRPC server (seen on this host: run 2's launch,
  `SPIKE-audio/emulator-run2-port-flag-no-grpc.log`, and the other projects' 5570 / 5572). Without it the emulator takes
  the first free console port (5554) and starts gRPC on 8554 with token auth, advertised in
  `/run/user/1000/avd/running/pid_<pid>.ini` — the file `emu_audio.py` reads.
- The live emulator log is outside the repo (the process writes it); `SPIKE-audio/run5-emulator-log.txt` is its
  startup / gRPC / audio excerpt.

Why it was relaunched: the AVD running before (pid 3309523, launched 2026-09-27 with `-allow-host-audio`) had lost its
PulseAudio connection in both directions (`pulseaudio: pa_simple_write failed / Reason: Connection terminated`, and
the same for read — `SPIKE-audio/run1-emulator-log-audio.txt`), most likely at the PipeWire restart of 2026-09-29. With
its output backend dead the mixer produced nothing, and `streamAudio` delivered zero packets in every format
(`run1-format-probe-*.out`). It was stopped with `adb -s emulator-5554 emu kill` (its console) and relaunched as above.

## Audio spike (build start, before any voice row) — PASS 20/0 (run 5)

Driver `scripts/spike_audio.sh`; client `qa/phase-03/scripts/emu_audio.py`; switch `AUDIO_ROUTE=emu` in phase 03's
`speak.sh` and `lib.sh` `say` (default path unchanged). The host's audio was never touched: no `audio.sh setup` / `say`
/ `record`, no `pactl` / `paplay` / `parecord`, no `hostmicon`; `audio.sh rms` is a file computation.

| step | result | evidence |
|---|---|---|
| (1) endpoint only from the discovery file with `port.serial=5554`; `ANDROID_SERIAL=emulator-5556` refused (rc 2) before any connection | PASS | `SPIKE-audio/run5-SPIKE-audio.txt` |
| (2) `getMicrophoneState` → `realAudioEnabled=false` | PASS | same |
| (3) Tess listening, `injectAudio` of `pod_bay_doors` → `asr: final … open="OPEN THE POD BAY DOORS"`, `heard=true`, no "no speech in the capture" | PASS | same, `run5-speech-slice.txt` |
| (4) reply captured through `streamAudio`, RMS over the 28-s window −28.53 dBFS > −40 | PASS | `run5-reply.wav`, `run5-record.out` |

The runs, all kept:

| run | result | what it showed |
|---|---|---|
| 1 | 18/1 | (1)-(3) pass; `streamAudio` 0 packets — the old AVD's dead PulseAudio link (above) |
| 2 | 18/1 | after the relaunch: 1430 packets but −97.30 dBFS — the AVD's media volume is 0 (`run2-timestamp-probe.out`: packets are Unix-µs stamped and arrive exactly while Tess speaks, all ≤ −80 dB) |
| 3 | 19/1 | `cmd media_session volume --set 10` is a silent no-op on this image (volume read back 0); −116.08 dBFS |
| 4 | 19/1 | `cmd audio set-volume 3 10` works: −42.97 dBFS over the window (5.8 s of speech in 28 s) |
| 5 | 20/0 | full scale (`set-volume 3 15`) for the audible step, restored to 0 after: −28.53 dBFS |

**Media volume rule for every audible step (from runs 2-5):** phase 03's `j5.sh` sets the AVD's media volume to 0 and
never restores it; Tess's `USAGE_ASSISTANT` voice follows the media stream on this image. A row that checks a reply's
RMS saves the volume, sets it with `adb shell cmd audio set-volume 3 15`, and restores it and asserts the restore
(phase 15's e9.sh / e26.sh form, RV12). Nothing reaches the desktop at any volume (the `none` backend).

Consequence for the doc's fallback clause: the spike passed, so the voice rows (E6, E8, E9, E13's phase 03 re-runs)
stay spoken on `AUDIO_ROUTE=emu` — no typed-only fallback.

## Build-start checks (`scripts/buildstart_checks.sh`, `BUILDSTART/`) — 7/0, 4 recorded, on the build before phase 14's code

- **Page absence (T14-8):** an off-screen page the pager has composed is absent from the dump — on Start no `app_list`,
  on the app list no `start_page` (4 assertions). Every "no `pod_bay`" / "no `app_list`" assertion also reads the
  `[start] page=<name>` line.
- **The session's HOME intent (Route Decision):** with Tess open over a resumed Start, her drawn Windows key (`goHome()`,
  the HOME intent) produced `[start] home: page 0, scrolled to top` 29 ms after `[cortana] session hidden` — the intent
  **reaches `onNewIntent`** while Start is resumed beneath the session (`topResumedActivity` was StartActivity). The
  focus-back `PodBayCheck` is there either way.

## Build readings (INDEX Change Log 2026-09-29, PHASE 14 BUILD)

- Agenda query from local start of today (the all-day probe: `BUILD-NOTES/cal_probe.sh`, `cal_probe.out`).
- Agenda row time text is `ReminderText.time` — "9:30 AM" on this 12-hour AVD.
- E3's all-day fixture uses the UTC midnight of the device's LOCAL date (phase 01's `edge_calendar.sh` DAY0 is the UTC
  date's midnight — tomorrow, in the evening behind UTC). Its `content insert` titles with spaces need escaping for the
  device shell (`title:s:QA\ all\ day`); unescaped, the insert fails with a usage error (seen in the probe's first run).
- `acrylic:pod_bay` sits inside the scroll container so the accessibility tree keeps it with the page's bounds
  (`BUILD-NOTES/smoke1/podbay2.xml`: `[0,0][1080,2196]`, the same as `pod_bay`).

## Harness (build task 6)

**The three Start helpers** now do the Decision's fix — `KEYCODE_HOME`, then `KEYCODE_BACK` while the dump shows
`app_list` or `pod_bay`, then assert `start_page` alone (a failure is a FAIL verdict, so it fails the row):
phase 02's `gestures.sh` `ensure_start` (it bumps the caller's `FAIL` counter), phase 03's `lib.sh` `ensure_start`
(`_verdict FAIL`), phase 11's `q.sh` `ensure_start_page` (`_verdict FAIL`).

**Re-grep** (`scripts/regrep.py`, every `input swipe` in the qa tree, literal and computed operands; before:
`BUILD-NOTES/regrep-before.txt` taken after the two helper edits, plus `git show` of the pre-fix files; after:
`BUILD-NOTES/regrep-after.txt`):

| hit | what it is | action |
|---|---|---|
| `phase-02/scripts/gestures.sh:44` (pre-fix) | `ensure_start`'s right swipe to page 0 | replaced by the fix |
| `phase-11/scripts/q.sh:82` (pre-fix) | `ensure_start_page`'s Back-then-right-swipe | replaced by the fix |
| `phase-13/scripts/l13_2_row.sh:43` | `pivot_moves_to_start`: app list → Start | still correct (it starts on the app list) |
| `phase-13/scripts/p13.sh:70` `to_start` (computed) | a right swipe at mid height | every caller runs it after `to_app_list` or from the app list (bs_step, e8-e10, edge, l13_3_race, b1_probe) — app list → Start, still correct; **a call made from Start would now open the pod bay**. Phase 13's rows are not re-run in phase 14 (E13 covers 01 / 02 / 03), so this is flagged for phase 13's next run, not changed |
| `phase-13/scripts/l13_2_row.sh:88`, `:169` (computed y) | rightward swipes on the app list's scrim / jump grid, asserting the page does NOT move | not a way to reach Start; unchanged |
| `phase-01/scripts/j2.sh:63` (computed) | a drag on Music's scrubber | not the pager |
| every other computed hit | a hold (`x y x y <ms>`), a vertical scroll, or a leftward swipe | not a right swipe |

**Greps of Start's home line** (renamed `home: page 0` → `home: page START`, T11-9 / T14-5): `phase-02/scripts/regress.sh:120`
and `phase-13/L13-3-investigation/back_interrupt.sh:29`, both updated.

**Launches (C-6):** after any launch, `am force-stop app.tileshell` + Home before the next grid assertion (the row
drivers' `c6`). **`baseline_layout-nomusic.json`** (E4): phase 02's baseline with `slots.MUSIC` removed and
`slot:music:v1` kept in `addedOnce`; the AVD has three `APP_MUSIC` handlers and no preferred one, so the slot is
unassigned (`cmd package query-activities … APP_MUSIC`: gramophone, auxio, app.tileshell/.music.MusicActivity).
The CALENDAR category has exactly one (`com.android.calendar/.AllInOneActivity`), E3's precondition.

## E12 and Android's immersive edge — RULED (a), 2026-09-30 (INDEX Change Log)

Found by E12 run 1 (`E12-run1-no-guard/`): with the AVD's gestural overlay on, a swipe from x = 2 on Start opened the
pod bay instead of firing Android's Back. The probes (`BUILD-NOTES/e12-probe/`) place the cause in the platform, not
the pod bay:

- probe 1: the same injected edge swipe over DeskClock (system bars showing) IS Back (x = 2, 10, 30): injection and
  gesture mode work (`navigation_mode=2`, `systemGestures` left inset 84 px).
- probe 2: over the shell's own SettingsActivity (system bars hidden, phase 01's bar rule) the edge swipe does NOT pop
  the page — every shell screen is an immersive window (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
- probe 3: on Start the first edge swipe reveals the bars (`visible=true` 0.5 s after) and Android still hands the drag
  to the app — the pager opened the pod bay; a second swipe while the bars showed was Back (`closed by back`).

**Fixed (ours):** the Decision's "never the pod bay" half — a horizontal pan that starts inside Android's left
system-gesture inset is not the pager's (`StartActivity.edgeGuard`, logged `[start] edge pan from x=… inside the gesture
inset (84 px): not the pager's`); taps and vertical scrolls from that strip are untouched; no exclusion rects.
E12 run 2 (`E12/`): 10 passed, 1 failed, 2 recorded — no pod bay, no `page=POD_BAY`, the guard's line, bars revealed.

**Open (Android's):** E12's clause "Android's Back fires: `topResumedActivity` is DeskClock" on the FIRST edge swipe
cannot hold while phase 01's bar rule hides the bars. probe 4 (guard build): the first edge swipe reveals the bars and
moves nothing; a second one 0.4 s later is Android's Back and Start's Back-history rule resumes DeskClock
(`[launch] tile=back`). The question to Jeremy: read E12's Back clause as "first swipe reveals the bars, second is
Back" (Change Log), or keep it as written and leave the Back proof to phone row P2.

## Row status (2026-09-30 20:04) — every row on the final APK a869ae73

`FINAL/final-run.txt` (driver `scripts/final_run.sh`; each row's earlier run kept as `<row>-apk-<id>/`). L14-1 and L14-2
are fixed (INDEX ledger). The table further down is the 00:05 state, kept for the history.

| row | result on a869ae73 | notes |
|---|---|---|
| E1 | 28/0 | |
| E2 | 10/0 | |
| E3 | 55/0, 4 recorded | |
| E4 | 39/0 | |
| E5 | 36/0 | |
| E6 | 44/0 | |
| E7 | 10/0 | |
| E8 | 26/0 | the Unlock button itself raises the PIN pad (L14-1); no `wm dismiss-keyguard` fallback |
| E9 | 20/0 | |
| E10 | 25/0 | |
| E11 | 15/0 | |
| E12 | 14/0 | under the 2026-09-30 ruling (a) |
| E13 | 70/1 (run 2) | the one failure: phase 03 E10 as a whole row (31/9; its unlock clause passes) — Q-E10 with Jeremy. Run 1 (53/9) found L14-2 and three driver faults: `E13-run1-1be3df67/` |
| E14 | 15/0 | |
| E15 | 17/0 | under the 2026-09-30 ruling (a): text nodes' right edge has 1.5 epx |
| E16 | 28/0, 1 recorded | |
| E17 | 16/0 | |

## Row status (2026-09-30 00:05) — phase BLOCKED on L14-1

Every row below ran on the APK installed at its run (the pod code changed between some runs — the fixes listed); the
gate needs every row again on ONE final APK before the reviews.

| row | result | evidence | notes |
|---|---|---|---|
| E1 pager | 27/0 | `E1/` (run 1 without the C-3 seed kept) | |
| E2 bars | 10/0 | `E2/` | |
| E3 pod content | 55/0, 4 recorded | `E3/` (runs 1-2 kept) | run 1 driver bugs; run 2 found the stopped-session defect (fixed, `MusicRules.podShows`) |
| E4 denied / empty / launch failures | 39/0 | `E4/` (run 1 kept) | run 1 found the Weather pod stuck on "Location is off" after a grant (fixed: the pod's retry) |
| E5 Settings | 36/0 | `E5/` (run 1 kept) | |
| E6 voice | 44/0 | `E6/` (run 1 kept) | speak.sh fixed to catch the final before a closing request unbinds the speech ring; doors RMS −29.88 dBFS, pane 291 ms after `speaking done` |
| E7 typed | 10/0 | `E7/` | exported components = phase 03's allow-list |
| E8 locked | 20/4, 1 recorded | `E8/` (runs 1-2 kept) | **BLOCKED — L14-1** (phase 03's unlock hand-off): every clause up to the Unlock button passes |
| E9 inside another app | not run | | |
| E10 edit mode | 25/0 | `E10/` (runs 1-2 kept) | run 2 found a hold on a pod launching (fixed: tap-or-hold) |
| E11 screen-off / lock / death | 15/0 | `E11/` (run 1 kept) | |
| E12 gesture nav | 14/0 (run 4, under Jeremy's ruling (a) of 2026-09-30) | `E12/` (runs 1-3 kept; run 3 had no device — the AVD had been closed at 08:03) | first edge swipe reveals the bars and moves nothing; the second is Back (DeskClock resumes) |
| E13 regression | not run | | |
| E14 backdrop | not run | | |
| E15 RV10 | not run | | |
| E16 diagnostics coverage | not run | | |
| E17 under the wizard | not run | | |
| JVM | PodBayCommandTest 12/0, PodBayRulesTest 11/0; CommandMatcherTest 27/0, LockGateTest 4/0, MusicRulesTest 18/0 | `app/build/test-results` (not kept in git) | |

## E8 and L14-1 (phase 03's unlock hand-off)

`E8/03-unlock-slice.txt`: `[cortana] card action UNLOCK pending=Locked` → Tess re-speaks "Unlock your phone to
continue." → 67 ms later `[cortana] unlock bridge: cancelled` → `[cortana] unlock cancelled; the pending request stays
on the card` → 100 ms later `[cortana] session hidden` (the model's `stop()` drops the pending request). The Unlock button
shows the AOSP lock screen, not the PIN pad (`E8-run1-pin-on-lockscreen`: the PIN typed there went nowhere; run 2: an
injected swipe up did not reach it; run 3: `wm dismiss-keyguard` did, and the device unlocked) — and nothing ran after
the unlock. Phase 03's own E10 records the same clause FAILED. Logged as L14-1 in INDEX's Blocked-on ledger; the fix
is phase 03's part and is planned with Jeremy.
