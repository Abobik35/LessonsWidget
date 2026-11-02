package com.nastlo.lessonswidget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastlo.lessonswidget.data.ThemePrefs

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by viewModel.themeMode.collectAsStateWithLifecycle()
            val useDark = when (mode) {
                ThemePrefs.MODE_DARK -> true
                ThemePrefs.MODE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(
                colorScheme = if (useDark) darkColorScheme() else lightColorScheme(),
            ) {
                EditorScreen(viewModel)
            }
        }
    }
}
