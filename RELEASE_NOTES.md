## Poing! v0.1.0-alpha.10 — D-pad highlight, joystick, black-screen fix, moveable FF/RW, Worm Light

The tenth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.9, so it installs straight over alpha.9 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.10.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.10

- D-pad highlight: the cross was static — it never showed which direction is held. Now the held arm (or arms, for diagonals) lights up in the pressed colour while held, and clears on release and on pause, following exactly the direction set the input already computes.
- Joystick option: the left-hand slot holds EITHER the D-pad OR a joystick, never both. Settings → Player → "Left control" picks the global default (D-pad by default); a game's layout editor can override it for that game alone. The joystick is HowlingDog's PlayStation-style stick — filled base disc, smaller nub riding at 0.45x the finger offset, fixed in place, snaps back to centre on release — with the same 0.22 dead zone and 0.35 diagonal detection as the D-pad, so it drives the same direction mask. It lives in the same spec slot, so a switching game keeps its tuned position, size, and opacity.
- Black screen on launch, fixed: launching a game could leave the picture black with audio until a press. The session started before the GL view was attached, so the first frame's render request was dropped, and the dirty check then blocked every later frame. The render request now survives surface creation — when a frame is already pending as the surface is created, the view requests a render and replays the session's latest frame, so the picture appears with no input.
- FF / 0.5x / RW are moveable now: the three buttons lived in a fixed row at the bottom-right corner. They are overlay controls like the rest — positions as full-screen fractions, resizable, opacity, editable in the full-screen layout editor, per-system default + per-game override. Existing installs keep the buttons exactly where they were (bottom-right). Behaviour is unchanged: FF hold-to-run at the chosen speed, 0.5x toggle, RW hold-to-step back through the last 30 seconds.
- Worm Light: a coverage slider in Settings → Display (75–175%, 100% by default, the current look) makes the pool of light wider or narrower, and it persists across launches. In aim mode, the finger's distance from the lens is now the forward/back: dragging out along a bearing stretches the pool further along it, dragging back in pulls it back. And the aim angle no longer resets — set an angle, leave aim mode, move the head, re-enter, and the angle is exactly where it was left. The head position and aim angle themselves are still per-session (they reset on relaunch, deliberately).
- What you should see now: a held D-pad arm lights up; a joystick choice in Settings and per game; the picture the moment a game launches, with no press; FF/RW moved to the centre in the editor, saved, and still there on relaunch; the Worm Light pool visibly bigger/smaller from the slider, and an aim angle that survives leaving and re-entering aim mode.

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
