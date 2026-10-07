package app.tileshell.music.radio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.tileshell.BuildConfig
import app.tileshell.diag.Diagnostics
import app.tileshell.music.MusicNet
import app.tileshell.net.FixedEndpoints
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.catalogue.QaBases

/**
 * The platform's side of the radio rules, kept thin (phase 20 build tasks 3 and 5): the one read of the debug-only
 * radio base, the four network facts [StreamGate] decides on, the headers, and the process-start line.
 */
object RadioNet {
    /**
     * The debug-only fixture base (T20-6, r3 D16), or null — always null in a release build, where `QaBases.read`
     * gives nothing and [CatalogueRules.base] ignores a pref whatever it holds. When set it bypasses the mirror
     * lookup, carries the click call, and its host is the one private host [StationUrl.accept] lets through. The ONE
     * read of this pref (`TrustWiringScanTest`).
     */
    fun qaBase(context: Context): String? {
        val app = context.applicationContext
        return RadioMirrors.qaBase(CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.RADIO), FixedEndpoints.RADIO_BROWSER))
    }

    /** [StationUrl.accept]'s and [StationItem.build]'s `qaHost`. */
    fun qaHost(context: Context): String? = StationUrl.qaHost(qaBase(context))

    /** The headers of every directory request: the speaking User-Agent, and never a credential. */
    fun headers(): Map<String, String> = MusicNet.headers(BuildConfig.VERSION_NAME)

    fun metered(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

    /** [StreamGate.decide] over the network the phone is on now ([isStation] false = a home-server track). */
    fun gate(context: Context, isStation: Boolean): StreamGate.Decision {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return StreamGate.decide(
            hasNetwork = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            captive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            metered = cm?.isActiveNetworkMetered ?: false,
            dataSaver = cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            isStation = isStation,
        )
    }

    /**
     * The process-start line `[music] radio: fm feature=<bool>` (Q3's negative, read on the Diagnostics page): whether
     * the phone exposes a broadcast-radio receiver. The shell has no FM tuner either way.
     */
    fun logFm(context: Context) {
        Diagnostics.add(StreamLine.TAG, RadioLine.fm(context.packageManager.hasSystemFeature(FEATURE_BROADCAST_RADIO)))
    }

    private const val FEATURE_BROADCAST_RADIO = "android.hardware.broadcastradio"
}
