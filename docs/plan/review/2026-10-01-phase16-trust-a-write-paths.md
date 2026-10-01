<!-- Saved verbatim by the lead from the reviewer's hand-back (an Opus subagent, adversarial mode; model output, not the owner's words). Brief: 2026-10-01-phase16-trust-brief.md. The lead's disposition of each finding is qa/phase-16/fix-round.md. -->

# Phase 16 trust review — Reviewer A (the write paths), commit 83754009

**No HIGH found.** Reading every write, I found no path by which the app, Tess, Sync or the receiver writes an account calendar or an un-ticked contact account. **But that the write layers actually refuse is NOT PROVEN by any test, JVM or device** (F1): 17 write-layer mutations survive, including "the gate ignores the guard's verdict". Four MEDIUM, eleven LOW.

Evidence is in the scratchpad `/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad/`, prefix `trustA-`. Nothing in the reviewed tree was changed. The trustA worktree is clean at 83754009 and back at 1111 / 0.

## Item 1 — Find every write: PROVEN (no bypass)

```
$ grep -rnE "\.(insert|update|delete|bulkInsert|applyBatch|call)\(" app/src/main --include='*.kt' --include='*.java'   → 79 hits
$ grep -rnE "ContentProviderOperation|applyBatch|bulkInsert|acquire*ProviderClient|AsyncQueryHandler" …               → PeopleWriter.kt only
$ grep -rlE "CalendarContract|ContactsContract" livetile-client calc r4probe testapps tools                           → (none)
```

- **Calendar:** all 18 `CalendarContract` writes are in `calendar/CalendarWrites.kt` (lines 157, 176, 200, 211, 224, 266, 285, 291, 294, 303, 321, 346, 357, 364, 440, 451, 459, 480). Each sits inside `guarded(…)` or an explicit `check(CalendarWriteGuard.check(…) == Allowed)` (157, 176).
- **Contacts:** all 11 `ContactsContract` writes are in `people/PeopleWriter.kt` (83, 149, 170, 220, 271, 283, 305, 330, 337, 359, 365). Each follows a `PeopleWriteGuard.check`. `writePhoto` and `removePhoto` (359, 365) rely on their caller's check.
- **Everything else:** the other hits are files, MediaStore (recorder) and the shell's own stores.
- **Intents:** the shell sends no intent that makes another app write a contact or event. The only `ACTION_DELETE` is the package uninstall.
- **Callers:** `Path` is always a literal at the call site (`ActionLayer.kt:499,544`, `CalendarEvents.kt`, `BirthdaysWriter.kt`, `LocalCalendar.kt:70`, `CalendarReminders.kt:130,139`). `CalendarSync.sync` is called only from two taps (`CalendarPages.kt:177,499`). `setAllowed` is called only from the "Can sync to" row tap (`CalendarPages.kt:531`), and `PeopleEditStore.set` only from the "Can edit" row (`PeopleSettingsPages.kt:154`).
- **C1 attacked:** the delete by `ORIGINAL_ID` through the sync-adapter URI (`CalendarWrites.kt:291`) is scoped to LOCAL/Tessera by the provider. I read AOSP `CalendarProvider2.deleteInTransaction` → `appendAccountToSelection` (branch `main`; the `android16-release` fetch failed). It scopes only when `account_name` is non-empty, which the guard's Tessera match guarantees.

## Item 2 — Red-proof of the guards' tests: FINDING

Baseline: `./gradlew :app:testDebugUnitTest` → `trustA-baseline.rc` = 0, 1111 tests, 0 failures.

Driver `trustA-mutate.py`: one mutation at a time, whole suite, rc to a file, `git checkout --` after each, 78 of 78 restored. Table: `trustA-mutations.tsv`. **78 mutations: 57 caught, 21 not.**

