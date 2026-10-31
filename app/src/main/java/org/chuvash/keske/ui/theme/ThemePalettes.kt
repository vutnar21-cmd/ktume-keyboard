package org.chuvash.keske.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.chuvash.keske.R

/**
 * МОДУЛЬ: ThemePalettes.kt
 * Здесь описаны:
 * 1) Единый шрифт Comfortaa (KeskeFontFamily) и типографика приложения;
 * 2) 7 цветовых палитр клавиатуры (KeskePalettes) и сквозная конвертация в Material 3 ColorScheme.
 * Настройки (KeyboardPreferences) и хранилище DataStore (PreferencesRepository) вынесены в пакет `data`.
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
