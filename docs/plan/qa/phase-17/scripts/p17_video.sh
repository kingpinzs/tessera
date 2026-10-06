#!/usr/bin/env bash
# Phase 17 — what the Movies & TV gate rows share (E11–E14, E20–E22, E19_VIDEO, E23_VIDEO, TRUST_VIDEO, edge_video.sh).
# Sourced AFTER lib.sh and p17.sh. Everything here drives the real shell on the emulator and real fixtures on the host;
# nothing is simulated. The working steps are the builder's development proof's (dev-video/scripts/v17.sh), re-cut to
# the gate's floor: ring_since from a MARK, absent_in, gdump for the player, rings_save before a kill.
#
# THE CATALOGUE FIXTURE'S PORT (INDEX Change Log 2026-10-05 14:27 (2)): port 8090 is held on this PC by another service,
# so catalogue_server.py runs on 8091 and every `10.0.2.2:8090` of E13, E20–E22 reads `10.0.2.2:8091` in these rows.
STAMP_FILES="$STAMP_FILES $P17/scripts/p17_video.sh"
GATE_APK_MD5="95b543037345b851"                 # the clean debug build of phase-17 at 7bd9f961 (355,589,093 bytes; the lead, 19:1x)
VPORT="${P17_VIDEO_PORT:-8091}"
FIXTURE_URL="http://10.0.2.2:$VPORT"
FIXTURE_HOST="10.0.2.2:$VPORT"
DUMMY_TOKEN="qa-dummy-token"
PREFS_EDIT="$P01S/prefs_edit.py"
QAFLIX="app.tileshell.testclient.qaflix"
QAFLIX_APK="$REPO/testapps/qa-flix/build/outputs/apk/debug/qa-flix-debug.apk"
QAVIEW="app.tileshell.testclient.qaview"
QAVIEW_APK="$REPO/testapps/qa-view/build/outputs/apk/debug/qa-view-debug.apk"
JF="$P17/fixtures/jellyfin/jellyfin_fixture.sh"
# The Jellyfin fixture's work folder (container id, volumes, the fixture admin's token): scratch, never the repo and
# never a row folder (leak_scan reads the row folder; the admin's token is a credential of the fixture's).
JF_WORK="${P17_JF_WORK:-/tmp/claude-1000/-home-jeremyking/b5b8c63b-5d38-49e9-84f3-de917a96acb2/scratchpad/qavideo-jf}"
SERVER_HOST="10.0.2.2:8096"; SERVER_USER="qa"; SERVER_PW="qa-password"

# The lock is taken ONCE per process (the lead's note; p17_photos.sh's form): lib.sh's take_device_lock re-opens the
# lock file each time it is called — row_begin calls it again — which lets go of the lock for a moment.
take_device_lock() {
  [ -n "${VIDEO_LOCK_HELD:-}" ] && return 0
  exec 9>"$DEVICE_LOCK"
  if ! flock -n 9; then
    echo "another QA driver is already driving the device (lock $DEVICE_LOCK); refusing to start" >&2
    exit 3
  fi
  VIDEO_LOCK_HELD=1
}

# ---------------------------------------------------------------- the build

# Install this worktree's debug APK when the device holds another build, then assert the gate build's id (the brief).
video_install() { # out-dir
  mkdir -p "$1"
  [ -f "$APK" ] || { echo "no APK at $APK (a clean ./gradlew :app:assembleDebug --offline first)" >&2; exit 4; }
  if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
    adb install -r "$APK" > "$1/install.out" 2>&1 || { echo "adb install -r of $APK failed:" >&2; cat "$1/install.out" >&2; exit 4; }
  fi
}
assert_gate_build() {
  assert_contains "the device holds this worktree's build" "yes" "$(apk_matches)"
  assert_eq "the installed APK is the gate build ($GATE_APK_MD5)" "$GATE_APK_MD5" "$(installed_apk_id)"
}
fixtures_made() { [ -f "$GEN/qa-steps.mp4" ] && [ -f "$GEN/qa-steps.colours" ] || bash "$P17/scripts/make_videos.sh" "$GEN" > /dev/null; }

# ---------------------------------------------------------------- rings and lines

