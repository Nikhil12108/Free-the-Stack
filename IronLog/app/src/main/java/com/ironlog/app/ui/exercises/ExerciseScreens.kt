package com.ironlog.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.Settings
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.data.db.SetHistoryRow
import com.ironlog.app.domain.Lifting
import com.ironlog.app.domain.PersonalBests
import com.ironlog.app.domain.PrKind
import com.ironlog.app.domain.SetValues
import com.ironlog.app.domain.Units
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.SetType
import com.ironlog.app.ui.ExerciseDetail
import com.ironlog.app.ui.ExerciseEditor
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.history.dayTimeFmt
import com.ironlog.app.ui.workout.describeSet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ExercisesTabScreen(nav: Navigator) {
  val c = LocalContainer.current
  val all by c.exercises.exercises.collectAsStateWithLifecycle(emptyList())
  val usage by c.exercises.usage.collectAsStateWithLifecycle(emptyMap())
  var query by remember { mutableStateOf("") }
  var muscle by remember { mutableStateOf<MuscleGroup?>(null) }
  var equipment by remember { mutableStateOf<Equipment?>(null) }
  val list = remember(all, query, muscle, equipment) { all.filter { (muscle == null || it.primaryMuscle == muscle) && (equipment == null || it.equipment == equipment) && it.matches(query) } }

  AppScaffold(
    title = "Exercises",
    large = true,
    floatingActionButton = { FloatingActionButton(onClick = { nav.go(ExerciseEditor()) }) { Icon(Icons.Filled.Add, contentDescription = "New exercise") } },
  ) { padding ->
    Column(Modifier.padding(padding)) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search ${all.size} exercises") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
      Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnumFilterChip("Muscle", muscle, MuscleGroup.entries, { it.label }) { muscle = it }
        EnumFilterChip("Equipment", equipment, Equipment.entries, { it.label }) { equipment = it }
      }
      LazyColumn(Modifier.weight(1f).imePadding()) {
        items(list, key = { it.id }) { ex ->
          Row(Modifier.fillMaxWidth().clickable { nav.go(ExerciseDetail(ex.id)) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(ex.name, style = MaterialTheme.typography.bodyLarge)
              Text(
                listOfNotNull(ex.primaryMuscle.label, ex.equipment.label.takeIf { ex.equipment != Equipment.NONE }, "custom".takeIf { ex.isCustom }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            usage[ex.id]?.let { Text("${it}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }
  }
}

@Composable
fun ExerciseEditorScreen(nav: Navigator, exerciseId: Long?) {
  val c = LocalContainer.current
  val scope = rememberCoroutineScope()
  val existing by remember(exerciseId) { if (exerciseId == null) kotlinx.coroutines.flow.flowOf(null) else c.exercises.observe(exerciseId) }.collectAsStateWithLifecycle(null)
  AppScaffold(title = if (exerciseId == null) "New exercise" else "Edit exercise", onBack = { nav.back() }) { padding ->
    if (exerciseId != null && existing == null) return@AppScaffold
    ExerciseForm(
      initial = existing,
      onSave = { ex ->
        scope.launch {
          if (exerciseId == null) c.exercises.create(ex) else c.exercises.update(ex)
          nav.back()
        }
      },
      modifier = Modifier.padding(padding).imePadding(),
    )
  }
}

/** The chartable series for an exercise type, newest session last. */
private enum class ExerciseMetric(val label: String) {
  ONE_RM("Best est. 1RM"),
  WEIGHT("Heaviest weight"),
  VOLUME("Session volume"),
  REPS("Most reps"),
  DURATION("Longest time"),
  DISTANCE("Distance"),
}

private fun metricsFor(type: ExerciseType): List<ExerciseMetric> =
  when (type) {
    ExerciseType.WEIGHT_REPS,
    ExerciseType.WEIGHTED_BODYWEIGHT -> listOf(ExerciseMetric.ONE_RM, ExerciseMetric.WEIGHT, ExerciseMetric.VOLUME)
    ExerciseType.REPS_ONLY,
    ExerciseType.ASSISTED_BODYWEIGHT -> listOf(ExerciseMetric.REPS)
    ExerciseType.DURATION -> listOf(ExerciseMetric.DURATION)
    ExerciseType.WEIGHT_DURATION -> listOf(ExerciseMetric.WEIGHT, ExerciseMetric.DURATION)
    ExerciseType.DISTANCE_DURATION -> listOf(ExerciseMetric.DISTANCE, ExerciseMetric.DURATION)
    ExerciseType.WEIGHT_DISTANCE -> listOf(ExerciseMetric.WEIGHT, ExerciseMetric.DISTANCE)
  }

private fun sessionValue(metric: ExerciseMetric, type: ExerciseType, sets: List<SetValues>): Double? {
  val work = sets.filter { it.type != SetType.WARMUP }
  return when (metric) {
    ExerciseMetric.ONE_RM -> work.mapNotNull { Lifting.estimatedOneRepMax(it.weightKg ?: 0.0, it.reps ?: 0) }.maxOrNull()
    ExerciseMetric.WEIGHT -> work.mapNotNull { it.weightKg }.maxOrNull()
    ExerciseMetric.VOLUME -> work.sumOf { Lifting.setVolume(type, it) }.takeIf { it > 0 }
    ExerciseMetric.REPS -> work.mapNotNull { it.reps }.maxOrNull()?.toDouble()
    ExerciseMetric.DURATION -> work.mapNotNull { it.durationSec }.maxOrNull()?.toDouble()
    ExerciseMetric.DISTANCE -> work.mapNotNull { it.distanceM }.maxOrNull()
  }
}

private fun formatMetric(metric: ExerciseMetric, value: Double, s: Settings): String =
  when (metric) {
    ExerciseMetric.ONE_RM,
    ExerciseMetric.WEIGHT -> Units.weight(value, s.weightUnit)
    ExerciseMetric.VOLUME -> Units.volume(value, s.weightUnit)
    ExerciseMetric.REPS -> "${value.toInt()} reps"
    ExerciseMetric.DURATION -> Units.clock(value.toLong())
    ExerciseMetric.DISTANCE -> Units.distance(value, s.distanceUnit)
  }

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun ExerciseDetailScreen(nav: Navigator, exerciseId: Long) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val scope = rememberCoroutineScope()
  val exercise by remember(exerciseId) { c.exercises.observe(exerciseId) }.collectAsStateWithLifecycle(null)
  val history by remember(exerciseId) { c.workouts.observeExerciseHistory(exerciseId) }.collectAsStateWithLifecycle(emptyList())
  var menu by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf(false) }
  var deleted by remember { mutableStateOf(false) }
  val active by c.activeWorkout.state.collectAsStateWithLifecycle()
  // Deleting an exercise the running workout still holds would leave that workout unsaveable.
  val inWorkout = active?.exercises?.any { it.exerciseId == exerciseId } == true
  val ex = exercise
  if (ex == null) {
    if (deleted) LaunchedEffect(Unit) { nav.back() }
    return
  }
  val zone = remember { ZoneId.systemDefault() }
  val sessions: List<List<SetHistoryRow>> = remember(history) { history.groupBy { it.set.workoutId }.values.toList() } // newest first
  val values = remember(sessions) { sessions.map { rows -> rows.sortedBy { it.set.position }.map { SetValues(it.set.type, it.set.weightKg, it.set.reps, it.set.durationSec, it.set.distanceM) } } }
  val bests = remember(values, ex.type) { Lifting.personalBests(ex.type, values) }
  val metrics = metricsFor(ex.type)
  var metric by remember(ex.type) { mutableStateOf(metrics.first()) }

  AppScaffold(
    title = ex.name,
    onBack = { nav.back() },
    actions = {
      Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(
            text = { Text("Edit") },
            onClick = {
              menu = false
              nav.go(ExerciseEditor(exerciseId))
            },
          )
          if (ex.isCustom) {
            DropdownMenuItem(
              enabled = !inWorkout,
              text = {
                Column {
                  Text("Delete", color = if (inWorkout) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                  if (inWorkout) Text("Remove it from your workout first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              },
              onClick = {
                menu = false
                deleting = true
              },
            )
          }
        }
      }
    },
  ) { padding ->
    LazyColumn(contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Text(
          listOf(ex.primaryMuscle.label + " (primary)", *ex.secondaryMuscles.map { it.label }.toTypedArray()).joinToString(" · ") + "\n${ex.equipment.label} · ${ex.type.label}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (sessions.isEmpty()) {
        item { EmptyState("No history yet", "Log this exercise in a workout to see records and progress here.") }
        return@LazyColumn
      }
      item { RecordsCard(ex.type, bests, sessions.size, settings) }
      item {
        SurfaceCard {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (metrics.size > 1) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { m -> SelectChip(selected = m == metric, onClick = { metric = m }, label = m.label) }
              }
            } else {
              Eyebrow(metric.label)
            }
            val points = sessions.zip(values).reversed().takeLast(20).mapNotNull { (rows, v) -> sessionValue(metric, ex.type, v)?.let { rows.first().startTime to it } }
            if (points.size >= 2) {
              val labels = listOf(points.first(), points[points.size / 2], points.last()).map { Instant.ofEpochMilli(it.first).atZone(zone).format(shortDate) }
              LineChart(points.map { it.second }, labels, formatValue = { formatMetric(metric, it, settings) })
            } else {
              Text("Log this exercise in one more workout to see a trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
      item { SectionHeader("History") }
      items(sessions, key = { it.first().set.workoutId }) { rows ->
        SurfaceCard {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(Instant.ofEpochMilli(rows.first().startTime).atZone(zone).format(dayTimeFmt))
            Text(rows.first().workoutName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            rows.sortedBy { it.set.position }.forEach { r ->
              val v = SetValues(r.set.type, r.set.weightKg, r.set.reps, r.set.durationSec, r.set.distanceM)
              Text((if (r.set.type == SetType.NORMAL) "" else "${r.set.type.short}  ") + describeSet(ex.type, v, settings), style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }
  }

  if (deleting) {
    ConfirmDialog(
      "Delete \"${ex.name}\"?",
      "Its logged sets are removed from your history too. This can't be undone.",
      "Delete",
      onConfirm = {
        deleted = true
        scope.launch { c.exercises.delete(ex) }
      },
      onDismiss = { deleting = false },
      destructive = true,
    )
  }
}

@Composable
private fun RecordsCard(type: ExerciseType, b: PersonalBests, sessions: Int, s: Settings) {
  val kinds = Lifting.prKindsFor(type)
  val rows = buildList {
    add("Sessions" to sessions.toString())
    if (PrKind.WEIGHT in kinds) b.heaviestWeightKg?.let { add(PrKind.WEIGHT.label to Units.weight(it, s.weightUnit)) }
    if (PrKind.ONE_RM in kinds) b.bestOneRepMaxKg?.let { add(PrKind.ONE_RM.label to Units.weight(it, s.weightUnit)) }
    if (PrKind.SET_VOLUME in kinds) b.bestSetVolumeKg?.let { add(PrKind.SET_VOLUME.label to Units.volume(it, s.weightUnit)) }
    if (type.countsVolume) b.bestSessionVolumeKg?.let { add("Best session volume" to Units.volume(it, s.weightUnit)) }
    if (PrKind.REPS in kinds) b.mostReps?.let { add(PrKind.REPS.label to "$it") }
    if (PrKind.DURATION in kinds) b.longestDurationSec?.let { add(PrKind.DURATION.label to Units.clock(it.toLong())) }
    if (PrKind.DISTANCE in kinds) b.longestDistanceM?.let { add(PrKind.DISTANCE.label to if (type == ExerciseType.WEIGHT_DISTANCE) Units.shortDistance(it) else Units.distance(it, s.distanceUnit)) }
  }
  SurfaceCard {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text("Records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
      rows.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth()) { pair.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) } }
      }
    }
  }
}
