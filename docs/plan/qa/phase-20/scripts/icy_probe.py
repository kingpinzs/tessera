#!/usr/bin/env python3
"""Phase 20 — read an ICY stream as a player does and print what its metadata said, and when.

usage: icy_probe.py <url> [seconds=26] [user-agent]
Sends `Icy-MetaData: 1`, prints the response's status and icy-* headers, then one line per StreamTitle CHANGE with the
seconds since the connection opened, and at the end the audio byte count and whether it starts with an MP3 frame sync.
Exit 0 when the stream answered 200 with an icy-metaint; 1 otherwise. Opens no audio device.
"""
import http.client
import sys
import time
from urllib.parse import urlsplit

url = urlsplit(sys.argv[1])
seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 26.0
agent = sys.argv[3] if len(sys.argv) > 3 else "icy_probe/1 (qa)"
conn = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=10)
opened = time.monotonic()
conn.request("GET", url.path or "/", headers={"Icy-MetaData": "1", "User-Agent": agent})
r = conn.getresponse()
print("status %d" % r.status)
for k, v in r.getheaders():
    if k.lower().startswith("icy-") or k.lower() in ("content-type", "content-length", "connection"):
        print("header %s: %s" % (k, v))
metaint = int(r.getheader("icy-metaint") or 0)
if r.status != 200 or not metaint:
    print("no icy-metaint")
    sys.exit(1)
audio = 0
head = b""
last = None
blocks = 0
while time.monotonic() - opened < seconds:
    chunk = r.read(metaint)
    if len(chunk) < metaint:
        break
    if not head:
        head = chunk[:4]
    audio += len(chunk)
    n = r.read(1)[0] * 16
    blocks += 1
    if n:
        text = r.read(n).rstrip(b"\0").decode("utf-8", "replace")
        if text != last:
            print("%5.1f s  %s" % (time.monotonic() - opened, text))
            last = text
elapsed = time.monotonic() - opened
print("read %d B of audio in %.1f s (%.0f B/s), %d metadata slots, first bytes %s (MP3 frame sync: %s)"
      % (audio, elapsed, audio / elapsed, blocks, head.hex(), "yes" if head[:1] == b"\xff" and head[1] & 0xE0 == 0xE0 else "NO"))
conn.close()
