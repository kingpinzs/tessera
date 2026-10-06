package app.tileshell.photos

import app.tileshell.media.ContentUriText
import app.tileshell.media.UriAccessPort
import app.tileshell.media.UriAccessRules

/**
 * What the viewer other apps reach (`ViewerActivity`, exported) may do with the URI an intent names (phase 17; the trust
 * reviews' C-M4 (b) and (c), C2-M1, C2-L1). Pure, so every combination is unit-tested (`ViewerRulesTest`). TRUST: on the
 * exported allow-list, under the adversarial review.
 *
 * The viewer opens a content URI with the SHELL's identity, and the shell can read all the phone's pictures and its
 * contacts. So who asked decides what is done:
 *  - the shell itself (the platform names the starter and it is the shell's own uid) → the picture is shown and every
 *    action is offered;
 *  - any other app, and an app the platform does not name → the picture is shown only when the platform says that app
 *    could read it itself ([UriAccessRules.starterMayRead], the one rule the player and the capture answer read by
 *    too), and then read-only: Share and File information, never Edit, Delete or Set as.
 */
object ViewerRules {
    const val LINE_NO_GRANT = "refused view: no grant"

    /**
     * [show]: decode and show the picture. [mayChange]: offer the actions that change something. [line]: the refusal's.
     * [launch]: the `launch answer read: …` line when the platform's launch answer was asked.
     */
    data class Open(val show: Boolean, val mayChange: Boolean, val line: String?, val request: String, val launch: String? = null)

    /**
     * What `ViewerNav` holds after a launch: the one URI to open ([uri], null when nothing is shown), whether a named
     * picture was [refused], whether anything may be changed, and the `[photosapp]` lines to write, in order.
     */
    data class State(val uri: String?, val refused: Boolean, val mayChange: Boolean, val lines: List<String>)

    /**
     * Whether the picture [uri] names is shown, and how. Another app's URI is shown read-only when
     * [UriAccessRules.starterMayRead] says the app that started the viewer could read it; anything else is refused: the
     * viewer's error state and [LINE_NO_GRANT].
     *
     * ON AN API 34 PHONE (Android 14) the platform neither names an ordinary starter nor answers for it, so a content
     * URI from another app that does not share its identity — nearly every app — is REFUSED, whatever that app could
     * read. On API 35 and later (the owner's phone is API 36) the platform is asked.
     */
    fun open(uri: ContentUriText, access: UriAccessPort): Open {
        if (UriAccessRules.isOwnLaunch(access)) return Open(show = true, mayChange = true, line = null, request = "viewer request from the shell: shown")
        val who = if (UriAccessRules.starterUid(access) == null) "an unnamed app" else "another app"
        val read = UriAccessRules.starterMayRead(access, uri)
        val launch = read.launch.takeIf { it != UriAccessRules.LAUNCH_NOT_ASKED }?.let { UriAccessRules.launchLine(UriAccessRules.READ, it) }
        return if (read.allowed) {
            Open(show = true, mayChange = false, line = null, request = "viewer request from $who: shown read-only", launch = launch)
        } else {
            Open(show = false, mayChange = false, line = LINE_NO_GRANT, request = "viewer request from $who: refused", launch = launch)
        }
    }

    /**
     * From the launch to the viewer's state — the whole mapping, so no decision is left to the Android side (C2-M1).
     * [data] reads the intent's data as its string (`Intent.getData()?.toString()`); it is read ONCE, and the URI that
     * is opened is that very text. A data that is missing, unreadable or not `content` shows nothing and refuses
     * nothing; a rule or a platform read that throws refuses.
     */
    fun state(data: () -> String?, access: UriAccessPort): State {
        val named = try { data() } catch (e: Throwable) { null }?.let(ContentUriText::parse)?.takeIf { it.scheme == "content" }
            ?: return State(uri = null, refused = false, mayChange = false, lines = emptyList())
        val decision = try {
            open(named, access)
        } catch (e: Throwable) {
            Open(show = false, mayChange = false, line = LINE_NO_GRANT, request = "viewer request unreadable (${UriAccessRules.className(e.javaClass.simpleName)})")
        }
        return State(
            uri = if (decision.show) named.text else null,
            refused = !decision.show,
            mayChange = decision.show && decision.mayChange,
            lines = listOfNotNull(decision.request, decision.launch, decision.line),
        )
    }

    enum class Action { SHARE, EDIT, DELETE, SLIDESHOW, SET_AS, FILE_INFORMATION }

    /**
     * The actions the viewer offers for the picture shown. [hasRow]: the shell found the picture's MediaStore row by its
     * own query; [editable]: its type is one the editor takes; [several]: the viewer holds more than one picture;
     * [mayChange]: [Open.mayChange] (always true inside Photos itself). Without [mayChange] nothing that changes a
     * file, the Start background or the lock screen is offered, whatever the row is.
     */
    fun actions(hasRow: Boolean, editable: Boolean, several: Boolean, mayChange: Boolean): List<Action> = buildList {
        add(Action.SHARE)
        if (mayChange && hasRow && editable) add(Action.EDIT)
        if (mayChange && hasRow) add(Action.DELETE)
        if (several) add(Action.SLIDESHOW)
        if (mayChange && hasRow) add(Action.SET_AS)
        if (hasRow) add(Action.FILE_INFORMATION)
    }
}
