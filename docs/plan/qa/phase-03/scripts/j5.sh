#!/usr/bin/env bash
# J5 — "play <song / artist / album>" plays it in the shell's own Music player, and Tess never claims a song
# she could not find (found 2026-09-23 while writing phase 20: MusicService's session never resolved a search,
# so "play Bloom" either did nothing to the playing queue or just opened Music, while Tess said "Playing Bloom.").
#
# Three cases on the MUSIC6 fixtures (Bloom / Codex / 4 Minute Warning by Radiohead on The King of Limbs, An
# Ending / Deep Blue Day by Brian Eno on Apollo, Zoo Station by U2):
#   a. nothing playing (shell restarted): "play bloom" -> Bloom is PLAYING in the shell's session;
#   b. something else playing: "play brian eno" -> a Brian Eno track replaces it;
#   c. "play zzqx nothing" -> Tess says she could not find it, and the playing track does not change.
. "$(dirname "$0")/lib.sh"

row_begin J5 "\"play <name>\" plays it in the shell's player; an unknown name is said, not faked"
FIX="$(cd "$(dirname "$0")/../../phase-01/MUSIC6-fixtures" && pwd)"
adb shell mkdir -p /sdcard/Music/tessera-qa >/dev/null 2>&1
for f in "$FIX"/*.mp3; do adb shell ls "/sdcard/Music/tessera-qa/$(basename "$f")" >/dev/null 2>&1 || adb push "$f" /sdcard/Music/tessera-qa/ >/dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1
session() { adb shell dumpsys media_session | awk '/package=app\.tileshell/ {f=1} f && /state=PlaybackState/ {print; exit}' | tr -d '\r'; }
title() { adb shell dumpsys media_session | awk '/package=app\.tileshell/ {f=1} f && /metadata:/ {print; exit}' | tr -d '\r'; }

# ---- a. nothing playing ------------------------------------------------------------------------------------
adb shell am force-stop $PKG; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 4
cortana_assist; sleep 4
type_request "play bloom" 8
note "a. Tess said: [$(reply_text)]; session: $(session); $(title)"
assert_contains "a. the shell's session is playing" "PLAYING" "$(session)"
assert_contains "a. and it is Bloom" "Bloom" "$(title)"
assert_contains "a. Tess names what she played" "Bloom" "$(reply_text)"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ---- b. something else playing -----------------------------------------------------------------------------
cortana_assist; sleep 4
type_request "play brian eno" 8
note "b. Tess said: [$(reply_text)]; session: $(session); $(title)"
assert_contains "b. still playing" "PLAYING" "$(session)"
T="$(title)"
assert_eq "b. a Brian Eno track replaced Bloom" yes "$(echo "$T" | grep -qE 'An Ending|Deep Blue Day' && echo yes || echo no)"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ---- c. a name that is not in the library --------------------------------------------------------------------
BEFORE="$(title)"
cortana_assist; sleep 4
type_request "play zzqx nothing" 8
R="$(reply_text)"
note "c. Tess said: [$R]; before: $BEFORE; after: $(title)"
assert_absent "c. Tess does not claim to be playing it" "Playing zzqx" "$R"
assert_contains "c. she says she could not find it" "couldn't find" "$R"
assert_eq "c. the playing track did not change" "$BEFORE" "$(title)"
adb shell input keyevent KEYCODE_HOME; sleep 1

adb shell am force-stop $PKG; adb shell input keyevent KEYCODE_HOME; sleep 2
note "restored: the shell restarted (music stopped); the fixtures stay (MUSIC rows share them)"
row_end
