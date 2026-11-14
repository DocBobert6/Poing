/* Poing! SGB fix — host test for the video frame path
 * (bridge/jni/bridge.cpp).
 *
 * Proves bridge_video accepts a 256x224 Super Game Boy frame
 * (SGB model + borders on) and copies it whole into g_frame,
 * and that the old sizes (160x144 GB, 240x160 GBA) still pass
 * while oversize frames are still dropped. Regression test for
 * the Donkey Kong Land 3 boot hang: under the old 240x160 spike
 * buffer every SGB frame was dropped, g_frame_ready was never
 * set, and the player sat on "Loading..." forever.
 *
 * Host-only: it runs on the build machine, never on the device
 * and never in CI (the CI proves the bridge compiles and links;
 * this proves the frame path itself). bridge.cpp is C++, so the
 * test is C++ and includes it directly to reach the static
 * bridge_video; the core is NOT linked — the retro_* entry
 * points are stubbed below, and the real ra_client.c /
 * sensors.c / rcheevos sources are compiled alongside, exactly
 * as in bridge/jni/CMakeLists.txt. Run from bridge/tests/:
 *
 *   HOSTINC=host-include
 *   INC="-I$HOSTINC -I../../src/platform/libretro -I../jni \
 *        -I../../deps/rcheevos/include -I../../deps/rcheevos/src \
 *        -I../../deps/rcheevos/src/rcheevos \
 *        -I../../deps/rcheevos/src/rhash \
 *        -I../../deps/rcheevos/src/rapi"
 * (host-include/ carries the two Android headers bridge.cpp
 * needs: a verbatim copy of the NDK's jni.h — it only includes
 * <stdarg.h>/<stdint.h> and is host-safe — plus a minimal
 * host-only stand-in for <android/log.h>. NEVER put the NDK
 * sysroot itself on the include path: its Bionic headers do
 * not compile under host gcc.)
 *   RC="../../deps/rcheevos/src/rc_client.c \
 *       ../../deps/rcheevos/src/rc_compat.c \
 *       ../../deps/rcheevos/src/rc_util.c \
 *       ../../deps/rcheevos/src/rc_version.c \
 *       ../../deps/rcheevos/src/rcheevos/alloc.c \
 *       ../../deps/rcheevos/src/rcheevos/condition.c \
 *       ../../deps/rcheevos/src/rcheevos/condset.c \
 *       ../../deps/rcheevos/src/rcheevos/consoleinfo.c \
 *       ../../deps/rcheevos/src/rcheevos/format.c \
 *       ../../deps/rcheevos/src/rcheevos/lboard.c \
 *       ../../deps/rcheevos/src/rcheevos/memref.c \
 *       ../../deps/rcheevos/src/rcheevos/operand.c \
 *       ../../deps/rcheevos/src/rcheevos/rc_validate.c \
 *       ../../deps/rcheevos/src/rcheevos/richpresence.c \
 *       ../../deps/rcheevos/src/rcheevos/runtime.c \
 *       ../../deps/rcheevos/src/rcheevos/runtime_progress.c \
 *       ../../deps/rcheevos/src/rcheevos/trigger.c \
 *       ../../deps/rcheevos/src/rcheevos/value.c \
 *       ../../deps/rcheevos/src/rhash/aes.c \
 *       ../../deps/rcheevos/src/rhash/cdreader.c \
 *       ../../deps/rcheevos/src/rhash/hash.c \
 *       ../../deps/rcheevos/src/rhash/hash_disc.c \
 *       ../../deps/rcheevos/src/rhash/hash_encrypted.c \
 *       ../../deps/rcheevos/src/rhash/hash_rom.c \
 *       ../../deps/rcheevos/src/rhash/hash_zip.c \
 *       ../../deps/rcheevos/src/rhash/md5.c \
 *       ../../deps/rcheevos/src/rapi/rc_api_common.c \
 *       ../../deps/rcheevos/src/rapi/rc_api_editor.c \
 *       ../../deps/rcheevos/src/rapi/rc_api_info.c \
 *       ../../deps/rcheevos/src/rapi/rc_api_runtime.c \
 *       ../../deps/rcheevos/src/rapi/rc_api_user.c"
 *   gcc -std=c99 -D_GNU_SOURCE -DRC_CLIENT_SUPPORTS_HASH $INC -c \
 *       ../jni/ra_client.c ../jni/sensors.c $RC
 *   g++ -std=c++17 -D_GNU_SOURCE -DRC_CLIENT_SUPPORTS_HASH $INC -c \
 *       video_test.cpp -o video_test.o
 *   g++ video_test.o ra_client.o sensors.o rc_client.o rc_compat.o \
 *       rc_util.o rc_version.o alloc.o condition.o condset.o \
 *       consoleinfo.o format.o lboard.o memref.o operand.o \
 *       rc_validate.o richpresence.o runtime.o runtime_progress.o \
 *       trigger.o value.o aes.o cdreader.o hash.o hash_disc.o \
 *       hash_encrypted.o hash_rom.o hash_zip.o md5.o \
 *       rc_api_common.o rc_api_editor.o rc_api_info.o \
 *       rc_api_runtime.o rc_api_user.o -o video_test -pthread
 *   ./video_test
 */

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../jni/bridge.cpp"

