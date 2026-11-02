package com.nastlo.lessonswidget.logic

import android.content.Context
import android.webkit.CookieManager
import com.nastlo.lessonswidget.LessonsApp
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.data.MySchoolApi
import com.nastlo.lessonswidget.data.MySchoolException
import com.nastlo.lessonswidget.data.MySchoolPrefs
import com.nastlo.lessonswidget.data.UpdatePrefs
import com.nastlo.lessonswidget.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.atomic.AtomicInteger

/**
 * Быстрое обновление «в один тап», без входа в аккаунт и подтверждений.
 *
 * Ход 1 (быстро, ~1–2 сек): 1 запрос событий за окно Пн−7 … Пн+21,
 * те же правила, что у импорта — без субботы, склейка чередующихся кабинетов,
 * пометки замен; расписание молча заменяется целиком. Учителя подставляются
 * из прежней версии, свои заметки пользователя переживают обновление.
 *
 * Ход 2 (фоном): дотягиваем учителя свежими запросами в порядке дней
 * «сегодня → завтра → …» (Пн 2-го — сначала уроки именно 2-го). Рабочий
 * вариант запроса запоминается в prefs — дальше 1 запрос на урок, без перебора.
 */
object MySchoolQuickRefresh {

    private data class Session(
        val api: MySchoolApi,
        val apiCookies: String?,
        val webCookies: String?,
        val account: MySchoolPrefs.SavedAccount,
    )

