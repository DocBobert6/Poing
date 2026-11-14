package app.poing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    // Performance fix 4 — the cartridge's sensor need, read
    // from the ROM header (same bytes the core uses).
    @get:Rule
    val temp = TemporaryFolder()

    private fun romFile(bytes: ByteArray): File {
        val f = temp.newFile()
        f.writeBytes(bytes)
        return f
    }

    private fun gbHeader(cartType: Int): ByteArray {
        val bytes = ByteArray(512)
        bytes[0x147] = cartType.toByte()
        return bytes
    }

    private fun gbaHeader(code: String): ByteArray {
        val bytes = ByteArray(512)
        code.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0xAC)
        return bytes
    }

    @Test
    fun mbc7NeedsTiltButMbc3RtcDoesNot() {
        // 0x22 is MBC7 (Kirby Tilt 'n' Tumble, the only GB
        // tilt cart) — tilt, nothing else.
        assertEquals(
            SensorNeed(tilt = true, gyro = false, light = false),
            cartridgeSensorNeed(romFile(gbHeader(0x22)), GameSystem.GB),
        )
        // 0x0F is MBC3+RTC (Pokemon Gold/Silver) — not tilt.
        // (The trap: 0x0F looks like a tilt byte if the MBC
        // table is misread; the core maps it to MBC3+RTC.)
        assertEquals(
            SensorNeed.NONE,
            cartridgeSensorNeed(romFile(gbHeader(0x0F)), GameSystem.GB),
        )
        // A plain MBC5 cart needs nothing either.
        assertEquals(
            SensorNeed.NONE,
            cartridgeSensorNeed(romFile(gbHeader(0x19)), GameSystem.GBC),
        )
    }

    @Test
    fun gbaTiltAndGyroCodesMatchTheCoreTable() {
        // Yoshi's Universal Gravitation (all regions) — tilt.
        for (code in listOf("KYGJ", "KYGE", "KYGP")) {
            assertEquals(
                SensorNeed(tilt = true, gyro = false, light = false),
                cartridgeSensorNeed(romFile(gbaHeader(code)), GameSystem.GBA),
            )
        }
        // Korokoro Puzzle Happy Panechu — tilt.
        assertEquals(
            SensorNeed(tilt = true, gyro = false, light = false),
            cartridgeSensorNeed(romFile(gbaHeader("KHPJ")), GameSystem.GBA),
        )
        // WarioWare: Twisted! (all regions) — gyro.
        for (code in listOf("RZWJ", "RZWE", "RZWP")) {
            assertEquals(
                SensorNeed(tilt = false, gyro = true, light = false),
                cartridgeSensorNeed(romFile(gbaHeader(code)), GameSystem.GBA),
            )
        }
        // Boktai needs no motion sensor (its solar sensor is
        // decided by the option, not the cartridge).
        assertEquals(
            SensorNeed.NONE,
            cartridgeSensorNeed(romFile(gbaHeader("U3IE")), GameSystem.GBA),
        )
        // An ordinary game (and an unknown code) needs nothing.
        assertEquals(
            SensorNeed.NONE,
            cartridgeSensorNeed(romFile(gbaHeader("AXVE")), GameSystem.GBA),
        )
    }

    @Test
    fun unreadableHeaderMeansRegisterEverything() {
        val short = temp.newFile()
        short.writeBytes(ByteArray(100))
        assertNull(cartridgeSensorNeed(short, GameSystem.GB))
        assertNull(cartridgeSensorNeed(File(temp.root, "missing.gb"), GameSystem.GBA))
    }
}
