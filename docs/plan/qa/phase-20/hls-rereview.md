# Phase 20 — the HLS re-review (the tripwire of `video/PlayerAccessTest`)

2026-10-07, integration builder. Phase 20 links `media3-exoplayer-hls` 1.9.0 on purpose (Decisions "HLS", r3 D10;
`app/build.gradle.kts:121`). `PlayerAccessTest`'s test "C2-M3 no playlist media source is linked" failed on the merge, as
it was written to, and named four points to re-review before it was changed. This is that re-review.

**How it was read.** No sources jar is in the Gradle cache, so Media3 was read from bytecode: `classes.jar` of
`media3-exoplayer-hls-1.9.0.aar`, `media3-exoplayer-1.9.0.aar`, `media3-datasource-1.9.0.aar` and
`media3-common-1.9.0.aar`, with `javap -p -c`. Every "Media3:" line below is a method read that way. Paths are relative
to the repo root; `K` = `app/src/main/kotlin/app/tileshell`, `T` = `app/src/test/kotlin/app/tileshell`.

## What Media3 1.9.0's HLS module does with data sources

- **M1. One factory, for everything.** `DefaultMediaSourceFactory$DelegateFactoryLoader.loadSupplier` loads
  `androidx.media3.exoplayer.hls.HlsMediaSource$Factory` by name and constructs it with the loader's own
  `dataSourceFactory` field (the `(Class, DataSource.Factory)` `newInstance`). `HlsMediaSource$Factory(DataSource.Factory)`
  wraps it in `DefaultHlsDataSourceFactory`, whose only field is that factory and whose `createDataSource(int dataType)`
  is `dataSourceFactory.createDataSource()` for every type.
- **M2. Who asks it.** A scan of every class of the HLS jar for `createDataSource`, `new …DataSource`,
  `DataSource$Factory.<init>`, `java/net/URL`, `openConnection`, `ContentResolver`, `FileInputStream`,
  `RandomAccessFile` finds only:
  - `DefaultHlsPlaylistTracker.start` — `createDataSource(4 = MANIFEST)` for the multivariant playlist;
  - `DefaultHlsPlaylistTracker$MediaPlaylistBundle.<init>` — `createDataSource(4)` for every media / nested playlist and
    every reload;
  - `HlsChunkSource.<init>` — `createDataSource(1 = MEDIA)` → `mediaDataSource` (segments, parts AND init segments:
    `HlsMediaChunk.createInstance` is handed `mediaDataSource`), and `createDataSource(3 = DRM)` → `encryptionDataSource`
    (the `EXT-X-KEY` URI, `HlsChunkSource$EncryptionKeyChunk`);
  - `HlsMediaChunk.buildDataSource` — `new Aes128DataSource(upstream, key, iv)`, which only WRAPS the media data source
    it was handed (it opens nothing itself);
  - `HlsInterstitialsAdsLoader` (and its `AdsMediaSourceFactory`) — `new DefaultDataSource.Factory(context)`: a data
    source of its OWN. It is referenced by nothing but itself and `AssetListParser`; a player reaches it only when the
    app constructs one and wires it in (`setLocalAdInsertionComponents` / an `AdsLoader.Provider`). Neither player does;
    `HlsInterstitialsAdsLoader` appears nowhere under `K`.
- **M3. Other modules the same loader would pick up.** `loadSupplier` also names `dash.DashMediaSource$Factory` and
  `smoothstreaming.SsMediaSource$Factory` (handed the factory, like HLS) and `rtsp.RtspMediaSource$Factory`, constructed
  with NO data source factory (its own sockets). None of the three is on the classpath; the new test pins that.
- **M4. The default data source.** `DefaultDataSource.open` dispatches on the exact scheme string: no scheme or `file`
  (`Util.isLocalFileUri`: `TextUtils.isEmpty(scheme) || Objects.equals("file", scheme)`) → file, or asset when the path
  starts `/android_asset/`; `asset`; `content`; `rtmp`; `udp`; `data`; `rawresource` / `android.resource`; anything else
  → the http base source.
