package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun category(name: String, score: Float): Category = Category.create(score, 0, name, "")

    private fun landmark(x: Float, y: Float): NormalizedLandmark = NormalizedLandmark.create(x, y, 0f)

    private class FakeGestureRecognizerResult(
        private val timestampMs: Long = 0L,
        private val landmarks: List<List<NormalizedLandmark>> = emptyList(),
        private val gestures: List<List<Category>> = emptyList(),
        private val handedness: List<List<Category>> = emptyList()
    ) : GestureRecognizerResult() {
        override fun timestampMs(): Long = timestampMs

        override fun landmarks(): List<List<NormalizedLandmark>> = landmarks

        override fun worldLandmarks(): List<List<Landmark>> = emptyList()

        override fun handedness(): List<List<Category>> = handedness

        override fun gestures(): List<List<Category>> = gestures
    }
}
