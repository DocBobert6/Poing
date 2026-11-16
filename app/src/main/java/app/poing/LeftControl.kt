package app.poing

/**
 * Alpha.10 — the control in the left-hand slot: the D-pad
 * cross, or a joystick. One slot holds EITHER, never both —
 * the setting picks which lives there. The global default
 * (AppSettingsStore) is the D-pad; a game can override it
 * (ControlLayoutStore's per-game choice file). The value is
 * stored as its lowercase name ("dpad" / "stick"), so a
 * stale or hand-edited value reads as the default instead of
 * stranding the slot.
 */
enum class LeftControl {
    DPAD,
    STICK,
}

fun leftControlFromName(raw: String?): LeftControl = when (raw?.lowercase()) {
    "stick" -> LeftControl.STICK
    else -> LeftControl.DPAD
}
