#!/usr/bin/env bash
# The idle-host matrix: two builds x (checker picture | no picture). Gap 0 gets 30 trials (the race is rare there at
# idle), the other gaps 10 each; EDGE_RAPID's host loop 3 x 10 per condition.
E="$(cd "$(dirname "$0")" && pwd)"; A=/tmp/claude-1000/l13-3-tmp/apks
L="bash $E/l13_3.sh"
HL=0 $L sweep "$E/A-f90081ce-checker-idle" 30 0
for c in "B-f90081ce-none-idle 9c4d049f none" "C-bababc86-checker-idle a7ef430b checker" "D-bababc86-none-idle a7ef430b none"; do
  set -- $c
  $L setup "$A/$2.apk" "$3" > "$E/setup-$1.txt" 2>&1
  HL=3 $L sweep "$E/$1" 30 0
  HL=0 $L sweep "$E/$1" 10 50 100 150 200 300
done
echo MATRIX DONE
