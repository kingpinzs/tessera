# L13-3 — investigation: a hold right after the Back that closed the app-list band is not taken

Ledger row L13-3 (INDEX.md). Diagnosis only: no product code changed, nothing committed or pushed. Background session,
2026-09-26 09:55-11:50 MDT, AVD tileshell_fhd2 on emulator-5556 (emulator-5554 not touched). Evidence:
`qa/phase-13/L13-3-investigation/` (every path below is relative to it unless it starts with `app/`).

## Answer

1. **Reproduced: yes**, on an idle host, on both builds (the code of 0eb36905 and of 509f6e49), with and without a Start
   picture. Only a down that lands about one frame after the Back is lost; at 50 ms or more after Back, 200 of 200 holds
   opened the band.
2. **Cause:** Back sets `menu = null`, but the band leaves the tree only at the next recomposition. A down handled
   before that frame is hit-tested against the stale band, whose full-size scrim sits above the list as a sibling and
   takes the down; the row never sees it (diagnostic build: 2 of 2 misses went to the scrim, 58 of 58 hits came after
   the band left). The race is in phase 02's band structure, not in L13-2's ModalOverlay: the pre-L13-2 build drops
   holds in the same loop.
3. **Why EDGE_RAPID read 1 of 10:** one dropped hold desynchronises the loop. The next Back finds no band and pivots
   to Start; the next hold's touch lands mid-pivot and interrupts it. That interruption **kills StartActivity's Back
   and Home handling for the rest of the activity's life**. This is a second, separate defect (phase 01), and a person
   can reach it with one tap on the drawn Back followed by a tap on the list 150 ms later.

## Setup

| APK (installed on 5556) | sha256 / md5 prefix | What it is |
|---|---|---|
| 9c4d049f.apk | f90081ce / 33984f11 | commit 9c4d049f = the code of apk 0eb36905 (same classes and assets; dex order differs) |
| a7ef430b.apk | bababc86 / 7b7341fa | commit a7ef430b = the code of apk 509f6e49 |
| diag-9c4d049f.apk | 815fe1e4 / 1ed75a36 | 9c4d049f + `diag-instrumentation.diff` (ring lines only) |
| pre-L13-2-2126f8c9.apk | 6ecc67f6 / 5be8a81f | 2126f8c9, the parent of the first L13-2 commit de57ec35 |

All four were built in a detached worktree outside the main tree (`/tmp/claude-1000/l13-3-wt`, since removed). The
lead's rebuilt main-tree APK (55dbde06) was copied but never installed. Details are in `apks.txt`.

Drivers: `l13_3.sh` (it sources `scripts/lib.sh` and `p13.sh` read-only; no file under `qa/*/scripts/` was edited).
Every hold is `input swipe 540 1206 540 1206 850` on the app list's 3rd row, EDGE_RAPID's row. Acrylic is on.
- **trials**: an isolated trial. Hold A opens the band, then 100 ms, then Back, then a gap of G ms, then hold B. All of
  it runs in one on-device shell, bracketed by device timestamps. A context-menu ring line inside hold B's window
  means B opened; the dump and screencap after every miss are kept (`tN-after.xml/png`).
- **hostloop**: EDGE_RAPID run2-4's exact loop (host adb: hold; Back; no sleeps) × 10.
- G = 0 means about 4 ms from the Back command's return to the start of the next command. The down was handled 11-16 ms
  after the Back (diagnostic timestamps).

## Reproduction

