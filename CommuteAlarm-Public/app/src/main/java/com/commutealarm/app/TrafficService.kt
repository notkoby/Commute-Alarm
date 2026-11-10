package com.commutealarm.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.net.Uri
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

sealed class TrafficPollResult {
    data class Success(
        val durationMinutes: Int,
        val provider: String,
        val noteOverride: String? = null
    ) : TrafficPollResult()

    data class Fallback(
        val baselineMinutes: Int,
        val reason: String,
        val canPromptOpenSource: Boolean = false
    ) : TrafficPollResult()
}

@Serializable
data class ComputeRoutesRequest(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val travelMode: String = "DRIVE",
    val routingPreference: String = "TRAFFIC_AWARE_OPTIMAL"
)

@Serializable
data class RouteWaypoint(
    val address: String? = null,
    val location: RouteLocation? = null
)

@Serializable
data class RouteLocation(
    val latLng: RouteLatLng
)

@Serializable
data class RouteLatLng(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ComputeRoutesResponse(
    val routes: List<RouteEntry>? = null
)

@Serializable
data class RouteEntry(
    val duration: String? = null,
    val distanceMeters: Int? = null
)

class TrafficService(private val context: Context) {

    companion object {
        // Loaded from local.properties via BuildConfig in Main project; scrubbed blank in Public copy
        val DEFAULT_TOMTOM_API_KEY: String
            get() = BuildConfig.DEFAULT_TOMTOM_KEY

        private val geocodeCache = ConcurrentHashMap<String, Pair<Double, Double>>()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun fetchLiveCommuteDuration(
        config: AlarmConfig,
        repository: CommuteRepository,
        isTwentyMinPreAlarmCheck: Boolean = false
    ): TrafficPollResult = withContext(Dispatchers.IO) {

        // Tier 1: User-Added Custom Google Maps API Key (Unlimited)
        if (config.apiKey.isNotBlank()) {
            val googleResult = tryGoogleRoutes(config, config.apiKey)
            if (googleResult != null) {
                return@withContext TrafficPollResult.Success(googleResult, "Google Maps")
            }
        }

        // Tier 2: User-Added Custom TomTom API Key (Unlimited)
        if (config.userTomTomApiKey.isNotBlank()) {
            val customTomTomResult = tryTomTomRoutes(config, config.userTomTomApiKey)
            if (customTomTomResult != null) {
                return@withContext TrafficPollResult.Success(customTomTomResult, "TomTom (Custom)")
            }
        }

        // Tier 3: Built-In Default TomTom Key (Max 5 calls/hour; 5th slot reserved for 20-min pre-alarm check)
        var wasRateLimited = false
        if (DEFAULT_TOMTOM_API_KEY.isNotBlank()) {
            val consumedCount = repository.tryConsumeBuiltInTomTomQuota(isUrgentPreAlarmCheck = isTwentyMinPreAlarmCheck)
            if (consumedCount != null) {
                val defaultTomTomResult = tryTomTomRoutes(config, DEFAULT_TOMTOM_API_KEY)
                if (defaultTomTomResult != null) {
                    return@withContext TrafficPollResult.Success(
                        durationMinutes = defaultTomTomResult,
                        provider = "TomTom ($consumedCount/5 hr)"
                    )
                }
            } else {
                wasRateLimited = true
            }
        }

        // Tier 4: Open-Source OSRM Rollback (Only if user gave permission)
        if (config.allowOpenSourceFallback) {
            val osrmResult = tryOpenSourceOsrm(config)
            if (osrmResult != null) {
                val providerLabel = if (wasRateLimited) "OSRM (5/5 hr cap)" else "OpenStreetMap"
                val note = if (!config.letOsrmControlAlarm) {
                    "Open-Source shows $osrmResult min drive. Alarm locked to failsafe baseline (${config.baselineCommuteMinutes} min + ${config.prepMinutes} min prep)."
                } else {
                    null
                }
                return@withContext TrafficPollResult.Success(
                    durationMinutes = osrmResult,
                    provider = providerLabel,
                    noteOverride = note
                )
            }
        }

        // Critical Rule: At the 20-minute pre-alarm mark (or if TomTom is unreachable and OSRM alarm control is off),
        // automatically fall back to failsafe baseline drive time + prep time!
        if (isTwentyMinPreAlarmCheck) {
            return@withContext TrafficPollResult.Fallback(
                baselineMinutes = config.baselineCommuteMinutes,
                reason = "20-min pre-alarm check: TomTom unavailable. Alarm set to failsafe baseline (${config.baselineCommuteMinutes}m drive + ${config.prepMinutes}m prep).",
                canPromptOpenSource = !config.allowOpenSourceFallback
            )
        }

        if (wasRateLimited) {
            return@withContext TrafficPollResult.Fallback(
                baselineMinutes = config.baselineCommuteMinutes,
                reason = "Built-in TomTom key reached 5/5 requests this hour. Alarm defaulted to failsafe baseline (${config.baselineCommuteMinutes}m drive + ${config.prepMinutes}m prep).",
                canPromptOpenSource = !config.allowOpenSourceFallback
            )
        }

        return@withContext TrafficPollResult.Fallback(
            baselineMinutes = config.baselineCommuteMinutes,
            reason = "TomTom unreachable. Alarm set to failsafe baseline (${config.baselineCommuteMinutes}m drive + ${config.prepMinutes}m prep).",
            canPromptOpenSource = !config.allowOpenSourceFallback
        )
    }

    private suspend fun tryGoogleRoutes(config: AlarmConfig, googleKey: String): Int? {
        return try {
            val activeTomTomKey = config.userTomTomApiKey.ifBlank { DEFAULT_TOMTOM_API_KEY }
            val originCoords = resolveOriginCoordinates(config, activeTomTomKey, false)
            val originWaypoint = if (originCoords != null) {
                RouteWaypoint(location = RouteLocation(RouteLatLng(originCoords.first, originCoords.second)))
            } else {
                parseWaypointString(config.originAddress)
            }
            val destWaypoint = parseWaypointString(config.destinationAddress)

            val requestPayload = ComputeRoutesRequest(origin = originWaypoint, destination = destWaypoint)
            val requestBodyString = json.encodeToString(ComputeRoutesRequest.serializer(), requestPayload)
            val request = Request.Builder()
                .url("https://routes.googleapis.com/directions/v2:computeRoutes")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Goog-Api-Key", googleKey)
                .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
                .post(requestBodyString.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(ComputeRoutesResponse.serializer(), bodyStr)
                val durationRaw = parsed.routes?.firstOrNull()?.duration ?: return null
                val seconds = durationRaw.removeSuffix("s").toDoubleOrNull() ?: return null
                if (seconds <= 0) return null
                ceil(seconds / 60.0).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryTomTomRoutes(config: AlarmConfig, tomtomKey: String): Int? {
        if (tomtomKey.isBlank()) return null
        return try {
            val origin = resolveOriginCoordinates(config, tomtomKey, false) ?: return null
            val dest = resolveCoordinatesFromInput(config.destinationAddress, tomtomKey, false) ?: return null

            val locationsPath = "${origin.first},${origin.second}:${dest.first},${dest.second}"
            val url = "https://api.tomtom.com/routing/1/calculateRoute/$locationsPath/json?traffic=true&travelMode=car&key=$tomtomKey"
            val request = Request.Builder().url(url).get().build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val routes = root["routes"]?.jsonArray
                if (routes.isNullOrEmpty()) return null
                val summary = routes[0].jsonObject["summary"]?.jsonObject ?: return null
                val seconds = summary["travelTimeInSeconds"]?.jsonPrimitive?.intOrNull ?: return null
                if (seconds <= 0) return null
                ceil(seconds / 60.0).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryOpenSourceOsrm(config: AlarmConfig): Int? {
        return try {
            val activeTomTomKey = config.userTomTomApiKey.ifBlank { DEFAULT_TOMTOM_API_KEY }
            val origin = resolveOriginCoordinates(config, activeTomTomKey, true) ?: return null
            val dest = resolveCoordinatesFromInput(config.destinationAddress, activeTomTomKey, true) ?: return null

            val coordsPath = "${origin.second},${origin.first};${dest.second},${dest.first}"
            val url = "https://router.project-osrm.org/route/v1/driving/$coordsPath?overview=false"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CommuteAlarmAndroidApp/2.5")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val routes = root["routes"]?.jsonArray
                if (routes.isNullOrEmpty()) return null
                val durationSeconds = routes[0].jsonObject["duration"]?.jsonPrimitive?.doubleOrNull ?: return null
                if (durationSeconds <= 0.0) return null
                ceil(durationSeconds / 60.0).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveOriginCoordinates(
        config: AlarmConfig,
        tomtomKey: String,
        allowNominatim: Boolean
    ): Pair<Double, Double>? {
        if (config.useGpsOrigin) {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val tokenSource = CancellationTokenSource()
                val location: Location? = fusedClient
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                    .await()
                if (location != null) {
                    return Pair(location.latitude, location.longitude)
                }
            } catch (_: Exception) {
            }
        }
        return resolveCoordinatesFromInput(config.originAddress, tomtomKey, allowNominatim)
            ?: Pair(38.9907, -77.0261)
    }

    private fun resolveCoordinatesFromInput(
        input: String,
        tomtomKey: String,
        allowNominatim: Boolean
    ): Pair<Double, Double>? {
        val normalizedKey = input.trim().lowercase()
        geocodeCache[normalizedKey]?.let { return it }

        val parts = input.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null) {
                val pair = Pair(lat, lon)
                geocodeCache[normalizedKey] = pair
                return pair
            }
        }

        if (tomtomKey.isNotBlank()) {
            try {
                val encoded = Uri.encode(input)
                val url = "https://api.tomtom.com/search/2/geocode/$encoded.json?limit=1&key=$tomtomKey"
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        val results = root["results"]?.jsonArray
                        if (!results.isNullOrEmpty()) {
                            val pos = results[0].jsonObject["position"]?.jsonObject
                            val lat = pos?.get("lat")?.jsonPrimitive?.doubleOrNull
                            val lon = pos?.get("lon")?.jsonPrimitive?.doubleOrNull
                            if (lat != null && lon != null) {
                                val pair = Pair(lat, lon)
                                geocodeCache[normalizedKey] = pair
                                return pair
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (allowNominatim) {
            try {
                val encoded = Uri.encode(input)
                val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CommuteAlarmAndroidApp/2.5")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val arr = json.parseToJsonElement(body).jsonArray
                        if (arr.isNotEmpty()) {
                            val obj = arr[0].jsonObject
                            val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                val pair = Pair(lat, lon)
                                geocodeCache[normalizedKey] = pair
                                return pair
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun parseWaypointString(input: String): RouteWaypoint {
        val parts = input.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lng = parts[1].trim().toDoubleOrNull()
            if (lat != null && lng != null) {
                return RouteWaypoint(location = RouteLocation(RouteLatLng(lat, lng)))
            }
        }
        return RouteWaypoint(address = input)
    }
}