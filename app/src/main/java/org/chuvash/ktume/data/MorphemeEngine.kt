package org.chuvash.ktume.data

/**
 * МОДУЛЬ: MorphemeEngine.kt — правила стыка чувашских морфем.
 *
 * Отвечает на три вопроса:
 * 1) какой ряд сингармонизма у слова ([harmonyOf]);
 * 2) какую форму морфемы взять после этой основы ([pickForm]);
 * 3) что показать в ленте над клавиатурой ([suggestions]).
 *
 * ЧЕРНОВИК: правила намеренно простые и собраны в одном месте, чтобы носитель мог
 * поправить их без переписывания UI. Спорные случаи (поведение после л, н, р, й, м)
 * требуют вычитки.
 */
object MorphemeEngine {

    private val SOFT_VOWELS = setOf('е', 'ӗ', 'и', 'ӳ', 'я', 'ю', 'э', 'ӭ', 'ё')
    private val HARD_VOWELS = setOf('а', 'ӑ', 'у', 'ы', 'о', 'ӧ')

    /** Последняя гласная слова; null, если гласных нет. */
    fun lastVowel(word: String): Char? =
        word.lowercase().lastOrNull { it in SOFT_VOWELS || it in HARD_VOWELS }

    /** Ряд сингармонизма по последней гласной. По умолчанию — твёрдый. */
    fun harmonyOf(word: String): Harmony {
        val vowel = lastVowel(word) ?: return Harmony.HARD
        return if (vowel in SOFT_VOWELS) Harmony.SOFT else Harmony.HARD
    }

    /** Оканчивается ли слово на гласную. */
    fun endsWithVowel(word: String): Boolean {
        val last = word.lastOrNull() ?: return false
        return last.lowercaseChar() in SOFT_VOWELS || last.lowercaseChar() in HARD_VOWELS
    }

    /**
     * Выбирает форму морфемы для конкретной основы.
     * Первая форма набора применяется, когда выполнено [Morpheme.attach]; иначе берётся вторая.
     */
    fun pickForm(stem: String, morpheme: Morpheme): String {
        val forms = morpheme.forms(harmonyOf(stem))
        if (forms.isEmpty()) return ""
        if (forms.size == 1) return forms.first()

        val vowelEnding = endsWithVowel(stem)
        val takeFirst = when (morpheme.attach) {
            AttachRule.ANY -> true
            AttachRule.AFTER_VOWEL -> vowelEnding
            AttachRule.AFTER_CONSONANT -> !vowelEnding
        }
        return if (takeFirst) forms.first() else forms[1]
    }

    /**
     * Приклеивает морфему к основе с сохранением регистра первой буквы.
     * Пример: `attach("хула", loc)` → `"хулара"`, `attach("Шупашкар", loc)` → `"Шупашкарта"`.
     */
    fun attach(stem: String, morpheme: Morpheme): String {
        if (stem.isEmpty()) return pickForm(stem, morpheme)
        val form = pickForm(stem, morpheme)
        val joiner = if (morpheme.showWithDash) "-" else ""
        val result = stem + joiner + form
        return preserveCapitalization(stem, result)
    }

    /** Если слово начиналось с заглавной, результат тоже начинается с заглавной. */
    private fun preserveCapitalization(stem: String, result: String): String {
        val first = stem.firstOrNull() ?: return result
        if (!first.isUpperCase()) return result
        return result.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Строки для ленты над клавиатурой: выбранные по гармонии формы,
     * частицы — с ведущим дефисом, аффиксы — без.
     *
     * Для пустого слова возвращает базовый набор, чтобы лента не пустовала.
     */
    fun suggestions(word: String, includeExperimental: Boolean = false): List<String> {
        val morphemes = if (includeExperimental) ChuvashMorphemes.all else ChuvashMorphemes.core()
        return morphemes.map { morpheme ->
            val form = pickForm(word, morpheme)
            morpheme.display(form)
        }
    }
}
