## Poing! v0.1.0-alpha.7 — GBA cover art: the subtree fix

The seventh signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1, alpha.2, alpha.3, alpha.4, alpha.5 and alpha.6, so it installs straight over alpha.6 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.7.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.7

- GBA cover art should finally load. The GBA index was the whole-repo listing — 7.19 MB, needing about 160 KB/s to finish inside the 45-second budget — and the connection to api.github.com runs slower than that, so GBA always timed out while GB and GBC made it. The index is now just the boxart folder's listing (2.09 MB, 5,961 entries), fetched in two steps, and the budget is 60 seconds — at that size it only needs about 35 KB/s. GB and GBC use the same smaller route now, and their covers already on the phone are untouched.
- What you should see now: open the library and the GBA covers appear on their tiles — Minish Cap, V-Rally 3, Golden Sun. Tap Trace and it should read "index gba loaded (5961 entries)" followed by "art ready" for the GBA games. If it still reads "timed out", the connection to api.github.com is slower than about 35 KB/s and the next step is a different fetch, not another budget tweak.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), and cover art from libretro-thumbnails with a visible fetch status line, Retry, and a fetch trace (Trace button in the status bar).
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains; edge smoothing as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — the fork decision for the core's link API is still open).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.

## Build
