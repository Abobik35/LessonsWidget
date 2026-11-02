package com.nastlo.lessonswidget.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import com.nastlo.lessonswidget.data.Lesson
import com.nastlo.lessonswidget.data.MySchoolApi
import com.nastlo.lessonswidget.data.MySchoolException
import com.nastlo.lessonswidget.data.MySchoolPrefs
import com.nastlo.lessonswidget.logic.MySchoolParser
import com.nastlo.lessonswidget.logic.NextLessonCalculator
import com.nastlo.lessonswidget.logic.ParsedEvent
import com.nastlo.lessonswidget.logic.ParsedProfile
import com.nastlo.lessonswidget.logic.ParsedStudent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class TimetableLoad(
    val lessons: List<Lesson>,
    val warning: String?,
    val throttledError: MySchoolException?,
    val rawJson: String?,
)

private val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(viewModel: EditorViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }

    var stage by remember { mutableStateOf(0) }
    // 0 приветствие, 1 WebView-вход, 2 выбор ребёнка, 3 предпросмотр
    var token by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ParsedProfile?>(null) }
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rawJson by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    var clearExisting by remember { mutableStateOf(true) }
    var manualToken by remember { mutableStateOf("") }
    var debugNote by remember { mutableStateOf<String?>(null) }
    var enrichOk by remember { mutableStateOf(0) }
    var enrichFail by remember { mutableStateOf(0) }
    var enrichInfo by remember { mutableStateOf<String?>(null) }
    var chosenStudent by remember { mutableStateOf<ParsedStudent?>(null) }
    var chosenProfileId by remember { mutableStateOf<Long?>(null) }
    var chosenRole by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun showError(e: Throwable) {
        error = e.message
        // Сырой ответ не затираем, если в ошибке нет тела — он нужен для разбора.
        val b = (e as? MySchoolException)?.body
        if (b != null) rawJson = b
    }

    /** Загружает профиль (children) по токену. */
    suspend fun loadProfile(tok: String) {
        loading = true
        try {
            val api = MySchoolApi(tok)
            // Сайт шлёт весь набор кук WebView — передадим их же (важно для authedu).
            val webCookies = CookieManager.getInstance().getCookie(MySchoolApi.WEB)
            val apiCookies = CookieManager.getInstance().getCookie(MySchoolApi.API)
            val raw: Any = try {
                api.profile(webCookies).also { debugNote = "профиль: web" }
            } catch (e: MySchoolException) {
                api.profileMobile(apiCookies).also { debugNote = "профиль: mobile (${e.httpCode})" }
            }
            rawJson = raw.toString().take(4000)
            val parsed = MySchoolParser.parseProfile(raw)
            if (parsed.students.isEmpty()) {
                error = "В профиле не найдено ни одного ученика. Смотри сырой ответ ниже."
            } else {
                var students = parsed.students
                if (students.all { s -> s.personId.isNullOrBlank() }) {
                    // UUID нет — пробуем достать из /v1/user/childrens
                    try {
                        val alt = MySchoolParser.parseChildrens(api.childrens(webCookies))
                        if (alt.isNotEmpty()) {
                            students = if (alt.size == students.size) {
                                students.mapIndexed { i, s -> s.copy(personId = alt[i].personId) }
                            } else {
                                alt
                            }
                            debugNote = (debugNote ?: "") + "; UUID — из childrens"
                        } else {
                            debugNote = (debugNote ?: "") + "; childrens: без person_id"
                        }
                    } catch (e: Exception) {
                        debugNote = (debugNote ?: "") + "; childrens: ${e.message?.take(120)}"
                    }
                }
                profile = ParsedProfile(parsed.profileId, students, parsed.role)
            }
        } catch (e: Exception) {
            showError(e)
        } finally {
            loading = false
        }
    }

    /** Загружает расписание: уроки берём из eventcalendar-events; запасной путь — periods_schedules. */
    suspend fun loadTimetable(tok: String, student: ParsedStudent, profileId: Long?, role: String?): List<Lesson> {
        val api = MySchoolApi(tok)
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = monday.minusDays(7)
        val to = monday.plusDays(21)
        val webCookies = CookieManager.getInstance().getCookie(MySchoolApi.WEB)
        val apiCookies = CookieManager.getInstance().getCookie(MySchoolApi.API)

        val notes = StringBuilder()
        val root: Any = if (!student.personId.isNullOrBlank()) {
            try {
                api.events(student.personId!!, role, from, to, webCookies).also { notes.append("путь: events ✓ ") }
            } catch (e: Exception) {
                notes.append("путь: events сбой (${e.message?.take(80)}) → periods_schedules ")
                api.periodsSchedules(student.userId, profileId, from, to, apiCookies)
            }
        } else {
            notes.append("путь: UUID не найден → periods_schedules ")
            api.periodsSchedules(student.userId, profileId, from, to, apiCookies)
        }
        rawJson = root.toString().take(9000)
        notes.append("ответ: ").append(
            when (root) {
                is JSONObject -> "total_count=${root.opt("total_count")}, длина response=${root.optJSONArray("response")?.length()}"
                is JSONArray -> "массив длиной ${root.length()}"
                else -> "неизвестный тип"
            },
        ).append(' ')

        val events = MySchoolParser.parseEvents(root)
            // Суббота не нужна: выкидываем уроки дня 6 (Сб) ещё до предпросмотра
            .filter { it.lesson.weekday <= 5 }
        if (events.isEmpty()) {
            throw IllegalStateException("Расписание на $from…$to не вернуло событий. ${notes}Смотри сырой ответ ниже.")
        }

        // группируем по дню недели + предмет + время (кабинет может чередоваться по неделям)
        val best = LinkedHashMap<String, Lesson>()
        val lessonIds = LinkedHashMap<String, Long>()
        val lessonTypes = LinkedHashMap<String, String?>()
        val roomCounts = LinkedHashMap<String, LinkedHashMap<String, Int>>()
        val replacedAny = LinkedHashMap<String, Boolean>()
        // слот повторяется каждую неделю — шаблоном берём самое актуальное
        // событие (сегодня/ближайшее будущее): его учитель и id для деталей
        val templates = LinkedHashMap<String, ParsedEvent>()
        for (e in events) {
            val key = listOf(e.lesson.weekday, e.lesson.subject, e.lesson.start).joinToString("|")
            val cur = templates[key]
            if (cur == null || MySchoolParser.isNewerEvent(e, cur, LocalDate.now())) templates[key] = e
            // помечаем замену только для текущей недели (Пн…Вс), иначе замена
            // из прошлой/будущей недели «прилипает» к такому же уроку
            if (e.replaced && e.date in monday..monday.plusDays(6)) replacedAny[key] = true
            e.lesson.room.takeIf { it.isNotBlank() }?.let { room ->
                roomCounts.getOrPut(key) { LinkedHashMap() }.merge(room, 1, Int::plus)
            }
        }
        for ((key, e) in templates) {
            best[key] = e.lesson
            e.eventId?.let { id ->
                lessonIds[key] = id
                lessonTypes[key] = e.source
            }
        }

        // дотягиваем учителя у каждого уникального урока (мягко: сбой не мешает)
        val enriched = best.toMutableMap()
        val enrichedKeys = lessonIds.entries.take(80).map { it.key }
        var okCount = 0
        var failCount = 0
        var firstFail: String? = null
        var firstSample: String? = null
        for (key in enrichedKeys) {
            val eventId = lessonIds.getValue(key)
            val type = lessonTypes[key] ?: "PLAN"
            // Урок-детали иначе капризны к параметрам — пробуем 4 варианта,
            // берём первый успешный (внутренняя ошибка сервера, если student_id
            // и profile-id не согласованы, лечится подбором).
            val itemUrl = MySchoolApi.API + "/family/mobile/v1/lesson_schedule_items/$eventId"
            val variants: List<Triple<String, Long?, String?>> = listOf(
                Triple("$itemUrl?student_id=${student.userId}&type=$type", profileId, apiCookies),
                Triple("$itemUrl?student_id=${student.userId}&type=$type", null, apiCookies),
                Triple("$itemUrl?student_id=${profileId ?: student.userId}&type=$type", profileId, apiCookies),
                Triple("$itemUrl?student_id=${student.userId}&type=$type", profileId, webCookies),
            )
            var raw: Any? = null
            var failDesc: String? = null
            for ((url, pid, cookies) in variants) {
                try {
                    raw = api.rawGet(url, pid)
                    break
                } catch (e: MySchoolException) {
                    failDesc = "HTTP ${e.httpCode}: ${e.body.take(250)}"
                } catch (e: Exception) {
                    failDesc = e.message ?: e.javaClass.simpleName
                }
            }
            if (raw == null) {
                failCount++
                if (firstFail == null) firstFail = failDesc
                continue
            }
            if (firstSample == null) firstSample = raw.toString().take(3000)
            val detail = MySchoolParser.parseLessonItem(raw) ?: continue
            val existing = enriched[key] ?: continue
            enriched[key] = existing.copy(
                teacher = detail.teacher ?: existing.teacher,
                room = existing.room.ifBlank { detail.room },
            )
            okCount++
        }
        enrichOk = okCount
        enrichFail = failCount
        if (failCount > 0 || okCount == 0) {
            enrichInfo = "учителя: ok=$okCount, fail=$failCount; ${firstFail ?: "ответ без известных полей"}"
            rawJson = (firstFail?.let { "ОШИБКА: $it\n\n" } ?: "") +
                (firstSample?.let { "ПРИМЕР ОТВЕТА:\n$it" } ?: "ответов нет")
        }

        // Кабинеты, чередующиеся по неделям, склеиваем: самый частый первым, через « / »;
        // если кабинет пуст — подставляем любой известный.
        for (key in best.keys) {
            val lesson = enriched[key] ?: continue
            val counts = roomCounts[key]
            val joined = counts?.entries
                ?.sortedByDescending { it.value }
                ?.joinToString(" / ") { it.key }
                ?.takeIf { it.isNotBlank() }
            when {
                joined != null && counts.size > 1 ->
                    enriched[key] = lesson.copy(room = joined)
                lesson.room.isBlank() -> {
                    val single = counts?.keys?.firstOrNull()
                    if (single != null) enriched[key] = lesson.copy(room = single)
                }
            }
        }

        // Замены: помечаем урок, у которого в какой-то из недель окна была замена учителя
        for ((key, _) in replacedAny) {
            val existing = enriched[key] ?: continue
            if (existing.note.isNullOrBlank()) {
                enriched[key] = existing.copy(note = "Замена учителя")
            }
        }

        return enriched.values.toList().sortedBy {
            it.weekday * 10_000 + (NextLessonCalculator.parse(it.start)?.toSecondOfDay() ?: 0)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Импорт из «Моя Школа»") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- 0: начало ----------------
            if (stage == 0) {
                Text("Как войти:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "1. Откроется встроенный браузер на myschool.mosreg.ru.\n" +
                        "2. Войди через Госуслуги как обычно — капча и СМС обрабатывает сам сайт.\n" +
                        "3. После входа приложение тихо возьмёт «cookie» aupdtoken — это и есть доступ к API.\n" +
                        "4. Токен хранится только в этом приложении.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(modifier = Modifier.fillMaxWidth(), onClick = { stage = 1 }) {
                    Text("Войти в «Моя Школа»")
                }
                Text("…или вставь токен aupdtoken вручную:", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Токен") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val tok = manualToken.trim()
                        if (tok.length < 20) {
                            error = "Это не похоже на токен"
                        } else {
                            token = tok
                            scope.launch { loadProfile(tok) }
                            stage = 2
                        }
                    },
                ) { Text("Использовать токен вручную") }
            }

            // ---------------- 1: WebView-вход ----------------
            if (stage == 1) {
                WebViewAuth(
                    onToken = { tok ->
                        token = tok
                        stage = 2
                        scope.launch { loadProfile(tok) }
                    },
                    onCancel = { stage = 0 },
                )
            }

            // ---------------- 2: выбор ребёнка ----------------
            if (stage == 2) {
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Загружаю профиль...")
                }
                profile?.let { pr ->
                    Text("Чьё расписание загрузить?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    pr.students.forEach { st ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                                scope.launch {
                                    chosenStudent = st
                                    chosenProfileId = pr.profileId
                                    chosenRole = pr.role
                                    loading = true
                                    error = null
                                    try {
                                        val result = loadTimetable(tokRequired(token), st, pr.profileId, pr.role)
                                        if (result.isEmpty()) {
                                            error = "Расписание пустое — смотри сырой ответ ниже."
                                        } else {
                                            lessons = result
                                            stage = 3
                                        }
                                    } catch (e: Exception) {
                                        showError(e)
                                    } finally {
                                        loading = false
                                    }
                                }
                            }) {
                                Column {
                                    Text(st.label, fontWeight = FontWeight.SemiBold)
                                    if (st.personId.isNullOrBlank()) {
                                        Text("Без UUID — попробует запасной путь", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                debugNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!loading && rawJson != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showRaw = !showRaw }) {
                            Text(if (showRaw) "Скрыть сырой ответ" else "Показать сырой ответ")
                        }
                        if (showRaw) {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(rawJson.orEmpty()))
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Сырой ответ", rawJson.orEmpty()))
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            }) { Text("Копировать") }
                        }
                    }
                    if (showRaw) {
                        SelectionContainer {
                            Text(
                                rawJson.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                TextButton(onClick = { stage = 0 }) { Text("Другой аккаунт") }
            }

            // ---------------- 3: предпросмотр ----------------
            if (stage == 3) {
                Text("Найдено уроков: ${lessons.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (enrichFail > 0 || enrichInfo != null) {
                    Text(
                        "Учителя подтянулись не полностью: $enrichInfo",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Скрыть сырой ответ" else "Показать сырой ответ (учителя)")
                    }
                    if (showRaw) {
                        Row {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(rawJson.orEmpty()))
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Сырой ответ учителей", rawJson.orEmpty()))
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            }) { Text("Копировать") }
                        }
                        SelectionContainer {
                            Text(
                                rawJson.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                Text(
                    "Список уже «сложен» из 4 недель реального расписания без дублей; учителя подтянуты по каждому уроку.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearExisting, onCheckedChange = { clearExisting = it })
                    Text("Очистить текущее расписание перед импортом")
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(lessons) { l ->
                        Text(
                            "${dayNames.getOrElse(l.weekday - 1) { "?" }} ${l.start}–${l.end}  ${l.subject}" +
                                (if (l.room.isNotBlank()) " · каб. ${l.room}" else "") +
                                (l.teacher?.let { " · $it" } ?: "") +
                                (l.note?.let { "  ⚠️ $it" } ?: ""),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { stage = 2 }) { Text("Назад") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Сохраняем аккаунт для ежесуточного автообновления замен
                            saveMySchoolAccount(context, profile?.let { chosenStudent }, chosenProfileId, chosenRole)
                            viewModel.importLessons(lessons, clearExisting)
                            onBack()
                        },
                    ) { Text("Добавить в расписание") }
                }
            }

            // ---------------- ошибка ----------------
            error?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SelectionContainer {
                            Text(
                                "⚠️ $msg",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(msg))
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Ошибка импорта", msg))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }) { Text("Копировать ошибку") }
                    }
                }
            }
        }
    }
}

