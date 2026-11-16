## Poing! v0.1.0-alpha.24 — RA chip follows the top bar

The twenty-fourth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.23, so it installs straight over alpha.23 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.24.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.24

- The in-game RetroAchievements chip ("RA 3/12") no longer stays on the whole session. It now follows the top bar: it shows while the bar is up, hides a few seconds into gameplay with it, and comes back with the bar when you summon it with a back swipe. It was built persistent in alpha.22 and sat outside the bar's hide gate — that was the bug.
- The game gets its height back too. The chip sat in the column above the game area, so while it was up it pushed the game down and made it smaller. Now that it hides with the bar, the game re-measures taller, like it already did for the bar itself.
- One accepted consequence: while the bar is hidden, the chip's Retry (on a failed set load) is unreachable in-game — but Pause → Achievements already carries a retry, so that path stays open.
- Nothing else changed: the unlock toast (4 seconds), the tracker line, the chip's own states, rarity tiers, unlock history, cover fetching (including the alpha.21 diagnostic), haptics, steering and Worm Light are untouched.
- What you should see: start a game and a few seconds in the "RA 3/12" chip is gone and the game is full height again; back-swipe and the chip returns with the top bar.

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
