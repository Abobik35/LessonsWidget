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
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
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
import com.nastlo.lessonswidget.data.ThemePrefs
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import com.nastlo.lessonswidget.logic.ScheduleState
import com.nastlo.lessonswidget.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LessonsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
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
        val colors = if (ThemePrefs.isDark(context)) DarkColors else LightColors

        provideContent {
            WidgetRoot(state, lessons, colors)
        }
    }

    @Composable
    private fun WidgetRoot(state: ScheduleState, allLessons: List<Lesson>, c: WColors) {
        val isWide = LocalSize.current.width >= 200.dp
        val isTall = LocalSize.current.height >= 150.dp

        val ui = when (state) {
            is ScheduleState.BeforeLessons -> WidgetUi(
                status = "через ${state.minutesUntil} мин",
                headline = state.next.subject,
                detail = describe(state.next),
            )
            is ScheduleState.DuringLesson -> WidgetUi(
                status = if (state.minutesToEnd <= 1) "скоро звонок" else
                    "идёт · до конца ${state.minutesToEnd} мин",
                headline = state.current.subject,
                detail = describe(state.current) + (state.next?.let {
                    val room = if (it.room.isBlank()) "" else " в ${it.room}"
                    " · далее: ${it.subject}$room"
                } ?: ""),
                progress = state.current.progressAt(LocalDateTime.now().toLocalTime()),
            )
            is ScheduleState.DuringBreak -> WidgetUi(
                status = "перемена · осталось ${state.minutesUntil} мин",
                headline = "${state.next.subject}",
                detail = "Каб. ${state.next.room}" +
                    (state.next.floor?.let { " · $it этаж" } ?: "") +
                    (state.next.teacher?.let { " · $it" } ?: "") +
                    (state.next.note?.let { " · ⚠ замена" } ?: ""),
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
                .background(ColorProvider(c.bg))
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
                            color = ColorProvider(c.accent),
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
                        color = ColorProvider(c.textMain),
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
                            color = ColorProvider(c.textDim),
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
                if (ui.progress != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    LessonProgress(ui.progress, c)
                }
                if (isTall) {
                    Spacer(GlanceModifier.height(6.dp))
                    DayList(allLessons, c)
                }
            }
        }
    }

    @Composable
    private fun DayList(lessons: List<Lesson>, c: WColors) {
        val todayLessons = lessons
            .filter { it.weekday == LocalDate.now().dayOfWeek.value }
            .filter { NextLessonCalculator.parse(it.start) != null && NextLessonCalculator.parse(it.end) != null }
        if (todayLessons.isEmpty()) return

        val nowT = LocalDateTime.now().toLocalTime()
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // страховка от дублей: один урок на «предмет + время начала»
            todayLessons
                .sortedBy { NextLessonCalculator.parse(it.start) }
                .distinctBy { listOf(it.subject, it.start) }
                .take(12).forEach { lesson ->
                    DayRow(lesson, nowT, c)
                }
        }
    }

    /** Строка одного урока: идущий — синяя обводка, закончившийся — серый. */
    @Composable
    private fun DayRow(lesson: Lesson, nowT: LocalTime, c: WColors) {
        val startT = NextLessonCalculator.parse(lesson.start)!!
        val endT = NextLessonCalculator.parse(lesson.end)!!
        val isCurrent = !nowT.isBefore(startT) && nowT.isBefore(endT)
        val isPast = !nowT.isBefore(endT)

        val timeColor = if (isPast) c.textDim else c.textMain
        val subjectColor = if (isPast) c.textDim else c.textMain
        val roomColor = if (isPast) c.textDim else c.accent

        val row: @Composable () -> Unit = {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = "${lesson.start}–${lesson.end}",
                    style = TextStyle(color = ColorProvider(timeColor), fontSize = 10.sp),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = lesson.subject + (if (lesson.note != null) " ⚠" else ""),
                    style = TextStyle(
                        color = ColorProvider(subjectColor),
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = lesson.room,
                    style = TextStyle(color = ColorProvider(roomColor), fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }

        if (isCurrent) {
            // «обводка» в Glance: синий фон-рамка, внутри — бокс с фоном виджета
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .background(ColorProvider(c.currentBorder))
                    .cornerRadius(8.dp),
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(2.dp)
                        .background(ColorProvider(c.bg))
                        .cornerRadius(6.dp),
                ) {
                    row()
                }
            }
        } else {
            // закончившийся — весь ряд серый; будущий — обычный
            row()
        }
    }

    /** Тонкая полоска: доля прошедшего времени урока. */
    @Composable
    private fun LessonProgress(fraction: Float, c: WColors) {
        // Glance не умеет fillMaxWidth(доля) — считаем ширину сами (паддинг 10dp с двух сторон)
        val barWidth = LocalSize.current.width - 20.dp
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(3.dp)
                .background(ColorProvider(c.textDim)),
        ) {
            Box(
                modifier = GlanceModifier
                    .width(barWidth * fraction)
                    .height(3.dp)
                    .background(ColorProvider(c.accent)),
            ) {}
        }
    }

    private fun describe(lesson: Lesson): String =
        "Каб. ${lesson.room}" +
            (lesson.floor?.let { " · $it этаж" } ?: "") +
            (lesson.teacher?.let { " · $it" } ?: "") +
            (lesson.note?.let { " · ⚠ замена" } ?: "")

    /** Доля прошедшего времени урока (0.05..1), null — если время не распарсилось. */
    private fun Lesson.progressAt(now: LocalTime): Float? {
        val s = NextLessonCalculator.parse(start) ?: return null
        val e = NextLessonCalculator.parse(end) ?: return null
        val total = java.time.Duration.between(s, e).seconds
        if (total <= 0) return null
        val passed = java.time.Duration.between(s, now).seconds
        return (passed.toDouble() / total).coerceIn(0.05, 1.0).toFloat()
    }

    private data class WidgetUi(
        val status: String,
        val headline: String,
        val detail: String,
        val progress: Float? = null,
    )

    private data class WColors(
        val bg: Color,
        val accent: Color,
        val textMain: Color,
        val textDim: Color,
        /** Цвет обводки текущего (идущего) урока. */
        val currentBorder: Color,
    )

    // актуальные цвета подставляются в provideGlance в зависимости от темы (ThemePrefs)
    private val DarkColors = WColors(
        bg = Color(0xFF262B35),
        accent = Color(0xFF7CB342),
        textMain = Color(0xFFF2F4F8),
        textDim = Color(0xFF9AA3B0),
        currentBorder = Color(0xFF5EB4FF),
    )
    private val LightColors = WColors(
        bg = Color(0xFFF4F6F9),
        accent = Color(0xFF4E8A2F),
        textMain = Color(0xFF1C2128),
        textDim = Color(0xFF6B7482),
        currentBorder = Color(0xFF1E6FD9),
    )
}
