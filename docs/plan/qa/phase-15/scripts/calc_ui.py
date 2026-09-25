#!/usr/bin/env python3
"""Phase 15 E12 / E13 / E29 and the Calculator edge cases: drive the REAL Calculator's pane, Converter and Date pages.

Built on calc_drive.py's floor (dump / by_id / centre / tap_many): every step is a real tap at a tagged node's own
bounds and every value read is the tagged node's own text (or, where the build puts the label in an untagged child
TextView — the pane rows, the pickers' rows — that child's text, which `label_of` reads). Expectations come from
calc-cases.tsv (host-computed by gen_calc_cases.py from Windows' source); this file never computes an answer.

Usage (needs ANDROID_SERIAL):
  calc_ui.py converter <out.tsv>        E12: every `converter` line of calc-cases.tsv through the pane, the unit
                                        pickers and the pad; writes id / expected / got / keys / units per line
  calc_ui.py date <out.tsv>             E29: every `date` line through the date page's pickers; writes id / expected /
                                        got / inputs / the `[calc] date` ring line logged since the case's MARK
  calc_ui.py goto <standard|scientific|programmer|date|converter:<1..12>>   the page, through the pane
  calc_ui.py pane-order <out.txt>       E12: the pane's rows top to bottom (tag<TAB>label), scrolling to the end
  calc_ui.py label <tag>                the tagged node's label (own text, else its first child's)
  calc_ui.py set-date <from|to> YYYY-MM-DD        one date field through its picker (E29's zone step)
  calc_ui.py probe-drag                 records how many rows a 3-row drag moves a picker column (H19 note)
"""
import os, re, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import calc_drive as cd  # noqa: E402

TSV = os.path.join(os.path.dirname(HERE), "calc-cases.tsv")
PKG = "app.tileshell"
LISTENER = f"{PKG}/.feeds.TileNotificationListener"
CATEGORIES = ["Volume", "Length", "Weight and Mass", "Temperature", "Energy", "Area", "Speed", "Time", "Power", "Data",
              "Pressure", "Angle"]


# ---------------------------------------------------------------------------------------------------- the floor

def bounds(n):
    return tuple(map(int, re.findall(r"-?\d+", n.get("bounds"))))


def label_of(n):
    """The node's own text, else the first descendant's non-empty text (the build's pane rows and picker rows)."""
    if n is None:
        return None
    if n.get("text"):
        return n.get("text")
    for c in n.iter("node"):
        if c is not n and c.get("text"):
            return c.get("text")
    return ""


def tap(root, rid, settle=0.4):
    n = cd.by_id(root, rid)
    if n is None:
        raise RuntimeError(f"no node {rid} on screen")
    x, y = cd.centre(n)
    cd.adb("shell", "input", "tap", str(x), str(y))
    time.sleep(settle)


def tap_node(n, settle=0.3):
    x, y = cd.centre(n)
    cd.adb("shell", "input", "tap", str(x), str(y))
    time.sleep(settle)


def swipe(x1, y1, x2, y2, ms):
    cd.adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def is_on(n):
    return n is not None and (n.get("checked") == "true" or n.get("selected") == "true")


def by_prefix(root, prefix):
    return [n for n in cd.nodes(root) if (n.get("resource-id") or "").startswith(prefix)]


def ring_mark():
    return cd.adb("shell", "date", "+%s%3N").strip()


def ring_since(mark, needle=None):
    out = cd.adb("shell", "dumpsys", "activity", "service", LISTENER)
    lines = []
    for line in out.splitlines():
        m = re.search(r"\bwall=(\d+)", line)
        if m and int(m.group(1)) >= int(mark) and (needle is None or needle in line):
            lines.append(line.rstrip())
    return lines


def launch():
    """Home, then the Calculator on Standard (its own task), and the pane closed."""
    return cd.open_mode("standard")


# ---------------------------------------------------------------------------------------------------- the pane

def open_pane():
    root = cd.dump()
    if cd.by_id(root, "calc_pane") is None:
        tap(root, "calc_menu", 0.7)
        root = cd.dump()
    if cd.by_id(root, "calc_pane") is None:
        raise RuntimeError("the pane did not open")
    return root


def close_pane():
    root = cd.dump()
    if cd.by_id(root, "calc_pane") is not None:
        cd.adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.6)


def _row_inside(n, lst):
    x1, y1, x2, y2 = bounds(n)
    lx1, ly1, lx2, ly2 = bounds(lst)
    return y1 >= ly1 and y2 <= ly2


