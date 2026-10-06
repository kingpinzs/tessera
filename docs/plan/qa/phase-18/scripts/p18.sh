#!/usr/bin/env bash
# Phase 18's driver floor (build task 12; Decisions "the phase's driver floor", the Acceptance preamble's "Round 3
# floor"), sourced by every driver AFTER lib.sh (phase 03's floor, symlinked here: row_begin / row_end, the asserts,
# ring_mark / ring_since / ring_save, record, ensure_start, wake_device, fill_volume / unfill_volume). Everything here
# drives the real emulator; nothing is simulated. No helper touches the host's audio or the microphone.
#
#   pubvol_up                      the row's own public volume (a virtual disk): asserts `mounted`, exports and prints
#                                  PUBVOL_UUID and PUBVOL_ID, installs pubvol_down's safety net in the EXIT trap
#   pubvol_down                    removes it; asserts no disk and no public volume remain (safe when none is up)
#   files_up [paced] [big] [zips] [tenk] [media]
#                                  the two snapshots FIRST, then the row's fixtures (the table below)
#   files_down                     removes them; asserts both snapshots equal the ones files_up took
#   fx_names / fx_bytes / fx_date / fx_detail / fx_md5 / fx_order   the fixture table's readers
#   pace_set <bps> / pace_now / pace_clear                          the debug-only pacing pref (qa_files_rate_bps)
#   mid_progress <op> <mark> [timeout_s]                            the floor's meaning of "mid"
#   jvm_gate <tests pattern> <report glob> <case>…                  a JVM gate: gradle.rc AND the TEST-*.xml reports
#   absent_in, c6, gdump, top_activity, q, rings_save               copies (below)
#   ensure_start                                                    lib.sh's, wherever a row says "Home"
#   layout_json / layout_save / layout_restore                      qa/phase-02/scripts/layout.sh, sourced
#
# Copies, because the file they live in cannot be sourced without its rows' own state (p17.sh sets phase 17's BASELINE,
# GEN, RINGS and STAMP_FILES, and its GEN is a folder under qa/phase-17 that this phase must not write — p17.sh:8-11's
# rule, phase 16's reason):
#   q, top_activity, gdump, absent_in, rings_save, c6          qa/phase-17/scripts/p17.sh (gdump's scratch name is p18_)
#   media_scan, media_ids, _media_dest, media_up, media_down   qa/phase-17/scripts/p17.sh:150-213, VERBATIM — the only
#                                                              change is GEN, which is this phase's gen/media
export ANDROID_SERIAL=emulator-5554

P18="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P01S="$(cd "$P18/../phase-01/scripts" && pwd)"
P02S="$(cd "$P18/../phase-02/scripts" && pwd)"
P17S="$(cd "$P18/../phase-17/scripts" && pwd)"
BASELINE="$P18/baseline_layout.json"
FXGEN="$P18/gen"              # generated fixtures (git-ignored): make_fixtures.py, phase 17's make_videos.sh
GEN="$FXGEN/media"            # what media_up pushes: make_photos.py's six images and make_videos.sh's videos
MUSIC6="$P18/../phase-01/MUSIC6-fixtures"
PREFS_EDIT="$P01S/prefs_edit.py"
MAKE_ZIPS="$P18/scripts/make_zips.py"
STAMP_FILES="$P18/scripts/p18.sh"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"

FILES_ACTIVITY="app.tileshell/.files.FilesActivity"
FILES_TILE="tile:app:app.tileshell/app.tileshell.files.FilesActivity:0"
QAC=app.tileshell.testclient.qacapture
QAC_APK="$REPO/testapps/qa-capture/build/outputs/apk/debug/qa-capture-debug.apk"
QAC_TAG=TileShellQa
# Files and its copy service run in the shell's main process (the manifest gives neither a process), so the launcher's
# ring is the only ring this phase reads; a row that also opens Photos' viewer, Video or Music adds that ring to RINGS.
RINGS=(launcher)

QA_FILES=/sdcard/QA-Files
QA_BIG=/sdcard/QA-Big
PACE_KEY=qa_files_rate_bps
PACE_BPS=8388608              # 8 MB/s (Decisions "the pacing switch"): big.bin and qa-big.zip take about 25 s each

# layout_json, layout_save, layout_restore (the verified restore, C-3).
. "$P02S/layout.sh"

# ---------------------------------------------------------------- copies (qa/phase-17/scripts/p17.sh)

# One command, one string; stdin is /dev/null so a `while read` loop around it keeps its input.
q() { adb shell "$1" 2>&1 </dev/null | tr -d '\r'; }

top_activity() {
  adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'
}

device_ms() { adb shell date +%s%3N | tr -d '\r'; }

# A screen that never idles — the progress box — is dumped through phase 05's gesture driver with
# setWaitForIdleTimeout(0) (C-10, RV13). A new file per attempt; after 20 tries the plain uiautomator dump is the fallback.
gdump() { # out.xml
  local out="$1" i name
  : > "$out.drv"
  for i in $(seq 1 20); do
    name="p18_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    if grep -q '<node' "$out"; then
      grep -o 'gesture.dump.windows=.*' "$out.drv" | tail -1 | tr -d '\r' > "$out.windows"
      return 0
    fi
    echo "(attempt $i read no nodes from $name)" >> "$out.drv"
    sleep 0.25
  done
  echo "(falling back to uiautomator dump)" >> "$out.drv"
  if adb shell uiautomator dump /sdcard/Download/p18_fallback.xml >/dev/null 2>&1; then
    adb shell cat /sdcard/Download/p18_fallback.xml > "$out" 2>/dev/null
    adb shell rm -f /sdcard/Download/p18_fallback.xml >/dev/null 2>&1
    if grep -q '<node' "$out"; then : > "$out.windows"; return 0; fi
  fi
  echo "(dump failed)" > "$out"
  return 1
}

# An absence check that cannot pass on an unreadable ring: the slice must hold a ring line (a `wall=` stamp) first.
absent_in() { # name needle slice
  if printf '%s\n' "$3" | grep -q 'wall='; then
    assert_absent "$1" "$2" "$3"
  else
    _verdict FAIL "$1" "the ring slice is empty or unreadable, so the absence proves nothing"
  fi
}

# Every ring the row names, saved: a ring whose process is not running prints nothing and saves nothing.
rings_save() {
  local r
  for r in "${RINGS[@]}"; do ring_save "$r"; done
}

