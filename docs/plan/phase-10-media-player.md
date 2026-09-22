---
status: FINAL   # frozen 2026-09-22 by Jeremy's ruling, WITHOUT the cross-model review (see Decisions); changes via a dated INDEX.md Change Log entry
---
# Phase 10 — W10M media player

## Goal

Jeremy, 2026-09-22: "Need a media player that feels and looks like the windows one."

A media player that belongs to the shell, in the W10M idiom the rest of this build is held to — so the
Music tile opens something that looks like it came off the phone rather than something Android happened
to resolve.

## Scope

**In (from Q1):** a local music app in Groove's idiom — a collection with pivots (albums / artists /
songs / playlists), a full now-playing screen, the shell's own playback engine and media session, and it
takes over the Music slot so the tile opens it. The tile's grown now-playing face and transport controls
already exist (INDEX Change Log 2026-09-21 item 3) and this is what they point at.

The rest of the scope is still being settled by the interview.

**Out (explicitly):** cloud accounts, streaming services and online catalogues (A11: no cloud); anything
that needs a separately installed app (RV1).

## Decisions

**Q1 — what the player IS (2026-09-22, Jeremy: "A").** A local music app in Groove Music's idiom:
collection pivots (albums / artists / songs / playlists), a full now-playing screen with large art, the
shell's own playback engine, and it takes over the MUSIC slot. NOT music-and-video (W10M shipped those as
two apps and the second is not asked for), and NOT a controller-only surface over someone else's player.

Two things follow without needing their own question. The MUSIC slot resolves to this app rather than to
whatever Android offers, which is what Q2 in the original queue was going to ask. And "no cloud" (A11)
takes Groove's Radio pivot and its streaming catalogue off the table by itself — what is left is the
local library, which is what a phone with no account showed anyway.

**Q3 — what this is measured against (2026-09-22, Jeremy: "C").** The NOW-PLAYING screen is measured;
the collection pivots are approximated from geometry this build already has.

That split is not a compromise for its own sake. The now-playing screen is the one people look at, and it
is the one surface here with no existing analogue anywhere in the shell — the art, the transport row and
the track metadata are laid out in a way nothing in R3, R6 or R7 covers. The collection, by contrast, IS
the pivot pattern that R6 measured for Cortana's pane and phase 06 measured for Phone's tabs: a pivot
header, a scrolling list beneath it, the same type ramp. Measuring it again would produce numbers this
build already has under another name.

So: a targeted research task (**R8**) produces the now-playing numbers before this doc can go FINAL, the
way R3 gated phases 01-03 and R7 gated phase 06. Every value the collection takes from an existing
measurement cites the measurement it came from; every value that is genuinely new and NOT measured is
recorded as an approximation with a NEEDS-HUMAN row, the same discipline the default Start layout got.

