package app.tileshell.process

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import app.tileshell.cortana.CortanaDestinationKey
import app.tileshell.cortana.CortanaMode
import app.tileshell.cortana.CortanaRoleNoticeActivity
import app.tileshell.cortana.CortanaService
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.ShellSettings
import app.tileshell.prefs.StartTheme
import java.io.File

/**
 * [ShellProcess]'s three calls, asked from any of the shell's processes but the launcher's (phase 17, D3 (a)): the theme
 * as it is now, the Start background pointed at Photos' copy, Cortana opened. Each is one `call()` on
 * [LauncherCallsProvider], which answers in the launcher's process — phase 05's form for the keyboard's settings
 * (`ime/KeyboardConfig.kt`): one writer, one holder of the state, no stale copy. A call that fails keeps what this
 * process had and says so in its ring (`[app] launcher call <method> failed: <class>`).
 */
object LauncherCalls {
    const val AUTHORITY = "app.tileshell.launchercalls"
    const val THEME = "theme"
    const val BACKGROUND = "background"
    const val CORTANA = "cortana"
    const val KEY_THEME = "theme"
    const val KEY_DESTINATION = "destination"
    const val KEY_DONE = "done"

    fun isLauncher(context: Context): Boolean = ShellProcess.isLauncher(Application.getProcessName(), context.packageName)

    /**
     * Before a window of this process draws: the theme the launcher's process holds now, taken as this process's copy —
     * when [ShellProcess.asksForTheme] says so (a warm process outside the launcher's). Nothing is written. Main thread:
     * one call to a process that is running whenever Start is. It BLOCKS: the binder call holds this thread until the
     * launcher's process answers (or the call fails), so the window's first frame waits on it.
     */
    fun refreshTheme(context: Context) {
        if (!ShellProcess.asksForTheme(isLauncher(context), ShellSettings.held())) return
        // The answer is read with the shell's own class loader: a Bundle that came across a process has none of its own.
        val theme = ask(context, THEME, null) { it.classLoader = StartTheme::class.java.classLoader; it.getSerializable(KEY_THEME, StartTheme::class.java) }
        if (theme == null) {
            Diagnostics.add("app", "theme: this process's copy kept (the launcher's process did not answer)")
            return
        }
        ShellSettings.get(context).adopt(theme)
        Diagnostics.add("app", "theme: read from the launcher's process")
    }

    /** Points Start's background at Photos' copy of row [id] (`files/backgrounds/<id>.jpg`, already written). True when it took it. */
    fun setBackground(context: Context, id: Long): Boolean = ask(context, BACKGROUND, id.toString()) { it.getBoolean(KEY_DONE) } == true

    /**
     * `CortanaService.open` from another process: true when the launcher's process showed the session. With no answer
     * from it ([ShellProcess.noticeHere]) this process shows the role notice itself, as the Search key did here before D3.
     */
    fun openCortana(context: Context, mode: CortanaMode, destination: CortanaDestinationKey?): Boolean {
        val extras = destination?.let { Bundle().apply { putString(KEY_DESTINATION, it.name) } }
        val answer = ask(context, CORTANA, mode.name, extras) { it.getBoolean(KEY_DONE) }
        val said = when (answer) { true -> "shown"; false -> "not shown"; null -> "no answer, so the role notice is shown here" }
        Diagnostics.add("cortana", "open($mode${destination?.let { ", $it" } ?: ""}): asked the launcher's process: $said")
        if (ShellProcess.noticeHere(answer)) context.startActivity(Intent(context, CortanaRoleNoticeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return answer == true
    }

    /** One call and the reading of its answer, both inside the guard: a call or an answer that throws is no answer. */
    private fun <T> ask(context: Context, method: String, arg: String?, extras: Bundle? = null, read: (Bundle) -> T?): T? = runCatching {
        context.contentResolver.call(Uri.parse("content://$AUTHORITY"), method, arg, extras)?.let(read)
    }.onFailure { Diagnostics.add("app", "launcher call $method failed: ${it.javaClass.simpleName}") }.getOrNull()

    internal fun answer(done: Boolean): Bundle = Bundle().apply { putBoolean(KEY_DONE, done) }
}

/**
 * [LauncherCalls]' answers, in the launcher's process (no `android:process`; `photos/ViewerProcessManifestTest` holds
 * that, since a provider in another process would ask itself). Not exported: only the shell's own processes reach it,
 * and its first statement refuses any caller whose uid is not the shell's ([ShellProcess.answersCaller]). What it accepts is [ShellProcess]'s answer: the background only as the copy of one row id that exists, Cortana only by
 * a mode's and a page's names; any other method is no answer.
 */
class LauncherCallsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!ShellProcess.answersCaller(Binder.getCallingUid(), Process.myUid())) {
            Diagnostics.add("app", "launcher call refused: the caller is not this app")
            return null
        }
        val ctx = context ?: return null
        return when (method) {
            LauncherCalls.THEME -> Bundle().apply { putSerializable(LauncherCalls.KEY_THEME, ShellSettings.get(ctx).theme.value) }
            LauncherCalls.BACKGROUND -> {
                val file = ShellProcess.backgroundCopy(arg)?.let { File(ctx.filesDir, it) }?.takeIf { it.isFile }
                if (file == null) {
                    Diagnostics.add("settings", "background from another process refused: no copy for that row")
                } else {
                    ShellSettings.get(ctx).update { it.copy(backgroundUri = Uri.fromFile(file).toString()) }
                }
                LauncherCalls.answer(file != null)
            }
            LauncherCalls.CORTANA -> {
                val open = ShellProcess.cortanaOpen(arg, extras?.getString(LauncherCalls.KEY_DESTINATION))
                LauncherCalls.answer(open != null && CortanaService.open(ctx, open.first, open.second))
            }
            else -> null
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
