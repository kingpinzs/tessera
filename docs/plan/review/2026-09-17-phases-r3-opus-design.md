# Phase-doc review, round 3 (last round): Reviewer 1 (opus), design / correctness / Android-Samsung feasibility, 2026-09-17

Read in full: PLAN.md, INDEX.md, phase-01 through phase-09, review/2026-09-16-phases-r2-{opus-design,opus-test,triage,owner}.md.
Research opened only to check named claims: r7-measurements.md §1.1, §1.5–1.7, §2.3, §2.5, §2.7, §3.1–3.9; r6-measurements.md §2.1.16–2.1.17,
§3.4.1–3.4.4. Web: AOSP Geocoder.java. Phase 04 was reviewed for design consistency only. Default verdict on every claim: not proven.
Line numbers are as of 2026-09-17 (`Lnn` = line in the named file).

Counts (new findings): **BLOCKING 5 · MAJOR 5 · MINOR 7**. Round-2 items not resolved: **R2D-06 (phase 09 part)**.

---

## Part 1: Round-2 BLOCKING / MAJOR status

| ID | Status | Evidence in current docs |
|---|---|---|
| R2D-01 glance over an unlocked phone (B) | RESOLVED | phase-07 L27 "glance starts only once the keyguard is actually locked (`KeyguardManager.isKeyguardLocked()` true), never on the screen-off event alone"; E6 L53 (`lock_screen_lock_after_timeout 5000`, `screen_off_timeout 15000`, wake inside the delay); P4 L59 at 5 s / 30 s; edges L69–70; H11 |
| R2D-02 reflow vs folder create (B) | RESOLVED | phase-02 L31 "that tile holds still for a dwell of 2000 ms and shows the folder-create feedback; a release during the dwell creates a folder … only then do the tiles there slide out of the way"; L34 dwell tie-in; E8 L64 runs both paths; H20. Leftover: see R3D-06 |
| R2D-03 Back history exclusions / boot | RESOLVED | phase-01 L83 excludes "`CATEGORY_HOME` (One UI Home, Settings' `FallbackHome`), SystemUI, the permission controller, the package installer, the resolver / chooser or an input method"; L84 "the later of … `KEYGUARD_SHOWN` and the user unlock"; E20 L192 reboot step captures `dumpsys usagestats`; phase-06 L34 and phase-07 L32 ADD their activities |
| R2D-04 speech model storage / process | RESOLVED | phase-03 L41 "That process is `app.tileshell:speech` … the launcher process never loads a speech model"; E12 L112; P4 L132; phase-05 L39, phase-06 L35 bind it; phase-08 L31 "latest baseline + 50 MB" |
| R2D-05 locked command mechanisms | RESOLVED | phase-03 L52 "the locked gate lives in this phase's action layer, not in the matcher … `EXTRA_SKIP_UI` as a voice activity … `TransportControls.playFromSearch` … directions, taking a photo, taking a note and adding a calendar event show 'Unlock to continue'"; E10 L110; P6 |
| R2D-06 locked LLM and memory | **NOT RESOLVED (phase 09 part)** | Owner ruling A applied in phase-08 L34, E2 L53, H3, edge L65. Phase 09 adds nothing on the read side: its only locked lines are writes (L36, E7 L74, edge L88). Phase 08's E2 also checks a Notebook memory that does not exist until phase 09. → R3D-03 (BLOCKING) |
| R2D-07 VVM helper verbs trust | RESOLVED | phase-06 L31 "Trust-touching (agent): the visual voicemail verbs … get adversarial team review before `done`"; P5 L92; edge L111; phase-04 Scope L18 and P1 L86 include the voicemail probe |
| R2D-08 glance hold / turn-off / re-trigger | RESOLVED | phase-07 L28 (keep-screen-on + 0.01 override; `GLOBAL_ACTION_LOCK_SCREEN`; re-trigger guard); depends-on `[01, 04]`; E7 L54 asleep ≥ 60 s; P5; L24 "if always-on exceeds the ceiling, Jeremy is re-asked" |
| R2D-09 glance settings page | RESOLVED | phase-07 L29 (15063 page, duration list H6, charging, background = Start image H8, night mode 50 % H9); L30 defaults H7; E8–E10 L55–57 |
| R2D-10 glance date / row / format / burn-in | RESOLVED as tagging | phase-07 L31 date 0.776 H, row 0.842 H, "h:mm"; L26 cycle spanning the height citing A21; E3 L50. The new cycle breaks E3: → R3D-05 (BLOCKING) |
| R2D-11 locked Cortana look | RESOLVED | phase-03 L53 "opens directly listening on the black page with the non-personalised greeting 'What's on your mind?', no ≡ menu"; E9 L109; H11 |
| R2D-12 keyboard behaviours / languages | RESOLVED | phase-05 L41 "English (US) only … no language key and no space-bar language switch"; L42 behaviour list; E9–E12; H13–H23. Leftovers: R3D-09, R3-m1, R3-m2 |
| R2D-13 reminder card / reminder kinds | RESOLVED (card and ruling applied) | phase-03 L58 card per R6 §3.4.2 with Remind / Cancel, recurrence H19, photo; E7 L107; ruling applied L64, task 12 L94, E13–E14 L128–129, P11–P12. Leftovers: R3D-01, R3D-02, R3D-07, R3D-08, R3D-10 |
| R2D-14 text box / session bars | RESOLVED | phase-03 L55 (§3.3.1–3.3.9 tagged, H13/H14), L56 "a Cortana session hides the system status and nav bars and draws phase 01's W10M status bar", L57 H15; E5 L105; E11 L111 |
| R2D-15 "New" caption placement / rules | RESOLVED | phase-01 L86 "(R6 §5.1.3–5.1.5, 15063, S2 … MEDIUM, ± 1.4 epx)"; X14; E12 L184; L81 §5.1.7 note; phase-02 L38, E2 |
| R2D-16 drawn bars / Back motion | RESOLVED | phase-01 L87 "status bar 28–29 ± 1.1 epx tall (R3 C4 …)", X16, X17 three slots; L88 X18; E19 L191 |
| F1-M12 (phase 04 part, carried) | RESOLVED | phase-04 Approximations Y1–Y12 L61–74, each with H4–H15; E4 L82 against R7; INDEX L18 "phase 04 does not freeze with the others" |
| F2-M5 (phase 09 part, carried) | RESOLVED | phase-09 L40 "each stored test completes … in ≤ 5 s … a whole run of N tests takes ≤ 5 × N s"; P2 L79 |