| Group | Caught | Not caught |
|---|---|---|
| Calendar guard (pure), 37 | 33 | G07, G13, G30, G32 |
| People guard (pure), 18 | 18 | — |
| Calendar write layer / Sync / store rules, 19 | 6 (W12–W16, W22: pure rules only) | W01–W11, W17, W19 (13) |
| People write layer, 4 | 0 | PW1–PW4 |

The mutations the brief named:
- a case's condition inverted (G01): caught
- two branches reordered (G12, G14): caught; G13 is not, but it only changes the refusal reason
- the allow-list check removed in the guard (G03): caught
- the same check removed in the write layer, where the list is actually read (W01, W02): **not caught**
- the stale-mapping re-read removed (W03): **not caught**
- the access-level threshold changed (G08–G11): caught
- "every" turned into "any" (P01): caught

The survivors:
```
G07  T16-12: `mapped && mappingTarget == null` check removed
G13  reorder: the access-level check before the allow-list check
G30  isTessera: the account-type half dropped (any account named Tessera)
G32  isBirthdays: the account-type half dropped
W01  syncRequest: targetAllowed no longer reads the allowed list
W02  syncRequest: targetAllowed compares the calendar _ID only (not account name + type)
W03  syncRequest: the stale-mapping re-read removed (copyCalendarId taken from the mapping)
W04  syncRequest: sourceInTessera no longer checks the source's calendar
W05  syncRequest: any row counts as mapped once a mapping exists
W06  push: the target's account name + type no longer compared with the re-read calendar
W07  push: the guard pre-check (syncCheck) removed
W08  the write layer's gate ignores the guard's verdict (every write goes through)
W09  deleteEvent asks the guard about Tessera instead of the row's re-read calendar
W10  r3 D1: Tess's delete matches a title on every calendar
W11  delete: Tess may delete 'here and from <calendar>'
W17  the always-running observer no longer prunes the allowed list
W19  r3 D7: a FAILED lookup creates another Tessera calendar
PW1  update(): the per-raw-contact guard loop removed
PW2  delete(): the guard's refusal ignored
PW3  create(): the guard not asked
PW4  the write layer's `refused` helper always says allowed
```

**F1 — MEDIUM: nothing proves the gate gates.**
- No JVM test touches `CalendarWrites`, `CalendarSync.push/delete`, `PeopleWriter`, `LocalCalendar` or Tess's Tessera-only match.
- The device row does not either: `qa/phase-16/scripts/e22.sh:212-220` proves its "refused" cases by re-running the JVM guard cases by name (`jcase … theEditorIsRefusedOnBirthdays` and so on).
- The UI hides every refused action, so the write layer's refusal is never reached on a device.
- E24's three Sync refusals come from `syncCheck`, which calls the guard directly (`CalendarSync.kt:91`), so even they would pass with W08.
- Files: `CalendarWrites.kt:80-88` (`guarded`), `:394-411` (`syncRequest`); `CalendarSync.kt:83-91,206`; `ActionLayer.kt:519`; `PeopleWriter.kt:69,131,164-165,420-421`; `LocalCalendar.kt:43-47`.
- Smallest fix: put the resolver calls of the two write layers behind a four-method interface (query / insert / update / delete) and add one JVM test per public write function with a recording fake: "the guard refuses → zero writes recorded", and "allowed → exactly the expected URI".
- Device alternative: add an E22 leg that reaches the refusal. Open the editor on a Tessera event, re-home that row to the Work calendar from adb, tap Save. Expect `[calendar] write update event=<id>: failed refused (not allowed)` and the row unchanged.

**F4 — MEDIUM (untested half): the account-type half of `isTessera` / `isBirthdays`.**
- `CalendarWriteGuard.kt:114,116`; G30 and G32 survive.
- No test holds a non-LOCAL calendar whose account is named `Tessera` (a CalDAV account can be named anything).
- Fix: add `CalendarFacts(8, "Tessera", "com.example.dav", 700)` and `CalendarFacts(9, "Tessera Birthdays", "com.google", 700)` to the refused lists in `everyOpOnAnAccountCalendarIsRefusedForEveryPathButSync` and `tesserasEventRowsMayBeWrittenAsItsOwnSyncAdapterAndNothingElseMay`.

