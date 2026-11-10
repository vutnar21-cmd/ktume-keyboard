package org.chuvash.ktume.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.chuvash.ktume.data.EmojiCategory
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: EmojiPickerPage.kt — страница выбора смайликов с нулевым скачком высоты (`boardBodyHeightDp`).
 *
 * - Вкладки категорий (`🕒 😊 👍 ❤️ 🌿 ☕`) интегрированы прямо в верхнюю полосу `TopToolbarStrip` (`36 dp`),
 *   поэтому над смайликами нет второй шапки и помещаются полноценные 4 ряда по 8 смайликов.
 * - Категории переключаются как тапом по иконке в верхнем баре, так и горизонтальным свайпом (`HorizontalPager`)
 *   с независимым скроллом для каждой категории.
 * - Сетка чистая (без серых рамок вокруг каждого смайла), с мягкой подсветкой нажатия и всплывающей «каплей».
 * - Нижний ряд (`АБВ · Пробел · ⌫ · ↵`) совпадает по высоте с основным нижним рядом и содержит контекстный `Enter`.
 */
@Composable
internal fun EmojiPickerPage(
    categories: List<EmojiCategory>,
    selectedCategoryIdx: Int,
    scrollResetSignal: Int,
    boardBodyHeightDp: Float,
    currentLang: KeyboardLang,
    enterIconType: KesjeIconType,
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    onGestureTick: () -> Unit = {},
    onSelectCategoryIdx: (Int) -> Unit,
    onCommitEmoji: (String) -> Unit,
    onCommitSpace: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onEnter: () -> Unit,
    onBackToLetters: () -> Unit
) {
    val currentOnSelectCategoryIdx by rememberUpdatedState(onSelectCategoryIdx)
    val safeInitialPage = selectedCategoryIdx.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { categories.size }
    )

    // Синхронизация: тап по вкладке в TopToolbarStrip -> плавный переход пейджера
    LaunchedEffect(selectedCategoryIdx) {
        val target = selectedCategoryIdx.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Синхронизация: свайп пейджера влево/вправо -> обновление активной вкладки в TopToolbarStrip
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedCategoryIdx) {
            currentOnSelectCategoryIdx(pagerState.currentPage)
        }
    }

    val hPad = prefs.keyGapHDp.dp / 2
    val vPad = if (prefs.hexagonalGrid) 0.dp else (prefs.keyGapVDp.dp / 2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(boardBodyHeightDp.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIdx ->
            val category = categories.getOrElse(pageIdx) { categories.first() }
            val pageScrollState = rememberScrollState()

            LaunchedEffect(scrollResetSignal) {
                if (scrollResetSignal > 0 && pageIdx == selectedCategoryIdx && pageScrollState.value > 0) {
                    pageScrollState.animateScrollTo(0)
                }
            }

            val rowsOfEmojis = remember(category.emojis) { category.emojis.chunked(8) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .verticalScroll(pageScrollState),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rowsOfEmojis.forEach { emojiRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (colIdx in 0 until 8) {
                            val emoji = emojiRow.getOrNull(colIdx)
                            if (emoji != null) {
                                EmojiGridCell(
                                    emoji = emoji,
                                    showPopup = prefs.showKeyPressPopup,
                                    palette = palette,
                                    modifier = Modifier.weight(1f),
                                    onCommitEmoji = onCommitEmoji
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f).height(42.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpecialKey(
                label = "АБВ",
                prefs = prefs,
                palette = palette,
                isActive = false,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(1.25f),
                onClick = onBackToLetters
            )
            SpacebarKey(
                lang = currentLang,
                prefs = prefs,
                palette = palette,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(3.2f),
                onGestureTick = onGestureTick,
                onCommitSpace = onCommitSpace,
                onMoveCursor = onMoveCursor
            )
            BackspaceKey(
                prefs = prefs,
                palette = palette,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(1.3f),
                onTapBackspace = onBackspace,
                onSwipeDeleteWord = onDeleteWord
            )
            SpecialKey(
                label = "",
                iconType = enterIconType,
                prefs = prefs,
                palette = palette,
                isAccent = true,
                gapPadH = hPad,
                gapPadV = vPad,
                modifier = Modifier.weight(1.3f),
                onClick = onEnter
            )
        }
    }
}

@Composable
private fun EmojiGridCell(
    emoji: String,
    showPopup: Boolean,
    palette: KeyboardPalette,
    modifier: Modifier = Modifier,
    onCommitEmoji: (String) -> Unit
) {
    val currentOnCommitEmoji by rememberUpdatedState(onCommitEmoji)
    var isPressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .height(42.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPressed) palette.accentBg.copy(alpha = 0.24f) else Color.Transparent
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = emoji
                onClick(label = emoji) {
                    currentOnCommitEmoji(emoji)
                    true
                }
            }
            .pointerInput(emoji) {
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            val pressJob = launch {
                                delay(35L)
                                isPressed = true
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressJob.cancel()
                                isPressed = false
                            }
                        }
                    },
                    onTap = {
                        currentOnCommitEmoji(emoji)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 23.sp
        )

        if (showPopup && isPressed) {
            val popupOffsetY = with(density) { (-48).dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupOffsetY),
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 48.dp)
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.keyBg)
                        .border(1.dp, palette.accentBg.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 28.sp
                    )
                }
            }
        }
    }
}
