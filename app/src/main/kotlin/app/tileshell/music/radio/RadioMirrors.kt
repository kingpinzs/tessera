package app.tileshell.music.radio

import app.tileshell.net.FixedEndpoints
import java.util.Locale
import java.util.Random

/**
 * Which radio-browser server is asked (phase 20 Decisions "the station directory"; r11 addendum §1, §7.5; T20-10).
 * The directory's etiquette: look `all.api.radio-browser.info` up, never link one server; shuffle; on a failure try
 * the next. The lookup gives addresses, and each address's canonical name is a mirror (`de1.api.radio-browser.info`
 * — one today). [order] keeps only names under `.api.radio-browser.info`, so every request stays inside the https-only
 * domain-config whatever a resolver answers, and an empty answer falls back to the round-robin name itself.
 *
 * A set debug-only override base bypasses the lookup altogether and carries every call — the click call too. Pure
 * (`RadioMirrorsTest`).
 */
object RadioMirrors {
    /** The name looked up, and the mirror of last resort. */
    const val ALL = "all.api.radio-browser.info"

    private const val SUFFIX = ".api.radio-browser.info"

    /** The mirrors to try, in order: [names] filtered to the directory's own, each once, shuffled by [seed]; never empty. */
    fun order(names: List<String>, seed: Long): List<String> {
        val own = names.map { it.trim().lowercase(Locale.ROOT).trimEnd('.') }
            .filter { it.endsWith(SUFFIX) && isLabel(it.removeSuffix(SUFFIX)) }
            .distinct()
            .toMutableList()
        own.shuffle(Random(seed))
        return own.ifEmpty { listOf(ALL) }
    }

    /** One DNS label: letters, digits and inner hyphens — so `evil.example/x.api.radio-browser.info` is not a mirror. */
    private fun isLabel(text: String): Boolean =
        text.length in 1..63 && text.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } && !text.startsWith('-') && !text.endsWith('-')

    /** The mirror after [failed] in [order], or null when that was the last. */
    fun next(order: List<String>, failed: String): String? = order.getOrNull(order.indexOf(failed) + 1).takeIf { failed in order }

    /** A mirror's base address: the fixed endpoint's own (`https://…/`) with the mirror's name as its host. */
    fun base(mirror: String): String = FixedEndpoints.RADIO_BROWSER.replace(FixedEndpoints.hostOf(FixedEndpoints.RADIO_BROWSER), mirror)

    /** Whether the DNS lookup is made at all: not when the debug-only override names the base. */
    fun needsLookup(qaBase: String?): Boolean = qaBase == null

    /**
     * The bases to try in turn. With the override ([qaBase] non-null — a debug build with the pref set) it is the one
     * base and [names] are not looked at; else the mirrors of [order].
     */
    fun bases(qaBase: String?, names: List<String>, seed: Long): List<String> =
        if (qaBase != null) listOf(qaBase) else order(names, seed).map(::base)

    /**
     * The override out of `CatalogueRules.base(debug, pref, FixedEndpoints.RADIO_BROWSER)`'s answer: null when that
     * answer is the fixed endpoint — always, in a release build.
     */
    fun qaBase(chosenBase: String): String? = chosenBase.takeIf { it != FixedEndpoints.RADIO_BROWSER }
}
