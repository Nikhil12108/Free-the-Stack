package com.ironlog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ironlog.app.domain.Units
import com.ironlog.app.theme.EyebrowStyle
import com.ironlog.app.theme.Mono
import com.ironlog.app.theme.accent
import com.ironlog.app.theme.onAccent
import com.ironlog.app.ui.components.IconDisc
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.TagPill
import com.ironlog.app.ui.exercises.ExerciseDetailScreen
import com.ironlog.app.ui.exercises.ExerciseEditorScreen
import com.ironlog.app.ui.exercises.ExercisesTabScreen
import com.ironlog.app.ui.history.HistoryScreen
import com.ironlog.app.ui.plan.PlanCreateScreen
import com.ironlog.app.ui.plan.PlanDetailScreen
import com.ironlog.app.ui.history.WorkoutDetailScreen
import com.ironlog.app.ui.profile.MeasurementsScreen
import com.ironlog.app.ui.profile.ProfileScreen
import com.ironlog.app.ui.profile.SettingsScreen
import com.ironlog.app.ui.workout.FinishWorkoutScreen
import com.ironlog.app.ui.workout.LiveWorkoutScreen
import com.ironlog.app.ui.workout.RoutineEditorScreen
import com.ironlog.app.ui.workout.WorkoutTabScreen
import com.ironlog.app.workout.ActiveWorkout
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable data object WorkoutTab : NavKey

@Serializable data object HistoryTab : NavKey

@Serializable data object ExercisesTab : NavKey

@Serializable data object ProfileTab : NavKey

@Serializable data object LiveWorkout : NavKey

@Serializable data object FinishWorkout : NavKey

@Serializable data class RoutineEditor(val routineId: Long? = null, val folderId: Long? = null) : NavKey

@Serializable data class WorkoutDetail(val workoutId: Long) : NavKey

@Serializable data class ExerciseDetail(val exerciseId: Long) : NavKey

@Serializable data class ExerciseEditor(val exerciseId: Long? = null) : NavKey

@Serializable data object Measurements : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data object PlanCreate : NavKey

@Serializable data class PlanDetail(val planId: Long) : NavKey

private data class Tab(val key: NavKey, val label: String, val icon: ImageVector)

private val tabs =
  listOf(
    Tab(WorkoutTab, "Workout", Icons.Filled.PlayArrow),
    Tab(HistoryTab, "History", Icons.Filled.DateRange),
    Tab(ExercisesTab, "Exercises", Icons.AutoMirrored.Filled.List),
    Tab(ProfileTab, "Profile", Icons.Filled.Person),
  )

/** Thin wrapper over the back stack so screens don't manipulate it directly. */
class Navigator(private val backStack: MutableList<NavKey>) {
  fun go(key: NavKey) {
    backStack.add(key)
  }

  /** Never pops the last entry — an empty back stack crashes NavDisplay. */
  fun back() {
    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
  }

  fun switchTab(tab: NavKey) {
    if (backStack.size == 1 && backStack[0] == tab) return
    backStack.add(tab)
    while (backStack.size > 1) backStack.removeAt(0)
  }

  /** Replaces the whole stack, e.g. after finishing a workout. */
  fun reset(vararg keys: NavKey) {
    backStack.addAll(keys)
    repeat(backStack.size - keys.size) { backStack.removeAt(0) }
  }
}

@Composable
fun MainNavigation() {
  val container = LocalContainer.current
  val backStack = rememberNavBackStack(WorkoutTab)
  val nav = remember(backStack) { Navigator(backStack) }
  val active by container.activeWorkout.state.collectAsStateWithLifecycle()
  val openRequest by container.openWorkoutRequest.collectAsStateWithLifecycle()

  LaunchedEffect(openRequest) {
    if (openRequest) {
      container.openWorkoutRequest.value = false
      if (container.activeWorkout.isActive && backStack.lastOrNull() != LiveWorkout) nav.go(LiveWorkout)
    }
  }

  val top = backStack.lastOrNull()
  val onTab = tabs.any { it.key == top }

  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars,
    bottomBar = {
      if (onTab) {
        Column {
          active?.let { WorkoutInProgressBar(it, onClick = { nav.go(LiveWorkout) }) }
          val colors = MaterialTheme.colorScheme
          NavigationBar(
            containerColor = colors.background,
            tonalElevation = 0.dp,
            modifier =
              Modifier.drawWithContent {
                drawContent()
                drawLine(colors.outlineVariant, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
              },
          ) {
            tabs.forEach { tab ->
              NavigationBarItem(
                selected = top == tab.key,
                onClick = { nav.switchTab(tab.key) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label.uppercase(), style = EyebrowStyle.copy(fontSize = 10.sp)) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onSurface,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.secondaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                  ),
              )
            }
          }
        }
      }
    },
  ) { padding ->
    NavDisplay(
      backStack = backStack,
      onBack = { nav.back() },
      modifier = Modifier.padding(padding).consumeWindowInsets(padding),
      entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
      entryProvider =
        entryProvider {
          entry<WorkoutTab> { WorkoutTabScreen(nav) }
          entry<HistoryTab> { HistoryScreen(nav) }
          entry<ExercisesTab> { ExercisesTabScreen(nav) }
          entry<ProfileTab> { ProfileScreen(nav) }
          entry<LiveWorkout> { LiveWorkoutScreen(nav) }
          entry<FinishWorkout> { FinishWorkoutScreen(nav) }
          entry<RoutineEditor> { key -> RoutineEditorScreen(nav, key.routineId, key.folderId) }
          entry<WorkoutDetail> { key -> WorkoutDetailScreen(nav, key.workoutId) }
          entry<ExerciseDetail> { key -> ExerciseDetailScreen(nav, key.exerciseId) }
          entry<ExerciseEditor> { key -> ExerciseEditorScreen(nav, key.exerciseId) }
          entry<Measurements> { MeasurementsScreen(nav) }
          entry<SettingsRoute> { SettingsScreen(nav) }
          entry<PlanCreate> { PlanCreateScreen(nav) }
          entry<PlanDetail> { key -> PlanDetailScreen(nav, key.planId) }
        },
    )
  }
}

/** The live workout, pinned above the tabs as an ember card. */
@Composable
private fun WorkoutInProgressBar(workout: ActiveWorkout, onClick: () -> Unit) {
  val now = rememberNow()
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    color = colors.accent,
    contentColor = colors.onAccent,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      IconDisc(Icons.Filled.PlayArrow, size = 40.dp)
      Column(Modifier.weight(1f)) {
        Text(workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("In progress · ${Units.clock((now - workout.startTime) / 1000)}".uppercase(), style = EyebrowStyle.copy(fontFamily = Mono), color = LocalContentColor.current.copy(alpha = 0.7f))
      }
      TagPill("Resume")
    }
  }
}

/** Wall-clock time that recomposes every [periodMillis]. */
@Composable
fun rememberNow(periodMillis: Long = 1000): Long {
  val state = remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(periodMillis) {
    while (true) {
      state.longValue = System.currentTimeMillis()
      delay(periodMillis - System.currentTimeMillis() % periodMillis)
    }
  }
  return state.longValue
}