---

## Part 2: New findings

### BLOCKING

#### R3D-01 · BLOCKING · Phase 03 · the "I'll remind you." card (R7 §3.8.1) is rendered but untagged
- **Claim:** phase-03 L58: `"yes" or Remind → the page "I'll remind you." with the reminder listed`.
- **Evidence:** R7 §3.8.1 (14393, N2 @ 866.5–868.6 s, MEDIUM) measured this card: the small persona at the top; title "I'll remind you." in accent (68,106,228), cap 16.7 epx (≈24-epx type), cap top 94.6 epx; caption "Reminder" in grey (140,145,139), cap 7.9 epx, top 133.9 epx; a row with the lightbulb-with-check icon and the reminder text in white, cap 17.1 epx at 169.2 epx, left 62.9 epx; subline "Tomorrow - 8:00 AM" in grey (150,155,151), cap 10.5 epx at 198.0 epx, written with a hyphen where the list page uses "at"; the "Ask me anything" query bar below it. R7 §3.5.6 adds that this card showed the lightbulb even for a timed reminder, while the list shows a clock. No phase doc cites §3.8, E7 L107 checks only that the reminder is stored, and PLAN L244–246's R7 follow-up moved §3.1–3.7 into phase 03 but left §3.8 out. RV9 (PLAN L177–178): "Phase docs go FINAL only with every value tagged".
- **Fix:** add a phase 03 Decisions line tagging §3.8.1 (14393, N2, ± 1.1 epx, MEDIUM): the lightbulb icon on every saved-reminder card and the hyphen subline form. The place and person sublines ("When I arrive at <place>", "Next time I talk to <contact>", as in L72) are approximations under H26 or a new H-row. Extend E7 to measure the card after "Remind" / "yes" (dump bounds, screencap, pixel colours). Add §3.8 to PLAN L244–246's list.

