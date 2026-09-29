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
 * PART TWO (2026-09-28), driven by the host script docs/plan/qa/r4/r4.sh over USB:
 *  - Parts 1-4 of R4: [HelperMain] runs under the shell uid via app_process, daemonises, and hands this app a binder
 *    through [HelperProvider]; the app then asks it to flip each toggle in [Toggles] (the helper pass) and to answer
 *    after the app is killed, the cable is pulled and Wireless debugging goes off. The helper is started from the PC:
 *    R4 proves the helper, and the on-device pairing client's licence (adb-kt, Apache-2.0) is already recorded in
 *    phase 04's doc; the pairing client itself is phase 04's build.
 *  - The cross-window blur probe (INDEX R4 row, R10): isCrossWindowBlurEnabled, its listener, and a FLAG_BLUR_BEHIND
 *    overlay held up for a screenshot, run by the script with power saving off and on.
 *  - `run` extra (overlay | blur | helper_ping | helper_toggles) so the script can drive each probe, and the report
 *    written to files/report.txt so the script can read it (`run-as`, the debug APK).
 * PQ2 (voicemail) is read by the script from the shell (carrier config, the handler app, counts only); nothing here
 * logs in to the carrier as the line.
 */
class ProbeActivity : Activity() {

    private val report = StringBuilder()
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The run needs the screen on throughout, including after the planned restart in part 4a (it may come back
        // behind the lock screen).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
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
        // R4 on this phone alone: the run, and the panel it asks its hands-on questions in.
        root.addView(button("Run R4 on this phone") { Runner.start(this, autoMode = false) })
        promptText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; setPadding(0, dp(8), 0, dp(8)) }
        promptRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(promptText); root.addView(promptRow)
        root.addView(button("Copy the whole report") { copy() })
        root.addView(button("Overlay probe: run it (needs the permission below)") { runOverlayProbe("app") })
        root.addView(button("Blur probe: run it (needs the permission below)") { runBlurProbe() })
        root.addView(button("Grant 'Display over other apps'") { askForOverlay() })
        root.addView(button("Helper: ping it") { runHelperPing() })
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
        HelperLink.onChange = { runOnUiThread { collect() } }
        RunToken.get(this)   // made at first open, so test tooling can read it before its first `run` request
        Runner.ui = this
        Runner.onChange = { runOnUiThread {
            renderPrompt(); collect()
            // A clean-up that could not finish offers Restore now right here, not only on the next open.
            if (!Runner.running && Runner.unfinishedStage(this) == "cleanup-pending") offerUnfinished("cleanup-pending")
        } }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        root.post {
            handleRun(intent)
            renderPrompt()
            // A run that did not finish (an unplanned stop): offer to carry on or to put everything back. After
            // renderPrompt, which would otherwise wipe it (review r4-phone r2 B5).
            val left = Runner.unfinishedStage(this)
            if (left != null && !Runner.running && intent?.getStringExtra("run") == null) offerUnfinished(left)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRun(intent)
    }

    /** The host script drives each probe with `am start … --es run <name>`; each writes "<NAME> DONE" when finished. */
    private lateinit var promptText: TextView
    private lateinit var promptRow: LinearLayout

    private var offering = false

    /** Draws the run's current question (or nothing) with its buttons; leaves an unfinished-run offer up until acted on. */
    private fun renderPrompt() {
        if (offering && !Runner.running && Runner.promptText == null) return
        offering = false
        promptText.text = Runner.promptText ?: ""
        promptRow.removeAllViews()
        for (label in Runner.promptButtons) {
            promptRow.addView(button(label) {
                if (label == Runner.OPEN_DEV) runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                else Runner.answer(label)
            })
        }
    }

    private fun offerUnfinished(stage: String) {
        offering = true
        promptRow.removeAllViews()
        if (stage == "cleanup-pending") {
            // A clean-up that did not finish is only ever retried: carrying on would take a new baseline over the
            // settings it left changed.
            promptText.text = "The last R4 run could not put everything back. Retry it now (Wireless debugging must be on)."
        } else {
            promptText.text = "An R4 run did not finish (it stopped at '$stage'). Carry on, or put everything back now?"
            promptRow.addView(button("Carry on") { offering = false; Runner.resume(this) })
        }
        promptRow.addView(button("Restore now") { offering = false; Runner.restoreNow(this) })
    }

