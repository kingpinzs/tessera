#!/usr/bin/env bash
# Phase 17 E23, the Camera's half (row E23_CAMERA) — App Shortcuts (phase 11 Q1's standing rule; C-8 / C-9).
#
# The row reads "hold the Camera tile". In qa/phase-17/baseline_layout.json the Camera is the bottom row's slot tile,
# tile:dock:slot:CAMERA. Whether a hold on a bottom-row tile bursts the shortcuts is not something the doc says, so:
#
#   dock      the bottom-row tile is held by phase 11 E3's method (qa/phase-16/scripts/e25.sh). If the dump taken with
#             the finger down holds quick_sat nodes, the Camera half runs on that tile. If it does not, that is
#             RECORDED with its evidence (the dump's quick_sat count, the launcher slice's [quick] lines, the
#             screencap) and the half runs on a pinned Camera app tile — `app:app.tileshell/app.tileshell.camera.
#             CameraActivity:0`, added as the first tile of a COPY of the baseline for the row, restored after.
#   burst     hold the Camera tile → quick_sat_label:0..1 = "Photo", "Video" in rank order, then the dynamic modes
#             E7's availability row predicts for this device (derived here by the same derivation: "Panorama" iff
#             arm64-v8a, "Slow motion" iff CONSTRAINED_HIGH_SPEED_VIDEO — none on the AVD), and no satellite beyond
#             them (quick_sat:<n> absent, start_page asserted in the same dump first);
#             `[quick] shortcuts for app.tileshell/.camera.CameraActivity/0: 2 (2 shown: camera_photo,camera_video)`
#             on the AVD (the activity-keyed line, T11-12).
#   dumpsys   `adb shell dumpsys shortcut` lists camera_photo / camera_video as MANIFEST shortcuts at ranks 0–1 on
#             CameraActivity, and exactly the predicted dynamic ids (none on the AVD: camera_panorama and
#             camera_slowmo absent).
#   taps      tap each satellite → CameraActivity resumed on that mode (camera_mode:<id> selected); `c6` +
#             `ensure_start` between holds (C-6; r3 V7).
#   restore   layout_restore of the baseline (asserted).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
DOCK_TILE="tile:dock:slot:CAMERA"
PIN_KEY="app:app.tileshell/app.tileshell.camera.CameraActivity:0"
PIN_TILE="tile:$PIN_KEY"
quick_line() { ring_since "$1" | grep -F '[quick] shortcuts for' | tail -1 | sed 's/.*\[quick\]/[quick]/; s/ *wall=.*//'; }

cam_install E23_CAMERA
row_begin E23_CAMERA "E23, Camera: the tile's burst (Photo, Video), dumpsys shortcut, each satellite's mode"
cam_preamble
D="$ROW_DIR"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
assert_eq "start: slots.CAMERA is the shell's Camera" "app.tileshell/app.tileshell.camera.CameraActivity" "$(slot_of CAMERA)"

# The dynamic modes E7's availability row predicts for this device.
SVC="$(media_camera)"; printf '%s\n' "$SVC" > "$D/media_camera.txt"
ABI="$(adb shell getprop ro.product.cpu.abi < /dev/null | tr -d '\r')"
CAPS="$(printf '%s\n' "$SVC" | python3 "$HERE/cam_facts.py" capabilities)"
WANT_LABELS="Photo,Video"; WANT_IDS="camera_photo,camera_video"; WANT_DYNAMIC=""
[ "$ABI" = arm64-v8a ] && { WANT_LABELS="$WANT_LABELS,Panorama"; WANT_IDS="$WANT_IDS,camera_panorama"; WANT_DYNAMIC="$WANT_DYNAMIC camera_panorama"; }
case "$CAPS" in *CONSTRAINED_HIGH_SPEED_VIDEO*) WANT_LABELS="$WANT_LABELS,Slow motion"; WANT_IDS="$WANT_IDS,camera_slowmo"; WANT_DYNAMIC="$WANT_DYNAMIC camera_slowmo" ;; esac
WANT_DYNAMIC="$(echo $WANT_DYNAMIC)"
N="$(echo "$WANT_IDS" | tr ',' '\n' | grep -c .)"
WANT_LINE="[quick] shortcuts for $CAMERA_ACTIVITY/0: $N ($N shown: $WANT_IDS)"
record "predicted for this device (ABI $ABI; capabilities: $CAPS)" "labels [$WANT_LABELS], dynamic ids [${WANT_DYNAMIC:-none}]"
[ "$ABI" = x86_64 ] && assert_eq "on the AVD the prediction is Photo, Video only" "Photo,Video" "$WANT_LABELS"

