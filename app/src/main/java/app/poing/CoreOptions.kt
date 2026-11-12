package app.poing

/**
 * Phase 4 — the audited mGBA core options, surfaced as real settings.
 *
 * Every entry is copied from the pinned core's own definitions
 * (src/platform/libretro/libretro_core_options.h, pin 7a12d6d4):
 * the key, the label, the value list in the core's order, the
 * documented default, and which options the header marks
 * "(Restart)". The bridge (bridge.cpp) answers GET_VARIABLE for
 * exactly these 17 keys with exactly these defaults — the two
 * tables must agree; CoreOptionsTest pins that.
 *
 * One exception: mgba_gb_colors. The header's static list holds
 * only "Grayscale" ("This list is populated at runtime") — the core
 * fills it from GBColorPresetList (src/gb/overrides.c) when the
 * environment is set. The 48 names here are that list, in the
 * core's order: 4 uniform palettes, 12 GBC palettes, 32 SGB
 * palettes. The core matches the chosen name against the same list
 * (_updateGbPal in libretro.c), so any of the 48 is a real value.
 *
 * Nothing here is invented: an option the core does not have is
 * not listed, and a value the core does not accept is not offered.
 */
enum class CoreOptionCategory(val label: String) {
    SYSTEM("System"),
    VIDEO("Video"),
    AUDIO("Audio"),
    INPUT("Input"),
    PERFORMANCE("Performance"),
}

data class CoreOption(
    val key: String,
    val label: String,
    val category: CoreOptionCategory,
    val values: List<String>,
    val displayValues: Map<String, String> = emptyMap(),
    val default: String,
    val restart: Boolean,
    val description: String,
) {
    fun display(value: String): String = displayValues[value] ?: value
}

object CoreOptions {

    /** The 48 GB palettes, in the core's own order (overrides.c). */
    val GB_PALETTES: List<String> = listOf(
        "Grayscale", "DMG Green", "GB Pocket", "GB Light",
        "GBC Brown ↑", "GBC Red ↑A", "GBC Dark Brown ↑B",
        "GBC Pale Yellow ↓", "GBC Orange ↓A", "GBC Yellow ↓B",
        "GBC Blue ←", "GBC Dark Blue ←A", "GBC Gray ←B",
        "GBC Green →", "GBC Dark Green →A", "GBC Reverse →B",
        "SGB 1-A", "SGB 1-B", "SGB 1-C", "SGB 1-D",
        "SGB 1-E", "SGB 1-F", "SGB 1-G", "SGB 1-H",
        "SGB 2-A", "SGB 2-B", "SGB 2-C", "SGB 2-D",
        "SGB 2-E", "SGB 2-F", "SGB 2-G", "SGB 2-H",
        "SGB 3-A", "SGB 3-B", "SGB 3-C", "SGB 3-D",
        "SGB 3-E", "SGB 3-F", "SGB 3-G", "SGB 3-H",
        "SGB 4-A", "SGB 4-B", "SGB 4-C", "SGB 4-D",
        "SGB 4-E", "SGB 4-F", "SGB 4-G", "SGB 4-H",
    )

    private fun range(from: Int, to: Int, step: Int): List<String> =
        (from..to step step).map { it.toString() }

