package app.tileshell.media

import app.tileshell.media.FakeUriAccess.Companion.CALLER
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
 * The owner's ruling D1 (a), 2026-10-31: a SHARED-LIBRARY item (a MediaStore `content://media/...` row) another app
 * opens "with" the viewer or the player, or names as the Camera's output, is admitted on a grant the shell HOLDS for
 * that exact text, carried with the launch's own flag ([UriAccessRules.launchGrant]). The fake port cannot say where a
 * held grant came from, so these tests cannot tell this launch's grant from a stale one: the accepted residual (the
 * owner's ruling of 2026-11-01; UriAccess.kt "WHAT THE LAUNCH GRANT LEAVES OPEN") is by construction invisible here.
 * Every refusal the three trust rounds put in place is pinned, so a change that over-admits fails a named test.
 */
class UriAccessLaunchGrantTest {
    private val mediaRow = "content://media/external/images/media/41"
    private val videoRow = "content://media/external_primary/video/media/12"
    private val contact = "content://com.android.contacts/contacts/7/display_photo"
    private val othersFile = "content://com.other.files/share/p.jpg"
    private val callersFile = "content://com.caller.files/cache/out.jpg"
    private val picker = "content://media/picker/0/com.android.providers.media.photopicker/media/41"

    /**
     * An unnamed app's launch on an API [api] phone: the launch intent's [flags], its [data] and [clip], the grants the
     * platform holds ([grants]: by default the one it issued the SHELL for that launch), and the shell's persisted ones.
     */
    private fun launched(
        uri: String = mediaRow, mode: Int = READ, api: Int = 36, flags: Int = mode, data: String? = if (mode == READ) uri else null,
        clip: List<String> = if (mode == WRITE) listOf(uri) else emptyList(), grants: Set<Triple<String, Int, Int>> = setOf(Triple(uri, SHELL, mode)),
        persisted: List<String> = emptyList(), from: Int = -1, providers: Map<String, ProviderFacts>? = null,
        providerAnswer: (String, Int, Int) -> Platform<Int> = { _, _, _ -> NO }, launchAnswer: (String, Int) -> Platform<Int> = { _, _ -> NO },
    ) = FakeUriAccess(
        apiLevel = api, launchedFrom = Platform.Said(from), grants = grants, providerAnswer = providerAnswer, launchAnswer = launchAnswer,
        launchFlags = Platform.Said(flags), launchData = Platform.Said(data), launchClip = Platform.Said(clip), persisted = Platform.Said(persisted),
        providers = providers ?: mapOf(
            "com.caller.files" to ProviderFacts(CALLER, false), "com.other.files" to ProviderFacts(OTHER, false),
            "media" to ProviderFacts(FakeUriAccess.MEDIA, true), "com.android.contacts" to ProviderFacts(FakeUriAccess.CONTACTS, false),
        ),
    )

    private fun read(access: FakeUriAccess, uri: String = mediaRow) = UriAccessRules.helperMayRead(access, ContentUriText.parse(uri))

    private fun grant(access: FakeUriAccess, uri: String = mediaRow, mode: Int = READ) = UriAccessRules.launchGrant(access, ContentUriText.parse(uri), mode)

    private fun write(access: FakeUriAccess, uri: String = mediaRow, recipient: String? = "com.caller") =
        UriAccessRules.captureMayWrite(access, ContentUriText.parse(uri), recipient)

    /** MediaStore, asked (API 35+), says the RECIPIENT may write [uri]: the caller's own row. */
    private fun recipientsRow(uri: String = mediaRow): (String, Int, Int) -> Platform<Int> = { u, uid, m -> if (u == uri && uid == CALLER && m == WRITE) YES else NO }

    // ------------------------------------------------------------------------------------------------- READ: admitted

