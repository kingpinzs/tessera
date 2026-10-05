#!/usr/bin/env python3
"""Nodes of a UI dump whose resource-id starts with a prefix, one per line: id<TAB>l t r b<TAB>selected<TAB>text.

usage: pnodes.py <dump.xml> <prefix>
"""
import re
import sys

xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    rid = re.search(r'resource-id="([^"]*)"', s)
    if not rid or not rid.group(1).startswith(sys.argv[2]):
        continue
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
    sel = re.search(r'selected="([^"]*)"', s)
    text = re.search(r'text="([^"]*)"', s)
    print("%s\t%s\t%s\t%s" % (rid.group(1), " ".join(b.groups()) if b else "", sel.group(1) if sel else "", text.group(1) if text else ""))
