package org.chuvash.keske.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** Счётчик переключений в новое поле ввода (сбрасывает одноразовый Shift прошлого поля). */
    val inputSessionId = MutableStateFlow(0)

    /** Текущее набираемое слово перед курсором (для ленты аффиксов). */
    val currentWord = MutableStateFlow("")
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
    private val KEY_HEIGHT = floatPreferencesKey("key_height_dp_v5")
    private val KEY_RADIUS = floatPreferencesKey("key_radius_dp_v5")
    private val KEY_GAP_H = floatPreferencesKey("key_gap_h_dp_v6")
    private val KEY_GAP_V = floatPreferencesKey("key_gap_v_dp_v6")
    /** Старый единый ключ: читается как fallback, чтобы не потерять значение пользователя. */
    private val KEY_GAP_LEGACY = floatPreferencesKey("key_gap_dp_v5")
    private val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp_v5")
    private val KEY_BOTTOM_INSET = floatPreferencesKey("bottom_inset_dp_v5")
    private val KEY_SOUND_VOLUME = floatPreferencesKey("sound_volume_pct_v3")
    private val KEY_LONG_PRESS = intPreferencesKey("long_press_ms_v5")
    private val KEY_LAYOUTS = stringSetPreferencesKey("enabled_layouts")
    private val KEY_NUM_ROW = booleanPreferencesKey("show_num_row")
    private val KEY_SUB_LABELS = booleanPreferencesKey("show_sub_labels_v3")
    private val KEY_HEX_GRID = booleanPreferencesKey("hex_grid_v3")
    private val KEY_SOUND = booleanPreferencesKey("sound_enabled_v3")
    private val KEY_VIBRATE = booleanPreferencesKey("vibration_enabled")
    private val KEY_AUTO_CAP = booleanPreferencesKey("auto_capitalize")
    private val KEY_SWIPE_UP_FIRST = booleanPreferencesKey("swipe_up_first_v1")
    private val KEY_DOUBLE_SPACE_COMMA = booleanPreferencesKey("double_space_comma")
    private val KEY_SHOW_HYPHEN = booleanPreferencesKey("show_hyphen_key")
    private val KEY_PARTICLES = booleanPreferencesKey("show_particles_v5")
    private val KEY_EXP_CAP_NAMES = booleanPreferencesKey("exp_capitalize_names_v1")

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

    fun ensureLoaded(context: Context) {
        synchronized(loadMutex) {
            if (isLoaded) return
            isLoaded = true
        }
        val appCtx = context.applicationContext
        scope.launch {
            try {
                val p = appCtx.keskeDataStore.data.first()
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
                val loaded = KeyboardPreferences(
                    paletteId = p[KEY_PALETTE] ?: "obsidian_forest",
                    chuvashPreset = presetId,
                    keyHeightDp = p[KEY_HEIGHT] ?: 48f,
                    keyCornerRadiusDp = p[KEY_RADIUS] ?: 14f,
                    keyGapHDp = p[KEY_GAP_H] ?: p[KEY_GAP_LEGACY] ?: 3f,
                    keyGapVDp = p[KEY_GAP_V] ?: 7f,
                    fontSizeSp = p[KEY_FONT_SIZE] ?: 26f,
                    bottomInsetDp = p[KEY_BOTTOM_INSET] ?: 20f,
                    soundVolumePercent = p[KEY_SOUND_VOLUME] ?: 70f,
                    longPressDelayMs = p[KEY_LONG_PRESS] ?: 210,
                    enabledLayouts = p[KEY_LAYOUTS] ?: setOf("cv", "ru", "lat"),
                    showNumberRow = p[KEY_NUM_ROW] ?: true,
                    showSubLabels = p[KEY_SUB_LABELS] ?: false,
                    hexagonalGrid = p[KEY_HEX_GRID] ?: false,
                    soundEnabled = p[KEY_SOUND] ?: true,
                    vibrationEnabled = p[KEY_VIBRATE] ?: true,
                    autoCapitalize = p[KEY_AUTO_CAP] ?: true,
                    swipeUpFirstVariant = p[KEY_SWIPE_UP_FIRST] ?: true,
                    doubleSpaceComma = p[KEY_DOUBLE_SPACE_COMMA] ?: true,
                    showHyphenKey = p[KEY_SHOW_HYPHEN] ?: true,
                    showParticlesStrip = p[KEY_PARTICLES] ?: true,
                    experimentalCapitalizeNames = p[KEY_EXP_CAP_NAMES] ?: false,
                    customRowsByLang = customMap
                )
                if (!hasPendingUserUpdate) {
                    PreferencesState.preferences.value = loaded
                }
                _loaded.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось прочитать настройки, используются значения по умолчанию", e)
                synchronized(loadMutex) {
                    isLoaded = false
                }
                // Экран всё равно разблокируем, иначе он навсегда останется в индикаторе загрузки.
                _loaded.value = true
            }
        }
    }

    fun update(context: Context, updated: KeyboardPreferences) {
        hasPendingUserUpdate = true
        PreferencesState.preferences.value = updated
        val appCtx = context.applicationContext
        scope.launch {
            try {
                appCtx.keskeDataStore.edit { p ->
                    p[KEY_PALETTE] = updated.paletteId
                    p[KEY_CV_PRESET] = updated.chuvashPreset
                    p[KEY_HEIGHT] = updated.keyHeightDp
                    p[KEY_RADIUS] = updated.keyCornerRadiusDp
                    p[KEY_GAP_H] = updated.keyGapHDp
                    p[KEY_GAP_V] = updated.keyGapVDp
                    p[KEY_FONT_SIZE] = updated.fontSizeSp
                    p[KEY_BOTTOM_INSET] = updated.bottomInsetDp
                    p[KEY_SOUND_VOLUME] = updated.soundVolumePercent
                    p[KEY_LONG_PRESS] = updated.longPressDelayMs
                    p[KEY_LAYOUTS] = updated.enabledLayouts
                    p[KEY_NUM_ROW] = updated.showNumberRow
                    p[KEY_SUB_LABELS] = updated.showSubLabels
                    p[KEY_HEX_GRID] = updated.hexagonalGrid
                    p[KEY_SOUND] = updated.soundEnabled
                    p[KEY_VIBRATE] = updated.vibrationEnabled
                    p[KEY_AUTO_CAP] = updated.autoCapitalize
                    p[KEY_SWIPE_UP_FIRST] = updated.swipeUpFirstVariant
                    p[KEY_DOUBLE_SPACE_COMMA] = updated.doubleSpaceComma
                    p[KEY_SHOW_HYPHEN] = updated.showHyphenKey
                    p[KEY_PARTICLES] = updated.showParticlesStrip
                    p[KEY_EXP_CAP_NAMES] = updated.experimentalCapitalizeNames
                    updated.customRowsByLang.forEach { (lang, rows) ->
                        p[rowsKey(lang)] = serializeRows(rows)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось сохранить настройки клавиатуры", e)
            }
        }
    }
}
