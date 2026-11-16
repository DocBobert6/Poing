package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alpha.16 — pure JVM tests for the proportional-steering pulse math (see ProportionalSteering). */
class ProportionalSteeringTest {

    private val up = 1 shl InputMask.JOY_UP
    private val down = 1 shl InputMask.JOY_DOWN
    private val left = 1 shl InputMask.JOY_LEFT
    private val right = 1 shl InputMask.JOY_RIGHT
    private val a = 1 shl InputMask.JOY_A

    @Test
    fun deflectionIsZeroAtOrBelowTheDeadZone() {
        assertEquals(0f, ProportionalSteering.deflection(0f, 0f, 0.22f))
        assertEquals(0f, ProportionalSteering.deflection(0.1f, 0f, 0.22f))
        assertEquals(0f, ProportionalSteering.deflection(0.22f, 0f, 0.22f))
        assertEquals(0f, ProportionalSteering.deflection(0f, -0.21f, 0.22f))
    }

    @Test
    fun deflectionIsOneAtFullTravel() {
        assertEquals(1f, ProportionalSteering.deflection(1f, 0f, 0.22f))
        assertEquals(1f, ProportionalSteering.deflection(0f, -1f, 0.22f))
        // A diagonal at full travel: the magnitude is 1 after
        // the stick clamps it, so it reads full too.
        assertEquals(1f, ProportionalSteering.deflection(0.7071f, -0.7071f, 0.22f), 0.001f)
    }

    @Test
    fun deflectionIsHalfHalfwayPastTheDeadZone() {
        // Halfway between the 0.22 zone and full travel is
        // 0.61 of travel, which is t = 0.5.
        assertEquals(0.5f, ProportionalSteering.deflection(0.61f, 0f, 0.22f), 0.001f)
        // A 50% dead zone: halfway past it is 0.75 of travel.
        assertEquals(0.5f, ProportionalSteering.deflection(0.75f, 0f, 0.5f), 0.001f)
    }

    @Test
    fun onFramesIsRoundOfSixTimesT() {
        assertEquals(0, ProportionalSteering.onFrames(0f))
        assertEquals(3, ProportionalSteering.onFrames(0.5f))
        assertEquals(6, ProportionalSteering.onFrames(1f))
        assertEquals(2, ProportionalSteering.onFrames(0.25f))
        assertEquals(1, ProportionalSteering.onFrames(1f / 6f))
        assertEquals(5, ProportionalSteering.onFrames(5f / 6f))
        // Out of range clamps: a deflection past full travel
        // (or below zero) can never widen the period.
        assertEquals(6, ProportionalSteering.onFrames(1.5f))
        assertEquals(0, ProportionalSteering.onFrames(-0.5f))
    }

    @Test
    fun pulseIsOnForTheFirstOnFramesStepsOfThePeriod() {
        // t = 0.5: ON for steps 0, 1, 2, OFF for 3, 4, 5.
        assertTrue(ProportionalSteering.isOn(0, 0.5f))
        assertTrue(ProportionalSteering.isOn(1, 0.5f))
        assertTrue(ProportionalSteering.isOn(2, 0.5f))
        assertFalse(ProportionalSteering.isOn(3, 0.5f))
        assertFalse(ProportionalSteering.isOn(4, 0.5f))
        assertFalse(ProportionalSteering.isOn(5, 0.5f))
        // The step wraps: step 6 is step 0 of the next period.
        assertTrue(ProportionalSteering.isOn(6, 0.5f))
        assertFalse(ProportionalSteering.isOn(9, 0.5f))
    }

    @Test
    fun fullDeflectionIsHeldContinuously() {
        for (step in 0..11) {
            assertTrue(ProportionalSteering.isOn(step, 1f))
        }
    }

    @Test
    fun zeroDeflectionNeverPulsesOn() {
        for (step in 0..11) {
            assertFalse(ProportionalSteering.isOn(step, 0f))
        }
    }

    @Test
    fun toggleOffPassesTheMaskThroughUnchanged() {
        val raw = left or a
        for (step in 0..5) {
            assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, step, 0.5f, false))
        }
        // Even at full deflection with the toggle off, the
        // mask is today's behaviour byte-for-byte.
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 0, 1f, false))
    }

    @Test
    fun noStickDirectionHeldPassesTheMaskThroughUnchanged() {
        val raw = up or a
        for (step in 0..5) {
            assertEquals(raw, ProportionalSteering.pulsedMask(raw, 0, step, 0.5f, true))
        }
    }

    @Test
    fun pulseGatesOnlyTheStickDirectionBits() {
        // Stick held left at t = 0.5 with A also held: the
        // pulse gates LEFT and leaves A alone.
        val raw = left or a
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 0, 0.5f, true))
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 2, 0.5f, true))
        assertEquals(a, ProportionalSteering.pulsedMask(raw, left, 3, 0.5f, true))
        assertEquals(a, ProportionalSteering.pulsedMask(raw, left, 5, 0.5f, true))
    }

    @Test
    fun diagonalStickBitsPulseTogether() {
        // A diagonal is one direction, not two: both bits go
        // on and off in step.
        val stick = up or left
        val raw = stick or a
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, stick, 1, 0.5f, true))
        assertEquals(a, ProportionalSteering.pulsedMask(raw, stick, 4, 0.5f, true))
    }

    @Test
    fun dpadBitsAreNeverPulsed() {
        // The D-pad has no deflection to measure: with the
        // stick mask at 0 (the D-pad is in the slot, or the
        // stick is centred) a held direction reaches the core
        // on every step, whatever the toggle says.
        val raw = up or down
        for (step in 0..5) {
            assertEquals(raw, ProportionalSteering.pulsedMask(raw, 0, step, 0.5f, true))
        }
    }

    @Test
    fun gamepadBitsAreNeverPulsed() {
        // A gamepad direction held at the same time as the
        // stick: only the stick's bits gate. UP here is the
        // gamepad's (it is in the raw mask but not in the
        // stick mask), so it stays on through the pulse's
        // OFF phase while the stick's LEFT gates.
        val raw = up or left
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 0, 0.5f, true))
        assertEquals(up, ProportionalSteering.pulsedMask(raw, left, 4, 0.5f, true))
    }
}
