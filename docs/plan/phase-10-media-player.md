---
status: DRAFT   # Stage A: interview in progress; Q1, Q3-Q6 ruled 2026-09-22, Q7 is with Jeremy
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

It is a real app inside the APK: a launcher activity with android.intent.category.APP_MUSIC, so the
MUSIC slot resolves to it exactly as it resolves any music app and no resolver, app list or per-package
tile rule needs a carve-out. That is also what W10M did — Groove was an app in the list.

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

_To be written once the interview settles the scope._

## Acceptance criteria

_To be written once the interview settles the scope._

## Edge cases

_To be written once the interview settles the scope._

## QA evidence

_None yet._
