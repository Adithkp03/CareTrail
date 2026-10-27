package com.caretrail.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sprint 3 of the Health-a-thon clinical PRD: geo / PIN-code facility locator
 * (< 10 km) and blood bank connectivity.
 *
 * Free-tier only: OpenStreetMap data via the public Overpass API for facilities
 * and blood banks, Nominatim for PIN-code geocoding. No keys, no paid services.
 * Directions open in whatever maps app is on the phone (geo: intent).
 * Results and the last location are cached locally for offline viewing.
 */

@Serializable
data class Facility(
    val id: String = "",
    val name: String = "",
    val kind: String = "",          // hospital, clinic, doctors, blood_bank
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val phone: String = "",
    val address: String = "",
    val government: Boolean = false,
    val distance_km: Double = 0.0,
)

@Serializable
data class LastLocation(val lat: Double = 0.0, val lon: Double = 0.0, val label: String = "")

object Geo {
    /** Great-circle distance in km. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Government facility heuristic from OSM operator tags and naming. */
    fun isGovernment(tags: Map<String, String>, name: String): Boolean {
        val op = (tags["operator:type"] ?: tags["operator"] ?: "").lowercase()
        if (op.contains("government") || op.contains("public")) return true
        val n = name.lowercase()
        return listOf("government", "govt", "phc", "chc", "district hospital", "medical college", "taluk", "civil hospital", "general hospital").any { it in n }
    }

    fun kindLabel(kind: String): String = when (kind) {
        "hospital" -> "Hospital"
        "clinic" -> "Clinic / PHC"
        "doctors" -> "Doctor"
        "blood_bank" -> "Blood bank"
        else -> kind
    }

    /** Parse an Overpass JSON response into facilities within [radiusKm], nearest first. */
    fun parseOverpass(json: String, lat: Double, lon: Double, radiusKm: Double): List<Facility> {
        val root = AppJson.parseToJsonElement(json).jsonObject
        val elements = (root["elements"] as? JsonArray) ?: return emptyList()
        val out = mutableListOf<Facility>()
        for (el in elements) {
            val o = el as? JsonObject ?: continue
            val id = "${o["type"]?.jsonPrimitive?.content}/${o["id"]?.jsonPrimitive?.longOrNull ?: continue}"
            val tagsObj = (o["tags"] as? JsonObject) ?: continue
            val tags = tagsObj.entries.associate { it.key to it.value.jsonPrimitive.content }
            val name = tags["name"] ?: continue   // unnamed map nodes are useless in a directory
            val elLat = o["lat"]?.jsonPrimitive?.doubleOrNull
                ?: (o["center"] as? JsonObject)?.get("lat")?.jsonPrimitive?.doubleOrNull ?: continue
            val elLon = o["lon"]?.jsonPrimitive?.doubleOrNull
                ?: (o["center"] as? JsonObject)?.get("lon")?.jsonPrimitive?.doubleOrNull ?: continue
            val amenity = tags["amenity"] ?: ""
            val healthcare = tags["healthcare"] ?: ""
            val kind = when {
                healthcare == "blood_bank" || amenity == "blood_donation" -> "blood_bank"
                amenity == "hospital" -> "hospital"
                amenity == "clinic" -> "clinic"
                amenity == "doctors" -> "doctors"
                else -> continue
            }
            val dist = haversineKm(lat, lon, elLat, elLon)
            if (dist > radiusKm) continue
            val addr = listOfNotNull(tags["addr:street"], tags["addr:city"] ?: tags["addr:place"], tags["addr:postcode"]).joinToString(", ")
            out.add(
                Facility(
                    id = id, name = name, kind = kind, lat = elLat, lon = elLon,
                    phone = tags["phone"] ?: tags["contact:phone"] ?: "",
                    address = addr,
                    government = isGovernment(tags, name),
                    distance_km = Math.round(dist * 10.0) / 10.0,
                )
            )
        }
        return out.sortedBy { it.distance_km }
    }
}

/** Nominatim (PIN -> coordinates) and Overpass (coordinates -> facilities). Free, key-less OSM services. */
class FacilityApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    class GeoException(message: String) : Exception(message)

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "CareTrail/1.0 (maternal health app; OSM data)").build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw GeoException("Service error ${res.code}")
            return body
        }
    }

    /** Geocode an Indian PIN code (or place name) to coordinates via Nominatim. */
    fun geocodePin(pin: String): LastLocation {
        val q = URLEncoder.encode(if (pin.all { it.isDigit() }) "$pin, India" else pin, "UTF-8")
        val json = get("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=in&q=$q")
        val arr = AppJson.parseToJsonElement(json).jsonArray
        val first = arr.firstOrNull() as? JsonObject ?: throw GeoException("Place not found - check the PIN code")
        val lat = first["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: throw GeoException("Place not found")
        val lon = first["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: throw GeoException("Place not found")
        val label = first["display_name"]?.jsonPrimitive?.content?.take(60) ?: pin
        return LastLocation(lat, lon, label)
    }

    private fun overpassQuery(lat: Double, lon: Double, radiusM: Int): String = """
        [out:json][timeout:25];
        (
          node(around:$radiusM,$lat,$lon)[amenity~"^(hospital|clinic|doctors)$"];
          way(around:$radiusM,$lat,$lon)[amenity~"^(hospital|clinic)$"];
          node(around:$radiusM,$lat,$lon)[healthcare=blood_bank];
          way(around:$radiusM,$lat,$lon)[healthcare=blood_bank];
          node(around:$radiusM,$lat,$lon)[amenity=blood_donation];
        );
        out center 80;
    """.trimIndent()

    /** Facilities within radiusKm of a point. Tries a second Overpass mirror if the first fails. */
    fun nearby(lat: Double, lon: Double, radiusKm: Int = 10): List<Facility> {
        val q = URLEncoder.encode(overpassQuery(lat, lon, radiusKm * 1000), "UTF-8")
        var lastError: Exception? = null
        for (host in listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")) {
            try {
                return Geo.parseOverpass(get("$host?data=$q"), lat, lon, radiusKm.toDouble())
            } catch (e: Exception) { lastError = e }
        }
        throw GeoException(lastError?.message ?: "Facility service unavailable")
    }
}

// ---- Local cache (offline viewing) ---------------------------------------------

private fun Store.getS3(key: String): String? = getCache("s3:$key")
private fun Store.putS3(key: String, json: String) = putCache("s3:$key", json)

fun Store.lastLocation(): LastLocation? =
    getS3("location")?.let { try { AppJson.decodeFromString<LastLocation>(it) } catch (_: Exception) { null } }

fun Store.saveLastLocation(v: LastLocation) = putS3("location", AppJson.encodeToString(LastLocation.serializer(), v))

fun Store.cachedFacilities(): List<Facility> =
    getS3("facilities")?.let { try { AppJson.decodeFromString<List<Facility>>(it) } catch (_: Exception) { null } } ?: emptyList()

fun Store.saveCachedFacilities(v: List<Facility>) =
    putS3("facilities", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Facility.serializer()), v))
