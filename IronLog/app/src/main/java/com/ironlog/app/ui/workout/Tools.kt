package com.ironlog.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.Settings
import com.ironlog.app.domain.PlateCalculator
import com.ironlog.app.domain.Units
import com.ironlog.app.domain.WarmupCalculator

@Composable
fun PlateCalculatorDialog(initialKg: Double?, settings: Settings, onDismiss: () -> Unit) {
  val unit = settings.weightUnit
  var target by remember { mutableStateOf(initialKg?.let { Units.number(Units.toDisplayWeight(it, unit)) } ?: "") }
  var bar by remember { mutableStateOf(Units.number(Units.toDisplayWeight(settings.barWeightKg, unit))) }
  val result =
    Units.parseDecimal(target)?.let { t -> PlateCalculator.calculate(t, Units.parseDecimal(bar) ?: PlateCalculator.defaultBar(unit), PlateCalculator.defaultPlates(unit)) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Plate calculator") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Total (${unit.label})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = bar,
            onValueChange = { bar = it },
            label = { Text("Bar") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
        }
        if (result != null) {
          Text("Each side", style = MaterialTheme.typography.labelLarge)
          if (result.platesPerSide.isEmpty()) Text("Just the bar")
          result.platesPerSide.forEach { (plate, n) -> Text("$n × ${Units.number(plate)} ${unit.label}", fontWeight = FontWeight.SemiBold) }
          if (result.remainder > 0.001) {
            Text(
              "Closest: ${Units.number(result.achievedTotal)} ${unit.label} (${Units.number(result.remainder)} short)",
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

/** Shows the warm-up ramp for the first working set and lets the user insert it. */
@Composable
fun WarmupDialog(workingKg: Double?, usesBar: Boolean, settings: Settings, onApply: (List<Pair<Double, Int>>) -> Unit, onDismiss: () -> Unit) {
  val unit = settings.weightUnit
  var working by remember { mutableStateOf(workingKg?.let { Units.number(Units.toDisplayWeight(it, unit)) } ?: "") }
  val barDisplay = if (usesBar) Units.toDisplayWeight(settings.barWeightKg, unit) else 0.0
  val sets = Units.parseDecimal(working)?.let { WarmupCalculator.warmups(it, barDisplay, unit) }.orEmpty()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Warm-up sets") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = working,
          onValueChange = { working = it },
          label = { Text("Working weight (${unit.label})") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (sets.isEmpty()) Text("Enter your working weight to see the ramp.", style = MaterialTheme.typography.bodySmall)
        sets.forEach { Text("${Units.number(it.weight)} ${unit.label} × ${it.reps}") }
        Text("Replaces any existing warm-up sets for this exercise.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
    confirmButton = {
      TextButton(
        enabled = sets.isNotEmpty(),
        onClick = {
          onApply(sets.map { Units.fromDisplayWeight(it.weight, unit) to it.reps })
          onDismiss()
        },
      ) {
        Text("Insert")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
