package org.chuvash.keske.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.isActive
import org.chuvash.keske.data.*
import org.chuvash.keske.ui.theme.KeskePalettes

/**
 * МОДУЛЬ: KeyboardScreen.kt — корневой экран клавиатуры.
 * Единственный экземпляр UI, общий для системного IME (KeskeImeService) и превью настроек.
 *
 * Держит состояние (язык, регистр Shift/autoShiftArmed, режим, оверлеи) и собирает экран из модулей:
 * - `Keys.kt` — KeyButton, BackspaceKey, SpecialKey, SpacebarKey;
 * - `HoneycombHexGrid.kt` — 4-рядные паркетные соты;
 * - `Toolbar.kt` — верхняя полоса частиц/аффиксов и буфер обмена;
 * - `EmojiPickerPage.kt`, `NumpadGridView.kt` — полноразмерные страницы смайликов и телефонной сетки 3 × 4.
 *
 * Режимы (KeyboardMode): LETTERS, SYMBOLS_1 (?123), SYMBOLS_2 (=\<), NUMPAD (1234), EMOJI_PAGE.
 * Инварианты: нижний отступ 20 dp + системные insets, общий цифровой ряд 1–0 на буквах и символах,
 * контекст слова и пустой строки только из редактора, семантика Role.Button и защита от ACTION_CANCEL.
 */

internal enum class KeyboardMode {
    LETTERS,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMPAD,
    EMOJI_PAGE
}

internal enum class ShiftState {
    OFF,
    ONCE,
    CAPS_LOCK
}

internal enum class ToolbarOverlay {
    NONE,
    CLIPBOARD
}

@Composable
fun KeyboardScreen(
    prefs: KeyboardPreferences,
    shouldAutoCapitalize: Boolean = false,
    inputSessionId: Int = 0,
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
        mutableStateOf(if (prefs.autoCapitalize && shouldAutoCapitalize) ShiftState.ONCE else ShiftState.OFF)
    }
    // Shift включён автоматически (а не нажатием пользователя) — только его можно снять автоматически
    var autoShiftArmed by remember { mutableStateOf(prefs.autoCapitalize && shouldAutoCapitalize) }
    var lastSeenSessionId by remember { mutableIntStateOf(inputSessionId) }
    var lastShiftTapMs by remember { mutableLongStateOf(0L) }
    var lastSpaceTapMs by remember { mutableLongStateOf(0L) }
    var lastCommittedWasSpace by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.LETTERS) }
    var toolbarOverlay by remember { mutableStateOf(ToolbarOverlay.NONE) }

    // Автозаглавная буква (как в Gboard): реактивно следует контексту редактора и EditorInfo.
    // При переключении на новое поле ввода сбрасывает незакреплённый Shift прошлого поля,
    // а при уходе курсора в строку с текстом или стирании пробела после точки снимает авто-Shift.
    LaunchedEffect(shouldAutoCapitalize, prefs.autoCapitalize, inputSessionId) {
        val sessionChanged = inputSessionId != lastSeenSessionId
        lastSeenSessionId = inputSessionId
        if (sessionChanged && shiftState != ShiftState.CAPS_LOCK) {
            shiftState = ShiftState.OFF
            autoShiftArmed = false
        }
        if (!prefs.autoCapitalize) {
            if (autoShiftArmed && shiftState == ShiftState.ONCE) {
                shiftState = ShiftState.OFF
                autoShiftArmed = false
            }
            return@LaunchedEffect
        }
        if (shouldAutoCapitalize) {
            if (shiftState == ShiftState.OFF) {
                shiftState = ShiftState.ONCE
                autoShiftArmed = true
            }
        } else if (autoShiftArmed && shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
            autoShiftArmed = false
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
        val hGap = prefs.keyGapHDp.dp
        val vGap = prefs.keyGapVDp.dp
        val uniformKeyWidth = (boardMaxWidth - hGap * (maxCols - 1)) / maxCols

        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Единая однорядная верхняя панель (36 dp без скачка высоты):
            // частицы/стрелки курсора/встроенная капсула буфера слева + [📋] [😊] [⚙️] справа
            TopToolbarStrip(
                context = context,
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
                onMoveCursor = { delta ->
                    lastCommittedWasSpace = false
                    onMoveCursor(delta)
                },
                onPasteText = { text ->
                    commitTrackedText(text)
                    toolbarOverlay = ToolbarOverlay.NONE
                },
                onOpenSettings = onOpenSettings
            )

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
                        hGap = hGap,
                        vGap = vGap,
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
                                autoShiftArmed = false
                            },
                            onCommit = { committed ->
                                commitTrackedText(committed)
                                if (shiftState == ShiftState.ONCE) {
                                    shiftState = ShiftState.OFF
                                    autoShiftArmed = false
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
                                horizontalArrangement = Arrangement.spacedBy(hGap)
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
                            Spacer(modifier = Modifier.height(vGap))
                        }

                        // Основные 3 ряда букв (10 + 9 + 7 в Чӑвашла) без пустых боковых полей
                        activeRows.forEachIndexed { rowIndex, rowKeys ->
                            val isMiddleNineRow = (rowIndex == 1 && rowKeys.size == 9 && activeRows.first().size == 10)
                            val rowSidePad = if (isMiddleNineRow) (uniformKeyWidth * 0.42f) else 0.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = rowSidePad),
                                horizontalArrangement = Arrangement.spacedBy(hGap),
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
                                                autoShiftArmed = false
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
                                                autoShiftArmed = false
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
                            Spacer(modifier = Modifier.height(vGap))
                        }
                    }

                    // Нижний ряд: ?123/АБВ, Запятая, Глобус/1234, Пробел, Дефис "-", Точка "." (со свайпом влево в "-"), Ввод ↵
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(hGap),
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
                            }
                        )
                    }
                }
            }
        }
    }
}
