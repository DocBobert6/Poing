// Poing! Phase 2 — bare player spike.
//
// JNI bridge from the app.poing frontend to the stock mGBA libretro
// core (pin 7a12d6d4). The bridge lives in the frontend, NOT in the
// core. The core's src/ is untouched: the core is used exactly as
// libretro defines it — the frontend supplies the environment,
// video, audio and input callbacks, and drives retro_run() once per
// frame from its own thread.
//
// Adapted from the HowlingDog Phase 2 bridge (same shape, same
// lessons), retuned for mGBA:
//
//   - VIDEO IS RGB565, not XRGB8888. The Android libretro build
//     defines COLOR_16_BIT + COLOR_5_6_5 (CMakeLists.txt), so the
//     core requests RETRO_PIXEL_FORMAT_RGB565 in retro_init and
//     hands the video callback 16-bit frames. The bridge converts
//     RGB565 -> ARGB_8888 while copying. A bridge that assumed
//     XRGB8888 here would show garbage.
//   - Max frame is 240x160 (GBA); GB/GBC are 160x144.
//   - Audio is resampled to 48 kHz in the bridge with a linear
//     interpolator (spike-only, same as HowlingDog): the core's
//     native rates are 32768 Hz (GBA) and 44100 Hz (GB), read from
//     av_info at load, never hardcoded.
//   - Input is player 1 on port 0 only. The core asks for the whole
//     mask (RETRO_DEVICE_ID_JOYPAD_MASK) and for single buttons;
//     both read the same mask.
//
// THE NO-VIDEO LESSON (HowlingDog Phase 2, applies here verbatim):
// the bridge MUST answer retro_environment GET_VARIABLE for every
// core option with a documented default. mGBA reads its options
// through GET_VARIABLE in check_variables() — 18 call sites in
// libretro.c — and a "not found" answer can leave the core running
// with broken video or wrong defaults and no error anywhere. The
// table below answers all 17 mgba_* keys from
// src/platform/libretro/libretro_core_options.h, with each default
// copied from that header (the value strings are static storage:
// the core may keep the pointer, so they must outlive the call).
// GET_INPUT_BITMASKS is answered true, or the core never asks for
// the mask and input silently does nothing.
//
// LINK CABLE (Phase 2 spike verdict — see bridge/LINK_SPIKE.md):
// self-link is NOT possible through the libretro API at this pin.
// The core keeps a single global `struct mCore* core`
// (libretro.c:86), exports only retro_* symbols (link.T), and the
// link implementation (GBSIOLockstep, src/gb/sio/lockstep.c) is an
// internal core API with no libretro exposure. Two emulator
// instances cannot be created or wired together from the frontend.
// Phone-to-phone link (Phase 7) needs the same core-level work.
//
// SPIKE-ONLY shortcuts, all marked as such — do not mistake them
// for the final implementation:
//   - Video is a Bitmap copy path (framebuffer -> int[] -> Bitmap).
//     Correct, but it copies every pixel every frame. The final
//     renderer (Phase 4+) will draw the core's framebuffer directly.
//   - Audio is a linear resampler (see above). A proper resampler
//     belongs in a later phase.
//   - Battery saves: the core does NOT write .sav files itself —
//     retro_load_game only allocates the save-RAM buffer and
//     retro_unload_game only frees it. The frontend persists the
//     buffer (retro_get_memory_data, RETRO_MEMORY_SAVE_RAM + RTC)
//     to <base>.sav: flushed on pause and on quit, written back
//     into the buffer right after load, before the first frame.
//     (The Phase 2 comment claiming the core writes it on unload
//     was wrong — verified against libretro.c at the Phase 4 audit.)
//   - Core options are settable through nativeSetOption: the bridge
//     answers GET_VARIABLE from a mutable table seeded with the
//     documented defaults; a set flags GET_VARIABLE_UPDATE so the
//     core re-reads the live options on the next frame. Options
//     marked "(Restart)" in the core header only take effect on the
//     next load.

#include <jni.h>

#include <android/log.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libretro.h"
#include "ra_client.h"
#include "sensors.h"

#define LOG_TAG "Poing"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

static char *g_system_dir = NULL; // BIOS directory (frontend-owned copy)
static char *g_save_dir = NULL;   // Save directory (app-private files)
static bool g_inited = false;
static bool g_loaded = false;

// Video: the core hands us a framebuffer pointer that is only valid
// for the duration of the video callback, so we copy it here, converting
// RGB565 -> ARGB_8888 as we go.
static uint32_t *g_frame = NULL; // ARGB, max GBA size (240x160)
static int g_frame_w = 0;
static int g_frame_h = 0;
static volatile bool g_frame_ready = false;
static pthread_mutex_t g_frame_mutex = PTHREAD_MUTEX_INITIALIZER;

#define FRAME_MAX_W 240
#define FRAME_MAX_H 160

// Audio: the core delivers batches at its own sample rate; we resample
// to 48 kHz (spike-only linear resampler, see header comment) into a
// ring buffer the Kotlin side drains into AudioTrack.
#define AUDIO_OUT_RATE 48000
#define AUDIO_RING_FRAMES 16384 // stereo frames
static int16_t g_audio_ring[AUDIO_RING_FRAMES * 2];
static int g_audio_head = 0; // write position (frames)
static int g_audio_tail = 0; // read position (frames)
static int g_audio_count = 0; // frames held
static bool g_audio_overflow = false;
static pthread_mutex_t g_audio_mutex = PTHREAD_MUTEX_INITIALIZER;

// Resampler state (input rate set from av_info after load).
static double g_resample_rate = 0.0; // core sample rate
static double g_resample_pos = 0.0;  // position in input frames
static int16_t g_resample_prev_l = 0;
static int16_t g_resample_prev_r = 0;
static bool g_resample_have_prev = false;

// Input: player-1 joypad bitmask, bits are (1 << RETRO_DEVICE_ID_JOYPAD_*).
static volatile int16_t g_input_mask = 0;

// AV info captured at load.
static int g_av_width = 0;
static int g_av_height = 0;
static double g_av_fps = 0.0;
static double g_av_rate = 0.0;

// Last core error line, for the UI to show on a failed load.
static char g_last_error[512] = {0};
static pthread_mutex_t g_error_mutex = PTHREAD_MUTEX_INITIALIZER;

static void set_last_error(const char *fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    pthread_mutex_lock(&g_error_mutex);
    strncpy(g_last_error, buf, sizeof(g_last_error) - 1);
    g_last_error[sizeof(g_last_error) - 1] = '\0';
    pthread_mutex_unlock(&g_error_mutex);
}

// ---------------------------------------------------------------------------
// Core log callback -> logcat (+ last-error capture)
// ---------------------------------------------------------------------------

static void bridge_log(enum retro_log_level level, const char *fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    // The core's messages end in '\n'; logcat does not want it.
    size_t n = strlen(buf);
    while (n > 0 && (buf[n - 1] == '\n' || buf[n - 1] == '\r'))
        buf[--n] = '\0';
    int prio = ANDROID_LOG_INFO;
    if (level == RETRO_LOG_ERROR)
        prio = ANDROID_LOG_ERROR;
    else if (level == RETRO_LOG_WARN)
        prio = ANDROID_LOG_WARN;
    else if (level == RETRO_LOG_DEBUG)
        prio = ANDROID_LOG_DEBUG;
    __android_log_print(prio, LOG_TAG, "%s", buf);
    if (level == RETRO_LOG_ERROR)
        set_last_error("%s", buf);
}