/* ------------------------------------------------------------------ */
/* Stubs: the core is not linked on the host.                         */
/* ------------------------------------------------------------------ */

extern "C" int __android_log_print(int prio, const char *tag,
                                   const char *fmt, ...) {
    (void)prio;
    (void)tag;
    (void)fmt;
    return 0;
}

extern "C" {

void retro_set_environment(retro_environment_t cb) { (void)cb; }
void retro_set_video_refresh(retro_video_refresh_t cb) { (void)cb; }
void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb) {
    (void)cb;
}
void retro_set_input_poll(retro_input_poll_t cb) { (void)cb; }
void retro_set_input_state(retro_input_state_t cb) { (void)cb; }
void retro_init(void) {}
void retro_deinit(void) {}
void retro_get_system_av_info(struct retro_system_av_info *info) {
    (void)info;
}
void retro_run(void) {}
size_t retro_serialize_size(void) { return 0; }
bool retro_serialize(void *data, size_t len) {
    (void)data;
    (void)len;
    return false;
}
bool retro_unserialize(const void *data, size_t len) {
    (void)data;
    (void)len;
    return false;
}
void retro_cheat_reset(void) {}
void retro_cheat_set(unsigned index, bool enabled, const char *code) {
    (void)index;
    (void)enabled;
    (void)code;
}
bool retro_load_game(const struct retro_game_info *game) {
    (void)game;
    return false;
}
void retro_unload_game(void) {}
void *retro_get_memory_data(unsigned id) {
    (void)id;
    return NULL;
}
size_t retro_get_memory_size(unsigned id) {
    (void)id;
    return 0;
}

} /* extern "C" */

static int g_failures;

#define CHECK(cond) do { \
    if (!(cond)) { \
        printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
        ++g_failures; \
    } \
} while (0)

/* Fill a 256x224 RGB565 source with a padded pitch, putting
 * known colours at the four corners and the centre. The +8
 * texel pad proves the copy honours pitch, not width. */
static void fill_sgb_frame(uint16_t *src, size_t stride) {
    unsigned x, y;
    for (y = 0; y < 224; ++y)
        for (x = 0; x < 256; ++x)
            src[(size_t)y * stride + x] = 0x0000;
    src[0] = 0xF800; /* top-left: pure red */
    src[255] = 0x07E0; /* top-right: pure green */
    src[(size_t)223 * stride + 0] = 0x001F; /* bottom-left: pure blue */
    src[(size_t)223 * stride + 255] = 0xFFFF; /* bottom-right: white */
    src[(size_t)112 * stride + 128] = 0x8410; /* centre: mid grey */
}

