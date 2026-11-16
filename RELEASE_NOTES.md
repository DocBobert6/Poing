## Poing! v0.1.0-alpha.15 — Haptics retune

The fifteenth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.14, so it installs straight over alpha.14 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.15.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.15

- Haptics retune, from the alpha.13 device verdict: the envelope click is good on Start/Select and worse than the old click on A/B. So A/B are back on the alpha.12 click (the scaled click primitive, no envelope) and Start/Select keep the envelope, unchanged.
- The D-pad and the stick try the envelope on a direction change — a rocker rolling onto a new dome, not a button press — tuned softer than Start/Select's (the resonant sharpness pulled down a quarter) and landing slightly stronger than the old tick. Where the device has no envelopes the roll falls back to the tick, whose scale rose 0.5 to 0.6 — clearly present, still obviously below A/B's full click, because the rocker really is softer than a face button.
- L/R take the envelope too — only the click part. The thud stays exactly as it is (a low thud at 0.3x, 20ms after the click starts), fired once the ~15ms envelope click has finished, so it still lands as one thunk. Without the thud primitive it is just the envelope click; without primitives at all it is the plain click.
- Nothing else changed: the release tick, the strength slider, and all the haptics wiring are untouched.
- What you should try: A/B should feel like alpha.12 again; Start/Select still the envelope; D-pad and stick rolls slightly stronger — and the verdict that matters is whether the envelope suits the rocker. L/R should click then thunk, as before.
- One fix on the way: the fold-in run went red on a single compile error (a new enum declared inside the companion object is not reachable as `Haptics.ShoulderClickRoute` — the same trap as the alpha.11 TickEffectKind red run). Moved to class level, no behaviour change.

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
