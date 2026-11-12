#ifndef POING_SENSORS_H
#define POING_SENSORS_H

#include <stdbool.h>
#include <stdint.h>

#include "libretro.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Phase 6 (item 10a) — the cartridge sensor values: the tilt /
 * gyroscope / light inputs the core reads through the libretro
 * sensor interface.
 *
 * Pure C — no JNI, no Android APIs — so the logic runs under the
 * host test (bridge/tests/sensor_test.c). The bridge owns the
 * libretro wiring (bridge.cpp): it answers
 * RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE with the two callbacks
 * here, and the Kotlin side (CartridgeSensors) pushes the
 * phone's sensor values in through [sensor_set_value].
 *
 * Units are the libretro ones, straight from the phone with no
 * conversion: the accelerometer in m/s^2, the gyroscope in rad/s,
 * the light sensor in lux. The core does its own scaling
 * (tilt x3e8, gyro x-5.5e8, lux cbrtf(x)*8 — libretro.c
 * _updateRotation / _updateLux in the pinned tree).
 *
 * Sensor ids (libretro.h): 0 accelerometer X, 1 Y, 2 Z,
 * 3 gyroscope X, 4 Y, 5 Z, 6 illuminance. Actions
 * (libretro.h): 0 accelerometer enable, 1 disable,
 * 2 gyroscope enable, 3 disable, 4 illuminance enable,
 * 5 disable. The core enables each sensor it needs at load
 * (_initSensors, rate 60) and only reads the enabled ones —
 * but [sensor_get_input] also returns 0 for a disabled sensor,
 * so a stray read can never see a stale value.
 */

/** The latest value pushed for a sensor id (0 when none was ever pushed, or the sensor is disabled). */
float sensor_get_input(unsigned port, unsigned id);

/** Enable or disable one sensor family (see the actions above). Returns false for an unknown action. */
bool sensor_set_state(unsigned port, enum retro_sensor_action action, unsigned rate);

/** Push one sensor value from the Kotlin side (an id above, 0..6). Unknown ids are ignored. */
void sensor_set_value(unsigned id, float value);

/** Forget every value and disable every sensor (game unload). */
void sensor_reset(void);

#ifdef __cplusplus
}
#endif

#endif // POING_SENSORS_H
