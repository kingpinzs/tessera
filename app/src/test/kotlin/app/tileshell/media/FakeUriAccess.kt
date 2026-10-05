package app.tileshell.media

/** A platform that answers from tables and records every question it was asked. */
class FakeUriAccess(
    val uids: Map<String, Int> = mapOf("com.caller" to CALLER, "com.other" to OTHER, "app.tileshell" to SHELL),
    val owners: Map<String, Int> = mapOf("com.caller.files" to CALLER, "com.other.files" to OTHER, "media" to MEDIA, "com.android.contacts" to CONTACTS),
    val writeGrants: Set<Pair<String, Int>> = emptySet(),
    val readGrants: Set<Pair<String, Int>> = emptySet(),
    /** Null = a platform below API 35: the provider cannot be asked. */
    val providerWrite: ((String, Int) -> Boolean)? = null,
    val providerRead: ((String, Int) -> Boolean)? = null,
    val names: Map<Int, String> = mapOf(CALLER to "com.caller", OTHER to "com.other", SHELL to "app.tileshell"),
) : UriAccessPort {
    val asked = mutableListOf<String>()

    override fun uidOf(packageName: String): Int? { asked += "uidOf $packageName"; return uids[packageName] }
    override fun providerOwnerUid(authority: String): Int? { asked += "owner $authority"; return owners[authority] }
    override fun holdsWriteGrant(uri: String, uid: Int): Boolean { asked += "writeGrant $uri $uid"; return (uri to uid) in writeGrants }
    override fun providerSaysMayWrite(uri: String, uid: Int): Boolean? { asked += "providerWrite $uri $uid"; return providerWrite?.invoke(uri, uid) }
    override fun holdsReadGrant(uri: String, uid: Int): Boolean { asked += "readGrant $uri $uid"; return (uri to uid) in readGrants }
    override fun providerSaysMayRead(uri: String, uid: Int): Boolean? { asked += "providerRead $uri $uid"; return providerRead?.invoke(uri, uid) }
    override fun nameOfUid(uid: Int): String? { asked += "name $uid"; return names[uid] }

    companion object {
        const val CALLER = 10123
        const val OTHER = 10200
        const val SHELL = 10077
        const val MEDIA = 10050
        const val CONTACTS = 10001
    }
}
