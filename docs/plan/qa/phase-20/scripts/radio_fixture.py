#!/usr/bin/env python3
"""Phase 20 — the radio fixture (build task 11; rows A1–A5, A7 d): the station DIRECTORY and the STREAMS in one server.

A stand-in for radio-browser.info and for four stations, so that no row touches the live directory or a real station.
The AVD reaches it at http://10.0.2.2:<port>/ (the debug pref `qa_radio_base`).

  /json/stations?order=&reverse=&hidebroken=&limit=&offset=
                         the directory in radio-browser's shape, ordered as asked (clickcount, reverse=true is what the
                         shell asks). A page never holds more than --page-cap rows (default 2), whatever `limit` says,
                         so four stations are three requests: offsets 0 and 2 full, offset 4 empty.
  /json/stations/byuuid?uuids=<a>,<b>   and   /json/stations/byuuid/<uuid>     (GET, or POST with a form body)
  /json/tags?order=&reverse=&limit=      [{"name","stationcount"}]
  /json/countries                        [{"name","iso_3166_1","stationcount"}]
  /json/url/<uuid>                       the click call: {"ok":true,"message":"retrieved station url",…}
  /stream/jazz-one       QA Jazz One: an endless MP3 with ICY metadata. StreamTitle is "QA Song 1" and becomes
                         "QA Song 2" --switch-s (default 20) seconds after EACH connection opens.
  /stream/jazz-two       QA Jazz Two: the same endless MP3, ICY headers, never a StreamTitle (the null-title case).
  /stream/news-one       QA News One: likewise.
  (QA File has no stream here: its url_resolved is file:///sdcard/Music/x.mp3, the scheme the shell must refuse.)
  /__qa/ready            200 "ok"; NEVER logged (a driver's readiness probe must not look like a request of the shell's)

The MP3 is generated here with ffmpeg the way MUSIC6's fixtures were (a lavfi sine source, mono, 44.1 kHz, 32 kbit/s
CBR; no audio device is opened and nothing is recorded), with no ID3 tag and no Xing frame so that the file can be sent
end to end for ever. It is sent at the real-time rate after a --burst-s (default 4) second head start.

usage: radio_fixture.py [--port 8080] [--bind 0.0.0.0] [--public-host 10.0.2.2] [--log FILE] [--loop FILE]
                        [--page-cap 2] [--switch-s 20] [--burst-s 4]

THE LOG. Every request (but /__qa/…) is written to stdout and to --log as

    <n> <HH:MM:SS.mmm> <METHOD> <path?query> -> <status>
        <Header>: <value>            one line per request header, as received

and, when --log is given, as one JSON object per line in <FILE>.jsonl ({"n","t","method","target","path","status",
"headers"}). A stream adds, when its connection ends, `<n> closed after <s> s, <bytes> B, titles: …`.
Nothing here is a secret: the fixture has no key, and a credential the shell wrongly sent would be visible — that is
what A7 (d) reads the log for.
"""
import argparse
import json
import os
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.join(HERE, "..", "gen")
BITRATE = 32000            # bit/s, CBR
BYTES_PER_S = BITRATE // 8
METAINT = 4096             # about one metadata slot a second at 32 kbit/s

JAZZ_ONE = "11111111-1111-4111-8111-111111111111"
JAZZ_TWO = "22222222-2222-4222-8222-222222222222"
NEWS_ONE = "33333333-3333-4333-8333-333333333333"
QA_FILE = "44444444-4444-4444-8444-444444444444"

# name, uuid, stream path (None = the file: URL), tags, country, code, clickcount
STATIONS = [
    ("QA Jazz One", JAZZ_ONE, "/stream/jazz-one", "jazz,qa", "The United States Of America", "US", 400),
    ("QA Jazz Two", JAZZ_TWO, "/stream/jazz-two", "jazz", "The United Kingdom Of Great Britain And Northern Ireland", "GB", 300),
    ("QA News One", NEWS_ONE, "/stream/news-one", "news,talk", "The United States Of America", "US", 200),
    ("QA File", QA_FILE, None, "misc", "Germany", "DE", 100),
]
STREAMS = {"/stream/jazz-one": ("QA Jazz One", "jazz", True), "/stream/jazz-two": ("QA Jazz Two", "jazz", False),
           "/stream/news-one": ("QA News One", "news", False)}


