package com.gymbro.local.data


import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupTest {
    private val codec = PortableBackupCodec()

    private fun validBackup() = PortableBackup(
        version = PortableBackup.CURRENT_VERSION,
        workoutSessions = listOf(WorkoutSessionEntity(7, "push", "Push", 100, null)),
        setCompletions = listOf(SetCompletionEntity(7, "chest-press", 0)),
        exerciseCompletions = listOf(ExerciseCompletionEntity(7, "chest-press", 100_000, 8, 200)),
        foodEntries = listOf(FoodEntryEntity(8, "rice", "Rice", 100, 130_000, 2_700, 300, MealSlot.LUNCH)),
        customFoods = listOf(CustomFoodEntity(9, "My food", 100_000, 10_000, 400)),
        savedMeals = listOf(SavedMealEntity(10, "Lunch", 500)),
        savedMealItems = listOf(SavedMealItemEntity(10, 0, "rice", "Rice", 100, 130_000, 2_700)),
        weightEntries = listOf(WeightEntryEntity(11, 80.5, 600)),
        settings = listOf(SettingsEntity(1, 2_100, 170)),
        exercises = WorkoutPlanSeedData.ppl45v2.exercises.sortedBy { it.id },
        workoutPlans = listOf(WorkoutPlanSeedData.ppl45v2.plan),
        workoutPlanDays = WorkoutPlanSeedData.ppl45v2.days.sortedWith(compareBy({ it.planId }, { it.position }, { it.id })),
        exercisePrescriptions = WorkoutPlanSeedData.ppl45v2.prescriptions.sortedWith(compareBy({ it.dayId }, { it.position }, { it.id })),
    )

    @Test fun `round trip preserves every Room domain and active session`() {
        val expected = validBackup()

        val decoded = codec.decodeAndValidate(codec.encode(expected))

        assertEquals(expected, decoded)
        assertEquals(null, decoded.workoutSessions.single().completedAt)
        assertEquals(1, decoded.setCompletions.size)
    }

    @Test fun `format v1 remains import compatible with absent workout plan domains`() {
        val legacy = """{"version":1,"workoutSessions":[],"setCompletions":[],"exerciseCompletions":[],"foodEntries":[],"customFoods":[],"savedMeals":[],"savedMealItems":[],"weightEntries":[],"settings":[{"id":1,"calorieGoal":2000,"proteinGoal":160}]}"""

        val decoded = codec.decodeAndValidate(legacy)

        assertEquals(1, decoded.version)
        assertTrue(decoded.exercises.isEmpty())
    }

    @Test fun `export JSON is deterministic regardless of input order`() {
        val first = validBackup().copy(weightEntries = listOf(WeightEntryEntity(12, 81.0, 700), WeightEntryEntity(11, 80.5, 600)))
        val second = first.copy(weightEntries = first.weightEntries.reversed())

        assertEquals(codec.encode(first), codec.encode(second))
    }

    @Test fun `malformed JSON is rejected with deterministic error`() {
        val error = runCatching { codec.decodeAndValidate("{not-json") }.exceptionOrNull()

        assertTrue(error is BackupValidationException)
        assertEquals("Malformed backup JSON.", error?.message)
    }

    @Test fun `unsupported version is rejected`() {
        val json = codec.encode(validBackup()).replace("\"version\":2", "\"version\":3")

        val error = runCatching { codec.decodeAndValidate(json) }.exceptionOrNull()

        assertEquals("Unsupported backup version: 3.", error?.message)
    }

    @Test fun `duplicate primary identity is rejected`() {
        val backup = validBackup().let { it.copy(foodEntries = it.foodEntries + it.foodEntries.single()) }

        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()

        assertEquals("Duplicate food entry identity: 8.", error?.message)
    }

    @Test fun `v2 rejects ambiguous persisted workout graph identities`() {
        val seed = WorkoutPlanSeedData.ppl45v2
        val cases = listOf(
            validBackup().copy(
                exercisePrescriptions = seed.prescriptions.mapIndexed { index, row ->
                    if (index == 0) row.copy(id = "") else row
                },
            ) to "Invalid exercise prescription .",
            validBackup().copy(
                workoutPlanDays = seed.days.mapIndexed { index, row ->
                    if (index == 1) row.copy(position = seed.days.first().position) else row
                },
            ) to "Duplicate workout plan day position: gymbro-ppl-45-v2/0.",
            validBackup().copy(
                exercisePrescriptions = seed.prescriptions.mapIndexed { index, row ->
                    if (index == 1) row.copy(position = seed.prescriptions.first().position) else row
                },
            ) to "Duplicate exercise prescription position: push/0.",
            validBackup().copy(
                exercisePrescriptions = seed.prescriptions.mapIndexed { index, row ->
                    if (index == 1) row.copy(exerciseId = seed.prescriptions.first().exerciseId) else row
                },
            ) to "Duplicate exercise prescription exercise: push/chest-press.",
        )

        cases.forEach { (backup, expectedMessage) ->
            val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()
            assertEquals(expectedMessage, error?.message)
        }
    }

    @Test fun `broken saved meal relationship is rejected`() {
        val backup = validBackup().copy(savedMealItems = listOf(SavedMealItemEntity(99, 0, "rice", "Rice", 100, 130_000, 2_700)))

        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()

        assertEquals("Saved meal item references missing meal: 99.", error?.message)
    }

    @Test fun `invalid values are rejected before restore`() {
        val backup = validBackup().copy(weightEntries = listOf(WeightEntryEntity(11, 401.0, 600)))
        val json = codec.encode(backup)

        val error = runCatching { codec.decodeAndValidate(json) }.exceptionOrNull()

        assertEquals("Invalid weight entry 11.", error?.message)
    }

    @Test fun `workout completions must belong to the session day prescription`() {
        val wrongExercise = validBackup().copy(
            setCompletions = listOf(SetCompletionEntity(7, "lat-pulldown", 0)),
            exerciseCompletions = emptyList(),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(wrongExercise)) }.exceptionOrNull()

        assertEquals("Set completion references an exercise outside session 7: lat-pulldown.", error?.message)
    }

    @Test fun `persisted workout completion validates against persisted prescription`() {
        val backup = validBackup().copy(
            setCompletions = emptyList(),
            exerciseCompletions = listOf(ExerciseCompletionEntity(7, "chest-press", 100_000, 8, 200)),
        )

        assertEquals(backup, codec.decodeAndValidate(codec.encode(backup)))
    }

    @Test fun `format v2 session relationships never fall back to legacy seed data`() {
        val withoutChestPress = validBackup().let { backup ->
            backup.copy(
                setCompletions = listOf(SetCompletionEntity(7, "chest-press", 0)),
                exerciseCompletions = emptyList(),
                exercises = backup.exercises + ExerciseEntity("custom-push", "Custom push", "chest", "machine"),
                exercisePrescriptions = backup.exercisePrescriptions.map {
                    if (it.id == "push-chest-press") it.copy(exerciseId = "custom-push", isSeeded = false) else it
                },
            )
        }

        val error = runCatching { codec.decodeAndValidate(codec.encode(withoutChestPress)) }.exceptionOrNull()

        assertEquals("Set completion references an exercise outside session 7: chest-press.", error?.message)
    }

    @Test fun `format v1 session relationships retain legacy seed fallback`() {
        val legacy = validBackup().copy(
            version = 1,
            exercises = emptyList(),
            workoutPlans = emptyList(),
            workoutPlanDays = emptyList(),
            exercisePrescriptions = emptyList(),
        )

        assertEquals(legacy, codec.decodeAndValidate(codec.encode(legacy)))
    }

    @Test fun `v2 rejects modified seeded records and missing canonical seed identities`() {
        val modifiedSeed = validBackup().copy(
            exercises = validBackup().exercises.map {
                if (it.id == "chest-press") it.copy(name = "Tampered seed") else it
            },
        )
        val missingSeed = validBackup().copy(
            exercisePrescriptions = validBackup().exercisePrescriptions.filterNot { it.id == "push-chest-press" },
            setCompletions = emptyList(),
            exerciseCompletions = emptyList(),
        )

        val modifiedError = runCatching { codec.decodeAndValidate(codec.encode(modifiedSeed)) }.exceptionOrNull()
        val missingError = runCatching { codec.decodeAndValidate(codec.encode(missingSeed)) }.exceptionOrNull()

        assertEquals("Modified seeded exercise: chest-press.", modifiedError?.message)
        assertEquals("Missing required seeded exercise prescription: push-chest-press.", missingError?.message)
    }

    @Test fun `v2 accepts user edited canonical seed identity when marked unseeded`() {
        val edited = validBackup().copy(
            exercisePrescriptions = validBackup().exercisePrescriptions.map {
                if (it.id == "push-chest-press") it.copy(sets = 4, isSeeded = false) else it
            },
        )

        assertEquals(edited, codec.decodeAndValidate(codec.encode(edited)))
    }

    @Test fun `format v2 legacy set completion remains valid after prescription sets are lowered`() {
        val edited = validBackup().copy(
            setCompletions = listOf(SetCompletionEntity(7, "chest-press", 2)),
            exerciseCompletions = emptyList(),
            exercisePrescriptions = validBackup().exercisePrescriptions.map {
                if (it.id == "push-chest-press") it.copy(sets = 2, isSeeded = false) else it
            },
        )

        assertEquals(edited, codec.decodeAndValidate(codec.encode(edited)))
    }

    @Test fun `format v2 legacy set index must be nonnegative and conservatively bounded`() {
        listOf(-1, 20).forEach { setIndex ->
            val invalid = validBackup().copy(
                setCompletions = listOf(SetCompletionEntity(7, "chest-press", setIndex)),
                exerciseCompletions = emptyList(),
            )

            val error = runCatching { codec.decodeAndValidate(codec.encode(invalid)) }.exceptionOrNull()

            assertEquals("Invalid set completion 7/chest-press/$setIndex.", error?.message)
        }
    }

    @Test fun `format v1 set index retains static plan bound`() {
        val legacy = validBackup().copy(
            version = 1,
            setCompletions = listOf(SetCompletionEntity(7, "chest-press", 3)),
            exerciseCompletions = emptyList(),
            exercises = emptyList(),
            workoutPlans = emptyList(),
            workoutPlanDays = emptyList(),
            exercisePrescriptions = emptyList(),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(legacy)) }.exceptionOrNull()

        assertEquals("Invalid set completion 7/chest-press/3.", error?.message)
    }

    @Test fun `exercise completion time must fall within its workout session`() {
        val beforeStart = validBackup().copy(
            setCompletions = emptyList(),
            exerciseCompletions = listOf(ExerciseCompletionEntity(7, "chest-press", 100_000, 8, 99)),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(beforeStart)) }.exceptionOrNull()

        assertEquals("Exercise completion time is outside session 7: chest-press.", error?.message)
    }

    @Test fun `invalid UTF-8 backup bytes are rejected instead of replaced`() {
        val invalid = byteArrayOf('{'.code.toByte(), '"'.code.toByte(), 0xC3.toByte(), 0x28, '"'.code.toByte(), '}'.code.toByte())

        val error = runCatching { decodeStrictUtf8(invalid) }.exceptionOrNull()

        assertTrue(error is BackupValidationException)
        assertEquals("Backup is not valid UTF-8.", error?.message)
    }

    @Test fun `credential-like fields are not part of the format`() {
        val json = codec.encode(validBackup())

        assertTrue("USDA credential must never be exported", !json.contains("api", ignoreCase = true) && !json.contains("key", ignoreCase = true))
    }
}
