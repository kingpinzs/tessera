#!/usr/bin/env bash
# L18-1 — Music's exported session never plays, or tells anything about, a file another app names (the INDEX
# Blocked-on ledger's row L18-1; the adversarial GATE review's H1 and its device experiment 1).
#
# MusicService is exported, so any app can connect a controller. testapps/qa-capture's MediaProbeActivity (an app that
# holds NO permission) does it the three ways an app can — a Media3 MediaController sending `setMediaItem` with an item
# that CARRIES a URI, the shell's own PLAY_FILE custom command, and a legacy MediaBrowser's play-from-URI — for four
# URIs, one run each:
#   hidden    file://…/QA-Files/hidden/qa-hidden.mp3         a real MP3 in a .nomedia folder (no MediaStore row)
#   missing   file://…/QA-Files/hidden/qa-missing.mp3        no such file
#   text      file://…/QA-Files/a.txt                        a file that is not media
#   provider  content://app.tileshell.files/root…/qa-hidden.mp3   the shell's own, unexported FileProvider
# The safe outcome (the fix's guarantee: an item from a controller that is not the shell's uid never keeps the URI it
# came with — MusicItemRule):
#   * nothing plays: `dumpsys media_session` shows no PLAYING music session after any of the four, and the fresh
#     controller the probe reads the session with reports no item, no title, no duration and no error;
#   * the probe's item DID carry its URI and the session said so: `[music] controller uid <qa-capture's uid>: 1 item(s)
#     came with a uri of their own, none used (0 from the library)` — without that line the leg proved nothing;
#   * PLAY_FILE is not offered to the probe and its ask is refused; no `[music] play file` line is ever written;
#   * what the probe observes is THE SAME TEXT for all four URIs (an existence or a file-type oracle would differ).
# Positive control (the shell's own controller keeps working): 03.mp3 tapped in Files plays through the same session
# (`state=PLAYING` with its MediaStore title), and a probe run WHILE it plays neither stops nor replaces it.
# The JVM side of the same guarantee (the rule, and the service's wiring read from its source) is gated last.
#
# Needs the fixed build installed (this row asserts the fix; on the phase's gate candidate 87f6eac1 it must FAIL) and
# rowsb.sh beside it (tap_row / files_at / session_state, the row writers' include). Nothing here touches the host's
# audio; the emulator runs with QEMU_AUDIO_DRV=none.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
[ -f "$HERE/rowsb.sh" ] && . "$HERE/rowsb.sh"
MUSIC=app.tileshell/.music.MusicActivity
QFP=/storage/emulated/0/QA-Files
PROBE="$QAC/.MediaProbeActivity"

keep_earlier_run L18_1
row_begin L18_1 "Music's exported session: another app's controller never makes it play, or learn about, a file it names"
if ! declare -F tap_row >/dev/null || ! declare -F session_state >/dev/null; then
  _verdict FAIL "rowsb.sh is beside this driver (tap_row, session_state)" "missing $HERE/rowsb.sh"
  row_end; exit 1
fi
record "the installed APK (md5; this row needs the build that holds the L18-1 fix)" "$(gate_apk_installed)"
baseline_start
files_up || { row_end; exit 1; }

# The music sessions only (the :video player's session has its own id), and the lines the probe logged for one tag.
music_session() { session_state | grep -v session.id.video; }
music_playing() { music_session | grep -c 'state=PLAYING'; }
probe_out() { adb logcat -d -s "$QAC_TAG:I" 2>/dev/null | tr -d '\r' | grep -o "media $1 .*" | sed "s/^media $1 //"; }

# ------------------------------------------------------------------------------------------------ the probe app
QAC_AT_BEGIN="$(q "pm path $QAC")"
if [ ! -f "$QAC_APK" ]; then
  _verdict FAIL "qa-capture is built" "missing $QAC_APK (./gradlew :testapps:qa-capture:assembleDebug --offline)"
  files_down; row_end; exit 1
