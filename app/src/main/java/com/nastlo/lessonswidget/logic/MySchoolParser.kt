package com.nastlo.lessonswidget.logic

import com.nastlo.lessonswidget.data.Lesson
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class ParsedStudent(
    val userId: Long,
    val label: String,
    val schoolName: String? = null,
    val className: String? = null,
    /** UUID ученика (contingent_guid) — нужен эндпоинту events. */
    val personId: String? = null,
)

data class ParsedProfile(
    val profileId: Long?,
    val students: List<ParsedStudent>,
    /** Тип аккаунта (student/parent) — уходит в заголовок x-mes-role. */
    val role: String? = null,
)

/** Урок «события» с id (id используется, чтобы дотянуть учителя по API). */
data class ParsedEvent(
    val eventId: Long?,
    val lesson: Lesson,
    val source: String? = null,
    /** Замена учителя (replaced=true у события). */
    val replaced: Boolean = false,
    /** Дата события (из start_at/beginDate) — чтобы помечать замены только конкретной недели. */
    val date: LocalDate? = null,
)

/**
 * Парсер JSON «Моя Школа». API отдаёт разнобой camelCase/snake_case в разных
 * сервисах, поэтому ключи ищем по нескольким вариантам имён. Структуру гарантий
 * нет — парсим максимально толерантно (глубокий обход + вариантные ключи).
 *
 * Основа схемы: OctoDiary-py (MIT), папка types/mobile.
 */
object MySchoolParser {

    // ---------- Профиль / дети ----------

    fun parseProfile(root: Any): ParsedProfile {
        if (root !is JSONObject) return ParsedProfile(null, emptyList())
        val profileObj = root.optJSONObject("profile")
        val profileId = profileObj?.let { idOf(firstOf(it, "id")) }
        val selfUserId = profileObj?.let { idOf(firstOf(it, "userId", "user_id")) }
        val selfName = profileObj?.let { humanName(it) }
        val role = profileObj?.optString("type")?.takeIf { s -> s.isNotBlank() }

        val children = mutableListOf<ParsedStudent>()
        val arr = root.optJSONArray("children")
        arr?.let {
            for (i in 0 until it.length()) {
                val c = it.optJSONObject(i) ?: continue
                val uid = idOf(firstOf(c, "userId", "user_id")) ?: continue
                val className = firstString(c, "className", "class_name")
                val school = c.optJSONObject("school")?.let { s ->
                    firstString(s, "short_name", "shortName", "name")
                }
                val personId = firstString(c, "contingent_guid", "contingentGuid", "personId", "person_id")
                val name = humanName(c) ?: "Ребёнок #$uid"
                children += ParsedStudent(
                    userId = uid,
                    label = if (className != null) "$name · $className" else name,
                    schoolName = school,
                    className = className,
                    personId = personId,
                )
            }
        }

        // если взрослых-детей нет — профиль сам ученик
        val students = if (children.isEmpty()) {
            val uid = selfUserId ?: return ParsedProfile(profileId, emptyList())
            listOf(ParsedStudent(uid, selfName ?: "Ученик #$uid"))
        } else children

        return ParsedProfile(profileId, students.distinctBy { it.userId }, role)
    }

    /** /v1/user/childrens: entities с person_id (uuid) — запасной источник uuid. */
    fun parseChildrens(root: Any): List<ParsedStudent> {
        if (root !is JSONObject) return emptyList()
        val entities = root.optJSONArray("entities") ?: return emptyList()
        val out = mutableListOf<ParsedStudent>()
        for (i in 0 until entities.length()) {
            val e = entities.optJSONObject(i) ?: continue
            val personId = firstString(e, "person_id", "personId") ?: continue
            val uid = idOf(firstOf(e, "id", "userId", "user_id")) ?: 0L
            val className = e.optJSONArray("education")?.optJSONObject(0)
                ?.optJSONObject("class")?.optString("name")?.takeIf { it.isNotBlank() }
            val name = humanName(e) ?: "Ученик"
            out += ParsedStudent(
                userId = uid,
                label = if (className != null) "$name · $className" else name,
                className = className,
                personId = personId,
            )
        }
        return out.distinctBy { it.personId }
    }

