package app.tessera.r4probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.io.File
import java.util.Properties
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * R4 on the phone alone (PLAN.md R4 "phone-only, standalone"; Jeremy 2026-09-28: the phone cannot be plugged into the
 * PC). The same run docs/plan/qa/r4/r4.sh does from a PC, done from inside this app: it pairs with the phone's own
 * Wireless debugging ([AdbSelf], code typed into a notification), runs shell-uid steps over that adb, starts the helper
 * ([HelperMain]) with it, and asks Jeremy in the app for the few hands-on steps. Every stage is saved to files, so the
 * planned kill in part 4a — and an unplanned one — resume where they left off, and a run that stops early can still be
 * restored. SAFETY is the script's (review/2026-09-28-r4-kit-review{,-r2,-r3}.md): a toggle is flipped only when its
 * read is a recognised state; the clean-up restores against the saved baseline and reports anything it cannot.
 */
object Runner {
    private lateinit var app: Context
    private val state = Properties()
    private val answers = LinkedBlockingQueue<String>()
    @Volatile var promptText: String? = null; private set
    @Volatile var promptButtons: List<String> = emptyList(); private set
    @Volatile var running = false; private set
    var onChange: (() -> Unit)? = null
    /** The activity, for the P5 overlay probe (it owns the drawing code). */
    @Volatile var ui: ProbeActivity? = null

    const val OPEN_DEV = "Open Developer options"
    private const val TAG = "phone"
    private val STAGES = listOf("pair", "baseline", "p1", "p2", "p3", "p4a", "p4a-check", "p4b", "blur", "p5", "pq2", "cleanup", "p4d", "done")

    private fun f(name: String) = File(app.filesDir, name)
    private fun save() = runCatching { f("run-state.properties").outputStream().use { state.store(it, "R4 phone-only run") } }
    private fun load() { runCatching { f("run-state.properties").inputStream().use { state.load(it) } } }
    private fun get(k: String, d: String = "") = state.getProperty(k, d)
    private fun put(k: String, v: String) { state.setProperty(k, v); save() }
    private val auto get() = get("auto") == "1"

    fun summary(): String = runCatching { f("run-summary.txt").readText() }.getOrDefault("")
    fun details(): String = runCatching { f("run-details.txt").readText() }.getOrDefault("")
    fun unfinishedStage(ctx: Context): String? {
        app = ctx.applicationContext; load()
        val s = get("stage"); return if (s.isNotEmpty() && s != "done") s else null
    }

    private fun result(kind: String, what: String) {
        f("run-summary.txt").appendText("%-8s %s\n".format(kind, what)); changed()
    }
    private fun detail(text: String) { f("run-details.txt").appendText(text.trimEnd() + "\n"); changed() }
    private fun changed() { onChange?.invoke() }

    fun answer(a: String) { answers.offer(a) }

    /** Shows a prompt and blocks the run until a button is tapped. `manual` steps are skipped in auto (test) mode. */
    private fun ask(text: String, vararg buttons: String, manual: Boolean = false): String {
        answers.clear()
        if (auto) {
            val a = if (manual) "Skip" else buttons.first { it != OPEN_DEV }
            detail("  [auto] $text -> $a"); return a
        }
        promptText = text; promptButtons = buttons.toList(); changed()
        val a = answers.take()
        promptText = null; promptButtons = emptyList(); changed()
        return a
    }

    /** Shows a prompt without waiting (the run keeps working while it shows); cleared by [clearPrompt]. */
    private fun show(text: String, vararg buttons: String) { answers.clear(); promptText = text; promptButtons = buttons.toList(); changed() }
    private fun clearPrompt() { promptText = null; promptButtons = emptyList(); changed() }

    // ---------------------------------------------------------------- adb over the phone's own Wireless debugging

