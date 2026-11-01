package org.chuvash.ktume.ime

import android.graphics.CornerPathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.unit.Density
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
import kotlinx.coroutines.launch
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KesjeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: Keys.kt — символьная клавиша [KeyButton], всплывающие попапы превью и общая отрисовка сот.
 *
 * Функциональные клавиши (`BackspaceKey`, `SpecialKey`, `SpacebarKey`) выделены в `FunctionalKeys.kt`.
 * В прямоугольном режиме каждая клавиша владеет половиной зазора (`gapPadH = hGap / 2`, `gapPadV = vGap / 2`),
 * исключая «мёртвые» щели между клавишами и рядами (Hex-режим передаёт `0 dp`).
 */

private class HexPaintCache(
    val fillPaint: android.graphics.Paint,
    val strokePaint: android.graphics.Paint
)

@Composable
private fun rememberHexPaints(
    cornerPx: Float,
    strokeWidthPx: Float,
    strokeColorArgb: Int
): HexPaintCache {
    return remember(cornerPx, strokeWidthPx, strokeColorArgb) {
        val effect = CornerPathEffect(cornerPx)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            pathEffect = effect
        }
        val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = strokeColorArgb
            pathEffect = effect
        }
        HexPaintCache(fill, stroke)
    }
}

/** Создаёт шестиугольный путь (pointy-top hexagon) для паркетного замощения. */
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
 * Общий модификатор видимой поверхности клавиши: скруглённый шестиугольник с кэшированным `Paint`
 * в режиме сот либо прямоугольник со скруглением `cornerRadiusDp` в классическом режиме.
 */
@Composable
internal fun rememberKeySurfaceModifier(
    useHexShape: Boolean,
    bg: Color,
    cornerRadiusDp: Float,
    boardBg: Color,
    addShadow: Boolean = false
): Modifier {
    val density = LocalDensity.current
    return if (useHexShape) {
        val hexCornerPx = with(density) { (cornerRadiusDp * 0.45f).coerceIn(4f, 9f).dp.toPx() }
        val hexStrokePx = with(density) { 1.4.dp.toPx() }
        val hexPaints = rememberHexPaints(hexCornerPx, hexStrokePx, boardBg.toArgb())
        Modifier.drawBehind {
            val androidPath = buildHexPath(size.width, size.height).asAndroidPath()
            hexPaints.fillPaint.color = bg.toArgb()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(androidPath, hexPaints.fillPaint)
                canvas.nativeCanvas.drawPath(androidPath, hexPaints.strokePaint)
            }
        }
    } else {
        val shape = RoundedCornerShape(cornerRadiusDp.dp)
        val shadowMod = if (addShadow) Modifier.shadow(1.dp, shape) else Modifier
        shadowMod
            .clip(shape)
            .background(bg)
    }
}

/**
 * Клавиша буквы или символа: поддерживает всплывающую «каплю» нажатия, быстрый свайп вверх
 * для 1-го символа, Long-Press с ведением пальца (шаг `40 dp`), свайп влево и гексагональную форму.
 */
