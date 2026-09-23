# R11 — People (W10M People app) measurements

Section of R11 (docs/plan/r11-inbox-apps.md) for phase 16 (docs/plan/phase-16-inbox-calendar-people.md). Format and
confidence rules follow docs/plan/r8-groove-measurements.md. Cited, not re-measured: R3 A9 / C3 (People tile), R7 §1.3
(Phone history row, the phase's current stand-in), R7 §2.1.1 (pivot-style caps header), R7 §3.5.8 / §2.1.14 (app bar),
R3 C2 and phase 01 X8 (app-list jump grid).

## Summary

The People app's contact list, jump grid, contact card and contact editor are measured at native resolution on **two
devices at two W10M scale factors**: GSMArena's Lumia 950 (400 %, 360-epx canvas) and 950 XL (350 %, 411 epx)
screenshots, Dec 2015. Every value that is fixed in epx lands on the same epx on both, so the numbers below are confirmed
rather than assumed. The list is not Phone's History row: it is a **50-epx row with a 32-epx avatar**, under large accent
letter headers. The jump grid is a **72-epx cell grid whose column count follows screen width** (4 columns on 360 epx, 5
on 411). The contact card is a **full accent-coloured page** with a 124-epx circular photo. The editor is a flat list of
outlined 32-epx fields and "+ field" rows, divided by 1-epx rules.

What could not be established: **the governing build.** Every native People screenshot found is 10586-era, and no
14393 / 15063 / 15254 capture of the People app was reached. The YouTube candidates are listed in §0.3; extraction was
blocked by the time they were found. The only later evidence is a Feb-2017 Microsoft marketing composite (Windows Latest)
that shows the same accent contact card with "Profile / What's New" pivots (LOW, structure only). Link / unlink, SIM
import, the filter page and every motion are UNMEASURED or LOW. One related measurement *is* on the governing era: the
14393 **app-list** jump grid (S1, 60 fps). It differs from People's 10586 grid, with 64-epx cells, 4 columns, white
letters, and an overlay over the dimmed list (§3).

---

## 0. Sources, calibration

### 0.1 Sources

| ID | Source | Date / build | Capture / canvas | Ruler |
|---|---|---|---|---|
| G1 | GSMArena Lumia 950 review screenshots, https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_0NN.jpg — 024 list, 025 jump grid, 026 contact card, 027 edit contact, 028 What's new | review Dec 2015 (R8 G1–G2 are the same set); **≈10586** by date | native 1440x2560, 400 % → 360x640 epx | **1 px = 0.25 epx** |
| G2 | GSMArena Lumia 950 XL review screenshots, https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_0NN.jpg — 027 list, 028 jump grid, 031 What's new, 032 contact card, 033 edit contact | Dec 2015; ≈10586 | native 1440x2560, **350 %** → 411.43x731.43 epx | **1 px = 0.2857 epx** |
| S1 | https://www.youtube.com/watch?v=I98ENfXJRqA (Windows Central 14393 official demo), app-list jump grid at 429.4–430.6 s | 2016-08-16, **14393** | PMS 1920x1080 @ 60 fps; screen x 706.52–1210.72, top 64.4 (settings-front.md §0) | **1 px = 0.7140 epx** |
| WL | https://www.windowslatest.com/wp-content/uploads/2017/02/Microsoft-People-app-for-windows-10-mobile-1.jpg (in the 2017-07-14 article on People 10.2.1921.0) | 2017-02 upload | Microsoft marketing composite, 635x371 | structure only |
| WC | Windows Central, "6 ways to improve your experience with the People app on Windows 10 Mobile" (2015-12-03), images https://cdn.mos.cms.futurecdn.net/<id>.jpg (k2inCYKtCRXXmekv9kgq64 filter page, of7ME6cxV5kd69cqSvpjKU Groups pivot, Tb56RMQc3yFxDCRwuJgRuR "Choose an account") | 10586 | camera photos, oblique | structure and strings only |
| D-UG | Microsoft "User Guide — Lumia with Windows 10 Mobile" Issue 1.1, People chapter (R6 D1; local `ug.txt` l.2155–2350) | 10586-era | document | — |

Every G1 / G2 file was re-hashed against a fresh download of its URL (identical sha256 prefixes, e.g. gsmarena_024
a60a923d…, XL gsmarena_027 845ff9a7…). The G1 and G2 sets are independent sessions (G1 status clock 17:37–17:38, G2
12:25–12:27, magenta vs blue accent).

### 0.2 Local copies (gitignored, docs/plan/r11/src/people/; unaltered; the PNGs are full decoded video frames)

| File | Resolution | Source | sha256 |
|---|---|---|---|
| gsmarena_950_024.jpg | 1440x2560 | G1 024 (list) | a60a923db5f204300646f390188277522dbbe207e68bb14407fd8f69aac84eb1 |
| gsmarena_950_025.jpg | 1440x2560 | G1 025 (jump grid) | f1bff72b3af572d7f6b1fb319237d7093eccb196967b7685eae9d1383ac87bff |
| gsmarena_950_026.jpg | 1440x2560 | G1 026 (card) | 67bfa5bb56d8c11d02c2ca5f9f724327571e0f960cdb8343479ebfbc7e5f81ea |
| gsmarena_950_027.jpg | 1440x2560 | G1 027 (edit) | fca2881b956d39020c99caebefcb44ff945cb2f0fa4766b7f439831ac00621ef |
| gsmarena_950_028.jpg | 1440x2560 | G1 028 (What's new) | a565799238600cbc46050b75546e3212a64ae4b944e8b1389de0f857af36a8e2 |
| gsmarena_950xl_027.jpg | 1440x2560 | G2 027 (list) | 845ff9a77b0ddb8cbd2b6bb1e1569ecf14af9aeceedc1c9bf901bbe0a786580d |
| gsmarena_950xl_028.jpg | 1440x2560 | G2 028 (jump grid) | 633d36954235013cd5a1e576591805c12341793c624cc2baba43d7663c03c13a |
| gsmarena_950xl_031.jpg | 1440x2560 | G2 031 (What's new) | 274f5c83b98643d79476d69bd280122101caeed47efdf61cee824118a41d0325 |
| gsmarena_950xl_032.jpg | 1440x2560 | G2 032 (card) | 8fce9c5fd4274b4dcfe376d509421a0509cafb81464cab93bf9e9d2925565847 |
| gsmarena_950xl_033.jpg | 1440x2560 | G2 033 (edit) | c30f02438e814ecc1ca01bbf092b43b42a792f80b219ea557ef80bccdc6c1873 |
| I98ENfXJRqA_t429.483.png | 1920x1080 | S1 (header tapped) | dcb0b2a77a0ae926700ab686941b35610d0f3f035699f2394d811febba5b2060 |
| I98ENfXJRqA_t429.533.png | 1920x1080 | S1 (grid first frame) | 953f8ef353f49aad1103de2eb3e62114a7f95b0d3a00c9b748371a621fae4be0 |
| I98ENfXJRqA_t429.617.png | 1920x1080 | S1 (grid mid-open) | df52c6f74666d85c416646c630b778d1a1612fb1e1b3910edbb0cc49cb1cc194 |
| I98ENfXJRqA_t429.817.png | 1920x1080 | S1 (grid settled) | 5e8fac90ef84120f07cebb5fe458dc37f14f71aa17ec232f2b615f4e825b3b9d |
| I98ENfXJRqA_t430.583.png | 1920x1080 | S1 (grid closed, list at "S") | cb654b5a7a0a0b980a8f7b863a3c1af37dab79fcdf1b3625bff484834d12400f |
| windowslatest_people_2017-02.jpg | 635x371 | WL | edc11f9c52b749ca0e6bf6b1d5d27ee64de8461471fd36737109f89d29f91fe7 |
| wc_k2inCYKtCRXXmekv9kgq64.jpg | 2048x1365 | WC filter page | 5715d35760dcb3148e5652ac4fd56aa22ea750142d9005218e7567b1ccf48b3a |
| wc_of7ME6cxV5kd69cqSvpjKU.jpg | 2048x1365 | WC Groups pivot | b80411d430a1519a5f281a3adc6c50d8e1b2a5af2364e5f1f149b16d7e8911ff |
| wc_Tb56RMQc3yFxDCRwuJgRuR.jpg | 2048x1365 | WC "Choose an account" | 6d029268cb7877c3686feb193e03863383286354626aa9303eba824610c6fdf3 |

Method: band detection and half-level crossings in epx (Pillow + numpy; session scratchpad `r11cal/scripts/ppl_list.py`,
`ppl_card.py`, `ppl_edit.py`, `jump.py`, `scalefit.py`). Native JPEG edges are ±1 px = ±0.25 / ±0.29 epx.

### 0.3 Searched without a governing-build People capture

yt-dlp search (logs `r11cal/search/q2.txt`–`q11.txt`): "Windows 10 Mobile People app", "… people contacts app hands on",
"… contacts edit link profiles", "… Anniversary Update calendar people". Candidates tVThtHGpI74 ("Hub People - Windows 10
Mobile", My Windows Mobile) and -igf0FzKNTU ("How are the Apps now?") could not be opened: every player client returned
"Sign in to confirm you're not a bot" by then (R8 §0.3). Windows Central, Windows Latest, OnMSFT and AAWP 2016–2017
People articles: marketing composites or desktop screenshots only (OnMSFT 403).

Confidence: **HIGH** = G1 and G2 agree (two scales) or two builds agree; **MEDIUM** = one native screenshot or one 60-fps
capture; **LOW** = camera, marketing composite or ambiguous; **UNMEASURED**. **All G1/G2 values are 10586-era.** HIGH
means the measurement is solid, not that the governing build is proven (as R8).

---

## 1. Chrome

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P0.1 | Page background (list, grid, editor) | **#000000** | HIGH | G1/G2 samples |
| P0.2 | Status bar height | **24.0 epx**: the card's accent page starts at exactly 96 px (950) = 24.0 epx and at 84 px (XL) = 24.0 epx | HIGH | G1 026 / G2 032 column x=W−6 |
| P0.3 | Nav bar | the accent page ends at 592.0 epx (950) / 683.43 (XL), i.e. **48 epx** above the bottom on both | HIGH | same columns |
| P0.4 | App bar | 48 epx above the nav bar, buttons at **68-epx pitch** with "…" centred 24 epx from the right edge (list: + at 210.0, select at 278.1, "…" at 336.0 on the 360 canvas; card: pin 142.1, link 210.0, edit 278.0, "…" 336.0); glyph row 558–578 epx (20-epx glyphs). Same pattern as R7 §2.1.14 | HIGH | G1 024/026, G2 027 |

## 2. Contact list (CONTACTS pivot)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P1.1 | Pivot header | "**CONTACTS  WHAT'S NEW  GROUPS**", all caps, bold; cap top **43.5 epx**, cap **11.0 epx** (15-epx semibold class, the same as R7 §2.1.1's "CONVERSATIONS"); words at x 12.5–96.5, 121.3–228.4, 253.1–317.8 (≈ 24.8-epx gaps) on both devices | HIGH | G1 024 / G2 027 |
| P1.2 | Pivot colours | selected **white (255)**, others **(156,156,156)** ≈ 61 % white; no underline | HIGH | |
| P1.3 | Search box | outer **72.0 → 108.0 epx (36 epx)**, x **12.0 → W−12** (348.0 / 399.4), **2-epx border (133,133,133)**, black fill; placeholder "**Search**" cap 13.5 epx (≈ 20-epx class) at x **25.0** (13 inside the edge), grey (142) | HIGH | G1/G2 column + row transitions |
| P1.4 | Filter caption | when a filter is on: "Showing **contacts with phone numbers**" under the box (the filter phrase in accent), top ≈ 128.9 epx, 12–13-epx class | MEDIUM | G2 027 only |
| P1.5 | Letter header | the letter alone in **accent**, no tile or box; cap **22.25 epx** (≈ 32-epx class), left **14.75 epx** | HIGH | G1 "D" 136.5–158.75, G2 "B" 168.86–191.14 |
| P1.6 | Header spacing | letter cap top → first avatar top **43.5 ± 0.5 epx**; previous avatar bottom → next letter cap top **29 ± 0.5 epx**; a letter group costs **54.6 epx** on top of the rows | HIGH | G2 Boss 312.29 → C 372.86 → Chewbacca 416.86; G1 I 392.0 → 435.25 |
| P1.7 | Avatar | **32-epx circle**, left **12.0 epx**, photo cropped to the circle | HIGH | G1 / G2 avatar bboxes 32.0 x 32.0 |
| P1.8 | Avatar without a photo | a grey disc with the initial ("B" for Boss) | MEDIUM | G2 027 |
| P1.9 | Name | left **57.75 epx**, cap **12.75 epx** (≈ 18-epx class), white, one line, vertically centred on the avatar | HIGH | G1 "Dexter" 186.75–199.5; G2 "Batman" 273.14–285.71 |
| P1.10 | Row pitch | **50.0 epx** | HIGH | G1 175.75 → 225.75; G2 212.29 → 262.29 → 312.29 |
| P1.11 | Row form vs the phase's stand-in | one line, 32-epx avatar, 50-epx pitch. **Not** R7 §1.3's 48-epx avatar / 70.5-epx two-line History row | HIGH (difference) | |
| P1.12 | List app bar | **+** (new contact), **select** (multi-select list glyph), **…** | HIGH | G1/G2 |

## 3. Jump grid

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P2.1 | Trigger and form | tap a letter header; the list is replaced by a full-page grid on black, pivot header and search box still shown above it | HIGH | G1 025 / G2 028; D-UG "Tap any letter … and in the following menu, tap the first letter" |
| P2.2 | Cell pitch | **72 epx** horizontally and vertically, **fixed in epx**, so the column count follows width: **4 columns on 360 epx, 5 on 411 epx** | HIGH | G1 column centres 68.5 / 140.5 / 212.3 / 284.8, rows 151.5 / 223.2 / 295.6 / 367.9 / 439.3 / 510.8; G2 columns 58.8 / 129.8 / 202.3 / 274.1 / 346.3, rows 150.7 … 510.2 |
| P2.3 | Grid placement | block centred horizontally, about 3 epx left of centre on both; first row cap top **143.5 epx** on both | HIGH | |
| P2.4 | Letters | cap **14.4 epx** (≈ 20-epx Subtitle class); letters with contacts in **accent**, others **(52,52,52)** | HIGH | G1 accent (196,1,122), G2 (66,102,254) |
| P2.5 | Cell order | "#", A–Z, then a **globe** glyph (other scripts) last | HIGH | G2 028 (globe at 202.6, 510.3); on G1 025 (4 columns) the X-Y-Z-globe row would start at ≈ 580 epx, under the app bar, and is not visible |
| P2.6 | 14393 **app-list** grid, for comparison (not People) | 4 columns, **64.4-epx** pitch both ways; letters **white** (available) / (44,46,44), cap **≈ 24 epx** (≈ 34-epx class); an overlay on the list, which is dimmed to ≈ 0 luma rather than removed; "#" first, globe last | MEDIUM | S1 t=429.817: rows 91.8 … 478.1 over six pitches = 64.38; columns 82.4 / 146.6 / 211.3 / 275.6 |
| P2.7 | Grid open motion (14393 app list) | first grid frame ≈ 33–50 ms after the header press. The grid **fades in while shrinking from ≈ 1.08x to 1.00x** about the grid centre: 1.08, 1.06, 1.05, 1.04, 1.03, 1.03, 1.02 at 150 ms, 1.01 at 183 ms, 1.00 by ≈ 280 ms. Letters reach full brightness by ≈ 150 ms, and the list behind dims 24.9 → 4.4 mean luma over ≈ 217 ms | MEDIUM (early-frame fits weak, corr 0.05–0.34) | `scalefit.py` S1 429.533–429.817 |
| P2.8 | Grid close motion (14393 app list) | tap a letter: grid out and list (scrolled to that letter) in as a crossfade of **≈ 83 ms** | MEDIUM | S1 430.500–430.583 |

## 4. Contact card

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P3.1 | Page | **full accent background** from the status bar (24.0) to the nav bar, all text white; status and nav bars black | HIGH | G1 (192,0,119) magenta, G2 (62,101,255) blue |
| P3.2 | Name | **ALL CAPS, semibold**, cap top **49.75–50.0 epx**, cap **10.3–10.5 epx** (15-epx class), left 13.25 | HIGH | "BARRY ALLEN" on both |
| P3.3 | Pivot | "**Profile**" (24-epx class, ascender top **80.0 epx**, left 14.3); "What's New" joins it when a social account supplies posts (WL composite, D-UG) | HIGH (Profile) / LOW (second item) | |
| P3.4 | Photo | **124-epx circle**, left **12.0**, top **120.0 epx** | HIGH | G1/G2 bbox x 12.0–136.0 |
| P3.5 | Account caption | account name ("Gmail") dimmed, under the photo at ≈ 274–282 epx | MEDIUM | |
| P3.6 | Actions | a list of action items: "**Send message**", "**Call Mobile**" + the number, "**Email Personal**" + the address, "**Company**" + the value. Label cap **13.5 epx** (20-epx class), second line ≈ 9.3 (≈ 13-epx), left **12.75–13.75 epx** | HIGH | G1 026 / G2 032 rows 310.75, 358.75 / 381.5, 424.25 / 446.75, 490.25 / 512.25 |
| P3.7 | Action pitch | one-line item → next **48.0 epx**; two-line item → next **65.5–66.0 epx** | HIGH | same rows on both |
| P3.8 | Dual-SIM choice | "Call Mobile" carries a right-aligned SIM selector "**Mtel 4781 ⌄**", right inset 12.3 epx | MEDIUM | G2 032 |
| P3.9 | Card app bar | **pin**, **link** (chain), **edit** (pencil), **…** | HIGH | G1 026, G2 032 |

## 5. Edit contact

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P4.1 | Header | "**EDIT <ACCOUNT> CONTACT**" ("EDIT GMAIL CONTACT"), all caps, cap top **45.5 epx**, cap **11.0**, left 13.0 | HIGH | G1 027 / G2 033 |
| P4.2 | Photo | the 124-epx circle at x 12, top 84 epx, when the contact has one | MEDIUM | G2 033 |
| P4.3 | Field label | typed fields: the type in **accent** with a ⌄ ("Mobile phone ⌄", "Personal email ⌄"), 15-epx class; fixed fields ("Name", "Company") in white | HIGH | G1 135.25; G2 332.29 / 473.43 |
| P4.4 | Text box | **32.0 epx** outer, or **34.0** when an edit (pencil) button sits right of it ("Name", "Company"); **2-epx border (133,133,133)**, black fill; x **12 → W−12** | HIGH | G1 158–190, 465–499; G2 355.1–387.1, 256–290 |
| P4.5 | Label → box | label cap top → box top **22.75 epx** | HIGH | G1 135.25 → 158.0; G2 332.29 → 355.14 |
| P4.6 | "+ field" rows | "+ Phone", "+ Email", "+ Ringtone", "+ Text alert tone", "+ Address", "+ Other": a 16-epx "+" and a 15-epx label at x 12; consecutive rows at **44-epx** pitch | MEDIUM | G1 277 → 321 |
| P4.7 | Group rules | **1-epx (103,103,103)** full-width rules (x 12 → W−12) between field groups, about **22–23 epx** clear above and below | HIGH | G1 113 / 254 / 359 / 420; G2 310 / 451 |
| P4.8 | Edit app bar | **save** (disabled until a change), **cancel** (X glyph), **…** | MEDIUM | G1 027 |

## 6. Other People pages

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P5.1 | Filter contact list (… > Settings) | page "Filter contacts": a toggle "Hide contacts without phone numbers" with the line "… only the contacts you can call or text. You can still find others by searching.", then "Show contacts from" with one checkbox per account; **Done / Cancel** buttons side by side at the bottom | LOW | WC k2inCYKt… (camera); D-UG l.2196 |
| P5.2 | Groups pivot | group rows: a round group avatar, the group name and "<n> members", under account headers ("GroupMe", "Windows") | LOW | WC of7ME6cx… |
| P5.3 | Account picker (new contact with several accounts / add account) | "Choose an account": Outlook.com, Exchange, Google, iCloud, … "Advanced setup", Close | LOW | WC Tb56RMQc…; D-UG l.2177 |
| P5.4 | Link contacts | D-UG: card ▸ link glyph ▸ "Select a contact to link"; unlink: link glyph ▸ the linked contact ▸ "**Unlink**". Visual UNMEASURED | UNMEASURED | D-UG l.2255–2262 |
| P5.5 | Import from SIM | D-UG: "… > Settings > Import from SIM", SIM 1 / SIM 2 choice, account choice (default Outlook), "next", "import", "clear" + checkboxes. Visual UNMEASURED | UNMEASURED | D-UG l.2273–2284 |
| P5.6 | Share contact | D-UG: card ▸ … ▸ "**Share Contact**" ▸ share picker | UNMEASURED | D-UG l.2314–2318 |
| P5.7 | Delete contact | D-UG: card ▸ … ▸ "**delete**"; multi-select from the list's select button | UNMEASURED (visual) | D-UG l.2189–2194 |
| P5.8 | People tile | R3 A9 (bubble event, 7.7-s period) and C3 (circle pattern); not re-measured | cited | R3 |

## 7. Motion

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| P6.1 | List → card, card → editor, editor → card, back to the list | UNMEASURED (no video of the People app on any build reached) | UNMEASURED | §0.3 |
| P6.2 | Pivot swipe CONTACTS ↔ WHAT'S NEW ↔ GROUPS | UNMEASURED | UNMEASURED | |
| P6.3 | Row press | UNMEASURED | UNMEASURED | |

## 8. Segoe MDL2 glyphs (2017 MDL2 list, windows-dev-docs d6050b7; visual match: MEDIUM)

List app bar: Add E710, MultiSelect E762, More E712. Card app bar: Pin E718, Link E71B, Edit E70F, More E712 (Unpin E77A
when pinned). Edit app bar: Save E74E, Cancel E711. Field rows: Add E710; label chevron ChevronDown E70D. Card actions (the
W10M card shows text only, no glyphs): Message E8BD, Phone E717, Mail E715, MapPin E707 / Street E913 if a P4 design adds
them. Jump grid globe: Globe E774 (World E909 is the alternative). Search: Search E721. People tile / app icon: People
E716, Contact E77B. Share E72D, Delete E74D, Import E8B5, Filter E71C. Licence: R1 §10.

## 9. Strings as shipped (10586 unless noted)

Pivots "CONTACTS", "WHAT'S NEW", "GROUPS"; placeholder "Search"; filter caption "Showing contacts with phone numbers"; card
actions "Send message", "Call Mobile", "Email Personal", "Company", account caption "Gmail"; card pivot "Profile" ("What's
New"); editor header "EDIT GMAIL CONTACT", "Name", "Mobile phone", "Personal email", "Company", "+ Phone", "+ Email", "+
Ringtone", "+ Text alert tone", "+ Address", "+ Other"; filter page "Filter contacts", "Hide contacts without phone
numbers", "Show contacts from", "Done", "Cancel"; link "Select a contact to link", "Unlink"; SIM "Import from SIM",
"next", "import", "clear"; share "Share Contact"; delete "delete". The What's New feed ("Showing Facebook") is out of scope
(A11).

## 10. UNMEASURED, with proposed tagged approximations

| # | What | Proposed approximation (derived from) |
|---|---|---|
| U1 | The People app on 14393 / 15063 / 15254 | carry G1/G2 across unchanged (the WL 2017 composite shows the same card form); [fidelity] H2 cannot be closed against the final build, say so in the H-row |
| U2 | Link / unlink page | the list form (P1.5–P1.10) for "Select a contact to link"; linked profiles as card-style action rows (P3.6) |
| U3 | Import from SIM page | the list form with a checkbox before the avatar (checkbox 20.6 epx, R7 §3.7.2); buttons as settings-front.md 4.9 |
| U4 | Filter page geometry | settings-front.md leaf page (4.1, 4.5 toggle, 4.9 buttons) with Done / Cancel as a full-width two-button bar |
| U5 | Motion of list → card → editor | the settings-front.md 6.2–6.3 page entrance (cut, then a ≈ 10-epx slide-up + fade, 250–300 ms), measured on 14393 and 15063 for the same app platform |
| U6 | Pivot swipe | phase 01 X13 (finger-tracked, 250-ms ease-out settle) |
| U7 | Row press | R7 §3.5.7 pressed fill (63,68,64) full width, the colour settings-front.md 3.6 also measured |
| U8 | Jump grid open / close for People | the 14393 app-list motion P2.7–P2.8 |
| U9 | Card photo fallback (no photo) | the P1.8 grey disc with the initial, scaled to 124 epx |

## 11. Gaps for the phase doc (phase-16-inbox-calendar-people.md)

- **The contact-row stand-in is wrong.** The Decisions and E10 use R7 §1.3's History row (48-epx avatar at 36.2, text at
  74–75.4, 70.5-epx pitch). W10M People is **32-epx avatar at x 12, name at x 57.75, 50-epx pitch** (P1.7–P1.10), with
  accent letter headers (P1.5–P1.6). E10's "70.5 ± 1.2 epx … 48-epx avatar centred 36.2" should become these values.
- **Jump grid (X8) differs by surface and build.** People 10586: 72-epx cells, 4 columns at 360 epx, accent letters, a full
  page (P2.2–P2.5). 14393 app list: 64.4-epx cells, white letters, an overlay (P2.6). Neither matches X8's "5 columns, ≈ 50
  epx" (R3 C2, 10586, 640x360 footage, LOW). E20's jump-grid assertion needs a choice: People's own grid, or phase 01's grid
  re-measured from S1 (a phase 01 matter).
- The contact card is a **full accent page** with a 124-epx photo and text-only actions (P3.1–P3.7). The phase says
  "contact card (photo, numbers with call / text, emails with mail, addresses with map, …)". The W10M layout puts each
  action as a titled row ("Call Mobile" + number), not as icons.
- The editor is a flat field list with accent type-labels and "+ field" rows (P4.*). The phase's current stand-in (R7 §3.7.1
  accent-bordered 43.4-epx fields) does not match: People fields are **32 epx with a grey 2-epx border**.
- Pivots: W10M People had CONTACTS / WHAT'S NEW / GROUPS. What's New is out (A11). **Groups** is not in the phase's scope:
  either add it (D-UG documents create / rename / delete groups and message a group) or record it as out.
- Status bar: 24 epx measured (P0.2). The phase cites 28 epx (R3 C4); see settings-front.md §10.
- Link / unlink, SIM import, share, filter: only guide wording exists (P5.*). H11 stays [accept] for all of them.
- "Me" (the Android profile) left out: no W10M People screen showed a Me row in the list (G1/G2). This agrees with H9.

## Tally

Each row in §1–§7 is counted once, at its stated level (P3.3 at its lower level, LOW). P5.8 (cited) is not counted. §10
rows that repeat a §6–§7 row are not counted again: U2 = P5.4, U3 = P5.5, U5 = P6.1, U6 = P6.2, U7 = P6.3.

- **HIGH 30**: P0.1–P0.4, P1.1, P1.2, P1.3, P1.5, P1.6, P1.7, P1.9, P1.10, P1.11, P1.12, P2.1–P2.5, P3.1, P3.2, P3.4,
  P3.6, P3.7, P3.9, P4.1, P4.3, P4.4, P4.5, P4.7.
- **MEDIUM 10**: P1.4, P1.8, P2.6, P2.7, P2.8, P3.5, P3.8, P4.2, P4.6, P4.8.
- **LOW 4**: P3.3, P5.1, P5.2, P5.3.
- **UNMEASURED 11**: P5.4–P5.7, P6.1–P6.3, U1, U4, U8, U9.

Biggest gap: no People capture on the governing build (U1). Every People geometry value is 10586-era. Next come link /
unlink and SIM import, which have guide wording only.
