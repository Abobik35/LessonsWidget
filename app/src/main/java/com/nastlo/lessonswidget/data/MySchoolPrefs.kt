package com.nastlo.lessonswidget.data

import android.content.Context
import com.nastlo.lessonswidget.logic.ParsedStudent

/**
 * Сохранённый аккаунт «Моя Школа» для фонового обновления замен.
 * Сам токен не храним: куки остаются только в хранилище WebView приложения.
 */
object MySchoolPrefs {
    const val FILE = "myschool_prefs"
    const val KEY_PERSON = "person_id"
    const val KEY_USER = "user_id"
    const val KEY_PROFILE = "profile_id"
    const val KEY_ROLE = "role"

    data class SavedAccount(
        val personId: String,
        val userId: Long,
        val profileId: Long?,
        val role: String?,
    )

    fun save(context: Context, student: ParsedStudent?, profileId: Long?, role: String?) {
        if (student == null) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PERSON, student.personId)
            .putLong(KEY_USER, student.userId)
            .putLong(KEY_PROFILE, profileId ?: -1L)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun load(context: Context): SavedAccount? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val person = prefs.getString(KEY_PERSON, null) ?: return null
        return SavedAccount(
            personId = person,
            userId = prefs.getLong(KEY_USER, 0L),
            profileId = prefs.getLong(KEY_PROFILE, -1L).takeIf { it > 0 },
            role = prefs.getString(KEY_ROLE, null),
        )
    }

    /**
     * «Рабочий вариант» запроса деталей урока (0..3): однажды найдя удачный,
     * дальнейшие обновления не перебирают все варианты — сразу берут этот.
     * −1 — ещё не найден.
     */
    const val KEY_VARIANT = "enrich_variant"

    fun loadVariant(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_VARIANT, -1)

    fun saveVariant(context: Context, variant: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_VARIANT, variant)
            .apply()
    }
}