vring() { ring_since "$1" "$VIDEO_RING"; }                 # the :video ring's lines since a MARK
vline() { vring "$1" | grep -F -- "$2" | tail -1; }         # the last such line holding a text
wall_of() { sed -n 's/.*wall=\([0-9]*\).*/\1/p' <<<"$1" | tail -1; }
await_vline() { # mark text [tenths] -> the line, once the :video ring holds it
  local i line=""
  for i in $(seq 1 "${3:-80}"); do line="$(vline "$1" "$2")"; [ -n "$line" ] && break; sleep 0.1; done
  printf '%s' "$line"
}
await_lline() { # mark text [tenths] -> the same on the launcher's ring
  local i line=""
  for i in $(seq 1 "${3:-80}"); do line="$(ring_since "$1" launcher | grep -F -- "$2" | tail -1)"; [ -n "$line" ] && break; sleep 0.1; done
  printf '%s' "$line"
}
# AndroidRuntime lines naming the shell since a MARK (E14's form, r3 V22: logcat's epoch form `-T <s.mmm>`).
crash_since() { # mark(ms)
  adb logcat -d -T "$(( $1 / 1000 )).$(printf '%03d' $(( $1 % 1000 )))" -s AndroidRuntime 2>/dev/null | tr -d '\r' | grep -F 'app.tileshell' | head -5
}
# A secret must not be in a text — and the verdict line must not print the secret either (assert_absent would).
assert_no_secret() { # name secret haystack
  [ -n "$2" ] || { _verdict FAIL "$1" "the secret to look for is empty, so the check proves nothing"; return; }
  case "$3" in *"$2"*) _verdict FAIL "$1" "the secret IS there" ;; *) _verdict PASS "$1" "the secret is not there" ;; esac
}

# ---------------------------------------------------------------- geometry

# A node's bounds in epx (px ÷ 3 on this AVD): "left top right bottom", one decimal.
epx() { # dump.xml resource-id
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo ""; return; }
  # shellcheck disable=SC2086
  python3 -c 'import sys; print(" ".join("%.1f" % (int(v)/3) for v in sys.argv[1:]))' $b
}
centre_px() { # dump.xml resource-id -> "x y"
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo ""; return; }
  # shellcheck disable=SC2086
  set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
# PASS when each number of the actual list is within tol of the expected one.
assert_near() { # name "expected…" "actual…" tol
  local ok
  ok="$(python3 -c 'import sys
e=sys.argv[1].split(); a=sys.argv[2].split()
print("yes" if len(e)==len(a) and len(a)>0 and all(abs(float(x)-float(y))<=float(sys.argv[3]) for x,y in zip(e,a)) else "no")' "$2" "$3" "$4" 2>/dev/null)"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "[$3] within $4 of [$2]"; else _verdict FAIL "$1" "expected [$2] ± $4, got [$3]"; fi
}
node_attr() { # dump.xml resource-id attribute
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if 'resource-id="%s"' % sys.argv[2] in s:
        m = re.search(r'\b%s="([^"]*)"' % re.escape(sys.argv[3]), s)
        print(m.group(1) if m else "")
        break
PY
}

# ---------------------------------------------------------------- the player

colour() { sed -n "$(( $1 + 1 ))p" "$GEN/qa-steps.colours"; }      # colour k of qa-steps.mp4, "r,g,b"
# The player's centre pixel: the screen's centre (the picture is letterboxed about it; the nav bar's 48 epx sit below).
CX=540; CY=1098
# Start the player the way E13 words it (`am start`, the shell uid). On the fixed build that is "another app" for a
# content source (the fixes file, C-M4 leg (vi)); http sources are open to every caller.
view_shell() { # uri [mime]
  adb shell am start -n "$PLAYER_ACTIVITY" -a android.intent.action.VIEW -d "$1" -t "${2:-video/mp4}" >/dev/null 2>&1
}
# E11's pixel rule: a screencap at 3.5 s after the `[video] playing` line (its wall= stamp), the centre pixel's colour.
# The capture is started 300 ms early: `screencap` takes that long to grab its frame on this AVD (recorded each time).
shot_at() { # T0(ms) ms-after out.png -> SHOT_RGB "r,g,b", SHOT_DONE (ms after T0 when the capture returned)
  local t0="$1" at="$2" out="$3"
  while [ "$(device_ms)" -lt $(( t0 + at - 300 )) ]; do sleep 0.03; done
  screencap "$out"
  SHOT_DONE=$(( $(device_ms) - t0 ))
  SHOT_RGB="$(px "$out" "$CX" "$CY")"
}
SHOT_DONE=0; SHOT_RGB=""
# The PlaybackState of the shell's session whose header carries <tag> (dumpsys media_session; phase 15 e0.sh's reader).
session_state() { # tag
  adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
tag = sys.argv[1]; hit = False
for l in sys.stdin:
    if re.match(r"^\s+\S+ \S+/\S+/\d+ \(userId=\d+\)", l):
        hit = " app.tileshell/" in l and tag in l
    elif hit:
        m = re.search(r"state=PlaybackState \{state=([A-Z_]+)", l)
        if m: print(m.group(1)); break' "$1"
}
shell_sessions() { adb shell dumpsys media_session | tr -d '\r' | grep -E 'package=app\.tileshell' | head -3; }
# Show the player's controls (they fade after ≈3.2 s) and pause it; the dump is of the paused page.
show_paused() { # out.xml
  sleep 4.4; adb shell input tap 540 600; sleep 0.5; adb shell input tap 540 2077; sleep 0.6; gdump "$1"
}
# The Videos grant: provision.sh grants it; a row that finds it missing grants it and puts it back as it was.
VIDEOS_WAS=""
videos_grant() {
  VIDEOS_WAS="$(perm_granted READ_MEDIA_VIDEO)"
  [ "$VIDEOS_WAS" = true ] || adb shell pm grant app.tileshell android.permission.READ_MEDIA_VIDEO
  record "READ_MEDIA_VIDEO before the row (granted for it when missing, put back at its end)" "$VIDEOS_WAS"
}
videos_grant_restore() { [ "$VIDEOS_WAS" = false ] && adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO; return 0; }

