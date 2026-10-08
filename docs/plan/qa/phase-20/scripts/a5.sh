#!/usr/bin/env bash
# Phase 20 A5 — Ask Tess (typed).
#
#   1  type_request "play jazz radio"
#   2  favourite QA News One, play it, stop; type_request "play radio"
#   3  type_request "play zzqx radio"
#   4  set a PIN; sleep / wake (qa/phase-17/scripts/e10.sh's K-leg); type_request "play jazz radio"; clear the PIN
#   5  qa/phase-03/scripts/j5.sh
#
# Pass (a)–(e) as the phase doc lists them. Tess is TYPED; nothing is spoken and the host's audio is never touched.
# Over the keyguard Tess first says "I didn't catch that." (build notes), so the reply read there is the LAST one since
# the mark; every reply since the mark is recorded.
# Step 4 pauses the player first, so "it plays" over the keyguard is a start made by THAT request.
# Step 5: j5.sh takes the device lock itself, so this driver lets go of it for the child and takes it back; the child's
# own log is qa/phase-03/J5/J5.txt (copied here) and its output is j5.out.
#
# Changes on the device: QA News One becomes a favourite; a PIN (1234) for step 4, cleared (also from the EXIT trap).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
PIN=1234; pin_set=no
restore_keyguard() {
  if [ "$pin_set" = yes ]; then adb shell locksettings clear --old $PIN > "$ROW_DIR/restore-clear.out" 2>&1 </dev/null && pin_set=no; fi
  adb shell locksettings set-disabled true > "$ROW_DIR/restore-disabled.out" 2>&1 </dev/null
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 </dev/null
  adb shell wm dismiss-keyguard >/dev/null 2>&1 </dev/null
}
trap 'restore_keyguard; fixtures_down' EXIT

# ask <label> <text>: Start, Tess open, the text typed. Sets MARK, REPLY (the last reply), SESSION; saves the slice.
ask() {
  ensure_start
  open_tess "tess-$1"; TESS_OPEN=$?
  MARK="$(ring_mark)"
  type_request "$2" 8; TYPED=$?
  REPLY="$(last_reply "$MARK")"; SESSION="$(sboth)"
  ring_since "$MARK" launcher > "$ROW_DIR/slice-$1.txt"
  session_save "session-$1"; d "after-$1"; shot "$1"
  log "      ASK [$2] -> replies [$(replies_from "$MARK" | tr '\n' '|')] session [$SESSION]"
  adb shell input keyevent KEYCODE_HOME; sleep 2
}

p20_begin A5 "ask Tess (typed): a genre, the favourite, a miss, over the keyguard, J5"
prefs_guard a b c d e
fixtures_up
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
music_fixtures
baseline_start
assert_eq "no keyguard is set before the row (locksettings get-disabled)" "true" "$(adb shell locksettings get-disabled | tr -d '\r')"
# The directory the resolver reads is the one the radio pivot cached; shown once here so the row does not depend on A1's file.
to_radio; assert_eq "the radio pivot lists the fixture's stations (the cached directory Tess resolves against)" "0 yes" "$? $(has radio "radio_group:stations")"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ----------------------------------------------------------------------------------------------- 1: play jazz radio
log "--- 1: type_request \"play jazz radio\""
letter a
ask 01-jazz "play jazz radio"
assert_eq "(a) Tess opened and the text box took the request" "0 0" "$TESS_OPEN $TYPED"
assert_eq "(a) the reply" "Playing QA Jazz One." "$REPLY"
for _ in 1 2 3 4 5; do [ "$(sstate)" = PLAYING ] && break; sleep 1; done
SESSION="$(sboth)"; record "(a) the session" "$SESSION"
assert_eq "(a) that station is playing: state" "PLAYING" "${SESSION%% |*}"
assert_contains "(a) …and it is QA Jazz One" "QA Jazz One" "${SESSION#* | }"
LINE="$(grep -F '[music] search "jazz radio"' "$ROW_DIR/slice-01-jazz.txt" | head -1 | stripped)"
record "(a) the line" "$LINE"
assert_contains "(a) [music] search \"jazz radio\": station QA Jazz One" '[music] search "jazz radio": station QA Jazz One' "$LINE"

# ----------------------------------------------------------------------------------------------- 2: the favourite
log "--- 2: favourite QA News One, play it, stop; type_request \"play radio\""
letter b
to_radio
if [ "$(has radio "radio_fav:$N1")" != yes ]; then
  radio_reach news0 "radio_row:$N1"; hold news0 "radio_row:$N1"; d news-menu
  tap news-menu music_menu_fav_add; sleep 2
fi
to_radio; d favs; shot 02-favourites
record "(b) the favourites, in order" "$(ids favs radio_fav: | tr '\n' ' ')"
assert_yes "(b) QA News One is a favourite (radio_fav:<id>)" "$(has favs "radio_fav:$N1")"
tap favs "radio_fav:$N1"
for _ in $(seq 1 20); do sleep 1; [ "$(sstate)" = PLAYING ] && smeta | grep -q 'QA News One' && break; done
sleep 4
NEWS="$(sboth)"; record "(b) QA News One played from its favourite row" "$NEWS"
assert_eq "(b) QA News One is PLAYING (the favourite played most recently)" "PLAYING yes" "${NEWS%% |*} $(printf '%s' "$NEWS" | grep -q 'QA News One' && echo yes || echo no)"
adb shell input keyevent KEYCODE_MEDIA_STOP; sleep 3
record "(b) after KEYCODE_MEDIA_STOP" "$(sboth)"
assert_ne "(b) it is stopped before the request" "PLAYING" "$(sstate)"
ask 03-play-radio "play radio"
for _ in 1 2 3 4 5 6; do [ "$(sstate)" = PLAYING ] && break; sleep 1; done
SESSION="$(sboth)"; record "(b) the reply / the session" "$REPLY / $SESSION"
assert_eq "(b) \"play radio\" plays: state" "PLAYING" "${SESSION%% |*}"
assert_contains "(b) …QA News One" "QA News One" "${SESSION#* | }"
record "(b) the [music] search line" "$(grep -F '[music] search' "$ROW_DIR/slice-03-play-radio.txt" | head -1 | stripped)"

