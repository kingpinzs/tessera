BLOCKING: 0 · SHOULD-FIX: 1 · NOTE: 2

# Phase 11 re-judge, round 2 (pass 6): Reviewer 1 (design / correctness)

Read-only review of main at b0311b3. The code is at 9f790b7: `git log aaf7adc..HEAD -- app/src` lists only 9f790b7, and c78f488
changes only the testapps fixture. Commits read: 9f790b7, c78f488, 50e0f30, 585e6c3, 19a9ffa, 239e74e, f8cd00a, b2eafb8, a45b2ad,
1477e75, 6d75888, 8ff76de, b0311b3. I did not run adb, touch an emulator, touch metro-launcher-p15 or build anything. Paths are
repo-relative. I opened evidence files only where a design claim rests on them. Whether the EDGE index is complete, and whether
the smokes and each row's evidence hold up, is Reviewer 2's lens.

## Findings

| id | sev | file:line | finding | evidence | exact fix |
|---|---|---|---|---|---|
| D2-1 | SHOULD-FIX | docs/plan/qa/phase-11/scripts/edge.sh:73-87; phase-11 doc :629; app/src/main/kotlin/app/tileshell/start/EditGestures.kt:84, :88-90 | **The fling sub-step does not show that Start was flinging when the press landed, and it has no positive control.** So its three absence checks cannot tell "the scroll took the press" (the bullet) apart from "the press never became a touch on a tile". The bullet means a press that lands while Start is still moving after a flick is taken by the scroll and never becomes a hold. The sub-step's only check that can fail is "the page moved (the fling ran)" (:82), and the finger's own 950-px drag satisfies it with no fling at all. The press also landed on `tile:folder:qa`. A folder never bursts (doc :144), so "no burst" is vacuous there, and "no hold" carries the whole bullet. No phase 02 doc line or row covers flings (`grep -i fling` over phase-02-edit-mode-folders.md and qa/phase-02 finds nothing), so this sub-step is the rule's only device evidence. | **The page moved exactly the drag minus the touch slop.** `tile:folder:qa` goes from y 1507 (EDGE/fling-before.xml) to 580 (EDGE/fling-after.xml), so Δ = 927 px. The drag is 1900 → 950, 950 px (edge.sh:79), and the slop is 8 dp ≈ 22.5 px (doc :193). 950 − 22.5 ≈ 927. EDGE-smoke2-picker also gives exactly 927. Neither run shows any travel after the release.<br>**The page's end did not stop it.** With a scroll of 927, the content would end 205 px below the last tile: cortana's bottom is 1991 + 927 = 2918. StartPage.kt:342-346 reserves the gutter plus page − fixedTop, about 13 + 261 = 274 px.<br>**Where the press landed.** The press point (536,610) lies inside folder:qa at [9,580][708,923] in fling-after.xml.<br>**The absence checks pass with no input.** EDGE-smoke1-nopressure/EDGE.txt, where no touch reached the device, shows `FAIL the page moved`, then `PASS no hold`, `PASS no burst` and `PASS no edit mode, no burst`.<br>Pass 6's result agrees with the rule. The point is that this evidence cannot rule out the other explanation. | In edge.sh's fling sub-step:<br>**(1) Prove that Start is flinging at the press.** Use a fast swipe with room left below it, e.g. `input swipe 540 1900 540 1500 40`, then in the same shell `sleep 0.1` and the raw press. Assert that folder:qa's Δy is greater than 400 − 22 + 40, so the fling has carried the page past the finger's own travel before the press stops it. First, a control run with no press must show this swipe flings (Δy well above 378) and does not reach the end.<br>**(2) Add a positive control.** Once the absence checks have run and the page has rested 1.5 s, send the same `mt_down` / `sleep 1.0` / `mt_up` at the same point. Assert `[edit] hold 783ms on <the tile under the point in fling-after.xml>` in a fresh slice, then run `c6`.<br>**(3) Optional:** aim the press at a tile that bursts, so the "no burst" half can fail. |
| D2-2 | NOTE | docs/plan/qa/phase-11/scripts/edge.sh:60-69 | **The flip sub-step's "the satellites stayed still" compares two dumps that are both taken outside a flip.** A flip lasts 108 ms (`Motion.FLIP_MS`, ui/motion/Motion.kt:11). Suppose a regression tracked the held tile inside TileView's flip layer (T11-41, doc :79-82). It would squash the satellites only during each flip, and :68 would still pass. The design itself is right by construction. StartPage.kt:977-986 puts `onGloballyPositioned` on the modifier that TileView applies before its own flip `graphicsLayer` (TileView.kt:278-288). The ring already holds the check that can fail: QuickBurst.kt:221-236 writes `[quick] satellite i rest=` again whenever the tracked bounds change and then settle. | EDGE/ring-launcher.txt:139-142 is the open's one set of rest lines (15:50:48.533). :144 (15:50:48.909) and :149 (15:50:53.862) are flips of the fixture under the open burst. There is no further `[quick]` line until `burst closed: stop` at :157. The evidence passes the stronger check today. | Before the MARK at :62, wait until `rest_lines` from the hold's own mark shows 4 rows, so the open's lines fall before the MARK. After the flip wait, add `assert_eq "no satellite re-rested across the flip (the tracked slot never moved, T11-41)" "" "$(rest_lines "$MARK")"` (q.sh:136's helper). |
| D2-3 | NOTE | docs/plan/qa/phase-11/README.md:91; app/src/main/kotlin/app/tileshell/start/QuickBurst.kt:284; phase-11 doc :86-87, :108 | **At X5 100 %, a satellite is see-through to the neighbouring tile's own glyph.** Two FINAL Decisions combine to produce this. The satellite takes "the tile fill Start tiles use (accent plus the X5 transparency setting)" (:86-87). A clamped satellite "may then overlap dimmed neighbours (the burst draws above them)" (:108). At 100 %, the neighbour's icon shows through the satellite, next to the satellite's own glyph. The sub-step tests the model exactly as built, and README :91 routes the look to H3. But the H3 line says only "a fifth of the accent over the dimmed tile behind it". It does not say that a neighbour's glyph shows through, which is the part Jeremy is most likely to object to. | EDGE/x5-100.png shows the People icon inside satellite "One" [152,276,317,441] and the Mail icon inside "Two". EDGE.txt:116 reads fill [18,40,65] over [23,20,28]. | README H3: append "…and the neighbouring tile's glyph shows through it (EDGE/x5-100.png: People under One, Mail under Two)". If Jeremy rejects it, the product change is the alpha at QuickBurst.kt:284 (for example an opaque satellite fill). That change needs an INDEX Change Log line, because :86-87 is a FINAL Decision. |

