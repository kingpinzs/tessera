#!/usr/bin/env bash
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"; . "$S/reminders_fixture.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/cleanup.txt"; : > "$LOG"
reminders_restore
show_start 3
