#!/usr/bin/env python3
"""e13_state.py <Start theme page dump.xml ...>: the Settings > Start values the dump shows (several dumps of one scrolled page merge).
Radio rows and accent swatches carry checked="true" (Compose reports selection as checked), toggles carry checked, the slider its value in content-desc."""
import re, sys
state = {}
for f in sys.argv[1:]:
    s = open(f).read()
    for m in re.finditer(r'<node [^>]*>', s):
        n = m.group(0)
        rid = re.search(r'resource-id="([^"]*)"', n).group(1)
        sel = re.search(r'selected="([a-z]+)"', n).group(1)
        chk = re.search(r'checked="([a-z]+)"', n).group(1)
        desc = re.search(r'content-desc="([^"]*)"', n).group(1)
        if rid.startswith(("theme_mode_", "press_")) and chk == "true": state["press" if rid.startswith("press_") else "mode"] = rid
        elif rid.startswith("accent:") and chk == "true": state["accent"] = rid
        elif rid in ("theme_show_more_tiles", "theme_show_profiles"): state[rid] = chk
        elif rid == "theme_transparency": state[rid] = desc
        elif rid == "theme_background_remove": state["background"] = "set"
state.setdefault("background", "none (no Remove picture row)")
for k in sorted(state): print(f"{k}: {state[k]}")
