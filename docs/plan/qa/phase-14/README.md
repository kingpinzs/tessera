# Phase 14 QA — the pod bay: evidence index

Phase doc: `docs/plan/phase-14-pod-bay.md` (FINAL 2026-09-29). Device: AOSP AVD `tileshell_fhd`, **emulator-5554 only**
(every driver exports `ANDROID_SERIAL=emulator-5554`; emulator-5556 and the others on this host belong to other
projects). Drivers: `scripts/` on phase 03's floor (`scripts/lib.sh` and `scripts/within.py` are symlinks to phase 03's).

## The AVD's launch (since 2026-09-29 22:03)

```
QEMU_AUDIO_DRV=none ~/Android/Sdk/emulator/emulator -avd tileshell_fhd -no-snapshot-load
```

- **`QEMU_AUDIO_DRV=none`, no `-allow-host-audio`:** the emulator opens no connection to the host's PipeWire at all;
  its audio mixer runs on the `none` backend's timer, which is what `streamAudio` captures from. The guest keeps its
  microphone and speaker (hw.audioInput / hw.audioOutput stay yes).
- **No `-port`:** an AVD launched with an explicit `-port` starts no gRPC server (seen on this host: run 2's launch,
  `SPIKE-audio/emulator-run2-port-flag-no-grpc.log`, and the other projects' 5570 / 5572). Without it the emulator takes
  the first free console port (5554) and starts gRPC on 8554 with token auth, advertised in
  `/run/user/1000/avd/running/pid_<pid>.ini` — the file `emu_audio.py` reads.
- The live emulator log is outside the repo (the process writes it); `SPIKE-audio/run5-emulator-log.txt` is its
  startup / gRPC / audio excerpt.

Why it was relaunched: the AVD running before (pid 3309523, launched 2026-09-27 with `-allow-host-audio`) had lost its
PulseAudio connection in both directions (`pulseaudio: pa_simple_write failed / Reason: Connection terminated`, and
the same for read — `SPIKE-audio/run1-emulator-log-audio.txt`), most likely at the PipeWire restart of 2026-09-29. With
its output backend dead the mixer produced nothing, and `streamAudio` delivered zero packets in every format
(`run1-format-probe-*.out`). It was stopped with `adb -s emulator-5554 emu kill` (its console) and relaunched as above.

## Audio spike (build start, before any voice row) — PASS 20/0 (run 5)

Driver `scripts/spike_audio.sh`; client `qa/phase-03/scripts/emu_audio.py`; switch `AUDIO_ROUTE=emu` in phase 03's
`speak.sh` and `lib.sh` `say` (default path unchanged). The host's audio was never touched: no `audio.sh setup` / `say`
/ `record`, no `pactl` / `paplay` / `parecord`, no `hostmicon`; `audio.sh rms` is a file computation.

| step | result | evidence |
|---|---|---|
| (1) endpoint only from the discovery file with `port.serial=5554`; `ANDROID_SERIAL=emulator-5556` refused (rc 2) before any connection | PASS | `SPIKE-audio/run5-SPIKE-audio.txt` |
| (2) `getMicrophoneState` → `realAudioEnabled=false` | PASS | same |
| (3) Tess listening, `injectAudio` of `pod_bay_doors` → `asr: final … open="OPEN THE POD BAY DOORS"`, `heard=true`, no "no speech in the capture" | PASS | same, `run5-speech-slice.txt` |
| (4) reply captured through `streamAudio`, RMS over the 28-s window −28.53 dBFS > −40 | PASS | `run5-reply.wav`, `run5-record.out` |

The runs, all kept:

| run | result | what it showed |
|---|---|---|
| 1 | 18/1 | (1)-(3) pass; `streamAudio` 0 packets — the old AVD's dead PulseAudio link (above) |
| 2 | 18/1 | after the relaunch: 1430 packets but −97.30 dBFS — the AVD's media volume is 0 (`run2-timestamp-probe.out`: packets are Unix-µs stamped and arrive exactly while Tess speaks, all ≤ −80 dB) |
| 3 | 19/1 | `cmd media_session volume --set 10` is a silent no-op on this image (volume read back 0); −116.08 dBFS |
| 4 | 19/1 | `cmd audio set-volume 3 10` works: −42.97 dBFS over the window (5.8 s of speech in 28 s) |
| 5 | 20/0 | full scale (`set-volume 3 15`) for the audible step, restored to 0 after: −28.53 dBFS |

**Media volume rule for every audible step (from runs 2-5):** phase 03's `j5.sh` sets the AVD's media volume to 0 and
never restores it; Tess's `USAGE_ASSISTANT` voice follows the media stream on this image. A row that checks a reply's
RMS saves the volume, sets it with `adb shell cmd audio set-volume 3 15`, and restores it and asserts the restore
(phase 15's e9.sh / e26.sh form, RV12). Nothing reaches the desktop at any volume (the `none` backend).

Consequence for the doc's fallback clause: the spike passed, so the voice rows (E6, E8, E9, E13's phase 03 re-runs)
stay spoken on `AUDIO_ROUTE=emu` — no typed-only fallback.
