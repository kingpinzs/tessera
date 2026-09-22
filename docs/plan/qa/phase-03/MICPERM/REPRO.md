# MICPERM — the three causes of "it never popped up the prompt", as reproduced (2026-09-22)

Jeremy, on the S25 Ultra: "it is saying it needs permission to use the microphone but it never popped up
the prompt to grant it". Three separate producer defects, each reproduced on tileshell_fhd before its fix.

## 1. Tess never asked (run 1, MICPERM-run1.txt)

Tess's listen path passed the request to `:speech`, which can only report NO_MICROPHONE_PERMISSION; nothing
requested the permission. When the page was then launched, the prompt was the resumed activity but drawn
UNDER the VoiceInteractionSession window. Fix: CortanaModel.startListening checks RECORD_AUDIO first and asks
the ActionHost; CortanaSession hides itself and starts CortanaPermissionActivity; a permission Android will no
longer ask for opens the app-info page instead of finishing silently.

## 2. The permission page's task was left behind, so later requests never ran (manual repro, before the fix)

After the "denied for good" path, the app-info page stayed in the permission page's affinity task
(`app.tileshell.cortana.permission`). The next NEW_TASK start only brought that task forward:

```
16:42:23.226 I ActivityTaskManager: START u0 {flg=0x10000000 xflg=0x4 cmp=app.tileshell/.cortana.CortanaPermissionActivity (has extras)} with LAUNCH_MULTIPLE from uid 10150 (BAL_ALLOW_VISIBLE_WINDOW) result code=2
16:42:23.253 V WindowManager: {WCT{RemoteToken{4f06f2b Task{7a2221f #627 type=standard A=10150:app.tileshell.cortana.permission}}} m=TO_FRONT f=MOVE_TO_TOP ...
```

`result code=2` is START_TASK_TO_FRONT. The ring stops at `[cortana] session hidden` with no `requesting ...`
line, and the resumed activity was `com.android.settings/.spa.SpaActivity t627`. So onCreate never ran.
The requestAssistant path leaves the assistant settings page in the same task the same way. Fix: every start
of CortanaPermissionActivity uses NEW_TASK | CLEAR_TASK. MICPERM part 4 re-creates this state and requires a
new `requesting` line.

## 3. Tess came back but did not listen (MICPERM-run2.txt, 10/11)

After a grant, CortanaService.open(LISTENING) re-shows the session. The session binds `:speech` on every show,
and the listen request arrived before the bind landed:

```
16:44:50.056 [speech] bind requested -> true
16:44:50.056 [speech] preload dropped: not bound
16:44:50.057 [cortana] session opened mode=LISTENING locked=false
16:44:50.080 [speech] startListening dropped: not bound
16:44:50.140 [speech] bound to :speech
```

Every open that goes straight to listening had the same race, and the page showed Tess listening meanwhile.
Fix: SpeechClient queues requests made while BINDING and delivers them in order on connect; only an UNBOUND
client drops them, and an unbind clears the queue.

Run 3 (MICPERM.txt): 15/15.
