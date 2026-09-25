#!/usr/bin/env python3
"""Phase 15 E11 / E12: drive the REAL Calculator app with calc-cases.tsv lines and read what it displays.

Every key is a tap at the bounds of its own `calc_key:<name>` node (uiautomator dump of the page showing); the result
is the `calc_display` node's own text (and `calc_converter_value*` / `calc_date_result` for the other modes). The
expectations in the tsv were computed on the host from Windows' source (gen_calc_cases.py) — this script never
computes an answer, it only presses and reads.

Usage: calc_drive.py <mode> <out.tsv> [--limit N]
  mode: standard | scientific | programmer
Writes one line per case: id <TAB> expected <TAB> got <TAB> keys. Needs ANDROID_SERIAL.
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
TSV = os.path.join(os.path.dirname(HERE), "calc-cases.tsv")
TMP = os.environ.get("TMPDIR", "/tmp")
SETUP_SPLIT = re.compile(r"\s+(?=(?:angle|radix|word|category|from|to|op)=)")


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def dump():
    path = os.path.join(TMP, "calc_drive.xml")
    for _ in range(4):
        adb("shell", "uiautomator", "dump", "/sdcard/calc_drive.xml")
        xml = adb("shell", "cat", "/sdcard/calc_drive.xml")
        if "<node" in xml:
            open(path, "w").write(xml)
            return ET.fromstring(xml)
        time.sleep(0.8)
    raise RuntimeError("uiautomator dump failed 4 times")


def nodes(root):
    return list(root.iter("node"))


def by_id(root, rid):
    for n in nodes(root):
        if n.get("resource-id") == rid:
            return n
    return None


def centre(n):
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def text_of(root, rid):
    n = by_id(root, rid)
    return None if n is None else n.get("text")


def tap_many(points):
    # One shell per batch: each input tap is a real, separate touch; the batch only saves adb round trips.
    for i in range(0, len(points), 12):
        cmd = "; ".join(f"input tap {x} {y}" for x, y in points[i:i + 12])
        adb("shell", cmd)


def key_points(root):
    pts = {}
    for n in nodes(root):
        rid = n.get("resource-id") or ""
        if rid.startswith("calc_key:"):
            pts[rid[len("calc_key:"):]] = centre(n)
    return pts


def selected(root, rid):
    # Compose reports the `selected` semantics as uiautomator's selected="true" on a Tab, and as checked="true" on any
    # other node (its accessibility mapping); both mean "this is the one showing / on".
    n = by_id(root, rid)
    return n is not None and (n.get("selected") == "true" or n.get("checked") == "true")


def any_text(root, choices):
    for n in nodes(root):
        if n.get("text") in choices:
            return n.get("text")
    return None


def open_mode(mode):
    # Home first: a window left on top (Tess's session, a dialog) would take every tap.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.0)
    adb("shell", "am", "start", "-W", "-n", "app.tileshell/.calculator.CalculatorActivity", "--es", "page", mode)
    time.sleep(1.5)
    root = dump()
    if not selected(root, f"calc_mode:{mode}"):
        raise RuntimeError(f"calc_mode:{mode} is not the page showing")
    return root


def settle_state(mode, setup, root, pts):
    """Clear, then bring ↑ / HYP / F-E off and angle / radix / word to the case's setup (defaults otherwise)."""
    taps = [pts["clear"]]
    for toggle in ("inv", "hyp", "fe"):
        if toggle in pts and selected(root, f"calc_key:{toggle}"):
            taps.append(pts[toggle])
    tap_many(taps)
    if mode == "scientific":
        want = setup.get("angle", "deg").upper()
        for _ in range(3):
            root = dump()
            if any_text(root, {"DEG", "RAD", "GRAD"}) == want:
                break
            tap_many([pts["angle"]])
    if mode == "programmer":
        want_r = setup.get("radix", "dec")
        root = dump()
        if not selected(root, f"calc_key:radix_{want_r}"):
            tap_many([pts[f"radix_{want_r}"]])
        want_w = setup.get("word", "qword").upper()
        for _ in range(4):
            root = dump()
            if any_text(root, {"QWORD", "DWORD", "WORD", "BYTE"}) == want_w:
                break
            tap_many([pts["word"]])
        tap_many([pts["clear"]])


def main():
    mode, out = sys.argv[1], sys.argv[2]
    limit = int(sys.argv[sys.argv.index("--limit") + 1]) if "--limit" in sys.argv else None
    rows = []
    for line in open(TSV, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) < 5 or f[0].startswith("#") or f[0] == "id" or f[1] != mode:
            continue
        rows.append(f)
    if limit:
        rows = rows[:limit]
    root = open_mode(mode)
    pts = key_points(root)
    with open(out, "w", encoding="utf-8") as o:
        for f in rows:
            cid, setup_s, keys, expected = f[0], f[2], f[3], f[4]
            setup = dict(p.split("=", 1) for p in SETUP_SPLIT.split(setup_s.strip()) if p) if setup_s.strip() else {}
            settle_state(mode, setup, root, pts)
            missing = [k for k in keys.split() if k not in pts]
            if missing:
                o.write(f"{cid}\t{expected}\tMISSING KEY {','.join(missing)}\t{keys}\n")
                continue
            tap_many([pts[k] for k in keys.split()])
            time.sleep(0.3)
            root = dump()
            got = text_of(root, "calc_display")
            o.write(f"{cid}\t{expected}\t{got}\t{keys}\n")
            o.flush()


if __name__ == "__main__":
    main()
