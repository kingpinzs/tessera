#!/usr/bin/env bash
# Phase 18 E12: diagnostics coverage (C-20; r3 V14's form, phase 17 E18's). Host-side reading: the union of
# qa/phase-18/<row>/ring-*.txt over this build's rows — a row folder counts when its own log is stamped
# `apk installed <the gate candidate's id>` (the rows' APK id matching); earlier runs kept as <row>-run<k> are not read.
#
#   * every `|` alternative of the doc's list is one line of E12/producers.tsv (the row or edge sub-step that produces
#     it) or of E12/notrun.tsv (no AVD row can produce it, with the reason); the doc's list is the one the floor's
#     builder transcribed (the committed producers.tsv and notrun.tsv, read from git HEAD), and every line of it must
#     still be named — a line missing from both files FAILS
#   * an alternative producers.tsv names a producer for and no slice holds FAILS; the detail names the slice that holds
#     it and whether that row's run passed
#   * `[files] share refused: outside shared storage` is the one line outside the ring union: it is E7's JVM test's, and
#     is read from E7's log (the gate's named case passed on this build)
#   * a line whose producer cell reads `NO PRODUCER NAMED IN THE DOC` (the floor's note of 2026-10-06: the doc lists the
#     alternative and names no leg that makes it) is held → PASS, not held → RECORDED for the lead to rule — it is
#     neither invented nor dropped
#   * the lines beyond the doc's list (Decisions 2026-10-06 (6), BUILD-NOTES) are RECORDED: held or not
# producers.tsv's third column is rewritten with the slice that holds each line.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"

# The row folder E12 holds producers.tsv and notrun.tsv themselves, so an earlier run's LOG and join are kept beside
# them (E12-run<k>.txt) rather than the folder renamed.
if [ -f "$P18/E12/E12.txt" ]; then k=1; while [ -e "$P18/E12/E12-run$k.txt" ]; do k=$((k + 1)); done; mv "$P18/E12/E12.txt" "$P18/E12/E12-run$k.txt"; [ -f "$P18/E12/join.tsv" ] && mv "$P18/E12/join.tsv" "$P18/E12/join-run$k.tsv"; fi
row_begin E12 "diagnostics: every line E12 lists, in the union of this build's rows' ring slices"
assert_gate_apk
git -C "$REPO" show "HEAD:docs/plan/qa/phase-18/E12/producers.tsv" > "$ROW_DIR/.doc-producers.tsv" 2>/dev/null
git -C "$REPO" show "HEAD:docs/plan/qa/phase-18/E12/notrun.tsv" > "$ROW_DIR/.doc-notrun.tsv" 2>/dev/null

python3 - "$P18" "${GATE_APK_MD5:0:16}" "$ROW_DIR" > "$ROW_DIR/join.out" <<'PY'
import glob, os, re, sys
QA, GATE, OUT = sys.argv[1], sys.argv[2], sys.argv[3]

def rows(path):
    out = []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"): continue
        c = line.split("\t")
        out.append(c + [""] * (3 - len(c)))
    return out

DIGITS = {"<n>", "<bytes>", "<ms>", "<bps>", "<eps>", "<total>"}
def to_regex(pattern):
    parts = [p for p in re.split(r"(<[^<>]*>|…)", pattern) if p]
    out = []
    for p in parts:
        if p in DIGITS: out.append(r"[0-9]+")
        elif p.startswith("<") and "|" in p: out.append("(?:" + "|".join(re.escape(c) for c in p[1:-1].split("|")) + ")")
        elif (p.startswith("<") and p.endswith(">")) or p == "…": out.append(r".+?")
        else: out.append(re.escape(p))
    return "".join(out) + r"\s*$"

# this build's rows: <row>/<row>.txt stamped with the gate candidate's id
runs = {}   # row -> (failed count or None, [ring files])
for d in sorted(glob.glob(os.path.join(QA, "*"))):
    row = os.path.basename(d)
    if not re.fullmatch(r"E[0-9]+[a-z]*|EDGE_[A-Z_]+|REVIEW_EXP|L18_[0-9]+", row) or row == "E12": continue
    log = os.path.join(d, row + ".txt")
    if not os.path.isfile(log): continue
    text = open(log, encoding="utf-8", errors="replace").read()
    if ("apk installed " + GATE) not in text: continue
    m = re.findall(r"^%s: (\d+) passed, (\d+) failed" % re.escape(row), text, re.M)
    runs[row] = (int(m[-1][1]) if m else None, sorted(glob.glob(os.path.join(d, "ring-*.txt"))))
slices = {}
for row, (failed, files) in runs.items():
    for f in files:
        try: slices[f] = (row, failed, open(f, encoding="utf-8", errors="replace").read())
        except OSError: pass
print("RECORD|this build's rows read (row: failed count, ring files)|" + " ".join("%s:%s,%d" % (r, "?" if v[0] is None else v[0], len(v[1])) for r, v in runs.items()))
others = sorted(os.path.basename(d) for d in glob.glob(os.path.join(QA, "*")) if re.fullmatch(r"(E[0-9]+[a-z]*|EDGE_[A-Z_]+|REVIEW_EXP|L18_[0-9]+)(-run[0-9]+)?", os.path.basename(d)) and os.path.basename(d) not in runs)
print("RECORD|row folders NOT read (an earlier run kept, another build's stamp, or no log)|" + (" ".join(others) or "none"))