    /** For the run's P5 step: starts the overlay probe of [kind] ("a11y" or "app") and reads its result text. */
    fun startOverlay(kind: String) { nonce = "run"; overlayResults.remove(kind); runOverlayProbe(kind) }
    fun overlayText(kind: String): String? = overlayResults[kind]

    /** The script's run id: each "<NAME> DONE" line carries it, so a wait can never be satisfied by an earlier run. */
    private var nonce = ""

    private fun handleRun(intent: Intent?) {
        // The extras drive shell-uid work and this activity is exported: they are obeyed only with the app's own secret
        // (RunToken), which the app's own commands and debug test tooling carry and no other app can read.
        if (intent?.getStringExtra("run") != null && intent.getStringExtra("token") != RunToken.get(this)) {
            HelperLink.note("ignored a 'run' request without this app's token")
            intent.removeExtra("run"); return
        }
        intent?.getStringExtra("nonce")?.let { nonce = it }
        when (intent?.getStringExtra("run")) {
            "overlay" -> runOverlayProbe("app")
            "overlay_a11y" -> runOverlayProbe("a11y")
            "blur" -> runBlurProbe()
            "helper_ping" -> runHelperPing()
            "helper_toggles" -> runHelperToggles()
            // The phone-only run: start (auto = the emulator test, which skips the hands-on steps) and resume (after the
            // planned kill in part 4a).
            "start" -> Runner.start(this, autoMode = intent.getBooleanExtra("auto", false))
            "resume" -> Runner.resume(this)
            // Test tooling: answers the run's current question.
            "answer" -> Runner.answer(intent.getStringExtra("answer") ?: "")
            // Spike (emulator): pair / connect to the phone's own adb from extras; the real run takes the code from a
            // notification and finds the ports with mDNS.
            "pair" -> spike { AdbSelf.pair(this, intent.getStringExtra("host") ?: "", intent.getIntExtra("port", 0), intent.getStringExtra("code") ?: "") }
            "discover" -> spike { Discovery.dump(this, Discovery.CONNECT, 8) + Discovery.dump(this, Discovery.PAIRING, 4) }
            "connect" -> spike {
                val c = AdbSelf.connect(this, intent.getStringExtra("host") ?: "", intent.getIntExtra("port", 0))
                c + "\n" + AdbSelf.shell("id").second.trim()
            }
        }
        intent?.removeExtra("run")
    }

    override fun onResume() {
        super.onResume()
        // The overlay permission is granted on a Settings page, so re-read on the way back.
        if (report.isNotEmpty()) collect()
    }

    // ---------------- the report ----------------

    private fun collect() {
        report.setLength(0)
        line("R4 PROBE")
        val summary = Runner.summary()
        if (summary.isNotEmpty()) {
            section("RUN (R4 on this phone)")
            report.append(summary)
            blank()
            section("RUN DETAILS")
            report.append(Runner.details())
            blank()
        }
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
        kv("accessibility service", if (ProbeA11yService.instance != null) "connected" else "not connected")
        if (overlayResults.isEmpty()) line("not run yet. Grant the permission, then tap the overlay button.")
        overlayResults.forEach { (kind, text) -> line("-- $kind"); line(text) }
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

        section("SELF ADB (pairing spike)")
        line(spikeResult ?: "not run yet")
        blank()
        section("HELPER (R4 parts 1-4)")
        kv("binder", if (HelperLink.connected()) "connected (tag ${HelperLink.tag}, from uid ${HelperLink.fromUid})" else "none")
        HelperLink.notes().forEach { line("  $it") }
        helperPingResult?.let { line("  ping: $it") }
        blank()
        section("HELPER TOGGLES (each flipped by the helper as the shell uid, read back, restored)")
        line(helperTogglesResult ?: "not run yet")
        blank()
        section("BLUR (cross-window blur probe)")
        line(blurResult ?: "not run yet")
        blank()

        section("SHELL PRESENT?")
        kv("app.tileshell installed", installed("app.tileshell"))
        kv("this probe's uid", "${android.os.Process.myUid()}")
        blank()

        line("END OF REPORT")
        // The report is pasted back: the phone's network address is written as "<this phone>" everywhere in it.
        val text = Runner.redact(report.toString())
        out.text = text
        runCatching { java.io.File(filesDir, "report.txt").writeText(text) }
    }

