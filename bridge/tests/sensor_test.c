/* Phase 6 (item 10a) — host test for the cartridge sensor store
 * (bridge/jni/sensors.c): the enable mask, the value store, and
 * the disabled-reads-zero rule. Pure C, no Android, no core:
 *
 *   gcc -std=c99 -I../../src/platform/libretro -I../jni \
 *       sensor_test.c ../jni/sensors.c -o sensor_test && ./sensor_test
 *
 * (The ids and actions are the libretro.h ones: accelerometer
 * X/Y/Z 0/1/2, gyroscope X/Y/Z 3/4/5, illuminance 6; the enable
 * actions come in per-family pairs.)
 */
#include <stdio.h>
#include <stdlib.h>

#include "libretro.h"
#include "sensors.h"

static int failures = 0;

#define CHECK(cond) do { \
    if (!(cond)) { \
        printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
        failures++; \
    } \
} while (0)

#define CHECK_FLOAT(a, b) do { \
    float _a = (a), _b = (b); \
    float _d = _a - _b; \
    if (_d < 0) _d = -_d; \
    if (_d > 0.0001f) { \
        printf("FAIL %s:%d: %s (%f != %f)\n", __FILE__, __LINE__, #a, (double)_a, (double)_b); \
        failures++; \
    } \
} while (0)

int main(void) {
    /* Nothing enabled yet: every read is zero, even with values pushed. */
    sensor_set_value(RETRO_SENSOR_ACCELEROMETER_X, 9.8f);
    sensor_set_value(RETRO_SENSOR_ILLUMINANCE, 1000.0f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ACCELEROMETER_X), 0.0f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ILLUMINANCE), 0.0f);

    /* Enable the accelerometer family: all three axes read. */
    CHECK(sensor_set_state(0, RETRO_SENSOR_ACCELEROMETER_ENABLE, 60) == true);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ACCELEROMETER_X), 9.8f);
    sensor_set_value(RETRO_SENSOR_ACCELEROMETER_Y, -3.5f);
    sensor_set_value(RETRO_SENSOR_ACCELEROMETER_Z, 0.25f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ACCELEROMETER_Y), -3.5f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ACCELEROMETER_Z), 0.25f);
    /* The gyroscope is still disabled. */
    sensor_set_value(RETRO_SENSOR_GYROSCOPE_Z, 1.5f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_GYROSCOPE_Z), 0.0f);

    /* Enable the gyroscope: Z reads now. */
    CHECK(sensor_set_state(0, RETRO_SENSOR_GYROSCOPE_ENABLE, 60) == true);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_GYROSCOPE_Z), 1.5f);

    /* Enable illuminance: the pushed lux reads. */
    CHECK(sensor_set_state(0, RETRO_SENSOR_ILLUMINANCE_ENABLE, 60) == true);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ILLUMINANCE), 1000.0f);

    /* Disable one family: its axes go back to zero, the others keep reading. */
    CHECK(sensor_set_state(0, RETRO_SENSOR_ACCELEROMETER_DISABLE, 0) == true);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ACCELEROMETER_X), 0.0f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_GYROSCOPE_Z), 1.5f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ILLUMINANCE), 1000.0f);

    /* Unknown action / id: false / zero, never a crash. */
    CHECK(sensor_set_state(0, 99, 60) == false);
    CHECK_FLOAT(sensor_get_input(0, 99), 0.0f);
    sensor_set_value(99, 5.0f);
    CHECK_FLOAT(sensor_get_input(0, 99), 0.0f);

    /* Reset: everything zero and disabled. */
    sensor_reset();
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_GYROSCOPE_Z), 0.0f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ILLUMINANCE), 0.0f);
    sensor_set_value(RETRO_SENSOR_ILLUMINANCE, 500.0f);
    CHECK_FLOAT(sensor_get_input(0, RETRO_SENSOR_ILLUMINANCE), 0.0f);

    if (failures != 0) {
        printf("sensor_test: %d checks failed\n", failures);
        return EXIT_FAILURE;
    }
    printf("sensor_test: all checks passed\n");
    return EXIT_SUCCESS;
}
