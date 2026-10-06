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

# The doc's E18, one entry per alternative: (what the doc writes, a fixed piece every producers / notrun pattern for
# it must contain).
DOC = [
    ('[photosapp] library: … access=GRANTED', 'access=GRANTED'), ('[photosapp] library: … access=PARTIAL', 'access=PARTIAL'),
    ('[photosapp] library: … access=DENIED', 'access=DENIED'), ('[photosapp] slideshow next <id>', '[photosapp] slideshow next'),
    ('[photosapp] edit <tool> -> <uri>', '[photosapp] edit'), ('[photosapp] trim <id> <from>..<to> -> <uri>', '[photosapp] trim'),
    ('[photosapp] delete <id>: refused by user', 'refused by user'), ('[photosapp] edit <tool> failed: <why>', 'failed'),
    ('[photosapp] trim <id> failed: <why>', 'failed'), ('[photosapp] set as background <id> -> <file>', 'set as background'),
    ('[camera] devices=<n> front=present', 'front=present'), ('[camera] devices=<n> front=absent', 'front=absent'),
    ('[camera] busy: <reason>', '[camera] busy'), ('[camera] saved <uri> <w>x<h>', '[camera] saved'),
    ('[camera] timer <n>s -> shutter', '[camera] timer'), ('[camera] focus at <x>,<y>: <state>', '[camera] focus at'),
    ('[camera] mode <x>: unavailable (<reason>)', 'unavailable'), ('[camera] refused output scheme=<s>', 'refused output scheme='),
    ('[camera] refused output: no grant', 'refused output: no grant'), ('[camera] video sound: off', 'video sound: off'),
    ('[camera] mode hdr: unavailable', 'mode hdr'), ('[camera] slowmo <n> fps captured', '[camera] slowmo'),
    ('[video] library: <n>', '[video] library'), ('[video] playing <id>', '[video] playing <id>'),
    ('[video] playing scheme=<s>', 'playing scheme='), ('[video] cannot decode <name>', 'cannot decode'),
    ('[video] cannot reach <host>', 'cannot reach'), ('[video] unsupported scheme=<s>', 'unsupported scheme='),
    ('[video] catalogue "<q>": <n>', '[video] catalogue "'), ('[video] catalogue "<q>": offline', 'offline'),
    ('[video] catalogue "<q>": error <code>', 'error'), ('[video] catalogue: no TMDB key saved', 'no TMDB key saved'),
    ('[video] watch-on … -> <intent>', '[video] watch-on'), ('[video] watch-on … not installed', 'not installed'),
    ('[video] watch-on … id found (wikidata)', 'found'), ('[video] watch-on … id none (wikidata)', 'none'),
    ('[video] server <host>: connected', 'connected'), ('[video] server <host>: unreachable', 'unreachable'),
    ('[video] server <host>: unauthorised', 'unauthorised'), ('[video] server <host>: insecure, asked', 'insecure'),
    ('[video] server token cleared', 'server token cleared'), ('[video] shortcut mediaserver published', 'published'),
    ('[video] shortcut mediaserver removed', 'removed'), ('[net] cleartext permitted for <host>: <bool>', '[net] cleartext permitted'),
    ('[music] session app.tileshell id=video -> none', '[music] session'),
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
    """The doc's words to a grep: <placeholders> and … match anything, the rest is literal."""
    parts = re.split(r'(<[^<>]*>|…)', pattern)
    return ''.join('.*' if (p.startswith('<') and p.endswith('>')) or p == '…' else re.escape(p) for p in parts if p)


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
        cre = re.compile(rx)
    except re.error as e:
        verdict('FAIL', pat, f'the regex does not compile: {e}')
        continue
    hit = next((f for f, t in gate.items() if cre.search(t)), None)
    if hit:
        verdict('PASS', pat, 'held by ' + os.path.relpath(hit, QA))
        joined.append((pat, by, os.path.relpath(hit, QA)))
        continue
    old = next((f for f, t in slices.items() if cre.search(t)), None)
    if old:
        verdict('RECORD', pat, 'held only by an earlier build\'s kept run: ' + os.path.relpath(old, QA))
        joined.append((pat, by, os.path.relpath(old, QA) + ' (earlier build)'))
    else:
        verdict('FAIL', pat, f'named by producers_{app}.tsv ({by}) and held by no slice; regex {rx}')
        joined.append((pat, by, 'NO SLICE'))

named = [p for _, p, _, _ in producers] + [p for _, p, _ in notrun]
for words, piece in DOC:
    if any(piece in p for p in named):
        verdict('PASS', 'the doc\'s line is named: ' + words, 'in a producers or notrun file')
    else:
        verdict('FAIL', 'the doc\'s line is named: ' + words, f'no producers or notrun pattern contains [{piece}]')

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
