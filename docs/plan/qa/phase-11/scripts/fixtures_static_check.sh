#!/usr/bin/env bash
# Phase 11 build task 7: a static check of the three fixture APKs, no device. Builds tileclient-a, -b and -b2, prints
# what the SDK's aapt2 reads out of each APK (the compiled manifest's application element, the resource table,
# tileclient-a's xml/shortcuts and both shortcut icons), then checks from those same dumps what the rows depend on:
#   - tileclient-a's LAUNCHER activity VerbActivity carries the android.app.shortcuts meta-data -> xml/shortcuts (T11-13)
#   - tileclient-a declares ShortcutActivity; tileclient-b declares the exported ShortcutVerbReceiver (T11-15);
#     tileclient-b2 declares neither, and only tileclient-a carries shortcuts meta-data
#   - id/shortcut_id is in every APK's resource table (the shared ShortcutActivity looks it up by name, T11-47)
#   - xml/shortcuts holds five shortcuts qa_one..qa_five, labels One..Five, each with the adaptive icon (which has a
#     monochrome layer), an ACTION_VIEW intent to tileclient-a's ShortcutActivity and qa_id = its own id (T11-31/33)
#   - tileclient-b's qa_dyn icon is an adaptive icon with a monochrome layer (T11-33)
#
#   bash docs/plan/qa/phase-11/scripts/fixtures_static_check.sh > docs/plan/qa/phase-11/fixtures-static-check.txt
# Exit code = the number of failed checks (a failed build counts as one).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../.." && pwd)"
cd "$REPO" || exit 1
AAPT2="$(ls -d "$HOME"/Android/Sdk/build-tools/*/aapt2 | sort -V | tail -1)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
apk() { echo "testapps/tileclient-$1/build/outputs/apk/debug/tileclient-$1-debug.apk"; }

echo "=============================================================================="
echo "Phase 11 fixtures: static check of the three test APKs (no device)"
echo "at        $(date -Is)"
echo "HEAD      $(git rev-parse HEAD)"
echo "testapps  $(git status --porcelain -- testapps | wc -l) uncommitted path(s) under testapps/"
echo "driver    fixtures_static_check.sh blob $(git hash-object "$HERE/fixtures_static_check.sh")"
echo "aapt2     ${AAPT2#"$HOME"/} ($("$AAPT2" version 2>&1))"
echo "=============================================================================="

echo
echo "\$ ./gradlew :testapps:tileclient-a:assembleDebug :testapps:tileclient-b:assembleDebug :testapps:tileclient-b2:assembleDebug"
./gradlew :testapps:tileclient-a:assembleDebug :testapps:tileclient-b:assembleDebug :testapps:tileclient-b2:assembleDebug \
  > "$OUT/gradle.txt" 2>&1
BUILD_RC=$?
grep -E '^BUILD |actionable tasks' "$OUT/gradle.txt"
echo "exit code: $BUILD_RC"
for a in a b b2; do
  echo "$(apk "$a")  $(stat -c%s "$(apk "$a")") bytes  sha256 $(sha256sum "$(apk "$a")" | cut -c1-16)"
done

for a in a b b2; do
  "$AAPT2" dump xmltree --file AndroidManifest.xml "$(apk "$a")" > "$OUT/manifest-$a.txt" 2>&1
  "$AAPT2" dump resources "$(apk "$a")" > "$OUT/resources-$a.txt" 2>&1
done
"$AAPT2" dump xmltree --file res/xml/shortcuts.xml "$(apk a)" > "$OUT/shortcuts-a.txt" 2>&1
"$AAPT2" dump xmltree --file res/drawable/ic_qa_shortcut.xml "$(apk a)" > "$OUT/icon-a.txt" 2>&1
"$AAPT2" dump xmltree --file res/drawable/ic_qa_dyn.xml "$(apk b)" > "$OUT/icon-b.txt" 2>&1

for a in a b b2; do
  echo
  echo "------------------------------------------------------------------------------ tileclient-$a: manifest"
  echo "\$ aapt2 dump xmltree --file AndroidManifest.xml $(apk "$a")   (from the application element on)"
  sed -n '/E: application /,$p' "$OUT/manifest-$a.txt"
  echo
  echo "------------------------------------------------------------------------------ tileclient-$a: resources"
  echo "\$ aapt2 dump resources $(apk "$a")"
  cat "$OUT/resources-$a.txt"
