#!/usr/bin/env bash
# EDGE_RECORDER — the phase doc's Recorder and "Other apps' recordings" Edge cases (phase-15 doc, Edge cases; Decisions
# "Voice Recorder mechanics", T15-3, T15-26, T15-36, T15-38, T15-39). One row, eight cases, in this order (takes carry over
# between cases and are deleted through the app at the restore):
#   1. a call mid-take (adb emu gsm call / accept / cancel), four calls in one take: pause at MODE_RINGTONE, paused
#      through MODE_IN_CALL, resume on Resume; the pulled file has no gap at a call pause beyond one AAC frame (1024
#      samples) — graded on the best of the four, since this AVD's microphone reopen itself loses 20-70 ms at random
#   2. RECORD_AUDIO revoked mid-take (pm revoke): the take stops (Android kills the app's processes on a revoke) and is
#      saved (recovered at the next open, T15-39); the page shows the permission state; the grant restored
#   3. the microphone taken by a third-party app capturing on top (Open Camera, on the image, recording video), three
#      times in one take: dumpsys audio reports the shell's client silenced, the take pauses with `paused: silenced`
#      (T15-38) within 200 ms of Android's own event, and each Resume records again (DEFECT.md: it is sometimes undone)
#   4. rename to an existing name (" (1)" appended), rename with characters MediaStore refuses (notice), trim to zero
#      length (refused)
#   5. share with no app able to receive audio: every ACTION_SEND audio/mp4 receiver disabled (as root, `pm disable`),
#      Android's chooser shows its empty state; each receiver put back to the state it had
#   6. the :recorder process killed between takes (kill -9 by its pid, as root): nothing lost, nothing recovered, the
#      next take records
#   7. a recording deleted outside the app (adb shell rm + a rescan): its row leaves the list through the ContentObserver
#      with no restart
#   8. other apps' recordings: a foreign recording deleted by its own app while listed (content delete by
#      com.android.shell, the fixture's owner), and a foreign IS_RECORDING file outside Recordings/ (Download/Recordings/,
#      which MediaStore itself marks is_recording 1): listed, read-only
# Storage full (E19) and the microphone taken by Tess (E17) are already proved on this build; cited, not repeated.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin EDGE_RECORDER "Voice Recorder edge cases: call, revoke, silenced, rename/trim refusals, share, kill, deletes, foreign files"

# ---------------------------------------------------------------- helpers
# A node's text by its tag, parsed as XML (a text holding a double quote is written with single quotes, which
# lib.sh's node_text regex cannot read — the rename notice is one).
xtext() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print(""); sys.exit()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(n.get("text", "")); break
PY
}
xattr() { # dump.xml resource-id attr
  python3 - "$1" "$2" "$3" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print(""); sys.exit()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(n.get(sys.argv[3], "")); break
PY
}
# dumpsys audio's CURRENT mode (edge_alarms.sh's reading: the setMode history keeps older modes).
amode() { adb shell dumpsys audio | tr -d '\r' | sed -n 's/^- Actual mode = //p' | head -1; }
wall_of() { grep -oE 'wall=[0-9]+' <<< "$1" | head -1 | cut -d= -f2; }
wait_status() { # key value timeout_s -> prints what it saw last
  local want="$2" limit=$(( $3 * 4 )) i=0 v=""
  while [ "$i" -lt "$limit" ]; do
    v="$(rec_status "$1")"; [ "$v" = "$want" ] && { echo "$v"; return 0; }
    sleep 0.25; i=$((i + 1))
  done
  echo "$v"; return 1
}
pidof_dev() { adb shell pidof "$1" | tr -d '\r'; }
# The gap at a pause point in a pulled take (the Edge case: "no gap artefact at the pause point beyond one AAC frame").
# The file is decoded to 44.1 kHz mono; its amplitude envelope (the analytic signal's magnitude, so the tone's own zero
# crossings do not read as gaps) is searched within ±300 ms of the pause's take time (the decoder's priming offset lies
# inside that). GAP = the longest run of samples whose envelope is below -40 dBFS; LEVEL_BEFORE / LEVEL_AFTER are the
# envelope medians 0.5-1.5 s either side (the tone is there); DIPS20 lists every run below -20 dBFS longer than 20 samples
# in the window, and CONTROL20 the same count over an equal window 1 s earlier (the host's audio path dips on its own).
gap_report() { # file.m4a pause_ms|start
  python3 - "$1" "$2" <<'PY'
import subprocess, sys
import numpy as np
f, pm = sys.argv[1], sys.argv[2]
pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-f", "s16le", "-ac", "1", "-ar", "44100", "-"],
                     capture_output=True).stdout
d = np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768.0
SR = 44100
def env(x):
    n = len(x); X = np.fft.fft(x); h = np.zeros(n)
    if n % 2 == 0: h[0] = h[n // 2] = 1; h[1:n // 2] = 2
    else: h[0] = 1; h[1:(n + 1) // 2] = 2
    return np.abs(np.fft.ifft(X * h))
e = env(d)
def runs(lo, off):
    out = []; i = 0
    while i < len(lo):
        if lo[i]:
            j = i
            while j < len(lo) and lo[j]: j += 1
            out.append((off + i, j - i)); i = j
        else: i += 1
    return out
if pm == "start":
    # the take's own start: after the encoder's 2048 priming samples, to 0.5 s
    p = 2048; a, b = 2048, min(len(e), int(0.5 * SR))
else:
    p = int(float(pm) / 1000.0 * SR)
    a, b = max(0, p - int(0.3 * SR)), min(len(e), p + int(0.3 * SR))
w = e[a:b]
gap = max([n for _, n in runs(w < 10 ** (-40 / 20), a)] or [0])
dips = [(round(s / SR, 4), n) for s, n in runs(w < 10 ** (-20 / 20), a) if n > 20]
ca, cb = max(0, a - SR), max(0, b - SR)
ctrl = [(round(s / SR, 4), n) for s, n in runs(e[ca:cb] < 10 ** (-20 / 20), ca) if n > 20]
def lvl(s, t):
    s, t = max(0, int(s * SR)), min(len(e), int(t * SR))
    if t <= s: return "-inf"
    m = float(np.median(e[s:t])); return "%.1f" % (20 * np.log10(m)) if m > 0 else "-inf"
print("GAP=%d" % gap)
print("LEVEL_BEFORE=%s" % lvl(p / SR - 1.5, p / SR - 0.5))
print("LEVEL_AFTER=%s" % lvl(p / SR + 0.5, p / SR + 1.5))
print("DIPS20=%s" % dips)
print("CONTROL20=%s" % ctrl)
print("SECONDS=%.3f" % (len(d) / SR))
PY
}
kv() { grep -m1 "^$1=" <<< "$2" | cut -d= -f2-; }
# The enabled state a component has for user 0: enabled | disabled | default (neither list in dumpsys package).
comp_state() { # pkg/cls (cls may be short, ".x.Y")
  local pkg="${1%%/*}" cls="${1#*/}"; case "$cls" in .*) cls="$pkg$cls" ;; esac
  adb shell dumpsys package "$pkg" | tr -d '\r' | python3 -c '
import sys
cls = sys.argv[1]; state = "default"; sect = None; user0 = False
for l in sys.stdin:
    s = l.strip()
    if s.startswith("User 0:"): user0 = True; sect = None; continue
    if s.startswith("User ") and not s.startswith("User 0:"): user0 = False; sect = None
    if not user0: continue
    if s == "disabledComponents:": sect = "disabled"; continue
    if s == "enabledComponents:": sect = "enabled"; continue
    if s.endswith(":"): sect = None
    if sect and s == cls: state = sect
print(state)' "$cls"
}
full_comp() { local pkg="${1%%/*}" cls="${1#*/}"; case "$cls" in .*) cls="$pkg$cls" ;; esac; echo "$pkg/$cls"; }
audio_receivers() { adb shell cmd package query-activities --brief -a android.intent.action.SEND -t audio/mp4 | tr -d '\r' | grep '/' | sed 's/^ *//'; }
# The recorder's one ongoing notification's title (id 1507).
notif_title() { shell_notification_block 1507 | grep -oE 'android.title=[^)]*\)' | head -1; }
ms_md5() { adb shell md5sum "'$1'" 2>/dev/null | cut -c1-32; }
root_on() { adb root >/dev/null 2>&1; adb wait-for-device; }
root_off() { adb unroot >/dev/null 2>&1; adb wait-for-device; }