    private var spikeResult: String? = null

    private fun spike(work: () -> String) {
        val n = nonce
        Thread { val r = work(); runOnUiThread { spikeResult = r + "\nSPIKE DONE $n"; collect() } }.start()
    }

    // ---------------- helper (R4 parts 1-4) ----------------

    private var helperPingResult: String? = null
    private var helperTogglesResult: String? = null

    private fun runHelperPing() {
        Thread {
            val r = HelperLink.ping()
            runOnUiThread { helperPingResult = r + "\nHELPER_PING DONE $nonce"; collect() }
        }.start()
    }

    /**
     * Each toggle through the helper's binder, so as the shell uid: read; if the read is a recognised state, flip away,
     * read, flip back, read; if not, leave it alone and say so; a toggle with a probe runs only its probe.
     */
    private fun runHelperToggles() {
        helperTogglesResult = "running…"
        collect()
        Thread {
            val sb = StringBuilder()
            if (!HelperLink.connected()) sb.append("no helper binder: nothing flipped\n")
            else for (t in Toggles.ALL) {
                sb.append("  ${t.name}  [${t.forItem}]\n")
                if (t.probe != null) {
                    val (e, o) = HelperLink.toggle(t.name, "probe")
                    sb.append("    probe   (exit $e) ${one(o)}\n")
                    sb.append("    PROBE ${if ("SecurityException" in o) "REFUSED to the shell uid" else "reached (no state changed)"}\n")
                    continue
                }
                val (e0, before) = HelperLink.toggle(t.name, "read")
                val st = t.state(before.trim())
                sb.append("    before  (exit $e0) ${one(before)}  [$st]\n")
                if (st == "unknown") { sb.append("    UNRECOGNISED STATE: not flipped\n"); continue }
                val (e1, o1) = HelperLink.toggle(t.name, if (st == "on") "off" else "on")
                Thread.sleep(3000)
                val (_, mid) = HelperLink.toggle(t.name, "read")
                val (e2, o2) = HelperLink.toggle(t.name, st)
                Thread.sleep(3000)
                val (_, after) = HelperLink.toggle(t.name, "read")
                val stMid = t.state(mid.trim()); val stAfter = t.state(after.trim())
                sb.append("    flip ${if (st == "on") "off" else "on "} (exit $e1) ${one(o1)}\n")
                sb.append("    read    ${one(mid)}  [$stMid]\n")
                sb.append("    flip $st back (exit $e2) ${one(o2)}\n")
                sb.append("    after   ${one(after)}  [$stAfter]\n")
                sb.append("    CHANGED ${if (stMid != st) "yes" else "NO"} · RESTORED ${if (stAfter == st) "yes" else "NO"}\n")
                runOnUiThread { helperTogglesResult = sb.toString() + "  …"; collect() }
            }
            sb.append("HELPER_TOGGLES DONE $nonce")
            runOnUiThread { helperTogglesResult = sb.toString(); collect() }
        }.start()
    }

    private fun one(text: String) = text.trim().replace('\n', ' ').take(160).ifEmpty { "(empty)" }

    // ---------------- blur (R10's cross-window blur probe) ----------------

    private var blurResult: String? = null