// ---------------------------------------------------------------------------
// libretro callbacks supplied by the frontend
// ---------------------------------------------------------------------------

// Core options. The bridge answers GET_VARIABLE from a mutable table
// seeded with the core's documented defaults
// (src/platform/libretro/libretro_core_options.h, pin 7a12d6d4).
// Every one of the 17 mgba_* keys is answered — see the header
// comment for why an unanswered key can mean a running game with
// broken video and no error anywhere.
//
// Notes on the defaults (all copied from the header):
//   - mgba_color_correction / mgba_interframe_blending only exist
//     under COLOR_16_BIT + COLOR_5_6_5, which the Android libretro
//     build defines — both are live here. Answering them costs
//     nothing if a future build drops the defines (the core just
//     never asks).
//   - mgba_use_bios is ON, but with no BIOS file in the system
//     directory the core falls back to its built-in HLE BIOS —
//     BIOS files stay optional and never gate a start (PLAN.md
//     §1.1). Phase 3 adds the BIOS import; the spike runs HLE.
//   - mgba_gb_model "Autodetect" picks the model from the ROM
//     header, so one .gb and one .gba both boot with no setting.
#define CORE_OPT_VALUE_LEN 32
static const struct {
    const char *key;
    const char *value; // documented default, from libretro_core_options.h
} kCoreOptionDefaults[] = {
    {"mgba_gb_model", "Autodetect"},
    {"mgba_use_bios", "ON"},
    {"mgba_skip_bios", "OFF"},
    {"mgba_gb_colors", "Grayscale"},
    {"mgba_gb_colors_preset", "0"},
    {"mgba_sgb_borders", "ON"},
    {"mgba_color_correction", "OFF"},
    {"mgba_interframe_blending", "OFF"},
    {"mgba_audio_low_pass_filter", "disabled"},
    {"mgba_audio_low_pass_range", "60"},
    {"mgba_allow_opposing_directions", "no"},
    {"mgba_solar_sensor_level", "0"},
    {"mgba_force_gbp", "OFF"},
    {"mgba_idle_optimization", "Remove Known"},
    {"mgba_frameskip", "disabled"},
    {"mgba_frameskip_threshold", "33"},
    {"mgba_frameskip_interval", "0"},
};

// Mutable current values, seeded from the defaults. Kotlin pushes
// settings through nativeSetOption before retro_init, and live
// changes while a game runs; GET_VARIABLE answers from here.
static char g_opt_values[sizeof(kCoreOptionDefaults) /
                         sizeof(kCoreOptionDefaults[0])][CORE_OPT_VALUE_LEN];
static bool g_opt_inited = false;
static pthread_mutex_t g_opt_mutex = PTHREAD_MUTEX_INITIALIZER;
// Set by nativeSetOption; GET_VARIABLE_UPDATE reports it once and
// clears it, so the core re-reads the live options on the next frame.
static volatile bool g_opt_dirty = false;

static void bridge_options_init(void) {
    pthread_mutex_lock(&g_opt_mutex);
    if (!g_opt_inited) {
        for (size_t i = 0;
             i < sizeof(kCoreOptionDefaults) / sizeof(kCoreOptionDefaults[0]);
             ++i) {
            strncpy(g_opt_values[i], kCoreOptionDefaults[i].value,
                    CORE_OPT_VALUE_LEN - 1);
            g_opt_values[i][CORE_OPT_VALUE_LEN - 1] = '\0';
        }
        g_opt_inited = true;
    }
    pthread_mutex_unlock(&g_opt_mutex);
}

// ---------------------------------------------------------------------------
// RetroAchievements (Phase 6, item 8)
// ---------------------------------------------------------------------------
//
// The RA client (ra_client.c) is pure C; this section is the JNI
// layer: the callbacks ra_client needs (the memory maps, the HTTP
// request, the unlock event, the async results), performed through
// JNI into Kotlin (Bridge.raHttpRequest / raEventUnlocked / ...),
// and the nativeRa* entry points Kotlin calls.
//
// The memory maps: the core hands them over in
// RETRO_ENVIRONMENT_SET_MEMORY_MAPS at load (see bridge_environ) —
// the core's descriptor array is stack memory in _setupMaps, so
// the bridge copies the descriptors (start/len/offset/select/ptr)
// into g_ra_descs. The pointers stay live while the game is
// loaded; the count is cleared on unload. ra_client translates RA
// addresses through the rcheevos region tables + these descriptors
// (see ra_client.h); the bridge never interprets the memory itself.
#define RA_MAX_DESCS 32
static ra_mem_desc_t g_ra_descs[RA_MAX_DESCS];
static size_t g_ra_num_descs = 0;

static JavaVM *g_ra_vm = NULL;
static jobject g_ra_bridge = NULL; // global ref to the Bridge object
static jmethodID g_ra_http_request_method = NULL;
static jmethodID g_ra_event_unlocked_method = NULL;
static jmethodID g_ra_leaderboard_event_method = NULL;
static jmethodID g_ra_leaderboard_entries_result_method = NULL;
static jmethodID g_ra_login_result_method = NULL;
static jmethodID g_ra_load_game_result_method = NULL;
static jmethodID g_ra_rumble_method = NULL;
static jmethodID g_cam_start_method = NULL;
static jmethodID g_cam_stop_method = NULL;

static JNIEnv *ra_get_env() {
    JNIEnv *env = NULL;
    if (!g_ra_vm)
        return NULL;
    if (g_ra_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_ra_vm->AttachCurrentThread(&env, NULL) != JNI_OK)
            return NULL;
    }
    return env;
}

static void ra_call_http_request(int request_id, const char *url, const char *post_data) {
    JNIEnv *env = ra_get_env();
    jstring jurl;
    jstring jpost;
    if (!env || !g_ra_bridge || !g_ra_http_request_method)
        return;
    jurl = env->NewStringUTF(url ? url : "");
    jpost = post_data ? env->NewStringUTF(post_data) : NULL;
    env->CallVoidMethod(g_ra_bridge, g_ra_http_request_method, (jint)request_id, jurl, jpost);
    if (jurl)
        env->DeleteLocalRef(jurl);
    if (jpost)
        env->DeleteLocalRef(jpost);
}

static void ra_call_event_unlocked(uint32_t id, const char *title, const char *description, uint32_t points) {
    JNIEnv *env = ra_get_env();
    jstring jtitle;
    jstring jdesc;
    if (!env || !g_ra_bridge || !g_ra_event_unlocked_method)
        return;
    jtitle = env->NewStringUTF(title ? title : "");
    jdesc = env->NewStringUTF(description ? description : "");
    env->CallVoidMethod(g_ra_bridge, g_ra_event_unlocked_method, (jint)id, jtitle, jdesc, (jint)points);
    if (jtitle)
        env->DeleteLocalRef(jtitle);
    if (jdesc)
        env->DeleteLocalRef(jdesc);
}

