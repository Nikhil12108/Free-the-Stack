package com.ironlog.app.data

import com.ironlog.app.data.db.BodyMeasurementEntity
import com.ironlog.app.data.db.WorkoutWithExercises
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Plain CSV in canonical units (kg, m, s) so it opens in any spreadsheet. One row per set. */
object CsvExport {
  private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

  fun workouts(workouts: List<WorkoutWithExercises>): String = buildString {
    appendLine("workout,start,end,exercise,set_index,set_type,weight_kg,reps,distance_m,duration_s,rpe,rir,target_weight_kg,target_reps,target_rir,exercise_notes,workout_notes")
    for (w in workouts.sortedBy { it.workout.startTime }) {
      for (e in w.orderedExercises) {
        e.orderedSets.forEachIndexed { i, s ->
          appendLine(
            listOf(
                w.workout.name,
                time.format(Instant.ofEpochMilli(w.workout.startTime)),
                time.format(Instant.ofEpochMilli(w.workout.endTime)),
                e.exercise.name,
                (i + 1).toString(),
                s.type.name.lowercase(),
                s.weightKg?.toString().orEmpty(),
                s.reps?.toString().orEmpty(),
                s.distanceM?.toString().orEmpty(),
                s.durationSec?.toString().orEmpty(),
                s.rpe?.toString().orEmpty(),
                s.rir?.toString().orEmpty(),
                s.targetWeightKg?.toString().orEmpty(),
                s.targetReps?.toString().orEmpty(),
                s.targetRir?.toString().orEmpty(),
                e.entry.notes,
                w.workout.notes,
              )
              .joinToString(",", transform = ::escape)
          )
        }
      }
    }
  }

  fun measurements(entries: List<BodyMeasurementEntity>): String = buildString {
    appendLine("date,weight_kg,body_fat_pct,neck_cm,shoulders_cm,chest_cm,left_bicep_cm,right_bicep_cm,left_forearm_cm,right_forearm_cm,waist_cm,hips_cm,left_thigh_cm,right_thigh_cm,left_calf_cm,right_calf_cm")
    for (m in entries.sortedBy { it.date }) {
      appendLine(
        listOf(m.date, m.weightKg, m.bodyFatPct, m.neckCm, m.shouldersCm, m.chestCm, m.leftBicepCm, m.rightBicepCm, m.leftForearmCm, m.rightForearmCm, m.waistCm, m.hipsCm, m.leftThighCm, m.rightThighCm, m.leftCalfCm, m.rightCalfCm)
          .joinToString(",") { it?.toString().orEmpty() }
      )
    }
  }

  private fun escape(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
