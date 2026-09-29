---
phase: 12
slug: setup-wizard
status: FINAL   # 2026-09-28 (Jeremy: "finalize phase 12") after review round 3 (the last) was triaged and applied; FINAL gates (T12-13) met the same day. Changes from here only through an INDEX Change Log line.
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
never sees it. The QA harness provisions every grant and then finishes the wizard once through the marker the wizard's own finish
writes, as a user who completed setup would (Decisions, lead 2026-09-23; re-cut 2026-09-23 by r2 triage C-15 — the split-time text
said no driver needed a bypass because the wizard had nothing to do, which a force-stop that deselects the keyboard makes false).
Every look value is a P4 design: W10M's out-of-box experience was never measured (no R3 / R6 / R7 row), so
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
(`qa/phase-03/scripts/provision.sh` grants the Setup rows and Tess's nine and writes the finished marker, `PROVISION_FINISH_WIZARD=0`
skipping the marker, C-15; each later phase appends its line, C-4) plus one
regression run; the "wizard step added" template row later phases cite (E14).
**Out (explicitly):** ~~Tess's own checklist rows (`cortana/CortanaChecklist.kt`: assistant role, microphone, contacts, texts,
calls, locations, call log) — they live on her Settings page by phase 03's decision, and the microphone is asked for the
first time she listens (INDEX Change Log 2026-09-22, `CortanaPermissionActivity`); this stands unless interview Q2 says
otherwise.~~ SUPERSEDED 2026-09-23 by Q2 C (T12-1): Tess's nine grant rows are steps; her five observation rows (`exact_alarms`,
`models`, `service`, `speech_process`, `person_triggers`) are not, and her Settings page stays her permanent health surface.
Phase 04's rows (accessibility, Wireless debugging pairing, helper — its overlay need is met by phase 15's `overlay` row,
`Checklist.kt:138`; phase 04 adds no second overlay row, r3 D14): phase 04 ADDs them to the checklist and the wizard
walks whatever rows exist — no placeholder step (Rule 16); phase 04 runs E14 for each. A launcher entry or a separate activity
for the wizard (the shell's launcher icons — six since phase 15: Music, Clock, Weather, Start settings, Recorder, Calculator, the
manifest's LAUNCHER filters — do not include Start; Start is reached through the Home role; r3 D13).
Any W10M OOBE page the shell cannot own (region, Wi-Fi, Microsoft account, restore, Cortana sign-in). Reordering or renaming
checklist rows (the checklists are phase 01's and phase 03's; the wizard reads them). Changing what a grant does. A lock-screen
picture (R12 §6 lists img5): a preset must not rewrite Android's lock-screen wallpaper as a side effect of a preview (phase 17's
"set as" and phase 19's Lock screen page are the user's routes); phase 07's glance is untouched (reason re-cut 2026-09-23 by T12-15
(b); the split-time reason, "the shell draws no lock screen", missed that the shell can set Android's lock wallpaper). Hooks for later phases.

## Decisions
- 2026-09-28: FINAL (Jeremy: "finalize phase 12").
- 2026-09-28: Review round 3 (the last): fable + codex-cli; every finding accepted and applied (triage
  review/2026-09-28-phase12-r3-triage.md; D1–D16 design, V1–V10 testability; apply log review/2026-09-28-phase12-r3-applied.md).
  No finding reopened a dated ruling of Jeremy's; no question went to him.
- 2026-09-28: R12 shipping ruling (Jeremy: "(a)"), closing T12-13's second FINAL gate: every build — the public CI APK
  (apk.yml's "latest" Release) included — bundles R12's two img0 pictures into the original preset (`preset_w10m_hero`,
  `preset_w10m_streaks`), read by build task 5's host script from docs/plan/r12/ (tracked since 9888f2c on his 2026-09-23
  "(c)" to committing them publicly). T12-7's no-picture form stays only as the fallback for a build where those files are
  missing (E12 / E13 still branch on the APK). Asked knowing the repo is public (GitHub API 200, unauthenticated) and that
  every push to main publishes the APK. Added 2026-09-28 (r3 triage D11, V2): shipped as committed WebP outputs of the host
  script, so the CI runner builds them in without running it (build task 5); the clause "T12-7's no-picture form stays only as
  the fallback for a build where those files are missing (E12 / E13 still branch on the APK)" is SUPERSEDED — under (a) E12 / E13
  FAIL an APK that lacks any preset picture (the no-picture branch is not an accepted outcome for a CI or dev build), and T12-7's
  no-picture form stays only as the runtime fallback when a picture cannot be decoded.
- 2026-09-28: Jeremy's variant pick (Jeremy: "hal-a lumia-b midnight-b soft-c"): HAL ← `art/themes/hal-a.png`, Lumia ←
  `lumia-b.png`, Midnight ← `midnight-b.png`, Soft ← `soft-c.png`; the other eight files stay local and unbundled. From the
  check below: hal-a, lumia-b and midnight-b are 941 px wide, so the host crop scales them ≈ 2.4× up (Lumia's edges soften;
  Jeremy judges it on the phone, H row); soft-c is 1536 wide. Agent calls the picks open (T12-2 left Soft's and Midnight's
  accents to "when the picture is in art/themes/"): Soft = Purple Shadow #8E8CD8, Midnight = Purple Shadow Dark #6B69D6 (the
  table gives each method). The four picked files are committed.
- 2026-09-28: Jeremy's pictures are in art/themes/ (Q6 input): three variants per preset, `hal-`, `soft-`, `lumia-`,
  `midnight-` × a / b / c. Agent check against theme-art-brief.md: all twelve are portrait with no text, logo, face or
  visible mark (full view, and each corner under autocontrast); every file carries an invisible C2PA content-credential
  chunk (PNG `caBX`, JPEG APP11), which the host crop's re-encode drops. The a and b files are PNG at 941 × 1672, under
  the brief's 1024-px width (to 1872 × 4056 is ≈ 2.4× up); the c files are 1536 × 2752 but JPEG named .png (the host
  script reads them as JPEG and writes WebP; r3 D10). Lumia-c's middle is busy behind the tiles (the brief: keep the middle calm).
  Which variant each preset ships is Jeremy's pick (asked 2026-09-28); FINAL still also waits on the R12 wallpaper ruling.
- 2026-09-23: From phase 15's review question Q-E (Jeremy: "(a)" — alarms ring as W10M's banner everywhere): the
  setup:overlay step ("Display over other apps", with its why line) EXISTS; phase 15 ADDs its row to the Setup
  checklist and its why line to this doc's table, and this phase builds the step (C-34, 2026-09-23: phase 15 was built
  first, beside phase 11). The Q-E B / C branches in the rows are not applicable.
- 2026-09-23: Interview Q9 — the original preset keeps Tess's lens, tinted Cobalt (Jeremy: "(a)"); no Cortana-style disc is
  built.
- 2026-09-23: Interview Q8 — both original Start pictures, as two variants of the preset (Jeremy: "(c)"): "Windows 10 Mobile
  (original)" offers the later rotated Hero img0 (14393 / 15063, the final release's) and the first build's light-streak img0
  (10586), both from R12's files in the A10 branding module; every other value of the preset is the same in both. The preset
  list shows one entry with the two pictures as its variants (agent: not two separate presets, so the list stays six).
