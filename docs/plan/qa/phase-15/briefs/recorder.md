# Brief — phase 15, Voice Recorder (build task 7 and its shares of tasks 1, 8, 9)

You are building **Voice Recorder**, one of three Windows 10 Mobile inbox apps that phase 15 adds to a Windows-10-Mobile-
style Android shell ("Tessera", package `app.tileshell`: Kotlin, Jetpack Compose with foundation only — no Material — one
sideloaded APK, minSdk 34, targetSdk 36).

## Where you work
- Your git worktree: `/home/jeremyking/projects/metro-launcher-p15-rec`, branch `phase-15-rec`. Work and commit ONLY there,
  logically (several commits, never one squash), each message written to a file and committed with `git commit -F <file>`,
  ending with the two trailer lines below. Never push. Never touch `/home/jeremyking/projects/metro-launcher` or
  `/home/jeremyking/projects/metro-launcher-p15` (the lead's worktree) — the lead merges your branch.
- Trailer lines for every commit message:
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01Wnw3DiCvZpK4eDLHrsVq8D`
- Build: `./gradlew :app:assembleDebug`; tests: `./gradlew :app:testDebugUnitTest` (JUnit 4, host JVM; Android classes are
  stubs there, so put every rule you want tested in pure Kotlin). Read exit codes from a file, never through a pipe.
- NO device work: never run `adb`, never start or touch an emulator (the lead verifies on the device after merging).
- Do not edit `docs/plan/INDEX.md`, `docs/plan/qa/phase-15/STATE.md` or any phase doc. The FINAL phase doc is the spec.

## The spec (read in full before coding)
- `docs/plan/phase-15-inbox-clock-calculator-recorder.md` — everything about Voice Recorder: Goal, Scope (the Voice
  Recorder bullet), Decisions "Voice Recorder mechanics" (T15-27 / T15-28 / T15-36 / T15-38 / T15-39 inside it), T15-3
  "Other apps' recordings" with its "Orphaned takes" (T15-26), T15-4, T15-16's Voice Recorder extras (Pause / Resume,
  markers in `recordings.json`, search / filter), T15-19 (READ_MEDIA_AUDIO denied), T15-43 (level rings, About page, no
  Feedback), "Harness contracts" (EVERY tag and EVERY diagnostics line — names exact), "Bars", build task 7, and rows
  E14, E14b, E15, E16, E17, E18, E19, E20, E21, E24, E30 and the Recorder edge cases (they say what must be observable).
- Measurements: `docs/plan/r11/voice-recorder.md` (every value you draw comes from here with its section, or is a tagged
  approximation already listed in the doc's H rows).
- Shared geometry the doc cites: `docs/plan/r7-measurements.md` §1.3.9 (top-anchored dialog), §3.5.8 (app bar), §3.5.9
  (empty-list line), §3.6.2 (flyout), `docs/plan/w10m-measurements.md` (R3).

## Code to reuse — read these before writing anything (they are the house style)
- `app/src/main/kotlin/app/tileshell/music/MusicActivity.kt` — the in-APK app template: `hideSystemBars()`, `ShellRoot`,
  `testTagsAsResourceId` on the root, drawn bars, Back / Windows handling, permission grant in place.
- `app/src/main/kotlin/app/tileshell/music/MusicCollectionPage.kt`, `MusicNowPlaying.kt` — lists, rows, app bar, slider
  (X23), menus as this codebase draws them; `bars/SystemBars.kt` (`BarMetrics.STATUS_EPX` / `NAV_EPX` — never write the
  literal), `ui/ShellTheme.kt`, `brand/Brand.kt` (fonts: `Brand.uiFont`), `brand/Glyph.kt` (glyph font), `diag/Diagnostics.kt`
  (one ring per process), `settings/SettingsWidgets.kt`, `settings/AboutPage.kt`.
- `cortana/speech/SpeechService.kt` (its `dump()` prints the process's Diagnostics — copy that for `RecorderService.dump()`),
  `SpeechClient.kt`, `MicArbiter.kt` (+ its JVM tests under app/src/test), `app/src/main/aidl/app/tileshell/cortana/speech/ISpeech.aidl`,
  `ime/KeyboardService.kt:407` (the keyboard's busy text), `cortana/CortanaModel.kt:276` (Tess's MICROPHONE_BUSY notice).
- `music/MusicStore.kt` — the Music library query where the `IS_RECORDING` skip predicate goes (Q2 A, `[music] skipped
  recording <id>`).
- `ShellApp.kt` (returns early outside the main process — your `:recorder` process relies on that).
- `AndroidManifest.xml` — add ONLY your components: `app.tileshell.recorder.RecorderActivity` (label "Voice Recorder",
  `taskAffinity` `app.tileshell.recorder`, launcher, exported only because it is a launcher activity) and
  `app.tileshell.recorder.RecorderService` (`android:process=":recorder"`, `exported="false"`,
  `foregroundServiceType="microphone"`), `FOREGROUND_SERVICE_MICROPHONE`, and the activity's `<meta-data
  android:name="android.app.shortcuts" android:resource="@xml/shortcuts_recorder" />` (task 9: ids `new_recording`,
  `recordings`, ranks 0–1, each an intent with the extra `page` = `record` / `list`; New recording starts NO take).

## What to build (the permanent form — no stubs, no TODOs, no "v1")
Everything the doc lists for Voice Recorder: the one page with record and list states and the playback page; the
`:recorder` foreground service (microphone type, ongoing notification with Stop, `START_NOT_STICKY`, recovery in
`onCreate`, `dump()` printing its Diagnostics ring); AAC-LC mono 44.1 kHz 64 kbps from `MediaCodec` to a kill-safe raw ADTS
stream, muxed to `.m4a` at stop, recovered into a normal recording after process death; MediaStore publish into
`Recordings/` with `IS_RECORDING`; names "Recording", "Recording (2)", …; the list of EVERY `IS_RECORDING` file with the
T15-3 rule (rename / delete / trim only where `OWNER_PACKAGE_NAME` is `app.tileshell`; orphaned and other apps' files
play and share only), the READ_MEDIA_AUDIO-denied form with `rec_list_notice`, a `ContentObserver` so outside deletes
drop rows live; search box and the "Showing …" filter (All / My / Other apps' recordings); date-group headers; the hold
menu Share / Delete / Rename and the playback app bar Share · Trim · Delete · Rename · "…"; playback in a Media3 session
with `setId("recorder")` (scrubber per r11 4.5, `rec_position`); trim as a real cut to a NEW file, original kept until
Save; rename (`_display_name`, the provider's " (1)" on a clash, refused names with a notice); delete (with W10M's
confirmation, U5); share (`ACTION_SEND audio/mp4` with the MediaStore content URI and a read grant); Pause / Resume;
markers (Flag while recording and on playback; `recordings.json` in the files dir keyed by MediaStore id; dots on the
track); level rings (2.7); "…" Settings → an About page (U13; no Feedback); the storage floor (checked before a take and
every 5 s; 50 MB of `getAllocatableBytes`); pause on a call from the AUDIO MODE listener (no READ_PHONE_STATE);
`AudioRecordingCallback` silenced → `paused: silenced`; the RECORD_AUDIO-denied state with the grant in place.

**The microphone hold (T15-4 + T15-28) — an ADD to phase 03/05's arbitration in the `:speech` process:** extend
`ISpeech.aidl` with `String holdMicrophone(ISpeechCallback cb, String who)` (null = held, else the holder's name) and
`void releaseMicrophone(ISpeechCallback cb)`; a `cb` not already registered is refused; `MicArbiter` keeps the holder's
NAME beside its key (Tess = `cortana`, the keyboard = `keyboard`, you = `recorder` — find where the existing clients
acquire and pass their names), every refusal including `startListening`'s logs `… refused: held by <who>` in the
`:speech` ring, `MICROPHONE_BUSY`'s detail carries `who`, and Tess's notice and the keyboard's busy text take their
wording from it ("The keyboard is using the microphone right now." / "The voice recorder is using the microphone right
now."). `SpeechService` stays `exported="false"`. Keep MicArbiter's existing JVM tests passing and add tests for the
names. The recorder binds `ISpeech` (as `ime/` does), registers, holds, records, releases; a killed `:recorder` is freed
by `onCallbackDied`.

**Also yours:** the Music skip predicate in `MusicStore` with its diagnostics line; the Voice Recorder Start tile is a
static glyph tile (U12: Microphone glyph) — nothing to publish; Voice Recorder's `shortcuts_recorder.xml`.

## Tests you must write (host JVM)
The ADTS header writer and the frame parser that recovery uses (a truncated last frame is dropped, never crashes); the
recording-name numbering; the T15-3 / T15-26 capability rule (own / other / orphan × share / rename / delete / trim); the
filter and search; the marker times (pause excluded); the storage-floor decision; the "Showing …" kinds; MicArbiter's
named hold and refusals. Report the count and the exact `./gradlew :app:testDebugUnitTest` result.

## Report back (final message, under 80 lines)
1) your commits (hash + subject), 2) files added / changed, 3) test result quoted, 4) the exact diagnostics lines and tags
you implemented (so the lead can grep), 5) every design call you made that the doc left open, 6) anything you could not
build and why, 7) the INDEX Change Log facts the lead must record (the MicArbiter ADD to phases 03/05, the MusicStore
predicate for phase 10).
