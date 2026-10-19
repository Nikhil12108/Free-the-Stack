package com.ironlog.app.ui.workout

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.domain.Units
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.theme.ClockStyle
import com.ironlog.app.theme.accent
import com.ironlog.app.theme.onAccent
import com.ironlog.app.ui.ExerciseDetail
import com.ironlog.app.ui.FinishWorkout
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import com.ironlog.app.ui.exercises.ExercisePickerDialog
import com.ironlog.app.ui.rememberNow
import com.ironlog.app.workout.ActiveWorkoutManager
import com.ironlog.app.workout.CompleteResult
import com.ironlog.app.workout.RestTimer
import com.ironlog.app.workout.SessionExercise
import kotlinx.coroutines.launch

@Composable
fun LiveWorkoutScreen(nav: Navigator) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val workout by c.activeWorkout.state.collectAsStateWithLifecycle()
  val contexts by c.activeWorkout.contexts.collectAsStateWithLifecycle()
  val timer by c.restTimer.state.collectAsStateWithLifecycle()
  val w = workout
  if (w == null) {
    LaunchedEffect(Unit) { nav.back() }
    return
  }
  KeepScreenOn(settings.keepScreenOn)
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val haptics = LocalHapticFeedback.current
  var adding by remember { mutableStateOf(false) }
  var discarding by remember { mutableStateOf(false) }
  val now = rememberNow()
  var sorenessMuscles by remember { mutableStateOf<List<TrainingMuscle>>(emptyList()) }
  val sessionId = w.plan?.sessionId
  LaunchedEffect(sessionId) { sorenessMuscles = if (sessionId != null) c.plans.sorenessCandidates(sessionId) else emptyList() }

  AppScaffold(
    title = Units.clock((now - w.startTime) / 1000),
    titleStyle = MaterialTheme.typography.titleLarge.merge(ClockStyle),
    onBack = { nav.back() },
    actions = { Button(onClick = { nav.go(FinishWorkout) }, modifier = Modifier.padding(end = 8.dp)) { Text("Finish") } },
    bottomBar = { timer?.let { RestTimerBar(it, now, onAdjust = { d -> c.restTimer.adjust(d) }, onSkip = { c.restTimer.skip() }) } },
    snackbarHostState = snackbar,
  ) { padding ->
    LazyColumn(Modifier.imePadding(), contentPadding = pagePadding(padding, horizontal = 0.dp)) {
      item { WorkoutHeader(w.name, onNameChange = { n -> c.activeWorkout.update { it.copy(name = n) } }, durationSec = (now - w.startTime) / 1000, w = w, includeWarmups = settings.includeWarmupsInStats) }
      item { AlertBanners(c.restTimer) }
      w.plan?.let { plan ->
        item { PlanBanner(plan) }
        if (plan.feedbackMode == FeedbackMode.FULL && sorenessMuscles.isNotEmpty()) {
          item {
            RecoveryCheckCard(sorenessMuscles, w.feedback, onSoreness = { m, v -> c.activeWorkout.update { it.copy(feedback = it.feedback.with(m) { a -> a.copy(soreness = v) }) } })
          }
        }
      }
      if (w.exercises.isEmpty()) item { EmptyState("Let's go", "Add an exercise to start logging sets. Tap ✓ when a set is done — the rest timer starts on its own.") }
      items(w.exercises, key = { it.key }) { ex ->
        val ctx = contexts[ex.exerciseId]
        val prs = remember(ex, ctx) { c.activeWorkout.prs(ex) }
        Column {
          EditableExerciseCard(
            exercise = ex,
            all = w.exercises,
            mode = SetTableMode.LIVE,
            settings = settings,
            previous = ctx?.previous.orEmpty(),
            prs = prs,
            edit = { f -> c.activeWorkout.updateExercises(f) },
            onToggleComplete = { setKey ->
              when (c.activeWorkout.toggleComplete(ex.key, setKey)) {
                CompleteResult.OK -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                CompleteResult.MISSING_VALUES -> scope.launch { snackbar.showSnackbar("Enter the set's numbers first") }
              }
            },
            onOpenExercise = { id -> nav.go(ExerciseDetail(id)) },
            modifier = Modifier.reveal(),
            bodyweightKg = c.activeWorkout.bodyweightKg,
          )
          val plan = w.plan
          if (plan != null && plan.feedbackMode != FeedbackMode.OFF) {
            val working = ex.sets.filter { it.type.isWorking }
            if (working.isNotEmpty() && working.all { it.completed }) {
              JointCheckRow(ex.jointPain, onPick = { v -> c.activeWorkout.updateExercises { list -> list.map { e -> if (e.key == ex.key) e.copy(jointPain = v) else e } } })
            }
            val muscle = w.isLastFor(ex)
            if (muscle != null && w.muscleDone(muscle)) {
              MuscleCheckCard(
                muscle,
                w.feedback.answer(muscle),
                plan.feedbackMode,
                onWorkload = { v -> c.activeWorkout.update { it.copy(feedback = it.feedback.with(muscle) { a -> a.copy(workload = v) }) } },
                onPump = { v -> c.activeWorkout.update { it.copy(feedback = it.feedback.with(muscle) { a -> a.copy(pump = v) }) } },
              )
            }
          }
        }
      }
      item {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" Add exercises")
          }
          TextButton(onClick = { discarding = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
            Text("Discard workout")
          }
        }
      }
    }
  }

  if (adding) {
    ExercisePickerDialog(
      title = "Add exercises",
      multiSelect = true,
      onDismiss = { adding = false },
      onPicked = { picked ->
        adding = false
        c.activeWorkout.updateExercises { list -> list + picked.map { SessionExercise.from(it, com.ironlog.app.workout.TargetPlanner.restFor(it, settings)) } }
      },
    )
  }
  if (discarding) {
    ConfirmDialog("Discard workout?", "Everything logged in this workout will be lost.", "Discard", onConfirm = { c.activeWorkout.discard() }, onDismiss = { discarding = false }, destructive = true)
  }
}

