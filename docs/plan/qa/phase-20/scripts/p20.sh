#!/usr/bin/env bash
# Phase 20's driver floor for the ONE round of testing (rows A1–A7). Sourced by every a<n>.sh AFTER lib.sh (phase 03's
# floor, symlinked here). It adds: phase 18's floor (baseline_start, absent_in, c6, rings_save, layout_restore — with
# BASELINE re-pointed at THIS phase's baseline), phase 01's music_fixtures, the builders' navigation helpers
# (build-notes-integration/integ.sh + nav.sh, which drove every feature once), the fixture prefs' SAFE ORDER (INDEX
# Change Log 2026-10-07 14:40), and a per-letter tally so each driver prints one PASS / FAIL line per lettered pass
# condition and a final `A<n>: <p> passed, <f> failed`.
#
# Nothing here touches the host's audio or a microphone. Tess is typed. The media stream is set to volume 0 on the DEVICE
# (music_lib.sh's reason: the AudioFlinger track, the session and the position are unchanged by it).
#
# A re-run of a row: P20_ROW=A3-rerun1 bash a3.sh  (evidence goes to qa/phase-20/A3-rerun1/, the first run is kept).
export ANDROID_SERIAL=emulator-5554

P20S="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA20="$(cd "$P20S/.." && pwd)"
QAROOT="$(cd "$QA20/.." && pwd)"
. "$QAROOT/phase-18/scripts/p18.sh"
BASELINE="$QA20/baseline_layout.json"
STAMP_FILES="$P20S/p20.sh"
RINGS=(launcher)
. "$QAROOT/phase-01/scripts/music_lib.sh"
set +e +u; set +o pipefail

FIX="$P20S/fixtures.sh"
RADIO_PORT="${P20_RADIO_PORT:-8092}"; CATALOGUE_PORT="${P20_CATALOGUE_PORT:-8093}"
J1=11111111-1111-4111-8111-111111111111; J2=22222222-2222-4222-8222-222222222222
N1=33333333-3333-4333-8333-333333333333; F1=44444444-4444-4444-8444-444444444444
MUSIC_ACTIVITY=app.tileshell/.music.MusicActivity
QATUNES=app.tileshell.testclient.qatunes
QATUNES_APK="$REPO/testapps/qa-tunes/build/outputs/apk/debug/qa-tunes-debug.apk"

# ---------------------------------------------------------------- the per-letter tally
declare -A LP LF LFN
LETTER=setup
eval "$(declare -f _verdict | sed '1s/^_verdict/_verdict_p03/')"
_verdict() { # PASS|FAIL name detail   — phase 03's line, plus the letter it belongs to
  _verdict_p03 "$1" "($LETTER) $2" "$3"
  if [ "$1" = PASS ]; then LP[$LETTER]=$(( ${LP[$LETTER]:-0} + 1 )); else LF[$LETTER]=$(( ${LF[$LETTER]:-0} + 1 )); LFN[$LETTER]+="$2 :: $3 || "; fi
}
letter() { LETTER="$1"; }
assert_yes() { assert_eq "$1" "yes" "$2"; }

# p20_begin <row> <description>: the row's folder (P20_ROW overrides the name for a re-run), the stamp, the wake.
p20_begin() {
  ROW_NAME="$1"
  row_begin "${P20_ROW:-$1}" "$2"
  letter setup
  assert_contains "the device holds the build under test (apk_matches)" "yes" "$(apk_matches)"
  record "APK stamp" "$(apk_matches) sha256 $(sha256sum "$APK" | cut -c1-16) $(stat -c%s "$APK") B; HEAD $(git -C "$REPO" rev-parse --short=8 HEAD)"
  record "run started" "$(date)"
  adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1
}

