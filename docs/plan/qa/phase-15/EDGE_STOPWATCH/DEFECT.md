# EDGE_STOPWATCH — DEFECT: once the laps list outgrows the screen, a new lap is not shown on top

r11/clock.md 7.4 and the build's own comment (`clock/StopwatchTab.kt:140`): the laps list is "newest on top". From
the 7th lap on (six 64-epx rows fill the list's viewport on this AVD), a new lap is added above the viewport and the
list keeps showing the lap that was on top before: the user sees lap 6 at the top however many laps they take, and
must drag the list down to find the newest one. The laps ARE stored and listed (a drag down shows them) — only the
list's scroll position is wrong.

Build: app-debug.apk md5 33cc71c192190cc9, emulator-5558. The row (`EDGE_STOPWATCH/EDGE_STOPWATCH.txt`, 15 passed,
2 failed): with 1,006 laps in the store, the gesture-driver dump of the tab lists `stopwatch_lap:6 … 2` and no
`stopwatch_lap:1006` (screencap `EDGE_STOPWATCH/thousand.png`: the stopwatch at 00:02:07.50 running, laps 6 → 1 on
screen). Its second FAIL, "the list scrolls (older laps came in)", follows from the first: the view is already at the
list's oldest end, so a swipe toward older laps brings nothing new.

## Minimal repro (10 laps, no gesture script)

    export ANDROID_SERIAL=emulator-5558
    adb shell am start -W -n app.tileshell/.clock.ClockActivity --es page stopwatch
    # tap Play (stopwatch_play), then tap the Flag (stopwatch_lap) ten times, 0.8 s apart, dumping after each
    # (the tab never idles: docs/plan/qa/phase-15/scripts/p15.sh gdump)

Output (the stopwatch.json lap count, then the `stopwatch_lap:<n>` ids in the dump, top first):

    after lap 1 (store 1): listed 1
    after lap 2 (store 2): listed 2 1
    after lap 3 (store 3): listed 3 2 1
    after lap 4 (store 4): listed 4 3 2 1
    after lap 5 (store 5): listed 5 4 3 2 1
    after lap 6 (store 6): listed 6 5 4 3 2 1
    after lap 7 (store 7): listed 6 5 4 3 2 1
    after lap 8 (store 8): listed 6 5 4 3 2 1
    after lap 9 (store 9): listed 6 5 4 3 2 1
    after lap 10 (store 10): listed 6 5 4 3 2 1
    after a drag down on the list: listed 10 9 8 7 6 5 4

## Cause (source)

`StopwatchTab.kt:147-148`: `LazyColumn(…) { itemsIndexed(sw.laps.asReversed(), key = { i, _ -> sw.laps.size - i }) … }`
with a default `rememberLazyListState()`. A keyed LazyColumn keeps its first visible item (by key) in place when items
are inserted before it; while all laps fit, the scroll offset clamps to 0 and the new lap shows, but once they overflow
the anchored lap (6 here) stays on top and every newer lap lands above the viewport. Nothing scrolls the list back to
index 0 when a lap is added.
