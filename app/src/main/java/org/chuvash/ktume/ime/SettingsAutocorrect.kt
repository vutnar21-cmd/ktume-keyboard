package org.chuvash.ktume.ime

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.data.PreferencesRepository
import org.chuvash.ktume.data.PreferencesState
import org.chuvash.ktume.data.autocorrect.AutocorrectEngine
import org.chuvash.ktume.ui.theme.KesjeFontFamily
import org.chuvash.ktume.ui.theme.KeyboardPalette

/**
 * МОДУЛЬ: SettingsAutocorrect.kt — блок эксперимента «Автозамена опечаток» на вкладке «Эксперименты»:
 * включение, языки словарей, обучение личного словаря, его просмотр, удаление слов и очистка.
 */
@Composable
internal fun AutocorrectSettingsBlock(
    prefs: KeyboardPreferences,
    palette: KeyboardPalette,
    savePrefs: (KeyboardPreferences) -> Unit
) {
    val context = LocalContext.current
    val personalWords by PreferencesState.personalWords.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    SettingToggleRow(
        title = "Автозамена опечаток",
        subtitle = "Слово с опечаткой исправляется при вводе пробела, знака препинания или переноса строки " +
            "(«превет» → «привет», «сак» → «ҫак»). Первое нажатие ⌫ сразу после исправления возвращает " +
            "набранное слово. Слова из словарей и личного словаря не трогаются. Пароли, адреса и e-mail " +
            "не исправляются.",
        checked = prefs.experimentalAutocorrect,
        palette = palette,
        onCheckedChange = { savePrefs(prefs.copy(experimentalAutocorrect = it)) }
    )
    if (!prefs.experimentalAutocorrect) return

    Text(
        text = "Словари автозамены (работают на раскладках Чӑвашла и Русская):",
        style = MaterialTheme.typography.bodySmall,
        color = palette.hintText,
        modifier = Modifier.padding(top = 6.dp)
    )
    AutocorrectEngine.SUPPORTED_LANGS.forEach { code ->
        val lang = KeyboardLang.entries.first { it.code == code }
        val checked = code in prefs.autocorrectLangs
        SettingToggleRow(
            title = "${lang.spaceLabel} (${lang.shortBadge})",
            subtitle = when (lang) {
                KeyboardLang.CHUVASH -> "≈160 тыс. словоформ из чувашского корпуса (CC0)"
                else -> "≈160 тыс. словоформ из субтитров OpenSubtitles (CC BY-SA 4.0)"
            },
            checked = checked,
            palette = palette,
            onCheckedChange = { enabled ->
                val next = if (enabled) prefs.autocorrectLangs + code else prefs.autocorrectLangs - code
                // Хотя бы один словарь: иначе включённая автозамена молча ничего не делает
                if (next.isNotEmpty()) savePrefs(prefs.copy(autocorrectLangs = next))
            }
        )
    }

    SettingToggleRow(
        title = "Запоминать отменённые слова",
        subtitle = "Слово, возвращённое через ⌫ после автозамены, попадает в личный словарь и больше не " +
            "исправляется. Словарь хранится только на телефоне; в режиме инкогнито приложений не пополняется.",
        checked = prefs.autocorrectLearnWords,
        palette = palette,
        onCheckedChange = { savePrefs(prefs.copy(autocorrectLearnWords = it)) }
    )

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Личный словарь: ${personalWords.size} ${wordsNoun(personalWords.size)}",
        color = palette.keyText,
        fontFamily = KesjeFontFamily,
        fontSize = 13.sp
    )
    if (personalWords.isEmpty()) {
        Text(
            text = "Пока пуст. Нажмите ⌫ сразу после неверной автозамены — слово запомнится.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.hintText
        )
        return
    }
    personalWords.sorted().forEach { word ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = word,
                color = palette.keyText,
                fontFamily = KesjeFontFamily,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { PreferencesRepository.removePersonalWord(context, word) }) {
                Text("Удалить", fontFamily = KesjeFontFamily, fontSize = 12.sp)
            }
        }
    }
    OutlinedButton(
        onClick = {
            if (confirmClear) {
                PreferencesRepository.clearPersonalWords(context)
                confirmClear = false
            } else {
                confirmClear = true
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (confirmClear) "Точно очистить? Нажмите ещё раз" else "Очистить личный словарь",
            fontFamily = KesjeFontFamily
        )
    }
}

/** «1 слово», «2 слова», «5 слов». */
internal fun wordsNoun(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "слов"
        mod10 == 1 -> "слово"
        mod10 in 2..4 -> "слова"
        else -> "слов"
    }
}
