#!/usr/bin/env bash
# E1 — the assistant role.
#
#   "adb shell cmd role get-role-holders android.app.role.ASSISTANT prints the shell after onboarding"
#
# The row is not just the role: it also proves that the role is what the SESSION depends on, which is
# why the role notice exists at all (H30). So it checks the holder, the service the platform resolved
# from it, and that the shell's own reading of the role agrees with the platform's.
. "$(dirname "$0")/lib.sh"

row_begin E1 "the assistant role"

holder="$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
assert_eq "role holder" "app.tileshell" "$holder"

# The platform resolves the role to a component; this is the one Secure settings ends up carrying, and
# it is what CortanaChecklist reads to say who holds the role.
assistant="$(adb shell settings get secure assistant | tr -d '\r')"
assert_eq "secure assistant component" "app.tileshell/.cortana.CortanaService" "$assistant"

vis="$(adb shell settings get secure voice_interaction_service | tr -d '\r')"
assert_eq "voice interaction service" "app.tileshell/.cortana.CortanaService" "$vis"

# The role is only useful if the service the platform binds is actually alive: the checklist's liveness
# row (N-01) reads the same fact.
ensure_start
cortana_assist
sleep 4
dump_ui "$ROW_DIR/e1_session.xml"
screencap "$ROW_DIR/e1_session.png"
assert_eq "a session opens on the assist key" "yes" "$(has_node "$ROW_DIR/e1_session.xml" cortana_session)"
assert_contains "the service reported itself ready" "VoiceInteractionService ready" "$(diag cortana)"

cortana_close
row_end
