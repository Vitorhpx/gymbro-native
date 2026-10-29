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
    )

    @Test fun `round trip preserves every Room v2 domain and active session`() {
        val expected = validBackup()

        val decoded = codec.decodeAndValidate(codec.encode(expected))

        assertEquals(expected, decoded)
        assertEquals(null, decoded.workoutSessions.single().completedAt)
        assertEquals(1, decoded.setCompletions.size)
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
        val json = codec.encode(validBackup()).replace("\"version\":1", "\"version\":2")

        val error = runCatching { codec.decodeAndValidate(json) }.exceptionOrNull()

        assertEquals("Unsupported backup version: 2.", error?.message)
    }

    @Test fun `duplicate primary identity is rejected`() {
        val backup = validBackup().let { it.copy(foodEntries = it.foodEntries + it.foodEntries.single()) }

        val error = runCatching { codec.decodeAndValidate(codec.encode(backup)) }.exceptionOrNull()

        assertEquals("Duplicate food entry identity: 8.", error?.message)
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

    @Test fun `legacy set index must fit the prescribed set count`() {
        val outOfRange = validBackup().copy(
            setCompletions = listOf(SetCompletionEntity(7, "chest-press", 3)),
            exerciseCompletions = emptyList(),
        )

        val error = runCatching { codec.decodeAndValidate(codec.encode(outOfRange)) }.exceptionOrNull()

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
