package com.ironlog.app

import android.app.Application
import android.content.Context
import com.ironlog.app.data.ExerciseRepository
import com.ironlog.app.data.MeasurementRepository
import com.ironlog.app.data.PlanRepository
import com.ironlog.app.data.RoutineRepository
import com.ironlog.app.data.SettingsStore
import com.ironlog.app.data.WorkoutRepository
import com.ironlog.app.data.db.IronLogDatabase
import com.ironlog.app.workout.ActiveWorkoutManager
import com.ironlog.app.workout.Notifications
import com.ironlog.app.workout.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Manual dependency graph; one instance per process, owned by [IronLogApp]. */
class AppContainer(context: Context) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val db = IronLogDatabase.create(context)
  val settings = SettingsStore(context)
  val exercises = ExerciseRepository(db)
  val routines = RoutineRepository(db)
  val workouts = WorkoutRepository(db)
  val measurements = MeasurementRepository(db)
  val restTimer = RestTimer(context, scope)
  val activeWorkout = ActiveWorkoutManager(context, scope, workouts, routines, settings, restTimer, measurements)
  val plans = PlanRepository(db, workouts, routines, settings, measurements)

  /** Set when the app is opened from a rest-timer notification; the nav host consumes it. */
  val openWorkoutRequest = MutableStateFlow(false)

  init {
    scope.launch { exercises.seedIfEmpty() }
    activeWorkout.onPlanSessionSaved = { workout, id -> plans.completeSession(workout, id) }
  }
}

class IronLogApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    Notifications.createChannels(this)
    container = AppContainer(this)
  }
}
