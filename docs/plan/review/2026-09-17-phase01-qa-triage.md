# Phase 01 QA gate — triage of the three reviews, 2026-09-17

Reviewers: codex-mcp (evidence vs acceptance criteria), opus (design and correctness), opus (adversarial, Live Tile
API trust surface). Jeremy's ruling the same day applies throughout: a gap a later phase's doc already owns is
recorded with that phase and does not block this one.

## Fixed in code (each with its own evidence)

| Finding | Where | Fix |
|---|---|---|
| Legacy badge broadcast let any app set or clear any other app's badge (adversarial F1, HIGH) | LegacyBadgeReceiver, LiveTileSettings | unverified senders refused by default, a switch to accept them, kill switches honoured, 30/min limit, and a 15-minute expiry sweep so a legacy count really lapses after three days. Evidence SEC/security_fixes.txt |
| A caller's image stream could hold a binder thread for ever (adversarial F2, HIGH) | ImageIngest | 3-second read deadline, at most two concurrent reads. Measured: refused after 3 s, call returned in 4 s |
| Images written before the quota was checked (adversarial F4) | ImageIngest | quota checked before anything is written; no files left after a refusal |
| Samsung badge authority trusted without checking the serving package (adversarial F5) | SamsungBadgeReader | only read when a system package serves the authority |
| Image errors named the package owning an authority, a package-visibility oracle (adversarial F6) | ImageIngest | the caller is told only that the authority is not its own; the detail goes to diagnostics |
| Per-app change URI let any app watch when another app updated its tile (adversarial F7) | LiveTileStore, client library | one shared change URI |
| Restored or hand-edited state could name any file, and app data was in the backup set (adversarial F8) | LiveTileStore, manifest | stored names must match the shell's own pattern; allowBackup off |
| No global switch for the API (adversarial F9) | LiveTileAccessPage | master switch "Let apps update their tiles" |
| A crossfading tile blanked completely, accent plate and badge included (Reviewer 1 finding 3) | TileView | a true cross-dissolve: outgoing face fades out while the next fades in; measured, 0 frames drop to the page background |
| An assigned app uninstalled or disabled fell back to another handler, against the Decisions (codex) | SlotResolver | the slot is unassigned while the app is gone, and shows it again when it returns |
| A role slot did not follow a default-app change (edge case) | SlotDefaults | Start re-resolves its slots on resume |
| The drawn clock ignored time zone and the 12/24-hour setting (edge case) | SystemBars | follows both, verified live |

Earlier in the gate, before the reviews: Back on Start (HOME activity exclusion, lock continuation, launchable
fallback), the Settings picker crash, the picker covering the drawn bars, the "New" caption baselines, the missing
peek motion, tile periods, flip duration, entrance fade, and the badge hidden on live faces.

## Evidence added or corrected

| Finding | Action |
|---|---|
| E5 measured composition, not the visible preview (codex) | re-run: the row's own method (post time vs render timestamp) reads 227 ms, and the preview face is shown to appear at the tile's next flip, 5.1 s later, then cycle. E05/E05_rerun_final_build.txt |
| No row was re-run against the final build; E1 stale (both reviewers) | the final pass: one APK (sha256 recorded), fresh install, rows re-run. FINAL/final_pass.txt |
| E12's "fixtures installed before onboarding" was substituted by deleting the caption store (codex) | the fresh install is the real precondition: 31 apps present before the shell became Home, 0 captions |
| Out-of-box defaults never observed (Reviewer 1 finding 11) | the fresh install records tile colour 0,120,215 (X26) and no stored preferences |
| Oversized XML, more images than the cap, an image URI with no grant, reinstall under a different signer (both) | exercised: xml-size 9084 > 8192, image-count 14 > 12, image-read "no read grant", and the stored state wiped on the signer change |
| Coverage claims larger than what was seen (Reviewer 1 finding 17, codex) | the readings now say what was observed (51 of 60 Quartz rows, 288 of 331 rows walked) |
| E10 misquoted its own analysis files (Reviewer 1 finding 13) | corrected to 217 / 250 / 251 ms |
| Weather reading contradicted the quoted diagnostics (Reviewer 1 finding 7) | corrected: the fetch after the geo fix used the old grid, the one after it used Denver |

## Recorded, not fixed here

| Finding | Why |
|---|---|
| Two columns leave six default tiles outside the grid (codex, Reviewer 1 finding 4) | Jeremy's ruling: phase 02 owns reflow (its Edge cases and E9). Recorded in E13 and the INDEX Change Log |
| The bottom row's exit fade cannot be measured by the analyser (Reviewer 1 finding 1) and "tapped tile last" rests on a frame strip (finding 2) | both are approximations judged by Jeremy in H8/H2; the readings and their limits are stated in E10 rather than claimed as measured |
| The tile-timing classifier takes kinds from the shell's own diagnostics (finding 15) | the measurement is of periods and durations; the kinds only label them. Stated in E10 |
| Peek graded against the flip band (finding 16) | recorded as an agent call in the INDEX Change Log |
| Rate-limit state is process-local and per package (adversarial F10) | recorded; it bounds a single caller, and the caps that matter (queue, images, quota, XML) are per payload |
| Uninstall cleanup may ride on the runtime receiver only (adversarial F11) | recorded for a device check on the phone (P row) |
| Partial photo access with a selected subset (both) | the selection UI cannot be driven from adb; the checklist "partial" state is proven, the subset is a phone check |
| Slider geometry X23/H31, status-bar glyph layout X16/H24 | approximations with their own NEEDS-HUMAN rows; no measured source to check against |
