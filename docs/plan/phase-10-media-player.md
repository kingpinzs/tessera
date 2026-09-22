---
status: DRAFT   # Stage A: interview in progress; Q1 ruled 2026-09-22, Q2 is with Jeremy
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

## Interview queue (Stage A step 4)

- ~~Q1 — what the player IS.~~ Ruled 2026-09-22 ("A"); see Decisions.
- ~~Q2 — where it lives.~~ Settled by Q1: it takes over the MUSIC slot.
- **Q3 — what this is measured against.** With Jeremy. Nothing in R1/R3/R6/R7 covers Groove Music: R3 and
  R6 measured Start, the app list and Cortana, and R7 measured Phone, Messaging, the Notebook and the
  action center. So either a research task produces the numbers first, or the build approximates from the
  shell's existing tokens and records every value as an approximation. This gates FINAL the same way R3
  gated phases 01-03 and R7 gated phase 06.
- Q4 — the library: what it indexes, how it handles a phone with no local audio, and whether it watches
  MediaStore the way PhotosFeed does.
- Q5 — playback: what the engine is, what happens to the existing MusicFeed and the tile's transport
  controls (they read a media session today — this app would OWN one), and headset / Bluetooth handling.
- Q6 — the lock-screen and glance relationship (phase 07 territory).

## Build tasks

_To be written once the interview settles the scope._

## Acceptance criteria

_To be written once the interview settles the scope._

## Edge cases

_To be written once the interview settles the scope._

## QA evidence

_None yet._