# ----------------------------------------------------------------------------------------------- 3: the miss
log "--- 3: type_request \"play zzqx radio\""
letter c
sleep 3; BEFORE="$(sboth)"
ask 04-miss "play zzqx radio"
sleep 2; AFTER="$(sboth)"
record "(c) the session before / after" "[$BEFORE] / [$AFTER]"
assert_eq "(c) the miss reply" "I couldn't find zzqx radio in your music." "$REPLY"
assert_ne "(c) there is a session to compare (QA News One playing)" " | " "$BEFORE"
assert_eq "(c) the session is unchanged" "$BEFORE" "$AFTER"

# ----------------------------------------------------------------------------------------------- 4: over the keyguard
log "--- 4: set a PIN; sleep / wake; type_request \"play jazz radio\"; clear the PIN"
letter d
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2
record "(d) the player before the keyguard leg (paused, so the request has to start it)" "$(sboth)"
assert_ne "(d) nothing is playing before the request" "PLAYING" "$(sstate)"
adb shell locksettings set-disabled false > "$ROW_DIR/K-set-disabled.out" 2>&1
adb shell locksettings set-pin $PIN > "$ROW_DIR/K-set-pin.out" 2>&1 && pin_set=yes
assert_eq "(d) a PIN is set and the keyguard is enabled" "yes false" "$pin_set $(adb shell locksettings get-disabled | tr -d '\r')"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; sleep 3
assert_eq "(d) awake, the keyguard showing (read without wake_device's dismiss)" "Awake isKeyguardShowing=true" "$(wakeful) $(keyguard)"
open_tess K-tess; shot 05-keyguard-tess
assert_eq "(d) Tess is open over the keyguard with her text box" "yes yes" "$(has K-tess cortana_session) $(has K-tess cortana_text_box_field)"
MARK="$(ring_mark)"
type_request "play jazz radio" 8; assert_eq "(d) type_request found the text box" "0" "$?"
for _ in 1 2 3 4 5 6; do [ "$(sstate)" = PLAYING ] && break; sleep 1; done
KG="$(keyguard)"; SESSION="$(sboth)"; REPLY="$(last_reply "$MARK")"
ring_since "$MARK" launcher > "$ROW_DIR/slice-keyguard.txt"; session_save session-keyguard
d K-typed; shot 06-keyguard-playing
adb shell dumpsys window | tr -d '\r' | grep -m3 -i 'keyguard' > "$ROW_DIR/K-window.txt"
record "(d) every reply since the mark (the LAST is the one read)" "$(replies_from "$MARK" | tr '\n' '|')"
record "(d) keyguard / session" "$KG / $SESSION"
assert_eq "(d) isKeyguardShowing=true while it plays" "isKeyguardShowing=true" "$KG"
assert_eq "(d) it plays over the keyguard: state" "PLAYING" "${SESSION%% |*}"
assert_contains "(d) …QA Jazz One" "QA Jazz One" "${SESSION#* | }"
assert_eq "(d) …and the last reply names it" "Playing QA Jazz One." "$REPLY"
record "(d) the [music] lines" "$(grep -F '[music]' "$ROW_DIR/slice-keyguard.txt" | grep -E 'search|stream: connected' | stripped | cut -c1-120 | tr '\n' ';')"
cortana_close
letter restore
rings_save
restore_keyguard
assert_eq "the PIN is cleared and the keyguard disabled again" "no true" "$pin_set $(adb shell locksettings get-disabled | tr -d '\r')"
assert_eq "awake" "Awake" "$(wake_device)"
sleep 1
assert_eq "no keyguard" "isKeyguardShowing=false" "$(keyguard)"
adb shell input keyevent KEYCODE_MEDIA_STOP; sleep 1
ensure_start

# ----------------------------------------------------------------------------------------------- 5: J5
log "--- 5: qa/phase-03/scripts/j5.sh"
letter e
rings_save
flock -u 9          # j5.sh takes the device lock itself
bash "$QAROOT/phase-03/scripts/j5.sh" > "$ROW_DIR/j5.out" 2>&1; echo $? > "$ROW_DIR/j5.rc"
flock -n 9 || log "      (the device lock could not be taken back)"
cp "$QAROOT/phase-03/J5/J5.txt" "$ROW_DIR/J5.txt" 2>/dev/null
J5SUM="$(grep -o 'J5: [0-9]* passed, [0-9]* failed' "$ROW_DIR/j5.out" | tail -1)"
record "(e) j5.sh's own summary / rc" "$J5SUM / $(cat "$ROW_DIR/j5.rc")"
assert_eq "(e) j5.sh rc" "0" "$(cat "$ROW_DIR/j5.rc")"
assert_eq "(e) j5.sh 8/8" "J5: 8 passed, 0 failed" "$J5SUM"

adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
fixtures_down
flog "$RLOG.jsonl" 'True' > "$ROW_DIR/radio-requests.txt"
ime_baseline
ensure_start
p20_end a b c d e
