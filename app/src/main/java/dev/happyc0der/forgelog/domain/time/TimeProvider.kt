package dev.happyc0der.forgelog.domain.time

fun interface TimeProvider {
    fun nowEpochMs(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
