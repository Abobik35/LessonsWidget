package com.nastlo.lessonswidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Мини-клиент официального API Дневник.ру (api.dnevnik.ru, v2.0).
 * Токен передаётся заголовком Access-Token. Зависимостей не добавляем:
 * HttpURLConnection + встроенный org.json.
 *
 * Гайд по токену: https://github.com/yasg1988/pydnevnikruapi-extended (MIT).
 */
class DnevnikApi(private val token: String) {

    /** Классы/учебные группы текущего пользователя. */
    suspend fun eduGroups(): Any = get("users/me/edu-groups")

    /** Расписание (timetable) учебной группы. */
    suspend fun timetable(eduGroupId: Long): Any = get("edu-groups/$eduGroupId/timetables")

    /** GET $BASE/v2.0/$path. Возвращает JSONObject либо JSONArray. */
    suspend fun get(path: String): Any = withContext(Dispatchers.IO) {
        val conn = URL("$BASE/v2.0/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Access-Token", token)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw DnevnikException(code, body)
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val BASE = "https://api.dnevnik.ru"

        /**
         * Ссылка авторизации: пользователь логинится (в т.ч. через Госуслуги),
         * подтверждает доступ, и в адресной строке появляется access_token=...
         */
        const val AUTH_URL =
            "https://login.dnevnik.ru/oauth2?response_type=token&client_id=b8006d75-70a9-4291-885c-13d8511bb2ae&scope=CommonInfo,EducationalInfo"

        /** Вытаскивает токен из любой вставленной строки (ссылки/JSON/просто токена). */
        fun extractToken(pasted: String): String? =
            Regex("access_token[=:\\s]+([A-Za-z0-9._\\-]+)").find(pasted)?.groupValues?.get(1)
                ?: pasted.trim().takeIf { it.matches(Regex("^[A-Za-z0-9._\\-]{20,}$")) }
    }
}

class DnevnikException(val httpCode: Int, body: String) : Exception("HTTP $httpCode: ${body.take(300)}")
