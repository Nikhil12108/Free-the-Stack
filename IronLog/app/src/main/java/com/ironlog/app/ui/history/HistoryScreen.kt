package com.ironlog.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.Settings
import com.ironlog.app.data.db.WorkoutWithExercises
import com.ironlog.app.domain.Lifting
import com.ironlog.app.domain.Units
import com.ironlog.app.model.SetType
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.WorkoutDetail
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.Rule
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import com.ironlog.app.ui.components.stagger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
val dayTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")

fun WorkoutWithExercises.durationSec() = ((workout.endTime - workout.startTime) / 1000).coerceAtLeast(0)

fun WorkoutWithExercises.volumeKg(includeWarmups: Boolean) =
  exercises.sumOf { e -> e.sets.filter { includeWarmups || it.type != SetType.WARMUP }.sumOf { s -> Lifting.setVolume(e.exercise.type, com.ironlog.app.domain.SetValues(s.type, s.weightKg, s.reps)) } }

fun WorkoutWithExercises.setCount(includeWarmups: Boolean) = exercises.sumOf { e -> e.sets.count { includeWarmups || it.type != SetType.WARMUP } }

@Composable
fun HistoryScreen(nav: Navigator) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val workouts by c.workouts.workouts.collectAsStateWithLifecycle(null)
  val zone = remember { ZoneId.systemDefault() }

  AppScaffold(title = "History", large = true) { padding ->
    val list = workouts ?: return@AppScaffold
    if (list.isEmpty()) {
      EmptyState("No workouts yet", "Finished workouts show up here with their sets, volume and records.", Modifier.padding(padding))
      return@AppScaffold
    }
    val byMonth = remember(list) { list.groupBy { Instant.ofEpochMilli(it.workout.startTime).atZone(zone).format(monthFmt) } }
    LazyColumn(contentPadding = pagePadding(padding)) {
      byMonth.forEach { (month, ws) ->
        item(key = "m-$month") { SectionHeader(month) { Eyebrow("${ws.size} workout${if (ws.size == 1) "" else "s"}") } }
        itemsIndexed(ws, key = { _, w -> w.workout.id }) { i, w ->
          WorkoutCard(w, settings, zone, onClick = { nav.go(WorkoutDetail(w.workout.id)) }, modifier = Modifier.padding(vertical = 6.dp).reveal(stagger(i)))
        }
      }
    }
  }
}

@Composable
private fun WorkoutCard(w: WorkoutWithExercises, settings: Settings, zone: ZoneId, onClick: () -> Unit, modifier: Modifier) {
  SurfaceCard(modifier, onClick = onClick) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Eyebrow(Instant.ofEpochMilli(w.workout.startTime).atZone(zone).format(dayTimeFmt))
      Text(w.workout.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatTile("Duration", Units.durationWords(w.durationSec()))
        StatTile("Volume", Units.volume(w.volumeKg(settings.includeWarmupsInStats), settings.weightUnit))
        StatTile("Sets", w.setCount(settings.includeWarmupsInStats).toString())
      }
      Rule(Modifier.padding(vertical = 6.dp))
      w.orderedExercises.take(5).forEach { e ->
        Row {
          Text("${e.sets.size} ×", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
          Text(e.exercise.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      if (w.exercises.size > 5) Text("+${w.exercises.size - 5} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