fi
adb install -r "$QAC_APK" > "$ROW_DIR/qa-capture-install.out" 2>&1
assert_contains "qa-capture installed (no -g: it holds no permission)" "Success" "$(cat "$ROW_DIR/qa-capture-install.out")"
QAC_UID="$(q "cmd package list packages -U $QAC" | sed -n "s/^package:$QAC uid://p" | head -1)"
SHELL_APP_UID="$(q "cmd package list packages -U app.tileshell" | sed -n 's/^package:app.tileshell uid://p' | head -1)"
assert_ne "the probe's uid is known and is not the shell's ($QAC_UID / $SHELL_APP_UID)" "$SHELL_APP_UID" "${QAC_UID:-$SHELL_APP_UID}"
record "qa-capture's requested permissions that are granted (dumpsys package)" "$(q "dumpsys package $QAC" | grep -c 'granted=true')"
assert_contains "precondition: the hidden MP3 is on the device" "$QFP/hidden/qa-hidden.mp3" "$(q "ls $QFP/hidden/qa-hidden.mp3")"
assert_eq "precondition: it has no audio row (a .nomedia folder)" "0" "$(q "content query --uri content://media/external/audio/media --projection _data" | grep -c 'qa-hidden.mp3')"
assert_eq "precondition: the missing path is missing" "" "$(q "ls $QFP/hidden/qa-missing.mp3 2>/dev/null")"

# ------------------------------------------------------------------------------------------------ the four URIs
# Music cold: the session then holds nothing, so anything it holds after a probe came from that probe.
c6; ensure_start
record "the music session before any probe (none, or not playing)" "$(music_session | xargs)"
assert_eq "before any probe: no music session is PLAYING" "0" "$(music_playing)"

probe() { # tag uri
  local tag="$1" uri="$2" m sl out
  # Music cold before EVERY probe, so the four runs start from one state and their lines can be compared as text.
  c6; ensure_start
  adb logcat -c
  m="$(ring_mark)"
  adb shell "am start -W -n $PROBE --es tag $tag --es uri '$uri' --es id qa-probe-$tag" > "$ROW_DIR/probe-$tag.start.txt" 2>&1
  sleep 12   # the probe: connect, 4 s, a fresh controller, the legacy browser, 4 s
  out="$(probe_out "$tag")"; printf '%s\n' "$out" > "$ROW_DIR/probe-$tag.txt"
  sl="$(ring_since "$m")"; printf '%s\n' "$sl" | grep -F '[music]' > "$ROW_DIR/probe-$tag.ring.txt"
  session_state > "$ROW_DIR/probe-$tag.session.txt"; adb shell dumpsys media_session > "$ROW_DIR/probe-$tag.media_session.txt"
  assert_contains "$tag: the probe ran to its end" "done" "$(printf '%s\n' "$out" | tail -1)"
  assert_contains "$tag: its Media3 controller connected (the service is exported) and was NOT offered PLAY_FILE" "connect=connected play_file_offered=false" "$out"
  # The leg is only a test if the item reached the session carrying its URI: the session's own line says it did.
  assert_contains "$tag: the session saw an item with a URI of the probe's own, and used none" \
    "[music] controller uid $QAC_UID: 1 item(s) came with a uri of their own, none used (0 from the library)" "$sl"
  absent_in "$tag: no file was played through the session (no [music] play file line)" "[music] play file" "$sl"
  absent_in "$tag: no library track was started for it either" "[music] play " "$sl"
  assert_eq "$tag: dumpsys media_session — no music session is PLAYING" "0" "$(grep -v session.id.video "$ROW_DIR/probe-$tag.session.txt" | grep -c 'state=PLAYING')"
  assert_contains "$tag: a fresh controller sees an empty session — no item, no title, no duration, no error" \
    "items=0 id=none title=none artist=none album=none duration=unset error=none saw_error=none saw_playing=false" "$(printf '%s\n' "$out" | grep '^observed ')"
  assert_contains "$tag: …and it is not playing" " playing=false items=0 " "$(printf '%s\n' "$out" | grep '^observed ')"
  assert_ne "$tag: the PLAY_FILE ask was answered" "" "$(printf '%s\n' "$out" | grep '^play_file ')"
  assert_ne "$tag: …and not with success (SessionResult.RESULT_SUCCESS = 0)" "play_file result=0" "$(printf '%s\n' "$out" | grep '^play_file ')"
  record "$tag: the probe's lines" "$(printf '%s\n' "$out" | tr '\n' ';')"
}
probe hidden "file://$QFP/hidden/qa-hidden.mp3"
probe missing "file://$QFP/hidden/qa-missing.mp3"
probe text "file://$QFP/a.txt"
probe provider "content://app.tileshell.files/root$QFP/hidden/qa-hidden.mp3"

# The oracle check: everything the probe could observe, as text, is identical for the four URIs.
for t in missing text provider; do
  assert_eq "the probe observes the SAME for $t as for the real MP3 (every line it logged)" "$(cat "$ROW_DIR/probe-hidden.txt")" "$(cat "$ROW_DIR/probe-$t.txt")"
