package com.nastlo.lessonswidget.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ежедневное обновление замен в 6:45 (диапазон «6:30–7:00»).
 * Точный будильник AlarmManager, который сам перепланирует себя на завтра.
 * В воркере делает всю работу ReplacementsWorker.
 */
object MorningUpdater {
    const val ACTION_MORNING_UPDATE = "com.nastlo.lessonswidget.MORNING_UPDATE"
    const val MORNING_HOUR = 6
    const val MORNING_MINUTE = 45

    fun scheduleNext(context: Context) {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), LocalTime.of(MORNING_HOUR, MORNING_MINUTE))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MorningReceiver::class.java).setAction(ACTION_MORNING_UPDATE)
        val pi = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) {
            // нет разрешения на точные будильники — окно ~10 минут всё равно в пределах 6:30–7:00
            am.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, pi)
        }
    }
}

/** Будильник в 6:45: запускает воркер замен и ставит сами-перепланировку на завтра. */
class MorningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                MorningUpdater.ACTION_MORNING_UPDATE -> {
                    val request = OneTimeWorkRequestBuilder<ReplacementsWorker>().build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "replacements_morning",
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                    MorningUpdater.scheduleNext(context)
                }
                // После перезагрузки телефона будильники не живут — планируем заново
                Intent.ACTION_BOOT_COMPLETED -> MorningUpdater.scheduleNext(context)
            }
        } catch (_: Exception) {
            // WorkManager может не успеть инициализироваться — попробует завтра
            MorningUpdater.scheduleNext(context)
        }
    }
}
