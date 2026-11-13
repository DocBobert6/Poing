package app.poing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Top bar / back swipe (Robert, 2026-11-12): the double-back
 * window as a pure decision — a press exits only if the
 * previous press is inside DOUBLE_BACK_EXIT_WINDOW_MS (2s).
 * Pinned here, not in the compose tests: the compose test
 * clock does not drive System.currentTimeMillis, so a compose
 * test cannot prove the window resets — the window is the
 * pure function's job, the hint and the exit are the compose
 * tests' job.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DoubleBackExitTest {

    @Test
    fun firstPressNeverExits() {
        // lastPressMs 0 (no press yet) against any now.
        assertFalse(doubleBackExits(1_000L, 0L))
        assertFalse(doubleBackExits(100_000L, 0L))
    }

    @Test
    fun secondPressInsideWindowExits() {
        assertTrue(doubleBackExits(1_500L, 1_000L))
        assertTrue(doubleBackExits(1_999L, 0L))
    }

    @Test
    fun secondPressAtOrAfterWindowDoesNotExit() {
        // Exactly at the window edge: not inside it.
        assertFalse(doubleBackExits(2_000L, 0L))
        assertFalse(doubleBackExits(2_500L, 0L))
        assertFalse(doubleBackExits(100_000L, 0L))
    }
}
