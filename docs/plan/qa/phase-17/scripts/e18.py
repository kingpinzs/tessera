#!/usr/bin/env python3
"""Phase 17 E18 - the join. Host only; it reads saved ring slices and touches no device.

For every alternative the three producers_<app>.tsv files (and the lead's own lines below) name, grep the union of
qa/phase-17/*/ring-*.txt. An alternative held by a slice of the gate build's runs passes; one held only by an earlier
build's kept run is RECORDED with that build (rows whose code did not change after that build were not run again:
the owner's rule is to test only what a change touches); one held by no slice FAILS. Every line of the doc's E18
(DOC below) must be named by a producers file or a notrun file, or the row fails.

usage: e18.py <qa/phase-17 dir> <gate build id, 8 hex>      writes <dir>/E18/producers.tsv, notrun.tsv, E18.txt
exit: 0 no failure, 1 otherwise
"""
import glob, os, re, sys

QA, GATE = sys.argv[1], sys.argv[2]
OUT = os.path.join(QA, 'E18')

# The doc's E18, one entry per alternative, in the exact words a producers or notrun line must carry.
DOC = [
    '[photosapp] library: images=<n> videos=<n> access=GRANTED', '[photosapp] library: images=<n> videos=<n> access=PARTIAL',
    '[photosapp] library: images=<n> videos=<n> access=DENIED', '[photosapp] slideshow next <id>',
    '[photosapp] edit <tool> -> <uri>', '[photosapp] trim <id> <from>..<to> -> <uri>',
    '[photosapp] delete <id>: refused by user', '[photosapp] edit <tool> failed: <why>',
    '[photosapp] trim <id> failed: <why>', '[photosapp] set as background <id> -> <file>',
    '[camera] devices=<n> front=present', '[camera] devices=<n> front=absent', '[camera] busy: <reason>',
    '[camera] saved <uri> <w>x<h>', '[camera] timer <n>s -> shutter', '[camera] focus at <x>,<y>: <state>',
    '[camera] mode <x>: unavailable (<reason>)', '[camera] refused output scheme=<s>',
    '[camera] refused output: no grant', '[camera] video sound: off (no microphone permission)',
    '[camera] mode hdr: unavailable', '[camera] slowmo <n> fps captured, encoded at 30 -> <uri>',
    '[video] library: <n>', '[video] playing <id>', '[video] playing scheme=<s>', '[video] cannot decode <name>',
    '[video] cannot reach <host>', '[video] unsupported scheme=<s>', '[video] catalogue "<q>": <n>',
    '[video] catalogue "<q>": <n> (refreshed)', '[video] catalogue "<q>": offline', '[video] catalogue "<q>": error <code>',
    '[video] catalogue: no TMDB key saved', '[video] watch-on <service> "<title>" -> https<intent>',
    '[video] watch-on <service> "<title>" -> not installed', '[video] watch-on <service> "<title>": id found (wikidata)',
    '[video] watch-on <service> "<title>": id none (wikidata)', '[video] server <host>: connected',
    '[video] server <host>: unreachable', '[video] server <host>: unauthorised', '[video] server <host>: insecure, asked',
    '[video] server token cleared', '[video] shortcut mediaserver published', '[video] shortcut mediaserver removed',
    '[net] cleartext permitted for <host>: false', '[music] session app.tileshell id=video -> none',
    # "every [motion] <name> … line the rows time"
    '[motion] viewer_open …', '[motion] photo_swipe …', '[motion] viewer_close …', '[motion] slideshow_step …',
    '[motion] photos_menu …', '[motion] viewer_zoom …', '[motion] capture_feedback …', '[motion] mode_switch …',
    '[motion] dial_open …', '[motion] controls_fade …', '[motion] pane_open …', '[motion] pane_close …',
]


def rows(path):
    out = []
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#') or line.startswith('line pattern'):
            continue
        cols = line.split('\t')
        out.append(cols + [''] * (3 - len(cols)))
    return out


