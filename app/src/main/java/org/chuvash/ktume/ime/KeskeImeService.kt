package org.chuvash.ktume.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
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
 * - отслеживает контекст редактора (автозаглавная Gboard, контекстный `Enter`, авто-Numpad, поле пароля);
 * - удаляет целые графемные кластеры Unicode, выделенный текст и слова (не пробивая переводы строк `\n`),
 *   а в пустых полях и полях `TYPE_NULL` отправляет `KEYCODE_DEL` / `KEYCODE_ENTER`;
 * - запоминает выбранную раскладку и синхронизирует её с системным подтипом IME;
 * - обеспечивает тактильный виброотклик, микро-тик жестов, звук нажатия и выбор системного метода ввода.
 */
class KeskeImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /** Вид клавиатуры: через него идёт системный тактильный отклик (`performHapticFeedback`). */
    private var keyboardView: View? = null

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
        PreferencesState.isPasswordField.value = KeyboardLayouts.isPasswordInputType(inputType)

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
        PreferencesState.editorContextVersion.value++
    }

    /**
     * Поле без текстовой модели (`TYPE_NULL`: терминалы, эмуляторы консоли) понимает только нажатия клавиш,
     * поэтому `⌫` и перевод строки отправляются туда как `KeyEvent`.
     */
    private fun isRawKeyEditor(): Boolean = currentInputEditorInfo?.inputType == EditorInfo.TYPE_NULL

    /** Составная правка одним шагом: приложение не видит промежуточного текста, отмена откатывает её целиком. */
    private inline fun InputConnection.batchEdit(block: InputConnection.() -> Unit) {
        beginBatchEdit()
        try {
            block()
        } finally {
            endBatchEdit()
        }
    }

    /** Подтип IME, о переключении на который мы сами попросили систему (ждём его подтверждения). */
    private var pendingSubtypeLang: KeyboardLang? = null

    /** Выставляет системный подтип IME под выбранную раскладку (язык проверки орфографии и подсказок приложений). */
    private fun syncSystemSubtype(lang: KeyboardLang) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        try {
            val imi = imm.enabledInputMethodList.firstOrNull { it.packageName == packageName } ?: return
            val target = imm.getEnabledInputMethodSubtypeList(imi, true)
                .firstOrNull { KeyboardLang.fromSubtypeTag(it.languageTag) == lang } ?: return
            if (imm.currentInputMethodSubtype == target) return
            switchInputMethod(imi.id, target)
            pendingSubtypeLang = lang
        } catch (_: Exception) {
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val lang = KeyboardLang.fromSubtypeTag(newSubtype?.languageTag) ?: return
        val pending = pendingSubtypeLang
        if (pending != null) {
            // Ответ на наш собственный запрос: промежуточные подтипы при быстром переключении 🌐 пропускаем
            if (lang == pending) pendingSubtypeLang = null
            return
        }
        if (lang.code in PreferencesState.preferences.value.enabledLayouts) {
            PreferencesRepository.saveActiveLang(this, lang)
        }
    }

    /** Эксперимент «автозаглавные имена собственные»: перед вставкой пробела поднимает регистр имени из списка. */
    private fun capitalizeNameBeforeCursor(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val word = KeyboardLayouts.extractCurrentWordBeforeCursor(before)
        val fixed = ChuvashNames.capitalizeIfName(word) ?: return
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(fixed, 1)
    }

    /**
     * Системный отклик клавиатуры: учитывает настройки телефона (вибрация клавиатуры, сила отклика)
     * и не требует разрешения `VIBRATE`.
     */
    private fun triggerFeedback(prefs: KeyboardPreferences) {
        if (prefs.vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        playKeypressSound(cachedAudioManager, prefs)
    }

    /** Лёгкий тик для жестов (свайп вверх 20 dp, выбор варианта Long-Press, шаг курсора). */
    private fun triggerGestureTick(prefs: KeyboardPreferences) {
        if (prefs.vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
        keyboardView = composeView
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
            val editorContextVersion by PreferencesState.editorContextVersion.collectAsState()
            val enterKeyAction by PreferencesState.enterKeyAction.collectAsState()
            val preferNumericMode by PreferencesState.preferNumericMode.collectAsState()
            val isPasswordField by PreferencesState.isPasswordField.collectAsState()
            val activeLang by PreferencesState.activeLang.collectAsState()

            KeyboardScreen(
                prefs = prefs,
                shouldAutoCapitalize = shouldAutoCapitalize,
                inputSessionId = inputSessionId,
                editorContextVersion = editorContextVersion,
                enterKeyAction = enterKeyAction,
                preferNumericMode = preferNumericMode,
                isPasswordField = isPasswordField,
                activeLang = activeLang,
                onLangChange = { lang ->
                    PreferencesRepository.saveActiveLang(this, lang)
                    syncSystemSubtype(lang)
                },
                onCommitText = { text ->
                    triggerFeedback(prefs)
                    val ic = currentInputConnection
                    if (ic != null && text == " " && prefs.experimentalCapitalizeNames) {
                        ic.batchEdit {
                            capitalizeNameBeforeCursor(this)
                            commitText(text, 1)
                        }
                    } else {
                        ic?.commitText(text, 1)
                    }
                    updateEditorContextState()
                },
                onReplaceSpaceWithComma = {
                    val ic = currentInputConnection ?: return@KeyboardScreen false
                    val selected = ic.getSelectedText(0)
                    val before = ic.getTextBeforeCursor(4, 0)?.toString().orEmpty()
                    if (KeyboardLayouts.canReplaceSpaceWithCommaBeforeCursor(before, !selected.isNullOrEmpty())) {
                        triggerFeedback(prefs)
                        ic.batchEdit {
                            deleteSurroundingText(1, 0)
                            commitText(", ", 1)
                        }
                        updateEditorContextState()
                        true
                    } else {
                        false
                    }
                },
                onBackspace = {
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val selected = ic.getSelectedText(0)
                    val before = ic.getTextBeforeCursor(16, 0)?.toString()
                    when {
                        !selected.isNullOrEmpty() -> ic.commitText("", 1)
                        // В пустом поле приложение ждёт KEYCODE_DEL: ячейки SMS-кода, «чипы» получателей
                        isRawKeyEditor() || before.isNullOrEmpty() -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        else -> {
                            val deleteLen = KeyboardLayouts.computeLastGraphemeLength(before).coerceAtLeast(1)
                            ic.deleteSurroundingText(deleteLen, 0)
                        }
                    }
                    updateEditorContextState()
                },
                onDeleteWord = {
                    triggerFeedback(prefs)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    val selected = ic.getSelectedText(0)
                    val before = ic.getTextBeforeCursor(64, 0)?.toString()
                    when {
                        !selected.isNullOrEmpty() -> ic.commitText("", 1)
                        isRawKeyEditor() || before.isNullOrEmpty() -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        else -> {
                            val charsToDelete = KeyboardLayouts.computeDeleteWordLength(before)
                            if (charsToDelete > 0) {
                                ic.deleteSurroundingText(charsToDelete, 0)
                            }
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
                        EnterKeyAction.NEWLINE -> if (isRawKeyEditor()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                        } else {
                            ic.commitText("\n", 1)
                        }
                    }
                    updateEditorContextState()
                },
                onMoveCursor = { delta ->
                    if (currentInputConnection == null) return@KeyboardScreen
                    sendDownUpKeyEvents(if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
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
