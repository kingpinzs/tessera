#!/usr/bin/env bash
# Phase 16 E13 — Create, edit, photo, delete (WRITE_CONTACTS), clause by clause from the phase doc's row E13.
#
#   fixtures   people_fixtures_up (phone-only, which Q-16-3 keeps editable with nothing on "Can edit")
#   new        New → people_editor_account reads "Phone" and offers no other row while nothing is allowed →
#              "Dan Ford" with a mobile number → the doc's data query shows the number under a NEW raw contact whose
#              account_name and account_type are NULL
#   edit       Ann's number edited → the data row changes
#   photo      Photo → Android's photo picker (dumpsys activity activities) → a pushed solid-colour JPEG chosen → a
#              vnd.android.cursor.item/photo data row for Ann; people_card_photo's centre pixel = its colour ± 8
#   delete     Dan deleted → his contact row is gone
#   revoked    pm revoke WRITE_CONTACTS → the editor says it cannot save and offers the grant in place (people_notice);
#              Setup shows checklist:people:partial while Tess's contacts row still reads granted
#              (cortana_check:contacts:granted); pm grant restores checklist:people:granted
#   restore    pm grant WRITE_CONTACTS (asserted held), people_fixtures_down, the pushed JPEG removed
#
# The `[people] write <op> raw=<id>: ok | failed <err>` lines of each step are asserted too: they are this row's
# producers for E21 (the row's own clauses are the provider reads).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"

row_begin E13 "create, edit, photo, delete; WRITE_CONTACTS revoked"
require_build
D="$ROW_DIR"

