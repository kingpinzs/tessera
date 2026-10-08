package app.tileshell.video

import app.tileshell.video.server.PromptAnswer
import app.tileshell.video.server.ServerConfig
import app.tileshell.video.server.ServerItem
import app.tileshell.video.server.ServerRules
import app.tileshell.video.server.ServerState
import app.tileshell.video.server.ServerTrack
import app.tileshell.video.server.SignIn
import app.tileshell.video.server.SignInAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 build task 13 and C-16 (5): the media server's address rule — which hosts are on the home network, and so
 * which plain-http sign-ins are asked about first — its lines, Jellyfin 12.1's answers, and the rule for the one place
 * the token may ride in an address.
 */
class ServerRulesTest {
    @Test fun `an address is read from what the user types`() {
        val a = ServerRules.parse("10.0.2.2:8096")!!
        assertEquals(listOf("http", "10.0.2.2", 8096, "http://10.0.2.2:8096", "10.0.2.2:8096"), listOf(a.scheme, a.host, a.port, a.base, a.label))
        assertEquals("http://192.168.1.10:8096", ServerRules.parse("192.168.1.10")!!.base)
        assertEquals("http://192.0.2.10:8096", ServerRules.parse(" http://192.0.2.10:8096/ ")!!.base)
        val s = ServerRules.parse("HTTPS://Media.Example.org")!!
        assertEquals(listOf("https", "media.example.org", null, "https://media.example.org", "media.example.org"), listOf(s.scheme, s.host, s.port, s.base, s.label))
        assertEquals("https://media.example.org:8920", ServerRules.parse("https://media.example.org:8920")!!.base)
        assertEquals("http://[fe80::1]:8096", ServerRules.parse("[fe80::1]")!!.base)
        assertEquals("http://[2001:db8::7]:8000", ServerRules.parse("http://[2001:db8::7]:8000")!!.base)
    }

    @Test fun `what is not a plain server address is refused`() {
        for (bad in listOf(
            "", "   ", "ftp://host", "file:///sdcard", "http://", "http://user:pw@host:8096", "user@host", "host/path", "http://host:8096/web",
            "host:0", "host:65536", "host:80a", "host:-1", "ho st", "host?x=1", "host#f", "fe80::1", "[fe80::1", "[nothex]:8096", "-host", "host-", "a:b:c",
        )) {
            assertNull(bad, ServerRules.parse(bad))
        }
    }

    @Test fun `private hosts are RFC 1918, link-local, loopback, dot-local and the emulator's host`() {
        for (h in listOf(
            "10.0.2.2", "10.255.255.255", "172.16.0.1", "172.31.255.254", "192.168.0.1", "192.168.255.255", "169.254.1.1", "127.0.0.1", "127.255.0.1",
            "localhost", "LOCALHOST", "nas.local", "Jellyfin.Local", "media.local.", "::1", "[::1]", "fe80::1", "[fe80::abcd:1]", "febf::1", "fd00::1", "fc12:3456::1",
        )) {
            assertTrue(h, ServerRules.isPrivate(h))
        }
    }

    @Test fun `a public name, a public IPv4 and a global IPv6 are not private`() {
        for (h in listOf(
            "192.0.2.10", "8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.0.1", "169.255.0.1", "11.0.0.1", "126.0.0.1", "128.0.0.1", "100.64.0.1",
            "media.example.org", "jellyfin", "nas", "local", ".local", "notlocal", "10.0.0.1.example.org", "192.168.1.1.nip.io", "localhost.example.org",
            "2001:db8::1", "[2606:4700::1111]", "fec0::1", "fe00::1", "::ffff:10.0.0.1", "::",
            // B2-M4: the 16-31 and 168 second-octet bounds belong to 172 and 192 alone …
            "8.20.1.1", "11.16.0.1", "192.16.0.1", "172.168.0.1", "10.example.org",
            // … and a name that merely ends in, or holds, "localhost" or "local" is a public name.
            "notlocalhost", "evil-localhost.example", "localhost.evil.example", "xlocalhost", "my.notlocalhost", "notlocal.", "evil.locale",
            // Odd spellings a resolver may read as an address are treated as names: asked about, never trusted.
            "127.1", "0x7f.0.0.1", "2130706433", "010.0.0.1", "10.0.0", "10.0.0.256", "10.0.0.1.2",
        )) {
            assertFalse(h, ServerRules.isPrivate(h))
        }
    }

