# E1 run 1: 30 passed, 11 failed — all driver or harness faults, no product defect

- Rows not found (Alarms & Clock, Calculator: 4 fails): the app list keeps its scroll position, so after the walk it
  reopened at the bottom, and scroll_to_node only scrolls further down (12 swipes, found=no). The driver now reaches
  each row through the jump grid (the letter's cell), then looks.
- gdump failed ("(dump failed)" in window_clock.xml and both Start dumps: 6 fails, the whole Music negative). p15.sh's
  gdump ran the gesture driver with --no-restart, which attaches to a RUNNING fixture process. The fixture had been
  stopped, so am instrument threw an NPE in setActiveInstrumentation (start_playing.xml.drv). gdump now lets the
  instrumentation restart the fixture; a dump needs none of its state. Verified with the fixture force-stopped: rc=0.
- E12's search (1 fail): the driver compared the search for "fossify" against the installed org.fossify.* PACKAGES.
  The search matches labels, and phase 01's own E12 recorded an empty result for it ("results for 'fossify': ").
  The driver now requires the re-run to give what phase 01 recorded.
