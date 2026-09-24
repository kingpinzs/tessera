# E0 run 1: 44 passed, 6 failed — two product defects (fixed), three driver faults (fixed), one AVD setup

## Product defect 1: a page request never reached a running Voice Recorder
Step 4 opened `RecorderActivity --es page record` while step 3's playback of other.m4a was still open. rec_record.xml
is the PLAYBACK page (rec_play, rec_playback; no rec_button), so no take started. Two causes:
- RecorderActivity had no launchMode (Alarms & Clock and Calculator are singleTask). With the task already running,
  `am start` only brought it to the front and dropped the intent, so onNewIntent never ran. The ring holds exactly
  one `[recorder] RecorderActivity created page=list` line across three launches.
- With the intent delivered, the requested page was set but an open playback page still took precedence.
Fix: `android:launchMode="singleTask"`, and a requested page closes the playback and trim pages first
(RecorderActivity.kt). After the fix (rec-page-after.txt): playback open: yes → after page=record: rec_button=yes,
rec_playback=no, rec_page:record=yes → after page=list: rec_page:list=yes.

## Product defect 2: a deleted timer's countdown notification stayed up
A timer created with the AlarmClock API (t13a91e43) and deleted in the app left its ongoing notification:
`dumpsys notification` showed pkg=app.tileshell id=-595992852 channel=clock_timers flags=ONGOING_EVENT, with
alarms.json and timers.json empty and 0 pending. -595992852 is "timer:t13a91e43".hashCode().
ClockNotifications.runningTimers only walked the timers still in the store, so a deleted one was never cancelled.
Fix: every posted clock_timers notification that is not a running timer's is cancelled, read from
activeNotifications. After the fix (timer-notif-after.txt): posted while running: 1 → posted after delete: 0.

## Driver faults (e0.sh / clock.sh), fixed in the driver
- "and played it again": phase 10's rule (MusicRules.plan) gives a PAUSED session no transport and no growth, and a tap
  opens the player instead. Expecting the tile to resume playback was the driver's error. Run 2 asserts the paused plan.
- "a timer is running": clock.sh's api_timer passed the name "E0 timer" to adb shell as separate words, so the
  device read `pkg=timer` and nothing was created (reproduced: `unable to resolve Intent { ... pkg=two }`). Reported to
  clock.sh's owner. Run 2 uses a one-word name.
- The listener's positive control (badges without app.tileshell) followed from the two missing notifications above.

## AVD setup (not a defect)
"Auxio is playing" failed because Auxio 4 had no music source, so its library was empty ("Your songs will show up here").
Set once on emulator-5556 through Auxio's own dialog: Music sources → Load from: System → Save. Auxio then loaded the six
fixture tracks and played the VIEW intent. Recorded in STATE.md as a baseline addition.
