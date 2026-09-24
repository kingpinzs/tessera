package app.tileshell.calculator

import android.content.ContextWrapper
import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine objects behind the Calculator are not observable, so [CalcModel] must make every READ of them a read
 * of Compose state: a composable that draws only engine state (the angle row's DEG / RAD / GRAD, HYP, MC / MR enabled)
 * is re-run when, and only when, a state object it read was changed. The test uses the mechanism Compose invalidates
 * by — the snapshot read observer while the scope runs, the apply notification after a change — so a scope that reads
 * `model.calc.angleUnit` and nothing else is proven to be invalidated by the key press that changes it.
 * (E11 run 1 on the device: the angle label stayed "DEG" and MR stayed disabled after MS.)
 */
class CalcModelObservableTest {

    private fun model(): CalcModel {
        val dir: File = Files.createTempDirectory("calcmodel").toFile()
        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = dir
        }
        return CalcModel(CalcStore(context), LocalDate.of(2026, 9, 23))
    }

    /** True when a scope that ran [read] would be invalidated by [change]. */
    private fun invalidates(read: () -> Unit, change: () -> Unit): Boolean {
        val reads = mutableSetOf<Any>()
        val snapshot = Snapshot.takeSnapshot(readObserver = { reads.add(it) })
        try { snapshot.enter(read) } finally { snapshot.dispose() }
        val changed = mutableSetOf<Any>()
        val handle = Snapshot.registerApplyObserver { set, _ -> changed.addAll(set) }
        try {
            change()
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        return reads.any { it in changed }
    }

    @Test fun `a scope reading only the angle unit is invalidated by the angle key`() {
        val m = model()
        m.showPage(CalcPage.SCIENTIFIC)
        assertTrue(invalidates({ m.calc.angleUnit }, { m.press("angle") }))
    }

    @Test fun `a scope reading only HYP is invalidated by the HYP key`() {
        val m = model()
        m.showPage(CalcPage.SCIENTIFIC)
        assertTrue(invalidates({ m.calc.hyp }, { m.press("hyp") }))
    }

    @Test fun `a scope reading only MR's enabled state is invalidated by MS`() {
        val m = model()
        m.press("5")
        assertTrue(invalidates({ m.calc.isEnabled("mr") }, { m.press("ms") }))
    }

    @Test fun `a scope reading only the converter's value is invalidated by a converter key`() {
        val m = model()
        m.showPage(CalcPage.CONVERTER)
        assertTrue(invalidates({ m.converter.value1 }, { m.converterPress("7") }))
    }

    @Test fun `a scope reading only the date engine is invalidated by a date change`() {
        val m = model()
        assertTrue(invalidates({ m.date.toDate }, { m.setDateTo(LocalDate.of(2027, 1, 1)) }))
    }
}
