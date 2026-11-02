package org.chuvash.ktume.ime

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.chuvash.ktume.data.ChuvashLayoutPreset
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KeskeFontFamily
import org.chuvash.ktume.ui.theme.KeskePalettes
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: SettingsSections.kt — вкладки экрана настроек:
 * «Раскладка», «Оформление», «Ввод», «Эксперименты» и «Система».
 */
internal enum class SettingsTab(val title: String) {
    LAYOUT("Раскладка"),
    APPEARANCE("Оформление"),
    INPUT("Ввод"),
    EXPERIMENTS("Эксперименты"),
    SYSTEM("Система")
}

@Composable
internal fun LayoutSettingsSection(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    savePrefs: (KeyboardPreferences) -> Unit,
    editingLang: KeyboardLang,
    onEditingLangChange: (KeyboardLang) -> Unit,
    selectedCell: Pair<Int, Int>?,
    onSelectedCellChange: (Pair<Int, Int>?) -> Unit
) {
    SettingsSectionCard(
        title = "Архитектура чувашской раскладки и Соты (Hex)",
        palette = palette
    ) {
        SettingToggleRow(
            title = "⬡ Паркетная сотовая сетка (4 ряда: 7 + 7 + 7 + 5)",
            subtitle = "Скруглённые шестиугольники, плотно вложенные друг в друга без съезжания",
            checked = prefs.hexagonalGrid,
            palette = palette,
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
                        customRowsByLang = updatedMap
                    )
                )
                onSelectedCellChange(null)
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        ChuvashLayoutPreset.entries.forEach { preset ->
            val isSelected = prefs.chuvashPreset == preset.id
            Surface(
                color = if (isSelected) palette.accentBg.copy(alpha = 0.16f) else palette.boardBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.accentBg else palette.hintText.copy(alpha = 0.25f),
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
                        onSelectedCellChange(null)
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = preset.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.keyText
                    )
                    Text(
                        text = preset.subtitle,
                        fontSize = 11.sp,
                        color = palette.hintText
                    )
                }
            }
        }
    }

    SettingsRowEditor(
        prefs = prefs,
        palette = palette,
        savePrefs = savePrefs,
        editingLang = editingLang,
        onEditingLangChange = onEditingLangChange,
        selectedCell = selectedCell,
        onSelectedCellChange = onSelectedCellChange
    )

    SettingsSectionCard(
        title = "Активные языковые раскладки",
        palette = palette
    ) {
        KeyboardLang.entries.forEach { lang ->
            val isChecked = prefs.enabledLayouts.contains(lang.code)
            SettingToggleRow(
                title = "${lang.spaceLabel} (${lang.shortBadge})",
                subtitle = "Переключение глобусом 🌐 (удержание открывает быстрое меню)",
                checked = isChecked,
                palette = palette,
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

@Composable
internal fun AppearanceSettingsSection(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    savePrefs: (KeyboardPreferences) -> Unit,
    savePrefsDebounced: (KeyboardPreferences) -> Unit
) {
    SettingsSectionCard(
        title = "Цветовые темы (с предпросмотром палитры)",
        palette = palette
    ) {
        Text(
            text = "Тема настроек и клавиатуры синхронизированы:",
            style = MaterialTheme.typography.bodySmall,
            color = palette.hintText
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

    SettingsSectionCard(
        title = "Геометрия клавиш, шрифт и задержка Long-Press",
        palette = palette
    ) {
        SettingToggleRow(
            title = "Компактный режим в альбомной ориентации",
            subtitle = "Автоматически уменьшает высоту рядов и шрифт при горизонтальном повороте экрана (не меняя книжные настройки)",
            checked = prefs.compactLandscape,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(compactLandscape = it)) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingSliderRow(
            label = "Высота клавиш (по умолч. 50): ${prefs.keyHeightDp.roundToInt()} dp",
            value = prefs.keyHeightDp,
            range = 38f..64f,
            onValueChange = { savePrefsDebounced(prefs.copy(keyHeightDp = it)) }
        )
        SettingSliderRow(
            label = "Скругление углов (по умолч. 14): ${prefs.keyCornerRadiusDp.roundToInt()} dp",
            value = prefs.keyCornerRadiusDp,
            range = 4f..24f,
            onValueChange = { savePrefsDebounced(prefs.copy(keyCornerRadiusDp = it)) }
        )
        SettingSliderRow(
            label = "Зазор по горизонтали (по умолч. 3): ${prefs.keyGapHDp.roundToInt()} dp",
            value = prefs.keyGapHDp,
            range = 0f..10f,
            onValueChange = { savePrefsDebounced(prefs.copy(keyGapHDp = it)) }
        )
        SettingSliderRow(
            label = "Зазор по вертикали (по умолч. 7): ${prefs.keyGapVDp.roundToInt()} dp",
            value = prefs.keyGapVDp,
            range = 0f..14f,
            onValueChange = { savePrefsDebounced(prefs.copy(keyGapVDp = it)) }
        )
        SettingSliderRow(
            label = "Размер шрифта клавиш (по умолч. 26): ${prefs.fontSizeSp.roundToInt()} sp",
            value = prefs.fontSizeSp,
            range = 14f..28f,
            onValueChange = { savePrefsDebounced(prefs.copy(fontSizeSp = it)) }
        )
        SettingSliderRow(
            label = "Задержка долгого нажатия (Long-Press): ${prefs.longPressDelayMs} мс",
            value = prefs.longPressDelayMs.toFloat(),
            range = 140f..400f,
            onValueChange = { savePrefsDebounced(prefs.copy(longPressDelayMs = it.roundToInt())) }
        )
    }
}

@Composable
internal fun InputSettingsSection(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    savePrefs: (KeyboardPreferences) -> Unit,
    savePrefsDebounced: (KeyboardPreferences) -> Unit
) {
    SettingsSectionCard(
        title = "Ввод, автозаглавная буква, звук и громкость",
        palette = palette
    ) {
        SettingToggleRow(
            title = "Автоматическая заглавная буква",
            subtitle = "Включает заглавную букву на пустой строке и после точки с пробелом (в полях с поддержкой заглавных)",
            checked = prefs.autoCapitalize,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(autoCapitalize = it)) }
        )
        SettingToggleRow(
            title = "Быстрый ввод 1-го символа свайпом вверх",
            subtitle = "Короткий свайп вверх по клавише вводит главный символ из Long-Press с мини-превью (к→г, т→д, п→б, ш→ж, .→-)",
            checked = prefs.swipeUpFirstVariant,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(swipeUpFirstVariant = it)) }
        )
        SettingToggleRow(
            title = "Всплывающее превью клавиши при нажатии",
            subtitle = "Показывает «каплю» с текущей буквой над пальцем и плавно подсвечивает символ при свайпе вверх",
            checked = prefs.showKeyPressPopup,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(showKeyPressPopup = it)) }
        )
        SettingToggleRow(
            title = "Звук при нажатии клавиш",
            subtitle = "Зависит от общей системной громкости и ползунка ниже",
            checked = prefs.soundEnabled,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(soundEnabled = it)) }
        )
        if (prefs.soundEnabled) {
            SettingSliderRow(
                label = "Громкость звука клавиш: ${prefs.soundVolumePercent.roundToInt()}%",
                value = prefs.soundVolumePercent,
                range = 5f..100f,
                onValueChange = { savePrefsDebounced(prefs.copy(soundVolumePercent = it)) }
            )
        }
        SettingToggleRow(
            title = "Тактильный виброотклик при нажатии",
            subtitle = "Системный отклик клавиатуры Android: сила и отключение — в настройках телефона",
            checked = prefs.vibrationEnabled,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(vibrationEnabled = it)) }
        )
        SettingToggleRow(
            title = "Клавиша дефиса «-» на главной странице",
            subtitle = "По умолчанию выключена, чтобы пробел был шире. Дефис всегда можно ввести свайпом влево от точки «.»",
            checked = prefs.showHyphenKey,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(showHyphenKey = it)) }
        )
        SettingToggleRow(
            title = "Стрелки курсора на верхней панели",
            subtitle = "Кнопки ← и → с автоповтором слева от значков буфера, смайликов и настроек",
            checked = prefs.showCursorArrows,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(showCursorArrows = it)) }
        )
        SettingToggleRow(
            title = "Двойной пробел ставит запятую «, »",
            subtitle = "Быстрая постановка запятой с пробелом двойным нажатием на пробел",
            checked = prefs.doubleSpaceComma,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(doubleSpaceComma = it)) }
        )
        SettingToggleRow(
            title = "Верхний цифровой ряд (1–0)",
            subtitle = if (prefs.hexagonalGrid) {
                "Сейчас включены соты (Hex) — ряд временно скрыт и вернётся после выключения сот"
            } else {
                "Отображается над буквами и страницами символов (в режиме сот скрывается)"
            },
            checked = prefs.showNumberRow,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(showNumberRow = it)) }
        )
        SettingToggleRow(
            title = "Вторичные микро-метки (Sub-labels) в углу клавиш",
            subtitle = "По умолчанию выключены для чистого вида клавиш",
            checked = prefs.showSubLabels,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(showSubLabels = it)) }
        )
    }
}

