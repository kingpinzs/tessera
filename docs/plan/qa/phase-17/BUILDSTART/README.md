# Phase 17 — build-start checks (build task 0), 2026-10-05

Read on 2026-10-05 by the lead and three Opus research agents (camera libraries; streaming forms; TMDB and Jellyfin).
Every claim below names its source. "UNVERIFIED" means no source proves it; the phone rows (P13, P1, P10, P11) are
where those are settled. Nothing here was verified on the S25 Ultra: BS-4's "verified by starting each on a phone" is
P13, which the owner does on the phone.

## The AVD (emulator-5554, tileshell_fhd, API 36, x86_64, 1080x2340 @ 450 dpi)

| Fact | Read with | Value |
|---|---|---|
| Cameras | `dumpsys media.camera` | 1 device, id 1, Facing Back, Orientation 90; no front camera |
| Hardware level | same | 3 (LEVEL_3) |
| Capabilities | same | BACKWARD_COMPATIBLE MANUAL_SENSOR MANUAL_POST_PROCESSING READ_SENSOR_SETTINGS BURST_CAPTURE PRIVATE_REPROCESSING YUV_REPROCESSING RAW DYNAMIC_RANGE_TEN_BIT STREAM_USE_CASE COLOR_SPACE_PROFILES DEPTH_OUTPUT — no CONSTRAINED_HIGH_SPEED_VIDEO |
| AE modes / EV range | same | [0 1 2 3] (OFF present) / [-9 9] |
| AF modes / min focus distance | same | [0 1 2 3 4] / 20.0 |
| AWB modes | same | [0 1 2 3 5 8] |
| Max digital zoom | same | 10.0 |
| So on the AVD | r3 D10's gates | the Pro dial shows all five controls (E19's dial sub-row runs here); Slow motion is hidden; Panorama is hidden (x86_64); zoom is present |
| STILL_IMAGE_CAMERA / VIDEO_CAMERA handlers | `cmd package query-activities` | com.android.camera2, net.sourceforge.opencamera, org.fossify.camera (three, before the shell's) |
| IMAGE_CAPTURE / VIDEO_CAPTURE handlers | same | com.android.camera2 only |
| Capture requests after the shell declares them (build task 1) | `cmd package query-activities -a android.media.action.IMAGE_CAPTURE`, then with `-p app.tileshell` | The implicit query still lists `com.android.camera2/com.android.camera.CaptureActivity` ONLY — Android 11's rule filters the shell's own query too — and the query that names the package resolves to `app.tileshell/.camera.CaptureActivity`. So E9's recorded fact is those two lists; the shell is not "among" the implicit one (Q-17-2 (b)'s premise, seen) |
| APP_GALLERY handlers | same | com.android.gallery3d, deckers.thibault.aves.libre |
| VIEW video/mp4 handlers | same | com.android.gallery3d, deckers.thibault.aves.libre, org.fossify.gallery |
| Decoders | `/vendor/etc/media_codecs.xml` | c2.goldfish h264, hevc, vp8, vp9 |
| Media on the device | `content query` | 6 images; 5+ videos left by earlier phases' recordings (e7_entry.mp4 …) — the census `media_up` takes |
| Camera extensions package | `pm list packages` | com.android.cameraextensions present |
| Host tools | `command -v` | ffmpeg, ffprobe, docker, python3 present; **exiftool is NOT installed** (E7 and E9 name it; the drivers read EXIF / XMP with a Python reader instead, or it is installed first) |
| Baseline build | `./gradlew :app:assembleDebug :app:testDebugUnitTest` at 1d01c517 | rc 0; APK 339,145,398 bytes, md5 585b457ffc29878c… (equal to the pre-17 upgrade APK); 1,285 unit tests, 0 failures |

Versions in the repo differ from the doc's wording: AGP is 9.4.0 (the doc says AGP 8), Kotlin 2.4.20, Gradle 9.7.1,
Media3 1.8.0, compileSdk / targetSdk 36, minSdk 34.

## BS-1 — OpenCV for panorama: the official SDK's static libraries behind one small JNI library

- **Route:** NOT the Maven AAR. Link the static libraries of the official OpenCV **4.14.0** Android SDK
  (`https://github.com/opencv/opencv/releases/download/4.14.0/opencv-4.14.0-android-sdk.zip`, 319,074,395 bytes) into one
  JNI library named `libopencv_pano.so`, arm64-v8a only. Modules: `core imgproc features2d flann calib3d stitching`
  (`find_package(OpenCV REQUIRED COMPONENTS …)`, `OpenCV_DIR=<sdk>/sdk/native/jni`), STL `c++_static`.
- **Why not the AAR** (`org.opencv:opencv:4.14.0`, 123,380,341 bytes): its `classes.jar` has no `org/opencv/stitching`
  package, `nm -D` on its arm64 `libopencv_java4.so` shows no exported `stitch` symbol, and that library is 24,657,160
  bytes — over E17's 20,971,520 bound by itself.
