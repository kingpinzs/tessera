#!/usr/bin/env python3
"""E5: the components the APK really exports, against the phase doc's allow-list.

    exported.py <app-debug.apk> <allowlist.txt>

Exits 0 only when the two sets are EQUAL. A component the APK exports that is not on the list fails,
and so does one on the list the APK does not export — the second half matters because a list that
drifts ahead of the build would quietly stop testing anything.

The APK's own merged manifest is the source, read with aapt2: `dumpsys package` prints resolver tables
that are easy to misparse, and the manifest is what actually decides the surface. A component with an
intent filter and no explicit android:exported is exported by the platform's default, so that case is
resolved here rather than assumed.
"""
import glob
import os
import re
import subprocess
import sys

AAPT2 = sorted(glob.glob(os.path.expanduser("~/Android/Sdk/build-tools/*/aapt2")))[-1]
COMPONENTS = ("activity", "activity-alias", "service", "receiver", "provider")

ATTR = re.compile(
    r'A: (?:http://schemas\.android\.com/apk/res/android:)?([A-Za-z_]+)'
    r'(?:\(0x[0-9a-f]+\))?='
    r'(?:\(type 0x[0-9a-f]+\))?'
    r'(?:"([^"]*)"|(\S+))'
)


def qualify(package, name):
    if name.startswith("."):
        return package + name
    if "." not in name:
        return package + "." + name
    return name


def exported_components(apk):
    tree = subprocess.run(
        [AAPT2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
        capture_output=True, text=True,
    ).stdout

    package = ""
    found = {}
    current = None          # [kind, indent, attrs, has_filter]
    # Attributes belong to the INNERMOST element. Without this, the android:name of an <action> or a
    # <category> inside an intent-filter overwrote the component's own name and every component came
    # out called "android.intent.category.LAUNCHER".
    on_component = False

    def flush():
        if not current:
            return
        kind, _, attrs, has_filter = current
        name = attrs.get("name")
        if not name:
            return
        exported = attrs.get("exported")
        if exported in ("true", "0xffffffff", "-1"):
            is_exported = True
        elif exported in ("false", "0x0", "0"):
            is_exported = False
        else:
            is_exported = has_filter
        if is_exported:
            found[qualify(package, name)] = (kind, attrs.get("permission", ""))

    for line in tree.splitlines():
        indent = len(line) - len(line.rstrip("\n").lstrip())
        text = line.strip()
        if text.startswith("E: "):
            element = text[3:].split(" ", 1)[0]
            if element in COMPONENTS:
                flush()
                current = [element, indent, {}, False]
                on_component = True
            else:
                on_component = False
                if element == "intent-filter" and current and indent > current[1]:
                    current[3] = True
                elif current and indent <= current[1]:
                    flush()
                    current = None
        elif text.startswith("A: "):
            m = ATTR.match(text)
            if not m:
                continue
            key = m.group(1)
            value = m.group(2) if m.group(2) is not None else m.group(3)
            if current and on_component:
                current[2][key] = value
            elif key == "package" and not package:
                package = value
    flush()
    return package, found


def parse_allowlist(path):
    wanted = {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) >= 3:
            wanted[parts[0].strip()] = (parts[1].strip(), parts[2].strip())
    return wanted


def main():
    apk, allowlist = sys.argv[1], sys.argv[2]
    package, device = exported_components(apk)
    allow = parse_allowlist(allowlist)

    extra = sorted(set(device) - set(allow))
    missing = sorted(set(allow) - set(device))

    print(f"{len(device)} exported in {package or apk}, {len(allow)} on the allow-list")
    for name in sorted(device):
        kind, permission = device[name]
        guard, why = allow.get(name, ("?", "NOT ON THE ALLOW-LIST"))
        print(f"  {kind:14s} {name}")
        print(f"                 manifest permission: {permission or '(none)'}")
        print(f"                 allow-list: {guard} - {why}")
    if extra:
        print("\nEXPORTED BUT NOT ALLOWED:")
        for name in extra:
            print(f"  {name}")
    if missing:
        print("\nON THE LIST BUT NOT EXPORTED (the list has drifted ahead of the build):")
        for name in missing:
            print(f"  {name}")
    sys.exit(1 if (extra or missing) else 0)


if __name__ == "__main__":
    main()