- 2026-09-23: Interview Q7 — Cobalt #3E65FF becomes a 49th accent everywhere accents are picked (Jeremy: "(a)"), as W10M
  phones effectively had it (R12: an OEM colour outside R3 A16's 48). It is an ADD to phase 01's accent picker, built by this
  phase and recorded in the INDEX Change Log when built; the "Windows 10 Mobile (original)" preset uses it.
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
  harness provisions every grant (SUPERSEDED in part 2026-09-23 (lead; r2 triage C-15): provisioning also writes the finished marker,
  because a force-stop deselects the keyboard). It walks the Setup checklist's own rows in order (one source of truth), then the accent.
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
  Core = the twenty grant rows of both checklists — the eleven Setup rows with a grant action (`Checklist.kt:93-140`, phase 15's
  `full_screen_alarms` and `overlay` included) and Tess's nine (Q1 B + Q2 C; T12-1, 2026-09-23; count re-cut by r3 D6). "Finished" is a marker set when the run
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
  also gain `partialIsDone` (2026-09-23, T12-1 (c)). Added 2026-09-28 (r3 D5): BOTH row types gain `grant: Boolean` — the flag
  that tells a grant row from an observation row, and the wizard's step filter — true for the eleven Setup rows and Tess's nine,
  false for `samsung_badges`, `legacy_badges`, `listener`, `exact_alarms`, `models`, `service`, `speech_process`,
  `person_triggers` (an action lambda or an empty `permissions` list tells neither: `listener` has an action,
  `Checklist.kt:141-143`, and `tess:assistant`'s `permissions` is empty; an id list inside the wizard would be the third list).
  Phase 01's `ChecklistPage` is unchanged in behaviour (an ADD to the data class; recorded in the INDEX Change Log when built).
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
  already follow (INDEX Change Log 2026-09-22 "THE MICROPHONE PROMPT NOW APPEARS"). For a `tess:` runtime step the blocked branch
  runs twice by construction: `CortanaPermissionActivity` opens the app-info page itself on the blocked result
  (`cortana/CortanaPermissionActivity.kt:33-38`) and, on the return, the wizard relabels the button as above (r3 D12). Denied for
  good is never silent. Added
  2026-09-23 (T12-17): a grant action whose intent cannot start (`ActivityNotFoundException` — One UI resolves Settings actions
  differently, and phase 19's table found three that resolve to nothing even on AOSP) logs `[wizard] step <ns>:<id>: action failed
  <intent action>: <exception>` and the button relabels to "Open Android settings", which opens Android's Settings home
  (`Settings.ACTION_SETTINGS`, phase 19's fallback form); the step then advances on resume like any other.
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
  `diag wizard` in the driver floor) — and, in every row where notification access may be revoked, through `StartActivity`'s dump
  (`adb shell dumpsys activity app.tileshell/.StartActivity`, build task 2; the service dump reaches only a running listener
  instance, and the ring is in-process only, `diag/Diagnostics.kt`; r3 V4). Re-cut 2026-09-23: row ids are namespaced `setup:<id>` / `tess:<id>` in every line
  (T12-1); `[wizard] accent <name>` is replaced by `[wizard] preset <name>` and `[theme] preset <name> applied` (T12-2); E10
  reads `[motion] wizard_page t0=<uptime> settle=<ms> frames=<n> maxGapMs=<ms>` (C-5, C-31; the built line also carries `peak=`
  and `overshoot=`, `ui/MotionTrace.kt:6,31-40`, so its fields are read by key, r3 D13). **Precedence** (2026-09-23, r2
  triage C-15; every provisioned AVD holds every core row AND the marker): `[wizard] not shown: core held` when every core row is
  held (marker or not); else `[wizard] not shown: finished` when the marker is set; else the run shows. Added 2026-09-23: `[wizard]
  step <ns>:<id>: action failed <intent action>: <exception>` (T12-17); `[theme] preset w10m variant <v> applied` (T12-10);
  `[theme] preset Custom restored` (T12-12).
- 2026-09-22 (agent): **Test tags:** `wizard_page` (root), `wizard_step:<row id>`, `wizard_progress`, `wizard_title`, `wizard_body`,
  `wizard_action`, `wizard_not_now`, `wizard_skip`, ~~`wizard_accent` (the personalisation step's root, with the shared grid's
  `accent:<name>` swatches inside it)~~, `wizard_done`. Re-cut 2026-09-23: `wizard_step:setup:<id>` / `wizard_step:tess:<id>`
  (T12-1); `wizard_why` (T12-4); `wizard_presets` replaces `wizard_accent` as the last page's root, with `preset:<name>`,
  `preset:Custom` and the items below them — the shared grid's `accent:<name>` swatches among them (T12-2); the original preset's
  variant chips `preset_variant:hero` / `preset_variant:streaks` on the wizard's page and `theme_preset_variant:<v>` on Start +
  theme (T12-10). Every text a row reads sits on the node that carries the tag (the MUSIC8 lesson, testability 24).
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
  (`qa/phase-03/scripts/provision.sh:35,58-59`; phase 15's two appops lines are `:52-53`, r3 D13) — so the provisioned AVD holds
  all twenty rows (the eleven Setup rows with a grant action, `Checklist.kt:93-140`, phase 15's `full_screen_alarms` and `overlay`
  included, and Tess's nine; r3 D6), which E1(a) proves; each later
  phase appends its own line (the C-4 line below). Extended 2026-09-23 (r2 triage C-15): after the grants, `provision.sh` writes the
  finished marker by the one mechanism in the lead's line below, and `PROVISION_FINISH_WIZARD=0` skips it for rows that test the
  wizard itself.
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
  (`qa/phase-03/exported-allowlist.txt`) is unchanged; E9 checks it. Added 2026-09-28 (r3 D15, V4): surfaces for the adversarial
  review — the Custom snapshot's grant release (T12-12); the `background` key accepting `android.resource://` URIs (opened by
  `start/BackgroundDecoder.kt:39` through `openInputStream` — the shell's own resources); `qa/phase-12/fixtures/setup_wizard.xml`
  written by `run-as` (debug builds, QA only); and the `StartActivity.dump(...)` override that exposes the diagnostics ring through
  the already-exported activity (build task 2).
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
  | `setup:people` (phase 16's row, added at its build; r2 triage T16-15; walked at its `Checklist.kt` position) | People shows and edits your contacts. Without it People can't see them. |
  | `setup:full_screen_alarms` (phase 15's row, walked at its `Checklist.kt` position; C-4, C-34 2026-09-23) | Alarms ring over the lock screen. Without it an alarm still sounds, but shows only as a notification. |
  | `setup:overlay` (phase 15's row, Q-E A, T15-14; C-34 2026-09-23) | Alarms ring over the app you're using. Without it an alarm shows as a notification. |
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
- 2026-09-23 (review triage T12-5, C-4): **Later phases' grants.** Every later phase that adds a grant — phase 04's accessibility
  and helper pairing (its overlay need is met by phase 15's `overlay` row, `Checklist.kt:138`; phase 04 adds no second overlay
  row, r3 D14), phase 06's roles, and the inbox apps' (15: the full-screen-intent appop; 16: WRITE_CONTACTS; 17:
  CAMERA and READ_MEDIA_VIDEO; 18: all-files access; 19: WRITE_SETTINGS and Do-not-disturb access) — (a) appends its line to
  `provision.sh`, (b) adds its checklist row, its step and its why line to the table above, (c) runs E14 ("wizard step added")
  for its step and re-runs E1 on its build, and (d) writes every `pm clear` row as "`pm clear` → `provision.sh` → Home". The
  finished-install rule stands (Persistence; H4): a phone that finished or skipped the wizard is not re-summoned by a later phase's
  new row — that row goes red on its checklist instead. Each of phases 15–19 restates that rule in one sentence.
- 2026-09-23 (review triage T12-9, R12): **The original preset's values come from R12** (`r12-w10m-stock-theme.md`, landed
  2026-09-23): Dark, accent Cobalt #3E65FF (outside R3 A16's 48; the 49th swatch here by Q7 A), full-screen picture img0 with
  accent tiles at α 0.40, press style none, acrylic off. Q7–Q9 answered 2026-09-23 (A, C, A; Decisions above), and the preset
  table and E13 are written to those answers (re-cut 2026-09-23 by r2 triage T12-10; the split-time "UNANSWERED … written
  conditionally" is struck). FINAL gates (T12-13): the four pictures in art/themes/ (Q6) AND Jeremy's ruling on committing /
  shipping R12's Microsoft wallpapers (asked separately 2026-09-23, because every `main` CI APK is a public download,
  `.github/workflows/apk.yml:3-8`; the repo has tracked them since commit 9888f2c, whose `.gitignore` note cites an owner answer —
  the lead confirms and records the ruling here); which build carries the original pictures follows that ruling; with none,
  T12-7's no-picture form ships. This doc does not assume them committed: the host script (build task 5) reads them into the A10
  branding module at build time, and E12 / E13 branch on the APK, so either answer is testable without a re-cut. Re-cut 2026-09-28
  (r3 D11, V2; the ruling is (a), Decisions): the host script's six WebP outputs are committed and the script is run by hand, not
  at build time (build task 5); "not assume them committed" now refers to the SOURCES only, which are tracked; E12 / E13 no
  longer branch — an APK lacking any preset picture FAILs.
- 2026-09-23 (agent, review triage T12-1): **Q2 applied — which rows, the tags, location, the order.** (a) Tess's steps are her
  nine rows with a grant action — `assistant`, `microphone`, `contacts`, `calendar`, `sms_send`, `call_phone`,
  `background_location`, `call_log`, `sms_read` (`cortana/CortanaChecklist.kt:37-118`, r3 D13); `exact_alarms` (granted at install,
  F1-m7), `models`, `service`, `speech_process` and `person_triggers` are observations and never steps, as the Steps line says of
  `samsung_badges` / `listener`. Each is walked through `CortanaPermissionActivity`'s existing paths: `tess:assistant` opens the
  Settings page that owns the choice (`Settings.ACTION_VOICE_INPUT_SETTINGS` first; ROLE_ASSISTANT is not requestable, so there
  is no role sheet — `cortana/CortanaPermissionActivity.kt:74-92`), button "Open settings"; the runtime rows raise Android's
  dialog, button "Allow"; `tess:background_location`, button "Allow all the time". (b) Tags and lines are namespaced —
  `wizard_step:setup:<id>` / `wizard_step:tess:<id>`, `[wizard] step setup:<id>: …` — because both checklists have a `calendar`
  row (`onboarding/Checklist.kt:108`, r3 D13; `CortanaChecklist.kt:44-53`). (c) No third step kind: Tess's `background_location` row
  already carries FINE + BACKGROUND (`CortanaChecklist.kt:62-71`) and `CortanaPermissionActivity.kt:57-69` already asks FINE
  first and then BACKGROUND alone, which Android answers with the app's location page, as it requires on API 30+. What changes is
  the PARTIAL rule: "PARTIAL counts as this step done" is Photos-only — a `partialIsDone` flag on the row, true for `setup:photos`
  and false for every other row that can be PARTIAL (`tess:background_location`, whose PARTIAL means FINE held and "all the time"
  still owed; `tess:calendar`, read held and write owed); such a step stays until GRANTED or "Not now". (d) Order: the Setup rows
  in `Checklist.kt` order, then Tess's in `CortanaChecklist` order, then the presets page; "Skip setup" covers both lists; a row
  that turns MISSING again mid-run rejoins the steps not yet shown, ahead of the presets page. Core = all twenty grant rows
  (eleven Setup, `Checklist.kt:93-140`, and Tess's nine; r3 D6) (Q1 B + Q2 C); a PARTIAL row still never summons the wizard.
  Reason: Q2 C lists the rows but not the observation split, the id
  collision, the location mechanics or the merged order, and each is settled by the existing code rather than by a new design
- 2026-09-23 (agent, review triage T12-2): **the preset model** (Q3–Q6 designed below the rulings; every value is an H6 accept
  except the R12-marked cells). Keys in `start_theme.xml` (`ShellSettings`): `accent`, `theme`, `background`, `transparency` and
  `press` exist (`prefs/ShellSettings.kt:63-73` read / `:79-89` write, r3 D13); `transparency_effects` is phase 13's; this phase
  ADDs `theme_preset`, `tess_lens` and `keyboard_palette`, and `theme_preset_variant` (added 2026-09-23 by T12-10, last Decisions
  lines); `tess_ring` is struck (r3 D3: not a key — see below the table).
  A tile's alpha over a picture is 1 − 0.8 · `transparency` (`start/StartPage.kt:429`, r3 D13).

  | Preset | `accent` | `theme` | `background` | `transparency` (tile α over the picture) | `press` | `tess_lens` | `keyboard_palette` | `transparency_effects` |
  |---|---|---|---|---|---|---|---|---|
  | Default | Default Blue #0078D7 (X26) | DARK | none | 0.5 (as built) | NONE | `hal` (`Brand.LENS_*`) | DARK (the built keyboard, R6) | true |
  | Windows 10 Mobile (original) | Cobalt #3E65FF (R12 §1, HIGH): the 49th named swatch `Cobalt`, after the 48 (Q7 A) | DARK (R12 §2) | img0, one of two variants (Q8 C; `theme_preset_variant`, T12-10): `hero` (default) = the later rotated Hero, the final release's, `img0_w10m_1607-1709.jpg` → `preset_w10m_hero`; `streaks` = the first build's light streaks, `img0_w10m_1507-1511.jpg` → `preset_w10m_streaks` (R12 §4.2); ~~a variant whose asset the build lacks is not offered, and with neither the preset applies with no picture (T12-7)~~ both ship in every build (ruling (a); an APK lacking either FAILs E12 / E13, r3 V2); a picture that cannot be decoded applies the preset with no picture (T12-7's runtime fallback) | 0.75 → α 0.40 (R12 §5.1) | NONE (R12 §5.2) | `accent` — the lens on Cobalt's hue (Q9 A) | DARK, the cursor dot in the accent (R12 §5.2) | false (R12 §5.2, approximation) |
  | HAL | Red #E81123 — the A16 swatch nearest the lens red `Brand.LENS_IRIS` #D81810 (`brand/Brand.kt:46`; RGB distance 25.8), agent call T12-11 | DARK | `preset_hal` | 0.35 (α 0.72) | NONE | `hal` — the exact HAL lens (`Brand.LENS_*`) | DARK | true |
  | Soft | Purple Shadow #8E8CD8 — the A16 swatch RGB-nearest each of soft-c's three main colours (k-means, k = 4: pink (250,219,230), lavender (231,213,237), pale blue (207,229,248)), agent call 2026-09-28 | LIGHT | `preset_soft` | 0.6 (α 0.52) | P4_PRESS | `accent` | LIGHT (new, P4: panel `Palette.lightChromeLow` #F2F2F2, keys `lightChromeMedium` #E6E6E6, labels #000000 — R1 §6.2's light chrome, `ui/tokens/Palette.kt:27-32`) | true |
  | Lumia | Seafoam #00B7C3 — the A16 swatch nearest WP8.1 Cyan #1BA1E2 (R12 §1) by RGB distance, 46.6; no swatch is named "Cyan" (`ui/tokens/Palette.kt:11-18`) | DARK | `preset_lumia` | 0.5 (α 0.6) | WP8_TILT | `accent` | DARK | true |
  | Midnight | Purple Shadow Dark #6B69D6 — the saturated A16 swatch nearest by hue to midnight-b's aurora (its non-black clusters at 225-249°); RGB distance, Soft's method, lands on greys for so dark a colour (Storm #4C4A48 first), agent call 2026-09-28 | DARK | `preset_midnight` | 0.0 (opaque tiles) | NONE | `hal_dim` — the HAL lens dimmed | DARK | false |

  **`tess_lens`** (r3 D7) is a string ∈ {`hal`, `accent`, `hal_dim`}: `hal` = `Brand.LENS_*` as built; `accent` = `LENS_RIM`,
  `LENS_IRIS` and `LENS_GLOW` (`brand/Brand.kt:45-47`) each re-hued to the current accent's HSV hue with its own S and V kept
  (`LENS_CORE` unchanged — the specular), so the lens follows the accent (Cobalt in the original, Q9 A) and changes with it;
  `hal_dim` = `Brand.LENS_*` with R, G, B each × 0.5 (`LENS_CORE` unchanged). The "Tess's look" row offers `hal` and `accent`;
  `hal_dim` is Midnight's. **`tess_ring` is not a key** (r3 D3): the persona's ring is lens-painted (`cortana/ui/Lens.kt:100-120`,
  `LENS_GLOW` → `LENS_RIM` at reveal 0), and the one accent-coloured form, the three-tap reveal, already follows `accent`
  (`Lens.kt:71`, `cortana/ui/CortanaSessionRoot.kt:134`), which every preset sets.

  **Custom rule:** `theme_preset` holds the preset's id (`default`, `w10m`, `hal`, `soft`, `lumia`, `midnight`) and becomes
  `custom` when a write changes any of the preset's keys — `accent`, `theme`, `background`, `transparency`, `press`,
  `transparency_effects` (phase 13's Transparency effects switch), `tess_lens`, `keyboard_palette` (`theme_preset_variant`
  excepted, T12-10) — and never on `columns`, `profiles`, `autosize`, `photos_slideshow` or `photo_frame` (r3 D8); the preset write
  puts only the preset's keys and never rewrites the others (`ShellSettings.update()` as built rewrites every key,
  `prefs/ShellSettings.kt:76-93`; build task 3 names the narrower write); re-tapping a preset restores every item it
  sets; choosing the original preset's picture variant is not an item change (T12-10). **Live apply:** tapping a preset IS the change — the whole shell restyles at once (Jeremy: "auto changes the phone so they
  can see"); "Not now", Back or "Skip setup" after a tap leave it applied. ~~A preset replaces a picture the user chose; the user's
  picture is NOT kept anywhere (re-chosen from Start + theme).~~ SUPERSEDED 2026-09-23 by T12-12 (last Decisions lines): Custom
  remembers the last custom set, the user's picture and its grant included. **The items below the presets** (Q4: each still changeable): Start +
  theme's existing rows (the accent grid — pulled out of `StartThemePage` into one shared composable, as the 2026-09-22 line
  planned — Dark / Light, choose / remove picture, tile transparency, press style), phase 13's Transparency effects switch, and two
  new rows, "Tess's look" (lens: `hal` or `accent`; `hal_dim` is Midnight's, r3 D7) and "Keyboard" (Dark / Light) (P4, H6).
  **Pictures:** `art/themes/<name>-<variant>.png` (Jeremy's, Q6; the variant each preset ships is his pick, Decisions 2026-09-28:
  `hal-a`, `lumia-b`, `midnight-b`, `soft-c` — soft-c is JPEG data under a .png name, read as JPEG) → centre-cropped on the host to
  1872 × 4056 (1440 × 3120 × 1.3 parallax headroom, portrait; scaled to fill the height, the width trimmed equally on both sides)
  → LOSSY WebP, quality 90 (`cwebp -q 90` or PIL `quality=90`), no metadata (the re-encode drops the C2PA chunks; r3 D10) →
  bundled in the branding module (A10) as `preset_<name>`, the WebP committed at
  `app/src/main/res/drawable-nodpi/preset_<name>.webp` (r3 D11); `background` holds its
  `android.resource://app.tileshell/drawable/preset_<name>` URI; the pictures add ≤ 8 MB to the APK in total and none is ≥ 2.5 MB
  (E12; measured at q 90: ≈ 0.7 MB for all six, r3 D10). The original preset's two variants are `preset_w10m_hero` and
  `preset_w10m_streaks`, made the same way from R12's two img0 files by the same hand-run script and committed beside them
  (T12-10; "never assumed committed", T12-13, now refers to the tracked SOURCES only, r3 D11). **ADDs to FINAL parts** (INDEX
  Change Log lines when built): phase 01's Start + theme gains the presets row and
  `theme_preset`; phase 03's persona reads `tess_lens` from `ShellSettings` (r3 D3); phase 05's keyboard reads
  `keyboard_palette` through the existing `KeyboardConfigProvider` (`AndroidManifest.xml:326`, r3 D13), and phase 05 E3's ± 32-level
  colour check is stated to run on the Default preset. **Tags:** `preset:<name>` on the wizard's presets page (`preset:Custom` for
  the Custom entry), `theme_preset:<name>` on Start + theme's copy (one composable, two tag prefixes); page roots `wizard_presets`
  and `theme_presets`; the variant chips `preset_variant:<v>` / `theme_preset_variant:<v>` (T12-10). **Diagnostics:** `[wizard]
  preset <name>` (a tap on the wizard's page), `[theme] preset <name> applied` (every apply, either surface), `[theme] preset w10m
  variant <v> applied` (T12-10), `[theme] preset Custom restored` (T12-12). Reason: Q3–Q6 rule what a preset sets, that it applies live and which six ship; the model above is
  what makes those rulings buildable and each value checkable
- 2026-09-23 (agent, review triage T12-3): build order **11 → 13 → 12 → 14**, and this phase's depends-on becomes `[01, 03, 05,
  10, 13]`. Reason: a preset writes `StartTheme.transparencyEffects`, a field only phase 13 adds (`prefs/ShellSettings.kt:53`,
  `transparencyEffects`, in since phase 13 was built; r3 D13); phase 13 does not depend on 12; and this doc builds its presets "in
  their final form" (Rule 16). INDEX's phase notes
  for 12 / 13 and PLAN's 2026-09-22 order line take the swap when the lead applies the triage there
- 2026-09-23 (agent, review triage T12-6): while the wizard shows, the pager is NOT composed — the feeds keep running, which is all
  "tiles keep running underneath" means, since there is nothing to draw for — and Start composes when the wizard finishes, under
  the X7 page motion. E2's "no `start_page` node" then holds as written. Reason: a covered-but-composed Start would be in the dump,
  and a z-order assertion by bounds is weaker than absence
- 2026-09-23 (agent, review triage T12-7): "Lumia" and "Windows 10 Mobile (original)" are `Brand` strings (`brand/Brand.kt`), and
  the stock pictures live in the A10 branding module like the Segoe substitute. If a build has no shippable stock picture (whether
  R12's files are committed / shipped is Jeremy's ruling, pending per T12-13 — the parenthetical "stay local and gitignored" was
  stale by 2026-09-23, the repo tracking them since commit 9888f2c), the original preset ships with R12's
  accent, mode, transparency, press style and acrylic values and NO picture — still a preset, stated, not a placeholder (no v1 →
  v2) — and logs `[theme] preset Windows 10 Mobile (original) applied: no picture`. Reason: A10 keeps every Microsoft name and
  asset swappable, and a preset without its picture is still the measured look minus one item. Re-cut 2026-09-28 (r3 V2; the
  ruling is (a)): every build ships both pictures, so this no-picture form is only the runtime fallback when a picture cannot be
  decoded; E12 / E13 FAIL an APK that lacks any preset picture

- 2026-09-23 (agent, lead, resolving the writer's flag on force-stop): QA provisioning FINISHES the wizard once, through the
  wizard's own finish marker, exactly like a user who completed setup — ONE mechanism (re-cut 2026-09-23 by r2 triage C-15 from
  "provision.sh taps through, or writes the same done marker the finish writes"; "taps through" is impossible — with every grant
  held the wizard never shows): `provision.sh` writes the marker with the shell stopped — `adb shell am force-stop app.tileshell`,
  `adb push qa/phase-12/fixtures/setup_wizard.xml /data/local/tmp/` (the file is `<map><boolean name="finished" value="true" /></map>`),
  `adb shell 'run-as app.tileshell sh -c "mkdir -p shared_prefs && cat /data/local/tmp/setup_wizard.xml >
  shared_prefs/setup_wizard.xml"'` (`layout_restore`'s push-then-cat form, `qa/phase-02/scripts/layout.sh:21-23`; SharedPreferences
  is cached in-process, hence the stop), then `ime enable` + `ime set app.tileshell/.ime.KeyboardService` (the stop deselected it),
  then Home; `PROVISION_FINISH_WIZARD=0` skips the marker step. After that, a missing row goes to the checklist per this doc's finish rule — so am force-stop app.tileshell,
  which makes Android deselect the keyboard (qa/phase-05/README.md), sends "Keyboard selected" to the checklist instead of
  summoning the wizard in every driver that restarts the shell (phase 12 E1(a), layout_restore, phase 14's force-stop + Home).
  Rows that test the wizard itself provision with `PROVISION_FINISH_WIZARD=0` (no marker) or start from `pm clear` alone, and end
  "`pm clear` → `provision.sh` → Home", which restores the marker (RV12); E1(a) runs on a fresh `pm clear` → `provision.sh`
  (marker written) and asserts both "not shown" lines under the precedence rule (Diagnostics).
- 2026-09-23 (agent, r2 triage T12-10): **the original preset is one id with one variant key.** `theme_preset` = `w10m`; new key
  `theme_preset_variant` = `hero` (default: `img0_w10m_1607-1709.jpg`, the final release's, PLAN Q12) | `streaks`
  (`img0_w10m_1507-1511.jpg`); the preset entry shows the Hero, and under it two small picture chips pick the variant (P4, H9),
  tagged `preset_variant:<v>` on the wizard's page and `theme_preset_variant:<v>` on Start + theme; a chip is drawn only for a
  variant whose asset the build carries (none → no chips, and T12-7's no-picture form; under ruling (a) both are always carried
  and an APK lacking either FAILs E13, r3 V2); each choice logs `[theme] preset w10m
  variant <v> applied`; changing the variant is not a Custom change, and re-tapping the preset applies the last-chosen variant.
  Cobalt is added as the 49th named swatch (`accent:Cobalt`, after the 48; R12 §6 places it last row, first column) in the one
  shared grid, so both surfaces show 49. Reason: Q8's Decision says "one entry … so the list stays six", which rules out a second
  preset id (review F3's two-id form rejected); one key keeps the Custom rule and every other key identical across the variants
- 2026-09-23 (agent, r2 triage T12-11): **HAL's `accent` is Red #E81123** (4293398819), the A16 swatch nearest the lens red
  `Brand.LENS_IRIS` #D81810 (`brand/Brand.kt:46`; RGB distance 25.8); `tess_lens` stays the exact HAL lens, so the lens look is
  unchanged; `tess_ring` is not a key: the reveal already follows `accent` (`Lens.kt:71`, `CortanaSessionRoot.kt:134`; r3 D3).
  Reason: Q7 A added Cobalt for a fidelity reason ("as W10M phones effectively had it") that HAL red does not have; the grid stays
  one-off (49), HAL's accent is pickable and shows selected in the grid, and the red that
  matters (the lens) is its own key. H6 judges
- 2026-09-23 (agent, r2 triage T12-12): **Custom remembers the last custom set.** When a preset tap replaces a state whose
  `theme_preset` is `custom` (every user item change makes it so), the full item set — `background` included, with its persisted
  URI grant kept, not released, while the snapshot references it — is saved as the Custom snapshot in its own `SharedPreferences`
  file `theme_custom` (the same key names, plus `saved_at`), never in `start_theme.xml`, so `start_theme.xml` holds only the live
  set; `pm clear` wipes it, `adb install -r` keeps it (r3 D9); `preset:Custom` /
  `theme_preset:Custom` is tappable when a snapshot exists and restores it (`[theme] preset Custom restored`); a newer snapshot
  replaces the older and releases a `content://` grant the shell took (`takePersistableUriPermission`,
  `settings/StartThemePage.kt:50`) that neither `background`, `photo_frame` nor the snapshot references any more; never on an
  `android.resource://` URI (r3 D15: one URI can back both `background` and `photo_frame`, `StartThemePage.kt:50,58`, and a
  preset picture carries no grant). Reason: live apply makes every preset tap a preview, and a preview
  must never cost the user their own picture (P1); the snapshot costs one saved key set. H6 judges
- 2026-09-23 (agent, r2 triage T16-15, from phase 16): **People gets its own Setup row `setup:people`** (READ_CONTACTS +
  WRITE_CONTACTS; `partialIsDone` false), ADDed by phase 16 at its build with its step and the why line now in the Why-lines table
  ("People shows and edits your contacts. Without it People can't see them." — approximation, H1); Tess's `tess:contacts` row is
  unchanged (READ only). Reason: phases 15 / 17 / 18 / 19 each put their app's grant on the Setup checklist, and Tess never writes
  contacts, so widening her row would read PARTIAL on her health page for a need that is People's

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

7. ~~Original accent~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   (added 2026-09-23 from R12) **The original W10M accent.** W10M phones shipped with "Cobalt" #3E65FF, a maker's colour
   that is NOT one of the 48 accent swatches the shell's picker offers (R12; R3 A16).
   A. Add Cobalt as a 49th swatch everywhere accents are picked, as W10M phones effectively had (lean)
   B. The original preset uses Cobalt, but the picker keeps its 48 (so choosing another accent loses Cobalt for good)
   C. Use the nearest of the 48 instead
   D. Other / let me clarify
8. ~~Which original picture~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   (added 2026-09-23 from R12) **Which original Start picture.** Phones on the first W10M build shipped a light-streak "img0";
   later builds (Anniversary / Creators Update) replaced it with the rotated "Hero" image. Both are bundled.
   A. The later Hero image, matching the final release this build follows (lean)
   B. The first light-streak image
   C. Both, as two variants of the preset
   D. Other / let me clarify
9. ~~Tess in the original preset~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   (added 2026-09-23 from R12) **Tess in the original preset.** W10M's Cortana was a flat accent-coloured disc; Tess's
   default look is the HAL-style lens.
   A. The original preset keeps Tess's lens, tinted to Cobalt (lean)
   B. The original preset switches Tess to a flat Cortana-style disc (a new look, in the branding module)
   C. Tess's look is untouched by this preset
   D. Other / let me clarify

## Build tasks
(Re-cut 2026-09-23 by T12-1 – T12-8 and C-4; the split-time tasks walked the Setup rows only and ended on the accent grid.)
1. Wizard model: the visibility rule over both checklists (core = the twenty grant rows, r3 D6); the step list — the Setup rows in
   `Checklist.kt` order, then Tess's nine in `CortanaChecklist` order, then the presets page — with namespaced ids (T12-1);
   `ChecklistRow.permissions`, and `partialIsDone` and `grant` on both row types (ADDs to phase 01's and phase 03's data classes;
   `grant` is the step filter, r3 D5); the
   `setup_wizard` marker store; the re-derive-on-resume rule; diagnostics lines with the "not shown" precedence (C-15); JVM tests on
   the pure rules (visibility, step derivation and order, namespacing — which also asserts the eight observation rows, `grant`
   false, are never steps (r3 D5) — `partialIsDone` per row, blocked detection, the precedence
   of the two "not shown" lines, and the step runner driven with an unresolvable intent → the action-failed line and the "Open
   Android settings" fallback, T12-17).
2. Wizard pages inside `StartActivity`: the host above the pager, the burst layer, the pin band and the picker (T12-8), with the
   pager not composed while it shows (T12-6); the step page with its `wizard_why` line from the Why-lines table (T12-4); "Not
   now" / "Skip setup" / progress; auto-advance on resume; the blocked-permission branch to the app-info page; the action-failed
   branch to Android's Settings home (T12-17); Tess's steps through
   `CortanaPermissionActivity`'s existing paths, owning any request adaptation the foreground-location upgrade before
   `tess:background_location`'s "all the time" needs on the target API (E3 walks it; no `pm grant` shortcut, r3 V5); the drawn bars
   and the Back / Windows / Search rules; the `[motion] wizard_page`
   line (C-5, with C-31's `frames` / `maxGapMs`); test tags. The Setup rows' actions (r3 D4): pull `ChecklistPage`'s `rows` (today a
   local of the composable closing over its `rememberLauncherForActivityResult` launchers, `onboarding/Checklist.kt:88-89,92-144`)
   into `Checklist.rows(context, requestRole: (Intent) -> Unit, requestPermissions: (Array<String>) -> Unit): List<ChecklistRow>` —
   same ids, order and actions; `ChecklistPage` calls it with its own launchers, the wizard with launchers registered in its host
   composable inside `StartActivity` (an ADD to phase 01's part, INDEX Change Log when built; `CortanaChecklist.rows(context)` is
   already a plain function). The ring read (r3 V4): `StartActivity.dump(...)` exposes the launcher's existing `Diagnostics.dump`
   through the already-exported activity — no new exported component — so the ring can be read while notification access is
   revoked (the listener's service dump, `feeds/TileNotificationListener.kt:91-94`, needs a running listener; an adversarial-review
   surface, Decisions). The "Start visible" gate (T12-14): the `SecondaryTiles` pin band (drawn
   ungated today, `StartActivity.kt:253-256`, r3 D13) and phase 14's `PodBayRequests` consumption both wait until Start is visible — after
   `[wizard] finished`, `[wizard] skip` or a `[wizard] not shown` line — so neither draws or moves behind the wizard.
3. Presets (T12-2): one shared presets composable drawn by the wizard's last page and by Start + theme (the ADD to phase 01's
   page), the item rows below it (the accent grid pulled out of `StartThemePage`, the other existing rows, phase 13's switch,
   "Tess's look", "Keyboard"), `theme_preset` and the Custom rule (flipped only by the preset's keys, r3 D8), live apply through a
   narrower write that puts only the preset's keys — never `ShellSettings.update()`'s every-key rewrite
   (`prefs/ShellSettings.kt:76-93`, r3 D8), the new keys and their readers — phase 03's persona
   (`tess_lens` ∈ {`hal`, `accent`, `hal_dim`}, the re-hue / × 0.5 derivations under the preset table, r3 D7; no `tess_ring`, r3
   D3) and phase 05's keyboard (`keyboard_palette` through `KeyboardConfigProvider`) — the `Brand` names
   (T12-7), diagnostics; Cobalt as the 49th swatch of the shared grid (Q7 A, `ui/tokens/Palette.kt:10-19` gains it); the original
   preset's `theme_preset_variant` and its chips (T12-10); HAL's accent Red (T12-11); the Custom snapshot in its own
   `SharedPreferences` file `theme_custom` (r3 D9) with its grant handling and lifetime — the user's persisted grant kept while
   the snapshot or the live keys reference it, released only for a `content://` grant the shell took that nothing references,
   never on an `android.resource://` URI (T12-12; r3 D15, V10); INDEX Change Log lines for phases 01, 03 and 05 when built, and,
   when this phase lands, the line R12 §7.1 asks for: R3's
   accent-coloured readings (A19 action-center active tile, A20 volume fill, A22 Cortana ring, the keyboard cursor dot) are
   renditions of Cobalt #3E65FF, not "#0078D7 plus a capture offset"; phase 04's DRAFT (its A19 / A20 derivations) is re-read before
   its interview, and phases 03 / 05's FINAL colour rows are re-checked at their re-runs (T12-15 (a)).
4. Harness (C-4): `provision.sh` grants the Setup rows (`cmd notification allow_listener …`, `appops set app.tileshell
   GET_USAGE_STATS allow`, `ime enable` / `ime set`) on top of what it already grants — Tess's nine through `adb install -r -g`
   and the ASSISTANT role line — and each later phase appends its line; then the finished-marker step by the one mechanism in the
   lead's Decision (force-stop, push `qa/phase-12/fixtures/setup_wizard.xml`, `run-as` cat into `shared_prefs/`, `ime enable` +
   `ime set`, Home), skipped when `PROVISION_FINISH_WIZARD=0` (C-15); the fixture file itself; `qa/phase-12/scripts/` with `lib.sh` symlinked; the
   regression run (one row per earlier phase, `regress.sh` pattern) proving no driver meets the wizard; E14's template proven on
   `setup:usage`; INDEX Change Log lines for phase 01 (the `ChecklistRow` ADDs, the shared accent grid) when built. Round-3 helpers
   under `qa/phase-12/scripts/`, none importing anything from the app: (i) the Start ring read (r3 V4) — `ring_since`'s `wall=`
   slicing and saved evidence over `adb shell dumpsys activity app.tileshell/.StartActivity` (build task 2's dump); every wizard
   ring read first asserts the header `tileshell diagnostics: <n> entries` (`diag/Diagnostics.kt:38`) is present, and empty or
   unavailable output is FAIL, absence checks included; E2 / E5 / E6 / E7 / E10 read through it while notification access is
   revoked. (ii) The picture oracle (r3 V7): decodes the shipped WebP and maps it independently to Start's measured viewport —
   `ContentScale.Crop`, centred 1.3× scale, `translationY = −scroll × 0.25` at scroll 0 (`start/StartPage.kt:563-570`) —
   validated against several visible gutter patches; at each glyph-free tile patch its picture pixels at the same coordinates are
   B; it takes the preset's α as a literal from this spec (never the app's computed alpha), requires usable patches and enough
   picture / accent separation (missing samples FAIL), and keeps the opaque / missing-picture negative control. (iii) The
   preset-colour checker (r3 V8, D3): a still-image checker, separate from `persona.py` (whose phase-03 interface is untouched:
   it hard-codes HAL red, filters `r > g > b` and reads a frame directory, `qa/phase-03/scripts/persona.py:44,72,199`), that
   takes the expected lens hue AND brightness as independent inputs (so Midnight's half-brightness red is told from HAL's),
   measures the named lens regions (rim, iris, glow), reports sampled values and sample counts per region, and fails on a
   missing region; negative controls: a wrong hue, and Midnight's lens checked against the undimmed HAL expectation. (iv) The
   grant-inspection helper (r3 V10): lists the shell's persisted URI grants for E11's snapshot-grant sub-row.
5. Pictures (T12-2, T12-7): a host script under `tools/` crops and scales the four picked variants `art/themes/hal-a.png`,
   `lumia-b.png`, `midnight-b.png`, `soft-c.png` (soft-c read as JPEG; r3 D1) — and, per Q8 C, R12's two img0
   files (`docs/plan/r12/img0_w10m_1607-1709.jpg` → `preset_w10m_hero`, `img0_w10m_1507-1511.jpg` → `preset_w10m_streaks`) — to
   1872 × 4056: centre crop (scaled to fill the height, the width trimmed equally), lossy WebP q 90, no metadata (r3 D10), logging
   each file's scale factor. Its six outputs are COMMITTED at `app/src/main/res/drawable-nodpi/preset_{hal,soft,lumia,midnight,
   w10m_hero,w10m_streaks}.webp` (≈ 0.7 MB in all); the script is run by hand and re-run when a source changes; apk.yml is
   unchanged (nothing to fetch), so the CI runner builds them in without running it (r3 D11). "Never assumed committed"
   (T12-13) now refers to the SOURCES only, which are tracked (`docs/plan/r12/img0_*.jpg`, the four picked `art/themes/`
   files). The runtime no-picture fallback (a picture that cannot be decoded, r3 V2) and its line; E12's size bounds and
   sha256 check.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 on the shell's own clock
(below); dumps follow RV13. "Provisioned" means `qa/phase-03/scripts/provision.sh` (with this phase's lines, Build task 4) on a
wiped AVD (tileshell_fhd, 1080×2340 @ 450 dpi, AOSP API 36, no Google); it ends by writing the finished marker, and
`PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` provisions every grant without it (C-15). "Diagnostics" is read with
phase 01's command, or through the Start ring read where notification access is revoked (Ring reads below; r3 V4).
**Ring reads (C-20):** every ring assertion reads `ring_since` from a MARK (`adb shell date +%s%3N`) taken immediately before the
step's action (after any clock jump, so the MARK is on the new clock); absence assertions read the same slice; `reply_text` is
`reply_since <MARK>` (helpers: phase 11's build task 7). In every missing-grant row — E2, E5, E6, E7, E10, and any other row read
from the E2 state or with notification access revoked (E8's pin-band slice included) — the wizard's ring is read through build
task 4's Start ring read (`dumpsys activity app.tileshell/.StartActivity`, the same
`wall=` slicing), which first asserts the `tileshell diagnostics:` header — empty or unavailable output is FAIL, absence checks
included (r3 V4). **Wake (C-25):** after any `adb reboot` (boot-completed poll), `dumpsys
battery unplug` or `KEYCODE_SLEEP` step, the driver calls `wake_device` and asserts it printed `Awake` before the next tap (battery
saver goes on and off only through phase 13's `battery_saver_on` / `battery_saver_off`, C-18). **Recorded clauses (C-26):** a
recorded clause uses `lib.sh` `record`, never an assert (phase 13's build task 7). **After a force-stop (C-15, phase 05 N-01):** a
row that asserts a full-green checklist after an `am force-stop` runs `ime set app.tileshell/.ime.KeyboardService` first, since
the stop deselects the keyboard.
**Tile-over-picture pixels — the picture oracle (T12-16; re-cut 2026-09-28 by r3 V7, which replaces the gutter-pair rule: no
column of the 342.75-px medium `slot:PEOPLE` tile, `ui/tokens/StartGrid.kt:9-14`, is within 12 px of both exterior gutters —
this corrects T12-16's applied text, not its requirement of independent picture evidence):** wherever a row reads the
`slot:PEOPLE` tile band T against the picture (E3, E11, E13), T comes from one dump and screencap of Start (phase 01's
`e13_pixels.py` locates the band) and B from build task 4's picture oracle: the shipped WebP decoded and mapped independently to
Start's measured viewport (`ContentScale.Crop`, centred 1.3× scale, `translationY = −scroll × 0.25` at scroll 0;
`start/StartPage.kt:563-570`), the mapping first validated against several visible gutter patches. At each selected glyph-free
tile patch, B = the oracle's picture pixel at the same coordinates, and the row asserts T = α · accent + (1 − α) · B ± 4 with α
the literal from the preset table (never the application's computed alpha); usable patches and enough picture / accent
separation are required, missing samples are FAIL, and the patches, their B values and the gutter validation are logged. A
second assertion, T ≠ the accent ± 4, fails the row if the picture is missing (then α = 1) — the opaque / missing-picture
negative control.
`pm clear app.tileshell` is the fresh-install state; every row that needs a grant absent also revokes it explicitly (`cmd
notification disallow_listener …`, `appops set … GET_USAGE_STATS ignore`, `appops set … USE_FULL_SCREEN_INTENT ignore` and
`… SYSTEM_ALERT_WINDOW ignore` (phase 15's two, r3 D6 / V3), `pm revoke app.tileshell <permission>`, `cmd role
remove-role-holder android.app.role.ASSISTANT app.tileshell`, `ime disable …`) so no row depends on what `pm clear` resets
(checked at build start and recorded here). Every row that ends on a fresh state ends "`pm clear` → `provision.sh` → Home"
before the next row (C-4 (d)).
**The E2 state** (E2–E7 start from it): `pm clear`, then `cmd notification disallow_listener
app.tileshell/app.tileshell.feeds.TileNotificationListener`, `appops set app.tileshell GET_USAGE_STATS ignore`, `appops set
app.tileshell USE_FULL_SCREEN_INTENT ignore` and `appops set app.tileshell SYSTEM_ALERT_WINDOW ignore` (phase 15's
`setup:full_screen_alarms` and `setup:overlay`, set explicitly rather than left to whatever `pm clear` resets; `appops get` reads
`ignore` for both before Home, r3 D6 / V3), `ime disable
app.tileshell/.ime.KeyboardService`, `cmd role remove-role-holder android.app.role.ASSISTANT app.tileshell`, and `pm revoke
app.tileshell android.permission.<P>` for each of READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED, READ_MEDIA_AUDIO,
READ_CALENDAR, WRITE_CALENDAR, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, RECORD_AUDIO,
READ_CONTACTS, SEND_SMS, CALL_PHONE, READ_CALL_LOG, READ_SMS; the Home role is kept, so Start is what draws.
**Seeding (C-3):** rows that read Start's grid (E3, E11, E13) first run `layout_restore qa/phase-02/baseline_layout.json`
(`qa/phase-02/scripts/layout.sh`) — this phase adds no `addedOnce` marker and pins no fixture, so phase 02's file, with its
markers and hand-set sizes, is complete for this build — and assert zero `assignSlotOnce … -> assigned` lines after it.
**Visual measurements after a preset (r3 V6):** the layout is seeded BEFORE the preset action, never between the action and its
first visual assertion (`layout_restore` force-stops the shell and presses Home, `qa/phase-02/scripts/layout.sh:21-26` — after a
tap it would restart an unfinished run and let an "apply only after restart" build pass a live-apply row). Wizard side (E11 (a),
E13 (a)), per preset / variant trial: `pm clear` → `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` (every grant, no
marker) → `layout_restore` (then `ime set …`, its force-stop having deselected the keyboard) → `appops set app.tileshell
GET_USAGE_STATS ignore` → Home → `wizard_not_now` through the steps to `wizard_presets`; assert `cmd role get-role-holders
android.app.role.ASSISTANT` prints `app.tileshell`; tap the preset, save its immediate `start_theme.xml` and ring slice, then tap
`wizard_done` and capture Start and Tess with no force-stop, reinstall or preference injection — Start's PID (`pidof
app.tileshell`) unchanged across the capture; the fixture is recreated for the next wizard-side trial. Settings side (E11 (b),
E13 (b)): seed once before the pass, and after each preset tap return to the running Start (Back / Home, no restart) for the
capture. Restart-persistence checks run only after the live-apply assertions; `kb_end` follows every keyboard probe; each row
still ends "`pm clear` → `provision.sh` → Home".
**Motion clock (C-5):** every motion the shell animates logs its own clock from `withFrameNanos` (`[motion] <name>
t0=<uptime> settle=<ms> frames=<n> maxGapMs=<ms>`) and the row asserts the logged numbers against RV11's tolerance, and `maxGapMs`
≤ 33.4 ms (2 vsync, C-31); a screenrecord corroborates under
phase 05's frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion) and is never the primary clock.
**Emulator:**
- E1 Absence, both provisioning routes (re-cut 2026-09-23 by r2 triage C-15): (a) provisioned AVD (`pm clear` → `provision.sh`,
  marker written), MARK, `adb shell am start -n com.android.deskclock/.DeskClock`, `adb shell input keyevent KEYCODE_HOME` (a
  resume, where the Visibility rule is evaluated and its line written): `uiautomator dump` has `start_page` and no `wizard_page`; the
  slice carries `[wizard] not shown: core held` (precedence: every core row held, marker or not); then MARK, `adb shell am
  force-stop app.tileshell` + `KEYCODE_HOME` → still no `wizard_page`, the slice carries `[wizard] not shown: finished`, `adb shell
  settings get secure default_input_method` ≠ `app.tileshell/.ime.KeyboardService` and the Setup page shows
  `checklist:keyboard_selected:missing` (the force-stop deselected the keyboard, phase 05 N-01 — the case the marker exists for);
  `adb shell ime set app.tileshell/.ime.KeyboardService` restores. (b) `pm clear app.tileshell`, then every grant made from adb alone — roles
  with `cmd role add-role-holder android.app.role.HOME app.tileshell` and `cmd role add-role-holder android.app.role.ASSISTANT
  app.tileshell`, `cmd package set-home-activity`, `allow_listener`, `appops set app.tileshell GET_USAGE_STATS allow`, `appops set
  app.tileshell USE_FULL_SCREEN_INTENT allow`, `appops set app.tileshell SYSTEM_ALERT_WINDOW allow` (phase 15's two,
  `provision.sh:52-53`; r3 D6 / V3), `pm grant`
  for READ_MEDIA_IMAGES / READ_MEDIA_AUDIO / READ_CALENDAR / ACCESS_COARSE_LOCATION (Setup) and RECORD_AUDIO, READ_CONTACTS,
  WRITE_CALENDAR, SEND_SMS, READ_SMS, CALL_PHONE, READ_CALL_LOG, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION (Tess, T12-1),
  `ime enable` + `ime set` — then Home: (a)'s first two assertions (`start_page`, no `wizard_page`; `[wizard] not shown: core
  held`) — a device provisioned by adb that never ran the wizard never
  sees it — and the `setup_wizard` file does not exist (`run-as app.tileshell ls shared_prefs`). Every later phase that adds a
  grant re-runs E1 on its build with its grant in both routes (C-4 (c)).
- E2 Appearance, order and why, against a hand-written list (T12-1, T12-4): the E2 state, Home: the dump has `wizard_page`,
  `wizard_step:setup:notifications` (Home is held, so it is not a step), no `start_page` node (the pager is not composed, T12-6),
  no `applist_*` node; `wizard_progress` reads "Step 1 of 20"; diagnostics `[wizard] shown: missing=…` (through the Start ring
  read, r3 V4) names exactly these nineteen ids — written here, never read from either checklist's dump, which is the wizard's
  own source: `setup:notifications`,
  `setup:photos`, `setup:music`, `setup:calendar`, `setup:location`, `setup:usage`, `setup:keyboard_enabled`,
  `setup:keyboard_selected`, `setup:full_screen_alarms`, `setup:overlay` (phase 15's two, at their `Checklist.kt:135-140`
  position; r3 D6 / V3), `tess:assistant`, `tess:microphone`, `tess:contacts`, `tess:calendar`, `tess:sms_send`,
  `tess:call_phone`, `tess:background_location`, `tess:call_log`, `tess:sms_read` (19 + the presets page = 20). Tapping
  `wizard_not_now` through the run records the step ids in exactly that ORDER and then `wizard_presets`, and on every step page
  the `wizard_why` text equals the Why-lines table's line for that id; a swipe left on the wizard page (`input swipe 950 1200 200
  1200 250`) changes nothing in the dump. Then `pm clear` → `provision.sh` → Home. The id list is extended by each later phase's
  C-4 line (the id at its `Checklist.kt` position, its N with it), and E2 is re-run with the full list at the end-of-build pass (T12-18).
- E3 The real grants through the real pages and dialogs, then a preset: the E2 state after `layout_restore
  qa/phase-02/baseline_layout.json`; walk every step with `wizard_action`. Setup: Notification access → `dumpsys activity
  activities` shows `com.android.settings` resumed (the listener detail page); tap its toggle by dump bounds and Back → the next
  step and `[wizard] step setup:notifications: granted`; Photos, Music, Calendar, Location → `com.android.permissioncontroller`'s
  dialog each, allowed by dump bounds; Usage access → the Settings usage page, toggle, Back; Keyboard enabled →
  `Settings.ACTION_INPUT_METHOD_SETTINGS`, enable, Back; Keyboard selected → the system picker, choose the shell keyboard;
  Full-screen alarms (r3 V3) → `com.android.settings` resumed on the full-screen-intent page
  (`Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`), its toggle by bounds, Back → `appops get app.tileshell
  USE_FULL_SCREEN_INTENT` reads `allow` and `[wizard] step setup:full_screen_alarms: granted`; Display over other apps →
  `com.android.settings` resumed on the overlay page (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`), its toggle by bounds, Back →
  `appops get app.tileshell SYSTEM_ALERT_WINDOW` reads `allow` and `[wizard] step setup:overlay: granted`. Tess
  (T12-1): `tess:assistant` → `com.android.settings` resumed on the assist & voice input page (`ACTION_VOICE_INPUT_SETTINGS`;
  there is no role sheet), choose the shell as the digital assistant by dump bounds, Back → `cmd role get-role-holders
  android.app.role.ASSISTANT` prints `app.tileshell` and `[wizard] step tess:assistant: granted`; microphone, contacts, calendar,
  texts, calls, call log, read texts → each its runtime dialog, allowed by bounds — or no dialog where Android grants from a
  permission group already held (READ_SMS after SEND_SMS; `tess:calendar` asks only for WRITE_CALENDAR, READ being held from
  `setup:calendar`) — the row records which, and every step advances with its `granted` line; `tess:background_location` (r3 V5:
  the E2 state revoked FINE and `setup:location` asks for COARSE only, `onboarding/Checklist.kt:111-112`): first `dumpsys package
  app.tileshell` shows ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION not granted; tap `wizard_action` ("Allow all the
  time") → `CortanaPermissionActivity` asks the foreground permission first (`cortana/CortanaPermissionActivity.kt:60-69`) —
  complete the precise-location request in `com.android.permissioncontroller`'s dialog by bounds; on return FINE is granted,
  BACKGROUND absent, the same step still shows (`wizard_step:tess:background_location`) and the slice holds `[wizard] step
  tess:background_location: partial` (FINE only reads PARTIAL, `CortanaChecklist.kt:65-67`); tap `wizard_action` again →
  `com.android.permissioncontroller` resumed on the app's location page, "Allow all the time" by bounds, Back → `dumpsys package
  app.tileshell` shows `android.permission.ACCESS_BACKGROUND_LOCATION: granted=true` and `appops get app.tileshell FINE_LOCATION`
  reads `allow` (not `foreground`), Tess's page reads `cortana_check:background_location:granted`, and `[wizard] step
  tess:background_location: granted` with the wizard advanced — no `pm grant` shortcut on this leg (build task 2 owns any request
  adaptation). Every step page's `wizard_why` equals
  its table line. The presets page: `wizard_presets` holds `preset:Default`, `preset:Windows 10 Mobile (original)`,
  `preset:HAL`, `preset:Soft`, `preset:Lumia`, `preset:Midnight` in that order with `preset:Default` `selected="true"`; tap
  `preset:HAL`, then `wizard_done`: `start_page` is back; `run-as app.tileshell cat shared_prefs/start_theme.xml` holds
  `theme_preset` = `hal` and `accent` = 4293398819 (Red 0xFFE81123, T12-11); T and B by the picture oracle (T12-16, r3 V7), T =
  0.72 · accent + 0.28 · B ± 4 with accent = Red (α = 1 − 0.8 · 0.35; R12 §0's method), and T ≠ Red ± 4 (the picture is there);
  the ring slice from a MARK before the HAL tap holds, in this order, `[wizard] preset HAL`, `[theme] preset HAL applied`, `[wizard]
  finished`; Settings > Setup checklist shows
  `checklist:<id>:granted` for all eleven Setup rows (r3 V3) and Tess's Settings page shows `cortana_check:<id>:granted` for her
  nine.
  Restore: Settings > Start + theme, tap `theme_preset:Default` (selected; `theme_preset` = `default`), then `pm clear` →
  `provision.sh` → Home.
- E4 Persistence and resume: from the E2 state grant the Photos step through the dialog, then `adb shell am force-stop
  app.tileshell`, Home: `wizard_page` again, `wizard_step:setup:notifications` first, `wizard_progress` reads one fewer N, no
  `wizard_step:setup:photos` anywhere in the run (tap `wizard_not_now` through to the end and record every step id seen); the
  same after `adb reboot` + `adb wait-for-device` + boot-completed poll + `wake_device` (asserting it printed `Awake`; it also
  dismisses the keyguard, C-25) in place of the force-stop.
- E5 Skip, and the marker's rules (every diagnostics read through the Start ring read, r3 V4): from the E2 state tap
  `wizard_skip`: `start_page` in the dump, diagnostics `[wizard] skip`,
  `checklist:notifications:missing` on the Settings page and `cortana_check:microphone:missing` on Tess's (Skip covers both
  lists), `shared_prefs/setup_wizard.xml` holds `finished` true; `am force-stop` + Home → no `wizard_page`, `[wizard] not
  shown: finished`; `adb install -r <same apk>` + Home → still none; `cmd notification allow_listener …` then `disallow_listener`
  again (a core grant revoked later) + Home → still none; `pm clear` + `disallow_listener` + Home → `wizard_page` again. Second
  half: from a fresh E2 state tap `wizard_not_now` on every step and `wizard_done` on the presets page → Start, marker set, next
  launch no wizard. Then `pm clear` → `provision.sh` → Home.
- E6 Denied for good: from the E2 state, on the Photos step tap `wizard_action` and the dialog's deny button twice across two
  tries (Android 11+: the second denial stops the prompt; `qa/phase-03/scripts/micperm.sh` is the precedent): the button's text
  becomes "Open app info" (`wizard_action` text), diagnostics (Start ring read, r3 V4)
  `[wizard] step setup:photos: blocked (app info)`; tapping it resumes `com.android.settings` on the shell's app-info page (`dumpsys activity activities`); Back returns to the same step. On
  `tess:microphone` the second deny opens the app-info page at once (`CortanaPermissionActivity.kt:33-38`; the slice holds
  `[cortana] permission blocked by Android (no prompt shown): [android.permission.RECORD_AUDIO]; opening app info`); Back → the
  step shows "Open app info" and `[wizard] step tess:microphone: blocked (app info)`; tapping it resumes app info again; Back
  returns to the step (r3 D12). Restore through `pm clear` → `provision.sh` → Home.
- E7 Revoked mid-run: from the E2 state advance past Notification access (grant it) to the Photos step, then from adb `cmd
  notification disallow_listener …` and `am start` the Settings hub and Back (a resume): `wizard_progress` N has grown by one and
  diagnostics (Start ring read, r3 V4) `[wizard] shown` is not repeated (same run); walking on with `wizard_not_now`,
  `setup:notifications` is seen again exactly once, before `wizard_presets` (it rejoins the steps not yet shown, T12-1 (d)); a grant made from adb while a step shows
  (`pm grant app.tileshell android.permission.READ_MEDIA_IMAGES` on the Photos step) advances that step on the next resume, with
  `[wizard] step setup:photos: granted`. Then `pm clear` → `provision.sh` → Home.
- E8 Keys and the pivot while the wizard shows: on the first step `input tap` on `nav_back` bounds changes nothing (dump
  identical); on step 2 it returns to step 1; `nav_windows` changes nothing; `input keyevent KEYCODE_HOME` leaves the wizard in
  place; `nav_search` opens Tess (`cortana_session` in the dump) and Back returns to the wizard at the same step; `dumpsys window`
  shows the system status and nav bars not visible (phase 01 E19's form) and the drawn bars measure (phase 01's drawn status bar
  at `BarMetrics.STATUS_EPX` epx — C-17, never a literal 28 — 48-epx nav, X17 slots). Pin band under the wizard (T12-14; the
  split-time edge case): with tileclient-a installed from its debug APK (as `qa/phase-02/scripts/e5.sh:17,25` installs it), from the
  E2 state with the wizard showing, `adb shell am start -n
  app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity --es verb secondary.requestCreate --es tileId st1 --es
  displayName Jen --es arguments chat=42 --es size medium --ez logo true` (phase 02 E5's request, `e5.sh:32-33`, `:58`), then Home →
  the dump has `wizard_page` and no `secondary_pin_prompt`; MARK, `tap_node wizard_skip` → the slice holds `[wizard] skip` and the
  dump shows `secondary_pin_prompt` over `start_page`; cancel the band (its cancel button, `e5.sh`'s `button … cancel`), then `pm
  clear` → `provision.sh` → Home.
- E9 Regression, the `regress.sh` pattern: on a freshly provisioned AVD run, unchanged, phase 01 E2 and phase 01 E14
  (`disallow_listener` flips the checklist row — re-proving the moved row list on the page that owns it, r3 D4), phase 02 E1
  (hold + drag), phase 03 E1 and E5, phase 05 E1 and phase 10 E2; every one passes, no dump any of them saved contains `wizard_page` (grep the
  row directories), the ring holds zero `assignSlotOnce … -> assigned` lines after each of their `layout_restore` calls (C-3), and
  `qa/phase-03/scripts/exported.py` against `qa/phase-03/exported-allowlist.txt` reports no new exported component.
- E10 Motion, on the shell's clock (C-5): each step-to-step transition logs `[motion] wizard_page t0=<uptime> settle=<ms>` (fields
  read by key — the built line also carries `peak=` and `overshoot=`, r3 D13; read through the Start ring read, r3 V4); three
  consecutive transitions read settle = 217 ms ± one frame (16.7 ms), phase 01 X7's Start entrance form, alpha complete in 217
  ms (RV11), and `maxGapMs` ≤ 33.4 ms on each (C-31), each line from the slice after a MARK taken just before its tap (C-20); `wizard_done` → Start logs the same line with the same settle (Start composes under the X7 motion, T12-6); a 60-fps
  screenrecord corroborates under phase 05's frame-spacing rule and is not the clock; the feel is judged in H1.
- E11 Presets, on both surfaces (T12-2): (a) the wizard's presets page (the wizard-side fixture of "Visual measurements after a
  preset" above: every grant provisioned without the marker, Usage access revoked, `wizard_not_now` to the presets, recreated for
  each preset; r3 V6 — the split-time "the E2 state" route left ASSISTANT absent and had no way to Start) and (b)
  Settings > Start + theme after a run (`theme_presets`, tags `theme_preset:<name>`; seeded once before the pass, r3 V6), the same
  assertions on each, every ring
  read-back from a MARK taken just before that tap on that surface, so (b) is never satisfied by (a)'s lines (C-20). Control first:
  from `pm clear` → `provision.sh` → Home, save `start_theme.xml` (the out-of-box values) and assert `theme_preset` is absent or
  `default` (provisioning writes the wizard marker but must never apply a preset; C-15). For each of the six in turn, tap it and
  read back the item keys: `start_theme.xml` holds that preset's row of the table — `theme_preset`, `accent`, `theme`, `background`
  (absent, or `android.resource://app.tileshell/drawable/preset_<name>`; for the original `…/preset_w10m_hero` with
  `theme_preset_variant` = `hero`, E13 covering the variant), `transparency`, `press`, `tess_lens` (the string — `hal`, `accent`
  or `hal_dim`, r3 D7; no `tess_ring` key, r3 D3),
  `keyboard_palette`, `transparency_effects` (false for Midnight and the original; phase 13's key, its E1 sub-row reads the
  `[fluent]` line) — and the slice holds `[theme] preset <name> applied` (plus `[wizard] preset <name>` on (a)). Start — seeded
  before the tap, captured after `wizard_done` on (a) and after Back / Home to the running Start on (b), Start's PID unchanged, no
  force-stop (r3 V6) — T and B by the picture oracle (T12-16, r3 V7): Default (no
  picture): T = the accent ± 2 and a gutter pixel = (0,0,0) ± 2; Midnight (transparency 0.0, α = 1): T = the accent ± 2; every
  other preset with a picture: T = α · accent + (1 − α) · B ± 4 with α = 1 − 0.8 · `transparency` (the table's literal;
  `start/StartPage.kt:429`, r3 D13), and T ≠ the accent ± 4 (fails if the picture is missing, then α = 1); the original's picture
  row is E13's. Tess (r3 D3, V8): over that running Start, `KEYCODE_ASSIST` opens her session (ASSISTANT held, V6), the persona
  idle at reveal 0 (not tapped — three taps open the reveal, `cortana/ui/Lens.kt:35`), one screencap after the session's entrance
  settles; build task 4's preset-colour checker, given the preset's expected lens hue and brightness as literals from this spec —
  `hal` (Default, HAL): HAL red at `Brand.LENS_*`'s own brightness; `accent` (the original, Soft, Lumia): the accent's hue at the
  HAL tones' brightness (Cobalt's hue for the original); `hal_dim` (Midnight): HAL red at half brightness — finds each named lens
  region (rim, iris, glow) and reports its sampled values and counts; a missing region FAILs. The ring-colour sample of T12-19 is
  struck — the persona's ring is lens-painted (`Lens.kt:100-120`). Keyboard: phase 05's `kb_begin` + `kb_dump`
  (`qa/phase-05/scripts/kb.sh`) over its
  fixture field; a letter key's fill reads DARK (48,48,48) ± 4 — phase 05 E3's letter-key fill (phase 05 Decisions, R6 §2.1.18) —
  or LIGHT #E6E6E6 = (230,230,230) ± 4 (T12-19); then `kb_end` (r3 V6). Custom: tap `preset:Lumia` (accent Seafoam — not Red;
  HAL's accent is Red since T12-11, so a Red tap from HAL would change nothing), then `accent:Red` among the
  items → `preset:Custom` (`theme_preset:Custom` on (b)) `selected="true"`, `theme_preset` = `custom`, `accent` = 4293398819,
  and every OTHER item key unchanged from Lumia's; after re-tapping a preset, toggling `theme_transparency_effects` gives Custom
  the same way; the control (r3 D8), on (b): after re-tapping a preset, toggle `theme_show_more_tiles` → `theme_preset` unchanged
  (`columns` is not a preset key), then toggle it back. Custom snapshot (T12-12), on (b): with the shell stopped,
  `qa/phase-01/scripts/prefs_edit.py` writes `background` = phase 13's checkerboard fixture's MediaStore URI as `push_picture` returns
  it (`content://media/…`, `qa/phase-13/scripts/p13.sh:28-44`; r3 V10 — not a `file://` URI) and `theme_preset` = `custom` (the route
  `qa/phase-01/scripts/item4.sh` uses), Home, save `start_theme.xml` (the pre-HAL state); `run-as app.tileshell ls shared_prefs`
  shows no `theme_custom.xml`; MARK, tap `theme_preset:HAL` →
  `background` = `…/drawable/preset_hal` and `ls shared_prefs` now shows `theme_custom.xml` (r3 D9); MARK2, tap
  `theme_preset:Custom` → the slice from MARK2 holds `[theme] preset Custom
  restored`, `background` = the checkerboard URI again and every item key equals the saved pre-HAL state; the checker shows on Start
  (a checker edge in a gutter column, sharp). Tapping `preset:Default` makes every item key equal the control's. Custom grant
  (r3 V10), on (b), its own sub-row: choose fixture A through the real picture picker (`theme_background_choose`; the picker's
  result takes the persisted grant, `settings/StartThemePage.kt:48-51`) and verify A's persisted read grant with build task 4's
  grant-inspection helper; tap `theme_preset:HAL` (A's grant still held — the snapshot references it), then, after that tap's
  live-apply assertions, `am force-stop app.tileshell` and reopen Start + theme; tap `theme_preset:Custom` → A decodes and renders
  on Start with the custom values restored, and A's grant is still held; choose fixture B the same way, then replace the snapshot
  by tapping a preset → A's grant is released (neither `background`, `photo_frame` nor the snapshot references A) while B's is
  retained. A and B are fixtures no broad media permission can read, so a missing persisted grant cannot be masked. Restore the
  grants, the fixtures and `preset:Default`. Persistence, on (b) after the run finished and after the live-apply assertions
  (r3 V6): `am
  force-stop app.tileshell`, reopen Start + theme → the keys and the selected preset unchanged. Restore: `preset:Default`, then
  `pm clear` → `provision.sh` → Home.
- E12 Pictures (T12-2): on the host, the four picked sources (Decisions 2026-09-28) read by `identify`: `art/themes/hal-a.png`,
  `lumia-b.png`, `midnight-b.png` = PNG 941 × 1672 and `soft-c.png` = JPEG 1536 × 2752 (JPEG data under a .png name; the brief's
  1024-px floor is waived for the three 941-px files by Jeremy's pick — recorded, not asserted), and the host script logs each
  file's scale factor (≈ 2.43× for the three, ≈ 1.47× for soft-c, ≈ 2.64× / ≈ 2.11× for the hero / streaks img0) (r3 D1); each
  committed WebP's sha256 equals the script's output on the same sources (the script run to a temp dir and compared, r3 D11); in
  the built APK, `unzip -p <apk> 'res/drawable-nodpi*/preset_<name>.webp' | identify -` reads 1872 × 4056 for each of the six —
  `preset_hal`, `preset_soft`, `preset_lumia`, `preset_midnight`, `preset_w10m_hero`, `preset_w10m_streaks` — and an APK that
  lacks any one FAILs (ruling (a); the no-picture branch is not an accepted outcome for a CI or dev build, r3 V2); `unzip -l`
  shows no preset asset ≥ 2.5 MB and their sum ≤ 8 MB (measured ≈ 0.7 MB at q 90, r3 D10), and the APK is ≤ 8 MB larger than the
  same commit built without them; with phase 13 present (it is, T12-3), on surface (b) for HAL, Soft and Lumia — the presets that
  change the picture and leave `transparency_effects` true — after the tap, Home and a swipe to the app list (the layer is built
  when the app-list page composes with its size and only while acrylic is on, `ui/fluent/StaticBackdrop.kt:195-200`,
  `applist/AppListPage.kt:290`), the slice from the MARK before the tap (C-20) holds `[fluent] static backdrop rebuilt for
  android.resource://app.tileshell/drawable/preset_<name> in <ms> ms`; for the original and Midnight the same steps yield no
  `rebuilt` line and `[fluent] acrylic=off reason=setting` (phase 13 E1's sub-row, T13-4); on surface (a) no `[fluent]` line is
  asserted (the pager is not composed under the wizard, T12-6) (r3 D2 / V9).
- E13 The "Windows 10 Mobile (original)" preset against R12 (T12-9, T12-10; Q7 A, Q8 C, Q9 A; re-cut 2026-09-23 from the
  conditional split-time row), on each surface in turn — (a) the wizard's presets page reached as E11 (a) reaches it (the
  wizard-side fixture, recreated for each trial, r3 V6; tags
  `preset:*`, `preset_variant:*`), (b) Start + theme after the run (`theme_preset:*`, `theme_preset_variant:*`; seeded once
  before the pass, r3 V6) — every ring read
  from a MARK taken just before that tap (C-20). Tap the original preset → `start_theme.xml` holds `theme_preset` = `w10m`,
  `theme_preset_variant` = `hero`, `accent` = 4282279423 (0xFF3E65FF), `theme` DARK, `transparency` 0.75, `press` NONE,
  `transparency_effects` false, `keyboard_palette` DARK; `accent:Cobalt` is present with `selected="true"`, and the accent grid
  holds exactly 49 `accent:*` nodes (the 48 plus Cobalt, Q7 A — counted over the dumps that lay out the whole grid, `scroll_to_node`
  to `accent:Cobalt`, de-duplicated by tag); on (a) `wizard_presets` holds exactly six `preset:*` entries (the variant is not a
  seventh preset, Q8); Tess, captured as E11 captures her (over the running Start after `wizard_done` / Back, the persona idle
  at reveal 0): build task 4's preset-colour checker finds the lens on Cobalt's hue line at the HAL tones' brightness
  (`tess_lens` = `accent`, Q9 A); the ring-colour sample is struck (r3 D3, V8). **Pictures, asserted present** (T12-10, T12-13;
  ruling (a), r3 V2 — the split-time "picture branch, chosen from the APK" is struck): `unzip -l <apk>` lists both
  `preset_w10m_hero.webp` and `preset_w10m_streaks.webp`, and a missing one FAILs the row (T12-7's no-picture form is only the
  runtime fallback when a picture cannot be decoded, never an accepted build); `background` =
  `android.resource://app.tileshell/drawable/preset_w10m_hero`, the slice holds `[theme] preset Windows 10 Mobile (original)
  applied`, and T = 0.40 · accent + 0.60 · B ± 4 by the picture oracle (T12-16, r3 V7; R12 §5.1's α) with T ≠ Cobalt ± 4, and
  the dump holds both chips. **The variant**, always run: MARK, tap `preset_variant:streaks`
  (`theme_preset_variant:streaks` on (b)) → `background` = `…/drawable/preset_w10m_streaks`, `theme_preset_variant` = `streaks`,
  the slice holds `[theme] preset w10m variant streaks applied`, `theme_preset` is still `w10m` (a variant change is not Custom)
  and every other item key is unchanged, T by the same rule (on (a) a trial of its own: the fixture recreated, the chip tapped
  before `wizard_done`, r3 V6); tap `preset:Default`, then the original preset again →
  `theme_preset_variant` = `streaks` (the last-chosen variant); tap `preset_variant:hero` → `background` names the Hero again.
  Restore: `preset:Default`, then `pm clear` → `provision.sh` → Home.
- E14 "Wizard step added" — the template every later phase that adds a grant runs for its own step, cited as "phase 12 E14"
  (T12-5, C-4; three parts since the 2026-09-23 re-cut by r2 triage C-15, because `provision.sh` now writes the finished marker):
  (a) `pm clear` → `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` → revoke <grant> (its adb form) → Home: the dump
  has `wizard_step:<ns>:<id>`, its `wizard_why` equals that step's line in the Why-lines table, and `wizard_progress` reads "Step 1
  of 2" (the step and the presets page); grant it from adb and resume (`am start` the Settings hub, Back) → the step is gone and
  `wizard_presets` shows. (b) `pm clear` → `provision.sh` (it carries that grant's line, C-4 (a), and writes the marker) → Home: no
  `wizard_page` and, after MARK and a resume (`am start` the Settings hub, Home), `[wizard] not shown: core held` in the slice (E1
  re-run on that build). (c) The finished-install rule: from (b)'s state (marker set), revoke <grant> → MARK, a resume as in (b): no
  `wizard_page`, the slice holds `[wizard] not shown: finished`,
  and the grant's checklist row reads `missing`; restore the grant. Every instance runs all three parts (a Skip-based
  finished-install half is not used). Known instances, each run in its own doc on its build with the `provision.sh` line C-4 (a) gives it — except
  phase 15's two, which run HERE, at this phase's build, because phase 15 was built before the wizard existed (C-34,
  2026-09-23; phase 15 E28 points here): `appops set app.tileshell USE_FULL_SCREEN_INTENT allow` (step
  `setup:full_screen_alarms`, checklist row `full_screen_alarms`) and `appops set app.tileshell SYSTEM_ALERT_WINDOW allow`
  (step `setup:overlay`, Q-E A); phase 16 `pm grant app.tileshell android.permission.WRITE_CONTACTS` (step
  `setup:people`, T16-15); phase 17
  `pm grant … CAMERA` and `pm grant … READ_MEDIA_VIDEO`; phase 18 `appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` (step
  `setup:files`); phase 19 `appops set app.tileshell WRITE_SETTINGS allow` and `cmd notification allow_dnd app.tileshell`. On this
  phase's own build the template runs once on `setup:usage` (revoke = `appops set app.tileshell GET_USAGE_STATS ignore`; restore
  `… allow`; (c)'s row is `checklist:usage:missing`), so it is proven before any later phase leans on it.

**Phone-only (S25 Ultra):**
- P1 From a fresh sideload (release-signed, `debuggable=false`): the route in is recorded (Settings > Default apps > Home
  app, or Start settings > Setup checklist > Default Home and One UI's default-Home sheet), then Home; each remaining step
  tapped once with the resumed activity recorded from `dumpsys activity activities` (Samsung's notification-access page,
  Usage data access, Samsung's permission controller for Photos / Music / Calendar / Location, Samsung's "Manage keyboards"
  and its picker; then Tess's: One UI's assist page for the digital assistant, the permission controller's dialogs for her
  runtime rows and its location page for "Allow all the time") and the step advancing on return; any step whose action logs
  `[wizard] step <ns>:<id>: action failed …` is recorded with its intent action and the "Open Android settings" page it fell back
  to (T12-17); the preset chosen shows on Start, on Tess and on the keyboard (screencaps for H6 / H7).
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
- H6 Each preset's look, the table's values as built (T12-2), on the phone — including HAL's accent Red beside the exact HAL lens
  (T12-11) and Custom bringing back the user's own set and picture after trying presets (T12-12).
- H7 Labels readable over each picture in its theme, judged on the phone (the NEEDS-HUMAN row Q6 promised).
- H8 The four pictures themselves: an original design with no text, logo, face or watermark (theme-art-brief.md); no metric
  exists.
- H9 [fidelity] The "Windows 10 Mobile (original)" preset against R12's evidence (its screenshots beside the phone's), including
  the Cobalt swatch's placement (the 49th, Q7 A), and the two picture variants and how one is chosen (Q8 C; the chips, T12-10).

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
- `tess:background_location` with both permissions held and device location off (`adb shell cmd location set-location-enabled
  false`; r3 D16): GRANTED also needs `PlaceTriggers.locationEnabled` (`CortanaChecklist.kt:65`,
  `cortana/reminders/PlaceTriggers.kt:79-80`), which no step can turn on, so the row reads PARTIAL (FINE held, `:66`) and never
  summons the wizard by itself; inside a run the step stays after "Allow all the time" (`[wizard] step tess:background_location:
  partial`, `partialIsDone` false) and "Not now" advances (`[wizard] step tess:background_location: not now`); restore `…
  set-location-enabled true`; P1 records the phone's location state.
- The Keyboard selected step: the picker dismissed without a choice (step stays); a force-stop later deselects the keyboard
  (phase 05 N-01) with the marker set: no wizard, the checklist row is the way back.
- The Default Home step when the shell was opened "Just once" from the chooser (Home missing): it is step 1, the role
  sheet is Android's on the AVD and Samsung's on the phone; choosing another launcher there leaves the step (Start stays
  behind the wizard until Home is pressed).
- The wizard while Tess's session is open over Start (E8); while a Live Tile secondary-pin band is pending (the band waits,
  R5 §1.9, until Start is visible — now E8's pin-band sub-row, built by build task 2's "Start visible" gate, T12-14); while a phase 14 pod-bay
  request is pending (it waits the same way, T12-8 / phase 14 T14-7); a burst cannot be open (edit mode is under the wizard).
- Light theme and a Start background set before the run: the wizard follows the theme (screencap recorded).
- ~~The accent step with an accent already changed on the same install (a run started after Settings were used): the grid
  shows that accent selected and "Done" leaves it.~~ Re-cut 2026-09-23 (T12-2): the presets page on an install whose items were
  already changed in Settings: `preset:Custom` selected, and "Done" leaves every item as it was.
- A preset applied under battery saver (`battery_saver_on`, phase 13's `lib.sh` helper, C-18 — a bare `cmd power set-mode 1`
  does nothing while `wake_device` holds the AVD on AC power, and the helper asserts `low_power` = 1): phase 13's rule wins —
  `[fluent] acrylic=off reason=battery-saver` — while `transparency_effects` is still written as the preset says (E11's read-back);
  `battery_saver_off` → acrylic follows the preset's value.
- A preset whose picture cannot be decoded — T12-7's runtime fallback, the only place the no-picture form is accepted (r3 V2):
  fault injection only, with its own evidence id, never the shipping gate (a QA build with `preset_hal.webp` removed, the
  removal verified, the normal APK restored afterwards): the preset applies with no
  picture, `background` absent, and `[theme] preset HAL applied: no picture` (the same line T12-7 gives the original preset).
- ~~A preset tapped over a picture the user chose themself: the preset's picture replaces it and the user's is NOT kept (T12-2);~~
  SUPERSEDED 2026-09-23 by T12-12: a preset tapped over a picture the user chose themself replaces it on screen, and the user's
  set, picture and grant included, is kept as the Custom snapshot and comes back on `preset:Custom` (E11's snapshot sub-row);
  "Remove picture" / "Choose picture" in the items still work afterwards and make the preset read Custom.
- Transparency effects toggled after a preset (E11): `theme_preset` = `custom`, every other key unchanged.
- Work / private profile present: no profile grant is a checklist row, so nothing changes (assert the step list against
  E18's profile setup).
- The AVD's keyguard enabled (`locksettings set-disabled false`, phase 01 E20's setup): StartActivity never shows over the
  keyguard, so the wizard never does either; after unlock it shows as usual.
- A later phase ADDs a core row to a finished install: covered by E5's revoked-later case (same rule); when phase 04 is
  built, its own doc re-runs E5 with its row, and every later phase runs E14 for its step (C-4).

## QA evidence
