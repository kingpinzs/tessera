# E9 run 1: 26 passed, 5 failed — driver faults; no product defect

- "the Alarm tab lists 7:20 AM" and "the Timer tab shows a timer counting": the dumps were taken with Tess's session
  window still on top (alarm_tab.xml holds Tess's transcript "Set in alarm for seven twenty a m"). The alarm WAS set
  (alarms.json: 07:20, enabled), and both timers WERE started. The ring shows them firing 5 minutes later
  (11:50:38 tc6e3c014 late=2 ms, 11:57:23 t4294286d late=24 ms). The driver now closes Tess before opening the Clock.
- "dumpsys alarm's Next alarm clock names app.tileshell": this image's section is
  "user:0 pendingSend:false time:1790342400000 = 2026-09-25 07:20:00.000", time only, no package. The driver now reads
  the time there and the shell's pending clock alarm from the pending list.
- The restore failed (stores not empty, 2 pending) for the same Tess-on-top reason, and the second timer rang on
  emulator-5556 from 11:57 until the lead dismissed it at the owner's request ("they are beeping"). The alarms and
  timers were then deleted in the app: stores empty, 0 pending. The driver now deletes each timer as soon as it is
  checked, and dismisses any ring before its restore.
