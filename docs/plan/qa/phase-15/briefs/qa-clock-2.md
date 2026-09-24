# Brief — phase 15 QA, Alarms & Clock continuation (emulator-5558)

You continue the Alarms & Clock QA pass. The first agent wrote the drivers and stopped at a usage limit; its final
report was lost, so its DEFECT.md files are the only record of what it found. Read `briefs/qa-clock.md` first: every
rule there still applies. This brief replaces its "Report back" section and tells you where things stand.

## Where things stand
- Branch `phase-15` in `/home/jeremyking/projects/metro-launcher-p15` is rebased onto main (phase 11 has landed). The
  current APK (`app/build/outputs/apk/debug/app-debug.apk`, md5 prefix 33cc71c192190cc9) is ALREADY installed on
  emulator-5558, with the home activity set. It carries the fixes for all three product defects the first pass found:
  - the lap crash (E7 / E31 / EDGE_STOPWATCH; `E7-run2/DEFECT.md`);
  - the lists whose last rows could never scroll into view (E10's Sounds page, the music picker, city search and the
    laps list; `E10-run4/DEFECT.md`);
  - the timer that ended while the phone was off now carries "Timer ended while the phone was off" on its notification
    (E6; `E6-run3/DEFECT.md`).
  Those clauses must now PASS.
- emulator-5558 was relaunched today (it powered itself off). Its microphone route is set: sink vmic5558, and
  `audio.sh check` passes with AUDIO_SINK=vmic5558, which p15.sh exports from the serial. The desktop's default source
  must stay the BRIO; never run `audio.sh setup` or `provision.sh` without AUDIO_SINK set.
- The emulators' output is MUTED on the host at the owner's request, so rings are inaudible there; read them from
  dumpsys audio, as the drivers do. Dismiss any ring your row leaves up.
- p15.sh's gdump is now the one dump for every row. It carries your predecessor's new-file-per-dump and fallback fixes,
  never passes --no-restart, and reads `gesture.dump.windows`; clock.sh no longer overrides it.
- A previous agent's EDGE_ALARMS driver ran on as an orphan and polluted earlier runs. No driver of yours may leave a
  process running after its row ends: kill background subshells by their recorded pid at the restore.

## The last gate pass on this emulator (build 2356b99f, before the fixes) — failures to resolve
Each is PRODUCT (write DEFECT.md and report it; do not work around it) or DRIVER (fix the driver and re-run). Decide
from evidence.
- E4 89/1: "below the toast: the wallpaper … matches ± 8 levels in >= 90 %": got 0.8729.
- E6 40/1: the ended-while-off text (now fixed in the product).
- E7 12/3: the stopwatch started at 00:09:13.40, a stopwatch left running by an earlier row (a baseline or restore
  fault); "moment 2: the dump's own latency <= 1 s" measured 16927 ms (the fallback dump while the hundredths tick);
  the lap crash (fixed).
- E8 33/1: "the shell uid's byte counters are unchanged across the world-clock steps", 37503/9101 -> 37607/9141. Is
  that the world clock, or other shell traffic in the same uid (the weather feed, for instance)? Settle it with
  evidence.
- E10 127/5: the ink positions 2.1 (x 36 vs 29 px), 2.7 (cap top 327 vs 332), 4.6 (title ink x 78 vs 73) and 4.8
  (column split 359 vs 353) are about 2 epx off, plus the Sounds list (fixed). If the build places text by its box
  where r11 measured ink (the Calculator had exactly this; see `E13-run1/DEFECT.md` and `calculator/CalcInk.kt`), that
  is a PRODUCT defect: write it up with the measurements and the cause you find in the source.
- E31 60/3: "pin_stopwatch: already pinned", a stopwatch tile left pinned by an earlier row; and the lap crash (fixed).
- E21 42/2: "the Next alarm clock line holds it" (expected 1790339400000, got 1790287080000: an earlier alarm of the
  row's own was still pending?). The microphone check has been fixed by the lead.
- EDGE_ALARMS: "Snooze acts on Ann / … on Bob" (the same-minute join); EDGE_STOPWATCH 5/1 (the lap crash).
- A reset script the lead used between rows is `/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/clock_reset.sh`.
  It turns automatic time on, dismisses any ring and deletes every alarm and timer through the app, but it does NOT
  reset the stopwatch or unpin clock tiles. Your drivers' own baselines and restores are the fix (RV12). A driver may
  bring a leftover to baseline, but it must assert the state it then starts from.

## Your job
On THIS build, run every clock row: E3, E4, E4b, E5, E6, E6c, E7, E8, E10, E23, E31, E32, E33, E21, then the edge
cases EDGE_ALARMS, EDGE_STOPWATCH, EDGE_TIMERS and EDGE_WORLD. Keep each previous evidence dir as `<ROW>-runN` first.
Fix drivers where they are wrong and re-run; write DEFECT.md for product faults. Use `emulator-5558` only, never
commit, and never edit app code or anything outside `docs/plan/qa/phase-15/scripts/` and the rows' evidence dirs.

## Report back (final message, under 90 lines)
Per row: the final summary line on this build, and each FAIL or NOT RUN with its reason and whether it is product or
driver. Then every product DEFECT (row, repro path, one line), every doc-vs-build difference, and the state you left
(no processes running, no alarms, timers or stopwatch, and the audio route).
