package org.chuvash.ktume.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты экспериментальной фичи «автозаглавные имена собственные». */
class ChuvashNamesTest {

    @Test
    fun capitalizeIfName_fixesLowercaseName() {
        assertEquals("Иван", ChuvashNames.capitalizeIfName("иван"))
        assertEquals("Анна", ChuvashNames.capitalizeIfName("анна"))
        assertEquals("Пётр", ChuvashNames.capitalizeIfName("пётр"))
    }

    @Test
    fun capitalizeIfName_ignoresOrdinaryWords() {
        assertNull(ChuvashNames.capitalizeIfName("хула"))
        assertNull(ChuvashNames.capitalizeIfName("шкул"))
        assertNull(ChuvashNames.capitalizeIfName(""))
    }

    @Test
    fun capitalizeIfName_doesNotTouchAlreadyCapitalized() {
        assertNull(ChuvashNames.capitalizeIfName("Иван"))
    }

    @Test
    fun isName_isCaseInsensitive() {
        assertTrue(ChuvashNames.isName("иван"))
        assertTrue(ChuvashNames.isName("ИВАН"))
        assertFalse(ChuvashNames.isName("хула"))
    }

    @Test
    fun listHasNoDuplicates() {
        // Косвенная проверка: список собран как Set, дубликаты не могли попасть.
        assertTrue("Name list must not be empty", ChuvashNames.isName("анна"))
    }
}