#### R3D-02 · BLOCKING · Phase 03 · place reminders have no defined, offline and Google-free source for a place, so E13 has no real setup path
- **Claim:** phase-03 L64 "a place is a saved address or a Notebook place, radius 150 m"; E13 L128 `"remind me to take out the trash when I get home" with home saved`; L15 "Cortana itself makes no network request"; PLAN P5 "NO to anything google that I dont have to have".
- **Evidence:**
  1. No doc says where or how an address is saved. Phase 03's Settings page holds "voice picker, Notes slot, 'Lock screen options'" (L73). Build task 12 (L94) has no places UI. Phase 09's Notebook rows are only "Memories, Corrections, Rules, Hooks", and it leaves W10M's "About me" out (phase-09 L45). So "a Notebook place" does not exist in any phase.
  2. `LocationManager.addProximityAlert` takes latitude, longitude and radius, so an address needs a geocoder to become coordinates. Android's framework does not ship one; it only reports whether a device has one: "Returns true if there is a geocoder implementation present on the device that may return results" (AOSP `Geocoder.isPresent()` Javadoc, https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/location/java/android/location/Geocoder.java). On Samsung phones that implementation is Google Play services, which looks addresses up over the network, and the AOSP (non-Google) AVD is not shown to have one. So "saved address → coordinates" is not proven offline or without Google, which conflicts with L15 and P5.
  3. The spoken "when I get home" needs a name → place mapping that nothing defines. E13 could therefore only create "home" through a test-only path, which Rule 4 forbids.
  4. Nothing re-registers proximity alerts after a reboot or an app update. Edge L152 lists only "arriving while the phone is rebooting".
- **Fix:** a Decisions line and a build task stating how a named place gets coordinates, marked as a P4 design with its own H-row. The agent default if Jeremy picks (a) below: a "Places" page in Cortana's Settings with Home, Work and custom names, each saved from a current `LocationManager` fix ("Use my current location"), plus the spoken "remember this place as home". The place card offers this page when a spoken place is unknown. Proximity alerts are re-registered at `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. E13 saves Home through that page after `adb emu geo fix`, then runs as written. Replace "a Notebook place".
- **Owner question (WHAT):** Place reminders need to know where "home" is, and with no internet and no Google the phone can't turn a typed address into a map position. (a) You save a place by being there: "Save this spot as Home" on a Places page, or by voice. (b) Typed addresses too, looked up once online through a non-Google service (OpenStreetMap), which adds an internet use to A11. (c) Other / let me clarify.