# ================================================================================================ E13_LEGS (narrow runs)
# The owner's ruling (2026-10-01): only what changed is run again. E13_LEGS names the legs to run INSTEAD of the row:
#   create   gate review A, finding 3 (product commit edc15843): New → name + number → Save on the phone's own account.
#            `[people] write insert raw=<id>: ok`; the row is in the phone's account (NULL / NULL); exactly one raw
#            contact was added; and NO delete of it follows (a slice from a MARK taken before Save, read 9 s after it).
#   grant    gate review A, finding 10 (product commit dcf28edc): Contacts not allowed → People's notice → the grant
#            made in place → `[app] feeds started (people grant)`; then a photo given to a contact reaches the People
#            tile on Start, and a photo removed from outside the shell leaves it, with the SAME process throughout.
# Each leg makes and removes its own fixtures; the run ends through row_end.
leg_create() {
  local name="Eve Hart" number="+1 555 000 0142" live0 max0 mark phones new slice new_rows row
  log "=== leg create: New → name + number → Save on the phone's own account (gate review A, finding 3)"
  ensure_start
  perm_ensure READ_CONTACTS WRITE_CONTACTS
  assert_eq "create: READ_CONTACTS and WRITE_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
  assert_eq "create: nothing is on \"Can edit\" (people_edit.json allows no account)" "" "$(people_allowed)"
  q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/c-raw-before.txt"
  live0="$(raw_count)"; RAW_BEFORE="$live0"
  max0="$(q "content query --uri $RAW --projection _id" | sed -n 's/.*Row: [0-9]* _id=\([0-9]*\).*/\1/p' | sort -n | tail -1)"
  note "create: $live0 live raw contacts before; the highest raw_contacts _id (deleted rows included) is ${max0:-none}"
  assert_eq "create: no raw contact is named $name before (deleted rows included)" "0" "$(q "content query --uri $RAW --projection _id --where \"display_name='$name'\"" | grep -c '_id=')"
  assert_eq "create: no phone row holds $number before" "0" "$(q "content query --uri $DATA --projection data1 --where \"mimetype='vnd.android.cursor.item/phone_v2'\"" | grep -cF "data1=$number")"

  open_people -a android.intent.action.MAIN
  wait_node "$D/c-list.xml" people_bar:add 8 || true
  tap_node "$D/c-list.xml" people_bar:add; sleep 2
  dump_ui "$D/c-new.xml"; screencap "$D/c-new.png"
  assert_contains "create: New opens the editor (people_page:editor selected)" 'selected="true"' "$(node_tag "$D/c-new.xml" people_page:editor)"
  assert_eq "create: people_editor_account reads \"Phone\" (the phone's own account)" "Phone" "$(xml_text "$D/c-new.xml" people_editor_account)"
  set_field people_field:name "$name"
  set_field people_field:phone "$number"
  dump_ui "$D/c-filled.xml"; screencap "$D/c-filled.png"
  assert_eq "create: the name field holds what was typed" "$name" "$(xml_text "$D/c-filled.xml" people_field:name)"
  assert_eq "create: the phone field holds what was typed" "$number" "$(xml_text "$D/c-filled.xml" people_field:phone)"
  assert_eq "create: nothing is in the provider before Save is tapped" "$live0" "$(raw_count)"
  mark="$(ring_mark)"                                        # BEFORE Save: the slice below holds everything Save did
  tap_node "$D/c-filled.xml" people_editor_save; sleep 3
  dump_ui "$D/c-after-save.xml"; screencap "$D/c-after-save.png"
  phones="$(q "content query --uri $DATA --projection raw_contact_id:mimetype:data1 --where \"mimetype='vnd.android.cursor.item/phone_v2'\"")"
  printf '%s\n' "$phones" > "$D/c-phones-after-save.txt"; printf '%s\n' "$phones" >> "$LOG"
  new="$(printf '%s\n' "$phones" | grep -F "data1=$number" | sed -n 's/.*raw_contact_id=\([0-9]*\),.*/\1/p' | head -1)"
  assert_ne "create: the data query shows the number under a raw contact" "" "$new"
  [ -n "$new" ] && echo "$new" >> "$ROW_DIR/people-fixtures.ids"      # removed by people_fixtures_down in the restore
  assert_eq "create: … exactly one phone row holds it" "1" "$(printf '%s\n' "$phones" | grep -cF "data1=$number")"
  # Every raw_contacts row above the highest _id before Save, deleted ones included, as "_id:deleted".
  new_rows="$(q "content query --uri $RAW --projection _id:deleted" | sed -n 's/.*Row: [0-9]* _id=\([0-9]*\), deleted=\([0-9]*\).*/\1:\2/p' | awk -F: -v m="${max0:-0}" '$1 > m' | tr '\n' ' ' | sed 's/ $//')"
  assert_eq "create: exactly ONE raw contact was added and it is live (every row above the highest _id before Save, as _id:deleted)" "${new:-?}:0" "$new_rows"
  assert_eq "create: the live raw-contact count is one more than before Save" "$((live0 + 1))" "$(raw_count)"
  row="$(raw_row "${new:-0}")"; log "$row"
  assert_contains "create: it is named $name" "display_name=$name" "$row"
  assert_contains "create: it is in the phone's own account: account_name and account_type are NULL (Q-16-3)" "account_name=NULL, account_type=NULL" "$row"
  # No delete of it follows. The take-back (edc15843) runs inside the save; the slice is read 9 s after Save all the same.
  sleep 6
  slice="$(ring_since "$mark")"; printf '%s\n' "$slice" > "$D/c-slice-from-before-save.txt"
  printf '%s\n' "$slice" | grep -F '[people] write' | sed 's/^.*wall=[0-9]* //' >> "$LOG"
  assert_contains "create: the slice from the MARK before Save holds [people] write insert raw=<id>: ok" "[people] write insert raw=${new:-?}: ok" "$slice"
  assert_eq "create: … exactly one write line is in that slice (the insert)" "1" "$(printf '%s\n' "$slice" | grep -cF '[people] write ')"
  absent_in "create: NO delete of it follows — no \`[people] write delete raw=<id>\` line in that slice, read 9 s after Save" "[people] write delete raw=${new:-?}:" "$slice"
  absent_in "create: … and no write delete line for any raw contact" "[people] write delete" "$slice"
  absent_in "create: … and no People line in it reports a failure" ": failed" "$(printf '%s\n' "$slice" | grep -F '[people] ')"
  absent_in "create: … or a refusal" ": refused" "$(printf '%s\n' "$slice" | grep -F '[people] ')"
  assert_eq "create: 9 s after Save the raw contact is still live (deleted=0)" "1" "$(q "content query --uri $RAW --projection _id:deleted --where \"_id=${new:-0} AND deleted=0\"" | grep -c '_id=')"
  assert_contains "create: … still in the phone's account" "account_name=NULL, account_type=NULL" "$(raw_row "${new:-0}")"
  dump_ui "$D/c-after-9s.xml"; screencap "$D/c-after-9s.png"
  record "create: the People page on show after Save" "$(ids_with_prefix "$D/c-after-9s.xml" people_page: | tr '\n' ' ')$(ids_with_prefix "$D/c-after-9s.xml" people_card: | head -1)"
  assert_eq "create: People is the activity on show (the page read below is People's)" "$PEOPLE_ACTIVITY" "$(top_activity)"
  assert_ne "create: Save closed the editor (people_page:editor is not the page on show)" "true" "$(node_attr "$D/c-after-9s.xml" people_page:editor selected)"
  assert_eq "create: … and the page carries no people_notice (the save did not fail aloud)" "no" "$(has_node "$D/c-after-9s.xml" people_notice)"

  log "--- create: restore"
  back 1; back 1
  people_fixtures_down
  q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/c-raw-after.txt"
  assert_eq "create restore: the raw_contacts rows equal the rows before the leg" "$(cat "$D/c-raw-before.txt")" "$(cat "$D/c-raw-after.txt")"
  assert_eq "create restore: nothing is on \"Can edit\"" "" "$(people_allowed)"
  c6; ensure_start
}

