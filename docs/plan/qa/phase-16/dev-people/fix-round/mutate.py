#!/usr/bin/env python3
"""Mutation proof for the fix round's parser and intent-rule tests (People's part of F4, F5, F11, F15, F22 and F23).

Each mutation is applied to its one source file, one test class is run, the failing tests are read from its XML
report, and the file is restored. Usage: mutate.py <test class>[,<test class>] <id> [<id> ...]   (run from the repo root)."""
import re, subprocess, sys

SRC = 'app/src/main/kotlin/app/tileshell/people/PeopleRoute.kt'
WRITES = 'app/src/main/kotlin/app/tileshell/people/PeopleWrites.kt'
GUARD = 'app/src/main/kotlin/app/tileshell/people/PeopleWriteGuard.kt'
MODEL = 'app/src/main/kotlin/app/tileshell/people/PeopleModel.kt'
MUTATIONS = {
    'P6': ('authority prefix without its trailing slash',
           'val prefix = "content://$AUTHORITY/"', 'val prefix = "content://$AUTHORITY"'),
    'P7': ('INSERT by type while the data is a foreign URI',
           '(data == null && type == TYPE_CONTACT_DIR)', '(type == TYPE_CONTACT_DIR)'),
    'P8': ('INSERT_OR_EDIT whatever the type or data',
           'if (path == listOf("contacts") || (data == null && type == TYPE_CONTACT_ITEM)) PeopleRoute.InsertOrEdit(prefill(extras)) else open(extras)',
           'PeopleRoute.InsertOrEdit(prefill(extras))'),
    'P8b': ('INSERT_OR_EDIT by type while the data is a foreign URI',
            '(data == null && type == TYPE_CONTACT_ITEM)', '(type == TYPE_CONTACT_ITEM)'),
    'P8c': ('INSERT_OR_EDIT by type only: the contacts URI no longer asks for it',
            'if (path == listOf("contacts") || (data == null && type == TYPE_CONTACT_ITEM))', 'if ((data == null && type == TYPE_CONTACT_ITEM))'),
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
            '        if (refused(write)) return groupFailed("delete", groupId.toString(), "refused (not allowed)", refused = true)\n', '', WRITES),
    'PW9b': ('deleteGroup(): every group removed outright through the sync-adapter URI, and the guard told so',
             'viaSyncAdapter = port.policy().isPhone(group.account))', 'viaSyncAdapter = true)', WRITES),
    'PW9c': ('deleteGroup(): no group removed outright (a phone group left marked deleted)',
             'viaSyncAdapter = port.policy().isPhone(group.account))', 'viaSyncAdapter = false)', WRITES),
    'PW9d': ('deleteGroup(): the sync-adapter URI used for every group while the guard is asked about the plain one',
             'val uri = "$GROUPS/$groupId" + if (write.viaSyncAdapter) "?${ContactsContract.CALLER_IS_SYNCADAPTER}=true" else ""',
             'val uri = "$GROUPS/$groupId" + "?${ContactsContract.CALLER_IS_SYNCADAPTER}=true"', WRITES),
    # ---- the guard's sync-adapter fact (F22)
    'GV1': ('guard: the sync-adapter delete allowed for any writable account, not the phone alone',
            'write.viaSyncAdapter -> if (write.op == WriteOp.DELETE && policy.isPhone(write.account))',
            'write.viaSyncAdapter -> if (write.op == WriteOp.DELETE && policy.canWrite(write.account))', GUARD),
    'GV2': ('guard: any group op allowed through the sync-adapter URI on the phone',
            'write.viaSyncAdapter -> if (write.op == WriteOp.DELETE && policy.isPhone(write.account))',
            'write.viaSyncAdapter -> if (policy.isPhone(write.account))', GUARD),
    'GV3': ('guard: the sync-adapter fact ignored',
            '            write.viaSyncAdapter -> if (write.op == WriteOp.DELETE && policy.isPhone(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)\n', '', GUARD),
    'GV4': ('guard: the sync-adapter delete refused even for the phone',
            'write.viaSyncAdapter -> if (write.op == WriteOp.DELETE && policy.isPhone(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)',
            'write.viaSyncAdapter -> GuardVerdict.Refused(null)', GUARD),
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
    # ---- another profile's rows (F23)
    'OP1': ('the write layer does not name another profile\'s contact as what it is (it just finds nothing)',
            'if (port.isOtherProfile(contactId)) listOf(OtherProfile.REF) else port.rawContactsOf(contactId)', 'port.rawContactsOf(contactId)', WRITES),
    'OP2': ('update(): a field or photo naming another profile\'s raw contact not put to the guard',
            '        if (named.any(OtherProfile::isRaw) && refused(PeopleWrite.DataRow(WriteOp.UPDATE, OtherProfile.REF))) return refusedLine("update", OtherProfile.RAW.toString())\n', '', WRITES),
    'OP3': ('the reference for another profile\'s contact not marked as another profile\'s',
            'val REF = RawRef(RAW, ContactAccount(null, null), otherProfile = true)', 'val REF = RawRef(RAW, ContactAccount(null, null), otherProfile = false)', GUARD),
    'OP5': ('guard: another profile\'s raw contact editable when its account is writable',
            'fun editable(raw: RawRef, policy: EditPolicy): Boolean = !raw.otherProfile && policy.canWrite(raw.account)',
            'fun editable(raw: RawRef, policy: EditPolicy): Boolean = policy.canWrite(raw.account)', GUARD),
    'OP6': ('the read model drops the mark on its way to the guard',
            'fun ref() = RawRef(id, account, otherProfile)', 'fun ref() = RawRef(id, account)', MODEL),
    'OP7': ('setMember(): another profile\'s contact looked for by the group\'s account like any other',
            'val raw = raws.firstOrNull { it.otherProfile } ?: raws.firstOrNull { it.account == group.account }', 'val raw = raws.firstOrNull { it.account == group.account }', WRITES),
    # ---- Link and Unlink with another profile's contact (the lead's ruling on F23)
    'GA1': ('guard: the other-profile check removed from the aggregation case (Link and Unlink allowed on any contact)',
            '            write.a.otherProfile -> GuardVerdict.Refused(write.a.id)\n            write.b.otherProfile -> GuardVerdict.Refused(write.b.id)\n', '', GUARD),
    'GA2': ('guard: only the first raw contact of the pair checked',
            '            write.b.otherProfile -> GuardVerdict.Refused(write.b.id)\n', '', GUARD),
    'GA3': ('guard: only the second raw contact of the pair checked',
            '            write.a.otherProfile -> GuardVerdict.Refused(write.a.id)\n', '', GUARD),
    'OA1': ('link(): another profile\'s contact looked up like any other (it finds nothing)',
            '        val a = rawsBehind(contactA)\n        val b = rawsBehind(contactB)\n', '        val a = port.rawContactsOf(contactA)\n        val b = port.rawContactsOf(contactB)\n', WRITES),
    'OA2': ('unlink(): another profile\'s contact not put to the guard',
            '        raws.firstOrNull { it.otherProfile }?.let { return aggregate("unlink", label, listOf(it to it), together = false) }\n', '', WRITES),
    'OA3': ('link / unlink: the guard not asked',
            'if (pairs.any { PeopleWriteGuard.check(PeopleWrite.Aggregation(it.first, it.second, together), policy) is GuardVerdict.Refused }) {', 'if (false) {', WRITES),
    # ---- the editor's group rules (F31, defect D-E20-1)
    'ER1': ('editor: a rule between Phone and Email always (the defect)',
            '!(above == FieldKind.PHONE && below == FieldKind.EMAIL && fields(FieldKind.PHONE) == 0 && fields(FieldKind.EMAIL) == 0)', 'true', MODEL),
    'ER2': ('editor: no rule between Phone and Email whatever fields they hold',
            '!(above == FieldKind.PHONE && below == FieldKind.EMAIL && fields(FieldKind.PHONE) == 0 && fields(FieldKind.EMAIL) == 0)',
            '!(above == FieldKind.PHONE && below == FieldKind.EMAIL)', MODEL),
    'ER3': ('editor: the rule dropped when there is no number, whatever the e-mails',
            ' && fields(FieldKind.PHONE) == 0 && fields(FieldKind.EMAIL) == 0)', ' && fields(FieldKind.PHONE) == 0)', MODEL),
    'ER4': ('editor: the rule dropped when there is no e-mail, whatever the numbers',
            ' && fields(FieldKind.PHONE) == 0 && fields(FieldKind.EMAIL) == 0)', ' && fields(FieldKind.EMAIL) == 0)', MODEL),
    'ER5': ('editor: the rule dropped at every boundary whose blocks are empty',
            '!(above == FieldKind.PHONE && below == FieldKind.EMAIL && fields(FieldKind.PHONE) == 0 && fields(FieldKind.EMAIL) == 0)',
            '!(fields(above) == 0 && fields(below) == 0)', MODEL),
}

def run(classes):
    names = classes.split(',')
    args = ['./gradlew', ':app:testDebugUnitTest']
    for name in names:
        args += ['--tests', name]
    p = subprocess.run(args, capture_output=True, text=True)
    failed, tests = [], 0
    try:
        for name in names:
            x = open('app/build/test-results/testDebugUnitTest/TEST-%s.xml' % name, encoding='utf-8').read()
            tests += int(re.search(r'<testsuite[^>]*tests="(\d+)"', x).group(1))
            failed += ['%s.%s' % (name.rsplit('.', 1)[-1], t) for t in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure', x)]
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