done
echo
echo "------------------------------------------------------------------------------ tileclient-a: xml/shortcuts"
echo "\$ aapt2 dump xmltree --file res/xml/shortcuts.xml $(apk a)"
cat "$OUT/shortcuts-a.txt"
echo
echo "------------------------------------------------------------------------------ tileclient-a: the shortcuts' icon"
echo "\$ aapt2 dump xmltree --file res/drawable/ic_qa_shortcut.xml $(apk a)"
cat "$OUT/icon-a.txt"
echo
echo "------------------------------------------------------------------------------ tileclient-b: qa_dyn's icon"
echo "\$ aapt2 dump xmltree --file res/drawable/ic_qa_dyn.xml $(apk b)"
cat "$OUT/icon-b.txt"

echo
echo "------------------------------------------------------------------------------ checks (computed from the dumps above)"
python3 - "$OUT" "$BUILD_RC" <<'PY'
import os, re, sys

out, build_rc = sys.argv[1], int(sys.argv[2])
ANDROID = "http://schemas.android.com/apk/res/android:"


def tree(path):
    """aapt2's xmltree text -> nested dicts {tag, attrs, children}, by indentation."""
    root = {"tag": "#root", "attrs": {}, "children": []}
    stack = [(-1, root)]
    for line in open(path, encoding="utf-8"):
        m = re.match(r"^( *)(E|A): (.*)$", line.rstrip("\n"))
        if not m:
            continue
        depth, kind, rest = len(m.group(1)), m.group(2), m.group(3)
        if kind == "E":
            node = {"tag": rest.split(" ")[0], "attrs": {}, "children": []}
            while stack[-1][0] >= depth:
                stack.pop()
            stack[-1][1]["children"].append(node)
            stack.append((depth, node))
        else:
            rest = rest.replace(ANDROID, "")
            a = re.match(r'^([\w:.-]+?)(?:\(0x[0-9a-f]+\))?=(?:"([^"]*)"|(\S+))', rest)
            if a:
                stack[-1][1]["attrs"][a.group(1)] = a.group(2) if a.group(2) is not None else a.group(3)
    return root


def walk(node):
    yield node
    for c in node["children"]:
        yield from walk(c)


def find(node, tag):
    return [n for n in walk(node) if n["tag"] == tag]


def resources(path):
    """aapt2 dump resources -> ({0xid: 'type/name'}, {'string/name': value})."""
    ids, strings, last = {}, {}, None
    for line in open(path, encoding="utf-8"):
        m = re.match(r"^\s+resource (0x[0-9a-f]+) (\S+)", line)
        if m:
            ids[m.group(1)] = m.group(2)
            last = m.group(2)
            continue
        m = re.match(r'^\s+\(\) "(.*)"$', line)
        if m and last and last.startswith("string/"):
            strings[last] = m.group(1)
    return ids, strings


fails = 0
passes = 0


def check(name, ok, detail=""):
    global fails, passes
    if ok:
        passes += 1
    else:
        fails += 1
    print("%-4s  %-76s %s" % ("PASS" if ok else "FAIL", name, detail))


check("the three APKs built (gradle exit code 0)", build_rc == 0, "exit %d" % build_rc)
man = {a: tree(os.path.join(out, "manifest-%s.txt" % a)) for a in ("a", "b", "b2")}
res = {a: resources(os.path.join(out, "resources-%s.txt" % a)) for a in ("a", "b", "b2")}


def component(a, tag, name):
    return [n for n in find(man[a], tag) if n["attrs"].get("name") == name]


def shortcuts_meta(a):
    return [n for n in find(man[a], "meta-data") if n["attrs"].get("name") == "android.app.shortcuts"]


# tileclient-a: the meta-data sits on the LAUNCHER activity VerbActivity and names xml/shortcuts.
verb_a = component("a", "activity", "app.tileshell.testclient.VerbActivity")
launcher = verb_a and any(
    c["attrs"].get("name") == "android.intent.category.LAUNCHER"
    for f in find(verb_a[0], "intent-filter") for c in find(f, "category"))
meta = [c for c in (verb_a[0]["children"] if verb_a else []) if c["tag"] == "meta-data"
        and c["attrs"].get("name") == "android.app.shortcuts"]
meta_res = res["a"][0].get(meta[0]["attrs"].get("resource", "").lstrip("@")) if meta else None
check("a: VerbActivity is the LAUNCHER activity", bool(launcher))
check("a: VerbActivity carries meta-data android.app.shortcuts -> xml/shortcuts", meta_res == "xml/shortcuts",
      "resource=%s" % meta_res)
check("a: that is the APK's only android.app.shortcuts meta-data", len(shortcuts_meta("a")) == 1,
      "%d found" % len(shortcuts_meta("a")))
