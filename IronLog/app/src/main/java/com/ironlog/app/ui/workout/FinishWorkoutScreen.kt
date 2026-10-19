package com.ironlog.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.domain.Units
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.workout.TargetPlanner
import com.ironlog.app.theme.accentText
import com.ironlog.app.ui.HistoryTab
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.WorkoutDetail
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.workout.ActiveWorkoutManager
import kotlinx.coroutines.launch

@Composable
fun FinishWorkoutScreen(nav: Navigator) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val scope = rememberCoroutineScope()
  val state by c.activeWorkout.state.collectAsStateWithLifecycle()
  val contexts by c.activeWorkout.contexts.collectAsStateWithLifecycle()
  var saving by remember { mutableStateOf(false) }
  val w = state
  if (w == null) {
    if (!saving) LaunchedEffect(Unit) { nav.back() }
    return
  }
  var name by remember { mutableStateOf(w.name) }
  var notes by remember { mutableStateOf(w.notes) }
  var updateRoutine by remember { mutableStateOf(false) }
  val routineName by produceState<String?>(null, w.routineId) { value = w.routineId?.let { c.routines.get(it)?.routine?.name } }
  val endTime = remember { System.currentTimeMillis() }

  val sets = ActiveWorkoutManager.completedSets(w, includeWarmups = true)
  val prs = remember(w, contexts) { w.exercises.flatMap { ex -> c.activeWorkout.prs(ex).values.flatten().toSet().map { ex.name to it.label } } }
  val unfinished = w.exercises.sumOf { ex -> ex.sets.count { !it.completed } }
  // Muscles trained but not yet rated, fixed when the screen opens so a card stays while it is answered.
  val quickCheck = remember {
    if (w.plan == null || w.plan.feedbackMode == FeedbackMode.OFF) emptyList()
    else w.exercises.filter { ex -> ex.sets.any { it.completed && it.type.isWorking } }.mapNotNull { TargetPlanner.traits(it).muscle }.distinct().filter { w.feedback.answer(it).workload == null }
  }

  AppScaffold(title = "Save workout", onBack = { nav.back() }) { padding ->
    Column(
      Modifier.padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Workout name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
      OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("How did it go?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
      SurfaceCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          StatTile("Duration", Units.durationWords((endTime - w.startTime) / 1000))
          StatTile("Volume", Units.volume(ActiveWorkoutManager.volumeKg(w, settings.includeWarmupsInStats), settings.weightUnit))
          StatTile("Sets", ActiveWorkoutManager.completedSets(w, settings.includeWarmupsInStats).toString())
        }
      }
      if (prs.isNotEmpty()) {
        SectionHeader("Personal records (${prs.size})")
        prs.forEach { (exercise, kind) ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.accentText, modifier = Modifier.size(18.dp))
            Text("  $exercise — $kind", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      w.plan?.let { plan ->
        SurfaceCard {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(if (plan.deload) "Plan · deload week" else "Plan · week ${plan.week + 1} of ${plan.weeks - 1}")
            Text(plan.dayName, style = MaterialTheme.typography.titleMedium)
            Text("Saving completes this session and works out next week's sets for this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (quickCheck.isNotEmpty()) {
          SectionHeader("Quick check")
          quickCheck.forEach { m ->
            MuscleCheckCard(
              m,
              w.feedback.answer(m),
              plan.feedbackMode,
              onWorkload = { v -> c.activeWorkout.update { it.copy(feedback = it.feedback.with(m) { a -> a.copy(workload = v) }) } },
              onPump = { v -> c.activeWorkout.update { it.copy(feedback = it.feedback.with(m) { a -> a.copy(pump = v) }) } },
            )
          }
        }
      }
      if (unfinished > 0) {
        Text("$unfinished unfinished set${if (unfinished == 1) "" else "s"} won't be saved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      routineName?.let { rn ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = updateRoutine, onCheckedChange = { updateRoutine = it })
          Text("Update \"$rn\" with today's sets and weights", style = MaterialTheme.typography.bodyMedium)
        }
      }
      Button(
        enabled = sets > 0 && !saving,
        onClick = {
          saving = true
          scope.launch {
            val id = c.activeWorkout.finish(name, notes, updateRoutine)
            nav.reset(HistoryTab, WorkoutDetail(id))
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Save workout")
      }
      if (sets == 0) Text("Complete at least one set to save this workout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
  }
}
