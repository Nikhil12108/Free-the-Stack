package com.ironlog.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.WorkoutRepository
import com.ironlog.app.domain.Lifting
import com.ironlog.app.domain.PrKind
import com.ironlog.app.domain.SetValues
import com.ironlog.app.domain.Units
import com.ironlog.app.model.SetType
import com.ironlog.app.theme.accentText
import com.ironlog.app.ui.ExerciseDetail
import com.ironlog.app.ui.LiveWorkout
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.OpenArrow
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import com.ironlog.app.ui.components.stagger
import com.ironlog.app.ui.workout.describeSet
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun WorkoutDetailScreen(nav: Navigator, workoutId: Long) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val workout by remember(workoutId) { c.workouts.observe(workoutId) }.collectAsStateWithLifecycle(null)
  var deleting by remember { mutableStateOf(false) }
  var deleted by remember { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  val w = workout

  // PR stars: compare each exercise's sets against everything logged *before* this workout.
  val prs by
    produceState(emptyMap<Long, Set<PrKind>>(), w) {
      val wk = w ?: return@produceState
      val result = mutableMapOf<Long, Set<PrKind>>()
      for (e in wk.exercises) {
        val earlier = c.workouts.exerciseHistory(e.exercise.id).filter { it.startTime < wk.workout.startTime }
        val sessions = earlier.groupBy { it.set.workoutId }.values.map { rows -> rows.map { SetValues(it.set.type, it.set.weightKg, it.set.reps, it.set.durationSec, it.set.distanceM) } }
        val bests = Lifting.personalBests(e.exercise.type, sessions)
        val sets = e.orderedSets
        sets.zip(Lifting.prsInSession(bests, e.exercise.type, sets.map { SetValues(it.type, it.weightKg, it.reps, it.durationSec, it.distanceM) })).forEach { (s, k) -> result[s.id] = k }
      }
      value = result
    }

  if (w == null) {
    if (deleted) LaunchedEffect(Unit) { nav.back() }
    return
  }
  val zone = remember { ZoneId.systemDefault() }

  AppScaffold(
    title = w.workout.name,
    onBack = { nav.back() },
    snackbarHostState = snackbar,
    actions = {
      Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Workout options") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(
            text = { Text("Repeat workout") },
            onClick = {
              menu = false
              if (c.activeWorkout.isActive) {
                scope.launch { snackbar.showSnackbar("Finish or discard the workout in progress first") }
              } else {
                c.activeWorkout.startFrom(w.workout.name, WorkoutRepository.toSession(w, settings.defaultRestSeconds))
                nav.go(LiveWorkout)
              }
            },
          )
          DropdownMenuItem(
            text = { Text("Save as routine") },
            onClick = {
              menu = false
              scope.launch {
                c.routines.save(null, w.workout.name, null, "", WorkoutRepository.toSession(w, settings.defaultRestSeconds))
                snackbar.showSnackbar("Saved to your routines")
              }
            },
          )
          DropdownMenuItem(
            text = { Text("Delete workout", color = MaterialTheme.colorScheme.error) },
            onClick = {
              menu = false
              deleting = true
            },
          )
        }
      }
    },
  ) { padding ->
    LazyColumn(contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Eyebrow(Instant.ofEpochMilli(w.workout.startTime).atZone(zone).format(dayTimeFmt))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("Duration", Units.durationWords(w.durationSec()))
            StatTile("Volume", Units.volume(w.volumeKg(settings.includeWarmupsInStats), settings.weightUnit))
            StatTile("Sets", w.setCount(settings.includeWarmupsInStats).toString())
            StatTile("Records", prs.values.sumOf { it.size }.toString())
          }
          if (w.workout.notes.isNotBlank()) Text(w.workout.notes, style = MaterialTheme.typography.bodyMedium)
        }
      }
      itemsIndexed(w.orderedExercises, key = { _, e -> e.entry.id }) { i, e ->
        SurfaceCard(Modifier.reveal(stagger(i))) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              Modifier.clip(MaterialTheme.shapes.extraSmall).clickable { nav.go(ExerciseDetail(e.exercise.id)) }.padding(bottom = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(e.exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, fontSize = 20.sp, modifier = Modifier.weight(1f, fill = false))
              OpenArrow()
            }
            if (e.entry.notes.isNotBlank()) Text(e.entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var n = 0
            e.orderedSets.forEach { s ->
              if (s.type != SetType.WARMUP) n++
              val label = if (s.type == SetType.NORMAL) n.toString() else s.type.short
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                Text(describeSet(e.exercise.type, SetValues(s.type, s.weightKg, s.reps, s.durationSec, s.distanceM), settings), modifier = Modifier.weight(1f))
                when {
                  s.rir != null -> Eyebrow(if (s.rir >= 5) "RIR 5+" else "RIR ${s.rir}")
                  s.rpe != null -> Eyebrow("RPE ${Units.number(s.rpe)}")
                }
                if (prs[s.id].orEmpty().isNotEmpty()) Icon(Icons.Filled.Star, contentDescription = "Personal record", tint = MaterialTheme.colorScheme.accentText, modifier = Modifier.size(18.dp).padding(start = 4.dp))
              }
            }
          }
        }
      }
    }
  }

  if (deleting) {
    ConfirmDialog(
      "Delete workout?",
      "This removes it from your history and stats.",
      "Delete",
      onConfirm = {
        deleted = true
        scope.launch { c.workouts.delete(workoutId) }
      },
      onDismiss = { deleting = false },
      destructive = true,
    )
  }
}
