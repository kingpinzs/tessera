#!/usr/bin/env bash
# Phase 15's Voice Recorder row helpers (E14–E20, E24, E30, E14b), sourced AFTER lib.sh and p15.sh:
#   . "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
# Everything here drives the real app on the emulator named by ANDROID_SERIAL; nothing is simulated. A take's
# audio is judged from the file the AVD RECORDED (pulled with adb, analysed on the host with ffprobe / ffmpeg).
# What the AVD PLAYS is judged through `audio.sh record`, which on this host reads digital silence for every
# emulator (EasyEffects processes the emulators' output streams; the owner left it so) — those assertions stay,
# fail, and say why with `note`; they are never replaced by another measurement.

REC_ACT="app.tileshell/.recorder.RecorderActivity"
REC_SVC="app.tileshell/.recorder.RecorderService"
AUDIO="$QROOT/phase-03/scripts/audio.sh"
MEDIA_URI="content://media/external/audio/media"
TAKE_PROJ="_id:_display_name:duration:is_recording:relative_path:owner_package_name:_data"

# ---------------------------------------------------------------- host audio
# The 440 Hz test tone at -12 dBFS RMS (peak 0.3548), 16 kHz mono — the sink's own rate, so nothing resamples it
# on the way to the AVD's microphone. Generated once per length into TMPDIR (the private driver lock lives there too).
tone() { # seconds -> path
  local dir="${TMPDIR:-/tmp}/p15-tones" f
  mkdir -p "$dir"
  f="$dir/tone440_${1}s.wav"
  [ -s "$f" ] || ffmpeg -loglevel error -y -f lavfi -i "aevalsrc=0.35481*sin(2*PI*440*t):s=16000:c=mono:d=$1" \
    -ac 1 -ar 16000 -sample_fmt s16 "$f"
  echo "$f"
}

# A tone looping into this emulator's microphone sink until tone_loop_stop: a background subshell whose pid the
# caller records. Stopped by that pid only (never pkill); the paplay it is inside ends with it.
tone_loop_start() { # wav -> pid
  ( trap 'kill $pp 2>/dev/null; exit 0' TERM INT
    while :; do "$AUDIO" say "$1" & pp=$!; wait $pp || break; done ) >/dev/null 2>&1 &
  echo $!
}
tone_loop_stop() { # pid
  [ -n "${1:-}" ] || return 0
  kill "$1" 2>/dev/null
  wait "$1" 2>/dev/null
}

# The precondition of every microphone step (T15-30): this emulator's capture stream is on $AUDIO_SINK.monitor and
# its output stream exists. `audio.sh setup` runs only when the check fails AND AUDIO_SINK names this emulator's own
# sink (never the shared default `vmic`, which would re-route the desktop's microphone). Asserted: rc 0.
audio_route() { # label
  local out rc
  out="$("$AUDIO" check 2>&1)"; rc=$?
  if [ "$rc" -ne 0 ] && [ -n "${AUDIO_SINK:-}" ] && [ "$AUDIO_SINK" != vmic ]; then
    note "$1: audio.sh check failed ($out); running audio.sh setup once for $AUDIO_SINK"
    "$AUDIO" setup >> "$LOG" 2>&1
    out="$("$AUDIO" check 2>&1)"; rc=$?
  fi
  note "$1: audio.sh check: $out (rc=$rc); default source $(pactl get-default-source)"
  assert_eq "$1: the audio route is up (audio.sh check rc)" 0 "$rc"
  AUDIO_OK=$([ "$rc" -eq 0 ] && echo yes || echo no)
}
AUDIO_OK=no

# ---------------------------------------------------------------- the pages
rec_open() { # record|list
  adb shell am start -W -n "$REC_ACT" --es page "$1" >/dev/null 2>&1
  sleep 2.5
}

# The record state and the playback page never idle (the timer, the rings, the position): dump them through the
# gesture driver (C-10). The list and the dialogs idle: plain dump_ui (RV13).
rdump() { gdump "$1"; }

# The `:recorder` service's own dump: its status block (phase, take, elapsed_ms, paused, markers, frames, file) above
# its Diagnostics ring (T15-17). Answers while the page is bound or a take runs.
rec_dump() { adb shell dumpsys activity service "$REC_SVC" 2>/dev/null | tr -d '\r'; }
rec_status() { # key
  rec_dump | sed -n "s/^ *$1=//p" | head -1
}
rec_ring() { # mark -> the :recorder ring since mark
  ring_since "$1" "$REC_SVC"
}
# T15-48: a recorder row saves its :recorder slice before it leaves the page (row_end saves it again; harmless).
rec_ring_save() { ring_save "$REC_SVC"; }

