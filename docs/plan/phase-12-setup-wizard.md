---
phase: 12
slug: setup-wizard
status: DRAFT
depends-on: [01, 03, 05, 10]   # it walks rows those phases put on the Setup checklist, and reuses phase 01's accent setting and phase 03's blocked-permission rule
---

# Phase 12 — First-run setup wizard

## Goal
A phone that is missing a core grant meets a W10M-style setup wizard the first time Start draws, instead of a Start whose
tiles are silently empty. The wizard walks the Setup checklist's own rows (`onboarding/Checklist.kt`) in their order — one
source of truth — showing only the rows that are not yet granted, fires each row's real grant (Android's role sheet, the
runtime-permission dialog, the Settings page), advances on its own when the grant comes back, and ends with the accent
step. A phone that already holds every core grant never sees it, which is also why no existing QA driver needs a bypass:
the harness provisions the grants, and the wizard has nothing to do. Every value is a P4 design: W10M's out-of-box
experience was never measured (no R3 / R6 / R7 row), so the look is judged in NEEDS-HUMAN "accept" rows, not measured.

## Scope
**In:** the wizard surface, hosted inside `StartActivity` above the Start / app-list pivot the way the slot picker and the pin
band are; the visibility rule (a core row MISSING and the run not finished); steps derived from `ChecklistPage`'s row list
(same ids, titles, state lines and grant actions — no second list); the personalisation step (accent, writing phase 01's
`ShellSettings` accent, the X26 setting); "Not now" per step and "Skip setup" for the run; the finished / skipped marker
and its store; resume after process death by re-deriving from live grant state; the blocked-permission branch (Android will
no longer ask → the app's own info page, phase 03's 2026-09-22 rule); diagnostics lines; test tags; the harness change that
keeps every earlier driver off the wizard (`qa/phase-03/scripts/provision.sh` grants the core rows) plus one regression run.
**Out (explicitly):** Tess's own checklist rows (`cortana/CortanaChecklist.kt`: assistant role, microphone, contacts, texts,
calls, locations, call log) — they live on her Settings page by phase 03's decision, and the microphone is asked for the
first time she listens (INDEX Change Log 2026-09-22, `CortanaPermissionActivity`); this stands unless interview Q2 says
otherwise. Phase 04's rows (accessibility, overlay, Wireless debugging pairing, helper): phase 04 ADDs them to the checklist
and the wizard walks whatever rows exist — no placeholder step (Rule 16). A launcher entry or a separate activity for the
wizard (the shell's only launcher icons are Start settings, Music and Weather; Start is reached through the Home role). Any
W10M OOBE page the shell cannot own (region, Wi-Fi, Microsoft account, restore, Cortana sign-in). Reordering or renaming
checklist rows (the checklist is phase 01's; the wizard reads it). Changing what a grant does. Hooks for later phases.

## Decisions
- 2026-09-23: From phase 13 interview Q2 (agent reading): phase 13's "Transparency effects" switch is one of the visual items a
  preset sets (Q4 "everything visual"), so a preset can turn acrylic off (e.g. Midnight, for the battery).
- 2026-09-23: Interview Q6 — the four generated pictures are AI images (Jeremy: "(b) tell me what I need to do because I have
  both gpt and gemini personal accounts"). Jeremy generates HAL, Soft, Lumia and Midnight on his PERSONAL ChatGPT or Gemini
  account from the prompts in docs/plan/theme-art-brief.md and saves them to art/themes/; the agent crops and scales them for
  the S25 Ultra (1440 x 3120, with Start's 1.3x parallax headroom) and bundles them. Constraints, from the brief: portrait, no
  text, logos, faces or watermark, a calm middle so labels read through see-through tiles, and an original design (the HAL
  picture must not copy the film). The pictures are an INPUT this doc waits on: its FINAL needs them in art/themes/, alongside
  R12 for the original W10M preset. NEEDS-HUMAN: labels readable over each picture in its theme, judged on the phone.
- 2026-09-23: Interview Q5 — six presets (Jeremy: "(b) and the original W10M theme that was sent with phones"): Default,
  Windows 10 Mobile (original), HAL, Soft, Lumia, Midnight. Agent reading of the two that could sound alike (Jeremy can
  overrule): "Default" is this shell's own out-of-box look as built and measured; "Windows 10 Mobile (original)" is the look
  W10M phones shipped with, including Microsoft's stock pictures, which live in the A10 branding module (fine on Jeremy's phone,
  swappable if the project goes public). What exactly W10M phones shipped with (default accent by device, Dark / Light, stock
  Start and lock-screen pictures) is not in R1-R8, so a research item (R12, INDEX) gathers it and gates this doc's FINAL.
- 2026-09-23: Interview Q4 — a preset sets EVERYTHING visual, and choosing one applies it at once (Jeremy: "(c) so it has x
  amount of themes user selects one but can scroll down to change invidual items. when selecting a theme it auto changes the
  phone so they can see what it will look like right away"). A preset sets accent, Dark / Light, background picture, tile
  transparency, press style, Tess's look (ring and lens colours) and the keyboard's colours. The page (wizard step and Start +
  theme alike) lists the presets at the top; tapping one APPLIES it to the whole shell immediately — the preview is the real
  thing, no separate preview screen — and the individual items follow below it, each still changeable, after which the preset
  reads "Custom". Part ownership (agent): the theme-able colour hooks in Tess's persona (phase 03) and the keyboard (phase 05)
  are ADDs to those FINAL parts, built by phase 12 and recorded in the INDEX Change Log when built.
- 2026-09-23: Interview Q3 — personalisation is accent + Dark / Light + background picture, offered as THEME PRESETS (Jeremy:
  "(c) but it should come with its own background pictures sort of like a theme like a Hal theme and a soft theme ect so maybe it
  has preset but indivudial items can still be toggled"). The shell bundles its own background pictures; a preset (examples
  from Jeremy: a "HAL" theme, a "soft" theme) sets the items in one tap, and each item can still be changed on its own afterwards.
  This is a SCOPE ADD (PLAN.md Rulings, 2026-09-23). Part ownership (agent, Hard Rule 16): phase 12 builds the theme presets in
  their final form — the preset set, the bundled pictures, the wizard step, and the same presets on Start + theme (an ADD to
  phase 01's page, recorded in the INDEX Change Log when built). Open, asked next: what a preset sets (Q4), and which presets
  ship and where their pictures come from (Q5; bundled pictures must be ours to ship — A10).
- 2026-09-23: Interview Q2 — all of Tess's grants join the walk (Jeremy: "(c) This is to make it feel like the person is setting
  up an new phone with out rooting thier phone"). After the Setup checklist's rows, every row on Tess's own checklist
  (`CortanaChecklist`: default assistant, microphone, contacts, calendar write, texts, calls, location all the time, call log,
  read texts) is a wizard step with its own "why" line (Q1), walked through `CortanaPermissionActivity`'s existing paths, and a
  missing Tess row also summons the wizard (Q1's rule). THE INTENT, recorded because it governs later answers: the wizard is
  the shell's out-of-box experience — it should feel like setting up a new phone, with no root. Agent reading of that intent
  (Jeremy can overrule): every later phase that adds a grant (phase 04's accessibility, overlay and helper pairing, phase 06's
  dialer and SMS roles, phase 07's overlay reuse, the inbox apps' permissions) adds its step to the wizard the same way each
  phase adds its checklist row — one list, walked in order, each step with its why.
- 2026-09-23: Interview Q1 — every grant row is core, and every step says why (Jeremy: "(B) with somehing about the why"). The
  wizard appears while ANY Setup checklist row with a grant action is missing (Home, Notification access, Photos, Music,
  Calendar, Location, Usage access, Keyboard enabled, Keyboard selected) until each is granted or a run is finished (the
  finish / skip rules below decide what "finished" leaves). Each step carries a short "why" line in plain words: what the grant
  makes work in the shell and what stops working without it (e.g. Notification access: "Live tiles and unread counts come from
  your notifications. Without it the tiles stay still."). The lines are agent-written P4 copy and are judged in a NEEDS-HUMAN
  row; the acceptance rows check that every step shows one.
- 2026-09-22: Scope add (Jeremy: "do we have MetroSetupWizardScreen in our plan if not have to add it"): **a first-run setup
  wizard.** "MetroSetupWizardScreen" is the secondary spec's name, not this build's (PLAN.md Rulings; R10 item 16 ADAPT).
- 2026-09-22 (agent, R10 plan review; PLAN.md "Agent calls", triage): **the wizard shows only while a core grant is missing.**
  A phone that already holds every grant never sees it — which is also why no existing driver needs a test-only bypass: the
  harness provisions every grant. It walks the Setup checklist's own rows in order (one source of truth), then the accent.
- 2026-09-22 (agent): **P4 design throughout.** R3, R6 and R7 contain no W10M OOBE frame (R6 §4.2.1's "first-run page" is
  Cortana's sign-in page, not the phone's setup). Every look value here is an approximation; every NEEDS-HUMAN row is an
  "accept this design" row, never a fidelity row (R10 testability finding 26).
- 2026-09-22 (agent): **Host.** The wizard is a full-screen page composed inside `StartActivity`'s root, above the
  `HorizontalPager`, exactly where `pickerSlot` draws the `SlotPicker` and where `SecondaryPinPrompt` is drawn — not a
  second activity. Reasons: the HOME-role sheet and the runtime-permission dialogs return their results to the activity that
  raised them, and Start is that activity; the root already signs the `testTagsAsResourceId` contract (R10 testability 24);
  and StartActivity is portrait-locked with `configChanges` covering density / size / orientation, so no rotation recreates
  it. While the wizard shows, the pager does not scroll (`userScrollEnabled = false`, as edit mode does), Start's tiles keep
  running underneath and are not drawn over it (the wizard fills the page between the drawn bars).
- 2026-09-22 (agent): **Bars.** Phase 01's bar rule applies: the wizard is a shell screen, so Samsung's bars are hidden and
  the W10M status bar and Back / Windows / Search nav bar are drawn (`W10mStatusBar`, `W10mNavBar`). Back = the previous step
  (nothing on the first); the Windows key does nothing while the wizard shows (Start is behind it and Home is where you
  are); the Search key keeps the bar component's default (opens Tess; she asks for her own microphone). E8 proves the keys.
- 2026-09-22 (agent): **Visibility rule.** `show ⇔ (some CORE row is MISSING) ∧ ¬finished`. Which rows are core is
  interview Q1. "Finished" is a marker set when the run reaches its end or "Skip setup" is tapped (the persistence line).
  The rule is evaluated when StartActivity is created and on every resume while no run is in progress; a PARTIAL row (the
  Photos row under Android 14+ partial access) is never a reason to show the wizard, only a step inside a run.
- 2026-09-22 (agent): **Steps.** A run's steps are the checklist rows whose state is MISSING or PARTIAL and which have a
  grant action, in `Checklist.kt`'s list order (today: Default Home, Notification access, Photos, Music, Calendar, Location,
  Usage access, Keyboard enabled, Keyboard selected) — then the personalisation step, always last. The two probe rows
  (`samsung_badges`, `legacy_badges`) and the liveness row (`listener`, which goes green by itself once notification access
  is granted) are observations, never steps. A row already GRANTED is not shown. The step list is re-derived from live state
  on every resume of the activity (the dialogs and Settings pages all return through resume): a row that turned GRANTED
  leaves the remaining steps, a row that turned MISSING again is put back in its list position ahead of the accent step. If a
  different walking order is ever wanted, the checklist is reordered; the wizard never keeps an order of its own.
- 2026-09-22 (agent): **`ChecklistRow` gains `permissions: List<String>`** (empty for role / Settings-page rows), mirroring
  `CortanaRow`, so the wizard can tell a runtime-permission step from a Settings-page step without a second table. Phase
  01's `ChecklistPage` is unchanged in behaviour (an ADD to the data class; recorded in the INDEX Change Log when built).
- 2026-09-22 (agent): **Step page, P4 design (H1).** One page per step between the drawn bars: a progress caption "Step n
  of N" (12-epx caption class, accent), the row's title in the 24-epx title class, the row's one-line state text
  (`ChecklistRow.detail`) in the 15-epx body class, the row's status glyph and colour as the checklist draws them, one accent
  button whose label is the verb the row implies ("Set as default" for Default Home, "Allow" for a runtime permission, "Open
  settings" for a Settings page, "Turn on" / "Choose" for the two keyboard rows) firing the row's own `action` lambda, and a
  plain-text "Not now" beneath it. Page background = the theme background; page-to-page motion = phase 01's X7 Settings
  transition (`Motion.PAGE_ENTER_MS`, the Start entrance form). The first step's page also carries "Skip setup" at the top
  right of the content area; later steps do not (a skip is a decision made at the start, W10M's OOBE offered "skip" per
  optional page and the checklist covers the rest). Wording is judged in H1; nothing here is measured.
- 2026-09-22 (agent): **Auto-advance.** When the activity resumes with the current step's row GRANTED, the wizard advances at
  once with no extra tap (P2 seamless). PARTIAL counts as this step done (the user chose "Select photos"); the checklist row
  stays Partial and says so. Returned still MISSING: the page stays, "Not now" is the way on. Runtime-permission step
  returned MISSING with `shouldShowRequestPermissionRationale` false for a requested permission (Android will no longer
  ask — denied twice or "Don't ask again"): the button relabels to "Open app info" and starts
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the shell, the rule `CortanaPermissionActivity` and phase 10's Music
  row already follow (INDEX Change Log 2026-09-22 "THE MICROPHONE PROMPT NOW APPEARS"). Denied for good is never silent.
- 2026-09-22 (agent): **Persistence.** No step index is ever stored: a run killed half-way resumes at the first row still
  missing because the steps are re-derived from live grant state (E4). The only stored state is the finished / skipped
  marker, kept in its own `SharedPreferences` file `setup_wizard` (key `finished`): not in `ShellSettings`, whose
  `update()` rewrites every Start + theme key on each write, and not in `LayoutStore`, which holds what the grid IS.
  `pm clear` wipes it; `adb install -r` keeps it, so an update install never re-runs the wizard (R10 design finding 23).
  Consequence, stated so it can be overruled: a core row ADDed by a later phase (04's accessibility service) to a phone that
  has finished or skipped the wizard does not summon it — that row goes red on the Setup checklist, which is the permanent
  health surface; the wizard is the first-run surface. A core grant revoked later behaves the same way (E5, E7). No
  install-time heuristic and no `LayoutStore` version marker is used: the rule is state-derived, which is what makes an
  adb-provisioned device and a hand-provisioned phone identical.
- 2026-09-22 (agent): **Skip.** "Skip setup" sets `finished` and shows Start; every row it skipped stays red on the
  checklist. "Not now" advances one step; a run that reaches its end by "Not now" alone is finished too. Neither is ever
  re-asked on the next launch (H4). The wizard has no "later" nag.
- 2026-09-22 (agent): **Personalisation step.** Its contents are interview Q3; the agent call is the accent only. The accent
  grid is the same composable Settings > Start + theme draws (R3 A16's 6 × 8 grid, the `accent:<name>` swatches), pulled
  out of `StartThemePage` into one shared composable so both screens draw one grid and write one setting —
  `ShellSettings.update { it.copy(accent = …) }`, the X26 setting, never a second store. The step shows the current accent
  selected (the out-of-box Default Blue on a fresh install), a "Done" button, and is shown once per run at the end. It is
  part of the run, so it appears only when the run itself shows: a provisioned phone never sees it either.
- 2026-09-22 (agent): **Diagnostics** (R10 testability 25): `[wizard] shown: missing=<row ids>` when a run starts;
  `[wizard] not shown: core held` and `[wizard] not shown: finished` when Start draws without it; `[wizard] step <id>:
  granted | partial | not now | blocked (app info)`; `[wizard] skip`; `[wizard] accent <name>`; `[wizard] finished`. Read
  with phase 01's diagnostics command (`adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener`,
  `diag wizard` in the driver floor).
- 2026-09-22 (agent): **Test tags:** `wizard_page` (root), `wizard_step:<row id>`, `wizard_progress`, `wizard_title`,
  `wizard_body`, `wizard_action`, `wizard_not_now`, `wizard_skip`, `wizard_accent` (the personalisation step's root, with
  the shared grid's `accent:<name>` swatches inside it), `wizard_done`. Every text a row reads sits on the node that carries
  the tag (the MUSIC8 lesson, testability 24).
- 2026-09-22 (agent): **Harness** (R10 testability 3, 12, 24). `qa/phase-03/scripts/provision.sh` today grants the roles and
  installs with `-g` but does not grant notification access or Usage access (phase 01's own rows grant those inside
  `final_rows.sh`, `edge_liveness.sh`, `e20_part3.sh`); after this phase it also runs `cmd notification allow_listener
  app.tileshell/app.tileshell.feeds.TileNotificationListener`, `appops set app.tileshell GET_USAGE_STATS allow`, and
  `ime enable` / `ime set app.tileshell/.ime.KeyboardService` (phase 05's rows), so a provisioned AVD holds every row the
  wizard walks and E1 proves the wizard absent on it. E9 then re-runs one row per earlier phase (the `qa/phase-02/scripts/
  regress.sh` pattern) with no driver change. New drivers live under `qa/phase-12/scripts/` with `lib.sh` symlinked from
  phase 03 (INDEX 2026-09-22: phase 01 symlinks it; no third floor).
- 2026-09-22 (agent): **Route in on the phone, recorded so the HOME step is understood.** `StartActivity` carries the HOME
  intent filter and no LAUNCHER category, so a freshly sideloaded Tessera is first opened through Settings > Default apps
  (or the Start settings icon's checklist row, which raises `RoleManager.createRequestRoleIntent(ROLE_HOME)`), and Start
  usually draws for the first time with the Home row already green. The Default Home step exists for the other route: the
  chooser's "Just once". P1 records the real route on One UI 8.
- 2026-09-22 (agent): **The microphone is not a step** (Scope). Jeremy's phone report that the microphone prompt never
  appeared was a phase 03 defect fixed at the producer on 2026-09-22 (`qa/phase-03/MICPERM`); the wizard does not stand in
  for that fix. Whether Tess's assistant-role and microphone rows join the walk is Q2.
- 2026-09-22 (agent): **No new exported component, no new permission, no network.** Phase 03 E5's exported allow-list
  (`qa/phase-03/exported-allowlist.txt`) is unchanged; E9 checks it.

## Interview queue (Stage A step 4)
Load-bearing first. Each answer lands in Decisions, dated.

1. ~~Core grants~~ RULED 2026-09-23: B, with a "why" on every step (see Decisions). Original question kept below.
   **What counts as a "core" grant — the rows whose absence makes the wizard appear at all?** (The run walks every missing
   row whichever way this goes; this decides only when a phone sees the wizard.)
   A. Default Home and Notification access — without them Start is not the phone's Start and the tiles are not live; a phone
   missing only Location or Usage access never sees the wizard and fixes those on the checklist. (lean)
   B. Every row with a grant action: Home, Notification access, Photos, Music, Calendar, Location, Usage access, Keyboard
   enabled, Keyboard selected — the wizard appears until each one is granted or the run is finished.
   C. Default Home only — the wizard is really the "become Home" flow, everything else is the checklist.
   D. Other / let me clarify.
2. ~~Tess's grants~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Do Tess's grants join the walk?** Her rows live on her own Settings page (phase 03), and she asks for the microphone
   herself the first time she listens.
   A. No — the Setup checklist's rows only; Tess keeps her own page. (lean, the R10 agent call: one source of truth)
   B. Yes, two of them: a "Tess" step after the checklist rows for the default assistant role and the microphone (walking
   `CortanaChecklist`'s `assistant` and `microphone` rows through `CortanaPermissionActivity`'s existing paths).
   C. Yes, all of them: every row on Tess's checklist becomes a step (contacts, calendar write, texts, calls, location all
   the time, call log, read texts, plus the two above).
   D. Other / let me clarify.
3. ~~Personalisation step~~ RULED 2026-09-23: C plus bundled theme presets (see Decisions). Original question kept below.
   **What does the personalisation step at the end hold?**
   A. The accent colour only, on the R3 A16 grid. (lean, the R10 agent call: "then the accent")
   B. The accent plus Dark / Light.
   C. The accent, Dark / Light, and "Choose a picture" for the Start background.
   D. Other / let me clarify.

4. ~~What a preset sets~~ RULED 2026-09-23: C, applied live (see Decisions). Original question kept below.
   (added 2026-09-23 from Q3's answer) **What does a theme preset set?**
   A. The three personalisation items only: accent, Dark / Light, background picture; each can still be changed on its own
   afterwards, and the page then shows "Custom" (lean)
   B. Those three plus Tess's look matching the theme (her ring and lens colours)
   C. Everything visual: the three, tile transparency, press style, Tess's look and the keyboard's colours
   D. Other / let me clarify
5. ~~Which presets~~ RULED 2026-09-23: B plus the original W10M theme (see Decisions). Original question kept below.
   (added 2026-09-23 from Q3's answer) **Which presets ship?**
   A. Three: Default (W10M as measured: dark, the default accent, no picture), HAL, Soft (lean)
   B. Five: those three plus Lumia (the Lumia phone colours) and Midnight (pure black, easiest on the battery)
   C. Jeremy names the list
   D. Other / let me clarify
6. ~~Picture source~~ RULED 2026-09-23: B (see Decisions). Original question kept below.
   (added 2026-09-23) **Where do the bundled pictures come from** (for HAL, Soft, Lumia and Midnight; the original W10M theme
   uses the stock pictures, see Decisions)?
   A. Drawn by the agent as vector or procedural art inside the APK: ours to ship, sharp at any size, small (lean)
   B. AI-generated images committed to the repo
   C. Jeremy supplies his own pictures
   D. Other / let me clarify

## Build tasks
1. Wizard model: the visibility rule, the step list derived from the checklist rows (`ChecklistRow.permissions` ADD), the
   `setup_wizard` marker store, the re-derive-on-resume rule, diagnostics lines; JVM tests on the pure rules (visibility,
   step derivation, PARTIAL counting as done, blocked detection).
2. Wizard pages inside `StartActivity`: host above the pivot, pager locked while showing, step page (P4), "Not now" /
   "Skip setup" / progress, auto-advance on resume, the blocked-permission branch to the app-info page, the drawn bars and
   the Back / Windows / Search rules, test tags.
3. Personalisation step: pull the accent grid out of `StartThemePage` into one shared composable, reuse it here, write the
   one `ShellSettings` accent; the "Done" button finishes the run.
4. Harness: `provision.sh` grants the core rows and the keyboard; `qa/phase-12/scripts/` with `lib.sh` symlinked; the
   regression run (one row per earlier phase, `regress.sh` pattern) proving no driver meets the wizard; INDEX Change Log
   lines for phase 01 (the `ChecklistRow` ADD, the shared accent grid) when built.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps follow RV13.
"Provisioned" means `qa/phase-03/scripts/provision.sh` on a wiped AVD (tileshell_fhd, 1080×2340 @ 450 dpi, AOSP API 36,
no Google). "Diagnostics" is read with phase 01's command. `pm clear app.tileshell` is the fresh-install state; every row that
needs a grant absent also revokes it explicitly (`cmd notification disallow_listener …`, `appops set … GET_USAGE_STATS
ignore`, `pm revoke app.tileshell <permission>`, `ime disable …`) so no row depends on what `pm clear` resets (checked
at build start and recorded here). Rows that end on a fresh state re-run `provision.sh` before the next row.
**Emulator:**
- E1 Absence, both provisioning routes, so no driver needs a bypass: (a) provisioned AVD, `adb shell am force-stop
  app.tileshell`, `adb shell input keyevent KEYCODE_HOME`: `uiautomator dump` has `start_page` and no `wizard_page`;
  diagnostics carry `[wizard] not shown: core held`. (b) `pm clear app.tileshell`, then every grant made from adb alone
  (roles with `cmd role add-role-holder`, `cmd package set-home-activity`, `allow_listener`, `appops … allow`, `pm grant`
  for READ_MEDIA_IMAGES / READ_MEDIA_AUDIO / READ_CALENDAR / ACCESS_COARSE_LOCATION, `ime enable` + `ime set`), Home: same
  two assertions — a device provisioned by adb that never ran the wizard never sees it, and the `setup_wizard` file does not
  exist (`run-as app.tileshell ls shared_prefs`).
- E2 Appearance and order: `pm clear`, `cmd notification disallow_listener …` (Home role kept, so Start is what draws),
  Home: the dump has `wizard_page`, `wizard_step:notifications` (Home is held, so it is not a step), no `start_page`
  node, no `applist_*` node; `wizard_progress` text reads "Step 1 of N" where N equals the number of rows the checklist
  dump (`checklist:<id>:missing|partial`) lists as not granted with an action, plus one for the accent step; diagnostics
  `[wizard] shown: missing=…` names exactly those ids; a swipe left on the wizard page (`input swipe 950 1200 200 1200
  250`) changes nothing in the dump.
- E3 The real grants through the real dialogs, and the accent: from E2's state, tap `wizard_action` on the Notification
  access step → `dumpsys activity activities` shows `com.android.settings` resumed (the listener detail page); tap its
  toggle by dump bounds and Back → the dump shows the next step and diagnostics `[wizard] step notifications: granted`;
  the Photos step → `com.android.permissioncontroller`'s dialog, tap its allow-all button by dump bounds → next step;
  Usage access → the Settings usage page, toggle, Back → next; Keyboard enabled → `Settings.ACTION_INPUT_METHOD_SETTINGS`
  page, enable, Back; Keyboard selected → the system picker (dump shows it), choose the shell keyboard; the accent step:
  the dump has `wizard_accent` and the `accent:<name>` swatches with Default Blue `selected="true"`; tap `accent:Red`,
  tap `wizard_done`: `start_page` is back, `run-as app.tileshell cat shared_prefs/start_theme.xml` holds
  `<long name="accent" value="4293398819" />` (0xFFE81123), a Start tile pixel read by phase 01's `e13_pixels.py` method is
  (232,17,35) ± 2, and diagnostics end with `[wizard] accent Red` then `[wizard] finished`; Settings > Setup checklist
  shows `checklist:notifications:granted`, `checklist:photos:granted`, `checklist:usage:granted`,
  `checklist:keyboard_enabled:granted`, `checklist:keyboard_selected:granted`. Restore: accent back to Default Blue through
  Settings (dump shows `accent:Default Blue` selected), `provision.sh`.
- E4 Persistence and resume: from E2's state grant the Photos step through the dialog, then `adb shell am force-stop
  app.tileshell`, Home: `wizard_page` again, `wizard_step:notifications` first, `wizard_progress` reads one fewer N, no
  `wizard_step:photos` anywhere in the run (tap `wizard_not_now` through to the end and record every step id seen); the same
  after `adb reboot` + `adb wait-for-device` + boot-completed poll + `wm dismiss-keyguard` in place of the force-stop.
- E5 Skip, and the marker's rules: from E2's state tap `wizard_skip`: `start_page` in the dump, diagnostics `[wizard]
  skip`, `checklist:notifications:missing` on the Settings page, `shared_prefs/setup_wizard.xml` holds `finished` true;
  `am force-stop` + Home → no `wizard_page`, `[wizard] not shown: finished`; `adb install -r <same apk>` + Home → still none;
  `cmd notification allow_listener …` then `disallow_listener` again (a core grant revoked later) + Home → still none;
  `pm clear` + `disallow_listener` + Home → `wizard_page` again. Second half: from a fresh E2 state tap `wizard_not_now` on
  every step and `wizard_done` on the accent step → Start, marker set, next launch no wizard.
- E6 Denied for good: from E2's state, on the Photos step tap `wizard_action` and the dialog's deny button twice across two
  tries (Android 11+: the second denial stops the prompt; `qa/phase-03/scripts/micperm.sh` is the precedent): the button's
  text becomes "Open app info" (`wizard_action` text), diagnostics `[wizard] step photos: blocked (app info)`; tapping it
  resumes `com.android.settings` on the shell's app-info page (`dumpsys activity activities`); Back returns to the same
  step. Restore through `pm clear` + `provision.sh`.
- E7 Revoked mid-run: from E2's state advance past Notification access (grant it) to the Photos step, then from adb
  `cmd notification disallow_listener …` and `am start` the Settings hub and Back (a resume): the dump shows
  `wizard_step:notifications` inserted before the accent step (diagnostics `[wizard] shown` not repeated — same run) and
  `wizard_progress` N grown by one; a grant made from adb while a step shows (`pm grant app.tileshell
  android.permission.READ_MEDIA_IMAGES` on the Photos step) advances that step on the next resume, with `[wizard] step
  photos: granted`.
- E8 Keys and the pivot while the wizard shows: on the first step `input tap` on `nav_back` bounds changes nothing (dump
  identical); on step 2 it returns to step 1; `nav_windows` changes nothing; `input keyevent KEYCODE_HOME` leaves the
  wizard in place; `nav_search` opens Tess (`cortana_session` in the dump) and Back returns to the wizard at the same step;
  `dumpsys window` shows the system status and nav bars not visible (phase 01 E19's form) and the drawn bars measure (28
  epx status, 48-epx nav, X17 slots).
- E9 Regression, the `regress.sh` pattern: on a freshly provisioned AVD run, unchanged, phase 01 E2, phase 02 E1 (hold +
  drag), phase 03 E1 and E5, phase 05 E1 and phase 10 E2; every one passes, no dump any of them saved contains
  `wizard_page` (grep the row directories), and `qa/phase-03/scripts/exported.py` against
  `qa/phase-03/exported-allowlist.txt` reports no new exported component.
- E10 Motion: the step-to-step transition measures as phase 01 X7 (the Start entrance form, alpha complete in 217 ms) from
  a 60-fps screenrecord, RV11's tolerance; judged for feel in H1.

**Phone-only (S25 Ultra):**
- P1 From a fresh sideload (release-signed, `debuggable=false`): the route in is recorded (Settings > Default apps > Home
  app, or Start settings > Setup checklist > Default Home and One UI's default-Home sheet), then Home; each remaining step
  tapped once with the resumed activity recorded from `dumpsys activity activities` (Samsung's notification-access page,
  Usage data access, Samsung's permission controller for Photos / Music / Calendar / Location, Samsung's "Manage keyboards"
  and its picker) and the step advancing on return; the accent chosen shows on a tile.
- P2 The marker survives a reboot, a Device care optimise and an update install (`adb install -r`): no wizard afterwards;
  a Samsung-side revocation (Notification access turned off in One UI Settings) reddens the checklist row and shows no wizard.

**NEEDS-HUMAN** (every row is an "accept this P4 design" row; there is no footage to match):
- H1 The wizard's look and wording: the step page, its button labels, "Not now", "Skip setup", the progress caption, the
  X7 page motion.
- H2 The walking order (the checklist's own order) reads right on the phone.
- H3 The accent step last, and its look with the shared grid.
- H4 The rule that a finished or skipped wizard never returns on that install (a revoked or newly added core grant goes to
  the checklist instead).
- H5 The Samsung sheets inside the flow (P1) do not break the feel.

## Edge cases
- A core grant revoked from adb or from Android's Settings while a later step shows (E7); a grant made from adb while its
  step shows (E7); a grant made while the wizard is not showing (the run starts without that step).
- Process death mid-run (`am force-stop`, E4); reboot mid-run (E4); `adb install -r` mid-run (the marker is not set, so the
  run resumes; assert as E4 with an install in place of the force-stop); a low-memory kill while a permission dialog is up
  (`am kill app.tileshell` with the dialog showing, then Home: the wizard re-derives and the dialog's answer, if any, is
  read from live state).
- Rotation: `settings put system accelerometer_rotation 0`, `settings put system user_rotation 1` while a step shows: the
  activity is portrait-locked and not recreated; the dump shows the same step; restore both.
- The runtime-permission dialog dismissed by Back or by a tap outside: the step stays; "Not now" still advances.
- Two quick taps on `wizard_action` (`input tap` twice, 100 ms apart): one dialog, no crash (`dumpsys activity` shows one
  permission-controller task; the launcher pid unchanged).
- Photos answered with "Select photos" (PARTIAL): the step advances; the checklist row reads Partial with its state line.
- The Keyboard selected step: the picker dismissed without a choice (step stays); a force-stop later deselects the keyboard
  (phase 05 N-01) with the marker set: no wizard, the checklist row is the way back.
- The Default Home step when the shell was opened "Just once" from the chooser (Home missing): it is step 1, the role
  sheet is Android's on the AVD and Samsung's on the phone; choosing another launcher there leaves the step (Start stays
  behind the wizard until Home is pressed).
- The wizard while Tess's session is open over Start (E8); while a Live Tile secondary-pin band is pending (the band waits,
  R5 §1.9, until Start is visible — assert `SecondaryTiles` shows it only after the wizard finishes).
- Light theme and a Start background set before the run: the wizard follows the theme (screencap recorded).
- The accent step with an accent already changed on the same install (a run started after Settings were used): the grid
  shows that accent selected and "Done" leaves it.
- Work / private profile present: no profile grant is a checklist row, so nothing changes (assert the step list against
  E18's profile setup).
- The AVD's keyguard enabled (`locksettings set-disabled false`, phase 01 E20's setup): StartActivity never shows over the
  keyguard, so the wizard never does either; after unlock it shows as usual.
- A later phase ADDs a core row to a finished install: covered by E5's revoked-later case (same rule); when phase 04 is
  built, its own doc re-runs E5 with its row.

## QA evidence
