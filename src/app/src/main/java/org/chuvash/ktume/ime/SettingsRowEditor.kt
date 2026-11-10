package org.chuvash.ktume.ime

import org.chuvash.ktume.data.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.chuvash.ktume.ui.theme.KesjeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: SettingsRowEditor.kt — конструктор рядов раскладки.
 * Выбор языка, добавление и удаление клавиш, обмен местами и перенос между рядами.
 * Состояние выбранной ячейки и редактируемого языка живёт в SettingsActivity.
 */
@Composable
internal fun SettingsRowEditor(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    savePrefs: (KeyboardPreferences) -> Unit,
    editingLang: KeyboardLang,
    onEditingLangChange: (KeyboardLang) -> Unit,
    selectedCell: Pair<Int, Int>?,
    onSelectedCellChange: (Pair<Int, Int>?) -> Unit
) {
    // 5. Количество букв в ряду и редактор состава рядов
    SettingsSectionCard(
        title = "Количество букв в ряду и конструктор рядов",
        palette = palette
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            KeyboardLang.entries.forEach { lang ->
                FilterChip(
                    selected = editingLang == lang,
                    onClick = {
                        onEditingLangChange(lang)
                        onSelectedCellChange(null)
                    },
                    label = { Text(lang.spaceLabel, fontFamily = KesjeFontFamily, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val langRows = prefs.customRowsByLang[editingLang]
            ?: KeyboardLayouts.getDefaultRows(editingLang)

        langRows.forEachIndexed { rIdx, rowList ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(palette.boardBg, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ряд ${rIdx + 1}: ${rowList.size} клавиш",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.keyText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                if (rowList.size > 4) {
                                    val mut = langRows.map { it.toMutableList() }.toMutableList()
                                    mut[rIdx].removeAt(mut[rIdx].lastIndex)
                                    val map = prefs.customRowsByLang.toMutableMap()
                                    map[editingLang] = mut
                                    savePrefs(prefs.copy(customRowsByLang = map))
                                    onSelectedCellChange(null)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("− Убрать", fontSize = 11.sp, fontFamily = KesjeFontFamily)
                        }
                        FilledTonalButton(
                            onClick = {
                                if (rowList.size < 12) {
                                    val mut = langRows.map { it.toMutableList() }.toMutableList()
                                    val used = mut.flatten().toSet()
                                    val next = KeyboardLayouts.getCandidatePool(editingLang)
                                        .firstOrNull { it !in used } ?: "ӑ"
                                    mut[rIdx].add(next)
                                    val map = prefs.customRowsByLang.toMutableMap()
                                    map[editingLang] = mut
                                    savePrefs(prefs.copy(customRowsByLang = map))
                                    onSelectedCellChange(rIdx to mut[rIdx].lastIndex)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("+ Добавить", fontSize = 11.sp, fontFamily = KesjeFontFamily)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowList.forEachIndexed { cIdx, ch ->
                        val pos = rIdx to cIdx
                        val isSel = selectedCell == pos
                        Surface(
                            color = if (isSel) palette.accentBg else palette.keyBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(34.dp)
                                .semantics {
                                    selected = isSel
                                    contentDescription = "Клавиша $ch, ряд ${rIdx + 1}"
                                }
                                .clickable {
                                    val prev = selectedCell
                                    if (prev == null) {
                                        onSelectedCellChange(pos)
                                    } else if (prev == pos) {
                                        onSelectedCellChange(null)
                                    } else {
                                        val mut = langRows.map { it.toMutableList() }.toMutableList()
                                        val a = mut[prev.first][prev.second]
                                        val b = mut[pos.first][pos.second]
                                        mut[prev.first][prev.second] = b
                                        mut[pos.first][pos.second] = a
                                        val map = prefs.customRowsByLang.toMutableMap()
                                        map[editingLang] = mut
                                        savePrefs(prefs.copy(customRowsByLang = map))
                                        onSelectedCellChange(null)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ch,
                                    color = if (isSel) palette.accentText else palette.keyText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = KesjeFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedCell?.let { sel ->
            val curChar = langRows.getOrNull(sel.first)?.getOrNull(sel.second)
            if (curChar != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Выбрана клавиша «$curChar» (нажмите на другую для обмена или перенесите в другой ряд):",
                    fontSize = 12.sp,
                    color = palette.accentBg,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (sel.first > 0 && langRows[sel.first].size > 4 && langRows[sel.first - 1].size < 12) {
                                val mut = langRows.map { it.toMutableList() }.toMutableList()
                                val ch = mut[sel.first].removeAt(sel.second)
                                val ins = sel.second.coerceAtMost(mut[sel.first - 1].size)
                                mut[sel.first - 1].add(ins, ch)
                                val map = prefs.customRowsByLang.toMutableMap()
                                map[editingLang] = mut
                                savePrefs(prefs.copy(customRowsByLang = map))
                                onSelectedCellChange((sel.first - 1) to ins)
                            }
                        }
                    ) {
                        Text("▲ В ряд выше", fontSize = 11.sp, fontFamily = KesjeFontFamily)
                    }
                    OutlinedButton(
                        onClick = {
                            if (sel.first < langRows.lastIndex && langRows[sel.first].size > 4 && langRows[sel.first + 1].size < 12) {
                                val mut = langRows.map { it.toMutableList() }.toMutableList()
                                val ch = mut[sel.first].removeAt(sel.second)
                                val ins = sel.second.coerceAtMost(mut[sel.first + 1].size)
                                mut[sel.first + 1].add(ins, ch)
                                val map = prefs.customRowsByLang.toMutableMap()
                                map[editingLang] = mut
                                savePrefs(prefs.copy(customRowsByLang = map))
                                onSelectedCellChange((sel.first + 1) to ins)
                            }
                        }
                    ) {
                        Text("▼ В ряд ниже", fontSize = 11.sp, fontFamily = KesjeFontFamily)
                    }
                }
            }
        }
    }
}
