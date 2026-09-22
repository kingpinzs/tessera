#!/usr/bin/env bash
# Phase 05: the keyboard's offline dictionary and its emoji panel artwork.
#
# Neither is in git: both are derived from third-party sources that are pinned here by URL and
# sha256 (or git commit + a sha256 over the files used), so the build is reproducible without
# carrying the bytes. Run it once after a clone; downloads are cached in .keyboardsrc/ and the
# outputs are regenerated from them on every run.
#
#   tools/fetch-keyboard.sh
#
# What it produces (all git-ignored):
#   app/src/main/assets/keyboard/en_US.tsv                 word<TAB>count, count descending then word
#                                                          ascending (code point order), no header
#   app/src/main/assets/keyboard/emoji/<codepoints>.png    128x128 RGBA, Fluent Emoji "Flat", default
#                                                          skin tone; e.g. 1f600.png, 0023-fe0f-20e3.png
#   app/src/main/assets/keyboard/emoji/index.tsv           one line per emoji in emoji-test.txt order
#   app/src/main/assets/licenses/scowl-2020.12.07-Copyright.txt
#   app/src/main/assets/licenses/12dicts-6.0.2-LICENSE.txt
#   app/src/main/assets/licenses/12dicts-6.0.2-agid.txt
#   app/src/main/assets/licenses/fluentui-emoji-MIT.txt
#   app/src/main/assets/licenses/unicode-emoji-LICENSE.txt
#
# Sources (licence quotes and the rejected candidates are in docs/plan/qa/phase-05/BUILD-START.md):
#   Dictionary membership: SCOWL 2020.12.07, Kevin Atkinson's permissive notice plus public-domain
#     parts (12dicts, ENABLE, Moby, Kelk); the same `mk-list ... en_US` recipe as the official
#     hunspell en_US dictionary, size 60, with level-1 spelling variants and accented forms kept.
#     Not the 2026 SCOWLv2 release: that one mixes in COCA n-gram data used under an NDA.
#   Dictionary ranking: 12dicts 6.0.2 "2+2+3frq" (Alan Beale, released to the public domain, no
#     requirements beyond AGID's permissive notice), 21 frequency bands, American English.
#   Emoji artwork: microsoft/fluentui-emoji at a pinned commit, MIT.
#   Emoji order and names: unicode.org emoji-test.txt 17.0, Unicode License V3.
#
# Determinism. Every output byte is a function of the pinned inputs and nothing else: the word
# list is sorted by (count, code points) in Python, never by the locale's `sort`; the PNGs are
# rendered by Inkscape and then rewritten keeping only IHDR/PLTE/tRNS/IDAT/IEND so no tool
# metadata (pHYs, tEXt "Software", timestamps) reaches the APK; the Fluent tree hash is taken over
# a byte-sorted (LC_ALL=C) file list. The phase 03 zip-timestamp lesson applies: nothing here
# takes the host's timezone or locale into account.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$root/.keyboardsrc"
assets="$root/app/src/main/assets/keyboard"
licenses="$root/app/src/main/assets/licenses"

SCOWL_URL="https://downloads.sourceforge.net/wordlist/scowl-2020.12.07.tar.gz"
SCOWL_SHA="5587667caa20c4891390c2d42dbb4d5c4c3f41bee77af1457ece3ba23fb859cc"
DICTS_URL="https://downloads.sourceforge.net/wordlist/12dicts-6.0.2.zip"
DICTS_SHA="64ac1d35acb66b550c7ebc56e080b62e0bad8f5984d72059dc2e05ac48780e52"
EMOJI_TEST_URL="https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt"
EMOJI_TEST_SHA="1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda"
# license.txt is not versioned at unicode.org; a mismatch here most likely means the copyright
# year rolled over. Re-read it, confirm it is still the Unicode License V3, and re-pin.
UNICODE_LICENSE_URL="https://www.unicode.org/license.txt"
UNICODE_LICENSE_SHA="e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96"
FLUENT_REPO="https://github.com/microsoft/fluentui-emoji.git"
FLUENT_COMMIT="1ffb34c752ecf5d402f04cfb4b392c77f57c54bc"
# sha256 over `sha256sum` of every file this script reads from that commit (LICENSE, every
# metadata.json, every Flat SVG), listed in byte order. Pins the content, not just the commit.
FLUENT_TREE_SHA="d32f5c93f3f9ed5a1c1469e98b0e8664b4cab0b0a9dc8832c205fd9724f9a008"

