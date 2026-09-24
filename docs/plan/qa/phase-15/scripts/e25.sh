#!/usr/bin/env bash
# E25 — APK budget (T15-21): the debug APK built WITHOUT this phase and the one built WITH it differ by <= 3 MB, and
# `unzip -l` lists no new entry >= 1 MB. At the gate both come from the rebased main; on the branch the "without"
# APK is built from the branch point (298a9d7, a `git archive` of it) and the delta is a development record.
#   e25.sh <apk-without-phase-15> [apk-with; default the build's own]
# MB here is 1,000,000 bytes (the stricter reading). The per-entry growth table is kept for any overage.
# The dex files are D8's positional multidex shards (classesN.dex): which N holds what moves between builds, so they are
# compared as ONE aggregate entry, "classes*.dex", and recorded; the new-entry rule (the Decisions' "no single new asset
# >= 1 MB", Stage C section) applies to every other entry.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E25 "the APK grows by at most 3 MB and gains no entry of 1 MB or more"

pre="${1:?usage: e25.sh <apk-without-phase-15> [apk-with]}"; post="${2:-$APK}"
s0="$(stat -c%s "$pre")"; s1="$(stat -c%s "$post")"
note "without: $pre $s0 bytes (sha256 $(sha256sum "$pre" | cut -c1-16))"
note "with:    $post $s1 bytes (sha256 $(sha256sum "$post" | cut -c1-16))"
record "APK size delta" "$((s1 - s0)) bytes ($s0 -> $s1)"
assert_eq "the APK grew by at most 3,000,000 bytes" yes "$([ $((s1 - s0)) -le 3000000 ] && echo yes || echo "no: +$((s1 - s0))")"

unzip -lv "$pre" > "$ROW_DIR/without.lv.txt"; unzip -lv "$post" > "$ROW_DIR/with.lv.txt"
python3 - "$ROW_DIR/without.lv.txt" "$ROW_DIR/with.lv.txt" "$ROW_DIR/entries.tsv" <<'PY' > "$ROW_DIR/new-large.txt"
import re, sys
def entries(path):
    out = {}
    for l in open(path):
        m = re.match(r"\s*(\d+)\s+\S+\s+(\d+)\s+\S+%?\s+\S+\s+\S+\s+[0-9a-f]{8}\s+(.+)$", l)
        if m:
            name = m.group(3).strip()
            if re.fullmatch(r"classes\d*\.dex", name): name = "classes*.dex"
            u, c = out.get(name, (0, 0))
            out[name] = (u + int(m.group(1)), c + int(m.group(2)))
    return out
a, b = entries(sys.argv[1]), entries(sys.argv[2])
rows = []
for name in sorted(set(a) | set(b)):
    (ua, ca), (ub, cb) = a.get(name, (0, 0)), b.get(name, (0, 0))
    if (ua, ca) != (ub, cb):
        rows.append((cb - ca, ub - ua, name, "new" if name not in a else ("gone" if name not in b else "changed")))
rows.sort(reverse=True)
with open(sys.argv[3], "w") as o:
    o.write("compressed_delta\tuncompressed_delta\tentry\tkind\n")
    for r in rows: o.write("%d\t%d\t%s\t%s\n" % r)
for cd, ud, name, kind in rows:
    if kind == "new" and b[name][0] >= 1000000: print("%s\t%d" % (name, b[name][0]))
PY
note "entries changed: $(($(wc -l < "$ROW_DIR/entries.tsv") - 1)); the ten largest compressed deltas:"
sed -n '2,11p' "$ROW_DIR/entries.tsv" | while IFS= read -r l; do note "  $l"; done
record "dex (classes*.dex, all shards)" "$(grep -P '\tclasses\*\.dex\t' "$ROW_DIR/entries.tsv" | cut -f1,2 | tr '\t' ' ') bytes (compressed uncompressed delta)"
assert_eq "no new entry of 1,000,000 bytes or more (unzip -l; dex shards as one)" "" "$(cat "$ROW_DIR/new-large.txt")"
row_end