#### R3D-03 · BLOCKING · Phases 08 / 09 · the locked-AI ruling: E2 checks a memory that doesn't exist until phase 09, nothing makes the model show the card, and phase 09 adds no locked rule for reading (R2D-06, phase 09 part)
- **Claim:** phase-08 L34: a request needing personal data "shows 'Unlock to continue' … enforced in the same action-layer gate plus a locked-mode context that excludes phase 09's memory providers". E2 L53: "a question whose answer lives only in a Notebook memory show 'Unlock to continue' … the runtime log shows no memory provider was read while locked".
- **Evidence:**
  1. Phase 08 depends on `[03, 04]`, and the memory store is phase 09's (phase-09 L20, build task 1). At phase 08's QA there is no Notebook memory and no memory provider. INDEX L47 says the "Next phase starts only after that", so E2 cannot pass and phase 08 cannot reach `done`.
  2. The action-layer gate fires only when the model calls an action. With memory kept out of the context, "what did I say I like" leaves the model nothing to call, so nothing produces the card; a small model will most likely answer "I don't know". Edge L65 lists this case with no outcome. And "when is my next meeting" is likely caught by phase 03's fixed "what's on my calendar" command before it reaches the LLM, so E2 would exercise phase 03's gate, not phase 08's.
  3. Phase 09 has no locked rule on the read side. Its "session start (inject context)" hooks (L24) and its context providers and recall (L20, L26) have no locked exclusion, no edge and no E-row. Its locked lines cover writes only (L36, E7, L88). E2 also names no input route over the keyguard (the request set is typed, L30).