- **Measured:** a trial link of a `cv::Stitcher::create(PANORAMA)->stitch()` shim with NDK r30 clang++ came to
  **5,809,048 bytes** (`-O2 --gc-sections --exclude-libs,ALL --strip-all`, 16 KB LOAD alignment). It also needed the SDK's
  `libtegra_hal`, `libkleidicv_hal`, `libkleidicv`, `libkleidicv_thread`, `libtbb`, `libcpufeatures`, `libittnotify` and
  `-lz -llog -ldl -lm -landroid`. The trial was linked by hand, not through Gradle, and never run on a device.
- **Licence:** Apache-2.0 (`https://raw.githubusercontent.com/opencv/opencv/4.14.0/LICENSE`; the Maven POM agrees).
  `opencv.org/license` answered 403, so its "4.5.0 and later" sentence was not read from that page. The stitching
  module needs imgproc, features2d, calib3d, flann; xfeatures2d (SURF) and CUDA are optional and absent from the
  official binary ("Non-free algorithms: NO"); `Stitcher` defaults to ORB. Third-party pieces linked in: ittnotify
  (dual GPL-2.0-only / BSD-3-Clause — the BSD-3 option is taken), TBB (Apache-2.0), KleidiCV and the Tegra HAL (their
  licence files ship in the SDK and are added to the app's licence page with OpenCV's).
- **Why 4.14.0, not 5.0.0:** 5.0 renames the modules and its release needed a separate 16 KB page-size fix.
- **Toolchain on this PC:** `~/Android/Sdk/ndk/30.0.16248370`, `~/Android/Sdk/cmake/3.31.6`.
- **Consequences for the build:** `app/build.gradle.kts:31` has `abiFilters += listOf("arm64-v8a", "x86_64")`; the
  native build is restricted to arm64-v8a, so no `lib/x86_64/libopencv*` exists (E17) and Panorama is hidden on the AVD
  with its reason (E7). The SDK zip is a git-ignored build input fetched by a script, as the speech AAR is
  (`tools/fetch-speech.sh`), and CI (`.github/workflows/apk.yml`) gains the same fetch and an NDK, or the phone build
  has no panorama.

## BS-2 — slow motion: CameraX 1.6.2's own high-speed session, no raw Camera2

- **Pin:** `androidx.camera` **1.6.2** (latest stable, 2026-08-26) for camera-core, camera-camera2, camera-lifecycle,
  camera-video, camera-view, camera-extensions.
- **Route:** `Recorder.getHighSpeedVideoCapabilities(cameraInfo)` (null → the mode is hidden with `[camera] mode slowmo:
  unavailable (no high-speed session)`), then `HighSpeedVideoSessionConfig(videoCapture, preview, frameRateRange,
  isSlowMotionEnabled = true)`, the range from `cameraInfo.getSupportedFrameRateRanges(config)`, bound with
  `bindToLifecycle(owner, selector, config)`; the recording is started without `withAudioEnabled()`.
- **Evidence:** the 1.5.0 notes introduce it; the 1.6.0-alpha02 notes say "`HighSpeedVideoSessionConfig` API is no
  longer experimental and is now a fully stable public API". The KDoc in `camera-video-1.6.2-sources.jar`: it follows
  Camera2's `CameraConstrainedHighSpeedCaptureSession`; "When the `isSlowMotionEnabled` flag is set to `true`, the
  recorded high-speed video will be processed and saved at a standard frame rate of 30 FPS" (120 fps → 1/4 speed,
  240 → 1/8) — r3 D11's form.
- **Its limits:** only `VideoCapture` plus an optional `Preview` in that session (no `ImageCapture`), no mirror mode, no
  resolution selector on the preview, no effects. "No audio track unless enabled" rests on `PendingRecording`'s opt-in,
  not on a sentence in the docs: P11 checks the file on the phone.
- **Interop and Extensions in 1.6.2:** `Camera2CameraControl`, `CaptureRequestOptions`, `Camera2CameraInfo` are present
  under `@OptIn(ExperimentalCamera2Interop::class)` (deprecated only from 1.7.0-alpha03). `ExtensionsManager`,
  `ExtensionMode.HDR` / `NIGHT` are present with no opt-in.
- **Compatibility:** minSdk 23, compileSdk ≥ 35, Kotlin ≥ 2.0 — all met. `camera-video` 1.6.2 depends on
  `androidx.media3:media3-muxer:1.9.0` while the repo pins Media3 1.8.0: checked with Gradle when the dependency is
  added (see the Decisions line for the outcome).

## BS-3 — the credential store

