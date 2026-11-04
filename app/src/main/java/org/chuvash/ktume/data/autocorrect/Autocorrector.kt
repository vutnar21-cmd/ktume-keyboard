package org.chuvash.ktume.data.autocorrect

import android.view.inputmethod.EditorInfo
import kotlin.math.ln

/**
 * МОДУЛЬ: Autocorrector.kt — решение «исправлять ли набранное слово и на что» (эксперимент, задача 013).
 *
 * Принципы (чтобы не ломать чувашские слова и не раздражать):
 * - слово, найденное в любом подключённом словаре (чувашском, русском) или в личном словаре, не трогается;
 * - исправляются только слова от [MIN_WORD_LENGTH] букв в нижнем регистре или с заглавной первой;
 *   ВСЕ ЗАГЛАВНЫЕ (аббревиатуры) и смешанный регистр (`iPhone`) не трогаются;
 * - допустимая цена правки растёт с длиной слова ([maxCostFor]);
 * - кандидат выбирается по частоте и цене; при близких по оценке кандидатах исправления нет;
 * - кандидат должен заметно превосходить гипотезу «набрано верно, просто редкое слово» — её оценка
 *   равна частоте слова на пороге словаря ([WordTrie.minCount]): редкие словоформы не «чинятся».
 *
 * Чистая логика без Android-контекста: проверяется JVM unit-тестами.
 */