TONE_PID=""; OC_WAS_RUNNING=""; RECV_DISABLED=""
stop_tone() { [ -n "$TONE_PID" ] && { kill "$TONE_PID" 2>/dev/null; wait "$TONE_PID" 2>/dev/null; }; TONE_PID=""; }
# A single uninterrupted tone for the takes whose audio is analysed (tone_loop_start restarts paplay every 10 s, and a
# restart is a dip of its own), played once with the very command `audio.sh say` runs (`paplay -d $AUDIO_SINK`), so the
# recorded pid IS the player and stopping it stops the sound (killing a wrapper would leave paplay playing on).
tone_once() { paplay -d "$AUDIO_SINK" "$(tone "${1:-60}")" >/dev/null 2>&1 & TONE_PID=$!; }
# Case 5's receivers back to the state each had (enabled -> pm enable, default -> pm default-state, disabled -> pm
# disable), as root; also run by the EXIT trap if the row dies while they are disabled (RV12).
restore_receivers() {
  root_on
  while read -r c s <&3; do
    case "$s" in
      enabled) adb shell pm enable --user 0 "$c" >> "$LOG" 2>&1 ;;
      disabled) adb shell pm disable --user 0 "$c" >> "$LOG" 2>&1 ;;
      *) adb shell pm default-state --user 0 "$c" >> "$LOG" 2>&1 ;;
    esac
  done 3< "$ROW_DIR/share_receivers_states.txt"
  root_off
  RECV_DISABLED=""
}
on_exit() { stop_tone; [ "$RECV_DISABLED" = yes ] && restore_receivers; adb emu gsm cancel 5551234 >/dev/null 2>&1; return 0; }
trap on_exit EXIT

# ---------------------------------------------------------------- baseline
wake_device >/dev/null
note "baseline grants: $(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -E 'android.permission.(RECORD_AUDIO|READ_MEDIA_AUDIO): granted' | sed 's/^ *//' | paste -sd' ')"
note "baseline appops: $(adb shell appops get app.tileshell RECORD_AUDIO | tr -d '\r' | paste -sd' ')"
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
assert_eq "baseline: no IS_RECORDING file of another app's (no fixture left)" "" "$(recording_rows)"
assert_eq "baseline: no take left on disk by a dead :recorder (files/recorder empty)" "" "$(adb shell "run-as app.tileshell ls files/recorder" < /dev/null 2>/dev/null | tr -d '\r')"
record "Storage full is E19's (cited, not repeated)" "E19 on 4b7ac321: 15 passed, 0 failed (rec_notice 'Not enough space', [recorder] storage floor)"
record "The microphone taken by Tess is E17's (cited, not repeated)" "E17 on 4b7ac321: 26 passed, 0 failed (Tess refused: 'The voice recorder is using the microphone right now.')"
audio_route "EDGE_RECORDER"
LPID0="$(pidof_dev app.tileshell)"; note "launcher pid at start: $LPID0"

if [ "$AUDIO_OK" != yes ]; then
  _verdict FAIL "the recorder edge cases" "NOT RUN: the audio route failed its check"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  row_end; exit $?
fi

# ================================================================= 1. a call mid-take (T15-36)
# The tone plays from BEFORE the take (so the take's own first moments are a control: a microphone open with sound
# already there) and runs uninterrupted through FOUR call pauses in the one take. The first is checked step by step
# (RINGTONE, IN_CALL, a refused Resume during the call, the cancel, the Resume); all four are checked for the pause and
# the resume. Why four: on this AVD a microphone (re)open intermittently loses 20-70 ms at its start — measured before
# this row with USER pauses and no call at all (2.5 / 4 / 6 / 8 s pauses: 870 / 27 / 1845 / 10 samples below -40 dBFS;
# the host's capture stream is the same PipeWire stream throughout, corked while paused), and at take starts with the
# tone already playing. A gap the call handling made would be in every call pause; the AVD's intermittent open loss is
# not. So the clause is asserted on the best of the four and every one is recorded with the take-start control.
log "── 1. a call arriving mid-take (four call pauses in one take)"
rec_open record; dump_ui "$ROW_DIR/c1_record.xml"
assert_eq "1: the record state shows (rec_page:record)" yes "$(has_node "$ROW_DIR/c1_record.xml" 'rec_page:record')"
assert_eq "1: the audio mode is MODE_NORMAL before the call" MODE_NORMAL "$(amode)"
BEFORE="$(own_ids | tr '\n' ' ')"
tone_once 150
sleep 1.5
CMARK1="$(ring_mark)"
gtap "$ROW_DIR/c1_record.xml" rec_button
wait_elapsed 4000 20 >/dev/null
assert_eq "1: the take is recording" recording "$(rec_status phase)"
CALL="$(ring_mark)"
adb emu gsm call 5551234 >/dev/null 2>&1
P="$(wait_status paused call 6)"
MODE_R="$(amode)"
rdump "$ROW_DIR/c1_ringing.xml"; screencap "$ROW_DIR/c1_ringing.png"
E_RING="$(rec_status elapsed_ms)"
NT1="$(notif_title)"
note "ringing: mode=$MODE_R phase=$(rec_status phase) paused=$P elapsed_ms=$E_RING notice=[$(xtext "$ROW_DIR/c1_ringing.xml" rec_notice)] notification $NT1"
assert_eq "1: dumpsys audio reads MODE_RINGTONE while it rings" MODE_RINGTONE "$MODE_R"
assert_eq "1: the take is paused for the call (status paused=call)" call "$P"
assert_eq "1: the page says so (rec_notice)" "The recording is paused for the call." "$(xtext "$ROW_DIR/c1_ringing.xml" rec_notice)"
rec_ring "$CALL" > "$ROW_DIR/ring_call_recorder.txt"
M1="$(grep -F '[recorder] audio mode 1' "$ROW_DIR/ring_call_recorder.txt" | head -1)"
PC="$(grep -F '[recorder] paused: call' "$ROW_DIR/ring_call_recorder.txt" | head -1)"
assert_ne "1: the :recorder ring saw the mode become RINGTONE ([recorder] audio mode 1)" "" "$M1"
assert_ne "1: the :recorder ring holds [recorder] paused: call" "" "$PC"
assert_absent "1: the pause came at RINGTONE, before any IN_CALL (no audio mode 2 yet)" "[recorder] audio mode 2" "$(cat "$ROW_DIR/ring_call_recorder.txt")"
[ -n "$M1" ] && [ -n "$PC" ] && assert_eq "1: paused within 500 ms of the RINGTONE mode line" yes "$(python3 -c 'import sys; d=int(sys.argv[2])-int(sys.argv[1]); print("yes" if 0 <= d <= 500 else "no (%d ms)" % d)' "$(wall_of "$M1")" "$(wall_of "$PC")")"
sleep 2
adb emu gsm accept 5551234 >/dev/null 2>&1; sleep 3
MODE_C="$(amode)"; E_CALL="$(rec_status elapsed_ms)"
note "in call: mode=$MODE_C phase=$(rec_status phase) paused=$(rec_status paused) elapsed_ms=$E_CALL"
assert_eq "1: after gsm accept dumpsys audio reads MODE_IN_CALL" MODE_IN_CALL "$MODE_C"
assert_eq "1: still paused through IN_CALL (paused=call)" call "$(rec_status paused)"
assert_eq "1: the take's clock stood still through the ring and the call" "$E_RING" "$E_CALL"
# Resume while the call is on is refused (TakeClock.resume(inCall)): the take stays paused until after the call.
rec_open record; rdump "$ROW_DIR/c1_incall.xml"
RMARK_IC="$(ring_mark)"
gtap "$ROW_DIR/c1_incall.xml" rec_pause; sleep 1.5
rdump "$ROW_DIR/c1_incall2.xml"
rec_ring "$RMARK_IC" > "$ROW_DIR/ring_call_resume_refused_recorder.txt"
assert_contains "1: Resume during the call is refused (:recorder ring)" "[recorder] resume refused: a call is on" "$(cat "$ROW_DIR/ring_call_resume_refused_recorder.txt")"
assert_eq "1: and the page says to resume after the call (rec_notice)" "Resume the recording after the call." "$(xtext "$ROW_DIR/c1_incall2.xml" rec_notice)"
assert_eq "1: the take is still paused for the call" call "$(rec_status paused)"
assert_contains "1: the ongoing notification says Paused during the call" "Paused" "$(notif_title)"
adb emu gsm cancel 5551234 >/dev/null 2>&1
for _ in $(seq 1 20); do [ "$(amode)" = MODE_NORMAL ] && break; sleep 0.25; done
sleep 1
assert_eq "1: after gsm cancel the mode is MODE_NORMAL again" MODE_NORMAL "$(amode)"
assert_eq "1: the call's end does not resume the take by itself (paused=call)" call "$(rec_status paused)"
assert_eq "1: the take's clock still stands at the pause" "$E_RING" "$(rec_status elapsed_ms)"
rec_open record; rdump "$ROW_DIR/c1_after.xml"; screencap "$ROW_DIR/c1_after.png"
RMARK="$(ring_mark)"
gtap "$ROW_DIR/c1_after.xml" rec_pause
assert_eq "1: Resume after the call records again (phase recording)" recording "$(wait_phase recording 10)"
E2="$(wait_elapsed $(( E_RING + 3000 )) 20)"; note "take clock after resuming: $E2"
assert_eq "1: after Resume the take's clock advances (3 s more take time within 20 s)" yes "$(python3 -c 'import sys; e=sys.argv[1]; print("yes" if e.isdigit() and int(e) >= int(sys.argv[2]) else "no (%s)" % e)' "$E2" "$(( E_RING + 3000 ))")"
rec_ring "$RMARK" > "$ROW_DIR/ring_call_resumed_recorder.txt"
assert_contains "1: the :recorder ring holds [recorder] resumed at <the pause's take time>" "[recorder] resumed at $E_RING ms" "$(cat "$ROW_DIR/ring_call_resumed_recorder.txt")"
PAUSES="$E_RING"
# calls 2-4: ring, answer, hang up, Resume
for k in 2 3 4; do
  CK="$(ring_mark)"
  adb emu gsm call 5551234 >/dev/null 2>&1
  PK="$(wait_status paused call 6)"; EK="$(rec_status elapsed_ms)"
  adb emu gsm accept 5551234 >/dev/null 2>&1; sleep 1.5
  MK="$(amode)"
  adb emu gsm cancel 5551234 >/dev/null 2>&1
  for _ in $(seq 1 20); do [ "$(amode)" = MODE_NORMAL ] && break; sleep 0.25; done
  sleep 0.5
  rec_open record; rdump "$ROW_DIR/c1_after$k.xml"
  gtap "$ROW_DIR/c1_after$k.xml" rec_pause
  wait_phase recording 10 >/dev/null
  EA="$(wait_elapsed $(( EK + 2500 )) 20)"
  rec_ring "$CK" > "$ROW_DIR/ring_call${k}_recorder.txt"
  assert_eq "1: call $k: after Resume the take's clock advances (2.5 s within 20 s)" yes "$(python3 -c 'import sys; e=sys.argv[1]; print("yes" if e.isdigit() and int(e) >= int(sys.argv[2]) else "no (%s)" % e)' "$EA" "$(( EK + 2500 ))")"
  note "call $k: paused=$PK at take time $EK; mode after accept $MK"
  assert_eq "1: call $k pauses the take (paused=call)" call "$PK"
  assert_contains "1: call $k: [recorder] paused: call then resumed at its take time" "[recorder] resumed at $EK ms" "$(cat "$ROW_DIR/ring_call${k}_recorder.txt")"
  PAUSES="$PAUSES $EK"
