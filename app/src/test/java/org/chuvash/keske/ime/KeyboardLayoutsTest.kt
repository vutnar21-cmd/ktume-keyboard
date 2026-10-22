package org.chuvash.keske.ime

import org.chuvash.keske.data.*

import org.junit.Assert.assertEquals
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
    fun longPressSelection_displayedVariantsMatchInitialAndSelectedVariant() {
        val periodVariants = KeyboardLayouts.getLongPressVariants(KeyboardLang.CHUVASH, ".", false)
        assertTrue("Period should have >7 variants", periodVariants.size > 7)

        val displayed = KeyboardLayouts.getDisplayedLongPressVariants(periodVariants)
        assertEquals(7, displayed.size)

        // Правый край экрана (colFraction = 0.85f для клавиши '.'): попап прижимается вправо,
        // но преселект остаётся первым — он совпадает с микро-меткой в углу клавиши.
        val initIdx = KeyboardLayouts.computeLongPressSelectedIndex(
            variants = periodVariants,
            colFraction = 0.85f,
            dragSteps = 0
        )
        assertEquals(
            "Initial index must always point to the first displayed variant",
            0,
            initIdx
        )
        assertEquals(
            "Selected variant must equal the first displayed variant (sub-label contract)",
            displayed.first(),
            displayed[initIdx]
        )

        // Ведение пальца вправо двигает выбор и не выходит за пределы видимого списка
        assertEquals(2, KeyboardLayouts.computeLongPressSelectedIndex(periodVariants, 0.85f, 2))
        assertEquals(
            "Drag must clamp to the last displayed variant",
            displayed.lastIndex,
            KeyboardLayouts.computeLongPressSelectedIndex(periodVariants, 0.85f, 99)
        )
        assertEquals(
            "Negative drag must clamp to the first variant",
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
        assertEquals(3f, prefs.keyGapDp, 0.001f)
        assertEquals(26f, prefs.fontSizeSp, 0.001f)
        assertEquals(20f, prefs.bottomInsetDp, 0.001f)
        assertEquals(210, prefs.longPressDelayMs)
        assertTrue(prefs.showNumberRow)
        assertTrue(prefs.showParticlesStrip)
    }
}
