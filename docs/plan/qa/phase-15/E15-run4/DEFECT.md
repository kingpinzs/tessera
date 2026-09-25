# DEFECT (E15, found 2026-09-24 on emulator-5560, APK md5 42d6fcbd160015a2, branch phase-15 at 3e04aa3)

## The Voice Recorder page reopened from the back stack shows a stale list

A take stopped from its notification (E15) is saved by `:recorder` while the page is stopped. Reopening Voice Recorder
(the same activity instance: `onNewIntent` + `onStart`) shows "No recordings found" although MediaStore holds the take;
the list only refreshes on the NEXT MediaStore change or a fresh instance. E15 run 3 hit it at the restore (the take's
row was not on the list, so it could not be deleted through the app); run 4 asserts it outright.

Cause, from the source (`app/src/main/kotlin/app/tileshell/recorder/`): `RecorderActivity.onStop` calls
`RecorderLibrary.stop` (unregisters the ContentObserver) and `onStart` calls `RecorderLibrary.start`, which only
re-registers it — nothing re-reads MediaStore. The only refreshes are `onCreate` ("open"), the ON_RESUME effect (only
when READ_MEDIA_AUDIO visibility changed), the observer (only for changes while registered) and `RecorderClient.saved`
(a `:recorder` that stopped itself and was frozen/killed has nothing untold for the next bind). So every change made
while the page was stopped — a take saved after a notification Stop, a file added or deleted outside the app — is not
shown until something else changes MediaStore.

## Minimal reproduction (no take needed), run for real — output in defect_repro.out

```
$ adb shell am force-stop app.tileshell; adb shell am start -W -n app.tileshell/.recorder.RecorderActivity --es page list
  page nodes: rec_button rec_empty rec_list rec_more rec_page:list rec_root            # empty list, correct
$ adb shell input keyevent KEYCODE_HOME                                                # onStop: observer unregistered
$ adb push docs/plan/qa/phase-15/fixtures/other.m4a /sdcard/Recordings/other.m4a
$ adb shell content call --uri content://media --method scan_volume --arg external_primary
$ adb shell content query --uri content://media/external/audio/media --projection _id:_display_name:is_recording --where "is_recording=1"
  Row: 0 _id=38, _display_name=other.m4a, is_recording=1
$ adb shell am start -W -n app.tileshell/.recorder.RecorderActivity --es page list       # same instance resumes
  page nodes: rec_button rec_empty rec_list rec_more rec_page:list rec_root
  rec_empty text: [No recordings found]                                                 # WRONG: MediaStore has one
  launcher ring since the reopen: only "[recorder] bind :recorder -> true" / "bound to :recorder" — no "[recorder] list:" line
$ adb shell am force-stop app.tileshell; adb shell am start -W -n app.tileshell/.recorder.RecorderActivity --es page list
  page nodes: rec_row:38                                                                # a fresh instance lists it
```

## In E15's own run (run 3, E15-run3/E15.txt)

After the notification's Stop: MediaStore row `_id=44 Recording.m4a` (29.8 s, RMS -6.1 dBFS) exists; `app_delete_take:
no rec_row:44 on the list` — the reopened page's dump (E15-run3/.delete_44.xml) holds `rec_page:list` + `rec_empty` and no
`rec_row:`. Run 4's assertion "reopened after the notification stop, the list shows the take's row" records it as a FAIL.

## Expected

`RecorderActivity.onStart` (or `RecorderLibrary.start`) refreshes the list when the observer is re-registered, so the
page shows what MediaStore holds when it comes back. No workaround is in the driver: the restore removes the take with
adb (rm + scan) when the app does not list it, and says so in the log.
