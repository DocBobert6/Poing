#include "sensors.h"

#include <string.h>

#include "libretro.h"

/* One slot per sensor id (0..6): accelerometer X/Y/Z, gyroscope
 * X/Y/Z, illuminance. */
#define SENSOR_COUNT 7

static float g_sensor_values[SENSOR_COUNT];
static bool g_sensor_enabled[SENSOR_COUNT];

float sensor_get_input(unsigned port, unsigned id) {
    (void)port;
    if (id >= SENSOR_COUNT)
        return 0.0f;
    if (!g_sensor_enabled[id])
        return 0.0f;
    return g_sensor_values[id];
}

bool sensor_set_state(unsigned port, enum retro_sensor_action action, unsigned rate) {
    unsigned base;
    bool enable;
    (void)port;
    (void)rate;
    /* Actions come in enable/disable pairs per family. */
    switch (action) {
        case RETRO_SENSOR_ACCELEROMETER_ENABLE:
        case RETRO_SENSOR_ACCELEROMETER_DISABLE:
            base = RETRO_SENSOR_ACCELEROMETER_X;
            enable = (action == RETRO_SENSOR_ACCELEROMETER_ENABLE);
            break;
        case RETRO_SENSOR_GYROSCOPE_ENABLE:
        case RETRO_SENSOR_GYROSCOPE_DISABLE:
            base = RETRO_SENSOR_GYROSCOPE_X;
            enable = (action == RETRO_SENSOR_GYROSCOPE_ENABLE);
            break;
        case RETRO_SENSOR_ILLUMINANCE_ENABLE:
        case RETRO_SENSOR_ILLUMINANCE_DISABLE:
            base = RETRO_SENSOR_ILLUMINANCE;
            enable = (action == RETRO_SENSOR_ILLUMINANCE_ENABLE);
            break;
        default:
            return false;
    }
    if (base == RETRO_SENSOR_ILLUMINANCE) {
        g_sensor_enabled[RETRO_SENSOR_ILLUMINANCE] = enable;
    } else {
        g_sensor_enabled[base] = enable;
        g_sensor_enabled[base + 1] = enable;
        g_sensor_enabled[base + 2] = enable;
    }
    return true;
}

void sensor_set_value(unsigned id, float value) {
    if (id >= SENSOR_COUNT)
        return;
    g_sensor_values[id] = value;
}

void sensor_reset(void) {
    memset(g_sensor_values, 0, sizeof(g_sensor_values));
    memset(g_sensor_enabled, 0, sizeof(g_sensor_enabled));
}
