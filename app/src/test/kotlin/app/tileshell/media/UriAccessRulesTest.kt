package app.tileshell.media

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.tileshell.media.FakeUriAccess.Companion.CALLER
import app.tileshell.media.FakeUriAccess.Companion.DENIED
import app.tileshell.media.FakeUriAccess.Companion.GRANTED
import app.tileshell.media.FakeUriAccess.Companion.NO
import app.tileshell.media.FakeUriAccess.Companion.OTHER
import app.tileshell.media.FakeUriAccess.Companion.READ
import app.tileshell.media.FakeUriAccess.Companion.SHELL
import app.tileshell.media.FakeUriAccess.Companion.WRITE
import app.tileshell.media.FakeUriAccess.Companion.YES
import app.tileshell.media.UriAccessRules.ReadWhy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE ONE RULE (phase 17 trust fixes, round 3: A2-F1, A2-F2, C2-L1, C2-L8): who may make the shell write (the capture
 * answer) or read (the viewer, the player) a content URI — every branch, every combination, over a fake platform.
 */
class UriAccessRulesTest {
    private val own = "content://com.caller.files/cache/out.jpg"
    private val mediaRow = "content://media/external/images/media/41"
    private val contact = "content://com.android.contacts/contacts/7/display_photo"
    private val othersFile = "content://com.other.files/share/p.jpg"

    private fun write(access: FakeUriAccess, uri: String, recipient: String? = "com.caller") =
        UriAccessRules.captureMayWrite(access, ContentUriText.parse(uri), recipient)

    private fun read(access: FakeUriAccess, uri: String) = UriAccessRules.starterMayRead(access, ContentUriText.parse(uri))

    private val launchYes = { _: String, _: Int -> YES }

