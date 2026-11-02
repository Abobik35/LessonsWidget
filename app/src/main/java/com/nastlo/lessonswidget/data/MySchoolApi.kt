package com.nastlo.lessonswidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Клиент API «Моя Школа» (Московская область, myschool.mosreg.ru).
 *
 * Токен — кука aupd_token, которую выдаёт сайт после входа через Госуслуги.
 * Заголовки повторяют то, что шлёт официальное приложение/сайт
 * (смотри OctoDiary-py, MIT): Authorization: Bearer + Auth-Token + Cookie,
 * плюс x-mes-subsystem / client-type / x-mes-role / profile-id.
 *
 * Для хоста authedu.mosreg.ru сайт-как правило шлёт весь набор кук WebView
 * (aupd_token + JSESSIONID и др.), поэтому вызывающий код может передать
 * строку кук целиком (cookieHeader) — так запросы выглядят как у сайта.
 */
class MySchoolApi(private val token: String) {

    private suspend fun get(
        fullUrl: String,
        profileId: Long?,
        role: String? = null,
        cookieHeader: String? = null,
    ): Any = withContext(Dispatchers.IO) {
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Auth-Token", token)
            val cookie = cookieHeader?.takeIf { it.isNotBlank() }
                ?: "aupd_token=$token; aupdtoken=$token"
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("x-mes-subsystem", "familymp")
            conn.setRequestProperty("client-type", "diary-mobile")
            if (!role.isNullOrBlank()) conn.setRequestProperty("x-mes-role", role)
            profileId?.let { conn.setRequestProperty("profile-id", it.toString()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw MySchoolException(code, body)
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else if (trimmed.startsWith("{")) JSONObject(trimmed)
            else JSONObject()
        } finally {
            conn.disconnect()
        }
    }

    /** Профиль семьи: profile + children[].contingent_guid — для события-уроков. */
    suspend fun profile(cookieHeader: String? = null): Any =
        get("$WEB/api/family/web/v1/profile", null, cookieHeader = cookieHeader)

    suspend fun profileMobile(cookieHeader: String? = null): Any =
        get("$API/family/mobile/v1/profile", null, cookieHeader = cookieHeader)

    /** /v1/user/childrens — запасной источник детей (и uuid). */
    suspend fun childrens(cookieHeader: String? = null): Any =
        get("$WEB/v1/user/childrens", null, cookieHeader = cookieHeader)

    /** Уроки (события) по ученику: главный источник расписания; два хоста. */
    suspend fun events(
        personId: String,
        role: String?,
        from: LocalDate,
        to: LocalDate,
        cookieHeader: String? = null,
    ): Any {
        var lastError: MySchoolException? = null
        for (base in listOf(WEB, API)) {
            try {
                return get(
                    "$base/api/eventcalendar/v1/api/events" +
                        "?person_ids=$personId&begin_date=$from&end_date=$to",
                    profileId = null,
                    role = role,
                    cookieHeader = cookieHeader,
                )
            } catch (e: MySchoolException) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("events: нет ответа")
    }

    /** Каркас дней (тип дня/модуль) — events там обычно пустые. */
    suspend fun periodsSchedules(
        studentUserId: Long,
        profileId: Long?,
        from: LocalDate,
        to: LocalDate,
        cookieHeader: String? = null,
    ): Any = get(
        "$API/family/mobile/v1/periods_schedules?student_id=$studentUserId&from=$from&to=$to",
        profileId,
        cookieHeader = cookieHeader,
    )

    /** Один урок — учитель и точное время (type = source события, обычно PLAN). */
    suspend fun lessonItem(
        lessonId: Long,
        profileId: Long?,
        studentId: Long,
        type: String?,
        cookieHeader: String? = null,
    ): Any = get(
        "$API/family/mobile/v1/lesson_schedule_items/$lessonId" +
            "?student_id=$studentId&type=${type ?: "PLAN"}",
        profileId,
        cookieHeader = cookieHeader,
    )

    /** Свободный GET — для перебора вариантов урока-деталей. */
    suspend fun rawGet(
        url: String,
        profileId: Long? = null,
        role: String? = null,
        cookieHeader: String? = null,
    ): Any = get(url, profileId, role, cookieHeader)

    companion object {
        const val WEB = "https://authedu.mosreg.ru"
        const val API = "https://api.myschool.mosreg.ru"

        /** Личный кабинет — отсюда пользователь входит через Госуслуги в WebView. */
        const val LOGIN_START_URL = "https://myschool.mosreg.ru/"

        const val TOKEN_COOKIE = "aupd_token"

        /** Домены, на которых может лежать кука (проверяем все сервисы МО).
         * порядок: основной сайт › authedu › api › прочие поддомены. */
        val COOKIE_DOMAINS = listOf(
            "https://myschool.mosreg.ru",
            "https://authedu.mosreg.ru",
            "https://api.myschool.mosreg.ru",
            "https://school.mosreg.ru",
            "https://sso.mosreg.ru",
            "https://adu.mosreg.ru",
        )

        fun tokenFromCookie(cookieString: String?): String? = cookieString?.let {
            Regex("(?:aupd_token|aupdtoken)=([^;]+)").find(it)?.groupValues?.get(1)
        }?.takeIf { it.length > 40 }
    }
}

class MySchoolException(val httpCode: Int, val body: String) : Exception("HTTP $httpCode: ${body.take(300)}")
