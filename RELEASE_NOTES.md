## Poing! v0.1.0-alpha.19 — The small UI pair

The nineteenth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.18, so it installs straight over alpha.18 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.19.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.19

- The layout editor's settings panel collapses. A Hide/Show settings toggle under the action row folds the panel down to a slim bar, so the settings stop covering the whole screen while you drag the controls around; the action row (Back / Done / Reset / Snap / Together) stays visible throughout, and the controls stay draggable while minimized. It is session-only — the editor always opens expanded, and collapsing never touches a saved layout.
- The cheat screen uses the detected system. It used to take the system from the file extension, so a GBC game in a `.gb` file got GB cheat formats; it now takes the system the core actually loaded (the header-detected one the library already knows), so that game gets GBC cheat formats.
- Nothing else changed: the haptics (including the alpha.17 pair), the steering and Worm Light tilt tuning, the dead zone, the black-screen path, cover fetching, and RA are untouched.
- What you should see: in the layout editor, tap Hide settings and the panel folds away to a slim bar with the controls free to drag; tap Show settings and it comes back. In the cheat screen for a GBC game in a `.gb` file, the formats are GBC ones.

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
