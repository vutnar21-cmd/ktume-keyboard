package org.chuvash.keske.ime

import android.content.ClipboardManager
import android.content.Context
import android.graphics.CornerPathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.chuvash.keske.ui.theme.KeskeFontFamily
import org.chuvash.keske.ui.theme.KeskePalettes
import org.chuvash.keske.ui.theme.KeyboardPalette
import org.chuvash.keske.ui.theme.KeyboardPreferences
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * МОДУЛЬ: KeyboardScreen.kt
 * Визуальный интерфейс клавиатуры на Jetpack Compose — единственный экземпляр UI,
 * который используется и в системном IME (KeskeImeService), и в превью настроек.
 *
 * Режимы (KeyboardMode):
 * - LETTERS — буквенная раскладка активного языка (обычная сетка или паркетные соты);
 * - SYMBOLS_1 (?123) — цифры и частотные символы в духе Gboard (10 + 10 + [=<] 7 [⌫]);
 * - SYMBOLS_2 (=\<) — валюты, математика, типографика;
 * - NUMPAD (1234) — телефонная сетка 3 × 4 с колонкой ⌫ / − / + / ↵;
 * - EMOJI_PAGE — полноэкранная страница смайликов.
 *
 * Инварианты:
 * - Нижний защитный отступ по умолчанию 20 dp плюс системные insets;
 * - Соты: ширина считается из фактического числа колонок, поэтому ряды не выезжают за край;
 * - Клавиши имеют семантику (Role.Button + onClick) и не срабатывают на отменённый жест;
 * - Словарные подсказки в верхней панели не показываются — только частицы и аффиксы.
 */

private enum class KeyboardMode {
    LETTERS,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMPAD,
    EMOJI_PAGE
}

private enum class ShiftState {
    OFF,
    ONCE,
    CAPS_LOCK
}

private enum class ToolbarOverlay {
    NONE,
    CLIPBOARD
}

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

