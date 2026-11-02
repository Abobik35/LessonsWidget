package com.nastlo.lessonswidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.data.MySchoolApi
import com.nastlo.lessonswidget.data.MySchoolException
import com.nastlo.lessonswidget.data.MySchoolPrefs
import com.nastlo.lessonswidget.logic.MySchoolParser
import com.nastlo.lessonswidget.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Раз в день тихо сверяет замены учителей на текущую неделю и ставит/снимает
 * пометку «Замена учителя» у соответствующих уроков в базе.
 * Токен не храним: читаем куки из хранилища WebView (то, чем пользователь залогинен).
 */
class ReplacementsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LessonsApp
        val account = MySchoolPrefs.load(app) ?: return Result.success()
        val webCookies = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(MySchoolApi.WEB)
        }
        val token = MySchoolApi.tokenFromCookie(webCookies) ?: return Result.success()

        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return try {
            val api = MySchoolApi(token)
            val root = api.events(
                personId = account.personId,
                role = account.role,
                from = monday,
                to = monday.plusDays(6),
                cookieHeader = webCookies,
            )
            applyReplacements(app, root)
            Result.success()
        } catch (e: MySchoolException) {
            if (e.httpCode in 500..599) Result.retry() else Result.success()
        } catch (e: Exception) {
            // сеть/сервер недоступны — просто попробуем в следующий раз
            Result.success()
        }
    }

    private suspend fun applyReplacements(app: LessonsApp, root: Any) {
        val replacedKeys = MySchoolParser.parseEvents(root)
            .filter { it.lesson.weekday <= 5 && it.replaced }
            .map {
                // ключ без конца урока — как в MySchoolQuickRefresh/импорте:
                // у «факт»-событий время конца иногда чуть другое
                listOf(it.lesson.weekday, it.lesson.subject, it.lesson.start).joinToString("|")
            }
            .toHashSet()

        val dao = app.db.lessonDao()
        var changed = 0
        val updated = dao.getAll().map { lesson ->
            val key = listOf(lesson.weekday, lesson.subject, lesson.start).joinToString("|")
            val hasReplacement = replacedKeys.contains(key)
            val newNote = when {
                hasReplacement && lesson.note.isNullOrBlank() -> "Замена учителя"
                !hasReplacement && lesson.note == "Замена учителя" -> null
                else -> lesson.note // свои заметки пользователя не трогаем
            }
            val mapped = lesson.copy(note = newNote)
            if (mapped.note != lesson.note) changed++
            mapped
        }
        if (changed > 0) {
            updated.forEach { dao.upsert(it) }
            WidgetRefresher.refreshNow(app)
        }
    }
}
