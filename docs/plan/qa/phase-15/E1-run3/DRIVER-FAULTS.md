# E1 run 3: 45 passed, 2 failed — two driver faults (different from run 2's), fixed; no product defect

- "E12: every fixture row is present" 16 of 17 (org.fossify.contacts missing; rows seen 32 vs run 2's 33): phase 01's
  walk swipes 1200 px in 300 ms, and the list flings on. One swipe measured on this AVD scrolled 1562 px with ONE row of
  overlap between consecutive dumps, so a slightly longer fling skips a row. Fixed at the source, in phase 01's
  e12_part1.sh (and E1's own walk, which copied it): a 1.5-s swipe scrolls 1211–1241 px with 4 rows of overlap
  (measured twice).
- "Calculator hold menu offers Pin to Start": no menu opened (menu_calc.xml shows only the list). The hold was sent
  right after the jump grid's smooth scroll, and a touch while the list moves only stops it. The driver now holds once
  the row's bounds are unchanged between two dumps.
