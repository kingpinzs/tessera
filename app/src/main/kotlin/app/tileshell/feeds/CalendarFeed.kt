package app.tileshell.feeds

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calendar tile (R3 C3): the day face (day name + day number) and, when there are upcoming events in the
 * next 24 hours, event faces (time + title lines). Reads CalendarProvider instances; refreshes on provider
 * changes and at each minute boundary so the day rolls over.
 */
object CalendarFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var ticking = false

    fun hasAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        val app = context.applicationContext
        if (hasAccess(app) && observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(app, "provider change")
            }.also { app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, it) }
        }
        if (!ticking) {
            ticking = true
            scope.launch {
                while (true) {
                    delay(60_000 - System.currentTimeMillis() % 60_000)
                    refresh(app, "minute")
                }
            }
        }
        refresh(app, "start")
    }

    fun refresh(context: Context, reason: String) {
        scope.launch {
            val now = Date()
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
            val dayNumber = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
            val faces = mutableListOf<TileFace>(TileFace.CalendarDay(dayName, dayNumber, emptyList()))
            if (hasAccess(context)) {
                runCatching {
                    val start = System.currentTimeMillis()
                    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    ContentUris.appendId(builder, start)
                    ContentUris.appendId(builder, start + 24L * 60 * 60 * 1000)
                    context.contentResolver.query(
                        builder.build(),
                        arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION),
                        null, null, "${CalendarContract.Instances.BEGIN} ASC",
                    )?.use { c ->
                        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                        while (c.moveToNext() && faces.size < 4) {
                            val title = c.getString(0) ?: continue
                            val allDay = c.getInt(2) == 1
                            val whenText = if (allDay) "All day" else timeFmt.format(Date(c.getLong(1)))
                            val location = c.getString(3).orEmpty()
                            faces += TileFace.CalendarDay(dayName, dayNumber, listOf(title, whenText, location).filter { it.isNotBlank() })
                        }
                    }
                }.onFailure { Diagnostics.add("calendar", "query failed: $it") }
            }
            LiveTileEngine.publish(LiveTileEngine.CALENDAR, TileContent(faces, FaceTransition.FLIP, System.currentTimeMillis(), "calendar"))
            Diagnostics.add("calendar", "refresh ($reason): access=${hasAccess(context)} faces=${faces.size}")
        }
    }
}
