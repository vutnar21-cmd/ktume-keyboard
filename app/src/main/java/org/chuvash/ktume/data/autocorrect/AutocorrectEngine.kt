package org.chuvash.ktume.data.autocorrect

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.chuvash.ktume.data.KeyboardLang
import org.chuvash.ktume.data.KeyboardLayouts
import org.chuvash.ktume.data.KeyboardPreferences
import org.chuvash.ktume.data.PreferencesState

/**
 * МОДУЛЬ: AutocorrectEngine.kt — загрузка словарей автозамены из `assets/dict/<язык>.tsv`
 * и общий [Autocorrector] для сервиса клавиатуры и тестового поля настроек.
 *
 * Словари грузятся в фоне только когда эксперимент включён, и выгружаются при его выключении
 * или снятии языка — память занимают только реально нужные словари. Пока словарь не готов,
 * автозамена просто не срабатывает.
 */
object AutocorrectEngine {
    private const val TAG = "KeskeAutocorrect"

    /** Языки, для которых есть словари (порядок — порядок приоритета при равных оценках). */
    val SUPPORTED_LANGS: List<String> = listOf(KeyboardLang.CHUVASH.code, KeyboardLang.RUSSIAN.code)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    @Volatile
    private var wanted: Set<String> = emptySet()
    private var loaded: Map<String, WordTrie> = emptyMap()
    private val loading = HashSet<String>()

    /** Текущий корректор или `null`, если эксперимент выключен или словари ещё грузятся. */
    @Volatile
    var corrector: Autocorrector? = null
        private set

    private var costCacheKey: List<List<String>>? = null
    private var costCache: TypoCostModel = TypoCostModel()

    /** Приводит набор загруженных словарей к настройкам: догружает нужные, выгружает лишние. */
    fun sync(context: Context, prefs: KeyboardPreferences) {
        val target = if (prefs.experimentalAutocorrect) {
            SUPPORTED_LANGS.filter { it in prefs.autocorrectLangs }.toSet()
        } else {
            emptySet()
        }
        val appCtx = context.applicationContext
        synchronized(lock) {
            if (target == wanted && loading.isEmpty() && loaded.keys == target) return
            wanted = target
            if (!loaded.keys.all { it in target }) {
                loaded = loaded.filterKeys { it in target }
                rebuildLocked()
            }
            for (lang in target) {
                if (lang in loaded || lang in loading) continue
                loading += lang
                scope.launch { load(appCtx, lang) }
            }
        }
    }

    private fun load(context: Context, lang: String) {
        val trie = try {
            val started = System.nanoTime()
            context.assets.open("dict/$lang.tsv").bufferedReader().useLines { WordTrie.fromTsv(it) }.also {
                Log.i(TAG, "Словарь $lang: ${it.size} слов за ${(System.nanoTime() - started) / 1_000_000} мс")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось загрузить словарь $lang", e)
            null
        }
        synchronized(lock) {
            loading -= lang
            if (trie != null && lang in wanted) {
                loaded = loaded + (lang to trie)
                rebuildLocked()
            }
        }
    }

    private fun rebuildLocked() {
        val dicts = SUPPORTED_LANGS.mapNotNull { lang -> loaded[lang]?.let { Autocorrector.LanguageDictionary(lang, it) } }
        corrector = if (dicts.isEmpty()) null else Autocorrector(dicts)
    }

    /** Активная раскладка так же, как её выбирает `KeyboardScreen`: сохранённая или первая включённая. */
    fun resolveActiveLang(prefs: KeyboardPreferences): KeyboardLang {
        val enabled = KeyboardLang.entries.filter { it.code in prefs.enabledLayouts }.ifEmpty { listOf(KeyboardLang.CHUVASH) }
        return PreferencesState.activeLang.value?.takeIf { it in enabled } ?: enabled.first()
    }

    /** Цены правок по рядам текущей раскладки (кэшируются, пока ряды не меняются). */
    fun costsFor(prefs: KeyboardPreferences, lang: KeyboardLang): TypoCostModel {
        val rows = prefs.customRowsByLang[lang] ?: KeyboardLayouts.getDefaultRows(lang)
        synchronized(lock) {
            if (rows != costCacheKey) {
                costCacheKey = rows
                costCache = TypoCostModel.fromRows(rows)
            }
            return costCache
        }
    }

    /**
     * Исправление слова перед курсором для [session] — единая точка для сервиса и тестового поля.
     * Раскладки без словаря (латиница, греческий) автозамену не включают.
     */
    fun findCorrection(
        session: AutocorrectSession,
        prefs: KeyboardPreferences,
        beforeCursor: String,
        separator: String,
        langOverride: KeyboardLang? = null
    ): Autocorrector.Correction? {
        if (!prefs.experimentalAutocorrect) {
            session.cancelPending()
            return null
        }
        val lang = langOverride ?: resolveActiveLang(prefs)
        val corrector = if (lang.code in SUPPORTED_LANGS) corrector else null
        return session.correctionBeforeSeparator(
            beforeCursor = beforeCursor,
            separator = separator,
            corrector = corrector,
            activeLang = lang.code,
            personalWords = PreferencesState.personalWords.value,
            costs = costsFor(prefs, lang)
        )
    }
}
