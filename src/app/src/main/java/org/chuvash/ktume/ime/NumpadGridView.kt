package org.chuvash.ktume.ime

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: NumpadGridView.kt — телефонная сетка `3 × 4` с колонкой действий `⌫ / − / + / ↵`.
 * Хитбоксы всех клавиш владеют половиной зазора (`hGap / 2`, `vGap / 2`) без мёртвых зон между ними.
 */
@Composable
internal fun NumpadGridView(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    hGap: Dp,
    vGap: Dp,
    enterIconType: KesjeIconType = KesjeIconType.ENTER,
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onEnter: () -> Unit,
    onBackToLetters: () -> Unit,
    onBackToSymbols: () -> Unit
) {
    val hPad = hGap / 2
    val vPad = vGap / 2
    val numpadPrefs = remember(prefs) { prefs.copy(showKeyPressPopup = false, showSubLabels = false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        KeyboardLayouts.numpadRows.forEachIndexed { rIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEachIndexed { cIdx, keyLabel ->
                    KeyButton(
                        label = keyLabel,
                        variants = emptyList(),
                        prefs = numpadPrefs,
                        palette = palette,
                        colFraction = cIdx / 3f,
                        useHexShape = false,
                        customFontSizeSp = prefs.fontSizeSp + 2f,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1f),
                        onCommit = onCommit
                    )
                }

                when (rIdx) {
                    0 -> BackspaceKey(
                        prefs = prefs,
                        palette = palette,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1f),
                        onTapBackspace = onBackspace,
                        onSwipeDeleteWord = onDeleteWord
                    )
                    1 -> SpecialKey(
                        label = "−",
                        prefs = prefs,
                        palette = palette,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1f),
                        onClick = { onCommit("-") }
                    )
                    2 -> SpecialKey(
                        label = "+",
                        prefs = prefs,
                        palette = palette,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1f),
                        onClick = { onCommit("+") }
                    )
                    else -> SpecialKey(
                        label = "",
                        iconType = enterIconType,
                        prefs = prefs,
                        palette = palette,
                        isAccent = true,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1f),
                        onClick = onEnter
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialKey(
                label = "?123",
                prefs = prefs,
                palette = palette,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(1.2f),
                onClick = onBackToSymbols
            )
            SpecialKey(
                label = "пробел",
                prefs = prefs,
                palette = palette,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(2.6f),
                onClick = { onCommit(" ") }
            )
            SpecialKey(
                label = "АБВ",
                prefs = prefs,
                palette = palette,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(1.2f),
                onClick = onBackToLetters
            )
        }
    }
}
