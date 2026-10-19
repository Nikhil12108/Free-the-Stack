package com.ironlog.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.AppContainer
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.data.db.RoutineWithExercises
import com.ironlog.app.progression.Periodization
import com.ironlog.app.progression.Priority
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.PlanDetail
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.TagPill
import com.ironlog.app.ui.components.pagePadding
import kotlinx.coroutines.launch

enum class PlanSource(val label: String) {
  ROUTINES("From my routines"),
  BUILDER("Build it for me"),
}

class PlanCreateViewModel(private val container: AppContainer) : ViewModel() {
  var source by mutableStateOf(PlanSource.ROUTINES)
  var name by mutableStateOf("")
  var weeks by mutableIntStateOf(Periodization.defaultWeeks(container.settings.current.experience))
  var mode by mutableStateOf(container.settings.current.feedbackMode)
  val selected = mutableStateListOf<Long>()
  var priorities by mutableStateOf<Map<TrainingMuscle, Priority>>(emptyMap())
  var creating by mutableStateOf(false)
    private set

  fun toggle(routineId: Long) {
    if (routineId in selected) selected.remove(routineId) else selected.add(routineId)
  }

  fun setPriority(m: TrainingMuscle, p: Priority) {
    priorities = priorities + (m to p)
  }

  val builder = BuilderState(container)

  fun createDraft(draft: com.ironlog.app.data.PlanDraft, onCreated: (Long) -> Unit) {
    if (creating) return
    creating = true
    viewModelScope.launch {
      val id = container.plans.create(draft)
      creating = false
      onCreated(id)
    }
  }

  fun createFromRoutines(routines: List<RoutineWithExercises>, onCreated: (Long) -> Unit) {
    if (creating || selected.isEmpty()) return
    creating = true
    viewModelScope.launch {
      val fallback = routines.firstOrNull { it.routine.id == selected.first() }?.routine?.name?.let { "Block · $it" } ?: "Training block"
      val draft = container.plans.draftFromRoutines(name.trim().ifBlank { fallback }, selected.toList(), weeks, mode).copy(priorities = priorities)
      val id = container.plans.create(draft)
      creating = false
      onCreated(id)
    }
  }
}

/** Muscles trained by the chosen routines, in display order. */
fun musclesOf(routines: List<RoutineWithExercises>, ids: List<Long>): List<TrainingMuscle> =
  routines
    .filter { it.routine.id in ids }
    .flatMap { r -> r.exercises.mapNotNull { TrainingMuscle.of(it.exercise.primaryMuscle) } }
    .distinct()
    .sortedBy { it.ordinal }

@Composable
fun PlanCreateScreen(nav: Navigator) {
  val c = LocalContainer.current
  val vm = viewModel { PlanCreateViewModel(c) }
  val routines by c.routines.routines.collectAsStateWithLifecycle(emptyList())
  val active by c.plans.active.collectAsStateWithLifecycle(null)
  val onCreated: (Long) -> Unit = { id ->
    nav.back()
    nav.go(PlanDetail(id))
  }

  AppScaffold(
    title = "New plan",
    onBack = { nav.back() },
    actions = {
      if (vm.source == PlanSource.ROUTINES) {
        TextButton(enabled = vm.selected.isNotEmpty() && !vm.creating, onClick = { vm.createFromRoutines(routines, onCreated) }) { Text("Create") }
      }
    },
  ) { padding ->
    LazyColumn(Modifier.imePadding(), contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        Text(
          "A plan repeats your training days for a few weeks. Each week the weights, reps and sets move a little, sets end closer to failure, and the last week is an easy deload so you start the next block fresh.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
          PlanSource.entries.forEach { s -> SelectChip(selected = vm.source == s, onClick = { vm.source = s }, label = s.label) }
        }
      }
      if (active != null) {
        item { Text("Creating a plan ends your current one (\"${active!!.name}\"); its history stays.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
      item {
        OutlinedTextField(value = vm.name, onValueChange = { vm.name = it }, label = { Text("Plan name") }, placeholder = { Text("e.g. Autumn hypertrophy") }, singleLine = true, modifier = Modifier.fillMaxWidth())
      }
      if (vm.source == PlanSource.BUILDER) {
        item { PlanBuilderSection(vm, onCreated) }
        return@LazyColumn
      }
      item {
        SectionHeader("Training days")
        Text("Pick your routines in the order you train them. Each one becomes a day of the week; its sets are week 1.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (routines.isEmpty()) {
        item { EmptyState("No routines yet", "Create a routine for each training day first, or let IronLog build the plan for you.") }
      }
      items(routines, key = { it.routine.id }) { r ->
        val order = vm.selected.indexOf(r.routine.id)
        RoutinePickRow(r, order, onClick = { vm.toggle(r.routine.id) })
      }
      item { LengthAndQuestions(vm.weeks, { vm.weeks = it }, vm.mode, { vm.mode = it }) }
      val muscles = musclesOf(routines, vm.selected)
      if (muscles.isNotEmpty()) {
        item {
          SectionHeader("Muscle priorities")
          Text("Emphasize lets a muscle climb toward the most volume you can recover from; grow keeps it near the minimum that works; maintain holds it steady. Emphasize one or two at a time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(muscles, key = { "m-${it.name}" }) { m ->
          Column(Modifier.padding(vertical = 4.dp)) {
            Text(m.label, style = MaterialTheme.typography.bodyLarge)
            PriorityChips(vm.priorities[m] ?: Priority.GROW, onSelect = { vm.setPriority(m, it) }, modifier = Modifier.padding(top = 2.dp))
          }
        }
      }
      item {
        Button(onClick = { vm.createFromRoutines(routines, onCreated) }, enabled = vm.selected.isNotEmpty() && !vm.creating, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
          Text(if (vm.selected.isEmpty()) "Pick at least one routine" else "Create ${vm.weeks}-week plan")
        }
      }
    }
  }
}

@Composable
fun LengthAndQuestions(weeks: Int, onWeeks: (Int) -> Unit, mode: FeedbackMode, onMode: (FeedbackMode) -> Unit) {
  Column {
    SectionHeader("Length")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      (Periodization.MIN_WEEKS..Periodization.MAX_WEEKS).forEach { w -> SelectChip(selected = weeks == w, onClick = { onWeeks(w) }, label = "$w weeks") }
    }
    Text(
      "Reps in reserve by week: " + Periodization.rirSchedule(weeks).joinToString(" · ") + ", then a deload week.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 4.dp),
    )
    SectionHeader("Questions after sets")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FeedbackMode.entries.forEach { m -> SelectChip(selected = mode == m, onClick = { onMode(m) }, label = m.label) }
    }
    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
  }
}

@Composable
private fun RoutinePickRow(r: RoutineWithExercises, order: Int, onClick: () -> Unit) {
  val names = r.orderedExercises.map { it.exercise.name }
  val sets = r.exercises.sumOf { e -> e.sets.count { it.type.isWorking } }
  SurfaceCard(onClick = onClick) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TagPill(if (order >= 0) "${order + 1}" else "–", color = if (order >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
      Column(Modifier.weight(1f)) {
        Text(r.routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Eyebrow("${names.size} exercises · $sets sets")
      }
    }
  }
}
