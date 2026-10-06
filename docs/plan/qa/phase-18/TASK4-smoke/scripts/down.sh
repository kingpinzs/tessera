#!/usr/bin/env bash
# The floor's files_down for the fixtures an earlier script's files_up made in leg $1 (options $2…).
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg "$1"; shift
FX_SNAPPED=1; FX_UP=1; FX_OPTS="$*"
ensure_start; files_down; echo "files_down: PASS=$PASS FAIL=$FAIL"