# Poll the take's own clock: returns once elapsed_ms >= ms (or after timeout seconds, printing what it saw).
wait_elapsed() { # ms [timeout_s]
  local want="$1" limit=$(( ${2:-30} * 10 )) i=0 e
  while [ "$i" -lt "$limit" ]; do
    e="$(rec_status elapsed_ms)"
    if [ -n "$e" ] && [ "$e" -ge "$want" ] 2>/dev/null; then echo "$e"; return 0; fi
    sleep 0.1; i=$((i + 1))
  done
  echo "${e:-none}"; return 1
}
wait_phase() { # phase [timeout_s]
  local want="$1" limit=$(( ${2:-15} * 4 )) i=0 p
  while [ "$i" -lt "$limit" ]; do
    p="$(rec_status phase)"
    [ "$p" = "$want" ] && { echo "$p"; return 0; }
    sleep 0.25; i=$((i + 1))
  done
  echo "${p:-none}"; return 1
}

# The record / stop disc, or the list's docked button: whichever the current screen holds. Dumps first (the button
# moves between the two states), so a stop tap after a take started lands on the disc.
tap_rec_button() { # out.xml
  rdump "$1" || return 1
  gtap "$1" rec_button
}

# A hold on a node: Edit.HOLD_MS (783 ms) with no movement.
hold_node() { # dump.xml resource-id
  local b
  b="$(bounds "$1" "$2")"
  [ -n "$b" ] || { note "hold_node: no $2 in $1"; return 1; }
  # shellcheck disable=SC2086
  set -- $b
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
}

ids_with_prefix() { # dump.xml prefix -> the resource-ids starting with prefix, one per line
  grep -o "resource-id=\"$2[^\"]*\"" "$1" | cut -d'"' -f2 | sort -u
}

# "m:ss" / "h:mm:ss" -> seconds (the row's rec_duration / rec_position / rec_total texts).
clock_to_s() {
  python3 -c '
import sys
t = sys.argv[1].strip().split(":")
if not t or not all(p.isdigit() for p in t): print(""); sys.exit()
s = 0
for p in t: s = s * 60 + int(p)
print(s)' "$1"
}

# ---------------------------------------------------------------- MediaStore
# The audio rows matching a where clause, one per line, as `content query` prints them.
ms_rows() { # where
  adb shell content query --uri "$MEDIA_URI" --projection "$TAKE_PROJ" --where "\"$1\"" 2>/dev/null | tr -d '\r' | grep '^Row:'
}
ms_field() { # row-line field
  printf '%s\n' "$1" | grep -oE "(^|[ ,])$2=[^,]*" | head -1 | sed "s/^[ ,]*$2=//"
}
own_rows() { ms_rows "is_recording=1 AND owner_package_name='app.tileshell'"; }
own_ids() { own_rows | grep -oE '_id=[0-9]+' | cut -d= -f2; }
own_count() { own_rows | grep -c '^Row:'; }
recording_rows() { ms_rows "is_recording=1"; }
id_by_name() { # _display_name -> _id
  ms_rows "_display_name='$1'" | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2
}
row_by_id() { ms_rows "_id=$1"; }
path_by_id() { ms_field "$(row_by_id "$1")" _data; }

# The newest of the shell's own takes not in the given list (the one a step just made).
new_own_id() { # "id id ..." (before)
  local id
  for id in $(own_ids | sort -n -r); do
    case " $1 " in *" $id "*) ;; *) echo "$id"; return 0 ;; esac
  done
  return 1
}

pull_take() { # id out
  local p; p="$(path_by_id "$1")"
  [ -n "$p" ] || { note "pull_take: no _data for $1"; return 1; }
  adb pull "$p" "$2" >/dev/null 2>&1
}

