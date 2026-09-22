#!/usr/bin/env bash
# Phase 03: the speech runtime and its two models.
#
# They are not in git: 250 MB of third-party binaries would be in every clone and every checkout, and
# the sources are immutable GitHub release assets. This script pins each one by URL and sha256, so the
# build is reproducible without carrying the bytes. Run it once after a clone; it is a no-op afterwards.
#
#   tools/fetch-speech.sh
#
# What it produces (all git-ignored):
#   app/libs/sherpa-onnx-1.13.8.aar                      the runtime (Apache-2.0), 4 ABIs
#   app/src/main/assets/speech/asr/*                     streaming zipformer en 2023-06-26 int8,
#                                                        plus bpe.model and the bpe.vocab derived from it
#   app/src/main/assets/speech/tts/model.int8.onnx       Kokoro-82M int8 en v0.19 (Apache-2.0)
#   app/src/main/assets/speech/tts/voices.bin            its 11 voices
#   app/src/main/assets/speech/tts/espeak-ng-data.zip    GPL-3.0-or-later, PQ1 = A (personal use)
#
# espeak-ng-data ships as ONE zip rather than ~1500 loose asset files: espeak-ng opens its data by file
# path, so it is extracted to app-private storage at first use anyway (phase 03 Decisions "Model storage
# and process"), and one zip is what the extractor reads and what the checksum covers.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$root/.speechsrc"
libs="$root/app/libs"
assets="$root/app/src/main/assets/speech"

AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"
AAR_SHA="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
# The ASR model is fetched file by file, not as a tarball: the release tarballs of this model carry
# every variant, and only these five files ship in the APK.
ASR_BASE="https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main"
ASR_FILES="
encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx encoder.int8.onnx 563fde436d16cf7607cf408cd6b30909819d03162652ef389c2450ced3f45ac1
decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx decoder.int8.onnx 98da299f471e38bb4e1a8df579b8cc9122d6039576a77e357b3c60f17dd83b02
joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx  joiner.int8.onnx  d944208d660d67c8d72cd2acaeac971fa5ceb8c80e76c1968148846fedd6e297
tokens.txt                                          tokens.txt        49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb
bpe.model                                           bpe.model         c53433de083c4a6ad12d034550ef22de68cec62c4f58932a7b6b8b2f1e743fa5
"
TTS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2"
TTS_SHA="c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd"

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

mkdir -p "$work" "$libs" "$assets/asr" "$assets/tts"

fetch "$AAR_URL" "$AAR_SHA" "$work/sherpa-onnx-1.13.8.aar"
fetch "$TTS_URL" "$TTS_SHA" "$work/kokoro.tar.bz2"

cp -f "$work/sherpa-onnx-1.13.8.aar" "$libs/sherpa-onnx-1.13.8.aar"

rm -rf "$work/models"; mkdir -p "$work/models" "$work/asr"
tar -xjf "$work/kokoro.tar.bz2" -C "$work/models"

echo "$ASR_FILES" | while read -r remote local sha; do
  [ -n "$remote" ] || continue
  fetch "$ASR_BASE/$remote" "$sha" "$work/asr/$local"
  cp -f "$work/asr/$local" "$assets/asr/$local"
done

# sherpa-onnx parses the BPE vocabulary itself and wants a TEXT file ("<piece> <score>" per line), not
# the sentencepiece binary — given bpe.model it refuses with "Each line in vocab should contain two
# items". The vocabulary is derived here, once, so the APK carries the form the runtime actually reads.
if ! python3 -c "import sentencepiece" 2>/dev/null; then
  echo "tools/fetch-speech.sh needs sentencepiece to derive bpe.vocab: pip install sentencepiece" >&2
  exit 1
fi
python3 - "$assets/asr/bpe.model" "$assets/asr/bpe.vocab" <<'PYEOF'
import sys
import sentencepiece as spm
sp = spm.SentencePieceProcessor()
sp.Load(sys.argv[1])
with open(sys.argv[2], "w") as out:
    for i in range(sp.GetPieceSize()):
        out.write(f"{sp.IdToPiece(i)} {sp.GetScore(i)}\n")
