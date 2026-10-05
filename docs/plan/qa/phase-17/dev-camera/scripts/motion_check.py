#!/usr/bin/env python3
"""Reads a Motion Photo the way E7 does, with no exiftool: the XMP's MotionPhoto value, the Container:Directory's video
item length, and the size of the MP4 that really ends the file (from the last `ftyp` box's start to the end). Writes
the trailing MP4 to argv[2]. Prints `MotionPhoto=<v> item_length=<n> trailing_mp4=<n> length_matches=<yes|no> timestamp_us=<n>`."""
import re, sys

data = open(sys.argv[1], 'rb').read()
xmp_at = data.find(b'http://ns.adobe.com/xap/1.0/\x00')
xmp = ''
if xmp_at >= 0:
    length = int.from_bytes(data[xmp_at - 2:xmp_at], 'big')
    xmp = data[xmp_at + 29:xmp_at - 2 + length].decode('utf-8', 'replace')
motion = re.search(r'Camera:MotionPhoto="(\d+)"', xmp)
stamp = re.search(r'MotionPhotoPresentationTimestampUs="(-?\d+)"', xmp)
item = None
for m in re.finditer(r'<Container:Item\b[^>]*>', xmp):
    if 'Item:Semantic="MotionPhoto"' in m.group(0):
        item = re.search(r'Item:Length="(\d+)"', m.group(0))
ftyp = data.rfind(b'ftyp')
start = ftyp - 4 if ftyp >= 4 else -1
trailing = len(data) - start if start >= 0 else 0
if start >= 0 and len(sys.argv) > 2:
    open(sys.argv[2], 'wb').write(data[start:])
item_length = int(item.group(1)) if item else -1
print('MotionPhoto=%s item_length=%d trailing_mp4=%d length_matches=%s timestamp_us=%s jpeg_bytes=%d' % (
    motion.group(1) if motion else 'none', item_length, trailing, 'yes' if item_length == trailing and trailing > 0 else 'no',
    stamp.group(1) if stamp else 'none', start))
