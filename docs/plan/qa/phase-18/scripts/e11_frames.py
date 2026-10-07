#!/usr/bin/env python3
"""Phase 18 E11: what a screenrecord's frames show, for the three forms the row corroborates (never the clock — the
`[motion]` lines are, and they are never read while the recorder runs).

  e11_frames.py folder    <mp4> <workdir> list=l,t,r,b name=l,t,r,b detail=l,t,r,b icon=l,t,r,b bar=l,t,r,b
  e11_frames.py paneclose <mp4> <workdir> probe=x,y edge=x,y list=l,t,r,b
  e11_frames.py paneopen  <mp4> <workdir> label=l,t,r,b row=y x1=<pane's right edge at rest, px>

Boxes are in device px, from the row's own dumps at rest. Every frame is decoded (-vsync 0); its time is the packet's
pts. Output: key=value lines the driver asserts on, and a per-frame table in <workdir>/<mode>-frames.tsv.

Phase 05's frame-spacing rule: the source frames are at most 18.2 ms apart DURING THE MOTION (the recorder writes a
frame only when the screen changes, so a still screen has no frames and no spacing) — `spacing_ok` says so and the
driver retakes otherwise."""
import glob, os, subprocess, sys
from PIL import Image

INK = 16          # a pixel is ink when a channel is more than this above black (a 0.17-alpha grey reads about 28)


def box(arg):
    return [int(float(v)) for v in arg.split("=", 1)[1].split(",")]


def args():
    return {a.split("=", 1)[0]: a for a in sys.argv[4:]}


def frames(mp4, work):
    out = os.path.join(work, "frames")
    os.makedirs(out, exist_ok=True)
    for f in glob.glob(out + "/*.png"): os.remove(f)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", mp4, "-vsync", "0", out + "/%04d.png"], check=True)
    pts = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "packet=pts_time", "-of", "csv=p=0", mp4],
                         capture_output=True, text=True).stdout.split()
    pts = sorted(float(p.strip(",")) * 1000 for p in pts if p.strip(","))
    files = sorted(glob.glob(out + "/*.png"))
    n = min(len(pts), len(files))
    return [(pts[i], files[i]) for i in range(n)]


def ink_rows(im, b, step=1, level=INK):
    """(count, top, bottom, left) of the ink in a box; top/bottom/left None when there is none."""
    l, t, r, bt = b
    px = im.load()
    n, top, bot, left = 0, None, None, None
    for y in range(t, bt, step):
        for x in range(l, r, step):
            p = px[x, y]
            if max(p) > level:
                n += 1
                if top is None: top = y
                bot = y
                if left is None or x < left: left = x
    return n, top, bot, left


def gaps(times):
    return [b - a for a, b in zip(times, times[1:])]


