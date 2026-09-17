# Windows 10 Mobile fidelity reference (R1)

Target: Galaxy S25 Ultra, 1440x3120 px, ~6.9" (from the project brief). Shell: Kotlin + Jetpack Compose.

Every number and hex value below cites the URL it was read from, inline. Scope tags:

- **[W10M]** Windows 10 Mobile specific (10586 / 14393 era).
- **[UWP]** general Windows 10 design / XAML guidance (desktop + mobile shared).
- **[WP8.1]** Windows Phone 8.1 (or the WP8.1 hardware adaptation kit docs, which Microsoft filed under "Windows 10 hardware dev").
- **[WP8]** / **[WP7]** older Windows Phone; may differ.
- **[WinUI3]** current Windows App SDK docs, post-W10M; included only where the Win10-era value could not be found.
- **[derived]** arithmetic on cited values; not itself sourced.

Where a value could not be sourced it says `NOT FOUND (searched: ...)`. Nothing below is filled from memory.

Fetch notes: `www.windowscentral.com`, `onmsft.com`, `gsmarena.com`, `windowsreport.com`, `digitalcitizen.life`, `tenforums.com` (some), `mspoweruser.com`, `packtpub.com` returned 403/timeouts; `web.archive.org` is blocked in this tool. `forums.windowscentral.com`, `thurrott.com`, `phonescoop.com`, `learn.microsoft.com`, `raw.githubusercontent.com`, `api.github.com` worked. Where a value comes only from a search-engine snippet of an unfetchable page it is marked "(search snippet; page 403)".

---

## 1. Start screen

### 1.1 Tile sizes

| Item | Value | Scope | Source |
|---|---|---|---|
| Sizes offered on W10M | small, medium, wide (three) | [W10M] | https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start |
| Large (310x310) on W10M | Not offered in shipping W10M: Thurrott lists three sizes; early Insider builds had 5 sizes incl. "Long" and "Large" which "may not have been available in the final release" | [W10M] | https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start ; https://onmsft.com/news/windows-10-mobile-new-tile-sizes-bring-whole-new-dynamic-start-screen/ (search snippet; page 403) |
| UWP tile sizes (epx) | small 71x71, medium 150x150, wide 310x150, large 310x310 | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/style/iconography/app-icon-construction |
| Scale plateaus for tile assets | 100/125/150/200/250/300/400% → medium 150/188/225/300/375/450/600 px; wide 310x150 → 1240x600 @400%; small 71 → 284 @400% | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/style/iconography/app-icon-construction |
| Grid unit model (OEM layout) | Start grid is addressed in small-tile units; small = 1x1, medium = 2x2 (even X/Y coords), "Large" (= wide) = 4x2 (X = 0 on 4-col, 0 or 2 on 6-col). 4-column layout = 2 medium columns; 6-column = 3 medium columns | [WP8.1] | https://learn.microsoft.com/en-us/previous-versions/dn772303(v=vs.85) |
| WP8 image sizes at WXGA (768x1280) | flip/cycle: small 159x159, medium 336x336, wide 691x336; iconic: small 110x110, medium 202x202 | [WP8] | https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh202948(v=vs.105) |

### 1.2 Gutter and edge margins

| Item | Value | Scope | Source |
|---|---|---|---|
| Design grid | 25x25 px squares, 12 px between squares, 24 px page padding; elements spaced in multiples/subdivisions of 12 px; 12 or 24 px left margin | [WP8] (WVGA 480x800 base) | http://bsubramanyamraju.blogspot.com/2014/03/ui-design-guidelines-for-windows-phone-8.html |
| Gutter between tiles at WXGA | 691 − (2 × 336) = 19 px, which is 12 px × 1.6 (WXGA/WVGA scale) — consistent with the 12 px grid | [derived] from hh202948 sizes above | https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh202948(v=vs.105) |
| W10M gutter / edge margin in epx | NOT FOUND (searched: "Windows 10 Mobile start screen tile gutter", "Windows Phone start screen tile spacing gutter pixels", "tile margin epx UWP start") | [W10M] | — |
| UWP phone side margins (app content, not Start) | 12 px left/right margins on phones | [UWP] | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/layout/screen-sizes-and-breakpoints-for-responsive-design.md |
| Internal tile content margin | 8 px from tile edge at 100% (16 @200%, 32 @400%); 8 px between adaptive columns | [UWP] | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/shell/tiles-and-notifications/app-assets.md ; https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |

### 1.3 Columns and "Show more tiles"

| Item | Value | Scope | Source |
|---|---|---|---|
| Auto default | ≤4.9" → 4-column (2 medium) layout; ≥5" → 6-column (3 medium) layout, keyed off HORZSIZE | [WP8.1] | https://learn.microsoft.com/en-us/previous-versions/dn772303(v=vs.85) |
| User override | Users on ≤4.9" phones can "show more tiles" in Start + theme | [WP8.1] | https://learn.microsoft.com/en-us/previous-versions/dn772303(v=vs.85) |
| Lumia 950 default | three-column layout by default | [W10M] | https://www.phonescoop.com/articles/article.php?a=16968&p=6669 ("arrange the Live Tiles into three columns"); https://www.windowscentral.com/enable-four-columns-start-screen-lumia-950 (search snippet; page 403) |
| Toggle effect | On = smaller tiles, more of them; Off = larger tiles, fewer | [W10M] | https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start |
| Small-phone layout | one wide + one medium adjacent, or three mediums; large phones (1520) two wides side by side or four mediums | [W10M] | https://onmsft.com/news/windows-10-mobile-new-tile-sizes-bring-whole-new-dynamic-start-screen/ (search snippet; page 403) |

### 1.4 Label (app name), badge, icon inside tile