- **M5. Redirects.** `DefaultHttpDataSource.handleRedirect` throws for a `Location` whose protocol is not `http` /
  `https` ("Unsupported protocol redirect") and, with `allowCrossProtocolRedirects` false, for one whose protocol differs
  from the request's ("Disallowed cross-protocol redirect"). A same-scheme redirect is followed INSIDE the http data
  source: no `DataSource.open` is called for the new address, so no guard sees it.
- **M6. DRM.** A playlist's `EXT-X-KEY` with a DRM `KEYFORMAT` only yields `DrmInitData` in a format; a licence request
  needs the item's own `drmConfiguration`, which neither player sets (`K/video/PlayerActivity.kt:272-275`; the four
  builders under `K/music/`). No licence source is reachable.

## (a) The VIDEO player — the test's four points

**(1) A network launch still admits only http(s) — HOLDS.**
`PlayerAccess.mayOpen` (`K/video/PlayerRules.kt:112-118`): for an `http` / `https` launch the asked address's scheme,
lower-cased, must be `http` or `https`. A playlist entry naming `content:`, `file:`, `asset:`, `android.resource:`,
`rawresource:`, `data:` (an inline key), `rtmp:`, `udp:` or no scheme is refused before anything opens it. Pinned:
`T/video/PlayerAccessTest.kt` "C2-M3 a network launch opens only http and https - a redirect or a nested reference to
anything local is refused" (`data:video/mp4;base64,…` is one of its cases).

**(2) Every segment, key, init segment and nested playlist goes through `GuardedDataSource` — HOLDS.**
`K/video/VideoPlayback.kt:37-48`: the http source (`setAllowCrossProtocolRedirects(false)`, `:40`), `DefaultDataSource`
over it (`:41`), the token resolver over that (`:42`), and OUTERMOST `GuardedDataSource(resolved.createDataSource(),
mayOpen)` (`:44`), which is the factory given to the one `DefaultMediaSourceFactory` (`:47-48`). By M1 / M2 that factory
makes every data source HLS uses — playlists, segments, parts, init segments, keys — and `Aes128DataSource` wraps one of
them. `GuardedDataSource.open` (`:105-108`) asks before `upstream.open`. Pinned: `T/media/UriAccessWiringScanTest.kt`
`playerProblems` (`:1053`; the factory's one form, `.setDataSourceFactory(sources)` once, `open`'s one form) and its
mutation twin. The player builds no `HlsInterstitialsAdsLoader` and sets no `drmConfiguration` (M2, M6).

**(3) The server token does not ride on a segment request to another host — HOLDS.**
The resolver is made only for the shell's own launch of an address whose path ends `/stream`
(`K/video/PlayerActivity.kt:256-257`, `PlayerAccess.serverToken`, `K/video/PlayerRules.kt:132-136`). It is asked per
REQUEST (`ResolvingDataSource`), and `MediaServer.streamResolver` (`K/video/server/MediaServer.kt:170-181`) adds
`ApiKey` only when `ServerStore.streamToken(url)` (`K/video/server/ServerStore.kt:145`) says so, which is
`ServerRules.mayCarryToken(url, sealedBase)` (`K/video/server/ServerRules.kt:273-282`): the request's scheme, host and
port must equal the SEALED server's, its path must be exactly `/Videos/<id>/stream`, and it must name no key of its own.
So a segment, key or nested playlist on any other host gets no token; one that names the saved server's own
`/Videos/<id>/stream` gets it — sent to the server the token belongs to, nobody else. What the player reports as opened
has the token removed (`resolveReportedUri`, `K/video/VideoPlayback.kt:89`), and HLS resolves a playlist's relative
entries against that reported address, so no derived address carries it. Pinned: `T/video/ServerRulesTest.kt`
(`mayCarryToken`), `T/video/PlayerAccessTest.kt` (`serverToken`).