class Autocorrector(
    private val dictionaries: List<LanguageDictionary>,
    private val tuning: Tuning = Tuning()
) {

    /** Словарь одного языка автозамены. */
    class LanguageDictionary(val lang: String, val trie: WordTrie)

    /**
     * Пороги решения (натуральный логарифм частоты). Подобраны 2026-11-04 сеткой в
     * `AutocorrectDictionaryTest.reportTuningGrid`: на синтетических опечатках частых слов исправляется
     * верно ~52 %, неверно ~5 %; на редких формах вне словаря срабатывает ~2 % (часть из них — сами опечатки).
     */
    data class Tuning(
        /** Сколько «стоит» единица цены правки: 2.3 ≈ кандидат должен быть в 10 раз частотнее. */
        val costWeight: Double = 2.3,
        /** Насколько кандидат должен превосходить гипотезу «редкое, но верное слово». */
        val minGain: Double = 2.0,
        /** Разница оценок лучшего и второго кандидата, ниже которой исправление рискованно. */
        val minMargin: Double = 1.5,
        /**
         * Бонус словарю текущей раскладки (в 10 раз по частоте): без него русская опечатка на русской
         * раскладке часто «исправлялась» в чувашское слово и наоборот.
         */
        val activeLangBonus: Double = ln(10.0)
    )

    /** Итог: слово для замены [replacement] (в регистре набранного) и язык словаря кандидата. */
    data class Correction(val original: String, val replacement: String, val lang: String)

    private data class Scored(val word: String, val lang: String, val score: Double)

    /** Оценка гипотезы «набранное — верное слово чуть реже порога словаря» (берём самую строгую). */
    private val unknownWordScore: Double = dictionaries.maxOfOrNull {
        ln(it.trie.minCount.toDouble() / it.trie.totalCount.coerceAtLeast(1L))
    } ?: 0.0

    /** Есть ли слово (в любом регистре) в одном из словарей. */
    fun isKnown(word: String): Boolean {
        val lower = word.lowercase()
        return dictionaries.any { lower in it.trie }
    }

    /**
     * Исправление для [typed] или `null`, если слово известно, не подходит под правила или нет
     * уверенного кандидата. [activeLang] — код текущей раскладки (его словарь получает небольшой бонус),
     * [personalWords] — слова пользователя в нижнем регистре, [costs] — цены правок текущей раскладки.
     */
    fun correct(
        typed: String,
        activeLang: String?,
        personalWords: Set<String>,
        costs: TypoCostModel
    ): Correction? {
        if (dictionaries.isEmpty()) return null
        val caseShape = caseShapeOf(typed) ?: return null
        val lower = typed.lowercase()
        if (lower.length < MIN_WORD_LENGTH || !lower.all(::isCyrillicLetter)) return null
        if (lower in personalWords || isKnown(lower)) return null

        val maxCost = maxCostFor(lower.length)
        val best = HashMap<String, Scored>()
        for (dict in dictionaries) {
            val total = dict.trie.totalCount.coerceAtLeast(1L).toDouble()
            val bonus = if (dict.lang == activeLang) tuning.activeLangBonus else 0.0
            for (m in dict.trie.search(lower, maxCost, costs)) {
                if (m.count < MIN_CANDIDATE_COUNT || (dict.lang == "ru" && isOffensive(m.word))) continue
                val score = ln(m.count / total) - tuning.costWeight * m.cost + bonus
                val prev = best[m.word]
                if (prev == null || score > prev.score) best[m.word] = Scored(m.word, dict.lang, score)
            }
        }
        if (best.isEmpty()) return null
        val ranked = best.values.sortedByDescending { it.score }
        val top = ranked[0]
        if (top.score - unknownWordScore < tuning.minGain) return null
        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null && top.score - runnerUp.score < tuning.minMargin) return null
        return Correction(typed, applyCase(top.word, caseShape), top.lang)
    }

    companion object {
        const val MIN_WORD_LENGTH = 3

        /** Кандидаты реже этого числа употреблений в корпусе не предлагаются (шум, опечатки корпуса). */
        private const val MIN_CANDIDATE_COUNT = 5

        /** Допустимая цена правки по длине слова (CHEAP = 1, FULL = 2). */
        fun maxCostFor(length: Int): Int = when {
            length <= 3 -> TypoCostModel.CHEAP
            length <= 5 -> TypoCostModel.FULL
            length <= 8 -> TypoCostModel.FULL + TypoCostModel.CHEAP
            else -> TypoCostModel.FULL * 2
        }

        fun isCyrillicLetter(c: Char): Boolean =
            c in 'а'..'я' || c == 'ё' || c == 'ӑ' || c == 'ӗ' || c == 'ҫ' || c == 'ӳ'

        /**
         * Русская обсценная лексика: такие слова словаря считаются известными (набранные — не исправляются),
         * но никогда не подставляются автозаменой вместо опечатки. Корни привязаны к началу слова
         * (с типичными приставками), чтобы не задеть «страхуют», «корабля», «мандарин».
         */
        private val OFFENSIVE = Regex(
            "^(?:на|по|за|от|до|вы|о|об|ни|при|рас|раз)?ху[йяеёию]|пизд|" +
                "^(?:за|вы|у|по|от|на|при|раз|об|до|съ|въ|подъ|недо)?[её]б[аиуоеыняёлщ]|" +
                "^бля|пид[оа]р|^муда[кч]|шлюх|залуп|гандон"
        )

        fun isOffensive(word: String): Boolean = OFFENSIVE.containsMatchIn(word)

        internal enum class CaseShape { LOWER, TITLE }

        /** Форма регистра набранного слова; `null` — не исправлять (ВСЕ ЗАГЛАВНЫЕ, смешанный регистр). */
        internal fun caseShapeOf(word: String): CaseShape? {
            if (word.isEmpty()) return null
            val tail = word.substring(1)
            if (tail.any { it.isUpperCase() }) return null
            return if (word[0].isUpperCase()) CaseShape.TITLE else CaseShape.LOWER
        }

        internal fun applyCase(word: String, shape: CaseShape): String = when (shape) {
            CaseShape.LOWER -> word
            CaseShape.TITLE -> word.replaceFirstChar { it.titlecaseChar() }
        }

        /** Символы, после ввода которых набранное слово проверяется автозаменой. */
        private val SEPARATORS = setOf(" ", ".", ",", "!", "?", ";", ":", ")", "»", "\"", "\n")

        fun isSeparator(text: String): Boolean = text in SEPARATORS

        /**
         * Поле допускает автозамену (как в AOSP LatinIME): только текст, не пароль, не адрес, не e-mail,
         * не фильтр; без `NO_SUGGESTIONS`; однострочное поле — только с явным `AUTO_CORRECT`.
         */
        fun isFieldAllowed(inputType: Int): Boolean {
            if (inputType and EditorInfo.TYPE_MASK_CLASS != EditorInfo.TYPE_CLASS_TEXT) return false
            val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
            if (variation in BLOCKED_VARIATIONS) return false
            val flagNoSuggestions = inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
            val flagAutoCorrect = inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT != 0
            val flagMultiLine = inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE != 0
            if (flagNoSuggestions) return false
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT && !flagAutoCorrect) return false
            return flagAutoCorrect || flagMultiLine
        }

        /** Можно ли запоминать слова в личный словарь (не в режиме инкогнито приложения). */
        fun isLearningAllowed(imeOptions: Int): Boolean =
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0

        private val BLOCKED_VARIATIONS = setOf(
            EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            EditorInfo.TYPE_TEXT_VARIATION_URI,
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            EditorInfo.TYPE_TEXT_VARIATION_FILTER
        )

        /**
         * Слово перед курсором, которое можно исправлять, или `null`. Токен до пробела не должен содержать
         * цифр, латиницы и «технических» символов (`@ # / \ _ = & % +`): адреса, хэштеги, команды не трогаем.
         */
        fun extractCorrectableWord(beforeCursor: String): String? {
            if (beforeCursor.isEmpty()) return null
            var idx = beforeCursor.length
            while (idx > 0 && beforeCursor[idx - 1].isLetter()) idx--
            val word = beforeCursor.substring(idx)
            if (word.isEmpty()) return null
            var tokenStart = idx
            while (tokenStart > 0 && !beforeCursor[tokenStart - 1].isWhitespace()) tokenStart--
            val prefix = beforeCursor.substring(tokenStart, idx)
            if (prefix.any { it.isLetterOrDigit() && !isCyrillicLetter(it.lowercaseChar()) || it in TECH_CHARS }) {
                return null
            }
            return word
        }

        private val TECH_CHARS = setOf('@', '#', '/', '\\', '_', '=', '&', '%', '+', '<', '>', '*', '~', '|', '$')
    }
}