    /**
     * Whether an overlay window can blur what is behind it: the platform's own answer (isCrossWindowBlurEnabled, which
     * turns false under power saving on some devices, so its listener is watched too), then a FLAG_BLUR_BEHIND overlay
     * over this report's text, held for 8 s so the script's screenshot can show whether the text behind it is blurred.
     */
    private fun runBlurProbe() {
        if (!Settings.canDrawOverlays(this)) { toast("Grant 'Display over other apps' first"); return }
        val wm = getSystemService(WindowManager::class.java)
        val sb = StringBuilder()
        sb.append("  isCrossWindowBlurEnabled  ${wm.isCrossWindowBlurEnabled}\n")
        sb.append("  power saving (low_power)  ${secureGlobalInt("low_power")}\n")
        val events = mutableListOf<String>()
        val listener = java.util.function.Consumer<Boolean> { events += "listener: blur enabled = $it" }
        runCatching { wm.addCrossWindowBlurEnabledListener(mainExecutor, listener) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply { blurBehindRadius = 80 }
        val veil = TextView(this).apply {
            text = "BLUR PROBE — is the text behind this blurred?"
            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            setBackgroundColor(0x33000000)
        }
        runCatching { wm.addView(veil, params) }.onFailure {
            sb.append("  overlay NOT added: ${it.javaClass.simpleName}: ${it.message}\n")
            blurResult = sb.toString() + "BLUR DONE $nonce"; collect(); return
        }
        sb.append("  overlay added: FLAG_BLUR_BEHIND, blurBehindRadius 80; held 8 s for the screenshot\n")
        blurResult = sb.toString() + "  (overlay up)"
        collect()
        veil.postDelayed({
            runCatching { wm.removeView(veil) }
            runCatching { wm.removeCrossWindowBlurEnabledListener(listener) }
            sb.append(if (events.isEmpty()) "  listener: no change while up\n" else events.joinToString("\n", "  ", "\n"))
            blurResult = sb.toString() + "BLUR DONE $nonce"
            collect()
        }, 8000)
    }

    // ---------------- P5 ----------------

    /** Per window kind ("app", "a11y"): the probe's result text. */
    private val overlayResults = linkedMapOf<String, String>()
    private val touches = mutableListOf<String>()

    /**
     * Ask for a window the size of the whole display and measure what actually arrives — for BOTH window types that
     * matter (review/2026-09-28-r4-kit-review.md B5): "app" is TYPE_APPLICATION_OVERLAY, which the window manager
     * layers BELOW the nav bar; "a11y" is TYPE_ACCESSIBILITY_OVERLAY, added through [ProbeA11yService], which is layered
     * above it and is what phase 04's action center is (its accessibility overlay). P5 is answered by the a11y run; the
     * app run is kept as the contrast.
     *
     * The question is whether the overlay's own content can occupy the nav bar's strip. An overlay laid out to the
     * display's full height AND reporting a zero bottom inset after asking for it is drawing under the bar; one that
     * stops short is not. Both numbers are printed so the conclusion can be checked rather than trusted.
     */
    private fun runOverlayProbe(kind: String) {
        val ctx: android.content.Context = if (kind == "a11y") {
            ProbeA11yService.instance ?: run {
                overlayResults[kind] = "the accessibility service is not connected — nothing was measured\nOVERLAY DONE $kind $nonce"
                collect(); return
            }
        } else {
            if (!Settings.canDrawOverlays(this)) {
                overlayResults[kind] = "no 'Display over other apps' permission — nothing was measured\nOVERLAY DONE $kind $nonce"
                collect(); return
            }
            this
        }
        val type = if (kind == "a11y") WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                   else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val wm = ctx.getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val results = StringBuilder()
        touches.clear()
        results.append("window type     ${if (kind == "a11y") "TYPE_ACCESSIBILITY_OVERLAY (phase 04's action center)" else "TYPE_APPLICATION_OVERLAY (contrast only)"}\n")
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
                type,
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
                android.widget.FrameLayout(ctx).apply {
                    setBackgroundColor(0x5500AAFFL.toInt())
                    addView(TextView(ctx).apply {
                        text = "TAP THE STRIP OVER THE NAV BUTTONS,\nthen tap anywhere higher up\n($kind)"
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
                View(ctx).apply { setBackgroundColor(0x5500AAFFL.toInt()) }
            }
            runCatching { wm.addView(probe, params) }.onFailure {
                results.append("$label: could not add the window (${it.javaClass.simpleName}: ${it.message})\n\n")
                if (label.startsWith("B")) { overlayResults[kind] = results.toString() + "OVERLAY DONE $kind $nonce"; collect() }
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
                    overlayResults[kind] = results.toString() +
                        "Screenshot it, and TAP the strip over the nav buttons before it goes.\n" +
                        "Whether the system still DRAWS its glyphs on top is the screenshot's job; who\n" +
                        "RECEIVES a tap there is the touch list below.\nWINDOW UP $kind $nonce"
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
                        overlayResults[kind] = results.toString() + "OVERLAY DONE $kind $nonce"
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
            .setPrimaryClip(ClipData.newPlainText("R4 probe", Runner.redact(report.toString())))
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
