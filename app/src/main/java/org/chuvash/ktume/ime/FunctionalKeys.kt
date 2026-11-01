package org.chuvash.ktume.ime

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KesjeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: FunctionalKeys.kt — функциональные клавиши клавиатуры:
 * - [BackspaceKey] — удаление графемы с автоповтором и свайпом удаления слова (в том числе после 380 мс);
 * - [SpecialKey] — функциональная клавиша (`Shift`, `?123`, `🌐`, контекстный `Enter`) с подсветкой нажатия и удержанием;
 * - [SpacebarKey] — пробел с подсветкой нажатия, двойным тапом и `10 dp` свайпом курсора с микро-тиком.
 */

@Composable
internal fun BackspaceKey(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    useHexShape: Boolean = false,
    customHeight: Dp? = null,
    gapPadH: Dp = 0.dp,
    gapPadV: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onTapBackspace: () -> Unit,
    onSwipeDeleteWord: () -> Unit
) {
    val currentOnTap by rememberUpdatedState(onTapBackspace)
    val currentOnSwipeWord by rememberUpdatedState(onSwipeDeleteWord)
    val density = LocalDensity.current
    val swipeWordPx = with(density) { 34.dp.toPx() }
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) palette.accentBg.copy(alpha = 0.30f) else palette.specialKeyBg
    val keyHeight = customHeight ?: prefs.keyHeightDp.dp
    val effectivePadH = if (useHexShape) 0.dp else gapPadH
    val effectivePadV = if (useHexShape) 0.dp else gapPadV

    val shapeMod = rememberKeySurfaceModifier(
        useHexShape = useHexShape,
        bg = bg,
        cornerRadiusDp = prefs.keyCornerRadiusDp,
        boardBg = palette.boardBg
    )

    Box(
        modifier = modifier
            .height(keyHeight + effectivePadV * 2)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Удалить символ"
                onClick(label = "Удалить символ") {
                    currentOnTap()
                    true
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var swipedWord = false
                        var repeatCount = 0

                        val repeatJob = launch {
                            delay(380L)
                            while (isActive && !swipedWord) {
                                currentOnTap()
                                repeatCount++
                                delay(65L)
                            }
                        }

                        var cancelled = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change == null) {
                                    cancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (change.isConsumed) cancelled = true
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                // Свайп влево удаляет слово даже после старта автоповтора (> 380 мс)
                                if (dx < -swipeWordPx && !swipedWord) {
                                    swipedWord = true
                                    repeatJob.cancel()
                                    currentOnSwipeWord()
                                }
                            }
                        } finally {
                            repeatJob.cancel()
                            isPressed = false
                        }

                        if (!cancelled && !swipedWord && repeatCount == 0) {
                            currentOnTap()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = effectivePadH, vertical = effectivePadV)
                .then(shapeMod),
            contentAlignment = Alignment.Center
        ) {
            KesjeVectorIcon(
                type = KesjeIconType.BACKSPACE,
                tint = palette.keyText,
                size = 20.dp
            )
        }
    }
}

