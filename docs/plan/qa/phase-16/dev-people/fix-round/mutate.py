#!/usr/bin/env python3
"""Mutation proof for the fix round's parser and intent-rule tests (People's part of F4, F5, F11, F15, F22 and F23).

Each mutation is applied to its one source file, one test class is run, the failing tests are read from its XML
report, and the file is restored. Usage: mutate.py <test class> <id> [<id> ...]   (run from the repo root)."""
import re, subprocess, sys

SRC = 'app/src/main/kotlin/app/tileshell/people/PeopleRoute.kt'
WRITES = 'app/src/main/kotlin/app/tileshell/people/PeopleWrites.kt'
GUARD = 'app/src/main/kotlin/app/tileshell/people/PeopleWriteGuard.kt'
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
    'A1': ('the action logged as the caller wrote it', 'action in HANDLED_ACTIONS -> action', 'action != null -> action'),
    'A2': ('an action outside the handled six let through (DELETE added to the set)',
           'setOf(ACTION_MAIN, ACTION_VIEW,', 'setOf("android.intent.action.DELETE", ACTION_MAIN, ACTION_VIEW,'),
    'A3': ('the open line carries the lookup key the URI named', 'is PeopleRoute.Card -> "card"', 'is PeopleRoute.Card -> "card ${route.contact.encodedLookupKey}"'),
    'A4': ('the open line carries the name the caller prefilled (the reviewer\'s X6, on People)',
           'is PeopleRoute.Insert -> "insert (prefilled, unsaved)"', 'is PeopleRoute.Insert -> "insert ${route.prefill.name} (prefilled, unsaved)"'),
    'K1': ('a PICK honoured with no caller', 'if (route is PeopleRoute.Pick && !hasCaller) PeopleRoute.Open(null) else route', 'route'),
    'K2': ('a PICK refused even with a caller', 'if (route is PeopleRoute.Pick && !hasCaller) PeopleRoute.Open(null) else route',
           'if (route is PeopleRoute.Pick) PeopleRoute.Open(null) else route'),
    'K3': ('a caller turns another route into a pick', 'if (route is PeopleRoute.Pick && !hasCaller) PeopleRoute.Open(null) else route',
           'if (route is PeopleRoute.Pick && !hasCaller) PeopleRoute.Open(null) else if (hasCaller && route is PeopleRoute.Open) PeopleRoute.Pick(PickKind.CONTACT) else route'),
    'X1': ('the PICK result also grants write, persistable and prefix (the reviewer\'s X1)',
           'const val PICK_RESULT_FLAGS = 0x1', 'const val PICK_RESULT_FLAGS = 0x1 or 0x2 or 0x40 or 0x80'),
    'X1b': ('the PICK result grants a persistable read', 'const val PICK_RESULT_FLAGS = 0x1', 'const val PICK_RESULT_FLAGS = 0x1 or 0x40'),
    'X1c': ('the PICK result grants nothing', 'const val PICK_RESULT_FLAGS = 0x1', 'const val PICK_RESULT_FLAGS = 0x0'),
    'L1': ('the no-caller line says a URI was granted', 'const val PICK_NO_CALLER = "pick: no caller to return a result to; the list was opened"',
           'const val PICK_NO_CALLER = "pick: one contact URI granted (read)"'),
    # ---- the write layer (F15): the reviewer's PW1-PW4, then the same idea for every other op
    'PW1': ('update(): the per-raw-contact guard loop removed',
            '        for (s in steps) if (refused(PeopleWrite.DataRow(s.op, raws.getValue(s.rawId)))) return refusedLine("update", s.rawId.toString())\n', '', WRITES),
    'PW2': ('delete(): the guard\'s refusal ignored',
            '        if (verdict is GuardVerdict.Refused) return refusedLine("delete", (verdict.rawId ?: raws.first().id).toString())\n', '', WRITES),
    'PW3': ('create(): the guard not asked',
            '        if (refused(PeopleWrite.NewRawContact(account, source))) return refusedLine("insert", "new")\n', '', WRITES),
    'PW4': ('the write layer\'s `refused` helper always says allowed',
            'private fun refused(write: PeopleWrite): Boolean = PeopleWriteGuard.check(write, port.policy()) is GuardVerdict.Refused',
            'private fun refused(write: PeopleWrite): Boolean = false', WRITES),
    'PW5': ('update(): the photo\'s raw contact not asked about before the fields are written',
            '        if (photoRaw != null && refused(PeopleWrite.DataRow(WriteOp.UPDATE, raws.getValue(photoRaw)))) return refusedLine("update", photoRaw.toString())\n', '', WRITES),
    'PW6': ('the photo write and removal no longer ask the guard themselves',
            '        if (refused(PeopleWrite.DataRow(op, raw))) return refusedLine("update", rawId.toString())\n', '', WRITES),
    'PW7': ('createGroup(): the guard not asked',
            '        if (refused(PeopleWrite.GroupRow(WriteOp.INSERT, account))) return groupFailed("create", "new", "refused (not allowed)", refused = true)\n', '', WRITES),
    'PW8': ('renameGroup(): the guard not asked',
            '        if (refused(PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return groupFailed("rename", groupId.toString(), "refused (not allowed)", refused = true)\n', '', WRITES),
    'PW9': ('deleteGroup(): the guard not asked',
            '        if (refused(PeopleWrite.GroupRow(WriteOp.DELETE, group.account))) return groupFailed("delete", groupId.toString(), "refused (not allowed)", refused = true)\n', '', WRITES),
    'PW9b': ('deleteGroup(): every group removed outright through the sync-adapter URI',
             'if (port.policy().isPhone(group.account)) uri += "?${ContactsContract.CALLER_IS_SYNCADAPTER}=true"', 'uri += "?${ContactsContract.CALLER_IS_SYNCADAPTER}=true"', WRITES),
    'PW9c': ('deleteGroup(): no group removed outright (a phone group left marked deleted)',
             'if (port.policy().isPhone(group.account)) uri += "?${ContactsContract.CALLER_IS_SYNCADAPTER}=true"', '', WRITES),
    'PW10a': ('setMember(): the group check removed (the data-row check stays)',
              '        if (refused(PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return refusedLine("update", raw.id.toString())\n', '', WRITES),
    'PW10b': ('setMember(): the data-row check removed (the group check stays)',
              '        if (refused(PeopleWrite.DataRow(if (member) WriteOp.INSERT else WriteOp.DELETE, raw))) return refusedLine("update", raw.id.toString())\n', '', WRITES),
    'PW10c': ('setMember(): neither check asked',
              '        if (refused(PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return refusedLine("update", raw.id.toString())\n        if (refused(PeopleWrite.DataRow(if (member) WriteOp.INSERT else WriteOp.DELETE, raw))) return refusedLine("update", raw.id.toString())\n', '', WRITES),
    'PW12': ('update(): a field\'s raw contact taken from the draft, not from the provider',
             'val raw = owner[e.dataId] ?: return failedLine("update", e.rawId?.toString() ?: "unknown", "the field is gone")',
             'val raw = e.rawId ?: owner[e.dataId] ?: return failedLine("update", "unknown", "the field is gone")', WRITES),
    'PW13': ('importSim(): the first allowed account instead of the phone',
             'val local = port.policy().local', 'val local = port.policy().allowed.firstOrNull() ?: port.policy().local', WRITES),
    'PW14': ('create(): WRITE_CONTACTS not checked',
             '        if (!port.mayWrite()) return failedLine("insert", "new", NOT_HELD, needsGrant = true)\n', '', WRITES),
    'PW15': ('update(): a data row addressed by its id alone, not with its raw contact',
             'const val DATA_ROW_OF_RAW = "${Data._ID}=? AND ${Data.RAW_CONTACT_ID}=?"', 'const val DATA_ROW_OF_RAW = "${Data._ID}=?"', WRITES),
    'PW16': ('delete(): only the first raw contact behind the contact removed',
             'port.applyBatch(raws.map { RowWrite(WriteOp.DELETE, "$RAW_CONTACTS/${it.id}") })', 'port.applyBatch(raws.take(1).map { RowWrite(WriteOp.DELETE, "$RAW_CONTACTS/${it.id}") })', WRITES),
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
for i in ids:
    what, old, new = MUTATIONS[i][:3]
    src = MUTATIONS[i][3] if len(MUTATIONS[i]) > 3 else SRC
    original = open(src, encoding='utf-8').read()
    if original.count(old) < 1:
        print('%s %s | NOT APPLIED (the text to mutate is absent)' % (i, what)); continue
    try:
        open(src, 'w', encoding='utf-8').write(original.replace(old, new))
        rc, tests, failed = run(cls)
    finally:
        open(src, 'w', encoding='utf-8').write(original)
    print('%s %s | %s (gradle rc=%d, %s tests run, %d failed) | %s' % (i, what, 'CAUGHT' if rc != 0 and failed else 'SURVIVED', rc, tests, len(failed), '; '.join(failed)), flush=True)
rc, tests, failed = run(cls)
print('unmutated | gradle rc=%d, %s tests run, %d failed' % (rc, tests, len(failed)))
