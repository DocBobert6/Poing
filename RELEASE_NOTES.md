## Poing! v0.1.0-alpha.14 — Joystick dead zone

The fourteenth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.13, so it installs straight over alpha.13 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.14.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.14

- Joystick dead zone, adjustable at last: the stick's dead zone was a fixed 22% of full travel — about 16dp from centre on a 144dp stick — and the nub moves from the moment you touch it, so in racing games the zone felt non-existent. There is now a "Joystick dead zone" slider in Settings → Player, beside "Left control": 0 to 50 percent in steps of 5, 22 by default (the old feel, unchanged on install). 0 is raw — no dead zone at all, like the real D-pad. A bigger zone means a wider neutral area in the middle of the stick, which is what steering in a racing game wants.
- Per-game override: a game can set its own dead zone in the layout editor — a stepper in 5% steps plus a Global pill, shown only while the stick is the control in the slot. Per-game wins over the global slider; Global follows the slider again. Reset in the editor is layout-only and never touches the dead-zone choice.
- Nothing else about the stick changed: below the zone nothing, above it the exact same diagonal rule, the same direction mask, the same nub tracking and snap-back. The dead zone is a pure gate — no rescaling. The D-pad keeps its own fixed dead zone, untouched.
- What you should try: set the slider to 30 for a racing game and see how the steering feels; then set a per-game override on that game and confirm it sticks after a relaunch. Above the zone, the stick should behave exactly as it did before.
- One fix on the way: the first build went red on a single editor test (the compose rule rejects a second setContent in one test) — split the test, no production code changed.

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
