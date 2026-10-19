package com.ironlog.app.workout

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironlog.app.MainActivity
import com.ironlog.app.R

object Notifications {
  private const val CHANNEL_RUNNING = "rest_running"
  private const val CHANNEL_DONE = "rest_done"
  private const val ID_RUNNING = 1
  private const val ID_DONE = 2
  const val EXTRA_OPEN_WORKOUT = "open_workout"

  fun createChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL_RUNNING, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Count-down while you rest between sets"
        setShowBadge(false)
      }
    )
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Alert when it's time for the next set"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 300, 150, 300)
      }
    )
  }

  private fun openWorkout(context: Context): PendingIntent =
    PendingIntent.getActivity(
      context,
      10,
      Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_WORKOUT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun timerAction(context: Context, action: String, requestCode: Int): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      requestCode,
      Intent(context, RestTimerReceiver::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
  fun showRestRunning(context: Context, state: RestTimer.State) {
    val nm = NotificationManagerCompat.from(context)
    if (!nm.areNotificationsEnabled()) return
    val n =
      NotificationCompat.Builder(context, CHANNEL_RUNNING)
        .setSmallIcon(R.drawable.ic_stat_timer)
        .setContentTitle("Resting")
        .setContentText(state.label)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(state.endAt)
        .setShowWhen(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setContentIntent(openWorkout(context))
        .addAction(0, "−15s", timerAction(context, RestTimerReceiver.ACTION_SUBTRACT, 11))
        .addAction(0, "+15s", timerAction(context, RestTimerReceiver.ACTION_ADD, 12))
        .addAction(0, "Skip", timerAction(context, RestTimerReceiver.ACTION_SKIP, 13))
        .build()
    nm.notify(ID_RUNNING, n)
  }

  fun cancelRestRunning(context: Context) = NotificationManagerCompat.from(context).cancel(ID_RUNNING)

  @SuppressLint("MissingPermission")
  fun showRestDone(context: Context, label: String) {
    val nm = NotificationManagerCompat.from(context)
    if (!nm.areNotificationsEnabled()) return
    val n =
      NotificationCompat.Builder(context, CHANNEL_DONE)
        .setSmallIcon(R.drawable.ic_stat_timer)
        .setContentTitle("Rest over")
        .setContentText(if (label.isBlank()) "Time for your next set" else "Next set: $label")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setTimeoutAfter(60_000)
        .setContentIntent(openWorkout(context))
        .build()
    nm.notify(ID_DONE, n)
  }
}
