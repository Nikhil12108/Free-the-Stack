package com.ironlog.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ironlog.app.data.Settings
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.progression.ProgressionRule
import com.ironlog.app.theme.IronLogTheme
import com.ironlog.app.ui.workout.ProgressionDialog
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The per-exercise progression dialog, on its own (see PlanScreensTest for why not inside the app). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressionDialogTest {
  @get:Rule val compose = createComposeRule()

  private val bench =
    SessionExercise(exerciseId = 1, name = "Barbell Bench Press", type = ExerciseType.WEIGHT_REPS, equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.CHEST, sets = List(3) { SessionSet(repMin = 8, repMax = 12) })

  private var applied: List<Any?>? = null
  private var dismissed = false

  private fun show() =
    compose.setContent {
      IronLogTheme { ProgressionDialog(bench, Settings(), onApply = { rule, min, max, rir -> applied = listOf(rule, min, max, rir) }, onDismiss = { dismissed = true }) }
    }

  @Test
  fun choosingARuleAndEffortAppliesThem() {
    show()
    compose.onNodeWithText(ProgressionRule.DOUBLE.label).performClick()
    compose.onNodeWithText(ProgressionRule.DOUBLE.description).assertExists()
    compose.onNodeWithText("1").performScrollTo().performClick()
    compose.onNodeWithText("Apply").performClick()
    assertEquals(listOf(ProgressionRule.DOUBLE, 8, 12, 1), applied)
    assertTrue(dismissed)
  }

  @Test
  fun smartAndDefaultEffortAreStoredAsNoOverride() {
    show()
    compose.onNodeWithText("Apply").performClick()
    assertEquals(listOf(null, 8, 12, null), applied)
  }
}
