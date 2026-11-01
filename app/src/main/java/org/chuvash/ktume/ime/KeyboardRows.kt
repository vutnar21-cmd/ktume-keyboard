package org.chuvash.ktume.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KeskeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: KeyboardRows.kt — ряды классической прямоугольной раскладки:
 * - [NumberRowStrip] — общий верхний ряд цифр `1–0` над буквами и символами;
 * - [AlphaSymbolRows] — основные 3 ряда букв или символов (`?123`, `=\<`);
 * - [KeyboardBottomBar] — нижний функциональный ряд (`?123 · , · 🌐 · пробел · - · . · ↵`);
 * - [GlobeLanguageMenuPopup] — всплывающее меню раскладок и выбора системной клавиатуры по удержанию `🌐`.
 */

@Composable
internal fun NumberRowStrip(
    currentLang: KeyboardLang,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    hPad: Dp,
    vPad: Dp,
    onGestureTick: () -> Unit,
    onCommit: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        KeyboardLayouts.numberRow.forEachIndexed { idx, num ->
            val numVariants = KeyboardLayouts.getLongPressVariants(currentLang, num, false)
            KeyButton(
                label = num,
                variants = numVariants,
                prefs = prefs,
                palette = palette,
                colFraction = idx.toFloat() / 9f,
                useHexShape = false,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(1f),
                onGestureTick = onGestureTick,
                onCommit = onCommit
            )
        }
    }
}

@Composable
internal fun AlphaSymbolRows(
    activeRows: List<List<String>>,
    currentLang: KeyboardLang,
    keyboardMode: KeyboardMode,
    shiftState: ShiftState,
    isShifted: Boolean,
    uniformKeyPitch: Dp,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    hPad: Dp,
    vPad: Dp,
    onToggleShift: () -> Unit,
    onToggleSymbolSubpage: () -> Unit,
    onGestureTick: () -> Unit,
    onCommitChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit
) {
    activeRows.forEachIndexed { rowIndex, rowKeys ->
        val isMiddleNineRow = (rowIndex == 1 && rowKeys.size == 9 && activeRows.first().size == 10)
        val rowSidePad = if (isMiddleNineRow) (uniformKeyPitch * 0.5f) else 0.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rowSidePad),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rowIndex == activeRows.lastIndex) {
                if (keyboardMode == KeyboardMode.LETTERS) {
                    SpecialKey(
                        label = "",
                        iconType = if (shiftState == ShiftState.CAPS_LOCK) KeskeIconType.CAPS_LOCK else KeskeIconType.SHIFT,
                        prefs = prefs,
                        palette = palette,
                        isActive = shiftState != ShiftState.OFF,
                        isAccent = shiftState == ShiftState.CAPS_LOCK,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1.35f),
                        onClick = onToggleShift
                    )
                } else {
                    SpecialKey(
                        label = if (keyboardMode == KeyboardMode.SYMBOLS_1) "=\\<" else "?123",
                        prefs = prefs,
                        palette = palette,
                        isActive = keyboardMode == KeyboardMode.SYMBOLS_2,
                        gapPadH = hPad,
                        gapPadV = vPad,
                        modifier = Modifier.weight(1.35f),
                        onClick = onToggleSymbolSubpage
                    )
                }
            }

            val lastColIdx = (rowKeys.size - 1).coerceAtLeast(1)
            rowKeys.forEachIndexed { colIndex, baseChar ->
                val displayChar = if (keyboardMode == KeyboardMode.LETTERS && isShifted) {
                    baseChar.uppercase()
                } else {
                    baseChar
                }
                val variants = if (keyboardMode == KeyboardMode.LETTERS) {
                    KeyboardLayouts.getLongPressVariants(currentLang, baseChar, isShifted)
                } else {
                    KeyboardLayouts.digitAndSymbolLongPress[baseChar] ?: emptyList()
                }

                KeyButton(
                    label = displayChar,
                    variants = variants,
                    prefs = prefs,
                    palette = palette,
                    colFraction = colIndex.toFloat() / lastColIdx.toFloat(),
                    useHexShape = false,
                    gapPadH = hPad,
                    gapPadV = vPad,
                    modifier = Modifier.weight(1f),
                    onGestureTick = onGestureTick,
                    onCommit = onCommitChar
                )
            }

            if (rowIndex == activeRows.lastIndex) {
                BackspaceKey(
                    prefs = prefs,
                    palette = palette,
                    gapPadH = hPad,
                    gapPadV = vPad,
                    modifier = Modifier.weight(1.35f),
                    onTapBackspace = onBackspace,
                    onSwipeDeleteWord = onDeleteWord
                )
            }
        }
    }
}

