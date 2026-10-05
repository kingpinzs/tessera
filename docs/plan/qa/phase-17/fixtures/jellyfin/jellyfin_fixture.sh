#!/usr/bin/env bash
# Phase 17 — the media-server fixture (build tasks 13 and 16; row E22; BS-5): the pinned Jellyfin image on port 8096
# with an EMPTY config volume and a media folder holding one 10-s test video, seeded over Jellyfin's own REST start-up
# calls (not a shipped config dir: that holds a database tied to one version). The AVD reaches it at 10.0.2.2:8096.
#
#   jellyfin_fixture.sh up <work dir> <video file>   start, seed, wait until the video is in the library
#   jellyfin_fixture.sh stop|start <work dir>        docker stop / docker start of the recorded container (E22)
#   jellyfin_fixture.sh devices <work dir>           GET /Devices with the fixture admin's token
#   jellyfin_fixture.sh token-of <work dir> <AppName>   the token the server issued to that app (from its database)
#   jellyfin_fixture.sh count-of <work dir> <AppName>   how many devices signed in under that AppName
#   jellyfin_fixture.sh revoke <work dir> <AppName>     delete that app's devices, so its token stops working
#   jellyfin_fixture.sh down <work dir>              stop and remove the container and its two volumes, by recorded id
#
# <work dir> is a scratch folder (never the repo): it receives container.id, volumes, admin.token, user.id, item.id and
# seed.log. The fixture account is "qa" with the password "qa-password" (a fixture value, in this file by design).
# Only the container this script started is ever stopped or removed: by the id it recorded, never by name or pattern.
set -uo pipefail
IMAGE="jellyfin/jellyfin:12.1@sha256:78d3ea1207d1322471fcac39a614f004f2ccf7e878f95ab2977d752f07e4dd7e"
PORT="${JELLYFIN_PORT:-8096}"
URL="http://127.0.0.1:$PORT"
USER_NAME="qa"; PASSWORD="qa-password"
CLIENT='MediaBrowser Client="qa-fixture", Device="host", DeviceId="qa-fixture-host", Version="1"'
cmd="${1:?usage: jellyfin_fixture.sh up|stop|start|devices|token-of|count-of|revoke|down <work dir> [video|AppName]}"; WORK="${2:?work dir}"
mkdir -p "$WORK"; LOG="$WORK/seed.log"
say() { echo "$*" | tee -a "$LOG"; }
code() { curl -s -o "$WORK/.body" -w '%{http_code}' -m 20 "$@"; }

case "$cmd" in
up)
  VIDEO="${3:?the video file}"
  [ -f "$WORK/container.id" ] && { echo "a container is already recorded in $WORK (run down first)" >&2; exit 2; }
  : > "$LOG"
  say "image $IMAGE"
  mkdir -p "$WORK/media/movies"; cp "$VIDEO" "$WORK/media/movies/qa-steps.mp4"
  CFG="$(docker volume create)"; CACHE="$(docker volume create)"; echo "$CFG $CACHE" > "$WORK/volumes"
  ID="$(docker run -d -p "127.0.0.1:$PORT:8096" -v "$CFG:/config" -v "$CACHE:/cache" -v "$WORK/media:/media:ro" "$IMAGE")" || { say "docker run failed"; exit 3; }
  echo "$ID" > "$WORK/container.id"; say "container $ID"
  say "image id $(docker inspect --format '{{.Image}}' "$ID")"
  # On a first start 12.1 answers 200 for a moment from its start-up host, goes away, and comes back as the real server
  # (found by running this: the plain "wait for 200" of BS-5's sequence then seeds into nothing). So: ready is the
  # server's own JSON with a Version, three times in a row a second apart.
  ok=0
  for _ in $(seq 1 120); do
    if [ "$(code "$URL/System/Info/Public")" = 200 ] && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("Version") else 1)' "$WORK/.body" 2>/dev/null; then ok=$((ok + 1)); else ok=0; fi
    [ "$ok" -ge 3 ] && break
    sleep 1
  done
  [ "$ok" -ge 3 ] || { say "the server never became ready"; exit 3; }
  say "GET /System/Info/Public -> $(code "$URL/System/Info/Public") $(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("Version", d.get("Version"), "StartupWizardCompleted", d.get("StartupWizardCompleted"))' "$WORK/.body" 2>/dev/null)"
  say "POST /Startup/Configuration -> $(code -X POST -H 'Content-Type: application/json' -d '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' "$URL/Startup/Configuration")"
  say "GET /Startup/User -> $(code "$URL/Startup/User")"
  say "POST /Startup/User -> $(code -X POST -H 'Content-Type: application/json' -d "{\"Name\":\"$USER_NAME\",\"Password\":\"$PASSWORD\"}" "$URL/Startup/User")"
  say "POST /Library/VirtualFolders -> $(code -X POST -H 'Content-Type: application/json' -d '{"LibraryOptions":{}}' "$URL/Library/VirtualFolders?name=Movies&collectionType=movies&paths=%2Fmedia%2Fmovies&refreshLibrary=true")"
  say "POST /Startup/Complete -> $(code -X POST "$URL/Startup/Complete")"
  say "POST /Users/AuthenticateByName -> $(code -X POST -H 'Content-Type: application/json' -H "Authorization: $CLIENT" -d "{\"Username\":\"$USER_NAME\",\"Pw\":\"$PASSWORD\"}" "$URL/Users/AuthenticateByName")"
  python3 - "$WORK" <<'PY' || { echo "sign-in as the fixture admin failed" >&2; exit 4; }
