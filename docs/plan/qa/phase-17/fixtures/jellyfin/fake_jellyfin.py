#!/usr/bin/env python3
"""Phase 17 — a FAKE media server for the trust legs (row TRUST_VIDEO; the fixes file's B-1 (3), B-2, B-4, B-5, B-9
and review round 2's B2-M1 / B2-M2). Not Jellyfin: the few answers the shell's client reads, in Jellyfin's shape, with
a request log that says whether a request carried the token — which the real container cannot show a row.

  POST /Users/AuthenticateByName   the sign-in answer, by MODE:
        ok       a plain token and a user id
        cr       the same token with a carriage return inside (B-1 (3): not a usable token)
        huge     an answer over the client's 16 MiB JSON cap (B-9), padded in memory, never on disk
        refuse   401 (a wrong password)
  GET  /Items…                     one video, "qa-steps"; 401 instead once the mode is `revoked` (B-2)
  GET  /Videos/<id>/stream         the file given with --video, with Range support
  GET  /__mode/<mode>              the driver's own switch (host only); /__ready answers 200

usage: fake_jellyfin.py --port 8097 --video PATH --log FILE [--mode ok]

THE LOG. One line per request, to --log, never a header or parameter VALUE:

    <n> <METHOD> <path> token=<header|absent> apikey=<present|absent> -> <status>

`token=header` means the request's Authorization header carried the token this server issued; `apikey=present` that
the query string named ApiKey or api_key. The fixture's token and user id are fixture values, in this file by design.
"""
import argparse
import json
import os
import re
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

TOKEN = "qafaketoken0123456789abcdef012345"
USER_ID = "0f0e0d0c0b0a09080706050403020100"
ITEM_ID = "00112233445566778899aabbccddeeff"
MODES = ("ok", "cr", "huge", "refuse", "revoked")


class Fake(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    mode = "ok"
    video = None
    log_file = None
    lock = threading.Lock()
    count = 0

    def log_message(self, *_):
        pass

    def note(self, path, status):
        header = self.headers.get("Authorization") or ""
        query = parse_qs(urlsplit(self.path).query)
        with Fake.lock:
            Fake.count += 1
            line = "%d %s %s token=%s apikey=%s -> %d" % (
                Fake.count, self.command, path, "header" if TOKEN in header else "absent",
                "present" if any(k.lower() in ("apikey", "api_key") for k in query) else "absent", status)
            if Fake.log_file:
                with open(Fake.log_file, "a", encoding="utf-8") as f:
                    f.write(line + "\n")

    def send(self, status, body, content_type="application/json; charset=utf-8", headers=None):
        data = body if isinstance(body, bytes) else json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            try:
                self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_POST(self):
        path = urlsplit(self.path).path
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)          # the sign-in body (a password): read and dropped, never logged
        if path == "/Users/AuthenticateByName":
            if Fake.mode == "refuse":
                self.note(path, 401)
                return self.send(401, {})
            answer = {"User": {"Id": USER_ID, "Name": "qa"}, "AccessToken": TOKEN, "ServerId": "fake"}
            if Fake.mode == "cr":
                answer["AccessToken"] = TOKEN[:8] + "\r" + TOKEN[8:]
            if Fake.mode == "huge":
                answer["Padding"] = "x" * (17 * 1024 * 1024)
            self.note(path, 200)
            return self.send(200, answer)
        self.note(path, 404)
        return self.send(404, {})

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/__ready":
            return self.send(200, {"mode": Fake.mode})
        m = re.fullmatch(r"/__mode/([a-z]+)", path)
        if m:
            if m.group(1) not in MODES:
                return self.send(400, {})
            Fake.mode = m.group(1)
            return self.send(200, {"mode": Fake.mode})
        if path == "/System/Info/Public":
            self.note(path, 200)
            return self.send(200, {"Version": "0-fake", "ServerName": "fake"})
        if path == "/Items" or path.startswith("/Users/"):
            if Fake.mode == "revoked" or TOKEN not in (self.headers.get("Authorization") or ""):
                self.note(path, 401)
                return self.send(401, {})
            self.note(path, 200)
            return self.send(200, {"Items": [{"Id": ITEM_ID, "Name": "qa-steps", "Type": "Movie"}], "TotalRecordCount": 1})
        if re.fullmatch(r"/Videos/[0-9A-Fa-f-]+/stream", path) and Fake.video and os.path.exists(Fake.video):
            data = open(Fake.video, "rb").read()
            rng = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", "") or "")
            if rng and (rng.group(1) or rng.group(2)):
                start = int(rng.group(1)) if rng.group(1) else max(0, len(data) - int(rng.group(2)))
                end = min(int(rng.group(2)), len(data) - 1) if rng.group(1) and rng.group(2) else len(data) - 1
                if start >= len(data):
                    self.note(path, 416)
                    return self.send(416, b"", "video/mp4", {"Content-Range": "bytes */%d" % len(data)})
                self.note(path, 206)
                return self.send(206, data[start:end + 1], "video/mp4",
                                 {"Content-Range": "bytes %d-%d/%d" % (start, end, len(data)), "Accept-Ranges": "bytes"})
            self.note(path, 200)
            return self.send(200, data, "video/mp4", {"Accept-Ranges": "bytes"})
        self.note(path, 404)
        return self.send(404, {})


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--port", type=int, default=8097)
    ap.add_argument("--video")
    ap.add_argument("--log")
    ap.add_argument("--mode", default="ok", choices=MODES)
    args = ap.parse_args()
    Fake.video, Fake.log_file, Fake.mode = args.video, args.log, args.mode
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Fake)   # the AVD's 10.0.2.2 is the host's loopback
    print("fake media server on :%d pid %d" % (args.port, os.getpid()), file=sys.stderr, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
