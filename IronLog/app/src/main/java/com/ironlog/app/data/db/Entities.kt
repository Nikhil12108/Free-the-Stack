package com.ironlog.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.ironlog.app.model.Equipment
import com.ironlog.app.model.ExerciseType
import com.ironlog.app.model.MuscleGroup
import com.ironlog.app.model.SetType
import com.ironlog.app.progression.ProgressionRule

// Canonical units everywhere in storage: kilograms, meters, seconds, epoch millis.

@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val type: ExerciseType,
  val equipment: Equipment,
  val primaryMuscle: MuscleGroup,
  val secondaryMuscles: List<MuscleGroup> = emptyList(),
  val isCustom: Boolean = false,
)

@Entity(tableName = "routine_folder")
data class RoutineFolderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val position: Int,
)

@Entity(
  tableName = "routine",
  foreignKeys = [ForeignKey(entity = RoutineFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)],
  indices = [Index("folderId")],
)
data class RoutineEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val folderId: Long?,
  val notes: String = "",
  val position: Int = 0,
  val createdAt: Long,
  val updatedAt: Long,
)

@Entity(
  tableName = "routine_exercise",
  foreignKeys =
    [
      ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
      ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
  indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val routineId: Long,
  val exerciseId: Long,
  val position: Int,
  val supersetGroup: Int? = null,
  val restSeconds: Int? = null,
  val notes: String = "",
  /** Null means the app default (Smart). */
  val progressionRule: ProgressionRule? = null,
  /** Reps in reserve this exercise aims for; null means the app default. */
  val targetRir: Int? = null,
)

@Entity(
  tableName = "routine_set",
  foreignKeys = [ForeignKey(entity = RoutineExerciseEntity::class, parentColumns = ["id"], childColumns = ["routineExerciseId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("routineExerciseId")],
)
data class RoutineSetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val routineExerciseId: Long,
  val position: Int,
  val type: SetType = SetType.NORMAL,
  val weightKg: Double? = null,
  val reps: Int? = null,
  val repMin: Int? = null,
  val repMax: Int? = null,
  val durationSec: Int? = null,
  val distanceM: Double? = null,
)

@Entity(tableName = "workout", indices = [Index("startTime")])
data class WorkoutEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val notes: String = "",
  val routineId: Long? = null,
  val startTime: Long,
  val endTime: Long,
  /** Set when the workout was a planned session (no foreign key: deleting a plan keeps history). */
  val planSessionId: Long? = null,
)

@Entity(
  tableName = "workout_exercise",
  foreignKeys =
    [
      ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
      ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
  indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutId: Long,
  val exerciseId: Long,
  val position: Int,
  val supersetGroup: Int? = null,
  val notes: String = "",
  /** 0 none … 3 a lot, rated after the exercise; null when not asked. */
  val jointPain: Int? = null,
)

/** workoutId / exerciseId are denormalised so per-exercise history is a single indexed query. */
@Entity(
  tableName = "workout_set",
  foreignKeys = [ForeignKey(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("workoutExerciseId"), Index("workoutId"), Index("exerciseId")],
)
data class WorkoutSetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutExerciseId: Long,
  val workoutId: Long,
  val exerciseId: Long,
  val position: Int,
  val type: SetType = SetType.NORMAL,
  val weightKg: Double? = null,
  val reps: Int? = null,
  val durationSec: Int? = null,
  val distanceM: Double? = null,
  val rpe: Double? = null,
  /** Reps left in the tank (replaces RPE for new sets). */
  val rir: Int? = null,
  /** What the set was aiming for, so later sessions can tell a hit from a miss. */
  val targetWeightKg: Double? = null,
  val targetReps: Int? = null,
  val targetRir: Int? = null,
  /** True for a deliberately easy set (deload week, recovery session): never a baseline for progression. */
  val light: Boolean? = null,
)

/** One row per calendar day; [date] is ISO yyyy-MM-dd. */
@Entity(tableName = "body_measurement")
data class BodyMeasurementEntity(
  @PrimaryKey val date: String,
  val weightKg: Double? = null,
  val bodyFatPct: Double? = null,
  val neckCm: Double? = null,
  val shouldersCm: Double? = null,
  val chestCm: Double? = null,
  val leftBicepCm: Double? = null,
  val rightBicepCm: Double? = null,
  val leftForearmCm: Double? = null,
  val rightForearmCm: Double? = null,
  val waistCm: Double? = null,
  val hipsCm: Double? = null,
  val leftThighCm: Double? = null,
  val rightThighCm: Double? = null,
  val leftCalfCm: Double? = null,
  val rightCalfCm: Double? = null,
)

// ---- Relations -------------------------------------------------------------------------------

data class RoutineExerciseWithSets(
  @Embedded val entry: RoutineExerciseEntity,
  @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
  @Relation(parentColumn = "id", entityColumn = "routineExerciseId") val sets: List<RoutineSetEntity>,
)

data class RoutineWithExercises(
  @Embedded val routine: RoutineEntity,
  @Relation(entity = RoutineExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
  val exercises: List<RoutineExerciseWithSets>,
) {
  val orderedExercises: List<RoutineExerciseWithSets>
    get() = exercises.sortedBy { it.entry.position }
}

data class WorkoutExerciseWithSets(
  @Embedded val entry: WorkoutExerciseEntity,
  @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
  @Relation(parentColumn = "id", entityColumn = "workoutExerciseId") val sets: List<WorkoutSetEntity>,
) {
  val orderedSets: List<WorkoutSetEntity>
    get() = sets.sortedBy { it.position }
}

data class WorkoutWithExercises(
  @Embedded val workout: WorkoutEntity,
  @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
  val exercises: List<WorkoutExerciseWithSets>,
) {
  val orderedExercises: List<WorkoutExerciseWithSets>
    get() = exercises.sortedBy { it.entry.position }
}

/** A completed set joined with its workout's times — the unit of per-exercise history. */
data class SetHistoryRow(
  @Embedded val set: WorkoutSetEntity,
  val workoutName: String,
  val startTime: Long,
  val endTime: Long,
)

class Converters {
  @TypeConverter fun musclesToString(value: List<MuscleGroup>): String = value.joinToString(",") { it.name }

  @TypeConverter
  fun stringToMuscles(value: String): List<MuscleGroup> =
    value.split(',').filter { it.isNotBlank() }.mapNotNull { name -> MuscleGroup.entries.firstOrNull { it.name == name } }
}
