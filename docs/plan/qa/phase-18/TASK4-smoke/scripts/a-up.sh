#!/usr/bin/env bash
# E6's fixtures: QA-Files (the table), and the MediaStore ids / absences the leg asserts on.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg a-e6
df -h / | tail -1
ensure_start; files_up; echo "files_up: PASS=$PASS FAIL=$FAIL"
for k in images video audio; do q "content query --uri content://media/external/$k/media --projection _id:_data" | grep QA-Files > "$ROW_DIR/rows-$k-before.txt"; done
cat "$ROW_DIR"/rows-*-before.txt