**F5 — LOW: G07 and G13.**
- `CalendarWriteGuard.kt:170` cannot be reached from `syncRequest` (mapped implies a mapping target), so G07 is a dead branch.
- G13 changes only the reason given.

**F6 — LOW: a test pins a wider allow set than C1 needs.**
- `CalendarWriteGuardTest.kt:74-79` pins that the editor and Tess may INSERT, UPDATE and DELETE Tessera events through the sync-adapter URI.
- C1 needs only DELETE (editor, Tess) and an UPDATE of `_sync_id` (editor).
- The guard reads `columns` only in case 4 (`CalendarWriteGuard.kt:143`), so a sync-adapter UPDATE setting `calendar_id` would pass. No code does that today.
- Fix: for `viaSyncAdapter` on Tessera events allow DELETE, and UPDATE only when `columns == {"_sync_id"}`; refuse `calendar_id` in any UPDATE.

## Item 3 — Both halves of each rule: FINDING (MEDIUM, part of F1)

| Rule | Allowed half | Refused half |
|---|---|---|
| Editor → Tessera only | JVM guard; device E4 / E5 | JVM guard only; write layer unreached (no actions on a non-Tessera event) |
| Tess insert / delete → Tessera only | JVM guard; E22_TESS | Tess's own filter is device-tested ("That event isn't in your Tessera calendar."); the write layer's refusal is unreached; W10, W11 survive JVM |
| Sync → ticked calendar only | E23 / E24 | JVM guard only; `refused (not allowed)` cannot be produced on a device (picker lists ticked only; `CalendarPages.kt:241` skips an un-ticked mapped target) |
| T16-12 stale mapping | — | JVM guard; E24 "moved copy" leg goes through `:168`, so the `copyCalendarId` re-read (W03) is covered by nothing |
| r3 D5 access ≥ 500 | JVM (500 passes) | JVM (499 refused); E24 read-only leg |
| r3 D7 no second Tessera | — | Not on JVM (W19) |
| Allowed list follows the provider | — | Pure `prune` tested; that the observer calls it is not (W17) |
| People: phone / ticked account | JVM; E28 | JVM guard only; E28 asserts the absence of Edit / Delete and the EDIT intent line, never `PeopleWriter`'s refusal (PW1–PW4) |
| People: aggregate delete, SIM import, group op, INSERT prefill refusals | JVM | Unreachable at the writer (UI or route never offers them) |
| People: STARRED / `RawContactRow` | JVM | Not reachable at all: nothing builds `PeopleWrite.ContactColumn` or `RawContactRow` (F11, LOW) |
| People: another profile's contact | JVM | The write layer never sets `otherProfile` (`PeopleWriter.kt:352`); an enterprise contact is refused there only because its ids resolve to nothing (`PeopleData.kt:271-275`, raw id −1) (F15, LOW) |

## Item 4 — The files that gate: FINDING

Probe: `trustA-TrustAStoreProbeTest.kt.txt` ran the product stores on the JVM against files in a temp dir (`trustA-probe.rc` = 0). The JSON library was JSON-java 20241224, not Android's org.json.