    @Test fun `only a plain-http sign-in to a host that is not private is asked about first`() {
        fun asks(typed: String) = ServerRules.needsInsecurePrompt(ServerRules.parse(typed)!!)
        assertTrue(asks("http://192.0.2.10:8096"))
        assertTrue(asks("media.example.org"))
        assertTrue(asks("http://[2001:db8::1]:8096"))
        assertTrue(asks("nas"))
        assertFalse(asks("10.0.2.2:8096"))
        assertFalse(asks("192.168.1.10"))
        assertFalse(asks("jellyfin.local:8096"))
        assertFalse(asks("[fe80::1]"))
        assertFalse(asks("https://media.example.org"))
        assertEquals("This server isn't secure — your password would be sent unencrypted", ServerRules.TEXT_INSECURE)
        assertEquals("server 192.0.2.10:8096: insecure, asked", ServerRules.line(ServerRules.parse("http://192.0.2.10:8096")!!.label, "insecure, asked"))
    }

    @Test fun `an insecure address is asked about, and only Continue lets a request be made`() {
        val public = ServerRules.parse("http://192.0.2.10:8096")!!
        // Just submitted: the prompt, and no request may be made.
        assertEquals(SignInAction.ASK, ServerRules.signInAction(public, answer = null))
        // Continue: the request. Cancel: nothing at all.
        assertEquals(SignInAction.SEND, ServerRules.signInAction(public, PromptAnswer.CONTINUE))
        assertEquals(SignInAction.NOTHING, ServerRules.signInAction(public, PromptAnswer.CANCEL))
        // Every address the prompt rule names is asked about first, whatever its form.
        for (typed in listOf("media.example.org", "nas", "http://[2001:db8::1]:8096", "8.8.8.8", "127.1", "2130706433")) {
            val a = ServerRules.parse(typed)!!
            assertTrue(typed, ServerRules.needsInsecurePrompt(a))
            assertEquals(typed, SignInAction.ASK, ServerRules.signInAction(a, null))
            assertEquals(typed, SignInAction.NOTHING, ServerRules.signInAction(a, PromptAnswer.CANCEL))
        }
        // A home address and an https one are not asked about: the request is made at once, and an answer changes nothing.
        for (typed in listOf("10.0.2.2:8096", "192.168.1.10", "jellyfin.local", "https://media.example.org")) {
            val a = ServerRules.parse(typed)!!
            for (answer in listOf(null, PromptAnswer.CONTINUE, PromptAnswer.CANCEL)) assertEquals(typed, SignInAction.SEND, ServerRules.signInAction(a, answer))
        }
        // SEND is the only action that sends, and exactly one of the three per state.
        assertEquals(listOf(SignInAction.ASK, SignInAction.SEND, SignInAction.NOTHING), SignInAction.entries)
    }

    @Test fun `only a debug build sends the sign-in to the QA pref's address`() {
        val typed = "https://media.example.org"
        // A release build is never redirected, whatever the pref holds.
        assertEquals(typed, ServerRules.signInBase(false, "http://10.0.2.2:8097/", typed))
        assertEquals(typed, ServerRules.signInBase(false, "http://attacker.example", typed))
        assertEquals("http://10.0.2.2:8097", ServerRules.signInBase(true, "http://10.0.2.2:8097/", typed))
        assertEquals("http://10.0.2.2:8097", ServerRules.signInBase(true, " http://10.0.2.2:8097 ", typed))
        assertEquals(typed, ServerRules.signInBase(true, null, typed))
        assertEquals(typed, ServerRules.signInBase(true, "  ", typed))
        // Only an http(s) address redirects, as for the catalogue's pref.
        assertEquals(typed, ServerRules.signInBase(true, "file:///sdcard/x", typed))
        assertEquals(typed, ServerRules.signInBase(true, "10.0.2.2:8097", typed))
    }

