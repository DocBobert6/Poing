package app.poing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 10a) — the cartridge sensors' lifecycle: start
 * registers the listeners, stop unregisters them, and both are
 * idempotent. The value flow itself (accelerometer / gyroscope /
 * light readings into the bridge) is proven by the host test
 * (bridge/tests/sensor_test.c) and by a tilt game on device —
 * under Robolectric there is no .so to push values into, so the
 * push is a guarded no-op.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CartridgeSensorsTest {

    private fun sensors() =
        CartridgeSensors(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun startAndStopToggleTheFlag() {
        val s = sensors()
        assertFalse(s.started)
        s.start()
        assertTrue(s.started)
        s.stop()
        assertFalse(s.started)
    }

    @Test
    fun startAndStopAreIdempotent() {
        val s = sensors()
        s.start()
        s.start()
        assertTrue(s.started)
        s.stop()
        s.stop()
        assertFalse(s.started)
        // Stopping before starting is a no-op, not a crash.
        val fresh = sensors()
        fresh.stop()
        assertFalse(fresh.started)
    }
}
