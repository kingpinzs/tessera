#!/usr/bin/env bash
# Phase 11 build task 7 (C-20, T11-21): the ring helpers in phase 03's lib.sh — ring_mark, ring_since, reply_since,
# ring_save, row_begin's ROW_MARK and row_end's RINGS — proved WITHOUT a device.
#
# adb is replaced by a shell function (and a trap binary first on PATH, which fails loudly if anything bypasses the
# function) that returns canned dumps: the ring lines are in Diagnostics.dump's own format
# ("<yyyy-MM-dd HH:mm:ss.SSS> wall=<ms> [<tag>] <message>", app/src/main/kotlin/app/tileshell/diag/Diagnostics.kt:40),
# wrapped as `dumpsys activity service` prints a client dump (SERVICE line, "Client:", four-space indent — the shape of
# qa/phase-01/E06/diagnostics.txt), with CRLF line ends as adb's pty gives them. The device lock is stubbed too, so a
# QA driver running on a real device at the same time is never disturbed.
#
#   bash docs/plan/qa/phase-11/scripts/test_ring_helpers.sh      exit 0 = every check passed
set -uo pipefail
SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=../../phase-03/scripts/lib.sh
. "$SCRIPTS/../../phase-03/scripts/lib.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
printf '#!/bin/sh\necho "REAL ADB CALLED: $*" >> "%s/real-adb.txt"\nexit 99\n' "$TMP" > "$TMP/bin/adb"
chmod +x "$TMP/bin/adb"
PATH="$TMP/bin:$PATH"
CALLS="$TMP/adb-calls.txt"
: > "$CALLS"
take_device_lock() { echo "take_device_lock (stubbed)" >> "$CALLS"; }

crlf() { sed 's/$/\r/'; }

MARK=1790000000500
# The launcher ring (diag()). Index 0 and 1 are before MARK (1 is MARK - 1); 2..7 are at or after it (2 is MARK).
L=(
  "    2026-09-23 18:00:00.000 wall=1790000000000 [layout] restore: 14 tiles"
  "    2026-09-23 18:00:00.499 wall=1790000000499 [speech] speak[u1] voice=0 text=\"Before the mark\""
  "    2026-09-23 18:00:00.500 wall=1790000000500 [quick] shortcuts for app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity/0: 5 (4 shown: qa_one,qa_two,qa_three,qa_four)"
  "    2026-09-23 18:00:00.510 wall=1790000000510 [quick] burst on tile:app:app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity:0: 4 satellites"
  "    2026-09-23 18:00:00.700 wall=1790000000700 [reminders] fired r1 (due) text=\"Not a reply\""
  "    2026-09-23 18:00:00.800 wall=1790000000800 [speech] speak[u2] voice=0 text=\"First after the mark\""
  "    2026-09-23 18:00:00.900 wall=1790000000900 [speech] speak[u3] voice=0 text=\"Second after the mark\""
  "    2026-09-23 18:00:01.000 wall=1790000001000 [quick] burst closed: tap elsewhere"
)
LAUNCHER_DUMP="$({
  echo "SERVICE app.tileshell/.feeds.TileNotificationListener 51af630 pid=6539 user=0"
  echo "  Client:"
  echo "    listener connected=true"
  echo "    badges={}"
  echo "    tileshell diagnostics: ${#L[@]} entries"
  printf '%s\n' "${L[@]}"
} | crlf)"
L_EXPECTED="$(printf '%s\n' "${L[@]:2}")"

# The :speech process's own ring (speech_dump()). Its last line is synthetic: a text="..." in THIS ring, after every
# mark below, which reply_since must never return (replies are read from the launcher's ring).
S=(
  "    2026-09-23 18:00:00.300 wall=1790000000300 [speech] asr: loaded"
  "    2026-09-23 18:00:00.600 wall=1790000000600 [speech] asr: final \"what time is it\""
  "    2026-09-23 18:00:00.960 wall=1790000000960 [speech] synthetic text=\"speech-process line, not a reply\""
)
SPEECH_DUMP="$({
  echo "SERVICE app.tileshell/.cortana.speech.SpeechService 1c2d3e4 pid=6601 user=0"
  echo "  Client:"
  echo "    tileshell speech process pid=6601"
  echo "    breadcrumb: none"
  echo "    --- status ---"
  echo "    asr=loaded tts=loaded"
  echo "    --- diagnostics ---"
  echo "    tileshell diagnostics: ${#S[@]} entries"
  printf '%s\n' "${S[@]}"
} | crlf)"
S_EXPECTED="$(printf '%s\n' "${S[@]:1}")"

# Any other service component (here the keyboard's process).
K=(
  "    2026-09-23 18:00:00.100 wall=1790000000100 [ime] shown"
  "    2026-09-23 18:00:00.550 wall=1790000000550 [ime] hidden"
)
IME_DUMP="$({
  echo "SERVICE app.tileshell/.ime.KeyboardService 7a8b9c0 pid=6702 user=0"
  echo "  Client:"
  echo "    tileshell diagnostics: ${#K[@]} entries"
  printf '%s\n' "${K[@]}"
} | crlf)"
K_EXPECTED="${K[1]}"

