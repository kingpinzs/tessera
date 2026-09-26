#!/usr/bin/env bash
# Deletes every QA13 reminder left by an aborted run (not row evidence).
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"; . "$S/reminders_fixture.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/cleanup.txt"; : > "$LOG"
wake_device >/dev/null; cortana_close; cortana_close
for n in 1 2 3 4 5 6; do
  open_reminders; id="$(reminder_id "QA13")"; [ -n "$id" ] || break
  set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$id"); delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); cortana_close
done
open_reminders; echo "left: [$(reminder_id QA13)]"; cortana_close; show_start 3