```
CAL missing -> allowed=[] mappings=[] notified=[]
CAL truncated-half -> allowed=[] mappings=[] notified=[]
CAL truncated-last-brace -> allowed=[] mappings=[] notified=[]
CAL empty-file -> allowed=[] …   CAL garbage -> allowed=[] …   CAL top-level-array -> allowed=[] …
CAL allowed-not-an-array -> allowed=[] mappings=[SyncMapping(localEventId=10, …)]
CAL allowed-entry-missing-accountType -> allowed=[] mappings=[] notified=[]
CAL hand-edited-adds-work -> allowed=[CalendarKey(id=6, accountName=work@example.com, …), CalendarKey(id=5, …)]
CAL corrupt-then-update: file now={"notifiedAlerts":["1:2:3"],"mappings":[],"hidden":[],"allowed":[],"version":1}
CAL running, file deleted: allowed=[]
CAL untick-save-fails: update returned allowed=[]; store.current.allowed=[]; file={"version":1,"allowed":[{"id":5,"accountName":"me@example.com",…
CAL untick-save-fails: after a restart allowed=[CalendarKey(id=5, accountName=me@example.com, accountType=com.google)]
PEOPLE missing / truncated / empty / garbage / top-level-array / allowed-not-an-array / entry-missing-type / null-name / empty-name -> allowed=[]
PEOPLE one-bad-one-good -> allowed=[ContactAccount(name=work@example.com, type=com.example)]
PEOPLE untick-save-fails: memory=[ContactAccount(name=me@example.com, type=com.google)]; file={"allowed":[{"name":"me@example.com",…}]}
```

- **Who can write them:** only the app's uid. Both are in the files dir, `android:allowBackup="false"`, no FileProvider, written only by `CalendarSyncStore.save` and `PeopleEditStore.write`. A hand-edited entry is honoured, as expected of the gate.
- **Corrupt, truncated, missing:** nothing allowed, both files. PROVEN.
- **Is the whole key compared:** yes in code — `prune`, the picker, `push` (`CalendarSync.kt:84`) and `syncRequest` (`CalendarWrites.kt:406`) compare id + account name + type. Only `prune` and the picker are tested (W02, W06 survive).

**F2 — MEDIUM: the key does not identify the calendar inside its account.**
- `CalendarKey` is `_ID` + account name + type (`CalendarSyncState.kt:7`). Ids are reused.
- A tick is dropped only if a prune runs while the calendar is absent (`CalendarReads.kt:367-378`, `CalendarModel.kt:186-188`).
- If one account's calendars are dropped and re-made with ids swapped, and the next prune sees only the end state, the tick moves to a calendar the user never ticked. That happens with a single provider notification, with the shell's process not running, or with READ_CALENDAR revoked in between.
- Probe (`trustA-key-probe.out`, product rules):
```
allowed after the prune: [CalendarKey(id=3, accountName=me@example.com, accountType=com.google)]
Sync picker offers: [3=Team (shared)]
guard on a first push into id 3 (Team (shared)): Allowed
```
- A different account under the same id is refused. This would be HIGH only if the owner's work calendar sits under the same account as a ticked one (for example shared into the personal Google account).
- The builder met this in `dev-cal/E24_SYNC2-run2-a-stale-tick-under-a-reused-id`; the observer prune narrows it but does not close it.
- Smallest fix: add `Calendars.NAME` (or `_SYNC_ID`) to `CalendarKey` and to `CALENDAR_COLUMNS`; data-class equality then carries it through every compare.
- Device check: `am force-stop app.tileshell` with another app in front; delete the ticked calendar; insert another under the same account so it takes the id; go Home; read `cal_settings_can_sync:<id>`. Un-ticked settles it.

**F3 — MEDIUM (also item 8): an un-tick whose save fails comes back ticked.**
- `CalendarSyncStore.kt:32-41,79-95`. On a failed write (storage full) the store keeps the new state in memory and writes only a diagnostics line.
- The page shows un-ticked; the next process start reads the calendar as allowed again. An already-synced event then pushes straight to it on one Sync tap (C4).
- Fix: on a save failure set `flow.value = load()` and return it, as `PeopleEditStore.kt:52-61` does. The probe shows People's store keeps the truth.

**F8 — LOW: one bad entry empties the calendar store for good.**
- `CalendarSyncStore.kt:52-77`. Any bad entry empties the whole state, and the next update overwrites the file.
- It fails closed for the gate, but the mappings and notified set are lost: copies show twice with two reminders (Q-16-2).
- Fix: keep the bad file aside and parse sections separately.