# Restore: every take the shell owns removed (the file, then a rescan drops the row), and a rescan afterwards.
# Rows delete through the APP where the row is about deleting; this is the sweep at the end.
purge_own_takes() {
  local list="$ROW_DIR/.purge_paths.txt" p n
  # The list is read on fd 3: adb inside the loop would swallow stdin.
  own_rows | while IFS= read -r r; do ms_field "$r" _data; done > "$list"
  while IFS= read -r p <&3; do
    [ -n "$p" ] && adb shell rm -f "'$p'"
  done 3< "$list"
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  sleep 1
  n="$(own_count)"
  note "purge_own_takes: $n of the shell's takes remain in MediaStore"
  echo "$n"
}

# ---------------------------------------------------------------- host analysis of a pulled file
file_duration() { # file -> seconds
  ffprobe -v error -show_entries format=duration -of csv=p=0 "$1" 2>/dev/null
}
file_stream() { # file -> "codec,sample_rate,channels"
  ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels -of csv=p=0 "$1" 2>/dev/null
}
# Overall RMS in dBFS over the whole file, or over [start, end) seconds. Digital silence prints -inf.
file_rms() { # file [start end]
  local af="astats=measure_perchannel=none:measure_overall=RMS_level"
  [ $# -ge 3 ] && af="atrim=start=$2:end=$3,$af"
  ffmpeg -v info -i "$1" -af "$af" -f null - 2>&1 | grep -E "RMS level dB" | tail -1 | sed 's/.*RMS level dB: *//'
}
# A number comparison for dB values ("-inf" is below everything): prints yes/no.
db_above() { # value threshold
  python3 -c 'import sys; v=sys.argv[1].strip(); print("yes" if v not in ("", "-inf", "-nan") and float(v) > float(sys.argv[2]) else "no")' "$1" "$2" 2>/dev/null || echo no
}
db_below() { # value threshold
  python3 -c 'import sys; v=sys.argv[1].strip(); print("yes" if v == "-inf" or (v not in ("", "-nan") and float(v) < float(sys.argv[2])) else "no")' "$1" "$2" 2>/dev/null || echo no
}

# ---------------------------------------------------------------- the audio system
# A started player of the shell's on USAGE_MEDIA (dumpsys audio's players block), or empty.
media_player_started() {
  local pid; pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  adb shell dumpsys audio 2>/dev/null | tr -d '\r' | grep -E "AudioPlaybackConfiguration .*u/pid:[0-9]+/$pid state:started .*usage=USAGE_MEDIA" | head -1
}
# The same, polled: a 3-s file's AudioTrack is started for under three seconds, and one read can land before the
# player is ready. Prints the first started line seen within the timeout (seconds), else empty.
wait_media_player() { # [timeout_s]
  local limit=$(( ${1:-3} * 10 )) i=0 line
  while [ "$i" -lt "$limit" ]; do
    line="$(media_player_started)"
    [ -n "$line" ] && { echo "$line"; return 0; }
    sleep 0.1; i=$((i + 1))
  done
  return 1
}
# The shell's Media3 session with the given id: its PlaybackState name (e0.sh's reader), or empty.
recorder_session_state() {
  adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
hit = False
for l in sys.stdin:
    if re.match(r"^\s+\S+ \S+/\S+/\d+ \(userId=\d+\)", l):
        hit = (" app.tileshell/" in l) and ("androidx.media3.session.id.recorder" in l)
    elif hit:
        m = re.search(r"state=PlaybackState \{state=([A-Z_]+)", l)
        if m: print(m.group(1)); break'
}

# Delete one of the shell's takes THROUGH THE APP: the list's hold menu → Delete → the confirmation (U5).
app_delete_take() { # id
  local d="$ROW_DIR/.delete_$1.xml"
  rec_open list
  dump_ui "$d" || return 1
  [ "$(has_node "$d" "rec_row:$1")" = yes ] || { note "app_delete_take: no rec_row:$1 on the list"; return 1; }
  hold_node "$d" "rec_row:$1"; sleep 1
  dump_ui "$d" || return 1
  gtap "$d" "rec_menu:delete" || return 1
  sleep 1
  dump_ui "$d" || return 1
  gtap "$d" rec_delete_confirm || return 1
  sleep 2
}

# ---------------------------------------------------------------- the notification shade
# Tap an action button of the shell's ongoing notification from the shade (E15: Stop). Expands the shade, dumps it,
# expands the notification itself when its actions are folded, taps the button by its own text, collapses. Prints
# yes when the button was found and tapped, else no (with the dump kept as <out>).
shade_tap_action() { # text out.xml
  local text="$1" out="$2" b found=no i
  # The dump is the gesture driver's (every window, C-10): a plain `uiautomator dump` returned the shell's own window
  # under the open shade while the recorder page was alive behind Start (E15 runs 1 and 2). SystemUI shows an action's
  # label in capitals, so the text is matched without regard to case.
  for i in 1 2 3; do
    adb shell cmd statusbar expand-notifications; sleep 2
    gdump "$out" || true
    grep -q 'package="com.android.systemui"' "$out" && break
    note "shade_tap_action: dump $i holds no SystemUI window ($(grep -o 'package="[^"]*"' "$out" | sort -u | paste -sd' ')); expanding again"
  done
  screencap "${out%.xml}.png"
  node_bounds_by_text() { # dump.xml text(case-insensitive) -> bounds
    python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
want = sys.argv[2].lower()
for n in re.finditer(r'<node[^>]*>', xml):
    s = n.group(0)
    m = re.search(r' text="([^"]*)"', s)
    if m and m.group(1).strip().lower() == want:
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        if b: print(" ".join(b.groups())); break
PY
  }
  b="$(node_bounds_by_text "$out" "$text")"
  if [ -z "$b" ]; then
    # The chevron that expands the notification (SystemUI's expander), when its actions are folded.
    local e
    e="$(python3 - "$out" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for n in re.finditer(r'<node[^>]*>', xml):
    s = n.group(0)
    if 'content-desc="Expand"' in s or 'resource-id="com.android.systemui:id/expand_button"' in s:
        m = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        if m: print(" ".join(m.groups())); break
PY
)"
    if [ -n "$e" ]; then
      # shellcheck disable=SC2086
      set -- $e
      adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5
      gdump "$out" || true
      b="$(node_bounds_by_text "$out" "$text")"
    fi
  fi
  if [ -n "$b" ]; then
    # shellcheck disable=SC2086
    set -- $b
    note "shade_tap_action: tapping [$text] at $(( ($1 + $3) / 2 )),$(( ($2 + $4) / 2 ))"
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    found=yes
  fi
  sleep 1.5
  adb shell cmd statusbar collapse; sleep 1
  echo "$found"
}

# The shell's notification records (dumpsys notification --noredact), for the ongoing take's (id 1507).
shell_notification_block() { # id
  adb shell dumpsys notification --noredact 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
want = sys.argv[1]; out = []; inside = False
for l in sys.stdin:
    if l.startswith("    NotificationRecord("):
        inside = ("pkg=app.tileshell" in l) and (" id=%s " % want in l)
    if inside: out.append(l.rstrip("\n"))
print("\n".join(out))' "$1"
}

# The block of `dumpsys activity services app.tileshell` describing one service (its ServiceRecord and the lines under it).
service_block() { # short class, e.g. .recorder.RecorderService
  adb shell dumpsys activity services app.tileshell 2>/dev/null | tr -d '\r' | python3 -c '
import sys
want = sys.argv[1]; out = []; inside = False
for l in sys.stdin:
    if l.lstrip().startswith("* ServiceRecord{"):
        inside = want in l
    if inside: out.append(l.rstrip("\n"))
print("\n".join(out))' "$1"
}

# ---------------------------------------------------------------- a take of a given length
# A take of `secs` seconds with the tone looping into the microphone, from whatever recorder page is showing (the record
# state's disc or the list's docked button); stopped on the take's own clock. Prints its MediaStore id (E16, E20, E30).
make_take() { # secs
  local before pid e id
  before="$(own_ids | tr '\n' ' ')"
  dump_ui "$ROW_DIR/.mk_before.xml"
  gtap "$ROW_DIR/.mk_before.xml" rec_button
  pid="$(tone_loop_start "$(tone 10)")"
  sleep 1; rdump "$ROW_DIR/.mk_recording.xml"
  e="$(wait_elapsed $(( $1 * 1000 - 250 )) $(( $1 + 15 )))"
  gtap "$ROW_DIR/.mk_recording.xml" rec_button
  tone_loop_stop "$pid"
  wait_phase idle 15 >/dev/null; sleep 1.5
  id="$(new_own_id "$before")"
  note "make_take ${1}s: stop tapped at elapsed_ms=$e -> id ${id:-none} ($(ms_field "$(row_by_id "$id")" _display_name), $(ms_field "$(row_by_id "$id")" duration) ms)"
  echo "$id"
}
