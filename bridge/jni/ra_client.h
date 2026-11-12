/* Poing! Phase 6 (item 8) — RetroAchievements client wrapper.
 *
 * Pure C, no JNI and no Android dependencies: the app-specific
 * pieces (the HTTP request, the unlock event, the async result)
 * are callbacks supplied by the bridge (bridge/jni/bridge.cpp),
 * which performs them through JNI into Kotlin. The host test
 * (bridge/tests/ra_memory_test.c) supplies stubs for the same
 * callbacks, so the memory mapping is fully testable on the host
 * with mock memory maps.
 *
 * Adapted from HowlingDog Phase 10's ra_client (same rcheevos
 * 12.5.0): the lifecycle, server plumbing, JSON builders, login,
 * load, leaderboards and rich presence are the same shape. What
 * differs is the memory map and the consoles:
 *
 * The memory map (the main technical piece): GB/GBC/GBA DO have
 * rcheevos regions (rc_console_memory_regions), and the core
 * hands the bridge its own memory maps (RETRO_ENVIRONMENT_
 * SET_MEMORY_MAPS, captured by the bridge — the core's array is
 * stack memory and dies after the call). The RA read translates
 * through both: an RA address finds its rcheevos region, the
 * region's real_address (the hardware address) finds the core's
 * map descriptor, and the descriptor's ptr + offset gives the
 * live core memory. The maps line up exactly with rcheevos's
 * tables (verified against the pinned tree,
 * src/platform/libretro/libretro.c _setupMaps):
 *   - GBA: IWRAM 0x03000000, EWRAM 0x02000000, SRAM 0x0E000000,
 *     VRAM 0x06000000. The wram buffer is one 0x48000 allocation
 *     (EWRAM + IWRAM) — the maps handle it, so this layer never
 *     trusts retro_get_memory_size(SYSTEM_RAM), which returns
 *     GB_SIZE_WORKING_RAM (0x8000) for both platforms.
 *   - GB: WRAM bank 0 at 0xC000, bank 1 at 0xD000 (fixed bank 1,
 *     not the currently mapped bank), HRAM at 0xFF80, OAM at
 *     0xFE00, VRAM at 0x8000 (bank 0 only), external RAM at
 *     0xA000 (bank 0) plus extra banks at 0x16000, GBC-only WRAM
 *     banks 2-7 at 0x10000.
 * Readable region types are SYSTEM_RAM, SAVE_RAM, VIDEO_RAM,
 * VIRTUAL_RAM (echo RAM, whose real address is 0xC000) and
 * READONLY (the ROM, exposed CONST by the maps — stable data,
 * and excluding it could only disable achievements). UNUSED and
 * HARDWARE_CONTROLLER (I/O registers, the interrupt vector)
 * read as unmapped (0 bytes), which makes rc_client disable the
 * achievements that reference them rather than read garbage.
 * A read is clamped at the end of the region it starts in and
 * at the end of the descriptor it reads from: it never crosses
 * from one region or one descriptor into the next.
 *
 * Hardcore mode is OFF (the default): Poing! is built around
 * save states, which hardcore forbids. Achievements unlock in
 * softcore. Leaderboards and rich presence are wired up (v1
 * scope, same as HowlingDog v1.2.0): submission is automatic
 * through the same async HTTP plumbing, and the presence is sent
 * by rc_client's periodic ping — the app only shows what
 * happened.
 *
 * No CD reader (no discs), no cheat-write (Poing!'s cheats go
 * through retro_cheat_set, Phase 6 item 3) — both HowlingDog
 * pieces are dropped here.
 */

#ifndef RA_CLIENT_H
#define RA_CLIENT_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Console ids (rcheevos include/rc_consoles.h). */
#define RA_CONSOLE_GAMEBOY 4
#define RA_CONSOLE_GAMEBOY_ADVANCE 5
#define RA_CONSOLE_GAMEBOY_COLOR 6

/* Event types forwarded to the app (rcheevos
 * RC_CLIENT_EVENT_*). Only the two below are forwarded. */
