# Poing!

**Poing!** is a free, open-source standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android — all three systems in one app, around the mGBA libretro core. No ads, no paid version.

**Status: first signed alpha — v0.1.0-alpha.1.** The app is playable end to end (library, player, saves, RetroAchievements, cover art, in-app updates). The alpha is signed with a fresh release key, so it does **not** install over the debug builds — uninstall the debug build first (which wipes local saves; RetroAchievements progress is server-side and safe). The link cable (Phase 7) is still undecided: phone-to-phone over the local network is the v1 scope, and single-device self-link was proven not feasible, so the fork decision for exposing the core's link API is still open.

- Package: `app.poing`
- Repo: https://github.com/DocBobert6/Poing
- Core: mGBA, pinned at `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` (libretro/mgba HEAD, 2026-09-16, "Libretro: Disable debug logging noise"). The core tree is imported verbatim at the repo root, with two docs-only exceptions, both recorded here: this file replaces upstream mGBA's top-level `README.md` (the upstream readme is recoverable from the pinned SHA; upstream documentation remains in `doc/`), and `.gitignore` carries the Poing! frontend entries appended under a marked comment. No core code is changed.
- Frontend licence: GPL-3.0 (see `frontend/LICENSE`). Core licence: MPL-2.0 (see `NOTICE` and the root `LICENSE`). Every other library compiled into the app — rcheevos (MIT), inih (BSD-3-Clause), the libretro API header (MIT-style), the fonts (SIL OFL 1.1), the Kotlin/AndroidX runtime (Apache 2.0) — is listed with its full text in `NOTICE`, shown in the app on the Licences screen.
- Target: arm64-v8a only, Pixel 10a first. CI is the compile path.

## What to supply (user-supplied, never bundled)

- Games: `.gb`, `.gbc`, `.gba` (or `.zip` containing one). No ROMs, games, BIOS files, or download links are in this repo or in the app, ever.
- BIOS (optional, never required): `gb_bios.bin`, `gbc_bios.bin`, `gba_bios.bin`. mGBA boots on its built-in HLE BIOS without them.

Note on the upstream tree: it contains test-suite ROMs under `cinema/` (blargg / acid test fixtures, e.g. `test.gb` files used by mGBA's own test harness). They are upstream test fixtures, not commercial games, and they are never bundled into the APK — the APK carries only the compiled core library. No BIOS binaries are anywhere in the tree.

## Era

1996–2001, Pocket + Atomic hybrid: Pocket grey/silver theme chrome, full-screen library (never shoved inside a depicted Pocket screen), translucent Atomic Purple menus and settings sheets only.

## What is in the app

- **Library:** import games from storage, per-game rows with system badges, remove per row, a BIOS status card (BIOS files are optional and never gated), and cover art fetched from libretro-thumbnails — Wi-Fi by default, with a mobile-data opt-in in Settings, and a visible fetch status line on the library (gate / index / error) with a Retry button.
- **Player:** touch controls and gamepad, pause menu, save states with thumbnails plus auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game saved control positions, playtime tracking with a Save Manager, toggleable input display, haptics, cartridge sensors (rumble, tilt, solar, camera), screenshot + video capture, and a Cartridge Surgery panel (clock, sensors, save tools).
- **RetroAchievements:** log in once from Settings; an Achievement Center in the library shows the username, softcore points, recent unlocks with badges and per-game progress; badges are downloaded and cached on disk. Leaderboards and rich presence are on.
- **Display:** per-system display chains, custom-built, parameters never disclosed: GB **Jade**, GBC **Prism**, GBA **Dusk**. Edge smoothing is an alternative to the chains, never stacked with them. A shader-only reconstruction engine lives behind a hidden Developer option (seven taps on the Settings version footer), off by default, as an experiment — it is not the shipped look.
- **Updates:** Settings → Updates checks the public GitHub releases and, on the user's tap only, downloads the APK and hands it to the system installer. Nothing downloads or installs without a tap, and a launch check only reads. The same key signs every release, so an update installs over the previous install.
- **Settings:** core options (the audited mGBA list, including colour correction and BIOS use/skip), per-system display and control defaults, and the Developer menu.

## What is NOT in the app

- Link cable (Phase 7, undecided — see Status above).
- No ROMs, BIOS files, or download links, ever. No Nintendo branding, and Poing! is not affiliated with or endorsed by Nintendo.

## Building

CI (`.github/workflows/android.yml`) builds the stock core for arm64-v8a with CMake (`-DLIBMGBA_ONLY=ON -DBUILD_LIBRETRO=ON`, NDK 27.2.12479018), verifies the `.so` is an ARM64 ELF with all `retro_*` entry points exported, builds the JNI bridge (which links rcheevos for RetroAchievements), runs the unit tests and the Robolectric startup gate, checks no app method exceeds 255 registers, and assembles a debug APK (CI artifact). A push of a `v*` tag additionally builds the signed release APK: the keystore is restored from CI secrets, the release is assembled with it, and its signature and version are verified in CI before the GitHub release is created from that artifact. The release keystore was minted 2026-11-15, fresh for Poing! (never the HowlingDog, Mariner or MainMenu signer); it is not in the repo — CI restores it from secrets and the backup lives outside the repo. The same key signs every release, so an update installs over the previous install. Local Gradle does not run in this sandbox; CI is the verification path.

The plan, with the running build record, lives in `~/workspace/poing/PLAN.md` (§15); the remote tip after each landed build is recorded in `remote-tip.txt`.

## Disclaimer

Poing! is not affiliated with or endorsed by Nintendo.
