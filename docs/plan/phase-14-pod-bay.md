---
phase: 14
slug: pod-bay
status: FINAL   # FINAL 2026-09-29 (Jeremy: "A"); was DRAFT → FINAL (only after Stage A step 7; changes after FINAL go through INDEX.md Change Log)
depends-on: [01, 03, 12, 13]   # 12 for T14-7's wizard gate and E17 (C-23, T14-12); 10 is a soft dependency (T14-4): the Now playing pod works with any media session through phase 01's MusicFeed; only its title tap opens the MUSIC slot app, which is phase 10's Music once built
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
**Out (explicitly):** real Android widgets through `AppWidgetHost` — ruled out 2026-09-23 (Q1 B) ~~unless interview Q1 rules them in
(then the interview adds the build task and rows named under Q1)~~; any new feed, permission or provider (the pods read what phases
01 and 03 already read); reordering or editing pods by gesture (pods are configured in Settings); a pod for an app that does not
exist yet (an inbox-app phase may ADD a pod through the same list — an ADD, not a hook); Mail, browser and Maps pods (R10-Q4, which
stands as a ruling under A11 as amended 2026-09-23: the shell builds no Mail, browser or Maps — C-7); a notifications pod (the
action center's job, phase 04); an edge-swipe trigger (the left edge is Android's Back gesture); phase 07's glance screen (W10M's
own "Glance", a different thing; the name collision is why this pane is the pod bay).

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
  `homeEvents` → START (reached by the drawn Windows key, `StartActivity.kt:234`, and by the HOME intent through `onNewIntent`,
  `:464-469`; r3 D3), and the X20 scroll-to-top when Start was already in front, exactly as today from the app list;
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
    the slot, the pod follows with no change here). The row's time text is `ReminderText.time(context, beginMs)` (the shell's time
    format, `ReminderText.kt:49`), "All day" as the tile writes it. The feed is an ADD to `CalendarFeed` (build task 3; r3 D5 —
    as built it publishes tile faces only, over `[now, now + 24 h]`, capped at 4, `CalendarFeed.kt:59-90`): `agenda:
    StateFlow<List<AgendaItem>>` (`title`, `beginMs`, `allDay`, `day: TODAY|TOMORROW`) filled by `refresh()` — the same observer
    and minute tick (`:40-57`) — from a second Instances query over `[now, local end of tomorrow]`, all-day first then BEGIN, cap 6
    across both days; an all-day instance's day is its BEGIN read in UTC (CalendarContract's convention; phase 01's own insert is
    `allDay:i:1`, `eventTimezone:s:UTC`), a timed one's in the default zone. The tile's 24-hour / 4-face query is unchanged
  - Weather — `WeatherFeed.state.value` (re-cut 2026-09-29, r3 D6): `NO_PERMISSION` or `LOCATION_OFF` in `problems` → the line
    "Location is off — turn it on in Setup" (tap → the checklist) and `empty: no location`, even with a cached report (a revoke
    force-stops the process and `start()` reloads the cache, `WeatherFeed.kt:85-87`, `:131-135`); else a report → place name,
    `WeatherFormat.degrees(current.temperature)`, condition text `WmoCodes.word(current.code, current.isDay)` (`WeatherModel.kt:53-80`
    — not `WeatherFormat`), the day's high / low as "H <high> L <low>" from `upcomingDays(now).first()` and precipitation as
    "<precipPct>%" (forms fixed 2026-09-23 so E3 can write its strings by hand, T14-2), and the X22 "Updated h:mm" line while the
    feed's own stale flag is set; no report → "No weather yet" and `empty: no report`; tap → `WeatherActivity`. ADD to
    `WeatherFeed` (build task 3): `State.stale: Boolean`, written by `check()` beside `publishedStale` (`:116`) — as built the stale
    flip republishes the tile only (`:107-118`, `:183-186`) — so the X22 line follows the feed's own flip
  - Now playing — `MusicFeed.now`, an ADD to `MusicFeed` (phase 01's part, build task 3; r3 D4 — as built its state is private,
    `MusicFeed.kt:45-48`, `:70`, and it keeps the last track as idle when the session dies, `:167-168`): `now:
    StateFlow<NowPlaying?>` where `NowPlaying(track: MusicRules.Track, playing: Boolean, art: ImageBitmap?)` is set in `publish()`
    from the live controller's `Now` and art (`:148-165`, `:171-179`) and is null whenever no routed session with metadata exists —
    unlike the tile, which keeps the last track idle. The pod shows album art one small-tile unit square, title, artist, and
    previous / play-pause / next controls sent through `MusicFeed.send(Transport)` — the same session path the Music tile's
    controls use, no second media path; `Transport` (`tiles/engine/TileContent.kt:107`, `{ PLAY_PAUSE, STOP, NEXT }` as built)
    gains `PREVIOUS` with a `skipToPrevious()` branch in `send` (`MusicFeed.kt:131-135`), the tile's `PLAYING_CONTROLS` unchanged
    (r3 D4); shown while `now != null`, playing or paused (the play glyph while paused; unlike the tile, which shrinks on pause,
    a pod has nothing to shrink — H3); empty: "Nothing playing"; tap on the title → the MUSIC slot app (the shell's Music app,
    phase 10 Q5)
  - Reminders — `ReminderStore.active()`: Today's by time, then the next 3 Coming up, then Whenever, up to 6 rows; row = title and
    the Reminders page's subline wording (`ReminderText`); empty: "No reminders — ask Tess to remind you"; tap → Tess's Reminders
    page, opened through `CortanaService.open(context, mode)` (`cortana/CortanaService.kt:74`), which calls the platform's
    `VoiceInteractionService.showSession(args, 0)` (`:87`); the destination is a new `EXTRA_*` on that `args` bundle beside
    `EXTRA_MODE` (`:68`) — an ADD to phase 03's session arguments, in-process, nothing exported (T14-6; the split-time text named
    `CortanaService.showSession`, the wrong method)
  Launches from a pod call `launchApp(target, bounds, null)` as the app list does (`StartActivity.kt:294`): no Start exit (the
  grid is not on screen), phase 01's entrance on return (`returningFromLaunch`, `:352`, `:544-549`); targets:
  `TileTarget.Shell(ShellTiles.WEATHER)` (`:372`), the CALENDAR / MUSIC slots resolved through `SlotResolver` as
  `ActionLayer.slotApp` does (`ActionLayer.kt:662-665`) to `TileTarget.App`; no tile key, so nothing is promoted (H11; re-cut
  2026-09-29, r3 D8 — the split-time text said "so the Start exit … are phase 01's", which the app list's path does not play)
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
  close-after-utterance path on `SpeechEvent.SpeakingDone`), and the pane opens when Start's `PodBayCheck` runs (the Route Decision
  below, r3 D1) — so the order is reply spoken,
  session closed, pane open, and the user sees it slide open on Start (H6, H7)
