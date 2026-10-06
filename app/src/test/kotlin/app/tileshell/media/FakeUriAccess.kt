package app.tileshell.media

/**
 * A platform that answers from tables and records every question it was asked. Its defaults are an API 36 phone on
 * which nobody is named as the starter, nobody holds a grant, and the provider and the launch both answer "denied".
 */
class FakeUriAccess(
    override val apiLevel: Int = 36,
    val shell: Int = SHELL,
    /** What `getLaunchedFromUid()` answers: -1 = the platform does not name the starter. */
    val launchedFrom: Platform<Int> = Platform.Said(-1),
    val uids: Map<String, Int> = mapOf("com.caller" to CALLER, "com.other" to OTHER, "app.tileshell" to SHELL, "android" to SYSTEM, "com.holds.contacts" to HOLDS_CONTACTS),
    val providers: Map<String, ProviderFacts> = mapOf(
        "com.caller.files" to ProviderFacts(CALLER, false), "com.other.files" to ProviderFacts(OTHER, false),
        "media" to ProviderFacts(MEDIA, true), "com.android.contacts" to ProviderFacts(CONTACTS, false),
    ),
    /** Explicit URI grants: (uri, uid, mode flag). */
    val grants: Set<Triple<String, Int, Int>> = emptySet(),
    /** `checkContentUriPermissionFull`: (uri, uid, mode flag) → the result code, or a throw. */
    val providerAnswer: (String, Int, Int) -> Platform<Int> = { _, _, _ -> Platform.Said(DENIED) },
    /** `getInitialCaller().checkContentUriPermission`: (uri, mode flag) → the result code, or a throw. */
    val launchAnswer: (String, Int) -> Platform<Int> = { _, _ -> Platform.Said(DENIED) },
    val names: Map<Int, String> = mapOf(CALLER to "com.caller", OTHER to "com.other", SHELL to "app.tileshell"),
) : UriAccessPort {
    val asked = mutableListOf<String>()

    override fun shellUid(): Int = shell
    override fun uidOf(packageName: String): Platform<Int> {
        asked += "uidOf $packageName"
        return uids[packageName]?.let { Platform.Said(it) } ?: Platform.Threw("NameNotFoundException")
    }
    override fun provider(authority: String): Platform<ProviderFacts?> { asked += "provider $authority"; return Platform.Said(providers[authority]) }
    override fun holdsGrant(uri: String, uid: Int, modeFlag: Int): Platform<Int> {
        asked += "grant $uri $uid $modeFlag"
        return Platform.Said(if (Triple(uri, uid, modeFlag) in grants) GRANTED else DENIED)
    }
    override fun providerSays(uri: String, uid: Int, modeFlag: Int): Platform<Int> { asked += "providerSays $uri $uid $modeFlag"; return providerAnswer(uri, uid, modeFlag) }
    override fun launchAnswer(uri: String, modeFlag: Int): Platform<Int> { asked += "launch $uri $modeFlag"; return launchAnswer.invoke(uri, modeFlag) }
    override fun launchedFromUid(): Platform<Int> = launchedFrom
    override fun nameOfUid(uid: Int): Platform<String?> { asked += "name $uid"; return Platform.Said(names[uid]) }

    companion object {
        const val CALLER = 10123
        const val OTHER = 10200
        const val SHELL = 10077
        const val MEDIA = 10050
        const val CONTACTS = 10001
        const val SYSTEM = 1000
        const val HOLDS_CONTACTS = 10300
        const val GRANTED = 0
        const val DENIED = -1
        const val READ = 1
        const val WRITE = 2
        val YES: Platform<Int> = Platform.Said(GRANTED)
        val NO: Platform<Int> = Platform.Said(DENIED)
    }
}