def folder():
    a = args()
    lst, name, detail, icon, bar, menu = box(a["list"]), box(a["name"]), box(a["detail"]), box(a["icon"]), box(a["bar"]), box(a["menu"])
    fr = frames(sys.argv[2], sys.argv[3])
    # The name's band, tall enough to hold it 12 epx below rest; the same for the detail and the icon.
    nb = [name[0], name[1] - 6, min(name[2] + 240, lst[2]), name[3] + 30]
    # The detail's band starts 8 px under its box's top: on the entrance's first frame the NAME sits 21.6 px low and its
    # descenders would reach a band that began at the box's top.
    db = [detail[0], detail[1] + 8, min(detail[2] + 240, lst[2]), detail[3] + 30]
    ib = [icon[0], icon[1] - 4, icon[2], icon[3] + 30]
    rows = []
    bar0 = menu0 = None

    def glyphs(im, b):
        """The box of everything in a bar that is not its fill (31): where its glyphs are, whatever their colour — and
        the brightest channel among them, the right-most 50 epx left out (••• stays live while the other buttons dim)."""
        px = im.load()
        xs, ys, top = [], [], 0
        for y in range(b[1] + 2, b[3] - 2):
            for x in range(b[0], b[2]):
                m = max(px[x, y])
                if m > 70:
                    xs.append(x); ys.append(y)
                    if m > top and x < b[2] - 150: top = m
        return ((min(xs), min(ys), max(xs), max(ys)) if xs else None), top

    for t, f in fr:
        im = Image.open(f).convert("RGB")
        bb, bright = glyphs(im, bar)
        mb, _ = glyphs(im, menu)
        if bar0 is None: bar0, menu0 = bb, mb
        same = bb is not None and bar0 is not None and all(abs(p - q) <= 1 for p, q in zip(bb, bar0))
        msame = mb is not None and menu0 is not None and all(abs(p - q) <= 1 for p, q in zip(mb, menu0))
        # The bars' own edges: the app bar's top row is fill (about 31) and the row above it is the page or a row's black.
        edge = max(im.getpixel((6, bar[1] + 1))) > 20 and max(im.getpixel((6, bar[3] - 2))) > 20
        ln, _, _, _ = ink_rows(im, lst, 3)
        nn, ntop, _, _ = ink_rows(im, nb)
        dn, _, _, _ = ink_rows(im, db)
        inn, _, _, _ = ink_rows(im, ib, 2)
        rows.append([t, ln, nn, ntop, dn, inn, "same" if same and msame and edge else "MOVED", bright])
    with open(os.path.join(sys.argv[3], "folder-frames.tsv"), "w") as o:
        o.write("ms\tlist_ink\tname_ink\tname_top\tdetail_ink\ticon_ink\tbars\tbar_glyph_brightest\n")
        for r in rows: o.write("\t".join("%.1f" % r[0] if i == 0 else str(r[i]) for i in range(8)) + "\n")
    print("frames=%d" % len(rows))
    # The cut: the first frame with an empty list after one that had rows.
    cut = next((i for i in range(1, len(rows)) if rows[i][1] == 0 and any(r[1] > 0 for r in rows[:i])), None)
    print("empty_frame=%s" % ("none" if cut is None else cut))
    if cut is None: return
    print("empty_frame_ms=%.1f" % rows[cut][0])
    print("frames_with_rows_before_the_cut=%d" % sum(1 for r in rows[:cut] if r[1] > 0))
    # Unmoved: the app bar's glyphs and the ≡ sit in the same boxes (± 1 px) as on the first frame, and the app bar's
    # own fill is where it was — in the empty frame and in every frame after it.
    print("bars_at_empty=%s" % rows[cut][6])
    print("bars_moved_frames_after=%d" % sum(1 for r in rows[cut:] if r[6] != "same"))
    # What does change in the bar (recorded, not a movement): its glyphs' brightness.
    print("bar_glyph_brightest_first=%d" % rows[0][7])
    print("bar_glyph_brightest_at_empty=%d" % rows[cut][7])
    print("bar_dim_frames=%d" % sum(1 for r in rows[cut:] if r[7] < 200))
    after = rows[cut:]
    first_name = next((i for i, r in enumerate(after) if r[2] > 0), None)
    if first_name is None:
        print("first_name=none"); return
    rest = after[-1][3]
    print("name_rest_top_px=%s" % rest)
    print("name_first_top_px=%s" % after[first_name][3])
    print("first_offset_epx=%.2f" % ((after[first_name][3] - rest) / 3.0))
    print("offsets_epx=%s" % ",".join("%.1f" % ((r[3] - rest) / 3.0) for r in after[first_name:] if r[3] is not None)[:400])
    first_detail = next((i for i, r in enumerate(after) if r[4] > 0), None)
    first_icon = next((i for i, r in enumerate(after) if r[5] > 0), None)
    t0 = after[first_name][0]
    print("name_first_ms=0")
    print("detail_first_ms=%s" % ("none" if first_detail is None else "%.1f" % (after[first_detail][0] - t0)))
    print("icon_first_ms=%s" % ("none" if first_icon is None else "%.1f" % (after[first_icon][0] - t0)))
    print("order=%s" % ("names<details<icons" if first_detail is not None and first_icon is not None and first_name < first_detail < first_icon else
                         "name@%s detail@%s icon@%s" % (first_name, first_detail, first_icon)))
    print("load_gap_ms=%.1f" % (after[first_name][0] - rows[cut][0]))
    # The motion: from the first entrance frame to the first frame at rest.
    settle = next(i for i in range(first_name, len(after)) if after[i][3] == rest and all(r[3] == rest for r in after[i:]))
    times = [r[0] for r in after[first_name:settle + 1]]
    g = gaps(times)
    print("entrance_ms=%.1f" % (times[-1] - times[0]))
    print("motion_frames=%d" % len(times))
    print("max_gap_ms=%.1f" % (max(g) if g else 0))
    print("spacing_ok=%s" % ("yes" if g and max(g) <= 18.2 else "no"))


