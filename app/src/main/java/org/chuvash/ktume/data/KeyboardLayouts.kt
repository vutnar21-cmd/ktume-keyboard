package org.chuvash.ktume.data

import kotlin.math.abs

/**
 * МОДУЛЬ: KeyboardLayouts.kt — языковые раскладки, пресеты сетки, слои символов,
 * таблицы долгого нажатия (Long-Press) и математика выбора вариантов / свайпа вверх.
 *
 * Каталог эмодзи выделен в [EmojiCatalog], словарь и движок морфем — в [ChuvashMorphemes] и [MorphemeEngine],
 * а чистая логика контекста редактора, Unicode и геометрии — в [EditorTextLogic].
 */
enum class KeyboardLang(val code: String, val spaceLabel: String, val shortBadge: String) {
    CHUVASH("cv", "Чӑвашла", "ЧВ"),
    RUSSIAN("ru", "Русская", "РУ"),
    LATINA("lat", "Latina", "LAT"),
    GREEK("el", "Ελληνικά", "ΕΛ")
}

enum class ChuvashLayoutPreset(val id: String, val title: String, val subtitle: String) {
    BALANCED_26(
        "balanced_26",
        "Эталонная сбалансированная (10 + 9 + 7 = 26 клавиш)",
        "По умолчанию: ряды 10, 9 и 7 без пустых краёв; э→е, ь/ъ→ы"
    ),
    HEX_4ROW(
        "hex_4row",
        "Сотовая паркетная 4 ряда (7 + 7 + 7 + 5 = 26 клавиш)",
        "Для режима сот (Hex): все 26 букв + Shift и ⌫, плотно вложенные паркетом без съезжания"
    ),
    FULL_28(
        "full_28",
        "Расширенная (10 + 10 + 8 = 28 клавиш)",
        "Все фонемы Чӑвашла + э, ь на первом слое"
    )
}

object KeyboardLayouts {

    /** Долгие варианты для всех цифр (0–9) и знаков препинания. */
    val digitAndSymbolLongPress: Map<String, List<String>> = mapOf(
        "1" to listOf("¹", "½", "⅓", "¼", "⅕", "⅙", "⅛", "¡", "①"),
        "2" to listOf("²", "⅔", "⅖", "②", "„", "“"),
        "3" to listOf("³", "¾", "⅗", "⅜", "③", "№"),
        "4" to listOf("⁴", "⅘", "④", "$", "€", "£"),
        "5" to listOf("⁵", "⅚", "⅝", "⑤", "‰", "‱"),
        "6" to listOf("⁶", "⑥", "^", "∧"),
        "7" to listOf("⁷", "⅞", "⑦", "&", "§"),
        "8" to listOf("⁸", "∞", "⑧", "*", "•"),
        "9" to listOf("⁹", "⑨", "(", "[", "{", "⟨"),
        "0" to listOf("⁰", "∅", "°", "⓪", ")", "]", "}", "⟩"),
        "." to listOf("-", "?", "!", "…", "—", "–", ":", ";", "«", "»", "„", "“"),
        "," to listOf("-", ";", ":", "!", "?", "—", "«", "»", "'", "\""),
        "-" to listOf("–", "—", "_", "±", "≈", "≠"),
        "+" to listOf("±", "×", "÷", "=", "≈", "≠", "≤", "≥", "∞"),
        "₽" to listOf("$", "€", "£", "¥", "₴", "₸", "₺", "₿", "¢"),
        "$" to listOf("€", "₽", "£", "¥", "₴", "₸", "₺", "₿", "¢"),
        "%" to listOf("‰", "‱", "°", "℃"),
        "\"" to listOf("«", "»", "„", "“", "”", "‹", "›"),
        "'" to listOf("‘", "’", "‚", "‛", "`", "´"),
        "_" to listOf("–", "—", "·", "‿"),
        "&" to listOf("§", "¶"),
        "/" to listOf("÷", "⁄", "\\"),
        "(" to listOf("[", "{", "⟨", "〈"),
        ")" to listOf("]", "}", "⟩", "〉"),
        "@" to listOf("©", "®", "™", "№"),
        "=" to listOf("≈", "≠", "≤", "≥", "≡"),
        "|" to listOf("¦", "‖", "∣"),
        "√" to listOf("∛", "∜", "∞"),
        "§" to listOf("¶", "©", "®"),
        "…" to listOf("‥", "⋯")
    )

