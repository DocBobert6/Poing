## Poing! v0.1.0-alpha.5 — landed covers now show on the tiles

The fifth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1, alpha.2, alpha.3 and alpha.4, so it installs straight over alpha.4 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.5.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.5

- Landed covers now show on the tiles: alpha.4 fixed the fetch — covers landed in seconds — but the tiles kept their placeholders. The decode only ran after the whole fetch pass returned, and the pass waits up to 45 seconds on a slow GBA index after the covers land, while the status line already says they landed. Each cover is now decoded into the tile the moment it lands, so it appears within seconds of the status line saying so — no waiting for the pass to end, no Retry, no scroll.
- What you should see now: open the library and the GB and GBC covers appear on their tiles as they land, while the pass is still running. Tap Trace to see the pass, newest first.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), and cover art from libretro-thumbnails with a visible fetch status line, Retry, and a fetch trace (Trace button in the status bar).
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains; edge smoothing as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — the fork decision for the core's link API is still open).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.

## Build
