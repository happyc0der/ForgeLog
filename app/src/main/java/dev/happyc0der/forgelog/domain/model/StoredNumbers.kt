package dev.happyc0der.forgelog.domain.model

/**
 * The largest numbers the app stores, decided in one place.
 *
 * This used to be two copies of one rule: the entry fields capped at six whole digits and two
 * decimals, and the backup importer turned away anything bigger. Written down twice they agreed
 * only for values stored in the units they were typed in — and a duration or a rest typed in
 * minutes is multiplied by sixty on its way to the database. Six digits of minutes became a number
 * the importer would not take back.
 *
 * Which broke in the worst direction. The set saved. The export wrote it out. The *restore* refused,
 * and it refuses the whole file, so one absurd rest — a stray digit is all it takes — made every
 * backup from then on unusable, with nothing to say which set was at fault. One rule in one place so
 * that a conversion can be checked against the same number the importer uses.
 */
object StoredNumbers {

    /** Counts and whole seconds: reps, sets, durations, rests. */
    const val MAX_WHOLE: Int = 999_999

    /** Measured quantities: weights, distances, body measurements. */
    const val MAX_MEASUREMENT: Double = 999_999.99

    /** Whole values that can be stored and read back, for clamping a converted entry. */
    val STORABLE_WHOLE: IntRange = 0..MAX_WHOLE
}