    /**
     * Карта модификаций для раскладки «Чӑвашла»:
     * первый символ в списке показывается на микро-метке, вводится свайпом вверх и первым в Long-Press.
     */
    val chuvashLongPress: Map<String, List<String>> = mapOf(
        "й" to listOf("ј", "ї"),
        "ӑ" to listOf("ә"),
        "у" to listOf("ў", "ү", "у́", "ū"),
        "к" to listOf("г", "ќ", "қ", "ҡ", "ґ", "ѓ", "ғ"),
        "е" to listOf("ё", "э", "є", "е́", "ē", "ѣ"),
        "н" to listOf("њ", "ң", "ҥ", "н́"),
        "ӗ" to listOf("ә", "ӛ"),
        "ш" to listOf("ж", "щ", "җ", "ш́"),
        "х" to listOf("һ", "ҳ"),
        "ӳ" to listOf("ү", "ӱ"),
        "ф" to listOf("ѳ"),
        "ы" to listOf("ь", "ъ", "и"),
        "в" to listOf("ў"),
        "а" to listOf("ä", "а́", "ā"),
        "п" to listOf("б", "ҧ"),
        "р" to listOf("р́", "ҏ"),
        "о" to listOf("ӧ", "ө", "о́"),
        "л" to listOf("љ", "ԓ", "л́"),
        "ҫ" to listOf("щ", "ѕ"),
        "э" to listOf("є", "ӗ", "ә"),
        "я" to listOf("я́", "ѣ"),
        "ч" to listOf("ц", "џ", "ћ", "җ", "ҷ"),
        "с" to listOf("з", "ѕ", "с́"),
        "м" to listOf("м́"),
        "и" to listOf("і", "ї", "и́", "ӣ"),
        "т" to listOf("д", "ђ", "ћ", "ҭ"),
        "ь" to listOf("ъ", "ѣ"),
        "ю" to listOf("ю́"),
        "ц" to listOf("џ", "ѕ", "ц́")
    )

    val russianLongPress: Map<String, List<String>> = mapOf(
        "и" to listOf("й", "і", "ї", "ј", "и́", "ӣ"),
        "е" to listOf("ё", "ӗ", "э", "є", "е́", "ē"),
        "а" to listOf("ӑ", "ә", "ä", "а́"),
        "с" to listOf("ҫ", "ѕ", "с́"),
        "у" to listOf("ӳ", "ў", "ү", "ӱ"),
        "ь" to listOf("ъ", "ѣ"),
        "г" to listOf("ґ", "ѓ", "ғ"),
        "к" to listOf("ќ", "қ", "ҡ"),
        "д" to listOf("ђ", "ѓ"),
        "т" to listOf("ћ", "ђ", "ҭ"),
        "л" to listOf("љ", "ԓ"),
        "н" to listOf("њ", "ң", "ҥ"),
        "ч" to listOf("џ", "ћ", "җ", "ҷ"),
        "ц" to listOf("џ", "ѕ"),
        "ш" to listOf("щ", "ш́"),
        "х" to listOf("һ", "ҳ"),
        "о" to listOf("ӧ", "ө")
    )

    /** Долгие варианты для латинской раскладки Latina (`a–z`). */
    val latinaLongPress: Map<String, List<String>> = mapOf(
        "a" to listOf("ă", "ä", "á", "à", "â", "ã", "å", "ā", "ą", "æ"),
        "b" to listOf("ß", "ɓ"),
        "c" to listOf("č", "ć", "ç", "ĉ", "ċ"),
        "d" to listOf("đ", "ď", "ð"),
        "e" to listOf("ĕ", "é", "è", "ê", "ë", "ē", "ė", "ę", "ě", "ə"),
        "g" to listOf("ğ", "ĝ", "ġ", "ģ"),
        "h" to listOf("ħ", "ĥ"),
        "i" to listOf("í", "ì", "î", "ï", "ī", "į", "ı", "ј"),
        "j" to listOf("ĵ", "ј"),
        "l" to listOf("ł", "ľ", "ĺ", "ļ", "љ"),
        "n" to listOf("ñ", "ń", "ň", "ņ", "ŋ", "њ"),
        "o" to listOf("ö", "ó", "ò", "ô", "õ", "ø", "œ", "ō", "ő"),
        "r" to listOf("ř", "ŕ", "ŗ"),
        "s" to listOf("š", "ś", "ş", "ß", "ŝ", "ș"),
        "t" to listOf("ť", "ţ", "ț", "þ"),
        "u" to listOf("ü", "ÿ", "ú", "ù", "û", "ū", "ů", "ű", "ų", "ŭ"),
        "y" to listOf("ÿ", "ý", "ŷ"),
        "z" to listOf("ž", "ź", "ż", "џ")
    )

