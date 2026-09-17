# R5 — Live tile counts and a public Live Tile API

Date: 2026-09-16. Scope: phase 01 (`docs/plan/phase-01-start-live-tiles.md`), tile templates per `docs/plan/w10m-reference.md` §2.
Owner ruling (2026-09-16): tile counts come from notifications for every app, PLUS real unread counts wherever Android/Samsung exposes them, AND the shell offers an API so apps can update their own tiles, modelled on Windows.

Conventions:
- Every factual claim cites a URL or a repo file path (`<repo> <path>:<line>`; line numbers are as of 2026-09-16 on the branch named).
- "not found in searches: …" = the search terms tried returned nothing usable; it is not a claim that the thing does not exist.
- "UNVERIFIED; QA on phone" = behaviour on One UI 8 / Android 16 on the S25 Ultra that no fetched source settles; the QA step to settle it is given inline.
- Scope tags as in R1: [UWP] Windows 10 shared docs, [W10M] phone-specific statement, [WP8.1] Windows Phone 8.1.

Fetch notes: `developer.android.com/reference/*` pages returned navigation only through the fetch tool, so Android API wording is quoted from the framework source on `android.googlesource.com` (`platform/frameworks/base`, branch `main`) instead. `forum.developer.samsung.com` (JS-rendered) and `eu.community.samsung.com` (403) could not be read. `github.com` issue pages returned only the opening post.

---

## 0. Summary

- Windows' model is: **TileUpdater** (Update / Clear / 5-deep queue with Tag + ExpirationTime / scheduled / periodic URL polling) + **BadgeUpdater** (1–99, "99+", 0 clears, fixed glyph set) + an **adaptive XML payload with one `<binding>` per size** + **secondary tiles** the user must confirm + **WNS push** from a server + **background tasks** (15-minute timer floor). §1 has every source; §1.12 says what W10M actually rendered.
- On the S25 Ultra the only real-unread-count sources that are verifiable from docs/source today are: (1) `Notification.number` read through the shell's `NotificationListenerService` (this is exactly what AOSP Launcher3 does), and (2) legacy badge broadcasts (`android.intent.action.BADGE_COUNT_UPDATE` and ShortcutBadger's Oreo action) — but only when the sender's discovery step finds a **manifest-declared** receiver in the shell. Samsung's `content://com.sec.badge/apps` is still shipped but its read-permission level is not documented anywhere fetched: UNVERIFIED; QA on phone. Nova's TeslaUnread is dead (removed from Nova 7, 2021). §2.
- Android precedents for "app pushes content into a host": DashClock's bound-service extension API (Apache-2.0), Kvaesitso's ContentProvider plugin SDK (Apache-2.0 SDK / GPL-3 app), TeslaUnread's ContentProvider with UID verification, Kustom's unauthenticated broadcast. AppWidgets deliver opaque RemoteViews and are a visual, not a data, source. Samsung's Edge SDK ended 2023-12-05. §3.
- Recommendation (§4): one exported `ContentProvider` in the shell (`<applicationId>.livetile`) whose `call()` verbs map 1:1 onto TileUpdater/BadgeUpdater/ScheduledTileNotification/SecondaryTile, authorised by Binder identity (an app may only touch tiles whose owner package is the caller), payload = the adaptive tile XML subset W10M rendered, images by content-URI grant copied at update time. Count precedence: API badge > legacy badge broadcast > Samsung provider (if readable) > notification-derived; never summed. Left out: periodic URL polling, WNS push, toast/raw, TileLarge, lock detailed status, DashClock adapter.

---

## 1. How Windows did it

### 1.1 Object model

`TileUpdateManager.CreateTileUpdaterForApplication()` / `CreateTileUpdaterForSecondaryTile(tileId)` return a `TileUpdater` that "is bound to a specific app or secondary tile, so the methods of this class affect only the single tile that the object instance is bound to." [UWP] https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.tileupdater — Requirements: "Windows 10 (introduced in 10.0.10240.0)", "Windows.Foundation.UniversalApiContract (introduced in v1.0)" (same page), i.e. present on W10M builds 10586/14393.

`TileUpdater` members (all from the page above, descriptions quoted):

| Member | Description |
|---|---|
| `Update(TileNotification)` | "Applies a change in content or appearance to the tile." |
| `Clear()` | "Removes all updates and causes the tile to display its default content as declared in the app's manifest." |
| `EnableNotificationQueue(Boolean)` | "Enables the tile to queue up to five notifications. This enables the notification queue on all tile sizes." |
| `EnableNotificationQueueForSquare150x150` / `…ForWide310x150` / `…ForSquare310x310` | per-size variants of the same, "up to five notifications" each |
| `AddToSchedule(ScheduledTileNotification)` / `RemoveFromSchedule(...)` / `GetScheduledTileNotifications()` | "Adds a ScheduledTileNotification to the schedule." / "Removes an upcoming tile update from the schedule." / "Retrieves a list of scheduled updates to the tile." |
| `StartPeriodicUpdate(Uri, PeriodicUpdateRecurrence)` and `(Uri, DateTime, PeriodicUpdateRecurrence)` | "Begins a series of timed content changes for the tile … Update content is retrieved from a specified Uniform Resource Identifier (URI)." |
| `StartPeriodicUpdateBatch(IIterable<Uri>, [DateTime], PeriodicUpdateRecurrence)` | "cycle on the tile … retrieved from an array of specified URI … Note: To use this feature, you must first enable the tile's notification queue by calling EnableNotificationQueue." |
| `StopPeriodicUpdate()` | "Cancels the current series of timed updates for the tile that the updater is bound to." |
| `Setting` (property) | "Gets a value that specifies whether a tile can be updated through notifications." |

`Setting` returns `NotificationSetting`: `Enabled` (0) "All notifications raised by this app can be displayed"; `DisabledForApplication` (1) "The user has disabled notifications for this app"; `DisabledForUser` (2); `DisabledByGroupPolicy` (3); `DisabledByManifest` (4). https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.notificationsetting

### 1.2 TileNotification: content, Tag, ExpirationTime

`TileNotification(XmlDocument)`; properties: `Content` ("the XML description of the notification content"), `ExpirationTime` ("Gets or sets the time that Windows will remove the notification from the tile. By default, a tile update does not expire. It is a best practice to explicitly set an expiration time to avoid stale content."), `Tag` ("Gets or sets a string that Windows can use to prevent duplicate notification content from appearing in the queue."). https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.tilenotification

Expiry defaults by delivery method: "By default, local tile and badge notifications don't expire, while push, periodic, and scheduled notifications expire after three days." https://learn.microsoft.com/en-us/windows/uwp/launch-resume/sending-a-local-tile-notification — and "toasts have a max of three days" https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/choosing-a-notification-delivery-method

`Clear()` semantics: "For a tile with the notification queue enabled and notifications in the queue, calling the Clear method empties the queue. You can't, however, clear a notification via your app's server; only the local app code can clear notifications." and "Scheduled notifications that haven't yet appeared are not cleared by this method." (sending-a-local-tile-notification, above). Unpinned primary tiles still accept updates: "Regardless of whether it's visible, your app's primary tile always exists, so you can send notifications to it even when it's not pinned. If the user pins your primary tile later, the notifications that you sent will appear then." (same page).

### 1.3 The notification queue

"When you enable cycling, up to five notifications are maintained in a queue and the tile cycles through them. If the queue has reached its capacity of five notifications, the next new notification replaces the oldest notification in the queue. However, by setting tags on your notifications, you can affect the queue's replacement policy. A tag is an app-specific, case-insensitive string of up to 16 alphanumeric characters … If a match is found, the new notification replaces the queued notification with the same tag. If no match is found, the default replacement rule is applied and the new notification replaces the oldest notification in the queue." https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/periodic-notification-overview — "The call to enable the queue needs to be done only once in your app's lifetime, but there is no harm in calling it each time your app is launched." (same page). Display timing/order of queued notifications is not app-controllable (R1 §2, `docs/plan/w10m-reference.md` "Notification queue" row).

[WP8.1] "you just call EnableNotificationQueue with true and add tiles in a series … On the downside only 5 tiles can be queued compared to 9 before with the Cyclic tile … The queue works as expected that first in is first out … It is possible to replace a specific tile in the queue if it has been added with a Tag." https://learn.microsoft.com/en-us/archive/blogs/thunbrynt/windows-phone-8-1-for-developerslive-tiles

### 1.4 Scheduled tile notifications