# C-6: after any launch — keep EVERY ring (a force-stop empties them), force-stop, Home, wait for Start. Follow it with
# ensure_start.
c6() {
  rings_save
  adb shell am force-stop app.tileshell
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 4
}

# ---- media fixtures: p17.sh:150-213 verbatim (media_scan … media_down). GEN above is this phase's.

media_scan() { adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 2; }
media_ids() { # images | video  -> the ids, one per line, sorted
  q "content query --uri content://media/external/$1/media --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n
}
media_count() { media_ids "$1" | grep -c . ; }
# The id of a file by its display name (the newest row of that name).
media_id() { # images|video name [relative_path]
  q "content query --uri content://media/external/$1/media --projection _id:_display_name:relative_path --sort '_id DESC'" | grep -F "_display_name=$2, relative_path=${3:-}" | head -1 | sed -n 's/.*_id=\([0-9]*\),.*/\1/p'
}

# Where each fixture goes: qa-photo-0..2 into DCIM/Camera, qa-photo-3..5 and the editor fixtures into Pictures/QA-Album
# (the two folders of the Fixtures paragraph), every video and its .srt into Movies.
_media_dest() {
  case "$1" in
    qa-photo-[0-2].png) echo /sdcard/DCIM/Camera ;;
    *.png|*.jpg|*.heic|*.dng) echo /sdcard/Pictures/QA-Album ;;
    *) echo /sdcard/Movies ;;
  esac
}

# media_up <names…>: a census of the image and video ids, then ONLY the named files pushed from $GEN, each given its
# own minute of mtime — the i-th name is i minutes older than the first — then a scan. With no names it is the census
# alone.
MEDIA_PUSHED=()
MEDIA_MARK=""
media_up() {
  local name dest i=0 base stamp
  [ -n "${ROW_DIR:-}" ] || { echo "media_up: row_begin has not run" >&2; return 1; }
  media_ids images > "$ROW_DIR/census-images.txt"; media_ids video > "$ROW_DIR/census-video.txt"
  CENSUS_IMAGES="$(grep -c . "$ROW_DIR/census-images.txt")"; CENSUS_VIDEO="$(grep -c . "$ROW_DIR/census-video.txt")"
  MEDIA_MARK="$(device_ms)"
  MEDIA_PUSHED=()
  base="$(adb shell date +%s | tr -d '\r')"
  for name in "$@"; do
    [ -f "$GEN/$name" ] || { _verdict FAIL "media_up fixture $name" "missing in $GEN (run make_photos.py <dir> editor and make_videos.sh <dir>)"; continue; }
    dest="$(_media_dest "$name")"
    adb shell mkdir -p "$dest"
    adb push "$GEN/$name" "$dest/$name" >/dev/null
    stamp="$(date -d "@$((base - 3600 - i * 60))" +%Y%m%d%H%M.%S)"
    adb shell touch -m -t "$stamp" "$dest/$name"
    MEDIA_PUSHED+=("$dest/$name")
    i=$((i + 1))
  done
  media_scan
  record "census" "images=$CENSUS_IMAGES video=$CENSUS_VIDEO pushed=${#MEDIA_PUSHED[@]}"
}

# media_down: everything the row pushed, plus every row the SHELL made since the MARK (captures, edits, trims), removed;
# a scan; and the ids asserted equal to the census (RV12).
media_down() {
  local f since
  [ -n "$MEDIA_MARK" ] || { echo "media_down: media_up has not run" >&2; return 1; }
  for f in "${MEDIA_PUSHED[@]}"; do adb shell rm -f "$f"; done
  since="$((MEDIA_MARK / 1000))"
  for kind in images video; do
    q "content delete --uri content://media/external/$kind/media --where \"owner_package_name='app.tileshell' AND date_added>=$since\"" >/dev/null
  done
  adb shell rmdir /sdcard/Pictures/QA-Album >/dev/null 2>&1
  media_scan
  # The same ids as the census, not only the same count: a row swapped for another would keep the count.
  assert_eq "media_down: the images are the census's ($CENSUS_IMAGES)" "$(tr '\n' ' ' < "$ROW_DIR/census-images.txt")" "$(media_ids images | tr '\n' ' ')"
  assert_eq "media_down: the videos are the census's ($CENSUS_VIDEO)" "$(tr '\n' ' ' < "$ROW_DIR/census-video.txt")" "$(media_ids video | tr '\n' ' ')"
  MEDIA_PUSHED=(); MEDIA_MARK=""
}

# ---------------------------------------------------------------- a row's start (the Acceptance preamble; C-3)

# Evidence is kept: a row folder left by an earlier run is renamed <ROW>-run<k> (the next free k) before row_begin
# writes into <ROW> again. Call it BEFORE row_begin.
keep_earlier_run() { # row
  local dir="$P18/$1" k=1
  [ -d "$dir" ] || return 0
  while [ -e "$dir-run$k" ]; do k=$((k + 1)); done
  mv "$dir" "$dir-run$k"
}

# Every row starts from qa/phase-18/baseline_layout.json through layout_restore, and after the restore the ring holds
# zero `assignSlotOnce … -> assigned` lines (a baseline missing one of the build's addedOnce markers would make the
# shell seed a slot again); then Start alone.
baseline_start() {
  local mark slice
  rings_save
  mark="$(ring_mark)"
  if layout_restore "$BASELINE"; then _verdict PASS "layout_restore: the shell came up on baseline_layout.json" "order, dock, folders and slots as written"
  else _verdict FAIL "layout_restore: the shell came up on baseline_layout.json" "layout_restore returned non-zero"; fi
  ensure_start
  slice="$(ring_since "$mark")"
  printf '%s\n' "$slice" > "$ROW_DIR/baseline-slice.txt"
  # Not a bare absence: the slice must be a readable ring (wall= stamps) that holds the seed's own lines.
  if printf '%s\n' "$slice" | grep -q 'wall='; then
    assert_eq "after the restore: zero assignSlotOnce … -> assigned lines" "0" "$(printf '%s\n' "$slice" | grep -cE 'assignSlotOnce .* -> assigned')"
    assert_ne "…and the slice does hold the seed's lines (assignSlotOnce … already run / kept)" "0" "$(printf '%s\n' "$slice" | grep -c 'assignSlotOnce')"
  else
    _verdict FAIL "after the restore: zero assignSlotOnce … -> assigned lines" "the ring slice is empty or unreadable, so the absence proves nothing"
  fi
}

# ---------------------------------------------------------------- the EXIT trap

