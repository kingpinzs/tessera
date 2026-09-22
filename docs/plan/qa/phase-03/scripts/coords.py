#!/usr/bin/env python3
"""E13: is the saved place within 0.0005 deg of where the AVD was standing?

    coords.py <reminders.json> <lat> <lon>
"""
import json
import sys

store = json.load(open(sys.argv[1]))
want_lat, want_lon = float(sys.argv[2]), float(sys.argv[3])
places = store.get("places", [])
if not places:
    print("no places in the store")
    sys.exit(1)
home = next((p for p in places if p.get("name", "").lower() == "home"), places[0])
dlat = abs(home["lat"] - want_lat)
dlon = abs(home["lon"] - want_lon)
print(f"{home['name']} at {home['lat']:.5f},{home['lon']:.5f} vs {want_lat},{want_lon} "
      f"(d={dlat:.5f},{dlon:.5f})")
sys.exit(0 if dlat <= 0.0005 and dlon <= 0.0005 else 1)
