# Phase 05 QA tooling — IME fixture app and gesture driver

Test tooling for the keyboard rows of `docs/plan/phase-05-keyboard.md` (E2, E4, E6, E9, E12). Never
shipped: it is a separate Gradle module, `:testapps:ime-fixture`, and two APKs come out of it.

| APK | Built by | Path (under the repo root) | Installs as |
|---|---|---|---|
| Fixture app | `./gradlew :testapps:ime-fixture:assembleDebug` | `testapps/ime-fixture/build/outputs/apk/debug/ime-fixture-debug.apk` | `app.tileshell.qa.imefixture` |
| Gesture driver | `./gradlew :testapps:ime-fixture:assembleDebugAndroidTest` | `testapps/ime-fixture/build/outputs/apk/androidTest/debug/ime-fixture-debug-androidTest.apk` | `app.tileshell.qa.imefixture.test` |

Install both with `adb install -r -t <apk>`. The driver targets the fixture package, so it needs the
fixture installed; its ops (swipe, drag, script, dump) act on whatever is on screen, not only on the
fixture.

Verified 2026-09-22 on emulator-5554 (AOSP API 36, 1080x2340 @ 450 dpi, stock
`com.android.inputmethod.latin/.LatinIME`); the shell's own keyboard was not installed.

## Prerequisite on this AVD: the soft keyboard is suppressed by the hardware keyboard

The AVD (`tileshell_fhd`) has `hw.keyboard=yes`, and `show_ime_with_hard_keyboard` is `0`. In that
state `dumpsys input_method` says `mInputShown=true` and the nav bar shows the keyboard-down arrow,
but **the IME window is transparent and draws nothing** (screencap checked), and its nodes are in no
dump at all. Every keyboard row needs:

    adb shell settings put secure show_ime_with_hard_keyboard 1     # before
    adb shell settings put secure show_ime_with_hard_keyboard 0     # restore (PLAN RV12)

## 1. The fixture app

Plain Views, no Compose. Two screens, both `singleTop`, portrait, `stateHidden`:

- `MainActivity` (launcher, `adjustResize`): the mirror block on top, then a ScrollView of labelled
  fields. Field ids (the contract): `field_text` (text, no cap), `field_number`, `field_phone`,
  `field_email`, `field_password`, `field_url` (textUri + actionGo), `field_search` (actionSearch),
  `field_send` (actionSend), `field_next` (actionNext), `field_done` (actionDone), `field_multiline`,
  `field_nosuggest`, `field_capsent`, `field_capwords`, `field_filter` (LengthFilter(5) + a-z only).
  Labels are `label_<name>`. No `android:hint` anywhere (an empty EditText reports its hint as
  `text` in a dump).
