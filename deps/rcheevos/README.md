# rcheevos (vendored)

The official RetroAchievements client library, vendored for
Poing! Phase 6, item 8 (RetroAchievements) — the same 12.5.0
vendoring HowlingDog Phase 10 uses.

- **Version:** 12.5.0 (release, 2026)
- **Source:** https://github.com/RetroAchievements/rcheevos
  (tag `v12.5.0`, tarball from codeload.github.com)
- **License:** MIT (see `LICENSE` in this directory)

## What is vendored

The public headers (`include/`) and the library sources needed
for the `rc_client` integration:

- `src/rc_client.c`, `rc_compat.c`, `rc_util.c`, `rc_version.c`
  (+ the private headers `rc_client_internal.h`,
  `rc_compat.h`, `rc_version.h`)
- `src/rcheevos/` — the achievement runtime (triggers,
  conditions, leaderboards, rich presence, memory references)
- `src/rhash/` — game hashing (MD5, disc hash, zip hash; the
  disc hash needs a CD-reader hook supplied by the app — Poing!
  has no discs, so no hook is supplied)
- `src/rapi/` — the RetroAchievements web-API request builders

NOT vendored (not needed for this integration):

- `src/rc_libretro.c` / `rc_libretro.h` — the libretro memory-map
  helper. Poing! supplies its own memory-map read through the
  core's SET_MEMORY_MAPS capture (see `bridge/jni/ra_client.c`):
  the core's maps are the source of truth for GB/GBC/GBA memory,
  and the helper's generic mapping does not fit them.
- `src/rc_client_external.c`, `rc_client_raintegration.c` —
  the Raintegration external-client support.
- `test/`, `validator/`, `.github/` — upstream's tests and CI.

## Build

`bridge/jni/CMakeLists.txt` builds the rcheevos sources into
the `poing-bridge` module (see that file for the source list).
The define `RC_CLIENT_SUPPORTS_HASH` is required — it enables
`rc_client_begin_identify_and_load_game` and the game hashing
used to identify a loaded game. The host test
`bridge/tests/ra_memory_test.c` compiles the same sources with
gcc (`-DRC_CLIENT_SUPPORTS_HASH`); it verifies the memory-map
translation only, not the server calls.

## Updating

To update to a newer rcheevos release, replace `include/` and
`src/` from the new release tarball, keep this README's file
list in sync, and re-run the host test plus CI. Do not edit
the vendored sources — all Poing!-specific behaviour lives
in `bridge/jni/ra_client.c`.
