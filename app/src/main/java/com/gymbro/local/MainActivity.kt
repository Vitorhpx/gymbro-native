package com.gymbro.local

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymbro.local.data.FoodEntryEntity
import com.gymbro.local.data.SetCompletionEntity
import com.gymbro.local.data.WeightEntryEntity
import com.gymbro.local.data.WorkoutSessionEntity
import com.gymbro.local.data.displayCalories
import com.gymbro.local.data.displayProteinGrams
import com.gymbro.local.data.totalCalories
import com.gymbro.local.data.totalProteinGrams
import com.gymbro.local.domain.SeedData
import com.gymbro.local.domain.WorkoutDay
import com.gymbro.local.domain.WorkoutRecommendation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GymBroTheme { GymBroApp(vm) } }
    }
}

private val GymBroColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF91D7C3), onPrimary = Color(0xFF00382F),
    secondary = Color(0xFFBEC9FF), surface = Color(0xFF111816),
    surfaceVariant = Color(0xFF28332F), background = Color(0xFF0B0F0E),
)

@Composable private fun GymBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GymBroColors, content = content)
}

private enum class Tab(val label: String, val glyph: String) {
    Today("Today", "●"), Train("Train", "▲"), Eat("Eat", "◆"), Progress("Progress", "■")
}

@Composable fun GymBroApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.Today) }
    val active by vm.active.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val completions by vm.completions.collectAsState()
    val foods by vm.foodEntries.collectAsState()
    val weights by vm.weights.collectAsState()
    val settings by vm.settings.collectAsState()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.glyph) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Today -> TodayScreen(active, sessions, foods, weights, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, vm)
                Tab.Train -> TrainScreen(active, completions, vm)
                Tab.Eat -> EatScreen(foods, settings?.calorieGoal ?: 2000, settings?.proteinGoal ?: 160, vm)
                Tab.Progress -> ProgressScreen(sessions, weights, vm)
            }
        }
    }
}

@Composable private fun Screen(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun startOfWeek(): Long = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun displayDate(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))

@Composable private fun TodayScreen(
    active: WorkoutSessionEntity?, sessions: List<WorkoutSessionEntity>, foods: List<FoodEntryEntity>, weights: List<WeightEntryEntity>,
    calorieGoal: Int, proteinGoal: Int, vm: MainViewModel,
) = Screen("Today") {
    val todayFoods = foods.filter { it.loggedAt >= startOfToday() }
    val calories = todayFoods.totalCalories()
    val protein = todayFoods.totalProteinGrams()
    val weekSessions = sessions.filter { it.completedAt != null && it.completedAt >= startOfWeek() }
    val recommended = WorkoutRecommendation.nextDay(weekSessions.map { it.dayId })
    Section("Nutrition") {
        Text("${(calorieGoal - calories).coerceAtLeast(0)} kcal remaining · $calories / $calorieGoal")
        Text("${(proteinGoal - protein).coerceAtLeast(0)} g protein remaining · $protein / $proteinGoal")
    }
    Section("This week") {
        Text("${weekSessions.size} / 3 workouts", style = MaterialTheme.typography.titleMedium)
        Text(if (weights.any { it.loggedAt >= startOfWeek() }) "Weekly weigh-in complete" else "Weekly weigh-in due")
    }
    Section(if (active != null) "Workout in progress" else "Recommended workout") {
        Text(active?.dayName ?: recommended.name, style = MaterialTheme.typography.titleMedium)
        Text("GymBro PPL 45 V2 · about 45 min")
        if (active == null) Button(onClick = { vm.startWorkout(recommended.id, recommended.name) }) { Text("Start ${recommended.name}") }
        else Text("Open Train to resume your saved session.")
    }
}

