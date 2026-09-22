#!/usr/bin/env python3
"""One WeatherReport for the Weather tile's sky (INDEX Change Log 2026-09-21 item 2).

SkyRules' own comment says the rainbow "cannot be summoned on demand in QA: it needs real weather,
and E9 runs against whatever the sky is doing that morning." That is true of the live provider and
not true of the feed: WeatherFeed caches its last report in filesDir/weather/last-report.json and
loads it at start, so a report written there IS the weather as far as the shell is concerned. Every
scene, the rainbow included, becomes reproducible without waiting for the sky to cooperate.

usage: weather_fixture.py <wmo-code> day|night <out.json>
"""
import json
import sys
import time

HOUR = 3_600_000
DAY = 24 * HOUR

code = int(sys.argv[1])
is_day = sys.argv[2] == "day"
out = sys.argv[3]
now = int(time.time() * 1000)

# The hours BEFORE now are what SkyRules.rainbow reads for "it has just rained"; the hours after are
# what the tile's own 3-day face and the Weather app draw.
hourly = [
    {"timeMs": now + i * HOUR, "temperature": 60.0 + i, "code": code, "precipPct": 30, "isDay": is_day}
    for i in range(-6, 18)
]
daily = [
    {"dateMs": now + d * DAY, "code": code, "high": 72.0 - d, "low": 51.0 - d,
     "precipPct": 30, "sunriseMs": now - 5 * HOUR, "sunsetMs": now + 6 * HOUR}
    for d in range(3)
]
report = {
    "provider": "qa-fixture",
    "latitude": 39.7392, "longitude": -104.9903, "place": "Denver",
    "timeZoneId": "America/Denver", "units": "IMPERIAL",
    # Now, so the face is not drawn stale: a stale report replaces the detail line with "Updated ...".
    "fetchedAtMs": now,
    "current": {
        "timeMs": now, "temperature": 64.0, "feelsLike": 62.0, "humidityPct": 40,
        "pressureHpa": 1012.0, "windSpeed": 8.0, "windDirectionDeg": 270,
        "code": code, "isDay": is_day,
    },
    "hourly": hourly,
    "daily": daily,
}
with open(out, "w") as f:
    json.dump(report, f)
print(f"{out}: code={code} isDay={'day' if is_day else 'night'}")
