## Poing! v0.1.0-alpha.2 — cover-art timeout fix, APK attached

The second signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1, so it installs straight over alpha.1 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.2.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.2

- Cover art no longer stalls on "fetching... 0 landed": every download now has a total budget (45s for an index, 20s for a cover), the three indexes load in parallel so one slow system no longer blocks the others, the status line names the stage it is on (e.g. "fetching gba index…") and the failure when one happens, and Retry cancels a hung download first.
- The release itself is now built and attached by the CI workflow, so the APK is on this page instead of a CI artifact link (alpha.1's upload returned 401 from outside Actions).

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains; edge smoothing as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — the fork decision for the core's link API is still open).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.

## Build
