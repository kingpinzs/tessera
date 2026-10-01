#!/usr/bin/env python3
"""Which checks passed in an OLD row log and do not pass in a NEW one (phase 14 E13, Jeremy's ruling Q-E10 (a)).

  regressed.py <old-row.txt> <new-row.txt>
Line 1: "old PASS <n>, new PASS <n>, new FAIL <n>". Then one line per check that was PASS in the old log and is not PASS
in the new one (nothing when there is none). Exit 0 when both logs were read and the old one holds at least one PASS;
2 when either cannot be read or the old log has no PASS line (a comparison against nothing proves nothing).
"""
import re
import sys


def names(path, kind):
    out = set()
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith(kind + "  "):
                out.add(re.split(r"\s{2,}", line[len(kind) + 2:].rstrip())[0])
    return out


try:
    old_pass = names(sys.argv[1], "PASS")
    new_pass = names(sys.argv[2], "PASS")
    new_fail = names(sys.argv[2], "FAIL")
except OSError as e:
    print(f"regressed.py: {e}", file=sys.stderr)
    sys.exit(2)
if not old_pass:
    print(f"regressed.py: no PASS line in {sys.argv[1]}", file=sys.stderr)
    sys.exit(2)
print(f"old PASS {len(old_pass)}, new PASS {len(new_pass)}, new FAIL {len(new_fail)}")
for name in sorted(old_pass - new_pass):
    print(name)
