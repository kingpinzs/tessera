#!/usr/bin/env bash
# Layout helpers: read, save and restore the shell's layout store (RV12 — every row restores what it changes).
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
layout_json() { adb shell run-as app.tileshell cat files/start_layout.json; }
layout_order() { layout_json | python3 -c "import json,sys; d=json.load(sys.stdin); print('order:', [o['key']+'/'+o['size'] for o in d['order']]); print('dock:', d['dock']); print('folders:', d.get('folders'))"; }
layout_save() { layout_json > "$1"; }

# layout_restore <file>: the shell is stopped FIRST, then the file is written, then it is read back and
# compared. Writing under a live shell let the running process overwrite the restore on its way out, and a
# lost restore is invisible — the gate found rows that started from the wrong layout that way.
layout_restore() {
  local want=$1 got=/tmp/qa_layout_readback.json i
  for i in 1 2 3; do
    adb shell am force-stop app.tileshell
    sleep 1
    adb push "$want" /data/local/tmp/restore_layout.json >/dev/null
    adb shell 'run-as app.tileshell sh -c "cat /data/local/tmp/restore_layout.json > files/start_layout.json"'
    layout_save "$got"
    if python3 - "$want" "$got" <<'PY'
import json, sys
a, b = (json.load(open(p)) for p in sys.argv[1:3])
sys.exit(0 if a == b else 1)
PY
    then
      adb shell input keyevent KEYCODE_HOME
      sleep 3
      return 0
    fi
    echo "layout_restore: read-back did not match $want (attempt $i)" >&2
  done
  echo "layout_restore: FAILED to restore $want" >&2
  return 1
}
