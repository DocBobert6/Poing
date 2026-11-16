## Poing! v0.1.0-alpha.12 — Screen Optics everywhere, deeper dimming

The twelfth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.11, so it installs straight over alpha.11 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.12.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.12

- Screen Optics everywhere: the room light's brightness, contrast and gamma used to ride only on the chains and on edge smoothing with the grid. Edge smoothing without the grid, and plain with no chain at all, never got them — the final blit is the plain program, which carried the Worm Light pool but no optics. The plain program now applies the same optics as the grid passes, before the pool, so the lamp still sits on top of the room light instead of being cancelled by it. The chains and edge-with-grid are untouched.
- Deeper dimming: in a dark room with the dimming toggle on, the picture now falls to about 0.35 brightness at pitch black, instead of 0.60. Same threshold and same ease-in as before — only the floor is deeper. The bright-room boost is unchanged.
- What you should see now: with edge smoothing on and the grid off, the picture brightens in a bright room and dims in a dark room (with the toggle on) just like it already did on the chains; and in a dark room with dimming on, the picture gets properly dark.

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

## Build
