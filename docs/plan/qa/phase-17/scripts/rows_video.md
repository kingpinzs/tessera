# Phase 17 — the Movies & TV rows

Drivers: `e11.sh` `e12.sh` `e13.sh` `e14.sh` `e19_video.sh` `e20.sh` `e21.sh` `e22.sh` `e23_video.sh` `trust_video.sh`
`edge_video.sh` `guard_selftest_video.sh`, all over `lib.sh`, `p17.sh` and `p17_video.sh` (the lock taken once per
process). Run one as `env -u TMPDIR bash docs/plan/qa/phase-17/scripts/<driver>`; exit 3 is a busy device. A run's
folder is `docs/plan/qa/phase-17/<ROW>/`; rename it before the next run. Fixtures: `make_videos.sh` (into `gen/media`),
`catalogue_server.py` on port **8091** (8090 is taken on this PC), `fixtures/jellyfin/jellyfin_fixture.sh` (the pinned
container), `fixtures/jellyfin/fake_jellyfin.py` (a fake server whose log says whether a request carried the token),
`testapps/qa-flix`, and `testapps/qa-view` (new: another app that VIEWs a video and can set the clipboard).

## Each row's last run (all on the gate build, debug APK md5 95b543037345b851)

| Row | Build | Totals (passed / failed / recorded) | Folder |
|---|---|---|---|
| E11 | 95b54303 | 33 / 0 / 24 | `E11-build-95b54303-run4-pass-33-0-24` |
| E12 | 95b54303 | 21 / 0 / 3 | `E12-build-95b54303-run2-pass-21-0-3` |
| E13 | 95b54303 | 63 / 0 / 14 | `E13-build-95b54303-run6-pass-63-0-14` |
| E14 | 95b54303 | 26 / 0 / 25 | `E14-build-95b54303-run3-pass-26-0-25` |
| E19_VIDEO | 95b54303 | 65 / 0 / 10 | `E19_VIDEO-build-95b54303-run2-pass-65-0-10` |
| E20 | 95b54303 | 91 / 0 / 9 | `E20-build-95b54303-run2-pass-91-0-9` |
| E21 | 95b54303 | 42 / 0 / 5 | `E21-build-95b54303-run1-pass-42-0-5` |
| E22 | 95b54303 | 67 / 0 / 9 | `E22-build-95b54303-run3-pass-67-0-9` |
| E23_VIDEO | 95b54303 | 55 / 0 / 6 | `E23_VIDEO-build-95b54303-run3-pass-55-0-6` |
| TRUST_VIDEO | 95b54303 | 151 / 0 / 55 | `TRUST_VIDEO-build-95b54303-run3-pass-151-0-55` |
| GUARD_SELF (the egress guard's control) | 95b54303 | 20 / 0 / 15 | `GUARD_SELF-build-95b54303-run2-pass-20-0-15` |
| EDGE_VIDEO_VIDEOS | 95b54303 | 27 / **2** / 9 — the 4K clause | `EDGE_VIDEO_VIDEOS-build-95b54303-run1-27-2-9-FAIL-the-AVD-plays-the-4K-fixture` |
| EDGE_VIDEO_APK_UPDATE | 95b54303 | 15 / 0 / 4 | `EDGE_VIDEO_APK_UPDATE-build-95b54303-run1-pass-15-0-4` |
| EDGE_VIDEO_CATALOGUE | 95b54303 | 26 / 0 / 2 | `EDGE_VIDEO_CATALOGUE-build-95b54303-run1-pass-26-0-2` |
| EDGE_VIDEO_WATCH_ON | 95b54303 | 11 / 0 / 4 | `EDGE_VIDEO_WATCH_ON-build-95b54303-run1-pass-11-0-4` |
| EDGE_VIDEO_MEDIA_SERVER | 95b54303 | 23 / 0 / 5 | `EDGE_VIDEO_MEDIA_SERVER-build-95b54303-run1-pass-23-0-5` |
| EDGE_VIDEO_OFFLINE | 95b54303 | 21 / 0 / 8 | `EDGE_VIDEO_OFFLINE-build-95b54303-run2-pass-21-0-8` |

Earlier runs are kept beside these under their own names (`-build-c7336aca-…`, `-dev1-…`, `-run<k>-<why>`).

## What a row changes on the device, and what it records instead of asserting

| Row | Changes (all restored) | Recorded instead of asserted, and why |
|---|---|---|
| E11 | one video pushed; device media volume | The centre pixel at k.5 s for all ten colours (the evidence for the ruling of 2026-10-06 on the pixel rule); the doc's own ± 8, which does not hold on this emulator. |
| E12 | one video pushed; Music played; device media volume | — |
| E13 | the egress guard (adb root for its span, location grants), airplane mode, one video, the catalogue fixture; runs FixedEndpointsTest before taking the device | The 404 page's text. Nothing is chosen in the resolver (Android remembers a choice). The fixed-hosts MARK is taken just before the force-stop (Android restarts the launcher at once). |
| E14 | six files pushed; the reader app installed and removed | vp9 and hevc, as the doc says. The 0-byte file and the sound-only file (which MediaStore lists as audio) have no My videos tile and are opened by the reader app with its identity shared. |
| E19_VIDEO | two videos and a .srt pushed; one host copy made and removed | The unplayed track's pixel (white at 25 % is a blend over the picture). |
| E20 | the guard, airplane mode, the QA pref, the key, the cache folder, QA-Flix, one video | "A fresh install offline" is the no-cache state, by the lead's ruling: no wipe was made. The stopped-fixture page's text. |
| E21 | the guard, both QA prefs, the key, QA-Flix | Who answers the bare link with no package. |
| E22 | the guard, the Jellyfin container, the key, the server | What Jellyfin 12.1 answers a stream request with no token (it plays). The token is compared and scanned for, never printed. |
| E23_VIDEO | the Start layout, the guard for the server set-up, the container | The dynamic shortcut's own rank (Android's: 0). |
| TRUST_VIDEO | the reader app, the clipboard, the key, a fake server, the store's two files (edited through run-as), the guard | Leg (vi) (what adb shell's own launch does, as the shell uid and as root). The read-flag variant of leg (v). B2-L8 (h): NOT RUN — it needs a wipe. |
| EDGE_VIDEO_* | as their rows above | Two audio tracks "plays the first" (the track count and playback are asserted). An embedded text track (no fixture). A service that answers no intent (not producible with the fixture). |