    val ALL: List<CoreOption> = listOf(
        CoreOption(
            key = "mgba_gb_model",
            label = "Game Boy Model",
            category = CoreOptionCategory.SYSTEM,
            values = listOf(
                "Autodetect", "Game Boy", "Super Game Boy",
                "Game Boy Color", "Super Game Boy Color", "Game Boy Advance",
            ),
            default = "Autodetect",
            restart = true,
            description = "Runs loaded content with a specific Game Boy model. " +
                "'Autodetect' selects the most appropriate model for the current game.",
        ),
        CoreOption(
            key = "mgba_use_bios",
            label = "Use BIOS File if Found",
            category = CoreOptionCategory.SYSTEM,
            values = listOf("ON", "OFF"),
            displayValues = mapOf("ON" to "enabled", "OFF" to "disabled"),
            default = "ON",
            restart = true,
            description = "Use the official BIOS/bootloader for the emulated hardware, " +
                "if present in the system directory. A missing BIOS never blocks a start.",
        ),
        CoreOption(
            key = "mgba_skip_bios",
            label = "Skip BIOS Intro",
            category = CoreOptionCategory.SYSTEM,
            values = listOf("OFF", "ON"),
            displayValues = mapOf("OFF" to "disabled", "ON" to "enabled"),
            default = "OFF",
            restart = true,
            description = "When using an official BIOS/bootloader, skip the start-up logo " +
                "animation. Ignored when 'Use BIOS File if Found' is disabled.",
        ),
        CoreOption(
            key = "mgba_gb_colors",
            label = "Default Game Boy Palette",
            category = CoreOptionCategory.VIDEO,
            values = GB_PALETTES,
            default = "Grayscale",
            restart = false,
            description = "Which palette is used for Game Boy games that are not Game Boy " +
                "Color or Super Game Boy compatible, or if the model is forced to Game Boy.",
        ),
        CoreOption(
            key = "mgba_gb_colors_preset",
            label = "Hardware Preset Game Boy Palettes",
            category = CoreOptionCategory.VIDEO,
            values = listOf("0", "1", "2", "3"),
            displayValues = mapOf(
                "0" to "Default Game Boy preset",
                "1" to "Game Boy Color presets only",
                "2" to "Super Game Boy presets only",
                "3" to "Any available presets",
            ),
            default = "0",
            restart = true,
            description = "Use the palettes for Game Boy games that have presets on the " +
                "Game Boy Color or Super Game Boy.",
        ),
        CoreOption(
            key = "mgba_sgb_borders",
            label = "Use Super Game Boy Borders",
            category = CoreOptionCategory.VIDEO,
            values = listOf("ON", "OFF"),
            displayValues = mapOf("ON" to "enabled", "OFF" to "disabled"),
            default = "ON",
            restart = true,
            description = "Display Super Game Boy borders when running Super Game Boy enhanced games.",
        ),
        CoreOption(
            key = "mgba_color_correction",
            label = "Color Correction",
            category = CoreOptionCategory.VIDEO,
            values = listOf("OFF", "GBA", "GBC", "Auto"),
            displayValues = mapOf(
                "OFF" to "disabled",
                "GBA" to "Game Boy Advance",
                "GBC" to "Game Boy Color",
                "Auto" to "Auto",
            ),
            default = "OFF",
            restart = false,
            description = "Adjusts output colors to match the display of real GBA/GBC hardware.",
        ),
        CoreOption(
            key = "mgba_interframe_blending",
            label = "Interframe Blending",
            category = CoreOptionCategory.VIDEO,
            values = listOf("OFF", "mix", "mix_smart", "lcd_ghosting", "lcd_ghosting_fast"),
            displayValues = mapOf(
                "OFF" to "disabled",
                "mix" to "Simple",
                "mix_smart" to "Smart",
                "lcd_ghosting" to "LCD Ghosting (Accurate)",
                "lcd_ghosting_fast" to "LCD Ghosting (Fast)",
            ),
            default = "OFF",
            restart = false,
            description = "Simulates LCD ghosting effects. 'Simple' mixes the current and " +
                "previous frames; 'Smart' only mixes flickering pixels; 'LCD Ghosting' " +
                "mimics natural LCD response times.",
        ),
        CoreOption(
            key = "mgba_audio_low_pass_filter",
            label = "Audio Filter",
            category = CoreOptionCategory.AUDIO,
            values = listOf("disabled", "enabled"),
            default = "disabled",
            restart = false,
            description = "Enables a low pass audio filter to reduce the 'harshness' of generated audio.",
        ),
        CoreOption(
            key = "mgba_audio_low_pass_range",
            label = "Audio Filter Level",
            category = CoreOptionCategory.AUDIO,
            values = range(5, 95, 5),
            displayValues = range(5, 95, 5).associateWith { "$it%" },
            default = "60",
            restart = false,
            description = "The cut-off frequency of the low pass audio filter. A higher value " +
                "increases the perceived 'strength' of the filter.",
        ),
        CoreOption(
            key = "mgba_allow_opposing_directions",
            label = "Allow Opposing Directional Input",
            category = CoreOptionCategory.INPUT,
            values = listOf("no", "yes"),
            displayValues = mapOf("no" to "disabled", "yes" to "enabled"),
            default = "no",
            restart = false,
            description = "Allow pressing left and right (or up and down) at the same time. " +
                "This may cause movement-based glitches.",
        ),
        CoreOption(
            key = "mgba_solar_sensor_level",
            label = "Solar Sensor Level",
            category = CoreOptionCategory.INPUT,
            values = listOf("sensor") + range(0, 10, 1),
            displayValues = mapOf("sensor" to "Use device sensor if available"),
            default = "0",
            restart = false,
            description = "Sets ambient sunlight intensity for games with a solar sensor in " +
                "the cartridge, e.g. the Boktai series.",
        ),
        CoreOption(
            key = "mgba_force_gbp",
            label = "Game Boy Player Rumble",
            category = CoreOptionCategory.INPUT,
            values = listOf("OFF", "ON"),
            displayValues = mapOf("OFF" to "disabled", "ON" to "enabled"),
            default = "OFF",
            restart = true,
            description = "Allow compatible games with the Game Boy Player boot logo to make " +
                "the controller rumble. May cause flickering or lag in some of these games.",
        ),
        CoreOption(
            key = "mgba_idle_optimization",
            label = "Idle Loop Removal",
            category = CoreOptionCategory.PERFORMANCE,
            values = listOf("Remove Known", "Detect and Remove", "Don't Remove"),
            default = "Remove Known",
            restart = false,
            description = "Reduce system load by optimizing idle loops — sections where " +
                "nothing happens but the CPU runs at full speed. Improves performance.",
        ),
        CoreOption(
            key = "mgba_frameskip",
            label = "Frameskip",
            category = CoreOptionCategory.PERFORMANCE,
            values = listOf("disabled", "auto", "auto_threshold", "fixed_interval"),
            displayValues = mapOf(
                "disabled" to "disabled",
                "auto" to "Auto",
                "auto_threshold" to "Auto (Threshold)",
                "fixed_interval" to "Fixed Interval",
            ),
            default = "disabled",
            restart = false,
            description = "Skip frames to avoid audio buffer under-run (crackling). Improves " +
                "performance at the expense of visual smoothness.",
        ),
        CoreOption(
            key = "mgba_frameskip_threshold",
            label = "Frameskip Threshold (%)",
            category = CoreOptionCategory.PERFORMANCE,
            values = range(15, 60, 3),
            default = "33",
            restart = false,
            description = "When 'Frameskip' is 'Auto (Threshold)', the audio buffer occupancy " +
                "threshold below which frames are skipped.",
        ),
        CoreOption(
            key = "mgba_frameskip_interval",
            label = "Frameskip Interval",
            category = CoreOptionCategory.PERFORMANCE,
            values = range(0, 10, 1),
            default = "0",
            restart = false,
            description = "When 'Frameskip' is 'Fixed Interval', the number of frames omitted " +
                "after a frame is rendered — 0 = 60fps, 1 = 30fps, 2 = 15fps.",
        ),
    )

    fun byKey(key: String): CoreOption? = ALL.firstOrNull { it.key == key }

    /** Every option, in the core's own order. */
    fun defaults(): Map<String, String> = ALL.associate { it.key to it.default }
}
