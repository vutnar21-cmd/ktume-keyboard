package org.chuvash.keske.ime

import org.chuvash.keske.data.*

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * МОДУЛЬ: KeskeImeService.kt
 * Системный сервис клавиатуры Android (InputMethodService).
 *
 * Связывает Jetpack Compose интерфейс клавиатуры с системой Android:
 * - Загружает и сохраняет настройки и кастомные ряды клавиш через PreferencesRepository (DataStore);
 * - Корректно удаляет символы, выделенный текст и слова;
 * - Обрабатывает умный Enter (Поиск / Отправить / Готово / Перенос строки);
 * - Обеспечивает тактильный виброотклик, звук нажатия, скрытие клавиатуры и выбор метода ввода.
 */
class KeskeImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        PreferencesRepository.ensureLoaded(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateEditorContextState()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateEditorContextState()
    }

    private fun updateEditorContextState() {
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val after = runCatching { ic?.getTextAfterCursor(64, 0)?.toString() }.getOrNull().orEmpty()
        PreferencesState.isFieldEmpty.value = KeyboardLayouts.isCursorOnEmptyLine(before, after)
        PreferencesState.currentWord.value = KeyboardLayouts.extractCurrentWordBeforeCursor(before)
    }

    /**
     * Эксперимент «автозаглавные имена собственные»: если перед курсором набрано имя
     * со строчной буквы, заменяет первую букву на заглавную. Вызывается перед вставкой
     * пробела, поэтому срабатывает ровно по завершении слова.
     */
    private fun capitalizeNameBeforeCursor(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val word = KeyboardLayouts.extractCurrentWordBeforeCursor(before)
        val fixed = ChuvashNames.capitalizeIfName(word) ?: return
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(fixed, 1)
    }

    private fun triggerFeedback(prefs: KeyboardPreferences) {
        if (prefs.vibrationEnabled) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
            }
        }
        if (prefs.soundEnabled) {
            try {
                val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
                if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

                // Громкость регулируется общим системным ползунком + ползунком в настройках клавиатуры
                val maxSys = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val curSys = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val sysRatio = (curSys.toFloat() / maxSys.toFloat()).coerceIn(0f, 1f)
                val userRatio = (prefs.soundVolumePercent / 100f).coerceIn(0f, 1f)
                val finalVol = (sysRatio * userRatio).coerceIn(0f, 1f)
                if (finalVol > 0.02f) {
                    am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, finalVol)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this)
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        composeView.setContent {
            val prefs by PreferencesState.preferences.collectAsState()
            val isFieldEmpty by PreferencesState.isFieldEmpty.collectAsState()
            val editorCurrentWord by PreferencesState.currentWord.collectAsState()
            KeyboardScreen(
                prefs = prefs,
                isFieldEmpty = isFieldEmpty,
                editorCurrentWord = editorCurrentWord,
                onUpdatePrefs = { updated ->
                    PreferencesRepository.update(this@KeskeImeService, updated)
                },
                onCommitText = { text ->
                    triggerFeedback(prefs)
                    val ic = currentInputConnection
                    // Эксперимент: имя, набранное со строчной, получает заглавную перед пробелом
                    if (ic != null && text == " " && prefs.experimentalCapitalizeNames) {
                        capitalizeNameBeforeCursor(ic)
                    }
                    ic?.commitText(text, 1)
                    updateEditorContextState()
                },
                onReplaceSpaceWithComma = {
                    val ic = currentInputConnection ?: return@KeyboardScreen false
                    val selected = ic.getSelectedText(0)
                    val before = ic.getTextBeforeCursor(4, 0)?.toString().orEmpty()
                    if (KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor(before, !selected.isNullOrEmpty())) {
                        triggerFeedback(prefs)
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(", ", 1)
                        updateEditorContextState()
                        true
                    } else {
                        false
                    }
                },
                onReplaceCurrentWord = { replacement ->
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                    val trimmed = before.trimEnd()
                    val trailingSpaces = before.length - trimmed.length
                    if (trailingSpaces > 0) ic.deleteSurroundingText(trailingSpaces, 0)
                    ic.commitText("$replacement ", 1)
                    updateEditorContextState()
                },
                onBackspace = {
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val selected = ic.getSelectedText(0)
                    if (!selected.isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        val before = ic.getTextBeforeCursor(16, 0)?.toString().orEmpty()
                        val deleteLen = KeyboardLayouts.computeLastGraphemeLength(before).coerceAtLeast(1)
                        ic.deleteSurroundingText(deleteLen, 0)
                    }
                    updateEditorContextState()
                },
                onDeleteWord = {
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                    val trimmed = before.trimEnd()
                    val lastSpace = trimmed.lastIndexOf(' ')
                    val charsToDelete = if (lastSpace == -1) before.length else (before.length - lastSpace - 1)
                    if (charsToDelete > 0) {
                        ic.deleteSurroundingText(charsToDelete, 0)
                    }
                    updateEditorContextState()
                },
                onEnter = {
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val info = currentInputEditorInfo
                    val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                    val noEnterAction = (info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) ?: 0) != 0
                    if (!noEnterAction && action in listOf(
                            EditorInfo.IME_ACTION_SEARCH,
                            EditorInfo.IME_ACTION_SEND,
                            EditorInfo.IME_ACTION_GO,
                            EditorInfo.IME_ACTION_DONE,
                            EditorInfo.IME_ACTION_NEXT
                        )
                    ) {
                        ic.performEditorAction(action)
                    } else {
                        ic.commitText("\n", 1)
                    }
                    updateEditorContextState()
                },
                onMoveCursor = { delta ->
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                    updateEditorContextState()
                },
                onOpenSettings = {
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            )
        }
        return composeView
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
