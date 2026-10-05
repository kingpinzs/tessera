#!/usr/bin/env python3
"""Phase 17 — the catalogue fixture (the acceptance preamble's "Network fixtures"; build tasks 11, 12, 16).

A stand-in on port 8090 for the three things Movies & TV calls over the network, so that no row ever touches the live
TMDB, the live Wikidata or a real service. The AVD reaches it at http://10.0.2.2:8090/.

  /3/…                 TMDB v3 in TMDB's shape, from ../fixtures/catalogue/*.json. A request without the expected
                       bearer token is answered as TMDB answers a refused key: 401, status_code 7.
  /img/<size>/<name>   a poster: a flat-colour PNG drawn here from posters.json (no image file is kept).
  /wikidata/sparql     a Wikidata-Query-Service-shaped answer for the TMDB id in the query.
  /qa-steps.mp4        the video file given with --video (E13), with Range support.
  /500/… /404/… /429/… /401/…   every path under these answers that status (the app's base URL is pointed at one).

usage: catalogue_server.py [--port 8090] [--public-host 10.0.2.2] [--token qa-dummy-token] [--video PATH] [--log FILE]

THE LOG (T17-13, C-32). One line per request, to stdout and to --log:

    <n> GET <path> <authorisation> api_key=<present|absent> -> <status>

<path> never carries the query string. <authorisation> is `bearer ok` or `bearer missing` on the TMDB API paths
(`/3/…` and the status paths), where the app must send the token. The image and Wikidata paths must NOT be sent the
token — it would be handed to another host on the real network — so there the field reads `authorisation absent`, or
`authorisation PRESENT` when the app sent one (a leak a row fails on). A Wikidata line adds `tmdb=<digits>
kind=<movie|tv>`, the id the query is keyed on. No header value and no parameter value is ever written.
"""
import argparse
import json
import os
import re
import struct
import sys
import threading
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, "..", "fixtures", "catalogue")
STATUS_PATHS = {"500": 500, "404": 404, "429": 429, "401": 401}
TMDB_REFUSED = {"status_code": 7, "status_message": "Invalid API key: You must be granted a valid key.", "success": False}
TMDB_BUSY = {"status_code": 25, "status_message": "Your request count is over the allowed limit.", "success": False}
TMDB_NOT_FOUND = {"status_code": 34, "status_message": "The resource you requested could not be found.", "success": False}


def fixture(name):
    with open(os.path.join(FIXTURES, name), encoding="utf-8") as f:
        return json.load(f)


def png(rgb, width=228, height=320):
    """A flat-colour PNG (8-bit RGB), written by hand so the fixture needs no imaging library."""
    row = b"\x00" + bytes(rgb) * width

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(row * height, 9)) + chunk(b"IEND", b""))