def to_regex(pattern):
    """The doc's words to a grep. <n> and <id> are digits, <a|b> is one of its choices, any other <placeholder> or …
    is a run of anything; a pattern that ends on literal text must end the line there (so "offline" is not "offline
    cache"), and one that ends on digits must not run on into more text."""
    parts = [p for p in re.split(r'(<[^<>]*>|…)', pattern) if p]
    out = []
    for p in parts:
        if p in ('<n>', '<id>', '<w>', '<h>'):
            out.append(r'[0-9]+')
        elif p.startswith('<') and '|' in p:
            out.append('(?:' + '|'.join(re.escape(c) for c in p[1:-1].split('|')) + ')')
        elif (p.startswith('<') and p.endswith('>')) or p == '…':
            out.append(r'.+?')
        else:
            out.append(re.escape(p))
    return ''.join(out) + r'(?=\s*$|\s*\(refreshed\)\s*$)'


slices = {}  # path -> text
for f in sorted(glob.glob(os.path.join(QA, '*', 'ring-*.txt')) + glob.glob(os.path.join(QA, '*', '*', 'ring-*.txt'))):
    try:
        slices[f] = open(f, encoding='utf-8', errors='replace').read()
    except OSError:
        pass
gate = {f: t for f, t in slices.items() if 'build-' + GATE in f and '-pass-' in f}  # the build's PASSING runs only

producers, notrun = [], []
for app in ('photos', 'camera', 'video'):
    for pat, by, rx in rows(os.path.join(OUT, f'producers_{app}.tsv')):
        producers.append((app, pat, by, rx if app == 'camera' and rx else to_regex(pat)))
    for cols in rows(os.path.join(OUT, f'notrun_{app}.tsv')):
        notrun.append((app, cols[0], ' | '.join(c for c in cols[1:] if c)))

log, npass, nfail, nrec = [], 0, 0, 0


def verdict(kind, name, detail):
    global npass, nfail, nrec
    if kind == 'PASS':
        npass += 1
    elif kind == 'FAIL':
        nfail += 1
    else:
        nrec += 1
    log.append(f'{kind:<6}{name}    {detail}')


joined = []
for app, pat, by, rx in producers:
    try:
        cre = re.compile(rx, re.M)
    except re.error as e:
        verdict('FAIL', pat, f'the regex does not compile: {e}')
        continue
    hit = next((f for f, t in gate.items() if cre.search(t, re.M)), None)
    if hit:
        verdict('PASS', pat, 'held by ' + os.path.relpath(hit, QA))
        joined.append((pat, by, os.path.relpath(hit, QA)))
        continue
    old = next((f for f, t in slices.items() if '-pass-' in f and cre.search(t)), None)
    # The doc's E18: "an alternative it names that no slice [of this build's run] holds fails the row."
    verdict('FAIL', pat, (f'held only by an earlier build\'s passing run ({os.path.relpath(old, QA)}), not by a passing run of {GATE}'
                          if old else f'named by producers_{app}.tsv ({by}) and held by no passing run\'s slice; regex {rx}'))
    joined.append((pat, by, 'NO SLICE OF THE GATE BUILD'))

named = {p for _, p, _, _ in producers} | {p for _, p, _ in notrun}
for words in DOC:
    if words in named:
        verdict('PASS', 'the doc\'s line is named: ' + words, 'the exact pattern is a line of a producers or notrun file')
    else:
        verdict('FAIL', 'the doc\'s line is named: ' + words, 'no producers or notrun line has exactly this pattern')

with open(os.path.join(OUT, 'producers.tsv'), 'w', encoding='utf-8') as f:
    f.write('# E18 - the join of producers_photos.tsv, producers_camera.tsv and producers_video.tsv, written by e18.py\n')
    f.write('# the line pattern\tthe row or edge sub-step that produces it\tthe ring slice that holds it\n')
    for r in joined:
        f.write('\t'.join(r) + '\n')
with open(os.path.join(OUT, 'notrun.tsv'), 'w', encoding='utf-8') as f:
    f.write('# E18 - the join of the three notrun_<app>.tsv files, written by e18.py\n')
    f.write('# the line pattern\tthe reason, and its P row or JVM test\n')
    for _, pat, why in notrun:
        f.write(pat + '\t' + why + '\n')
log.append(f'   E18: {npass} passed, {nfail} failed, {nrec} recorded   (gate build {GATE}; {len(gate)} of {len(slices)} slices are the gate build\'s)')
open(os.path.join(OUT, 'E18.txt'), 'w', encoding='utf-8').write('\n'.join(log) + '\n')
print('\n'.join(l for l in log if not l.startswith('PASS')))
sys.exit(1 if nfail else 0)
