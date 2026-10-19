package com.ironlog.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.AppContainer
import com.ironlog.app.data.RoutineRepository
import com.ironlog.app.ui.ExerciseDetail
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.exercises.ExercisePickerDialog
import com.ironlog.app.workout.SessionExercise
import kotlinx.coroutines.launch

class RoutineEditorViewModel(private val container: AppContainer, private val routineId: Long?, folderId: Long?) : ViewModel() {
  var name by mutableStateOf("")
  var notes by mutableStateOf("")
  var folderId by mutableStateOf(folderId)
  var exercises by mutableStateOf<List<SessionExercise>>(emptyList())
    private set

  var loading by mutableStateOf(routineId != null)
    private set

  var dirty by mutableStateOf(false)
    private set

  val isNew = routineId == null

  init {
    if (routineId != null) {
      viewModelScope.launch {
        container.routines.get(routineId)?.let { r ->
          name = r.routine.name
          notes = r.routine.notes
          this@RoutineEditorViewModel.folderId = r.routine.folderId
          exercises = RoutineRepository.toSession(r, container.settings.current.defaultRestSeconds)
        }
        loading = false
      }
    }
  }

  fun edit(f: (List<SessionExercise>) -> List<SessionExercise>) {
    exercises = f(exercises)
    dirty = true
  }

  fun touch() {
    dirty = true
  }

  fun save(onSaved: () -> Unit) {
    viewModelScope.launch {
      container.routines.save(routineId, name.trim().ifBlank { "My routine" }, folderId, notes.trim(), exercises)
      dirty = false
      onSaved()
    }
  }
}

@Composable
fun RoutineEditorScreen(nav: Navigator, routineId: Long?, folderId: Long?) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val vm = viewModel { RoutineEditorViewModel(c, routineId, folderId) }
  var adding by remember { mutableStateOf(false) }
  var confirmLeave by remember { mutableStateOf(false) }

  BackHandler(enabled = vm.dirty) { confirmLeave = true }

  AppScaffold(
    title = if (vm.isNew) "New routine" else "Edit routine",
    onBack = { if (vm.dirty) confirmLeave = true else nav.back() },
    actions = { TextButton(enabled = vm.exercises.isNotEmpty() && !vm.loading, onClick = { vm.save { nav.back() } }) { Text("Save") } },
  ) { padding ->
    if (vm.loading) return@AppScaffold
    LazyColumn(Modifier.imePadding(), contentPadding = pagePadding(padding, horizontal = 0.dp)) {
      item {
        Column(Modifier.padding(horizontal = 16.dp)) {
          OutlinedTextField(
            value = vm.name,
            onValueChange = {
              vm.name = it
              vm.touch()
            },
            label = { Text("Routine name") },
            placeholder = { Text("e.g. Push Day") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = vm.notes,
            onValueChange = {
              vm.notes = it
              vm.touch()
            },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
          Text(
            "Weights and reps here are targets. Type a range like 8-12 for reps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
          )
        }
      }
      items(vm.exercises, key = { it.key }) { ex ->
        EditableExerciseCard(
          exercise = ex,
          all = vm.exercises,
          mode = SetTableMode.ROUTINE,
          settings = settings,
          previous = emptyList(),
          prs = emptyMap(),
          edit = vm::edit,
          onToggleComplete = {},
          onOpenExercise = { id -> nav.go(ExerciseDetail(id)) },
        )
      }
      item {
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text(" Add exercises")
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
        vm.edit { list -> list + picked.map { SessionExercise.from(it, com.ironlog.app.workout.TargetPlanner.restFor(it, settings)) } }
      },
    )
  }
  if (confirmLeave) {
    ConfirmDialog("Discard changes?", "Your edits to this routine haven't been saved.", "Discard", onConfirm = { nav.back() }, onDismiss = { confirmLeave = false }, destructive = true)
  }
}