# The dynamic shortcuts are published by the Camera's own start (its publishing rule): one start before the holds.
MARK="$(ring_mark)"
open_camera; record "camera ready after (s)" "$(wait_camera "$MARK")"
assert_contains "the Camera's publishing rule ran" "[camera] shortcuts dynamic: ${WANT_DYNAMIC:+$(echo "$WANT_DYNAMIC" | tr ' ' ',')}" "$(cam_since "$MARK")"
[ -z "$WANT_DYNAMIC" ] && assert_contains "… and published none on this device" "[camera] shortcuts dynamic: none" "$(cam_since "$MARK")"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- dock: does it burst?
log "--- the bottom-row Camera tile ($DOCK_TILE) held"
MARK="$(ring_mark)"
burst_on "$DOCK_TILE" dock-hold; assert_eq "dock: the bottom-row Camera tile is on Start and was held" "0" "$?"
assert_eq "dock: the dump taken with the finger down is Start's" "yes" "$(has_node "$D/dock-hold.xml" start_page)"
DOCK_SATS="$(grep -c 'resource-id="quick_sat:' "$D/dock-hold.xml")"
DOCK_QUICK="$(ring_since "$MARK" | grep -F '[quick]' | sed 's/.*\[quick\]/[quick]/; s/ *wall=.*//' | tr '\n' ';')"
record "dock: quick_sat nodes in the dump while the finger is down" "$DOCK_SATS"
record "dock: the launcher's [quick] lines since the hold" "${DOCK_QUICK:-(none)}"
record "dock: what the hold showed instead (the dump's top-level tags)" "$(grep -o 'resource-id="[a-z_]*"' "$D/dock-hold.xml" | sort | uniq -c | sort -rn | head -8 | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 1          # whatever the hold opened (a burst, a context menu) is closed
c6; ensure_start
if [ "$DOCK_SATS" -ge 1 ]; then
  TILE="$DOCK_TILE"; PINNED=no
  record "which tile the Camera half runs on" "$DOCK_TILE — a hold on the bottom-row tile bursts the shortcuts"
else
  TILE="$PIN_TILE"; PINNED=yes
  record "which tile the Camera half runs on" "a pinned Camera app tile ($PIN_TILE) on a copy of the baseline — the bottom-row tile's hold shows no burst (evidence: dock-hold.xml, dock-hold.png, the two lines above)"
  python3 - "$BASELINE" "$D/layout-with-camera-pin.json" "$PIN_KEY" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); key = sys.argv[3]
d["order"] = [{"key": key, "size": "MEDIUM"}] + [o for o in d["order"] if o["key"] != key]
d["manualSizes"] = sorted(set(d.get("manualSizes", [])) | {key})
json.dump(d, open(sys.argv[2], "w"), indent=2)
PY
  rings_save
  layout_restore "$D/layout-with-camera-pin.json" > "$D/restore-pin.out" 2>&1; assert_eq "pin: layout_restore of the baseline copy with the Camera app tile" "0" "$?"
  ensure_start
  gdump "$D/pin-start.xml"
  assert_ne "pin: the Camera app tile is on Start" "" "$(bounds "$D/pin-start.xml" "$PIN_TILE")"
fi

