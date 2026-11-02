package com.nastlo.lessonswidget.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Время последнего успешного обновления дневника («Моя Школа»). */
object UpdatePrefs {
    const val FILE = "update_prefs"
    const val KEY_LAST = "last_diary_update"

    fun loadLast(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    fun saveNow(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST, System.currentTimeMillis())
            .apply()
    }

    fun saveAt(context: Context, millis: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST, millis)
            .apply()
    }

    /** «сегодня в 15:31» / «вчера в 19:48» / «3 ноя, 15:31» / «ещё не было». */
    fun text(context: Context): String {
        val millis = loadLast(context)
        if (millis <= 0) return "ещё не было"
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        val today = LocalDate.now()
        val time = DateTimeFormatter.ofPattern("HH:mm").format(dt)
        return when (dt.toLocalDate()) {
            today -> "сегодня в $time"
            today.minusDays(1) -> "вчера в $time"
            today.plusDays(1) -> "завтра в $time"
            else -> dt.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")))
        }
    }
}
