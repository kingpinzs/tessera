#!/usr/bin/env python3
"""Phase 05 E3 motion: the keyboard's show / hide slide and the press popup's timing, from frames.

  motion.py slide FRAMES_DIR X REST_TOP GONE_Y BLOCK_H FPS
      Per frame, the panel's top edge: the first pixel darker than luminance 200 scanning DOWN column X
      from 0.4 of the screen height (the fixture's page above the keyboard is light, ≥ 240). A contrast
      threshold rather than "dark", because Android's IME animation also FADES the window (E3M run 1:
      the hide's translucent panel was lost by a < 70 threshold). The panel's opacity is read from the
      strip's empty right end, 12 px under that edge, against the page colour and the panel colour
      (22,27,21). The panel is "visible" once its edge is above GONE_Y (the nav bar's top), "at rest"
      within 2 px of REST_TOP. Prints "frame_ms offset_px fraction alpha" per frame, then
      first_visible_ms, rest_ms, duration_ms, t90_ms (when 90 % of the travel was done).

  motion.py popup FRAMES_DIR TX TY PX0 PY0 PX1 PY1 FPS
      Frame of the first touch indicator at (TX, TY) (show_touches draws a translucent grey disc, which
      moves the pixel away from the key's own colour) and the first frame with accent pixels filling the
      popup box. Prints touch_ms, popup_ms, delay_ms, popup_fill (share of the box that is accent).

A pixel's colour is read from the frames ffmpeg resampled to FPS; the real capture rate is checked
separately by the row (ffprobe), and a capture below 55 fps is rejected there (RV11).
"""
import glob
import os
import sys

from PIL import Image


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def frames(d):
    return sorted(glob.glob(os.path.join(d, "f_*.png")))


EDGE = 200
PANEL_LUM = lum((22, 27, 21))


def edges(fs, x):
    """Per frame: (panel top edge, panel opacity 0..1)."""
    out = []
    page = None
    for f in fs:
        with Image.open(f) as im:
            im = im.convert("RGB")
            w, h = im.size
            px = im.load()
            if page is None:
                page = lum(px[x, int(h * 0.4)])
            top = h
            for y in range(int(h * 0.4), h):
                if lum(px[x, y]) < EDGE:
                    top = y
                    break
            alpha = 0.0
            if top < h - 12:
                alpha = max(0.0, min(1.0, (page - lum(px[x, top + 12])) / (page - PANEL_LUM)))
            out.append((top, alpha))
    return out


def slide(d, x, rest_top, gone_y, block_h, fps):
    fs = frames(d)
    ea = edges(fs, x)
    tops = [t for t, _ in ea]
    visible = [i for i, t in enumerate(tops) if t < gone_y - 2]
    if not visible:
        print("the panel never appeared")
        sys.exit(2)
    frame_ms = 1000.0 / fps
    # The motion is the stretch between the panel's edge leaving (or reaching) the nav bar and resting.
    moving = [i for i, t in enumerate(tops) if abs(t - rest_top) > 2 and t < gone_y - 2]
    at_rest = [i for i, t in enumerate(tops) if abs(t - rest_top) <= 2]
    for i, (t, a) in enumerate(ea):
        print("%.1f %d %.3f %.2f" % (i * frame_ms, t - rest_top, (t - rest_top) / block_h, a))
    first = visible[0]
    rest = next((i for i in at_rest if i >= first), None)
    if rest is None:
        print("the panel never came to rest")
        sys.exit(2)
    travel = tops[first] - rest_top
    t90 = next((i for i in range(first, rest + 1) if (tops[i] - rest_top) <= 0.1 * travel), rest)
    print("first_visible_ms=%.1f" % (first * frame_ms))
    print("rest_ms=%.1f" % (rest * frame_ms))
    print("duration_ms=%.1f" % ((rest - first) * frame_ms))
    print("t90_ms=%.1f" % ((t90 - first) * frame_ms))
    print("moving_frames=%d" % len(moving))
    print("alpha_first_visible=%.2f" % ea[first][1])
    print("motion_window_s=%.4f %.4f" % (first / fps, rest / fps))


def hide(d, x, rest_top, gone_y, fps):
    fs = frames(d)
    ea = edges(fs, x)
    tops = [t for t, _ in ea]
    frame_ms = 1000.0 / fps
    for i, (t, a) in enumerate(ea):
        print("%.1f %d %.2f" % (i * frame_ms, t - rest_top, a))
    start = next((i for i, t in enumerate(tops) if t > rest_top + 2), None)
    gone = next((i for i in range(start or 0, len(tops)) if tops[i] >= gone_y - 2), None) if start is not None else None
    if start is None or gone is None:
        print("no hide motion found")
        sys.exit(2)
    print("hide_start_ms=%.1f" % (start * frame_ms))
    print("hide_ms=%.1f" % ((gone - start) * frame_ms))
    print("alpha_at_start=%.2f alpha_mid=%.2f" % (ea[start][1], ea[(start + gone) // 2][1]))
    print("motion_window_s=%.4f %.4f" % (start / fps, gone / fps))


def popup(d, tx, ty, box, fps):
    fs = frames(d)
    base = None
    touch = pop = None
    x0, y0, x1, y1 = box
    for i, f in enumerate(fs):
        with Image.open(f) as im:
            im = im.convert("RGB")
            px = im.load()
            p = px[tx, ty]
            if base is None:
                base = p
            if touch is None and sum(abs(a - b) for a, b in zip(p, base)) > 30:
                touch = i
            n = acc = 0
            for y in range(y0, y1, 3):
                for x in range(x0, x1, 3):
                    q = px[x, y]
                    n += 1
                    if q[2] > 150 and q[2] - q[0] > 60:
                        acc += 1
            if pop is None and acc / n > 0.5:
                pop = i
                fill = acc / n
    frame_ms = 1000.0 / fps
    if touch is None or pop is None:
        print("touch=%s popup=%s: not both seen" % (touch, pop))
        sys.exit(2)
    print("touch_ms=%.1f" % (touch * frame_ms))
    print("popup_ms=%.1f" % (pop * frame_ms))
    print("delay_ms=%.1f" % ((pop - touch) * frame_ms))
    print("popup_fill=%.2f" % fill)


if __name__ == "__main__":
    cmd, a = sys.argv[1], sys.argv[2:]
    if cmd == "slide":
        slide(a[0], int(a[1]), int(a[2]), int(a[3]), float(a[4]), float(a[5]))
    elif cmd == "hide":
        hide(a[0], int(a[1]), int(a[2]), int(a[3]), float(a[4]))
    elif cmd == "popup":
        popup(a[0], int(a[1]), int(a[2]), tuple(map(int, a[3:7])), float(a[7]))
    else:
        sys.exit("unknown " + cmd)