**(4) A content launch of a playlist opens only the launch URI — HOLDS.**
For any launch that is not http(s), `mayOpen` is `asked == launchUri` (or the shell's own subtitle) —
`K/video/PlayerRules.kt:117`. A playlist read from a `content:` (or the shell's own `file:`) launch has every entry
refused: a relative one resolves to another `content:` address, an absolute `http` one is not the launch URI. Pinned:
"C2-M3 a content launch opens only the launch URI itself - and the subtitle the shell found beside its own item".

**Nothing in (a) needed a change.** One observation, not a defect of this phase: a network launch's playlist may name
http(s) segments on a private host. The video player is exported and takes an http launch from anyone (Q-D: A; the home
server is on the private network), so a launch can already name such a host directly; the playlist adds no reach.

## (b) The MUSIC player — a gap this phase's dependency opened, closed here

**The gap.** `MusicService` built `DefaultMediaSourceFactory(this, extractors)`: a bare `DefaultDataSource`, which opens
every scheme of M4 with the shell's identity. `StationUrl.accept` weighs a station ITEM's address
(`K/music/radio/StationUrl.kt:58`), but with HLS linked a station's PLAYLIST names segments, keys, init segments and
nested playlists (M2), none of which is an item: `file:///data/user/0/app.tileshell/files/…`, `content://…`,
`asset:///…`, `data:…`, and `http://192.168.1.1/…` or `http://127.0.0.1/…` all reached the data source unweighed. The
spec's rule (Decisions "untrusted URLs never reach ExoPlayer's other schemes", r3 D11 / D12) did not hold.

**The fix: the same guard as the video player, at the data source.**
- `K/music/MusicService.kt:129-135`: `DefaultDataSource.Factory(this)` wrapped, outermost, in `GuardedDataSource` asking
  `MusicSources.own.mayOpen(asked, qaHost)`; that factory is the one `DefaultMediaSourceFactory`'s, which is the factory
  of the service's player (`:136`) and of the crossfade's fader (`:171`; `K/music/CrossfadeFader.kt:143`). There is no
  other data source, media source or player under `K/music/`.
- The pure rule, `MusicSourceRule.mayOpen(asked, queued, qaHost)` (`K/music/MusicSourceRule.kt`), in order:
  1. `asked` is, character for character, an address the shell itself queued → open (any scheme). That is a library
     track's MediaStore URI, phase 18's play-file URI, a station's accepted address, a home-server track's address — the
     home server is rightly on the private network — and so every re-open of it (a reconnect, a seek's range request);
  2. else not `http` / `https` → refused;
  3. else `StationUrl.accept(asked, qaHost)` must be `Ok`: a public host. Loopback, link-local, RFC 1918, unique-local
     and unspecified literals, `localhost`, and every spelling only an address parser reads as an address are refused, so
     a playlist is not a way to make the phone call a device on its own network. `qaHost` is `RadioNet.qaHost` — the
     debug-only fixture host, null in a release build — read once when the service starts (`MusicService.kt:130`).
- **How the guard learns the queued addresses, with no second `localConfiguration` read.** The four `.setUri(` sites
  under `K/music/` — the only ones (`UriAccessWiringScanTest.stationProblems`) — hand the address through
  `MusicSources.own.queued(…)` on its way into the item: `MusicService.mediaItem` (`:599`) and `fileItem` (`:433`) via
  the service's `queued(uri)` (`:594`), `StationItem.build` (`K/music/radio/StationItem.kt:77`), `ServerTrackItem.build`
  (`K/music/server/ServerTrackItem.kt:51`). The Music screens' controller and the service run in one process (the
  manifest gives neither a `android:process`), so an item Music builds and the session keeps (`MusicItemRule` Keep, own
  uid only) is already in the set; a stranger's item never keeps its address, so it never reaches a builder with one.
  The set is in memory, only grows, and is not saved (resumption is not answered).

**Pinned.** The rule, every case: `T/music/MusicSourceRuleTest.kt` (queued addresses incl. a private server track and
its re-open; public segments / keys / nested playlists allowed; private, loopback, link-local, unspecified and odd
spellings refused; the fixture host only with `qaHost`; every other scheme; whole-string matching). The wiring:
`T/media/UriAccessWiringScanTest.kt` `musicSourceProblems` (`:723`) and its two tests (`:762`, `:769`):
the service's one guarded factory in its one form, handed to the media source factory, the player and the fader; no
other data source / media source / player under `music/`; the set told only at the four builders; the fixture host
`RadioNet`'s; the rule's one form and its one asker. `UriAccessWiringScanTest`'s older clauses stay as strict as they
were (one `localConfiguration` read; four `.setUri(`), rewritten only for the `queued(…)` wrapper.

## Stated gaps (for the adversarial review, build task 12)

Both are Decisions D12's own, unchanged by this phase's guard, and apply to BOTH players:

1. **A same-scheme redirect to a private literal is followed.** By M5 the http data source follows up to 20 same-scheme
   redirects itself; the guard is asked once, for the address it was handed, and never for the `Location`. So a public
   station (or a public segment / key / nested-playlist address in its playlist) that answers `302 Location:
   http://192.168.1.1/…` — or `http://127.0.0.1:<port>/…` — makes the phone send that GET (with `Icy-MetaData: 1` and the
   player's User-Agent, no credential). The response reaches only the extractor: it is not shown or sent anywhere, but
   whether it answered, and how fast, is observable to the station through what the player asks for next. Redirect
   handling was deliberately NOT changed (D12: "Media3's default is kept"). Cross-protocol redirects and redirects to a
   non-http scheme stay refused (M5).
2. **A name that resolves privately is not checked.** `StationUrl.accept` reads the address's text and resolves nothing
   (`K/music/radio/StationUrl.kt` class comment). A host NAME whose DNS answer is a private, loopback or link-local
   address — for a station's own address, or for anything its playlist names — passes rule 3 and is connected to. DNS
   rebinding is the same gap seen over time.

Also for the review, not gaps of the rule: the queued set admits a private http(s) address only by exact match, and only
`ServerTrackItem.build` (after `accepts`) and `StationItem.build` (after `StationUrl.accept`, so private only for the
debug fixture host) can put one there; and the video player's network launch admits any http(s) host by design
(observation under (a)).

## Proved on the device (integration smoke, 2026-10-07; evidence qa/phase-20/build-notes-integration/)

With the guard in, through the service's player: a fixture station at the debug fixture host plays, reconnects and is
stepped through as a preset; a home-server track at a private address plays and SEEKS (the range re-open of the queued
address); a library track plays, scrubs and crossfades into the next (the fader opens the next track's MediaStore URI
through the same factory); phase 18's play-file plays a file the library does not list. NOT driven on the device: an
HLS station — none of the fixture's four stations is HLS (`hls` is 0 on every row) — so HLS through the guard rests on
the bytecode reading above and the JVM rule; and no refusal of a private segment was provoked on the device.

## The test that replaced the tripwire

`T/video/PlayerAccessTest.kt` "C2-M3 HLS is linked on purpose and is the only playlist or network source module - DASH,
SmoothStreaming and RTSP are not on the classpath": `HlsMediaSource` IS linked; `DefaultHlsDataSourceFactory` still has
exactly one field, the `DataSource.Factory` it was given (M1 — a Media3 upgrade that changes this fails here);
`DashMediaSource`, `SsMediaSource` and `RtspMediaSource` are NOT linked. Its comment names the tests that hold points
(1)–(4) and says what to re-review the day another module is added (RTSP in particular takes no data source factory).
