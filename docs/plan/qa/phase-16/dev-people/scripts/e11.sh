#!/usr/bin/env bash
# DEV-E11 (development proof, E11's core): the People tile on Start — with three solid-colour contact photos, the
# bubble events and their motion lines over 40 s; with none, the static circle pattern. Start never idles while the
# tile cycles, so its dumps go through the gesture driver (gdump). Photos are set through People's editor and Android's
# photo picker; the fixtures and the pushed pictures are removed at the end.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E11 "the People tile: bubble events and the static pattern (E11's core)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
BEFORE="$(raw_count)"
D="$ROW_DIR"

ANN=$(fx_raw); fx_name $ANN "Ann Lee"
BOB=$(fx_raw); fx_name $BOB "Bob Stone"
ZOE=$(fx_raw); fx_name $ZOE "Zoe Aren"
for c in red green blue; do push_photo $c >/dev/null; done
declare -A COLOUR LOOK
give_photo() { # raw colour-name "r,g,b"
  local raw="$1" cid; cid="$(contact_of "$raw")"; local lookup; lookup="$(lookup_of "$cid")"
  LOOK[$raw]="$lookup"; COLOUR[$lookup]="$3"
  adb shell am start -W -n "$PEOPLE" -a android.intent.action.EDIT -d "content://com.android.contacts/contacts/lookup/$lookup/$cid" >/dev/null 2>&1; sleep 3
  dump_ui "$D/edit-$2.xml"; tap_node "$D/edit-$2.xml" people_editor_photo; sleep 3
  pick_photo "$3"
  dump_ui "$D/edit-$2-picked.xml"; tap_node "$D/edit-$2-picked.xml" people_editor_save; sleep 4
  assert_contains "$2: a photo data row exists for raw $raw" "mimetype=vnd.android.cursor.item/photo" "$(S content query --uri content://com.android.contacts/data --projection mimetype --where "raw_contact_id=$raw")"
}
give_photo "$ANN" red "254,0,0"
give_photo "$BOB" green "0,255,1"
give_photo "$ZOE" blue "0,0,254"
log "lookups: ann=${LOOK[$ANN]} bob=${LOOK[$BOB]} zoe=${LOOK[$ZOE]}"

# ---- Start, with photos: one publisher, two keys, then the events
# The MARK is taken BEFORE the stop: the shell is the home app, Android restarts it the moment it is stopped, and the
# feed publishes as the process starts (run 1 took the MARK after and read nothing).
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
sleep 2
SLICE="$(ring_since "$MARK")"
gdump "$D/start.xml"; screencap "$D/start.png"
TB="$(bounds "$D/start.xml" tile:slot:PEOPLE)"; log "the PEOPLE slot tile: [$TB]"
assert_ne "the PEOPLE slot tile is on Start's first screen" "" "$TB"
assert_eq "the tile's face is the People face (people_tile_face)" "yes" "$(has_node "$D/start.xml" people_tile_face)"
echo "$SLICE" | grep -E '\[people\] tile:|publish (feed:people|cmp:app.tileshell/app.tileshell.people)' | sed 's/.*wall=[0-9]* //' >> "$LOG"
assert_contains "the feed read three photos" "[people] tile: 3 photos" "$SLICE"
assert_contains "published under the PEOPLE slot's key" "[engine] publish feed:people " "$SLICE"
assert_contains "and under the People component's key" "[engine] publish cmp:app.tileshell/app.tileshell.people.PeopleActivity " "$SLICE"
assert_absent "never under a package key" "publish pkg:app.tileshell" "$(echo "$SLICE" | grep -F 'source=people')"

EVENTS_MARK="$(ring_mark)"
SEEN=0; SHOTS=0
END=$(( $(date +%s) + 42 ))
while [ "$(date +%s)" -lt "$END" ]; do
  N="$(ring_since "$EVENTS_MARK" | grep -c 'people_bubble_in')"
  if [ "$N" -gt "$SEEN" ]; then
    SEEN="$N"
    # A screencap right after this event's in-slide settled: the settled bubble's centre is the incoming contact's colour.
    screencap "$D/event-$N.png"
    LK="$(ring_since "$EVENTS_MARK" | grep -F '[people] tile event' | sed -n "${N}p" | sed -E 's/.*lookup=([^ ]+).*/\1/')"
    gdump "$D/event-$N.xml"
    BB="$(bounds "$D/event-$N.xml" people_tile_bubble)"
    if [ -n "$BB" ] && [ -n "${COLOUR[$LK]:-}" ]; then
      assert_color "event $N: the settled bubble's centre is the colour of lookup=$LK" "${COLOUR[$LK]}" "$(px "$D/event-$N.png" $(( ($(bfield "$BB" 1) + $(bfield "$BB" 3)) / 2 )) $(( ($(bfield "$BB" 2) + $(bfield "$BB" 4)) / 2 )))" 8
      SHOTS=$((SHOTS + 1))
    else
      log "event $N: no settled bubble in the dump (bounds [$BB], lookup [$LK])"
    fi
  fi
  sleep 0.4
done
ring_since "$EVENTS_MARK" | grep -E '\[people\] tile event|\[motion\] people_bubble' > "$D/events.txt"
cat "$D/events.txt" >> "$LOG"
assert_eq "a screencap was graded for at least three events" "yes" "$([ "$SHOTS" -ge 3 ] && echo yes || echo no)"
while IFS= read -r line; do
  _verdict "${line%% *}" "$(echo "${line#* }" | sed 's/ | .*//')" "$(echo "$line" | sed 's/.* | //')"
done < <(python3 "$HERE/tile_times.py" "$D/events.txt")

# ---- every photo removed: the static pattern, still
MARK="$(ring_mark)"
for raw in $ANN $BOB $ZOE; do
  adb shell "content delete --uri content://com.android.contacts/data --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id=$raw\"" >/dev/null 2>&1
done
sleep 10
assert_contains "the feed reads no photo" "[people] tile: 0 photos" "$(ring_since "$MARK")"
gdump "$D/pattern.xml"
assert_eq "the tile shows the static pattern" "yes" "$(has_node "$D/pattern.xml" people_tile_pattern)"
assert_eq "its root is still people_tile_face" "yes" "$(has_node "$D/pattern.xml" people_tile_face)"
PMARK="$(ring_mark)"
for i in 1 2 3 4 5 6; do screencap "$D/pattern-$i.png"; sleep 3; done
SAME="$(python3 - "$D" $TB <<'PY'
import sys
from PIL import Image, ImageChops
d, l, t, r, b = sys.argv[1], *[int(v) for v in sys.argv[2:6]]
first = Image.open(d + "/pattern-1.png").convert("RGB").crop((l, t, r, b))
print("yes" if all(ImageChops.difference(first, Image.open("%s/pattern-%d.png" % (d, i)).convert("RGB").crop((l, t, r, b))).getbbox() is None for i in range(2, 7)) else "no")
PY
)"
assert_eq "six screencaps over 15 s show no change on the tile" "yes" "$SAME"
assert_absent "no tile event while there is no photo" "[people] tile event" "$(ring_since "$PMARK")"

# ---- restore
people_fixtures_down
remove_photos
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
