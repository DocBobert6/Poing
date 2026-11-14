package app.poing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.io.File

/**
 * Phase 6 (item 10a) — the cartridge sensors: the phone's
 * hardware feeding the core's libretro sensor interface. The
 * tilt games (WarioWare: Twisted!, Yoshi's Universal
 * Gravitation) read the accelerometer + gyroscope, and the
 * Boktai solar sensor reads the light sensor when the
 * mgba_solar_sensor_level option is "sensor".
 *
 * The listeners run while a game is playing (started and
 * stopped with the session) and push every reading to the
 * bridge (nativeSetSensorValue); the core only reads the
 * sensors it enabled at load, and a sensor it never enabled
 * reads zero (see sensors.c). No permission is needed — these
 * are the phone's ordinary motion/light sensors.
 *
 * Units are the libretro ones, straight from the phone with no
 * conversion: the accelerometer in m/s^2, the gyroscope in
 * rad/s, the light sensor in lux. The core does its own
 * scaling (tilt x3e8, gyro x-5.5e8, lux cbrtf(x)*8).
 * SENSOR_DELAY_GAME (~50 Hz) is close to the rate the core
 * asks for (60) without the chatter of SENSOR_DELAY_FASTEST.
 *
 * The sensor ids are the libretro ones (sensors.h): 0/1/2
 * accelerometer X/Y/Z, 3/4/5 gyroscope X/Y/Z, 6 illuminance.
 */