# Add a command to the EXIT trap IN FRONT of whatever the row already set there (the row's own trap still runs, after).
_p18_trap_add() { # command
  local cur
  cur="$(trap -p EXIT)"
  case "$cur" in *"$1"*) return 0 ;; esac
  if [ -z "$cur" ]; then
    trap "$1" EXIT
  else
    # `trap -p` prints: trap -- '<the command, shell-quoted>' EXIT
    eval "set -- \"\$1\" ${cur#trap -- }"
    trap "$1; $2" EXIT
  fi
}

# ---------------------------------------------------------------- the row's own public volume (a virtual disk)

PUBVOL_UP=""; PUBVOL_UUID=""; PUBVOL_ID=""; PUBVOL_DISK=""; PUBVOL_PATH=""
_pubvol_disks() { q "sm list-disks" | grep -c '^disk:'; }
_pubvol_volumes() { q "sm list-volumes public" | grep -c '^public:'; }

# Task 0 (a) measured it: `sm set-virtual-disk true` makes disk:7,416, `sm partition … public` mounts public:7,417 (vfat,
# 512 MB, label "Virtual SD card") within 3 s, with a NEW UUID each time.
pubvol_up() {
  local i line
  if [ "$(_pubvol_disks)" != 0 ] || [ "$(_pubvol_volumes)" != 0 ]; then
    _verdict FAIL "pubvol_up precondition: no disk and no public volume" "disks=[$(q 'sm list-disks' | xargs)] volumes=[$(q 'sm list-volumes public' | xargs)]"
    return 1
  fi
  PUBVOL_UP=1
  _p18_trap_add _pubvol_exit
  adb shell sm set-virtual-disk true
  for i in $(seq 1 40); do
    PUBVOL_DISK="$(q "sm list-disks" | grep -m1 '^disk:')"
    [ -n "$PUBVOL_DISK" ] && break
    sleep 0.25
  done
  assert_ne "pubvol_up: sm list-disks names the virtual disk" "" "$PUBVOL_DISK"
  [ -n "$PUBVOL_DISK" ] || return 1
  adb shell sm partition "$PUBVOL_DISK" public
  line=""
  for i in $(seq 1 80); do
    line="$(q "sm list-volumes public" | grep -m1 -E '^public:[0-9]+,[0-9]+ mounted ')"
    [ -n "$line" ] && break
    sleep 0.25
  done
  note "pubvol_up: sm list-volumes public -> $(q 'sm list-volumes public' | xargs)"
  assert_contains "pubvol_up: the public volume is mounted" " mounted " "$line "
  [ -n "$line" ] || return 1
  PUBVOL_ID="${line%% *}"
  PUBVOL_UUID="${line##* }"
  PUBVOL_PATH="/storage/$PUBVOL_UUID"
  export PUBVOL_UUID PUBVOL_ID PUBVOL_DISK PUBVOL_PATH
  # FUSE's view of the volume appears a moment after `mounted`: wait until the shell can list it.
  for i in $(seq 1 40); do adb shell "ls -d $PUBVOL_PATH" >/dev/null 2>&1 && break; sleep 0.25; done
  assert_eq "pubvol_up: $PUBVOL_PATH is a directory the shell can list" "$PUBVOL_PATH" "$(q "ls -d $PUBVOL_PATH")"
  echo "$PUBVOL_UUID $PUBVOL_ID"
}

_pubvol_remove() {
  local i
  adb shell sm set-virtual-disk false
  for i in $(seq 1 80); do
    [ "$(_pubvol_disks)" = 0 ] && [ "$(_pubvol_volumes)" = 0 ] && break
    sleep 0.25
  done
}

pubvol_down() {
  _pubvol_remove
  assert_eq "pubvol_down: sm list-disks is empty" "" "$(q 'sm list-disks' | xargs)"
  assert_eq "pubvol_down: no public volume remains" "" "$(q 'sm list-volumes public' | xargs)"
  PUBVOL_UP=""; PUBVOL_UUID=""; PUBVOL_ID=""; PUBVOL_DISK=""; PUBVOL_PATH=""
}

# The safety net: a row that died between pubvol_up and pubvol_down still gives the disk back. It adds no verdict (the
# row's summary may already be written) and says so on stderr.
_pubvol_exit() {
  [ -n "$PUBVOL_UP" ] || return 0
  echo "p18: the row ended with its public volume still up — removing it (EXIT trap)" >&2
  _pubvol_remove
  echo "p18: after the trap: disks=[$(q 'sm list-disks' | xargs)] public volumes=[$(q 'sm list-volumes public' | xargs)]" >&2
  PUBVOL_UP=""
}

# ---------------------------------------------------------------- the pacing pref (Decisions "the pacing switch")

# qa_files_rate_bps in the shell's start_theme prefs, set or removed with prefs_edit.py the way phase 17's drivers write
# their QA prefs (p17_video.sh qa_pref): every ring saved, another app in front, the shell stopped, then the file. The
# shell is left stopped with Settings in front; the caller goes Home (files_up / files_down call ensure_start).
PACE_FILE_WAS=""   # "present" / "absent": whether start_theme.xml existed before this row's first write
_pace_file() { adb shell run-as app.tileshell cat shared_prefs/start_theme.xml 2>/dev/null; }
_pace_write() { # value|--remove
  rings_save
  adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
  adb shell am force-stop app.tileshell; sleep 0.5
  if [ -z "$PACE_FILE_WAS" ]; then
    if adb shell run-as app.tileshell ls shared_prefs/start_theme.xml >/dev/null 2>&1; then PACE_FILE_WAS=present; else PACE_FILE_WAS=absent; fi
  fi
  _pace_file > "$ROW_DIR/.prefs-in.xml"
  python3 "$PREFS_EDIT" "$ROW_DIR/.prefs-in.xml" "$PACE_KEY" string "$1" > "$ROW_DIR/.prefs-out.xml"
  if [ "$1" = --remove ] && [ "$PACE_FILE_WAS" = absent ] && ! grep -q 'name=' "$ROW_DIR/.prefs-out.xml"; then
    # The file did not exist before the row and holds nothing else now: remove it, so the app is as the row found it.
    adb shell run-as app.tileshell rm -f shared_prefs/start_theme.xml
  else
    adb shell "run-as app.tileshell sh -c 'mkdir -p shared_prefs && cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/.prefs-out.xml"
  fi
}
pace_now() { _pace_file | tr -d '\r' | sed -n "s/.*name=\"$PACE_KEY\">\([^<]*\)<.*/\1/p"; }
pace_set() { # [bps]
  local bps="${1:-$PACE_BPS}"
  _pace_write "$bps"
  assert_eq "pace_set: $PACE_KEY read back from start_theme.xml" "$bps" "$(pace_now)"
}
pace_clear() {
  _pace_write --remove
  assert_eq "pace_clear: $PACE_KEY is gone from start_theme.xml" "" "$(pace_now)"
}

