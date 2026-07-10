package com.google.mediapipe.examples.gesturerecognizer.gesture

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkGestureClassifierTest {
    @Test
    fun recognizesSupportedGestures() {
        val poses = listOf(
            "Pointing_Up" to pose(thumbExtended = false, extendedFingers = setOf(Finger.Index)),
            "Victory" to pose(thumbExtended = false, extendedFingers = setOf(Finger.Index, Finger.Middle)),
            "ILoveYou" to pose(
                thumbExtended = true,
                extendedFingers = setOf(Finger.Index, Finger.Pinky)
            )
        )

        poses.forEach { (expectedName, landmarks) ->
            val estimate = assertNotNullEstimate(LandmarkGestureClassifier.estimate(landmarks))

            assertEquals(expectedName, estimate.resolvedName)
            assertTrue("Expected a confident score for $expectedName", estimate.score >= 0.82f)
            assertTrue("Expected a clear margin for $expectedName", estimate.margin >= 0.12f)
        }
    }

    @Test
    fun rejectsRockHornsAndOpenPalmAsUnsupported() {
        val rockHorns = pose(
            thumbExtended = false,
            extendedFingers = setOf(Finger.Index, Finger.Pinky)
        )
        val openPalm = pose(
            thumbExtended = true,
            extendedFingers = Finger.entries.toSet()
        )

        assertNull(assertNotNullEstimate(LandmarkGestureClassifier.estimate(rockHorns)).resolvedName)
        assertNull(assertNotNullEstimate(LandmarkGestureClassifier.estimate(openPalm)).resolvedName)
    }

    @Test
    fun remainsInvariantUnderRotationReflectionScaleAndTranslation() {
        val source = pose(
            thumbExtended = true,
            extendedFingers = setOf(Finger.Index, Finger.Pinky)
        )
        val baseline = assertNotNullEstimate(LandmarkGestureClassifier.estimate(source))
        val transformed = source.map { landmark -> landmark.transformed() }
        val transformedEstimate = assertNotNullEstimate(LandmarkGestureClassifier.estimate(transformed))

        assertEquals("ILoveYou", baseline.resolvedName)
        assertEquals(baseline.resolvedName, transformedEstimate.resolvedName)
        assertEquals(baseline.score, transformedEstimate.score, SCORE_TOLERANCE)
        assertEquals(baseline.margin, transformedEstimate.margin, SCORE_TOLERANCE)
        assertFingerScoresEqual(baseline.fingers, transformedEstimate.fingers)
    }

    @Test
    fun leavesTransitionBetweenPointingAndVictoryUnresolved() {
        val transition = pose(
            thumbExtended = false,
            extendedFingers = setOf(Finger.Index),
            partialMiddle = true
        )

        val estimate = assertNotNullEstimate(LandmarkGestureClassifier.estimate(transition))

        assertNull(estimate.resolvedName)
        assertTrue(estimate.fingers.middle > 0.32f)
        assertTrue(estimate.fingers.middle < 0.74f)
    }

    @Test
    fun returnsNullForInvalidOrDegenerateLandmarks() {
        val validPose = pose(thumbExtended = false, extendedFingers = setOf(Finger.Index))
        val nonFinitePose = validPose.toMutableList().apply {
            this[8] = GestureLandmark3D(Float.NaN, 1f, 0f)
        }
        val collapsedPose = List(LANDMARK_COUNT) { GestureLandmark3D(1f, 1f, 1f) }
        val collinearPalm = validPose.toMutableList().apply {
            this[0] = GestureLandmark3D(0f, 0f, 0f)
            this[5] = GestureLandmark3D(0f, 1.0f, 0f)
            this[9] = GestureLandmark3D(0f, 1.1f, 0f)
            this[13] = GestureLandmark3D(0f, 1.05f, 0f)
            this[17] = GestureLandmark3D(0f, 0.9f, 0f)
        }

        assertNull(LandmarkGestureClassifier.estimate(validPose.dropLast(1)))
        assertNull(LandmarkGestureClassifier.estimate(nonFinitePose))
        assertNull(LandmarkGestureClassifier.estimate(collapsedPose))
        assertNull(LandmarkGestureClassifier.estimate(collinearPalm))
    }

    @Test
    fun validatesAndSummarizesFingerScores() {
        val scores = FingerExtensionScores(
            thumb = 0.1f,
            index = 0.9f,
            middle = 0.2f,
            ring = 0.3f,
            pinky = 0.4f
        )

        assertEquals("thumb 0.10, index 0.90, middle 0.20, ring 0.30, pinky 0.40", scores.summary)
        assertThrows(IllegalArgumentException::class.java) {
            FingerExtensionScores(thumb = -0.1f, index = 0f, middle = 0f, ring = 0f, pinky = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FingerExtensionScores(thumb = 0f, index = Float.NaN, middle = 0f, ring = 0f, pinky = 0f)
        }
    }

    private fun pose(thumbExtended: Boolean, extendedFingers: Set<Finger>, partialMiddle: Boolean = false): List<GestureLandmark3D> {
        val landmarks = MutableList(LANDMARK_COUNT) { point(0.0, 0.0, 0.0) }
        landmarks[0] = point(0.0, 0.0, 0.0)
        landmarks[1] = point(0.58, 0.32, 0.0)
        addThumb(landmarks, thumbExtended)

        addFinger(landmarks, Finger.Index, point(0.45, 1.0, 0.0), Finger.Index in extendedFingers)
        addFinger(
            landmarks,
            Finger.Middle,
            point(0.12, 1.10, 0.0),
            Finger.Middle in extendedFingers,
            partiallyExtended = partialMiddle
        )
        addFinger(landmarks, Finger.Ring, point(-0.18, 1.05, 0.0), Finger.Ring in extendedFingers)
        addFinger(landmarks, Finger.Pinky, point(-0.45, 0.90, 0.0), Finger.Pinky in extendedFingers)
        return landmarks
    }

    private fun addThumb(landmarks: MutableList<GestureLandmark3D>, extended: Boolean) {
        if (extended) {
            landmarks[2] = point(0.85, 0.48, 0.0)
            landmarks[3] = point(1.12, 0.58, 0.0)
            landmarks[4] = point(1.38, 0.64, 0.0)
        } else {
            landmarks[2] = point(0.75, 0.50, 0.0)
            landmarks[3] = point(0.55, 0.66, 0.12)
            landmarks[4] = point(0.28, 0.72, 0.08)
        }
    }

    private fun addFinger(
        landmarks: MutableList<GestureLandmark3D>,
        finger: Finger,
        mcp: GestureLandmark3D,
        extended: Boolean,
        partiallyExtended: Boolean = false
    ) {
        landmarks[finger.mcp] = mcp
        when {
            partiallyExtended -> {
                landmarks[finger.pip] = mcp.offset(dy = 0.52)
                landmarks[finger.dip] = mcp.offset(dy = 0.82, dz = 0.25)
                landmarks[finger.tip] = mcp.offset(dy = 0.87, dz = 0.59)
            }

            extended -> {
                landmarks[finger.pip] = mcp.offset(dy = 0.60)
                landmarks[finger.dip] = mcp.offset(dy = 1.08)
                landmarks[finger.tip] = mcp.offset(dy = 1.46)
            }

            else -> {
                landmarks[finger.pip] = mcp.offset(dy = 0.45)
                landmarks[finger.dip] = mcp.offset(dy = 0.50, dz = 0.40)
                landmarks[finger.tip] = mcp.offset(dy = 0.15, dz = 0.50)
            }
        }
    }

    private fun GestureLandmark3D.offset(dx: Double = 0.0, dy: Double = 0.0, dz: Double = 0.0): GestureLandmark3D =
        point(x + dx, y + dy, z + dz)

    private fun GestureLandmark3D.transformed(): GestureLandmark3D {
        val angleX = 0.63
        val angleZ = -0.41
        val rotatedX = x.toDouble()
        val rotatedY = y * cos(angleX) - z * sin(angleX)
        val rotatedZ = y * sin(angleX) + z * cos(angleX)
        val twiceRotatedX = rotatedX * cos(angleZ) - rotatedY * sin(angleZ)
        val twiceRotatedY = rotatedX * sin(angleZ) + rotatedY * cos(angleZ)
        val scale = 2.7
        return point(
            x = -twiceRotatedX * scale + 4.2,
            y = twiceRotatedY * scale - 1.7,
            z = rotatedZ * scale + 0.8
        )
    }

    private fun assertFingerScoresEqual(expected: FingerExtensionScores, actual: FingerExtensionScores) {
        assertEquals(expected.thumb, actual.thumb, SCORE_TOLERANCE)
        assertEquals(expected.index, actual.index, SCORE_TOLERANCE)
        assertEquals(expected.middle, actual.middle, SCORE_TOLERANCE)
        assertEquals(expected.ring, actual.ring, SCORE_TOLERANCE)
        assertEquals(expected.pinky, actual.pinky, SCORE_TOLERANCE)
    }

    private fun assertNotNullEstimate(estimate: LandmarkGestureEstimate?): LandmarkGestureEstimate {
        assertNotNull(estimate)
        return requireNotNull(estimate)
    }

    private fun point(x: Number, y: Number, z: Number): GestureLandmark3D = GestureLandmark3D(
        x = x.toFloat(),
        y = y.toFloat(),
        z = z.toFloat()
    )

    private enum class Finger(val mcp: Int, val pip: Int, val dip: Int, val tip: Int) {
        Index(mcp = 5, pip = 6, dip = 7, tip = 8),
        Middle(mcp = 9, pip = 10, dip = 11, tip = 12),
        Ring(mcp = 13, pip = 14, dip = 15, tip = 16),
        Pinky(mcp = 17, pip = 18, dip = 19, tip = 20)
    }

    private companion object {
        const val LANDMARK_COUNT = 21
        const val SCORE_TOLERANCE = 0.0001f
    }
}
