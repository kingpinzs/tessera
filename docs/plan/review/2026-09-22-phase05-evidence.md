# Phase 05 QA gate — reviewer 2 (testability, evidence integrity, adversarial) — PART 1

Read-only review of `docs/plan/qa/phase-05/scripts/*` against `docs/plan/phase-05-keyboard.md` (FINAL),
R6 §2, the IME / speech sources, the manifest and the fixture + gesture driver. Default verdict: not
proven. PART 2 (per-criterion verdict against the final pass's logs) is not attempted here; a few log
lines from the 14:xx runs are cited only where they show what a driver's branch actually does.

State found while reading (matters for everything below): HEAD `bef3d55` committed 15:03:06; the APK
under the final pass (`FINAL-PASS.txt`: sha256 `c34da1006a535e9d`, md5 `b9948a444fe18037`, matches the
installed APK per every row header) was built 15:03:08, i.e. from `bef3d55`. `Editor.kt` and
`KeyboardService.kt` were then modified on disk at 15:04:39 / 15:04:57 (uncommitted, `git diff HEAD`),
during the pass, and the built APK's dex contains none of the new strings (0 hits for ` hidden)`,
`password field`, `hidden: password field`). So the code I reviewed for the password ring is NOT the
code under test — see B1.

## Findings

### BLOCKING

**B1 — The build under test writes every password character to a dumpsys-readable ring; the fix exists
only as uncommitted source written after the build, untested by any row.**
Evidence: `Editor.kt@bef3d55` (`git diff HEAD -- app/src/main/kotlin/app/tileshell/ime/Editor.kt`):
`Diagnostics.add("ime", "commit ${quote(text)} ($why)")` with `quote()` unconditional and `why =
"key ${key.id}"` (KeyboardController.kt:550) → for a password "tessa" the `:ime` ring holds
`commit "t" (key t)` … five times, readable with `adb shell dumpsys activity service
app.tileshell/.ime.KeyboardService` (KeyboardService.kt:417-427). EDGE2 types the keyguard password
with this keyboard twice (edge2.sh:72,82). The on-disk fix (`Editor.secret`, KeyboardService.kt:240,
299, 371) is (a) not in the APK the pass is running on — dex string grep above; (b) not committed;
(c) not in `README.md`'s defects table; (d) not asserted by any driver (no row reads the ring after
password typing). Also it does not cover M4.
Consequence: the gate's evidence is for a build with a known plaintext-password leak; the fixed build
has no evidence. Required: commit, rebuild, re-run; add to E2 (after `[Secret1]`) and EDGE2 (after the
keyguard unlock): `assert_absent "Secret1"/"tessa" "$(ime_dump)"` and
`assert_contains "commit (1 hidden) (password field)"`.