- **Fix:**
  - **Phase 08 Decisions:** while the keyguard is locked, the prompt carries no personal context, and the model's action list keeps the personal-data actions (calendar read, contact lookup, messages, and a memory-recall action that phase 09 ADDs). The action-layer gate turns each of those calls into "Unlock to continue". Name the input route (typed in the session over the keyguard, or spoken).
  - **Phase 08 E2:** use a free-form personal question that diagnostics show reached the not-understood handler (not phase 03's matcher). It gets the card, and "how far is the moon" is answered.
  - **Phase 09:** ADD an E-row. Push a memory with the PC script, lock the phone, and ask about it: the card shows, and diagnostics show no memory provider, recall or session-start / before-prompt hook ran. Unlock, and it is answered. Add a Decisions line and an edge saying phase 09's providers, recall and context-injecting hooks do not run while locked.

#### R3D-04 · BLOCKING · Phase 06 · Phone and Messaging values measure against drawn W10M bars that no phase 06 decision draws
- **Claim:** phase-06 L36 "the AVD (1080x2340 at 3 px/epx, RV10), 780 epx tall, with phase 01's 48-epx drawn nav bar (X6). Distances given from the screen top stay fixed". Other lines measure against those bars:
  - L38: app bar "on the nav bar"; held tab cell "from 21.4 to 73.1 epx"
  - L41: "Call bar … full width on the nav bar"; "the tab header scrolls off so the caption line sits under the status bar"
  - L44: "Slide up bar … sitting on the nav bar"
  - L46: "all stacked flush on the nav bar"
  - L49: "directly under the status bar"
- **Evidence:** phase-01 L64 says "inside other apps Samsung's 3-button bar stays", and L114 says "inside other apps Samsung's bars remain (M4)". Only Start (phase-01 L75) and the Cortana session (phase-03 L56, "Session bars (agent, same treatment as Start, P2)") hide the system bars. Phase 06's Scope (L16), build tasks (L62–67) and E1–E19 have no bar decision and no `dumpsys window` insets row. If Samsung's bars stay:
  - targetSdk 36 draws edge-to-edge, so values "from the screen top" (the held tab cell starts 21.4 epx down) sit under Samsung's status icons, or need an inset offset that nobody defined.
  - "On the nav bar" would mean Samsung's bar, whose height is not X6's 48 epx. E9's "≈101 epx on the AVD" and every "flush on the nav bar" check would measure against the wrong reference.

  R7's captures show W10M's own bars in these apps (R7 §2.3.2: "directly under the 29-epx status bar"). R7 §1.5.1 shows the incoming-call screen over the lock screen with "the lock-screen variant (camera / Start / Search)" nav bar, which no doc mentions.
- **Fix (agent, P2, same as phase-03 L56):**
  - **Decisions:** the Phone, incoming-call, in-call and Messaging windows hide the system status and nav bars and draw phase 01's status bar and the Back / Windows / Search nav bar (an ADD).
  - **Incoming call over the keyguard:** either R7 §1.5.1's lock-screen nav-bar variant (LOW candidate, H-row) or the normal bar (approximation, H-row).
  - **Keyboard up:** the compose row sits on the IME, and the drawn nav bar is below it.
  - Add a Scope / task item and an E-row in phase-01 E19's form for History, Dial pad, incoming call (unlocked and over the keyguard), in-call and a Messaging thread.
  - If hiding the bars over the keyguard fails, record that and measure from the window edges, as phase-03 L56 does.

#### R3D-05 · BLOCKING · Phase 07 · the re-derived burn-in cycle cannot put the clock where E3 measures it
- **Claim:** phase-07 L26: "a fixed cycle of 8 positions whose block top steps evenly from 0.10 of the screen height down to the lowest position that keeps the whole block on screen and back up … the cycle starts at the step nearest the §6.1.1 candidate position". E3 L50: "at the burn-in cycle's first position … clock digit top at 0.646 ± 0.016 of the screen height".
- **Evidence:** the block runs from the digit top (0.646 H) to the bottom of the notification row (549 / 640 = 0.858 H). It is ≈0.21 H tall, so its lowest top is ≈0.79 H.
  - Eight positions going down and back up give 5 levels about 0.17 H apart: 0.10, 0.27, 0.44, 0.62 and 0.79. The nearest to 0.646 is 0.62, which is 0.03 H away.
  - Eight distinct levels give steps of 0.098 H. The nearest are 0.59 and 0.69, at least 0.044 H away.

  Both readings miss E3's ± 0.016 H band (± 10 epx of 640), so E3 fails by construction. The date and notification-row fractions measured "at the first position" move with it. L26 also doesn't say whether "block top" means the digit top or the text box top.
- **Fix:** anchor the cycle so one of its 8 positions is exactly the §6.1.1 candidate (digit top 0.646 H, inset 29 epx). The other positions step evenly from there up to 0.10 H and down to the lowest position. Say that "block top" means the digit top. E3 measures at the anchor. Alternatively, E3 measures the date and row centres relative to the digit top (0.130 H and 0.196 H below it) and the digit top against the computed step.

### MAJOR (each cheap to fix)

#### R3D-06 · MAJOR · Phase 02 · no gesture adds a tile to an existing folder, yet the doc renders 4-member folders
- **Claim:** phase-02 L34 "member icons as mini tiles in a 3-column grid … 4 members → 3 + 1"; L31 "a release during the dwell creates a folder"; Scope L17 "live folders (create, rename per interview, expand, remove, dissolve)".
- **Evidence:** only a two-tile create is defined, and E3 L59 builds only a two-tile folder. No line adds a third tile. The dwell rule, applied to a folder tile, would create a folder inside a folder. No defined gesture can build the 4-member folder whose face L34 specifies.
- **Fix:** add these rules:
  - A release during the dwell over a folder tile adds the dragged tile to that folder. The folder shows the folder-create feedback (approximation, H-row).
  - A tile dropped into an expanded folder band is added at the drop cell.
  - Folders never nest: a folder tile dragged over a tile or folder only reflows.

  Add to E3 / E8: a third and a fourth tile added, with the 3 + 1 mini-tile face checked. Add edges: a wide tile added to a folder, and a folder dropped on a folder.

#### R3D-07 · MAJOR · Phase 03 · place and person reminder cards and their spoken wording are undefined; H22 has no stand-in
- **Claim:** build task 12 L94 "place and person fields on the reminder card"; H22 L134 "place / person reminder cards look like W10M's (approximation where R6 §3.4.2 has no place/person variant)".
- **Evidence:** L58 describes only the time card ("outlined fields for the reminder text, the time and the day"; spoken "Remind you to <reminder> at <time> <day>. Sound good?"). Nothing defines the place or person card's fields, whether recurrence and "Add a photo" show there, the read-back, the prompt for an unknown place or contact, or a contact with several numbers. E13 and E14 (L128–129) check only that the reminder fires, so the reply pass rule (L62) has no string to compare. The only W10M capture of a place / person entry is R7 §3.5.11 (14322, LOW, not governing): a "Person Place or Time" row on the + form. No doc cites it.
- **Fix:** one Decisions line, as an approximation under H22:
  - **Place card:** the time card with one place field (a saved place's name) in place of time and day. Spoken: "Remind you to <reminder> when you get to <place>. Sound good?". An unknown place goes to R3D-02's Places route.
  - **Person card:** R6 §3.4.1's contact chip in place of time and day. Spoken: "Remind you to <reminder> next time you talk to <contact>. Sound good?".
  - Say whether recurrence and photo apply to either card.

  Cite R7 §3.5.11 as a LOW, non-governing reference. Extend E13 and E14 with the card dump and the reply text.

#### R3D-08 · MAJOR · Phase 03 · E15's reminder "without a time" has no way to be created through the card
- **Claim:** E15 L130 "Setup through the reminder card: a reminder later today with a photo, one for tomorrow and one without a time"; L58 "Missing time … the card title reads 'When would you like to be reminded?'"; E7 L107 "a reminder without a time shows 'When would you like to be reminded?'".
- **Evidence:** the only defined response to a request with no time is R6 §3.4.3's prompt for a time, so the card can never store an untimed reminder. R6 §3.4.3 shows the prompt and nothing else. L71's + page ("save enabled once the text field has text") could store one, but E15 says "through the reminder card". So E15's "Whenever" row, and its lightbulb-icon check, cannot be set up as written.
- **Fix:** either set up the Whenever reminder through + (L71) in E15, or add a card rule: at "When would you like to be reminded?", saying "no time" / "whenever", or tapping Remind with the time empty, stores a Whenever reminder (approximation, with H16 extended), checked in E7.

#### R3D-09 · MAJOR · Phase 05 · holding &123 opens the one-handed menu and also starts slide-to-type, with no rule to tell them apart; letter alternates cite the wrong R6 row
- **Claim:** phase-05 L42 "accent/alternate characters on long-press (R6 §2.1.17 shows the long-press dots; the popup look is an approximation, H19), one-handed mode docked left or right (H20), &123 slide-to-type (H21), caps lock by double-tapping Shift (H22)"; E12 L77 "one-handed left and right shift the key grid … sliding from &123 to '1' and releasing commits '1'".
- **Evidence:** R6 §2.1.17 is "&123 long-press dots: three ≈1-px dots at the key's top-left" of the &123 key, not of the letter keys. Microsoft's guide, quoted in R2D-12 from R6 D1 l.1596–1605, opens one-handed mode with "tap and hold the numbers and symbols key or language key". Slide-to-type also starts by holding &123: "while holding the numbers and symbol key, slide your finger to the character, and lift". With the language key ruled out (L41), both behaviours start from the same hold. No line says how the IME tells them apart, what the one-handed choice looks like, or how wide the docked grid is. H19, H20 and H22 name no stand-in values, so E12 has no defined way to open one-handed mode.
- **Fix:** add a Decisions rule:
  - Touch-down on &123 shows the symbol layer at once.
  - Moving off &123 before lift commits the symbol under the finger (slide-to-type).
  - Holding still past the long-press timeout opens a small flyout with dock-left / dock-right / full-width choices (approximation, H20).
  - Name the docked geometry stand-in, the alternates popup stand-in (H19) and the caps-lock indicator stand-in (H22).
  - Cite §2.1.17 for the &123 hold only.

  E12 drives each path with `input motionevent`.

#### R3D-10 · MAJOR · review record · R2D-13's ruling "A" matches the opposite option in the recorded question
- **Claim:** r2-owner L6 "R2D-13, phase 03: A. All three W10M reminder kinds … Jeremy: 'A'".
- **Evidence:** r2-triage L16 records the question as "(a) time reminders only (with W10M's repeat and photo); (b) time, place and person reminders; (c) other / clarify", and r2-opus-design L126 has the same order. The "A" answers to R2D-06 and R2D-12 match option (a) as recorded; R2D-13's does not. The ruling brings in location "Allow all the time" plus call-log and SMS reading (phase-03 L64, build task 12), so the mismatch is load-bearing.
- **Fix:** record in r2-owner.md the exact option wording Jeremy answered. If it was the triage wording, re-ask before phase 03 goes FINAL.
- **Owner question (WHAT):** You answered "A" to Cortana reminders. Did you mean (a) all three W10M kinds, time, place ("when I get home") and person ("next time I talk to Mom"), which needs location set to "Allow all the time" and call-log access; or (b) time reminders only, with W10M's repeat and photo; or (c) other / let me clarify?

### MINOR (each cheap)

- **R3-m1 · stale H-row ranges.**
  - phase-03 H3 (L134) says "accept any approximation not covered by H5–H20", but its rows now run to H28.
  - phase-05 H2 (L81) says "not covered by H3–H18" and E3 (L68) says "judged in H3–H18", but its rows now run to H23.
- **R3-m2 · phase-05 L90** edge "switching languages if more than English is ever added" contradicts L41 "no language key and no space-bar language switch" and PLAN L85–86 (no hooks for post-plan work). Delete it.
- **R3-m3 · status drift.**
  - PLAN L3 still reads "3 owner questions pending" (INDEX L17 says they were ruled and applied) and "R7 … applied to phases 04, 06, 09" (phase 03 got its R7 values on 09-17).
  - INDEX L29's R7 "Gates" column reads "FINAL of phases 04, 06, 09", but PLAN L246 and INDEX L18 include 03.
- **R3-m4 · phase 04 toggle commands while locked are unclassified.** PQ3's locked set (phase-03 L45, L52) covers only phase 03's commands. Phase 04 adds Wi-Fi / Bluetooth / data / airplane commands (L25, build task 7) with no locked rule, and airplane mode from a locked phone matters if the phone is lost. Add this to phase 04's interview queue.
  - **Owner question (WHAT), for the phase 04 interview:** While the phone is locked, should Cortana (a) switch Wi-Fi, Bluetooth, mobile data and airplane mode directly; (b) show "Unlock to continue" for them; or (c) other / let me clarify?
- **R3-m5 · phase 06 Settings pages are rendered but untagged.** The Phone "…" menu "Settings" (L37), "List → Messaging settings: a one-frame cut" (L47) and build task 6's "Settings page (Text reply messages and on / off)" have no line saying what either page holds or how it looks. R7 §2.7.1 (14393, MEDIUM) gives Messaging's page structure (History & sync / Sign in, Related Settings, About this app), mostly out under A11. Add one approximation line (phase-03 L73's Settings-page look) listing each page's contents, with its own H-row.
- **R3-m6 · person-reminder trigger direction and permissions.**
  - phase-03 L64 says "fire when Jeremy calls or texts that contact", but E14 L129 fires on an incoming `adb emu sms send <contact number> hi`, which is the contact texting Jeremy. Pick a rule (either direction counts, or outgoing only) and make E14 match it.
  - Reading the call log and the SMS provider needs READ_CALL_LOG and READ_SMS. These are hard-restricted permissions that a non-role app gets only when the installer allow-lists them (`adb install` does unless `--restrict-permissions` is passed). Record that dependency. Build task 12 lists a call-log checklist row but no SMS-read row.
- **R3-m7 · phase-07 Goal vs the no-lock case.** The Goal (L13) says "it never shows over an unlocked phone". L27 says "With no secure lock set, glance starts on the screen-off event", and E1 / E7 (L48, L54) run with "no lock set". With lock type None there is no keyguard, so edge L71's "wake to the keyguard" has nothing to wake to. Reword the Goal to cover secure locks only, and state what a touch on glance does when there is no keyguard.
