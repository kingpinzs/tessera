package app.tessera.r4probe

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * R4, the helper feasibility spike (PLAN.md R4: "phone-only, standalone"), part one.
 *
 * It prints one plain-text report and copies it to the clipboard, because the phone it has to run on is
 * not the machine the answers are needed on. Everything it reports is read live on the device; nothing
 * is assumed, and anything it could not determine says so rather than being left out.
 *
 * WHAT THIS PART ANSWERS
 *  - P5, the nav-bar probe (R7 §4.1.10, phase 04 interview item 8): can a full-height overlay draw
 *    UNDER Android's navigation bar, with gesture navigation and with 3-button? W10M's open action
 *    center covered its nav bar, so if the overlay cannot, the panel's bottom edge has to be designed
 *    around it. Measured, not eyeballed: the overlay asks for the whole display, then reports its own
 *    height against the display's and what the system says the nav-bar inset is.
 *  - The facts phase 04's other answers depend on: navigation mode, display metrics in the epx the
 *    plan measures in, One UI and API level, and whether the toggles the helper is supposed to flip
 *    are writable without it (they are not — this records HOW they fail, which is the case for the
 *    helper).
 *
 * WHAT IT DOES NOT DO YET
 *  - Parts 1-4 of R4 (app_process under the shell uid, daemonising, the binder handoff, survival) need
 *    an ADB pairing client inside the app. That is the next probe, and R4's licence part already found
 *    an Apache-2.0 library that does the SPAKE2 pairing (adb-kt).
 *  - PQ2, the T-Mobile visual voicemail probe.
 */
class ProbeActivity : Activity() {

