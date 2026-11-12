package app.poing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Phase 6 (item 12) — the Cartridge Surgery panel: a slide-out
 * translucent schematic of the cartridge being played, with live
 * hardware controls. Reached from the pause menu while the game
 * is paused; the game stays paused while it is open.
 *
 * The schematic is the cartridge shell (a silver outline with
 * the game as its label) with one bordered chip per part inside
 * it: the RTC clock, the solar cell (GBA only — only Boktai
 * cartridges have one), the rumble motor, and the tilt + light
 * sensors.
 *
 * What each control does:
 * - RTC thumbwheel: spins the cartridge's saved clock
 *   ([CartridgeClock]) — the game sees the new time when it next
 *   loads. The live clock cannot be spun while it runs (the core
 *   only reads the RTC buffer at load), so the panel says so.
 * - Solar slider: the mgba_solar_sensor_level option, live on the
 *   next frame (non-restart), saved like any core option. "Sensor"
 *   reads the phone's light sensor (the item 10a path).
 * - Rumble / sensors toggles: session-only, default on — they gate
 *   the hardware for this game and never touch Settings.
 *
 * NOT in it (declined): camera UV sync, dead-battery simulation.
 */
@Composable
fun CartridgeSurgeryPanel(
    gameName: String,
    isGba: Boolean,
    rtcTime: CartridgeClock.RtcTime?,
    onSpinRtc: (Long) -> Unit,
    solarLevel: String,
    onSolarLevel: (String) -> Unit,
    rumbleEnabled: Boolean,
    onRumbleEnabled: (Boolean) -> Unit,
    sensorsEnabled: Boolean,
    onSensorsEnabled: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    // The slide-out: the panel mounts fresh each time it opens
    // (GameScreen shows it behind an `if`), so the enter
    // animation plays on open — in from the right edge.
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { it }),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .padding(24.dp)
                .testTag("cartridge-panel-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Cartridge",
                    style = displayTextStyle(DisplaySizeMedium),
                    color = Color.White,
                )
                Text(
                    text = "Surgery — the hardware inside this cartridge",
                    color = PoingSilver,
                )
            }
            item {
                AtomicPanel(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // The cartridge shell: a silver outline with
                    // the game as its label, the parts inside it.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(2.dp, PoingSilver),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = gameName,
                            style = displayTextStyle(DisplaySizeSmall),
                            color = Color.White,
                        )
                        // RTC — the clock chip. Only MBC3 cartridges
                        // have one; every other game gets the plain
                        // explanation, not an empty control.
                        ChipBox(label = "RTC — real-time clock") {
                            if (rtcTime == null) {
                                Text(
                                    text = "This cartridge has no clock.",
                                    color = PoingSilver,
                                )
                            } else {
                                Text(
                                    text = CartridgeClock.format(rtcTime),
                                    style = segmentTextStyle14(DisplaySizeSmall),
                                    color = Color.White,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onSpinRtc(-86_400L) }) {
                                        Text("−1 day")
                                    }
                                    Button(onClick = { onSpinRtc(-3_600L) }) {
                                        Text("−1 hour")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onSpinRtc(3_600L) }) {
                                        Text("+1 hour")
                                    }
                                    Button(onClick = { onSpinRtc(86_400L) }) {
                                        Text("+1 day")
                                    }
                                }
                                Text(
                                    text = "Spins the cartridge clock. The game sees the " +
                                        "new time when it next loads — the live clock " +
                                        "cannot be spun while it runs.",
                                    color = PoingSilver,
                                )
                            }
                        }
                        // Solar cell — only GBA cartridges with a
                        // solar sensor (the Boktai series) have one.
                        if (isGba) {
                            ChipBox(label = "Solar cell") {
                                SolarControl(
                                    level = solarLevel,
                                    onLevel = onSolarLevel,
                                )
                            }
                        }
                        // Rumble motor.
                        ChipBox(label = "Rumble motor") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "The cartridge rumbles the phone",
                                    color = Color.White,
                                )
                                Switch(
                                    checked = rumbleEnabled,
                                    onCheckedChange = onRumbleEnabled,
                                    modifier = Modifier.testTag("cartridge-rumble-switch"),
                                )
                            }
                        }
                        // Tilt + light sensors.
                        ChipBox(label = "Tilt + light sensors") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "The phone's motion and light",
                                    color = Color.White,
                                )
                                Switch(
                                    checked = sensorsEnabled,
                                    onCheckedChange = onSensorsEnabled,
                                    modifier = Modifier.testTag("cartridge-sensors-switch"),
                                )
                            }
                            if (!sensorsEnabled) {
                                Text(
                                    text = "Off — the core reads flat: no tilt, no light.",
                                    color = PoingSilver,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onDone) {
                    Text("Done")
                }
            }
        }
    }
}

/** One bordered chip inside the cartridge shell, with its label. */
@Composable
private fun ChipBox(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, PoingSilver),
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = displayTextStyle(DisplaySizeSmall),
            color = PoingSilver,
        )
        content()
    }
}

/**
 * The solar slider: 0–10, committed on release (the drag itself
 * only moves the thumb), plus a "Sensor" chip that reads the
 * phone's light sensor instead of a fixed level.
 */
@Composable
private fun SolarControl(
    level: String,
    onLevel: (String) -> Unit,
) {
    val isSensor = level == "sensor"
    var sliderValue by remember(level) {
        mutableStateOf(level.toFloatOrNull() ?: 0f)
    }
    Text(
        text = if (isSensor) {
            "Sensor — the phone's light sensor"
        } else {
            "Level ${sliderValue.toInt()} of 10"
        },
        color = Color.White,
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = {
            onLevel(sliderValue.toInt().toString())
        },
        valueRange = 0f..10f,
        steps = 9,
        enabled = !isSensor,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onLevel("sensor") },
            enabled = !isSensor,
        ) {
            Text("Use device sensor")
        }
        if (isSensor) {
            Button(onClick = { onLevel(sliderValue.toInt().toString()) }) {
                Text("Use a fixed level")
            }
        }
    }
    Text(
        text = "Sunlight for Boktai cartridges. Takes effect on the next frame.",
        color = PoingSilver,
    )
}
