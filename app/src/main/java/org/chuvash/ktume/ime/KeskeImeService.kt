package org.chuvash.ktume.ime

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
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.chuvash.ktume.data.*

/**
 * Единое воспроизведение звука нажатия клавиши с учётом системной громкости `STREAM_MUSIC`,
 * режима звонка и пользовательского ползунка `soundVolumePercent` (общее для IME и превью настроек).
 */
internal fun playKeypressSound(audioManager: AudioManager?, prefs: KeyboardPreferences) {
    if (!prefs.soundEnabled) return
    val am = audioManager ?: return
    try {
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
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

/**
 * МОДУЛЬ: KeskeImeService.kt — системный сервис клавиатуры Android (`InputMethodService`).
 *
 * Связывает Jetpack Compose интерфейс клавиатуры с системой Android:
 * - читает настройки из [PreferencesRepository] (DataStore) и ждёт `loaded` перед первым кадром;
 * - отслеживает контекст редактора (автозаглавная Gboard, слово-основа, контекстный `Enter`, авто-Numpad);
 * - удаляет целые графемные кластеры Unicode, выделенный текст и слова (не пробивая переводы строк `\n`);
 * - обеспечивает тактильный виброотклик, микро-тик жестов, звук нажатия и выбор системного метода ввода.
 */
class KeskeImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val cachedVibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private val cachedAudioManager: AudioManager? by lazy {
        getSystemService(AUDIO_SERVICE) as? AudioManager
    }

    private var lastFieldSignature: String? = null

    override fun onCreate() {
        super.onCreate()
        PreferencesRepository.ensureLoaded(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    private fun computeFieldSignature(info: EditorInfo?): String {
        if (info == null) return ""
        return "${info.packageName}:${info.fieldId}:${info.inputType}:${info.imeOptions}"
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val sig = computeFieldSignature(attribute)
        val fieldChanged = sig != lastFieldSignature
        lastFieldSignature = sig
        updateEditorContextState(attribute)
        // В Jetpack Compose все поля одного экрана делят общий AndroidComposeView и переключаются
        // через restartInput (restarting = true), поэтому учитываем и смену сигнатуры EditorInfo.
        if (!restarting || fieldChanged) {
            PreferencesState.inputSessionId.value++
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val sig = computeFieldSignature(info)
        val fieldChanged = sig != lastFieldSignature
        lastFieldSignature = sig
        updateEditorContextState(info)
        if (!restarting || fieldChanged) {
            PreferencesState.inputSessionId.value++
        }
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

    private fun updateEditorContextState(explicitInfo: EditorInfo? = null) {
        val info = explicitInfo ?: currentInputEditorInfo
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val imeOptions = info?.imeOptions ?: EditorInfo.IME_NULL

        PreferencesState.preferNumericMode.value = KeyboardLayouts.prefersNumericPad(inputType)
        PreferencesState.enterKeyAction.value = KeyboardLayouts.resolveEnterKeyAction(imeOptions)

        val ic = currentInputConnection
        val capFlags = KeyboardLayouts.resolveEditorCapFlags(inputType)
        val beforeSeq = ic?.getTextBeforeCursor(64, 0)
        val afterSeq = runCatching { ic?.getTextAfterCursor(64, 0) }.getOrNull()
        val before = beforeSeq?.toString().orEmpty()
        val after = afterSeq?.toString().orEmpty()

        val autoCap = when {
            capFlags == 0 -> false
            beforeSeq != null -> KeyboardLayouts.shouldAutoCapitalize(before, after, capFlags)
            else -> runCatching { (ic?.getCursorCapsMode(capFlags) ?: 0) != 0 }.getOrDefault(false)
        }
        PreferencesState.shouldAutoCapitalize.value = autoCap
        PreferencesState.currentWord.value = KeyboardLayouts.extractMorphemeWordBeforeCursor(before)
    }

    /** Эксперимент «автозаглавные имена собственные»: перед вставкой пробела поднимает регистр имени из списка. */
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
                cachedVibrator?.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
            }
        }
        playKeypressSound(cachedAudioManager, prefs)
    }

    /** Короткий лёгкий вибро-тик для жестов (свайп вверх 20 dp, выбор варианта Long-Press, шаг курсора). */
    private fun triggerGestureTick(prefs: KeyboardPreferences) {
        if (!prefs.vibrationEnabled) return
        try {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(6L, 65)
            }
            cachedVibrator?.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        composeView.setContent {
            val prefsLoaded by PreferencesRepository.loaded.collectAsState()
            if (!prefsLoaded) return@setContent

            val prefs by PreferencesState.preferences.collectAsState()
            val shouldAutoCapitalize by PreferencesState.shouldAutoCapitalize.collectAsState()
            val inputSessionId by PreferencesState.inputSessionId.collectAsState()
            val editorCurrentWord by PreferencesState.currentWord.collectAsState()
            val enterKeyAction by PreferencesState.enterKeyAction.collectAsState()
            val preferNumericMode by PreferencesState.preferNumericMode.collectAsState()

            KeyboardScreen(
                prefs = prefs,
                shouldAutoCapitalize = shouldAutoCapitalize,
                inputSessionId = inputSessionId,
                editorCurrentWord = editorCurrentWord,
                enterKeyAction = enterKeyAction,
                preferNumericMode = preferNumericMode,
                onCommitText = { text ->
                    triggerFeedback(prefs)
                    val ic = currentInputConnection
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
                    val trailingHorizontalSpaces = KeyboardLayouts.computeTrailingHorizontalSpacesAfterWord(before)
                    if (trailingHorizontalSpaces > 0) {
                        ic.deleteSurroundingText(trailingHorizontalSpaces, 0)
                    }
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
                    val selected = ic.getSelectedText(0)
                    if (!selected.isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                        val charsToDelete = KeyboardLayouts.computeDeleteWordLength(before)
                        if (charsToDelete > 0) {
                            ic.deleteSurroundingText(charsToDelete, 0)
                        }
                    }
                    updateEditorContextState()
                },
                onEnter = {
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val imeOptions = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_NULL
                    when (KeyboardLayouts.resolveEnterKeyAction(imeOptions)) {
                        EnterKeyAction.SEARCH -> ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                        EnterKeyAction.SEND -> ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                        EnterKeyAction.GO -> ic.performEditorAction(EditorInfo.IME_ACTION_GO)
                        EnterKeyAction.NEXT -> ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
                        EnterKeyAction.DONE -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
                        EnterKeyAction.NEWLINE -> ic.commitText("\n", 1)
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
                onGestureTick = {
                    triggerGestureTick(prefs)
                },
                onShowImePicker = {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
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