@Composable
internal fun KeyButton(
    label: String,
    variants: List<String>,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    colFraction: Float = 0.5f,
    useHexShape: Boolean = prefs.hexagonalGrid,
    customHeight: Dp? = null,
    customFontSizeSp: Float? = null,
    gapPadH: Dp = 0.dp,
    gapPadV: Dp = 0.dp,
    swipeLeftCommit: String? = null,
    modifier: Modifier = Modifier,
    onGestureTick: () -> Unit = {},
    onCommit: (String) -> Unit
) {
    val displayedVariants = remember(variants, colFraction) {
        KeyboardLayouts.getDisplayedLongPressVariants(variants, colFraction)
    }
    val primaryVariant = remember(variants) {
        KeyboardLayouts.primaryLongPressVariant(variants)
    }
    var showLongPressPopup by remember { mutableStateOf(false) }
    var showSwipeUpPreview by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember(variants, colFraction) {
        mutableIntStateOf(KeyboardLayouts.initialLongPressVariantIndex(variants, colFraction))
    }
    var isPressed by remember { mutableStateOf(false) }

    val currentLabel by rememberUpdatedState(label)
    val currentDisplayedVariants by rememberUpdatedState(displayedVariants)
    val currentPrimaryVariant by rememberUpdatedState(primaryVariant)
    val currentSwipeUpEnabled by rememberUpdatedState(prefs.swipeUpFirstVariant)
    val currentLongPressMs by rememberUpdatedState(prefs.longPressDelayMs)
    val currentSwipeLeft by rememberUpdatedState(swipeLeftCommit)
    val currentOnGestureTick by rememberUpdatedState(onGestureTick)
    val currentOnCommit by rememberUpdatedState(onCommit)

    val density = LocalDensity.current
    // Ячейка Long-Press имеет размер 36 dp + зазор 4 dp = ровно 40 dp шага
    val stepPx = with(density) { 40.dp.toPx() }
    val swipeUpThresholdPx = with(density) { 20.dp.toPx() }
    val cancelMovePx = with(density) { 28.dp.toPx() }

    val bg = if (isPressed) palette.accentBg.copy(alpha = 0.32f) else palette.keyBg
    val keyHeight = customHeight ?: prefs.keyHeightDp.dp
    val effectivePadH = if (useHexShape) 0.dp else gapPadH
    val effectivePadV = if (useHexShape) 0.dp else gapPadV

    val visualModifier = rememberKeySurfaceModifier(
        useHexShape = useHexShape,
        bg = bg,
        cornerRadiusDp = prefs.keyCornerRadiusDp,
        boardBg = palette.boardBg,
        addShadow = true
    )

    Box(
        modifier = modifier
            .height(keyHeight + effectivePadV * 2)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label = label) {
                    currentOnCommit(currentLabel)
                    true
                }
            }
            .pointerInput(colFraction) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var longPressTriggered = false
                        var swipeUpActive = false
                        var totalDx = 0f
                        var totalDy = 0f

                        val longPressJob = launch {
                            delay(currentLongPressMs.toLong().coerceIn(130L, 500L))
                            if (!swipeUpActive && currentDisplayedVariants.isNotEmpty()) {
                                longPressTriggered = true
                                selectedVariantIndex = KeyboardLayouts.computeLongPressSelectedIndex(
                                    variants = currentDisplayedVariants,
                                    colFraction = colFraction,
                                    dragSteps = 0
                                )
                                showLongPressPopup = true
                                currentOnGestureTick()
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

                                val canSwipeUp = currentSwipeUpEnabled && currentPrimaryVariant != null
                                if (!longPressTriggered && canSwipeUp) {
                                    if (!swipeUpActive && KeyboardLayouts.isSwipeUpGesture(dx, dy, swipeUpThresholdPx)) {
                                        longPressJob.cancel()
                                        swipeUpActive = true
                                        showSwipeUpPreview = true
                                        currentOnGestureTick()
                                        change.consume()
                                    } else if (swipeUpActive) {
                                        if (KeyboardLayouts.shouldKeepSwipeUpActive(dx, dy, swipeUpThresholdPx)) {
                                            change.consume()
                                        } else {
                                            swipeUpActive = false
                                            showSwipeUpPreview = false
                                        }
                                    }
                                }

                                if (!longPressTriggered && !swipeUpActive && (abs(dx) > cancelMovePx || abs(dy) > cancelMovePx)) {
                                    longPressJob.cancel()
                                }

                                if (longPressTriggered && currentDisplayedVariants.isNotEmpty()) {
                                    change.consume()
                                    val shiftIdx = (dx / stepPx).roundToInt()
                                    val nextIdx = KeyboardLayouts.computeLongPressSelectedIndex(
                                        variants = currentDisplayedVariants,
                                        colFraction = colFraction,
                                        dragSteps = shiftIdx
                                    )
                                    if (nextIdx != selectedVariantIndex) {
                                        selectedVariantIndex = nextIdx
                                        currentOnGestureTick()
                                    }
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            isPressed = false
                            showSwipeUpPreview = false
                        }

                        if (cancelled) {
                            showLongPressPopup = false
                        } else if (longPressTriggered && currentDisplayedVariants.isNotEmpty()) {
                            val chosen = currentDisplayedVariants.getOrElse(selectedVariantIndex) {
                                currentDisplayedVariants.first()
                            }
                            showLongPressPopup = false
                            currentOnCommit(chosen)
                        } else if (swipeUpActive && currentPrimaryVariant != null) {
                            currentOnCommit(currentPrimaryVariant!!)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = effectivePadH, vertical = effectivePadV)
                .then(visualModifier),
            contentAlignment = Alignment.Center
        ) {
            if (prefs.showSubLabels && primaryVariant != null && !useHexShape) {
                Text(
                    text = primaryVariant,
                    color = palette.hintText.copy(alpha = 0.75f),
                    fontFamily = KesjeFontFamily,
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
                    fontFamily = KesjeFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 2.dp, start = 4.dp)
                )
            }

            Text(
                text = label,
                color = palette.keyText,
                fontFamily = KesjeFontFamily,
                fontSize = (customFontSizeSp ?: prefs.fontSizeSp).sp,
                fontWeight = FontWeight.Bold
            )

            val isSwipeArmed = showSwipeUpPreview && primaryVariant != null
            val showSinglePreview = !showLongPressPopup && (
                isSwipeArmed || (prefs.showKeyPressPopup && isPressed)
            )
            if (showSinglePreview) {
                KeyPressPreviewPopup(
                    previewText = if (isSwipeArmed) primaryVariant else label,
                    isSwipeArmed = isSwipeArmed,
                    keyHeight = keyHeight,
                    density = density,
                    palette = palette
                )
            }

            if (showLongPressPopup && displayedVariants.isNotEmpty()) {
                LongPressVariantsPopup(
                    displayedVariants = displayedVariants,
                    selectedVariantIndex = selectedVariantIndex,
                    colFraction = colFraction,
                    keyHeight = keyHeight,
                    density = density,
                    palette = palette,
                    onDismiss = { showLongPressPopup = false }
                )
            }
        }
    }
}

