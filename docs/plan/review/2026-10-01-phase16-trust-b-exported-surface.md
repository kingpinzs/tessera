<!-- Saved verbatim by the lead from the reviewer's hand-back (an Opus subagent, adversarial mode; model output, not the owner's words). Brief: 2026-10-01-phase16-trust-brief.md. The lead's disposition of each finding is qa/phase-16/fix-round.md. -->

# Reviewer B — phase 16 exported surface, commit 83754009

**Bottom line:** no exported handler can save, delete, send, call or text without a tap, and no route reaches an account calendar or a non-allowed contact account. There are 4 findings at MEDIUM or above in the product, 3 MEDIUM test gaps, and several LOW ones. The one HIGH is the owner's Q-16-4 ruling, which is not built at this commit.

**State of the review.** The scratch worktree is clean at 83754009 and nothing was written to the review tree. The branch moved to 16be5a51 while I worked (docs only):
```
app/src diff between reviewed commit and the tree I read: 0 lines (0 = identical)
```
Baseline in trustB: `./gradlew :app:testDebugUnitTest` → rc `0`, `tests 1111 failures 0 errors 0 skipped 0`.

No emulator or adb was used. Everything below is code reading, JVM runs, the merged manifest, and the builders' captured device output where named.

## Findings

| # | Sev | What | Where |
|---|---|---|---|
| F1 | HIGH (known, unbuilt) | First poke notifies the whole alert backlog; Q-16-4 "a" is not in the code | `CalendarReads.kt:309-334`, `CalendarReminders.kt:100-136` |
| F2 | MEDIUM | A swipe marks DISMISSED by row id only; a reused id dismisses another event's alert | `CalendarReminders.kt:66-82,138-141`, `CalendarWrites.kt:476-483` |
| F3 | MEDIUM | The caller's action string is written verbatim to the diagnostics ring | `CalendarActivity.kt:166`, `PeopleActivity.kt:79` |
| F4 | MEDIUM | PICK's grant was never observed; the "granted (read)" line prints with no caller | `PeopleActivity.kt:91-100`, `dev-people/scripts/intents.sh:63-83` |
| F5 | MEDIUM | The lock-screen claim holds only when the phone hides sensitive content | `CalendarReminders.kt:172-188`, spec D6 (e), H7 |
| F6 | MEDIUM | Two receiver rules are unpinned by any test | `CalendarReads.kt:91`, `CalendarSyncState.kt:75` |
| F7 | MEDIUM | Eight Android-side trust properties have no JVM test | see item 2 |
| F8 | LOW | No coalescing or rate limit on pokes; ring spam; no catch in the worker | `CalendarReminders.kt:48-60,101-104,125-128` |
| F9 | LOW | Reminder notifications never expire; past Android's per-app cap a dropped one is still recorded as notified | `CalendarReminders.kt:125-131,154-191` |
| F10 | LOW | Eleven parser halves untested | `CalendarRoute.kt`, `PeopleRoute.kt` |
| F11 | LOW | A caller-chosen occurrence time is trusted for a repeating event | `CalendarRoute.kt:93-94`, `CalendarPages.kt:152,221,257-258` |
| F12 | LOW | Any new intent wipes an unsaved draft; pick mode can linger | `CalendarApp.kt:130-135`, `PeopleApp.kt:105-110` |
| F13 | LOW | A dynamic receiver is registered `RECEIVER_EXPORTED` without need | `CalendarActivity.kt:132-140` |

**F1 — first-poke backlog.** `remindersSince` is absent at 83754009 (`grep -rn remindersSince app/src` prints nothing). `dueAlerts` selects `alarmTime <= now AND state IN (SCHEDULED, FIRED)` with no lower bound and no LIMIT; `poke()` posts one notification per row.
- Probe on unmutated rules: `PROBE first poke: 500 due rows (SCHEDULED or FIRED), empty notified set -> 500 notifications planned`.
- The only bound is the provider's own retention, which I recall as about 7 days on AOSP; I could not confirm it here, and Samsung's is unknown.
- The channel is IMPORTANCE_HIGH with no group, so each one alerts.
- Any app can trigger it at will with a forged poke.
- An unreadable `calendar_sync.json` empties the notified set and repeats the burst (`CalendarSyncStore.kt:73-77`).
- Fix: build Q-16-4 as ruled, with `alarmTime >= remindersSince` in the selection.