    val greekLongPress: Map<String, List<String>> = mapOf(
        "α" to listOf("ά", "ἀ", "ἁ"),
        "ε" to listOf("έ", "ἐ", "ἑ"),
        "η" to listOf("ή", "ἠ", "ἡ"),
        "ι" to listOf("ί", "ϊ", "ΐ"),
        "ο" to listOf("ό", "ὀ", "ὁ"),
        "υ" to listOf("ύ", "ϋ", "ΰ"),
        "ω" to listOf("ώ", "ὠ", "ὡ"),
        "σ" to listOf("ς")
    )

    /**
     * Общий верхний ряд цифр. Показывается и над буквами, и над страницами символов,
     * поэтому высота клавиатуры не меняется при переключении между ними.
     */
    val numberRow: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    /**
     * Слой спецсимволов 1 (`?123`) — три ряда символов (`10 + 10 + 7`).
     * Цифры `1–0` живут только в общем ряду [numberRow].
     */
    val symbolsLayer1: List<List<String>> = listOf(
        listOf("%", "^", "~", "`", "|", "\\", "{", "}", "[", "]"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?")
    )

    /** Слой спецсимволов 2 (`=\<`) — валюты, математика, типографика (`10 + 10 + 7`). */
    val symbolsLayer2: List<List<String>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("€", "£", "¥", "₽", "©", "®", "™", "§", "№", "="),
        listOf("…", "—", "–", "‹", "›", "«", "»")
    )

