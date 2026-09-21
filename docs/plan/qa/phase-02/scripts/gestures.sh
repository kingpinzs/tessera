#!/usr/bin/env bash
# Phase 02 QA gestures: one continuous touch stream through `adb shell input motionevent`, so the 783-ms hold
# (R6 §1.1.1) and the 2000-ms dwell (Decisions / H20) are real waits inside one gesture, as E1 and E8 require.
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
source "$(dirname "$0")/../../phase-01/scripts/ui.sh"

down() { adb shell input motionevent DOWN "$1" "$2"; }
move() { adb shell input motionevent MOVE "$1" "$2"; }
up()   { adb shell input motionevent UP "$1" "$2"; }

# glide <x1> <y1> <x2> <y2> <steps>: MOVE events along a straight line (the finger travelling).
glide() {
  local x1=$1 y1=$2 x2=$3 y2=$4 n=${5:-6} i
  for i in $(seq 1 "$n"); do
    move $(( x1 + (x2 - x1) * i / n )) $(( y1 + (y2 - y1) * i / n ))
  done
}

# enter_edit <x> <y>: hold past 783 ms on a tile and lift without moving.
enter_edit() { down "$1" "$2"; sleep 1.1; up "$1" "$2"; sleep 0.8; }

# tile_bounds <dump.xml> <tile id>: "x1 y1 x2 y2"
tile_bounds() { bounds "$1" "tile:$2"; }

# tile_center <dump.xml> <tile id>: "x y"
tile_center() { center "$1" "tile:$2"; }