`ScheduledTileNotification(XmlDocument, DateTime)`: "Defines the visual content and timing for a single, non-recurring scheduled update to a tile." Properties `Content`, `DeliveryTime` ("the time at which the tile is scheduled to be updated"), `ExpirationTime` ("By default, a tile notification does not expire"), `Id` ("the unique ID that is used to identify the scheduled tile in the schedule"), `Tag`. https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.scheduledtilenotification — "scheduled notifications cannot be used for badge notifications"; "By default, scheduled notifications expire three days from the time they are delivered. You can override this default expiration time on scheduled tile notifications" (choosing-a-notification-delivery-method, above). Limit on the number of scheduled notifications: not found on the API page.

### 1.5 Periodic (polled) updates

"At each polling interval, such as once an hour, Windows sends an HTTP GET request to the URI, downloads the requested tile or badge content (as XML) that is supplied in response to the request, and displays the content on the app's tile." "Any valid HTTP or HTTPS web address can be used." "Polling continues until you explicitly stop it (with TileUpdater.StopPeriodicUpdate), your app is uninstalled, or, in the case of a secondary tile, the tile is removed. Otherwise, Windows continues to poll for updates to your tile or badge even if your app is never launched again." "The requested poll interval can be delayed by up to 15 minutes at the discretion of Windows." "If you provide a start time, the first call to the method polls for content immediately. Then, regular polling starts within 15 minutes of the provided start time." "By default, periodic tile and badge notifications expire three days from the time they are downloaded." Expiry header `X-WNS-Expires` (HTTP-date), tag header `X-WNS-Tag`. Batch: "you can provide up to five URIs at once for use with the notification queue. Each URI is polled for a single notification payload … Each polled URI can return its own expiration and tag value." "periodic updates cannot be used with toast notifications." https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/periodic-notification-overview

Recurrence enum `PeriodicUpdateRecurrence`: `HalfHour`=0 "Poll every half an hour", `Hour`=1, `SixHours`=2, `TwelveHours`=3, `Daily`=4; "Windows can delay the polling of your URL by up to 15 minutes if necessary to optimize power and performance." https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.periodicupdaterecurrence — `BadgeUpdater.StartPeriodicUpdate` "Note that only web resources (http/https) are allowed in a periodic update." https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.badgeupdater

### 1.6 BadgeUpdateManager / BadgeUpdater

`BadgeUpdateManager.CreateBadgeUpdaterForApplication()` / `CreateBadgeUpdaterForSecondaryTile(tileId)`; `BadgeUpdater` members: `Update(BadgeNotification)` "Applies a change to the badge's glyph or number.", `Clear()` "Removes the badge from the tile", `StartPeriodicUpdate(Uri, [DateTime], PeriodicUpdateRecurrence)`, `StopPeriodicUpdate()`. Requirements: Windows 10 10.0.10240.0, UniversalApiContract v1. https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.badgeupdater

Values: "They can be numeric (1-99) or one of a set of system-provided glyphs." "A number from 1 to 99. A value of 0 is equivalent to the glyph value "none" and will clear the badge." "Any number greater than 99" renders the 99+ image (`<badge value="100"/>`). "You cannot provide your own badge image; only system-provided badge images can be used." Glyphs: `none, activity, alarm, alert, attention, available, away, busy, error, newMessage, paused, playing, unavailable`. XML: `<badge value="1"/>`, `<badge value="alert"/>`. "Badges can be displayed on all tile sizes." https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/badges — "Any value greater than 99 displays "99+" instead of the actual number." https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh779719(v=win.10)

Phone history: [WP8.1] "On Windows Phone 8.1, they appear in the upper-right corner of the tile" and "As of Windows Phone 8.1, the available glyphs for use with Windows Phone are … none, alert, attention … If you send one of the other Windows-supported glyphs, the badge on the phone, if showing, will clear." (hh779719, above). [W10M] "In Windows 10, badges on live tiles are displayed in the same location, and they support the same badge glyphs on both phone and desktop (phone gained new glyphs that desktop always had)." "Badges are now displayed in the lower right on both Phone and Desktop, and all the badges from Desktop are supported on Phone" https://learn.microsoft.com/en-us/archive/blogs/tiles_and_toasts/whats-new-with-live-tiles-in-windows-10 (2015-07-06).

### 1.7 Adaptive tile XML schema

Root: `<tile><visual>…<binding template="TileSmall|TileMedium|TileWide|TileLarge">…</binding></visual></tile>`; "Content for each tile size is individually specified in separate TileBinding elements". https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/create-adaptive-tiles

