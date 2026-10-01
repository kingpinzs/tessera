import subprocess, sys, os, glob, json, xml.etree.ElementTree as ET
W='/home/jeremyking/projects/metro-launcher-p16/.claude/worktrees/trustB'
S='/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad'
K='app/src/main/kotlin/app/tileshell/'
CR=K+'calendar/CalendarRoute.kt'; PR=K+'people/PeopleRoute.kt'
REM=K+'calendar/CalendarReminders.kt'; READS=K+'calendar/CalendarReads.kt'; ST=K+'calendar/CalendarSyncState.kt'; G=K+'calendar/CalendarWriteGuard.kt'
PA=K+'people/PeopleActivity.kt'; PACT=K+'people/PeopleActions.kt'; CAPP=K+'calendar/CalendarApp.kt'; CA=K+'calendar/CalendarActivity.kt'
MAN='app/src/main/AndroidManifest.xml'
M=[
 # ---- CalendarIntents
 ('C1 title/notes length cap removed', CR, 'value?.trim()?.take(max)?.takeIf { it.isNotEmpty() }', 'value?.trim()?.takeIf { it.isNotEmpty() }', 1),
 ('C2 time(): upper bound removed', CR, 'ms?.takeIf { it in 0..MAX_MS }', 'ms?.takeIf { it >= 0 }', 1),
 ('C3 time(): lower bound removed', CR, 'ms?.takeIf { it in 0..MAX_MS }', 'ms?.takeIf { it <= MAX_MS }', 1),
 ('C4 authority prefix without the trailing slash', CR, 'val prefix = "content://$AUTHORITY/"', 'val prefix = "content://$AUTHORITY"', 1),
 ('C5 EDIT accepts an id <= 0', CR, '?.get(1)?.toLongOrNull()?.takeIf { it > 0 }', '?.get(1)?.toLongOrNull()', 1),
 ('C6 INSERT by type even when the data is a foreign URI', CR, '(data == null && type == TYPE_EVENT_DIR)', '(type == TYPE_EVENT_DIR)', 1),
 ('C7 INSERT keeps an end before its start', CR, 'time(extras.long(EXTRA_END))?.takeIf { begin != null && it >= begin }', 'time(extras.long(EXTRA_END))', 1),
 ('C8 allDay true unless the caller says false', CR, 'extras.boolean(EXTRA_ALL_DAY) == true', 'extras.boolean(EXTRA_ALL_DAY) != false', 1),
 ('C9 VIEW accepts an event id <= 0', CR, 'path[1].toLongOrNull()?.takeIf { it > 0 }', 'path[1].toLongOrNull()', 1),
 ('C10 EDIT on any two-segment path (time URI too)', CR, 'path?.takeIf { it.size == 2 && it[0] == "events" }', 'path?.takeIf { it.size == 2 }', 1),
 ('C11 fragment not stripped from the path', CR, ".substringBefore('?').substringBefore('#')", ".substringBefore('?')", 1),
 ('C12 query not stripped from the path', CR, ".substringBefore('?').substringBefore('#')", ".substringBefore('#')", 1),
 ('C13 prefill text not trimmed', CR, 'value?.trim()?.take(max)?.takeIf { it.isNotEmpty() }', 'value?.take(max)?.takeIf { it.isNotEmpty() }', 1),
 ('C14 INSERT on any calendar-provider path', CR, '(path != null && path == listOf("events"))', '(path != null)', 1),
 ('C15 VIEW event: begin/end extras not range-checked', CR, 'CalendarRoute.Event(it, time(extras.long(EXTRA_BEGIN)), time(extras.long(EXTRA_END)))', 'CalendarRoute.Event(it, extras.long(EXTRA_BEGIN), extras.long(EXTRA_END))', 1),
 ('C16 VIEW on an event routes to the editor (Edit)', CR, '?.let { CalendarRoute.Event(it, time(extras.long(EXTRA_BEGIN)), time(extras.long(EXTRA_END))) } ?: open(extras)', '?.let { CalendarRoute.Edit(it) } ?: open(extras)', 1),
 # ---- PeopleIntents
 ('P1 field length cap removed', PR, 'value?.trim()?.take(max)?.takeIf { it.isNotEmpty() }', 'value?.trim()?.takeIf { it.isNotEmpty() }', 1),
 ('P2 contacts/<id> accepts id <= 0', PR, 'path.size == 2 -> path[1].toLongOrNull()?.takeIf { it > 0 }?.let', 'path.size == 2 -> path[1].toLongOrNull()?.let', 1),
 ('P3 lookup key length bound removed', PR, ' && path[2].length <= MAX_FIELD ->', ' ->', 1),
 ('P4 any PICK is a contact pick', PR, 'type == TYPE_CONTACT_DIR || path == listOf("contacts") -> PeopleRoute.Pick(PickKind.CONTACT)', 'true -> PeopleRoute.Pick(PickKind.CONTACT)', 1),
 ('P5 a phone PICK answers as a contact PICK', PR, 'path == listOf("data", "phones") -> PeopleRoute.Pick(PickKind.PHONE)', 'path == listOf("data", "phones") -> PeopleRoute.Pick(PickKind.CONTACT)', 1),
 ('P6 authority prefix without the trailing slash', PR, 'val prefix = "content://$AUTHORITY/"', 'val prefix = "content://$AUTHORITY"', 1),
 ('P7 INSERT by type even when the data is a foreign URI', PR, '(data == null && type == TYPE_CONTACT_DIR)', '(type == TYPE_CONTACT_DIR)', 1),
 ('P8 INSERT_OR_EDIT whatever the type or data', PR, 'if (type == TYPE_CONTACT_ITEM || path == listOf("contacts")) PeopleRoute.InsertOrEdit(prefill(extras)) else open(extras)', 'PeopleRoute.InsertOrEdit(prefill(extras))', 1),
 ('P9 lookup/<key>/<id> accepts id <= 0', PR, 'path.getOrNull(3)?.toLongOrNull()?.takeIf { it > 0 }', 'path.getOrNull(3)?.toLongOrNull()', 1),
 ('P10 lookup path of any length >= 3', PR, 'path.size in 3..4 && path[1] == "lookup"', 'path.size >= 3 && path[1] == "lookup"', 1),
 ('P11 query not stripped from the path', PR, ".substringBefore('?').substringBefore('#')", ".substringBefore('#')", 1),
 ('P12 fragment not stripped from the path', PR, ".substringBefore('?').substringBefore('#')", ".substringBefore('?')", 1),
 ('P13 contacts/<x>/<y> taken as a lookup key whatever <x> is', PR, 'path.size in 3..4 && path[1] == "lookup" && path[2].isNotEmpty()', 'path.size in 3..4 && path[2].isNotEmpty()', 1),
 ('P14 VIEW on a contact routes to Edit', PR, 'ACTION_VIEW -> contact(path)?.let { PeopleRoute.Card(it) }', 'ACTION_VIEW -> contact(path)?.let { PeopleRoute.Edit(it) }', 1),
 ('P15 INSERT on any contacts-provider path', PR, 'if (path == listOf("contacts") || path == listOf("raw_contacts") || (data == null && type == TYPE_CONTACT_DIR)) PeopleRoute.Insert(prefill(extras))', 'if (path != null || (data == null && type == TYPE_CONTACT_DIR)) PeopleRoute.Insert(prefill(extras))', 1),
 ('P16 prefill text not trimmed', PR, 'value?.trim()?.take(max)?.takeIf { it.isNotEmpty() }', 'value?.take(max)?.takeIf { it.isNotEmpty() }', 1),
 # ---- the receiver's rules
 ('R1 plan ignores the notified set', REM, 'due.filter { it.key !in notified }.map {', 'due.map {', 1),
 ('R2 plan notifies a synced copy', REM, 'if (it.eventId in copies) Action.SkipCopy(it) else Action.Notify(it)', 'Action.Notify(it)', 1),
 ('R3 plan: copy test inverted', REM, 'if (it.eventId in copies) Action.SkipCopy(it)', 'if (it.eventId !in copies) Action.SkipCopy(it)', 1),
 ('R4 alert key without the alarm time', READS, 'get() = "$eventId:$beginMs:$alarmTimeMs"', 'get() = "$eventId:$beginMs"', 1),
 ('R5 alert key without the begin', READS, 'get() = "$eventId:$beginMs:$alarmTimeMs"', 'get() = "$eventId:$alarmTimeMs"', 1),
 ('R6 alert key on the row id', READS, 'get() = "$eventId:$beginMs:$alarmTimeMs"', 'get() = "$id:$beginMs:$alarmTimeMs"', 1),
 ('R7 notified set never shrinks', ST, '(state.notifiedAlerts intersect live) + added', 'state.notifiedAlerts + added', 1),
 ('R8 notified set = every live alert (a failed post is recorded too)', ST, '(state.notifiedAlerts intersect live) + added', 'live + added', 1),
 ('R9 notified set drops what was just added', ST, '(state.notifiedAlerts intersect live) + added', '(state.notifiedAlerts intersect live)', 1),
 ('G1 receiver may set any columns', G, ' && r.columns == setOf(ALERT_STATE_COLUMN) && !r.viaSyncAdapter', ' && !r.viaSyncAdapter', 1),
 ('G2 receiver may use the sync-adapter URI', G, ' && r.columns == setOf(ALERT_STATE_COLUMN) && !r.viaSyncAdapter', ' && r.columns == setOf(ALERT_STATE_COLUMN)', 1),
 ('G3 receiver may insert/delete alerts', G, 'val stateOnly = r.op == Op.UPDATE && r.table == Table.CALENDAR_ALERTS', 'val stateOnly = r.table == Table.CALENDAR_ALERTS', 1),
 ('G4 receiver may update any table', G, 'val stateOnly = r.op == Op.UPDATE && r.table == Table.CALENDAR_ALERTS', 'val stateOnly = r.op == Op.UPDATE', 1),
 ('G5 receiver may set state plus other columns', G, 'r.columns == setOf(ALERT_STATE_COLUMN)', 'ALERT_STATE_COLUMN in r.columns', 1),
 # ---- Android-side trust properties: is any JVM test watching?
 ('X1 PICK result grants write + persistable + prefix', PA, 'addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))', 'addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))', 1),
 ('X2 share stream = the whole contacts table', PACT, 'val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, card.lookup)', 'val uri = ContactsContract.Contacts.CONTENT_URI', 1),
 ('X3 reminder notification VISIBILITY_PUBLIC', REM, '.setVisibility(Notification.VISIBILITY_PRIVATE)', '.setVisibility(Notification.VISIBILITY_PUBLIC)', 1),
 ('X4 reminder PendingIntents mutable', REM, 'PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE', 'PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE', 2),
 ('X5 exported EDIT opens the editor for any calendar', CAPP, '} else if (calendar?.isTessera == true) {', '} else if (true) {', 1),
 ('X6 the caller\'s title written to the diagnostics ring', CA, 'is CalendarRoute.Insert -> "insert (prefilled, unsaved)"', 'is CalendarRoute.Insert -> "insert ${route.prefill.title}"', 1),
 ('X7 public (lock-screen) version carries the event title', REM, '.setContentTitle("Calendar reminder")', '.setContentTitle(EventRules.shownTitle(row.title))', 1),
 ('X8 the swipe receiver exported', MAN, 'android:name=".calendar.CalendarReminderDismissReceiver"\n            android:exported="false"', 'android:name=".calendar.CalendarReminderDismissReceiver"\n            android:exported="true"', 1),
]
only = sys.argv[1:] 
res=[]
def sh(cmd): return subprocess.run(cmd, shell=True, cwd=W, capture_output=True, text=True)
for (name, f, old, new, n) in M:
    mid=name.split()[0]
    if only and mid not in only: continue
    p=os.path.join(W,f); src=open(p).read()
    if src.count(old)!=n:
        res.append((name,'NOT APPLIED (count=%d)'%src.count(old),[])); print(res[-1],flush=True); continue
    open(p,'w').write(src.replace(old,new))
    diff=sh('git diff --stat -- '+f).stdout.strip().splitlines()[-1] if True else ''
    log=f'{S}/trustB-mut2-{mid}.log'; rcf=f'{S}/trustB-mut2-{mid}.rc'
    subprocess.run(f'./gradlew :app:testDebugUnitTest > {log} 2>&1; echo $? > {rcf}', shell=True, cwd=W)
    rc=open(rcf).read().strip()
    logt=open(log).read()
    failed=[]
    if 'testDebugUnitTest' in logt and ('> Task :app:testDebugUnitTest' in logt):
        t=0
        for x in glob.glob(W+'/app/build/test-results/testDebugUnitTest/*.xml'):
            r=ET.parse(x).getroot(); t+=int(r.get('tests'))
            for tc in r.findall('testcase'):
                if tc.find('failure') is not None or tc.find('error') is not None:
                    failed.append(tc.get('classname').split('.')[-1]+'.'+tc.get('name'))
        verdict=('CAUGHT' if failed else 'SURVIVED')+f' (gradle rc={rc}, {t} tests run, {len(failed)} failed)'
    else:
        verdict=f'COMPILE/BUILD FAILED before tests (gradle rc={rc})'
    sh('git checkout -- '+f)
    clean=sh('git status --short').stdout.strip()
    res.append((name,verdict,failed)); 
    print(f'{name} | {diff} | {verdict} | {"; ".join(failed[:6])}{" ..." if len(failed)>6 else ""} | tree-clean={clean==""}',flush=True)
json.dump(res,open(f'{S}/trustB-mut2-results.json','w'),indent=1)
