# Poing! v0.1.0-alpha.30 — Settings in their right menus + Grid "Remove" no longer wraps

The thirtieth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.29, so it installs straight over alpha.29 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.30.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.30

Two fixes from the UI screenshot audit, both small and both in the interface only:

- **Settings sit in their correct menus now.** The Rewind control and the Dev stats row were sitting inside the RetroAchievements section, where neither belongs. Rewind has moved to the Player section, beside the fast-forward rows; Dev stats has moved to Player too, beside Input display. The RetroAchievements section now holds only the login/Account rows and the web API key. Nothing changed meaning, default, or storage — this is a move only, so your settings are exactly where you left them.
- **"Remove" no longer wraps in Grid view.** In a grid cell the three action links (Cover / Saves / Remove) were too wide for the narrowest cell, so "Remove" broke across two lines as "Remo" / "ve". The three links are now compact single-line labels that fit the cell, so all three stay visible on one line. The List rows have room and were already fine — they are untouched.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence, in-game RA indicator, per-game library progress, rarity tiers and unlock history with a web API key.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
