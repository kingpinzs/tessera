---
phase: 19
slug: settings-front
status: DRAFT   # split 2026-09-22; interview pending; lands AFTER phase 04 (its helper toggles); R11 §Settings gates FINAL
depends-on: [01, 04]
---

# Phase 19 — the W10M Settings front

## Goal
The shell's Settings app looks and reads like Windows 10 Mobile's Settings on the final release: the W10M
top level (the categories R11 §Settings confirms — candidate: System, Devices, Network & wireless,
Personalization, Accounts, Time & language, Ease of Access, Privacy, Update & security, Extras), a "Find a
setting" box, and a category page per entry listing W10M's pages. Every page is one of three kinds and says
which: **shell-owned** (the shell changes the setting itself), **helper** (phase 04's privileged helper flips
it), or **deep-link** (the page opens Android's own settings page; where nothing resolves, the row says
"Change this in Android settings" and opens Android's Settings home). Phase 01's Start settings hub pages are
re-homed under it, so there is ONE shell Settings app, not two (Rule 16). It is partial by nature: Samsung's
own settings pages cannot be replaced (PLAN "Android limits").

## Scope
**In:**
- The W10M Settings home (R3 C1 geometry: header block 57 ± 2 epx, "Find a setting" 31 epx tall with 12-epx
  margins, two-line rows at a 64 ± 1 epx pitch with a 30-epx glyph at x = 12 and text at x = 55, toggles
  44 × 20 epx), the category pages, and every page in the table below with its stated mechanism.
- Ownership: `SettingsActivity` keeps its component (every phase 01–05 driver, the voice-interaction and
  recognition services' `settingsActivity`, and the `shell:settings` tile point at it); its home page becomes
  the W10M top level; the hub's pages keep their code and test tags (`settings_start_theme`,
  `settings_tile_apps`, `settings_live_tile_access`, `settings_keyboard`, `settings_checklist`,
  `settings_diagnostics`, `settings_about`) and move under their categories; the launcher label per Q1.
- Shell-owned pages that need new grants: Display (brightness, auto-brightness, rotation lock, screen
  timeout via `WRITE_SETTINGS`, granted through `android.settings.action.MANAGE_WRITE_SETTINGS`), Sounds
  (ringtone and notification sound via `RingtoneManager`, the same grant), Lock screen background
  (`WallpaperManager` FLAG_LOCK, `SET_WALLPAPER`), Date & time's 24-hour toggle (`Settings.System.TIME_12_24`),
  Do not disturb (notification-policy access, `NOTIFICATION_POLICY_ACCESS_SETTINGS`), plus reads: battery,
  storage (`StorageStatsManager`; usage access is held), data usage (`NetworkStatsManager`; the same grant).
- Helper rows: Wi-Fi, Bluetooth, mobile data and airplane mode drawn with phase 04's own toggle-state UI
  (its Y4 / H7 "helper not running" form) — an ADD of a second surface over 04's verbs, not a second
  mechanism; further verbs only per Q3.
- Deep-link rows: a table-driven list; each resolves at open time (`PackageManager.resolveActivity`) and
  falls back per Q4; the per-page table below records the AOSP activity each action resolved to on the AVD
  (2026-09-22) and marks One UI as "verify on the phone".
- Phase 04's "All settings" quick action opens this front (coordination with phase 04, DRAFT: its E4(d) taps
  "All settings" as an app launch); "Find a setting" searches this front's page titles.
- Setup checklist rows: "Modify system settings" (`WRITE_SETTINGS`), "Do not disturb access"; diagnostics
  lines; the app-list regression; the exported allow-list (no new component); the APK budget.
**Out (explicitly):** replacing Samsung's settings pages, the status bar, the permission dialogs or the
lock-screen security (CANNOT, PLAN "Android limits"); flipping any toggle the platform reserves for system apps
without phase 04's helper (Wi-Fi, Bluetooth, data, airplane; location mode; NFC; hotspot) — no
"open a panel instead" placeholder for the four ruled toggles (phase 03 Scope's Rule 16 wording); a W10M page
with no Android counterpart (Kid's corner, Apps corner, Work access, Sync your settings, Windows Insider
Program, Find my phone, Backup — PLAN: no backup); a Microsoft-account sign-in; an "Apps" category (desktop
Windows' 15063 addition, not W10M's); any interim front that phase 04 later replaces (Rule 16).

## Decisions
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
  on the emulator, not only on the phone.
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
- 2026-09-22 (agent): **R11 gates FINAL.** The home and category pages take R3 C1's measured values (header,
  search box, row pitch, glyph, text x, toggle); what R3 C1 did not capture — category glyphs, the category
  page header, the fallback line, per-page layouts — is "from R11 §Settings" (pending) or a Y row with an
  H row (RV9 / Q10). The two kinds of NEEDS-HUMAN row are labelled (*fidelity* / *accept*), and
  qa/phase-19/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.
