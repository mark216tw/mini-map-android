package com.example.minimap

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object LocationShare {
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.TAIWAN)

    fun format(latitude: Double, longitude: Double, time: LocalDateTime): String {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)

        val coordinate = String.format(Locale.US, "%.6f°, %.6f°", latitude, longitude)
        val url = "https://www.google.com.tw/maps/place/" +
            degreesMinutesSeconds(latitude, "N", "S") + "+" +
            degreesMinutesSeconds(longitude, "E", "W")
        return "${time.format(timeFormat)}\n$coordinate\n$url"
    }

    private fun degreesMinutesSeconds(value: Double, positive: String, negative: String): String {
        // Round the whole angle to a tenth of a second so 59.96 seconds carries into a minute.
        val tenths = (abs(value) * 36_000).roundToLong()
        val degrees = tenths / 36_000
        val minutes = tenths % 36_000 / 600
        val secondsTenths = tenths % 600
        return String.format(
            Locale.US, "%d°%02d'%02d.%d\"%s",
            degrees, minutes, secondsTenths / 10, secondsTenths % 10,
            if (value < 0) negative else positive
        )
    }
}
