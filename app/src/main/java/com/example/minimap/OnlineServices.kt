package com.example.minimap

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class SearchResult(val name: String, val latitude: Double, val longitude: Double)

object OnlineServices {
    private var lastSearchAt = 0L
    private val searchCache = LinkedHashMap<String, List<SearchResult>>()

    // Only called for explicit submissions, never for every keystroke. Nominatim: <= 1 request/s.
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        searchCache[query]?.let { return@withContext it }
        val wait = 1100 - (System.currentTimeMillis() - lastSearchAt)
        if (wait > 0) delay(wait)
        lastSearchAt = System.currentTimeMillis()
        val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=${Uri.encode(query)}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "miniMap-Android/1.0 (com.example.minimap)")
            setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            if (connection.responseCode != 200) error("搜尋服務暫時無法使用（${connection.responseCode}）")
            val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val matches = (0 until results.length()).mapNotNull { i ->
                val item = results.getJSONObject(i)
                val lat = item.optString("lat").toDoubleOrNull()
                val lon = item.optString("lon").toDoubleOrNull()
                if (lat == null || lon == null) null
                else SearchResult(item.optString("display_name"), lat, lon)
            }
            if (searchCache.size >= 20) searchCache.remove(searchCache.keys.first())
            searchCache[query] = matches
            matches
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("MissingPermission") // The caller checks location permission before invoking this function.
    suspend fun currentLocation(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(15000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                try {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                } catch (e: Exception) {
                    manager.removeUpdates(listener)
                    continuation.cancel(e)
                }
            }
        }
    }
}