- 2026-09-22 (agent): **harness contracts.** `SettingsActivity` already signs `testTagsAsResourceId`; each
  category row and page row carries its tag (`settings_cat:<name>`, `settings_page:<name>`), the fallback
  line its own (`settings_fallback:<action>`), and every deep-link and fallback writes a diagnostics line
  (E12). Drivers symlink qa/phase-03/scripts/lib.sh; evidence under qa/phase-19/.
- 2026-09-22 (agent): **APK budget** — no new dependency; E13 checks the size against phase 03's ≤ 600 MB.

### Per-page table
Mechanism: **owned** = the shell changes it; **helper** = phase 04's helper (R4-gated); **link** = opens
Android's page; **panel** = a system Settings panel; **read** = the shell shows a value it reads. "AOSP
(AVD)" = the activity `cmd package query-activities` resolved on 2026-09-22; **NONE** = no handler, the
fallback line applies. "One UI" = verify on the S25U (P1) — Samsung substitutes its own activities.

| Category > page | What it shows | Mechanism | AOSP (AVD) 2026-09-22 | One UI |
|---|---|---|---|---|
| System > Display | brightness slider, auto-brightness, rotation lock, screen timeout; "more" → Android | owned (`WRITE_SETTINGS`: `screen_brightness`, `screen_brightness_mode`, `accelerometer_rotation`, `screen_off_timeout`) + link `DISPLAY_SETTINGS` | `com.android.settings/.Settings$DisplaySettingsActivity` | verify |
| System > Notifications & actions | phase 04's quick-actions page (its part); per-app notifications; Do not disturb | owned (DND via notification-policy access) + link `ALL_APPS_NOTIFICATION_SETTINGS`; access grant `NOTIFICATION_POLICY_ACCESS_SETTINGS` | `Settings$NotificationAppListActivity`; `Settings$ZenAccessSettingsActivity` | verify |
| System > Phone / Messaging | phase 06's pages when built (its ADD); default apps | link `MANAGE_DEFAULT_APPS_SETTINGS` | `com.android.permissioncontroller/.role.ui.DefaultAppListActivity` | verify |
| System > Battery saver | level and charging state; battery use; battery saver | read (`BatteryManager`) + link `POWER_USAGE_SUMMARY`, `BATTERY_SAVER_SETTINGS` (helper only if Q3 adds it) | `Settings$PowerUsageSummaryActivity`; `Settings$BatterySaverSettingsActivity` | verify |
| System > Storage | used / free per volume; "Files" opens phase 18; manage | read (`StorageStatsManager`) + link `INTERNAL_STORAGE_SETTINGS` | `Settings$StorageDashboardActivity` | verify |
| System > Apps for websites | apps' default links | link `MANAGE_ALL_APPLICATIONS_SETTINGS` (per-app: `APP_OPEN_BY_DEFAULT_SETTINGS` with `package:`) | `Settings$ManageApplicationsActivity`; `.applications.InstalledAppOpenByDefaultActivity` | verify |
| System > About | the shell's About page (owned, exists); device name, Android version | owned + link `DEVICE_INFO_SETTINGS` | `Settings$MyDeviceInfoActivity` | verify |
| Devices > Default camera | the Camera slot picker (owned, exists: `Route.Picker(Slot.CAMERA)`) | owned | — | — |
| Devices > Bluetooth | toggle; paired devices; pair new | helper (04) + link `BLUETOOTH_SETTINGS`, `BLUETOOTH_PAIRING_SETTINGS` | `Settings$ConnectedDeviceDashboardActivity`; `Settings$BlueToothPairingActivity` | verify |
| Devices > NFC | toggle state; Android's page | read + panel `panel.action.NFC` / link `NFC_SETTINGS` (helper only per Q3) | `.panel.SettingsPanelActivity`; `Settings$NfcSettingsActivity` (no NFC hardware on the AVD) | verify |
| Devices > USB | USB preferences | link — `USB_SETTINGS` | **NONE** → fallback | verify |
| Devices > Printers | print services | link `ACTION_PRINT_SETTINGS` | `Settings$PrintSettingsActivity` | verify |
| Network & wireless > Wi-Fi | toggle; connect to a network; more | helper (04) + panel `panel.action.WIFI` + link `WIFI_SETTINGS` | `.panel.SettingsPanelActivity`; `Settings$WifiSettingsActivity` | verify |
| Network & wireless > Airplane mode | toggle | helper (04) + link `AIRPLANE_MODE_SETTINGS` | `Settings$NetworkDashboardActivity` | verify |
| Network & wireless > Cellular & SIM | mobile data toggle; network page | helper (04) + link `DATA_ROAMING_SETTINGS` | `Settings$MobileNetworkActivity` | verify |
| Network & wireless > Data usage | this cycle's totals per app | read (`NetworkStatsManager`) + link `DATA_USAGE_SETTINGS` | `Settings$DataUsageSummaryActivity` | verify |
| Network & wireless > Mobile hotspot | Android's page (helper only per Q3) | link `TETHER_SETTINGS` | `Settings$TetherSettingsActivity` | verify |
| Network & wireless > VPN | Android's page | link `VPN_SETTINGS` | `Settings$VpnSettingsActivity` | verify |
| Personalization > Start | background, show more tiles, transparency, press effect, bottom row, Tile apps, Live tile access (phase 01's rows, re-homed) | owned | — | — |
| Personalization > Colors | accent, light / dark (phase 01's rows, re-homed) | owned | — | — |
| Personalization > Sounds | ringtone, notification sound; key sounds (phase 05's row) | owned (`RingtoneManager.setActualDefaultRingtoneUri`, `WRITE_SETTINGS`) + link `SOUND_SETTINGS` | `Settings$SoundSettingsActivity` | verify |
| Personalization > Lock screen | background picture; screen times out after; notifications on lock screen; sign-in | owned (`WallpaperManager` FLAG_LOCK; `screen_off_timeout`) + link `LOCK_SCREEN_SETTINGS`, `SECURITY_SETTINGS`, `SET_NEW_PASSWORD` | `Settings$LockScreenSettingsActivity`; `Settings$SecurityDashboardActivity`; `.password.SetNewPasswordActivity` | verify |
| Personalization > Glance screen | phase 07's page when built (its ADD) | owned (07) | — | — |
| Accounts > Your email and accounts | accounts; add account | link `SYNC_SETTINGS`, `ADD_ACCOUNT_SETTINGS` | `Settings$AccountDashboardActivity`; `.accounts.AddAccountSettings` | verify |
| Accounts > Sign-in options | PIN / fingerprint / face | link `SECURITY_SETTINGS`, `BIOMETRIC_ENROLL` | `Settings$SecurityDashboardActivity`; `.biometrics.BiometricEnrollActivity` | verify |
| Time & language > Date & time | 24-hour clock; date, time and zone | owned (`TIME_12_24`) + link `DATE_SETTINGS` | `Settings$DateTimeSettingsActivity` | verify |
| Time & language > Language / Region | Android's pages | link `LOCALE_SETTINGS`, `REGIONAL_PREFERENCES_SETTINGS` | `Settings$LocalePickerActivity`; `Settings$RegionalPreferencesActivity` | verify |
| Time & language > Keyboard | phase 05's Keyboard page (owned, exists); manage keyboards | owned + link `INPUT_METHOD_SETTINGS` | `Settings$AvailableVirtualKeyboardActivity` | verify |
| Time & language > Speech | Tess's settings page (phase 03, owned, exists: voice, Notes slot, lock-screen options); assistant app | owned + link `VOICE_INPUT_SETTINGS` | `Settings$ManageAssistActivity` | verify |
| Ease of Access > Narrator / Magnifier / More options | Android's accessibility pages | link `ACCESSIBILITY_SETTINGS` | `Settings$AccessibilitySettingsActivity` | verify |
| Ease of Access > High contrast | Android's page | link `ACCESSIBILITY_COLOR_CONTRAST_SETTINGS` | `Settings$ColorContrastActivity` | verify |
| Ease of Access > Closed captions | Android's page | link `CAPTIONING_SETTINGS` | `Settings$CaptioningSettingsActivity` | verify |
| Ease of Access > Text size | Android's page (RV10: the shell does not follow it) | link `TEXT_READING_SETTINGS` | `Settings$TextReadingSettingsActivity` | verify |
| Privacy > Location | state; Android's page (helper only per Q3) | read (`LocationManager.isLocationEnabled`) + link `LOCATION_SOURCE_SETTINGS` | `Settings$LocationSettingsActivity` | verify |
| Privacy > Camera / Microphone / Contacts / Calendar / … | per-permission pages | link `PRIVACY_SETTINGS`; per app `APPLICATION_DETAILS_SETTINGS` with `package:` | `Settings$PrivacyDashboardActivity`; `.applications.InstalledAppDetails` | verify |
| Privacy > Notifications | apps with notification access | link `ACTION_NOTIFICATION_LISTENER_SETTINGS` | `Settings$NotificationAccessSettingsActivity` | verify |
| Update & security > Phone update | Android's update page | link `SYSTEM_UPDATE_SETTINGS` | **NONE** → fallback | verify (One UI has Software update) |
| Update & security > For developers | Developer options (phase 04's Wireless debugging row needs it) | link `APPLICATION_DEVELOPMENT_SETTINGS` | `Settings$DevelopmentSettingsActivity` | verify |
| Extras > Setup | the Setup checklist (owned, exists) | owned | — | — |
| Extras > Diagnostics | the Diagnostics page (owned, exists) | owned | — | — |
| Extras > Regulatory | Android's page | link `SHOW_REGULATORY_INFO` | **NONE** → fallback | verify |

W10M pages with no row above are OUT (Scope). Where R11 §Settings shows a page this table lacks, or a
different category placement, the table follows R11 and the change is recorded here.

### Approximations (until R11 lands; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Category glyphs and the category page header | R11 §Settings pending (R3 C1 has the home page only) | R3 C1's row form with Fluent-icon glyphs; page header as the hub's `PageHeader` | H4 |
| Y2 | The fallback line ("Change this in Android settings") and its row form | no W10M original (P4) | a two-line row whose subtitle carries the line, accent-coloured title | H3 |
| Y3 | Helper-row state UI | phase 04's Y4 / H7 | 04's own form reused | 04's H7 |
| Y4 | Page transitions and toggle motion | UNMEASURED | the hub's existing `PageTransition` (phase 01) | H5 |
| Y5 | Category → page mapping where W10M and Android disagree | agent call | the table above | H2 |

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3).

1. **Q1 — the app-list identity.** Today the hub's launcher label is "Start settings" so it is not confused
   with Android's Settings (phase 01 build-start call 7). With the W10M front that name stops being right.
   A. Ours is "Settings"; Android's Settings stays in the app list relabelled "Android settings" (the
   slot-label rule of 2026-09-22 extended to one app entry). (lean — one "Settings", nothing hidden)
   B. Ours is "Settings"; Android's Settings is hidden from the app list and reached only through the front's
   links and its "All Android settings" row.
   C. Keep "Start settings" and leave Android's entry alone (two entries, two names).
   D. Other / let me clarify.
2. **Q2 — the category set.**
   A. W10M final's categories exactly, omitting only pages with no Android counterpart (Kid's corner, Work
   access, Windows Insider, …). (lean)
   B. Only the categories holding at least one page the shell can change itself (fewer, but not W10M's list).
   C. W10M's categories plus an "Apps" category for the app list's Uninstall / defaults / permissions (desktop
   Windows 10's 2017 addition, "as if they never stopped developing it").
   D. Other / let me clarify.
3. **Q3 — helper verbs beyond phase 04's four.** The helper's verb allow-list is a trust surface (phase 04
   Decisions: adversarial review, no generic shell); each verb is R4-proven on the phone.
   A. None: battery saver, location, NFC and hotspot are deep-links; the helper keeps its four verbs. (lean —
   no new trust surface in this phase)
   B. Add battery saver and location (two verbs, each proved by R4's method and reviewed).
   C. Add every toggle the shell uid can flip (battery saver, location, NFC, hotspot, auto-time).
   D. Other / let me clarify.
4. **Q4 — a deep-link that resolves to nothing on this phone.**
   A. The row stays, its subtitle reads "Change this in Android settings", and a tap opens Android's Settings
   home. (lean — the page keeps W10M's shape; nothing is silently missing)
   B. The row is hidden on that phone.
   C. The row stays, disabled, with the reason.
   D. Other / let me clarify.

## Build tasks
1. **Top level.** The W10M home (R3 C1 values), "Find a setting" (searches this front's page titles; empty
   line for no match), the category pages, `settings_cat:` / `settings_page:` tags, the fallback row form (Y2).
2. **Re-home.** The hub's pages under their categories with tags unchanged; Start + theme's rows split
   across Personalization > Start and Colors (H6); the launcher label per Q1 (and Android's entry per Q1);
   INDEX Change Log entry for phase 01's part.
3. **Owned pages.** Display, Sounds, Lock screen, Date & time (24-hour), Do not disturb; the reads (battery,
   storage, data usage, NFC and location state); `WRITE_SETTINGS` and DND-access grants with their checklist
   rows and the disabled-with-reason state; re-read on resume and through a `ContentObserver` on
   `Settings.System` so a change made in Android's own page shows at once.
4. **Helper rows.** The four toggles over phase 04's verbs with 04's state UI; per Q3, further verbs are
   phase 04 ADDs (its allow-list, its R4 proof, its review), not this phase's.
5. **Deep-link rows.** One table-driven list (action, optional `package:` data, W10M title, subtitle);
   resolve at open, fallback per Q4, diagnostics line per tap; the panels for Wi-Fi and NFC.
6. **"All settings"** from phase 04's action center opens this front (04's E4(d) target), coordinated with
   phase 04's build.
7. **Regressions.** Phase 01 E19 on a re-homed page; every phase 01 / 05 driver that opens a hub page by
   tag; the app-list regression (phase 02's regress.sh pattern); the exported allow-list (unchanged); APK size.
8. **R11 values applied.** Once R11 §Settings lands, the Y rows it measures are replaced and E14 is written;
   FINAL only then (RV9).

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps
follow RV13. Harness: qa/phase-19/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh. Device: the AOSP AVD
tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google). Baseline values recorded on the AVD 2026-09-22:
`screen_brightness` 102, `screen_brightness_mode` 0, `accelerometer_rotation` 1, `screen_off_timeout`
2147483647, `zen_mode` 0, `navigation_mode` 0. Helper rows (E9) run only after phase 04's own rows pass.

**Emulator:**
- E1 **Structure (R3 C1).** The Settings home dump lists the category rows in W10M's order (`settings_cat:`
  tags, the set per Q2); "Find a setting" measures 31 ± 2 epx tall with 12-epx side margins; row pitch
  64 ± 1 epx; glyph 30 epx at x = 12 epx; text at x = 55 epx; the header block 57 ± 2 epx (dump bounds and
  screencap, px ÷ 3 = epx); each category page lists exactly the table's pages for it (dump).
- E2 **Re-home (phase 01 re-run).** Each hub page opened through its category still carries its tag
  (`settings_start_theme` under Personalization > Start, `settings_keyboard` under Time & language, `settings_checklist`
  and `settings_diagnostics` under Extras, `settings_about` under System, …) and its rows are unchanged
  (phase 01 E19's bar check re-run on one re-homed page: system bars hidden, drawn bars measured); `aapt dump
  badging` shows the launcher label per Q1; Android's Settings entry in the app list per Q1 (dump).
- E3 **Display (owned).** `adb shell appops set app.tileshell WRITE_SETTINGS allow`; drag `settings_brightness`
  to its right end → `settings get system screen_brightness` = 255; auto on → `screen_brightness_mode` = 1;
  rotation lock on → `accelerometer_rotation` = 0; timeout "1 minute" → `screen_off_timeout` = 60000; restore
  each to the baseline. `appops set app.tileshell WRITE_SETTINGS default` → the page's controls are disabled
  with "Allow Tessera to modify system settings" and a link that starts `Settings$WriteSettingsActivity`
  (`dumpsys activity activities`), the checklist row "Modify system settings" is red, diagnostics
  `[settingsfront] write_settings=denied`.
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
  disallow_dnd app.tileshell` → the row is disabled with its grant link (`Settings$ZenAccessSettingsActivity`).
- E8 **Deep-links (AOSP).** For every **link** and **panel** row in the table: tap → `dumpsys activity
  activities` topResumedActivity equals the table's AOSP activity; Back returns to the front's category page
  (dump), and the diagnostics line `[settingsfront] link <action> -> <component>` is present. For the three
  **NONE** actions: the row shows the fallback subtitle (Q4 A), a tap resumes
  `com.android.settings/.homepage.SettingsHomepageActivity`, and the line reads `-> none (fallback)`.
- E9 **Helper rows (after phase 04).** Wi-Fi off / on → `dumpsys wifi | grep -i "Wi-Fi is"`; airplane →
  `settings get global airplane_mode_on`; mobile data → `settings get global mobile_data`; Bluetooth →
  `settings get global bluetooth_on`; with the helper stopped (04's method) each row shows 04's "helper not
  running, tap to start" state (04 interview item 4's default) and flips nothing.
- E10 **Find a setting.** `adb shell input text bright` → the list shows Display; `wi` → Wi-Fi; `zzz` → the
  empty line; tapping a result opens that page (dump).
- E11 **"All settings" (phase 04 coordination).** Phase 04's E4(d) re-run with the front as the target: the
  wipe plays and `dumpsys activity activities` resumes `app.tileshell/.settings.SettingsActivity` on the home
  page.
- E12 **Diagnostics.** Lines asserted by the rows above: `[settingsfront] write_settings=<allowed|denied>`,
  `[settingsfront] dnd_access=<allowed|denied>`, `[settingsfront] link <action> -> <component|none (fallback)>`,
  `[settingsfront] set <key>=<value>`, `[settingsfront] search "<q>": <n>`.
- E13 **App list, surface, budget.** The Settings entry per Q1 under S; phase 02's regress.sh pattern passes;
  `dumpsys package app.tileshell` exported components equal qa/phase-03/exported-allowlist.txt (no ADD in this
  phase); `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes.
- E14 **RV10.** `adb shell settings put system font_scale 1.3` → every measured value in E1 is unchanged
  (dump); restore `font_scale 1.0`.
- E15 **Geometry against R11 §Settings** — written once R11 lands; not runnable before, and the doc cannot go
  FINAL without it.

**Phone-only:**
- P1 Every action in the table started on the S25U with `adb shell am start -a <action>` (with `-d
  package:app.tileshell` where the table says `package:`), the resumed activity recorded next to the AOSP
  column; every unresolved action shows the fallback row (Q4); One UI's Software update reached or not through
  `SYSTEM_UPDATE_SETTINGS`.
- P2 One UI's "Modify system settings", "Do not disturb access" and Developer options pages from the
  checklist rows; the grants surviving a reboot and a Device care optimise.
- P3 The helper rows against the real radios (phase 04 P2's observables) from this front.
- P4 Samsung's Side key page (no public action): the fallback row, recorded.
- P5 Screen zoom and font size on One UI leave the front unchanged (RV10, phase 01 P-rows' method).
- P6 The 24-hour toggle against One UI's own clock formats (the shell's status bar follows it; Samsung's
  shade is its own).

**NEEDS-HUMAN:** H1 *fidelity* — the front matches R11 §Settings on the phone; H2 *accept* — the category →
page mapping (Y5) where W10M and Android disagree; H3 *accept* — the fallback row wording and form (Y2, P4);
H4 *accept* — any Y1 value R11 does not measure; H5 *accept* — page and toggle motion (Y4); H6 *accept* — the
Start + theme split into Start and Colors; H7 *accept* — the app-list result of Q1 as it looks on the phone.

## Edge cases
- Helper not running (phase 04's Y4 state): the four toggles show "tap to start" and change nothing; a tap
  starts the helper's restart path (04's rule); no row here ever falls back to a Settings panel for them.
- `WRITE_SETTINGS` revoked while Display is open: the page re-checks on resume and disables its controls; a
  write refused by the platform (SecurityException) shows the grant line, never a silent no-op.
- A deep-link that resolves but finishes at once (a Samsung activity that redirects): Back lands on the front's
  page, not on Start; no loop (`dumpsys activity activities` task order).
- A deep-link target present on the AVD and absent on One UI (P1) and the reverse: the table's One UI column
  is what the phone row records; the row's fallback is the same code path either way.
- Android's own Settings changes a value underneath (brightness moved in Android's slider while the front is
  in the background): the front shows the new value on resume (observer).
- Device policy caps: a screen timeout above `DevicePolicyManager.getMaximumTimeToLock` is refused by the
  platform; the slider clamps and says so.
- "Find a setting" with no match, with a query matching a page whose row is a fallback on this phone (it still
  opens), and with a query typed while a page transition runs.
- Font size / Screen zoom changed by Android (RV10: unchanged here; Android's own pages follow it).
- The shell's tile `shell:settings` and Tess's "open Settings" (phase 03's open-app command) both open the
  front's home; the voice-interaction services' `settingsActivity` still resolves (phase 03 E-rows).
- Two "Settings" entries (Q1 C) in the app list: search returns both, ordered by label then package.
- The accessibility service (phase 04) disabled: no effect on any owned page; the helper rows show 04's state.
- Liveness (N-01): reboot, Device care and a force-stop leave the grants and rows intact.

## QA evidence
_None yet._