    private fun ensureAdb(): Boolean {
        if (AdbSelf.connected()) return true
        val h = get("adb_host"); val p = get("adb_port").toIntOrNull() ?: 0
        if (h.isNotEmpty() && p > 0 && !AdbSelf.connect(app, h, p).startsWith("CONNECT FAILED") && AdbSelf.connected()) return true
        // Every advertised address, newest first: mDNS can still hold a dead adbd's record beside the live one.
        val dead = get("adb_dead_port")
        for (c in Discovery.findAll(app, Discovery.CONNECT, 6).filter { it.second.toString() != dead }) {
            if (!AdbSelf.connect(app, c.first, c.second).startsWith("CONNECT FAILED") && AdbSelf.connected()) {
                put("adb_host", c.first); put("adb_port", c.second.toString()); return true
            }
        }
        return false
    }

    /** One shell-uid command over the phone's own adb; "" and exit -1 when adb is not reachable. */
    private fun sh(cmd: String): Pair<Int, String> {
        if (!ensureAdb()) return -1 to "adb not reachable"
        val (e, o) = AdbSelf.shell(cmd)
        return e to o.replace("\r", "")
    }
    private fun sh1(cmd: String) = sh(cmd).second.trim().replace('\n', ' ')

    // ---------------------------------------------------------------- start / resume

    fun start(ctx: Context, autoMode: Boolean) {
        app = ctx.applicationContext
        if (running) return
        f("run-summary.txt").delete(); f("run-details.txt").delete(); f("run-state.properties").delete()
        state.clear(); put("auto", if (autoMode) "1" else "0"); put("stage", "pair")
        result("INFO", "R4 phone-only run ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        launch("pair")
    }

    fun resume(ctx: Context) {
        app = ctx.applicationContext
        if (running) return
        load()
        val stage = get("stage")
        if (stage.isEmpty() || stage == "done") return
        launch(stage)
    }

    /** Stops a run that did not finish and puts everything back (the clean-up, then the helper's exit). */
    fun restoreNow(ctx: Context) {
        app = ctx.applicationContext
        if (running) return
        load()
        running = true
        Thread {
            try { stageCleanup(); endHelper(); put("stage", "done") } finally { running = false; changed() }
        }.start()
    }

    private fun launch(from: String) {
        running = true; changed()
        Thread {
            try {
                var i = STAGES.indexOf(from).coerceAtLeast(0)
                while (i < STAGES.size) {
                    val s = STAGES[i]
                    put("stage", s)
                    val go = when (s) {
                        "pair" -> stagePair()
                        "baseline" -> stageBaseline()
                        "p1" -> { stageP1(); true }
                        "p2" -> stageP2()
                        "p3" -> { stageP3(); true }
                        "p4a" -> { stageP4a(); return@Thread }   // the app is killed here; resume() continues at p4a-check
                        "p4a-check" -> { stageP4aCheck(); true }
                        "p4b" -> { stageP4b(); true }
                        "blur" -> { stageBlur(); true }
                        "p5" -> { stageP5(); true }
                        "pq2" -> { stagePq2(); true }
                        "cleanup" -> { stageCleanup(); true }
                        "p4d" -> { stageP4d(); true }
                        "done" -> { stageDone(); true }
                        else -> true
                    }
                    if (!go) { detail("stopped at stage $s"); stageCleanup(); endHelper(); put("stage", "done"); break }
                    i++
                }
            } catch (e: Throwable) {
                detail("RUN ERROR: ${e.javaClass.simpleName}: ${e.message}")
                result("FAIL", "the run stopped on an error (details); restoring")
                runCatching { stageCleanup() }; runCatching { endHelper() }; put("stage", "done")
            } finally { running = false; clearPrompt() }
        }.start()
    }

    // ---------------------------------------------------------------- pair

    private fun stagePair(): Boolean {
        if (Settings.Global.getInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 1) {
            val a = ask("Turn on Developer options first: Settings > About phone > Software information > tap Build number 7 times. Then come back and tap Done.", "Done", "Stop")
            if (a == "Stop") return false
        }
        // Paired before (the key is kept): Wireless debugging on is enough.
        if (ensureAdb() && sh1("id").contains("uid=2000")) {
            result("PASS", "adb on this phone (already paired): runs as the shell uid"); return true
        }
        show("Open Developer options > Wireless debugging: turn it ON (allow this network), then tap 'Pair device with pairing code'. " +
            "A notification from R4 probe appears: pull it down, tap 'Enter code', type the 6 digits.", OPEN_DEV, "Stop")
        AdbSelf.lastPair = null
        var posted: Pair<String, Int>? = null
        val end = System.currentTimeMillis() + 15 * 60_000
        while (System.currentTimeMillis() < end) {
            if (answers.poll() == "Stop") { clearPrompt(); return false }
            AdbSelf.lastPair?.let { lp ->
                if (lp.startsWith("paired")) break
                detail("pairing: $lp"); AdbSelf.lastPair = null   // wrong code: keep waiting for another try
            }
            val svc = Discovery.find(app, Discovery.PAIRING, 5)
            if (svc != null && svc != posted) { postPairNotification(svc.first, svc.second); posted = svc }
        }
        clearPrompt(); cancelPairNotification()
        if (AdbSelf.lastPair?.startsWith("paired") != true) { result("FAIL", "pairing did not happen within 15 minutes"); return false }
        detail("pairing: ${AdbSelf.lastPair}")
        if (!ensureAdb()) { result("FAIL", "paired, but could not connect to this phone's adb"); return false }
        val id = sh1("id")
        detail("adb id: $id")
        return if (id.contains("uid=2000")) { result("PASS", "paired with this phone's Wireless debugging; adb runs as the shell uid"); true }
        else { result("FAIL", "adb did not run as the shell uid ($id)"); false }
    }

    private fun postPairNotification(host: String, port: Int) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("r4pair", "R4 pairing", NotificationManager.IMPORTANCE_HIGH))
        val reply = PendingIntent.getBroadcast(
            app, 1, Intent(app, PairReceiver::class.java).putExtra("host", host).putExtra("port", port),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = Notification.Action.Builder(null, "Enter code", reply)
            .addRemoteInput(RemoteInput.Builder("code").setLabel("6-digit pairing code").build()).build()
        nm.notify(7, Notification.Builder(app, "r4pair").setSmallIcon(R.drawable.ic_r4probe_glyph)
            .setContentTitle("R4 probe: type the pairing code").setContentText("The 6 digits Wireless debugging shows")
            .addAction(action).setOngoing(true).build())
    }
    private fun cancelPairNotification() { runCatching { app.getSystemService(NotificationManager::class.java).cancel(7) } }

