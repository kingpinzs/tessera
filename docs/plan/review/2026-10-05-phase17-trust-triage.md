# Phase 17 — adversarial trust reviews, round 1 (2026-10-05) and the lead's triage

Three Opus reviewers, brief `docs/plan/prompts/phase-17-trust-review.md`, tree 809f7194. **All three: FAIL. No HIGH in A
or C; one HIGH in B.** Nothing found lets another app write where it may not, read a file, or take a credential, except
B-1 (a credential can reach logcat through a crash).

**Limits of this round (the host's disk filled to zero at about 14:31–14:40 while they ran):** A and C ran their
red-proof and mutation tables on the real source and test classes compiled outside Gradle (Kotlin 2.4.20 compiler jar +
JUnit 4.13.2, in /dev/shm), not through `:app:testDebugUnitTest`. **B ran nothing**: its tables are predictions from
reading, its search of the dev-video folders for credential-shaped strings was NOT done, and it did not read
PlayerScreen.kt, HubPages.kt, TitlePage.kt, the Jellyfin fixture or the dev-video run folders. B must be re-run in full.

Status words: FIX = fixed at the producer before `done`, with a test that fails without the fix, then one re-review of
the fixes; ROW = a device row or leg is added; DOC = the doc / allow-list text is corrected; OWNER = the owner rules;
NOTE = recorded, not changed in this phase (reason given).

## A — the capture answer and the MediaStore write layer (verdict FAIL: 3 MEDIUM, 7 LOW)

| # | Sev | Finding | Triage |
|---|---|---|---|
| A-M1 | MEDIUM | Condition (d)'s second branch is not what the doc says: `Context.checkUriPermission(uri, -1, uid, WRITE)` answers explicit URI grants only (AMS → `UriGrantsManagerService.checkUriPermissionLocked`; verified from android14 / 16 source) and never asks the provider, so "MediaStore does for a row the caller owns" is false. A caller that inserts its own MediaStore row and passes it as EXTRA_OUTPUT (the standard Android 10+ pattern) is REFUSED. Fails closed. The grant branch has never been seen true anywhere. | FIX + ROW + OWNER. On API 35+ `checkContentUriPermissionFull` asks the provider; API 34 has no such call. Lean: use it where it exists and keep the refusal on 34 (the S25 Ultra is on One UI 8 = API 36), correct the KDoc and the Decisions line, add E9 legs (the caller's own MediaStore row; a URI the caller holds only a write grant for). The owner is told: on a phone below Android 15 an app that hands the Camera its own MediaStore row is refused. |
| A-M2 | MEDIUM | `CaptureCallerAccess.callerMayWrite` and `CaptureActivity.decide`'s mapping from the intent to the guard's inputs have no JVM test: hard-coding `callerMayWrite = true`, dropping the `@` check or the "EXTRA_OUTPUT is not a Uri" refusal survives every unit test. | FIX: a small port (uid of package, owner uid of authority, has grant / provider says yes) so the rule is unit-tested; the intent-to-inputs mapping extracted and tested (non-Uri EXTRA_OUTPUT, unreadable extras, unknown package). |
| A-M3 | MEDIUM | Location stripping is unproven both ways: CaptureActivity never starts the location feed, so "gps_tags=0" is vacuous; flipping `keepsLocation` or deleting the strip fails nothing; `GPS_TAGS` omits `TAG_GPS_H_POSITIONING_ERROR`. | FIX + ROW: a JVM test of the strip over an EXIF block with every GPS tag; the missing tag added; E9's GPS control reordered so the Camera's own shot is shown to carry GPS first and the caller's capture then has none. |
| A-L1 | LOW | The guard reads authority and (d) from the `Uri` extra and writes through a re-parse of its string. Not exploitable at minSdk 34 (a Uri is parcelled as its string). | FIX (cheap): parse `output.toString()` once and derive everything from that one value. |
| A-L2 | LOW | `getCallingPackage()` names the result's recipient: with FLAG_ACTIVITY_FORWARD_RESULT a trampoline can make another app the "caller". Needs a victim that owns a provider the shell can already write AND starts the attacker for a result. From knowledge, not seen. | NOTE + ROW question (the reviewer's device question 4); `getLaunchedFromUid()` (API 34) names who really started the activity — compare the two and refuse when they differ. FIX if the device shows the trampoline case reads as the reviewer says. |
| A-L3 | LOW | A grant held at the check can be gone at Accept. No gain to the caller. | NOTE. |
| A-L4 | LOW | Stale text: `exported-allowlist.txt` and three doc lines still say "three-condition"; the ViewerActivity line says "read under the caller's grant; nothing is written from an intent" — the viewer reads with the shell's own access and offers Delete / Set as / Edit for a MediaStore image (each needs the user's tap). | DOC (with C-M4). |
| A-L5 | LOW | `PhotoStore.canonical()` drops the volume segment: an `internal` URI can match the external row with the same id. Copy-only, user-driven. | FIX (small): keep the volume in the comparison. |
| A-L6 | LOW | `openOutputStream(uri, "w")` does not truncate on every provider. | FIX: `"wt"`. |
| A-L7 | LOW | The camera builder's device logs were made before the pending ledger existed; the "no pending row" asserts are vacuous for the capture path and `pending_rows` has no positive control. | ROW: the gate rows run on the gate build; the pending check gets a positive control (a row seen pending during a save). |
| A-S | — | Surviving mutation worth a test: `Accepted(clipUris.first())` for `Accepted(output)` — no test has a ClipData of several URIs. | FIX: the test (clip = [other, out]). |

