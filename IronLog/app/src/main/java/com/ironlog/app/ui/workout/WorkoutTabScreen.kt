package com.ironlog.app.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.db.RoutineFolderEntity
import com.ironlog.app.data.db.RoutineWithExercises
import com.ironlog.app.domain.Units
import com.ironlog.app.theme.EyebrowStyle
import com.ironlog.app.theme.Mono
import com.ironlog.app.data.PlanOverview
import com.ironlog.app.data.db.PlanStatus
import com.ironlog.app.ui.LiveWorkout
import com.ironlog.app.ui.Navigator
import com.ironlog.app.ui.PlanCreate
import com.ironlog.app.ui.PlanDetail
import com.ironlog.app.ui.plan.PlanSummaryRow
import com.ironlog.app.ui.plan.rememberPlanSessionStarter
import com.ironlog.app.ui.RoutineEditor
import com.ironlog.app.ui.SettingsRoute
import com.ironlog.app.ui.components.AccentCard
import com.ironlog.app.ui.components.AppScaffold
import com.ironlog.app.ui.components.ConfirmDialog
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.Eyebrow
import com.ironlog.app.ui.components.IconDisc
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.Rule
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.SurfaceCard
import com.ironlog.app.ui.components.TagPill
import com.ironlog.app.ui.components.TextPromptDialog
import com.ironlog.app.ui.components.pagePadding
import com.ironlog.app.ui.components.reveal
import com.ironlog.app.ui.components.stagger
import com.ironlog.app.ui.rememberNow
import com.ironlog.app.workout.ActiveWorkout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Asks for POST_NOTIFICATIONS (Android 13+) the first time a workout starts; rest alerts need it. */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  return remember(context) {
    {
      if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}

@Composable
fun WorkoutTabScreen(nav: Navigator) {
  val c = LocalContainer.current
  val scope = rememberCoroutineScope()
  val folders by c.routines.folders.collectAsStateWithLifecycle(emptyList())
  val routines by c.routines.routines.collectAsStateWithLifecycle(emptyList())
  val active by c.activeWorkout.state.collectAsStateWithLifecycle()
  val activePlan by c.plans.active.collectAsStateWithLifecycle(null)
  val planOverview by remember(activePlan?.id) { activePlan?.let { c.plans.observeOverview(it.id) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)
  val allPlans by c.plans.plans.collectAsStateWithLifecycle(emptyList())
  val startPlanSession = rememberPlanSessionStarter(nav)
  val requestNotifications = rememberNotificationPermissionRequest()
  val collapsed = remember { mutableStateMapOf<Long, Boolean>() }

  var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
  var creatingFolder by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<RoutineFolderEntity?>(null) }
  var deletingFolder by remember { mutableStateOf<RoutineFolderEntity?>(null) }
  var deletingRoutine by remember { mutableStateOf<RoutineWithExercises?>(null) }
  var moving by remember { mutableStateOf<RoutineWithExercises?>(null) }

  fun start(begin: () -> Unit) {
    if (active != null) {
      pendingStart = begin
    } else {
      requestNotifications()
      begin()
      nav.go(LiveWorkout)
    }
  }

  @Composable
  fun Routine(r: RoutineWithExercises, index: Int) {
    RoutineCard(
      routine = r,
      onOpen = { nav.go(RoutineEditor(r.routine.id)) },
      onStart = { start { c.activeWorkout.startRoutine(r) } },
      onDuplicate = { scope.launch { c.routines.duplicate(r.routine.id) } },
      onMove = { moving = r },
      onDelete = { deletingRoutine = r },
      modifier = Modifier.padding(vertical = 6.dp).reveal(stagger(index + 1)),
    )
  }

  AppScaffold(title = "Workout", large = true, actions = { IconButton(onClick = { nav.go(SettingsRoute) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } }) { padding ->
    LazyColumn(contentPadding = pagePadding(padding)) {
      item { TodayLine(Modifier.padding(bottom = 12.dp)) }
      val nextSession = planOverview?.next
      if (active == null && planOverview != null && nextSession != null) {
        item { NextSessionCard(planOverview!!, onStart = { startPlanSession(nextSession.id) }, onOpen = { nav.go(PlanDetail(planOverview!!.plan.id)) }, modifier = Modifier.reveal()) }
        item {
          TextButton(onClick = { start { c.activeWorkout.startEmpty() } }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Or start an empty workout") }
        }
      } else {
        item {
          StartCard(
            active = active,
            onClick = { if (active != null) nav.go(LiveWorkout) else start { c.activeWorkout.startEmpty() } },
            modifier = Modifier.reveal(),
          )
        }
      }
      item {
        SectionHeader("Plan") {
          TextButton(onClick = { nav.go(PlanCreate) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("New plan")
          }
        }
      }
      val shown = allPlans.filter { it.status == PlanStatus.ACTIVE } + allPlans.filter { it.status != PlanStatus.ACTIVE }.take(2)
      if (shown.isEmpty()) {
        item {
          Text(
            "Plans run your routines (or a week IronLog builds for you) for 4–8 weeks: targets climb each week, volume adapts to how you respond, and the last week is a deload.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      items(shown.size) { i ->
        val plan = shown[i]
        val ov = if (plan.id == planOverview?.plan?.id) planOverview else null
        if (ov != null) {
          PlanSummaryRow(ov, onOpen = { nav.go(PlanDetail(plan.id)) })
        } else {
          SurfaceCard(onClick = { nav.go(PlanDetail(plan.id)) }) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
              Eyebrow(if (plan.status == PlanStatus.COMPLETE) "Completed" else if (plan.status == PlanStatus.ENDED) "Ended" else "Active")
              Text(plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
      item {
        SectionHeader("Routines") {
          Row {
            TextButton(onClick = { creatingFolder = true }) { Text("New folder") }
            TextButton(onClick = { nav.go(RoutineEditor()) }) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Text("Routine")
            }
          }
        }
      }
      if (routines.isEmpty() && folders.isEmpty()) {
        item {
          EmptyState(
            "No routines yet",
            "Save a plan you repeat — like Push Day — and start it with one tap. The values you log become next time's starting point.",
          ) {
            OutlinedButton(onClick = { nav.go(RoutineEditor()) }) { Text("Create a routine") }
          }
        }
      }
      folders.forEach { folder ->
        val inFolder = routines.filter { it.routine.folderId == folder.id }
        val isCollapsed = collapsed[folder.id] == true
        item(key = "folder-${folder.id}") {
          FolderHeader(
            folder = folder,
            count = inFolder.size,
            collapsed = isCollapsed,
            onToggle = { collapsed[folder.id] = !isCollapsed },
            onAddRoutine = { nav.go(RoutineEditor(folderId = folder.id)) },
            onRename = { renaming = folder },
            onDelete = { deletingFolder = folder },
          )
        }
        if (!isCollapsed) itemsIndexed(inFolder, key = { _, r -> "routine-${r.routine.id}" }) { i, r -> Routine(r, i) }
      }
      val loose = routines.filter { it.routine.folderId == null }
      if (loose.isNotEmpty() && folders.isNotEmpty()) item { SectionHeader("My routines") }
      itemsIndexed(loose, key = { _, r -> "routine-${r.routine.id}" }) { i, r -> Routine(r, i) }
    }
  }

  pendingStart?.let { begin ->
    AlertDialog(
      onDismissRequest = { pendingStart = null },
      title = { Text("Workout in progress") },
      text = { Text("Finish or discard your current workout before starting another — or discard it now.") },
      confirmButton = {
        TextButton(
          onClick = {
            pendingStart = null
            c.activeWorkout.discard()
            begin()
            nav.go(LiveWorkout)
          },
          colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Discard & start")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            pendingStart = null
            nav.go(LiveWorkout)
          }
        ) {
          Text("Resume current")
        }
      },
    )
  }
  if (creatingFolder) TextPromptDialog("New folder", "", "Folder name", "Create", onConfirm = { n -> scope.launch { c.routines.createFolder(n) } }, onDismiss = { creatingFolder = false })
  renaming?.let { f -> TextPromptDialog("Rename folder", f.name, "Folder name", "Save", onConfirm = { n -> scope.launch { c.routines.renameFolder(f, n) } }, onDismiss = { renaming = null }) }
  deletingFolder?.let { f ->
    ConfirmDialog("Delete \"${f.name}\"?", "Routines inside are kept and moved out of the folder.", "Delete", onConfirm = { scope.launch { c.routines.deleteFolder(f.id) } }, onDismiss = { deletingFolder = null }, destructive = true)
  }
  deletingRoutine?.let { r ->
    ConfirmDialog("Delete \"${r.routine.name}\"?", "Past workouts logged from it stay in your history.", "Delete", onConfirm = { scope.launch { c.routines.delete(r.routine.id) } }, onDismiss = { deletingRoutine = null }, destructive = true)
  }
  moving?.let { r ->
    AlertDialog(
      onDismissRequest = { moving = null },
      title = { Text("Move to folder") },
      text = {
        Column {
          (listOf<RoutineFolderEntity?>(null) + folders).forEach { f ->
            Text(
              f?.name ?: "No folder",
              fontWeight = if (f?.id == r.routine.folderId) FontWeight.Bold else FontWeight.Normal,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    moving = null
                    scope.launch { c.routines.move(r.routine.id, f?.id) }
                  }
                  .padding(vertical = 12.dp),
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun FolderHeader(
  folder: RoutineFolderEntity,
  count: Int,
  collapsed: Boolean,
  onToggle: () -> Unit,
  onAddRoutine: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(
      if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
      contentDescription = if (collapsed) "Expand" else "Collapse",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    TagPill("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.weight(1f))
    Box {
      IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Folder options") }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
          text = { Text("New routine here") },
          onClick = {
            menu = false
            onAddRoutine()
          },
        )
        DropdownMenuItem(
          text = { Text("Rename") },
          onClick = {
            menu = false
            onRename()
          },
        )
        DropdownMenuItem(
          text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) },
          onClick = {
            menu = false
            onDelete()
          },
        )
      }
    }
  }
}

@Composable
private fun RoutineCard(
  routine: RoutineWithExercises,
  onOpen: () -> Unit,
  onStart: () -> Unit,
  onDuplicate: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menu by remember { mutableStateOf(false) }
  val names = routine.orderedExercises.map { it.exercise.name }
  val summary = if (names.size <= 3) names.joinToString(", ") else names.take(3).joinToString(", ") + " +${names.size - 3} more"
  SurfaceCard(modifier, onClick = onOpen) {
    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row {
        Text(routine.routine.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Box {
          IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Routine options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf("Edit" to onOpen, "Duplicate" to onDuplicate, "Move to folder" to onMove).forEach { (label, action) ->
              DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                  menu = false
                  action()
                },
              )
            }
            DropdownMenuItem(
              text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
              onClick = {
                menu = false
                onDelete()
              },
            )
          }
        }
      }
      Text(summary.ifEmpty { "No exercises yet" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 12.dp))
      Rule(Modifier.padding(end = 12.dp, top = 4.dp, bottom = 2.dp))
      Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("${names.size} exercise${if (names.size == 1) "" else "s"}", modifier = Modifier.weight(1f))
        Button(onClick = onStart, enabled = names.isNotEmpty()) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Start")
        }
      }
    }
  }
}

/** The plan's next session as the screen's ember card. */
@Composable
private fun NextSessionCard(ov: PlanOverview, onStart: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val next = ov.next ?: return
  val day = ov.day(next.planDayId)
  val sets = day?.orderedSlots?.sumOf { ov.plannedSets(next, it.slot) ?: it.slot.startSets } ?: 0
  AccentCard(onClick = onStart, modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconDisc(Icons.Filled.PlayArrow)
      Spacer(Modifier.weight(1f))
      TagPill(if (ov.isDeload(next.week)) "Deload week" else "Week ${next.week + 1} · ${ov.rir(next.week)} RIR")
    }
    Spacer(Modifier.height(36.dp))
    val title = buildAnnotatedString {
      append("Next up\n")
      withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(day?.day?.name ?: "Session") }
    }
    Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(10.dp))
    Text(
      "${day?.slots?.size ?: 0} exercises · $sets sets · ${ov.plan.name}",
      style = MaterialTheme.typography.bodyLarge,
      color = LocalContentColor.current.copy(alpha = 0.72f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(20.dp))
    Rule()
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Eyebrow("Tap to start", color = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
      TextButton(onClick = onOpen, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) { Text("View plan") }
    }
  }
}

/** Mono date and time line, the page's quiet timestamp. */
@Composable
private fun TodayLine(modifier: Modifier = Modifier) {
  val now = rememberNow(periodMillis = 60_000)
  val at = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()) }
  val style = EyebrowStyle.copy(fontFamily = Mono)
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Eyebrow(at.format(todayFmt), style = style)
    Box(Modifier.width(1.dp).height(12.dp).background(MaterialTheme.colorScheme.outline))
    Eyebrow(at.format(timeFmt), style = style)
  }
}

private val todayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** The ember hero: start an empty workout, or jump back into the one running. */
@Composable
private fun StartCard(active: ActiveWorkout?, onClick: () -> Unit, modifier: Modifier = Modifier) {
  AccentCard(onClick = onClick, modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconDisc(Icons.Filled.PlayArrow)
      Spacer(Modifier.weight(1f))
      if (active != null) {
        val now = rememberNow() // ticks only while a workout is running
        TagPill("Live · ${Units.clock((now - active.startTime) / 1000)}")
      } else {
        TagPill("Quick start")
      }
    }
    Spacer(Modifier.height(36.dp))
    val title = buildAnnotatedString {
      append(if (active != null) "Resume\n" else "Start an\n")
      withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(active?.name ?: "empty workout") }
    }
    Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(10.dp))
    Text(
      if (active != null) "Your sets are waiting where you left them." else "Log as you go and add exercises when you get to them.",
      style = MaterialTheme.typography.bodyLarge,
      color = LocalContentColor.current.copy(alpha = 0.72f),
    )
    Spacer(Modifier.height(20.dp))
    Rule()
  }
}