Attributes (from https://learn.microsoft.com/en-us/windows/uwp/launch-resume/tile-schema unless noted):

| Element | Attribute | Values / notes |
|---|---|---|
| visual | `branding` | `none`, `name`, `logo`, `nameAndLogo`; "If you don't specify the branding … If the base tile shows the display name, then the branding will default to name. Otherwise … none" (create-adaptive-tiles). [W10M] `nameAndLogo` "is desktop-specific. Mobile does not even support showing the corner logo at all, and will default to "name"." (whats-new blog) |
| visual / binding | `displayName` | "override the tile's display name while showing this notification" |
| visual / binding | `arguments` | "New in Anniversary Update: App-defined data that is passed back to your app via the TileActivatedInfo property on LaunchActivatedEventArgs when the user launches your app from the Live Tile … On devices without the Anniversary Update, this will simply be ignored." |
| visual | `hint-lockDetailedStatus1..3` | "If you specify this, you must also provide a TileWide binding. This is the first line of text that will be displayed on the lock screen if the user has selected your tile as their detailed status app." (W10M rendering: not found in searches: "Windows 10 Mobile lock detailed status tile", "hint-lockDetailedStatus phone") |
| visual / binding / image | `baseUri`, `addImageQuery` | base URL for relative image sources; append `?ms-scale=…&ms-contrast=…&ms-lang=…` |
| visual / binding / text | `lang` | BCP-47 |
| binding | `template` | `TileSmall`, `TileMedium`, `TileWide`, `TileLarge` |
| binding | `hint-textStacking` | `top`, `center`, `bottom` (also on subgroup) |
| binding | `hint-presentation` | `photos`, `people`, `contact` (§1.8) |
| binding | `hint-overlay` | (legacy form; per-image `hint-overlay` is the documented one below) |
| text | `hint-style` | `caption 12 epx Regular, body 15 Regular, base 15 Semibold, subtitle 20 Regular, title 24 Semilight, subheader 34 Light, header 46 Light`; `*Numeral` variants "reduce the line height"; `*Subtle` variants "60% opacity"; "The style defaults to caption if hint-style isn't specified." (create-adaptive-tiles) |
| text | `hint-wrap`, `hint-maxLines`, `hint-minLines`, `hint-align` | "By default, text doesn't wrap"; align `left`/`center`/`right` |
| image | `src` | "ms-appx, ms-appdata, and http are supported. As of the Fall Creators Update, web images can be up to 3 MB … On devices not yet running the Fall Creators Update, web images must be no larger than 200 KB." |
| image | `placement` | inline (default), `background` ("full bleed"), `peek` ("animates in from the top of the Tile") |
| image | `hint-crop` | `none`, `circle` (inline, background and peek; background/peek crop "New in 1511") |
| image | `hint-overlay` | 0–100 black overlay; background "defaults to 20% overlay as long as you have some text elements in your payload (otherwise … 0%)"; peek overlay "Starting in Windows 10 version 1511 … default overlay for peek images is 0" (create-adaptive-tiles) |
| image | `hint-removeMargin`, `hint-align`, `alt` | "By default, inline images have an 8-pixel margin"; align shows native resolution |
| group / subgroup | `hint-weight` | star-weighted columns; "An 8-pixel margin is automatically added between the columns"; "The only valid child of a group is a subgroup." |

Peek: "The peek image uses an animation to slide down/up from the top of the tile, peeking into view, and then later sliding back out to reveal the main content on the tile." Both peek and background may be set together. (create-adaptive-tiles)

### 1.8 Special templates

- Iconic: `TileSquare150x150IconWithBadge` / `TileSquare71x71IconWithBadge` with `<image id="1" src=…/>`; "The number next to the icon is achieved through a separate badge notification." "At minimum, to support both Desktop and Mobile, Small and Medium tiles, provide a square aspect ratio image with a resolution of 200x200, PNG format, with transparency and no color other than white." (tile-schema) "Windows 10 on tablet, laptop, and desktop only supports square icon assets." https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/special-tile-templates-catalog
- Photos: `hint-presentation="photos"`, "zoom and cross-fade animation that cycles through selected photos and loops indefinitely", "Windows displays up to 12 photos"; "supported on all tile sizes, including small" (special-tile-templates-catalog). [W10M] "Up to 12 images can be provided (Mobile will only display up to 9)" (tile-schema).
- People: `hint-presentation="people"`, "images in circles that slide around vertically or horizontally", "available since Windows 10 Build 10572"; sizes Medium, Wide, "Large tile (desktop only)"; recommended 9 / 15 / 20 photos (special-tile-templates-catalog). "New in 1511: Supported on Medium, Wide, and Large (Desktop and Mobile). Previously this was Mobile-only and only Medium and Wide." (tile-schema)
- Contact: "Mobile-only. Supported on Small, Medium, and Wide." Image + one line of text, "Not displayed on small tile." (tile-schema)

### 1.9 Secondary tiles

"Only users can pin a secondary tile; apps cannot pin secondary tiles programmatically without user approval. The user must explicitly click a "Pin" button within your app, at which point you then use the API to request to create a secondary tile, and then the system displays a dialog box asking the user to confirm." Differences from primary: "Users can delete their secondary tiles at any time", "Secondary tiles can be created at run time", "They are automatically deleted when the app is uninstalled." Updaters: "CreateBadgeUpdaterForApplication vs. CreateBadgeUpdaterForSecondaryTile". https://learn.microsoft.com/en-us/windows/uwp/launch-resume/secondary-tiles

API: `new SecondaryTile(tileId, displayName, arguments, square150x150Logo, TileSize.Default)`; "You MUST provide initialized values for all of the above properties"; `VisualElements.Wide310x150Logo / Square310x310Logo / Square71x71Logo / Square44x44Logo`, `ShowNameOnSquare150x150Logo` etc. ("By default the display name will NOT be shown"); `await tile.RequestCreateAsync()` "must be called from a UI thread. On Desktop, a dialog will appear asking the user to confirm"; `SecondaryTile.Exists(tileId)`; `new SecondaryTile(tileId).RequestDeleteAsync()`; `UpdateAsync()` ("Assign ALL properties, including ones you aren't changing"); `SecondaryTile.FindAllAsync()`. https://learn.microsoft.com/en-us/windows/uwp/launch-resume/secondary-tiles-pinning — Sending to a missing secondary tile throws: "If you try to create a tile updater for a secondary tile that doesn't exist … an exception will be thrown." (sending-a-local-tile-notification). [W10M] "When requesting to pin a secondary tile, the request is now done without any user or system interaction … This is a change from phone, which used to exit your app and bring the user to their Start screen." (whats-new blog). [WP8.1] "when a secondary tile is clicked the activation argument string can be retrieved" (thunbrynt blog).

### 1.10 WNS push path

Flow: "1. Your app requests a push notification channel from WNS … 3. The notification channel URI is returned by WNS to your app. 4. Your app sends the URI to your own cloud service … 5. When your cloud service has an update to send, it notifies WNS using the channel URI. This is done by issuing an HTTP POST request, including the notification payload, over Secure Sockets Layer (SSL). This step requires authentication." Channel via `CreatePushNotificationChannelForApplicationAsync`; "channel URIs expire after 30 days"; cloud service auth = OAuth 2.0 client credentials with "Package security identifier (SID) and a secret key" from the Store dashboard against `https://login.live.com/accesstoken.srf`; POST headers `Content-Type: text/xml`, `X-WNS-Type: wns/tile` (also `wns/badge`, `wns/toast`, `wns/raw`), `Authorization: Bearer …`; "When the device is offline, by default WNS will store one of each notification type (tile, badge, toast) for each channel URI and no raw notifications." Expiry: "tile and badge notifications expire three days after being downloaded … setting the X-WNS-TTL HTTP header". Frequency guidance "at most one every 30 minutes" for generic updates. https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/windows-push-notification-services--wns--overview
[W10M] Battery saver: "When battery saver is on, the receipt of push notifications is disabled to save energy." The "Allow push notifications from any app while in battery saver" setting "applies only to Windows 10 for desktop editions" (same page) — i.e. on phone only the per-app "Always allowed" list exempted push.

### 1.11 Background tasks

"The built-in timer for Universal Windows Platform (UWP) apps that target the desktop or mobile device family runs background tasks in 15-minute intervals." "If FreshnessTime is set to less than 15 minutes, an exception is thrown". "A background task will only run using a TimeTrigger if you have called RequestAccessAsync first." https://learn.microsoft.com/en-us/windows/uwp/launch-resume/run-a-background-task-on-a-timer- — Triggers: TimeTrigger ("as frequently as every 15 minutes"), PushNotificationTrigger ("to receive raw push notifications"), ControlChannelTrigger ("not supported on Windows Phone"), SystemTrigger types, MaintenanceTrigger ("only run when the device is plugged in"); "Background tasks are limited to 30 seconds of wall-clock usage."; in-process tasks since 1607; "IsNetworkRequested … even if the device has entered Connected Standby mode … (for example, when a phone's screen is turned off.)"; Battery Saver "will prevent background tasks from running" unless exempted. https://learn.microsoft.com/en-us/windows/uwp/launch-resume/support-your-app-with-background-tasks — Periodic-URI apps were told to "add a daily time trigger background task which calls StartPeriodicUpdate with the new URI" after app updates (periodic-notification-overview). `XamlRenderingBackgroundTask` "used to be phone-specific … allows you to render XAML trees as a bitmap from a background task, often to generate custom tiles" (whats-new blog).

### 1.12 What W10M actually rendered on phones

| Feature | On W10M | Source |
|---|---|---|
| Adaptive templates (`<binding>` per size) | Yes — UWP contract v1; "The final appearance of a notification should be based on the specific device on which it will appear, whether it's phone, tablet, or desktop" | create-adaptive-tiles; tileupdater requirements |
| Sizes | small, medium, wide; no large in shipping W10M | R1 §1.1 (`docs/plan/w10m-reference.md`); [WP8.1] "large tile (310x310) is not available in Windows Phone 8.1 and … will be ignored" (thunbrynt blog); People template "Large tile (desktop only)" |
| Badge position / glyphs | lower-right; all 12 glyphs + numeric 1–99 / 99+ | whats-new blog (2015-07-06) |
| Branding | `name` or `none`; no corner logo; `nameAndLogo` desktop-only | whats-new blog |
| Peek / background image / hint-overlay / circle crop | No phone-specific statement found (searches: "Windows 10 Mobile peek image tile", "hint-overlay phone"); treat as rendered, R3 footage decides | create-adaptive-tiles |
| Photos template | up to 9 images on mobile (12 desktop), all sizes | tile-schema; special-tile-templates-catalog |
| People template | Medium + Wide on mobile; Large desktop-only | tile-schema; special-tile-templates-catalog |
| Contact template | mobile-only, small/medium/wide | tile-schema |
| Iconic template | small + medium, desktop and mobile | tile-schema; special-tile-templates-catalog |
| Notification queue (5, Tag) | Yes since WP8.1 | thunbrynt blog; periodic-notification-overview |
| Scheduled tile notifications | UWP contract v1 (no phone-specific statement found) | scheduledtilenotification API page |
| Periodic URL polling | UWP contract v1; WP8.1 user report of `StartPeriodicUpdateBatch` on phone | periodicupdaterecurrence API page; thunbrynt blog comments (user report) |
| Chaseable tiles (`arguments`) | 1607 (build 14393) and later; ignored before | tile-schema |
| Lock detailed status (`hint-lockDetailedStatus`) | not found in searches (see §1.7) | — |
| Secondary tile pin dialog | Win10: no longer exits the app on phone; desktop shows a dialog; phone dialog wording not found | whats-new blog; secondary-tiles-pinning |
| WNS push | Yes; battery saver blocks unless app is in "Always allowed" | wns-overview |
| Background tasks | 15-minute TimeTrigger floor on "mobile device family"; ControlChannelTrigger not on phone | run-a-background-task-on-a-timer-; support-your-app-with-background-tasks |

---

## 2. Where real unread counts come from on Android today

### 2.1 `Notification.number` (API 26+ as a launcher badge)

Framework javadoc: "The number of events that this notification represents. For example, in a new mail notification, this could be the number of unread messages. The system may or may not use this field to modify the appearance of the notification. Starting with android.os.Build.VERSION_CODES#O, the number may be displayed as a badge icon in Launchers that support badging." `public int number = 0;` — `platform/frameworks/base core/java/android/app/Notification.java:368-376`. `Builder.setNumber(int)`: "Sets the number of items this notification represents. May be displayed as a badge count for Launchers that support badging." (same file :5020-5028). `setBadgeIconType`: "Note: This value might be ignored, for launchers that don't support badge icons." with `BADGE_ICON_NONE = 0` "If this notification is being shown as a badge, always show as a number", `BADGE_ICON_SMALL = 1`, `BADGE_ICON_LARGE = 2` (same file :1839-1854, :4661-4672).

Google's guide: "By default, each notification increments a number displayed on the touch & hold menu. You can override this number by calling setNumber() on the notification". https://developer.android.com/develop/ui/views/notifications/badges

Read path: a `NotificationListenerService` receives `StatusBarNotification` objects; `sbn.getNotification().number` is the field. AOSP Launcher3 reads exactly this: `NotificationKeyData.fromNotification(sbn)` passes `notif.number` as `count`, stored as `Math.max(1, count)` — `platform/packages/apps/Launcher3 src/com/android/launcher3/notification/NotificationKeyData.java:47-60`. `DotInfo` sums the per-notification counts (`mTotalCount += notificationKey.count`), updates in place when "Notification was updated with a new count", and caps at `MAX_COUNT = 999` — `src/com/android/launcher3/dot/DotInfo.java:31, 47-66, 83-85`. So AOSP's semantics: **a notification counts as 1 unless it carries `number`, in which case it counts as `number`; the app's dot count is the sum over its badge-eligible notifications.**

### 2.2 Channel `setShowBadge` and the Ranking filter

`NotificationChannel.setShowBadge(boolean)`: "Sets whether notifications posted to this channel can appear as application icon badges in a Launcher. Only modifiable before the channel is submitted to NotificationManager#createNotificationChannel" — `core/java/android/app/NotificationChannel.java:645-656`; `canShowBadge()`: "Returns whether notifications posted to this channel can appear as badges in a Launcher application. Note that badging may be disabled for other reasons." (:1014-1022). The listener sees the resolved answer through `NotificationListenerService.Ranking.canShowBadge()`: "Returns whether this notification can be displayed as a badge." — `core/java/android/service/notification/NotificationListenerService.java:2105-2112`.

Launcher3's eligibility filter (`NotificationListener.notificationIsValidForUI`, `src/com/android/launcher3/notification/NotificationListener.java:291-316`): drop if `!ranking.canShowBadge()`; drop `FLAG_ONGOING_EVENT` notifications on the legacy `DEFAULT_CHANNEL_ID` ("Miscellaneous") channel; drop group summaries (`FLAG_GROUP_SUMMARY`); drop notifications with neither `EXTRA_TITLE` nor `EXTRA_TEXT`. It also unbinds itself when the system "notification dots" setting is off (`NOTIFICATION_BADGING_URI`, :205-224). Group bookkeeping (`updateGroupKeyIfNecessary`, :257-289) exists "so we can cancel the summary when the last child is canceled".

### 2.3 `NotificationListenerService` on Android 14–16

Declaration: `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` + intent filter `android.service.notification.NotificationListenerService`; optional `default_filter_types` / `disabled_filter_types` meta-data (`conversations|alerting|ongoing|silent`). "The service should wait for the onListenerConnected() event before performing any operations. The requestRebind(ComponentName) method is the only one that is safe to call before onListenerConnected() or after onListenerDisconnected()." "Notification listeners cannot get notification access or be bound by the system on low-RAM devices running Android Q (and below). The system also ignores notification listeners running in a work profile." "From N onward all callbacks are called on the main thread." — `NotificationListenerService.java` class javadoc. `getActiveNotifications()`: "Request the list of outstanding notifications (that is, those that are visible to the current user)." (:1017-1026). `requestUnbind()`: "The service will likely be killed by the system after this call." (:1393-1403); `requestRebind`: "will fail for listeners that have not been granted the permission by the user" (:1362-1368). The Launcher3 listener wraps `getActiveNotifications` in a `SecurityException` catch (:195-203) — the grant can be revoked at any time.

Android 15: "Android will stop untrusted apps that implement a NotificationListenerService from reading unredacted content from notifications where an OTP has been detected." https://developer.android.com/about/versions/15/behavior-changes-all — affects preview text, not `number`. Android 16: no notification-listener change listed; the only broadcast change is "broadcast delivery order using the android:priority attribute … across different processes will not be guaranteed" https://developer.android.com/about/versions/16/behavior-changes-all. Samsung-specific listener throttling on One UI 8: UNVERIFIED; QA on phone (phase-01 P5 already covers reboot / 24 h / Device care).

### 2.4 Samsung's badge provider `content://com.sec.badge/apps`

Protocol (from ShortcutBadger, Apache-2.0): URI `content://com.sec.badge/apps?notify=true`; columns `_id`, `package`, `class`, `badgecount`; the writer queries `package=?`, `update`s each row's `badgecount`, and `insert`s a row if the entry activity has none — `leolin310148/ShortcutBadger ShortcutBadger/src/main/java/me/leolin/shortcutbadger/impl/SamsungHomeBadger.java:16-70`. Permissions declared for it: `com.sec.android.provider.badge.permission.READ` and `.WRITE` — `ShortcutBadger/src/main/AndroidManifest.xml:13-15`. Samsung launcher packages targeted: `com.sec.android.app.launcher`, `com.sec.android.app.twlauncher` (SamsungHomeBadger.java:75-80). Note the library itself prefers the broadcast route on Samsung: on SDK ≥ 21 it constructs a `DefaultBadger` and uses it "if defaultBadger.isSupported(context)" (a manifest receiver for the badge action exists), falling back to the provider only otherwise (SamsungHomeBadger.java:22-31).

Still shipped: Samsung's `BadgeProvider` (package `com.sec.android.provider.badge`) is listed with 2025 releases (2.1.00.13/15/19) https://www.apkmirror.com/apk/samsung-electronics-co-ltd/badgeprovider/ (release listing; version→One UI mapping is APKMirror's, not Samsung's).

Can a third-party app read it on One UI 8? Protection level of `com.sec.android.provider.badge.permission.READ`: not found in searches ("com.sec.android.provider.badge.permission.READ" protectionLevel; Samsung BadgeProvider manifest permission declaration). The only fetched evidence of enforcement is a Xamarin developer on "Samsung phone running Android 10" getting `SecurityException: Permission Denial: writing com.sec.android.provider.badge.BadgeProvider uri content://com.sec.badge/apps … requires com.sec.android.provider.badge.permission.WRITE, or grantUriPermission()` despite `<uses-permission>` for both READ and WRITE in the manifest https://learn.microsoft.com/en-us/answers/questions/242582/how-to-add-permission-to-com-sec-android-provider — consistent with WRITE not being a normal-level permission (a normal-level permission is granted at install just by declaring it), but it says nothing about READ. **UNVERIFIED; QA on phone:**
1. `adb shell dumpsys package com.sec.android.provider.badge` → read the `declared permissions:` block for `prot=` of `…permission.READ` / `…WRITE`, and the provider's `readPermission` / `writePermission`.
2. From the shell APK (with `<uses-permission android:name="com.sec.android.provider.badge.permission.READ"/>`): `contentResolver.query(Uri.parse("content://com.sec.badge/apps"), arrayOf("package","class","badgecount"), null, null, null)` → record `SecurityException` vs rows; also `adb shell content query --uri content://com.sec.badge/apps` (runs as the `shell` uid, so a denial here is expected and only tells you the permission is enforced).
3. Correlate rows with the One UI setting Settings › Notifications › App icon badges › "Show with number" https://www.samsung.com/hk_en/support/mobile-devices/android-o-os-app-icon-can-show-badges-with-numbers-or-dot-style-badges/ — what Samsung's number represents (count of notifications vs app-supplied `number`) is UNVERIFIED; Aqua Mail's support note says only "For Samsung One UI handsets notification badges are not handled by individual apps." https://aquamail.freshdesk.com/support/solutions/articles/77000519592--samsung-app-badge-is-not-correct (a Samsung developer-forum thread on the same question exists but could not be fetched: https://forum.developer.samsung.com/t/badge-count-update-on-samsung-without-active-notification/41039).

### 2.5 Legacy badge broadcasts and whether a Home app still receives them

Protocol: action `android.intent.action.BADGE_COUNT_UPDATE` (and, since ShortcutBadger 1.1.20 "added Android Oreo support", `me.leolin.shortcutbadger.BADGE_COUNT_UPDATE`) — `ShortcutBadger/src/main/java/me/leolin/shortcutbadger/impl/IntentConstants.java:3-6`; extras `badge_count` (int), `badge_count_package_name`, `badge_count_class_name` — `impl/DefaultBadger.java:16-27`. Launchers that declared support for the plain broadcast in the library: KISS (`fr.neamar.kiss`), LaunchTime (DefaultBadger.java:30-37); the whole per-OEM roster (ADW, Apex, HTC, Nova, Sony, ASUS, Huawei, OPPO, Samsung, ZUK, Vivo, ZTE, EverythingMe, Yandex) is in `ShortcutBadger.java:58-74` and the README table. Licence Apache-2.0 (`LICENSE`).

How the sender delivers it — this decides what the shell must declare:
- `BroadcastHelper.sendIntentExplicitly` first calls `PackageManager.queryBroadcastReceivers(intent, 0)` and **throws "unable to resolve intent" if the list is empty** — `util/BroadcastHelper.java:19-33`. `queryBroadcastReceivers` is "Retrieve all receivers that can handle a broadcast of the given intent" from the package manager — `core/java/android/content/pm/PackageManager.java:7961-7973` — i.e. it resolves **manifest-declared** receivers only; a runtime-registered receiver is invisible to it. So a Home app with only a `registerReceiver` listener never gets these broadcasts because the sender gives up before sending.
- For each `ResolveInfo` it sends a copy with `setPackage(info.resolvePackageName)` (BroadcastHelper.java:27-31). `ResolveInfo.resolvePackageName` is "Optional -- if non-null, the labelRes and icon resources will be loaded from this package, rather than the one containing the resolved component." — `core/java/android/content/pm/ResolveInfo.java:155-160` — so it is normally null and the broadcast goes out **implicit**.
- Delivery rules for an implicit broadcast: "Apps that target Android 8.0 or higher can no longer register broadcast receivers for implicit broadcasts in their manifest unless the broadcast is restricted to that app specifically … Apps can use Context.registerReceiver() at runtime to register a receiver for any broadcast, whether implicit or explicit." https://developer.android.com/about/versions/oreo/background — and on target 34+: "Apps and services that target Android 14 (API level 34) or higher and use context-registered receivers are required to specify a flag … either RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED" https://developer.android.com/about/versions/14/behavior-changes-14. Context-registered receivers "receive broadcasts as long as their registering context is valid … If you register with the Application context, you receive broadcasts as long as the app runs." https://developer.android.com/develop/background-work/background-tasks/broadcasts

Consequence for the shell (targetSdk 36): declare **both** an exported manifest receiver for the two actions (so senders' `queryBroadcastReceivers` succeeds, and so explicit-by-package sends reach it) **and** an Application-context `registerReceiver(..., RECEIVER_EXPORTED)` (which is what actually receives the implicit copy). Sender identity is available since API 34: `BroadcastReceiver.getSentFromUid()` "Returns the uid of the app that initially sent this broadcast … or Process#INVALID_UID if the current receiver cannot access the identity of the broadcasting app" and `getSentFromPackage()` — `core/java/android/content/BroadcastReceiver.java:718-723` (and the `@hide` PendingResult twins at :343-348). Which copy arrives first, and whether Samsung's own BadgeProvider/One UI Home still declares a receiver for `BADGE_COUNT_UPDATE` (which makes ShortcutBadger senders choose the broadcast over the provider): UNVERIFIED; QA on phone — `adb shell pm query-receivers --brief -a android.intent.action.BADGE_COUNT_UPDATE` and the same for `me.leolin.shortcutbadger.BADGE_COUNT_UPDATE`, before and after the shell is installed.

### 2.6 Nova / TeslaUnread

Protocol: `ContentResolver.insert("content://com.teslacoilsw.notifier/unread_count", {tag = "pkg/activity", count})`; "TeslaUnread can verify the UID of the app inserting an unread count against the desired package name" https://novalauncher.com/api/teslaunread/ ; ShortcutBadger's `NovaHomeBadger.java:14-32` implements it and targets `com.teslacoilsw.launcher`. Status: "As of the release of Nova 7 back in 2021, TeslaUnread support has been completely removed from Nova Launcher and will not be available at any point going forward." "Because of it's use of the READ_SMS and READ_CALL_LOG permissions it is no longer welcome on the Google Play Store" https://novalauncher.com/teslaunread/ . Not a source for this shell; its UID-verification design is reused in §4.

### 2.7 How open-source launchers get counts (read from code)

- **AOSP Launcher3** (Apache-2.0 headers): §2.1–2.2. The count is only shown in the long-press popup and a11y label; the dot itself is count-less (`DotInfo` supplies `getNotificationCount()`; AOSP `BubbleTextView` is not fetched here — Lawnchair's fork below is).
- **Lawnchair 15-dev** (Apache-2.0: `LawnchairLauncher/lawnchair LICENSE.txt` header "Copyright (c) 2024, Lawnchair … Licensed under the Apache License, Version 2.0"): adds a `show_notification_count` preference (`lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt:202-204`) and passes `mDotInfo.getNotificationCount()` into `DotRenderer.draw` so the dot renders the number (`src/com/android/launcher3/BubbleTextView.java:711`, `:1107`). The number is still Launcher3's `Notification.number`-aware sum — no Samsung provider, no badge broadcasts (searched the Lawnchair tree for `BADGE_COUNT`, `com.sec.badge`: no hits in the files fetched; `lawnchair/src/app/lawnchair/NotificationManager.kt` has no count logic).
- **Kvaesitso** (GPL-3 app, `MM2-0/Kvaesitso LICENSE.txt`): no badge counts in the plugin types (weather, files, contacts, places, calendar) https://kvaesitso.mm20.de/docs/developer-guide/plugins/get-started.html — relevant to §3 only.

### 2.8 Which popular apps set `Notification.number` or send badge broadcasts

| App | Sets `setNumber` | Sends badge broadcast / provider | Evidence |
|---|---|---|---|
| Signal (AGPL-3, `signalapp/Signal-Android LICENSE`) | Yes: `setNumber(conversation.messageCount)` and `setNumber(state.messageCount)` — `app/src/main/java/org/thoughtcrime/securesms/notifications/v2/NotificationFactory.kt:253, 297`; forwarded to `builder.setNumber` in `NotificationBuilder.kt:461-462` | Yes: `ShortcutBadger.applyCount(context, count)` / `removeCount` — `DefaultMessageNotifier.kt:14, 309-311` | source |
| Telegram (DrKLO/Telegram) | Yes: `.setNumber(total_unread_count)` — `TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java:4586`; per-dialog `.setNumber(… messageObjects.size())` `:5589` | not found in that file (`ShortcutBadger`, `BADGE_COUNT`: no hits) | source |
| Thunderbird for Android / K-9 (`thunderbird/thunderbird-android`) | Summary notification: `.setNumber(notificationData.additionalMessagesCount)` — `legacy/core/src/main/java/com/fsck/k9/notification/SummaryNotificationCreator.kt:62` (additional messages, not total unread) | no hits in the two files fetched | source |
| Gmail, WhatsApp, Google Messages, Outlook, Slack | UNVERIFIED (closed source; searches: `"BADGE_COUNT_UPDATE" WhatsApp OR Gmail OR Outlook OR Slack decompiled badge broadcast` — nothing usable) | UNVERIFIED | — |

QA probe for the closed-source apps (phase 01, P3): the shell's own listener logs `sbn.packageName`, `notification.number`, `ranking.canShowBadge()` per posted notification, and the legacy receiver logs `getSentFromPackage()` + `badge_count`; a day of real use on the S25 Ultra fills the table above.

---

## 3. Android precedents for apps pushing content to a host

### 3.1 AppWidgets / RemoteViews

Host side: "An AppWidgetHost must have an ID that is unique within the host's own package"; `allocateAppWidgetId()`; `<uses-permission android:name="android.permission.BIND_APPWIDGET" />`; "At runtime, the user must explicitly grant permission to your app to let it add a widget to the host … use the bindAppWidgetIdIfAllowed() method. If bindAppWidgetIdIfAllowed() returns false, your app must display a dialog" via `ACTION_APPWIDGET_BIND` with "allow" / "always allow"; `AppWidgetHostView` is "a frame that the widget is wrapped in whenever it needs to be displayed". https://developer.android.com/develop/ui/views/appwidgets/host
What the host receives: `RemoteViews` is "A class that describes a view hierarchy that can be displayed in another process. The hierarchy is inflated from a layout resource file" and is limited to a fixed set of layouts/widgets (`FrameLayout, GridLayout, GridView, LinearLayout, ListView, RelativeLayout, StackView, ViewFlipper, AdapterViewFlipper` / `AnalogClock, Button, Chronometer, ImageButton, ImageView, ProgressBar, TextClock, TextView`, plus API 31 additions) — `core/java/android/widget/RemoteViews.java` class javadoc. There is no data contract: the host gets a view tree, not fields. A host could inflate it and walk the tree for `TextView` text ("widget scraping") — that is undocumented behaviour of someone else's layout and breaks on any redesign, so it is not a count source. Android 15 also disables all of an app's widgets while the app is force-stopped / in the stopped state https://developer.android.com/about/versions/15/behavior-changes-all. Verdict: a widget can be a *visual* inside a tile (host it in an `AppWidgetHostView`) but not a *data* source for counts or previews. Not in phase 01 scope; noted only so it is not re-litigated.

### 3.2 DashClock extension API (Apache-2.0)

"Extensions are a way for other apps to show additional status information within DashClock widgets"; "An extension is simply a service that the DashClock process binds to"; discovery by intent action `com.google.android.apps.dashclock.Extension`; the service is protected by `com.google.android.apps.dashclock.permission.READ_EXTENSION_DATA` "so that only DashClock can bind to your service and request updates"; `<meta-data>` `protocolVersion` (1 or 2), `description`, `settingsActivity`, `worldReadable` ("will allow other apps besides DashClock to read data for this extension"). Host→extension AIDL: `onInitialize(host, isReconnect)`, `onUpdate(reason)`; extension→host: `publishUpdate(ExtensionData)`, `addWatchContentUris(String[])`, `setUpdateWhenScreenOn(bool)`, `removeAllWatchContentUris()` (v2). Update reasons: `UNKNOWN, INITIAL, PERIODIC` ("roughly once per hour"), `SETTINGS_CHANGED, CONTENT_CHANGED, SCREEN_ON, MANUAL`. `ExtensionData`: `visible, icon / iconUri, status (≤32 chars), expandedTitle (≤100), expandedBody (≤1000), clickIntent, contentDescription`, enforced by `clean()`. — `romannurik/dashclock api/src/main/java/com/google/android/apps/dashclock/api/DashClockExtension.java:37-184`, `ExtensionData.java:87-114, 493-511`, `api/src/main/aidl/com/google/android/apps/dashclock/api/internal/IExtension.aidl`, `IExtensionHost.aidl`. Licence header "Copyright 2013 Google Inc. Licensed under the Apache License, Version 2.0" in every file (root `LICENSE` file: 404 on `master`).
Relevance: the closest Android analogue to a live-tile content contract (bounded text fields + icon + click intent, versioned protocol, permission-gated binding). Its direction is host-pulls-by-binding, the opposite of Windows' app-pushes; §4 keeps DashClock's field discipline and versioning but not its binding model.

### 3.3 Samsung Edge panels

Panels ("cocktails") are RemoteViews-based, updated through `SlookCocktailManager` (`updateCocktail(cocktailId, RemoteViews)`; bitmap memory ≤ ~1.5 screens) https://developer.samsung.com/galaxy-edge/api-reference/com/samsung/android/sdk/look/cocktailbar/SlookCocktailManager.html (search snippet; the page now redirects to the landing page). "The Edge SDK will no longer be supported starting from December 5, 2023." https://developer.samsung.com/galaxy-edge/overview.html . Not reusable and, being RemoteViews, has the §3.1 limitation anyway.

### 3.4 Launchers / home-screen apps with a public content API

| Product | Mechanism | Direction | Auth | Licence | Status |
|---|---|---|---|---|---|
| Nova + TeslaUnread | `ContentProvider` insert (`tag`, `count`) | app → host (push) | provider verifies caller UID against `tag` package | proprietary | dead since Nova 7 (§2.6) |
| Kvaesitso | plugin = `ContentProvider` in the plugin app, discovered by intent filter `de.mm20.launcher2.action.PLUGIN` + DEFAULT category; "Under the hood, plugins are implemented using Android's content provider APIs"; authority "must not change … later or things will break"; SDK `de.mm20.launcher2:plugin-sdk` (2.3.0, 2026-04-12) | host → plugin (pull: weather, files, contacts, places, calendar) | provider-side (plugin decides) | SDK Apache-2.0 (`plugins/sdk/LICENSE.txt`), app GPL-3 (`LICENSE.txt`) | active — https://kvaesitso.mm20.de/docs/developer-guide/plugins/get-started.html , https://central.sonatype.com/artifact/de.mm20.launcher2/plugin-sdk |
| Kustom (KWGT/KLWP) | implicit broadcast `org.kustom.action.SEND_VAR` with extras `EXT_NAME`, `VAR_NAME`/`VAR_VALUE` (or `_ARRAY`), consumed as `$br(foo, myvar)$` | app → host (push) | none (any app can send any variable) | proprietary | active since 2.09 — https://docs.kustom.rocks/docs/developers/send_variables_broadcast/ |
| Sesame Shortcuts | search-provider integration for Nova/Lawnchair/Hyperion/Niagara; contract not public in fetched pages | host ↔ Sesame | — | proprietary | https://www.xda-developers.com/sesame-shortcuts-integration-lawnchair-hyperion-launcher/ |
| Square Home (W10-style launcher) | live tiles from notifications; "supports unofficial badge count APIs that were defined … by device manufacturers or famous third-party launchers such as ADW launcher" | app → host via legacy badge APIs | — | proprietary | no public tile-content API found (searches: "Square Home tile API developers", "Square Home live tile third-party app update tile intent") https://squarehome2.blogspot.com/2020/05/live-tiles.html |
| ShortcutBadger (client side) | per-OEM adapters for the above | app → host | per OEM | Apache-2.0 | maintained but last README release note is 1.1.23 |

### 3.5 Licence summary for anything reusable

- ShortcutBadger: Apache-2.0 — reuse its constants/extras names verbatim (the shell is the *receiver*; nothing to vendor).
- DashClock API: Apache-2.0 — reuse the field-length discipline and versioned-AIDL rule ("Do NOT modify a signature once a protocol version is finalized", `IExtension.aidl`); no code to vendor.
- Kvaesitso plugin SDK: Apache-2.0 — precedent only; its contract is pull-shaped.
- AOSP Launcher3 / Lawnchair: Apache-2.0 — the eligibility filter and count-sum semantics (§2.1–2.2) are re-implemented, not copied.
- TeslaUnread / Kustom / Samsung Edge: proprietary / EOL — design references only.

---

## 4. Recommendation (permanent design, Rule 16)

Assuming: package id placeholder `<app>` = the shell's `applicationId`; all names below are final and versioned from day one; nothing here is an interim build.

### 4a. Count-source precedence and merging

Per tile the shell keeps up to four count inputs, each tagged with its source and timestamp:

| Source | Origin | Trust | Expiry |
|---|---|---|---|
| S1 `api` | Live Tile API `badge.update` (§4b) from the tile's owner package (Binder-verified) | highest — the app said so | app-supplied `expiresAt`, else none (Windows local-badge default, §1.2) |
| S3 `legacy` | `BADGE_COUNT_UPDATE` / ShortcutBadger Oreo action, `getSentFromPackage()` == `badge_count_package_name` (§2.5) | high — the app said so, older protocol, no expiry field | 3 days (the Windows push/periodic default, §1.5; the protocol has no expiry) |
| S4 `samsung` | row for the package in `content://com.sec.badge/apps`, if readable (§2.4, UNVERIFIED) | medium — origin of Samsung's number unverified | none; row is live |
| S2 `notif` | sum over badge-eligible notifications of `max(1, number)`, Launcher3 rules (§2.1–2.2), capped 999 | always available once notification access is granted | live |

Rules:
1. **Displayed count = the highest-precedence source that is present and unexpired: S1 > S3 > S4 > S2. Sources are never summed.** Rationale: S1/S3/S4 are all "the app's unread count" through different pipes and S2 is "how many things are shouting"; adding them double-counts the same messages.
2. An explicit value replaces the previous value of the same source; an explicit **0** (or glyph `none`) from S1/S3 **clears that source and the tile falls through** to the next source. Windows: "A value of 0 … will clear the badge" (§1.6). Because S2 is live, an app that reports 0 while it still has notifications shows the notification count — the preview and the count stay consistent with the notification shade, which is the owner's ruling ("counts come from notifications for every app").
3. Numeric rendering follows Windows: 1–99 as digits, ≥100 as `99+`, 0/none = no badge; glyphs from the 12-glyph set drawn with Fluent icons (branding module) in the badge slot. `DotInfo.MAX_COUNT` 999 is the storage cap, `99+` the display cap.
4. Expiry is evaluated on every tile redraw and on a 15-minute alarm (mirrors Windows' 15-minute timer floor, §1.11); an expired source is dropped and the tile falls through.
5. **Preview content** (the flip/peek face) is a separate precedence: API tile notifications in the tile's queue (§4b) > notification-derived previews. A tile with an API queue never shows notification text; a tile without one behaves as phase 01 already specifies. Windows' own rule (§1.2): the app's `TileUpdater` owns the content.
6. Package uninstall/disable clears S1/S3/S4 state for that package (Windows: secondary tiles "are automatically deleted when the app is uninstalled", §1.9). Revoked notification access sets S2 to "unknown" (not 0) and the checklist row goes red (phase 01 E14).
7. Disagreement is logged, not shown: every ingest writes `(pkg, source, value, ts)` to the engine's ring buffer, exposed in Settings › Live tiles › Diagnostics, so QA on the phone can see which pipe fed which number (memory rule: no telemetry stranded in logcat).

### 4b. Public Live Tile API

**Transport: one exported `ContentProvider`** in the shell, authority `<app>.livetile`, `android:exported="true"`, no `readPermission`/`writePermission`; every verb authorises on Binder identity. Why a provider rather than a bound service or broadcasts: the caller's identity is checked by the framework — `ContentProvider.getCallingPackage()` "@throws SecurityException if the calling package doesn't belong to the calling UID" (`core/java/android/content/ContentProvider.java:1159-1162`); the provider is started on demand so apps can update tiles while the shell process is dead; `call()` is the documented escape hatch for "interfaces that are cheaper and/or unnatural for a table-like model" with the framework's own warning that "Any implementation of this method must do its own permission checks on incoming calls" (`ContentProvider.java:2689-2707`) — which is exactly the identity rule below. TeslaUnread and Kvaesitso used the same shape (§3.4); DashClock's bound-service direction would force the shell to poll (§3.2); broadcasts have no identity before API 34 and Kustom's are unauthenticated (§3.4).

**Identity rule:** a caller may only address tiles whose `owner` is its own package (`getCallingPackage()`); secondary tiles are keyed `(owner, tileId)`; the shell's own package may address any tile (for Settings and the legacy adapters). No custom `<permission>`: the check is in code on every verb, the framework does not enforce anything on `call()` anyway, and there is no install-order dependence.

**Verbs** (`ContentResolver.call(authority, method, arg, extras) → Bundle{ok: Boolean, error: String?}`), mapping 1:1 onto §1:

| Verb (`method`) | Windows | Extras |
|---|---|---|
| `tile.update` | `TileUpdater.Update` | `tileId` (null = primary), `xml` (String, §schema), `tag` (≤16 chars, case-insensitive), `expiresAt` (epoch ms, optional) |
| `tile.clear` | `TileUpdater.Clear` | `tileId` — empties the queue; leaves scheduled items (§1.2) |
| `tile.enableQueue` | `EnableNotificationQueue(bool)` | `tileId`, `enabled` — one flag for all sizes (W10M has three sizes; the per-size variants are dropped) |
| `tile.schedule` / `tile.unschedule` / `tile.scheduled` | `AddToSchedule` / `RemoveFromSchedule` / `GetScheduledTileNotifications` | `tileId`, `id`, `deliveryAt`, `xml`, `tag`, `expiresAt`; `tile.scheduled` returns `ids[]` + `deliveryAt[]` |
| `badge.update` | `BadgeUpdater.Update` | `tileId`, `value` (Int ≥ 0 or glyph String from the 12-glyph set), `expiresAt` optional |
| `badge.clear` | `BadgeUpdater.Clear` | `tileId` |
| `tile.setting` | `TileUpdater.Setting` | returns `ENABLED` / `DISABLED_FOR_APPLICATION` (user turned the app's live tile off in Settings › Live tiles) / `NOT_PINNED` (updates are stored, Windows §1.2 semantics) |
| `secondary.exists` / `secondary.findAll` | `SecondaryTile.Exists` / `FindAllAsync` | `tileId` → `exists: Boolean`; → `tileIds[]` |
| `secondary.requestCreate` | `RequestCreateAsync` | `tileId`, `displayName`, `arguments`, `logo` (content URI), `size` (`small`/`medium`/`wide`), `showName` — the shell shows a W10M-style confirmation; result arrives via `secondary.exists` polling or the callback URI below (pin UI is phase 02 scope; the verb and storage are defined now) |
| `secondary.requestDelete` / `secondary.update` | `RequestDeleteAsync` / `UpdateAsync` | `tileId` (+ the create fields for update; "assign ALL properties") |

Change notifications back to the app: the provider calls `notifyChange(content://<app>.livetile/tiles/<owner>/<tileId>)`; an app that cares registers a `ContentObserver` (DashClock's `addWatchContentUris` idea, reversed).

**Launch arguments:** tapping a tile launches the owner's `CATEGORY_LAUNCHER` activity (LauncherApps, as phase 01 build task 10) with extras `<app>.extra.TILE_ID` (secondary tiles), `<app>.extra.ARGUMENTS` (the secondary tile's `arguments`) and `<app>.extra.TILE_ACTIVATED_ARGS` (String[] of the `arguments` attributes of the notifications currently in the queue — the chaseable-tiles contract, §1.7). Only the owner's own launcher activity is ever started; the API never accepts an arbitrary `Intent`.

**Payload schema:** the adaptive tile XML of §1.7 restricted to what W10M rendered (§1.12):
- `<tile><visual branding? displayName? arguments? lang?><binding template="TileSmall|TileMedium|TileWide" …>` — `TileLarge` is rejected with an error (W10M had no large tile).
- `binding` attributes: `branding` (`none`|`name` only; `logo`/`nameAndLogo` map to `name`, per §1.12), `displayName`, `arguments`, `hint-textStacking`, `hint-presentation` (`photos` ≤9 images, `people`, `contact`).
- `text`: `hint-style` (all 7 + Numeral + Subtle variants, rendered with the R1 §5 type ramp), `hint-wrap`, `hint-maxLines`, `hint-minLines`, `hint-align`.
- `image`: `src`, `placement` (`inline`|`background`|`peek`), `hint-crop`, `hint-overlay`, `hint-removeMargin`, `hint-align`, `alt`.
- `group`/`subgroup` with `hint-weight`, `hint-textStacking`.
- Iconic: `template="TileSquare150x150IconWithBadge"` / `TileSquare71x71IconWithBadge` with one `<image id="1">`.
- Rejected (error, never silently dropped): `hint-lockDetailedStatus*`, `baseUri`, `addImageQuery`, `http(s)` image sources, `TileLarge`, unknown elements/attributes. Size caps: XML ≤ 8 KB; ≤ 12 images per notification; each image ≤ 200 KB decoded input (the Windows pre-FCU cap, §1.7).
- Images: `android.resource://<owner>/…` or a `content://` URI from the owner's own provider with `FLAG_GRANT_READ_URI_PERMISSION` granted to `<app>` (`Context.grantUriPermission`); the shell copies the bytes into its own storage at `tile.update` time so the grant may lapse, and rejects any URI whose authority does not belong to the caller.

**Queue / expiry / scheduling:** per tile, 5 entries; tag match replaces, else oldest out (§1.3); `expiresAt` honoured per entry; scheduled entries delivered by the engine's existing scheduler (phase 01 build task 8) using `AlarmManager` exact-inexact windows, with `Id` uniqueness per tile; display order and dwell time of the queue belong to the engine's staggered random scheduler and are not app-controllable (Windows, §1.3).

**Storage:** the live tile engine's store gains tables `tile_queue(owner, tile_id, seq, tag, xml, images, expires_at, delivered_at)`, `tile_badge(owner, tile_id, value, source, expires_at, updated_at)` (one row per source per tile, §4a), `tile_scheduled(owner, tile_id, id, deliver_at, tag, xml, expires_at)`, `secondary_tile(owner, tile_id, display_name, arguments, logo, size, show_name)`. Updates for unpinned owners are kept (Windows §1.2) with a cap of one queue per package; overflow evicts the least-recently-updated unpinned package.

**Client library:** `livetile-client` (Kotlin, zero dependencies, Apache-2.0, published from the shell repo as an AAR in phase 02 with the pin UI): `LiveTile.forApplication(context)` / `LiveTile.forSecondaryTile(context, tileId)` exposing `update(TileContent)`, `clear()`, `enableNotificationQueue(Boolean)`, `schedule(ScheduledTileContent)`, `badge(Int)`, `badge(Glyph)`, `clearBadge()`, `setting()`, and a `TileContent { small { … } medium { text("…", style = Caption) ; image(uri, placement = Peek) } wide { … } }` builder that emits the XML. Discovery: `packageManager.resolveContentProvider("<app>.livetile", 0) == null` → every call returns `false` silently (ShortcutBadger's `applyCount` contract, §2.5). Protocol version in `call` extras `v = 1`; new verbs are appended, signatures never change (DashClock's AIDL rule, §3.5).

**Legacy adapters inside the shell** (these are the "real unread counts wherever Android/Samsung exposes them"):
- `LegacyBadgeReceiver`: manifest-declared, exported, filters `android.intent.action.BADGE_COUNT_UPDATE` and `me.leolin.shortcutbadger.BADGE_COUNT_UPDATE`; plus the same class registered on the Application context with `RECEIVER_EXPORTED`; dedupe on `(sender uid, package, count, ±2 s)`; accept only when `getSentFromPackage() == badge_count_package_name` (or `getSentFromUid() == INVALID_UID`, flagged in diagnostics); ingest as S3.
- `SamsungBadgeReader`: onboarding/health row "Samsung badge provider" tries the query in §2.4; on success registers a `ContentObserver` on `content://com.sec.badge/apps` and ingests `badgecount` rows as S4; on `SecurityException` the row reads "not readable on this One UI" and S4 is disabled. Never writes.
- `NotificationCountFeed`: the phase-01 listener already planned, extended with the Launcher3 eligibility filter and `max(1, number)` sum → S2.

**Security summary:** identity on every verb; tiles namespaced by owner package; no arbitrary intents; no network; payload validated and size-capped; images copied under the shell's ownership; per-app kill switch in Settings (maps to `DISABLED_FOR_APPLICATION`); rate limit 60 verbs/min per caller (excess → `error: "rate"`); diagnostics ring buffer instead of logcat.

### 4c. Deliberately left out, and why

| Left out | Why |
|---|---|
| Periodic URL polling (`StartPeriodicUpdate*`) | needs internet from the shell; "Weather in, the only internet use" (phase 01 decision Q5/A11). Apps can poll themselves with WorkManager and call `tile.update`. |
| WNS push (channels, cloud auth) | needs a server; the app's own FCM/other push + `tile.update` is the Android equivalent. |
| Toast and raw notifications | Android notifications are the toast; raw push has no tile meaning. |
| `TileLarge` and `nameAndLogo` / `logo` branding | W10M rendered neither (§1.12). |
| Lock detailed status (`hint-lockDetailedStatus*`), `LockScreenApplication*` triggers | glance/lock is phase 07; W10M rendering of this attribute not found. |
| Per-size `EnableNotificationQueueFor*` | one flag suffices for three sizes; W10M users never saw a per-size difference that footage could confirm. |
| `baseUri` / `addImageQuery` / http images | web-image machinery; no network. |
| Background-task framework (`TimeTrigger`, `RequestAccessAsync`) | Android apps schedule their own work; the shell exposes only the 15-minute expiry sweep. |
| DashClock extension adapter (shell binds to `…dashclock.Extension` services) | would add a host-pull scheduler and a 2013-era permission model; no current app population found to justify it (searches: "DashClock extension 2025", "dashclock api active apps" — not run; decision on scope, not on facts). |
| AppWidget hosting inside tiles | visual only, no data contract (§3.1); not in phase 01 scope. |
| Custom badge images | Windows: "only system-provided badge images can be used" (§1.6). |
| `RequestCreateAsync` confirmation UI, client AAR publication | phase 02 (pin/unpin scope); verbs and storage are final now so phase 02 extends, never replaces (Rule 16). |

### 4d. Effects on phase 01 scope (for the phase doc's Change Log)

1. Build task 8 (live tile engine) grows: the `<app>.livetile` provider, the S1–S4 badge store and precedence, and the payload validator/renderer for the §4b schema — the renderer already exists for Photos/People/Calendar tiles, this is the same adaptive-tile subset fed from XML.
2. Build task 9 (feeds) grows: `LegacyBadgeReceiver` (manifest + runtime), `SamsungBadgeReader`, and the Launcher3 eligibility filter + `number` sum in the notification feed.
3. Build task 13 (checklist): two rows — "Samsung badge provider readable" (UNVERIFIED until QA) and "Legacy badge broadcasts seen" (diagnostic, not a grant).
4. Interview Q2 (notification semantics) is partly settled by §4a: count = precedence rule; preview = notification content unless an API queue exists; Jeremy still rules on dismissed/redacted/grouped behaviour for S2.
5. New acceptance criteria: E15 — a test APK calling `tile.update` / `badge.update` through the client library changes its tile within one engine tick, and `adb shell content call --uri content://<app>.livetile --method badge.update --extra value:i:5` (shell uid ≠ owner) is rejected with `error: "identity"`; E16 — the same test APK sending `BADGE_COUNT_UPDATE` via ShortcutBadger updates the count and the diagnostics show source `legacy` with a matching sender package; P6 (phone) — Samsung provider read result recorded, and `pm query-receivers` output for both badge actions captured before/after install.
6. Phase 02 inherits: secondary tile pin/unpin/confirm UI, client AAR release, and `TILE_ACTIVATED_ARGS` end-to-end test.

---

## Source index

Microsoft: https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.tileupdater · …/windows.ui.notifications.tilenotification · …/windows.ui.notifications.scheduledtilenotification · …/windows.ui.notifications.periodicupdaterecurrence · …/windows.ui.notifications.badgeupdater · …/windows.ui.notifications.notificationsetting · https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/badges · …/periodic-notification-overview · …/create-adaptive-tiles · …/special-tile-templates-catalog · …/secondary-tiles · …/sending-a-local-tile-notification · …/windows-push-notification-services--wns--overview · …/choosing-a-notification-delivery-method · https://learn.microsoft.com/en-us/windows/uwp/launch-resume/tile-schema · …/secondary-tiles-pinning · …/run-a-background-task-on-a-timer- · …/support-your-app-with-background-tasks · https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh779719(v=win.10) · https://learn.microsoft.com/en-us/archive/blogs/tiles_and_toasts/whats-new-with-live-tiles-in-windows-10 · https://learn.microsoft.com/en-us/archive/blogs/thunbrynt/windows-phone-8-1-for-developerslive-tiles · https://learn.microsoft.com/en-us/answers/questions/242582/how-to-add-permission-to-com-sec-android-provider

Android docs: https://developer.android.com/develop/ui/views/notifications/badges · https://developer.android.com/about/versions/oreo/background · https://developer.android.com/about/versions/14/behavior-changes-14 · https://developer.android.com/about/versions/15/behavior-changes-all · https://developer.android.com/about/versions/16/behavior-changes-all · https://developer.android.com/develop/background-work/background-tasks/broadcasts · https://developer.android.com/develop/ui/views/appwidgets/host

Android source (`https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/`): core/java/android/app/Notification.java · core/java/android/app/NotificationChannel.java · core/java/android/service/notification/NotificationListenerService.java · core/java/android/content/BroadcastReceiver.java · core/java/android/content/ContentProvider.java · core/java/android/content/pm/ResolveInfo.java · core/java/android/content/pm/PackageManager.java · core/java/android/widget/RemoteViews.java. Launcher3 (`https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/`): src/com/android/launcher3/notification/NotificationListener.java · …/NotificationKeyData.java · src/com/android/launcher3/dot/DotInfo.java

GitHub raw: leolin310148/ShortcutBadger (master): README.md, LICENSE, ShortcutBadger/src/main/AndroidManifest.xml, …/ShortcutBadger.java, …/impl/SamsungHomeBadger.java, …/impl/DefaultBadger.java, …/impl/NovaHomeBadger.java, …/impl/IntentConstants.java, …/util/BroadcastHelper.java · romannurik/dashclock (master): api/src/main/java/com/google/android/apps/dashclock/api/DashClockExtension.java, ExtensionData.java, api/src/main/aidl/…/IExtension.aidl, IExtensionHost.aidl · LawnchairLauncher/lawnchair (15-dev): LICENSE.txt, lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt, src/com/android/launcher3/BubbleTextView.java · MM2-0/Kvaesitso (main): LICENSE.txt, plugins/sdk/LICENSE.txt · signalapp/Signal-Android (main): LICENSE, app/src/main/java/org/thoughtcrime/securesms/notifications/v2/NotificationFactory.kt, NotificationBuilder.kt, DefaultMessageNotifier.kt · DrKLO/Telegram (master): TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java · thunderbird/thunderbird-android (main): legacy/core/src/main/java/com/fsck/k9/notification/SummaryNotificationCreator.kt

Other: https://novalauncher.com/api/teslaunread/ · https://novalauncher.com/teslaunread/ · https://kvaesitso.mm20.de/docs/developer-guide/plugins/get-started.html · https://central.sonatype.com/artifact/de.mm20.launcher2/plugin-sdk · https://docs.kustom.rocks/docs/developers/send_variables_broadcast/ · https://developer.samsung.com/galaxy-edge/overview.html · https://developer.samsung.com/galaxy-edge/api-reference/com/samsung/android/sdk/look/cocktailbar/SlookCocktailManager.html · https://www.apkmirror.com/apk/samsung-electronics-co-ltd/badgeprovider/ · https://www.samsung.com/hk_en/support/mobile-devices/android-o-os-app-icon-can-show-badges-with-numbers-or-dot-style-badges/ · https://aquamail.freshdesk.com/support/solutions/articles/77000519592--samsung-app-badge-is-not-correct · https://squarehome2.blogspot.com/2020/05/live-tiles.html · https://www.xda-developers.com/sesame-shortcuts-integration-lawnchair-hyperion-launcher/
