# Music streaming and Radio — research addendum (2026-10-07 08:55)

For `docs/plan/phase-20-music-streaming-radio.md`. One Opus research agent, desk research only (vendor documentation,
source at the pinned versions, a handful of read-only GETs); written up by the lead. It settles the facts the doc's build
task 1 deferred to "build-start checks". Sources: `docs/plan/r11/src/music-radio/` (91 files, 11.1 MB, git-ignored;
`SOURCES.tsv` there has file, URL, UTC fetch time and sha256). Confidence: **HIGH** = the vendor's doc, source or a live
response says it; **MEDIUM** = inferred or third-party; **UNVERIFIED** = needs the device (listed in §8).

Three limits of the pass: three Android reference pages and the 2,000-station sample were over 2 MB raw and are kept as
stripped text / a 12-field slim JSON; the one unbounded station request returned only 1,000 rows, so the full directory's
size is an extrapolation; 6(a) cites one AOSP source file because developer.android.com does not state the rule.

## 1. radio-browser.info

| fact | value | conf | source file |
|---|---|---|---|
| Server discovery | "Do a DNS-lookup of 'all.api.radio-browser.info'… Randomize the list… If a request fails just retry the request with the next entry"; alternative: the SRV record `_api._tcp.radio-browser.info`; "Never use a direct link to a single new server" | HIGH | `rb-api-docs.html` |
| Mirrors today | ONE: `de1.api.radio-browser.info` (A 91.98.4.78; SRV and `/json/servers` agree). The A lookup gives IPs; names come from reverse DNS, the SRV record or `/json/servers` | HIGH | `rb-dns-all-api.txt`, `rb-servers.json` |
| User-Agent | "Send a speaking http agent string (e.g. mycoolapp/1.4)" | HIGH | `rb-api-docs.html`, `rb-docs-de1.html` |
| Click counter | ASKED FOR: "Send /json/url requests for every click the user makes"; "should be called everytime when a user starts playing a stream"; counted once per IP per station per day | HIGH | same |
| Vote | Optional; one per IP per station per 10 minutes | HIGH | `rb-docs-de1.html` |
| Licence | "Data license: public domain, software license: GPL"; stream content, favicons and homepages stay the stations' property. No rate limit stated; "can be used freely but without guarantee" | HIGH | `rb-www-chunk-XJBKC73J.js`, `rb-www-chunk-FFUJJMXR.js` |
| `/json/stats` | 60,509 stations, 7,147 broken, 12,477 tags, 241 countries; server 0.7.45 | HIGH | `rb-stats.json` |
| Default row cap | A list call with no `limit` returns 1,000 rows (changelog 0.7.38); an explicit `limit=` is honoured (2,000 and 20,000 tested) | HIGH | `rb-changelog.md`, `rb-stations-nolimit-probe.txt` |
| Wire compression | None (`Accept-Encoding: gzip` came back uncompressed) | HIGH | `rb-stations-nolimit-probe.txt` |
| Sizes | `topclick/2000?hidebroken=true`: 2,415,828 B (819 KB with the 11 needed fields). `/json/tags?limit=20000&hidebroken=true`: 11,525 rows, 464 KB. `/json/countries`: 242 rows, 14 KB | HIGH | `rb-sample-stats.txt`, `rb-tags-limit20000.json`, `rb-countries.json` |
| Full directory | About 53,000 working stations at 1.1–1.6 KB each: roughly 60–85 MB full-field, 19–22 MB slim, in 50+ pages | MEDIUM | extrapolated from `rb-sample-stats.txt` |
| Fields | stationuuid, name, url, url_resolved, favicon, tags, countrycode, codec, bitrate, hls, lastcheckok all present; `url_resolved` empty on 50 of 1,500 mid-directory rows; `favicon` empty on 26 % of the top 2,000 and `http://` on 159 | HIGH | `rb-sample-stats.txt` |
| Top 2,000: HLS | 9.8 % `hls=1` (9 of the top 100) | HIGH | same |
| Top 2,000: scheme | `url_resolved` 33.7 % http, 66.3 % https (36 of the top 100 are http) | HIGH | same |
| Top 2,000: playlists | `url`: 3.6 % .pls, 3.0 % .m3u, 9.5 % .m3u8. `url_resolved`: 2 rows (0.1 %) still .pls / .m3u; 9.7 % .m3u8 | HIGH | same |
| Top 2,000: codec | MP3 67.8 %, AAC 15.8 %, AAC+ 10.7 %, UNKNOWN 4.8 % (all HLS), OGG 0.7 %; 6 rows are video | HIGH | same |
| `url_resolved` | The server resolves "playlists (M3U/PLS/ASX...), HTTP redirects"; "If hls==1 then this is still a HLS-playlist" (131 of 133 playlist URLs resolved) | HIGH | `rb-docs-de1.html` |

