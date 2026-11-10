package org.chuvash.ktume.data

import org.chuvash.ktume.BuildConfig

/**
 * МОДУЛЬ: KeyboardPreferences.kt
 * Неизменяемая модель всех настроек клавиатуры: палитра, геометрия, звук, поведение ввода
 * и пользовательские раскладки по языкам.
 *
 * Значения по умолчанию — эталон из AGENTS.md. Если меняешь значение, которое уже могло
 * сохраниться на устройстве, подними суффикс версии ключа в PreferencesRepository.
 */
data class KeyboardPreferences(
    val paletteId: String = "obsidian_forest",
    val chuvashPreset: String = ChuvashLayoutPreset.BALANCED_26.id,
    val keyHeightDp: Float = 50f,
    val keyCornerRadiusDp: Float = 14f,
    /** Горизонтальный зазор между клавишами в ряду. */
    val keyGapHDp: Float = 3f,
    /** Вертикальный зазор между рядами: «воздух» без сжатия ширины клавиш. */
    val keyGapVDp: Float = 7f,
    val fontSizeSp: Float = 26f,
    val bottomInsetDp: Float = 20f,     // По умолчанию 20 dp (регулируется ползунком в настройках)
    val soundVolumePercent: Float = 70f,// Регулировка громкости звука клавиш (привязана к общей громкости телефона)
    val longPressDelayMs: Int = 300, // Как в Gboard: меньше случайных попапов (быстрый вариант — свайп вверх)
    val enabledLayouts: Set<String> = setOf("cv", "ru", "lat"),
    val showNumberRow: Boolean = true,
    val showSubLabels: Boolean = false, // Micro-labels отключены по умолчанию
    val showKeyPressPopup: Boolean = true, // Всплывающая «капля» над клавишей при нажатии
    val compactLandscape: Boolean = true,  // Адаптивно компактная высота в альбомной ориентации
    val hexagonalGrid: Boolean = false, // Доступно только в Debug-сборке
    val soundEnabled: Boolean = true,   // Звук при нажатии включён по умолчанию
    val vibrationEnabled: Boolean = true,
    val autoCapitalize: Boolean = true, // Автозаглавная на пустом поле и после точки
    val swipeUpFirstVariant: Boolean = true, // Быстрый ввод 1-го символа из Long-Press свайпом вверх
    val doubleSpaceComma: Boolean = true, // Двойной пробел ставит запятую ", "
    val showHyphenKey: Boolean = false,   // Отдельная клавиша дефиса "-" (по умолчанию выкл.: шире пробел; дефис — свайп влево от точки)
    val showCursorArrows: Boolean = true, // Стрелки курсора ← / → в левой части верхней панели
    val autocorrectEnabled: Boolean = true, // Автозамена опечаток и автозаглавная имён собственных по разделителю
    val autocorrectLangs: Set<String> = setOf("cv", "ru"), // Словари автозамены (коды KeyboardLang)
    val autocorrectLearnWords: Boolean = true, // Слово, возвращённое через ⌫ после автозамены, — в личный словарь
    val recentEmojis: List<String> = emptyList(), // Недавно использованные смайлики (до 24 шт.)
    val customRowsByLang: Map<KeyboardLang, List<List<String>>> = mapOf(
        KeyboardLang.CHUVASH to KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, ChuvashLayoutPreset.BALANCED_26),
        KeyboardLang.RUSSIAN to KeyboardLayouts.getDefaultRows(KeyboardLang.RUSSIAN),
        KeyboardLang.LATINA to KeyboardLayouts.getDefaultRows(KeyboardLang.LATINA),
        KeyboardLang.GREEK to KeyboardLayouts.getDefaultRows(KeyboardLang.GREEK)
    )
) {
    /** Актуальные ряды для раскладки [lang] с учётом выбранного пресета и типа сборки. */
    fun rowsFor(lang: KeyboardLang, isDebugBuild: Boolean = BuildConfig.DEBUG): List<List<String>> {
        val preset = ChuvashLayoutPreset.resolve(chuvashPreset, isDebugBuild)
        val fallback = if (lang == KeyboardLang.CHUVASH) {
            KeyboardLayouts.getDefaultRows(lang, preset)
        } else {
            KeyboardLayouts.getDefaultRows(lang)
        }
        val saved = customRowsByLang[lang] ?: return fallback
        if (!isDebugBuild && saved.size == 4) return fallback
        return saved
    }

    /**
     * Приводит настройки к ограничениям сборки:
     * - в Release фиксирует эталонную геометрию клавиш (`50 dp`, `14 dp`, `3 dp`, `7 dp`, `26 sp`) и отключает Hex;
     * - в Debug оставляет и соты, и все ползунки геометрии.
     */
    fun sanitizedForBuild(isDebugBuild: Boolean = BuildConfig.DEBUG): KeyboardPreferences {
        val resolvedPreset = ChuvashLayoutPreset.resolve(chuvashPreset, isDebugBuild)
        if (isDebugBuild) {
            return if (resolvedPreset.id == chuvashPreset) this else copy(chuvashPreset = resolvedPreset.id)
        }
        val sanitizedCustomRows = customRowsByLang.mapValues { (lang, rows) ->
            if (rows.size == 4) {
                if (lang == KeyboardLang.CHUVASH) {
                    KeyboardLayouts.getDefaultRows(lang, resolvedPreset)
                } else {
                    KeyboardLayouts.getDefaultRows(lang)
                }
            } else {
                rows
            }
        }
        return copy(
            chuvashPreset = resolvedPreset.id,
            keyHeightDp = DEFAULT_KEY_HEIGHT_DP,
            keyCornerRadiusDp = DEFAULT_KEY_CORNER_RADIUS_DP,
            keyGapHDp = DEFAULT_KEY_GAP_H_DP,
            keyGapVDp = DEFAULT_KEY_GAP_V_DP,
            fontSizeSp = DEFAULT_FONT_SIZE_SP,
            hexagonalGrid = false,
            customRowsByLang = sanitizedCustomRows
        )
    }

    companion object {
        const val DEFAULT_KEY_HEIGHT_DP = 50f
        const val DEFAULT_KEY_CORNER_RADIUS_DP = 14f
        const val DEFAULT_KEY_GAP_H_DP = 3f
        const val DEFAULT_KEY_GAP_V_DP = 7f
        const val DEFAULT_FONT_SIZE_SP = 26f
    }
}
