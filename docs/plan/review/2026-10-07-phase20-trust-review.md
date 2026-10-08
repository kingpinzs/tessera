# Phase 20 — adversarial review of the trust surface (build task 12, C-16 gate)

Reviewed: worktree `metro-launcher-p20-review` at `62eea325` (phase-20). One pass. Read-only on product code; every
mutation and the scratch test were reverted (`git status --short` empty at the end).
`K` = `app/src/main/kotlin/app/tileshell`, `T` = `app/src/test/kotlin/app/tileshell`.

How things were proved:
- **Scratch test** (a JVM unit test, deleted after the run) printed what the real rules answer. Its output is kept:
  `scratchpad/r20-scratch-lines.txt` (91 lines, exit code 0).
- **Mutations**: 36, each applied to the worktree, its test classes run with
  `./gradlew :app:testDebugUnitTest -Ptmdb.readToken=qa-dummy-token --offline --tests …`, output and exit code to
  files, then `git checkout -- <file>`. Log: `scratchpad/r20-mutation-log.txt`; per-mutation output `r20-Mnn.out` / `.rc`.
- **Media3 1.9.0 bytecode** (`javap -p -c` on the six `classes.jar` in the Gradle cache), read independently of the
  builder's re-review.
- NOT done: nothing was run on a device or an emulator, and no network service was contacted. Anything that needs the
  platform's HTTP stack or a real player is marked SUSPECTED.

---

## Findings (most severe first)

### R20-1 — MEDIUM — PROVEN (rule + bytecode) — phase 20
**The "no private host" rule (D11 / D12) stops accidents, not an attacker: a host NAME or one redirect reaches any
device on the home network.**

- Where: `K/music/radio/StationUrl.kt:58-64` (`accept`), `K/music/MusicSourceRule.kt:37-42`,
  `K/music/MusicService.kt:129-131` (the guarded factory over a stock `DefaultDataSource`),
  `K/music/radio/StationLogo.kt:20-21` + `K/music/radio/StationLogos.kt:32-35` (the logo fetch uses the same rule).
- Attack (anyone can add a station to the community directory; no account):
  1. **By name.** Station address `http://192.168.1.1.nip.io/cm?cmnd=Power%20Toggle` (or any A record the attacker
     owns, or `http://nas/…`, `http://router.lan/…`). The rule reads text only → `Ok`. The phone connects to
     192.168.1.1.
  2. **By redirect.** A public station answers `302 Location: http://192.168.1.1/…` or `http://127.0.0.1:<port>/…`.
     `DefaultHttpDataSource` follows up to 20 same-scheme redirects inside itself; the guard is never asked.
  3. **Same two for everything an HLS playlist names** (segments, keys, init segments, nested playlists) and **for
     the logo** (`favicon`), which is fetched without playing: for every favourite when the Radio pivot draws, and
     for the playing station. The logo fetch follows no redirect, but a name works.
- What the attacker gets on Jeremy's network: an unauthenticated **GET with a path and query of his choosing to any
  host and port** the phone can reach, including the phone's own loopback. That is enough to fire GET-actuated
  devices (Tasmota `…/cm?cmnd=…`, Shelly `…/relay/0?turn=on`, many cameras and printers). With an HLS playlist he can
  list many such addresses and read back, from how fast the player returns to his server for the next segment or
  retry, which hosts and ports answered: a LAN scan run from the phone. He does NOT get response bodies (they go to
  the extractor; audio would be played aloud to Jeremy), and no credential rides on any of it (R20 item 6 held).
- How it is reached without a suspicious tap: `MusicSearch.resolve` falls back to the directory's exact name for a
  plain "play <words>" with no library match, and to name-contains for "<words> radio" — over the keyguard too. The
  directory orders by click count, which anyone can raise.
- Proof:
  - scratch: `accept http://192.168.1.1.nip.io/relay/0?turn=on => Ok | mayOpen=true`; `accept http://nas/x => Ok`;
    `accept http://router.lan/x => Ok`; `logo url nip.io => http://192.168.1.1.nip.io/favicon.ico`;
    `resolve 'thriller' with empty library => STATION evil-1 plan=Play(… url=http://192.168.1.1.nip.io/cm?cmnd=Power%20Toggle …)`.
  - bytecode: `DefaultHttpDataSource`: `MAX_REDIRECTS = 20`, `setInstanceFollowRedirects(false)` then its own
    `handleRedirect` loop, which refuses only a non-http(s) or cross-protocol `Location`.
    `DefaultDataSource$Factory(Context)` builds a plain `DefaultHttpDataSource$Factory`.
