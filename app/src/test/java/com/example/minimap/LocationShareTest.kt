package com.example.minimap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class LocationShareTest {
    private val time = LocalDateTime.of(2026, 9, 27, 11, 44, 11)

    @Test fun matchesRequestedTaiwanExample() {
        assertEquals(
            "2026/09/27 11:44:11\n22.985160°, 120.196722°\n" +
                "https://www.google.com.tw/maps/place/22°59'06.6\"N+120°11'48.2\"E",
            LocationShare.format(22.985160, 120.196722, time)
        )
    }

    @Test fun handlesSouthernAndWesternHemispheres() {
        assertEquals(
            "2026/09/27 11:44:11\n-22.985160°, -120.196722°\n" +
                "https://www.google.com.tw/maps/place/22°59'06.6\"S+120°11'48.2\"W",
            LocationShare.format(-22.985160, -120.196722, time)
        )
    }

    @Test fun roundsSecondsAcrossMinuteAndDegreeBoundaries() {
        val nearBoundary = 22 + 59 / 60.0 + 59.96 / 3600.0
        assertEquals(
            "https://www.google.com.tw/maps/place/23°00'00.0\"N+0°00'00.0\"E",
            LocationShare.format(nearBoundary, 0.0, time).lineSequence().last()
        )
    }
}
