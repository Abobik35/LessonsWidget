package com.nastlo.lessonswidget.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastlo.lessonswidget.data.ThemePrefs
import com.nastlo.lessonswidget.data.UpdatePrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()

    var showTheme by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(ThemePrefs.load(context)) }

    if (showFaq) {
        FaqScreen(onBack = { showFaq = false })
        return
    }

    // перечитываем время последнего обновления, когда что-то обновилось
    var lastText by remember { mutableStateOf(UpdatePrefs.text(context)) }
    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearRefreshMessage()
        }
        lastText = UpdatePrefs.text(context)
    }

    val version = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            "V$code"
        }.getOrDefault("V?")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ---------- Обновление ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                enabled = !refreshing,
                onClick = { viewModel.quickRefresh() },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("Обновить расписание", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            Text(
                "Последнее обновление: $lastText",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            // ---------- Интерфейс ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showTheme = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("Тема", fontWeight = FontWeight.SemiBold)
                }
            }

            // ---------- часто задаваемые вопросы ----------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                onClick = { showFaq = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        AppIcons.Help,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text("Частые вопросы", fontWeight = FontWeight.SemiBold)
                }
            }

            // версия — мелким шрифтом внизу (так и оставляем)
            Text(
                "LessonsWidget — $version",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Тема") },
            text = {
                Column {
                    ThemeOption("Как в системе", ThemePrefs.MODE_SYSTEM, sel) { m ->
                        sel = m
                        viewModel.setTheme(m)
                    }
                    ThemeOption("Тёмная", ThemePrefs.MODE_DARK, sel) { m ->
                        sel = m
                        viewModel.setTheme(m)
                    }
                    ThemeOption("Светлая", ThemePrefs.MODE_LIGHT, sel) { m ->
                        sel = m
                        viewModel.setTheme(m)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTheme = false }) { Text("Готово") }
            },
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}
