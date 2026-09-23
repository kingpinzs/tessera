# R11 — Calendar (W10M Outlook Calendar) measurements

Section of R11 (docs/plan/r11-inbox-apps.md) for phase 16 (docs/plan/phase-16-inbox-calendar-people.md). Format and
confidence rules follow docs/plan/r8-groove-measurements.md. Cited, not re-measured: R3 C3 (Calendar tile face), R7
§2.1.14 / §2.1.15 (app bar and its expanded menu), settings-front.md §6 (page-entrance motion on the same app platform).

## Summary

Outlook Calendar on W10M is **Agenda / Day / Week plus a month drop-down**. The month-and-year header ("NOVEMBER 2015 ⌄")
opens a month grid over the page. Under the header, a **week strip on a W/7 grid** sits above an **agenda of day groups**.
Each event row is an **8-epx colour bar** flush with the left screen edge, then a time or "All day" label tinted with the
calendar's colour, then the title in white at **x = 92.5 epx**. Every calendar has one colour, and the app uses it in
three places: the event bar, the tint of the event's label text, and the fill of the calendar's checkbox in the ≡ calendar
list. The ≡ list groups calendars under their accounts and shows or hides them by checkbox. The app has no "calendar
colour picker" of its own.

Measured at native resolution: the agenda, week view and month drop-down (GSMArena Lumia 950, 400 %, Dec 2015, ≈ 10586),
and the ≡ calendar list and a light-theme agenda (Windows Central, 720x1280 native, Sep 2015, pre-10586). Measured on the
governing era at low resolution: the agenda, app bar and overflow menu from AAWP's Jul-2017 screenshot (450-wide downscale
of a 432-epx-wide 15063-era phone). Two values are confirmed as **fixed epx across two canvases** (360 and 432 epx): the
event title x (92.5 / 93.1) and the label x (24.3 / 25.9). The week strip is confirmed **proportional (W/7)**.

**UNMEASURED**: the day view, event details, the new / edit event editor, the reminder toast, Calendar settings and all
motion. No capture of any of them was reached on any build; the Lumia user guide gives their wording only. This is the
biggest gap for phase 16.

---

## 0. Sources, calibration

| ID | Source | Date / build | Capture / canvas | Ruler |
|---|---|---|---|---|
| C1 | GSMArena Lumia 950 review, https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_083.jpg (agenda) and gsmarena_085.jpg (month drop-down open) | Dec 2015, ≈ 10586; Mail and Calendar ≥ 17.6506 (it shows the "portrait week view" that version added, WC 2015-12-08) | native 1440x2560, 400 % → 360 epx | **1 px = 0.25 epx** |
| C2 | GSMArena Lumia 950 XL review, https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_311.jpg (week view) | same session as C1 (status clock 18:04 on all three), so a **950** screenshot posted in the XL review | native 1440x2560, 400 % | 1 px = 0.25 epx (header lands on the same epx as C1) |
| A17 | AAWP, "Microsoft To-Do UWP now integrated with Outlook Calendar", http://allaboutwindowsphone.com/flow/item/22416_Microsoft_To-Do_UWP_now_integr.php, image https://allaboutwindowsphone.com/images/flow/misc/outlook-todo1.jpg (+ outlook-todo2.jpg) | article Aug 2017, screen "JULY 2017"; **15063 era** | 450x800 downscale; the nav bar is 50 px tall → **432x768-epx canvas** (1080p at 250 %, the author's Alcatel Idol 4 class phone) | **1 px = 0.96 epx**; LOW for sizes (JPEG bloom ±1 px) |
| A15 | AAWP, "Week view returns… to Windows 10 Mobile (Outlook) Calendar", http://allaboutwindowsphone.com/flow/item/21026_Week_view_returns_to_Windows_1.php, images https://allaboutwindowsphone.com/images/flow/misc/weekview1.jpg and https://allaboutwindowsphone.com/images/flow/misc/weekview2.jpg (hash-verified against fresh downloads) | 2015-10-22, pre-10586 Insider; app **before** the Dec-2015 portrait week view | 450x800; the ≡ + title land on C1's exact epx at 360-epx scaling → **1 px = 0.8 epx** | LOW |
| WCS | Windows Central, "How to select which calendars appear on Outlook for Windows 10 Mobile" (2015-09-29), https://cdn.mos.cms.futurecdn.net/4LNJdM9aDBRVykixpgqVPV.jpg: two native 720x1280 screenshots side by side (x 0–719 agenda + month, x 740–1459 ≡ calendar list) | 2015-09, pre-10586, **light theme** | native 720x1280, 200 % → 360 epx | **1 px = 0.5 epx** |
| T16 | AAWP, "Outlook Calendar live tile now shows up to 3 events", http://allaboutwindowsphone.com/flow/item/21581_Outlook_Calendar_live_tile_now.php, image https://allaboutwindowsphone.com/images/flow/misc/wp_ss_20160720_0005.jpg | 2016-07-20 | 800x620 crop of Start | tile only |
| WCW | Windows Central, "Outlook Mail and Calendar snags week view for mobile devices and more" (2015-12-08): 6430 → 6506 "Added portrait week view for mobile devices; Added context menu quick actions for calendar events on mobile devices" | 2015-12 | document | — |
| D-UG | Microsoft "User Guide — Lumia with Windows 10 Mobile" Issue 1.1, Calendar section (`ug.txt` l.1702–1780) | 10586-era | document | — |

