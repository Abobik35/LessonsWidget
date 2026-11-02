package com.nastlo.lessonswidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import com.nastlo.lessonswidget.logic.ScheduleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Отвечает за своевременное обновление виджета:
 * - refreshNow() — сразу после правок расписания;
 * - scheduleNext() — будильник в момент ближайшего начала/конца урока
 *   (в пределах завтра), fallback: системный updatePeriodMillis каждые 30 минут.
 */
object WidgetRefresher {

    const val ACTION_REFRESH = "com.nastlo.lessonswidget.REFRESH"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                LessonsWidget().updateAll(app)
            } finally {
                scheduleNext(app)
            }
        }
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        if (app !is LessonsApp) return
        scope.launch {
            try {
                val lessons = app.db.lessonDao().getAll()
                val now = LocalDateTime.now()
                val state = NextLessonCalculator.compute(lessons, now.toLocalDate(), now.toLocalTime())
                val transition = nextTransitionMillis(lessons, now)

                // Пока виден живой отсчёт («осталось N мин») — тикаем раз в минуту,
                // чтобы число не зависало. Границы уроков остаются точками точности.
                // Утром/вечером (уроки кончились) тикать нечего — спим до ближайшей границы.
                val millis = if (countdownVisible(state)) {
                    val nextTick = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
                    val tickMillis = nextTick.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (transition == null) tickMillis else minOf(transition, tickMillis)
                } else {
                    transition ?: return@launch
                }
                scheduleAlarm(app, millis)
            } catch (_: Exception) {
                // пустая база и т.п. — тихо ждём системного обновления
            }
        }
    }

    /** Отсчёт минут виден у этих состояний — их и «тикаем» каждую минуту. */
    private fun countdownVisible(state: ScheduleState): Boolean =
        state is ScheduleState.BeforeLessons ||
            state is ScheduleState.DuringLesson ||
            state is ScheduleState.DuringBreak

    /** Время ближайшего будущего обновления: начало или конец любого урока сегодня/завтра. */
    private fun nextTransitionMillis(lessons: List<Lesson>, now: LocalDateTime): Long? {
        val candidates = mutableListOf<LocalDateTime>()

        for (offset in 0L..1L) {
            val date = now.toLocalDate().plusDays(offset)
            lessons
                .filter { it.weekday == date.dayOfWeek.value }
                .forEach { lesson ->
                    NextLessonCalculator.parse(lesson.start)?.let {
                        val t = LocalDateTime.of(date, it)
                        if (t.isAfter(now)) candidates += t
                    }
                    NextLessonCalculator.parse(lesson.end)?.let {
                        val t = LocalDateTime.of(date, it)
                        if (t.isAfter(now)) candidates += t
                    }
                }
        }

        val next = candidates.minOrNull() ?: return null
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun scheduleAlarm(context: Context, millis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, LessonsWidgetReceiver::class.java).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) {
            // Пользователь не выдал разрешение на точные будильники —
            // ставим неточную: сработает в течение ~10 минут, для виджета это ок.
            am.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, pi)
        }
    }
}