@Composable
internal fun KeyboardBottomBar(
    keyboardMode: KeyboardMode,
    enabledLangs: List<KeyboardLang>,
    currentLang: KeyboardLang,
    showLangMenuPopup: Boolean,
    enterIconType: KeskeIconType,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    padH: Dp,
    padV: Dp,
    onToggleSymbolsMode: () -> Unit,
    onOpenNumpad: () -> Unit,
    onGlobeClick: () -> Unit,
    onGlobeLongClick: () -> Unit,
    onSelectLang: (KeyboardLang) -> Unit,
    onDismissLangMenu: () -> Unit,
    onShowImePicker: (() -> Unit)?,
    onGestureTick: () -> Unit,
    onCommitSpace: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onCommitText: (String) -> Unit,
    onEnter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpecialKey(
            label = if (keyboardMode == KeyboardMode.LETTERS) "?123" else "АБВ",
            prefs = prefs,
            palette = palette,
            isActive = keyboardMode != KeyboardMode.LETTERS,
            gapPadH = padH,
            gapPadV = padV,
            modifier = Modifier.weight(1.25f),
            onClick = onToggleSymbolsMode
        )

        KeyButton(
            label = ",",
            variants = KeyboardLayouts.getLongPressVariants(currentLang, ",", false),
            prefs = prefs,
            palette = palette,
            colFraction = 0.2f,
            useHexShape = false,
            gapPadH = padH,
            gapPadV = padV,
            modifier = Modifier.weight(0.9f),
            onGestureTick = onGestureTick,
            onCommit = onCommitText
        )

        if (keyboardMode == KeyboardMode.LETTERS) {
            SpecialKey(
                label = "",
                iconType = KeskeIconType.GLOBE,
                prefs = prefs,
                palette = palette,
                isActive = showLangMenuPopup,
                gapPadH = padH,
                gapPadV = padV,
                modifier = Modifier.weight(0.95f),
                onLongClick = onGlobeLongClick,
                onClick = onGlobeClick,
                overlayContent = {
                    if (showLangMenuPopup) {
                        GlobeLanguageMenuPopup(
                            enabledLangs = enabledLangs,
                            currentLang = currentLang,
                            keyHeight = prefs.keyHeightDp.dp,
                            palette = palette,
                            onSelectLang = onSelectLang,
                            onShowImePicker = onShowImePicker,
                            onDismiss = onDismissLangMenu
                        )
                    }
                }
            )
        } else {
            SpecialKey(
                label = "1234",
                prefs = prefs,
                palette = palette,
                isActive = false,
                gapPadH = padH,
                gapPadV = padV,
                modifier = Modifier.weight(1.05f),
                onClick = onOpenNumpad
            )
        }

        SpacebarKey(
            lang = currentLang,
            prefs = prefs,
            palette = palette,
            gapPadH = padH,
            gapPadV = padV,
            modifier = Modifier.weight(if (prefs.showHyphenKey) 3.4f else 4.2f),
            onGestureTick = onGestureTick,
            onCommitSpace = onCommitSpace,
            onMoveCursor = onMoveCursor
        )

        if (prefs.showHyphenKey) {
            KeyButton(
                label = "-",
                variants = KeyboardLayouts.getLongPressVariants(currentLang, "-", false),
                prefs = prefs,
                palette = palette,
                colFraction = 0.72f,
                useHexShape = false,
                gapPadH = padH,
                gapPadV = padV,
                modifier = Modifier.weight(0.85f),
                onGestureTick = onGestureTick,
                onCommit = onCommitText
            )
        }

        KeyButton(
            label = ".",
            variants = KeyboardLayouts.getLongPressVariants(currentLang, ".", false),
            prefs = prefs,
            palette = palette,
            colFraction = 0.85f,
            useHexShape = false,
            gapPadH = padH,
            gapPadV = padV,
            swipeLeftCommit = "-",
            modifier = Modifier.weight(0.9f),
            onGestureTick = onGestureTick,
            onCommit = onCommitText
        )

        SpecialKey(
            label = "",
            iconType = enterIconType,
            prefs = prefs,
            palette = palette,
            isAccent = true,
            gapPadH = padH,
            gapPadV = padV,
            modifier = Modifier.weight(1.35f),
            onClick = onEnter
        )
    }
}

@Composable
private fun GlobeLanguageMenuPopup(
    enabledLangs: List<KeyboardLang>,
    currentLang: KeyboardLang,
    keyHeight: Dp,
    palette: KeyboardPalette,
    onSelectLang: (KeyboardLang) -> Unit,
    onShowImePicker: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val currentOnSelectLang by rememberUpdatedState(onSelectLang)
    val currentOnShowImePicker by rememberUpdatedState(onShowImePicker)
    // Alignment.BottomStart привязывает нижний край попапа к нижнему краю клавиши 🌐,
    // поэтому сдвиг на -(keyHeight + 6.dp) всегда размещает меню над клавишей независимо от числа строк.
    val offsetY = with(density) { (-keyHeight - 6.dp).roundToPx() }

    Popup(
        alignment = Alignment.BottomStart,
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(focusable = false, clippingEnabled = true),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .width(196.dp)
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(palette.popupBg)
                .border(1.5.dp, palette.accentBg.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            enabledLangs.forEach { lang ->
                val isCurrent = lang == currentLang
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isCurrent) palette.accentBg else palette.keyBg.copy(alpha = 0.65f))
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = lang.spaceLabel
                            onClick(label = lang.spaceLabel) {
                                currentOnSelectLang(lang)
                                true
                            }
                        }
                        .pointerInput(lang) {
                            detectTapGestures(onTap = { currentOnSelectLang(lang) })
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lang.spaceLabel,
                        color = if (isCurrent) palette.accentText else palette.keyText,
                        fontFamily = KeskeFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCurrent) "✓" else lang.shortBadge,
                        color = if (isCurrent) palette.accentText else palette.hintText,
                        fontFamily = KeskeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (onShowImePicker != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = palette.hintText.copy(alpha = 0.25f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.specialKeyBg)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "Другая клавиатура"
                            onClick(label = "Другая клавиатура") {
                                currentOnShowImePicker?.invoke()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { currentOnShowImePicker?.invoke() })
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KeskeVectorIcon(
                        type = KeskeIconType.IME_SWITCHER,
                        tint = palette.accentBg,
                        size = 16.dp
                    )
                    Text(
                        text = "Другая клавиатура",
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
}
