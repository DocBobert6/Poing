# Poing! v0.1.0-alpha.34 — Achievement Center build 3: screenshot on unlock

The thirty-fourth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.33, so it installs straight over alpha.33 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.34.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.34

The last of the three queued Achievement Center builds — one piece:

- **Screenshot on unlock.** Every achievement unlock can now save a screenshot of the moment it happened. Turn it on in Settings → RetroAchievements ("Screenshot on unlock", off by default) and each unlock saves one PNG to Pictures/Poing!, named for the game and the achievement ("Poing-Tetris-Clear_40_Lines-20261119-151234.png"), so a screenshot names the moment it caught. One shot per unlock, of the frame on screen when the popup appears. Off means exactly today's behaviour: no screenshot is taken.
- **No clip.** This is the screenshot half only — there is no video clip of the seconds before an unlock. That would need a new always-recording buffer, and it is not in this build.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel, themed fps counter (Dev stats, movable in the layout editor).
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence, in-game RA indicator, per-game library progress, rarity tiers and unlock history with a web API key, live progress bars, Search for Help, closest-first sort, HUD pin, rarity pop-up variants, screenshot on unlock.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
