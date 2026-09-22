# R10 — Review of the "Metro OS for Android" secondary spec

**Provenance.** Pasted by Jeremy on 2026-09-22 as a *secondary plan that can be used to modify our
current plan*, with two instructions: "do not use it all and dont take all as fact … use it to
reference", and "do not use names as 100% fact". His framing: **make this more like Windows Mobile
"as if they never stopped developing it."** Also a standing order from the same message: the Start
button must stop being the Windows logo.

**Method.** Each item in the spec was checked against what this build already has (the INDEX phase
table, PLAN.md, the FINAL phase docs, and the code) and against the platform. Nothing here changes a
FINAL doc; items marked *question* go to Jeremy one at a time in the Stage A question shape, and a
ruling lands in PLAN.md before anything is split into a phase.

Verdicts: **HAVE** (already built or already in a FINAL phase — ours is better grounded), **ADD**
(worth adding, as a question), **ADAPT** (right idea, wrong mechanism or wrong facts), **REJECT**.

---

## 1. The Start button (standing order, not a question of whether)

The only Microsoft mark drawn by the shell is the Windows logo on the drawn nav bar
(`WindowsGlyph()` in `bars/SystemBars.kt`, one call site, `nav_windows`). Four candidates were drawn at
the real size, 20 epx in the 48-epx bar, next to today's glyph:

- `r10/start-mark-candidates.png` — each mark in a real nav bar with the Back and Search glyphs
- `r10/start-mark-large.png` — the same marks large, so the shapes can be judged

| | Mark | Reads as | Risk |
|---|---|---|---|
| A | one wide tile over two small | the Start screen itself (wide + small tiles) | still a square split into panes — the closest of the four to the four-pane flag |
| **B** | **three tiles and one empty place** | **a tile being laid — which is what "tessera" means** | **clearly not four panes; distinct L silhouette at 20 epx** |
| C | a tile outline holding a live square | a tile with something on it | reads as a media "stop" or a screen-mirror icon |
| D | two tiles, one stepped behind the other | layered tiles | reads as the universal "copy" icon — rejected |

**Lean: B.** It is the only one that is both unmistakably tiles and unmistakably not the flag, and it
is the product's own name drawn as a picture. The swap is one drawing function; it is reversible.

Two lesser references to flag while this is open (not marks, descriptive uses of the name):
- Start + theme's press-style radio labels read "None (Windows 10 Mobile)" and "Tilt (Windows Phone 8)".
- Tess's three-tap easter egg reveals the original assistant's look for 5 s (no name drawn).

---

## 2. Triage

