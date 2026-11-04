package org.chuvash.ktume.data.autocorrect

/**
 * МОДУЛЬ: AutocorrectSession.kt — состояние автозамены в одном поле ввода (эксперимент, задача 013).
 *
 * Отвечает за две вещи, общие для сервиса клавиатуры и тестового поля в настройках:
 * - перед вводом разделителя (пробел, знак препинания, перевод строки) находит исправление слова
 *   перед курсором;
 * - помнит последнюю автозамену, чтобы первый `⌫` сразу после неё вернул набранное слово
 *   (разделитель остаётся), и больше не исправлял это слово до смены поля.
 *
 * Чистая логика: применять правки к тексту и сохранять слово в личный словарь — дело вызывающего.
 */
class AutocorrectSession {

    /** Последняя автозамена: [corrected] + [separator] сейчас стоят перед курсором вместо [original]. */
    data class Revert(val corrected: String, val original: String, val separator: String) {
        /** Текст, который должен стоять перед курсором, чтобы откат был возможен. */
        val committedTail: String get() = corrected + separator
    }

    private var pending: Revert? = null
    private val rejected = HashSet<String>()

    /** Есть ли автозамена, которую можно откатить следующим `⌫`. */
    val hasPendingRevert: Boolean get() = pending != null

    /**
     * Исправление слова перед курсором перед вводом [separator] или `null`.
     * Любой ввод отменяет возможность откатить предыдущую автозамену.
     */
    fun correctionBeforeSeparator(
        beforeCursor: String,
        separator: String,
        corrector: Autocorrector?,
        activeLang: String?,
        personalWords: Set<String>,
        costs: TypoCostModel
    ): Autocorrector.Correction? {
        pending = null
        if (corrector == null || !Autocorrector.isSeparator(separator)) return null
        val word = Autocorrector.extractCorrectableWord(beforeCursor) ?: return null
        if (word.lowercase() in rejected) return null
        return corrector.correct(word, activeLang, personalWords, costs)
    }

    /** Вызывается после того, как исправление и разделитель вставлены в поле. */
    fun onCorrectionApplied(correction: Autocorrector.Correction, separator: String) {
        pending = Revert(correction.replacement, correction.original, separator)
    }

    /**
     * Откат для `⌫`: возвращает последнюю автозамену, если текст перед курсором всё ещё оканчивается
     * на неё, и запоминает исходное слово как отклонённое. Возможность отката сбрасывается в любом случае.
     */
    fun takeRevert(beforeCursor: String): Revert? {
        val revert = pending ?: return null
        pending = null
        if (!beforeCursor.endsWith(revert.committedTail)) return null
        rejected += revert.original.lowercase()
        return revert
    }

    /** Любое другое действие (перемещение курсора, удаление слова, смена раскладки) отменяет откат. */
    fun cancelPending() {
        pending = null
    }

    /** Новое поле ввода: забываем и откат, и отклонённые в прошлом поле слова. */
    fun reset() {
        pending = null
        rejected.clear()
    }
}