def station_rows(base):
    """The four stations with every field radio-browser 0.7.45 returns for a station (rb-sample-stats.txt)."""
    rows = []
    for i, (name, uuid, path, tags, country, code, clicks) in enumerate(STATIONS, 1):
        url = base + path if path else "file:///sdcard/Music/x.mp3"
        rows.append({
            "changeuuid": "c%07d-0000-4000-8000-000000000000" % i, "stationuuid": uuid,
            "serveruuid": None, "name": name, "url": url, "url_resolved": url,
            "homepage": "", "favicon": "", "tags": tags, "country": country, "countrycode": code,
            "iso_3166_2": "", "state": "", "language": "english", "languagecodes": "en", "votes": clicks // 10,
            "lastchangetime": "2026-10-01 00:00:00", "lastchangetime_iso8601": "2026-10-01T00:00:00Z",
            "codec": "MP3", "bitrate": BITRATE // 1000, "hls": 0, "lastcheckok": 1,
            "lastchecktime": "2026-10-07 00:00:00", "lastchecktime_iso8601": "2026-10-07T00:00:00Z",
            "lastcheckoktime": "2026-10-07 00:00:00", "lastcheckoktime_iso8601": "2026-10-07T00:00:00Z",
            "lastlocalchecktime": "2026-10-07 00:00:00", "lastlocalchecktime_iso8601": "2026-10-07T00:00:00Z",
            "clicktimestamp": "2026-10-07 00:00:00", "clicktimestamp_iso8601": "2026-10-07T00:00:00Z",
            "clickcount": clicks, "clicktrend": 0, "ssl_error": 0, "geo_lat": None, "geo_long": None,
            "geo_distance": None, "has_extended_info": False,
        })
    return rows


