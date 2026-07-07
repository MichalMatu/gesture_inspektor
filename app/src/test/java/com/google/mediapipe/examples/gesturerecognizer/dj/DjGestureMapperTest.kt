package com.google.mediapipe.examples.gesturerecognizer.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjGestureMapperTest {
    @Test
    fun oncePerHoldCommandFiresOnlyOnceUntilGestureChanges() {
        val mapper = DjGestureMapper(stableFramesRequired = 2)

        assertNull(mapper.nextCommand(frame("Open_Palm", 0L)))
        assertEquals(
            DjCommand.PlayPauseDeckA,
            mapper.nextCommand(frame("Open_Palm", 33L))
        )
        assertNull(mapper.nextCommand(frame("Open_Palm", 66L)))
        assertNull(mapper.nextCommand(frame("Open_Palm", 1200L)))

        assertNull(mapper.nextCommand(frame(GestureFrame.NONE, 1233L, 0f)))
        assertNull(mapper.nextCommand(frame("Open_Palm", 1300L)))
        assertEquals(
            DjCommand.PlayPauseDeckA,
            mapper.nextCommand(frame("Open_Palm", 1333L))
        )
    }

    @Test
    fun repeatCommandUsesRepeatIntervalAfterGestureIsStable() {
        val mapper = DjGestureMapper(stableFramesRequired = 2)

        assertNull(mapper.nextCommand(frame("Thumb_Up", 0L)))
        assertEquals(
            DjCommand.VolumeUpDeckA,
            mapper.nextCommand(frame("Thumb_Up", 50L))
        )
        assertNull(mapper.nextCommand(frame("Thumb_Up", 100L)))
        assertNull(mapper.nextCommand(frame("Thumb_Up", 349L)))
        assertEquals(
            DjCommand.VolumeUpDeckA,
            mapper.nextCommand(frame("Thumb_Up", 350L))
        )
    }

    @Test
    fun lowConfidenceFramesDoNotTriggerCommands() {
        val mapper = DjGestureMapper(stableFramesRequired = 2)

        assertNull(mapper.nextCommand(frame("Open_Palm", 0L, 0.2f)))
        assertNull(mapper.nextCommand(frame("Open_Palm", 33L, 0.2f)))
        assertNull(mapper.nextCommand(frame("Open_Palm", 66L)))
        assertEquals(
            DjCommand.PlayPauseDeckA,
            mapper.nextCommand(frame("Open_Palm", 99L))
        )
    }

    @Test
    fun controllerAppliesDeckStateChanges() {
        val controller = DjGestureController(
            DjGestureMapper(stableFramesRequired = 2)
        )

        controller.handle(frame("Open_Palm", 0L))
        val playingSnapshot = controller.handle(frame("Open_Palm", 33L))

        assertTrue(playingSnapshot.deckA.isPlaying)
        assertEquals(DjCommand.PlayPauseDeckA, playingSnapshot.lastAction?.command)

        controller.handle(frame(GestureFrame.NONE, 66L, 0f))
        controller.handle(frame("Open_Palm", 99L))
        val pausedSnapshot = controller.handle(frame("Open_Palm", 132L))

        assertFalse(pausedSnapshot.deckA.isPlaying)
    }

    private fun frame(
        name: String,
        timestampMs: Long,
        score: Float = 0.90f,
    ): GestureFrame = GestureFrame(
        name = name,
        score = score,
        timestampMs = timestampMs,
        handCount = if (name == GestureFrame.NONE) 0 else 1,
        centerX = 0.5f,
        centerY = 0.5f,
    )
}