@Composable
private fun KeyPressPreviewPopup(
    previewText: String,
    isSwipeArmed: Boolean,
    keyHeight: Dp,
    density: Density,
    palette: KeyboardPalette
) {
    val previewOffsetY = with(density) { (-keyHeight - 8.dp).roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, previewOffsetY),
        properties = PopupProperties(focusable = false, clippingEnabled = true)
    ) {
        Box(
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(13.dp))
                .clip(RoundedCornerShape(13.dp))
                .background(palette.popupBg)
                .border(
                    width = 1.5.dp,
                    color = if (isSwipeArmed) palette.accentBg.copy(alpha = 0.8f) else palette.hintText.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(13.dp)
                )
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSwipeArmed) palette.accentBg else palette.keyBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = previewText,
                    color = if (isSwipeArmed) palette.accentText else palette.keyText,
                    fontFamily = KesjeFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LongPressVariantsPopup(
    displayedVariants: List<String>,
    selectedVariantIndex: Int,
    colFraction: Float,
    keyHeight: Dp,
    density: Density,
    palette: KeyboardPalette,
    onDismiss: () -> Unit
) {
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
        onDismissRequest = onDismiss
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
            val activeIdx = selectedVariantIndex.coerceIn(0, displayedVariants.lastIndex)
            displayedVariants.forEachIndexed { idx, variant ->
                val isSelected = idx == activeIdx
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
                        fontFamily = KesjeFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