#define RA_EVENT_ACHIEVEMENT_TRIGGERED 1
#define RA_EVENT_LEADERBOARD_STARTED 2
#define RA_EVENT_LEADERBOARD_FAILED 3
#define RA_EVENT_LEADERBOARD_SUBMITTED 4
#define RA_EVENT_LEADERBOARD_TRACKER_SHOW 10
#define RA_EVENT_LEADERBOARD_TRACKER_HIDE 11
#define RA_EVENT_LEADERBOARD_TRACKER_UPDATE 12
#define RA_EVENT_LEADERBOARD_SCOREBOARD 13
#define RA_EVENT_GAME_COMPLETED 15

/* One captured memory-map descriptor (RETRO_ENVIRONMENT_
 * SET_MEMORY_MAPS, copied by the bridge). ptr points at live
 * core memory and is never owned here; offset is where start
 * maps within ptr. select is 0 when the descriptor matches by
 * range only. */
typedef struct {
    uint32_t start;
    uint32_t len;
    uint32_t offset;
    uint32_t select;
    const uint8_t* ptr;
} ra_mem_desc_t;

/* The core's captured memory maps, supplied by the bridge
 * (which reads them from the SET_MEMORY_MAPS environ call) or
 * by a stub in the host test. NULL / 0 when no game is loaded. */
typedef void (*ra_memory_map_provider_fn)(const ra_mem_desc_t** descs, size_t* num_descs, void* ctx);

/* A server call the app must perform asynchronously (the
 * RetroAchievements web API). The app later delivers the
 * response with ra_http_response(request_id, ...). */
typedef void (*ra_server_call_fn)(int request_id, const char* url, const char* post_data, void* ctx);

/* An achievement event (unlock, game completed). */
typedef void (*ra_event_fn)(int type, uint32_t achievement_id, const char* title, const char* description, uint32_t points, void* ctx);

/* A leaderboard event (tracker show/hide/update, attempt
 * started/failed/submitted, scoreboard). The payload is a JSON
 * string (NULL when the event carries none): the tracker events
 * carry {"display":"..."} and the scoreboard carries
 * {"submitted_score","best_score","new_rank","num_entries",
 * "top":[{"username","rank","score"}]}. The wrapper builds the
 * JSON and frees it after the callback returns, so the app must
 * copy anything it wants to keep. Registered separately from
 * ra_init (see ra_set_leaderboard_event_handler) so the
 * achievement path stays untouched. */
typedef void (*ra_leaderboard_event_fn)(int type, uint32_t leaderboard_id, const char* json, void* ctx);

/* An async JSON result (a leaderboard entries fetch). json is
 * NULL on failure (result != 0); on success it is the entries
 * object, and the app must copy anything it wants to keep — the
 * wrapper frees it after the callback returns. */
typedef void (*ra_json_result_fn)(int result, const char* error_message, const char* json, void* ctx);

/* An async result (login, game load). result is 0 (RC_OK) on
 * success, non-zero on failure; error_message may be NULL. */
typedef void (*ra_result_fn)(int result, const char* error_message, void* ctx);

/* Console id for a game file, by extension. 0 when the file is
 * not a RetroAchievements game for this frontend (the caller
 * keeps RA inactive for it). The library extracts .zip imports
 * to the contained ROM before the player ever sees them, so no
 * .zip case is needed here. */
uint32_t ra_console_id_for_path(const char* path);

/* The RA address -> core memory translation (the memory maps).
 * Returns the number of bytes read (0 = unmapped). Pure and
 * host-testable: the maps are passed in, not read globally.
 * See the header comment for the region rules and the clamping. */
uint32_t ra_read_memory_mapped(uint32_t console_id, uint32_t address, uint8_t* buffer, uint32_t num_bytes,
                               const ra_mem_desc_t* descs, size_t num_descs);

/* Lifecycle. Returns 0 on success. The callbacks are the
 * bridge's JNI implementations (or stubs in the host test). */
