#!/usr/bin/env python3
"""Set or remove one key in a SharedPreferences XML. New file on stdout.

usage: prefs_edit.py <prefs.xml> <key> boolean|string <value|--remove>
"""
import sys
import xml.etree.ElementTree as ET

path, key, kind, value = sys.argv[1:5]
try:
    root = ET.parse(path).getroot()
except Exception:
    root = ET.Element("map")
for el in list(root):
    if el.get("name") == key:
        root.remove(el)
if value != "--remove":
    if kind == "boolean":
        ET.SubElement(root, "boolean", {"name": key, "value": value})
    else:
        ET.SubElement(root, "string", {"name": key}).text = value
sys.stdout.write('<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n')
sys.stdout.write(ET.tostring(root, encoding="unicode"))
