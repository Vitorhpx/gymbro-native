package com.gymbro.local.workout

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gymbro.local.MainActivity
import com.gymbro.local.R

data class WorkoutNotificationSpec(
    val title: String,
    val text: String,
    val whenMillis: Long,
    val usesChronometer: Boolean,
    val ongoing: Boolean,
)

fun workoutNotificationSpec(startedAt: Long) = WorkoutNotificationSpec(
    title = "Workout in progress",
    text = "Tap to return to GymBro",
    whenMillis = startedAt,
    usesChronometer = true,
    ongoing = true,
)

class WorkoutNotificationManager(private val context: Context) {
    fun show(startedAt: Long) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active workout", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows elapsed time while a workout is active"
                    setShowBadge(false)
                },
            )
        }

        val spec = workoutNotificationSpec(startedAt)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setContentIntent(pendingIntent)
            .setWhen(spec.whenMillis)
            .setUsesChronometer(spec.usesChronometer)
            .setShowWhen(true)
            .setOngoing(spec.ongoing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "active-workout"
        const val NOTIFICATION_ID = 2_001
    }
}