@Composable private fun TrainScreen(active: WorkoutSessionEntity?, completions: List<SetCompletionEntity>, vm: MainViewModel) = Screen("Train") {
    if (active == null) {
        Text("GymBro PPL 45 V2", style = MaterialTheme.typography.titleLarge)
        Text("3×/week · ~45 min · focused sessions")
        SeedData.plan.days.forEach { day ->
            Section(day.name) {
                Text(day.exercises.joinToString(" · ") { it.name })
                Button(onClick = { vm.startWorkout(day.id, day.name) }, Modifier.fillMaxWidth()) { Text("Start ${day.name}") }
            }
        }
    } else {
        val day = SeedData.plan.days.firstOrNull { it.id == active.dayId } ?: SeedData.plan.days.first()
        Text("${day.name} · started ${displayDate(active.startedAt)}")
        day.exercises.forEach { exercise ->
            Section(exercise.name) {
                Text("${exercise.sets} sets × ${exercise.reps} reps · ${exercise.restSeconds}s rest")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(exercise.sets) { index ->
                        val checked = completions.any { it.sessionId == active.id && it.exerciseId == exercise.id && it.setIndex == index }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(checked, onCheckedChange = { vm.setChecked(active.id, exercise.id, index, it) })
                            Text("Set ${index + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Button(onClick = { vm.completeWorkout(active.id) }, Modifier.fillMaxWidth()) { Text("Complete workout") }
        Text("Checked sets and this active workout are saved on-device automatically.")
    }
}

@Composable private fun EatScreen(entries: List<FoodEntryEntity>, calorieGoal: Int, proteinGoal: Int, vm: MainViewModel) = Screen("Eat") {
    val today = entries.filter { it.loggedAt >= startOfToday() }
    var grams by remember { mutableStateOf("100") }
    var caloriesGoalText by remember(calorieGoal) { mutableStateOf(calorieGoal.toString()) }
    var proteinGoalText by remember(proteinGoal) { mutableStateOf(proteinGoal.toString()) }
    Section("Today") {
        Text("${today.totalCalories()} / $calorieGoal kcal", style = MaterialTheme.typography.titleMedium)
        Text("${today.totalProteinGrams()} / $proteinGoal g protein")
    }
    Section("Quick log by grams") {
        OutlinedTextField(grams, { grams = it.filter(Char::isDigit) }, label = { Text("Grams") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        SeedData.foods.forEach { food ->
            OutlinedButton(onClick = { vm.logFood(food, grams.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth()) {
                Text("${food.name} · ${food.caloriesPer100g.toInt()} kcal/100g")
            }
        }
    }
    if (today.isNotEmpty()) Section("Logged today") {
        today.forEach { entry ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(entry.name); Text("${entry.grams}g · ${entry.displayCalories()} kcal · ${entry.displayProteinGrams()}g protein") }
                OutlinedButton(onClick = { vm.deleteFood(entry.id) }) { Text("Remove") }
            }
            HorizontalDivider()
        }
    }
    Section("Daily goals") {
        OutlinedTextField(caloriesGoalText, { caloriesGoalText = it.filter(Char::isDigit) }, label = { Text("Calories") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(proteinGoalText, { proteinGoalText = it.filter(Char::isDigit) }, label = { Text("Protein (g)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveGoals(caloriesGoalText.toIntOrNull() ?: 0, proteinGoalText.toIntOrNull() ?: 0) }) { Text("Save goals") }
    }
}

@Composable private fun ProgressScreen(sessions: List<WorkoutSessionEntity>, weights: List<WeightEntryEntity>, vm: MainViewModel) = Screen("Progress") {
    val weekCount = sessions.count { it.completedAt != null && it.completedAt >= startOfWeek() }
    var kg by remember { mutableStateOf("") }
    Section("Workout consistency") { Text("$weekCount / 3 workouts this week", style = MaterialTheme.typography.titleMedium) }
    Section("Log body weight") {
        OutlinedTextField(kg, { kg = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text("Weight (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Button(onClick = { kg.toDoubleOrNull()?.let { vm.logWeight(it); kg = "" } }, Modifier.fillMaxWidth()) { Text("Save weigh-in") }
    }
    Section("Recent weigh-ins") {
        if (weights.isEmpty()) Text("No weigh-ins yet.")
        weights.take(12).forEach { Text("${it.kilograms} kg · ${displayDate(it.loggedAt)}") }
    }
    Section("Recent workouts") {
        val completed = sessions.filter { it.completedAt != null }.take(12)
        if (completed.isEmpty()) Text("No completed workouts yet.")
        completed.forEach { Text("${it.dayName} · ${displayDate(it.completedAt!!)}") }
    }
}
