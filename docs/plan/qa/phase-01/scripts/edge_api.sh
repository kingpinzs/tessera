#!/usr/bin/env bash
# Edge cases: Live Tile API counts and trust surface.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_api.txt; PKG=app.tileshell.testclient.a
verb() { adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity $*" >/dev/null 2>&1; sleep 2.5; adb shell logcat -d -s TileClient | tail -2 >> "$LOG"; }
echo "# Edge cases: Live Tile API $(date -Iseconds)" > "$LOG"
adb shell logcat -c
echo '## XML with a DTD' >> "$LOG"
verb '--es verb tile.update --es xml "<?xml version=\"1.0\"?><!DOCTYPE tile [<!ENTITY x \"expanded\">]><tile><visual><binding template=\"TileMedium\"><text>&x;</text></binding></visual></tile>"'
echo '## XML with an external entity (XXE)' >> "$LOG"
verb '--es verb tile.update --es xml "<?xml version=\"1.0\"?><!DOCTYPE tile [<!ENTITY xxe SYSTEM \"file:///data/data/app.tileshell/files/start_layout.json\">]><tile><visual><binding template=\"TileMedium\"><text>&xxe;</text></binding></visual></tile>"'
echo '## entity expansion (billion laughs)' >> "$LOG"
verb '--es verb tile.update --es xml "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\"><!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]><tile><visual><binding template=\"TileMedium\"><text>&c;</text></binding></visual></tile>"'
echo '## malformed XML' >> "$LOG"
verb '--es verb tile.update --es xml "<tile><visual><binding template=\"TileMedium\"><text>unclosed"'
echo '## an image URI whose authority is the shell itself (confused deputy)' >> "$LOG"
verb '--es verb tile.update --es text "borrowed image" --ez image true --es imageAuthority app.tileshell.livetile'
echo '## an oversized image (> 200 KB)' >> "$LOG"
verb '--es verb tile.update --es text "big image" --ez bigImage true'
echo '## queue overflow past 5' >> "$LOG"
verb '--es verb demo.queue'
verb '--es verb tile.update --es text "sixth item" --es tag q6'
echo "queue entries now: $(adb shell run-as app.tileshell cat files/livetile/$PKG/state.json | python3 -c 'import json,sys; d=json.load(sys.stdin); print(len(d["queue"]), [e.get("tag") for e in d["queue"]])')" >> "$LOG"
echo '## rate limit (70 calls in a minute)' >> "$LOG"
verb '--es verb flood --ei count 70'
echo '## API app uninstalled: its tile content and badge go' >> "$LOG"
verb '--es verb tile.update --es text "still here"'
verb '--es verb badge.update --ei value 4'
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "before uninstall: $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[engine\] publish pkg:'$PKG | tail -1 | sed 's/.*publish //'); badge $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[badge\]' | tail -1 | sed 's/.*\[badge\] //')" >> "$LOG"
echo "\$ adb uninstall $PKG: $(adb uninstall $PKG)" >> "$LOG"; sleep 5
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "after uninstall: stored state dir $(adb shell run-as app.tileshell ls files/livetile 2>&1 | tr '\n' ' '); badge $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[badge\]' | tail -1 | sed 's/.*\[badge\] //')" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[livetile\] (reject|accept|expire|uninstall)" | tail -14 >> "$LOG"
