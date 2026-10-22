package org.chuvash.keske.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты движка морфем. Проверяют черновой список из [ChuvashMorphemes]:
 * сингармонизм, выбор формы по окончанию основы и сохранение заглавной буквы.
 */
class MorphemeEngineTest {

    private fun morpheme(id: String): Morpheme =
        ChuvashMorphemes.all.first { it.id == id }

    @Test
    fun harmonyOf_usesLastVowel() {
        assertEquals(Harmony.SOFT, MorphemeEngine.harmonyOf("пӗр"))
        assertEquals(Harmony.SOFT, MorphemeEngine.harmonyOf("кил"))
        assertEquals(Harmony.SOFT, MorphemeEngine.harmonyOf("анне"))
        assertEquals(Harmony.HARD, MorphemeEngine.harmonyOf("хула"))
        assertEquals(Harmony.HARD, MorphemeEngine.harmonyOf("шкул"))
        assertEquals(Harmony.HARD, MorphemeEngine.harmonyOf("Шупашкар"))
        // Слово без гласных — твёрдый ряд по умолчанию
        assertEquals(Harmony.HARD, MorphemeEngine.harmonyOf("мн"))
    }

    @Test
    fun localCase_switchesBetweenRaAndTaByWordEnding() {
        val loc = morpheme("loc")
        assertEquals("хулара", MorphemeEngine.attach("хула", loc))
        assertEquals("шкулта", MorphemeEngine.attach("шкул", loc))
        assertEquals("аннере", MorphemeEngine.attach("анне", loc))
        assertEquals("килте", MorphemeEngine.attach("кил", loc))
    }

    @Test
    fun ablative_and_dative_pickFormByEnding() {
        assertEquals("хуларан", MorphemeEngine.attach("хула", morpheme("abl")))
        assertEquals("шкултан", MorphemeEngine.attach("шкул", morpheme("abl")))
        assertEquals("хулана", MorphemeEngine.attach("хула", morpheme("dat")))
        assertEquals("шкула", MorphemeEngine.attach("шкул", morpheme("dat")))
    }

    @Test
    fun attach_preservesLeadingCapital() {
        assertEquals("Шупашкарта", MorphemeEngine.attach("Шупашкар", morpheme("loc")))
        assertEquals("Хулара", MorphemeEngine.attach("Хула", morpheme("loc")))
    }

    @Test
    fun particles_areShownWithDash() {
        val suggestions = MorphemeEngine.suggestions("хула")
        assertTrue("General question particle expected", "-и?" in suggestions)
        assertTrue("Softening particle expected", "-ха" in suggestions)
        assertTrue("Affixes are shown without dash", "сем" in suggestions)
        assertTrue("Local case expected", "ра" in suggestions)
        assertEquals("Dash must only appear on particles", 0, suggestions.count { it.startsWith("-") } - 5)
    }

    @Test
    fun experimentalMorphemes_areHiddenByDefault() {
        val core = MorphemeEngine.suggestions("хула")
        val withExperimental = MorphemeEngine.suggestions("хула", includeExperimental = true)
        assertTrue(
            "Experimental list must be longer than the core one",
            withExperimental.size > core.size
        )
        assertTrue(
            "Core suggestions must not contain derivational forms",
            core.none { it == "ҫӑ" || it == "лӑх" }
        )
    }
}