import json, sys
w = sys.argv[1]; d = json.load(open(w + "/.body"))
open(w + "/admin.token", "w").write(d["AccessToken"]); open(w + "/user.id", "w").write(d["User"]["Id"])
PY
  TOKEN="$(cat "$WORK/admin.token")"; USER_ID="$(cat "$WORK/user.id")"
  say "fixture admin signed in; user id $USER_ID"
  ITEM=""
  for i in $(seq 1 120); do
    code -H "Authorization: MediaBrowser Token=\"$TOKEN\"" "$URL/Items?userId=$USER_ID&recursive=true&includeItemTypes=Movie,Episode,Video" >/dev/null
    ITEM="$(python3 -c 'import json,sys
try:
    items = json.load(open(sys.argv[1])).get("Items", [])
    print(next((i["Id"] for i in items if "qa-steps" in i.get("Name", "")), ""))
except Exception: print("")' "$WORK/.body")"
    [ -n "$ITEM" ] && break
    [ "$i" = 20 ] && say "POST /Library/Refresh -> $(code -X POST -H "Authorization: MediaBrowser Token=\"$TOKEN\"" "$URL/Library/Refresh")"
    sleep 1
  done
  rm -f "$WORK/.body"
  [ -n "$ITEM" ] || { say "the video never appeared in the library"; exit 5; }
  echo "$ITEM" > "$WORK/item.id"; say "library lists qa-steps as item $ITEM"
  ;;
stop|start)
  docker "$cmd" "$(cat "$WORK/container.id")" >/dev/null && echo "$cmd: $(cat "$WORK/container.id" | cut -c1-12)"
  ;;
devices)
  curl -s -m 20 -H "Authorization: MediaBrowser Token=\"$(cat "$WORK/admin.token")\"" "$URL/Devices"
  ;;
token-of)
  # 12.1's GET /Devices carries NO AccessToken (found by running it; r3 V8 expected one there), so the token the
  # server issued to an app is read from the server's own database: Devices.AccessToken, by AppName. Printed to
  # stdout for a driver's variable — never into a log.
  APP="${3:?the AppName, e.g. Tessera}"; ID="$(cat "$WORK/container.id")"; rm -rf "$WORK/db"; mkdir -p "$WORK/db"
  for f in jellyfin.db jellyfin.db-wal jellyfin.db-shm; do docker cp "$ID:/config/data/$f" "$WORK/db/$f" >/dev/null 2>&1; done
  python3 - "$WORK/db/jellyfin.db" "$APP" <<'PY'
import sqlite3, sys
rows = sqlite3.connect(sys.argv[1]).execute("select AccessToken from Devices where AppName = ? order by DateCreated desc", (sys.argv[2],)).fetchall()
print(rows[0][0] if rows else "")
PY
  rm -rf "$WORK/db"
  ;;
count-of)
  # How many devices the server has issued a token to under an AppName (0 = that app never signed in).
  APP="${3:?the AppName}"
  curl -s -m 20 -H "Authorization: MediaBrowser Token=\"$(cat "$WORK/admin.token")\"" "$URL/Devices" | python3 -c 'import json,sys; print(sum(1 for i in json.load(sys.stdin).get("Items", []) if i.get("AppName") == sys.argv[1]))' "$APP"
  ;;
revoke)
  # Makes an app's token invalid on the server (the edge case "the token invalid after a password change").
  APP="${3:?the AppName}"; TOKEN="$(cat "$WORK/admin.token")"
  for id in $(curl -s -m 20 -H "Authorization: MediaBrowser Token=\"$TOKEN\"" "$URL/Devices" | python3 -c 'import json,sys; print(" ".join(i["Id"] for i in json.load(sys.stdin).get("Items", []) if i.get("AppName") == sys.argv[1]))' "$APP"); do
    echo "DELETE /Devices -> $(curl -s -o /dev/null -w '%{http_code}' -m 20 -X DELETE -H "Authorization: MediaBrowser Token=\"$TOKEN\"" "$URL/Devices?id=$id")"
  done
  ;;
down)
  if [ -f "$WORK/container.id" ]; then
    ID="$(cat "$WORK/container.id")"
    docker stop "$ID" >/dev/null 2>&1; docker rm "$ID" >/dev/null 2>&1 && echo "removed container $(echo "$ID" | cut -c1-12)"
    rm -f "$WORK/container.id"
  fi
  if [ -f "$WORK/volumes" ]; then
    # shellcheck disable=SC2046
    docker volume rm $(cat "$WORK/volumes") >/dev/null 2>&1 && echo "removed its volumes"
    rm -f "$WORK/volumes"
  fi
  rm -f "$WORK/admin.token" "$WORK/user.id" "$WORK/item.id"
  ;;
*) echo "unknown command $cmd" >&2; exit 2 ;;
esac
