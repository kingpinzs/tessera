# Secondary tiles — the seam Start wires to (phase 02 build task 6)

Everything Start needs is on `app.tileshell.tiles.api.SecondaryTiles` (an object) plus one composable,
`app.tileshell.start.SecondaryPinPrompt`. Start never touches `LiveTileStore` for secondary tiles.

Nothing here has to be started by Start: `LiveTileProvider.onCreate` already calls `SecondaryTiles.start(app)` at
every process start, which is also what publishes the flows below.

## 1. The pin confirmation (H22)

```kotlin
val pending: StateFlow<SecondaryTiles.PendingRequest?>          // null = nothing to confirm
fun setStartVisible(visible: Boolean)                            // onResume(true) / onPause(false)
fun accept()                                                     // pins; called BY the prompt
fun decline()                                                    // pins nothing; called BY the prompt

data class PendingRequest(
    val owner: String,          // the requesting package
    val ownerLabel: String,     // its own label, for "<app> wants to pin a tile to Start"
    val tileId: String,
    val displayName: String,
    val size: TileSize,         // app.tileshell.tiles.TileSize: SMALL | MEDIUM | WIDE
    val showName: Boolean,
    val logoFile: java.io.File?,
    val requestedAtMs: Long,
) { val key: TileKey.SecondaryTile }
```

```kotlin
@Composable
fun SecondaryPinPrompt(
    request: SecondaryTiles.PendingRequest,      // non-null: draw it only when there is one
    modifier: Modifier = Modifier,
    onDone: (accepted: Boolean) -> Unit = {},
)
```

Wiring, all of it:

```kotlin
// StartActivity (or wherever Start's onResume/onPause live)
override fun onResume() { …; SecondaryTiles.setStartVisible(true) }
override fun onPause()  { SecondaryTiles.setStartVisible(false); … }

// In Start's content, above the page, between the drawn status bar and nav bar (the bar rule):
val pinRequest by SecondaryTiles.pending.collectAsState()
pinRequest?.let { SecondaryPinPrompt(it, Modifier.align(Alignment.TopCenter)) { accepted -> /* optional */ } }
```

The prompt calls `accept()` / `decline()` itself and then `onDone(accepted)`; the host only decides where the band
sits and what (if anything) it does afterwards. Accepting pins through
`LayoutStore.pin(TileKey.SecondaryTile(owner, tileId), size)` — the same call as every other pin — so the tile
appears through the layout flow Start already collects. Declining pins nothing and deletes the copied logo.

**Hold rule (phase 02 Decisions, edge case "request while Start is not visible or the requester is in the
background"):** a request is presented the NEXT time Start is shown, never during the showing it arrived in, so it is
never drawn over another app and never dropped. `setStartVisible(true)` is what advances that; if Start never calls
it, the band never appears. Requests queue (max 8), one at a time, oldest first; a second request for the same tile
replaces the first in place.

## 2. Drawing a pinned secondary tile

```kotlin
val tiles: StateFlow<Map<String, SecondaryTiles.SecondaryTileInfo>>   // key = TileKey.SecondaryTile(...).id
fun info(context: Context, owner: String, tileId: String): SecondaryTileInfo?
fun logo(info: SecondaryTileInfo, sizePx: Int): ImageBitmap?          // null = the owner gave no logo
fun contentKey(owner: String, tileId: String): String                 // LiveTileEngine.content[...] key
fun badgeKey(owner: String, tileId: String): String                   // BadgeStore.counts[...] key

data class SecondaryTileInfo(
    val owner: String,
    val tileId: String,
    val displayName: String,
    val arguments: String,
    val size: TileSize,       // what the OWNER asked for; after the pin the layout owns the size
    val showName: Boolean,
    val logoFile: java.io.File?,
) { val key: TileKey.SecondaryTile; val contentKey: String; val badgeKey: String }
```

In `rememberPlacedTiles`, the `is TileKey.SecondaryTile ->` branch becomes (the shape the other branches use):

```kotlin
val secondaries by SecondaryTiles.tiles.collectAsState()      // beside the existing collectAsState calls
…
is TileKey.SecondaryTile -> {
    val info = secondaries[key.id]
    val model = TileModel(
        id = idPrefix + key.id,
        label = if (info?.showName == true) info.displayName else "",
        size = size,
        icon = info?.let { SecondaryTiles.logo(it, iconPx) }?.let { TileIcons.Icon(it, monochrome = false) },
        fallbackGlyph = Glyph.APPS,
        content = if (live) content[SecondaryTiles.contentKey(key.owner, key.tileId)] else null,
        badge = badges[SecondaryTiles.badgeKey(key.owner, key.tileId)] ?: 0,
        unassigned = info == null,
    )
    PlacedTile(model, TileTarget.Secondary(key.owner, key.tileId), x, y, w, h)
}
```

`SecondaryTiles.tiles` is the recomposition trigger for the record (name, logo, size); the faces and the badge come
out of the maps Start already collects (`LiveTileEngine.content`, `BadgeStore.counts`) under the two keys above, so
a live update repaints exactly as an app tile's does.

## 3. The tap

```kotlin
fun launch(
    context: Context,
    owner: String,
    tileId: String,
    sourceBounds: android.graphics.Rect? = null,
    options: android.os.Bundle? = null,
): Boolean            // false = not pinned, or the owner has no launcher activity (already logged)
```

Add a `TileTarget` for it (StartPage.kt is yours) and call it from `StartActivity.launchApp` where the other targets
are handled, with the same `sourceBounds` and `ActivityOptions` the app path uses. It starts the OWNER'S
`CATEGORY_LAUNCHER` activity with `app.tileshell.extra.TILE_ID`, `…extra.ARGUMENTS` and
`…extra.TILE_ACTIVATED_ARGS` (R5 §4b). The API never accepts an intent from an app, so nothing else is ever started.

## 4. What the seam does on its own (no wiring needed)

- A tile the user unpins (by hand, with its folder, or with the app) stops existing for its owner: the record, queue,
  badge and logo are dropped and `secondary.exists` answers false. This follows the layout flow, so any unpin path
  Start uses is covered.
- Uninstalling an owner unpins its secondary tiles wherever they are — grid, bottom row or inside a folder — and
  wipes their stored state (R5 §1.9, E5). Build task 5's general `LayoutStore.onPackagesRemoved` doing the same
  later is harmless.
- An owner reinstalled under a different identity loses its tiles (the store wipes the records, the seam unpins the
  now-recordless tiles).

## 5. Notes for QA (E5)

- The test APK: `adb shell am start -n app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity` shows a
  **PIN TILE** button (Windows requires the user to press one inside the app) and takes the same extras as a verb:
  `--es tileId st1 --es displayName "Jen" --es arguments "chat=42" --es size medium --ez logo true`.
- After a tap on the pinned tile, the APK's top line reads `TILE_ID=… ARGS=… ACTIVATED=…`, which `uiautomator dump`
  picks up (`dumpsys activity` prints only "(has extras)").
- Verbs by hand: `--es verb secondary.exists --es tileId st1`, `--es verb secondary.findAll`,
  `--es verb secondary.requestDelete --es tileId st1`, and any tile/badge verb plus `--es tileId st1`.
- `adb shell content call --uri content://app.tileshell.livetile --method secondary.requestCreate …` is refused with
  `error: "identity"` (the adb shell uid), exactly as in phase 01.