done
rdump "$ROW_DIR/c1_rec2.xml"
gtap "$ROW_DIR/c1_rec2.xml" rec_button
wait_phase idle 15 >/dev/null; sleep 1.5
stop_tone
rec_ring "$CMARK1" > "$ROW_DIR/ring_call_take_recorder.txt"
T1="$(new_own_id "$BEFORE")"; note "take 1 (the call take): id ${T1:-none} ($(ms_field "$(row_by_id "$T1")" _display_name))"
assert_ne "1: the take is saved in MediaStore" "" "$T1"
SP="$(grep -F '[recorder] stop Recording' "$ROW_DIR/ring_call_take_recorder.txt" | tail -1)"
STOP_MS="$(grep -oE 'ms=[0-9]+' <<< "$SP" | head -1 | cut -d= -f2)"
pull_take "$T1" "$ROW_DIR/call_take.m4a"
D1="$(file_duration "$ROW_DIR/call_take.m4a")"; note "call take: $D1 s, stop line ms=$STOP_MS, $(file_stream "$ROW_DIR/call_take.m4a")"
assert_within "1: the file's duration is the take's own clock (stop ms, ± 0.15 s)" "$(python3 -c "print(${STOP_MS:-0}/1000)")" "$D1" 0.15
# The calls are left out of the file: wall(start..stop) - file = the sum of wall(paused..resumed), within 1 s.
LEFT="$(python3 - "$ROW_DIR/ring_call_take_recorder.txt" "$D1" <<'PY'
import re, sys
st = sp = None; spans = []; p = None
for l in open(sys.argv[1]):
    m = re.search(r"wall=(\d+) \[recorder\] (start Recording|paused: call|resumed at|stop Recording)", l)
    if not m: continue
    w, what = int(m.group(1)), m.group(2)
    if what == "start Recording" and st is None: st = w
    elif what == "paused: call": p = w
    elif what == "resumed at" and p is not None: spans.append(w - p); p = None
    elif what == "stop Recording": sp = w
if st is None or sp is None: print(""); sys.exit()
print("%.2f" % (((sp - st) / 1000.0 - float(sys.argv[2])) - sum(spans) / 1000.0))
PY
)"
note "wall(start..stop) - file duration - the four paused spans = $LEFT s"
assert_within "1: the four paused spans are absent from the file (wall - file - pauses, ± 1 s)" 0 "$LEFT" 1
: > "$ROW_DIR/call_gap.txt"; BEST=""; ALL=""
for pm in $PAUSES; do
  G="$(gap_report "$ROW_DIR/call_take.m4a" "$pm")"; printf 'pause %s ms\n%s\n' "$pm" "$G" >> "$ROW_DIR/call_gap.txt"
  g="$(kv GAP "$G")"; LB="$(kv LEVEL_BEFORE "$G")"; LA="$(kv LEVEL_AFTER "$G")"
  # A pause is measurable only with the tone on both sides of it (the envelope medians 0.5-1.5 s either side > -20 dBFS):
  # run 2 saw the AVD's input fade out for 1.2 s WHILE RECORDING, ~1.5 s after a resume and before the next call rang.
  if [ "$(db_above "$LB" -20)" = yes ] && [ "$(db_above "$LA" -20)" = yes ]; then
    ALL="$ALL $pm:$g"; { [ -z "$BEST" ] || [ "$g" -lt "$BEST" ]; } && BEST="$g"
  else
    ALL="$ALL $pm:$g(unmeasurable:before=${LB}dB,after=${LA}dB)"
  fi
done
GS="$(gap_report "$ROW_DIR/call_take.m4a" start)"; printf 'take start\n%s\n' "$GS" >> "$ROW_DIR/call_gap.txt"
note "gaps at the four call pauses (take ms:samples below -40 dBFS):$ALL; the take's start (tone already playing): $(kv GAP "$GS") samples"
record "1: longest run below -40 dBFS at each call pause (take ms:samples), and at the take's own start (the AVD's open loss)" "$ALL; start: $(kv GAP "$GS")"
assert_ne "1: at least one call pause is measurable (the tone on both sides of it)" "" "$BEST"
assert_eq "1: no gap at the pause point beyond one AAC frame (best of the four call pauses <= 1024 samples)" yes "$(python3 -c 'import sys; g=int(sys.argv[1] or 99999); print("yes" if g <= 1024 else "no (%d samples)" % g)' "$BEST")"

