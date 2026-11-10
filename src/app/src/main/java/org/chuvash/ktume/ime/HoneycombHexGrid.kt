package org.chuvash.ktume.ime

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: HoneycombHexGrid.kt — паркетная 4-рядная сотовая сетка (`7 + 7 + 7 + [Shift + 5 букв + ⌫]`).
 *
 * Зона нажатия каждого ряда ограничена его высотой через `.height(rowHitHeight).clipToBounds()`:
 * без этого касание в нижнюю часть соты уходило бы в перекрывающий ряд ниже.
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
    onGestureTick: () -> Unit = {},
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit
) {
    val gap = prefs.keyGapHDp.dp
    val maxHexCellsInRow = rows.mapIndexed { idx, r ->
        if (idx == rows.lastIndex) r.size + 2 else r.size
    }.maxOrNull()?.coerceAtLeast(7) ?: 7
    // N сот + 0.5 соты шахматного сдвига = (N + 0.5) шагов по горизонтали
    val hexWidth = (maxWidth - gap * maxHexCellsInRow.toFloat()) / (maxHexCellsInRow.toFloat() + 0.5f)
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
                        iconType = if (shiftState == ShiftState.CAPS_LOCK) KesjeIconType.CAPS_LOCK else KesjeIconType.SHIFT,
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
                        useHexShape = true,
                        customHeight = hexHeight,
                        modifier = Modifier.width(hexWidth),
                        onGestureTick = onGestureTick,
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
