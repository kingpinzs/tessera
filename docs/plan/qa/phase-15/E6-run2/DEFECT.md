# E6 — DEFECT: a timer that ended while the phone was off rings without the "ended while the phone was off" text on its notification

Phase 15 Decisions "Clock rules": *a timer whose deadline passed while the process was dead fires as soon as it is
re-armed, as an overdue reminder does ("timer ended while the phone was off" on its notification)*; E6's third pass
(T15-53): *it fires at once with the "ended while the phone was off" notification text*.

The build logs the fact in the launcher ring (`[alarms] timer <id> ended while the phone was off`, RingService.kt:126)
but the ring notification it posts carries only the ordinary title and body (`ClockNotifications.ring`: `ring.title` =
"Timer finished", `ring.body` = name · length). Nothing puts the text on the notification.

## Repro (emulator-5558, build 39402722396d9129 / 337842979 bytes; the row's third pass, E6.txt run 2)

    adb shell am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 60 \
      --es android.intent.extra.alarm.MESSAGE Minute --ez android.intent.extra.alarm.SKIP_UI true \
      -n app.tileshell/.clock.AlarmApiActivity
    # when the Timer tab's [timer] line reads remaining <= 10000:
    adb reboot; adb wait-for-device; (poll sys.boot_completed = 1)
    adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -F '[alarms]'
    adb shell dumpsys notification --noredact | grep -A12 'pkg=app.tileshell' | grep -E 'android.title|android.text'

Output (run 2, 2026-09-24):

    [alarms] fired t… kind=timer late=…            (fires at once after the boot: the re-arm's RING_NOW path)
    [alarms] timer t… ended while the phone was off (the ring line is there)
    android.title=String (Timer finished)
    android.text=String (Minute · 0:01:00)          (no "ended while the phone was off" anywhere on the notification)

The row's assertion `the notification text says it ended while the phone was off` fails on the product; everything
else in the pass (fires at once after the boot, the ring line) passes.