# The settled bubble of the PEOPLE slot tile, read at a rest: a screencap taken the moment an in-slide's motion line is
# logged (the bubble then rests 5.8 s), then the dump. Sets G_BUBBLE ("l t r b"), G_DESC, G_PNG.
tile_bubble_at_rest() { # tag
  local k mark nb
  G_BUBBLE=""; G_DESC=""; G_PNG=""
  for k in 1 2 3; do
    mark="$(ring_mark)"
    for _ in $(seq 1 20); do ring_since "$mark" | grep -q 'people_bubble_in' && break; sleep 0.5; done
    ring_since "$mark" | grep -q 'people_bubble_in' || note "$1 try $k: no in-slide was logged in 10 s (a bubble that is not sliding rests)"
    screencap "$D/$1-$k.png"
    gdump "$D/$1-$k.xml" || true
    nb="$(node_inside "$D/$1-$k.xml" "$PEOPLE_SLOT_TILE" people_tile_bubble)"
    if [ -n "$nb" ]; then G_BUBBLE="${nb%%|*}"; G_DESC="${nb#*|}"; G_PNG="$D/$1-$k.png"; return 0; fi
    note "$1 try $k: the dump holds no people_tile_bubble in the tile (taken mid-slide, or no bubble)"
  done
  return 1
}
# The shell's entries in Android's crash drop box (one per crash of its process; D-E13-1's two are there).
crash_count() { adb shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | grep -c '^Process: app.tileshell'; }
grant_restore() {
  log "--- grant: restore"
  ring_save
  perm_ensure READ_CONTACTS WRITE_CONTACTS
  assert_eq "grant restore: READ_CONTACTS and WRITE_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
  people_fixtures_down
  remove_photos
  assert_eq "grant restore: the pushed JPEG is removed" "0" "$(pushed_photos_left)"
  q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/g-raw-after.txt"
  assert_eq "grant restore: the raw_contacts rows equal the rows before the leg" "$(cat "$D/g-raw-before.txt")" "$(cat "$D/g-raw-after.txt")"
  layout_restore "$BASELINE"; assert_eq "grant restore: layout_restore of the baseline" "0" "$?"
  c6; ensure_start
  assert_eq "grant restore: the Home intent still resolves to the shell (a home app that crashes twice loses its place; D-E13-1)" "app.tileshell/.StartActivity" "$(adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tr -d '\r' | tail -1)"
}
leg_grant() {
  local CR0 l_ann pid0 pid1 r_mark r_slice g_mark g_slice p_mark p_slice x_mark x_slice p revoke="${E13_GRANT_REVOKE:-both}" want_write=false
  # E13_GRANT_REVOKE=both (the default): READ_CONTACTS and WRITE_CONTACTS revoked — a phone on which Contacts was never
  # allowed. On e03a1d23 People crashes when opened in that state (defects/D-E13-1.md; the kept run).
  # E13_GRANT_REVOKE=read: READ_CONTACTS alone revoked, WRITE_CONTACTS held (the lead's wording of the leg; EDGE P06's state).
  [ "$revoke" = read ] && want_write=true
  record "grant: which Contacts permissions the leg takes away (E13_GRANT_REVOKE)" "$revoke"
  log "=== leg grant: Contacts allowed in place, from People's notice, starts the People tile's observer (gate review A, finding 10)"
  layout_restore "$BASELINE"; assert_eq "grant: layout_restore of the baseline (the PEOPLE slot tile is on Start)" "0" "$?"
  ensure_start
  perm_ensure READ_CONTACTS WRITE_CONTACTS
  q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/g-raw-before.txt"
  people_fixtures_up
  l_ann="$(lookup_of "$(contact_of "$ANN")")"; note "grant: Ann Lee is raw=$ANN lookup=$l_ann"
  push_photo red >/dev/null
  assert_eq "grant: the solid-red JPEG is pushed" "1" "$(pushed_photos_left)"
  assert_eq "grant: no contact holds a photo data row before the leg" "0" "$(q "content query --uri $DATA --projection raw_contact_id --where \"mimetype='vnd.android.cursor.item/photo'\"" | grep -c 'raw_contact_id=')"

  log "--- grant: Contacts taken away (pm revoke ends the shell's process; Android starts the home app again)"
  ring_save
  r_mark="$(ring_mark)"
  adb shell pm revoke app.tileshell android.permission.READ_CONTACTS
  [ "$revoke" = read ] || adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS
  sleep 2
  # A "Don't allow" some earlier row tapped must not make Android answer for the user: the two flags that would are cleared.
  for p in READ_CONTACTS WRITE_CONTACTS; do adb shell pm clear-permission-flags app.tileshell "android.permission.$p" user-set user-fixed >/dev/null 2>&1; done
  note "grant: dumpsys package's lines for the two permissions: $(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -E 'android.permission.(READ|WRITE)_CONTACTS: granted' | tr -s ' ' | tr '\n' ';')"
  assert_eq "grant: READ_CONTACTS is revoked, and WRITE_CONTACTS is held = $want_write (E13_GRANT_REVOKE=$revoke)" "false $want_write" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
  ensure_start; sleep 3
  pid0="$(shell_pid)"; note "grant: the shell's pid after the revoke: $pid0"
  # The crash MARK: the count of the shell's entries in the crash drop box before People is opened.
  assert_contains "grant: Android's crash drop box can be read (dumpsys dropbox)" "Drop box contents" "$(adb shell dumpsys dropbox data_app_crash 2>/dev/null | tr -d '\r' | head -3)"
  CR0="$(crash_count)"; note "grant: the shell's entries in the crash drop box before People is opened: $CR0"
  assert_ne "grant: the shell is running" "" "$pid0"
  r_slice="$(ring_since "$r_mark")"; printf '%s\n' "$r_slice" > "$D/g-slice-after-revoke.txt"
  assert_contains "grant: the process started without the read — [app] feeds started (process start)" "[app] feeds started (process start)" "$r_slice"
  assert_contains "grant: … and its People feed read nothing — [people] tile: 0 photos (no contacts access, start)" "[people] tile: 0 photos (no contacts access, start)" "$r_slice"
  gdump "$D/g-start-revoked.xml" || true; screencap "$D/g-start-revoked.png"
  assert_ne "grant: the PEOPLE slot tile is on Start" "" "$(bounds "$D/g-start-revoked.xml" "$PEOPLE_SLOT_TILE")"
  assert_ne "grant: … showing the static pattern (people_tile_pattern inside the tile)" "" "$(node_inside "$D/g-start-revoked.xml" "$PEOPLE_SLOT_TILE" people_tile_pattern)"

  log "--- grant: People's notice, the grant made in place"
  open_people -a android.intent.action.MAIN
  wait_node "$D/g-notice.xml" people_notice 8 || true
  screencap "$D/g-notice.png"
  log "people_notice: [$(xml_text "$D/g-notice.xml" people_notice)]  people_notice_action: [$(xml_text "$D/g-notice.xml" people_notice_action)]"
  assert_contains "grant: People says it cannot read the contacts (people_notice)" "can't read your contacts" "$(xml_text "$D/g-notice.xml" people_notice)"
  assert_eq "grant: … and offers the grant in place (people_notice_action)" "yes" "$(has_node "$D/g-notice.xml" people_notice_action)"
  assert_eq "grant: no contact is listed while the read is not held" "0" "$(count_ids "$D/g-notice.xml" people_row:)"
  pid1="$(shell_pid)"
  assert_eq "grant: the shell's process is still the one that started after the revoke" "$pid0" "$pid1"
  assert_eq "grant: D-E13-1 — opening People with neither Contacts permission added no entry to the crash drop box" "$CR0" "$(crash_count)"
  assert_eq "grant: … and People is the activity on show" "$PEOPLE_ACTIVITY" "$(top_activity)"
  record "grant: [people] observer not registered lines since the revoke (the line is written only when the provider refuses a registration People tried)" "$(ring_since "$r_mark" | grep -cF '[people] observer not registered')"
  # People did not stay on show, or the process is another one: the leg STOPS (run 1 on e03a1d23 went on, opened People
  # a second time, and the second crash cost the shell its place as the home app — D-E13-1).
  if [ "$(top_activity)" != "$PEOPLE_ACTIVITY" ] || [ "$pid1" != "$pid0" ]; then
    _verdict FAIL "grant: People is on show, under the process that opened it (the leg stops here: nothing after this can be read)" "top activity $(top_activity); pid $pid0 → $pid1"
    adb logcat -b crash -d -t 300 2>/dev/null | tr -d '\r' > "$D/g-logcat-crash.txt"
    grep -E 'FATAL EXCEPTION|Process: app.tileshell|Exception' "$D/g-logcat-crash.txt" | tail -4 | cut -c1-300 >> "$LOG"
    grant_restore
    return 1
  fi
  g_mark="$(ring_mark)"
  tap_node "$D/g-notice.xml" people_notice_action; sleep 3
  dump_ui "$D/g-dialog.xml"; screencap "$D/g-dialog.png"
  if grep -q 'package="com[^"]*permissioncontroller"' "$D/g-dialog.xml"; then
    record "grant: the in-place offer raised Android's permission dialog" "yes ($(top_activity))"
    assert_eq "grant: the dialog has its Allow button" "yes" "$(has_node "$D/g-dialog.xml" com.android.permissioncontroller:id/permission_allow_button)"
    tap_node "$D/g-dialog.xml" com.android.permissioncontroller:id/permission_allow_button; sleep 3
  else
    record "grant: the in-place offer raised Android's permission dialog" "no dialog in the dump; top activity $(top_activity)"
  fi
  assert_eq "grant: READ_CONTACTS and WRITE_CONTACTS are held after the in-place grant" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
  sleep 2
  dump_ui "$D/g-after-grant.xml"; screencap "$D/g-after-grant.png"
  g_slice="$(ring_since "$g_mark")"; printf '%s\n' "$g_slice" > "$D/g-slice-grant.txt"
  printf '%s\n' "$g_slice" | grep -E '\[people\] permission request|\[app\] feeds started|\[people\] tile:' | sed 's/^.*wall=[0-9]* //' >> "$LOG"
  assert_contains "grant: the slice holds People's own line for the answer: READ_CONTACTS=true" "READ_CONTACTS=true" "$(printf '%s\n' "$g_slice" | grep -F '[people] permission request:')"
  assert_contains "grant: A-10 — the slice holds [app] feeds started (people grant)" "[app] feeds started (people grant)" "$g_slice"
  assert_eq "grant: … once" "1" "$(printf '%s\n' "$g_slice" | grep -cF '[app] feeds started (people grant)')"
  absent_in "grant: no \`[app] feeds started (process start)\` in that slice — the process did not start again" "feeds started (process start)" "$g_slice"
  assert_eq "grant: the shell's pid is unchanged by the grant" "$pid1" "$(shell_pid)"
  assert_eq "grant: People is the activity on show (the page read below is People's)" "$PEOPLE_ACTIVITY" "$(top_activity)"
  assert_ne "grant: People now lists the contacts (people_row: nodes)" "0" "$(count_ids "$D/g-after-grant.xml" people_row:)"
  assert_eq "grant: … and the notice is gone" "no" "$(has_node "$D/g-after-grant.xml" people_notice)"

  log "--- grant: a photo given to Ann afterwards reaches the People tile, no process restart"
  p_mark="$(ring_mark)"
  give_photo "$ANN" red "grant: Ann (red), given after the grant"
  for _ in $(seq 1 24); do ring_since "$p_mark" | grep -qF '[people] tile: 1 photos' && break; sleep 0.5; done
  p_slice="$(ring_since "$p_mark")"; printf '%s\n' "$p_slice" > "$D/g-slice-photo.txt"
  printf '%s\n' "$p_slice" | grep -E '\[app\] feeds started|\[people\] tile:|\[people\] write' | sed 's/^.*wall=[0-9]* //' >> "$LOG"
  assert_contains "grant: the feed read the new photo — [people] tile: 1 photos, in a slice from a MARK taken after the grant" "[people] tile: 1 photos" "$p_slice"
  # PeopleFeed reads at a start and on its provider observer, nowhere else (feeds/PeopleFeed.kt): with no start line in
  # the slice, the read above came from the observer the grant registered.
  absent_in "grant: … and no \`[app] feeds started\` line of any kind is in that slice: the read came from the observer" "[app] feeds started" "$p_slice"
  ensure_start
  tile_bubble_at_rest g-tile || true
  note "grant: the tile's settled bubble: bounds [$G_BUBBLE] content-desc [$G_DESC] ($G_PNG)"
  assert_ne "grant: the PEOPLE slot tile shows a bubble (people_tile_bubble inside the tile)" "" "$G_BUBBLE"
  assert_eq "grant: … whose content-desc is Ann's lookup key" "$l_ann" "$G_DESC"
  assert_color "grant: … and whose centre pixel is the red JPEG's colour ± 8 (the screencap taken at the bubble's rest)" "${PHOTO_RGB[red]}" "$(centre_px "${G_PNG:-$D/g-tile-1.png}" "${G_BUBBLE:-0 0 2 2}")" 8
  assert_eq "grant: the shell's pid is still the one from before the grant (no process restart between the grant and the photo on the tile)" "$pid1" "$(shell_pid)"

  log "--- grant: a photo removed from OUTSIDE the shell (content delete over adb) leaves the tile: only the observer can carry it"
  x_mark="$(ring_mark)"
  q "content delete --uri $DATA --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id=$ANN\"" >/dev/null
  assert_eq "grant: Ann holds no photo data row any more" "0" "$(photo_rows "$ANN")"
  for _ in $(seq 1 24); do ring_since "$x_mark" | grep -qE '\[people\] tile: 0 photos[[:space:]]*$' && break; sleep 0.5; done
  x_slice="$(ring_since "$x_mark")"; printf '%s\n' "$x_slice" > "$D/g-slice-photo-removed.txt"
  printf '%s\n' "$x_slice" | grep -E '\[app\] feeds started|\[people\] tile:' | sed 's/^.*wall=[0-9]* //' >> "$LOG"
  assert_eq "grant: the feed read the removal — a [people] tile: 0 photos line (with access; not the no-access form)" "yes" "$(printf '%s\n' "$x_slice" | grep -qE '\[people\] tile: 0 photos[[:space:]]*$' && echo yes || echo no)"
  absent_in "grant: … with no \`[app] feeds started\` line in that slice" "[app] feeds started" "$x_slice"
  sleep 3      # the bubble on show finishes its turn
  gdump "$D/g-tile-pattern.xml" || true; screencap "$D/g-tile-pattern.png"
  assert_ne "grant: the PEOPLE slot tile is on Start" "" "$(bounds "$D/g-tile-pattern.xml" "$PEOPLE_SLOT_TILE")"
  assert_ne "grant: … back to the static pattern (people_tile_pattern inside the tile)" "" "$(node_inside "$D/g-tile-pattern.xml" "$PEOPLE_SLOT_TILE" people_tile_pattern)"
  assert_eq "grant: … and no bubble" "" "$(node_inside "$D/g-tile-pattern.xml" "$PEOPLE_SLOT_TILE" people_tile_bubble)"
  assert_eq "grant: the shell's pid is still the same" "$pid1" "$(shell_pid)"
  assert_eq "grant: no new entry of the shell's in the crash drop box since the MARK taken before People was opened" "$CR0" "$(crash_count)"

  grant_restore
}
if [ -n "${E13_LEGS:-}" ]; then
  record "E13_LEGS — a NARROW run, only these legs of E13 ran (the counted whole-row run is E13/ on 3c1ad1e0)" "$E13_LEGS"
  for LEG in $(printf '%s' "$E13_LEGS" | tr ',' ' '); do
    case "$LEG" in
      create) leg_create ;;
      grant) leg_grant ;;
      *) _verdict FAIL "E13_LEGS names a leg this driver has" "unknown: $LEG (known: create, grant)" ;;
    esac
  done
  row_end; exit $?
