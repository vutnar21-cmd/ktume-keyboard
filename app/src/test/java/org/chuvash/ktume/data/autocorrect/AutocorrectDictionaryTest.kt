package org.chuvash.ktume.data.autocorrect

import java.io.File
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Автозамена на настоящих словарях `assets/dict/cv.tsv` и `ru.tsv`: типовые опечатки исправляются,
 * частые чувашские и русские слова не трогаются, поиск укладывается в бюджет времени.
 * Если в `scripts/dictionaries/.cache/` есть выборки редких форм (их пишет скрипт сборки),
 * печатается доля ложных исправлений — для подбора порогов, без жёсткой проверки.
 */
class AutocorrectDictionaryTest {

    companion object {
        private lateinit var corrector: Autocorrector
        private lateinit var cv: WordTrie
        private lateinit var ru: WordTrie

        private fun load(lang: String): WordTrie =
            File("src/main/assets/dict/$lang.tsv").bufferedReader().useLines { WordTrie.fromTsv(it) }

        @JvmStatic
        @BeforeClass
        fun loadDictionaries() {
            cv = load("cv")
            ru = load("ru")
            corrector = Autocorrector(
                listOf(Autocorrector.LanguageDictionary("cv", cv), Autocorrector.LanguageDictionary("ru", ru))
            )
        }
    }

