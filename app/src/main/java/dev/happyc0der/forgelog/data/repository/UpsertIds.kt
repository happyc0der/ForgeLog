package dev.happyc0der.forgelog.data.repository

/**
 * The id of the row an upsert wrote.
 *
 * Room's `@Upsert` answers the new row id for an insert and -1 for an update, so a caller writing a
 * row that already exists -- an importer meeting an activity again, say -- would be handed -1 and
 * go on to attach children to it. [id] is the id the row was written with; 0 for a new one.
 */
internal fun Long.orExistingId(id: Long): Long = if (this == -1L && id != 0L) id else this
