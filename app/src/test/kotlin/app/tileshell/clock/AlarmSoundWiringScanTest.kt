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
 *  - the editor's hand-off bundle (ClockActivity is exported) has its sound weighed again, with nobody to ask;
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
        val clock = sources["clock/ClockActivity.kt"].orEmpty()
        val ring = sources["clock/RingService.kt"].orEmpty()
        // The API handler: the extra goes to the rule and nowhere else, beside the real port; the rule's sound is stored.
        if (count(api, "EXTRA_RINGTONE") != 1 || !api.contains("ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE),") ||
            count(api, "AlarmApiRules.parse(") != 1 || !api.contains("access = AndroidUriAccess(this), )") || count(api, "access") != 1
        ) problems += "the handler does not hand AlarmApiRules.parse the caller's ringtone and the real port, once"
        val parsers = sources.mapValues { (_, text) -> count(text, "AlarmApiRules.parse(") }.filterValues { it > 0 }
        if (parsers != mapOf("clock/AlarmApiActivity.kt" to 1)) problems += "the API's request is parsed somewhere else: $parsers"
        if (count(api, "AlarmSound") != 0 || Regex("\\.sound\\b").findAll(api).count() != 3 ||
            !api.contains("val alarm = store.newAlarm(f.hour!!, f.minute, f.message, f.days, f.sound, Alarm.DEFAULT_SNOOZE)") ||
            !api.contains("putString(ClockActivity.API_SOUND_KIND, f.sound.kind.name) f.sound.uri?.let { putString(ClockActivity.API_SOUND_URI, it) }")
        ) problems += "the handler stores or hands on a sound other than the rule's"
        // The rule's side: the ringtone becomes a sound only through AlarmRingtoneRules.fromApi, asked with that port.
        if (Regex("\\bringtone\\b").findAll(rules).count() != 2 || Regex("\\baccess\\b").findAll(rules).count() != 2 || count(rules, "AlarmSound(") != 0 ||
            !rules.contains("val tone = AlarmRingtoneRules.fromApi(ringtone, access) val fields = AlarmFields(hour, minute, daySet, text, tone.sound, tone.refused)")
        ) problems += "AlarmApiRules does not make the ringtone a sound through AlarmRingtoneRules.fromApi(ringtone, access)"
        val askers = sources.mapValues { (_, text) -> count(text, "AlarmRingtoneRules.fromApi(") }.filterValues { it > 0 }
        if (askers != mapOf("clock/AlarmApiRules.kt" to 1, "clock/ClockActivity.kt" to 1)) problems += "a ringtone is weighed somewhere else: $askers"
        // The editor's hand-off: exported, so the bundle's sound is weighed again — with no port, never with a yes.
        if (count(clock, "API_SOUND_URI") != 2 || !clock.contains("AlarmSound.Kind.TONE.name -> AlarmRingtoneRules.fromApi(api.getString(API_SOUND_URI), null).sound") ||
            count(clock, "AlarmSound(") != 1 || !clock.contains("AlarmSound.Kind.VIBRATE.name -> AlarmSound(AlarmSound.Kind.VIBRATE)")
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
        assertTrue(with("clock/AlarmApiRules.kt", "val tone = AlarmRingtoneRules.fromApi(ringtone, access)", "val tone = AlarmRingtoneRules.FromApi(if (ringtone == null) AlarmSound.DEFAULT else AlarmSound(AlarmSound.Kind.TONE, ringtone, null))").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "f.days, f.sound, Alarm.DEFAULT_SNOOZE)", "f.days, AlarmSound(AlarmSound.Kind.TONE, intent.getStringExtra(AlarmClock.EXTRA_RINGTONE), null), Alarm.DEFAULT_SNOOZE)").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "f.sound.uri?.let { putString(ClockActivity.API_SOUND_URI, it) }", "putString(ClockActivity.API_SOUND_URI, intent.getStringExtra(AlarmClock.EXTRA_RINGTONE))").isNotEmpty())
        // The rule's answer ignored, or asked with no port / about nothing.
        assertTrue(with("clock/AlarmApiRules.kt", "AlarmFields(hour, minute, daySet, text, tone.sound, tone.refused)", "AlarmFields(hour, minute, daySet, text, ringtone?.let { AlarmSound(AlarmSound.Kind.TONE, it, null) } ?: tone.sound, tone.refused)").isNotEmpty())
        assertTrue(with("clock/AlarmApiRules.kt", "AlarmRingtoneRules.fromApi(ringtone, access)", "AlarmRingtoneRules.fromApi(ringtone, null)").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "access = AndroidUriAccess(this),", "access = null,").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE),", "ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE) ?: intent.dataString,").isNotEmpty())
        // The editor trusting its bundle (the old line), or a second parser of the API.
        assertTrue(with("clock/ClockActivity.kt", "AlarmRingtoneRules.fromApi(api.getString(API_SOUND_URI), null).sound", "AlarmSound(AlarmSound.Kind.TONE, api.getString(API_SOUND_URI), null)").isNotEmpty())
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
}
