package app.tileshell.applist

/** Caption state of one package in one profile. */
enum class NewState { NEW, SEEN }

data class InstallRecord(val firstInstallTime: Long, val state: NewState) {
    fun encode(): String = "$firstInstallTime:${if (state == NewState.NEW) "N" else "S"}"

    companion object {
        fun decode(value: String?): InstallRecord? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 2) return null
            val time = parts[0].toLongOrNull() ?: return null
            return InstallRecord(time, if (parts[1] == "N") NewState.NEW else NewState.SEEN)
        }
    }
}

/**
 * The accent "New" caption rules (phase 01 Decisions, R6 §5.1.1 MEDIUM; approximations X11 and X14). Pure, JVM-tested.
 *
 * - System apps (FLAG_SYSTEM) never carry it (R6 §5.1.1).
 * - Apps already installed when the shell first became Home never carry it (X14): the baseline marks them SEEN,
 *   and any app first seen later whose install time is not after the baseline is SEEN too (hidden-profile apps).
 * - An update (`adb install -r`) keeps the first install time, so the record and its state are kept (X14).
 * - A reinstall (new first install time) is a new install.
 * - It clears the first time the app is launched, from the shell or seen through UsageStats (X11). No timer (R6 §5.2.1).
 */
object NewAppRules {
    fun key(packageName: String, userSerial: Long): String = "$packageName#$userSerial"

    fun serialOf(key: String): Long? = key.substringAfterLast('#', "").toLongOrNull()

    fun packageOf(key: String): String = key.substringBeforeLast('#')

    /** Record for an app present when the baseline is taken (the shell first became Home). */
    fun atBaseline(firstInstallTime: Long): InstallRecord = InstallRecord(firstInstallTime, NewState.SEEN)

    /** Record for an app seen after the baseline. */
    fun decide(existing: InstallRecord?, isSystem: Boolean, firstInstallTime: Long, baselineMs: Long): InstallRecord {
        if (isSystem) return InstallRecord(firstInstallTime, NewState.SEEN)
        if (existing != null && existing.firstInstallTime == firstInstallTime) return existing
        val fresh = firstInstallTime > 0 && firstInstallTime > baselineMs
        return InstallRecord(firstInstallTime, if (fresh) NewState.NEW else NewState.SEEN)
    }

    fun afterLaunch(existing: InstallRecord): InstallRecord = existing.copy(state = NewState.SEEN)

    /** An ACTIVITY_RESUMED usage event of the package clears the caption when it happened at or after the install. */
    fun clearedByUsage(record: InstallRecord, resumedAtMs: Long): Boolean =
        record.state == NewState.NEW && resumedAtMs >= record.firstInstallTime
}
