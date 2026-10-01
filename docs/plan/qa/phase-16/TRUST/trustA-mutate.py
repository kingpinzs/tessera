#!/usr/bin/env python3
"""Reviewer A's mutation driver. One mutation at a time in the trustA scratch worktree; whole unit suite each time;
the exit code is written to a file; the file is restored with `git checkout --` before the next. Never commits."""
import glob, os, shutil, subprocess, sys, time
import xml.etree.ElementTree as ET

W = "/home/jeremyking/projects/metro-launcher-p16/.claude/worktrees/trustA"
S = "/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad"
K = "app/src/main/kotlin/app/tileshell/"
GUARD = K + "calendar/CalendarWriteGuard.kt"
WRITES = K + "calendar/CalendarWrites.kt"
SYNC = K + "calendar/CalendarSync.kt"
STATE = K + "calendar/CalendarSyncState.kt"
READS = K + "calendar/CalendarReads.kt"
LOCAL = K + "feeds/LocalCalendar.kt"
ACTION = K + "cortana/action/ActionLayer.kt"
PGUARD = K + "people/PeopleWriteGuard.kt"
PWRITER = K + "people/PeopleWriter.kt"

NA = "return refused(Refusal.NOT_ALLOWED)"
STALE3 = (
    "        if (sync.mappingTarget != null && sync.mappingTarget != calendar.id) return refused(Refusal.MAPPING_STALE)\n"
    "        if (sync.copyCalendarId != null && sync.copyCalendarId != sync.mappingTarget) return refused(Refusal.MAPPING_STALE)\n"
    "        if (sync.mapped && sync.mappingTarget == null) return refused(Refusal.MAPPING_STALE)\n"
)
ALLOWED_LINE = "        if (!sync.targetAllowed) return refused(Refusal.NOT_ALLOWED)\n"
D5_COMMENT = "        // r3 D5: the provider accepts a normal insert whatever the level, and the copy would sit dirty and never upload.\n"
LEVEL_LINE = "        if (calendar.accessLevel < ACCESS_CONTRIBUTOR) return refused(Refusal.READ_ONLY)\n"
RECEIVER_BLOCK = (
    "        if (r.path == Path.RECEIVER) {\n"
    "            val stateOnly = r.op == Op.UPDATE && r.table == Table.CALENDAR_ALERTS && r.columns == setOf(ALERT_STATE_COLUMN) && !r.viaSyncAdapter\n"
    "            return if (stateOnly) Verdict.Allowed else refused(Refusal.NOT_ALLOWED)\n"
    "        }\n"
)
ALERTS_COMMENT = "        // Nobody but the receiver writes the alerts, and no path writes a table this rule does not name.\n"
ALERTS_LINE = "        if (r.table == Table.CALENDAR_ALERTS || r.table == Table.OTHER) return refused(Refusal.NOT_ALLOWED)\n"