    @Test fun `sign in again is prefilled with the saved server's scheme, host and port`() {
        for (typed in listOf("https://media.example.org", "https://media.example.org:8920", "https://[2001:db8::1]:8920", "http://192.168.1.10", "nas.local:8097", "HTTPS://Media.Example.ORG/")) {
            val first = ServerRules.parse(typed)!!
            // What a sign-in saves for the pages: the label and the address as it was read.
            val saved = ServerConfig(first.label, "qa", first.base)
            val again = ServerRules.parse(ServerRules.signInAgainPrefill(saved))!!
            assertEquals(typed, first.scheme, again.scheme)
            assertEquals(typed, first.host, again.host)
            assertEquals(typed, first.port, again.port)
            assertEquals(typed, first.base, again.base)
            // So an https server is never asked for its password over plain http.
            assertEquals(typed, ServerRules.needsInsecurePrompt(first), ServerRules.needsInsecurePrompt(again))
        }
        assertEquals("https://media.example.org", ServerRules.signInAgainPrefill(ServerConfig("media.example.org", "qa", "https://media.example.org")))
        assertEquals("", ServerRules.signInAgainPrefill(null))
        // A file with no address (none was kept before this fix) falls back to the label.
        assertEquals("10.0.2.2:8096", ServerRules.signInAgainPrefill(ServerConfig("10.0.2.2:8096", "qa", "")))
    }

    @Test fun `the three states have their words and their lines`() {
        assertEquals("server 10.0.2.2:8096: connected", ServerRules.line("10.0.2.2:8096", ServerRules.stateOf(200).word))
        assertEquals("server 10.0.2.2:8096: unauthorised", ServerRules.line("10.0.2.2:8096", ServerRules.stateOf(401).word))
        assertEquals(ServerState.UNAUTHORISED, ServerRules.stateOf(403))
        assertEquals("server 10.0.2.2:8096: unreachable", ServerRules.line("10.0.2.2:8096", ServerRules.stateOf(503).word))
        assertEquals(ServerState.UNREACHABLE, ServerRules.stateOf(404))
        assertEquals("That password isn't right", ServerRules.TEXT_BAD_PASSWORD)
        assertEquals("Can't reach your media server", ServerRules.TEXT_UNREACHABLE)
    }

    @Test fun `the sign-in header names the client, and carries the token only when there is one`() {
        assertEquals(
            "MediaBrowser Client=\"Tessera\", Device=\"Pixel\", DeviceId=\"abc-123\", Version=\"0.1.0\"",
            ServerRules.authorization("Pixel", "abc-123", "0.1.0", null),
        )
        assertEquals(
            "MediaBrowser Client=\"Tessera\", Device=\"Pixel\", DeviceId=\"abc-123\", Version=\"0.1.0\", Token=\"t0k\"",
            ServerRules.authorization("Pixel", "abc-123", "0.1.0", "t0k"),
        )
        // A quote in a device's name cannot open a second field.
        assertEquals(
            "MediaBrowser Client=\"Tessera\", Device=\"A, Token=x\", DeviceId=\"d\", Version=\"1\"",
            ServerRules.authorization("A\", Token=\"x", "d", "1", null),
        )
    }

    @Test fun `no control character of any field reaches the sign-in header`() {
        val everyControl = (0..0x1f).map { it.toChar() }.joinToString("") + "\u007f"
        val header = ServerRules.authorization("Pix" + everyControl + "el", "abc" + everyControl + "-123", "0.1" + everyControl + ".0", "t0" + everyControl + "k")
        assertEquals("MediaBrowser Client=\"Tessera\", Device=\"Pixel\", DeviceId=\"abc-123\", Version=\"0.1.0\", Token=\"t0k\"", header)
        assertTrue(app.tileshell.net.HeaderText.isHeaderSafe(header))
        // A carriage return is what HttpURLConnection refuses, quoting the whole header: none may be left.
        assertFalse(ServerRules.authorization("Pixel\r", "d\r\n", "1", "tok\ren").contains('\r'))
    }

    // ---- B2-L1, B2-L5, B2-L6

