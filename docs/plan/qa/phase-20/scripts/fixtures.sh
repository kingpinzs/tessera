#!/usr/bin/env bash
# Phase 20 — start and stop the two Python fixtures (build task 11): radio_fixture.py and catalogue_server.py.
# (The third server, Jellyfin, is qa/phase-17/fixtures/jellyfin/jellyfin_fixture.sh, with its own up / down.)
#
#   fixtures.sh up <radio|catalogue|all> [log dir]   start; wait until it answers; print its base URL and prefs
#   fixtures.sh down <radio|catalogue|all>           stop, by the pid this script recorded and nothing else
#   fixtures.sh status                               what is recorded and whether it answers
#   fixtures.sh prefs                                the three debug prefs' values for the ports in use
#
# THE PORTS. The doc names 10.0.2.2:8080 and :8081. On this PC both are held by other services (docker containers
# publishing 0.0.0.0:8080 and :8081 — not ours, never stopped), exactly as 8090 was for phase 17 (p17_video.sh:7-11).
# So the defaults here are 8092 (radio) and 8093 (catalogue); every `10.0.2.2:8080` / `:8081` of the rows reads
# `10.0.2.2:$P20_RADIO_PORT` / `:$P20_CATALOGUE_PORT`. The bases are debug PREFS, so nothing in the app changes.
# Override with P20_RADIO_PORT / P20_CATALOGUE_PORT. P20_RADIO_PAGE_CAP (default 0 = honour the app's own limit; INDEX Change Log 2026-10-07: the app stops at the first page shorter than 2,000 rows, so a capped page would end the directory after it) is radio_fixture.py's --page-cap:
# the most stations in one /json/stations answer, whatever `limit` asks (0 = `limit` alone decides). `up` refuses a port that something already listens on.
#
# State (git-ignored): ../gen/run/<name>.pid, <name>.port, <name>.err. The request log goes to <log dir>/<name>.log
# (+ .jsonl); default log dir ../gen/run. A log is appended to, never truncated here: a row gives its own folder.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN="$HERE/../gen/run"; mkdir -p "$RUN"
RADIO_PORT="${P20_RADIO_PORT:-8092}"
CATALOGUE_PORT="${P20_CATALOGUE_PORT:-8093}"

script_of() { case "$1" in radio) echo radio_fixture.py ;; catalogue) echo catalogue_server.py ;; esac; }
port_of() { case "$1" in radio) echo "$RADIO_PORT" ;; catalogue) echo "$CATALOGUE_PORT" ;; esac; }
# The recorded pid, only while it is still THIS fixture (the pid may have been reused by another process).
live_pid() {
  local pid; pid="$(cat "$RUN/$1.pid" 2>/dev/null)" || return 1
  [ -n "$pid" ] && [ -r "/proc/$pid/cmdline" ] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q "$(script_of "$1")" && echo "$pid"
}
listening() { python3 -c 'import socket,sys
s=socket.socket(); s.settimeout(1)
sys.exit(0 if s.connect_ex(("127.0.0.1", int(sys.argv[1]))) == 0 else 1)' "$1"; }

up_one() {
  local name="$1" logdir="$2" port pid i
  port="$(port_of "$name")"
  if pid="$(live_pid "$name")"; then echo "$name: already up, pid $pid, port $(cat "$RUN/$name.port")"; return 0; fi
  if listening "$port"; then echo "$name: port $port is already in use by something else — set P20_${name^^}_PORT" >&2; return 3; fi
  mkdir -p "$logdir"
  local extra=(); [ "$name" = radio ] && extra=(--page-cap "${P20_RADIO_PAGE_CAP:-0}")
  nohup python3 "$HERE/$(script_of "$name")" --port "$port" --log "$logdir/$name.log" "${extra[@]}" >/dev/null 2>"$RUN/$name.err" &
  pid=$!; echo "$pid" > "$RUN/$name.pid"; echo "$port" > "$RUN/$name.port"
  for i in $(seq 1 50); do
    curl -s -o /dev/null -m 2 "http://127.0.0.1:$port/__qa/ready" && { echo "$name: up, pid $pid, http://10.0.2.2:$port/ (host: 127.0.0.1:$port), log $logdir/$name.log"; return 0; }
    command sleep 0.2
  done
  echo "$name: did not answer; see $RUN/$name.err" >&2; return 4
}

down_one() {
  local name="$1" pid
  if pid="$(live_pid "$name")"; then
    kill "$pid"; for _ in $(seq 1 25); do [ -d "/proc/$pid" ] || break; command sleep 0.2; done
    [ -d "/proc/$pid" ] && { echo "$name: pid $pid did not stop" >&2; return 5; }
    echo "$name: stopped pid $pid"
  else
    echo "$name: not running (nothing recorded, or the recorded pid is gone)"
  fi
  rm -f "$RUN/$name.pid" "$RUN/$name.port"
}

names() { case "${1:-}" in radio|catalogue) echo "$1" ;; all) echo radio catalogue ;; *) echo "usage: fixtures.sh up|down <radio|catalogue|all> [log dir] | status | prefs" >&2; return 64 ;; esac; }

prefs() {
  echo "qa_radio_base=http://10.0.2.2:$RADIO_PORT/"
  echo "qa_music_catalogue_base=http://10.0.2.2:$CATALOGUE_PORT/ws/2/"
  echo "qa_coverart_base=http://10.0.2.2:$CATALOGUE_PORT/coverart/"
}

case "${1:-}" in
up) list="$(names "${2:-}")" || exit $?; rc=0; for n in $list; do up_one "$n" "${3:-$RUN}" || rc=$?; done; exit "$rc" ;;
down) list="$(names "${2:-}")" || exit $?; rc=0; for n in $list; do down_one "$n" || rc=$?; done; exit "$rc" ;;
status)
  for n in radio catalogue; do
    if pid="$(live_pid "$n")"; then
      port="$(cat "$RUN/$n.port")"
      echo "$n: pid $pid port $port ready=$(curl -s -o /dev/null -w '%{http_code}' -m 2 "http://127.0.0.1:$port/__qa/ready")"
    else echo "$n: down"; fi
  done ;;
prefs) prefs ;;
*) names "" ;;
esac
