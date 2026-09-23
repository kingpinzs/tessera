---
phase: 12
slug: setup-wizard
status: DRAFT
depends-on: [01, 03, 05, 10, 13]   # it walks rows those phases put on the Setup checklist and Tess's checklist (phase 03), reuses phase 01's theme settings and phase 03's blocked-permission rule; 13 because a preset writes phase 13's StartTheme.transparencyEffects (T12-3: build order 11 -> 13 -> 12 -> 14)
---

# Phase 12 — First-run setup wizard

## Goal
A phone that is missing a grant meets a W10M-style setup wizard the first time Start draws, instead of a Start whose tiles
are silently empty and a Tess who cannot hear. The wizard is the shell's out-of-box experience (Q2's recorded intent: it
should feel like setting up a new phone, with no root). It walks the Setup checklist's own rows (`onboarding/Checklist.kt`)
in their order, then Tess's nine grant rows (`cortana/CortanaChecklist.kt`) in theirs — the two checklists are the only
sources, never a third list — showing only the rows that are not yet granted, each with a short line saying why; fires each
row's real grant (Android's role sheet or the Settings page that owns it, the runtime-permission dialog, the app's location
page); advances on its own when the grant comes back; and ends on the theme presets page: six presets, each applied to the
whole shell the moment it is tapped, with the individual items below them (Q3–Q6). A phone that already holds every grant
never sees it, which is also why no existing QA driver needs a bypass: the harness provisions the grants, and the wizard has
nothing to do. Every look value is a P4 design: W10M's out-of-box experience was never measured (no R3 / R6 / R7 row), so
the look is judged in NEEDS-HUMAN "accept" rows, not measured — except the "Windows 10 Mobile (original)" preset, whose
values R12 measured. (Re-cut 2026-09-23 from the split-time Goal, which ended "with the accent step", by T12-1 / T12-2.)

