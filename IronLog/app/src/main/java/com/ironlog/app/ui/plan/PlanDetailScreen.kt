package com.ironlog.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.PlanOverview
import com.ironlog.app.data.PreparedSession
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.data.db.PlanSessionEntity
import com.ironlog.app.data.db.PlanStatus
import com.ironlog.app.data.db.SessionStatus
import com.ironlog.app.progression.Priority
import com.ironlog.app.ui.LiveWorkout
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.Rule
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.SelectChip
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.TagPill
import com.ironlog.app.ui.components.TextPromptDialog
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import com.ironlog.app.ui.workout.rememberNotificationPermissionRequest
import kotlinx.coroutines.launch

/** Prepares a planned session and opens it, asking first if another workout is running. */
@Composable
fun rememberPlanSessionStarter(nav: Navigator): (Long) -> Unit {
  val c = LocalContainer.current
  val scope = rememberCoroutineScope()
  val requestNotifications = rememberNotificationPermissionRequest()
  var pending by remember { mutableStateOf<PreparedSession?>(null) }
  fun begin(p: PreparedSession) {
    requestNotifications()
    c.activeWorkout.startPlanned(p.name, p.link, p.exercises)
    nav.go(LiveWorkout)
  }
  pending?.let { p ->
    AlertDialog(
      onDismissRequest = { pending = null },
      title = { Text("Workout in progress") },
      text = { Text("Finish or discard your current workout before starting this session — or discard it now.") },
      confirmButton = {
        TextButton(
          onClick = {
            pending = null
            c.activeWorkout.discard()
            begin(p)
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Discard & start")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            pending = null
            nav.go(LiveWorkout)
          }
        ) {
          Text("Resume current")
        }
      },
    )
  }
  return remember(c) {
    { sessionId ->
      scope.launch {
        val p = c.plans.prepare(sessionId) ?: return@launch
        if (c.activeWorkout.isActive) pending = p else begin(p)
      }
    }
  }
}

@Composable
fun PlanDetailScreen(nav: Navigator, planId: Long) {
  val c = LocalContainer.current
  val scope = rememberCoroutineScope()
  val overview by remember(planId) { c.plans.observeOverview(planId) }.collectAsStateWithLifecycle(null)
  val start = rememberPlanSessionStarter(nav)
  val ov = overview
  var selectedWeek by rememberSaveable(planId) { mutableStateOf<Int?>(null) }
  var menu by remember { mutableStateOf(false) }
  var editingPriorities by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var ending by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf(false) }
  var skipping by remember { mutableStateOf<PlanSessionEntity?>(null) }

  AppScaffold(
    title = ov?.plan?.name ?: "Plan",
    onBack = { nav.back() },
    actions = {
      if (ov != null) {
        Box {
          IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plan options") }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val close = { menu = false }
            MenuItem("Muscle priorities", close) { editingPriorities = true }
            MenuItem("Rename", close) { renaming = true }
            if (ov.isActive) {
              MenuItem("Add a week", close, enabled = c.plans.canAddWeek(ov)) { scope.launch { c.plans.addWeek(planId) } }
              MenuItem("Remove a week", close, enabled = c.plans.canRemoveWeek(ov)) { scope.launch { c.plans.removeWeek(planId) } }
              MenuItem("End plan", close, destructive = true) { ending = true }
            }
            MenuItem("Delete plan", close, destructive = true) { deleting = true }
          }
        }
      }
    },
  ) { padding ->
    if (ov == null) return@AppScaffold
    val week = (selectedWeek ?: ov.currentWeek).coerceIn(0, ov.plan.weeks - 1)
    LazyColumn(contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item {
        Column(Modifier.reveal()) {
          Eyebrow(
            when (ov.plan.status) {
              PlanStatus.ACTIVE -> "Active plan · ${ov.plan.feedbackMode.label} questions"
              PlanStatus.COMPLETE -> "Completed"
              PlanStatus.ENDED -> "Ended early"
            }
          )
          Text(weekLabel(ov, week), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
          WeekStrip(
            weeks = ov.plan.weeks,
            schedule = ov.schedule,
            current = ov.currentWeek,
            selected = week,
            done = { w -> ov.week(w).all { it.status != SessionStatus.PENDING } },
            onSelect = { selectedWeek = it },
          )
        }
      }
      items(ov.week(week), key = { it.id }) { s ->
        SessionCard(ov, s, onStart = { start(s.id) }, onSkip = { skipping = s })
      }
      item { VolumeTable(ov) }
    }
  }

  if (ov != null && editingPriorities) PrioritiesDialog(ov, onSet = { m, p -> scope.launch { c.plans.setPriority(planId, m, p) } }, onMode = { m -> scope.launch { c.plans.setFeedbackMode(planId, m) } }, onDismiss = { editingPriorities = false })
  if (ov != null && renaming) TextPromptDialog("Rename plan", ov.plan.name, "Plan name", "Save", onConfirm = { n -> scope.launch { c.plans.rename(planId, n) } }, onDismiss = { renaming = false })
  if (ending) ConfirmDialog("End this plan?", "Sessions you haven't done are marked skipped. Your logged workouts stay in History.", "End plan", onConfirm = { scope.launch { c.plans.endPlan(planId) } }, onDismiss = { ending = false }, destructive = true)
  if (deleting) {
    ConfirmDialog(
      "Delete this plan?",
      "The plan and its schedule are removed. Workouts you logged with it stay in History.",
      "Delete",
      onConfirm = {
        scope.launch {
          c.plans.delete(planId)
          nav.back()
        }
      },
      onDismiss = { deleting = false },
      destructive = true,
    )
  }
  skipping?.let { s ->
    ConfirmDialog("Skip this session?", "It's marked skipped and next week's sets for this day stay the same.", "Skip", onConfirm = { scope.launch { c.plans.skip(s.id) } }, onDismiss = { skipping = null })
  }
}

