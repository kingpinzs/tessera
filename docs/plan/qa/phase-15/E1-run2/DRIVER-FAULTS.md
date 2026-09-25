# E1 run 2: 40 passed, 1 failed — one driver fault, no product defect

"The Clock tile keeps its own face" asserted a `tile_clock_headline:` node, but with no alarm set ClockTiles publishes
nothing (ClockTiles.kt:83-84), so the tile shows its bell and label. There was no headline in start_idle.xml either
(idle and playing: `w=699 h=343 controls=no texts=Alarms & Clock`), so the negative tested nothing. Run 3 seeds an
alarm first (the AlarmClock API) so the Clock tile has a live face Music could replace, asserts that face while idle,
and deletes the alarm at the restore.
