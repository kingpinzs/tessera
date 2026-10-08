#!/usr/bin/env python3
"""Phase 20 — the music catalogue fixture (build task 11; row A6): MusicBrainz and the Cover Art Archive in one server.

A stand-in so that no row touches the live MusicBrainz or coverartarchive.org. The AVD reaches it at
http://10.0.2.2:<port>/ — the debug prefs are `qa_music_catalogue_base` = …/ws/2/ and `qa_coverart_base` = …/coverart/.

  /ws/2/recording?query=<Lucene>&fmt=json&limit=<n>
        MusicBrainz's `recording` search answer. ONE answer exists: a query holding the words "qa artist" (any case,
        with or without field names and quotes) returns QA Song A, QA Song B and QA Song C by QA Artist, each with one
        release. Anything else returns the empty answer ({"count":0,"recordings":[]}), as MusicBrainz does for no hit.
  /coverart/release/<mbid>/front[-250|-500|-1200]
        a PNG for the two releases this fixture knows (200, image/png — no redirect: the fixture is one host);
        404 for any other id, as the archive answers a release with no front image.
  /__qa/ready
        200 "ok"; NEVER logged.

usage: catalogue_server.py [--port 8081] [--bind 0.0.0.0] [--log FILE]

THE LOG. Every request (but /__qa/…) goes to stdout and to --log as

    <n> <HH:MM:SS.mmm> <METHOD> <path?query> -> <status>
        <Header>: <value>            one line per request header, as received

and, with --log, as one JSON object per line in <FILE>.jsonl ({"n","t","method","target","path","status","headers"}).
The fixture has no key; nothing in the log is a secret.
"""
import argparse
import json
import os
import re
import struct
import sys
import threading
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

ARTIST_ID = "a0000000-0000-4000-8000-00000000000a"
RELEASE_ONE = "e1111111-1111-4111-8111-111111111111"   # "QA Album One": QA Song A and QA Song C
RELEASE_TWO = "e2222222-2222-4222-8222-222222222222"   # "QA Album Two": QA Song B
COVERS = {RELEASE_ONE: (200, 60, 40), RELEASE_TWO: (40, 90, 200)}   # two PNGs, two flat colours
# title, recording id, length (ms), release id, release title, release-group id
RECORDINGS = [
    ("QA Song A", "c000000a-0000-4000-8000-00000000000a", 181000, RELEASE_ONE, "QA Album One", "f1111111-1111-4111-8111-111111111111"),
    ("QA Song B", "c000000b-0000-4000-8000-00000000000b", 202000, RELEASE_TWO, "QA Album Two", "f2222222-2222-4222-8222-222222222222"),
    ("QA Song C", "c000000c-0000-4000-8000-00000000000c", 223000, RELEASE_ONE, "QA Album One", "f1111111-1111-4111-8111-111111111111"),
]


def png(rgb, side=250):
    """A flat-colour PNG (8-bit RGB), written by hand as phase 17's fixture does: no imaging library, no file kept."""
    row = b"\x00" + bytes(rgb) * side

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", side, side, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(row * side, 9)) + chunk(b"IEND", b""))


def search_answer(hit):
    """MusicBrainz's recording-search JSON (mb-search-recording.json): the fields the addendum §2 lists, and their kin."""
    recordings = []
    for i, (title, rid, length, release, release_title, group) in enumerate(RECORDINGS if hit else [], 1):
        recordings.append({
            "id": rid, "score": 100, "title": title, "length": length, "video": None,
            "artist-credit": [{"name": "QA Artist", "artist": {"id": ARTIST_ID, "name": "QA Artist", "sort-name": "Artist, QA"}}],
            "first-release-date": "2020-01-01",
            "releases": [{
                "id": release, "status-id": "4e304316-386d-3409-af2e-78857eec5cfe", "count": 1, "title": release_title,
                "status": "Official", "date": "2020-01-01", "country": "US",
                "artist-credit": [{"name": "QA Artist", "artist": {"id": ARTIST_ID, "name": "QA Artist", "sort-name": "Artist, QA"}}],
                "release-group": {"id": group, "type-id": "f529b476-6e62-324f-b0aa-1f3e33d313fc", "primary-type-id": "f529b476-6e62-324f-b0aa-1f3e33d313fc",
                                  "title": release_title, "primary-type": "Album"},
                "track-count": 2,
                "media": [{"position": 1, "format": "Digital Media",
                           "track": [{"id": "d%07d-0000-4000-8000-000000000000" % i, "number": str(i), "title": title, "length": length}],
                           "track-count": 2, "track-offset": i - 1}],
            }],
        })
    return {"created": time.strftime("%Y-%m-%dT%H:%M:%S.000Z", time.gmtime()), "count": len(recordings), "offset": 0, "recordings": recordings}


class Fixture(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    log_file = None
    lock = threading.Lock()
    count = 0

    def log_message(self, *_):
        pass

    def note(self, status):
        with Fixture.lock:
            Fixture.count += 1
            now = time.time()
            stamp = time.strftime("%H:%M:%S", time.localtime(now)) + ".%03d" % (int(now * 1000) % 1000)
            lines = ["%d %s %s %s -> %d" % (Fixture.count, stamp, self.command, self.path, status)]
            lines += ["    %s: %s" % (k, v) for k, v in self.headers.items()]
            print("\n".join(lines), flush=True)
            if Fixture.log_file:
                with open(Fixture.log_file, "a", encoding="utf-8") as f:
                    f.write("\n".join(lines) + "\n")
                with open(Fixture.log_file + ".jsonl", "a", encoding="utf-8") as f:
                    f.write(json.dumps({"n": Fixture.count, "t": stamp, "method": self.command, "target": self.path,
                                        "path": urlsplit(self.path).path, "status": status,
                                        "headers": [[k, v] for k, v in self.headers.items()]}) + "\n")

    def send(self, status, body, content_type="application/json; charset=utf-8", log=True):
        if log:
            self.note(status)
        data = body if isinstance(body, bytes) else json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urlsplit(self.path)
        path = url.path.rstrip("/")
        query = parse_qs(url.query)
        if path.startswith("/__qa/"):
            return self.send(200, b"ok\n", "text/plain", log=False)

        if path == "/ws/2/recording":
            # Lucene or plain: `qa artist`, `"qa artist"`, `artist:"QA Artist"`, `recording:qa AND artist:artist` …
            words = re.sub(r"[^a-z0-9 ]+", " ", re.sub(r"\b[a-z]+:", " ", " ".join(query.get("query", [])).lower())).split()
            return self.send(200, search_answer("qa" in words and "artist" in words))

        m = re.fullmatch(r"/coverart/release/([0-9a-f-]{36})/front(-250|-500|-1200)?", path)
        if m:
            colour = COVERS.get(m.group(1))
            if colour is None:
                return self.send(404, b"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n<title>404 Not Found</title>\n<h1>Not Found</h1>\n<p>No cover art found for release %s</p>\n" % m.group(1).encode(), "text/html")
            return self.send(200, png(colour), "image/png")

        return self.send(404, {"error": "Not Found", "help": "For usage, please see: https://musicbrainz.org/development/mmd"})


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--port", type=int, default=8081)
    ap.add_argument("--bind", default="0.0.0.0")
    ap.add_argument("--log", help="append the request log to this file (and <file>.jsonl) as well as stdout")
    args = ap.parse_args()
    Fixture.log_file = args.log
    ThreadingHTTPServer.daemon_threads = True
    server = ThreadingHTTPServer((args.bind, args.port), Fixture)
    print("music catalogue fixture on %s:%d pid %d" % (args.bind, args.port, os.getpid()), file=sys.stderr, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
