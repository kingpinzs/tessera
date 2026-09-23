---
phase: 14
slug: pod-bay
status: DRAFT   # DRAFT → FINAL (only after Stage A step 7; changes after FINAL go through INDEX.md Change Log)
depends-on: [01, 03, 13]
---

# Phase 14 — The pod bay: a pager page left of Start holding at-a-glance pods, and Tess's "open the pod bay doors"

## Goal
Swiping right on Start pans to a third pivot page, the "pod bay", that sits left of Start on the same pager as the app list and
holds at-a-glance cards called "pods": Agenda (phase 01's calendar feed), Weather (its weather feed), Now playing (its music feed,
with transport controls) and Reminders (phase 03's reminder store). Each pod shows what it has, says why when it has nothing, and
opens its app or page on tap. Back and Home from the pod bay return to Start. Telling Tess "open the pod bay doors" makes her answer
"I'm afraid I can't do that, Dave." and then open it anyway; "open the pod bay" opens it plainly; both are refused behind "Unlock to
continue" while the phone is locked. The pod bay draws on phase 13's app-list backdrop material. No new permission, no new process,
no new exported component, no new internet use.

## Scope
**In:** the third pager page and the Back / Home / focus rules it needs in `StartActivity`; the pod frame and the four pods on the
existing feeds; the now-playing transport through `MusicFeed`; a "Pod bay" page in Start settings (one switch per pod); Tess's
command (matcher rule, grammar hotwords, action, lock-gate entry, in-process route to Start, replies) with the HAL strings in the A10
branding module; diagnostics lines and test tags; the harness change for every driver that swipes right to reach Start, and the
phase 01 / 02 / 03 regression re-runs that change owes.
**Out (explicitly):** real Android widgets through `AppWidgetHost` unless interview Q1 rules them in (then the interview adds the
build task and rows named under Q1); any new feed, permission or provider (the pods read what phases 01 and 03 already read);
reordering or editing pods by gesture (pods are configured in Settings); a pod for an app that does not exist yet (an inbox-app phase
may ADD a pod through the same list — an ADD, not a hook); Mail, browser and Maps pods (A11, R10-Q4); a notifications pod (the action
center's job, phase 04); an edge-swipe trigger (the left edge is Android's Back gesture); phase 07's glance screen (W10M's own
"Glance", a different thing; the name collision is why this pane is the pod bay).

