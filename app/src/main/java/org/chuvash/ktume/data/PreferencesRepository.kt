package org.chuvash.ktume.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * МОДУЛЬ: PreferencesRepository.kt
 *
 * Единственное место, где настройки читаются и пишутся на диск (DataStore).
 * Не зависит от IME: состояние живёт в [PreferencesState], поэтому репозиторий
 * можно использовать и тестировать без Android-сервиса.
 */
object PreferencesState {
    /** Поток текущих настроек: его читают и сервис клавиатуры, и экран настроек. */
    val preferences = MutableStateFlow(KeyboardPreferences())

    /**
     * true, когда контекст текущего поля ввода и позиция курсора требуют автозаглавной буквы
     * (по образцу Gboard: по умолчанию false, включается только если поле запрашивает CAP_*).
     */
    val shouldAutoCapitalize = MutableStateFlow(false)

    /** Счётчик переключений в новое поле ввода (сбрасывает режимы и одноразовый Shift прошлого поля). */
    val inputSessionId = MutableStateFlow(0)

    /**
     * Счётчик обновлений контекста редактора (ввод, удаление, перемещение курсора). Позволяет повторно
     * взвести автозаглавную, когда [shouldAutoCapitalize] остаётся `true` (поля `CAP_CHARACTERS`).
     */
    val editorContextVersion = MutableStateFlow(0)

    /** true, если текущее поле — пароль или PIN: вставка из буфера идёт без нормализации символов. */
    val isPasswordField = MutableStateFlow(false)

    /** Последняя выбранная раскладка (переживает пересоздание клавиатуры и перезапуск процесса). */
    val activeLang = MutableStateFlow<KeyboardLang?>(null)

    /** Контекстное действие клавиши Enter текущего поля ввода (Поиск, Отправить, Готово, Далее, Перенос). */
    val enterKeyAction = MutableStateFlow(EnterKeyAction.NEWLINE)

    /** true, если текущее поле ввода числовое или телефонное (автоматически открывает Numpad 1234). */
    val preferNumericMode = MutableStateFlow(false)

    /** Личный словарь автозамены (нижний регистр): эти слова никогда не исправляются. Только на устройстве. */
    val personalWords = MutableStateFlow<Set<String>>(emptySet())
}

private val Context.keskeDataStore by preferencesDataStore(name = "keske_keyboard_prefs_v3")

object PreferencesRepository {
    private const val TAG = "KeskePrefs"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var isLoaded = false
    @Volatile
    private var hasPendingUserUpdate = false
    private val loadMutex = Any()

    private val _loaded = MutableStateFlow(false)

    /**
     * true, когда стартовое чтение DataStore завершилось (успешно или с ошибкой).
     * Экран настроек не должен принимать правки до этого момента: иначе снимок
     * из значений по умолчанию перезапишет сохранённые настройки и раскладки.
     */
    val loaded: StateFlow<Boolean> = _loaded

