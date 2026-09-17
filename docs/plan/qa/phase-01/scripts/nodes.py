#!/usr/bin/env python3
"""nodes.py <dump.xml> <resource-id prefix>: one line per matching node: id | content-desc | descendant texts."""
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    rid = n.get("resource-id", "")
    if rid.startswith(sys.argv[2]):
        texts = [c.get("text") for c in n.iter("node") if c.get("text")]
        print(f"{rid} | desc={n.get('content-desc','')} | texts={' / '.join(texts)}")
