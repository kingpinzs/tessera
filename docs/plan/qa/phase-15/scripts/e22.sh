#!/usr/bin/env bash
# E22 — diagnostics coverage (T15-48): EVERY alternative of every line phase 15's Decisions name appears at least once
# in the union of this build's saved ring slices — qa/phase-15/*/ring-launcher.txt, ring-speech.txt, ring-recorder.txt
# and ring-launcher-prekill.txt, the edge-case drivers' slices included, and the slices a row saves mid-run under the
# drivers' ring_<what>_<ring>.txt convention (ring_kill_speech.txt, ring_tess_launcher.txt) — counting only rows whose log names the APK
# this row runs against (the "apk built" line). The pattern table with each line's producer is e22-producers.tsv beside
# this driver: it is the row's input, so it lives with the scripts, not in the evidence dir the runner rotates.
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
      launcher) ls "$d"/ring-launcher*.txt "$d"/ring_*_launcher*.txt 2>/dev/null ;;
      speech) ls "$d"/ring-speech*.txt "$d"/ring_*_speech*.txt "$d"/ring-app.tileshell_.cortana.speech.SpeechService*.txt 2>/dev/null ;;
      recorder) ls "$d"/ring-recorder*.txt "$d"/ring_*_recorder*.txt "$d"/ring-app.tileshell_.recorder.RecorderService*.txt 2>/dev/null ;;
    esac
  done
}

TABLE="$(dirname "$0")/e22-producers.tsv"
checked=0
while IFS=$'\t' read -r ring pattern producer; do
  case "$ring" in ""|\#*) continue ;; esac
  checked=$((checked + 1))
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
done < "$TABLE"
# A missing or empty table must fail the row, not pass it with nothing checked.
assert_ne "the pattern table was read ($TABLE)" 0 "$checked"
cp "$TABLE" "$ROW_DIR/producers.tsv"
row_end