## 2. MusicBrainz and the Cover Art Archive

| fact | value | conf | source file |
|---|---|---|---|
| Rate limit | Per IP "(on average) 1 request per second"; above it requests are declined with HTTP 503 until the rate drops | HIGH | `mb-rate-limiting.html` |
| User-Agent | Required: `Application name/<version> ( contact )`. Blank and library defaults (`Java`, …) are throttled as anonymous; a misbehaving app is blocked by its UA | HIGH | same |
| Search | `GET /ws/2/{recording\|release\|artist}?query=<Lucene>&fmt=json&limit=1..100` (default 25) | HIGH | `mb-api-search.html` |
| Recording fields | `id`, `score`, `title`, `length` (ms), `artist-credit[].name`, `releases[].id` (the artwork MBID), `.title`, `.date`, `.release-group.id` | HIGH | `mb-search-recording.json` |
| Data licence | Core data CC0; supplementary CC BY-NC-SA 3.0 | HIGH | `mb-data-license.html` |
| Cover art URLs | `/release/{mbid}/front[-250\|-500\|-1200]`, `/release-group/{mbid}/front[-…]`; 307 on a hit, 404 with no front image | HIGH | `caa-api.html` |
| Redirect chain (live) | `coverartarchive.org` 307 → `archive.org/download/…` 302 → `dn710604.ca.archive.org` 200. The last host is a subdomain of `archive.org` (not `ia*.us.archive.org` any more) | HIGH | `caa-redirect-chain.txt` |
| Cover art limits | "currently no rate limiting rules"; no licence is granted on the images | HIGH | `caa-api.html`, `caa-doc.html` |

## 3. Hand-off

| fact | value | conf | source file |
|---|---|---|---|
| Pandora search link | Pandora's own search page publishes `al:android:url` = `pandorav8://search/<query>/all` for `com.pandora.android` | HIGH that Pandora advertises it; UNVERIFIED that the installed app resolves it | `pandora-web-search-page.html` |
| Pandora web link | `https://www.pandora.com/search/<q>/all` answers 200; `www.pandora.com` delegates its URLs to `com.pandora.android` | HIGH for the association; the paths the app claims are UNVERIFIED | `assetlinks-www.pandora.com.json` |
| Pandora `MEDIA_PLAY_FROM_SEARCH` | No vendor documentation found | UNVERIFIED | — |
| YouTube Music | `https://music.youtube.com/search?q=` (`com.google.android.apps.youtube.music`) | HIGH link, UNVERIFIED in-app | `assetlinks-music.youtube.com.json` |
| Amazon Music | `https://music.amazon.com/search/<q>` (`com.amazon.mp3`) | same | `assetlinks-music.amazon.com.json` |
| Apple Music | `https://music.apple.com/us/search?term=` (`com.apple.android.music`) | same | `assetlinks-music.apple.com.json` |
| Tidal | `tidal.com/search?q=` answered 403 to curl (`com.aspiro.tidal`) | UNVERIFIED | `assetlinks-tidal.com.json` |
| Deezer | `https://www.deezer.com/search/<q>` (`deezer.android.app`) | HIGH link, UNVERIFIED in-app | `assetlinks-www.deezer.com.json` |
| SoundCloud | `https://soundcloud.com/search?q=` (`com.soundcloud.android`) | same | `assetlinks-soundcloud.com.json` |
| Android's contract | `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` "always includes the EXTRA_MEDIA_FOCUS and SearchManager.QUERY extras"; no data URI; sent with `startActivity` to an `<activity>` filter with `CATEGORY_DEFAULT`; unstructured focus is `"vnd.android.cursor.item/*"` | HIGH | `and-MediaStore.txt`, `and-common-intents.html` |
| `<queries>` | Not needed: the shell holds `QUERY_ALL_PACKAGES` (`app/src/main/AndroidManifest.xml:6`) | HIGH | `and-package-visibility-declaring.html` |

