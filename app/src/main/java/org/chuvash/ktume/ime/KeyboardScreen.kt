package org.chuvash.ktume.ime

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.max
import org.chuvash.ktume.data.EnterKeyAction
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.data.PreferencesRepository
import org.chuvash.ktume.ui.theme.KesjePalettes

/**
 * МОДУЛЬ: KeyboardScreen.kt — корневой экран клавиатуры.
 * Единственный экземпляр UI, общий для системного IME (`KesjeImeService`) и превью настроек.
 *
 * Держит состояние (язык, регистр `Shift`/`autoShiftArmed`, режим, оверлеи) и собирает экран из модулей:
 * - `KeyboardRows.kt` — цифровой ряд, 3 ряда букв/символов, нижний ряд и меню раскладок `🌐`;
 * - `Keys.kt`, `FunctionalKeys.kt` — клавиши и их жесты без мёртвых зазоров;
 * - `HoneycombHexGrid.kt` — 4-рядные паркетные соты;
 * - `Toolbar.kt` — однорядная верхняя полоса (`36 dp`);
 * - `EmojiPickerPage.kt`, `NumpadGridView.kt` — страницы смайликов и телефонной сетки `3 × 4`.
 */

internal const val DOUBLE_TAP_WINDOW_MS = 420L

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

/**
 * Единый переход состояния клавиши `Shift` по нажатию:
 * `OFF → ONCE`, двойной тап быстрее [DOUBLE_TAP_WINDOW_MS] `ONCE → CAPS_LOCK`, повторный тап `→ OFF`.
 */
internal fun nextShiftStateOnTap(
    current: ShiftState,
    nowMs: Long,
    lastTapMs: Long
): ShiftState = when {
    current == ShiftState.CAPS_LOCK -> ShiftState.OFF
    current == ShiftState.ONCE && (nowMs - lastTapMs) < DOUBLE_TAP_WINDOW_MS -> ShiftState.CAPS_LOCK
    current == ShiftState.OFF -> ShiftState.ONCE
    else -> ShiftState.OFF
}

/** Состояние `Shift` вместе с флагом «включён автозаглавной, а не пользователем». */
internal data class AutoShift(val shiftState: ShiftState, val armed: Boolean)

/**
 * Синхронизирует `Shift` с автозаглавной редактора при каждом обновлении его контекста:
 * взводит `ONCE`, если поле просит заглавную, а `Shift` выключен (в том числе повторно в полях
 * `CAP_CHARACTERS`), и снимает только тот `ONCE`, который включила сама автозаглавная.
 */
internal fun resolveAutoShift(
    current: AutoShift,
    autoCapitalizeEnabled: Boolean,
    shouldAutoCapitalize: Boolean
): AutoShift = when {
    autoCapitalizeEnabled && shouldAutoCapitalize ->
        if (current.shiftState == ShiftState.OFF) AutoShift(ShiftState.ONCE, armed = true) else current
    current.armed && current.shiftState == ShiftState.ONCE -> AutoShift(ShiftState.OFF, armed = false)
    else -> current
}