def make_loop(path):
    """The looping MP3: ten seconds of a 440 Hz sine, as MUSIC6's files were made (ffmpeg, lavfi; no audio device)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
                    "-ac", "1", "-ar", "44100", "-c:a", "libmp3lame", "-b:a", "32k", "-write_xing", "0",
                    "-id3v2_version", "0", "-write_id3v1", "0", "-f", "mp3", path], check=True)


def icy_block(title):
    """One ICY metadata block: a length byte (in 16-byte units) and the padded text; b"\\0" when there is none."""
    if title is None:
        return b"\x00"
    text = ("StreamTitle='%s';" % title).encode("utf-8")
    text += b"\x00" * (-len(text) % 16)
    return bytes([len(text) // 16]) + text


class Fixture(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    base = "http://10.0.2.2:8080"
    log_file = None
    loop = b""
    page_cap = 2
    switch_s = 20.0
    burst_s = 4.0
    lock = threading.Lock()
    count = 0

    def log_message(self, *_):
        pass

    # ---------------------------------------------------------------- the log
    @staticmethod
    def write(text, obj=None):
        print(text, flush=True)
        if Fixture.log_file:
            with open(Fixture.log_file, "a", encoding="utf-8") as f:
                f.write(text + "\n")
            if obj is not None:
                with open(Fixture.log_file + ".jsonl", "a", encoding="utf-8") as f:
                    f.write(json.dumps(obj) + "\n")

    def note(self, status):
        with Fixture.lock:
            Fixture.count += 1
            n = Fixture.count
            now = time.time()
            stamp = time.strftime("%H:%M:%S", time.localtime(now)) + ".%03d" % (int(now * 1000) % 1000)
            lines = ["%d %s %s %s -> %d" % (n, stamp, self.command, self.path, status)]
            lines += ["    %s: %s" % (k, v) for k, v in self.headers.items()]
            Fixture.write("\n".join(lines), {"n": n, "t": stamp, "method": self.command, "target": self.path,
                                             "path": urlsplit(self.path).path, "status": status,
                                             "headers": [[k, v] for k, v in self.headers.items()]})
        return n

    # ---------------------------------------------------------------- answers
    def send(self, status, body, content_type="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def answer(self, status, body, content_type="application/json"):
        self.note(status)
        self.send(status, body, content_type)

    def do_HEAD(self):
        self.do_GET()

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        form = parse_qs(self.rfile.read(length).decode("utf-8", "replace")) if length else {}
        self.route(form)

    def do_GET(self):
        self.route({})

    def route(self, form):
        url = urlsplit(self.path)
        path = url.path.rstrip("/") or "/"
        query = parse_qs(url.query)
        query.update(form)

        def arg(name, default=""):
            return query.get(name, [default])[0]

        if path.startswith("/__qa/"):
            return self.send(200, b"ok\n", "text/plain")

        rows = station_rows(Fixture.base)

        if path == "/json/stations":
            order = arg("order", "name")
            if order in rows[0]:
                rows.sort(key=lambda r: (r[order] is None, r[order]), reverse=arg("reverse") == "true")
            try:
                offset = max(0, int(arg("offset", "0")))
                limit = max(0, int(arg("limit", "100000")))
            except ValueError:
                return self.answer(400, {"ok": False, "message": "limit and offset are numbers"})
            size = min(limit, Fixture.page_cap) if Fixture.page_cap > 0 else limit
            return self.answer(200, rows[offset:offset + size])

        if path == "/json/stations/byuuid" or path.startswith("/json/stations/byuuid/"):
            wanted = [u for u in (arg("uuids").split(",") if path == "/json/stations/byuuid" else [unquote(path.rsplit("/", 1)[1])]) if u]
            return self.answer(200, [r for r in rows if r["stationuuid"] in wanted])

        if path == "/json/tags":
            counts = {}
            for r in rows:
                for t in r["tags"].split(","):
                    counts[t] = counts.get(t, 0) + 1
            tags = sorted(({"name": k, "stationcount": v} for k, v in counts.items()), key=lambda t: t["name"])
            if arg("order") == "stationcount":  # a stable sort: equal counts stay in name order
                tags.sort(key=lambda t: t["stationcount"], reverse=arg("reverse") == "true")
            elif arg("reverse") == "true":
                tags.reverse()
            try:
                limit = int(arg("limit", "100000"))
            except ValueError:
                limit = 100000
            return self.answer(200, tags[:limit])

        if path == "/json/countries":
            seen = {}
            for r in rows:
                c = seen.setdefault(r["countrycode"], {"name": r["country"], "iso_3166_1": r["countrycode"], "stationcount": 0})
                c["stationcount"] += 1
            return self.answer(200, sorted(seen.values(), key=lambda c: c["name"]))

        if path.startswith("/json/url/"):
            uuid = unquote(path.rsplit("/", 1)[1])
            row = next((r for r in rows if r["stationuuid"] == uuid), None)
            if row is None:
                return self.answer(200, {"ok": False, "message": "did not find station with matching id", "stationuuid": "", "name": "", "url": ""})
            return self.answer(200, {"ok": True, "message": "retrieved station url", "stationuuid": uuid, "name": row["name"], "url": row["url_resolved"]})

        if path in STREAMS:
            return self.stream(path)

        return self.answer(404, {"ok": False, "message": "not found"})

    # ---------------------------------------------------------------- the stream
    def stream(self, path):
        name, genre, titled = STREAMS[path]
        icy = self.headers.get("Icy-MetaData") == "1"
        n = self.note(200)
        self.close_connection = True
        self.send_response(200)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("icy-name", name)
        self.send_header("icy-genre", genre)
        self.send_header("icy-br", str(BITRATE // 1000))
        self.send_header("icy-pub", "0")
        if icy:
            self.send_header("icy-metaint", str(METAINT))
        self.send_header("Cache-Control", "no-cache, no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        if self.command == "HEAD":
            return
        opened = time.monotonic()
        sent = 0          # audio bytes so far
        pos = 0           # where in the loop the next audio byte comes from
        last_title = object()
        titles = []
        loop = Fixture.loop
        try:
            while True:
                # Real time, after a head start: never more than (elapsed + burst) seconds of audio on the wire.
                allowed = int((time.monotonic() - opened + Fixture.burst_s) * BYTES_PER_S)
                if sent >= allowed:
                    time.sleep(0.05)
                    continue
                want = min(allowed - sent, METAINT - (sent % METAINT)) if icy else min(allowed - sent, 4096)
                chunk = loop[pos:pos + want]
                if len(chunk) < want:
                    chunk += loop[:want - len(chunk)]
                pos = (pos + want) % len(loop)
                self.wfile.write(chunk)
                sent += want
                if icy and sent % METAINT == 0:
                    elapsed = time.monotonic() - opened
                    title = ("QA Song 2" if elapsed >= Fixture.switch_s else "QA Song 1") if titled else None
                    if title != last_title:
                        self.wfile.write(icy_block(title))
                        last_title = title
                        if title is not None:
                            titles.append("%s@%.1fs" % (title, elapsed))
                    else:
                        self.wfile.write(b"\x00")
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, TimeoutError, OSError):
            pass
        with Fixture.lock:
            Fixture.write("%d closed after %.1f s, %d B, titles: %s" % (n, time.monotonic() - opened, sent, ", ".join(titles) or "none"))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--bind", default="0.0.0.0")
    ap.add_argument("--public-host", default="10.0.2.2", help="the host the DEVICE reaches this server at (the stations' stream URLs)")
    ap.add_argument("--log", help="append the request log to this file (and <file>.jsonl) as well as stdout")
    ap.add_argument("--loop", default=os.path.join(GEN, "radio-loop.mp3"), help="the MP3 sent for ever; made with ffmpeg when missing")
    ap.add_argument("--page-cap", type=int, default=2, help="the most stations in one /json/stations answer (0 = only `limit`)")
    ap.add_argument("--switch-s", type=float, default=20.0, help="seconds after a connection opens at which QA Song 1 becomes QA Song 2")
    ap.add_argument("--burst-s", type=float, default=4.0, help="seconds of audio sent ahead of real time")
    args = ap.parse_args()
    if not os.path.exists(args.loop):
        make_loop(args.loop)
    with open(args.loop, "rb") as f:
        Fixture.loop = f.read()
    if len(Fixture.loop) < BYTES_PER_S:
        sys.exit("the loop file is too short: " + args.loop)
    Fixture.base = "http://%s:%d" % (args.public_host, args.port)
    Fixture.log_file, Fixture.page_cap = args.log, args.page_cap
    Fixture.switch_s, Fixture.burst_s = args.switch_s, args.burst_s
    ThreadingHTTPServer.daemon_threads = True
    server = ThreadingHTTPServer((args.bind, args.port), Fixture)
    print("radio fixture on %s:%d pid %d, stations at %s, loop %d B" % (args.bind, args.port, os.getpid(), Fixture.base, len(Fixture.loop)), file=sys.stderr, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
