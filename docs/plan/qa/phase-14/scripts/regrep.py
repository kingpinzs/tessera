"""Phase 14 harness re-grep: every `input swipe` in the qa tree, classified. Literal operands: a rightward swipe at page
height (y1 == y2 within 60 px, x2 > x1 by at least 300 px, y inside the page 200..2150) is a hit. Computed operands
(any non-integer): listed for classification by hand. Also every grep of Start's home line ("home: page")."""
import os, re, sys
root = sys.argv[1]
swipe = re.compile(r"input\s+swipe\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)")
hits, computed, home = [], [], []
for dp, dn, fn in os.walk(root):
    if "/utterances" in dp or "__pycache__" in dp: continue
    for f in fn:
        if not f.endswith((".sh", ".py")): continue
        p = os.path.join(dp, f)
        rel = os.path.relpath(p, root)
        for i, line in enumerate(open(p, encoding="utf-8", errors="replace"), 1):
            if "home: page" in line: home.append(f"{rel}:{i}: {line.strip()[:150]}")
            for m in swipe.finditer(line):
                ops = m.groups()
                if all(re.fullmatch(r"-?\d+", o) for o in ops):
                    x1, y1, x2, y2 = map(int, ops)
                    if abs(y1 - y2) <= 60 and x2 - x1 >= 300 and 200 <= y1 <= 2150:
                        hits.append(f"{rel}:{i}: {line.strip()[:150]}")
                else:
                    computed.append(f"{rel}:{i}: {line.strip()[:150]}")
print("== literal right swipes at page height"); print("\n".join(hits))
print("== computed operands (classify by hand)"); print("\n".join(computed))
print("== greps of Start's home line"); print("\n".join(home))
