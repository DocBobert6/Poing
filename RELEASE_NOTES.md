## Poing! v0.1.0-alpha.13 — Envelope click

The thirteenth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.12, so it installs straight over alpha.12 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.13.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.13

- Envelope click: the press click (A/B and Start/Select) is now built with Android 16's envelope API where the device supports it — an instant attack of about 15ms total, with the sharpness placed at the actuator's own resonant frequency from the device's frequency profile. It is an alternative press click, not a claim of a better one: whether it feels better than the current click is for your device to judge, and this build is the way to judge it.
- Graceful fallback: the device is probed at runtime. Where envelope effects are not supported, the press falls back to the current click, exactly as before. And if an envelope the device claims to support will not build, the plain click is the fallback there too — a button always clicks, never goes silent.
- Nothing else about haptics changed: the release tick, the roll tick, the shoulder click+thud, the strength slider and all the wiring are untouched. The envelope click is the press only.
- What you should try: press the buttons as usual and compare the feel against alpha.12. If the envelope click feels better, it stays; if not, it goes.

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
