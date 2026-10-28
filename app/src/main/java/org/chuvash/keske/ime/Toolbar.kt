package org.chuvash.keske.ime

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeskeFontFamily
import org.chuvash.keske.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: Toolbar.kt — верхняя полоса клавиатуры.
 * Лента сингармонических частиц и аффиксов, кнопки буфера, смайликов и настроек,
 * выпадающая полоса буфера с неявной нормализацией чувашского Unicode при вставке.
 */

/**
 * Верхняя панель над клавиатурой без словарных подсказок:
 * - Слева (если включены частицы в настройках) чипы чувашских частиц (-и?, -ши?, -ха, -ҫкӗ), иначе просто компактный индикатор раскладки;
 * - Справа 3 кнопки: Буфер обмена, Смайлики, Настройки.
 */
@Composable
internal fun TopToolbarStrip(
    lang: KeyboardLang,
    typedWord: String,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    isEmojiPage: Boolean,
    toolbarOverlay: ToolbarOverlay,
    onToggleClipboard: () -> Unit,
    onToggleEmojiPage: () -> Unit,
    onSelectParticle: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val particlesAndAffixes = remember(typedWord) {
        KeyboardLayouts.getSynharmonicParticlesAndAffixes(typedWord)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (lang == KeyboardLang.CHUVASH && prefs.showParticlesStrip) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                particlesAndAffixes.forEach { item ->
                    val displayLabel = if (item.startsWith("-")) item else "+$item"
                    Surface(
                        color = palette.specialKeyBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.pointerInput(item) {
                            detectTapGestures(onTap = { onSelectParticle(item) })
                        }
                    ) {
                        Text(
                            text = displayLabel,
                            color = palette.accentBg,
                            fontFamily = KeskeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIconButton(
                iconType = KeskeIconType.CLIPBOARD,
                palette = palette,
                isActive = toolbarOverlay == ToolbarOverlay.CLIPBOARD,
                onClick = onToggleClipboard
            )
            ToolbarIconButton(
                iconType = KeskeIconType.EMOJI,
                palette = palette,
                isActive = isEmojiPage,
                onClick = onToggleEmojiPage
            )
            ToolbarIconButton(
                iconType = KeskeIconType.SETTINGS,
                palette = palette,
                isActive = false,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun ToolbarIconButton(
    iconType: KeskeIconType,
    palette: KeyboardPalette,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val a11yLabel = when (iconType) {
        KeskeIconType.CLIPBOARD -> "Буфер обмена"
        KeskeIconType.EMOJI -> "Смайлики"
        KeskeIconType.SETTINGS -> "Настройки клавиатуры"
        else -> iconType.name
    }
    Surface(
        color = if (isActive) palette.accentBg else palette.specialKeyBg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .size(32.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yLabel
                onClick(label = a11yLabel) {
                    onClick()
                    true
                }
            }
            .pointerInput(iconType, isActive) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            KeskeVectorIcon(
                type = iconType,
                tint = if (isActive) palette.accentText else palette.keyText,
                size = 16.dp
            )
        }
    }
}

@Composable
private fun MiniActionChip(
    text: String,
    palette: KeyboardPalette,
    onClick: () -> Unit
) {
    Surface(
        color = palette.keyBg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.pointerInput(text) {
            detectTapGestures(onTap = { onClick() })
        }
    ) {
        Text(
            text = text,
            color = palette.keyText,
            fontFamily = KeskeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun ClipboardOverlayBar(
    context: Context,
    palette: KeyboardPalette,
    onCommit: (String) -> Unit
) {
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    var clipText by remember {
        val item = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        mutableStateOf(item)
    }
    val hasText = clipText.isNotBlank()

    Surface(
        color = palette.specialKeyBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Вся полоса — зона вставки: тап по тексту вставляет содержимое буфера.
            // Конвертация «ложной латиницы» (ăĕçÿ → ӑӗҫӳ) выполняется неявно, буфер не перезаписывается.
            Text(
                text = if (hasText) clipText.take(36) else "Буфер обмена пуст",
                color = if (hasText) palette.keyText else palette.hintText,
                fontFamily = KeskeFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (hasText) {
                            Modifier
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    contentDescription = "Вставить из буфера обмена"
                                    onClick(label = "Вставить") {
                                        onCommit(KeyboardLayouts.normalizeChuvashUnicode(clipText))
                                        true
                                    }
                                }
                                .pointerInput(clipText) {
                                    detectTapGestures(onTap = {
                                        onCommit(KeyboardLayouts.normalizeChuvashUnicode(clipText))
                                    })
                                }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