@Composable
fun KeyboardScreen(
    prefs: KeyboardPreferences,
    shouldAutoCapitalize: Boolean = false,
    inputSessionId: Int = 0,
    editorContextVersion: Int = 0,
    enterKeyAction: EnterKeyAction = EnterKeyAction.NEWLINE,
    preferNumericMode: Boolean = false,
    isPasswordField: Boolean = false,
    activeLang: KeyboardLang? = null,
    isPreview: Boolean = false,
    onLangChange: (KeyboardLang) -> Unit = {},
    onCommitText: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onReplaceSpaceWithComma: () -> Boolean = {
        onBackspace()
        onCommitText(", ")
        true
    },
    onEnter: () -> Unit = { onCommitText("\n") },
    onMoveCursor: (Int) -> Unit,
    onGestureTick: () -> Unit = {},
    onShowImePicker: (() -> Unit)? = null,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val geo = remember(prefs, isLandscape) {
        KeyboardLayouts.resolveEffectiveGeometry(prefs, isLandscape)
    }
    // Эффективные размеры для отрисовки (при повороте экрана не перезаписывают пользовательские настройки в DataStore)
    val renderPrefs = remember(prefs, geo, isPreview) {
        prefs.copy(
            keyHeightDp = geo.keyHeightDp,
            keyGapHDp = geo.keyGapHDp,
            keyGapVDp = geo.keyGapVDp,
            fontSizeSp = geo.fontSizeSp,
            bottomInsetDp = if (isPreview) 6f else geo.bottomInsetDp
        )
    }

    val palette = remember(prefs.paletteId) { KesjePalettes.byId(prefs.paletteId) }

    val enabledLangs = remember(prefs.enabledLayouts) {
        KeyboardLang.entries.filter { prefs.enabledLayouts.contains(it.code) }
            .ifEmpty { listOf(KeyboardLang.CHUVASH) }
    }
    var currentLang by remember(enabledLangs) {
        mutableStateOf(activeLang?.takeIf { it in enabledLangs } ?: enabledLangs.first())
    }
    if (currentLang !in enabledLangs) {
        currentLang = enabledLangs.first()
    }
    // Раскладка, выбранная извне: системный подтип IME или восстановление после пересоздания клавиатуры
    LaunchedEffect(activeLang) {
        if (activeLang != null && activeLang in enabledLangs) {
            currentLang = activeLang
        }
    }

    fun selectLang(lang: KeyboardLang) {
        currentLang = lang
        onLangChange(lang)
    }

    var shiftState by remember {
        mutableStateOf(if (prefs.autoCapitalize && shouldAutoCapitalize) ShiftState.ONCE else ShiftState.OFF)
    }
    var autoShiftArmed by remember { mutableStateOf(prefs.autoCapitalize && shouldAutoCapitalize) }
    var lastSeenSessionId by remember { mutableIntStateOf(inputSessionId) }
    var lastShiftTapMs by remember { mutableLongStateOf(0L) }
    var lastSpaceTapMs by remember { mutableLongStateOf(0L) }
    var lastCommittedWasSpace by remember { mutableStateOf(false) }
    var keyboardMode by remember {
        mutableStateOf(if (preferNumericMode) KeyboardMode.NUMPAD else KeyboardMode.LETTERS)
    }
    var toolbarOverlay by remember { mutableStateOf(ToolbarOverlay.NONE) }
    var showLangMenuPopup by remember { mutableStateOf(false) }
    var emojiCategoryIdx by remember { mutableIntStateOf(if (prefs.recentEmojis.isNotEmpty()) 0 else 1) }
    var emojiScrollResetSignal by remember { mutableIntStateOf(0) }
    var frozenRecentEmojis by remember { mutableStateOf(prefs.recentEmojis) }
    if (keyboardMode != KeyboardMode.EMOJI_PAGE || emojiCategoryIdx != 0) {
        frozenRecentEmojis = prefs.recentEmojis
    }
    val emojiCategories = remember(frozenRecentEmojis) {
        KeyboardLayouts.resolveEmojiCategories(frozenRecentEmojis)
    }

    // При переключении в новое поле ввода сбрасываем залипшие режимы, включаем Numpad для чисел/телефонов
    // и на каждое обновление контекста редактора синхронизируем автозаглавную.
    LaunchedEffect(shouldAutoCapitalize, prefs.autoCapitalize, inputSessionId, editorContextVersion, preferNumericMode) {
        val sessionChanged = inputSessionId != lastSeenSessionId
        lastSeenSessionId = inputSessionId
        if (sessionChanged) {
            keyboardMode = if (preferNumericMode) KeyboardMode.NUMPAD else KeyboardMode.LETTERS
            toolbarOverlay = ToolbarOverlay.NONE
            showLangMenuPopup = false
            lastCommittedWasSpace = false
            shiftState = ShiftState.OFF
            autoShiftArmed = false
        }
        val synced = resolveAutoShift(
            current = AutoShift(shiftState, autoShiftArmed),
            autoCapitalizeEnabled = prefs.autoCapitalize,
            shouldAutoCapitalize = shouldAutoCapitalize
        )
        shiftState = synced.shiftState
        autoShiftArmed = synced.armed
    }

    val isShifted = shiftState != ShiftState.OFF
    val enterIconType = remember(enterKeyAction) { enterKeyAction.toIconType() }
    val baseLangRows = prefs.customRowsByLang[currentLang] ?: KeyboardLayouts.getDefaultRows(currentLang)

    val activeRows: List<List<String>> = when (keyboardMode) {
        KeyboardMode.LETTERS -> if (prefs.hexagonalGrid) KeyboardLayouts.getHex4RowLayout(baseLangRows) else baseLangRows
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

    fun handleToggleShift() {
        showLangMenuPopup = false
        val now = System.currentTimeMillis()
        shiftState = nextShiftStateOnTap(shiftState, now, lastShiftTapMs)
        lastShiftTapMs = now
        autoShiftArmed = false
    }

    fun commitTrackedText(text: String) {
        showLangMenuPopup = false
        onCommitText(text)
        lastCommittedWasSpace = (text == " ")
    }

    fun commitCharacterAndConsumeOnceShift(committed: String) {
        commitTrackedText(committed)
        if (shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
            autoShiftArmed = false
        }
    }

    fun handleBackspaceTap() {
        showLangMenuPopup = false
        onBackspace()
        lastCommittedWasSpace = false
    }

    fun handleDeleteWordSwipe() {
        showLangMenuPopup = false
        onDeleteWord()
        lastCommittedWasSpace = false
    }

    fun handleCommitSpace() {
        showLangMenuPopup = false
        val now = System.currentTimeMillis()
        val replaced = if (renderPrefs.doubleSpaceComma && lastCommittedWasSpace && (now - lastSpaceTapMs) < DOUBLE_TAP_WINDOW_MS) {
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
    }

    fun handleEnterKey() {
        showLangMenuPopup = false
        onEnter()
        lastCommittedWasSpace = false
    }

    val boardBodyHeightDp = remember(renderPrefs, baseLangRows.size) {
        KeyboardLayouts.computeKeyboardBodyHeightDp(renderPrefs, baseLangRows.size)
    }

    val sidePad = (if (isPreview) 2f else geo.sidePaddingDp).dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.boardBg)
            .then(if (isPreview) Modifier else Modifier.navigationBarsPadding())
            .padding(
                start = sidePad,
                end = sidePad,
                top = 4.dp,
                bottom = renderPrefs.bottomInsetDp.dp
            )
    ) {
        val boardMaxWidth = maxWidth
        val hGap = renderPrefs.keyGapHDp.dp
        val vGap = renderPrefs.keyGapVDp.dp
        val hPad = hGap / 2
        val vPad = vGap / 2
        val uniformKeyPitch = boardMaxWidth / maxCols

        Column(modifier = Modifier.fillMaxWidth()) {
            TopToolbarStrip(
                context = context,
                palette = palette,
                isEmojiPage = keyboardMode == KeyboardMode.EMOJI_PAGE,
                isPasswordField = isPasswordField,
                emojiCategories = emojiCategories,
                selectedEmojiCategoryIdx = emojiCategoryIdx,
                toolbarOverlay = toolbarOverlay,
                onToggleClipboard = {
                    showLangMenuPopup = false
                    toolbarOverlay = if (toolbarOverlay == ToolbarOverlay.CLIPBOARD) {
                        ToolbarOverlay.NONE
                    } else {
                        ToolbarOverlay.CLIPBOARD
                    }
                },
                onToggleEmojiPage = {
                    showLangMenuPopup = false
                    if (keyboardMode == KeyboardMode.EMOJI_PAGE) {
                        keyboardMode = KeyboardMode.LETTERS
                    } else {
                        frozenRecentEmojis = prefs.recentEmojis
                        keyboardMode = KeyboardMode.EMOJI_PAGE
                    }
                    toolbarOverlay = ToolbarOverlay.NONE
                },
                onSelectEmojiCategory = { idx ->
                    showLangMenuPopup = false
                    if (idx == emojiCategoryIdx) {
                        if (idx == 0) {
                            frozenRecentEmojis = prefs.recentEmojis
                        }
                        emojiScrollResetSignal++
                    } else {
                        if (idx == 0) {
                            frozenRecentEmojis = prefs.recentEmojis
                        }
                        emojiCategoryIdx = idx
                    }
                },
                onMoveCursor = { delta ->
                    showLangMenuPopup = false
                    lastCommittedWasSpace = false
                    onMoveCursor(delta)
                },
                onPasteText = { text ->
                    commitTrackedText(text)
                    toolbarOverlay = ToolbarOverlay.NONE
                },
                onOpenSettings = {
                    showLangMenuPopup = false
                    onOpenSettings()
                }
            )

            Spacer(modifier = Modifier.height(2.dp))

            when (keyboardMode) {
                KeyboardMode.EMOJI_PAGE -> {
                    EmojiPickerPage(
                        categories = emojiCategories,
                        selectedCategoryIdx = emojiCategoryIdx,
                        scrollResetSignal = emojiScrollResetSignal,
                        boardBodyHeightDp = boardBodyHeightDp,
                        currentLang = currentLang,
                        enterIconType = enterIconType,
                        prefs = renderPrefs,
                        palette = palette,
                        onGestureTick = onGestureTick,
                        onSelectCategoryIdx = { idx ->
                            if (idx == 0 && emojiCategoryIdx != 0) {
                                frozenRecentEmojis = prefs.recentEmojis
                            }
                            emojiCategoryIdx = idx
                        },
                        onCommitEmoji = { emoji ->
                            commitTrackedText(emoji)
                            PreferencesRepository.recordRecentEmoji(context, emoji)
                        },
                        onCommitSpace = ::handleCommitSpace,
                        onMoveCursor = { delta ->
                            showLangMenuPopup = false
                            lastCommittedWasSpace = false
                            onMoveCursor(delta)
                        },
                        onBackspace = ::handleBackspaceTap,
                        onDeleteWord = ::handleDeleteWordSwipe,
                        onEnter = ::handleEnterKey,
                        onBackToLetters = { keyboardMode = KeyboardMode.LETTERS }
                    )
                }

                KeyboardMode.NUMPAD -> {
                    NumpadGridView(
                        prefs = renderPrefs,
                        palette = palette,
                        hGap = hGap,
                        vGap = vGap,
                        enterIconType = enterIconType,
                        onCommit = ::commitTrackedText,
                        onBackspace = ::handleBackspaceTap,
                        onDeleteWord = ::handleDeleteWordSwipe,
                        onEnter = ::handleEnterKey,
                        onBackToLetters = { keyboardMode = KeyboardMode.LETTERS },
                        onBackToSymbols = { keyboardMode = KeyboardMode.SYMBOLS_1 }
                    )
                }

                else -> {
                    val isHexLetters = renderPrefs.hexagonalGrid && keyboardMode == KeyboardMode.LETTERS
                    if (isHexLetters) {
                        HoneycombHexGrid(
                            rows = activeRows,
                            currentLang = currentLang,
                            isShifted = isShifted,
                            shiftState = shiftState,
                            prefs = renderPrefs,
                            palette = palette,
                            maxWidth = boardMaxWidth,
                            onToggleShift = ::handleToggleShift,
                            onGestureTick = onGestureTick,
                            onCommit = ::commitCharacterAndConsumeOnceShift,
                            onBackspace = ::handleBackspaceTap,
                            onDeleteWord = ::handleDeleteWordSwipe
                        )
                    } else {
                        if (renderPrefs.showNumberRow) {
                            NumberRowStrip(
                                currentLang = currentLang,
                                prefs = renderPrefs,
                                palette = palette,
                                hPad = hPad,
                                vPad = vPad,
                                onGestureTick = onGestureTick,
                                onCommit = ::commitTrackedText
                            )
                        }

                        AlphaSymbolRows(
                            activeRows = activeRows,
                            currentLang = currentLang,
                            keyboardMode = keyboardMode,
                            shiftState = shiftState,
                            isShifted = isShifted,
                            uniformKeyPitch = uniformKeyPitch,
                            prefs = renderPrefs,
                            palette = palette,
                            hPad = hPad,
                            vPad = vPad,
                            onToggleShift = ::handleToggleShift,
                            onToggleSymbolSubpage = {
                                showLangMenuPopup = false
                                keyboardMode = if (keyboardMode == KeyboardMode.SYMBOLS_1) {
                                    KeyboardMode.SYMBOLS_2
                                } else {
                                    KeyboardMode.SYMBOLS_1
                                }
                            },
                            onGestureTick = onGestureTick,
                            onCommitChar = ::commitCharacterAndConsumeOnceShift,
                            onBackspace = ::handleBackspaceTap,
                            onDeleteWord = ::handleDeleteWordSwipe
                        )
                    }

                    KeyboardBottomBar(
                        keyboardMode = keyboardMode,
                        enabledLangs = enabledLangs,
                        currentLang = currentLang,
                        showLangMenuPopup = showLangMenuPopup,
                        enterIconType = enterIconType,
                        prefs = renderPrefs,
                        palette = palette,
                        padH = hPad,
                        padV = if (isHexLetters) 0.dp else vPad,
                        onToggleSymbolsMode = {
                            showLangMenuPopup = false
                            keyboardMode = if (keyboardMode == KeyboardMode.LETTERS) {
                                KeyboardMode.SYMBOLS_1
                            } else {
                                KeyboardMode.LETTERS
                            }
                        },
                        onOpenNumpad = {
                            showLangMenuPopup = false
                            keyboardMode = KeyboardMode.NUMPAD
                        },
                        onGlobeClick = {
                            if (showLangMenuPopup) {
                                showLangMenuPopup = false
                            } else {
                                val idx = enabledLangs.indexOf(currentLang)
                                selectLang(enabledLangs[(idx + 1) % enabledLangs.size])
                                lastCommittedWasSpace = false
                                showLangMenuPopup = false
                            }
                        },
                        onGlobeLongClick = {
                            onGestureTick()
                            showLangMenuPopup = !showLangMenuPopup
                        },
                        onSelectLang = { chosen ->
                            selectLang(chosen)
                            lastCommittedWasSpace = false
                            showLangMenuPopup = false
                        },
                        onDismissLangMenu = { showLangMenuPopup = false },
                        onShowImePicker = onShowImePicker?.let { picker ->
                            {
                                showLangMenuPopup = false
                                picker()
                            }
                        },
                        onGestureTick = onGestureTick,
                        onCommitSpace = ::handleCommitSpace,
                        onMoveCursor = { delta ->
                            showLangMenuPopup = false
                            lastCommittedWasSpace = false
                            onMoveCursor(delta)
                        },
                        onCommitText = ::commitTrackedText,
                        onEnter = ::handleEnterKey
                    )
                }
            }
        }
    }
}
