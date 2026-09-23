---
phase: 19
slug: settings-front
status: DRAFT   # split 2026-09-22; interview DONE 2026-09-23; review triage round 1 applied 2026-09-23 (review/2026-09-23-phases11-19-triage.md); lands AFTER phase 04 (its helper toggles); r11/settings-front.md gates FINAL (pending on disk 2026-09-23 — docs/plan/r11-inbox-apps.md is the index; r11/src/settings-front/ holds the sources)
depends-on: [01, 04]   # the front lists what exists at build time (T19-6): phase 18's Files under Storage (and its Recycle Bin size), phase 12's presets (Themes) and phase 13's Transparency effects (Colors) when built, each by the ADD rule phase 01 already has
---

# Phase 19 — the W10M Settings front

## Goal
The shell's Settings app looks and reads like Windows 10 Mobile's Settings on the final release: the W10M
top level (W10M final's categories plus "Apps", Q2 C — the set in the per-page table: System, Devices, Network & wireless,
Personalization, Accounts, Time & language, Ease of Access, Privacy, Update & security, Extras, Apps; r11/settings-front.md
confirms order and glyphs), a "Find a setting" box, and a category page per entry listing W10M's pages. Every page is one
of three kinds and says which: **shell-owned** (the shell changes the setting itself), **helper** (phase 04's privileged
helper flips it — its four ruled verbs plus the five this phase ADDs, Q3 C), or **deep-link** (the page opens Android's
own settings page; where nothing resolves, the row says "Change this in Android settings" and opens Android's Settings
home). Phase 01's Start settings hub pages are re-homed under it, so there is ONE shell Settings app, not two (Rule 16).
It is partial by nature: Samsung's own settings pages cannot be replaced (PLAN "Android limits").

## Scope
**In:**
- The W10M Settings home (R3 C1 geometry: header block 57 ± 2 epx, "Find a setting" 31 epx tall with 12-epx
  margins, two-line rows at a 64 ± 1 epx pitch with a 30-epx glyph at x = 12 and text at x = 55, toggles
  44 × 20 epx), the category pages, and every page in the table below with its stated mechanism.
- Ownership: `SettingsActivity` keeps its component (every phase 01–05 driver, the voice-interaction and
  recognition services' `settingsActivity`, and the `shell:settings` tile point at it); its home page becomes
  the W10M top level; the hub's pages keep their code and test tags (`settings_start_theme`,
  `settings_tile_apps`, `settings_live_tile_access`, `settings_keyboard`, `settings_checklist`,
  `settings_diagnostics`, `settings_about`) and move under their categories; the launcher label is "Settings" and
  Android's Settings is relabelled "Android settings" in the shell's app list (Q1 A; Decisions).
- Shell-owned pages that need new grants: Display (brightness, auto-brightness, rotation lock, screen
  timeout via `WRITE_SETTINGS`, granted through `android.settings.action.MANAGE_WRITE_SETTINGS`), Sounds
  (ringtone and notification sound via `RingtoneManager`, the same grant), Lock screen background
  (`WallpaperManager` FLAG_LOCK, `SET_WALLPAPER`), Date & time's 24-hour toggle (`Settings.System.TIME_12_24`),
  Do not disturb (notification-policy access, `NOTIFICATION_POLICY_ACCESS_SETTINGS`), plus reads: battery,
  storage (`StorageStatsManager`; usage access is held; phase 18's Recycle Bin size with Empty), data usage
  (`NetworkStatsManager`; the same grant).
- Helper rows: Wi-Fi, Bluetooth, mobile data and airplane mode drawn with phase 04's own toggle-state UI
  (its Y4 / H7 "helper not running" form) — an ADD of a second surface over 04's verbs, not a second
  mechanism; and the five verbs Q3 C adds — battery saver, location, NFC, hotspot, automatic time — BUILT BY THIS PHASE as
  ADDs to phase 04's helper (one named allow-list verb per toggle, each R4-proven on the phone and adversarially reviewed;
  Decisions, T19-1). ~~further verbs only per Q3~~ SUPERSEDED 2026-09-23 by Q3 C (T19-10).