    private val KEY_PALETTE = stringPreferencesKey("palette_id_v5")
    private val KEY_CV_PRESET = stringPreferencesKey("cv_preset_v3")
    private val KEY_HEIGHT = floatPreferencesKey("key_height_dp_v6")
    private val KEY_RADIUS = floatPreferencesKey("key_radius_dp_v5")
    private val KEY_GAP_H = floatPreferencesKey("key_gap_h_dp_v6")
    private val KEY_GAP_V = floatPreferencesKey("key_gap_v_dp_v6")
    /** Старый единый ключ: читается как fallback, чтобы не потерять значение пользователя. */
    private val KEY_GAP_LEGACY = floatPreferencesKey("key_gap_dp_v5")
    private val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp_v5")
    private val KEY_BOTTOM_INSET = floatPreferencesKey("bottom_inset_dp_v5")
    private val KEY_SOUND_VOLUME = floatPreferencesKey("sound_volume_pct_v3")
    private val KEY_LONG_PRESS = intPreferencesKey("long_press_ms_v6")
    private val KEY_LAYOUTS = stringSetPreferencesKey("enabled_layouts")
    private val KEY_NUM_ROW = booleanPreferencesKey("show_num_row")
    private val KEY_SUB_LABELS = booleanPreferencesKey("show_sub_labels_v3")
    private val KEY_PRESS_POPUP = booleanPreferencesKey("show_key_press_popup_v1")
    private val KEY_COMPACT_LANDSCAPE = booleanPreferencesKey("compact_landscape_v1")
    private val KEY_HEX_GRID = booleanPreferencesKey("hex_grid_v3")
    private val KEY_SOUND = booleanPreferencesKey("sound_enabled_v3")
    private val KEY_VIBRATE = booleanPreferencesKey("vibration_enabled")
    private val KEY_AUTO_CAP = booleanPreferencesKey("auto_capitalize")
    private val KEY_SWIPE_UP_FIRST = booleanPreferencesKey("swipe_up_first_v1")
    private val KEY_DOUBLE_SPACE_COMMA = booleanPreferencesKey("double_space_comma")
    private val KEY_SHOW_HYPHEN = booleanPreferencesKey("show_hyphen_key_v2")
    private val KEY_CURSOR_ARROWS = booleanPreferencesKey("show_cursor_arrows_v1")
    private val KEY_EXP_CAP_NAMES = booleanPreferencesKey("exp_capitalize_names_v1")
    private val KEY_EXP_AUTOCORRECT = booleanPreferencesKey("exp_autocorrect_v1")
    private val KEY_AUTOCORRECT_LANGS = stringSetPreferencesKey("autocorrect_langs_v1")
    private val KEY_AUTOCORRECT_LEARN = booleanPreferencesKey("autocorrect_learn_v1")
    /** Личный словарь автозамены: пишется отдельно от [update] и не сбрасывается кнопкой «Сбросить». */
    private val KEY_PERSONAL_WORDS = stringSetPreferencesKey("personal_words_v1")
    private val KEY_RECENT_EMOJIS = stringPreferencesKey("recent_emojis_v1")
    /** Пишется отдельно от [update]: переключение языка не перезаписывает остальные настройки. */
    private val KEY_ACTIVE_LANG = stringPreferencesKey("active_lang_v1")

    private var pendingSaveJob: Job? = null
    private val saveMutex = Any()

    private fun rowsKey(lang: KeyboardLang) = stringPreferencesKey("custom_rows_v3_${lang.code}")

    private fun serializeRows(rows: List<List<String>>): String =
        rows.joinToString(separator = "\n") { it.joinToString(separator = "\t") }

