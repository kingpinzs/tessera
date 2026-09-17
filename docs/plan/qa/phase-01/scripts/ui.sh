#!/usr/bin/env bash
# Shared QA helpers (phase 01). RV13: uiautomator dump retried up to 3 times.
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
dump() { # dump <out.xml>
  for i in 1 2 3 4; do
    if adb shell uiautomator dump /sdcard/qa_ui.xml 2>&1 | grep -q "dumped to"; then adb shell cat /sdcard/qa_ui.xml > "$1"; return 0; fi
    sleep 1
  done
  echo "dump failed after retries" >&2; return 1
}
bounds() { # bounds <xml> <resource-id> -> "x1 y1 x2 y2"
  python3 - "$1" "$2" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
m=re.search(r'resource-id="'+re.escape(sys.argv[2])+r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
print(" ".join(m.groups()) if m else "")
PY
}
center() { # center <xml> <resource-id> -> "x y" (python does the math so zsh and bash both work)
  python3 - "$1" "$2" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
m=re.search(r'resource-id="'+re.escape(sys.argv[2])+r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
if not m: sys.exit(1)
x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2, (y1+y2)//2)
PY
}
tap_id() { # tap_id <xml> <resource-id>
  local xy; xy=$(center "$1" "$2") || { echo "no node $2" >&2; return 1; }
  adb shell "input tap $xy"
}
scroll_to_id() { # scroll_to_id <xml out> <resource-id>: swipe the page up until the node is on screen (up to 8 swipes)
  for i in 1 2 3 4 5 6 7 8 9; do
    dump "$1" && grep -q "resource-id=\"$2\"" "$1" && return 0
    adb shell input swipe 540 1800 540 900 300; sleep 1
  done
  echo "no node $2 after scrolling" >&2; return 1
}