fi

# ================================================================================================ the whole row
ensure_start
perm_ensure READ_CONTACTS WRITE_CONTACTS
assert_eq "precondition: READ_CONTACTS and WRITE_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
assert_eq "precondition: nothing is on \"Can edit\" (people_edit.json allows no account)" "" "$(people_allowed)"
note "people_edit.json: $(people_edit_json)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"

log "--- fixtures"
people_fixtures_up
MAX_BEFORE="$(q "content query --uri $RAW --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tail -1)"
L_ANN="$(lookup_of "$(contact_of "$ANN")")"
assert_contains "Ann Lee is phone-only (Q-16-3: editable with nothing allowed)" "account_name=NULL, account_type=NULL" "$(raw_row "$ANN")"
RED="$(push_photo red)"; note "pushed $RED"
assert_eq "the solid-red fixture is on the device" "1" "$(pushed_photos_left)"

# ------------------------------------------------------------------------------------------------ New
log "--- New: the account choice, then Dan Ford with a mobile number"
open_people -a android.intent.action.MAIN
wait_node "$D/list.xml" people_bar:add 8 || true
tap_node "$D/list.xml" people_bar:add; sleep 2
dump_ui "$D/new.xml"; screencap "$D/new.png"
assert_contains "New opens the editor (people_page:editor selected)" 'selected="true"' "$(node_tag "$D/new.xml" people_page:editor)"
assert_eq "people_editor_account reads \"Phone\"" "Phone" "$(xml_text "$D/new.xml" people_editor_account)"
tap_node "$D/new.xml" people_editor_account; sleep 2
dump_ui "$D/new-accounts.xml"; screencap "$D/new-accounts.png"
note "account rows offered: $(ids_with_prefix "$D/new-accounts.xml" people_editor_account_row: | tr '\n' ' ')"
assert_eq "its menu offers the phone's row" "yes" "$(has_node "$D/new-accounts.xml" people_editor_account_row:phone)"
assert_eq "… and no other row while nothing is allowed" "1" "$(count_ids "$D/new-accounts.xml" people_editor_account_row:)"
back 1
dump_ui "$D/new-back.xml"
if [ "$(has_node "$D/new-back.xml" people_page:editor)" != yes ]; then   # Back left the editor instead of the menu
  note "Back closed the editor, not only the account menu: reopening New"
  open_people -a android.intent.action.MAIN; tapid people_bar:add 2