| # | The spec says | This build | Verdict | Why |
|---|---|---|---|---|
| 1 | 0-dp corners, lowercase pivots, accent palette | phase 01, measured (R1/R3/R6) | **HAVE** | The spec's numbers (36–42 sp pivots, a 4-column tile grid) carry no source; ours are measured. Keep ours. |
| 2 | Segoe UI everywhere | Selawik (Q8) | **REJECT as written** | Segoe UI is Microsoft's font and cannot ship in the APK. Selawik is Microsoft's own open-source (OFL) metric match, which is why Q8 picked it. |
| 3 | Fluent "acrylic" modifier | nothing | **ADD — question 1** | This is the strongest "never stopped developing" signal: Fluent Design reached desktop Windows 10 in 2017, right after W10M stopped. **But the spec's code is wrong**: a `graphicsLayer` render effect blurs the element's OWN content, not what is behind it. Real acrylic needs a blurred copy of the backdrop (in-app) or cross-window blur-behind for overlay windows (API 31+, device-dependent — `WindowManager.isCrossWindowBlurEnabled`). |
| 4 | Accessibility global actions for Back / Home / Recents | phase 04 (the accessibility service), Recents is phase 04 open item 7 | **ADAPT** | The API is real (`performGlobalAction`). It belongs to phase 04, which already owns the accessibility service. |
| 5 | A W10M nav bar drawn over **every** app | drawn on the shell's own screens only | **ADD — question, gated on R4** | W10M's bar was everywhere, so this is on-brief. The cost the spec does not mention: unrooted, other apps cannot be made to inset for it, so it covers the bottom of every app (tab bars, send buttons); Android's own nav bar or gesture handle cannot be hidden in other apps since Android 11 removed `policy_control`; the keyboard still draws above it. Phase 04's R4 nav-bar probe (P5) is where the real behaviour gets measured. At most an opt-in. |
| 6 | 12-dp top-edge grabber + sliding action center | phase 04 (R7-measured pull-down) | **HAVE / ADAPT** | A full-width 12-dp touch strip on top of every app steals the top of every app's touches (toolbars, back arrows). The spec's 0.75 threshold and 240 ms are unsourced; phase 04's come from R7's 60-fps recording. |
| 7 | Notification parser | phase 01 listener (tiles), phase 04 (action center list) | **HAVE** | — |
| 8 | A third pane LEFT of Start: a "Glance feed" of cards | two panes (Start, app list) | **ADD — question** | W10M never had it; the continued-development analogue is desktop Windows' later widgets board. **Name collision**: our phase 07 "Glance" is the always-on screen (W10M's Glance Screen), a different thing — this needs another name. Content that already exists in the build: agenda, weather, now playing, Tess reminders; plus real Android widgets through AppWidgetHost (they draw themselves, so they would not look Metro). |
| 9 | 3D flip engine | phase 01 (R3 A7 flip) | **HAVE** | — |
| 10 | "MixView": press a tile, four quick actions burst out around it | nothing | **ADD — question** | A cancelled pre-release concept (treat the name as unverified). The real data source on Android is **App Shortcuts** (`LauncherApps.getShortcuts`, which only the HOME app may call — we are it): "New message", "Navigate home", etc. **Conflict**: press-and-hold is W10M's measured way into edit mode (R6 §1.1.1), so this needs a different trigger — a swipe on the tile, or pen hover if the S25U's S Pen still reports hover (to verify on the phone). The spec's code is Material 3; this build uses foundation only. |
| 11 | App list with an A-Z jump matrix | phase 01 | **HAVE** | The spec says 4 columns; ours follows R3 X8. |
| 12 | "Terra" assistant: ring canvas, notebook, remote LLM client | Tess (03), on-device LLM (08), harness + Notebook (09) | **HAVE, one conflict** | The notebook is phase 09's Notebook, already ruled. The **remote client (OpenAI / Ollama) conflicts with Q2 / A11 (no cloud AI)**. An Ollama on Jeremy's own PC is not "cloud", which is the only version worth putting to him — not as the default. |
| 13 | Settings hub, toggle switch | phase 01 hub, W10M toggles | **HAVE** | — |
| 14 | Phone and Messaging hubs | phase 06 | **HAVE** | The spec's bubble colours are unsourced; phase 06 measures its own. |
| 15 | Calculator (standard / scientific / programmer) | nothing | **ADD — question** | A W10M inbox app, self-contained, cheap, and Tess can use it ("what's 15 % of 80"). Belongs with a wider question: which inbox apps next (Alarms & Clock, Calculator, Camera, Photos, Calendar, Voice recorder…). |
| 16 | First-run setup wizard (OOBE) | the setup checklist | **ADAPT** | A W10M-style first-run flow that walks the existing checklist rows in order, picks the accent, and sets Home. Small, and built on what exists. |
| 17 | Monorepo of ~15 Gradle modules | one app module | **REJECT (for now)** | A restructure with no visible gain while the build is fast; Hard Rule 3. Revisit only if build times hurt. |
| 18 | Room database | small JSON stores, temp-and-rename writes | **REJECT (for its own sake)** | Adopt only when a feature needs real queries. |
| 19 | minSdk 29, CameraX listed but no camera app | minSdk 34 (the S25U is the target) | **n/a** | CameraX only matters if a Camera app is chosen under item 15. |

---

## 3. What "as if they never stopped developing it" plausibly means

Agent inference, labelled as such — what desktop Windows 10 shipped in the years after W10M's last
build that a phone still in development would most likely have received:

- **Fluent Design** (2017): acrylic materials, reveal light, depth and connected animations — item 3.
- **A widgets / "at a glance" surface** (2021 onward) — item 8.
- **Clipboard history and an emoji / GIF panel** in the keyboard (2017–2018) — phase 05's territory.
- **Focus assist** (2018), the successor to W10M's quiet hours — phase 04's action center.

Everything else in the spec either exists here already or conflicts with a ruling.

---

## 4. Question order for Jeremy (Stage A shape, one at a time)

1. **Design direction** — frozen W10M look plus new capabilities / W10M plus Fluent materials /
   a full Windows 11-style turn. Load-bearing: it decides items 3, 8 and 10 and how the Start mark
   is drawn. **RULED 2026-09-22: B, W10M plus Fluent materials** (PLAN.md Rulings).
2. The Start mark — A / B / C, lean B (drawn in `r10/`). **RULED 2026-09-22: B.** Also ruled the same
   day without a question: the first-run setup wizard (item 16) is added, and tile quick actions
   (item 10) are added with Jeremy's own motion values. The quick actions' trigger is question 3.
3. Tile quick actions: the trigger (press-and-hold is edit mode). *(asked 2026-09-22)*
4. A third pane left of Start — and its name.
5. Which inbox apps next (Calculator first?).
6. A Tessera bar over every app — opt-in only, after R4's probe.
7. A local-network LLM (Ollama on the PC) as an option for phase 08 — against Q2's no-cloud ruling.