**F2 — the swipe hits the wrong row.** `dismissed()` runs `update(calendar_alerts/<alertId>, state=DISMISSED)` with no check of event, begin or alarm time. The builder's own E6 run records the provider reusing a deleted alert's id: `the first alert's row id, the second's … 1 / 1 / 281 282`.
- Sequence: A's reminder is shown from row n; A's row is deleted (event moved or removed); B's future alert is given id n; the user swipes A's stale notification; B's SCHEDULED row becomes DISMISSED.
- Consequence: the shell's read then skips B. Other calendar apps would likely skip it too, and the provider not re-create it — that part is from memory of AOSP, not checked.
- B can be a work-calendar event. It is still "state only", so guard case 4 passes it.
- E6's reuse leg covers only the notify side.
- Fix: carry begin and alarmTime in the dismiss PendingIntent and update with `event_id=? AND begin=? AND alarmTime=? AND state IN (0,1)`.

**F3 — action string in the ring.** Both activities log `"open ${intent?.action ?: "no action"} -> …"`. An explicit intent to an exported activity may carry any action string. `describe()`'s comment claims caller text never reaches the ring; DEV-INTENTS checked only the name and number extras.
- Effect: forged ring lines (newlines), and unbounded strings held in a 4000-entry ring inside the launcher process.
- Nothing reaches logcat: there is no `Log.*` in `calendar/` or `people/`.
- Fix: log the route only, or the action only when it is one of the known constants.

**F4 — PICK is unproven.** The only driver uses `am start`, which has no caller; it passes on the shell's own line. The People builder's report says the same: "`am start` has no caller, so only the line and the finish were seen".
- `callingActivity` is never consulted (grep: none), so the line prints when nothing was granted.
- No JVM test pins the flags (mutation X1 survived).
- Fix: a caller test app row; enter pick mode and log "granted" only when `callingActivity != null`.

**F5 — lock screen.** The code sets `VISIBILITY_PRIVATE` and a public version titled only "Calendar reminder". Android shows that public version only when the lock screen is set to hide sensitive content; set to show all content, the event title and time appear.
- E6 asserts only `vis=PRIVATE`; the public version's content is unpinned (X7 survived).
- Decision for the owner: accept H7 with that condition written in, or switch to `VISIBILITY_SECRET`, which hides the reminder from the lock screen entirely. Nothing else on the app side makes it unconditional.

**F6 — unpinned receiver rules.**
- R4: the alarm-time part of the key. Without it a second reminder on one occurrence (60 min and 10 min) would never notify. The builder lists "two reminders on one event" as not proven.
- R8: a due alert whose post failed must not be recorded.
- R5: the begin part of the key (near-equivalent).
- My scratch tests kill all three; they are named in item 2.

**F8 – F13 fixes.**
- F8: drop a poke when one is queued; catch Throwable in the worker. Optional: `android:permission="android.permission.READ_CALENDAR"` on the receiver, if `dumpsys package com.android.providers.calendar` shows it granted on the AVD and the S25.
- F9: cancel notifications whose key left `live`, or `setTimeoutAfter`.
- F10: add the probe tests.
- F11: accept `beginTime` only when Instances holds that occurrence.
- F12: treat PICK without a caller as Browse.
- F13: `RECEIVER_NOT_EXPORTED`. The three actions are protected broadcasts, so it is unreachable today.

## Item 2 — mutation proof of the parser and receiver tests: FINDING

54 mutations, one at a time, whole suite each, rc captured to a file, file restored after each. 32 caught, 22 survived.

Caught (sample lines):
```
C1 title/notes length cap removed | CAUGHT (gradle rc=1, 1111 tests run, 1 failed) | CalendarIntentsTest.insertBoundsWhatACallerSuggests
P4 any PICK is a contact pick | CAUGHT (gradle rc=1, 1111 tests run, 1 failed) | PeopleIntentsTest.pickNamesOnlyWhatKindOfUriGoesBack
R6 alert key on the row id | CAUGHT (gradle rc=1, 1111 tests run, 1 failed) | CalendarRulesTest.aNewAlertThatInheritedAHandledRowsIdStillNotifies
G1 receiver may set any columns | CAUGHT (gradle rc=1, 1111 tests run, 2 failed) | CalendarWriteGuardTest.everyCombinationOutsideTheFourCasesIsRefused; …anyOtherWriteByTheReceiverIsRefused
```
The full caught set is C1–C3, C5, C7–C10, C12–C14, C16; P1–P5, P10, P14–P16; R1–R3, R6, R7, R9; G1–G5.

Survived (each `SURVIVED (gradle rc=0, 1111 tests run, 0 failed)`):