# ---------------------------------------------------------------- the fixture table (Fixtures; r3 V13 / V12)

# ONE table: every order and detail assertion of every row reads from it. No two fixtures share a date or a size.
#   name    the path under /sdcard/QA-Files (a folder ends in /)
#   bytes   the size (- for a folder)
#   date    the `touch -d` date given on the device AFTER the file is written (device-local time); `now` = touched last,
#           with no date, so it is the newest file on the phone (never.png, Q-18-1)
#   detail  the literal detail string per r11/files.md 1.5.8: a folder shows the date only, a file its size then its date;
#           the size to 3 significant figures in bytes / KB / MB / GB (1 KB = 1,024 bytes, ROUNDED to nearest), the date in
#           en-US short form (M/D/YYYY). @today = the device's date when `now` was touched.
#   how     dev = made on the device (by adb shell; .nomedia's 35 bytes by MediaProvider's scan); push = made on the host and pushed; dir = a folder; opt:<x> = only
#           with files_up's <x> argument
# Where the images come from: scripts/make_fixtures.py (make_photos.py's writer and colours — img-N is colour N,
# qa-hidden.png colour 4, r1 / r2 / r3 / never colours 0 / 1 / 2 / 3). qa-steps.mp4 is phase 17's make_videos.sh's;
# qa-hidden.mp4 is that file with its sound removed (a different size); 03.mp3 is MUSIC6's; qa-hidden.mp3 is MUSIC6's
# 04.mp3 with every tag removed (E6: "the fixture carries no tags").
FX_TABLE='a.txt|10|2026-01-01 00:00|10 bytes 1/1/2026|dev
b.bin|307200|2026-01-02 00:00|300 KB 1/2/2026|dev
sub/|-|2026-01-03 12:00|1/3/2026|dir
img-0.png|1949|2026-01-09 12:00|1.90 KB 1/9/2026|push
img-1.png|3809|2026-01-04 12:00|3.72 KB 1/4/2026|push
img-2.png|5669|2026-01-08 12:00|5.54 KB 1/8/2026|push
img-3.png|7530|2026-01-05 12:00|7.35 KB 1/5/2026|push
img-4.png|9361|2026-01-07 12:00|9.14 KB 1/7/2026|push
img-5.png|11156|2026-01-06 12:00|10.9 KB 1/6/2026|push
qa-steps.mp4|111574|2026-01-10 12:00|109 KB 1/10/2026|push
03.mp3|360494|2026-01-11 12:00|352 KB 1/11/2026|push
.hidden.txt|7|2026-01-12 12:00|7 bytes 1/12/2026|dev
hidden/|-|2026-01-13 12:00|1/13/2026|dir
hidden/.nomedia|35|2026-01-14 12:00|35 bytes 1/14/2026|dev
hidden/qa-hidden.mp3|360176|2026-01-15 12:00|352 KB 1/15/2026|push
hidden/qa-hidden.png|2789|2026-01-16 12:00|2.72 KB 1/16/2026|push
hidden/qa-hidden.mp4|10257|2026-01-17 12:00|10.0 KB 1/17/2026|push
recent/|-|2026-01-18 12:00|1/18/2026|dir
recent/r1.png|780|2026-01-19 12:00|780 bytes 1/19/2026|push
recent/r2.png|2160|2026-01-20 12:00|2.11 KB 1/20/2026|push
recent/r3.png|3540|2026-01-21 12:00|3.46 KB 1/21/2026|push
recent/never.png|4921|now|4.81 KB @today|push
big.bin|209715200|2026-01-22 12:00|200 MB 1/22/2026|opt:big'

FX_OPTS=""        # the arguments files_up was given
FX_TODAY=""       # the device's date (M/D/YYYY) when never.png was touched

_fx_has() { case " $FX_OPTS " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }
# The table's rows this row made: everything but the opt:<x> rows whose <x> was not asked for.
_fx_rows() {
  local line how
  while IFS= read -r line; do
    how="${line##*|}"
    case "$how" in opt:*) _fx_has "${how#opt:}" || continue ;; esac
    printf '%s\n' "$line"
  done <<< "$FX_TABLE"
}
fx_names() { _fx_rows | cut -d'|' -f1; }
_fx_field() { # name column
  _fx_rows | awk -F'|' -v n="$1" -v c="$2" '$1 == n { print $c; found = 1 } END { exit !found }' \
    || { echo "p18: no fixture named [$1] in the table" >&2; return 1; }
}
fx_bytes() { _fx_field "$1" 2; }
fx_date() { _fx_field "$1" 3; }
fx_detail() { local d; d="$(_fx_field "$1" 4)" || return 1; printf '%s\n' "${d//@today/$FX_TODAY}"; }
fx_md5() { awk -v n="$1" '$2 == n { print $1; found = 1 } END { exit !found }' "$ROW_DIR/fixtures.md5"; }
# The names directly inside one folder of QA-Files ("" = its root), in the order a sort key dictates (E3's order rules,
# r3 V13 / Y2: folders before files in every sort; name A → Z ignoring case; date newest first; size largest first with
# folders among themselves by name; ties by name). Hidden names (a leading dot) are left out unless the third argument
# is `hidden`.
fx_order() { # name|date|size [folder/] [hidden]
  _fx_rows | python3 -c '
import sys
key, folder, hidden = sys.argv[1], sys.argv[2], sys.argv[3] == "hidden"
rows = []
for line in sys.stdin:
    name, size, date, detail, how = line.rstrip("\n").split("|")
    if not name.startswith(folder) or name == folder: continue
    rest = name[len(folder):]
    is_dir = rest.endswith("/")
    leaf = rest.rstrip("/")
    if "/" in leaf or (leaf.startswith(".") and not hidden): continue
    rows.append((leaf, is_dir, 0 if is_dir else int(size), "9999" if date == "now" else date))
by_name = lambda r: r[0].lower()
rows.sort(key=by_name)
if key == "date": rows.sort(key=lambda r: r[3], reverse=True)
elif key == "size": rows.sort(key=lambda r: r[2], reverse=True)
elif key != "name": sys.exit("fx_order: name|date|size, got [%s]" % key)
rows.sort(key=lambda r: not r[1])
print("\n".join(r[0] for r in rows))' "$1" "${2:-}" "${3:-}"
}