class CartridgeSensors(context: Context) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val light: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** True once [start] has registered the listeners. */
    var started = false
        private set

    /**
     * Set while the game is paused (see GameScreen): in-flight
     * sensor events landing after stop() unregistered the
     * listeners are dropped here, not pushed to the core.
     */
    var paused: Boolean = false

    /**
     * Phase 8 — Screen Optics: an optional lux callback for
     * the ambient light sensor. The light sensor already
     * feeds the core (id 6) while a game is playing; this
     * hands the same reading to the display chain, so no
     * second listener is registered. Null when the player
     * is not listening (the default).
     */
    var onLux: ((Float) -> Unit)? = null

    /**
     * Start feeding sensor values to the bridge. A phone without
     * a sensor simply never pushes that sensor's values (the
     * core reads zero for it). Only the sensors in [need] are
     * registered — the rest would only wake the core's readers
     * for values no cartridge can consume (see
     * [cartridgeSensorNeed]). Idempotent; safe to call before
     * the native libraries load — the push is then a no-op that
     * the bridge's ensureLoaded guard swallows (see [push]).
     */
    fun start(need: SensorNeed = SensorNeed.ALL) {
        if (started) return
        if (need.tilt) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        if (need.gyro) {
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        if (need.light) {
            light?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        started = true
    }

    /** Stop feeding sensor values. Idempotent. */
    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(this)
        started = false
    }

    /**
     * Phase 6 (item 12) — push zero for every sensor id (0–6):
     * when the Cartridge Surgery panel switches the sensors off,
     * the core must read flat (no tilt, no light), not the last
     * pushed values frozen in place. A sensor the core never
     * enabled reads zero anyway; this only matters for enabled
     * ones. Safe to call before the native libraries load.
     */
    fun zero() {
        for (id in 0..6) push(id, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Dropped while paused: the listeners are stopped on
        // pause, but an event already in flight can still land.
        if (paused) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                push(0, event.values.getOrElse(0) { 0f })
                push(1, event.values.getOrElse(1) { 0f })
                push(2, event.values.getOrElse(2) { 0f })
            }
            Sensor.TYPE_GYROSCOPE -> {
                push(3, event.values.getOrElse(0) { 0f })
                push(4, event.values.getOrElse(1) { 0f })
                push(5, event.values.getOrElse(2) { 0f })
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values.getOrElse(0) { 0f }
                push(6, lux)
                onLux?.invoke(lux)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy changes do not affect the values pushed.
    }

    private fun push(id: Int, value: Float) {
        if (!Bridge.ensureLoaded()) return
        try {
            Bridge.nativeSetSensorValue(id, value)
        } catch (_: UnsatisfiedLinkError) {
            // The native libraries are gone (the game quit while
            // a sensor event was in flight); the value is dropped.
        }
    }
}

/**
 * Which of the phone's sensors this session needs. ALL is
 * today's behaviour (everything registered); a session whose
 * cartridge has no sensor hardware registers none.
 */
data class SensorNeed(val tilt: Boolean, val gyro: Boolean, val light: Boolean) {
    companion object {
        val ALL = SensorNeed(tilt = true, gyro = true, light = true)
        val NONE = SensorNeed(tilt = false, gyro = false, light = false)
    }
}

/**
 * The motion-sensor need of the cartridge in [romFile], read
 * from the ROM header — the same bytes the core's own
 * detection uses, so the gate can never disagree with the
 * core about a game:
 * - GB/GBC: the cartridge-type byte at 0x147. MBC7 (0x22) is
 *   the only GB mapper with tilt hardware (Kirby Tilt 'n'
 *   Tumble); 0x0F is MBC3+RTC, not tilt (mbc.c). No GB
 *   cartridge has a gyroscope or a solar sensor.
 * - GBA: the 4-letter game code at 0xAC, looked up in the
 *   core's own overrides table (overrides.c) — the core
 *   enables tilt/gyro/light hardware ONLY for the codes in
 *   that table, with no fallback for unknown codes, so this
 *   lookup matches it exactly, including for homebrew (no
 *   hardware on either side). Tilt (accelerometer): KHPJ
 *   (Korokoro Puzzle Happy Panechu), KYGJ/KYGE/KYGP (Yoshi's
 *   Universal Gravitation). Gyro: RZWJ/RZWE/RZWP (WarioWare:
 *   Twisted!). (The Boktai solar codes — the U3I, U32 and
 *   U33J families — are the same table, but light is decided
 *   by the caller from the solar option and Screen Optics,
 *   not here.)
 * The light flag is always false in the result: light is
 * decided by the caller (see GameScreen.sensorNeed), not by
 * the cartridge. Null when the header cannot be read — the
 * caller registers everything then, so an unreadable header
 * can never break a tilt game.
 *
 * Verification note (2026-11-14): the core's sensor
 * enablement itself cannot be the gate — _initSensors
 * (libretro.c) enables all three families unconditionally at
 * load, and the libretro frontend attaches the rotation
 * source for every game (libretro.c:2051), so GBA tilt games
 * DO consume the accelerometer through it. The cartridge
 * hardware is the only signal that discriminates, and this
 * is it.
 */
fun cartridgeSensorNeed(romFile: File, system: GameSystem): SensorNeed? {
    val header = ByteArray(HEADER_BYTES)
    val read = try {
        romFile.inputStream().use { input ->
            var offset = 0
            while (offset < HEADER_BYTES) {
                val n = input.read(header, offset, HEADER_BYTES - offset)
                if (n < 0) break
                offset += n
            }
            offset
        }
    } catch (_: Exception) {
        return null
    }
    if (read < HEADER_BYTES) return null
    return when (system) {
        GameSystem.GB, GameSystem.GBC ->
            SensorNeed(tilt = header[GB_CART_TYPE_OFFSET] == GB_MBC7_TYPE, gyro = false, light = false)
        GameSystem.GBA -> {
            val code = String(header, GBA_GAME_CODE_OFFSET, 4, Charsets.US_ASCII)
            SensorNeed(tilt = code in TILT_CODES, gyro = code in GYRO_CODES, light = false)
        }
    }
}

/** The header bytes cartridgeSensorNeed reads (covers 0x147 and 0xAC). */
private const val HEADER_BYTES = 512

/** GB/GBC cartridge-type byte: MBC7 (tilt) is 0x22 (mbc.c). */
private const val GB_CART_TYPE_OFFSET = 0x147
private const val GB_MBC7_TYPE: Byte = 0x22

/** GBA game-code offset (overrides.c looks the code up there). */
private const val GBA_GAME_CODE_OFFSET = 0xAC

/** GBA tilt (accelerometer) games, from the core's overrides table. */
private val TILT_CODES = setOf("KHPJ", "KYGJ", "KYGE", "KYGP")

/** GBA gyro games (WarioWare: Twisted!), from the core's overrides table. */
private val GYRO_CODES = setOf("RZWJ", "RZWE", "RZWP")
