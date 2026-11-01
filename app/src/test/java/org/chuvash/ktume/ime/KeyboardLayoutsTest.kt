package org.chuvash.ktume.ime

import android.view.inputmethod.EditorInfo
import org.chuvash.ktume.data.*
import org.chuvash.ktume.ui.theme.KesjePalettes

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
        assertTrue(prefs.swipeUpFirstVariant)
        assertTrue(prefs.showKeyPressPopup)
        assertTrue(prefs.compactLandscape)
        assertEquals("obsidian_forest", KesjePalettes.byId("non_existent_palette").id)
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
        // TYPE_NUMBER_FLAG_DECIMAL (0x2000) совпадает по биту с TYPE_TEXT_FLAG_CAP_WORDS — должен давать 0
        assertEquals(
            0,
            KeyboardLayouts.resolveEditorCapFlags(
                EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
            )
        )

        // Реальный кейс Compose-полей (Google Contacts First name: 0x202060 без бита TYPE_CLASS_TEXT)
        val composePersonName = EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME or
            EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS or
            EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE
        assertEquals(
            EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS,
            KeyboardLayouts.resolveEditorCapFlags(composePersonName)
        )

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

    @Test
    fun swipeUpFirstVariant_resolvesPrimaryVariantAndDirectionalCone() {
        // 1-й вариант всегда совпадает с variants[0] (самым частым символом и микро-меткой),
        // независимо от того, на левой или на правой половине находится клавиша
        assertEquals("г", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "к", false)))
        assertEquals("Г", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "к", true)))
        assertEquals("д", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "т", false)))
        assertEquals("ж", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "ш", false)))
        assertEquals("-", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, ".", false)))
        assertEquals("-", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, ",", false)))
        assertEquals("–", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "-", false)))
        assertEquals("¹", KeyboardLayouts.primaryLongPressVariant(KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, "1", false)))
        assertEquals(null, KeyboardLayouts.primaryLongPressVariant(emptyList()))

        val threshold = 80f
        // Обычное дрожание пальца при тапе или свайп вниз/вбок не активируют свайп вверх
        assertFalse(KeyboardLayouts.isSwipeUpGesture(dx = 0f, dy = -18f, thresholdPx = threshold))
        assertFalse(KeyboardLayouts.isSwipeUpGesture(dx = 0f, dy = 90f, thresholdPx = threshold))
        assertFalse("Свайп влево (например . -> -) не должен перехватываться свайпом вверх",
            KeyboardLayouts.isSwipeUpGesture(dx = -95f, dy = -30f, thresholdPx = threshold))

        // Прямой и естественный дуговой свайп большим пальцем вверх — активируют
        assertTrue(KeyboardLayouts.isSwipeUpGesture(dx = 0f, dy = -82f, thresholdPx = threshold))
        assertTrue(KeyboardLayouts.isSwipeUpGesture(dx = 45f, dy = -90f, thresholdPx = threshold))
        assertTrue(KeyboardLayouts.isSwipeUpGesture(dx = -45f, dy = -90f, thresholdPx = threshold))

        // Гистерезис: небольшое смещение у границы удерживает превью, возврат вниз к клавише — отменяет
        assertTrue(KeyboardLayouts.shouldKeepSwipeUpActive(dx = 20f, dy = -60f, thresholdPx = threshold))
        assertFalse(KeyboardLayouts.shouldKeepSwipeUpActive(dx = 0f, dy = -35f, thresholdPx = threshold))
    }

    @Test
    fun morphemeContextAndSpaceTrimming_preserveNewlinesAndKeepHarmonyAfterSpace() {
        // Во время набора слова и сразу после пробела на той же строке сингармонизм сохраняет основу «хула»
        assertEquals("хула", KeyboardLayouts.extractMorphemeWordBeforeCursor("хула"))
        assertEquals("хула", KeyboardLayouts.extractMorphemeWordBeforeCursor("хула "))
        assertEquals("Шупашкар", KeyboardLayouts.extractMorphemeWordBeforeCursor("Салам, Шупашкар  "))

        // После точки с пробелом или на новой строке основа сбрасывается
        assertEquals("", KeyboardLayouts.extractMorphemeWordBeforeCursor("хула. "))
        assertEquals("", KeyboardLayouts.extractMorphemeWordBeforeCursor("хула\n"))
        assertEquals("", KeyboardLayouts.extractMorphemeWordBeforeCursor("хула\n  "))

        // Подрезка пробела перед приклеиванием аффикса не трогает \n, отступ пустой строки и пробел после пунктуации/цифр
        assertEquals(0, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("хула"))
        assertEquals(1, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("хула "))
        assertEquals(2, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("Строка 1\nхула  "))
        assertEquals(0, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("Строка 1\n"))
        assertEquals(0, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("Строка 1\n   "))
        assertEquals(0, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("Салам. "))
        assertEquals(0, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("Салам, "))
        assertEquals(0, KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord("123 "))
    }

    @Test
    fun computeDeleteWordLength_stopsAtNewlineBoundaryAndProtectsOrphanLowSurrogate() {
        assertEquals(0, KeyboardLayouts.computeDeleteWordLength(""))
        assertEquals(8, KeyboardLayouts.computeDeleteWordLength("Салам Шупашкар"))
        assertEquals(9, KeyboardLayouts.computeDeleteWordLength("Салам Шупашкар "))
        // Не пробивает \n при удалении первого слова новой строки
        assertEquals(8, KeyboardLayouts.computeDeleteWordLength("Строка 1\nШупашкар"))
        assertEquals(3, KeyboardLayouts.computeDeleteWordLength("Строка 1\n   "))
        // На начале пустой строки удаляет ровно один перевод строки
        assertEquals(1, KeyboardLayouts.computeDeleteWordLength("Строка 1\n"))
        assertEquals(2, KeyboardLayouts.computeDeleteWordLength("Строка 1\r\n"))
        // Если окно getTextBeforeCursor(64, 0) разрезало суррогатную пару пополам (первый char — low surrogate),
        // не захватываем этот хвост, чтобы не оставить битый high surrogate в редакторе
        val splitWindow = "\uDE0AШупашкар"
        assertEquals("Шупашкар".length, KeyboardLayouts.computeDeleteWordLength(splitWindow))
    }

    @Test
    fun nextShiftStateOnTap_cyclesOffOnceCapsLockWithDoubleTapWindow() {
        // 1. Из OFF любой тап включает ONCE
        assertEquals(
            ShiftState.ONCE,
            nextShiftStateOnTap(current = ShiftState.OFF, nowMs = 1_000L, lastTapMs = 0L)
        )
        // 2. Быстрый повторный тап (< 420 мс) переводит ONCE -> CAPS_LOCK
        assertEquals(
            ShiftState.CAPS_LOCK,
            nextShiftStateOnTap(
                current = ShiftState.ONCE,
                nowMs = 1_000L + DOUBLE_TAP_WINDOW_MS - 1L,
                lastTapMs = 1_000L
            )
        )
        // 3. Медленный повторный тап (>= 420 мс) выключает ONCE -> OFF
        assertEquals(
            ShiftState.OFF,
            nextShiftStateOnTap(
                current = ShiftState.ONCE,
                nowMs = 1_000L + DOUBLE_TAP_WINDOW_MS,
                lastTapMs = 1_000L
            )
        )
        // 4. Из CAPS_LOCK любой тап (даже мгновенный) выключает в OFF
        assertEquals(
            ShiftState.OFF,
            nextShiftStateOnTap(current = ShiftState.CAPS_LOCK, nowMs = 1_200L, lastTapMs = 1_100L)
        )
    }

    @Test
    fun editorActionAndNumericPad_resolveContextualEnterAndNumpad() {
        assertEquals(EnterKeyAction.SEARCH, KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_SEARCH))
        assertEquals(EnterKeyAction.SEND, KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_SEND))
        assertEquals(EnterKeyAction.GO, KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_GO))
        assertEquals(EnterKeyAction.NEXT, KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_NEXT))
        assertEquals(EnterKeyAction.DONE, KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_DONE))
        assertEquals(EnterKeyAction.NEWLINE, KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_NONE))
        assertEquals(
            "NO_ENTER_ACTION forces newline in multiline fields",
            EnterKeyAction.NEWLINE,
            KeyboardLayouts.resolveEnterKeyAction(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        )

        assertTrue(KeyboardLayouts.prefersNumericPad(EditorInfo.TYPE_CLASS_NUMBER))
        assertTrue(KeyboardLayouts.prefersNumericPad(EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL))
        assertTrue(KeyboardLayouts.prefersNumericPad(EditorInfo.TYPE_CLASS_PHONE))
        assertFalse(KeyboardLayouts.prefersNumericPad(EditorInfo.TYPE_CLASS_TEXT))
        assertFalse(KeyboardLayouts.prefersNumericPad(EditorInfo.TYPE_NULL))
    }

    @Test
    fun resolveEffectiveGeometry_compactsLandscapeWithoutMutatingPortraitDefaults() {
        val prefs = KeyboardPreferences()
        val portrait = KeyboardLayouts.resolveEffectiveGeometry(prefs, isLandscape = false)
        assertEquals(48f, portrait.keyHeightDp, 0.001f)
        assertEquals(7f, portrait.keyGapVDp, 0.001f)
        assertEquals(26f, portrait.fontSizeSp, 0.001f)

        val landscape = KeyboardLayouts.resolveEffectiveGeometry(prefs, isLandscape = true)
        assertTrue("Landscape key height must be more compact", landscape.keyHeightDp < portrait.keyHeightDp)
        assertTrue("Landscape vertical gap must be smaller", landscape.keyGapVDp < portrait.keyGapVDp)
        assertTrue("Landscape font must fit compact keys", landscape.fontSizeSp < portrait.fontSizeSp)

        val disabledCompact = KeyboardLayouts.resolveEffectiveGeometry(
            prefs.copy(compactLandscape = false),
            isLandscape = true
        )
        assertEquals(48f, disabledCompact.keyHeightDp, 0.001f)
    }

    @Test
    fun emojiCatalogAndRecents_provideSixEightColumnCategoriesAndMruOrdering() {
        val categories = KeyboardLayouts.resolveEmojiCategories()
        assertEquals(6, categories.size)
        assertEquals(
            listOf("recent", "smileys", "gestures", "hearts", "nature", "food_life"),
            categories.map { it.id }
        )
        categories.forEach { cat ->
            assertTrue("Category ${cat.id} must not be empty", cat.emojis.isNotEmpty())
            assertEquals(
                "Category ${cat.id} count (${cat.emojis.size}) must be a multiple of 8 for clean 8-col grid",
                0,
                cat.emojis.size % 8
            )
            assertEquals(
                "Category ${cat.id} must not contain duplicate emojis",
                cat.emojis.size,
                cat.emojis.distinct().size
            )
        }

        // Новая запись в «Недавние» поднимает смайлик на 1-е место, не создаёт дублей и держит 24 слота
        val recents1 = KeyboardLayouts.recordRecentEmoji(emptyList(), "🥟")
        assertEquals(24, recents1.size)
        assertEquals("🥟", recents1.first())

        val recents2 = KeyboardLayouts.recordRecentEmoji(recents1, "🦊")
        assertEquals(24, recents2.size)
        assertEquals("🦊", recents2[0])
        assertEquals("🥟", recents2[1])

        // Повторный выбор уже имеющегося смайлика переносит его на 0-й индекс без дублирования
        val recents3 = KeyboardLayouts.recordRecentEmoji(recents2, "🥟")
        assertEquals(24, recents3.size)
        assertEquals("🥟", recents3[0])
        assertEquals("🦊", recents3[1])
        assertEquals(24, recents3.distinct().size)
    }

    @Test
    fun computeKeyboardBodyHeightDp_matchesRectangularAndHexRowsWithZeroJump() {
        val prefs = KeyboardPreferences()
        // С цифровым рядом: (1 цифр + 3 букв + 1 нижний) * (48 + 7) = 5 * 55 = 275 dp
        assertEquals(275f, KeyboardLayouts.computeKeyboardBodyHeightDp(prefs, alphaRowCount = 3), 0.001f)

        // Без цифрового ряда: (0 + 3 + 1) * (48 + 7) = 4 * 55 = 220 dp
        val noNumRow = prefs.copy(showNumberRow = false)
        assertEquals(220f, KeyboardLayouts.computeKeyboardBodyHeightDp(noNumRow, alphaRowCount = 3), 0.001f)

        // В режиме сот Hex: 48 * 3.4 + 3 (gap) + 48 (нижний ряд) = 214.2 dp
        val hexPrefs = prefs.copy(hexagonalGrid = true)
        assertEquals(214.2f, KeyboardLayouts.computeKeyboardBodyHeightDp(hexPrefs, alphaRowCount = 4), 0.001f)
    }
}