# ---------------------------------------------------------------- the hub

hub() { adb shell am start -W -n "$VIDEO_ACTIVITY" -a android.intent.action.VIEW --es page "$1" >/dev/null 2>&1; sleep "${2:-2.5}"; }
# The pane row that is current on the page now shown (the pane is opened, read and closed).
pane_current() { # dump-prefix
  dump_ui "$1.xml"; tap_node "$1.xml" hub_menu; sleep 0.9; dump_ui "$1-pane.xml"
  grep -o '<node[^>]*resource-id="hub_pane:[a-z]*"[^>]*>' "$1-pane.xml" | grep 'selected="true"' | sed 's/.*resource-id="hub_pane:\([a-z]*\)".*/\1/' | xargs
  adb shell input tap 960 1200; sleep 0.6
}
# Open a video from the My videos page (the shell's own launch: the player takes it with the shell's own access).
play_from_hub() { # media id, dump-prefix -> 0 when the tile was tapped
  hub myvideos 2.5
  scroll_to_node "$2-mine.xml" "video_tile:$1" 12 || return 1
  tap_node "$2-mine.xml" "video_tile:$1"
}

# ---------------------------------------------------------------- the catalogue fixture (host, port $VPORT)

FIXTURE_LOG=""
fixture_up() { # [video file]
  FIXTURE_LOG="$ROW_DIR/fixture.log"; : > "$FIXTURE_LOG"
  python3 "$P17/scripts/catalogue_server.py" --port "$VPORT" --video "${1:-$GEN/qa-steps.mp4}" --log "$FIXTURE_LOG" >/dev/null 2>"$ROW_DIR/fixture.err" &
  echo $! > "$ROW_DIR/fixture.pid"
  local i; for i in $(seq 1 40); do curl -s -o /dev/null "http://127.0.0.1:$VPORT/ready" && { : > "$FIXTURE_LOG"; return 0; }; sleep 0.2; done
  echo "the catalogue fixture did not start: $(cat "$ROW_DIR/fixture.err")" >&2; return 1
}
fixture_down() { [ -f "$ROW_DIR/fixture.pid" ] && kill "$(cat "$ROW_DIR/fixture.pid")" 2>/dev/null; rm -f "${ROW_DIR:?}/fixture.pid"; return 0; }
# The same server again after a stop, its log kept (the offset form reads one growing file).
fixture_again() {
  python3 "$P17/scripts/catalogue_server.py" --port "$VPORT" --video "$GEN/qa-steps.mp4" --log "$FIXTURE_LOG" >/dev/null 2>>"$ROW_DIR/fixture.err" &
  echo $! > "$ROW_DIR/fixture.pid"
  local i; for i in $(seq 1 40); do curl -s -o /dev/null "http://127.0.0.1:$VPORT/ready" && return 0; sleep 0.2; done
  return 1
}
fixture_lines() { wc -l < "$FIXTURE_LOG" | tr -d ' '; }             # the log's length on the host, as an offset
fixture_since() { tail -n +"$(( $1 + 1 ))" "$FIXTURE_LOG"; }        # the lines after an offset

