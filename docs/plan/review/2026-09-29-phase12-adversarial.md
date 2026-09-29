# Phase 12 — adversarial review (2026-09-29)

Reviewer: a Fable subagent in adversarial mode (default verdict "not proven"; read-only; mutation runs in a scratch copy),
on the phase doc's named surfaces (r3 D15 / V4) plus the wizard's grant plumbing. Code at e5b9cc7d. Filed by the lead;
the report came back as text.

**Verdict: ADVERSARIAL: FAIL (2 blocking)**

## Surfaces

| # | Surface | Verdict |
|---|---|---|
| 1 | Custom snapshot / persisted grant (T12-12) | DEFECT (B1) + NOT PROVEN (B2) |
| 2 | `background` accepting `android.resource://` | PROVEN — only the app writes it (private prefs, allowBackup false, release non-debuggable); every reader opens as the app itself; the decoder bounds-checks and catches |
| 3 | The marker fixture / marker store | PROVEN — only Skip / Done write it (both gated on a live run); `commit()` is right; the fixture needs `run-as`, impossible on the release APK; E5 / E1 / E4 prove both halves on the AVD (P2 pending on the phone) |
| 4 | `StartActivity.dump` exposing the ring | PROVEN — gated by `android.permission.DUMP` like the listener's dump; five other components already dump the same ring; no manifest change (`git diff 97c19ef4..HEAD -- AndroidManifest.xml` empty) |
| 5 | Grant plumbing, fallbacks, reconcile, Back / Home, pin gate | PROVEN, with notes N8–N10 |

## Findings

- **B1 (BLOCKING)** — a Start + theme set customised BEFORE phase 12 has no `theme_preset` key, reads as Default, is never
  snapshotted, and the first preset tap drops the user's picture with no Custom to bring it back (an update install on
  the phone is exactly this). Fix: read an absent key as Custom when the items are not Default's.
- **B2 (BLOCKING, conditional)** — neither half of the grant keep / release contract has a completed proof: no JVM test,
  and E11 run 1's grant sub-row read an empty helper (its "A released" PASS is vacuous). Clears when E11 passes the grant
  sub-row with the rewritten `grants.sh`.
- N1 — the release considers only the older snapshot's background (a re-pick chain A → B → C while custom leaves B's grant);
  "Remove picture" and re-picks release nothing (phase 01 / 13 behaviour).
- N2 — no JVM coverage of snapshot / release / restore; extract a pure release decision.
- N3 — tapping Custom while already custom logs `[wizard] preset Custom` though nothing happens (cosmetic).
- N4 — BackgroundDecoder bounds the decode by height only (phase 13, pre-existing).
- N5 — `theme_preset_variant` is not validated on read.
- N7 — the ring holds typed text, Tess transcripts, reminder texts, contact names, phone numbers, addresses and picture
  URIs (pre-existing); P1 / P2 ask Jeremy to paste "Copy everything" — say so.
- N8 — reconcile treats a key missing from both lists as granted (fail-open, unreachable today).
- N9 — `fire` catches only ActivityNotFoundException; a SecurityException from an OEM Settings page would crash Home.
- N10 — "never asked" and "blocked" share `shouldShowRequestPermissionRationale == false`; the verdict holds on the AVD
  (E6), P1 checks it on One UI.

## Mutation table (both test classes, baseline 41 / 41)

| # | Mutation | Result |
|---|---|---|
| M1 | `needsStep` ignores `partialIsDone` | caught |
| M2 | `itemsOf` blind to `keyboardPalette` | caught |
| M3 | blocked rule `none` → `any` | caught |
| M4 | rejoin drops the declined filter | caught |
| M5 | `hal_dim` R 0.5 → 0.55 | caught |
| M6 | visibility precedence swapped | caught |
| M7 | `label` drops the MISSING condition | **survived** |
| M8 | first walk lists MISSING rows only | **survived** |
| M9 | partial-change condition inverted | caught |
| M11 | `notNow` does not record declined | **hung** (unbounded walk loop) |
| M12 | `back` does not un-decline | caught |
| M16 | a passed step stays blocked | **survived** |
| M18 | release drops the photo-frame guard | **survived** (no JVM test touches it) |

## Triage and what was done (lead, 2026-09-29)

| Finding | Action |
|---|---|
| B1 | FIXED in 0c8fe8f4: `ThemePresets.presetOnRead` (an absent key reads Custom when the items are not Default's; a fresh install still reads Default), used by `ShellSettings.read()`; JVM test; E11 gained the device sub-row "a set stored before phase 12" (no `theme_preset` key → reads Custom → HAL → `theme_custom.xml` → Custom restores the picture) |
| B2 | E11 re-runs on the fixed APK with `grants.sh` reading the persisted table itself (`su` + `abx2xml` of /data/system/urigrants.xml); plus N2's pure decision under JVM test |
| N2 / M18 | FIXED: `ThemePresets.releasable(uri, photoFrame, snapshotBackground, held)`, four gates under JVM test |
| M7, M8, M16, M11 | Tests added (blocked-then-PARTIAL reads its verb; the first walk lists PARTIAL-not-done rows; blocked cleared on a grant; walk loops bounded); lead's own mutation check on 0c8fe8f4: M7, M8, M16 and B1's removal each fail the tests (49 / 49 unmutated) |
| N5 | FIXED: `ThemePresets.variantOnRead` |
| N8 | FIXED: a key missing from both lists is dropped, never logged granted (JVM test) |
| N9 | FIXED: SecurityException takes the action-failed path (JVM test) |
| N7 | NEEDS-HUMAN.md now tells Jeremy what the paste carries and that only `[wizard]` / `[theme]` lines are needed |
| N1 | Recorded, not changed: a sweep of every unreferenced persisted grant would today touch only the background and the photo frame (the only two takers), but would silently release grants later phases take (phase 17 Photos, phase 18 Files); re-picks never releasing is phase 01's behaviour — for Jeremy / phase 19's Personalization rebuild |
| N3, N4, N10 | Recorded as notes (cosmetic; pre-existing phase 13; checked by P1 on One UI) |

## Re-verification at 3bceaaec (fix commit 0c8fe8f4) — ADVERSARIAL: PASS

Same reviewer, same rules (read-only; a fresh scratch copy; 49 / 49 baseline).

- **B1 CLEARED.** `presetOnRead` re-derived: a fresh install reads Default (E11 run 5's control PASS, "absent"); a stored id
  always wins; non-item keys never make a set Custom; the derived Custom reaches the snapshot on the first preset tap.
  Device proof: E11 run 5, sub-row "(b) a set stored before phase 12" (4 / 4). JVM: the read-rule test catches M19–M22.
- **B2 CLEARED.** E11 run 5's grant reads are real (`g-grants-1..4.txt`: A, A, A with no media permission and the
  checker drawn through the grant alone, then B only), so "A released" is no longer vacuous; both halves hold. The pure
  `releasable` decision's four gates are pinned (M23–M26 caught).
- Hardening N5 / N8 / N9 and the loop bounds verified (M28–M30 caught; M11 now fails in 14 s instead of hanging).
- Mutations: 23 of 24 caught. The survivor, M27 (the call site passing null for the photo frame), is Android-bound; no
  device row sets photo_frame == background (NOTE). N1, N4, N7, N10 stand as notes. NOTE: two KDoc lines
  (ShellSettings.kt:56, ThemePresets.kt:87) still describe an absent key as Default only.