    private val cvCosts = TypoCostModel.fromRows(KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH))
    private val ruCosts = TypoCostModel.fromRows(KeyboardLayouts.getDefaultRows(KeyboardLang.RUSSIAN))

    private fun fixRu(word: String) = corrector.correct(word, "ru", emptySet(), ruCosts)?.replacement
    private fun fixCv(word: String) = corrector.correct(word, "cv", emptySet(), cvCosts)?.replacement

    @Test
    fun dictionariesAreLoaded() {
        assertTrue(cv.size > 100_000)
        assertTrue(ru.size > 100_000)
        assertTrue("вӑл" in cv && "ҫак" in cv && "чӑваш" in cv)
        assertTrue("привет" in ru && "сегодня" in ru)
    }

    @Test
    fun russianTyposAreCorrected() {
        assertEquals("привет", fixRu("превет"))
        assertEquals("сегодня", fixRu("сегоднч"))
        assertEquals("спасибо", fixRu("спасибл"))
        assertEquals("пожалуйста", fixRu("пожалйста"))
        assertEquals("здравствуйте", fixRu("здраствуйте"))
        assertEquals("Хорошо", fixRu("Хорошл"))
    }

    @Test
    fun chuvashTyposAreCorrected() {
        assertEquals("вӗсем", fixCv("весем"))
        assertEquals("чӑваш", fixCv("чаваш"))
        assertEquals("эпӗ", fixCv("эпе"))
    }

    @Test
    fun frequentWordsAreLeftAlone() {
        val chuvash = listOf(
            "вӑл", "пӗр", "тата", "мар", "чӑваш", "ҫул", "ҫине", "эпӗ", "ҫак", "анчах", "вӗсем", "пирӗн",
            "ҫынсем", "хула", "шкулта", "килте", "ачасем", "пулать", "пулнӑ", "тӑрӑшать", "кӗнеке"
        )
        val russian = listOf("что", "это", "как", "сегодня", "привет", "хорошо", "чебоксары", "ещё", "еще")
        for (w in chuvash) assertNull("cv: $w", fixCv(w))
        for (w in russian) assertNull("ru: $w", fixRu(w))
    }

    @Test
    fun searchFitsTimeBudget() {
        val words = listOf("превет", "сегоднч", "здраствуйте", "чаваш", "пожалйста", "неизвестнословоо", "ыфвапр")
        repeat(3) { words.forEach { fixRu(it) } } // прогрев JIT
        val started = System.nanoTime()
        repeat(10) { words.forEach { fixRu(it); fixCv(it) } }
        val perWordMs = (System.nanoTime() - started) / 1_000_000.0 / (10 * words.size * 2)
        println("Автозамена: %.2f мс на слово (JVM)".format(perWordMs))
        assertTrue("слишком медленно: $perWordMs мс", perWordMs < 30.0)
    }

    @Test
    fun reportFalseCorrectionsOnRareForms() {
        for ((lang, costs) in listOf("cv" to cvCosts, "ru" to ruCosts)) {
            val words = holdout(lang) ?: continue
            val corrected = words.mapNotNull { w -> corrector.correct(w, lang, emptySet(), costs)?.let { w to it.replacement } }
            println(
                "Редкие формы $lang: исправлено ${corrected.size} из ${words.size} " +
                    "(%.1f %%); примеры: %s".format(
                        100.0 * corrected.size / words.size.coerceAtLeast(1),
                        corrected.shuffled(java.util.Random(1)).take(25).joinToString { "${it.first}→${it.second}" }
                    )
            )
        }
    }

    private fun holdout(lang: String): List<String>? {
        val file = File("../scripts/dictionaries/.cache/${lang}_holdout.tsv")
        if (!file.isFile) return null
        return file.readLines().map { it.substringBefore('\t') }.filter { it.length >= 3 }
    }

    /** Слова словаря с рангами [from, to) — основа синтетических опечаток. */
    private fun rankedWords(lang: String, from: Int, to: Int): List<String> =
        File("src/main/assets/dict/$lang.tsv").useLines { lines ->
            lines.filter { !it.startsWith("#") }.map { it.substringBefore('\t') }.drop(from).take(to - from).toList()
        }

    /**
     * Синтетические опечатки (соседняя клавиша, пропуск, перестановка, сдвоенная буква, без надстрочного
     * знака) из частых слов: какая доля возвращается к исходному слову.
     */
    private fun syntheticTypos(lang: String, rows: List<List<String>>, n: Int): List<Pair<String, String>> {
        val rnd = java.util.Random(7)
        val neighbors = HashMap<Char, MutableList<Char>>()
        val flat = rows.mapIndexed { r, row -> row.mapIndexed { i, k -> Triple(k[0], r, i + (rows.maxOf { it.size } - row.size) / 2f) } }.flatten()
        for (a in flat) for (b in flat) {
            if (a.first != b.first && kotlin.math.abs(a.second - b.second) <= 1 && kotlin.math.abs(a.third - b.third) <= 1.01f) {
                neighbors.getOrPut(a.first) { mutableListOf() } += b.first
            }
        }
        val plain = mapOf('ӑ' to 'а', 'ӗ' to 'е', 'ҫ' to 'с', 'ӳ' to 'у')
        val words = rankedWords(lang, 300, 30_000).filter { it.length >= 4 }
        val out = ArrayList<Pair<String, String>>()
        while (out.size < n) {
            val w = words[rnd.nextInt(words.size)]
            val i = rnd.nextInt(w.length)
            val typo = when (rnd.nextInt(5)) {
                0 -> neighbors[w[i]]?.let { w.substring(0, i) + it[rnd.nextInt(it.size)] + w.substring(i + 1) }
                1 -> w.removeRange(i, i + 1)
                2 -> if (i + 1 < w.length) w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2) else null
                3 -> w.substring(0, i) + w[i] + w.substring(i)
                else -> w.indexOfFirst { it in plain }.takeIf { it >= 0 }?.let { j -> w.substring(0, j) + plain[w[j]] + w.substring(j + 1) }
            } ?: continue
            if (typo == w || corrector.isKnown(typo)) continue
            out += typo to w
        }
        return out
    }

    /** Сетка порогов: долгая (~2 мин), запускается только с `KTUME_TUNING=1` и кэшем скрипта сборки словарей. */
    @Test
    fun reportTuningGrid() {
        org.junit.Assume.assumeTrue(System.getenv("KTUME_TUNING") == "1" && holdout("cv") != null)
        val cvTypos = syntheticTypos("cv", KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH), 1000)
        val ruTypos = syntheticTypos("ru", KeyboardLayouts.getDefaultRows(KeyboardLang.RUSSIAN), 1000)
        val cvRare = holdout("cv").orEmpty().take(1500)
        val ruRare = holdout("ru").orEmpty().take(1500)
        val dicts = listOf(Autocorrector.LanguageDictionary("cv", cv), Autocorrector.LanguageDictionary("ru", ru))
        for (bonus in listOf(10.0, 30.0)) for (margin in listOf(0.7, 1.5)) for (gain in listOf(1.5, 2.0, 2.5)) {
            val weight = 2.3
            val c = Autocorrector(
                dicts,
                Autocorrector.Tuning(
                    costWeight = weight, minGain = gain, minMargin = margin, activeLangBonus = kotlin.math.ln(bonus)
                )
            )
            fun recall(typos: List<Pair<String, String>>, lang: String, costs: TypoCostModel) =
                100.0 * typos.count { (t, w) -> c.correct(t, lang, emptySet(), costs)?.replacement == w } / typos.size
            fun wrong(typos: List<Pair<String, String>>, lang: String, costs: TypoCostModel) =
                100.0 * typos.count { (t, w) -> c.correct(t, lang, emptySet(), costs)?.replacement.let { it != null && it != w } } / typos.size
            fun falseRate(rare: List<String>, lang: String, costs: TypoCostModel) =
                if (rare.isEmpty()) Double.NaN else 100.0 * rare.count { c.correct(it, lang, emptySet(), costs) != null } / rare.size
            println(
                "bonus=x%.0f margin=%.1f gain=%.1f | cv: верно %.1f%%, неверно %.1f%%, ложно на редких %.1f%% | ru: верно %.1f%%, неверно %.1f%%, ложно на редких %.1f%%".format(
                    bonus, margin, gain,
                    recall(cvTypos, "cv", cvCosts), wrong(cvTypos, "cv", cvCosts), falseRate(cvRare, "cv", cvCosts),
                    recall(ruTypos, "ru", ruCosts), wrong(ruTypos, "ru", ruCosts), falseRate(ruRare, "ru", ruCosts)
                )
            )
        }
    }
}
