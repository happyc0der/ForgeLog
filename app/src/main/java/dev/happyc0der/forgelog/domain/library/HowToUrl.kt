package dev.happyc0der.forgelog.domain.library

object HowToUrl {
    private val urlPattern = Regex(
        pattern = "^https?://[^\\s/$.?#].[^\\s]*$",
        option = RegexOption.IGNORE_CASE,
    )

    fun normalize(raw: String): Result<String?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.success(null)
        val candidate = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return if (urlPattern.matches(candidate)) {
            Result.success(candidate)
        } else {
            Result.failure(IllegalArgumentException("Enter a valid http or https link."))
        }
    }
}
