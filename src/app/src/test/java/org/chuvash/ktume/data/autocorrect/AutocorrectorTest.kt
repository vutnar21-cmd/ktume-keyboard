package org.chuvash.ktume.data.autocorrect

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Движок автозамены (задача 013): дерево, цены правок, решение об исправлении, сессия отката. */
class AutocorrectorTest {

    private val ruRows = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"),
        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
    )
    private val costs = TypoCostModel.fromRows(ruRows)

    private val ru = WordTrie.build(
        mapOf(
            "привет" to 5000, "приветик" to 40, "сегодня" to 8000, "что" to 90000, "кто" to 30000,
            "был" to 20000, "бил" to 300, "класс" to 900, "спасибо" to 7000, "мандарин" to 50,
            "пизда" to 9000, "пицца" to 10
        )
    )
    private val cv = WordTrie.build(
        mapOf("ҫак" to 50000, "вӑл" to 80000, "вӗсем" to 30000, "пирӗн" to 20000, "сак" to 100)
    )
    // Маленькие словари нерепрезентативны для порога «редкое верное слово»: проверяем его отдельно
    private val corrector = Autocorrector(
        listOf(Autocorrector.LanguageDictionary("cv", cv), Autocorrector.LanguageDictionary("ru", ru)),
        Autocorrector.Tuning(minGain = 0.0)
    )

    private fun fix(word: String, lang: String = "ru", personal: Set<String> = emptySet()) =
        corrector.correct(word, lang, personal, costs)?.replacement

    // --- WordTrie ---

    @Test
    fun trieFindsWordsAndCounts() {
        assertEquals(5000, ru.countOf("привет"))
        assertEquals(40, ru.countOf("приветик"))
        assertEquals(0, ru.countOf("прив"))
        assertFalse("приветики" in ru)
        assertEquals(12, ru.size)
    }

    @Test
    fun trieFromTsvMatchesMapBuild() {
        val tsv = sequenceOf("# комментарий", "привет\t5", "при\t2", "приветик\t1", "привет\t3", "", "а")
        val trie = WordTrie.fromTsv(tsv)
        assertEquals(8, trie.countOf("привет"))
        assertEquals(2, trie.countOf("при"))
        assertEquals(1, trie.countOf("приветик"))
        assertEquals(1, trie.countOf("а"))
        assertEquals(4, trie.size)
        assertEquals(12L, trie.totalCount)
    }

    @Test
    fun searchCoversAllEditKinds() {
        val plain = TypoCostModel()
        fun cost(typed: String, word: String) = ru.search(typed, 4, plain).firstOrNull { it.word == word }?.cost
        assertEquals(2, cost("правет", "привет"))      // замена
        assertEquals(2, cost("првет", "привет"))       // пропуск
        assertEquals(1, cost("приввет", "привет"))     // сдвоенное нажатие — дёшево
        assertEquals(2, cost("пирвет", "привет"))      // перестановка соседних
        assertEquals(4, cost("прывед", "привет"))      // две правки
        assertEquals(1, cost("клас", "класс"))         // потерянная двойная буква — дёшево
        assertNull(cost("пока", "привет"))
    }

    // --- TypoCostModel ---

    @Test
    fun adjacentKeysAndSimilarLettersAreCheap() {
        assertEquals(TypoCostModel.CHEAP, costs.substitutionCost('и', 'м'))              // соседи в ряду
        assertEquals(TypoCostModel.CHEAP, costs.substitutionCost('ц', 'у'))
        assertEquals(TypoCostModel.CHEAP, costs.substitutionCost('ы', 'в'))              // соседние ряды
        assertEquals(TypoCostModel.FULL, costs.substitutionCost('й', 'ю'))
        assertEquals(TypoCostModel.CHEAP, costs.substitutionCost('ҫ', 'с'))              // надстрочный знак
        assertEquals(TypoCostModel.CHEAP, costs.substitutionCost('ё', 'е'))
        assertEquals(TypoCostModel.CHEAP, costs.insertionCost("приввет", 4))              // сдвоенное нажатие
    }

    // --- Autocorrector ---

    @Test
    fun correctsTyposWithCase() {
        assertEquals("привет", fix("превет"))
        assertEquals("Привет", fix("Превет"))
        assertEquals("сегодня", fix("сегоднч"))
        assertEquals("спасибо", fix("спасибл"))
        assertEquals("что", fix("чоо"))
    }

    @Test
    fun knownAndPersonalWordsAreNeverCorrected() {
        assertNull(fix("бил"))
        assertNull(fix("сак"))                                  // есть в чувашском словаре
        assertNull(fix("превет", personal = setOf("превет")))
        assertNull(fix("вӑл"))
    }

    @Test
    fun chuvashDiacriticsAreRestored() {
        assertEquals("вӗсем", fix("весем", lang = "cv"))
        assertEquals("пирӗн", fix("пирен", lang = "cv"))
    }

    @Test
    fun skipsShortUppercaseMixedAndForeignWords() {
        assertNull(fix("чо"))          // меньше 3 букв
        assertNull(fix("ПРЕВЕТ"))      // аббревиатура / капслок
        assertNull(fix("пРевет"))      // смешанный регистр
        assertNull(fix("privet"))      // латиница
        assertNull(fix("превет2"))
    }

    @Test
    fun neverSubstitutesOffensiveWords() {
        assertNull(fix("пизза"))       // ближайшее частое — обсценное: не подставляем
        assertTrue(Autocorrector.isOffensive("пизда"))
        assertFalse(Autocorrector.isOffensive("мандарин"))
        assertFalse(Autocorrector.isOffensive("страхуют"))
        assertFalse(Autocorrector.isOffensive("корабля"))
        assertFalse(Autocorrector.isOffensive("небо"))
    }

    @Test
    fun rareCandidatesDoNotBeatUnknownWordHypothesis() {
        // «приветик» встречается всего в 4 раза чаще порога словаря: «преветик» может быть верным редким словом
        val strict = Autocorrector(listOf(Autocorrector.LanguageDictionary("ru", ru)), Autocorrector.Tuning(minGain = 2.0))
        assertNull(strict.correct("преветик", "ru", emptySet(), costs))
        assertEquals("привет", strict.correct("превет", "ru", emptySet(), costs)?.replacement)
    }

    @Test
    fun maxCostGrowsWithLength() {
        assertEquals(1, Autocorrector.maxCostFor(3))
        assertEquals(2, Autocorrector.maxCostFor(5))
        assertEquals(3, Autocorrector.maxCostFor(8))
        assertEquals(4, Autocorrector.maxCostFor(12))
    }

    // --- Контекст поля и слова ---

    @Test
    fun extractsOnlyPlainWords() {
        assertEquals("превет", Autocorrector.extractCorrectableWord("Ну превет"))
        assertEquals("превет", Autocorrector.extractCorrectableWord("(превет"))
        assertEquals("нибуть", Autocorrector.extractCorrectableWord("кто-нибуть"))
        assertNull(Autocorrector.extractCorrectableWord("#превет"))
        assertNull(Autocorrector.extractCorrectableWord("site.ru/превет"))
        assertNull(Autocorrector.extractCorrectableWord("user@превет"))
        assertNull(Autocorrector.extractCorrectableWord("2превет"))
        assertNull(Autocorrector.extractCorrectableWord("превет "))
        assertNull(Autocorrector.extractCorrectableWord(""))
    }

    @Test
    fun fieldPolicyFollowsAosp() {
        val text = EditorInfo.TYPE_CLASS_TEXT
        assertTrue(Autocorrector.isFieldAllowed(text or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE))
        assertTrue(Autocorrector.isFieldAllowed(text or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT))
        assertFalse(Autocorrector.isFieldAllowed(text))
        assertFalse(
            Autocorrector.isFieldAllowed(
                text or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            )
        )
        assertFalse(
            Autocorrector.isFieldAllowed(
                text or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            )
        )
        assertFalse(
            Autocorrector.isFieldAllowed(
                text or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT or EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
        assertFalse(
            Autocorrector.isFieldAllowed(text or EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE)
        )
        assertFalse(Autocorrector.isFieldAllowed(EditorInfo.TYPE_CLASS_NUMBER))
        assertFalse(Autocorrector.isFieldAllowed(EditorInfo.TYPE_NULL))
        assertTrue(Autocorrector.isLearningAllowed(EditorInfo.IME_ACTION_SEND))
        assertFalse(Autocorrector.isLearningAllowed(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
    }

    // --- AutocorrectSession ---

    @Test
    fun sessionRevertsOnceAndRemembersRejectedWord() {
        val session = AutocorrectSession()
        val c = session.correctionBeforeSeparator("Ну превет", " ", corrector, "ru", emptySet(), costs)
        assertNotNull(c)
        session.onCorrectionApplied(c!!, " ")
        assertTrue(session.hasPendingRevert)

        val revert = session.takeRevert("Ну привет ")
        assertEquals("превет", revert?.original)
        assertEquals("привет ", revert?.committedTail)
        assertFalse(session.hasPendingRevert)
        // То же слово в этом поле больше не исправляется
        assertNull(session.correctionBeforeSeparator("превет ну превет", " ", corrector, "ru", emptySet(), costs))
        // В новом поле — снова исправляется
        session.reset()
        assertNotNull(session.correctionBeforeSeparator("превет", " ", corrector, "ru", emptySet(), costs))
    }

    @Test
    fun sessionDoesNotRevertAfterOtherInput() {
        val session = AutocorrectSession()
        val c = session.correctionBeforeSeparator("превет", ".", corrector, "ru", emptySet(), costs)!!
        session.onCorrectionApplied(c, ".")
        assertNull(session.takeRevert("привет. А"))           // текст изменился
        session.onCorrectionApplied(c, ".")
        session.cancelPending()
        assertNull(session.takeRevert("привет."))
        // Не разделитель — исправления нет
        assertNull(session.correctionBeforeSeparator("превет", "а", corrector, "ru", emptySet(), costs))
        assertNull(session.correctionBeforeSeparator("превет", " ", null, "ru", emptySet(), costs))
    }
}
