package com.ironlog.app.ui

import android.Manifest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ironlog.app.IronLogApp
import com.ironlog.app.MainActivity
import com.ironlog.app.data.db.FeedbackMode
import com.ironlog.app.data.db.SessionStatus
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.ProgressionRule
import com.ironlog.app.progression.TrainingMuscle
import com.ironlog.app.workout.SessionExercise
import com.ironlog.app.workout.SessionSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke tests for the plan screens on the JVM: the real activity, container, theme and navigation, driven
 * by taps. They catch composition-time crashes and broken wiring; they say nothing about how it looks.
 * The window is very tall so lazy lists compose everything without scrolling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h3200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanScreensTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val app: IronLogApp = ApplicationProvider.getApplicationContext()
  private val c
    get() = app.container

  @Before
  fun setUp() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    // The container seeds the exercise library in the background when the app starts.
    val deadline = System.currentTimeMillis() + 20_000
    while (runBlocking { c.db.exercises().observeAll().first() }.isEmpty()) {
      check(System.currentTimeMillis() < deadline) { "exercise library was never seeded" }
      Thread.sleep(50)
    }
  }

  private fun routine(name: String, vararg exercises: String): Long = runBlocking {
    val all = c.db.exercises().observeAll().first()
    val exs = exercises.map { n -> SessionExercise.from(all.first { it.name == n }, 90).copy(sets = List(3) { SessionSet(repMin = 8, repMax = 12) }) }
    c.routines.save(null, name, null, "", exs)
  }

  // Case-insensitive because eyebrow labels are shown in capitals.
  private fun text(text: String, substring: Boolean) = hasText(text, substring = substring, ignoreCase = true)

  private fun waitFor(matcher: SemanticsMatcher, what: String, timeout: Long = 15_000) {
    try {
      compose.waitUntil(timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
      throw AssertionError("$what never appeared", e)
    }
  }

  private fun waitForText(text: String, substring: Boolean = false) = waitFor(text(text, substring), "\"$text\"")

  /** Taps the [index]th clickable node matching [matcher], so titles that repeat a button's label are skipped. */
  private fun tap(matcher: SemanticsMatcher, what: String, index: Int = 0) {
    val clickable = matcher and hasClickAction()
    waitFor(clickable, "clickable $what")
    compose.onAllNodes(clickable)[index].apply {
      // Brings nodes inside a long single list item into the window; nodes outside a scroller stay put.
      runCatching { performScrollTo() }
      performClick()
    }
  }

  private fun tapText(text: String, substring: Boolean = false, index: Int = 0) = tap(text(text, substring), "\"$text\"", index)

  private fun tapDescription(description: String, index: Int = 0) = tap(hasContentDescription(description), "node described \"$description\"", index)

  private fun activePlanId(): Long = runBlocking { c.plans.active.first() }?.id ?: throw AssertionError("no active plan")

  @Test
  fun planFromRoutines_trainAnswerFinishAndStartTheNextDay() {
    routine("Day A", "Barbell Bench Press", "Dumbbell Lateral Raise")
    routine("Day B", "Dumbbell Bench Press", "Barbell Bent-Over Row")
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      // Workout tab → new plan from both routines, full questions.
      tapText("New plan")
      waitForText("From my routines")
      tapText("Day A")
      tapText("Day B")
      tapText("Full")
      waitForText("Muscle priorities")
      tapText("Emphasize")
      tapText("Create")

      // Plan screen.
      waitForText("Active plan · Full questions", substring = true)
      waitForText("Sets per week")
      tapDescription("Plan options")
      tapText("Muscle priorities")
      waitForText("Changes apply from the next session you finish.")
      tapText("Done")
      val planId = activePlanId()
      val overview = runBlocking { c.plans.overview(planId) }!!
      assertEquals(FeedbackMode.FULL, overview.plan.feedbackMode)
      assertEquals(2, overview.days.size)

      // Start day A: live workout with the plan banner and week-1 effort.
      tapText("Start")
      waitForText("Finish")
      waitForText("good reps left in the tank", substring = true)
      val workout = c.activeWorkout.state.value
      assertNotNull(workout?.plan)
      assertTrue("targets are worked out at start", workout!!.exercises.all { it.targetsReady })

      // What the Progression dialog's Apply does: the bench's targets are worked out again in plan mode. (The
      // dialog has its own test; text-field dialogs never settle over the full activity under Robolectric.)
      val bench = workout.exercises.first { it.name == "Barbell Bench Press" }
      c.activeWorkout.updateExercises { list -> list.map { if (it.key == bench.key) it.copy(rule = ProgressionRule.DOUBLE, targetsReady = false) else it } }
      compose.waitUntil(15_000) { c.activeWorkout.state.value!!.exercises.first { it.key == bench.key }.targetsReady }

      // Log the bench: a weight is needed in week 1 (no history), then tick every set in the UI.
      c.activeWorkout.updateExercises { list ->
        list.map { ex -> if (ex.name == "Barbell Bench Press") ex.copy(sets = ex.sets.map { s -> if (s.type == SetType.WARMUP) s else s.copy(weightKg = 60.0) }) else ex }
      }
      repeat(3) { tapDescription("Complete set") }
      // Joint check and the chest questions appear once all chest work is done; leave the chest ones.
      waitForText("Joints")
      waitForText("Chest · how did it go?")
      tapText("Fine")

      // Finish: the plan card, and a quick check for chest that stays while both questions are answered.
      tapText("Finish")
      waitForText("Save workout")
      waitForText("Plan · week 1 of", substring = true)
      waitForText("Quick check")
      tapText("Easy")
      tapText("Great")
      val answers = c.activeWorkout.state.value!!.feedback.single { it.muscle == TrainingMuscle.CHEST }
      assertEquals(0, answers.workload)
      assertEquals(2, answers.pump)
      tapText("Save workout")
      waitForText("Day A")
      compose.waitUntil(15_000) { c.activeWorkout.state.value == null }
      val after = runBlocking { c.plans.overview(planId) }!!
      val dayA = after.sessions.first { it.week == 0 && after.day(it.planDayId)?.day?.name == "Day A" }
      assertEquals(SessionStatus.PARTIAL, dayA.status) // the laterals were not done
      assertEquals("Day B", after.day(after.next!!.planDayId)?.day?.name)

      // Back to History, then the Workout tab: the next-session card starts day B with the recovery check for chest.
      scenario.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
      tapText("WORKOUT")
      waitForText("Next up", substring = true)
      tapText("Next up", substring = true)
      waitForText("Recovery check")
      tapText("Still sore")
      assertEquals(4, c.activeWorkout.state.value!!.feedback.single { it.muscle == TrainingMuscle.CHEST }.soreness)

      tapText("Discard workout")
      tapText("Discard")
      compose.waitUntil(15_000) { c.activeWorkout.state.value == null }
    }
  }

  @Test
  fun builderLaysOutAWeekAndCreatesThePlan() {
    ActivityScenario.launch(MainActivity::class.java).use {
      tapText("New plan")
      tapText("Build it for me")
      tapText("3 days")
      tapText("Lay out my week")
      waitForText("Your week")
      tapText("Swap")
      tapText("week plan", substring = true)
      waitForText("Sets per week")
      val ov = runBlocking { c.plans.overview(activePlanId()) }!!
      assertEquals(3, ov.days.size)
      assertTrue(ov.days.all { it.slots.isNotEmpty() })
      waitForText("Start")
    }
  }

  @Test
  fun settingsShowProgressionAndEquipment() {
    ActivityScenario.launch(MainActivity::class.java).use {
      tapDescription("Settings")
      waitForText("Progression")
      waitForText("Equipment")
      val before = c.settings.current.smartWarmups
      tapText("Smart warm-ups")
      compose.waitUntil(5_000) { c.settings.current.smartWarmups != before }
    }
  }
}
