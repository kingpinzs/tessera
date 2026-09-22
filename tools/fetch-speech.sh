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
#   app/src/main/assets/speech/asr/*                     streaming zipformer en 20M int8 (Apache-2.0)
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
ASR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile.tar.bz2"
ASR_SHA="753a5362538539212442efbfb8dd5748db82e35e7deaec5330f49c56e81e40fd"
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
fetch "$ASR_URL" "$ASR_SHA" "$work/asr.tar.bz2"
fetch "$TTS_URL" "$TTS_SHA" "$work/kokoro.tar.bz2"

cp -f "$work/sherpa-onnx-1.13.8.aar" "$libs/sherpa-onnx-1.13.8.aar"

rm -rf "$work/models"; mkdir -p "$work/models"
tar -xjf "$work/asr.tar.bz2" -C "$work/models"
tar -xjf "$work/kokoro.tar.bz2" -C "$work/models"

asrsrc="$work/models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile"
# The int8 encoder and joiner plus the fp32 decoder: the release ships no int8 decoder, and at 2 MB it is
# not worth one (phase 03 Decisions "Model variants and budget": int8 ASR).
cp -f "$asrsrc/encoder-epoch-99-avg-1.int8.onnx" "$assets/asr/encoder.int8.onnx"
cp -f "$asrsrc/decoder-epoch-99-avg-1.onnx"      "$assets/asr/decoder.onnx"
cp -f "$asrsrc/joiner-epoch-99-avg-1.int8.onnx"  "$assets/asr/joiner.int8.onnx"
cp -f "$asrsrc/tokens.txt"                       "$assets/asr/tokens.txt"

ttssrc="$work/models/kokoro-int8-en-v0_19"
cp -f "$ttssrc/model.int8.onnx" "$assets/tts/model.int8.onnx"
cp -f "$ttssrc/voices.bin"      "$assets/tts/voices.bin"
cp -f "$ttssrc/tokens.txt"      "$assets/tts/tokens.txt"
cp -f "$ttssrc/LICENSE"         "$root/app/src/main/assets/licenses/kokoro-Apache-2.0.txt"

# One deterministic zip (no timestamps, sorted) so its checksum is the same on every machine.
rm -f "$assets/tts/espeak-ng-data.zip"
( cd "$ttssrc" && find espeak-ng-data -type f | sort | zip -q -X -D "$assets/tts/espeak-ng-data.zip" -@ )

echo
echo "payload:"
du -sh "$libs/sherpa-onnx-1.13.8.aar" "$assets/asr" "$assets/tts"
echo
echo "espeak-ng-data.zip sha256 (paste into SpeechModels.ESPEAK_ZIP_SHA256):"
sha256sum "$assets/tts/espeak-ng-data.zip" | cut -d' ' -f1
