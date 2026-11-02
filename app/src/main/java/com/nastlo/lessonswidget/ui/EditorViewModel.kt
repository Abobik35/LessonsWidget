package com.nastlo.lessonswidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.data.MySchoolException
import com.nastlo.lessonswidget.data.ThemePrefs
import com.nastlo.lessonswidget.data.UpdatePrefs
import com.nastlo.lessonswidget.logic.MySchoolQuickRefresh
import com.nastlo.lessonswidget.widget.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as LessonsApp).db.lessonDao()

    val lessons: StateFlow<List<Lesson>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Режим темы (0 системная / 1 тёмная / 2 светлая) — наблюдается в MainActivity. */
    val themeMode = MutableStateFlow(ThemePrefs.load(app))

    fun setTheme(mode: Int) {
        ThemePrefs.save(getApplication(), mode)
        themeMode.value = mode
        // виджет перекрашиваем сразу
        WidgetRefresher.refreshNow(getApplication())
    }

    fun save(lesson: Lesson) = viewModelScope.launch {
        dao.upsert(lesson)
        refreshWidget()
        // контрольный повтор: часть лаунчеров «съедает» первую перерисовку
        kotlinx.coroutines.delay(300)
        refreshWidget()
    }

    fun delete(lesson: Lesson) = viewModelScope.launch {
        dao.delete(lesson)
        refreshWidget()
        kotlinx.coroutines.delay(300)
        refreshWidget()
    }

    /** Импорт расписания из внешнего источника (например, «Моя Школа»). */
    fun importLessons(newLessons: List<Lesson>, clearExisting: Boolean) = viewModelScope.launch {
        if (clearExisting) dao.clearAll()
        newLessons.forEach { dao.upsert(it) }
        UpdatePrefs.saveNow(getApplication())
        refreshWidget()
        kotlinx.coroutines.delay(300)
        refreshWidget()
    }

    /** Немедленная перерисовка виджета (данные уже в базе). */
    fun refreshWidget() = viewModelScope.launch {
        WidgetRefresher.refreshNow(getApplication())
    }

    /** Идёт быстрое обновление расписания по кнопке ⟳ / в настройках. */
    val refreshing = MutableStateFlow(false)

    /** Сообщение-результат (для тоста). */
    val refreshMessage = MutableStateFlow<String?>(null)

    /** Быстрое обновление «Моя Школа» в один тап: без входа и подтверждений. */
    fun quickRefresh() = viewModelScope.launch {
        if (refreshing.value) return@launch
        refreshing.value = true
        refreshMessage.value = try {
            val n = MySchoolQuickRefresh.refreshAndReplace(getApplication())
            // учителя догружаем фоном: сначала сегодня, потом последующие дни
            launch {
                try {
                    MySchoolQuickRefresh.enrichTeachers(getApplication())
                } catch (_: Exception) {
                    // фоновая догрузка тихая — основной результат уже показан
                }
            }
            "Обновлено: $n уроков · учителя догружаются"
        } catch (e: MySchoolException) {
            when (e.httpCode) {
                401 -> "Вход истёк — нажми «Импорт» и перелогинься один раз"
                in 500..599 -> "«Моя Школа» недоступна (HTTP ${e.httpCode}) — попробуй позже"
                else -> "«Моя Школа»: HTTP ${e.httpCode}"
            }
        } catch (e: Exception) {
            e.message?.take(160) ?: "Не удалось обновить"
        }
        refreshing.value = false
    }

    fun clearRefreshMessage() {
        refreshMessage.value = null
    }
}
