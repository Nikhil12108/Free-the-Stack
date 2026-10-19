package com.ironlog.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.Settings
import com.ironlog.app.domain.PrKind
import com.ironlog.app.domain.SetValues
import com.ironlog.app.domain.Units
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.SetType
import com.ironlog.app.theme.EyebrowStyle
import com.ironlog.app.theme.accent
import com.ironlog.app.theme.accentText
import com.ironlog.app.theme.onAccent
import com.ironlog.app.theme.warmup
import com.ironlog.app.ui.components.DurationCell
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.NumberCell
import com.ironlog.app.ui.components.OpenArrow
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.progression.Verdict
import com.ironlog.app.progression.describe
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionOps
import com.ironlog.app.workout.SessionSet
import com.ironlog.app.workout.TargetPlanner

enum class SetTableMode {
  LIVE,
  ROUTINE,
}

/** One menu entry in an exercise card's overflow menu. */
data class CardAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun ExerciseCard(
  exercise: SessionExercise,
  mode: SetTableMode,
  settings: Settings,
  previous: List<SetValues>,
  prs: Map<Long, Set<PrKind>>,
  supersetColor: Color?,
  actions: List<CardAction>,
  onTitleClick: () -> Unit,
  onNotesChange: (String) -> Unit,
  onRestClick: () -> Unit,
  onUpdateSet: (Long, (SessionSet) -> SessionSet) -> Unit,
  onToggleComplete: (Long) -> Unit,
  onAddSet: () -> Unit,
  onRemoveSet: (Long) -> Unit,
  modifier: Modifier = Modifier,
  bodyweightKg: Double? = null,
) {
  val colors = MaterialTheme.colorScheme
  SurfaceCard(modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
    // Superset stripe down the card's left edge (the card clips it to its corners).
    val stripe = Modifier.drawBehind { if (supersetColor != null) drawRect(supersetColor, size = Size(4.dp.toPx(), size.height)) }
    Column(stripe.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onTitleClick).padding(vertical = 6.dp)) {
          if (exercise.supersetGroup != null) Eyebrow("Superset", color = supersetColor ?: colors.onSurfaceVariant)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, fontSize = 20.sp, modifier = Modifier.weight(1f, fill = false))
            OpenArrow()
          }
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
          IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options") }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            actions.forEach { a ->
              DropdownMenuItem(
                text = { Text(a.label, color = if (a.destructive) colors.error else Color.Unspecified) },
                onClick = {
                  menuOpen = false
                  a.onClick()
                },
              )
            }
          }
        }
      }
      if (mode == SetTableMode.LIVE) TargetLine(exercise, settings)
      NotesField(exercise.notes, onNotesChange)
      Box(
        Modifier.padding(vertical = 6.dp).clip(CircleShape).border(1.dp, colors.outline, CircleShape).clickable(onClick = onRestClick).padding(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Eyebrow("Rest · " + if (exercise.restSeconds > 0) Units.clock(exercise.restSeconds.toLong()) else "off", color = colors.onSurface)
      }
      HeaderRow(exercise.type, mode, settings)
      var normalIndex = 0
      exercise.sets.forEachIndexed { i, set ->
        if (set.type != SetType.WARMUP) normalIndex++
        // Keyed so a row's in-progress text stays with its set when sets are removed or reordered.
        key(set.key) {
        SetRow(
          set = set,
          label = if (set.type == SetType.NORMAL) normalIndex.toString() else set.type.short,
          type = exercise.type,
          mode = mode,
          settings = settings,
          previous = SessionOps.previousFor(exercise.sets, previous, i),
          prs = prs[set.key].orEmpty(),
          bodyweightKg = bodyweightKg,
          onUpdate = { f -> onUpdateSet(set.key, f) },
          onToggle = { onToggleComplete(set.key) },
          onRemove = { onRemoveSet(set.key) },
        )
        }
      }
      FilledTonalButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 6.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add set")
      }
    }
  }
}

