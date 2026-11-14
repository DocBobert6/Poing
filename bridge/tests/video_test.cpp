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
 * as in bridge/jni/CMakeLists.txt.
 *
 * Performance fix 5 (2026-11-14) adds the dirty check to the
 * same harness: frame_is_new() compares g_frame against the
 * last handed-out copy and returns false for an unchanged
 * picture, so the whole JNI/Bitmap/texture copy chain is
 * skipped on static screens. The test drives bridge_video
 * twice with the same picture and expects new-then-unchanged,
 * then changes one pixel and expects new again, then changes
 * geometry and expects new, then forgets the last frame (as
 * nativeUnloadGame does) and expects the same picture to be
 * new again — the next game's first frame can never falsely
 * match the previous game's last one. Run from bridge/tests/:
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

static void test_dirty_detection(void) {
    static uint16_t src[240 * 160];
    memset(src, 0, sizeof(src));
    src[0] = 0xF800;

    /* First frame of a game is always new. */
    g_frame_last_valid = false;
    bridge_video(src, 160, 144, 160 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == true);
    /* Same picture again: not new — the copy chain is skipped. */
    bridge_video(src, 160, 144, 160 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == false);
    /* One pixel changes: new again, then unchanged once more. */
    src[100] = 0x07E0;
    bridge_video(src, 160, 144, 160 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == true);
    bridge_video(src, 160, 144, 160 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == false);
    /* Geometry change: new even though the pixels match. */
    bridge_video(src, 240, 160, 240 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == true);
    bridge_video(src, 240, 160, 240 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == false);
    /* Unload forgets the last frame: the same picture is new
     * again, so a new game's first frame can never falsely
     * match the previous game's last one. */
    g_frame_last_valid = false;
    bridge_video(src, 160, 144, 160 * sizeof(uint16_t));
    CHECK(frame_is_new(g_frame_w, g_frame_h) == true);
}

/* Performance fix 9 — the fixed-point resampler: Q16.16
 * position/step, a power-of-two ring mask instead of the
 * modulo, and the discard flag. The reference is the double
 * math the old code did: at 32768 Hz in, the step is
 * 44739 (Q16.16), so the first outputs of a 0,100,200...
 * ramp are 0, 68, 136 — pinned exactly, not approximately.
 */
static void test_audio_resample(void) {
    /* 32768 Hz in, 48000 Hz out. */
    g_resample_rate = 32768.0;
    g_resample_step_fp = (int32_t)(32768.0 * 65536.0 / 48000.0);
    if (g_resample_step_fp < 1)
        g_resample_step_fp = 1;
    g_resample_pos_fp = 0;
    g_resample_have_prev = false;
    g_audio_head = 0;
    g_audio_tail = 0;
    g_audio_count = 0;
    g_audio_overflow = false;
    g_audio_discard.store(false);

    /* A ramp: 100 input frames, L = R = i * 100. The first
     * sample seeds prev (no output); the rest interpolate. */
    int16_t in[200];
    for (int i = 0; i < 100; ++i) {
        in[i * 2] = (int16_t)(i * 100);
        in[i * 2 + 1] = (int16_t)(i * 100);
    }
    bridge_audio_push(in, 100);
    /* ~99 intervals * 48000/32768 = ~145 outputs. */
    CHECK(g_audio_count > 130 && g_audio_count < 150);
    /* The output is the ramp, interpolated: first 0, then
     * 68 (100 * 44739 / 65536), then 136. */
    CHECK(g_audio_ring[0] == 0);
    CHECK(g_audio_ring[2] == 68);
    CHECK(g_audio_ring[4] == 136);
    /* Monotone, and the last output is in the final
     * interval [9800, 9900]. */
    for (int i = 1; i < g_audio_count; ++i) {
        CHECK(g_audio_ring[i * 2] >= g_audio_ring[(i - 1) * 2]);
    }
    int16_t last = g_audio_ring[(g_audio_count - 1) * 2];
    CHECK(last >= 9800 && last <= 9900);

    /* Discard: the batch is dropped without resampling, the
     * count does not move, and the resampler forgets its
     * previous sample so it re-syncs cleanly. */
    int count_before = g_audio_count;
    g_audio_discard.store(true);
    bridge_audio_push(in, 100);
    CHECK(g_audio_count == count_before);
    CHECK(g_resample_have_prev == false);
    g_audio_discard.store(false);
    bridge_audio_push(in, 100);
    CHECK(g_audio_count > count_before);

    /* Ring wrap: 48000 Hz in is one output per input, so
     * 20000 frames are 19999 outputs into a 16384-frame
     * ring — it fills, drops the oldest, and both ends
     * wrap to 19999 % 16384 = 3615. The sawtooth input
     * (i % 50) * 100 keeps every sample in int16 range. */
    g_resample_rate = 48000.0;
    g_resample_step_fp = 65536;
    g_resample_pos_fp = 0;
    g_resample_have_prev = false;
    g_audio_head = 0;
    g_audio_tail = 0;
    g_audio_count = 0;
    g_audio_overflow = false;
    int16_t saw[40000];
    for (int i = 0; i < 20000; ++i) {
        saw[i * 2] = (int16_t)((i % 50) * 100);
        saw[i * 2 + 1] = (int16_t)((i % 50) * 100);
    }
    bridge_audio_push(saw, 20000);
    CHECK(g_audio_count == AUDIO_RING_FRAMES);
    CHECK(g_audio_overflow == true);
    CHECK(g_audio_head == 3615);
    CHECK(g_audio_tail == 3615);
    /* Output k is input k (one per interval, at pos 0), so
     * the oldest surviving frame is output 3615, whose
     * value is (3615 % 50) * 100 = 1500. */
    CHECK(g_audio_ring[g_audio_tail * 2] == 1500);
    printf("video_test: audio resample ok (ring full %d, head/tail %d)\n",
           g_audio_count, g_audio_head);
}

int main(void) {
    g_frame = (uint32_t *)calloc((size_t)FRAME_MAX_W * FRAME_MAX_H,
                                 sizeof(uint32_t));
    if (!g_frame) {
        printf("video_test: g_frame calloc failed\n");
        return EXIT_FAILURE;
    }
    g_frame_last = (uint32_t *)calloc((size_t)FRAME_MAX_W * FRAME_MAX_H,
                                      sizeof(uint32_t));
    if (!g_frame_last) {
        printf("video_test: g_frame_last calloc failed\n");
        return EXIT_FAILURE;
    }

    test_sgb_frame();
    test_old_sizes_still_pass();
    test_oversize_still_drops();
    test_null_and_zero();
    test_dirty_detection();
    test_audio_resample();

    free(g_frame);
    g_frame = NULL;
    free(g_frame_last);
    g_frame_last = NULL;

    if (g_failures) {
        printf("video_test: %d FAILURES\n", g_failures);
        return EXIT_FAILURE;
    }
    printf("video_test: all checks passed\n");
    return EXIT_SUCCESS;
}