fi
set_field people_field:name "Dan Ford"
set_field people_field:phone "+1 555 000 0042"
dump_ui "$D/new-filled.xml"; screencap "$D/new-filled.png"
assert_eq "the phone field's type reads Mobile (a mobile number)" "Mobile phone" "$(xml_text "$D/new-filled.xml" people_field_type:phone)"
assert_eq "nothing is in the provider before Save is tapped" "0" "$(q "content query --uri $RAW --projection _id --where \"display_name='Dan Ford'\"" | grep -c '_id=')"
MARK="$(ring_mark)"
tap_node "$D/new-filled.xml" people_editor_save; sleep 3
# The doc's query, whole.
PHONES="$(q "content query --uri $DATA --projection raw_contact_id:mimetype:data1 --where \"mimetype='vnd.android.cursor.item/phone_v2'\"")"
printf '%s\n' "$PHONES" > "$D/phones-after-new.txt"; printf '%s\n' "$PHONES" >> "$LOG"
DAN="$(printf '%s\n' "$PHONES" | grep -F 'data1=+1 555 000 0042' | sed -n 's/.*raw_contact_id=\([0-9]*\),.*/\1/p' | head -1)"
assert_ne "the data query shows the number under a raw contact" "" "$DAN"
[ -n "$DAN" ] && echo "$DAN" >> "$ROW_DIR/people-fixtures.ids"
assert_eq "… exactly one phone row holds it" "1" "$(printf '%s\n' "$PHONES" | grep -cF 'data1=+1 555 000 0042')"
assert_eq "… a NEW raw contact (its _id is above every _id before New)" "yes" "$([ -n "$DAN" ] && [ "$DAN" -gt "${MAX_BEFORE:-0}" ] && echo yes || echo no)"
DAN_ROW="$(raw_row "${DAN:-0}")"; log "$DAN_ROW"
assert_contains "… named Dan Ford" "display_name=Dan Ford" "$DAN_ROW"
assert_contains "… whose account_name and account_type are NULL (the phone, Q-16-3)" "account_name=NULL, account_type=NULL" "$DAN_ROW"
assert_contains "… the number stored as a mobile (data2 = 2)" "data2=2" "$(q "content query --uri $DATA --projection data1:data2 --where \"raw_contact_id=${DAN:-0} AND mimetype='vnd.android.cursor.item/phone_v2'\"")"
assert_contains "E21: [people] write insert raw=<id>: ok" "[people] write insert raw=$DAN: ok" "$(ring_since "$MARK")"
L_DAN="$(lookup_of "$(contact_of "${DAN:-0}")")"

