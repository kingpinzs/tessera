package app.tileshell.testclient

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import kotlin.random.Random

/**
 * This test app's own image provider (authority <package>.images, not exported, grantUriPermissions) so the client
 * library can grant the shell read access for one call. /peek.png is a small generated PNG; /big.png is a noise PNG
 * well over the shell's 200 KB per-image cap.
 */
class TestImageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("no context")
        val name = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
        val file = File(ctx.cacheDir, name)
        if (!file.exists()) {
            val bitmap = when (name) {
                PEEK -> peek(ctx.packageName)
                BIG -> noise()
                else -> throw FileNotFoundException(uri.toString())
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun peek(pkg: String): Bitmap {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(if (pkg.endsWith(".a")) Color.rgb(0, 120, 215) else Color.rgb(96, 60, 186))
        c.drawCircle(128f, 128f, 80f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
        return b
    }

    private fun noise(): Bitmap {
        val b = Bitmap.createBitmap(700, 700, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        val pixels = IntArray(700 * 700) { 0xFF000000.toInt() or rnd.nextInt(0x1000000) }
        b.setPixels(pixels, 0, 700, 0, 0, 700, 700)
        return b
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        const val PEEK = "peek.png"
        const val BIG = "big.png"
        fun uri(context: Context, name: String): Uri = Uri.parse("content://${context.packageName}.images/$name")
    }
}