@Composable
internal fun SpecialKey(
    label: String,
    iconType: KesjeIconType? = null,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    isActive: Boolean = false,
    isAccent: Boolean = false,
    useHexShape: Boolean = false,
    customHeight: Dp? = null,
    gapPadH: Dp = 0.dp,
    gapPadV: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentLongPressMs by rememberUpdatedState(prefs.longPressDelayMs)

    val bg = when {
        isAccent && isPressed -> palette.accentBg.copy(alpha = 0.72f)
        isAccent -> palette.accentBg
        isPressed -> palette.accentBg.copy(alpha = 0.34f)
        isActive -> palette.accentBg.copy(alpha = 0.24f)
        else -> palette.specialKeyBg
    }
    val fg = if (isAccent) palette.accentText else palette.keyText
    val keyHeight = customHeight ?: prefs.keyHeightDp.dp
    val effectivePadH = if (useHexShape) 0.dp else gapPadH
    val effectivePadV = if (useHexShape) 0.dp else gapPadV
    val density = LocalDensity.current
    val cancelMovePx = with(density) { 32.dp.toPx() }

    val shapeMod = rememberKeySurfaceModifier(
        useHexShape = useHexShape,
        bg = bg,
        cornerRadiusDp = prefs.keyCornerRadiusDp,
        boardBg = palette.boardBg
    )

    val a11yLabel = when {
        label.isNotBlank() -> label
        iconType == KesjeIconType.SHIFT -> "Регистр Shift"
        iconType == KesjeIconType.CAPS_LOCK -> "Caps Lock"
        iconType == KesjeIconType.ENTER -> "Ввод"
        iconType == KesjeIconType.SEARCH -> "Поиск"
        iconType == KesjeIconType.SEND -> "Отправить"
        iconType == KesjeIconType.NEXT -> "Далее"
        iconType == KesjeIconType.DONE -> "Готово"
        iconType == KesjeIconType.ARROW_RIGHT -> "Перейти"
        iconType == KesjeIconType.GLOBE -> "Сменить язык"
        iconType == KesjeIconType.BACKSPACE -> "Удалить"
        else -> "Клавиша"
    }

    Box(
        modifier = modifier
            .height(keyHeight + effectivePadV * 2)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yLabel
                onClick(label = a11yLabel) {
                    currentOnClick()
                    true
                }
                if (onLongClick != null) {
                    onLongClick(label = "Меню раскладок") {
                        currentOnLongClick?.invoke()
                        true
                    }
                }
            }
            .pointerInput(label, iconType, onLongClick != null) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var longPressed = false
                        var movedOut = false

                        val lpJob = if (currentOnLongClick != null) {
                            launch {
                                delay(currentLongPressMs.toLong().coerceIn(150L, 500L))
                                if (!movedOut) {
                                    longPressed = true
                                    currentOnLongClick?.invoke()
                                }
                            }
                        } else null

                        var cancelled = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change == null) {
                                    cancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (change.isConsumed) cancelled = true
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (abs(dx) > cancelMovePx || abs(dy) > cancelMovePx) {
                                    movedOut = true
                                    lpJob?.cancel()
                                }
                            }
                        } finally {
                            lpJob?.cancel()
                            isPressed = false
                        }

                        if (!cancelled && !longPressed && !movedOut) {
                            currentOnClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = effectivePadH, vertical = effectivePadV)
                .then(shapeMod),
            contentAlignment = Alignment.Center
        ) {
            if (iconType != null) {
                KesjeVectorIcon(
                    type = iconType,
                    tint = fg,
                    size = 19.dp
                )
            } else {
                Text(
                    text = label,
                    color = fg,
                    fontFamily = KesjeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            overlayContent()
        }
    }
}

@Composable
internal fun SpacebarKey(
    lang: KeyboardLang,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    gapPadH: Dp = 0.dp,
    gapPadV: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onGestureTick: () -> Unit = {},
    onCommitSpace: () -> Unit,
    onMoveCursor: (Int) -> Unit
) {
    val currentOnSpace by rememberUpdatedState(onCommitSpace)
    val currentOnMove by rememberUpdatedState(onMoveCursor)
    val currentOnTick by rememberUpdatedState(onGestureTick)
    var isPressed by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val cursorStepPx = with(density) { 10.dp.toPx() }
    val bg = if (isPressed) palette.accentBg.copy(alpha = 0.30f) else palette.keyBg
    val surfaceMod = rememberKeySurfaceModifier(
        useHexShape = false,
        bg = bg,
        cornerRadiusDp = prefs.keyCornerRadiusDp,
        boardBg = palette.boardBg,
        addShadow = true
    )

    Box(
        modifier = modifier
            .height(prefs.keyHeightDp.dp + gapPadV * 2)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Пробел (${lang.spaceLabel})"
                onClick(label = "Пробел") {
                    currentOnSpace()
                    true
                }
            }
            .pointerInput(lang) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var draggingCursor = false
                        var lastAnchorX = down.position.x
                        var cancelled = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change == null) {
                                    cancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (change.isConsumed && !draggingCursor) cancelled = true
                                    break
                                }
                                val totalDx = change.position.x - down.position.x
                                val totalDy = change.position.y - down.position.y
                                if (!draggingCursor && abs(totalDx) >= cursorStepPx && abs(totalDx) > abs(totalDy)) {
                                    draggingCursor = true
                                }
                                if (draggingCursor) {
                                    change.consume()
                                    val deltaFromAnchor = change.position.x - lastAnchorX
                                    if (deltaFromAnchor >= cursorStepPx) {
                                        val steps = (deltaFromAnchor / cursorStepPx).toInt()
                                        repeat(steps) { currentOnMove(1) }
                                        currentOnTick()
                                        lastAnchorX += steps * cursorStepPx
                                    } else if (deltaFromAnchor <= -cursorStepPx) {
                                        val steps = (-deltaFromAnchor / cursorStepPx).toInt()
                                        repeat(steps) { currentOnMove(-1) }
                                        currentOnTick()
                                        lastAnchorX -= steps * cursorStepPx
                                    }
                                }
                            }
                        } finally {
                            isPressed = false
                        }

                        if (!cancelled && !draggingCursor) {
                            currentOnSpace()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = gapPadH, vertical = gapPadV)
                .then(surfaceMod),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lang.spaceLabel,
                color = palette.hintText,
                fontFamily = KesjeFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
