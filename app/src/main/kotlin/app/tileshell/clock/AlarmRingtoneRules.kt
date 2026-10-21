package app.tileshell.clock

import app.tileshell.media.ContentUriText
import app.tileshell.media.UriAccessPort
import app.tileshell.media.UriAccessRules

/**
 * Which sound an alarm may play when the sound was NAMED BY ANOTHER APP, and what the ring may open whatever the
 * store holds (ledger L18-2; trust rules, free of Android — `AlarmRingtoneRulesTest`).
 *
 * The ring opens an alarm's sound with the SHELL's identity, and the shell can read every file on every volume and its
 * own unexported providers. So a ringtone that arrives through the `AlarmClock` API is never taken on its word:
 *
 * [fromApi] keeps it only when it is a sound the system offers AND the app that asked could read it itself —
 *  - `content://settings/system/<alarm_alert | ringtone | notification_sound>`: the phone's current default sounds,
 *    which every app can read;
 *  - `content://media/internal/audio/media/<id>`: a row of MediaStore's internal volume, which holds only the sounds
 *    the system ships and which every app can read;
 *  - `content://media/<volume>/audio/media/<id>` on any other volume (a ringtone the user added): only when the
 *    platform says the app that started the handler could read that row itself — the same question, asked of the same
 *    port, as the exported viewer and player ask ([UriAccessRules.starterMayRead]).
 * It is ONE rule for both of the API's paths (ledger L18-3): with `EXTRA_SKIP_UI` the handler stores its answer; without
 * it the editor opens filled in from the handler's bundle, and weighs that bundle's sound by this same rule, the
 * caller's question answered by the handler that asked it ([Caller.AskedByHandler]). So the alarm the editor opens has
 * the sound the alarm made with SKIP_UI would have had (phase 15 T15-37: "without `EXTRA_SKIP_UI` it opens the editor
 * filled in, with it the item is created").
 * Anything else — a `file:` URI, a bare path, `http(s):`, another scheme, another provider, any authority of the
 * shell's own, a user-id authority, an odd path — becomes the DEFAULT sound. The alarm is still made: an app that
 * passes an odd ringtone asked for an alarm, and gets one.
 *
 * [sinkRefusal] is the ring's own check, whatever the store holds (an alarm saved before this rule, a store edited by
 * hand): only a `content:` URI is opened, and never one of the shell's own providers. The sounds the shell's own editor
 * offers are all MediaStore rows or `tessera-sound:` ids (resolved before this is asked), so none is lost.
 */
object AlarmRingtoneRules {
    /** The shell's application id: every provider authority the shell declares is this or starts with this and a dot. */
    const val SHELL_PACKAGE = "app.tileshell"

    const val WHY_NOT_CONTENT = "not a content uri"
    const val WHY_AUTHORITY = "no plain authority"
    const val WHY_SHELL = "the shell's own provider"
    const val WHY_NOT_SOUND = "not a system sound"
    const val WHY_CALLER = "the caller could not read it"

    /**
     * [refused] is why the ringtone asked for was not kept (a diagnostics word, never the caller's text), else null;
     * [asked] is what the platform answered when it was asked about the caller (ledger L18-3, N12), else null.
     */
    data class FromApi(val sound: AlarmSound, val refused: String? = null, val asked: String? = null) {
        /**
         * The handler's diagnostics line for this answer, or null when there is nothing to say: `api ringtone not kept
         * (<why>[; <asked>]) -> default sound`, or `api ringtone kept (<asked>)` for a row kept on the platform's word.
         */
        val line: String? get() = when {
            refused != null -> "api ringtone not kept (${listOfNotNull(refused, asked).joinToString("; ")}) -> default sound"
            asked != null -> "api ringtone kept ($asked)"
            else -> null
        }
    }

    /** Who answers [fromApi]'s one question that depends on where it is asked: could the app that named a row of the user's own volume read it? */
    sealed interface Caller {
        /**
         * The API handler: asked of [access], the platform port of the activity the caller started; null when nobody can be
         * asked about the caller — then only the sounds every app can read are kept.
         */
        data class Ask(val access: UriAccessPort?) : Caller

        /**
         * The editor, for the handler's hand-off bundle — honoured only when the shell itself sent it (`ClockIntents.open`):
         * the handler asked the platform, and its bundle names a row only when the answer was yes. Every other check is
         * made again.
         */
        data object AskedByHandler : Caller
    }