int ra_init(ra_memory_map_provider_fn map_provider, void* map_ctx,
            ra_server_call_fn server_call, void* server_ctx,
            ra_event_fn event_cb, void* event_ctx);
void ra_shutdown(void);
int ra_is_ready(void);

/* Login. Password login is the only login the app offers: the
 * user types their RetroAchievements username and website
 * password, the server hands back a session token, and the app
 * stores the token (never the password) for the next launch,
 * which re-logs in with it. Async: the result is delivered
 * through result_cb. */
int ra_login_with_password(const char* username, const char* password, ra_result_fn result_cb, void* result_ctx);
int ra_login_with_token(const char* username, const char* token, ra_result_fn result_cb, void* result_ctx);
void ra_logout(void);
int ra_is_logged_in(void);
const char* ra_logged_in_username(void);
/* The session token of the logged-in user (NULL when logged
 * out) — the token the password login returned, for the app to
 * store and re-log in with on the next launch. */
const char* ra_logged_in_token(void);

/* Load a game (hash it and fetch its achievement set). Async:
 * the result is delivered through result_cb. console_id must
 * be RA_CONSOLE_GAMEBOY, RA_CONSOLE_GAMEBOY_ADVANCE or
 * RA_CONSOLE_GAMEBOY_COLOR. */
int ra_load_game(uint32_t console_id, const char* file_path, ra_result_fn result_cb, void* result_ctx);
void ra_unload_game(void);
int ra_is_game_loaded(void);
const char* ra_game_title(void);

/* Per-frame evaluation — call once per displayed frame, only
 * while a game is loaded. The Kotlin loop calls it once after
 * the displayed frame. */
void ra_do_frame(void);

/* Reset the runtime (all achievements back to their initial
 * state) — used when a save state is loaded without an RA
 * sidecar, so the progress matches the rewound game state. */
void ra_reset(void);

/* Progress (the save-state sidecar). ra_progress_size is 0
 * when no game is loaded. */
size_t ra_progress_size(void);
int ra_serialize_progress(uint8_t* buffer, size_t buffer_size);
int ra_deserialize_progress(const uint8_t* buffer, size_t buffer_size);

/* The achievements list as a JSON array string (malloc'd; the
 * caller frees it). NULL when no game is loaded. Each entry:
 * id, title, description, points, state (0 inactive, 1 active,
 * 2 unlocked, 3 disabled), unlocked (0/1), badge_url, rarity,
 * measured_progress, measured_percent. */
char* ra_get_achievements_json(void);

/* Deliver an HTTP response for a pending server call (called by
 * the bridge when the app's HTTP request finishes). */
void ra_http_response(int request_id, const char* body, size_t body_length, int http_status_code);

/* Leaderboards. The list is the loaded game's boards as a JSON
 * array string (malloc'd; the caller frees it). NULL when no
 * game is loaded. Each entry: id, title, description, format
 * (0 time, 1 score, 2 value), lower_is_better (0/1), state,
 * tracker_value ("" when the board is not being attempted). The
 * entries fetch is async: around_user fetches the entries
 * around the logged-in user, otherwise the top entries (10,
 * from the top); the result is delivered through result_cb,
 * exactly once. Returns -1 when no game is loaded (the
 * callback is not called). */
void ra_set_leaderboard_event_handler(ra_leaderboard_event_fn fn, void* ctx);
int ra_has_leaderboards(void);
char* ra_get_leaderboards_json(void);
int ra_fetch_leaderboard_entries(uint32_t leaderboard_id, int around_user, ra_json_result_fn result_cb, void* ctx);

/* Rich presence. The presence string is sent to the server
 * automatically by rc_client's periodic ping (driven by
 * ra_do_frame), so the app never sends it by hand — these only
 * read the current state, for display. Returns 0 (and an empty
 * message) when the game has no rich presence. */
int ra_has_rich_presence(void);
size_t ra_get_rich_presence(char* buffer, size_t buffer_size);

#ifdef __cplusplus
}
#endif

#endif /* RA_CLIENT_H */