    @Test
    fun `READ D1 a library item an unnamed app opened WITH the read grant flag, the platform's grant to the shell held - admitted on API 34, 35 and 36`() {
        for (api in listOf(34, 35, 36)) {
            val access = launched(api = api)
            val launch = if (api < 35) "not available" else "denied"
            assertEquals("API $api", UriAccessRules.Read(ReadWhy.LAUNCH_GRANT, launch, "held"), read(access))
            // The launch answer first (35+), then the provider's kind, the shell's own grant for that very text and READ, and its persisted list.
            val expected = (if (api < 35) emptyList() else listOf("launch $mediaRow $READ")) + listOf("provider media", "grant $mediaRow $SHELL $READ", "persisted")
            assertEquals("API $api", expected, access.asked)
            assertEquals("API $api", listOf("flags", "data", "clip"), access.launchReads)
        }
        // A video row on another volume name, and the URI in the ClipData rather than the data: the same.
        assertEquals(ReadWhy.LAUNCH_GRANT, read(launched(uri = videoRow), videoRow).why)
        assertEquals(ReadWhy.LAUNCH_GRANT, read(launched(data = null, clip = listOf(mediaRow))).why)
        // A grant from a launch that asked for READ and WRITE is a read grant too; a PREFIX grant flag changes nothing here.
        assertEquals(ReadWhy.LAUNCH_GRANT, read(launched(flags = READ or WRITE)).why)
        assertEquals(ReadWhy.LAUNCH_GRANT, read(launched(flags = READ or 0x80)).why)
        assertEquals("launch grant read: held", UriAccessRules.grantLine(READ, "held"))
        assertEquals("launch grant write: none held", UriAccessRules.grantLine(WRITE, "none held"))
        assertEquals("a grant from the launch", ReadWhy.LAUNCH_GRANT.word)
    }

    @Test
    fun `READ a launch that names only a prefix of the item - as its data or a clip entry - refused`() {
        // The D1 review's M01: the item must BE the launch's data or one of its clip entries, by its whole text.
        val prefix = "content://media/external/images/media/4"
        assertEquals(UriAccessRules.GRANT_NOT_IN_LAUNCH, grant(launched(data = prefix)))
        assertEquals(ReadWhy.NONE, read(launched(data = prefix)).why)
        assertEquals(UriAccessRules.GRANT_NOT_IN_LAUNCH, grant(launched(data = null, clip = listOf(prefix))))
    }

    @Test
    fun `READ the item as a later clip entry - admitted, as the platform checks and grants every entry`() {
        // The D1 review's M03: not only the first entry is the launch's.
        val other = "content://media/external/images/media/7"
        assertEquals(ReadWhy.LAUNCH_GRANT, read(launched(data = null, clip = listOf(other, mediaRow))).why)
    }

    @Test
    fun `READ a named starter whose own answers say no, and whose launch granted the shell the item - admitted by the launch grant too`() {
        // API 34: MediaStore cannot be asked about a uid; the launch grant is the platform's word all the same.
        val access = launched(api = 34, from = OTHER)
        assertEquals(UriAccessRules.Read(ReadWhy.LAUNCH_GRANT, "not available", "held"), read(access))
    }

    @Test
    fun `READ what the existing rule admits is admitted as before - and the launch grant is not even read`() {
        val byLaunch = launched(launchAnswer = { u, m -> if (u == mediaRow && m == READ) YES else NO })
        assertEquals(UriAccessRules.Read(ReadWhy.LAUNCH_ACCESS, "granted"), read(byLaunch))
        assertEquals(emptyList<String>(), byLaunch.launchReads)
        val ownProvider = launched(uri = othersFile, from = OTHER)
        assertEquals(UriAccessRules.Read(ReadWhy.OWN_PROVIDER, "not asked"), read(ownProvider, othersFile))
        assertEquals(emptyList<String>(), ownProvider.launchReads)
    }

    @Test
    fun `READ Clock's rule (starterMayRead) is unchanged - the launch grant is the exported viewer's and player's only`() {
        for (api in listOf(34, 36)) {
            val access = launched(api = api)
            val launch = if (api < 35) "not available" else "denied"
            assertEquals("API $api", UriAccessRules.Read(ReadWhy.NONE, launch), UriAccessRules.starterMayRead(access, ContentUriText.parse(mediaRow)))
            assertEquals("API $api: the launch intent is not read by starterMayRead", emptyList<String>(), access.launchReads)
        }
    }

