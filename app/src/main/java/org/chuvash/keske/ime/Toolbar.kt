package org.chuvash.keske.ime

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeskeFontFamily
import org.chuvash.keske.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: Toolbar.kt — единая однорядная верхняя полоса клавиатуры (36 dp).
 *
 * Всегда занимает ровно один ряд фиксированной высоты (клавиатура не прыгает при открытии буфера):
 * - Справа постоянно закреплены 3 компактные иконки: Буфер обмена [📋], Смайлики [😊], Настройки [⚙️].
 * - Слева (weight 1f) контекстно переключаются три режима:
 *   1) При нажатии [📋] — встроенная капсула буфера обмена на всю левую часть строки (тап по тексту
 *      вставляет содержимое с неявной нормализацией `ăĕçÿ → ӑӗҫӳ`).
 *   2) В раскладке «Чӑвашла» (когда включены частицы) — лента сингармонических частиц и аффиксов.
 *   3) В остальных раскладках (или когда частицы выключены) — кнопки курсора [←] [→] (с автоповтором
 *      при удержании) и быстрая кнопка «Вставить» в 1 касание.
 */

private fun readCurrentClipboardText(clipboard: ClipboardManager?): String {
    val clip = clipboard?.primaryClip ?: return ""
    if (clip.itemCount <= 0) return ""
    return clip.getItemAt(0)?.text?.toString().orEmpty()
}

@Composable
internal fun TopToolbarStrip(
    context: Context,
    lang: KeyboardLang,
    typedWord: String,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    isEmojiPage: Boolean,
    toolbarOverlay: ToolbarOverlay,
    onToggleClipboard: () -> Unit,
    onToggleEmojiPage: () -> Unit,
    onSelectParticle: (String) -> Unit,
    onMoveCursor: (Int) -> Unit,
    onPasteText: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val isClipboardOpen = toolbarOverlay == ToolbarOverlay.CLIPBOARD
    var clipText by remember { mutableStateOf("") }

    DisposableEffect(clipboard, isClipboardOpen) {
        if (isClipboardOpen) {
            clipText = readCurrentClipboardText(clipboard)
            val listener = ClipboardManager.OnPrimaryClipChangedListener {
                clipText = readCurrentClipboardText(clipboard)
            }
            clipboard?.addPrimaryClipChangedListener(listener)
            onDispose {
                clipboard?.removePrimaryClipChangedListener(listener)
            }
        } else {
            onDispose {}
        }
    }

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
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            when {
                isClipboardOpen -> {
                    InlineClipboardCapsule(
                        clipText = clipText,
                        palette = palette,
                        onPaste = onPasteText
                    )
                }

                lang == KeyboardLang.CHUVASH && prefs.showParticlesStrip -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        particlesAndAffixes.forEach { item ->
                            val displayLabel = if (item.startsWith("-")) item else "+$item"
                            Surface(
                                color = palette.specialKeyBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        contentDescription = displayLabel
                                        onClick(label = displayLabel) {
                                            onSelectParticle(item)
                                            true
                                        }
                                    }
                                    .pointerInput(item) {
                                        detectTapGestures(onTap = { onSelectParticle(item) })
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 9.dp)
                                ) {
                                    Text(
                                        text = displayLabel,
                                        color = palette.accentBg,
                                        fontFamily = KeskeFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    CursorAndEditStrip(
                        clipboard = clipboard,
                        palette = palette,
                        onMoveCursor = onMoveCursor,
                        onPasteText = onPasteText
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIconButton(
                iconType = KeskeIconType.CLIPBOARD,
                palette = palette,
                isActive = isClipboardOpen,
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
private fun InlineClipboardCapsule(
    clipText: String,
    palette: KeyboardPalette,
    onPaste: (String) -> Unit
) {
    val hasText = clipText.isNotBlank()
    val singleLinePreview = remember(clipText) {
        if (hasText) clipText.replace(Regex("\\s+"), " ").trim() else "Буфер обмена пуст"
    }

    Surface(
        color = palette.specialKeyBg,
        shape = RoundedCornerShape(10.dp),
        border = if (hasText) BorderStroke(1.dp, palette.accentBg.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .then(
                if (hasText) {
                    Modifier
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "Вставить из буфера обмена"
                            onClick(label = "Вставить") {
                                onPaste(KeyboardLayouts.normalizeChuvashUnicode(clipText))
                                true
                            }
                        }
                        .pointerInput(clipText) {
                            detectTapGestures(onTap = {
                                onPaste(KeyboardLayouts.normalizeChuvashUnicode(clipText))
                            })
                        }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = singleLinePreview,
                color = if (hasText) palette.keyText else palette.hintText,
                fontFamily = KeskeFontFamily,
                fontSize = 12.sp,
                fontWeight = if (hasText) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CursorAndEditStrip(
    clipboard: ClipboardManager?,
    palette: KeyboardPalette,
    onMoveCursor: (Int) -> Unit,
    onPasteText: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CursorRepeatButton(
            iconType = KeskeIconType.ARROW_LEFT,
            a11yLabel = "Курсор влево",
            palette = palette,
            onStep = { onMoveCursor(-1) }
        )
        CursorRepeatButton(
            iconType = KeskeIconType.ARROW_RIGHT,
            a11yLabel = "Курсор вправо",
            palette = palette,
            onStep = { onMoveCursor(1) }
        )
        Surface(
            color = palette.specialKeyBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .height(32.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Вставить из буфера обмена"
                    onClick(label = "Вставить") {
                        val raw = readCurrentClipboardText(clipboard)
                        if (raw.isNotEmpty()) {
                            onPasteText(KeyboardLayouts.normalizeChuvashUnicode(raw))
                        }
                        true
                    }
                }
                .pointerInput(clipboard) {
                    detectTapGestures(onTap = {
                        val raw = readCurrentClipboardText(clipboard)
                        if (raw.isNotEmpty()) {
                            onPasteText(KeyboardLayouts.normalizeChuvashUnicode(raw))
                        }
                    })
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 11.dp)
            ) {
                Text(
                    text = "Вставить",
                    color = palette.keyText,
                    fontFamily = KeskeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CursorRepeatButton(
    iconType: KeskeIconType,
    a11yLabel: String,
    palette: KeyboardPalette,
    onStep: () -> Unit
) {
    val currentOnStep by rememberUpdatedState(onStep)
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        color = if (isPressed) palette.accentBg.copy(alpha = 0.30f) else palette.specialKeyBg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .width(38.dp)
            .height(32.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = a11yLabel
                onClick(label = a11yLabel) {
                    currentOnStep()
                    true
                }
            }
            .pointerInput(iconType) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var repeated = false
                        val repeatJob = launch {
                            delay(360L)
                            while (isActive) {
                                repeated = true
                                currentOnStep()
                                delay(55L)
                            }
                        }
                        val up = try {
                            waitForUpOrCancellation()
                        } finally {
                            repeatJob.cancel()
                            isPressed = false
                        }
                        if (up != null && !repeated) {
                            currentOnStep()
                        }
                    }
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            KeskeVectorIcon(
                type = iconType,
                tint = palette.keyText,
                size = 15.dp
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
