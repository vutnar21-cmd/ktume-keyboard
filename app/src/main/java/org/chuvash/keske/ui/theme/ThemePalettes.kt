package org.chuvash.keske.ui.theme

import android.content.Context
import android.util.Log
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.chuvash.keske.R
import org.chuvash.keske.ime.ChuvashLayoutPreset
import org.chuvash.keske.ime.KeskeImeService
import org.chuvash.keske.ime.KeyboardLang
import org.chuvash.keske.ime.KeyboardLayouts

/**
 * МОДУЛЬ: ThemePalettes.kt
 * Здесь описаны:
 * 1) Единый шрифт Comfortaa (KĕskeFontFamily) и типографика приложения;
 * 2) Цветовые палитры клавиатуры и сквозная синхронизация с темой приложения (включая тёмную тему);
 * 3) Класс настроек KeyboardPreferences и сохранение на диск через Jetpack DataStore.
 */
val KeskeFontFamily = FontFamily(
    Font(R.font.comfortaa, FontWeight.Normal),
    Font(R.font.comfortaa, FontWeight.Medium),
    Font(R.font.comfortaa, FontWeight.SemiBold),
    Font(R.font.comfortaa, FontWeight.Bold)
)

val KeskeTypography = Typography(
    headlineSmall = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = KeskeFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

data class KeyboardPalette(
    val id: String,
    val title: String,
    val isDark: Boolean,
    val boardBg: Color,
    val keyBg: Color,
    val specialKeyBg: Color,
    val accentBg: Color,
    val accentText: Color,
    val keyText: Color,
    val hintText: Color,
    val popupBg: Color
) {
    fun toColorScheme() = if (isDark) {
        darkColorScheme(
            primary = accentBg,
            onPrimary = accentText,
            primaryContainer = specialKeyBg,
            onPrimaryContainer = keyText,
            secondary = accentBg,
            onSecondary = accentText,
            secondaryContainer = keyBg,
            onSecondaryContainer = keyText,
            background = boardBg,
            onBackground = keyText,
            surface = keyBg,
            onSurface = keyText,
            surfaceVariant = specialKeyBg,
            onSurfaceVariant = hintText,
            outline = hintText.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = accentBg,
            onPrimary = accentText,
            primaryContainer = specialKeyBg,
            onPrimaryContainer = keyText,
            secondary = accentBg,
            onSecondary = accentText,
            secondaryContainer = specialKeyBg,
            onSecondaryContainer = keyText,
            background = boardBg,
            onBackground = keyText,
            surface = keyBg,
            onSurface = keyText,
            surfaceVariant = specialKeyBg,
            onSurfaceVariant = hintText,
            outline = hintText.copy(alpha = 0.45f)
        )
    }
}

object KeskePalettes {
    val SoftSage = KeyboardPalette(
        id = "sage",
        title = "Утренний Шалфей (Soft Sage)",
        isDark = false,
        boardBg = Color(0xFFEAF0EC),
        keyBg = Color(0xFFFDFEFE),
        specialKeyBg = Color(0xFFDCE6DF),
        accentBg = Color(0xFF4A7C59),
        accentText = Color(0xFFFFFFFF),
        keyText = Color(0xFF1E2D24),
        hintText = Color(0xFF5C7A67),
        popupBg = Color(0xFFF7FBF8)
    )

    val WarmSand = KeyboardPalette(
        id = "sand",
        title = "Тёплый Песок (Кӗскӗ Oat)",
        isDark = false,
        boardBg = Color(0xFFF3ECE3),
        keyBg = Color(0xFFFFFDF9),
        specialKeyBg = Color(0xFFE6DDD0),
        accentBg = Color(0xFFB85D3B),
        accentText = Color(0xFFFFFFFF),
        keyText = Color(0xFF2E241D),
        hintText = Color(0xFF8C6D58),
        popupBg = Color(0xFFFFFBF5)
    )

    val LavenderMist = KeyboardPalette(
        id = "lavender",
        title = "Лавандовый Туман (Lavender Mist)",
        isDark = false,
        boardBg = Color(0xFFEDEAF4),
        keyBg = Color(0xFFFCFBFF),
        specialKeyBg = Color(0xFFDDD8EC),
        accentBg = Color(0xFF6750A4),
        accentText = Color(0xFFFFFFFF),
        keyText = Color(0xFF221D30),
        hintText = Color(0xFF70658E),
        popupBg = Color(0xFFF9F7FF)
    )

    val BlushRose = KeyboardPalette(
        id = "rose",
        title = "Нежная Сакура (Blush Rose)",
        isDark = false,
        boardBg = Color(0xFFF5E9EC),
        keyBg = Color(0xFFFFFBFC),
        specialKeyBg = Color(0xFFEAD7DC),
        accentBg = Color(0xFFA8425E),
        accentText = Color(0xFFFFFFFF),
        keyText = Color(0xFF331C23),
        hintText = Color(0xFF8D5B69),
        popupBg = Color(0xFFFFF7F9)
    )

    val NordicSky = KeyboardPalette(
        id = "nordic",
        title = "Северный Бриз (Nordic Sky)",
        isDark = false,
        boardBg = Color(0xFFE6EEF4),
        keyBg = Color(0xFFFAFDFF),
        specialKeyBg = Color(0xFFD4E2EE),
        accentBg = Color(0xFF366A9A),
        accentText = Color(0xFFFFFFFF),
        keyText = Color(0xFF192836),
        hintText = Color(0xFF58758E),
        popupBg = Color(0xFFF4F9FD)
    )

    val VelvetDark = KeyboardPalette(
        id = "velvet_dark",
        title = "Бархатная Ночь (Velvet Dark)",
        isDark = true,
        boardBg = Color(0xFF1A1D21),
        keyBg = Color(0xFF282C33),
        specialKeyBg = Color(0xFF20242A),
        accentBg = Color(0xFF8AB4F8),
        accentText = Color(0xFF121820),
        keyText = Color(0xFFEAECEF),
        hintText = Color(0xFF9AA4B2),
        popupBg = Color(0xFF323741)
    )

    val ObsidianForest = KeyboardPalette(
        id = "obsidian_forest",
        title = "Ночной Лес (OLED Dark)",
        isDark = true,
        boardBg = Color(0xFF111614),
        keyBg = Color(0xFF1E2723),
        specialKeyBg = Color(0xFF171F1B),
        accentBg = Color(0xFF68B684),
        accentText = Color(0xFF0E1511),
        keyText = Color(0xFFE5F0EA),
        hintText = Color(0xFF8AA696),
        popupBg = Color(0xFF26332D)
    )

    val all = listOf(SoftSage, WarmSand, LavenderMist, BlushRose, NordicSky, VelvetDark, ObsidianForest)

    fun byId(id: String): KeyboardPalette = all.find { it.id == id } ?: SoftSage
}

/**
 * Хранилище всех пользовательских настроек клавиатуры, включая
 * пользовательское расположение букв и количество клавиш в рядах (customRowsByLang).
 */
data class KeyboardPreferences(
    val paletteId: String = "obsidian_forest",
    val chuvashPreset: String = ChuvashLayoutPreset.BALANCED_26.id,
    val keyHeightDp: Float = 48f,
    val keyCornerRadiusDp: Float = 14f,
    val keyGapDp: Float = 3f,
    val fontSizeSp: Float = 26f,
    val bottomInsetDp: Float = 20f,     // По умолчанию 20 dp (регулируется ползунком в настройках)
    val soundVolumePercent: Float = 70f,// Регулировка громкости звука клавиш (привязана к общей громкости телефона)
    val longPressDelayMs: Int = 210,
    val enabledLayouts: Set<String> = setOf("cv", "ru", "lat"),
    val showNumberRow: Boolean = true,
    val showSubLabels: Boolean = false, // Micro-labels отключены по умолчанию
    val hexagonalGrid: Boolean = false, // Включается ТОЛЬКО в настройках
    val soundEnabled: Boolean = true,   // Звук при нажатии включён по умолчанию
    val vibrationEnabled: Boolean = true,
    val autoCapitalize: Boolean = true, // Автозаглавная на пустом поле и после точки
    val doubleSpaceComma: Boolean = true, // Двойной пробел ставит запятую ", "
    val showHyphenKey: Boolean = true,    // Отдельная клавиша дефиса "-" на главной странице + свайп влево от точки
    val showParticlesStrip: Boolean = true, // Лента чувашских частиц и аффиксов включена по умолчанию
    val customRowsByLang: Map<KeyboardLang, List<List<String>>> = mapOf(
        KeyboardLang.CHUVASH to KeyboardLayouts.getDefaultRows(KeyboardLang.CHUVASH, ChuvashLayoutPreset.BALANCED_26),
        KeyboardLang.RUSSIAN to KeyboardLayouts.getDefaultRows(KeyboardLang.RUSSIAN),
        KeyboardLang.LATINA to KeyboardLayouts.getDefaultRows(KeyboardLang.LATINA),
        KeyboardLang.GREEK to KeyboardLayouts.getDefaultRows(KeyboardLang.GREEK)
    )
)

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
    private val KEY_GAP = floatPreferencesKey("key_gap_dp_v5")
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
    private val KEY_DOUBLE_SPACE_COMMA = booleanPreferencesKey("double_space_comma")
    private val KEY_SHOW_HYPHEN = booleanPreferencesKey("show_hyphen_key")
    private val KEY_PARTICLES = booleanPreferencesKey("show_particles_v5")

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
                    keyGapDp = p[KEY_GAP] ?: 3f,
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
                    doubleSpaceComma = p[KEY_DOUBLE_SPACE_COMMA] ?: true,
                    showHyphenKey = p[KEY_SHOW_HYPHEN] ?: true,
                    showParticlesStrip = p[KEY_PARTICLES] ?: true,
                    customRowsByLang = customMap
                )
                if (!hasPendingUserUpdate) {
                    KeskeImeService.preferencesState.value = loaded
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
        KeskeImeService.preferencesState.value = updated
        val appCtx = context.applicationContext
        scope.launch {
            try {
                appCtx.keskeDataStore.edit { p ->
                    p[KEY_PALETTE] = updated.paletteId
                    p[KEY_CV_PRESET] = updated.chuvashPreset
                    p[KEY_HEIGHT] = updated.keyHeightDp
                    p[KEY_RADIUS] = updated.keyCornerRadiusDp
                    p[KEY_GAP] = updated.keyGapDp
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
                    p[KEY_DOUBLE_SPACE_COMMA] = updated.doubleSpaceComma
                    p[KEY_SHOW_HYPHEN] = updated.showHyphenKey
                    p[KEY_PARTICLES] = updated.showParticlesStrip
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
