#!/usr/bin/env python3
"""E3's hand on Android's own pages: given a uiautomator dump taken after a wizard step's button, decide ONE action.

    screen_act.py <dump.xml> <step key> <state-file>

Prints one line: `tap <x> <y> <why>`, `back <why>`, `wizard <step key shown>` (the wizard is in front again: the walk
goes on), or `unknown <package> <texts>`. The state file remembers what was already done on this step (a switch turned
on, a radio chosen), so the walker presses Back once the grant is made rather than tapping the same control twice.

It drives the REAL pages: the permission controller's dialogs by their own resource-ids, Settings' toggles by the
switch widget beside the row, the input-method picker and the assistant list by the shell's name. Nothing is granted from
adb on this path (E3; r3 V5: no pm grant shortcut).
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

APP = "Tessera"
PC = "com.android.permissioncontroller:id/"


def centre(n):
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds", "0 0 0 0")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    dump, step, state_file = sys.argv[1], sys.argv[2], sys.argv[3]
    state = json.load(open(state_file)) if os.path.exists(state_file) else {}
    try:
        root = ET.parse(dump).getroot()
    except Exception:
        print("unknown (dump unreadable)")
        return
    nodes = list(root.iter("node"))
    rid = {n.get("resource-id", ""): n for n in nodes if n.get("resource-id")}
    texts = [n.get("text", "") for n in nodes if n.get("text")]
    pkgs = {n.get("package", "") for n in nodes}

    def save():
        json.dump(state, open(state_file, "w"))

    def tap(n, why):
        x, y = centre(n)
        print(f"tap {x} {y} {why}")

    def by_text(pattern, pkg=None, clickable_parent=False):
        for n in nodes:
            if pkg and not n.get("package", "").startswith(pkg):
                continue
            if re.search(pattern, n.get("text", ""), re.I):
                return n
        return None

    # The wizard is back in front.
    for k in rid:
        if k.startswith("wizard_step:"):
            print(f"wizard {k[len('wizard_step:'):]}")
            return
    if "wizard_presets" in rid:
        print("wizard presets")
        return

    # ---- the permission controller's runtime dialogs
    for b, why in [
        ("permission_allow_all_button", "Allow all (photos)"),
        ("permission_location_accuracy_radio_fine", None),
        ("permission_allow_foreground_only_button", "While using the app"),
        ("permission_allow_button", "Allow"),
    ]:
        n = rid.get(PC + b)
        if n is None:
            continue
        if b == "permission_location_accuracy_radio_fine":
            # the precise / approximate chooser: choose Precise once, then the foreground button below
            if not state.get("fine_chosen") and n.get("checked") != "true":
                state["fine_chosen"] = True
                save()
                tap(n, "choose Precise")
                return
            continue
        tap(n, why)
        return
    # The precise-location upgrade dialog ("Change to precise location?")
    n = by_text(r"^change to precise location", "com.android.permissioncontroller")
    if n is not None:
        tap(n, "Change to precise location")
        return
    # The app's location page (Allow all the time), reached by the background request.
    n = rid.get(PC + "allow_always_radio_button")
    if n is not None:
        if n.get("checked") != "true":
            tap(n, "Allow all the time")
            return
        print("back location page: all the time is chosen")
        return

    # ---- the digital assistant: the permission controller's default-app list (None / the shell), then its OK
    if step == "tess:assistant" and any(p.endswith("permissioncontroller") for p in pkgs):
        ok = by_text(r"^ok$", "com.android.permissioncontroller")
        if ok is not None and state.get("assistant_chosen") and not state.get("assistant_ok"):
            state["assistant_ok"] = True
            save()
            tap(ok, "OK (the assistant's access warning)")
            return
        n = by_text(r"^%s$" % APP, "com.android.permissioncontroller")
        if n is not None and not state.get("assistant_chosen"):
            state["assistant_chosen"] = True
            save()
            tap(n, "choose the shell in the default-assistant list")
            return
        if state.get("assistant_chosen"):
            print("back assistant chosen")
            return

    # ---- the input-method picker (a system dialog)
    if by_text(r"^choose input method$") is not None or (by_text(r"^%s keyboard$" % APP) is not None and "android" in pkgs and not any(p.startswith("com.android.settings") for p in pkgs)):
        # the selectable row is the subtype under the keyboard's name
        names = [n for n in nodes if re.search(r"^%s keyboard$" % APP, n.get("text", ""))]
        if names:
            y0 = centre(names[0])[1]
            below = [n for n in nodes if n.get("text") and centre(n)[1] > y0 + 20 and n.get("package") == "android"]
            if below:
                tap(below[0], "the shell keyboard's subtype")
                return

    # ---- Settings' pages
    if any(p.startswith("com.android.settings") for p in pkgs):
        # a confirmation dialog on top (notification access, the IME warning, the assistant change)
        for pat in [r"^allow$", r"^ok$", r"^agree$"]:
            # (an ElementTree node with no children is falsy, so never `a or b` on nodes)
            n = by_text(pat, "com.android.settings")
            if n is None:
                n = by_text(pat, "android")
            if n is not None and state.get("toggled"):
                state["confirmed"] = True
                save()
                tap(n, f"confirm ({n.get('text')})")
                return
        # The assistant: the Assist page's "Default digital assistant app" row, then the shell in the list.
        if step == "tess:assistant":
            n = by_text(r"^%s$" % APP, "com.android.settings")
            if n is not None and not state.get("assistant_chosen"):
                state["assistant_chosen"] = True
                state["toggled"] = True
                save()
                tap(n, "choose the shell as the assistant")
                return
            n = by_text(r"digital assistant app", "com.android.settings")
            if n is not None and not state.get("assistant_chosen"):
                tap(n, "Default digital assistant app")
                return
            if state.get("assistant_chosen"):
                print("back assistant chosen")
                return
        # The input-method list: the switch beside the shell keyboard's name.
        if step == "setup:keyboard_enabled":
            names = [n for n in nodes if re.search(r"^%s keyboard$" % APP, n.get("text", ""))]
            if names and not state.get("toggled"):
                y = centre(names[0])[1]
                switches = [n for n in nodes if n.get("class", "").endswith("Switch") and abs(centre(n)[1] - y) < 120]
                target = switches[0] if switches else names[0]
                if target.get("checked") != "true":
                    state["toggled"] = True
                    save()
                    tap(target, "the shell keyboard's switch")
                    return
                state["toggled"] = True
                save()
            if state.get("toggled"):
                print("back keyboard enabled")
                return
        # A page with one switch for this app (notification access, usage access, full-screen intent, overlay).
        # (Settings' newer SPA pages draw the toggle as a checkable, clickable android.view.View, not a Switch)
        switches = [n for n in nodes if n.get("class", "").endswith("Switch") or n.get("resource-id") == "android:id/switch_widget"
                    or (n.get("checkable") == "true" and n.get("clickable") == "true" and n.get("package", "").startswith("com.android.settings"))]
        if switches and not state.get("toggled"):
            s = switches[0]
            state["toggled"] = True
            save()
            if s.get("checked") != "true":
                tap(s, "the page's switch")
                return
        if state.get("toggled"):
            print("back switch set")
            return
        # A page that lists apps (usage access / special access without the package filter): open the shell's entry.
        n = by_text(r"^%s$" % APP, "com.android.settings")
        if n is not None:
            tap(n, "the shell's entry")
            return

    print("unknown " + ",".join(sorted(p for p in pkgs if p)) + " | " + " / ".join(texts[:12]))


if __name__ == "__main__":
    main()