# ---------------------------------------------------------------- the snapshots (RV12, specified)

# The shared-storage listing. The doc writes `find /sdcard -not -path '*/Android/*' | sort`; on this image /sdcard is a
# symlink and that prints ONE line ("/sdcard"), a snapshot that could never differ. The trailing slash makes find walk it.
_snap_sdcard() { q "find /sdcard/ -not -path '*/Android/*' | sort"; }
# The `files` collection's paths (every volume MediaStore calls external).
_snap_files() { q "content query --uri content://media/external/file --projection _data" | sed -n 's/^Row: [0-9]* _data=//p' | LC_ALL=C sort; }

# ---------------------------------------------------------------- files_up / files_down

# The host-made part of QA-Files, built once into gen/ (git-ignored) and reused.
_fx_gen() {
  local stage="$FXGEN/QA-Files"
  if [ ! -f "$FXGEN/.made" ] || [ "$P18/scripts/make_fixtures.py" -nt "$FXGEN/.made" ] || [ "$P18/scripts/p18.sh" -nt "$FXGEN/.made" ]; then
    rm -rf "$stage" "$GEN"
    python3 "$P18/scripts/make_fixtures.py" "$FXGEN" > "$FXGEN.make_fixtures.out" 2>&1 || { cat "$FXGEN.make_fixtures.out" >&2; return 1; }
    mv "$FXGEN.make_fixtures.out" "$FXGEN/make_fixtures.out"
    bash "$P17S/make_videos.sh" "$GEN" > "$FXGEN/make_videos.out" 2>&1 || { cat "$FXGEN/make_videos.out" >&2; return 1; }
    cp "$GEN/qa-steps.mp4" "$stage/qa-steps.mp4"
    cp "$MUSIC6/03.mp3" "$stage/03.mp3"
    ffmpeg -hide_banner -loglevel error -y -i "$GEN/qa-steps.mp4" -an -c:v copy -movflags +faststart "$stage/hidden/qa-hidden.mp4" || return 1
    ffmpeg -hide_banner -loglevel error -y -i "$MUSIC6/04.mp3" -map 0:a -c:a copy -map_metadata -1 -id3v2_version 0 -write_id3v1 0 -write_xing 0 "$stage/hidden/qa-hidden.mp3" || return 1
    date -Is > "$FXGEN/.made"
  fi
}

