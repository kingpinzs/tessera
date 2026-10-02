#!/usr/bin/env python3
"""Phase 16 QA, the Calendar rows: one leg's frames, read from `dumpsys gfxinfo <pkg> framestats`.

    cal_frames.py longest <framestats.txt>
        -> one line, tab-separated:
           longest_ms  input  animation  traversal  draw  sync  issue_commands  swap  frame_index  frames  late
        The longest frame is the one with the largest FrameCompleted - IntendedVsync; its stage split is, in ms:
        input (HandleInputStart..AnimationStart), animation (..PerformTraversalsStart), traversal (..DrawStart),
        draw (..SyncQueued) — the UI thread's stages — then sync (SyncStart..IssueDrawCommandsStart), issue_commands
        (..SwapBuffers) and swap (..FrameCompleted), the render thread's. `late` counts frames completed after their
        FrameDeadline. Frames are de-duplicated by IntendedVsync (a window's block can be printed twice).
    cal_frames.py table <framestats.txt>
        -> every frame, one line each: index, LATE or -, total and the same seven stages.
"""
import sys


def frames(path):
    rows, hdr, inb = {}, None, False
    for l in open(path, errors="replace").read().splitlines():
        if l.startswith("---PROFILEDATA---"):
            inb = not inb
            continue
        if not inb:
            continue
        if l.startswith("Flags"):
            hdr = l.rstrip(",").split(",")
            continue
        v = l.rstrip(",").split(",")
        if hdr and len(v) >= len(hdr):
            try:
                r = dict(zip(hdr, (int(x) for x in v[:len(hdr)])))
            except ValueError:
                continue
            if r["FrameCompleted"] <= r["IntendedVsync"]:      # a frame still in flight (or skipped) when the dump was taken
                continue
            rows[r["IntendedVsync"]] = r
    return [rows[k] for k in sorted(rows)]


def split(r):
    ms = lambda a, b: (r[b] - r[a]) / 1e6
    return (ms("IntendedVsync", "FrameCompleted"), ms("HandleInputStart", "AnimationStart"), ms("AnimationStart", "PerformTraversalsStart"),
            ms("PerformTraversalsStart", "DrawStart"), ms("DrawStart", "SyncQueued"), ms("SyncStart", "IssueDrawCommandsStart"),
            ms("IssueDrawCommandsStart", "SwapBuffers"), ms("SwapBuffers", "FrameCompleted"))


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    fr = frames(path)
    if cmd == "longest":
        if not fr:
            print("\t".join(["0.0"] * 8 + ["0", "0", "0"]))
            return
        best = max(range(len(fr)), key=lambda i: fr[i]["FrameCompleted"] - fr[i]["IntendedVsync"])
        late = sum(1 for r in fr if r["FrameCompleted"] > r["FrameDeadline"])
        print("\t".join(["%.1f" % x for x in split(fr[best])] + [str(best + 1), str(len(fr)), str(late)]))
    elif cmd == "table":
        for i, r in enumerate(fr, 1):
            print("\t".join([str(i), "LATE" if r["FrameCompleted"] > r["FrameDeadline"] else "-"] + ["%.1f" % x for x in split(r)]))
    else:
        sys.exit("cal_frames.py: longest|table <framestats.txt>")


if __name__ == "__main__":
    main()
