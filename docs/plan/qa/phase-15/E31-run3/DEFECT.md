# E31 (Share, three laps) — the same DEFECT as E7/DEFECT.md: taking a lap crashes Alarms & Clock

Every lap crashes the app: `StopwatchTab.kt:106` builds the lap row with `buildAnnotatedString { … check(toString() ==
line) … }`. Inside the builder `toString()` is `AnnotatedString.Builder`'s object identity, never the text, so the
check always throws `IllegalStateException: lap row text must equal the shared line` the moment a lap row is
composed — the first Flag tap on a running stopwatch, and every later open of the Stopwatch tab while the store
holds a lap (the row is composed at open). E7's Lap clause, E31's Share clause (three laps) and the stopwatch edge
cases (1,000 laps; killed between laps) all stop here.

## Repro (emulator-5558, build 39402722396d9129 / 337842979 bytes)

    adb shell am start -n app.tileshell/.clock.ClockActivity --es page stopwatch
    # tap the ring play button (stopwatch_play), then the Flag button (stopwatch_lap)
    adb shell dumpsys dropbox --print data_app_crash | grep -A20 'Process: app.tileshell' | grep -E 'Exception|StopwatchTab'

Output (E31 run 1, 2026-09-24 08:33:16 and 08:34:52 — two crashes, one per lap tap):

    java.lang.IllegalStateException: lap row text must equal the shared line
        at app.tileshell.clock.StopwatchTabKt.lapText-ZkgLGzA(StopwatchTab.kt:106)
        at app.tileshell.clock.StopwatchTabKt.access$lapText-ZkgLGzA(StopwatchTab.kt:1)
        at app.tileshell.clock.StopwatchTabKt$StopwatchTab$lambda$5$1$0$$inlined$itemsIndexed$default$3.invoke(LazyDsl.kt:560)

The store did take the lap (`stopwatch.json` `laps: [101180]`), so the Stopwatch tab then crashes on every open until
the store is cleared; the drivers clear `stopwatch.json` as their baseline restore (noted in their logs), never to
pass a clause.
