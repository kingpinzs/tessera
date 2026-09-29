# Phase 12 pixel helpers — build task 4 (ii) / (iii) / (iv)

Written 2026-09-29 by a Fable subagent (host-only; it ran nothing on a device), filed here by the lead.

| Helper | What it proves | Self-test |
|---|---|---|
| `picture_oracle.py` | Start's `tile:slot:PEOPLE` band = α · accent + (1 − α) · B ± 4, B from the shipped WebP mapped independently to Start's viewport (ContentScale.Crop into 1080 × 2196, ×1.3 about the layer centre, translationY −scroll × 0.25), the mapping first validated at ≥ 4 spread gutter patches (± 6); T ≠ accent ± 4 (the missing-picture control); `--expect-opaque` (Midnight), `--no-picture` (Default: black gutters). Also a geometry-sensitivity block (informational). | synthetic HAL Start PASS; opaque tiles, a 30-px shift, layer scale 1.0, a wrong α literal, translucent-vs-opaque, picture-vs-no-picture all FAIL; a real phase-15 Default capture PASS |
| `lens_check.py` | Tess's lens colours off one still: per radial band, the hue (± 8°) and the brightness (V ± 0.05) the spec predicts at that radius for `--hue hal|<deg>` and `--brightness 1.0|0.5`, independent literals | HAL, Cobalt-re-hued and dimmed rings PASS; Cobalt vs HAL red, Midnight vs undimmed, HAL vs dimmed FAIL; a real phase-15 HAL idle ring PASS against 1.0 and FAILs against 0.5 and Cobalt |
| `grants.sh` | `persisted_grants`: the shell's persisted read URI grants from `dumpsys activity permissions` | against a hand-written AOSP-format transcript; unverified on API 36 until E11 runs it |

Run: `bash docs/plan/qa/phase-12/scripts/selftest/run.sh [out-dir]`.

**Lead's own check (2026-09-29):** re-ran the self-test myself — `21 of 21 cases ended with their expected exit code`, rc 0 (out dir in the session scratchpad). Read both scripts' headers and CLIs; wired `presets_lib.sh` to them (`--tile tile:slot:PEOPLE`, `--hue hal` for the HAL lens, which keeps each Brand tone's own hue).

**Doc observation (goes to Jeremy, not changed here):** E11 / E13 say the checker "finds each named lens region (rim, iris, glow) … a missing region FAILs". The idle persona at reveal 0 — the form those rows capture — is a stroked ring whose gradient runs glow → rim (`cortana/ui/Lens.kt` drawLensRing; `Persona.kt` drawIdleRing): the iris tone is never drawn in it. The checker measures three bands across the ring (inner / glow side, middle, outer / rim side), each against the colour predicted at its radius, and reports iris as "not drawn by the ring form". The hue and brightness checks still separate every preset pair the rows need (the negative controls above).
