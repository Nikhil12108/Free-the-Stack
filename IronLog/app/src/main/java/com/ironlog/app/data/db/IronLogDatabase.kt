package com.ironlog.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities =
    [
      ExerciseEntity::class,
      RoutineFolderEntity::class,
      RoutineEntity::class,
      RoutineExerciseEntity::class,
      RoutineSetEntity::class,
      WorkoutEntity::class,
      WorkoutExerciseEntity::class,
      WorkoutSetEntity::class,
      BodyMeasurementEntity::class,
      PlanEntity::class,
      PlanDayEntity::class,
      PlanSlotEntity::class,
      PlanPriorityEntity::class,
      PlanSessionEntity::class,
      PlanSessionSlotEntity::class,
      MuscleFeedbackEntity::class,
    ],
  version = 3,
  exportSchema = true,
  // v2: reps in reserve, set targets and joint pain on logged work; per-exercise progression in
  // routines; training plans. Only new tables and nullable columns, so Room migrates in place.
  autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class IronLogDatabase : RoomDatabase() {
  abstract fun exercises(): ExerciseDao

  abstract fun routines(): RoutineDao

  abstract fun workouts(): WorkoutDao

  abstract fun measurements(): MeasurementDao

  abstract fun plans(): PlanDao

  companion object {
    fun create(context: Context): IronLogDatabase =
      Room.databaseBuilder(context, IronLogDatabase::class.java, "ironlog.db").build()
  }
}
