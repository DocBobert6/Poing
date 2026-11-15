## Poing! v0.1.0-alpha.6 — cover art rebuilt from the start

The sixth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1, alpha.2, alpha.3, alpha.4 and alpha.5, so it installs straight over alpha.5 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.6.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.6

- Cover art is rebuilt from the start: one repository is now the single source of truth — three independent per-system pipelines, no global pass, no cap — and the tiles, the status line and the trace all read the same map, so a cover that lands shows on its tile at once.
- A stuck index download can no longer hold the library: the fetch races the network call and abandons it on its budget. Alpha.4's pass was still reading "fetching gba index…" two and a half minutes in with no timeout; a stuck index now reads "timed out" instead, and the other systems' covers land anyway.
- First launch re-verifies every cover file already on the phone: a file that decodes is a cover and shows at once with no download, and a truncated one is deleted and downloaded again. The ten covers that landed on alpha.4 but never showed should now either appear or re-download.
- What you should see now: open the library and the covers appear on their tiles as they land. Tap Trace to see each game's stages (matched → downloaded → decoded → ready); a stuck GBA index reads "timed out" with the GB and GBC covers already visible.

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
