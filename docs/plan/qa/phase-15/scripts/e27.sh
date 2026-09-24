#!/usr/bin/env bash
# E27 — App Shortcuts (build task 9; phase 11 Q1's standing rule, C-8). Development half (runs on the branch too):
# `dumpsys shortcut` lists the ten ids with ranks 0..n-1 per activity, each with its glyph; each shortcut's own intent
# (action, component and `page` extra, read from that dump) opens its page with that page's tag selected — force-stop and
# Home between launches (the promoted recent app is in memory only, qa/phase-01/scripts/recent0922.sh:19-21);
# `converter` opens the last-used category after a conversion in Length, and Volume the first time after `pm clear` ->
# provision.sh. The burst half (hold each pinned tile -> quick_sat_label:0..n in rank order, the activity-keyed
# `[quick] shortcuts for` line, tap each satellite) needs phase 11's burst and runs on the rebased main (T15-21); on a
# build without it each burst step is a FAIL marked NOT RUN with that reason.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
. "$QROOT/phase-02/scripts/layout.sh"
row_begin E27 "the three apps' App Shortcuts: declared, ranked, iconed, and each opens its page"

CLOCK="app.tileshell/app.tileshell.clock.ClockActivity"
CALC="app.tileshell/app.tileshell.calculator.CalculatorActivity"
REC="app.tileshell/app.tileshell.recorder.RecorderActivity"
declare -A WANT=( [$CLOCK]="alarm timer stopwatch world_clock" [$CALC]="standard scientific programmer converter" [$REC]="new_recording recordings" )
declare -A PAGE_TAG=( [alarm]="clock_pivot:alarm" [timer]="clock_pivot:timer" [stopwatch]="clock_pivot:stopwatch" [world_clock]="clock_pivot:world_clock"
  [standard]="calc_mode:standard" [scientific]="calc_mode:scientific" [programmer]="calc_mode:programmer" [converter]="calc_mode:converter"
  [new_recording]="rec_page:record" [recordings]="rec_page:list" )

layout_restore "$P15/baseline_layout.json"
assert_eq "baseline layout restored" 0 $?

# ---------------------------------------------------------------- dumpsys shortcut
adb shell dumpsys shortcut | tr -d '\r' > "$ROW_DIR/shortcut.txt"
# app.tileshell's block only: id, activity, rank, icon resource and the page extra, one line per shortcut.
python3 - "$ROW_DIR/shortcut.txt" > "$ROW_DIR/shortcuts.tsv" <<'PY'
import re, sys
text = open(sys.argv[1]).read()
m = re.search(r"\n\s+Package: app\.tileshell\s.*?(?=\n\s+Package: |\Z)", text, re.S)
block = m.group(0) if m else ""
for s in re.split(r"\n\s+ShortcutInfo \{", block)[1:]:
    g = lambda p: (re.search(p, s) or [None, ""])[1]
    print("\t".join([g(r"^id=([^,]+)"), g(r"activity=ComponentInfo\{([^}]+)\}"), g(r"rank=(\d+)"),
                     g(r"iconRes=\d+\[([^\]]*)\]"), g(r"act=(\S+)"), g(r"cmp=(\S+) "), g(r"\{page=([^}]+)\}")]))
PY
note "$(wc -l < "$ROW_DIR/shortcuts.tsv") shortcuts for app.tileshell:"; while IFS= read -r l; do note "  $l"; done < "$ROW_DIR/shortcuts.tsv"
for act in "$CLOCK" "$CALC" "$REC"; do
  got="$(awk -F'\t' -v a="$act" '$2 == a' "$ROW_DIR/shortcuts.tsv" | sort -t$'\t' -k3,3n | cut -f1 | tr '\n' ' ' | sed 's/ $//')"
  assert_eq "${act##*.}: its shortcuts in rank order" "${WANT[$act]}" "$got"
  ranks="$(awk -F'\t' -v a="$act" '$2 == a' "$ROW_DIR/shortcuts.tsv" | cut -f3 | sort -n | tr '\n' ' ' | sed 's/ $//')"
  n=$(echo "${WANT[$act]}" | wc -w)
  assert_eq "${act##*.}: ranks 0..$((n - 1))" "$(seq -s ' ' 0 $((n - 1)))" "$ranks"
  for id in ${WANT[$act]}; do
    assert_contains "$id carries its app's glyph" "_glyph" "$(awk -F'\t' -v i="$id" '$1 == i {print $4}' "$ROW_DIR/shortcuts.tsv")"
  done
done

# ---------------------------------------------------------------- each shortcut's own intent opens its page
recs_before="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"is_recording=1\"" | tr -d '\r' | grep -c '_id=')"
# \x1f-separated on fd 3: an empty field must not shift the columns (tab is IFS whitespace), and adb inside the loop
# must not read the list.
while IFS=$'\x1f' read -r id act _ _ action cmp page <&3; do
  [ -n "${PAGE_TAG[$id]:-}" ] || continue
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
  adb shell "am start -W -a $action -n $cmp --es page $page" > "$ROW_DIR/launch_$id.txt" 2>&1
  sleep 2
  if [ "$id" = alarm ] || [ "$id" = timer ] || [ "$id" = stopwatch ] || [ "$id" = world_clock ]; then gdump "$ROW_DIR/page_$id.xml"; else dump_ui "$ROW_DIR/page_$id.xml"; fi
  assert_contains "$id: ${cmp} resumed" "${cmp#app.tileshell/}" "$(resumed)"
  tag="${PAGE_TAG[$id]}"
  sel="$(python3 -c '
