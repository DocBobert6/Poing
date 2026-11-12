# Poing!

**Poing!** is a free, open-source standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android — all three systems in one app, around the mGBA libretro core.

**Status: Phase 1** — the mGBA core is imported at a pinned commit and builds green in CI for arm64-v8a. No games can be played yet; the player, library and era UI land in later phases (see the plan in `~/workspace/poing/PLAN.md`).

- Package: `app.poing`
- Repo: https://github.com/DocBobert6/Poing
- Core: mGBA, pinned at `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` (libretro/mgba HEAD, 2026-09-16, "Libretro: Disable debug logging noise"). The core tree is imported verbatim at the repo root, with two docs-only exceptions, both recorded here: this file replaces upstream mGBA's top-level `README.md` (the upstream readme is recoverable from the pinned SHA; upstream documentation remains in `doc/`), and `.gitignore` carries the Poing! frontend entries appended under a marked comment. No core code is changed.
- Frontend licence: GPL-3.0 (see `frontend/LICENSE`). Core licence: MPL-2.0 (see `NOTICE` and the root `LICENSE`).
- Target: arm64-v8a only, Pixel 10a first. CI is the compile path.

## What to supply (user-supplied, never bundled)

- Games: `.gb`, `.gbc`, `.gba` (or `.zip` containing one). No ROMs, games, BIOS files, or download links are in this repo or in the app, ever.
- BIOS (optional, never required): `gb_bios.bin`, `gbc_bios.bin`, `gba_bios.bin`. mGBA boots on its built-in HLE BIOS without them.

Note on the upstream tree: it contains test-suite ROMs under `cinema/` (blargg / acid test fixtures, e.g. `test.gb` files used by mGBA's own test harness). They are upstream test fixtures, not commercial games, and they are never bundled into the APK — the APK carries only the compiled core library. No BIOS binaries are anywhere in the tree.

## Era

1996–2001, Pocket + Atomic hybrid: Pocket grey/silver theme chrome, full-screen library (never shoved inside a depicted Pocket screen), translucent Atomic Purple menus and settings sheets only.

## Display

Per-system display chains, custom-built, parameters never disclosed: GB **Jade**, GBC **Prism**, GBA **Dusk**. Edge smoothing lands last, and only if it beats xBR / ScaleFX in a side-by-side on real game frames.

## Building

CI (`.github/workflows/android.yml`) builds the stock core for arm64-v8a with CMake (`-DLIBMGBA_ONLY=ON -DBUILD_LIBRETRO=ON`, NDK 27.2.12479018), verifies the `.so` is an ARM64 ELF with all `retro_*` entry points exported, runs the unit tests and the Robolectric startup gate, checks no app method exceeds 255 registers, and assembles a debug APK (CI artifact only — no releases in Phase 1, no keystore yet).

## Disclaimer

Poing! is not affiliated with or endorsed by Nintendo.
