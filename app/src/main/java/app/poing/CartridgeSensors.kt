package app.poing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

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
     * core reads zero for it). Idempotent; safe to call before
     * the native libraries load — the push is then a no-op that
     * the bridge's ensureLoaded guard swallows (see [push]).
     */
    fun start() {
        if (started) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        light?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
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
