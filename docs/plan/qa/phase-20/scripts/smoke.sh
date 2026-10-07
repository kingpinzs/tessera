#!/usr/bin/env bash
# Phase 20 — the fixtures' own smoke (build task 11), host side: every endpoint the rows read, asked once with curl.
# usage: smoke.sh <out dir>        (the two fixtures must be up: fixtures.sh up all <out dir>)
# Writes one file per call under <out dir> and a summary to stdout. Not an acceptance row; it asserts nothing of the app.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:?out dir}"; mkdir -p "$OUT"
R="http://127.0.0.1:${P20_RADIO_PORT:-8092}"; C="http://127.0.0.1:${P20_CATALOGUE_PORT:-8093}"
UA="Tessera/0-smoke (fixture smoke; not the app)"
J1=11111111-1111-4111-8111-111111111111; N1=33333333-3333-4333-8333-333333333333
names() { python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(len(d), [r.get("name") for r in d])' "$1"; }
get() { # get <file stem> <url> -> "<status> <content-type> <bytes>"
  curl -s -m 15 -A "$UA" -D "$OUT/$1.headers" -o "$OUT/$1.body" -w '%{http_code} %{content_type} %{size_download}B' "$2"; echo " rc=$?"
}
echo "== radio $R"
for off in 0 2 4; do
  echo "stations offset=$off: $(get stations-$off "$R/json/stations?order=clickcount&reverse=true&hidebroken=true&limit=2000&offset=$off") rows $(names "$OUT/stations-$off.body")"
done
echo "byuuid ?uuids=: $(get byuuid-q "$R/json/stations/byuuid?uuids=$J1,$N1") rows $(names "$OUT/byuuid-q.body")"
echo "byuuid /<uuid>: $(get byuuid-p "$R/json/stations/byuuid/$J1") rows $(names "$OUT/byuuid-p.body")"
echo "tags: $(get tags "$R/json/tags?order=stationcount&reverse=true&limit=500") $(cat "$OUT/tags.body")"
echo "countries: $(get countries "$R/json/countries") $(cat "$OUT/countries.body")"
echo "click: $(get click "$R/json/url/$J1") $(cat "$OUT/click.body")"
echo "station fields: $(python3 -c 'import json,sys; d=json.load(open(sys.argv[1]))[0]; print({k: d[k] for k in ("stationuuid","name","url","url_resolved","favicon","tags","countrycode","codec","bitrate","hls","lastcheckok","clickcount")})' "$OUT/stations-0.body")"
echo "QA File: $(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print([(r["name"], r["url_resolved"]) for r in d])' "$OUT/stations-2.body")"
echo "== ICY stream (QA Jazz One, 26 s)"
python3 "$HERE/icy_probe.py" "$R/stream/jazz-one" 26 "$UA" > "$OUT/icy-jazz-one.txt" 2>&1; echo "icy rc=$?"; cat "$OUT/icy-jazz-one.txt"
echo "== ICY stream (QA Jazz Two, 3 s: no StreamTitle)"
python3 "$HERE/icy_probe.py" "$R/stream/jazz-two" 3 "$UA" > "$OUT/icy-jazz-two.txt" 2>&1; echo "icy rc=$?"; cat "$OUT/icy-jazz-two.txt"
echo "== the stream without Icy-MetaData (3 s), decoded by ffprobe"
curl -s -m 3 -A "$UA" -D "$OUT/plain.headers" -o "$OUT/plain.mp3" "$R/stream/jazz-one"; echo "curl rc=$? (28 = the 3 s limit, the stream never ends)"
grep -i -E "^(HTTP|icy-|content-type)" "$OUT/plain.headers" | tr -d '\r' | tr '\n' '|'; echo
ffprobe -v error -show_entries stream=codec_name,sample_rate,channels,bit_rate -of csv=p=0 "$OUT/plain.mp3" 2>&1 | head -3
echo "== catalogue $C"
echo "search qa artist: $(get search "$C/ws/2/recording?query=qa%20artist&fmt=json&limit=25") $(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(d["count"], [(r["title"], r["artist-credit"][0]["name"], r["length"], r["releases"][0]["id"]) for r in d["recordings"]])' "$OUT/search.body")"
echo "search zzqx: $(get search-miss "$C/ws/2/recording?query=zzqx&fmt=json&limit=25") $(cat "$OUT/search-miss.body" | cut -c1-120)"
for m in e1111111-1111-4111-8111-111111111111 e2222222-2222-4222-8222-222222222222; do
  echo "cover $m: $(get cover-$m "$C/coverart/release/$m/front-250") $(file -b "$OUT/cover-$m.body")"
done
echo "cover unknown: $(get cover-unknown "$C/coverart/release/e9999999-9999-4999-8999-999999999999/front-250")"