def holder(rx):
    cre = re.compile(rx, re.M)
    hits = [(f, row, failed) for f, (row, failed, t) in slices.items() if cre.search(t)]
    hits.sort(key=lambda h: (h[2] != 0, h[0]))     # a passing run's slice first
    return hits

prod, notrun = rows(os.path.join(QA, "E12", "producers.tsv")), rows(os.path.join(QA, "E12", "notrun.tsv"))
raw = open(os.path.join(QA, "E12", "producers.tsv"), encoding="utf-8").read().split("\n")
beyond_at = next((i for i, l in enumerate(raw) if l.startswith("# beyond the doc's list")), len(raw))
beyond = {l.split("\t")[0] for l in raw[beyond_at:] if l and not l.startswith("#")}
e7 = ""
p7 = os.path.join(QA, "E7", "E7.txt")
if "E7" in runs and os.path.isfile(p7): e7 = open(p7, encoding="utf-8", errors="replace").read()
joined, failing_only = {}, []
for pat, by, _ in prod:
    if pat == "[files] share refused: outside shared storage":
        ok = bool(re.search(r"^PASS\s+JVM gate \*FileShareGuard\*: case the app's own filesDir file is refused", e7, re.M)) and bool(re.search(r"^PASS\s+JVM gate \*FileShareGuard\*: 0 failures / errors / skipped", e7, re.M))
        print("%s|%s|%s" % ("PASS" if ok else "FAIL", pat, "E7's JVM gate on this build: FileShareGuard's refusing cases passed (E7/E7.txt) — the one line outside the ring union" if ok else "E7's log on this build holds no passed FileShareGuard gate"))
        joined[pat] = "E7/E7.txt (the JVM gate; outside the ring union)" if ok else "NOT HELD"
        continue
    hits = holder(to_regex(pat))
    if hits:
        f, row, failed = hits[0]
        rel = os.path.relpath(f, QA)
        note = "" if failed == 0 else " — that row's run has %s failed" % ("?" if failed is None else failed)
        if failed != 0: failing_only.append(pat)
        kind = "RECORD" if pat in beyond else "PASS"
        print("%s|%s|held by %s%s (%d slice(s) in all; producer named: %s)" % (kind, pat, rel, note, len(hits), by[:70]))
        joined[pat] = rel + note
    elif pat in beyond:
        print("RECORD|%s|beyond the doc's list; held by no slice of this build's rows (producer named: %s)" % (pat, by[:90]))
        joined[pat] = "NOT HELD (beyond the doc's list)"
    elif by.startswith("NO PRODUCER NAMED IN THE DOC"):
        print("RECORD|%s|held by no slice — and the doc names no leg that makes it: for the lead to rule (not invented, not dropped)" % pat)
        joined[pat] = "NOT HELD — no producer named in the doc; for the lead to rule"
    else:
        print("FAIL|%s|named by producers.tsv (%s) and held by no slice of this build's rows; regex %s" % (pat, by[:90], to_regex(pat)))
        joined[pat] = "NOT HELD"
if failing_only:
    print("RECORD|lines held only by a row whose run has failures on this build|" + " ; ".join(failing_only))
# every line of the doc's list (the floor's transcription at git HEAD) is still named
doc = [r[0] for r in rows(os.path.join(OUT, ".doc-producers.tsv"))] + [r[0] for r in rows(os.path.join(OUT, ".doc-notrun.tsv"))]
named = {r[0] for r in prod} | {r[0] for r in notrun}
missing = [d for d in doc if d not in named]
print("%s|every alternative of the doc's list (%d, the floor's transcription at git HEAD) is a line of producers.tsv or notrun.tsv|%s" % ("PASS" if doc and not missing else "FAIL", len(doc), "all named" if doc and not missing else "missing: " + " ; ".join(missing or ["the HEAD files could not be read"])))
for pat, why, _ in notrun:
    hits = holder(to_regex(pat))
    print("RECORD|notrun: %s|%s" % (pat, ("HELD after all by " + os.path.relpath(hits[0][0], QA)) if hits else "held by no slice, as stated — " + why[:110]))
# producers.tsv with its third column rewritten
out = []
for line in raw:
    c = line.split("\t")
    if line and not line.startswith("#") and c[0] in joined:
        c = (c + ["", ""])[:3]; c[2] = joined[c[0]]; line = "\t".join(c)
    out.append(line)
open(os.path.join(QA, "E12", "producers.tsv"), "w", encoding="utf-8").write("\n".join(out))
open(os.path.join(OUT, "join.tsv"), "w", encoding="utf-8").write("\n".join("%s\t%s" % kv for kv in joined.items()) + "\n")
PY
echo $? > "$ROW_DIR/join.rc"
assert_eq "the join ran (exit code)" "0" "$(cat "$ROW_DIR/join.rc")"
while IFS='|' read -r kind name detail; do
  case "$kind" in
    PASS|FAIL) _verdict "$kind" "$name" "$detail" ;;
    RECORD) record "$name" "$detail" ;;
  esac
done < "$ROW_DIR/join.out"
row_end
