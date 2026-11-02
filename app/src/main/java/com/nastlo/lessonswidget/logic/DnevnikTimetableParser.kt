package com.nastlo.lessonswidget.logic

import com.nastlo.lessonswidget.data.Lesson
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class ParsedGroup(val id: Long, val name: String)

/**
 * Защищённый парсер JSON Дневник.ру.
 * Структура timetable может отличаться между регионами/версиями API, поэтому
 * ключи ищем по нескольким вариантам имён, а «сырой» ответ показываем в UI.
 */
object DnevnikTimetableParser {

    // 1. Классы/группы — только верхний уровень, чтобы не принять школу за класс.

    fun parseGroups(root: Any): List<ParsedGroup> {
        val items = mutableListOf<Any?>()
        when (root) {
            is JSONArray -> for (i in 0 until root.length()) items += root.opt(i)
            is JSONObject -> {
                val data = root.opt("data")
                if (data is JSONArray) for (i in 0 until data.length()) items += data.opt(i)
                else items += root
            }
        }

        val out = LinkedHashMap<Long, ParsedGroup>()
        for (item in items) {
            val id: Long
            val name: String
            when (item) {
                is Int, is Long, is String, is Double -> {
                    id = idOf(item) ?: continue
                    name = "Класс/группа #$id"
                }
                is JSONObject -> {
                    id = idOf(firstOf(item, "id", "eduGroupId", "groupId")) ?: continue
                    name = firstString(item, "name", "title", "groupName", "readableName")
                        ?: "Класс/группа #$id"
                }
                else -> continue
            }
            if (id > 0 && !out.containsKey(id)) out[id] = ParsedGroup(id, name)
        }
        return out.values.toList()
    }

    // 2. Уроки — глубокий обход: ищем объекты с "startTime".

    fun parseLessons(root: Any): List<Lesson> {
        val found = mutableListOf<Lesson>()
        walk(root) { node ->
            if (node is JSONObject && node.has("startTime")) {
                lessonFrom(node)?.let { found += it }
            }
        }
        return found
            .distinctBy { listOf(it.weekday, it.subject, it.room, it.start, it.end, it.teacher) }
            .sortedBy {
                it.weekday * 10_000 + (NextLessonCalculator.parse(it.start)?.toSecondOfDay() ?: 0)
            }
    }

    private fun lessonFrom(node: JSONObject): Lesson? {
        val start = toHhmm(node.opt("startTime")) ?: return null
        val end = toHhmm(node.opt("endTime")) ?: defaultEnd(start)
        val weekday = extractWeekday(node) ?: return null
        val subject = extractSubject(node) ?: return null

        return Lesson(
            weekday = weekday,
            subject = subject,
            room = extractRoom(node) ?: "",
            floor = null,
            teacher = extractTeacher(node),
            note = null,
            start = start,
            end = end,
        )
    }

    private fun extractWeekday(node: JSONObject): Int? {
        for (key in listOf("weekday", "dayOfWeek", "dayNumber", "day")) {
            when (val v = node.opt(key)) {
                is Int -> if (v in 1..7) return v
                is Long -> if (v in 1..7) return v.toInt()
                is String -> {
                    val t = v.trim()
                    val asInt = t.toIntOrNull()
                    when {
                        asInt != null && asInt in 1..7 -> return asInt
                        else -> dayNameMap[t.lowercase()]?.let { return it }
                    }
                }
            }
        }
        // weekday нет — пробуем день из даты
        for (key in listOf("date", "startDate", "lessonDate", "from")) {
            val raw = node.opt(key) as? String ?: continue
            val d = raw.take(10)
            val day = runCatching { LocalDate.parse(d).dayOfWeek.value }.getOrNull() ?: continue
            return day
        }
        return null
    }

    private fun extractSubject(node: JSONObject): String? {
        for (key in listOf("subjectName", "subject", "name", "lessonName", "subjectReadableName")) {
            when (val v = node.opt(key)) {
                is String -> if (v.isNotBlank()) return v.trim()
                is JSONObject -> firstString(v, "name", "title", "fullName", "readableName")?.let {
                    if (it.isNotBlank()) return it
                }
            }
        }
        return null
    }

    private fun extractRoom(node: JSONObject): String? =
        nestedName(node, "room")
            ?: nestedName(node, "classroom")
            ?: nestedName(node, "auditorium")
            ?: nestedName(node, "location")
            ?: firstString(node, "roomNumber", "roomName", "classroomName", "auditoriumName")

    private fun extractTeacher(node: JSONObject): String? =
        nestedName(node, "teacher")
            ?: nestedName(node, "teachers")
            ?: firstString(node, "teacherName", "teacherFullName", "teacherReadableName")

    // helpers

    private fun idOf(v: Any?): Long? = when (v) {
        is Int -> v.toLong().takeIf { it > 0 }
        is Long -> v.takeIf { it > 0 }
        is Double -> v.toLong().takeIf { it > 0 }
        is String -> v.trim().toLongOrNull()?.takeIf { it > 0 }
        else -> null
    }

    private fun firstOf(obj: JSONObject, vararg keys: String): Any? {
        for (k in keys) obj.opt(k)?.let { if (it !== JSONObject.NULL) return it }
        return null
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj.opt(k)
            if (v is String && v.isNotBlank()) return v.trim()
        }
        return null
    }

    private fun nestedName(obj: JSONObject, key: String): String? {
        return when (val v = obj.opt(key) ?: return null) {
            is String -> v.trim().ifBlank { null }
            is JSONObject -> firstString(v, "name", "fullName", "readableName", "title")
            is JSONArray -> {
                for (i in 0 until v.length()) {
                    val inner = v.opt(i) as? JSONObject ?: continue
                    val n = firstString(inner, "name", "fullName", "title") ?: continue
                    return n
                }
                null
            }
            else -> null
        }
    }

    private fun toHhmm(v: Any?): String? = when (v) {
        is String -> parseHhmm(v)
        is Int -> minutesToHhmm(v)
        is Long -> if (v in 0..1439) minutesToHhmm(v.toInt()) else null
        else -> null
    }

    private fun parseHhmm(s: String): String? {
        val t = s.trim()
        // ISO-дата со временем: "2026-11-02T09:30:00"
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

    private fun walk(node: Any?, visit: (Any?) -> Unit) {
        visit(node)
        when (node) {
            is JSONObject -> {
                val keys = mutableListOf<String>()
                val it = node.keys()
                while (it.hasNext()) keys += it.next()
                keys.forEach { walk(node.opt(it), visit) }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private val dayNameMap = mapOf(
        "понедельник" to 1, "monday" to 1, "пн" to 1, "mon" to 1,
        "вторник" to 2, "tuesday" to 2, "вт" to 2, "tue" to 2,
        "среда" to 3, "среду" to 3, "wednesday" to 3, "ср" to 3, "wed" to 3,
        "четверг" to 4, "thursday" to 4, "чт" to 4, "thu" to 4,
        "пятница" to 5, "friday" to 5, "пт" to 5, "fri" to 5,
        "суббота" to 6, "saturday" to 6, "сб" to 6, "sat" to 6,
        "воскресенье" to 7, "sunday" to 7, "вс" to 7, "sun" to 7,
    )
}