    // ------------------------------------------------------------------------------------------------- READ: refused

    @Test
    fun `READ no read grant flag on the launch - the platform checked nobody - refused whatever grant the shell holds, and nothing more is asked`() {
        for (flags in listOf(0, WRITE, 0x40, 0x80, 0x10000000)) {
            val access = launched(flags = flags)
            assertEquals("flags $flags", UriAccessRules.Read(ReadWhy.NONE, "denied", null), read(access))
            assertEquals("flags $flags", listOf("launch $mediaRow $READ"), access.asked)
            assertEquals("flags $flags", listOf("flags"), access.launchReads)
        }
    }

    @Test
    fun `READ the shell holds no grant for that text - no grant was issued (a system or root starter, an unchecked start path) - refused`() {
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "denied", "none held"), read(launched(grants = emptySet())))
        // The shell's grant for another row, a WRITE-only grant, the STARTER's own grant, another uid's: not the shell's read grant.
        val notIt = listOf(
            Triple("$mediaRow/x", SHELL, READ), Triple("content://media/external/images/media/42", SHELL, READ), Triple(mediaRow, SHELL, WRITE),
            Triple(mediaRow, CALLER, READ), Triple(mediaRow, OTHER, READ), Triple(mediaRow, -1, READ),
        )
        for (g in notIt) assertEquals("$g", "none held", read(launched(grants = setOf(g))).grant)
        // A result code that is not PERMISSION_GRANTED, a check that throws, and a shell uid the platform cannot give.
        for (code in listOf<Platform<Int>>(Platform.Said(1), Platform.Said(-1), Platform.Said(Int.MIN_VALUE), Platform.Threw("SecurityException"))) {
            val odd = object : UriAccessPort by launched() {
                override fun holdsGrant(uri: String, uid: Int, modeFlag: Int): Platform<Int> = code
            }
            assertFalse("$code", UriAccessRules.helperMayRead(odd, ContentUriText.parse(mediaRow)).allowed)
        }
        assertFalse(read(FakeUriAccess(shell = -1, launchFlags = Platform.Said(READ), launchData = Platform.Said(mediaRow), grants = setOf(Triple(mediaRow, -1, READ)))).allowed)
    }

    @Test
    fun `READ the URI is not the launch's data nor in its ClipData - the platform's check was about other URIs - refused`() {
        val elsewhere = launched(data = "content://media/external/images/media/42", clip = listOf("content://media/external/images/media/43"))
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "denied", "not in the launch"), read(elsewhere))
        assertFalse(read(launched(data = null, clip = emptyList())).allowed)
        // The same text only: no trimming, no case, no user id added.
        for (other in listOf(" $mediaRow", "$mediaRow ", "content://MEDIA/external/images/media/41", "content://0@media/external/images/media/41")) {
            assertEquals(other, "not in the launch", read(launched(data = other)).grant)
        }
    }

    @Test
    fun `READ A2-F2 C2-L8 a provider that is not forceUriPermissions - the platform's grant check took the manifest's word - refused`() {
        // The shell holds a grant the launch gave it for a file manager's FileProvider item and a contact's photo: on API
        // 34 nothing proves the starter could read them, so the launch grant does not admit a provider that is not MediaStore's kind.
        for (uri in listOf(othersFile, contact, "content://mms/part/9")) {
            val access = launched(uri = uri, api = 34, providers = mapOf("com.other.files" to ProviderFacts(OTHER, false), "com.android.contacts" to ProviderFacts(FakeUriAccess.CONTACTS, false), "mms" to ProviderFacts(10010, false)))
            assertEquals(uri, UriAccessRules.Read(ReadWhy.NONE, "not available", "not a library item"), read(access, uri))
            assertTrue(uri, access.asked.none { it.startsWith("grant") })
        }
        // A provider nobody declares, and a provider read that throws.
        assertEquals("not a library item", read(launched(providers = emptyMap())).grant)
        val throwing = object : UriAccessPort by launched() {
            override fun provider(authority: String): Platform<ProviderFacts?> = Platform.Threw("SecurityException")
        }
        assertFalse(UriAccessRules.helperMayRead(throwing, ContentUriText.parse(mediaRow)).allowed)
    }

    @Test
    fun `READ another user's provider, an escaped authority, and an address that is not plain - refused before the platform is asked about it`() {
        for (uri in listOf(
            "content://10@media/external/images/media/41", "content://10%40media/external/images/media/41", "content://media%2Fx/y",
            "content://media/external/images/media/4%31", "content://media/external/images/media/41?requireOriginal=1", "content://media/external/images/media/41#x",
            "content://media/external/images/media/41\\x", "content://media/external/images/media/41;x",
        )) {
            val access = launched(uri = uri, providers = mapOf("media" to ProviderFacts(FakeUriAccess.MEDIA, true), "10@media" to ProviderFacts(FakeUriAccess.MEDIA, true), "10%40media" to ProviderFacts(FakeUriAccess.MEDIA, true)))
            assertEquals(uri, "not a library item", read(access, uri).grant)
            assertFalse(uri, read(access, uri).allowed)
        }
        // Not a content URI with an authority: refused, and no launch answer and no grant is asked.
        for (uri in listOf("content:///x", "content:x", "file:///sdcard/x.jpg", "CONTENT://media/external/images/media/41", "")) {
            val access = launched(uri = uri)
            assertFalse(uri, read(access, uri).allowed)
            assertTrue(uri, access.asked.none { it.startsWith("grant") || it.startsWith("launch") })
        }
    }

    @Test
    fun `READ the shell's own persisted grant covers the URI - an older grant, not this launch's - refused`() {
        // Exactly the URI, a path above it (a persisted grant may be a prefix one), the provider itself, a case variant,
        // an escaped form (taken as covering: the rule does not decode), and the photo picker's own URI the Start background keeps.
        for (p in listOf(mediaRow, "content://media/external/images", "content://media/external/images/", "content://media", "content://MEDIA/external/images/media/41", "content://media/external/images/media/4%31", "content://media/x?y")) {
            assertEquals(p, UriAccessRules.Read(ReadWhy.NONE, "denied", "the shell's own persisted grant"), read(launched(persisted = listOf(p))))
        }
        assertEquals("the shell's own persisted grant", read(launched(uri = picker, persisted = listOf(picker)), picker).grant)
        // Persisted grants that do not cover it: another row, a sibling with a shared prefix of characters, another provider, a deeper path.
        for (p in listOf("content://media/external/images/media/42", "content://media/external/images/media/4", "content://com.other.files/share/p.jpg", "$mediaRow/x", picker)) {
            assertEquals(p, ReadWhy.LAUNCH_GRANT, read(launched(persisted = listOf(p))).why)
        }
        // The persisted list cannot be read: refused.
        assertEquals("threw SecurityException", read(FakeUriAccess(launchFlags = Platform.Said(READ), launchData = Platform.Said(mediaRow), grants = setOf(Triple(mediaRow, SHELL, READ)), persisted = Platform.Threw("SecurityException"))).grant)
    }

    @Test
    fun `READ a launch intent that cannot be read - refused, and the line names the class only`() {
        assertEquals("threw BadParcelableException", read(FakeUriAccess(launchFlags = Platform.Threw("BadParcelableException"), grants = setOf(Triple(mediaRow, SHELL, READ)))).grant)
        assertEquals("threw NullPointerException", read(FakeUriAccess(launchFlags = Platform.Said(READ), launchData = Platform.Threw("NullPointerException"), grants = setOf(Triple(mediaRow, SHELL, READ)))).grant)
        assertEquals("threw RuntimeException", read(FakeUriAccess(launchFlags = Platform.Said(READ), launchData = Platform.Said(mediaRow), launchClip = Platform.Threw("RuntimeException"), grants = setOf(Triple(mediaRow, SHELL, READ)))).grant)
        assertEquals("threw Evilline", read(FakeUriAccess(launchFlags = Platform.Threw("Evil\nline"))).grant)
    }

    @Test
    fun `READ a port that does not answer the launch reads - the interface's own answers - refuses`() {
        val base = launched()
        val old = object : UriAccessPort {
            override val apiLevel = 36
            override fun shellUid() = base.shellUid()
            override fun uidOf(packageName: String) = base.uidOf(packageName)
            override fun provider(authority: String) = base.provider(authority)
            override fun holdsGrant(uri: String, uid: Int, modeFlag: Int) = base.holdsGrant(uri, uid, modeFlag)
            override fun providerSays(uri: String, uid: Int, modeFlag: Int) = base.providerSays(uri, uid, modeFlag)
            override fun launchAnswer(uri: String, modeFlag: Int) = base.launchAnswer(uri, modeFlag)
            override fun launchedFromUid() = base.launchedFromUid()
            override fun nameOfUid(uid: Int) = base.nameOfUid(uid)
        }
        assertEquals(UriAccessRules.Read(ReadWhy.NONE, "denied", "threw NotAnswered"), UriAccessRules.helperMayRead(old, ContentUriText.parse(mediaRow)))
        // Each read alone not answered: refused.
        for (which in listOf("data", "clip", "persisted")) {
            val partial = object : UriAccessPort by base {
                override fun launchData() = if (which == "data") Platform.Threw("NotAnswered") else base.launchData()
                override fun launchClipUris() = if (which == "clip") Platform.Threw("NotAnswered") else base.launchClipUris()
                override fun persistedGrants() = if (which == "persisted") Platform.Threw("NotAnswered") else base.persistedGrants()
            }
            assertFalse(which, UriAccessRules.helperMayRead(partial, ContentUriText.parse(mediaRow)).allowed)
        }
    }

    @Test
    fun `READ every combination - admitted only with the read flag, the URI in the launch, MediaStore's kind, the shell's own read grant, no persisted one, a plain address`() {
        var cases = 0
        var admitted = 0
        for (api in listOf(34, 36)) for (flag in listOf(false, true)) for (where in listOf("data", "clip", "neither")) for (force in listOf(true, false))
            for (shellGrant in listOf(READ, WRITE, 0)) for (persisted in listOf(false, true)) for (plain in listOf(true, false)) {
                val uri = if (plain) mediaRow else "$mediaRow?x=1"
                val access = launched(
                    uri = uri, api = api, flags = if (flag) READ else WRITE, data = if (where == "data") uri else null, clip = if (where == "clip") listOf(uri) else emptyList(),
                    grants = if (shellGrant == 0) emptySet() else setOf(Triple(uri, SHELL, shellGrant)), persisted = if (persisted) listOf(uri) else emptyList(),
                    providers = mapOf("media" to ProviderFacts(FakeUriAccess.MEDIA, force)),
                )
                val expected = flag && where != "neither" && force && shellGrant == READ && !persisted && plain
                assertEquals("api=$api flag=$flag where=$where force=$force shellGrant=$shellGrant persisted=$persisted plain=$plain", expected, read(access, uri).allowed)
                cases++
                if (expected) admitted++
            }
        assertEquals(2 * 2 * 3 * 2 * 3 * 2 * 2, cases)
        assertEquals("exactly the two places in the launch, on each API level", 4, admitted)
    }

    // ------------------------------------------------------------------------------------------------ WRITE: admitted

    @Test
    fun `WRITE D1 the caller's own MediaStore row as EXTRA_OUTPUT, the launch's write grant held by the shell - accepted on API 35 and 36`() {
        for (api in listOf(35, 36)) {
            val access = launched(mode = WRITE, api = api, flags = WRITE or READ, providerAnswer = recipientsRow())
            assertEquals("API $api", UriAccessRules.Write(allowed = true, recipientMayWrite = true, starter = "denied", launchGrant = "held"), write(access))
            assertEquals(
                "API $api: (d1) about the recipient, (d2) the launch answer, then the launch grant",
                listOf("uidOf com.caller", "provider media", "grant $mediaRow $CALLER $WRITE", "providerSays $mediaRow $CALLER $WRITE", "launch $mediaRow $WRITE", "provider media", "grant $mediaRow $SHELL $WRITE", "persisted"),
                access.asked,
            )
        }
    }

    @Test
    fun `WRITE the launch answer still admits on its own - the launch grant is not read then`() {
        val access = launched(mode = WRITE, flags = WRITE or READ, providerAnswer = recipientsRow(), launchAnswer = { _, m -> if (m == WRITE) YES else NO })
        assertEquals(UriAccessRules.Write(allowed = true, recipientMayWrite = true, starter = "granted"), write(access))
        assertEquals(emptyList<String>(), access.launchReads)
    }

    // ------------------------------------------------------------------------------------------------- WRITE: refused

    @Test
    fun `WRITE API 34 - MediaStore cannot be asked about the recipient, so (d1) fails and the launch grant is never read - refused`() {
        val access = launched(mode = WRITE, api = 34, flags = WRITE or READ, providerAnswer = { _, _, _ -> YES })
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked"), write(access))
        assertEquals(emptyList<String>(), access.launchReads)
    }

    @Test
    fun `WRITE (d1) MediaStore says the recipient may not write the row - refused however the shell came by its grant`() {
        val access = launched(mode = WRITE, flags = WRITE or READ, providerAnswer = recipientsRow("content://media/external/images/media/42"))
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked"), write(access))
        assertEquals(emptyList<String>(), access.launchReads)
    }

    @Test
    fun `WRITE A2-F1 a starter the platform names that is not the recipient - refused before anything, launch grant or not`() {
        val access = launched(mode = WRITE, flags = WRITE or READ, from = OTHER, providerAnswer = recipientsRow())
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked", forwardedFromUid = OTHER), write(access))
        assertEquals(listOf("uidOf com.caller"), access.asked)
    }

    @Test
    fun `WRITE no write flag, a READ-only grant, the URI not in the ClipData, a persisted grant - each refused`() {
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = true, starter = "denied"), write(launched(mode = WRITE, flags = READ, providerAnswer = recipientsRow())))
        val readOnly = launched(mode = WRITE, flags = WRITE or READ, grants = setOf(Triple(mediaRow, SHELL, READ)), providerAnswer = recipientsRow())
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = true, starter = "denied", launchGrant = "none held"), write(readOnly))
        val notInClip = launched(mode = WRITE, flags = WRITE or READ, clip = listOf("content://media/external/images/media/42"), providerAnswer = recipientsRow())
        assertEquals("not in the launch", write(notInClip).launchGrant)
        assertFalse(write(notInClip).allowed)
        val persisted = launched(mode = WRITE, flags = WRITE or READ, persisted = listOf("content://media/external/images"), providerAnswer = recipientsRow())
        assertEquals("the shell's own persisted grant", write(persisted).launchGrant)
        assertFalse(write(persisted).allowed)
        // The launch answer throwing is not a reason to admit: the launch grant decides alone, and here it is absent.
        val threw = launched(mode = WRITE, flags = WRITE or READ, grants = emptySet(), providerAnswer = recipientsRow(), launchAnswer = { _, _ -> Platform.Threw("IllegalArgumentException") })
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = true, starter = "threw IllegalArgumentException", launchGrant = "none held"), write(threw))
    }

    @Test
    fun `WRITE A2-F1 a provider the recipient owns that is not MediaStore's kind - the launch grant does not stand in for the starter's launch answer`() {
        // Round 3's forwarded case: V owns the provider, a go-between started the capture, the platform's launch answer
        // says no. The platform gave the shell a grant for the launch (the go-between, holding V's grant, could) — but for
        // a provider that is not forceUriPermissions that proves nothing: refused, as before.
        val access = launched(uri = callersFile, mode = WRITE, flags = WRITE or READ)
        assertEquals(UriAccessRules.Write(allowed = false, recipientMayWrite = true, starter = "denied", launchGrant = "not a library item"), write(access, callersFile))
        // A2-F2: a recipient with a permission-wide access and no grant, on a contacts URI, with a launch grant on the shell.
        for (recipient in listOf("android", "com.holds.contacts")) {
            val c = launched(uri = contact, mode = WRITE, flags = WRITE or READ, providerAnswer = { _, _, _ -> YES })
            assertEquals(recipient, UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = "not asked"), write(c, contact, recipient))
        }
    }

    @Test
    fun `WRITE another user's provider and an escaped authority - refused before anything is asked, launch grant or not`() {
        for (uri in listOf("content://10@media/external/images/media/41", "content://10%40media/external/images/media/41")) {
            val access = launched(uri = uri, mode = WRITE, flags = WRITE or READ, providerAnswer = { _, _, _ -> YES }, launchAnswer = { _, _ -> YES })
            assertFalse(uri, write(access, uri).allowed)
            assertEquals(uri, emptyList<String>(), access.asked)
            assertEquals(uri, emptyList<String>(), access.launchReads)
        }
    }

    @Test
    fun `WRITE every combination - accepted only when (d1) holds AND, from API 35, the launch answer or the launch grant says the starter could`() {
        var cases = 0
        var accepted = 0
        val answers = listOf(YES, NO, Platform.Threw("SecurityException"))
        for (api in listOf(34, 35, 36)) for (says in listOf(true, false)) for (launch in answers) for (flag in listOf(true, false)) for (inClip in listOf(true, false))
            for (shellGrant in listOf(WRITE, READ, 0)) for (persisted in listOf(false, true)) for (starter in listOf(-1, CALLER, OTHER)) {
                val access = launched(
                    mode = WRITE, api = api, from = starter, flags = if (flag) WRITE or READ else READ, clip = if (inClip) listOf(mediaRow) else emptyList(),
                    grants = if (shellGrant == 0) emptySet() else setOf(Triple(mediaRow, SHELL, shellGrant)), persisted = if (persisted) listOf(mediaRow) else emptyList(),
                    providerAnswer = if (says) recipientsRow() else { _, _, _ -> NO }, launchAnswer = { _, _ -> launch },
                )
                val d1 = api >= 35 && says
                val d2 = launch == YES || (flag && inClip && shellGrant == WRITE && !persisted)
                val expected = starter != OTHER && d1 && d2
                val case = "api=$api says=$says launch=$launch flag=$flag inClip=$inClip shellGrant=$shellGrant persisted=$persisted starter=$starter"
                assertEquals(case, expected, write(access).allowed)
                if (!d1 || starter == OTHER || launch == YES) assertNull(case, write(launched(
                    mode = WRITE, api = api, from = starter, flags = if (flag) WRITE or READ else READ, clip = if (inClip) listOf(mediaRow) else emptyList(),
                    grants = if (shellGrant == 0) emptySet() else setOf(Triple(mediaRow, SHELL, shellGrant)), providerAnswer = if (says) recipientsRow() else { _, _, _ -> NO }, launchAnswer = { _, _ -> launch },
                )).launchGrant)
                cases++
                if (expected) accepted++
            }
        assertEquals(3 * 2 * 3 * 2 * 2 * 3 * 2 * 3, cases)
        assertTrue("the table accepts some and refuses most ($accepted of $cases)", accepted in 1 until cases / 4)
    }

    // ------------------------------------------------------------------------------------------- the port's word, alone

    @Test
    fun `launchGrant is null when the launch asked for no grant of that mode, and a word otherwise`() {
        assertNull(grant(launched(flags = 0)))
        assertNull(grant(launched(flags = READ), mode = WRITE))
        assertEquals("held", grant(launched()))
        assertEquals("held", grant(launched(mode = WRITE, flags = WRITE), mode = WRITE))
    }
}
