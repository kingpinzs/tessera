# Phase 03 QA gate — plan and evidence index

Rows come from `docs/plan/phase-03-cortana-commands.md` (FINAL). Rows start from the baseline state and
restore what they change (PLAN RV12); motion rows are measured from screenrecord frames (RV11); dumps
follow RV13 (retried up to 3 times).

## What this gate answers before it starts

Phase 02's gate was returned by BOTH reviewers on evidence integrity, not on product behaviour: rows
whose logs predated their drivers, a bracket that could not fail, verdicts that never reached the log,
sub-rows that were never run. `scripts/lib.sh` is built so none of that is possible here:

* every row stamps its log with the driver's git blob, the harness's blob, and a comparison of the
  **installed** APK's md5 against the one just built (`apk match yes (…)`), so a log cannot belong to a
  different build than it claims;
* every assertion is a real comparison that reaches both the log and the exit code;
* **a row that makes zero assertions FAILS** — a driver that asserts nothing has not tested anything;
* the per-row summary is computed from the recorded verdicts, never written by hand.

## Method note — the audio route

Every row that speaks to Cortana plays real audio into the AVD's real microphone. There is no text
injection path, and the phase doc forbids one existing at all (E5 proves the exported surface).

The utterances are synthesised on the HOST with the same Kokoro voice the shell ships
(`scripts/utterances.py`), written to 16 kHz mono WAVs, and played into a PulseAudio null sink that
`scripts/audio.sh setup` makes the AVD's microphone. Synthesising the prompts with the shipped model is
deliberate and is **not** circular for what this gate measures: the spoken-reply pass rule (Decisions,
review T-m8) grades the REPLY TEXT from the diagnostics dump and the RMS of the captured audio, and
never grades the recogniser's accuracy. The audio only has to be real speech on a real microphone.

Three things about that route were wrong on the first run and are fixed, all in the harness:

1. **The sink must be 16 kHz mono.** A 48 kHz stereo null sink puts a resample and a downmix between
   the utterance and the recogniser; it read "What time is it?" as "BUT TIME IS IT NOT".
2. **The feed must outlast the endpoint.** When playback stopped before the recogniser endpointed, the
   AVD's virtual microphone looped its last buffer and the utterance was heard twice
   ("WHAT TIME IS IT WHAT TIME IS IT", audioMs 5600 for a 2.6 s file). The utterances carry 3 s of real
   trailing silence.
3. **The utterance must start the moment listening does.** A driver that dumps the UI between tapping
   the microphone and playing the audio loses the request to the endpoint — an early probe heard "AND".
   `scripts/speak.sh` exists so that order cannot be got wrong in a driver.

Host audio input is blocked by default since emulator 28.0.3, so `audio.sh setup` also runs
`adb emu avd hostmicon` on the running AVD.

## Method note — the diagnostics

The `app.tileshell:speech` process has its **own** diagnostics ring, and the launcher's dump cannot see
it. A native abort inside onnxruntime takes that ring with it. So:

* `SpeechService.dump()` prints the speech process's status and its whole ring —
  `adb shell dumpsys activity service app.tileshell/.cortana.speech.SpeechService`;
* a breadcrumb file is written before each native construction and cleared after, so an abort still
  says which model was being built, and the next process start reports what the last one died doing.

Both were added because the first device run showed only "process gone" and nothing about why.

## Scripts

| Script | What it drives |
|---|---|
| `scripts/lib.sh` | the driver floor: the build guard, the assertions, the row summary, the device helpers |
| `scripts/audio.sh` | the null sink, the AVD's host microphone, `record`, and the `rms` the pass rule needs |
| `scripts/utterances.py` | the 35 spoken commands, synthesised, padded and peak-normalised |
| `scripts/speak.sh` | one spoken request, in the only order the endpoint allows |
| `scripts/exported.py` | the APK's exported components, from its own merged manifest, vs the allow-list |
| `scripts/barvis.py` | E11: are Android's own status and nav bar windows visible |
| `scripts/pixel.py`, `scripts/within.py` | one captured pixel; one measured comparison |
| `scripts/e1.sh` … | one per acceptance row |

## Rows

Status is what the driver's exit code says, not a judgement.

