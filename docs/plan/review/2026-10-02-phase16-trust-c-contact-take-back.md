# Phase 16 — adversarial review of the contact take-back (commit edc15843), 2026-10-02

Saved by the lead from the reviewer's hand-back (an independent Opus agent, read-only). It read `git show edc15843`,
`PeopleWrites.kt`, `PeopleWriter.kt`, `PeopleWriteGuard.kt`, `PeopleEditStore.kt`, `PeopleWriterTest.kt`, the callers
and the plan's Q-16-3 text, and checked provider behaviour against AOSP source (ContactsProvider `android14-release`,
`android15-release`, `android16-release`, frameworks/base, CTS). Samsung's provider is closed source: everything
Samsung-specific is marked undecided. Paths are under `app/src/main/kotlin/app/tileshell/people/` unless given in full.
Line numbers are the reviewed commit's.

**Verdict: SOUND WITH FIXES**

## Findings

**1. HIGH (narrow path) — a failed take-back is reported as done.** `PeopleWrites.kt:113` swallows the delete's
exception and ignores its count; `:115` logs "…; it was taken back" unconditionally; the notice for any `Failed` is
"People couldn't save this contact. Nothing was changed." (`PeopleApp.kt:335,341`). If the phone misfiles the row and
the delete fails, a contact stays in an un-ticked account while the line and the notice say otherwise. Fix: check the
delete's result, re-read; if the row is still there say so, return a distinct result, and test it.

**2. MEDIUM — SIM import repeats the write-then-delete for every entry** (`:271-275` breaks only on `needsGrant`). On
a misfiling phone N entries make N inserts and N tombstones in the un-ticked account. Fix: stop at the first take-back.

**3. MEDIUM — group create has the same hole and no read-back** (`:284-293`). AOSP resolves a group's account through
the same code as a raw contact's (`ContactsProvider2.java:3326-3328, 3768-3775`). Fix: read back with `port.group(id)`,
compare, take back, say so.

**4. MEDIUM (adjacent) — on Android 16 the provider refuses rather than redirects, and the app reports it opaquely.**
The app always names both account columns (`:101`; `PeopleWriter.kt:166-168` writes explicit NULL). AOSP 14 / 15: an
explicit-null account always goes to the local account. AOSP 16: with the columns present and a cloud default,
`validateAccountForContactAdditionInternal` throws `IllegalArgumentException("Cannot add contacts to local or SIM
accounts when default account is set to cloud")` for apps targeting 36 — this app targets 36
(`app/build.gradle.kts:17,21,22`). The message is 81 characters, so `describe` (limit 80) reduces it to
`failed IllegalArgumentException`. If the S25's provider follows AOSP 16 and its default account is Google or Samsung,
every Save to Phone, SIM import and phone group create fails with that opaque line. Fix: on API 36 ask
`RawContacts.DefaultAccount.getDefaultAccountForNewContacts` before a local insert and fail before writing with a
readable reason; recognise the exception in `describe`. On the phone: save a new contact to Phone in People and read the
`[people] write insert` line on the diagnostics page.

**5. LOW — "could not be read back" covers three things** (gone, marked deleted, the read failed:
`ResolverContacts.rawContacts` turns any exception into an empty map). Failing closed is acceptable; the line should
say which.

**6. LOW — the line cannot settle the Samsung question as written:** `accountWord` printed the name only. Fix: print
`ContactAccount.id` (`type:name`) for both sides.

**7. LOW — the take-back is the first write that does not ask the guard, and only a comment says so.** Fix: a guard
kind for it with a test, its own `write delete` line, and `rawId > 0` before the delete (−1 is the other-profile
sentinel).

**8. LOW (optional) — name and number are written before the check** (one batch): a two-step create would keep the
data out entirely, at the cost of atomicity.

## Answers to the six questions

1. **Can the take-back delete anything else?** No path found: `rawId` is result index 0 of the batch, always the
   raw-contact insert; the URI is `raw_contacts/<id>` with no selection and no sync-adapter flag; AOSP's by-id delete
   touches that one raw contact (others of the aggregate are only re-aggregated); a local row is removed outright, a
   cloud row marked `deleted=1, dirty=1` for its adapter. A contact already on the server could be deleted only if the
   provider returned an existing row's id for an insert — AOSP does not; Samsung is unverifiable.
2. **Is the read-back sound?** Yes as code: it excludes `deleted=1` rows, reads the two columns as the provider returns
   them, NULL/NULL equals NULL/NULL; `policy.local` comes from `getLocalAccountName/Type`, the same source the editor's
   account uses. CTS `testRawContactCreate_nullAccountUsesLocalAccount` requires a null-account insert to read back as
   exactly that, so a CTS-passing S25 should compare equal; a good save being taken back there is unlikely but not
   decidable without the phone. Write the comparison through the policy
   (`if (policy.isPhone(account)) policy.isPhone(landed) else landed == account`).
3. **Failure path:** finding 1.
4. **Can the write be avoided by a pre-check?** Only partly: the read-back is the only reliable guard against a
   provider that misfiles; the API 36 pre-check predicts AOSP 16's refusal, not a redirect. Keep the read-back; add the
   pre-check for a clear early failure.
5. **Other creators:** `createGroup` (finding 3); SIM import goes through `create` (finding 2); `setMember` and `update`
   insert on an already-resolved raw contact; no Contacts insert exists outside `PeopleWrites` / `ResolverContacts`.
6. **Tests:** each of the three would fail on its defect. Missing: the delete throws or returns 0; the batch returns
   null for row 0; `importSim` when each create is taken back; `createGroup` misfiled; an editor create on a named-local
   policy with a NULL/NULL landing.

## What the lead did with it (2026-10-02)

Every finding but 8 is fixed in the commit that follows this file: the take-back checks the delete's count and re-reads,
and says "taken back", "could NOT be taken back — still in <type:name>" or "not known" truthfully, with a distinct
result (`Failed.takenBack` / `leftBehind`) and its own notices; a SIM import stops at the first take-back; a group
create is read back and taken back the same way; on API 36 a save to the phone is refused before anything is written
when the phone's default account for new contacts is a cloud one (`ContactsPort.newContactsGoToCloud`), in words, and
`describe` recognises the provider's own refusal; lines print `type:name`; the comparison goes through the policy; the
take-back is a guard kind (`TakeBack`, allowed only for an id above 0) with its own `write delete … (taken back)`
line. `PeopleWriterTest` gains the missing cases. Finding 8 (a two-step create) is recorded, not done: the batch's
atomicity is kept, and on AOSP 16 the pre-check already stops the write before it happens. Phone row P8 reads the line.
