package com.nastlo.lessonswidget.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FaqItem(val q: String, val a: String)

private val faq = listOf(
    FaqItem(
        "Как загрузить уроки из «Моя Школа»",
        "1. На главном экране нажми «Импорт».\n" +
            "2. Нажми «Войти в «Моя Школа»» и войди через Госуслуги.\n" +
            "3. Приложение само поймает токен — ничего копировать не надо.\n" +
            "4. Выбери свою карточку (имя + класс).\n" +
            "5. Посмотри предпросмотр и нажми «Добавить в расписание».\n\n" +
            "Больше полный вход не нужен: дальше расписание обновляется по стрелке ⟳ и автоматически каждое утро в 6:45.",
    ),
    FaqItem(
        "Пишет «Вход истёк»",
        "Сессия «Моя Школа» со временем устаревает. Открой «Импорт» → войди через Госуслуги один раз — дальше всё продолжит работать (⟳ и утреннее обновление).",
    ),
    FaqItem(
        "Нажал ⟳, учителя не появились",
        "Расписание ставится сразу, а учителя догружаются фоном по дням: сначала сегодня, потом следующие дни. Подожди 20–40 секунд или нажми ⟳ ещё раз.",
    ),
    FaqItem(
        "Виджет показывает старое",
        "1. Тапни по виджету — он обновится.\n" +
            "2. В настройках глянь «Последнее обновление»: если давно — нажми ⟳ в приложении.\n" +
            "3. Если система «усыпляет» приложение — в настройках телефона разреши ему работу в фоне (аккумулятор → без ограничений, автозапуск).",
    ),
    FaqItem(
        "Метка «⚠ замена» стоит, где её нет (или наоборот)",
        "Пометка привязана к текущей неделе: после ⟳ или утреннего обновления в 6:45 она ставится/снимается по данным сервера. Если после обновления всё равно неверно — напиши мне, посмотрим сырой ответ сервера по этому уроку.",
    ),
    FaqItem(
        "Уроки задублировались",
        "Нажми ⟳ — расписание перезаписывается с нуля, дубли исчезают. Старые дубли в виджете могут висеть, пока он не перечитает данные: тапни по виджету.",
    ),
    FaqItem(
        "Время уроков не совпадает со звонками",
        "Время берётся с сервера школы. Обнови ⟳; если и после этого не совпадает, напиши мне — посмотрю, что отдаёт сервер.",
    ),
    FaqItem(
        "Как поменять тему / цвета",
        "Настройки → Тема: «Как в системе», «Тёмная», «Светлая». Меняется и приложение, и виджет.",
    ),
    FaqItem(
        "Как удалить урок",
        "Карточка урока → корзина справа. Учти: после ⟳ серверное расписание вернётся целиком.",
    ),
    FaqItem(
        "Что значат значки в виджете",
        "⚠ — замена учителя на этой неделе.\n" +
            "Синяя рамка — этот урок идёт прямо сейчас.\n" +
            "Серый урок — уже закончился.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit) {
    // открытые вопросы (их может быть несколько)
    val open = remember { mutableStateListOf<Int>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Частые вопросы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            faq.forEachIndexed { index, item ->
                val isOpen = open.contains(index)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isOpen) open.remove(index) else open.add(index)
                    },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(item.q, fontWeight = FontWeight.SemiBold)
                        if (isOpen) {
                            Text(
                                item.a,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