def pane_tap(rid):
    """Opens the pane and taps its row `rid`, scrolling the list (a slow drag, no fling) when the row is off the fold."""
    root = open_pane()
    lst = cd.by_id(root, "calc_pane_list")
    lx1, ly1, lx2, ly2 = bounds(lst)
    cx = (lx1 + lx2) // 2
    for direction in (-1, -1, -1, 1, 1, 1, 1, 1, 1):
        n = cd.by_id(root, rid)
        if n is not None and _row_inside(n, lst):
            tap_node(n, 0.7)
            return
        if direction < 0:
            swipe(cx, ly2 - 80, cx, ly2 - 80 - 4 * 144, 700)
        else:
            swipe(cx, ly1 + 80, cx, ly1 + 80 + 4 * 144, 700)
        time.sleep(0.8)
        root = cd.dump()
    raise RuntimeError(f"pane row {rid} not reachable")


def pane_order(out):
    """The pane's rows top to bottom, over as many scrolls as the list needs; written as tag<TAB>label lines."""
    root = open_pane()
    lst = cd.by_id(root, "calc_pane_list")
    lx1, ly1, lx2, ly2 = bounds(lst)
    cx = (lx1 + lx2) // 2
    seen = {}
    order = []
    for _ in range(4):
        rows = [n for n in cd.nodes(root) if (n.get("resource-id") or "").startswith(("calc_mode:", "calc_converter_category:"))]
        rows.sort(key=lambda n: bounds(n)[1])
        for n in rows:
            rid = n.get("resource-id")
            if rid not in seen:
                seen[rid] = label_of(n)
                order.append(rid)
        swipe(cx, ly2 - 80, cx, ly2 - 80 - 4 * 144, 700)
        time.sleep(0.8)
        root = cd.dump()
    with open(out, "w", encoding="utf-8") as o:
        for rid in order:
            o.write(f"{rid}\t{seen[rid]}\n")
    # the whole dump's texts, for the "no node reads Currency" check
    with open(out + ".texts", "w", encoding="utf-8") as o:
        for n in cd.nodes(root):
            if n.get("text"):
                o.write(n.get("text") + "\n")
    close_pane()


def goto(page):
    """The page through the pane: standard | scientific | programmer | date | converter:<n>."""
    if page.startswith("converter:"):
        n = int(page.split(":")[1])
        pane_tap(f"calc_converter_category:{n}")
        root = cd.dump()
        if not is_on(cd.by_id(root, "calc_mode:converter")):
            raise RuntimeError("calc_mode:converter is not the page showing")
        title = cd.text_of(root, "calc_title")
        if title != CATEGORIES[n - 1].upper():
            raise RuntimeError(f"converter title [{title}] is not [{CATEGORIES[n - 1].upper()}]")
        return root
    pane_tap(f"calc_mode:{page}")
    root = cd.dump()
    if not is_on(cd.by_id(root, f"calc_mode:{page}")):
        raise RuntimeError(f"calc_mode:{page} is not the page showing")
    return root


# ---------------------------------------------------------------------------------------------------- the converter