M = [
    # ---- the calendar write guard (pure) ----
    ("G01", GUARD, "case 3: `path != SYNC` inverted to `path == SYNC`", "if (r.path != Path.SYNC) " + NA, "if (r.path == Path.SYNC) " + NA),
    ("G02", GUARD, "case 3: the path check removed (any path gets Sync's rule)", "if (r.path != Path.SYNC) " + NA, "if (false) " + NA),
    ("G03", GUARD, "case 3: the allow-list check removed", ALLOWED_LINE, ""),
    ("G05", GUARD, "T16-12: `mappingTarget != calendar.id` check removed", STALE3.split("\n")[0] + "\n", ""),
    ("G06", GUARD, "T16-12: `copyCalendarId != mappingTarget` check removed", STALE3.split("\n")[1] + "\n", ""),
    ("G07", GUARD, "T16-12: `mapped && mappingTarget == null` check removed", STALE3.split("\n")[2] + "\n", ""),
    ("G08", GUARD, "r3 D5: threshold 500 -> 200", "const val ACCESS_CONTRIBUTOR = 500", "const val ACCESS_CONTRIBUTOR = 200"),
    ("G09", GUARD, "r3 D5: `<` -> `<=` (500 itself refused)", "if (calendar.accessLevel < ACCESS_CONTRIBUTOR)", "if (calendar.accessLevel <= ACCESS_CONTRIBUTOR)"),
    ("G10", GUARD, "r3 D5: the access-level check removed", D5_COMMENT + LEVEL_LINE, ""),
    ("G11", GUARD, "r3 D5: threshold 500 -> 499", "const val ACCESS_CONTRIBUTOR = 500", "const val ACCESS_CONTRIBUTOR = 499"),
    ("G12", GUARD, "reorder: the allow-list check before the three stale checks", STALE3 + ALLOWED_LINE, ALLOWED_LINE + STALE3),
    ("G13", GUARD, "reorder: the access-level check before the allow-list check", ALLOWED_LINE + D5_COMMENT + LEVEL_LINE, LEVEL_LINE + ALLOWED_LINE),
    ("G14", GUARD, "reorder: the alerts/other-table refusal before the receiver's case", RECEIVER_BLOCK + ALERTS_COMMENT + ALERTS_LINE, ALERTS_LINE + RECEIVER_BLOCK),
    ("G15", GUARD, "case 3: the sync-adapter-URI refusal removed", "        if (r.viaSyncAdapter) " + NA + "\n", ""),
    ("G16", GUARD, "case 3: the LOCAL-target refusal removed", "        if (calendar.accountType == ACCOUNT_TYPE_LOCAL) " + NA + "\n", ""),
    ("G17", GUARD, "case 3: the source-in-Tessera check removed", "        if (!sync.sourceInTessera) " + NA + "\n", ""),
    ("G18", GUARD, "case 3: the unmapped-row check removed", "        if (!firstInsert && !sync.mapped) " + NA + "\n", ""),
    ("G19", GUARD, "case 3: the table check removed", "        if (r.table != Table.EVENTS && r.table != Table.REMINDERS) " + NA + "\n", ""),
    ("G20", GUARD, "case 1: reminders through the sync-adapter URI allowed",
     "allowIf(r.table == Table.EVENTS || (r.table == Table.REMINDERS && !r.viaSyncAdapter))", "allowIf(r.table == Table.EVENTS || r.table == Table.REMINDERS)"),
    ("G21", GUARD, "case 1: the editor / Tess may write any table of Tessera",
     "allowIf(r.table == Table.EVENTS || (r.table == Table.REMINDERS && !r.viaSyncAdapter))", "allowIf(true)"),
    ("G22", GUARD, "case 1: Tessera's creation without the sync-adapter URI allowed",
     "allowIf(r.table == Table.CALENDARS && r.op == Op.INSERT && r.viaSyncAdapter)", "allowIf(r.table == Table.CALENDARS && r.op == Op.INSERT)"),
    ("G39", GUARD, "case 1: LOCAL_CALENDAR may update / delete the Tessera calendar row",
     "allowIf(r.table == Table.CALENDARS && r.op == Op.INSERT && r.viaSyncAdapter)", "allowIf(r.table == Table.CALENDARS && r.viaSyncAdapter)"),
    ("G23", GUARD, "case 2: the Birthdays path check removed", "r.path == Path.BIRTHDAYS && r.viaSyncAdapter &&", "r.viaSyncAdapter &&"),
    ("G24", GUARD, "case 2: the sync-adapter-URI requirement removed", "r.path == Path.BIRTHDAYS && r.viaSyncAdapter &&", "r.path == Path.BIRTHDAYS &&"),
    ("G25", GUARD, "case 2: reminder rows allowed (T16-4)", "(r.table == Table.CALENDARS && r.op == Op.INSERT) || r.table == Table.EVENTS),",
     "(r.table == Table.CALENDARS && r.op == Op.INSERT) || r.table == Table.EVENTS || r.table == Table.REMINDERS),"),
    ("G38", GUARD, "case 2: the Birthdays writer may update / delete its calendar row", "(r.table == Table.CALENDARS && r.op == Op.INSERT) || r.table == Table.EVENTS),",
     "r.table == Table.CALENDARS || r.table == Table.EVENTS),"),
    ("G26", GUARD, "case 4: `columns == {state}` -> `state in columns`", "r.columns == setOf(ALERT_STATE_COLUMN)", "ALERT_STATE_COLUMN in r.columns"),
    ("G27", GUARD, "case 4: the op check removed", "val stateOnly = r.op == Op.UPDATE && r.table", "val stateOnly = r.table"),
    ("G28", GUARD, "case 4: the sync-adapter-URI refusal removed", "r.columns == setOf(ALERT_STATE_COLUMN) && !r.viaSyncAdapter", "r.columns == setOf(ALERT_STATE_COLUMN)"),
    ("G29", GUARD, "a calendar that cannot be read is allowed (fail open)",
     "val calendar = r.calendar ?: return refused(if (r.path == Path.SYNC) Refusal.CALENDAR_GONE else Refusal.NOT_ALLOWED)", "val calendar = r.calendar ?: return Verdict.Allowed"),
    ("G30", GUARD, "isTessera: the account-type half dropped (any account named Tessera)",
     "fun isTessera(c: CalendarFacts): Boolean = c.accountType == ACCOUNT_TYPE_LOCAL && c.accountName == TESSERA_ACCOUNT", "fun isTessera(c: CalendarFacts): Boolean = c.accountName == TESSERA_ACCOUNT"),
    ("G31", GUARD, "isTessera: the account-name half dropped (any LOCAL calendar)",
     "fun isTessera(c: CalendarFacts): Boolean = c.accountType == ACCOUNT_TYPE_LOCAL && c.accountName == TESSERA_ACCOUNT", "fun isTessera(c: CalendarFacts): Boolean = c.accountType == ACCOUNT_TYPE_LOCAL"),
    ("G32", GUARD, "isBirthdays: the account-type half dropped",
     "fun isBirthdays(c: CalendarFacts): Boolean = c.accountType == ACCOUNT_TYPE_LOCAL && c.accountName == BIRTHDAYS_ACCOUNT", "fun isBirthdays(c: CalendarFacts): Boolean = c.accountName == BIRTHDAYS_ACCOUNT"),
    ("G33", GUARD, "the alerts / other-table refusal removed", ALERTS_COMMENT + ALERTS_LINE, ""),
    ("G34", GUARD, "case 3: firstInsert drops its table condition", "val firstInsert = r.op == Op.INSERT && r.table == Table.EVENTS && !sync.mapped", "val firstInsert = r.op == Op.INSERT && !sync.mapped"),
    ("G35", GUARD, "case 3: firstInsert drops its op condition", "val firstInsert = r.op == Op.INSERT && r.table == Table.EVENTS && !sync.mapped", "val firstInsert = r.table == Table.EVENTS && !sync.mapped"),
    ("G36", GUARD, "case 3: a Sync write with no facts treated as a first push", "val sync = r.sync ?: " + NA, "val sync = r.sync ?: SyncFacts(true, false, true, null, null)"),
    # ---- the calendar write layer, Sync and the store's rules ----
    ("W01", WRITES, "syncRequest: targetAllowed no longer reads the allowed list",
     "targetAllowed = calendar != null && CalendarKey(calendar.id, calendar.accountName.orEmpty(), calendar.accountType.orEmpty()) in state.allowed,", "targetAllowed = calendar != null,"),
    ("W02", WRITES, "syncRequest: targetAllowed compares the calendar _ID only (not account name + type)",
     "targetAllowed = calendar != null && CalendarKey(calendar.id, calendar.accountName.orEmpty(), calendar.accountType.orEmpty()) in state.allowed,",
     "targetAllowed = calendar != null && state.allowed.any { it.id == calendar.id },"),
    ("W03", WRITES, "syncRequest: the stale-mapping re-read removed (copyCalendarId taken from the mapping)",
     "copyCalendarId = mapping?.let { eventRow(context, it.copyEventId)?.first },", "copyCalendarId = mapping?.target?.id,"),
    ("W04", WRITES, "syncRequest: sourceInTessera no longer checks the source's calendar",
     "sourceInTessera = source != null && CalendarWriteGuard.isTessera(source),", "sourceInTessera = source != null,"),
    ("W05", WRITES, "syncRequest: any row counts as mapped once a mapping exists",
     "val mapped = mapping != null && rowEventId != null && (rowEventId == mapping.copyEventId || row?.second == mapping.copyEventId)", "val mapped = mapping != null && rowEventId != null"),
    ("W08", WRITES, "the write layer's gate ignores the guard's verdict (every write goes through)",
     "when (val verdict = CalendarWriteGuard.check(request)) {", "when (val verdict: Verdict = Verdict.Allowed.also { CalendarWriteGuard.check(request) }) {"),
    ("W09", WRITES, "deleteEvent asks the guard about Tessera instead of the row's re-read calendar",
     "        val calendar = facts(context, row.first)\n        if (row.second != null) {",
     "        val calendar: CalendarFacts? = CalendarFacts(row.first, CalendarWriteGuard.TESSERA_ACCOUNT, CalendarWriteGuard.ACCOUNT_TYPE_LOCAL, 700)\n        if (row.second != null) {"),
    ("W06", SYNC, "push: the target's account name + type no longer compared with the re-read calendar",
     "if (present == null || present.key != target) return Outcome.CalendarGone", "if (present == null) return Outcome.CalendarGone"),
    ("W07", SYNC, "push: the guard pre-check (syncCheck) removed",
     "        (CalendarWrites.syncCheck(context, localEventId, target) as? Verdict.Refused)?.let { return outcomeOf(it.why) }\n", ""),
    ("W11", SYNC, "delete: Tess may delete 'here and from <calendar>'", "            if (path != Path.EDITOR) return WriteResult.Refused(Refusal.NOT_ALLOWED)\n", ""),
    ("W16", SYNC, "the Sync picker lists every candidate, allowed or not",
     "= candidates(calendars).filter { it.key in state.allowed }", "= candidates(calendars)"),
    ("W22", SYNC, "the marker offers delete-'both' for a target no longer allowed",
     "targetAllowed = present != null && mapping.target in state.allowed,", "targetAllowed = present != null,"),
    ("W12", STATE, "prune keeps a calendar that left the phone on the allowed list",
     "        allowed = state.allowed.filter { it in present },", "        allowed = state.allowed,"),
    ("W13", STATE, "setAllowed inverted", "state.copy(allowed = if (allowed) (state.allowed - key) + key else state.allowed - key)",
     "state.copy(allowed = if (!allowed) (state.allowed - key) + key else state.allowed - key)"),
    ("W14", READS, "Can sync to: the access-level floor dropped",
     "get() = accountType != CalendarWriteGuard.ACCOUNT_TYPE_LOCAL && accessLevel >= CalendarWriteGuard.ACCESS_CONTRIBUTOR", "get() = accountType != CalendarWriteGuard.ACCOUNT_TYPE_LOCAL"),
    ("W15", READS, "Can sync to: LOCAL calendars listed",
     "get() = accountType != CalendarWriteGuard.ACCOUNT_TYPE_LOCAL && accessLevel >= CalendarWriteGuard.ACCESS_CONTRIBUTOR", "get() = accessLevel >= CalendarWriteGuard.ACCESS_CONTRIBUTOR"),
    ("W17", READS, "the always-running observer no longer prunes the allowed list",
     "val after = store.update { SyncStateRules.prune(it, present) }", "val after = store.update { it }"),
    ("W19", LOCAL, "r3 D7: a FAILED lookup creates another Tessera calendar", "is Lookup.Failed -> null", "is Lookup.Failed -> create(context)"),
    ("W10", ACTION, "r3 D1: Tess's delete matches a title on every calendar", "val mine = events.filter { it.calendarId == local }", "val mine = events"),
    # ---- the People write guard (pure) ----
    ("P01", PGUARD, "'every raw contact editable' -> 'any'",
     "return if (blocked == null) GuardVerdict.Allowed else GuardVerdict.Refused(blocked.id)",
     "return if (blocked == null || raws.any { editable(it, policy) }) GuardVerdict.Allowed else GuardVerdict.Refused(blocked.id)"),
    ("P02", PGUARD, "another profile's contact no longer refused",
     "fun editable(raw: RawRef, policy: EditPolicy): Boolean = !raw.otherProfile && policy.canWrite(raw.account)", "fun editable(raw: RawRef, policy: EditPolicy): Boolean = policy.canWrite(raw.account)"),
    ("P03", PGUARD, "canWrite: the Can edit half removed (allowed accounts refused)",
     "fun canWrite(account: ContactAccount): Boolean = isPhone(account) || account in allowed", "fun canWrite(account: ContactAccount): Boolean = isPhone(account)"),
    ("P04", PGUARD, "canWrite: the phone half removed", "fun canWrite(account: ContactAccount): Boolean = isPhone(account) || account in allowed",
     "fun canWrite(account: ContactAccount): Boolean = account in allowed"),
    ("P05", PGUARD, "canWrite: every account writable (the allow-list check removed)",
     "fun canWrite(account: ContactAccount): Boolean = isPhone(account) || account in allowed", "fun canWrite(account: ContactAccount): Boolean = true"),
    ("P06", PGUARD, "isPhone: a literal null in place of the device's local account",
     "fun isPhone(account: ContactAccount): Boolean = account == local", "fun isPhone(account: ContactAccount): Boolean = account.isNull"),
    ("P07", PGUARD, "a SIM import may land in an allowed account", "NewContactSource.SIM_IMPORT -> if (policy.isPhone(write.account))", "NewContactSource.SIM_IMPORT -> if (policy.canWrite(write.account))"),
    ("P08", PGUARD, "a new contact / INSERT prefill allowed in any account",
     "NewContactSource.EDITOR, NewContactSource.INSERT_PREFILL ->\n                if (policy.canWrite(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)",
     "NewContactSource.EDITOR, NewContactSource.INSERT_PREFILL ->\n                GuardVerdict.Allowed"),
    ("P09", PGUARD, "a group op allowed in any account",
     "is PeopleWrite.GroupRow -> if (policy.canWrite(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)", "is PeopleWrite.GroupRow -> GuardVerdict.Allowed"),
    ("P10", PGUARD, "an aggregate with no raw contact resolved is allowed", "        if (raws.isEmpty()) return GuardVerdict.Refused(null)\n", ""),
    ("P11", PGUARD, "a data-row write allowed on any raw contact", "is PeopleWrite.DataRow -> one(write.raw, policy)", "is PeopleWrite.DataRow -> GuardVerdict.Allowed"),
    ("P12", PGUARD, "a raw-contact-row write allowed on any raw contact", "is PeopleWrite.RawContactRow -> one(write.raw, policy)", "is PeopleWrite.RawContactRow -> GuardVerdict.Allowed"),
    ("P14", PGUARD, "an upstream Contacts column (STARRED) allowed on any contact", "is PeopleWrite.ContactColumn -> all(write.raws, policy)", "is PeopleWrite.ContactColumn -> GuardVerdict.Allowed"),
    ("P15", PGUARD, "Link / Unlink refused on a read-only contact (the allowed half)", "is PeopleWrite.Aggregation -> GuardVerdict.Allowed", "is PeopleWrite.Aggregation -> one(write.a, policy)"),
    ("P16", PGUARD, "Can edit: prune keeps an account the provider no longer names",
     "return allowed.filterTo(LinkedHashSet()) { it in still }", "return allowed.filterTo(LinkedHashSet()) { true || it in still }"),
    ("P17", PGUARD, "Can edit: an account with no name or type can be ticked", "account.name == null || account.type == null -> allowed", "false -> allowed"),
    ("P18", PGUARD, "the card offers Delete when ANY raw contact is editable",
     "delete = raws.isNotEmpty() && editableCount == raws.size,", "delete = raws.isNotEmpty() && editableCount > 0,"),
    ("P19", PGUARD, "the card offers Edit on a read-only contact", "edit = editableCount > 0,", "edit = true,"),
    # ---- the People write layer ----
    ("PW1", PWRITER, "update(): the per-raw-contact guard loop removed",
     "        for (s in steps) if (refused(context, PeopleWrite.DataRow(s.op, raws.getValue(s.rawId)))) return refusedLine(\"update\", s.rawId.toString())\n", ""),
    ("PW2", PWRITER, "delete(): the guard's refusal ignored",
     "        if (verdict is GuardVerdict.Refused) return refusedLine(\"delete\", (verdict.rawId ?: raws.first().id).toString())\n", ""),
    ("PW3", PWRITER, "create(): the guard not asked",
     "        if (refused(context, PeopleWrite.NewRawContact(account, source))) return refusedLine(\"insert\", \"new\")\n", ""),
    ("PW4", PWRITER, "the write layer's `refused` helper always says allowed",
     "PeopleWriteGuard.check(write, PeopleEditStore.policy(context)) is GuardVerdict.Refused", "PeopleWriteGuard.check(write, PeopleEditStore.policy(context)) is GuardVerdict.Refused && false"),
]

