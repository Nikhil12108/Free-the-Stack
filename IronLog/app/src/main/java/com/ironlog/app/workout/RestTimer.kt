package com.ironlog.app.workout

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.ironlog.app.IronLogApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rest timer defined only by its end time, so nothing has to tick in the background:
 * - an ongoing notification shows a count-down chronometer with -15s / +15s / Skip actions;
 * - an alarm (exact when the user allows it) posts the "rest over" alert, even if the process died;
 * - while the process is alive a coroutine does the same, which covers devices without exact alarms.
 */
class RestTimer(private val context: Context, private val scope: CoroutineScope) {
  data class State(val endAt: Long, val totalSeconds: Int, val label: String) {
    fun remainingMillis(now: Long = System.currentTimeMillis()) = (endAt - now).coerceAtLeast(0)
  }

  private val prefs = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow<State?>(null)
  val state: StateFlow<State?> = _state.asStateFlow()
  private var job: Job? = null

  init {
    val saved = load()
    if (saved != null) {
      val late = System.currentTimeMillis() - saved.endAt
      when {
        late > 60_000 -> persist(null) // expired long ago: drop silently
        late >= 0 -> { // just expired, most likely the alarm restarted our process: alert now
          persist(null)
          Notifications.cancelRestRunning(context)
          Notifications.showRestDone(context, saved.label)
        }
        else -> set(saved) // still running: resume it
      }
    }
  }

  fun start(seconds: Int, label: String) {
    if (seconds <= 0) return
    set(State(System.currentTimeMillis() + seconds * 1000L, seconds, label))
  }

  fun adjust(deltaSeconds: Int) {
    val s = _state.value ?: return
    val end = s.endAt + deltaSeconds * 1000L
    if (end <= System.currentTimeMillis()) skip() else set(s.copy(endAt = end, totalSeconds = (s.totalSeconds + deltaSeconds).coerceAtLeast(1)))
  }

  @Synchronized
  fun skip() {
    job?.cancel()
    cancelAlarm()
    persist(null)
    _state.value = null
    Notifications.cancelRestRunning(context)
  }

  /** Called by the alarm receiver or the in-process coroutine; whichever comes first wins. */
  @Synchronized
  fun onTimeUp() {
    val s = _state.value ?: return
    if (s.endAt - System.currentTimeMillis() > 1_000) return // stale wake-up after an adjustment
    skip()
    Notifications.showRestDone(context, s.label)
  }

  @Synchronized
  private fun set(s: State) {
    persist(s)
    _state.value = s
    scheduleAlarm(s.endAt)
    Notifications.showRestRunning(context, s)
    job?.cancel()
    job =
      scope.launch {
        delay(s.remainingMillis())
        onTimeUp()
      }
  }

  fun canScheduleExact(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

  private fun alarmIntent(): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_TIME_UP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun scheduleAlarm(at: Long) {
    val am = context.getSystemService(AlarmManager::class.java)
    val pi = alarmIntent()
    try {
      if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
      else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    } catch (_: SecurityException) {
      am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
  }

  private fun cancelAlarm() = context.getSystemService(AlarmManager::class.java).cancel(alarmIntent())

  private fun persist(s: State?) {
    prefs.edit { if (s == null) clear() else putLong("endAt", s.endAt).putInt("total", s.totalSeconds).putString("label", s.label) }
  }

  private fun load(): State? {
    val end = prefs.getLong("endAt", 0)
    if (end == 0L) return null
    return State(end, prefs.getInt("total", 0), prefs.getString("label", "") ?: "")
  }
}

class RestTimerReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val timer = (context.applicationContext as IronLogApp).container.restTimer
    when (intent.action) {
      ACTION_TIME_UP -> timer.onTimeUp()
      ACTION_ADD -> timer.adjust(15)
      ACTION_SUBTRACT -> timer.adjust(-15)
      ACTION_SKIP -> timer.skip()
    }
  }

  companion object {
    const val ACTION_TIME_UP = "com.ironlog.app.REST_TIME_UP"
    const val ACTION_ADD = "com.ironlog.app.REST_ADD"
    const val ACTION_SUBTRACT = "com.ironlog.app.REST_SUBTRACT"
    const val ACTION_SKIP = "com.ironlog.app.REST_SKIP"
  }
}
