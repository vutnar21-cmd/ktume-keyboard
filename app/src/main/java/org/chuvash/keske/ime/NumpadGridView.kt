package org.chuvash.keske.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeskeFontFamily
import org.chuvash.keske.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: NumpadGridView.kt — телефонная сетка 3 × 4 с колонкой действий ⌫ / − / + / ↵.
 */

@Composable
internal fun NumpadGridView(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    gap: Dp,
    onCommit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onEnter: () -> Unit,
    onBackToLetters: () -> Unit,
    onBackToSymbols: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        KeyboardLayouts.numpadRows.forEachIndexed { rIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { keyLabel ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(prefs.keyHeightDp.dp)
                            .shadow(1.dp, RoundedCornerShape(prefs.keyCornerRadiusDp.dp))
                            .clip(RoundedCornerShape(prefs.keyCornerRadiusDp.dp))
                            .background(palette.keyBg)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = "Клавиша $keyLabel"
                                onClick(label = keyLabel) {
                                    onCommit(keyLabel)
                                    true
                                }
                            }
                            .pointerInput(keyLabel) {
                                detectTapGestures(onTap = { onCommit(keyLabel) })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = keyLabel,
                            color = palette.keyText,
                            fontFamily = KeskeFontFamily,
                            fontSize = (prefs.fontSizeSp + 2f).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 4-я колонка: ⌫, −, +, ↵ (как в телефонной панели Gboard/iOS)
                when (rIdx) {
                    0 -> BackspaceKey(
                        prefs = prefs,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onTapBackspace = onBackspace,
                        onSwipeDeleteWord = onDeleteWord
                    )
                    1 -> SpecialKey(
                        label = "−",
                        prefs = prefs,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onClick = { onCommit("-") }
                    )
                    2 -> SpecialKey(
                        label = "+",
                        prefs = prefs,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onClick = { onCommit("+") }
                    )
                    else -> SpecialKey(
                        label = "",
                        iconType = KeskeIconType.ENTER,
                        prefs = prefs,
                        palette = palette,
                        isAccent = true,
                        modifier = Modifier.weight(1f),
                        onClick = onEnter
                    )
                }
            }
        }

        // Нижний ряд: к символам, пробел, к буквам
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialKey(
                label = "?123",
                prefs = prefs,
                palette = palette,
                modifier = Modifier.weight(1.2f),
                onClick = onBackToSymbols
            )
            SpecialKey(
                label = "пробел",
                prefs = prefs,
                palette = palette,
                modifier = Modifier.weight(2.6f),
                onClick = { onCommit(" ") }
            )
            SpecialKey(
                label = "АБВ",
                prefs = prefs,
                palette = palette,
                modifier = Modifier.weight(1.2f),
                onClick = onBackToLetters
            )
        }
    }
}
