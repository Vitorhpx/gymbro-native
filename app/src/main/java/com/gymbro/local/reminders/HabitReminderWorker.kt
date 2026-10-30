package com.gymbro.local.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymbro.local.MainActivity
import com.gymbro.local.data.GymBroDatabase
import com.gymbro.local.data.SettingsEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderSchedule {
    val defaultTime: LocalTime = LocalTime.of(19, 30)

    fun nextRun(now: ZonedDateTime, reminderTime: LocalTime = defaultTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(reminderTime).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(reminderTime).atZone(now.zone)
    }

    fun isDeliveryWindow(now: ZonedDateTime, reminderTime: LocalTime = defaultTime): Boolean {
        val time = now.toLocalTime()
        return !time.isBefore(reminderTime) && time.isBefore(reminderTime.plusHours(4))
    }
}

object ReminderScheduler {
    const val PREFERENCES = "private_settings"
    const val ENABLED_KEY = "habit_reminders_enabled"
    private const val UNIQUE_WORK = "gymbro-habit-reminder"

    fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

    fun replaceScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

    fun scheduleAfterWorker(context: Context) = enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val appContext = context.applicationContext
        val enabled = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, false)
        if (!enabled) {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, ReminderSchedule.nextRun(now)).toMillis().coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }
}

class HabitReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(ReminderScheduler.PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(ReminderScheduler.ENABLED_KEY, false)) return Result.success()
        if (!ReminderSchedule.isDeliveryWindow(ZonedDateTime.now())) {
            ReminderScheduler.scheduleAfterWorker(applicationContext)
            return Result.success()
        }
        return try {
            val dao = GymBroDatabase.get(applicationContext).dao()
            dao.ensureSettings(SettingsEntity())
            val settings = dao.allSettingsOnce().singleOrNull() ?: SettingsEntity()
            val tasks = ReminderEvaluator.evaluate(
                asOf = LocalDate.now(),
                zoneId = ZoneId.systemDefault(),
                foodEntries = dao.allFoodEntriesOnce(),
                workoutSessions = dao.allSessionsOnce(),
                weightEntries = dao.allWeightEntriesOnce(),
                preparedMeals = dao.allPreparedMealsOnce(),
                settings = settings,
            )
            if (tasks.isNotEmpty()) showNotification(tasks)
            ReminderScheduler.scheduleAfterWorker(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(tasks: List<ReminderTask>) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders for unfinished GymBro habits"
                },
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle()
        tasks.forEach { style.addLine(it.message) }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.gymbro.local.R.mipmap.ic_launcher)
            .setContentTitle("GymBro check-in")
            .setContentText(tasks.first().message)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "habit-reminders"
        private const val NOTIFICATION_ID = 1001
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_TIME_CHANGED || intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
            ReminderScheduler.replaceScheduled(context)
        }
    }
}
