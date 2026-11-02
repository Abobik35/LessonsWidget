package com.nastlo.lessonswidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.update
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Отвечает за своевременное обновление виджета:
 * - updateAll()  — сразу после правок расписания;
 * - scheduleNext() — будильник в момент ближайшего начала/конца урока
 *   (в пределах завтра), при отсутствии уроков — не ставится,
 *   fallback: системный updatePeriodMillis каждые 30 минут.
 */
object WidgetRefresher {

    const val ACTION_REFRESH = "com.nastlo.lessonswidget.REFRESH"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                LessonsWidget().update(app)
            } finally {
                scheduleNext(app)
            }
        }
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        if (app !is LessonsApp) return
        scope.launch {
            val millis = nextTransitionMillis(app) ?: return@launch
            scheduleAlarm(app, millis)
        }
    }

    /** Время ближайшего будущего обновления: начало или конец любого урока сегодня/завтра. */
    private suspend fun nextTransitionMillis(app: LessonsApp): Long? {
        val lessons = app.db.lessonDao().getAll()
        val now = LocalDateTime.now()
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