def git(*a):
    return subprocess.run(["git", "-C", W, *a], capture_output=True, text=True)

def results():
    t = f = 0
    failed = []
    for p in glob.glob(W + "/app/build/test-results/testDebugUnitTest/*.xml"):
        r = ET.parse(p).getroot()
        t += int(r.get("tests")); f += int(r.get("failures")) + int(r.get("errors"))
        for tc in r.findall("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                failed.append(r.get("name").split(".")[-1] + "." + tc.get("name"))
    return t, f, failed

only = set(sys.argv[1:])
ids = [m[0] for m in M]
assert len(ids) == len(set(ids)), "duplicate ids"
tsv = open(S + "/trustA-mutations.tsv", "a")
for mid, rel, what, old, new in M:
    if only and mid not in only:
        continue
    assert git("status", "--porcelain").stdout.strip() == "", "worktree not clean before " + mid
    path = os.path.join(W, rel)
    src = open(path).read()
    n = src.count(old)
    if n != 1:
        tsv.write(f"{mid}\tINVALID (old text found {n} times)\t-\t-\t{what}\t-\n"); tsv.flush(); continue
    open(path, "w").write(src.replace(old, new))
    shutil.rmtree(W + "/app/build/test-results/testDebugUnitTest", ignore_errors=True)
    out = f"{S}/trustA-mut-{mid}.out"
    t0 = time.time()
    with open(out, "w") as o:
        rc = subprocess.run(["./gradlew", ":app:testDebugUnitTest"], cwd=W, stdout=o, stderr=subprocess.STDOUT).returncode
    open(f"{S}/trustA-mut-{mid}.rc", "w").write(str(rc) + "\n")
    log = open(out).read()
    if "compileDebugKotlin FAILED" in log or "compileDebugUnitTestKotlin FAILED" in log:
        verdict, t, f, failed = "COMPILE-ERROR (mutation invalid)", 0, 0, []
    else:
        t, f, failed = results()
        verdict = "CAUGHT" if rc != 0 and f > 0 else ("NOT CAUGHT" if rc == 0 and f == 0 and t > 0 else f"UNCLEAR rc={rc}")
    restore = git("checkout", "--", rel)
    clean = git("status", "--porcelain").stdout.strip() == ""
    tsv.write(f"{mid}\t{verdict}\trc={rc}\ttests={t} failed={f}\t{what}\t{'; '.join(sorted(failed)[:6])}{' …' if len(failed) > 6 else ''}\t{int(time.time()-t0)}s\trestored={'yes' if restore.returncode == 0 and clean else 'NO'}\n")
    tsv.flush()
    assert clean, "worktree not clean after " + mid
tsv.write("DONE\n"); tsv.close()
