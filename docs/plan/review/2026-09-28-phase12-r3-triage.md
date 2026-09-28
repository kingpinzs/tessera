# Phase 12 — review round 3 triage (the last round; 2026-09-28)

Reviewers: Fable (design, `2026-09-28-phase12-r3-design.md`: BLOCKING 3 · SHOULD-FIX 9 · NOTE 4) and codex CLI (testability,
`2026-09-28-phase12-r3-testability.md`: BLOCKING 9 · SHOULD-FIX 1). Roster: fable + codex-cli (codex MCP not loaded).
The two overlap on four blocking findings (D1 = V1, D2 = V9, D3 ≈ V8, D11 ≈ V2) and on the grant count (D6 = V3).

No finding reopens one of Jeremy's dated rulings. `tess_ring` (D3) was a key this doc's table invented, not a ruling; Q9's
"the original preset keeps Tess's lens, tinted Cobalt" stays, carried by `tess_lens`. No question goes to Jeremy.

Every row below is ACCEPTED: the fix is applied to `phase-12-setup-wizard.md` as the reviewer wrote it, with the
adjustments stated. "Apply as written" means the reviewer's "Fix" / "Smallest fix" text, adapted only to fit the doc's line.

| Id | Sev | Ruling / exact change |
|---|---|---|
| D1 = V1 | BLOCKING | Apply D1's fix: E12 names the four picked sources and their real sizes (the 1024-px floor waived for the three 941-px files by Jeremy's pick — recorded, not asserted), logs each scale factor; build task 5 crops the four picked variants (soft-c read as JPEG). |
| D2 = V9 | BLOCKING | Apply D2's fix: the `[fluent] static backdrop rebuilt` clause moves to surface (b) for HAL, Soft and Lumia only (after the tap, Home, a swipe to the app list); the original and Midnight yield no `rebuilt` line and `[fluent] acrylic=off reason=setting`; nothing asserted on surface (a). |
| D3 ≈ V8 | BLOCKING | Apply D3's smallest fix: strike `tess_ring` everywhere (the table column becomes `tess_lens`; l.328, l.355, l.404, build task 3); the ring-colour samples in E11 / E13 are struck. Then V8: the lens check is NOT `persona.py` (hard-coded HAL red, `r > g > b` filter, frame-directory input): build task 4 builds a separate still-image preset-colour checker that takes the expected lens hue AND brightness as independent inputs (so Midnight's half-brightness red is told from HAL's), reports sampled values and counts per named region, fails on a missing region, and imports nothing from the app; E11 / E13 name it and the UI state each capture needs. `persona.py`'s phase-03 interface is untouched. |
| D11 ≈ V2 | BLOCKING | Apply D11's fix: the host script's six WebP outputs are COMMITTED at `app/src/main/res/drawable-nodpi/preset_{hal,soft,lumia,midnight,w10m_hero,w10m_streaks}.webp`; the script is run by hand and re-run when a source changes; apk.yml unchanged. The 2026-09-28 ruling line adds "shipped as committed WebP outputs". E12 adds the sha256 check (script re-run to a temp dir, compared). And V2: under ruling (a) E12 / E13 FAIL an APK that lacks any preset picture — the no-picture branch is no longer an accepted outcome for a CI or dev build; T12-7's no-picture form stays only as the runtime fallback when a picture cannot be decoded. |
| D6 = V3 | BLOCKING | Apply both: the grant-row count and every fixture that assumed nine Setup grants are corrected for the two rows phase 15 added (the reviewers name them: `Checklist.kt` as built), and E1(b) / the E2 state set those two rows explicitly rather than leaving them to `pm clear`. |
| V4 | BLOCKING | Apply V4's fix: build task 2 (and 4) add `StartActivity.dump(...)` exposing the launcher's existing `Diagnostics.dump` through the already-exported activity (no new exported component), and a phase-12 ring helper reading `dumpsys activity app.tileshell/.StartActivity` with `wall=` slicing kept; E2 / E5 / E6 / E7 / E10 read through it while notification access is revoked. The dump override joins the adversarial-review surfaces (with D15's). |
| V5 | BLOCKING | Apply V5's fix to E3's `tess:background_location` clause (assert FINE and BACKGROUND absent; Allow all the time; complete the foreground precise-location request; on return FINE granted, BACKGROUND absent, the same step still shown with its `partial` line; then the background grant); build task 2 owns any request adaptation; no `pm grant` shortcut. |
| V6 | BLOCKING | Apply V6's fix: the visual measurements seed the layout before the preset action, never between the action and its first visual assertion; each wizard-side trial provisions all grants without the finished marker, revokes Usage access, launches the wizard and uses Not now to reach the presets; the Settings-side pass seeds once and returns to the running Start after each tap; restart-persistence checks only after the live-apply assertions; `kb_end` after keyboard probes; the final provisioning restore kept. |
| V7 | BLOCKING | Apply V7's fix: the gutter-pair rule (l.543-547) is replaced by a host pixel oracle built in task 4 — it decodes the shipped WebP and maps it independently to Start's measured viewport (ContentScale.Crop, centred 1.3× scale, translationY = −scroll × 0.25 at scroll 0; `StartPage.kt:563-570`), validated against several visible gutter patches; E3 / E11 / E13 sample through it. This corrects T12-16's applied text, not its requirement (independent expectations). |
| D4 | SHOULD-FIX | Apply: build task 2 names the refactor lifting the Setup rows' action lambdas out of `ChecklistPage()` so the wizard can run them. |
| D5 | SHOULD-FIX | Apply: the row types gain the flag that tells a grant row from an observation row (the reviewer's wording). |
| D7 | SHOULD-FIX | Apply: `tess_lens`'s stored values and the "on the accent's hue line" derivation are defined as the reviewer proposes. |
| D8 | SHOULD-FIX | Apply: "any single item change" is limited to the preset's keys; the write path must not rewrite unrelated keys (`ShellSettings.update()` as built rewrites every key — the task names the narrower write). |
| D9 | SHOULD-FIX | Apply: the Custom snapshot gets a named store. |
| D10 | SHOULD-FIX | Apply: centre crop (height-fit, width trimmed equally), LOSSY WebP quality 90, no metadata; l.70 "writes PNG" → "writes WebP"; build task 5 repeats it. E12's budget stands (measured total ≈ 0.7 MB). |
| D12 | SHOULD-FIX | Apply: the Auto-advance line and E6's Tess half describe `CortanaPermissionActivity` opening app info itself on the blocked result, then the relabel on return. |
| V10 | SHOULD-FIX | Apply: E11's Custom fixture uses phase 13's real `push_picture` MediaStore URI; the distinct sub-row picks fixture A through the real picker, checks its persisted grant survives a preset tap and a restart, and is released only when nothing references it; task 4 owns the grant-inspection helper, task 3 the lifetime. |
| D13 | NOTE | Apply every stale cite and the two stale facts as listed. |
| D14 | NOTE | Apply: phase 04 adds no second `overlay` row (phase 15's `overlay`, `Checklist.kt:138`, meets it). |
| D15 | NOTE | Apply: the grant release is scoped to `content://` grants the shell took and nothing references; never an `android.resource://` URI; the surfaces for the adversarial review are named (with V4's dump override). |
| D16 | NOTE | Apply: the edge case for `tess:background_location` with device location off. |

After the edits: every change is a DRAFT edit (the doc is not FINAL yet), so no Change Log line is needed beyond the INDEX
Stage A row's round-3 entry. The doc goes FINAL on Jeremy's word (Stage A step 7); round 3 was the last review round.