Held (checked by the reviewer): file and every non-content scheme refused before a camera opens; wrong-typed
EXTRA_OUTPUT refused; `10@authority` and `%40` refused; own authorities refused; thumbnail ≤ 256 px; the returned video
URI carries READ only; no DCIM copy for an accepted output; `Accepted` has an internal constructor with one consumer;
the dump services and EditActivity are not exported; diagnostics lines carry bounded, filtered text. **The premise for
(d) is confirmed from platform source** (`checkGrantUriPermissionUnlocked` returns before the caller check when the
target holds the access; a `forceUriPermissions` provider never takes that shortcut) and on the device (dev-camera B3).
Whole-app grep: Photos' and Camera's MediaStore writes are only `MediaWrites.save`, `writeCaptureOutput` and
`deleteRequest`; phase 15's Voice Recorder and phase 16's People have their own guarded writers (audio rows, contact
photos), so "the one write layer" is true for images and video. Red-proof: every guard removed fails a named test
except CaptureCallerAccess and the location strip (no test exists). Mutations: 52 run, 48 caught, 4 survived (the one
above; three benign).

## B — the credential store, the TMDB key and the Jellyfin sign-in (verdict FAIL; REVIEW INCOMPLETE — re-run owed)

| # | Sev | Finding | Triage |
|---|---|---|---|
| B-1 | HIGH (from source, not seen on a device) | A credential can reach an exception message and logcat, then crash-loop: `HttpURLConnection.setRequestProperty` throws IllegalArgumentException with the WHOLE header value in its message for a control character inside it; `VideoHttp.send` / `bytes` catch IOException only; a token pasted with an interior CR (the key page only trims; the field strips `\n` only) is stored, every later request throws, `:video` dies and AndroidRuntime prints the token; Browse then crashes on every open. The same for a Jellyfin `AccessToken` or device name holding a control character. | FIX, before anything else: validate at entry (the key page accepts printable ASCII only and says so; `parseSignIn` rejects a token outside a safe set; the header quoting drops every control character) AND `VideoHttp` catches RuntimeException and passes no message on; JVM tests for each; a device row that pastes a token with an interior CR and shows no crash and no token in logcat. |
| B-2 | MEDIUM | "Sign in again" prefills the host with no scheme, so a server saved as https is contacted over plain http on port 8096 — for a private host with no prompt. | FIX: keep the scheme (prefill the saved base); a ServerRules test. |
| B-3 | MEDIUM | "Sends nothing until Continue" and the debug-only gate of `qa_server_base` have no JVM test; the latter does not go through `CatalogueRules.base`. | FIX: the prompt decision as a pure function with a test; SERVER routed through the same gate as the other two prefs. |
| B-4 | MEDIUM | The token is bound to the name "jellyfin", not to the server: `media_server.json`'s base decides where it goes, and the save is two files with a failed rename ignored (token B can end up paired with base A, reported connected). | FIX: the base sealed with the token (one entry, or AAD = name + base) and compared on every send; the write reports failure. |
| B-5 | LOW | ExoPlayer is handed the token-bearing URI as its reported URI. Nothing prints it today. | FIX: strip `ApiKey` in `resolveReportedUri`. |
| B-6 | LOW | The image base from `/3/configuration` is used unchecked (no token travels there). | FIX (with C-M2): in release it must start with `FixedEndpoints.TMDB_IMAGES`. |
| B-7 | LOW | `allowBackup=false` but no `dataExtractionRules`: on Android 12+ device-to-device transfer could carry the ciphertext and `media_server.json` (not the key). | FIX: rules excluding both files. |
| B-8 | LOW | Two-process hazards for phase 20: Keystore key creation is check-then-generate with no cross-process lock; the file's read-modify-write is locked in-process only. Only `:video` uses the store today. | NOTE on INDEX for phase 20, or FIX (a file lock) if cheap. |
| B-9 | LOW | No response size cap in `VideoHttp`. | FIX: a cap. |
| B-10 | LOW | `SAFE_ID` admits `..`: a Wikidata value can steer a "Watch on" link to another path on the service's own host. | FIX: reject `..` segments; a test. |
| B-11 | LOW | `EXTRA_TITLE` unbounded into session metadata; no FLAG_SECURE on the key and password pages; the clipboard keeps the pasted token. | FIX the bound; OWNER for FLAG_SECURE (lean: yes on those two pages). |