NOW="$MARK"   # what `adb shell date +%s%3N` answers
adb() {
  echo "$*" >> "$CALLS"
  case "$*" in
    "shell date +%s%3N") printf '%s\r\n' "$NOW" ;;
    "shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener")
      # How many lines the row log had when the launcher ring was read (row_end must read it before its summary).
      if [ -n "$LOG" ] && [ -f "$LOG" ]; then wc -l < "$LOG" >> "$TMP/log-lines-at-ring-read.txt"; fi
      printf '%s\n' "$LAUNCHER_DUMP" ;;
    "shell dumpsys activity service app.tileshell/.cortana.speech.SpeechService") printf '%s\n' "$SPEECH_DUMP" ;;
    "shell dumpsys activity service app.tileshell/.ime.KeyboardService") printf '%s\n' "$IME_DUMP" ;;
    "shell dumpsys power") printf '  mWakefulness=Awake\r\n' ;;
    *) ;;
  esac
}

T_PASS=0
T_FAIL=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then
    printf 'PASS  %s\n' "$1"
    T_PASS=$((T_PASS + 1))
  else
    printf 'FAIL  %s\n      expected [%s]\n      got      [%s]\n' "$1" "$2" "$3"
    T_FAIL=$((T_FAIL + 1))
  fi
}
yes_no() { if "$@"; then echo yes; else echo no; fi; }
has_cr() { [[ "$1" == *$'\r'* ]]; }

echo "== ring_mark"
check "ring_mark prints the device clock in ms, CR stripped" "$MARK" "$(ring_mark)"

echo "== ring_since (launcher, the default)"
OUT="$(ring_since "$MARK")"
check "ring_since MARK = exactly the six lines stamped >= MARK, in ring order" "$L_EXPECTED" "$OUT"
check "ring_since MARK launcher = the same slice" "$L_EXPECTED" "$(ring_since "$MARK" launcher)"
check "no CR left in the slice" no "$(yes_no has_cr "$OUT")"
check "the line stamped MARK itself is in (>=)" yes "$(yes_no grep -qF 'wall=1790000000500 ' <<< "$OUT")"
check "the line stamped MARK - 1 is out" no "$(yes_no grep -qF 'wall=1790000000499 ' <<< "$OUT")"
check "dump header lines (no wall=) are out" no "$(yes_no grep -qE 'listener connected|tileshell diagnostics:|SERVICE' <<< "$OUT")"
check "a mark after every line gives an empty slice" "" "$(ring_since 1790000002000)"
check "ring_since read the listener's dump" "shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener" \
  "$(grep -F 'TileNotificationListener' "$CALLS" | tail -1)"
ERR="$(ring_since "" 2>&1 >/dev/null)"; RC=$?
check "an empty mark is refused (non-zero)" 1 "$RC"
check "an empty mark says why on stderr" yes "$(yes_no grep -q 'ring_since: mark must be' <<< "$ERR")"
check "an empty mark prints no ring line on stdout" "" "$(ring_since "" 2>/dev/null)"

echo "== ring_since speech"
check "ring_since MARK speech = the :speech ring's lines >= MARK" "$S_EXPECTED" "$(ring_since "$MARK" speech)"
check "ring_since speech read SpeechService's dump" "shell dumpsys activity service app.tileshell/.cortana.speech.SpeechService" \
  "$(grep -F 'SpeechService' "$CALLS" | tail -1)"

echo "== ring_since <service component>"
check "ring_since MARK app.tileshell/.ime.KeyboardService = that dump's lines >= MARK" "$K_EXPECTED" \
  "$(ring_since "$MARK" app.tileshell/.ime.KeyboardService)"
check "ring_since <component> ran dumpsys activity service <component>" "shell dumpsys activity service app.tileshell/.ime.KeyboardService" \
  "$(grep -F 'KeyboardService' "$CALLS" | tail -1)"

echo "== reply_since"
check "the first [speech] text= after MARK (skips the earlier reply and the [reminders] text=)" "First after the mark" "$(reply_since "$MARK")"
check "a later mark gives the next reply" "Second after the mark" "$(reply_since 1790000000850)"
check "no reply after the mark gives empty (the :speech ring's text= is not a reply)" "" "$(reply_since 1790000000950)"

echo "== ring_save"
ROW_DIR="$TMP/rowA"
mkdir -p "$ROW_DIR"
ROW_MARK="$MARK"
ring_save
check "ring_save writes ring-launcher.txt = ring_since ROW_MARK" "$L_EXPECTED" "$(cat "$ROW_DIR/ring-launcher.txt")"
ring_save launcher
check "a second ring_save appends (the slice twice; duplicates are harmless)" "$(printf '%s\n%s' "$L_EXPECTED" "$L_EXPECTED")" \
  "$(cat "$ROW_DIR/ring-launcher.txt")"
