package com.example.minimap

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoJsonTest {
    @Test fun roundTripUsesLongitudeLatitudeOrderAndKeepsNotes() {
        val place = Place("id", "台北車站", "出口旁", 25.0478, 121.5170)
        val json = GeoJson.encode(listOf(place))
        val (imported, invalid) = GeoJson.decode(json)
        assertEquals(0, invalid)
        assertEquals(1, imported.size)
        assertEquals(place.name, imported.single().name)
        assertEquals(place.note, imported.single().note)
        assertEquals(place.latitude, imported.single().latitude, 0.0)
        assertEquals(place.longitude, imported.single().longitude, 0.0)
    }

    @Test fun invalidCoordinatesAndNonPointsAreSkipped() {
        val json = """{"type":"FeatureCollection","features":[
            {"type":"Feature","geometry":{"type":"Point","coordinates":[181,25]},"properties":{}},
            {"type":"Feature","geometry":{"type":"LineString","coordinates":[]},"properties":{}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[121,25]},"properties":{"name":"保留"}}
        ]}"""
        val (imported, invalid) = GeoJson.decode(json)
        assertEquals(2, invalid)
        assertEquals("保留", imported.single().name)
    }
}
