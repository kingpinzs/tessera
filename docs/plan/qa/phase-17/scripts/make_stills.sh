#!/usr/bin/env bash
# Phase 17 fixtures for the "HEIC and RAW" edge case (the Fixtures paragraph, r3 V14: "a DNG and a HEIC still whose source
# is recorded beside them, ffmpeg 4.4 being unable to write HEIC"). No HEIC or DNG file from a camera exists on this
# host, so both are MADE here from qa-photo-0.png / a flat field, with what the host has, and `qa-stills.source` beside
# them says exactly how, and what could not be made:
#
#   qa-still.heic  ImageMagick 6.9 `convert` with its heic delegate (libheif): a real HEIF container holding one HEVC
#                  still, 640 x 480, the flat colour of qa-photo-0 (220,40,40). NOT a phone's file: no EXIF, no
#                  thumbnail item, no Samsung maker note, 8-bit 4:2:0 — the S25U's own HEIC is P3's.
#   qa-still.dng   ImageMagick cannot write DNG (its DNG coder is read-only, through ufraw-batch, which is not
#                  installed) and no dng tool is on the host. What is written is a minimal LINEAR DNG by hand: a
#                  baseline TIFF (PIL) of a flat (40,90,220) field with the DNG tags that make it one — DNGVersion
#                  1.4, DNGBackwardVersion 1.1, UniqueCameraModel, PhotometricInterpretation = LinearRaw (34892),
#                  ColorMatrix1 (identity-like), AsShotNeutral 1 1 1, CalibrationIlluminant1 = D65. It has NO embedded
#                  JPEG preview and no CFA mosaic, so the bullet's "shows its embedded preview" branch cannot be shown
#                  with it — only "or a placeholder" / a render of the linear data; a camera's DNG (with a preview) is
#                  P3's.
# usage: make_stills.sh <dir>   (needs <dir>/qa-photo-0.png: make_photos.py <dir>)
set -euo pipefail
OUT="${1:?usage: make_stills.sh <dir>}"
SRC="$OUT/qa-stills.source"
: > "$SRC"
say() { echo "$*" | tee -a "$SRC"; }
say "made $(date -Is) on $(uname -n | md5sum | cut -c1-8) with: $(convert -version | head -1)"
say "convert delegates: $(convert -list configure | sed -n 's/^DELEGATES *//p' | head -1)"

rm -f "$OUT/qa-still.heic" "$OUT/qa-still.dng"
if convert "$OUT/qa-photo-0.png" "$OUT/qa-still.heic" 2> "$OUT/.heic.err" && [ -s "$OUT/qa-still.heic" ]; then
  say "qa-still.heic: MADE by 'convert qa-photo-0.png qa-still.heic' (libheif delegate) — $(stat -c%s "$OUT/qa-still.heic") bytes, $(file -b "$OUT/qa-still.heic")"
  say "qa-still.heic: decoded back on the host: $(convert "$OUT/qa-still.heic" -colorspace sRGB -format '%w x %h, centre %[pixel:p{320,240}]' info: 2>&1 | head -1)"
  say "qa-still.heic: source = the generated flat-colour qa-photo-0.png (220,40,40); it is NOT an S25U file (no EXIF, no thumbnail) — the phone's HEIC is P3"
else
  say "qa-still.heic: COULD NOT BE MADE: $(head -1 "$OUT/.heic.err")"
fi
rm -f "$OUT/.heic.err"

python3 - "$OUT/qa-still.dng" >> "$SRC" 2>&1 <<'PY' || echo "qa-still.dng: COULD NOT BE MADE (the script above failed)" >> "$SRC"
import struct, sys
from fractions import Fraction
from PIL import Image, TiffImagePlugin as T
path = sys.argv[1]
ifd = T.ImageFileDirectory_v2()
def put(tag, kind, value):
    ifd[tag] = value; ifd.tagtype[tag] = kind
put(50706, 1, bytes([1, 4, 0, 0]))            # DNGVersion
put(50707, 1, bytes([1, 1, 0, 0]))            # DNGBackwardVersion
put(50708, 2, "QA Fixture")                   # UniqueCameraModel
put(50721, 10, tuple(Fraction(v) for v in (1, 0, 0, 0, 1, 0, 0, 0, 1)))   # ColorMatrix1
put(50728, 5, (Fraction(1), Fraction(1), Fraction(1)))                    # AsShotNeutral
put(50778, 3, 21)                             # CalibrationIlluminant1 = D65
Image.new("RGB", (640, 480), (40, 90, 220)).save(path, format="TIFF", tiffinfo=ifd, compression="raw")
# PIL writes PhotometricInterpretation = RGB (2); a linear DNG says LinearRaw (34892): the IFD entry is patched in place.
d = bytearray(open(path, "rb").read())
end = "<" if d[:2] == b"II" else ">"
off = struct.unpack(end + "I", d[4:8])[0]
n = struct.unpack(end + "H", d[off:off + 2])[0]
patched = False
for i in range(n):
    e = off + 2 + i * 12
    tag, kind, count = struct.unpack(end + "HHI", d[e:e + 8])
    if tag == 262 and kind == 3 and count == 1:
        d[e + 8:e + 10] = struct.pack(end + "H", 34892); patched = True
open(path, "wb").write(d)
print("qa-still.dng: MADE by hand — a baseline TIFF from PIL %s with the DNG tags (DNGVersion 1.4, LinearRaw %s, ColorMatrix1, AsShotNeutral, D65), %d bytes, flat (40,90,220), 640 x 480" % (Image.__version__, "patched in" if patched else "NOT patched", len(d)))
print("qa-still.dng: it has NO embedded JPEG preview and no CFA data; ImageMagick's DNG coder is read-only (ufraw-batch, not installed) and the host has no DNG writer — a camera's DNG is P3")
PY
[ -f "$OUT/qa-still.dng" ] && say "qa-still.dng: $(file -b "$OUT/qa-still.dng" | cut -c1-160)"
cat "$SRC" > /dev/null