# p20_end <letters…>: one line per letter, the final count, exit code 0 only when every letter (and the set-up and
# restore checks) passed.
p20_end() {
  local l p=0 f=0 extra_fail=0
  rings_save
  adb shell dumpsys media_session > "$ROW_DIR/session-end.txt" 2>&1
  # Counted in THIS shell (a `{ … } | tee` would count in a subshell and lose p and f: the first A1 run exited 0 with
  # three letters failed — a driver fault, fixed before the re-run), written to a file, then shown and logged.
  local sum="$ROW_DIR/.summary.txt"
  echo "------------------------------------------------------------------------------" > "$sum"
  for l in setup restore; do
    [ -n "${LP[$l]:-}${LF[$l]:-}" ] || continue
    if [ "${LF[$l]:-0}" -gt 0 ]; then extra_fail=1; echo "FAIL  $ROW_NAME ($l): ${LF[$l]} of $(( ${LP[$l]:-0} + ${LF[$l]} )) checks failed: ${LFN[$l]}" >> "$sum"
    else echo "ok    $ROW_NAME ($l): ${LP[$l]} checks" >> "$sum"; fi
  done
  for l in "$@"; do
    if [ "${LF[$l]:-0}" -gt 0 ]; then f=$((f + 1)); echo "FAIL  $ROW_NAME ($l): ${LF[$l]} of $(( ${LP[$l]:-0} + ${LF[$l]} )) checks failed: ${LFN[$l]}" >> "$sum"
    elif [ "${LP[$l]:-0}" -eq 0 ]; then f=$((f + 1)); echo "FAIL  $ROW_NAME ($l): not reached (no check ran)" >> "$sum"
    else p=$((p + 1)); echo "PASS  $ROW_NAME ($l): ${LP[$l]} checks" >> "$sum"; fi
  done
  echo "$ROW_NAME: $p passed, $f failed$([ "$extra_fail" = 1 ] && echo ' (and the set-up / restore checks FAILED)')" >> "$sum"
  echo "run ended $(date)" >> "$sum"
  tee -a "$LOG" < "$sum"
  row_end >/dev/null
  [ "$f" -eq 0 ] && [ "$extra_fail" -eq 0 ]
}

# ---------------------------------------------------------------- dumps, taps, readers (the builders' helpers)
d() { dump_ui "$ROW_DIR/$1.xml" >/dev/null 2>&1; }
shot() { adb exec-out screencap -p > "$ROW_DIR/$1.png"; }
texts() { # dump name -> "resource-id | text | content-desc | bounds" for every node that has one of them
  python3 - "$ROW_DIR/$1.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1])
except Exception: sys.exit(0)
for n in root.iter('node'):
    t = n.get('text'); r = n.get('resource-id'); c = n.get('content-desc')
    if t or r or c: print("%-44s | %s | %s | %s" % (r, t, c, n.get('bounds')))
PY
}
nt() { # dump name, resource-id -> its text, XML-unescaped ("" when the node is absent)
  python3 - "$ROW_DIR/$1.xml" "$2" <<'PY'
import sys
import xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1])
except Exception: print(""); sys.exit(0)
for n in root.iter('node'):
    if n.get('resource-id') == sys.argv[2]: print(n.get('text', '')); break
else: print("")
PY
}
has() { has_node "$ROW_DIR/$1.xml" "$2"; }                 # dump name, resource-id -> yes / no
ids() { # dump name, id prefix -> the resource-ids starting with it, in document order
  python3 - "$ROW_DIR/$1.xml" "$2" <<'PY'
import sys
import xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1])
except Exception: sys.exit(0)
for n in root.iter('node'):
    r = n.get('resource-id') or ''
    if r.startswith(sys.argv[2]): print(r)
PY
}
tap() { tap_node "$ROW_DIR/$1.xml" "$2"; }                 # dump name, resource-id
hold() { # dump name, resource-id — a 900 ms press in its middle
  local b; b="$(bounds "$ROW_DIR/$1.xml" "$2")"; [ -n "$b" ] || { echo "hold: no node $2" >&2; return 2; }
  # shellcheck disable=SC2086
  set -- $b; local x=$(( ($1 + $3) / 2 )) y=$(( ($2 + $4) / 2 ))
  adb shell input swipe $x $y $x $y 900 </dev/null; sleep 1.2
}
mring() { ring_since "$1" launcher | grep -F '[music]'; }  # the [music] lines since a mark
stripped() { sed 's/^.*wall=[0-9]* //'; }                   # a ring line without its stamp

# The shell's media session: its state word, its metadata line, and both as one comparable string.
session_raw() { adb shell dumpsys media_session </dev/null | tr -d '\r' | awk '/package=app\.tileshell/ { f = 1 } f && /state=PlaybackState|metadata: |description=/ { print } f && /^$/ { exit }'; }
# (`state=PlaybackState {state=PLAYING(3), …`: the word wanted is the one followed by "(" — the first A2 run read the
# "P" of PlaybackState as well and failed two checks on its own reader; a driver fault, that row re-run.)
sstate() { session_raw | grep -o 'state=[A-Z_]*(' | head -1 | sed 's/state=//; s/($//'; }
smeta() { session_raw | grep -m1 -o 'description=.*' | sed 's/^description=//'; }
sboth() { echo "$(sstate) | $(smeta)"; }
session_save() { adb shell dumpsys media_session > "$ROW_DIR/$1.txt" 2>&1 </dev/null; }

