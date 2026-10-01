#!/usr/bin/env python3
"""Mutation proof for the fix round's parser and intent-rule tests (People's part of F11).

Each mutation is applied to the source file alone, one test class is run, the failing tests are read from its XML
report, and the file is restored. Usage: mutate.py <test class> <id> [<id> ...]   (run from the repo root)."""
import re, subprocess, sys

SRC = 'app/src/main/kotlin/app/tileshell/people/PeopleRoute.kt'
MUTATIONS = {
    'P6': ('authority prefix without its trailing slash',
           'val prefix = "content://$AUTHORITY/"', 'val prefix = "content://$AUTHORITY"'),
    'P7': ('INSERT by type while the data is a foreign URI',
           '(data == null && type == TYPE_CONTACT_DIR)', '(type == TYPE_CONTACT_DIR)'),
    'P8': ('INSERT_OR_EDIT whatever the type or data',
           'if (type == TYPE_CONTACT_ITEM || path == listOf("contacts")) PeopleRoute.InsertOrEdit(prefill(extras)) else open(extras)',
           'PeopleRoute.InsertOrEdit(prefill(extras))'),
    'P9': ('lookup/<key>/<id> accepts an id of 0 or less',
           'path[3].toLongOrNull()?.takeIf { it > 0 }?.let { ContactRef(path[2], it) }', 'path[3].toLongOrNull()?.let { ContactRef(path[2], it) }'),
    'P9b': ('lookup/<key>/<anything> read as the key alone (the parser before the fix round)',
            'path[3].toLongOrNull()?.takeIf { it > 0 }?.let { ContactRef(path[2], it) }', 'ContactRef(path[2], path[3].toLongOrNull()?.takeIf { it > 0 })'),
    'P11': ('query not stripped', ".substringBefore('?').substringBefore('#')", ".substringBefore('#')"),
    'P12': ('fragment not stripped', ".substringBefore('?').substringBefore('#')", ".substringBefore('?')"),
    'P13': ('contacts/<x>/<y> taken as a lookup key', 'path[1] == "lookup" && ', ''),
    'D1': ('a dot segment accepted as a lookup key',
           'segment.isNotEmpty() && segment.length <= MAX_FIELD && segment.replace("%2e", ".", ignoreCase = true).any { it != \'.\' }',
           'segment.isNotEmpty() && segment.length <= MAX_FIELD'),
    'D2': ('only a literal dot segment refused, not %2E',
           'segment.replace("%2e", ".", ignoreCase = true).any', 'segment.any'),
}

def run(cls):
    p = subprocess.run(['./gradlew', ':app:testDebugUnitTest', '--tests', cls], capture_output=True, text=True)
    failed, tests = [], '?'
    try:
        x = open('app/build/test-results/testDebugUnitTest/TEST-%s.xml' % cls, encoding='utf-8').read()
        tests = re.search(r'<testsuite[^>]*tests="(\d+)"', x).group(1)
        failed = re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure', x)
    except OSError:
        failed = ['(no report: the build failed) ' + ' '.join(l for l in p.stdout.splitlines() if l.startswith('e: '))[:300]]
    return p.returncode, tests, failed

cls, ids = sys.argv[1], sys.argv[2:]
original = open(SRC, encoding='utf-8').read()
for i in ids:
    what, old, new = MUTATIONS[i]
    if original.count(old) < 1:
        print('%s %s | NOT APPLIED (the text to mutate is absent)' % (i, what)); continue
    try:
        open(SRC, 'w', encoding='utf-8').write(original.replace(old, new))
        rc, tests, failed = run(cls)
    finally:
        open(SRC, 'w', encoding='utf-8').write(original)
    print('%s %s | %s (gradle rc=%d, %s tests run, %d failed) | %s' % (i, what, 'CAUGHT' if rc != 0 and failed else 'SURVIVED', rc, tests, len(failed), '; '.join(failed)), flush=True)
rc, tests, failed = run(cls)
print('unmutated | gradle rc=%d, %s tests run, %d failed' % (rc, tests, len(failed)))
