package com.calltags.app.helpers

import java.text.Normalizer

/** Phone keypad letters, so names and labels can be found by the digits typed on the dialpad. */
object T9 {
    private val KEYS = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )
    private val KEY_OF_LETTER = buildMap { KEYS.forEach { (key, letters) -> letters.forEach { put(it, key) } } }
    private val ACCENTS = Regex("\\p{Mn}+")

    /**
     * Whether [digits] spell the start of some word in [text] on the keypad, possibly running on into the
     * following words: "Camera shop" matches "2263" and "22637467", but not "263". Accents are ignored.
     */
    fun matches(text: String, digits: String): Boolean {
        if (digits.isEmpty()) return false

        val keys = StringBuilder()
        val wordStarts = ArrayList<Int>()
        var inWord = false
        for (char in Normalizer.normalize(text, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()) {
            val key = if (char in '0'..'9') char else KEY_OF_LETTER[char]
            if (key == null) {
                inWord = false
                continue
            }
            if (!inWord) wordStarts += keys.length
            inWord = true
            keys.append(key)
        }
        return wordStarts.any { keys.startsWith(digits, it) }
    }
}
