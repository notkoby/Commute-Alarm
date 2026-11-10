package com.commutealarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_POLL_TRAFFIC = "com.commutealarm.app.ACTION_POLL_TRAFFIC"
        const val ACTION_RING_ALARM = "com.commutealarm.app.ACTION_RING_ALARM"
        const val ACTION_DISMISS_ALARM = "com.commutealarm.app.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.commutealarm.app.ACTION_SNOOZE_ALARM"

        private const val REQ_CODE_RING = 1001
        private const val REQ_CODE_POLL = 1002

        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val TWENTY_MIN_MS = 20 * 60 * 1000L
    }

    fun scheduleAlarmAndProgressivePolling(config: AlarmConfig) {
        cancelAll()
        if (!config.isEnabled) return

        val nowMillis = System.currentTimeMillis()
        val nowLocal = LocalDateTime.now()

        val dynamicWakeMillis = config.nextWakeUpEpochMillis(nowLocal)
        scheduleRingAlarm(dynamicWakeMillis)

        // Schedule automatic API polling every 1 hour AND at the 20-minute mark before the alarm goes off
        val twentyMinBeforeWake = dynamicWakeMillis - TWENTY_MIN_MS
        val oneHourFromNow = nowMillis + ONE_HOUR_MS

        val nextPollMillis: Long? = when {
            nowMillis < twentyMinBeforeWake - 60_000L -> minOf(oneHourFromNow, twentyMinBeforeWake)
            nowMillis < dynamicWakeMillis - (3 * 60_000L) -> nowMillis + (5 * 60_000L)
            else -> null
        }

        if (nextPollMillis != null && nextPollMillis < dynamicWakeMillis - 60_000L) {
            scheduleTrafficPoll(nextPollMillis)
        }
    }

    fun scheduleSnooze(minutes: Int = 9) {
        cancelAll()
        val triggerAt = System.currentTimeMillis() + (minutes * 60_000L)
        scheduleRingAlarm(triggerAt)
    }

    private fun scheduleRingAlarm(triggerAtMillis: Long) {
        val ringIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RING_ALARM
        }
        val ringPendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_CODE_RING,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
        alarmManager.setAlarmClock(clockInfo, ringPendingIntent)
    }

    private fun scheduleTrafficPoll(triggerAtMillis: Long) {
        val pollIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_POLL_TRAFFIC
        }
        val pollPendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_CODE_POLL,
            pollIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pollPendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pollPendingIntent
            )
        }
    }

    fun cancelAll() {
        val ringIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_RING_ALARM }
        val pollIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_POLL_TRAFFIC }

        val ringPi = PendingIntent.getBroadcast(
            context, REQ_CODE_RING, ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pollPi = PendingIntent.getBroadcast(
            context, REQ_CODE_POLL, pollIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(ringPi)
        alarmManager.cancel(pollPi)
    }
}

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CommuteAlarm::ReceiverWakeLock"
            ).apply { acquire(30_000L) }

            try {
                val repo = CommuteRepository(context)
                val scheduler = AlarmScheduler(context)
                val trafficService = TrafficService(context)
                val config = repo.getSnapshot()

                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        if (config.isEnabled) {
                            scheduler.scheduleAlarmAndProgressivePolling(config)
                        }
                    }

                    AlarmScheduler.ACTION_POLL_TRAFFIC -> {
                        if (!config.isEnabled) return@launch
                        val nowMillis = System.currentTimeMillis()
                        val currentWakeMillis = config.nextWakeUpEpochMillis()
                        val minutesUntilWake = (currentWakeMillis - nowMillis) / 60_000L
                        val isTwentyMinPreAlarmCheck = minutesUntilWake <= 25L

                        when (val pollResult = trafficService.fetchLiveCommuteDuration(
                            config = config,
                            repository = repo,
                            isTwentyMinPreAlarmCheck = isTwentyMinPreAlarmCheck
                        )) {
                            is TrafficPollResult.Success -> {
                                repo.recordLiveTrafficUpdate(
                                    commuteMinutes = pollResult.durationMinutes,
                                    isFallback = false,
                                    providerName = pollResult.provider,
                                    promptOpenSource = false,
                                    customReason = pollResult.noteOverride
                                )
                            }
                            is TrafficPollResult.Fallback -> {
                                repo.recordLiveTrafficUpdate(
                                    commuteMinutes = pollResult.baselineMinutes,
                                    isFallback = true,
                                    providerName = "Baseline Failsafe",
                                    promptOpenSource = pollResult.canPromptOpenSource,
                                    customReason = pollResult.reason
                                )
                            }
                        }
                        val updatedConfig = repo.getSnapshot()
                        val updatedWakeMillis = updatedConfig.nextWakeUpEpochMillis()
                        if (updatedWakeMillis <= System.currentTimeMillis() + 60_000L) {
                            startRingingService(context)
                        } else {
                            scheduler.scheduleAlarmAndProgressivePolling(updatedConfig)
                        }
                    }

                    AlarmScheduler.ACTION_RING_ALARM -> {
                        startRingingService(context)
                    }

                    AlarmScheduler.ACTION_DISMISS_ALARM -> {
                        context.stopService(Intent(context, AlarmRingingService::class.java))
                        val current = repo.getSnapshot()
                        if (current.isEnabled) {
                            scheduler.scheduleAlarmAndProgressivePolling(current)
                        }
                    }

                    AlarmScheduler.ACTION_SNOOZE_ALARM -> {
                        context.stopService(Intent(context, AlarmRingingService::class.java))
                        scheduler.scheduleSnooze(9)
                    }
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private fun startRingingService(context: Context) {
        val serviceIntent = Intent(context, AlarmRingingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

object MapsLauncher {
    fun launchTurnByTurnNavigation(context: Context, destination: String) {
        val encodedDest = Uri.encode(destination)
        val navUri = Uri.parse("google.navigation:q=$encodedDest&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDest&travelmode=driving")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}