#!/usr/bin/env bash
# E4 — Alarm fires over the keyguard (phase 15, clock jump; Q-E A, T15-14). An alarm at 09:05 with the default
# 10-minute snooze (seeded through the AlarmClock API — the editor is E3's subject); a PIN set, the phone put to
# sleep and woken (the real lock screen, locked_ref.png), then asleep again; the clock jumped to 10 s before the
# alarm → within 30 s the ring activity is on top of the SHOWING, OCCLUDED keyguard with the display on, the
# alarm-channel notification carries a full-screen intent, category alarm and Snooze / Dismiss, an ALARM player
# of the shell is started, a vibration of the shell is running, and the ring slice holds `fired … late ≤ 1000`,
# `notification <id>: fullscreen` and `surface: toast-locked <id>`; the toast (gesture-driver dump) spans the full
# width from y 0 to 248 epx ± 3 % and holds its tags, no window of any other package is up, and below the toast
# the pixels equal the build-start check's outcome (the wallpaper beneath an occluded keyguard: locked_ref.png with
# the lock screen's own clock and notification stack masked out) ± 8 levels in ≥ 90 % of the region. Then Snooze →
# +10 min and "Snoozed until"; a ring-time "Snooze for" 20 → +20 min; Dismiss → nothing armed, missed count 0; a
# daily alarm dismissed re-arms for the next day. Restore: RV12's clock restore, the PIN cleared, both alarms
# deleted through the app, force-stop + Home, wake_device.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E4 "alarm fires over the keyguard: locked toast, snooze, snooze-for, dismiss, daily re-arm"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
TZ0="$(adb shell getprop persist.sys.timezone | tr -d '\r')"

# Every ring in this row: the device facts the doc lists, from a MARK taken right after the jump.
ring_checks() { # label id mark
  local label="$1" id="$2" mark="$3" fired late notif
  fired="$(wait_ring "$mark" "[alarms] fired $id kind=alarm" 30)"
  assert_ne "$label: the alarm fired within 30 s of the jump" "" "$fired"
  late="$(field_of "$fired" late)"
  assert_within "$label: late <= 1000 ms" 0 "${late:-99999}" 1000
  sleep 3
  assert_eq "$label: the keyguard is showing" "true" "$(keyguard_showing)"
  assert_eq "$label: … and occluded by the ring activity" "true" "$(keyguard_occluded)"
  assert_contains "$label: the ring activity has the focus" "clock.RingActivity" "$(current_focus)"
  assert_eq "$label: the display is on (Awake)" "Awake" "$(wakefulness)"
  assert_ne "$label: a player of the shell on usage ALARM is started" 0 "$(alarm_player_started)"
  adb shell dumpsys vibrator_manager | tr -d '\r' > "$ROW_DIR/vibrator_$label.txt"
  assert_contains "$label: vibrator_manager lists a vibration request from the shell (uid $SHELL_UID)" "uid=$SHELL_UID" "$(grep -m1 -E "uid=$SHELL_UID" "$ROW_DIR/vibrator_$label.txt")"
  local slice; slice="$(ring_since "$mark")"
  assert_contains "$label: notification $id: fullscreen" "[alarms] notification $id: fullscreen" "$slice"
  assert_contains "$label: surface: toast-locked $id" "[alarms] surface: toast-locked $id" "$slice"
}

# ---- the alarm: 09:05, snooze 10 minutes (the default the API gives) ------------------------------------------
ID="$(api_alarm 9 5 "Wake")"
assert_ne "the alarm was created" "" "$ID"
assert_eq "its snooze time is 10 minutes" 10 "$(alarm_field "$ID" snoozeMinutes)"
ADATE="$(alarm_field "$ID" date)"
TARGET="$(python3 -c '
import sys, datetime, zoneinfo
tz = zoneinfo.ZoneInfo(sys.argv[1] or "UTC"); d = datetime.date.fromisoformat(sys.argv[2])
print(int(datetime.datetime(d.year, d.month, d.day, 9, 5, tzinfo=tz).timestamp() * 1000))' "$TZ0" "$ADATE")"
note "alarm $ID armed for $ADATE 09:05 = $TARGET"
assert_eq "dumpsys alarm holds it at 09:05 on its date" "$TARGET" "$(alarm_trigger_ms | paste -sd,)"

# ---- the keyguard: PIN, sleep, wake (the lock screen), reference screencap, sleep again --------------------------
set_pin
assert_eq "with the PIN set, sleep + wake shows the keyguard" "true" "$(lock_and_wake)"
# The lock screen's own hierarchy, dumped just before its reference capture: the clock and notification-stack masks
# below come from these bounds (run 7 on 5558: the gate pass's fixed masks were run 1's clock block, but a missed-call
# group on the lock screen moved the clock to the top and put its card at y 513–821, outside every fixed mask).
dump_ui "$ROW_DIR/locked_ref.xml"
screencap "$ROW_DIR/locked_ref.png"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
assert_ne "the phone is asleep before the jump" "Awake" "$(wakefulness)"