Local copies (gitignored, docs/plan/r11/src/calendar/, unaltered):

| File | Resolution | Source | sha256 |
|---|---|---|---|
| gsmarena_950_083.jpg | 1440x2560 | C1 083 (agenda) | 0319686176a03fcdac229e66e2f860a230c7a0b8a58e397542ef34976a195dcc |
| gsmarena_950_085.jpg | 1440x2560 | C1 085 (month drop-down) | f1cbea9ded941f36308db9d2ff46d01db1ecc8bc71c7922ef513917cadcdbf8b |
| gsmarena_950xl-review_311.jpg | 1440x2560 | C2 (week view) | 7054d085548a639a67c3765e14c7723702fb2be79f966edcd376f66f683d7f88 |
| aawp_outlook-todo1.jpg | 450x800 | A17 (agenda + overflow menu, 15063 era) | 686f2a3a25b510eff52a82d4ded364e7e597a0a582d50d0f50179ca7c51c701a |
| aawp_outlook-todo2.jpg | 450x800 | A17 (To-Do app, context only) | 63eb7cb8aec57a989de51e4f80c612d203a4b869dd1de01d7d2310ce7b28ad9c |
| aawp_weekview1.jpg | 450x800 | A15 (2015 week view) | 154c700816b1a442b6b50ac547e029f0c7b6b8e17fa7d242cbaa009c04563a55 |
| aawp_weekview2.jpg | 450x800 | A15 (2015 View menu open) | ce23f7aefdae3172d0a0b392b1643d91ddbef1231a723ddf2791f02e6a140337 |
| wc_4LNJdM9aDBRVykixpgqVPV.jpg | 1460x1280 | WCS | 901d9d9d7faecf8ac18761d562fb3ae94e1e1d864cbce610bfd438c4fa78eaec |
| aawp_wp_ss_20160720_0005.jpg | 800x620 | T16 | d9fda4919e49d22c2a9182921b3e6ee412b718fcffe32259fc9b4f826906fe01 |

C1/C2 were re-hashed against fresh downloads (identical). Method: band detection and half-level crossings in epx (session
scratchpad `r11cal/scripts/cal1.py`–`cal3.py`, `mm.py`).

Searched without finding a Calendar capture of the day view, event page or editor on any build: yt-dlp searches
"Windows 10 Mobile Outlook Calendar app", "… calendar app 2017", "… Creators Update Outlook calendar", "Lumia 950 calendar
app", "… calendar week view" (logs `r11cal/search/cal1.txt`, `q3`–`q9`). Candidates bNlCMSyvDVQ (Old Guy Geek, "The New
Outlook A-Z", 908 s), eNTFG3EroMI, dD-iXJCmWdk ("Outlook gets dark theme", Windows Central) and hoGA4p4WEPQ (On MSFT,
"Calendar reminders when shutting off your phone": the reminder toast) all returned "Sign in to confirm you're not a
bot" on every player client. The Microsoft Store listing (displaycatalog API and the 2016–2017 Wayback snapshots) carries
desktop screenshots only.

Confidence: **HIGH** = two independent sources or canvases agree; **MEDIUM** = one native screenshot; **LOW** = a 450-wide
downscale, camera, or a non-governing build only; **UNMEASURED**.

---