- `BottomFieldActivity` (exported, not a launcher, `adjustNothing`): `top_field` under the mirror
  block, `bottom_field` anchored 8 dp above the nav bar (E9's tap-through target).

Mirror block (always visible, outside the ScrollView; the resource ids are read from the dump):

| id | value |
|---|---|
| `mirror_focus` | focused field's id name (`field_email`) or `none` |
| `mirror_text` | `[` + raw text + `]`, newlines escaped as `\n` (raw even for password fields) |
| `mirror_sel` | `selectionStart,selectionEnd` |
| `mirror_len` | text length |
| `mirror_action` | last editor action received: `IME_ACTION_SEARCH@field_search`; `none` initially |
| `mirror_inputtype` | focused field's `inputType` as hex (`0x21` = textEmailAddress, `0x81` = textPassword) |

Refreshed on every text change (TextWatcher), every caret move (`EditText.onSelectionChanged`
override, since a TextWatcher does not fire on caret moves), every focus change and every editor
action. Every view has a real `android:id`, so a dump reports
`resource-id="app.tileshell.qa.imefixture:id/<name>"`.

Starting a screen with a field focused and the keyboard requested (`showSoftInput` once the window
has focus):

    adb shell am start -S -W -n app.tileshell.qa.imefixture/.MainActivity -e focus field_email
    adb shell am start -S -W -n app.tileshell.qa.imefixture/.BottomFieldActivity -e focus top_field

`-S` force-stops first, for a fresh instance (rows start from the baseline state). Without `-S`, a
second `am start` of a screen that is already on top re-delivers the intent to the running instance
(`singleTop`, "intent has been delivered to currently running top-most instance"), which moves focus
and keeps the other fields' text; without `singleTop` the framework would drop the extras entirely
because they are not part of the intent filter.

## 2. The gesture driver

One instrumentation test, `app.tileshell.qa.imefixture.Gesture#run`, executes exactly one op per
invocation and reports through `Instrumentation.sendStatus`, which `am instrument -r` prints as
`INSTRUMENTATION_STATUS: gesture.<key>=<value>` lines. Template:

    adb shell am instrument --no-restart -r -w -e op <op> [op args] \
        app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner

`--no-restart` is load-bearing: without it `am instrument` force-stops the target package before the
run and again after it, which kills a fixture screen that is up and takes the keyboard with it.
With it the instrumentation attaches to the running fixture process (verified: activity, keyboard and
typed text all survive; `gesture.wait` reported `elapsed_ms=250` for `-e ms 250`). If the fixture is
not running, drop `--no-restart` (the driver then starts the process with no activity, which is fine
for `dump`, `swipe`, `drag` and `script` on a third-party app).

`-r` is required or `am` prints only the stream result. A failed op ends with `gesture.ok=false`,
`gesture.error=...` and `FAILURES!!!`; a good one with `gesture.ok=true` and `OK (1 test)`.

| op | args | what it does | reports |
|---|---|---|---|
| `swipe` | `-e points "x,y;x,y;..." -e steps N` (default 20) | `UiDevice.swipe(Point[], steps)`, one continuous gesture through every point (E4 Word Flow) | `swipe.result`, `swipe.elapsed_ms`, start/end uptime |
| `drag` | `-e from x,y -e to x,y -e steps N` (default 40) | `UiDevice.drag(sx, sy, ex, ey, steps)`: long-press, move, release (E9 space-bar drag) | `drag.result`, `drag.elapsed_ms`, start/end uptime |
| `script` | `-e script "cmd; cmd; ..."` | timed one-pointer MotionEvents through `UiAutomation.injectInputEvent(event, true)` with real `SystemClock.uptimeMillis()` event times (E12) | per command: `script.cmd.N`, `script.planned.N` (ms from t0), `script.start.N`, `script.end.N` (uptime), `script.gap.N` (start − previous start); `script.t0`, `script.total_ms` |
| `dump` | `-e out /sdcard/Download/<file>.xml` | `Configurator.setWaitForIdleTimeout(0)` then `UiDevice.dumpWindowHierarchy(File)`, plus a window report | `dump.windows` (`TYPE:package:bounds:focused:active;...` from `UiAutomation.getWindows()`), `dump.roots`, `dump.nodes`, `dump.packages` (nodes per package), `dump.ime_windows`, `dump.ime_nodes`, `dump.all_windows` |
| `wait` | `-e ms N` | sleeps N ms (timing sanity check) | `wait.elapsed_ms` |

Script grammar (parser: `testapps/ime-fixture/src/main/kotlin/app/tileshell/qa/imefixture/gesture/GestureScript.kt`,
plain Kotlin with a JVM unit test, 9 cases, `./gradlew :testapps:ime-fixture:testDebugUnitTest`):

    down X Y | move X Y | up | sleep MS | tap X Y | hold X Y MS | moveto X Y MS STEPS

`tap` is down + up 40 ms apart; `hold` is down, MS, up; `moveto` is STEPS interpolated moves from
the last position spread over MS (needs a pointer down). Time is one sequential timeline: each
command starts when the previous one ends (tap lasts 40 ms, hold/moveto/sleep last MS, the rest 0),
and the runner sleeps to each command's planned start on the uptime clock before injecting, so one
slow injection never shifts the next command. **Two taps 150 ms apart are
`tap X Y; sleep 110; tap X Y`**; read `script.start.N` (or `script.gap.N`) for the spacing that
actually happened. A script that ends with a pointer down gets an `up` injected and
`script.note` says so. Quoting: `adb shell 'am instrument ... -e script "tap 60 552; sleep 110; tap 60 552" ...'`.

Why `/sdcard/Download/`: with `--no-restart` the fixture process keeps its scoped-storage sandbox,
so `/sdcard/<file>.xml` fails with `EPERM` (`--no-isolated-storage` cannot help; it only applies to
a process the instrumentation starts). `/sdcard/Download/<file>.xml` is writable and
`adb shell cat` / `adb pull` read it back.

### Does `dumpWindowHierarchy` reach the IME window?

**Yes.** uiautomator 2.4.0's `UiDevice.dumpWindowHierarchy` walks every window from
`UiAutomation.getWindows()` (UiDevice sets `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` itself) and writes
one `<hierarchy rotation="0">` with one root `<node>` per window; each root carries the window's
`package` and its `bounds` equal the window's bounds, so the IME root is the one whose package and
bounds match the `INPUT_METHOD` entry of `dump.windows`. The custom walk the phase doc allowed for
was not needed. Plain `adb shell uiautomator dump` on the same state contains **zero** IME nodes, so
it must not be used for anything that reads the keyboard; the fixture's mirror views are fine in
either dump. The node attributes are the `uiautomator dump` set plus `visible-to-user`,
`drawing-order`, `hint` and `display-id`.

One thing the dump op handles for you: `UiAutomation.getWindows()` is delivered asynchronously and
the first calls after connecting can return an empty or partial list (seen: 0 and 2 windows while
4 were up); the op polls until the active window is in the list before reporting `dump.windows`.

## 3. Evidence, 2026-09-22, final build (JVM tests 9/9, both APKs installed with `-r -t`)

### (a) `am start -e focus field_email`, `input text hello`, plain `uiautomator dump`

    $ adb shell am start -W -n app.tileshell.qa.imefixture/.MainActivity -e focus field_email
    Status: ok
    $ adb shell input text hello
    $ adb shell dumpsys input_method | grep mInputShown
          mInputShown=true
    $ adb shell uiautomator dump /sdcard/qa-a.xml
    text="field_email" resource-id="app.tileshell.qa.imefixture:id/mirror_focus"
    text="[hello]" resource-id="app.tileshell.qa.imefixture:id/mirror_text"
    text="5,5" resource-id="app.tileshell.qa.imefixture:id/mirror_sel"
    text="5" resource-id="app.tileshell.qa.imefixture:id/mirror_len"
    text="none" resource-id="app.tileshell.qa.imefixture:id/mirror_action"
    text="0x21" resource-id="app.tileshell.qa.imefixture:id/mirror_inputtype"
    text="hello" resource-id="app.tileshell.qa.imefixture:id/field_email"

### (b) password field: raw in the mirror, masked in the dump

    $ adb shell am start -S -W -n app.tileshell.qa.imefixture/.MainActivity -e focus field_password
    $ adb shell input text Secret1
    text="field_password" resource-id="app.tileshell.qa.imefixture:id/mirror_focus"
    text="[Secret1]" resource-id="app.tileshell.qa.imefixture:id/mirror_text"
    text="7,7" resource-id="app.tileshell.qa.imefixture:id/mirror_sel"
    text="7" resource-id="app.tileshell.qa.imefixture:id/mirror_len"
    text="0x81" resource-id="app.tileshell.qa.imefixture:id/mirror_inputtype"
    text="•••••••" resource-id="app.tileshell.qa.imefixture:id/field_password" class="android.widget.EditText" ... focused="true" ... password="true"

Then, without `-S`, `am start ... -e focus field_email` on the running instance: `Warning: Activity
not started, intent has been delivered to currently running top-most instance.`, and the dump read
`mirror_focus=field_email`, `mirror_text=[]`, `mInputShown=true`.

### (c) `op=script`, two taps 150 ms apart on `field_text` (bounds `[23,493][1057,612]`, "hello" typed)

    $ adb shell am instrument --no-restart -r -w -e op script -e script "tap 60 552; sleep 110; tap 60 552" app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner
    INSTRUMENTATION_STATUS: gesture.script.cmd.0=tap 60 552
    INSTRUMENTATION_STATUS: gesture.script.planned.0=0
    INSTRUMENTATION_STATUS: gesture.script.start.0=12829333
    INSTRUMENTATION_STATUS: gesture.script.end.0=12829395
    INSTRUMENTATION_STATUS: gesture.script.cmd.1=sleep 110
    INSTRUMENTATION_STATUS: gesture.script.planned.1=40
    INSTRUMENTATION_STATUS: gesture.script.start.1=12829395
    INSTRUMENTATION_STATUS: gesture.script.gap.1=62
    INSTRUMENTATION_STATUS: gesture.script.cmd.2=tap 60 552
    INSTRUMENTATION_STATUS: gesture.script.planned.2=150
    INSTRUMENTATION_STATUS: gesture.script.start.2=12829483
    INSTRUMENTATION_STATUS: gesture.script.end.2=12829761
    INSTRUMENTATION_STATUS: gesture.script.gap.2=88
    INSTRUMENTATION_STATUS: gesture.script.t0=12829333
    INSTRUMENTATION_STATUS: gesture.ok=true
    OK (1 test)

Tap starts 12829333 and 12829483: **150 ms apart, 0 ms off** (an earlier run: 12623422 → 12623572,
also 150). The first tap's synchronous down+up took 62 ms, so the `sleep` started 22 ms late, and
the deadline scheduling still put the second tap exactly on 150. The taps reached the field: the
double-tap on the word selected it, `mirror_sel` went `5,5` → `0,5`:

    text="field_text" resource-id="app.tileshell.qa.imefixture:id/mirror_focus"
    text="[hello]" resource-id="app.tileshell.qa.imefixture:id/mirror_text"
    text="0,5" resource-id="app.tileshell.qa.imefixture:id/mirror_sel"

### (d) `op=swipe`, 5 points across the stock keyboard's key rows

    $ adb shell am instrument --no-restart -r -w -e op swipe -e points "150,1900;350,1900;550,1900;750,1900;950,1900" -e steps 20 app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner
    INSTRUMENTATION_STATUS: gesture.swipe.points=150,1900;350,1900;550,1900;750,1900;950,1900
    INSTRUMENTATION_STATUS: gesture.swipe.steps=20
    INSTRUMENTATION_STATUS: gesture.swipe.result=true
    INSTRUMENTATION_STATUS: gesture.swipe.elapsed_ms=2779
    INSTRUMENTATION_STATUS: gesture.ok=true
    OK (1 test)

Speed note for E4: `steps` is per segment and each injected step is synchronous, so 4 segments × 20
steps took 2.78 s (≈35 ms per step). Use fewer steps for a faster Word Flow stroke. (The stroke did
land on LatinIME: it replaced the selected "hello" with its own gesture result, `mirror_text=[]`.)

### (e) `op=dump` with the keyboard up: the IME window's nodes are in the file

    $ adb shell am instrument --no-restart -r -w -e op dump -e out /sdcard/Download/qa-e.xml app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner
    INSTRUMENTATION_STATUS: gesture.dump.windows=SYSTEM:com.android.launcher3:[0,2205][1080,2340]:focused=false:active=false;SYSTEM:com.android.systemui:[0,0][1080,136]:focused=false:active=false;INPUT_METHOD:com.android.inputmethod.latin:[0,1422][1080,2205]:focused=false:active=false;APPLICATION:app.tileshell.qa.imefixture:[0,0][1080,2340]:focused=true:active=true
    INSTRUMENTATION_STATUS: gesture.dump.window_count=4
    INSTRUMENTATION_STATUS: gesture.dump.roots=4
    INSTRUMENTATION_STATUS: gesture.dump.nodes=123
    INSTRUMENTATION_STATUS: gesture.dump.packages=app.tileshell.qa.imefixture=37;com.android.launcher3=11;com.android.systemui=28;com.android.inputmethod.latin=47
    INSTRUMENTATION_STATUS: gesture.dump.ime_windows=com.android.inputmethod.latin
    INSTRUMENTATION_STATUS: gesture.dump.ime_nodes=47
    INSTRUMENTATION_STATUS: gesture.dump.all_windows=true
    INSTRUMENTATION_STATUS: gesture.ok=true
    OK (1 test)

The file's four root nodes and the keys under the IME root:

    root node 0: package app.tileshell.qa.imefixture  bounds [0,0][1080,2340]     descendants 36
    root node 1: package com.android.launcher3        bounds [0,2205][1080,2340]  descendants 10
    root node 2: package com.android.systemui         bounds [0,0][1080,136]      descendants 27
    root node 3: package com.android.inputmethod.latin bounds [0,1422][1080,2205] descendants 46
    key: q com.android.inputmethod.keyboard.Key [0,1550][109,1715]
    key: w com.android.inputmethod.keyboard.Key [108,1550][217,1715]
    key: e com.android.inputmethod.keyboard.Key [216,1550][325,1715]
    key: a com.android.inputmethod.keyboard.Key [0,1715][163,1880]
    key: Shift com.android.inputmethod.keyboard.Key [0,1880][163,2045]
    key: Delete com.android.inputmethod.keyboard.Key [918,1880][1080,2045]

Plain `adb shell uiautomator dump` on the same state: `0` nodes with
`package="com.android.inputmethod.latin"`.

### (f) BottomFieldActivity: `bottom_field` at the bottom, a tap there focuses it

    $ adb shell am start -S -W -n app.tileshell.qa.imefixture/.BottomFieldActivity -e focus top_field
    Status: ok
          mInputShown=true
    resource-id="app.tileshell.qa.imefixture:id/top_field" bounds="[23,493][1057,612]"
    resource-id="app.tileshell.qa.imefixture:id/bottom_field" bounds="[23,2063][1057,2182]"
    text="top_field" resource-id="app.tileshell.qa.imefixture:id/mirror_focus"

`bottom_field` ends at y=2182, the nav bar starts at 2205: 23 px = 8 dp at 2.8125 px/dp. With the
stock keyboard up (panel `[0,1422][1080,2205]`) the field is under the panel, so for this check the
keyboard was hidden with one `KEYCODE_BACK` (`mInputShown=false`) before the tap; in E9 the shell's
panel is dragged upward instead and the field sits in the band below it.

    $ adb shell am instrument --no-restart -r -w -e op script -e script "tap 540 2122" app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner
    INSTRUMENTATION_STATUS: gesture.script.cmd.0=tap 540 2122
    INSTRUMENTATION_STATUS: gesture.ok=true
    OK (1 test)
    text="bottom_field" resource-id="app.tileshell.qa.imefixture:id/mirror_focus"
    resource-id="app.tileshell.qa.imefixture:id/bottom_field" focused="true"
          mInputShown=true

Restored afterwards: `show_ime_with_hard_keyboard` back to `0`, the `/sdcard` dump files removed,
the fixture force-stopped (the shell's `StartActivity` had focus again). `app.tileshell` was not
touched.
