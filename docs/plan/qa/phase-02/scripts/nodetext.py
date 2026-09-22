#!/usr/bin/env python3
"""nodetext.py <dump.xml> <resource-id>: the text of that node, or every text in the dump when the id is empty."""
import re, sys
s = open(sys.argv[1]).read()
rid = sys.argv[2] if len(sys.argv) > 2 else ""
if rid:
    m = re.search(r'resource-id="' + re.escape(rid) + r'"[^>]*text="([^"]*)"', s) or \
        re.search(r'text="([^"]*)"[^>]*resource-id="' + re.escape(rid) + r'"', s)
    print(m.group(1) if m else "")
else:
    for t in re.findall(r'text="([^"]+)"', s):
        print(t)
