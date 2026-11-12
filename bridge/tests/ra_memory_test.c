/* Poing! Phase 6 (item 8) — host test for the RA memory map.
 *
 * Verifies ra_read_memory_mapped (bridge/jni/ra_client.c) and
 * ra_console_id_for_path against mock memory maps built exactly
 * like the core's SET_MEMORY_MAPS descriptors
 * (src/platform/libretro/libretro.c _setupMaps): the GBA maps
 * (IWRAM / EWRAM / SRAM / VRAM) and the GB maps (ROM / VRAM /
 * WRAM bank 0 / bank 1 / OAM / IO / HRAM / IE / external RAM /
 * extra SRAM banks / GBC WRAM banks 2-7).
 *
 * Host-only: it runs on the build machine with gcc, never on the
 * device and never in CI (the CI proves the bridge compiles and
 * links; this proves the translation itself). Run:
 *   gcc -std=c99 -D_GNU_SOURCE -DRC_CLIENT_SUPPORTS_HASH \
 *     -I../../deps/rcheevos/include -I../../deps/rcheevos/src \
 *     -I../../deps/rcheevos/src/rcheevos \
 *     -I../../deps/rcheevos/src/rhash \
 *     -I../../deps/rcheevos/src/rapi \
 *     -I../jni ra_memory_test.c ../jni/ra_client.c \
 *     <rcheevos sources, see the Makefile note below> \
 *     -o ra_memory_test && ./ra_memory_test
 * (rcheevos sources: the list in bridge/jni/CMakeLists.txt.)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ra_client.h"

static int g_failures;

#define CHECK(cond) do { \
    if (!(cond)) { \
        printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
        ++g_failures; \
    } \
} while (0)

/* Fill a buffer with a per-offset pattern (offset low byte XOR
 * the tag), so a read from the wrong offset is detectable. */
static void fill_pattern(uint8_t* buf, size_t size, uint8_t tag) {
    size_t i;
    for (i = 0; i < size; ++i)
        buf[i] = (uint8_t)((i & 0xFF) ^ tag);
}

/* Read `n` bytes at an RA address and check them against the
 * pattern of the buffer they should come from. */
static void check_read(uint32_t console, uint32_t address, uint32_t n,
                       const ra_mem_desc_t* descs, size_t num_descs,
                       const uint8_t* expect_buf, size_t expect_off, uint8_t tag) {
    uint8_t got[64];
    uint32_t got_n;
    uint32_t i;
    CHECK(n <= sizeof(got));
    got_n = ra_read_memory_mapped(console, address, got, n, descs, num_descs);
    CHECK(got_n == n);
    for (i = 0; i < got_n && i < n; ++i)
        CHECK(got[i] == (uint8_t)(((expect_off + i) & 0xFF) ^ tag));
}

/* ------------------------------------------------------------------ */
/* GBA                                                                */
/* ------------------------------------------------------------------ */

