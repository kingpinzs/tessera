package app.tileshell.media

/**
 * What the PLATFORM says about who may reach a content URI (phase 17; the trust review's A-M1, A-M2, C-M4). The rules
 * that decide whether the shell writes into, or shows, a URI another app handed it ask these questions and nothing
 * else; the real answers come from `AndroidUriAccess`, the tests' from a fake. Free of Android types.
 *
 * Reads and writes are two methods of the one port: the capture answer asks the write pair, the viewer the read pair.
 */
interface UriAccessPort {
    /** The uid of an installed package, or null when the platform does not know (or does not show) it. */
    fun uidOf(packageName: String): Int?

    /** The uid of the app that declares the provider behind [authority], or null when there is none. */
    fun providerOwnerUid(authority: String): Int?

    /** Whether [uid] holds an explicit WRITE grant for [uri] (`Context.checkUriPermission`: URI grants only). */
    fun holdsWriteGrant(uri: String, uid: Int): Boolean

    /**
     * Whether the provider itself says [uid] may write [uri] (`Context.checkContentUriPermissionFull`, API 35+), or
     * null where the platform has no such question (API 34).
     */
    fun providerSaysMayWrite(uri: String, uid: Int): Boolean?

    /** Whether [uid] holds an explicit READ grant for [uri]. */
    fun holdsReadGrant(uri: String, uid: Int): Boolean

    /** As [providerSaysMayWrite], for reading. */
    fun providerSaysMayRead(uri: String, uid: Int): Boolean?

    /** A name for [uid] (its package, or its shared-uid name), or null when the platform gives none. */
    fun nameOfUid(uid: Int): String?
}

/**
 * A URI another app handed the shell, read from its text ONCE (A-L1): the scheme and the authority every rule weighs
 * are the ones this text has, and [text] is the very string the write or the read is made through — never a second
 * reading of the caller's `Uri` object, whose parts need not agree with its text.
 *
 * The split is the platform's own for a URI made from a string (`Uri.parse`): the scheme ends at the first `:`; an
 * authority follows `//` and ends at the first `/`, `\`, `?` or `#`.
 */
class ContentUriText private constructor(val text: String, val scheme: String?, val authority: String?) {
    /**
     * The authority when it plainly names one provider of this user: not empty, no user id (`10@media`) and no
     * percent-escape (`%40` is `@` once decoded; no real authority holds one). Null otherwise.
     */
    val plainAuthority: String? get() = authority?.takeIf { it.isNotEmpty() && '@' !in it && '%' !in it }

    companion object {
        fun parse(text: String): ContentUriText {
            val colon = text.indexOf(':')
            val scheme = if (colon < 0) null else text.substring(0, colon)
            val start = colon + 1
            val authority = if (text.length > start + 1 && text[start] == '/' && text[start + 1] == '/') {
                var end = start + 2
                while (end < text.length && text[end] != '/' && text[end] != '\\' && text[end] != '?' && text[end] != '#') end++
                text.substring(start + 2, end)
            } else {
                null
            }
            return ContentUriText(text, scheme, authority)
        }
    }
}

/**
 * May a given uid reach a `content://` URI itself? True only when the platform says so, in one of three ways: the
 * provider behind the URI is that uid's own; that uid holds an explicit grant for the URI; or (API 35+) the provider,
 * asked, says that uid may. Everything unknown is false: a negative uid, a URI that is not `content`, an authority that
 * is empty, escaped or another user's, a provider nobody declares.
 */
object UriAccessRules {
    fun mayWrite(access: UriAccessPort, uri: ContentUriText, uid: Int): Boolean {
        val authority = plain(uri, uid) ?: return false
        if (access.providerOwnerUid(authority) == uid) return true
        if (access.holdsWriteGrant(uri.text, uid)) return true
        return access.providerSaysMayWrite(uri.text, uid) == true
    }

    fun mayRead(access: UriAccessPort, uri: ContentUriText, uid: Int): Boolean {
        val authority = plain(uri, uid) ?: return false
        if (access.providerOwnerUid(authority) == uid) return true
        if (access.holdsReadGrant(uri.text, uid)) return true
        return access.providerSaysMayRead(uri.text, uid) == true
    }

    private fun plain(uri: ContentUriText, uid: Int): String? = if (uid < 0 || uri.scheme != "content") null else uri.plainAuthority
}
