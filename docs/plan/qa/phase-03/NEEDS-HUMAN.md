# Phase 03 — NEEDS-HUMAN rows

Hard Rule 15: anything only Jeremy can verify gets a row here, and `done` requires his sign-off on each
one. Nothing below is a defect; every line is a design choice the footage could not settle, recorded
with what it was derived from and where to look at it.

The phase doc's Decisions carry the full reasoning for each; this sheet is the index and the capture.

| H | What Jeremy judges | Why it is his call | Where to look |
|---|---|---|---|
| H1 | The persona's feel — the ring's pop-in, its rotation, the listening pulse | The numbers are measured (R3 A22, R6 §3.1) but "does it feel like Cortana" is not a number | `E11/e11.png`, and the live session |
| H2 | Each bundled voice's intelligibility, and whether the default is the closest to Cortana | 11 Kokoro voices; speaker 2 (`af_nicole`) is the build's pick | Cortana > Settings > Voice, which speaks each name in its own voice |
| H3 | Any approximation not covered by H5–H30 | catch-all | — |
| H4 | The side key's outcome if One UI refuses registration | phone only | phone row P2 |
| H5 | The text read-back card's layout | R6 §3.4.1 layout is LOW; the right button's label was not legible, so "Cancel" is the agent's pick | E7 captures |
| H6 | The call confirmation flow | R6 §3.4.4 is UNMEASURED; the card mirrors the text read-back | E7 captures |
| H7 | The Cortana tile's face | A11 buys no news internet use, so W10M's headline back face is out; the static ring is an approximation | `E6/e6_start.png` |
| H8 | "Lock screen options" On by default, and its wording | R6 §3.5.1–3.5.3: the wording is 10586-era, and both filmed devices had it On, with no fresh-device default captured | E9 captures |
| H9 | That the large centred persona never speaks | R6 §3.2.6 UNMEASURED; every spoken reply is shown on a card, so the small persona is the speaking one | E4 captures |
| H10 | That the waveform's bars follow the microphone level | R6 §3.1 does not say what drives the shapes | E4 captures |
| H11 | The locked look | R6 §3.5.5 is LOW (one camera capture) | E9 captures |
| H12 | The "Unlock to continue" card, and the rule that only the card on screen is pending | no R6 source | E10 captures |
| H13 | "Ask me anything" over "Type here to search" | R6 §3.3.5: two screen recordings say one, one camera capture says the other, cause not visible | `E5/e5_home.png` — **the placeholder is measured and passing** |
| H14 | The text box sitting on the keyboard while typing | R6 §3.3.8 UNMEASURED | E5 captures |
| H15 | The Search key's tap motion | R6 §4.2.2 is LOW; the build starts the exit at release and does not copy the delays | E8 captures |
| H16 | The missing-time card, storing a Whenever reminder from it, and that card having no subline | R6 §3.4.3 shows only the prompt | E7 captures |
| H17 | The calendar-event card | no R6 source; patterned on the reminder card | E7 captures |
| H18 | The delete confirmation cards | no R6 source | E7 captures |
| H19 | The recurrence options (Only once / day / week / month / year) | R6 §3.4.3 shows only "Every Month" | E7, E15 captures |
| H20 | The Search key's hold time (Android's own long-press timeout) | W10M's hold time is not in R6 | E8 captures |
| H21 | Whether the 150 m place radius feels right | no source | E13 captures |
| H22 | The place reminder card, its spoken wording and its saved-card subline | P4 design; no footage shows a place card | E13 captures |
| H23 | The ≡ pane without W10M's Feedback item, its empty slot, and the 350 ms accent-fill hold | the shell has no feedback service (A11); N1 read 500 ms where N2 read 350 | E15 captures |
| H24 | The Reminders page and reminder page as the 14393 dark pages | R7 §3.9.1: the final release's page is UNMEASURED | E15 captures |
| H25 | The + page, the History page, the "…" button and the reminder page's app-bar states | R7 §3.9.5 UNMEASURED: nothing in the footage taps them | E15 captures |
| H26 | Place and person reminders on the Reminders page, and their reminder page | no R7 row shows one | E15 captures |
| H27 | Cortana's Settings page structure | R7 §3.1.4 is LOW, geometry not measurable | E9 captures |
| H28 | Cortana's Settings page look on the final release | R7 §3.9.4 UNMEASURED | E9 captures |
| H29 | The person reminder card, its spoken wording and its saved-card subline | P4 design | E14 captures |
| H30 | The role-notice page shown when another app holds the assistant role | no W10M counterpart | E1-adjacent capture |
| H31 | The tolerant contact lookup: a spoken name that matches no contact exactly is matched to the one that sounds closest | The shipped recogniser hears "Mom" as "MAM" and "MA'AM" — on the device AND on the host, with "Mom" in the grammar pass's hotwords at a raised boost. Without tolerance the ruled "call or text a contact" command cannot be used at all; with it, there is a risk of texting the wrong person. Only a single close match counts, and two near-misses are refused rather than guessed | `ContactsNearMatchTest`, and the contacts lines in the diagnostics |
| H32 | Tess's eye is HAL 9000's lens, and Cortana is the easter egg inside it (Jeremy, 2026-09-21) | A paint change, not a geometry one: every A22 / R6 number stays where it was and the circles are lit as a lens instead of filled flat. Two things are Jeremy's call and nobody else's — whether the lens reads as HAL at persona size and at tile size, and whether three taps inside 1.5 s is the right way in to the five-second Cortana reveal (R6 gives the persona no tap, so nothing spec'd was taken for it) | The tile face on the device: `qa/phase-03/H32/tile_face.png`, and the lens found by E4's own colour search at 100.0 px against a geometric 98.74 px on the same capture. `LensTest` (7 tests) covers the tap window, the fade and the promise that a full reveal is the pre-HAL drawing. NOT captured: the persona inside a session, because this AVD will not open one (its input is wedged and `cmd voiceinteraction show` needs a signature permission) — the same fault that blocks E4, E8-E10 and E13-E15 |

## Build-start agent calls in this phase, for the record

These are decisions the build had to make that the doc left to the agent. They are listed here because
Jeremy reads this sheet, not the commit log.

1. **ASR model swapped mid-build.** The doc names sherpa-onnx and an "English open-vocabulary ASR
   model" without naming the export. The first pick (streaming zipformer en 20M) ships no `bpe.model`,
   so sherpa-onnx cannot tokenize plain-text hotwords — and a contact's name cannot be precomputed. The
   ruled grammar pass is impossible with it. The model is now **streaming zipformer en 2023-06-26,
   int8, chunk-16-left-128**, which ships one. 73 MB.
2. **TTS**: Kokoro int8 en v0.19, 11 voices, ~140 MB, default speaker 2 (`af_nicole`) — H2.
3. **The BPE vocabulary ships as a derived text file**, `bpe.vocab`, not the sentencepiece binary:
   sherpa-onnx parses the vocabulary itself and wants `<piece> <score>` per line.
4. **espeak-ng-data ships as one deterministic zip** and is extracted once per versionCode, checksum
   first. Loose assets would be ~1500 files for data that has to reach the filesystem anyway.
5. **TTS synthesis is one call, not the streaming callback.** sherpa-onnx's JNI looks the callback up
   by a signature Kotlin does not emit, and calling it aborts the process. Replies are a second or two
   long, so the cost is latency and the crash is gone.
6. **The Notes app picker lists every app**, with `ACTION_CREATE_NOTE` handlers first. Android has no
   notes category the way it has APP_MUSIC, so the user's notes app is whatever they say it is.
7. **`androidx.profileinstaller.ProfileInstallReceiver` is on the exported allow-list.** It is the
   library's, not the shell's, and it is guarded by DUMP — but the list has to be the whole exported
   surface to be worth anything.