sa = component("a", "activity", "app.tileshell.testclient.ShortcutActivity")
check("a: declares ShortcutActivity (exported=false)", len(sa) == 1 and sa[0]["attrs"].get("exported") == "false",
      "exported=%s" % (sa[0]["attrs"].get("exported") if sa else None))
check("a: declares no ShortcutVerbReceiver", not component("a", "receiver", "app.tileshell.testclient.ShortcutVerbReceiver"))

rb = component("b", "receiver", "app.tileshell.testclient.ShortcutVerbReceiver")
check("b: declares ShortcutVerbReceiver, exported=true", len(rb) == 1 and rb[0]["attrs"].get("exported") == "true",
      "exported=%s" % (rb[0]["attrs"].get("exported") if rb else None))
check("b: declares no ShortcutActivity", not component("b", "activity", "app.tileshell.testclient.ShortcutActivity"))
check("b: carries no android.app.shortcuts meta-data (its shortcuts are dynamic)", not shortcuts_meta("b"))

check("b2: declares no ShortcutActivity", not component("b2", "activity", "app.tileshell.testclient.ShortcutActivity"))
check("b2: declares no ShortcutVerbReceiver", not component("b2", "receiver", "app.tileshell.testclient.ShortcutVerbReceiver"))
check("b2: carries no android.app.shortcuts meta-data", not shortcuts_meta("b2"))

for a in ("a", "b", "b2"):
    check("%s: id/shortcut_id is in the resource table" % a, "id/shortcut_id" in res[a][0].values())

# tileclient-a's xml/shortcuts.
ids_a, strings_a = res["a"]
sc = find(tree(os.path.join(out, "shortcuts-a.txt")), "shortcut")
want = ["qa_one", "qa_two", "qa_three", "qa_four", "qa_five"]
labels = ["One", "Two", "Three", "Four", "Five"]
check("a: xml/shortcuts holds exactly five shortcuts", len(sc) == 5, "%d found" % len(sc))
got_ids = [s["attrs"].get("shortcutId") for s in sc]
check("a: their ids in declaration (rank) order are qa_one..qa_five", got_ids == want, ",".join(map(str, got_ids)))
got_labels = [strings_a.get(ids_a.get(s["attrs"].get("shortcutShortLabel", "").lstrip("@"), ""), None) for s in sc]
check("a: their short labels are One..Five", got_labels == labels, ",".join(map(str, got_labels)))
icons = [ids_a.get(s["attrs"].get("icon", "").lstrip("@")) for s in sc]
check("a: every shortcut has android:icon = drawable/ic_qa_shortcut", icons == ["drawable/ic_qa_shortcut"] * 5,
      ",".join(map(str, icons)))
intents_ok = []
for s in sc:
    it = find(s, "intent")
    ex = find(it[0], "extra") if it else []
    intents_ok.append(
        len(it) == 1
        and it[0]["attrs"].get("action") == "android.intent.action.VIEW"
        and it[0]["attrs"].get("targetPackage") == "app.tileshell.testclient.a"
        and it[0]["attrs"].get("targetClass") == "app.tileshell.testclient.ShortcutActivity"
        and len(ex) == 1 and ex[0]["attrs"].get("name") == "qa_id"
        and ex[0]["attrs"].get("value") == s["attrs"].get("shortcutId"))
check("a: each intent is VIEW -> a/ShortcutActivity with qa_id = its own id", all(intents_ok) and len(sc) == 5,
      "".join("y" if x else "n" for x in intents_ok))
check("a: no shortcut is disabled", all(s["attrs"].get("enabled", "true") == "true" for s in sc))


def adaptive(path, apk_ids):
    t = tree(path)
    ai = find(t, "adaptive-icon")
    layers = {c["tag"]: apk_ids.get(c["attrs"].get("drawable", "").lstrip("@")) for c in (ai[0]["children"] if ai else [])}
    return bool(ai), layers


ok, layers = adaptive(os.path.join(out, "icon-a.txt"), ids_a)
check("a: drawable/ic_qa_shortcut is an adaptive-icon", ok)
check("a: ... with background, foreground and a monochrome layer",
      set(layers) == {"background", "foreground", "monochrome"} and all(layers.values()), str(layers))
ok, layers = adaptive(os.path.join(out, "icon-b.txt"), res["b"][0])
check("b: drawable/ic_qa_dyn (qa_dyn's icon) is an adaptive-icon", ok)
check("b: ... with background, foreground and a monochrome layer",
      set(layers) == {"background", "foreground", "monochrome"} and all(layers.values()), str(layers))

print("------------------------------------------------------------------------------")
print("fixtures static check: %d passed, %d failed" % (passes, fails))
sys.exit(fails)
PY
RC=$?
echo "exit code: $RC"
exit "$RC"
