#!/usr/bin/env bash
# L18-4 — phase 10's Music, the exported session under a hostile controller (the INDEX Blocked-on ledger's row L18-4;
# the adversarial review pass 2's N3, and pass 3's P3-1). Driven from testapps/qa-capture (no permission). Legs are
# selectable: l18_4.sh [a b]; the default run is `a` alone.
#
#   a (N3)   from qa-capture, a Media3 `MediaController` sends `setMediaItems([<a real library id>, "x"], 1, 0)` to the
#            shell's exported MusicService; one item survives the library rule, so start index 1 is out of range. SAFE:
#            the player is NOT wedged — Music's own play / pause keeps driving it (the session state changes on each
#            tap), the shell's process stays alive, and the request's `[music]` line is written. MUSIC6 fixtures.
#            Positive control (L18-5's bound must not refuse the shell's own seeks): Music's own next-track control
#            changes the playing track, and no `[music] … refused` line is written for it.
#   b (P3-1) from qa-capture, a LEGACY controller (framework MediaBrowser + MediaController) calls
#            `skipToQueueItem(2147483648)`, then `4294967294`, then `4294967295` (becomes -1, must be ignored). Media3
#            1.9.0's legacy stub refuses only queueId < 0 then l2i-casts into seekToDefaultPosition, and its dispatch
#            catches only RemoteException. SAFE: the launcher's pid is unchanged after each, logcat holds no FATAL for
#            app.tileshell, and the session's player wrote `[music] controller uid <qa-capture's uid>:
#            seekToDefaultPosition(<the cast index>) refused, the queue has <n> item(s)` (ledger L18-5's fix, gate build
#            23fe18a8: -2147483648, -2 and -1). On a build without the fix it crashes; the leg restarts the shell after a
#            crash so the next id (and the next row) still runs. Selectable alone.
#
# A crash of the shell's process while it hosts Start (the HOME activity) makes Android clear the preferred home
# activity (the HOME role stays the shell's): Home then raises "Select a Home app" (android/ResolverActivity) and
# ensure_start reads no start_page — the 4-of-4 `ensure_start: Start alone` failures on b1a22a1d. Proven on
# tileshell_fhd, 2026-10-20 (RESUME-2026-10-20/runner2/probe-crash-home: `am crash app.tileshell` with Music on top →
# resolve-activity HOME = ResolverActivity; `cmd package set-home-activity` → Start again; an uninstall re-derives it
# from the role, which is why the restore's last ensure_start passed after qa-capture was removed). home_back puts the
# preferred home back the way provision.sh sets it, says so in the log, and the restore asserts Home resolves to Start.
#
# No host audio is touched: the emulator runs with QEMU_AUDIO_DRV=none; playback is read from `dumpsys media_session`
# and the shell's own `[music]` lines, never from the host.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
LEGS="${*:-a}"
leg() { case " $LEGS " in *" $1 "*) log "--- leg $1 ($(date '+%H:%M:%S'))"; return 0 ;; *) return 1 ;; esac; }
PROBE="$QAC/.MediaProbeActivity"
LQ="$QAC/.LegacyQueueActivity"
MUSIC_ACT=app.tileshell/.music.MusicActivity
MUSICDIR=/sdcard/Music/tessera-qa
pid_now() { q 'pidof app.tileshell'; }
music_session() { session_state | grep -v session.id.video; }
music_playing() { music_session | grep -c 'state=PLAYING'; }
music_state() { music_session | grep -oE 'state=[A-Z]+' | head -1 | cut -d= -f2; }
probe_out() { adb logcat -d -s "$QAC_TAG:I" 2>/dev/null | tr -d '\r' | grep -o "$1 .*"; }
HOME_ACT=app.tileshell/.StartActivity
home_resolves() { q 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -1; }
# After a crash (see the header): the chooser dismissed, the preferred home activity set again (provision.sh's step).
home_back() {
  local r; r="$(home_resolves)"
  [ "$r" = "$HOME_ACT" ] && return 0
  [ "$(top_activity)" = android/com.android.internal.app.ResolverActivity ] && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
  adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1
  record "home_back: Home resolved to [$r] (the crash cleared the preferred home activity); set-home-activity, as provision.sh" "now [$(home_resolves)]"
}