Held on reading: the AAD is the entry name (a swap of the two ciphertexts does not open); the IV is Keystore-made per
seal; an undecryptable value reads as absent and logs a class name only; `instanceFollowRedirects` is false wherever a
header is sent; Wikidata and image requests carry no Authorization; the stream URL in the intent, the MediaItem and the
diagnostics line has no token; `mayCarryToken` needs exact scheme, host, port and the stream path and refuses
user-info; the private-address rule fails toward asking; the cache name cannot traverse; SPARQL takes a Long only.

## C — the network security config and the exported surface (verdict FAIL: 4 MEDIUM, 7 LOW)

| # | Sev | Finding | Triage |
|---|---|---|---|
| C-M1 | MEDIUM | `.github/workflows/apk.yml` publishes a DEBUG APK to the public rolling release when the signing secrets are absent (a warning in the summary only). A debug APK is debuggable, carries the 10.0.2.2 exception and honours the three QA base-URL prefs — the catalogue base receives the TMDB token. Predates phase 17; phase 17 makes it matter. | FIX (phase 01's CI, a Change Log line): the job fails, or skips the release step, when the variant is debug. OWNER is told. |
| C-M2 | MEDIUM | "The list is complete" has no test: `FixedEndpointsTest` scans no source; weather and the place search hold their own literals; an unlisted `http://` constant survives. The grep today is clean. | FIX: a test that scans `app/src/main` for URL literals against the set; the image base checked (B-6). |
| C-M3 | MEDIUM | Config mutations no test sees: user-added CAs trusted, a padded `<domain>`, the manifest attribute removed, `logPolicy` printing a constant. | FIX: the test asserts no trust-anchors / debug-overrides / pin-set, untrimmed domain text, and the manifest's attribute; the `[net]` lines stay E13's. |
| C-M4 | MEDIUM | The allow-list and manifest comments for the two helpers say more than the code enforces: (a) PlayerActivity plays `file://` under the shell's own data and external-files dirs from ANY caller; (b) both helpers open a content URI with the SHELL's identity, so an app with no media permission can make the shell show the user any MediaStore item (nothing is returned to the caller); (c) the externally started viewer offers Edit / Delete / Set as for a MediaStore image (each a user tap); (d) `EXTRA_QUEUE` from any caller drives Autoplay to further ids. | FIX at the producer: read `getLaunchedFromUid()`; `file` and `EXTRA_QUEUE` only for the shell's own uid; a foreign caller's content URI is opened only when that caller may read it (`checkUriPermission` for its uid, or its own provider) — else the refusal line; the write actions hidden for a foreign caller. Then DOC. JVM tests for the rule, a device row from a permission-less app. |
| C-L1 | LOW | Caller text reaches the `:video` ring unbounded and unescaped (scheme, host, last path segment; `%0A` forges a ring line). | FIX: bound and strip control characters at the three sites. |
| C-L2 | LOW | ViewerActivity decodes another app's image in the LAUNCHER's process; the Probe page reads up to 64 MB of a picked image there. | OWNER / NOTE: lean — move ViewerActivity to `:photosedit` (a manifest attribute; r3 D8's own reason) and lower the probe's read; re-run E4's rows it touches. |
| C-L3 | LOW | The helpers declare no task affinity (they share the package default with Start). | ROW question (reviewer's question 7), then decide. |
| C-L4 | LOW | Build task 17 names a `[video] cleartext refused <host>` line; none exists (Q-D A is the only form built; r3 V6 struck it from E13 / E18). | DOC: strike it from task 17's text. |
| C-L5 | LOW | `logPolicy` is skipped when the process starts before the first unlock. | FIX (one line). |
| C-L6 | LOW | PlayerRulesTest gaps (a password with `@` leaking into the host label; a foreign provider's `/video/media/N` read as a MediaStore id; an empty own-root); the activity's canonicalisation untested. | FIX with C-M4. |
| C-L7 | LOW | `EXTRA_TITLE` unbounded. | FIX (B-11). |

Held: only HttpURLConnection and Media3's DefaultHttpDataSource make requests (no OkHttp, Cronet, WebView or raw
socket; the panorama library links no socket call); redirects are not followed where a header is sent and the player
refuses cross-protocol redirects; the merged manifest's 26 exported components equal the allow-list's 26 lines;
permissions added are CAMERA, READ_MEDIA_VIDEO and SET_WALLPAPER only; nothing shows over the keyguard; the debug
config is the nine denied hosts plus 10.0.2.2; no phase 17 activity starts an Intent taken from its extras.

## What happens next
1. Fix B-1 first, then every FIX above, each with a test that fails without it (one commit per finding or tight group).
2. Re-run reviewer B in full (it ran nothing), and give A and C the fix diff for one re-review of the fixes only.
3. The device questions each reviewer listed become legs of E9, E13, E20–E22 or a TRUST row; the list is in the
   reviewers' reports as quoted in `.claude-build-state.md`'s entry of this date.
4. OWNER items, each asked one at a time at the gate if not ruled before: A-M1's API 34 edge; B-11's FLAG_SECURE;
   C-L2's process move; C-M1's CI rule.

---

# Round 2 — review B re-run in full on the fixed code (e5e30678), 2026-10-05

Verdict: **FAIL — no HIGH, four MEDIUM.** Everything was RUN this time: 128 baseline tests of `net` + `video`, then 175
single-change runs (53 guard-removed, 106 subtly-wrong, 16 call-site): 91 mutations caught, 14 survived. Round 1's HIGH
(B-1) is fixed at the rule level and its tests were seen to fail without the fix; **its call sites are proven by
nothing until the device legs run.** No JVM test pins a defect as correct; one dev script does (B2-M2).

| # | Sev | Finding | Triage |
|---|---|---|---|
| B2-M1 | MEDIUM | A live server token can exist while the hub says "no server" and offers no Remove: "set up" needs the pages' file AND the sealed entry, but the player's token path reads the sealed entry alone; `ServerStore.remove()` ignores a failed credential removal, deletes the pages' file, and `[video] server token cleared` is written regardless (probe run: storage full → the token still rides). The same after a kill between `save()`'s two writes. An earlier build's bare token, or an entry whose key is gone, is never deleted. `TMDB key removed` is also written whether or not it worked. | FIX: `remove()` reports whether the sealed entry went; the line is written only then and the pages' file deleted only after it; the stream token also needs the pages' file; on hub start an entry with no file, or one that does not open as a sealed server, is removed; the TMDB line likewise. Tests for each. |
| B2-M2 | MEDIUM | Any app with no permission (or a web link) can make the stored token travel: a VIEW of the saved server's own `/Videos/<hex>/stream?…` gets the owner's token attached and the item played. Only to the saved server, nothing returns to the caller, but the caller picks the item and the stream parameters. | FIX: the token resolver only for the shell's own launch, as a tested rule. |
| B2-M3 | MEDIUM | Every fix's CALL SITE is unproven: 14 of 14 wiring mutations survive (the key page storing an unvalidated paste; the form sending at once; the debug gates; the resolver using the wrong token function; the reported URI; `ownCaller = true`; the secret field in the clear; the cipher with no AAD or a 128-bit key). | ROW (the fixes file's device legs, plus: a sealed entry moved under the other name reads as absent) + FIX (JVM source-scan tests: the three QA-pref reads pass `BuildConfig.DEBUG`; the key page stores only the validator's result). |
| B2-M4 | MEDIUM | Test gaps on rules that hold today: redirects with headers not followed (two sites); the check of an already-stored key in `Catalogue.request`; a failed rename in `CredentialFile.write`; the private-address rule's 172.16–31 bound for other first octets and "any name ending localhost"; `posterBase` with `contains`. | FIX: a test each. |
| B2-L1 | LOW | `FetchOutcome.Answer`'s `toString()` is the body (a sign-in answer holds the token). Nothing prints it. | FIX. |
| B2-L2 | LOW | Evidence: `dev-video/C_CATALOGUE-run1/C_CATALOGUE.txt` holds the fixture's DUMMY TMDB token in two PASS lines (the harness printed its needle). | NOTE: evidence stays; the gate's leak scans run over the gate rows' folders; this file is a known dev-proof match of a dummy. |
| B2-L3 | LOW | The extraction rules omit the `.tmp` / `.lock` files, `media_server_device.txt` and `video_catalogue/`. | FIX. |
| B2-L4 | LOW | No total deadline on a response read. | FIX if small, else NOTE. |
| B2-L5 | LOW | Server-supplied text (a title, the query) reaches `[video]` lines unbounded outside the player. | FIX: the same cleaner. |
| B2-L6 | LOW | `mayCarryToken` does not percent-decode the caller's own key name; `reportedUrl` misses a `;`-separated key. | FIX. |
| B2-L7 | LOW | The key field cuts a paste at the cap: an over-long paste is stored truncated. | FIX: refuse, do not truncate. |
| B2-L8 | LOW | The Keystore key allows use while locked "for the player", which never runs over the keyguard. | FIX: unlocked-device-required for a newly made key; say what happens to an existing key. |
| B2-L9 | LOW | The doc says on Android 14 another app's item is refused "unless it is that app's own provider"; by the code an unnamed app's own provider is refused too. | DOC. |
| B2-L10 | LOW | The pasted token stays on the clipboard. | OWNER (with the screenshots question). |

Held: the IV is Keystore-made per seal; base, token and user id are one plaintext, so the base cannot be swapped; the
origin compare survives trailing dots, IPv6 default ports, user-info and case; the private rule fails toward asking;
the JSON reader survives 200k nested brackets; only `VideoHttp` sets headers, each a constant or guarded; no intent,
MediaItem or session metadata carries a token; the secret field reads as dots in all 140 UI dumps. The search of all
807 dev-video files found no real credential (the redaction in D_SERVER-run2 confirmed; the one dummy match is B2-L2).

---

# Round 2 — review A's re-review of the capture fixes (87a71840), 2026-10-05

Verdict: **FAIL — one HIGH (from platform source, not seen on a device), two MEDIUM, six LOW.** Real Gradle runs this
time: every fix's removal fails a named test; 58 mutations, 41 caught, 17 survived (11 of them on the Android-side
ports and call sites, which no JVM test executes). Round 1's A-M1, A-M2, A-L1, A-S, A-L5, A-L6 are closed.

| # | Sev | Finding | Triage |
|---|---|---|---|
| A2-F1 | HIGH | The forwarded-result case is not closed, and round 1's API 35+ branch widened it. Condition (d) is asked about `getCallingPackage()` — the app that RECEIVES the result — never about the app that started the capture; `getLaunchedFromUid()` is -1 unless the starter shares its identity (verified in ActivityClientController on android14 / android16: a for-result start makes no difference), so the cross-check never fires against an attacker. A permission-less app T, itself started for a result by an app V that holds WRITE_CONTACTS or runs as the system uid, starts the Camera with FLAG_ACTIVITY_FORWARD_RESULT and a contact's `display_photo` URI: T's start does not throw (the shell holds WRITE_CONTACTS), the caller reads as V, the platform says V may write, and after the user shoots and taps Accept another contact's photo is overwritten. MediaStore rows cannot be reached this way. | FIX (round 3, the last): on API 35+ the REAL starter must pass too — `getInitialCaller().checkContentUriPermission(uri, WRITE)`; refuse on false or any throw; both the recipient and the starter must pass. And A2-F2's narrowing removes the permission-wide recipient altogether. On API 34 only "the provider is the caller's own" or an explicit grant remain; the residual (a victim that owns the provider and starts the attacker for a result) is recorded for the owner. E9's forward leg is rewritten with a URI the SHELL can already write (the leg as written would throw on the sender and falsely reassure). |
| A2-F2 | MEDIUM | "The provider itself, asked, says the caller may" is false for every provider but MediaStore: `checkContentUriPermissionFull` is a MANIFEST answer (system uid; the owner; an exported provider whose write permission the uid holds — or that declares NO write permission at all), and runs the provider's own code only for a `forceUriPermissions` provider. So it says yes for any uid on `content://mms/part/<n>` (guarded by an app op in code, not by a manifest permission). Not exploitable today only because the shell itself cannot write those. | FIX: the third branch only when the provider is `forceUriPermissions` (MediaStore); the port method renamed; the KDoc and the Decisions line corrected. A caller holding a permission-wide access with no grant is REFUSED on every Android version — the conservative reading of the 10:59 line, which closes that owner item unless he rules otherwise. |
| A2-F3 | MEDIUM | The real ports and call sites have no test: eleven mutations applied together (the port failing OPEN on an exception, the grant check inverted, a String extra taken as the output, both strips deleted, the refused branch not finishing, one ledger file for all processes …) and the whole suite still passed. | FIX: the ports thinned so the logic sits in tested rules (the port returns the raw answer or the thrown class; the rule maps it, failing closed); source-scan tests of the call sites in the form of `video/TrustWiringScanTest`; device legs for the rest (the ledger across two processes; the refusal on an unreadable request). |
| A2-L1 | LOW | The second location strip (`writeToCaller`) swallows a failure and still writes. Not reachable with a location today (the capture answer never starts a location feed). | FIX: fail closed (RESULT_CANCELED with a reason). E9's leg (f) shows absence of GPS, not that the strip works: said so in the row. |
| A2-L2 | LOW | `CaptureRequestRule.decide` catches Exception; an Error while unparcelling hostile extras would crash `:camera`. | FIX: catch Throwable. |
| A2-L3 | LOW | The pending ledger accepts any `content://` line and the cleanup would hand it to `delete` (only the shell can write that file; the row must also read back as the shell's pending row). | FIX: only `content://media/` lines. |
| A2-L4 | LOW | Several `MediaWrites` objects per process over one ledger file can lose each other's updates (worst case a leaked pending row). | FIX: one ledger object per process. |
| A2-L5 | LOW | Work-profile, clone and private-space callers that pass EXTRA_OUTPUT are always refused (the platform rewrites the URI with the user id). Fails closed. | NOTE for the owner at sign-off. |
| A2-L6 | LOW | `"wt"` may be refused by a provider that takes only `"w"`. Fails closed. | ROW: the display_photo leg records it. |
| A2-S | — | Survivors worth a test: `?` and `#` ending the authority in `ContentUriText.parse`; the rule parsing a trimmed or lower-cased text; the guard-inputs line's authority unbounded (caller text into a `[camera]` line). | FIX: tests, and bound that line. |

For the other parts (not reviewed by A): the viewer and the player use the same manifest answer for READING, so A2-F2
applies there (yes for every uid on an exported provider with no read permission, and for a system-uid launcher), the
read then being made with the shell's identity — review C's second round has this; the two access ports should be
joined in the same change. `CameraProcess.startOnce` wipes `cacheDir/camera` at process start in a cache dir all the
shell's processes share.

Side effect reported by the reviewer: it ran `./gradlew --stop` at about 17:50, which stops every Gradle daemon of
this user; a build of another session that failed then with a vanished daemon is re-run, not a defect.

---

# Round 2 — review C's re-review of the viewer, player, network and CI fixes (87a71840), 2026-10-05

Verdict: **FAIL — no HIGH, three MEDIUM, nine LOW.** Real Gradle runs: 18 of 18 guards removed fail a named test; rule
mutations 56 of 61 caught; the network config 24 of 24 caught in the two known files. Round 1's C-M4, C-M1, C-M3, C-L1,
C-L6, C-L7 are closed at the rule level. Nothing found lets another app read, write or take anything. Nothing is opened
before the decision; the refused paths are no oracle; the viewer's read-only mode reaches only Share, File
information, zoom and Back; the built APK's 26 exported components equal the allow-list's 26 lines.

| # | Sev | Finding | Triage |
|---|---|---|---|
| C2-M1 | MEDIUM | Every call site of the C-M4 fix is proven by nothing: 11 of 11 wiring mutations survive (`ownCaller = true`, `callerMayRead = true`, the queue read for every caller, the launch answer true on an exception, the grant check asking WRITE, the viewer passing its own uid as the launcher, a refused URI still opened, `mayChange = true`, the bar ignoring the rule, the provider question true on an exception). `mayChange` has a fail-OPEN default value. The API 35+ branches have never been seen to answer on a device. | FIX: `mayChange` required; the decision-to-state mappings as pure, tested functions; source-scan tests of both activities (the form of `video/TrustWiringScanTest`); the device legs. |
| C2-M2 | MEDIUM | A shadow copy of the network security config in `src/release/res/xml/` or `res/xml-v34/` would replace the checked one and no test sees it. | FIX: a test that the only files of that name under `app/src/*/res/xml*/` are the two known ones. |
| C2-M3 | MEDIUM (latent) | The player's rule covers only the launch URI: its data source opens `content:` / `file:` / `asset:` for anything the media source asks for, with the shell's identity. Safe today only because no playlist format (HLS, DASH) is linked; the day one is (phase 20's radio), any caller's playlist can name the shell's files or any MediaStore item. | FIX: the upstream wrapped so a network source opens only http(s) and a content source only its own launch URI; a test that pins it. |
| C2-L1 | LOW | The two helpers implement one rule twice and differ: the viewer refuses an authority with `@` or `%` (so a work-profile / clone / Secure Folder "Open with" is refused on every Android version), the player passes it to the platform (verified correct: the platform splits user and authority itself). | FIX: ONE rule and ONE port for both helpers and the capture answer. `@`: the platform's own handling for the two readers (so a cross-profile "Open with" works where the platform says the launcher could read it); the capture answer keeps refusing it (a write). |
| C2-L2 | LOW | Docs: the 16:32 Decisions line and the fixes file say on Android 14 another app's item is refused "unless it is that app's own provider" — by the code an unnamed app's own provider is refused too; "a test scans every URL literal" scans Kotlin under app/src/main only; the allow-list's PlayerActivity line omits that a caller's `title` sets the session title. | DOC (the lead), after C2-L3 / L7. |
| C2-L3 | LOW | `EXTRA_TITLE` is honoured from any caller (200 cleaned characters in the system media controls under the shell's name). | FIX: the shell's own launches only. |
| C2-L4 | LOW | On Android 14 both helpers stay registered VIEW handlers yet refuse every other app's content URI. | NOTE for the owner (his phone is on Android 16). |
| C2-L5 | LOW | The viewer still decodes another app's image in the LAUNCHER's process (which holds SMS, call, contacts and location access); the probe still runs ExifInterface and MediaExtractor on the whole picked file there. | OWNER — asked at the gate; the reviewer presses for moving the viewer to `:photosedit`. |
| C2-L6 | LOW | CI: the debug APK no longer reaches a release, but: `r4probe-debug.apk` (debuggable, throwaway key) is still published; the APK is chosen by `find … | head -1`, not by variant path, and nothing asserts it is non-debuggable or signed by the expected certificate; a debug APK already on "latest" stays until a signed run; the keystore sits on disk for every later step; actions are pinned by tag. Not run. | FIX (small, unrunnable here): the APK taken from the release output path; the job fails unless the published APK is not debuggable. The rest is NOTED for the owner: it is phase 01's CI and he should look at the "latest" page. |
| C2-L7 | LOW | The URL-literal scan is a tripwire, not a guard: 10 of 12 hiding forms survive (a built scheme, `URL("http", host, …)`, a Java source, a string resource, the `:calc` module). | FIX (cheap half): also scan `*.java`, `res/values*`, the manifest and `:calc`; flag `"://"` and `URL(` / `Uri.Builder().scheme(` outside `net/`; the doc says "tripwire". |
| C2-L8 | LOW | The platform's read answer trusts manifest permissions and asks the provider only for MediaStore; a provider that enforces reads only in code would be answered "any uid may read". None known in AOSP's media providers. | FIX with A2-F2: the "provider says" branch for `forceUriPermissions` providers only; other providers need the caller's own provider or a grant, or the launch answer. |
| C2-L9 | LOW | The player resolves a caller-named `file:` path before the decision; a cleaned line can still read like another line within its 80 characters; `?` / `#` authority terminators untested. | FIX: decide first; the tests. Rows match whole lines. |

Device evidence since (the Photos row writer's TRUST_PHOTOS, run 3, build c7336aca): leg (j) FAILS — an app holding
READ_MEDIA_IMAGES that VIEWs a MediaStore image is REFUSED, where the fix expects it shown read-only. The first time
the API 35+ launch answer was asked on a device, it did not say yes. The round-3 fixer reads that run's folder.
