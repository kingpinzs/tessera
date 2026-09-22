# Phase 05 QA gate — Reviewer 1: DESIGN AND CORRECTNESS

Reviewer: Fable 5.1 (reviewer 1 of 2; codex out). Date 2026-09-22. Read-only: no code, docs, evidence or
emulator touched. Brief: `docs/plan/review/2026-09-22-phase05-gate-brief.md`. Contract: the Decisions,
E1–E12, edge cases and NEEDS-HUMAN rows of `docs/plan/phase-05-keyboard.md` (FINAL), with R6 §2
(`docs/plan/r6-measurements.md` l.172–316) as the measurement source.

**This file holds PART 1 only** — the implementation judged against the Decisions, plus the bug hunt in the
IME, the speech change and the main-process guard. PART 2 (the per-criterion evidence verdict against the
final pass's logs, and the overall GATE verdict) is appended when the final pass's logs are handed over.
Nothing here is a PASS/FAIL on E1–E12: a code read is not evidence.

Method: every Decision line was read against the code that implements it, and every number the Decision
cites was recomputed from `KeyGrid` / `Layouts` / `KeyboardMetrics` by hand (the values are plain
constants and grid arithmetic, so they can be). Then the touch state machine, the editor bookkeeping,
the service lifecycle, the speech ownership and the process guard were walked for inputs that produce a
wrong result. Line numbers are those of the tree at `bef3d55` (HEAD). **Tree state at review time:** the
working tree carries UNCOMMITTED edits to `ime/Editor.kt` (HEAD blob `e0f34f4` → `195618a`) and
`ime/KeyboardService.kt` (`8f94472` → `f9474df`), made by someone else while this review ran — they redact
password-field text from the `:ime` diagnostics ring (see MAJOR-2). Everything below was read at the HEAD
blobs; where the working-tree edit matters it is said.

---

## 1. Decisions, line by line

Verdict key: **MATCHES** — the code does what the line says with the numbers it cites (recomputed, given);
**MATCHES (approx, Hn)** — an approximation the Decision itself declares, carried by its NEEDS-HUMAN row;
**DEVIATES** — the code does something the line does not say (all deviations are also findings in §2).

| Decision (2026-09-16/17) | Where in the code | Verdict |
|---|---|---|
| Shell-owned IME in the one APK; FlorisBoard code may be reused | `AndroidManifest.xml:248-258` (`.ime.KeyboardService`, `BIND_INPUT_METHOD`, `:ime`); `res/xml/method.xml`. No FlorisBoard code was taken (BUILD-START.md §3 records the boundary; the engine is original) | MATCHES |
| One permanent keyboard route | one IME, one manifest entry | MATCHES |
| Features C: Word Flow, suggestions + autocorrect (offline dictionary), cursor dot, emoji, key sounds + vibration, offline voice typing via phase 03's engine | `KeyboardController.kt` (swipe 437–472, dot 503–530), `EngineBrain.kt`, `EmojiPanel.kt`, `KeyFeedback.kt`, `KeyboardService.kt:332-398` | MATCHES |
| Dictionary permissive, non-Google, verified at build start; Fluent Emoji (MIT) in the branding module, inserted as Unicode; depends on phase 03 | `BUILD-START.md` §1–2 (SCOWL 2020.12.07 + 12dicts, Fluent at a pinned commit); `EmojiCatalog.kt:51` (`Brand.EMOJI_ARTWORK_DIR`), `KeyboardService.kt:294` commits `action.text` (Unicode) | MATCHES |
| R3 has no keyboard row; every value from R6 §2; E3 measures only numbered values | all constants in `KeyGrid.kt` cite an R6 row | MATCHES |
| Test fixture app mirroring text + selection | `testapps/ime-fixture` (README.md describes `read_mirror`) | MATCHES |
| **Key grid** — phys on a 1440 panel scaled by display width / 1440; pitch 144; row 1 keys 130, gap 14, margins 5 / 9; row 2 keys 128, gap 16, insets 77.5 / 82; shift + backspace 201; z…m on s…k columns; row 4 = 201 · 128 · 128 · 560 · 128 · 201; key height 202; row pitch 217.5; gap 15; bottom margin 7; block 865; labels x-height 41 / caps 48; "&123" digits 40 tall, 112 wide; grip 59 × 18, top 20; three hold dots; colours (22,27,21) / (48,48,48) / ≈(73,74,72) / white | `KeyGrid.kt:19-63` (144 / 130 / 5 / 9 / 128 / 77.5 / 202 / 217.5 / 7); `KeyboardMetrics.kt:35` (`sx = width/1440`). Recomputed: row-1 right margin 1440 − (5 + 9·144 + 130) = 9 ✓; row-2 right inset 1440 − (77.5 + 8·144 + 128) = 82.5 ✓; shift 205.5 − 5 = 200.5 ✓ (201 ± 2); backspace 1431 − 1229.5 = 201.5 ✓; row 4: 200.5 · 128 · 128 · (1069.5 − 509.5 = 560) · 128 · 201.5 ✓; vertical gap 217.5 − 202 = 15.5 ✓; block 3 × 217.5 + 202 + 7 = 861.5 ✓ (865 ± 5, the comment at `KeyGrid.kt:30-34` says why it is built, not copied); labels `KeyboardView.kt:246-257`, "&123" `214-220` (condensed to 112), grip `206-213`, dots `221-227`; colours `KeyColors` `60-69` | MATCHES |
| **Suggestion strip** — 46.5 epx tall, panel colour, no separator / dividers; cap height 13.3 epx (Selawik to that cap); 26 epx between items; first item 13 epx in; mic glyph 13.8 × 20.3 leading, first word 28.6 epx right of it; bold autocorrect first; tapping a word → original + alternatives, "+ word" / "– word", pressed item accent-filled; swipe left for more | `KeyboardMetrics.kt:86` (46.5), `KeyboardView.kt:543-552` (13.3/CAP_RATIO, 13, 26, 28.6, 13.8), strip `451-540` (ink-placed, `horizontalScroll` 462, pressed fill 519), bold `695`; tap handling `KeyboardController.kt:622-655`, tapped-word mode `698-710`. Mic size: `MIC_EPX = 24` with the ink ≈ 85 % of the em — a fit to the number, E3's to confirm | MATCHES |
| **Key press popup** — pressed key accent; 173 × 233 rectangle, larger white glyph (x-height 59.5), bottom 7 above the key, top 25 above the row above; row-1 popups cover the strip and rise above the panel; no tail / shadow / corners; full size 17–50 ms after touch-down, no animation; next key's popup replaces it; function keys only fill | `KeyGrid.kt:96-100, 117`; `KeyboardView.kt:358-380`; overlay canvas above the strip `152-156`; headroom `KeyboardMetrics.kt:64-67`; `press()` `KeyboardController.kt:331-337` (popup only for CHAR + DARK, set synchronously on DOWN — one frame); rollover `159-162`. Recomputed top-of-popup to top-of-row-above: 217.5 − 7 − 233 = −22.5 → 22.5 above ✓ (25 ± 4) | MATCHES |
| **Cursor dot** — core 21 / disc 56 / ring 87; centre 358 from the left, 218 above the nav bar, at the row 3/4 · z/x · emoji/comma crossing (same in the URL row); drag visuals LOW (dim 50 %, four chevrons, inner edges 105–126, accent line ≈15) H4; stepping LOW (joystick, 150 ms/char, 0.5–1 s/line, release leaves the caret) H5 | `KeyGrid.kt:68-91`: x = (349.5 + 365.5)/2 = 357.5 ✓, y above nav bar = 861.5 − 644.75 = 216.75 ✓; URL row keeps the same columns (`Layouts.kt:141`) ✓. Drag: `KeyboardView.kt:326-337`, stepper `KeyboardController.kt:503-530` (150 / 750 ms, DPAD keys). Chevron placement: see MINOR-6 | MATCHES; drag visuals MATCHES (approx, H4) with one geometry note |
| **Word Flow trail** LOW — flat accent line 37 phys with round ends, comet whose tail is erased, lag ≈170–280 ms; after lift stays ≈100 ms, retracts ≈230–270 ms, gone ≈330–370; strip shows candidates bold first; H3 | `KeyboardView.kt:340-355` (37, Round caps, PathMeasure retract), `KeyboardController.kt:444-454` (comet by LENGTH, 1000 phys ≈ R6's 240 video px, not by time), `478-494` (100 + 250 ms), `468-471` (candidates, bold first) | MATCHES (approx, H3) — the lag is length-based, a stand-in for the time figure |
| **Emoji panel** LOW — replaces the four key rows, strip stays; 4 rows in the key-row 1–3 space, scrolls horizontally, ≈7.5 columns; 10-cell category row at key pitch in row 4's space, in the listed order, active accent-filled, abc/backspace grey; H6; cells ≈159 pitch × 192 wide; H7 | `EmojiPanel.kt:43-46` (grid top = row 1, height = rows 1–3 = 637 → 159.25 per row; width 1440/7.5 = 192), `60` (4 fixed rows, horizontal), `83-124` (order abc · recent · smileys · people · celebration · food · travel · symbols · text · backspace at `row1Left(i)`, 130 wide; fills 102–106) | MATCHES (approx, H6/H7) |
| **Show / hide** — IME-owned slide if the build-start check allows; else the system inset animation, result recorded, H10, E3 checks only what the IME draws | Build-start result: the IME cannot own it (commit 9ce189a; `NEEDS-HUMAN.md` H10 l.22; `E3M/E3M.txt` l.28-31; INDEX.md l.45 status cell). The code draws no slide of its own. The Decision says "the result is recorded here" and the FINAL doc changes "only via a dated INDEX.md Change Log entry" — there is no Change Log entry (INDEX.md §Change Log has none for phase 05): MINOR-11 | MATCHES (failure branch taken); the record is in the wrong place |
| **Bottom row per field** — default `&123 · ☺ · , · space · . · ↵` grey Enter; search: white action key, dark magnifier, 201; URL: `.com` (201, x-height 28) · space 418.6 · period 201 · white →; phone: 3 × 4 keypad + backspace column LOW H8; Go → white →, Send/Next/Done → grey ↵, email → default row H9 | `Layouts.kt:124-166`. Recomputed URL row: `.com` 1.5 × 144 − 16 = 200 ✓; space 3 × 144 − 16 = 416 (418.6 ± 3 → lower bound 415.6 ✓, by 0.4); period 1213.5 − 1013.5 = 200 ✓. `FieldInfo.kt:65-107` maps input types; `enterKey` `159-166`; keypad `210-226`; `.com` x-height `KeyboardView.kt:247-250` | MATCHES; the URL space bar sits at the edge of its tolerance (a grid consequence, not a defect) |
| Colour tolerance ± 32 (E3); H11 | E3's business; the code draws the capture rendition exactly (`KeyColors`) | MATCHES |
| **Key sounds + vibration** — original click from the branding module, `KEYBOARD_TAP`, each with a Settings toggle, both On; H12 | `brand/KeyClick.kt` (synthesised WAV, drop-in `assets/brand/key_click.wav`), `KeyFeedback.kt:49-57`, `KeyboardPage.kt:25-26`, defaults `KeyboardConfig.kt:24-26`; read on every show `KeyboardService.kt:234-235` | MATCHES |
| **Moving the keyboard** — hold space + drag moves strip and keys, stays where dropped; range rest → +865 phys H13; transparent non-touchable band via `onComputeInsets` (UNVERIFIED, build-start) | `KeyboardController.kt:192-195, 227, 246-255, 298-301, 429-433`; `KeyboardMetrics.kt:58, 70, 88` (865); store `ImeStore.kt:25-27` (phys); reload `KeyboardService.kt:184, 244`; insets `200-216` (touchable region = panel + nav strip, content insets = panel top; window background transparent `155`). The UNVERIFIED item is verified by E9 run 3 (README.md l.82-83), not by a build-start note | MATCHES |
| **Handedness** — Settings "Cursor controller" with "Right handed usage" / "Left handed usage" (H14); right = 358; left = 1077.5 ± 3, 218 above the nav bar (H15); default right (H16) | `KeyboardPage.kt:29-31`; `KeyGrid.kt:88`: (1069.5 + 1085.5)/2 = 1077.5 ✓, same y; default `KeyboardConfig.kt:28` | MATCHES |
| **Switch back to letters after an emoticon** — checkbox; while on, one emoji returns to the letters (H18); default On (H17) | `KeyboardPage.kt:33-35`; `KeyboardService.kt:299-303`; default `KeyboardConfig.kt:30`. In a PHONE field the return goes to QWERTY, not the keypad: MINOR-9 | MATCHES (one field-type wrinkle) |
| **Voice key binds `:speech`** (an ADD to its clients); the IME never loads a model | `KeyboardService.kt:343-348` (`SpeechClient.bind(this, includeCapabilities = true)`), no model code in `ime/`; `ISpeech.aidl:13-19`; the service keeps a `RemoteCallbackList` `SpeechService.kt:99-105` | MATCHES |
| **English (US) only** — one layout, one dictionary, one model; no language key, no space-bar switch | `method.xml` one `en_US` subtype; `Layouts.kt` one letter layout; `EngineBrain.kt:65` one TSV; space bar carries only the grip | MATCHES |
| **R2D-12 behaviours in**: long-press alternates (H19), one-handed dock (H20), &123 slide (H21), caps lock by double tap (H22), double-space period (H23), word learning (never in passwords) | see the six stand-ins below; learning gate `engine/FieldKind.kt:29` (`PASSWORD.learns = false`), `UserDictionary.kt:60-61` | MATCHES |
| **Stand-in (1)** &123: tap → symbols; move past `scaledTouchSlop` before `getLongPressTimeout()` → slide, symbol layer at once, lift over a key commits and returns to letters, lift on &123 commits nothing; hold still → one-handed options | `KeyboardController.kt:55-58` (platform values), `191`, `226`, `419-427`, `287-293`, `392-398`. A drifted tap (> slop, lifted on &123) flashes the symbol layer and returns: MINOR-3 | MATCHES |
| **Stand-in (2)** one-handed: popup in the press-popup's form above &123, three glyph cells at 144 pitch (dock left · full · dock right), slide + lift picks, lift elsewhere nothing; docked = 0.80 width (pitch 115.2), heights kept, flush to the edge, freed band panel-coloured with one glyph whose tap restores; persists | `KeyboardView.kt:396-416` (three cells, 233 high, no tail/shadow/corners), `KeyboardController.kt:401-417`, `KeyboardMetrics.kt:33-46, 85` (`sx *= 0.80`, `sy` untouched, `offsetX`, `freeBand`), band `KeyboardView.kt:141-151`, persist `ImeStore.kt:29-31` | MATCHES |
| **Stand-in (3)** alternates: after the long-press timeout, 233-high popup, one cell per character at key pitch, plain letter first over the key; slide + lift commits; no alternates → normal popup only | `KeyboardController.kt:369-389, 279-286`; `KeyboardView.kt:383-393`; `engine/Alternates.kt` | MATCHES |
| **Stand-in (4)** shift: one tap fills the arrow white; second touch-down within `getDoubleTapTimeout()` (300 ms) locks caps, shown accent-filled with a white arrow, until tapped again | `engine/ShiftState.kt:35-45` (judged on MotionEvent down times, `KeyboardController.kt:538-544`), glyph `KeyboardView.kt:200-204`, fill `188-190` | MATCHES |
| **Stand-in (5)** double-space: second space within 1100 ms, directly after a letter, → ". "; never after a digit, a period, or in URL / email | `engine/SpaceRules.kt:15, 30-38`; `FieldKind.kt:27-28` (URL, EMAIL off; PASSWORD off is a declared agent pick, l.16-18); `KeyboardController.kt:573-587` | MATCHES |
| **Stand-in (6)** learning: an unknown word is offered once committed twice outside password fields; "– word" removes | `UserDictionary.kt:60-75, 87-91` (two commits, remove drops both counts); "+ word" `78-84`; strip `KeyboardController.kt:625-637, 704-708` | MATCHES |
| **Settings page + checklist rows** (build task 9) | `settings/KeyboardPage.kt` (4 controls); `onboarding/Checklist.kt:59-72, 121-128` ("Keyboard enabled" via `enabledInputMethodList`, "Keyboard selected" via `currentInputMethodInfo` — the id is `ComponentName.flattenToShortString()`, which is how `InputMethodInfo.getId()` is built, so the comparison is exact) | MATCHES |

**Sum:** every Decision line is implemented, and every HIGH/MEDIUM number the Decisions cite is reproduced by
the grid arithmetic within its tolerance. No DEVIATES. The approximations all carry their H-row (H2 is the
declared umbrella for the landscape height, the number-field page, the symbol pages, backspace repeat,
glyph sizes). What follows are correctness findings from the bug hunt, not Decision misses.

---

## 2. Findings

### BLOCKING

None found.

### MAJOR

**MAJOR-1 — A second finger is routed by where the FIRST finger landed, and a second DOWN is never checked
against the key block. Two thumbs produce wrong text.**
`KeyboardService.kt:442-454` decides `ownGesture` on `ACTION_DOWN` only and then hands every later pointer of
that gesture to the controller (or to Compose) wholesale. `KeyboardController.kt:156-171` (`down()`, reached
for `ACTION_POINTER_DOWN` too) never calls `inKeyBlock`; it goes straight to `keyAt()`, and `Layouts.kt:56`
(`Layout.hit`) accepts any y from `−ROW_PITCH/2` (−108.75 phys = 81.5 px above row 1, i.e. the lower 58 % of
the 139.5-px strip) and any x at all (nearest key).
Concrete inputs, on the emulator's geometry:
- Hold a letter key with one thumb (a slow typist's finger still down on "a") and tap a suggestion in the
  strip with the other. The strip tap is swallowed (Compose never sees the pointer) AND the nearest row-1 key
  under that x — "t", "y", "u"… — is committed on lift (`up()` → `commitKey`, l.268). Wrong letter, no
  suggestion.
- Docked left, hold a key, tap the restore glyph in the freed band with the other thumb: the nearest right-hand
  key ("p" / "l" / "m" column) is committed and the band tap is lost.
- The mirror: rest one thumb on the strip (or mid-tap on a suggestion) and tap a key with the other: the key
  goes to Compose, which has nothing there, and the keystroke is silently dropped.
The same looseness lets a `SYMBOL_SLIDE` (`move()` l.236-244) that drifts up into the strip's lower half
"press" and, on lift, commit a row-1 symbol. Fix at the producer: route per pointer (decide key-block
ownership on every `ACTION_POINTER_DOWN`) and have `down()` refuse a point `inKeyBlock()` rejects.
Why MAJOR: the touch state machine is built for multi-touch by its own doc comment (`KeyboardController.kt:21-24`)
and the phase's row E2 is "types correctly"; this produces wrong characters from an ordinary two-thumb hold.
Not BLOCKING because it needs two simultaneous pointers and no acceptance row drives that.

**MAJOR-2 — At HEAD, every character typed into a password field is written in clear to the `:ime`
diagnostics ring.**
`Editor.kt:78` (`commit`) logs `commit "s" (key s)` for every keystroke, `Editor.kt:89` (`replaceAround`) logs
the replacement text, and nothing in `Editor` or `KeyboardService` knows the field is a password; the ring
(4000 entries, `diag/Diagnostics.kt:15`) is printed by `KeyboardService.dump()` (l.416-427) through
`adb shell dumpsys activity service app.tileshell/.ime.KeyboardService` — the very command the QA drivers
use (README.md l.51-52). Concrete input: type a password into any password field, or the keyguard (edge case
"Keyguard password entry", P2); `dumpsys` then prints it one character per line, in order, and it stays there
until 4000 later entries push it out. `dumpsys` needs `DUMP` (adb / shell only) and the Settings > Diagnostics
page reads the MAIN process's ring, so the exposure is adb and any evidence file a driver pastes the ring into
(the committed E2 / EDGE2 logs happen not to contain `commit "` lines — checked — so the evidence itself is
clean at HEAD). The FieldInfo doc comment (`FieldInfo.kt:30`, "a password must land exactly as typed") and the
edge case "Password fields: no suggestions, no learning" both express the intent that password text is not
retained anywhere; the ring retained it.
State at review time: an UNCOMMITTED working-tree fix exists (`Editor.secret`, set from `field.isPassword` in
`onStartInputView`; `quote()` prints `(n hidden)`, the reason prints "password field"; the emoji and voice
lines are hidden too). Reviewed it: it closes `commit`, `replaceAround`, `backspace` (already count-only), the
emoji code points and the voice final. **Residual after the fix:** `KeyboardController.kt:381` logs
`alternates for e: e è é ê …` on a long-press in ANY field — in a password field that names one character of
the password each time the user holds a key for an accent (H19 says the popup opens in password fields too;
nothing gates it). One more line to gate on `state.field.isPassword`. Whether the final pass's APK carries the
fix is a PART 2 question (each log header records the installed-APK match); at HEAD the defect stands.
Not BLOCKING because the ring is adb-only, no acceptance row asserts the ring's contents for a password, and
the fix is in hand; MAJOR because it contradicts the phase's own password rule and P2 puts the keyguard
password through it.

### MINOR

**MINOR-1 — `Editor` treats the first selection report after a `restartInput` as "not mine" and flips the strip mode.**
`Editor.kt:34-39` clears `pending` on `reset`; `InputMethodManager` re-reports the (unchanged) selection once
after every `restartInput`, so `selectionChanged` (l.42-49) returns false → `selectionMoved(false)` →
`refreshStrip(typing = false)` (`KeyboardController.kt:660-666`). Input: an app that calls `setText()` from a
TextWatcher while the caret sits at the end of a word (a formatter, a draft restore) — the strip switches from
"suggestions for the word being typed, bold first" to the tapped-word set (original / "+ word" /
alternatives, none bold) although the user never tapped the text. Cosmetic and rare; noted because it is the
one hole in an otherwise sound in-flight scheme (`pending` as a FIFO of expected carets, l.13-19, is right).

**MINOR-2 — Two swipes inside ≈350 ms fight over `state.trail`.**
`retractTrail` (`KeyboardController.kt:478-494`) keeps a Handler tick writing `state.trail = Trail(oldPoints, f)`
every 16 ms for 350 ms after lift, and its last tick sets `state.trail = null`. A new swipe started inside that
window writes `state.trail = Trail(newTail)` per sample (l.232-233); the two alternate on screen, and the old
ticker's final `null` erases the live trail until the next MOVE sample. Input: the edge case "very fast swipes"
— swipe "the", lift, swipe "cat" within a third of a second. Decode is unaffected (the path is per track);
the trail is a LOW candidate under H3. Fix: give the retract a generation the next `startSwipe` bumps.

**MINOR-3 — A tap on &123 that drifts past the touch slop does nothing.**
`move()` l.226 starts the symbol slide once `moved > touchSlop` (≈22 px at 450 dpi), and `up()` l.287-293
returns to LETTERS when the lift is not over a CHAR key. So a tap that wobbles 1.2 mm and lifts on &123 shows
the symbol layer for one frame and hides it; the user sees nothing happen. Stand-in (1) says "lifting back on
&123 commits nothing" and is silent about the layer; leaving the symbol layer UP in that case would make a
drifted tap behave as a tap. Feel; H21's row.

**MINOR-4 — Holding the space bar past the long-press timeout without dragging inserts no space.**
`down()` l.192-195 schedules `startSpaceMove` after `longPressMs`; once in `SPACE_MOVE`, `up()` l.298-301
stores the raise and never calls `space()`. Input: rest a thumb on the space bar for > 500 ms and lift without
moving — nothing is typed. No R6 value covers a held space; H13/H2 territory, recorded so the human row sees
it.

**MINOR-5 — `SpeechClient` bind/unbind count drifts by one after the `:speech` process dies.**
`KeyboardService.startVoice` (l.343-348) binds whenever `SpeechClient.bound` is false — which is also the
state while Android is re-binding after a process death (`SpeechClient.kt:116-124` sets BINDING, `service = null`)
— so `bindCount` (`SpeechClient.kt:137`) goes to 2 while only one `bindService` exists; `stopVoice` (l.352-360)
unbinds once per keyboard hide. Input: tap the mic, kill `:speech` (phase 03 E12's step), tap the mic again,
hide the keyboard: the `:ime` process stays bound (both engines resident, the 5-minute idle release never
starts) until the NEXT keyboard hide, which brings the count to 0. Self-heals in one show/hide cycle, so
MINOR; the fix is to bind on `connection == UNBOUND`, not on `!bound`.

**MINOR-6 — Cursor-drag chevrons sit ≈135 phys from the dot, outside R6's 105–126.**
`KeyboardView.kt:329-335` places each chevron glyph's CENTRE at `inner (115) + size/2 (35)` = 150 phys from
the dot, so the ink's inner edge is 150 minus the glyph's half-ink-width (≈15 for a Fluent chevron at 70 phys)
≈ 135. The Decision's figure is the inner EDGE, 105–126. LOW candidate, H4 judges it; the number is easy to
hit by placing the glyph by ink (`drawGlyphInkAt` already exists, l.290-300).

**MINOR-7 — `stopSpeaking()` has no owner; any client can cancel another's speech.**
`SpeechService.kt:430-435` and `ISpeech.aidl:42`. `speak()` records `speakOwner` (l.415) and routes TTS events
to it, but `stopSpeaking` is unguarded, unlike `stopListening` (l.402-410). No caller misuses it today (the
keyboard never speaks), so this is an API hole between two of our own processes, not a live defect.

**MINOR-8 — An emoji insert or emoji-panel backspace leaves the strip stale.**
`KeyboardService.kt:293-304, 313` commit / delete through `editor` without `controller.refreshStrip(...)` and
without clearing `pendingAdd`. Input: type "hel", open the panel, insert 😀 — the strip still offers
"hello / help / hell" for a word that now ends in an emoji; backspacing the emoji from the panel does not
update it either. The next key press repairs it.

**MINOR-9 — In a phone-number field the emoji paths return to QWERTY, not the keypad.**
`KeyboardService.kt:302, 311` call `switchLayer(Layer.LETTERS)` for "switch back after an emoticon" and for
the panel's `abc`, whereas the symbol page's own `abc` key goes back to `Layer.PHONE` for a phone field
(`KeyboardController.kt:272`). Input: phone field → &123 → ☺ → any emoji (switch-back on): the keyboard now
shows the letter grid in a phone field. Same wrinkle for a NUMBER field (opens on SYMBOLS_1, returns to
LETTERS). Route both through the same "initial layer" rule.

**MINOR-10 — After tapping the ORIGINAL spelling of an autocorrected word, "+ word" is not offered.**
`stripTapped` l.652 sets `pendingAdd` only for `Kind.VERBATIM`; an `ORIGINAL` pick (the R6 §2.2.7 t=128 s
flow: autocorrect fired on space, the user taps back into the word, chooses the original) replaces the word
and refreshes to an empty strip (l.653 → l.698-703, caret after the trailing space). R6 §2.2.7 t=138 s shows
"+ Becase" offered after that pick. Learning still works through the typing-mode VERBATIM path (E12's row),
so the Decision holds; this is the second of the two D1 l.1558-1560 routes.

**MINOR-11 — The show/hide build-start result is not where the Decision says it is.**
The Decision line ("Show / hide") says "the result is recorded here", and the doc header says a FINAL doc
changes only through a dated INDEX.md Change Log entry. The result (the IME cannot own the slide; system
slide fades, 167 / 117 ms) is recorded in INDEX.md's status cell (l.45), in `NEEDS-HUMAN.md` H10 and in
`E3M/E3M.txt` l.28-31 — but INDEX.md's Change Log has no phase-05 entry. Same for the `onComputeInsets`
UNVERIFIED item, which E9 proves but nothing records as "verified". Process, not code; one dated entry closes
both.

**MINOR-12 — Every `onStartInputView` blocks the IME's main thread on a cross-process provider call.**
`KeyboardService.kt:231` → `KeyboardConfigProvider.read` (`KeyboardConfig.kt:122-125`) → `ContentResolver.call`
into the main process. When the launcher process is not running (the LMK took it, or the shell is not the
Home), the first keyboard show spawns it and waits for the provider to publish (providers publish before
`Application.onCreate`, so the wait is the process spawn, ≈100–300 ms, not the whole launcher start-up). The
design note at `KeyboardConfig.kt:58-64` explains the choice (one writer, one reader); the cost is a show
latency the separate-process decision was meant to avoid. Worth a one-line note in H1 (typing feel) rather
than a change.

**MINOR-13 — A 3-letter prefix of a long common word autocorrects to a short word on space.**
`Suggester.kt:96-104`: only a whole-word CORRECTION within `autoBudget` may replace the typed word; a pure
completion carries `edits = MAX_VALUE` (l.77 applies `COMPLETION_COST` to the score only), so it can never
be the replacement, and the two compete on score alone (l.76-80). When the correction outranks the
completion — "be" is commoner than "because", so for "bec" it does — the correction is bold and replaces
"bec" on space, and the word the user was typing is thrown away. NEEDS-HUMAN H1 already records this exact
observation ("after 'bec' the bold candidate is 'be'"); listed here with its mechanism so the fix, if Jeremy
wants one, lands in the right place (a common completion of the typed prefix should veto a shorter-word
correction, or the prefix's length should count against deleting from it).

**MINOR-14 — Word Flow does not autocorrect the word typed before it, and a typed letter after a swiped word
runs into it.**
`finishSwipe` (l.456-472) commits `" " + word` (leading space, `needsSpace` l.463) and never calls
`autocorrectBefore()`, so "teh" + swipe "cat" → "teh cat" (the space that would have fixed "teh" never came
through `space()`); and since no trailing space is added, swipe "hello" then tap "w" → "hellow". D1 l.1503-1506
("Your phone adds the spaces") is satisfied for swipe-after-swipe. Both are H3/H1 feel items, recorded for the
human rows.

**MINOR-15 — `sentenceCase` lower-cases every recognised word not in the lexicon's cased form.**
`KeyboardService.kt:405-412`: "NEW YORK" from the recogniser → "new York" (SCOWL carries "York", not "New York").
Dictation cosmetics; H1.

---

## 3. What was hunted and found sound (so PART 2 can lean on it)

- **Rollover / multi-touch (single-block case):** `down()` l.159-162 commits the previous CHAR press and ends
  its track; the old pointer's UP is then ignored (l.143, no track); the new popup replaces the old at once
  (`release` → `press`). `canSwipe` requires `tracks.size == 1` (l.209), so a rollover never turns a tap into
  a swipe. ✓ (MAJOR-1 is the cross-block case.)
- **Long-press timers:** every `schedule`d runnable checks `tracks[t.id] === t` before acting (l.353); `endTrack`,
  `cancelAll` and `ACTION_CANCEL` cancel both the long-press and the repeat runnables (l.311-318, 320-327).
  The backspace repeat (l.184-189) and the cursor stepper (l.511-527) are removed on UP. ✓
- **Swipe start:** half a pitch (72 phys, l.765) from a letter in a field with `swipeOn`; the long press is
  cancelled when the swipe starts (l.438). ✓
- **Lift point (5627ad7):** the UP position is applied as a final `move()` before `up()` (l.147-148); for
  SPACE_MOVE that is the raise, for ALTERNATES / ONE_HANDED the picked cell, for SYMBOL_SLIDE the key under the
  lift (E12's "MOVE onto 1, UP → 1 committed" depends on it). ✓
- **SPACE_MOVE:** raise = `raiseAtDown + (downY − y)` clamped to `[0, maxRaise]` (l.247); only the raise
  changes and a layout pass is requested (l.252-253, `KeyboardService.kt:223-225`); the raise is stored in
  phys on UP (l.299) and reloaded on start-input / new input view only (`recomputeMetrics(..., reloadRaise)`),
  never on a dock change mid-gesture. ✓ The E9 run-1 defect (README l.82) is genuinely fixed.
- **CURSOR stepping:** axis locks on the first exit from the ring (l.509-510); the stepper re-reads the finger
  from the track every tick and steps only while the finger is outside the dead zone on the locked axis
  (l.518-522); `handler.post` gives the first step at once, then 150 / 750 ms. ✓
- **Editor in-flight carets:** FIFO of expected caret positions; an update matching ANY pending entry pops
  through it and keeps the newest still-pending caret (l.50-53); a non-matching update clears and adopts
  (l.44-49). Batched `replaceAround` produces one update. ✓ (MINOR-1 is the restart wrinkle.)
- **KeyboardService lifecycle:** owners set on the host AND the decor view (l.149-153, the phase-03 lesson);
  RESUMED at once for Compose pointer input (l.90); `onFinishInputView` cancels gestures and stops / unbinds
  voice (l.254-259); `onDestroy` cancels the scope, the loader, the SoundPool and the speech binding. ✓
- **onComputeInsets:** content / visible top = the panel's top; touchable region = the panel plus the nav
  strip; nothing above the panel (popups, headroom) or below a raised panel is touchable, and the window
  background is transparent (l.155). ✓ E9's band tap-through is the proof.
- **Voice start / stop / unbind:** permission checked in-process (l.333-340, raises the grant page with
  NEW_TASK from the `:ime` process — the activity runs in the main process, the grant is per UID ✓); `listen`
  is deferred until BOUND through the connection collector (l.102-109); errors map to notices (l.381-391,
  `MICROPHONE_BUSY` → "Tess is using the microphone."). ✓ (MINOR-5 is the count drift.)
- **Config from the main process:** provider not exported, same-UID only (`AndroidManifest.xml:261-264`);
  a failed read keeps the last config and logs (`KeyboardConfig.kt:122-125`). ✓ (MINOR-12 is latency.)
- **EngineBrain:** built once off the main thread (`KeyboardService.kt:100, 113-119`), `brain` volatile,
  used only on the main thread afterwards; the swipe path is handed over in phys through `toPhysX/Y`, so the
  dock and the raise do not move the letters under the decoder (`EngineBrain.kt:21-23, 67-71`); learned
  words written temp-and-rename. `kindOf` (l.89-95): password → PASSWORD (no suggest / autocorrect / learn /
  double-space), `textNoSuggestions` / phone / number → NO_SUGGESTIONS, URL / email keep completions but never
  autocorrect. ✓
- **Speech: one microphone owner.** `startListening` (`SpeechService.kt:355-400`) takes `micLock`, refuses a
  different owner with `MICROPHONE_BUSY` (never queues, never steals), lets the same owner replace its own
  capture by bumping the generation on the Binder thread, and the worker clears ownership only if its
  generation is still current (l.392-398). `stopListening` ignores a non-owner (l.402-410). `unregister`
  (l.347-353) and `onCallbackDied` (l.99-105) both release the mic and interrupt the capture; a dead owner met
  mid-dispatch is released too (l.273-285, a `DeadObjectException` is thrown for a `oneway` call to a dead
  binder). A queued capture superseded before it starts returns without touching ownership (`SherpaAsr.kt:238-241`
  + the worker's generation check). Cortana's side: `CortanaModel.kt:258` says "The keyboard is using the
  microphone right now." and speaks it (`isSpeakable` true for code 7); `CortanaRecognitionService.kt:93` maps
  it to `ERROR_RECOGNIZER_BUSY`. The edge case "one engine, one microphone: refused with a notice, never a
  crash" holds in both directions; "the engine is ours, the role isn't required" holds (the keyboard binds
  the service directly, no role check). ✓ (MINOR-7 is the TTS stop.)
- **`SpeechClient` connection order:** `connectionState = BOUND` (l.110) precedes `register(callback)` (l.111),
  so the keyboard's deferred `listen` can reach the service before its callback is registered. Harmless:
  `startListening` does not require registration (it addresses the owner proxy directly) and `register` follows
  within the same main-thread turn; noted so nobody "fixes" the order the other way.
- **ShellApp main-process guard** (`ShellApp.kt:31-35`): the `:ime` and `:speech` processes skip the catalog,
  the layout ADDs, the music-slot claim, the feeds, the badge sweep and the reminder re-arm. Nothing in
  `SpeechService` / `SherpaAsr` / `SherpaTts` / `SpeechBreadcrumb` / `SpeechAssets` / `EspeakData` reads any
  of those, and nothing in `ime/` does either (the only cross-process needs are the config provider and the
  speech binding, both explicit). `ReminderScheduler.rearm` is still covered by the main process's own start
  and the BOOT_COMPLETED receiver (both main). ✓
- **Checklist rows** compare against the exact `InputMethodInfo.getId()` form; "selected" uses
  `currentInputMethodInfo` (API 33+), the fix E1 forced (README l.84). ✓
- **Manifest:** the IME is `exported=true` behind `BIND_INPUT_METHOD` (system-only, the required form); the
  config provider is not exported. ✓

---

## 4. PART 1 verdict (provisional — no gate verdict until PART 2)

The implementation matches the FINAL spec's Decisions line by line, with the numbers the Decisions cite
reproduced by the grid to within tolerance; the approximations are the ones the spec declares and each has
its H-row. No BLOCKING finding. Two MAJORs: multi-touch routing across the strip / band boundary (MAJOR-1),
which should be fixed at the producer before the phone rows P1 (typing in real daily apps) run, since two-thumb
typing is exactly what P1 exercises; and password text retained in clear in the `:ime` diagnostics ring at
HEAD (MAJOR-2), for which an uncommitted fix exists that still leaves the `alternates for <letter>` line
open — commit the fix with that line gated before P2 (keyguard password) runs. Fifteen MINORs, none of which
contradicts a Decision. The overall GATE verdict waits on the evidence pass (PART 2).

<!-- PART 2 (per-criterion evidence verdict, E1–E12 + edge cases, and the overall GATE verdict) is appended below when the final pass's logs are delivered. -->