static void ra_call_result(jmethodID method, int result, const char *error_message) {
    JNIEnv *env = ra_get_env();
    jstring jerror;
    if (!env || !g_ra_bridge || !method)
        return;
    jerror = error_message ? env->NewStringUTF(error_message) : NULL;
    env->CallVoidMethod(g_ra_bridge, method, result == 0 ? JNI_TRUE : JNI_FALSE, jerror);
    if (jerror)
        env->DeleteLocalRef(jerror);
}

// The core's captured memory maps, through the RA provider. The
// descriptors are only valid while a game is loaded (the count is
// cleared on unload); before that the provider reports none.
static void bridge_ra_map_provider(const ra_mem_desc_t **descs, size_t *num_descs, void *ctx) {
    (void)ctx;
    *descs = g_ra_descs;
    *num_descs = g_ra_num_descs;
}

static void bridge_ra_server_call(int request_id, const char *url, const char *post_data, void *ctx) {
    (void)ctx;
    ra_call_http_request(request_id, url, post_data);
}

static void bridge_ra_event(int type, uint32_t achievement_id, const char *title, const char *description, uint32_t points, void *ctx) {
    (void)ctx;
    if (type == RA_EVENT_ACHIEVEMENT_TRIGGERED)
        ra_call_event_unlocked(achievement_id, title, description, points);
    // RA_EVENT_GAME_COMPLETED is not surfaced: the achievements
    // list the app refreshes on every unlock already shows the
    // completed state.
}

static void bridge_ra_leaderboard_event(int type, uint32_t leaderboard_id, const char *json, void *ctx) {
    JNIEnv *env = ra_get_env();
    jstring jjson;
    (void)ctx;
    if (!env || !g_ra_bridge || !g_ra_leaderboard_event_method)
        return;
    jjson = json ? env->NewStringUTF(json) : NULL;
    env->CallVoidMethod(g_ra_bridge, g_ra_leaderboard_event_method, (jint)type, (jint)leaderboard_id, jjson);
    if (jjson)
        env->DeleteLocalRef(jjson);
}

static void bridge_ra_leaderboard_entries_result(int result, const char *error_message, const char *json, void *ctx) {
    JNIEnv *env = ra_get_env();
    jstring jerror;
    jstring jjson;
    (void)ctx;
    if (!env || !g_ra_bridge || !g_ra_leaderboard_entries_result_method)
        return;
    jerror = error_message ? env->NewStringUTF(error_message) : NULL;
    jjson = json ? env->NewStringUTF(json) : NULL;
    env->CallVoidMethod(g_ra_bridge, g_ra_leaderboard_entries_result_method, result == 0 ? JNI_TRUE : JNI_FALSE, jerror, jjson);
    if (jerror)
        env->DeleteLocalRef(jerror);
    if (jjson)
        env->DeleteLocalRef(jjson);
}

static void bridge_ra_login_result(int result, const char *error_message, void *ctx) {
    (void)ctx;
    ra_call_result(g_ra_login_result_method, result, error_message);
}

static void bridge_ra_load_game_result(int result, const char *error_message, void *ctx) {
    (void)ctx;
    ra_call_result(g_ra_load_game_result_method, result, error_message);
}

// Phase 6 (item 10b) — rumble: the core calls this whenever the
// rumble level changes (both motors get the same level —
// _setRumble in the pinned tree). The level is forwarded to
// Kotlin (Bridge.onRumble) as-is; Kotlin takes the latest
// strength and runs the motor while it is above zero. The call
// comes from the emu thread; ra_get_env attaches it. When the
// JVM side is not set up (RA init never ran), the rumble is
// dropped and the core is told so.
static bool bridge_rumble_set_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    JNIEnv *env;
    (void)port;
    (void)effect;
    env = ra_get_env();
    if (!env || !g_ra_bridge || !g_ra_rumble_method)
        return false;
    env->CallVoidMethod(g_ra_bridge, g_ra_rumble_method, (jint)strength);
    return true;
}

// Phase 6 (item 10c) — GB Camera: the core's frame callback,
// stashed from the GET_CAMERA_INTERFACE struct (the core sets
// frame_raw_framebuffer before the call; the bridge fills in
// start/stop). The Kotlin side (GbCamera) pushes XRGB8888
// 128x112 frames here; the core copies each one into its camera
// buffer (_updateCamera), so the pushed array is only read
// synchronously. A frame with no game loaded is dropped.
// The start/stop upcalls run on the emu thread (the core calls
// them from retro_run); ra_get_env attaches it. The start
// returns whether the camera is (now) opening — camera2 opens
// asynchronously, so the first frame arrives later; the game
// retries the capture until then (_requestImage calls start
// again while no frame has arrived).
#define GB_CAM_WIDTH 128
#define GB_CAM_HEIGHT 112
static retro_camera_frame_raw_framebuffer_t g_cam_frame_cb = NULL;

static bool bridge_camera_start(void) {
    JNIEnv *env = ra_get_env();
    if (!env || !g_ra_bridge || !g_cam_start_method)
        return false;
    return env->CallBooleanMethod(g_ra_bridge, g_cam_start_method) == JNI_TRUE;
}

static void bridge_camera_stop(void) {
    JNIEnv *env = ra_get_env();
    if (!env || !g_ra_bridge || !g_cam_stop_method)
        return;
    env->CallVoidMethod(g_ra_bridge, g_cam_stop_method);
}