Android Keystore alias **`tessera_credentials_v1`** (AES-256-GCM, non-exportable, no user authentication, so the
player can read the server token with the screen locked); file **`files/credentials_v1.json`** in the app's private
files dir, written temp-and-rename and re-read on every use. It maps a name (`jellyfin`, `tmdb`) to a fresh 12-byte IV
and the ciphertext, Base64. No security-crypto library.

## BS-4 — the streaming services' forms (none verified on a phone yet — P13)

Package names were each confirmed on Google Play. A host's `assetlinks.json` proves the host belongs to the package,
not which paths the app opens, so every title and search PATH is unverified until P13. Wikidata usage counts are the
property pages' on 2026-10-05.

| Service | Package | Title form (id from Wikidata) | Wikidata property | Search form (no id) |
|---|---|---|---|---|
| Netflix | `com.netflix.mediaclient` | `https://www.netflix.com/title/<id>` | P1874 Netflix ID (~32,060) | `https://www.netflix.com/search?q=<q>` |
| Prime Video | `com.amazon.avod.thirdpartyclient` | `https://app.primevideo.com/detail?gti=<gti>` | P14462 Prime Video GTI (~3,777); P14440 (~221) holds the same form | `https://www.primevideo.com/search/?phrase=<q>` |
| Disney+ | `com.disney.disneyplus` | `https://www.disneyplus.com/browse/<id>` | P13902 Disney+ browse ID (~2,919; the value includes `entity-`) | none exists on the web (`/search?q=` is 404): the app is opened |
| Hulu | `com.hulu.plus` | `https://www.hulu.com/movie/<uuid>` / `/series/<uuid>` | P6466 movie (~1,645), P6467 series (~1,610) | `https://www.hulu.com/search?q=<q>` |
| Max (HBO Max) | `com.wbd.stream` | `https://play.hbomax.com/<value>` (the value carries `movie/` or `show/`) | P8298 HBO Max ID (~719); values starting `feature/urn:` or `series/urn:` are stale and skipped | `https://play.hbomax.com/search?q=<q>` |
| Apple TV | `com.apple.atve.androidtv.appletv` | `https://tv.apple.com/movie/<id>` / `/show/<id>` | P9586 movie (~50,500), P9751 show (~2,891) | `https://tv.apple.com/search?term=<q>` |
| Paramount+ | `com.cbs.app` | none (Wikidata has only a video id, P13147, ~211) | — | `https://www.paramountplus.com/search/?q=<q>` |
| Peacock | `com.peacocktv.peacockandroid` | none (P11815 has ~40 values; the app's link needs an id Wikidata lacks) | — | `https://www.peacocktv.com/watch/search?q=<q>` |
| YouTube | `com.google.android.youtube` | none (a catalogue title has no YouTube id) | — | `https://www.youtube.com/results?search_query=<q>` |
| Plex | `com.plexapp.android` | none usable (P11460's key opens only the web app; `watch.plex.tv` titles need a slug) | — | `https://watch.plex.tv/search?q=<q>` |
| Jellyfin | `org.jellyfin.mobile` | none; no deep link or search intent exists | — | none: left out of the per-title rows (the user's Jellyfin is the Media server page) |

- TMDB ids on Wikidata: **P4947** TMDB movie ID (~285,332), **P4983** TMDB TV series ID (~64,204).
- One query per title (for TV, `wdt:P4983`), run on 2026-10-05 for TMDB 78 and 335984, both HTTP 200:

```
SELECT ?item ?netflix ?primeGti ?primeId ?disneyBrowse ?huluMovie ?huluSeries ?hboMax ?appleMovie ?appleShow WHERE {
  ?item wdt:P4947 "78" .
  OPTIONAL { ?item wdt:P1874  ?netflix }
  OPTIONAL { ?item wdt:P14462 ?primeGti }
  OPTIONAL { ?item wdt:P14440 ?primeId }
  OPTIONAL { ?item wdt:P13902 ?disneyBrowse }
  OPTIONAL { ?item wdt:P6466  ?huluMovie }
  OPTIONAL { ?item wdt:P6467  ?huluSeries }
  OPTIONAL { ?item wdt:P8298  ?hboMax }
  OPTIONAL { ?item wdt:P9586  ?appleMovie }
  OPTIONAL { ?item wdt:P9751  ?appleShow }
}
```

  Response shape (`Accept: application/sparql-results+json`): `{"results":{"bindings":[{"item":{…},"primeGti":{"type":
  "literal","value":"amzn1.dv.gti.98a9f73a-…"},"appleMovie":{"type":"literal","value":"umc.cmc.31vu3b779lzce3c40rprt9qxx"}}]}}`
  for 78 (Blade Runner: Prime and Apple TV only); 335984 (Blade Runner 2049) has `netflix` = `80185760` and `appleMovie`,
  no Prime id. An absent property is a missing key; a property with several values multiplies the rows, so the first
  value per variable is taken.
- Coverage is sparse and not region- or availability-aware: the search form is the common path (H11 says so).
- Wikidata Query Service limits (`https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual`): a descriptive
  `User-Agent` is required; 60 s of processing per 60 s per client; 5 parallel queries per IP; 60-s timeout; HTTP 429
  with `Retry-After`. The shell asks once per title, one query at a time, caches the answer with the title page (the
  7-day cache), and sends `User-Agent: Tessera/<version> (personal launcher; StreamingHandoff)`.

## BS-5 — TMDB's terms; Jellyfin's version and API

**TMDB** (`https://www.themoviedb.org/api-terms-of-use`, `https://developer.themoviedb.org/docs/faq`,
`…/docs/rate-limiting`, `…/docs/errors`):
- Free for non-commercial use with attribution; the licence is non-transferable (each user's own key fits it — an
  inference, no clause says so). Caching is limited to 6 months: the 7-day cache is inside it.
- Attribution text: "This product uses the TMDB API but is not endorsed or certified by TMDB." in an About or Credits
  section, with an approved logo (`https://www.themoviedb.org/about/logos-attribution`), unmodified and less prominent
  than the app's own name. Watch-provider data: "you must attribute the source of the data as JustWatch".
- Rate limit: "somewhere in the 40 requests per second range", HTTP 429 (status_code 25) when exceeded; `Retry-After`
  is not documented.
- A refused token: HTTP 401, `{"status_code":7,"status_message":"Invalid API key: You must be granted a valid
  key.","success":false}` (a live probe with a made-up token).
- Endpoints: `/3/configuration` (`images.secure_base_url`), `/3/search/multi`, `/3/movie/{id}`, `/3/tv/{id}`,
  `/3/{movie|tv}/{id}/watch/providers` (results keyed by country: `link`, `flatrate` / `rent` / `buy` / `ads` arrays of
  `{logo_path, provider_id, provider_name, display_priority}`), `/3/trending/all/week`, `/3/movie/popular`,
  `/3/tv/popular`; images at `https://image.tmdb.org/t/p/<size><file_path>`.

**Jellyfin** (`https://github.com/jellyfin/jellyfin/releases/tag/v12.1`,
`https://api.jellyfin.org/openapi/jellyfin-openapi-stable.json`, the v12.1 source):
- Latest stable: **12.1** (2026-09-15). Fixture image: **`jellyfin/jellyfin:12.1@sha256:78d3ea1207d1322471fcac39a614f004f2ccf7e878f95ab2977d752f07e4dd7e`**
  (the index; linux/amd64 is `sha256:326be1010b16c92e492f6c7dd6fd105943db84ce723c73183279a1ab357b8f9b`), read with
  `docker buildx imagetools inspect`.
- Sign-in: `POST /Users/AuthenticateByName`, body `{"Username","Pw"}`, header `Authorization: MediaBrowser
  Client="…", Device="…", DeviceId="…", Version="…"`; the answer holds `AccessToken` and `User.Id`. A wrong password is
  401; a disabled user 403.
- Every later call: `Authorization: MediaBrowser Token="…"`. **12.x switches the legacy forms off by default**
  (`X-Emby-Token`, `api_key=`); the query form `ApiKey=<token>` still works and is what a stream URL carries.
- **The library is `GET /Items?userId=<id>&recursive=true&includeItemTypes=Movie,Episode&fields=…`** —
  `/Users/{userId}/Items`, which the phase doc names, is not in 12.1's API.
- Direct play: `GET /Videos/{itemId}/stream?static=true` (+ `&ApiKey=<token>`); images `GET
  /Items/{itemId}/Images/Primary`; reachability `GET /System/Info/Public` (no sign-in; `StartupWizardCompleted`).
- `GET /Devices` (admin): `DeviceInfoDto` carries `AccessToken` and `AppName` — E22's read (r3 V8) holds.
- **The fixture is seeded over REST into an empty config volume**, not shipped as a config dir (a config dir holds a
  database tied to one version): wait for `/System/Info/Public`; `POST /Startup/Configuration`; `GET /Startup/User`
  (it creates the first user — the POST is 404 without it); `POST /Startup/User {"Name","Password"}`; `POST
  /Library/VirtualFolders?name=Movies&collectionType=movies&paths=/media/movies&refreshLibrary=true`; `POST
  /Startup/Complete`; then poll `/Items` until the fixture video is listed. The sequence comes from the 12.1 spec and
  source and has not been run yet — task 16 runs it.

## Cover Art Archive's redirect host (C-16 (1))

`curl -sIL https://coverartarchive.org/release/<mbid>/front-250` on 2026-10-05: 307 → `https://archive.org/download/…`,
302 → `https://dn711103.ca.archive.org/…`, 200. So `archive.org` with its subdomains is on the fixed-host list.
