# Poing! v0.1.0-alpha.29 — Surface-change replay (black screen, second cause)

The twenty-ninth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.28, so it installs straight over alpha.28 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.29.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.29

- The black screen's second cause is fixed. Your trace (DW 3, alpha.28) showed the picture drawing after the surface was created — and then the surface changing size when the top bar appeared, which throws the drawn picture away: a size change rebuilds the surface, and on a still screen nothing re-drew into the new one, so it stayed black until a button press changed the picture. Now, whenever the surface changes size, the game re-delivers its latest frame and redraws into the new surface — the same replay the alpha.20 fix does when a surface is created. A rotation settling or the top bar appearing should no longer strand a black screen, and the button-press workaround should no longer be needed.
- The display trace stays on for verification: if the screen ever stays black again, turn the trace on in Settings → Display, reproduce it, then Pause → Trace and screenshot it. A size change now shows a "surface ready" event right after the "surface changed" event — that is the replay happening.
- Nothing else changed: the alpha.20 creation replay is exactly as it was, the dirty check and the frame backpressure are untouched, and the trace recorder, viewer and toggle are untouched.

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