/** "3 × 10 · 82.5 kg · 2 RIR" plus why — quiet text, the accent stays for live state and records. */
@Composable
private fun TargetLine(exercise: SessionExercise, settings: Settings) {
  val summary = targetSummary(exercise, settings) ?: return
  val colors = MaterialTheme.colorScheme
  Column(Modifier.padding(top = 2.dp, bottom = 2.dp)) {
    Text(summary, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
    exercise.reason?.let { r ->
      Text(r.describe(settings.weightUnit, exercise.sets.firstNotNullOfOrNull { it.targetRir } ?: exercise.targetRir ?: settings.defaultRir), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
  }
}

/** Compact description of an exercise's targets, or null when it has none. */
fun targetSummary(ex: SessionExercise, s: Settings): String? {
  val working = ex.sets.filter { it.type.isWorking }
  if (working.isEmpty() || working.none { it.targetWeightKg != null || it.targetReps != null || it.targetRir != null }) return null
  val weights = working.mapNotNull { it.targetWeightKg }
  val sign =
    when (ex.type) {
      ExerciseType.WEIGHTED_BODYWEIGHT -> "+"
      ExerciseType.ASSISTED_BODYWEIGHT -> "−"
      else -> ""
    }
  val weightPart =
    when {
      !ex.type.usesWeight || weights.isEmpty() -> null
      weights.distinct().size == 1 -> sign + Units.weight(weights[0], s.weightUnit)
      else -> sign + Units.number(Units.toDisplayWeight(weights.min(), s.weightUnit)) + "–" + Units.weight(weights.max(), s.weightUnit)
    }
  val reps = working.map { it.targetReps }
  val repsPart =
    when {
      reps.all { it != null } && reps.distinct().size == 1 -> "${working.size} × ${reps[0]}"
      reps.all { it != null } -> reps.joinToString(", ") + " reps"
      else -> {
        val lo = working.firstNotNullOfOrNull { it.repMin }
        val hi = working.firstNotNullOfOrNull { it.repMax }
        if (lo != null && hi != null) "${working.size} × $lo–$hi" else "${working.size} sets"
      }
    }
  val rir = working.firstNotNullOfOrNull { it.targetRir }?.let { if (it >= 5) "easy" else "$it RIR" }
  return listOfNotNull(repsPart, weightPart, rir).joinToString(" · ")
}

@Composable
private fun NotesField(notes: String, onChange: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface)
  BasicTextField(
    value = notes,
    onValueChange = onChange,
    textStyle = style,
    cursorBrush = SolidColor(colors.primary),
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    decorationBox = { inner ->
      Box {
        if (notes.isEmpty()) Text("Add notes…", style = style.copy(color = colors.onSurfaceVariant.copy(alpha = 0.6f)))
        inner()
      }
    },
  )
}

private val SetColW = 40.dp
private val CheckColW = 44.dp

private fun weightHeader(type: ExerciseType, s: Settings) =
  when (type) {
    ExerciseType.WEIGHTED_BODYWEIGHT -> "+${s.weightUnit.label}"
    ExerciseType.ASSISTED_BODYWEIGHT -> "−${s.weightUnit.label}"
    else -> s.weightUnit.label
  }.uppercase()

private fun distanceHeader(type: ExerciseType, s: Settings) = if (type == ExerciseType.WEIGHT_DISTANCE) "M" else s.distanceUnit.label.uppercase()

@Composable
private fun HeaderRow(type: ExerciseType, mode: SetTableMode, s: Settings) {
  val style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp)
  val c = MaterialTheme.colorScheme.onSurfaceVariant
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("SET", style = style, color = c, modifier = Modifier.width(SetColW), textAlign = TextAlign.Center)
    if (mode == SetTableMode.LIVE) Text("PREVIOUS", style = style, color = c, modifier = Modifier.weight(1.4f), textAlign = TextAlign.Center)
    if (type.usesWeight) Text(weightHeader(type, s), style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    if (type.usesDistance) Text(distanceHeader(type, s), style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    if (type.usesReps) Text("REPS", style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    if (type.usesDuration) Text("TIME", style = style, color = c, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    if (mode == SetTableMode.LIVE && s.rirEnabled) Text("RIR", style = style, color = c, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
    if (mode == SetTableMode.LIVE) Box(Modifier.width(CheckColW), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = c, modifier = Modifier.size(16.dp)) }
  }
}

/** "60 kg × 8", "1:30", "2.5 km · 15:00" — the PREVIOUS column and history lists. */
fun describeSet(type: ExerciseType, v: SetValues, s: Settings): String {
  val parts = mutableListOf<String>()
  val w = v.weightKg
  if (type.usesWeight && w != null) {
    val sign =
      when (type) {
        ExerciseType.WEIGHTED_BODYWEIGHT -> "+"
        ExerciseType.ASSISTED_BODYWEIGHT -> "−"
        else -> ""
      }
    parts += sign + Units.weight(w, s.weightUnit)
  }
  val d = v.distanceM
  if (type.usesDistance && d != null) parts += if (type == ExerciseType.WEIGHT_DISTANCE) Units.shortDistance(d) else Units.distance(d, s.distanceUnit)
  val r = v.reps
  if (type.usesReps && r != null) return (if (parts.isEmpty()) "$r reps" else parts.joinToString(" ") + " × $r")
  val t = v.durationSec
  if (type.usesDuration && t != null) parts += Units.clock(t.toLong())
  return parts.joinToString(" · ").ifEmpty { "—" }
}

@Composable
private fun SetRow(
  set: SessionSet,
  label: String,
  type: ExerciseType,
  mode: SetTableMode,
  settings: Settings,
  previous: SetValues?,
  prs: Set<PrKind>,
  bodyweightKg: Double?,
  onUpdate: ((SessionSet) -> SessionSet) -> Unit,
  onToggle: () -> Unit,
  onRemove: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val done = mode == SetTableMode.LIVE && set.completed
  val live = mode == SetTableMode.LIVE
  val unit = settings.weightUnit
  val rowBg = if (done) colors.accent.copy(alpha = 0.14f) else Color.Transparent
  Row(
    Modifier.fillMaxWidth().background(rowBg, RoundedCornerShape(10.dp)).padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    SetTypeLabel(label, set.type, prs.isNotEmpty(), onTypeChange = { t -> onUpdate { it.copy(type = t) } }, onRemove = onRemove)
    if (mode == SetTableMode.LIVE) {
      val arrow =
        when (set.verdict.takeIf { done }) {
          Verdict.IMPROVED -> "↑ "
          Verdict.HELD -> "= "
          Verdict.REGRESSED -> "↓ "
          null -> ""
        }
      Text(
        arrow + (previous?.let { describeSet(type, it, settings) } ?: "—"),
        style = MaterialTheme.typography.bodySmall,
        color = if (set.verdict == Verdict.IMPROVED && done) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier =
          Modifier.weight(1.4f).clickable(enabled = previous != null && !done) {
            // Tap PREVIOUS to copy last time's numbers into this set.
            previous?.let { p -> onUpdate { it.copy(weightKg = p.weightKg, reps = p.reps, durationSec = p.durationSec, distanceM = p.distanceM) } }
          },
      )
    }
    val prevWeight = previous?.weightKg?.let { Units.number(Units.toDisplayWeight(it, unit)) }
    val targetWeight = set.targetWeightKg.takeIf { live }?.let { Units.number(Units.toDisplayWeight(it, unit)) }
    if (type.usesWeight) {
      NumberCell(
        value = set.weightKg?.let { Units.toDisplayWeight(it, unit) },
        onValueChange = { v -> onUpdate { it.copy(weightKg = v?.let { x -> Units.fromDisplayWeight(x, unit) }) } },
        placeholder = targetWeight ?: prevWeight ?: "0",
        modifier = Modifier.weight(1f),
        done = done,
      )
    }
    if (type.usesDistance) {
      val short = type == ExerciseType.WEIGHT_DISTANCE
      NumberCell(
        value = set.distanceM?.let { if (short) it else Units.toDisplayDistance(it, settings.distanceUnit) },
        onValueChange = { v -> onUpdate { it.copy(distanceM = v?.let { x -> if (short) x else Units.fromDisplayDistance(x, settings.distanceUnit) }) } },
        placeholder = previous?.distanceM?.let { if (short) Units.number(it) else Units.number(Units.toDisplayDistance(it, settings.distanceUnit)) } ?: "0",
        modifier = Modifier.weight(1f),
        done = done,
      )
    }
    if (type.usesReps) {
      if (mode == SetTableMode.ROUTINE) {
        RepRangeCell(set, onUpdate, Modifier.weight(1f))
      } else {
        val range = if (set.repMin != null && set.repMax != null) "${set.repMin}-${set.repMax}" else null
        val target = TargetPlanner.effectiveTargetReps(set, type, bodyweightKg)?.toString()
        NumberCell(
          value = set.reps?.toDouble(),
          onValueChange = { v -> onUpdate { it.copy(reps = v?.toInt()) } },
          placeholder = target ?: range ?: previous?.reps?.toString() ?: "0",
          decimal = false,
          modifier = Modifier.weight(1f),
          done = done,
        )
      }
    }
    if (type.usesDuration) {
      DurationCell(
        value = set.durationSec,
        onValueChange = { v -> onUpdate { it.copy(durationSec = v) } },
        placeholder = previous?.durationSec?.let { Units.clock(it.toLong()) } ?: "0:00",
        modifier = Modifier.weight(1f),
        done = done,
      )
    }
    if (live && settings.rirEnabled) RirButton(set.rir, set.targetRir, onPick = { r -> onUpdate { it.copy(rir = r) } })
    if (mode == SetTableMode.LIVE) {
      Box(
        Modifier.width(CheckColW).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (done) colors.accent else colors.surfaceContainerHighest).clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Filled.Check, contentDescription = if (done) "Mark not done" else "Complete set", tint = if (done) colors.onAccent else colors.onSurfaceVariant)
      }
    }
  }
}

/** Routine targets: "10" for exact reps or "8-12" for a range. */
@Composable
private fun RepRangeCell(set: SessionSet, onUpdate: ((SessionSet) -> SessionSet) -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  fun format(s: SessionSet) = if (s.repMin != null && s.repMax != null) "${s.repMin}-${s.repMax}" else s.reps?.toString() ?: ""
  var text by remember { mutableStateOf(format(set)) }
  LaunchedEffect(set.reps, set.repMin, set.repMax) { if (parseRange(text) != Triple(set.reps, set.repMin, set.repMax)) text = format(set) }
  val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
  BasicTextField(
    value = text,
    onValueChange = { t ->
      val clean = t.filter { it.isDigit() || it == '-' }.take(7)
      text = clean
      val (reps, min, max) = parseRange(clean)
      onUpdate { it.copy(reps = reps, repMin = min, repMax = max) }
    },
    singleLine = true,
    textStyle = style,
    cursorBrush = SolidColor(colors.primary),
    modifier = modifier.background(colors.surfaceContainerHighest, RoundedCornerShape(10.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
    decorationBox = { inner ->
      Box(contentAlignment = Alignment.Center) {
        if (text.isEmpty()) Text("8-12", style = style.copy(color = colors.onSurfaceVariant.copy(alpha = 0.55f)))
        inner()
      }
    },
  )
}

/** "10" -> (10, null, null); "8-12" -> (null, 8, 12); partial "8-" -> (null, null, null). */
internal fun parseRange(text: String): Triple<Int?, Int?, Int?> {
  val parts = text.split('-')
  if (parts.size == 1) return Triple(parts[0].toIntOrNull(), null, null)
  val a = parts[0].toIntOrNull()
  val b = parts.getOrNull(1)?.toIntOrNull()
  return if (a != null && b != null && b >= a) Triple(null, a, b) else Triple(null, null, null)
}

@Composable
private fun SetTypeLabel(label: String, type: SetType, isPr: Boolean, onTypeChange: (SetType) -> Unit, onRemove: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  var open by remember { mutableStateOf(false) }
  val color =
    when (type) {
      SetType.WARMUP -> colors.warmup
      SetType.FAILURE -> colors.error
      SetType.DROPSET -> colors.tertiary
      SetType.NORMAL -> colors.onSurface
    }
  Box(Modifier.width(SetColW)) {
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
      if (isPr) Icon(Icons.Filled.Star, contentDescription = "Personal record", tint = colors.accentText, modifier = Modifier.size(14.dp))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      SetType.entries.forEach { t ->
        DropdownMenuItem(
          text = { Text(t.label + if (t.short.isNotEmpty()) "  (${t.short})" else "") },
          onClick = {
            open = false
            onTypeChange(t)
          },
        )
      }
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text("Remove set", color = colors.error) },
        onClick = {
          open = false
          onRemove()
        },
      )
    }
  }
}

/** Reps in reserve: how many more reps the set had left. Shows the target faintly until logged. */
@Composable
private fun RirButton(rir: Int?, target: Int?, onPick: (Int?) -> Unit) {
  var open by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  Text(
    rir?.let { if (it >= 5) "5+" else it.toString() } ?: target?.let { if (it >= 5) "5+" else it.toString() } ?: "–",
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    color = if (rir != null) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.55f),
    modifier = Modifier.width(36.dp).clickable { open = true }.padding(vertical = 8.dp),
  )
  if (open) {
    AlertDialog(
      onDismissRequest = { open = false },
      title = { Text("Reps in reserve") },
      text = {
        Column {
          Text(
            "How many more good reps could you have done? 0 means nothing left. Logging it sharpens your targets, but it's optional.",
            style = MaterialTheme.typography.bodySmall,
          )
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..5).forEach { v ->
              TextButton(
                onClick = {
                  onPick(v)
                  open = false
                }
              ) {
                Text(if (v == 5) "5+" else v.toString(), fontWeight = if (v == rir) FontWeight.Bold else FontWeight.Normal)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            onPick(null)
            open = false
          }
        ) {
          Text("Clear")
        }
      },
      dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
  }
}

/** Off, then common rest lengths. */
val RestOptions = listOf(0, 30, 45, 60, 75, 90, 105, 120, 150, 180, 210, 240, 300)

@Composable
fun RestPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rest timer") },
    text = {
      Column {
        RestOptions.chunked(4).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { s ->
              TextButton(
                onClick = {
                  onPick(s)
                  onDismiss()
                }
              ) {
                Text(if (s == 0) "Off" else Units.clock(s.toLong()), fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal)
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
