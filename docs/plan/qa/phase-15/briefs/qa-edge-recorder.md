# Edge agent R — Voice Recorder edge cases, emulator-5560

Read `briefs/qa-edge-common.md` first; every rule there applies. Your emulator is **emulator-5560**. The recorder's
own rows (E14–E20, E24, E30, E14b) pass on the final build; their drivers and `rec.sh` show how the recorder is driven
(the page tags, `push_fixture_recordings` / `remove_fixture_recordings`, how a take is started, stopped and found in
MediaStore). The source under `app/src/main/kotlin/app/tileshell/recorder/` is the truth for tags and log lines.

## Your cases (the phase doc's Edge cases, "Recorder:" and "Other apps' recordings:")
Row EDGE_RECORDER (`edge_recorder.sh`):
1. A call arriving mid-take (`adb emu gsm call <n>`): the take pauses when the audio mode becomes RINGTONE (`[recorder]
   paused: call`, T15-36), stays paused through IN_CALL (answer it), and resumes on Resume after `gsm cancel`. The
   take's audio has no gap artefact at the pause point beyond one AAC frame: pull the file and analyse it around the
   pause.
2. RECORD_AUDIO revoked mid-take (`pm revoke`): the take stops and is saved, and the page shows the permission state.
   Restore the grant.
3. The microphone taken by a third-party app that starts capture on top: `AudioRecordingCallback` reports the shell's
   client silenced, and the take pauses with `[recorder] paused: silenced` (T15-38). You need an app that captures. Look
   for one on the image first; failing that, a minimal QA-only fixture app that records (the repo has an IME fixture
   under `docs/plan/qa/` to copy the pattern from), built outside the worktree's app. If neither is possible, NOT RUN
   with the reason.
4. A recording deleted outside the app (`adb shell rm` + a media rescan): its row disappears through the
   ContentObserver with no restart.
5. Rename to an existing name: the provider appends " (1)". Rename with characters MediaStore refuses: refused with a
   notice. Trim to zero length: refused.
6. Share with no app able to receive audio: Android's resolver empty state. Make "no app" true by disabling the audio
   receivers with `pm disable-user` for the test and re-enabling them all after (record which).
7. The `:recorder` process killed between takes (low-memory kill, simulated by its pid): nothing lost, nothing
   recovered.
8. Other apps' recordings: a foreign recording deleted by its own app while listed (its row disappears through the
   ContentObserver), and a foreign file with `is_recording` 1 outside Recordings/ (listed, read-only). Say if an
   existing row already proves either one, citing its check, instead of repeating it.

Row EDGE_RECORDER_LONG (`edge_recorder_long.sh`), run after EDGE_RECORDER:
9. The screen off for 30 minutes during a take: the foreground service keeps the take, and the file grows.
10. A 60-minute take: the file is about 30 MB, listed with its duration, and it plays and seeks.
These two may share one long take if the doc's clauses stay separately asserted.

Storage full (E19) and the microphone taken by Tess (E17) are already proved; cite, do not repeat.
