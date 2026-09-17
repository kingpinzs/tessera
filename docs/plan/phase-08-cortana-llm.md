---
phase: 08
slug: cortana-llm
status: FINAL   # frozen 2026-09-17 (Jeremy: "A"); changes only via a dated INDEX.md Change Log entry
depends-on: [03, 04]
---

# Phase 08 — Cortana on-device LLM layer

## Goal
An on-device, fully offline LLM extends phase 03's not-understood handler: Cortana answers free-form requests in persona and can call
the same command actions phase 03 (and the toggle commands from phase 04) expose, within latency, memory and thermal thresholds measured on the S25 Ultra.
Nothing in phase 03's speech, TTS or command path is replaced.

## Scope
**In:** LLM runtime + model in its own process; persona prompt; action calling into the existing command actions (which carry phase 03's confirmation flow); model provisioning; Settings page + checklist rows (model present, runtime healthy).
**Out (explicitly):** the memory / Notebook / rules / hooks / replay-test harness (phase 09); cloud AI (Q2); replacing phase 03 engines (RV2); new actions beyond the existing command set unless the interview adds them.

## Decisions
- 2026-09-16: On-device, fully offline; added on top of the permanent command layer (Q2, A7, Jeremy)
- 2026-09-16: Extends the not-understood handler from phase 03 (RV3, agent)
- 2026-09-16: Model delivery: B. A one-time download the first time Cortana's AI is turned on, from the model maker's official host (non-Google, P5), with checksum verification; fully offline afterwards. Amends A11: the launcher's internet uses are Weather and this one-time model download (interview Q1, Jeremy). A11 later also lists visual voicemail downloads if the R4 voicemail probe succeeds (PQ2) (note added by review R2-m1 / T-m11)
- 2026-09-16: Confirmations: A. Cortana confirms before anything that reaches other people or changes Jeremy's data (sending texts, placing calls, adding or deleting calendar events and reminders); opening apps, alarms, timers, music and toggles run directly (interview Q2, Jeremy)
- 2026-09-16: Split (agent, P3): phase 08 builds the prompt assembly with an ordered list of context providers and a pre/post action pipeline, which phase 09's harness plugs into (an ADD, no replacement)
- 2026-09-16: Correctable harness (scope add): Cortana's AI must be tunable the way Jeremy tunes Claude: a harness with memory, rules and hooks so it can be corrected and gets better. Jeremy: "as I have tuned you and did a tone of extra I need to be able to do it to this tell it has the correct harness/memoery/hooks to work better. So it need to be able to be corrected". Corrections work through memory, rules and hooks, not by retraining the model (interview Q2, Jeremy). Shape ruled in Q3; the harness is its own part, phase 09
- 2026-09-16: Process isolation (agent): the LLM runtime runs in its own `android:process` (`app.tileshell:llm`); the launcher process never loads model weights, so a low-memory kill of the runtime cannot take down Home, the notification listener, the tile engine, the IME or the InCallService. Model file size: the chosen GGUF is roughly 2–2.5 GB at Q4_K_M for the candidates below (SmolLM3-3B smallest); the exact size is recorded here at build start. Pre-download storage check: the shell refuses to start the download unless free storage is at least twice the model size, and says so (review F1-M8, agent)
- 2026-09-16: Runtime and model (agent, closes interview 4): runtime = llama.cpp (MIT, R2 §6.1) with GGUF models; candidates in order Microsoft Phi-4-mini (MIT), Qwen3-4B (Apache-2.0), SmolLM3-3B (Apache-2.0), licences re-verified at build start; the first candidate that meets every threshold on the S25U is the model. Thresholds on the S25U: time to first spoken word ≤ 2.5 s for a short request; decode ≥ 10 tokens/s; runtime process PSS ≤ 4 GB; no thermal status ≥ SEVERE across 20 consecutive requests. A Google model needs its reason written here and amends the "non-Google host" clause of the model-delivery ruling (review F2-M14, F2-M5, agent)
- 2026-09-16: The confirmation flow is phase 03's: its action layer reads back texts and calls and confirms calendar and reminder changes. Phase 08 calls those same actions and adds no confirmation of its own; the Q2 ruling above is met by phase 03's flow (review F2-M7, agent)
- 2026-09-16: Metered data (agent, P2): the model download runs on unmetered networks; on a metered network it waits, showing "Waiting for Wi-Fi" with a "Download now" button that allows metered data for this download only (review T-M15, agent)
- 2026-09-16: Performance definitions (agent): the request set is 20 fixed free-form requests of 3–12 words, stored with the QA evidence and typed through Cortana's text box (the real text path, so ASR time is excluded); time to first spoken word runs from submit to the first TTS audio frame; decode speed is generated tokens ÷ decode time per request; thermal status is polled every second with `adb shell dumpsys thermalservice` for the whole run (review T-m12, agent)
- 2026-09-16: Memory bounds (agent): runtime process PSS ≤ 4 GB (above); the launcher process PSS ≤ the most recent recorded launcher baseline (phase 03 P4, which re-recorded phase 01 P9's figure after speech moved to `app.tileshell:speech`) + 50 MB. This replaces "unchanged from phase 01's figure", which phase 03's speech work would have broken and which had no tolerance (review R2D-04 / T-M17, agent)
- 2026-09-16: The emulator rows need the AVD hardware set in phase 01 (`hw.ramSize=8192`, `disk.dataPartition.size=16G`), so the ≈2–2.5 GB model has twice its size free and RAM for weights plus KV cache (review T-M16, agent)

- 2026-09-16: Locked phone: A. While the keyguard is locked, the AI answers general questions only and never reads Notebook memory, contacts, calendar, messages or any personal data; a request needing any of those shows "Unlock to continue" and finishes after unlock. Same rule as phase 03's locked commands (PQ3), enforced in the same action-layer gate, as set in the locked AI mechanism line below. Jeremy: "A" to R2D-06 (review R2D-06, Jeremy; enforcement clause re-pointed by review R3D-03 / R3T-B8, agent)
- 2026-09-17: Locked AI mechanism (agent, the R2D-06 ruling with what exists in this phase): while the keyguard is locked, the prompt carries no personal context (no contact, calendar or message content), and the model's action list still holds this phase's personal-data actions: reading the calendar (phase 03's "what's on my calendar" action) and looking up a contact (the lookup behind phase 03's call and text actions). A personal question gets its data only by calling one of those actions, and phase 03's action-layer locked gate refuses that call while locked and shows "Unlock to continue"; after unlock the same request runs again. This phase adds no message-reading action (Scope: no new actions), so messages never reach the model. Each request's diagnostics entry lists the context providers that ran and the actions called and refused. Requests reach a locked session by voice through phase 03's route or typed into the session's text box. Memory does not exist in this phase: phase 09 ADDs a memory-recall action to this gated set and keeps its memory providers and context-injecting hooks out while locked, tested there (review R3D-03 / R3T-B8, agent)
- 2026-09-17: Model download UI, P4 design (W10M never downloaded a model): Cortana's Settings page gains an "AI" section with the model's name and size and a "Download" button; while downloading, a non-interactive accent progress bar in phase 01's slider-track geometry (X23) with "Downloading... <n> MB of <total> MB"; on a metered network the line "Waiting for Wi-Fi" with a "Download now" button; "Not enough storage" when the storage check refuses; "Delete" and "Download again" once the model is present (agent pick, H4) (review R3T §5, agent)
## Interview queue (Stage A step 4)
1. ~~Model delivery~~ ruled 2026-09-16 (see Decisions)
2. ~~Confirmations~~ ruled 2026-09-16 (see Decisions)
3. ~~Tuning harness~~ ruled 2026-09-16, moved to phase 09 (see phase-09-cortana-harness.md)
4. ~~[agent] Runtime + model pick with licence check~~ closed 2026-09-16 (see Decisions: llama.cpp + Phi-4-mini / Qwen3-4B / SmolLM3-3B, thresholds set). Was: Jeremy 2026-09-16: "I think the small llm is either google or microsoft so we will need to use that unless there is a better tiny model". With P5: Microsoft's Phi small models (fit the Microsoft theme; licence checked) are the first candidate, benchmarked on the S25U against other non-Google tiny models; Google's Gemma only if no non-Google model meets the thresholds, with the reason written here. Earlier note: R2's LiteRT-LM + Gemma candidate is Google's, so prefer R2's non-Google route (llama.cpp, MIT, with a permissively licensed non-Google model such as Qwen3 / Phi-4-mini / SmolLM3) unless the phase doc states why Google is required; S25U latency / memory / thermal thresholds (R2-16)

## Build tasks
1. Runtime integration (llama.cpp in its own `android:process`) + one-time model download (storage check, progress, checksum, resume, delete/re-download in Settings)
2. Persona prompt + response path into the existing TTS
3. Action calling into the existing command actions
4. Action calls route through phase 03's confirmation flow (no separate flow here)
5. Settings page (with the model download section, P4 design) + checklist rows

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); `uiautomator dump` follows RV13; "diagnostics" is read with phase 01's diagnostics command.
**Emulator:**
- E0 Happy-path download: first enable → the download runs with visible progress, the checksum passes and the model loads; `adb shell cmd connectivity airplane-mode enable` mid-download pauses it (`adb shell dumpsys connectivity` shows no active default network) and `adb shell cmd connectivity airplane-mode disable` resumes it on Wi-Fi from where it stopped, not from zero (byte offset in diagnostics); with `adb shell svc wifi disable` and the emulated mobile network reported without `NOT_METERED` in `dumpsys connectivity`, the download waits with "Waiting for Wi-Fi" until "Download now" is tapped; restore: `adb shell svc wifi enable` (review T-M15)
- E1 With the network off (phase 01 E9's command and `dumpsys connectivity` proof; restore: `adb shell cmd connectivity airplane-mode disable`), an utterance outside the command list produces a model answer through the existing TTS path (functional check; performance is phone-only)
- E2 Locked AI (Decisions): with a calendar event inserted for next Friday afternoon (`content insert` into `content://com.android.calendar/events`) and a contact "Sam" with a number, `adb shell locksettings set-pin 1234`, `adb shell input keyevent KEYCODE_SLEEP`, `adb shell input keyevent KEYCODE_WAKEUP`, and Cortana opened over the keyguard (phase 03 E9's route); each request is a WAV played through phase 03's audio route. "How far is the moon" is answered (reply text in diagnostics). "Am I free on Friday afternoon" and "what's Sam's number" each reach the not-understood handler (diagnostics show no fixed-command match), the model calls the calendar read or the contact lookup, and the gate shows "Unlock to continue" (dump), with no calendar or contact provider in the request's diagnostics entry and no event or number in the reply. With the second card showing, `adb shell input tap` on "Unlock" at its dump bounds raises the bouncer (`adb shell dumpsys window`), then `adb shell input text 1234` and `adb shell input keyevent KEYCODE_ENTER` unlock, and the reply text in diagnostics holds Sam's number. Restore: `adb shell locksettings clear --old 1234`, and delete the test event and contact (review R3D-03 / R3T-B8 / R3T-m11)

**Phone-only:** P1 over the request set in Decisions: time to first spoken word ≤ 2.5 s and decode ≥ 10 tokens/s for every request (captured timings); P2 memory during P1's run: `adb shell dumpsys meminfo app.tileshell:llm` peak PSS ≤ 4 GB and `adb shell dumpsys meminfo app.tileshell` PSS ≤ the recorded launcher baseline + 50 MB (Decisions); thermal status never ≥ SEVERE across the 20 requests (polled every second); P3 a free-form request triggers the correct existing action; P4 airplane mode throughout
**NEEDS-HUMAN:** H1 answer quality and persona; H2 confirmation flow feels right; H3 the line between "general" and "personal" feels right in daily use; H4 P4 design: the model download section, progress and "Waiting for Wi-Fi" states on Cortana's Settings page

## Edge cases
- Model missing or corrupt; download interrupted, resumed, or on metered data (waits for Wi-Fi unless "Download now" is tapped); not enough storage (refused before download starts); checksum mismatch; thermal throttling
- Runtime process killed (low memory or the user): Start, tiles, listener, IME and dialer are unaffected; Cortana says the AI is reloading and reloads the runtime
- Answer interrupted by the user or by an incoming call
- Risky or ambiguous action requests ("text everyone"); a request that maps to no action
- Very long prompts or answers
- Liveness (N-01): runtime after reboot, Device care, app update
- Locked: a general-sounding question that needs personal data: one the calendar read or contact lookup answers gets "Unlock to continue"; "what did I say I like" has no memory to draw on in this phase and no personal context in the prompt, so it is answered without personal data (phase 09 turns it into a gated memory-recall call); unlock cancelled; the phone locks mid-answer (answer stops, nothing personal is spoken)

## QA evidence