## Scope
**In:** the wizard surface, hosted inside `StartActivity` above the Start / app-list pivot the way the slot picker and the pin
band are, and above phase 11's burst layer (T12-8); the visibility rule (any grant row on either checklist MISSING and the run
not finished); steps derived from `ChecklistPage`'s row list and then `CortanaChecklist`'s nine grant rows (same ids — namespaced
`setup:` / `tess:` in tags and lines — titles, state lines and grant actions; no third list, T12-1); a why line per step (Q1;
the Why-lines table in Decisions, T12-4); the theme presets page at the end — six presets applied live, the "Custom" rule, the
individual items below them, the bundled pictures, and the same presets on Start + theme (Q3–Q6; ADDs to phases 01, 03 and 05;
T12-2); "Not now" per step and "Skip setup" for the run; the finished / skipped marker and its store; resume after process death
by re-deriving from live grant state; the blocked-permission branch (Android will no longer ask → the app's own info page, phase
03's 2026-09-22 rule); diagnostics lines; test tags; the harness change that keeps every earlier driver off the wizard
(`qa/phase-03/scripts/provision.sh` grants the Setup rows and Tess's nine; each later phase appends its line, C-4) plus one
regression run; the "wizard step added" template row later phases cite (E14).
**Out (explicitly):** ~~Tess's own checklist rows (`cortana/CortanaChecklist.kt`: assistant role, microphone, contacts, texts,
calls, locations, call log) — they live on her Settings page by phase 03's decision, and the microphone is asked for the
first time she listens (INDEX Change Log 2026-09-22, `CortanaPermissionActivity`); this stands unless interview Q2 says
otherwise.~~ SUPERSEDED 2026-09-23 by Q2 C (T12-1): Tess's nine grant rows are steps; her five observation rows (`exact_alarms`,
`models`, `service`, `speech_process`, `person_triggers`) are not, and her Settings page stays her permanent health surface.
Phase 04's rows (accessibility, overlay, Wireless debugging pairing, helper): phase 04 ADDs them to the checklist and the wizard
walks whatever rows exist — no placeholder step (Rule 16); phase 04 runs E14 for each. A launcher entry or a separate activity
for the wizard (the shell's only launcher icons are Start settings, Music and Weather; Start is reached through the Home role).
Any W10M OOBE page the shell cannot own (region, Wi-Fi, Microsoft account, restore, Cortana sign-in). Reordering or renaming
checklist rows (the checklists are phase 01's and phase 03's; the wizard reads them). Changing what a grant does. A lock-screen
picture (R12 §6 lists img5; the shell draws no lock screen, and phase 07's glance is untouched). Hooks for later phases.

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
  it. SUPERSEDED 2026-09-23 by T12-6 (last Decisions lines; the pager is not composed while the wizard shows): "While the wizard
  shows, the pager does not scroll (`userScrollEnabled = false`, as edit mode does), Start's tiles keep running underneath and are
  not drawn over it (the wizard fills the page between the drawn bars)." The z-order against the burst, the pin band, the picker
  and a pod-bay request: T12-8 below.
- 2026-09-22 (agent): **Bars.** Phase 01's bar rule applies: the wizard is a shell screen, so Samsung's bars are hidden and
  the W10M status bar and Back / Windows / Search nav bar are drawn (`W10mStatusBar`, `W10mNavBar`). Back = the previous step
  (nothing on the first); the Windows key does nothing while the wizard shows (Start is behind it and Home is where you
  are); the Search key keeps the bar component's default (opens Tess; she asks for her own microphone). E8 proves the keys.
- 2026-09-22 (agent): **Visibility rule.** `show ⇔ (some CORE row is MISSING) ∧ ¬finished`. ~~Which rows are core is interview Q1.~~
  Core = the eighteen grant rows of both checklists (Q1 B + Q2 C; T12-1, 2026-09-23). "Finished" is a marker set when the run
  reaches its end or "Skip setup" is tapped (the persistence line). The rule is evaluated when StartActivity is created and on every
  resume while no run is in progress; a PARTIAL row (the Photos row under Android 14+ partial access) is never a reason to show the
  wizard, only a step inside a run.
- 2026-09-22 (agent): **Steps.** SUPERSEDED 2026-09-23 in its row list and order by T12-1 (last Decisions lines: the Setup rows,
  then Tess's nine, then the presets page); the re-derive-on-resume rule below stands. A run's steps are the checklist rows whose
  state is MISSING or PARTIAL and which have a grant action, in `Checklist.kt`'s list order (today: Default Home, Notification
  access, Photos, Music, Calendar, Location, Usage access, Keyboard enabled, Keyboard selected) — then the personalisation step,
  always last. The two probe rows (`samsung_badges`, `legacy_badges`) and the liveness row (`listener`, which goes green by itself
  once notification access is granted) are observations, never steps. A row already GRANTED is not shown. The step list is
  re-derived from live state on every resume of the activity (the dialogs and Settings pages all return through resume): a row that
  turned GRANTED leaves the remaining steps, a row that turned MISSING again is put back in its list position ahead of the accent
  step. If a different walking order is ever wanted, the checklist is reordered; the wizard never keeps an order of its own.
- 2026-09-22 (agent): **`ChecklistRow` gains `permissions: List<String>`** (empty for role / Settings-page rows), mirroring
  `CortanaRow`, so the wizard can tell a runtime-permission step from a Settings-page step without a second table. Both row types
  also gain `partialIsDone` (2026-09-23, T12-1 (c)). Phase 01's `ChecklistPage` is unchanged in behaviour (an ADD to the data class;
  recorded in the INDEX Change Log when built).
- 2026-09-22 (agent): **Step page, P4 design (H1).** One page per step between the drawn bars: a progress caption "Step n
  of N" (12-epx caption class, accent), the row's title in the 24-epx title class, the row's one-line state text
  (`ChecklistRow.detail`) in the 15-epx body class, the row's status glyph and colour as the checklist draws them, one accent
  button whose label is the verb the row implies ("Set as default" for Default Home, "Allow" for a runtime permission, "Open
  settings" for a Settings page, "Turn on" / "Choose" for the two keyboard rows) firing the row's own `action` lambda, and a
  plain-text "Not now" beneath it. Page background = the theme background; page-to-page motion = phase 01's X7 Settings
  transition (`Motion.PAGE_ENTER_MS`, the Start entrance form). The first step's page also carries "Skip setup" at the top
  right of the content area; later steps do not (a skip is a decision made at the start, W10M's OOBE offered "skip" per
  optional page and the checklist covers the rest). Wording is judged in H1; nothing here is measured. Added 2026-09-23 (T12-4):
  under the title, the row's why line from the Why-lines table below, on the node tagged `wizard_why`.
- 2026-09-22 (agent): **Auto-advance.** When the activity resumes with the current step's row GRANTED, the wizard advances at once
  with no extra tap (P2 seamless). PARTIAL counts as this step done (the user chose "Select photos"); the checklist row stays
  Partial and says so — for Photos only since 2026-09-23 (T12-1 (c), `partialIsDone`). Returned still MISSING: the page stays, "Not
  now" is the way on. Runtime-permission step returned MISSING with `shouldShowRequestPermissionRationale` false for a requested
  permission (Android will no longer ask — denied twice or "Don't ask again"): the button relabels to "Open app info" and starts
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the shell, the rule `CortanaPermissionActivity` and phase 10's Music row
  already follow (INDEX Change Log 2026-09-22 "THE MICROPHONE PROMPT NOW APPEARS"). Denied for good is never silent.
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
- 2026-09-22 (agent): SUPERSEDED 2026-09-23 by Q3–Q6 and T12-2 (the presets page; the shared accent grid survives as one of
  its items): **Personalisation step.** Its contents are interview Q3; the agent call is the accent only. The accent
  grid is the same composable Settings > Start + theme draws (R3 A16's 6 × 8 grid, the `accent:<name>` swatches), pulled
  out of `StartThemePage` into one shared composable so both screens draw one grid and write one setting —
  `ShellSettings.update { it.copy(accent = …) }`, the X26 setting, never a second store. The step shows the current accent
  selected (the out-of-box Default Blue on a fresh install), a "Done" button, and is shown once per run at the end. It is
  part of the run, so it appears only when the run itself shows: a provisioned phone never sees it either.
- 2026-09-22 (agent): **Diagnostics** (R10 testability 25): `[wizard] shown: missing=<row ids>` when a run starts;
  `[wizard] not shown: core held` and `[wizard] not shown: finished` when Start draws without it; `[wizard] step <id>:
  granted | partial | not now | blocked (app info)`; `[wizard] skip`; ~~`[wizard] accent <name>`~~; `[wizard] finished`. Read
  with phase 01's diagnostics command (`adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener`,
  `diag wizard` in the driver floor). Re-cut 2026-09-23: row ids are namespaced `setup:<id>` / `tess:<id>` in every line
  (T12-1); `[wizard] accent <name>` is replaced by `[wizard] preset <name>` and `[theme] preset <name> applied` (T12-2); E10
  reads `[motion] wizard_page t0=<uptime> settle=<ms>` (C-5).
- 2026-09-22 (agent): **Test tags:** `wizard_page` (root), `wizard_step:<row id>`, `wizard_progress`, `wizard_title`, `wizard_body`,
  `wizard_action`, `wizard_not_now`, `wizard_skip`, ~~`wizard_accent` (the personalisation step's root, with the shared grid's
  `accent:<name>` swatches inside it)~~, `wizard_done`. Re-cut 2026-09-23: `wizard_step:setup:<id>` / `wizard_step:tess:<id>`
  (T12-1); `wizard_why` (T12-4); `wizard_presets` replaces `wizard_accent` as the last page's root, with `preset:<name>`,
  `preset:Custom` and the items below them — the shared grid's `accent:<name>` swatches among them (T12-2). Every text a row reads
  sits on the node that carries the tag (the MUSIC8 lesson, testability 24).
- 2026-09-22 (agent): **Harness** (R10 testability 3, 12, 24). `qa/phase-03/scripts/provision.sh` today grants the roles and
  installs with `-g` but does not grant notification access or Usage access (phase 01's own rows grant those inside
  `final_rows.sh`, `edge_liveness.sh`, `e20_part3.sh`); after this phase it also runs `cmd notification allow_listener
  app.tileshell/app.tileshell.feeds.TileNotificationListener`, `appops set app.tileshell GET_USAGE_STATS allow`, and
  `ime enable` / `ime set app.tileshell/.ime.KeyboardService` (phase 05's rows), so a provisioned AVD holds every row the
  wizard walks and E1 proves the wizard absent on it. E9 then re-runs one row per earlier phase (the `qa/phase-02/scripts/
  regress.sh` pattern) with no driver change. New drivers live under `qa/phase-12/scripts/` with `lib.sh` symlinked from
  phase 03 (INDEX 2026-09-22: phase 01 symlinks it; no third floor). Extended 2026-09-23 (T12-1, C-4): Tess's nine are already
  granted by `provision.sh` — `adb install -r -g` grants RECORD_AUDIO, READ_CONTACTS, READ / WRITE_CALENDAR, SEND_SMS,
  CALL_PHONE, ACCESS_FINE / BACKGROUND_LOCATION, READ_CALL_LOG and READ_SMS, and the ASSISTANT role line holds `tess:assistant`
  (`qa/phase-03/scripts/provision.sh:35,50-51`) — so the provisioned AVD holds all eighteen rows, which E1(a) proves; each later
  phase appends its own line (the C-4 line below).
- 2026-09-22 (agent): **Route in on the phone, recorded so the HOME step is understood.** `StartActivity` carries the HOME
  intent filter and no LAUNCHER category, so a freshly sideloaded Tessera is first opened through Settings > Default apps
  (or the Start settings icon's checklist row, which raises `RoleManager.createRequestRoleIntent(ROLE_HOME)`), and Start
  usually draws for the first time with the Home row already green. The Default Home step exists for the other route: the
  chooser's "Just once". P1 records the real route on One UI 8.
- 2026-09-22 (agent): SUPERSEDED 2026-09-23 by Q2 C (T12-1: `tess:microphone` is a step): **The microphone is not a step** (Scope).
  Jeremy's phone report that the microphone prompt never appeared was a phase 03 defect fixed at the producer on 2026-09-22
  (`qa/phase-03/MICPERM`); the wizard does not stand in for that fix. Whether Tess's assistant-role and microphone rows join the
  walk is Q2.
- 2026-09-22 (agent): **No new exported component, no new permission, no network.** Phase 03 E5's exported allow-list
  (`qa/phase-03/exported-allowlist.txt`) is unchanged; E9 checks it.
- 2026-09-23 (review triage T12-4): **Why lines** (Q1's "every step says why"; agent-written P4 copy, judged in H1). Each step
  page shows its row's line under the title on the node tagged `wizard_why`; E2, E3 and E14 compare the text with this table
  exactly. The Notification access line is Q1's own example. A later phase's step adds its row here (C-4 line below).

  | Step id | Why line |
  |---|---|
  | `setup:home` | Start opens when you press Home. Without it Home goes to another launcher. |
  | `setup:notifications` | Live tiles and unread counts come from your notifications. Without it the tiles stay still. |
  | `setup:photos` | The Photos tile shows your pictures. Without it the tile stays empty. |
  | `setup:music` | Music plays the songs on this phone. Without it Music finds nothing to play. |
  | `setup:calendar` | The Calendar tile shows what's next. Without it the tile stays empty. |
  | `setup:location` | Weather shows the weather where you are. Without it Weather cannot tell where that is. |
  | `setup:usage` | Back on Start returns to the app you were using. Without it Back stays on Start. |
  | `setup:keyboard_enabled` | This turns on the Windows-style keyboard. Without it the keyboard cannot be chosen. |
  | `setup:keyboard_selected` | This makes it the keyboard wherever you type. Without it your old keyboard stays. |
  | `tess:assistant` | The side key and the assist gesture open Tess. Without it they open another assistant. |
  | `tess:microphone` | Tess hears what you ask. Without it you can only type to her. |
  | `tess:contacts` | Tess calls and texts people by name. Without it she cannot find them. |
  | `tess:calendar` | Tess reads your calendar and adds to it. Without it she cannot see or add events. |
  | `tess:sms_send` | Tess sends a text after reading it back to you. Without it she cannot send one. |
  | `tess:call_phone` | Tess places a call after you confirm. Without it she cannot call. |
  | `tess:background_location` | Place reminders go off when you arrive. Without "all the time" they never fire. |
  | `tess:call_log` | Person reminders go off after you talk to that person. Without it they cannot tell you did. |
  | `tess:sms_read` | Person reminders go off after you text that person. Without it they cannot tell you did. |

- 2026-09-23 (review triage T12-8): **Z-order in `StartActivity`'s root.** The wizard sits above the pager, phase 11's burst
  layer, the secondary-pin band and the slot picker. A burst cannot coexist with it (edit mode is under the wizard and cannot be
  entered while it shows); a pending pin band waits until the wizard finishes (edge case); a pending phase 14 pod-bay request waits
  the same way (phase 14 T14-7).
- 2026-09-23 (review triage T12-5, C-4): **Later phases' grants.** Every later phase that adds a grant — phase 04's accessibility,
  overlay and helper pairing, phase 06's roles, and the inbox apps' (15: the full-screen-intent appop; 16: WRITE_CONTACTS; 17:
  CAMERA and READ_MEDIA_VIDEO; 18: all-files access; 19: WRITE_SETTINGS and Do-not-disturb access) — (a) appends its line to
  `provision.sh`, (b) adds its checklist row, its step and its why line to the table above, (c) runs E14 ("wizard step added")
  for its step and re-runs E1 on its build, and (d) writes every `pm clear` row as "`pm clear` → `provision.sh` → Home". The
  finished-install rule stands (Persistence; H4): a phone that finished or skipped the wizard is not re-summoned by a later phase's
  new row — that row goes red on its checklist instead. Each of phases 15–19 restates that rule in one sentence.
- 2026-09-23 (review triage T12-9, R12): **The original preset's values come from R12** (`r12-w10m-stock-theme.md`, landed
  2026-09-23): Dark, accent Cobalt #3E65FF (outside the 48 swatches), full-screen picture img0 with accent tiles at α 0.40, press
  style none, acrylic off. The three choices R12 raised are this doc's queue Q7–Q9, UNANSWERED at this writing, so the preset table
  and E13 are written conditionally on them; the doc cannot go FINAL before they are answered and the four pictures are in
  art/themes/.
- 2026-09-23 (agent, review triage T12-1): **Q2 applied — which rows, the tags, location, the order.** (a) Tess's steps are her
  nine rows with a grant action — `assistant`, `microphone`, `contacts`, `calendar`, `sms_send`, `call_phone`,
  `background_location`, `call_log`, `sms_read` (`cortana/CortanaChecklist.kt:37-122`); `exact_alarms` (granted at install,
  F1-m7), `models`, `service`, `speech_process` and `person_triggers` are observations and never steps, as the Steps line says of
  `samsung_badges` / `listener`. Each is walked through `CortanaPermissionActivity`'s existing paths: `tess:assistant` opens the
  Settings page that owns the choice (`Settings.ACTION_VOICE_INPUT_SETTINGS` first; ROLE_ASSISTANT is not requestable, so there
  is no role sheet — `cortana/CortanaPermissionActivity.kt:74-92`), button "Open settings"; the runtime rows raise Android's
  dialog, button "Allow"; `tess:background_location`, button "Allow all the time". (b) Tags and lines are namespaced —
  `wizard_step:setup:<id>` / `wizard_step:tess:<id>`, `[wizard] step setup:<id>: …` — because both checklists have a `calendar`
  row (`onboarding/Checklist.kt:105`, `CortanaChecklist.kt:44-53`). (c) No third step kind: Tess's `background_location` row
  already carries FINE + BACKGROUND (`CortanaChecklist.kt:62-71`) and `CortanaPermissionActivity.kt:57-69` already asks FINE
  first and then BACKGROUND alone, which Android answers with the app's location page, as it requires on API 30+. What changes is
  the PARTIAL rule: "PARTIAL counts as this step done" is Photos-only — a `partialIsDone` flag on the row, true for `setup:photos`
  and false for every other row that can be PARTIAL (`tess:background_location`, whose PARTIAL means FINE held and "all the time"
  still owed; `tess:calendar`, read held and write owed); such a step stays until GRANTED or "Not now". (d) Order: the Setup rows
  in `Checklist.kt` order, then Tess's in `CortanaChecklist` order, then the presets page; "Skip setup" covers both lists; a row
  that turns MISSING again mid-run rejoins the steps not yet shown, ahead of the presets page. Core = all eighteen grant rows
  (Q1 B + Q2 C); a PARTIAL row still never summons the wizard. Reason: Q2 C lists the rows but not the observation split, the id
  collision, the location mechanics or the merged order, and each is settled by the existing code rather than by a new design
- 2026-09-23 (agent, review triage T12-2): **the preset model** (Q3–Q6 designed below the rulings; every value is an H6 accept
  except the R12-marked cells). Keys in `start_theme.xml` (`ShellSettings`): `accent`, `theme`, `background`, `transparency` and
  `press` exist (`prefs/ShellSettings.kt:72-82`); `transparency_effects` is phase 13's; this phase ADDs `theme_preset`,
  `tess_lens`, `tess_ring` and `keyboard_palette`. A tile's alpha over a picture is 1 − 0.8 · `transparency`
  (`start/StartPage.kt:413`).

  | Preset | `accent` | `theme` | `background` | `transparency` (tile α over the picture) | `press` | `tess_lens` / `tess_ring` | `keyboard_palette` | `transparency_effects` |
  |---|---|---|---|---|---|---|---|---|
  | Default | Default Blue #0078D7 (X26) | DARK | none | 0.5 (as built) | NONE | HAL lens (`Brand.LENS_*`) / the accent | DARK (the built keyboard, R6) | true |
  | Windows 10 Mobile (original) | Cobalt #3E65FF (R12 §1, HIGH), by Q7: A (lean) a 49th named swatch `Cobalt`; B set but not in the picker; C Purple Shadow Dark #6B69D6 instead (nearest of the 48 by RGB distance, 61.0) | DARK (R12 §2) | img0 by Q8: A (lean) the Hero, `img0_w10m_1607-1709.jpg`; B the light streaks, `img0_w10m_1507-1511.jpg`; C both, as two variants (R12 §4.2) | 0.75 → α 0.40 (R12 §5.1) | NONE (R12 §5.2) | by Q9: A (lean) the lens on Cobalt's hue line / Cobalt; B the pre-HAL drawing — the lens at reveal = 1, the accent ring, already built (`cortana/ui/Lens.kt:21-24`); C untouched (Default's) | DARK, the cursor dot in the accent (R12 §5.2) | false (R12 §5.2, approximation) |
  | HAL | `Brand.LENS_IRIS` #D81810 — off the 48 like Cobalt, so it follows Q7's answer the same way (C: Red #E81123, RGB distance 25.8) | DARK | `preset_hal` | 0.35 (α 0.72) | NONE | HAL lens / the accent | DARK | true |
  | Soft | the A16 swatch nearest the Soft picture's dominant colour, picked when the picture is in art/themes/ | LIGHT | `preset_soft` | 0.6 (α 0.52) | P4_PRESS | the lens on the accent's hue line / the accent | LIGHT (new, P4: panel `Palette.lightChromeLow` #F2F2F2, keys `lightChromeMedium` #E6E6E6, labels #000000 — R1 §6.2's light chrome, `ui/tokens/Palette.kt:27-32`) | true |
  | Lumia | Seafoam #00B7C3 — the A16 swatch nearest WP8.1 Cyan #1BA1E2 (R12 §1) by RGB distance, 46.6; no swatch is named "Cyan" (`ui/tokens/Palette.kt:11-18`) | DARK | `preset_lumia` | 0.5 (α 0.6) | WP8_TILT | the lens on the accent's hue line / the accent | DARK | true |
  | Midnight | the A16 swatch nearest the Midnight picture's dominant colour, picked as Soft's is (T12-2 named none) | DARK | `preset_midnight` | 0.0 (opaque tiles) | NONE | HAL lens dimmed (each tone × 0.5 on its hue line) / the accent | DARK | false |

  **Custom rule:** `theme_preset` holds the preset's id (`default`, `w10m`, `hal`, `soft`, `lumia`, `midnight`) and becomes
  `custom` on any single item change (phase 13's Transparency effects switch included); re-tapping a preset restores every item it
  sets. **Live apply:** tapping a preset IS the change — the whole shell restyles at once (Jeremy: "auto changes the phone so they
  can see"); "Not now", Back or "Skip setup" after a tap leave it applied. A preset replaces a picture the user chose; the user's
  picture is NOT kept anywhere (re-chosen from Start + theme). **The items below the presets** (Q4: each still changeable): Start +
  theme's existing rows (the accent grid — pulled out of `StartThemePage` into one shared composable, as the 2026-09-22 line
  planned — Dark / Light, choose / remove picture, tile transparency, press style), phase 13's Transparency effects switch, and two
  new rows, "Tess's look" (lens: HAL red or the accent) and "Keyboard" (Dark / Light) (P4, H6). **Pictures:**
  `art/themes/<name>.png` (Jeremy's, Q6) → cropped and scaled on the host to 1872 × 4056 (1440 × 3120 × 1.3 parallax headroom,
  portrait) → bundled WebP in the branding module (A10) as `preset_<name>`; `background` holds its
  `android.resource://app.tileshell/drawable/preset_<name>` URI; the pictures add ≤ 8 MB to the APK in total and none is ≥ 2.5 MB
  (E12). **ADDs to FINAL parts** (INDEX Change Log lines when built): phase 01's Start + theme gains the presets row and
  `theme_preset`; phase 03's persona reads `tess_lens` / `tess_ring` from `ShellSettings`; phase 05's keyboard reads
  `keyboard_palette` through the existing `KeyboardConfigProvider` (`AndroidManifest.xml:261`), and phase 05 E3's ± 32-level
  colour check is stated to run on the Default preset. **Tags:** `preset:<name>` on the wizard's presets page (`preset:Custom` for
  the Custom entry), `theme_preset:<name>` on Start + theme's copy (one composable, two tag prefixes); page roots `wizard_presets`
  and `theme_presets`. **Diagnostics:** `[wizard] preset <name>` (a tap on the wizard's page), `[theme] preset <name> applied`
  (every apply, either surface). Reason: Q3–Q6 rule what a preset sets, that it applies live and which six ship; the model above is
  what makes those rulings buildable and each value checkable
- 2026-09-23 (agent, review triage T12-3): build order **11 → 13 → 12 → 14**, and this phase's depends-on becomes `[01, 03, 05,
  10, 13]`. Reason: a preset writes `StartTheme.transparencyEffects`, a field only phase 13 adds (`prefs/ShellSettings.kt:17-24`
  has none); phase 13 does not depend on 12; and this doc builds its presets "in their final form" (Rule 16). INDEX's phase notes
  for 12 / 13 and PLAN's 2026-09-22 order line take the swap when the lead applies the triage there
- 2026-09-23 (agent, review triage T12-6): while the wizard shows, the pager is NOT composed — the feeds keep running, which is all
  "tiles keep running underneath" means, since there is nothing to draw for — and Start composes when the wizard finishes, under
  the X7 page motion. E2's "no `start_page` node" then holds as written. Reason: a covered-but-composed Start would be in the dump,
  and a z-order assertion by bounds is weaker than absence
- 2026-09-23 (agent, review triage T12-7): "Lumia" and "Windows 10 Mobile (original)" are `Brand` strings (`brand/Brand.kt`), and
  the stock pictures live in the A10 branding module like the Segoe substitute. If a build has no shippable stock picture (R12's
  files stay local and gitignored until Jeremy rules on committing them — INDEX R12 row), the original preset ships with R12's
  accent, mode, transparency, press style and acrylic values and NO picture — still a preset, stated, not a placeholder (no v1 →
  v2) — and logs `[theme] preset Windows 10 Mobile (original) applied: no picture`. Reason: A10 keeps every Microsoft name and
  asset swappable, and a preset without its picture is still the measured look minus one item

- 2026-09-23 (agent, lead, resolving the writer's flag on force-stop): QA provisioning FINISHES the wizard once, through the
  wizard's own finish path (provision.sh taps through, or writes the same done marker the finish writes), exactly like a user who
  completed setup. After that, a missing row goes to the checklist per this doc's finish rule — so am force-stop app.tileshell,
  which makes Android deselect the keyboard (qa/phase-05/README.md), sends "Keyboard selected" to the checklist instead of
  summoning the wizard in every driver that restarts the shell (phase 12 E1(a), layout_restore, phase 14's force-stop + Home).
  Rows that test the wizard itself clear the marker first and restore it after (RV12); E1(a) runs on a fresh pm clear.

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

7. (added 2026-09-23 from R12) **The original W10M accent.** W10M phones shipped with "Cobalt" #3E65FF, a maker's colour
   that is NOT one of the 48 accent swatches the shell's picker offers (R12; R3 A16).
   A. Add Cobalt as a 49th swatch everywhere accents are picked, as W10M phones effectively had (lean)
   B. The original preset uses Cobalt, but the picker keeps its 48 (so choosing another accent loses Cobalt for good)
   C. Use the nearest of the 48 instead
   D. Other / let me clarify
8. (added 2026-09-23 from R12) **Which original Start picture.** Phones on the first W10M build shipped a light-streak "img0";
   later builds (Anniversary / Creators Update) replaced it with the rotated "Hero" image. Both are bundled.
   A. The later Hero image, matching the final release this build follows (lean)
   B. The first light-streak image
   C. Both, as two variants of the preset
   D. Other / let me clarify
9. (added 2026-09-23 from R12) **Tess in the original preset.** W10M's Cortana was a flat accent-coloured disc; Tess's
   default look is the HAL-style lens.
   A. The original preset keeps Tess's lens, tinted to Cobalt (lean)
   B. The original preset switches Tess to a flat Cortana-style disc (a new look, in the branding module)
   C. Tess's look is untouched by this preset
   D. Other / let me clarify

## Build tasks
(Re-cut 2026-09-23 by T12-1 – T12-8 and C-4; the split-time tasks walked the Setup rows only and ended on the accent grid.)
1. Wizard model: the visibility rule over both checklists (core = the eighteen grant rows); the step list — the Setup rows in
   `Checklist.kt` order, then Tess's nine in `CortanaChecklist` order, then the presets page — with namespaced ids (T12-1);
   `ChecklistRow.permissions` and `partialIsDone` on both row types (ADDs to phase 01's and phase 03's data classes); the
   `setup_wizard` marker store; the re-derive-on-resume rule; diagnostics lines; JVM tests on the pure rules (visibility, step
   derivation and order, namespacing, `partialIsDone` per row, blocked detection).
2. Wizard pages inside `StartActivity`: the host above the pager, the burst layer, the pin band and the picker (T12-8), with the
   pager not composed while it shows (T12-6); the step page with its `wizard_why` line from the Why-lines table (T12-4); "Not
   now" / "Skip setup" / progress; auto-advance on resume; the blocked-permission branch to the app-info page; Tess's steps through
   `CortanaPermissionActivity`'s existing paths; the drawn bars and the Back / Windows / Search rules; the `[motion] wizard_page`
   line (C-5); test tags.
3. Presets (T12-2): one shared presets composable drawn by the wizard's last page and by Start + theme (the ADD to phase 01's
   page), the item rows below it (the accent grid pulled out of `StartThemePage`, the other existing rows, phase 13's switch,
   "Tess's look", "Keyboard"), `theme_preset` and the Custom rule, live apply, the new keys and their readers — phase 03's persona
   (`tess_lens`, `tess_ring`) and phase 05's keyboard (`keyboard_palette` through `KeyboardConfigProvider`) — the `Brand` names
   (T12-7), diagnostics; INDEX Change Log lines for phases 01, 03 and 05 when built.
4. Harness (C-4): `provision.sh` grants the Setup rows (`cmd notification allow_listener …`, `appops set app.tileshell
   GET_USAGE_STATS allow`, `ime enable` / `ime set`) on top of what it already grants — Tess's nine through `adb install -r -g`
   and the ASSISTANT role line — and each later phase appends its line; `qa/phase-12/scripts/` with `lib.sh` symlinked; the
   regression run (one row per earlier phase, `regress.sh` pattern) proving no driver meets the wizard; E14's template proven on
   `setup:usage`; INDEX Change Log lines for phase 01 (the `ChecklistRow` ADDs, the shared accent grid) when built.
5. Pictures (T12-2, T12-7): a host script under `tools/` crops and scales `art/themes/<name>.png` — and, per Q8, R12's local img0
   file — to 1872 × 4056 WebP in the branding module; the no-picture fallback and its line; E12's size bounds.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 on the shell's own clock
(below); dumps follow RV13. "Provisioned" means `qa/phase-03/scripts/provision.sh` (with this phase's lines, Build task 4) on a
wiped AVD (tileshell_fhd, 1080×2340 @ 450 dpi, AOSP API 36, no Google). "Diagnostics" is read with phase 01's command.
`pm clear app.tileshell` is the fresh-install state; every row that needs a grant absent also revokes it explicitly (`cmd
notification disallow_listener …`, `appops set … GET_USAGE_STATS ignore`, `pm revoke app.tileshell <permission>`, `cmd role
remove-role-holder android.app.role.ASSISTANT app.tileshell`, `ime disable …`) so no row depends on what `pm clear` resets
(checked at build start and recorded here). Every row that ends on a fresh state ends "`pm clear` → `provision.sh` → Home"
before the next row (C-4 (d)).
**The E2 state** (E2–E7 start from it): `pm clear`, then `cmd notification disallow_listener
app.tileshell/app.tileshell.feeds.TileNotificationListener`, `appops set app.tileshell GET_USAGE_STATS ignore`, `ime disable
app.tileshell/.ime.KeyboardService`, `cmd role remove-role-holder android.app.role.ASSISTANT app.tileshell`, and `pm revoke
app.tileshell android.permission.<P>` for each of READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED, READ_MEDIA_AUDIO,
READ_CALENDAR, WRITE_CALENDAR, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, RECORD_AUDIO,
READ_CONTACTS, SEND_SMS, CALL_PHONE, READ_CALL_LOG, READ_SMS; the Home role is kept, so Start is what draws.
**Seeding (C-3):** rows that read Start's grid (E3, E11, E13) first run `layout_restore qa/phase-02/baseline_layout.json`
(`qa/phase-02/scripts/layout.sh`) — this phase adds no `addedOnce` marker and pins no fixture, so phase 02's file, with its
markers and hand-set sizes, is complete for this build — and assert zero `assignSlotOnce … -> assigned` lines after it.
**Motion clock (C-5):** every motion the shell animates logs its own clock from `withFrameNanos` (`[motion] <name>
t0=<uptime> settle=<ms>`) and the row asserts the logged numbers against RV11's tolerance; a screenrecord corroborates under
phase 05's frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion) and is never the primary clock.
**Emulator:**
- E1 Absence, both provisioning routes, so no driver needs a bypass: (a) provisioned AVD, `adb shell am force-stop
  app.tileshell`, `adb shell input keyevent KEYCODE_HOME`: `uiautomator dump` has `start_page` and no `wizard_page`;
  diagnostics carry `[wizard] not shown: core held`. (b) `pm clear app.tileshell`, then every grant made from adb alone — roles
  with `cmd role add-role-holder android.app.role.HOME app.tileshell` and `cmd role add-role-holder android.app.role.ASSISTANT
  app.tileshell`, `cmd package set-home-activity`, `allow_listener`, `appops set app.tileshell GET_USAGE_STATS allow`, `pm grant`
  for READ_MEDIA_IMAGES / READ_MEDIA_AUDIO / READ_CALENDAR / ACCESS_COARSE_LOCATION (Setup) and RECORD_AUDIO, READ_CONTACTS,
  WRITE_CALENDAR, SEND_SMS, READ_SMS, CALL_PHONE, READ_CALL_LOG, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION (Tess, T12-1),
  `ime enable` + `ime set` — then Home: the same two assertions — a device provisioned by adb that never ran the wizard never
  sees it — and the `setup_wizard` file does not exist (`run-as app.tileshell ls shared_prefs`). Every later phase that adds a
  grant re-runs E1 on its build with its grant in both routes (C-4 (c)).
- E2 Appearance, order and why, against a hand-written list (T12-1, T12-4): the E2 state, Home: the dump has `wizard_page`,
  `wizard_step:setup:notifications` (Home is held, so it is not a step), no `start_page` node (the pager is not composed, T12-6),
  no `applist_*` node; `wizard_progress` reads "Step 1 of 18"; diagnostics `[wizard] shown: missing=…` names exactly these
  seventeen ids — written here, never read from either checklist's dump, which is the wizard's own source: `setup:notifications`,
  `setup:photos`, `setup:music`, `setup:calendar`, `setup:location`, `setup:usage`, `setup:keyboard_enabled`,
  `setup:keyboard_selected`, `tess:assistant`, `tess:microphone`, `tess:contacts`, `tess:calendar`, `tess:sms_send`,
  `tess:call_phone`, `tess:background_location`, `tess:call_log`, `tess:sms_read` (17 + the presets page = 18). Tapping
  `wizard_not_now` through the run records the step ids in exactly that ORDER and then `wizard_presets`, and on every step page
  the `wizard_why` text equals the Why-lines table's line for that id; a swipe left on the wizard page (`input swipe 950 1200 200
  1200 250`) changes nothing in the dump. Then `pm clear` → `provision.sh` → Home.
- E3 The real grants through the real pages and dialogs, then a preset: the E2 state after `layout_restore
  qa/phase-02/baseline_layout.json`; walk every step with `wizard_action`. Setup: Notification access → `dumpsys activity
  activities` shows `com.android.settings` resumed (the listener detail page); tap its toggle by dump bounds and Back → the next
  step and `[wizard] step setup:notifications: granted`; Photos, Music, Calendar, Location → `com.android.permissioncontroller`'s
  dialog each, allowed by dump bounds; Usage access → the Settings usage page, toggle, Back; Keyboard enabled →
  `Settings.ACTION_INPUT_METHOD_SETTINGS`, enable, Back; Keyboard selected → the system picker, choose the shell keyboard. Tess
  (T12-1): `tess:assistant` → `com.android.settings` resumed on the assist & voice input page (`ACTION_VOICE_INPUT_SETTINGS`;
  there is no role sheet), choose the shell as the digital assistant by dump bounds, Back → `cmd role get-role-holders
  android.app.role.ASSISTANT` prints `app.tileshell` and `[wizard] step tess:assistant: granted`; microphone, contacts, calendar,
  texts, calls, call log, read texts → each its runtime dialog, allowed by bounds — or no dialog where Android grants from a
  permission group already held (READ_SMS after SEND_SMS; `tess:calendar` asks only for WRITE_CALENDAR, READ being held from
  `setup:calendar`) — the row records which, and every step advances with its `granted` line; `tess:background_location` →
  `com.android.permissioncontroller` resumed on the app's location page, "Allow all the time" by bounds, Back → `dumpsys package
  app.tileshell` shows `android.permission.ACCESS_BACKGROUND_LOCATION: granted=true` and `appops get app.tileshell FINE_LOCATION`
  reads `allow` (not `foreground`), then `[wizard] step tess:background_location: granted`. Every step page's `wizard_why` equals
  its table line. The presets page: `wizard_presets` holds `preset:Default`, `preset:Windows 10 Mobile (original)`,
  `preset:HAL`, `preset:Soft`, `preset:Lumia`, `preset:Midnight` in that order with `preset:Default` `selected="true"`; tap
  `preset:HAL`, then `wizard_done`: `start_page` is back; `run-as app.tileshell cat shared_prefs/start_theme.xml` holds
  `theme_preset` = `hal` and `accent` = 4292352016 (0xFFD81810; the Q7-C form, Red 4293398819, if Q7 is answered C); phase 01's
  `e13_pixels.py` over a dump and screencap of Start reads the `slot:PEOPLE` tile band T and the gutter G beside it, and T =
  0.72 · accent + 0.28 · G ± 4 (α = 1 − 0.8 · 0.35; R12 §0's two-sample method, both samples from one capture); diagnostics end
  with `[wizard] preset HAL`, `[theme] preset HAL applied`, `[wizard] finished`; Settings > Setup checklist shows
  `checklist:<id>:granted` for all nine Setup rows and Tess's Settings page shows `cortana_check:<id>:granted` for her nine.
  Restore: Settings > Start + theme, tap `theme_preset:Default` (selected; `theme_preset` = `default`), then `pm clear` →
  `provision.sh` → Home.
- E4 Persistence and resume: from the E2 state grant the Photos step through the dialog, then `adb shell am force-stop
  app.tileshell`, Home: `wizard_page` again, `wizard_step:setup:notifications` first, `wizard_progress` reads one fewer N, no
  `wizard_step:setup:photos` anywhere in the run (tap `wizard_not_now` through to the end and record every step id seen); the
  same after `adb reboot` + `adb wait-for-device` + boot-completed poll + `wm dismiss-keyguard` in place of the force-stop.
- E5 Skip, and the marker's rules: from the E2 state tap `wizard_skip`: `start_page` in the dump, diagnostics `[wizard] skip`,
  `checklist:notifications:missing` on the Settings page and `cortana_check:microphone:missing` on Tess's (Skip covers both
  lists), `shared_prefs/setup_wizard.xml` holds `finished` true; `am force-stop` + Home → no `wizard_page`, `[wizard] not
  shown: finished`; `adb install -r <same apk>` + Home → still none; `cmd notification allow_listener …` then `disallow_listener`
  again (a core grant revoked later) + Home → still none; `pm clear` + `disallow_listener` + Home → `wizard_page` again. Second
  half: from a fresh E2 state tap `wizard_not_now` on every step and `wizard_done` on the presets page → Start, marker set, next
  launch no wizard. Then `pm clear` → `provision.sh` → Home.
- E6 Denied for good: from the E2 state, on the Photos step tap `wizard_action` and the dialog's deny button twice across two
  tries (Android 11+: the second denial stops the prompt; `qa/phase-03/scripts/micperm.sh` is the precedent): the button's text
  becomes "Open app info" (`wizard_action` text), diagnostics `[wizard] step setup:photos: blocked (app info)`; tapping it resumes
  `com.android.settings` on the shell's app-info page (`dumpsys activity activities`); Back returns to the same step. The same on
  `tess:microphone` (`[wizard] step tess:microphone: blocked (app info)`). Restore through `pm clear` → `provision.sh` → Home.
- E7 Revoked mid-run: from the E2 state advance past Notification access (grant it) to the Photos step, then from adb `cmd
  notification disallow_listener …` and `am start` the Settings hub and Back (a resume): `wizard_progress` N has grown by one and
  diagnostics `[wizard] shown` is not repeated (same run); walking on with `wizard_not_now`, `setup:notifications` is seen again
  exactly once, before `wizard_presets` (it rejoins the steps not yet shown, T12-1 (d)); a grant made from adb while a step shows
  (`pm grant app.tileshell android.permission.READ_MEDIA_IMAGES` on the Photos step) advances that step on the next resume, with
  `[wizard] step setup:photos: granted`. Then `pm clear` → `provision.sh` → Home.
- E8 Keys and the pivot while the wizard shows: on the first step `input tap` on `nav_back` bounds changes nothing (dump
  identical); on step 2 it returns to step 1; `nav_windows` changes nothing; `input keyevent KEYCODE_HOME` leaves the wizard in
  place; `nav_search` opens Tess (`cortana_session` in the dump) and Back returns to the wizard at the same step; `dumpsys window`
  shows the system status and nav bars not visible (phase 01 E19's form) and the drawn bars measure (28 epx status, 48-epx nav,
  X17 slots).
- E9 Regression, the `regress.sh` pattern: on a freshly provisioned AVD run, unchanged, phase 01 E2, phase 02 E1 (hold + drag),
  phase 03 E1 and E5, phase 05 E1 and phase 10 E2; every one passes, no dump any of them saved contains `wizard_page` (grep the
  row directories), the ring holds zero `assignSlotOnce … -> assigned` lines after each of their `layout_restore` calls (C-3), and
  `qa/phase-03/scripts/exported.py` against `qa/phase-03/exported-allowlist.txt` reports no new exported component.
- E10 Motion, on the shell's clock (C-5): each step-to-step transition logs `[motion] wizard_page t0=<uptime> settle=<ms>`; three
  consecutive transitions read settle = 217 ms ± one frame (16.7 ms), phase 01 X7's Start entrance form, alpha complete in 217
  ms (RV11); `wizard_done` → Start logs the same line with the same settle (Start composes under the X7 motion, T12-6); a 60-fps
  screenrecord corroborates under phase 05's frame-spacing rule and is not the clock; the feel is judged in H1.
- E11 Presets, on both surfaces (T12-2): (a) the wizard's presets page (the E2 state, `wizard_not_now` through the steps) and (b)
  Settings > Start + theme after a run (`theme_presets`, tags `theme_preset:<name>`), the same assertions on each. Control first:
  from `pm clear` → `provision.sh` → Home, save `start_theme.xml` (the out-of-box values). For each of the six in turn, tap it and
  read back: `start_theme.xml` holds that preset's row of the table — `theme_preset`, `accent`, `theme`, `background` (absent, or
  `android.resource://app.tileshell/drawable/preset_<name>`), `transparency`, `press`, `tess_lens`, `tess_ring`,
  `keyboard_palette`, `transparency_effects` (false for Midnight and the original; phase 13's key, its E1 sub-row reads the
  `[fluent]` line) — and diagnostics `[theme] preset <name> applied` (plus `[wizard] preset <name>` on (a)). Start, seeded with
  `layout_restore qa/phase-02/baseline_layout.json`: `e13_pixels.py` reads the `slot:PEOPLE` band T and the gutter G — Default (no
  picture): T = the accent ± 2 and G = (0,0,0) ± 2; Midnight (transparency 0.0, α = 1): T = the accent ± 2; every other preset
  with a picture: T = α · accent + (1 − α) · G ± 4 with α = 1 − 0.8 · `transparency` (`start/StartPage.kt:413`), which fails if the
  picture is missing (then α = 1). Tess: `KEYCODE_ASSIST`, screencap, phase 03's `qa/phase-03/scripts/persona.py` colour search
  finds the lens on the preset's `tess_lens` hue (HAL red for Default and HAL, the accent's hue for Soft and Lumia, the dimmed red
  for Midnight). Keyboard: phase 05's `kb_begin` + `kb_dump` (`qa/phase-05/scripts/kb.sh`) over its fixture field; a letter key's
  fill reads the palette's key colour ± 4 (DARK: R6's keys, luminance ≈ 48; LIGHT: #E6E6E6). Custom: tap `accent:Red` among the
  items → `preset:Custom` (`theme_preset:Custom` on (b)) `selected="true"`, `theme_preset` = `custom`, `accent` = 4293398819,
  and every OTHER key unchanged from the preset's; after re-tapping a preset, toggling `theme_transparency_effects` gives Custom
  the same way. Tapping `preset:Default` makes every key equal the control's. Persistence, on (b) after the run finished: `am
  force-stop app.tileshell`, reopen Start + theme → the keys and the selected preset unchanged. Restore: `preset:Default`, then
  `pm clear` → `provision.sh` → Home.
- E12 Pictures (T12-2): on the host, `art/themes/{hal,soft,lumia,midnight}.png` are each ≥ 1024 px wide (`identify`) — Jeremy's
  four inputs (Q6); in the built APK, `unzip -p <apk> 'res/drawable-nodpi*/preset_<name>.webp' | identify -` reads 1872 × 4056
  for every bundled preset picture (the original's too, per Q8, when the build carries it); `unzip -l` shows no preset asset ≥
  2.5 MB and their sum ≤ 8 MB, and the APK is ≤ 8 MB larger than the same commit built without them; with phase 13 present (it is,
  T12-3), each preset tap that changes the picture is followed by `[fluent] static backdrop rebuilt for <uri>` naming that
  preset's URI.
- E13 The "Windows 10 Mobile (original)" preset against R12 (T12-9; R12 landed 2026-09-23): tap it → `start_theme.xml` holds
  `theme` DARK, `transparency` 0.75, `press` NONE, `transparency_effects` false, `keyboard_palette` DARK; the `slot:PEOPLE` band
  T = 0.40 · accent + 0.60 · G ± 4 (R12 §5.1's α by R12's own method). The accent, picture and Tess sub-assertions follow Q7–Q9:
  Q7 A or B → `accent` = 4282279423 (0xFF3E65FF), with Q7 A also `accent:Cobalt` present as the 49th swatch and selected; Q7 C →
  `accent` = 4285229526 (Purple Shadow Dark 0xFF6B69D6); Q8 → `background` names the chosen img0's asset (C: two presets, one per
  picture, each asserted); Q9 A → `persona.py` finds the lens on Cobalt's hue, B → it finds the flat accent ring (the reveal = 1
  drawing), C → the HAL red lens. Until Q7–Q9 are answered this row is not runnable in full and the doc cannot go FINAL.
- E14 "Wizard step added" — the template every later phase that adds a grant runs for its own step, cited as "phase 12 E14"
  (T12-5, C-4): `pm clear` → `provision.sh` → revoke <grant> (its adb form) → Home: the dump has `wizard_step:<ns>:<id>`, its
  `wizard_why` equals that step's line in the Why-lines table, and `wizard_progress` reads "Step 1 of 2" (the step and the presets
  page); grant it from adb and resume (`am start` the Settings hub, Back) → the step is gone and `wizard_presets` shows;
  `provision.sh` carries that grant's line, so `pm clear` → `provision.sh` → Home shows no `wizard_page` (E1 re-run on that
  build). Known instances, each run in its own doc on its build with the `provision.sh` line C-4 (a) gives it: phase 15 `appops
  set app.tileshell USE_FULL_SCREEN_INTENT allow`; phase 16 `pm grant app.tileshell android.permission.WRITE_CONTACTS`; phase 17
  `pm grant … CAMERA` and `pm grant … READ_MEDIA_VIDEO`; phase 18 `appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` (step
  `setup:files`); phase 19 `appops set app.tileshell WRITE_SETTINGS allow` and `cmd notification allow_dnd app.tileshell`. On this
  phase's own build the template runs once on `setup:usage` (revoke = `appops set app.tileshell GET_USAGE_STATS ignore`), so it is
  proven before any later phase leans on it.

**Phone-only (S25 Ultra):**
- P1 From a fresh sideload (release-signed, `debuggable=false`): the route in is recorded (Settings > Default apps > Home
  app, or Start settings > Setup checklist > Default Home and One UI's default-Home sheet), then Home; each remaining step
  tapped once with the resumed activity recorded from `dumpsys activity activities` (Samsung's notification-access page,
  Usage data access, Samsung's permission controller for Photos / Music / Calendar / Location, Samsung's "Manage keyboards"
  and its picker; then Tess's: One UI's assist page for the digital assistant, the permission controller's dialogs for her
  runtime rows and its location page for "Allow all the time") and the step advancing on return; the preset chosen shows on
  Start, on Tess and on the keyboard (screencaps for H6 / H7).
- P2 The marker survives a reboot, a Device care optimise and an update install (`adb install -r`): no wizard afterwards;
  a Samsung-side revocation (Notification access turned off in One UI Settings) reddens the checklist row and shows no wizard.

**NEEDS-HUMAN** (every row is an "accept this P4 design" row unless marked; there is no footage to match):
- H1 The wizard's look and wording: the step page, its button labels, each step's why line (the Why-lines table, T12-4), "Not
  now", "Skip setup", the progress caption, the X7 page motion.
- H2 The walking order — the Setup checklist's rows, then Tess's nine, then the presets page — reads right on the phone.
- H3 The presets page last: the six presets at the top, the items below them, "Done" (was "the accent step last", before T12-2).
- H4 The rule that a finished or skipped wizard never returns on that install (a revoked or newly added core grant goes to
  the checklist instead).
- H5 The Samsung sheets inside the flow (P1) do not break the feel.
- H6 Each preset's look, the table's values as built (T12-2), on the phone.
- H7 Labels readable over each picture in its theme, judged on the phone (the NEEDS-HUMAN row Q6 promised).
- H8 The four pictures themselves: an original design with no text, logo, face or watermark (theme-art-brief.md); no metric
  exists.
- H9 [fidelity] The "Windows 10 Mobile (original)" preset against R12's evidence (its screenshots beside the phone's), including
  the Cobalt swatch's placement if Q7 is A.

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
  `tess:background_location` answered "Allow only while using the app" (PARTIAL: FINE only) and `tess:calendar` with read only:
  each step STAYS (`partialIsDone` false, T12-1 (c)) with `[wizard] step tess:<id>: partial`; "Not now" advances.
- The Keyboard selected step: the picker dismissed without a choice (step stays); a force-stop later deselects the keyboard
  (phase 05 N-01) with the marker set: no wizard, the checklist row is the way back.
- The Default Home step when the shell was opened "Just once" from the chooser (Home missing): it is step 1, the role
  sheet is Android's on the AVD and Samsung's on the phone; choosing another launcher there leaves the step (Start stays
  behind the wizard until Home is pressed).
- The wizard while Tess's session is open over Start (E8); while a Live Tile secondary-pin band is pending (the band waits,
  R5 §1.9, until Start is visible — assert `SecondaryTiles` shows it only after the wizard finishes); while a phase 14 pod-bay
  request is pending (it waits the same way, T12-8 / phase 14 T14-7); a burst cannot be open (edit mode is under the wizard).
- Light theme and a Start background set before the run: the wizard follows the theme (screencap recorded).
- ~~The accent step with an accent already changed on the same install (a run started after Settings were used): the grid
  shows that accent selected and "Done" leaves it.~~ Re-cut 2026-09-23 (T12-2): the presets page on an install whose items were
  already changed in Settings: `preset:Custom` selected, and "Done" leaves every item as it was.
- A preset applied under battery saver (`cmd power set-mode 1`): phase 13's rule wins — `[fluent] acrylic=off
  reason=battery-saver` — while `transparency_effects` is still written as the preset says (E11's read-back); `set-mode 0` →
  acrylic follows the preset's value.
- A preset whose picture asset is missing from the build (a QA build with `preset_hal.webp` removed): the preset applies with no
  picture, `background` absent, and `[theme] preset HAL applied: no picture` (the same line T12-7 gives the original preset).
- A preset tapped over a picture the user chose themself: the preset's picture replaces it and the user's is NOT kept (T12-2);
  "Remove picture" / "Choose picture" in the items still work afterwards and make the preset read Custom.
- Transparency effects toggled after a preset (E11): `theme_preset` = `custom`, every other key unchanged.
- Work / private profile present: no profile grant is a checklist row, so nothing changes (assert the step list against
  E18's profile setup).
- The AVD's keyguard enabled (`locksettings set-disabled false`, phase 01 E20's setup): StartActivity never shows over the
  keyguard, so the wizard never does either; after unlock it shows as usual.
- A later phase ADDs a core row to a finished install: covered by E5's revoked-later case (same rule); when phase 04 is
  built, its own doc re-runs E5 with its row, and every later phase runs E14 for its step (C-4).

## QA evidence
