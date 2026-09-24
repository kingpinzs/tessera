# E10 — DEFECT: the Sounds page's last rows can never be scrolled into view

r11/clock.md 4.6: the Sounds page lists the branding sound-alikes and the image's `RingtoneManager.TYPE_ALARM` tones.
The build lays the list out as `LazyColumn(Modifier.offset(y = 170.dp).fillMaxSize() …)` (AlarmTab.kt:461): the
column is sized to the whole page and then moved 170 epx down, so its last 170 epx — the last two or three 60.4-epx
rows — sit below the screen's bottom edge and the list reaches its end before they come up. On this image the page
lists 22 sounds (5 brand + 17 tones from `content query --uri content://media/internal/audio/media --where
"is_alarm=1"`); the last two, "Rooster Alarm" and "Scandium", are never reachable. The same construction is on the
music picker (`offset(y = 106.dp).fillMaxSize()`, AlarmTab.kt:523) and the city search results
(`offset(y = 60.dp).fillMaxSize()`, WorldClockTab.kt:232).

## Repro (emulator-5558, build 39402722396d9129; E10 run 2, sounds_1..sounds_5.xml)

    adb shell am start -n app.tileshell/.clock.ClockActivity --es page alarm
    # Add → Sound → Pick from ringtones; then swipe the list up until it stops moving; dump:
    adb shell uiautomator dump /sdcard/qa.xml; adb shell cat /sdcard/qa.xml | grep -o 'sounds_row:[^"]*'

Output: from the third swipe on every dump is identical (tone.211 … tone.213 at y 594 → 2139), and the rows
`sounds_row:tone.218`… hold 20 of the 22 names; the two last tones never appear. The row's assertion `4.6 the rows'
names are the brand sound-alikes plus the image's TYPE_ALARM tones` fails on the product (20 of 22 listed).
