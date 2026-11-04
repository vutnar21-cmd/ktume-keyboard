package org.chuvash.ktume.data.autocorrect

import kotlin.math.abs

/**
 * МОДУЛЬ: TypoCostModel.kt — цены правок для поиска исправлений.
 *
 * Единица цены: [FULL] = 2 — обычная правка (замена, пропуск, лишняя буква, перестановка соседних),
 * [CHEAP] = 1 — «вероятная» опечатка: соседняя клавиша на раскладке, пропущенный надстрочный знак
 * (`а/ӑ`, `е/ӗ`, `с/ҫ`, `у/ӳ`, `е/ё`, `и/й`, `ь/ъ`), сдвоенная или потерянная двойная буква.
 * Правка первой буквы дороже на [firstLetterExtra].
 */
class TypoCostModel(
    /** Пары соседних клавиш текущей раскладки (обе стороны, нижний регистр). */
    private val adjacentKeys: Set<Long> = emptySet()
) {
    val transpositionCost: Int = FULL

    fun substitutionCost(expected: Char, typed: Char): Int = when {
        expected == typed -> 0
        isSimilarLetter(expected, typed) -> CHEAP
        pairKey(expected, typed) in adjacentKeys -> CHEAP
        else -> FULL
    }

    /** Цена пропуска буквы [missing] кандидата; пропуск второй из двойных букв дешевле. */
    fun omissionCost(missing: Char, previous: Char): Int = if (missing == previous) CHEAP else FULL

    /**
     * Цена лишней набранной буквы `typed[index]`: дёшево только сдвоенное нажатие. Задетую соседнюю
     * клавишу не удешевляем: иначе «доми» → «дом», «марон» → «мар» — обрезка слова становится слишком дешёвой.
     */
    fun insertionCost(typed: String, index: Int): Int {
        val c = typed[index]
        val before = if (index > 0) typed[index - 1] else null
        if (before == c) return CHEAP
        return FULL + if (index == 0) firstLetterExtra else 0
    }

    /** Надбавка за правку первой буквы: в начале слова ошибаются реже всего. */
    val firstLetterExtra: Int = CHEAP

    companion object {
        const val CHEAP = 1
        const val FULL = 2

        /** Буквы, которые путают или набирают без надстрочного знака / долгого нажатия. */
        private val SIMILAR_GROUPS: List<String> = listOf(
            "аӑ", "еӗёэ", "сҫ", "уӳ", "ий", "ьъы", "шщ"
        )

        private val SIMILAR: Set<Long> = buildSet {
            for (group in SIMILAR_GROUPS) {
                for (a in group) for (b in group) if (a != b) add(pairKey(a, b))
            }
        }

        fun isSimilarLetter(a: Char, b: Char): Boolean = pairKey(a, b) in SIMILAR

        internal fun pairKey(a: Char, b: Char): Long = (a.code.toLong() shl 32) or b.code.toLong()

        /**
         * Соседство клавиш по рядам раскладки: ряды центрируются друг под другом, соседями считаются
         * клавиши в одном ряду рядом и клавиши соседних рядов, чьи центры ближе одной ширины клавиши.
         * Нелитерные метки (`⇧`, `⌫`, многосимвольные) пропускаются.
         */
        fun fromRows(rows: List<List<String>>): TypoCostModel {
            val widest = rows.maxOfOrNull { it.size } ?: return TypoCostModel()
            data class Key(val ch: Char, val row: Int, val x: Float)
            val keys = ArrayList<Key>()
            rows.forEachIndexed { r, row ->
                val offset = (widest - row.size) / 2f
                row.forEachIndexed { i, label ->
                    if (label.length == 1 && label[0].isLetter()) {
                        keys += Key(label[0].lowercaseChar(), r, offset + i + 0.5f)
                    }
                }
            }
            val pairs = HashSet<Long>()
            for (a in keys) for (b in keys) {
                if (a === b || a.ch == b.ch) continue
                val dRow = abs(a.row - b.row)
                val dx = abs(a.x - b.x)
                val near = (dRow == 0 && dx <= 1.01f) || (dRow == 1 && dx <= 1.01f)
                if (near) {
                    pairs += pairKey(a.ch, b.ch)
                    pairs += pairKey(b.ch, a.ch)
                }
            }
            return TypoCostModel(pairs)
        }
    }
}
