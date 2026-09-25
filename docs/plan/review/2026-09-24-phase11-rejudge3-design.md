BLOCKING: 0 · SHOULD-FIX: 0 · NOTE: 3

# Phase 11 re-judge, round 3 (pass 7): Reviewer 1 (design / correctness)

Read-only review of main at 2783ef7. Commits read: 2981b06, 9805f5e, 7f46c4a, db1f364, 01f891c and 2783ef7. No product or
fixture code changed in this round: `git log b0311b3..HEAD -- app/ testapps/` is empty, so the code is still 9f790b7. The drivers
at HEAD are the ones pass 7 ran. Their blobs match: edge.sh 20006a1 (EDGE/EDGE.txt:4), l11_1.sh 79caef2 and e13.sh 4266ead.
I did not run adb, touch an emulator, touch metro-launcher-p15, build anything or read Reviewer 2's report. Paths are
repo-relative. "doc" is docs/plan/phase-11-tile-quick-actions.md.

**Verdict from this lens.** The phase 11 gate and the L11-1 fix's own gate can be called passed as evidenced. That excludes
what only Jeremy can close: H1-H11, P1-P3, P5, P6, and readings (a) and (b). The three NOTEs below do not block. Each is a
record or coverage refinement.

## Findings

| id | sev | file:line | finding | evidence | exact fix |
|---|---|---|---|---|---|
| D3-1 | NOTE | docs/plan/qa/phase-11/scripts/edge.sh:146-174; doc :630-633; app/src/main/kotlin/app/tileshell/start/EditGestures.kt:107-146 | **One of the bullet's four still-press cases is not run with a long press: the held tile with a burst open.** The bullet says a still press "of any length" in edit mode is a tap. It covers four cases: another tile or the held tile, each with or without a burst open. This is also the only case where a regression could re-open the burst on the tile that opened it. An example is a "hold the held tile again to re-burst" path. The bullet's "only the hold that enters edit mode opens a burst" is aimed at exactly that. By construction the case is safe, because edit mode's branch has no timer anywhere. The only `withTimeoutOrNull` is :90, in the not-in-edit branch, and :129 → :136 closes an open burst on any `Press.Tap`, whatever the key. | Three of the four cases are run with a ≥1.0-s still press:<br>- **Another tile, burst open.** EDGE.txt:55-58 (ring :551 `tap elsewhere`).<br>- **Held tile, no burst.** EDGE.txt:59-60 (ring :556 `tap on the held tile … exit`).<br>- **Another tile, no burst.** E5/E5.txt:66-71 (Start settings, the selection moves).<br>The fourth case, the held tile with a burst open, is run only as a quick `input tap` (e5.sh:66-75, E5.txt:27-31), and a quick tap never exercises a hold timer. edge_index.tsv's bullet-1 line lists exactly the three cases, so nothing is over-claimed. | After edge.sh:173, add:<br>`fresh_burst; read -r HX HY <<< "$(center "$ROW_DIR/B.xml" "tile:$A_KEY")"; MARK="$(ring_mark)"; hold "$HX" "$HY" 1.0; sleep 0.5; qdump "$ROW_DIR/press-held-open.xml"; S="$(quick_since "$MARK")"`<br>Then assert:<br>- `burst closed: tap elsewhere` in `$S`<br>- `0` for `grep -c 'burst on'`<br>- `edit_disc:unpin` present<br>- `[edit] tap on the held tile` absent<br>Then `c6`, and add the case to the bullet-1 line in edge_index.tsv. |
| D3-2 | NOTE | EditGestures.kt:81-84; StartPage.kt:546-549, :581; INDEX.md:101 | **On this build, phase 02's fling rule rests on T11-38's consumed-DOWN return (:84). That line's comment names only the transport control.** I infer :84 because every other exit from the hold race is ruled out (evidence column). If so, the child `verticalScroll` took the DOWN that stopped the fling. Before d34d7bb (4 inserted lines, :81-84), the same press would have raced to 783 ms and entered edit mode. Round 2 found that no phase 02 row covers flings. Pass 7 is the first run whose press actually landed mid-fling, so this is new evidence. The risk is a later change that narrows :84 to "the tile's own handler", as its comment reads today. That change would silently undo the fling rule. The EDGE fling sub-step would catch it, but only at the next gate run. | **The press is still.** It is `mt_down … sleep 1.0; mt_up` with no move between (edge.sh:103). So `awaitPress` (:434-442) gets no event before the 783-ms timeout at :90, and the UP comes at 1.0 s.<br>**It is not :80.** :80 returns on a press that hits no tile. The same point on the same page is `tile:folder:qa`: EDGE.txt:42 and fling-after.xml give folder:qa [9,857][708,1200], and the point (540,900) is 43 px inside it. The positive control there is a hold (EDGE.txt:43).<br>**The press stopped the page.** It travelled 650 px against the control's 865 (EDGE.txt:32, :36).<br>**What remains is :84.** The gesture Box (StartPage.kt:546-549) is an ancestor of the `verticalScroll` (:581), so the scroll sees the DOWN first.<br>**One step I cannot verify from the repo:** that a still sendevent contact produces no pointer event between its DOWN and its UP. | **Code comment only; no behaviour change.** In EditGestures.kt:81-83, append: "— or the grid's own scroll taking a press that stops a fling (phase 02's 'the scroll consumes the press'; EDGE's fling sub-step proves it)".<br>**INDEX.md:101.** In the phase 02 list, after "(T11-38, d34d7bb)", add: "; the same return is what keeps a still press that stops a fling from becoming a hold". |
| D3-3 | NOTE | docs/plan/qa/phase-11/README.md:91; doc :90-91, :108 | **On a full grid, every corner label lies on a dimmed neighbouring tile, not on the page, and can cross that tile's own text.** H3's line says only "label outside in the theme's text colour, both themes". The Decision's reason for the colour is that "the label sits on the dimmed page, not on the tile" (:90-91). The build is correct: :108 allows overlap with dimmed neighbours. But Jeremy is likely to judge exactly this look, and H3 does not point him to it. This is the same kind of issue as D2-3. | **Every label lands on a neighbour** (EDGE/theme-light.xml, label box against tile bounds):<br>- label 0 [145,228][323,276] lies inside PEOPLE [73,198][360,484].<br>- label 1 lies inside MAIL [714,198][1000,484].<br>- label 2 [145,1044][323,1092] lies inside CALENDAR [85,838][668,1124].<br>- label 3 overlaps shell:settings [708,992][846,1130].<br>**Pass 7 measured this first.** Its theme check reads the page ground apart from the label's ground: Light page=223 vs label ground=122 (EDGE.txt:118), Dark page=0 vs 51 (:122).<br>**What it looks like.** In EDGE/theme-light.png and theme-dark.png, "Three" crosses Calendar's "Thursday" and "Four" sits against Start settings' gear glyph. | **README H3:** after "both themes", append: "; in the corner arrangement on a full grid each label lies on a dimmed neighbour, not on the page, and can cross that tile's text (EDGE/theme-light.png, theme-dark.png: One on People, Two on Mail, Three across Calendar's 'Thursday', Four against Start settings' gear; label ground 122 vs page 223 in Light, EDGE.txt:118)".<br>If Jeremy wants a scrim or another placement, that changes the FINAL Decision at :90-91 or :108, so it goes through an INDEX Change Log line. |

