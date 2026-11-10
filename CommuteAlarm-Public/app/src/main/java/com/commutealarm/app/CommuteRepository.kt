package com.commutealarm.app

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
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
    val isEnabled: Boolean = false, // Defaults to OFF on first install
    val targetArrivalHour: Int = 9,
    val targetArrivalMinute: Int = 0,
    val prepMinutes: Int = 45,
    val baselineCommuteMinutes: Int = 45,
    val liveCommuteMinutes: Int = 22,
    val originAddress: String = "38.9907,-77.0261",
    val useGpsOrigin: Boolean = true,
    val destinationAddress: String = "1600 Pennsylvania Ave NW, Washington, DC",
    val destinationLabel: String = "Work",
    val userTomTomApiKey: String = "",
    val apiKey: String = "",
    val builtInCallsLastHour: Int = 0,
    val allowOpenSourceFallback: Boolean = false,
    val letOsrmControlAlarm: Boolean = false, // Unless enabled, OSRM won't shift alarm time
    val needsOpenSourcePrompt: Boolean = false,
    val activeProvider: String = "TomTom (0/5 hr)",
    val lastPollEpochMillis: Long = 0L,
    val statusNote: String = "Alarm paused. Enable switch above to schedule.",
    val isFallbackActive: Boolean = false
) {
    val targetArrivalTime: LocalTime
        get() = LocalTime.of(targetArrivalHour, targetArrivalMinute)

    // Uses baseline commute if fallback is active OR if OSRM is the active provider while letOsrmControlAlarm is false
    val usesBaselineForSchedule: Boolean
        get() = isFallbackActive || (activeProvider.startsWith("OSRM") || activeProvider.startsWith("OpenStreetMap")) && !letOsrmControlAlarm

    val effectiveCommuteMinutes: Int
        get() = if (usesBaselineForSchedule) baselineCommuteMinutes else liveCommuteMinutes

    val displayedDriveMinutes: Int
        get() = if (isFallbackActive) baselineCommuteMinutes else liveCommuteMinutes

    val leaveByTime: LocalTime
        get() = targetArrivalTime.minusMinutes(effectiveCommuteMinutes.toLong())

    val dynamicWakeTime: LocalTime
        get() = leaveByTime.minusMinutes(prepMinutes.toLong())

    val baselineWakeTime: LocalTime
        get() = targetArrivalTime.minusMinutes((baselineCommuteMinutes + prepMinutes).toLong())

    val sleepDeltaMinutes: Int
        get() = baselineCommuteMinutes - effectiveCommuteMinutes

    val trafficCondition: TrafficCondition
        get() = when {
            displayedDriveMinutes <= baselineCommuteMinutes * 0.75 -> TrafficCondition.CLEAR
            displayedDriveMinutes <= baselineCommuteMinutes * 1.10 -> TrafficCondition.MODERATE
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
        val LET_OSRM_CONTROL_ALARM = booleanPreferencesKey("let_osrm_control_alarm")
        val NEEDS_OSRM_PROMPT = booleanPreferencesKey("needs_osrm_prompt")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val LAST_POLL = longPreferencesKey("last_poll")
        val STATUS_NOTE = stringPreferencesKey("status_note")
        val IS_FALLBACK = booleanPreferencesKey("is_fallback")
    }

    @SuppressLint("HardwareIds")
    private fun getHardwareBoundQuotaFile(): File {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "default_hw"
        // Stored in system cache/no_backup & mirrored with hardware ID so standard data clears or auto-backups track it
        return File(context.filesDir, ".quota_lock_$androidId.dat")
    }

    private fun parseRecentTimestamps(raw: String?, nowMillis: Long = System.currentTimeMillis()): List<Long> {
        val fromPrefs = raw.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
        val hwFile = getHardwareBoundQuotaFile()
        val fromHw = if (hwFile.exists()) {
            hwFile.readText().split(",").mapNotNull { it.trim().toLongOrNull() }
        } else {
            emptyList()
        }
        return (fromPrefs + fromHw)
            .distinct()
            .filter { nowMillis - it in 0..ONE_HOUR_MILLIS }
            .sorted()
    }

    val configFlow: Flow<AlarmConfig> = context.dataStore.data.map { prefs ->
        val isEnabled = prefs[Keys.IS_ENABLED] ?: false
        val baseline = prefs[Keys.BASELINE_COMMUTE] ?: 45
        val live = prefs[Keys.LIVE_COMMUTE] ?: 22
        val isFallback = prefs[Keys.IS_FALLBACK] ?: false
        val letOsrmControl = prefs[Keys.LET_OSRM_CONTROL_ALARM] ?: false
        val provider = prefs[Keys.ACTIVE_PROVIDER] ?: "TomTom"
        val recentCalls = parseRecentTimestamps(prefs[Keys.BUILTIN_TIMESTAMPS]).size

        val usesBaseline = isFallback || ((provider.startsWith("OSRM") || provider.startsWith("OpenStreetMap")) && !letOsrmControl)
        val effective = if (usesBaseline) baseline else live
        val delta = baseline - effective

        val defaultStatus = when {
            !isEnabled -> "Alarm is off. Toggle the switch at the top right to activate."
            isFallback -> "Failsafe active: Alarm set to baseline drive ($baseline min) + prep time."
            usesBaseline -> "Open-Source route is $live min, but alarm is locked to baseline ($baseline min)."
            delta > 0 -> "Roads are clear. $delta more minutes of sleep."
            delta < 0 -> "Heavy traffic detected. Waking up ${-delta} min early."
            else -> "Normal traffic conditions along your route."
        }

        AlarmConfig(
            isEnabled = isEnabled,
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
            letOsrmControlAlarm = letOsrmControl,
            needsOpenSourcePrompt = prefs[Keys.NEEDS_OSRM_PROMPT] ?: false,
            activeProvider = prefs[Keys.ACTIVE_PROVIDER] ?: "TomTom ($recentCalls/5 hr)",
            lastPollEpochMillis = prefs[Keys.LAST_POLL] ?: 0L,
            statusNote = prefs[Keys.STATUS_NOTE] ?: defaultStatus,
            isFallbackActive = isFallback
        )
    }

    suspend fun getSnapshot(): AlarmConfig = configFlow.first()

    /**
     * Enforces max 5 calls/hour on the built-in TomTom key.
     * Reserves the 5th slot for the critical 20-minute pre-alarm check so manual refreshes don't waste it.
     */
    suspend fun tryConsumeBuiltInTomTomQuota(isUrgentPreAlarmCheck: Boolean = false): Int? {
        var newUsageCount: Int? = null
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val validList = parseRecentTimestamps(prefs[Keys.BUILTIN_TIMESTAMPS], now).toMutableList()
            val maxAllowed = if (isUrgentPreAlarmCheck) MAX_BUILTIN_CALLS_PER_HOUR else (MAX_BUILTIN_CALLS_PER_HOUR - 1)
            if (validList.size < maxAllowed || (validList.size < MAX_BUILTIN_CALLS_PER_HOUR && !prefs[Keys.IS_ENABLED, false])) {
                validList.add(now)
                val serialized = validList.joinToString(",")
                prefs[Keys.BUILTIN_TIMESTAMPS] = serialized
                runCatching { getHardwareBoundQuotaFile().writeText(serialized) }
                newUsageCount = validList.size
            } else {
                val serialized = validList.joinToString(",")
                prefs[Keys.BUILTIN_TIMESTAMPS] = serialized
                runCatching { getHardwareBoundQuotaFile().writeText(serialized) }
                newUsageCount = null
            }
        }
        return newUsageCount
    }

    private operator fun Preferences.get(key: Preferences.Key<Boolean>, default: Boolean): Boolean {
        return this[key] ?: default
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
            it[Keys.PREP_MINUTES] = prepMinutes.coerceIn(1, 300)
            it[Keys.BASELINE_COMMUTE] = baselineMinutes.coerceIn(1, 360)
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
        allowOpenSourceFallback: Boolean,
        letOsrmControlAlarm: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.ORIGIN_ADDRESS] = originAddress.trim()
            it[Keys.USE_GPS_ORIGIN] = useGps
            it[Keys.DEST_ADDRESS] = destinationAddress.trim()
            it[Keys.DEST_LABEL] = destinationLabel.trim().ifEmpty { "Work" }
            it[Keys.USER_TOMTOM_KEY] = userTomTomKey.trim()
            it[Keys.API_KEY] = googleApiKey.trim()
            it[Keys.ALLOW_OSRM] = allowOpenSourceFallback
            it[Keys.LET_OSRM_CONTROL_ALARM] = if (allowOpenSourceFallback) letOsrmControlAlarm else false
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
            val letOsrmControl = prefs[Keys.LET_OSRM_CONTROL_ALARM] ?: false
            prefs[Keys.LIVE_COMMUTE] = commuteMinutes
            prefs[Keys.IS_FALLBACK] = isFallback
            prefs[Keys.ACTIVE_PROVIDER] = providerName
            prefs[Keys.NEEDS_OSRM_PROMPT] = promptOpenSource
            prefs[Keys.LAST_POLL] = System.currentTimeMillis()

            val isOsrmProvider = providerName.startsWith("OSRM") || providerName.startsWith("OpenStreetMap")
            val usesBaseline = isFallback || (isOsrmProvider && !letOsrmControl)
            val effective = if (usesBaseline) baseline else commuteMinutes
            val delta = baseline - effective

            prefs[Keys.STATUS_NOTE] = customReason ?: when {
                isFallback -> "TomTom unavailable. Alarm locked to failsafe baseline ($baseline min drive + prep)."
                isOsrmProvider && !letOsrmControl -> "Open-Source shows $commuteMinutes min drive, but alarm uses failsafe baseline ($baseline min)."
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
        if (epochMillis <= 0L) return "Not synced yet"
        val localTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return formatFull(localTime)
    }
}