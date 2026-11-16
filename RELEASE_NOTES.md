## Poing! v0.1.0-alpha.22 — The RetroAchievements display pair

The twenty-second signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.21, so it installs straight over alpha.21 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.22.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.22

- RetroAchievements now tells you *why* a game has no achievements, instead of showing the same line for every case. Pause → Achievements distinguishes three outcomes: an unrecognised ROM ("RetroAchievements doesn't recognise this ROM, so it has no achievement set"), a failed load ("couldn't load this game's achievements", with a Retry button), and a genuinely empty set ("This game has no achievements").
- A failed set load is no longer silent during gameplay: a small gold RA chip under the top bar shows the loaded game's state while you play — "RA 3/12" when the set is in, "RA: unrecognised ROM" when there is no set, "RA: load failed — Retry" when the load failed (tap the chip to retry). It hides while paused and when logged out, like the other RA notes.
- The unlock toast is richer: it now shows the achievement's description under its title and points, as the Achievement Center already does.
- The library now shows per-game achievement progress ("Achievements 3/12") on each row and grid cell, from the Achievement Center's cached sets. A game that has never loaded a set shows nothing — never a misleading 0/12.
- Nothing else changed: the haptics, the steering and Worm Light tilt tuning, the dead zone, the layout editor minimize, the cheat screen, cover fetching (including the alpha.21 diagnostic), and RA login are untouched.
- What you should see: play a game with achievements and the RA chip shows its progress under the top bar; open a ROM hack or an unrecognised game and Pause → Achievements says plainly that RA doesn't recognise it.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
