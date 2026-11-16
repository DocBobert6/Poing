## Poing! v0.1.0-alpha.11 — Haptics realism, Worm Light aim tilt

The eleventh signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.10, so it installs straight over alpha.10 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.11.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.11

- Haptics realism: every button now feels like its own hardware. A real rubber-dome button has two tactile events — the dome collapsing on press and springing back on release — and the D-pad is a rocker, so the phone now plays both. A/B keep the full press click; Start and Select click lighter; rolling the D-pad (or the joystick) onto a new direction ticks, lighter than the press, while holding one direction steady fires nothing; every button ticks lightly on release, always lighter than its press; GBA L/R are a click plus a low thud a beat later, one thunk for the longer travel. Each effect is probed at runtime and falls back to the plain click where the vibrator does not play it. The strength slider and the haptics toggle still silence everything, and a game's cartridge rumble still wins while it is running.
- Worm Light aim tilt, fixed: in aim mode the forward/back tilt was dead on device — the angle spun, but the pool never stretched. The tilt needed a sweep far wider than a thumb can make. It now runs over a thumb's orbit: no tilt only with the finger right on the lens, full tilt at a comfortable orbit out from it, so dragging out along a bearing visibly stretches the pool further along that bearing, and dragging back in pulls it back. The angle still spins the same way, and the pool centre stays on the head.
- What you should feel now: a lighter click on Start and Select; a light tick when you roll the D-pad or stick to a new direction, and a lighter tick still when you lift any button; a click-plus-thud on L and R. And in Worm Light aim mode, dragging your thumb out from the lamp stretches the pool forward along the bearing you are pointing.

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
