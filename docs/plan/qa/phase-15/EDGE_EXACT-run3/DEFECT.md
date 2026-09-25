# EDGE_EXACT — product defects (FINAL build 61c5b610716197e3's source, QA build without USE_EXACT_ALARM; emulator-5560, 2026-09-25)

Row log: `EDGE_EXACT.txt` (29 passed, 6 failed, 13 recorded). The same six clauses failed in run 2 on 4b7ac321's source
(`EDGE_EXACT-run2/`, emulator-5558) and five of them in run 1 (`-run1/`, which had no crash assertion). The driver is
right in all three: the red row, the inexact arm and the window are read and pass; what fails is the product.

The QA build: `git archive` of HEAD 8aeb2724 (its app/ is identical to 9a34ae17, the FINAL build's source) built in the
scratchpad (`scratchpad/p15/edgeC2-build.sh`), the one line
`<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` removed from `app/src/main/AndroidManifest.xml`,
md5 3ea9a820bfc02063, same debug signer; `adb install -r`, then `appops set app.tileshell SCHEDULE_EXACT_ALARM deny`.
(Recorded: the op reads back `SCHEDULE_EXACT_ALARM: default`, because the QA build declares neither exact-alarm
permission — dumpsys package lists none — so the app holds no exact-alarm grant at all; the re-arm line reads
`exact=false`, which is the state the edge case needs.)

## 1. An alarm armed inexactly never rings: the ring service crashes the shell at the fire (silent alarm)

Repro: the QA build installed; an alarm set through Tess (typed "set an alarm for 11:36 am"); wait in real time.

- It arms inexactly, as the Decisions say: `11:34:32.694 [alarms] rearm (store change): 1 alarms, 0 timers,
  exact=false` (`ring_exact_launcher.txt`); its dumpsys alarm entry (`dumpsys_alarm_entry.txt`) has no
  `exactAllowReason`, `origWhen=2026-09-25 11:36:00.000 window=+1m5s480ms`, and there is no Next alarm clock.
- AlarmManager delivers it at the window's end (11:37:05.48): the shell crashes, and again a second later when the home
  app restarts (`dropbox_data_app_crash.txt`, `adb shell dumpsys dropbox --print data_app_crash`):

```
2026-09-25 11:37:05 data_app_crash … Process: app.tileshell
java.lang.RuntimeException: Unable to start service app.tileshell.clock.RingService@cedf16f with Intent { act=app.tileshell.clock.RING … }:
java.lang.SecurityException: Starting FGS with type systemExempted callerApp=ProcessRecord{725aacc 3054:app.tileshell/u0a174} targetSDK=36
requires permissions: all of the permissions allOf=true [android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED]
any of the permissions allOf=false [android.permission.SCHEDULE_EXACT_ALARM, android.permission.USE_EXACT_ALARM, android:activate_vpn]
2026-09-25 11:37:06 data_app_crash … (the same, ProcessRecord{686b54e 4397:app.tileshell/u0a174})
```

- No `[alarms] fired` line survives (the process died with its ring), no ALARM player ever starts, no toast
  (`exact_ring.png`), and the next process start re-arms `0 alarms` — the one-shot alarm is gone without a sound.

Cause: `RingService` starts itself in the foreground with `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED`
(`app/src/main/kotlin/app/tileshell/clock/RingService.kt`); Android allows that type only to a holder of
SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM. The Decisions' own fallback — the grant revoked, alarms armed with
`setAndAllowWhileIdle` — is exactly the case where that type is refused. The Decisions name `mediaPlayback` as the other
type ("`systemExempted` … else `mediaPlayback`, which the manifest holds").
Failing clauses: "the alarm rings", "… inside Android's inexact window", "… and sounds", "… with no crash of
app.tileshell while it was due".

## 2. The exact-alarms notice is neither spoken nor shown for an alarm

Repro: the QA build installed; Tess, typed "set an alarm for 11:36 am".

- `reply_since`: `Alarm set for 11:36 AM.`; the session's texts (`tess_alarm_reply.xml`, `tess_alarm_reply.png`):
  `set an alarm for 11:36 am | Alarm set for 11:36 AM. | Ask me anything`. The Alarm tab shows no notice either (RECORD).
- Tess's own checklist row is red at the same time (`cortana_check:exact_alarms:missing`, glyph 232,17,35, "Off ·
  Reminders will be a few minutes late") and the re-arm reads `exact=false`, so the app knows exact alarms are off.

The Decisions: alarms "fall back to `setAndAllowWhileIdle` with the same spoken and shown notice reminders give"; the
edge case: "the notice is shown and spoken". The notice exists only on the reminder path
(`cortana/action/ActionLayer.kt`, " Exact alarms are off, so it may be a few minutes late."); `ActionLayer.alarm()`
returns `answer("Alarm set for …")` and never adds it.
Failing clauses: "the notice is SPOKEN", "the notice is SHOWN".

## What passes

The QA build has no USE_EXACT_ALARM (aapt2) while the FINAL build does; the restart and the store change arm with
`exact=false`; Tess's checklist row is red; no Next alarm clock, no exact-allow reason, a non-zero window. The
no-microphone rule held: RECORD_AUDIO was revoked for the Tess steps and granted back with its flags as found, and
dumpsys audio's recording-activity log gained no event during the row. Restore passed: the FINAL APK reinstalled
(61c5b610716197e3), home activity set, USE_EXACT_ALARM granted, `exact=true` again, the store empty.