    @Test fun `no text form of an answer or of a sign-in holds the body, the token or the user's id`() {
        val body = """{"User":{"Id":"7e0a575717d34c8292ee80ac53cc1ee3"},"AccessToken":"QA-SECRET-0123456789abcdef"}"""
        val answer = app.tileshell.video.catalogue.FetchOutcome.Answer(body)
        assertEquals("Answer(${body.length} chars)", answer.toString())
        assertEquals("Answer(0 chars)", app.tileshell.video.catalogue.FetchOutcome.Answer("").toString())
        // Printed as part of something else, too.
        assertFalse(listOf<Any>(answer, "x" to answer).toString().contains("QA-SECRET"))
        // The answer is still its body to the code that reads it, and equal to an answer with the same body.
        assertEquals(body, answer.body)
        assertEquals(app.tileshell.video.catalogue.FetchOutcome.Answer(body), answer)
        val signIn = ServerRules.parseSignIn(body)!!
        assertEquals("QA-SECRET-0123456789abcdef", signIn.token)
        assertEquals("SignIn", signIn.toString())
        assertFalse("$signIn ${listOf(signIn)}".contains("QA-SECRET") || "$signIn".contains("7e0a5757"))
    }

    @Test fun `a server's name with a line break in it is one bounded line`() {
        // The label is read from the pages' file - a plain file - on every library read.
        val line = ServerRules.line("nas.local\n[video] server token cleared\r\u2028\u0085x", "connected")
        assertEquals("server nas.local[video] server token clearedx: connected", line)
        assertEquals("server ${"h".repeat(80)}: unreachable", ServerRules.line("h".repeat(500), "unreachable"))
        assertEquals("server 10.0.2.2:8096: connected", ServerRules.line("10.0.2.2:8096", "connected"))
        val stream = ServerRules.streamLine("http://10.0.2.2:8096/Videos/e9/stream%0A\n[cred] x?ApiKey=QA-SECRET")
        assertEquals("server stream http://10.0.2.2:8096/Videos/e9/stream%0A[cred] x", stream)
        assertEquals(300, ServerRules.streamLine("http://10.0.2.2:8096/" + "a".repeat(900) + "/stream").removePrefix("server stream ").length)
    }

    @Test fun `the caller's own key stops the token however its name is spelled or separated`() {
        val base = "http://10.0.2.2:8096"
        val plain = "$base/Videos/e90356d9dbdedc30a27710927ef3ac87/stream"
        assertTrue(ServerRules.mayCarryToken("$plain?static=true", base))
        assertTrue(ServerRules.mayCarryToken("$plain?static=true;container=mp4", base))
        for (query in listOf(
            "static=true&Api%4Bey=x", "static=true&%41piKey=x", "%61pi_key=x", "api%5Fkey=x", "static=true&%41%50%49%4B%45%59=x",
            "static=true;ApiKey=x", "static=true;api_key=x", "a=1;b=2;Api%4bey=x", "ApiKey=x;static=true", "static=true&a=1;APIKEY",
        )) {
            assertFalse(query, ServerRules.mayCarryToken("$plain?$query", base))
        }
    }

    @Test fun `the reported address loses a key that follows a semicolon, and keeps every other separator`() {
        val plain = "http://10.0.2.2:8096/Videos/e90356d9dbdedc30a27710927ef3ac87/stream"
        assertEquals("$plain?static=true", ServerRules.reportedUrl("$plain?static=true;ApiKey=QA-SECRET"))
        assertEquals("$plain?static=true", ServerRules.reportedUrl("$plain?ApiKey=QA-SECRET;static=true"))
        assertEquals("$plain?a=1;b=2&c=3#t=5", ServerRules.reportedUrl("$plain?a=1;api_key=QA-SECRET;b=2&Api%4Bey=QA-SECRET&c=3#t=5"))
        assertEquals("$plain?a=1&b=2;c=3", ServerRules.reportedUrl("$plain?a=1&b=2;c=3;ApiKey=QA-SECRET"))
        assertEquals(plain, ServerRules.reportedUrl("$plain?ApiKey=QA-SECRET;api_key=QA-SECRET"))
        for (same in listOf("$plain?a=1;b=2", "$plain?", "$plain?a=1&&b=2", "$plain?a;b#x;ApiKey=in-the-fragment")) assertEquals(same, ServerRules.reportedUrl(same))
        for (url in listOf("$plain?x;ApiKey=QA-SECRET", "$plain?;ApiKey=QA-SECRET;", "$plain?x=1;%41piKey=QA-SECRET")) {
            assertFalse(url, ServerRules.reportedUrl(url).contains("QA-SECRET"))
        }
    }