ring_save speech
check "ring_save speech writes ring-speech.txt" "$S_EXPECTED" "$(cat "$ROW_DIR/ring-speech.txt")"
ring_save app.tileshell/.ime.KeyboardService
check "ring_save <component> writes ring-<sanitised component>.txt" "$K_EXPECTED" \
  "$(cat "$ROW_DIR/ring-app.tileshell_.ime.KeyboardService.txt" 2>/dev/null)"
check "the row dir holds exactly those three files" "ring-app.tileshell_.ime.KeyboardService.txt ring-launcher.txt ring-speech.txt" \
  "$(cd "$ROW_DIR" && ls | sort | tr '\n' ' ' | sed 's/ $//')"
ROW_DIR="$TMP/rowB"
mkdir -p "$ROW_DIR"
ROW_MARK=""
ring_save 2>/dev/null; RC=$?
check "ring_save with no ROW_MARK refuses (non-zero)" 1 "$RC"
check "... and writes nothing" "" "$(ls "$ROW_DIR")"

echo "== row_begin sets ROW_MARK after the lock and the wake"
QA="$TMP/qa"
: > "$CALLS"
NOW=1790000003000
row_begin T11RB "ring helpers under test" > "$TMP/row_begin.out" 2> "$TMP/row_begin.err"
check "ROW_MARK = the device clock when the row began" 1790000003000 "$ROW_MARK"
check "ROW_DIR is the row's own dir" "$QA/T11RB" "$ROW_DIR"
LOCK_AT="$(grep -n 'take_device_lock' "$CALLS" | head -1 | cut -d: -f1)"
WAKE_AT="$(grep -n 'shell input keyevent KEYCODE_WAKEUP' "$CALLS" | tail -1 | cut -d: -f1)"
DATE_AT="$(grep -n 'shell date +%s%3N' "$CALLS" | tail -1 | cut -d: -f1)"
check "the lock, then the wake, then the mark (adb call order)" yes \
  "$(yes_no test -n "$LOCK_AT" -a -n "$WAKE_AT" -a -n "$DATE_AT" -a "${LOCK_AT:-0}" -lt "${WAKE_AT:-0}" -a "${WAKE_AT:-0}" -lt "${DATE_AT:-0}")"

echo "== row_end saves RINGS (default launcher) first, keeps its pass/fail rule"
NOW="$MARK"
row_begin T11RE1 "one passing assertion, RINGS unset" > /dev/null 2>&1
unset RINGS
: > "$TMP/log-lines-at-ring-read.txt"
assert_eq "a passing assertion" x x > /dev/null
row_end > "$TMP/row_end1.out"; RC=$?
check "row_end with one PASS returns 0" 0 "$RC"
check "row_end saved ring-launcher.txt (RINGS default) = ring_since ROW_MARK" "$L_EXPECTED" "$(cat "$ROW_DIR/ring-launcher.txt")"
check "and no other ring" "ring-launcher.txt" "$(cd "$ROW_DIR" && ls ring-* | tr '\n' ' ' | sed 's/ $//')"
SUMMARY_AT="$(grep -n '^T11RE1: 1 passed, 0 failed$' "$LOG" | cut -d: -f1)"
READ_AT="$(tail -1 "$TMP/log-lines-at-ring-read.txt")"
check "the ring was read before the summary was written" yes \
  "$(yes_no test -n "$SUMMARY_AT" -a -n "$READ_AT" -a "${READ_AT:-0}" -lt "${SUMMARY_AT:-0}")"

row_begin T11RE2 "one failing assertion, RINGS=launcher speech" > /dev/null 2>&1
RINGS="launcher speech"
assert_eq "a failing assertion" x y > /dev/null
row_end > "$TMP/row_end2.out"; RC=$?
check "row_end with a FAIL returns non-zero" 1 "$RC"
check "row_end saved every ring in RINGS" "ring-launcher.txt ring-speech.txt" "$(cd "$ROW_DIR" && ls ring-* | tr '\n' ' ' | sed 's/ $//')"
check "ring-speech.txt = ring_since ROW_MARK speech" "$S_EXPECTED" "$(cat "$ROW_DIR/ring-speech.txt")"
check "the summary line is unchanged" "T11RE2: 0 passed, 1 failed" "$(grep '^T11RE2:' "$LOG")"

row_begin T11RE3 "no assertions" > /dev/null 2>&1
unset RINGS
row_end > "$TMP/row_end3.out"; RC=$?
check "row_end with zero assertions still fails" 1 "$RC"
check "... and says so in the log" "FAIL  T11RE3 made no assertions at all" "$(grep 'made no assertions' "$LOG")"
check "... after saving the ring" "$L_EXPECTED" "$(cat "$ROW_DIR/ring-launcher.txt")"

echo "== no real adb"
check "nothing reached a real adb binary" no "$(yes_no test -e "$TMP/real-adb.txt")"

echo "------------------------------------------------------------------------------"
echo "test_ring_helpers: $T_PASS passed, $T_FAIL failed"
[ "$T_FAIL" -eq 0 ]
