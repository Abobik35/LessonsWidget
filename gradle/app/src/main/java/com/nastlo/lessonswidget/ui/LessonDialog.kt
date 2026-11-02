package com.nastlo.lessonswidget.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import java.time.LocalDate

internal val dayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDialog(
    lesson: Lesson? = null,
    onDismiss: () -> Unit,
    onSave: (Lesson) -> Unit,
) {
    var subject by remember { mutableStateOf(lesson?.subject ?: "") }
    var room by remember { mutableStateOf(lesson?.room ?: "") }
    var floor by remember { mutableStateOf(lesson?.floor?.toString() ?: "") }
    var teacher by remember { mutableStateOf(lesson?.teacher ?: "") }
    var note by remember { mutableStateOf(lesson?.note ?: "") }
    var start by remember { mutableStateOf(lesson?.start ?: "08:30") }
    var end by remember { mutableStateOf(lesson?.end ?: "09:15") }
    var weekday by remember { mutableStateOf(lesson?.weekday ?: LocalDate.now().dayOfWeek.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lesson == null) "Новый урок" else "Изменить урок") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    dayLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = weekday == index + 1,
                            onClick = { weekday = index + 1 },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Предмет *") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Кабинет") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = floor,
                    onValueChange = { floor = it },
                    label = { Text("Этаж (необязательно)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Преподаватель") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка: «принести тетрадь»") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Начало HH:mm") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("Конец HH:mm") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val valid = subject.isNotBlank() &&
                        NextLessonCalculator.parse(start) != null &&
                        NextLessonCalculator.parse(end) != null
                    if (!valid) return@TextButton
                    onSave(
                        Lesson(
                            id = lesson?.id ?: 0,
                            weekday = weekday,
                            subject = subject.trim(),
                            room = room.trim(),
                            floor = floor.toIntOrNull(),
                            teacher = teacher.trim().ifEmpty { null },
                            note = note.trim().ifEmpty { null },
                            start = start.trim(),
                            end = end.trim(),
                        )
                    )
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
