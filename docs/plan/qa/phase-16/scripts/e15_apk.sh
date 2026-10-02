#!/usr/bin/env bash
# E15's APK clause (the lead's; the People row writer's e15.sh records it as not asserted there). As re-cut by the
# owner's ruling Q-16-5 ("A", 2026-10-01): the APK's file size after task 8 is at most 5 MB over the size before task 2,
# and `unzip -l` shows no new entry of 1 MB or more other than code files (classes*.dex — a debug build shards its code
# into files whose numbers move from build to build).
# Recorded beside it, because the file size of a debug APK carries dead space an incremental build leaves behind: the
# entries' own bytes, before and after, and the packaging overhead of this file.
# Inputs: qa/phase-16/apk-size-before.txt and apk-entries-1mb-before.txt (taken at 4951debf, before build task 2).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p16.sh"
row_begin E15_APK "E15's APK clause: growth over the pre-task-2 APK, and no new large entry that is not code"

before_bytes="$(sed -n 's/^bytes //p' "$P16/apk-size-before.txt")"
before_total="$(awk 'NR == 1 { print $1 }' "$P16/apk-entries-1mb-before.txt")"
assert_ne "the before size is on file" "" "$before_bytes"
assert_ne "the before entries' total is on file" "" "$before_total"
after_bytes="$(stat -c%s "$APK")"
delta=$(( after_bytes - before_bytes ))
record "file size before task 2 / now / delta" "$before_bytes / $after_bytes / $delta bytes"
assert_eq "the APK measured is the one installed (the row's header says apk match)" "yes" "$(grep -m1 '^apk match' "$LOG" | awk '{print $3}')"
assert_eq "growth is at most 5 MB (5,000,000 bytes; Q-16-5)" "yes" "$([ "$delta" -le 5000000 ] && echo yes || echo "no ($delta)")"
assert_eq "the whole APK is within phase 03's 600 MB" "yes" "$([ "$after_bytes" -le 600000000 ] && echo yes || echo no)"

unzip -l "$APK" | awk 'NF == 4 && $1 ~ /^[0-9]+$/ && $1 >= 1000000 { print $1, $4 }' | sort -k2 > "$ROW_DIR/entries-1mb-now.txt"
awk 'NF == 2 && $1 ~ /^[0-9]+$/ { print $2 }' "$P16/apk-entries-1mb-before.txt" | sort > "$ROW_DIR/names-1mb-before.txt"
awk '{ print $2 }' "$ROW_DIR/entries-1mb-now.txt" | sort > "$ROW_DIR/names-1mb-now.txt"
comm -13 "$ROW_DIR/names-1mb-before.txt" "$ROW_DIR/names-1mb-now.txt" > "$ROW_DIR/new-1mb-names.txt"
record "entries of 1 MB or more now" "$(wc -l < "$ROW_DIR/entries-1mb-now.txt") (before: $(wc -l < "$ROW_DIR/names-1mb-before.txt"))"
record "new names among them" "$(tr '\n' ' ' < "$ROW_DIR/new-1mb-names.txt")"
assert_eq "no new entry of 1 MB or more other than classes*.dex" "" "$(grep -vE '^classes[0-9]*\.dex$' "$ROW_DIR/new-1mb-names.txt" | tr '\n' ' ')"
# The same check on the large entries that are NOT code, by size: an asset that grew past the earlier one would show here.
awk 'NF == 2 && $1 ~ /^[0-9]+$/ && $2 !~ /^classes[0-9]*\.dex$/ { print $1, $2 }' "$P16/apk-entries-1mb-before.txt" | sort -k2 > "$ROW_DIR/noncode-before.txt"
grep -vE ' classes[0-9]*\.dex$' "$ROW_DIR/entries-1mb-now.txt" > "$ROW_DIR/noncode-now.txt"
assert_eq "every large entry that is not code is byte-for-byte the size it was" "" "$(diff "$ROW_DIR/noncode-before.txt" "$ROW_DIR/noncode-now.txt")"

now_total="$(unzip -l "$APK" | tail -1 | awk '{ print $1 }')"
now_packed="$(unzip -lv "$APK" | tail -1 | awk '{ print $2 }')"
dex_now="$(unzip -l "$APK" | awk '$4 ~ /^classes[0-9]*\.dex$/ { s += $1 } END { print s + 0 }')"
record "the entries' own bytes before / now / growth" "$before_total / $now_total / $(( now_total - before_total ))"
record "code (classes*.dex) now" "$dex_now bytes in $(unzip -l "$APK" | awk '$4 ~ /^classes[0-9]*\.dex$/' | wc -l) files"
record "packaging overhead of this file (file size − the entries' stored bytes)" "$(( after_bytes - now_packed )) bytes"
row_end
