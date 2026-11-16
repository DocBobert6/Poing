## Poing! v0.1.0-alpha.18 — The tuning pair

The eighteenth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.17, so it installs straight over alpha.17 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.18.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.18

- Proportional steering is retuned. The duty now follows the deflection squared instead of linearly, so a slight push counts for much less than it did; and the fractional remainder carries forward across periods instead of rounding per period, so a tiny push gives one full-steering frame every N periods rather than nothing-or-a-sixth. The 6-frame period is unchanged, full deflection still holds continuously, and the D-pad is untouched.
- The Worm Light tilt is less sensitive. The tilt distance range widens from 12–64dp to 12–96dp: a 50dp orbit now tilts 0.45 instead of 0.73, and full tilt needs a deliberate 96dp sweep — still inside a thumb's reach, no longer hit by accident. The 12dp floor is unchanged: a finger on the lens still does not tilt.
- Nothing else changed: the haptics (including the alpha.17 pair), the dead zone, the editor, the black-screen path, cover fetching, cheats, and RA are untouched.
- What you should feel: in a racing game, a slight stick push should steer gently instead of grabbing; aiming the Worm Light should need a wider orbit to tilt fully.

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
