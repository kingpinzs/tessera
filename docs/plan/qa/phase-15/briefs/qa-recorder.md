# Brief — phase 15 QA: the Voice Recorder rows (development checks on branch phase-15)

You write and RUN the device drivers for phase 15's Voice Recorder rows on your own emulator and report exact results.
This is the build-time development pass (T15-21): the gate is a later full re-run on main, so every driver you write is
the permanent, re-runnable row driver, and this run's evidence is real evidence.

## Where and how
- Repo: the lead's worktree `/home/jeremyking/projects/metro-launcher-p15` (branch `phase-15`). Write ONLY
  `docs/plan/qa/phase-15/scripts/<row>.sh` (plus small new helper files there) and each row's evidence under
  `docs/plan/qa/phase-15/<ROW>/`. Do NOT edit app code, `lib.sh`, `p15.sh`, `audio.sh`, `speak.sh`, other rows' drivers,
  INDEX.md, STATE.md or any phase doc. Do NOT commit (the lead commits). Never touch `/home/jeremyking/projects/metro-launcher`.
- YOUR emulator ONLY: `export ANDROID_SERIAL=emulator-5560` for every adb call and driver run. Never address emulator-5554,
  -5556 or -5558. Private driver lock: `export TMPDIR=/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/tmp5560`.
  PATH gets `$HOME/Android/Sdk/platform-tools`.
- The APK is built: `app/build/outputs/apk/debug/app-debug.apk`. Do NOT run gradle. emulator-5560 is already provisioned
  (by the Calculator QA pass). Install the current APK first (`adb install -r …`) and re-assert the home activity:
  `adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity`.
- Drivers source `docs/plan/qa/phase-15/scripts/lib.sh`, then `p15.sh`. Read both fully, plus `docs/plan/qa/phase-03/scripts/audio.sh`
  and `speak.sh`, and one existing phase 15 driver for style (e.g. `e0.sh`, `e26.sh`).

## The host audio route (read this twice)
- p15.sh exports `AUDIO_SINK=vmic<port>`, so on emulator-5560 audio.sh uses its own null sink `vmic5560`. Run
  `docs/plan/qa/phase-03/scripts/audio.sh setup` ONCE with that environment (it creates vmic5560, turns the AVD's host mic
  on and moves THIS emulator's capture stream onto vmic5560.monitor), then `audio.sh check` must pass (rc 0) before any
  microphone step. The setup runs from a shell that sourced p15.sh, or with AUDIO_SINK=vmic5560 exported by hand.
- NEVER run `audio.sh setup` or `provision.sh` without AUDIO_SINK set: with the default sink name (`vmic`) it switches the
  desktop's default microphone to the virtual sink (the owner's machine). If you must re-provision (for example after
  E14b's uninstall), export AUDIO_SINK=vmic5560 first. Then check `pactl get-default-source` is still
  `alsa_input.usb-046d_Logitech_BRIO_9130DEA5-03.analog-stereo`; if it is not, set it back to that and report it.
- EasyEffects on this host leaves the emulators' MICROPHONE streams alone (its input blocklist), but still processes
  their OUTPUT streams. Recording an emulator's own output stream (`audio.sh record`) returns digital silence (about
  -115 dBFS), even though the AVD plays (dumpsys audio shows the player). The owner has decided to leave it that way
  ("we are good here"). So every assertion that measures what the AVD PLAYS through `audio.sh record` fails for that
  reason. Keep those assertions, let them FAIL, and write the reason in the row's log with `note`. Never bend them, and
  never substitute a different measurement. What the AVD RECORDS (a take's own file, pulled with adb and analysed on the
  host) is NOT affected, so assert on that.
- Test tones: generate host WAVs with ffmpeg (e.g. a 440 Hz sine, 16 kHz mono) into your TMPDIR or the row's evidence
  dir, and play them with `audio.sh say <wav>` (it plays into this emulator's sink). A "tone looping" step can loop
  `audio.sh say` in a background subshell whose pid you record, and stop it by that pid (never pkill).

## The spec
`docs/plan/phase-15-inbox-clock-calculator-recorder.md`: read the Acceptance preamble IN FULL, "Harness contracts", the
Voice Recorder Decisions, and rows **E14, E15, E16, E17, E18, E19, E20, E24, E30, then E14b LAST** (it uninstalls the app:
reinstall from the APK with `adb install -g`, re-assert the home activity and the notification listener, restore the
layout, and leave the emulator as you found it), plus the Voice Recorder EDGE CASES line. **E21's recorder parts are NOT
yours**: another agent owns e21.sh. Report anything E21 would need from you. Build facts: `docs/plan/qa/phase-15/briefs/recorder.md`
(the builder's brief) and the source under `app/src/main/kotlin/app/tileshell/recorder/`, which is the truth. Fixture
recordings: `push_fixture_recordings` / `remove_fixture_recordings` in lib.sh (other.m4a is 3 s by design, E14 pins it).

## Rules that are not negotiable
1. Every assertion must be able to FAIL. Read a node's OWN text by its tag, a dumpsys line, a MediaStore query, a
   pulled file's analysis, or the ring slice from a MARK taken just before the action. A row with zero assertions fails.
2. Every row starts from the baseline and restores what it changed (RV12): takes deleted, fixtures removed, permissions
   back, the volume back, the layout back. Say what you left.
3. Run every driver for real on emulator-5560 and keep its log, dumps and screencaps under `docs/plan/qa/phase-15/<ROW>/`.
   A failed run is kept (rename the dir to `<ROW>-runN` before re-running) and fixed only where the DRIVER was wrong.
   If the PRODUCT is wrong, write `docs/plan/qa/phase-15/<ROW>/DEFECT.md` (commands and output), report it, and add no
   workaround.
4. Something the AVD cannot do is NOT RUN, with the exact reason. Never fake it.
5. Kill processes only by recorded pid. Read exit codes from files, never through a pipe. `adb shell` re-joins its
   arguments: pass a command with spaces or quotes as ONE string. A `while read` loop that runs adb inside must read
   its list on another fd (`done 3< file`, `read … <&3`), because adb would swallow stdin. Tab is IFS whitespace, so
   empty tab-separated fields collapse; use another separator.

## Report back (final message, under 90 lines)
Per row: the driver path and the final run's summary line quoted, and each FAIL or NOT RUN with its reason. Then every
product DEFECT (row, repro path, one line), every doc-vs-build difference, what E21's recorder part needs, and the
emulator and host state you left (the default source, 5560's capture stream, the sinks).