# ================================================================= 2. RECORD_AUDIO revoked mid-take
log "── 2. RECORD_AUDIO revoked mid-take"
assert_contains "2: RECORD_AUDIO is granted before the revoke" "RECORD_AUDIO: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r')"
UIDMODE0="$(adb shell appops get app.tileshell RECORD_AUDIO | tr -d '\r' | grep 'Uid mode' | head -1)"; note "appops before: $UIDMODE0"
BEFORE="$(own_ids | tr '\n' ' ')"
rec_open record; rdump "$ROW_DIR/c2_record.xml"
RVMARK0="$(ring_mark)"
gtap "$ROW_DIR/c2_record.xml" rec_button
tone_once 60
wait_elapsed 5000 20 >/dev/null
assert_eq "2: the take is recording" recording "$(rec_status phase)"
RPID="$(pidof_dev app.tileshell:recorder)"; LPID1="$(pidof_dev app.tileshell)"
# Android kills every process of the app on a runtime-permission revoke: keep the rings first (T15-56).
rec_ring "$RVMARK0" > "$ROW_DIR/ring_revoke_prekill_recorder.txt"
ring_save launcher; ring_save "$REC_SVC"
ring_since "$ROW_MARK" > "$ROW_DIR/ring-launcher-prekill.txt"
E_RV="$(rec_status elapsed_ms)"
RV="$(ring_mark)"
adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO; RVRC=$?
note "pm revoke rc=$RVRC at take time $E_RV ms (recorder pid $RPID, launcher pid $LPID1)"
assert_eq "2: pm revoke RECORD_AUDIO rc" 0 "$RVRC"
sleep 3
stop_tone
note "after the revoke: launcher pid $(pidof_dev app.tileshell), recorder pid [$(pidof_dev app.tileshell:recorder)], speech pid [$(pidof_dev app.tileshell:speech)]"
assert_eq "2: the take stopped: no app.tileshell:recorder process" "" "$(pidof_dev app.tileshell:recorder)"
assert_eq "2: no recorder service left (dumpsys activity services)" "" "$(service_block .recorder.RecorderService)"
assert_eq "2: the ongoing notification is gone" "" "$(shell_notification_block 1507)"
record "2: the revoke killed the launcher too (pm revoke kills the app's uid)" "launcher pid $LPID1 -> $(pidof_dev app.tileshell)"
LEFTOVER="$(adb shell "run-as app.tileshell ls -la files/recorder" < /dev/null 2>&1 | tr -d '\r' | grep take- | awk '{print $5, $NF}' | paste -sd' ')"
note "left on disk by the killed process: $LEFTOVER"
assert_contains "2: the take is on disk as its kill-safe stream (take-*.aac)" ".aac" "$LEFTOVER"
assert_contains "2: RECORD_AUDIO now reads granted=false" "RECORD_AUDIO: granted=false" "$(adb shell dumpsys package app.tileshell | tr -d '\r')"
# Reopen: the new :recorder process recovers the take (T15-39) and the page shows the permission state.
OM="$(ring_mark)"
adb shell am start -W -n "$REC_ACT" >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/c2_reopen.xml"; screencap "$ROW_DIR/c2_reopen.png"
rec_ring "$OM" > "$ROW_DIR/ring_revoke_recorder.txt"
ring_since "$OM" > "$ROW_DIR/ring_revoke_launcher.txt"
REC2="$(grep -F '[recorder] recovered ' "$ROW_DIR/ring_revoke_recorder.txt" | head -1)"; note "recovery: ${REC2#*] }"
assert_contains "2: the take is saved: [recorder] recovered Recording (2) ms=<n> in the new :recorder ring" "[recorder] recovered Recording (2) ms=" "$REC2"
N2="$(grep -oE 'ms=[0-9]+' <<< "$REC2" | head -1 | cut -d= -f2)"
assert_within "2: recovered ms = the take time at the revoke (+ the revoke's own latency, 750 ± 1000 ms)" "$(( E_RV + 750 ))" "${N2:-0}" 1000
T2="$(new_own_id "$BEFORE")"; note "take 2 (the revoked take): id ${T2:-none} ($(ms_field "$(row_by_id "$T2")" _display_name))"
assert_ne "2: the recovered take is in MediaStore" "" "$T2"
assert_eq "2: the list shows it (rec_row)" yes "$(has_node "$ROW_DIR/c2_reopen.xml" "rec_row:$T2")"
assert_within "2: its rec_duration reads the take's length (± 1 s)" "$(python3 -c "print(round(${N2:-0}/1000))")" "$(clock_to_s "$(xtext "$ROW_DIR/c2_reopen.xml" "rec_duration:$T2")")" 1
pull_take "$T2" "$ROW_DIR/revoked_take.m4a"
D2="$(file_duration "$ROW_DIR/revoked_take.m4a")"; R2="$(file_rms "$ROW_DIR/revoked_take.m4a")"; note "revoked take: $D2 s, RMS $R2 dBFS"
assert_within "2: the pulled file plays: ffprobe duration = recovered ms (± 0.15 s)" "$(python3 -c "print(${N2:-0}/1000)")" "$D2" 0.15
assert_eq "2: and holds the tone (RMS > -40 dBFS)" yes "$(db_above "$R2" -40)"
assert_eq "2: the list's record button is disabled without the grant" false "$(xattr "$ROW_DIR/c2_reopen.xml" rec_button enabled)"
rec_open record; dump_ui "$ROW_DIR/c2_perm.xml"; screencap "$ROW_DIR/c2_perm.png"
assert_contains "2: the record page shows the permission state (rec_mic_notice)" "Voice Recorder can't use the microphone yet." "$(xtext "$ROW_DIR/c2_perm.xml" rec_mic_notice)"
assert_eq "2: and offers the grant (rec_mic_grant)" "allow access" "$(xtext "$ROW_DIR/c2_perm.xml" rec_mic_grant)"
assert_eq "2: its record button is disabled" false "$(xattr "$ROW_DIR/c2_perm.xml" rec_button enabled)"
# Restore the grant.
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO
assert_contains "2: restore: RECORD_AUDIO granted=true again" "RECORD_AUDIO: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r')"
assert_eq "2: restore: the appops uid mode is back to its value before" "$UIDMODE0" "$(adb shell appops get app.tileshell RECORD_AUDIO | tr -d '\r' | grep 'Uid mode' | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1.5
rec_open record; dump_ui "$ROW_DIR/c2_regranted.xml"
assert_eq "2: restore: the page no longer shows the notice" no "$(has_node "$ROW_DIR/c2_regranted.xml" rec_mic_notice)"
assert_eq "2: restore: the record button is enabled" true "$(xattr "$ROW_DIR/c2_regranted.xml" rec_button enabled)"

# ================================================================= 3. the microphone taken by a third-party app (T15-38)
log "── 3. a third-party app captures on top (Open Camera records video)"
OC=net.sourceforge.opencamera
OC_PERMS="$(adb shell dumpsys package $OC | tr -d '\r' | grep -E 'android.permission.(RECORD_AUDIO|CAMERA): granted' | sed 's/^ *//' | sort -u | paste -sd' ')"
note "Open Camera (third-party, $(adb shell dumpsys package $OC | tr -d '\r' | grep -m1 versionName | sed 's/^ *//')): $OC_PERMS"
assert_contains "3: Open Camera holds RECORD_AUDIO (it can capture)" "RECORD_AUDIO: granted=true" "$OC_PERMS"
OC_WAS_RUNNING="$(pidof_dev $OC)"; note "Open Camera running before: [${OC_WAS_RUNNING}]"
DCIM_OC_BEFORE="$(adb shell ls /sdcard/DCIM/OpenCamera 2>/dev/null | tr -d '\r' | paste -sd' ')"
DCIM_OC_EXISTED="$(adb shell "[ -d /sdcard/DCIM/OpenCamera ] && echo yes || echo no" | tr -d '\r')"
note "DCIM/OpenCamera before: exists=$DCIM_OC_EXISTED [$DCIM_OC_BEFORE]"
BEFORE="$(own_ids | tr '\n' ' ')"
rec_open record; rdump "$ROW_DIR/c3_record.xml"
SMARK0="$(ring_mark)"
gtap "$ROW_DIR/c3_record.xml" rec_button
tone_once 150
wait_elapsed 3000 20 >/dev/null
assert_eq "3: the take is recording" recording "$(rec_status phase)"
SESSION="$(rec_ring "$SMARK0" | grep -oE 'capture open: [^,]*, session [0-9]+' | tail -1 | grep -oE '[0-9]+$')"; note "the take's AudioRecord session: $SESSION"
adb shell am start -W -a android.media.action.VIDEO_CAMERA -n $OC/.MainActivity >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/c3_oc.xml"
if grep -q 'android:id/alertTitle' "$ROW_DIR/c3_oc.xml"; then
  # Open Camera's first-run note: Back cancels it WITHOUT setting its first-run flag (OK would write its prefs).
  record "3: Open Camera showed its first-run note; dismissed with Back (no pref written by the row)" "$(xtext "$ROW_DIR/c3_oc.xml" android:id/alertTitle)"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5; dump_ui "$ROW_DIR/c3_oc.xml"