@Composable
internal fun ExperimentsSettingsSection(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    savePrefs: (KeyboardPreferences) -> Unit
) {
    SettingsSectionCard(
        title = "Экспериментальные функции",
        palette = palette
    ) {
        Text(
            text = "Функции в разработке: могут вести себя непредсказуемо. " +
                "Включайте по одной, чтобы понять, что именно сломалось.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.hintText
        )
        SettingToggleRow(
            title = "Автозаглавные имена собственные",
            subtitle = "Если набрать имя со строчной буквы (например, «иван») " +
                "и поставить пробел, первая буква станет заглавной. " +
                "Работает по базовому списку имён.",
            checked = prefs.experimentalCapitalizeNames,
            palette = palette,
            onCheckedChange = { savePrefs(prefs.copy(experimentalCapitalizeNames = it)) }
        )
    }
}

@Composable
internal fun SystemSettingsSection(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    onOpenImeSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    savePrefsDebounced: (KeyboardPreferences) -> Unit,
    onRequestReset: () -> Unit
) {
    SettingsSectionCard(
        title = "Подключение в системе Android",
        palette = palette
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenImeSettings,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentBg,
                    contentColor = palette.accentText
                )
            ) {
                Text("1. Включить кТӳме", fontSize = 12.sp, fontFamily = KeskeFontFamily)
            }
            FilledTonalButton(
                onClick = onShowImePicker,
                modifier = Modifier.weight(1f)
            ) {
                Text("2. Выбрать метод", fontSize = 12.sp, fontFamily = KeskeFontFamily)
            }
        }
    }

    SettingsSectionCard(
        title = "Отступ снизу (защита от перекрытия навигации Android)",
        palette = palette
    ) {
        Text(
            text = "По умолчанию установлен в 20 dp (при необходимости отрегулируйте ползунок под высоту навигационной полосы вашего телефона):",
            style = MaterialTheme.typography.bodySmall,
            color = palette.hintText
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingSliderRow(
            label = "Нижний отступ (по умолч. 20): ${prefs.bottomInsetDp.roundToInt()} dp",
            value = prefs.bottomInsetDp,
            range = 0f..80f,
            onValueChange = { savePrefsDebounced(prefs.copy(bottomInsetDp = it)) }
        )
    }

    OutlinedButton(
        onClick = onRequestReset,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "↺ Сбросить все настройки и раскладки к эталону",
            fontFamily = KeskeFontFamily
        )
    }
}
