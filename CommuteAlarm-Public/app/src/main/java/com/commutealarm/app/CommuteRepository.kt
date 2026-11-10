package com.commutealarm.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "commute_alarm_prefs")

enum class TrafficCondition {
    CLEAR, MODERATE, HEAVY
}

data class AlarmConfig(
    val isEnabled: Boolean = true,
    val targetArrivalHour: Int = 9,
    val targetArrivalMinute: Int = 0,
    val prepMinutes: Int = 45,
    val baselineCommuteMinutes: Int = 45,
    val liveCommuteMinutes: Int = 22,
    val originAddress: String = "38.9907,-77.0261",
    val useGpsOrigin: Boolean = true,
    val destinationAddress: String = "1600 Pennsylvania Ave NW, Washington, DC",
    val destinationLabel: String = "Work",
    val userTomTomApiKey: String = "", // User's optional unlimited TomTom key
    val apiKey: String = "", // User's optional unlimited Google Maps key
    val builtInCallsLastHour: Int = 0,
    val allowOpenSourceFallback: Boolean = false,
    val needsOpenSourcePrompt: Boolean = false,
    val activeProvider: String = "TomTom (0/5 hr)",
    val lastPollEpochMillis: Long = 0L,
    val statusNote: String = "Roads are clear. 23 more minutes of sleep.",
    val isFallbackActive: Boolean = false
) {
    val targetArrivalTime: LocalTime
        get() = LocalTime.of(targetArrivalHour, targetArrivalMinute)

    val effectiveCommuteMinutes: Int
        get() = if (isFallbackActive) baselineCommuteMinutes else liveCommuteMinutes

    val leaveByTime: LocalTime
        get() = targetArrivalTime.minusMinutes(effectiveCommuteMinutes.toLong())

    val dynamicWakeTime: LocalTime
        get() = leaveByTime.minusMinutes(prepMinutes.toLong())

    val baselineWakeTime: LocalTime
        get() = targetArrivalTime.minusMinutes((baselineCommuteMinutes + prepMinutes).toLong())

    val sleepDeltaMinutes: Int
        get() = baselineCommuteMinutes - effectiveCommuteMinutes

    val hasCustomUnlimitedKey: Boolean
        get() = userTomTomApiKey.isNotBlank() || apiKey.isNotBlank()

    val trafficCondition: TrafficCondition
        get() = when {
            effectiveCommuteMinutes <= baselineCommuteMinutes * 0.75 -> TrafficCondition.CLEAR
            effectiveCommuteMinutes <= baselineCommuteMinutes * 1.10 -> TrafficCondition.MODERATE
            else -> TrafficCondition.HEAVY
        }

    fun nextWakeUpEpochMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        var targetDate = now.toLocalDate()
        var wakeDateTime = LocalDateTime.of(targetDate, dynamicWakeTime)
        if (!wakeDateTime.isAfter(now)) {
            targetDate = targetDate.plusDays(1)
            wakeDateTime = LocalDateTime.of(targetDate, dynamicWakeTime)
        }
        return wakeDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

class CommuteRepository(private val context: Context) {

    companion object {
        const val MAX_BUILTIN_CALLS_PER_HOUR = 5
        private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
    }

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val TARGET_HOUR = intPreferencesKey("target_hour")
        val TARGET_MINUTE = intPreferencesKey("target_minute")
        val PREP_MINUTES = intPreferencesKey("prep_minutes")
        val BASELINE_COMMUTE = intPreferencesKey("baseline_commute")
        val LIVE_COMMUTE = intPreferencesKey("live_commute")
        val ORIGIN_ADDRESS = stringPreferencesKey("origin_address")
        val USE_GPS_ORIGIN = booleanPreferencesKey("use_gps_origin")
        val DEST_ADDRESS = stringPreferencesKey("dest_address")
        val DEST_LABEL = stringPreferencesKey("dest_label")
        val USER_TOMTOM_KEY = stringPreferencesKey("user_tomtom_key")
        val API_KEY = stringPreferencesKey("api_key")
        val BUILTIN_TIMESTAMPS = stringPreferencesKey("builtin_tomtom_timestamps")
        val ALLOW_OSRM = booleanPreferencesKey("allow_osrm_fallback")
        val NEEDS_OSRM_PROMPT = booleanPreferencesKey("needs_osrm_prompt")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val LAST_POLL = longPreferencesKey("last_poll")
        val STATUS_NOTE = stringPreferencesKey("status_note")
        val IS_FALLBACK = booleanPreferencesKey("is_fallback")
    }

    private fun parseRecentTimestamps(raw: String?, nowMillis: Long = System.currentTimeMillis()): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { nowMillis - it in 0..ONE_HOUR_MILLIS }
    }

    val configFlow: Flow<AlarmConfig> = context.dataStore.data.map { prefs ->
        val baseline = prefs[Keys.BASELINE_COMMUTE] ?: 45
        val live = prefs[Keys.LIVE_COMMUTE] ?: 22
        val isFallback = prefs[Keys.IS_FALLBACK] ?: false
        val delta = baseline - (if (isFallback) baseline else live)
        val recentCalls = parseRecentTimestamps(prefs[Keys.BUILTIN_TIMESTAMPS]).size

        val defaultStatus = when {
            isFallback -> "Offline failsafe active. Using baseline commute."
            delta > 0 -> "Roads are clear. $delta more minutes of sleep."
            delta < 0 -> "Heavy traffic detected. Waking up ${-delta} min early."
            else -> "Normal traffic conditions along your route."
        }

        AlarmConfig(
            isEnabled = prefs[Keys.IS_ENABLED] ?: true,
            targetArrivalHour = prefs[Keys.TARGET_HOUR] ?: 9,
            targetArrivalMinute = prefs[Keys.TARGET_MINUTE] ?: 0,
            prepMinutes = prefs[Keys.PREP_MINUTES] ?: 45,
            baselineCommuteMinutes = baseline,
            liveCommuteMinutes = live,
            originAddress = prefs[Keys.ORIGIN_ADDRESS] ?: "38.9907,-77.0261",
            useGpsOrigin = prefs[Keys.USE_GPS_ORIGIN] ?: true,
            destinationAddress = prefs[Keys.DEST_ADDRESS] ?: "1600 Pennsylvania Ave NW, Washington, DC",
            destinationLabel = prefs[Keys.DEST_LABEL] ?: "Work",
            userTomTomApiKey = prefs[Keys.USER_TOMTOM_KEY] ?: "",
            apiKey = prefs[Keys.API_KEY] ?: "",
            builtInCallsLastHour = recentCalls,
            allowOpenSourceFallback = prefs[Keys.ALLOW_OSRM] ?: false,
            needsOpenSourcePrompt = prefs[Keys.NEEDS_OSRM_PROMPT] ?: false,
            activeProvider = prefs[Keys.ACTIVE_PROVIDER] ?: "TomTom ($recentCalls/5 hr)",
            lastPollEpochMillis = prefs[Keys.LAST_POLL] ?: System.currentTimeMillis(),
            statusNote = prefs[Keys.STATUS_NOTE] ?: defaultStatus,
            isFallbackActive = isFallback
        )
    }

    suspend fun getSnapshot(): AlarmConfig = configFlow.first()

    /**
     * Checks if the built-in TomTom key has remaining quota (< 5 calls in the last 60 minutes).
     * If allowed, records the new call timestamp and returns the updated usage count (1..5).
     * If maxed out at 5, returns null without consuming a call.
     */
    suspend fun tryConsumeBuiltInTomTomQuota(): Int? {
        var newUsageCount: Int? = null
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val validList = parseRecentTimestamps(prefs[Keys.BUILTIN_TIMESTAMPS], now).toMutableList()
            if (validList.size < MAX_BUILTIN_CALLS_PER_HOUR) {
                validList.add(now)
                prefs[Keys.BUILTIN_TIMESTAMPS] = validList.joinToString(",")
                newUsageCount = validList.size
            } else {
                prefs[Keys.BUILTIN_TIMESTAMPS] = validList.joinToString(",")
                newUsageCount = null
            }
        }
        return newUsageCount
    }

    suspend fun setAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    suspend fun updateTargetArrival(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.TARGET_HOUR] = hour
            it[Keys.TARGET_MINUTE] = minute
        }
    }

    suspend fun updatePrepAndBaseline(prepMinutes: Int, baselineMinutes: Int) {
        context.dataStore.edit {
            it[Keys.PREP_MINUTES] = prepMinutes.coerceIn(5, 180)
            it[Keys.BASELINE_COMMUTE] = baselineMinutes.coerceIn(5, 240)
        }
    }

    suspend fun setAllowOpenSourceFallback(allowed: Boolean) {
        context.dataStore.edit {
            it[Keys.ALLOW_OSRM] = allowed
            if (allowed) it[Keys.NEEDS_OSRM_PROMPT] = false
        }
    }

    suspend fun updateLocationsAndApi(
        originAddress: String,
        useGps: Boolean,
        destinationAddress: String,
        destinationLabel: String,
        userTomTomKey: String,
        googleApiKey: String,
        allowOpenSourceFallback: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.ORIGIN_ADDRESS] = originAddress.trim()
            it[Keys.USE_GPS_ORIGIN] = useGps
            it[Keys.DEST_ADDRESS] = destinationAddress.trim()
            it[Keys.DEST_LABEL] = destinationLabel.trim().ifEmpty { "Work" }
            it[Keys.USER_TOMTOM_KEY] = userTomTomKey.trim()
            it[Keys.API_KEY] = googleApiKey.trim()
            it[Keys.ALLOW_OSRM] = allowOpenSourceFallback
            if (allowOpenSourceFallback) it[Keys.NEEDS_OSRM_PROMPT] = false
        }
    }

    suspend fun recordLiveTrafficUpdate(
        commuteMinutes: Int,
        isFallback: Boolean,
        providerName: String,
        promptOpenSource: Boolean = false,
        customReason: String? = null
    ) {
        context.dataStore.edit { prefs ->
            val baseline = prefs[Keys.BASELINE_COMMUTE] ?: 45
            prefs[Keys.LIVE_COMMUTE] = commuteMinutes
            prefs[Keys.IS_FALLBACK] = isFallback
            prefs[Keys.ACTIVE_PROVIDER] = providerName
            prefs[Keys.NEEDS_OSRM_PROMPT] = promptOpenSource
            prefs[Keys.LAST_POLL] = System.currentTimeMillis()

            val effective = if (isFallback) baseline else commuteMinutes
            val delta = baseline - effective
            prefs[Keys.STATUS_NOTE] = customReason ?: when {
                isFallback -> "Primary APIs unavailable. Failsafe baseline alarm locked."
                delta > 0 -> "Roads are clear ($providerName). $delta more minutes of sleep."
                delta < 0 -> "Congestion ahead ($providerName). Alarm moved ${-delta} min earlier."
                else -> "Traffic is flowing at typical speeds ($providerName)."
            }
        }
    }
}

object TimeFormatters {
    private val time12h = DateTimeFormatter.ofPattern("h:mm")
    private val amPm = DateTimeFormatter.ofPattern("a")
    private val full12h = DateTimeFormatter.ofPattern("h:mm a")

    fun formatDigits(time: LocalTime): String = time.format(time12h)
    fun formatAmPm(time: LocalTime): String = time.format(amPm)
    fun formatFull(time: LocalTime): String = time.format(full12h)

    fun formatEpochTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Just now"
        val localTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return formatFull(localTime)
    }
}