for tool in curl tar unzip git perl python3 inkscape sha256sum; do
  command -v "$tool" >/dev/null || { echo "tools/fetch-keyboard.sh needs $tool" >&2; exit 1; }
done
case "$(inkscape --version 2>/dev/null | head -1)" in
  "Inkscape 1."*) ;;
  *) echo "tools/fetch-keyboard.sh needs Inkscape 1.x (--shell actions); found: $(inkscape --version 2>/dev/null | head -1)" >&2; exit 1 ;;
esac

fetch() { # url sha dest
  local url="$1" sha="$2" dest="$3"
  if [ -f "$dest" ] && [ "$(sha256sum "$dest" | cut -d' ' -f1)" = "$sha" ]; then
    echo "have  $(basename "$dest")"; return
  fi
  echo "fetch $(basename "$dest")"
  curl -fsSL -o "$dest.part" "$url"
  local got; got="$(sha256sum "$dest.part" | cut -d' ' -f1)"
  [ "$got" = "$sha" ] || { echo "sha256 mismatch for $url: got $got want $sha" >&2; exit 1; }
  mv "$dest.part" "$dest"
}

mkdir -p "$work" "$licenses"
rm -rf "$assets"
mkdir -p "$assets/emoji"

fetch "$SCOWL_URL"           "$SCOWL_SHA"           "$work/scowl-2020.12.07.tar.gz"
fetch "$DICTS_URL"           "$DICTS_SHA"           "$work/12dicts-6.0.2.zip"
fetch "$EMOJI_TEST_URL"      "$EMOJI_TEST_SHA"      "$work/emoji-test-17.0.txt"
fetch "$UNICODE_LICENSE_URL" "$UNICODE_LICENSE_SHA" "$work/unicode-license.txt"

# ---------------------------------------------------------------------------------------------
# Fluent Emoji: a blob-less fetch of one commit, sparse-checked-out to the Flat SVGs and the
# metadata (the full repository is several GB of PNG/SVG in every style and skin tone).
fluent="$work/fluentui-emoji"
if [ ! -f "$fluent/.pinned-$FLUENT_COMMIT" ]; then
  echo "fetch fluentui-emoji@${FLUENT_COMMIT:0:12} (sparse: Flat SVGs + metadata)"
  rm -rf "$fluent"; mkdir -p "$fluent"
  git -C "$fluent" init -q
  git -C "$fluent" remote add origin "$FLUENT_REPO"
  git -C "$fluent" sparse-checkout set --no-cone '/LICENSE' '/assets/*/metadata.json' '/assets/*/Flat/*.svg' '/assets/*/Default/Flat/*.svg'
  git -C "$fluent" fetch -q --filter=blob:none --depth 1 origin "$FLUENT_COMMIT"
  git -C "$fluent" checkout -q FETCH_HEAD
  touch "$fluent/.pinned-$FLUENT_COMMIT"
else
  echo "have  fluentui-emoji@${FLUENT_COMMIT:0:12}"
fi
[ "$(git -C "$fluent" rev-parse HEAD)" = "$FLUENT_COMMIT" ] || { echo "fluentui-emoji checkout is not $FLUENT_COMMIT" >&2; exit 1; }
fluent_tree="$(cd "$fluent" && LC_ALL=C find LICENSE assets -type f \( -name metadata.json -o -path '*/Flat/*.svg' \) | LC_ALL=C sort | xargs -d '\n' sha256sum | sha256sum | cut -d' ' -f1)"
if [ "$fluent_tree" != "$FLUENT_TREE_SHA" ]; then
  echo "fluentui-emoji tree sha256 mismatch: got $fluent_tree want $FLUENT_TREE_SHA" >&2
  echo "(the sparse checkout does not hold the pinned files; delete $fluent and rerun)" >&2
  exit 1
fi
echo "ok    fluentui-emoji tree sha256 matches"

