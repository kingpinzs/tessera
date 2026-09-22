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

    /**
     * R3 C3 Weather tile, current-conditions face (LOW) and 3-day face (LOW).
     *
     * [sky] is the animated background the text sits on (INDEX Change Log 2026-09-21 item 2). It is optional
     * because a face published before the sky existed — a cached report read back off disk, or any other
     * publisher of this face — must still render: no sky simply means the flat accent plate it always was.
     */
    data class WeatherNow(
        val condition: String,
        val temperature: String,
        val details: List<String>,
        val stale: String?,
        val place: String? = null,
        val sky: WeatherSky? = null,
    ) : TileFace
    data class WeatherDays(val days: List<Triple<String, String, String>>, val stale: String?) : TileFace

    /**
     * X3 approximation: album art full-bleed with title / artist lines.
     *
     * [controls] is what turns this face into the player (Jeremy, 2026-09-21: "show what is currently
     * playing with play pauese stop skip on it"). It is empty on the idle face, so a tile that is merely
     * SHOWING the last track — the one that flips behind the logo when nothing is playing — has no tap
     * targets on it and behaves like every other tile: a tap launches the app.
     *
     * [playing] is what the play/pause control draws, and it is carried on the face rather than derived
     * from `controls` because a face with no controls still knows which state it is describing.
     */
    data class NowPlaying(
        val art: ImageBitmap?,
        val title: String,
        val artist: String,
        val playing: Boolean = false,
        val controls: List<Transport> = emptyList(),
    ) : TileFace
}

/**
 * A transport command a tile can carry (INDEX Change Log 2026-09-21 item 3, Jeremy's "play pauese stop
 * skip"). One play/pause control rather than two, because the tile always knows which state it is in and
 * a pair of buttons where one is always dead is not what W10M did with a transport.
 *
 * The enum lives in the engine, next to the face that carries it: it says WHAT the tile offers. Which
 * media session it is sent to is the feed's business ([app.tileshell.feeds.MusicFeed.send]).
 */
enum class Transport { PLAY_PAUSE, STOP, NEXT }

/**
 * Which sky a weather face animates (Jeremy, INDEX Change Log 2026-09-21 item 2: "the main tile should be the
 * given days weather and it should have animation of sunny, raining, snowing, rainbow ect. something fun").
 *
 * The scene lives in the engine, next to the face that carries it, because it is presentation — WHAT Start
 * draws. WHICH scene a report means is weather knowledge and stays in the feed's package
 * ([app.tileshell.weather.SkyRules]), so the engine keeps depending on nothing.
 *
 * One scene covers a whole WMO group rather than one code per scene: a drizzle and a downpour are the same
 * picture with more water in it, which is what [intensity] (0..1, from the code's own severity) is for.
 */
enum class SkyScene { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER, RAINBOW }

/** A scene, the day/night form of it (the feed's `is_day`), and how hard it is coming down. */
data class WeatherSky(val scene: SkyScene, val isDay: Boolean, val intensity: Float)

/**
 * How a tile moves between faces (R3 A7): flip tiles squash vertically, image tiles crossfade, and peek tiles slide:
 * the face on show retracts (downward when a photo comes in, as the Store tile's text panel does; upward when the
 * content comes back) while the next face follows it in.
 */
enum class FaceTransition { FLIP, CROSSFADE, PEEK }

data class TileContent(
    val faces: List<TileFace>,
    val transition: FaceTransition = FaceTransition.FLIP,
    /** Source timestamp (e.g. StatusBarNotification.getPostTime()) for render-latency diagnostics (E5). */
    val sourceTimeMs: Long = 0L,
    val sourceTag: String = "",
    /**
     * A face that replaces the tile's logo front (face 0) instead of taking a turn behind it.
     *
     * Weather is the one tile whose front IS its content (Jeremy, 2026-09-21: "the main tile should be the
     * given days weather"), so it publishes the current conditions here and only the 3-day face in [faces].
     * Publishing the same conditions in [faces] as well would flip the tile between two identical pictures
     * every 5 s. Null everywhere else: every other tile's front is its app logo, as W10M drew it.
     *
     * Last in the list because the feeds pass the fields before it positionally.
     */
    val front: TileFace? = null,
    /**
     * Advance the faces on this fixed cadence (ms) instead of R3 A8's own random timer.
     *
     * 0 everywhere but a running slideshow (INDEX Change Log 2026-09-21 item 4), which is the one tile
     * content that is not a live tile showing its news: it is a slideshow, and a slideshow with a random
     * 4.0-4.8 s period looks like a tile that cannot decide. [app.tileshell.start.TileTiming] owns the
     * rule; this is how a feed asks for it.
     */
    val slideshowMs: Int = 0,
)