**People, removed and re-added account.** The key is the account itself and both parts are compared. It starts NOT allowed only if `PeopleFeed` or the app pruned while it was absent (`PeopleFeed.kt:83`, `PeopleApp.kt:227`). Same process-death window as F2, lower consequence. LOW.

## Item 8 — Fail-open paths: FINDING (F3), the rest deny

- **Deny on failure, by reading and probe:** `CalendarReads.calendar` (null → refused, G29 caught); `eventRow`; `LocalCalendar.find` Failed; `ActionLayer.calendarEvents`; `PeopleWriter.resolveRaws`; `PeopleData.rawContacts`; `PeopleData.group`; both store loads.
- **F9 — LOW:** a failed read of the copy row (`CalendarWrites.kt:57-63,408`) gives `copyCalendarId = null`, which the guard takes as "no copy" (`CalendarWriteGuard.kt:169`). The worst case is a duplicate copy in the allowed calendar. Fix: carry "copy read failed" in `SyncFacts` and refuse.
- **F7 — LOW:** `CalendarSync.kt:87` drops the old mapping before the guard is asked at `:91`. A refused Sync to another target loses it.
- **F14 — LOW:** `CalendarWrites.kt:290` uses `calendar?.accountName.orEmpty()`. Guarded today; add `require(account.isNotEmpty())`, since the provider un-scopes on an empty name.

## Item 9 — Manifest, my lens: PROVEN on the source manifest; NOT PROVEN on the APK

```
exported in source manifest: 19   allow-list entries: 20
in manifest, not on list: []      on list, not in source manifest: ['androidx.profileinstaller.ProfileInstallReceiver']
```
`CalendarReminderDismissReceiver` is `exported="false"`; its PendingIntents are explicit and `FLAG_IMMUTABLE`. WRITE_CALENDAR and WRITE_CONTACTS are declared.

## Other LOW findings

- **F10:** `PeopleWriter.kt:301-304` deletes a phone group through `CALLER_IS_SYNCADAPTER`. This is not in the spec or INDEX's "built otherwise" list, and the People guard has no notion of it. Fix: record it, add `viaSyncAdapter` to `GroupRow`, refuse it off the phone.
- **F12:** the dismiss PendingIntent carries the alert row id (`CalendarReminders.kt:138-141,166-171`), which the provider reuses (C2 fixed only the notified set). Swiping an old notification can mark another alert DISMISSED, and that reminder never shows. Fix: carry event + begin + alarmTime and update by all four.
- **F13:** on the Sync path the reminder batch reuses `SyncFacts` computed once (`CalendarWrites.kt:464-467`), so "re-read before every write" is not literal there.

## What I could not check, and why

1. **Anything on a device** (no emulator, no adb), including F2's and F3's reproductions and the E22 leg proposed under F1.
2. **A "Phone" contact on Android 16 with a cloud default account.** I read AOSP `android16-release` `AccountResolver.java`: People's insert names the null account explicitly, so it is "local", not "default"; with the restriction config on and targetSdk 36 the provider throws (fails closed). Samsung's provider is not AOSP. P8 should create a contact on "Phone" on the S25 with the default account set to Google; it passes only if the row's account is the local one or the save is refused.
3. **Whether a synced recurring copy with exceptions loses its other occurrences** in the account calendar, as the builder found for a Tessera series with no `_sync_id` (C1). Sync a weekly Tessera event with one edited occurrence, then query `instances` for the copy.
4. **The Android org.json.** The store probe used JSON-java, so coercion details may differ; the fallbacks are the product's.
5. **The built APK's merged manifest** (`exported.py` needs the APK).
6. **The builders' two reports:** not read.

One slip: while locating the SDK path I printed the first lines of `local.properties`, which include the TMDB read token, into my own tool output. It is in no file I wrote and not in this report.
