package app.tileshell.tiles.engine

import androidx.compose.ui.graphics.ImageBitmap

/** One face of a live tile. The front (logo) face is always drawn by the tile itself; these are the live faces. */
sealed interface TileFace {
    /** R3 C3 Mail / notification tile: caption-class lines at the top-left, 16-epx pitch. */
    data class TextLines(val lines: List<String>) : TileFace

    /** R3 C3 Money/News style: white caption lines over a darkened photo. */
    data class Photo(val image: ImageBitmap, val overlayLines: List<String> = emptyList()) : TileFace

    /** R3 C3 Calendar tile: day name (body) + day number (≈43-epx Light) centred; optional event text. */
    data class CalendarDay(val dayName: String, val dayNumber: String, val eventLines: List<String>) : TileFace

    /** R3 C3 Weather tile, current-conditions face (LOW) and 3-day face (LOW). */
    data class WeatherNow(val condition: String, val temperature: String, val details: List<String>, val stale: String?) : TileFace
    data class WeatherDays(val days: List<Triple<String, String, String>>, val stale: String?) : TileFace

    /** X3 approximation: album art full-bleed with title / artist lines. */
    data class NowPlaying(val art: ImageBitmap?, val title: String, val artist: String) : TileFace
}

/** How a tile moves between faces (R3 A7): flip tiles squash vertically, image tiles crossfade. */
enum class FaceTransition { FLIP, CROSSFADE }

data class TileContent(
    val faces: List<TileFace>,
    val transition: FaceTransition = FaceTransition.FLIP,
    /** Source timestamp (e.g. StatusBarNotification.getPostTime()) for render-latency diagnostics (E5). */
    val sourceTimeMs: Long = 0L,
    val sourceTag: String = "",
)