fi
screencap "$ROW_DIR/c3_oc.png"
assert_eq "3: Open Camera is on top in video mode (its shutter says Start recording video)" "Start recording video" "$(xattr "$ROW_DIR/c3_oc.xml" $OC:id/take_photo content-desc)"
SIL="$(ring_mark)"
gtap "$ROW_DIR/c3_oc.xml" $OC:id/take_photo
PS="$(wait_status paused silenced 10)"
adb shell dumpsys audio | tr -d '\r' > "$ROW_DIR/c3_dumpsys_audio.txt"
E_SIL="$(rec_status elapsed_ms)"
NT=""; for _ in $(seq 1 12); do NT="$(notif_title)"; case "$NT" in *Paused*) break ;; esac; sleep 0.25; done
shell_notification_block 1507 > "$ROW_DIR/c3_notification.txt"
dump_ui "$ROW_DIR/c3_oc_rec.xml"; screencap "$ROW_DIR/c3_oc_rec.png"
rec_ring "$SIL" > "$ROW_DIR/ring_silenced_recorder.txt"
note "Open Camera recording: shutter [$(xattr "$ROW_DIR/c3_oc_rec.xml" $OC:id/take_photo content-desc)]; take phase=$(rec_status phase) paused=$PS elapsed_ms=$E_SIL; notification $NT"
assert_eq "3: Open Camera is recording video (its shutter says Stop recording video)" "Stop recording video" "$(xattr "$ROW_DIR/c3_oc_rec.xml" $OC:id/take_photo content-desc)"
MON="$(sed -n '/RecordActivityMonitor/,$p' "$ROW_DIR/c3_dumpsys_audio.txt")"
assert_contains "3: dumpsys audio: Open Camera's capture is active and not silenced" "pack:$OC -- format" "$(grep -F "pack:$OC" <<< "$MON" | grep -F 'silenced:false' | head -1)"
assert_contains "3: dumpsys audio reports the shell's client (its session) silenced" "silenced" "$(grep -E "rec (update|start) riid:[0-9]+ uid:[0-9]+ session:$SESSION src:MIC silenced pack:app.tileshell|session:$SESSION .*silenced:true" <<< "$MON" | head -1)"
assert_eq "3: the take is paused, reason silenced (status)" silenced "$PS"
assert_contains "3: the :recorder ring holds [recorder] paused: silenced" "[recorder] paused: silenced" "$(cat "$ROW_DIR/ring_silenced_recorder.txt")"
assert_absent "3: no package named in the silenced line (T15-38)" "paused: silenced by" "$(cat "$ROW_DIR/ring_silenced_recorder.txt")"
assert_contains "3: the ongoing notification says Paused while the other app records (read within 3 s)" "Paused" "$NT"
# How fast the take paused once Android reported the shell's client silenced: dumpsys audio's own event time for the
# silencing (device-local "MM-DD HH:MM:SS:mmm") against the ring line's wall stamp.
SILEV="$(grep -E "rec (update|start) riid:[0-9]+ uid:[0-9]+ session:$SESSION src:MIC silenced pack:app.tileshell" <<< "$MON" | head -1)"
PSL="$(grep -F '[recorder] paused: silenced' "$ROW_DIR/ring_silenced_recorder.txt" | head -1)"
SIL_MS="$(python3 - "$SILEV" "$(adb shell getprop persist.sys.timezone | tr -d '\r')" "$(adb shell date +%Y | tr -d '\r')" <<'PY'
import re, sys, datetime, zoneinfo
m = re.match(r"(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d):(\d{3}) ", sys.argv[1])
if not m: print(""); sys.exit()
mo, d, h, mi, s, ms = map(int, m.groups())
t = datetime.datetime(int(sys.argv[3]), mo, d, h, mi, s, ms * 1000, tzinfo=zoneinfo.ZoneInfo(sys.argv[2] or "UTC"))
print(int(t.timestamp() * 1000))
PY
)"
note "Android's silenced event: [${SILEV}] = $SIL_MS; the pause line: ${PSL#*] } (wall $(wall_of "$PSL"))"
assert_eq "3: the take paused within 200 ms of Android reporting the client silenced" yes "$(python3 -c 'import sys; a,b=sys.argv[1:3]; print("no (no event or no pause line)" if not a or not b else ("yes" if 0 <= int(b)-int(a) <= 200 else "no (%d ms)" % (int(b)-int(a))))' "$SIL_MS" "$(wall_of "$PSL")")"
sleep 2
assert_eq "3: the take's clock stands still while silenced" "$E_SIL" "$(rec_status elapsed_ms)"
# Open Camera stops and leaves; the take stays paused until the user resumes (Decisions: "until the user resumes or stops").
dump_ui "$ROW_DIR/c3_oc_rec2.xml"; gtap "$ROW_DIR/c3_oc_rec2.xml" $OC:id/take_photo; sleep 2.5
adb shell input keyevent KEYCODE_BACK; sleep 1.5
rec_open record; rdump "$ROW_DIR/c3_back.xml"; screencap "$ROW_DIR/c3_back.png"
assert_eq "3: back on the page the take is still paused (silenced)" silenced "$(rec_status paused)"
RSM="$(ring_mark)"
gtap "$ROW_DIR/c3_back.xml" rec_pause
assert_eq "3: Resume records again" recording "$(wait_phase recording 10)"
E3R="$(wait_elapsed $(( E_SIL + 2500 )) 20)"
rec_ring "$RSM" > "$ROW_DIR/ring_silenced_resumed_recorder.txt"
adb shell dumpsys audio | tr -d '\r' | grep -E "rec (start|update|stop) riid:[0-9]+ uid:[0-9]+ session:$SESSION " > "$ROW_DIR/c3_rec_events.txt"
note "Android's record events for the take's session: $(tr '\n' ';' < "$ROW_DIR/c3_rec_events.txt")"
note ":recorder ring after Resume: $(cut -c25- "$ROW_DIR/ring_silenced_resumed_recorder.txt" | tr '\n' ';')"
ADV3="$(python3 -c 'import sys; e=sys.argv[1]; print("yes" if e.isdigit() and int(e) >= int(sys.argv[2]) else "no (%s)" % e)' "$E3R" "$(( E_SIL + 2500 ))")"
assert_eq "3: after Resume the take's clock advances (2.5 s more take time within 20 s)" yes "$ADV3"
assert_absent "3: the Resume is not undone (no second paused: silenced after it)" "[recorder] paused: silenced" "$(cat "$ROW_DIR/ring_silenced_resumed_recorder.txt")"
assert_contains "3: [recorder] resumed at <the silenced pause's take time>" "[recorder] resumed at $E_SIL ms" "$(cat "$ROW_DIR/ring_silenced_resumed_recorder.txt")"
# Two more silence-and-Resume cycles in the same take. Run 3 found a Resume undone 19 ms after it by a second
# `paused: silenced` (Android's `rec start` for the session carried the stale silenced flag, cleared 2 ms later), and an
# exploration reproduced it in 3 of 5 cycles; one cycle alone misses it often, three rarely.
[ "$(rec_status phase)" = paused ] && { rdump "$ROW_DIR/c3_again.xml"; gtap "$ROW_DIR/c3_again.xml" rec_pause; wait_phase recording 10 >/dev/null; note "cycle 1's Resume was undone; resumed a second time to go on"; }
for k in 2 3; do
  adb shell am start -W -a android.media.action.VIDEO_CAMERA -n $OC/.MainActivity >/dev/null 2>&1; sleep 2.5
  dump_ui "$ROW_DIR/c3_oc$k.xml"
  grep -q 'android:id/alertTitle' "$ROW_DIR/c3_oc$k.xml" && { adb shell input keyevent KEYCODE_BACK; sleep 1.5; dump_ui "$ROW_DIR/c3_oc$k.xml"; }
  gtap "$ROW_DIR/c3_oc$k.xml" $OC:id/take_photo
  PSK="$(wait_status paused silenced 10)"; EK="$(rec_status elapsed_ms)"
  sleep 1.5; dump_ui "$ROW_DIR/c3_oc${k}b.xml"; gtap "$ROW_DIR/c3_oc${k}b.xml" $OC:id/take_photo; sleep 2.5
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  rec_open record; rdump "$ROW_DIR/c3_back$k.xml"
  RK="$(ring_mark)"
  gtap "$ROW_DIR/c3_back$k.xml" rec_pause
  EKR="$(wait_elapsed $(( EK + 2000 )) 15)"
  rec_ring "$RK" > "$ROW_DIR/ring_silenced_resumed${k}_recorder.txt"
  note "cycle $k: paused=$PSK at take time $EK; after Resume: $(cut -c25- "$ROW_DIR/ring_silenced_resumed${k}_recorder.txt" | tr '\n' ';') clock $EKR"
  assert_eq "3: cycle $k: Open Camera's capture silences the take again (paused=silenced)" silenced "$PSK"
  assert_absent "3: cycle $k: the Resume is not undone (no paused: silenced after it)" "[recorder] paused: silenced" "$(cat "$ROW_DIR/ring_silenced_resumed${k}_recorder.txt")"
  assert_eq "3: cycle $k: after Resume the take's clock advances (2 s within 15 s)" yes "$(python3 -c 'import sys; e=sys.argv[1]; print("yes" if e.isdigit() and int(e) >= int(sys.argv[2]) else "no (%s)" % e)' "$EKR" "$(( EK + 2000 ))")"
  [ "$(rec_status phase)" = paused ] && { rdump "$ROW_DIR/c3_again$k.xml"; gtap "$ROW_DIR/c3_again$k.xml" rec_pause; wait_phase recording 10 >/dev/null; note "cycle $k's Resume was undone; resumed a second time to go on"; }
done
adb shell dumpsys audio | tr -d '\r' | grep -E "rec (start|update|stop) riid:[0-9]+ uid:[0-9]+ session:$SESSION " > "$ROW_DIR/c3_rec_events.txt"
note "Android's record events for the take's session (all cycles): $(tr '\n' ';' < "$ROW_DIR/c3_rec_events.txt")"
rdump "$ROW_DIR/c3_rec2.xml"; gtap "$ROW_DIR/c3_rec2.xml" rec_button
wait_phase idle 15 >/dev/null; sleep 1.5
stop_tone
T3="$(new_own_id "$BEFORE")"; note "take 3 (the silenced take): id ${T3:-none} ($(ms_field "$(row_by_id "$T3")" _display_name))"
assert_ne "3: the take is saved" "" "$T3"
pull_take "$T3" "$ROW_DIR/silenced_take.m4a"
G3="$(gap_report "$ROW_DIR/silenced_take.m4a" "$E_SIL")"; printf '%s\n' "$G3" > "$ROW_DIR/silenced_gap.txt"
note "gap analysis at the silenced pause (take time $E_SIL ms): $(printf '%s' "$G3" | paste -sd' ')"
R3="$(file_rms "$ROW_DIR/silenced_take.m4a" 1.0 2.5)"; note "silenced take: RMS [1.0, 2.5) s = $R3 dBFS (before Open Camera came up)"
assert_eq "3: the tone was recorded before Open Camera came up (RMS 1.0-2.5 s > -40 dBFS)" yes "$(db_above "$R3" -40)"
if [ "$ADV3" = yes ]; then
  assert_eq "3: and after the Resume (envelope 0.5-1.5 s after it > -20 dBFS)" yes "$(db_above "$(kv LEVEL_AFTER "$G3")" -20)"
else
  note "the tone after the Resume: not measured — the take recorded nothing after it (see the clock assertion above)"
