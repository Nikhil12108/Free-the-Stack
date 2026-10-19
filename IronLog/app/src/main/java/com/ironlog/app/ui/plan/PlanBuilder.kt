package com.ironlog.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironlog.app.AppContainer
import com.ironlog.app.data.DayDraft
import com.ironlog.app.data.PlanDraft
import com.ironlog.app.data.SlotDraft
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment
import com.ironlog.app.progression.Builder
import com.ironlog.app.progression.ExerciseHistoryFacts
import com.ironlog.app.progression.ExerciseTraits
import com.ironlog.app.progression.Pattern
import com.ironlog.app.progression.Priority
import com.ironlog.app.progression.Suggest
import com.ironlog.app.progression.Targets
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.exercises.ExercisePickerDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Equipment groups the builder can choose from. */
enum class GymKit(val label: String, val equipment: Set<Equipment>) {
  BARBELL("Barbells", setOf(Equipment.BARBELL, Equipment.PLATE)),
  DUMBBELL("Dumbbells", setOf(Equipment.DUMBBELL)),
  MACHINE("Machines", setOf(Equipment.MACHINE)),
  CABLE("Cables", setOf(Equipment.CABLE)),
  BODYWEIGHT("Bodyweight", setOf(Equipment.NONE, Equipment.SUSPENSION, Equipment.RESISTANCE_BAND, Equipment.OTHER)),
  KETTLEBELL("Kettlebells", setOf(Equipment.KETTLEBELL)),
}

data class BuiltSlot(val muscle: TrainingMuscle, val sets: Int, val options: List<ExerciseEntity>, val chosen: ExerciseEntity?)

data class BuiltDay(val weekday: Int, val name: String, val slots: List<BuiltSlot>)

/** State for "Build it for me", kept in the create screen's view model. */
class BuilderState(private val container: AppContainer) {
  var days by mutableIntStateOf(4)
  var priorities by
    mutableStateOf<Map<TrainingMuscle, Priority?>>(
      TrainingMuscle.major.associateWith { if (it == TrainingMuscle.TRAPS || it == TrainingMuscle.FOREARMS) null else Priority.GROW }
    )
  var kit by mutableStateOf(GymKit.entries.toSet())
  var week by mutableStateOf<List<BuiltDay>?>(null)
    private set

  private var library: List<ExerciseEntity> = emptyList()
  private var facts: Map<Long, ExerciseHistoryFacts> = emptyMap()

  suspend fun generate() {
    if (library.isEmpty()) library = container.exercises.exercises.first()
    facts = container.exercises.facts(container.workouts)
    val chosen = priorities.filterValues { it != null }.mapValues { it.value!! }
    val plan = Builder.generate(days, chosen, TrainingMuscle.major, container.settings.current.experience)
    val equipment = kit.flatMap { it.equipment }.toSet()
    val usedThisWeek = mutableSetOf<Long>()
    val patterns = mutableMapOf<TrainingMuscle, MutableSet<Pattern>>()
    week =
      plan.mapIndexed { di, d ->
        val usedToday = mutableSetOf<Long>()
        var heavy = false
        BuiltDay(
          d.weekday,
          "${weekdayNames[d.weekday]} · ${d.name}",
          d.slots.mapIndexed { si, s ->
            val ranked =
              Suggest.rank(
                Suggest.Slot(s.muscle, di, plan.size, si, d.slots.size, usedToday.toSet(), usedThisWeek.toSet(), patterns[s.muscle].orEmpty(), heavy),
                library,
                facts,
                equipment,
              )
            val pick = ranked.firstOrNull()
            if (pick != null) {
              val t = ExerciseTraits.of(pick)
              usedToday += pick.id
              usedThisWeek += pick.id
              patterns.getOrPut(s.muscle) { mutableSetOf() } += t.pattern
              if (t.systemicCost >= 3) heavy = true
            }
            BuiltSlot(s.muscle, s.sets, ranked.take(12), pick)
          },
        )
      }
  }

  /** The next ranked exercise for a slot. */
  fun swap(day: Int, slot: Int) {
    val w = week ?: return
    week =
      w.mapIndexed { di, d ->
        if (di != day) d
        else
          d.copy(
            slots =
              d.slots.mapIndexed { si, s ->
                if (si != slot || s.options.isEmpty()) s
                else {
                  val i = s.options.indexOfFirst { it.id == s.chosen?.id }
                  s.copy(chosen = s.options[(i + 1).mod(s.options.size)])
                }
              }
          )
      }
  }