- 2026-09-22: Route to Start with no new exported surface (agent; design 8d, testability 14; re-cut 2026-09-29 by r3 D1 — the
  split-time route started the HOME intent from the action and consumed the request in `StartActivity.onResume`, which never runs
  when Tess's session, a window over Start, closes: Start is never paused under it and gets only its window focus back,
  `StartActivity.kt:491-503`, L13-7; and a HOME intent started at `run()` time reaches Start before the reply is spoken): a
  main-process `PodBayRequests` singleton (`StateFlow<Request?>`, the shape of `SecondaryTiles.pending`, `SecondaryTiles.kt:86`)
  records the pending open (with `doors`) or close; a new request replaces it. The action only records it and returns
  `Outcome(spoken, card, close = true)` — it starts nothing. When the session closes for that outcome (`closeRequests.collect {
  hide() }`, `CortanaSession.kt:126` — an ADD to phase 03's session, in-process: `CortanaSessionService` has no `android:process`)
  and a request is pending, the session first starts the HOME intent as `goHome()` does (`CortanaSession.kt:266-271`) with one
  boolean extra `app.tileshell.extra.POD_BAY_CHECK` (a signal only; it carries no request), then hides — so Start comes to the
  front when Tess was over another app, and the intent can never open the pane before the reply ends. `StartActivity` consumes
  the request in ONE place, the `homeEvents` collector (`StartActivity.kt:148-168`, the one coroutine that moves the pager for
  keys, L13-4): `homeEvents` becomes a flow of `Home(alreadyInFront)` | `PodBayCheck`; `onNewIntent` (`:464-469`) emits
  `PodBayCheck` when the HOME intent carries the extra, else `Home(inFront)` as today; `onWindowFocusChanged(hasFocus = true)`
  (`:497-503`) and `onWizardEnded()` (`:514-517`) also emit `PodBayCheck`. On `PodBayCheck` the collector: returns while
  `SetupWizard.showing` (`:152`; the request stays pending — T14-7); takes the request (none → does nothing, never scrolls); closes
  an open burst and exits edit mode as Home does (`:156-158`); open → `animateScrollToPage(POD_BAY, tween(Motion.PIVOT_SETTLE_MS))`,
  close → `animateScrollToPage(START)`; when uninterrupted logs `[podbay] opened by voice[ (doors)]` / `[podbay] closed by voice`;
  no scroll-to-top and no `home:` line for this event; on the page already (open on the pod bay, close on Start) it takes the
  request and moves nothing. Nothing is consumed in `onResume`. A request survives the session being dismissed mid-line (Back,
  the Windows key, a touch outside: the focus-back `PodBayCheck` consumes it); after unlock (E8) `onUnlocked` re-runs the request
  (`CortanaModel.kt:417-427`) and the same path follows. Build-start check, recorded in `qa/phase-14/README.md`: whether a HOME
  intent from the session reaches `onNewIntent` while Start is resumed beneath it (T11-16 says `KEYCODE_HOME` does not;
  `goHome()` already relies on the intent form) — the focus-back event makes the rows independent of the answer. Phase 03's
  exported allow-list is unchanged and its E5 row is re-run as is. Added 2026-09-23 (T14-7), re-cut 2026-09-29 (r3 D10 / V20):
  while phase 12's setup wizard shows, a pending request is NOT consumed and waits until the wizard finishes — as built the pager
  does not exist then (`StartPivot` is not composed while `SetupWizard.showing`, `StartActivity.kt:222-230`) and Home is a no-op
  under the wizard (`:150-152`), so the HOME intent moves nothing; `onWizardEnded()` (`:514-517`, where phase 12's built "Start
  visible" gate calls `SecondaryTiles.setStartVisible(true)`, beside `onResume`'s call at `:484`, T12-14) emits the `PodBayCheck`
  that releases it. E17 proves the wait (T14-12)
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
  Start (`closed by home`; the drawn Windows key or the HOME intent, both into `homeEvents`, `StartActivity.kt:149`, `:234`, `:467`
  — on this AVD only the Windows key reaches a resumed Start, T11-16, so `KEYCODE_HOME` from the pod bay is phone row P5; r3 D3); screen off and on, with or without a lock, leaves the pager on the pod bay, as it leaves the app list
  today (H9); an app launched from a pod and returned to by its own finish leaves the pager where it was, while a return by Home is
  Start; process death → Start (the page is not persisted, as the app list's is not)
- 2026-09-22: Edit mode (agent; design 29): edit mode is entered only by a hold on a Start tile; while it is on the pivot is locked,
  so the pod bay cannot be reached; the pod bay has no edit mode and a hold on a pod does nothing (pods are configured in Settings)
- 2026-09-22: Gesture navigation (agent; design 25, testability 30): the pod bay opens on a pager pan starting anywhere inside the
  page; a swipe that starts in Android's left gesture inset is Android's Back and never the pod bay; `systemGestureExclusionRects` is
  not used. E12 proves both on the AVD's gestural overlay; One UI's hint area is P2
- 2026-09-22: Harness (agent; testability 3, the R10 "harness note"): every driver that reaches Start with a right swipe now opens
  the pod bay instead. At the split that is `qa/phase-02/scripts/gestures.sh` `ensure_start` (`input swipe 200 1200 950 1200 250`,
  `gestures.sh:28-49`); the build re-greps the qa tree for right swipes at page height — matching `input swipe` with computed
  operands too, not only literal coordinates — and lists every hit in `qa/phase-14/README.md`. Known on 2026-09-29 (r3 V17):
  `gestures.sh:44`, `qa/phase-11/scripts/q.sh:82` (`ensure_start_page`, the same Back-then-swipe form),
  `qa/phase-13/scripts/l13_2_row.sh:43` (from the app list → Start; still correct) and `qa/phase-13/scripts/p13.sh:67-72` `to_start`
  (computed coordinates). The fix is in the driver: `KEYCODE_HOME`, then `KEYCODE_BACK` while the dump shows `app_list` or
  `pod_bay`, then assert `start_page` alone (a failed assert fails the row). It applies to all three helpers (r3 V1): `gestures.sh`
  `ensure_start`, phase 03's `lib.sh` `ensure_start` (`lib.sh:287-290`, `KEYCODE_HOME` only — which leaves the pager on the pod
  bay, since this AVD does not re-deliver the HOME intent to the resumed home activity, `qa/phase-02/README.md:73-75`, T11-16) and
  phase 11's `q.sh` `ensure_start_page` (`q.sh:75-85`, Back then a right swipe). The same re-grep lists every driver that greps
  Start's home line, because this phase renames it from `home: page 0` (`StartActivity.kt:166`) to `home: page START` (T11-9 /
  T14-5): at the split that is `qa/phase-02/scripts/regress.sh:120` (`grep -q "home: page 0"`), updated with the `ensure_start`
  fix; known on 2026-09-29 also `qa/phase-13/L13-3-investigation/back_interrupt.sh:29` (r3 V17). Launches (T14-3, C-6): after any launch (E3's Now playing tap, E9, the E13 re-runs that open an app),
  `am force-stop app.tileshell` + Home before the next assertion on Start's grid — RecentApp is in memory only
  (`qa/phase-01/scripts/recent0922.sh:19-21`), so a launched app is promoted above the bottom row. E13 re-runs the affected rows on
  this build
- 2026-09-22: Harness contracts (agent; testability 24, 25, 33, 34): the page is inside `StartActivity`'s root, which sets
  `testTagsAsResourceId`; test tags `pod_bay` (page root), `pod_bay_scroll`, `pod:<id>` (agenda | weather | nowplaying | reminders),
  `pod_header:<id>`, `pod_subheader:<id>:<key>`, `pod_row:<id>:<n>` on the node carrying each row's text, `pod_empty:<id>` on the empty
  line, `pod_control:nowplaying:<PREVIOUS|PLAY_PAUSE|NEXT>`, `pod_bay_empty`, `settings_pod_bay` (the hub item),
  `settings_page_pod_bay` (the Pod bay page's root; r3 V19), `pod_switch:<id>`; diagnostics
  `[podbay] opened by swipe|voice|voice (doors)` (`|settings` struck 2026-09-29, r3 V13: nothing opens the pod bay from Settings), `[podbay] closed by back|home|swipe|voice`, `[podbay] pod <id>: <n> rows` /
  `[podbay] pod <id>: empty: <reason>`, `[podbay] pods enabled: <list>`, `[podbay] launch <id> -> <component>`, and (added
  2026-09-23, T14-8) `[start] page=POD_BAY|START|APP_LIST` each time the pager settles on a page, the second source for every
  page-absence assertion; the home line becomes `[start] home: page START[, scrolled to top]` (T11-9). Added 2026-09-23: `[podbay]
  launch <id> failed: <why>` (`slot unassigned` | `no session: not the assistant` | `ActivityNotFoundException`), so a pod tap that
  opens nothing is never silent (T14-13) — the slot picker still opens for `slot unassigned` (the tap follows the tile,
  `StartActivity.kt:333`) and phase 03's role notice for `no session` (`CortanaService.kt:76-84`), r3 D9; the
  `ActivityNotFoundException` form is a guard written and exercised by no row — recorded (C-26), r3 V13; tag `acrylic:pod_bay` on the page's backdrop node and phase 13's `[fluent] pod_bay
  source=static tint=(0,0,0) alpha=0.8 blur=30epx` line (T14-11, last Decisions line). Rows are adb-driven
  on phase 03's `lib.sh` (symlinked as phase 01 did), evidence under `qa/phase-14/`
- 2026-09-22: Process, permissions, surface (agent; design 27; testability 35): launcher process; no new permission (Calendar,
  Location and the media rows are phase 01's, reminders are the shell's own); no new launcher entry, so the app list's groups are
  untouched; no network beyond Weather's own refresh (offline preferred; A11 as amended 2026-09-23 — C-7, T14-10). "No new exported
  component / permission" holds against the manifest (no new `<activity>` / `<service>`; the allow-list already carries phases 10
  and 15, `qa/phase-03/exported-allowlist.txt:37-45`). Surfaces for the gate's adversarial review (r3 D12): (1) the new `EXTRA_*`
  on the `showSession` args bundle — in-process only (`CortanaService.kt:86-87`); system-originated shows carry none of our args
  (`CortanaSession.kt:205-209`), so the value is validated as an enum name exactly as `EXTRA_MODE` is (`runCatching { valueOf }`),
  the destination being `CortanaDestinationKey.REMINDERS` (`CortanaModel.kt:67`); (2) the HOME intent's `POD_BAY_CHECK` extra on
  the exported HOME activity — a third party can send it, which only makes Start consult an empty in-process singleton, never the
  request itself; (3) `SettingsActivity.EXTRA_PAGE = POD_BAY` on an exported LAUNCHER activity (`SettingsActivity.kt:58`, the
  existing pattern for every page); (4) `PodBayRequests`, in-process
- 2026-09-22: NEEDS-HUMAN rows here are ACCEPT rows (testability 26): the pod bay is a P4 design with no W10M original; the one
  fidelity-shaped item, Tess speaking the line on the phone, is a phone row because TTS on the phone is still unproven (INDEX, phase 03)
- 2026-09-23 (agent, r2 triage T14-11): the pod bay's backdrop is phase 13's app-list backdrop material exactly — static source,
  tint (0,0,0), α 0.8, blur 30 epx, fallback the wallpaper under the tint unblurred (A18's form), no picture → the theme background —
  tagged `acrylic:pod_bay`, logged `[fluent] pod_bay source=static tint=(0,0,0) alpha=0.8 blur=30epx`, and phase 13's surface-table
  row is re-cut to match (it said "its own P4 fill"); E14 carries the values. Reason: this doc's Backdrop Decision is the explicit
  one (H10 is written for it), and two sibling pager pages share one material. Added 2026-09-29 (r3 D7; INDEX Change Log
  2026-09-26, A-N4 — "StaticBackdrop is one layer keyed by (uri, size, tint, radius); the pod bay must share the app list's key or
  it evicts its layer"): ADD to phase 13's engine (build task 2): `FluentSurface.POD_BAY("pod_bay", STATIC)` (`Fluent.kt:93-99` is
  a closed enum) and `AppListBackdrop` (`StaticBackdrop.kt:174-222`, hard-wired to `FluentSurface.APPLIST` today) takes the
  surface as a parameter, its tag and `logShown` following it; the pod bay composes it with the same arguments —
  `theme.backgroundUri`, `LocalShellColors.current.background`, a root that fills the pager page — so its `Key(uri, size, tint,
  radiusPx)` (`StaticBackdrop.kt:59-60`) equals the app list's and neither page evicts the other's layer; `visible` is derived by
  the app list's ≥ 50 %-in-window rule (`AppListPage.kt:281-286`), so `[fluent] pod_bay …` is logged when the page comes into view
- 2026-09-29 (agent, Stage A round 3, the last; triage review/2026-09-29-phase14-r3-triage.md): the doc re-checked against the
  code as built by phases 10-13 and 15. Re-cut: the route to Start (r3 D1 — the session's close sends the HOME intent with a
  `POD_BAY_CHECK` signal and Start consumes in its `homeEvents` collector, fed by `onNewIntent`, window focus back and
  `onWizardEnded`); Home from the pod bay is the drawn Windows key on the AVD, `KEYCODE_HOME` a phone row (r3 D3 / V1); the pods
  read ADDs to `MusicFeed` (`now`, `Transport.PREVIOUS`), `CalendarFeed` (`agenda`) and `WeatherFeed` (`State.stale`, problems
  win) (r3 D4-D6); the backdrop shares the app list's `StaticBackdrop` key (r3 D7); pod launches take the app list's path, no
  Start exit (r3 D8); the QA floor fixed (typed reminders, the lock screen, the Start ring read under E17, both-location revoke,
  scroll before reads, E14's strip and A-N4 check, E16's literal patterns). Q-R3-1 (how the voice rows run now that QA never touches
  the host's audio) RULED 2026-09-29 through Q-R3-1a (a) — below
- 2026-09-29: Q-R3-1, Jeremy's first reply: "I am done with calls tonight but dont change the system audio but you can use it
  now". Read as: audio may be used, the host's audio settings may not be changed. Phase 03's route cannot do both (its `audio.sh
  setup` loads a null sink and switches the desktop's default microphone, `audio.sh:28-50`), so a follow-up (Q-R3-1a) asks which
  route the spoken rows take. Found for it (agent, 2026-09-29): the installed emulator (37.1.11) has gRPC `injectAudio` ("Injects
  a series of audio packets into the Android microphone") and `streamAudio` (the device's audio output) —
  `~/Android/Sdk/emulator/lib/emulator_controller.proto:325-348` — and `tileshell_fhd` (emulator-5554) runs with gRPC on port
  8554, token auth (`/run/user/1000/avd/running/pid_*.ini`); neither call goes through the host's PipeWire. Not yet tried.
- 2026-09-29: Q-R3-1a — the spoken rows' route (Jeremy: "(a)"): the emulator's own gRPC audio — the synthesised utterance goes
  into the AVD's microphone by `injectAudio`, Tess's reply is captured by `streamAudio` for the RMS check; the host's audio is never
  touched (no `audio.sh setup`, no null sink, no default-microphone change, no `hostmicon`). Phase 14's build starts with a short
  spike that proves the route; if it fails, those rows fall back to typed-only on the emulator plus the phone rows (P1 / H13).
  Applied: the Acceptance criteria's Audio route and Audio spike paragraphs, C-30 in r3 V16's form, E6 / E8 / E9 as `speak.sh`
  steps, E13's phase 03 re-runs under `AUDIO_ROUTE=emu`, build task 6's `emu_audio.py` and switch, and the hotwords JVM test
  (r3 V2 / D13)
- 2026-09-29: FINAL (Jeremy: "A") — Stage A step 7, after round 3 (the last) and Q-R3-1 / Q-R3-1a. From here the doc changes
  only through a dated INDEX.md Change Log entry

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
2. ~~Voice rows without host audio~~ RULED 2026-09-29 (round 3, Q-R3-1 then Q-R3-1a): (a) — the emulator's own gRPC audio
   (`injectAudio` / `streamAudio`), proven by a build-start spike, typed-only plus the phone rows if the spike fails (see Decisions).
   Q-R3-1 asked A typed gate + spoken sub-rows when the audio route is opened (lean) / B typed only, spoken proof on the phone /
   C spoken rows stay gate rows / D Other; Jeremy's reply ("dont change the system audio but you can use it now") led to Q-R3-1a:
   A the emulator's gRPC audio (lean) / B phase 03's `audio.sh` route in windows he opens / C typed only, phone rows / D Other.

## Build tasks
1. Pager: the third page with named indices, initial page START, the Back / Home rules, the focus guard, the `[podbay] opened by
   swipe` / `closed by …` lines, the renamed `[start] home: page START` line and the `[start] page=<name>` line (T11-9, T14-8).
   The literal indices to re-cut (r3 D11): `rememberPagerState(pageCount = { 2 })` (`StartActivity.kt:141`) → 3;
   `animateScrollToPage(0, …)` in the Home collector (`:160`) → START; Back's `pager.currentPage == 1 -> … animateScrollToPage(0 …)`
   (`:180-182`) → `APP_LIST || POD_BAY -> START`; `if (index == 0)` (`:282`) → START; the app list's `canFocus =
   pager.currentPage == 1` (`:292`) → APP_LIST; the home line's text (`:166`). Left as is, `:180` would make Back on Start pivot
   to the pod bay
2. Pod frame: the page on phase 13's app-list backdrop (`acrylic:pod_bay`, its `[fluent] pod_bay` line, T14-11), the scroll,
   headers, rows, subheaders, empty lines, the layout tokens, tags; the ADD to phase 13's engine — `FluentSurface.POD_BAY` and a
   surface parameter on `AppListBackdrop`, the pod bay passing the app list's key arguments (A-N4, r3 D7)
3. The four pods on the existing feeds: Agenda, Weather, Now playing (with `MusicFeed.send` transport), Reminders, with the
   feed ADDs the pods read — `CalendarFeed.agenda` (r3 D5), `WeatherFeed` `State.stale` (r3 D6), `MusicFeed.now` and
   `Transport.PREVIOUS` (r3 D4); their taps through `launchApp(target, bounds, null)` as the app list's (r3 D8) and
   `CortanaService.open(context, mode)` → `showSession(args, 0)` with the Reminders destination as a new `EXTRA_*` in `args`
   (T14-6); every tap that opens nothing logs `[podbay] launch <id> failed: <why>` (T14-13)
4. Settings: the "Pod bay" page, the hub item, the per-pod switches in the settings store, the empty-bay line
5. Tess: `Request.OpenPodBay` / `ClosePodBay`, the matcher rule and hotwords, the `ActionLayer` branch and outcomes, the `LockGate`
   entries and captions, `PodBayRequests`, the session's close-time HOME intent with the `POD_BAY_CHECK` extra, and the
   `homeEvents` `PodBayCheck` consumption fed by `onNewIntent`, `onWindowFocusChanged` and `onWizardEnded` (held while the wizard
   shows, T14-7; r3 D1), the Brand strings, the five utterances
6. Harness: the `ensure_start` fix in all three helpers — `gestures.sh` `ensure_start`, phase 03's `lib.sh` `ensure_start`,
   phase 11's `q.sh` `ensure_start_page` (r3 V1) — and the re-grep (computed operands included, r3 V17), which also lists and
   updates every grep of Start's home line (`qa/phase-02/scripts/regress.sh:120` at the split, T11-9 / T14-5); the
   force-stop-after-launch rule (C-6); the regression run (E13); `qa/phase-14/baseline_layout-nomusic.json` for E4's
   launch-failure sub-row (T14-13); E17's driver (T14-12), which sources `qa/phase-12/scripts/p12.sh` for its ring reads (r3 V3);
   the audio route (Q-R3-1a (a)): `qa/phase-03/scripts/emu_audio.py` — a gRPC client of the emulator's own controller
   (`emulator_controller.proto`, shipped with the SDK) with `say <wav>` (`injectAudio`), `record <out> <secs>` (`streamAudio`) and
   `mic-state` (`getMicrophoneState`), which reads its endpoint only from the discovery file whose `port.serial=5554` — and the
   `AUDIO_ROUTE=emu` switch in phase 03's `speak.sh` (`:42`, `timeout 40 "$HERE/audio.sh" say`) and `lib.sh` `say` (`:293-299`),
   an ADD to phase 03's floor whose default path is unchanged; the audio spike, run first; evidence index `qa/phase-14/README.md`
7. ~~Only if Q1 is A or C: the Widgets pod kind (`AppWidgetHost`, picker, bind consent, configure, host lifecycle, persistence) —
   added at the interview with its rows; not built otherwise~~ Ruled out 2026-09-23 (Q1 B): no widget pod kind is built (T14-1)

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; `uiautomator dump` follows
RV13; "diagnostics" is read with phase 01's command, or — where notification access is revoked (E17) — through phase 12's Start
ring read (`qa/phase-12/scripts/p12.sh` `start_slice`, `:48-58`; a missing `tileshell diagnostics: <n> entries` header fails the
row; r3 V3). Every E row runs on the AOSP AVD `tileshell_fhd` (1080×2340 @ 450 dpi, no
Google) through adb on phase 03's driver floor (`qa/phase-03/scripts/lib.sh`). **Audio route (Q-R3-1a (a), 2026-09-29):** voice
rows never touch the host's audio — no `audio.sh setup` / `say` / `record`, no `pactl`, `paplay` or `parecord`, no `emu avd
hostmicon`. A spoken step is `qa/phase-03/scripts/speak.sh <id> <settle>` with its exit status asserted 0 — phase 03's one-request
script, which opens listening from `cortana_text_box_mic` unless `cortana_listening_box` already shows and exits 4 (`NO NEW
FINAL`) when the recogniser produced nothing; never bare `say`, which neither taps the mic nor checks a result, and `KEYCODE_ASSIST`
opens Tess in HOME mode, not listening (`CortanaService.kt:12-16`; r3 V2). Run with `AUDIO_ROUTE=emu`, `speak.sh` sends its
utterance into the AVD's microphone through the emulator's own gRPC `injectAudio` ("Injects a series of audio packets into the
Android microphone", `~/Android/Sdk/emulator/lib/emulator_controller.proto:334-348`, emulator 37.1.11), and a reply window is
captured through `streamAudio` (`:325-332`) — both by build task 6's `emu_audio.py`; the RMS is `audio.sh rms` on the captured file
(a file computation, no host audio). Every driver with a spoken step asserts `AUDIO_ROUTE=emu` before its first one and fails
otherwise, so no row can fall through to the host route. Voice rows prove the pipeline with a synthesised voice; Jeremy's own voice
and Tess speaking on the phone are P1 / H13. **Audio spike (build start, before any voice row; evidence `qa/phase-14/SPIKE-audio/`):**
`emu_audio.py` (1) takes the gRPC port and token files from the discovery file whose `port.serial=5554`
(`/run/user/1000/avd/running/pid_*.ini`; `tileshell_fhd` listed `grpc.port=8554` on 2026-09-29) and never from any other — the
second AVD, emulator-5556, is another project's; (2) `getMicrophoneState` reads host microphone access off (`injectAudio` returns
`FAILED_PRECONDITION` while another microphone is active, `:344`); (3) with Tess listening, injects `pod_bay_doors` → a new
`[speech] asr: final` holding "pod bay"; (4) captures a spoken reply through `streamAudio` with RMS above −40 dBFS. **If the spike
fails** (Jeremy's ruling): every spoken step below becomes its typed twin — `type_request` with the same words once the session is
open, E7's form — its RMS and C-30 clauses are recorded NOT RUN with the spike's evidence, the spoken proof is P1 / H13, and INDEX's
phase 14 row says so. **Ring reads (C-20):** every ring assertion reads `ring_since` from a MARK (`adb shell date +%s%3N`) taken
immediately before the step's action (after any clock jump, so the MARK is on the new clock); absence assertions read the same
slice; `reply_text` is `reply_since <MARK>` (the first reply after the MARK, empty if none — so a row whose request produced no reply
fails instead of reading an earlier row's identical line); helpers: phase 11's build task 7. **Voice verdict (C-30):** each spoken
step also saves `speech_dump` sliced from its MARK (`ring_since <MARK> speech`, the `:speech` process's own ring) and asserts that the
slice holds an `asr: levels … (heard=true)` line and no `asr: no speech in the capture …` line (`cortana/speech/SherpaAsr.kt:410-419`),
so a gate drop fails with its own reason, not as a matcher miss (r3 V16: with nothing decoded, the last `asr:` line is the levels
line with `heard=false`, which the old "last line is not `no speech`" clause passed); the five utterances are built through `utterances.py build`. **Wake (C-25):** after any `adb
reboot` (boot-completed poll), `dumpsys battery unplug` or `KEYCODE_SLEEP` step, the driver calls `wake_device` and asserts it printed
`Awake` before the next tap — except where the row's point is the lock screen (E8, E11's locked half), which wakes with
`KEYCODE_WAKEUP` alone and asserts `mWakefulness=Awake` in `dumpsys power` before its next tap, since `wake_device` dismisses the
keyguard. **Seeding (C-3):** rows that read Start's grid (E1, E10, E13) start with `layout_restore
qa/phase-02/baseline_layout.json` (`qa/phase-02/scripts/layout.sh`) — this phase adds no `addedOnce` marker and pins no fixture,
so phase 02's file, with its markers and hand-set sizes, is complete for this build — and assert zero `assignSlotOnce … ->
assigned` lines after it. **Launches (C-6, T14-3):** after any launch (E3's Now playing title tap, E9, the E13 re-runs that open an
app), `am force-stop app.tileshell` + Home before the next grid assertion. **Motion clock (C-5):** every motion the shell animates
logs its own `[motion]` clock and rows assert the logged numbers, a screenrecord only corroborating; this phase adds no motion of
its own (the pan and the request's settle are phase 01's X13, `Motion.PIVOT_SETTLE_MS`). **Page absence (T14-8):** "no
`pod_bay`" / "no `app_list`" assertions rely on an off-screen composed page being absent from the dump; that holds today
(`StartActivity.kt:278` `beyondViewportPageCount = 1`, r3 D10, and `qa/phase-02/scripts/gestures.sh:39` asserts `app_list` absent
on Start) and is re-checked at build start, because a Compose upgrade could change it; every such assertion also reads the
`[start] page=<name>` line as its second source. **Below the fold (r3 V15):** uiautomator dumps only what is laid out
(`qa/phase-03/scripts/lib.sh:222-225`), and with four pods of up to 6 rows the later pods sit below the fold on 2340 px, so every
per-pod presence, absence or row-count assertion is taken on a dump made after `scroll_to_node <dump> pod:<id>` (`lib.sh:226-237`,
the swipes noted in the log).
**Emulator:**
- E1 Pager: first `adb shell am start -n com.android.deskclock/.DeskClock`, `KEYCODE_HOME` (delivered: Start was not the resumed
  activity) → `start_page`, so the Back history holds DeskClock (phase 01 E20's seeding; r3 V11); from Start, `adb shell input swipe
  200 1200 950 1200 250` (a pan inside the page) → dump shows `pod_bay`, diagnostics `[podbay] opened by swipe`; `adb shell input
  swipe 900 1200 150 1200 250` → `start_page` and no `pod_bay`, `[podbay] closed by swipe` (r3 V13); a second left swipe →
  `app_list` (phase 01 E12 unchanged); from the pod bay `KEYCODE_BACK` → `start_page`, `[podbay] closed by back`; on Start
  `KEYCODE_BACK` still runs phase 01 E20's rule: `dumpsys activity activities` `topResumedActivity` is DeskClock (r3 V11), then
  `am force-stop app.tileshell` + Home (C-6); swipe right to the pod bay, then `tap_node nav_windows` (the drawn Windows key;
  `KEYCODE_HOME` is not re-delivered to the resumed home activity on this AVD, T11-16, and is phone row P5) → `start_page`, no
  `pod_bay`, the slice holds `[podbay] closed by home` and `[start] home: page START, scrolled to top` (the renamed line, T11-9;
  the drawn key emits `homeEvents(true)`, `StartActivity.kt:232-235`; r3 D3 / V1); each page reached in this row is also named by
  its `[start] page=<name>` line (T14-8)
- E2 Bars: on the pod bay `adb shell dumpsys window` shows the status and nav bar inset sources not visible (phase 01 E19's form) and
  the screencap shows the drawn bars; `tap_node` on the drawn Back key → Start; on the Windows key → Start
- E3 Pod content from fixtures, each asserted present and then gone; every per-pod read follows `scroll_to_node <dump> pod:<id>`
  (r3 V15). Agenda — phase 01's local "qa" calendar route (`qa/phase-01/scripts/edge_calendar.sh:13-20`: the calendar at access
  700, then `content insert … events`): "QA all day" today (all-day, `eventTimezone` UTC), "QA overlap one" today at a `dtstart`
  the driver picks, and "QA tomorrow" tomorrow at 09:30 local → `pod_row:agenda:0..2` texts written here by hand (r3 V23 / D5):
  "All day  QA all day", "<h:mm>  QA overlap one" (h:mm = the inserted `dtstart` formatted on the host), then
  `pod_subheader:agenda:tomorrow` above "9:30  QA tomorrow"; delete the events and the qa calendar → rows gone,
  `pod_empty:agenda` = "Nothing on your calendar today". Weather — phase 01 ITEM2's fixture report written into the feed's cache
  (`weather_fixture.py 3 day <out.json>`, pushed by `qa/phase-01/scripts/item2.sh:14-21` with the shell stopped — `ring_save`
  first, the ring resets; r3 V18), expected strings written here by hand and never taken from the shell's `WeatherFormat` (T14-2):
  `weather_fixture.py 3 day` writes place "Denver", current 64.0, WMO code 3, today's high 72.0 / low 51.0 and precipitation 30 %
  (`qa/phase-01/scripts/weather_fixture.py:26-45`), so the `pod_row:weather:*` texts contain, in this order, "Denver", "64°",
  "Cloudy", "H 72° L 51°" and "30%"; clock + 61 minutes (`qa/phase-01/scripts/e9_e12_clock.sh:19-24`: `adb root`, `auto_time 0`,
  `date -u`; MARK after the jump, C-20) → the "Updated h:mm" row within 180 s, h:mm = the fixture JSON's `fetchedAtMs` formatted
  on the host (X22; the pod follows the feed's own `State.stale`, r3 D6); restore `auto_time 1`, the clock, `adb unroot` (RV12).
  Now playing — phase 10's queue fixture (`qa/phase-01/scripts/music_lib.sh:16-28` `music_fixtures` + `music_open`: six tagged
  90-s MP3s in `MUSIC6-fixtures/`; phase 01 E8's one-file fixture has no next track; r3 V7), the first track played in phase 10's
  row form → `pod:nowplaying` title and artist equal the first fixture's tags, written here by hand from `MUSIC6-fixtures/`;
  `tap_node pod_control:nowplaying:PLAY_PAUSE` → `session_state` (`music_lib.sh:51`) PAUSED, `[music] control PLAY_PAUSE ->
  app.tileshell`, the play glyph shown (screencap), the pod still present; `NEXT` → the second fixture's title, `[music] control
  NEXT -> app.tileshell`; `PREVIOUS` → the first title again, `[music] control PREVIOUS -> app.tileshell`; `tap_node` on the pod's
  title → `.music.MusicActivity` (the MUSIC slot app) resumed (`dumpsys activity activities`) and `[podbay] launch nowplaying ->
  …`; Home, swipe to the pod bay; `cmd media_session dispatch stop` → `pod_empty:nowplaying` = "Nothing playing"; then `am
  force-stop app.tileshell` + Home (C-6). Reminders — phase 13's typed fixture (`qa/phase-13/scripts/reminders_fixture.sh`; phase
  03 E15's spoken setup makes no reminder row, INDEX row 03; r3 V6): `make_typed_reminder "remind me to check the QA14 today list
  at <h:mm> pm"` (h:mm a time later today the driver picks from the device clock), `make_typed_reminder "remind me to check the
  QA14 tomorrow list tomorrow at 9 am"`, and a Whenever reminder "check the QA14 whenever" through the Reminders page's add route
  as `reminders_setup` does (`:63-117`, no photo) → `pod_row:reminders:0..2` texts equal, written here by hand from
  `ReminderText.listSubline`'s forms (`ReminderText.kt:15-20`), never taken from the app: "check the QA14 today list" / "Today at
  <h:mm> PM", "check the QA14 tomorrow list" / "Tomorrow at 9:00 AM", "check the QA14 whenever" / no subline; `reminders_restore`
  (`:128-144`) → `pod_empty:reminders` = "No reminders — ask Tess to remind you". Launches (so E16's launch patterns have a
  producer, r3 V13), each from its own MARK: `tap_node pod_header:agenda` → the CALENDAR slot's app resumed and `[podbay] launch agenda -> <component>`, where the
  expected component is listed by the driver on the host, never taken from the app: the baseline pins no CALENDAR slot (its
  `slots` holds MUSIC only), so the slot is a category slot (`SlotResolver.kt:34-37`) and the driver asserts that
  `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR`
  lists exactly one activity (phase 01's form, `qa/phase-01/scripts/e4_part1.sh:15-16`; `com.android.calendar/.AllInOneActivity`
  on this AVD, `qa/phase-01/FINAL/E04/E04.txt:15`) — any other count fails the precondition, not the pod (lead, r3 V13;
  RE-CUT 2026-10-01 by phase 16's build, its r3 D8, INDEX Change Log: from that build the shell's Calendar is a second
  APP_CALENDAR handler and phase 16 seeds the slot, so the precondition is "the baseline's `slots.CALENDAR` is the shell's
  Calendar" — `qa/phase-16/baseline_layout.json` — and the expected component is read from that file on the host);
  `tap_node pod_header:weather` → `WeatherActivity` resumed and `launch weather -> …`; with a reminder present, `tap_node
  pod_row:reminders:0` → `cortana_session` on the Reminders page and `launch reminders -> …`; C-6 after each
- E4 Denied and empty states with diagnostics, both directions (a runtime revoke kills the process and resets the ring, so each
  revoke is preceded by `ring_save` and a MARK, which still slices the new ring; r3 V4): `adb shell pm revoke app.tileshell
  android.permission.READ_CALENDAR` → `pod_empty:agenda` = "Calendar access is off — turn it on in Setup" and `[podbay] pod agenda:
  empty: no calendar access`; `tap_node pod_empty:agenda` → `dumpsys activity activities` shows `SettingsActivity` resumed and, after
  `scroll_to_node … checklist:calendar:missing` (the checklist's `checklist:<id>:<state>` tag, `onboarding/Checklist.kt:186`; r3
  V19), the dump shows the Calendar row; `pm grant` → the rows of E3 return. `pm revoke app.tileshell
  android.permission.ACCESS_FINE_LOCATION` and `… ACCESS_COARSE_LOCATION` (location counts as on with either,
  `weather/WeatherLocation.kt:31-33`; BACKGROUND cannot outlive FINE) → `pod_empty:weather` = "Location is off — turn it on in
  Setup" and `[podbay] pod weather: empty: no location`, even with the cached report (r3 D6); restore `pm grant` FINE, COARSE and
  BACKGROUND and assert `dumpsys package app.tileshell` reads `android.permission.ACCESS_FINE_LOCATION: granted=true` (r3 V4). No
  session → `empty: no session`; no reminders → `empty: none`; weather never fetched → `empty: no report` (r3 D6). Each `[podbay]
  pod <id>: <n> rows` line matches the count of `pod_row:<id>:*` nodes in the dump taken after `scroll_to_node <dump> pod:<id>`
  (r3 V15). Launch failures (T14-13; one form, both assertions, r3 D9 / V12), each from a MARK before the tap: `layout_restore
  qa/phase-14/baseline_layout-nomusic.json` (phase 02's file with `slots.MUSIC` removed, `slot:music:v1` kept in `addedOnce` so the
  shell does not re-seed it) with E3's Now playing fixture playing → `tap_node` on the Now playing pod's title → `slot_picker` in
  the dump (`SlotPicker.kt:44`; the tap follows the tile, `StartActivity.kt:333`) AND the slice holds `[podbay] launch nowplaying
  failed: slot unassigned`; `KEYCODE_BACK` closes the picker; `layout_restore qa/phase-02/baseline_layout.json`. `adb shell cmd role
  remove-role-holder android.app.role.ASSISTANT app.tileshell`, a reminder present (E3's typed setup, r3 V6) → `tap_node
  pod_row:reminders:0` → the slice holds `[podbay] launch reminders failed: no session: not the assistant` and phase 03's
  `[cortana] open(…) cannot show a session … showing the role notice` (`CortanaService.kt:76-84`), `dumpsys activity activities`
  shows `CortanaRoleNoticeActivity` resumed (phase 03 H30), `dumpsys window` shows no session window; `KEYCODE_BACK`, Home;
  restore: MARK, `cmd role add-role-holder android.app.role.ASSISTANT app.tileshell` → the slice holds `[cortana]
  VoiceInteractionService ready` (`qa/phase-03/scripts/e1.sh:32`'s line) and `cmd role get-role-holders android.app.role.ASSISTANT`
  prints `app.tileshell`; `reminders_restore`
- E5 Settings: the hub shows `settings_pod_bay`; `tap_node settings_pod_bay` → `settings_page_pod_bay` in the dump (r3 V19);
  `tap_node pod_switch:weather`; Back to Start, swipe right → `pod:weather` absent, the other three present in the same order (each
  read after `scroll_to_node`, r3 V15), `[podbay] pods enabled: agenda,nowplaying,reminders`; all four off → `pod_bay_empty`
  present, `tap_node` on it → `SettingsActivity` on the Pod bay page (`settings_page_pod_bay` in the dump); `adb shell am force-stop app.tileshell`, Home → the switches persist (dump); restore all On
- E6 Voice, the easter egg (the audio route above, `AUDIO_ROUTE=emu`): from Start, `KEYCODE_ASSIST`, MARK, `speak.sh pod_bay_doors 11`
  (rc 0) → the slice holds `[match] "…" -> OpenPodBay(doors=true)`; `reply_since` MARK equals "I'm afraid I can't do that, Dave."
  and the `emu_audio.py record` (`streamAudio`) capture's RMS over the reply window is above −40 dBFS (phase 03's spoken reply pass
  rule); the `[podbay] opened by voice (doors)` line's `wall=` value is
  after the `[speech] speaking done <utteranceId> cancelled=false` line's `wall=` for that utterance (the launcher-ring literal,
  `cortana/speech/SpeechClient.kt:107`; T14-14) — both read from the launcher ring (`diag`) sliced from the MARK taken before the spoken step
  (`Diagnostics.dump` prints `wall=<ms>` on every line, `diag/Diagnostics.kt:40`, and the speech client writes into that ring), never
  from the host clock (T14-8, C-20); `dumpsys window` shows no session window and
  the dump shows `pod_bay`. Each of the following from its own MARK, each a `speak.sh` step: `pod_bay_open` → `OpenPodBay(doors=false)`, reply "Opening
  the pod bay.", `opened by voice`; `pod_bay_close` → `ClosePodBay`, reply "Closing the pod bay.", `start_page`, `[podbay] closed
  by voice`. Negatives (ruled 2026-09-30, INDEX Change Log: spoken, the name BEGINS "the pod" and the reply begins "I don't see an
  app called the pod"; the exact form is asserted typed in EDGE P3): `pod_bay_neg1` ("open the pod") → `[match] "…" -> OpenApp(name=the pod)` (phase 03's open-app rule,
  `CommandMatcher.kt:86`), `reply_since` MARK = "I don't see an app called the pod." (`ActionLayer.kt:212-219`), no `pod_bay` and no
  `[podbay] opened` in the slice (r3 D2 / V8); `pod_bay_doors_noart` → doors=true; no match line in the slices of the four
  pod-bay utterances contains `OpenApp`; every spoken step also passes the C-30
  voice-verdict check
- E7 Typed form: from Start (`ensure_start` with the Harness fix; `start_page` alone and no `pod_bay` asserted first, so the open
  can fail — r3 V9), `KEYCODE_ASSIST` → `cortana_session` in the dump, MARK, `type_request "open the pod bay doors" 8` → the same
  match line and `reply_since` MARK = the same reply text (empty fails: E6's identical line is before the MARK, C-20), the slice
  holds `[podbay] opened by voice (doors)`, the dump shows `pod_bay`; phase 03 E5's
  exported-components check re-run against `qa/phase-03/exported-allowlist.txt`: unchanged
- E8 Locked (the AOSP AVD's lock screen is disabled by `provision.sh:46`, and a PIN alone does not bring the keyguard back,
  `qa/phase-03/scripts/e10.sh:47-51`; r3 V5): `adb shell locksettings set-disabled false`, `set-pin 1234`, assert `locksettings
  get-disabled` = false; `KEYCODE_SLEEP`, `KEYCODE_WAKEUP` (the lock-screen exception to C-25: `mWakefulness=Awake` asserted,
  keyguard kept), assert `isKeyguardShowing=true` in `dumpsys window` (`e10.sh:52-62`'s form); Tess over the keyguard by
  `e10.sh:31-45`'s `lock_and_open` (`KEYCODE_ASSIST`, then `cmd voiceinteraction show` if no `cortana_session`), MARK,
  `speak.sh pod_bay_doors 11` (rc 0; the locked session opens listening, `qa/phase-03/scripts/e9.sh:104`) → `cortana_card:unlock` in the dump with caption "Open the pod bay" (`LockGate.restate`), `reply_since` MARK =
  "Unlock your phone to continue.", `dumpsys window` still shows the keyguard, no `[podbay] opened` in the slice; MARK2, `tap_node
  cortana_card_button:unlock` (`e10.sh:124`) → `isKeyguardShowing=true` (the bouncer), `adb shell input text 1234`, `KEYCODE_ENTER`
  (`e10.sh:128-129`) → `reply_since` MARK2 is the doors line and the pod bay opens (E6's checks on the MARK2 slice); restore in an
  EXIT trap: `adb shell locksettings clear --old 1234`, `locksettings set-disabled true`, `KEYCODE_WAKEUP`, `wm dismiss-keyguard`
  (`e10.sh:16-23`)
- E9 From inside another app: `adb shell am start -n com.android.deskclock/.DeskClock`, `KEYCODE_ASSIST`, MARK, `speak.sh pod_bay_doors 11`
  (rc 0, C-30) → after the reply (`[podbay] opened by voice (doors)` after `speaking done` in the slice, E6's order) `dumpsys activity activities` shows `StartActivity` resumed and the dump shows `pod_bay`; `KEYCODE_BACK` → Start; a second
  `KEYCODE_BACK` → DeskClock resumes (phase 01 E20: the Back history survived the detour); then `am force-stop app.tileshell` +
  Home (C-6)
- E10 Edit mode and the pivot: `enter_edit` on a tile (phase 02's `gestures.sh`) → `adb shell input swipe 200 1200 950 1200 250`
  opens nothing (dump unchanged, no `[podbay] opened`, no `[start] page=POD_BAY`); exit edit mode; on the pod bay `adb shell input
  swipe x y x y 1000` on a pod → dump unchanged, no `edit_disc:*`, no `quick_burst` (phase 11's burst, `StartActivity.kt:174-177`),
  no menu (r3 V21); phase 02 E3's folder-name step, opened and dismissed, from a MARK taken before the step → the pivot stays on
  Start (no `pod_bay`, no `app_list` in the dump, and no `[start] page=APP_LIST` or `[start] page=POD_BAY` in the slice — r3 V21:
  "the last `page=` line is START" passed with no line logged at all)
- E11 Screen-off and process death with the pod bay open: `KEYCODE_SLEEP`, `KEYCODE_WAKEUP`, `adb shell wm dismiss-keyguard` → `pod_bay`
  still the page; with a PIN set (`locksettings set-disabled false`, `set-pin 1234`, and `isKeyguardShowing=true` asserted after
  the wake — E8's form, r3 V5), sleep, wake, unlock (`wm dismiss-keyguard` to the bouncer, `input text 1234`, `KEYCODE_ENTER`) →
  still the page; `adb shell am force-stop app.tileshell`, `KEYCODE_HOME` → `start_page`; restore in an EXIT trap: `locksettings
  clear --old 1234`, `set-disabled true`, `KEYCODE_WAKEUP`, `wm dismiss-keyguard`
- E12 Gesture navigation: `adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural`; from Start a pan from x = 200 →
  `pod_bay`; back to Start; `am start -n com.android.deskclock/.DeskClock`, `KEYCODE_HOME` (delivered: Start was not resumed),
  MARK, a swipe from x = 2 inside the left gesture inset (`input swipe 2 1200 400 1200 250`) → Android's Back fires: `dumpsys
  activity activities` `topResumedActivity` is DeskClock, and the slice holds no `[podbay] opened` and no `[start] page=POD_BAY`
  (the no-history branch is struck — it cannot be told from a swipe that did nothing, `StartActivity.kt:439-445`; r3 V10); then
  C-6; restore `cmd overlay disable …` and assert `cmd overlay list` shows it `[ ]` (`qa/phase-11/scripts/e12.sh:8-9`, `:33-34`)
- E13 Regression on the same build, after the harness fix: `qa/phase-02/scripts/regress.sh` with every assertion passing (12/12 on the
  2026-09-22 suite, qa/JEREMY-QA.md P02), its home-line grep updated to `home: page START` (T14-5); a row that starts on the pod bay and
  one that starts on the app list and calls each fixed helper — `gestures.sh` `ensure_start`, phase 03's `lib.sh` `ensure_start`,
  phase 11's `q.sh` `ensure_start_page` (r3 V1) — → `start_page` alone; phase 01 E2 (Home shows Start), E12
  (swipe left → app list, search, jump grid), E19 (bars on all shell screens), E20 (Back on Start); with `AUDIO_ROUTE=emu` exported
  (phase 03's `e3.sh` and `e10.sh` speak only through `speak.sh`, `e3.sh:14,38`, `e10.sh:65-110`) phase 03 E3 (an utterance outside
  the list is still not understood), E5 (typed request and the allow-list), E10 (ruled 2026-09-30, Q-E10 (a), INDEX Change Log: run unchanged, and it passes here when no check that passed in phase 03's own last run fails now) (locked commands; "now with the pod-bay phrase" struck 2026-09-29, r3 V22 — E8 proves the locked phrase and phase
  03's `e10.sh` is not edited);
  `utterances.py build` succeeds with the five new ids; after every re-run that opened an app, `am force-stop app.tileshell` + Home
  (C-6), and after every `layout_restore`, zero `assignSlotOnce … -> assigned` lines (C-3)
- E14 Backdrop (T14-11): phase 13 E2's method on the pod bay, with phase 13's checkerboard fixture as the Start background (its
  `prefs_edit.py` route): swipe to the pod bay, screencap; the strip is phase 13 E2's (r3 V14): `dumpq.py clear_rows <pod-bay dump>
  675 945 $(dumpq.py checker_rows <dump> 1080 2340)` — a checker square-centre row clear of every text node in x 675..945, `p13.sh`
  `applist_strip`'s form on the pod bay (`qa/phase-13/scripts/e2.sh:43-47`, `dumpq.py:29-36`) — taken with no media session (the
  Now playing art square carries no text, so `clear_rows` cannot see it), the box for `acrylic_expect.py static` being `pod_bay`'s
  bounds: the edge spread is the blurred width (2.563 · σ, σ = 0.57735 · r + 0.5, r = 30 epx · px/epx) ± 20 % and the
  pixels are 0.2 × (blurred checker) ± 3 (T = (0,0,0), α 0.8; B from phase 13's `acrylic_expect.py`); in the same capture a pod
  header's and a pod row's text edges are sharp (≤ 2 px); `acrylic:pod_bay` is in the dump with the page's bounds; the ring slice
  from a MARK before the swipe holds `[fluent] pod_bay source=static tint=(0,0,0) alpha=0.8 blur=30epx`. Shared key (A-N4; r3 V14 /
  D7): after the first `[fluent] pod_bay …` line, `ring_save`, MARK, Start → app list → Start → pod bay → Start → app list → Start
  (E1's swipes) → the slice holds zero `[fluent] static backdrop rebuilt for` lines (`StaticBackdrop.kt:93-96`). Acrylic off:
  `ring_save`, `set_pref transparency_effects boolean false` (`p13.sh:17-25`, which force-stops the shell — the ring resets), Home,
  swipe to the pod bay → edges sharp and pixels 0.2 × checker ± 3, `acrylic:pod_bay` still present. Restore `set_pref
  transparency_effects boolean true` and the background (`clear_background`, `p13.sh:48-52`)
- E15 RV10: `wm size 1440x3120` / `720x1560`, `wm density 560`, `font_scale 1.3` leave every pod node's bounds in epx unchanged ± 1 epx
  (phase 01 E3's method, one dump each); restore; ruled 2026-09-30 (Q-E15 (a), INDEX Change Log): ± 1 epx is each node's left, top and bottom, and its right
  edge too unless the node carries text — a text node's right edge is the text's own width and has ± 1.5 epx
- E16 Every diagnostics line in Decisions is asserted by at least one row above — grep the union of `qa/phase-14/*/ring-*.txt` saved
  by this build's run (the rows whose log's APK id matches), each pattern at least once; never one final ring, which every `am
  force-stop` / `layout_restore` resets (`diag/Diagnostics.kt:14-26`; C-20). The patterns, literally (r3 V13): `opened by swipe`,
  `opened by voice`, `opened by voice (doors)`, `closed by back`, `closed by home`, `closed by swipe`, `closed by voice`; for each of
  `pod agenda: `, `pod weather: `, `pod nowplaying: `, `pod reminders: ` one ` rows` line and one `: empty: ` line; `pods enabled: `;
  `launch agenda -> `, `launch weather -> `, `launch nowplaying -> `, `launch reminders -> `; `launch nowplaying failed: slot
  unassigned`, `launch reminders failed: no session`; `[start] page=POD_BAY`, `[start] page=START`, `[start] page=APP_LIST`;
  `[start] home: page START`; `[fluent] pod_bay source=static`. `launch <id> failed: ActivityNotFoundException` is written and
  exercised by no row — recorded (C-26), not asserted
- E17 Pod-bay request under the wizard (T14-7, T14-12; phase 12 E14 (a)'s form, C-15); every ring read in this row goes through
  phase 12's Start ring read — `qa/phase-12/scripts/p12.sh` `start_slice <MARK>` (`:48-58`), because the revoked listener leaves
  `diag` empty (`qa/phase-03/scripts/lib.sh:155-159`) — and a missing `tileshell diagnostics: <n> entries` header fails the row, so
  no absence assertion can pass on an unreadable ring; the reply is the first `[speech] speak[…] text="…"` line in that slice (r3
  V3): `pm clear app.tileshell` →
  `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` (every grant, no finished marker) → `adb shell cmd notification
  disallow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener` → Home: the dump has `wizard_page`; `tap_node
  nav_search` opens Tess (`cortana_session`, phase 12 E8), MARK, `type_request "open the pod bay"` → `reply_since` MARK = "Opening
  the pod bay." and `dumpsys window` shows no session window; the slice from MARK holds no `[podbay] opened` and no `[start]
  page=POD_BAY`, and the dump still shows `wizard_page` and no `pod_bay`; MARK2, `tap_node wizard_skip` → the slice from MARK2 holds
  `[wizard] skip` and then `[podbay] opened by voice` with a later `wall=`, and the dump shows `pod_bay`. Restore: `pm clear` →
  `provision.sh` → Home

**Phone-only:** P1 Jeremy's own voice on "open the pod bay doors" (the grammar hotword) and Tess SPEAKING the line on the S25 Ultra
(TTS on the phone is unproven until a CI build is installed, INDEX 2026-09-22); P2 One UI gesture navigation: a pan opens the pod bay,
the left-edge swipe is One UI's Back, and the hint area does not take the pan; P3 Samsung Calendar's events in the Agenda pod and a
Samsung media session (Samsung Music, YouTube Music) in Now playing with its transport; ~~P4 (only if Q1 is A or C) Samsung's own
widgets bind, draw and update in the bay~~ ruled out 2026-09-23 (Q1 B, T14-1); P5 `KEYCODE_HOME` from the pod bay → Start,
`[podbay] closed by home` (the HOME-intent path, which this AVD does not re-deliver to a resumed home activity, T11-16; r3 D3 /
V1 — numbered P5 because P4 is the struck widget row); added 2026-09-30 (INDEX Change Log): P-L14-1 Tess over the lock screen, the microphone open, speak and tap Unlock
mid-sentence — after the PIN the request on the card runs and nothing is answered behind the PIN pad; and, by Jeremy's ruling,
the Edge cases the AVD cannot drive: P6 Home while the pane is mid-swipe; P7 a reminder firing, and one completed, while the pane
shows; P8 a place and a person reminder's subline; P9 a second request in one Tess session; P10 a pan during a live flip; P11 Show
more tiles, theme, accent, the X5 slider; P12 a pod switched off while its feed updates

**NEEDS-HUMAN (all accept rows):** H1 the pod bay as a whole (P4 design, no W10M original); H2 the pod frame — type-only Metro
cards, spacing, accent headers (P4); H3 each pod's content choices, caps and the paused-session rule for Now playing (P4); H4 the
empty-state wording (P4); H5 the Settings page — switches, fixed order, the empty-bay line (P4); H6 the doors line's exact wording
and delivery — spoken on a card, then the session closes, then the pane opens; "Dave", not Jeremy's name (film reference); H7 the
plain replies "Opening the pod bay." / "Closing the pod bay." (agent); H8 Back from the pod bay going to Start rather than the last
app (approximation, an ADD to phase 01's Back rule); H9 the pod bay keeping its page across screen-off and lock (agent); H10 the pod
bay on phase 13's app-list backdrop rather than Start's wallpaper (agent); H11 pod launches with no Start exit (the grid is not on screen) and phase 01's entrance on return, as the app list's (agent;
re-worded 2026-09-29, r3 D8); H12 accept
any approximation not covered by H2–H11; H13 Tess speaking the line on the phone (P1)

## Edge cases
- Pane open + edit mode: the pivot is locked, so the pod bay is unreachable while edit mode is on; a hold on a pod does nothing;
  the folder-name box never swings the pivot to the pod bay (the phase 02 gate's defect, second direction)
- Back / Home / screen-off / lock / process death with the pane open (Decisions); Home while the pane is mid-swipe (settles on
  Start); ~~a pod launch while the Start exit is already playing (ignored, as a second tile tap is)~~ struck 2026-09-29 (r3 D8: a
  pod launch plays no Start exit)
- A pod's feed absent: weather never fetched; calendar access denied; no media session; no reminders — each shows its line and its
  diagnostics reason, never a blank pod; a feed that arrives while the pane is showing (the pod fills in place)
- Content limits: 20 calendar events (cap 6, the page scrolls, the pod does not); long titles and artist names (one line, ellipsised);
  a session that publishes no art (the art square draws the tile fill with the music glyph); a session paused for hours (shown paused,
  H3); a reminder that fires or is completed while the pane shows (its row goes); a Whenever reminder; a place or person reminder
  (its subline as on the Reminders page)
- The phrase over the keyguard (E8), typed (E7), from inside another app (E9), while the pod bay is already open (the line is spoken,
  nothing moves), "close" on Start (the line is spoken, nothing moves); the session dismissed mid-line (the utterance is cancelled,
  the pending open is still consumed when Start's window focus returns, r3 D1); a second request in the same session (replaces the pending one); "open the
  pod" / "open the bay" (phase 03's `OpenApp` — "I don't see an app called …" — never the pod bay, r3 D2); "open pod bay doors" without the article (doors); ASR returning "podbay" as one word (still
  matched); the grammar hotword present but the open pass winning with a different transcript (E3's diagnostics show which)
- The HAL strings in the A10 branding module: changing `Brand.POD_BAY_NAME` changes the page title, the Settings entry, the empty-bay
  line and the phrase set together (a JVM test asserts the matcher follows the Brand value, and that `CommandMatcher.hotwords()`
  holds "open the pod bay doors", "open the pod bay" and "close the pod bay doors" — the grammar pass's input, which no AVD row
  reads directly; r3 V2)
- Gesture navigation: the pan vs the left-edge Back (E12); a pan that starts on a bottom-row tile (the pager still pans, as it does on
  Start today); a pan during a live flip
- Show more tiles toggled, theme light / dark, the accent changed (headers follow), the X5 transparency slider (pods are not tiles and
  do not follow it); RV10 (E15)
- Pod switches all off (the empty-bay line); a pod switched off while its feed updates (nothing drawn, nothing logged for it)
- ~~If widgets are in (Q1 A or C): provider uninstalled, configure activity cancelled, bind refused, host `startListening` after process
  death, widget size classes against the epx canvas, Samsung's Font size changing a widget's text (RV10 cost, recorded)~~ Ruled out
  2026-09-23 (Q1 B, T14-1)
- A pod-bay request (voice or typed) made while phase 12's setup wizard shows: nothing moves behind the wizard; the pane opens once
  the wizard finishes (T14-7; proven by E17 since 2026-09-23, T14-12)

## QA evidence