| Item | Value | Scope | Source |
|---|---|---|---|
| Label position | Branding (display name + corner logo) sits "on the bottom of a live tile"; default branding = `name` if the base tile shows its display name | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |
| Branding bar height | 32 px @100% (64 @200%, 128 @400%) | [UWP] | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/shell/tiles-and-notifications/app-assets.md |
| Label font size | NOT FOUND as an explicit value (searched: "tile display name font size epx", "Windows Phone tile title font size"). Nearest sourced value: the default tile text style is `caption` = 12 epx Regular | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |
| Label char budget | medium tile title ≈19 chars, wide ≈39 (Segoe WP, no wrap, truncate) | [WP8] | https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj662924(v=vs.105) |
| Badge placement | "lower-right corner of its start tile"; numeric 1–99, >99 shows the 99+ glyph; 0 clears; system glyph set only | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/badges |
| Icon size inside tile | small: ≤66% of tile width and height; medium/wide/large: width ≤66%, height ≤50%; list-view icons 75% | [UWP] | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/shell/tiles-and-notifications/app-assets.md |
| App-list icon base | Square44x44Logo = 44 px @100% (88 @200%, 176 @400%) | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/style/iconography/app-icon-construction |
| WP8 iconic best-fit | small icon 70x110 in a 110x110 box; medium 130x202 in 202x202; pad vertically, crop tight horizontally | [WP8] | https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj662924(v=vs.105) |

### 1.5 Background image and transparency

