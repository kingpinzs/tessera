#!/usr/bin/env bash
# Phase 17 development proof, C-16 (5) (brief D; E22's "Insecure address"): a sign-in to a plain-http address that is not
# private asks first and sends NOTHING until Continue. The debug-only pref qa_server_base points the client at the
# Jellyfin fixture whatever host is typed, so "nothing was sent" is read on a server that WOULD have seen the sign-in:
# Cancel leaves the server with no device of the shell's, Continue (the control) makes one. Restores: the server
# removed from the app, the pref removed, the container removed.
. "$(dirname "$0")/v17.sh"
row_begin D_INSECURE "the insecure-server prompt: asked first, Cancel sends nothing, Continue signs in"
D="$ROW_DIR"
PUBLIC="192.0.2.10:8096"; PW=qa-password
jf_up || { echo "the Jellyfin fixture did not come up" >&2; exit 5; }
trap 'jf down >/dev/null' EXIT
assert_absent "no server token is saved at the start" "jellyfin" "$(cred_names)"
qa_pref qa_server_base "http://10.0.2.2:8096"
assert_eq "qa_server_base reads back" "http://10.0.2.2:8096" "$(qa_pref_now qa_server_base)"
assert_eq "the server knows no device of the shell's before" "0" "$(jf count-of Tessera)"

hub settings 2; dump_ui "$D/s0.xml"; tap_node "$D/s0.xml" hub_settings:server; sleep 1
MARK="$(ring_mark)"
server_form "$D/ask" "http://$PUBLIC" qa "$PW"; sleep 2
dump_ui "$D/asked.xml"
assert_eq "the page asks first" "This server isn't secure — your password would be sent unencrypted" "$(node_text "$D/asked.xml" server_insecure)"
assert_contains "[video] server <host>: insecure, asked" "[video] server $PUBLIC: insecure, asked" "$(vring "$MARK")"
assert_eq "Continue and Cancel are offered" "yes yes" "$(has_node "$D/asked.xml" server_insecure_continue) $(has_node "$D/asked.xml" server_insecure_cancel)"
assert_eq "while it asks, nothing reached the server" "0" "$(jf count-of Tessera)"
tap_node "$D/asked.xml" server_insecure_cancel; sleep 2
dump_ui "$D/cancelled.xml"
SLICE="$(vring "$MARK")"
assert_eq "Cancel: the form is back" "yes" "$(has_node "$D/cancelled.xml" server_connect)"
assert_eq "Cancel: the password field is empty" "" "$(node_text "$D/cancelled.xml" server_password)"
for word in connected unreachable unauthorised; do assert_absent "Cancel: no '$word' line" "server $PUBLIC: $word" "$SLICE"; done
assert_eq "Cancel: the server still knows no device of the shell's (nothing was sent)" "0" "$(jf count-of Tessera)"
assert_absent "Cancel: nothing saved" "jellyfin" "$(cred_names)"

# The control: the same address, Continue — the sign-in now reaches the server, so the read above could have failed.
MARK="$(ring_mark)"
tap_node "$D/cancelled.xml" server_password; sleep 0.6; adb shell input text "$PW"; adb shell input keyevent KEYCODE_BACK; sleep 0.6
dump_ui "$D/again.xml"; tap_node "$D/again.xml" server_connect; sleep 1.5
dump_ui "$D/asked2.xml"
assert_eq "asked again" "yes" "$(has_node "$D/asked2.xml" server_insecure)"
tap_node "$D/asked2.xml" server_insecure_continue; sleep 4
assert_contains "Continue: the sign-in is sent and answered" "[video] server $PUBLIC: connected" "$(vring "$MARK")"
assert_eq "Continue: the server now knows the shell (the control)" "1" "$(jf count-of Tessera)"
for r in $RINGS; do ring_save "$r"; done

# A private address is not asked about: 10.0.2.2 goes straight to the sign-in (a wrong password, so nothing is saved).
hub settings 2; dump_ui "$D/r0.xml"; tap_node "$D/r0.xml" hub_settings:server; sleep 1
dump_ui "$D/r1.xml"; tap_node "$D/r1.xml" server_remove; sleep 1.5
MARK="$(ring_mark)"
server_form "$D/private" "10.0.2.2:8096" qa wrong-on-purpose; sleep 3
dump_ui "$D/private.xml"
assert_eq "a private address: no prompt" "no" "$(has_node "$D/private.xml" server_insecure)"
assert_absent "a private address: no 'insecure, asked' line" "insecure, asked" "$(vring "$MARK")"
assert_contains "a private address: the sign-in went out at once" "[video] server 10.0.2.2:8096: unauthorised" "$(vring "$MARK")"
for r in $RINGS; do ring_save "$r"; done

assert_absent "restored: no server token" "jellyfin" "$(cred_names)"
for secret in "$PW" wrong-on-purpose; do
  assert_eq "a password is in no file of this row's folder" "" "$(grep -rlF -- "$secret" "$D" | head -3)"
  assert_eq "…and in no logcat line" "0" "$(adb logcat -d | grep -cF -- "$secret")"
done
qa_pref qa_server_base --remove
assert_eq "the QA pref is removed" "" "$(qa_pref_now qa_server_base)"
assert_eq "no crash of the shell" "" "$(no_crash)"
adb shell am force-stop app.tileshell; ensure_start
row_end
