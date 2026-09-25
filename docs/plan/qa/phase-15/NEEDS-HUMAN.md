# Phase 15 — what only Jeremy can judge

Hard Rule 15: row 15 goes `done` only with your sign-off on every line below. Write `OK`, or `NO: <why>`, in the last
column; a NO becomes a fix and a re-run. Evidence paths are relative to `docs/plan/qa/phase-15/`. The row dirs are
refreshed by the final gate on the final build (the names stay); each earlier run is kept beside them as `<ROW>-runN`.

Kinds: **[fidelity]** = it matches r11 within r11's tolerance, judged by eye on the phone; **[accept]** = an
approximation or a design call you accept or reject.

## Phone-only checks (Galaxy S25 Ultra, the final APK sideloaded)

The emulator cannot show Samsung's keyguard, One UI's permission pages, a real microphone or a real call. Each P row is
done on the phone, and its result goes in the last column.

| ID | Do this on the phone | What should happen | Result |
|---|---|---|---|
| P1 | `adb shell appops get app.tileshell USE_FULL_SCREEN_INTENT` (note the output). Set an alarm 2 min out, lock the phone. Then repeat while using a Samsung app, with "Display over other apps" granted and again with it off. Then with Do not disturb on, and with Sleep mode on. | Locked: the screen turns on and the toast shows over Samsung's keyguard; Snooze and Dismiss work without unlocking. In use: the overlay toast (grant on) with no heads-up over it, or the heads-up (grant off); Snooze / Dismiss work. Volume follows Samsung's Alarm slider. DND and Sleep mode: it still rings. Note whether Samsung's lock screen shows the next alarm (it may show only Samsung Clock's; recorded either way). | |
| P2 | Arm an alarm for tomorrow morning and start a 30-min timer. Leave the phone idle 24 h, run Device care → Optimise, reboot, and do NOT unlock after the reboot (PIN set). | The timer ends on time; the alarm rings at its time over the keyguard before any unlock. | |
| P3 | Record a take on the phone's microphone, then one on a Bluetooth headset microphone. | Both takes play back; judge their quality under H15. | |
| P4 | Start a take; have someone call you (a real call), end it. Repeat with a Signal (VoIP) call. | The take pauses during the call and resumes or stops after it, and the file is intact, both times. | |
| P5 | With the phone locked, ask Tess to set an alarm and a timer. | Both land in the shell's Alarms & Clock (not Samsung Clock). | |
| P7 | Make a take in Samsung Voice Recorder, then open the shell's Voice Recorder. | It is listed with its duration, plays and shares; its hold menu has no rename or delete; its playback page has no trim, delete or rename. | |

P6 was superseded on 2026-09-23 (no motion value with a tolerance of 17 ms or less exists in the three r11 docs).

## NEEDS-HUMAN rows

