## Poing! v0.1.0-alpha.20 — The black screen fix

The twentieth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.19, so it installs straight over alpha.19 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.20.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.20

- The black screen on launch is fixed — properly this time. Launching a game still showed black with audio until you pressed a button, even after the alpha.10 fix. The hole in that fix: it only asked for the picture again while a frame was still waiting — but the game asks for landscape from a portrait library, so the screen is destroyed and rebuilt during the rotation, and a rebuild after the waiting frame was already shown found nothing to ask for, so nothing drew. On a still screen (a title screen) no new frame ever arrived to break the silence, so it stayed black until the picture changed — which is what pressing a button does, and what tapping empty space does not. Now every rebuild of the screen asks the game to send its latest frame again, so the picture comes back with no input at all. The same rebuild happens coming back from a phone call, and the fix covers that too.
- Nothing else changed: the haptics (including the alpha.17 pair), the steering and Worm Light tilt tuning, the dead zone, the layout editor minimize, the cheat screen, cover fetching, and RA are untouched.
- What you should see: launch a game and the picture appears on its own, with no press needed — including after rotating or coming back from a phone call.

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