# ------------------------------------------------------------------------------------------------ edit Ann's number
log "--- edit Ann's number"
PH_BEFORE="$(q "content query --uri $DATA --projection _id:mimetype:data1 --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/phone_v2'\"")"; log "before: $PH_BEFORE"
card_of "$ANN" "$D/ann-card.xml"
assert_eq "Ann's card (people_card:<lookup>)" "yes" "$(has_node "$D/ann-card.xml" "people_card:$L_ANN")"
tap_node "$D/ann-card.xml" people_card_edit; sleep 2
dump_ui "$D/ann-edit.xml"
assert_eq "the editor shows her stored number" "+1 555 000 0001" "$(xml_text "$D/ann-edit.xml" people_field:phone)"
set_field people_field:phone "+1 555 000 0077"
MARK="$(ring_mark)"
dump_ui "$D/ann-edit2.xml"; tap_node "$D/ann-edit2.xml" people_editor_save; sleep 3
PH_AFTER="$(q "content query --uri $DATA --projection _id:mimetype:data1 --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/phone_v2'\"")"; log "after:  $PH_AFTER"
assert_contains "her phone data row holds the new number" "data1=+1 555 000 0077" "$PH_AFTER"
assert_absent "… and the old number is gone" "data1=+1 555 000 0001" "$PH_AFTER"
assert_eq "… still one phone row for her" "1" "$(printf '%s\n' "$PH_AFTER" | grep -c 'phone_v2')"
record "the phone data row's _id before → after the edit" "$(printf '%s' "$PH_BEFORE" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p') → $(printf '%s' "$PH_AFTER" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')"
assert_contains "her e-mail row is untouched" "data1=ann@example.com" "$(data_rows "$ANN")"
assert_contains "E21: [people] write update raw=<id>: ok" "[people] write update raw=$ANN: ok" "$(ring_since "$MARK")"