## 4. Media3 1.9.0 (the repo's version; the doc says 1.8.0)

| fact | value | conf | source file |
|---|---|---|---|
| ICY mapping | `IcyHeaders`: name → `MediaMetadata.station`, genre → `genre`; url not mapped. `IcyInfo`: StreamTitle → `title` only | HIGH | `m3-IcyHeaders.java:170`, `m3-IcyInfo.java:52` |
| Precedence | "MediaItem metadata is prioritized over metadata within the media" | HIGH | `m3-ExoPlayerImpl.java:2923` |
| `Icy-MetaData: 1` | Sent on every progressive load by `ProgressiveMediaPeriod`; nothing to configure | HIGH | `m3-ProgressiveMediaPeriod.java:1231,1305` |
| HLS stations | The ICY path exists only in `ProgressiveMediaPeriod`: an HLS station gives no StreamTitle | MEDIUM | same |
| Endless progressive stream | Duration `C.TIME_UNSET`; `isCurrentMediaItemLive()` true when length and duration are both unknown; a response with ICY headers is forced unseekable | HIGH | `m3-ProgressiveMediaPeriod.java:863-866`, `m3-ProgressiveMediaSource.java:502` |
| .pls / .m3u | Not played: only `mpd`, `m3u8`, `ism(l)` are inferred; anything else goes to the progressive extractors and fails | HIGH routing, MEDIUM failure | `m3-Util.java`, `and-media3-supported-formats.html` |
| `WAKE_MODE_NETWORK` | A WakeLock and a WifiLock, held while READY or BUFFERING with `playWhenReady`; needs `WAKE_LOCK` | HIGH | `m3-C.java:1493`, `m3-ExoPlayer.java:1930` |
| HLS module | `media3-exoplayer-hls` AAR 224,441 B, depends only on `media3-exoplayer`; `DefaultMediaSourceFactory` loads it by reflection for `.m3u8` or a set MIME type | HIGH | `m3-maven-sizes.txt`, `m3-DefaultMediaSourceFactory.java:839` |
| Cross-protocol redirects | Default FALSE; at most 20 redirects; 8 s connect and read timeouts | HIGH | `m3-DefaultHttpDataSource.java:141-148,240-246` |
| Load retries | 6 for a live progressive load, 3 otherwise; delay `min((errorCount-1)*1000, 5000)` ms; the count resets once samples were extracted; no retry for parser errors, file-not-found, cleartext-not-permitted | HIGH | `m3-DefaultLoadErrorHandlingPolicy.java:36-42,111-124` |

## 5. Jellyfin (the fixture pins 12.1)

| fact | value | conf | source file |
|---|---|---|---|
| Listing | `GET /Items?userId=&includeItemTypes=MusicAlbum\|Audio&recursive=true…`; `GET /Artists/AlbumArtists?userId=`; `GET /UserViews?userId=` | HIGH | `jellyfin-openapi-12.2-audio-excerpt.json` |
| Streaming | `/Audio/{itemId}/stream` (`static=true` = direct play, "streamed statically without any encoding"); `/Audio/{itemId}/universal` picks direct or transcode and may answer 302 | HIGH | same |
| Header-only token | Works: `Authorization: MediaBrowser Token="…"` is read first; the `ApiKey` query is a fallback; `X-Emby-Token` / `api_key` only with legacy authorization on | HIGH | `jf-v12.1-AuthorizationContext.cs:82-111` |
| Stream endpoint auth | `/Audio/{itemId}/stream` has no `[Authorize]` in 12.1 (`security: null` in the spec): it needs no token; `/universal` does | HIGH | `jf-v12.1-AudioController.cs:88-92`, `jf-v12.1-UniversalAudioController.cs:94` |

## 6. Android 16 (API 36)

| fact | value | conf | source file |
|---|---|---|---|
| Data Saver | The docs say only that it "blocks background data usage" on metered networks; AOSP allows a process at or above bound-foreground-service state | MEDIUM | `and-data-saver.html`, `aosp-NetworkPolicyManager.java.b64` (188, 876-881) |
| Captive portal / validated | Public capabilities; `registerDefaultNetworkCallback` and `isActiveNetworkMetered` need only `ACCESS_NETWORK_STATE` | HIGH | `and-NetworkCapabilities.txt`, `and-ConnectivityManager.txt` |
| `mediaPlayback` service | Runtime prerequisites "None"; the only type rule is no start from `BOOT_COMPLETED`. No listed background-start exemption names a voice command over the keyguard | HIGH for the docs; UNVERIFIED for the keyguard case | `and-fgs-service-types.html`, `and-fgs-bg-start-restrictions.html` |

