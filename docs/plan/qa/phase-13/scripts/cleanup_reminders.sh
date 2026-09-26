#!/usr/bin/env bash
# Removes E3's reminder fixture (the two QA13 reminders and the split picture) when a row aborted before its own
# restore (EDGE_SCREEN_OFF-run1-photo-tag, 2026-09-26). Evidence of the restore, not a row of the doc.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"
row_begin CLEANUP_REMINDERS "remove E3's reminder fixture left by an aborted row"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
open_reminders
note "before: QA13 rows: $(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/reminders.xml" | grep -c 'QA13')"
cortana_close
reminders_restore
show_start 3
row_end
