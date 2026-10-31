package org.chuvash.keske.ime

import android.view.inputmethod.EditorInfo
import org.chuvash.keske.data.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {

    @Test
    fun getHex4RowLayout_preservesAllKeysForEveryLanguageAndPreset() {
        val scenarios = listOf(
            KeyboardLang.CHUVASH to ChuvashLayoutPreset.BALANCED_26,
            KeyboardLang.CHUVASH to ChuvashLayoutPreset.HEX_4ROW,
            KeyboardLang.CHUVASH to ChuvashLayoutPreset.FULL_28,
            KeyboardLang.RUSSIAN to ChuvashLayoutPreset.BALANCED_26,
            KeyboardLang.LATINA to ChuvashLayoutPreset.BALANCED_26,
            KeyboardLang.GREEK to ChuvashLayoutPreset.BALANCED_26
        )

        for ((lang, preset) in scenarios) {
            val baseRows = KeyboardLayouts.getDefaultRows(lang, preset)
            val hexRows = KeyboardLayouts.getHex4RowLayout(baseRows)

            val expectedKeys = baseRows.flatten()
            val actualKeys = hexRows.flatten()

            assertEquals(
                "Hex layout must have 4 rows for $lang ($preset)",
                4,
                hexRows.size
            )
            assertEquals(
                "Hex layout must preserve exact key sequence for $lang ($preset)",
                expectedKeys,
                actualKeys
            )
            assertTrue(
                "No row in hex layout should be empty for $lang ($preset)",
                hexRows.all { it.isNotEmpty() }
            )
        }
    }

    @Test
    fun longPressSelection_rightHalfReversesOrderRightToLeftKeepingMostFrequentFirst() {
        val periodVariants = KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, ".", false)
        assertTrue("Period should have >7 variants", periodVariants.size > 7)

        // Правая половина клавиатуры (colFraction >= 0.5f): включая центрально-правые клавиши
        // (0.5f, 0.56f на «н», 0.78f на «ш», 0.83f на «т», 0.85f на «.»).
        for (rightCol in listOf(0.5f, 0.56f, 0.778f, 0.833f, 0.85f, 1.0f)) {
            assertTrue("colFraction=$rightCol must be right-side", KeyboardLayouts.isRightSideKey(rightCol))
        }
        assertFalse("Left half (< 0.5f) is not right-side", KeyboardLayouts.isRightSideKey(0.44f))

        val displayedRight = KeyboardLayouts.getDisplayedLongPressVariants(periodVariants, colFraction = 0.85f)
        assertEquals(7, displayedRight.size)
        assertEquals(
            "Right-side popup must place top-priority variants from right to left",
            periodVariants.take(7).reversed(),
            displayedRight
        )

        val initIdx = KeyboardLayouts.computeLongPressSelectedIndex(
            variants = periodVariants,
            colFraction = 0.85f,
            dragSteps = 0
        )
        assertEquals(displayedRight.lastIndex, initIdx)
        assertEquals(
            "Press without movement on right-half key must insert the #1 most frequent variant",
            periodVariants.first(),
            displayedRight[initIdx]
        )
        assertEquals(
            "Sub-label contract on right-half key: matches #1 variant",
            periodVariants.first(),
            displayedRight[KeyboardLayouts.initialLongPressVariantIndex(periodVariants, 0.85f)]
        )

        // Проверка на чувашской «т» (colFraction = 5/6 = 0.833f) и «ш» (7/9 = 0.778f):
        // первой справа должна быть «д» (а не «ҭ») и «ж» (а не «ш́»), дальше влево — «ђ» / «щ».
        val tVariants = KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "т", false)
        val tDisplayed = KeyboardLayouts.getDisplayedLongPressVariants(tVariants, colFraction = 5f / 6f)
        assertEquals("д", tDisplayed[KeyboardLayouts.computeLongPressSelectedIndex(tVariants, 5f / 6f, 0)])
        assertEquals("ђ", tDisplayed[KeyboardLayouts.computeLongPressSelectedIndex(tVariants, 5f / 6f, -1)])

        val shVariants = KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "ш", false)
        val shDisplayed = KeyboardLayouts.getDisplayedLongPressVariants(shVariants, colFraction = 7f / 9f)
        assertEquals("ж", shDisplayed[KeyboardLayouts.computeLongPressSelectedIndex(shVariants, 7f / 9f, 0)])
        assertEquals("щ", shDisplayed[KeyboardLayouts.computeLongPressSelectedIndex(shVariants, 7f / 9f, -1)])

        // Левая половина (colFraction = 0.1f): слева направо, старт с 0
        val displayedLeft = KeyboardLayouts.getDisplayedLongPressVariants(periodVariants, colFraction = 0.1f)
        assertEquals(periodVariants.take(7), displayedLeft)
        assertEquals(0, KeyboardLayouts.initialLongPressVariantIndex(periodVariants, 0.1f))
        assertEquals(2, KeyboardLayouts.computeLongPressSelectedIndex(periodVariants, 0.1f, 2))

        // Зажим в границы списка при драге в обе стороны
        assertEquals(
            displayedRight.lastIndex - 2,
            KeyboardLayouts.computeLongPressSelectedIndex(periodVariants, 0.85f, -2)
        )
        assertEquals(
            displayedRight.lastIndex,
            KeyboardLayouts.computeLongPressSelectedIndex(periodVariants, 0.85f, 99)
        )
        assertEquals(
            0,
            KeyboardLayouts.computeLongPressSelectedIndex(periodVariants, 0.1f, -5)
        )
    }

    @Test
    fun computeLastGraphemeLength_handlesChuvashSurrogatePairsAndZwjEmoji() {
        assertEquals(0, KeyboardLayouts.computeLastGraphemeLength(""))
        assertEquals(1, KeyboardLayouts.computeLastGraphemeLength("Чӑвашла"))

        // Суррогатная пара (2 UTF-16 code units): 😊
        val withEmoji = "Салам 😊"
        assertEquals(2, KeyboardLayouts.computeLastGraphemeLength(withEmoji))
        assertEquals("Салам ", KeyboardLayouts.dropLastGrapheme(withEmoji))

        // Составной ZWJ-эмодзи из категории жестов: ❤️‍🔥
        val withZwj = "Чӗре ❤️‍🔥"
        val zwjLen = KeyboardLayouts.computeLastGraphemeLength(withZwj)
        assertEquals("❤️‍🔥".length, zwjLen)
        assertEquals("Чӗре ", KeyboardLayouts.dropLastGrapheme(withZwj))
    }

    @Test
    fun extractCurrentWordBeforeCursor_findsTrailingLettersFromEditorContext() {
        assertEquals("Шупашкар", KeyboardLayouts.extractCurrentWordBeforeCursor("Салам, Шупашкар"))
        assertEquals("пӗр", KeyboardLayouts.extractCurrentWordBeforeCursor("пӗр"))
        assertEquals("", KeyboardLayouts.extractCurrentWordBeforeCursor("Салам "))
        assertEquals("", KeyboardLayouts.extractCurrentWordBeforeCursor("Салам!"))
        assertEquals("Чӑваш", KeyboardLayouts.extractCurrentWordBeforeCursor("Строка 1\nЧӑваш"))
    }

    @Test
    fun canReplaceSpaceWithCommaBeforeCursor_requiresWordPlusSingleSpaceAndNoSelection() {
        assertTrue(KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor("Салам ", hasSelection = false))
        assertTrue(KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor("Чӑваш1 ", hasSelection = false))
        assertEquals(false, KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor("Салам ", hasSelection = true))
        assertEquals(false, KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor(" ", hasSelection = false))
        assertEquals(false, KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor("Салам  ", hasSelection = false))
        assertEquals(false, KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor("Салам, ", hasSelection = false))
        assertEquals(false, KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor("Салам", hasSelection = false))
    }

    @Test
    fun symbolLayers_keepGboardRowShapeAndNoDuplicates() {
        val pages = listOf(KeyboardLayouts.symbolsLayer1, KeyboardLayouts.symbolsLayer2)
        pages.forEach { page ->
            assertEquals("Symbol page must keep 3 rows", 3, page.size)
            assertEquals("Row 1 must hold 10 keys", 10, page[0].size)
            assertEquals("Row 2 must hold 10 keys", 10, page[1].size)
            assertEquals("Row 3 must hold 7 keys (+ =< and ⌫ are drawn by the UI)", 7, page[2].size)
            val all = page.flatten()
            assertEquals("Symbol page must not contain duplicates", all.size, all.toSet().size)
        }

        val flat1 = KeyboardLayouts.symbolsLayer1.flatten()
        listOf(
            "%", "^", "~", "`", "|", "\\", "{", "}", "[", "]",
            "@", "#", "$", "_", "&", "-", "+", "(", ")", "/",
            "*", "\"", "'", ":", ";", "!", "?"
        ).forEach { symbol ->
            assertTrue("Missing '$symbol' on symbolsLayer1", symbol in flat1)
        }
    }

    @Test
    fun numberRow_isSharedAcrossPagesAndAbsentFromSymbolPages() {
        assertEquals(10, KeyboardLayouts.numberRow.size)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            KeyboardLayouts.numberRow
        )

        // Цифры живут только в общем верхнем ряду: на страницах символов их быть не должно,
        // иначе ряд дублировался бы и высота страницы расходилась с буквенной.
        val digits = KeyboardLayouts.numberRow.toSet()
        listOf(KeyboardLayouts.symbolsLayer1, KeyboardLayouts.symbolsLayer2).forEach { page ->
            page.flatten().forEach { symbol ->
                assertTrue("Digit '$symbol' must not appear on symbol pages", symbol !in digits)
            }
        }
    }

    @Test
    fun numpad_isPhoneGridWithThreeDigitColumns() {
        assertEquals(4, KeyboardLayouts.numpadRows.size)
        KeyboardLayouts.numpadRows.forEach { row ->
            assertEquals("Numpad data row must hold 3 digit columns (4th is the action key)", 3, row.size)
        }
        assertEquals(listOf("1", "2", "3"), KeyboardLayouts.numpadRows[0])
        assertEquals(listOf(",", "0", "."), KeyboardLayouts.numpadRows[3])
    }

    @Test
    fun defaultPreferences_matchOptimalPresetAndBottomInset20() {
        val prefs = KeyboardPreferences()
        assertEquals("obsidian_forest", prefs.paletteId)
        assertEquals(48f, prefs.keyHeightDp, 0.001f)
        assertEquals(14f, prefs.keyCornerRadiusDp, 0.001f)
        assertEquals(3f, prefs.keyGapHDp, 0.001f)
        assertEquals(7f, prefs.keyGapVDp, 0.001f)
        assertEquals(26f, prefs.fontSizeSp, 0.001f)
        assertEquals(20f, prefs.bottomInsetDp, 0.001f)
        assertEquals(210, prefs.longPressDelayMs)
        assertTrue(prefs.showNumberRow)
        assertTrue(prefs.showParticlesStrip)
    }

    @Test
    fun isCursorOnEmptyLine_trueOnlyForReallyEmptyLine() {
        assertTrue("Пустое поле", KeyboardLayouts.isCursorOnEmptyLine("", ""))
        assertTrue("Новая строка после Enter", KeyboardLayouts.isCursorOnEmptyLine("Текст\n", ""))
        assertTrue("Пустая строка в середине текста", KeyboardLayouts.isCursorOnEmptyLine("abc\n", "\ndef"))
        assertTrue("Пустая первая строка", KeyboardLayouts.isCursorOnEmptyLine("", "\nabc"))

        assertFalse("Текст перед курсором в той же строке", KeyboardLayouts.isCursorOnEmptyLine("Привет", ""))
        assertFalse(
            "Курсор в начале строки, где уже есть текст",
            KeyboardLayouts.isCursorOnEmptyLine("Первая строка\n", "вторая строка")
        )
        assertFalse(
            "Курсор в середине непустой строки",
            KeyboardLayouts.isCursorOnEmptyLine("abc\ndef", "gh")
        )
        assertFalse(
            "Курсор в конце строки с текстом",
            KeyboardLayouts.isCursorOnEmptyLine("abc\ndef", "")
        )
    }

    @Test
    fun resolveEditorCapFlags_respectsGboardFieldRules() {
        // Поля без флагов CAP_* (поиск, обычные веб-формы, терминал) — не включать большую букву по умолчанию
        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(EditorInfo.TYPE_NULL))
        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(EditorInfo.TYPE_CLASS_TEXT))
        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(EditorInfo.TYPE_CLASS_NUMBER))

        // URL, email и пароли всегда запрещают автозаглавную, даже если ошибочно передан CAP_SENTENCES
        val badUri = EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_VARIATION_URI or
            EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        val badEmail = EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or
            EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        val badWebEmail = EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS or
            EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
        val badPassword = EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS

        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(badUri))
        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(badEmail))
        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(badWebEmail))
        assertEquals(0, KeyboardLayouts.resolveEditorCapFlags(badPassword))

        // Обычный чат / заметки с CAP_SENTENCES — разрешено
        val chatType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertEquals(
            EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
            KeyboardLayouts.resolveEditorCapFlags(chatType)
        )
    }

    @Test
    fun shouldAutoCapitalize_preventsCapsInEmailsUrlsCommandsAndMidLine() {
        val capSentences = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES

        // 1. Поле без запроса автозаглавной (браузер, поиск, терминал, логин) — даже пустое со строчной
        assertFalse(KeyboardLayouts.shouldAutoCapitalize("", "", editorCapFlags = 0))
        assertFalse(KeyboardLayouts.shouldAutoCapitalize("Салам. ", "", editorCapFlags = 0))

        // 2. В поле чата (CAP_SENTENCES): пустое поле и новая пустая строка — с заглавной
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("", "", capSentences))
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("Первая строка\n", "", capSentences))

        // 3. Сразу после точки без пробела (email, сайты, команды, версии) — строчная!
        assertFalse("Email user.", KeyboardLayouts.shouldAutoCapitalize("ivan.", "", capSentences))
        assertFalse("Email domain", KeyboardLayouts.shouldAutoCapitalize("ivan.petrov@mail.", "", capSentences))
        assertFalse("URL site.", KeyboardLayouts.shouldAutoCapitalize("https://chuvash.", "", capSentences))
        assertFalse("Command ./", KeyboardLayouts.shouldAutoCapitalize(".", "", capSentences))
        assertFalse("Command ./gradlew", KeyboardLayouts.shouldAutoCapitalize("./", "", capSentences))
        assertFalse("Bash source command '. '", KeyboardLayouts.shouldAutoCapitalize(". ", "", capSentences))
        assertFalse("Command 'cd .. '", KeyboardLayouts.shouldAutoCapitalize("cd .. ", "", capSentences))

        // 4. Если после точки (или после курсора на этой строке) уже есть символ — строчная!
        assertFalse(
            "Cursor right after dot before symbol",
            KeyboardLayouts.shouldAutoCapitalize("ivan.", "petrov", capSentences)
        )
        assertFalse(
            "Cursor after '. ' but before existing word on same line",
            KeyboardLayouts.shouldAutoCapitalize("Салам. ", "туссем", capSentences)
        )
        assertFalse(
            "Cursor at start of non-empty line",
            KeyboardLayouts.shouldAutoCapitalize("Строка 1\n", "строка 2", capSentences)
        )

        // 5. Настоящий конец предложения с пробелом и пустым хвостом строки — заглавная!
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("Салам. ", "", capSentences))
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("Епле пурӑнатӑн? ", "", capSentences))
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("Тавтапуҫ! ", "", capSentences))
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("Ну ладно... ", "", capSentences))
        assertTrue(KeyboardLayouts.shouldAutoCapitalize("«Салам!» ", "", capSentences))
    }
}
