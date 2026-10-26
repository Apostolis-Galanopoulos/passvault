package dev.passvault.crypto

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?/~"

    data class Options(
        val length: Int = 20,
        val lower: Boolean = true,
        val upper: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
    ) {
        internal val sets: List<String>
            get() = listOfNotNull(
                LOWER.takeIf { lower },
                UPPER.takeIf { upper },
                DIGITS.takeIf { digits },
                SYMBOLS.takeIf { symbols },
            )

        val isValid: Boolean get() = sets.isNotEmpty() && length >= sets.size
    }

    private val random = SecureRandom()

    /** Generates a password containing at least one character from every enabled set. */
    fun generate(options: Options): String {
        require(options.isValid) { "Select at least one character set" }
        val sets = options.sets
        val all = sets.joinToString("")
        val chars = CharArray(options.length) { i ->
            val set = if (i < sets.size) sets[i] else all
            set[random.nextInt(set.length)]
        }
        // Fisher-Yates shuffle so the guaranteed characters aren't always first.
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }
}
