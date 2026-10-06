#!/usr/bin/env python3
"""Makes the three hostile stills of dev-living's L2 in a folder: each a small JPEG with a Motion Photo XMP packet (the
form `media/MotionPhoto.kt` writes) and bytes after the JPEG's end.

  living-broken.jpg   the directory's length is right and the bytes there start with an `ftyp` box, but what follows is
                      not an MP4: the reader takes it as a Living Image; the player must fail cleanly.
  living-lying.jpg    the directory's length is three times the file's size: not a Living Image.
  living-notmp4.jpg   the directory's length is right but the bytes there are not an MP4: not a Living Image.

usage: make_living_fixtures.py <out dir>     prints "<name> <size> <clip bytes>" per file
"""
import io, os, struct, sys
from PIL import Image

XMP_HEADER = b"http://ns.adobe.com/xap/1.0/\x00"
CLIP = 4096


def xmp(length):
    return ('<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="dev-living fixture">'
            '<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">'
            '<rdf:Description rdf:about="" xmlns:Camera="http://ns.google.com/photos/1.0/camera/" '
            'xmlns:Container="http://ns.google.com/photos/1.0/container/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/" '
            'Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1" Camera:MotionPhotoPresentationTimestampUs="0">'
            '<Container:Directory><rdf:Seq>'
            '<rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li>'
            '<rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="%d" Item:Padding="0"/></rdf:li>'
            '</rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>' % length).encode()


def jpeg(colour):
    out = io.BytesIO()
    Image.new("RGB", (640, 480), colour).save(out, "JPEG", quality=90)
    return out.getvalue()


def with_xmp(picture, length):
    payload = XMP_HEADER + xmp(length)
    # After SOI and the JFIF APP0 segment PIL writes first.
    at = 2
    if picture[2:4] == b"\xff\xe0":
        at = 4 + struct.unpack(">H", picture[4:6])[0]
    return picture[:at] + b"\xff\xe1" + struct.pack(">H", len(payload) + 2) + payload + picture[at:]


def noise(n, seed):
    out = bytearray()
    x = seed
    while len(out) < n:
        x = (x * 1103515245 + 12345) & 0x7FFFFFFF
        out.append((x >> 16) & 0xFF)
    return bytes(out)


out_dir = sys.argv[1]
os.makedirs(out_dir, exist_ok=True)
broken_clip = struct.pack(">I", 24) + b"ftypmp42" + b"\x00\x00\x00\x00" + b"mp42isom" + noise(CLIP - 24, 7)
files = {
    "living-broken.jpg": with_xmp(jpeg((200, 60, 60)), CLIP) + broken_clip,
    "living-notmp4.jpg": with_xmp(jpeg((60, 60, 200)), CLIP) + noise(CLIP, 11),
}
lying = jpeg((60, 180, 60))
files["living-lying.jpg"] = with_xmp(lying, 3 * (len(lying) + CLIP + 1024)) + broken_clip
for name, data in files.items():
    open(os.path.join(out_dir, name), "wb").write(data)
    print(name, len(data), CLIP)