## Round-1 findings

| id | status | evidence |
|---|---|---|
| R1-1 | resolved in code; the device step ran only the benign order | **Code:** MusicFeed.kt:197-207, with the null publish at :201. The full argument is in "What I checked" 1.<br>**Device:** L11-1 (d) uninstalled the player while it was PLAYING (L11-1/L11-1.txt:42-57). L11-1/uninstall-sequence.txt shows the session's death publishing the paused face (:2, 15:41:29.985) *before* the engine's forget (:4, 15:41:30.094). Forget's own null then runs as a no-op (:5, `-> shows nothing`).<br>**Limit:** R1-1's order, a death between the two halves, was not produced, and adb cannot force it. The closure rests on the main-thread argument. README :31's "(F-2, R1-1)" for (d) describes the end state, not the interleaving. |
| R1-2 | resolved | MusicFeed.kt:56 and :66-68: `forgetRegistered.compareAndSet(false, true)`, the same pattern LiveTileSystemReceiver's registerRuntime uses. |
| R1-3 | open, recorded; still acceptable | No caller sends a bare `pkg:` key through `publish()`. The callers are PhotosFeed.kt:94/:119/:178, CalendarFeed.kt:87, WeatherFeed.kt:215 and LiveTileStore.kt:203/:382/:408/:474 (`secondaryContentKey`). 9f790b7 adds only a `publishPackage` call (MusicFeed.kt:201).<br>The triage's reason (a `require` would crash the launcher) is a fair trade. If Jeremy wants the guard without the crash, the first line of `publish()` can be `if (key.startsWith("pkg:") && '#' !in key) { Diagnostics.add("engine", "refused bare $key: use publishPackage"); return }`. |
| R1-4 | open, recorded; still acceptable | Unchanged in kind. 9f790b7 adds one path of the same class. On an identity-change wipe, a new install's face published inside forget's post window is cleared as well. It returns at that session's next state or metadata callback, because `published` is null (MusicFeed.kt:146-147). MusicFeed's memory and the engine slot now agree. |
| R1-5 | resolved | e10.sh:44-71. From E10/E10.txt:<br>- `the Music tile shows its strip`.<br>- The art point (892,1086) is inside the tile [721,973][1064,1316], above the strip's top 1199, and on no control.<br>- `[edit] hold` fired, with no `[tile_control]` line.<br>- `edit_disc:unpin` is present, and the labels read `Songs,Albums,Artists,Playlists,`.<br>The row passed 19/19 (SUITE.txt:15). The control step still runs on Fossify's tile, which round 1 (#10) judged equivalent by design. |
| R1-6 | resolved | LiveTileEngine.kt:49 gives `packageSources` its own KDoc. The empty-content KDoc sits above `empty()` at :52-59. |

## What I checked and found right

### 1. 9f790b7 closes R1-1's window under every interleaving of the wipe's thread and main

The engine's forget E runs on the wipe's thread (LiveTileEngine.kt:74-82) and posts MusicFeed's `forget` F to main. Every MusicFeed publish P runs on main (callbacks registered with main-looper handlers, MusicFeed.kt:75, :99). There are three possible orders:

- **P, then E, then F.** E drops every slot. F's null finds `had` = false, so it changes nothing (LiveTileEngine.kt:89-96). The state map is an equal copy, so the StateFlow does not emit. It logs `-> shows nothing`, which is the line seen on the device at uninstall-sequence.txt:5.
- **E, then P, then F.** This is R1-1's window. P re-creates the slot and sets `publishedPkg = pkg` (:171). F sees `publishedPkg == pkg` (:198) and nulls the MUSIC slot (:201). After that, `published` is null (:202), so a late death cannot republish (:146-147).
- **E, then F, then P.** P can write the gone package's slot only if it picks a controller of that package that still has metadata (:128-146).

Further checks on the fix:

- **Other producers.** F's null removes only MUSIC. An API or NOTIFICATIONS slot re-created after E stays and wins, as `resolve` says.
- **Lock order.** It is unchanged. F takes only the engine's monitor, and it takes it on main, as every MusicFeed publish already does. The forget listener still only posts, under the store's lock.
- **A bound I could not verify.** Every controller callback captures the `controllers` list from registration time (MusicFeed.kt:88-96). So a publish after F, from another session's callback, could pick the gone package's controller again. That requires a destroyed session's `MediaController` to still return metadata. That is framework behaviour I cannot verify from the repo. The code is older than 9f790b7 and is outside R1-1's interleaving, so I record it here and not as a finding.

**A new track of the same package (R1-2).** With the listener registered once (MusicFeed.kt:56, :66-68), each wipe posts exactly one F. F can clear a new track only if that track publishes inside the E→F window:

- On an uninstall, no new install exists until a reinstall finishes, so main would have to stall for that long.
- On an identity-change wipe (R1-4's path), the new install's in-window face is cleared together with its memory, and it comes back at its next callback. This is R1-4's accepted transient.

**Nothing new breaks:**

- **Registration.** It happens before the session-access `runCatching` (:66-72). When access is denied, F simply returns at :198.
- **Drivers.** Only one driver reads engine lines across an uninstall: L11-1 (d), whose `tail -1` is now F's line (l11_1.sh:103-104). E8 (c) uninstalls tileclient-b, which has no session, so F returns early.

### 2. The two readings for Jeremy are consistent with the Decisions; neither needs a product change

**(a) A ringing call keeps the burst; answering closes it with `stop`.**

- **The rule is keyed on Start stopping** (doc :176). It is built as `onStop` → `CloseReason.STOP` (StartActivity.kt:363-367). "an incoming call" appears in the rule's parenthesis as an example, and it is true only when the call's window comes in front.
- **What the row shows.** A ringing heads-up leaves Start resumed (EDGE.txt:44-46). Answering puts InCallActivity in front and logs `stop` (:47-49).
- **Why the ring cannot close it within the Decisions.** Closing at the ring would need a ring signal. One option is a phone-state permission, which doc :213 excludes ("no new permission"). The other is reading the call's notification through the listener. It would also need a reason. Logging `stop` with Start still resumed would give that word two meanings. Any new word breaks "exactly" (:178), the diagnostics list (:208) and E14's literal set (QuickRules.kt:39-50 holds exactly ten).
- **Where the imprecision is recorded.** Doc :176 and :636 are imprecise for the heads-up case. On a FINAL doc, the INDEX Change Log line (:90) is the right place for that. A ring-time close would be a new Decision with an 11th reason, not a fix.

**(b) A listener rebind with Start in front keeps the burst.**

- **No reason fits.** None of the ten reasons is a listener event. The Decisions say a live flip under the burst changes nothing (:177), and a rebind only reconnects and rescans content (TileNotificationListener.kt:24-28). It is the same kind of event.
- **Who can trigger it.** The shell never rebinds itself: there is no `requestRebind` or `requestUnbind` in app/src/main. With Start in front, only adb or a process death restarts the listener (process death is its own EDGE sub-step).
- **What the row shows.** The user's path, Settings, stops Start, so the burst closes with `stop` (EDGE.txt:56). The in-place rebind logged 0 closes and kept the burst (EDGE.txt:59).

**(c), the whitespace label** (not asked). By construction, a null short label becomes "" and never the id: `info.shortLabel?.toString().orEmpty()` (QuickSource.kt:94). The whitespace fixture (c78f488) is a fair proxy for that path.

### 3. The EDGE sub-steps as design checks, beyond D2-1 and D2-2

- **Flip.** The bullet (:628) is met by construction, and the ring evidence confirms it (see D2-2). "The flip continues" rests on two fixture flips under the open burst (ring :144, :149).
- **Second finger (:638).** `EditGestures` follows only the first pointer: `awaitPress` :436-442, `waitForUpConsuming` :449-455, `dragLoop` :188 and `scrollLoop` :463, all filtered by `down.id`, and `awaitEachGesture` keeps a second pointer inside the first gesture. The sub-step's second finger lands on an empty point, where a single tap closes an open burst (E5's `tap elsewhere`). So "the burst stays" is a negative that can fail, checked with the first finger held, during the second finger, and after release (EDGE.txt:38-42). The other state, fingers landing on a burst that is already open, rests on the same construction and does not need its own sub-step.
- **Folder dissolve (:646-647).** The fixture is unpinned from its own burst inside `folder:qa`'s expanded band. The folder2 baseline holds two tiles, the fixture and `slot:MAPS`. The ring shows `burst closed: unpin` exactly once (:1058), then `folder qa left with one tile: dissolved into slot:MAPS` (:1059). At :1062 the close motion runs back to the tile's last centre, because `track()` keeps the last bounds once the node has left (QuickBurst.kt:89-97). The layout checks are at EDGE.txt:77-81.
- **X5 (:657).** The model is the built one:
  - StartPage.kt:418: `tileAlpha` = 1 − 0.8·transparency when a background picture is set, and 1 without one.
  - QuickBurst.kt:282-284: the satellite is the accent at `accent.alpha·tileAlpha`, under a layer alpha of 1 at rest.
  - So on screen a satellite is α·accent + (1 − α)·whatever lies behind it.
  - edge.sh:329 and :335-336 compute exactly that. They read the backdrop from a capture of the same edit-mode page with the burst closed, and that capture is asserted to have edit mode and no burst (:316-320).
  - At 100 % a wrong alpha or an opaque satellite would miss by far more than the tolerance of 14. EDGE.txt:111-117 reads max |d| 0.0 at 0 % and 0.4 at 100 %.
  - This matches :86-87 word for word. D2-3 is about the look, not the check.

### 4. Doc discipline

8ff76de changes the FINAL phase doc only by appending the QA evidence section (hunk `@@ -658,3 +658,37 @@`, doc :660-694), and INDEX Change Log :88 records it. No Decisions or Edge Cases text changed in this round. The readings live in the Change Log (:90), and INDEX row 11 (:59) leaves them to Jeremy.
