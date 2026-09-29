package app.tessera.r4probe

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel

/**
 * The door the helper hands its binder through (R4 part 2, "binder handoff to the app"). Exported, because the caller
 * is the shell's app_process, but it takes the binder only from the shell uid (2000) or root (0); anything else is
 * refused and recorded.
 */
class HelperProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "helper_binder") return null
        val caller = Binder.getCallingUid()
        if (caller != 2000 && caller != 0) {
            HelperLink.note("refused a binder from uid $caller")
            return null
        }
        val binder = extras?.getBinder("binder") ?: return null
        HelperLink.attach(binder, extras.getString("tag") ?: "?", caller)
        return Bundle()
    }

    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** The app's end of the helper: the binder, who sent it, and the two calls. Calls block, so callers use a thread. */
object HelperLink {
    @Volatile private var binder: IBinder? = null
    @Volatile var tag: String = ""; private set
    @Volatile var fromUid: Int = -1; private set
    private val notes = mutableListOf<String>()
    var onChange: (() -> Unit)? = null

    fun attach(b: IBinder, t: String, uid: Int) {
        binder = b; tag = t; fromUid = uid
        note("binder received from uid $uid (tag $t)")
    }

    fun note(text: String) {
        synchronized(notes) { notes += "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $text" }
        onChange?.invoke()
    }

    fun notes(): List<String> = synchronized(notes) { notes.toList() }

    fun connected(): Boolean = binder?.isBinderAlive == true

    fun ping(): String = call(HelperMain.PING) { }

    /** (exit code, `pidof adbd` output) read by the helper. */
    fun adbdPid(): Pair<Int, String> { var e = -1; val o = call(HelperMain.ADBD_PID, { e = it }) { }; return e to o }

    fun exit(): String = call(HelperMain.EXIT) { }

    fun wirelessDebuggingOn(): String = call(HelperMain.WD_ON) { }

    fun binderAlive(): Boolean = binder?.isBinderAlive == true

    /** (exit code, output) for a NAMED toggle; the helper maps the name to its own fixed command. */
    fun toggle(name: String, action: String): Pair<Int, String> {
        var exit = -1
        val out = call(HelperMain.TOGGLE, { exit = it }) { it.writeString(name); it.writeString(action) }
        return exit to out
    }

    private fun call(code: Int, exitOut: (Int) -> Unit = {}, write: (Parcel) -> Unit): String {
        val b = binder ?: return "no helper binder"
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            write(data)
            b.transact(code, data, reply, 0)
            val exit = reply.readInt()
            exitOut(exit)
            reply.readString() ?: ""
        } catch (e: Exception) {
            exitOut(-1)
            "call failed: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            data.recycle(); reply.recycle()
        }
    }
}