class Fixture(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    token = "qa-dummy-token"
    port = 8090
    public_host = "10.0.2.2"
    video = None
    log_file = None
    lock = threading.Lock()
    count = 0

    def log_message(self, *_):  # the handler's own access log would print the request line, query string and all
        pass

    def note(self, path, auth, api_key, status, extra=""):
        with Fixture.lock:
            Fixture.count += 1
            line = "%d %s %s %s api_key=%s%s -> %d" % (Fixture.count, self.command, path, auth, "present" if api_key else "absent", extra, status)
            print(line, flush=True)
            if Fixture.log_file:
                with open(Fixture.log_file, "a", encoding="utf-8") as f:
                    f.write(line + "\n")

    def send(self, status, body, content_type="application/json;charset=utf-8", headers=None):
        data = body if isinstance(body, bytes) else json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urlsplit(self.path)
        path = url.path
        query = parse_qs(url.query)
        api_key = "api_key" in query
        header = self.headers.get("Authorization")
        bearer_ok = header == "Bearer " + Fixture.token
        api_auth = "bearer ok" if bearer_ok else "bearer missing"
        other_auth = "authorisation PRESENT" if header else "authorisation absent"
        parts = [p for p in path.split("/") if p]

        # The status paths: everything under /500, /404, /429, /401.
        if parts and parts[0] in STATUS_PATHS:
            status = STATUS_PATHS[parts[0]]
            body = {500: {"status_code": 11, "status_message": "Internal error: Something went wrong, contact TMDB.", "success": False},
                    404: TMDB_NOT_FOUND, 429: TMDB_BUSY, 401: TMDB_REFUSED}[status]
            self.note(path, api_auth, api_key, status)
            return self.send(status, body, headers={"Retry-After": "60"} if status == 429 else None)

        if parts[:1] == ["img"]:
            name = os.path.splitext(parts[-1])[0] if len(parts) >= 2 else ""
            colours = fixture("posters.json")
            status = 200 if name in colours else 404
            self.note(path, other_auth, api_key, status)
            if status == 404:
                return self.send(404, b"", "text/plain")
            return self.send(200, png(colours[name]), "image/png")

        if parts[:2] == ["wikidata", "sparql"]:
            text = " ".join(query.get("query", []))
            m = re.search(r'wdt:(P4947|P4983)\s+"(\d+)"', text)
            answers = fixture("wikidata.json")
            tmdb = m.group(2) if m else ""
            kind = {"P4947": "movie", "P4983": "tv"}.get(m.group(1), "none") if m else "none"
            self.note(path, other_auth, api_key, 200, " tmdb=%s kind=%s" % (tmdb or "none", kind))
            return self.send(200, answers.get(tmdb, answers["*"]), "application/sparql-results+json;charset=utf-8")

        if parts[:1] == ["3"]:
            if not bearer_ok:
                self.note(path, api_auth, api_key, 401)
                return self.send(401, TMDB_REFUSED)
            rest = "/".join(parts[1:])
            body = None
            if rest == "configuration":
                # The image base is THIS server, whatever port it runs on (T17-20): the recorded file names 8090.
                body = fixture("configuration.json")
                base = "http://%s:%d/img/" % (Fixture.public_host, Fixture.port)
                body["images"]["base_url"] = body["images"]["secure_base_url"] = base
            elif rest == "search/multi":
                q = " ".join(query.get("query", [])).strip().lower()
                body = fixture({"blade runner": "search_blade_runner.json", "no artwork": "search_no_artwork.json"}.get(q, "search_empty.json"))
            elif rest in ("trending/all/week", "movie/popular", "tv/popular"):
                body = fixture("sections.json")[rest]
            elif re.fullmatch(r"(movie|tv)/\d+/watch/providers", rest):
                body = fixture("providers.json").get(rest.rsplit("/watch/providers", 1)[0], {"id": int(parts[2]), "results": {}})
            elif re.fullmatch(r"(movie|tv)/\d+", rest):
                name = rest.replace("/", "_") + ".json"
                if os.path.exists(os.path.join(FIXTURES, name)):
                    body = fixture(name)
            status = 200 if body is not None else 404
            self.note(path, api_auth, api_key, status)
            return self.send(status, body if body is not None else TMDB_NOT_FOUND)

        if path == "/qa-steps.mp4" and Fixture.video and os.path.exists(Fixture.video):
            data = open(Fixture.video, "rb").read()
            rng = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", "") or "")
            if rng and (rng.group(1) or rng.group(2)):
                start = int(rng.group(1)) if rng.group(1) else max(0, len(data) - int(rng.group(2)))
                end = min(int(rng.group(2)), len(data) - 1) if rng.group(1) and rng.group(2) else len(data) - 1
                if start >= len(data):
                    self.note(path, other_auth, api_key, 416)
                    return self.send(416, b"", "video/mp4", {"Content-Range": "bytes */%d" % len(data)})
                self.note(path, other_auth, api_key, 206)
                return self.send(206, data[start:end + 1], "video/mp4",
                                 {"Content-Range": "bytes %d-%d/%d" % (start, end, len(data)), "Accept-Ranges": "bytes"})
            self.note(path, other_auth, api_key, 200)
            return self.send(200, data, "video/mp4", {"Accept-Ranges": "bytes"})

        self.note(path, other_auth, api_key, 404)
        return self.send(404, TMDB_NOT_FOUND)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--port", type=int, default=8090)
    ap.add_argument("--token", default="qa-dummy-token", help="the bearer token the TMDB paths accept (never logged)")
    ap.add_argument("--public-host", default="10.0.2.2", help="the host the DEVICE reaches this server at (the image base in /3/configuration)")
    ap.add_argument("--video", help="the file served at /qa-steps.mp4")
    ap.add_argument("--log", help="append the request log to this file as well as stdout")
    args = ap.parse_args()
    Fixture.token, Fixture.video, Fixture.log_file = args.token, args.video, args.log
    Fixture.port, Fixture.public_host = args.port, args.public_host
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Fixture)  # the AVD's 10.0.2.2 is the host's loopback
    print("catalogue fixture on :%d pid %d" % (args.port, os.getpid()), file=sys.stderr, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