airplane() { # enable|disable — and, on disable, wait until the host answers again
  adb shell cmd connectivity airplane-mode "$1" >/dev/null
  if [ "$1" = disable ]; then
    local i; for i in $(seq 1 60); do adb shell ping -c 1 -W 1 10.0.2.2 >/dev/null 2>&1 && return 0; sleep 0.5; done
    echo "the network did not come back after airplane mode" >&2; return 1
  fi
  sleep 1.5
}
airplane_now() { adb shell settings get global airplane_mode_on | tr -d '\r'; }

# One debug-only QA pref (qa_catalogue_base, qa_wikidata_base, qa_server_base) in start_theme.xml, set or removed with
# prefs_edit.py. The shell is stopped first and another app is in front, so no process of the shell rewrites the file.
qa_pref() { # key value|--remove
  rings_save
  adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
  adb shell am force-stop app.tileshell; sleep 0.5
  adb shell run-as app.tileshell cat shared_prefs/start_theme.xml > "$ROW_DIR/.prefs-in.xml" 2>/dev/null
  python3 "$PREFS_EDIT" "$ROW_DIR/.prefs-in.xml" "$1" string "$2" > "$ROW_DIR/.prefs-out.xml"
  adb shell "run-as app.tileshell sh -c 'cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/.prefs-out.xml"
}
qa_pref_now() { adb shell run-as app.tileshell cat shared_prefs/start_theme.xml 2>/dev/null | tr -d '\r' | sed -n "s/.*name=\"$1\">\([^<]*\)<.*/\1/p"; }

