package example.nucleus.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerTimeFormatTest {

    @Test
    fun clockUsesMinutesUntilAnHourThenHours() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(-5_000))
        assertEquals("1:05", formatClock(65_000))
        assertEquals("1:02:05", formatClock(3_725_000))
    }

    @Test
    fun videoDurationHidesUnknownValues() {
        assertEquals("", formatVideoDuration(0))
        assertEquals("", formatVideoDuration(-1))
        assertEquals("0:59", formatVideoDuration(59))
        assertEquals("1:00:00", formatVideoDuration(3_600))
    }

    @Test
    fun miniPlayerKeepsMinutesWithoutHours() {
        assertEquals("1:05", formatMiniTime(65_000))
        assertEquals("60:00", formatMiniTime(3_600_000))
    }

    @Test
    fun queueDurationUsesHourMinuteWording() {
        assertEquals("", formatQueueDuration(0))
        assertEquals("1 min", formatQueueDuration(90))
        assertEquals("1 h 0 min", formatQueueDuration(3_600))
        assertEquals("1 h 2 min", formatQueueDuration(3_725))
    }
}
