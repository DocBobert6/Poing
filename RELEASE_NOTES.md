## Poing! v0.1.0-alpha.4 — cover-art stall fix

The fourth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1, alpha.2 and alpha.3, so it installs straight over alpha.3 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.4.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.4

- Cover art no longer stalls: the trace caught the real bug — the 45-second budget bounded only the download, while the pass waited on an unbounded join for the slowest index, so a stuck GBA index held the whole pass (and every cover) forever. The join is now bounded by one budget, a stalled index is named "timed out" in the trace, and covers for systems whose index is already ready land at once instead of waiting on the slowest one. Two smaller races found along the way are fixed too: concurrent index events could lose a system's status entry, and a timed-out index could be re-reported as "cancelled".
- What you should see now: GB and GBC covers land within seconds of opening the library; the GBA index either loads and its covers follow, or it reads "index gba failed: timed out" at about 45 seconds with the pass ending — never a minutes-long stall with 0 landed. Tap Trace in the library's status bar to see the pass, newest first.

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