    /** Авторизация из сохранённого аккаунта + кук WebView; ошибки — понятными словами. */
    private suspend fun auth(context: Context): Session {
        val app = context.applicationContext as LessonsApp
        val account = MySchoolPrefs.load(app)
            ?: throw IllegalStateException(
                "Сначала один раз открой «Импорт» и войди — потом расписание обновляется по этой кнопке",
            )
        val webCookies = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(MySchoolApi.WEB)
        }
        val apiCookies = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(MySchoolApi.API)
        }
        val token = MySchoolApi.tokenFromCookie(webCookies)
            ?: throw MySchoolException(401, "Вход в «Моя Школа» истёк — открой «Импорт» и перелогинься один раз")
        return Session(MySchoolApi(token), apiCookies, webCookies, account)
    }

    private fun window(): Pair<LocalDate, LocalDate> {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.minusDays(7) to monday.plusDays(21)
    }

    /** События за окно (один запрос), суббота выкинута. */
    private suspend fun fetchEvents(s: Session): List<ParsedEvent> {
        val (from, to) = window()
        val root = s.api.events(s.account.personId, s.account.role, from, to, s.webCookies)
        val events = MySchoolParser.parseEvents(root).filter { it.lesson.weekday <= 5 }
        if (events.isEmpty()) throw IllegalStateException("Сервер не вернул событий на $from…$to")
        return events
    }

    /**
     * Ход 1: заменяет расписание сеткой недели (без учителей — но старые
     * учителя переносятся по ключу день+предмет+время, как и свои заметки).
     * Возвращает число уроков.
     */
    suspend fun refreshAndReplace(context: Context): Int {
        val app = context.applicationContext as LessonsApp
        val s = auth(context)
        val dao = app.db.lessonDao()
        val old = dao.getAll()

        val oldNotes = old
            .filter { !it.note.isNullOrBlank() && it.note != "Замена учителя" }
            .associateBy({ key(it) }) { it.note!! }
        val oldTeachers = old
            .filter { !it.teacher.isNullOrBlank() }
            .associateBy({ key(it) }) { it.teacher!! }

        val events = fetchEvents(s)
        val today = LocalDate.now()

        val best = LinkedHashMap<String, Lesson>()
        val roomCounts = LinkedHashMap<String, LinkedHashMap<String, Int>>()
        val replacedAny = LinkedHashMap<String, Boolean>()
        // замены считаем только по текущей неделе (Пн…Вс): иначе замена из
        // прошлой/будущей недели «прилипает» к такому же уроку на весь месяц
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = monday.plusDays(6)
        // слот повторяется каждую неделю — шаблоном берём самое актуальное
        // событие (сегодня/ближайшее будущее): его учитель и id
        val templates = LinkedHashMap<String, ParsedEvent>()
        for (e in events) {
            val key = key(e.lesson)
            val cur = templates[key]
            if (cur == null || MySchoolParser.isNewerEvent(e, cur, today)) templates[key] = e
            e.lesson.room.takeIf { it.isNotBlank() }?.let { room ->
                roomCounts.getOrPut(key) { LinkedHashMap() }.merge(room, 1, Int::plus)
            }
            if (e.replaced && e.date in monday..weekEnd) replacedAny[key] = true
        }
        for ((key, e) in templates) best[key] = e.lesson

        for (key in best.keys) {
            val lesson = best[key] ?: continue
            val counts = roomCounts[key]
            val joined = counts?.entries
                ?.sortedByDescending { it.value }
                ?.joinToString(" / ") { it.key }
                ?.takeIf { it.isNotBlank() }
            when {
                joined != null && counts.size > 1 ->
                    best[key] = lesson.copy(room = joined)
                lesson.room.isBlank() -> {
                    val single = counts?.keys?.firstOrNull()
                    if (single != null) best[key] = lesson.copy(room = single)
                }
            }
        }
        for ((key, _) in replacedAny) {
            val existing = best[key] ?: continue
            if (existing.note.isNullOrBlank()) {
                best[key] = existing.copy(note = "Замена учителя")
            }
        }

        val fresh = best.values.toList()
            .sortedBy { it.weekday * 10_000 + (NextLessonCalculator.parse(it.start)?.toSecondOfDay() ?: 0) }
            .map {
                it.copy(
                    note = it.note ?: oldNotes[key(it)],
                    teacher = it.teacher ?: oldTeachers[key(it)],
                )
            }

        dao.clearAll()
        fresh.forEach { dao.upsert(it) }
        UpdatePrefs.saveNow(app)
        WidgetRefresher.refreshNow(app)
        return fresh.size
    }

    /**
     * Ход 2: дотягивает учителей фоновой догрузкой — в порядке дней
     * «сегодня, завтра, …» (из 5 учебных дней). Параллелизм умеренный (4),
     * после каждого дня результаты уже в базе и виджет обновлён.
     * Возвращает число обновлённых уроков.
     */
    suspend fun enrichTeachers(context: Context): Int {
        val app = context.applicationContext as LessonsApp
        val s = auth(context)
        val dao = app.db.lessonDao()
        val events = fetchEvents(s)

        // ключ слота → (id урока, тип). Слот повторяется каждую неделю —
        // берём id самого актуального события (сегодня/ближайшее будущее),
        // иначе детали приходят от прошлой недели и учитель «до замены».
        val today = LocalDate.now()
        val templates = LinkedHashMap<String, ParsedEvent>()
        for (e in events) {
            val key = key(e.lesson)
            val cur = templates[key]
            if (cur == null || MySchoolParser.isNewerEvent(e, cur, today)) templates[key] = e
        }
        val slots = LinkedHashMap<String, Pair<Long, String?>>()
        for ((key, e) in templates) {
            e.eventId?.let { id -> slots[key] = id to e.source }
        }
        if (slots.isEmpty()) return 0

        val todayWd = LocalDate.now().dayOfWeek.value
        // сегодня → завтра → … → позавчера (цикл из 5 дней)
        val dayOrder = (0..4).map { ((todayWd - 1 + it) % 5) + 1 }

        val variantHolder = AtomicInteger(MySchoolPrefs.loadVariant(app))
        var enrichedCount = 0

        for (wd in dayOrder) {
            val dayLessons = dao.getAll()
                .filter {
                    it.weekday == wd &&
                        NextLessonCalculator.parse(it.start) != null &&
                        NextLessonCalculator.parse(it.end) != null &&
                        slots.containsKey(key(it))
                }
            if (dayLessons.isEmpty()) continue

            val sem = Semaphore(4)
            val results = withContext(Dispatchers.IO) {
                dayLessons.map { lesson ->
                    async {
                        sem.withPermit {
                            enrichOne(s, slots, variantHolder, lesson)
                        }
                    }
                }.awaitAll()
            }

            dayLessons.forEachIndexed { index, oldLesson ->
                (results.getOrNull(index))?.let { fresh ->
                    dao.upsert(fresh)
                    enrichedCount++
                }
            }
            // группа дня готова — виджет показывает учителей сразу
            WidgetRefresher.refreshNow(app)
        }

        variantHolder.get().takeIf { it in 0..3 }?.let {
            MySchoolPrefs.saveVariant(app, it)
        }
        return enrichedCount
    }

    /** Один урок: 1 запрос по запомненному варианту, при сбое — лестница из 4. */
    private suspend fun enrichOne(
        s: Session,
        slots: Map<String, Pair<Long, String?>>,
        variantHolder: AtomicInteger,
        lesson: Lesson,
    ): Lesson? {
        val (eventId, type) = slots.getValue(key(lesson))
        val urlType = type ?: "PLAN"
        val cached = variantHolder.get()
        val order = buildList {
            if (cached in 0..3) add(cached)
            for (i in 0 until 4) if (i != cached) add(i)
        }
        for (variant in order) {
            val url = variantUrl(variant, s, eventId, urlType)
            try {
                val raw = s.api.rawGet(url.first, url.second, null, url.third)
                val detail = MySchoolParser.parseLessonItem(raw) ?: continue
                // этот вариант рабочий — запоминаем (перезапись атомарная, гонка не страшна)
                variantHolder.set(variant)
                return lesson.copy(
                    teacher = detail.teacher ?: lesson.teacher,
                    room = lesson.room.ifBlank { detail.room },
                )
            } catch (_: Exception) {
                // пробуем следующий вариант
            }
        }
        return null
    }

    private fun variantUrl(
        variant: Int,
        s: Session,
        eventId: Long,
        type: String,
    ): Triple<String, Long?, String> {
        val itemUrl = MySchoolApi.API + "/family/mobile/v1/lesson_schedule_items/$eventId"
        return when (variant) {
            0 -> Triple("$itemUrl?student_id=${s.account.userId}&type=$type", s.account.profileId, s.apiCookies!!)
            1 -> Triple("$itemUrl?student_id=${s.account.userId}&type=$type", null, s.apiCookies!!)
            2 -> Triple("$itemUrl?student_id=${s.account.profileId ?: s.account.userId}&type=$type", s.account.profileId, s.apiCookies!!)
            else -> Triple("$itemUrl?student_id=${s.account.userId}&type=$type", s.account.profileId, s.webCookies!!)
        }
    }

    private fun key(l: Lesson): String =
        // конец времени не включаем: у «факт»-событий (EC) он иногда на пару
        // минут отличается от планового — иначе один урок раздваивается
        listOf(l.weekday, l.subject, l.start).joinToString("|")
}
