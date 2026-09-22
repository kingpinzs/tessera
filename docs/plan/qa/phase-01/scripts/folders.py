#!/usr/bin/env python3
"""The folders a Start layout holds, one per line as "id|name|members".

`folders` is a LIST in the persisted layout, not a map. Reading it as a map is how the first run of the
ASKS0922 row printed nothing and then passed an assert_absent against that nothing.

usage: folders.py <layout.json>
"""
import json
import sys

for folder in json.load(open(sys.argv[1]))["folders"]:
    print(f"{folder['id']}|{folder.get('name')}|{len(folder.get('members', []))}")