# ------------------------------------------------------------------------------------------------ photo
log "--- Photo: Android's photo picker, a pushed JPEG, the photo data row, the card"
assert_eq "Ann has no photo data row before" "0" "$(photo_rows "$ANN")"
card_of "$ANN" "$D/ann-card2.xml"; tap_node "$D/ann-card2.xml" people_card_edit; sleep 2
dump_ui "$D/ann-edit3.xml"; tap_node "$D/ann-edit3.xml" people_editor_photo; sleep 3
adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity' > "$D/picker-activities.txt"
PICKER="$(top_activity)"; log "after the Photo tap, dumpsys activity activities shows: $PICKER"
assert_contains "Android's photo picker is the resumed activity" "photopicker" "$(echo "$PICKER" | tr 'A-Z' 'a-z')"
assert_absent "… not an activity of the shell's" "app.tileshell/" "$PICKER"
pick_photo "${PHOTO_RGB[red]}"
assert_eq "after the choice People's editor is back" "$PEOPLE_ACTIVITY" "$(top_activity)"
MARK="$(ring_mark)"
dump_ui "$D/ann-edit4.xml"; screencap "$D/ann-edit4.png"
tap_node "$D/ann-edit4.xml" people_editor_save; sleep 4
PHOTO="$(q "content query --uri $DATA --projection _id:raw_contact_id:mimetype --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/photo'\"")"; log "$PHOTO"
assert_eq "a vnd.android.cursor.item/photo data row exists for Ann" "1" "$(photo_rows "$ANN")"
sleep 1
dump_ui "$D/ann-card3.xml"; screencap "$D/ann-card3.png"
if [ "$(has_node "$D/ann-card3.xml" "people_card:$L_ANN")" != yes ]; then card_of "$ANN" "$D/ann-card3.xml"; screencap "$D/ann-card3.png"; fi
assert_eq "her card is on show (people_card:<lookup>)" "yes" "$(has_node "$D/ann-card3.xml" "people_card:$L_ANN")"
CB="$(bounds "$D/ann-card3.xml" people_card_photo)"
assert_ne "the card has people_card_photo" "" "$CB"
assert_color "people_card_photo's centre pixel equals the fixture's colour ± 8" "${PHOTO_RGB[red]}" "$(centre_px "$D/ann-card3.png" "${CB:-0 0 2 2}")" 8
# shown again from a cold start of the shell (the photo is read from the provider, not kept from the picker)
c6; ensure_start
card_of "$ANN" "$D/ann-card4.xml"; screencap "$D/ann-card4.png"
CB="$(bounds "$D/ann-card4.xml" people_card_photo)"
assert_color "… and again after the shell's process restarted" "${PHOTO_RGB[red]}" "$(centre_px "$D/ann-card4.png" "${CB:-0 0 2 2}")" 8

# ------------------------------------------------------------------------------------------------ delete Dan
log "--- delete Dan"
assert_eq "Dan's contact row exists before the delete" "1" "$(q "content query --uri $CONTACTS --projection _id --where \"display_name='Dan Ford'\"" | grep -c '_id=')"
card_of "${DAN:-0}" "$D/dan-card.xml"
assert_eq "Dan's card (asserted first)" "yes" "$(has_node "$D/dan-card.xml" "people_card:$L_DAN")"
tap_node "$D/dan-card.xml" people_card_delete; sleep 2
dump_ui "$D/dan-confirm.xml"; screencap "$D/dan-confirm.png"
MARK="$(ring_mark)"
if [ "$(has_node "$D/dan-confirm.xml" people_delete_dialog)" = yes ]; then
  record "Delete asks once before it deletes (built so; Change Log 2026-10-01, P2)" "people_delete_dialog shown; confirm tapped"
  tap_node "$D/dan-confirm.xml" people_delete_confirm; sleep 3
else
  record "Delete asks once before it deletes" "no dialog in the dump"
fi
assert_eq "Dan's contact row is gone" "0" "$(q "content query --uri $CONTACTS --projection _id --where \"display_name='Dan Ford'\"" | grep -c '_id=')"
assert_eq "… and no live raw contact of his is left" "0" "$(q "content query --uri $RAW --projection _id:deleted --where \"_id=${DAN:-0} AND deleted=0\"" | grep -c '_id=')"
assert_contains "E21: [people] write delete raw=<id>: ok" "[people] write delete raw=$DAN: ok" "$(ring_since "$MARK")"
assert_eq "Ann is still there (the delete took Dan only)" "1" "$(q "content query --uri $RAW --projection _id --where \"_id=$ANN AND deleted=0\"" | grep -c '_id=')"

