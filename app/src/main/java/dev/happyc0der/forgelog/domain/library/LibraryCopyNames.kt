package dev.happyc0der.forgelog.domain.library

object LibraryCopyNames {
    fun programCopy(name: String): String = "${name.trim()} (copy)"

    fun dayCopy(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Day B"
        return if (trimmed.endsWith(" B", ignoreCase = true)) {
            "$trimmed copy"
        } else {
            "$trimmed B"
        }
    }
}