# ---- jump to 09:04:50 → it rings over the keyguard ---------------------------------------------------------------
jump_clock $(( TARGET - 10000 )) >/dev/null
MARK="$(ring_mark)"
ring_checks "ring 1" "$ID" "$MARK"
adb shell dumpsys notification --noredact | tr -d '\r' > "$ROW_DIR/notification_ring1.txt"
NOTIF="$(notification_on_channel clock_ringing)"
printf '%s\n' "$NOTIF" > "$ROW_DIR/notification_ring1_block.txt"
assert_ne "the alarm channel's notification is posted" "" "$NOTIF"
assert_contains "… category alarm" "category=alarm" "$NOTIF"
assert_ne "… with a full-screen intent" "" "$(printf '%s\n' "$NOTIF" | grep -i 'fullScreenIntent=PendingIntent')"
assert_contains "… and a Snooze action" "Snooze" "$NOTIF"
assert_contains "… and a Dismiss action" "Dismiss" "$NOTIF"

gdump "$ROW_DIR/ring_locked.xml"; screencap "$ROW_DIR/ring_locked.png"
B="$(bounds "$ROW_DIR/ring_locked.xml" ring_surface)"; note "ring_surface bounds: $B"
assert_eq "ring_surface spans the full width from the top" "0 0 1080" "$(echo "$B" | cut -d' ' -f1-3)"
assert_within "ring_surface is 248 epx tall (± 3 %)" $(( 248 * PX )) "$(echo "$B" | cut -d' ' -f4)" $(( 248 * PX * 3 / 100 ))
assert_eq "ring_title reads Alarm" "Alarm" "$(node_text "$ROW_DIR/ring_locked.xml" ring_title)"
assert_eq "ring_name is the alarm's name" "Wake" "$(node_text "$ROW_DIR/ring_locked.xml" ring_name)"
assert_eq "ring_time is the alarm's time" "9:05 AM" "$(node_text "$ROW_DIR/ring_locked.xml" ring_time)"
# The build tags the ComboBox's box, not its label (RingToast.kt:92-98): the label is its untagged child TextView.
assert_eq "ring_snooze_for reads 10 minutes (its child label; the box's own text is empty — reported)" "10 minutes" "$(node_child_text "$ROW_DIR/ring_locked.xml" ring_snooze_for)"
record "ring_snooze_for node's own text (harness contract: the text node should carry the tag)" "[$(node_text "$ROW_DIR/ring_locked.xml" ring_snooze_for)]"
assert_eq "ring_snooze is on the toast" yes "$(has_node "$ROW_DIR/ring_locked.xml" ring_snooze)"
assert_eq "ring_dismiss is on the toast" yes "$(has_node "$ROW_DIR/ring_locked.xml" ring_dismiss)"
WINS="$(gwindows "$ROW_DIR/ring_locked.xml")"; note "windows: $WINS"
# The list must be non-empty first, or the "no other package" clause below passes about nothing (run 3 did).
assert_contains "the gesture driver's window list names the shell's ring window" ":app.tileshell:" "$WINS"
# This image's gesture navigation is Launcher3's Quickstep: its taskbar window (SYSTEM:com.android.launcher3:
# [0,2205][1080,2340], the nav-bar strip) is on every screen, locked or not (run 4). It is recorded and left out of
# the "no other package" clause, which is about what is drawn BENEATH the toast, above the nav bar.
record "Launcher3's nav-strip window (gesture navigation) in the list" "$(printf '%s\n' "$WINS" | tr ';' '\n' | grep 'com.android.launcher3' | paste -sd,)"
OTHER="$(printf '%s\n' "$WINS" | tr ';' '\n' | grep -vE ':com\.android\.launcher3:\[0,2[0-9]{3}\]' | awk -F: 'NF > 1 {print $2}' | grep -vE '^(app\.tileshell|com\.android\.systemui)$' | sort -u | paste -sd,)"
assert_eq "no window of any package but the shell and SystemUI is up (Launcher3's nav-strip window excepted, recorded)" "" "$OTHER"
# Below the toast: the wallpaper (the build-start check's outcome), against locked_ref.png with the lock screen's
# own clock and notification stack masked out. The masks are the rows in which the two captures differ by more
# than 10 % of their pixels ABOVE the toast's bottom edge cannot be used (the toast covers them), so the mask is
# the lock screen's clock block (its top 260 epx are under the toast anyway) and its bottom lock glyph / hint.
Y0=$(( 260 * PX )); Y1=$(( 2340 - 48 * PX ))
DIFF_ROWS="$(python3 "$HERE/pixcmp.py" diffrows "$ROW_DIR/ring_locked.png" "$ROW_DIR/locked_ref.png" "$Y0" "$Y1" 8)"
note "rows differing by > 10 % of pixels between the ring capture and locked_ref: ${DIFF_ROWS:-none}"
RAW="$(python3 "$HERE/pixcmp.py" region "$ROW_DIR/ring_locked.png" "$ROW_DIR/locked_ref.png" "$Y0" "$Y1" 8)"
record "fraction of the region 260 epx→nav bar within ±8 of locked_ref, no mask" "$RAW"
# The masks are what the doc names — the lock screen's clock and its notification stack — plus its lock glyph and
# indication line, each read from locked_ref.xml (SystemUI's own nodes: lockscreen_clock_view and any keyguard
# clock / date / smartspace view, every top-level expandableNotificationRow and the shelf, device_entry_icon_view /
# lock_icon_view, keyguard_indication_area), padded 16 px for the cards' shadows and rounded corners. They are
# recorded; a lock screen whose dump yields none of them fails below rather than comparing against nothing.
MASKS="$(python3 - "$ROW_DIR/locked_ref.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
want = re.compile(r"com\.android\.systemui:id/(lockscreen_clock_view\w*|keyguard_clock\w*|keyguard_status_view|keyguard_slice_view|date_smartspace_view|bc_smartspace_view|expandableNotificationRow|notification_shelf|device_entry_icon_view|lock_icon_view|keyguard_indication_area)$")
out = []
for n in re.finditer(r"<node[^>]*>", xml):
    s = n.group(0)
    rid = re.search(r'resource-id="([^"]*)"', s)
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
    if not rid or not b or not want.match(rid.group(1)): continue
    x0, y0, x1, y1 = map(int, b.groups())
    if x1 <= x0 or y1 <= y0: continue
    out.append("%d,%d,%d,%d" % (max(0, x0 - 16), max(0, y0 - 16), min(1080, x1 + 16), min(2340, y1 + 16)))
