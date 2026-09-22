---
status: DRAFT   # Stage A: interview in progress; Q1 and Q3 ruled 2026-09-22, Q4 is with Jeremy
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

## Interview queue (Stage A step 4)

- ~~Q1 — what the player IS.~~ Ruled 2026-09-22 ("A"); see Decisions.
- ~~Q2 — where it lives.~~ Settled by Q1: it takes over the MUSIC slot.
- ~~Q3 — what this is measured against.~~ Ruled 2026-09-22 ("C"); see Decisions. Adds research task R8.
- **Q4 — what the Music tile follows once the shell has its own player.** With Jeremy. The tile reads
  whatever media session is active today, and that is built and verified (INDEX Change Log 2026-09-21
  item 3; Jeremy on the device: "it grows, glyphs do survive, buttons do drive the player"). Once this
  app owns a session of its own, "the Music tile" could mean the active session whoever owns it, or this
  player's tile specifically. It is the one question here that can take away something already working.
- Q5 — the library: what it indexes, a phone with no local audio at all, and whether it watches
  MediaStore the way PhotosFeed does.
- Q6 — playback: the engine, headset and Bluetooth handling, and what becomes of MusicFeed.
- Q7 — the lock-screen and glance relationship (phase 07 territory).

## Build tasks

_To be written once the interview settles the scope._

## Acceptance criteria

_To be written once the interview settles the scope._

## Edge cases

_To be written once the interview settles the scope._

## QA evidence

_None yet._