  fun clear() {
    week = null
  }

  fun pick(day: Int, slot: Int, exercise: ExerciseEntity) {
    week = week?.mapIndexed { di, d -> if (di != day) d else d.copy(slots = d.slots.mapIndexed { si, s -> if (si == slot) s.copy(chosen = exercise) else s }) }
  }

  fun toDraft(name: String, weeks: Int, mode: com.ironlog.app.data.db.FeedbackMode): PlanDraft? {
    val w = week ?: return null
    val days =
      w.map { d ->
        DayDraft(
          d.name,
          d.weekday,
          d.slots.mapNotNull { s ->
            val e = s.chosen ?: return@mapNotNull null
            val range = Targets.defaultRange(ExerciseTraits.of(e))
            SlotDraft(e.id, s.muscle, range.first, range.last, s.sets)
          },
        )
      }
    if (days.all { it.slots.isEmpty() }) return null
    return PlanDraft(name, weeks, mode, days, priorities.filterValues { it != null }.mapValues { it.value!! })
  }
}

@Composable
fun PlanBuilderSection(vm: PlanCreateViewModel, onCreated: (Long) -> Unit) {
  val scope = rememberCoroutineScope()
  val b = vm.builder
  var picking by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var working by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      "Choose how often you can train and what matters most. IronLog lays out the week, spaces each muscle for recovery, and picks exercises you can swap.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionHeader("Days per week")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (2..6).forEach { d -> SelectChip(selected = b.days == d, onClick = { b.days = d }, label = "$d days") } }
    SectionHeader("What to train")
    Text("Emphasize one or two muscles; grow the rest you care about; maintain or skip the others.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TrainingMuscle.major.forEach { m ->
      Column(Modifier.padding(vertical = 4.dp)) {
        Text(m.label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Priority.entries.forEach { p -> SelectChip(selected = b.priorities[m] == p, onClick = { b.priorities = b.priorities + (m to p) }, label = p.label) }
          SelectChip(selected = b.priorities[m] == null, onClick = { b.priorities = b.priorities + (m to null) }, label = "Skip")
        }
      }
    }
    SectionHeader("Equipment you have")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      GymKit.entries.forEach { k ->
        val on = k in b.kit
        SelectChip(selected = on, onClick = { b.kit = if (on && b.kit.size > 1) b.kit - k else b.kit + k }, label = k.label)
      }
    }
    LengthAndQuestions(vm.weeks, { vm.weeks = it }, vm.mode, { vm.mode = it })
    Button(
      enabled = !working && b.priorities.values.any { it != null },
      onClick = {
        working = true
        scope.launch {
          b.generate()
          working = false
        }
      },
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
      Text(if (b.week == null) "Lay out my week" else "Lay it out again")
    }
    b.week?.let { week ->
      SectionHeader("Your week")
      week.forEachIndexed { di, d ->
        SurfaceCard(Modifier.padding(vertical = 4.dp)) {
          Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleMedium)
            if (d.slots.size > Builder.MAX_SLOTS_PER_DAY) Text("A long day — consider training fewer muscles or adding a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            d.slots.forEachIndexed { si, s ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Eyebrow("${s.muscle.label} · ${s.sets} sets")
                  Text(s.chosen?.name ?: "No exercise for your equipment", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.options.size > 1) TextButton(onClick = { b.swap(di, si) }) { Text("Swap") }
                TextButton(onClick = { picking = di to si }) { Text("Pick") }
              }
            }
          }
        }
      }
      Button(
        enabled = !vm.creating,
        onClick = {
          val draft = b.toDraft(vm.name.trim().ifBlank { "Built block" }, vm.weeks, vm.mode) ?: return@Button
          vm.createDraft(draft, onCreated)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      ) {
        Text("Create ${vm.weeks}-week plan")
      }
      OutlinedButton(onClick = { b.clear() }, modifier = Modifier.fillMaxWidth()) { Text("Start over") }
    }
  }
  picking?.let { (di, si) ->
    ExercisePickerDialog(
      title = "Pick exercise",
      multiSelect = false,
      onDismiss = { picking = null },
      onPicked = { picked ->
        picking = null
        picked.firstOrNull()?.let { b.pick(di, si, it) }
      },
    )
  }
}