print(f"bpe.vocab: {sp.GetPieceSize()} pieces")
PYEOF

ttssrc="$work/models/kokoro-int8-en-v0_19"
cp -f "$ttssrc/model.int8.onnx" "$assets/tts/model.int8.onnx"
cp -f "$ttssrc/voices.bin"      "$assets/tts/voices.bin"
cp -f "$ttssrc/tokens.txt"      "$assets/tts/tokens.txt"
cp -f "$ttssrc/LICENSE"         "$root/app/src/main/assets/licenses/kokoro-Apache-2.0.txt"

# ONE ZIP THAT IS ACTUALLY DETERMINISTIC.
#
# `zip -X` drops the extra fields but NOT the DOS date/time in each entry header, and DOS time is
# written in the BUILDER'S LOCAL timezone. The same 355 files zipped here and on the CI runner
# therefore came out with identical contents and different bytes, so ESPEAK_ZIP_SHA256 was a
# checksum of this machine's timezone: every CI APK carried an espeak zip the app then refused, and
# shipped with no voice at all. Measured 2026-09-22 from the phone's own diagnostics — the APK's zip
# hashed a477296a…, the pin said cd01895b…, the payloads were byte-identical, and one source tree
# built under UTC / America-Denver / Asia-Tokyo gave three different hashes.
#
# Python writes the archive instead of the zip binary so nothing machine-dependent can get in: entry
# order, timestamp, mode and compression level are all fixed here rather than taken from the host.
rm -f "$assets/tts/espeak-ng-data.zip"
python3 - "$ttssrc/espeak-ng-data" "$assets/tts/espeak-ng-data.zip" <<'PYEOF'
import pathlib
import sys
import zipfile

source, out = pathlib.Path(sys.argv[1]), sys.argv[2]
names = sorted(p.relative_to(source.parent).as_posix() for p in source.rglob("*") if p.is_file())
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name in names:
        entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        entry.external_attr = 0o100644 << 16
        entry.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(entry, (source.parent / name).read_bytes())
print(f"espeak-ng-data.zip: {len(names)} entries")
PYEOF

echo
echo "payload:"
du -sh "$libs/sherpa-onnx-1.13.8.aar" "$assets/asr" "$assets/tts"
echo
echo "checksums (paste into SpeechAssets):"
echo "  ESPEAK_ZIP_SHA256 = $(sha256sum "$assets/tts/espeak-ng-data.zip" | cut -d' ' -f1)"
echo "  ASR_BPE_SHA256    = $(sha256sum "$assets/asr/bpe.vocab" | cut -d' ' -f1)"

# These two pins are what the app checks before it will build an engine, so a payload that no longer
# matches them is a phone with no voice and no ears. Fail HERE, in one line of build output, instead
# of on a device. bpe.vocab is derived by whatever sentencepiece pip installed and its scores are
# written with Python's default float formatting, so it can drift the same way the zip did.
echo
echo "pins:"
models_kt="$root/app/src/main/kotlin/app/tileshell/cortana/speech/SpeechModels.kt"
verify_pin() {
  local name="$1" file="$2" actual pinned
  actual="$(sha256sum "$file" | cut -d' ' -f1)"
  pinned="$(grep -oE "$name = \"[0-9a-f]{64}\"" "$models_kt" | grep -oE '[0-9a-f]{64}' || true)"
  if [ "$actual" != "$pinned" ]; then
    echo "  FAIL $name" >&2
    echo "    $(basename "$file") is $actual" >&2
    echo "    SpeechModels.kt pins ${pinned:-<constant not found>}" >&2
    echo "    The payload changed. Once you know why, set $name = $actual" >&2
    return 1
  fi
  echo "  $name matches SpeechModels.kt"
}
verify_pin ESPEAK_ZIP_SHA256 "$assets/tts/espeak-ng-data.zip"
verify_pin ASR_BPE_SHA256    "$assets/asr/bpe.vocab"