## Round-2 findings

| id | status | evidence |
|---|---|---|
| D2-1 | resolved | **(1) Start is flinging when the press lands.** The control swipe (edge.sh:95) carries the page 865 px (EDGE.txt:32-33). FLING-probe shows the end is 995: its 40-ms and 80-ms swipes both stop at 995, and the 150-ms swipe reaches 849. So the control flings and does not reach the end. The pressed run travels 650 (EDGE.txt:36). That is past the finger's own 400 − 22 ≈ 378 and well short of 865, so the press landed mid-fling and stopped the page (:37).<br>**(2) The positive control.** The same raw press at the same point, with the page at rest, gives `[edit] hold 783ms on folder:qa` (EDGE.txt:43; ring :340). So `no hold` (EDGE.txt:38) is a negative that can fail. It is the only thing that differs between the two presses.<br>**(3) Optional, not done.** "no burst" and "nothing launched" still cannot fail on a folder. "no hold" carries the bullet with its control, so I do not re-raise it. D3-2 records what the product does here. |
| D2-2 | resolved | **The open's rest lines now fall before the MARK.** edge.sh:60-63 waits for the open's four `rest=` lines first (ring :140-143, 18:05:36.45). The fixture then flips three times under the open burst (ring :145, :150, :155). No `[quick]` line follows until `burst closed: stop` at :162, which is the next verb's activity.<br>**The check can fail.** The assertion is at edge.sh:74 and its result at EDGE.txt:28. QuickBurst.kt:226-236 re-arms the rest log on any change in the tracked bounds (:229 `if (!still) logged = false`). So a tracked slot that moved during a 108-ms flip would re-log and fail the check. |
| D2-3 | resolved (README half) | **README.** README:91's H3 now says a neighbour's glyph shows through at X5 100 %, and that an opaque satellite would change the FINAL Decision (doc :86-87).<br>**Pass 7 agrees.** EDGE/x5-100.png shows the People glyph under One and the Mail glyph under Two. EDGE.txt:142 reads fill [18,40,65] over [23,20,28], with α = 0.20.<br>**The product half.** The triage recorded it as Jeremy's, under "Recorded, not fixed".<br>**Two citations, one picture.** README:91 cites EDGE-pass6/x5-100.png and INDEX:88 cites EDGE/x5-100.png. Both files exist and show the same thing. |

