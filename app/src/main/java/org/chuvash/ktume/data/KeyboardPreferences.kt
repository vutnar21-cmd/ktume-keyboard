package org.chuvash.ktume.data

/**
 * МОДУЛЬ: KeyboardPreferences.kt
 * Неизменяемая модель всех настроек клавиатуры: палитра, геометрия, звук, поведение ввода
 * и пользовательские раскладки по языкам.
 *
 * Значения по умолчанию — эталон из AGENT.md. Если меняешь значение, которое уже могло
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
    val hexagonalGrid: Boolean = false, // Включается ТОЛЬКО в настройках
    val soundEnabled: Boolean = true,   // Звук при нажатии включён по умолчанию
    val vibrationEnabled: Boolean = true,
    val autoCapitalize: Boolean = true, // Автозаглавная на пустом поле и после точки
    val swipeUpFirstVariant: Boolean = true, // Быстрый ввод 1-го символа из Long-Press свайпом вверх
    val doubleSpaceComma: Boolean = true, // Двойной пробел ставит запятую ", "
    val showHyphenKey: Boolean = false,   // Отдельная клавиша дефиса "-" (по умолчанию выкл.: шире пробел; дефис — свайп влево от точки)
    val showCursorArrows: Boolean = true, // Стрелки курсора ← / → в левой части верхней панели
    val experimentalCapitalizeNames: Boolean = false, // Эксперимент: автозаглавные имена собственные
    val experimentalAutocorrect: Boolean = false, // Эксперимент: автозамена опечаток по пробелу (задача 013)
    val autocorrectLangs: Set<String> = setOf("cv", "ru"), // Словари автозамены (коды KeyboardLang)
    val autocorrectLearnWords: Boolean = true, // Слово, возвращённое через ⌫ после автозамены, — в личный словарь
    val recentEmojis: List<String> = emptyList(), // Недавно использованные смайлики (до 24 шт.)
    val customRowsByLang: Map<KeyboardLang, List<List<String>>> = mapOf(
        KeyboardLang.CHUVASH to KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, ChuvashLayoutPreset.BALANCED_26),
        KeyboardLang.RUSSIAN to KeyboardLayouts.getDefaultRows(KeyboardLang.RUSSIAN),
        KeyboardLang.LATINA to KeyboardLayouts.getDefaultRows(KeyboardLang.LATINA),
        KeyboardLang.GREEK to KeyboardLayouts.getDefaultRows(KeyboardLang.GREEK)
    )
)