**B2 — E3 popup timing (R6 2.3.5, HIGH) is measured against a pixel the press itself changes; the
assertion cannot fail for a slow popup.**
Evidence: e3motion.sh:113 `gy = key centre`, :119 the touch reference pixel is `(gx, gy + 40)`; the g
key is 202 phys = 151 px tall, so ±75 px from its centre is inside the key. `press()`
(KeyboardController.kt:331-337) adds the key to `state.pressed` (accent fill, asserted by E3 itself at
e3.sh:151-152) and sets `state.popup` in the same call → the reference pixel and the popup box change
in the same frame whatever the latency. motion.py:124 treats any Δ>30 at that pixel as "touch"; accent
(0,120,215) vs key (48,48,48) is Δ=287. Every run recorded `touch_ms == popup_ms`, delay 0.0
(E3M.txt, E3M-run1..3). Mutation: popup and fill 100 ms late → still 0.0 → PASS.
Fix: sample the show_touches indicator at a pixel no press changes, e.g. the g/h gap `(gr + 6, gy)`
(panel colour; the spot's radius covers it), and print touch / key-fill / popup frames separately.

### MAJOR

**M1 — E3M "at full size in its first frame (no scale / fade)" passes a 2–3-frame fade-in.**
motion.py:133 defines the popup's first frame as the first with >50 % accent coverage, then
e3motion.sh:136 asserts fill ≥ 0.88 of the box. A pixel counts as accent from alpha ≈0.61 (b = 48 +
167a > 150), so a fade reaching 0.61 at frame 3 registers "first frame" = 3 with fill 0.93 → PASS. R6's
own method is "≥ 91 % of its FINAL accent-pixel count in the first frame" — first frame meaning first
with any accent. Fix: pop = first frame with >2 % accent in the box; assert fill(pop)/fill(final) ≥ 0.91.

**M2 — E3 does not measure the cursor dot's three HIGH diameters.** Decisions: "an accent centre dot
21 ± 3 phys across on a key-grey disc 56 ± 4 phys, inside a dark ring 87 ± 4 phys … Measured by E3."
e3.sh:70-71 and measure.py:73-76 measure the centre only. A dot drawn 30/70/120 phys passes. Fix: a
radial luminance profile at the dot centre in `e3_letters.png` (accent→grey→dark crossings).

**M3 — Password field: an emoji inserted into a password is learned into "Recent".**
KeyboardService.kt:296-297 `store.addRecent(action.text)` runs regardless of `editor.secret`; it is
persisted (ImeStore.kt:38-40) and the panel opens on RECENT from then on (KeyboardService.kt:284;
EmojiPanel.kt:51). Scenario: password `🐱sunset9` → the next emoji-panel open, in any app, shows 🐱 as
the first Recent cell. This is learning from a password field (Decisions R2D-12 "never in password
fields"; edge case 1). No row opens the panel in a password field. Fix: skip `addRecent` when
`field.isPassword`; add an EDGE1 line.

**M4 — Password field: the alternates line names the letter.** KeyboardController.kt:381
`"alternates for ${key.text}: …"` is written for every long-press, password or not; the on-disk fix
does not touch it. Scenario: "café" typed with a hold on e → ring: `alternates for e: e è é ê ë ē ė ę`
then `commit (1 hidden) (password field)`. Same channel as B1. Sub-point: KeyboardService.kt:451 logs
strip / emoji-panel touch coordinates, which with `emoji category X` (:308) narrows the emoji chosen.

**M5 — The microphone refusal (edge case "one engine, one microphone … refused with a notice") is
exercised by no device row that can fail for its absence.** edge3.sh:59-64 is an OR: the run on record
took "the keyboard let go when its window hid" (EDGE3.txt:22) and its own second half FAILED ("Cortana
then owns the microphone", owner -1, EDGE3.txt:23); the reverse direction skips the refusal check with a
note and zero assertions when Cortana's text box does not raise the keyboard (edge3.sh:91-93; taken,
EDGE3.txt:28). So `SpeechError.MICROPHONE_BUSY` (SpeechService.kt:361-378) and the keyboard's notice
(KeyboardService.kt:386) have JVM tests only. Fix: a direction that must observe either the refusal or
a hand-off, never a note; and a busy scenario that keeps the IME's input view alive while Cortana
asks (or the reverse with the text box not ending Cortana's capture). Expect NOT PROVEN in PART 2
unless the final log shows the refusal branch.

**M6 — E3's show / hide failure branch is entered without its record, on an unevidenced check.** The
Decisions require "the result is recorded here" (this FINAL doc changes only via a dated INDEX Change
Log entry). The result exists in INDEX.md:45 (status cell), commit 9ce189a's message and
e3motion.sh:7-12; there is no dated INDEX entry for phase 05 at all, BUILD-START.md has no section for
it, and the Decisions still read UNVERIFIED for both the slide and `onComputeInsets`. The check itself
is architecture reasoning (no experiment recorded); credible, but a build-start check was promised.
E3M's two slide lines assert only that a number was measured (e3motion.sh:93,100) — honest and
labelled; the clause will read NOT PROVEN / H10 in PART 2.

**M7 — The harness header cannot tie an APK to a commit.** lib.sh:58-63 records driver blob, harness
blob, APK hash and installed match — not `HEAD` nor tree cleanliness at build time. In this pass the
tree went dirty 91 s after the build (B1) and every header still says `apk match yes`. Fix: bake
`HEAD` + `git status --porcelain app/ | wc -l` into BuildConfig and print them in the header.

### MINOR

- m1 E5's "bold and first" (e5.sh:37) and E4's "*word" (e4.sh:43) read `state.strip` via the dump
  (KeyboardService.kt:425), not pixels; a view that ignores `bold` passes R6 2.2.6 (HIGH "drawn bold").
- m2 "+1 px" tolerances (e3.sh:11-14, 29-30): applied only to pixel-read values and stated per line —
  acceptable in effect; the stated reason ("the capture's quantum, the allowance RV11 gives a frame")
  is wrong (RV11's frame is a motion allowance; a screencap is lossless). The real basis is threshold
  quantisation of anti-aliased ink, up to 1 px per edge. Reword, keep. The hold-dots' ±4 (e3.sh:116-117)
  is the driver's own number — neither R6 2.1.17 nor the Decisions give one; say so.
- m3 E3M's lower-bound relaxation to −16.7 ms (e3motion.sh:128-135): the reasoning (R6's 17-ms floor
  is the Lumia's touch-to-photon latency, unresolvable at 60 fps, P3 re-measures) is sound and stated;
  moot until B2.
- m4 edge1.sh:104 "a non-dictionary swipe commits a dictionary word or nothing" asserts only
  `!= "[qzxv]"`; "[qzx]" passes. Assert empty-or-in-lexicon.
- m5 edge1.sh:113 "no crash": `ime_dump | head -3` can never contain "FATAL"; vacuous.
- m6 edge3.sh:113-123 "in use by a call": `adb emu gsm call` does not take the microphone and the
  assertion accepts either outcome — proves "no crash" only → NOT PROVEN for the edge case.
- m7 edge2.sh:94 "physical keys type through the shell keyboard's service": key events reach the app
  whether or not the IME acts; the line proves typing with the soft keyboard hidden, not the service.
- m8 edge2.sh:149-161 changes handedness after the hold is cancelled (the driver says so); the
  scenario "while the dot is held" cannot occur → record N/A, not PASS.
- m9 No row types a correctable misspelling + space in a password field ("teh " → "[teh ]"); "no
  autocorrect" rides on the suggests=false coupling (Suggester.kt:65, KeyboardController.kt:605).
- m10 Dictated text sits in clear in two rings: SpeechClient.kt:91 (`[speech] final open=…`, in the
  `:ime` process — next to the KeyboardService.kt:371 guard it undermines), SherpaAsr.kt:375-378 (`:speech`),
  and `dump()` prints `voice=Listening(partial=…)` (KeyboardService.kt:424). Not a password leak today:
  the mic is hidden when `!suggestionsOn` (KeyboardView.kt:453) and no other path calls `startVoice`.
- m11 Tap-to-stop while BINDING is dropped (KeyboardService.kt:322 → SpeechClient.kt:191-195) and
  `pendingListen` still starts the capture; a second tap is needed. UX.
- m12 `speak()` / `stopSpeaking()` take no owner check (SpeechService.kt:412-435): any client cancels
  another's speech. TTS only; the keyboard never speaks.
- m13 engine `FieldKind.NO_SUGGESTIONS.learns = true` (engine/FieldKind.kt:30) contradicts
  `FieldInfo.learningOn` (FieldInfo.kt:44); dead in practice, two truth tables.
- m14 Not measured anywhere: popup "no tail / shadow / rounded corners", "the next key's popup replaces
  it at once", strip "no separator / no dividers", the black → glyph on the URL key, Enter grey in a
  default text field (edge1.sh:21 skips `field_text`), emoji / &123 / Enter colours (shift only sampled).
- m15 R6 2.2.7 "+ word" and the pressed item's accent fill, 2.2.8 swipe-left for more: no row.
- m16 Edge case m9 "recovery through Android's IME switcher": EDGE2 recovers via Android's process
  restart; the switcher is never used.

## (a) Method — driver by driver

| Driver | Proves | Can its lines fail when the feature is wrong? | Gaps / notes |
|---|---|---|---|
| lib.sh (phase 03 floor) | header, verdict counting | yes: `within.py` is `abs(a−e) ≤ tol`, non-numeric → FAIL; zero assertions → FAIL | M7 |
| kb.sh | IME dump, mirror, gesture ops | n/a helpers; `kb_dump` failure → empty bounds → downstream FAIL (fails safe) | — |
| e1.sh | E1 | yes, bracketed: disabled → missing/missing; enable → granted/missing; set → granted/granted | — |
| e2.sh | E2 | yes: mirror text per type, layer per field, password strip empty + no mic, Fossify shows "Tessera" | m9; Fossify leftover text could mask a typing failure only if typing failed entirely |
| e3.sh | E3 static | yes on 130+ lines from dump bounds at R6 tolerance; pixel lines at +1 px | M2, m1, m2, m14 |
| e3motion.sh | E3 motion | popup timing: NO (B2); no-fade: weak (M1); slide: records only (M6) | fps gate is real (retakes on >18.2 ms gaps) |
| e4.sh | E4 | yes: five words accumulate in the mirror; strip leads bold | m1; trail is H3 |
| e5.sh | E5 | yes: strip changes on each of 6 keys; "because/the/receive" commits; learned file bracketed password vs text | m1, m9 |
| e6.sh | E6 | yes: 7±2 / 4±2 steps, axis lock, dim 24±6, up/down lines | LOW values asserted against R6 numbers as the spec allows |
| e7.sh | E7 | yes: three cells vs index code points, index[0]=1F600 | — |
| e8.sh | E8 | yes: airplane on, mic owner = ime pid, "hat time is it" | — |
| e9.sh | E9 | yes: +300±3, clamp 865, persists exactly, band pixel not panel, tap → bottom_field focus, rest | best bracketed row |
| e10.sh | E10 | yes: dump ±3 and screen accent centroid ±(3+1.33) | — |
| e11.sh | E11 | yes: on → letters, off → panel stays, food category | — |
| e12.sh | E12 | yes: caps 150 vs 500 (pixel + text), dsp 500 vs 1500, slide "1", dock 1152±3 flush 0/W, learning bracketed | PART 2 must read the `.script` gaps |
| edge1.sh | edge cases (fields/keys) | mostly yes | m4, m5, m9 |
| edge2.sh | crash, force-stop, keyguard, hw kbd, raised, landscape, handedness | mostly yes | m7, m8, m16, B1 (ring not asserted clean) |
| edge3.sh | voice edge cases | permission-denied yes; busy NO (M5); role yes; call NO (m6) | — |
| edge4.sh | liveness (reboot) | yes | — |
| measure.py / motion.py | measurement | geom/ink/segments fine; popup touch reference confounded (B2), pop threshold 0.5 (M1) | — |

Relaxations judged: (1) system slide recorded not asserted — honest and labelled, but the failure
branch's own precondition (the record) is unmet: M6. (2) popup lower bound −16.7 ms — justified by the
spec's text (R6 says the 17 ms is 1 frame after the capture's indicator) and P3: m3, moot under B2.
(3) +1 device pixel on pixel-read values — justified in effect, mis-attributed in reason: m2. No
dump-bound value was widened.

Spec sub-clauses with no line: dot diameters (M2); the refusal notice (M5); the popup's corners and
replacement (m14); strip separators/dividers (m14); R6 2.2.7 "+ word" / pressed fill, 2.2.8 (m15);
password autocorrect (m9); switcher recovery (m16); "in use by a call" (m6).

## (b) Adversarial — trust surfaces

**Password fields.** Gated correctly in code: suggestions / autocorrect / swipe / learning / double-space
(FieldInfo.kt:38-44,60; engine/FieldKind.kt:29; Suggester.kt:65; KeyboardController.kt:209,605,652,677,707;
UserDictionary.kt:61); the voice key is absent (KeyboardView.kt:453) and `startVoice` has no other caller.
Leaks: B1 (every character, in the tested build, via `dumpsys`), M3 (emoji → Recent, persisted),
M4 (alternates line), m10 (dictation rings, not password today). The learned-words file
(`files/learned_words.txt`) never receives a password commit (pending count included, UserDictionary.kt:61).

**Speech process / microphone.** Contract holds as written: owner = callback binder (SpeechService.kt:363);
a second client is refused and only it hears the error (:364-378); non-owner `stopListening` ignored
(:402-410); owner death → `releaseMicIf` (:99-105) and any dead dispatch → release (:277-279);
`unregister` → release (:347-353); events go to the owner only (:308-315); a same-owner restart bumps the
generation and the running capture unwinds (:369-373, SherpaAsr.kt:322); the keyboard frees the
microphone when its view finishes (KeyboardService.kt:255-258 → unbind → unregister). No path found to
steal, stop another's capture, or receive another's transcript through the AIDL. Weak spots: a client
whose `register` failed (swallowed, SpeechClient.kt:111-112) can own the microphone with no death link
until the next dispatch throws; `speak`/`stopSpeaking` unowned (m12); the refusal is unproven on the
device (M5).

**Exported surface (manifest).** `.ime.KeyboardService` exported=true, `permission=BIND_INPUT_METHOD`,
`process=":ime"` (AndroidManifest.xml:248-258) ✓; `.ime.KeyboardConfigProvider` exported=false (:261-264),
no `process` attr so it runs in the main process — reachable only by the app's own UID ✓;
`.cortana.speech.SpeechService` exported=false, `:speech` (:235-238) ✓; the only two `android:process`
attributes are :237 and :252 ✓. `SettingsActivity` is exported=true (:79-90 of the middle block) as
`method.xml`'s `settingsActivity` requires. `CortanaPermissionActivity` (exported=false) is started from
the IME with NEW_TASK (CortanaPermissionActivity.kt:98-105) ✓. The `dump()` ring is behind
`android.permission.DUMP` (shell) — which is exactly B1's channel.

**Process guard.** ShellApp.kt:31-35 returns before catalog / layout ADDs / feeds / reminders for any
process whose name ≠ packageName ✓ (`:ime`, `:speech`). Diagnostics is per process (Diagnostics.kt).

## For PART 2 (not done here)
Read the final pass's logs for every row; confirm each header's md5 `b9948a444fe18037`; verify E12's
`.script` gaps (≈150 / 500 / 460 / 1460 ms) and E6's hold durations; decide the busy branch taken in
EDGE3; apply B1 to E2 / EDGE2 / edge-case-1 verdicts.
