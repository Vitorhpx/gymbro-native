package com.gymbro.local.domain

data class ExercisePrescription(
    val id: String,
    val name: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
)

data class WorkoutDay(val id: String, val name: String, val exercises: List<ExercisePrescription>)
data class WorkoutPlan(val id: String, val name: String, val days: List<WorkoutDay>)

object SeedData {
    private fun ex(id: String, name: String, sets: Int, reps: String, rest: Int = 90) =
        ExercisePrescription(id, name, sets, reps, rest)

    val foods = listOf(
        FoodPreset("chicken", "Chicken breast, cooked", 165.0, 31.0),
        FoodPreset("rice", "White rice, cooked", 130.0, 2.7),
        FoodPreset("beans", "Black beans, cooked", 132.0, 8.9),
        FoodPreset("broccoli", "Broccoli, cooked", 35.0, 2.4),
        FoodPreset("picanha", "Picanha, cooked (~10% fat)", 200.0, 27.5),
    )

    val plan = WorkoutPlan(
        id = "gymbro-ppl-45-v2",
        name = "GymBro PPL 45 V2",
        days = listOf(
            WorkoutDay("push", "Push", listOf(
                ex("chest-press", "Chest Press Machine", 3, "6–10", 120),
                ex("incline-press", "Incline Chest Press Machine", 2, "8–12"),
                ex("shoulder-press", "DB Shoulder Press", 2, "8–12"),
                ex("lateral-raise", "DB Lateral Raises", 3, "12–20", 60),
                ex("triceps-pushdown", "Rope Triceps Pushdown", 2, "10–15", 60),
            )),
            WorkoutDay("pull", "Pull", listOf(
                ex("lat-pulldown", "Lat Pulldown", 3, "6–10", 120),
                ex("seated-row", "Seated Row", 3, "8–12", 120),
                ex("rear-delt", "Rear Delt Fly Machine", 3, "12–20", 60),
                ex("cable-curl", "EZ Bar Cable Curl", 2, "8–12", 60),
                ex("hammer-curl", "Hammer Curls", 2, "10–15", 60),
            )),
            WorkoutDay("legs", "Legs", listOf(
                ex("smith-squat", "Smith Squat", 2, "6–8", 120),
                ex("smith-rdl", "Smith RDL", 2, "8–10", 120),
                ex("hip-thrust", "Smith Hip Thrust", 3, "10–15"),
                ex("hip-abductor", "Hip Abductor Machine", 2, "12–20", 60),
                ex("calf-raise", "Calf Raise Machine", 3, "12–20", 60),
            )),
        ),
    )
}
