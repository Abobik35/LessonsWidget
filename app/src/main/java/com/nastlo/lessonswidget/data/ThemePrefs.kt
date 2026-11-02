package com.nastlo.lessonswidget.data

import android.content.Context
import android.content.res.Configuration

/**
 * Режим темы для приложения и виджета.
 * MODE_SYSTEM — следовать системной, MODE_DARK — тёмная, MODE_LIGHT — светлая.
 * По умолчанию тёмная — как виджет выглядел изначально.
 */
object ThemePrefs {
    const val FILE = "theme_prefs"
    const val KEY_MODE = "mode"
    const val MODE_SYSTEM = 0
    const val MODE_DARK = 1
    const val MODE_LIGHT = 2

    fun load(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_MODE, MODE_DARK)

    fun save(context: Context, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MODE, mode)
            .apply()
    }

    /** Тёмная ли тема с учётом режима «как в системе». */
    fun isDark(context: Context): Boolean = when (load(context)) {
        MODE_LIGHT -> false
        MODE_SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        else -> true
    }
}