static bool bridge_environ(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            if (!data)
                return false;
            enum retro_pixel_format fmt = *(enum retro_pixel_format *)data;
            // The Android libretro build defines COLOR_16_BIT +
            // COLOR_5_6_5, so the core asks for RGB565. Accept
            // only that — the video callback converts it.
            return fmt == RETRO_PIXEL_FORMAT_RGB565;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            if (!data || !g_system_dir)
                return false;
            *(const char **)data = g_system_dir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            if (!data || !g_save_dir)
                return false;
            *(const char **)data = g_save_dir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            if (!data)
                return false;
            struct retro_log_callback *log = (struct retro_log_callback *)data;
            log->log = bridge_log;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            // We answer the mask query (see bridge_input_state).
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            // Answer from the mutable table (defaults, plus anything
            // Kotlin set through nativeSetOption). Unknown keys still
            // return false.
            if (!data)
                return false;
            struct retro_variable *var = (struct retro_variable *)data;
            if (!var->key)
                return false;
            // Seed the table on first use: GET_VARIABLE is only
            // reached after retro_init (nativeInit seeds it), but
            // never answer from a zero-initialised buffer.
            bridge_options_init();
            for (size_t i = 0;
                 i < sizeof(kCoreOptionDefaults) /
                         sizeof(kCoreOptionDefaults[0]);
                 ++i) {
                if (strcmp(var->key, kCoreOptionDefaults[i].key) == 0) {
                    var->value = g_opt_values[i];
                    return true;
                }
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            // Report a pending options change once, then clear it.
            if (!data)
                return false;
            pthread_mutex_lock(&g_opt_mutex);
            bool dirty = g_opt_dirty;
            g_opt_dirty = false;
            pthread_mutex_unlock(&g_opt_mutex);
            *(bool *)data = dirty;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
        case RETRO_ENVIRONMENT_SET_AUDIO_BUFFER_STATUS_CALLBACK:
            // Acknowledged, no action in the spike.
            return true;
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
            // Phase 6 (item 8) — capture the core's memory maps
            // for RetroAchievements: the core's descriptor array
            // is stack memory in _setupMaps, so copy the entries
            // (the pointers stay live while the game is loaded).
            // The RA read translates through these (see
            // ra_client.h); the bridge never interprets them.
            const struct retro_memory_map *mmaps = (const struct retro_memory_map *)data;
            size_t i;
            size_t n;
            if (!mmaps || !mmaps->descriptors)
                return false;
            n = mmaps->num_descriptors;
            if (n > RA_MAX_DESCS)
                n = RA_MAX_DESCS;
            for (i = 0; i < n; ++i) {
                g_ra_descs[i].start = (uint32_t)mmaps->descriptors[i].start;
                g_ra_descs[i].len = (uint32_t)mmaps->descriptors[i].len;
                g_ra_descs[i].offset = (uint32_t)mmaps->descriptors[i].offset;
                g_ra_descs[i].select = (uint32_t)mmaps->descriptors[i].select;
                g_ra_descs[i].ptr = (const uint8_t *)mmaps->descriptors[i].ptr;
            }
            g_ra_num_descs = n;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
            // Phase 6 (item 8) — RetroAchievements is supported:
            // the core's `yes` local is never read after this call
            // in the pinned tree, but the answer is the truthful
            // one, and the RA work lives in the bridge + rcheevos.
            if (!data)
                return false;
            *(bool *)data = true;
            return true;
        case RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE: {
            // Phase 6 (item 10a) — the cartridge sensors (tilt /
            // gyroscope / solar): the core enables each family it
            // needs at load and reads the values through these
            // callbacks (see sensors.c). The values come from the
            // phone's SensorManager via nativeSetSensorValue; a
            // sensor the core never enabled reads zero.
            struct retro_sensor_interface *iface = (struct retro_sensor_interface *)data;
            if (!iface)
                return false;
            iface->set_sensor_state = sensor_set_state;
            iface->get_sensor_input = sensor_get_input;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE: {
            // Phase 6 (item 10b) — rumble: the core calls the
            // callback whenever the rumble level changes (see
            // bridge_rumble_set_state). The level is forwarded to
            // Kotlin, which runs the phone's vibrator while it is
            // above zero.
            struct retro_rumble_interface *iface = (struct retro_rumble_interface *)data;
            if (!iface)
                return false;
            iface->set_rumble_state = bridge_rumble_set_state;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CAMERA_INTERFACE: {
            // Phase 6 (item 10c) — the GB Camera: the core wants
            // 128x112 XRGB8888 frames (GBCAM_WIDTH/HEIGHT,
            // include/mgba/internal/gb/mbc.h:30-31) through the
            // raw-framebuffer callback it sets on this struct.
            // The bridge fills in start/stop (upcalls to Kotlin,
            // which runs camera2) and stashes the frame callback
            // for nativePushCameraFrame. Without a camera — no
            // permission, no hardware — start returns false and
            // the game keeps its white image (camData is memset
            // 0xFF until the first frame arrives).
            struct retro_camera_callback *cam = (struct retro_camera_callback *)data;
            if (!cam)
                return false;
            cam->start = bridge_camera_start;
            cam->stop = bridge_camera_stop;
            g_cam_frame_cb = cam->frame_raw_framebuffer;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
            // Not supported: plain stdio file I/O.
            return false;
        default:
            return false;
    }
}

static void bridge_video(const void *data, unsigned width, unsigned height,
                         size_t pitch) {
    if (!data || width == 0 || height == 0)
        return;
    if (width > (unsigned)FRAME_MAX_W || height > (unsigned)FRAME_MAX_H) {
        LOGW("frame %ux%u exceeds spike buffer %dx%d, dropping",
             width, height, FRAME_MAX_W, FRAME_MAX_H);
        return;
    }
    pthread_mutex_lock(&g_frame_mutex);
    const uint16_t *src = (const uint16_t *)data;
    size_t src_stride = pitch / sizeof(uint16_t);
    for (unsigned y = 0; y < height; ++y) {
        const uint16_t *srow = src + (size_t)y * src_stride;
        uint32_t *drow = g_frame + (size_t)y * width;
        for (unsigned x = 0; x < width; ++x) {
            uint16_t px = srow[x];
            uint32_t r = (px >> 11) & 0x1Fu;
            uint32_t g = (px >> 5) & 0x3Fu;
            uint32_t b = px & 0x1Fu;
            // RGB565 -> ARGB_8888, expanding each channel to 8 bits.
            drow[x] = 0xFF000000u | ((r << 3) | (r >> 2)) << 16 |
                      ((g << 2) | (g >> 4)) << 8 | ((b << 3) | (b >> 2));
        }
    }
    g_frame_w = (int)width;
    g_frame_h = (int)height;
    g_frame_ready = true;
    pthread_mutex_unlock(&g_frame_mutex);
}

// Spike-only linear resampler: core rate -> 48 kHz, stereo.
// Appends resampled frames to the ring buffer; drops the oldest
// frames (and flags it) if the reader falls behind.
static void bridge_audio_push(const int16_t *data, size_t frames) {
    if (g_resample_rate <= 0.0)
        return;
    const double step = g_resample_rate / (double)AUDIO_OUT_RATE;
    pthread_mutex_lock(&g_audio_mutex);
    for (size_t i = 0; i < frames; ++i) {
        int16_t in_l = data[i * 2];
        int16_t in_r = data[i * 2 + 1];
        if (!g_resample_have_prev) {
            g_resample_prev_l = in_l;
            g_resample_prev_r = in_r;
            g_resample_have_prev = true;
            g_resample_pos = 0.0;
            continue;
        }
        // g_resample_pos is the position of the next output sample
        // within [prev, cur]: 0 = at prev, 1 = at cur. Emit while it
        // falls inside this interval, then carry the remainder.
        while (g_resample_pos < 1.0) {
            double t = g_resample_pos;
            int32_t out_l = (int32_t)(g_resample_prev_l +
                (in_l - g_resample_prev_l) * t);
            int32_t out_r = (int32_t)(g_resample_prev_r +
                (in_r - g_resample_prev_r) * t);
            if (g_audio_count >= AUDIO_RING_FRAMES) {
                // Reader is behind: drop the oldest frame.
                g_audio_tail = (g_audio_tail + 1) % AUDIO_RING_FRAMES;
                g_audio_count--;
                g_audio_overflow = true;
            }
            g_audio_ring[g_audio_head * 2] = (int16_t)out_l;
            g_audio_ring[g_audio_head * 2 + 1] = (int16_t)out_r;
            g_audio_head = (g_audio_head + 1) % AUDIO_RING_FRAMES;
            g_audio_count++;
            g_resample_pos += step;
        }
        g_resample_pos -= 1.0;
        g_resample_prev_l = in_l;
        g_resample_prev_r = in_r;
    }
    pthread_mutex_unlock(&g_audio_mutex);
}

static size_t bridge_audio_batch(const int16_t *data, size_t frames) {
    bridge_audio_push(data, frames);
    return frames;
}

static void bridge_input_poll(void) {
    // Nothing to poll: the touch UI writes the mask directly.
}

static int16_t bridge_input_state(unsigned port, unsigned device,
                                  unsigned index, unsigned id) {
    (void)index;
    if (port != 0 || device != RETRO_DEVICE_JOYPAD)
        return 0;
    // Player 1 only in the spike. The core asks for the whole mask
    // (RETRO_DEVICE_ID_JOYPAD_MASK) and for single buttons; both
    // read the same mask.
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK)
        return g_input_mask;
    if (id < 16)
        return (g_input_mask & (int16_t)(1u << id)) ? 1 : 0;
    return 0;
}

// ---------------------------------------------------------------------------
// JNI entry points (app.poing.Bridge)
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeInit(JNIEnv *env, jobject /* thiz */,
                                 jstring systemDir, jstring saveDir) {
    if (g_inited)
        return JNI_TRUE;
    const char *sys = env->GetStringUTFChars(systemDir, nullptr);
    const char *sav = env->GetStringUTFChars(saveDir, nullptr);
    if (!sys || !sav) {
        if (sys)
            env->ReleaseStringUTFChars(systemDir, sys);
        if (sav)
            env->ReleaseStringUTFChars(saveDir, sav);
        return JNI_FALSE;
    }
    g_system_dir = strdup(sys);
    g_save_dir = strdup(sav);
    env->ReleaseStringUTFChars(systemDir, sys);
    env->ReleaseStringUTFChars(saveDir, sav);
    if (!g_system_dir || !g_save_dir)
        return JNI_FALSE;

    g_frame = (uint32_t *)calloc((size_t)FRAME_MAX_W * FRAME_MAX_H,
                                 sizeof(uint32_t));
    if (!g_frame)
        return JNI_FALSE;

    // Seed the option table before the core reads it in retro_init.
    bridge_options_init();
    retro_set_environment(bridge_environ);
    retro_set_video_refresh(bridge_video);
    retro_set_audio_sample_batch(bridge_audio_batch);
    retro_set_input_poll(bridge_input_poll);
    retro_set_input_state(bridge_input_state);
    retro_init();
    g_inited = true;
    LOGI("bridge initialised (system=%s save=%s)", g_system_dir, g_save_dir);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeLoadGame(JNIEnv *env, jobject /* thiz */,
                                     jstring path) {
    if (!g_inited)
        return JNI_FALSE;
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (!p)
        return JNI_FALSE;
    struct retro_game_info info;
    memset(&info, 0, sizeof(info));
    info.path = p;
    info.data = NULL;
    info.size = 0;
    bool ok = retro_load_game(&info);
    env->ReleaseStringUTFChars(path, p);
    if (!ok) {
        LOGE("retro_load_game failed");
        return JNI_FALSE;
    }
    struct retro_system_av_info av;
    memset(&av, 0, sizeof(av));
    retro_get_system_av_info(&av);
    g_av_width = (int)av.geometry.base_width;
    g_av_height = (int)av.geometry.base_height;
    g_av_fps = av.timing.fps;
    g_av_rate = av.timing.sample_rate;
    g_resample_rate = g_av_rate;
    g_resample_pos = 0.0;
    g_resample_have_prev = false;
    g_loaded = true;
    LOGI("game loaded: %dx%d @ %.3f fps, audio %.1f Hz",
         g_av_width, g_av_height, g_av_fps, g_av_rate);
    return JNI_TRUE;
}

JNIEXPORT jintArray JNICALL
Java_app_poing_Bridge_nativeGetAvInfo(JNIEnv *env, jobject /* thiz */) {
    jintArray out = env->NewIntArray(4);
    if (!out)
        return nullptr;
    jint vals[4] = {g_av_width, g_av_height, (jint)(g_av_fps * 1000.0),
                    (jint)g_av_rate};
    env->SetIntArrayRegion(out, 0, 4, vals);
    return out;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeRunFrame(JNIEnv * /* env */,
                                     jobject /* thiz */) {
    if (g_inited && g_loaded)
        retro_run();
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeGetFrame(JNIEnv *env, jobject /* thiz */,
                                     jintArray pixels, jintArray dims) {
    if (!g_frame_ready)
        return JNI_FALSE;
    pthread_mutex_lock(&g_frame_mutex);
    int w = g_frame_w;
    int h = g_frame_h;
    jsize len = env->GetArrayLength(pixels);
    if (len < w * h) {
        pthread_mutex_unlock(&g_frame_mutex);
        return JNI_FALSE;
    }
    env->SetIntArrayRegion(pixels, 0, w * h, (const jint *)g_frame);
    g_frame_ready = false;
    pthread_mutex_unlock(&g_frame_mutex);
    jint d[2] = {w, h};
    env->SetIntArrayRegion(dims, 0, 2, d);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_app_poing_Bridge_nativeReadAudio(JNIEnv *env, jobject /* thiz */,
                                      jshortArray samples,
                                      jint maxFrames) {
    pthread_mutex_lock(&g_audio_mutex);
    int frames = g_audio_count < maxFrames ? g_audio_count : (int)maxFrames;
    if (frames > 0) {
        // The ring may wrap; copy in up to two chunks.
        int first = AUDIO_RING_FRAMES - g_audio_tail;
        if (first > frames)
            first = frames;
        env->SetShortArrayRegion(samples, 0, first * 2,
                                 (const jshort *)(g_audio_ring +
                                                  (size_t)g_audio_tail * 2));
        if (frames > first) {
            env->SetShortArrayRegion(samples, first * 2, (frames - first) * 2,
                                     (const jshort *)g_audio_ring);
        }
        g_audio_tail = (g_audio_tail + frames) % AUDIO_RING_FRAMES;
        g_audio_count -= frames;
    }
    pthread_mutex_unlock(&g_audio_mutex);
    return frames;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeSetInput(JNIEnv * /* env */,
                                     jobject /* thiz */, jint mask) {
    g_input_mask = (int16_t)mask;
}

// Set one core option. The value is stored in the mutable table and
// flags GET_VARIABLE_UPDATE, so the core re-reads the live options
// on the next frame. Options marked "(Restart)" in the core header
// only take effect on the next load. Returns false for an unknown
// key. Safe to call before nativeInit.
JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeSetOption(JNIEnv *env, jobject /* thiz */,
                                      jstring key, jstring value) {
    bridge_options_init();
    const char *k = key ? env->GetStringUTFChars(key, nullptr) : nullptr;
    const char *v = value ? env->GetStringUTFChars(value, nullptr) : nullptr;
    bool ok = false;
    if (k && v) {
        pthread_mutex_lock(&g_opt_mutex);
        for (size_t i = 0;
             i < sizeof(kCoreOptionDefaults) / sizeof(kCoreOptionDefaults[0]);
             ++i) {
            if (strcmp(k, kCoreOptionDefaults[i].key) == 0) {
                strncpy(g_opt_values[i], v, CORE_OPT_VALUE_LEN - 1);
                g_opt_values[i][CORE_OPT_VALUE_LEN - 1] = '\0';
                g_opt_dirty = true;
                ok = true;
                break;
            }
        }
        pthread_mutex_unlock(&g_opt_mutex);
    }
    if (k)
        env->ReleaseStringUTFChars(key, k);
    if (v)
        env->ReleaseStringUTFChars(value, v);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeUnloadGame(JNIEnv * /* env */,
                                       jobject /* thiz */) {
    if (g_inited && g_loaded) {
        retro_unload_game();
        g_loaded = false;
    }
    // The captured memory maps point at the unloaded game's
    // memory: drop them so the RA read sees nothing (and
    // ra_unload_game is also called from nativeRaUnloadGame).
    g_ra_num_descs = 0;
    // The cartridge sensors belong to the loaded game too: forget
    // the values and the enabled mask (Phase 6, item 10a).
    sensor_reset();
    // The GB Camera frame callback belongs to the loaded game
    // (Phase 6, item 10c): a frame pushed after unload is dropped.
    g_cam_frame_cb = NULL;
    pthread_mutex_lock(&g_audio_mutex);
    g_audio_head = g_audio_tail = g_audio_count = 0;
    pthread_mutex_unlock(&g_audio_mutex);
    g_frame_ready = false;
}

// ---------------------------------------------------------------------------
// Save states + battery save (Phase 4)
// ---------------------------------------------------------------------------
//
// The core never writes .sav files itself: retro_get_memory_data
// returns the live save-RAM buffer, and the frontend persists it —
// the Phase 2 comment claiming the core writes it on unload was
// wrong (retro_unload_game only deinits; retro_load_game only
// allocates the buffer). So the Kotlin side flushes the buffer to
// <base>.sav on pause and on quit, and writes it back into the
// buffer right after load, before the first frame (the deferred
// setup copies the buffer into the core).
//
// Save states go through retro_serialize/unserialize, which the
// core implements via mCoreSaveStateNamed (state includes save RAM
// + RTC, so a state is self-contained). They must be called while
// the emu loop is paused — no retro_run in flight — which the
// Kotlin side enforces (save/load only from the pause screen).

JNIEXPORT jint JNICALL
Java_app_poing_Bridge_nativeSerializeSize(JNIEnv * /* env */,
                                          jobject /* thiz */) {
    if (!g_inited || !g_loaded)
        return 0;
    return (jint)retro_serialize_size();
}

// Fills [data] with the state; returns bytes written, or -1. The
// caller sizes the array with nativeSerializeSize first.
JNIEXPORT jint JNICALL
Java_app_poing_Bridge_nativeSerialize(JNIEnv *env, jobject /* thiz */,
                                      jbyteArray data) {
    if (!g_inited || !g_loaded || !data)
        return -1;
    jsize len = env->GetArrayLength(data);
    if (len <= 0)
        return -1;
    size_t need = retro_serialize_size();
    if (need == 0 || (size_t)len < need)
        return -1;
    uint8_t *buf = (uint8_t *)malloc(need);
    if (!buf)
        return -1;
    bool ok = retro_serialize(buf, need);
    if (ok)
        env->SetByteArrayRegion(data, 0, (jsize)need, (const jbyte *)buf);
    free(buf);
    return ok ? (jint)need : -1;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeUnserialize(JNIEnv *env, jobject /* thiz */,
                                        jbyteArray data) {
    if (!g_inited || !g_loaded || !data)
        return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0)
        return JNI_FALSE;
    uint8_t *buf = (uint8_t *)malloc((size_t)len);
    if (!buf)
        return JNI_FALSE;
    env->GetByteArrayRegion(data, 0, len, (jbyte *)buf);
    bool ok = retro_unserialize(buf, (size_t)len);
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_app_poing_Bridge_nativeSaveRamSize(JNIEnv * /* env */,
                                        jobject /* thiz */) {
    if (!g_inited || !g_loaded)
        return 0;
    return (jint)retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeReadSaveRam(JNIEnv *env, jobject /* thiz */,
                                        jbyteArray data) {
    if (!g_inited || !g_loaded || !data)
        return JNI_FALSE;
    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (size == 0)
        return JNI_FALSE;
    void *src = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!src)
        return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if ((size_t)len < size)
        return JNI_FALSE;
    env->SetByteArrayRegion(data, 0, (jsize)size, (const jbyte *)src);
    return JNI_TRUE;
}

// Writes [data] into the live save-RAM buffer (up to the buffer
// size). Call right after load, before the first frame — the
// deferred setup copies the buffer into the core.
JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeWriteSaveRam(JNIEnv *env, jobject /* thiz */,
                                         jbyteArray data) {
    if (!g_inited || !g_loaded || !data)
        return JNI_FALSE;
    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (size == 0)
        return JNI_FALSE;
    void *dst = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!dst)
        return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    size_t n = (size_t)len < size ? (size_t)len : size;
    jbyte *tmp = (jbyte *)malloc(n);
    if (!tmp)
        return JNI_FALSE;
    env->GetByteArrayRegion(data, 0, (jsize)n, tmp);
    memcpy(dst, tmp, n);
    free(tmp);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_app_poing_Bridge_nativeRtcSize(JNIEnv * /* env */,
                                    jobject /* thiz */) {
    if (!g_inited || !g_loaded)
        return 0;
    return (jint)retro_get_memory_size(RETRO_MEMORY_RTC);
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeReadRtc(JNIEnv *env, jobject /* thiz */,
                                    jbyteArray data) {
    if (!g_inited || !g_loaded || !data)
        return JNI_FALSE;
    size_t size = retro_get_memory_size(RETRO_MEMORY_RTC);
    if (size == 0)
        return JNI_FALSE;
    void *src = retro_get_memory_data(RETRO_MEMORY_RTC);
    if (!src)
        return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if ((size_t)len < size)
        return JNI_FALSE;
    env->SetByteArrayRegion(data, 0, (jsize)size, (const jbyte *)src);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeWriteRtc(JNIEnv *env, jobject /* thiz */,
                                     jbyteArray data) {
    if (!g_inited || !g_loaded || !data)
        return JNI_FALSE;
    size_t size = retro_get_memory_size(RETRO_MEMORY_RTC);
    if (size == 0)
        return JNI_FALSE;
    void *dst = retro_get_memory_data(RETRO_MEMORY_RTC);
    if (!dst)
        return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    size_t n = (size_t)len < size ? (size_t)len : size;
    jbyte *tmp = (jbyte *)malloc(n);
    if (!tmp)
        return JNI_FALSE;
    env->GetByteArrayRegion(data, 0, (jsize)n, tmp);
    memcpy(dst, tmp, n);
    free(tmp);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeDeinit(JNIEnv * /* env */,
                                   jobject /* thiz */) {
    if (g_inited) {
        if (g_loaded)
            retro_unload_game();
        retro_deinit();
        g_inited = false;
        g_loaded = false;
    }
    g_ra_num_descs = 0;
    sensor_reset();
    g_cam_frame_cb = NULL;
    free(g_frame);
    g_frame = NULL;
    free(g_system_dir);
    g_system_dir = NULL;
    free(g_save_dir);
    g_save_dir = NULL;
}

JNIEXPORT jstring JNICALL
Java_app_poing_Bridge_nativeLastError(JNIEnv *env, jobject /* thiz */) {
    pthread_mutex_lock(&g_error_mutex);
    jstring out = env->NewStringUTF(g_last_error);
    pthread_mutex_unlock(&g_error_mutex);
    return out;
}

// ---------------------------------------------------------------------------
// Cheats (Phase 6, item 3)
// ---------------------------------------------------------------------------
//
// The core implements retro_cheat_set/reset (libretro.c:2174-2215):
// GBA codes are "XXXXXXXX XXXXXXXX" (GameShark) or "XXXXXXXX YYYY"
// (CodeBreaker); GB codes are "XXX-XXX-XXX" (Game Genie), 8 hex
// digits (GameShark) or "XXXX:XX" (VBA). The converter inside
// retro_cheat_set splits the input into one line per code, so the
// Kotlin side passes one code per call. retro_cheat_set ignores
// its index/enabled arguments and appends to cheat set 0 — to
// disable one cheat the Kotlin side resets all and re-adds the
// enabled ones. Only valid while a game is loaded (the cheat
// device lives on the core); the Kotlin side enforces that, and
// these guards make a stray call a no-op instead of a crash.
//
JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeSetCheat(JNIEnv *env, jobject /* thiz */,
                                     jstring code) {
    if (!g_inited || !g_loaded || !code)
        return;
    const char *c = env->GetStringUTFChars(code, nullptr);
    if (!c)
        return;
    retro_cheat_set(0, true, c);
    env->ReleaseStringUTFChars(code, c);
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeClearCheats(JNIEnv * /* env */,
                                        jobject /* thiz */) {
    if (!g_inited || !g_loaded)
        return;
    retro_cheat_reset();
}

// ---------------------------------------------------------------------------
// Cartridge sensors (Phase 6, item 10a)
// ---------------------------------------------------------------------------
//
// One sensor value at a time from the Kotlin side (CartridgeSensors
// reads the phone's SensorManager and pushes each reading here).
// The core reads them through the sensor interface (see
// bridge_environ + sensors.c); a sensor the core never enabled
// reads zero, and the values are forgotten on unload.

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeSetSensorValue(JNIEnv * /* env */,
                                           jobject /* thiz */,
                                           jint sensorId, jfloat value) {
    sensor_set_value((unsigned)sensorId, (float)value);
}

// ---------------------------------------------------------------------------
// GB Camera (Phase 6, item 10c)
// ---------------------------------------------------------------------------
//
// One XRGB8888 128x112 frame from the Kotlin side (GbCamera runs
// camera2 and converts YUV to XRGB). The core copies the frame
// into its camera buffer synchronously (_updateCamera), so the
// array is only read during this call. A frame with no game
// loaded (no callback stashed) is dropped.

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativePushCameraFrame(JNIEnv *env, jobject /* thiz */, jintArray pixels) {
    jint *data;
    jsize len;
    if (!g_cam_frame_cb || !pixels)
        return;
    len = env->GetArrayLength(pixels);
    if (len != GB_CAM_WIDTH * GB_CAM_HEIGHT)
        return;
    data = env->GetIntArrayElements(pixels, nullptr);
    if (!data)
        return;
    g_cam_frame_cb((const uint32_t *)data, GB_CAM_WIDTH, GB_CAM_HEIGHT, GB_CAM_WIDTH * 4);
    env->ReleaseIntArrayElements(pixels, data, JNI_ABORT);
}

// ---------------------------------------------------------------------------
// RetroAchievements (Phase 6, item 8)
// ---------------------------------------------------------------------------
//
// The nativeRa* entry points Kotlin calls (Bridge.kt). The client
// is created once and lives for the app's lifetime; login persists,
// so it is NOT shut down in nativeDeinit. Only the loaded game is
// unloaded (in nativeRaUnloadGame, and the maps are dropped in
// nativeUnloadGame above). The memory maps come from the
// SET_MEMORY_MAPS capture; HTTP responses and the achievements
// JSON cross as byte[] (UTF-8, encoding-safe).

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaInit(JNIEnv *env, jobject thiz) {
    jclass bridge_class;
    if (ra_is_ready())
        return JNI_TRUE;
    if (env->GetJavaVM(&g_ra_vm) != JNI_OK)
        return JNI_FALSE;
    if (!g_ra_bridge) {
        g_ra_bridge = env->NewGlobalRef(thiz);
        if (!g_ra_bridge)
            return JNI_FALSE;
        bridge_class = env->GetObjectClass(thiz);
        g_ra_http_request_method = env->GetMethodID(bridge_class, "raHttpRequest", "(ILjava/lang/String;Ljava/lang/String;)V");
        g_ra_event_unlocked_method = env->GetMethodID(bridge_class, "raEventUnlocked", "(ILjava/lang/String;Ljava/lang/String;I)V");
        g_ra_leaderboard_event_method = env->GetMethodID(bridge_class, "raLeaderboardEvent", "(IILjava/lang/String;)V");
        g_ra_leaderboard_entries_result_method = env->GetMethodID(bridge_class, "raLeaderboardEntriesResult", "(ZLjava/lang/String;Ljava/lang/String;)V");
        g_ra_login_result_method = env->GetMethodID(bridge_class, "raLoginResult", "(ZLjava/lang/String;)V");
        g_ra_load_game_result_method = env->GetMethodID(bridge_class, "raLoadGameResult", "(ZLjava/lang/String;)V");
        g_ra_rumble_method = env->GetMethodID(bridge_class, "onRumble", "(I)V");
        g_cam_start_method = env->GetMethodID(bridge_class, "onCameraStart", "()Z");
        g_cam_stop_method = env->GetMethodID(bridge_class, "onCameraStop", "()V");
        env->DeleteLocalRef(bridge_class);
        if (!g_ra_http_request_method || !g_ra_event_unlocked_method ||
            !g_ra_leaderboard_event_method || !g_ra_leaderboard_entries_result_method ||
            !g_ra_login_result_method || !g_ra_load_game_result_method || !g_ra_rumble_method ||
            !g_cam_start_method || !g_cam_stop_method) {
            LOGE("nativeRaInit: a Kotlin callback is missing (RA, rumble or camera)");
            return JNI_FALSE;
        }
    }
    if (ra_init(bridge_ra_map_provider, NULL, bridge_ra_server_call, NULL, bridge_ra_event, NULL) != 0)
        return JNI_FALSE;
    ra_set_leaderboard_event_handler(bridge_ra_leaderboard_event, NULL);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaLogin(JNIEnv *env, jobject /* thiz */, jstring username, jstring password) {
    const char *user;
    const char *pass;
    int rc;
    if (!ra_is_ready() || !username || !password)
        return JNI_FALSE;
    user = env->GetStringUTFChars(username, NULL);
    pass = env->GetStringUTFChars(password, NULL);
    if (!user || !pass) {
        if (user)
            env->ReleaseStringUTFChars(username, user);
        if (pass)
            env->ReleaseStringUTFChars(password, pass);
        return JNI_FALSE;
    }
    // The username and password are copied into the request by
    // rcheevos before this returns, so releasing them here is
    // safe. The password is used once, for this login only — it
    // is never stored anywhere (the session token the server
    // returns is what the app keeps; see nativeRaToken).
    rc = ra_login_with_password(user, pass, bridge_ra_login_result, NULL);
    env->ReleaseStringUTFChars(username, user);
    env->ReleaseStringUTFChars(password, pass);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

// Re-log in with a stored session token (the token a password
// login returned on an earlier launch). No password involved.
JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaLoginWithToken(JNIEnv *env, jobject /* thiz */, jstring username, jstring token) {
    const char *user;
    const char *tok;
    int rc;
    if (!ra_is_ready() || !username || !token)
        return JNI_FALSE;
    user = env->GetStringUTFChars(username, NULL);
    tok = env->GetStringUTFChars(token, NULL);
    if (!user || !tok) {
        if (user)
            env->ReleaseStringUTFChars(username, user);
        if (tok)
            env->ReleaseStringUTFChars(token, tok);
        return JNI_FALSE;
    }
    // As above: rcheevos copies the credentials into the request
    // before this returns.
    rc = ra_login_with_token(user, tok, bridge_ra_login_result, NULL);
    env->ReleaseStringUTFChars(username, user);
    env->ReleaseStringUTFChars(token, tok);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

// The session token of the logged-in user (NULL when logged
// out) — what the app stores after a password login, so the
// next launch can re-log in without the password.
JNIEXPORT jstring JNICALL
Java_app_poing_Bridge_nativeRaToken(JNIEnv *env, jobject /* thiz */) {
    const char *token = ra_logged_in_token();
    return token ? env->NewStringUTF(token) : NULL;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeRaLogout(JNIEnv * /* env */, jobject /* thiz */) {
    ra_logout();
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaIsLoggedIn(JNIEnv * /* env */, jobject /* thiz */) {
    return ra_is_logged_in() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_app_poing_Bridge_nativeRaLoggedInUsername(JNIEnv *env, jobject /* thiz */) {
    const char *user = ra_logged_in_username();
    return user ? env->NewStringUTF(user) : NULL;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaLoadGame(JNIEnv *env, jobject /* thiz */, jstring path) {
    const char *cpath;
    uint32_t console_id;
    int rc;
    if (!ra_is_ready() || !path)
        return JNI_FALSE;
    cpath = env->GetStringUTFChars(path, NULL);
    if (!cpath)
        return JNI_FALSE;
    console_id = ra_console_id_for_path(cpath);
    if (console_id == 0) {
        // Not a RetroAchievements game for this frontend
        // (or an unsupported extension) — RA stays inactive.
        env->ReleaseStringUTFChars(path, cpath);
        return JNI_FALSE;
    }
    // The path is only used synchronously (to hash the file),
    // so releasing it here is safe.
    rc = ra_load_game(console_id, cpath, bridge_ra_load_game_result, NULL);
    env->ReleaseStringUTFChars(path, cpath);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeRaUnloadGame(JNIEnv * /* env */, jobject /* thiz */) {
    ra_unload_game();
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaIsGameLoaded(JNIEnv * /* env */, jobject /* thiz */) {
    return ra_is_game_loaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_app_poing_Bridge_nativeRaGameTitle(JNIEnv *env, jobject /* thiz */) {
    const char *title = ra_game_title();
    return title ? env->NewStringUTF(title) : NULL;
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeRaDoFrame(JNIEnv * /* env */, jobject /* thiz */) {
    ra_do_frame();
}

JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeRaReset(JNIEnv * /* env */, jobject /* thiz */) {
    ra_reset();
}

JNIEXPORT jbyteArray JNICALL
Java_app_poing_Bridge_nativeRaSerializeProgress(JNIEnv *env, jobject /* thiz */) {
    size_t size = ra_progress_size();
    uint8_t *buf;
    jbyteArray out;
    if (size == 0)
        return NULL;
    buf = (uint8_t *)malloc(size);
    if (!buf)
        return NULL;
    if (ra_serialize_progress(buf, size) != 0) {
        free(buf);
        return NULL;
    }
    out = env->NewByteArray((jsize)size);
    if (out)
        env->SetByteArrayRegion(out, 0, (jsize)size, (const jbyte *)buf);
    free(buf);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaDeserializeProgress(JNIEnv *env, jobject /* thiz */, jbyteArray data) {
    jsize len;
    uint8_t *buf;
    int rc;
    if (!data)
        return JNI_FALSE;
    len = env->GetArrayLength(data);
    if (len <= 0)
        return JNI_FALSE;
    buf = (uint8_t *)malloc((size_t)len);
    if (!buf)
        return JNI_FALSE;
    env->GetByteArrayRegion(data, 0, len, (jbyte *)buf);
    rc = ra_deserialize_progress(buf, (size_t)len);
    free(buf);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

// The achievements list as UTF-8 JSON (a byte array, not a
// string, so non-ASCII titles survive the JNI crossing intact).
JNIEXPORT jbyteArray JNICALL
Java_app_poing_Bridge_nativeRaGetAchievements(JNIEnv *env, jobject /* thiz */) {
    char *json = ra_get_achievements_json();
    size_t len;
    jbyteArray out;
    if (!json)
        return NULL;
    len = strlen(json);
    out = env->NewByteArray((jsize)len);
    if (out)
        env->SetByteArrayRegion(out, 0, (jsize)len, (const jbyte *)json);
    free(json);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaHasLeaderboards(JNIEnv * /* env */, jobject /* thiz */) {
    return ra_has_leaderboards() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_app_poing_Bridge_nativeRaGetLeaderboards(JNIEnv *env, jobject /* thiz */) {
    char *json = ra_get_leaderboards_json();
    size_t len;
    jbyteArray out;
    if (!json)
        return NULL;
    len = strlen(json);
    out = env->NewByteArray((jsize)len);
    if (out)
        env->SetByteArrayRegion(out, 0, (jsize)len, (const jbyte *)json);
    free(json);
    return out;
}

// The result arrives asynchronously through
// raLeaderboardEntriesResult. Returns false when no game is
// loaded (the callback is not called then).
JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaFetchLeaderboardEntries(JNIEnv * /* env */, jobject /* thiz */, jint leaderboard_id, jboolean around_user) {
    return ra_fetch_leaderboard_entries((uint32_t)leaderboard_id, around_user == JNI_TRUE ? 1 : 0, bridge_ra_leaderboard_entries_result, NULL) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_poing_Bridge_nativeRaHasRichPresence(JNIEnv * /* env */, jobject /* thiz */) {
    return ra_has_rich_presence() ? JNI_TRUE : JNI_FALSE;
}

// The current rich presence message ("" when the game has
// none). The presence itself is sent to the server
// automatically by rc_client's periodic ping, so this only
// reads it for display.
JNIEXPORT jstring JNICALL
Java_app_poing_Bridge_nativeRaGetRichPresence(JNIEnv *env, jobject /* thiz */) {
    char buffer[512];
    if (ra_get_rich_presence(buffer, sizeof(buffer)) == 0)
        return env->NewStringUTF("");
    return env->NewStringUTF(buffer);
}

// The HTTP response body arrives as UTF-8 bytes (a byte array,
// not a string, so the JSON parses exactly as the server sent
// it). A null body is an empty response.
JNIEXPORT void JNICALL
Java_app_poing_Bridge_nativeRaHttpResponse(JNIEnv *env, jobject /* thiz */, jint request_id, jbyteArray body, jint http_status) {
    jsize len;
    uint8_t *buf;
    if (!body) {
        ra_http_response((int)request_id, "", 0, (int)http_status);
        return;
    }
    len = env->GetArrayLength(body);
    if (len <= 0) {
        ra_http_response((int)request_id, "", 0, (int)http_status);
        return;
    }
    buf = (uint8_t *)malloc((size_t)len + 1);
    if (!buf) {
        ra_http_response((int)request_id, "", 0, 500);
        return;
    }
    env->GetByteArrayRegion(body, 0, len, (jbyte *)buf);
    buf[len] = '\0';
    ra_http_response((int)request_id, (const char *)buf, (size_t)len, (int)http_status);
    free(buf);
}

} // extern "C"
