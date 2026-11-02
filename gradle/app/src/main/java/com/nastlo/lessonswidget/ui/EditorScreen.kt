package com.nastlo.lessonswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastlo.lessonswidget.data.Lesson
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val todayWeekday = remember { LocalDate.now().dayOfWeek.value }
    var selectedDay by rememberSaveable { mutableStateOf(todayWeekday) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val dayLessons = lessons.filter { it.weekday == selectedDay }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Расписание уроков") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить урок")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedDay - 1) {
                dayLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedDay == index + 1,
                        onClick = { selectedDay = index + 1 },
                        text = { Text(label) },
                    )
                }
            }

            if (dayLessons.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Нет уроков. Нажми «+», чтобы добавить.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(dayLessons, key = { it.id }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            onEdit = { editing = lesson },
                            onDelete = { viewModel.delete(lesson) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        LessonDialog(
            onDismiss = { showAdd = false },
            onSave = { lesson ->
                viewModel.save(lesson)
                showAdd = false
            },
        )
    }

    editing?.let { current ->
        LessonDialog(
            lesson = current,
            onDismiss = { editing = null },
            onSave = { lesson ->
                viewModel.save(lesson)
                editing = null
            },
        )
    }
}

@Composable
private fun LessonCard(lesson: Lesson, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${lesson.start}–${lesson.end} · ${lesson.subject}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    append("Каб. ")
                    append(lesson.room)
                    lesson.floor?.let { append(" · ").append(it).append(" этаж") }
                    lesson.teacher?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            lesson.note?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
