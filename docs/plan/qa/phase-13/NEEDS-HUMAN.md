# Phase 13 — NEEDS-HUMAN rows and phone rows

Hard Rule 15: anything only Jeremy can verify gets a row here, and `done` requires his sign-off on each one. Nothing
below is a defect. Every acrylic and Reveal value is an approximation from Microsoft's Fluent documentation (Fluent is
not a W10M original), so these are "accept this Fluent design" rows. H3 alone is a fidelity row, judged against R3 A18.
The emulator gate passed on 2026-09-26 (gate round 3, both reviewers; INDEX Change Log).

To see it on the phone: build 0eb36905's release twin (the build of 9c4d049f), set a Start background picture in
Settings > Start + theme, then open the surfaces below. The switch is Settings > Start + theme > Effects > Transparency
effects.

| H | What Jeremy judges | Why it is his call | Where to look (AVD captures; the phone's own are owed, P4) |
|---|---|---|---|
| H1 | The acrylic look on every surface: the app-list backdrop, the app-list hold band, the Music menus (hold menu and now-playing's `•••`), Tess's ≡ pane, the reminder long-press menu | a Fluent design, no W10M original | `E2/applist-fhd.png`; `E4/band.png`, `E4/plmenu.png`, `E4/npmenu.png`; `E5/pane-home.png`, `E5/pane-rem.png`; `E3/menu-tomorrow.png`, `E3/menu-photo.png`; light theme `EDGE_LIGHT/applist.png`; high-contrast text `EDGE_A11Y/applist-hightext.png`. Display inversion cannot be screencapped (the colour transform is applied after capture): judge it on the phone |
| H2 | Blur radius 30 epx, tint opacity 0.8, 2 % noise, no exclusion layer | approximations from Fluent's desktop recipe | the captures in H1; measured: blur width 128.8–134 px at 3 px/epx (`BS_STEP`, `E2`), noise σ 3.21 levels (`E6`) |
| H3 | Fidelity (R3 A18, MEDIUM): the app list shows the Start wallpaper through a dark layer — blurred with acrylic on, unblurred off — and the wallpaper moves with the pivot page | A18 sampled the colour only; the pivot behaviour was never measured | on / off: `E1/on-0.png`, `E1/saver.png`, `E2/applist-fhd.png`, `E2/applist-off.png`. The pivot half has no capture (E8 sets no picture): swipe Start ↔ app list on the phone with a picture set |
| H4 | The two lights on press: a 1-epx border ring (white 30 %) and a radial light under the finger (r 40 epx, 10 % falling to 0) that follows the finger, on menu rows, pane items and phase 11's satellites; none on tiles or plain pages | Fluent's pointer Reveal adapted to touch (Q3 B) | `E7/*-U.png`, `*-D.png`, `*-M.png`, `*-A.png` for (a) reminder menu, (b) pane, (c) satellites, (d) band, (e) Music menu; controls `E7/ctl-*.png`; `explore/lights-*.png` |
| H5 | The "Transparency effects" toggle: wording, placement under "Effects" on Start + theme, default On | Windows 10's own wording, placed by the agent | the page on the phone (the AVD run kept a dump, `E1/settings-theme.xml`, not a screencap) |
| H6 | Acrylic turning off under battery saver | Windows' rule | `E1/saver.png`; `EDGE_SAVER_MENU/menu-saver.png` |
| H7 | Any surface the tint derivation leaves solid | none in this phase (phase 04's action center is its own interview) | nothing to judge |
| H8 | Whether the shell should follow One UI's "Reduce transparency and blur" | phone only | P3's probe |
| H9 | The feel with acrylic on, on the phone | phone only | P2's numbers, P4's captures |

## Phone rows (S25 Ultra)

| P | What | How |
|---|---|---|
| P1 | R4's cross-window blur results, as information for phase 04 | waits on R4 (INDEX Research gating); phase 13 waits for nothing |
| P2 | Frame pacing and memory at 1440 × 3120: janky ≤ 5 %, 99th percentile ≤ 2 vsyncs, launcher PSS ≤ phase 03 P4's baseline + 30 MB with the app-list layer built | `scripts/e9.sh` against the phone, with `dumpsys display` before and after; phase 03 P4's PSS baseline is not recorded yet, so it is measured first |
| P3 | Samsung Power saving on / off turns acrylic off / on (the `[fluent]` lines), over Wireless debugging with the phone unplugged; and the "Reduce transparency and blur" probe (`settings list global/system/secure` diffed across the toggle) | a phone on USB is powered, as the AVD is (C-18), so battery saver needs it unplugged |
| P4 | In-app acrylic looks as on the AVD | screencaps of the H1 surfaces on the phone |

## Open questions for Jeremy (INDEX Change Log, 2026-09-26)

- E8's reminder-menu band rejects about one correct open in six: `[motion] reminder_menu` stamps t0 inside the
  half-height frame while the doc measures from that frame. Fix the log's clock (a one-frame change to when the menu
  first draws) or re-cut E8's band (a FINAL-doc change)?
- The Back race: holds pressed right after the Back that closed the app-list band opened 1 of 10 twice on 0eb36905 and
  10 of 10 on 509f6e49 at the same cadence. The cause is not isolated (host load, or L13-2's ModalOverlay).
