package com.nastlo.lessonswidget

import android.app.Application
import com.nastlo.lessonswidget.data.AppDatabase

class LessonsApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
}
