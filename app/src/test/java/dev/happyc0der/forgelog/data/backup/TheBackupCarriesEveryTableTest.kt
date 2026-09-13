package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.data.local.dao.BackupDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A table the backup does not carry is training history a restore throws away.
 *
 * Nothing catches that today except noticing. The export reads whole tables and the restore wipes and
 * rewrites them, both by hand, one call per table — so a new entity added to the database is simply
 * absent from all three lists, and the app goes on working perfectly: it is only the user who
 * restores from a backup who finds the new table empty, long after, with no way back. The schema is
 * a committed contract and the app is meant to grow, which is exactly when this happens.
 *
 * So the count is pinned, deliberately as a count rather than by inspecting the queries. Adding an
 * eighth entity fails this test, and the message says what to go and do.
 */
class TheBackupCarriesEveryTableTest {

    /*
     * Read from the newest committed schema, not by reflection: Room's @Database annotation is
     * BINARY-retained, so it does not exist at runtime to be asked. The schema JSONs are a committed
     * contract anyway, which makes them the right thing to hold the backup to.
     */
    private val tables: List<String> = newestSchema().let { schema ->
        Json.parseToJsonElement(schema.readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("entities")
            .jsonArray
            .map { entity -> entity.jsonObject.getValue("tableName").jsonPrimitive.content }
            .sorted()
    }

    private fun newestSchema(): File {
        val roots = listOf(File("schemas"), File("app/schemas"))
        val root = roots.firstOrNull { it.isDirectory }
        assertTrue("no committed schemas found, looked in $roots", root != null)
        val versions = root!!.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()
        assertTrue("no schema JSON under $root", versions.isNotEmpty())
        return versions.maxByOrNull { it.nameWithoutExtension.toInt() }!!
    }

    /** The table each list on the envelope carries, written out so a failure can name the gap. */
    private val carried: Map<String, String> = mapOf(
        "exercises" to "exercises",
        "workout_programs" to "programs",
        "program_days" to "programDays",
        "program_exercises" to "programExercises",
        "workout_sessions" to "sessions",
        "session_exercises" to "sessionExercises",
        "set_logs" to "setLogs",
    )

    @Test
    fun everyTableInTheDatabaseIsCarriedByTheBackup() {
        val uncarried = tables.filterNot { it in carried }
        assertEquals(
            "the database has tables the backup does not carry. Add each to BackupEnvelope, to " +
                "BackupDao (all/insert/deleteAll), to BackupRepositoryImpl's export, restore and " +
                "delete-all, and to this map — a restore drops whatever is missing: $uncarried",
            emptyList<String>(),
            uncarried,
        )
    }

    @Test
    fun theBackupCarriesNothingTheDatabaseNoLongerHas() {
        val stale = carried.keys.filterNot { it in tables }
        assertEquals("the backup names tables the database does not have: $stale", emptyList<String>(), stale)
    }

    /**
     * And the envelope has one list per table, no more.
     *
     * The map above is hand-written, so on its own it would go on agreeing with itself if a list
     * were added to the envelope and left out of it. This counts the lists themselves.
     */
    @Test
    fun theEnvelopeHasOneListPerTable() {
        // Java reflection on the backing fields: kotlin-reflect is not on the test classpath,
        // and a data class's fields are exactly its properties.
        val lists = BackupEnvelope::class.java.declaredFields
            .filter { field -> List::class.java.isAssignableFrom(field.type) }
            .map { field -> field.name }
            .sorted()
        assertEquals(
            "the envelope's lists and the database's tables have come apart",
            carried.values.sorted(),
            lists,
        )
    }

    /**
     * And the restore can write, and wipe, every one of them.
     *
     * Counted rather than matched by name: an export that reads a table a restore cannot write is
     * a file that fails halfway through, which is worse than not carrying it at all.
     */
    @Test
    fun theDaoCanReadWriteAndWipeEveryTable() {
        val methods = BackupDao::class.java.methods.map { it.name }
        listOf("all" to "read", "insert" to "write", "deleteAll" to "wipe").forEach { (prefix, what) ->
            val matching = methods.filter { it.startsWith(prefix) }
            assertEquals(
                "BackupDao cannot $what every table: ${matching.sorted()}",
                tables.size,
                matching.size,
            )
        }
    }
}