- This is the builder's two "stated gaps" (`hls-rereview.md`). Stated is not closed: D12's sentence "a station must
  not be a way to make the phone call a device on the network it is on" is false as built.
- Would be HIGH if a GET-actuated device is known to be on the LAN; no data or credential leaves, so MEDIUM.
- Smallest fix at the producer (the HTTP source, one place for both gaps and for rebinding): give the music player an
  http data source whose **connection-time address** is checked — `media3-datasource-okhttp` with an `OkHttpClient`
  whose `Dns` refuses any answer that is loopback / link-local / site-local / unique-local / CGNAT / unspecified
  (`InetAddress` predicates), except for the host of a queued private address (the home server) and `qaHost`. OkHttp
  asks `Dns` for every connection, redirects included. Use the same client (or the same `InetAddress` check before
  connect) in `StationLogos`. Without a new dependency the half-fix is: resolve the host in the guard and refuse a
  private answer — that closes "by name" but not "by redirect".

### R20-2 — MEDIUM — SUSPECTED (mechanism bytecode-verified; not driven on a device) — phase 20
**A station's stream can put its OWN embedded picture, any size, into Now Playing and the Radio row, decoded with no
bounds (D2 bypassed).**

- Where: `K/music/MusicPlayer.kt:154` (`if (live) c.mediaMetadata.artworkData?.let { liveArt = it }`),
  decoded at `K/music/MusicNowPlaying.kt:339` and `K/music/MusicRadioPages.kt:161-162`
  (`BitmapFactory.decodeByteArray(bytes, 0, bytes.size)` — no bounds read, no sample size, no byte cap);
  the gap is `K/music/KnownDurationPlayer.kt:50-56` (the corrected value is posted, not delivered first).
- Mechanism: an MP3 stream that opens with an ID3 `APIC` frame, or an Ogg / FLAC stream with a picture block, gives
  ExoPlayer `artworkData` (`ApicFrame` / `PictureFrame` call `maybeSetArtworkData`). ExoPlayer's
  `updatePlaybackInfo` queues the tracks event BEFORE the metadata event. Media3's session listener, on the tracks
  event, posts `MSG_PLAYER_INFO_CHANGED` (only if none is pending); on the metadata event it stores the RAW metadata
  (`playerInfo.copyWithMediaMetadata(arg)`). `KnownDurationPlayer` posts its "retell" during the metadata event —
  after that message. So the message runs first and sends the raw value to every Media3 controller, the shell's own
  included. This is the "one raw frame" the builder saw. `readSession` cleans the title but keeps the raw
  `artworkData` as `liveArt`; the corrected frame carries the logo or NO art, and `?.let` never clears, so with no
  logo the stream's picture stays.
- Effect: the picture is the stream operator's. A few-megabyte PNG that decodes to more than 100 MB makes
  `Canvas` throw "trying to draw too large bitmap" in the draw pass → the launcher process dies (and the service
  with it) each time that station plays with Now Playing or its row on screen. A smaller one simply shows the
  operator's image where D2 says only the shell's bounded logo may show.
- Proof: bytecode — `MediaSessionImpl$PlayerListener.onMediaMetadataChanged` (`copyWithMediaMetadata` →
  `sendPlayerInfoChangedMessage`), `PlayerInfoChangedHandler.sendPlayerInfoChangedMessage` (guarded by
  `hasMessages`), `handleMessage` reads `playerInfo` when it runs; `ExoPlayerImpl.updatePlaybackInfo` event order.
  Mutation M34 (the wrapper built from the STREAM's metadata instead of the item's) SURVIVED every test under
  `music/`, `media/` and `video/*ScanTest`: nothing holds this path.
- Smallest fix at the producer: `MusicPlayer.readSession` never takes art from the session for a live item — the
  service and the screens are one process, so read `StationLogos.get(ctx).cached(station)` (already bounded). If the
  session read is kept: accept it only when `StationLogo.accept(bytes)` and decode both sites through
  `StationLogo.sampleSize`.

### R20-3 — MEDIUM — rule PROVEN, end effect SUSPECTED — phase 20 (robustness, not a trust break)
**A station whose address ends `.mpd`, `.ism` or `.isml` passes both URL rules and makes the whole queue set fail
silently; one such favourite stops every favourite from starting.**

