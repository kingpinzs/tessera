#!/usr/bin/env python3
"""Phase 16 E19 — the Calendar's geometry, measured on the DRAWN pixels of a screencap, with the dump used only to
find where to look (and for layout boxes that draw nothing of their own).

    cal_geo.py <view> <dump.xml> <shot.png> [key=value …]

Prints one measurement per line, tab-separated; e19.sh turns each into an assertion or a record:
    W  <name>  <expected>  <actual>  <tolerance>     a length or position, epx (1080 px = 360 epx)
    C  <name>  <r g b>     <r g b>   <tolerance>     a colour, levels per channel
    E  <name>  <expected>  <actual>                  an equality
    R  <name>  <value>                               a recorded fact
An actual that could not be measured is printed empty, which fails its assertion.

Every expected value is r11/calendar.md's (the K-number is in the name) or the row's own words; none is read from the
app's source. "sb" below is the drawn status bar's bottom: a value R11 measured under a 24-epx bar is taken from it.
"""
import datetime
import re
import sys
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image

PX = 3.0
PAGE = (26, 26, 26)
RULE = (80, 80, 80)
GREY = (151, 151, 151)
STRIP_CENTRES = [25.0, 76.6, 127.5, 178.4, 229.0, 280.4, 331.3]


class Shot:
    def __init__(self, dump, png):
        self.root = ET.parse(dump).getroot()
        self.a = np.asarray(Image.open(png).convert("RGB")).astype(np.int16)
        self.h, self.w = self.a.shape[:2]
        self.nodes = list(self.root.iter("node"))

    # ---- the dump
    @staticmethod
    def _b(n):
        return tuple(int(v) for v in re.findall(r"-?\d+", n.get("bounds") or ""))

    def node(self, rid):
        for n in self.nodes:
            if n.get("resource-id") == rid:
                return n
        return None

    def b(self, rid):
        n = self.node(rid)
        return self._b(n) if n is not None else None

    def prefixed(self, prefix):
        return [n for n in self.nodes if (n.get("resource-id") or "").startswith(prefix)]

    def texts(self, text):
        return [n for n in self.nodes if n.get("text") == text]

    def child_text_node(self, rid):
        n = self.node(rid)
        if n is None:
            return None
        if n.get("text"):
            return n
        for c in n.iter("node"):
            if c.get("text"):
                return c
        return None

    # ---- the pixels
    def crop(self, box):
        x1, y1, x2, y2 = box
        return self.a[max(0, y1):min(self.h, y2), max(0, x1):min(self.w, x2)], max(0, x1), max(0, y1)

    def mode(self, box):
        c, _, _ = self.crop(box)
        vals, counts = np.unique(c.reshape(-1, 3), axis=0, return_counts=True)
        return tuple(int(v) for v in vals[counts.argmax()])

    def px(self, x, y):
        return tuple(int(v) for v in self.a[min(max(int(y), 0), self.h - 1), min(max(int(x), 0), self.w - 1)])

    def ink(self, box, thr=None):
        """The ink's box (px, right / bottom exclusive): pixels at least half way from the box's background (its most
        common colour) to its core ink colour — R11's half-level crossing."""
        c, ox, oy = self.crop(box)
        if c.size == 0:
            return None
        dist = np.abs(c - np.array(self.mode(box), dtype=np.int16)).max(axis=2)
        if dist.max() < 12:
            return None
        t = thr if thr is not None else max(1, int(dist.max()) // 2)
        ys, xs = np.nonzero(dist >= t)
        return (int(xs.min()) + ox, int(ys.min()) + oy, int(xs.max()) + ox + 1, int(ys.max()) + oy + 1)

    def core(self, box):
        """The ink's core colour: the pixel farthest from the box's background."""
        c, _, _ = self.crop(box)
        if c.size == 0:
            return None
        dist = np.abs(c - np.array(self.mode(box), dtype=np.int16)).max(axis=2)
        if dist.max() < 12:
            return None
        y, x = np.unravel_index(dist.argmax(), dist.shape)
        return tuple(int(v) for v in c[y, x])

    def colour_box(self, box, rgb, tol):
        c, ox, oy = self.crop(box)
        m = np.abs(c - np.array(rgb, dtype=np.int16)).max(axis=2) <= tol
        ys, xs = np.nonzero(m)
        if len(xs) == 0:
            return None
        return (int(xs.min()) + ox, int(ys.min()) + oy, int(xs.max()) + ox + 1, int(ys.max()) + oy + 1)

    def runs(self, axis, fixed, lo, hi, rgb, tol):
        """Runs of a colour along a row (axis 'h', y fixed) or a column ('v', x fixed): [(start, end)], end exclusive."""
        lo, hi = max(0, lo), min(self.w if axis == "h" else self.h, hi)
        line = self.a[fixed, lo:hi] if axis == "h" else self.a[lo:hi, fixed]
        m = np.abs(line - np.array(rgb, dtype=np.int16)).max(axis=1) <= tol
        out, start = [], None
        for i, v in enumerate(m):
            if v and start is None:
                start = i
            if not v and start is not None:
                out.append((start + lo, i + lo)); start = None
        if start is not None:
            out.append((len(m) + lo - (len(m) - start), len(m) + lo))
        return out

    def stem_ratio(self, box):
        """The text's stroke weight: its most common horizontal ink run (a vertical stem's width) across the middle of
        its cap band, over the cap height (the first glyph's drawn height). On this screen a semibold line reads about
        0.19–0.20 and a regular one 0.09–0.15."""
        ib = self.ink(box)
        if ib is None:
            return None
        c, _, _ = self.crop(ib)
        dist = np.abs(c - np.array(self.mode(box), dtype=np.int16)).max(axis=2)
        m = dist >= max(1, int(dist.max()) // 2)
        cols = m.any(axis=0)
        end = 0
        while end < len(cols) and cols[end]:
            end += 1
        rows = np.nonzero(m[:, :end].any(axis=1))[0]
        if len(rows) == 0:
            return None
        top, cap = int(rows.min()), int(rows.max() + 1 - rows.min())
        lens = {}
        for row in m[top + int(cap * 0.35):top + int(cap * 0.65)]:
            start = None
            for i, v in enumerate(row):
                if v and start is None:
                    start = i
                if not v and start is not None:
                    lens[i - start] = lens.get(i - start, 0) + 1; start = None
            if start is not None:
                lens[len(row) - start] = lens.get(len(row) - start, 0) + 1
        if not lens:
            return None
        return max(lens, key=lens.get) / float(cap)


def epx(v):
    return "" if v is None else "%.2f" % (v / PX)


def rgb(c):
    return "" if c is None else "%d %d %d" % c


def out(kind, name, *vals):
    print("\t".join([kind, name] + [str(v) for v in vals]))


def bars(s, a):
    """The drawn status and nav bars (C-17: against BarMetrics' own numbers, passed in by the driver)."""
    sb, nb = s.b("w10m_status_bar"), s.b("w10m_nav_bar")
    view = a.get("view", "")
    out("E", "%s: the drawn status bar is on the view (w10m_status_bar)" % view, "yes", "yes" if sb else "no")
    out("E", "%s: the drawn nav bar is on the view (w10m_nav_bar)" % view, "yes", "yes" if nb else "no")
    if not sb or not nb:
        return 0, s.h
    out("W", "%s: the status bar's height is BarMetrics.STATUS_EPX (%s)" % (view, a["status"]), a["status"], epx(sb[3] - sb[1]), 0.34)
    out("W", "%s: the nav bar's height is BarMetrics.NAV_EPX (%s)" % (view, a["nav"]), a["nav"], epx(nb[3] - nb[1]), 0.34)
    out("W", "%s: the nav bar ends at the screen's bottom edge" % view, epx(s.h), epx(nb[3]), 0.34)
    # drawn: black from the top edge down to the bar's bottom at the far left column (clear of the bar's glyphs)
    r = s.runs("v", 6, 0, sb[3] + 30, (0, 0, 0), 2)
    out("W", "%s: the status bar is drawn black down to its bottom edge (px column 6)" % view, epx(sb[3]), epx(r[0][1]) if r and r[0][0] == 0 else "", 0.34)
    r = s.runs("v", 6, nb[1] - 30, s.h, (0, 0, 0), 2)
    out("W", "%s: the nav bar is drawn black from its top edge (px column 6)" % view, epx(nb[1]), epx(r[-1][0]) if r and r[-1][1] == s.h else "", 0.34)
    return sb[3], nb[1]


def header(s, sbot, tag=""):
    # ≡ : three 1-epx bars at a 5-epx pitch, x 16–36 (K1.1)
    g = s.b("cal_menu_glyph")
    if g:
        region = (g[0] - 12, g[1] - 12, g[2] + 12, g[3] + 12)
        ib = s.ink(region)
        col = s.runs("v", (g[0] + g[2]) // 2, region[1], region[3], s.core(region) or (255, 255, 255), 60)
        out("E", "K1.1%s ≡ is three bars" % tag, 3, len(col))
        for i, (y1, y2) in enumerate(col[:3]):
            out("W", "K1.1%s ≡ bar %d is 1 epx thick" % (tag, i + 1), 1, epx(y2 - y1), 0.34)
        for i in range(1, min(3, len(col))):
            out("W", "K1.1%s ≡ bars %d–%d at a 5-epx pitch" % (tag, i, i + 1), 5, epx(col[i][0] - col[i - 1][0]), 0.5)
        out("W", "K1.1%s ≡ starts at x 16" % tag, 16, epx(ib[0]) if ib else "", 1)
        out("W", "K1.1%s ≡ ends at x 36" % tag, 36, epx(ib[2]) if ib else "", 1)
    else:
        out("E", "K1.1%s ≡ is drawn (cal_menu_glyph)" % tag, "yes", "no")
    t = s.b("cal_month_title")
    tn = s.node("cal_month_title")
    if t:
        ib = s.ink((t[0] - 6, t[1] - 6, t[2] + 6, t[3] + 6))
        text = tn.get("text") or ""
        out("E", "K1.1%s the month and year in caps (\"%s\")" % (tag, text), text.upper(), text)
        out("E", "K1.1%s … a month name and a year" % tag, "yes", "yes" if re.fullmatch(r"\S+ \d{4}", text) else "no")
        out("W", "K1.1%s … at x 51.0 (the ink's left edge)" % tag, 51.0, epx(ib[0]) if ib else "", 1)
        out("W", "K1.1%s … its cap top 15.5 epx below the status bar" % tag, 15.5, epx(ib[1] - sbot) if ib else "", 1)
        out("W", "K1.1%s … cap 11.0" % tag, 11.0, epx(ib[3] - ib[1]) if ib else "", 1)
        sr = s.stem_ratio((t[0] - 6, t[1] - 6, t[2] + 6, t[3] + 6))
        out("E", "K1.1%s … semibold (its stems at least 0.16 of the cap height; a regular line reads 0.09–0.15 here)" % tag, "yes", "yes" if sr and sr >= 0.16 else "no (%s)" % sr)
        out("R", "K1.1%s the title's stroke / cap ratio" % tag, "%.3f" % sr if sr else "")
    else:
        out("E", "K1.1%s the month and year (cal_month_title)" % tag, "yes", "no")
    band = s.b("cal_header_band")
    out("W", "K1.2%s the header band starts at the status bar's bottom" % tag, epx(sbot), epx(band[1]) if band else "", 0.34)
    out("W", "K1.2%s the band is 40 epx tall" % tag, 40, epx(band[3] - band[1]) if band else "", 1)
    out("E", "K1.1%s the chevron after the title is %s" % (tag, "⌃ (the drop-down is open)" if tag else "⌄"), "up" if tag else "down", chevron(s))


def chevron(s):
    c = s.b("cal_header_chevron")
    if not c:
        return "(no cal_header_chevron)"
    region = (c[0] - 6, c[1] - 6, c[2] + 6, c[3] + 6)
    ib = s.ink(region)
    if not ib:
        return "(nothing drawn)"
    crop, _, _ = s.crop(ib)
    dist = np.abs(crop - np.array(s.mode(region), dtype=np.int16)).max(axis=2)
    m = dist >= max(1, int(dist.max()) // 2)
    def width(row):
        xs = np.nonzero(row)[0]
        return int(xs.max() - xs.min() + 1) if len(xs) else 0
    top, bottom = width(m[0]), width(m[-1])
    return "down" if top > bottom else "up" if bottom > top else "(flat: %d / %d)" % (top, bottom)


def app_bar(s, navt):
    r = s.runs("v", 30, navt - 240, navt, RULE, 4)
    edge = r[-1] if r else None
    out("W", "K1.5 the app bar's top edge is 1 epx of (80,80,80) ± 4", 1, epx(edge[1] - edge[0]) if edge else "", 0.34)
    out("W", "K1.5 the app bar is 47 epx (its top edge to the nav bar's top)", 47, epx(navt - edge[0]) if edge else "", 1)
    out("C", "K1.5 its fill is (33,33,33)", "33 33 33", rgb(s.mode((6, (edge[1] if edge else navt - 100) + 6, 200, navt - 6))), 4)
    for name, want in (("today", 217.9), ("new", 150.2), ("view", 82.6), ("more", 24.5)):
        b = s.b("cal_bar:" + name)
        ib = s.ink(b) if b else None
        cx = (ib[0] + ib[2]) / 2.0 if ib else None
        out("W", "K1.6 %s: its glyph's centre %.1f epx from the right edge (the 68-epx CommandBar, 15063 form)" % ("…" if name == "more" else name.capitalize(), want), want, epx(s.w - cx) if cx is not None else "", 1)


def agenda(s, a):
    """sections= any of: chrome (bars, header, page, strip, app bar), today (today's heading, the empty day),
    rows (another day's heading, its event rows)."""
    sections = a.get("sections", "chrome,today,rows").split(",")
    sb, nb = s.b("w10m_status_bar"), s.b("w10m_nav_bar")
    sbot, navt = (sb[3] if sb else 0), (nb[1] if nb else s.h)
    sel = s.b("cal_strip_selected")
    acc = s.px(sel[0] + 6, sel[1] + 6) if sel else None
    if "chrome" in sections:
        sbot, navt = bars(s, dict(a, view="Agenda"))
        header(s, sbot)
        out("C", "K1.3 the page is (26,26,26)", "26 26 26", rgb(s.mode((300, navt - 700, 1000, navt - 300))), 2)
        out("C", "K1.3 the status bar is black", "0 0 0", rgb(s.mode((6, 2, 30, sbot - 2))), 2)
        out("C", "K1.3 the nav bar is black", "0 0 0", rgb(s.mode((6, navt + 4, 100, s.h - 4))), 2)
        # ---- the week strip (K2)
        strip = s.b("cal_strip")
        days = s.prefixed("cal_strip_day:")
        names = [n for n in s.nodes if not n.get("resource-id") and n.get("text") and strip and strip[1] <= s._b(n)[1] < (s._b(days[0])[1] if days else strip[3]) and len(n.get("text")) <= 4]
        names.sort(key=lambda n: s._b(n)[0])
        out("E", "K2.1 seven day names over the strip", 7, len(names))
        for i, n in enumerate(names[:7]):
            ib = s.ink(s._b(n))
            out("W", "K2.1 the week strip on W/7: column %d (%s), its drawn centre %.1f" % (i + 1, n.get("text"), STRIP_CENTRES[i]), STRIP_CENTRES[i], epx((ib[0] + ib[2]) / 2.0) if ib else "", 1)
        if names:
            out("C", "K2.2 day names are (151,151,151)", "151 151 151", rgb(s.core(s._b(names[0]))), 4)
        tops = sorted({s._b(n)[1] for n in days})
        out("E", "K2.4 the 15063 form: two week rows of seven days", "2 14", "%d %d" % (len(tops), len(days)))
        if sel:
            cell = s.b("cal_strip_day:%s" % a.get("today", ""))
            box = s.colour_box((sel[0] - 24, sel[1] - 24, sel[2] + 24, min(sel[3] + 24, cell[3]) if cell else sel[3] + 24), acc, 6)
            out("W", "K2.5 the selected day's accent square is 32 epx wide", 32, epx(box[2] - box[0]) if box else "", 2)
            out("W", "K2.5 … and 32 epx tall", 32, epx(box[3] - box[1]) if box else "", 2)
            out("R", "K2.5 the accent on this screen (the selected square's fill)", rgb(acc))
        else:
            out("E", "K2.5 the selected day's square is drawn (cal_strip_selected)", "yes", "no")
        if len(tops) == 2:
            def digit_core(n):
                t = [c for c in n.iter("node") if c.get("text")]
                return s.core(s._b(t[0])) if t else None
            today = a.get("today", "")
            row1 = [n for n in days if s._b(n)[1] == tops[0] and n.get("selected") != "true" and not n.get("resource-id").endswith(today)]
            row2 = [n for n in days if s._b(n)[1] == tops[1] and n.get("selected") != "true" and not n.get("resource-id").endswith(today)]
            c1, c2 = digit_core(row1[0]) if row1 else None, digit_core(row2[0]) if row2 else None
            out("R", "K2.4 a first-row date's colour and a second-row date's", "%s / %s" % (rgb(c1), rgb(c2)))
            out("E", "K2.4 the second week row is dimmed (its dates at least 60 levels darker than the first row's)", "yes", "yes" if c1 and c2 and max(c1) - max(c2) >= 60 else "no")
        app_bar(s, navt)
    if "today" in sections:
        today = a.get("today")
        th = s.b("cal_day:%s" % today)
        tn = s.node("cal_day:%s" % today)
        ib = s.ink(th) if th else None
        out("W", "K3.3 today's heading (\"%s\") at x 24 (the ink's left edge)" % (tn.get("text") if tn is not None else ""), 24, epx(ib[0]) if ib else "", 1)
        out("C", "K3.3 today's heading is in the accent (the selected square's colour on this screen)", rgb(acc) if acc else "", rgb(s.core(th)) if th else "", 4)
        em = s.node("cal_empty:%s" % today)
        eb = s._b(em) if em is not None else None
        out("E", "K3.4 an empty day reads \"No events today\"", "No events today", em.get("text") if em is not None else "")
        out("C", "K3.4 … in grey (151,151,151)", "151 151 151", rgb(s.core(eb)) if eb else "", 4)
        ib = s.ink(eb) if eb else None
        out("W", "K3.4 … at x 24 (the ink's left edge)", 24, epx(ib[0]) if ib else "", 1)
    if "rows" in sections:
        other = a.get("other")
        oh, on = s.b("cal_day:%s" % other), s.node("cal_day:%s" % other)
        ib = s.ink(oh) if oh else None
        out("W", "K3.2 a day heading (\"%s\", R11's own sample is a Saturday) at x 24 (the ink's left edge)" % (on.get("text") if on is not None else ""), 24, epx(ib[0]) if ib else "", 1)
        out("C", "K3.2 a day heading is white", "255 255 255", rgb(s.core(oh)) if oh else "", 4)
        sr = s.stem_ratio(oh) if oh else None
        out("E", "K3.2 … semibold (its stems at least 0.16 of the cap height; a regular line reads 0.09–0.15 here)", "yes", "yes" if sr and sr >= 0.16 else "no (%s)" % sr)
        calc = tuple(int(v) for v in a.get("colour", "0 99 177").split())
        a1, a2, t1 = a.get("allday1"), a.get("allday2"), a.get("timed1")
        label_tint = None
        for name, eid in (("an all-day row", a1), ("a timed row", t1)):
            row, bar = s.b("cal_event:%s" % eid), s.b("cal_event_bar:%s" % eid)
            if not row or not bar:
                out("E", "K3.8 %s is on the Agenda (cal_event_bar:%s)" % (name, eid), "yes", "no")
                continue
            mid = (bar[1] + bar[3]) // 2
            r = s.runs("h", mid, 0, 90, calc, 4)
            out("W", "K3.8 %s: cal_event_bar's colour bar starts at x 0" % name, 0, epx(r[0][0]) if r else "", 0.34)
            out("W", "K3.8 %s: … and is 8 epx wide" % name, 8, epx(r[0][1] - r[0][0]) if r else "", 1)
            out("C", "K3.8 %s: … in the calendar's colour" % name, rgb(calc), rgb(s.px(12, mid)), 4)
            v = [x for x in s.runs("v", 12, row[1] - 12, row[3] + 12, calc, 4) if x[0] <= mid < x[1]]
            want = 40 if eid == a1 else 56
            out("W", "K3.%s %s: its bar is %d epx tall" % ("5" if eid == a1 else "6", name, want), want, epx(v[0][1] - v[0][0]) if v else "", 1)
            tn = s.node("cal_event_time:%s" % eid)
            if tn is None:   # an all-day row's label carries no tag: the "All day" text inside the row
                lab = [c for c in s.node("cal_event:%s" % eid).iter("node") if c.get("text") == "All day"]
                lb = s._b(lab[0]) if lab else None
                out("E", "K3.5 an all-day row's label reads \"All day\"", "yes", "yes" if lab else "no")
            else:
                lb = s._b(tn)
            ib = s.ink(lb) if lb else None
            out("W", "K3.7 %s: the %s label at x 24.3 (the ink's left edge)" % (name, "\"All day\"" if eid == a1 else "time"), 24.3, epx(ib[0]) if ib else "", 1)
            if lb and label_tint is None:
                label_tint = s.core(lb)
            tb, tnode = s.b("cal_event_title:%s" % eid), s.node("cal_event_title:%s" % eid)
            ib = s.ink(tb) if tb else None
            out("W", "K3.7 %s: the title (\"%s\") at x 92.5 (the ink's left edge)" % (name, tnode.get("text") if tnode is not None else ""), 92.5, epx(ib[0]) if ib else "", 1)
            out("R", "K3.7 %s: the title's text box starts at (epx)" % name, epx(tb[0]) if tb else "")
            out("C", "K3.5 %s: the title is white" % name, "255 255 255", rgb(s.core(tb)) if tb else "", 4)
        b1, b2 = s.b("cal_event_bar:%s" % a1), s.b("cal_event_bar:%s" % a2)
        if b1 and b2:
            v1 = [x for x in s.runs("v", 12, b1[1] - 12, b1[3] + 6, calc, 4)]
            v2 = [x for x in s.runs("v", 12, b2[1] - 6, b2[3] + 12, calc, 4)]
            out("W", "K3.5 all-day bars on a 44-epx pitch (the second bar's top below the first's)", 44, epx(v2[0][0] - v1[0][0]) if v1 and v2 else "", 1)
        else:
            out("E", "K3.5 two all-day rows are on the Agenda", "yes", "no")
        out("R", "K3.5 the label's tint on this screen (the calendar colour's light tint)", rgb(label_tint))


def month(s, a):
    sbot, navt = s.b("w10m_status_bar")[3], s.b("w10m_nav_bar")[1]
    header(s, sbot, " (drop-down open)")
    d = s.b("cal_month_dropdown")
    out("E", "K5.1 the drop-down is open (cal_month_dropdown)", "yes", "yes" if d else "no")
    if not d:
        return
    hb = sbot + int(40 * PX)
    # The border, by its own lines (the panel lies over the page, whose anti-aliased text also passes through this
    # grey): the left and right edges on a row inside the panel, then the left edge's own run from top to bottom.
    row = s.runs("h", hb + 60, 0, s.w, RULE, 4)
    box = None
    if len(row) >= 2:
        col = s.runs("v", row[0][0] + 1, hb - 12, navt - 150, RULE, 4)
        col = max(col, key=lambda r: r[1] - r[0]) if col else None
        if col:
            box = (row[0][0], col[0], row[-1][1], col[1])
    out("W", "K5.1 the panel's border starts at x 5", 5, epx(box[0]) if box else "", 1)
    out("W", "K5.1 … and ends at x 355", 355, epx(box[2]) if box else "", 1)
    out("W", "K5.1 … from the header's bottom (40 epx below the status bar)", 40, epx(box[1] - sbot) if box else "", 1)
    out("W", "K5.1 … down 235 epx", 235, epx(box[3] - box[1]) if box else "", 1)
    if box:
        mid = (box[1] + box[3]) // 2
        r = s.runs("h", mid, 0, 60, RULE, 4)
        out("W", "K5.1 a 1-epx border (its left edge's thickness)", 1, epx(r[0][1] - r[0][0]) if r else "", 0.34)
        out("C", "K5.1 the border is (80,80,80)", "80 80 80", rgb(s.px(box[0] + 1, mid)), 4)
        out("C", "K5.1 the panel's fill is the page colour", "26 26 26", rgb(s.mode((box[0] + 12, box[1] + 12, box[2] - 12, box[3] - 12))), 2)
    cells = s.prefixed("cal_month_cell:")
    out("E", "K5.2 six date rows of seven (42 cal_month_cell nodes inside the panel)", 42, len(cells))
    if len(cells) != 42:
        return
    def digit(n):
        t = [c for c in n.iter("node") if c.get("text")]
        return s.ink(s._b(t[0])) if t else None
    ys = []
    for r in range(6):
        ib = digit(cells[r * 7 + 3])
        ys.append((ib[1] + ib[3]) / 2.0 if ib else None)
    for r in range(1, 6):
        ok = ys[r] is not None and ys[r - 1] is not None
        out("W", "K5.2 date rows %d–%d at a 34.25-epx pitch (the digits' centres)" % (r, r + 1), 34.25, epx(ys[r] - ys[r - 1]) if ok else "", 1)
    for i in range(7):
        ib = digit(cells[14 + i])
        out("W", "K5.2 on the strip's W/7 columns: column %d centre %.1f" % (i + 1, STRIP_CENTRES[i]), STRIP_CENTRES[i], epx((ib[0] + ib[2]) / 2.0) if ib else "", 1)
    shown = a.get("month")
    other = [n for n in cells if not n.get("resource-id")[len("cal_month_cell:"):].startswith(shown) and n.get("selected") != "true"]
    inm = [n for n in cells if n.get("resource-id")[len("cal_month_cell:"):].startswith(shown) and n.get("selected") != "true" and not n.get("resource-id").endswith(a.get("today", "x"))]
    out("E", "K5.3 the panel shows dates of another month", "yes", "yes" if other else "no")
    if other:
        t = [c for c in other[0].iter("node") if c.get("text")]
        out("C", "K5.3 other-month dates are (110,110,110)", "110 110 110", rgb(s.core(s._b(t[0]))) if t else "", 4)
    if inm:
        t = [c for c in inm[0].iter("node") if c.get("text")]
        out("R", "K5.3 an in-month date's colour (R11: white)", rgb(s.core(s._b(t[0]))) if t else "")


def week(s, a):
    sbot, navt = bars(s, dict(a, view="Week"))
    hb = sbot + int(40 * PX)
    cells_top = None
    # the header's bottom: a 1-epx black rule under the band in week view (K1.2)
    r = s.runs("v", 30, hb - 9, hb + 12, (0, 0, 0), 2)
    out("W", "K1.2 the header band is 40 epx: the black rule under it in week view is 40 epx below the status bar", 40, epx(r[0][0] - sbot) if r else "", 1)
    cells_top = r[0][1] if r else hb + 3
    # the vertical split at W/2
    vy = cells_top + 200
    r = [x for x in s.runs("h", vy, 480, 600, RULE, 4)]
    out("W", "K4.1 the cells are split at W/2: a (80,80,80) rule at x 179–180", 179, epx(r[0][0]) if r else "", 1)
    out("W", "K4.1 … 1 epx wide", 1, epx(r[0][1] - r[0][0]) if r else "", 0.34)
    # the horizontal rules, down the left column's far edge
    hr = s.runs("v", 20, cells_top + 30, navt - 141, RULE, 4)
    edges = [cells_top] + [x[0] for x in hr]
    out("E", "K4.1 2 × 4 cells: at least the three rules between the four rows (down the left column)", "yes", "yes" if len(hr) >= 3 else "no (%d)" % len(hr))
    for i in range(1, min(4, len(edges))):
        out("W", "K4.1 cell row %d is 120 epx (rule to rule)" % i, 120, epx(edges[i] - edges[i - 1] + (hr[0][1] - hr[0][0] if i == 1 else 0)), 1)
    if len(edges) >= 5:
        out("W", "K4.1 cell row 4 is 120 epx (a rule closes it on this taller screen)", 120, epx(edges[4] - edges[3]), 1)
    else:
        out("R", "K4.1 cell row 4 has no rule under it; the app bar's top is (epx)", epx(navt - 141))
    labels = s.prefixed("cal_day:")
    out("E", "K4.1 seven day cells (cal_day labels)", 7, len(labels))
    out("E", "K4.2 every cell is labelled \"23 MON\"-style (%s)" % ", ".join(n.get("text") or "" for n in labels), 7, sum(1 for n in labels if re.fullmatch(r"\d{1,2} [A-Z]{3}", n.get("text") or "")))
    twos = 0
    for i, n in enumerate(labels[:7]):
        text = n.get("text") or ""
        ib = s.ink(s._b(n))
        col = 0 if (s._b(n)[0] < s.w // 2) else 1
        if text.startswith("2"):   # R11's sample label is "23 MON": the same leading glyph, the same side bearing
            twos += 1
            out("W", "K4.2 the label \"%s\" at x 10 inside its cell (x %d; the ink's left edge)" % (text, 10 + 180 * col), 10 + 180 * col, epx(ib[0]) if ib else "", 1)
        else:
            out("R", "K4.2 the label \"%s\": its ink's left edge inside its cell (epx; another leading glyph than R11's \"2\")" % text, epx(ib[0] - 540 * col) if ib else "")
    out("E", "K4.2 the week shown holds labels that start with R11's glyph \"2\"", "yes", "yes" if twos else "no")
    if labels:
        out("C", "K4.2 the labels are grey (151,151,151)", "151 151 151", rgb(s.core(s._b(labels[0]))), 4)
        ib = s.ink(s._b(labels[0]))
        out("R", "K4.2 the first label's cap top under its cell's top rule (R11: 11 epx)", epx(ib[1] - cells_top) if ib else "")
    e1, e2 = s.b("cal_event_title:%s" % a.get("allday1")), s.b("cal_event_title:%s" % a.get("allday2"))
    if e1 and e2:
        i1, i2 = s.ink(e1), s.ink(e2)
        out("W", "K4.3 events in a cell are lines at a 19-epx pitch", 19, epx(abs((e2[1] + e2[3]) - (e1[1] + e1[3])) / 2.0), 1)
        out("R", "K4.3 the two lines' ink tops (px)", "%s / %s" % (i1[1] if i1 else "", i2[1] if i2 else ""))
        tint = tuple(int(v) for v in a.get("tint", "0 0 0").split())
        out("C", "K4.3 … tinted: the line's colour is the calendar colour's tint (the Agenda label's on this run)", rgb(tint), rgb(s.core(e1)), 4)
        out("E", "K4.3 … with no colour bar in the cell (no cal_event_bar node)", 0, len(s.prefixed("cal_event_bar:")))
    else:
        out("E", "K4.3 two events show in one cell", "yes", "no")
    mm = s.b("cal_mini_month")
    ok = bool(mm) and mm[0] >= s.w // 2 - 3 and mm[1] >= cells_top + int(3 * 120 * PX) - 6 and mm[3] <= navt - 135
    out("E", "K4.4 the mini month is in the eighth cell (right column, fourth row)", "yes", "yes" if ok else "no %s" % (mm,))


def pane(s, a):
    p = s.b("cal_pane")
    out("E", "K6.4 the ≡ pane is open (cal_pane)", "yes", "yes" if p else "no")
    if not p:
        return
    items = [n for n in s.nodes if (n.get("resource-id") or "").startswith(("cal_account:", "cal_calendar_row:"))]
    items.sort(key=lambda n: s._b(n)[1])
    for i in range(1, len(items)):
        out("W", "K6.4 the pane's rows at a 48.1-epx pitch (%s → %s)" % (items[i - 1].get("resource-id"), items[i].get("resource-id")), 48.1, epx(s._b(items[i])[1] - s._b(items[i - 1])[1]), 1)
    chrome = s.mode((p[0] + 300, p[3] - 300, p[2] - 30, p[3] - 30))
    out("C", "U10 the pane's chrome is #1F1F1F", "31 31 31", rgb(chrome), 2)
    for n in items:
        rid, b = n.get("resource-id"), s._b(n)
        named = [c for c in n.iter("node") if (c.get("text") or "").strip() and all(ord(ch) < 0xE000 or ord(ch) > 0xF8FF for ch in c.get("text"))]
        glyphs = [c for c in n.iter("node") if c.get("text") and any(0xE000 <= ord(ch) <= 0xF8FF for ch in c.get("text"))]
        if rid.startswith("cal_account:"):
            ib = s.ink((b[0], b[1], b[0] + 96, b[3]))   # the header's collapse chevron
            out("W", "K6.4 the account header %s starts at x 14 (its chevron's ink)" % rid[len("cal_account:"):], 14, epx(ib[0]) if ib else "", 1)
            out("R", "K6.4 … its chevron's glyph box and its name's ink start at (epx)", "%s / %s" % (epx(s._b(glyphs[0])[0]) if glyphs else "", epx(s.ink(s._b(named[0]))[0]) if named and s.ink(s._b(named[0])) else ""))
        else:
            tb = s.ink(s._b(named[0])) if named else None
            out("W", "K6.4 the calendar name \"%s\" at x 62 (the ink's left edge)" % (named[0].get("text") if named else ""), 62, epx(tb[0]) if tb else "", 1)
            if rid == "cal_calendar_row:%s" % a.get("tessera"):
                calc = tuple(int(v) for v in a.get("colour", "0 99 177").split())
                box = s.colour_box((b[0], b[1], b[0] + 180, b[3]), calc, 4)
                out("E", "K6.4 the row is ticked", "true", n.get("checked"))
                out("E", "K6.4 a checked box is filled with the calendar's colour (a box of it left of the name)", "yes", "yes" if box and (box[2] - box[0]) >= 30 and (box[3] - box[1]) >= 30 else "no %s" % (box,))
                out("R", "K6.4 the checkbox's filled box (epx: left top right bottom)", " ".join(epx(v) for v in box) if box else "")


def day(s, a):
    sbot, navt = bars(s, dict(a, view="Day"))
    d = s.node("cal_day:%s" % a.get("date"))
    text = d.get("text") if d is not None else ""
    out("E", "U1 the Day view's header is the week-cell label (\"%s\")" % text, "yes", "yes" if re.fullmatch(r"\d{1,2} [A-Z]{3}", text or "") else "no")
    out("E", "U1 the all-day band is there (cal_allday:%s)" % a.get("date"), "yes", "yes" if s.b("cal_allday:%s" % a.get("date")) else "no")
    g = s.b("cal_day_grid")
    out("E", "U1 the hour grid is there (cal_day_grid)", "yes", "yes" if g else "no")
    if g:
        r = s.runs("v", g[2] - 9, g[1], g[3], RULE, 4)
        gaps = [r[i][0] - r[i - 1][0] for i in range(1, len(r))]
        out("E", "U1 the grid draws hour rules", "yes", "yes" if len(r) >= 3 else "no (%d)" % len(r))
        if gaps:
            out("W", "U1 hour rows at 48 epx (the rules' pitch)", 48, epx(float(np.median(gaps))), 1)


def editor(s, a):
    sbot, navt = bars(s, dict(a, view="the editor"))
    out("E", "U3 the editor is open (cal_editor)", "yes", "yes" if s.b("cal_editor") else "no")
    border = (133, 133, 133)
    for f in a.get("fields", "title,location,start_date,start_time,end_date,end_time,repeat,reminder,calendar").split(","):
        b = s.b("cal_editor_field:%s" % f)
        if not b:
            out("E", "U3 the field %s is on the page" % f, "yes", "no")
            continue
        box = s.colour_box((b[0] - 6, b[1] - 6, b[2] + 6, b[3] + 6), border, 4)
        out("W", "U3 the %s field's drawn box is 32 epx tall (its (133,133,133) border, edge to edge)" % f, 32, epx(box[3] - box[1]) if box else "", 1)
        if box:
            x = (box[0] + box[2]) // 2
            r = s.runs("v", x, box[1] - 3, box[1] + 30, border, 4)
            out("W", "U3 … with a 2-epx border (its top edge's thickness)", 2, epx(r[0][1] - r[0][0]) if r else "", 0.34)
            out("C", "U3 … of (133,133,133)", "133 133 133", rgb(s.px(x, box[1] + 2)), 4)
            if f in ("title", "start_date"):
                out("R", "U3 the %s field: its drawn box (epx l t r b) against its dump node's height" % f, "%s / %s" % (" ".join(epx(v) for v in box), epx(b[3] - b[1])))


def event_page(s, a):
    eid = a.get("event")
    for tag in ("cal_event_page:%s" % eid, "cal_event_title:%s" % eid, "cal_event_time:%s" % eid, "cal_event_calendar", "cal_event_action:edit", "cal_event_action:delete", "cal_event_action:sync"):
        out("E", "U2 the event page carries %s" % tag, "yes", "yes" if s.b(tag) else "no")
    t = s.b("cal_event_title:%s" % eid)
    ib = s.ink(t) if t else None
    out("R", "U2 the title's left edge (epx)", epx(ib[0]) if ib else "")


VIEWS = {"agenda": agenda, "month": month, "week": week, "pane": pane, "day": day, "editor": editor, "event_page": event_page}

if __name__ == "__main__":
    view, dump, png = sys.argv[1:4]
    args = dict(kv.split("=", 1) for kv in sys.argv[4:])
    VIEWS[view](Shot(dump, png), args)