- Deep-link rows: a table-driven list; each resolves at open time (`PackageManager.resolveActivity`) and
  falls back to "Change this in Android settings" + Android's Settings home (Q4 A); the per-page table below records the
  AOSP activity each action resolved to on the AVD (2026-09-22; the Apps rows 2026-09-23) and marks One UI as "verify on
  the phone".
- The Apps category (Q2 C): Apps & features (with Uninstall through the app list's path), Default apps, Apps for websites,
  App permissions (table; T19-2).
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
Program, Find my phone, Backup — PLAN: no backup); a Microsoft-account sign-in; a generic command verb in the helper (one
named verb per toggle, Q3); any interim front that phase 04 later replaces (Rule 16).
~~an "Apps" category (desktop Windows' 15063 addition, not W10M's)~~ SUPERSEDED 2026-09-23 by Q2 C (T19-2): Apps is in.

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
- 2026-09-22 (agent): **R11 gates FINAL.** The home and category pages take R3 C1's measured values (header,
  search box, row pitch, glyph, text x, toggle); what R3 C1 did not capture — category glyphs, the category
  page header, the fallback line, per-page layouts — is "from R11 §Settings" (pending — R11 is now an index,
  docs/plan/r11-inbox-apps.md; this phase's section is `docs/plan/r11/settings-front.md`, not written as of 2026-09-23 —
  C-12) or a Y row with an
  H row (RV9 / Q10). The two kinds of NEEDS-HUMAN row are labelled (*fidelity* / *accept*), and
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
  Personalization > Colors (Windows 10's own placement). H6 lists both; H9 covers a category with no W10M original.
- 2026-09-23 (review triage C-4, a doc update): **this phase's two grants join the setup wizard.** "Modify system settings"
  (`WRITE_SETTINGS`, Settings-page step, action `MANAGE_WRITE_SETTINGS` with `package:app.tileshell`, why line "Settings can
  change brightness, sounds and the screen timeout for you. Without it those pages only show them.") and "Do not disturb
  access" (Settings-page step, action `NOTIFICATION_POLICY_ACCESS_SETTINGS`, why line "Settings can turn Do not disturb on
  and off. Without it the switch can't change anything.") are wizard steps `wizard_step:setup:write_settings` and
  `wizard_step:setup:dnd_access`; `qa/phase-03/scripts/provision.sh` gains `adb shell appops set app.tileshell WRITE_SETTINGS
  allow` and `adb shell cmd notification allow_dnd app.tileshell`; E16 is phase 12 E14's template for both; phase 12 E1
  re-runs on this build. Phase 12's rule, restated: a finished or skipped wizard is never re-summoned on that install — these
  grants go red on the Setup checklist instead.

### Per-page table
Mechanism: **owned** = the shell changes it; **helper** = phase 04's helper (R4-gated); **helper (04 ADD)** = a verb this
phase ADDs to that helper (T19-1; R4-gated, reviewed; a verb R4 cannot prove leaves the row as its link); **link** = opens
Android's page; **panel** = a system Settings panel; **read** = the shell shows a value it reads. "AOSP
(AVD)" = the activity `cmd package query-activities` resolved on 2026-09-22 (Apps rows 2026-09-23); **NONE** = no handler,
the fallback line applies. "One UI" = verify on the S25U (P1) — Samsung substitutes its own activities.

| Category > page | What it shows | Mechanism | AOSP (AVD) 2026-09-22 | One UI |
|---|---|---|---|---|
| System > Display | brightness slider, auto-brightness, rotation lock, screen timeout; "more" → Android | owned (`WRITE_SETTINGS`: `screen_brightness`, `screen_brightness_mode`, `accelerometer_rotation`, `screen_off_timeout`) + link `DISPLAY_SETTINGS` | `com.android.settings/.Settings$DisplaySettingsActivity` | verify |
| System > Notifications & actions | phase 04's quick-actions page (its part); per-app notifications; Do not disturb | owned (DND via notification-policy access) + link `ALL_APPS_NOTIFICATION_SETTINGS`; access grant `NOTIFICATION_POLICY_ACCESS_SETTINGS` | `Settings$NotificationAppListActivity`; `Settings$ZenAccessSettingsActivity` | verify |
| System > Phone / Messaging | phase 06's pages when built (its ADD) ~~; default apps~~ (MOVED 2026-09-23 to Apps > Default apps, T19-2) | owned (06) ~~link `MANAGE_DEFAULT_APPS_SETTINGS`~~ | — | — |
| System > Battery saver | level and charging state; battery use; battery saver toggle | read (`BatteryManager`) + helper (04 ADD, `battery_saver`) + link `POWER_USAGE_SUMMARY`, `BATTERY_SAVER_SETTINGS` ~~(helper only if Q3 adds it)~~ | `Settings$PowerUsageSummaryActivity`; `Settings$BatterySaverSettingsActivity` | verify |
| System > Storage | used / free per volume; the Recycle Bin's size per volume with Empty (phase 18, T18-1); "Files" opens phase 18; manage | read (`StorageStatsManager`; phase 18's bin index) + owned (Empty, through phase 18's bin) + link `INTERNAL_STORAGE_SETTINGS` | `Settings$StorageDashboardActivity` | verify |
| ~~System > Apps for websites~~ | MOVED 2026-09-23 to Apps > Apps for websites (T19-2) | — | — | — |
| System > About | the shell's About page (owned, exists); device name, Android version | owned + link `DEVICE_INFO_SETTINGS` | `Settings$MyDeviceInfoActivity` | verify |
| Devices > Default camera | the Camera slot picker (owned, exists: `Route.Picker(Slot.CAMERA)`) | owned | — | — |
| Devices > Bluetooth | toggle; paired devices; pair new | helper (04) + link `BLUETOOTH_SETTINGS`, `BLUETOOTH_PAIRING_SETTINGS` | `Settings$ConnectedDeviceDashboardActivity`; `Settings$BlueToothPairingActivity` | verify |
| Devices > NFC | toggle; Android's page | helper (04 ADD, `nfc`) + read + panel `panel.action.NFC` / link `NFC_SETTINGS` ~~(helper only per Q3)~~ | `.panel.SettingsPanelActivity`; `Settings$NfcSettingsActivity` (no NFC hardware on the AVD: `dumpsys nfc` = "Can't find service: nfc", 2026-09-23) | verify |
| Devices > USB | USB preferences | link — `USB_SETTINGS` | **NONE** → fallback | verify |
| Devices > Printers | print services | link `ACTION_PRINT_SETTINGS` | `Settings$PrintSettingsActivity` | verify |
| Network & wireless > Wi-Fi | toggle; connect to a network; more | helper (04) + panel `panel.action.WIFI` + link `WIFI_SETTINGS` | `.panel.SettingsPanelActivity`; `Settings$WifiSettingsActivity` | verify |
| Network & wireless > Airplane mode | toggle | helper (04) + link `AIRPLANE_MODE_SETTINGS` | `Settings$NetworkDashboardActivity` | verify |
| Network & wireless > Cellular & SIM | mobile data toggle; network page | helper (04) + link `DATA_ROAMING_SETTINGS` | `Settings$MobileNetworkActivity` | verify |
| Network & wireless > Data usage | this cycle's totals per app | read (`NetworkStatsManager`) + link `DATA_USAGE_SETTINGS` | `Settings$DataUsageSummaryActivity` | verify |
| Network & wireless > Mobile hotspot | toggle (if R4 finds a tethering path); Android's page | helper (04 ADD, `hotspot`) + link `TETHER_SETTINGS` ~~(helper only per Q3)~~ | `Settings$TetherSettingsActivity` | verify |
| Network & wireless > VPN | Android's page | link `VPN_SETTINGS` | `Settings$VpnSettingsActivity` | verify |
| Personalization > Start | background, show more tiles, transparency, press effect, bottom row, Tile apps, Live tile access (phase 01's rows, re-homed) | owned | — | — |
| Personalization > Colors | accent, light / dark (phase 01's rows, re-homed); Transparency effects (phase 13's switch, T19-5) | owned | — | — |
| Personalization > Themes | phase 12's preset page (T19-5; when built) | owned (12) | — | — |
| Personalization > Sounds | ringtone, notification sound; key sounds (phase 05's row) | owned (`RingtoneManager.setActualDefaultRingtoneUri`, `WRITE_SETTINGS`) + link `SOUND_SETTINGS` | `Settings$SoundSettingsActivity` | verify |
| Personalization > Lock screen | background picture; screen times out after; notifications on lock screen; sign-in | owned (`WallpaperManager` FLAG_LOCK; `screen_off_timeout`) + link `LOCK_SCREEN_SETTINGS`, `SECURITY_SETTINGS`, `SET_NEW_PASSWORD` | `Settings$LockScreenSettingsActivity`; `Settings$SecurityDashboardActivity`; `.password.SetNewPasswordActivity` | verify |
| Personalization > Glance screen | phase 07's page when built (its ADD) | owned (07) | — | — |
| Accounts > Your email and accounts | accounts; add account | link `SYNC_SETTINGS`, `ADD_ACCOUNT_SETTINGS` | `Settings$AccountDashboardActivity`; `.accounts.AddAccountSettings` | verify |
| Accounts > Sign-in options | PIN / fingerprint / face | link `SECURITY_SETTINGS`, `BIOMETRIC_ENROLL` | `Settings$SecurityDashboardActivity`; `.biometrics.BiometricEnrollActivity` | verify |
| Time & language > Date & time | 24-hour clock; set time automatically; date, time and zone | owned (`TIME_12_24`) + helper (04 ADD, `auto_time`) + link `DATE_SETTINGS` | `Settings$DateTimeSettingsActivity` | verify |
| Time & language > Language / Region | Android's pages | link `LOCALE_SETTINGS`, `REGIONAL_PREFERENCES_SETTINGS` | `Settings$LocalePickerActivity`; `Settings$RegionalPreferencesActivity` | verify |
| Time & language > Keyboard | phase 05's Keyboard page (owned, exists); manage keyboards | owned + link `INPUT_METHOD_SETTINGS` | `Settings$AvailableVirtualKeyboardActivity` | verify |
| Time & language > Speech | Tess's settings page (phase 03, owned, exists: voice, Notes slot, lock-screen options); assistant app | owned + link `VOICE_INPUT_SETTINGS` | `Settings$ManageAssistActivity` | verify |
| Ease of Access > Narrator / Magnifier / More options | Android's accessibility pages | link `ACCESSIBILITY_SETTINGS` | `Settings$AccessibilitySettingsActivity` | verify |
| Ease of Access > High contrast | Android's page | link `ACCESSIBILITY_COLOR_CONTRAST_SETTINGS` | `Settings$ColorContrastActivity` | verify |
| Ease of Access > Closed captions | Android's page | link `CAPTIONING_SETTINGS` | `Settings$CaptioningSettingsActivity` | verify |
| Ease of Access > Text size | Android's page (RV10: the shell does not follow it) | link `TEXT_READING_SETTINGS` | `Settings$TextReadingSettingsActivity` | verify |
| Privacy > Location | toggle; Android's page | helper (04 ADD, `location`) + read (`LocationManager.isLocationEnabled`) + link `LOCATION_SOURCE_SETTINGS` ~~(helper only per Q3)~~ | `Settings$LocationSettingsActivity` | verify |
| Privacy > Camera / Microphone / Contacts / Calendar / … | per-permission pages | link `PRIVACY_SETTINGS`; per app `APPLICATION_DETAILS_SETTINGS` with `package:` | `Settings$PrivacyDashboardActivity`; `.applications.InstalledAppDetails` | verify |
| Privacy > Notifications | apps with notification access | link `ACTION_NOTIFICATION_LISTENER_SETTINGS` | `Settings$NotificationAccessSettingsActivity` | verify |
| Update & security > Phone update | Android's update page | link `SYSTEM_UPDATE_SETTINGS` | **NONE** → fallback | verify (One UI has Software update) |
| Update & security > For developers | Developer options (phase 04's Wireless debugging row needs it) | link `APPLICATION_DEVELOPMENT_SETTINGS` | `Settings$DevelopmentSettingsActivity` | verify |
| Extras > Setup | the Setup checklist (owned, exists) | owned | — | — |
| Extras > Diagnostics | the Diagnostics page (owned, exists) | owned | — | — |
| Extras > Regulatory | Android's page | link `SHOW_REGULATORY_INFO` | **NONE** → fallback | verify |
| Apps > Apps & features | installed apps (the app list's data); per app: its Android page; Uninstall | owned (list; Uninstall through the app list's `REQUEST_DELETE_PACKAGES` path → the installer's dialog, the self package excluded by `AppUninstall.canUninstall`) + link `MANAGE_ALL_APPLICATIONS_SETTINGS`; per app `APPLICATION_DETAILS_SETTINGS` with `package:` | `Settings$ManageApplicationsActivity`; `.applications.InstalledAppDetails`; uninstall `com.android.packageinstaller/.UninstallerActivity` (2026-09-23) | verify |
| Apps > Default apps | default apps (MOVED here from System > Phone / Messaging) | link `MANAGE_DEFAULT_APPS_SETTINGS` | `com.android.permissioncontroller/.role.ui.DefaultAppListActivity` | verify |
| Apps > Apps for websites | apps' default links (MOVED here from System) | link `MANAGE_ALL_APPLICATIONS_SETTINGS` (per-app: `APP_OPEN_BY_DEFAULT_SETTINGS` with `package:`) | `Settings$ManageApplicationsActivity`; `.applications.InstalledAppOpenByDefaultActivity` | verify |
| Apps > App permissions | per app, its permissions (the Privacy pages' per-app link) | link `APPLICATION_DETAILS_SETTINGS` with `package:` | `.applications.InstalledAppDetails` | verify |

W10M pages with no row above are OUT (Scope). Where R11 §Settings shows a page this table lacks, or a
different category placement, the table follows R11 and the change is recorded here.

### Approximations (until R11 lands; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Category glyphs and the category page header | r11/settings-front.md pending (R3 C1 has the home page only) | R3 C1's row form with Fluent-icon glyphs; page header as the hub's `PageHeader` | H4 |
| Y2 | The fallback line ("Change this in Android settings") and its row form | no W10M original (P4) | a two-line row whose subtitle carries the line, accent-coloured title | H3 |
| Y3 | Helper-row state UI (the four ruled toggles and the five ADDs) | phase 04's Y4 / H7 | 04's own form reused | 04's H7 |
| Y4 | Page transitions and toggle motion | UNMEASURED; timed by the `[motion]` clock (C-5) | the hub's existing `PageTransition` (phase 01) | H5 |
| Y5 | Category → page mapping where W10M and Android disagree | agent call | the table above | H2 |
| Y6 | The Apps category's pages (no W10M original; desktop Windows 10's 2017 addition) | P4 design | the table's rows in R3 C1's form | H9 |

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
1. **Top level.** The W10M home (R3 C1 values), "Find a setting" (searches this front's page titles; empty
   line for no match), the category pages including Apps, `settings_cat:` / `settings_page:` tags, the fallback row form
   (Y2).
2. **Re-home.** The hub's pages under their categories with tags unchanged; Start + theme's rows split
   across Personalization > Start and Colors (H6), Transparency effects under Colors and phase 12's preset page as
   Personalization > Themes when those phases are built (T19-5); the launcher label "Settings" (Q1 A); Android's entry
   relabelled "Android settings" through a per-package label override in phase 01's app list, filed under A, search
   returning ours first (an ADD to phase 01's part, T19-3); INDEX Change Log entry for phase 01's part.
3. **Owned pages.** Display, Sounds, Lock screen, Date & time (24-hour), Do not disturb; the reads (battery,
   storage incl. phase 18's Recycle Bin size with Empty, data usage, NFC and location state); `WRITE_SETTINGS` and
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
5. **Deep-link rows.** One table-driven list (action, optional `package:` data, W10M title, subtitle);
   resolve at open, fall back to "Change this in Android settings" + Android's Settings home (Q4 A), diagnostics line per
   tap; the panels for Wi-Fi and NFC; the Apps category's links and Uninstall path (T19-2).
6. **"All settings"** from phase 04's action center opens this front (04's E4(d) target), coordinated with
   phase 04's build.
7. **Regressions.** Phase 01 E19 on a re-homed page; every phase 01 / 05 driver that opens a hub page by
   tag; the app-list regression (phase 02's regress.sh pattern); the exported allow-list (unchanged); APK size; phase 12
   E1 on this build (C-4 c).
8. **R11 values applied.** Once `r11/settings-front.md` lands, the Y rows it measures are replaced and E15 is written;
   FINAL only then (RV9).
9. **App Shortcuts** (phase 11 Q1's standing rule; C-8, T11-10). The static `shortcuts.xml` entries phase 11 declared for
   Settings — Start + theme / Tile apps / Setup / Diagnostics, ranks 0–3, targeting `SettingsActivity` with `EXTRA_PAGE`
   (`START_THEME`, `TILE_APPS`, `CHECKLIST`, `DIAGNOSTICS`) — kept as they are, each now landing on the re-homed page with
   Back going to its category. E17.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11 and take their clock
from the shell's `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`), a screenrecord
corroborating under phase 05's frame-spacing rule and never the primary clock (C-5); dumps follow RV13. A row that wipes
the app reads "`pm clear` → `provision.sh` → Home" (C-4 d), and phase 12's rule holds here: a finished or skipped wizard is
never re-summoned on that install — this phase's grants go red on the Setup checklist instead (C-4 e). Harness:
qa/phase-19/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh. Device: the AOSP AVD
tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google). Baseline values recorded on the AVD 2026-09-22:
`screen_brightness` 102, `screen_brightness_mode` 0, `accelerometer_rotation` 1, `screen_off_timeout`
2147483647, `zen_mode` 0, `navigation_mode` 0; and 2026-09-23 for the five helper ADDs: `low_power` 0, `location_mode` 3,
`auto_time` 1, no NFC service. Helper rows (E9) run only after phase 04's own rows pass.

**Emulator:**
- E1 **Structure (R3 C1).** The Settings home dump lists exactly eleven category rows in the table's order (`settings_cat:`
  tags: System, Devices, Network & wireless, Personalization, Accounts, Time & language, Ease of Access, Privacy, Update &
  security, Extras, Apps — Q2 C; r11/settings-front.md may reorder, recorded in the table); "Find a setting" measures
  31 ± 2 epx tall with 12-epx side margins; row pitch 64 ± 1 epx; glyph 30 epx at x = 12 epx; text at x = 55 epx; the
  header block 57 ± 2 epx (dump bounds and screencap, px ÷ 3 = epx); each category page lists exactly the table's pages for
  it that exist on this build (dump) — Apps lists Apps & features, Default apps, Apps for websites, App permissions, and
  System no longer lists Apps for websites or a default-apps link.
- E2 **Re-home (phase 01 re-run) and the app-list labels (T19-3).** Each hub page opened through its category still
  carries its tag (`settings_start_theme` under Personalization > Start, `settings_keyboard` under Time & language,
  `settings_checklist` and `settings_diagnostics` under Extras, `settings_about` under System, …) and its rows are unchanged
  (phase 01 E19's bar check re-run on one re-homed page: system bars hidden, drawn bars measured); `aapt dump
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
  `[settingsfront] write_settings=denied`; restore `appops set … allow`.
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
  `[settingsfront] link <action> -> <component>` is present. For the three **NONE** actions: the row shows the fallback
  subtitle "Change this in Android settings", a tap resumes `com.android.settings/.homepage.SettingsHomepageActivity`, and
  the line reads `-> none (fallback)`. Apps > Apps & features: Uninstall on a fixture app (`org.fossify.notes`) resumes
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
  get global auto_time` = 0, on → 1 (restore 1). **Unproved verb:** for any verb R4 could not prove, its row is the table's
  link and diagnostics say `[settingsfront] helper <verb>: not built (<R4 reason>)`. **Allow-list negative** (phase 04's
  method, its edge case "a verb outside the allow-list"): a string that is not a table verb (`battery_saver on; id`, and a
  bare `settings put global low_power 1`) is refused and logged by the helper, and `settings get global low_power` is
  unchanged. **Helper stopped** (04's method): every one of the nine rows shows 04's "helper not running, tap to start"
  state (04 interview item 4's default) and flips nothing (each observable above unchanged).
- E10 **Find a setting.** `adb shell input text bright` → the list shows Display; `wi` → Wi-Fi; `zzz` → the
  empty line; tapping a result opens that page (dump).
- E11 **"All settings" (phase 04 coordination).** Phase 04's E4(d) re-run with the front as the target: the
  wipe plays and `dumpsys activity activities` resumes `app.tileshell/.settings.SettingsActivity` on the home
  page.
- E12 **Diagnostics.** Lines asserted by the rows above: `[settingsfront] write_settings=<allowed|denied>`,
  `[settingsfront] dnd_access=<allowed|denied>`, `[settingsfront] link <action> -> <component|none (fallback)>`,
  `[settingsfront] set <key>=<value>`, `[settingsfront] search "<q>": <n>`, `[settingsfront] helper <verb> <on|off>: ok |
  unsupported | refused | helper not running`, `[settingsfront] helper <verb>: not built (<reason>)`, and every `[motion]
  <name> …` line a row times.
- E13 **App list, surface, budget.** The shell's entry "Settings" under S and "Android settings" under A (E2's
  assertions); phase 02's regress.sh pattern passes; `dumpsys package app.tileshell` exported components equal
  qa/phase-03/exported-allowlist.txt (no ADD in this phase); `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤
  629,145,600 bytes.
- E14 **RV10.** `adb shell settings put system font_scale 1.3` → every measured value in E1 is unchanged
  (dump); restore `font_scale 1.0`.
- E15 **Geometry and motion against `r11/settings-front.md`** — written once that section lands (Y4's motion through the
  `[motion]` clock, C-5); not runnable before, and the doc cannot go FINAL without it.
- E16 **Wizard steps added (phase 12 E14's template; C-4 b).** `pm clear app.tileshell` → `provision.sh` → `adb shell appops
  set app.tileshell WRITE_SETTINGS default` and `cmd notification disallow_dnd app.tileshell` + Home →
  `wizard_step:setup:write_settings` and `wizard_step:setup:dnd_access` present, each with its `wizard_why` text equal to
  Decisions' line, their actions starting `Settings$WriteSettingsActivity` and `Settings$ZenAccessSettingsActivity`; grant
  both from adb + Home → both absent and the wizard not shown; then phase 12 E1 re-run on this build (C-4 c).
- E17 **App Shortcuts (C-8, T11-10).** The Settings tile (`shell:settings`, on the baseline layout): hold → `quick_sat_label:
  0..3` = "Start + theme", "Tile apps", "Setup", "Diagnostics" in rank order (phase 11 E3's method); tap each → `SettingsActivity`
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
  get global auto_time`); each both directions with restore; each unproved verb's row is its link.
- P4 Samsung's Side key page (no public action): the fallback row, recorded. RECORDED.
- P5 Screen zoom and font size on One UI leave the front unchanged (RV10, phase 01 P-rows' method).
- P6 The 24-hour toggle against One UI's own clock formats (the shell's status bar follows it; Samsung's
  shade is its own).

**NEEDS-HUMAN:** H1 *fidelity* — the front matches r11/settings-front.md on the phone; H2 *accept* — the category →
page mapping (Y5) where W10M and Android disagree; H3 *accept* — the fallback row wording and form (Y2, P4);
H4 *accept* — any Y1 value R11 does not measure; H5 *accept* — page and toggle motion (Y4); H6 *accept* — the
Start + theme split into Start and Colors, Transparency effects under Colors and the presets as Themes (T19-5); H7 *accept*
— the app list with "Settings" and "Android settings" as it looks on the phone (Q1 A); ~~the app-list result of Q1~~
SUPERSEDED 2026-09-23 by the ruled form (T19-10); H8 *accept* — the Settings tile's four satellites stay Start + theme /
Tile apps / Setup / Diagnostics, or Jeremy swaps them for categories (T11-10); H9 *accept* — the Apps category's pages
(Y6; a category with no W10M original, P4).

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
