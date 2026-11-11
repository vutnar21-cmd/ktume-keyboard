package org.chuvash.ktume.ime

import android.content.Context
import android.media.AudioManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.awaitCancellation
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.data.PreferencesRepository
import org.chuvash.ktume.data.autocorrect.AutocorrectEngine
import org.chuvash.ktume.data.autocorrect.AutocorrectSession
import org.chuvash.ktume.ui.theme.KesjeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: SettingsPreviewBench.kt — живой стенд проверки в студии настроек:
 * - [SettingsTestFieldCard] — тестовое поле с перехватом системного IME (`InterceptPlatformTextInput`);
 * - [SettingsKeyboardPreview] — закреплённый снизу экземпляр [KeyboardScreen], печатающий напрямую в тестовое поле.
 */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsTestFieldCard(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    palette: KeyboardPalette
) {
    SettingsSectionCard(
        title = "Тестовое поле",
        palette = palette
    ) {
        InterceptPlatformTextInput(
            interceptor = { _, _ -> awaitCancellation() }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Text(
                        "Тестовое поле",
                        fontFamily = KesjeFontFamily,
                        fontSize = 12.sp
                    )
                },
                placeholder = {
                    Text(
                        "Начните печатать (с заглавной буквы)...",
                        fontFamily = KesjeFontFamily,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = KesjeFontFamily)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
internal fun SettingsKeyboardPreview(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    testFieldValue: TextFieldValue,
    onTestFieldChange: (TextFieldValue) -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val latestPrefs by rememberUpdatedState(prefs)
    val latestFieldValue by rememberUpdatedState(testFieldValue)
    val latestOnFieldChange by rememberUpdatedState(onTestFieldChange)

    val fullText = testFieldValue.text
    val selStart = testFieldValue.selection.min.coerceIn(0, fullText.length)
    val selEnd = testFieldValue.selection.max.coerceIn(0, fullText.length)
    val beforeCursor = fullText.substring(0, selStart)
    val afterCursor = fullText.substring(selEnd)

    fun currentSlice(): Triple<String, String, Boolean> {
        val current = latestFieldValue
        val txt = current.text
        val minSel = current.selection.min.coerceIn(0, txt.length)
        val maxSel = current.selection.max.coerceIn(0, txt.length)
        return Triple(txt.substring(0, minSel), txt.substring(maxSel), minSel != maxSel)
    }

    fun feedback() = playKeypressSound(audioManager, latestPrefs)

    // Автозамена в тестовом поле работает идентично сервису клавиатуры (включая имена и Enter)
    val autocorrect = remember { AutocorrectSession() }
    // Раскладка стенда живёт внутри KeyboardScreen; запоминаем её, чтобы автозамена знала язык
    var previewLang by remember { mutableStateOf<KeyboardLang?>(null) }
    LaunchedEffect(prefs.autocorrectEnabled, prefs.autocorrectLangs) {
        AutocorrectEngine.sync(context, prefs)
    }

    fun resolvePreviewLang(): KeyboardLang? =
        previewLang?.takeIf { it.code in latestPrefs.enabledLayouts }
            ?: KeyboardLang.entries.firstOrNull { it.code in latestPrefs.enabledLayouts }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, palette.hintText.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        KeyboardScreen(
            prefs = prefs,
            isPreview = true,
            shouldAutoCapitalize = KeyboardLayouts.shouldAutoCapitalize(
                beforeCursor = beforeCursor,
                afterCursor = afterCursor,
                editorCapFlags = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
            ),
            onLangChange = { lang ->
                previewLang = lang
                autocorrect.cancelPending()
            },
            onCommitText = { committed ->
                feedback()
                val (curBefore, curAfter, hasSelection) = currentSlice()
                val correction = if (!hasSelection) {
                    AutocorrectEngine.findCorrection(autocorrect, latestPrefs, curBefore, committed, resolvePreviewLang())
                } else {
                    autocorrect.cancelPending()
                    null
                }
                val fixedBefore = if (correction != null) {
                    curBefore.dropLast(correction.original.length) + correction.replacement
                } else {
                    curBefore
                }
                val nextText = fixedBefore + committed + curAfter
                val nextCursor = (fixedBefore.length + committed.length).coerceIn(0, nextText.length)
                latestOnFieldChange(TextFieldValue(nextText, TextRange(nextCursor)))
                if (correction != null) autocorrect.onCorrectionApplied(correction, committed)
            },
            onReplaceSpaceWithComma = {
                autocorrect.cancelPending()
                val (curBefore, curAfter, hasSelection) = currentSlice()
                if (KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor(curBefore, hasSelection)) {
                    feedback()
                    val trimmedBefore = curBefore.dropLast(1)
                    val nextText = "$trimmedBefore, $curAfter"
                    val nextCursor = (trimmedBefore.length + 2).coerceIn(0, nextText.length)
                    latestOnFieldChange(TextFieldValue(nextText, TextRange(nextCursor)))
                    true
                } else {
                    false
                }
            },
            onBackspace = {
                feedback()
                val (curBefore, curAfter, hasSelection) = currentSlice()
                val revert = if (!hasSelection) autocorrect.takeRevert(curBefore) else null
                if (revert != null) {
                    // Первый ⌫ после автозамены возвращает набранное слово
                    val newBefore = curBefore.dropLast(revert.committedTail.length) + revert.original + revert.separator
                    latestOnFieldChange(TextFieldValue(newBefore + curAfter, TextRange(newBefore.length)))
                    if (latestPrefs.autocorrectLearnWords) PreferencesRepository.addPersonalWord(context, revert.original)
                } else if (hasSelection) {
                    autocorrect.cancelPending()
                    latestOnFieldChange(TextFieldValue(curBefore + curAfter, TextRange(curBefore.length)))
                } else if (curBefore.isNotEmpty()) {
                    val newBefore = KeyboardLayouts.dropLastGrapheme(curBefore)
                    latestOnFieldChange(TextFieldValue(newBefore + curAfter, TextRange(newBefore.length)))
                }
            },
            onDeleteWord = {
                feedback()
                autocorrect.cancelPending()
                val (curBefore, curAfter, hasSelection) = currentSlice()
                if (hasSelection) {
                    latestOnFieldChange(TextFieldValue(curBefore + curAfter, TextRange(curBefore.length)))
                } else {
                    val deleteLen = KeyboardLayouts.computeDeleteWordLength(curBefore)
                    val newBefore = curBefore.dropLast(deleteLen)
                    latestOnFieldChange(TextFieldValue(newBefore + curAfter, TextRange(newBefore.length)))
                }
            },
            onEnter = {
                feedback()
                val (curBefore, curAfter, hasSelection) = currentSlice()
                val correction = if (!hasSelection) {
                    AutocorrectEngine.findCorrection(autocorrect, latestPrefs, curBefore, "\n", resolvePreviewLang())
                } else {
                    autocorrect.cancelPending()
                    null
                }
                val fixedBefore = if (correction != null) {
                    curBefore.dropLast(correction.original.length) + correction.replacement
                } else {
                    curBefore
                }
                val nextText = "$fixedBefore\n$curAfter"
                val nextCursor = (fixedBefore.length + 1).coerceIn(0, nextText.length)
                latestOnFieldChange(TextFieldValue(nextText, TextRange(nextCursor)))
                if (correction != null) autocorrect.onCorrectionApplied(correction, "\n")
            },
            onMoveCursor = { delta ->
                autocorrect.cancelPending()
                val current = latestFieldValue
                val nextCursor = (current.selection.max + delta).coerceIn(0, current.text.length)
                latestOnFieldChange(current.copy(selection = TextRange(nextCursor)))
            },
            onShowImePicker = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            },
            onOpenSettings = {}
        )
    }
}