# Open Music cold, switch to the songs pivot, play the first song → now-playing.
music_play_first() { # dump-prefix -> echoes the played track id
  adb shell am force-stop app.tileshell; sleep 1
  adb shell "am start -n $MUSIC_ACT" >/dev/null 2>&1; sleep 4
  D "$1-coll"
  if [ "$(H "$1-coll" music_pivot_header:songs)" = yes ]; then tap_node "$ROW_DIR/$1-coll.xml" music_pivot_header:songs; sleep 2; fi
  D "$1-songs"
  local sid; sid="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/$1-songs.xml" | head -1 | sed 's/resource-id="music_song://;s/"$//')"
  if [ -n "$sid" ]; then tap_node "$ROW_DIR/$1-songs.xml" "music_song:$sid"; sleep 4; fi
  echo "$sid"
}

keep_earlier_run L18_4
row_begin L18_4 "Music's exported session: a hostile setMediaItems never wedges the player; a legacy skipToQueueItem never crashes it"
record "the installed APK (md5); legs a and b assert the gate build (L18-5 fixed in 23fe18a8)" "$(gate_apk_installed)"
baseline_start

# ------------------------------------------------------------------------------------------------ qa-capture + the MUSIC6 library
QAC_AT_BEGIN="$(q "pm path $QAC")"
if [ ! -f "$QAC_APK" ]; then
  _verdict FAIL "qa-capture is built" "missing $QAC_APK (./gradlew :testapps:qa-capture:assembleDebug --offline)"
  row_end; exit 1
fi
adb install -r "$QAC_APK" > "$ROW_DIR/qa-capture-install.out" 2>&1
assert_contains "qa-capture installed" "Success" "$(cat "$ROW_DIR/qa-capture-install.out")"
QAC_UID="$(q "cmd package list packages -U $QAC" | sed -n "s/^package:$QAC uid://p" | head -1)"
assert_ne "the controller's uid is known" "" "$QAC_UID"

