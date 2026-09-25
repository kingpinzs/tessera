# Edge agent C — Alarms & Clock and app-list edge cases, emulator-5558

Read `briefs/qa-edge-common.md` first; every rule there applies. Your emulator is **emulator-5558**. The clock rows
pass on the final build; `clock.sh` and the EDGE_ALARMS / E6c / E21 / E31 drivers show how alarms are set, jumped to
and rung, and how the clock is reset (`scratchpad/p15/clock_reset.sh`, used as the runner's `PRE_ROW`). Use
`gdump_for` / `gdump_windows` (clock.sh): the gesture dump tool sometimes returns a partial dump.

## Your cases
Row EDGE_ALARM_CONTEXT (`edge_alarm_context.sh`), the doc's "Alarm firing while …" bullet:
1. An alarm fires while Tess is listening: the session hides.
2. … while Start is in edit mode: edit mode ends.
3. … while Music plays (phase 10's player; `push_fixture_recordings` or the Music fixtures the phase 10/15 drivers use):
   the player pauses on the transient focus loss and, per phase 10's E9, does not resume by itself. That is recorded,
   not hidden.
4. … while the recorder runs: the take continues, and the alarm sound is on another stream.
5. … while glance (phase 07) shows. Phase 07 is not built (INDEX row 07 "pending"), so this is NOT RUN with that reason
   unless you find glance on the build.

Row EDGE_LOCKED_SOUND (`edge_locked_sound.sh`), a Decisions clause no row drives (T15-22): an alarm whose sound is
"Pick from my music" (a MediaStore file) rings after a reboot before the first unlock. The file cannot be read before the
unlock, so the default sound rings and `[alarms] sound <uri> locked -> default` is logged. E6c's driver shows the
reboot-with-PIN and locked-ring method. Also cover the Liveness bullet's force-stop (`am force-stop app.tileshell` with an
armed alarm: it is re-armed at the next start, as reminders are), unless E6 or E21 already asserts it; if so, cite the
check.

Row EDGE_EXACT (`edge_exact.sh`), the `USE_EXACT_ALARM` bullet: on a QA-only build with `USE_EXACT_ALARM` removed from
the manifest (built from a `git archive` of HEAD in your scratch dir, never the worktree's app), with `appops set
app.tileshell SCHEDULE_EXACT_ALARM deny`: the Setup `exact_alarms` row is red, alarms arm inexactly, the notice is shown
and spoken, and the alarm still rings within Android's inexact window. Restore: reinstall the FINAL APK from
`app/build/outputs/apk/debug/app-debug.apk` (assert its md5 4b7ac321ce4d0ede is installed again), set the home
activity again, and set the appop back to default.

Row EDGE_APPLIST (`edge_applist.sh`), the "App list:" bullet: the three apps under `wm size 720x1560` (HD+, 2 px/epx,
RV10); pin each to Start and unpin it; the pinned Alarms & Clock tile when no alarm is set (the static face) and when the
next alarm is a snoozed one. Restore `wm size reset` and the Start layout.

Then re-run E21 (`e21.sh`) on the final build: its driver now saves the READ_MEDIA_AUDIO-denied ring slice as it asserts
it.

World clock's "two zones with one exemplar name" was NOT RUN in EDGE_WORLD ("this image never labels two zones alike").
Try once more with a link zone and its canonical zone (e.g. America/Indianapolis and America/Indiana/Indianapolis, if
the device's zone list offers both); if the list never shows two alike, leave it NOT RUN with the evidence.
