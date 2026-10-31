package org.chuvash.keske.ime

import android.graphics.CornerPathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeskeFontFamily
import org.chuvash.keske.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: Keys.kt — клавиши и их жесты.
 *
 * KeyButton — буква или символ с Long-Press и ведением пальца; BackspaceKey — удаление
 * с автоповтором и свайпом по слову; SpecialKey — функциональная клавиша;
 * SpacebarKey — пробел со свайпом курсора и двойным нажатием.
 */

/**
 * Создаёт шестиугольный путь (pointy-top hexagon) для паркетного замощения
 */
private fun buildHexPath(w: Float, h: Float): Path {
    return Path().apply {
        moveTo(w * 0.5f, 0f)
        lineTo(w, h * 0.26f)
        lineTo(w, h * 0.74f)
        lineTo(w * 0.5f, h)
        lineTo(0f, h * 0.74f)
        lineTo(0f, h * 0.26f)
        close()
    }
}

/**
 * Клавиша с выравниванием облачка долгого нажатия (Long-Press) на боковых клавишах
 * и скруглённой гексагональной отрисовкой (для режима сот).
 */
@Composable
internal fun KeyButton(
    label: String,
    variants: List<String>,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    colFraction: Float = 0.5f,
    isSwapMode: Boolean,
    isSelectedForSwap: Boolean,
    useHexShape: Boolean = prefs.hexagonalGrid,
    customHeight: Dp? = null,
    swipeLeftCommit: String? = null,
    modifier: Modifier = Modifier,
    onSwapClick: () -> Unit,
    onCommit: (String) -> Unit
) {
    val displayedVariants = remember(variants, colFraction) {
        KeyboardLayouts.getDisplayedLongPressVariants(variants, colFraction)
    }
    var showLongPressPopup by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember(variants, colFraction) {
        mutableIntStateOf(KeyboardLayouts.initialLongPressVariantIndex(variants, colFraction))
    }
    var isPressed by remember { mutableStateOf(false) }

    val currentLabel by rememberUpdatedState(label)
    val currentVariants by rememberUpdatedState(displayedVariants)
    val currentSwapMode by rememberUpdatedState(isSwapMode)
    val currentLongPressMs by rememberUpdatedState(prefs.longPressDelayMs)
    val currentSwipeLeft by rememberUpdatedState(swipeLeftCommit)
    val currentOnSwapClick by rememberUpdatedState(onSwapClick)
    val currentOnCommit by rememberUpdatedState(onCommit)

    val density = LocalDensity.current
    val stepPx = with(density) { 28.dp.toPx() }
    val cancelMovePx = with(density) { 28.dp.toPx() }
    val hexCornerPx = with(density) { (prefs.keyCornerRadiusDp * 0.45f).coerceIn(4f, 9f).dp.toPx() }

    val bg = when {
        isSelectedForSwap -> Color(0xFFFEF3C7)
        isPressed -> palette.accentBg.copy(alpha = 0.30f)
        else -> palette.keyBg
    }

    val keyHeight = customHeight ?: prefs.keyHeightDp.dp
    val rectShape = RoundedCornerShape(prefs.keyCornerRadiusDp.dp)

    val visualModifier = if (useHexShape) {
        Modifier.drawBehind {
            val androidPath = buildHexPath(size.width, size.height).asAndroidPath()
            val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = bg.toArgb()
                pathEffect = CornerPathEffect(hexCornerPx)
            }
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = with(density) { 1.4.dp.toPx() }
                color = palette.boardBg.toArgb()
                pathEffect = CornerPathEffect(hexCornerPx)
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(androidPath, fillPaint)
                canvas.nativeCanvas.drawPath(androidPath, strokePaint)
            }
        }
    } else {
        val borderMod = if (isSwapMode) {
            Modifier.border(
                width = if (isSelectedForSwap) 2.dp else 1.dp,
                color = if (isSelectedForSwap) Color(0xFFD97706) else palette.accentBg,
                shape = rectShape
            )
        } else Modifier

        Modifier
            .shadow(1.dp, rectShape)
            .clip(rectShape)
            .background(bg)
            .then(borderMod)
    }

    Box(
        modifier = modifier
            .height(keyHeight)
            .then(visualModifier)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label = label) {
                    if (currentSwapMode) currentOnSwapClick() else currentOnCommit(currentLabel)
                    true
                }
            }
            .pointerInput(colFraction) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var longPressTriggered = false
                        var totalDx = 0f
                        var totalDy = 0f

                        val longPressJob = launch {
                            delay(currentLongPressMs.toLong().coerceIn(130L, 500L))
                            if (!currentSwapMode && currentVariants.isNotEmpty()) {
                                longPressTriggered = true
                                selectedVariantIndex = KeyboardLayouts.computeLongPressSelectedIndex(
                                    variants = currentVariants,
                                    colFraction = colFraction,
                                    dragSteps = 0
                                )
                                showLongPressPopup = true
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
                                val dy = change.position.y - down.position.y
                                totalDx = dx
                                totalDy = dy

                                if (!longPressTriggered && (abs(dx) > cancelMovePx || abs(dy) > cancelMovePx)) {
                                    longPressJob.cancel()
                                }

                                if (longPressTriggered && currentVariants.isNotEmpty()) {
                                    change.consume()
                                    val shiftIdx = (dx / stepPx).roundToInt()
                                    selectedVariantIndex = KeyboardLayouts.computeLongPressSelectedIndex(
                                        variants = currentVariants,
                                        colFraction = colFraction,
                                        dragSteps = shiftIdx
                                    )
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            isPressed = false
                        }

                        if (cancelled) {
                            showLongPressPopup = false
                        } else if (currentSwapMode) {
                            currentOnSwapClick()
                        } else if (longPressTriggered && currentVariants.isNotEmpty()) {
                            val chosen = currentVariants.getOrElse(selectedVariantIndex) { currentVariants.first() }
                            showLongPressPopup = false
                            currentOnCommit(chosen)
                        } else if (currentSwipeLeft != null && totalDx < -cancelMovePx && abs(totalDx) > abs(totalDy)) {
                            currentOnCommit(currentSwipeLeft!!)
                        } else {
                            currentOnCommit(currentLabel)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (prefs.showSubLabels && displayedVariants.isNotEmpty() && !isSwapMode && !useHexShape) {
            Text(
                text = displayedVariants.getOrElse(
                    KeyboardLayouts.initialLongPressVariantIndex(variants, colFraction)
                ) { displayedVariants.first() },
                color = palette.hintText.copy(alpha = 0.75f),
                fontFamily = KeskeFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }

        if (swipeLeftCommit != null && !prefs.showHyphenKey) {
            Text(
                text = swipeLeftCommit,
                color = palette.hintText.copy(alpha = 0.75f),
                fontFamily = KeskeFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp, start = 4.dp)
            )
        }

        Text(
            text = label,
            color = if (isSelectedForSwap) Color(0xFF92400E) else palette.keyText,
            fontFamily = KeskeFontFamily,
            fontSize = prefs.fontSizeSp.sp,
            fontWeight = FontWeight.Bold
        )

        if (showLongPressPopup && displayedVariants.isNotEmpty()) {
            val popupOffsetY = with(density) { (-keyHeight - 8.dp).roundToPx() }
            val popupAlignment = if (KeyboardLayouts.isRightSideKey(colFraction)) {
                Alignment.TopEnd
            } else {
                Alignment.TopStart
            }

            Popup(
                alignment = popupAlignment,
                offset = IntOffset(0, popupOffsetY),
                properties = PopupProperties(focusable = false, clippingEnabled = true),
                onDismissRequest = { showLongPressPopup = false }
            ) {
                Row(
                    modifier = Modifier
                        .shadow(10.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.popupBg)
                        .border(1.5.dp, palette.accentBg.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayedVariants.forEachIndexed { idx, variant ->
                        val isSelected = idx == selectedVariantIndex.coerceIn(0, displayedVariants.lastIndex)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) palette.accentBg else palette.keyBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = variant,
                                color = if (isSelected) palette.accentText else palette.keyText,
                                fontFamily = KeskeFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackspaceKey(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    useHexShape: Boolean = false,
    customHeight: Dp? = null,
    modifier: Modifier = Modifier,
    onTapBackspace: () -> Unit,
    onSwipeDeleteWord: () -> Unit
) {
    val currentOnTap by rememberUpdatedState(onTapBackspace)
    val currentOnSwipeWord by rememberUpdatedState(onSwipeDeleteWord)
    val density = LocalDensity.current
    val swipeWordPx = with(density) { 34.dp.toPx() }
    val hexCornerPx = with(density) { (prefs.keyCornerRadiusDp * 0.45f).coerceIn(4f, 9f).dp.toPx() }
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) palette.accentBg.copy(alpha = 0.25f) else palette.specialKeyBg
    val keyHeight = customHeight ?: prefs.keyHeightDp.dp

    val shapeMod = if (useHexShape) {
        Modifier.drawBehind {
            val androidPath = buildHexPath(size.width, size.height).asAndroidPath()
            val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = bg.toArgb()
                pathEffect = CornerPathEffect(hexCornerPx)
            }
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = with(density) { 1.4.dp.toPx() }
                color = palette.boardBg.toArgb()
                pathEffect = CornerPathEffect(hexCornerPx)
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(androidPath, fillPaint)
                canvas.nativeCanvas.drawPath(androidPath, strokePaint)
            }
        }
    } else {
        Modifier
            .clip(RoundedCornerShape(prefs.keyCornerRadiusDp.dp))
            .background(bg)
    }

    Box(
        modifier = modifier
            .height(keyHeight)
            .then(shapeMod)
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
                                if (dx < -swipeWordPx && !swipedWord && repeatCount == 0) {
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
        KeskeVectorIcon(
            type = KeskeIconType.BACKSPACE,
            tint = palette.keyText,
            size = 20.dp
        )
    }
}

@Composable
internal fun SpecialKey(
    label: String,
    iconType: KeskeIconType? = null,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    isActive: Boolean = false,
    isAccent: Boolean = false,
    useHexShape: Boolean = false,
    customHeight: Dp? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        isAccent -> palette.accentBg
        isActive -> palette.accentBg.copy(alpha = 0.24f)
        else -> palette.specialKeyBg
    }
    val fg = if (isAccent) palette.accentText else palette.keyText
    val keyHeight = customHeight ?: prefs.keyHeightDp.dp
    val density = LocalDensity.current
    val hexCornerPx = with(density) { (prefs.keyCornerRadiusDp * 0.45f).coerceIn(4f, 9f).dp.toPx() }

    val shapeMod = if (useHexShape) {
        Modifier.drawBehind {
            val androidPath = buildHexPath(size.width, size.height).asAndroidPath()
            val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = bg.toArgb()
                pathEffect = CornerPathEffect(hexCornerPx)
            }
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = with(density) { 1.4.dp.toPx() }
                color = palette.boardBg.toArgb()
                pathEffect = CornerPathEffect(hexCornerPx)
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(androidPath, fillPaint)
                canvas.nativeCanvas.drawPath(androidPath, strokePaint)
            }
        }
    } else {
        Modifier
            .clip(RoundedCornerShape(prefs.keyCornerRadiusDp.dp))
            .background(bg)
    }

    val a11yLabel = when {
        label.isNotBlank() -> label
        iconType == KeskeIconType.SHIFT -> "Регистр Shift"
        iconType == KeskeIconType.CAPS_LOCK -> "Caps Lock"
        iconType == KeskeIconType.ENTER -> "Ввод"
        iconType == KeskeIconType.GLOBE -> "Сменить язык"
        iconType == KeskeIconType.BACKSPACE -> "Удалить"
        else -> "Клавиша"
    }

    Box(
        modifier = modifier
            .height(keyHeight)
            .then(shapeMod)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yLabel
                onClick(label = a11yLabel) {
                    onClick()
                    true
                }
            }
            .pointerInput(label, iconType) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (iconType != null) {
            KeskeVectorIcon(
                type = iconType,
                tint = fg,
                size = 19.dp
            )
        } else {
            Text(
                text = label,
                color = fg,
                fontFamily = KeskeFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun SpacebarKey(
    lang: KeyboardLang,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    modifier: Modifier = Modifier,
    onCommitSpace: () -> Unit,
    onMoveCursor: (Int) -> Unit
) {
    val currentOnSpace by rememberUpdatedState(onCommitSpace)
    val currentOnMove by rememberUpdatedState(onMoveCursor)

    Box(
        modifier = modifier
            .height(prefs.keyHeightDp.dp)
            .shadow(1.dp, RoundedCornerShape(prefs.keyCornerRadiusDp.dp))
            .clip(RoundedCornerShape(prefs.keyCornerRadiusDp.dp))
            .background(palette.keyBg)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Пробел (${lang.spaceLabel})"
                onClick(label = "Пробел") {
                    currentOnSpace()
                    true
                }
            }
            .pointerInput(lang) {
                detectTapGestures(onTap = { currentOnSpace() })
            }
            .pointerInput(Unit) {
                var accumDx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumDx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumDx += dragAmount
                        if (accumDx > 18f) {
                            currentOnMove(1)
                            accumDx = 0f
                        } else if (accumDx < -18f) {
                            currentOnMove(-1)
                            accumDx = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lang.spaceLabel,
            color = palette.hintText,
            fontFamily = KeskeFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