# ----------------------------------------------------------------------------------------------- the burst and its taps
# One satellite: hold, assert the burst, tap satellite i, read the mode the Camera opened on.
launch() { # index mode-id tag
  local mark
  mark="$(ring_mark)"
  burst_on "$TILE" "$3" || { _verdict FAIL "$3: the burst opened" "the tile was not found on Start"; return 1; }
  assert_eq "$3: the dump is Start's (start_page)" "yes" "$(has_node "$D/$3.xml" start_page)"
  assert_eq "$3: the burst's labels in rank order" "$WANT_LABELS" "$(sat_labels "$D/$3.xml")"
  assert_eq "$3: no satellite beyond the predicted $N (quick_sat:$N absent)" "no no" "$(has_node "$D/$3.xml" "quick_sat:$N") $(has_node "$D/$3.xml" "quick_sat_label:$N")"
  assert_eq "$3: the launcher's line names only this activity's ids (T11-12)" "$WANT_LINE" "$(quick_line "$mark")"
  assert_eq "$3: quick_sat:$1 is in the burst" "yes" "$(has_node "$D/$3.xml" "quick_sat:$1")"
  local cmark; cmark="$(ring_mark)"
  tap_node "$D/$3.xml" "quick_sat:$1"; sleep 2
  wait_camera "$cmark" >/dev/null
  cgdump "$D/$3-app.xml"; screencap "$D/$3-app.png"
  assert_eq "$3: CameraActivity is resumed" "$CAMERA_ACTIVITY" "$(top_activity)"
  assert_eq "$3: camera_mode:$2 is selected" "true" "$(node_attr "$D/$3-app.xml" "camera_mode:$2" selected)"
  c6; ensure_start
}
log "--- the burst on $TILE"
launch 0 photo sat-0-photo
assert_eq "Photo: Video is not the selected mode" "false" "$(node_attr "$D/sat-0-photo-app.xml" camera_mode:video selected)"
launch 1 video sat-1-video
assert_eq "Video: Photo is not the selected mode" "false" "$(node_attr "$D/sat-1-video-app.xml" camera_mode:photo selected)"
assert_eq "Video: the shutter is camera_record" "yes" "$(has_node "$D/sat-1-video-app.xml" camera_record)"
i=2
for id in $WANT_DYNAMIC; do
  launch "$i" "${id#camera_}" "sat-$i-${id#camera_}"; i=$((i + 1))
done

# ----------------------------------------------------------------------------------------------- dumpsys shortcut
log "--- adb shell dumpsys shortcut"
adb shell dumpsys shortcut | tr -d '\r' > "$D/dumpsys-shortcut.txt"
python3 - "$D/dumpsys-shortcut.txt" > "$D/shortcuts.tsv" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r"\n\s+Package: app\.tileshell\s+UID:.*?(?=\n\s+Package: |\Z)", text, re.S)
block = m.group(0) if m else ""
for s in re.finditer(r"ShortcutInfo \{id=([^,]+),\s*flags=0x[0-9a-f]+ \[([^\]]*)\].*?activity=ComponentInfo\{([^}]+)\}.*?rank=(\d+)", block, re.S):
    kind = "manifest" if "Man" in s.group(2) else ("dynamic" if "Dyn" in s.group(2) else "other")
    print("%s\t%s\t%s\t%s\t%s" % (s.group(3), s.group(4), s.group(1), kind, s.group(2)))
PY
CAM_COMPONENT="app.tileshell/app.tileshell.camera.CameraActivity"
grep -F "$CAM_COMPONENT" "$D/shortcuts.tsv" | sort | tee -a "$LOG" >/dev/null
assert_ne "dumpsys shortcut: the shell's package block was read" "0" "$(grep -c . "$D/shortcuts.tsv")"
of_kind() { awk -F'\t' -v a="$CAM_COMPONENT" -v k="$1" '$1==a && $4==k {print $2 "=" $3}' "$D/shortcuts.tsv" | sort | tr '\n' ' ' | sed 's/ $//'; }
assert_eq "dumpsys shortcut: camera_photo / camera_video are manifest shortcuts at ranks 0–1" "0=camera_photo 1=camera_video" "$(of_kind manifest)"
assert_eq "dumpsys shortcut: exactly the predicted dynamic ids on CameraActivity" "$WANT_DYNAMIC" "$(awk -F'\t' -v a="$CAM_COMPONENT" '$1==a && $4=="dynamic" {print $3}' "$D/shortcuts.tsv" | sort | tr '\n' ' ' | sed 's/ $//')"
for id in camera_panorama camera_slowmo; do
  case " $WANT_DYNAMIC " in
    *" $id "*) assert_contains "dumpsys shortcut: $id is listed (predicted)" "id=$id," "$(cat "$D/dumpsys-shortcut.txt")" ;;
    *) assert_absent "dumpsys shortcut: $id is absent (not predicted on this device)" "id=$id," "$(cat "$D/dumpsys-shortcut.txt")" ;;
  esac
done
assert_eq "dumpsys shortcut: no camera_ id is declared on another activity" "0" "$(awk -F'\t' -v a="$CAM_COMPONENT" '$1!=a && $3 ~ /^camera_/' "$D/shortcuts.tsv" | grep -c .)"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
no_crash
rings_save
layout_restore "$BASELINE" > "$D/restore-end.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
[ "$PINNED" = yes ] && assert_eq "restore: the Camera app tile is no longer in the layout" "0" "$(layout_json | grep -c "$PIN_KEY")"
ensure_start
row_end
