package com.nastlo.lessonswidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.widget.WidgetRefresher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as LessonsApp).db.lessonDao()

    val lessons: StateFlow<List<Lesson>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(lesson: Lesson) = viewModelScope.launch {
        dao.upsert(lesson)
        WidgetRefresher.refreshNow(getApplication())
    }

    fun delete(lesson: Lesson) = viewModelScope.launch {
        dao.delete(lesson)
        WidgetRefresher.refreshNow(getApplication())
    }
}
