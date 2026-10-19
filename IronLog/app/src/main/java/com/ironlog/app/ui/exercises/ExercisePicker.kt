package com.ironlog.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.theme.accentText
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.SelectChip
import kotlinx.coroutines.launch

/** Matches every word of [query] against name, muscle and equipment, so "db press" finds dumbbell presses. */
fun ExerciseEntity.matches(query: String): Boolean {
  val hay = "$name ${primaryMuscle.label} ${equipment.label} ${if (equipment == Equipment.DUMBBELL) "db" else ""}".lowercase()
  return query.lowercase().split(' ').filter { it.isNotBlank() }.all { it in hay }
}

@Composable
fun ExercisePickerDialog(title: String, multiSelect: Boolean, onDismiss: () -> Unit, onPicked: (List<ExerciseEntity>) -> Unit) {
  val container = LocalContainer.current
  val scope = rememberCoroutineScope()
  val all by container.exercises.exercises.collectAsStateWithLifecycle(emptyList())
  val usage by container.exercises.usage.collectAsStateWithLifecycle(emptyMap())
  var query by remember { mutableStateOf("") }
  var muscle by remember { mutableStateOf<MuscleGroup?>(null) }
  var equipment by remember { mutableStateOf<Equipment?>(null) }
  val selected = remember { mutableStateListOf<Long>() }
  var creating by remember { mutableStateOf(false) }

  val list =
    remember(all, usage, query, muscle, equipment) {
      all
        .filter { (muscle == null || it.primaryMuscle == muscle) && (equipment == null || it.equipment == equipment) && it.matches(query) }
        .sortedWith(compareByDescending<ExerciseEntity> { usage[it.id] ?: 0 }.thenBy { it.name.lowercase() })
    }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.safeDrawingPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          TextButton(onClick = { creating = true }) { Text("Create") }
        }
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search exercises") },
          leadingIcon = { Icon(Icons.Filled.Search, null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          EnumFilterChip("Muscle", muscle, MuscleGroup.entries, { it.label }) { muscle = it }
          EnumFilterChip("Equipment", equipment, Equipment.entries, { it.label }) { equipment = it }
        }
        LazyColumn(Modifier.weight(1f)) {
          items(list, key = { it.id }) { ex ->
            val isSelected = ex.id in selected
            Row(
              Modifier.fillMaxWidth()
                .clickable {
                  if (!multiSelect) onPicked(listOf(ex)) else if (isSelected) selected.remove(ex.id) else selected.add(ex.id)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                Text(
                  listOfNotNull(ex.primaryMuscle.label, ex.equipment.label.takeIf { ex.equipment != Equipment.NONE }, "custom".takeIf { ex.isCustom }).joinToString(" · "),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.accentText)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
        if (multiSelect) {
          Button(
            onClick = { onPicked(selected.mapNotNull { id -> all.firstOrNull { it.id == id } }) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
          ) {
            Text(if (selected.size <= 1) "Add exercise" else "Add ${selected.size} exercises")
          }
        }
      }
    }
  }

  if (creating) {
    ExerciseFormDialog(
      initial = null,
      onDismiss = { creating = false },
      onSave = { draft ->
        scope.launch {
          val id = container.exercises.create(draft)
          creating = false
          if (multiSelect) selected.add(id) else onPicked(listOf(draft.copy(id = id, isCustom = true)))
        }
      },
    )
  }
}

@Composable
fun <E> EnumFilterChip(label: String, value: E?, options: List<E>, name: (E) -> String, onChange: (E?) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    SelectChip(selected = value != null, onClick = { open = true }, label = value?.let(name) ?: label, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) })
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text("Any ${label.lowercase()}") },
        onClick = {
          onChange(null)
          open = false
        },
      )
      options.forEach { o ->
        DropdownMenuItem(
          text = { Text(name(o)) },
          onClick = {
            onChange(o)
            open = false
          },
        )
      }
    }
  }
}

/** Create/edit form shared by the picker and the exercise editor screen. */
@Composable
fun ExerciseForm(initial: ExerciseEntity?, onSave: (ExerciseEntity) -> Unit, modifier: Modifier = Modifier, saveLabel: String = "Save") {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var type by remember { mutableStateOf(initial?.type ?: ExerciseType.WEIGHT_REPS) }
  var equipment by remember { mutableStateOf(initial?.equipment ?: Equipment.BARBELL) }
  var primary by remember { mutableStateOf(initial?.primaryMuscle ?: MuscleGroup.CHEST) }
  val secondary = remember { mutableStateListOf<MuscleGroup>().apply { addAll(initial?.secondaryMuscles.orEmpty()) } }

  Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    LabeledDropdown("Exercise type", type, ExerciseType.entries, { it.label }) { type = it }
    LabeledDropdown("Equipment", equipment, Equipment.entries, { it.label }) { equipment = it }
    LabeledDropdown("Primary muscle", primary, MuscleGroup.entries, { it.label }) {
      primary = it
      secondary.remove(it)
    }
    Eyebrow("Other muscles", modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      MuscleGroup.entries.filter { it != primary }.forEach { m ->
        SelectChip(selected = m in secondary, onClick = { if (m in secondary) secondary.remove(m) else secondary.add(m) }, label = m.label)
      }
    }
    Button(
      enabled = name.isNotBlank(),
      onClick = {
        val base = initial ?: ExerciseEntity(name = "", type = type, equipment = equipment, primaryMuscle = primary, isCustom = true)
        onSave(base.copy(name = name.trim(), type = type, equipment = equipment, primaryMuscle = primary, secondaryMuscles = secondary.toList()))
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(saveLabel)
    }
  }
}

@Composable
fun ExerciseFormDialog(initial: ExerciseEntity?, onDismiss: () -> Unit, onSave: (ExerciseEntity) -> Unit) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.safeDrawingPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text(if (initial == null) "New exercise" else "Edit exercise", style = MaterialTheme.typography.titleLarge)
        }
        ExerciseForm(initial, onSave)
      }
    }
  }
}

@Composable
fun <E> LabeledDropdown(label: String, value: E, options: List<E>, name: (E) -> String, onChange: (E) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    OutlinedTextField(
      value = name(value),
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      modifier = Modifier.fillMaxWidth(),
      enabled = false,
      colors =
        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
          disabledTextColor = MaterialTheme.colorScheme.onSurface,
          disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          disabledBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
    Box(Modifier.matchParentSize().clickable { open = true })
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      options.forEach { o ->
        DropdownMenuItem(
          text = { Text(name(o)) },
          onClick = {
            onChange(o)
            open = false
          },
        )
      }
    }
  }
}