    private val report = StringBuilder()
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, dp(36), pad, pad)
        }
        out = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(button("Copy the whole report") { copy() })
        root.addView(button("Overlay probe: run it (needs the permission below)") { runOverlayProbe() })
        root.addView(button("Grant 'Display over other apps'") { askForOverlay() })
        root.addView(ScrollView(this).apply { addView(out) })
        setContentView(root)
        collect()
        // The insets are null in onCreate AND in onResume: neither has had a layout pass yet, and the
        // first run of this probe duly reported "insets UNAVAILABLE" on a device that has insets. They
        // arrive with the first traversal, so the report is rebuilt when they do.
        root.setOnApplyWindowInsetsListener { _, insets ->
            collect()
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // The overlay permission is granted on a Settings page, so re-read on the way back.
        if (report.isNotEmpty()) collect()
    }

    // ---------------- the report ----------------

    private fun collect() {
        report.setLength(0)
        line("R4 PROBE — part one")
        line("run at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        blank()

        section("DEVICE")
        kv("model", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        kv("android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        kv("build", Build.DISPLAY)
        kv("fingerprint", Build.FINGERPRINT)
        kv("one ui", oneUi())
        kv("security patch", Build.VERSION.SECURITY_PATCH)
        blank()

        section("DISPLAY, in the units the plan measures in")
        val metrics = resources.displayMetrics
        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        kv("size px", "$widthPx x $heightPx")
        kv("density", "${metrics.density} (dpi ${metrics.densityDpi})")
        // RV10 / Q11: the plan's canvas is 360 epx wide, so 1 epx is the display width / 360.
        kv("1 epx in px", "%.4f".format(widthPx / 360f))
        kv("height in epx", "%.2f".format(heightPx * 360f / widthPx))
        blank()

        section("NAVIGATION (P5 context)")
        val mode = secureInt("navigation_mode")
        kv("navigation_mode", "$mode ${navName(mode)}")
        val insets = window.decorView.rootWindowInsets
        if (insets != null) {
            val nav = insets.getInsets(WindowInsets.Type.navigationBars())
            val status = insets.getInsets(WindowInsets.Type.statusBars())
            val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
            kv("nav bar inset px", "left ${nav.left} top ${nav.top} right ${nav.right} bottom ${nav.bottom}")
            kv("status bar inset px", "top ${status.top}")
            kv("cutout inset px", "top ${cutout.top}")
        } else {
            kv("insets", "UNAVAILABLE (no root window insets yet)")
        }
        kv("overlay permission", if (Settings.canDrawOverlays(this)) "granted" else "NOT granted")
        blank()

        section("P5 — THE OVERLAY PROBE")
        line(overlayResult ?: "not run yet. Grant the permission, then tap the overlay button.")
        line("Run it once with gesture navigation and once with 3-button, and paste both reports:")
        line("  Settings > Display > Navigation bar")
        blank()

        section("TOGGLES THE HELPER IS FOR")
        line("These are what the privileged helper exists to flip. An app cannot, and HOW it")
        line("fails is the evidence that the helper is needed at all.")
        kv("WRITE_SECURE_SETTINGS", permissionState("android.permission.WRITE_SECURE_SETTINGS"))
        kv("WRITE_SETTINGS", if (Settings.System.canWrite(this)) "granted" else "not granted")
        // Both of these live in Settings.Global, not Secure. Reading them from Secure returned -1 on
        // the S25 Ultra, which reads as "off" and means "wrong table".
        kv("adb_wifi_enabled", "${secureGlobalInt("adb_wifi_enabled")} (Wireless debugging)")
        kv("development_settings_enabled", "${secureGlobalInt("development_settings_enabled")}")
        blank()

        section("SHELL PRESENT?")
        kv("app.tileshell installed", installed("app.tileshell"))
        kv("this probe's uid", "${android.os.Process.myUid()}")
        blank()

        line("END OF REPORT")
        out.text = report
    }

    // ---------------- P5 ----------------

    private var overlayResult: String? = null
    private val touches = mutableListOf<String>()

    /**
     * Ask for a window the size of the whole display and measure what actually arrives.
     *
     * The question P5 asks is whether the overlay's own content can occupy the nav bar's strip. An
     * overlay that is laid out to the display's full height AND reports a zero bottom inset after
     * asking for it is drawing under the bar; one that stops short is not. Both numbers are printed so
     * the conclusion can be checked rather than trusted.
     */
    private fun runOverlayProbe() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Grant 'Display over other apps' first")
            return
        }
        val wm = getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val results = StringBuilder()
        touches.clear()
        results.append("navigation_mode = ${secureInt("navigation_mode")} ${navName(secureInt("navigation_mode"))}\n")
        results.append("display bounds  ${bounds.width()} x ${bounds.height()} px\n\n")

        // TWO attempts, because the first version of this probe asked the wrong question and answered
        // "cannot cover the nav bar" on a device that had never been asked properly.
        //
        //   A. MATCH_PARENT with the legacy flags. A window is fitted to the system-bar insets by
        //      DEFAULT, so MATCH_PARENT means "as big as the space left over", not "as big as the
        //      screen". It comes back exactly the nav bar short, every time, on every device — which
        //      looks like a platform answer and is really just the default.
        //   B. The same window with setFitInsetsTypes(0) and an EXPLICIT pixel height. That is the
        //      modern way to say "do not fit me to anything"; it is what an overlay that means to
        //      cover the bar has to do, and it is the one whose answer counts.
        //
        // Both are reported. If A and B differ, the difference IS the finding.
        fun attempt(label: String, fitInsets: Boolean, explicitSize: Boolean, then: () -> Unit) {
            val params = WindowManager.LayoutParams(
                if (explicitSize) bounds.width() else WindowManager.LayoutParams.MATCH_PARENT,
                if (explicitSize) bounds.height() else WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                if (!fitInsets) runCatching { setFitInsetsTypes(0) }
            }
            // Attempt B is also the TOUCH test, which is the half a screenshot cannot answer: the nav
            // glyphs being drawn on top says nothing about who receives a tap there. The overlay
            // records every touch it gets and where. A tap in the bottom strip that never arrives is
            // the nav bar taking it, and that would make any content placed there dead.
            val probe: View = if (label.startsWith("B")) {
                android.widget.FrameLayout(this).apply {
                    setBackgroundColor(0x5500AAFFL.toInt())
                    addView(TextView(this@ProbeActivity).apply {
                        text = "TAP THE STRIP OVER THE NAV BUTTONS,\nthen tap anywhere higher up"
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        gravity = Gravity.CENTER
                    })
                    setOnTouchListener { _, e ->
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                            val nav = navBarPx()
                            val inStrip = nav > 0 && e.y >= height - nav
                            touches += "    touch at y=${e.y.toInt()} of $height — " +
                                (if (inStrip) "IN the nav bar's strip" else "above the strip")
                        }
                        true
                    }
                }
            } else {
                View(this).apply { setBackgroundColor(0x5500AAFFL.toInt()) }
            }
            runCatching { wm.addView(probe, params) }.onFailure {
                results.append("$label: could not add the window (${it.javaClass.simpleName})\n\n")
                then()
                return
            }
            probe.post {
                val navInset = probe.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: -1
                val short = bounds.height() - probe.height
                results.append("$label\n")
                results.append("  laid out       ${probe.width} x ${probe.height} px\n")
                results.append("  short by       $short px (the nav bar is ${navBarPx()} px)\n")
                results.append("  nav inset seen $navInset px from inside the overlay\n")
                results.append(
                    if (short <= 0) "  REACHES the display's bottom edge.\n\n"
                    else "  STOPS $short px short.\n\n"
                )
                // Keep B on screen to be photographed; A is measured and taken straight down.
                if (label.startsWith("B")) {
                    overlayResult = results.toString() +
                        "Screenshot it, and TAP the strip over the nav buttons before it goes.\n" +
                        "Whether the system still DRAWS its glyphs on top is the screenshot's job; who\n" +
                        "RECEIVES a tap there is the touch list below."
                    collect()
                    toast("15 s: screenshot it, then tap the nav strip")
                    probe.postDelayed({
                        runCatching { wm.removeView(probe) }
                        results.append("\n  TOUCHES the overlay received:\n")
                        results.append(
                            if (touches.isEmpty()) "    none at all\n"
                            else touches.joinToString("\n") + "\n"
                        )
                        results.append(
                            if (touches.any { "IN the nav" in it })
                                "  RESULT: a tap in the nav bar's strip REACHED the overlay.\n"
                            else
                                "  RESULT: no tap in the strip reached the overlay. Either none was made, or\n" +
                                    "  the nav bar takes them — compare with the taps above the strip.\n"
                        )
                        overlayResult = results.toString()
                        collect()
                    }, 15000)
                } else {
                    runCatching { wm.removeView(probe) }
                    then()
                }
            }
        }

        attempt("A. MATCH_PARENT, insets fitted (the default)", fitInsets = true, explicitSize = false) {
            attempt("B. setFitInsetsTypes(0) + explicit full height", fitInsets = false, explicitSize = true) {}
        }
    }

    private fun askForOverlay() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", packageName, null))
            )
        }.onFailure { toast("No overlay settings page on this build") }
    }

    // ---------------- helpers ----------------

    private fun section(name: String) { line("== $name"); }
    private fun line(text: String) { report.append(text).append('\n') }
    private fun blank() { report.append('\n') }
    private fun kv(name: String, value: String) { line("  %-28s %s".format(name, value)) }

    private fun navBarPx(): Int =
        window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: -1

    private fun secureInt(key: String): Int =
        runCatching { Settings.Secure.getInt(contentResolver, key, -1) }.getOrDefault(-2)

    private fun secureGlobalInt(key: String): Int =
        runCatching { Settings.Global.getInt(contentResolver, key, -1) }.getOrDefault(-2)

    private fun navName(mode: Int) = when (mode) {
        0 -> "(3-button)"
        1 -> "(2-button)"
        2 -> "(gesture)"
        -1 -> "(not set)"
        else -> "(unreadable)"
    }

    private fun permissionState(name: String): String =
        if (checkSelfPermission(name) == android.content.pm.PackageManager.PERMISSION_GRANTED) "granted"
        else "not granted (expected: it is signature|privileged)"

    /**
     * Package visibility (Android 11+) hides other apps unless they are declared in <queries>, and a
     * plain runCatching here reported "not installed" for a shell that WAS installed. A probe that can
     * be quietly wrong is worse than one that says it does not know, so the failure is reported.
     */
    private fun installed(pkg: String): String = try {
        packageManager.getPackageInfo(pkg, 0)
        "yes"
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        "no"
    } catch (e: Throwable) {
        "UNKNOWN (${e.javaClass.simpleName})"
    }

    /** Samsung records One UI as a system property; absent on anything else. */
    private fun oneUi(): String = runCatching {
        @Suppress("PrivateApi")
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        val raw = get.invoke(null, "ro.build.version.oneui") as? String
        if (raw.isNullOrBlank()) "not a One UI build" else raw
    }.getOrElse { "unreadable (${it.javaClass.simpleName})" }

    private fun copy() {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("R4 probe", report.toString()))
        toast("Copied — paste it back")
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