static void test_gba(void) {
    static uint8_t iwram[0x8000];
    static uint8_t wram[0x40000];
    static uint8_t sram[0x10000];
    static uint8_t vram[0x18000];
    ra_mem_desc_t descs[4];
    uint8_t got[8];
    uint32_t n;

    fill_pattern(iwram, sizeof(iwram), 0xA5);
    fill_pattern(wram, sizeof(wram), 0x00);
    fill_pattern(sram, sizeof(sram), 0x5A);
    fill_pattern(vram, sizeof(vram), 0x3C);

    descs[0] = (ra_mem_desc_t){ 0x03000000, 0x8000, 0, 0xFF000000, iwram };
    descs[1] = (ra_mem_desc_t){ 0x02000000, 0x40000, 0, 0xFF000000, wram };
    descs[2] = (ra_mem_desc_t){ 0x0E000000, 0x10000, 0, 0xFF000000, sram };
    descs[3] = (ra_mem_desc_t){ 0x06000000, 0x18000, 0, 0xFF000000, vram };

    /* IWRAM: RA 0x000000-0x007FFF -> iwram[0..0x7FFF]. */
    check_read(RA_CONSOLE_GAMEBOY_ADVANCE, 0x000000, 4, descs, 4, iwram, 0, 0xA5);
    check_read(RA_CONSOLE_GAMEBOY_ADVANCE, 0x007FFF, 1, descs, 4, iwram, 0x7FFF, 0xA5);
    /* EWRAM: RA 0x008000-0x047FFF -> wram[0..0x3FFFF]. */
    check_read(RA_CONSOLE_GAMEBOY_ADVANCE, 0x008000, 4, descs, 4, wram, 0, 0x00);
    check_read(RA_CONSOLE_GAMEBOY_ADVANCE, 0x047FFF, 1, descs, 4, wram, 0x3FFFF, 0x00);
    /* SRAM: RA 0x048000-0x057FFF -> sram[0..0xFFFF]. */
    check_read(RA_CONSOLE_GAMEBOY_ADVANCE, 0x048000, 4, descs, 4, sram, 0, 0x5A);
    check_read(RA_CONSOLE_GAMEBOY_ADVANCE, 0x057FFF, 1, descs, 4, sram, 0xFFFF, 0x5A);
    /* Past the end: no region -> 0 bytes. */
    n = ra_read_memory_mapped(RA_CONSOLE_GAMEBOY_ADVANCE, 0x058000, got, 4, descs, 4);
    CHECK(n == 0);
    /* Crossing a region boundary clamps: 0x007FFE + 4 ends at 0x007FFF. */
    n = ra_read_memory_mapped(RA_CONSOLE_GAMEBOY_ADVANCE, 0x007FFE, got, 4, descs, 4);
    CHECK(n == 2);
    CHECK(got[0] == (uint8_t)((0x7FFE & 0xFF) ^ 0xA5));
    CHECK(got[1] == (uint8_t)((0x7FFF & 0xFF) ^ 0xA5));
    /* Unknown console: nothing is mapped. */
    n = ra_read_memory_mapped(0, 0x008000, got, 4, descs, 4);
    CHECK(n == 0);
    /* No descriptors at all: nothing is mapped. */
    n = ra_read_memory_mapped(RA_CONSOLE_GAMEBOY_ADVANCE, 0x008000, got, 4, NULL, 0);
    CHECK(n == 0);
}

/* ------------------------------------------------------------------ */
/* GB / GBC                                                           */
/* ------------------------------------------------------------------ */