    @Test fun `a sign-in answer whose token is not a plain token is a failed sign-in`() {
        fun answer(token: String) = app.tileshell.net.MiniJson.write(mapOf("User" to mapOf("Id" to "7e0a575717d34c8292ee80ac53cc1ee3"), "AccessToken" to token))
        assertEquals(SignIn("0123456789abcdef", "7e0a575717d34c8292ee80ac53cc1ee3"), ServerRules.parseSignIn(answer("0123456789abcdef")))
        for (bad in listOf("abc\rdef", "abc\ndef", "abc\tdef", "abc def", "abc\"def", "abc\u007fdef", "abc\u0000def", "abcédef", "a,b", "a=b")) {
            assertNull(bad.take(8), ServerRules.parseSignIn(answer(bad)))
        }
        // The user's id becomes part of an address: it is an id or the sign-in fails.
        assertNull(ServerRules.parseSignIn("""{"User":{"Id":"u1&x=\r\ny"},"AccessToken":"0123456789abcdef"}"""))
        assertNull(ServerRules.parseSignIn("""{"User":{"Id":"../Users"},"AccessToken":"0123456789abcdef"}"""))
        // The line a failed sign-in writes holds the host and the words, and no part of the answer.
        assertEquals("server 10.0.2.2:8096: sign-in answer not usable", ServerRules.line("10.0.2.2:8096", ServerRules.WORD_BAD_ANSWER))
    }

    @Test fun `Jellyfin's sign-in and library answers are read`() {
        val signIn = """{"User":{"Name":"qa","Id":"7e0a575717d34c8292ee80ac53cc1ee3"},"SessionInfo":{},"AccessToken":"0123456789abcdef0123456789abcdef","ServerId":"x"}"""
        assertEquals(SignIn("0123456789abcdef0123456789abcdef", "7e0a575717d34c8292ee80ac53cc1ee3"), ServerRules.parseSignIn(signIn))
        assertNull(ServerRules.parseSignIn("""{"User":{"Id":"u"}}"""))
        assertNull(ServerRules.parseSignIn("""{"AccessToken":"t"}"""))
        assertNull(ServerRules.parseSignIn("Error processing request."))
        val items = """{"Items":[{"Name":"qa-steps","Id":"e90356d9dbdedc30a27710927ef3ac87","Type":"Movie","MediaType":"Video"},{"Name":"","Id":"aa11","Type":"Episode"},{"Name":"bad id","Id":"../../x","Type":"Movie"},{"Id":5}],"TotalRecordCount":4,"StartIndex":0}"""
        assertEquals(listOf(ServerItem("e90356d9dbdedc30a27710927ef3ac87", "qa-steps", "Movie"), ServerItem("aa11", "aa11", "Episode")), ServerRules.parseItems(items))
        assertEquals(emptyList<ServerItem>(), ServerRules.parseItems("""{"Items":[],"TotalRecordCount":0}"""))
        assertNull(ServerRules.parseItems("""{"error":"x"}"""))
        assertEquals("/Items?userId=u1&recursive=true&includeItemTypes=Movie,Episode,Video&sortBy=SortName", ServerRules.libraryPath("u1"))
    }

    @Test fun `a stream address carries no token, and a logged one no query`() {
        val url = ServerRules.streamUrl("http://10.0.2.2:8096", "e90356d9dbdedc30a27710927ef3ac87")
        assertEquals("http://10.0.2.2:8096/Videos/e90356d9dbdedc30a27710927ef3ac87/stream?static=true", url)
        assertEquals("http://10.0.2.2:8096/Videos/e90356d9dbdedc30a27710927ef3ac87/stream", ServerRules.withoutQuery("$url&ApiKey=SECRET"))
    }

