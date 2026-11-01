package org.chuvash.ktume.data

import android.view.inputmethod.EditorInfo
import java.text.BreakIterator

/**
 * МОДУЛЬ: EditorTextLogic.kt — чистые функции обработки текста, Unicode-графем,
 * контекста поля ввода (EditorInfo), автозаглавной буквы и адаптивной геометрии.
 *
 * Не зависит от Android View / Compose UI и полностью покрывается быстрыми JVM unit-тестами.
 */

/** Контекстное действие клавиши Enter по [EditorInfo.imeOptions]. */
enum class EnterKeyAction {
    NEWLINE,
    SEARCH,
    SEND,
    GO,
    NEXT,
    DONE
}

/** Эффективная геометрия клавиатуры с учётом ориентации экрана (не перезаписывает настройки пользователя). */
data class EffectiveKeyboardGeometry(
    val keyHeightDp: Float,
    val keyGapHDp: Float,
    val keyGapVDp: Float,
    val fontSizeSp: Float,
    val bottomInsetDp: Float,
    val sidePaddingDp: Float
)

internal object EditorTextLogic {

    private val CLOSING_SENTENCE_CHARS = setOf('»', '"', '”', '\'', '’', ')', ']')

    /** Конвертер «ложной латиницы» (`ă, ĕ, ç, ÿ, ŭ → ӑ, ӗ, ҫ, ӳ`). */
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
     * Корректно обрабатывает суррогатные пары, ZWJ-последовательности (`❤️‍🔥`) и вариационные селекторы.
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

    /** Извлекает текущее набираемое слово непосредственно перед курсором из контекста редактора. */
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
     * Извлекает слово-основу для сингармонизма ленты частиц и аффиксов на текущей строке.
     * Работает как во время набора слова («хула»), так и сразу после пробела на той же строке («хула »),
     * но не пересекает перевод строки `\n` и не берёт слово после знака препинания («хула. »).
     */
    fun extractMorphemeWordBeforeCursor(beforeCursor: String): String {
        if (beforeCursor.isEmpty()) return ""
        val lineBefore = beforeCursor.substringAfterLast('\n')
        val trimmedLine = lineBefore.trimEnd { it == ' ' || it == '\t' }
        return extractCurrentWordBeforeCursor(trimmedLine)
    }

    /**
     * Количество хвостовых горизонтальных пробелов (`' '`, `'\t'`) после слова на текущей строке,
     * которые нужно убрать перед приклеиванием частицы или аффикса.
     * Не трогает переводы строк (`\n`, `\r`), отступы пустой строки и пробелы после знаков препинания.
     */
    fun computeTrailingHorizontalSpacesAfterWord(beforeCursor: String): Int {
        if (beforeCursor.isEmpty()) return 0
        val lineBefore = beforeCursor.substringAfterLast('\n')
        val trimmedLine = lineBefore.trimEnd { it == ' ' || it == '\t' }
        if (trimmedLine.isEmpty()) return 0
        val lastCp = Character.codePointBefore(trimmedLine, trimmedLine.length)
        if (!Character.isLetter(lastCp)) return 0
        return lineBefore.length - trimmedLine.length
    }

    /**
     * Вычисляет безопасное число UTF-16 code units для удаления одного слова свайпом влево по `⌫`.
     * Не пробивает границу перевода строки `\n` и не оставляет оборванную суррогатную пару на границе окна.
     */
    fun computeDeleteWordLength(beforeCursor: String): Int {
        if (beforeCursor.isEmpty()) return 0
        if (beforeCursor.endsWith("\r\n")) return 2
        if (beforeCursor.endsWith("\n")) return 1
        val lineBefore = beforeCursor.substringAfterLast('\n')
        val trimmedLine = lineBefore.trimEnd { it == ' ' || it == '\t' }
        val rawLen = if (trimmedLine.isEmpty()) {
            lineBefore.length
        } else {
            val lastWs = trimmedLine.indexOfLast { it == ' ' || it == '\t' }
            if (lastWs == -1) lineBefore.length else lineBefore.length - lastWs - 1
        }
        return if (rawLen == beforeCursor.length && rawLen > 1 && beforeCursor[0].isLowSurrogate()) {
            rawLen - 1
        } else {
            rawLen
        }
    }

