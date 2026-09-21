#!/usr/bin/env bash
# Layout helpers: read, save and restore the shell's layout store (RV12 — every row restores what it changes).
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
layout_json() { adb shell run-as app.tileshell cat files/start_layout.json; }
layout_order() { layout_json | python3 -c "import json,sys; d=json.load(sys.stdin); print('order:', [o['key']+'/'+o['size'] for o in d['order']]); print('dock:', d['dock']); print('folders:', d.get('folders'))"; }
layout_save() { layout_json > "$1"; }
layout_restore() { # layout_restore <file>
  adb push "$1" /data/local/tmp/restore_layout.json >/dev/null
  adb shell 'run-as app.tileshell sh -c "cat /data/local/tmp/restore_layout.json > files/start_layout.json"'
  adb shell am force-stop app.tileshell
  adb shell input keyevent KEYCODE_HOME
  sleep 3
}
