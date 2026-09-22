# Phase 05 — NEEDS-HUMAN rows

Hard Rule 15: anything only Jeremy can verify gets a row here, and `done` requires his sign-off on each
one. Nothing below is a defect; every line is a choice the footage could not settle (LOW candidates and
approximations, phase doc Decisions), recorded with where to look at it. The measured values (HIGH /
MEDIUM) are E3's and are not repeated here.

How to look: install the build, open any text field (Start settings' search, Messages, the fixture app),
and use the keyboard. Every capture named below is in this folder.

| H | What Jeremy judges | Why it is his call | Where to look |
|---|---|---|---|
| H1 | Typing feel: key response, popup, autocorrect aggressiveness | "Does it type like W10M" is not a number. One thing seen in QA: after "bec" the bold candidate is "be", so a space there would autocorrect a prefix to a shorter word | live; `E5/E5.txt` (strip after each keystroke) |
| H2 | Any approximation not covered by H3–H23: the symbol pages' contents, number fields opening on the digits page, the landscape keyboard's height, backspace auto-repeat, the function-key glyph size, the voice key's "Listening…" line, the text-emoticon list | none of these is in R6 | `E2/`, `EDGE2/edge2_landscape.png`, live |
| H3 | Word Flow trail: accent, ≈9.4 epx wide, comet tail, stays ≈100 ms then retracts | R6 §2.4 is one 2015 recording (LOW) | live (swipe any word); `E4/e4_after.png` |
| H4 | Cursor-dot drag visuals: keyboard dims to 50 %, four chevrons, an accent line to the finger | R6 §2.5.7 LOW (2015) | `E6/e6_held.png` |
| H5 | Cursor-dot stepping: a direction-locked joystick, ≈150 ms per character, ≈750 ms per line | R6 §2.5.8 LOW | live; `E6/E6.txt` |
| H6 | Emoji panel structure and category row, and which emoji sit under which category (animals with smileys, plants with food, objects with celebration) | R6 §2.6.1–2.6.2 LOW; the grouping follows the Windows touch keyboard of the time | `EDGE1`/live; `E7`, `E11/e11_off.png` |
| H7 | Emoji cell geometry: 4 rows over key rows 1–3, columns at width / 7.5, artwork at 55 % of the cell | R6 §2.6.4 UNMEASURED | `E11/e11_off.png` |
| H8 | Phone-number keypad: 3 × 4 with letter sub-labels and a function column (⌫, space, &123, ↵) | R6 §2.8.4 LOW (2015) | `E2/` (phone field), live |
| H9 | Go / Send / Next / Done and email fields: Go is the URL field's white →, the rest the grey ↵, email the default row | R6 §2.8.5 UNMEASURED | `EDGE1/edge1_enter_*.png` |
| H10 | **The system's show / hide slide, because the keyboard cannot own it.** Measured: in, at rest 167 ms after its first frame with the first frame 33 % opaque (it fades in); out, 117 ms while fading. W10M: in 250 ± 33 ms, out ≈133 ms, no fade | Android runs the IME window's slide from the focused app's InsetsController; an input method has no API that sets it (build-start check, Decisions "Show / hide") | `E3M/E3M.txt`, `E3M/e3m_show1.mp4`, `E3M/e3m_hide1.mp4` |
| H11 | Keyboard colours: the build uses R6's capture rendition — panel (22,27,21), keys (48,48,48), function keys (73,74,72) | the capture's colours, not W10M's true ones | `E3/e3_letters.png` |
| H12 | Key sound and vibration: the synthesised click (an original sound-alike; a real Microsoft file can be dropped in as assets/brand/key_click.wav) and KEYBOARD_TAP, both On | no R6 source | live |
| H13 | Keyboard move range: from rest up to one key block (865 phys) higher | approximation | `E9/e9_raised.png` |
| H14 | Cursor-controller setting labels: "Right handed usage" (R6) and "Left handed usage" (agent pick) | R6 §2.5.5 LOW | Start settings > Keyboard |
| H15 | Left-handed dot position: the n/m, space/period and row 3/4 gaps, 1077.5 phys | derived, not measured | `E10/e10_left.png` |
| H16 | Right-handed as the default | approximation | Start settings > Keyboard |
| H17 | "Switch back to letters after I type an emoticon" On by default | K1's checked box is one user's choice | Start settings > Keyboard |
| H18 | Switching back to the letters after one emoji | R6 §2.6.5 LOW | `E11/e11_on.png` |
| H19 | Long-press alternates popup: accent, one cell per character at the key pitch, the plain letter first over the key, the selected cell white with an accent glyph | approximation | `E12/e12_alt.png` |
| H20 | One-handed options popup (dock left / full / dock right), the 0.80-width docked layout with the restore glyph, and the long-press timeout that opens it | approximation | `E12/e12_onehanded.png`, `E12/e12_dock_left.png`, `E12/e12_dock_right.png` |
| H21 | &123 slide-to-type: starts once the finger passes the touch slop before the long-press timeout | approximation | live |
| H22 | Caps lock: Shift accent-filled with a white arrow; the 300-ms double-tap window | approximation | `E12/e12_capslock.png` |
| H23 | Double-space period and its 1100-ms window | approximation | live; `E12/E12.txt` |
