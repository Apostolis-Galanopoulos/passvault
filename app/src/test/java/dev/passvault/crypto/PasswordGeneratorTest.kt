package dev.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun includesEveryEnabledCharacterClass() {
        repeat(200) {
            val pw = PasswordGenerator.generate(PasswordGenerator.Options(length = 8))
            assertEquals(8, pw.length)
            assertTrue(pw.any { it.isLowerCase() })
            assertTrue(pw.any { it.isUpperCase() })
            assertTrue(pw.any { it.isDigit() })
            assertTrue(pw.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun respectsDisabledClasses() {
        val pw = PasswordGenerator.generate(
            PasswordGenerator.Options(length = 64, lower = false, upper = false, symbols = false)
        )
        assertTrue(pw.all { it.isDigit() })
    }

    @Test
    fun invalidOptions() {
        assertFalse(PasswordGenerator.Options(lower = false, upper = false, digits = false, symbols = false).isValid)
        assertFalse(PasswordGenerator.Options(length = 3).isValid)
    }

    @Test
    fun outputsDiffer() {
        val options = PasswordGenerator.Options()
        assertEquals(50, List(50) { PasswordGenerator.generate(options) }.toSet().size)
    }
}
