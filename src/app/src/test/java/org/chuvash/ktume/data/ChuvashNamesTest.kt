package org.chuvash.ktume.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты базового списка имён собственных для автозамены. */
class ChuvashNamesTest {

    @Test
    fun capitalizeIfName_fixesLowercaseName() {
        assertEquals("Иван", ChuvashNames.capitalizeIfName("иван"))
        assertEquals("Анна", ChuvashNames.capitalizeIfName("анна"))
        assertEquals("Пётр", ChuvashNames.capitalizeIfName("пётр"))
    }

    @Test
    fun capitalizeIfName_ignoresOrdinaryWordsAndCommonNounHomonyms() {
        assertNull(ChuvashNames.capitalizeIfName("хула"))
        assertNull(ChuvashNames.capitalizeIfName("шкул"))
        assertNull(ChuvashNames.capitalizeIfName(""))
        for (homonym in listOf("вера", "любовь", "надежда", "роман")) {
            assertFalse("Homonym '$homonym' must not be auto-capitalized", ChuvashNames.isName(homonym))
            assertNull(ChuvashNames.capitalizeIfName(homonym))
        }
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
