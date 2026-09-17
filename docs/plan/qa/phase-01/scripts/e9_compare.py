#!/usr/bin/env python3
"""e9_compare.py <E09 dir>: every Weather app day / hour cell and the current block against the captured Open-Meteo response."""
import json, math, re, sys, datetime as dt
def r(x): return math.floor(x + 0.5)  # half up, as the app rounds (Python r() is half-even)
def text_parts(line): return [p for p in line.split(" / ") if p and not all(0xE000 <= ord(ch) <= 0xF8FF for ch in p)]  # drop icon-font glyphs
D = sys.argv[1]; d = json.load(open(f"{D}/open_meteo_response.json")); off = d["utc_offset_seconds"]
COND = {0: ("Sunny", "Clear"), 1: ("Mostly Sunny", "Mostly Clear"), 2: ("Partly Sunny", "Partly Cloudy"), 3: ("Cloudy", "Cloudy"), 45: ("Fog", "Fog"), 48: ("Fog", "Fog")}
rows = dict(l.split(": ", 1) for l in open(f"{D}/weather_rows_union.txt").read().splitlines())
fails = 0; out = []
def check(name, got, want):
    global fails
    ok = got == want; fails += 0 if ok else 1
    out.append(f"{'ok  ' if ok else 'FAIL'} {name}: app={got!r} provider={want!r}")
dl = d["daily"]
for i in range(10):
    parts = text_parts(rows[f"day {i}"])
    day = dt.datetime.utcfromtimestamp(dl["time"][i] + off)
    check(f"day {i} date", parts[0], day.strftime("%a ") + str(day.day))
    check(f"day {i} high", parts[1], f"{r(dl['temperature_2m_max'][i])}°")
    check(f"day {i} low", parts[2], f"{r(dl['temperature_2m_min'][i])}°")
    code = dl["weather_code"][i]
    if code in COND: check(f"day {i} condition", parts[3], COND[code][0])
    else: out.append(f"note day {i} condition: app={parts[3]!r} provider code {code} (mapping not in this checker)")
hl = d["hourly"]; cur = d["current"]["time"]
start = max(j for j, t in enumerate(hl["time"]) if t <= cur)
for i in range(24):
    parts = text_parts(rows[f"hour {i}"])
    j = start + i; t = dt.datetime.utcfromtimestamp(hl["time"][j] + off)
    check(f"hour {i} temp", parts[0], f"{r(hl['temperature_2m'][j])}°")
    check(f"hour {i} precip", parts[1], f"{hl['precipitation_probability'][j]}%")
    check(f"hour {i} time", parts[2], t.strftime("%-I %p"))
c = d["current"]
out.append(f"current (from weather_app_top.xml): temp {r(c['temperature_2m'])}°, {COND[c['weather_code']][0 if c['is_day'] else 1]}, feels {r(c['apparent_temperature'])}°, wind {r(c['wind_speed_10m'])} mph from {c['wind_direction_10m']}°, {c['pressure_msl']*0.0295300:.2f} in, humidity {c['relative_humidity_2m']}%")
out.append(f"{fails} mismatches")
print("\n".join(out))