print(" ".join(dict.fromkeys(out)))
PY
)"
record "the lock screen's clock / notification stack / lock glyph masks (from locked_ref.xml, +16 px)" "${MASKS:-none}"
assert_ne "the lock screen's dump yields the masks" "" "$MASKS"
# shellcheck disable=SC2086
MASKED="$(python3 "$HERE/pixcmp.py" region "$ROW_DIR/ring_locked.png" "$ROW_DIR/locked_ref.png" "$Y0" "$Y1" 8 $MASKS)"
record "fraction with the lock screen's clock, notification stack and lock glyph / hint masked" "$MASKED"
assert_eq "below the toast: the wallpaper (locked_ref with its clock / notification stack / lock glyph masked) matches ± 8 levels in >= 90 %" yes "$(python3 -c 'import sys; print("yes" if float(sys.argv[1]) >= 0.9 else "no (%s)" % sys.argv[1])' "$MASKED")"

# ---- Snooze → 10 min ahead ------------------------------------------------------------------------------------
MARK="$(ring_mark)"
NOW="$(device_ms)"
gtap "$ROW_DIR/ring_locked.xml" ring_snooze; sleep 3
assert_contains "snooze: ring ended $ID: snooze" "[alarms] ring ended $ID: snooze" "$(ring_since "$MARK")"
assert_absent "snooze: the ring activity left the focus" "clock.RingActivity" "$(current_focus)"
gdump "$ROW_DIR/after_snooze.xml"
assert_eq "snooze: the surface closed (no ring_surface)" no "$(has_node "$ROW_DIR/after_snooze.xml" ring_surface)"
assert_eq "snooze: the ALARM player stopped" 0 "$(alarm_player_started)"
SNZ="$(alarm_field "$ID" snoozedUntilMs)"; case "$SNZ" in ''|null) SNZ=0 ;; esac
assert_within "snooze: the store's snoozedUntil is 10 min after the tap" $(( NOW + 600000 )) "$SNZ" 5000
assert_eq "snooze: dumpsys alarm shows the alarm re-armed at the snooze instant" "$SNZ" "$(alarm_trigger_ms | paste -sd,)"
SNOTIF="$(notification_on_channel clock_snoozed)"; printf '%s\n' "$SNOTIF" > "$ROW_DIR/notification_snoozed.txt"
assert_contains "snooze: the notification reads Snoozed until h:mm" "Snoozed until $(time_12h $(device_hm "$SNZ"))" "$SNOTIF"