def set_unit(which, name):
    """The top (1) or bottom (2) unit through its picker: the row whose label is the en-US unit name."""
    tag = f"calc_converter_unit{which}"
    root = cd.dump()
    if cd.text_of(root, tag) == name:
        return
    tap(root, tag, 0.7)
    root = cd.dump()
    picker = cd.by_id(root, "calc_unit_picker")
    if picker is None:
        raise RuntimeError(f"no calc_unit_picker after tapping {tag}")
    px1, py1, px2, py2 = bounds(picker)
    for _ in range(6):
        hit = None
        for n in by_prefix(root, "calc_unit:"):
            if label_of(n) == name:
                hit = n
                break
        if hit is not None and _row_inside(hit, picker):
            tap_node(hit, 0.6)
            break
        swipe((px1 + px2) // 2, py2 - 60, (px1 + px2) // 2, py2 - 60 - 3 * 132, 600)
        time.sleep(0.7)
        root = cd.dump()
    else:
        raise RuntimeError(f"unit [{name}] not found in the picker")
    root = cd.dump()
    got = cd.text_of(root, tag)
    if got != name:
        raise RuntimeError(f"{tag} reads [{got}] after picking [{name}]")


def converter_cases(out):
    rows = []
    for line in open(TSV, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) < 5 or f[0].startswith("#") or f[0] == "id" or f[1] != "converter":
            continue
        rows.append(f)
    launch()
    current = None
    with open(out, "w", encoding="utf-8") as o:
        for f in rows:
            cid, setup_s, keys, expected = f[0], f[2], f[3], f[4]
            setup = dict(p.split("=", 1) for p in cd.SETUP_SPLIT.split(setup_s.strip()) if p)
            category, frm, to = setup["category"], setup["from"], setup["to"]
            try:
                if category != current:
                    goto(f"converter:{CATEGORIES.index(category) + 1}")
                    current = category
                root = cd.dump()
                tap(root, "calc_key:clear", 0.3)
                set_unit(1, frm)
                set_unit(2, to)
                root = cd.dump()
                pts = cd.key_points(root)
                missing = [k for k in keys.split() if k not in pts]
                if missing:
                    o.write(f"{cid}\t{expected}\tMISSING KEY {','.join(missing)}\t{keys}\t{category}: {frm} -> {to}\n")
                    o.flush()
                    continue
                cd.tap_many([pts[k] for k in keys.split()])
                time.sleep(0.4)
                root = cd.dump()
                got = cd.text_of(root, "calc_converter_value2")
                typed = cd.text_of(root, "calc_converter_value1")
                u1, u2 = cd.text_of(root, "calc_converter_unit1"), cd.text_of(root, "calc_converter_unit2")
                title = cd.text_of(root, "calc_title")
                o.write(f"{cid}\t{expected}\t{got}\t{keys}\t{title}: {u1} [{typed}] -> {u2}\n")
                o.flush()
                cd.tap_many([pts["clear"]])
            except Exception as e:  # a driver failure is recorded as the case's result, never hidden
                o.write(f"{cid}\t{expected}\tDRIVER ERROR {e}\t{keys}\t{category}: {frm} -> {to}\n")
                o.flush()
                close_pane()


# ---------------------------------------------------------------------------------------------------- the date page

def picker_rows(root, col):
    rows = {}
    for n in by_prefix(root, f"calc_date_pick:{col}:"):
        rows[int(n.get("resource-id").split(":")[2])] = n
    return rows


def picker_selected(root, col):
    for v, n in picker_rows(root, col).items():
        if is_on(n):
            return v
    return None


def set_column(col, target):
    """Brings the column's centre row to `target` by taps: a visible value is tapped directly, otherwise the farthest
    visible row toward it (three rows per tap). The taps are planned from one dump (the values are consecutive
    integers, seven rows show, the chosen one in the middle) and sent as one batch, then ONE dump verifies the
    column's own checked row; if it is not `target` the slow path below re-reads the screen after every tap."""
    root = cd.dump()
    rows = picker_rows(root, col)
    sel = picker_selected(root, col)
    if rows and sel is not None and sel != target:
        col_node = cd.by_id(root, f"calc_date_picker_column:{col}")
        x1, y1, x2, y2 = bounds(col_node)
        row_px = (y2 - y1) / 7.0
        cx = (x1 + x2) // 2
        plan = []
        cur = sel
        while cur != target and len(plan) < 400:
            k = max(-3, min(3, target - cur))
            plan.append(k)
            cur += k
        taps = [f"input tap {cx} {int(y1 + (k + 3) * row_px + row_px / 2)}" for k in plan]
        for i in range(0, len(taps), 10):
            cd.adb("shell", "; sleep 0.3; ".join(taps[i:i + 10]))
        time.sleep(0.4)
        root = cd.dump()
        if picker_selected(root, col) == target:
            return
    for _ in range(80):
        root = cd.dump()
        rows = picker_rows(root, col)
        if not rows:
            raise RuntimeError(f"no picker column {col} on screen")
        sel = picker_selected(root, col)
        if sel == target:
            return
        if target in rows:
            tap_node(rows[target], 0.35)
            continue
        step = max(rows) if target > (sel if sel is not None else 0) else min(rows)
        if step == sel:
            raise RuntimeError(f"picker column {col} cannot move from {sel} toward {target}")
        tap_node(rows[step], 0.35)
    raise RuntimeError(f"picker column {col} never reached {target}")


def set_date_field(tag, iso):
    """Opens the field's picker and sets month, year, then day (the day list follows the month), then ✓."""
    y, m, d = (int(p) for p in iso.split("-"))
    root = cd.dump()
    tap(root, tag, 0.7)
    root = cd.dump()
    if cd.by_id(root, "calc_date_picker") is None:
        raise RuntimeError(f"no calc_date_picker after tapping {tag}")
    set_column("month", m)
    set_column("year", y)
    set_column("day", d)
    root = cd.dump()
    for col, want in (("month", m), ("year", y), ("day", d)):
        if picker_selected(root, col) != want:
            raise RuntimeError(f"{tag}: column {col} shows {picker_selected(root, col)}, wanted {want}")
    tap(root, "calc_date_pick_ok", 0.7)


def set_amount_field(years, months, days):
    root = cd.dump()
    tap(root, "calc_date_amount", 0.7)
    root = cd.dump()
    if cd.by_id(root, "calc_date_picker") is None:
        raise RuntimeError("no calc_date_picker after tapping calc_date_amount")
    set_column("years", years)
    set_column("months", months)
    set_column("days", days)
    root = cd.dump()
    for col, want in (("years", years), ("months", months), ("days", days)):
        if picker_selected(root, col) != want:
            raise RuntimeError(f"amount: column {col} shows {picker_selected(root, col)}, wanted {want}")
    tap(root, "calc_date_pick_ok", 0.7)


def set_op(op):
    root = cd.dump()
    n = cd.by_id(root, f"calc_date_op:{op}")
    if n is None:
        raise RuntimeError(f"no calc_date_op:{op}")
    if not is_on(n):
        tap_node(n, 0.5)
        root = cd.dump()
        if not is_on(cd.by_id(root, f"calc_date_op:{op}")):
            raise RuntimeError(f"calc_date_op:{op} did not select")


def date_cases(out):
    rows = []
    for line in open(TSV, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) < 5 or f[0].startswith("#") or f[0] == "id" or f[1] != "date":
            continue
        rows.append(f)
    launch()
    goto("date")
    with open(out, "w", encoding="utf-8") as o:
        for f in rows:
            cid, setup_s, keys, expected = f[0], f[2], f[3], f[4]
            op = dict(p.split("=", 1) for p in setup_s.split())["op"]
            kv = dict(p.split("=", 1) for p in keys.split())
            mark = ring_mark()
            try:
                set_op(op)
                set_date_field("calc_date_from", kv["from"])
                if op == "difference":
                    set_date_field("calc_date_to", kv["to"])
                    inputs = f"{kv['from']} {kv['to']}"
                else:
                    set_amount_field(int(kv["years"]), int(kv["months"]), int(kv["days"]))
                    inputs = f"{kv['from']} years={kv['years']} months={kv['months']} days={kv['days']}"
                time.sleep(0.3)
                root = cd.dump()
                got = (cd.text_of(root, "calc_date_result") or "").replace("\n", " | ")
                shown_from = cd.text_of(root, "calc_date_from")
                shown_to = cd.text_of(root, "calc_date_to") if op == "difference" else cd.text_of(root, "calc_date_amount")
                ring = ring_since(mark, "[calc] date")
                last = ring[-1] if ring else ""
                o.write(f"{cid}\t{expected}\t{got}\t{op} {inputs}\t{last}\t{shown_from} / {shown_to}\n")
                o.flush()
            except Exception as e:
                o.write(f"{cid}\t{expected}\tDRIVER ERROR {e}\t{op} {keys}\t\t\n")
                o.flush()
                root = cd.dump()
                if cd.by_id(root, "calc_date_picker") is not None:
                    tap(root, "calc_date_pick_cancel", 0.5)


def probe_drag():
    """How far a 3-row drag moves the day column (the doc says a drag scrolls; recorded, not graded)."""
    root = cd.dump()
    tap(root, "calc_date_from", 0.7)
    root = cd.dump()
    before = picker_selected(root, "day")
    col = cd.by_id(root, "calc_date_picker_column:day")
    x1, y1, x2, y2 = bounds(col)
    row = (y2 - y1) / 7
    cx = (x1 + x2) // 2
    swipe(cx, int(y1 + 5.5 * row), cx, int(y1 + 5.5 * row - 3 * row - 40), 500)
    time.sleep(0.7)
    root = cd.dump()
    after = picker_selected(root, "day")
    tap(root, "calc_date_pick_cancel", 0.5)
    print(f"day column: {before} -> {after} after a 3-row upward drag ({before is not None and after is not None and after - before} rows moved)")


# ---------------------------------------------------------------------------------------------------- CLI

def main():
    cmd = sys.argv[1]
    if cmd == "converter":
        converter_cases(sys.argv[2])
    elif cmd == "date":
        date_cases(sys.argv[2])
    elif cmd == "goto":
        goto(sys.argv[2])
    elif cmd == "pane-order":
        pane_order(sys.argv[2])
    elif cmd == "label":
        print(label_of(cd.by_id(cd.dump(), sys.argv[2])) or "")
    elif cmd == "set-date":
        set_date_field("calc_date_from" if sys.argv[2] == "from" else "calc_date_to", sys.argv[3])
    elif cmd == "probe-drag":
        probe_drag()
    else:
        sys.exit(f"unknown command {cmd}")


if __name__ == "__main__":
    main()