/** Where this session sits in the plan and how hard sets should be this week. */
@Composable
private fun PlanBanner(plan: com.ironlog.app.workout.PlanLink) {
  SurfaceCard(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Eyebrow(if (plan.deload) "Deload week" else "Week ${plan.week + 1} of ${plan.weeks - 1}")
      Text(
        if (plan.deload) "Keep every set easy — light weights, about half the reps. You should finish fresh."
        else if (plan.rir == 0) "Last hard week: take sets to failure (one rep short on barbell squats and presses)."
        else "Stop each set with about ${plan.rir} good rep${if (plan.rir == 1) "" else "s"} left in the tank.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun WorkoutHeader(name: String, onNameChange: (String) -> Unit, durationSec: Long, w: com.ironlog.app.workout.ActiveWorkout, includeWarmups: Boolean) {
  val settings = LocalSettings.current
  val colors = MaterialTheme.colorScheme
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Eyebrow("Workout name")
    BasicTextField(
      value = name,
      onValueChange = onNameChange,
      singleLine = true,
      textStyle = MaterialTheme.typography.headlineMedium.copy(color = colors.onSurface),
      cursorBrush = SolidColor(colors.primary),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      StatTile("Duration", Units.durationWords(durationSec))
      StatTile("Volume", Units.volume(ActiveWorkoutManager.volumeKg(w, includeWarmups), settings.weightUnit))
      StatTile("Sets", ActiveWorkoutManager.completedSets(w, includeWarmups).toString())
    }
  }
}

/** The rest countdown, floating above the screen bottom as an ember card. */
@Composable
private fun RestTimerBar(state: RestTimer.State, now: Long, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
  val remaining = state.remainingMillis(now)
  val colors = MaterialTheme.colorScheme
  Surface(color = colors.accent, contentColor = colors.onAccent, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
    Column {
      LinearProgressIndicator(
        progress = { (remaining / (state.totalSeconds * 1000f)).coerceIn(0f, 1f) },
        color = colors.onAccent.copy(alpha = 0.75f),
        trackColor = colors.onAccent.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
      )
      Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Eyebrow("Rest", color = colors.onAccent.copy(alpha = 0.7f))
          Text(Units.clock((remaining + 999) / 1000), style = MaterialTheme.typography.headlineMedium.merge(ClockStyle))
        }
        TextButton(onClick = { onAdjust(-15) }, colors = ButtonDefaults.textButtonColors(contentColor = colors.onAccent)) { Text("−15") }
        TextButton(onClick = { onAdjust(15) }, colors = ButtonDefaults.textButtonColors(contentColor = colors.onAccent)) { Text("+15") }
        Button(onClick = onSkip, colors = ButtonDefaults.buttonColors(containerColor = colors.onAccent, contentColor = Color.White)) { Text("Skip") }
      }
    }
  }
}

/** Nudges for the two permissions rest alerts depend on; re-checked whenever the screen resumes. */
@Composable
private fun AlertBanners(timer: RestTimer) {
  val context = LocalContext.current
  val requestNotifications = rememberNotificationPermissionRequest()
  var resumes by remember { mutableIntStateOf(0) }
  LifecycleResumeEffect(Unit) {
    resumes++
    onPauseOrDispose {}
  }
  val notificationsOff = remember(resumes) { !NotificationManagerCompat.from(context).areNotificationsEnabled() }
  val exactOff = remember(resumes) { !timer.canScheduleExact() }
  Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (notificationsOff) {
      Banner("Turn on notifications to get an alert when your rest is over.", "Allow") {
        if (Build.VERSION.SDK_INT >= 33) requestNotifications()
        else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
      }
    }
    if (exactOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Banner("Allow \"Alarms & reminders\" so rest alerts arrive on time even with the screen off.", "Open settings") {
        context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
      }
    }
  }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
  SurfaceCard {
    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
      TextButton(onClick = onClick) { Text(action) }
    }
  }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
  val view = LocalView.current
  DisposableEffect(enabled) {
    view.keepScreenOn = enabled
    onDispose { view.keepScreenOn = false }
  }
}