| Row | Status | Evidence |
|---|---|---|
| E1 assistant role | **PASS 5/5** | `E1/E1.txt` |
| E2 the ruled command list, spoken, offline | PARTIAL — see "what E2 still owes" below | `E2/E2.txt` |
| E3 unmatched speech reaches the not-understood handler | **PASS 7/7** | `E3/E3.txt` |
| E4 persona motion from screenrecord | NOT RUN | — |
| E5 typed request, text box geometry, exported components | **PASS 12/12** | `E5/E5.txt` |
| E6 the tile ADD runs once; reminders survive force-stop and reboot | tile half PASS 8/8; reminder half owed a re-run | `E6/E6.txt` |
| E7 the confirmation flow | NOT RUN | — |
| E8 the Search key, tap and hold | NOT RUN | — |
| E9 lock screen options and the locked session | NOT RUN | — |
| E10 the locked commands | NOT RUN | — |
| E11 the session's drawn bars | **PASS 14/14** | `E11/E11.txt` |
| E12 the speech process's death is contained | 9/10 on the run captured; the one failure was the driver's, fixed, re-run owed | `E12/E12.txt` |
| E13 place reminder | NOT RUN | — |
| E14 person reminder | NOT RUN | — |
| E15 the pane, Reminders and Settings pages | NOT RUN | — |
| Edge cases | NOT RUN | — |

## Defects this gate found, all fixed in this phase

| # | Found by | Defect |
|---|---|---|
| 1 | the first device run | The BPE vocabulary was the sentencepiece `bpe.model` binary where sherpa-onnx wants a TEXT vocab. On the host it says so; on Android it aborted the speech process on every bind, every 16 s. Fixed at the producer: `tools/fetch-speech.sh` derives `bpe.vocab`. |
| 2 | the second open of Cortana | The framework REUSES a VoiceInteractionSession across show and hide, so binding the speech process in `onCreate` left every open after the first with no engine ("startListening dropped: not bound"). The bind moved to `onShow`. |
| 3 | the first spoken reply | `OfflineTts.generateWithCallback`'s JNI looks the callback up as `invoke([F)Ljava/lang/Integer;`, which neither D8's invokedynamic lambda nor a Kotlin lambda class carries; the native side called `NewFloatArray` with a pending `NoSuchMethodError` and ART aborted the process. The streaming callback is gone. |
| 4 | the unit tests | "six forty five" parsed as 6:40 with a stray "five"; "at midnight" said in the morning became noon; "is it going to rain" was not a weather request; a contact's name was read back in the recogniser's lower case. |
| 5 | `exported.py` | `androidx.profileinstaller.ProfileInstallReceiver` is exported (guarded by DUMP) and was not on the allow-list. The list has to be the WHOLE exported surface to be worth anything. |
| 6 | E2 | A fixed query was exact-matched, so the device's own "AT WHAT TIME IS IT" became "Sorry, I can't do that yet."; and the recogniser spells the meridiem out ("SEVEN TWENTY A M"), which left the time unparseable. Behind the second one, reading the word just before the meridiem took the MINUTE as the hour. |
| 7 | E2 | A command that opens an app cancelled its own spoken reply: the close was emitted at the same moment the speech started. Captured at −118 dBFS against −32 for every other command. |
| 8 | E2 | **"Set an alarm" and "set a timer" could never have worked.** The manifest did not declare `com.android.alarm.permission.SET_ALARM`, so the platform refused both — through the voice activity and through the normal start it falls back to. |

## Questions the phase doc left open that the device answered

* **The system bars CAN be hidden over a voice-interaction window.** The doc had this UNVERIFIED with a
  fallback if it could not be done. The session logs `system bars hidden=true` and E11 measures it.
* **`bpeVocab` works as an asset path** — no extraction needed. `status()` reports
  `asr_bpe_vocab=asset:speech/asr/bpe.vocab`.
* **Kokoro reports 11 speakers**, matching the bundled voice table.

## What E2 still owes, and one defect it left open

E2 ran the whole ruled list and drove every observable. What it has NOT closed:

1. **A reply spoken while a voice activity takes the screen is still cut off.** "Set an alarm for
   seven twenty AM" now really sets the alarm — `dumpsys alarm` holds DeskClock's pending alarm, which
   is the row's observable and it PASSES — but the spoken "Alarm set for 7:20 AM" is captured at
   −118 dBFS. `startVoiceActivity` moves focus to the Clock's voice activity, the session hides, and
   the reply dies with it. This is the same class as the close-after-speaking defect already fixed, on
   a path the fix does not cover: there the shell hides the session, here the platform does. **Not
   fixed. Not worked around.** The fix belongs with the action layer's alarm and timer path (speak,
   then start), and it needs its own build-and-run cycle.
2. **A duplicated verdict line.** The alarm row's "reply was audible" verdict appears twice in one
   log. Each assertion should reach the log exactly once. Until that is explained, E2's counts are not
   trustworthy at face value — which is precisely the kind of evidence-integrity smell phase 02's gate
   was returned for, so it is written down here rather than left for a reviewer to find.
3. The row has not been run start-to-finish on a single build since the SET_ALARM permission landed.

A device lock was added to `lib.sh` after two E2 runs overlapped and wrote to the same log: the second
row read the first's diagnostics and recorded a verdict about an utterance it never spoke. A driver
that cannot take the lock now refuses to start.

## NEEDS-HUMAN

H1–H30 are Jeremy's to judge; `NEEDS-HUMAN.md` lists each with the capture that shows the behaviour.