    /**
     * @param ringtone `AlarmClock.EXTRA_RINGTONE` as it arrived: null, blank, "silent", or what should be a content URI
     * @param caller who answers whether the caller could read a row of the user's own volume ([Caller])
     */
    fun fromApi(ringtone: String?, caller: Caller): FromApi {
        if (ringtone == null || ringtone.isBlank()) return FromApi(AlarmSound.DEFAULT)
        if (ringtone == AlarmApiRules.RINGTONE_SILENT) return FromApi(AlarmSound(AlarmSound.Kind.VIBRATE))
        val uri = ContentUriText.parse(ringtone)
        sinkRefusal(ringtone)?.let { return FromApi(AlarmSound.DEFAULT, it) }
        val rest = ringtone.substring(CONTENT.length + uri.authority.orEmpty().length)
        val kept = FromApi(AlarmSound(AlarmSound.Kind.TONE, ringtone, null))
        return when (uri.authority) {
            "settings" -> if (rest in SYSTEM_DEFAULTS) kept else FromApi(AlarmSound.DEFAULT, WHY_NOT_SOUND)
            "media" -> when {
                INTERNAL_SOUND.matches(rest) -> kept
                !VOLUME_SOUND.matches(rest) -> FromApi(AlarmSound.DEFAULT, WHY_NOT_SOUND)
                else -> callerMayRead(caller, uri).let { (yes, asked) -> if (yes) kept.copy(asked = asked) else FromApi(AlarmSound.DEFAULT, WHY_CALLER, asked) }
            }
            else -> FromApi(AlarmSound.DEFAULT, WHY_NOT_SOUND)
        }
    }

    /**
     * Whether the caller may read [uri], and what the platform answered (null: it was not asked). Ledger L18-3, N12: on
     * API 35+ the launch answer is asked about a URI that came as a string extra, which the platform may answer with a
     * throw — handed back by the real port as its class, so "no". A port that throws itself is "no" too: nothing it
     * throws reaches the launcher's process.
     */
    private fun callerMayRead(caller: Caller, uri: ContentUriText): Pair<Boolean, String?> = when (caller) {
        Caller.AskedByHandler -> true to null
        is Caller.Ask -> when (val access = caller.access) {
            null -> false to null
            else -> try {
                val read = UriAccessRules.starterMayRead(access, uri)
                val launch = UriAccessRules.launchLine(UriAccessRules.READ, read.launch)
                read.allowed to (if (read.allowed) "${read.why.word}; $launch" else launch)
            } catch (e: Throwable) {
                false to "the check threw ${UriAccessRules.className(e.javaClass.simpleName.orEmpty())}"
            }
        }
    }

    /**
     * Why the ring must NOT open [uri] as an alarm's sound — it then rings the default — or null when it may: the URI
     * is `content:` (exactly; never `file:`, a path, `http:`, anything else), names one provider plainly, holds no
     * white space or control character, and the provider is not one of the shell's own.
     */
    fun sinkRefusal(uri: String): String? {
        val parsed = ContentUriText.parse(uri)
        if (parsed.scheme != "content" || !uri.startsWith(CONTENT) || uri.any { it <= ' ' || it == '\u007f' }) return WHY_NOT_CONTENT
        val authority = parsed.plainAuthority ?: return WHY_AUTHORITY
        if (isShellAuthority(authority)) return WHY_SHELL
        return null
    }

    /** True for the shell's package name and everything under it, in any letter case. */
    fun isShellAuthority(authority: String): Boolean {
        val a = authority.lowercase()
        return a == SHELL_PACKAGE || a.startsWith("$SHELL_PACKAGE.")
    }

    private const val CONTENT = "content://"

    /** `Settings.System.DEFAULT_ALARM_ALERT_URI`, `DEFAULT_RINGTONE_URI` and `DEFAULT_NOTIFICATION_URI`, after the authority. */
    private val SYSTEM_DEFAULTS = setOf("/system/alarm_alert", "/system/ringtone", "/system/notification_sound")

    /** A MediaStore audio row; the query RingtoneManager and Settings add (`?title=Argon&canonical=1`) is allowed, a fragment is not. */
    private const val ROW = "/audio/media/[0-9]{1,18}(\\?[A-Za-z0-9_=&%.+~-]*)?"
    private val INTERNAL_SOUND = Regex("/internal$ROW")
    private val VOLUME_SOUND = Regex("/[A-Za-z0-9_-]{1,64}$ROW")
}
