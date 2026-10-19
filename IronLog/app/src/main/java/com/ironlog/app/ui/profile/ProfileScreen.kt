package com.ironlog.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.db.WorkoutWithExercises
import com.ironlog.app.domain.Stats
import com.ironlog.app.domain.Units
import com.ironlog.app.domain.WeeklyMetric
import com.ironlog.app.domain.WorkoutTotals
import com.ironlog.app.model.SetType
import com.ironlog.app.theme.accent
import com.ironlog.app.theme.onAccent
import com.ironlog.app.ui.ExerciseDetail
import com.ironlog.app.ui.Measurements
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.SettingsRoute
import com.ironlog.app.ui.WorkoutDetail
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.BarChart
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.OpenArrow
import com.ironlog.app.ui.components.Rule
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import com.ironlog.app.ui.components.stagger
import com.ironlog.app.ui.history.durationSec
import com.ironlog.app.ui.history.setCount
import com.ironlog.app.ui.history.volumeKg
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val weekLabel = DateTimeFormatter.ofPattern("d MMM")
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun ProfileScreen(nav: Navigator) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val workouts by c.workouts.workouts.collectAsStateWithLifecycle(emptyList())
  val zone = remember { ZoneId.systemDefault() }
  val today = remember { LocalDate.now(zone) }

  val totals =
    remember(workouts, settings.includeWarmupsInStats) {
      workouts.map { w ->
        val reps = w.exercises.sumOf { e -> e.sets.filter { settings.includeWarmupsInStats || it.type != SetType.WARMUP }.sumOf { it.reps ?: 0 } }
        WorkoutTotals(w.workout.id, w.workout.startTime, w.workout.endTime, w.volumeKg(settings.includeWarmupsInStats), reps, w.setCount(settings.includeWarmupsInStats))
      }
    }
  val dates = remember(workouts) { workouts.map { Stats.localDate(it.workout.startTime, zone) } }

  AppScaffold(
    title = "Profile",
    large = true,
    actions = { IconButton(onClick = { nav.go(SettingsRoute) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } },
  ) { padding ->
    LazyColumn(contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        SurfaceCard(Modifier.reveal()) {
          Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("Workouts", workouts.size.toString())
            StatTile("Week streak", Stats.weekStreak(dates, today).toString())
            StatTile("Total time", Units.durationWords(workouts.sumOf { it.durationSec() }))
          }
        }
      }
      if (workouts.isEmpty()) {
        item { EmptyState("Your stats live here", "Finish a workout and you'll see weekly charts, your training calendar and which muscles you've worked.") }
      } else {
        item { WeeklyChartCard(totals, today, zone, Modifier.reveal(stagger(1))) }
        item { CalendarCard(dates.toSet(), workouts, zone, onOpen = { nav.go(WorkoutDetail(it)) }, Modifier.reveal(stagger(2))) }
        item { MuscleSplitCard(workouts, today, zone, Modifier.reveal(stagger(3))) }
        item { TopExercisesCard(workouts, onOpen = { nav.go(ExerciseDetail(it)) }, Modifier.reveal(stagger(4))) }
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { nav.go(Measurements) }, modifier = Modifier.weight(1f)) { Text("Measurements") }
          OutlinedButton(onClick = { nav.go(SettingsRoute) }, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
      }
    }
  }
}

@Composable
private fun WeeklyChartCard(totals: List<WorkoutTotals>, today: LocalDate, zone: ZoneId, modifier: Modifier = Modifier) {
  val settings = LocalSettings.current
  var metric by remember { mutableStateOf(WeeklyMetric.DURATION) }
  val buckets = remember(totals, today) { Stats.weeklyBuckets(totals, 12, today, zone) }
  SurfaceCard(modifier) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Last 12 weeks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeeklyMetric.entries.forEach { m -> SelectChip(selected = m == metric, onClick = { metric = m }, label = m.label) }
      }
      val values = buckets.map { Stats.metricValue(it, metric) }
      val labels = buckets.mapIndexed { i, b -> if (i % 3 == 0 || i == buckets.lastIndex) b.weekStart.format(weekLabel) else "" }
      BarChart(
        values = values,
        labels = labels,
        formatMax = { v ->
          when (metric) {
            WeeklyMetric.DURATION -> "max ${Units.number(v)} h"
            WeeklyMetric.VOLUME -> "max ${Units.volume(v, settings.weightUnit)}"
            WeeklyMetric.REPS -> "max ${v.toInt()} reps"
            WeeklyMetric.WORKOUTS -> "max ${v.toInt()} workouts"
          }
        },
      )
    }
  }
}

