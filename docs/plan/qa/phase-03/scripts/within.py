#!/usr/bin/env python3
"""One measured comparison, as "VERDICT|name|detail" for lib.sh's assert_within."""
import sys

name, expected, actual, tol = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
try:
    expected, actual, tol = float(expected), float(actual), float(tol)
except ValueError:
    sys.exit(1)
ok = abs(actual - expected) <= tol
print(f"{'PASS' if ok else 'FAIL'}|{name}|{actual:.2f} vs {expected:.2f} +/- {tol}")
