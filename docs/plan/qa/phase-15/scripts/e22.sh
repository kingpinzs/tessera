#!/usr/bin/env bash
# E22 — diagnostics coverage (T15-48): EVERY alternative of every line phase 15's Decisions name appears at least once
# in the union of this build's saved ring slices — qa/phase-15/*/ring-launcher.txt, ring-speech.txt, ring-recorder.txt
# and ring-launcher-prekill.txt, the edge-case drivers' slices included — counting only rows whose log names the APK
# this row runs against (the "apk built" line). The pattern table with each line's producer is E22/producers.tsv.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E22 "every diagnostics alternative is in this build's saved ring slices"

build="$(sha256sum "$APK" | cut -c1-16)"
note "this build: $build"
# The rows run against THIS APK: their log's "apk built" line carries its sha prefix.
rows=()
for log in "$P15"/*/*.txt; do
  d="$(dirname "$log")"; r="$(basename "$d")"
  [ "$log" = "$d/$r.txt" ] || continue
  [ "$r" = E22 ] && continue
  grep -q "^apk built     $build" "$log" && rows+=("$d")
done
note "rows on this build: $(for d in "${rows[@]}"; do basename "$d"; done | tr '\n' ' ')"
assert_ne "at least one row ran on this build" 0 "${#rows[@]}"

files_for() { # ring
  local d
  for d in "${rows[@]}"; do
    case "$1" in
      launcher) ls "$d"/ring-launcher*.txt 2>/dev/null ;;
      speech) ls "$d"/ring-speech*.txt "$d"/ring-app.tileshell_.cortana.speech.SpeechService*.txt 2>/dev/null ;;
      recorder) ls "$d"/ring-recorder*.txt "$d"/ring-app.tileshell_.recorder.RecorderService*.txt 2>/dev/null ;;
    esac
  done
}

while IFS=$'\t' read -r ring pattern producer; do
  case "$ring" in ""|\#*) continue ;; esac
  mapfile -t files < <(files_for "$ring")
  hit=""
  if [ "${#files[@]}" -gt 0 ]; then
    hit="$(grep -lE -- "$pattern" "${files[@]}" 2>/dev/null | head -1)"
  fi
  if [ -n "$hit" ]; then
    _verdict PASS "[$ring] $pattern" "in ${hit#"$P15"/} (producer: $producer)"
  else
    _verdict FAIL "[$ring] $pattern" "in no saved $ring slice of this build (producer: $producer)"
  fi
done < "$P15/E22/producers.tsv"
row_end
