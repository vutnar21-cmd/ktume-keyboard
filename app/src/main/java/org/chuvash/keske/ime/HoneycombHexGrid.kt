package org.chuvash.keske.ime

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: HoneycombHexGrid.kt — паркетная 4-рядная сетка сот.
 * Зона нажатия каждого ряда ограничена его высотой через clipToBounds: без этого
 * касание в нижнюю часть клавиши уходило в клавишу из ряда ниже.
 */

/**
 * Исправленная 4-рядная паркетная сотовая сетка (Honeycomb):
 * - Ряд 1 (нечётный): 7 сот (сдвиг 0)
 * - Ряд 2 (чётный): 7 сот (сдвиг +0.5 шага соты — ровно в пазах под Рядом 1!)
 * - Ряд 3 (нечётный): 7 сот (сдвиг 0 — ровно в пазах под Рядом 2!)
 * - Ряд 4 (чётный): Shift + 5 букв + Backspace = 7 сот (сдвиг +0.5 шага соты — ровно в пазах под Рядом 3!)
 *
 * Ширина одной соты вычисляется из 7.5 колонок (`maxWidth / 7.5`), поэтому все 7 сот со сдвигом 0.5
 * гарантированно помещаются на экран, ни одна клавиша не уезжает за правый край, и нижний ряд с Enter
 * всегда остаётся на своём месте!
 */
@Composable
internal fun HoneycombHexGrid(
    rows: List<List<String>>,
    currentLang: KeyboardLang,
    isShifted: Boolean,
    shiftState: ShiftState,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    maxWidth: Dp,
    onToggleShift: () -> Unit,
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit
) {
    val gap = prefs.keyGapDp.dp
    val maxHexCellsInRow = rows.mapIndexed { idx, r ->
        if (idx == rows.lastIndex) r.size + 2 else r.size
    }.maxOrNull()?.coerceAtLeast(7) ?: 7
    // N сот + 0.5 соты шахматного сдвига = (N + 0.5) шагов по горизонтали
    val pitchX = (maxWidth - gap * maxHexCellsInRow.toFloat()) / (maxHexCellsInRow.toFloat() + 0.5f)
    val hexWidth = pitchX
    val halfPitchOffset = (hexWidth + gap) * 0.5f

    val hexHeight = prefs.keyHeightDp.dp
    val verticalOverlap = hexHeight * 0.20f
    val rowStepY = hexHeight - verticalOverlap
    val totalHeight = hexHeight + rowStepY * (rows.size - 1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        rows.forEachIndexed { rowIndex, rowKeys ->
            val topY = rowStepY * rowIndex
            val isOddRow = (rowIndex % 2 == 1)
            val startIndent = if (isOddRow) halfPitchOffset else 0.dp

            // Ряды рисуются с шагом rowStepY < hexHeight, поэтому их прямоугольные зоны нажатия
            // перекрываются. Без ограничения Compose отдаёт касание нижнему ряду (он нарисован позже),
            // и тап по нижней части клавиши печатает букву из ряда ниже.
            // clipToBounds обрезает И отрисовку, И hit-test по высоте ряда: визуально ничего не теряется
            // (нижние 20 % всё равно перекрыты следующим рядом), а попадание становится однозначным.
            // Последнему ряду снизу ничего не мешает — ему оставляем полную высоту.
            val rowHitHeight = if (rowIndex == rows.lastIndex) hexHeight else rowStepY

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = topY)
                    .padding(start = startIndent)
                    .height(rowHitHeight)
                    .clipToBounds(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
                verticalAlignment = Alignment.Top
            ) {
                if (rowIndex == rows.lastIndex) {
                    SpecialKey(
                        label = "",
                        iconType = if (shiftState == ShiftState.CAPS_LOCK) KeskeIconType.CAPS_LOCK else KeskeIconType.SHIFT,
                        prefs = prefs,
                        palette = palette,
                        isActive = shiftState != ShiftState.OFF,
                        isAccent = shiftState == ShiftState.CAPS_LOCK,
                        useHexShape = true,
                        customHeight = hexHeight,
                        modifier = Modifier.width(hexWidth),
                        onClick = onToggleShift
                    )
                }

                val totalInRow = if (rowIndex == rows.lastIndex) rowKeys.size + 2 else rowKeys.size
                val lastIdx = (totalInRow - 1).coerceAtLeast(1)

                rowKeys.forEachIndexed { colIndex, baseChar ->
                    val displayChar = if (isShifted) baseChar.uppercase() else baseChar
                    val variants = KeyboardLayouts.getLongPressVariants(currentLang, baseChar, isShifted)
                    val effectiveCol = if (rowIndex == rows.lastIndex) colIndex + 1 else colIndex

                    KeyButton(
                        label = displayChar,
                        variants = variants,
                        prefs = prefs,
                        palette = palette,
                        colFraction = effectiveCol.toFloat() / lastIdx.toFloat(),
                        isSwapMode = false,
                        isSelectedForSwap = false,
                        useHexShape = true,
                        customHeight = hexHeight,
                        modifier = Modifier.width(hexWidth),
                        onSwapClick = {},
                        onCommit = onCommit
                    )
                }

                if (rowIndex == rows.lastIndex) {
                    BackspaceKey(
                        prefs = prefs,
                        palette = palette,
                        useHexShape = true,
                        customHeight = hexHeight,
                        modifier = Modifier.width(hexWidth),
                        onTapBackspace = onBackspace,
                        onSwipeDeleteWord = onDeleteWord
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(gap))
}