- Where: `K/music/radio/StationUrl.kt:48-55` (`playable` refuses only `.pls` / `.m3u` / `.asx`).
- Mechanism: with no MIME type on the item, Media3 infers DASH / SmoothStreaming from the path
  (`Util.inferContentTypeForExtension`: `mpd`→0, `ism` / `isml`→1). Neither module is linked (the phase's own
  test pins that), so `DefaultMediaSourceFactory.createMediaSource` throws `IllegalStateException`
  (bytecode: `getMediaSourceFactory` → `ClassNotFoundException` → `new IllegalStateException(e)` → `athrow`).
  `ExoPlayerImpl.setMediaItems` calls it for EVERY item before touching the queue. Media3's
  `Util.postOrRunWithCompletion` catches `Throwable`, so there is no crash and no error shown: the queue is not
  replaced, then `MusicPlayer.playOwn` calls `prepare()` and `play()` on the OLD queue and writes
  `play station <name> (n of m)`; Tess says "Playing <name>."
- A favourite queue is all favourites (`RadioFavourites.queueFor`), so one such favourite breaks every favourite
  tap, next / previous and "play radio" until it is removed.
- Proof: scratch — `playable http://stream.example.net/live.mpd => Stream(…, mimeType=null) | plan=Play(…)`, same
  for `…/radio.ism/manifest`, `…/x.isml`, `…/x.MPD`. Bytecode as above.
- Smallest fix: in `StationUrl.playable`, refuse a path Media3 would infer as DASH or SmoothStreaming (the same
  suffix list plus `.ism/` and `.isml/` segments) as `Playable.Playlist`.

### R20-4 — LOW — PROVEN (mutations) — phase 20
**The D9 / D2 wiring is held by no test.** The pure rules are well tested; what calls them is not.

| Mutation | Result |
|---|---|
| M33 `MusicPlayer.readSession`: raw `c.mediaMetadata.title` for a station instead of `LiveMetadata.merge` | SURVIVED |
| M34 `KnownDurationPlayer.getMediaMetadata`: built from the stream's metadata, not the item's | SURVIVED |
| M36 `KnownDurationPlayer`: raw StreamTitle to the tile and the notification (`RadioText` skipped) | SURVIVED |
| M35 `StationLogos`: `VideoHttp.bytes(it)` — no header (so redirects are followed), 8 MiB instead of 512 KiB | SURVIVED |

Tests run for each: `app.tileshell.media.*`, `app.tileshell.music.*`, `app.tileshell.video.*ScanTest`, exit code 0.
Fix: four clauses in `UriAccessWiringScanTest` (the one-form style it already uses) for those four call sites.

### R20-5 — LOW — rule PROVEN, platform half SUSPECTED — phase 20
**Non-ASCII spellings of a private host pass `StationUrl.accept`.** The rule compares ASCII; Android's HTTP stack
runs the host through IDNA first.

- Scratch: `http://127。0。0。1/x` (U+3002) `=> Ok | IDN.toASCII(host)=127.0.0.1`; circled digits
  `①⑨②.①⑥⑧.①.①` `=> Ok | IDN=192.168.1.1`; fullwidth `ｌｏｃａｌｈｏｓｔ` `=> Ok | IDN=localhost`.
  (Fullwidth DIGITS are refused, by luck: `ServerRules.ipv4` uses `Char::isDigit`.)
- Fix: in `hostOf`, refuse any host character outside `a-z 0-9 . -` (and hex digits and `:` inside brackets). The
  R20-1 fix covers it too.

### R20-6 — LOW — PROVEN — phase 20
**The literal list is short, and one malformed authority is accepted.**

