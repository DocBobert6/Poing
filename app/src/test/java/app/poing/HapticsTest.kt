package app.poing

import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 7) — haptics, light version only: one short click.
 * The Vibrator is the Robolectric one, so the shadow records what
 * was vibrated.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticsTest {

    private fun vibrator(): Vibrator =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Vibrator::class.java)

    @Test
    fun clickVibratesOnce() {
        val v = vibrator()
        Haptics(v).click()
        assertEquals(1, shadowOf(v).vibrations.size)
    }

    @Test
    fun disabledClickDoesNothing() {
        val v = vibrator()
        Haptics(v, enabled = false).click()
        assertEquals(0, shadowOf(v).vibrations.size)
    }

    @Test
    fun nullVibratorIsASilentNoOp() {
        // A device without a vibrator must never crash.
        Haptics(null).click()
    }
}