done
assert_ne "…and that text is not empty (the comparison is of real lines)" "" "$(grep '^observed ' "$ROW_DIR/probe-hidden.txt")"

# ------------------------------------------------------------------------------------------------ the positive control
log "--- positive control: the shell's own play of a library track, and a probe while it plays"
AUD="$(q "content query --uri content://media/external/audio/media --projection _id:_data" | grep -F "_data=$QFP/03.mp3" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
TITLE="$(q "content query --uri content://media/external/audio/media --projection _id:title --where _id=$AUD" | sed -n 's/.*title=\(.*\)$/\1/p')"
assert_ne "03.mp3 has an audio row and a title ($AUD / $TITLE)" "" "${AUD:+x}${TITLE:+x}"
M="$(ring_mark)"; tap_row "$QFP" 03.mp3 4; screencap "$ROW_DIR/control.png"
assert_eq "03.mp3 tapped in Files: top_activity is Music" "$MUSIC" "$(top_activity)"
SL="$(ring_since "$M")"; printf '%s\n' "$SL" | grep -E '\[(files|music)\]' > "$ROW_DIR/control.ring.txt"
absent_in "the shell's own launch is not ignored" "play extra ignored" "$SL"
absent_in "the shell's own controller's items are kept: no 'none used' line for it" "came with a uri of their own" "$SL"
session_state > "$ROW_DIR/control.session.txt"
assert_contains "the shell's music session is PLAYING that track" "state=PLAYING desc=[$TITLE," "$(grep -v session.id.video "$ROW_DIR/control.session.txt")"

adb logcat -c
M="$(ring_mark)"
adb shell "am start -W -n $PROBE --es tag playing --es uri 'file://$QFP/hidden/qa-hidden.mp3' --es id qa-probe-playing" > "$ROW_DIR/probe-playing.start.txt" 2>&1
sleep 12
probe_out playing > "$ROW_DIR/probe-playing.txt"
SL="$(ring_since "$M")"; printf '%s\n' "$SL" | grep -F '[music]' > "$ROW_DIR/probe-playing.ring.txt"
session_state > "$ROW_DIR/probe-playing.session.txt"
assert_contains "while it plays: the session saw the probe's URI item and used none" "[music] controller uid $QAC_UID: 1 item(s) came with a uri of their own, none used (0 from the library)" "$SL"
absent_in "while it plays: no file was played for the probe" "[music] play file" "$SL"
assert_contains "while it plays: the session still holds the shell's own track (not stopped, not replaced)" "desc=[$TITLE," "$(grep -v session.id.video "$ROW_DIR/probe-playing.session.txt")"
# What another app's controller may do with what is ALREADY playing (pause it, read its title) is the session's ordinary
# contract and not this row's subject; the legacy controller's play request can only resume the shell's own track.
record "while it plays: the music session / the probe's lines" "$(grep -v session.id.video "$ROW_DIR/probe-playing.session.txt" | xargs) / $(tr '\n' ';' < "$ROW_DIR/probe-playing.txt")"
# The probe's own text is not a ring slice (it holds no wall= stamp, so absent_in could never pass on it — the row's
# first run): the plain-text absence, after proving the text is the probe's real lines.
assert_ne "while it plays: the probe logged what it observed (the text the next line searches is real)" "" "$(grep '^observed ' "$ROW_DIR/probe-playing.txt")"
assert_absent "while it plays: the fresh controller never sees the probe's own id as the item" "id=qa-probe-playing" "$(cat "$ROW_DIR/probe-playing.txt")"

# ------------------------------------------------------------------------------------------------ restore
c6; ensure_start
assert_eq "restore: no music session is PLAYING" "0" "$(music_playing)"
if [ -z "$QAC_AT_BEGIN" ]; then
  adb uninstall "$QAC" > "$ROW_DIR/qa-capture-uninstall.out" 2>&1
  assert_eq "qa-capture was not installed at the start: uninstalled again" "" "$(q "pm path $QAC")"
fi
files_down
ensure_start

# ------------------------------------------------------------------------------------------------ the JVM side
log "--- the rule and the service's wiring on the JVM"
jvm_gate '*MusicItemRuleTest*' '*MusicItemRuleTest*' \
  "another app's controller NEVER keeps the URI it sent - whatever the id, the search or the uid" \
  "the shell's own controller keeps the URI its item carries - the library queue and the one-file item alike"
row_end