# The names the credential file holds (never a value): "jellyfin tmdb", or empty.
cred_names() { adb shell run-as app.tileshell cat files/credentials_v1.json 2>/dev/null | python3 -c 'import json,sys
try: print(" ".join(sorted(json.load(sys.stdin).keys())))
except Exception: print("")'; }
# Type the dummy TMDB token into the key setting (its page on screen) and save it.
key_type_and_save() { # dump-prefix
  dump_ui "$1-key.xml"; tap_node "$1-key.xml" tmdb_key_field; sleep 0.8
  adb shell input text "$DUMMY_TOKEN"; sleep 0.8
  dump_ui "$1-typed.xml"
  tap_node "$1-typed.xml" tmdb_key_save; sleep 1.2
  dump_ui "$1-saved.xml"
}
# Open settings > TMDB key and save the dummy token (E21 / E22 / E23 / the edges: "as E20 enters it").
key_enter() { # dump-prefix
  hub settings 2; dump_ui "$1-settings.xml"; tap_node "$1-settings.xml" hub_settings:tmdbkey; sleep 1
  key_type_and_save "$1"
  assert_eq "the TMDB key setting says a key is saved" "A key is saved." "$(node_text "$1-saved.xml" tmdb_key_status)"
}
key_remove_if_saved() { # removes the TMDB key through the setting, when one is saved
  [[ " $(cred_names) " == *" tmdb "* ]] || return 0
  hub settings 2; dump_ui "$ROW_DIR/.rm1.xml"; tap_node "$ROW_DIR/.rm1.xml" hub_settings:tmdbkey; sleep 1
  dump_ui "$ROW_DIR/.rm2.xml"; tap_node "$ROW_DIR/.rm2.xml" tmdb_key_remove; sleep 1.2
}
# Browse, the search typed when its rows are not already there (the page re-runs its last search on open).
search_blade_runner() { # dump (out)
  hub browse 3; dump_ui "$1"
  if [ "$(has_node "$1" hub_result:78)" != yes ]; then
    tap_node "$1" hub_search_box; sleep 0.8
    adb shell input text "Blade%sRunner"; sleep 0.5
    adb shell input keyevent KEYCODE_ENTER; sleep 3
    dump_ui "$1"
  fi
}
result_ids() { grep -o 'resource-id="hub_result:[a-z0-9-]*"' "$1" | sed 's/.*hub_result://;s/"//' | xargs; }

# ---------------------------------------------------------------- the media-server fixture (the pinned Jellyfin)

jf() { bash "$JF" "$1" "$JF_WORK" "${@:2}"; }
jf_up() { # the container, seeded; its image and id into the row log (T17-21). Nothing of its work folder is in the row.
  [ -f "$JF_WORK/container.id" ] && jf down >/dev/null
  jf up "$GEN/qa-steps.mp4" > "$ROW_DIR/jellyfin-up.txt" 2>&1 || return 1
  record "the media-server fixture's image (the pinned digest, T17-21)" "$(sed -n 's/^image //p' "$ROW_DIR/jellyfin-up.txt" | head -1)"
  record "its container" "$(cut -c1-12 "$JF_WORK/container.id")"
  SERVER_ITEM="$(cat "$JF_WORK/item.id")"
}
# (${JF_WORK:?}: the shell stops rather than run a removal on a path built from an empty name.)
jf_down() { jf down > "$ROW_DIR/jellyfin-down.txt" 2>&1; rm -rf "${JF_WORK:?}/media" "${JF_WORK:?}/seed.log"; rmdir "${JF_WORK:?}" 2>/dev/null; return 0; }
jf_wait() { local i; for i in $(seq 1 90); do [ "$(curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8096/System/Info/Public)" = 200 ] && { sleep 3; return 0; }; sleep 1; done; return 1; }
# Type a server sign-in into the form that is on screen and tap Connect (the password goes through the keyboard).
server_form() { # dump-prefix host user password
  dump_ui "$1-form.xml"
  tap_node "$1-form.xml" server_host; sleep 0.6; for _ in $(seq 1 44); do adb shell input keyevent KEYCODE_DEL; done; adb shell input text "$2"; sleep 0.4
  tap_node "$1-form.xml" server_user; sleep 0.6; for _ in $(seq 1 12); do adb shell input keyevent KEYCODE_DEL; done; adb shell input text "$3"; sleep 0.4
  tap_node "$1-form.xml" server_password; sleep 0.6; adb shell input text "$4"; sleep 0.4
  adb shell input keyevent KEYCODE_BACK; sleep 0.6          # the keyboard away, so Connect is on screen
  dump_ui "$1-typed.xml"
  tap_node "$1-typed.xml" server_connect
}
server_page() { hub settings 2; dump_ui "$1-settings.xml"; tap_node "$1-settings.xml" hub_settings:server; sleep 1; }
server_remove_if_saved() {
  [[ " $(cred_names) " == *" jellyfin "* ]] || [ -n "$(adb shell run-as app.tileshell cat files/media_server.json 2>/dev/null)" ] || return 0
  server_page "$ROW_DIR/.srm"; dump_ui "$ROW_DIR/.srm2.xml"
  [ "$(has_node "$ROW_DIR/.srm2.xml" server_remove)" = yes ] && { tap_node "$ROW_DIR/.srm2.xml" server_remove; sleep 1.5; }
  return 0
}
shortcut_dump() { adb shell dumpsys shortcut | tr -d '\r' | awk '/Package: app.tileshell /,/Package: [^a]/'; }
# The shell's shortcuts as "<activity>\t<rank>\t<id>\t<flags>" lines.
shortcut_table() { # out.tsv
  adb shell dumpsys shortcut | tr -d '\r' > "$1.txt"
  python3 - "$1.txt" > "$1" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r"\n\s+Package: app\.tileshell\s+UID:.*?(?=\n\s+Package: |\Z)", text, re.S)
block = m.group(0) if m else ""
for s in re.finditer(r"ShortcutInfo \{id=([^,]+), flags=(0x[0-9a-f]+) \[([^\]]*)\].*?activity=ComponentInfo\{([^}]+)\}.*?rank=(\d+)", block, re.S):
    print("%s\t%s\t%s\t%s" % (s.group(4), s.group(5), s.group(1), s.group(3)))
PY
}

# ---------------------------------------------------------------- the form every Movies & TV row shares