| ID | Kind | Judge | Emulator evidence | Sign-off |
|---|---|---|---|---|
| H1 | fidelity | Alarms & Clock against r11/clock.md's HIGH / MEDIUM values: the tab header (§1), the alarm list and "No alarms" (§2), the editor and its flyouts (§3–§4), the Sounds page (4.6), the timer editor (4.8), the world-clock rows (§5), the timer and stopwatch tabs (§6–§7), the timer toast (§8.9–8.11). The LOW / UNMEASURED parts and the motion values fall under H16. | `E10/` (133 of 133 measurements pass: `tabs.png`, `alarm_empty.png`, `alarm_rows.png`, `editor.png`, `snooze_list.png`, `sound_flyout.png`, `sounds.png`, `timer_editor.png`, `stopwatch.png`, `tap.mp4`); `E8/tokyo.png`; `E4b/timer_toast.png` | |
| H2 | fidelity | Calculator against r11/calculator.md, judged against 10586: header, Standard, Programmer, the Converter page and the pane (§1–§5). Scientific, the history pane, the memory flyout, the pressed key and the text-set glyphs fall under H11. | `E13/` (145 pass: `std.png`, `prog.png`, `speed.png`, `pane.png`) | |
| H3 | fidelity | Voice Recorder against r11/voice-recorder.md's MEDIUM values: the list row (3.4), group headers (3.3), the docked button (3.8), playback's header, disc, scrubber and app bar (4.1–4.7). The LOW record-state values are flagged approximations. | `E24/` (`list.png`, `record.png`, `recording.png`, `playback.png`, `cut.mp4`) | |
| H4 | accept | The World Clock tab as a list under "Local time" with W10M's difference lines, without W10M's map (offline preferred). | `E8/london.png`, `E8/tokyo.png` | |
| H5 | fidelity + accept | The ring surface: the toast form (8.1) and the timer toast (8.9–8.11), locked and as the in-use overlay [fidelity]. [accept]: the alarm toast's LOW geometry, the English timer-toast strings, what shows beneath the locked toast, the in-use toast laid out below an app's status bar, the 0.5 dim, touches outside consumed, the 217-ms entrance, and Android's own heads-up / lock-screen fallbacks when the overlay or full-screen grant is off. | `E4/ring_locked.png`, `E4b/overlay.png`, `E4b/headsup.png`, `E4b/timer_toast.png`, `E23/locked.png`, `E23/overlay.png` | |
| H6 | accept | Ring timeout 10 min, the missed-alarm notification and "Snoozed until h:mm", ringing on boot inside the timeout. | `E32/after_timeout.png`, `E4/`, `E6c/` | |
| H7 | accept | The in-call alarm level: one eighth, with vibration. | `EDGE_ALARMS/` (the call case: rings over the dialer's call screen at 1/8) | |
| H8 | accept | The branding module's alarm sound-alikes. | `E10/sounds.png` (the list) | |
| H9 | accept | The DST and time-zone rules for alarms and timers. | `EDGE_ALARMS/`, `EDGE_TIMERS/` | |
| H10 | accept | Several timers at once, the 99:59:59 ceiling, the stopwatch across a reboot, rollover. | `EDGE_TIMERS/`, `E7/`, `E6/` | |
| H11 | accept | Calculator history kept until cleared; the Converter's twelve categories. Its units and rounding follow microsoft/calculator's tables, except the 13 factors your 2026-09-24 ruling set to the units' exact definitions (INDEX Change Log). Also: the e-notation forms and the result font's step-down from 46 to 12 epx; Scientific's geometry; the history pane; the pressed key; x², ¹⁄x, xʸ … set as Selawik text. | `E11/`, `E12/`, `EDGE-CALC/` (the 32-digit and 79-character results) | |
| H12 | accept | The recorder's call pause, storage floor, "Recording" / "Recording (2)" names, trim, rename, the delete confirmation, the static tile. | `E16/`, `E19/`, `E24/` | |
| H13 | accept | The microphone-busy wording, both directions. | `E17/` | |
| H14 | accept | The next-alarm tile face in r11 §9's 2015 form (wide: time, name, repeat days, label, bell; small: glyph and bell badge). | `E0/` | |
| H15 | accept | Recording quality on the phone's microphone and over Bluetooth. | P3 on the phone | |
| H16 | accept | Every approximation not covered elsewhere, including the two Setup rows' detail strings and Voice Recorder's AAC-LC / 44.1 kHz / 64 kbps encoding, the clock's LOW / UNMEASURED parts and motion values. | `E21/`, `E14/` | |
| H17 | accept | Tess's spoken arithmetic: the restated expression, Windows' error strings spoken, a large result in e-notation, and the reply wording. The 8 E26 audibility checks fail because the host's audio route cannot capture the emulator's replies (accepted 2026-09-24, "we are good here move on"); the reply files are there to hear. Also judge the wait: a 30-digit e-notation reply (9^1047) is silent for about 24 s while it is synthesised, and closing Tess does not stop a synthesis already under way (phase 03's speech process; EDGE_TESS). | `E26/*.wav`, `EDGE_TESS/` | |
| H18 | accept | Other apps' recordings read-only (play and share only), and the shell's own earlier takes after an uninstall-and-reinstall or Clear storage, which Android re-files as another app's and which lose their markers. | `E14/list_others.png`, `E14/menu_other.png`, `E14b/list_orphan.png`, `E14b/menu_orphan.png` | |
| H19 | accept | Date calculation's layout, wording and answers (microsoft/calculator's date engine; a week entered as 7 days). | `E29/` | |
| H20 | fidelity | Voice Recorder's search box (3.1), the "Showing <kind>" line (3.2) and the marker dots on the playback track (4.5). | `E30/search.png`, `E30/mine.png`, `E30/playback.png` | |
| H21 | accept | The recorder's pause state, the marker rows while recording and on playback, the filter's kinds All / My / Other apps' recordings. | `E30/paused1.png`, `E30/flagged.png`, `E30/recording.png` | |
| H22 | fidelity | The Clock's compare mode: the 48-epx accent hour strip in place of the app bar (r11 5.7). | `E31/compare.png`, `E31/compare_next.png` | |
| H23 | accept | The expanded timer and stopwatch views, the pinned timer / stopwatch tiles, the stopwatch Share text. | `E31/pin_timer_pinned.png`, `E31/pin_stopwatch_pinned.png`, `E31/messages_thread.png` | |
| H24 | fidelity + accept | The Sound flyout with "Pick from my music" (4.3) [fidelity]; the music picker page [accept]. | `E10/sound_flyout.png`, `E31/music_flyout.png`, `E31/music_picker.png` | |
| H25 | accept | Alarms & Clock answering other apps' set-alarm / set-timer / show requests, and Android's one-time "which clock" prompt while Samsung Clock is installed. | `E33/`, `E31/chooser.png` | |
| H26 | accept | The Clock bars' Select and More (About; Notification settings → Setup; no Send feedback), the About pages, and the `converter` shortcut's last-used category. | `E10/more.png`, `E10/select.png`, `E27/`, `E12/` | |
| H27 | fidelity + accept | The Sounds page (4.6) and the timer editor (4.8) [fidelity]; Voice Recorder's level rings [accept]. | `E10/sounds.png`, `E10/timer_editor.png`, `E24/recording.png` | |

## Already ruled (for reference; not for sign-off)

- 2026-09-24: where Windows is wrong, the right answer wins. Shifts past the word size give 0 (−1 for a negative
  arithmetic shift), and 13 converter factors are exact. BYTE 255, weeks as 7 days and the "Invalid input" paste stay as
  Windows. See INDEX Change Log "WHERE WINDOWS IS WRONG".
- 2026-09-24: E26's reply-audibility checks stay failing for a host-audio reason ("we are good here move on").
