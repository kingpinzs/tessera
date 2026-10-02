#!/usr/bin/env bash
# E21 — diagnostics coverage (r3 V6, phase 15 E22's form): EVERY alternative of every line phase 16's Decisions name
# appears at least once in the union of this build's saved ring slices — qa/phase-16/<row>/ring-launcher.txt and the
# slices a row saves mid-run (ring*.txt, *slice*.txt: the ring is the shell process's memory, so a row that stops the
# shell saves its slice first) — counting ONLY rows whose log names the APK installed now ("apk installed").
# Inputs, the row writers' tables:
#   E21/producers.tsv   alternative <TAB> the row or EDGE sub-step that produces it <TAB> regex (grep -E)
#   E21/notrun.tsv      alternative <TAB> why no row can produce it on a device <TAB> the JVM test that covers it
# A pattern no slice holds fails the row; a not-run alternative whose JVM test file does not exist fails the row.
# The row cannot know an alternative that is in neither table: that completeness is the gate reviewers' to check
# against the doc's Decisions.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p16.sh"
row_begin E21 "every diagnostics alternative is in this build's saved ring slices"

build="$(adb shell md5sum "$(adb shell pm path app.tileshell | head -1 | tr -d '\r' | sed 's/^package://')" < /dev/null | cut -c1-16 | tr -d '\r')"
note "this build (installed): $build"
# Earlier builds whose rows still count, each with its reason (E21/builds.txt: "<md5 prefix> <row> <reason>") — the
# owner's ruling of 2026-10-01 ("Only test the fixes not EVERY THING"; the same as 2026-09-25): a row whose code the
# fix round did not touch is not run again, and its run on the earlier build is its evidence.
# A row of "*" counts every row's run on that build (a later build whose diff touches one row's code: that row is run
# again on the installed build, the others stand).
declare -A also=()
allrows=()
if [ -f "$P16/E21/builds.txt" ]; then
  while read -r sha row why; do
    case "$sha" in ""|\#*) continue ;; esac
    if [ "$row" = "*" ]; then allrows+=("$sha"); else also["$row"]="${also[$row]:-} $sha"; fi
    note "also counting $row on build $sha: $why"
  done < "$P16/E21/builds.txt"
fi
# A run's folder is <ROW> or <ROW>-<what the run was>; its log is <ROW>.txt inside it. A kept run that failed on a
# counted build still shows what the build logged, so its slices count.
rows=()
for log in "$P16"/*/*.txt; do
  d="$(dirname "$log")"; r="$(basename "$log" .txt)"; dn="$(basename "$d")"
  case "$dn" in "$r"|"$r"-*) ;; *) continue ;; esac
  [ "$r" = E21 ] && continue
  got="$(grep -m1 '^apk installed' "$log" | awk '{print $3}')"
  [ -n "$got" ] || continue
  ok=no
  case "$got" in "$build"*) ok=yes ;; esac
  for b in ${allrows[@]+"${allrows[@]}"} ${also[$r]:-}; do case "$got" in "$b"*) ok=yes ;; esac; done
  [ "$ok" = yes ] && rows+=("$d")
done
note "run folders counted: $(for d in "${rows[@]}"; do basename "$d"; done | tr '\n' ' ')"
assert_ne "at least one row ran on this build" 0 "${#rows[@]}"
files=()
for d in "${rows[@]}"; do
  while IFS= read -r f; do files+=("$f"); done < <(find "$d" -maxdepth 1 -type f \( -name 'ring*.txt' -o -name '*slice*.txt' -o -name '*ring*.txt' \) | sort)
done
record "ring slices read" "${#files[@]} files in ${#rows[@]} rows"
assert_ne "at least one slice" 0 "${#files[@]}"

TABLE="$P16/E21/producers.tsv"; NOTRUN="$P16/E21/notrun.tsv"
checked=0
while IFS=$'\t' read -r alt producer pattern; do
  case "$alt" in ""|\#*) continue ;; esac
  checked=$((checked + 1))
  if [ -z "$pattern" ]; then _verdict FAIL "$alt" "the table gives no pattern (producer: $producer)"; continue; fi
  hit="$(grep -lE -- "$pattern" "${files[@]}" 2>/dev/null | head -1)"
  if [ -n "$hit" ]; then
    _verdict PASS "$alt" "in ${hit#"$P16"/} (producer: $producer)"
  else
    _verdict FAIL "$alt" "in no saved slice of this build (producer: $producer; pattern: $pattern)"
  fi
done < "$TABLE"
record "alternatives with a producer" "$checked"

notrun=0
while IFS=$'\t' read -r alt why test; do
  case "$alt" in ""|\#*) continue ;; esac
  notrun=$((notrun + 1))
  path="$(printf '%s' "$test" | sed -n 's/.*(\(app\/src\/test[^)]*\)).*/\1/p')"
  if [ -n "$path" ] && [ -f "$REPO/$path" ]; then
    record "NOT RUN: $alt" "$why — JVM: $test"
  else
    _verdict FAIL "NOT RUN: $alt" "its JVM test is not named by path or the file is missing ($test)"
  fi
done < "$NOTRUN"
record "alternatives not run on a device" "$notrun"
# One alternative in both tables is a contradiction the tables' owner must settle.
both="$(comm -12 <(grep -v '^#' "$TABLE" | cut -f1 | sort -u) <(grep -v '^#' "$NOTRUN" | cut -f1 | sort -u) | grep -c . || true)"
assert_eq "no alternative is in both tables" "0" "$both"
row_end
