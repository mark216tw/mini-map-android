package com.example.minimap

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object GeoJson {
    fun encode(places: List<Place>): String {
        val features = JSONArray()
        places.forEach { place ->
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().put(place.longitude).put(place.latitude))
                })
                put("properties", JSONObject().apply {
                    put("name", place.name)
                    put("note", place.note)
                })
            })
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString(2)
    }

    // Accept valid Point features only. Coordinates in GeoJSON are [longitude, latitude].
    fun decode(text: String): Pair<List<Place>, Int> {
        val root = JSONObject(text)
        require(root.optString("type") == "FeatureCollection") { "檔案不是 GeoJSON FeatureCollection" }
        val features = root.getJSONArray("features")
        val places = mutableListOf<Place>()
        var invalid = 0
        for (i in 0 until features.length()) {
            try {
                val feature = features.getJSONObject(i)
                require(feature.getString("type") == "Feature")
                val geometry = feature.getJSONObject("geometry")
                require(geometry.getString("type") == "Point")
                val coords = geometry.getJSONArray("coordinates")
                val lon = coords.getDouble(0)
                val lat = coords.getDouble(1)
                require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
                val properties = feature.optJSONObject("properties")
                places.add(Place(
                    id = UUID.randomUUID().toString(),
                    name = properties?.optString("name")?.trim()?.take(100)?.ifBlank { "未命名地標" }
                        ?: "未命名地標",
                    note = properties?.optString("note")?.take(2000) ?: "",
                    latitude = lat,
                    longitude = lon
                ))
            } catch (_: Exception) {
                invalid++
            }
        }
        return places to invalid
    }
}
