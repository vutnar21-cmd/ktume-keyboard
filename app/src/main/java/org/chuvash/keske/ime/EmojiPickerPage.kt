package org.chuvash.keske.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeskeFontFamily
import org.chuvash.keske.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: EmojiPickerPage.kt — полноэкранная страница смайликов в духе Gboard.
 */

/**
 * Увеличенная полноразмерная страница выбора смайликов (как в Gboard):
 * высота увеличена до 5.3 рядов клавиш (~255 dp), чтобы помещалось 4 полных ряда смайлов без обрезания.
 */
@Composable
internal fun EmojiPickerPage(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    onCommitEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onBackToLetters: () -> Unit
) {
    var selectedCategoryIdx by remember { mutableIntStateOf(0) }
    val categories = KeyboardLayouts.emojiCategories
    val currentCategory = categories.getOrElse(selectedCategoryIdx) { categories.first() }
    val pageHeight = (prefs.keyHeightDp * 5.3f).coerceAtLeast(252f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(pageHeight),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Верхние вкладки категорий смайликов без обрезания слов
        val shortTabTitles = listOf("😊 Эмоции", "👍 Жесты", "❤️ Сердца", "☀️ Природа")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { idx, cat ->
                val isSelected = idx == selectedCategoryIdx
                val tabLabel = shortTabTitles.getOrElse(idx) { "${cat.icon} ${cat.title}" }
                Surface(
                    color = if (isSelected) palette.accentBg else palette.specialKeyBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .pointerInput(cat.id) {
                            detectTapGestures(onTap = { selectedCategoryIdx = idx })
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tabLabel,
                            color = if (isSelected) palette.accentText else palette.keyText,
                            fontFamily = KeskeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Крупное прокручиваемое окно смайликов (7 колонок, высота ячейки 42 dp)
        val rowsOfEmojis = remember(currentCategory) { currentCategory.emojis.chunked(7) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rowsOfEmojis.forEach { emojiRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    emojiRow.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(palette.keyBg.copy(alpha = 0.72f))
                                .pointerInput(emoji) {
                                    detectTapGestures(onTap = { onCommitEmoji(emoji) })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        // Нижняя строка управления на странице смайликов
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(prefs.keyGapHDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialKey(
                label = "АБВ",
                prefs = prefs,
                palette = palette,
                isAccent = true,
                customHeight = 44.dp,
                modifier = Modifier.weight(1.5f),
                onClick = onBackToLetters
            )
            SpecialKey(
                label = "Пробел",
                prefs = prefs,
                palette = palette,
                customHeight = 44.dp,
                modifier = Modifier.weight(3.5f),
                onClick = { onCommitEmoji(" ") }
            )
            BackspaceKey(
                prefs = prefs,
                palette = palette,
                customHeight = 44.dp,
                modifier = Modifier.weight(1.4f),
                onTapBackspace = onBackspace,
                onSwipeDeleteWord = onDeleteWord
            )
        }
    }
}