@Composable
private fun MenuItem(label: String, close: () -> Unit, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
  DropdownMenuItem(
    text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
    enabled = enabled,
    onClick = {
      close()
      onClick()
    },
  )
}

@Composable
private fun SessionCard(ov: PlanOverview, s: PlanSessionEntity, onStart: () -> Unit, onSkip: () -> Unit) {
  val day = ov.day(s.planDayId) ?: return
  val isNext = s.id == ov.next?.id
  val canStart = ov.isActive && s.status == SessionStatus.PENDING && s.week <= ov.currentWeek
  SurfaceCard(Modifier.reveal()) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(day.day.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TagPill(statusLabel(s, ov.next), color = if (isNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
      }
      day.orderedSlots.forEach { sw ->
        val sets = ov.plannedSets(s, sw.slot)
        if (sets == 0) return@forEach
        Row {
          Text(sw.exercise.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          Text(
            when {
              sets == null -> "${sw.slot.repMin}–${sw.slot.repMax}"
              else -> "$sets × ${sw.slot.repMin}–${sw.slot.repMax}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (ov.isDeload(s.week)) Text("Traps and forearms rest this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (canStart) {
        Rule(Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = onSkip) { Text("Skip") }
          Spacer(Modifier.weight(1f))
          Button(onClick = onStart) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Start")
          }
        }
      }
    }
  }
}

/** Weekly sets per muscle across the block — how the volume has moved so far. */
@Composable
private fun VolumeTable(ov: PlanOverview) {
  if (ov.muscles.isEmpty()) return
  Column {
    SectionHeader("Sets per week")
    Row {
      Spacer(Modifier.weight(1.6f))
      for (w in 0 until ov.plan.weeks) Eyebrow(if (ov.isDeload(w)) "DL" else "W${w + 1}", modifier = Modifier.weight(1f))
    }
    val weekly = (0 until ov.plan.weeks).map { ov.weeklySets(it) }
    ov.muscles.forEach { m ->
      Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.6f)) {
          Text(m.label, style = MaterialTheme.typography.bodyMedium)
          Eyebrow((ov.priorities[m] ?: Priority.GROW).label)
        }
        for (w in 0 until ov.plan.weeks) {
          val v = weekly[w][m]
          Text(v?.toString() ?: "·", style = MaterialTheme.typography.bodyMedium, color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun PrioritiesDialog(ov: PlanOverview, onSet: (com.ironlog.app.progression.TrainingMuscle, Priority) -> Unit, onMode: (FeedbackMode) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Muscle priorities") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Changes apply from the next session you finish.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ov.muscles.forEach { m ->
          Column {
            Text(m.label, style = MaterialTheme.typography.bodyLarge)
            PriorityChips(ov.priorities[m] ?: Priority.GROW, onSelect = { onSet(m, it) })
          }
        }
        Eyebrow("Questions after sets")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FeedbackMode.entries.forEach { mode -> SelectChip(selected = ov.plan.feedbackMode == mode, onClick = { onMode(mode) }, label = mode.label) }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

/** Card for the Workout tab: the plan's next session, or a nudge when the plan has finished. */
@Composable
fun PlanSummaryRow(ov: PlanOverview, onOpen: () -> Unit) {
  SurfaceCard(onClick = onOpen) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Eyebrow(if (ov.isActive) weekLabel(ov, ov.currentWeek).substringBefore(" ·") else "Plan finished")
      Text(ov.plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      ov.next?.let { n -> Text("Next: ${ov.day(n.planDayId)?.day?.name ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
  }
}
