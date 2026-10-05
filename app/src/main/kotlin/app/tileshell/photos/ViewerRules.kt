package app.tileshell.photos

import app.tileshell.media.ContentUriText
import app.tileshell.media.UriAccessPort
import app.tileshell.media.UriAccessRules

/**
 * What the viewer other apps reach (`ViewerActivity`, exported) may do with the URI an intent names (phase 17; the trust
 * review's C-M4 (b) and (c)). Pure, so every combination is unit-tested (`ViewerRulesTest`). TRUST: on the exported
 * allow-list, under the adversarial review.
 *
 * The viewer opens a content URI with the SHELL's identity, and the shell can read all the phone's pictures and its
 * contacts. So who asked decides what is done:
 *  - the shell itself (the launching uid is the shell's own) → the picture is shown and every action is offered;
 *  - any other app, and an app the platform does not name → the picture is shown only when the platform says that app
 *    could read it itself, and then read-only: Share and File information, never Edit, Delete or Set as.
 */
object ViewerRules {
    const val LINE_NO_GRANT = "refused view: no grant"

    /** [show]: decode and show the picture. [mayChange]: offer the actions that change something. [line]: the refusal's. */
    data class Open(val show: Boolean, val mayChange: Boolean, val line: String?, val request: String)

    /**
     * @param uri the intent's data, read from its text once
     * @param launchedFromUid `Activity.getLaunchedFromUid()`, null when the platform does not say (-1). The platform
     *   gives it only for a starter that shares its identity, and for the shell itself — so an unknown uid is always
     *   ANOTHER app, never the shell
     * @param shellUid the shell's own uid
     *
     * Another app's URI is shown when one of these holds, each the platform's word and none the intent's:
     *  1. the launching uid is known and may read the URI itself ([UriAccessRules.mayRead]: its own provider, an
     *     explicit read grant it holds, or — API 35+ — the provider, asked, says it may);
     *  2. the SHELL holds an explicit read grant for exactly this URI. The platform issues that grant when an activity
     *     is started with FLAG_GRANT_READ_URI_PERMISSION, and only after it has checked that the sender itself could
     *     read the URI; it issues none where the shell could read the URI anyway by a permission of its own (contacts),
     *     so a grant is never a by-product of the shell's own reach. This is the only way an app the platform does not
     *     name (nearly every app: few share their identity) can have a picture shown.
     * Anything else is refused: the viewer's error state and [LINE_NO_GRANT].
     */
    fun open(uri: ContentUriText, launchedFromUid: Int?, shellUid: Int, access: UriAccessPort): Open {
        if (launchedFromUid != null && launchedFromUid == shellUid) return Open(show = true, mayChange = true, line = null, request = "viewer request from the shell: shown")
        val who = if (launchedFromUid == null) "an unnamed app" else "another app"
        val callerMayRead = launchedFromUid != null && UriAccessRules.mayRead(access, uri, launchedFromUid)
        val handedOver = uri.scheme == "content" && uri.plainAuthority != null && access.holdsReadGrant(uri.text, shellUid)
        return if (callerMayRead || handedOver) {
            Open(show = true, mayChange = false, line = null, request = "viewer request from $who: shown read-only")
        } else {
            Open(show = false, mayChange = false, line = LINE_NO_GRANT, request = "viewer request from $who: refused")
        }
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
