package org.chuvash.keske.ime

import java.text.BreakIterator

/**
 * МОДУЛЬ: KeyboardLayouts.kt
 * Отвечает за языковые раскладки ("Чӑвашла", "Русская", "Latina", "Ελληνικά"),
 * пресеты чувашской сетки (28 клавиш 10+10+8 или компактная 26 клавиш 9+9+8 под соты),
 * слои символов (?123, =\<) и телефонный Numpad 1234 (3 × 4 + колонка действий), таблицы долгого нажатия (Long-Press),
 * чувашские частицы (-и, -ши, -ха, -ҫкӗ) и словарь подсказок.
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

data class EmojiCategory(
    val id: String,
    val icon: String,
    val title: String,
    val emojis: List<String>
)

object KeyboardLayouts {

    /**
     * Долгие варианты для всех цифр (0–9) и знаков препинания.
     */
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
     * Карта модификаций для раскладки "Чӑвашла":
     * Первая буква в списке отображается как микро-метка (sub-label) в углу клавиши
     * и выбирается первой при долгом нажатии (с выбором движением пальца).
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

    /**
     * Долгие варианты для латинской раскладки Latina (a–z).
     */
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
     * Слой спецсимволов 1 (?123) — три ряда символов (10 + 10 + 7).
     * Цифры 1–0 здесь намеренно отсутствуют: они в общем ряду [numberRow],
     * иначе ряд дублировался бы и высота страницы не совпадала с буквенной.
     * В 3-м ряду 7 клавиш, чтобы вместе с "=\<" слева и "⌫" справа получилось 9,
     * как в эталонном нижнем ряду буквенной раскладки (7 + Shift + ⌫).
     */
    val symbolsLayer1: List<List<String>> = listOf(
        listOf("%", "^", "~", "`", "|", "\\", "{", "}", "[", "]"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?")
    )

    /**
     * Слой спецсимволов 2 (=\<) — валюты, математика, типографика. Строго 3 ряда (10 + 10 + 7).
     */
    val symbolsLayer2: List<List<String>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("€", "£", "¥", "₽", "©", "®", "™", "§", "№", "="),
        listOf("…", "—", "–", "‹", "›", "«", "»")
    )

    /**
     * Цифровая панель Numpad (как в Gboard/iOS): телефонная сетка 3 × 4.
     * 4-я колонка — функциональные клавиши (⌫, −, +, ↵), они рисуются в NumpadGridView.
     */
    val numpadRows: List<List<String>> = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(",", "0", ".")
    )

    /**
     * Эталонные ряды для выбранной раскладки.
     * По умолчанию для Чӑвашла: 10 + 9 + 7 = 26 клавиш!
     */
    fun getDefaultRows(
        lang: KeyboardLang,
        preset: ChuvashLayoutPreset = ChuvashLayoutPreset.BALANCED_26
    ): List<List<String>> {
        return when (lang) {
            KeyboardLang.CHUVASH -> when (preset) {
                ChuvashLayoutPreset.BALANCED_26 -> listOf(
                    listOf("й", "ӑ", "у", "к", "е", "н", "ӗ", "ш", "х", "ӳ"), // 10 клавиш
                    listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "ҫ"),      // 9 клавиш
                    listOf("я", "ч", "с", "м", "и", "т", "ю")                 // 7 клавиш (+ Shift и Backspace)
                )
                ChuvashLayoutPreset.HEX_4ROW -> listOf(
                    listOf("й", "ӑ", "у", "к", "е", "н", "ӗ"), // Ряд 1: 7 сот
                    listOf("ф", "ы", "в", "а", "п", "р", "о"), // Ряд 2: 7 сот (в пазах со сдвигом 0.5w)
                    listOf("л", "ҫ", "ш", "х", "ӳ", "я", "ч"), // Ряд 3: 7 сот
                    listOf("с", "м", "и", "т", "ю")            // Ряд 4: Shift + 5 букв + ⌫ = 7 сот (в пазах со сдвигом 0.5w)
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
     * Для раскладок из 26 букв сохраняет эталонный паркет (7 + 7 + 7 + [Shift + 5 букв + ⌫] = 7 сот в каждом ряду).
     * Для раскладок другого размера (например, Русская — 31, FULL_28 — 28, Ελληνικά — 25, кастомные)
     * распределяет ВСЕ клавиши по 4 рядам без потери символов и с запасом +2 ячейки под Shift и ⌫ в 4-м ряду.
     */
    fun getHex4RowLayout(currentRows: List<List<String>>): List<List<String>> {
        if (currentRows.size == 4 && currentRows.all { it.isNotEmpty() }) return currentRows
        val flat = currentRows.flatten()
        if (flat.isEmpty()) {
            return getDefaultRows(KeyboardLang.CHUVASH, ChuvashLayoutPreset.HEX_4ROW)
        }
        if (flat.size == 26) {
            return listOf(
                flat.subList(0, 7),   // Ряд 1: 7 букв
                flat.subList(7, 14),  // Ряд 2: 7 букв (в пазах со сдвигом +0.5w)
                flat.subList(14, 21), // Ряд 3: 7 букв
                flat.subList(21, 26)  // Ряд 4: 5 букв (+ Shift слева и Backspace справа = 7 сот)
            )
        }

        val totalSlots = flat.size + 2 // +2 под Shift и Backspace в 4-м ряду
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
            // Защита для сверхмалых кастомных наборов (< 4 клавиш)
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

    /**
     * Подбор чувашских частиц и гармонирующих аффиксов по закону сингармонизма:
     * - если в текущем слове преобладают гласные переднего (мягкого) ряда (е, ӗ, ӳ, и, я, ю, э),
     *   предлагаются мягкие аффиксы (-сем, -ре, -пе, -тен, -е, -че) + частицы (-и?, -ши?, -ха, -ҫкӗ);
     * - иначе предлагаются твёрдые аффиксы (-сем, -ра, -па, -тан, -а, -чӗ) + частицы.
     */
    fun getSynharmonicParticlesAndAffixes(currentWord: String): List<String> {
        val lower = currentWord.lowercase()
        val softVowels = setOf('е', 'ӗ', 'ӳ', 'и', 'я', 'ю', 'э')
        val hardVowels = setOf('а', 'ӑ', 'у', 'ы', 'о')
        val lastVowel = lower.lastOrNull { it in softVowels || it in hardVowels }
        val isSoft = lastVowel != null && lastVowel in softVowels

        return if (currentWord.isBlank()) {
            listOf("-и?", "-ши?", "-ха", "-ҫкӗ", "-сем", "-па", "-ра")
        } else if (isSoft) {
            listOf("-и?", "-ши?", "-ха", "-ҫкӗ", "сем", "ре", "пе", "тен")
        } else {
            listOf("-и?", "-ши?", "-ха", "-ҫкӗ", "сем", "ра", "па", "тан")
        }
    }

    /**
     * Полный пул букв для добавления клавиши в ряд при кастомизации
     */
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

    /**
     * Видимые варианты в Popup долгого нажатия (единый список для рендеринга, выбора и вставки).
     */
    fun getDisplayedLongPressVariants(variants: List<String>, maxCount: Int = 7): List<String> =
        variants.take(maxCount)

    /**
     * Вычисляет индекс выбранного варианта внутри видимого списка (getDisplayedLongPressVariants).
     *
     * Преселект всегда равен 0: микро-метка в углу клавиши показывает variants.first(),
     * поэтому при долгом нажатии без движения вставляется именно первый вариант.
     * Прижатие попапа к правому краю экрана — задача позиционирования (KeyboardScreen), а не выбора.
     */
    fun computeLongPressSelectedIndex(
        variants: List<String>,
        colFraction: Float,
        dragSteps: Int,
        maxCount: Int = 7
    ): Int {
        val displayed = getDisplayedLongPressVariants(variants, maxCount)
        if (displayed.isEmpty()) return 0
        return dragSteps.coerceIn(0, displayed.lastIndex)
    }

    /**
     * Быстрые чувашские частицы и аффиксы
     */
    val chuvashParticles: List<String> = listOf(
        "-и?", "-ши?", "-ха", "-ҫкӗ", "-тӑр", "-сем", "-па", "-ра", "-тан"
    )

    val emojiCategories: List<EmojiCategory> = listOf(
        EmojiCategory(
            id = "smileys",
            icon = "😊",
            title = "Эмоции",
            emojis = listOf(
                "😊", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤩",
                "🥳", "🤗", "🤔", "😏", "😌", "😉", "🙃", "😇",
                "😅", "😆", "😁", "😄", "😃", "😀", "🥲", "😋",
                "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😒", "😞",
                "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩",
                "🥺", "😢", "😭", "😤", "😠", "😡", "🤯", "😳",
                "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "😴"
            )
        ),
        EmojiCategory(
            id = "gestures",
            icon = "👍",
            title = "Жесты и люди",
            emojis = listOf(
                "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟",
                "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋",
                "🤚", "🖐️", "🖖", "👋", "🤝", "🙏", "💪", "🦾",
                "👏", "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊",
                "🫶", "❤️‍🔥", "💃", "🕺", "🚶", "🏃", "🧘", "🫂"
            )
        ),
        EmojiCategory(
            id = "hearts",
            icon = "❤️",
            title = "Сердца и символы",
            emojis = listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                "💘", "💝", "💟", "🔥", "✨", "🌟", "💫", "⭐",
                "💥", "💯", "✅", "☑️", "❌", "❗", "❓", "💬",
                "💭", "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🎯"
            )
        ),
        EmojiCategory(
            id = "nature",
            icon = "☀️",
            title = "Природа и дом",
            emojis = listOf(
                "☀️", "🌤️", "⛅", "🌈", "❄️", "⚡", "🌊", "🌺",
                "🌸", "🌼", "🌻", "🌹", "🌷", "🌱", "🌲", "🌳",
                "🍀", "🍁", "🍂", "🐱", "🐶", "🦊", "🐻", "🐼",
                "🦁", "🐴", "🐝", "🦋", "🦅", "🦉", "🏡", "🚗",
                "🚀", "✈️", "☕", "🍵", "🍯", "🍎", "🍓", "🎂"
            )
        )
    )

    val dictionarySuggestions: Map<KeyboardLang, List<String>> = mapOf(
        KeyboardLang.CHUVASH to listOf(
            "Салам", "Тавтапуҫ", "Чӑвашла", "Ырӑ кун пултӑр", "Епле пурӑнатӑн?",
            "Кӗске", "Шупашкар", "Юрататӑп", "Пурнӑҫ", "Тӑван", "Ҫӗнӗ", "Ӳсӗм", "Чӗре",
            "Аван", "Пирӗн", "Чӑваш Ен", "Сывлӑх", "Телей", "Туслӑх", "Каҫарӑр"
        ),
        KeyboardLang.RUSSIAN to listOf(
            "Привет", "Спасибо", "Как дела?", "Чувашия", "Чебоксары", "Сегодня", "Отлично",
            "Клавиатура", "Хорошо", "Пожалуйста", "Здравствуйте", "Доброе утро", "Документ"
        ),
        KeyboardLang.LATINA to listOf(
            "Salam", "Chuvash", "Tavtapuç", "Keyboard", "Dobar dan", "Hvala", "Welcome", "Message"
        ),
        KeyboardLang.GREEK to listOf(
            "Καλημέρα", "Ευχαριστώ", "Παρακαλώ", "Ελλάδα", "Γεια"
        )
    )

    /**
     * Подбор подсказок по текущему префиксу + сингармонизм для чувашского языка
     */
    fun getSuggestionsForWord(lang: KeyboardLang, currentWord: String): List<String> {
        val baseList = dictionarySuggestions[lang] ?: emptyList()
        if (currentWord.isBlank()) return baseList.take(4)

        val lower = currentWord.lowercase()
        val matched = baseList.filter { it.lowercase().startsWith(lower) && it.lowercase() != lower }
        if (matched.isNotEmpty()) return matched.take(4)

        if (lang == KeyboardLang.CHUVASH && currentWord.length >= 2) {
            val softVowels = setOf('е', 'ӗ', 'ӳ', 'и', 'я', 'ю')
            val isSoft = lower.any { it in softVowels }
            val suffixes = if (isSoft) {
                listOf("${currentWord}сем", "${currentWord}ре", "${currentWord}пе", "${currentWord}-и?")
            } else {
                listOf("${currentWord}сен", "${currentWord}ра", "${currentWord}па", "${currentWord}-ха")
            }
            return suffixes
        }
        return baseList.take(4)
    }

    /**
     * Конвертер "ложной латиницы" (ă, ĕ, ç, ÿ -> ӑ, ӗ, ҫ, ӳ)
     */
    fun normalizeChuvashUnicode(input: String): String {
        return input
            .replace('ă', 'ӑ').replace('Ă', 'Ӑ')
            .replace('ĕ', 'ӗ').replace('Ĕ', 'Ӗ')
            .replace('ç', 'ҫ').replace('Ç', 'Ҫ')
            .replace('ÿ', 'ӳ').replace('Ÿ', 'Ӳ')
            .replace('ŭ', 'ӳ').replace('Ŭ', 'Ӳ')
    }

    /**
     * Вычисляет длину последнего графемного кластера (в UTF-16 code units) перед курсором.
     * Корректно обрабатывает суррогатные пары, ZWJ-последовательности (❤️‍🔥), модификаторы тона кожи и вариационные селекторы.
     */
    fun computeLastGraphemeLength(beforeCursor: String): Int {
        if (beforeCursor.isEmpty()) return 0
        return try {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(beforeCursor)
            val end = iterator.last()
            val start = iterator.previous()
            if (start != BreakIterator.DONE && end > start) {
                end - start
            } else {
                Character.charCount(Character.codePointBefore(beforeCursor, beforeCursor.length))
            }
        } catch (_: Exception) {
            Character.charCount(Character.codePointBefore(beforeCursor, beforeCursor.length))
        }
    }

    fun dropLastGrapheme(text: String): String {
        val len = computeLastGraphemeLength(text)
        return if (len <= 0 || len > text.length) "" else text.substring(0, text.length - len)
    }

    /**
     * Извлекает текущее набираемое слово непосредственно перед курсором из контекста редактора.
     */
    fun extractCurrentWordBeforeCursor(beforeCursor: String): String {
        if (beforeCursor.isEmpty()) return ""
        var idx = beforeCursor.length
        while (idx > 0) {
            val cp = Character.codePointBefore(beforeCursor, idx)
            if (Character.isLetter(cp)) {
                idx -= Character.charCount(cp)
            } else {
                break
            }
        }
        return beforeCursor.substring(idx)
    }

    /**
     * Проверяет, можно ли безопасно заменить предыдущий пробел на запятую с пробелом (", ").
     * Разрешено только тогда, когда нет выделенного текста, прямо перед курсором стоит одиночный пробел,
     * а перед этим пробелом стоит буква или цифра.
     */
    fun canReplaceSpaceWithCommaBeforeCursor(beforeCursor: String, hasSelection: Boolean): Boolean {
        if (hasSelection || beforeCursor.length < 2 || !beforeCursor.endsWith(" ")) return false
        val cpBeforeSpace = Character.codePointBefore(beforeCursor, beforeCursor.length - 1)
        return Character.isLetterOrDigit(cpBeforeSpace)
    }
}
