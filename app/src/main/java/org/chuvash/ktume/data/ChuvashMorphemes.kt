package org.chuvash.ktume.data

/**
 * МОДУЛЬ: ChuvashMorphemes.kt — словарь чувашских морфем.
 *
 * ЧЕРНОВИК. Список и правила стыка требуют проверки носителем языка:
 * здесь собраны падежные, притяжательные, словообразовательные аффиксы и частицы,
 * которые чаще всего нужны при наборе текста. Правки не требуют изменения кода —
 * достаточно поправить [all].
 *
 * Каждая морфема хранит два набора форм: для мягкого (передний ряд) и твёрдого
 * (задний ряд) слова. Какую форму взять, решает [MorphemeEngine].
 */

/** Ряд сингармонизма: мягкий (передний) или твёрдый (задний). */
enum class Harmony { SOFT, HARD }

/** Тип морфемы — влияет только на группировку и порядок в ленте. */
enum class MorphemeKind { PARTICLE, CASE, POSSESSIVE, PLURAL, DERIVATIONAL }

/**
 * Условие, при котором берётся первая форма из набора.
 * Вторая форма используется во всех остальных случаях.
 */
enum class AttachRule {
    /** Всегда первая форма. */
    ANY,

    /** Первая форма — после гласной, вторая — после согласной (например, -на / -та). */
    AFTER_VOWEL,

    /** Первая форма — после согласной, вторая — после гласной. */
    AFTER_CONSONANT
}

/**
 * Одна морфема.
 *
 * @param id стабильный идентификатор (в отчётах и тестах)
 * @param kind тип морфемы
 * @param showWithDash показывать в ленте с ведущим дефисом (частицы, -и?, -ха)
 * @param soft формы для мягкого слова
 * @param hard формы для твёрдого слова
 * @param attach правило выбора между первой и второй формой
 * @param experimental морфема показывается только при включённых экспериментах
 */
data class Morpheme(
    val id: String,
    val kind: MorphemeKind,
    val showWithDash: Boolean,
    val soft: List<String>,
    val hard: List<String>,
    val attach: AttachRule = AttachRule.ANY,
    val experimental: Boolean = false
) {
    /** Формы для выбранного ряда сингармонизма. */
    fun forms(harmony: Harmony): List<String> = if (harmony == Harmony.SOFT) soft else hard

    /** Как морфема выглядит в ленте: «-и?», «сем», «-ра». */
    fun display(form: String): String = if (showWithDash) "-$form" else form
}

/**
 * Словарь морфем. Порядок в списке = порядок в ленте над клавиатурой.
 */
object ChuvashMorphemes {

    val all: List<Morpheme> = listOf(
        // --- частицы (всегда с дефисом) ---
        Morpheme("q_general", MorphemeKind.PARTICLE, showWithDash = true,
            soft = listOf("и?"), hard = listOf("и?")),
        Morpheme("q_confirm", MorphemeKind.PARTICLE, showWithDash = true,
            soft = listOf("ши?"), hard = listOf("ши?")),
        Morpheme("q_soften", MorphemeKind.PARTICLE, showWithDash = true,
            soft = listOf("хе"), hard = listOf("ха")),
        Morpheme("q_polite", MorphemeKind.PARTICLE, showWithDash = true,
            soft = listOf("ҫкӗ"), hard = listOf("ҫкӗ")),
        Morpheme("q_let", MorphemeKind.PARTICLE, showWithDash = true,
            soft = listOf("тӗр"), hard = listOf("тӑр")),

        // --- множественное число ---
        Morpheme("pl", MorphemeKind.PLURAL, showWithDash = false,
            soft = listOf("сем"), hard = listOf("сем")),

        // --- падежи ---
        // Местный: после гласной -ра/-ре, после согласной -та/-те (хулара, шкулта).
        Morpheme("loc", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("ре", "те"), hard = listOf("ра", "та"),
            attach = AttachRule.AFTER_VOWEL),
        // Исходный: -ран/-рен после гласной, -тан/-тен после согласной.
        Morpheme("abl", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("рен", "тен"), hard = listOf("ран", "тан"),
            attach = AttachRule.AFTER_VOWEL),
        // Дательно-винительный: после гласной -на/-не, после согласной -а/-е.
        Morpheme("dat", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("не", "е"), hard = listOf("на", "а"),
            attach = AttachRule.AFTER_VOWEL),
        // Творительный: -па/-пе (литературная норма — всегда -па/-пе).
        Morpheme("ins", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("пе"), hard = listOf("па")),
        // Причинно-целевой: -шӑн/-шӗн.
        Morpheme("ben", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("шӗн"), hard = listOf("шӑн")),
        // Лишительный: -сӑр/-сӗр.
        Morpheme("abe", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("сӗр"), hard = listOf("сӑр")),
        // Сравнительный: -ла/-ле.
        Morpheme("equ", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("ле"), hard = listOf("ла")),
        // Предельный: -ччен.
        Morpheme("term", MorphemeKind.CASE, showWithDash = false,
            soft = listOf("ччен"), hard = listOf("ччен")),

        // --- принадлежность (посессив) ---
        Morpheme("px1s", MorphemeKind.POSSESSIVE, showWithDash = false,
            soft = listOf("ӗм", "м"), hard = listOf("ӑм", "м"),
            attach = AttachRule.AFTER_CONSONANT),
        Morpheme("px2s", MorphemeKind.POSSESSIVE, showWithDash = false,
            soft = listOf("ӳ", "у"), hard = listOf("у", "у")),
        Morpheme("px3s", MorphemeKind.POSSESSIVE, showWithDash = false,
            soft = listOf("ӗ", "и"), hard = listOf("ӗ", "и")),
        Morpheme("px1p", MorphemeKind.POSSESSIVE, showWithDash = false,
            soft = listOf("ӗмӗр", "мӗр"), hard = listOf("ӑмӑр", "мӑр"),
            attach = AttachRule.AFTER_CONSONANT),
        Morpheme("px2p", MorphemeKind.POSSESSIVE, showWithDash = false,
            soft = listOf("ӗр", "рӗр"), hard = listOf("ӑр", "рӑр"),
            attach = AttachRule.AFTER_CONSONANT),

        // --- словообразование (экспериментальные) ---
        Morpheme("der_agent", MorphemeKind.DERIVATIONAL, showWithDash = false,
            soft = listOf("ҫӗ"), hard = listOf("ҫӑ"), experimental = true),
        Morpheme("der_abstract", MorphemeKind.DERIVATIONAL, showWithDash = false,
            soft = listOf("лӑх", "лӗх"), hard = listOf("лӑх", "лӗх"), experimental = true)
    )

    /** Морфемы для обычного режима (без экспериментальных). */
    fun core(): List<Morpheme> = all.filter { !it.experimental }
}
