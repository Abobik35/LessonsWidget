package com.nastlo.lessonswidget.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.data.ThemePrefs
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import java.time.LocalDate
import java.time.LocalTime
import android.widget.Toast
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onThemeChange: (Int) -> Unit = {},
) {
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val todayWeekday = remember { LocalDate.now().dayOfWeek.value }
    var selectedDay by rememberSaveable { mutableStateOf(todayWeekday) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // каждый возврат в приложение перерисовывает виджет — с ним и в редакторе
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWidget()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    val toastContext = LocalContext.current
    // тик раз в 30 сек: пересчитывает «идёт/закончился» у карточек
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick++
        }
    }

    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            Toast.makeText(toastContext, it, Toast.LENGTH_LONG).show()
            viewModel.clearRefreshMessage()
        }
    }

    if (showImport) {
        ImportScreen(viewModel = viewModel, onBack = { showImport = false })
        return
    }

    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
        return
    }

    val dayLessons = lessons.filter { it.weekday == selectedDay }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Расписание уроков") },
                actions = {
                    // Быстрое обновление «Моя Школа» в один тап
                    if (refreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.quickRefresh() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Обновить расписание",
                            )
                        }
                    }
                    TextButton(onClick = { showImport = true }) {
                        Text("Импорт")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            AppIcons.Settings,
                            contentDescription = "Настройки",
                        )
                    }
                },
            )
        },
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
                            // подсвечиваем проход/завершение только на вкладке «сегодня»;
                            // tick читается, чтобы состояние карточек обновлялось раз в полминуты
                            stateNow = if (selectedDay == todayWeekday && tick >= 0) LocalTime.now() else null,
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
private fun LessonCard(lesson: Lesson, stateNow: LocalTime?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val blue = if (isSystemInDarkTheme()) Color(0xFF64B5F6) else Color(0xFF1565C0)
    val startT = stateNow?.let { NextLessonCalculator.parse(lesson.start) }
    val endT = stateNow?.let { NextLessonCalculator.parse(lesson.end) }
    val isCurrent = startT != null && endT != null && stateNow != null &&
        !stateNow.isBefore(startT) && stateNow.isBefore(endT)
    val isPast = endT != null && stateNow != null && !stateNow.isBefore(endT)

    Card(
        onClick = onEdit,
        border = if (isCurrent) BorderStroke(2.dp, blue) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    // закончившийся — весь блок содержания «серым»: тускнеет
                    .alpha(if (isPast) 0.45f else 1f),
            ) {
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
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Удалить урок",
                )
            }
        }
    }
}