# The row's opening: the lock, this worktree's build, the stamp, the gate build's id, an awake device, the fixtures.
video_row_begin() { # id description
  take_device_lock
  video_install "$QA/$1"
  row_begin "$1" "$2"
  D="$ROW_DIR"
  assert_gate_build
  assert_eq "wake" "Awake" "$(wake_device)"
  fixtures_made
  anr_dismiss
}
# "<app> isn't responding" (the emulator's own, seen once on 2026-10-05 after the resolver): Wait is tapped and the
# fact recorded, so a row never taps into the system's dialog and reads a verdict about nothing.
anr_dismiss() {
  local d="$ROW_DIR/.anr.xml"
  gdump "$d" >/dev/null 2>&1 || return 0
  if [ "$(has_node "$d" android:id/aerr_wait)" = yes ]; then
    record "a system 'isn't responding' dialog was up (Wait tapped)" "$(node_text "$d" android:id/alertTitle)"
    tap_node "$d" android:id/aerr_wait; sleep 1
  fi
}
# The DEVICE's media volume at 0 for a row that plays sound (the emulator's output is the host's speakers; no host
# audio setting is touched), and put back.
VOL_WAS=""
vol_now() { adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+'; }
quiet_on() { VOL_WAS="$(vol_now)"; adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1; record "device media volume before the row (0 for the row, put back at its end)" "${VOL_WAS:-?}"; }
quiet_off() { [ -n "$VOL_WAS" ] && adb shell cmd media_session volume --stream 3 --set "$VOL_WAS" >/dev/null 2>&1; assert_eq "device media volume put back" "${VOL_WAS:-?}" "$(vol_now)"; }

# E11's pixel rule ON THE EMULATOR, as the lead ruled it (INDEX Change Log 2026-10-06): the centre pixel is nearer to
# colour k than to any other of the fixture's ten colours, AND within ± 20 per channel of colour k, with the measured
# value recorded. The doc's ± 8 does not hold on this AVD — its decoder lifts every dark channel by 14 to 17 and white
# by 1 (E11's ten measurements; the ± 8 runs are kept on disk) — so the old comparison stays as a RECORD line.
nearest_colour() { # "r,g,b" -> the index 0..9 of the nearest of qa-steps.colours, and the largest channel difference to it
  python3 - "$1" "$GEN/qa-steps.colours" <<'PY'
import sys
try: p = [int(v) for v in sys.argv[1].split(",")]
except ValueError: p = []
if len(p) != 3: print("none 999"); sys.exit()
cols = [[int(v) for v in l.split(",")] for l in open(sys.argv[2]) if l.strip()]
best = min(range(len(cols)), key=lambda i: sum((a - b) ** 2 for a, b in zip(cols[i], p)))
print(best, max(abs(a - b) for a, b in zip(cols[best], p)))
PY
}
assert_pixel_rule() { # name k "r,g,b"
  local near old; near="$(nearest_colour "$3")"
  assert_eq "$1: the centre pixel is nearer to colour $2 than to any other of the fixture's ten colours" "$2" "${near%% *}"
  assert_rgb "$1: … and within ± 20 per channel of colour $2 (the pixel rule as ruled for the emulator, 2026-10-06)" "$(colour "$2")" "$3" 20
  record "$1: the measured centre pixel, and its largest channel difference to colour $2 ($(colour "$2"))" "$3 / ${near##* }"
  old="$(python3 -c 'import sys
e=[int(v) for v in sys.argv[1].split(",")]; a=[int(v) for v in sys.argv[2].split(",")] if sys.argv[2].count(",")==2 else None
print("holds" if a and all(abs(x-y)<=8 for x,y in zip(e,a)) else "does NOT hold")' "$(colour "$2")" "$3")"
  record "$1: the doc's own ± 8 per channel (kept visible, not graded)" "$old"
}
record_pixel() { # name k "r,g,b" — a RECORDED sub-row (E14's vp9 / hevc)
  local near; near="$(nearest_colour "$3")"
  record "$1: centre pixel (colour $2 is $(colour "$2"))" "$3 — nearest colour ${near%% *}, largest channel difference ${near##* }"
}

# Another app that MAY read a MediaStore video (testapps/qa-view holding READ_MEDIA_VIDEO) starts the player with a
# VIEW. Since the trust fixes the shell uid's own `am start` of a content item is refused (C-M4 leg (vi), recorded by
# TRUST_VIDEO): a row whose clause starts the player with a VIEW of a content item uses this app instead and says so.
# The reader starts the player WITH ITS IDENTITY SHARED (ActivityOptions.setShareIdentityEnabled): the one access rule
# (Decisions 2026-10-05 19:09) admits a NAMED starter on MediaStore's own answer for it; an app that only holds the
# permission and does not share its identity is refused — the platform's launch answer is "denied" for any MediaStore
# item (TRUST_VIDEO's leg (v) asserts that refusal).
reader_up() {
  [ -f "$QAVIEW_APK" ] || { _verdict FAIL "the QA View fixture APK" "missing: ./gradlew :testapps:qa-view:assembleDebug --offline"; return 1; }
  adb install -r "$QAVIEW_APK" > "$ROW_DIR/qaview-install.out" 2>&1
  adb shell pm grant "$QAVIEW" android.permission.READ_MEDIA_VIDEO
  adb shell pm grant "$QAVIEW" android.permission.READ_MEDIA_AUDIO
}
reader_down() { adb uninstall "$QAVIEW" >/dev/null 2>&1; assert_eq "the QA View fixture app is uninstalled" "" "$(adb shell pm list packages "$QAVIEW" | tr -d '\r')"; }
qaview() { # extras… (am start arguments after the component)
  adb shell am start -n "$QAVIEW/.ViewProbeActivity" "$@" >/dev/null 2>&1
}
qaview_log() { adb logcat -d -s TileShellQa 2>/dev/null | tr -d '\r' | grep -F 'qa-view:' | tail -"${1:-4}"; }
# Open a MediaStore video in the player: from My videos when its tile is there (the shell's own launch), else through
# the reader app. OPENED_BY says which.
OPENED_BY=""
open_video() { # media id, dump-prefix
  if play_from_hub "$1" "$2"; then OPENED_BY="the My videos tile"; else
    OPENED_BY="a VIEW from the reader app, its identity shared (no tile on My videos)"
    qaview --es uri "content://media/external/video/media/$1" --ez share true
  fi
}

# After a restart inside the egress guard (the preamble; r3 V2): the launcher's slice holds the weather refresh ending
# with no coordinates and no fetch line — the location grants are revoked for the span, so no packet leaves for weather.
guard_restart() { # name — rings saved, force-stop, Home, the two assertions on the slice from a MARK before the stop
  local mark line
  rings_save
  mark="$(ring_mark)"
  adb shell am force-stop app.tileshell; sleep 1
  ensure_start
  line="$(await_lline "$mark" "[weather] refresh ended without new data: no coordinates" 200)"
  assert_contains "$1: after the restart inside the guard, [weather] refresh ended without new data: no coordinates" "[weather] refresh ended without new data: no coordinates" "$line"
  absent_in "$1: … and no [weather] fetch provider= line" "[weather] fetch provider=" "$(ring_since "$mark" launcher)"
}

# A row's EXIT trap: whatever was left on by a driver that died mid-way is undone — the guard's rule and root, airplane
# mode, the fixture server, the container. A normal run has already undone each (the trap then finds nothing to do).
GUARD_ON=0
guard_on() { egress_guard_on; GUARD_ON=1; record "inside the guard: adbd's uid (root for the span)" "$(adb shell id -u | tr -d '\r')"; }
guard_off() { egress_guard_off; GUARD_ON=0; assert_eq "adbd is back to the shell uid (adb unroot)" "2000" "$(adb shell id -u | tr -d '\r')"; }
video_cleanup() {
  if [ "$GUARD_ON" = 1 ] && [ -n "$APP_UID" ]; then
    adb shell iptables -D OUTPUT -m owner --uid-owner "$APP_UID" ! -d 10.0.2.2 -j REJECT >/dev/null 2>&1
    adb unroot >/dev/null 2>&1; adb wait-for-device
    adb shell pm grant app.tileshell android.permission.ACCESS_COARSE_LOCATION; adb shell pm grant app.tileshell android.permission.ACCESS_FINE_LOCATION 2>/dev/null
    echo "video_cleanup: the egress guard was still on at exit; removed, unrooted, location granted back" | tee -a "${LOG:-/dev/null}"
  fi
  [ "$(airplane_now)" = 1 ] && { airplane disable; echo "video_cleanup: airplane mode was still on at exit; off" | tee -a "${LOG:-/dev/null}"; }
  [ -n "${ROW_DIR:-}" ] && fixture_down
  [ -f "$JF_WORK/container.id" ] && jf_down
  [ -n "${EXTRA_PIDS:-}" ] && kill $EXTRA_PIDS 2>/dev/null
  return 0
}

# Browse with the search "Blade Runner" run: the page re-runs its last search on open (the cache folder keeps it);
# when it did not (no line for the query since the MARK), the query is typed and sent.
browse_blade_runner() { # mark dump(out) [settle]
  hub browse "${3:-3}"; dump_ui "$2"
  if [ -z "$(vline "$1" 'catalogue "Blade Runner"')" ]; then
    tap_node "$2" hub_search_box; sleep 0.8
    case "$(node_text "$2" hub_search_box)" in *"Blade Runner"*) : ;; *) adb shell input text "Blade%sRunner"; sleep 0.5 ;; esac
    adb shell input keyevent KEYCODE_ENTER; sleep 3
    dump_ui "$2"
  fi
}
# The leak scan every credential row ends with (gated): the row's folder — its log, dumps, saved ring slices — and
# `adb logcat -d`, for the secrets given and any tmdb.* value local.properties still holds. Its output names files
# only; it is kept in a scratch file until the scan is over (so the scan does not read its own report) and then
# copied beside the row.
leak_scan_row() { # secrets…
  local tmp rc
  rings_save
  tmp="$(mktemp)"
  bash "$P17/scripts/leak_scan.sh" --logcat --path "$ROW_DIR" -- "$@" > "$tmp" 2>&1; rc=$?
  cp "$tmp" "$ROW_DIR/leak_scan.txt"; rm -f "$tmp"
  assert_eq "leak scan (leak_scan.sh --logcat --path <the row's folder> -- <$# secrets>): nothing matched" "0" "$rc"
  record "leak scan: clean / MATCH lines" "$(grep -c '^clean' "$ROW_DIR/leak_scan.txt") / $(grep -c '^MATCH' "$ROW_DIR/leak_scan.txt")"
}

