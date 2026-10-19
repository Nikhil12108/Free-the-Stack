package com.ironlog.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class SetType(val short: String, val label: String) {
  WARMUP("W", "Warm-up"),
  NORMAL("", "Normal"),
  FAILURE("F", "Failure"),
  DROPSET("D", "Drop set");

  /** Sets that count for progression and planned volume: not warm-ups, not the lighter tail of a drop set. */
  val isWorking: Boolean
    get() = this == NORMAL || this == FAILURE
}

/** What a set of this exercise records. Decides which columns the set table shows. */
@Serializable
enum class ExerciseType(val label: String, val usesWeight: Boolean, val usesReps: Boolean, val usesDuration: Boolean, val usesDistance: Boolean) {
  WEIGHT_REPS("Weight & reps", usesWeight = true, usesReps = true, usesDuration = false, usesDistance = false),
  REPS_ONLY("Reps only", usesWeight = false, usesReps = true, usesDuration = false, usesDistance = false),
  WEIGHTED_BODYWEIGHT("Weighted bodyweight", usesWeight = true, usesReps = true, usesDuration = false, usesDistance = false),
  ASSISTED_BODYWEIGHT("Assisted bodyweight", usesWeight = true, usesReps = true, usesDuration = false, usesDistance = false),
  DURATION("Duration", usesWeight = false, usesReps = false, usesDuration = true, usesDistance = false),
  WEIGHT_DURATION("Weight & duration", usesWeight = true, usesReps = false, usesDuration = true, usesDistance = false),
  DISTANCE_DURATION("Distance & duration", usesWeight = false, usesReps = false, usesDuration = true, usesDistance = true),
  WEIGHT_DISTANCE("Weight & distance", usesWeight = true, usesReps = false, usesDuration = false, usesDistance = true);

  /** Volume (weight x reps) only means something when the weight is load lifted. */
  val countsVolume: Boolean
    get() = this == WEIGHT_REPS || this == WEIGHTED_BODYWEIGHT
}

@Serializable
enum class MuscleGroup(val label: String) {
  CHEST("Chest"),
  SHOULDERS("Shoulders"),
  BICEPS("Biceps"),
  TRICEPS("Triceps"),
  FOREARMS("Forearms"),
  LATS("Lats"),
  UPPER_BACK("Upper back"),
  TRAPS("Traps"),
  LOWER_BACK("Lower back"),
  ABDOMINALS("Abs"),
  QUADRICEPS("Quads"),
  HAMSTRINGS("Hamstrings"),
  GLUTES("Glutes"),
  CALVES("Calves"),
  ABDUCTORS("Abductors"),
  ADDUCTORS("Adductors"),
  NECK("Neck"),
  CARDIO("Cardio"),
  FULL_BODY("Full body"),
  OTHER("Other"),
}

@Serializable
enum class Equipment(val label: String) {
  NONE("None"),
  BARBELL("Barbell"),
  DUMBBELL("Dumbbell"),
  KETTLEBELL("Kettlebell"),
  MACHINE("Machine"),
  CABLE("Cable"),
  PLATE("Plate"),
  RESISTANCE_BAND("Band"),
  SUSPENSION("Suspension"),
  OTHER("Other"),
}

enum class WeightUnit(val label: String) {
  KG("kg"),
  LBS("lbs"),
}

enum class DistanceUnit(val label: String) {
  KM("km"),
  MILES("mi"),
}

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}
