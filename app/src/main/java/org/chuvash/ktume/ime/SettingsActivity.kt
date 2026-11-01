package org.chuvash.ktume.ime

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.data.PreferencesRepository
import org.chuvash.ktume.data.PreferencesState
import org.chuvash.ktume.ui.theme.KeskeFontFamily
import org.chuvash.ktume.ui.theme.KeskePalettes
import org.chuvash.ktume.ui.theme.KeskeTypography

/**
 * МОДУЛЬ: SettingsActivity.kt — экран настроек и студия кастомизации клавиатуры «Кӗске Тӳме» (кТӳме).
 *
 * Собирает экран из модулей:
 * - `SettingsSections.kt` — 5 вкладок («Раскладка», «Оформление», «Ввод», «Эксперименты», «Система»);
 * - `SettingsRowEditor.kt` — конструктор рядов раскладки;
 * - `SettingsPreviewBench.kt` — тестовое поле и единственный экземпляр `KeyboardScreen` снизу;
 * - `SettingsComponents.kt` — карточки секций, превью палитр, тумблеры и ползунки.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferencesRepository.ensureLoaded(this)

        setContent {
            val prefs by PreferencesState.preferences.collectAsState()
            val prefsLoaded by PreferencesRepository.loaded.collectAsState()
            val activePalette = remember(prefs.paletteId) { KeskePalettes.byId(prefs.paletteId) }
            val colorScheme = remember(activePalette) { activePalette.toColorScheme() }
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

            var testFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue(""))
            }
            var editingLang by rememberSaveable { mutableStateOf(KeyboardLang.CHUVASH) }
            var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.LAYOUT) }
            var showLandscapePreview by rememberSaveable { mutableStateOf(false) }
            var showResetDialog by remember { mutableStateOf(false) }

            val savePrefs: (KeyboardPreferences) -> Unit = { updated ->
                PreferencesRepository.update(this@SettingsActivity, updated, debounceDisk = false)
            }
            val savePrefsDebounced: (KeyboardPreferences) -> Unit = { updated ->
                PreferencesRepository.update(this@SettingsActivity, updated, debounceDisk = true)
            }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = KeskeTypography
            ) {
                Scaffold(
                    containerColor = activePalette.boardBg,
                    contentColor = activePalette.keyText
                ) { padding ->
                    if (!prefsLoaded) {
                        Box(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = activePalette.accentBg)
                        }
                        return@Scaffold
                    }

                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .background(activePalette.boardBg)
                            .padding(horizontal = 14.dp, vertical = if (isLandscape) 4.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Кӗске Тӳме (Kĕske Tüme)",
                                    style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                    color = activePalette.keyText
                                )
                                if (!isLandscape) {
                                    Text(
                                        text = "Чӑвашла • Русская • Latina • Ελληνικά",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = activePalette.hintText
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isLandscape) {
                                    FilterChip(
                                        selected = showLandscapePreview,
                                        onClick = { showLandscapePreview = !showLandscapePreview },
                                        label = {
                                            Text(
                                                text = "⌨ Превью",
                                                fontFamily = KeskeFontFamily,
                                                fontSize = 12.sp
                                            )
                                        }
                                    )
                                }

                                FilterChip(
                                    selected = activePalette.isDark,
                                    onClick = {
                                        val nextPaletteId = if (activePalette.isDark) "sage" else "velvet_dark"
                                        savePrefs(prefs.copy(paletteId = nextPaletteId))
                                    },
                                    label = {
                                        Text(
                                            text = if (activePalette.isDark) "☀ Светлая" else "🌙 Тёмная",
                                            fontFamily = KeskeFontFamily,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontFamily = KeskeFontFamily,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingsTestFieldCard(
                                value = testFieldValue,
                                onValueChange = { testFieldValue = it },
                                palette = activePalette
                            )

                            when (selectedTab) {
                                SettingsTab.LAYOUT -> LayoutSettingsSection(
                                    prefs = prefs,
                                    palette = activePalette,
                                    savePrefs = savePrefs,
                                    editingLang = editingLang,
                                    onEditingLangChange = { editingLang = it },
                                    selectedCell = selectedCell,
                                    onSelectedCellChange = { selectedCell = it }
                                )

                                SettingsTab.APPEARANCE -> AppearanceSettingsSection(
                                    prefs = prefs,
                                    palette = activePalette,
                                    savePrefs = savePrefs,
                                    savePrefsDebounced = savePrefsDebounced
                                )

                                SettingsTab.INPUT -> InputSettingsSection(
                                    prefs = prefs,
                                    palette = activePalette,
                                    savePrefs = savePrefs,
                                    savePrefsDebounced = savePrefsDebounced
                                )

                                SettingsTab.EXPERIMENTS -> ExperimentsSettingsSection(
                                    prefs = prefs,
                                    palette = activePalette,
                                    savePrefs = savePrefs
                                )

                                SettingsTab.SYSTEM -> SystemSettingsSection(
                                    prefs = prefs,
                                    palette = activePalette,
                                    onOpenImeSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                                    onShowImePicker = {
                                        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                                        imm?.showInputMethodPicker()
                                    },
                                    savePrefsDebounced = savePrefsDebounced,
                                    onRequestReset = { showResetDialog = true }
                                )
                            }
                        }

                        if (!isLandscape || showLandscapePreview) {
                            Spacer(modifier = Modifier.height(2.dp))
                            SettingsKeyboardPreview(
                                prefs = prefs,
                                palette = activePalette,
                                testFieldValue = testFieldValue,
                                onTestFieldChange = { testFieldValue = it }
                            )
                        }
                    }

                    if (showResetDialog) {
                        AlertDialog(
                            onDismissRequest = { showResetDialog = false },
                            title = {
                                Text(
                                    "Сбросить всё к эталону?",
                                    fontFamily = KeskeFontFamily
                                )
                            },
                            text = {
                                Text(
                                    "Будут потеряны пользовательские раскладки всех языков, выбранная палитра " +
                                        "и настройки геометрии. Действие необратимо.",
                                    fontFamily = KeskeFontFamily,
                                    fontSize = 13.sp
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        savePrefs(KeyboardPreferences())
                                        selectedCell = null
                                        showResetDialog = false
                                    }
                                ) {
                                    Text("Сбросить", fontFamily = KeskeFontFamily)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetDialog = false }) {
                                    Text("Отмена", fontFamily = KeskeFontFamily)
                                }
                            },
                            containerColor = activePalette.keyBg,
                            titleContentColor = activePalette.keyText,
                            textContentColor = activePalette.hintText
                        )
                    }
                }
            }
        }
    }
}
