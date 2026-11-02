package com.nastlo.lessonswidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import com.nastlo.lessonswidget.logic.ScheduleState
import com.nastlo.lessonswidget.ui.MainActivity
import java.time.LocalDateTime

class LessonsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        listOf(
            DpSize(140.dp, 60.dp),
            DpSize(220.dp, 90.dp),
            DpSize(300.dp, 180.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as LessonsApp).db.lessonDao()
        val lessons = dao.getAll()
        val now = LocalDateTime.now()
        val state = NextLessonCalculator.compute(lessons, now.toLocalDate(), now.toLocalTime())

        provideContent {
            WidgetRoot(state, lessons)
        }
    }

    @Composable
    private fun WidgetRoot(state: ScheduleState, allLessons: List<Lesson>) {
        val isWide = LocalSize.current.width >= 200.dp
        val isTall = LocalSize.current.height >= 150.dp

        val ui = when (state) {
            is ScheduleState.BeforeLessons -> WidgetUi(
                status = "через ${state.minutesUntil} мин",
                headline = state.next.subject,
                detail = describe(state.next),
            )
            is ScheduleState.DuringLesson -> WidgetUi(
                status = "идёт · до конца ${state.minutesToEnd} мин",
                headline = state.current.subject,
                detail = describe(state.current) + (state.next?.let {
                    val room = if (it.room.isBlank()) "" else " в ${it.room}"
                    " · далее: ${it.subject}$room"
                } ?: ""),
            )
            is ScheduleState.DuringBreak -> WidgetUi(
                status = "перемена · осталось ${state.minutesUntil} мин",
                headline = "${state.next.subject}",
                detail = "Каб. ${state.next.room}" +
                    (state.next.floor?.let { " · $it этаж" } ?: "") +
                    (state.next.teacher?.let { " · $it" } ?: ""),
            )
            is ScheduleState.AfterLessons -> WidgetUi(
                status = "на сегодня всё",
                headline = "Уроки закончились",
                detail = "Хорошего отдыха ✌️",
            )
            ScheduleState.NothingPlanned -> WidgetUi(
                status = "нет данных",
                headline = "Расписание пусто",
                detail = "Откройте приложение, чтобы добавить уроки",
            )
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Bg))
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = ui.status,
                        style = TextStyle(
                            color = ColorProvider(Accent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = ui.headline,
                    style = TextStyle(
                        color = ColorProvider(TextMain),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (isWide) {
                    Spacer(GlanceModifier.height(1.dp))
                    Text(
                        text = ui.detail,
                        style = TextStyle(
                            color = ColorProvider(TextDim),
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
                if (isTall) {
                    Spacer(GlanceModifier.height(6.dp))
                    DayList(allLessons)
                }
            }
        }
    }

    @Composable
    private fun DayList(lessons: List<Lesson>) {
        val todayLessons = lessons
            .filter { it.weekday == java.time.LocalDate.now().dayOfWeek.value }
            .filter { NextLessonCalculator.parse(it.start) != null && NextLessonCalculator.parse(it.end) != null }
        if (todayLessons.isEmpty()) return

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            todayLessons.sortedBy { NextLessonCalculator.parse(it.start) }.take(12).forEach { lesson ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        text = "${lesson.start}–${lesson.end}",
                        style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = lesson.subject,
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = lesson.room,
                        style = TextStyle(color = ColorProvider(Accent), fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    private fun describe(lesson: Lesson): String =
        "Каб. ${lesson.room}" +
            (lesson.floor?.let { " · $it этаж" } ?: "") +
            (lesson.teacher?.let { " · $it" } ?: "")

    private data class WidgetUi(val status: String, val headline: String, val detail: String)

    private companion object {
        val Bg = Color(0xFF262B35)
        val Accent = Color(0xFF7CB342)
        val TextMain = Color(0xFFF2F4F8)
        val TextDim = Color(0xFF9AA3B0)
    }
}
