# Poing! v0.1.0-alpha.32 — FPS counter: themed per system, movable in the layout editor

The thirty-second signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.31, so it installs straight over alpha.31 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.32.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.32

The fps counter is now its own overlay item instead of a line in the top bar:

- **Themed per system.** The counter chip takes its colour from the overlay theme for the game you are playing: maroon on Game Boy, Atomic Purple on Game Boy Color, indigo on Game Boy Advance. White text on all three.
- **Movable in the layout editor.** With Dev stats on, open the layout editor and the counter shows as a sample chip you can drag anywhere and resize, like the unlock popup. Its position is saved per system default and per game, like every other control. It starts at the top centre, just below the top bar.
- **Same numbers.** Frame size, core fps, measured fps, frames drawn, the draw-path token (edge, chain, plain — including "plain (edge fell back)") and the live lux reading are all still there, on one line. Nothing about the numbers changed, only where they live.
- **Dev stats still works the same way.** The counter shows only while Dev stats is on (Settings → Player), and a mid-session toggle change still takes effect on relaunch. The top bar still stays visible while Dev stats is on. The top bar itself just no longer carries the numbers.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel, themed fps counter (Dev stats, movable in the layout editor).
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence, in-game RA indicator, per-game library progress, rarity tiers and unlock history with a web API key, live progress bars, Search for Help, closest-first sort.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