    /** Телефонная сетка Numpad (`3 × 4`; 4-я колонка действий рисуется в `NumpadGridView`). */
    val numpadRows: List<List<String>> = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(",", "0", ".")
    )

    val emojiCategories: List<EmojiCategory> get() = EmojiCatalog.categories

    /** Эталонные ряды для выбранной раскладки (по умолчанию для Чӑвашла: `10 + 9 + 7 = 26`). */
    fun getDefaultRows(
        lang: KeyboardLang,
        preset: ChuvashLayoutPreset = ChuvashLayoutPreset.BALANCED_26
    ): List<List<String>> {
        return when (lang) {
            KeyboardLang.CHUVASH -> when (preset) {
                ChuvashLayoutPreset.BALANCED_26 -> listOf(
                    listOf("й", "ӑ", "у", "к", "е", "н", "ӗ", "ш", "х", "ӳ"),
                    listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "ҫ"),
                    listOf("я", "ч", "с", "м", "и", "т", "ю")
                )
                ChuvashLayoutPreset.HEX_4ROW -> listOf(
                    listOf("й", "ӑ", "у", "к", "е", "н", "ӗ"),
                    listOf("ф", "ы", "в", "а", "п", "р", "о"),
                    listOf("л", "ҫ", "ш", "х", "ӳ", "я", "ч"),
                    listOf("с", "м", "и", "т", "ю")
                )
                ChuvashLayoutPreset.FULL_28 -> listOf(
                    listOf("й", "ӑ", "у", "к", "е", "н", "ӗ", "ш", "х", "ӳ"),
                    listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "ҫ", "э"),
                    listOf("я", "ч", "с", "м", "и", "т", "ь", "ю")
                )
            }
            KeyboardLang.RUSSIAN -> listOf(
                listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"),
                listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
                listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
            )
            KeyboardLang.LATINA -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m")
            )
            KeyboardLang.GREEK -> listOf(
                listOf("ς", "ε", "ρ", "τ", "υ", "θ", "ι", "ο", "π"),
                listOf("α", "σ", "δ", "φ", "γ", "η", "ξ", "κ", "λ"),
                listOf("ζ", "χ", "ψ", "ω", "β", "ν", "μ")
            )
        }
    }

    /**
     * Сетка 4 рядов для паркетных сот.
     * Для раскладок из 26 букв сохраняет эталонный паркет (`7 + 7 + 7 + [Shift + 5 букв + ⌫]`).
     * Для раскладок другого размера распределяет все клавиши по 4 рядам без потери символов.
     */
    fun getHex4RowLayout(currentRows: List<List<String>>): List<List<String>> {
        if (currentRows.size == 4 && currentRows.all { it.isNotEmpty() }) return currentRows
        val flat = currentRows.flatten()
        if (flat.isEmpty()) {
            return getDefaultRows(KeyboardLang.CHUVASH, ChuvashLayoutPreset.HEX_4ROW)
        }
        if (flat.size == 26) {
            return listOf(
                flat.subList(0, 7),
                flat.subList(7, 14),
                flat.subList(14, 21),
                flat.subList(21, 26)
            )
        }

        val totalSlots = flat.size + 2
        val baseCols = totalSlots / 4
        val rem = totalSlots % 4
        val row0Size = (baseCols + if (rem > 0) 1 else 0).coerceAtLeast(1)
        val row1Size = (baseCols + if (rem > 1) 1 else 0).coerceAtLeast(1)
        val row2Size = (baseCols + if (rem > 2) 1 else 0).coerceAtLeast(1)

        val cut1 = row0Size.coerceAtMost(flat.size)
        val cut2 = (cut1 + row1Size).coerceAtMost(flat.size)
        val cut3 = (cut2 + row2Size).coerceAtMost((flat.size - 1).coerceAtLeast(cut2))

        val rows = listOf(
            flat.subList(0, cut1),
            flat.subList(cut1, cut2),
            flat.subList(cut2, cut3),
            flat.subList(cut3, flat.size)
        ).filter { it.isNotEmpty() }

        return if (rows.size == 4) rows else {
            val padded = rows.toMutableList()
            while (padded.size < 4 && padded.any { it.size > 1 }) {
                val idx = padded.indexOfFirst { it.size > 1 }
                val split = padded[idx]
                padded[idx] = split.subList(0, split.size - 1)
                padded.add(idx + 1, split.subList(split.size - 1, split.size))
            }
            padded
        }
    }

    /** Лента частиц и аффиксов для текущего слова (фасад к [MorphemeEngine.suggestions]). */
    fun getSynharmonicParticlesAndAffixes(currentWord: String): List<String> =
        MorphemeEngine.suggestions(currentWord)

    /** Полный пул букв для добавления клавиши в ряд при кастомизации. */
    fun getCandidatePool(lang: KeyboardLang): List<String> {
        return when (lang) {
            KeyboardLang.CHUVASH -> listOf(
                "й", "ӑ", "у", "к", "е", "н", "ӗ", "ш", "х", "ӳ",
                "ф", "ы", "в", "а", "п", "р", "о", "л", "ҫ", "э",
                "я", "ч", "с", "м", "и", "т", "ь", "ю",
                "б", "г", "д", "ж", "з", "ц", "щ", "ъ", "ё", "-"
            )
            KeyboardLang.RUSSIAN -> listOf(
                "й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ",
                "ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э", "ё",
                "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "-"
            )
            KeyboardLang.LATINA -> ('a'..'z').map { it.toString() } + listOf("ă", "ĕ", "ç", "ÿ", "-")
            KeyboardLang.GREEK -> listOf(
                "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ",
                "ν", "ξ", "ο", "π", "ρ", "σ", "ς", "τ", "υ", "φ", "χ", "ψ", "ω"
            )
        }
    }

    fun getLongPressVariants(lang: KeyboardLang, keyLower: String, isShifted: Boolean): List<String> {
        val lower = keyLower.lowercase()
        val map = when (lang) {
            KeyboardLang.CHUVASH -> chuvashLongPress
            KeyboardLang.RUSSIAN -> russianLongPress
            KeyboardLang.LATINA -> latinaLongPress
            KeyboardLang.GREEK -> greekLongPress
        }
        val list = map[lower] ?: digitAndSymbolLongPress[lower] ?: emptyList()
        return if (isShifted) list.map { it.uppercase() } else list
    }

    /** Порог правой половины клавиатуры (`colFraction >= 0.5f`): попап раскрывается влево (`TopEnd`). */
    const val RIGHT_HALF_COL_THRESHOLD = 0.5f

    fun isRightSideKey(colFraction: Float): Boolean = colFraction >= RIGHT_HALF_COL_THRESHOLD

    /**
     * Видимые варианты в Popup долгого нажатия (единый список для рендеринга, выбора и вставки).
     * На правой половине (`colFraction >= 0.5f`) разворачиваются справа налево с сохранением `v0` над клавишей.
     */
    fun getDisplayedLongPressVariants(
        variants: List<String>,
        colFraction: Float = 0f,
        maxCount: Int = 7
    ): List<String> {
        val top = variants.take(maxCount)
        return if (isRightSideKey(colFraction)) top.reversed() else top
    }

    /** Вычисляет индекс выбранного варианта внутри видимого списка ([getDisplayedLongPressVariants]). */
    fun computeLongPressSelectedIndex(
        variants: List<String>,
        colFraction: Float,
        dragSteps: Int,
        maxCount: Int = 7
    ): Int {
        val count = variants.size.coerceAtMost(maxCount)
        if (count == 0) return 0
        val base = if (isRightSideKey(colFraction)) count - 1 else 0
        return (base + dragSteps).coerceIn(0, count - 1)
    }

    /** Индекс ячейки в [getDisplayedLongPressVariants], вставляемой при долгом нажатии без движения. */
    fun initialLongPressVariantIndex(variants: List<String>, colFraction: Float, maxCount: Int = 7): Int {
        val count = variants.size.coerceAtMost(maxCount)
        if (count == 0) return 0
        return if (isRightSideKey(colFraction)) count - 1 else 0
    }

    /** Главный (1-й по частотности) символ из списка Long-Press (микро-метка и свайп вверх). */
    fun primaryLongPressVariant(variants: List<String>): String? = variants.firstOrNull()

    /** Проверяет, прошёл ли палец порог быстрого свайпа вверх в направленном конусе вверх. */
    fun isSwipeUpGesture(dx: Float, dy: Float, thresholdPx: Float): Boolean {
        if (thresholdPx <= 0f) return false
        return dy <= -thresholdPx && abs(dy) >= abs(dx) * 1.15f
    }

    /** Гистерезис удержания активного свайпа вверх до отпускания пальца. */
    fun shouldKeepSwipeUpActive(dx: Float, dy: Float, thresholdPx: Float): Boolean {
        if (thresholdPx <= 0f) return false
        return dy <= -(thresholdPx * 0.65f) && abs(dy) >= abs(dx) * 0.9f
    }

    // --- Фасады к EditorTextLogic (единая точка входа для IME, превью и тестов) ---

    fun normalizeChuvashUnicode(input: String): String =
        EditorTextLogic.normalizeChuvashUnicode(input)

    fun computeLastGraphemeLength(beforeCursor: String): Int =
        EditorTextLogic.computeLastGraphemeLength(beforeCursor)

    fun dropLastGrapheme(text: String): String =
        EditorTextLogic.dropLastGrapheme(text)

    fun extractCurrentWordBeforeCursor(beforeCursor: String): String =
        EditorTextLogic.extractCurrentWordBeforeCursor(beforeCursor)

    fun extractMorphemeWordBeforeCursor(beforeCursor: String): String =
        EditorTextLogic.extractMorphemeWordBeforeCursor(beforeCursor)

    fun computeTrailingHorizontalSpacesAfterWord(beforeCursor: String): Int =
        EditorTextLogic.computeTrailingHorizontalSpacesAfterWord(beforeCursor)

    fun computeDeleteWordLength(beforeCursor: String): Int =
        EditorTextLogic.computeDeleteWordLength(beforeCursor)

    fun canReplaceSpaceWithCommaBeforeCursor(beforeCursor: String, hasSelection: Boolean): Boolean =
        EditorTextLogic.canReplaceSpaceWithCommaBeforeCursor(beforeCursor, hasSelection)

    fun isCursorOnEmptyLine(beforeCursor: String, afterCursor: String): Boolean =
        EditorTextLogic.isCursorOnEmptyLine(beforeCursor, afterCursor)

    fun resolveEditorCapFlags(inputType: Int): Int =
        EditorTextLogic.resolveEditorCapFlags(inputType)

    fun shouldAutoCapitalize(beforeCursor: String, afterCursor: String, editorCapFlags: Int): Boolean =
        EditorTextLogic.shouldAutoCapitalize(beforeCursor, afterCursor, editorCapFlags)

    fun resolveEnterKeyAction(imeOptions: Int): EnterKeyAction =
        EditorTextLogic.resolveEnterKeyAction(imeOptions)

    fun prefersNumericPad(inputType: Int): Boolean =
        EditorTextLogic.prefersNumericPad(inputType)

    fun resolveEffectiveGeometry(prefs: KeyboardPreferences, isLandscape: Boolean): EffectiveKeyboardGeometry =
        EditorTextLogic.resolveEffectiveGeometry(prefs, isLandscape)

    fun computeKeyboardBodyHeightDp(prefs: KeyboardPreferences, alphaRowCount: Int = 3): Float =
        EditorTextLogic.computeKeyboardBodyHeightDp(prefs, alphaRowCount)

    fun resolveEmojiCategories(recentEmojis: List<String> = emptyList()): List<EmojiCategory> =
        EmojiCatalog.resolveCategories(recentEmojis)

    fun effectiveRecentEmojis(recentEmojis: List<String>): List<String> =
        EmojiCatalog.effectiveRecentEmojis(recentEmojis)

    fun recordRecentEmoji(current: List<String>, emoji: String): List<String> =
        EmojiCatalog.recordRecentEmoji(current, emoji)
}