# ---- jump again → rings; Snooze for 20 → 20 min ahead (G2) ------------------------------------------------------
[ "$SNZ" -gt 0 ] || SNZ="$(device_ms)"   # a failed snooze above: jump nowhere useful rather than to 1970
jump_clock $(( SNZ - 10000 )) >/dev/null
MARK="$(ring_mark)"
ring_checks "ring 2" "$ID" "$MARK"
gdump "$ROW_DIR/ring2.xml"
gtap "$ROW_DIR/ring2.xml" ring_snooze_for; sleep 1.5
gdump "$ROW_DIR/ring2_list.xml"; screencap "$ROW_DIR/ring2_list.png"
assert_eq "Snooze for opens the choices (ring_snooze_choice:20)" yes "$(has_node "$ROW_DIR/ring2_list.xml" 'ring_snooze_choice:20')"
gtap "$ROW_DIR/ring2_list.xml" 'ring_snooze_choice:20'; sleep 1
gdump "$ROW_DIR/ring2_chosen.xml"
assert_eq "ring_snooze_for now reads 20 minutes (its child label)" "20 minutes" "$(node_child_text "$ROW_DIR/ring2_chosen.xml" ring_snooze_for)"
MARK="$(ring_mark)"; NOW="$(device_ms)"
gtap "$ROW_DIR/ring2_chosen.xml" ring_snooze; sleep 3
assert_contains "snooze for 20: ring ended $ID: snooze" "[alarms] ring ended $ID: snooze" "$(ring_since "$MARK")"
SNZ2="$(alarm_field "$ID" snoozedUntilMs)"; case "$SNZ2" in ''|null) SNZ2=0 ;; esac
assert_within "snooze for 20: the next alarm is 20 min ahead" $(( NOW + 1200000 )) "${SNZ2:-0}" 5000
assert_eq "snooze for 20: dumpsys alarm holds that instant" "$SNZ2" "$(alarm_trigger_ms | paste -sd,)"

# ---- jump again → rings; Dismiss → nothing left, missed count 0 ----------------------------------------------
[ "$SNZ2" -gt 0 ] || SNZ2="$(device_ms)"
jump_clock $(( SNZ2 - 10000 )) >/dev/null
MARK="$(ring_mark)"
ring_checks "ring 3" "$ID" "$MARK"
gdump "$ROW_DIR/ring3.xml"
MARK="$(ring_mark)"
gtap "$ROW_DIR/ring3.xml" ring_dismiss; sleep 3
assert_contains "dismiss: ring ended $ID: dismiss" "[alarms] ring ended $ID: dismiss" "$(ring_since "$MARK")"
assert_eq "dismiss: nothing left in dumpsys alarm for the one-shot" "" "$(alarm_trigger_ms | paste -sd,)"
assert_eq "dismiss: the one-shot is switched off in the store" false "$(alarm_field "$ID" enabled)"
assert_eq "dismiss: the ALARM player stopped" 0 "$(alarm_player_started)"
assert_eq "the missed count over the whole row is 0 (no 'ring ended $ID: missed')" 0 "$(ring_since "$ROW_MARK" | grep -c "ring ended $ID: missed")"
assert_eq "… and no Missed alarm notification" "" "$(notification_on_channel clock_missed)"

# ---- a daily alarm dismissed re-arms for the next day ----------------------------------------------------------
NOW="$(device_ms)"
read -r DH DM <<< "$(device_hm $(( NOW + 120000 )))"
ID2="$(api_alarm "$DH" "$DM" "Daily" "1,2,3,4,5,6,7")"
assert_ne "a daily alarm was created" "" "$ID2"
DT="$(alarm_trigger_ms | head -1)"; DT="${DT:-0}"
assert_ne "… and armed" "0" "$DT"
[ "$DT" -gt 0 ] || DT="$(device_ms)"
jump_clock $(( DT - 10000 )) >/dev/null
MARK="$(ring_mark)"
ring_checks "daily" "$ID2" "$MARK"
gdump "$ROW_DIR/ring_daily.xml"
MARK="$(ring_mark)"
gtap "$ROW_DIR/ring_daily.xml" ring_dismiss; sleep 3
assert_contains "daily dismiss: ring ended $ID2: dismiss" "[alarms] ring ended $ID2: dismiss" "$(ring_since "$MARK")"
assert_eq "daily dismiss: re-armed for the next day (+24 h)" "$(( DT + 86400000 ))" "$(alarm_trigger_ms | paste -sd,)"
assert_eq "daily dismiss: still enabled in the store" true "$(alarm_field "$ID2" enabled)"

# ---- restore --------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
clear_pin
assert_eq "restore: wake_device printed Awake (C-25)" "Awake" "$(wake_device)"
app_delete_alarm "$ID"; app_delete_alarm "$ID2"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_clock_empty "restore"
row_end
