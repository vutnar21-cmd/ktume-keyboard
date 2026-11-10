package org.chuvash.ktume.ime

/**
 * МОДУЛЬ: ShiftStateMachine.kt — чистая модель режимов экрана и конечный автомат клавиши `Shift`
 * (ручной тап, двойной тап `CAPS_LOCK` и синхронизация с автозаглавной поля ввода).
 *
 * Не зависит от Compose/Android View и напрямую покрывается unit-тестами.
 */

internal const val DOUBLE_TAP_WINDOW_MS = 420L

internal enum class KeyboardMode {
    LETTERS,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMPAD,
    EMOJI_PAGE
}

internal enum class ShiftState {
    OFF,
    ONCE,
    CAPS_LOCK
}

internal enum class ToolbarOverlay {
    NONE,
    CLIPBOARD
}

/**
 * Единый переход состояния клавиши `Shift` по нажатию:
 * `OFF → ONCE`, двойной тап быстрее [DOUBLE_TAP_WINDOW_MS] `ONCE → CAPS_LOCK`, повторный тап `→ OFF`.
 */
internal fun nextShiftStateOnTap(
    current: ShiftState,
    nowMs: Long,
    lastTapMs: Long
): ShiftState = when {
    current == ShiftState.CAPS_LOCK -> ShiftState.OFF
    current == ShiftState.ONCE && (nowMs - lastTapMs) < DOUBLE_TAP_WINDOW_MS -> ShiftState.CAPS_LOCK
    current == ShiftState.OFF -> ShiftState.ONCE
    else -> ShiftState.OFF
}

/** Состояние `Shift` вместе с флагом «включён автозаглавной, а не пользователем». */
internal data class AutoShift(val shiftState: ShiftState, val armed: Boolean)

/**
 * Синхронизирует `Shift` с автозаглавной редактора при каждом обновлении его контекста:
 * взводит `ONCE`, если поле просит заглавную, а `Shift` выключен (в том числе повторно в полях
 * `CAP_CHARACTERS`), и снимает только тот `ONCE`, который включила сама автозаглавная.
 */
internal fun resolveAutoShift(
    current: AutoShift,
    autoCapitalizeEnabled: Boolean,
    shouldAutoCapitalize: Boolean
): AutoShift = when {
    autoCapitalizeEnabled && shouldAutoCapitalize ->
        if (current.shiftState == ShiftState.OFF) AutoShift(ShiftState.ONCE, armed = true) else current
    current.armed && current.shiftState == ShiftState.ONCE -> AutoShift(ShiftState.OFF, armed = false)
    else -> current
}