## 1. Header, page and chrome

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K1.1 | Header | **≡** (three 1-epx bars at a 5-epx pitch, x **16–36**, centre y ≈ 44), then the month and year in **ALL CAPS semibold** ("NOVEMBER 2015"), cap top **39.5 epx**, cap **11.0** (15-epx class), left **51.0**, then a **⌄** chevron (**⌃** while the month drop-down is open) | HIGH | C1 083, C1 085, C2 311 identical; A15 2015 (≡ 16–36, cap 40.0–51.2) |
| K1.2 | Header band | from the status bar (24) to **64 epx** (40 epx); a 1-epx black rule under it in week view | MEDIUM | C2 x=90 column: rule at 64.0 |
| K1.3 | Page background (dark theme) | **#1A1A1A** (26,26,26) native. The page is dark grey, not black, while the status bar and nav bar are black | HIGH | C1 083/085, C2 samples |
| K1.4 | Rules | day groups and the strip bottom are divided by **1-epx black (0,0,0)** rules; week-view cells by **1-epx (80,80,80)** rules | HIGH | C1 083 rules at 132, 173, 278, 370; C2 185 / 305 / 425 and x 179–180 |
| K1.5 | App bar | **47 epx** (546–592) fill **(33,33,33)** with a 1-epx (80,80,80) top edge; buttons **Today · New (+) · View · …** | HIGH (form) | C1 083 / 085, C2; A17 labels "Today", "New", "View" |
| K1.6 | App bar button positions | 10586 (360 canvas): glyph centres 162.0 / 229.5 / 287.5 / 336.0 (from the right edge 198 / 130.5 / 72.5 / 24). 15063 era (432 canvas): from the right edge **217.9 / 150.2 / 82.6 / 24.5**, i.e. the standard 68-epx CommandBar (R7 §2.1.14's 217.5 / 147.5 / 80.5 / 23.5). The two builds differ | MEDIUM / LOW | C1 083 columns; A17 columns |
| K1.7 | Light theme (pre-10586) | header area accent-filled with white text; page white | LOW | WCS (left half) |

## 2. Week strip (top of Agenda)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K2.1 | Columns | **W/7**: 51.4 epx on 360 (centres 25.0, 76.6, 127.5, 178.4, 229.0, 280.4, 331.3), 61.7 on 432 (30.2 … 396.0); **proportional to width**, not fixed epx | HIGH | C1 083; A17 |
| K2.2 | Day names | "Mon … Sun" (locale's first day: Monday here, Sunday in WCS's US locale), grey **(151,151,151)**, cap top **75.3 epx**, ≈ 15-epx class, centred in the column | HIGH (C1 + A17 agree on form) | C1 083 row 75.25–85.25 |
| K2.3 | Dates | white, centred in the column, row centre ≈ **111 epx** (10586) | MEDIUM | C1 083 |
| K2.4 | Rows shown (collapsed strip) | **one week row** on 10586 (strip ends at the black rule at **132 epx**); **two rows** (current week + the next, the second dimmed) on the 15063-era app, strip ending at ≈ 188 epx | MEDIUM (difference) | C1 083; A17 rule at 187.2–190 |
| K2.5 | Selected day | 10586: the whole cell (**W/7 x 34 epx**, x 204.0–255.0, y 93.0–127.0) filled with dark accent **(20,76,123)**, with a lighter accent disc ≈ 32 epx behind the number. 15063 era: a **32 x 32-epx accent square** centred on the number (x 380.2–412.8, y 105.6–137.3), no full cell | MEDIUM / LOW (difference) | C1 083; A17 |
| K2.6 | Event dots | small accent dots under dates that have events (A17 rows 117 / 158) | LOW | A17 |

## 3. Agenda

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K3.1 | Structure | a scrolling list of **day groups**, each a day heading, then its events; the week strip stays pinned above it | HIGH | C1 083, A17, WCS |
| K3.2 | Day heading | "**Saturday 12**" (weekday + day number), white **semibold**, ≈ 15-epx class (ink 294.0–309.0 incl. the "y" descender), left **24 epx** | MEDIUM | C1 083 |
| K3.3 | Today's heading | "**Wednesday 2**" in **accent** (35,139,221), larger: ≈ **20-epx** class (ink 191.25–211.75), left 24 | MEDIUM | C1 083 |
| K3.4 | Empty day | "**No events today**" in grey **(151,151,151)**, ≈ 20-epx class, left 24 | MEDIUM | C1 083 |
| K3.5 | Event row, all-day | a **40-epx** colour bar, rows at a **44-epx** pitch (4-epx gap between bars); "**All day**" at x **24.3** in the calendar colour's light tint; title at x **92.5**, white, cap/ascender 13.5 (≈ 20-epx class), one line | MEDIUM | C1 083 bars 410–450, 454–494, 498–538; labels 426.0 / 469.75 / 513.75; titles 424.25 / 468.25 / 512.25 |
| K3.6 | Event row, timed | a **56-epx** bar; start and end times stacked in two lines ("13:00" / "16:00"; "6:00 PM" / "6:00 PM"), 16.3-epx line pitch, at x **25.9**, tinted; title at x **93.1**, white | LOW (A17 450-wide) / MEDIUM (WCS native, pre-10586) | A17 bars 323.5–379.2, 383.0–438.7; WCS 319.5–375.5 |
| K3.7 | Title and label x, fixed or proportional | title **92.5 (360) / 93.1 (432)**, label 24.3 / 25.9: the same epx on two canvases, so **fixed epx** | HIGH | C1 083 + A17 |
| K3.8 | Colour bar | **8 epx wide, flush with the screen's left edge (x 0–8)**. Either **solid** in the calendar colour, or a **2-epx outline** with the page colour inside (seen on the all-day holidays and "Ride a bike"; "Pay rent" and the 2017 timed events are solid). The outline most likely marks Free / tentative events (INFERRED: the free/busy state is not visible) | MEDIUM (form) / INFERRED (meaning) | C1 083 x 0–2 / 2–6 / 6–8 at y 430; WCS y 347 (outline) vs y 563 (solid); A17 solid 0–7.7 |
| K3.9 | Calendar colours seen | red **(168,0,0)**, blue **(0,120,215)**, orange **(215,59,2)**; label tints **(245,72,85)**, **(48,163,250)**, **(254,116,87)**; A17 yellow (250,191,29) | HIGH (values) | C1 083 samples |

## 4. Week view (10586 "portrait week view")

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K4.1 | Grid | **2 columns** split at W/2 (1-epx rule at x 179–180 of 360) x **4 rows of 120 epx** from 65 to 545 epx: Mon / Tue, Wed / Thu, Fri / Sat, Sun / **mini month** | MEDIUM | C2 311 |
| K4.2 | Cell label | "**23 MON**" (day number + caps weekday), grey (151), cap 13 (≈ 19–20-epx class), top 11 epx under the cell's top rule, left **10 epx** inside the cell (x 10 / 190) | MEDIUM | C2 311 rows 76.25, 197.25, 317.25, 437.25 |
| K4.3 | Events in a cell | one line per event, in the calendar colour's **tint** (no bar), **19-epx** line pitch, the first line 29 epx under the label | MEDIUM | C2 311 ("Thanksgiving Day" 226.5 / 245.5) |
| K4.4 | Mini month | in the eighth cell: a 7-column month with a "Mo Tu We Th Fr Sa Su" header, **14.1-epx** rows, ≈ 10-epx digits, left x 190 | MEDIUM | C2 311 rows 453.75 … 524.0 |
| K4.5 | 2015 week view (older) | 2 columns of short cells (≈ 66 epx) with "No events" and a daily agenda below. It was replaced by K4.1 in version 6506 (WCW) | LOW | A15 |

## 5. Month (header drop-down)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K5.1 | Form | tapping the header opens a month panel **over** the current view (the chevron flips to ⌃): x **5–355**, y **64–299**, 1-epx (80,80,80) border, page-colour fill | MEDIUM | C1 085 |
| K5.2 | Grid | day-name row (grey, cap top 76.25) + **6 date rows at a 34.25-epx pitch** (106.25, 140.5, 174.5 …), W/7 columns shared with the strip | MEDIUM | C1 085 |
| K5.3 | Date colours | in-month white (254); other-month grey **(110,110,110)**; selected date as K2.5 (dark accent cell) | MEDIUM | C1 085 |
| K5.4 | Light theme (pre-10586) | the month panel expands in place under an accent header (Sun-first, US) with today as an accent disc | LOW | WCS |

## 6. View menu, overflow menu, calendar list

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K6.1 | Views | **Agenda**, **Day**, **Week** from the View button; the month from the header. D-UG: "To see your plan for the day hour by hour … the whole week … only the days with events … To go back to today" | MEDIUM | A15 weekview2 menu; D-UG l.1756–1762 |
| K6.2 | View menu form | the app bar expands upward into a list: rows **44.8 epx**, 18-epx glyph at x 23.2, text at x 60 | LOW | A15 weekview2 rows 472.8 / 517.6 / 562.4 |
| K6.3 | Overflow ("…") menu | 15063 era: **Print · Manage accounts · To-Do · Settings**, each with an 18-epx glyph at x 23, text at x **59.5**, rows **45.8 epx**, panel fill (70,70,70); 10586-era camera photo: "Manage accounts · Settings · Feedback" | LOW | A17 rows 491.5 / 537.6 / 582.7 / 628.8; Windows Latest 2017-08 photo |
| K6.4 | ≡ calendar list | a pane from the ≡ button, **accent-filled** in the light theme, full width. Calendars are grouped under account headers with a **⌃** collapse chevron ("^ Gmail", "^ MONA") at x **14**. Each calendar is a row with a checkbox and its name at x **62**. Rows **48.1 epx**. A **checked checkbox is filled with the calendar's colour** (yellow "mark guim", magenta "MoNa Events"). WC: "You can also choose to include or exclude reminders" | MEDIUM (native, pre-10586) | WCS right half: label tops 130.5 … 611.5 over ten pitches |
| K6.5 | Birthday calendar | listed like any calendar ("Birthday calendar", "Birthdays" per account). D-UG: "The birthday calendar compiles the birthday information from your social networking accounts. You can't add new calendars to your phone, but you can choose which calendars from your different accounts you want to see." | MEDIUM | WCS; D-UG l.1741–1745 |

## 7. Tile (for phase 01, cited)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| K7.1 | Wide tile agenda face | "Today" + up to **3 events** as time / title pairs ("16:30 squash at JBH", "18:00 meet Fiona", "19:00 party at Jim's"), label "Outlook Calendar" bottom-left (AAWP: "now shows up to 3 events (up from 1!)", 2016-07) | LOW | T16 |
| K7.2 | Medium day face | R3 C3 (day name + 30-epx number) | cited | R3 |

## 8. UNMEASURED, with proposed tagged approximations

| # | What | Proposed approximation (derived from) |
|---|---|---|
| U1 | **Day view** (hour grid) | the week-view cell label (K4.2) as the day header; hour rows at the settings-front.md 3.3 **48-epx** row, with event blocks as K3.8 bars widened to the column; [accept] H-row |
| U2 | **Event details page** | card-style: title in 20-epx Subtitle at x 12, then time, location, calendar (with its colour swatch = K3.8 bar), reminder and notes rows in the people.md P3.6 two-line action form (label + value, 65.5-epx pitch) |
| U3 | **New / edit event editor** | the people.md editor form: accent type-labels + 32-epx outlined fields (P4.3–P4.5), 1-epx group rules (P4.7), combo boxes for Repeat / Reminder / Calendar / Status as settings-front.md 4.7, date and time fields side by side as R7 §3.7.3; app bar Save / Delete / … |
| U4 | Edit "this occurrence / this and following / all" prompt | a W10M content dialog (R7 §1.3.9 top-anchored dialog) with three buttons |
| U5 | **Reminder toast / notification** | the Action Center notification item (R3 A19: 48-epx circle, title / preview / time lines) with the Calendar glyph |
| U6 | Calendar Settings page | settings-front.md leaf page (4.1–4.7); D-UG items: "Calendar Settings", week numbers menu, alternate calendars "Enable" checkbox + language / calendar combo |
| U7 | First day of week wording | K2.2 shows W10M followed the locale; the setting's wording is UNMEASURED |
| U8 | All motion (view switch, month drop-down open / close, day paging, event open) | the drop-down as R7 §2.2.6 (grows from its top edge, 200 ms ease-out); page changes as settings-front.md 6.2–6.3 (cut, ≈ 10-epx slide-up + fade, 250–300 ms); day paging as phase 01 X13 |
| U9 | 3-day view | not present in any source; W10M's views were Agenda / Day / Week (K6.1). Do not add one |
| U10 | Dark-theme ≡ calendar list and the governing-build list | WCS is light theme, pre-10586: in dark theme, use the #1F1F1F chrome (settings-front.md 2.1) with the same geometry |

## 9. Segoe MDL2 glyphs (2017 MDL2 list, windows-dev-docs d6050b7; visual match: MEDIUM)

Header: GlobalNavigationButton E700 (≡), ChevronDown E70D / ChevronUp E70E. App bar: Today = a calendar-with-arrow glyph
(CalendarReply E8F5 is the nearest listed name; no "GoToToday" in the 2017 list), New = Add E710, View = CalendarWeek E8C0
(Agenda = BulletedList E8FD, Day = CalendarDay E8BF, Week = CalendarWeek E8C0), More E712. Overflow: Print E749, Manage
accounts = Contact E77B, To-Do = CheckMark E73E / Accept E8FB, Settings E713. Calendar list: Checkbox E739 /
CheckboxComposite E73A. App / tile: Calendar E787. Reminder: Reminder EB50 (ReminderFill EB4F). Licence: R1 §10.

## 10. Strings as shipped

Header month "NOVEMBER 2015" style (month name + year, caps); day names "Mon … Sun"; agenda "Wednesday 2" / "Saturday 12"
(weekday + day number), "No events today", "All day", times "13:00" / "16:00" or "6:00 PM"; week view "23 MON"; app bar
"Today", "New", "View"; View menu "Agenda", "Day", "Week"; overflow (15063 era) "Print", "Manage accounts", "To-Do",
"Settings" (10586: "Manage accounts", "Settings", "Feedback"); calendar list "Birthday calendar" / "Birthdays", account
names as group headers. D-UG: "Repeat", "Private", "Calendar Settings", "Accounts", "Add account", "Edit". The editor
labels themselves are UNMEASURED (U3).

## 11. Gaps for the phase doc (phase-16-inbox-calendar-people.md)

- **Views.** W10M had Agenda, Day and Week, plus the month as a header drop-down (K6.1, K5). Build task 4 and the App
  Shortcuts Decision (Agenda, Day, Month, New event) should read "Month" as the drop-down state of any view, or accept a
  standalone Month page as a P4 design. There was no 3-day view (U9).
- **Calendar colours (Q2 / Q3 context).** W10M showed multiple calendars by colour only: the bar colour, the tinted label
  and the coloured checkbox in the ≡ list (K3.8, K3.9, K6.4). It had no picker. For the local calendar (H4), the Birthdays
  calendar (H5) and the "Can sync to" list (Q2 rule 4), the colour appears in these three places. The Sync picker and the
  "synced to <calendar>" mark are P4 designs with no W10M source. The natural W10M form for "Can sync to" is a checkbox list
  styled like K6.4.
- **The ≡ calendar list is the W10M place for show / hide per calendar** (K6.4). The phase's calendar list (`cal_calendar_row:`)
  should be that pane, with account group headers.
- The first day of the week followed the locale (K2.2): this matches the phase's `WeekFields.of(locale)` default. The setting's
  wording is unsourced (H6 stays [accept]).
- **Event bars**: 8 epx at x 0, solid vs outlined (K3.8). The phase has no row form yet; E19 should assert the 40 / 56-epx
  rows, the 44-epx all-day pitch and the title at x 92.5.
- Selected day, strip rows and app-bar spacing **differ between 10586 and the 15063-era app** (K2.4, K2.5, K1.6). The later
  form is the governing one but was only seen at 450-wide resolution (LOW). Use the 15063 form with an H1 [fidelity] note.
- Day view, event page, editor, reminder toast, settings: UNMEASURED (U1–U6). H1 cannot close against footage for them. The
  editor and event page need [accept] rows (H13), not [fidelity].
- Background: W10M Calendar's page is **#1A1A1A**, not black (K1.3), unlike People and Settings leaf pages.

## Tally

Each row in §1–§7 is counted once; a row that gives two levels counts at the lower one. K7.2 (cited) is not counted, and
the §8 rows are all UNMEASURED.

- **HIGH 9**: K1.1, K1.3, K1.4, K1.5, K2.1, K2.2, K3.1, K3.7, K3.9.
- **MEDIUM 18**: K1.2, K2.3, K2.4, K3.2, K3.3, K3.4, K3.5, K3.8, K4.1, K4.2, K4.3, K4.4, K5.1, K5.2, K5.3, K6.1, K6.4,
  K6.5.
- **LOW 10**: K1.6, K1.7, K2.5, K2.6, K3.6, K4.5, K5.4, K6.2, K6.3, K7.1.
- **UNMEASURED 10**: U1–U10.

Biggest gap: the day view, event details and new / edit event editor (U1–U3) have no capture on any build. Next is the
governing-build agenda, seen only at 450-wide resolution (A17).
