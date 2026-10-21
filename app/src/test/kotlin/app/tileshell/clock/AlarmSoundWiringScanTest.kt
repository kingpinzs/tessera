package app.tileshell.clock

import app.tileshell.media.SourceScan
import app.tileshell.media.SourceScan.body
import app.tileshell.media.SourceScan.count
import app.tileshell.media.SourceScan.mutate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ledger L18-2 (the adversarial GATE review's H2), read from the SOURCES in the form of `media/UriAccessWiringScanTest`:
 * the rules are pure and unit-tested (`AlarmApiRulesTest`, `AlarmRingtoneRulesTest`); what no unit test runs is the code
 * that hands them the real request and does what they say. Held here:
 *  - the API handler hands `AlarmApiRules.parse` the caller's ringtone and the REAL platform port of the activity the
 *    caller started, and stores the sound the rule answered — never the extra itself;
 *  - the editor's hand-off bundle (ClockActivity is exported) has its sound weighed again by the same rule, the caller's
 *    question answered by the handler that asked it — and only there, after the bundle is known to be the shell's
 *    (ledger L18-3: one rule for both of the API's paths);
 *  - the ring asks `AlarmRingtoneRules.sinkRefusal` before it opens or plays a stored URI, and a refusal is the default
 *    sound; a stored URI is opened in that one function and nowhere else in the clock (the Sounds page previews the
 *    rows of its own list).
 * Each check has its twin: the same source with the site changed must be caught.
 */
class AlarmSoundWiringScanTest {
    private val sinkForm = "{ val id = ring.logId fun default(): Uri = Uri.fromFile(AlarmSounds.file(this, AlarmSounds.default)) return when (sound.kind) { " +
        "AlarmSound.Kind.VIBRATE -> null.also { Diagnostics.add(\"alarms\", \"ring \$id sound=vibrate\") } " +
        "AlarmSound.Kind.DEFAULT -> default().also { Diagnostics.add(\"alarms\", \"ring \$id sound=default\") } " +
        "AlarmSound.Kind.TONE, AlarmSound.Kind.MUSIC -> { " +
        "val uri = sound.uri ?: return default().also { Diagnostics.add(\"alarms\", \"ring \$id sound=default\") } " +
        "AlarmSounds.byUri(uri)?.let { brand -> return Uri.fromFile(AlarmSounds.file(this, brand)).also { Diagnostics.add(\"alarms\", \"ring \$id sound=\$uri\") } } " +
        "AlarmRingtoneRules.sinkRefusal(uri)?.let { why -> Diagnostics.add(\"alarms\", \"ring \$id sound refused (\$why) -> default\") return default() } " +
        "if (sound.kind == AlarmSound.Kind.MUSIC && !getSystemService(UserManager::class.java).isUserUnlocked) { Diagnostics.add(\"alarms\", \"sound \$uri locked -> default\") return default() } " +
        "val readable = runCatching { contentResolver.openFileDescriptor(Uri.parse(uri), \"r\")?.use { true } ?: false }.getOrDefault(false) " +
        "if (readable) { Diagnostics.add(\"alarms\", \"ring \$id sound=\$uri\") Uri.parse(uri) } else { Diagnostics.add(\"alarms\", \"sound \$uri missing -> default\") default() } } } }"

    private fun problems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val api = sources["clock/AlarmApiActivity.kt"].orEmpty()
        val rules = sources["clock/AlarmApiRules.kt"].orEmpty()
        // Ledger L18-3: the editor's hand-off bundle is read and weighed in ClockIntents (pure), not in the activity.
        val clock = sources["clock/ClockIntents.kt"].orEmpty()
        val clockActivity = sources["clock/ClockActivity.kt"].orEmpty()
        val ring = sources["clock/RingService.kt"].orEmpty()
        // The API handler: the extra goes to the rule and nowhere else, beside the real port; the rule's sound is stored.
        if (count(api, "EXTRA_RINGTONE") != 1 || !api.contains("ringtone = extras.string(AlarmClock.EXTRA_RINGTONE, AlarmApiRules.MAX_EXTRA_TEXT),") ||
            count(api, "AlarmApiRules.parse(") != 1 || !api.contains("access = AndroidUriAccess(this), )") || count(api, "access") != 1
        ) problems += "the handler does not hand AlarmApiRules.parse the caller's ringtone and the real port, once"
        val parsers = sources.mapValues { (_, text) -> count(text, "AlarmApiRules.parse(") }.filterValues { it > 0 }
        if (parsers != mapOf("clock/AlarmApiActivity.kt" to 1)) problems += "the API's request is parsed somewhere else: $parsers"
        if (count(api, "AlarmSound") != 0 || Regex("\\.sound\\b").findAll(api).count() != 3 ||
            !api.contains("val alarm = store.newAlarm(f.hour!!, f.minute, f.message, f.days, f.sound, Alarm.DEFAULT_SNOOZE)") ||
            !api.contains("putString(ClockActivity.API_SOUND_KIND, f.sound.kind.name) f.sound.uri?.let { putString(ClockActivity.API_SOUND_URI, it) }")
        ) problems += "the handler stores or hands on a sound other than the rule's"
        // Ledger L18-3 (N12): the handler's ringtone line is the rule's, written as it is; no other text reaches it.
        if (!api.contains("is Request.CreateAlarm -> request.fields.soundLine is Request.EditAlarm -> request.fields.soundLine else -> null } soundLine?.let { Diagnostics.add(\"alarms\", it) }") ||
            count(api, "soundLine") != 4 || count(api, "ringtone") != 1
        ) problems += "the handler does not write the rule's ringtone line"
        // The rule's side: the ringtone becomes a sound only through AlarmRingtoneRules.fromApi, asked with that port.
        if (Regex("\\bringtone\\b").findAll(rules).count() != 2 || Regex("\\baccess\\b").findAll(rules).count() != 2 || count(rules, "AlarmSound(") != 0 ||
            !rules.contains("val tone = AlarmRingtoneRules.fromApi(ringtone, AlarmRingtoneRules.Caller.Ask(access)) val fields = AlarmFields(hour, minute, daySet, text, tone.sound, tone.refused, tone.line)")
        ) problems += "AlarmApiRules does not make the ringtone a sound through AlarmRingtoneRules.fromApi(ringtone, Caller.Ask(access))"
        val askers = sources.mapValues { (_, text) -> count(text, "AlarmRingtoneRules.fromApi(") }.filterValues { it > 0 }
        if (askers != mapOf("clock/AlarmApiRules.kt" to 1, "clock/ClockIntents.kt" to 1)) problems += "a ringtone is weighed somewhere else: $askers"
        // The editor's hand-off: exported, so the bundle's sound is weighed again by the same rule — the caller's question
        // answered by the handler, in ClockIntents.edit alone (reached only for the shell's own bundle, extrasProblems).
        val handlerAsked = sources.filterKeys { it != "clock/AlarmRingtoneRules.kt" }.mapValues { (_, text) -> count(text, "AskedByHandler") }.filterValues { it > 0 }
        if (handlerAsked != mapOf("clock/ClockIntents.kt" to 1)) problems += "the handler's answer stands in for the caller somewhere else: $handlerAsked"
        if (count(clock, "API_SOUND_URI") != 2 || !clock.contains("AlarmSound.Kind.TONE.name -> AlarmRingtoneRules.fromApi(soundUri, AlarmRingtoneRules.Caller.AskedByHandler).sound") || !clock.contains("val soundUri = api.string(API_SOUND_URI, MAX_URI)") ||
            count(clock, "AlarmSound(") != 1 || count(clockActivity, "AlarmSound(") != 0 || !clock.contains("AlarmSound.Kind.VIBRATE.name -> AlarmSound(AlarmSound.Kind.VIBRATE)")
        ) problems += "the editor takes the hand-off bundle's sound without weighing it"
        // The sink: one function opens a stored URI, and it asks the rule first.
        val source = body(ring, "private fun source(ring: Ring, sound: AlarmSound): Uri?")
        if (source != sinkForm) problems += "RingService.source is not its one form:\n  is:      $source\n  must be: $sinkForm"
        if (count(ring, "sinkRefusal(") != 1 || count(ring, "Uri.parse(") != 2 || count(ring, "openFileDescriptor(") != 1 || count(ring, "setDataSource(") != 1 ||
            Regex("\\.uri\\b").findAll(ring).count() != 1 || !ring.contains("val source = source(ring, sound) if (source != null) {") || !ring.contains("setDataSource(this@RingService, source)")
        ) problems += "the ring opens or plays a stored URI outside source()"
        val openers = sources.filterKeys { it.startsWith("clock/") }.mapValues { (_, text) -> count(text, "openFileDescriptor(") + count(text, "setDataSource(") + count(text, "openInputStream(") + count(text, "openAssetFileDescriptor(") }.filterValues { it > 0 }
        // The Sounds page previews a row of its OWN list (the shell's sounds and RingtoneManager's), never a stored URI.
        val tab = sources["clock/AlarmTab.kt"].orEmpty()
        if (openers != mapOf("clock/AlarmTab.kt" to 1, "clock/RingService.kt" to 2) || !body(tab, "fun preview(row: SoundRow)").contains("player.setDataSource(context, uri)")) {
            problems += "a clock file other than the ring and the Sounds page's preview of its own rows opens a sound: $openers"
        }
        return problems
    }

    @Test fun `L18-2 a ringtone from the API is the rule's sound, and the ring asks before it opens a stored URI`() {
        val sources = SourceScan.all()
        assertTrue("the scan read the sources (${sources.size} files)", sources.size > 200)
        assertEquals(emptyList<String>(), problems(sources))
    }

    @Test fun `L18-2 a handler that keeps the extra, a port that is not the launch's, an editor that trusts its bundle or a ring that opens first is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = problems(sources + (file to mutate(sources.getValue(file), old, new)))
        // The defect itself (H2): any string kept as a TONE — in the rule, or by the handler round the rule.
        assertTrue(with("clock/AlarmApiRules.kt", "val tone = AlarmRingtoneRules.fromApi(ringtone, AlarmRingtoneRules.Caller.Ask(access))", "val tone = AlarmRingtoneRules.FromApi(if (ringtone == null) AlarmSound.DEFAULT else AlarmSound(AlarmSound.Kind.TONE, ringtone, null))").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "f.days, f.sound, Alarm.DEFAULT_SNOOZE)", "f.days, AlarmSound(AlarmSound.Kind.TONE, intent.getStringExtra(AlarmClock.EXTRA_RINGTONE), null), Alarm.DEFAULT_SNOOZE)").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "f.sound.uri?.let { putString(ClockActivity.API_SOUND_URI, it) }", "putString(ClockActivity.API_SOUND_URI, intent.getStringExtra(AlarmClock.EXTRA_RINGTONE))").isNotEmpty())
        // The rule's answer ignored, or asked with no port / about nothing.
        assertTrue(with("clock/AlarmApiRules.kt", "AlarmFields(hour, minute, daySet, text, tone.sound, tone.refused, tone.line)", "AlarmFields(hour, minute, daySet, text, ringtone?.let { AlarmSound(AlarmSound.Kind.TONE, it, null) } ?: tone.sound, tone.refused, tone.line)").isNotEmpty())
        assertTrue(with("clock/AlarmApiRules.kt", "AlarmRingtoneRules.Caller.Ask(access))", "AlarmRingtoneRules.Caller.Ask(null))").isNotEmpty())
        // L18-3: the handler taking the editor's stand-in for the caller's answer — a yes nobody asked for.
        assertTrue(with("clock/AlarmApiRules.kt", "AlarmRingtoneRules.Caller.Ask(access))", "AlarmRingtoneRules.Caller.AskedByHandler)").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "access = AndroidUriAccess(this),", "access = null,").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "ringtone = extras.string(AlarmClock.EXTRA_RINGTONE, AlarmApiRules.MAX_EXTRA_TEXT),", "ringtone = extras.string(AlarmClock.EXTRA_RINGTONE, AlarmApiRules.MAX_EXTRA_TEXT) ?: intent.dataString,").isNotEmpty())
        // L18-3 (N12): the rule's ringtone line dropped, or the caller's own text written beside it.
        assertTrue(with("clock/AlarmApiActivity.kt", "soundLine?.let { Diagnostics.add(\"alarms\", it) }", "").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "soundLine?.let { Diagnostics.add(\"alarms\", it) }", "soundLine?.let { Diagnostics.add(\"alarms\", \"\$it \${intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)}\") }").isNotEmpty())
        // The editor trusting its bundle (the old line), or a second parser of the API.
        assertTrue(with("clock/ClockIntents.kt", "AlarmRingtoneRules.fromApi(soundUri, AlarmRingtoneRules.Caller.AskedByHandler).sound", "AlarmSound(AlarmSound.Kind.TONE, soundUri, null)").isNotEmpty())
        // L18-3: the stand-in used where the bundle is not yet known to be the shell's (before the caller check).
        assertTrue(with("clock/ClockIntents.kt", "val sent = try {", "AlarmRingtoneRules.fromApi(\"x\", AlarmRingtoneRules.Caller.AskedByHandler) val sent = try {").isNotEmpty())
        assertTrue(with("clock/ClockActivity.kt", "open.alarm?.let { nav.openAlarmEditor(it) }", "open.alarm?.let { nav.openAlarmEditor(it.copy(sound = AlarmSound(AlarmSound.Kind.TONE, intent?.dataString, null))) }").isNotEmpty())
        assertTrue(problems(sources + ("clock/ClockText.kt" to sources.getValue("clock/ClockText.kt") + " fun x(a: app.tileshell.media.UriAccessPort) = AlarmApiRules.parse(null, null, null, null, null, true, null, \"x\", null, a)")).isNotEmpty())
        // The sink: the check dropped, turned round, made after the open, or its answer ignored.
        val check = "AlarmRingtoneRules.sinkRefusal(uri)?.let { why -> Diagnostics.add(\"alarms\", \"ring \$id sound refused (\$why) -> default\") return default() } "
        fun ring(old: String, new: String) = with("clock/RingService.kt", old, new)
        assertTrue(ring(check, "").isNotEmpty())
        assertTrue(ring("AlarmRingtoneRules.sinkRefusal(uri)?.let { why ->", "AlarmRingtoneRules.sinkRefusal(uri).let { why -> if (why != null) return Uri.parse(uri)").isNotEmpty())
        assertTrue(ring("(\$why) -> default\") return default() }", "(\$why) -> default\") }").isNotEmpty())
        assertTrue(ring("(\$why) -> default\") return default() }", "(\$why) -> default\") return Uri.parse(uri) }").isNotEmpty())
        assertTrue(ring("AlarmRingtoneRules.sinkRefusal(uri)?.let { why ->", "if (sound.kind == AlarmSound.Kind.MUSIC) return Uri.parse(uri) AlarmRingtoneRules.sinkRefusal(uri)?.let { why ->").isNotEmpty())
        assertTrue(ring("AlarmRingtoneRules.sinkRefusal(uri)?.let { why ->", "AlarmRingtoneRules.sinkRefusal(\"content://media/x\")?.let { why ->").isNotEmpty())
        // A second place that plays a stored URI: the player handed the alarm's own string, or another clock file opening one.
        assertTrue(ring("setDataSource(this@RingService, source)", "setDataSource(this@RingService, Uri.parse(sound.uri))").isNotEmpty())
        assertTrue(problems(sources + ("clock/AlarmTab.kt" to sources.getValue("clock/AlarmTab.kt") + " fun x(c: android.content.Context, u: android.net.Uri) = c.contentResolver.openFileDescriptor(u, \"r\")")).isNotEmpty())
    }

    // ------------------------------------------------------------------------------- a sound that will not play (L18-3, N11)

    /** The one place the ring makes a player: started, or released and thrown; an error while playing goes to the rule. */
    private val openForm = "{ val p = MediaPlayer() try { p.setAudioAttributes(attrs) p.setDataSource(this@RingService, source) p.isLooping = true p.prepare() " +
        "p.setOnErrorListener { failed, what, extra -> if (player === failed) { player = null failed.release() " +
        "take(RingSoundRules.fallback(id, source, \"error \$what \$extra\", { defaultSound() }) { open(id, attrs, it) }) applyCallLevel() } true } " +
        "p.start() return p } catch (e: Throwable) { p.release() throw e } }"

    /**
     * Ledger L18-3 (the adversarial review's N11): `RingSoundRules` is the rule (`RingSoundRulesTest`); held here is that
     * the ring hands it the chosen source, the DEFAULT sound as the fallback and the one function that starts a player —
     * at the start and on an error while playing — and that what plays is the rule's answer, its lines written.
     */
    private fun ringSoundProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val ring = sources["clock/RingService.kt"].orEmpty()
        val start = body(ring, "private fun startSound(ring: Ring, store: ClockStore)")
        if (!start.contains("val source = source(ring, sound) if (source != null) { val audio = getSystemService(AudioManager::class.java) " +
                "focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build().also { audio.requestAudioFocus(it) } " +
                "take(RingSoundRules.start(ring.logId, source, { defaultSound() }) { open(ring.logId, attrs, it) }) applyCallLevel()")
        ) problems += "the ring does not start its sound through RingSoundRules.start with the default sound as the fallback"
        val open = body(ring, "private fun open(id: String, attrs: AudioAttributes, source: Uri): MediaPlayer")
        if (open != openForm) problems += "RingService.open is not its one form:\n  is:      $open\n  must be: $openForm"
        if (body(ring, "private fun take(started: RingSoundRules.Started<MediaPlayer>)") != "{ started.lines.forEach { Diagnostics.add(\"alarms\", it) } player = started.player }") {
            problems += "what plays is not the rule's answer, or its lines are not written"
        }
        if (!ring.contains("private fun defaultSound(): Uri = Uri.fromFile(AlarmSounds.file(this, AlarmSounds.default))")) problems += "the fallback is not the default alarm sound"
        if (count(ring, "MediaPlayer()") != 1 || count(ring, "RingSoundRules.start(") != 1 || count(ring, "RingSoundRules.fallback(") != 1 || count(ring, "take(") != 3 ||
            Regex("\\bplayer = ").findAll(ring).count() != 3 || count(ring, "prepare()") != 1
        ) problems += "a player is made, or the ring's player set, outside open() and the rule's answer"
        return problems
    }

    @Test fun `L18-3 N11 the ring's sound is the rule's answer - the default when the chosen will not play, at the start or while playing`() {
        assertEquals(emptyList<String>(), ringSoundProblems(SourceScan.all()))
    }

    @Test fun `L18-3 N11 a ring that logs and goes silent, falls back to the chosen sound, drops the error path or keeps a failed player is caught`() {
        val sources = SourceScan.all()
        fun with(old: String, new: String) = ringSoundProblems(sources + ("clock/RingService.kt" to mutate(sources.getValue("clock/RingService.kt"), old, new)))
        val started = "take(RingSoundRules.start(ring.logId, source, { defaultSound() }) { open(ring.logId, attrs, it) })"
        // The defect itself: the failure only logged, the alarm vibrating silently.
        assertTrue(with(started, "player = runCatching { open(ring.logId, attrs, source) }.onFailure { Diagnostics.add(\"alarms\", \"ring \${ring.logId} could not play \$source: \$it\") }.getOrNull()").isNotEmpty())
        // The fallback that is not the default sound, the rule's answer ignored, or a second player made round the rule.
        assertTrue(with("source, { defaultSound() }) { open(ring.logId", "source, { source }) { open(ring.logId").isNotEmpty())
        assertTrue(with("private fun defaultSound(): Uri = Uri.fromFile(AlarmSounds.file(this, AlarmSounds.default))", "private fun defaultSound(): Uri = Uri.fromFile(AlarmSounds.file(this, AlarmSounds.all.last()))").isNotEmpty())
        assertTrue(with("player = started.player", "player = started.player ?: MediaPlayer()").isNotEmpty())
        assertTrue(with("started.lines.forEach { Diagnostics.add(\"alarms\", it) }", "").isNotEmpty())
        // An error while playing: not listened for, or its fallback dropped.
        assertTrue(with("p.setOnErrorListener { failed, what, extra ->", "p.setOnCompletionListener { failed -> val what = 0; val extra = 0").isNotEmpty())
        assertTrue(with("take(RingSoundRules.fallback(id, source,", "(RingSoundRules.fallback(id, source,").isNotEmpty())
        // A player that would not start kept, not released.
        assertTrue(with("catch (e: Throwable) { p.release() throw e }", "catch (e: Throwable) { throw e }").isNotEmpty())
    }

    // ------------------------------------------------------------------------------- the exported activities' extras (L18-3)

    /**
     * Ledger L18-3 (the adversarial review's N1 and N4): ClockActivity and AlarmApiActivity are exported, in the
     * launcher's process. `SafeExtras` and `ClockIntents` are the rules (`ClockIntentsTest`); held here is that the two
     * activities read NO extra themselves, that ClockActivity routes every intent through `ClockIntents.open` with the
     * uid that sent THAT intent (the port for the launch, the platform's own caller for a new intent on API 35+, nobody
     * below it), and that the handler refuses a request one of whose extras could not be read.
     */
    private fun extrasProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val api = sources["clock/AlarmApiActivity.kt"].orEmpty()
        val clock = sources["clock/ClockActivity.kt"].orEmpty()
        val rawRead = Regex("get\\w*Extra\\(|hasExtra\\(|\\.extras\\b|getParcelable|getSerializable|\\.getString\\(|\\.getInt\\(|\\.getIntArray\\(|\\.getLong\\(|\\.getBundle\\(|containsKey")
        for ((name, text) in listOf("AlarmApiActivity" to api.substringBefore("private fun openClock("), "ClockActivity" to clock)) {
            rawRead.findAll(text).forEach { problems += "$name reads an extra itself (${it.value}): only SafeExtras reads another app's extras" }
        }
        if (count(clock, "route(") != 4 || !clock.contains("route(intent, ClockIntents.launchCaller(AndroidUriAccess(this)))") ||
            !clock.contains("if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) keepIf(route(intent, ClockIntents.NO_CALLER), intent)") ||
            !clock.contains("super.onNewIntent(intent, caller) keepIf(route(intent, try { caller.uid } catch (e: Throwable) { ClockIntents.NO_CALLER }), intent)") ||
            !clock.contains("private fun route(intent: Intent?, callerUid: Int): ClockOpen { val open = ClockIntents.open(intent?.let { AndroidExtras(it) }, callerUid, Process.myUid()) ") ||
            count(clock, "Process.myUid()") != 1 || count(clock, "openAlarmEditor(") != 2 || count(clock, "openTimerEditor(") != 2
        ) problems += "ClockActivity does not route every intent through ClockIntents.open with that intent's own caller"
        val openers = sources.mapValues { (_, text) -> count(text, "ClockIntents.open(") }.filterValues { it > 0 }
        if (openers != mapOf("clock/ClockActivity.kt" to 1)) problems += "an intent is weighed for Clock somewhere else: $openers"
        if (!sources["clock/ClockIntents.kt"].orEmpty().contains("val fromShell = myUid >= 0 && callerUid == myUid if (sent) { if (!fromShell) { api = \"ignored: \$WHY_NOT_SHELL\" } else { when (val edit = edit(safe.nested(EXTRA_API_EDIT))) {")) {
            problems += "the edit bundle is read before, or without, the caller check"
        }
        if (!api.contains("val extras = SafeExtras(AndroidExtras(intent)) val parsed = AlarmApiRules.parse(") || count(api, "SafeExtras(") != 1 ||
            !api.contains("val request = AlarmApiRules.unlessUnreadable(parsed, extras.refused.size)") || Regex("\\bparsed\\b").findAll(api).count() != 2 ||
            !api.contains("Diagnostics.add(\"alarms\", AlarmApiRules.line(intent.action, caller, result))")
        ) problems += "the handler acts on a request whose extras it could not all read, or reads them outside SafeExtras"
        return problems
    }

    /**
     * Ledger L18-3, the third pass's P3-4: a relaunch routes the activity's intent again against the app that FIRST
     * launched it. `ClockIntents.open`'s [ClockOpen.keep] is the rule (`ClockIntentsTest`); held here is that a new
     * intent becomes the activity's own only through it — at the one `setIntent`, from both onNewIntent paths — and that
     * the launch path never consults it.
     */
    private fun relaunchProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val clock = sources["clock/ClockActivity.kt"].orEmpty()
        if (!clock.contains("private fun keepIf(open: ClockOpen, intent: Intent) { if (open.keep) setIntent(intent) else Diagnostics.add(\"clock\", ClockIntents.NOT_KEPT) }") ||
            count(clock, "setIntent(") != 1 || Regex("(?<![\\w.])intent\\s*=(?!=)|\\.intent\\s*=(?!=)").containsMatchIn(clock)
        ) problems += "a new intent becomes the activity's own other than through ClockOpen.keep"
        if (count(clock, "keepIf(") != 3 ||
            !clock.contains("if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) keepIf(route(intent, ClockIntents.NO_CALLER), intent)") ||
            !clock.contains("super.onNewIntent(intent, caller) keepIf(route(intent, try { caller.uid } catch (e: Throwable) { ClockIntents.NO_CALLER }), intent)") ||
            !clock.contains("private fun route(intent: Intent?, callerUid: Int): ClockOpen { val open = ClockIntents.open(intent?.let { AndroidExtras(it) }, callerUid, Process.myUid()) ") ||
            !body(clock, "private fun route(intent: Intent?, callerUid: Int): ClockOpen").endsWith(" return open }")
        ) problems += "a new intent is kept other than by its own answer, or the launch path consults it"
        return problems
    }

    @Test fun `L18-3 P3-4 a new intent becomes the activity's own only when its own answer keeps it`() {
        assertEquals(emptyList<String>(), relaunchProblems(SourceScan.all()))
    }

    @Test fun `L18-3 P3-4 an activity that keeps every new intent, or sets its intent another way, is caught`() {
        val sources = SourceScan.all()
        fun with(old: String, new: String) = relaunchProblems(sources + ("clock/ClockActivity.kt" to mutate(sources.getValue("clock/ClockActivity.kt"), old, new)))
        // The defect itself: every new intent kept, whoever sent its bundle.
        assertTrue(with("if (open.keep) setIntent(intent) else Diagnostics.add(\"clock\", ClockIntents.NOT_KEPT)", "setIntent(intent)").isNotEmpty())
        assertTrue(with("override fun onNewIntent(intent: Intent) { super.onNewIntent(intent)", "override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) setIntent(intent)").isNotEmpty())
        assertTrue(with("super.onNewIntent(intent, caller) keepIf(", "super.onNewIntent(intent, caller) this.intent = intent keepIf(").isNotEmpty())
        // The answer of another intent, or the new intent's answer dropped on one path.
        assertTrue(with("keepIf(route(intent, ClockIntents.NO_CALLER), intent)", "keepIf(ClockOpen(), intent)").isNotEmpty())
        assertTrue(with("keepIf(route(intent, try { caller.uid } catch (e: Throwable) { ClockIntents.NO_CALLER }), intent)", "route(intent, try { caller.uid } catch (e: Throwable) { ClockIntents.NO_CALLER })").isNotEmpty())
    }

    /** The handler's one start of Clock: a new task, cleared when the rule says the hand-off must be a launch (L18-6). */
    private val openClockForm = "{ startActivity( Intent(this, ClockActivity::class.java) .putExtra(ClockActivity.EXTRA_PAGE, tab.id) " +
        ".apply { edit?.let { putExtra(ClockActivity.EXTRA_API_EDIT, it) } } .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) " +
        ".apply { if (ClockIntents.handOffFresh(Build.VERSION.SDK_INT, edit != null)) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }, ) }"

    /**
     * Ledger L18-6 (the third pass's P3-7): on API 34 the platform names no new intent's sender, so ClockActivity routes a
     * new intent with nobody as its sender (held by extrasProblems) and the shell's own hand-off to a running Clock opened
     * no editor. `ClockIntents.handOffFresh` is the rule (`ClockIntentsTest`); held here is that the handler starts Clock
     * once, in that one form — the platform's API level and whether there is an editor to hand over, nothing else.
     */
    private fun handOffProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val api = sources["clock/AlarmApiActivity.kt"].orEmpty()
        val open = body(api, "private fun openClock(tab: ClockTab, edit: Bundle?)")
        if (open != openClockForm) problems += "AlarmApiActivity.openClock is not its one form:\n  is:      $open\n  must be: $openClockForm"
        if (count(api, "startActivity(") != 1 || count(api, "ClockActivity::class.java") != 1 || count(api, "openClock(") != 5) problems += "the handler starts Clock outside openClock"
        val clears = sources.mapValues { (_, text) -> count(text, "FLAG_ACTIVITY_CLEAR_TASK") }.filterValues { it > 0 }
        val askers = sources.mapValues { (_, text) -> count(text, "handOffFresh(") }.filterValues { it > 0 }
        if (askers != mapOf("clock/AlarmApiActivity.kt" to 1, "clock/ClockIntents.kt" to 1) || clears["clock/AlarmApiActivity.kt"] != 1) {
            problems += "Clock's task is cleared other than by the handler's rule: $askers $clears"
        }
        return problems
    }

    @Test fun `L18-6 the handler hands the editor over as a fresh launch exactly where the rule says`() {
        assertEquals(emptyList<String>(), handOffProblems(SourceScan.all()))
    }

    @Test fun `L18-6 a handler that never clears, always clears, asks about a fixed API or clears another way is caught`() {
        val sources = SourceScan.all()
        fun with(old: String, new: String) = handOffProblems(sources + ("clock/AlarmApiActivity.kt" to mutate(sources.getValue("clock/AlarmApiActivity.kt"), old, new)))
        val rule = ".apply { if (ClockIntents.handOffFresh(Build.VERSION.SDK_INT, edit != null)) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }"
        // The defect itself: the hand-off a new intent on every API.
        assertTrue(with(rule, "").isNotEmpty())
        // The rule asked about a fixed API, or for a request with no editor, or its answer ignored.
        assertTrue(with("handOffFresh(Build.VERSION.SDK_INT, edit != null)", "handOffFresh(36, edit != null)").isNotEmpty())
        assertTrue(with("handOffFresh(Build.VERSION.SDK_INT, edit != null)", "handOffFresh(Build.VERSION.SDK_INT, true)").isNotEmpty())
        assertTrue(with(rule, ".addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)").isNotEmpty())
        // Another flag in its place, or a second start of Clock beside it.
        assertTrue(with("addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }", "addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }").isNotEmpty())
        assertTrue(with("private fun notice(text: String) {", "private fun x() { startActivity(Intent(this, ClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } private fun notice(text: String) {").isNotEmpty())
    }

    @Test fun `L18-3 the exported clock activities read no extra themselves, and the edit bundle is weighed against that intent's caller`() {
        assertEquals(emptyList<String>(), extrasProblems(SourceScan.all()))
    }

    @Test fun `L18-3 an activity that reads an extra itself, drops or fakes the caller, or acts on a half-read request is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = extrasProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        // The defect itself (N1, N4): a raw read in either activity.
        assertTrue(with("clock/ClockActivity.kt", "open.tab?.let { nav.show(it) }", "open.tab?.let { nav.show(it) } intent?.getBundleExtra(\"api_edit\")?.getIntArray(\"days\")?.map { DayOfWeek.of(it) }").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "days = extras.ints(AlarmClock.EXTRA_DAYS, AlarmApiRules.MAX_DAYS),", "days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS),").isNotEmpty())
        // The caller dropped, made the shell's own, or the launch's caller used for a new intent.
        assertTrue(with("clock/ClockActivity.kt", "ClockIntents.open(intent?.let { AndroidExtras(it) }, callerUid, Process.myUid())", "ClockIntents.open(intent?.let { AndroidExtras(it) }, Process.myUid(), Process.myUid())").isNotEmpty())
        assertTrue(with("clock/ClockActivity.kt", "route(intent, ClockIntents.launchCaller(AndroidUriAccess(this)))", "route(intent, Process.myUid())").isNotEmpty())
        assertTrue(with("clock/ClockActivity.kt", "VANILLA_ICE_CREAM) keepIf(route(intent, ClockIntents.NO_CALLER)", "VANILLA_ICE_CREAM) keepIf(route(intent, Process.myUid())").isNotEmpty())
        assertTrue(with("clock/ClockActivity.kt", "route(intent, try { caller.uid } catch (e: Throwable) { ClockIntents.NO_CALLER })", "route(intent, try { caller.uid } catch (e: Throwable) { Process.myUid() })").isNotEmpty())
        assertTrue(with("clock/ClockIntents.kt", "val fromShell = myUid >= 0 && callerUid == myUid", "val fromShell = myUid >= 0 || callerUid == myUid").isNotEmpty())
        // The handler acting on what it parsed although an extra was unreadable.
        assertTrue(with("clock/AlarmApiActivity.kt", "val request = AlarmApiRules.unlessUnreadable(parsed, extras.refused.size)", "val request = parsed").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "AlarmApiRules.unlessUnreadable(parsed, extras.refused.size)", "AlarmApiRules.unlessUnreadable(parsed, 0)").isNotEmpty())
    }
}