# files_up [paced] [big] [zips] [tenk] [media] — a row asks only for what it needs:
#   (always)  /sdcard/QA-Files with the table's fixtures (all but big.bin)
#   paced     the pacing pref at 8 MB/s (PACE_BPS); the shell is stopped for the write and Start is asserted after
#   big       big.bin: 200 MB of zeros (dd bs=1m count=200) — the host's free disk is checked first
#   zips      make_zips.py's output pushed to /sdcard/QA-Files/zips/ (fails loudly if the script is not there)
#   tenk      /sdcard/QA-Big: 10,000 empty files f1 … f10000 with 10,000 distinct minutes (f1 the oldest)
#   media     qa-photo-0..2.png into DCIM/Camera and qa-steps.mp4 into Movies, through media_up (and out through
#             media_down) — the ONLY way an image or the video reaches MediaStore's camera folders in this phase
# The two snapshots are taken FIRST, before anything is made. A device that already holds QA-Files or QA-Big is another
# row's dirt: files_up fails rather than build on it.
files_up() {
  local opt line name bytes date how f got want t0 t1 host
  [ -n "${ROW_DIR:-}" ] || { echo "files_up: row_begin has not run" >&2; return 1; }
  FX_OPTS=""
  for opt in "$@"; do
    case "$opt" in paced|big|zips|tenk|media) FX_OPTS="$FX_OPTS $opt" ;; *) _verdict FAIL "files_up: argument" "unknown [$opt] (paced big zips tenk media)"; return 1 ;; esac
  done
  FX_OPTS="${FX_OPTS# }"
  got="$(q "ls -d $QA_FILES $QA_BIG 2>/dev/null" | xargs)"
  if [ -n "$got" ]; then
    _verdict FAIL "files_up precondition: no QA-Files and no QA-Big on the device" "found [$got] — an earlier row's fixtures were not removed"
    return 1
  fi
  _snap_sdcard > "$ROW_DIR/snap-sdcard-before.txt"
  _snap_files > "$ROW_DIR/snap-files-before.txt"
  FX_SNAPPED=1
  note "files_up: snapshots — $(grep -c . "$ROW_DIR/snap-sdcard-before.txt") shared-storage paths, $(grep -c . "$ROW_DIR/snap-files-before.txt") files-collection rows"
  if [ "$(grep -c . "$ROW_DIR/snap-sdcard-before.txt")" -lt 2 ]; then
    _verdict FAIL "files_up: the shared-storage snapshot lists the volume" "only $(grep -c . "$ROW_DIR/snap-sdcard-before.txt") line(s): the snapshot is empty and could prove nothing"
    return 1
  fi
  _fx_gen || { _verdict FAIL "files_up: the host-made fixtures" "make_fixtures.py / make_videos.sh / ffmpeg failed (see $FXGEN)"; return 1; }

  # MediaStore's own folders first, through phase 17's helpers (the census is taken before QA-Files exists).
  if _fx_has media; then media_up qa-photo-0.png qa-photo-1.png qa-photo-2.png qa-steps.mp4; fi

  adb shell mkdir -p "$QA_FILES/sub"
  adb push "$FXGEN/QA-Files/." "$QA_FILES/" > "$ROW_DIR/fixtures-push.out" 2>&1 || { _verdict FAIL "files_up: adb push" "$(tail -1 "$ROW_DIR/fixtures-push.out")"; return 1; }
  adb shell "printf 0123456789 > $QA_FILES/a.txt"
  adb shell "dd if=/dev/zero of=$QA_FILES/b.bin bs=1024 count=300" >/dev/null 2>&1
  adb shell "printf 'hidden\n' > $QA_FILES/.hidden.txt"
  if _fx_has big; then
    host="$(df -BG --output=avail / | tail -1 | tr -dc '0-9')"
    if [ "${host:-0}" -lt 40 ]; then
      _verdict FAIL "files_up big: the host has at least 40 GB free" "$host GB free on / — stopping before a large fixture"
      return 1
    fi
    t0="$(date +%s.%N)"
    adb shell "dd if=/dev/zero of=$QA_FILES/big.bin bs=1m count=200" > "$ROW_DIR/big-dd.out" 2>&1
    t1="$(date +%s.%N)"
    FX_BIG_S="$(python3 -c "print('%.1f' % ($t1 - $t0))")"
    record "files_up big: big.bin written (200 MB of zeros), seconds" "$FX_BIG_S"
  fi
  if _fx_has zips; then _fx_zips || return 1; fi

  # One scan BEFORE the dates are given, so MediaStore has settled when the row acts. Two things the self-test's first
  # runs showed on this image: until a scan reads hidden/.nomedia the audio collection lists qa-hidden.mp3 (FUSE gives
  # every new file a row at once); and the scan REWRITES .nomedia — MediaProvider stores the folder's own path in it
  # (35 bytes for /storage/emulated/0/QA-Files/hidden) and that write moves its date to now. So the table holds the
  # 35-byte file, and the dates are given after the scan (a later scan leaves the file alone: the self-test checks).
  media_scan

  # Every fixture's own date, given on the device after it is written (adb push keeps the host's mtime): the files
  # first, then the folders (making a file moves its folder's date), then never.png with no date — touched last.
  {
    while IFS='|' read -r name bytes date _ how; do
      [ "$how" = dir ] || [ "$date" = now ] || printf 'touch -d "%s" "%s/%s" || echo "TOUCH FAILED %s"\n' "$date" "$QA_FILES" "$name" "$name"
    done < <(_fx_rows)
    while IFS='|' read -r name bytes date _ how; do
      [ "$how" = dir ] && printf 'touch -d "%s" "%s/%s" || echo "TOUCH FAILED %s"\n' "$date" "$QA_FILES" "$name" "$name"
    done < <(_fx_rows)
    printf 'touch "%s/recent/never.png" || echo "TOUCH FAILED never.png"\n' "$QA_FILES"
    printf 'date -r "%s/recent/never.png" +%%-m/%%-d/%%Y\n' "$QA_FILES"
  } > "$ROW_DIR/fixtures-touch.sh"
  adb push "$ROW_DIR/fixtures-touch.sh" /data/local/tmp/p18-touch.sh >/dev/null 2>&1
  q "sh /data/local/tmp/p18-touch.sh; rm -f /data/local/tmp/p18-touch.sh" > "$ROW_DIR/fixtures-touch.out"
  FX_TODAY="$(tail -1 "$ROW_DIR/fixtures-touch.out")"

  # What the device holds against the table: name|bytes|date to the minute, one line per fixture, compared whole.
  : > "$ROW_DIR/fixtures-want.txt"
  while IFS='|' read -r name bytes date _ how; do
    [ "$date" = now ] && continue
    printf '%s|%s|%s\n' "${name%/}" "$bytes" "$date" >> "$ROW_DIR/fixtures-want.txt"
  done < <(_fx_rows)
  fx_stat > "$ROW_DIR/fixtures-got.txt"
  want="$(cat "$ROW_DIR/fixtures-want.txt")"; got="$(grep -v '^recent/never.png|' "$ROW_DIR/fixtures-got.txt")"
  if [ "$want" = "$got" ]; then
    _verdict PASS "files_up: every fixture has the table's size and date" "$(grep -c . "$ROW_DIR/fixtures-want.txt") fixtures (fixtures-got.txt)"
  else
    _verdict FAIL "files_up: every fixture has the table's size and date" "$(diff <(echo "$want") <(echo "$got") | grep '^[<>]' | head -6 | xargs)"
  fi
  assert_eq "files_up: never.png has the table's size" "$(fx_bytes recent/never.png)" "$(q "stat -c %s $QA_FILES/recent/never.png")"
  assert_eq "files_up: never.png is the newest file under /sdcard" "$QA_FILES/recent/never.png" \
    "$(q "find /sdcard/ -type f -not -path '*/Android/*' -exec stat -c '%Y %n' {} + | sort -n | tail -1 | cut -d' ' -f2-")"

  # The md5s, recorded with adb shell md5sum; a pushed file's must be its host file's.
  q "cd $QA_FILES && md5sum $(_fx_rows | awk -F'|' '$5 != "dir" { printf "\"%s\" ", $1 }')" | sed 's/  */ /' > "$ROW_DIR/fixtures.md5"
  ( cd "$FXGEN/QA-Files" && md5sum $(_fx_rows | awk -F'|' '$5 == "push" { printf "%s ", $1 }') ) | sed 's/  */ /' | LC_ALL=C sort > "$ROW_DIR/.fixtures-host.md5"
  got="$(awk 'NR == FNR { pushed[$0]; next } ($2 in pushed)' <(_fx_rows | awk -F'|' '$5 == "push" { print $1 }') "$ROW_DIR/fixtures.md5" | LC_ALL=C sort)"
  if [ "$(cat "$ROW_DIR/.fixtures-host.md5")" = "$got" ]; then
    _verdict PASS "files_up: every pushed fixture's md5 on the device is its host file's" "$(grep -c . "$ROW_DIR/.fixtures-host.md5") files"
  else
    _verdict FAIL "files_up: every pushed fixture's md5 on the device is its host file's" "$(diff "$ROW_DIR/.fixtures-host.md5" <(echo "$got") | grep '^[<>]' | head -4 | xargs)"
  fi
  record "files_up: md5s recorded (adb shell md5sum)" "$(grep -c . "$ROW_DIR/fixtures.md5") files -> fixtures.md5"

  if _fx_has tenk; then _fx_tenk || return 1; fi
  if _fx_has paced; then pace_set "$PACE_BPS"; ensure_start; fi
  FX_UP=1
}

# name|bytes|date-to-the-minute of every fixture the row made, as the device has them (- for a folder's size).
fx_stat() {
  local names
  names="$(fx_names | sed 's|/$||' | awk '{ printf "\"%s\" ", $0 }')"
  q "cd $QA_FILES && stat -c '%n|%F|%s|%y' $names" | awk -F'|' '{ size = ($2 == "directory") ? "-" : $3; print $1 "|" size "|" substr($4, 1, 16) }'
}

