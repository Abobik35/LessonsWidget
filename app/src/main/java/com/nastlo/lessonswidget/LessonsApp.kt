package com.nastlo.lessonswidget

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nastlo.lessonswidget.data.AppDatabase
import com.nastlo.lessonswidget.worker.MorningUpdater
import com.nastlo.lessonswidget.worker.ReplacementsWorker
import java.util.concurrent.TimeUnit

class LessonsApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        // Ежедневное тихое обновление замен по текущей неделе
        try {
            val request = PeriodicWorkRequestBuilder<ReplacementsWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "replacements_refresh",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } catch (_: Exception) {
            // WorkManager не инициализировался — не критично, замены обновятся при импорте
        }
        // Точный будильник каждый день в 6:45 (окно 6:30–7:00)
        try {
            MorningUpdater.scheduleNext(this)
        } catch (_: Exception) {
            // нет разрешения на будильники — остаётся WorkManager-страховка
        }
    }
}
