package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alpha.16 — pure JVM tests for the proportional-steering pulse math (see ProportionalSteering). Alpha.18 rewrote the duty (t-squared) and the counting (error diffusion across periods). */
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
    fun dutyIsTSquared() {
        // Alpha.18 — a slight push counts for much less than
        // under the old linear duty: t = 0.5 is a quarter,
        // not a half; t = 0.1 is a hundredth, not a tenth.
        assertEquals(0f, ProportionalSteering.duty(0f), 0f)
        assertEquals(0.01f, ProportionalSteering.duty(0.1f), 0.001f)
        assertEquals(0.25f, ProportionalSteering.duty(0.5f), 0.001f)
        assertEquals(1f, ProportionalSteering.duty(1f), 0f)
        // Out of range clamps: a deflection past full travel
        // (or below zero) can never widen the period.
        assertEquals(1f, ProportionalSteering.duty(1.5f), 0f)
        assertEquals(0f, ProportionalSteering.duty(-0.5f), 0f)
    }

    @Test
    fun onFramesCarriesTheFractionalRemainder() {
        // Alpha.18 — the period's ON count is floor(6 * duty
        // + carry): the fraction is not rounded away, it is
        // handed to the next period via nextCarry.
        assertEquals(0, ProportionalSteering.onFrames(0f, 0f))
        assertEquals(1, ProportionalSteering.onFrames(0.5f, 0f))
        assertEquals(2, ProportionalSteering.onFrames(0.5f, 0.5f))
        assertEquals(6, ProportionalSteering.onFrames(1f, 0f))
        assertEquals(0, ProportionalSteering.onFrames(0.1f, 0f))
        assertEquals(1, ProportionalSteering.onFrames(0.1f, 0.96f))
        assertEquals(0.5f, ProportionalSteering.nextCarry(0.5f, 0f), 0.001f)
        assertEquals(0f, ProportionalSteering.nextCarry(0.5f, 0.5f), 0.001f)
        assertEquals(0f, ProportionalSteering.nextCarry(1f, 0f), 0.001f)
        assertEquals(0.06f, ProportionalSteering.nextCarry(0.1f, 0f), 0.001f)
        // Out of range clamps: a deflection past full travel
        // (or below zero) can never widen the period.
        assertEquals(6, ProportionalSteering.onFrames(1.5f, 0f))
        assertEquals(0, ProportionalSteering.onFrames(-0.5f, 0f))
    }

    @Test
    fun aTinyDeflectionEventuallyFiresButStaysGentle() {
        // Alpha.18 — no dead band just above the dead zone:
        // at t = 0.1 the duty is 0.01, only 0.06 of a frame
        // per period, so nothing fires for sixteen periods —
        // and then one full-steering frame lands, in period
        // 17, because the remainder carried forward. Over 100
        // periods the ON frames add up to about 6 (0.06 each),
        // not 100 as the old linear rounding would have given
        // (round(6 * 0.1) = 1 per period) and not 0 as a
        // per-period floor would. The range, not an exact 6,
        // is the assertion: the accumulation is float math.
        var carry = 0f
        var firstOnPeriod = -1
        var totalOn = 0
        repeat(100) { period ->
            val on = ProportionalSteering.onFrames(0.1f, carry)
            if (on > 0 && firstOnPeriod < 0) firstOnPeriod = period + 1
            totalOn += on
            carry = ProportionalSteering.nextCarry(0.1f, carry)
        }
        assertEquals(17, firstOnPeriod)
        assertTrue(totalOn in 5..7)
        // t = 0.25 is exact in binary (duty 0.0625, 0.375 of
        // a frame per period), so eight periods are exactly
        // three ON frames — no range needed here.
        carry = 0f
        totalOn = 0
        repeat(8) {
            totalOn += ProportionalSteering.onFrames(0.25f, carry)
            carry = ProportionalSteering.nextCarry(0.25f, carry)
        }
        assertEquals(3, totalOn)
    }

    @Test
    fun pulseIsOnForTheFirstOnFramesStepsOfThePeriod() {
        // 2 ON frames in the period: ON for steps 0, 1, OFF
        // for 2, 3, 4, 5.
        assertTrue(ProportionalSteering.isOn(0, 2))
        assertTrue(ProportionalSteering.isOn(1, 2))
        assertFalse(ProportionalSteering.isOn(2, 2))
        assertFalse(ProportionalSteering.isOn(3, 2))
        assertFalse(ProportionalSteering.isOn(4, 2))
        assertFalse(ProportionalSteering.isOn(5, 2))
        // The step wraps: step 6 is step 0 of the next period.
        assertTrue(ProportionalSteering.isOn(6, 2))
        assertFalse(ProportionalSteering.isOn(9, 2))
    }

    @Test
    fun fullDeflectionIsHeldContinuously() {
        for (step in 0..11) {
            assertTrue(ProportionalSteering.isOn(step, 6))
        }
    }

    @Test
    fun zeroDeflectionNeverPulsesOn() {
        for (step in 0..11) {
            assertFalse(ProportionalSteering.isOn(step, 0))
        }
    }

    @Test
    fun toggleOffPassesTheMaskThroughUnchanged() {
        val raw = left or a
        for (step in 0..5) {
            assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, step, 1, false))
        }
        // Even at full deflection with the toggle off, the
        // mask is today's behaviour byte-for-byte.
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 0, 6, false))
    }

    @Test
    fun noStickDirectionHeldPassesTheMaskThroughUnchanged() {
        val raw = up or a
        for (step in 0..5) {
            assertEquals(raw, ProportionalSteering.pulsedMask(raw, 0, step, 1, true))
        }
    }

    @Test
    fun pulseGatesOnlyTheStickDirectionBits() {
        // Stick held left with 1 ON frame in the period and A
        // also held: the pulse gates LEFT and leaves A alone.
        val raw = left or a
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 0, 1, true))
        assertEquals(a, ProportionalSteering.pulsedMask(raw, left, 1, 1, true))
        assertEquals(a, ProportionalSteering.pulsedMask(raw, left, 5, 1, true))
    }

    @Test
    fun diagonalStickBitsPulseTogether() {
        // A diagonal is one direction, not two: both bits go
        // on and off in step.
        val stick = up or left
        val raw = stick or a
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, stick, 1, 2, true))
        assertEquals(a, ProportionalSteering.pulsedMask(raw, stick, 4, 2, true))
    }

    @Test
    fun dpadBitsAreNeverPulsed() {
        // The D-pad has no deflection to measure: with the
        // stick mask at 0 (the D-pad is in the slot, or the
        // stick is centred) a held direction reaches the core
        // on every step, whatever the toggle says.
        val raw = up or down
        for (step in 0..5) {
            assertEquals(raw, ProportionalSteering.pulsedMask(raw, 0, step, 1, true))
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
        assertEquals(raw, ProportionalSteering.pulsedMask(raw, left, 0, 1, true))
        assertEquals(up, ProportionalSteering.pulsedMask(raw, left, 4, 1, true))
    }
}