Idle host (load average 3-5 on 32 threads, the other session's emulator running).

| Build | Picture | gap 0 | gap 50 | 100 | 150 | 200 | 300 | EDGE_RAPID host loop (bands per loop) |
|---|---|---|---|---|---|---|---|---|
| 9c4d049f (0eb36905) | checker | 38 / 40 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10, 10, 10 |
| 9c4d049f (0eb36905) | none | 27 / 30 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10, **4**, 10 |
| a7ef430b (509f6e49) | checker | 28 / 30 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | **4**, **9**, 10 |
| a7ef430b (509f6e49) | none | 29 / 30 | 10/10 | 10/10 | 10/10 | 10/10 | 10/10 | 10, **1**, 10 |
| diag (9c4d049f + lines) | checker | 39 / 40 (+19 / 20 exploratory) | — | — | — | — | — | 10, 10, 10; cascade run **5** |
| pre-L13-2 (2126f8c9) | checker | 60 / 60 | — | — | — | — | — | **7**, **8**, 10 |

Summaries: `A-…/`, `B-…/`, `C-…/`, `D-…/`, `F-…/`, `K-…/` (each has a `SUMMARY.txt`), `H-diag815fe1e4-cascade/`,
`explore/index.txt`.

- Every one of the 11 missed trials left the app list on screen with no band. Nothing else happened (`tN-after.xml`).
- **The build is not the variable.** Both builds miss at gap 0 (5 / 70 and 3 / 60) and in the host loop. The
  a7ef430b build reproduces the kept runs' "1 of 10" at idle (`D-bababc86-none-idle/hostloop-105429/`). Kept run2's
  10 / 10 on 509f6e49 fits a run in which no down fell in the window: 3 of this build's 6 loops here read 10 / 10.
- **The picture is not the variable.** Misses occur with and without it.
- **Pre-L13-2 drops holds too**: its host loops opened 7, 8 and 10 bands of 10. Its scrim is the topmost hit as
  well (see the root cause), which is why it drops holds at all. It dropped none of its 60 isolated gap-0 trials; that
  count is reported as measured, not explained.

### Host load

Not separated by measurement.
- The one gradle build meant as load finished (10:22:33) before the loops began. Those loops are kept as idle
  (`explore/hostloop-idle-after-build*`).
- A stress-ng run confined to this emulator's CPUs was stopped after 8 trials by the session's permission check,
  because the host is shared with the other session's emulator. It was not repeated (`E-…/ABORTED.txt`).
- What the data does show: load is not needed to produce the symptom. The idle host reproduces it, the kept runs'
  shape included. Load can only widen the window, which is the time from the Back to the next frame. Measured at
  idle, that time is:
  - 2-5 ms in 34 of 35 diagnostic host-loop cycles, 7-17 ms in the 60 trials;
  - 18 ms in the one host-loop cycle that missed.

  A slower frame lengthens the window; a loaded host would plausibly miss more often, but that is not measured.

## Mechanism (diagnostic build, `diag-instrumentation.diff`)

The diagnostic lines record:
- the Back that closes the band;
- which detector took the next down (the band's scrim or the row), with the event time and the handling time;
- when the band left composition.

Parsed by `diag_parse.py`:

```
explore/diag-815fe1e4-trials-g0/t11  (MISS)            F-…/g0/t27 (MISS): same shape
  back closes band              now=1895158
  scrim down eventUptime=1895171 now=1895172 byItem=false     <- the down, 13 ms after Back, goes to the stale scrim
  scrim gesture ended tapOff=true                              <- the removal's synthetic cancel reaches the scrim
  band left composition         now=1895174                    <- the recomposition, 16 ms after Back
  (no "row down"; no hold)
explore/diag-815fe1e4-trials-g0/t10  (hit)
  back closes band              now=1891063
  band left composition         now=1891073                    <- 10 ms after Back
  row down eventUptime=1891076  now=1891077                    <- the down reaches the row; the hold opens the band
```

Over 60 gap-0 trials:
- The down went to the scrim twice. Both were handled before the band left.
- The down went to the row 58 times. Every one was handled after the band left: `python3 diag_parse.py
  explore/diag-815fe1e4-trials-g0 F-diag815fe1e4-checker-idle/g0` prints "True" for both checks.
- Back to band-left took 7-17 ms (median 13).

The ordering of the band's removal against the down is the whole story.

## Root cause (producer: phase 02's band, H21; the same shape in the two jump grids)

- `app/src/main/kotlin/app/tileshell/applist/AppListPage.kt:238` —
  `BackHandler(enabled = visible && menu != null) { menu = null }`. Back only writes state. The band stays in the tree
  until the next recomposition.
- `AppListPage.kt:322-343` — the band is composed conditionally (`menu?.let { PinToStartMenu(…) }`) in an overlay box
  that is a later sibling of the list's box (`:285`).
  - Compose hit-tests siblings top-down and stops at the first one hit, unless that child shares pointer input with
    its siblings (compose-ui 1.12.1: `InnerNodeCoordinator.hitTestChild` →
    `PointerInputSource.shareWithSiblings` → the child's `NodeCoordinator.shouldSharePointerInputWithSiblings`).
  - While the stale band is placed, the list and its rows are not in the down's hit path at all.
- `app/src/main/kotlin/app/tileshell/applist/AppListMenu.kt:122-127` — the band's scrim fills the list's rectangle and
  carries a pointer detector: `modalOverlay` since L13-2 (`ui/components/ModalOverlay.kt:26-31`,
  `awaitFirstDown(requireUnconsumed = false)` then `down.consume()`). Before L13-2 it carried the phase 02 tap
  detector (7c3cdbb5, 2026-09-21).
  - In both forms it is the topmost hit, so it takes the down whether or not it consumes it.
  - That is why the pre-L13-2 build loses holds too.
- `AppListMenu.kt:69-71` (`HoldRow`) never receives the down.
  - When the band leaves one frame later, the scrim's gesture is cancelled.
  - A pointer's hit path is fixed at its down, so the rest of the gesture reaches no row.
  - The hold is simply gone.
- **The same shape exists in:**
  - the app list's jump grid (`AppListPage.kt:237` BackHandler, `:345-354`, `:615`);
  - Music's jump grid (`music/MusicCollectionPage.kt:772` BackHandler, `:780`).

  A tap on a row, not only a hold, is lost the same way.

## Why the kept runs show "1 of 10, then nothing": a second defect (phase 01)

Cascade on the diagnostic build (`H-diag815fe1e4-cascade/hostloop1/slice.txt`):
1. Hold 6's down goes to the stale scrim, as above.
2. Back 7 finds no band. StartActivity's Back collector pivots to Start (`back on app list: pivot to Start`).
3. Hold 7's down arrives 14 ms later, mid-pivot. The pager takes it as a drag at once (`pager Start`), which cancels
   the `animateScrollToPage` that Back 7 started.
4. From then on, no Back is handled at all: Backs 8-10 log nothing. Holds 8-10 land while the pager is still
   scrolling, and the pager takes each one as a drag, so no band opens.
5. One long `[motion] pivot` (177 frames) is written when the scrolling finally stops.

   Run3/run4's driver read its slice right after the loop, which is before that line would be written. This probably
   explains why their slices hold no pivot line; the kept runs cannot confirm it, because their rings stop there.

Isolated, deterministic, on the plain build (`I-back-interrupt/`, driver `back_interrupt.sh`):

| Case | After the first key | Later keys on the app list |
|---|---|---|
| Back alone (control) | Start | Back → Start, twice |
| Back, then a still press 15 ms later | app list (the drag snapped back) | **Back does nothing, twice** |
| Home alone (control) | Start | Home → Start, twice (3 `[start] home` lines) |
| Home, then a still press 15 ms later | app list | **Home does nothing, twice** (0 `[start] home` lines) |
| drawn nav-bar Back, then a press 30 ms later | app list | **drawn Back and system Back do nothing** |
| drawn nav-bar Back, then a tap 150 ms later | Start | **drawn Back does nothing, twice** |

- **Cause.** Two blocks call `pager.animateScrollToPage(0, …)` (and Home also calls `scroll.animateScrollTo(0)`)
  inside a `collect { }` of a `LaunchedEffect(Unit)`:
  - `app/src/main/kotlin/app/tileshell/StartActivity.kt:149-161` (the Back collector, 254fe952, phase 01);
  - `StartActivity.kt:133-147` (the Home collector, c6b7235c).

  A user drag cancels that animation. The CancellationException ends the `collect`, which ends the effect, and
  nothing restarts it. From then on, `backEvents` and `homeEvents` (the drawn Back and Windows keys included) are
  emitted into flows that nobody collects, until StartActivity is recreated. Nothing is logged when it happens.
- **Who can hit it:** anyone who taps Back or Windows on the app list and touches the screen during the 250-ms pivot.
- **Fix direction, for its own plan:** keep each collector alive when one animation is cancelled. For example, run
  the animation in a child `launch` from the effect's scope, or catch the cancellation of the animation only. The
  regression check is the table above.
- Recommend its own ledger row (phase 01's part). It is not L13-3, but L13-3's re-test cannot pass while it stands:
  any dropped hold turns into a dead Back.

## A third finding on the way (L13-2's shared rule): an overlay item runs when its overlay is removed under the finger

`J-item-cancel/` (driver `item_cancel.sh`, plain build):
- Setup: a finger held on the band's "Pin to Start" (at 540,1335) for 1.2 s, with Back pressed 0.3 s in.
- Result: `[applist] pin to Start … -> already on Start` is logged 44 ms after the Back, about 870 ms **before** the
  finger lifted.
- Control: the same press with no Back runs Pin on the lift, as it should.

Cause:
- `ui/components/ModalOverlay.kt:70-75`. `overlayItem` tests `changedToUpIgnoreConsumed()`.
- When the band leaves, Compose sends its detectors a synthetic, consumed "up" as the cancel (the same event shows as
  `scrim gesture ended tapOff=true` in the diagnostic trace above). `overlayItem` reads that cancel as a lift on the
  item and runs it.
- On "Uninstall" this opens the system uninstall dialog. The Music and app-list jump cells share the rule.

Reaching it takes a removal while an item is held: a system Back with the other hand, or the page stopping. The
L13-3 window can also cause it, if a down lands on an item within one frame of a Back. A person is unlikely to do
this, but it is real. It lives in the same file as fix A below, so its plan and test would sit alongside A's.

## Can a person hit L13-3 itself?

Unlikely, for two reasons.
- **The window is short.** It runs from the Back to the next frame: 7-18 ms measured at idle, about half that at the
  phone's 120 Hz. The next touch has to be handled inside it.
- **The on-screen Back never reaches it.** With the band open, the drawn nav-bar Back pivots to Start and the band
  closes with the page (`L-drawn-back/`). It goes to StartActivity's collector (`StartActivity.kt:253-256`), not to
  the band's BackHandler. Only a system Back (key or gesture) runs `AppListPage.kt:238`.

Under a long UI stall right after a system Back the window grows; that is unmeasured here. The consequence is mild: one
hold or tap is ignored. What made EDGE_RAPID look alarming was the second defect.

## Fix options for L13-3

**A — A dismissed overlay lets go of input at the moment it is dismissed, not at the next frame (producer; L13-2's
shared rule).**
- Owner: `ui/components/ModalOverlay.kt` (de57ec35), plus the three call sites:
  - phase 02's band (`AppListMenu.kt:126`);
  - phase 01's jump grid (`AppListPage.kt:615`);
  - phase 10's Music grid (`MusicCollectionPage.kt:780`).
- Shape:
  - Each call site passes its live state, for example `active = { menu != null }`.
  - The overlay's container, which is the list's sibling (`AppListPage.kt:322`, the Music equivalent), carries a
    pointer-input node whose `sharePointerInputWithSiblings()` returns `!active()`.
  - A down that reaches the scrim after dismissal is neither consumed nor acted on.
  - Result: in the stale frame the down also reaches the row.
- The flag has to sit on the container, not the scrim. Compose decides sibling sharing per child layout node (see the
  root cause).
- Plus: a real test of the race, the gap-0 trials with the diagnostic lines. Before the fix, misses show `scrim down`
  and no `row down`. After it, every down has a `row down`. L13-2's row (`l13_2_row.sh`) is re-run so the overlays are
  proven still modal while active.
- Cost and risk: new code in a shared, phone-verified component, and it leans on Compose's sibling-sharing hit test.
  It must be proven on the device first, the way L13-2's Q1 risk was.
- The overlayItem cancel finding would naturally ride along (same file, same tests).

**B — Accept the one-frame race; change nothing in the band; fix only what a person can hit.**
- Record on the ledger row that a down within about one frame of a system Back is dropped. EDGE_RAPID's 0.2 s after
  Back stays.
- Fix the Back/Home collector death (phase 01, its own row) and the overlayItem cancel (L13-2's rule, its own row).
- Cost: none in the band. Risk: a system-Back user who hits a UI stall loses one hold or tap.

**Lean: B for L13-3, with the collector-death row planned first.**
- The measured window is about one frame and is not reachable from the on-screen Back.
- The defect that turns one dropped hold into a broken launcher is the collector death, and a person can reach it with
  one hand.
- A is the right shape if Jeremy wants the window closed at its producer, for system-Back users or under jank. It
  should be proven on the device before it is claimed.

## What was not done

- **Host-load separation by measurement:** see "Host load" above.
- **Evidence from the kept runs themselves:** run3/run4's rings stop at their slices. Their exact cascade on 5554
  (for example, what lay under 540,1206 on Start) is inferred from the reproduction here, not read from their logs.
- **Pinned tile on 5556's layout:** the item-cancel control pinned Aves to Start on 5556's layout (my AVD only).