# ------------------------------------------------------------------------------------------------ WRITE_CONTACTS revoked
log "--- pm revoke WRITE_CONTACTS: the cannot-save notice, the two checklists, pm grant"
ring_save
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS; sleep 2     # ends the shell's process
assert_eq "WRITE_CONTACTS is revoked" "false" "$(perm_granted WRITE_CONTACTS)"
assert_eq "READ_CONTACTS is still held" "true" "$(perm_granted READ_CONTACTS)"
ensure_start
ANN_DATA_BEFORE="$(data_rows "$ANN" | grep -v 'cursor.item/photo')"
card_of "$ANN" "$D/r-card.xml"; tap_node "$D/r-card.xml" people_card_edit; sleep 2
set_field people_field:name "Ann Leigh"
MARK="$(ring_mark)"
dump_ui "$D/r-edit.xml"; tap_node "$D/r-edit.xml" people_editor_save; sleep 3
dump_ui "$D/r-notice.xml"; screencap "$D/r-notice.png"
NOTICE="$(xml_text "$D/r-notice.xml" people_notice)"; log "people_notice: [$NOTICE]  people_notice_action: [$(xml_text "$D/r-notice.xml" people_notice_action)]"
assert_eq "people_notice is on the editor" "yes" "$(has_node "$D/r-notice.xml" people_notice)"
assert_contains "… and says it cannot save" "can't save" "$NOTICE"
assert_eq "… with the grant offered in place (people_notice_action)" "yes" "$(has_node "$D/r-notice.xml" people_notice_action)"
assert_contains "the editor is still the page on show, the edit kept" 'selected="true"' "$(node_tag "$D/r-notice.xml" people_page:editor)"
assert_eq "nothing was written: Ann's data rows equal their read before Save" "$ANN_DATA_BEFORE" "$(data_rows "$ANN" | grep -v 'cursor.item/photo')"
assert_contains "E21: [people] write update raw=<id>: failed <err>" "[people] write update raw=$ANN: failed " "$(ring_since "$MARK")"
log "$(ring_since "$MARK" | grep -F '[people] write' | sed 's/.*\[people\]/[people]/')"
# The notice is a node that stays until the page changes, never a toast (Harness, round 3): still there 6 s later.
sleep 6; dump_ui "$D/r-notice-6s.xml"
assert_eq "the notice is still on the page 6 s later (a node, not a toast)" "yes" "$(has_node "$D/r-notice-6s.xml" people_notice)"
back 1; back 1
SETUP="$(setup_people_row "$D/r-setup.xml")"; screencap "$D/r-setup.png"
assert_eq "the Setup checklist shows checklist:people:partial (READ held, WRITE not)" "checklist:people:partial" "$SETUP"
adb shell input keyevent KEYCODE_HOME; sleep 2
TESS="$(tess_contacts_row "$D/r-tess.xml")"; screencap "$D/r-tess.png"
assert_eq "Tess's contacts row still reads granted (cortana_check:contacts:granted; T16-15)" "cortana_check:contacts:granted" "$TESS"
cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 2
ring_save
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS; sleep 1
assert_eq "pm grant: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
SETUP="$(setup_people_row "$D/g-setup.xml")"; screencap "$D/g-setup.png"
assert_eq "pm grant restores checklist:people:granted" "checklist:people:granted" "$SETUP"
adb shell input keyevent KEYCODE_HOME; sleep 2

# The offer itself, taken once: the in-place grant is tapped and what Android did is recorded (a dialog, or a silent
# grant because READ_CONTACTS of the same group is held); the editor then saves.
log "--- the in-place offer tapped (phase 10 E18's form)"
ring_save
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS; sleep 2
ensure_start
card_of "$ANN" "$D/o-card.xml"; tap_node "$D/o-card.xml" people_card_edit; sleep 2
set_field people_field:name "Ann Leigh"
dump_ui "$D/o-edit.xml"; tap_node "$D/o-edit.xml" people_editor_save; sleep 3
dump_ui "$D/o-notice.xml"
tap_node "$D/o-notice.xml" people_notice_action; sleep 3
dump_ui "$D/o-dialog.xml"; screencap "$D/o-dialog.png"
if grep -q 'package="com[^"]*permissioncontroller"' "$D/o-dialog.xml"; then
  record "the in-place offer raised Android's permission dialog" "yes ($(top_activity))"
  ALLOW="$(bounds "$D/o-dialog.xml" com.android.permissioncontroller:id/permission_allow_button)"
  [ -n "$ALLOW" ] && { tap_node "$D/o-dialog.xml" com.android.permissioncontroller:id/permission_allow_button; sleep 3; }
else
  record "the in-place offer raised Android's permission dialog" "no dialog: Android granted it at once (READ_CONTACTS of the same group is held); top activity $(top_activity)"
fi
assert_eq "the in-place offer leads to WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
dump_ui "$D/o-after.xml"
MARK="$(ring_mark)"
if [ "$(has_node "$D/o-after.xml" people_editor_save)" = yes ]; then tap_node "$D/o-after.xml" people_editor_save; sleep 3; fi
assert_contains "… and Save then writes her name" "data1=Ann Leigh" "$(data_rows "$ANN")"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back 1; back 1
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "restore: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
assert_eq "restore: READ_CONTACTS held" "true" "$(perm_granted READ_CONTACTS)"
people_fixtures_down
remove_photos
assert_eq "restore: the pushed JPEG is removed" "0" "$(pushed_photos_left)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
assert_eq "restore: nothing is on \"Can edit\"" "" "$(people_allowed)"
c6; ensure_start
row_end