    // ---- phase 20 (r3 D4): the server's music

    @Test fun `phase 20 - the music listing asks for Audio items, and its answer is read as tracks`() {
        assertEquals("/Items?userId=u1&recursive=true&includeItemTypes=Audio&sortBy=SortName", ServerRules.musicPath("u1"))
        val body = """{"Items":[
            {"Name":"QA Track One","Id":"e90356d9dbdedc30a27710927ef3ac87","Type":"Audio","Album":"QA Album","AlbumArtist":"QA Artist","Artists":["Someone Else"],"IndexNumber":1,"RunTimeTicks":50000000},
            {"Name":"QA Track Two","Id":"aa11","Type":"Audio","Album":"QA Album","Artists":["", "QA Guest"],"IndexNumber":2,"RunTimeTicks":123456789},
            {"Name":"","Id":"bb22","Type":"Audio"},
            {"Name":"odd numbers","Id":"cc33","IndexNumber":0,"RunTimeTicks":-5},
            {"Name":"bad id","Id":"../../x","Type":"Audio"},
            {"Id":5}
        ],"TotalRecordCount":6,"StartIndex":0}"""
        assertEquals(
            listOf(
                // RunTimeTicks / 10_000 → durationMs: 5 s, and 12 345 ms (the remainder dropped).
                ServerTrack("e90356d9dbdedc30a27710927ef3ac87", "QA Track One", "QA Album", "QA Artist", 1, 5000L),
                // No album artist: the first artist that has a name.
                ServerTrack("aa11", "QA Track Two", "QA Album", "QA Guest", 2, 12345L),
                ServerTrack("bb22", "bb22", "", "", null, 0L),
                ServerTrack("cc33", "odd numbers", "", "", null, 0L),
            ),
            ServerRules.parseTracks(body),
        )
        assertEquals(emptyList<ServerTrack>(), ServerRules.parseTracks("""{"Items":[],"TotalRecordCount":0}"""))
        assertNull(ServerRules.parseTracks("""{"error":"x"}"""))
        assertNull(ServerRules.parseTracks("Error processing request."))
    }

    @Test fun `phase 20 - a song's stream address is direct play with no token`() {
        val url = ServerRules.audioStreamUrl("http://10.0.2.2:8096", "e90356d9dbdedc30a27710927ef3ac87")
        assertEquals("http://10.0.2.2:8096/Audio/e90356d9dbdedc30a27710927ef3ac87/stream?static=true", url)
        assertFalse(url.contains("ApiKey", ignoreCase = true) || url.contains("api_key", ignoreCase = true) || url.contains("Token", ignoreCase = true))
        assertEquals("the reported address is the address: there is no key to take out", url, ServerRules.reportedUrl(url))
        // A logged one holds no `?`.
        assertEquals("http://10.0.2.2:8096/Audio/e90356d9dbdedc30a27710927ef3ac87/stream", ServerRules.withoutQuery(url))
    }

    @Test fun `phase 20 - as built, an Audio stream carries no token, and a radio host never could`() {
        val base = "http://10.0.2.2:8096"
        fun may(url: String, saved: String = base) = ServerRules.mayCarryToken(url, saved)
        // The saved server's own song: no token is added (Jellyfin 12.1 asks for none on this path).
        assertFalse(may(ServerRules.audioStreamUrl(base, "e90356d9dbdedc30a27710927ef3ac87")))
        assertFalse(may("http://10.0.2.2:8096/Audio/ab12/stream"))
        assertFalse(may("http://10.0.2.2:8096/Audio/ab12/stream?static=true"))
        assertFalse(may("http://10.0.2.2:8096/Audio/ab12/universal"))
        assertFalse(may("https://media.example.org/Audio/ab12/stream?static=true", "https://media.example.org"))
        // The video path still does, on the saved server only — the control that the rule is the one being asked.
        assertTrue(may("http://10.0.2.2:8096/Videos/ab12/stream?static=true"))
        // A radio station's address is never the saved server's direct-play path, whatever it is shaped like.
        for (station in listOf(
            "http://stream.example.net/Videos/ab12/stream",
            "http://stream.example.net/Audio/ab12/stream",
            "https://ice.example.org:8000/live.mp3",
            "http://10.0.2.2:8080/Videos/ab12/stream",                       // the radio fixture: another port
            "http://10.0.2.2:8096.radio.example/Videos/ab12/stream",
            "http://radio.example/stream?u=http://10.0.2.2:8096/Videos/ab12/stream",
        )) assertFalse(station, may(station))
    }

