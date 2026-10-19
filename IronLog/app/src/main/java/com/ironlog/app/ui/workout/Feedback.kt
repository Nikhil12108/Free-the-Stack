package com.ironlog.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.workout.ActiveWorkout
import com.ironlog.app.workout.MuscleAnswers
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.TargetPlanner

val WorkloadOptions = listOf("Easy", "About right", "At my limit", "Too much")
val PumpOptions = listOf("Low", "Moderate", "Great")
/** Soreness options map to 1…4 (never sore … still sore). */
val SorenessOptions = listOf("Never sore", "Healed early", "Just in time", "Still sore")
val JointOptions = listOf("Fine", "Mild", "Achy", "Painful")

fun List<MuscleAnswers>.answer(m: TrainingMuscle): MuscleAnswers = firstOrNull { it.muscle == m } ?: MuscleAnswers(m)

fun List<MuscleAnswers>.with(m: TrainingMuscle, f: (MuscleAnswers) -> MuscleAnswers): List<MuscleAnswers> = filterNot { it.muscle == m } + f(answer(m))

/** True when every working set of every exercise for [m] is ticked. */
fun ActiveWorkout.muscleDone(m: TrainingMuscle): Boolean {
  val exs = exercises.filter { TargetPlanner.traits(it).muscle == m }
  return exs.isNotEmpty() && exs.all { ex -> ex.sets.filter { it.type.isWorking }.let { s -> s.isNotEmpty() && s.all { it.completed } } }
}

/** The last exercise in the list for [ex]'s muscle — where that muscle's questions appear. */
fun ActiveWorkout.isLastFor(ex: SessionExercise): TrainingMuscle? {
  val m = TargetPlanner.traits(ex).muscle ?: return null
  return m.takeIf { exercises.lastOrNull { TargetPlanner.traits(it).muscle == m }?.key == ex.key }
}

@Composable
private fun ChoiceRow(question: String, options: List<String>, selected: Int?, onPick: (Int?) -> Unit) {
  Column(Modifier.padding(vertical = 4.dp)) {
    Text(question, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
      options.forEachIndexed { i, label -> SelectChip(selected = selected == i, onClick = { onPick(if (selected == i) null else i) }, label = label) }
    }
  }
}

/** Questions about a muscle once its work is done: how much it was, and (full mode) the pump. */
@Composable
fun MuscleCheckCard(muscle: TrainingMuscle, answers: MuscleAnswers, mode: FeedbackMode, onWorkload: (Int?) -> Unit, onPump: (Int?) -> Unit, modifier: Modifier = Modifier) {
  SurfaceCard(modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Eyebrow("${muscle.label} · how did it go?")
      ChoiceRow("How much work was that for your ${muscle.label.lowercase()}?", WorkloadOptions, answers.workload, onWorkload)
      if (mode == FeedbackMode.FULL) ChoiceRow("Pump?", PumpOptions, answers.pump, onPump)
      Text("Your answers shape next week's sets for this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Full mode: how each muscle recovered since it was last trained in this plan. */
@Composable
fun RecoveryCheckCard(muscles: List<TrainingMuscle>, answers: List<MuscleAnswers>, onSoreness: (TrainingMuscle, Int?) -> Unit, modifier: Modifier = Modifier) {
  if (muscles.isEmpty()) return
  SurfaceCard(modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Eyebrow("Recovery check")
      Text("How sore did each muscle get after its last session?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      muscles.forEach { m -> ChoiceRow(m.label, SorenessOptions, answers.answer(m).soreness?.minus(1), { i -> onSoreness(m, i?.plus(1)) }) }
    }
  }
}

/** One-tap joint check under an exercise once its sets are done. */
@Composable
fun JointCheckRow(value: Int?, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Eyebrow("Joints")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      JointOptions.forEachIndexed { i, label -> SelectChip(selected = value == i, onClick = { onPick(if (value == i) null else i) }, label = label) }
    }
  }
}