static void test_sgb_frame(void) {
    /* The buffer the fix sizes: an SGB frame must fit in it. */
    CHECK(FRAME_MAX_W >= 256);
    CHECK(FRAME_MAX_H >= 224);

    static uint16_t src[224 * (256 + 8)];
    const size_t stride = 256 + 8;
    fill_sgb_frame(src, stride);

    g_frame_ready = false;
    bridge_video(src, 256, 224, stride * sizeof(uint16_t));
    CHECK(g_frame_ready == true);
    CHECK(g_frame_w == 256);
    CHECK(g_frame_h == 224);
    /* RGB565 -> ARGB_8888, channel-expanded (Phase 2 rule). */
    CHECK(g_frame[0] == 0xFFFF0000u);
    CHECK(g_frame[255] == 0xFF00FF00u);
    CHECK(g_frame[(size_t)223 * 256 + 0] == 0xFF0000FFu);
    CHECK(g_frame[(size_t)223 * 256 + 255] == 0xFFFFFFFFu);
    CHECK(g_frame[(size_t)112 * 256 + 128] == 0xFF848284u);
    /* A row past the old 160-row buffer proves the whole frame
     * landed, not just the old region. */
    CHECK(g_frame[(size_t)200 * 256 + 0] == 0xFF000000u);
}

static void test_old_sizes_still_pass(void) {
    static uint16_t src[240 * 160];
    memset(src, 0, sizeof(src));
    src[0] = 0xF800;

    g_frame_ready = false;
    bridge_video(src, 160, 144, 160 * sizeof(uint16_t));
    CHECK(g_frame_ready == true);
    CHECK(g_frame_w == 160);
    CHECK(g_frame_h == 144);
    CHECK(g_frame[0] == 0xFFFF0000u);

    g_frame_ready = false;
    bridge_video(src, 240, 160, 240 * sizeof(uint16_t));
    CHECK(g_frame_ready == true);
    CHECK(g_frame_w == 240);
    CHECK(g_frame_h == 160);
    CHECK(g_frame[0] == 0xFFFF0000u);
}

static void test_oversize_still_drops(void) {
    static uint16_t src[257 * 225];
    memset(src, 0, sizeof(src));

    /* One pixel over in width: dropped, dims untouched. */
    g_frame_ready = false;
    g_frame_w = 123;
    g_frame_h = 456;
    bridge_video(src, 257, 224, 257 * sizeof(uint16_t));
    CHECK(g_frame_ready == false);
    CHECK(g_frame_w == 123);
    CHECK(g_frame_h == 456);

    /* One pixel over in height: dropped, dims untouched. */
    g_frame_ready = false;
    g_frame_w = 123;
    g_frame_h = 456;
    bridge_video(src, 256, 225, 256 * sizeof(uint16_t));
    CHECK(g_frame_ready == false);
    CHECK(g_frame_w == 123);
    CHECK(g_frame_h == 456);
}

static void test_null_and_zero(void) {
    static uint16_t src[256 * 224];
    memset(src, 0, sizeof(src));

    g_frame_ready = false;
    bridge_video(NULL, 256, 224, 256 * sizeof(uint16_t));
    CHECK(g_frame_ready == false);

    g_frame_ready = false;
    bridge_video(src, 0, 224, 256 * sizeof(uint16_t));
    CHECK(g_frame_ready == false);

    g_frame_ready = false;
    bridge_video(src, 256, 0, 256 * sizeof(uint16_t));
    CHECK(g_frame_ready == false);
}

int main(void) {
    g_frame = (uint32_t *)calloc((size_t)FRAME_MAX_W * FRAME_MAX_H,
                                 sizeof(uint32_t));
    if (!g_frame) {
        printf("video_test: g_frame calloc failed\n");
        return EXIT_FAILURE;
    }

    test_sgb_frame();
    test_old_sizes_still_pass();
    test_oversize_still_drops();
    test_null_and_zero();

    free(g_frame);
    g_frame = NULL;

    if (g_failures) {
        printf("video_test: %d FAILURES\n", g_failures);
        return EXIT_FAILURE;
    }
    printf("video_test: all checks passed\n");
    return EXIT_SUCCESS;
}