    private fun deserializeRows(raw: String?, fallback: List<List<String>>): List<List<String>> {
        if (raw.isNullOrBlank()) return fallback
        val parsed = raw.split("\n").map { line ->
            if (line.isBlank()) emptyList() else line.split("\t").filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        return if (parsed.size in 3..4) parsed else fallback
    }

    private fun serializeRecentEmojis(emojis: List<String>): String =
        emojis.take(EmojiCatalog.MAX_RECENT_EMOJIS).joinToString(separator = "\t")

    private fun deserializeRecentEmojis(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("\t").map { it.trim() }.filter { it.isNotEmpty() }.take(EmojiCatalog.MAX_RECENT_EMOJIS)
    }

    fun ensureLoaded(context: Context) {
        synchronized(loadMutex) {
            if (isLoaded) return
            isLoaded = true
        }
        val appCtx = context.applicationContext
        scope.launch {
            try {
                val p = appCtx.keskeDataStore.data.first()
                val savedLang = KeyboardLang.entries.find { it.code == p[KEY_ACTIVE_LANG] }
                if (savedLang != null && PreferencesState.activeLang.value == null) {
                    PreferencesState.activeLang.value = savedLang
                }
                p[KEY_PERSONAL_WORDS]?.let { saved ->
                    PreferencesState.personalWords.value = saved + PreferencesState.personalWords.value
                }
                if (hasPendingUserUpdate) return@launch
                val presetId = p[KEY_CV_PRESET] ?: ChuvashLayoutPreset.BALANCED_26.id
                val preset = ChuvashLayoutPreset.entries.find { it.id == presetId } ?: ChuvashLayoutPreset.BALANCED_26
                val customMap = KeyboardLang.entries.associateWith { lang ->
                    val def = if (lang == KeyboardLang.CHUVASH) {
                        KeyboardLayouts.getDefaultRows(lang, preset)
                    } else {
                        KeyboardLayouts.getDefaultRows(lang)
                    }
                    deserializeRows(p[rowsKey(lang)], def)
                }
                val defaults = KeyboardPreferences()
                val loaded = KeyboardPreferences(
                    paletteId = p[KEY_PALETTE] ?: defaults.paletteId,
                    chuvashPreset = presetId,
                    keyHeightDp = p[KEY_HEIGHT] ?: defaults.keyHeightDp,
                    keyCornerRadiusDp = p[KEY_RADIUS] ?: defaults.keyCornerRadiusDp,
                    keyGapHDp = p[KEY_GAP_H] ?: p[KEY_GAP_LEGACY] ?: defaults.keyGapHDp,
                    keyGapVDp = p[KEY_GAP_V] ?: defaults.keyGapVDp,
                    fontSizeSp = p[KEY_FONT_SIZE] ?: defaults.fontSizeSp,
                    bottomInsetDp = p[KEY_BOTTOM_INSET] ?: defaults.bottomInsetDp,
                    soundVolumePercent = p[KEY_SOUND_VOLUME] ?: defaults.soundVolumePercent,
                    longPressDelayMs = p[KEY_LONG_PRESS] ?: defaults.longPressDelayMs,
                    enabledLayouts = p[KEY_LAYOUTS] ?: defaults.enabledLayouts,
                    showNumberRow = p[KEY_NUM_ROW] ?: defaults.showNumberRow,
                    showSubLabels = p[KEY_SUB_LABELS] ?: defaults.showSubLabels,
                    showKeyPressPopup = p[KEY_PRESS_POPUP] ?: defaults.showKeyPressPopup,
                    compactLandscape = p[KEY_COMPACT_LANDSCAPE] ?: defaults.compactLandscape,
                    hexagonalGrid = p[KEY_HEX_GRID] ?: defaults.hexagonalGrid,
                    soundEnabled = p[KEY_SOUND] ?: defaults.soundEnabled,
                    vibrationEnabled = p[KEY_VIBRATE] ?: defaults.vibrationEnabled,
                    autoCapitalize = p[KEY_AUTO_CAP] ?: defaults.autoCapitalize,
                    swipeUpFirstVariant = p[KEY_SWIPE_UP_FIRST] ?: defaults.swipeUpFirstVariant,
                    doubleSpaceComma = p[KEY_DOUBLE_SPACE_COMMA] ?: defaults.doubleSpaceComma,
                    showHyphenKey = p[KEY_SHOW_HYPHEN] ?: defaults.showHyphenKey,
                    showCursorArrows = p[KEY_CURSOR_ARROWS] ?: defaults.showCursorArrows,
                    experimentalCapitalizeNames = p[KEY_EXP_CAP_NAMES] ?: defaults.experimentalCapitalizeNames,
                    experimentalAutocorrect = p[KEY_EXP_AUTOCORRECT] ?: defaults.experimentalAutocorrect,
                    autocorrectLangs = p[KEY_AUTOCORRECT_LANGS] ?: defaults.autocorrectLangs,
                    autocorrectLearnWords = p[KEY_AUTOCORRECT_LEARN] ?: defaults.autocorrectLearnWords,
                    recentEmojis = deserializeRecentEmojis(p[KEY_RECENT_EMOJIS]),
                    customRowsByLang = customMap
                )
                if (!hasPendingUserUpdate) {
                    PreferencesState.preferences.value = loaded
                }
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось прочитать настройки, используются значения по умолчанию", e)
                synchronized(loadMutex) {
                    isLoaded = false
                }
            } finally {
                // Разблокируем UI гарантированно при любом исходе (включая ранний выход hasPendingUserUpdate).
                _loaded.value = true
            }
        }
    }

    /** Запоминает выбранную раскладку: сразу в [PreferencesState.activeLang], на диск — отдельным ключом. */
    fun saveActiveLang(context: Context, lang: KeyboardLang) {
        if (PreferencesState.activeLang.value == lang) return
        PreferencesState.activeLang.value = lang
        val appCtx = context.applicationContext
        scope.launch {
            try {
                appCtx.keskeDataStore.edit { p -> p[KEY_ACTIVE_LANG] = lang.code }
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось сохранить выбранную раскладку", e)
            }
        }
    }

    /** Добавляет слово (в нижнем регистре) в личный словарь автозамены. */
    fun addPersonalWord(context: Context, word: String) {
        val lower = word.trim().lowercase()
        if (lower.isEmpty() || lower in PreferencesState.personalWords.value) return
        PreferencesState.personalWords.value = PreferencesState.personalWords.value + lower
        editPersonalWords(context) { it + lower }
    }

    fun removePersonalWord(context: Context, word: String) {
        PreferencesState.personalWords.value = PreferencesState.personalWords.value - word
        editPersonalWords(context) { it - word }
    }

    fun clearPersonalWords(context: Context) {
        PreferencesState.personalWords.value = emptySet()
        editPersonalWords(context) { emptySet() }
    }

    private fun editPersonalWords(context: Context, transform: (Set<String>) -> Set<String>) {
        val appCtx = context.applicationContext
        scope.launch {
            try {
                appCtx.keskeDataStore.edit { p ->
                    p[KEY_PERSONAL_WORDS] = transform(p[KEY_PERSONAL_WORDS] ?: emptySet())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось сохранить личный словарь", e)
            }
        }
    }

    fun recordRecentEmoji(context: Context, emoji: String) {
        val current = PreferencesState.preferences.value
        val updatedRecents = EmojiCatalog.recordRecentEmoji(current.recentEmojis, emoji)
        if (updatedRecents != current.recentEmojis) {
            update(context, current.copy(recentEmojis = updatedRecents), debounceDisk = true)
        }
    }

    fun update(context: Context, updated: KeyboardPreferences, debounceDisk: Boolean = false) {
        hasPendingUserUpdate = true
        PreferencesState.preferences.value = updated
        val appCtx = context.applicationContext
        synchronized(saveMutex) {
            pendingSaveJob?.cancel()
            pendingSaveJob = scope.launch {
                if (debounceDisk) {
                    delay(220L)
                }
                val latest = PreferencesState.preferences.value
                try {
                    appCtx.keskeDataStore.edit { p ->
                        p[KEY_PALETTE] = latest.paletteId
                        p[KEY_CV_PRESET] = latest.chuvashPreset
                        p[KEY_HEIGHT] = latest.keyHeightDp
                        p[KEY_RADIUS] = latest.keyCornerRadiusDp
                        p[KEY_GAP_H] = latest.keyGapHDp
                        p[KEY_GAP_V] = latest.keyGapVDp
                        p[KEY_FONT_SIZE] = latest.fontSizeSp
                        p[KEY_BOTTOM_INSET] = latest.bottomInsetDp
                        p[KEY_SOUND_VOLUME] = latest.soundVolumePercent
                        p[KEY_LONG_PRESS] = latest.longPressDelayMs
                        p[KEY_LAYOUTS] = latest.enabledLayouts
                        p[KEY_NUM_ROW] = latest.showNumberRow
                        p[KEY_SUB_LABELS] = latest.showSubLabels
                        p[KEY_PRESS_POPUP] = latest.showKeyPressPopup
                        p[KEY_COMPACT_LANDSCAPE] = latest.compactLandscape
                        p[KEY_HEX_GRID] = latest.hexagonalGrid
                        p[KEY_SOUND] = latest.soundEnabled
                        p[KEY_VIBRATE] = latest.vibrationEnabled
                        p[KEY_AUTO_CAP] = latest.autoCapitalize
                        p[KEY_SWIPE_UP_FIRST] = latest.swipeUpFirstVariant
                        p[KEY_DOUBLE_SPACE_COMMA] = latest.doubleSpaceComma
                        p[KEY_SHOW_HYPHEN] = latest.showHyphenKey
                        p[KEY_CURSOR_ARROWS] = latest.showCursorArrows
                        p[KEY_EXP_CAP_NAMES] = latest.experimentalCapitalizeNames
                        p[KEY_EXP_AUTOCORRECT] = latest.experimentalAutocorrect
                        p[KEY_AUTOCORRECT_LANGS] = latest.autocorrectLangs
                        p[KEY_AUTOCORRECT_LEARN] = latest.autocorrectLearnWords
                        p[KEY_RECENT_EMOJIS] = serializeRecentEmojis(latest.recentEmojis)
                        latest.customRowsByLang.forEach { (lang, rows) ->
                            p[rowsKey(lang)] = serializeRows(rows)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось сохранить настройки клавиатуры", e)
                }
            }
        }
    }
}
