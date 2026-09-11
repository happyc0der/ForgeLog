package dev.happyc0der.forgelog.ui.input

/**
 * What a numeric field will accept as the user types.
 *
 * The numeric keyboard is a hint, not a filter: the pad on the test device has "." and "," keys
 * beside the digits even for an integer field, and a paste or a hardware keyboard ignores it
 * entirely. Two failures followed:
 *
 *  - The logger parsed reps with toIntOrNull, so "9." -- one stray tap -- saved reps as null while
 *    the field went on showing "9.", with nothing to say the number had been thrown away.
 *  - Weight parsed with toDoubleOrNull, which does not accept a comma, so anyone whose locale writes
 *    62,5 lost the weight the same silent way.
 *
 * So a keystroke that would make the text unparseable is refused outright, and a decimal comma is
 * read as a decimal point. The field can then never show something different from what is stored.
 */
object NumericInput {

    /**
     * The text to keep, or null to refuse the keystroke and leave the field as it was.
     *
     * Partial input is accepted when it could still become a number -- "", "62." and "." are all
     * mid-typing states, not errors.
     */
    fun accept(text: String, decimal: Boolean): String? {
        if (!decimal) return text.takeIf { candidate -> candidate.all(Char::isDigit) }
        val normalised = text.replace(',', '.')
        val separators = normalised.count { it == '.' }
        val digitsAndPoint = normalised.all { it.isDigit() || it == '.' }
        return normalised.takeIf { digitsAndPoint && separators <= 1 }
    }
}