def paneclose():
    a = args()
    probe, edge, lst = box(a["probe"]), box(a["edge"]), box(a["list"])
    fr = frames(sys.argv[2], sys.argv[3])
    rows = []
    for t, f in fr:
        im = Image.open(f).convert("RGB")
        p, e = im.getpixel(tuple(probe)), im.getpixel(tuple(edge))
        ln, _, _, _ = ink_rows(im, lst, 3)
        rows.append([t, p[2], e[2], ln])
    with open(os.path.join(sys.argv[3], "paneclose-frames.tsv"), "w") as o:
        o.write("ms\tprobe_blue\tedge_blue\tlist_ink\n")
        for r in rows: o.write("%.1f\t%d\t%d\t%d\n" % tuple(r))
    print("frames=%d" % len(rows))
    print("pane_at_start=%s" % ("yes" if rows and rows[0][1] > 60 and rows[0][2] > 60 else "no"))
    gone = next((i for i in range(1, len(rows)) if rows[i][1] < 40 and rows[i][2] < 40), None)
    print("gone_frame=%s" % ("none" if gone is None else gone))
    if gone is None: return
    # No frame between "whole pane" and "no pane": the frame before still has both the probe and the pane's edge.
    print("frame_before_has_whole_pane=%s" % ("yes" if rows[gone - 1][1] > 60 and rows[gone - 1][2] > 60 else "no"))
    print("list_ink_in_gone_frame=%d" % rows[gone][3])
    later = next((i for i in range(gone + 1, len(rows)) if rows[i][3] > 0), None)
    print("rows_enter_ms_after=%s" % ("none" if later is None else "%.1f" % (rows[later][0] - rows[gone][0])))
    print("pane_back_later=%s" % ("yes" if any(r[1] > 60 for r in rows[gone:]) else "no"))


def paneopen():
    a = args()
    label, rowy, x1 = box(a["label"]), int(a["row"].split("=")[1]), int(a["x1"].split("=")[1])
    fr = frames(sys.argv[2], sys.argv[3])
    rows = []
    for t, f in fr:
        im = Image.open(f).convert("RGB")
        px = im.load()
        # The reveal's edge on the current row's centre line: the right-most pixel that is the row's fill (blue-led).
        e = None
        for x in range(x1 + 20, 0, -1):
            p = px[x, rowy]
            if p[2] > 60 and p[2] - p[0] > 40:
                e = x; break
        # The label's ink (white on the pane): its left-most pixel.
        left = None
        for x in range(label[0] - 30, label[2] + 2):
            if any(min(px[x, y]) > 150 for y in range(label[1], label[3])):
                left = x; break
        rows.append([t, e, left])
    with open(os.path.join(sys.argv[3], "paneopen-frames.tsv"), "w") as o:
        o.write("ms\tedge_x\tlabel_left\n")
        for r in rows: o.write("%.1f\t%s\t%s\n" % tuple(r))
    print("frames=%d" % len(rows))
    moving = [i for i, r in enumerate(rows) if r[1] is not None]
    if not moving:
        print("first_edge=none"); return
    first = moving[0]
    full = next((i for i in moving if rows[i][1] >= x1 - 3), None)
    print("first_edge_px=%d" % rows[first][1])
    print("full_frame=%s" % ("none" if full is None else full))
    if full is None: return
    span = rows[first:full + 1]
    edges = [r[1] for r in span]
    print("edges_px=%s" % ",".join(str(e) for e in edges))
    print("edge_grows=%s" % ("yes" if len(edges) >= 3 and all(b >= a for a, b in zip(edges, edges[1:])) and edges[0] < edges[-1] else "no"))
    print("partial_frames=%d" % sum(1 for e in edges if e < x1 - 3))
    lefts = [r[2] for r in span if r[2] is not None]
    final = rows[-1][2]
    print("label_final_left_px=%s" % final)
    print("label_lefts_px=%s" % ",".join(str(v) for v in lefts))
    print("label_seen_while_partial=%d" % sum(1 for r in span if r[2] is not None and r[1] < x1 - 3))
    print("label_x_unchanged=%s" % ("yes" if lefts and final is not None and all(abs(v - final) <= 1 for v in lefts) else "no"))
    times = [r[0] for r in span]
    g = gaps(times)
    print("open_ms=%.1f" % (times[-1] - times[0]))
    print("max_gap_ms=%.1f" % (max(g) if g else 0))
    print("spacing_ok=%s" % ("yes" if g and max(g) <= 18.2 else "no"))


if __name__ == "__main__":
    {"folder": folder, "paneclose": paneclose, "paneopen": paneopen}[sys.argv[1]]()