# The zips: make_zips.py (another builder's; python 3.11 zipfile on the host) writes them into gen/zips, they are pushed
# to QA-Files/zips/, and each gets its own date (2026-03-01 + its place in name order, 12:00) — zips.tsv holds
# name, bytes, date and md5 for the rows.
_fx_zips() {
  local out="$FXGEN/zips" host i=0 f day want
  if [ ! -f "$MAKE_ZIPS" ]; then
    _verdict FAIL "files_up zips: scripts/make_zips.py exists" "missing $MAKE_ZIPS — the zip fixtures cannot be made"
    return 1
  fi
  host="$(df -BG --output=avail / | tail -1 | tr -dc '0-9')"
  if [ "${host:-0}" -lt 40 ]; then
    _verdict FAIL "files_up zips: the host has at least 40 GB free" "$host GB free on /"
    return 1
  fi
  # Made once and reused (qa-big.zip is 200 MB of urandom, qa-huge.zip 3 GB of zeros to deflate): remade only when
  # make_zips.py is newer than the last run's stamp.
  if [ ! -f "$out/.made" ] || [ "$MAKE_ZIPS" -nt "$out/.made" ]; then
    rm -rf "$out"; mkdir -p "$out"
    python3 "$MAKE_ZIPS" "$out" > "$out/make_zips.out" 2>&1
    echo $? > "$out/make_zips.rc"
    [ "$(cat "$out/make_zips.rc")" = 0 ] && date -Is > "$out/.made"
  fi
  cp "$out/make_zips.out" "$out/make_zips.rc" "$ROW_DIR/" 2>/dev/null
  assert_eq "files_up zips: make_zips.py exits 0" "0" "$(cat "$ROW_DIR/make_zips.rc" 2>/dev/null)"
  [ "$(cat "$ROW_DIR/make_zips.rc" 2>/dev/null)" = 0 ] || return 1
  if ! ls "$out"/*.zip >/dev/null 2>&1; then
    _verdict FAIL "files_up zips: make_zips.py wrote zips into $out" "none found (its usage may differ: see make_zips.out)"
    return 1
  fi
  adb shell mkdir -p "$QA_FILES/zips"
  adb push "$out"/*.zip "$QA_FILES/zips/" > "$ROW_DIR/zips-push.out" 2>&1
  : > "$ROW_DIR/zips.tsv"
  for f in $(cd "$out" && ls *.zip | LC_ALL=C sort); do
    day="$(date -d "2026-03-01 + $i days" +%Y-%m-%d) 12:00"
    adb shell "touch -d '$day' '$QA_FILES/zips/$f'"
    printf '%s\t%s\t%s\t%s\n' "$f" "$(q "stat -c %s '$QA_FILES/zips/$f'")" "$day" "$(q "md5sum '$QA_FILES/zips/$f'" | cut -d' ' -f1)" >> "$ROW_DIR/zips.tsv"
    i=$((i + 1))
  done
  adb shell "touch -d '2026-02-28 12:00' '$QA_FILES/zips'"
  want="$(cd "$out" && md5sum $(ls *.zip | LC_ALL=C sort) | awk '{ print $2, $1 }')"
  if [ "$want" = "$(awk -F'\t' '{ print $1, $4 }' "$ROW_DIR/zips.tsv")" ]; then
    _verdict PASS "files_up zips: every zip on the device has its host file's md5" "$(grep -c . "$ROW_DIR/zips.tsv") zips (zips.tsv: name, bytes, date, md5)"
  else
    _verdict FAIL "files_up zips: every zip on the device has its host file's md5" "$(diff <(echo "$want") <(awk -F'\t' '{ print $1, $4 }' "$ROW_DIR/zips.tsv") | grep '^[<>]' | head -4 | xargs)"
  fi
}

# The 10,000-file folder (Edge cases, first bullet; E8's walk): f1 … f10000, empty, minute i after 2025-01-01 00:00 UTC
# for f<i> — so f10000 is the newest and f1 the oldest. One device-side script, timed.
_fx_tenk() {
  local t0 t1
  cat > "$ROW_DIR/tenk.sh" <<'SH'
mkdir -p /sdcard/QA-Big && cd /sdcard/QA-Big || exit 1
i=1
while [ $i -le 10000 ]; do : > f$i; i=$((i + 1)); done
i=1
while [ $i -le 10000 ]; do touch -d @$((1735689600 + i * 60)) f$i; i=$((i + 1)); done
SH
  adb push "$ROW_DIR/tenk.sh" /data/local/tmp/p18-tenk.sh >/dev/null 2>&1
  t0="$(date +%s.%N)"
  q "sh /data/local/tmp/p18-tenk.sh; rm -f /data/local/tmp/p18-tenk.sh" > "$ROW_DIR/tenk.out"
  t1="$(date +%s.%N)"
  FX_TENK_S="$(python3 -c "print('%.1f' % ($t1 - $t0))")"
  record "files_up tenk: QA-Big made (10,000 files, 10,000 touch -d), seconds" "$FX_TENK_S"
  assert_eq "files_up tenk: QA-Big holds 10,000 files" "10000" "$(q "find $QA_BIG -type f | wc -l" | xargs)"
  assert_eq "files_up tenk: with 10,000 distinct dates" "10000" "$(q "find $QA_BIG -type f -exec stat -c %Y {} + | sort -u | wc -l" | xargs)"
  assert_eq "files_up tenk: the newest is f10000, the oldest f1" "f10000 f1" \
    "$(q "cd $QA_BIG && find . -type f -exec stat -c '%Y %n' {} + | sort -n | sed -n '1p;\$p' | cut -d/ -f2 | tac" | xargs)"
}

# files_down: the pace pref cleared (when files_up set it), every fixture removed — QA-Files, QA-Big, the media_up
# files, /sdcard/.Tessera only when the snapshot did not hold it, and the harness's own dump scratch (/sdcard/qa.xml,
# lib.sh dump_ui's file) only when the snapshot did not hold it — then BOTH snapshots asserted equal to files_up's, with
# the diff in the verdict and in snap-*.diff. MediaStore drops its rows a moment after the files go, so the comparison
# waits up to 30 s for the two to be equal before it gives its verdict; it never passes on anything but equality.
files_down() {
  local i s f extra
  [ -n "${FX_SNAPPED:-}" ] || { echo "files_down: files_up has not run" >&2; return 1; }
  if _fx_has paced || [ -n "$(pace_now)" ]; then pace_clear; ensure_start; fi
  adb shell rm -rf "$QA_FILES" "$QA_BIG"
  for extra in /sdcard/.Tessera /sdcard/qa.xml; do
    grep -qxF "$extra" "$ROW_DIR/snap-sdcard-before.txt" || adb shell rm -rf "$extra"
  done
  if _fx_has media && [ -n "$MEDIA_MARK" ]; then media_down; else media_scan; fi
  for i in $(seq 1 30); do
    _snap_sdcard > "$ROW_DIR/snap-sdcard-after.txt"
    _snap_files > "$ROW_DIR/snap-files-after.txt"
    cmp -s "$ROW_DIR/snap-sdcard-before.txt" "$ROW_DIR/snap-sdcard-after.txt" && cmp -s "$ROW_DIR/snap-files-before.txt" "$ROW_DIR/snap-files-after.txt" && break
    sleep 1
  done
  for s in sdcard files; do
    f="$ROW_DIR/snap-$s"
    diff "$f-before.txt" "$f-after.txt" > "$f.diff"
    if [ -s "$f.diff" ]; then
      _verdict FAIL "files_down: the $s snapshot equals the one taken before the row" "$(grep -c '^<' "$f.diff") gone, $(grep -c '^>' "$f.diff") new: $(grep '^[<>]' "$f.diff" | head -6 | xargs)"
    else
      _verdict PASS "files_down: the $s snapshot equals the one taken before the row" "$(grep -c . "$f-after.txt") lines, no difference"
    fi
  done
  FX_UP=""; FX_SNAPPED=""; FX_OPTS=""
}

# ---------------------------------------------------------------- "mid" (the Round 3 floor, 3)

# mid_progress <op> <mark> [timeout_s]: polls the ring slice from the MARK for a `[files] <op> progress <bytes>/<total>`
# line with 0 < bytes < total (<op> = copy | move | zip extract | zip create). The first such line is printed and gets a
# PASS; with none inside the timeout (default 30 s) it is a FAIL and non-zero — a row that cannot read one fails rather
# than guessing.
mid_progress() { # op mark [timeout_s]
  local op="$1" mark="$2" timeout="${3:-30}" end line=""
  end=$(( $(date +%s) + timeout ))
  while :; do
    line="$(ring_since "$mark" | python3 -c '
import re, sys
pat = re.compile(r"\[files\] " + re.escape(sys.argv[1]) + r" progress (\d+)/(\d+)")
for raw in sys.stdin:
    m = pat.search(raw)
    if m and 0 < int(m.group(1)) < int(m.group(2)):
        print(raw.strip()); break' "$op")"
    [ -n "$line" ] && break
    [ "$(date +%s)" -ge "$end" ] && break
    sleep 0.3
  done
  if [ -n "$line" ]; then
    _verdict PASS "mid-$op: a progress line with 0 < bytes < total" "$line"
    printf '%s\n' "$line"
    return 0
  fi
  _verdict FAIL "mid-$op: a progress line with 0 < bytes < total" "none in the slice from MARK $mark within $timeout s"
  return 1
}

# ---------------------------------------------------------------- the JVM gate (the Round 3 floor, 6)

# jvm_gate <tests pattern> <report glob> <case>…: gradle's exit code goes to <row>/gradle.rc; the gate asserts rc 0 AND
# that the reports app/build/test-results/testDebugUnitTest/TEST-<report glob>.xml (copied into the row) exist, hold
# 0 failures / errors / skipped over at least one test, and hold every named case passed (precedent
# qa/phase-11/scripts/e14.sh:40-42). Example: jvm_gate '*FileShareGuard*' '*FileShareGuard*' refusesTheAppsOwnFile …
jvm_gate() { # tests-pattern report-glob case…
  local pattern="$1" glob="$2" dir="$REPO/app/build/test-results/testDebugUnitTest" out f
  shift 2
  ( cd "$REPO" && ./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --offline --tests "$pattern" ) > "$ROW_DIR/gradle.out" 2>&1
  echo $? > "$ROW_DIR/gradle.rc"
  assert_eq "JVM gate $pattern: gradle's exit code (gradle.rc)" "0" "$(cat "$ROW_DIR/gradle.rc")"
  mkdir -p "$ROW_DIR/test-reports"
  for f in "$dir"/TEST-$glob.xml; do [ -f "$f" ] && cp "$f" "$ROW_DIR/test-reports/"; done
  out="$(python3 - "$ROW_DIR/test-reports" "$@" <<'PY'
import glob, sys
import xml.etree.ElementTree as ET
files = sorted(glob.glob(sys.argv[1] + "/TEST-*.xml"))
tot = dict(tests=0, failures=0, errors=0, skipped=0)
cases = {}
for f in files:
    suite = ET.parse(f).getroot()
    for k in tot: tot[k] += int(suite.get(k, 0))
    for c in suite.iter("testcase"):
        bad = [t.tag for t in c if t.tag in ("failure", "error", "skipped")]
        cases[c.get("name")] = bad[0] if bad else "passed"
print("reports=%d tests=%d failures=%d errors=%d skipped=%d" % (len(files), tot["tests"], tot["failures"], tot["errors"], tot["skipped"]))
for name in sys.argv[2:]:
    print("case %s: %s" % (name, cases.get(name, "MISSING")))
PY
)"
  note "$(printf '%s' "$out" | tr '\n' ';')"
  assert_ne "JVM gate $pattern: reports TEST-$glob.xml exist" "reports=0" "$(printf '%s\n' "$out" | head -1 | cut -d' ' -f1)"
  assert_ne "JVM gate $pattern: the reports hold tests" "tests=0" "$(printf '%s\n' "$out" | head -1 | cut -d' ' -f2)"
  assert_eq "JVM gate $pattern: 0 failures / errors / skipped" "failures=0 errors=0 skipped=0" "$(printf '%s\n' "$out" | head -1 | cut -d' ' -f3-)"
  for f in "$@"; do
    assert_eq "JVM gate $pattern: case $f" "case $f: passed" "$(printf '%s\n' "$out" | grep -F "case $f: ")"
  done
}

# ---------------------------------------------------------------- the send probe (testapps/qa-capture; E7)

# The lines SendProbeActivity logged since a logcat clear the row made before the share:
#   send action=<action> type=<mime> uri=<uri> md5=<md5|unreadable>      one per stream (SEND: one; SEND_MULTIPLE: each)
#   send streams=<n> action=<action> type=<mime>                         SEND_MULTIPLE only, before its stream lines
probe_lines() { adb logcat -d -s "$QAC_TAG:I" 2>/dev/null | tr -d '\r' | grep -o 'send .*'; }
