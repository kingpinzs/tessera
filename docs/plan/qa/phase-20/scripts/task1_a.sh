#!/usr/bin/env bash
# Phase 20 build task 1 (a): does the Jellyfin 12.1 fixture answer /Audio/<id>/stream?static=true with 200 and NO token?
# usage: task1_a.sh <out dir> <jellyfin work dir> <video file for the fixture's Movies folder>
# Brings the fixture up (its `up` with phase 20's Music folder), asks from the host with curl, and leaves the container
# as it found it: if none was recorded in the work dir before, it is taken down again at the end.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JF="$HERE/../../phase-17/fixtures/jellyfin/jellyfin_fixture.sh"
OUT="${1:?out dir}"; WORK="${2:?work dir}"; VIDEO="${3:?video}"; mkdir -p "$OUT"
URL="http://127.0.0.1:${JELLYFIN_PORT:-8096}"
{
  echo "start $(date)"; df -h / | tail -1
  echo "jellyfin containers before: $(docker ps -a --format '{{.ID}} {{.Image}} {{.Status}}' | grep -i jellyfin || echo none)"
  WAS_UP=no; [ -f "$WORK/container.id" ] && WAS_UP=yes; echo "a container was recorded in the work dir before: $WAS_UP"
  if [ "$WAS_UP" = no ]; then bash "$JF" up "$WORK" "$VIDEO"; echo "up rc=$?"; fi
  echo "--- audio.ids"; cat "$WORK/audio.ids"
  n=0
  while IFS=$'\t' read -r id name secs; do
    n=$((n + 1))
    echo "--- track $n: $name ($secs s by the server), item $id"
    echo "GET /Audio/$id/stream?static=true  (no Authorization header, no ApiKey)"
    curl -s -m 30 -D "$OUT/a-stream-$n.headers" -o "$OUT/a-stream-$n.mp3" -w 'status %{http_code} type %{content_type} bytes %{size_download}\n' "$URL/Audio/$id/stream?static=true"; echo "curl rc=$?"
    tr -d '\r' < "$OUT/a-stream-$n.headers"
    echo "the body, by ffprobe: $(ffprobe -v error -show_entries format=duration:stream=codec_name -of csv=p=0 "$OUT/a-stream-$n.mp3" 2>&1 | tr '\n' ' ')"
    echo "Range bytes=0-999, no token: $(curl -s -m 30 -o /dev/null -H 'Range: bytes=0-999' -w 'status %{http_code} bytes %{size_download}' "$URL/Audio/$id/stream?static=true")"
    echo "the same path without ?static=true, no token: $(curl -s -m 30 -o /dev/null -w 'status %{http_code} type %{content_type} bytes %{size_download}' "$URL/Audio/$id/stream")"
    echo "/Audio/$id/universal, no token (the addendum: this one needs it): $(curl -s -m 30 -o /dev/null -w 'status %{http_code}' "$URL/Audio/$id/universal")"
  done < "$WORK/audio.ids"
  USER_ID="$(cat "$WORK/user.id")"
  echo "--- the listing calls WITHOUT a token (they must need one)"
  echo "GET /Items?includeItemTypes=Audio no token: $(curl -s -m 20 -o /dev/null -w 'status %{http_code}' "$URL/Items?userId=$USER_ID&recursive=true&includeItemTypes=Audio")"
  echo "--- the listing calls with the fixture admin's token in the Authorization header (shape only; the token is not printed)"
  for q in "Items?userId=$USER_ID&recursive=true&includeItemTypes=MusicAlbum" "Artists/AlbumArtists?userId=$USER_ID" "Items?userId=$USER_ID&recursive=true&includeItemTypes=Audio&fields=MediaSources" "UserViews?userId=$USER_ID"; do
    stem="$OUT/a-list-$(echo "$q" | tr -c 'A-Za-z0-9' '_' | cut -c1-48)"
    echo "GET /$q -> $(curl -s -m 20 -o "$stem.json" -w '%{http_code}' -H "Authorization: MediaBrowser Token=\"$(cat "$WORK/admin.token")\"" "$URL/$q") $(python3 -c 'import json,sys
d=json.load(open(sys.argv[1])); print(d.get("TotalRecordCount"), [(i.get("Type"), i.get("Name"), i.get("Album"), i.get("AlbumArtist"), i.get("IndexNumber"), i.get("RunTimeTicks"), i.get("CollectionType")) for i in d.get("Items", [])])' "$stem.json" 2>&1)"
  done
  echo "--- seed.log"; cat "$WORK/seed.log"
  if [ "$WAS_UP" = no ]; then
    bash "$JF" down "$WORK"; echo "down rc=$?"
    rm -rf "${WORK:?}/media" "${WORK:?}/seed.log"; rmdir "$WORK" 2>/dev/null
  fi
  echo "jellyfin containers after: $(docker ps -a --format '{{.ID}} {{.Image}} {{.Status}}' | grep -i jellyfin || echo none)"
  echo "end $(date)"
} > "$OUT/a-jellyfin.txt" 2>&1
