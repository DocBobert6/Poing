## Poing! v0.1.0-alpha.23 — RetroAchievements rarity + history

The twenty-third signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.22, so it installs straight over alpha.22 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.23.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.23

- RetroAchievements now shows how rare each achievement is. Pause → Achievements carries a rarity tier beside every achievement once it loads — "Rare · 12%", "Common · 50%", "Legendary · 0.4%" — from the RetroAchievements web API (how many players earned it, out of how many played). The tiers are labels in the existing gold text, not a new colour system.
- The Achievement Center now shows your full unlock history, newest first with dates, under "Unlock history" — the whole account's unlocks, not just the ones earned in Poing. The local "Recent unlocks" above it stays as it is.
- Both need your RetroAchievements web API key, which is new and optional: Settings → RetroAchievements → "Web API key (optional)". It comes from your account settings on retroachievements.org, is stored on this device only like the login token, is never shown in full after you enter it (just the first and last few characters), and can be removed at any time. Without a key, the hub keeps showing exactly what it already showed — no error, no nag. This build does not need your key: add your own on the device.
- Nothing else changed: the RA login, the in-game indicator, the unlock toast, badges, rich presence, cover fetching (including the alpha.21 diagnostic), haptics, steering and Worm Light are untouched.
- What you should see: after adding your key, open a game and Pause → Achievements shows a rarity beside each achievement; open the Achievement Center from the library and your unlock history is listed newest first.

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
