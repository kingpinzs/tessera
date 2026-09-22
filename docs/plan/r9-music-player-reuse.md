# R9 — Reuse + licence check: is there a Groove-like music player we can add?

Asked by Jeremy, 2026-09-22: "Music player app we build unless there is one exactly like groove already
open source we can add." Same method as R2: the licence is what GitHub's detector reports for the repo
(`license.spdx_id` from `api.github.com/repos/<owner>/<repo>`), read directly rather than off a search
result or a README.

**Verdict: we build it.** Nothing exists that can be added. The single closest candidate is GPL-3.0 —
which by R2's own finding would make the whole APK GPL — and it is styled after Windows Phone 8 rather
than Windows 10 Mobile, so it is not "exactly like Groove" either. The UI kit underneath it IS usable
and is already this build's dependency, so the idiom is available without the licence.

## What was checked

| Project | What it is | Licence (read from the API) | Verdict |
|---|---|---|---|
| [Diffechento/MetroMusic](https://github.com/Diffechento/MetroMusic) | Android music player "styled after Windows Phone 8 — the panorama is the library, the full player rises out of the mini one"; Compose, Media3, built on MangoTile. Created 2026-07-28, last push 2026-09-14, 15 stars | **GPL-3.0** | **NO.** Copying it makes the shell GPL (R2's standing trap). Also WP8 panorama, not W10M pivot — see below |
| [Diffechento/MangoTile](https://github.com/Diffechento/MangoTile) | The Compose UI kit MetroMusic is built on: panorama, pivot, live tiles, LongListSelector with its jump grid, WP8 transitions. Last push 2026-09-14 | **MIT** | **YES — and already ours.** R2 Part 1 already picked MangoTile as a dependency for tilt / turnstile / pivot / long-list |
| [android/uamp](https://github.com/android/uamp) | Google's Universal Android Music Player *sample*. 13,170 stars, **archived**, last push 2026-01-09 | **Apache-2.0** | **REFERENCE.** Not a player and no longer maintained, but the canonical Media3 session/service shape |
| OxygenCobalt/Auxio, Fossify Music Player, Retro Music, Gramophone, Vanilla | The mature local-library Android players | **GPL-3.0** across the board (the same pattern R2 found on every dialer, SMS app and keyboard) | **NO** for code. Auxio is already installed here as a QA fixture and stays useful as one |
| BreadPlayer, zhiyiYo/Groove, GrooveMusicClone | The things that come back for "Groove clone" | — | **Not applicable.** All desktop or web (UWP / C#, PyQt5); there is no Android Groove clone |

## Why the closest match still would not have been it

MetroMusic is **Windows Phone 8**: its library is a *panorama* — one wide canvas the eye pans across.
Groove on **Windows 10 Mobile** is a *pivot* — separate headed sections you swipe between — and this
build's target was moved from WP8.1 to W10M in PLAN Q3. So even with a permissive licence it would have
been a port between two different idioms, not an adoption.

## What this changes

Nothing about the plan, and one thing about the cost. Phase 10 builds the player, as Q1 said. It gets
MangoTile's pivot and LongListSelector for free — MIT, already a dependency, and the same kit the one
credible look-alike chose — so the collection half of the app is assembly rather than invention, which
is what Q3's "approximate the collection from geometry we already have" was already counting on.