**Round 1, not re-raised.** R1-3 and R1-4 were recorded, not fixed. Their evidence is unchanged, because nothing under app/ changed
after 9f790b7.

## What I checked and found right

1. **The keyguard sub-step tests what the bullet (:636) can mean.**
   - **The precondition now holds.** A PIN is set for the sub-step, the keyguard is asserted showing after the wake
     (EDGE.txt:71), the PIN unlocks it (:72), and the lock screen is cleared afterwards (:70).
   - **The close comes at the screen-off.** `stop` is logged 1.9 s after the hold (ring :721 18:07:50.834, :731 18:07:52.706),
     which is at KEYCODE_SLEEP and before the wake. That is the Decision's rule, Start stopping (:176).
   - **What the sub-step adds beyond E5's screen-off:** the keyguard is really present over a stopped Start, and Start comes
     back plain after the unlock (:73-74).
2. **The second finger's control isolates the right variable.**
   - **Only the first pointer is followed.** `awaitPress` (:437), `waitForUpConsuming` (:452), `dragLoop` (:188) and
     `scrollLoop` (:463) all filter on `down.id`.
   - **The control fails in the other direction.** The same slot-1 contact alone, with the burst open, closes it (`tap
     elsewhere`, EDGE.txt:52). The ring has `burst on` at :452 and then only the control's close at :466.
   - So the main step's "stays" (EDGE.txt:49-51) comes from the contact being a second pointer, not from the contact never
     reaching the app.
3. **The still press in edit mode.** The cases it runs fail where they should.
   - **Another tile, burst open.** `grep -c 'burst on'` also counts `no burst on`. So a hold regression on Tess, who has no
     shortcuts, would log `no burst on shell:cortana: no shortcuts` and fail "no second burst" (edge.sh:166).
   - **The selection** is checked through the disc bounds (EDGE.txt:58).
   - **The held tile, no burst:** it exits edit mode (EDGE.txt:59; ring :556). The fourth case is D3-1.
4. **L11-1 (d)'s new assertion identifies 9f790b7's own publish.**
   - **Only `forget()` can write the pair.** It is the only path that writes `[music] forgot` (MusicFeed.kt:206), and it
     publishes the null first (:201). The other null publishes, :168 and :216, cannot name the package once :204 has nulled
     `publishedPkg`.
   - **The check fails without the fix.** Before 9f790b7, `forget()` published nothing, so l11_1.sh's new check would fail
     on the old code.
   - **The device run:** uninstall-sequence.txt has the engine's forget at :4, then `from=music faces=0 … -> shows nothing` at
     :5, then `forgot` at :6 (L11-1.txt:52).
   - **The race window** was not produced (L11-1.txt:53), and README:31 says exactly that. R1-1's closure still rests on
     round 2's main-thread argument, which is unchanged.
5. **The flip, fling, theme, X5 and RV10 sub-steps now each have a precondition that can fail.** The ring shows the theme
   change (EDGE.txt:119, :123). The stored transparency is at each end (:134, :140). Each RV10 change is read back in the
   global configuration (:148-157). The X5 model is unchanged from round 2's check and still measures max |d| 0.0 and 0.4
   (:136, :142).
6. **The two "edit mode after …" notes record the driver's way back to Start, not phase 02's reaction to the event.** Neither
   is asserted, and the bullet leaves edit mode to phase 02, so there is nothing to fix. It is recorded so nobody reads
   "toggle → no edit mode" as a rule.
   - **After the toggle: no** (EDGE.txt:93). `set_more_tiles` returns with KEYCODE_HOME (q.sh:200), and Home exits edit mode by
     the Decision (doc :173). The ring shows :1154 `[start] home: page 0`, then :1156 `[edit] exit first frame`.
   - **After the keyguard: yes** (EDGE.txt:75). That path comes back without Home.
7. **Doc discipline and the README.**
   - **The FINAL doc.** 2783ef7 changes it only inside `## QA evidence` (hunks @@ -659 and @@ -679, doc :660-694).
   - **INDEX.** Round 2 is recorded at INDEX :88, and row 11 (:59) says pass 7, the round-3 re-judge owed, and NEEDS-HUMAN.
   - **The README's row results match SUITE.txt:4-23:** unit 651, E1-E16, L11-1 70/70, EDGE 84/84, E14 last, SUITE PASSED.