    /**
     * Проверяет, можно ли безопасно заменить предыдущий пробел на запятую с пробелом (`", "`).
     * Разрешено только без выделения, когда перед курсором одиночный пробел после буквы или цифры.
     */
    fun canReplaceSpaceWithCommaBeforeCursor(beforeCursor: String, hasSelection: Boolean): Boolean {
        if (hasSelection || beforeCursor.length < 2 || !beforeCursor.endsWith(" ")) return false
        val cpBeforeSpace = Character.codePointBefore(beforeCursor, beforeCursor.length - 1)
        return Character.isLetterOrDigit(cpBeforeSpace)
    }

    /** Курсор стоит на пустой строке: ни слева, ни справа до ближайшего `\n` текста нет. */
    fun isCursorOnEmptyLine(beforeCursor: String, afterCursor: String): Boolean {
        val lineBefore = beforeCursor.substringAfterLast('\n')
        val lineAfter = afterCursor.substringBefore('\n')
        return lineBefore.isEmpty() && lineAfter.isEmpty()
    }

    /**
     * Извлекает разрешённые флаги автозаглавной буквы из [EditorInfo.inputType] по образцу Gboard:
     * блокирует нетекстовые поля, URL, email и пароли; возвращает только явно запрошенные `TYPE_TEXT_FLAG_CAP_*`.
     */
    fun resolveEditorCapFlags(inputType: Int): Int {
        if (inputType == EditorInfo.TYPE_NULL) return 0
        val inputClass = inputType and EditorInfo.TYPE_MASK_CLASS
        // Некоторые Compose-поля (например, Google Contacts First name: 0x202060) задают текстовые
        // вариации и CAP_*-флаги без бита TYPE_CLASS_TEXT (inputClass == 0 при inputType != 0).
        if (inputClass != EditorInfo.TYPE_CLASS_TEXT && inputClass != EditorInfo.TYPE_NULL) return 0
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) {
            return 0
        }
        return inputType and (
            EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS or
                EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
    }