| Id | Mutation |
|---|---|
| C4 / P6 | authority prefix without the trailing slash |
| C6 / P7 | INSERT by type when the data is a foreign URI |
| C11 / P12 | fragment not stripped |
| P11 | query not stripped (People) |
| C15 | VIEW's begin/end extras not range-checked |
| P8 | INSERT_OR_EDIT whatever the type or data |
| P9 | `lookup/<key>/<id>` accepts id ≤ 0 |
| P13 | `contacts/<x>/<y>` taken as a lookup key |
| R4 | alert key without the alarm time |
| R5 | alert key without the begin |
| R8 | notified set = every live alert |
| X1 | PICK result also grants write, persistable, prefix |
| X2 | share stream = the whole contacts table |
| X3 | reminder `VISIBILITY_PUBLIC` |
| X4 | reminder PendingIntents mutable |
| X5 | exported EDIT opens the editor for any calendar |
| X6 | the caller's title written to the ring |
| X7 | public version carries the event title |
| X8 | swipe receiver `exported="true"` |

- I wrote 15 scratch tests (`TrustBProbeTest`, since deleted): 15/0 on unmutated code, and with them every C/P/R survivor is caught (`1126 tests run, 1 failed`, each naming its probe).
- X1–X8 rest on device rows only. X1, X4, X6 and X7 have none. X3 has E6's `vis=PRIVATE`; X2 has DEV-E15; X5 has E22; X8 has the allow-list comparison.
- Tests that pin something false: `intents.sh` passes on the "granted (read)" line with no caller. `CalendarIntentsTest.aQueryOrFragmentOnTheUriIsNotPartOfTheId` tests only the query.

## Item 5 — exported handlers: PROVEN by code, with F3, F4, F11, F12

**No tap-less save, delete, send, call or text.** Every call site sits in a tap handler:
```
CalendarPages.kt:152  CalendarEvents.delete(…)        (Delete button, then the prompt)
CalendarPages.kt:177,499  CalendarSync.sync(…)        (Sync button / target row)
CalendarPages.kt:349  CalendarEvents.save(…)          (cal_editor_save)
PeopleEditorPage.kt:234,241  PeopleWriter.create/update   (people_editor_save)
PeopleCardPage.kt:96  PeopleWriter.delete             (people_delete_confirm)
PeopleActions.kt:42  placeCall   :87 chooser   :119 startActivity   (card rows, share menu)
PeopleActivity.kt:96  setResult                       (row tap)
```
What a zero-permission caller does cause without a tap:
- Starting `CalendarActivity` creates the shell's own `Tessera` LOCAL calendar if it is absent and WRITE is held (`CalendarModel.kt:144-171`, guard case 1).
- A forged poke posts notifications for due alerts and marks those rows FIRED.
- The shell's own two json files are pruned.
- A draft is discarded (F12); a ring line is written (F3).

**INSERT.**
- `EventPrefill` has no calendar field; Save uses `model.ensureLocal()`.
- `ContactPrefill` has no account field; the draft starts on `repo.local`.
- Text is capped (C1 and P1 caught).

**EDIT.**
- Calendar: `calendar?.isTessera == true` decides (`CalendarApp.kt:250`); a null read is read-only.
- People: `cardActions(...).edit` decides; a failed raw-contact read gives an empty list, so the card.

**Hostile URIs** (probe output on real code):
```
VIEW content://com.android.calendar/events/../time/5 -> Open(page=null)   EDIT -> Open(page=null)
VIEW content://com.android.calendar/events/%2e%2e -> Open(page=null)
VIEW content://com.android.contacts/contacts/lookup/../5 -> Card(contact=ContactRef(encodedLookupKey=.., id=5))
VIEW content://com.android.contacts/data/7 -> Open(page=null)
INSERT wrong-typed extras -> Insert(prefill=EventPrefill(title=null, …, allDay=false))
```
- Foreign authorities, userinfo and port forms all open the default page (probes c4 and p6 pass).
- The `..` lookup key goes to the provider as one segment and resolves to nothing.
- Ids are bound as query arguments.

**Hostile Parcelable.** Extras are read only inside `runCatching { intent.extras?.get(key) }`, and nothing else in either activity reads extras.

**PICK.**
- Result: `Intent().setData(uri).addFlags(FLAG_GRANT_READ_URI_PERMISSION)` — no ClipData, no other flag.
- URI: `Contacts.getLookupUri(row.id, row.lookup)`, or `content://com.android.contacts/data/<dataId>` for a phone.
- The route carries only the kind, so the caller cannot shape the URI. Work-profile rows are not listed in pick mode (`PeopleListPage.kt:423`).
- With `startActivityForResult` and no NEW_TASK, I expect Android to create a second instance in the caller's task and deliver the result; that is from memory of the platform and unverified on a device.
- With NEW_TASK or a plain `startActivity`, no result reaches anyone: the main instance enters pick mode, a tap finishes People with nothing granted, and the mode stays until the next intent. It also stays through Recents, and returns after a process restart because `setIntent` kept the PICK.

## Item 6 — the receiver: PROVEN for the intent and PendingIntents, with F1, F2, F5, F8, F9

