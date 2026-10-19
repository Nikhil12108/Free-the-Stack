package com.ironlog.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.Settings
import com.ironlog.app.domain.PrKind
import com.ironlog.app.domain.SetValues
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.ProgressionRule
import com.ironlog.app.theme.SupersetColors
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.exercises.ExercisePickerDialog
import com.ironlog.app.workout.TargetPlanner
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionOps.addSet
import com.ironlog.app.workout.SessionOps.leaveSuperset
import com.ironlog.app.workout.SessionOps.move
import com.ironlog.app.workout.SessionOps.removeExercise
import com.ironlog.app.workout.SessionOps.removeSet
import com.ironlog.app.workout.SessionOps.replace
import com.ironlog.app.workout.SessionOps.setWarmups
import com.ironlog.app.workout.SessionOps.supersetWithNext
import com.ironlog.app.workout.SessionOps.updateExercise
import com.ironlog.app.workout.SessionOps.updateSet

typealias ExercisesEdit = ((List<SessionExercise>) -> List<SessionExercise>) -> Unit

/** An exercise card plus its menu, dialogs and list edits — identical in workouts and routines. */
@Composable
fun EditableExerciseCard(
  exercise: SessionExercise,
  all: List<SessionExercise>,
  mode: SetTableMode,
  settings: Settings,
  previous: List<SetValues>,
  prs: Map<Long, Set<PrKind>>,
  edit: ExercisesEdit,
  onToggleComplete: (Long) -> Unit,
  onOpenExercise: (Long) -> Unit,
  modifier: Modifier = Modifier,
  bodyweightKg: Double? = null,
) {
  val index = all.indexOfFirst { it.key == exercise.key }
  var progression by remember { mutableStateOf(false) }
  var replacing by remember { mutableStateOf(false) }
  var warmups by remember { mutableStateOf(false) }
  var plates by remember { mutableStateOf(false) }
  var rest by remember { mutableStateOf(false) }
  val key = exercise.key

  val actions = buildList {
    if (index > 0) add(CardAction("Move up") { edit { it.move(key, -1) } })
    if (index < all.lastIndex) add(CardAction("Move down") { edit { it.move(key, 1) } })
    add(CardAction("Replace exercise") { replacing = true })
    if (TargetPlanner.supports(exercise.type)) add(CardAction("Progression…") { progression = true })
    if (exercise.supersetGroup != null) add(CardAction("Remove from superset") { edit { it.leaveSuperset(key) } })
    if (index < all.lastIndex) add(CardAction("Superset with next") { edit { it.supersetWithNext(key) } })
    if (exercise.type.usesWeight) add(CardAction("Warm-up sets") { warmups = true })
    if (exercise.equipment == Equipment.BARBELL) add(CardAction("Plate calculator") { plates = true })
    add(CardAction("Remove exercise", destructive = true) { edit { it.removeExercise(key) } })
  }

  ExerciseCard(
    exercise = exercise,
    mode = mode,
    settings = settings,
    previous = previous,
    prs = prs,
    supersetColor = exercise.supersetGroup?.let { SupersetColors[(it - 1).mod(SupersetColors.size)] },
    actions = actions,
    onTitleClick = { onOpenExercise(exercise.exerciseId) },
    onNotesChange = { n -> edit { it.updateExercise(key) { e -> e.copy(notes = n) } } },
    onRestClick = { rest = true },
    onUpdateSet = { setKey, f -> edit { it.updateSet(key, setKey, f) } },
    onToggleComplete = onToggleComplete,
    onAddSet = { edit { it.addSet(key) } },
    onRemoveSet = { setKey -> edit { it.removeSet(key, setKey) } },
    modifier = modifier,
    bodyweightKg = bodyweightKg,
  )

  if (progression) {
    ProgressionDialog(
      exercise = exercise,
      settings = settings,
      onApply = { rule, min, max, rir ->
        edit { list ->
          list.updateExercise(key) { e ->
            e.copy(
              rule = rule,
              targetRir = rir,
              sets = e.sets.map { s -> if (s.type.isWorking) s.copy(repMin = min, repMax = max) else s },
              targetsReady = false,
            )
          }
        }
      },
      onDismiss = { progression = false },
    )
  }

  if (replacing) {
    ExercisePickerDialog(
      title = "Replace exercise",
      multiSelect = false,
      onDismiss = { replacing = false },
      onPicked = { picked ->
        replacing = false
        picked.firstOrNull()?.let { ex -> edit { it.replace(key, ex) } }
      },
    )
  }
  if (rest) RestPickerDialog(exercise.restSeconds, onPick = { s -> edit { it.updateExercise(key) { e -> e.copy(restSeconds = s) } } }, onDismiss = { rest = false })
  if (warmups) {
    val working = exercise.sets.firstOrNull { it.type != SetType.WARMUP && it.weightKg != null }?.weightKg ?: previous.firstOrNull { it.type != SetType.WARMUP }?.weightKg
    WarmupDialog(
      workingKg = working,
      usesBar = exercise.equipment == Equipment.BARBELL,
      settings = settings,
      onApply = { sets -> edit { it.setWarmups(key, sets) } },
      onDismiss = { warmups = false },
    )
  }
  if (plates) {
    val w = exercise.sets.lastOrNull { it.weightKg != null }?.weightKg
    PlateCalculatorDialog(initialKg = w, settings = settings, onDismiss = { plates = false })
  }
}

/** How this exercise progresses: the rule, its rep range and how close to failure sets should end. */
@Composable
fun ProgressionDialog(exercise: SessionExercise, settings: Settings, onApply: (ProgressionRule?, Int, Int, Int?) -> Unit, onDismiss: () -> Unit) {
  val first = exercise.sets.firstOrNull { it.type.isWorking }
  val range = TargetPlanner.repRange(exercise)
  var rule by remember { mutableStateOf(exercise.rule ?: ProgressionRule.SMART) }
  var min by remember { mutableStateOf((first?.repMin ?: range.first).toString()) }
  var max by remember { mutableStateOf((first?.repMax ?: range.last).toString()) }
  var rir by remember { mutableStateOf(exercise.targetRir) }
  val lo = min.toIntOrNull()
  val hi = max.toIntOrNull()
  val valid = lo != null && hi != null && lo in 1..100 && hi in lo..100
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Progression") },
    text = {
      // Scrolls so the effort chips stay reachable on short screens and in landscape.
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ProgressionRule.entries.forEach { r -> SelectChip(selected = rule == r, onClick = { rule = r }, label = r.label) }
        }
        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Eyebrow("Rep range")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(value = min, onValueChange = { min = it.filter(Char::isDigit).take(3) }, label = { Text("From") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
          OutlinedTextField(value = max, onValueChange = { max = it.filter(Char::isDigit).take(3) }, label = { Text("To") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Eyebrow("Reps in reserve")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SelectChip(selected = rir == null, onClick = { rir = null }, label = "Default (${settings.defaultRir})")
          (0..4).forEach { v -> SelectChip(selected = rir == v, onClick = { rir = v }, label = v.toString()) }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = {
          onApply(rule.takeIf { it != ProgressionRule.SMART }, lo!!, hi!!, rir)
          onDismiss()
        },
      ) {
        Text("Apply")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
