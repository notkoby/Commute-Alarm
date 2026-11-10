package com.commutealarm.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CommuteUiState(
    val config: AlarmConfig = AlarmConfig(),
    val isPollingTraffic: Boolean = false
)

class CommuteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CommuteRepository(application)
    private val trafficService = TrafficService(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _isPolling = MutableStateFlow(false)

    val uiState: StateFlow<CommuteUiState> = combine(
        repository.configFlow,
        _isPolling
    ) { config, polling ->
        CommuteUiState(config = config, isPollingTraffic = polling)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CommuteUiState()
    )

    init {
        viewModelScope.launch {
            val initial = repository.getSnapshot()
            if (initial.isEnabled) {
                alarmScheduler.scheduleAlarmAndProgressivePolling(initial)
                if (System.currentTimeMillis() - initial.lastPollEpochMillis > 15 * 60 * 1000L) {
                    refreshTrafficNow()
                }
            }
        }
    }

    fun toggleAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmEnabled(enabled)
            val updated = repository.getSnapshot()
            alarmScheduler.scheduleAlarmAndProgressivePolling(updated)
            if (enabled && System.currentTimeMillis() - updated.lastPollEpochMillis > 15 * 60 * 1000L) {
                refreshTrafficNow()
            }
        }
    }

    fun updateTargetArrival(hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateTargetArrival(hour, minute)
            val updated = repository.getSnapshot()
            alarmScheduler.scheduleAlarmAndProgressivePolling(updated)
        }
    }

    fun updatePrepAndBaseline(prepMinutes: Int, baselineMinutes: Int) {
        viewModelScope.launch {
            repository.updatePrepAndBaseline(prepMinutes, baselineMinutes)
            val updated = repository.getSnapshot()
            alarmScheduler.scheduleAlarmAndProgressivePolling(updated)
        }
    }

    fun enableOpenSourceRollbackAndRetry() {
        viewModelScope.launch {
            repository.setAllowOpenSourceFallback(true)
            refreshTrafficNow()
        }
    }

    fun updateLocationsAndApiKey(
        originAddress: String,
        useGps: Boolean,
        destinationAddress: String,
        destinationLabel: String,
        userTomTomKey: String,
        googleApiKey: String,
        allowOpenSourceFallback: Boolean,
        letOsrmControlAlarm: Boolean
    ) {
        viewModelScope.launch {
            repository.updateLocationsAndApi(
                originAddress = originAddress,
                useGps = useGps,
                destinationAddress = destinationAddress,
                destinationLabel = destinationLabel,
                userTomTomKey = userTomTomKey,
                googleApiKey = googleApiKey,
                allowOpenSourceFallback = allowOpenSourceFallback,
                letOsrmControlAlarm = letOsrmControlAlarm
            )
            refreshTrafficNow()
        }
    }

    fun refreshTrafficNow() {
        viewModelScope.launch {
            _isPolling.value = true
            val current = repository.getSnapshot()

            when (val result = trafficService.fetchLiveCommuteDuration(current, repository)) {
                is TrafficPollResult.Success -> {
                    repository.recordLiveTrafficUpdate(
                        commuteMinutes = result.durationMinutes,
                        isFallback = false,
                        providerName = result.provider,
                        promptOpenSource = false,
                        customReason = result.noteOverride
                    )
                }
                is TrafficPollResult.Fallback -> {
                    repository.recordLiveTrafficUpdate(
                        commuteMinutes = result.baselineMinutes,
                        isFallback = true,
                        providerName = "Baseline Failsafe",
                        promptOpenSource = result.canPromptOpenSource,
                        customReason = result.reason
                    )
                }
            }

            val updated = repository.getSnapshot()
            alarmScheduler.scheduleAlarmAndProgressivePolling(updated)
            _isPolling.value = false
        }
    }

    fun triggerRingPreview() {
        val context = getApplication<Application>()
        val ringIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_RING_ALARM
        }
        context.sendBroadcast(ringIntent)
    }
}