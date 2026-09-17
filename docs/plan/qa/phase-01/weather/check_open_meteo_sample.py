#!/usr/bin/env python3
"""Phase 01 task 12: check OpenMeteoProvider.parse() against a real Open-Meteo response (no JVM needed).

Builds the request exactly as OpenMeteoProvider.describeRequest() does (field lists read from the Kotlin source),
fetches it (or reuses the saved sample with --offline), and asserts that every JSON key parse() reads exists with
the type the Kotlin getter needs. The key set is also read from the Kotlin source, so a key added to parse()
without a type rule here fails the check.

Usage (from the repo root): python3 docs/plan/qa/phase-01/weather/check_open_meteo_sample.py [--offline]
"""
import json
import re
import sys
import urllib.request

SRC = "app/src/main/kotlin/app/tileshell/weather/WeatherProvider.kt"
SAMPLE = "docs/plan/qa/phase-01/weather/open-meteo-sample.json"
LAT, LON = 40.71, -74.01  # sample coordinate (New York), imperial units as the US region requests them

src = open(SRC).read()


def const(name):
    return re.search(r'const val %s = "([^"]+)"' % name, src).group(1)


url = (f"{const('ENDPOINT')}?latitude={LAT:.4f}&longitude={LON:.4f}&current={const('CURRENT_FIELDS')}"
       f"&hourly={const('HOURLY_FIELDS')}&daily={const('DAILY_FIELDS')}&forecast_days=10&timezone=auto&timeformat=unixtime"
       f"&temperature_unit=fahrenheit&wind_speed_unit=mph")

if "--offline" in sys.argv:
    body = json.load(open(SAMPLE))
else:
    print("GET", url)
    with urllib.request.urlopen(url, timeout=30) as r:
        assert r.status == 200, r.status
        body = json.loads(r.read())
    with open(SAMPLE, "w") as f:
        json.dump(body, f, indent=1, ensure_ascii=False)
    print("saved", SAMPLE)

num = (int, float)


class Arr(tuple):
    """Array rule: (element type, element may be null where parse() checks isNull)."""


# (section, key) -> python type for scalars, or Arr((element type, nullable)) for arrays
RULES = {
    ("root", "latitude"): num, ("root", "longitude"): num, ("root", "timezone"): str,
    ("root", "current"): dict, ("root", "hourly"): dict, ("root", "daily"): dict,
    ("current", "time"): int, ("current", "temperature_2m"): num, ("current", "apparent_temperature"): num,
    ("current", "relative_humidity_2m"): int, ("current", "pressure_msl"): num, ("current", "wind_speed_10m"): num,
    ("current", "wind_direction_10m"): int, ("current", "weather_code"): int, ("current", "is_day"): int,
    ("hourly", "time"): Arr((int, False)), ("hourly", "temperature_2m"): Arr((num, True)),
    ("hourly", "weather_code"): Arr((int, True)), ("hourly", "precipitation_probability"): Arr((int, True)),
    ("hourly", "is_day"): Arr((int, True)),
    ("daily", "time"): Arr((int, False)), ("daily", "weather_code"): Arr((int, True)),
    ("daily", "temperature_2m_max"): Arr((num, True)), ("daily", "temperature_2m_min"): Arr((num, True)),
    ("daily", "precipitation_probability_max"): Arr((int, True)), ("daily", "sunrise"): Arr((int, True)),
    ("daily", "sunset"): Arr((int, True)),
}

# Keys parse() reads, taken from the Kotlin source (receivers root / c / h / d).
parse_src = src[src.index("fun parse("):src.index("private fun JSONArray.intOrNull")]
section = {"root": "root", "c": "current", "h": "hourly", "d": "daily"}
read = {(section[m.group(1)], m.group(2)) for m in re.finditer(r'\b(root|c|h|d)\.(?:get|opt)\w*\("([a-z_0-9]+)"', parse_src)}
read -= {("root", "error"), ("root", "reason")}  # only present on an error body
if read != set(RULES):
    print(f"FAIL: parse() keys differ from the rules: only in code {read - set(RULES)}, only in rules {set(RULES) - read}")
    sys.exit(1)

failures = []
sections = {"root": body, "current": body.get("current", {}), "hourly": body.get("hourly", {}), "daily": body.get("daily", {})}
for (sec, key), rule in sorted(RULES.items()):
    obj = sections[sec]
    if key not in obj:
        failures.append(f"{sec}.{key} missing")
        continue
    v = obj[key]
    if isinstance(rule, Arr):
        et, nullable = rule
        if not isinstance(v, list):
            failures.append(f"{sec}.{key} not an array")
            continue
        bad = [x for x in v if not ((x is None and nullable) or (isinstance(x, et) and not isinstance(x, bool)))]
        if bad:
            failures.append(f"{sec}.{key} bad elements {bad[:3]}")
    elif isinstance(v, bool) or not isinstance(v, rule):
        failures.append(f"{sec}.{key} is {type(v).__name__}, expected {rule}")

h, d = body["hourly"], body["daily"]
if len({len(h[k]) for k in h}) != 1:
    failures.append("hourly arrays differ in length")
if len({len(d[k]) for k in d}) != 1:
    failures.append("daily arrays differ in length")
if len(d["time"]) != 10:
    failures.append(f"daily has {len(d['time'])} days, expected 10")
if len(h["time"]) < 24:
    failures.append("fewer than 24 hourly values")
if body.get("current_units", {}).get("temperature_2m") != "°F":
    failures.append("temperature unit is not °F")

cu = body["current_units"]
c = body["current"]
print(f"keys checked: {len(RULES)}  hourly: {len(h['time'])}  daily: {len(d['time'])}  timezone: {body['timezone']}")
print("current:", {k: f"{v}{cu.get(k, '')}" for k, v in c.items()})
if failures:
    print("FAIL")
    for f in failures:
        print(" -", f)
    sys.exit(1)
print("PASS: every key OpenMeteoProvider.parse() reads is present with the type its getter needs")
