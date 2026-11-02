package com.nastlo.lessonswidget.logic

import com.nastlo.lessonswidget.data.Lesson
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Текущее состояние расписания относительно момента времени «сейчас». */
sealed interface ScheduleState {
    /** Уроков на сегодня нет. */
    data object NothingPlanned : ScheduleState

    /** До ближайшего урока ещё есть время. */
    data class BeforeLessons(val next: Lesson, val minutesUntil: Long) : ScheduleState

    /** Урок идёт прямо сейчас. */
    data class DuringLesson(
        val current: Lesson,
        val minutesToEnd: Long,
        val next: Lesson?,
    ) : ScheduleState

    /** Перемена: предыдущий урок закончился, до следующего осталось N минут. */
    data class DuringBreak(
        val next: Lesson,
        val minutesUntil: Long,
        val previous: Lesson,
    ) : ScheduleState

    /** Все уроки на сегодня прошли. */
    data class AfterLessons(val last: Lesson) : ScheduleState
}

object NextLessonCalculator {

    /**
     * Чистая логика без Android-зависимостей: легко покрыть тестами.
     * День недели берётся из [today]: LocalDate.dayOfWeek.value (1..7, Пн..Вс).
     */
    fun compute(lessons: List<Lesson>, today: LocalDate, now: LocalTime): ScheduleState {
        fun Lesson.startT(): LocalTime = parse(start) ?: throw IllegalArgumentException(start)
        fun Lesson.endT(): LocalTime = parse(end) ?: throw IllegalArgumentException(end)

        val todayLessons = lessons
            .filter { it.weekday == today.dayOfWeek.value }
            .filter { parse(it.start) != null && parse(it.end) != null }
            .sortedBy { parse(it.start) }

        if (todayLessons.isEmpty()) return ScheduleState.NothingPlanned

        // Идёт ли сейчас какой-то урок
        val current = todayLessons.firstOrNull {
            !now.isBefore(it.startT()) && now.isBefore(it.endT())
        }
        if (current != null) {
            val index = todayLessons.indexOf(current)
            val next = todayLessons.getOrNull(index + 1)
            val minutesToEnd = Duration.between(now, current.endT()).toMinutes().coerceAtLeast(0)
            return ScheduleState.DuringLesson(current, minutesToEnd, next)
        }

        // Ближайший будущий урок сегодня
        val next = todayLessons.firstOrNull { now.isBefore(it.startT()) }
        if (next != null) {
            val index = todayLessons.indexOf(next)
            val previous = if (index > 0) todayLessons[index - 1] else null
            val minutesUntil = Duration.between(now, next.startT()).toMinutes()
            return if (previous != null && !now.isBefore(previous.endT())) {
                ScheduleState.DuringBreak(next, minutesUntil, previous)
            } else {
                ScheduleState.BeforeLessons(next, minutesUntil)
            }
        }

        return ScheduleState.AfterLessons(todayLessons.last())
    }

    /** Безопасный парс "HH:mm". Возвращает null для мусора. */
    fun parse(hhmm: String): LocalTime? = try {
        LocalTime.parse(hhmm.trim())
    } catch (_: Exception) {
        null
    }
}