| Item | Value | Scope | Source |
|---|---|---|---|
| Background modes | "Tile picture" (image shows only through tiles, not between them), "Full screen picture" (image fills the screen), None | [W10M] | https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start |
| Transparency slider | Only in Full screen picture mode; 0% = completely transparent … 100% = opaque | [W10M] | https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start |
| Default slider value | NOT FOUND (searched: "Windows 10 Mobile tile transparency default", tenforums 7188 fetch 403) | [W10M] | — |
| Which tiles go transparent (tile-picture mode) | only main system tiles (dialler, messaging, calendar, Cortana, other built-ins) | [WP8.1] | https://blogs.windows.com/devices/2014/04/29/deep-dive-windows-phone-8-1-start-screen-backgrounds/ |
| Which tiles go transparent (full-screen mode) | NOT FOUND from a fetchable page (forum thread exists: https://forums.windowscentral.com/windows-10-mobile-insider-preview/360778-why-some-tiles-colorized-why-not-transparent.html, not fetched) | [W10M] | — |
| Parallax | Tile-picture background "scrolls ever so slightly along with the homescreen, but at a different rate", vertical parallax | [WP8.1] | https://www.windowscentral.com/windows-phone-81-video-reveals-startscreen-parallax-view (search snippet; page 403); https://venturebeat.com/business/microsoft-launches-tileart-app-to-bring-parallax-effects-to-windows-phone-homescreens/ (search snippet) |
| Parallax rate / amount | NOT FOUND (searched: "Windows Phone 8.1 start background parallax rate", "Windows 10 Mobile full screen background parallax") | [WP8.1]/[W10M] | — |
| Parallax in W10M full-screen mode | NOT FOUND (Thurrott's W10M customization tip does not mention parallax or scrolling) | [W10M] | https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start |

---

## 2. Live tile motion

| Item | Value | Scope | Source |
|---|---|---|---|
| WP8 templates | flip (front→back), iconic, cycle (up to nine images) | [WP8] | https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh202948(v=vs.105) |
| Flip properties | Title, Count, BackTitle, BackContent, WideBackContent, Small/Background/BackBackground/WideBackground/WideBackBackground images | [WP8] | https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj206971(v=vs.105) |
| Flip timing | flips "at random intervals with smart logic to make flips asynchronous" | [WP] | https://www.slideshare.net/slideshow/live-tiles-and-notifications-in-windows-phone/10500564 (search snippet) |
| UWP adaptive tiles | one `<binding>` per size (TileSmall/Medium/Wide/Large); text hint-styles; groups/subgroups = columns; background image; peek image | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |
| Peek | "slide down/up from the top of the tile, peeking into view, and then later sliding back out to reveal the main content" | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |
| Overlay defaults | background image overlay 20% black when text present (else 0); peek image overlay default 0 (overlay on peek from 1511); hint-overlay 0–100 | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |
| "Subtle" text | 60% opacity | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles |
| Notification queue | up to 5 notifications cycled; "The amount of time each notification in the queue is displayed and the order … cannot be controlled by apps" | [UWP] | https://learn.microsoft.com/en-us/windows/apps/develop/notifications/periodic-notification-overview (search snippet) |
| Multi-tile choreography | tiles "flip one by one to catch the user's attention; after some time, the animation will change to 'slide' from bottom to up. Finally, the animation will stop at the last tile"; without the queue they flip once and stop | [UWP desktop] | https://learn.microsoft.com/en-us/archive/blogs/wushuai/tips-to-handle-live-tile-update-in-background-task |
| Flip / peek / cycle durations (ms) | NOT FOUND (searched: "live tile flip animation duration", "peek image animation duration", "tile notification animation timing seconds") | — | — |
| Stagger / randomization rule | only the qualitative statements above; no numeric interval found (same searches) | — | — |
| People tile (rotating photo mosaic) | NOT FOUND (searched: "People tile animation Windows Phone", "circular people rolling animation") — one forum thread title exists: https://forums.windowscentral.com/threads/circular-people-rolling-animation-is-awesome.364268/ (not fetched) | — | — |

---

## 3. Motion system

### 3.1 Press / tilt

| Item | Value | Scope | Source |
|---|---|---|---|
| Tilt max angle | `MaxAngle = 0.3` rad (≈17.2°) | [WP7/8 toolkit port] | https://raw.githubusercontent.com/timheuer/callisto/master/src/Callisto/Effects/TiltEffect.cs |
| Max depression | `MaxDepression = 25` px (translate into the screen) | [WP7/8 toolkit port] | same |
| Return delay / duration | `TiltReturnAnimationDelay = 200 ms`, `TiltReturnAnimationDuration = 100 ms`, linear (no easing) | [WP7/8 toolkit port] | same |
| Formulas | `angle = angleMagnitude * MaxAngle * 180/π`; `depression = (1 − angleMagnitude) * MaxDepression`; magnitude = normalized distance of touch from element centre | [WP7/8 toolkit port] | same |
| Original tilt math | `xAngle = asin((y − h/2)/(h/2))`, `yAngle = acos((x − w/2)/(w/2))` via PlaneProjection; `TiltStrength` and `PressStrength` 0..1 | [WP7] | https://learn.microsoft.com/en-us/archive/blogs/ptorr/tilt-effect-for-windows-phone-controls |
| UWP pointer-down | `PointerDownThemeAnimation`/`PointerUpThemeAnimation` give "feedback for a successful tap or click on a tile"; duration is preconfigured (Duration ignored); overrides Projection and RenderTransform | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/motion/xaml-animation ; https://learn.microsoft.com/en-us/uwp/api/windows.ui.xaml.media.animation.pointerdownthemeanimation |
| UWP pointer-down scale/duration values | NOT FOUND (searched: "PointerDownThemeAnimation scale 0.97 duration", API page gives none) | [UWP] | — |

### 3.2 Page transitions

WP7/8 toolkit turnstile storyboards (the canonical "turnstile" numbers):

| Storyboard | RotationY | Duration | Easing | Opacity | Source |
|---|---|---|---|---|---|
| TurnstileForwardIn | −80° → 0° | 0.35 s | ExponentialEase EaseOut, Exponent 6 | 0→1 at 0.01 s | https://raw.githubusercontent.com/microsoftarchive/WindowsPhoneToolkit/master/Microsoft.Phone.Controls.Toolkit/Transitions/Storyboards/TurnstileForwardIn.xaml |
| TurnstileForwardOut | 0° → 50° | 0.25 s | ExponentialEase EaseIn, Exponent 6 | 1 until 0.24 s, 0 at 0.25 s | https://raw.githubusercontent.com/microsoftarchive/WindowsPhoneToolkit/master/Microsoft.Phone.Controls.Toolkit/Transitions/Storyboards/TurnstileForwardOut.xaml |
| TurnstileBackwardIn | 50° → 0° | 0.35 s | ExponentialEase EaseOut, Exponent 6 | 0→1 at 0.01 s | https://raw.githubusercontent.com/microsoftarchive/WindowsPhoneToolkit/master/Microsoft.Phone.Controls.Toolkit/Transitions/Storyboards/TurnstileBackwardIn.xaml |
| TurnstileBackwardOut | 0° → −80° | 0.25 s | ExponentialEase EaseIn, Exponent 6 | 1 until 0.24 s, 0 at 0.25 s | https://raw.githubusercontent.com/microsoftarchive/WindowsPhoneToolkit/master/Microsoft.Phone.Controls.Toolkit/Transitions/Storyboards/TurnstileBackwardOut.xaml |

Scope: [WP7/WP8 Silverlight toolkit]. CenterOfRotationX is not animated in these files (rotation is about the element's default centre unless the host sets it). The same folder also has Slide*, Swivel*, Roll and Rotate* storyboards: https://api.github.com/repos/microsoftarchive/WindowsPhoneToolkit/contents/Microsoft.Phone.Controls.Toolkit/Transitions/Storyboards

WP8.1 / UWP navigation transitions:

| Item | Value | Scope | Source |
|---|---|---|---|
| WP8.1 default on app launch | `CommonNavigationTransitionInfo` = "that roll in from the right transition"; `ContinuumNavigationTransitionInfo` = "a very short zoom-in"; `SlideNavigationTransitionInfo` = slide | [WP8.1] | https://www.c-sharpcorner.com/UploadFile/6d1860/utilizing-page-transition-animations-in-windows-phone-8-1-ap/ |
| Continuum (UWP API) | "Specifies the object that will fly between pages to provide context during a continuum transition"; ExitElement / IsEntranceElement / IsExitElement / ExitElementContainer | [UWP] (introduced 10.0.10240) | https://learn.microsoft.com/en-us/uwp/api/windows.ui.xaml.media.animation.continuumnavigationtransitioninfo |
| Common (UWP API) | `IsStaggeringEnabled`, `IsStaggerElement` attached property (the staggered/feathered entrance) | [UWP] (10.0.10240) | https://learn.microsoft.com/en-us/uwp/api/windows.ui.xaml.media.animation.commonnavigationtransitioninfo |
| UWP default | `NavigationThemeTransition` default = Page refresh (`EntranceNavigationTransitionInfo`: slide up + fade in); Drill (`DrillInNavigationTransitionInfo`); Slide FromLeft/FromRight; Suppress | [UWP] | https://learn.microsoft.com/en-us/uwp/api/windows.ui.xaml.media.animation.navigationthemetransition ; https://learn.microsoft.com/en-us/windows/apps/design/motion/page-transitions |
| Old WP behaviour (per MS docs) | "Turnstile page transition where the current page would rotate out and the new page would rotate in"; Continuum "current page would sort of sink into the background and the new one would fade in" | [WP8.x] | https://learn.microsoft.com/en-us/windows/apps/design/motion/page-transitions (search snippet of the legacy note) |
| W10M shell Start→app launch animation (tiles fly out / turnstile) | NOT FOUND (searched: "Windows 10 Mobile app launch animation tiles fly", "Windows Phone tiles fly off screen animation", "Windows 10 Mobile turnstile app open") | [W10M] | — |

### 3.3 Durations and easing

| Item | Value | Scope | Source |
|---|---|---|---|
| Page transition (Fluent example) | Forward out: fade 150 ms, accelerate. Forward in: slide up 150 px, 300 ms, decelerate. Backward out: slide down 150 px, 150 ms, accelerate. Backward in: fade 300 ms, decelerate | [UWP/Fluent, RS5-era doc] | https://learn.microsoft.com/en-us/windows/apps/design/motion/motion-in-practice |
| Object expand/contract | Expand 300 ms Standard; Contract 150 ms accelerate | [UWP/Fluent] | same |
| Decelerate ("Fast Out, Slow In") | `cubic-bezier(0, 0, 0, 1)` — entering objects | [UWP/Fluent] | https://learn.microsoft.com/en-us/windows/apps/design/motion/timing-and-easing |
| Accelerate ("Slow Out, Fast In") | `cubic-bezier(1, 0, 1, 1)` — exiting objects | [UWP/Fluent] | same |
| "Standard" curve values | NOT FOUND on current page (older Win10 timing page could not be fetched: raw commit 404, archive blocked) | — | — |
| WinUI 3 named durations | ControlNormalAnimationDuration 250 ms; ControlFast 167 ms; ControlFaster 83 ms | [WinUI3] | https://learn.microsoft.com/en-us/windows/apps/design/motion/timing-and-easing |
| Connected animation | Direct config animates over 150 ms with Decelerate; Gravity = "scale and dip"; source element frozen ≤~250 ms; disposed after 3 s | [UWP 1809+] | https://learn.microsoft.com/en-us/windows/apps/design/motion/connected-animation |
| Theme animation catalogue | Entrance, Content, Fade, PointerDown/Up, Reposition, PopIn/Out, EdgeUI, Pane, AddDelete, Reorder, Drag/Drop, SplitOpen/Close, DrillIn/DrillOut | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/motion/xaml-animation |

---

## 4. App list

| Item | Value | Scope | Source |
|---|---|---|---|
| Gesture | swipe left from Start to reach the alphabetical app list | [W10M] | https://forums.windowscentral.com/ask-question/304854-why-dont-i-see-list-apps-when-swiping-right.html ("try swiping left") |
| Layout | "a long list of apps sorted alphabetically"; "the search bar at the top" is the fastest navigation | [W10M] | https://www.phonescoop.com/articles/article.php?a=16968&p=6669 |
| Letter headers / jump grid | apps alphabetical in a vertical column; tapping any capital letter header "summons the entire alphabet" to jump | [WP8.1] | https://www.phonescoop.com/articles/article.php?a=13917&p=6047 |
| "Recently added" group on W10M | NOT FOUND from a fetchable W10M-specific page (searched: "Windows 10 Mobile app list recently added", "'Windows 10 Mobile' 'Recently added' app list header"). Desktop Win10 Start shows "recently added" at the top of the app list and a clock glyph in the letter index for it | [UWP desktop only] | https://www.microsoftpressstore.com/articles/article.php?p=3104533&seqNum=2 (search snippet) |
| New installs | "New apps or games that you download won't be placed on the Start screen right away" — they land in the App list | [WP8.1] | https://blogs.windows.com/devices/2015/01/26/lumia-screens-explained-glance-lock-start-app-list/ |
| Search box specifics (height, placeholder) | NOT FOUND | [W10M] | — |

---

## 5. Typography

### 5.1 Windows 10 XAML type ramp (Segoe UI)

| Style | Weight | Size (epx) | Source |
|---|---|---|---|
| Header | Light | 46 | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/controls-and-patterns/xaml-theme-resources.md |
| Subheader | Light | 34 | same |
| Title | SemiLight | 24 | same |
| Subtitle | Normal (Regular) | 20 | same |
| Base | SemiBold | 15 | same |
| Body | Normal (Regular) | 15 | same |
| Caption | Normal (Regular) | 12 | same |

Scope [UWP, Windows 10]. FontFamily "Segoe UI", TextWrapping Wrap, LineStackingStrategy MaxHeight (same source). Line height rule: "Line spacing should equal 125% of font size, rounded to the nearest 4px multiple"; kerning "metrics", tracking 0, letter spacing 0 (https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/style/typography.md). The tile text ramp is identical: caption 12 Regular, body 15 Regular, base 15 Semibold, subtitle 20 Regular, title 24 Semilight, subheader 34 Light, header 46 Light (https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles). Explicit per-style line heights for Win10: NOT FOUND in text form (the Win10 typography page only shipped the ramp as an image).

Do not use the Windows 11 ramp (Caption 12/16, Body 14/20, Subtitle 20/28 Semibold, Title 28/36, …, Segoe UI Variable) — that is post-W10M: https://learn.microsoft.com/en-us/windows/apps/design/style/typography

### 5.2 WP8 / WP8.1 (Segoe WP) for comparison

| Resource | Value | Source |
|---|---|---|
| PhoneFontSizeSmall / Normal / Medium / MediumLarge / Large / ExtraLarge / ExtraExtraLarge / Huge | 18.667 / 20 / 22.667 / 25.333 / 32 / 42.667 / 72 / 186.667 | https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff769552(v=vs.105) |
| Families | Segoe WP, Segoe WP Light, Segoe WP Semilight, Segoe WP Semibold | same |
| Margins | PhoneMargin 12; PhoneHorizontalMargin 12,0; PhoneTouchTargetOverhang 12; PhoneTouchTargetLargeOverhang 12,20 | same |
| Group header style | SemiLight, PhoneFontSizeLarge (32), subtle brush | same |

Scope [WP8].

### 5.3 Selawik

| Item | Value | Source |
|---|---|---|
| License | SIL Open Font License 1.1 (26 Feb 2007); "Copyright 2015, Microsoft Corporation … with Reserved Font Name Selawik"; file is `LICENSE.txt` at repo root | https://raw.githubusercontent.com/microsoft/Selawik/master/LICENSE.txt ; https://api.github.com/repos/microsoft/Selawik/contents/ |
| Purpose | "Selawik is an open source replacement for Segoe UI" | https://raw.githubusercontent.com/microsoft/Selawik/master/README.md |
| Metric compatibility | Microsoft: "An open-source font that's metrically compatible with Segoe UI, intended for apps on other platforms that don't want to bundle Segoe UI" | https://learn.microsoft.com/en-us/windows/apps/design/style/typography |
| Known gaps | "Selawik is missing kerning to match Segoe UI"; "Selawik needs improved hinting" | https://raw.githubusercontent.com/microsoft/Selawik/master/README.md |
| Weights | Regular, Semilight, Light, Bold, Semibold | https://learn.microsoft.com/en-us/windows/apps/design/style/typography |
| Source | single `selawik.glyphs` + UFO folder (weights not enumerated by file name) | https://api.github.com/repos/microsoft/Selawik/contents/Source%20files/Glyphs |

Implication: OFL permits bundling in an APK (cannot be sold on its own; keep the notice; renamed derivatives cannot use "Selawik"). Segoe UI itself is not redistributable under any open license found.

---

## 6. Color

### 6.1 Accent palettes

Windows Phone 8.1 adaptation-kit palette (20 colors, non-ARGB hex) — Microsoft filed this doc under "Windows 10 hardware dev" but it is dated 2015-08-28 for the WP8.1 kit. Scope [WP8.1]. Source: https://learn.microsoft.com/en-us/previous-versions/dn772323(v=vs.85)

| ID | Name | Hex | Dual-SIM complement |
|---|---|---|---|
| 0 | Lime | A4C400 | Green |
| 1 | Green | 60A917 | Emerald |
| 2 | Emerald | 008A00 | Teal |
| 3 | Teal | 00ABA9 | Cyan |
| 4 | Cyan | 1BA1E2 | Cobalt |
| 5 | Cobalt | 3E65FF | Indigo |
| 6 | Indigo | 6A00FF | Violet |
| 7 | Violet | AA00FF | Pink |
| 8 | Pink | F472D0 | Magenta |
| 9 | Magenta | D80073 | Crimson |
| 10 | Crimson | A20025 | Red |
| 11 | Red | E51400 | Orange |
| 12 | Orange | FA6800 | Amber |
| 13 | Amber | F0A30A | Yellow |
| 14 | Yellow | E3C800 | Amber |
| 15 | Brown | 825A2C | Taupe |
| 16 | Olive | 6D8764 | Taupe |
| 17 | Steel | 647687 | Mauve |
| 18 | Mauve | 76608A | Steel |
| 19 | Taupe | 87794E | Brown |

OEM custom accents: up to 4, ARGB `0xFFxxxxxx`, IDs 101–104 (same source). Default theme: `DefaultBackgroundColor` 0 = light, 1 = dark (same source).

Windows 10 accent palette (48 colors, 8 rows × 6). Scope [UWP, Win10]. Hex grid: https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/style/color.md ; names: https://jmacthefatcat.github.io/win-10-colours/

| Row | Hex (col 1..6) |
|---|---|
| 1 | FFB900 Yellow Gold · E74856 Pale Red · 0078D7 Default Blue · 0099BC Cool Blue Bright · 7A7574 Grey · 767676 Overcast |
| 2 | FF8C00 Gold · E81123 Red · 0063B1 Navy Blue · 2D7D9A Cool Blue · 5D5A58 Grey Brown · 4C4A48 Storm |
| 3 | F7630C Orange Bright · EA005E Rose Bright · 8E8CD8 Purple Shadow · 00B7C3 Seafoam · 68768A Steel Blue · 69797E Blue Grey |
| 4 | CA5010 Orange Dark · C30052 Rose · 6B69D6 Purple Shadow Dark · 038387 Seafoam Teal · 515C6B Metal Blue · 4A5459 Grey Dark |
| 5 | DA3B01 Rust · E3008C Plum Light · 8764B8 Iris Pastel · 00B294 Mint Light · 567C73 Pale Moss · 647C64 Liddy Green |
| 6 | EF6950 Pale Rust · BF0077 Plum · 744DA9 Iris Spring · 018574 Mint Dark · 486860 Moss · 525E54 Sage |
| 7 | D13438 Brick Red · C239B3 Orchid Light · B146C2 Violet Red Light · 00CC6A Turf Green · 498205 Meadow Green · 847545 Camouflage Desert |
| 8 | FF4343 Mod Red · 9A0089 Orchid · 881798 Violet Red · 10893E Sport Green · 107C10 Green · 7E735F Camouflage |

Which palette the W10M Start + theme picker actually exposed (the 20 WP8.1 colors, the 48 Win10 colors, or both): NOT FOUND (searched: "Windows 10 Mobile 48 accent colors", "Windows 10 Mobile accent colors hex values list", "Start + theme colors list Windows 10 Mobile"). Both palettes are given so the shell can offer either; do not assume.

Accent shades: "Light and dark shades of the accent color are created based on HSB values of color luminosity"; seven variants SystemAccentColorLight3…Dark3 [UWP] (color.md above). Exact shade formula: NOT FOUND.

### 6.2 Theme base colors

| Resource | Light | Dark | Scope | Source |
|---|---|---|---|---|
| SystemAltHighColor (page background) | #FFFFFFFF | #FF000000 | [UWP] | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/controls-and-patterns/xaml-theme-resources.md |
| SystemBaseHighColor (text) | #FF000000 | #FFFFFFFF | [UWP] | same |
| SystemChromeLowColor | #FFF2F2F2 | #FF171717 | [UWP] | same |
| SystemChromeMediumColor | #FFE6E6E6 | #FF1F1F1F | [UWP] | same |
| SystemChromeBlackHighColor | #FF000000 | #FF000000 | [UWP] | same |
| WP8 PhoneBackgroundColor / PhoneForegroundColor hex | NOT FOUND in text (ff769552 lists names only) | — | [WP8] | https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff769552(v=vs.105) |
| Glance/lock text | "time and date in large, white text on the black background" | [W10M] | https://www.phonescoop.com/articles/article.php?a=16968&p=6669 |

---

## 7. Action center (W10M)

| Item | Value | Scope | Source |
|---|---|---|---|
| Gesture | swipe down from the top of the screen | [W10M] | https://en.wikipedia.org/wiki/Action_Center ; https://www.phonescoop.com/articles/article.php?a=16968 (search snippet) |
| Quick actions | 16 controls available; default view shows four; expand to see all 16 | [W10M] | https://www.phonescoop.com/articles/article.php?a=16968&p=6669 (search snippet of the same review; fetched page confirms Action Center "access to certain controls and more in-depth notifications") |
| Collapsed/expanded | "either your top four picks or all your available quick actions", expand/collapse | [W10M] | https://www.tenforums.com/tutorials/47403-action-center-quick-actions-add-remove-windows-10-mobile.html (search snippet; page 403) |
| Customisation | add/remove/re-arrange from build 14322, Settings > System > Notifications & actions | [W10M] | same |
| Per-app limits | choose "how many messages each application can show in Action Center" and a priority per app | [W10M AU] | http://allaboutwindowsphone.com/features/item/21605_Windows_10_Mobile_whats_new_fr.php |
| Notification list | grouped by app; "swipe right to clear notifications"; WP8.1 had "four customisable boxes at the top"; W10 added actionable notifications | [WP8.1]/[W10M] | https://en.wikipedia.org/wiki/Action_Center |
| Grid rows in expanded state, tile size, spacing, "Clear all" placement | NOT FOUND (searched: "Windows 10 Mobile action center quick actions expanded rows", "action center clear all Windows 10 Mobile layout") | [W10M] | — |

---

## 8. Volume panel and glance screen

### 8.1 Volume

| Item | Value | Scope | Source |
|---|---|---|---|
| Position | "appear at the top of the screen"; on top of playback controls when audio plays | [WP8.1] | https://www.thurrott.com/mobile/windows-phone/3675/windows-phone-tip-master-the-custom-volume-settings |
| Sliders | Ringer + notifications 1–10; Apps + games 1–30 revealed by a "little caret control" | [WP8.1] | same |
| Vibrate | toggle "in the lower left of the volume notification" | [WP8.1] | same |
| W10M behaviour | ringer/notifications slider with "a down arrow which can also show the apps volume slider"; apps slider "only appears if there's actually an app giving sound output" (build 14393.351) | [W10M] | https://forums.windowscentral.com/threads/apps-volume-slider-missing.444677/ |
| Panel height / colours | NOT FOUND | [W10M] | — |

### 8.2 Glance

| Item | Value | Scope | Source |
|---|---|---|---|
| Content | time and date, missed calls, emails, calendar entries, weather, lock-screen photo, health/fitness | [WP8.1] | https://blogs.windows.com/devices/2015/01/26/lumia-screens-explained-glance-lock-start-app-list/ |
| W10M look | "the time and date in large, white text on the black background" | [W10M] | https://www.phonescoop.com/articles/article.php?a=16968&p=6669 |
| Options | date, notification icons; modes off / 30 s (proximity) / 15 min / always on; night mode red, green or blue | [W10M] | https://www.windowslatest.com/2016/02/11/how-to-use-glance-screen-on-lumia-phones/ |
| Night mode (Lumia) | clock red instead of white; charging indicator, vibrate "wavy line", silent "struck-through bell" icons | [WP8] | https://allaboutwindowsphone.com/features/item/17736_Nokia_Glance_Screen_and_displa.php |
| Clock position / font size | NOT FOUND (searched: "glance screen clock top left", "Windows 10 Mobile glance screen layout") | — | — |

---

## 9. Cortana persona (ring) states

| Item | Value | Scope | Source |
|---|---|---|---|
| Form | "two nested circles, which were animated to indicate activities such as searching or talking"; "black or white background and shades of blue for the respective circles" | [WP8.1]/[W10M] | https://en.wikipedia.org/wiki/Cortana_(virtual_assistant) |
| WP8.1 persona set | CIRCLE_CALM1, LISTENING1, THINKING1, SPEAKING1, ALERT1, BOUNCY1, ELATED1, OPTIMISTIC1, SATISFIED1, SENSITIVE1, ABASHED1, CONSIDERATE1, NEEDMORE1, REMINDER1, GREETING1–4, AUDIO1, OOBEINTRO1 (+ weather/flight/train/package + CLIPPY easter eggs); stored as animated GIFs with XML headers in `PersonaAssets720x1280.dll` | [WP8.1] | https://leonzandman.com/2014/04/22/hacking-cortana-meet-all-of-cortanas-personas/ |
| Emotion visuals | Speaking = "light inner halo and a dark outer halo"; Optimistic = "lighter ring outside a darker ring"; Elated = "inner halo is a bit narrower at the top"; Calm = steady halo; most states are light-inner/dark-outer or the reverse | [WP8.1] | https://www.pcworld.com/article/431767/cortanas-ui-now-expresses-18-different-emotions-siri-remains-detached-and-aloof.html |
| W10M listening | pre-14356: "a bunch of letters that change quickly as if the computer is deciphering each word"; from build 14356: "a miniature audio waveform" | [W10M] | https://pcworld.com/article/3078162/windows/new-windows-10-mobile-preview-brings-phone-notifications-to-your-pc.html |
| W10M idle | users report "only a slight 'throbbing' animation" vs WP8.1's "bubbly and full of life"; Cortana live tile static | [W10M] | https://forums.windowscentral.com/windows-10/399166-cortana-animations-windows-10-mobile-live-tile-nonexistent.html |
| Colour | animation colour follows the theme/accent colour | [W10M] | https://learn.microsoft.com/en-us/answers/questions/2781894/cortana-animations (search snippet) |
| Ring radii, stroke, frame timings for idle/listening/thinking/speaking | NOT FOUND (searched: "Cortana circle animation listening thinking ring", "Cortana thinking animation dots spin", "Cortana persona animation frames"). The WP8.1 GIFs in PersonaAssets720x1280.dll are the only primary artefacts identified | — | — |

---

## 10. Icons

| Item | Value | Source |
|---|---|---|
| Segoe MDL2 Assets — license | No open license. Font-list page gives only "Licensing and redistribution info → Font redistribution FAQ for Windows / License Microsoft fonts (fonts.com) for … redistribution"; © 2019 Microsoft, file Segmdl2.ttf, shipped with Windows 10 from 1507 (2015-07-29). Design page: pre-installed on Windows, Mac users download via aka.ms/SegoeFonts; glyphs are fixed-width, consistent height/left origin, PUA code points, not for inline text | https://learn.microsoft.com/en-us/typography/font-list/segoe-mdl2-assets ; https://learn.microsoft.com/en-us/windows/apps/design/style/segoe-ui-symbol-font |
| Conclusion | Treat MDL2 as proprietary; do not bundle in the APK without a Microsoft font license. (The downloadable font's EULA text itself: NOT FOUND — aka.ms redirect not fetched.) | — |
| Fluent UI System Icons — license | MIT ("Permission is hereby granted, free of charge … to deal in the Software without restriction … The above copyright notice and this permission notice shall be included …") | https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/LICENSE |
| Sizes / styles | 12, 16, 20, 24, 28, 32, 48 px; `regular` and `filled` at every size, `light` at 32 (e.g. ic_fluent_add_32_light.svg) | https://api.github.com/repos/microsoft/fluentui-system-icons/contents/assets/Add/SVG |
| Android artifact | `implementation 'com.microsoft.design:fluent-system-icons:1.1.341@aar'` (Maven Central) | https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/README.md |
| Style closeness to MDL2 | No lineage statement in the repo (NOT FOUND). Observable difference from sources: MDL2 is a single fixed-width monoline symbol font drawn for PUA glyph layering (segoe-ui-symbol-font page), Fluent is per-size outlined SVGs with regular/filled pairs (API listing). Treat Fluent `regular` as the nearest open substitute; expect stroke-weight and metaphor differences | — |

---

## 11. Units: epx → dp, and scaling to the S25 Ultra

| Item | Value | Scope | Source |
|---|---|---|---|
| epx definition | "Effective pixels (epx) are a virtual unit … independent of screen density"; sizes/margins/positions in multiples of 4 epx; scale plateaus 100/125/150/175/200/225/250/300/350/400% | [UWP] | https://learn.microsoft.com/en-us/windows/apps/design/layout/screen-sizes-and-breakpoints-for-responsive-design |
| Phone effective sizes | 4"–6" phones: 320x569, 360x640, 480x854 epx; 12 px side margins; single column | [UWP] | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/layout/screen-sizes-and-breakpoints-for-responsive-design.md |
| ResolutionScale enum | 100,120*,125,140,150,160,175,180,200,225*,250,300,350,400,450,500 (*unused); recommended asset sets: UWP 100/140/180; WP8.1+ apps 100/140/240; WP8 100/150/160 | [UWP] | https://learn.microsoft.com/en-us/uwp/api/windows.graphics.display.resolutionscale |
| Lumia 950 default scale | 400% ("change the font size from the default 400%") | [W10M] | https://forums.windowscentral.com/windows-10-mobile/418502-screen-scaling-issue-950-there-fix.html |
| Lumia 950 panel | 5.2", 1440x2560, 564 ppi | [W10M] | https://en.wikipedia.org/wiki/Microsoft_Lumia_950 (search snippet) |
| 950 effective resolution | 1440/4 = 360 epx × 2560/4 = 640 epx, matching the documented 360x640 phone size | [derived] | from the two rows above |

Conversion guidance:

1. Android `dp` = px ÷ (`DisplayMetrics.density`); 1 dp = 1/160 in at the device's *reported* density. Read `density` at runtime; do not hardcode the S25 Ultra's value (not sourced here).
2. Physical size of 1 epx on the Lumia 950 = 4 px ÷ 564 ppi = 0.00709 in ≈ **1.135 dp** [derived]. So a W10M layout reproduced 1 epx = 1 dp comes out ~12% smaller than on a 950; 1 epx = 1.135 dp reproduces the 950's physical sizes.
3. S25 Ultra at 1440x3120 / 6.9": ppi = √(1440² + 3120²) ÷ 6.9 ≈ 498 [derived from brief]. Options:
   - **Option A — same epx grid as the 950 (360 epx wide):** 1 epx = 4 px → 1 epx = 4/498 in = 0.00803 in (≈13% larger than on the 950). Start grid gets 360x780 epx.
   - **Option B — same physical size as the 950:** 1 epx = 4 × 498/564 = 3.53 px → 408x884 epx canvas; the grid keeps 950 physical dimensions and shows more rows.
   Pick one and state it in the design tokens; the 12 px side margin and 4-epx multiples rule above apply in either.
4. Keep tile assets at the 400% scale set (medium 600 px, wide 1240x600, small 284 px per https://learn.microsoft.com/en-us/windows/apps/design/style/iconography/app-icon-construction); at ≥3.5 px/epx that is the only plateau that will not upscale.

---

## Source index (fetched successfully)

- https://learn.microsoft.com/en-us/previous-versions/dn772303(v=vs.85) — WP8.1 Start tile layout (OEM)
- https://learn.microsoft.com/en-us/previous-versions/dn772323(v=vs.85) — WP8.1 themes and accent colors
- https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh202948(v=vs.105) — WP8 tiles
- https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj206971(v=vs.105) — WP8 flip tile
- https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj662924(v=vs.105) — WP8 iconic tile guidelines
- https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj662929(v=vs.105) — WP8 tile design guidelines
- https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff769552(v=vs.105) — WP8 theme resources
- https://learn.microsoft.com/en-us/windows/apps/design/style/iconography/app-icon-construction — UWP tile/icon sizes
- https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles — adaptive tiles
- https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/badges — badges
- https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/shell/tiles-and-notifications/app-assets.md — icon padding, margins
- https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/controls-and-patterns/xaml-theme-resources.md — Win10 type ramp, theme colors
- https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/style/typography.md — line-height rule
- https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/style/color.md — 48 accents
- https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/layout/screen-sizes-and-breakpoints-for-responsive-design.md — phone epx sizes
- https://learn.microsoft.com/en-us/windows/apps/design/layout/screen-sizes-and-breakpoints-for-responsive-design — epx, multiples of 4
- https://learn.microsoft.com/en-us/uwp/api/windows.graphics.display.resolutionscale — scale enum
- https://learn.microsoft.com/en-us/windows/apps/design/motion/xaml-animation ; …/page-transitions ; …/timing-and-easing ; …/motion-in-practice ; …/connected-animation — motion
- https://learn.microsoft.com/en-us/uwp/api/windows.ui.xaml.media.animation.navigationthemetransition ; …commonnavigationtransitioninfo ; …continuumnavigationtransitioninfo ; …pointerdownthemeanimation — APIs
- https://raw.githubusercontent.com/microsoftarchive/WindowsPhoneToolkit/master/Microsoft.Phone.Controls.Toolkit/Transitions/Storyboards/Turnstile{ForwardIn,ForwardOut,BackwardIn,BackwardOut}.xaml — turnstile
- https://raw.githubusercontent.com/timheuer/callisto/master/src/Callisto/Effects/TiltEffect.cs ; https://learn.microsoft.com/en-us/archive/blogs/ptorr/tilt-effect-for-windows-phone-controls — tilt
- https://learn.microsoft.com/en-us/archive/blogs/wushuai/tips-to-handle-live-tile-update-in-background-task — tile choreography
- https://www.c-sharpcorner.com/UploadFile/6d1860/utilizing-page-transition-animations-in-windows-phone-8-1-ap/ — WP8.1 transitions
- https://www.thurrott.com/mobile/windows-phone/4722/windows-10-mobile-tip-customize-start ; https://www.thurrott.com/mobile/windows-phone/3675/windows-phone-tip-master-the-custom-volume-settings
- https://www.phonescoop.com/articles/article.php?a=16968&p=6669 ; https://www.phonescoop.com/articles/article.php?a=13917&p=6047
- https://forums.windowscentral.com/windows-10-mobile/418502-screen-scaling-issue-950-there-fix.html ; https://forums.windowscentral.com/threads/apps-volume-slider-missing.444677/ ; https://forums.windowscentral.com/windows-10/399166-cortana-animations-windows-10-mobile-live-tile-nonexistent.html ; https://forums.windowscentral.com/ask-question/304854-why-dont-i-see-list-apps-when-swiping-right.html
- https://blogs.windows.com/devices/2015/01/26/lumia-screens-explained-glance-lock-start-app-list/ ; https://blogs.windows.com/devices/2014/04/29/deep-dive-windows-phone-8-1-start-screen-backgrounds/
- https://en.wikipedia.org/wiki/Action_Center ; https://en.wikipedia.org/wiki/Cortana_(virtual_assistant)
- https://leonzandman.com/2014/04/22/hacking-cortana-meet-all-of-cortanas-personas/ ; https://www.pcworld.com/article/431767/… ; https://pcworld.com/article/3078162/…
- https://www.windowslatest.com/2016/02/11/how-to-use-glance-screen-on-lumia-phones/ ; https://allaboutwindowsphone.com/features/item/17736_Nokia_Glance_Screen_and_displa.php ; http://allaboutwindowsphone.com/features/item/21605_Windows_10_Mobile_whats_new_fr.php
- http://bsubramanyamraju.blogspot.com/2014/03/ui-design-guidelines-for-windows-phone-8.html — WP8 12 px grid
- https://raw.githubusercontent.com/microsoft/Selawik/master/LICENSE.txt ; https://raw.githubusercontent.com/microsoft/Selawik/master/README.md ; https://learn.microsoft.com/en-us/windows/apps/design/style/typography
- https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/LICENSE ; https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/README.md ; https://api.github.com/repos/microsoft/fluentui-system-icons/contents/assets/Add/SVG
- https://learn.microsoft.com/en-us/typography/font-list/segoe-mdl2-assets ; https://learn.microsoft.com/en-us/windows/apps/design/style/segoe-ui-symbol-font
- https://jmacthefatcat.github.io/win-10-colours/ — accent names