    private fun humanName(obj: JSONObject): String? {
        val last = obj.optString("last_name", obj.optString("lastname", ""))
        val first = obj.optString("first_name", obj.optString("firstname", ""))
        val middle = obj.optString("middle_name", obj.optString("patronymic", ""))
        val raw = buildString {
            if (last.isNotBlank()) append(last)
            if (first.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(first.firstOrNull()?.toString()?.plus('.') ?: "")
            }
            if (middle.isNotBlank()) {
                append(' ')
                append(middle.firstOrNull()?.toString()?.plus('.') ?: "")
            }
        }.trim()
        return raw.takeIf { it.isNotBlank() }
    }

    // ---------- Расписание: основной проход ----------

    /** events от periods_schedules. Возвращает уроки с eventId (если есть). */
    fun parseEvents(root: Any): List<ParsedEvent> {
        val found = mutableListOf<ParsedEvent>()
        walkWithAncestors(root, emptyList()) { node, ancestors ->
            if (node is JSONObject) {
                val hasTime = anyOf(node, "startAt", "start_at", "startTime", "beginTime", "begin_time")
                if (hasTime) {
                    eventFrom(node, ancestors)?.let { found += it }
                }
            }
        }
        return found
            .distinctBy {
                listOf(
                    it.lesson.weekday,
                    it.lesson.subject,
                    it.lesson.room,
                    it.lesson.start,
                    it.lesson.end,
                    it.date?.toString() ?: "",
                )
            }
            .sortedBy {
                it.lesson.weekday * 10_000 + (NextLessonCalculator.parse(it.lesson.start)?.toSecondOfDay() ?: 0)
            }
    }

    /**
     * Какое из повторяющихся событий одного слота (слот = день+предмет+время,
     * повторяется каждую неделю) считать «текущим»: ближайшая будущая/сегодняшняя
     * дата, а среди прошлых — самая поздняя. Именно у такого события берём
     * актуального учителя и id для дотягивания деталей.
     * Факт-событие (EC, «фактическое расписание») всегда старше планового (PLAN):
     * если для дня есть оба — берём факт.
     */
    fun isNewerEvent(e: ParsedEvent, cur: ParsedEvent, today: LocalDate): Boolean {
        val eActual = e.source?.equals("EC", ignoreCase = true) == true
        val cActual = cur.source?.equals("EC", ignoreCase = true) == true
        if (eActual != cActual) return eActual
        val ed = e.date ?: return false
        val cd = cur.date ?: return true
        val eFuture = !ed.isBefore(today)
        val cFuture = !cd.isBefore(today)
        return when {
            eFuture && !cFuture -> true
            !eFuture && cFuture -> false
            eFuture && cFuture -> ed < cd // из будущих — ближайшая
            else -> ed > cd               // из прошлых — самая поздняя
        }
    }

    private fun eventFrom(node: JSONObject, ancestors: List<JSONObject>): ParsedEvent? {
        if (node.optBoolean("cancelled", false)) return null
        val start = toHhmm(firstOf(node, "startAt", "start_at", "startTime", "beginTime", "begin_time")) ?: return null
        val end = toHhmm(firstOf(node, "finishAt", "finish_at", "finishTime", "endTime", "end_time")) ?: defaultEnd(start)
        val date = extractDate(node, ancestors) ?: return null
        val subject = extractSubject(node) ?: return null

        return ParsedEvent(
            eventId = idOf(firstOf(node, "id", "lessonId")),
            lesson = Lesson(
                weekday = date.dayOfWeek.value,
                subject = subject,
                room = extractRoom(node) ?: "",
                floor = null,
                teacher = extractTeacher(node),
                note = null,
                start = start,
                end = end,
            ),
            source = firstString(node, "source", "sourceType", "type_of_source"),
            replaced = node.optBoolean("replaced", false),
            date = date,
        )
    }

    /** lesson_schedule_items/{id}: LessonScheduleItem — учитель и точное время. */
    fun parseLessonItem(root: Any): Lesson? {
        if (root !is JSONObject) return null
        val start = toHhmm(firstOf(root, "beginTime", "begin_time", "startAt", "start_at", "startTime")) ?: return null
        val end = toHhmm(firstOf(root, "endTime", "end_time", "finishAt", "finish_at")) ?: defaultEnd(start)
        val date = extractDate(root, emptyList()) ?: return null
        val subject = extractSubject(root) ?: return null

        return Lesson(
            weekday = date.dayOfWeek.value,
            subject = subject,
            room = extractRoom(root) ?: "",
            floor = null,
            teacher = extractTeacher(root),
            note = null,
            start = start,
            end = end,
        )
    }

