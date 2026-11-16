## Poing! v0.1.0-alpha.28 — Black-screen trace

The twenty-eighth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.27, so it installs straight over alpha.27 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.28.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.28

- There is now a display trace for diagnosing the black screen. Turn it on in Settings → Display ("Display trace", off by default), then start a game: it records the display path with timestamps — session start, first frame, surface created / changed, surface ready (with whether a frame was pending and whether the last frame was consumed), the first frame drawn after a surface is created, pause / resume, and orientation.
- When the black screen bites, open the pause menu and tap "Display trace". The record reads newest first, and it survives the session, so you can screenshot what the display path did — in particular whether the surface was ever created, and whether a frame ever drew after it.
- This build only diagnoses: the trace records, it changes nothing about how the picture draws. The fix for whatever it catches follows as its own build.
- Nothing else changed: the D-pad envelope split, haptics, RA, covers, and all the wiring are untouched.
- What you should do: turn the trace on, play until the screen stays black before a button press, then pause and screenshot the trace.

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
