package dev.happyc0der.forgelog.domain.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * A number as a person writes it: no trailing `.0` on a whole value, no scientific notation, and a
 * decimal point regardless of the device locale.
 *
 * `Double.toString()` renders 225 as "225.0" and 12000000 as "1.2E7". On screen that looks like a
 * bug; in an editable field it hands back something different from what was typed; in an exported
 * CSV a spreadsheet imports both as text rather than as numbers.
 *
 * It lives in the domain because the screens and the CSV exporter both need it and must agree — an
 * exported figure should read exactly as the app displayed it.
 */
fun plainNumber(value: Double): String =
    if (value % 1.0 == 0.0 && abs(value) < 1e15) {
        String.format(Locale.US, "%.0f", value)
    } else {
        BigDecimal(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
