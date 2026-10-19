package com.ironlog.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.Settings
import com.ironlog.app.data.db.BodyMeasurementEntity
import com.ironlog.app.domain.Units
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LineChart
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private class MeasureField(val label: String, val get: (BodyMeasurementEntity) -> Double?, val set: (BodyMeasurementEntity, Double?) -> BodyMeasurementEntity)

/** Circumferences, all stored in cm. */
private val cmFields =
  listOf(
    MeasureField("Neck", { it.neckCm }) { m, v -> m.copy(neckCm = v) },
    MeasureField("Shoulders", { it.shouldersCm }) { m, v -> m.copy(shouldersCm = v) },
    MeasureField("Chest", { it.chestCm }) { m, v -> m.copy(chestCm = v) },
    MeasureField("Left bicep", { it.leftBicepCm }) { m, v -> m.copy(leftBicepCm = v) },
    MeasureField("Right bicep", { it.rightBicepCm }) { m, v -> m.copy(rightBicepCm = v) },
    MeasureField("Left forearm", { it.leftForearmCm }) { m, v -> m.copy(leftForearmCm = v) },
    MeasureField("Right forearm", { it.rightForearmCm }) { m, v -> m.copy(rightForearmCm = v) },
    MeasureField("Waist", { it.waistCm }) { m, v -> m.copy(waistCm = v) },
    MeasureField("Hips", { it.hipsCm }) { m, v -> m.copy(hipsCm = v) },
    MeasureField("Left thigh", { it.leftThighCm }) { m, v -> m.copy(leftThighCm = v) },
    MeasureField("Right thigh", { it.rightThighCm }) { m, v -> m.copy(rightThighCm = v) },
    MeasureField("Left calf", { it.leftCalfCm }) { m, v -> m.copy(leftCalfCm = v) },
    MeasureField("Right calf", { it.rightCalfCm }) { m, v -> m.copy(rightCalfCm = v) },
  )

private val listDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val chartDate = DateTimeFormatter.ofPattern("d MMM")

private fun summary(m: BodyMeasurementEntity, s: Settings): String =
  buildList {
      m.weightKg?.let { add("Weight ${Units.weight(it, s.weightUnit)}") }
      m.bodyFatPct?.let { add("Body fat ${Units.number(it)}%") }
      cmFields.forEach { f -> f.get(m)?.let { add("${f.label} ${Units.number(it)} cm") } }
    }
    .joinToString(" · ")
    .ifEmpty { "No values" }

@Composable
fun MeasurementsScreen(nav: Navigator) {
  val c = LocalContainer.current
  val settings = LocalSettings.current
  val entries by c.measurements.measurements.collectAsStateWithLifecycle(emptyList())
  var editing by remember { mutableStateOf<BodyMeasurementEntity?>(null) }
  var isNew by remember { mutableStateOf(false) }

  AppScaffold(
    title = "Measurements",
    onBack = { nav.back() },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          isNew = true
          editing = BodyMeasurementEntity(date = LocalDate.now().toString())
        }
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Log measurements")
      }
    },
  ) { padding ->
    if (entries.isEmpty()) {
      EmptyState("Track your body", "Log bodyweight, body fat and circumferences to see how they change over time.", Modifier.padding(padding))
      return@AppScaffold
    }
    LazyColumn(contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      val weights = entries.filter { it.weightKg != null }.sortedBy { it.date }.takeLast(30)
      if (weights.size >= 2) {
        item {
          SurfaceCard(Modifier.reveal()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Bodyweight", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
              val labels = listOf(weights.first(), weights[weights.size / 2], weights.last()).map { LocalDate.parse(it.date).format(chartDate) }
              LineChart(weights.map { Units.toDisplayWeight(it.weightKg!!, settings.weightUnit) }, labels, formatValue = { "${Units.number(it)} ${settings.weightUnit.label}" })
            }
          }
        }
      }
      items(entries, key = { it.date }) { m ->
        SurfaceCard(
          onClick = {
            isNew = false
            editing = m
          }
        ) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(LocalDate.parse(m.date).format(listDate), style = MaterialTheme.typography.titleMedium)
            Text(summary(m, settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }

  editing?.let { entry -> MeasurementEditor(entry, isNew, settings, existingDates = entries.map { it.date }.toSet(), onDismiss = { editing = null }) }
}

@Composable
private fun MeasurementEditor(entry: BodyMeasurementEntity, isNew: Boolean, settings: Settings, existingDates: Set<String>, onDismiss: () -> Unit) {
  val c = LocalContainer.current
  val scope = rememberCoroutineScope()
  var date by remember { mutableStateOf(LocalDate.parse(entry.date)) }
  var pickingDate by remember { mutableStateOf(false) }
  val texts =
    remember {
      mutableStateMapOf<String, String>().apply {
        put("weight", entry.weightKg?.let { Units.number(Units.toDisplayWeight(it, settings.weightUnit)) } ?: "")
        put("fat", entry.bodyFatPct?.let(Units::number) ?: "")
        cmFields.forEach { f -> put(f.label, f.get(entry)?.let(Units::number) ?: "") }
      }
    }

  fun build(): BodyMeasurementEntity {
    var m = BodyMeasurementEntity(date = date.toString(), weightKg = Units.parseDecimal(texts["weight"].orEmpty())?.let { Units.fromDisplayWeight(it, settings.weightUnit) }, bodyFatPct = Units.parseDecimal(texts["fat"].orEmpty()))
    cmFields.forEach { f -> m = f.set(m, Units.parseDecimal(texts[f.label].orEmpty())) }
    return m
  }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.safeDrawingPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text(if (isNew) "Log measurements" else "Edit measurements", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (isNew) {
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) { Text(date.format(listDate)) }
            if (date.toString() in existingDates) Text("An entry for this day exists — saving replaces it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          } else {
            Text(date.format(listDate), style = MaterialTheme.typography.titleMedium)
          }
          MeasureInput("Bodyweight (${settings.weightUnit.label})", texts["weight"].orEmpty(), Modifier.fillMaxWidth()) { texts["weight"] = it }
          MeasureInput("Body fat (%)", texts["fat"].orEmpty(), Modifier.fillMaxWidth()) { texts["fat"] = it }
          Eyebrow("Circumferences (cm)", modifier = Modifier.padding(top = 12.dp))
          cmFields.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              pair.forEach { f -> MeasureInput(f.label, texts[f.label].orEmpty(), Modifier.weight(1f)) { texts[f.label] = it } }
              if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
          }
          Button(
            onClick = {
              scope.launch {
                c.measurements.save(build())
                onDismiss()
              }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          ) {
            Text("Save")
          }
          if (!isNew) {
            TextButton(
              onClick = {
                scope.launch {
                  c.measurements.delete(entry.date)
                  onDismiss()
                }
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Delete entry", color = MaterialTheme.colorScheme.error)
            }
          }
        }
      }
    }
  }

  if (pickingDate) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
      onDismissRequest = { pickingDate = false },
      confirmButton = {
        TextButton(
          onClick = {
            state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
            pickingDate = false
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
    ) {
      DatePicker(state)
    }
  }
}

@Composable
private fun MeasureInput(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(7)) },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
  )
}
