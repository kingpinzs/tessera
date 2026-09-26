L13-2 JVM tests: app/src/test/kotlin/app/tileshell/ui/components/ModalOverlayTest.kt (the press rule and the root's tap).

red-before.txt   the test before ModalOverlay.kt existed: does not compile (Unresolved reference 'OverlayItemPress' /
                 'OverlayRootTap'), rc=1 (red-before.rc)
green-after.txt  after the rule: 9 tests, 0 failures, rc=0 (green-after.rc)

Mutations of app/src/main/kotlin/app/tileshell/ui/components/ModalOverlay.kt, one at a time, file restored after
(cmp against the saved original: identical):
  leave-not-permanent  move(): 'if (!onItem) pressed = false' -> 'pressed = onItem'         caught: leavingTheItemEndsThePressForGood
  run-on-lift-off      lift(): 'pressed && onItem' -> 'pressed'                               caught: aLiftOffTheItemRunsNothing
  drag-dismisses       OverlayRootTap.lift(): '!downTakenByItem && !dragged' -> '!downTakenByItem'  caught: aDragOnTheRootNeverDismisses
(mutation-*.txt: each rc=1, 9 tests, 1 failure; mutations.txt)

The pointer plumbing (Main-pass consumption stopping the pivots) is proved on the device: scripts/l13_2_row.sh.