    // ---------- мелкие вытаскиватели ----------

    private fun extractDate(node: JSONObject, ancestors: List<JSONObject>): LocalDate? {
        val fromNode = firstString(node, "date", "beginDate", "startDate")
            ?: "${firstOf(node, "startAt", "start_at", "startTime", "beginTime", "begin_time")}"
        val d = toDateString(fromNode)
        if (d != null) return d
        for (a in ancestors.asReversed()) {
            val s = firstString(a, "date", "beginDate", "startDate") ?: continue
            val d2 = toDateString(s) ?: continue
            return d2
        }
        return null
    }

    private fun toDateString(raw: String): LocalDate? {
        val d = raw.take(10)
        if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(d)) return runCatching { LocalDate.parse(d) }.getOrNull()
        return null
    }

    private fun extractSubject(node: JSONObject): String? {
        for (key in listOf("subjectName", "subject_name", "subjectReadableName", "lessonName", "lesson_name", "subject")) {
            when (val v = node.opt(key)) {
                is String -> if (v.isNotBlank()) return v.trim()
                is JSONObject -> firstString(v, "name", "title")?.let { if (it.isNotBlank()) return it }
            }
        }
        return null
    }

    private fun extractRoom(node: JSONObject): String? {
        val number = firstString(node, "roomNumber", "room_number")
        val name = firstString(node, "roomName", "room_name", "building")
        return when {
            !number.isNullOrBlank() && !name.isNullOrBlank() -> number
            !number.isNullOrBlank() -> number
            else -> name
        }
    }

    private fun extractTeacher(node: JSONObject): String? {
        node.optJSONObject("teacher")?.let { t ->
            humanName(t)?.let { return it }
        }
        node.optJSONArray("teachers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                humanName(t)?.let { return it }
            }
        }
        return firstString(node, "teacherName", "teacher_name")
    }

    // ---------- utils (аналог NextLessonCalculator.parse, расширенный ключами) ----------

    private fun anyOf(node: JSONObject, vararg keys: String): Boolean =
        keys.any { node.opt(it) !== null && node.opt(it) !== JSONObject.NULL }

    private fun idOf(v: Any?): Long? = when (v) {
        is Int -> v.toLong().takeIf { it > 0 }
        is Long -> v.takeIf { it > 0 }
        is Double -> v.toLong().takeIf { it > 0 }
        is String -> v.trim().toLongOrNull()?.takeIf { it > 0 }
        else -> null
    }

    private fun firstOf(obj: JSONObject, vararg keys: String): Any? {
        for (k in keys) {
            val v = obj.opt(k) ?: continue
            if (v !== JSONObject.NULL) return v
        }
        return null
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj.opt(k)
            if (v is String && v.isNotBlank()) return v.trim()
        }
        return null
    }

    private fun toHhmm(v: Any?): String? = when (v) {
        is String -> parseHhmm(v)
        is Int -> minutesToHhmm(v)
        is Long -> if (v in 0..1439) minutesToHhmm(v.toInt()) else null
        else -> null
    }

    private fun parseHhmm(s: String): String? {
        val t = s.trim()
        val hhmm = if (t.length >= 16 && t[10] == 'T') t.substring(11, 16) else t
        val m = Regex("""^(\d{1,2}):(\d{2})""").find(hhmm) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return null
        return "%02d:%02d".format(h, min)
    }

    private fun minutesToHhmm(v: Int): String? =
        if (v in 0..1439) "%02d:%02d".format(v / 60, v % 60) else null

    private fun defaultEnd(start: String): String {
        val t = NextLessonCalculator.parse(start) ?: return start
        val e = t.plusMinutes(45)
        return "%02d:%02d".format(e.hour, e.minute)
    }

    private fun walkWithAncestors(node: Any?, ancestors: List<JSONObject>, visit: (Any?, List<JSONObject>) -> Unit) {
        visit(node, ancestors)
        when (node) {
            is JSONObject -> {
                val keys = mutableListOf<String>()
                val kit = node.keys()
                while (kit.hasNext()) keys += kit.next()
                keys.forEach { walkWithAncestors(node.opt(it), ancestors + node, visit) }
            }
            is JSONArray -> for (i in 0 until node.length()) walkWithAncestors(node.opt(i), ancestors, visit)
        }
    }
}