static void test_gb(int gbc) {
    static uint8_t rom[0x8000];
    static uint8_t vram[0x4000];
    static uint8_t wram[0x8000];
    static uint8_t sram[0x20000];
    static uint8_t oam[0xA0];
    static uint8_t io[0x80];
    static uint8_t hram[0x80];
    static uint8_t ie;
    uint32_t console = gbc ? RA_CONSOLE_GAMEBOY_COLOR : RA_CONSOLE_GAMEBOY;
    ra_mem_desc_t descs[12];
    size_t num_descs = 0;
    uint8_t got[8];
    uint32_t n;

    fill_pattern(rom, sizeof(rom), 0x11);
    fill_pattern(vram, sizeof(vram), 0x22);
    fill_pattern(wram, sizeof(wram), 0x33);
    fill_pattern(sram, sizeof(sram), 0x44);
    fill_pattern(oam, sizeof(oam), 0x55);
    fill_pattern(io, sizeof(io), 0x66);
    fill_pattern(hram, sizeof(hram), 0x77);
    ie = 0xE1;

    descs[num_descs++] = (ra_mem_desc_t){ 0x0000, 0x4000, 0, 0, rom };
    descs[num_descs++] = (ra_mem_desc_t){ 0x4000, 0x4000, 0x4000, 0, rom };
    descs[num_descs++] = (ra_mem_desc_t){ 0x8000, 0x2000, 0, 0, vram };
    descs[num_descs++] = (ra_mem_desc_t){ 0xC000, 0x1000, 0, 0, wram };
    descs[num_descs++] = (ra_mem_desc_t){ 0xD000, 0x1000, 0x1000, 0, wram };
    descs[num_descs++] = (ra_mem_desc_t){ 0xFE00, 0xA0, 0, 0xFFFFFF60, oam };
    descs[num_descs++] = (ra_mem_desc_t){ 0xFF00, 0x80, 0, 0, io };
    descs[num_descs++] = (ra_mem_desc_t){ 0xFF80, 0x80, 0, 0xFFFFFF80, hram };
    descs[num_descs++] = (ra_mem_desc_t){ 0xFFFF, 1, 0, 0, &ie };
    descs[num_descs++] = (ra_mem_desc_t){ 0xA000, 0x2000, 0, 0, sram };
    descs[num_descs++] = (ra_mem_desc_t){ 0x16000, 0x1E000, 0x2000, 0, sram };
    if (gbc)
        descs[num_descs++] = (ra_mem_desc_t){ 0x10000, 0x6000, 0, 0, wram + 0x2000 };

    /* WRAM bank 0 (fixed) and bank 1 (fixed, not the mapped bank). */
    check_read(console, 0xC000, 4, descs, num_descs, wram, 0, 0x33);
    check_read(console, 0xCFFF, 1, descs, num_descs, wram, 0xFFF, 0x33);
    check_read(console, 0xD000, 4, descs, num_descs, wram, 0x1000, 0x33);
    /* Echo RAM reads the fixed bank 0 (VIRTUAL_RAM, real 0xC000). */
    check_read(console, 0xE000, 4, descs, num_descs, wram, 0, 0x33);
    /* HRAM ("Quick RAM") and OAM. */
    check_read(console, 0xFF80, 4, descs, num_descs, hram, 0, 0x77);
    check_read(console, 0xFFFE, 1, descs, num_descs, hram, 0x7E, 0x77);
    check_read(console, 0xFE00, 4, descs, num_descs, oam, 0, 0x55);
    /* VRAM bank 0 and the ROM (READONLY, exposed CONST). */
    check_read(console, 0x8000, 4, descs, num_descs, vram, 0, 0x22);
    check_read(console, 0x0150, 4, descs, num_descs, rom, 0x150, 0x11);
    /* External RAM bank 0 and the extra banks. */
    check_read(console, 0xA000, 4, descs, num_descs, sram, 0, 0x44);
    check_read(console, 0x16000, 4, descs, num_descs, sram, 0x2000, 0x44);
    check_read(console, 0x33FFF, 1, descs, num_descs, sram, 0x1FFFF, 0x44);
    /* I/O registers are not memory: unmapped. */
    n = ra_read_memory_mapped(console, 0xFF00, got, 4, descs, num_descs);
    CHECK(n == 0);
    /* The unused gap is unmapped. */
    n = ra_read_memory_mapped(console, 0xFEA0, got, 4, descs, num_descs);
    CHECK(n == 0);
    /* GBC-only WRAM banks 2-7: on GB the region is UNUSED (0
     * bytes); on GBC it reads wram[0x2000..]. */
    if (gbc) {
        check_read(console, 0x10000, 4, descs, num_descs, wram, 0x2000, 0x33);
    } else {
        n = ra_read_memory_mapped(console, 0x10000, got, 4, descs, num_descs);
        CHECK(n == 0);
    }
    /* Crossing a region boundary clamps: 0xCFFE + 4 ends at 0xCFFF. */
    n = ra_read_memory_mapped(console, 0xCFFE, got, 4, descs, num_descs);
    CHECK(n == 2);
    CHECK(got[0] == (uint8_t)((0xFFE & 0xFF) ^ 0x33));
    CHECK(got[1] == (uint8_t)((0xFFF & 0xFF) ^ 0x33));
}

static void test_console_ids(void) {
    CHECK(ra_console_id_for_path("game.gb") == RA_CONSOLE_GAMEBOY);
    CHECK(ra_console_id_for_path("GAME.GB") == RA_CONSOLE_GAMEBOY);
    CHECK(ra_console_id_for_path("game.gbc") == RA_CONSOLE_GAMEBOY_COLOR);
    CHECK(ra_console_id_for_path("game.gba") == RA_CONSOLE_GAMEBOY_ADVANCE);
    CHECK(ra_console_id_for_path("game.GBA") == RA_CONSOLE_GAMEBOY_ADVANCE);
    CHECK(ra_console_id_for_path("game.zip") == 0);
    CHECK(ra_console_id_for_path("game") == 0);
    CHECK(ra_console_id_for_path(NULL) == 0);
}

int main(void) {
    test_console_ids();
    test_gba();
    test_gb(0);
    test_gb(1);
    if (g_failures) {
        printf("ra_memory_test: %d FAILURES\n", g_failures);
        return 1;
    }
    printf("ra_memory_test: all checks passed\n");
    return 0;
}
