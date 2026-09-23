package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureFrameSetTest {
    @Test
    fun fromResultCreatesHandsCandidatesCentersAndHandedness() {
        val result = FakeGestureRecognizerResult(
            timestampMs = 123L,
            landmarks = listOf(
                listOf(landmark(0.20f, 0.30f), landmark(0.40f, 0.50f)),
                listOf(landmark(0.80f, 0.20f))
            ),
            gestures = listOf(
                listOf(category("Victory", 0.60f), category("Open_Palm", 0.90f)),
                listOf(category("Thumb_Up", 0.70f))
            ),
            handedness = listOf(
                listOf(category("Left", 0.80f)),
                listOf(category("Right", 0.95f))
            )
        )

        val frameSet = GestureFrameSet.fromResult(result)

        assertEquals(123L, frameSet.timestampMs)
        assertEquals(2, frameSet.handCount)

        val firstHand = frameSet.hands[0]
        assertEquals(0, firstHand.detectionIndex)
        assertEquals("Left", firstHand.handedness)
        assertEquals(0.80f, firstHand.handednessScore ?: 0f, 0.001f)
        assertEquals("Open_Palm", firstHand.name)
        assertEquals(0.90f, firstHand.score, 0.001f)
        assertEquals("Open_Palm", firstHand.candidates[0].name)
        assertEquals(1, firstHand.candidates[0].rank)
        assertEquals("Victory", firstHand.candidates[1].name)
        assertEquals(2, firstHand.candidates[1].rank)
        assertEquals(0.30f, firstHand.centerX ?: 0f, 0.001f)
        assertEquals(0.40f, firstHand.centerY ?: 0f, 0.001f)
        assertEquals(2, firstHand.landmarkCount)

        val secondHand = frameSet.hands[1]
        assertEquals("Right", secondHand.handedness)
        assertEquals("Thumb_Up", secondHand.name)
        assertEquals(0.80f, secondHand.centerX ?: 0f, 0.001f)
        assertEquals(0.20f, secondHand.centerY ?: 0f, 0.001f)
    }

    @Test
    fun fromResultIgnoresClassificationsWithoutLandmarks() {
        val result = FakeGestureRecognizerResult(
            timestampMs = 50L,
            landmarks = emptyList(),
            gestures = listOf(listOf(category("Closed_Fist", 0.72f))),
            handedness = emptyList()
        )

        val frameSet = GestureFrameSet.fromResult(result)

        assertEquals(0, frameSet.handCount)
    }

    @Test
    fun fromResultIgnoresEmptyLandmarkGroups() {
        val result = FakeGestureRecognizerResult(
            landmarks = listOf(emptyList()),
            gestures = listOf(listOf(category("Closed_Fist", 0.72f)))
        )

        assertEquals(0, GestureFrameSet.fromResult(result).handCount)
    }

    @Test
    fun fromResultRejectsInvalidScoresAndInvalidLandmarks() {
        val landmarks = MutableList(21) { landmark(0.50f, 0.50f) }
        landmarks[20] = landmark(Float.NaN, 0.50f)
        val result = FakeGestureRecognizerResult(
            landmarks = listOf(landmarks),
            gestures = listOf(
                listOf(
                    category("Invalid", 2f),
                    category("Open_Palm", 0.80f)
                )
            )
        )

        val hand = GestureFrameSet.fromResult(result).hands.single()

        assertEquals(listOf("Open_Palm"), hand.candidates.map { candidate -> candidate.name })
        assertNull(hand.centerX)
        assertNull(hand.centerY)
    }

    @Test
    fun fromResultHandlesEmptyResult() {
        val frameSet = GestureFrameSet.fromResult(FakeGestureRecognizerResult())

        assertEquals(0L, frameSet.timestampMs)
        assertEquals(0, frameSet.handCount)
    }

    @Test
    fun fromResultUsesConfidentLandmarkPoseToCorrectModelCandidate() {
        val result = FakeGestureRecognizerResult(
            landmarks = listOf(List(21) { landmark(0.50f, 0.50f) }),
            worldLandmarks = listOf(pointingUpWorldLandmarks()),
            gestures = listOf(listOf(category("Victory", 0.66f)))
        )

        val hand = GestureFrameSet.fromResult(result).hands.single()

        assertEquals("Victory", hand.bestCandidate?.name)
        assertEquals("Pointing_Up", hand.name)
        assertEquals("landmarks", hand.classificationSource)
        assertTrue(hand.score >= 0.82f)
    }

    private fun category(name: String, score: Float): Category = Category.create(score, 0, name, "")

    private fun landmark(x: Float, y: Float): NormalizedLandmark = NormalizedLandmark.create(x, y, 0f)

    private fun pointingUpWorldLandmarks(): List<Landmark> = MutableList(21) { worldLandmark(0f, 0f, 0f) }.apply {
        this[0] = worldLandmark(0f, 0f, 0f)
        this[1] = worldLandmark(0.58f, 0.32f, 0f)
        this[2] = worldLandmark(0.75f, 0.50f, 0f)
        this[3] = worldLandmark(0.55f, 0.66f, 0.12f)
        this[4] = worldLandmark(0.28f, 0.72f, 0.08f)
        addWorldFinger(mcpIndex = 5, x = 0.45f, y = 1.00f, extended = true)
        addWorldFinger(mcpIndex = 9, x = 0.12f, y = 1.10f, extended = false)
        addWorldFinger(mcpIndex = 13, x = -0.18f, y = 1.05f, extended = false)
        addWorldFinger(mcpIndex = 17, x = -0.45f, y = 0.90f, extended = false)
    }

    private fun MutableList<Landmark>.addWorldFinger(mcpIndex: Int, x: Float, y: Float, extended: Boolean) {
        this[mcpIndex] = worldLandmark(x, y, 0f)
        if (extended) {
            this[mcpIndex + 1] = worldLandmark(x, y + 0.60f, 0f)
            this[mcpIndex + 2] = worldLandmark(x, y + 1.08f, 0f)
            this[mcpIndex + 3] = worldLandmark(x, y + 1.46f, 0f)
        } else {
            this[mcpIndex + 1] = worldLandmark(x, y + 0.45f, 0f)
            this[mcpIndex + 2] = worldLandmark(x, y + 0.50f, 0.40f)
            this[mcpIndex + 3] = worldLandmark(x, y + 0.15f, 0.50f)
        }
    }

    private fun worldLandmark(x: Float, y: Float, z: Float): Landmark = Landmark.create(x, y, z)

    private class FakeGestureRecognizerResult(
        private val timestampMs: Long = 0L,
        private val landmarks: List<List<NormalizedLandmark>> = emptyList(),
        private val worldLandmarks: List<List<Landmark>> = emptyList(),
        private val gestures: List<List<Category>> = emptyList(),
        private val handedness: List<List<Category>> = emptyList()
    ) : GestureRecognizerResult() {
        override fun timestampMs(): Long = timestampMs

        override fun landmarks(): List<List<NormalizedLandmark>> = landmarks

        override fun worldLandmarks(): List<List<Landmark>> = worldLandmarks

        override fun handedness(): List<List<Category>> = handedness

        override fun gestures(): List<List<Category>> = gestures
    }
}
