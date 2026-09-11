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
        if (!decimal) {
            return text.takeIf { candidate ->
                candidate.all(Char::isDigit) && candidate.length <= MAX_WHOLE_DIGITS
            }
        }
        val normalised = text.replace(',', '.')
        if (!normalised.all { it.isDigit() || it == '.' }) return null
        val parts = normalised.split('.')
        return normalised.takeIf {
            parts.size <= 2 &&
                parts[0].length <= MAX_WHOLE_DIGITS &&
                parts.getOrElse(1) { "" }.length <= MAX_FRACTION_DIGITS
        }
    }

    /*
     * Limits, so the same silent loss cannot come back by length: an integer field took any run of
     * digits, and past 2,147,483,647 toIntOrNull gave null -- the field showing a number while
     * storing none. Six digits covers every count here (reps, sets, the seconds in a day); two
     * decimals, every plate (0.25 kg) and every minutes-typed rest ("1.27").
     */
    private const val MAX_WHOLE_DIGITS = 6
    private const val MAX_FRACTION_DIGITS = 2
}