@Composable
fun KeyboardScreen(
    prefs: KeyboardPreferences,
    isFieldEmpty: Boolean = false,
    editorCurrentWord: String? = null,
    onUpdatePrefs: (KeyboardPreferences) -> Unit,
    onCommitText: (String) -> Unit,
    onReplaceCurrentWord: (String) -> Unit = { onCommitText("$it ") },
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onReplaceSpaceWithComma: () -> Boolean = {
        onBackspace()
        onCommitText(", ")
        true
    },
    onEnter: () -> Unit = { onCommitText("\n") },
    onMoveCursor: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val palette = remember(prefs.paletteId) { KeskePalettes.byId(prefs.paletteId) }

    val enabledLangs = remember(prefs.enabledLayouts) {
        KeyboardLang.entries.filter { prefs.enabledLayouts.contains(it.code) }
            .ifEmpty { listOf(KeyboardLang.CHUVASH) }
    }
    var currentLang by remember(enabledLangs) {
        mutableStateOf(enabledLangs.first())
    }
    if (currentLang !in enabledLangs) {
        currentLang = enabledLangs.first()
    }

    var shiftState by remember {
        mutableStateOf(if (prefs.autoCapitalize && isFieldEmpty) ShiftState.ONCE else ShiftState.OFF)
    }
    var lastShiftTapMs by remember { mutableLongStateOf(0L) }
    var lastSpaceTapMs by remember { mutableLongStateOf(0L) }
    var lastCommittedWasSpace by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.LETTERS) }
    var toolbarOverlay by remember { mutableStateOf(ToolbarOverlay.NONE) }

    // Автоматическая заглавная буква на пустом поле
    LaunchedEffect(isFieldEmpty, prefs.autoCapitalize) {
        if (prefs.autoCapitalize && isFieldEmpty && shiftState == ShiftState.OFF) {
            shiftState = ShiftState.ONCE
        }
    }

    var isSwapMode by remember { mutableStateOf(false) }
    var swapFirstPos by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val isShifted = shiftState != ShiftState.OFF

    val baseLangRows = prefs.customRowsByLang[currentLang]
        ?: KeyboardLayouts.getDefaultRows(currentLang)

    val activeRows: List<List<String>> = when (keyboardMode) {
        KeyboardMode.LETTERS -> if (prefs.hexagonalGrid) {
            KeyboardLayouts.getHex4RowLayout(baseLangRows)
        } else {
            baseLangRows
        }
        KeyboardMode.SYMBOLS_1 -> KeyboardLayouts.symbolsLayer1
        KeyboardMode.SYMBOLS_2 -> KeyboardLayouts.symbolsLayer2
        KeyboardMode.NUMPAD -> KeyboardLayouts.numpadRows
        KeyboardMode.EMOJI_PAGE -> emptyList()
    }

    val maxCols = remember(activeRows, prefs.showNumberRow, keyboardMode, prefs.hexagonalGrid) {
        when {
            keyboardMode == KeyboardMode.NUMPAD -> 4
            prefs.hexagonalGrid && keyboardMode == KeyboardMode.LETTERS -> {
                activeRows.mapIndexed { idx, r ->
                    if (idx == activeRows.lastIndex) r.size + 2 else r.size
                }.maxOrNull()?.coerceAtLeast(7) ?: 7
            }
            else -> {
                var cols = 9
                activeRows.forEachIndexed { idx, r ->
                    val needed = if (idx == activeRows.lastIndex) r.size + 2 else r.size
                    cols = max(cols, needed)
                }
                if (prefs.showNumberRow) {
                    cols = max(cols, 10)
                }
                cols
            }
        }
    }

    val cycleNextLang = {
        val idx = enabledLangs.indexOf(currentLang)
        currentLang = enabledLangs[(idx + 1) % enabledLangs.size]
        swapFirstPos = null
        lastCommittedWasSpace = false
    }

    fun updateCurrentLangRows(newRows: List<List<String>>) {
        val updatedMap = prefs.customRowsByLang.toMutableMap()
        updatedMap[currentLang] = newRows
        onUpdatePrefs(prefs.copy(customRowsByLang = updatedMap))
    }

    fun swapTwoKeys(posA: Pair<Int, Int>, posB: Pair<Int, Int>) {
        val mutableRows = activeRows.map { it.toMutableList() }.toMutableList()
        val charA = mutableRows.getOrNull(posA.first)?.getOrNull(posA.second) ?: return
        val charB = mutableRows.getOrNull(posB.first)?.getOrNull(posB.second) ?: return
        mutableRows[posA.first][posA.second] = charB
        mutableRows[posB.first][posB.second] = charA
        updateCurrentLangRows(mutableRows)
    }

    /**
     * Единственный путь ввода текста: сообщает наружу и обновляет признак «предыдущий ввод — пробел»
     * для двойного пробела. Текущее слово не дублируется локально — оно приходит из редактора
     * (editorCurrentWord), поэтому подсказки не расходятся с реальным содержимым поля.
     */
    fun commitTrackedText(text: String) {
        onCommitText(text)
        lastCommittedWasSpace = (text == " ")
    }

    val activeTypedWord = editorCurrentWord.orEmpty()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.boardBg)
            .navigationBarsPadding()
            .padding(
                start = 2.dp,
                end = 2.dp,
                top = 4.dp,
                bottom = prefs.bottomInsetDp.dp
            )
    ) {
        val boardMaxWidth = maxWidth
        val gap = prefs.keyGapDp.dp
        val uniformKeyWidth = (boardMaxWidth - gap * (maxCols - 1)) / maxCols

        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Чистая верхняя панель без словарных подсказок (с сингармоническими частицами/аффиксами + Буфер, Смайлы, Настройки)
            TopToolbarStrip(
                lang = currentLang,
                typedWord = activeTypedWord,
                prefs = prefs,
                palette = palette,
                isEmojiPage = keyboardMode == KeyboardMode.EMOJI_PAGE,
                toolbarOverlay = toolbarOverlay,
                onToggleClipboard = {
                    toolbarOverlay = if (toolbarOverlay == ToolbarOverlay.CLIPBOARD) {
                        ToolbarOverlay.NONE
                    } else {
                        ToolbarOverlay.CLIPBOARD
                    }
                },
                onToggleEmojiPage = {
                    keyboardMode = if (keyboardMode == KeyboardMode.EMOJI_PAGE) {
                        KeyboardMode.LETTERS
                    } else {
                        KeyboardMode.EMOJI_PAGE
                    }
                    toolbarOverlay = ToolbarOverlay.NONE
                },
                onSelectParticle = { particle ->
                    onReplaceCurrentWord(particle)
                    lastCommittedWasSpace = true
                },
                onOpenSettings = onOpenSettings
            )

            // Всплывающая полоса буфера обмена (с конвертером ăĕçÿ -> ӑӗҫӳ)
            if (toolbarOverlay == ToolbarOverlay.CLIPBOARD) {
                Spacer(modifier = Modifier.height(4.dp))
                ClipboardOverlayBar(
                    context = context,
                    palette = palette,
                    onCommit = { text ->
                        commitTrackedText(text)
                        toolbarOverlay = ToolbarOverlay.NONE
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            when (keyboardMode) {
                KeyboardMode.EMOJI_PAGE -> {
                    // Увеличенное полноразмерное окно выбора смайликов (как в Gboard)
                    EmojiPickerPage(
                        prefs = prefs,
                        palette = palette,
                        onCommitEmoji = { emoji -> commitTrackedText(emoji) },
                        onBackspace = {
                            onBackspace()
                            lastCommittedWasSpace = false
                        },
                        onDeleteWord = {
                            onDeleteWord()
                            lastCommittedWasSpace = false
                        },
                        onBackToLetters = { keyboardMode = KeyboardMode.LETTERS }
                    )
                }

                KeyboardMode.NUMPAD -> {
                    // Полноразмерный Numpad 1234 (телефонная сетка 3 × 4 с колонкой ⌫ / − / + / ↵)
                    NumpadGridView(
                        prefs = prefs,
                        palette = palette,
                        gap = gap,
                        onCommit = { commitTrackedText(it) },
                        onBackspace = {
                            onBackspace()
                            lastCommittedWasSpace = false
                        },
                        onDeleteWord = {
                            onDeleteWord()
                        },
                        onEnter = onEnter,
                        onBackToLetters = { keyboardMode = KeyboardMode.LETTERS },
                        onBackToSymbols = { keyboardMode = KeyboardMode.SYMBOLS_1 }
                    )
                }

                else -> {
                    if (prefs.hexagonalGrid && keyboardMode == KeyboardMode.LETTERS) {
                        // Исправленная паркетная 4-рядная сотовая сетка (7 + 7 + 7 + [Shift + 5 букв + ⌫])
                        HoneycombHexGrid(
                            rows = activeRows,
                            currentLang = currentLang,
                            isShifted = isShifted,
                            shiftState = shiftState,
                            prefs = prefs,
                            palette = palette,
                            maxWidth = boardMaxWidth,
                            onToggleShift = {
                                val now = System.currentTimeMillis()
                                shiftState = when {
                                    shiftState == ShiftState.CAPS_LOCK -> ShiftState.OFF
                                    shiftState == ShiftState.ONCE && (now - lastShiftTapMs) < 420L -> ShiftState.CAPS_LOCK
                                    shiftState == ShiftState.OFF -> ShiftState.ONCE
                                    else -> ShiftState.OFF
                                }
                                lastShiftTapMs = now
                            },
                            onCommit = { committed ->
                                commitTrackedText(committed)
                                if (shiftState == ShiftState.ONCE) {
                                    shiftState = ShiftState.OFF
                                }
                            },
                            onBackspace = {
                                onBackspace()
                                lastCommittedWasSpace = false
                            },
                            onDeleteWord = {
                                onDeleteWord()
                            }
                        )
                    } else {
                        // Общий верхний ряд цифр: одинаков на буквенной странице и на страницах
                        // символов, поэтому высота клавиатуры не меняется при переключении ?123 / =\<
                        if (prefs.showNumberRow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                KeyboardLayouts.numberRow.forEachIndexed { idx, num ->
                                    val numVariants = KeyboardLayouts.getLongPressVariants(currentLang, num, false)
                                    KeyButton(
                                        label = num,
                                        variants = numVariants,
                                        prefs = prefs,
                                        palette = palette,
                                        colFraction = idx.toFloat() / 9f,
                                        isSwapMode = false,
                                        isSelectedForSwap = false,
                                        useHexShape = false,
                                        modifier = Modifier.weight(1f),
                                        onSwapClick = {},
                                        onCommit = { commitTrackedText(it) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(gap))
                        }

                        // Основные 3 ряда букв (10 + 9 + 7 в Чӑвашла) без пустых боковых полей
                        activeRows.forEachIndexed { rowIndex, rowKeys ->
                            val isMiddleNineRow = (rowIndex == 1 && rowKeys.size == 9 && activeRows.first().size == 10)
                            val rowSidePad = if (isMiddleNineRow) (uniformKeyWidth * 0.42f) else 0.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = rowSidePad),
                                horizontalArrangement = Arrangement.spacedBy(gap),
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
                                            modifier = Modifier.weight(1.35f),
                                            onClick = {
                                                val now = System.currentTimeMillis()
                                                shiftState = when {
                                                    shiftState == ShiftState.CAPS_LOCK -> ShiftState.OFF
                                                    shiftState == ShiftState.ONCE && (now - lastShiftTapMs) < 420L -> ShiftState.CAPS_LOCK
                                                    shiftState == ShiftState.OFF -> ShiftState.ONCE
                                                    else -> ShiftState.OFF
                                                }
                                                lastShiftTapMs = now
                                            }
                                        )
                                    } else {
                                        SpecialKey(
                                            label = if (keyboardMode == KeyboardMode.SYMBOLS_1) "=\\<" else "?123",
                                            prefs = prefs,
                                            palette = palette,
                                            isActive = keyboardMode == KeyboardMode.SYMBOLS_2,
                                            modifier = Modifier.weight(1.35f),
                                            onClick = {
                                                keyboardMode = if (keyboardMode == KeyboardMode.SYMBOLS_1) {
                                                    KeyboardMode.SYMBOLS_2
                                                } else {
                                                    KeyboardMode.SYMBOLS_1
                                                }
                                            }
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
                                    val currentPos = rowIndex to colIndex
                                    val isSelected = (swapFirstPos == currentPos)

                                    KeyButton(
                                        label = displayChar,
                                        variants = variants,
                                        prefs = prefs,
                                        palette = palette,
                                        colFraction = colIndex.toFloat() / lastColIdx.toFloat(),
                                        isSwapMode = isSwapMode && keyboardMode == KeyboardMode.LETTERS,
                                        isSelectedForSwap = isSelected,
                                        useHexShape = false,
                                        modifier = Modifier.weight(1f),
                                        onSwapClick = {
                                            val first = swapFirstPos
                                            if (first == null) {
                                                swapFirstPos = currentPos
                                            } else if (first == currentPos) {
                                                swapFirstPos = null
                                            } else {
                                                swapTwoKeys(first, currentPos)
                                                swapFirstPos = null
                                            }
                                        },
                                        onCommit = { committed ->
                                            commitTrackedText(committed)
                                            if (shiftState == ShiftState.ONCE) {
                                                shiftState = ShiftState.OFF
                                            }
                                        }
                                    )
                                }

                                if (rowIndex == activeRows.lastIndex) {
                                    BackspaceKey(
                                        prefs = prefs,
                                        palette = palette,
                                        modifier = Modifier.weight(1.35f),
                                        onTapBackspace = {
                                            onBackspace()
                                            lastCommittedWasSpace = false
                                        },
                                        onSwipeDeleteWord = {
                                            onDeleteWord()
                                            lastCommittedWasSpace = false
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(gap))
                        }
                    }

                    // Нижний ряд: ?123/АБВ, Запятая, Глобус/1234, Пробел, Дефис "-", Точка "." (со свайпом влево в "-"), Ввод ↵
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpecialKey(
                            label = if (keyboardMode == KeyboardMode.LETTERS) "?123" else "АБВ",
                            prefs = prefs,
                            palette = palette,
                            isActive = keyboardMode != KeyboardMode.LETTERS,
                            modifier = Modifier.weight(1.25f),
                            onClick = {
                                keyboardMode = if (keyboardMode == KeyboardMode.LETTERS) {
                                    KeyboardMode.SYMBOLS_1
                                } else {
                                    KeyboardMode.LETTERS
                                }
                            }
                        )

                        KeyButton(
                            label = ",",
                            variants = KeyboardLayouts.getLongPressVariants(currentLang, ",", false),
                            prefs = prefs,
                            palette = palette,
                            colFraction = 0.2f,
                            isSwapMode = false,
                            isSelectedForSwap = false,
                            useHexShape = false,
                            modifier = Modifier.weight(0.9f),
                            onSwapClick = {},
                            onCommit = { commitTrackedText(it) }
                        )

                        if (keyboardMode == KeyboardMode.LETTERS) {
                            SpecialKey(
                                label = "",
                                iconType = KeskeIconType.GLOBE,
                                prefs = prefs,
                                palette = palette,
                                modifier = Modifier.weight(0.95f),
                                onClick = cycleNextLang
                            )
                        } else {
                            SpecialKey(
                                label = "1234",
                                prefs = prefs,
                                palette = palette,
                                isActive = false,
                                modifier = Modifier.weight(1.05f),
                                onClick = { keyboardMode = KeyboardMode.NUMPAD }
                            )
                        }

                        SpacebarKey(
                            lang = currentLang,
                            prefs = prefs,
                            palette = palette,
                            modifier = Modifier.weight(if (prefs.showHyphenKey) 3.4f else 4.2f),
                            onCommitSpace = {
                                val now = System.currentTimeMillis()
                                val replaced = if (prefs.doubleSpaceComma && lastCommittedWasSpace && (now - lastSpaceTapMs) < 420L) {
                                    onReplaceSpaceWithComma()
                                } else {
                                    false
                                }
                                if (replaced) {
                                    lastCommittedWasSpace = false
                                    lastSpaceTapMs = 0L
                                } else {
                                    commitTrackedText(" ")
                                    lastSpaceTapMs = now
                                }
                            },
                            onMoveCursor = { delta ->
                                lastCommittedWasSpace = false
                                onMoveCursor(delta)
                            }
                        )

                        if (prefs.showHyphenKey) {
                            KeyButton(
                                label = "-",
                                variants = KeyboardLayouts.getLongPressVariants(currentLang, "-", false),
                                prefs = prefs,
                                palette = palette,
                                colFraction = 0.72f,
                                isSwapMode = false,
                                isSelectedForSwap = false,
                                useHexShape = false,
                                modifier = Modifier.weight(0.85f),
                                onSwapClick = {},
                                onCommit = { commitTrackedText(it) }
                            )
                        }

                        KeyButton(
                            label = ".",
                            variants = KeyboardLayouts.getLongPressVariants(currentLang, ".", false),
                            prefs = prefs,
                            palette = palette,
                            colFraction = 0.85f,
                            isSwapMode = false,
                            isSelectedForSwap = false,
                            useHexShape = false,
                            swipeLeftCommit = "-",
                            modifier = Modifier.weight(0.9f),
                            onSwapClick = {},
                            onCommit = { committed ->
                                commitTrackedText(committed)
                                if (prefs.autoCapitalize && shiftState == ShiftState.OFF && (committed == "." || committed == "!" || committed == "?")) {
                                    shiftState = ShiftState.ONCE
                                }
                            }
                        )

                        SpecialKey(
                            label = "",
                            iconType = KeskeIconType.ENTER,
                            prefs = prefs,
                            palette = palette,
                            isAccent = true,
                            modifier = Modifier.weight(1.35f),
                            onClick = {
                                onEnter()
                                lastCommittedWasSpace = false
                                if (prefs.autoCapitalize && shiftState == ShiftState.OFF) {
                                    shiftState = ShiftState.ONCE
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

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
private fun HoneycombHexGrid(
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

/**
 * Верхняя панель над клавиатурой без словарных подсказок:
 * - Слева (если включены частицы в настройках) чипы чувашских частиц (-и?, -ши?, -ха, -ҫкӗ), иначе просто компактный индикатор раскладки;
 * - Справа 3 кнопки: Буфер обмена, Смайлики, Настройки.
 */
@Composable
private fun TopToolbarStrip(
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
private fun ClipboardOverlayBar(
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
            Text(
                text = if (clipText.isBlank()) "Буфер обмена пуст" else clipText.take(36),
                color = palette.keyText,
                fontFamily = KeskeFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (clipText.isNotBlank()) {
                MiniActionChip("Вставить", palette) {
                    // Конвертация «ложной латиницы» (ăĕçÿ → ӑӗҫӳ) выполняется неявно при вставке:
                    // отдельная кнопка для этого не нужна. Системный буфер не перезаписываем.
                    onCommit(KeyboardLayouts.normalizeChuvashUnicode(clipText))
                }
            }
        }
    }
}

/**
 * Увеличенная полноразмерная страница выбора смайликов (как в Gboard):
 * высота увеличена до 5.3 рядов клавиш (~255 dp), чтобы помещалось 4 полных ряда смайлов без обрезания.
 */
@Composable
private fun EmojiPickerPage(
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
            horizontalArrangement = Arrangement.spacedBy(prefs.keyGapDp.dp),
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

/**
 * Клавиша с выравниванием облачка долгого нажатия (Long-Press) на боковых клавишах
 * и скруглённой гексагональной отрисовкой (для режима сот).
 */
@Composable
private fun KeyButton(
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
    var showLongPressPopup by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember { mutableIntStateOf(0) }
    var isPressed by remember { mutableStateOf(false) }

    val displayedVariants = remember(variants) {
        KeyboardLayouts.getDisplayedLongPressVariants(variants)
    }
    val currentLabel by rememberUpdatedState(label)
    val currentVariants by rememberUpdatedState(displayedVariants)
    val currentSwapMode by rememberUpdatedState(isSwapMode)
    val currentLongPressMs by rememberUpdatedState(prefs.longPressDelayMs)
    val currentSwipeLeft by rememberUpdatedState(swipeLeftCommit)
    val currentOnSwapClick by rememberUpdatedState(onSwapClick)
    val currentOnCommit by rememberUpdatedState(onCommit)

    val density = LocalDensity.current
    val stepPx = with(density) { 34.dp.toPx() }
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
        if (prefs.showSubLabels && variants.isNotEmpty() && !isSwapMode && !useHexShape) {
            Text(
                text = variants.first(),
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
            val popupAlignment = when {
                colFraction < 0.24f -> Alignment.TopStart
                colFraction > 0.76f -> Alignment.TopEnd
                else -> Alignment.TopCenter
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
private fun BackspaceKey(
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
private fun SpecialKey(
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
private fun SpacebarKey(
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

@Composable
private fun NumpadGridView(
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
