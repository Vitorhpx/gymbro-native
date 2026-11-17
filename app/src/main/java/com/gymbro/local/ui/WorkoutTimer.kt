package com.gymbro.local.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

fun formatWorkoutElapsed(startedAt: Long, now: Long): String {
    val totalSeconds = ((now - startedAt).coerceAtLeast(0L)) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun workoutTimerAccessibilityLabel(elapsed: String) = "Workout time $elapsed"

@Composable
fun WorkoutElapsedTimer(startedAt: Long, modifier: Modifier = Modifier) {
    val now by produceState(initialValue = System.currentTimeMillis(), startedAt) {
        while (true) {
            val current = System.currentTimeMillis()
            value = current
            delay((1_000L - (current - startedAt).mod(1_000L)).coerceAtLeast(1L))
        }
    }
    val elapsed = formatWorkoutElapsed(startedAt, now)
    Text(
        text = elapsed,
        modifier = modifier.semantics { contentDescription = workoutTimerAccessibilityLabel(elapsed) },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
    )
}