- **Nothing is read from the intent.** `onReceive` never touches `intent` (`CalendarReminders.kt:49-59`). The swipe receiver reads three extras of its own PendingIntent and is `exported=false` in the merged manifest.
- **Any app can send the poke.** EVENT_REMINDER is not a protected broadcast on platform 36: `EVENT_REMINDER in platform-36 protected-broadcast list: 0`.
- **Per-poke work.** One unbounded alerts query; then per fresh row one notification, one state update and one ring line. No coalescing.
- **Stored set.** `(notified ∩ live) + added`, so never larger than the due rows; R7 is caught.
- **A forged flood** with nothing fresh costs one provider query each and no file write. It adds one ring line per poke while READ is denied, and N lines per poke while notifications are off.
- **PendingIntents.** Both are `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` and explicit (`setClass`, `Intent(context, …DismissReceiver)`).
- **Only provider write:** `calendar_alerts.state`.
- **Change Log claim "tapping opens the event":** true, and the row then stays FIRED, since a tap does not fire the delete intent.

## Item 7 — the share: PROVEN by code and the builder's device output; no JVM pin (X2)

- Stream: `Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, card.lookup)`, where `card.lookup` is the provider's own LOOKUP_KEY re-read in `PeopleData.card`, never the intent's key.
- Flags: `FLAG_GRANT_READ_URI_PERMISSION` only, plus `ClipData.newRawUri` of that one URI.
- Not offered on a work-profile card (`PeopleCardPage.kt:111`).
- Builder's device output (dev builds; `PeopleActions.kt` unchanged since 10:47):
```
UriPermission{… content://com.android.contacts/contacts/as_vcard/0r117-2B4545413333 [user 0]} … targetPkg=com.android.intentresolver mode=0x1 owned=0x1 global=0x0 persistable=0x0
RECORD URI grants naming as_vcard after the choice  UriPermission{… as_vcard/0r70-2B4545413333 …} … targetPkg=org.fossify.notes mode=0x1 … persistable=0x0
```
- So: read-only, one URI, not persistable, to the share sheet and then to the app the user picks.

## Item 8 — fail-open paths in my lens: PROVEN deny

- A null calendar read gives read-only.
- A failed raw-contacts read gives the card, not the editor.
- A failed `resolve` gives the list.
- A failed `getType` gives the default page.
- A wrong-typed or unreadable extra is no value.
- READ denied or a failed alerts query means the poke does nothing.
- A failed copies query keeps the copies hidden.
- A missing `people_edit.json` allows nothing.
- One default lands on the noisy side: an unreadable `calendar_sync.json` empties the notified set (see F1).

## Item 9 — the manifest: PROVEN

`aapt2 dump xmltree` of the debug APK against the allow-list:
```
  20 trustB-exported-apk.txt
  20 trustB-exported-allow.txt
--- diff (apk vs allow-list; empty = identical)
diff rc=0
```
- `CalendarActivity`, `PeopleActivity` and `CalendarReminderReceiver` carry exactly the filters Scope names.
- Neither activity has `showWhenLocked` or a permission.
- The APK is from 15:33, the merge commit, 8 minutes before 83754009; `app/src` did not change in between.
- Nothing exported beyond the phase's need, apart from F13.

## Not checked, and what would settle each

- **PICK from a real caller.** A test app calls `startActivityForResult(ACTION_PICK, Contacts.CONTENT_URI)`. Expect:
  - a second PeopleActivity record in its task;
  - RESULT_OK with the lookup URI and flags 0x1;
  - one `UriPermission … mode=0x1 persistable=0x0` in `dumpsys activity permissions`;
  - a SecurityException on `…/contacts` and on `…/lookup/<key>/<id>/data`;
  - with NEW_TASK: an immediate CANCELED and no grant.
- **A hostile Parcelable** in the extras, sent by a test app: no crash, and the route logged.
- **F2 on a device.** Fire A; delete A; create B so its alert takes A's id; swipe A's notification. B's row should stay state 0 after the fix; today it should read 2.
- **F3 on a device.** `am start -n app.tileshell/.people.PeopleActivity -a "$(printf 'x\n[people] forged')"`, then read the ring.
- **Lock screen.** With a PIN set, `settings put secure lock_screen_allow_private_notifications 0`, then `1`; fire a reminder locked and compare what each shows.
- **Provider facts** on the AVD and the S25: the alert retention window, and whether the provider holds READ_CALENDAR.
- **A flood's real cost** (1,000 forged pokes while an alarm is due), and Q-16-4's leg (e) once built.

Full outputs are in `/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad/`. Open `trustB-mut-all.out` first; `trustB-mut2-all.out` and `trustB-probe.log` hold the second round and the probes.