    @Test
    fun `the rule's constants are the platform's`() {
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, UriAccessRules.READ)
        assertEquals(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, UriAccessRules.WRITE)
        assertEquals(PackageManager.PERMISSION_GRANTED, UriAccessRules.GRANTED)
        assertEquals(Build.VERSION_CODES.VANILLA_ICE_CREAM, UriAccessRules.API_ASKS_PROVIDER)
        assertEquals(CaptureOutputGuard.FLAG_GRANT_WRITE, UriAccessRules.WRITE)
    }

    // ---------------------------------------------------------------------------------------------------- WRITE

    @Test
    fun `WRITE the legitimate capture - qa-capture's own FileProvider output, and the starter could write it - accepted`() {
        val access = FakeUriAccess(launchAnswer = { uri, mode -> if (uri == own && mode == WRITE) YES else NO })
        assertEquals(UriAccessRules.Write(allowed = true, recipientMayWrite = true, starter = "granted"), write(access, own))
        // The recipient's own provider is its own; then the platform is asked about the STARTER, for WRITE, on that very text.
        assertEquals(listOf("uidOf com.caller", "provider com.caller.files", "launch $own $WRITE"), access.asked)
    }

    @Test
    fun `WRITE A2-F1 the recipient may write but the starter may not - refused`() {
        // A go-between T with no access forwards the result of V: the recipient (V) owns the provider, the platform's
        // launch answer is about T.
        val result = write(FakeUriAccess(launchAnswer = { _, _ -> NO }), own)
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = true, starter = "denied"), result)
        // The same when the recipient holds a write grant, and when MediaStore itself says the recipient may.
        assertFalse(write(FakeUriAccess(grants = setOf(Triple(contact, CALLER, WRITE))), contact).allowed)
        assertFalse(write(FakeUriAccess(providerAnswer = { _, _, _ -> YES }), mediaRow).allowed)
        // A READ answer for the starter is not a write answer.
        assertFalse(write(FakeUriAccess(launchAnswer = { _, mode -> if (mode == READ) YES else NO }), own).allowed)
    }

    @Test
    fun `WRITE A2-F1 the launch answer throws - refused, and the line's word names the class only`() {
        for (thrown in listOf("SecurityException", "IllegalArgumentException", "RemoteException", "NoSuchMethodError", "DeadObjectException")) {
            val result = write(FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw(thrown) }), own)
            assertEquals(thrown, UriAccessRules.Write(allowed = false, recipientMayWrite = true, starter = "threw $thrown"), result)
        }
        // Whatever a thrown class is called, the word is letters and digits, bounded.
        val odd = write(FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw("Evil\n[camera] capture answer: RESULT_OK " + "x".repeat(200)) }), own)
        assertEquals("threw EvilcameracaptureanswerRESULT_OK" + "x".repeat(60 - 32), odd.starter)
    }

    @Test
    fun `WRITE a result code that is not PERMISSION_GRANTED is no - from the launch, the grant and the provider alike`() {
        for (code in listOf(DENIED, 1, 2, -2, Int.MIN_VALUE)) {
            assertFalse("launch $code", write(FakeUriAccess(launchAnswer = { _, _ -> Platform.Said(code) }), own).allowed)
            assertFalse("provider $code", write(FakeUriAccess(providerAnswer = { _, _, _ -> Platform.Said(code) }, launchAnswer = launchYes), mediaRow).allowed)
        }
        // A port whose grant check answers a non-zero code for everything grants nothing.
        val odd = object : UriAccessPort by FakeUriAccess(launchAnswer = launchYes) {
            override fun holdsGrant(uri: String, uid: Int, modeFlag: Int): Platform<Int> = Platform.Said(1)
        }
        assertFalse(UriAccessRules.captureMayWrite(odd, ContentUriText.parse(contact), "com.caller").allowed)
    }

    @Test
    fun `WRITE A2-F2 a system-uid recipient and a contacts URI - refused on every Android version, whatever the manifest answer would be`() {
        for (api in listOf(34, 35, 36)) {
            // checkContentUriPermissionFull says yes for the system uid on every provider — and is never asked here.
            val access = FakeUriAccess(apiLevel = api, providerAnswer = { _, _, _ -> YES }, launchAnswer = launchYes)
            val result = write(access, contact, recipient = "android")
            assertEquals("API $api", UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked"), result)
            assertEquals("API $api: the provider's manifest answer was not asked for a provider that is not forceUriPermissions",
                listOf("uidOf android", "provider com.android.contacts", "grant $contact ${FakeUriAccess.SYSTEM} $WRITE"), access.asked)
        }
    }

    @Test
    fun `WRITE A2-F2 a recipient holding WRITE_CONTACTS and a contacts URI - refused on every Android version`() {
        for (api in listOf(34, 35, 36)) {
            val access = FakeUriAccess(apiLevel = api, providerAnswer = { _, uid, _ -> if (uid == FakeUriAccess.HOLDS_CONTACTS) YES else NO }, launchAnswer = launchYes)
            assertFalse("API $api", write(access, contact, recipient = "com.holds.contacts").allowed)
            assertTrue("API $api: no provider answer and no launch answer was asked", access.asked.none { it.startsWith("providerSays") || it.startsWith("launch") })
        }
        // An exported provider with no write permission at all (mms parts): the manifest answer is yes for every uid.
        val mms = "content://mms/part/9"
        val open = FakeUriAccess(providers = mapOf("mms" to ProviderFacts(10010, false)), providerAnswer = { _, _, _ -> YES }, launchAnswer = launchYes)
        assertFalse(write(open, mms).allowed)
    }

    @Test
    fun `WRITE the caller's own MediaStore row - API 35 and later accepted when MediaStore and the launch answer say so - API 34 refused`() {
        val asked = mutableListOf<Triple<String, Int, Int>>()
        fun media(api: Int) = FakeUriAccess(
            apiLevel = api,
            providerAnswer = { uri, uid, mode -> asked += Triple(uri, uid, mode); if (uri == mediaRow && uid == CALLER && mode == WRITE) YES else NO },
            launchAnswer = { uri, mode -> if (uri == mediaRow && mode == WRITE) YES else NO },
        )
        for (api in listOf(35, 36)) {
            asked.clear()
            assertEquals("API $api", UriAccessRules.Write(allowed = true, recipientMayWrite = true, starter = "granted"), write(media(api), mediaRow))
            assertEquals("the provider is asked about the recipient's uid, that very URI, for WRITE", listOf(Triple(mediaRow, CALLER, WRITE)), asked)
        }
        // A row that is not the caller's: MediaStore says no.
        assertFalse(write(media(36), "content://media/external/images/media/42").allowed)
        // API 34: the provider cannot be asked — refused, and it was not asked.
        asked.clear()
        val api34 = media(34)
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked"), write(api34, mediaRow))
        assertEquals(emptyList<Triple<String, Int, Int>>(), asked)
        assertTrue(api34.asked.none { it.startsWith("launch") || it.startsWith("providerSays") })
    }

    @Test
    fun `WRITE API 34 - (d2) cannot be asked, so (d1) decides alone - the recipient's own provider and a grant it holds are accepted`() {
        val ownProvider = FakeUriAccess(apiLevel = 34)
        assertEquals(UriAccessRules.Write(allowed = true, recipientMayWrite = true, starter = "not available"), write(ownProvider, own))
        assertTrue("the launch answer does not exist on API 34 and is not called", ownProvider.asked.none { it.startsWith("launch") })
        assertTrue(write(FakeUriAccess(apiLevel = 34, grants = setOf(Triple(mediaRow, CALLER, WRITE))), mediaRow).allowed)
        // THE RESIDUAL the KDoc records for the owner: on API 34 a go-between that forwards the result of a victim that
        // owns the provider is not seen (the platform does not name it) — this is that case, and it is accepted.
        assertTrue(write(FakeUriAccess(apiLevel = 34, launchedFrom = Platform.Said(-1)), own).allowed)
        // Where the platform DOES name the starter (it shared its identity), the forward is refused on API 34 too.
        assertFalse(write(FakeUriAccess(apiLevel = 34, launchedFrom = Platform.Said(OTHER)), own).allowed)
    }

    @Test
    fun `WRITE an explicit write grant the recipient holds for exactly that URI - (d1) - and nothing else is one`() {
        assertTrue(write(FakeUriAccess(grants = setOf(Triple(contact, CALLER, WRITE)), launchAnswer = launchYes), contact).allowed)
        // A grant for another URI, a grant another uid holds, and a READ grant are not the recipient's write grant.
        assertFalse(write(FakeUriAccess(grants = setOf(Triple("$contact/x", CALLER, WRITE)), launchAnswer = launchYes), contact).allowed)
        assertFalse(write(FakeUriAccess(grants = setOf(Triple(contact, OTHER, WRITE), Triple(contact, SHELL, WRITE)), launchAnswer = launchYes), contact).allowed)
        assertFalse(write(FakeUriAccess(grants = setOf(Triple(contact, CALLER, READ)), launchAnswer = launchYes), contact).allowed)
    }

    @Test
    fun `WRITE a starter the platform names that is not the recipient - a forwarded result - refused before anything else is asked`() {
        val access = FakeUriAccess(launchedFrom = Platform.Said(OTHER), launchAnswer = launchYes)
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked", forwardedFromUid = OTHER), write(access, own))
        assertEquals(listOf("uidOf com.caller"), access.asked)
        // Named and the recipient itself, or not named: not a forward by this check.
        assertNull(write(FakeUriAccess(launchedFrom = Platform.Said(CALLER), launchAnswer = launchYes), own).forwardedFromUid)
        assertTrue(write(FakeUriAccess(launchedFrom = Platform.Said(CALLER), launchAnswer = launchYes), own).allowed)
        assertTrue(write(FakeUriAccess(launchedFrom = Platform.Threw("RemoteException"), launchAnswer = launchYes), own).allowed)
    }

    @Test
    fun `WRITE no recipient, an unknown package and a uid that cannot be read - refused, and nothing is asked about the URI`() {
        val everything = { FakeUriAccess(providerAnswer = { _, _, _ -> YES }, launchAnswer = launchYes, grants = setOf(Triple(own, CALLER, WRITE))) }
        val none = everything()
        assertFalse(write(none, own, recipient = null).allowed)
        assertEquals(emptyList<String>(), none.asked)
        val unknown = everything()
        assertFalse(write(unknown, own, recipient = "com.not.installed").allowed)
        assertEquals(listOf("uidOf com.not.installed"), unknown.asked)
        val negative = FakeUriAccess(uids = mapOf("com.caller" to -1), providers = mapOf("com.caller.files" to ProviderFacts(-1, false)), launchAnswer = launchYes)
        assertFalse(write(negative, own).allowed)
    }

    @Test
    fun `WRITE another user's provider, an escaped or empty authority and any scheme but content - refused whatever the platform would say`() {
        for (uri in listOf(
            "content://10@media/external/images/media/41", "content://10%40media/external/images/media/41", "content://0@com.caller.files/x",
            "content://media%2Fx/y", "content:///x", "content:x", "content:/x", "file:///sdcard/x.jpg", "http://h/x", "CONTENT://media/x", "/sdcard/x.jpg", "",
        )) {
            val access = FakeUriAccess(
                providers = listOf("media", "10@media", "10%40media", "", "h", "0@com.caller.files", "media%2Fx").associateWith { ProviderFacts(CALLER, true) },
                grants = setOf(Triple(uri, CALLER, WRITE)), providerAnswer = { _, _, _ -> YES }, launchAnswer = launchYes,
            )
            assertFalse(uri, write(access, uri).allowed)
            assertEquals("$uri: the platform was asked nothing", emptyList<String>(), access.asked)
        }
    }

    @Test
    fun `WRITE a provider nobody declares, and a provider read that throws - refused`() {
        assertFalse(write(FakeUriAccess(launchAnswer = launchYes), "content://com.nobody.declares/x").allowed)
        val throwing = object : UriAccessPort by FakeUriAccess(launchAnswer = launchYes) {
            override fun provider(authority: String): Platform<ProviderFacts?> = Platform.Threw("SecurityException")
        }
        assertFalse(UriAccessRules.captureMayWrite(throwing, ContentUriText.parse(own), "com.caller").allowed)
    }

    @Test
    fun `WRITE every combination - accepted only when the recipient may write AND, from API 35, the starter could`() {
        var cases = 0
        var accepted = 0
        val answers = listOf(YES, NO, Platform.Threw("SecurityException"))
        for (api in listOf(34, 35, 36)) for (owner in listOf(CALLER, OTHER)) for (grant in listOf(false, true)) for (force in listOf(false, true))
            for (says in answers) for (launch in answers) for (starter in listOf(-1, CALLER, OTHER)) {
                val uri = "content://some.provider/item/1"
                val access = FakeUriAccess(
                    apiLevel = api, launchedFrom = Platform.Said(starter), providers = mapOf("some.provider" to ProviderFacts(owner, force)),
                    grants = if (grant) setOf(Triple(uri, CALLER, WRITE)) else emptySet(), providerAnswer = { _, _, _ -> says }, launchAnswer = { _, _ -> launch },
                )
                val d1 = owner == CALLER || grant || (force && api >= 35 && says == YES)
                val d2 = api < 35 || launch == YES
                val forwarded = starter == OTHER
                val expected = d1 && d2 && !forwarded
                val case = "api=$api owner=$owner grant=$grant force=$force says=$says launch=$launch starter=$starter"
                assertEquals(case, expected, write(access, uri).allowed)
                if (!force || api < 35) assertTrue("$case: a provider answer was asked", access.asked.none { it.startsWith("providerSays") })
                if (api < 35) assertTrue("$case: a launch answer was asked", access.asked.none { it.startsWith("launch") })
                cases++
                if (expected) accepted++
            }
        assertEquals(3 * 2 * 2 * 2 * 3 * 3 * 3, cases)
        assertTrue("the table accepts some and refuses most ($accepted of $cases)", accepted in 1 until cases / 2)
    }

    // ----------------------------------------------------------------------------------------------------- READ

    @Test
    fun `READ the shell's own launch is the shell's - and only a named starter that IS the shell`() {
        assertTrue(UriAccessRules.isOwnLaunch(FakeUriAccess(launchedFrom = Platform.Said(SHELL))))
        assertFalse(UriAccessRules.isOwnLaunch(FakeUriAccess(launchedFrom = Platform.Said(OTHER))))
        assertFalse("the platform does not say", UriAccessRules.isOwnLaunch(FakeUriAccess(launchedFrom = Platform.Said(-1))))
        assertFalse("the read threw", UriAccessRules.isOwnLaunch(FakeUriAccess(launchedFrom = Platform.Threw("RemoteException"))))
        assertFalse("-1 is never anybody, not even a shell whose own uid reads -1", UriAccessRules.isOwnLaunch(FakeUriAccess(shell = -1, launchedFrom = Platform.Said(-1))))
        assertNull(UriAccessRules.starterUid(FakeUriAccess(launchedFrom = Platform.Said(-1))))
        assertNull(UriAccessRules.starterUid(FakeUriAccess(launchedFrom = Platform.Said(-7))))
        assertEquals(OTHER, UriAccessRules.starterUid(FakeUriAccess(launchedFrom = Platform.Said(OTHER))))
    }

    @Test
    fun `READ the legitimate Open with - a file manager's own FileProvider item with a read grant, starter unnamed - the launch answer admits it`() {
        // The platform's launch answer is yes for the provider's owner (the device's leg (k) on API 36).
        val access = FakeUriAccess(launchAnswer = { uri, mode -> if (uri == othersFile && mode == READ) YES else NO })
        assertEquals(UriAccessRules.Read(ReadWhy.LAUNCH_ACCESS, "granted"), read(access, othersFile))
        assertEquals("an unnamed starter: only the launch is asked, for READ, on that very text", listOf("launch $othersFile $READ"), access.asked)
        // A WRITE answer is not a read answer; an answer for another URI is not this one's.
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "denied"), read(FakeUriAccess(launchAnswer = { _, mode -> if (mode == WRITE) YES else NO }), othersFile))
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "denied"), read(access, mediaRow))
    }

    @Test
    fun `READ the launch answer denied or thrown - refused, and the outcome class is kept for the line`() {
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "denied"), read(FakeUriAccess(), mediaRow))
        for (thrown in listOf("SecurityException", "IllegalArgumentException", "RemoteException")) {
            assertEquals(UriAccessRules.Read(ReadWhy.NONE, "threw $thrown"), read(FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw(thrown) }), mediaRow))
        }
        assertEquals("launch answer read: denied", UriAccessRules.launchLine(UriAccessRules.READ, "denied"))
        assertEquals("launch answer write: threw SecurityException", UriAccessRules.launchLine(UriAccessRules.WRITE, "threw SecurityException"))
    }

    @Test
    fun `READ API 34 - an unnamed starter is refused whatever it could read, and the launch answer is not called`() {
        val access = FakeUriAccess(apiLevel = 34, launchAnswer = launchYes, providerAnswer = { _, _, _ -> YES }, grants = setOf(Triple(mediaRow, SHELL, READ)))
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "not available"), read(access, mediaRow))
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "not available"), read(access, othersFile))
        assertEquals(emptyList<String>(), access.asked)
    }

    @Test
    fun `READ a named starter - its own provider, a read grant it holds, or MediaStore's own answer - each admits it`() {
        val named = Platform.Said(OTHER)
        assertEquals(UriAccessRules.Read(ReadWhy.OWN_PROVIDER, "not asked"), read(FakeUriAccess(launchedFrom = named), othersFile))
        assertEquals(UriAccessRules.Read(ReadWhy.URI_GRANT, "not asked"), read(FakeUriAccess(launchedFrom = named, grants = setOf(Triple(own, OTHER, READ))), own))
        val media = FakeUriAccess(launchedFrom = named, providerAnswer = { uri, uid, mode -> if (uri == mediaRow && uid == OTHER && mode == READ) YES else NO })
        assertEquals(UriAccessRules.Read(ReadWhy.PROVIDER_ALLOWS, "not asked"), read(media, mediaRow))
        // Somebody else's provider, somebody else's grant, a WRITE grant, the shell's own grant: none is this starter's read.
        assertFalse(read(FakeUriAccess(launchedFrom = named), own).allowed)
        assertFalse(read(FakeUriAccess(launchedFrom = named, grants = setOf(Triple(own, CALLER, READ), Triple(own, SHELL, READ), Triple(own, OTHER, WRITE))), own).allowed)
        // API 34: MediaStore cannot be asked.
        assertFalse(read(FakeUriAccess(apiLevel = 34, launchedFrom = named, providerAnswer = { _, _, _ -> YES }), mediaRow).allowed)
    }

    @Test
    fun `READ C2-L8 the provider's own answer is asked of a forceUriPermissions provider only`() {
        // An exported provider with no read permission, and a system-uid starter: the manifest answer is yes — not asked.
        for (starter in listOf(OTHER, FakeUriAccess.SYSTEM)) {
            val access = FakeUriAccess(launchedFrom = Platform.Said(starter), providerAnswer = { _, _, _ -> YES })
            assertEquals("starter $starter", UriAccessRules.Read(ReadWhy.NONE, "denied"), read(access, contact))
            assertTrue(access.asked.none { it.startsWith("providerSays") })
        }
    }

    @Test
    fun `READ C2-L1 an authority with a user id is handed to the platform as it is - shown where the launch answer says so - and never taken as the starter's own provider`() {
        for (uri in listOf("content://10@media/external/images/media/41", "content://10%40media/external/images/media/41")) {
            // A work-profile "Open with": the platform says the starter could read it.
            val granted = FakeUriAccess(launchAnswer = { asked, mode -> if (asked == uri && mode == READ) YES else NO })
            assertEquals(uri, UriAccessRules.Read(ReadWhy.LAUNCH_ACCESS, "granted"), read(granted, uri))
            assertEquals(listOf("launch $uri $READ"), granted.asked)
            // Even a table that calls such an authority the starter's own, or MediaStore's, does not admit it: the
            // provider is not looked up at all, so neither the own-provider nor the provider-says branch can fire.
            val authority = ContentUriText.parse(uri).authority!!
            val tables = FakeUriAccess(
                launchedFrom = Platform.Said(OTHER), providers = mapOf(authority to ProviderFacts(OTHER, true), "media" to ProviderFacts(OTHER, true)), providerAnswer = { _, _, _ -> YES },
            )
            assertEquals(uri, UriAccessRules.Read(ReadWhy.NONE, "denied"), read(tables, uri))
            assertEquals(listOf("grant $uri $OTHER $READ", "launch $uri $READ"), tables.asked)
            // An explicit grant the named starter holds for that very text is the platform's word too.
            assertEquals(ReadWhy.URI_GRANT, read(FakeUriAccess(launchedFrom = Platform.Said(OTHER), grants = setOf(Triple(uri, OTHER, READ))), uri).why)
        }
    }

    @Test
    fun `READ anything that is not a content URI with an authority - refused, and the platform is asked nothing`() {
        for (uri in listOf("content:///x", "content:x", "content:/x", "file:///sdcard/x.jpg", "http://h/x", "CONTENT://media/x", "/sdcard/x.jpg", "")) {
            val access = FakeUriAccess(launchedFrom = Platform.Said(OTHER), launchAnswer = launchYes, providerAnswer = { _, _, _ -> YES }, grants = setOf(Triple(uri, OTHER, READ)))
            assertEquals(uri, UriAccessRules.Read(ReadWhy.NONE, "not asked"), read(access, uri))
            assertEquals(uri, emptyList<String>(), access.asked)
        }
    }

    @Test
    fun `READ every combination - shown only by the launch answer, or by what the platform says of a NAMED starter`() {
        var cases = 0
        val answers = listOf(YES, NO, Platform.Threw("IllegalArgumentException"))
        for (api in listOf(34, 35, 36)) for (starter in listOf(-1, OTHER)) for (owner in listOf(OTHER, CALLER)) for (grant in listOf(false, true))
            for (shellGrant in listOf(false, true)) for (force in listOf(false, true)) for (says in answers) for (launch in answers) {
                val uri = "content://some.provider/item/1"
                val access = FakeUriAccess(
                    apiLevel = api, launchedFrom = Platform.Said(starter), providers = mapOf("some.provider" to ProviderFacts(owner, force)),
                    grants = buildSet { if (grant) add(Triple(uri, OTHER, READ)); if (shellGrant) add(Triple(uri, SHELL, READ)) },
                    providerAnswer = { _, _, _ -> says }, launchAnswer = { _, _ -> launch },
                )
                val named = starter == OTHER
                // What the SHELL holds never counts; nothing keyed by uid counts for an unnamed starter.
                val expected = (named && (owner == OTHER || grant || (force && api >= 35 && says == YES))) || (api >= 35 && launch == YES)
                assertEquals("api=$api starter=$starter owner=$owner grant=$grant shellGrant=$shellGrant force=$force says=$says launch=$launch", expected, read(access, uri).allowed)
                cases++
            }
        assertEquals(3 * 2 * 2 * 2 * 2 * 2 * 3 * 3, cases)
    }

    // ------------------------------------------------------------------------------- the URI's text, and the lines

    @Test
    fun `A2-S the authority ends at a slash, a backslash, a question mark and a hash - as the platform splits it`() {
        fun authority(text: String) = ContentUriText.parse(text).authority
        assertEquals("com.caller.files", authority("content://com.caller.files/x"))
        assertEquals("com.caller.files", authority("content://com.caller.files?x=//media/1"))
        assertEquals("com.caller.files", authority("content://com.caller.files#//media/1"))
        assertEquals("com.caller.files", authority("content://com.caller.files\\@media/1"))
        assertEquals("com.caller.files", authority("content://com.caller.files"))
        assertEquals("", authority("content://?media"))
        assertEquals("", authority("content://#media"))
        // So a query or a fragment cannot smuggle another authority past the rule: the provider looked up is the real one.
        for (uri in listOf("content://com.other.files?@com.caller.files/x", "content://com.other.files#com.caller.files/x", "content://com.other.files?//com.caller.files/x")) {
            val access = FakeUriAccess(launchAnswer = launchYes)
            assertFalse(uri, write(access, uri).allowed)
            assertEquals(uri, listOf("uidOf com.caller", "provider com.other.files", "grant $uri $CALLER $WRITE"), access.asked)
        }
    }

    @Test
    fun `A2-S the rule uses the caller's text exactly - nothing trimmed, no case changed`() {
        val padded = " $own"
        assertEquals(" content", ContentUriText.parse(padded).scheme)
        assertEquals(padded, ContentUriText.parse(padded).text)
        for (uri in listOf(padded, "$own ", "Content://com.caller.files/cache/out.jpg", "\tcontent://com.caller.files/x")) {
            if (ContentUriText.parse(uri).scheme == "content") continue
            assertFalse("[$uri]", write(FakeUriAccess(launchAnswer = launchYes), uri).allowed)
        }
        // The authority's case is the text's: an upper-cased authority is another provider, looked up as written.
        val upper = "content://COM.CALLER.FILES/cache/out.jpg"
        val access = FakeUriAccess(launchAnswer = launchYes)
        assertFalse(write(access, upper).allowed)
        assertEquals(listOf("uidOf com.caller", "provider COM.CALLER.FILES", "grant $upper $CALLER $WRITE"), access.asked)
        // And the text the platform is asked about is the caller's own, trailing space and all.
        val trailing = "$own "
        val asks = FakeUriAccess(launchAnswer = launchYes)
        write(asks, trailing)
        assertEquals(listOf("uidOf com.caller", "provider com.caller.files", "launch $trailing $WRITE"), asks.asked)
    }

    @Test
    fun `A2-S an authority in a line is an authority's characters and at most 80 of them`() {
        assertEquals("com.caller.files", UriAccessRules.lineAuthority("com.caller.files"))
        assertEquals("10@media", UriAccessRules.lineAuthority("10@media"))
        assertEquals("", UriAccessRules.lineAuthority(null))
        assertEquals("acameracaptureanswerRESULT_OK", UriAccessRules.lineAuthority("a\n[camera] capture answer: RESULT_OK"))
        assertEquals(80, UriAccessRules.lineAuthority("a".repeat(500)).length)
        assertEquals("a40b", UriAccessRules.lineAuthority("a%40b"))
    }
}
