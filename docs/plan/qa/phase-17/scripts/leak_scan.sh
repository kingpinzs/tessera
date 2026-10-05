#!/usr/bin/env bash
# Phase 17 leak scan (build task 16; T17-13, C-32): no credential may reach an evidence file, a saved ring slice, logcat
# or a build output. It looks for the secrets given as arguments (on the AVD: the typed TMDB token qa-dummy-token, the
# Jellyfin token read from the server, qa-password) plus every tmdb.* value local.properties still holds (the build
# reads none since Q-17-1 (a); one left there is scanned for, so a build or a log that picked it up fails).
#
# usage: leak_scan.sh [--logcat] [--path <dir-or-file>]... -- <secret>...
#   --logcat          also scan `adb logcat -d`
#   --path            scan this file or directory; default: docs/plan/qa/phase-17 (its scripts/ and fixtures/ and gen/
#                     are never scanned: they hold qa-dummy-token and qa-password as literals by design)
# Prints one line per secret and per place: clean, or MATCH with the FILE NAME only. No secret is ever echoed.
# Exit 0 when nothing matched; 1 on any match; 2 when there was nothing to look for (never a pass).
set -uo pipefail
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "$0")" && pwd)"; P17="$(cd "$HERE/.." && pwd)"; REPO="$(cd "$P17/../../../.." && pwd)"
LOGCAT=0; PATHS=(); SECRETS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --logcat) LOGCAT=1; shift ;;
    --path) PATHS+=("$2"); shift 2 ;;
    --) shift; SECRETS+=("$@"); break ;;
    *) echo "leak_scan: unknown argument (secrets go after --)" >&2; exit 64 ;;
  esac
done
[ ${#PATHS[@]} -gt 0 ] || PATHS=("$P17")
# local.properties' tmdb.* values, read without echoing.
if [ -f "$REPO/local.properties" ]; then
  while IFS= read -r v; do [ -n "$v" ] && SECRETS+=("$v"); done < <(sed -n 's/^tmdb\.[A-Za-z]*=//p' "$REPO/local.properties")
fi
# An empty secret would match every file; a short one would match by chance. Neither is scanned for.
KEEP=(); for s in "${SECRETS[@]}"; do [ ${#s} -ge 6 ] && KEEP+=("$s"); done
if [ ${#KEEP[@]} -eq 0 ]; then echo "leak_scan: nothing to look for (no secret of 6+ characters given or held) — NOT a pass"; exit 2; fi
LOGFILE=""
if [ "$LOGCAT" = 1 ]; then LOGFILE="$(mktemp)"; adb logcat -d > "$LOGFILE" 2>/dev/null; fi
rc=0; n=0
for s in "${KEEP[@]}"; do
  n=$((n + 1))
  for p in "${PATHS[@]}"; do
    hits="$(grep -rlF --exclude-dir=scripts --exclude-dir=fixtures --exclude-dir=gen -e "$s" "$p" 2>/dev/null | head -20)"
    if [ -n "$hits" ]; then rc=1; printf 'MATCH  secret #%d in:\n%s\n' "$n" "$hits"; else printf 'clean  secret #%d not in %s\n' "$n" "$p"; fi
  done
  if [ -n "$LOGFILE" ]; then
    if grep -qF -e "$s" "$LOGFILE"; then rc=1; printf 'MATCH  secret #%d in adb logcat -d\n' "$n"; else printf 'clean  secret #%d not in adb logcat -d (%s lines)\n' "$n" "$(wc -l < "$LOGFILE")"; fi
  fi
done
[ -n "$LOGFILE" ] && rm -f "$LOGFILE"
exit $rc