**Q4 — what the Music tile follows (2026-09-22, Jeremy: "So if I am playing an mp3 then the music player
is used if its a different app then its that apps tile if there is one").**

Not one of the three offered: the now-playing face belongs to THE TILE OF THE APP THAT OWNS THE SESSION.
Playing a local file goes through this player, so it appears on the Music tile; playing something in
another app appears on THAT app's tile, if one is pinned; and if that app has no tile, nothing shows,
because no tile is borrowed to display another app's music.

That is what W10M actually did — a live tile showed its own app's content, and Groove's tile showed
Groove — and unlike the strict reading of it (offered as "B") it takes nothing away: the behaviour moves
rather than disappearing.

It is buildable from machinery that already exists, which is why it was accepted as given rather than
re-offered. [LiveTileEngine.packageKey] already keys content per package, [StartPage] already falls back
to package content for a slot tile, and [MusicFeed] already records the session owner (the controller's
packageName). What changes is the KEY the now-playing face is published under — the owner's package
instead of the MUSIC slot — and the tile [ActiveTiles] grows with it.

Two consequences, both recorded because they touch shipped work. (1) The tile growth and the transport
controls verified on the device (INDEX Change Log 2026-09-21 item 3; Jeremy: "it grows, glyphs do
survive, buttons do drive the player") move to whichever tile owns the session, so they are RE-verified
against this rule when it is built rather than assumed to still hold. (2) Until this player exists the
change is nearly invisible: a local file plays in whatever app resolves the MUSIC slot today, so the
face lands on the same tile it lands on now. The rule only starts to bite when a NON-slot app plays.

**Q5 — an app, and we build it (2026-09-22, Jeremy: "Music player app we build unless there is one
exactly like groove already open source we can add").**

It is a real app inside the APK: a launcher activity with android.intent.category.APP_MUSIC, so it sits
in the app list beside every other app and the MUSIC slot points at it the way it points at any music
app. That is also what W10M did — Groove was an app in the list.

**CORRECTION, found while building task 1 on 2026-09-22.** This decision as first written said the slot
would resolve to the player "with no carve-out". That is wrong, and the code says so: SlotResolver
auto-assigns a category slot only when Android resolves EXACTLY ONE handler for the category, or a
handler the user has set as default. Declaring APP_MUSIC makes this player one candidate among however
many music apps the phone has — on the QA emulator that is at least three — so the slot would resolve to
nothing and the tile would read "Tap to choose". The fix is to SEED the assignment once
(LayoutStore.assignSlotOnce, marker slot:music:v1), which is the explicit-assignment path that already
existed and exactly what the user would otherwise do by hand. Re-pointing the slot at another player
still works and still sticks, so nothing is taken away. Acceptance row E1 is what proves it.

The "unless" was checked rather than assumed: research task **R9** (r9-music-player-reuse.md). Nothing
can be added. The one credible look-alike, MetroMusic, is GPL-3.0 — which by R2's standing finding makes
the whole APK GPL — and is styled after Windows Phone 8's panorama rather than W10M's pivot, so it was
never "exactly like Groove". Every mature Android player is GPL-3.0, the same wall R2 hit on dialers,
SMS apps and keyboards. What IS reusable is the UI kit underneath it, MangoTile (MIT), which R2 already
picked as a dependency for this build.

One consequence for the app list: this player appears there like any other app, where the hold menu now
offers Uninstall. It cannot be uninstalled separately — it is part of the shell APK — so it must be
excluded the way the shell's own package already is (AppUninstall.canUninstall), or the menu offers an
item that cannot work.

**Q6 — what the library indexes (2026-09-22, Jeremy: "A").** Everything MediaStore reports as audio,
watched for changes the way PhotosFeed already watches images rather than scanned once at start.

The consequence, recorded because it is the thing the other options existed to avoid: MediaStore's audio
collection is not only music. Ringtones, notification sounds, alarms and voice recordings are audio too,
so on a real phone they appear in the songs list and their folders appear as albums. That is the answer
given, and it is one predicate to add later (MediaStore.Audio.Media.IS_MUSIC) if it turns out noisy on
the S25 Ultra — a NEEDS-HUMAN row rather than a rebuild, since nothing else depends on it.

**Q7 — what the first version holds (2026-09-22, Jeremy: "EVERYTHING there is only one version built").**
All of it, and there is no "first version": collection pivots, now-playing, queue, shuffle and repeat,
notification controls, headset and Bluetooth, playlists that can be created / renamed / reordered /
deleted, sleep timer, equaliser, and gapless or crossfade playback.

"Only one version" is not a new rule, it is Hard Rule 16 applied here: a phase builds the FINAL form of
its part and never an interim one for a later phase to replace. So playlist editing and the extras are
in this build, not deferred behind it.

**Q8 — playback mechanics (agent call, 2026-09-22).** No question was put to Jeremy because no fork
survives the rulings above. Media3 / ExoPlayer: Apache-2.0, maintained, and what both MetroMusic and
Google's own archived sample use — R9 found nothing else with a usable licence. Playback lives in a
MediaSessionService as a foreground service so it survives Start being killed, which is also what makes
Android draw the notification transport and what gives Q4's rule a session to key on. MusicFeed is NOT
replaced: it keeps reading media sessions, and gains the per-package routing Q4 settled, so this player
is simply the session it usually finds.

**Q9 — glance (deferred, 2026-09-22).** The lock screen and glance surface belong to phase 07 and are
gated on phase 04's accessibility service. This phase publishes a media session; what a glance screen
draws from it is phase 07's question, asked there.

**R8 landed 2026-09-22, and the two rulings it asks for (agent calls).**

R8 (r8-groove-measurements.md) established the now-playing screen's structure and every static value
from four native-resolution screenshots across two devices at two different W10M scale factors — which
is what lets each number be classified as fixed-in-epx or proportional-to-width instead of guessed. The
shape task 7 builds: a 72-epx chrome band (24 status + 48 header carrying the hamburger, "NOW PLAYING"
and search), art flush beneath it, a two-line metadata block, a time-labelled scrubber with an 18-epx
HOLLOW thumb, a six-button transport row on an exact W/6 grid, and a centred chevron at W/2 that
expands the play queue. THERE IS NO BOTTOM APP BAR — the overflow is the sixth transport cell. Artist
art is full-bleed with no scrim, no blur and no tint; album art insets a square on a black page. And
nothing on the collapsed screen is accent-coloured: the scrubber is white at two opacities and the
accent appears only on the queue's playing row, proven across two phones with different accents.

**Ruling 1 — build to V-2016 (agent call).** Neither measurable source is the governing build: every
capture is 10586-era, and the Dec-2015 and Feb-2016 versions genuinely differ (art 311 epx with 24-epx
margins versus 328 epx with 16-epx margins; title cap 22.75 versus ~14 epx; a second line reading
"Artist" versus "Artist • Album"). V-2016 is the later of the two and the only one carrying the album on
the second line, and it is the release AAWP documents as the update that changed artwork handling — so
it is the nearer ancestor of whatever 14393+ shipped. Taken as an agent call rather than sent back as a
question because it is reversible by eye: **NEEDS-HUMAN row H-M1** asks Jeremy to look at the finished
screen, and switching to V-2015 is four numbers and a string.

**Ruling 2 — the vertical layout is anchored, not absolute (agent call).** R8's own §1.10 says the
chrome anchors to the top while the nav bar, chevron and transport row anchor to the bottom at 48 / 16 /
56 epx, and that this holds across both a 640-epx and a 731-epx canvas. The S25 Ultra is ~780 epx tall,
so the measured absolute y values (400 / 444 / 488 / 536) are NOT carried across; only the offsets from
each end are, and the extra ~140 epx falls between the art and the metadata. Building to the absolute
numbers would put the transport row 244 epx off the bottom of the phone this is for.

**Motion is entirely UNMEASURED** and task 7 cannot close it: YouTube extraction was blocked in the
research environment, so not one frame was pulled. Entry and exit, art transitions on track change, the
queue collapse/expand and press feedback all get **approximations recorded as approximations**, with
**NEEDS-HUMAN row H-M2**. MangoTile's own artwork-slide claim is explicitly WP8 and carries no numbers,
so it is not a substitute.

## Interview queue (Stage A step 4)

- ~~Q1 — what the player IS.~~ Ruled 2026-09-22 ("A"); see Decisions.
- ~~Q2 — where it lives.~~ Settled by Q1: it takes over the MUSIC slot.
- ~~Q3 — what this is measured against.~~ Ruled 2026-09-22 ("C"); see Decisions. Adds research task R8.
- ~~Q4 — what the Music tile follows.~~ Ruled 2026-09-22; see Decisions.
- ~~Q5 — an app or a shell page.~~ Ruled 2026-09-22; an app we build. Adds research task R9 (done).
- **Q6 — the library**: what it indexes, a phone with no local audio at all, and whether it watches
  MediaStore the way PhotosFeed does.
- Q7 — playback: the engine, headset and Bluetooth handling, and what becomes of MusicFeed.
- Q8 — the lock-screen and glance relationship (phase 07 territory).

## Build tasks

Ordered so that everything the tile rule and the UI depend on exists before they are built, and so the
one task that touches shipped code is done early enough to be re-verified rather than at the end.

1. **App identity.** A launcher activity with android.intent.category.APP_MUSIC inside the shell APK, so
   the MUSIC slot resolves to it with no carve-out (Q5). It appears in the app list like any app — and
   must be excluded from the hold menu's Uninstall the way the shell's own package already is
   (AppUninstall.canUninstall), because it cannot be uninstalled separately and an item that cannot work
   is worse than no item.
2. **Library index.** Everything MediaStore reports as audio (Q6), grouped into albums, artists, songs
   and playlists, observed for changes with a ContentObserver the way PhotosFeed observes images rather
   than scanned once at start. Includes the empty case: a phone with no audio at all.
3. **Playback.** Media3 / ExoPlayer inside a MediaSessionService running as a foreground service (Q8),
   with queue, shuffle, repeat, and gapless / crossfade (Q7). Surviving Start being killed is the point
   of the service, not a bonus.
   **Status 2026-09-22:** the service, the session, audio focus and becoming-noisy are built; queue,
   shuffle and repeat are ExoPlayer's own and are driven by the UI in task 6. **Gapless is ExoPlayer's
   default and comes free; CROSSFADE IS NOT BUILT.** ExoPlayer has no crossfade — it needs two players
   with volume ramps, or a custom AudioProcessor — so it is the one part of Q7's "everything" still
   outstanding, recorded here rather than quietly dropped. E17 does not pass until it exists.
4. **Session, notification and buttons.** The media session Android draws its transport notification
   from, plus headset and Bluetooth media buttons, and audio focus (ducking, pausing on a call, not
   resuming after a transient loss the user did not ask to resume).
5. **The tile rule (touches shipped code).** MusicFeed publishes the now-playing face under
   LiveTileEngine.packageKey(owner) instead of the MUSIC slot key, and ActiveTiles grows THAT tile (Q4).
   Re-verifies the three device findings this moves: the tile grows, white glyphs survive a bright
   cover, and the transport controls drive the player.
6. **Collection UI.** The pivot — albums / artists / songs / playlists — on MangoTile's pivot and
   LongListSelector with its jump grid (MIT, already a dependency, R9). Geometry approximated from R6's
   Cortana pane and phase 06's Phone tabs, each value citing the measurement it came from (Q3).
   **Built 2026-09-22, WITHOUT MangoTile, and that sentence above is wrong on two counts.** MangoTile is
   **not** a dependency of this build — R2 proposed it, nothing was ever added, and phase 01 wrote the
   jump grid in-house — and R8 §0.4 found its numbers unusable: it is explicitly WP8-targeted and its
   one motion claim carries no duration, no easing and no citation. Adding a WP8 kit to draw a W10M
   pivot would put un-retimed motion into the screen this phase exists to get right, so the pivot is
   built on the geometry this shell already has and every number cites its source (MusicMetrics). Phase
   06 does not exist yet, so its Phone tabs could not be cited either; the rows are the app list's
   (R3 C2 / R6 §5.1.4) and the pivot header is P4 design, flagged as such. Evidence
   qa/phase-01/MUSIC6, 37/37 on the emulator against six tagged fixtures.
7. **Now-playing screen.** Built to R8's measurements. **Gated on R8**: building it first would mean
   building it twice, which is the whole reason Q3 ruled for measuring this one screen.
8. **Playlists.** Create, rename, reorder, delete (Q7), persisted where MediaStore playlists are not
   writable on modern Android — the store is the build's own, and where it lives is a build-time call
   recorded in this doc when task 8 starts.
9. **Extras.** Sleep timer and equaliser (Q7).
10. **Settings page + checklist rows.** The audio permission (READ_MEDIA_AUDIO), and the rows the
    onboarding checklist needs so a phone with the permission denied says so rather than showing an
    empty library.

## Acceptance criteria

Every row runs on the device and captures its evidence, per the harness in qa/phase-01/scripts/lib.sh.

| Row | What must hold |
|---|---|
| E1 | The MUSIC slot resolves to this player with no explicit assignment, and its tile opens it |
| E2 | The app appears in the app list, and its hold menu offers Pin to Start but NOT Uninstall |
| E3 | The library lists every audio file MediaStore reports, grouped into the four pivots |
| E4 | A file added while the shell is running appears without a restart (the observer, not a rescan) |
| E5 | A phone with no audio at all shows an empty state, not a crash and not a blank pivot |
| E6 | Play, pause, next, previous, shuffle and repeat all do what they say, with audio actually heard |
| E7 | Playback continues when Start is killed, and the notification transport still drives it |
| E8 | A headset button and a Bluetooth button both reach the session |
| E9 | Audio focus: a call pauses playback; a notification ducks it; playback does not resume by itself |
| E10 | Playing a local file puts the now-playing face on THIS app's tile (Q4) |
| E11 | Playing in another app that has a pinned tile puts the face on THAT tile, not on this one |
| E12 | Playing in an app with no tile puts the face nowhere — no tile is borrowed |
| E13 | The tile grows while playing, white glyphs survive a bright cover, and its controls drive playback (re-verification of the 2026-09-21 item 3 findings under the new rule) |
| E14 | The now-playing screen matches R8's measured values within its stated tolerances, built to V-2016 and anchored per §1.10 rather than to absolute y values |
| H-M1 | NEEDS-HUMAN: Jeremy looks at the finished now-playing screen and rules V-2016 vs V-2015 (four numbers and a string) |
| H-M2 | NEEDS-HUMAN: Jeremy judges the motion, which R8 could not measure at all |
| E15 | Playlists can be created, renamed, reordered and deleted, and survive a restart |
| E16 | Sleep timer stops playback at its time; the equaliser changes what is heard |
| E17 | Gapless / crossfade behaves as set between two tracks |
| E18 | With READ_MEDIA_AUDIO denied, the app says so and the checklist row offers the grant |

## Edge cases

- Audio with no album art at all, and audio whose art fails to decode
- A track whose metadata is entirely missing (no title, no artist) — it still has to be playable and nameable
- Enormous libraries: the jump grid and the pivot must stay responsive at thousands of tracks
- A file deleted, or its storage unmounted, while it is the playing track
- Two audio files with identical title, artist and album
- Ringtones, alarms and recordings in the library, which Q6 admits by design — the UI must not assume "music"
- A playlist referring to a file that no longer exists
- Another app taking audio focus and never giving it back
- The shell being killed mid-track, and what the tile shows on the next start
- A Bluetooth device disconnecting mid-playback
- The MUSIC slot explicitly reassigned to a different player: this app stays in the app list and keeps
  working, and Q4's rule then puts its face on its own tile rather than the slot's

## QA evidence

_None yet._
