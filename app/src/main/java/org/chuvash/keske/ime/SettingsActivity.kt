package org.chuvash.keske.ime

import org.chuvash.keske.data.*

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.chuvash.keske.ui.theme.*
import kotlin.math.roundToInt

/**
 * Вкладки экрана настроек: раскладка, оформление, поведение ввода и системные параметры.
 */
private enum class SettingsTab(val title: String) {
    LAYOUT("Раскладка"),
    APPEARANCE("Оформление"),
    INPUT("Ввод"),
    EXPERIMENTS("Эксперименты"),
    SYSTEM("Система")
}

/**
 * МОДУЛЬ: SettingsActivity.kt
 * Экран настроек и студия кастомизации клавиатуры «Кеске», синхронизированный по палитре и шрифту Comfortaa.
 *
 * Структура экрана:
 * 1) Шапка с переключателем светлой/тёмной темы;
 * 2) Вкладки «Раскладка», «Оформление», «Ввод», «Система» — прокручиваемая область;
 * 3) Тестовое поле и единственный экземпляр клавиатуры, всегда видимый снизу,
 *    поэтому настройки можно проверять вживую, не покидая экран.
 *
 * Инварианты:
 * - Любое изменение сразу сохраняется в PreferencesRepository;
 * - Значение bottomInsetDp из превью не перезаписывает пользовательскую настройку;
 * - Повторный выбор уже активного пресета не сбрасывает пользовательские ряды.
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

            var testFieldValue by remember { mutableStateOf(TextFieldValue("")) }
            var editingLang by remember { mutableStateOf(KeyboardLang.CHUVASH) }
            var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            var selectedTab by remember { mutableStateOf(SettingsTab.LAYOUT) }
            var showResetDialog by remember { mutableStateOf(false) }

            val fullText = testFieldValue.text
            val selStart = testFieldValue.selection.min.coerceIn(0, fullText.length)
            val selEnd = testFieldValue.selection.max.coerceIn(0, fullText.length)
            val beforeCursor = fullText.substring(0, selStart)
            val afterCursor = fullText.substring(selEnd)

            val savePrefs: (KeyboardPreferences) -> Unit = { updated ->
                PreferencesRepository.update(this@SettingsActivity, updated)
            }

            fun playPreviewFeedback() {
                if (prefs.soundEnabled) {
                    try {
                        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
                        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
                        val maxSys = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val curSys = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val sysRatio = (curSys.toFloat() / maxSys.toFloat()).coerceIn(0f, 1f)
                        val userRatio = (prefs.soundVolumePercent / 100f).coerceIn(0f, 1f)
                        val finalVol = (sysRatio * userRatio).coerceIn(0f, 1f)
                        if (finalVol > 0.02f) {
                            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, finalVol)
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = KeskeTypography
            ) {
                Scaffold(
                    containerColor = activePalette.boardBg,
                    contentColor = activePalette.keyText
                ) { padding ->
                    // Гейт загрузки: пока DataStore не прочитан, в памяти лежат значения по умолчанию.
                    // Любая правка в этот момент записала бы их поверх сохранённых настроек и раскладок.
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Шапка приложения с переключателем Светлой/Тёмной темы
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Кеске (Kĕske)",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = activePalette.keyText
                                )
                                Text(
                                    text = "Чӑвашла • Русская • Latina • Ελληνικά",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = activePalette.hintText
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

                        // Вкладки: содержимое прокручивается, клавиатура остаётся видимой снизу
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
                        if (selectedTab == SettingsTab.SYSTEM) {
                            // 1. Подключение клавиатуры в Android
                            SettingsSectionCard(
                                title = "Подключение в системе Android",
                                palette = activePalette
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = activePalette.accentBg,
                                            contentColor = activePalette.accentText
                                        )
                                    ) {
                                        Text("1. Включить Кеске", fontSize = 12.sp, fontFamily = KeskeFontFamily)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.showInputMethodPicker()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("2. Выбрать метод", fontSize = 12.sp, fontFamily = KeskeFontFamily)
                                    }
                                }
                            }
                        }

                        // 2. Тестовое поле: системная клавиатура не открывается,
                        //    печатать нужно превью-клавиатурой, закреплённой снизу
                        SettingsSectionCard(
                            title = "Тестовое поле",
                            palette = activePalette
                        ) {
                            val softwareKeyboard = LocalSoftwareKeyboardController.current

                            OutlinedTextField(
                                value = testFieldValue,
                                onValueChange = { testFieldValue = it },
                                label = {
                                    Text(
                                        "Тестовое поле",
                                        fontFamily = KeskeFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "Начните печатать (с заглавной буквы)...",
                                        fontFamily = KeskeFontFamily,
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state ->
                                        if (state.isFocused) softwareKeyboard?.hide()
                                    },
                                textStyle = LocalTextStyle.current.copy(fontFamily = KeskeFontFamily)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                        }

                        if (selectedTab == SettingsTab.APPEARANCE) {
                            // 3. Цветовые палитры с визуальным предпросмотром каждой темы
                            SettingsSectionCard(
                                title = "Цветовые темы (с предпросмотром палитры)",
                                palette = activePalette
                            ) {
                                Text(
                                    text = "Тема настроек и клавиатуры синхронизированы:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = activePalette.hintText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    KeskePalettes.all.forEach { p ->
                                        PalettePreviewCard(
                                            item = p,
                                            isSelected = prefs.paletteId == p.id,
                                            onSelect = { savePrefs(prefs.copy(paletteId = p.id)) }
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedTab == SettingsTab.LAYOUT) {
                            // 4. Архитектура чувашской раскладки и Паркетные соты (4 ряда)
                            SettingsSectionCard(
                                title = "Архитектура чувашской раскладки и Соты (Hex)",
                                palette = activePalette
                            ) {
                                SettingToggleRow(
                                    title = "⬡ Паркетная сотовая сетка (4 ряда: 7 + 7 + 7 + 5)",
                                    subtitle = "Скруглённые шестиугольники, плотно вложенные друг в друга без съезжания",
                                    checked = prefs.hexagonalGrid,
                                    palette = activePalette,
                                    onCheckedChange = { enabled ->
                                        val targetPreset = if (enabled) ChuvashLayoutPreset.HEX_4ROW else ChuvashLayoutPreset.BALANCED_26
                                        val currentCvRows = prefs.customRowsByLang[KeyboardLang.CHUVASH]
                                        val isDefaultPresetRows = currentCvRows == null ||
                                            ChuvashLayoutPreset.entries.any {
                                                currentCvRows == KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, it)
                                            }
                                        val updatedMap = prefs.customRowsByLang.toMutableMap()
                                        if (isDefaultPresetRows) {
                                            updatedMap[KeyboardLang.CHUVASH] = KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, targetPreset)
                                        }
                                        savePrefs(
                                            prefs.copy(
                                                hexagonalGrid = enabled,
                                                chuvashPreset = if (isDefaultPresetRows) targetPreset.id else prefs.chuvashPreset,
                                                // showNumberRow не трогаем: в режиме сот ряд просто не рисуется,
                                                // а после выключения Hex настройка возвращается как была.
                                                customRowsByLang = updatedMap
                                            )
                                        )
                                        selectedCell = null
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                ChuvashLayoutPreset.entries.forEach { preset ->
                                    val isSelected = prefs.chuvashPreset == preset.id
                                    Surface(
                                        color = if (isSelected) activePalette.accentBg.copy(alpha = 0.16f) else activePalette.boardBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) activePalette.accentBg else activePalette.hintText.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                if (isSelected) return@clickable
                                                val updatedMap = prefs.customRowsByLang.toMutableMap()
                                                updatedMap[KeyboardLang.CHUVASH] = KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, preset)
                                                val isHexPreset = (preset == ChuvashLayoutPreset.HEX_4ROW)
                                                savePrefs(
                                                    prefs.copy(
                                                        chuvashPreset = preset.id,
                                                        hexagonalGrid = isHexPreset,
                                                        customRowsByLang = updatedMap
                                                    )
                                                )
                                                selectedCell = null
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = preset.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = activePalette.keyText
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                fontSize = 11.sp,
                                                color = activePalette.hintText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedTab == SettingsTab.LAYOUT) {
                            SettingsRowEditor(
                                prefs = prefs,
                                palette = activePalette,
                                savePrefs = savePrefs,
                                editingLang = editingLang,
                                onEditingLangChange = { editingLang = it },
                                selectedCell = selectedCell,
                                onSelectedCellChange = { selectedCell = it }
                            )

                            // 6. Активные раскладки
                            SettingsSectionCard(
                                title = "Активные языковые раскладки",
                                palette = activePalette
                            ) {
                                KeyboardLang.entries.forEach { lang ->
                                    val isChecked = prefs.enabledLayouts.contains(lang.code)
                                    SettingToggleRow(
                                        title = "${lang.spaceLabel} (${lang.shortBadge})",
                                        subtitle = "Переключение глобусом 🌐 в нижнем ряду",
                                        checked = isChecked,
                                        palette = activePalette,
                                        onCheckedChange = { checked ->
                                            val nextSet = prefs.enabledLayouts.toMutableSet()
                                            if (checked) nextSet.add(lang.code)
                                            else if (nextSet.size > 1) nextSet.remove(lang.code)
                                            savePrefs(prefs.copy(enabledLayouts = nextSet))
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedTab == SettingsTab.SYSTEM) {
                            // 7. Отступ снизу (по умолчанию 20 dp)
                            SettingsSectionCard(
                                title = "Отступ снизу (защита от перекрытия навигации Android)",
                                palette = activePalette
                            ) {
                                Text(
                                    text = "По умолчанию установлен в 20 dp (при необходимости отрегулируйте ползунок под высоту навигационной полосы вашего телефона):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = activePalette.hintText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SettingSliderRow(
                                    label = "Нижний отступ (по умолч. 20): ${prefs.bottomInsetDp.roundToInt()} dp",
                                    value = prefs.bottomInsetDp,
                                    range = 0f..80f,
                                    onValueChange = { savePrefs(prefs.copy(bottomInsetDp = it)) }
                                )
                            }
                        }

                        if (selectedTab == SettingsTab.APPEARANCE) {
                            // 8. Размеры, геометрия клавиш и таймер долгого нажатия
                            SettingsSectionCard(
                                title = "Геометрия клавиш, шрифт и задержка Long-Press",
                                palette = activePalette
                            ) {
                                SettingSliderRow(
                                    label = "Высота клавиш (по умолч. 48): ${prefs.keyHeightDp.roundToInt()} dp",
                                    value = prefs.keyHeightDp,
                                    range = 38f..64f,
                                    onValueChange = { savePrefs(prefs.copy(keyHeightDp = it)) }
                                )
                                SettingSliderRow(
                                    label = "Скругление углов (по умолч. 14): ${prefs.keyCornerRadiusDp.roundToInt()} dp",
                                    value = prefs.keyCornerRadiusDp,
                                    range = 4f..24f,
                                    onValueChange = { savePrefs(prefs.copy(keyCornerRadiusDp = it)) }
                                )
                                SettingSliderRow(
                                    label = "Зазор по горизонтали (по умолч. 3): ${prefs.keyGapHDp.roundToInt()} dp",
                                    value = prefs.keyGapHDp,
                                    range = 0f..10f,
                                    onValueChange = { savePrefs(prefs.copy(keyGapHDp = it)) }
                                )
                                SettingSliderRow(
                                    label = "Зазор по вертикали (по умолч. 7): ${prefs.keyGapVDp.roundToInt()} dp",
                                    value = prefs.keyGapVDp,
                                    range = 0f..14f,
                                    onValueChange = { savePrefs(prefs.copy(keyGapVDp = it)) }
                                )
                                SettingSliderRow(
                                    label = "Размер шрифта Comfortaa (по умолч. 26): ${prefs.fontSizeSp.roundToInt()} sp",
                                    value = prefs.fontSizeSp,
                                    range = 14f..28f,
                                    onValueChange = { savePrefs(prefs.copy(fontSizeSp = it)) }
                                )
                                SettingSliderRow(
                                    label = "Задержка долгого нажатия (Long-Press): ${prefs.longPressDelayMs} мс",
                                    value = prefs.longPressDelayMs.toFloat(),
                                    range = 140f..400f,
                                    onValueChange = { savePrefs(prefs.copy(longPressDelayMs = it.roundToInt())) }
                                )
                            }
                        }

                        if (selectedTab == SettingsTab.EXPERIMENTS) {
                            SettingsSectionCard(
                                title = "Экспериментальные функции",
                                palette = activePalette
                            ) {
                                Text(
                                    text = "Функции в разработке: могут вести себя непредсказуемо. " +
                                        "Включайте по одной, чтобы понять, что именно сломалось.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = activePalette.hintText
                                )
                                SettingToggleRow(
                                    title = "Автозаглавные имена собственные",
                                    subtitle = "Если набрать имя со строчной буквы (например, «иван») " +
                                        "и поставить пробел, первая буква станет заглавной. " +
                                        "Работает по базовому списку имён.",
                                    checked = prefs.experimentalCapitalizeNames,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(experimentalCapitalizeNames = it)) }
                                )
                            }
                        }

                        if (selectedTab == SettingsTab.INPUT) {
                            // 9. Поведение, дефис, двойной пробел, звук (с ползунком громкости) и виброотклик
                            SettingsSectionCard(
                                title = "Ввод, автозаглавная буква, звук и громкость",
                                palette = activePalette
                            ) {
                                SettingToggleRow(
                                    title = "Автоматическая заглавная буква",
                                    subtitle = "Включает заглавную букву на пустом поле, с новой строки и после точки",
                                    checked = prefs.autoCapitalize,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(autoCapitalize = it)) }
                                )
                                SettingToggleRow(
                                    title = "Звук при нажатии клавиш",
                                    subtitle = "Зависит от общей системной громкости и ползунка ниже",
                                    checked = prefs.soundEnabled,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(soundEnabled = it)) }
                                )
                                if (prefs.soundEnabled) {
                                    SettingSliderRow(
                                        label = "Громкость звука клавиш: ${prefs.soundVolumePercent.roundToInt()}%",
                                        value = prefs.soundVolumePercent,
                                        range = 5f..100f,
                                        onValueChange = { savePrefs(prefs.copy(soundVolumePercent = it)) }
                                    )
                                }
                                SettingToggleRow(
                                    title = "Тактильный виброотклик при нажатии",
                                    subtitle = "Короткая мягкая вибрация клавиш",
                                    checked = prefs.vibrationEnabled,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(vibrationEnabled = it)) }
                                )
                                SettingToggleRow(
                                    title = "Клавиша дефиса «-» на главной странице",
                                    subtitle = "Отдельная кнопка рядом с точкой + быстрый свайп влево от точки «.»",
                                    checked = prefs.showHyphenKey,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(showHyphenKey = it)) }
                                )
                                SettingToggleRow(
                                    title = "Двойной пробел ставит запятую «, »",
                                    subtitle = "Быстрая постановка запятой с пробелом двойным нажатием на пробел",
                                    checked = prefs.doubleSpaceComma,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(doubleSpaceComma = it)) }
                                )
                                SettingToggleRow(
                                    title = "Верхний цифровой ряд (1–0)",
                                    subtitle = "В режиме сот не отображается; после выключения Hex возвращается",
                                    checked = prefs.showNumberRow,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(showNumberRow = it)) }
                                )
                                SettingToggleRow(
                                    title = "Чувашские частицы и аффиксы (по сингармонизму)",
                                    subtitle = "Подбирает -и?, -ши?, -ха, -ҫкӗ и мягкие/твёрдые аффиксы (+сем, +ра/+ре, +па/+пе, +тан/+тен)",
                                    checked = prefs.showParticlesStrip,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(showParticlesStrip = it)) }
                                )
                                SettingToggleRow(
                                    title = "Вторичные микро-метки (Sub-labels) в углу клавиш",
                                    subtitle = "По умолчанию выключены для чистого вида клавиш",
                                    checked = prefs.showSubLabels,
                                    palette = activePalette,
                                    onCheckedChange = { savePrefs(prefs.copy(showSubLabels = it)) }
                                )
                            }
                        }

                        if (selectedTab == SettingsTab.SYSTEM) {
                            // Сброс к эталону: только на вкладке «Система» и только после подтверждения
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "↺ Сбросить все настройки и раскладки к эталону",
                                    fontFamily = KeskeFontFamily
                                )
                            }
                        }
                        }

                        // Единственный экземпляр клавиатуры: виден на любой вкладке
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, activePalette.hintText.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        ) {
                                KeyboardScreen(
                                    prefs = prefs.copy(bottomInsetDp = 8f),
                                    isFieldEmpty = KeyboardLayouts.isCursorOnEmptyLine(beforeCursor, afterCursor),
                                    editorCurrentWord = KeyboardLayouts.extractCurrentWordBeforeCursor(beforeCursor),
                                    onUpdatePrefs = { updated ->
                                        // Не перезаписываем реальный bottomInsetDp пользователя значением 8f из превью
                                        savePrefs(updated.copy(bottomInsetDp = prefs.bottomInsetDp))
                                    },
                                    onCommitText = { committed ->
                                        playPreviewFeedback()
                                        val nextText = beforeCursor + committed + afterCursor
                                        val nextCursor = (beforeCursor.length + committed.length).coerceIn(0, nextText.length)
                                        testFieldValue = TextFieldValue(nextText, TextRange(nextCursor))
                                    },
                                    onReplaceSpaceWithComma = {
                                        if (KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor(beforeCursor, selStart != selEnd)) {
                                            playPreviewFeedback()
                                            val trimmedBefore = beforeCursor.dropLast(1)
                                            val nextText = "$trimmedBefore, $afterCursor"
                                            val nextCursor = (trimmedBefore.length + 2).coerceIn(0, nextText.length)
                                            testFieldValue = TextFieldValue(nextText, TextRange(nextCursor))
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    onReplaceCurrentWord = { replacement ->
                                        playPreviewFeedback()
                                        val trimmedBefore = beforeCursor.trimEnd()
                                        val nextText = "$trimmedBefore$replacement $afterCursor"
                                        val nextCursor = (trimmedBefore.length + replacement.length + 1).coerceIn(0, nextText.length)
                                        testFieldValue = TextFieldValue(nextText, TextRange(nextCursor))
                                    },
                                    onBackspace = {
                                        playPreviewFeedback()
                                        if (selStart != selEnd) {
                                            testFieldValue = TextFieldValue(beforeCursor + afterCursor, TextRange(selStart))
                                        } else if (beforeCursor.isNotEmpty()) {
                                            val newBefore = KeyboardLayouts.dropLastGrapheme(beforeCursor)
                                            testFieldValue = TextFieldValue(newBefore + afterCursor, TextRange(newBefore.length))
                                        }
                                    },
                                    onDeleteWord = {
                                        playPreviewFeedback()
                                        val newBefore = beforeCursor.replace(Regex("\\S+\\s*$"), "")
                                        testFieldValue = TextFieldValue(newBefore + afterCursor, TextRange(newBefore.length))
                                    },
                                    onEnter = {
                                        playPreviewFeedback()
                                        val nextText = "$beforeCursor\n$afterCursor"
                                        val nextCursor = beforeCursor.length + 1
                                        testFieldValue = TextFieldValue(nextText, TextRange(nextCursor))
                                    },
                                    onMoveCursor = { delta ->
                                        val nextCursor = (selEnd + delta).coerceIn(0, fullText.length)
                                        testFieldValue = testFieldValue.copy(selection = TextRange(nextCursor))
                                    },
                                    onOpenSettings = {}
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
