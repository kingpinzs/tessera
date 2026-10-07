#!/usr/bin/env bash
# Phase 18 E20: the edge cases, executed (r3 V17). scripts/edge_index.tsv has one line per bullet of the doc's "Edge
# cases" (the bullet's first words, the sub-step / row / P row / JVM test that covers it, its last run), and
# `edge_files.sh <ID>` runs each AVD sub-step on its own fixtures (row folders EDGE_<ID>). This row is the JOIN: it runs
# no device command. It FAILS when a bullet of the doc has no line, or a line names a sub-step with no pass on a gate
# build — a pass is EDGE_<ID>/EDGE_<ID>.txt (the sub-step's LATEST run) stamped `apk installed <id>` with the current
# gate candidate's id or a prior gate build's whose rows stand (rowsb.sh GATE_PRIOR_FILES), ending `0 failed`; each
# line says WHICH build its pass is on. The
# rows, P rows and JVM tests a line names beside its sub-step are RECORDED with their state. The index's last-run
# column is rewritten from the logs.
#
#   edge_files.sh <ID>…    run the sub-steps first (each is its own row); then: e20.sh
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"

keep_earlier_run E20
jvm_row_begin E20 "the edge cases, executed: every bullet has a line, every sub-step a line names has a pass on this build"
python3 - "$P18" "$REPO/docs/plan/phase-18-files.md" "$(gate_ids)" "$HERE/edge_index.tsv" "$HERE/edge_files.sh" > "$ROW_DIR/join.out" <<'PY'
import os, re, sys
QA, DOC, GATES, INDEX, DRIVER = sys.argv[1:6]
GATES = GATES.split()
print("RECORD|the gate builds a pass may be on (the current one first; the others are prior gate builds whose rows stand)|" + " ".join(GATES))
doc = open(DOC, encoding="utf-8").read()
sec = doc[doc.index("\n## Edge cases"):]
sec = sec[:sec.index("\n## ", 5)]
bullets = [re.sub(r"\s+", " ", b.strip()) for b in re.findall(r"^- (.*(?:\n  .*)*)", sec, re.M)]
lines = open(INDEX, encoding="utf-8").read().split("\n")
rows = [l.split("\t") for l in lines if l.strip() and not l.startswith("#")]
ids = re.findall(r'^ALL_IDS="([^"]*)"', open(DRIVER, encoding="utf-8").read(), re.M)[0].split()
print("RECORD|the doc's Edge cases bullets / the index's lines / edge_files.sh's sub-steps|%d / %d / %d" % (len(bullets), len(rows), len(ids)))

def state(row):
    log = os.path.join(QA, row, row + ".txt")
    if not os.path.isfile(log): return ("never run", "never")
    t = open(log, encoding="utf-8", errors="replace").read()
    at = (re.search(r"^at\s+(\S+)", t, re.M) or [None, "?"])[1]
    build = (re.search(r"^apk installed ([0-9a-f]{16})", t, re.M) or re.search(r"^apk built\s+md5 ([0-9a-f]{16})", t, re.M) or [None, "?"])[1]   # a JVM row stamps the APK file's md5
    m = re.findall(r"^%s: (\d+) passed, (\d+) failed, (\d+) recorded" % re.escape(row), t, re.M)
    if not m: return ("no summary line (the run did not end)", "%s — did not end (build %s)" % (at, build))
    p, f, r = m[-1]
    on = build in GATES
    which = "the current build" if build == GATES[0] else ("a prior gate build, standing" if on else "NOT a gate build")
    ok = on and f == "0" and int(p) > 0
    return ("pass" if ok else ("%s failed" % f if on else "another build (%s)" % build), "%s %s: %s passed, %s failed, %s recorded (build %s — %s)" % (at, "PASS" if ok else "FAIL", p, f, r, build, which))

for b in bullets:
    hit = [r for r in rows if b.replace("`", "").startswith(r[0].replace("`", ""))]
    print("%s|the bullet has a line: %s|%s" % ("PASS" if len(hit) == 1 else "FAIL", b[:70], ("-> " + hit[0][1][:90]) if len(hit) == 1 else "%d lines of edge_index.tsv start with its first words" % len(hit)))
named = set()
last = {}
for r in rows:
    r += [""] * (3 - len(r))
    subs = re.findall(r"\bedge ([A-Z_]+)\b", r[1])
    others = sorted(set(re.findall(r"\b(E[0-9]+b?)\b", r[1])))
    runs = []
    for s in subs:
        named.add(s)
        st, text = state("EDGE_" + s)
        print("%s|%s — sub-step %s has a pass on a gate build|%s" % ("PASS" if st == "pass" else "FAIL", r[0][:48], s, text))
        runs.append("EDGE_%s %s" % (s, text))
    for o in others:
        st, text = state(o)
        print("RECORD|%s — also named: row %s|%s" % (r[0][:48], o, text))
        runs.append("%s %s" % (o, text))
    if not subs and not others:
        print("RECORD|%s — no AVD sub-step and no row is named|%s" % (r[0][:48], r[1][:150]))
    elif not subs:
        print("RECORD|%s — covered by a row or a JVM test, no AVD sub-step|%s" % (r[0][:48], r[1][:150]))
    last[r[0]] = "; ".join(runs) if runs else "no AVD run (see the cover column)"
for s in ids:
    print("%s|edge_files.sh's sub-step %s is named by a line of the index|%s" % (("PASS", s, "named") if s in named else ("FAIL", s, "no line names it")))
out = []
for l in lines:
    c = l.split("\t")
    if l.strip() and not l.startswith("#") and c[0] in last:
        c = (c + ["", ""])[:3]; c[2] = last[c[0]]; l = "\t".join(c)
    out.append(l)
open(INDEX, "w", encoding="utf-8").write("\n".join(out))
PY
echo $? > "$ROW_DIR/join.rc"
assert_eq "the join ran (exit code)" "0" "$(cat "$ROW_DIR/join.rc")"
while IFS='|' read -r kind name detail; do
  case "$kind" in
    PASS|FAIL) _verdict "$kind" "$name" "$detail" ;;
    RECORD) record "$name" "$detail" ;;
  esac
done < "$ROW_DIR/join.out"
jvm_row_end