AUDIO_WAS="$(q "dumpsys package app.tileshell" | grep -c 'android.permission.READ_MEDIA_AUDIO: granted=true')"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
# MUSIC6: the six tagged MP3s the existing Music rows use (p18.sh MUSIC6), pushed and scanned into MediaStore.
AUDIO_BEFORE="$(q "content query --uri content://media/external/audio/media --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tr '\n' ' ')"
adb shell "mkdir -p $MUSICDIR" >/dev/null 2>&1
for f in "$MUSIC6"/*.mp3; do adb push "$f" "$MUSICDIR/" >/dev/null 2>&1; done
adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 3
# Polled (up to 10 s more): on b1a22a1d's first run the scan had not yet given the pushed files their rows at 3 s.
for _ in 1 2 3 4 5; do
  REAL_ID="$(q "content query --uri content://media/external/audio/media --projection _id:_data" | grep -F "/Music/tessera-qa/" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | sort -n | head -1)"
  [ -n "$REAL_ID" ] && break; sleep 2
done
assert_ne "a real library id exists (a MUSIC6 track in MediaStore)" "" "$REAL_ID"
record "the library track id the probe points at (and its count)" "$REAL_ID (library size $(q "content query --uri content://media/external/audio/media --projection _id" | grep -c '_id='))"

# ------------------------------------------------------------------------------------------------ a (N3)
if leg a; then
  SID="$(music_play_first a)"
  record "a: Music's own play — the song tapped / the session" "$SID / $(music_session | xargs)"
  assert_eq "a: Music is playing before the probe (a song was tapped)" "1" "$(music_playing)"
  P0="$(pid_now)"
  adb logcat -c; M="$(ring_mark)"
  adb shell "am start -n $PROBE --es tag n3 --esa items $REAL_ID,x --ei start 1 --el pos 0" > "$ROW_DIR/a-probe.start.txt" 2>&1
  sleep 6
  probe_out "media n3" > "$ROW_DIR/a-probe.txt"; sed 's/^/      /' "$ROW_DIR/a-probe.txt" >> "$LOG"
  SL="$(ring_since "$M")"; printf '%s\n' "$SL" | grep -F '[music]' > "$ROW_DIR/a-probe.ring.txt"
  assert_contains "a: the probe's setMediaItems ran (setitems ... result=ok)" "setitems count=2 start=1 result=ok" "$(cat "$ROW_DIR/a-probe.txt")"
  assert_contains "a: SAFE — the session re-derived the start (the request's [music] line): start index 1 -> 0" \
    "[music] controller uid $QAC_UID: start index 1 -> 0 (1 of its 2 item(s) from the library)" "$SL"
  assert_eq "a: SAFE — the shell's process is alive (same pid), no crash" "$P0 0" "$(pid_now) $(crash)"
  # Music's own play/pause still drives the player: reopen now-playing and toggle, reading the session each tap.
  adb shell "am start -n $MUSIC_ACT" >/dev/null 2>&1; sleep 3; D a-np
  if [ "$(H a-np nowplaying_control:play_pause)" != yes ]; then
    # Not on now-playing (the probe replaced the queue): tap the first song again to reach the transport.
    music_play_first a2 >/dev/null; D a-np
  fi
  S0="$(music_state)"
  tap_node "$ROW_DIR/a-np.xml" nowplaying_control:play_pause 2>/dev/null; sleep 2; S1="$(music_state)"
  D a-np2; tap_node "$ROW_DIR/a-np2.xml" nowplaying_control:play_pause 2>/dev/null; sleep 2; S2="$(music_state)"
  record "a: Music's own play/pause after the probe — session state per tap" "$S0 -> $S1 -> $S2"
  assert_ne "a: SAFE — the first tap changes the session state (the player still follows the UI)" "$S0" "$S1"
  assert_ne "a: SAFE — the second tap changes it again (not wedged)" "$S1" "$S2"
  assert_eq "a: SAFE — still no crash after driving play/pause" "0" "$(crash)"
  # Positive control (L18-5): the session's bound must not refuse the shell's own seeks. A fresh queue (the songs pivot,
  # six tracks), then Music's own next-track control: the playing track changes. The mark is taken BEFORE the cold
  # start (music_play_first force-stops the shell, which empties its ring), so the slice holds the service's start line.
  rings_save; M2="$(ring_mark)"
  music_play_first a3 >/dev/null; D a-next0
  assert_eq "a: positive control — now-playing shows Music's own next-track control" "yes" "$(H a-next0 nowplaying_control:next)"
  T0="$(music_session | grep -o 'desc=\[.*\]')"
  tap_node "$ROW_DIR/a-next0.xml" nowplaying_control:next 2>/dev/null; sleep 3
  T1="$(music_session | grep -o 'desc=\[.*\]')"
  SL2="$(ring_since "$M2")"; printf '%s\n' "$SL2" | grep -F '[music]' > "$ROW_DIR/a-next.ring.txt"
  record "a: Music's own next-track control — the session's track before / after the tap" "$T0 -> $T1"
  assert_ne "a: positive control — a track is playing before the tap" "desc=[]" "${T0:-desc=[]}"
  assert_ne "a: positive control — Music's own next-track changes the playing track (the bound lets the shell's own seek through)" "$T0" "$T1"
  absent_in "a: positive control — no [music] … refused line for the shell's own controls" "refused, the queue has" "$SL2"
  c6; home_back; ensure_start; rings_save
fi

# ------------------------------------------------------------------------------------------------ b (P3-1)
if leg b; then
  for QID in 2147483648 4294967294 4294967295; do
    case "$QID" in 2147483648) IDX=-2147483648 ;; 4294967294) IDX=-2 ;; 4294967295) IDX=-1 ;; esac   # Media3's l2i cast
    music_play_first "b-$QID" >/dev/null
    record "b: before skipToQueueItem($QID) — session / pid" "$(music_session | xargs) / $(pid_now)"
    P0="$(pid_now)"; adb logcat -c; M="$(ring_mark)"
    adb shell "am start -n $LQ --es tag q$QID --el queue $QID" > "$ROW_DIR/b-$QID.start.txt" 2>&1
    sleep 5
    probe_out "legacyqueue q$QID" > "$ROW_DIR/b-$QID.txt"; sed 's/^/      /' "$ROW_DIR/b-$QID.txt" >> "$LOG"
    P1="$(pid_now)"; CR="$(crash)"
    adb logcat -d -s AndroidRuntime:E > "$ROW_DIR/b-$QID.fatal.txt" 2>&1   # the exception itself, kept before the clear below
    SL="$(ring_since "$M")"; printf '%s\n' "$SL" | grep -F '[music]' > "$ROW_DIR/b-$QID.ring.txt"
    record "b: skipToQueueItem($QID) — probe lines / pid $P0 -> $P1 / crash $CR" "$(tr '\n' ';' < "$ROW_DIR/b-$QID.txt")"
    assert_contains "b ($QID): the probe's skipToQueueItem was sent" "legacyqueue q$QID skipToQueueItem queue=$QID result=sent" "$(cat "$ROW_DIR/b-$QID.txt")"
    assert_eq "b ($QID): SAFE — the launcher's pid is unchanged and AndroidRuntime is empty of the shell" "$P0 0" "$P1 $CR"
    REF="$(printf '%s\n' "$SL" | grep -oE "\[music\] controller uid $QAC_UID: seekToDefaultPosition\\($IDX\\) refused, the queue has [0-9]+ item\\(s\\)" | head -1)"
    record "b ($QID): the session player's refusal line" "[${REF}]"
    assert_ne "b ($QID): SAFE — the session's player refused it: [music] controller uid $QAC_UID: seekToDefaultPosition($IDX) refused, the queue has <n> item(s)" "" "$REF"
    if [ "$P0" != "$P1" ] || [ "$CR" != 0 ]; then
      note "b ($QID): the shell was killed — relaunching Start so the next check runs"
      adb logcat -c   # clear the FATAL so the next id's crash() starts clean
      c6; home_back; ensure_start
    fi
  done
  c6; home_back; ensure_start; rings_save
fi

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
c6; home_back; ensure_start
assert_eq "restore: Home resolves to Start (the preferred home activity, as provision.sh sets it)" "$HOME_ACT" "$(home_resolves)"
assert_eq "restore: no music session is PLAYING" "0" "$(music_playing)"
adb shell "rm -rf $MUSICDIR" >/dev/null 2>&1
for id in $(q "content query --uri content://media/external/audio/media --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p'); do
  case " $AUDIO_BEFORE " in *" $id "*) ;; *) adb shell "content delete --uri content://media/external/audio/media/$id" >/dev/null 2>&1 ;; esac
done
adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 1
AUDIO_AFTER="$(q "content query --uri content://media/external/audio/media --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tr '\n' ' ')"
assert_eq "restore: the audio collection is back to what it was before the row" "$AUDIO_BEFORE" "$AUDIO_AFTER"
[ "$AUDIO_WAS" = 0 ] && adb shell pm revoke app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
if [ -z "$QAC_AT_BEGIN" ]; then adb uninstall "$QAC" >/dev/null 2>&1; assert_eq "restore: qa-capture (not installed at the start) is uninstalled" "" "$(q "pm path $QAC")"; fi
ensure_start
assert_eq "no crash of the shell in the restore (AndroidRuntime)" "0" "$(crash)"
row_end