# ---------------------------------------------------------------------------------------------
# Unpack the word lists. SCOWL's own mk-list (Perl) builds the en_US list at each size level, the
# same way speller/make-hunspell-dict builds the official hunspell en_US dictionary; the sizes
# are cumulative, so a word's size is the smallest list it appears in.
rm -rf "$work/scowl" "$work/12dicts"
mkdir -p "$work/scowl" "$work/12dicts"
tar -xzf "$work/scowl-2020.12.07.tar.gz" -C "$work/scowl"
unzip -q "$work/12dicts-6.0.2.zip" -d "$work/12dicts"
scowl="$work/scowl/scowl-2020.12.07"
for size in 10 20 35 40 50 55 60; do
  perl "$scowl/mk-list" -d "$scowl/final" -v1 --accents=both en_US "$size" > "$work/scowl/en_US.$size"
done

python3 - "$work/scowl" "$work/12dicts/Lemmatized" "$assets/en_US.tsv" <<'PYEOF'
import pathlib
import sys

scowl, lem_dir, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
SIZES = [10, 20, 35, 40, 50, 55, 60]

def ok(w):
    # letters (any script, so café and Atatürk survive), apostrophe and hyphen after the first letter
    return bool(w) and w[0].isalpha() and all(c.isalpha() or c in "'-" for c in w)

# --- membership and SCOWL size (SCOWL is ISO-8859-1) ---
size_of = {}
for size in SIZES:
    for line in (scowl / f"en_US.{size}").read_text(encoding="latin-1").splitlines():
        w = line.strip()
        if ok(w) and w not in size_of:
            size_of[w] = size

# --- 12dicts 2+2+3 lemmatised lists (ASCII, CRLF) ---
# 2+2+3lem: "headword" line, then an indented "a, b, c" line of inflections / variants;
#           "x -> [y]" is a cross-reference to headword y (x itself is a word); "!" marks a neologism.
# 2+2+3frq: the same lemmas in 21 frequency bands ("----- N -----", band 1 = the/be/to); regular
#           inflections are omitted there, so they come from 2+2+3lem; "*" marks a word that also
#           lives under another headword; "(word)" wraps entries not found in 2+2+3lem.
def tokens(text):
    for raw in text.split(","):
        t = raw.strip().split("->")[0].strip().strip("()").rstrip("*!")
        for part in t.split("/"):
            if ok(part):
                yield part

related = {}
head = None
for line in (lem_dir / "2+2+3lem.txt").read_text(encoding="ascii").splitlines():
    if not line.strip():
        continue
    if line[0].isspace():
        related.setdefault(head, []).extend(tokens(line))
    else:
        head = next(tokens(line), None)

band_of = {}
def note(w, band):
    if band < band_of.get(w, 99):
        band_of[w] = band

band = None
head = None
for line in (lem_dir / "2+2+3frq.txt").read_text(encoding="ascii").splitlines():
    if not line.strip():
        continue
    if line.startswith("-----"):
        band = int(line.strip("- \r"))
    elif line[0].isspace():
        for w in tokens(line):
            note(w, band)
    else:
        head = next(tokens(line), None)
        if head is None:
            continue
        note(head, band)
        for w in related.get(head, ()):
            note(w, band)

# --- synthetic counts ---
# 2+2+3frq band b holds lemmas that occur 2^(21-b)*16..2^(21-b)*32 times in the BYU/COCA data
# (band 21 = 16..31, band 20 = 32..63, ...), so successive bands differ by a factor of two and
# count = 2^(30-b) preserves the ratios: band 1 -> 536870912, band 21 -> 512.
# A SCOWL word absent from 2+2+3frq gets the band whose cumulative lemma count best matches the
# cumulative size of its SCOWL level (level 10 ~ 4.4k words ~ bands 1-11; level 20 ~ 12.6k ~
# bands 1-15; level 35 ~ 50k ~ beyond band 21), then one band per further level:
SIZE_TO_BAND = {10: 11, 20: 15, 35: 21, 40: 22, 50: 23, 55: 24, 60: 25}
def count_for_band(b):
    return 1 << (30 - b)

# Matching is exact (case included): 2+2+3frq carries its own capitalised entries (Monday, I), and
# a case-folded fallback would hand "Am" / "Be" / "Grey" the counts of am / be / grey.
entries = []
from_frq = 0
for w, size in size_of.items():
    b = band_of.get(w)
    if b is None:
        b = SIZE_TO_BAND[size]
    else:
        from_frq += 1
    entries.append((w, count_for_band(b)))