    /**
     * Определяет, должна ли автоматически включиться заглавная буква в текущей позиции курсора
     * (поведение Gboard: только при `editorCapFlags != 0`, пустом хвосте строки и пустой строке либо конце предложения с пробелом).
     */
    fun shouldAutoCapitalize(
        beforeCursor: String,
        afterCursor: String,
        editorCapFlags: Int
    ): Boolean {
        if (editorCapFlags == 0) return false
        if ((editorCapFlags and EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) return true

        val lineBefore = beforeCursor.substringAfterLast('\n')
        val lineAfter = afterCursor.substringBefore('\n')
        if (lineAfter.isNotEmpty()) return false
        if (lineBefore.isEmpty()) return true

        val endsWithHorizontalSpace = lineBefore.endsWith(' ') || lineBefore.endsWith('\t')
        if (!endsWithHorizontalSpace) return false

        if ((editorCapFlags and EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
            return true
        }
        if ((editorCapFlags and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
            return isAfterSentenceBoundary(lineBefore)
        }
        return false
    }

    /**
     * Определяет иконку и действие клавиши Enter по [EditorInfo.imeOptions].
     * При установленном `IME_FLAG_NO_ENTER_ACTION` всегда возвращает [EnterKeyAction.NEWLINE].
     */
    fun resolveEnterKeyAction(imeOptions: Int): EnterKeyAction {
        if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EnterKeyAction.NEWLINE
        }
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> EnterKeyAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterKeyAction.SEND
            EditorInfo.IME_ACTION_GO -> EnterKeyAction.GO
            EditorInfo.IME_ACTION_NEXT -> EnterKeyAction.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKeyAction.DONE
            else -> EnterKeyAction.NEWLINE
        }
    }

    /** Должна ли клавиатура автоматически открываться в режиме Numpad (`1234`) для данного [EditorInfo.inputType]. */
    fun prefersNumericPad(inputType: Int): Boolean {
        if (inputType == EditorInfo.TYPE_NULL) return false
        return when (inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.TYPE_CLASS_PHONE -> true
            else -> false
        }
    }

    /**
     * Вычисляет эффективную геометрию для рендера с учётом альбомной ориентации.
     * В книжной ориентации (или при выключенном `compactLandscape`) возвращает точные значения пользователя.
     */
    fun resolveEffectiveGeometry(
        prefs: KeyboardPreferences,
        isLandscape: Boolean
    ): EffectiveKeyboardGeometry {
        if (!isLandscape || !prefs.compactLandscape) {
            return EffectiveKeyboardGeometry(
                keyHeightDp = prefs.keyHeightDp,
                keyGapHDp = prefs.keyGapHDp,
                keyGapVDp = prefs.keyGapVDp,
                fontSizeSp = prefs.fontSizeSp,
                bottomInsetDp = prefs.bottomInsetDp,
                sidePaddingDp = 2f
            )
        }
        return EffectiveKeyboardGeometry(
            keyHeightDp = (prefs.keyHeightDp * 0.72f).coerceIn(32f, 40f),
            keyGapHDp = (prefs.keyGapHDp * 1.35f).coerceIn(prefs.keyGapHDp, 6f),
            keyGapVDp = (prefs.keyGapVDp * 0.55f).coerceAtMost(4f),
            fontSizeSp = (prefs.fontSizeSp * 0.80f).coerceIn(14f, 21f),
            bottomInsetDp = (prefs.bottomInsetDp * 0.3f).coerceAtMost(6f),
            sidePaddingDp = 16f
        )
    }

    /**
     * Вычисляет точную высоту рабочей области клавиатуры (под верхней полосой `36 dp`) в `dp`.
     * Используется в `EmojiPickerPage`, чтобы переключение `Буквы ⇄ Смайлики` происходило с нулевым скачком высоты.
     */
    fun computeKeyboardBodyHeightDp(prefs: KeyboardPreferences, alphaRowCount: Int = 3): Float {
        return if (prefs.hexagonalGrid) {
            val hexRows = 4
            val hexGridHeight = prefs.keyHeightDp + (prefs.keyHeightDp * 0.80f) * (hexRows - 1)
            hexGridHeight + prefs.keyGapHDp + prefs.keyHeightDp
        } else {
            val numRow = if (prefs.showNumberRow) 1 else 0
            val totalRows = numRow + alphaRowCount.coerceAtLeast(1) + 1
            totalRows * (prefs.keyHeightDp + prefs.keyGapVDp)
        }
    }

    private fun isAfterSentenceBoundary(lineBefore: String): Boolean {
        var i = lineBefore.length - 1
        while (i >= 0 && (lineBefore[i] == ' ' || lineBefore[i] == '\t')) {
            i--
        }
        while (i >= 0 && lineBefore[i] in CLOSING_SENTENCE_CHARS) {
            i--
        }
        if (i < 0) return false

        return when (lineBefore[i]) {
            '!', '?', '…' -> {
                if (i == 0) return false
                val cpBefore = Character.codePointBefore(lineBefore, i)
                Character.isLetterOrDigit(cpBefore) || lineBefore[i - 1] in CLOSING_SENTENCE_CHARS
            }
            '.' -> {
                var dotStart = i
                while (dotStart > 0 && lineBefore[dotStart - 1] == '.') {
                    dotStart--
                }
                if (dotStart == 0) return false
                val cpBeforeDots = Character.codePointBefore(lineBefore, dotStart)
                Character.isLetterOrDigit(cpBeforeDots) || lineBefore[dotStart - 1] in CLOSING_SENTENCE_CHARS
            }
            else -> false
        }
    }
}