# music17.sh's awk: how many AudioFlinger tracks of the shell's process are ACTIVE (and their sessions).
ours() { local pid; pid="$(adb shell pidof app.tileshell </dev/null | tr -d '\r')"
  adb shell dumpsys media.audio_flinger 2>/dev/null </dev/null | awk -v pid="$pid/" '
    { for (i = 1; i <= 3; i++) if ($i == "yes" && $(i+1) == pid && $(i+5) == "A") { n++; s = s " " $(i+3) } }
    END { print n + 0 s }'; }
wakeful() { adb shell dumpsys power </dev/null | grep -m1 'mWakefulness=' | tr -d '\r ' | sed 's/mWakefulness=//'; }
keyguard() { adb shell dumpsys window </dev/null | tr -d '\r' | grep -o 'isKeyguardShowing=[a-z]*' | head -1; }
top() { adb shell dumpsys activity activities </dev/null | tr -d '\r' | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -o '[A-Za-z0-9_.]*/[A-Za-z0-9_.]*' | head -1; }

# ---------------------------------------------------------------- navigation (build-notes-integration/nav.sh)
to_pivot() { # name — Music on that pivot, from wherever; the dump is nav.xml and <name>.xml
  adb shell am start -W -n $MUSIC_ACTIVITY >/dev/null 2>&1 </dev/null; sleep 2.5; d nav
  local i
  for i in 1 2 3 4 5; do [ "$(has nav music_pivot)" = yes ] && break
    adb shell input keyevent KEYCODE_BACK </dev/null; sleep 1.5; d nav
    [ "$(has nav music_root)" = yes ] || [ "$(has nav nowplaying_root)" = yes ] || { adb shell am start -W -n $MUSIC_ACTIVITY >/dev/null 2>&1 </dev/null; sleep 2.5; d nav; }
  done
  for i in 1 2 3 4 5 6; do [ "$(has nav "music_list:$1")" = yes ] && break
    if [ "$(has nav "music_pivot_header:$1")" = yes ]; then tap nav "music_pivot_header:$1"
    elif [ "$1" = radio ]; then adb shell input swipe 900 1200 150 1200 200 </dev/null
    else adb shell input swipe 150 1200 900 1200 200 </dev/null; fi
    sleep 1.8; d nav
  done
  cp "$ROW_DIR/nav.xml" "$ROW_DIR/$1.xml"
  [ "$(has nav "music_list:$1")" = yes ]
}
to_radio() { to_pivot radio; }
# A row of the radio list that may sit under the fold: swipe the list until the node's middle is above the nav bar.
radio_reach() { # dump name, resource-id
  local i b
  for i in 1 2 3 4; do
    d "$1"; b="$(bounds "$ROW_DIR/$1.xml" "$2")"
    # shellcheck disable=SC2086
    if [ -n "$b" ]; then set -- $1 $2 $b; [ $(( ($4 + $6) / 2 )) -lt 2150 ] && [ $(( ($4 + $6) / 2 )) -gt 340 ] && return 0; fi
    adb shell input swipe 540 1700 540 1100 400 </dev/null; sleep 1.2
  done
  return 1
}

# ---------------------------------------------------------------- fixtures and the debug prefs
fixtures_up() { # the two Python fixtures, their request logs in the ROW's folder (a fresh log per row)
  bash "$FIX" down all > "$ROW_DIR/fixtures-down-before.out" 2>&1
  mkdir -p "$ROW_DIR/fixture-logs"
  bash "$FIX" up all "$ROW_DIR/fixture-logs" > "$ROW_DIR/fixtures-up.out" 2>&1; echo $? > "$ROW_DIR/fixtures-up.rc"
  assert_eq "fixtures.sh up all (rc)" "0" "$(cat "$ROW_DIR/fixtures-up.rc")"
  RLOG="$ROW_DIR/fixture-logs/radio.log"; CLOG="$ROW_DIR/fixture-logs/catalogue.log"
}
fixtures_down() { bash "$FIX" down all > "$ROW_DIR/fixtures-down.out" 2>&1; }
prefs_count() { adb shell run-as app.tileshell cat shared_prefs/start_theme.xml 2>/dev/null </dev/null | tr -d '\r' | python3 -c '
import sys, re
want = dict(l.strip().split("=", 1) for l in open(sys.argv[1]) if "=" in l)
got = dict(re.findall(r"name=\"(qa_[a-z_]*)\">([^<]*)<", sys.stdin.read()))
print(sum(1 for k, v in want.items() if got.get(k) == v))' <(bash "$FIX" prefs); }
airplane() { adb shell settings get global airplane_mode_on </dev/null | tr -d '\r'; }
# Can the EMULATOR reach a fixture? (toybox nc; /__qa/ready is never logged, so it is not a request of the shell's)
emu_reaches() { # port
  adb shell "printf 'GET /__qa/ready HTTP/1.0\r\nHost: 10.0.2.2:$1\r\n\r\n' | toybox timeout 5 nc -w 4 10.0.2.2 $1 | head -c 40" 2>/dev/null </dev/null | tr -d '\r' | head -1
}
wait_network() { # up to 40 s for the emulator to reach the radio fixture again
  local i; for i in $(seq 1 20); do case "$(emu_reaches "$RADIO_PORT")" in *200*) return 0 ;; esac; sleep 2; done; return 1
}
# THE SAFE ORDER (INDEX Change Log 2026-10-07 14:40): airplane mode ON → Settings in front → force-stop → write the three
# prefs → force-stop AGAIN and read the file back → airplane mode OFF. Only then may Music be opened.
qa_prefs_safe() {
  adb shell cmd connectivity airplane-mode enable </dev/null; sleep 1
  assert_eq "prefs: airplane mode is ON while they are written" "1" "$(airplane)"
  adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1 </dev/null; sleep 0.5
  adb shell am force-stop app.tileshell </dev/null; sleep 0.3
  adb shell run-as app.tileshell cat shared_prefs/start_theme.xml > "$ROW_DIR/.prefs-0.xml" 2>/dev/null </dev/null
  cp "$ROW_DIR/.prefs-0.xml" "$ROW_DIR/.prefs-cur.xml"
  local k v
  while IFS='=' read -r k v; do
    python3 "$QAROOT/phase-01/scripts/prefs_edit.py" "$ROW_DIR/.prefs-cur.xml" "$k" string "$v" > "$ROW_DIR/.prefs-next.xml"; mv "$ROW_DIR/.prefs-next.xml" "$ROW_DIR/.prefs-cur.xml"
  done < <(bash "$FIX" prefs)
  adb shell "run-as app.tileshell sh -c 'mkdir -p shared_prefs && cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/.prefs-cur.xml"
  adb shell am force-stop app.tileshell </dev/null; sleep 2
  local n; n="$(prefs_count)"
  assert_eq "prefs: the three qa_* prefs are in the file after the second stop (read back)" "3" "$n"
  [ "$n" = 3 ] || { echo "PREFS NOT WRITTEN — airplane mode is left ON" >&2; return 1; }
  adb shell cmd connectivity airplane-mode disable </dev/null; sleep 6
  assert_eq "prefs: airplane mode is OFF again" "0" "$(airplane)"
  wait_network; assert_eq "prefs: the emulator reaches the radio fixture again" "0" "$?"
}
# Rows A2–A7: the prefs are already in the file (A1 wrote them; a force-stop keeps them). Proven before Music opens;
# a row that does not find all three stops there, with the network off.
prefs_guard() {
  local n; n="$(prefs_count)"
  assert_eq "the three qa_* prefs point at the fixtures (read from the file before Music is opened)" "3" "$n"
  if [ "$n" != 3 ]; then
    adb shell cmd connectivity airplane-mode enable </dev/null
    echo "STOP: the fixture prefs are not in place; airplane mode set ON so nothing reaches a live server" | tee -a "$LOG"
    p20_end "$@"; exit 1
  fi
}

# Requests of a fixture's .jsonl log: `flog <log.jsonl> <python expression over r>` prints the matching targets.
# (The expression is a literal written in the row's own driver, never data read from the device or a log.)
flog() { python3 - "$1" "$2" <<'PY'
import json, sys
try: rows = [json.loads(l) for l in open(sys.argv[1]) if l.strip()]
except FileNotFoundError: rows = []
for r in rows:
    h = {k.lower(): v for k, v in r.get("headers", [])}
    if eval(sys.argv[2]): print(r["n"], r["method"], r["target"], "| UA:", h.get("user-agent", "(none)"))
PY
}

# Play a station from the radio pivot (a favourite's row when it is one) and wait until the session reads PLAYING.
play_station() { # uuid [wait seconds=20]
  local id="radio_row:$1" i
  to_radio || return 1
  [ "$(has radio "radio_fav:$1")" = yes ] && id="radio_fav:$1"
  radio_reach radio "$id" || return 1
  tap radio "$id"
  for i in $(seq 1 "${2:-20}"); do sleep 1; [ "$(sstate)" = PLAYING ] && return 0; done
  return 1
}

# Tess, typed: the assist key (phase 03 E10's fallback when the session did not come up), then type_request.
open_tess() { # dump name
  cortana_assist; sleep 5; d "$1"
  if [ "$(has "$1" cortana_session)" != yes ]; then adb shell cmd voiceinteraction show >/dev/null 2>&1 </dev/null; sleep 5; d "$1"; fi
  [ "$(has "$1" cortana_session)" = yes ]
}
# Every reply since a mark, oldest first; and the LAST one (over the keyguard Tess first says "I didn't catch that.").
replies_from() { ring_since "$1" launcher | grep -F '[speech]' | grep -oE 'text="[^"]*"' | sed 's/^text="//; s/"$//'; }
last_reply() { replies_from "$1" | tail -1; }