entries.sort(key=lambda e: (-e[1], e[0]))
with open(out, "w", encoding="utf-8", newline="\n") as f:
    for w, c in entries:
        f.write(f"{w}\t{c}\n")

per_size = {s: sum(1 for w in size_of if size_of[w] == s) for s in SIZES}
print(f"en_US.tsv: {len(entries)} entries ({from_frq} ranked by 2+2+3frq, {len(entries) - from_frq} by SCOWL level); per SCOWL level {per_size}")
assert 60000 <= len(entries) <= 150000, "entry count outside the 60k-150k window the phase asks for"
for must in ("don't", "it's", "I'm", "I", "can't", "won't", "you're", "Monday", "café"):
    assert must in size_of, f"expected word missing: {must}"
PYEOF

# ---------------------------------------------------------------------------------------------
# Emoji. emoji-test.txt gives the order, the group/subgroup and the CLDR short name of every
# fully-qualified sequence; a Fluent asset is matched by its metadata.json "unicode" field with
# the FE0F presentation selectors ignored on both sides (Fluent omits some of them).
rm -rf "$work/emoji-svg" "$work/emoji-png"
mkdir -p "$work/emoji-svg" "$work/emoji-png"
python3 - "$work/emoji-test-17.0.txt" "$fluent/assets" "$work/emoji-svg" "$work/emoji-png" "$work/inkscape-batch.txt" "$work/index.tsv" <<'PYEOF'
import json
import pathlib
import re
import shutil
import sys

emoji_test, fluent, svg_dir, png_dir, batch, index = sys.argv[1:7]
fluent = pathlib.Path(fluent)

by_key = {}
for d in sorted(fluent.iterdir()):
    meta = d / "metadata.json"
    if not meta.is_file():
        continue
    m = json.loads(meta.read_text(encoding="utf-8"))
    svgs = sorted(d.glob("Flat/*.svg")) + sorted(d.glob("Default/Flat/*.svg"))
    if not svgs:
        continue
    key = tuple(int(t, 16) for t in m["unicode"].split() if int(t, 16) != 0xFE0F)
    assert key not in by_key, f"two Fluent assets claim {m['unicode']}: {d.name}, {by_key[key][0].name}"
    by_key[key] = (d, svgs[0])

lines = []
group = subgroup = None
line_re = re.compile(r"^([0-9A-F ]+?)\s*;\s*(\S+)\s*#\s*(\S+)\s+E\d+\.\d+\s+(.*)$")
for raw in open(emoji_test, encoding="utf-8"):
    raw = raw.rstrip("\n")
    if raw.startswith("# group:"):
        group = raw.split(":", 1)[1].strip()
    elif raw.startswith("# subgroup:"):
        subgroup = raw.split(":", 1)[1].strip()
    elif raw and not raw.startswith("#"):
        m = line_re.match(raw)
        assert m, raw
        cps, status, glyph, name = m.groups()
        if status != "fully-qualified":
            continue
        key = tuple(int(t, 16) for t in cps.split() if int(t, 16) != 0xFE0F)
        hit = by_key.get(key)
        if hit is None:
            continue
        png = "-".join(t.lower() for t in cps.split()) + ".png"
        lines.append((cps, glyph, group, subgroup, name, png, hit[1]))

with open(batch, "w", encoding="utf-8", newline="\n") as b, open(index, "w", encoding="utf-8", newline="\n") as ix:
    for cps, glyph, group, subgroup, name, png, svg in lines:
        stem = png[:-4]
        shutil.copyfile(svg, f"{svg_dir}/{stem}.svg")
        b.write(f"file-open:{svg_dir}/{stem}.svg\n"
                f"export-filename:{png_dir}/{png}\n"
                "export-area-page\nexport-width:128\nexport-height:128\nexport-background-opacity:0\n"
                "export-do\nfile-close\n")
        ix.write(f"{cps}\t{glyph}\t{group}\t{subgroup}\t{name}\t{png}\n")
    b.write("quit\n")
print(f"emoji: {len(lines)} fully-qualified sequences have Fluent Flat artwork ({len(by_key)} Fluent assets)")
assert len(lines) == len(by_key), "some Fluent asset matched no fully-qualified emoji-test entry"
PYEOF

