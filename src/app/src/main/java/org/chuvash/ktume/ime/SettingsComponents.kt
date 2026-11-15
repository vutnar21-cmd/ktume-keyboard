package org.chuvash.ktume.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.chuvash.ktume.data.ChuvashLayoutPreset
import org.chuvash.ktume.data.KeyboardHeightPreset
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.ui.theme.KesjeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: SettingsComponents.kt — переиспользуемые элементы экрана настроек:
 * карточка секции, карточка палитры, строка-переключатель и строка-ползунок.
 */

@Composable
internal fun SettingsSectionCard(
    title: String,
    palette: KeyboardPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = palette.keyBg,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.keyText,
                fontFamily = KesjeFontFamily
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun PalettePreviewCard(
    item: KeyboardPalette,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = item.boardBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .width(142.dp)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) item.accentBg else item.hintText.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title,
                color = item.keyText,
                fontFamily = KesjeFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 2
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ӑ", "ӗ", "ҫ").forEach { ch ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(item.keyBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch,
                            color = item.keyText,
                            fontFamily = KesjeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(item.accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSelected) "✓" else "↵",
                        color = item.accentText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: KeyboardPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = palette.keyText,
                fontFamily = KesjeFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = subtitle,
                color = palette.hintText,
                fontFamily = KesjeFontFamily,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
internal fun SettingSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            fontFamily = KesjeFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
internal fun ChuvashPresetOptionCard(
    preset: ChuvashLayoutPreset,
    isSelected: Boolean,
    palette: KeyboardPalette,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) palette.accentBg.copy(alpha = 0.16f) else palette.boardBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) palette.accentBg else palette.hintText.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = preset.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = palette.keyText
            )
            Text(
                text = preset.subtitle,
                fontSize = 11.sp,
                color = palette.hintText
            )
        }
    }
}

@Composable
internal fun KeyboardHeightPresetSelector(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    onSelectPreset: (KeyboardHeightPreset) -> Unit
) {
    val activePreset = prefs.activeHeightPreset()
    val headerText = if (activePreset != null) {
        val suffix = if (activePreset == KeyboardHeightPreset.NORMAL) ", по умолч." else ""
        "Высота клавиатуры: ${activePreset.title} (${activePreset.keyHeightDp.roundToInt()} dp$suffix)"
    } else {
        "Высота клавиатуры: своя (${prefs.keyHeightDp.roundToInt()} dp / ${prefs.fontSizeSp.roundToInt()} sp)"
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = headerText,
            color = palette.keyText,
            fontFamily = KesjeFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Text(
            text = "Пять готовых ступеней с подобранным размером шрифта",
            color = palette.hintText,
            fontFamily = KesjeFontFamily,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyboardHeightPreset.entries.forEach { preset ->
                val isSelected = activePreset == preset
                val heightLabel = "${preset.keyHeightDp.roundToInt()}"
                Surface(
                    color = if (isSelected) palette.accentBg.copy(alpha = 0.18f) else palette.boardBg,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) palette.accentBg else palette.hintText.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(11.dp)
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelectPreset(preset) }
                        )
                        .semantics {
                            contentDescription = "${preset.title}, $heightLabel dp"
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = heightLabel,
                            color = if (isSelected) palette.accentBg else palette.keyText,
                            fontFamily = KesjeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = preset.badge,
                            color = if (isSelected) palette.keyText else palette.hintText,
                            fontFamily = KesjeFontFamily,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