## Decisions
- 2026-09-23: Interview Q1 — no real Android widgets in this plan (Jeremy: "(b)"). The pod bay holds the four shell-drawn pods
  (agenda, weather, now playing, Tess's reminders) only. A widget pod, if ever, is an ADD in Jeremy's post-plan updates; nothing
  in this phase pre-builds for it (Hard Rule 16). A8's "optionally real Android widgets" is resolved: out.
- 2026-09-22: Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out thing at a glance thing").
  PLAN.md feature list: "The 'pod bay': a side pane left of Start whose at-a-glance cards are 'pods', opened by a swipe or by telling
  Tess 'open the pod bay doors'." Its content, from the same scope add: "at-a-glance cards (agenda, weather, now playing, Tess's
  reminders) and optionally real Android widgets through AppWidgetHost (those draw themselves and will not look Metro). W10M never
  had one; desktop Windows' later widgets board is the continued-development analogue (R10 item 8)." "Optionally" is not a ruling
  under A8 (everything in the list is in; only order is decided), so widgets are interview Q1 (Jeremy; the Q1 gap by R10 triage)
- 2026-09-22: R10-Q5 The side pane's name (Jeremy: "(a)", asking for "something easter egg like and play on words and AI leaning"):
  "pod bay" — a bay you pull open, continuing the HAL lineage already in Tess's eye (the lens, 2026-09-21). Its cards are "pods"
  (Jeremy: "so each card is called a pod?" — yes: in the film the pods are what the pod bay holds). The easter egg: telling Tess
  "open the pod bay doors" slides the pane open — after she answers "I'm afraid I can't do that, Dave…" and then does it anyway. It
  is a phase 03 command (Tess's offline matcher) wired to the pane, so it lands with whichever phase builds the pane — this one.
  The name and the reply are a film reference, not a mark; "Halo" was considered for the Cortana nod and rejected as a Microsoft
  game trademark (Jeremy)
- 2026-09-22: Agent calls from the R10 plan review (PLAN.md; Jeremy can overrule): the pod bay is a pager page reached by swiping
  right on Start, never an edge swipe — the left edge is Android's Back gesture under gesture navigation, and a pager pan (any x on
  Start) has no conflict (design 25); Back from the pod bay returns to Start, as it does from the app list; drivers that swipe to
  reach Start get a harness note in this doc; order — after phase 13, so the pane has its backdrop from day one (agent)
- 2026-09-22: Pager (agent; `StartActivity.StartHost`): the pager gains a page at index 0 and Start moves to index 1 — pages POD_BAY /
  START / APP_LIST, named constants, initial page START; Back: APP_LIST → START, POD_BAY → START, else `backOnStart()` (the Back
  history rule applies only when Start is the current page — an ADD to phase 01's Back rules, approximation, H8); Home:
  `homeEvents` → START, and the X20 scroll-to-top when Start was already in front, exactly as today from the app list;
  `userScrollEnabled = !edit.active` stays, so edit mode locks the pivot in both directions; the pod bay page carries the same
  `focusGroup().focusProperties { canFocus = pager.currentPage == POD_BAY }` guard as the app list, so nothing on it can swing the
  pager (the folder-name-box defect from phase 02's gate, in its second direction); `beyondViewportPageCount = 1` means the pod bay is
  composed while Start shows — its pods read feeds that already run for the tiles, so that costs drawing only. The swipe motion is
  phase 01's X13 approximation (1:1, 250-ms ease-out settle), already judged in phase 01 H21; nothing new to measure
- 2026-09-22: Bars (agent; phase 01's bar rule): the pod bay is a page of `StartActivity`, so it shares the drawn W10M status and nav
  bars; on it the drawn Back key is Back (→ Start) and the Windows key is Home (→ Start)
- 2026-09-22: Backdrop (agent; R10-Q1 names "the app-list backdrop"): the pod bay is the app list's sibling page and takes the same
  material — phase 13's app-list backdrop, same engine, same parameters — with no wallpaper parallax of its own; hence depends-on 13
  (H10). Where phase 13's rows measure the app-list backdrop, E14 measures the pod bay the same way
- 2026-09-22: Pod frame (P4 design, agent; H2): pods stack in one vertical scroll (`pod_bay_scroll`) between the grid's left and
  right margins, in the fixed order Agenda, Weather, Now playing, Reminders, 24 epx apart; a pod is Metro type on the backdrop, no
  box: a header in the 15-epx class semibold in the accent ("Agenda", "Weather", "Now playing", "Reminders", read from Brand where a
  name is branded), rows in the 15-epx body class in the theme foreground, second lines in the subtle text colour, 12 epx between
  rows; every pod caps its rows (below) and never scrolls inside itself — the page scrolls; an empty pod shows its header and one
  subtle line saying why (H4)
- 2026-09-22: The pods (agent; content per PLAN, layout P4, H3):
  - Agenda — `CalendarFeed` (READ_CALENDAR through the checklist's Calendar row): today's events from now, then tomorrow's under a
    "Tomorrow" subheader, up to 6 rows, all-day first; row = "h:mm  title" ("All day  title"); empty: "Nothing on your calendar
    today"; no access: "Calendar access is off — turn it on in Setup", and a tap on that line opens the Setup checklist; a tap on
    the header or a row opens what the Calendar tile opens (phase 01's CALENDAR slot resolution — when phase 16's Calendar takes
    the slot, the pod follows with no change here)
  - Weather — `WeatherFeed.state.report`: place name, current temperature and condition text (`WeatherFormat`), the day's high /
    low and precipitation, and the X22 "Updated h:mm" line once the report is older than 60 minutes; empty: "No weather yet";
    location off: "Location is off — turn it on in Setup" (tap → the checklist); tap → `WeatherActivity`
  - Now playing — `MusicFeed`'s `MusicRules.Now`: album art one small-tile unit square, title, artist, and previous / play-pause /
    next controls sent through `MusicFeed.send(Transport)` — the same session path the Music tile's controls use, no second media
    path; shown while a session exists, playing or paused (the play glyph while paused; unlike the tile, which shrinks on pause,
    a pod has nothing to shrink — H3); empty: "Nothing playing"; tap on the title → the MUSIC slot app (the shell's Music app,
    phase 10 Q5)
  - Reminders — `ReminderStore.active()`: Today's by time, then the next 3 Coming up, then Whenever, up to 6 rows; row = title and
    the Reminders page's subline wording (`ReminderText`); empty: "No reminders — ask Tess to remind you"; tap → Tess's Reminders
    page, opened with `CortanaService.showSession` carrying a destination argument (an ADD to phase 03's session arguments,
    in-process, nothing exported)
  Launches from a pod go through `StartActivity`'s launch path like the app list's, so the Start exit and return are phase 01's (H11)
- 2026-09-22: Settings (P4 design, agent; H5): Start settings gains a "Pod bay" page (`SettingsPage.POD_BAY`; hub item "Pod bay —
  Agenda, weather, now playing, reminders") with one W10M toggle per pod, default all On, persisted in the shell's settings store;
  a pod switched off is absent from the page and the order of the rest is fixed; with every pod off the page shows one line
  "Turn on pods in Start settings" (`pod_bay_empty`) that opens the page
- 2026-09-22: Tess's command (agent; ADDs to phase 03's matcher, action layer and gate — the "wired to the pane" of R10-Q5):
  `Request.OpenPodBay(doors: Boolean)` and `Request.ClosePodBay`. Phrase rule, checked FIRST in `CommandMatcher.command()` so the
  words never reach the open-app rule as `OpenApp("the pod bay doors")`: the normalised text contains "pod bay" (or "podbay") and
  starts with open / show / pull out → OpenPodBay with doors = the text also contains "door" or "doors"; starts with close / shut →
  ClosePodBay. The phrase set is built from `Brand.POD_BAY_NAME`, and "open the pod bay doors", "open the pod bay" and "close the pod
  bay doors" join `COMMAND_PHRASES` for the grammar pass (`CommandMatcher.hotwords`). Replies, exact strings the rows compare by
  equality: doors → `Brand.POD_BAY_DOORS_REPLY` = "I'm afraid I can't do that, Dave." (the ellipsis in PLAN.md is the beat before
  she opens it, not part of the string, because the spoken text is what the engine is handed); plain open → "Opening the pod bay.";
  close → "Closing the pod bay."; "open" while it is already open still speaks its line (the doors line is the joke) and moves
  nothing; "close" on Start speaks its line and moves nothing. Every reply is shown on a response card and spoken by the small
  persona (phase 03 H9 stands). Timing: the outcome closes the session after the utterance (`Outcome.close`, phase 03's
  close-after-utterance path on `SpeechEvent.SpeakingDone`), and the pane opens when Start resumes — so the order is reply spoken,
  session closed, pane open, and the user sees it slide open on Start (H6, H7)
- 2026-09-22: Route to Start with no new exported surface (agent; design 8d, testability 14): a main-process `PodBayRequests`
  singleton (the shape of `SecondaryTiles.pending`) records the pending open or close; the action then starts the HOME intent through
  `ActionHost.launch` so Start comes to the front when Tess was opened over another app; `StartActivity.onResume` consumes the
  request (as it consumes `pendingRecent`) and animates the pager to the page with `Motion.PIVOT_SETTLE_MS`. When Start is already
  under the session, the session's close resumes it and the same consumption runs. A request survives the session being dismissed
  mid-line (the request ran; the open is pending) and a new request in the same session replaces it. Phase 03's exported allow-list is
  unchanged and its E5 row is re-run as is
- 2026-09-22: Locked gate (agent; phase 03's PQ3 rule, "opens apps or reads personal data"): `LockGate.allowedWhileLocked` is false
  for OpenPodBay and ClosePodBay — the bay shows the agenda and reminders and is a Start surface; the card's caption is "Open the pod
  bay" / "Close the pod bay", Tess says "Unlock your phone to continue.", and after unlock the same request continues, so the doors
  line is spoken then and the pane opens (phase 03 H12's pending rule)
- 2026-09-22: Typed form (fidelity A4, phase 03 Decisions): the request typed into the real text box runs the same matcher and
  reply path; E7 proves it on phase 03 E5's route. Spoken utterances are synthesised into `qa/phase-03/scripts/utterances.py` as
  `pod_bay_doors`, `pod_bay_doors_noart` ("open pod bay doors"), `pod_bay_open`, `pod_bay_close`, `pod_bay_neg1` ("open the pod")
- 2026-09-22: Branding (agent; design 9, A10): `Brand.POD_BAY_NAME` ("pod bay"), `Brand.POD_NAME` ("pod") and
  `Brand.POD_BAY_DOORS_REPLY` live beside `Brand.ASSISTANT_NAME`; the page title, the Settings entry, the empty-bay line and the phrase
  set read them, so the whole HAL layer (lens, name, line) swaps together in a public build
- 2026-09-22: Back / Home / screen-off with the pane open (agent; design 6, 29): Back → Start (`[podbay] closed by back`); Home →
  Start (`closed by home`); screen off and on, with or without a lock, leaves the pager on the pod bay, as it leaves the app list
  today (H9); an app launched from a pod and returned to by its own finish leaves the pager where it was, while a return by Home is
  Start; process death → Start (the page is not persisted, as the app list's is not)
- 2026-09-22: Edit mode (agent; design 29): edit mode is entered only by a hold on a Start tile; while it is on the pivot is locked,
  so the pod bay cannot be reached; the pod bay has no edit mode and a hold on a pod does nothing (pods are configured in Settings)
- 2026-09-22: Gesture navigation (agent; design 25, testability 30): the pod bay opens on a pager pan starting anywhere inside the
  page; a swipe that starts in Android's left gesture inset is Android's Back and never the pod bay; `systemGestureExclusionRects` is
  not used. E12 proves both on the AVD's gestural overlay; One UI's hint area is P2
- 2026-09-22: Harness (agent; testability 3, the R10 "harness note"): every driver that reaches Start with a right swipe now opens
  the pod bay instead. At the split that is `qa/phase-02/scripts/gestures.sh` `ensure_start` (`input swipe 200 1200 950 1200 250`);
  the build re-greps the qa tree for right swipes at page height and lists every hit in `qa/phase-14/README.md`. The fix is in the
  driver: `KEYCODE_HOME`, then `KEYCODE_BACK` while the dump shows `app_list` or `pod_bay`, then assert `start_page` alone. Phase 03's
  `lib.sh` `ensure_start` (`KEYCODE_HOME` only) is already right. E13 re-runs the affected rows on this build
- 2026-09-22: Harness contracts (agent; testability 24, 25, 33, 34): the page is inside `StartActivity`'s root, which sets
  `testTagsAsResourceId`; test tags `pod_bay` (page root), `pod_bay_scroll`, `pod:<id>` (agenda | weather | nowplaying | reminders),
  `pod_header:<id>`, `pod_subheader:<id>:<key>`, `pod_row:<id>:<n>` on the node carrying each row's text, `pod_empty:<id>` on the empty
  line, `pod_control:nowplaying:<PREVIOUS|PLAY_PAUSE|NEXT>`, `pod_bay_empty`, `settings_pod_bay`, `pod_switch:<id>`; diagnostics
  `[podbay] opened by swipe|voice|voice (doors)|settings`, `[podbay] closed by back|home|swipe|voice`, `[podbay] pod <id>: <n> rows` /
  `[podbay] pod <id>: empty: <reason>`, `[podbay] pods enabled: <list>`, `[podbay] launch <id> -> <component>`. Rows are adb-driven
  on phase 03's `lib.sh` (symlinked as phase 01 did), evidence under `qa/phase-14/`
- 2026-09-22: Process, permissions, surface (agent; design 27; testability 35): launcher process; no new permission (Calendar,
  Location and the media rows are phase 01's, reminders are the shell's own); no new launcher entry, so the app list's groups are
  untouched; no network beyond Weather's own refresh (A11)
- 2026-09-22: NEEDS-HUMAN rows here are ACCEPT rows (testability 26): the pod bay is a P4 design with no W10M original; the one
  fidelity-shaped item, Tess speaking the line on the phone, is a phone row because TTS on the phone is still unproven (INDEX, phase 03)

## Interview queue (Stage A step 4)
1. ~~Real widgets~~ RULED 2026-09-23: B (see Decisions). Original question kept below.
   Real Android widgets in the pod bay — in or out? A8 needs a ruling, not "optionally". Costs if in, recorded now so the choice is
   made with them in view (design 7): a widget inflates at the system density and follows Samsung's Font size, so RV10 (nothing
   Samsung's Screen zoom or Font size resizes) is broken inside every widget; a non-privileged host gets Android's bind-consent
   dialog per widget (a P2 seam); widgets draw themselves and will not look Metro.
   A. In: a fifth pod kind, "Widgets", with a picker, the bind dialog, configure activities and host persistence; the two costs are
      accepted and recorded; rows added at the interview: bind through the system dialog (uiautomator on AOSP, Samsung's on the
      phone), a bound DeskClock digital widget's text equals the emulator clock, `dumpsys appwidget` lists the shell's host and ids
      across `am force-stop`, provider uninstalled, configure cancelled, bind refused
   B. Out for this plan: the four pods only; a widget pod, if ever, is an ADD in the post-plan updates (lean)
   C. In, but behind a Start-settings switch that is Off by default, so the default bay stays Metro and the seams are opt-in
   D. Other / let me clarify

## Build tasks
1. Pager: the third page with named indices, initial page START, the Back / Home rules, the focus guard, the `[podbay] opened by
   swipe` / `closed by …` lines
2. Pod frame: the page on phase 13's app-list backdrop, the scroll, headers, rows, subheaders, empty lines, the layout tokens, tags
3. The four pods on the existing feeds: Agenda, Weather, Now playing (with `MusicFeed.send` transport), Reminders; their taps
   through `StartActivity`'s launch path and `CortanaService.showSession` with the Reminders destination
4. Settings: the "Pod bay" page, the hub item, the per-pod switches in the settings store, the empty-bay line
5. Tess: `Request.OpenPodBay` / `ClosePodBay`, the matcher rule and hotwords, the `ActionLayer` branch and outcomes, the `LockGate`
   entries and captions, `PodBayRequests` and its consumption in `StartActivity.onResume`, the Brand strings, the five utterances
6. Harness: the `ensure_start` fix and the re-grep, the regression run (E13), evidence index `qa/phase-14/README.md`
7. Only if Q1 is A or C: the Widgets pod kind (`AppWidgetHost`, picker, bind consent, configure, host lifecycle, persistence) —
   added at the interview with its rows; not built otherwise

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; `uiautomator dump` follows
RV13; "diagnostics" is read with phase 01's command. Every E row runs on the AOSP AVD `tileshell_fhd` (1080×2340 @ 450 dpi, no
Google) through adb on phase 03's driver floor (`qa/phase-03/scripts/lib.sh`); voice rows use phase 03's audio route (null-sink
microphone, `say`, `reply_text`, `parecord`) and inherit phase 03's status: they prove the pipeline with a synthesised voice, and
Jeremy's own voice is P1.
**Emulator:**
- E1 Pager: from Start, `adb shell input swipe 200 1200 950 1200 250` (a pan inside the page) → dump shows `pod_bay`, diagnostics
  `[podbay] opened by swipe`; `adb shell input swipe 900 1200 150 1200 250` → `start_page` and no `pod_bay`; a second left swipe →
  `app_list` (phase 01 E12 unchanged); from the pod bay `KEYCODE_BACK` → `start_page`, `[podbay] closed by back`; on Start
  `KEYCODE_BACK` still runs phase 01 E20's rule (DeskClock resumes); from the pod bay `KEYCODE_HOME` → Start scrolled to the top
  (`[start] home: page START, scrolled to top`)
- E2 Bars: on the pod bay `adb shell dumpsys window` shows the status and nav bar inset sources not visible (phase 01 E19's form) and
  the screencap shows the drawn bars; `tap_node` on the drawn Back key → Start; on the Windows key → Start
- E3 Pod content from fixtures, each asserted present and then gone: Agenda — `content insert` two events today (one all-day) and
  one tomorrow (phase 01 E7's route) → `pod_row:agenda:0..2` texts (all-day first, "h:mm title", the third under
  `pod_subheader:agenda:tomorrow`); delete → rows gone, `pod_empty:agenda` = "Nothing on your calendar today". Weather — phase 01
  ITEM2's fixture report written into the feed's cache (`weather_fixture.py`) → `pod_row:weather:*` texts equal the report's
  formatted values; clock + 61 minutes (phase 01 E9's commands) → the "Updated h:mm" line (X22); restore per RV12. Now playing —
  phase 01 E8's playback fixture → `pod:nowplaying` title and artist equal `dumpsys media_session`'s metadata; `tap_node
  pod_control:nowplaying:PLAY_PAUSE` → `dumpsys media_session` state PAUSED, the play glyph shown (screencap), the pod still present;
  `NEXT` → the next track's title; stop → `pod_empty:nowplaying` = "Nothing playing". Reminders — three reminders through Tess
  (phase 03 E15's setup) → `pod_row:reminders:*` texts are the titles with the Reminders page's sublines; delete all → the empty line
- E4 Denied and empty states with diagnostics, both directions: `adb shell pm revoke app.tileshell android.permission.READ_CALENDAR` →
  `pod_empty:agenda` = "Calendar access is off — turn it on in Setup" and `[podbay] pod agenda: empty: no calendar access`; `tap_node
  pod_empty:agenda` → `dumpsys activity activities` shows `SettingsActivity` resumed and the dump shows the checklist's Calendar row;
  `pm grant` → the rows of E3 return. `pm revoke … ACCESS_COARSE_LOCATION` → the weather line and `empty: no location`; restore. No
  session → `empty: no session`; no reminders → `empty: none`. Each `[podbay] pod <id>: <n> rows` line matches the dump's row count
- E5 Settings: the hub shows `settings_pod_bay`; `tap_node pod_switch:weather` → `pod:weather` absent, the other three present in the
  same order, `[podbay] pods enabled: agenda,nowplaying,reminders`; all four off → `pod_bay_empty` present, `tap_node` on it →
  `SettingsActivity` on the Pod bay page; `adb shell am force-stop app.tileshell`, Home → the switches persist (dump); restore all On
- E6 Voice, the easter egg: from Start, `KEYCODE_ASSIST`, `say pod_bay_doors` → diagnostics `[match] "…" -> OpenPodBay(doors=true)`;
  `reply_text` equals "I'm afraid I can't do that, Dave." and the `parecord` capture's RMS over the reply window is above −40 dBFS
  (phase 03's spoken reply pass rule); the `[podbay] opened by voice (doors)` line's wall time is after the speech ring's
  `SpeakingDone` line for that utterance; `dumpsys window` shows no session window and the dump shows `pod_bay`. `say pod_bay_open` →
  `OpenPodBay(doors=false)`, reply "Opening the pod bay.", `opened by voice`; `say pod_bay_close` → `ClosePodBay`, reply "Closing the
  pod bay.", `start_page`, `[podbay] closed by voice`. Negatives: `say pod_bay_neg1` ("open the pod") → the not-understood handler
  line (phase 03 E3) and no `pod_bay`; `say pod_bay_doors_noart` → doors=true; no match line ever contains `OpenApp` for these
- E7 Typed form: `type_request "open the pod bay doors"` → the same match line, the same reply text, `pod_bay` open; phase 03 E5's
  exported-components check re-run against `qa/phase-03/exported-allowlist.txt`: unchanged
- E8 Locked: `adb shell locksettings set-pin 1234`, `KEYCODE_SLEEP`, `KEYCODE_WAKEUP`, Tess over the keyguard (phase 03 E9's route),
  `say pod_bay_doors` → the "Unlock to continue" card with caption "Open the pod bay" (dump), `reply_text` = "Unlock your phone to
  continue.", `dumpsys window` still shows the keyguard, no `[podbay] opened`; `tap_node` on "Unlock", `adb shell input text 1234`,
  `KEYCODE_ENTER` → the doors line is spoken and the pod bay opens (E6's checks); restore `adb shell locksettings clear --old 1234`
- E9 From inside another app: `adb shell am start -n com.android.deskclock/.DeskClock`, `KEYCODE_ASSIST`, `say pod_bay_doors` → after
  the reply `dumpsys activity activities` shows `StartActivity` resumed and the dump shows `pod_bay`; `KEYCODE_BACK` → Start; a second
  `KEYCODE_BACK` → DeskClock resumes (phase 01 E20: the Back history survived the detour)
- E10 Edit mode and the pivot: `enter_edit` on a tile (phase 02's `gestures.sh`) → `adb shell input swipe 200 1200 950 1200 250` opens
  nothing (dump unchanged, no `[podbay] opened`); exit edit mode; on the pod bay `adb shell input swipe x y x y 1000` on a pod → dump
  unchanged, no `edit_disc:*`, no menu; phase 02 E3's folder-name step, opened and dismissed → the pivot stays on Start (no `pod_bay`,
  no `app_list`)
- E11 Screen-off and process death with the pod bay open: `KEYCODE_SLEEP`, `KEYCODE_WAKEUP`, `adb shell wm dismiss-keyguard` → `pod_bay`
  still the page; with a PIN set, sleep, wake, unlock → still the page; `adb shell am force-stop app.tileshell`, `KEYCODE_HOME` →
  `start_page`; restore the lock
- E12 Gesture navigation: `adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural`; from Start a pan from x = 200 →
  `pod_bay`; a swipe from x = 2 (inside the left gesture inset) → no `[podbay] opened` and Android's Back fires (with DeskClock in the
  history it resumes; with none, Start stays, phase 01 X12); restore with `cmd overlay disable`
- E13 Regression on the same build, after the harness fix: `qa/phase-02/scripts/regress.sh` 6/6; a row that starts on the pod bay and
  one that starts on the app list and calls `gestures.sh` `ensure_start` → `start_page` alone; phase 01 E2 (Home shows Start), E12
  (swipe left → app list, search, jump grid), E19 (bars on all shell screens), E20 (Back on Start); phase 03 E3 (an utterance outside
  the list is still not understood), E5 (typed request and the allow-list), E10 (locked commands, now with the pod-bay phrase);
  `utterances.py build` succeeds with the five new ids
- E14 Backdrop: phase 13's app-list backdrop row re-run on the pod bay with the same method and a KNOWN high-contrast Start background
  (the checkerboard through phase 01's background URI grant, `prefs_edit.py`), cited by that row's number when phase 13 is FINAL
- E15 RV10: `wm size 1440x3120` / `720x1560`, `wm density 560`, `font_scale 1.3` leave every pod node's bounds in epx unchanged ± 1 epx
  (phase 01 E3's method, one dump each); restore
- E16 Every diagnostics line in Decisions is asserted by at least one row above

**Phone-only:** P1 Jeremy's own voice on "open the pod bay doors" (the grammar hotword) and Tess SPEAKING the line on the S25 Ultra
(TTS on the phone is unproven until a CI build is installed, INDEX 2026-09-22); P2 One UI gesture navigation: a pan opens the pod bay,
the left-edge swipe is One UI's Back, and the hint area does not take the pan; P3 Samsung Calendar's events in the Agenda pod and a
Samsung media session (Samsung Music, YouTube Music) in Now playing with its transport; P4 (only if Q1 is A or C) Samsung's own
widgets bind, draw and update in the bay

**NEEDS-HUMAN (all accept rows):** H1 the pod bay as a whole (P4 design, no W10M original); H2 the pod frame — type-only Metro
cards, spacing, accent headers (P4); H3 each pod's content choices, caps and the paused-session rule for Now playing (P4); H4 the
empty-state wording (P4); H5 the Settings page — switches, fixed order, the empty-bay line (P4); H6 the doors line's exact wording
and delivery — spoken on a card, then the session closes, then the pane opens; "Dave", not Jeremy's name (film reference); H7 the
plain replies "Opening the pod bay." / "Closing the pod bay." (agent); H8 Back from the pod bay going to Start rather than the last
app (approximation, an ADD to phase 01's Back rule); H9 the pod bay keeping its page across screen-off and lock (agent); H10 the pod
bay on phase 13's app-list backdrop rather than Start's wallpaper (agent); H11 pod launches using the Start exit (agent); H12 accept
any approximation not covered by H2–H11; H13 Tess speaking the line on the phone (P1)

## Edge cases
- Pane open + edit mode: the pivot is locked, so the pod bay is unreachable while edit mode is on; a hold on a pod does nothing;
  the folder-name box never swings the pivot to the pod bay (the phase 02 gate's defect, second direction)
- Back / Home / screen-off / lock / process death with the pane open (Decisions); Home while the pane is mid-swipe (settles on
  Start); a pod launch while the Start exit is already playing (ignored, as a second tile tap is)
- A pod's feed absent: weather never fetched; calendar access denied; no media session; no reminders — each shows its line and its
  diagnostics reason, never a blank pod; a feed that arrives while the pane is showing (the pod fills in place)
- Content limits: 20 calendar events (cap 6, the page scrolls, the pod does not); long titles and artist names (one line, ellipsised);
  a session that publishes no art (the art square draws the tile fill with the music glyph); a session paused for hours (shown paused,
  H3); a reminder that fires or is completed while the pane shows (its row goes); a Whenever reminder; a place or person reminder
  (its subline as on the Reminders page)
- The phrase over the keyguard (E8), typed (E7), from inside another app (E9), while the pod bay is already open (the line is spoken,
  nothing moves), "close" on Start (the line is spoken, nothing moves); the session dismissed mid-line (the utterance is cancelled,
  the pending open is still consumed when Start resumes); a second request in the same session (replaces the pending one); "open the
  pod" / "open the bay" (not understood); "open pod bay doors" without the article (doors); ASR returning "podbay" as one word (still
  matched); the grammar hotword present but the open pass winning with a different transcript (E3's diagnostics show which)
- The HAL strings in the A10 branding module: changing `Brand.POD_BAY_NAME` changes the page title, the Settings entry, the empty-bay
  line and the phrase set together (a JVM test asserts the matcher follows the Brand value)
- Gesture navigation: the pan vs the left-edge Back (E12); a pan that starts on a bottom-row tile (the pager still pans, as it does on
  Start today); a pan during a live flip
- Show more tiles toggled, theme light / dark, the accent changed (headers follow), the X5 transparency slider (pods are not tiles and
  do not follow it); RV10 (E15)
- Pod switches all off (the empty-bay line); a pod switched off while its feed updates (nothing drawn, nothing logged for it)
- If widgets are in (Q1 A or C): provider uninstalled, configure activity cancelled, bind refused, host `startListening` after process
  death, widget size classes against the epx canvas, Samsung's Font size changing a widget's text (RV10 cost, recorded)

## QA evidence