import re, sys
x = open(sys.argv[1]).read()
m = re.search(r"<node [^>]*resource-id=\"%s\"[^>]*>" % re.escape(sys.argv[2]), x)
print("yes" if m and re.search(r"(selected|checked)=\"true\"", m.group(0)) else ("present" if m else "absent"))' "$ROW_DIR/page_$id.xml" "$tag")"
  case "$tag" in
    rec_page:*)
      other="rec_page:list"; [ "$tag" = rec_page:list ] && other="rec_page:record"
      assert_eq "$id: $tag is the page shown" yes "$([ "$sel" = absent ] && echo no || echo yes)"
      assert_eq "$id: and $other is not" no "$(has_node "$ROW_DIR/page_$id.xml" "$other")" ;;
    *) assert_eq "$id: $tag selected" yes "$sel" ;;
  esac
done 3< <(tr '\t' '\037' < "$ROW_DIR/shortcuts.tsv")
recs_after="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"is_recording=1\"" | tr -d '\r' | grep -c '_id=')"
assert_eq "new_recording opened the record page without starting a take (is_recording count unchanged)" "$recs_before" "$recs_after"

# ---------------------------------------------------------------- converter: last-used category (T15-43)
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell am start -W -n app.tileshell/.calculator.CalculatorActivity --es page converter > /dev/null 2>&1; sleep 2
dump_ui "$ROW_DIR/conv0.xml"
first="$(node_text "$ROW_DIR/conv0.xml" calc_title)"; note "converter opened on: $first"
tap_node "$ROW_DIR/conv0.xml" calc_menu; sleep 1.5
dump_ui "$ROW_DIR/conv_pane.xml"
len="$(python3 -c '
import re, sys
x = open(sys.argv[1]).read()
for m in re.finditer(r"<node [^>]*>", x):
    n = m.group(0); r = re.search(r"resource-id=\"(calc_converter_category:\d+)\"", n)
    if r and re.search(r"text=\"Length\"", n): print(r.group(1)); break' "$ROW_DIR/conv_pane.xml")"
note "Length's pane row: $len"
tap_node "$ROW_DIR/conv_pane.xml" "$len"; sleep 1.5
dump_ui "$ROW_DIR/conv_len.xml"
tap_node "$ROW_DIR/conv_len.xml" calc_key:5; sleep 1
dump_ui "$ROW_DIR/conv_len5.xml"
note "Length conversion: $(node_text "$ROW_DIR/conv_len5.xml" calc_converter_value1) -> $(node_text "$ROW_DIR/conv_len5.xml" calc_converter_value2)"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
conv="$(awk -F'\t' '$1 == "converter"' "$ROW_DIR/shortcuts.tsv")"
adb shell "am start -W -a $(echo "$conv" | cut -f5) -n $(echo "$conv" | cut -f6) --es page $(echo "$conv" | cut -f7)" > /dev/null 2>&1; sleep 2
dump_ui "$ROW_DIR/conv_last.xml"
assert_eq "converter reopens on the last-used category" LENGTH "$(node_text "$ROW_DIR/conv_last.xml" calc_title)"

# ---------------------------------------------------------------- converter: Volume the first time (pm clear -> provision.sh)
ring_save
adb shell pm clear app.tileshell > "$ROW_DIR/pm_clear.txt" 2>&1
bash "$QROOT/phase-03/scripts/provision.sh" > "$ROW_DIR/provision.txt" 2>&1
echo $? > "$ROW_DIR/provision.rc"
assert_eq "provision.sh after pm clear" 0 "$(cat "$ROW_DIR/provision.rc")"
adb shell cmd notification allow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener
adb shell "am start -W -a $(echo "$conv" | cut -f5) -n $(echo "$conv" | cut -f6) --es page $(echo "$conv" | cut -f7)" > /dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/conv_first.xml"
assert_eq "after pm clear the converter opens on Volume" VOLUME "$(node_text "$ROW_DIR/conv_first.xml" calc_title)"

# ---------------------------------------------------------------- the burst half (phase 11)
# Phase 11 E3's method through its own helpers (q.sh: the no-idle dump, the hold, the [quick] slice, C-6's force-stop
# and Home after a launch). Each pinned tile bursts its app's shortcuts in rank order with the activity-keyed line
# naming only that activity's ids (T11-12), and each satellite opens its page.
# Counted, not grep -q: under pipefail a grep that stops at its first match kills unzip with SIGPIPE, and the pipeline
# then "fails" on a build that HAS the burst (E27's first rebased run said NOT RUN).
if [ "$(unzip -p "$APK" 'classes*.dex' 2>/dev/null | grep -ac 'quick_sat_label')" = 0 ]; then
  for t in "Alarms & Clock" Calculator "Voice Recorder"; do
    _verdict FAIL "$t: burst satellites and the [quick] shortcuts line" "NOT RUN: this build has no phase 11 burst (T15-21: the burst half runs on the rebased main)"
  done