## 7. CONTRADICTS THE SPEC (doc line → replacement)

1. **L139, L161, L349 — "Media3 1.8.0":** the repo is on 1.9.0 (`gradle/libs.versions.toml:12`).
2. **L157-162 — live metadata:** ICY name lands in `MediaMetadata.station`, genre in `genre`, StreamTitle in `title`;
   MediaItem metadata wins, so the station item sets `albumTitle` (the station name) and NEVER `title`; with no
   StreamTitle `title` is null and the service supplies the station name itself. An HLS station has no StreamTitle.
3. **L163-172, L375, P10 — "the directory" as one fetch:** an unbounded call returns 1,000 rows; the whole directory is
   ~53,000 rows / 60–85 MB and cannot be "cached" as written. What is fetched (explicit `limit` / `offset`,
   `hidebroken=true`) and cached must be stated — e.g. the top-clicked N (2,000 = 2.4 MB, 819 KB slim) plus `/json/tags`
   and `/json/countries`, with search / genre / country going to the server when online. The T20-5 cap follows from it.
4. **L165-167 — etiquette:** add the click call, `GET /json/url/<stationuuid>` on every station start (the directory asks
   for it).
5. **L166 — "a DNS lookup → a host list":** the A lookup gives IPs; there is one mirror today. The mirror rule must cover a
   one-entry answer and say where names come from (`/json/servers` or SRV).
6. **L142-144, L353, L498 — the HLS count:** settled: 9.8 % of the top 2,000, for a 224 KB module. Decide add-or-not now;
   the `unsupported hls` branch follows the decision.
7. **L231-235, L365, L693 — the .pls / .m3u resolver:** `url_resolved` already resolves them (2 of 2,000 left). Play
   `url_resolved`; a leftover playlist is "can't play this station". The resolver and its fixtures are near-moot.
8. **L240-241 — 35.0 % http:** re-measured 33.7 %. Stands.
9. **L692 — "cross-protocol redirects allowed":** the default is false and `MusicService`'s factory does not set it (the
   video player sets it false on purpose, `video/VideoPlayback.kt:40`). Either the service builds a
   `DefaultHttpDataSource.Factory` that allows them, or the clause goes — a trust call (an https station redirected to
   http, or an http one to a fixed host).
10. **L191-196 — reconnect:** ExoPlayer itself retries a live progressive load 6 times at 0/1/2/3/4/5 s (plus 8 s
    timeouts) before any player error; E8's "`lost, retrying` within 5 s" needs the line written on the first LOAD error.
11. **L258-264, L399, L588 — the Jellyfin token "in the query":** `/Audio/<id>/stream?static=true` needs no token in 12.1,
    and every other call can send the `Authorization: MediaBrowser Token=…` header. (Phase 17 as built adds `ApiKey` in
    the player's data source for video, `video/server/ServerRules.kt:213-214`.) A header put on the shared player must be
    scoped to the server's host, or radio hosts would receive it.
12. **L199-201 — "the platform exempts foreground services" (Data Saver):** not stated on developer.android.com; cite the
    AOSP rule or let the device row prove it.
13. **L181 — `EXTRA_MEDIA_FOCUS`:** `SearchManager.QUERY` is mandatory too.
14. **L55-57, L351-352, L582 — Pandora's form:** `pandorav8://search/<urlencoded query>/all`, fallback
    `https://www.pandora.com/search/<q>/all`; "search not passed" is then the last fallback only.
15. **L243-244 — the Cover Art redirect host:** fine as built (`archive.org` with subdomains).

## 8. STILL NEEDS THE DEVICE
- Whether installed Pandora resolves `pandorav8://search/…/all` and lands on results (the phone).
- Which installed services answer `MEDIA_PLAY_FROM_SEARCH`, and which web-search paths each app claims (the phone).
- Data Saver leaving the stream alone (AVD and One UI); the `cmd netpolicy` metered-network id form.
- Starting playback of a network item from Tess over the keyguard.
- A real station's ICY title encoding; what `MusicFeed` shows when `title` is null.
- The Jellyfin 12.1 fixture answering `/Audio/<id>/stream?static=true` with 200 and no token.