echo "render emoji with $(inkscape --version 2>/dev/null | head -1) (one --shell process)"
inkscape --shell < "$work/inkscape-batch.txt" > "$work/inkscape.log" 2>&1 || { tail -20 "$work/inkscape.log" >&2; exit 1; }

# Keep only the chunks that carry pixels. Inkscape also writes pHYs and a tEXt "Software" chunk;
# other builds/versions may add tIME. None of that belongs in the APK or in the checksum.
python3 - "$work/emoji-png" "$work/index.tsv" "$assets/emoji" <<'PYEOF'
import collections
import pathlib
import shutil
import struct
import sys
import zlib

src, index, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3])
KEEP = {b"IHDR", b"PLTE", b"tRNS", b"IDAT", b"IEND"}

def strip(png):
    data = png.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", png
    pos, kept = 8, [data[:8]]
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos:pos + 12 + length]
        if ctype == b"IHDR":
            w, h = struct.unpack(">II", chunk[8:16])
            assert (w, h) == (128, 128), (png, w, h)
        if ctype in KEEP:
            kept.append(chunk)
        pos += 12 + length
    return b"".join(kept)

groups = collections.Counter()
total = 0
rows = [line.rstrip("\n").split("\t") for line in index.open(encoding="utf-8")]
for cps, glyph, group, subgroup, name, png in rows:
    rendered = src / png
    assert rendered.is_file(), f"Inkscape produced no {png} (see .keyboardsrc/inkscape.log)"
    data = strip(rendered)
    (out / png).write_bytes(data)
    total += len(data)
    groups[group] += 1
shutil.copyfile(index, out / "index.tsv")
total += (out / "index.tsv").stat().st_size
print(f"emoji: {len(rows)} PNGs written, {total} bytes including index.tsv")
for g, n in groups.items():
    print(f"  {n:4d}  {g}")
PYEOF

# ---------------------------------------------------------------------------------------------
# Licence texts, read by the About page. Latin-1 sources are transcoded so the page shows them
# as written (agid.txt spells "café" in ISO-8859-1).
cp -f "$scowl/Copyright" "$licenses/scowl-2020.12.07-Copyright.txt"
python3 - "$work/12dicts" "$licenses" <<'PYEOF'
import pathlib
import re
import sys

src, licenses = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
(licenses / "12dicts-6.0.2-agid.txt").write_text(
    (src / "agid.txt").read_text(encoding="latin-1"), encoding="utf-8", newline="\n")
# The 12dicts terms are one paragraph of ReadMe.html; it is lifted verbatim rather than retyped.
text = re.sub(r"<[^>]+>", "", (src / "ReadMe.html").read_text(encoding="latin-1"))
m = re.search(r"The 12dicts lists were compiled by Alan Beale\..*?- Alan Beale -", text, re.S)
assert m, "12dicts ReadMe.html no longer carries the expected public-domain statement; re-verify it"
para = "\n".join(line.strip() for line in m.group(0).splitlines() if line.strip())
(licenses / "12dicts-6.0.2-LICENSE.txt").write_text(
    "12dicts 6.0.2 (Alan Beale), from ReadMe.html of 12dicts-6.0.2.zip:\n\n" + para +
    "\n\nAGID's terms (agid.txt) are in 12dicts-6.0.2-agid.txt.\n", encoding="utf-8", newline="\n")
PYEOF
cp -f "$fluent/LICENSE"          "$licenses/fluentui-emoji-MIT.txt"
cp -f "$work/unicode-license.txt" "$licenses/unicode-emoji-LICENSE.txt"

echo
echo "payload:"
du -sh "$assets/en_US.tsv" "$assets/emoji"
echo
echo "checksums:"
echo "  en_US.tsv  $(sha256sum "$assets/en_US.tsv" | cut -d' ' -f1)"
echo "  index.tsv  $(sha256sum "$assets/emoji/index.tsv" | cut -d' ' -f1)"
echo "  emoji PNGs $(cd "$assets/emoji" && LC_ALL=C ls *.png | LC_ALL=C sort | xargs cat | sha256sum | cut -d' ' -f1)  (all PNGs concatenated in byte order)"