# Files under the shell's data that hold a text, read AS ROOT (E22's own clause: `adb root`, `grep -rlF … /data/data/
# app.tileshell/`). Only inside the guard's span, where adbd is root. Prints file names, never the text.
root_files_holding() { # text
  [ "$(adb shell id -u | tr -d '\r')" = 0 ] || { echo "(adbd is not root: the grep did not run)"; return; }
  adb shell "grep -rlF -- '$1' /data/data/app.tileshell/ /data/user_de/0/app.tileshell/ 2>/dev/null" | tr -d '\r' | head -5
}
# "Add a server" with the fixture's account; asserts the connected line. Leaves the Media server page up.
server_add() { # dump-prefix
  local mark; mark="$(ring_mark)"
  server_page "$1"
  server_form "$1" "$SERVER_HOST" "$SERVER_USER" "$SERVER_PW"; sleep 4.5
  dump_ui "$1-library.xml"
  SERVER_SLICE="$(vring "$mark")"
  assert_contains "Add a server: [video] server $SERVER_HOST: connected" "[video] server $SERVER_HOST: connected" "$SERVER_SLICE"
}

# ---------------------------------------------------------------- the fake media server (fixtures/jellyfin/fake_jellyfin.py)
FAKE_PORT=8097; FAKE_HOST="10.0.2.2:$FAKE_PORT"
FAKE_ITEM="00112233445566778899aabbccddeeff"
FAKE_TOKEN="qafaketoken0123456789abcdef012345"      # the fixture's own value (fake_jellyfin.py); never printed by a row
EXTRA_PIDS=""
fake_up() { # port log [mode]
  python3 "$P17/fixtures/jellyfin/fake_jellyfin.py" --port "$1" --video "$GEN/qa-steps.mp4" --log "$2" --mode "${3:-ok}" 2>>"$ROW_DIR/fake.err" &
  local pid=$! i
  EXTRA_PIDS="$EXTRA_PIDS $pid"; echo "$pid" > "$ROW_DIR/fake-$1.pid"
  for i in $(seq 1 40); do curl -s -o /dev/null "http://127.0.0.1:$1/__ready" && return 0; sleep 0.2; done
  return 1
}
fake_down() { [ -f "$ROW_DIR/fake-$1.pid" ] && kill "$(cat "$ROW_DIR/fake-$1.pid")" 2>/dev/null; rm -f "${ROW_DIR:?}/fake-$1.pid"; return 0; }
fake_mode() { curl -s -o /dev/null "http://127.0.0.1:$FAKE_PORT/__mode/$1"; }
flines() { [ -f "$1" ] && wc -l < "$1" | tr -d ' ' || echo 0; }
fsince() { [ -f "$1" ] && tail -n +"$(( $2 + 1 ))" "$1"; }
# Put a text on the device's clipboard through the QA View app (base64, so a control character survives), and paste
# it into the focused field (KEYCODE_PASTE). The text is never in a command line in the clear, and never logged.
clip_set() { adb shell am start -W -n "$QAVIEW/.ViewProbeActivity" --es clip_b64 "$(printf '%s' "$1" | base64 -w0)" >/dev/null 2>&1; sleep 1.5; }
clip_clear() { adb shell am start -W -n "$QAVIEW/.ViewProbeActivity" --ez clip_clear true >/dev/null 2>&1; sleep 1.2; }
# logcat since a MARK (every tag), in logcat's epoch form. The device's log is never cleared: other rows read it too,
# and a leak scan at a row's end must still see everything the row did.
logcat_since() { adb logcat -d -T "$(( $1 / 1000 )).$(printf '%03d' $(( $1 % 1000 )))" 2>/dev/null | tr -d '\r'; }