fi
# What the input delivered between Open Camera starting its capture and Android flagging the shell's client: the run
# below -40 dBFS that ends at the pause point (run 1: 1356 ms of digital zeros BEFORE the silenced event, which the take
# paused 14 ms after). No callback told the shell anything in that span, so it is recorded, not graded (T15-38's signal
# is the silenced flag).
PRE="$(python3 - "$ROW_DIR/silenced_take.m4a" "$E_SIL" <<'PY'
import subprocess, sys
import numpy as np
pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", sys.argv[1], "-f", "s16le", "-ac", "1", "-ar", "44100", "-"], capture_output=True).stdout
d = np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768.0
SR = 44100; n = len(d); X = np.fft.fft(d); h = np.zeros(n)
if n % 2 == 0: h[0] = h[n // 2] = 1; h[1:n // 2] = 2
else: h[0] = 1; h[1:(n + 1) // 2] = 2
e = np.abs(np.fft.ifft(X * h)); lo = e < 10 ** (-40 / 20)
p = int(float(sys.argv[2]) / 1000 * SR) + 2048
# the low run that touches [p - 0.1 s, p + 0.1 s], walked back to its start
i = next((k for k in range(max(0, p - int(0.1 * SR)), min(n, p + int(0.1 * SR))) if lo[k]), None)
if i is None: print("0 ms"); sys.exit()
j = i
while j < n and lo[j]: j += 1
while i > 0 and lo[i - 1]: i -= 1
print("%d ms (from %.3f s to %.3f s of the file)" % ((j - i) * 1000 // SR, i / SR, j / SR))
PY
)"
record "3: silence the input delivered before Android flagged the client (the run ending at the pause point)" "$PRE"
record "3: the page's notice when the user comes back (the notice went to no bound page while the other app was on top)" "rec_notice [$(xtext "$ROW_DIR/c3_back.xml" rec_notice)]"
# Restore: Open Camera's video(s) removed, its folder if the row made it, and Open Camera stopped if it was not running.
DCIM_OC_AFTER="$(adb shell ls /sdcard/DCIM/OpenCamera 2>/dev/null | tr -d '\r')"
NEWVID=""
for f in $DCIM_OC_AFTER; do case " $DCIM_OC_BEFORE " in *" $f "*) ;; *) NEWVID="$NEWVID $f"; adb shell rm -f "/sdcard/DCIM/OpenCamera/$f" ;; esac; done
[ "$DCIM_OC_EXISTED" = no ] && adb shell rmdir /sdcard/DCIM/OpenCamera 2>/dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
note "restore: Open Camera's videos removed:$NEWVID"
assert_ne "3: Open Camera had written a video (its capture was real)" "" "$NEWVID"
assert_eq "3: restore: DCIM/OpenCamera as it was" "$DCIM_OC_BEFORE" "$(adb shell ls /sdcard/DCIM/OpenCamera 2>/dev/null | tr -d '\r' | paste -sd' ')"
[ -z "$OC_WAS_RUNNING" ] && adb shell am force-stop $OC
assert_eq "3: restore: Open Camera's running state as before" "$OC_WAS_RUNNING" "$(pidof_dev $OC)"

# ================================================================= 4. rename to an existing name / refused characters / trim to zero
log "── 4. rename to an existing name, rename refused, trim to zero length"
note "names now: T1=$(ms_field "$(row_by_id "$T1")" _display_name) T2=$(ms_field "$(row_by_id "$T2")" _display_name) T3=$(ms_field "$(row_by_id "$T3")" _display_name)"
assert_eq "4: T1 is named Recording.m4a (the existing name)" "Recording.m4a" "$(ms_field "$(row_by_id "$T1")" _display_name)"
rec_open list; dump_ui "$ROW_DIR/c4_list.xml"
hold_node "$ROW_DIR/c4_list.xml" "rec_row:$T3"; sleep 1; dump_ui "$ROW_DIR/c4_menu.xml"
gtap "$ROW_DIR/c4_menu.xml" rec_menu:rename; sleep 1.5; dump_ui "$ROW_DIR/c4_rename.xml"
assert_eq "4: the rename dialog is up" yes "$(has_node "$ROW_DIR/c4_rename.xml" rec_rename_dialog)"
RNM="$(ring_mark)"
adb shell input text Recording; sleep 0.5
gtap "$ROW_DIR/c4_rename.xml" rec_rename_ok; sleep 2.5
ring_since "$RNM" > "$ROW_DIR/ring_rename_clash_launcher.txt"
dump_ui "$ROW_DIR/c4_list2.xml"
assert_eq "4: rename to an existing name: MediaStore appends (1)" "Recording (1).m4a" "$(ms_field "$(row_by_id "$T3")" _display_name)"
assert_eq "4: the other take keeps its name" "Recording.m4a" "$(ms_field "$(row_by_id "$T1")" _display_name)"
assert_eq "4: the list shows Recording (1) (rec_name)" "Recording (1)" "$(xtext "$ROW_DIR/c4_list2.xml" "rec_name:$T3")"
assert_contains "4: the launcher ring: rename -> Recording (1).m4a" "[recorder] rename $T3 -> Recording (1).m4a" "$(cat "$ROW_DIR/ring_rename_clash_launcher.txt")"
for BAD in 'Bad:Name' 'Bad*Name'; do
  hold_node "$ROW_DIR/c4_list2.xml" "rec_row:$T3"; sleep 1; dump_ui "$ROW_DIR/c4_menu2.xml"
  gtap "$ROW_DIR/c4_menu2.xml" rec_menu:rename; sleep 1.5; dump_ui "$ROW_DIR/c4_rename2.xml"
  RFM="$(ring_mark)"
  adb shell "input text '$BAD'"; sleep 0.5
  dump_ui "$ROW_DIR/c4_rename2b.xml"
  assert_eq "4: the field holds [$BAD]" "$BAD" "$(xtext "$ROW_DIR/c4_rename2b.xml" rec_rename_field)"
  gtap "$ROW_DIR/c4_rename2b.xml" rec_rename_ok; sleep 1.5
  dump_ui "$ROW_DIR/c4_refused.xml"; screencap "$ROW_DIR/c4_refused_${BAD//[^A-Za-z]/_}.png"
  ring_since "$RFM" > "$ROW_DIR/ring_rename_refused_launcher.txt"
  assert_eq "4: [$BAD] is refused with a notice (rec_rename_notice)" "A name can't contain any of these: \" * / : < > ? \\ |" "$(xtext "$ROW_DIR/c4_refused.xml" rec_rename_notice)"
  assert_eq "4: the dialog stays open on the refusal" yes "$(has_node "$ROW_DIR/c4_refused.xml" rec_rename_dialog)"
  assert_eq "4: MediaStore's name unchanged after [$BAD]" "Recording (1).m4a" "$(ms_field "$(row_by_id "$T3")" _display_name)"
  assert_contains "4: the launcher ring: rename refused" "[recorder] rename $T3 refused: A name can't contain" "$(cat "$ROW_DIR/ring_rename_refused_launcher.txt")"
  gtap "$ROW_DIR/c4_refused.xml" rec_rename_cancel; sleep 1
  dump_ui "$ROW_DIR/c4_list2.xml"
done
# trim to zero length (T2): the handles brought together by taps at their midpoint (a tap moves the nearer handle;
# a tie moves the start), until they stand within one pixel — the closest to zero the track allows.
handle_ms() { # dump.xml handle-id duration_ms
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(tag):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(tag), xml)
    return [int(v) for v in m.groups()] if m else None
s, h = b("rec_scrubber"), b(sys.argv[2])
if not s or not h: print(""); sys.exit()
r = (h[2] - h[0]) / 2.0
track_left, track_w = s[0] + r, (s[2] - s[0]) - 2 * r
print(int(round(((h[0] + h[2]) / 2.0 - track_left) / track_w * float(sys.argv[3]))))
PY
}
cx_of() { local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || return 1; set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
N_OWN="$(own_count)"; D_T2="$(ms_field "$(row_by_id "$T2")" duration)"
gtap "$ROW_DIR/c4_list2.xml" "rec_row:$T2"; sleep 1.5; rdump "$ROW_DIR/c4_play.xml"
gtap "$ROW_DIR/c4_play.xml" rec_bar:trim; sleep 1.5
for i in $(seq 1 14); do
  rdump "$ROW_DIR/c4_trim.xml"
  read -r SX Y <<< "$(cx_of "$ROW_DIR/c4_trim.xml" rec_trim_start)"; read -r EX _ <<< "$(cx_of "$ROW_DIR/c4_trim.xml" rec_trim_end)"
  [ -n "$SX" ] && [ -n "$EX" ] || break
  [ $(( EX - SX )) -le 1 ] && break
  adb shell input tap $(( (SX + EX) / 2 )) "$Y"; sleep 0.6
done
screencap "$ROW_DIR/c4_trim.png"
IN_MS="$(handle_ms "$ROW_DIR/c4_trim.xml" rec_trim_start "$D_T2")"; OUT_MS="$(handle_ms "$ROW_DIR/c4_trim.xml" rec_trim_end "$D_T2")"
note "trim handles: $IN_MS ms [$(xtext "$ROW_DIR/c4_trim.xml" rec_trim_in)] .. $OUT_MS ms [$(xtext "$ROW_DIR/c4_trim.xml" rec_trim_out)] on a $D_T2-ms take"
assert_eq "4: the trim handles stand together (kept part < 20 ms, zero at the track's resolution)" yes "$(python3 -c 'import sys; a,b=sys.argv[1:3]; print("yes" if a and b and 0 <= int(b)-int(a) < 20 else "no (%s..%s)" % (a,b))' "$IN_MS" "$OUT_MS")"
TZM="$(ring_mark)"
gtap "$ROW_DIR/c4_trim.xml" rec_trim_save; sleep 2
rdump "$ROW_DIR/c4_trim_saved.xml"; screencap "$ROW_DIR/c4_trim_saved.png"
ring_since "$TZM" > "$ROW_DIR/ring_trim_zero_launcher.txt"
assert_eq "4: trim to zero length is refused (rec_notice)" "Choose a longer part of the recording to keep." "$(xtext "$ROW_DIR/c4_trim_saved.xml" rec_notice)"
assert_contains "4: the launcher ring: trim refused" "[recorder] trim $T2 refused: Choose a longer part" "$(cat "$ROW_DIR/ring_trim_zero_launcher.txt")"
assert_eq "4: MediaStore unchanged: the take is still there, same duration" "$D_T2" "$(ms_field "$(row_by_id "$T2")" duration)"
assert_eq "4: MediaStore unchanged: the shell's take count" "$N_OWN" "$(own_count)"
assert_eq "4: no working file was made" "" "$(ms_rows "_display_name LIKE '%trimming%'")"
gtap "$ROW_DIR/c4_trim_saved.xml" rec_trim_cancel; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ================================================================= 5. share with no app able to receive audio
log "── 5. share with no app able to receive audio"
audio_receivers > "$ROW_DIR/share_receivers_before.txt"
: > "$ROW_DIR/share_receivers_states.txt"
while IFS= read -r c <&3; do echo "$(full_comp "$c") $(comp_state "$c")" >> "$ROW_DIR/share_receivers_states.txt"; done 3< "$ROW_DIR/share_receivers_before.txt"
record "5: the ACTION_SEND audio/mp4 receivers disabled for the test (component, its state before)" "$(paste -sd';' "$ROW_DIR/share_receivers_states.txt")"
assert_ne "5: there are receivers to disable (the empty state is made, not found)" "" "$(cat "$ROW_DIR/share_receivers_before.txt")"
# `pm disable-user` on a COMPONENT leaves it enabled on this image (it prints "new state: enabled"), and the shell user may
# not change another package's component state at all ("Shell cannot change component state"); so, as root, `pm disable`.
root_on
while read -r c s <&3; do adb shell pm disable --user 0 "$c" >> "$LOG" 2>&1; done 3< "$ROW_DIR/share_receivers_states.txt"
root_off
RECV_DISABLED=yes
sleep 1
assert_eq "5: no activity resolves ACTION_SEND audio/mp4 now" "" "$(audio_receivers)"
rec_open list; dump_ui "$ROW_DIR/c5_list.xml"
hold_node "$ROW_DIR/c5_list.xml" "rec_row:$T1"; sleep 1; dump_ui "$ROW_DIR/c5_menu.xml"
SHM="$(ring_mark)"
gtap "$ROW_DIR/c5_menu.xml" rec_menu:share; sleep 3
dump_ui "$ROW_DIR/c5_chooser.xml"; screencap "$ROW_DIR/c5_chooser.png"
ring_since "$SHM" > "$ROW_DIR/ring_share_launcher.txt"
assert_contains "5: the launcher ring: share sent ACTION_SEND audio/mp4 with the take's URI" "[recorder] share $T1 type=audio/mp4 uri=content://media/external/audio/media/$T1 (own)" "$(cat "$ROW_DIR/ring_share_launcher.txt")"
assert_contains "5: Android's chooser is in front" "com.android.intentresolver" "$(resumed)"
assert_eq "5: its empty state shows (android:id/resolver_empty_state)" yes "$(has_node "$ROW_DIR/c5_chooser.xml" android:id/resolver_empty_state)"
assert_contains "5: reading No apps can perform this action." "text=\"No apps can perform this action.\"" "$(cat "$ROW_DIR/c5_chooser.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_contains "5: Back returns to Voice Recorder" "recorder.RecorderActivity" "$(resumed)"
restore_receivers
sleep 1
audio_receivers > "$ROW_DIR/share_receivers_after.txt"
assert_eq "5: restore: the same receivers resolve again" "$(paste -sd' ' "$ROW_DIR/share_receivers_before.txt")" "$(paste -sd' ' "$ROW_DIR/share_receivers_after.txt")"
: > "$ROW_DIR/share_receivers_states_after.txt"
while IFS= read -r c <&3; do echo "$(full_comp "$c") $(comp_state "$c")" >> "$ROW_DIR/share_receivers_states_after.txt"; done 3< "$ROW_DIR/share_receivers_before.txt"
assert_eq "5: restore: each receiver's component state as before" "$(paste -sd';' "$ROW_DIR/share_receivers_states.txt")" "$(paste -sd';' "$ROW_DIR/share_receivers_states_after.txt")"

# ================================================================= 6. the :recorder process killed between takes
log "── 6. the :recorder process low-memory-killed between takes"
rec_open list; dump_ui "$ROW_DIR/c6_list.xml"
assert_eq "6: no take is running (phase idle)" idle "$(rec_status phase)"
RP1="$(pidof_dev app.tileshell:recorder)"; LP6="$(pidof_dev app.tileshell)"
assert_ne "6: the page's :recorder process is alive between takes" "" "$RP1"
own_rows | sort > "$ROW_DIR/c6_rows_before.txt"
: > "$ROW_DIR/c6_md5_before.txt"
while IFS= read -r r <&3; do p="$(ms_field "$r" _data)"; echo "$p $(ms_md5 "$p")" >> "$ROW_DIR/c6_md5_before.txt"; done 3< "$ROW_DIR/c6_rows_before.txt"
rec_ring_save; ring_save launcher
root_on
KM="$(ring_mark)"
adb shell kill -9 "$RP1"
root_off
sleep 3
RP2="$(pidof_dev app.tileshell:recorder)"; note ":recorder pid $RP1 killed; now [$RP2]; launcher pid $LP6 -> $(pidof_dev app.tileshell)"
assert_ne "6: the page's binding brought up a new :recorder process" "" "$RP2"
assert_ne "6: not the killed one" "$RP1" "$RP2"
assert_eq "6: the launcher (the page) was not restarted" "$LP6" "$(pidof_dev app.tileshell)"
rec_ring "$KM" > "$ROW_DIR/ring_kill_recorder.txt"
ring_since "$KM" > "$ROW_DIR/ring_kill_launcher.txt"
assert_contains "6: the new :recorder process's ring starts it" "[recorder] service created pid=$RP2" "$(cat "$ROW_DIR/ring_kill_recorder.txt")"
assert_absent "6: nothing recovered (no [recorder] recovered)" "[recorder] recovered" "$(cat "$ROW_DIR/ring_kill_recorder.txt")"
assert_absent "6: no recovery run at all (no [recorder] recovery:)" "[recorder] recovery:" "$(cat "$ROW_DIR/ring_kill_recorder.txt")"
assert_contains "6: the page saw the process go and re-bound (launcher ring)" "[recorder] :recorder process gone; awaiting re-bind" "$(cat "$ROW_DIR/ring_kill_launcher.txt")"
own_rows | sort > "$ROW_DIR/c6_rows_after.txt"
: > "$ROW_DIR/c6_md5_after.txt"
while IFS= read -r r <&3; do p="$(ms_field "$r" _data)"; echo "$p $(ms_md5 "$p")" >> "$ROW_DIR/c6_md5_after.txt"; done 3< "$ROW_DIR/c6_rows_after.txt"
assert_eq "6: nothing lost: MediaStore's rows of the shell's takes unchanged" "$(cat "$ROW_DIR/c6_rows_before.txt")" "$(cat "$ROW_DIR/c6_rows_after.txt")"
assert_eq "6: nothing lost: every take's file byte-identical (md5)" "$(cat "$ROW_DIR/c6_md5_before.txt")" "$(cat "$ROW_DIR/c6_md5_after.txt")"
assert_eq "6: nothing left to recover (files/recorder empty)" "" "$(adb shell "run-as app.tileshell ls files/recorder" < /dev/null 2>/dev/null | tr -d '\r')"
dump_ui "$ROW_DIR/c6_list2.xml"
assert_eq "6: the list shows the same rows" "$(ids_with_prefix "$ROW_DIR/c6_list.xml" 'rec_row:')" "$(ids_with_prefix "$ROW_DIR/c6_list2.xml" 'rec_row:')"
# the next take records normally
TK="$(ring_mark)"
TONE_PID=""
T4="$(make_take 3)"
rec_ring "$TK" > "$ROW_DIR/ring_kill_next_take_recorder.txt"
assert_ne "6: the next take is saved" "" "$T4"
assert_contains "6: the next take's stop line in the new process's ring" "[recorder] stop Recording (3) ms=" "$(cat "$ROW_DIR/ring_kill_next_take_recorder.txt")"
assert_eq "6: its name is the next free one" "Recording (3).m4a" "$(ms_field "$(row_by_id "$T4")" _display_name)"
assert_eq "6: it was recorded by the new process (same pid)" "$RP2" "$(pidof_dev app.tileshell:recorder)"

# ================================================================= 7. a recording deleted outside the app
log "── 7. a recording deleted outside the app (adb shell rm + rescan)"
rec_open list; dump_ui "$ROW_DIR/c7_list.xml"
assert_eq "7: T4 is listed before the rm" yes "$(has_node "$ROW_DIR/c7_list.xml" "rec_row:$T4")"
N7="$(own_count)"; LP7="$(pidof_dev app.tileshell)"; P4="$(path_by_id "$T4")"
DM="$(ring_mark)"
adb shell rm -f "'$P4'"
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
sleep 2.5
dump_ui "$ROW_DIR/c7_list2.xml"; screencap "$ROW_DIR/c7_list2.png"
ring_since "$DM" > "$ROW_DIR/ring_delete_outside_launcher.txt"
assert_eq "7: MediaStore dropped the row" "" "$(row_by_id "$T4")"
assert_eq "7: its row left the list" no "$(has_node "$ROW_DIR/c7_list2.xml" "rec_row:$T4")"
assert_contains "7: through the ContentObserver (list refreshed (media change))" "[recorder] list refreshed (media change)" "$(cat "$ROW_DIR/ring_delete_outside_launcher.txt")"
assert_contains "7: the refresh counts one fewer" "[recorder] list: $(( N7 - 1 )) recordings (0 by other apps)" "$(cat "$ROW_DIR/ring_delete_outside_launcher.txt")"
assert_eq "7: no restart: the launcher pid unchanged" "$LP7" "$(pidof_dev app.tileshell)"
assert_absent "7: no restart: the page was not re-created" "[recorder] RecorderActivity created" "$(cat "$ROW_DIR/ring_delete_outside_launcher.txt")"
assert_contains "7: the page is still the one in front" "recorder.RecorderActivity" "$(resumed)"

# ================================================================= 8. other apps' recordings
log "── 8. other apps' recordings"
record "8: an existing row that proves either case" "none: E14 lists other.m4a in Recordings/ read-only and E30 proves a foreign file JOINING through the ContentObserver; neither deletes one nor lists one outside Recordings/"
# 8a. a foreign recording deleted by its own app while listed
FM0="$(ring_mark)"
push_fixture_recordings
sleep 2
OTHER="$(id_by_name other.m4a)"; OROW="$(row_by_id "$OTHER")"; note "other.m4a: $OROW"
assert_eq "8a: other.m4a is IS_RECORDING" 1 "$(ms_field "$OROW" is_recording)"
OOWN="$(ms_field "$OROW" owner_package_name)"
record "8a: other.m4a's owner (the app that made it, adb push through the shell)" "$OOWN"
assert_ne "8a: it is another app's (owner not app.tileshell)" "app.tileshell" "$OOWN"
rec_open list; dump_ui "$ROW_DIR/c8a_list.xml"
assert_eq "8a: other.m4a is listed" yes "$(has_node "$ROW_DIR/c8a_list.xml" "rec_row:$OTHER")"
N8="$(( $(own_count) + 1 ))"; LP8="$(pidof_dev app.tileshell)"
FM="$(ring_mark)"
# Its own app deletes it: `content delete` runs as com.android.shell, the owner MediaStore records — a delete through
# MediaStore by the owning package (no rm, no rescan).
adb shell content delete --uri "$MEDIA_URI/$OTHER"; FDRC=$?
assert_eq "8a: the owner's MediaStore delete rc" 0 "$FDRC"
sleep 2.5
dump_ui "$ROW_DIR/c8a_list2.xml"; screencap "$ROW_DIR/c8a_list2.png"
ring_since "$FM" > "$ROW_DIR/ring_foreign_delete_launcher.txt"
assert_eq "8a: MediaStore dropped the row" "" "$(row_by_id "$OTHER")"
assert_contains "8a: and the file with it" "No such file" "$(adb shell ls /sdcard/Recordings/other.m4a 2>&1 | tr -d '\r')"
assert_eq "8a: its row left the list" no "$(has_node "$ROW_DIR/c8a_list2.xml" "rec_row:$OTHER")"
assert_contains "8a: through the ContentObserver (list refreshed (media change))" "[recorder] list refreshed (media change)" "$(cat "$ROW_DIR/ring_foreign_delete_launcher.txt")"
assert_contains "8a: the refresh counts it gone" "[recorder] list: $(( N8 - 1 )) recordings (0 by other apps)" "$(cat "$ROW_DIR/ring_foreign_delete_launcher.txt")"
assert_eq "8a: no restart: the launcher pid unchanged" "$LP8" "$(pidof_dev app.tileshell)"
remove_fixture_recordings
assert_eq "8a: restore: the fixtures are gone from MediaStore" "" "$(id_by_name other.m4a)$(id_by_name song.m4a)"
# 8b. a foreign IS_RECORDING file outside Recordings/
DL_REC_EXISTED="$(adb shell "[ -d /sdcard/Download/Recordings ] && echo yes || echo no" | tr -d '\r')"
FOREIGN=edgeR_foreign.m4a
OM8="$(ring_mark)"
adb shell mkdir -p /sdcard/Download/Recordings
adb push "$FIXTURE_RECORDINGS_DIR/other.m4a" "/sdcard/Download/Recordings/$FOREIGN" >/dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
sleep 2
FID="$(id_by_name "$FOREIGN")"; FROW="$(row_by_id "$FID")"; note "$FOREIGN: $FROW"
assert_eq "8b: MediaStore marks it is_recording 1" 1 "$(ms_field "$FROW" is_recording)"
assert_eq "8b: outside Recordings/ (relative_path Download/Recordings/)" "Download/Recordings/" "$(ms_field "$FROW" relative_path)"
assert_ne "8b: another app's (owner not app.tileshell)" "app.tileshell" "$(ms_field "$FROW" owner_package_name)"
rec_open list; dump_ui "$ROW_DIR/c8b_list.xml"; screencap "$ROW_DIR/c8b_list.png"
ring_since "$OM8" > "$ROW_DIR/ring_foreign_outside_launcher.txt"
assert_eq "8b: it is listed (rec_row)" yes "$(has_node "$ROW_DIR/c8b_list.xml" "rec_row:$FID")"
assert_eq "8b: under its name" "edgeR_foreign" "$(xtext "$ROW_DIR/c8b_list.xml" "rec_name:$FID")"
assert_contains "8b: the launcher ring counts it as another app's" "[recorder] list: $(( $(own_count) + 1 )) recordings (1 by other apps)" "$(cat "$ROW_DIR/ring_foreign_outside_launcher.txt")"
hold_node "$ROW_DIR/c8b_list.xml" "rec_row:$FID"; sleep 1; dump_ui "$ROW_DIR/c8b_menu.xml"
note "hold menu: $(ids_with_prefix "$ROW_DIR/c8b_menu.xml" 'rec_menu' | paste -sd' ')"
assert_eq "8b: read-only: its hold menu offers Share" yes "$(has_node "$ROW_DIR/c8b_menu.xml" rec_menu:share)"
assert_eq "8b: read-only: no Delete" no "$(has_node "$ROW_DIR/c8b_menu.xml" rec_menu:delete)"
assert_eq "8b: read-only: no Rename" no "$(has_node "$ROW_DIR/c8b_menu.xml" rec_menu:rename)"
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/c8b_list2.xml"
gtap "$ROW_DIR/c8b_list2.xml" "rec_row:$FID"
PLAYER="$(wait_media_player 3)"; note "player: ${PLAYER:-none}"
assert_ne "8b: a row tap plays it (a started player of the shell's)" "" "$PLAYER"
rdump "$ROW_DIR/c8b_play.xml"; screencap "$ROW_DIR/c8b_play.png"
note "app bar: $(ids_with_prefix "$ROW_DIR/c8b_play.xml" 'rec_bar' | paste -sd' ')"
assert_eq "8b: its app bar holds Share" yes "$(has_node "$ROW_DIR/c8b_play.xml" rec_bar:share)"
for m in trim delete rename; do assert_eq "8b: its app bar has no $m" no "$(has_node "$ROW_DIR/c8b_play.xml" "rec_bar:$m")"; done
assert_absent "8b: no consent dialog (no providers.media activity)" "providers.media" "$(adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity|ActivityRecord')"
sleep 3.5
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell rm -f "/sdcard/Download/Recordings/$FOREIGN"
[ "$DL_REC_EXISTED" = no ] && adb shell rmdir /sdcard/Download/Recordings
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
assert_eq "8b: restore: the foreign file is gone from MediaStore" "" "$(id_by_name "$FOREIGN")"
assert_eq "8b: restore: Download/Recordings/ as it was" "$DL_REC_EXISTED" "$(adb shell "[ -d /sdcard/Download/Recordings ] && echo yes || echo no" | tr -d '\r')"

# ================================================================= restore
log "── restore"
rec_ring_save
for id in $T1 $T2 $T3; do [ -n "$id" ] && app_delete_take "$id"; done
assert_eq "restore: every take deleted through the app" 0 "$(own_count)"
[ "$(own_count)" != 0 ] && note "restore: sweeping leftovers -> $(purge_own_takes) remain"
assert_eq "restore: no IS_RECORDING file of another app's left" "" "$(recording_rows)"
assert_contains "restore: RECORD_AUDIO granted" "RECORD_AUDIO: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r')"
note "recordings.json keeps the metadata of the take deleted outside the app (id $T4; no product path prunes it): $(adb shell "run-as app.tileshell cat files/recordings.json" < /dev/null 2>/dev/null | tr -d '\r' | cut -c1-300)"
adb shell input keyevent KEYCODE_HOME; sleep 1
stop_tone
row_end