- Accepted as public: `100.64.0.0/10` (carrier-grade NAT — Tailscale's range), `198.18.0.0/15`, `192.0.0.0/24`,
  `224.0.0.0/4`, `255.255.255.255`, `[fec0::1]`, `[ff02::1]`, `[64:ff9b::c0a8:101]` (NAT64 of 192.168.1.1),
  `[2002:c0a8:101::1]`. Scratch lines `accept http://100.100.100.100/x => Ok` and the rest.
- `http://127.0.0.1:80:80/x => Ok | mayOpen=true`: the rule reads it as an IPv6-like name. `java.net.URL` throws
  `MalformedURLException` for it (run with `java`), so it fails closed — but the rule's promise ("what this rule
  reads and what the platform's parser reads must be the same host") is not kept.
- Fix: add the ranges; refuse an unbracketed authority with more than one `:`.

### R20-7 — LOW — PROVEN (mutations) — phase 20
**Four load-bearing checks have no test.**

| Mutation | Why it matters |
|---|---|
| M03 `substringAfterLast('@')` → `substringAfter('@')` SURVIVED | `http://a@b@localhost/` would be accepted; the tests' double-`@` cases all end in a numeric host, which another rule catches |
| M09 `%` in a host no longer refused SURVIVED | `http://%6c%6f%63%61%6c%68%6f%73%74/` would be accepted; the tests' only `%` case is numeric |
| M07 `host == qaHost` → `host.endsWith(qaHost)` SURVIVED | debug builds only: `110.0.2.2` would count as the fixture host |
| M17 `CoverArt.mayFollow` no longer refuses `:` SURVIVED | a port; the suffix check still catches the tested cases |

M26 (`ServerTrackItem.accepts` without `!mayCarryToken`) also survived, but is equivalent: an `/Audio/…` path can
never match the token rule's `/Videos/…` form.

### R20-8 — LOW — PROVEN — phase 20
**"listen to <x> on <app>": an unknown app's exact label beats a table app's prefix.** `byLabel` looks for an exact
label across ALL entries before any prefix. An installed app that declares `CATEGORY_APP_MUSIC` and calls itself
"YouTube" takes "listen to … on youtube" from YouTube Music; one called "Pandora" takes Pandora's place when Pandora
is not installed.

- Scratch: `byLabel youtube => evil.app`; `byLabel pandora (real one not installed) => evil.two`;
  `plan for spoof => [PlayFromSearch(query=secret words), Launch]`.
- It receives the spoken words and is brought to the front (after the unlock — the gate holds). It gets no data URI
  and nothing else. Tess then says "Opening <its own label>."
- Fix: in `byLabel`, try the table's rows (exact, then prefix) before any unknown app.

### R20-9 — LOW — rule PROVEN, platform half SUSPECTED — phase 20
**`CoverArt.mayFollow` accepts a non-ASCII authority.** `https://evil.example／.archive.org/x` (fullwidth solidus;
also `？ ＃ ＠ ：`) `=> true | IDN=evil.example/.archive.org`. Android's HTTP stack should reject the mapped host
(fail closed); not run. It also needs a hostile `Location` from coverartarchive.org or archive.org.
Fix: authority must match `[a-z0-9.-]+`.

### R20-10 — LOW — PROVEN — phase 20
**MusicBrainz strings are not cleaned or cut.** MusicBrainz is community-edited like the station directory, but
`MusicCatalogue.parse` only trims. Scratch: `catalogue title len => 600000`; artist kept as
`a U+202E b U+000A c`; `handoff query len => 600006`. These reach the catalogue rows and the `PlayFromSearch`
extra; an oversized extra makes `startActivity` throw a `RuntimeException` that `StreamingHandoff.start` does not
catch (SUSPECTED crash). Fix: `RadioText.shown(…, max)` on title, artist and release in `parse`.

### R20-11 — LOW — PROVEN by the builder's own test; effect SUSPECTED — phase 20
**The queued set lets an HLS playlist name a local address the shell queued earlier.** `asked in queued` passes any
scheme, the set never shrinks, and library URIs are guessable (`content://media/external/audio/media/<id>`); a
stranger's controller can add any library id to it (`Rebuild` → `mediaItem`). A hostile playlist naming such a URI
as a segment makes the phone play Jeremy's own file as "the station" and tells the operator, by what is asked for
next, whether that id exists. Nothing leaves the phone. Fix: the guard also asks whether the loaded item is live
(a flag the service already has); while it is, only http(s) may open.

### R20-12 — LOW — SUSPECTED (code read) — phase 20
**Any app can drive the directory's click counter from Jeremy's phone.** `StreamWatch` counts a `SEEK` transition as
"user started" whoever sent it; a stranger's controller calling next / previous over a favourites queue fires
`GET /json/url/<uuid>` and a favourites-file write each time (`K/music/radio/StreamWatch.kt:134, 242-246`).
Fix: count it only when the session's current controller is the shell's own.

### R20-13 — LOW — PROVEN — phase 20
**`RadioText.shown` keeps combining marks.** A name of one letter and 77 stacked marks comes out whole
(`shown zalgo len => 78`): it overdraws neighbouring rows and the tile. Bidi overrides, isolates, controls, line
separators and lone surrogates ARE removed (scratch lines; M19–M21 caught). Fix: cap consecutive combining marks.

### OLDER-PHASE (for the ledger, not this phase's fix round)

- **R20-O1 — LOW — PROVEN — phase 17.** `ServerRules.isPrivate` answers false for `0.0.0.0`, `::`,
  `0:0:0:0:0:0:0:1`, `0000:…:0001`, `::ffff:127.0.0.1`, `[::ffff:192.168.1.1]`, `100.64.0.1`, `fec0::1`
  (scratch lines). Phase 17 uses it for the "isn't secure" prompt, where false means "ask first" — the safe side.
  `StationUrl` adds its own checks for the first six, so phase 20 is not exposed by them.
- **R20-O2 — LOW — phase 17, by design (Q-D).** The video player has R20-1's two gaps as well: a network launch
  from any app may name, redirect to or resolve to a private host. Recorded by the builder in `hls-rereview.md` (a).
- **R20-O3 — LOW — phases 10 / 18, by design.** `MusicService` accepts every controller with the default commands
  plus sleep, equaliser and crossfade: any installed app can read what is playing (titles, the station's name, the
  cleaned StreamTitle) and pause, skip, re-prepare, or set the sleep timer. It cannot read an item's address
  (Media3 strips `localConfiguration` for a controller in another process).

---

## The surface, item by item

| # | Item | Verdict | Why |
|---|---|---|---|
| 1 | D1 — exported `MusicService` | HELD | Every item of every controller goes through `MusicItemRule.decide`; `station:` / `server:` from a stranger is dropped before search (M11–M13 caught); stations only for the shell's uid (M14, M32 caught); `playFromUri` gives no `localConfiguration` and the service never reads `mediaUri`. The rewritten scan test is as strict as phase 18's: the removed lines are the old exact forms, replaced by the same forms with `queued(…)` and one more counted site. |
| 2 | The new guard | BROKEN | Exact-match, scheme and wiring hold (M01, M02, M31 caught; my own scan of the HLS jar agrees with the builder's M1 / M2: one factory, plus `HlsInterstitialsAdsLoader`, which nothing builds). But a name or a redirect walks past it — R20-1 — and it lets a playlist name a queued local address — R20-11. |
| 3 | D11 / D12 — `StationUrl` | BROKEN | Parsing tricks tested here are refused (userinfo, octal / hex / short / decimal, trailing dot, zone id, backslash, white space, `%`, IPv4-mapped). Not refused: names (R20-1), non-ASCII spellings (R20-5), CGNAT and other ranges, `host:80:80` (R20-6), DASH / SS paths (R20-3). `qaHost` is null in a release build by construction. |
| 4 | D2 — logos | BROKEN | The shell's own fetch holds: http(s) only, 512 KiB, bounds-first decode, no redirect, no `artworkUri` anywhere (grep and Media3 jars: no stream metadata sets one). Broken beside it: the stream's embedded picture reaches the screens unbounded (R20-2), and the logo host can be a private name (R20-1). The fetch's cap and header are untested (M35). |
| 5 | D9 — `RadioText` | NOT PROVEN | Right in the code today: every station field is cleaned in `Station.fromJson`, StreamTitle in `LiveMetadata.merge` at both sinks, Tess's reply and every line use cleaned text. But no test holds the two sinks (M33, M34, M36 survived), and the raw first frame is real (R20-2). |
| 6 | D4 — token scoping | HELD | No resolver, token or `Authorization` anywhere under `music/` (grep); the music player has no way to add one; `/Audio/<id>/stream` is built with no key and `accepts` re-checks it; lines go through `StreamLine.url` (userinfo, query, fragment removed — M22 caught). |
| 7 | D13 — cover art | HELD | https only, `archive.org` and subdomains, ≤ 3, no header carried, 8 MiB a hop (M15, M16, M18 caught). Hardening: R20-9. |
| 8 | Hand-off intents | HELD | Every intent names its package; play-from-search carries two extras and no data (M30 caught); a title cannot change a link's shape (M29 caught; scratch: `../../x?y#z&a=b` → fully encoded); `ListenOn` is gated when locked (M25 caught); "play <station>" goes to the session or the shell's own controller, no activity. Label spoofing: R20-8. |
| 9 | `QaBases` overrides | HELD | `read` returns null first unless `BuildConfig.DEBUG`; each of the three new prefs is read at one site behind `CatalogueRules.base(BuildConfig.DEBUG, …)`; the prefs file is the app's private one; `TrustWiringScanTest` names all eight. |
| 10 | Directory fetch and cache | HELD | 4 MiB a response, 40 pages, 32 MiB file, JSON depth 64, strings cleaned at one reader, mirrors limited to one label under `.api.radio-browser.info` (M23 caught), newlines escaped in the cache lines, favourites capped at 200. One loose end (a hostile mirror only): the countries list is not capped against the cache's tail room. |

## Mutation log

Caught = the test run failed (exit code 1). Full lines in `r20-mutation-log.txt`.

| Id | Rule | Mutation | Caught |
|---|---|---|---|
| M01 | `MusicSourceRule` | queued match ignores case | yes |
| M02 | `MusicSourceRule` | scheme not lower-cased | yes |
| M03 | `StationUrl.hostOf` | first `@` instead of last | **no** |
| M04 | `StationUrl` | `0.x.x.x` no longer refused | yes |
| M05 | `StationUrl` | trailing dot kept | yes |
| M06 | `StationUrl` | IPv6 all-zero first group no longer refused | yes |
| M07 | `StationUrl.accept` | `qaHost` matched by suffix | **no** |
| M08 | `StationUrl.hostOf` | space and backslash allowed | yes |
| M09 | `StationUrl.hostOf` | `%` in host allowed | **no** |
| M10 | `StationUrl.playable` | suffix check case-sensitive | yes |
| M11 | `MusicItemRule` | search branch moved above the station / server drop | yes |
| M12 | `MusicItemRule` | `myUid >= 0` removed | yes |
| M13 | `MusicItemRule` | `server:` no longer dropped | yes |
| M14 | `RadioSearchRule` | `myUid >= 0` removed | yes |
| M15 | `CoverArt.fetch` | a fourth redirect allowed | yes |
| M16 | `CoverArt.fetch` | headers carried to the next hop | yes |
| M17 | `CoverArt.mayFollow` | `:` allowed in the authority | **no** |
| M18 | `CoverArt.mayFollow` | empty-label check removed | yes |
| M19 | `RadioText` | format characters kept | yes |
| M20 | `RadioText` | lone surrogates kept | yes |
| M21 | `RadioText` | length cut off by one | yes |
| M22 | `StreamLine.url` | first `@` instead of last | yes |
| M23 | `RadioMirrors.order` | suffix only, no label check | yes |
| M24 | `StationLogo.sampleSize` | height limit removed | yes |
| M25 | `LockGate` | `ListenOn` allowed while locked | yes |
| M26 | `ServerTrackItem.accepts` | `!mayCarryToken` removed | no (equivalent) |
| M27 | `Station.fromJson` | name not through `RadioText` | yes |
| M28 | `LiveMetadata.merge` | StreamTitle not through `RadioText` | yes |
| M29 | `MusicServicesTable.link` | path query not encoded | yes |
| M30 | `HandoffPlan.PlayFromSearch` | a data URI added | yes |
| M31 | `GuardedDataSource.open` | check moved after the open | yes |
| M32 | `MusicService` | station search asked with the shell's uid for everyone | yes |
| M33 | `MusicPlayer.readSession` | raw session title for a station | **no** |
| M34 | `KnownDurationPlayer` | built from the stream's metadata | **no** |
| M35 | `StationLogos` | no header, 8 MiB cap | **no** |
| M36 | `KnownDurationPlayer` | raw StreamTitle to the session | **no** |

27 caught, 8 survived that matter (M03, M07, M09, M17, M33, M34, M35, M36), 1 equivalent (M26). No test was found that pins a defect as correct behaviour.

## Verdict

NOT PASSED as built: no HIGH, 3 MEDIUM (R20-1 the private-network rule is bypassed by a name or a redirect; R20-2 a
stream's own picture reaches the screens unbounded; R20-3 a DASH / SS address silently breaks the queue), 10 LOW,
3 OLDER-PHASE for the ledger; items 1, 6, 7, 8, 9, 10 held.
