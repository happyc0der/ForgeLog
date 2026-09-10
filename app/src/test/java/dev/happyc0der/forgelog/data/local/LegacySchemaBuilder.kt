package dev.happyc0der.forgelog.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Builds a database at an older schema version straight from the committed schema JSON.
 *
 * `MigrationTestHelper` reads those JSONs from assets, which Robolectric's instrumentation context
 * does not expose, so the schema is read from `app/schemas` on disk instead. The result is a higher
 * fidelity test than a hand-written CREATE TABLE would be: the tables, indices and identity hash
 * come from the exact file that shipped with that version, and the upgrade is then performed by Room
 * itself rather than by the test, so Room's own schema validation is what passes or fails.
 */
internal object LegacySchemaBuilder {

    private const val SCHEMA_DIR =
        "schemas/dev.happyc0der.forgelog.data.local.ForgeLogDatabase"

    /**
     * Creates [databaseFile] at schema [version] and hands it to [seed] for test rows.
     * The database is closed afterwards so Room can open and migrate it.
     */
    fun create(
        context: Context,
        databaseFile: File,
        version: Int,
        seed: (SupportSQLiteDatabase) -> Unit = {},
    ) {
        databaseFile.delete()
        val schema = JSONObject(schemaFile(version).readText()).getJSONObject("database")

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )

        helper.writableDatabase.use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").withTableName(tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (indexPosition in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(indexPosition)
                            .getString("createSql")
                            .withTableName(tableName),
                    )
                }
            }
            // room_master_table plus the version's identity hash, so Room recognises the database
            // as a genuine older version rather than a corrupt one.
            val setupQueries = schema.getJSONArray("setupQueries")
            for (index in 0 until setupQueries.length()) {
                db.execSQL(setupQueries.getString(index))
            }
            db.version = version
            seed(db)
        }
    }

    private fun schemaFile(version: Int): File {
        val file = File("$SCHEMA_DIR/$version.json")
        check(file.exists()) { "Missing committed schema for version $version at ${file.absolutePath}" }
        return file
    }

    private fun String.withTableName(tableName: String): String =
        replace("\${TABLE_NAME}", tableName)
}
