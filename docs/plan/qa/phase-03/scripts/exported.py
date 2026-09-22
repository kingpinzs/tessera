#!/usr/bin/env python3
"""E5: the components the APK really exports, against the phase doc's allow-list.

    exported.py <dumpsys-package.txt> <allowlist.txt>

Exits 0 only when the two sets are EQUAL. A component on the device that is not on the list fails, and
so does one on the list that the device does not have — the second half matters because a list that
drifts ahead of the build would quietly stop testing anything.

`dumpsys package` prints each component under "Activity Resolver Table" / "Service Resolver Table" /
"Receiver Table" / "Provider Table" and marks non-exported ones elsewhere, so the exported set is read
from the resolver tables (which only carry components reachable from outside) plus any provider whose
own line says exported=true.
"""
import re
import sys


def parse_device(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    found = set()

    # Resolver tables list only components other apps can resolve, one per "<pkg>/<class> filter" line.
    for m in re.finditer(r"^\s+([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+) filter", text, re.M):
        pkg, cls = m.group(1), m.group(2)
        if pkg != "app.tileshell":
            continue
        found.add(pkg + (cls if cls.startswith(".") is False else cls))

    # Providers are listed with their authority rather than in a resolver table.
    for m in re.finditer(r"^\s+app\.tileshell/([A-Za-z0-9_.$]+):\s*$", text, re.M):
        found.add(m.group(1) if m.group(1).startswith("app.tileshell") else "app.tileshell" + m.group(1))

    # Normalise "app.tileshell.cortana.CortanaService" vs "app.tileshell/.cortana.CortanaService".
    return {c.replace("app.tileshell/.", "app.tileshell.").replace("/", "") for c in found}


def parse_allowlist(path):
    wanted = {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        wanted[parts[0].strip()] = (parts[1].strip(), parts[2].strip())
    return wanted


def main():
    device = parse_device(sys.argv[1])
    allow = parse_allowlist(sys.argv[2])

    extra = sorted(device - set(allow))
    missing = sorted(set(allow) - device)

    print(f"{len(device)} exported on the device, {len(allow)} on the allow-list")
    for c in sorted(device):
        guard, why = allow.get(c, ("?", "NOT ON THE ALLOW-LIST"))
        print(f"  {c}\n      {guard}\n      {why}")
    if extra:
        print("\nEXPORTED BUT NOT ALLOWED:")
        for c in extra:
            print(f"  {c}")
    if missing:
        print("\nON THE LIST BUT NOT EXPORTED (the list has drifted ahead of the build):")
        for c in missing:
            print(f"  {c}")
    sys.exit(1 if (extra or missing) else 0)


if __name__ == "__main__":
    main()