else
  . "$QROOT/phase-11/scripts/q.sh"
  layout_restore "$P15/baseline_layout.json"
  burst() { # tile-id tag -> held dump at $ROW_DIR/burst_<tag>.xml
    local X Y
    adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
    qdump "$ROW_DIR/burst_$2-rest.xml"
    read -r X Y <<< "$(center "$ROW_DIR/burst_$2-rest.xml" "tile:$1")"
    hold_down "$X" "$Y"; sleep 1.0; qdump "$ROW_DIR/burst_$2.xml"; hold_up "$X" "$Y"; sleep 0.8
  }
  declare -A TILE=( [clock]="app:app.tileshell/app.tileshell.clock.ClockActivity:0" [calc]="app:app.tileshell/app.tileshell.calculator.CalculatorActivity:0" [rec]="app:app.tileshell/app.tileshell.recorder.RecorderActivity:0" )
  declare -A LABELS=( [clock]="Alarm,Timer,Stopwatch,World Clock," [calc]="Standard,Scientific,Programmer,Converter," [rec]="New recording,Recordings," )
  declare -A LINE=( [clock]="[quick] shortcuts for app.tileshell/.clock.ClockActivity/0: 4 (4 shown: alarm,timer,stopwatch,world_clock)"
    [calc]="[quick] shortcuts for app.tileshell/.calculator.CalculatorActivity/0: 4 (4 shown: standard,scientific,programmer,converter)"
    [rec]="[quick] shortcuts for app.tileshell/.recorder.RecorderActivity/0: 2 (2 shown: new_recording,recordings)" )
  declare -A IDS=( [clock]="alarm timer stopwatch world_clock" [calc]="standard scientific programmer converter" [rec]="new_recording recordings" )
  for k in clock calc rec; do
    n=$(echo "${IDS[$k]}" | wc -w)
    MARK="$(ring_mark)"; burst "${TILE[$k]}" "$k"
    got="$(for i in $(seq 0 $((n - 1))); do node_text "$ROW_DIR/burst_$k.xml" "quick_sat_label:$i"; done | tr '\n' ',')"
    assert_eq "$k: satellites' labels in rank order" "${LABELS[$k]}" "$got"
    [ "$k" = rec ] && assert_eq "rec: no third satellite" no "$(has_node "$ROW_DIR/burst_$k.xml" quick_sat_label:2)"
    S="$(quick_since "$MARK")"
    assert_contains "$k: the activity-keyed shortcuts line (T11-12)" "${LINE[$k]}" "$S"
    for other in clock calc rec music; do
      [ "$other" = "$k" ] && continue
      case "$other" in clock) a=.clock.ClockActivity ;; calc) a=.calculator.CalculatorActivity ;; rec) a=.recorder.RecorderActivity ;; music) a=.music.MusicActivity ;; esac
      assert_absent "$k: its burst names no $other shortcut" "shortcuts for app.tileshell/$a" "$S"
    done
    i=0
    for id in ${IDS[$k]}; do
      [ "$i" -gt 0 ] && burst "${TILE[$k]}" "$k-$i"
      d="$ROW_DIR/burst_$k.xml"; [ "$i" -gt 0 ] && d="$ROW_DIR/burst_$k-$i.xml"
      [ "$id" = new_recording ] && rb="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"is_recording=1\"" | tr -d '\r' | grep -c '_id=')"
      tap_node "$d" "quick_sat:$i"; sleep 3.5
      qdump "$ROW_DIR/landed_$id.xml"
      tag="${PAGE_TAG[$id]}"
      case "$k" in clock) act=.clock.ClockActivity ;; calc) act=.calculator.CalculatorActivity ;; rec) act=.recorder.RecorderActivity ;; esac
      assert_contains "$id: the satellite resumed its app" "app.tileshell/$act" "$(resumed)"
      if [ "$k" = rec ]; then
        assert_eq "$id: $tag is the page shown" yes "$(has_node "$ROW_DIR/landed_$id.xml" "$tag")"
      else
        assert_eq "$id: $tag selected" yes "$(python3 -c '
import re, sys
x = open(sys.argv[1]).read()
m = re.search(r"<node [^>]*resource-id=\"%s\"[^>]*>" % re.escape(sys.argv[2]), x)
print("yes" if m and re.search(r"(selected|checked)=\"true\"", m.group(0)) else "no")' "$ROW_DIR/landed_$id.xml" "$tag")"
      fi
      if [ "$id" = new_recording ]; then
        assert_eq "new_recording started no take (is_recording count unchanged)" "$rb" "$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"is_recording=1\"" | tr -d '\r' | grep -c '_id=')"
      fi
      i=$((i + 1))
    done
  done
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
fi

# ---------------------------------------------------------------- restore
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
layout_restore "$P15/baseline_layout.json"
row_end