private fun tokRequired(token: String?): String =
    token ?: throw IllegalStateException("Токен был потерян — войти заново")

/** Сохраняет аккаунт для ежедневного автообновления замен (см. ReplacementsWorker). */
private fun saveMySchoolAccount(
    context: Context,
    student: ParsedStudent?,
    profileId: Long?,
    role: String?,
) {
    MySchoolPrefs.save(context, student, profileId, role)
}

/**
 * Экран входа: WebView на личном кабинете «Моя Школа», после входа ловим
 * куку aupdtoken и возвращаем её. Куки опрашиваем каждую секунду (сайт — SPA,
 * токен может появиться позже загрузки страницы), плюс есть кнопка
 * консультации-диагностики на случай, если автоматика не сработала.
 */
@Composable
private fun WebViewAuth(onToken: (String) -> Unit, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    var finishedCapture by remember { mutableStateOf(false) }
    var debugInfo by remember { mutableStateOf<String?>(null) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) { Text("← Назад") }
            Text(
                "Вход в «Моя Школа»",
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Вошёл, но ничего не происходит? Нажми «Проверить вручную» ниже и пришли скриншот.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (debugInfo != null) {
            val context = LocalContext.current
            val clipboard = LocalClipboardManager.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { debugInfo = null }) { Text("Скрыть диагностику") }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(debugInfo.orEmpty()))
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Диагностика", debugInfo.orEmpty()))
                        Toast.makeText(context, "Скопировано — можно вставить куда угодно", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Копировать") }
            }
            SelectionContainer {
                Text(
                    debugInfo.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .heightIn(max = 170.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
        TextButton(
            onClick = { scope.launch { debugInfo = fullDiagnostics(webRef) } },
        ) {
            Text("Проверить вручную (если вход завис)")
        }

        // Опрос: куки каждую секунду + раз в 2 секунды — JS-хранилища страницы
        // (сайт — SPA, токен может лежать в localStorage, а не в куках)
        LaunchedEffect(Unit) {
            var tick = 0
            while (isActive && !finishedCapture) {
                kotlinx.coroutines.delay(1_000L)
                tick++
                var tok = pollForToken()
                if (tok == null && tick % 2 == 0) {
                    tok = firstTokenCandidate(runHarvest(webRef))
                }
                if (tok != null) {
                    finishedCapture = true
                    onToken(tok)
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        private fun checkCookies() {
                            if (finishedCapture) return
                            val tok = pollForToken()
                            if (tok != null) {
                                finishedCapture = true
                                onToken(tok)
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) = checkCookies()
                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = checkCookies()
                    }
                    loadUrl(MySchoolApi.LOGIN_START_URL)
                }.also { webRef = it }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

/** Ищет aupdtoken среди кук всех доменов «Моя Школа»; null — если нет. */
private fun pollForToken(): String? {
    val cm = CookieManager.getInstance()
    for (url in MySchoolApi.COOKIE_DOMAINS) {
        val tok = MySchoolApi.tokenFromCookie(cm.getCookie(url))
        if (tok != null) return tok
    }
    return null
}

/** JS: собирает localStorage/sessionStorage и текущий адрес страницы. */
private const val HARVEST_JS =
    "(function(){var ls={},ss={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);ls[k]=(localStorage.getItem(k)||'').slice(0,3000);}" +
        "for(var j=0;j<sessionStorage.length;j++){var k2=sessionStorage.key(j);ss[k2]=sessionStorage.getItem(k2)||'';}" +
        "return JSON.stringify({href:location.href,cookie:document.cookie,ls:ls,ss:ss});})()"

@Suppress("UNCHECKED_CAST")
private fun parseHarvest(raw: String?): JSONObject? = runCatching {
    JSONTokener(raw ?: "").nextValue().let { v ->
        when (v) {
            is String -> JSONObject(v)
            is JSONObject -> v
            else -> null
        }
    }
}.getOrNull()

/** Читает JS-хранилища открытой страницы (suspend — ждёт ответ WebView). */
private suspend fun runHarvest(web: WebView?): JSONObject? = suspendCancellableCoroutine { cont ->
    if (web == null) {
        cont.resumeWith(Result.success(null))
        return@suspendCancellableCoroutine
    }
    web.post {
        web.evaluateJavascript(HARVEST_JS) { raw ->
            cont.resumeWith(Result.success(parseHarvest(raw as? String)))
        }
    }
}

private fun storeToPairs(store: JSONObject): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val keys = store.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        out += k to store.optString(k)
    }
    return out
}

/** Первый похожий на токен кандидат из localStorage/sessionStorage. */
private fun firstTokenCandidate(obj: JSONObject?): String? {
    if (obj == null) return null
    val stores = listOfNotNull(obj.optJSONObject("ls"), obj.optJSONObject("ss"))
    for (store in stores) {
        val pairs = storeToPairs(store)
        // JWT начинается с «eyJ» (base64 от «{"») и содержит точки
        for ((_, v) in pairs) {
            if (v.length > 40 && v.startsWith("eyJ") && v.count { it == '.' } >= 2) return v
        }
        // иначе — любое значение >30 символов у ключа со словом «token»
        for ((k, v) in pairs) {
            if (v.length > 30 && k.lowercase().contains("token")) return v
        }
    }
    return null
}

/**
 * Полная диагностика: куки по доменам + JS-хранилища текущей страницы.
 * Значения не показываем (секретные), только имена и длины + пометки про JWT.
 */
private suspend fun fullDiagnostics(web: WebView?): String = buildString {
    val cm = CookieManager.getInstance()
    appendLine("КУКИ:")
    for (url in MySchoolApi.COOKIE_DOMAINS) {
        appendLine("• $url")
        val all = cm.getCookie(url)
        if (all.isNullOrBlank()) appendLine("   (куки отсутствуют)")
        else all.split("; ").forEach { c ->
            appendLine("   ${c.substringBefore("=")}  — длина ${c.substringAfter("=", "").length}")
        }
    }
    appendLine("JS-ХРАНИЛИЩА СТРАНИЦЫ:")
    val js = runHarvest(web)
    if (js == null) appendLine("   (не удалось прочитать)")
    else {
        appendLine("Текущая страница: ${js.optString("href")}")
        for ((key, label) in listOf("ls" to "localStorage", "ss" to "sessionStorage")) {
            appendLine("— $label:")
            val store = js.optJSONObject(key)
            if (store == null || store.length() == 0) {
                appendLine("   (пусто)")
            } else {
                storeToPairs(store).forEach { (k, v) ->
                    appendLine(
                        "   $k — длина ${v.length}" +
                            (if (v.startsWith("eyJ") && v.count { it == '.' } >= 2) "  ← ПОХОЖЕ НА JWT-ТОКЕН!" else ""),
                    )
                }
            }
        }
    }
    appendLine("─".repeat(24))
    appendLine("Если токен не заработал — пришли этот скриншот целиком.")
}
