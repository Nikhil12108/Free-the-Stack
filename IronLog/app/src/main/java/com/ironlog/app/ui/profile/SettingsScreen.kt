package com.ironlog.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ironlog.app.BuildConfig
import com.ironlog.app.data.CsvExport
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.domain.PlateCalculator
import com.ironlog.app.domain.Units
import com.ironlog.app.model.DistanceUnit
import com.ironlog.app.model.ThemeMode
import com.ironlog.app.model.WeightUnit
import com.ironlog.app.progression.Experience
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.workout.RestPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: Navigator) {
  val c = LocalContainer.current
  val s = LocalSettings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  var pickingRest by remember { mutableStateOf(false) }
  var resumes by remember { mutableIntStateOf(0) }
  LifecycleResumeEffect(Unit) {
    resumes++
    onPauseOrDispose {}
  }
  val notificationsOn = remember(resumes) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
  val exactOn = remember(resumes) { c.restTimer.canScheduleExact() }

  fun export(uri: Uri?, build: suspend () -> String) {
    uri ?: return
    scope.launch {
      val ok =
        withContext(Dispatchers.IO) {
          runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(build().toByteArray()) } != null }.getOrDefault(false)
        }
      snackbar.showSnackbar(if (ok) "Exported" else "Export failed")
    }
  }
  val exportWorkouts = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> export(uri) { CsvExport.workouts(c.workouts.workouts.first()) } }
  val exportMeasurements = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> export(uri) { CsvExport.measurements(c.measurements.measurements.first()) } }

  AppScaffold(title = "Settings", onBack = { nav.back() }, snackbarHostState = snackbar) { padding ->
    Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
      SectionHeader("Units")
      Segmented("Weight", WeightUnit.entries, s.weightUnit, { it.label }) { u ->
        c.settings.update { it.copy(weightUnit = u, barWeightKg = Units.fromDisplayWeight(PlateCalculator.defaultBar(u), u)) }
      }
      Segmented("Distance", DistanceUnit.entries, s.distanceUnit, { it.label }) { u -> c.settings.update { it.copy(distanceUnit = u) } }

      SectionHeader("Workout")
      ClickRow("Default rest timer", if (s.defaultRestSeconds > 0) Units.clock(s.defaultRestSeconds.toLong()) else "Off") { pickingRest = true }
      ClickRow("Barbell weight", Units.weight(s.barWeightKg, s.weightUnit) + " (used by plate & warm-up calculators)") {
        val options = if (s.weightUnit == WeightUnit.KG) listOf(20.0, 15.0, 10.0) else listOf(45.0, 35.0, 25.0)
        val current = Units.toDisplayWeight(s.barWeightKg, s.weightUnit)
        val next = options[(options.indexOfFirst { kotlin.math.abs(it - current) < 0.1 } + 1).mod(options.size)]
        c.settings.update { it.copy(barWeightKg = Units.fromDisplayWeight(next, s.weightUnit)) }
      }
      SwitchRow("Keep screen on during workouts", null, s.keepScreenOn) { v -> c.settings.update { it.copy(keepScreenOn = v) } }
      SwitchRow("Log reps in reserve (RIR)", "How many more reps each set had left. Optional, but it sharpens your targets.", s.rirEnabled) { v -> c.settings.update { it.copy(rirEnabled = v) } }
      SwitchRow("Count warm-up sets in stats", "Volume, set counts and muscle split.", s.includeWarmupsInStats) { v -> c.settings.update { it.copy(includeWarmupsInStats = v) } }

      SectionHeader("Progression")
      SwitchRow("Smart targets", "Pre-fill each set with a weight and reps from your history and equipment.", s.smartTargets) { v -> c.settings.update { it.copy(smartTargets = v) } }
      ChipRow("Default reps in reserve", "How close to failure sets should end, unless an exercise or plan week says otherwise.", (0..4).toList(), s.defaultRir, { it.toString() }) { v ->
        c.settings.update { it.copy(defaultRir = v) }
      }
      ChipRow("Training experience", "Sets how fast targets expect you to progress.", Experience.entries, s.experience, { it.label.substringBefore(" (") }) { v ->
        c.settings.update { it.copy(experience = v) }
      }
      ChipRow("Questions after sets in a plan", s.feedbackMode.description, FeedbackMode.entries, s.feedbackMode, { it.label }) { v -> c.settings.update { it.copy(feedbackMode = v) } }
      SwitchRow("Smart warm-ups", "Add warm-up sets before the first exercise for each muscle.", s.smartWarmups) { v -> c.settings.update { it.copy(smartWarmups = v) } }
      SwitchRow("Rest by movement", "New exercises get rest suited to them: longest for heavy lower-body lifts, shortest for isolation.", s.restByMovement) { v ->
        c.settings.update { it.copy(restByMovement = v) }
      }

      SectionHeader("Equipment")
      Text(
        "Targets only use weights you can actually load.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val eq = s.equipment
      val unitLabel = s.weightUnit.label
      val plateOptions = if (s.weightUnit == WeightUnit.KG) listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5) else listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)
      Column(Modifier.padding(vertical = 6.dp)) {
        Text("Plates ($unitLabel, in pairs)", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
          plateOptions.forEach { p ->
            val on = eq.plates.any { kotlin.math.abs(it - p) < 1e-6 }
            SelectChip(
              selected = on,
              onClick = {
                val next = if (on) eq.plates.filterNot { kotlin.math.abs(it - p) < 1e-6 } else (eq.plates + p).sortedDescending()
                if (next.isNotEmpty()) c.settings.update { it.copy(equipment = eq.copy(plates = next)) }
              },
              label = Units.number(p),
            )
          }
        }
      }
      val kg = s.weightUnit == WeightUnit.KG
      ChipRow("Dumbbell steps ($unitLabel)", null, if (kg) listOf(1.0, 2.0, 2.5) else listOf(2.5, 5.0), eq.dumbbellStep, { Units.number(it) }) { v ->
        c.settings.update { it.copy(equipment = eq.copy(dumbbellStep = v)) }
      }
      ChipRow("Machine stack steps ($unitLabel)", null, if (kg) listOf(2.5, 5.0, 10.0) else listOf(5.0, 10.0, 15.0), eq.machineStep, { Units.number(it) }) { v ->
        c.settings.update { it.copy(equipment = eq.copy(machineStep = v)) }
      }
      ChipRow("Cable stack steps ($unitLabel)", null, if (kg) listOf(1.25, 2.5, 5.0) else listOf(2.5, 5.0, 10.0), eq.cableStep, { Units.number(it) }) { v ->
        c.settings.update { it.copy(equipment = eq.copy(cableStep = v)) }
      }

      SectionHeader("Rest alerts")
      ClickRow("Notifications", if (notificationsOn) "On" else "Off — tap to turn on") {
        context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ClickRow("Alarms & reminders", if (exactOn) "Allowed — alerts arrive on time" else "Not allowed — alerts may be late. Tap to allow.") {
          context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
        }
      }

      SectionHeader("Appearance")
      Segmented("Theme", ThemeMode.entries, s.theme, { it.label }) { t -> c.settings.update { it.copy(theme = t) } }

      SectionHeader("Your data")
      ClickRow("Export workouts (CSV)", "One row per set, in kg / m / s.") { exportWorkouts.launch("ironlog-workouts.csv") }
      ClickRow("Export measurements (CSV)", null) { exportMeasurements.launch("ironlog-measurements.csv") }

      SectionHeader("About")
      Text(
        "IronLog ${BuildConfig.VERSION_NAME}\nAn independent workout tracker. Everything you log stays on this device.\n" +
          "Typefaces: Inter and Playfair Display, under the SIL Open Font License.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (pickingRest) RestPickerDialog(s.defaultRestSeconds, onPick = { v -> c.settings.update { it.copy(defaultRestSeconds = v) } }, onDismiss = { pickingRest = false })
}

@Composable
private fun <E> Segmented(label: String, options: List<E>, selected: E, name: (E) -> String, onSelect: (E) -> Unit) {
  Column(Modifier.padding(vertical = 6.dp)) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
      options.forEachIndexed { i, o ->
        SegmentedButton(
          selected = o == selected,
          onClick = { onSelect(o) },
          shape = SegmentedButtonDefaults.itemShape(i, options.size),
          colors =
            SegmentedButtonDefaults.colors(
              activeContainerColor = MaterialTheme.colorScheme.primary,
              activeContentColor = MaterialTheme.colorScheme.onPrimary,
              inactiveContainerColor = Color.Transparent,
              inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
          Text(name(o))
        }
      }
    }
  }
}

/** A labelled row of single-choice pills. */
@Composable
private fun <E> ChipRow(title: String, subtitle: String?, options: List<E>, selected: E, name: (E) -> String, onSelect: (E) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
      options.forEach { o -> SelectChip(selected = o == selected, onClick = { onSelect(o) }, label = name(o)) }
    }
  }
}

@Composable
private fun ClickRow(title: String, subtitle: String?, onClick: () -> Unit) {
  Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
