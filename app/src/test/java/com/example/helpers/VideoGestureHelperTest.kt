package com.example.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoGestureHelperTest {

    @Test
    fun resolveGestureModeLocksToCorrectAxisAndZone() {
        val viewportWidth = 1080f

        // Left half vertical swipe -> Brightness
        val leftVertical = VideoGestureHelper.resolveGestureMode(
            startX = 220f,
            viewportWidth = viewportWidth,
            dragDx = 3f,
            dragDy = -40f,
            isLongPressSpeedActive = false
        )
        assertEquals(LockedGestureMode.VERTICAL_BRIGHTNESS, leftVertical)

        // Right half vertical swipe -> Volume
        val rightVertical = VideoGestureHelper.resolveGestureMode(
            startX = 820f,
            viewportWidth = viewportWidth,
            dragDx = -4f,
            dragDy = -45f,
            isLongPressSpeedActive = false
        )
        assertEquals(LockedGestureMode.VERTICAL_VOLUME, rightVertical)

        // Horizontal swipe -> Seek
        val horizontal = VideoGestureHelper.resolveGestureMode(
            startX = 500f,
            viewportWidth = viewportWidth,
            dragDx = 60f,
            dragDy = 10f,
            isLongPressSpeedActive = false
        )
        assertEquals(LockedGestureMode.HORIZONTAL_SEEK, horizontal)

        // Long press active -> Dynamic Speed Slide
        val speedSlide = VideoGestureHelper.resolveGestureMode(
            startX = 500f,
            viewportWidth = viewportWidth,
            dragDx = 30f,
            dragDy = 5f,
            isLongPressSpeedActive = true
        )
        assertEquals(LockedGestureMode.DYNAMIC_SPEED_SLIDE, speedSlide)
    }

    @Test
    fun brightnessThresholdsTriggerExpectedHapticPulses() {
        val height = 1000f

        // Crossing 50% midpoint upward (45% -> 58%)
        val (midVal, midPulse) = VideoGestureHelper.computeBrightnessStep(
            currentBrightness = 45,
            dragDy = -100f,
            viewportHeight = height
        )
        assertEquals(58, midVal)
        assertEquals(HapticPulseType.MIDPOINT_TICK, midPulse)

        // Hitting 100% upper boundary (92% -> 100%)
        val (maxVal, maxPulse) = VideoGestureHelper.computeBrightnessStep(
            currentBrightness = 92,
            dragDy = -120f,
            viewportHeight = height
        )
        assertEquals(100, maxVal)
        assertEquals(HapticPulseType.BOUNDARY_LIMIT, maxPulse)

        // Hitting 2% night-mode minimum boundary (8% -> 2%)
        val (minVal, minPulse) = VideoGestureHelper.computeBrightnessStep(
            currentBrightness = 8,
            dragDy = 100f,
            viewportHeight = height
        )
        assertEquals(2, minVal)
        assertEquals(HapticPulseType.BOUNDARY_LIMIT, minPulse)
    }

    @Test
    fun volumeThresholdsTriggerBoostAndBoundaryHaptics() {
        val height = 1000f

        // Crossing 100% hardware boost threshold (92% -> 108%)
        val (boostVal, boostPulse) = VideoGestureHelper.computeVolumeStep(
            currentVolume = 92,
            dragDy = -100f,
            viewportHeight = height
        )
        assertEquals(108, boostVal)
        assertEquals(HapticPulseType.BOOST_THRESHOLD, boostPulse)

        // Hitting 200% maximum LoudnessEnhancer limit (190% -> 200%)
        val (maxVol, maxPulse) = VideoGestureHelper.computeVolumeStep(
            currentVolume = 190,
            dragDy = -100f,
            viewportHeight = height
        )
        assertEquals(200, maxVol)
        assertEquals(HapticPulseType.BOUNDARY_LIMIT, maxPulse)

        // Hitting 0% mute boundary (10% -> 0%)
        val (muteVol, mutePulse) = VideoGestureHelper.computeVolumeStep(
            currentVolume = 10,
            dragDy = 100f,
            viewportHeight = height
        )
        assertEquals(0, muteVol)
        assertEquals(HapticPulseType.BOUNDARY_LIMIT, mutePulse)
    }

    @Test
    fun horizontalSeekTriggersStepTickAndBoundaryHaptics() {
        val width = 1000f
        val duration = 180_000L // 3 minutes

        // Small seek within same 10s bucket (12,000ms -> ~16,500ms) -> no haptic tick
        val (smallPos, smallPulse) = VideoGestureHelper.computeHorizontalSeekStep(
            currentPositionMs = 12_000L,
            durationMs = duration,
            dragDx = 50f,
            viewportWidth = width
        )
        assertEquals(16_500L, smallPos)
        assertNull(smallPulse)

        // Crossing 20,000ms 10-second step notch (16,500ms -> 25,500ms) -> STEP_TICK
        val (stepPos, stepPulse) = VideoGestureHelper.computeHorizontalSeekStep(
            currentPositionMs = 16_500L,
            durationMs = duration,
            dragDx = 100f,
            viewportWidth = width
        )
        assertEquals(25_500L, stepPos)
        assertEquals(HapticPulseType.STEP_TICK, stepPulse)

        // Rewinding to 0ms start boundary -> BOUNDARY_LIMIT
        val (startPos, startPulse) = VideoGestureHelper.computeHorizontalSeekStep(
            currentPositionMs = 3_000L,
            durationMs = duration,
            dragDx = -100f,
            viewportWidth = width
        )
        assertEquals(0L, startPos)
        assertEquals(HapticPulseType.BOUNDARY_LIMIT, startPulse)
    }
}
