---
phase: 19
slug: settings-front
status: DRAFT   # split 2026-09-22; interview DONE 2026-09-23; review triage round 1 applied 2026-09-23 (review/2026-09-23-phases11-19-triage.md); round 2 applied 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md); lands AFTER phase 04 (its helper toggles); r11/settings-front.md landed 2026-09-23 and is applied (T19-11; E15 written); DRAFT → FINAL after Stage A step 7 (the per-page table's build-start checks — Driving mode, Mouse, Navigation bar — are recorded when built)
depends-on: [01, 04, 11, 12]   # 11 for the E17 burst, 12 for the E16 wizard template and C-15 (C-23); the front lists what exists at build time (T19-6): phase 18's Files under Storage (and its Recycle Bin size), phase 12's presets (Themes) and phase 13's Transparency effects (Colors) when built, each by the ADD rule phase 01 already has
---

# Phase 19 — the W10M Settings front

## Goal
The shell's Settings app looks and reads like Windows 10 Mobile's Settings on the final release: the W10M
top level (W10M final's categories, Apps included, Q2 C — in 15063's own order, r11/settings-front.md 1.12: System, Devices,
Network & wireless, Personalization, Apps, Accounts, Time & language, Ease of Access, Privacy, Update & security, Extras;
Apps is W10M 15063's own category, not a desktop addition — T19-11), a "Find a setting" box, and a category page per entry
listing W10M's pages. Every page is one
of three kinds and says which: **shell-owned** (the shell changes the setting itself), **helper** (phase 04's privileged
helper flips it — its four ruled verbs plus the five this phase ADDs, Q3 C), or **deep-link** (the page opens Android's
own settings page; where nothing resolves, the row says "Change this in Android settings" and opens Android's Settings
home). Phase 01's Start settings hub pages are re-homed under it, so there is ONE shell Settings app, not two (Rule 16).
It is partial by nature: Samsung's own settings pages cannot be replaced (PLAN "Android limits").

## Scope
**In:**
- The W10M Settings home (r11/settings-front.md §2, measured at 1080p on 14393 and 15063, which replaces R3 C1's 720p
  values: header gear 24-epx box and title "Settings" at x 49 ± 1, "Find a setting" box 32.7 epx tall (outer 80.3 → 113.0)
  with ≈12-epx margins drawn in its focused-at-rest style — white fill, 2-epx accent border, no keyboard (2.5; T19-11 agent
  call), two-line category rows at a 64.2-epx pitch with a 31-epx accent glyph at x 12.5–13.3 and the title at x 56–57.3,
  toggles 44 × 20 epx (4.5)), the category pages (the home header and box kept, a grey section title, 48-epx one-line rows,
  §3), the leaf pages (a 72.6-epx band over black content with the 15063 sidebar, §4), page motion as measured (§6), and every
  page in the table below with its stated mechanism.
- Ownership: `SettingsActivity` keeps its component (every phase 01–05 driver, the voice-interaction and
  recognition services' `settingsActivity`, and the `shell:settings` tile point at it); its home page becomes
  the W10M top level; the hub's pages keep their code and test tags (`settings_start_theme`,
  `settings_tile_apps`, `settings_live_tile_access`, `settings_keyboard`, `settings_checklist`,
  `settings_diagnostics`, `settings_about`) and move under their categories; the launcher label is "Settings" and
  Android's Settings is relabelled "Android settings" in the shell's app list (Q1 A; Decisions).
- Shell-owned pages that need new grants: Display (brightness, auto-brightness, rotation lock, screen
  timeout via `WRITE_SETTINGS`, granted through `android.settings.action.MANAGE_WRITE_SETTINGS`), Sounds
  (ringtone and notification sound via `RingtoneManager`, the same grant), Lock screen background
  (`WallpaperManager` FLAG_LOCK, `SET_WALLPAPER` — the install-time permission phase 17 ADDs for Photos' "set as"; this
  phase reuses it, no second ADD, T17-18), Date & time's 24-hour toggle (`Settings.System.TIME_12_24`),
  Do not disturb (notification-policy access, `NOTIFICATION_POLICY_ACCESS_SETTINGS`), plus reads: battery,
  storage (`StorageStatsManager`; usage access is held), data usage (`NetworkStatsManager`; the same grant); a read with
  usage access revoked says so on the page and in diagnostics, never a silent zero (T19-12). The Storage page's Recycle Bin
  row (the bin's size per volume with Empty) is BUILT HERE over phase 18's published bin index reader and its
  `empty(volume)` call; phase 18 does not ADD it (T18-9).
- Helper rows: Wi-Fi, Bluetooth, mobile data and airplane mode drawn with phase 04's own toggle-state UI
  (its Y4 / H7 "helper not running" form) — an ADD of a second surface over 04's verbs, not a second
  mechanism; and the five verbs Q3 C adds — battery saver, location, NFC, hotspot, automatic time — BUILT BY THIS PHASE as
  ADDs to phase 04's helper (one named allow-list verb per toggle, each R4-proven on the phone and adversarially reviewed;
  Decisions, T19-1). ~~further verbs only per Q3~~ SUPERSEDED 2026-09-23 by Q3 C (T19-10).
- Deep-link rows: a table-driven list; each resolves at open time (`PackageManager.resolveActivity`) and
  falls back to "Change this in Android settings" + Android's Settings home (Q4 A); the per-page table below records the
  AOSP activity each action resolved to on the AVD (2026-09-22; the Apps rows 2026-09-23) and marks One UI as "verify on
  the phone". Where the fallback line sits (T19-11): a category row that is ITSELF the link (its tap goes straight to
  Android's page) keeps Q4 A's ruled form — the row takes a second line, "Change this in Android settings", under its label
  (a two-line exception to the 48-epx one-line category row, H3); a link INSIDE a shell-drawn leaf page (Display's "more",
  Lock screen's sign-in, Date & time's date / zone page) sits in that page's 15063 sidebar under "Related settings"
  (r11/settings-front.md 4.11, a measured W10M slot), and when unresolved that sidebar entry reads "Change this in Android
  settings" and opens Android's Settings home.
- The Apps category (Q2 C; W10M 15063's own category, r11/settings-front.md 1.5, HIGH): Apps & features (with Uninstall
  through the app list's path) and Apps for websites are W10M's own pages [fidelity]; Default apps and App permissions are
  the P4 additions Q2 C allows (table; T19-2, T19-11). W10M's third Apps page, Offline maps, is Out (below).
- Phase 04's "All settings" quick action opens this front (coordination with phase 04, DRAFT: its E4(d) taps
  "All settings" as an app launch); "Find a setting" searches this front's page titles.
- The per-package label override that shows Android's Settings as "Android settings" in phase 01's app list (an ADD to
  phase 01's part; Decisions, T19-3); the Settings tile's static App Shortcuts kept as phase 11 Q1 ruled them, re-pointed at
  the re-homed pages (T11-10, C-8).
- Setup checklist rows and setup-wizard steps: "Modify system settings" (`WRITE_SETTINGS`), "Do not disturb access"
  (phase 12 Q1 / Q2; C-4); diagnostics lines; the app-list regression; the exported allow-list (no new component); the APK
  budget.
**Out (explicitly):** replacing Samsung's settings pages, the status bar, the permission dialogs or the
lock-screen security (CANNOT, PLAN "Android limits"); flipping any toggle the platform reserves for system apps
without phase 04's helper (Wi-Fi, Bluetooth, data, airplane; location mode; NFC; hotspot) — no
"open a panel instead" placeholder for the four ruled toggles (phase 03 Scope's Rule 16 wording); a W10M page
with no Android counterpart (Kid's corner, Apps corner, Work access, Sync your settings, Windows Insider
Program, Find my phone, Backup — PLAN: no backup); Apps > Offline maps (the shell builds no Maps, R10-Q4; T19-11); Driving
mode, Mouse and Navigation bar only if the build-start check decides they have no Android counterpart the shell can reach
(each recorded in the table with its reason, T19-11); a Microsoft-account sign-in; a generic command verb in the helper (one
named verb per toggle, Q3); any interim front that phase 04 later replaces (Rule 16).
~~an "Apps" category (desktop Windows' 15063 addition, not W10M's)~~ SUPERSEDED 2026-09-23 by Q2 C (T19-2): Apps is in. The
struck description was also wrong: W10M 15063 shipped its own Apps category (r11/settings-front.md 1.5; T19-11).

## Decisions
- 2026-09-23: Interview Q4 — a row whose Android page does not exist on the phone stays (Jeremy: "(a)"): its subtitle reads
  "Change this in Android settings" and a tap opens Android's Settings home; the page keeps W10M's shape and nothing silently
  disappears. The row and its unresolved target are logged in diagnostics.
- 2026-09-23: The App Shortcuts under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Settings — System,
  Personalisation, Network & wireless, Apps.
  **SUPERSEDED 2026-09-23 by T11-10** (agent line at the end of Decisions): the Settings tile keeps phase 11 Q1's
  Jeremy-ruled four (Start + theme / Tile apps / Setup / Diagnostics); H8 lets Jeremy swap them for categories.
- 2026-09-23: Interview Q3 — the helper gains every toggle the shell uid can flip (Jeremy: "(c)"): battery saver, location,
  NFC, hotspot, automatic time. Each new verb is a TRUST change, so each gets: proof on the S25 Ultra by R4's method, a named
  verb in the allow-list (never a generic command), and the adversarial review phase 04 requires for helper changes. A verb R4
  shows the shell uid cannot perform on One UI 8 is not faked: that row stays a deep-link into Android's page and the build
  record says why.
- 2026-09-23: Interview Q2 — W10M final's categories plus "Apps" (Jeremy: "(c)"): every W10M category with an Android
  counterpart (pages with none — Kid's corner, Work access, Windows Insider — omitted), plus an "Apps" category (desktop
  Windows 10's 2017 addition) for uninstall, default apps and per-app permissions, each a deep-link into Android's page for
  that app where the shell cannot act itself.
  - Note 2026-09-23 (r2 triage T19-11): the ruling stands; only its description is corrected. Apps was not a desktop-only
    addition: W10M 15063 shipped its own Apps category, fifth on the home page, with Apps & features, Offline maps and Apps
    for websites (r11/settings-front.md 1.5, 1.12, HIGH; absent on 14393). So Apps & features and Apps for websites are
    W10M's own pages [fidelity], Default apps and App permissions are the P4 additions this ruling allows, and Offline maps is
    Out (no Maps, R10-Q4).
- 2026-09-23: Interview Q1 — ours is "Settings", Android's is relabelled "Android settings" in the shell's app list (Jeremy:
  "(a)"): one "Settings", nothing hidden; the 2026-09-22 slot-label rule extended to this one app entry (the label is the
  shell's own drawing of the list; Android's Settings itself is untouched).
- 2026-09-22: From phase 11 interview Q1 (Jeremy: "A"), a standing rule for every shell app: this phase's apps declare their
  own top-level screens as static App Shortcuts, so a hold on their tiles bursts those screens (phase 11). Which screens each app
  declares is settled at this phase's own interview; a build task and an acceptance row carry it.
- 2026-09-22 Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out
  thing at a glance thing"): "a W10M Settings front that deep-links into Android's own pages where it cannot
  change a setting itself (partial by nature — Android limits, and phase 04's helper for the toggles it can
  flip)" (PLAN.md, 2026-09-22 scope add).
- 2026-09-16 Q13 (Jeremy; reconfirmed 2026-09-22 "keep it"): the privileged helper is the mechanism for
  Wi-Fi / Bluetooth / mobile data / airplane; phase 04 owns it and its R4 gate. This phase therefore lands
  AFTER phase 04 (R10 triage order: "the Settings front after phase 04 (its toggles need 04's helper)") and
  adds no toggle mechanism of its own.
- 2026-09-22 (agent, R10 design 12 / triage): **ownership — the front IS the hub's W10M top level.** PLAN's
  feature list makes the Start settings hub "a permanent hub from phase 1; each phase adds its own page";
  Rule 16 forbids a hub the front later replaces; so the front is the hub's new home page and category pages,
  with the existing pages re-homed and their tags unchanged. Consequences, recorded: (1) the home page's list
  changes — a post-FINAL change to phase 01's part, INDEX Change Log when built; (2) phase 01's E19 (bars on
  "a Settings hub page") and every driver that opens a hub page by tag re-run through the new category route
  (E2); (3) the Start + theme page's rows are split across W10M's Personalization > Start and Personalization
  > Colors without rewriting a row (a re-home, H6); (4) the pages other phases own keep their owner: phase 04's
  quick-actions page, phase 05's Keyboard page, phase 03's Tess settings, and phase 06 / 07's pages when they
  exist — a phase that adds a page ADDs it to its category, the rule the hub already has. Not a hook for
  later phases: the front lists what exists at build time and the ADD rule is phase 01's.
- 2026-09-22 (agent, R10 testability 17): **three classes, three observables.** OWNED: asserted with
  `settings get`, `dumpsys wallpaper`, `dumpsys notification`; HELPER: phase 04's own observables (`dumpsys
  wifi`, `settings get global airplane_mode_on`, …), runnable only after 04's rows pass; DEEP-LINK: `dumpsys
  activity activities` resumed = the table's activity on the AVD, and on the phone every action started with
  `am start -a` and its resumed activity recorded (One UI 8 resolves some actions to Samsung activities and
  some to nothing).
- 2026-09-22 (agent): **every deep-link action was resolved on the AVD today** (`adb shell cmd package
  query-activities --brief -a <action>`, sdk_phone64_x86_64 API 36, 2026-09-22); the table's fourth column is
  that result. Three actions resolve to NOTHING on AOSP (`android.settings.SYSTEM_UPDATE_SETTINGS`,
  `android.settings.USB_SETTINGS`, `android.settings.SHOW_REGULATORY_INFO`), so the fallback path is exercised
  on the emulator, not only on the phone. (The Apps category's actions were resolved the same way on 2026-09-23 with
  `--components`: `MANAGE_ALL_APPLICATIONS_SETTINGS` → `Settings$ManageApplicationsActivity`, `MANAGE_DEFAULT_APPS_SETTINGS` →
  `com.android.permissioncontroller/.role.ui.DefaultAppListActivity`, `APPLICATION_DETAILS_SETTINGS` with `package:` →
  `.applications.InstalledAppDetails`, `APP_OPEN_BY_DEFAULT_SETTINGS` with `package:` →
  `.applications.InstalledAppOpenByDefaultActivity`, `ACTION_DELETE` with `package:` →
  `com.android.packageinstaller/.UninstallerActivity`.)
- 2026-09-22 (agent): **the Settings panels** (`android.settings.panel.action.WIFI`,
  `…INTERNET_CONNECTIVITY`, `…NFC`, `…VOLUME`; all `com.android.settings/.panel.SettingsPanelActivity` on the
  AVD) are half-screen system sheets, closer to staying in the shell than a full Android page; the Wi-Fi
  network list uses the panel for "connect to a network" and the full page for "more Wi-Fi settings". They are
  still Android-drawn (deep-link class).
- 2026-09-22 (agent): **`WRITE_SETTINGS` is an appop, not a runtime permission.** On the AVD `appops get
  app.tileshell WRITE_SETTINGS` = "No operations" (default) today; the row grants it with `appops set
  app.tileshell WRITE_SETTINGS allow`, the phone through Android's "Modify system settings" page
  (`MANAGE_WRITE_SETTINGS` → `Settings$WriteSettingsActivity` on the AVD). Do-not-disturb access is
  `cmd notification allow_dnd app.tileshell` on the AVD. Both are checklist rows; a denied grant disables the
  page's controls and says why (no silent no-op — INDEX 2026-09-22 UNINSTALL's lesson).
- 2026-09-22 (agent): **RV10 holds** — the front sets `Density(pxPerEpx, fontScale = 1)` like every shell
  window; Android's font size and Screen zoom change nothing here (E-row with `font_scale`); the Ease of
  Access > text-size row therefore deep-links (Android's own UI follows it, the shell's does not).
- 2026-09-22 (agent): **R11 gates FINAL.** ~~The home and category pages take R3 C1's measured values (header,
  search box, row pitch, glyph, text x, toggle); what R3 C1 did not capture — category glyphs, the category
  page header, the fallback line, per-page layouts — is "from R11 §Settings" (pending — R11 is now an index,
  docs/plan/r11-inbox-apps.md; this phase's section is `docs/plan/r11/settings-front.md`, not written as of 2026-09-23 —
  C-12) or a Y row with an
  H row (RV9 / Q10).~~ SUPERSEDED 2026-09-23 by T19-11: r11/settings-front.md landed (29 HIGH / 20 MEDIUM / 4 LOW / 8
  UNMEASURED, measured at 1080p on 14393 and 15063 at 60 fps) and its values replace R3 C1's 720p readings everywhere in this
  doc (Scope, the per-page table, Y1 / Y2 / Y4, E1, E15); what it leaves UNMEASURED (U1–U8) stays a Y row with an H row
  (RV9 / Q10). The two kinds of NEEDS-HUMAN row are labelled (*fidelity* / *accept*), and
  qa/phase-19/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.
- 2026-09-22 (agent): **harness contracts.** `SettingsActivity` already signs `testTagsAsResourceId`; each
  category row and page row carries its tag (`settings_cat:<name>`, `settings_page:<name>`), the fallback
  line its own (`settings_fallback:<action>`), and every deep-link and fallback writes a diagnostics line
  (E12). Drivers symlink qa/phase-03/scripts/lib.sh; evidence under qa/phase-19/.
- 2026-09-22 (agent): **APK budget** — no new dependency; E13 checks the size against phase 03's ≤ 600 MB.
- 2026-09-23 (agent, review triage T19-1): **the five new helper verbs are built BY THIS PHASE, as ADDs to phase 04's
  helper.** One named allow-list verb per toggle, never a generic command: `battery_saver on|off` (candidate mechanisms for
  R4: `settings put global low_power 1|0`, `cmd power set-mode 1|0`), `location on|off` (`cmd location set-location-enabled
  true|false`), `nfc on|off` (`svc nfc enable|disable`), `hotspot on|off` (a TETHERING path the probe must find —
  `cmd wifi start-softap` is a local-only AP, not the phone's hotspot; the shell uid holds `TETHER_PRIVILEGED`, so
  `TetheringManager.startTethering(TETHERING_WIFI)` from the helper is the first candidate; if no path works, hotspot stays a
  deep-link), `auto_time on|off` (`settings put global auto_time 1|0`). Each is recorded in phase 04's Change Log when built;
  the adversarial review phase 04 mandates for helper changes is a GATE in build task 4 (recorded under qa/phase-19/ before
  `done`), not a sentence. INDEX's R4 row gains the five probes, each run as the shell uid on One UI 8 (the lead records them
  there; they are listed here so this phase's gate is visible): battery saver, location, NFC, hotspot (tethering, not
  local-only), auto-time. A verb R4 could not prove stays a deep-link with its reason (Q3's own rule) and its E9 sub-row
  asserts that form. This is not a toggle mechanism of this phase's own (the 2026-09-16 Q13 line): the verbs ride phase 04's
  helper, its handoff check and its allow-list; if phase 04's interview (pending R4) reshapes the helper, these ride with it.
  Reason: this is the phase that needs them, and phase 04 is DRAFT with a four-verb set that does not know about them.
- 2026-09-23 (agent, review triage T11-10): **the Settings tile keeps phase 11 Q1's four satellites** — Start + theme / Tile
  apps / Setup / Diagnostics — and the 2026-09-23 agent line above ("System, Personalisation, Network & wireless, Apps") is
  withdrawn. Reason: the four are a Jeremy ruling and the pages people open; a satellite that lands on a category is one tap
  short of them. The shortcuts' page extras (`SettingsActivity`'s `EXTRA_PAGE`, `settings/SettingsActivity.kt:58,100,117`)
  keep opening the re-homed pages, with Back returning to the page's category (E2, E16). H8 [accept] lets Jeremy swap them for
  categories on the phone.
- 2026-09-23 (review triage T19-3, a doc update): **the rule Q1 cites is findable** — INDEX Change Log 2026-09-22 "JEREMY'S
  FOUR ASKS", item (3) ("Contacts should be called people": a slot IS the W10M tile, the resolved app is not its name; fixed
  for slots only). It labels slot TILES, so relabelling Android's Settings in the APP LIST is a different mechanism: a
  per-package label override in phase 01's app list (an ADD to phase 01's part, INDEX Change Log when built) — the row for
  `com.android.settings` reads "Android settings", files under A in the letter groups and the jump grid, and search
  "settings" returns both entries, ours first. The partial reject: the ruling's own words are left as written.
- 2026-09-23 (review triage T19-2 / T19-5, a doc update): **Apps, Themes and Colors.** The Apps category holds Apps &
  features, Default apps (MOVED here from System > Phone / Messaging), Apps for websites (MOVED here from System) and App
  permissions; Personalization > Themes is phase 12's preset page; phase 13's Transparency effects switch sits under
  Personalization > Colors (Windows 10's own placement). H6 lists both; ~~H9 covers a category with no W10M original~~
  SUPERSEDED 2026-09-23 by T19-11: Apps is W10M 15063's own category, so H9 covers only its two P4 pages (Default apps, App
  permissions).
- 2026-09-23 (review triage C-4, a doc update): **this phase's two grants join the setup wizard.** "Modify system settings"
  (`WRITE_SETTINGS`, Settings-page step, action `MANAGE_WRITE_SETTINGS` with `package:app.tileshell`, why line "Settings can
  change brightness, sounds and the screen timeout for you. Without it those pages only show them.") and "Do not disturb
  access" (Settings-page step, action `NOTIFICATION_POLICY_ACCESS_SETTINGS`, why line "Settings can turn Do not disturb on
  and off. Without it the switch can't change anything.") are wizard steps `wizard_step:setup:write_settings` and
  `wizard_step:setup:dnd_access`; `qa/phase-03/scripts/provision.sh` gains `adb shell appops set app.tileshell WRITE_SETTINGS
  allow` and `adb shell cmd notification allow_dnd app.tileshell`; E16 is phase 12 E14's template for both; phase 12 E1
  re-runs on this build. Phase 12's rule, restated: a finished or skipped wizard is never re-summoned on that install — these
  grants go red on the Setup checklist instead.
- 2026-09-23 (agent, r2 triage T19-11): R11 settings-front applied. **Apps is W10M's own category** (15063, r11/settings-front.md
  1.5 / 1.12 HIGH), fifth on the home page (System · Devices · Network & wireless · Personalization · Apps · Accounts · Time &
  language · Ease of Access · Privacy · Update & security · Extras); Apps & features and Apps for websites are [fidelity],
  Default apps and App permissions the P4 additions Q2 C allows (Y6 / H9 narrow to those two), Offline maps Out (no Maps,
  R10-Q4). The per-page table follows R11 §1.1–1.10; every as-shipped page with an Android counterpart has its row and the
  rest are Out with their reason; Driving mode, Mouse and Navigation bar are decided at build start (row or Out, recorded in
  the table). Page forms: category pages keep the home header and box, add a grey section title and list 48-epx one-line rows
  (label x 44.5); leaf pages are a 72.6-epx band over black; the 15063 leaf sidebar hosts an in-page link's fallback line,
  while a category row that is itself the link keeps Q4 A's two-line subtitle form. Motion as measured (§6): the old page cuts,
  the new page slides up ≈10 epx and fades in (E15). Reason: R11 measured the governing build on two builds and two scales;
  fidelity (A4) beats the R3 C1 720p stand-ins and the "desktop addition" description, and Jeremy's Q2 ruling needs no change.
- 2026-09-23 (agent, r2 triage T19-11): **the home "Find a setting" box is drawn in its measured focused-at-rest style** (white
  fill, 2-epx accent border, r11/settings-front.md 2.5) **without taking IME focus** (no keyboard until the user taps it); H10
  [accept] judges it. Reason: that is how 14393 and 15063 drew the box at rest on both captures, and focusing it for real would
  raise the keyboard on every visit.
- 2026-09-23 (agent, r2 triage C-17): this doc cites the drawn status bar as phase 01's `BarMetrics.STATUS_EPX`
  (`app/src/main/kotlin/app/tileshell/bars/SystemBars.kt:77-80`, 28 today from R3 C4), never a literal; R11 measured 24 epx in
  Settings (r11/settings-front.md 2.2, 4.1) and the R3 C4 re-check at phase 01 (INDEX research table) decides the value.
  Reason: one re-measure settles 28 vs 24 and no row here should need an edit when it does.

### Per-page table
Mechanism: **owned** = the shell changes it; **helper** = phase 04's helper (R4-gated); **helper (04 ADD)** = a verb this
phase ADDs to that helper (T19-1; R4-gated, reviewed; a verb R4 cannot prove leaves the row as its link); **link** = opens
Android's page; **panel** = a system Settings panel; **read** = the shell shows a value it reads. "AOSP
(AVD)" = the activity `cmd package query-activities` resolved on 2026-09-22 (Apps rows 2026-09-23); **NONE** = no handler,
the fallback line applies (Scope: a category row that is itself the link takes the two-line fallback form; an in-page link
shows it in the leaf page's sidebar). "One UI" = verify on the S25U (P1) — Samsung substitutes its own activities. "W10M" =
the page's status against r11/settings-front.md §1 (T19-11): **15063** = a page W10M's final release shipped there
[fidelity for its form]; **P4** = a shell addition W10M did not have there (H2, or H9 for Apps); **build start** = an
as-shipped page whose Android counterpart is decided at build start, then recorded here as a row or moved to Scope Out with
its reason. Categories and pages run in 15063's order (R11 1.1–1.12).

| Category > page | What it shows | Mechanism | AOSP (AVD) 2026-09-22 | One UI | W10M |
|---|---|---|---|---|---|
| System > Display | brightness slider, auto-brightness, rotation lock, screen timeout; Android's Display page as a "Related settings" sidebar link (W10M's Display had no "more", R11 §10) | owned (`WRITE_SETTINGS`: `screen_brightness`, `screen_brightness_mode`, `accelerometer_rotation`, `screen_off_timeout`) + link `DISPLAY_SETTINGS` (sidebar) | `com.android.settings/.Settings$DisplaySettingsActivity` | verify | 15063 (the sidebar link P4) |
| System > Notifications & actions | phase 04's quick-actions page (its part); per-app notifications; Do not disturb | owned (DND via notification-policy access) + link `ALL_APPS_NOTIFICATION_SETTINGS`; access grant `NOTIFICATION_POLICY_ACCESS_SETTINGS` | `Settings$NotificationAppListActivity`; `Settings$ZenAccessSettingsActivity` | verify | 15063 |
| System > Phone / Messaging | phase 06's pages when built (its ADD) ~~; default apps~~ (MOVED 2026-09-23 to Apps > Default apps, T19-2) | owned (06) ~~link `MANAGE_DEFAULT_APPS_SETTINGS`~~ | — | — | 15063 (two pages) |
| System > Storage | used / free per volume; the Recycle Bin's size per volume with Empty (BUILT HERE over phase 18's bin index reader and `empty(volume)`, T18-9); "Files" opens phase 18; manage | read (`StorageStatsManager`; phase 18's bin index) + owned (Empty, through phase 18's `empty(volume)`) + link `INTERNAL_STORAGE_SETTINGS` | `Settings$StorageDashboardActivity` | verify | 15063 (the bin row P4, phase 18 H2) |
| System > Battery (~~Battery saver~~ renamed 2026-09-23 by T19-11: 15063's page is "Battery") | level and charging state; battery use; battery saver toggle | read (`BatteryManager`) + helper (04 ADD, `battery_saver`) + link `POWER_USAGE_SUMMARY`, `BATTERY_SAVER_SETTINGS` ~~(helper only if Q3 adds it)~~ | `Settings$PowerUsageSummaryActivity`; `Settings$BatterySaverSettingsActivity` | verify | 15063 |
| System > Driving mode | W10M's driving rules (silence calls / texts while driving) | **build start** (T19-11): a row (a link, or DND's own rule through the owned DND control) if an Android counterpart the shell can reach resolves on the AVD and One UI, else Scope Out with its reason; the result and its `query-activities` output recorded here | to record | verify | build start |
| ~~System > Apps for websites~~ | MOVED 2026-09-23 to Apps > Apps for websites (T19-2; 14393 had it here, 15063 moved it to Apps — R11 1.1) | — | — | — | — |
| System > About | the shell's About page (owned, exists); device name, Android version | owned + link `DEVICE_INFO_SETTINGS` | `Settings$MyDeviceInfoActivity` | verify | 15063 |
| Devices > Default camera | the Camera slot picker (owned, exists: `Route.Picker(Slot.CAMERA)`) | owned | — | — | 15063 |
| Devices > Bluetooth & other devices (~~Bluetooth~~ renamed 2026-09-23 by T19-11, 15063's label) | toggle; paired devices; pair new | helper (04) + link `BLUETOOTH_SETTINGS`, `BLUETOOTH_PAIRING_SETTINGS` | `Settings$ConnectedDeviceDashboardActivity`; `Settings$BlueToothPairingActivity` | verify | 15063 |
| Devices > NFC | toggle; Android's page | helper (04 ADD, `nfc`) + read + panel `panel.action.NFC` / link `NFC_SETTINGS` ~~(helper only per Q3)~~ | `.panel.SettingsPanelActivity`; `Settings$NfcSettingsActivity` (no NFC hardware on the AVD: `dumpsys nfc` = "Can't find service: nfc", 2026-09-23) | verify | 15063 |
| Devices > Mouse | pointer settings for a connected mouse | **build start** (T19-11): a link if an Android pointer / mouse page resolves (AVD and One UI), else Scope Out with its reason; recorded here | to record | verify | build start |
| Devices > USB | USB preferences | link — `USB_SETTINGS` | **NONE** → fallback | verify | 15063 |
| Devices > Printers | print services | link `ACTION_PRINT_SETTINGS` | `Settings$PrintSettingsActivity` | verify | P4 (not a 15063 phone page; H2) |
| Network & wireless > Data usage | this cycle's totals per app; with usage access revoked, a line naming the Setup row (T19-12) | read (`NetworkStatsManager`) + link `DATA_USAGE_SETTINGS` | `Settings$DataUsageSummaryActivity` | verify | 15063 |
| Network & wireless > Cellular & SIM | mobile data toggle; network page | helper (04) + link `DATA_ROAMING_SETTINGS` | `Settings$MobileNetworkActivity` | verify | 15063 |
| Network & wireless > Wi-Fi | toggle; connect to a network; more | helper (04) + panel `panel.action.WIFI` + link `WIFI_SETTINGS` | `.panel.SettingsPanelActivity`; `Settings$WifiSettingsActivity` | verify | 15063 |
| Network & wireless > Airplane mode | toggle | helper (04) + link `AIRPLANE_MODE_SETTINGS` | `Settings$NetworkDashboardActivity` | verify | 15063 |
| Network & wireless > Mobile hotspot | toggle (if R4 finds a tethering path); Android's page | helper (04 ADD, `hotspot`) + link `TETHER_SETTINGS` ~~(helper only per Q3)~~ | `Settings$TetherSettingsActivity` | verify | 15063 |
| Network & wireless > VPN | Android's page | link `VPN_SETTINGS` | `Settings$VpnSettingsActivity` | verify | 15063 |
| Personalization > Start | background, show more tiles, transparency, press effect, bottom row, Tile apps, Live tile access (phase 01's rows, re-homed) | owned | — | — | 15063 |
| Personalization > Colors | accent, light / dark (phase 01's rows, re-homed); Transparency effects (phase 13's switch, T19-5) | owned | — | — | 15063 |
| Personalization > Themes | phase 12's preset page (T19-5; when built) | owned (12) | — | — | P4 (H6) |
| Personalization > Sounds | ringtone, notification sound; key sounds (phase 05's row) | owned (`RingtoneManager.setActualDefaultRingtoneUri`, `WRITE_SETTINGS`) + link `SOUND_SETTINGS` | `Settings$SoundSettingsActivity` | verify | 15063 |
| Personalization > Lock screen | background picture; screen times out after; notifications on lock screen; sign-in (sidebar link) | owned (`WallpaperManager` FLAG_LOCK with phase 17's `SET_WALLPAPER`, T17-18; `screen_off_timeout`) + link `LOCK_SCREEN_SETTINGS`, `SECURITY_SETTINGS`, `SET_NEW_PASSWORD` | `Settings$LockScreenSettingsActivity`; `Settings$SecurityDashboardActivity`; `.password.SetNewPasswordActivity` | verify | 15063 |
| Personalization > Glance screen | phase 07's page when built (its ADD) | owned (07) | — | — | 15063 |
| Personalization > Navigation bar | W10M's nav-bar options (colour, hide button) | **build start** (T19-11): phase 01 draws the shell's own nav bar and has no setting page for it yet (R11 §10: "a phase 01 / 04 concern"); a row only if phase 01 / 04 own such a setting by then, or a link if Android's navigation-mode page resolves, else Scope Out with its reason; recorded here | to record | verify | build start |
| Apps > Apps & features | installed apps (the app list's data) in 15063's app-row form (40-epx icon, name + publisher, size right-aligned, 60-epx pitch — R11 4.13); per app: its Android page; Uninstall | owned (list; Uninstall through the app list's `REQUEST_DELETE_PACKAGES` path → the installer's dialog, the self package excluded by `AppUninstall.canUninstall`) + link `MANAGE_ALL_APPLICATIONS_SETTINGS`; per app `APPLICATION_DETAILS_SETTINGS` with `package:` | `Settings$ManageApplicationsActivity`; `.applications.InstalledAppDetails`; uninstall `com.android.packageinstaller/.UninstallerActivity` (2026-09-23) | verify | 15063 |
| Apps > Apps for websites | apps' default links (MOVED here from System) | link `MANAGE_ALL_APPLICATIONS_SETTINGS` (per-app: `APP_OPEN_BY_DEFAULT_SETTINGS` with `package:`) | `Settings$ManageApplicationsActivity`; `.applications.InstalledAppOpenByDefaultActivity` | verify | 15063 |
| Apps > Default apps | default apps (MOVED here from System > Phone / Messaging) | link `MANAGE_DEFAULT_APPS_SETTINGS` | `com.android.permissioncontroller/.role.ui.DefaultAppListActivity` | verify | P4 (Q2 C; H9) |
| Apps > App permissions | per app, its permissions (the Privacy pages' per-app link) | link `APPLICATION_DETAILS_SETTINGS` with `package:` | `.applications.InstalledAppDetails` | verify | P4 (Q2 C; H9) |
| Accounts > Your email and accounts | accounts; add account | link `SYNC_SETTINGS`, `ADD_ACCOUNT_SETTINGS` | `Settings$AccountDashboardActivity`; `.accounts.AddAccountSettings` | verify | 15063 (list LOW, R11 1.6) |
| Accounts > Sign-in options | PIN / fingerprint / face | link `SECURITY_SETTINGS`, `BIOMETRIC_ENROLL` | `Settings$SecurityDashboardActivity`; `.biometrics.BiometricEnrollActivity` | verify | 15063 (list LOW) |
| Time & language > Date & time | 24-hour clock; set time automatically; date, time and zone (sidebar link) | owned (`TIME_12_24`) + helper (04 ADD, `auto_time`) + link `DATE_SETTINGS` | `Settings$DateTimeSettingsActivity` | verify | 15063 (list LOW, R11 1.7) |
| Time & language > Language / Region | Android's pages (two pages on the phone, R11 1.7) | link `LOCALE_SETTINGS`, `REGIONAL_PREFERENCES_SETTINGS` | `Settings$LocalePickerActivity`; `Settings$RegionalPreferencesActivity` | verify | 15063 (list LOW) |
| Time & language > Keyboard | phase 05's Keyboard page (owned, exists); manage keyboards | owned + link `INPUT_METHOD_SETTINGS` | `Settings$AvailableVirtualKeyboardActivity` | verify | 15063 (list LOW) |
| Time & language > Speech | Tess's settings page (phase 03, owned, exists: voice, Notes slot, lock-screen options); assistant app | owned + link `VOICE_INPUT_SETTINGS` | `Settings$ManageAssistActivity` | verify | 15063 (list LOW) |
| Ease of Access > Narrator / Magnifier / More options | Android's accessibility pages | link `ACCESSIBILITY_SETTINGS` | `Settings$AccessibilitySettingsActivity` | verify | 15063 (list LOW, R11 1.8) |
| Ease of Access > High contrast | Android's page | link `ACCESSIBILITY_COLOR_CONTRAST_SETTINGS` | `Settings$ColorContrastActivity` | verify | 15063 (list LOW) |
| Ease of Access > Closed captions | Android's page | link `CAPTIONING_SETTINGS` | `Settings$CaptioningSettingsActivity` | verify | 15063 (list LOW) |
| Ease of Access > Text size | Android's page (RV10: the shell does not follow it); W10M held it under More options | link `TEXT_READING_SETTINGS` | `Settings$TextReadingSettingsActivity` | verify | 15063 (inside More options) |
| Privacy > Location | toggle; Android's page | helper (04 ADD, `location`) + read (`LocationManager.isLocationEnabled`) + link `LOCATION_SOURCE_SETTINGS` ~~(helper only per Q3)~~ | `Settings$LocationSettingsActivity` | verify | 15063 |
| Privacy > Camera / Microphone / Contacts / Calendar / … | per-permission pages | link `PRIVACY_SETTINGS`; per app `APPLICATION_DETAILS_SETTINGS` with `package:` | `Settings$PrivacyDashboardActivity`; `.applications.InstalledAppDetails` | verify | 15063 (tail order MEDIUM, R11 1.9) |
| Privacy > Notifications | apps with notification access | link `ACTION_NOTIFICATION_LISTENER_SETTINGS` | `Settings$NotificationAccessSettingsActivity` | verify | 15063 |
| Update & security > Windows Update (~~Phone update~~ renamed 2026-09-23 by T19-11: the final release's 15254 name, R11 1.10; a Microsoft name carried by the A10 branding module) | Android's update page | link `SYSTEM_UPDATE_SETTINGS` | **NONE** → fallback | verify (One UI has Software update) | 15063 / 15254 |
| Update & security > Device encryption | whether the phone's storage is encrypted; Android's security page | read (`DevicePolicyManager.getStorageEncryptionStatus`) + link (the action resolved at build start with `query-activities`, recorded here; `SECURITY_SETTINGS` if nothing narrower resolves) | to record (`SECURITY_SETTINGS` → `Settings$SecurityDashboardActivity`, as Accounts > Sign-in options) | verify | 15063 (T19-11) |
| Update & security > For developers | Developer options (phase 04's Wireless debugging row needs it) | link `APPLICATION_DEVELOPMENT_SETTINGS` | `Settings$DevelopmentSettingsActivity` | verify | 15063 |
| Extras > Setup | the Setup checklist (owned, exists) | owned | — | — | P4 placement (Extras held OEM settings apps, R11 1.11) |
| Extras > Diagnostics | the Diagnostics page (owned, exists) | owned | — | — | P4 placement (as Setup) |
| Extras > Regulatory | Android's page | link `SHOW_REGULATORY_INFO` | **NONE** → fallback | verify | P4 (not a 15063 Extras page; H2) |

W10M pages with no row above are OUT (Scope), each with its reason: Backup, Find My Phone, Windows Insider Program, Kid's
Corner, Apps Corner, Access work or school, Sync your settings, Apps > Offline maps (no Maps, R10-Q4). ~~Where R11 §Settings
shows a page this table lacks, or a different category placement, the table follows R11 and the change is recorded here.~~
SUPERSEDED 2026-09-23 by T19-11: R11 applied — the table is in 15063's category and page order (1.1–1.12); Battery,
Bluetooth & other devices and Windows Update carry 15063 / 15254 names; Device encryption added; Mouse, Driving mode and
Navigation bar carry build-start rows; Printers, Regulatory, Themes and the Extras placements are marked P4.

### Approximations (each has an H-row; r11/settings-front.md applied 2026-09-23, T19-11)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Category glyphs and the category page header | ~~r11/settings-front.md pending (R3 C1 has the home page only)~~ CLOSED 2026-09-23 by T19-11: measured (R11 §3, HIGH) — the category page keeps the home header and box, adds the category name as a grey (142,145,142) section title at x 13.3, cap top 152, and lists 48-epx one-line rows with a 16–20-epx white glyph at x 13.3 and the label at x 44.5; category glyphs are the MDL2 names of R11 §7 drawn with the Fluent System Icons stand-in (glyph shapes at 1080p only, U8) | ~~R3 C1's row form with Fluent-icon glyphs; page header as the hub's `PageHeader`~~ | H1 (fidelity); H4 for U8's glyph shapes |
| Y2 | The fallback line ("Change this in Android settings") and its form | P4 wording (Q4 A) in two measured slots (T19-11): a category row that is itself the link takes a second line under its label (Q4 A's ruled subtitle; a two-line exception to R11 §3's one-line row); an in-page link sits in the leaf page's 15063 sidebar under "Related settings" (R11 4.11, HIGH) and reads the line when unresolved | ~~a two-line row whose subtitle carries the line, accent-coloured title~~ | H3 |
| Y3 | Helper-row state UI (the four ruled toggles and the five ADDs) | phase 04's Y4 / H7 | 04's own form reused | 04's H7 |
| Y4 | Page transitions and toggle motion | ~~UNMEASURED~~ page transitions CLOSED 2026-09-23 by T19-11: measured at 60 fps on two builds (R11 6.2–6.3, HIGH) — the old page cuts out in one frame, the new page slides up from +9.8 epx and fades in (90 % of travel by ≈200 ms, settled 250–300 ms), timed by the `[motion]` clock (C-5) and asserted in E15; back navigation (U1) takes the same entrance; toggle thumb (U2) and combo open (U3) stay UNMEASURED | ~~the hub's existing `PageTransition` (phase 01)~~ replaced by the measured entrance; toggle: a linear 100-ms thumb slide (R11 U2's proposal); combo: R7 §2.2.6's flyout open (U3) | H1 (page motion, fidelity); H5 (toggle / combo) |
| Y5 | Category → page mapping where W10M and Android disagree | agent call | the table above | H2 |
| Y6 | The Apps category's two P4 pages, Default apps and App permissions (~~no W10M original; desktop Windows 10's 2017 addition~~ SUPERSEDED 2026-09-23 by T19-11: Apps is W10M 15063's own category; Apps & features and Apps for websites are [fidelity] under H1) | P4 design | the table's rows in R11 §3's category-row and §4's leaf-page forms | H9 |
| Y7 | The home "Find a setting" box at rest | agent call (T19-11): drawn in its measured focused style (white fill, 2-epx accent border, R11 2.5) without taking IME focus | — | H10 |

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3).

1. ~~Q1 — app-list identity~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q1 — the app-list identity.** Today the hub's launcher label is "Start settings" so it is not confused
   with Android's Settings (phase 01 build-start call 7). With the W10M front that name stops being right.
   A. Ours is "Settings"; Android's Settings stays in the app list relabelled "Android settings" (the
   slot-label rule of 2026-09-22 extended to one app entry). (lean — one "Settings", nothing hidden)
   B. Ours is "Settings"; Android's Settings is hidden from the app list and reached only through the front's
   links and its "All Android settings" row.
   C. Keep "Start settings" and leave Android's entry alone (two entries, two names).
   D. Other / let me clarify.
2. ~~Q2 — category set~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q2 — the category set.**
   A. W10M final's categories exactly, omitting only pages with no Android counterpart (Kid's corner, Work
   access, Windows Insider, …). (lean)
   B. Only the categories holding at least one page the shell can change itself (fewer, but not W10M's list).
   C. W10M's categories plus an "Apps" category for the app list's Uninstall / defaults / permissions (desktop
   Windows 10's 2017 addition, "as if they never stopped developing it").
   D. Other / let me clarify.
3. ~~Q3 — helper verbs~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q3 — helper verbs beyond phase 04's four.** The helper's verb allow-list is a trust surface (phase 04
   Decisions: adversarial review, no generic shell); each verb is R4-proven on the phone.
   A. None: battery saver, location, NFC and hotspot are deep-links; the helper keeps its four verbs. (lean —
   no new trust surface in this phase)
   B. Add battery saver and location (two verbs, each proved by R4's method and reviewed).
   C. Add every toggle the shell uid can flip (battery saver, location, NFC, hotspot, auto-time).
   D. Other / let me clarify.
4. ~~Q4 — unresolved deep-link~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q4 — a deep-link that resolves to nothing on this phone.**
   A. The row stays, its subtitle reads "Change this in Android settings", and a tap opens Android's Settings
   home. (lean — the page keeps W10M's shape; nothing is silently missing)
   B. The row is hidden on that phone.
   C. The row stays, disabled, with the reason.
   D. Other / let me clarify.

## Build tasks
1. **Top level.** The W10M home in r11/settings-front.md §2's values (~~R3 C1 values~~ SUPERSEDED 2026-09-23 by T19-11), the
   box drawn focused at rest without IME focus (T19-11, H10), "Find a setting" (searches this front's page titles; results in
   R11 §5's dropdown under the box, 44.4-epx rows, glyph at x 26, text at x 51.8; no match = the one row "No results for
   <query>" — ~~empty line for no match~~ SUPERSEDED 2026-09-23 by T19-11), the category pages including Apps in 15063's order
   (R11 1.12) in R11 §3's form, the leaf pages in R11 §4's form (72.6-epx band over black, the page's 20-epx heading, the
   15063 sidebar with "Have a question? / Get help", "Make Windows better. / Give us feedback" and "Related settings"),
   `settings_cat:` / `settings_page:` / `settings_sidebar:<page>` tags, the fallback forms (Y2), and the page motion of R11
   6.2–6.3 (the old page cut in one frame, the new page's entrance from +9.8 epx with its fade, logged as `[motion]
   settings_page t0=… first_offset_epx=<n> settle=<ms> frames=<n> maxGapMs=<ms>`), replacing phase 01's `PageTransition` on
   this front.
2. **Re-home.** The hub's pages under their categories with tags unchanged; Start + theme's rows split
   across Personalization > Start and Colors (H6), Transparency effects under Colors and phase 12's preset page as
   Personalization > Themes when those phases are built (T19-5); the launcher label "Settings" (Q1 A); Android's entry
   relabelled "Android settings" through a per-package label override in phase 01's app list, filed under A, search
   returning ours first (an ADD to phase 01's part, T19-3); INDEX Change Log entry for phase 01's part.
3. **Owned pages.** Display, Sounds, Lock screen (reusing phase 17's `SET_WALLPAPER` ADD — no second manifest ADD,
   T17-18), Date & time (24-hour), Do not disturb; the reads (battery, storage, data usage, encryption status, NFC and
   location state), with usage access revoked each usage read logs `[settingsfront] read <data_usage|storage>: denied
   (usage access)` and its page shows one line naming the Setup checklist's usage-access row instead of zeros (T19-12); the
   Storage page's Recycle Bin row — the bin's size per volume with Empty — built HERE over phase 18's published bin index
   reader and `empty(volume)` call (phase 18 no longer ADDs it, T18-9); `WRITE_SETTINGS` and
   DND-access grants with their checklist rows, their setup-wizard steps and why lines (C-4, Decisions) and the
   disabled-with-reason state; re-read on resume and through a `ContentObserver` on `Settings.System` so a change made in
   Android's own page shows at once; `qa/phase-03/scripts/provision.sh` gains `adb shell appops set app.tileshell
   WRITE_SETTINGS allow` and `adb shell cmd notification allow_dnd app.tileshell` (C-4 a).
4. **Helper rows and the five helper ADDs** (T19-1). The four ruled toggles over phase 04's verbs with 04's state UI. The
   five verbs `battery_saver`, `location`, `nfc`, `hotspot`, `auto_time` added to phase 04's helper: one named allow-list
   entry each (no generic command, no content-provider call — phase 04's rule), each mechanism the one R4 proved on One UI
   8 (Decisions), each reporting `unsupported` where the device has no such hardware; their rows in the front with 04's
   state UI; a verb R4 could not prove is NOT built and its row stays the table's link with the reason in diagnostics;
   phase 04's Change Log line when built. GATE: the adversarial review phase 04 mandates for helper changes, recorded under
   qa/phase-19/ before `done`. ~~per Q3, further verbs are phase 04 ADDs (its allow-list, its R4 proof, its review), not this
   phase's~~ SUPERSEDED 2026-09-23 by T19-1 (this phase is the owner).
5. **Deep-link rows.** One table-driven list (action, optional `package:` data, W10M title, subtitle, and whether the link
   is a category row or a sidebar entry); resolve at open, fall back to "Change this in Android settings" + Android's
   Settings home (Q4 A) in the Y2 form for its slot, diagnostics line per tap; the panels for Wi-Fi and NFC; the Apps
   category's links and Uninstall path (T19-2). Build-start checks recorded in the per-page table: Driving mode, Mouse and
   Navigation bar (row or Scope Out, with the `query-activities` output) and Device encryption's action (T19-11).
6. **"All settings"** from phase 04's action center opens this front (04's E4(d) target), coordinated with
   phase 04's build.
7. **Regressions.** Phase 01 E19 on a re-homed page; every phase 01 / 05 driver that opens a hub page by
   tag; the app-list regression (phase 02's regress.sh pattern); the exported allow-list (unchanged); APK size; phase 12
   E1 on this build (C-4 c). Harness (C-28, C-3): `qa/phase-19/baseline_layout.json` = phase 18's
   `qa/phase-18/baseline_layout.json` unchanged (this phase adds no `addedOnce` marker), plus any marker phase 04 adds when
   it is built (C-14's Build order builds 20 and 04 between 18 and 19; phase 20's file only adds an Auxio pin this phase does
   not need); the previous file kept as `qa/phase-19/baseline_layout-pre-19.json`; drivers in `qa/phase-19/scripts/`.
8. **R11 values applied.** ~~Once `r11/settings-front.md` lands, the Y rows it measures are replaced and E15 is written;
   FINAL only then (RV9).~~ SUPERSEDED 2026-09-23 by T19-11: r11/settings-front.md landed and is applied in this doc (Scope,
   the per-page table, Y1 / Y2 / Y4 / Y6 / Y7, E1, E10, E15); E15 is written, so R11 no longer holds the doc from FINAL
   (RV9); the build takes those values and E15 proves them.
9. **App Shortcuts** (phase 11 Q1's standing rule; C-8, T11-10). The static `shortcuts.xml` entries phase 11 declared for
   Settings — Start + theme / Tile apps / Setup / Diagnostics, ranks 0–3, targeting `SettingsActivity` with `EXTRA_PAGE`
   (`START_THEME`, `TILE_APPS`, `CHECKLIST`, `DIAGNOSTICS`) — kept as they are, each now landing on the re-homed page with
   Back going to its category. E17.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 and take their clock
from the shell's `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`), a screenrecord
corroborating under phase 05's frame-spacing rule and never the primary clock (C-5); the `[motion]` line also carries
`frames=<n> maxGapMs=<ms>`, and every motion row asserts `maxGapMs` ≤ 33.4 ms (2 vsync) beside its numbers (C-31); dumps
follow RV13. Every ring assertion reads `ring_since` from a MARK taken immediately before the step's action (after any clock
jump, so the MARK is on the new clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`;
`row_end` saves each ring the row names to `<row>/ring-<name>.txt` (C-20; `lib.sh` `ring_mark` / `ring_since` /
`reply_since`, built by phase 11's build task 7). After any `adb reboot` (boot-completed poll), `dumpsys battery unplug` or
`KEYCODE_SLEEP` step, the driver calls `wake_device` and asserts it printed `Awake` before the next tap (C-25). A recorded
clause uses `lib.sh` `record <name> <value>`, never an assert; a row with only recorded facts ends `<row>: recorded only
(<n> facts)` with exit 0 (C-26; helper built by phase 13's build task 7). Every row that reads Start (E2, E13, E17) starts
from `qa/phase-19/baseline_layout.json` through `layout_restore` (`qa/phase-02/scripts/layout.sh:18`) and asserts zero
`assignSlotOnce … -> assigned` lines in the ring after the restore (C-28, C-3). A row that wipes
the app reads "`pm clear` → `provision.sh` → Home" (C-4 d), and phase 12's rule holds here: a finished or skipped wizard is
never re-summoned on that install — this phase's grants go red on the Setup checklist instead (C-4 e). Harness:
qa/phase-19/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh. Device: the AOSP AVD
tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google). Baseline values recorded on the AVD 2026-09-22:
`screen_brightness` 102, `screen_brightness_mode` 0, `accelerometer_rotation` 1, `screen_off_timeout`
2147483647, `zen_mode` 0, `navigation_mode` 0; and 2026-09-23 for the five helper ADDs: `low_power` 0, `location_mode` 3,
`auto_time` 1, no NFC service. Helper rows (E9) run only after phase 04's own rows pass.

**Emulator:**
- E1 **Structure (r11/settings-front.md §1–§5; re-cut 2026-09-23 by T19-11).** Dump bounds and screencap, px ÷ 3 = epx,
  R11's HIGH tolerance ± 1 epx unless stated; vertical values are measured from the drawn status bar's bottom edge (phase
  01's `BarMetrics.STATUS_EPX`, C-17), since R11's absolute y includes a 24-epx bar. **Home:** exactly eleven category rows
  in 15063's order (`settings_cat:` tags: System, Devices, Network & wireless, Personalization, Apps, Accounts, Time &
  language, Ease of Access, Privacy, Update & security, Extras — R11 1.12); header gear 24-epx box, title "Settings" at x
  49 ± 1; "Find a setting" box outer 32.7 ± 1 epx tall, its top 56.3 ± 1 below the status bar, side margins 12 ± 1.5 epx
  (R11 2.5: 10.7–12.4); at rest its fill samples (253,255,252) ± 4 with a 2-epx accent border and `adb shell dumpsys
  input_method | grep -c 'mInputShown=true'` = 0 (T19-11: focused style, no keyboard); category row pitch 64.2 ± 1 epx; glyph
  box 31 ± 1 epx at x 12.5–13.3 (± 1); row title x 56–57.3 (± 1). ~~"Find a setting" measures 31 ± 2 epx tall with 12-epx
  side margins; row pitch 64 ± 1 epx; glyph 30 epx at x = 12 epx; text at x = 55 epx; the header block 57 ± 2 epx~~
  SUPERSEDED 2026-09-23 by T19-11 (R3 C1's 720p readings). **Category page** (System, Apps): the home header and box at the
  same bounds; the section title (the category name) in (142,145,142) ± 6 at x 13.3 ± 1, cap top 128 ± 2 below the status
  bar (R11 3.2: 152 − 24, MEDIUM); one-line rows at a 48.0 ± 1 epx pitch, glyph at x 13.3 ± 1, label at x 44.5 ± 1; each
  category page lists exactly the table's pages for it that exist on this build, in the table's order (dump) — Apps lists
  Apps & features, Apps for websites, Default apps, App permissions and no Offline maps; System lists Display, Notifications
  & actions, Storage, Battery, About (+ Phone / Messaging and Driving mode only where built or decided in) and no Apps for
  websites or default-apps link. **Leaf page** (Display): a band of the status bar + 48.6 ± 1 epx over black content ((0,0,0)
  ± 2 sampled 20 epx under the band), the gear and "Display" at x 49 ± 1, the page's own 20-epx heading at x 13.3 ± 1; at the
  page's foot the sidebar nodes `settings_sidebar:display` holding "Have a question?", "Get help", "Make Windows better.",
  "Give us feedback" and "Related settings" with the Display link (R11 4.11). **Find a setting:** `input text wi` → a
  dropdown directly under the box, the box's width ± 2, rows 44.4 ± 2 epx, glyph at x 26 ± 2, text at x 51.8 ± 2 (R11 5.2,
  MEDIUM, so ± 2).
- E2 **Re-home (phase 01 re-run) and the app-list labels (T19-3).** Each hub page opened through its category still
  carries its tag (`settings_start_theme` under Personalization > Start, `settings_keyboard` under Time & language,
  `settings_checklist` and `settings_diagnostics` under Extras, `settings_about` under System, …) and its rows are unchanged
  (phase 01 E19's bar check re-run on one re-homed page: system bars hidden, and the drawn bars measured against phase 01's
  drawn status bar (`BarMetrics.STATUS_EPX`, `app/src/main/kotlin/app/tileshell/bars/SystemBars.kt:77-80`; its value is open
  at phase 01 against R11's 24 epx, r11/settings-front.md 4.1 — C-17) and `BarMetrics.NAV_EPX`, never a literal); `aapt dump
  badging` shows the launcher label "Settings"; in the app list `applist_row:com.android.settings`'s label text is "Android
  settings" and it sits under the A letter group (and the jump grid's A), the shell's entry reads "Settings" under S; search
  "settings" lists both with the shell's entry first; Android's Settings launches unchanged from its row (`dumpsys activity
  activities` resumes `com.android.settings/.Settings`, whose own label is untouched).
- E3 **Display (owned).** `adb shell appops set app.tileshell WRITE_SETTINGS allow`; drag `settings_brightness`
  to its right end → `settings get system screen_brightness` = 255; auto on → `screen_brightness_mode` = 1;
  rotation lock on → `accelerometer_rotation` = 0; timeout "1 minute" → `screen_off_timeout` = 60000; restore
  each to the baseline. `appops set app.tileshell WRITE_SETTINGS default` → the page's controls are disabled
  with "Allow Tessera to modify system settings" and a link that starts `Settings$WriteSettingsActivity`
  (`dumpsys activity activities`), the checklist row "Modify system settings" is red, diagnostics
  `[settingsfront] write_settings=denied`; restore `appops set … allow`. **Usage access revoked (T19-12):** MARK, `adb shell
  appops set app.tileshell GET_USAGE_STATS ignore`, open Network & wireless > Data usage, then System > Storage → each page
  shows one line naming the Setup checklist's usage-access row (dump text; no per-app totals drawn as zeros) and the slice
  holds `[settingsfront] read data_usage: denied (usage access)` and `[settingsfront] read storage: denied (usage access)`;
  `appops set app.tileshell GET_USAGE_STATS allow`, a new MARK, reopen both → totals drawn and neither line in the new
  slice (RV12).
- E4 **Sounds (owned).** With the grant, pick the second ringtone in the list (the AVD's ringtones under
  /product/media/audio/ringtones) → `settings get system ringtone` is that URI; pick a notification sound →
  `notification_sound`; restore both.
- E5 **Lock screen (owned).** Set a make_photos.py fixture as the lock-screen picture → `adb shell dumpsys
  wallpaper` shows the lock wallpaper's id changed from the recorded value; "Remove" clears it (id back to the
  system wallpaper); "screen times out after" is E3's setting shown again (same value).
- E6 **Date & time (owned).** 24-hour on → `settings get system time_12_24` = 24 and the drawn status bar's
  clock (phase 01) reads H:mm (dump text); off → 12 and h:mm; restore.
- E7 **Do not disturb (owned).** `adb shell cmd notification allow_dnd app.tileshell`; toggle on →
  `settings get global zen_mode` = 1 (`dumpsys notification` shows the zen state); off → 0; `cmd notification
  disallow_dnd app.tileshell` → the row is disabled with its grant link (`Settings$ZenAccessSettingsActivity`); restore
  `allow_dnd`.
- E8 **Deep-links (AOSP).** For every **link** and **panel** row in the table, the Apps rows included: tap → `dumpsys
  activity activities` topResumedActivity equals the table's AOSP activity (for a `package:` row, with the chosen app's
  package in the intent's data); Back returns to the front's category page (dump), and the diagnostics line
  `[settingsfront] link <action> -> <component>` is present. For the three **NONE** actions (USB, Windows Update,
  Regulatory — each a category row that is itself the link): the row shows the fallback second line "Change this in Android
  settings" under its label (Y2, Q4 A), a tap resumes `com.android.settings/.homepage.SettingsHomepageActivity`, and
  the line reads `-> none (fallback)`; every resolving category row stays one line (48 ± 1 epx, E1). The sidebar form has no
  unresolved action on AOSP, so the table-driven resolver's JVM test covers it: a sidebar entry whose resolution is null
  renders "Change this in Android settings" in the sidebar slot and logs `-> none (fallback)` (and P1 records any One UI
  sidebar link that fails to resolve). Apps > Apps & features: Uninstall on a fixture app (`org.fossify.notes`) resumes
  `com.android.packageinstaller/.UninstallerActivity` (CANCEL, nothing removed — `pm list packages` still lists it), and the
  shell's own row offers no Uninstall (qa/phase-01/UNINSTALL's method).
- E9 **Helper rows (after phase 04).** Where it runs: on the AVD only if phase 04 records at its build start that its helper
  runs there (Wireless-debugging pairing on the emulator is 04's build-start question; phase 04 today proves its toggles on
  the phone, its P2) — otherwise the running-helper sub-rows below move to P3 unchanged and the AVD keeps the helper-stopped
  sub-row (T19-7; this row mirrors whatever phase 04 records). **The four ruled toggles:** Wi-Fi off / on → `dumpsys wifi |
  grep -i "Wi-Fi is"`; airplane → `settings get global airplane_mode_on`; mobile data → `settings get global mobile_data`;
  Bluetooth → `settings get global bluetooth_on`. **The five ADDs (T19-1), each both directions with RV12 restore:**
  battery saver on → `settings get global low_power` = 1, off → 0 (restore 0); location off → `settings get secure
  location_mode` = 0, on → 3 (restore 3); NFC → on the AVD there is no NFC service, so the verb answers `unsupported`, the
  row shows the fallback line and its link, and `[settingsfront] helper nfc: unsupported` — a negative, not a skip; hotspot
  on → the tethering state in `dumpsys tethering` (a Wi-Fi tethered interface; `cmd wifi status`'s soft-AP line where
  present), off → none, or `unsupported` with the fallback line where the device cannot tether; auto-time off → `settings
  get global auto_time` = 0, on → 1 (restore 1). Battery saver is the one toggle AOSP refuses while the device is powered,
  and `wake_device` forces AC power (`qa/phase-03/scripts/lib.sh:47-56`), so its sub-row runs unplugged (C-18): `adb shell
  dumpsys battery unplug`, save and raise `screen_off_timeout` to 1800000, then the front's toggle drives the helper's
  `battery_saver on` (the verb is what is under test, so the row does not call `cmd power set-mode` itself) → `low_power` =
  1; toggle off → 0; restore through `lib.sh` `battery_saver_off` (`set-mode 0`, the timeout restored, `wake_device`; owner:
  phase 13's build task 7) and assert `wake_device` printed `Awake` (C-25). The same unplugged precondition is checked
  first with `lib.sh` `battery_saver_on` / `battery_saver_off` once, so a platform that refuses low-power mode unplugged fails
  loudly as a precondition, not as the verb. **Unproved verb:** for any verb R4 could not prove, its row is the table's
  link and diagnostics say `[settingsfront] helper <verb>: not built (<R4 reason>)`. **Allow-list negative** (phase 04's
  method, its edge case "a verb outside the allow-list"): a string that is not a table verb (`battery_saver on; id`, and a
  bare `settings put global low_power 1`) is refused and logged by the helper, and `settings get global low_power` is
  unchanged. **Helper stopped** (04's method): every one of the nine rows shows 04's "helper not running, tap to start"
  state (04 interview item 4's default) and flips nothing (each observable above unchanged).
- E10 **Find a setting.** `adb shell input text bright` → the dropdown under the box shows Display; `wi` → Wi-Fi; `zzz` →
  one row reading exactly "No results for zzz" (R11 5.3; ~~the empty line~~ SUPERSEDED 2026-09-23 by T19-11) and
  `[settingsfront] search "zzz": 0`; tapping a result opens that page (dump); the category list stays drawn under the dropdown
  (dump holds `settings_cat:System` while the dropdown is open).
- E11 **"All settings" (phase 04 coordination).** Phase 04's E4(d) re-run with the front as the target: the
  wipe plays and `dumpsys activity activities` resumes `app.tileshell/.settings.SettingsActivity` on the home
  page.
- E12 **Diagnostics.** Lines asserted by the rows above: `[settingsfront] write_settings=<allowed|denied>`,
  `[settingsfront] dnd_access=<allowed|denied>`, `[settingsfront] link <action> -> <component|none (fallback)>`,
  `[settingsfront] set <key>=<value>`, `[settingsfront] search "<q>": <n>`, `[settingsfront] helper <verb> <on|off>: ok |
  unsupported | refused | helper not running`, `[settingsfront] helper <verb>: not built (<reason>)`, `[settingsfront] read
  <data_usage|storage>: denied (usage access)` (T19-12), `[settingsfront] page <name> hidden` / `page <name> shown` (E15's
  one-frame cut), and every `[motion] <name> …` line a row times (`[motion]
  settings_page …`, E15). Coverage: grep the union of `qa/phase-19/*/ring-*.txt` from this build's run (the rows' APK id
  matching), each pattern at least once (C-20) — never one final ring, which every `am force-stop`, `pm clear` and
  `layout_restore` resets (`app/src/main/kotlin/app/tileshell/diag/Diagnostics.kt:14-27`, one in-memory ring per process).
- E13 **App list, surface, budget.** The shell's entry "Settings" under S and "Android settings" under A (E2's
  assertions); phase 02's regress.sh pattern passes; `dumpsys package app.tileshell` exported components equal
  qa/phase-03/exported-allowlist.txt (no ADD in this phase); `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤
  629,145,600 bytes.
- E14 **RV10.** `adb shell settings put system font_scale 1.3` → every measured value in E1 is unchanged
  (dump); restore `font_scale 1.0`.
- E15 **Page motion against `r11/settings-front.md` §6 (written 2026-09-23, T19-11; geometry is E1).** ~~written once that
  section lands (Y4's motion through the `[motion]` clock, C-5); not runnable before~~ SUPERSEDED 2026-09-23 by T19-11. Three
  navigations, each from a MARK taken just before the tap (C-20): home → System (category page), System → Display (leaf
  page), and Back from Display to System (U1: the same entrance). Each writes `[motion] settings_page t0=<uptime>
  first_offset_epx=<n> settle=<ms> frames=<n> maxGapMs=<ms>` (`withFrameNanos`, C-5 / C-31); the row asserts the first-frame
  offset 9.8 ± 1.2 epx (R11 6.3: 8.7–10.3 on two builds), 90 % of travel by 200 ± 17 ms, settle between 250 and 317 ms
  (250–300 plus one frame), `maxGapMs` ≤ 33.4; and the old page gone in one frame — the ring's `[settingsfront] page
  <old> hidden` and `page <new> shown` lines are ≤ 17 ms apart, corroborated by a screencap taken in the frame after the tap
  that shows no row of the old page (dump of that frame through phase 05's gesture-driver `dumpWindowHierarchy` with
  `setWaitForIdleTimeout(0)`). A 60-fps `adb shell screenrecord` corroborates under phase 05's frame-spacing rule (source-frame
  spacing ≤ 18.2 ms), never the primary clock. A leaf page's content that loads late pops in with no motion (R11 6.4): no
  `[motion]` line for it. Fails on a `PageTransition` fade, a slide of the wrong travel, an exit animation, or jank.
- E16 **Wizard steps added (phase 12 E14's template; C-4 b; re-cut 2026-09-23 by C-15).** Phase 12: every grant row is
  core, and the precedence is `not shown: core held` (every core row held, marker or not), else `not shown: finished`
  (marker set), else the run shows. (a) `pm clear app.tileshell` → `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh`
  → `adb shell appops set app.tileshell WRITE_SETTINGS default` and `cmd notification disallow_dnd app.tileshell` → Home:
  `wizard_step:setup:write_settings` and `wizard_step:setup:dnd_access` present, each `wizard_why` equal to Decisions' line,
  actions starting `Settings$WriteSettingsActivity` and `Settings$ZenAccessSettingsActivity`, `wizard_progress` reads "Step 1
  of 3" (two steps and the presets page); grant both from adb and resume → both gone and `wizard_presets` shows. (b) `pm
  clear` → `provision.sh` (marker written) → Home → no `wizard_page`, `[wizard] not shown: core held` (phase 12 E1 re-run on
  this build, C-4 c). (c) the finished-install rule: with the marker set (after (b)), revoke both → Home → no `wizard_page`,
  `[wizard] not shown: finished`, both checklist rows `missing`; restore both (RV12). ~~grant both from adb + Home → both
  absent and the wizard not shown; then phase 12 E1 re-run on this build (C-4 c)~~ SUPERSEDED 2026-09-23 by C-15 (with the
  marker written by provisioning, the wizard never shows after a plain `provision.sh`).
- E17 **App Shortcuts (C-8, T11-10).** The Settings tile (`shell:settings`, on `qa/phase-19/baseline_layout.json`, C-28):
  hold → `quick_sat_label:0..3` = "Start + theme", "Tile apps", "Setup", "Diagnostics" in rank order (phase 11 E3's
  method), and the slice holds the activity-keyed line `[quick] shortcuts for app.tileshell/.settings.SettingsActivity/0: 4
  (4 shown: start_theme,tile_apps,checklist,diagnostics)` (T11-12); tap each → `SettingsActivity`
  resumed with that page's tag in the dump (`settings_start_theme`, `settings_tile_apps`, `settings_checklist`,
  `settings_diagnostics`), and Back lands on its category page (`settings_cat:` header Personalization / Personalization /
  Extras / Extras); `dumpsys shortcut` lists the four ids as manifest shortcuts with ranks 0–3; `am force-stop
  app.tileshell` + Home between holds.

**Phone-only:**
- P1 Every action in the table started on the S25U with `adb shell am start -a <action>` (with `-d
  package:app.tileshell` where the table says `package:`), the resumed activity recorded next to the AOSP
  column; every unresolved action shows the fallback row (Q4 A); One UI's Software update reached or not through
  `SYSTEM_UPDATE_SETTINGS`. RECORDED per action; the fallback rows are gated.
- P2 One UI's "Modify system settings", "Do not disturb access" and Developer options pages from the
  checklist rows; the grants surviving a reboot and a Device care optimise; the two wizard steps on a phone with the grants
  revoked.
- P3 The helper rows against the real radios (phase 04 P2's observables) from this front, and the five ADDs on the S25U with
  R4's method: battery saver (`settings get global low_power`, and One UI's own battery-saver tile agreeing), location
  (`settings get secure location_mode`), NFC (`dumpsys nfc` state), hotspot (a client device sees the network and
  `dumpsys tethering` lists the Wi-Fi interface — or the verb was not built and the row is the link), auto-time (`settings
  get global auto_time`); each both directions with restore; each unproved verb's row is its link. Battery saver runs over
  Wireless debugging with the phone unplugged (One UI, like AOSP, may refuse it while charging); where that is not possible
  the charging state and One UI's behaviour are recorded with `record` (RECORDED; C-18, C-26).
- P4 Samsung's Side key page (no public action): the fallback row, recorded with `record`. RECORDED (C-26).
- P5 Screen zoom and font size on One UI leave the front unchanged (RV10, phase 01 P-rows' method).
- P6 The 24-hour toggle against One UI's own clock formats (the shell's status bar follows it; Samsung's
  shade is its own).

**NEEDS-HUMAN:** H1 *fidelity* — the front matches r11/settings-front.md on the phone: the home, category and leaf page
forms (E1), the 15063 category and page order, Apps & features and Apps for websites, and the measured page motion (E15,
R11 6.2–6.3); H2 *accept* — the category → page mapping (Y5) where W10M and Android disagree, and the P4 additions the table
marks (Printers, Regulatory, the Extras placements, the Display sidebar link, and whichever of Driving mode / Mouse /
Navigation bar the build-start check keeps or drops); H3 *accept* — the fallback wording and its two slots (Y2: the two-line
category row and the leaf-page sidebar entry, P4);
H4 *accept* — the category glyph shapes R11 read only at 1080p (U8); H5 *accept* — ~~page and toggle motion (Y4)~~ toggle
thumb and combo-box motion (U2, U3), page motion having moved to H1 (SUPERSEDED in part 2026-09-23 by T19-11); H6 *accept* — the
Start + theme split into Start and Colors, Transparency effects under Colors and the presets as Themes (T19-5); H7 *accept*
— the app list with "Settings" and "Android settings" as it looks on the phone (Q1 A); ~~the app-list result of Q1~~
SUPERSEDED 2026-09-23 by the ruled form (T19-10); H8 *accept* — the Settings tile's four satellites stay Start + theme /
Tile apps / Setup / Diagnostics, or Jeremy swaps them for categories (T11-10); H9 *accept* — the Apps category's two P4
pages, Default apps and App permissions (Y6; ~~a category with no W10M original~~ SUPERSEDED 2026-09-23 by T19-11: Apps is
W10M 15063's own category, its own pages are H1); H10 *accept* — the home "Find a setting" box drawn in its focused style
at rest without raising the keyboard (Y7, T19-11 agent call, R11 2.5).

## Edge cases
- Helper not running (phase 04's Y4 state): the four toggles and the five ADDs show "tap to start" and change nothing; a
  tap starts the helper's restart path (04's rule); no row here ever falls back to a Settings panel for them.
- `WRITE_SETTINGS` revoked while Display is open: the page re-checks on resume and disables its controls; a
  write refused by the platform (SecurityException) shows the grant line, never a silent no-op.
- A deep-link that resolves but finishes at once (a Samsung activity that redirects): Back lands on the front's
  page, not on Start; no loop (`dumpsys activity activities` task order).
- A deep-link target present on the AVD and absent on One UI (P1) and the reverse: the table's One UI column
  is what the phone row records; the row's fallback is the same code path either way.
- Android's own Settings changes a value underneath (brightness moved in Android's slider while the front is
  in the background): the front shows the new value on resume (observer); the same for a helper ADD's value changed from
  Samsung's quick panel (battery saver, location, auto-time re-read on resume).
- Device policy caps: a screen timeout above `DevicePolicyManager.getMaximumTimeToLock` is refused by the
  platform; the slider clamps and says so.
- "Find a setting" with no match, with a query matching a page whose row is a fallback on this phone (it still
  opens), and with a query typed while a page transition runs.
- Font size / Screen zoom changed by Android (RV10: unchanged here; Android's own pages follow it).
- The shell's tile `shell:settings` and Tess's "open Settings" (phase 03's open-app command) both open the
  front's home; the voice-interaction services' `settingsActivity` still resolves (phase 03 E-rows).
- The app list with Android's entry relabelled (Q1 A): search "settings" returns both, the shell's "Settings" first, then
  "Android settings"; search "android" finds Android's; a phone where `com.android.settings` is absent from the launcher
  (a work profile) shows no "Android settings" row and no error. ~~Two "Settings" entries (Q1 C) in the app list: search
  returns both, ordered by label then package.~~ SUPERSEDED 2026-09-23 by the Q1 A form (T19-3).
- Hotspot on a phone whose carrier blocks tethering: the verb reports the platform's refusal, the row shows it and its link,
  never a toggle that pretends.
- The accessibility service (phase 04) disabled: no effect on any owned page; the helper rows show 04's state.
- Liveness (N-01): reboot, Device care and a force-stop leave the grants and rows intact.

## QA evidence
_None yet._