    // ---------------------------------------------------------------- baseline

    private val settingsKeys = listOf(
        "secure navigation_mode", "global adb_wifi_enabled", "global adb_enabled", "secure enabled_accessibility_services",
        "secure accessibility_enabled", "global low_power", "global low_power_sticky",
    )

    private fun stageBaseline(): Boolean {
        val sb = StringBuilder("== baseline\n")
        for (k in settingsKeys) { val v = sh1("settings get $k"); put("set.$k", v); sb.append("  $k = $v\n") }
        for (t in Toggles.ALL) {
            val r = sh1(t.read); put("tog.${t.name}", r)
            sb.append("  %-14s %-8s %s\n".format(t.name, if (t.probe != null) "probe" else t.state(r), r.take(80)))
        }
        detail(sb.toString())
        val list = Toggles.ALL.filter { it.probe == null }.joinToString("\n") { "  ${it.name}: ${it.state(get("tog.${it.name}"))}" }
        val a = ask("R4 will flip each of these and flip it straight back ('unknown' ones are left alone; the hotspot is only probed):\n$list\n" +
            "Wi-Fi and airplane mode cut Wireless debugging for a moment; R4 turns it back on. If a state above is wrong, tap Stop.",
            "Go ahead", "Stop")
        return a == "Go ahead"
    }

    // ---------------------------------------------------------------- parts 1-3

    private fun apk() = app.applicationInfo.sourceDir
    private fun helperCmd(args: String) = "CLASSPATH=${apk()} app_process /system/bin app.tessera.r4probe.HelperMain $args"

    private fun stageP1() {
        val o = sh(helperCmd("--probe")).second
        detail("== part 1: app_process --probe\n$o")
        if (Regex("(?m)^uid\\s+2000").containsMatchIn(o)) result("PASS", "1 app_process runs as the shell uid") else result("FAIL", "1 app_process runs as the shell uid")
    }