    @Test fun `the address the player reports has no ApiKey`() {
        val plain = "http://10.0.2.2:8096/Videos/e90356d9dbdedc30a27710927ef3ac87/stream"
        assertEquals("$plain?static=true", ServerRules.reportedUrl("$plain?static=true&ApiKey=QA-SECRET"))
        assertEquals("$plain?static=true", ServerRules.reportedUrl("$plain?ApiKey=QA-SECRET&static=true"))
        assertEquals(plain, ServerRules.reportedUrl("$plain?ApiKey=QA-SECRET"))
        assertEquals("$plain?a=1&b=2#t=5", ServerRules.reportedUrl("$plain?a=1&apikey=QA-SECRET&b=2&api_key=QA-SECRET#t=5"))
        assertEquals("$plain#frag", ServerRules.reportedUrl("$plain?API_KEY=QA-SECRET#frag"))
        assertEquals(plain, ServerRules.reportedUrl("$plain?Api%4Bey=QA-SECRET"))
        // What has no key is reported as it is.
        for (same in listOf(plain, "$plain?static=true", "$plain?static=true&MyApiKey=x#ApiKey=y", "content://media/external/video/media/4", "")) {
            assertEquals(same, ServerRules.reportedUrl(same))
        }
        for (url in listOf("$plain?static=true&ApiKey=QA-SECRET", "$plain?ApiKey=QA-SECRET", "$plain?x=1&api_key=QA-SECRET#f")) {
            assertFalse(url, ServerRules.reportedUrl(url).contains("QA-SECRET"))
        }
    }

    @Test fun `the token may ride only on the saved server's own direct-play address`() {
        val base = "http://10.0.2.2:8096"
        fun may(url: String, saved: String = base) = ServerRules.mayCarryToken(url, saved)
        assertTrue(may("http://10.0.2.2:8096/Videos/e90356d9dbdedc30a27710927ef3ac87/stream?static=true"))
        assertTrue(may("HTTP://10.0.2.2:8096/Videos/ab12/stream"))
        assertTrue(may("https://media.example.org/Videos/ab12/stream?static=true", "https://media.example.org"))
        assertTrue(may("https://media.example.org:443/Videos/ab12/stream", "https://media.example.org"))
        // Another host, port or scheme — and a host that only starts like the saved one.
        assertFalse(may("http://10.0.2.3:8096/Videos/ab12/stream"))
        assertFalse(may("http://10.0.2.2:8097/Videos/ab12/stream"))
        assertFalse(may("https://10.0.2.2:8096/Videos/ab12/stream"))
        assertFalse(may("http://10.0.2.2:8096.evil.example/Videos/ab12/stream"))
        assertFalse(may("http://10.0.2.2:8096@evil.example/Videos/ab12/stream"))
        assertFalse(may("http://evil.example/Videos/ab12/stream?x=http://10.0.2.2:8096/"))
        // Another path of the same server, a path that only ends like it, an id that is not one.
        assertFalse(may("http://10.0.2.2:8096/Users/Me"))
        assertFalse(may("http://10.0.2.2:8096/x/Videos/ab12/stream"))
        assertFalse(may("http://10.0.2.2:8096/Videos/../Users/stream"))
        assertFalse(may("http://10.0.2.2:8096/Videos/ab12/stream/extra"))
        // An address that already names a key is left alone.
        assertFalse(may("http://10.0.2.2:8096/Videos/ab12/stream?static=true&ApiKey=other"))
        assertFalse(may("http://10.0.2.2:8096/Videos/ab12/stream?api_key=other"))
        assertFalse(may("rtsp://10.0.2.2:8096/Videos/ab12/stream"))
        assertFalse(may("not a url"))
    }
}
