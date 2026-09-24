# E11 run 2: two product defects (both fixed)

Run 2 was on the APK with run 1's redraw fix: 237 passed, 4 failed (E11.txt). Standard 79/80, scientific 87/87,
programmer 64/64; the failures were standard-076 and the three paste assertions.

## 1. Paste crashed the whole shell process

After a tap on `calc_paste` the Calculator was gone and Start showed (after_paste.xml carries `start_page` and no
`calc_display`). `adb logcat -b crash`:

    E AndroidRuntime: Caused by: java.lang.IllegalArgumentException: UNICODE_CHARACTER_CLASS flag not supported
    E AndroidRuntime:     at java.util.regex.Pattern.compile(Pattern.java:947)
    E AndroidRuntime:     at app.tileshell.calc.engine.CopyPaste.fullMatch(CopyPaste.kt:38)
    E AndroidRuntime:     at app.tileshell.calc.engine.CopyPaste.<clinit>(CopyPaste.kt:40)

The port compiled its paste patterns with `Pattern.UNICODE_CHARACTER_CLASS` to get .NET's Unicode `\d` and `\s`. The
JVM accepts that flag, so the unit tests passed. Android's ICU-backed regex rejects it, so the first paste threw in
the class initializer and took the launcher's process down with it.
**Fix:** the two classes spelled out exactly as .NET defines them (`\d` = `\p{Nd}`, `\s` = `[\f\n\r\t\v\x85\p{Z}]`)
and no flag. They mean the same on both platforms. `:calc` 98/98.
**After the fix, on emulator-5556:** "12+3" copied from the fixture app and pasted gives `calc_display` 3 and
`calc_expr` "12 +", with no crash in the crash buffer.

## 2. A tap on a key enabled one frame earlier was dropped (standard-076)

`mc 5 mminus mr` showed 5 where the oracle says -5. The JVM oracle (including its sequential run) gives -5. On the
device, with line 075 (memory holding 10) pressed just before, the batched taps land about 15 ms apart:
- repro/rate.py, 30 trials of 075 then 076: **15 failures**, all on 076.
- repro/which.py, 16 trials, reading the memory flyout after each failure: **5 failures, memory = [-5] every time.**
  MC and M- both landed; only MR's tap was lost.

Cause: `KeyCell` accepted a tap only if `enabled` was true in the last composed frame. MC disabled MR, and M-
re-enabled it in the engine, but the frame that would redraw MR as enabled had not run when MR's tap arrived.
**Fix (at the producer):** KeyCell forwards every completed tap, and the callee decides against the state as it is
at that moment. `Calculator.press` already refuses a disabled key. The flyout toggle and the two Delete actions
(`clearHistory`, `memoryClearAll`) check their own conditions when they run. `CalcModel.press` skips the save and
redraw when the engine refuses a key, so a tap on a disabled key still changes nothing.
**After the fix:** repro/rate.py, 30 trials: **0 failures**.

Run 3 is `../E11/`.