    private fun stageP2(): Boolean {
        sh("rm -f /data/local/tmp/r4helper-$TAG.pid /data/local/tmp/r4helper-$TAG.log")
        sh("CLASSPATH=${apk()} setsid nohup app_process /system/bin app.tessera.r4probe.HelperMain --daemon $TAG > /dev/null 2>&1 < /dev/null &")
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end && !HelperLink.connected()) Thread.sleep(500)
        val pid = sh1("cat /data/local/tmp/r4helper-$TAG.pid")
        put("helper_pid", pid)
        val ppid = sh1("ps -o PPID -p $pid | tail -1")
        detail("== part 2\n  helper pid $pid, parent $ppid\n" + sh("cat /data/local/tmp/r4helper-$TAG.log").second)
        if (ppid == "1") result("PASS", "2a the helper daemonises (parent = init)") else result("FAIL", "2a the helper daemonises (parent $ppid)")
        val ping = HelperLink.ping()
        detail("  ping: $ping")
        return if (ping.contains("uid=2000 pid=$pid ")) { result("PASS", "2b binder handed to the app; the ping answered"); true }
        else { result("FAIL", "2b binder handed to the app (ping: $ping)"); false }
    }

    /** Wi-Fi and airplane mode cut the run's own adb (Wireless debugging rides on Wi-Fi): only the helper flips them. */
    private val cutsAdb = setOf("wifi", "airplane")

    private fun stageP3() {
        sh("dumpsys battery unplug")
        val direct = StringBuilder("== part 3a: toggles directly over adb (shell uid)\n")
        var changedN = 0; var total = 0
        for (t in Toggles.ALL) {
            direct.append("  ${t.name}  [${t.forItem}]\n")
            if (t.probe != null) {
                val o = sh1(t.probe)
                direct.append("    probe   ${o.take(200)}\n    PROBE ${if ("SecurityException" in o) "REFUSED to the shell uid" else "reached (no state changed)"}\n"); continue
            }
            if (t.name in cutsAdb) { direct.append("    not flipped over adb: it would cut this run's own Wireless debugging (flipped through the helper)\n"); continue }
            total++
            val before = sh1(t.read); val st = t.state(before)
            direct.append("    before  $before  [$st]\n")
            if (st == "unknown") { direct.append("    UNRECOGNISED STATE: not flipped\n"); continue }
            val o1 = sh1(if (st == "on") t.off else t.on); Thread.sleep(3000)
            val mid = sh1(t.read); val o2 = sh1(if (st == "on") t.on else t.off); Thread.sleep(3000)
            val after = sh1(t.read)
            val sm = t.state(mid); val sa = t.state(after)
            if (sm != st) changedN++
            direct.append("    flip away: ${o1.ifEmpty { "(no output)" }}\n    read    $mid  [$sm]\n    back:   ${o2.ifEmpty { "(no output)" }}\n    after   $after  [$sa]\n")
            direct.append("    CHANGED ${if (sm != st) "yes" else "NO"} · RESTORED ${if (sa == st) "yes" else "NO"}\n")
        }
        detail(direct.toString())
        result("INFO", "3a toggles directly over adb: $changedN/$total changed")
        if ("RESTORED NO" in direct) result("FAIL", "3a a toggle was not restored at once (the clean-up retries it)")

        val viaHelper = StringBuilder("== part 3b: toggles through the helper\n")
        var hc = 0; var ht = 0
        for (t in Toggles.ALL) {
            viaHelper.append("  ${t.name}  [${t.forItem}]\n")
            if (t.probe != null) {
                val (e, o) = HelperLink.toggle(t.name, "probe")
                viaHelper.append("    probe   (exit $e) ${o.take(200)}\n    PROBE ${if ("SecurityException" in o) "REFUSED to the shell uid" else "reached (no state changed)"}\n"); continue
            }
            ht++
            val before = HelperLink.toggle(t.name, "read").second.trim(); val st = t.state(before)
            viaHelper.append("    before  $before  [$st]\n")
            if (st == "unknown") { viaHelper.append("    UNRECOGNISED STATE: not flipped\n"); continue }
            val (e1, o1) = HelperLink.toggle(t.name, if (st == "on") "off" else "on"); Thread.sleep(4000)
            val mid = HelperLink.toggle(t.name, "read").second.trim()
            val (e2, o2) = HelperLink.toggle(t.name, st); Thread.sleep(4000)
            val after = HelperLink.toggle(t.name, "read").second.trim()
            val sm = t.state(mid); val sa = t.state(after)
            if (sm != st) hc++
            viaHelper.append("    flip away (exit $e1) ${o1.take(120)}\n    read    $mid  [$sm]\n    back (exit $e2) ${o2.take(120)}\n    after   $after  [$sa]\n")
            viaHelper.append("    CHANGED ${if (sm != st) "yes" else "NO"} · RESTORED ${if (sa == st) "yes" else "NO"}\n")
            if (t.name in cutsAdb) viaHelper.append("    " + bringBackAdb() + "\n")
        }
        detail(viaHelper.toString())
        result("INFO", "3b toggles through the helper: $hc/$ht changed")
        if ("RESTORED NO" in viaHelper) result("FAIL", "3b a toggle was not restored at once (the clean-up retries it)")
        sh("dumpsys battery reset")
    }

    /**
     * After a Wi-Fi / airplane flip: Wi-Fi reconnects, the helper turns Wireless debugging back on (Android turned it off
     * with Wi-Fi), and adbd comes back on a new port, found again by mDNS. The dead connection is dropped once; then it
     * keeps asking for Wireless debugging and looking for adb for up to 3 minutes, and says how long it took.
     */
    private fun bringBackAdb(): String {
        val t0 = System.currentTimeMillis()
        put("adb_dead_port", get("adb_port"))   // the port adbd had before Wi-Fi dropped; its mDNS record lingers
        AdbSelf.disconnect(); put("adb_port", "0")
        var wd = ""
        val tries = StringBuilder()
        while (System.currentTimeMillis() - t0 < 180_000) {
            Thread.sleep(3000)
            wd = HelperLink.wirelessDebuggingOn()
            val ok = ensureAdb()
            tries.append("      ${(System.currentTimeMillis() - t0) / 1000}s: wd=$wd -> ${if (ok) "adb on port ${get("adb_port")}" else "not yet"}\n")
            if (ok) return "adb back after ${(System.currentTimeMillis() - t0) / 1000} s (Wireless debugging $wd, port ${get("adb_port")})"
        }
        detail(tries.toString())
        return "FAIL: adb NOT back within 3 minutes (Wireless debugging $wd) — steps after this that need adb will fail"
    }

    // ---------------------------------------------------------------- part 4

    private fun stageP4a() {
        put("stage", "p4a-check")
        detail("== part 4a: the app is killed on purpose (by a detached shell command) and restarted")
        sh("setsid sh -c 'sleep 2; am force-stop app.tessera.r4probe; sleep 4; am start -n app.tessera.r4probe/.ProbeActivity --es run resume' > /dev/null 2>&1 < /dev/null &")
        Thread.sleep(30_000)
        result("FAIL", "4a the app was not killed (still running 30 s later)")
        put("stage", "p4b"); launchLater("p4b")
    }
    private fun launchLater(from: String) { running = false; Thread { Thread.sleep(500); launch(from) }.start() }

    private fun stageP4aCheck() {
        val pid = get("helper_pid")
        val end = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < end && !HelperLink.connected()) Thread.sleep(500)
        val alive = sh1("ps -o PID -p $pid | tail -n +2") == pid
        val ping = HelperLink.ping()
        detail("  after the kill and restart: helper $pid alive: $alive; ping: $ping")
        if (alive && ping.contains("pid=$pid ")) result("PASS", "4a app killed: the helper lives and re-hands the binder")
        else result("FAIL", "4a app killed: the helper lives and re-hands the binder")
    }

    private fun stageP4b() {
        sh("dumpsys battery unplug")
        val doze = sh1("dumpsys deviceidle force-idle")
        detail("== part 4b: $doze")
        if ("Now forced in to deep idle mode" in doze) {
            Thread.sleep(20_000)
            val pid = get("helper_pid")
            val ok = sh1("ps -o PID -p $pid | tail -n +2") == pid && HelperLink.ping().contains("pid=$pid ")
            detail("  deep state: ${sh1("dumpsys deviceidle get deep")}; helper answered: $ok")
            result(if (ok) "PASS" else "FAIL", "4b forced deep Doze: the helper lives and answers")
        } else result("INFO", "4b deep Doze could NOT be forced here (not tested)")
        sh("dumpsys deviceidle unforce"); sh("dumpsys battery reset")
        result("INFO", "4c cable pulled: not applicable (no PC in a phone-only run)")
    }

    // ---------------------------------------------------------------- blur

    private fun stageBlur() {
        sh("appops set app.tessera.r4probe SYSTEM_ALERT_WINDOW allow")
        val sb = StringBuilder("== blur\n")
        sb.append("  ${sh1("getprop | grep -i blur")}\n")
        for ((label, mode) in listOf("normal" to "0", "powersave" to "1")) {
            sh("dumpsys battery unplug; cmd power set-mode $mode"); Thread.sleep(2000)
            val wm = app.getSystemService(WindowManager::class.java)
            sb.append("  -- $label: low_power=${sh1("settings get global low_power")}; isCrossWindowBlurEnabled=${wm.isCrossWindowBlurEnabled}; ${sh1("dumpsys window | grep -i -m2 blur")}\n")
            val veil = arrayOfNulls<TextView>(1)
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            main.post {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    PixelFormat.TRANSLUCENT,
                ).apply { blurBehindRadius = 80; gravity = Gravity.BOTTOM; height = 900 }
                val v = TextView(app).apply {
                    text = "BLUR PROBE ($label)"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
                    setBackgroundColor(0x33000000)
                }
                runCatching { wm.addView(v, params); veil[0] = v }.onFailure { sb.append("  overlay NOT added: ${it.message}\n") }
            }
            val a = ask("Look at the tinted panel at the bottom ($label pass). Is the text behind it blurred?", "Blurred", "Not blurred", "Can't tell", manual = true)
            main.post { veil[0]?.let { v -> runCatching { wm.removeView(v) } } }
            sb.append("  -- $label: Jeremy says: $a\n")
        }
        sb.append("  ${restoreSticky()}\n")
        sh("dumpsys battery reset")
        detail(sb.toString())
        result("INFO", "blur: the platform answer and Jeremy's look, normal and power saving (details)")
    }

    /** The owner's Power saving exactly as it was (review r3 R3-1): the mode set to their own value, read unplugged. */
    private fun restoreSticky(): String {
        val base = get("set.global low_power_sticky"); val low = get("set.global low_power")
        val want = if (base == "1" || low == "1") "1" else "0"
        sh("dumpsys battery unplug; cmd power set-mode $want"); Thread.sleep(1000)
        val lp = sh1("settings get global low_power")
        if (base == "null" || base.isEmpty()) sh("settings delete global low_power_sticky") else sh("settings put global low_power_sticky $base")
        val st = sh1("settings get global low_power_sticky")
        sh("dumpsys battery reset")
        return if (lp == want && (st == base || (base.isEmpty() && st == "null"))) "power saving: as it was (on when unplugged: $want; low_power_sticky $st)"
        else "FAIL: power saving is not as it was (unplugged it reads $lp, it should be $want; low_power_sticky $st, it was $base) — set Power saving by hand"
    }

    // ---------------------------------------------------------------- P5

    private val a11yId = "app.tessera.r4probe/app.tessera.r4probe.ProbeA11yService"

    private fun stageP5() {
        val base = get("set.secure enabled_accessibility_services")
        put("a11y_touched", "1")
        sh(if (base == "null" || base.isEmpty()) "settings put secure enabled_accessibility_services $a11yId"
           else "settings put secure enabled_accessibility_services '$base:$a11yId'")
        sh("settings put secure accessibility_enabled 1")
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end && ProbeA11yService.instance == null) Thread.sleep(500)
        val sb = StringBuilder("== P5: accessibility service ${if (ProbeA11yService.instance != null) "connected" else "NOT connected"}\n")
        fun pass(label: String) {
            for (kind in listOf("a11y", "app")) {
                val u = ui ?: run { sb.append("  $label $kind: the app screen is not open\n"); return }
                u.runOnUiThread { u.startOverlay(kind) }
                val until = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < until && u.overlayText(kind)?.contains("WINDOW UP") != true && u.overlayText(kind)?.contains("OVERLAY DONE") != true) Thread.sleep(300)
                val size = sh1("wm size").substringAfterLast(": ").trim(); val w = size.substringBefore("x").toIntOrNull() ?: 1080; val h = size.substringAfter("x").toIntOrNull() ?: 2340
                sh("input tap ${w / 2} ${h - 12}"); Thread.sleep(700); sh("input tap ${w / 2} ${h / 2}")
                val done = System.currentTimeMillis() + 25_000
                while (System.currentTimeMillis() < done && u.overlayText(kind)?.contains("OVERLAY DONE") != true) Thread.sleep(500)
                sb.append("-- $label $kind (navigation_mode=${sh1("settings get secure navigation_mode")}; taps at y=${h - 12} and y=${h / 2})\n")
                sb.append(u.overlayText(kind) ?: "  (no result)").append("\n")
            }
        }
        pass("nav-${sh1("settings get secure navigation_mode")}")
        if (ask("Switch the navigation type (Settings > Display > Navigation bar: Swipe gestures <-> Buttons), come back here, and tap Done.", "Done", "Skip", manual = true) == "Done") {
            pass("nav-${sh1("settings get secure navigation_mode")}")
            ask("Switch the navigation type back to what you use, come back, and tap Done.", "Done", manual = true)
        }
        restoreA11y()
        detail(sb.toString())
        result("INFO", "P5 nav-bar overlay: accessibility + app overlay, taps received (details)")
    }

    private fun restoreA11y(): String {
        if (get("a11y_touched") != "1") return ""
        val base = get("set.secure enabled_accessibility_services"); val on = get("set.secure accessibility_enabled")
        if (base == "null" || base.isEmpty()) sh("settings delete secure enabled_accessibility_services") else sh("settings put secure enabled_accessibility_services '$base'")
        if (on == "null" || on.isEmpty()) sh("settings delete secure accessibility_enabled") else sh("settings put secure accessibility_enabled $on")
        val now = sh1("settings get secure enabled_accessibility_services")
        return if (now == base) "accessibility services: as they were" else "FAIL: accessibility services are NOT as they were — check Settings > Accessibility > Installed apps"
    }

    // ---------------------------------------------------------------- PQ2

    private fun stagePq2() {
        val sb = StringBuilder("== PQ2 visual voicemail (what the shell can see; Jeremy 2026-09-28 \"A\": observe only, no sign-in)\n")
        sb.append("  sim operator: ${sh1("getprop gsm.sim.operator.alpha")} · network: ${sh1("getprop gsm.operator.alpha")}\n")
        sb.append("  -- carrier config (voicemail / VVM / GBA keys)\n")
        sb.append(sh("dumpsys carrier_config 2>&1 | grep -i -E 'vvm|visual_voicemail|voicemail|gba' | sed 's/^ *//' | sort -u | head -60").second.trimEnd()).append("\n")
        sb.append("  -- apps that could handle visual voicemail\n")
        sb.append(sh("pm list packages 2>&1 | grep -i -E 'vvm|voicemail|visualvoice|tmobile|t-mobile|digits'").second.trimEnd()).append("\n")
        sb.append("  -- the default dialer: ${sh1("cmd role get-role-holders android.app.role.DIALER")}\n")
        val vq = sh("content query --uri content://com.android.voicemail/voicemail --projection source_package 2>&1").second
        val counts = Regex("source_package=([^,\\s]*)").findAll(vq).map { it.groupValues[1] }.groupingBy { it }.eachCount()
        sb.append("  -- the system voicemail provider: rows per source app (counts only): ${if (counts.isEmpty()) "no rows (${vq.lines().firstOrNull()?.take(80)})" else counts}\n")
        sb.append("  -- what the shell uid holds\n")
        sb.append(sh("dumpsys package com.android.shell 2>&1 | grep -E 'MODIFY_PHONE_STATE|READ_VOICEMAIL|ADD_VOICEMAIL|READ_PRIVILEGED_PHONE_STATE' | sed 's/^ *//' | sort -u").second.trimEnd()).append("\n")
        detail(sb.toString())
        result("INFO", "PQ2 visual voicemail: carrier config, handler app, provider counts (no sign-in)")
    }

    // ---------------------------------------------------------------- clean-up, 4d, end

    private fun stageCleanup() {
        val sb = StringBuilder("== clean-up\n")
        if (!ensureAdb()) {
            sb.append("  FAIL: adb is not reachable, so settings could not be restored. Turn Wireless debugging on, open R4 probe, and tap 'Restore now';\n")
            sb.append("  or reboot the phone and check Wi-Fi, Bluetooth, NFC, location, mobile data, airplane mode, automatic date & time, Power saving,\n")
            sb.append("  the navigation type and Settings > Accessibility by hand.\n")
        } else {
            sh("dumpsys deviceidle unforce"); sh("dumpsys battery reset")
            restoreA11y().takeIf { it.isNotEmpty() }?.let { sb.append("  $it\n") }
            for (t in Toggles.ALL) {
                if (t.probe != null) continue
                val want = t.state(get("tog.${t.name}"))
                if (want == "unknown") { sb.append("  ${t.name}: its start state was not recognised; never flipped, left alone\n"); continue }
                val now = t.state(if (t.name in cutsAdb || !HelperLink.connected()) sh1(t.read) else HelperLink.toggle(t.name, "read").second.trim())
                if (now == want) { sb.append("  ${t.name}: as it was ($want)\n"); continue }
                if (HelperLink.connected()) HelperLink.toggle(t.name, want) else sh(if (want == "on") t.on else t.off)
                Thread.sleep(4000)
                if (t.name in cutsAdb) bringBackAdb()
                val again = t.state(if (HelperLink.connected()) HelperLink.toggle(t.name, "read").second.trim() else sh1(t.read))
                sb.append(if (again == want) "  ${t.name}: had been left changed; set back to $want\n" else "  FAIL: ${t.name} could NOT be set back to $want (now $again) — set it by hand\n")
            }
            sb.append("  ${restoreSticky()}\n")
            val nav = sh1("settings get secure navigation_mode")
            if (nav != get("set.secure navigation_mode")) sb.append("  NOTE: the navigation type is $nav, it was ${get("set.secure navigation_mode")} — switch it back in Settings > Display > Navigation bar\n")
        }
        detail(sb.toString())
        if ("FAIL" in sb) result("FAIL", "clean-up: something could not be set back (details)") else result("PASS", "clean-up: every change undone")
    }

    private fun stageP4d() {
        val before = sh1("pidof adbd")
        put("adbd_before", before)
        val a = ask("Last step (4d): open Developer options, turn Wireless debugging OFF, then turn USB debugging OFF. Wait 30 seconds, come back here and tap Done.",
            OPEN_DEV, "Done", "Skip", manual = true)
        if (a != "Done") { result("SKIPPED", "4d adbd gone"); return }
        Thread.sleep(2000)
        val (e, after) = HelperLink.adbdPid()
        val ping = HelperLink.ping()
        val pid = get("helper_pid")
        detail("== part 4d: adbd pid before $before, after '${after}' (exit $e); ping: $ping")
        when {
            !ping.contains("pid=$pid ") -> result("FAIL", "4d adbd gone: the helper did NOT answer")
            after.isNotEmpty() && after == before -> result("INFO", "4d NOT tested: adbd never stopped (same pid) — were both debugging switches off?")
            else -> result("PASS", "4d adbd gone (${if (after.isEmpty()) "not running" else "new pid"}): the helper lives and answers")
        }
        val usb = get("set.global adb_enabled")
        if (usb == "1") ask("USB debugging was on before the run: turn it back on if you want it (Developer options). Tap Done.", OPEN_DEV, "Done", manual = true)
    }

    private fun endHelper() {
        if (!HelperLink.connected()) return
        detail("  helper: ${HelperLink.exit()}")
        Thread.sleep(1500)
        detail("  helper binder alive after exit: ${HelperLink.binderAlive()}")
    }

    private fun stageDone() {
        endHelper()
        result("INFO", "done: tap 'Copy the whole report' and paste it back; then uninstall R4 probe, and in Wireless debugging > Paired devices tap R4 probe > Forget (the app cannot remove its own pairing)")
    }
}
