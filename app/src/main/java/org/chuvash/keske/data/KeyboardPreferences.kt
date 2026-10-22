package org.chuvash.keske.data

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
    val keyHeightDp: Float = 48f,
    val keyCornerRadiusDp: Float = 14f,
    val keyGapDp: Float = 3f,
    val fontSizeSp: Float = 26f,
    val bottomInsetDp: Float = 20f,     // По умолчанию 20 dp (регулируется ползунком в настройках)
    val soundVolumePercent: Float = 70f,// Регулировка громкости звука клавиш (привязана к общей громкости телефона)
    val longPressDelayMs: Int = 210,
    val enabledLayouts: Set<String> = setOf("cv", "ru", "lat"),
    val showNumberRow: Boolean = true,
    val showSubLabels: Boolean = false, // Micro-labels отключены по умолчанию
    val hexagonalGrid: Boolean = false, // Включается ТОЛЬКО в настройках
    val soundEnabled: Boolean = true,   // Звук при нажатии включён по умолчанию
    val vibrationEnabled: Boolean = true,
    val autoCapitalize: Boolean = true, // Автозаглавная на пустом поле и после точки
    val doubleSpaceComma: Boolean = true, // Двойной пробел ставит запятую ", "
    val showHyphenKey: Boolean = true,    // Отдельная клавиша дефиса "-" на главной странице + свайп влево от точки
    val showParticlesStrip: Boolean = true, // Лента чувашских частиц и аффиксов включена по умолчанию
    val customRowsByLang: Map<KeyboardLang, List<List<String>>> = mapOf(
        KeyboardLang.CHUVASH to KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, ChuvashLayoutPreset.BALANCED_26),
        KeyboardLang.RUSSIAN to KeyboardLayouts.getDefaultRows(KeyboardLang.RUSSIAN),
        KeyboardLang.LATINA to KeyboardLayouts.getDefaultRows(KeyboardLang.LATINA),
        KeyboardLang.GREEK to KeyboardLayouts.getDefaultRows(KeyboardLang.GREEK)
    )
)
