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
import androidx.compose.runtime.remember
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
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
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

    val fullText = testFieldValue.text
    val selStart = testFieldValue.selection.min.coerceIn(0, fullText.length)
    val selEnd = testFieldValue.selection.max.coerceIn(0, fullText.length)
    val beforeCursor = fullText.substring(0, selStart)
    val afterCursor = fullText.substring(selEnd)

    fun feedback() = playKeypressSound(audioManager, prefs)

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
            onCommitText = { committed ->
                feedback()
                val nextText = beforeCursor + committed + afterCursor
                val nextCursor = (beforeCursor.length + committed.length).coerceIn(0, nextText.length)
                onTestFieldChange(TextFieldValue(nextText, TextRange(nextCursor)))
            },
            onReplaceSpaceWithComma = {
                if (KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor(beforeCursor, selStart != selEnd)) {
                    feedback()
                    val trimmedBefore = beforeCursor.dropLast(1)
                    val nextText = "$trimmedBefore, $afterCursor"
                    val nextCursor = (trimmedBefore.length + 2).coerceIn(0, nextText.length)
                    onTestFieldChange(TextFieldValue(nextText, TextRange(nextCursor)))
                    true
                } else {
                    false
                }
            },
            onBackspace = {
                feedback()
                if (selStart != selEnd) {
                    onTestFieldChange(TextFieldValue(beforeCursor + afterCursor, TextRange(selStart)))
                } else if (beforeCursor.isNotEmpty()) {
                    val newBefore = KeyboardLayouts.dropLastGrapheme(beforeCursor)
                    onTestFieldChange(TextFieldValue(newBefore + afterCursor, TextRange(newBefore.length)))
                }
            },
            onDeleteWord = {
                feedback()
                if (selStart != selEnd) {
                    onTestFieldChange(TextFieldValue(beforeCursor + afterCursor, TextRange(selStart)))
                } else {
                    val deleteLen = KeyboardLayouts.computeDeleteWordLength(beforeCursor)
                    val newBefore = beforeCursor.dropLast(deleteLen)
                    onTestFieldChange(TextFieldValue(newBefore + afterCursor, TextRange(newBefore.length)))
                }
            },
            onEnter = {
                feedback()
                val nextText = "$beforeCursor\n$afterCursor"
                val nextCursor = beforeCursor.length + 1
                onTestFieldChange(TextFieldValue(nextText, TextRange(nextCursor)))
            },
            onMoveCursor = { delta ->
                val nextCursor = (selEnd + delta).coerceIn(0, fullText.length)
                onTestFieldChange(testFieldValue.copy(selection = TextRange(nextCursor)))
            },
            onShowImePicker = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            },
            onOpenSettings = {}
        )
    }
}