@Composable
private fun CalendarCard(days: Set<LocalDate>, workouts: List<WorkoutWithExercises>, zone: ZoneId, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
  var month by remember { mutableStateOf(YearMonth.now(zone)) }
  val colors = MaterialTheme.colorScheme
  val inMonth = days.count { YearMonth.from(it) == month }
  SurfaceCard(modifier) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(month.format(monthTitle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
          Eyebrow("$inMonth workout${if (inMonth == 1) "" else "s"}")
        }
        IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
        IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
      }
      Row(Modifier.fillMaxWidth()) {
        java.time.DayOfWeek.entries.forEach {
          Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
      }
      val first = month.atDay(1)
      val leading = first.dayOfWeek.value - 1 // Monday-first grid
      val cells = leading + month.lengthOfMonth()
      (0 until (cells + 6) / 7).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
          (0 until 7).forEach { col ->
            val dayNum = row * 7 + col - leading + 1
            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
              if (dayNum in 1..month.lengthOfMonth()) {
                val date = month.atDay(dayNum)
                val trained = date in days
                val id = if (trained) workouts.firstOrNull { Stats.localDate(it.workout.startTime, zone) == date }?.workout?.id else null
                val isToday = date == LocalDate.now(zone)
                Box(
                  Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (trained) colors.accent else Color.Transparent)
                    .then(if (isToday && !trained) Modifier.border(1.dp, colors.onSurface.copy(alpha = 0.4f), CircleShape) else Modifier)
                    .clickable(enabled = id != null) { id?.let(onOpen) },
                  contentAlignment = Alignment.Center,
                ) {
                  Text("$dayNum", style = MaterialTheme.typography.bodySmall, color = if (trained) colors.onAccent else colors.onSurface, fontWeight = if (isToday || trained) FontWeight.SemiBold else FontWeight.Normal)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MuscleSplitCard(workouts: List<WorkoutWithExercises>, today: LocalDate, zone: ZoneId, modifier: Modifier = Modifier) {
  val settings = LocalSettings.current
  val since = today.minusDays(29)
  val counts =
    remember(workouts, settings.includeWarmupsInStats) {
      val sets =
        workouts
          .filter { !Stats.localDate(it.workout.startTime, zone).isBefore(since) }
          .flatMap { w -> w.exercises.flatMap { e -> e.sets.filter { settings.includeWarmupsInStats || it.type != SetType.WARMUP }.map { e.exercise.primaryMuscle to e.exercise.secondaryMuscles } } }
      Stats.muscleSetCounts(sets)
    }
  SurfaceCard(modifier) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Sets per muscle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
      Eyebrow("Last 30 days")
      Text("Primary muscle counts 1 per set, other muscles ½.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (counts.isEmpty()) Text("No sets in the last 30 days.", style = MaterialTheme.typography.bodySmall)
      val max = counts.maxOfOrNull { it.second } ?: 1.0
      counts.take(10).forEach { (muscle, n) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(muscle.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp))
          Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape)) {
            Box(Modifier.fillMaxWidth((n / max).toFloat()).height(10.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), CircleShape))
          }
          Text(Units.number(n), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        }
      }
    }
  }
}

@Composable
private fun TopExercisesCard(workouts: List<WorkoutWithExercises>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
  val top =
    remember(workouts) {
      workouts.flatMap { w -> w.exercises.map { it.exercise } }.groupBy { it.id }.map { (_, list) -> list.first() to list.size }.sortedByDescending { it.second }.take(5)
    }
  SurfaceCard(modifier) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Most trained", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
      top.forEachIndexed { i, (ex, n) ->
        if (i > 0) Rule()
        Row(Modifier.fillMaxWidth().clickable { onOpen(ex.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ex.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            OpenArrow()
          }
          Eyebrow("$n workout${if (n == 1) "" else "s"}")
        }
      }
    }
  }
}
