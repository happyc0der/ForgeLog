package dev.happyc0der.forgelog.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The wrapper that keeps a database SQLite will not open, instead of letting it be deleted silently.
 *
 * Two things are worth pinning here and neither is obvious from reading the class. The first is that
 * it is a *wrapper*: the configuration it hands the real factory is one it built itself, so any
 * field it forgets to carry across quietly reverts to a default, and every callback it forgets to
 * pass on is one Room never hears about. The second is the order — the copy has to be taken before
 * the platform's own handling deletes the file, and reversing the two lines would leave the feature
 * reporting a loss with nothing kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorruptionPreservingFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A real one, only because the callbacks take one. None of them reads it. */
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        database = FrameworkSQLiteOpenHelperFactory()
            .create(configuration(name = "scratch.db", callback = DoesNothing(version = 1)))
            .writableDatabase
    }

    /** Notes what it was handed, so the wrapper can be asked whether it passed things on. */
    private open class DoesNothing(version: Int) : SupportSQLiteOpenHelper.Callback(version) {
        val calls = mutableListOf<String>()

        override fun onConfigure(db: SupportSQLiteDatabase) {
            calls += "onConfigure"
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            calls += "onCreate"
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            calls += "onUpgrade($oldVersion to $newVersion)"
        }

        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            calls += "onDowngrade($oldVersion to $newVersion)"
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            calls += "onOpen"
        }

        override fun onCorruption(db: SupportSQLiteDatabase) {
            calls += "onCorruption"
        }
    }

    /** The platform's handling, which is to delete the database and let Room build a new one. */
    private class DeletesTheDatabase(version: Int, private val source: File) : DoesNothing(version) {
        override fun onCorruption(db: SupportSQLiteDatabase) {
            super.onCorruption(db)
            source.delete()
            File(source.path + "-wal").delete()
            File(source.path + "-shm").delete()
        }
    }

    /** Keeps whatever it is told, and counts, so a second record cannot hide behind the first. */
    private class Remembers : DatabaseRecoveryLog {
        var recorded: UnreadableDatabase? = null
            private set
        var records = 0
            private set

        override fun record(unreadable: UnreadableDatabase) {
            recorded = unreadable
            records++
        }

        override fun unreported(): UnreadableDatabase? = recorded
        override fun markReported() = Unit
    }

    /** Fails at the one moment the disk is least likely to cooperate. */
    private class Throws : DatabaseRecoveryLog {
        override fun record(unreadable: UnreadableDatabase): Unit = throw java.io.IOException("full")
        override fun unreported(): UnreadableDatabase? = null
        override fun markReported() = Unit
    }

    /** Keeps the configuration it was given, and opens nothing until asked. */
    private class Capturing : SupportSQLiteOpenHelper.Factory {
        lateinit var captured: SupportSQLiteOpenHelper.Configuration
            private set

        override fun create(
            configuration: SupportSQLiteOpenHelper.Configuration,
        ): SupportSQLiteOpenHelper {
            captured = configuration
            return FrameworkSQLiteOpenHelperFactory().create(configuration)
        }
    }

    private fun configuration(
        name: String?,
        callback: SupportSQLiteOpenHelper.Callback,
        noBackupDirectory: Boolean = false,
        allowDataLossOnRecovery: Boolean = false,
    ): SupportSQLiteOpenHelper.Configuration = SupportSQLiteOpenHelper.Configuration
        .builder(context)
        .name(name)
        .callback(callback)
        .noBackupDirectory(noBackupDirectory)
        .allowDataLossOnRecovery(allowDataLossOnRecovery)
        .build()

    /** Builds the wrapper over [Capturing] and returns the callback it handed the real factory. */
    private fun wrap(
        configuration: SupportSQLiteOpenHelper.Configuration,
        recoveryLog: DatabaseRecoveryLog = Remembers(),
        now: Long = 1_700_000_000_000L,
    ): SupportSQLiteOpenHelper.Configuration {
        val capturing = Capturing()
        CorruptionPreservingFactory(capturing, recoveryLog) { now }.create(configuration)
        return capturing.captured
    }

    private fun writeDatabaseFile(directory: File, name: String, contents: String): File {
        directory.mkdirs()
        val file = File(directory, name)
        file.writeText(contents)
        File(file.path + "-wal").writeText("$contents wal")
        File(file.path + "-shm").writeText("$contents shm")
        return file
    }

    private val databasesDir: File get() = context.getDatabasePath("any.db").parentFile!!

    // --- the wrapper passes everything on -------------------------------------------------------

    @Test
    fun `every callback but corruption is handed straight to the one Room gave`() {
        val room = DoesNothing(version = 7)
        val wrapped = wrap(configuration(name = "delegation.db", callback = room))

        wrapped.callback.onConfigure(database)
        wrapped.callback.onCreate(database)
        wrapped.callback.onUpgrade(database, 2, 5)
        wrapped.callback.onDowngrade(database, 5, 2)
        wrapped.callback.onOpen(database)

        assertEquals(
            listOf("onConfigure", "onCreate", "onUpgrade(2 to 5)", "onDowngrade(5 to 2)", "onOpen"),
            room.calls,
        )
    }

    @Test
    fun `the version Room asked for is the version the helper is built with`() {
        val wrapped = wrap(configuration(name = "version.db", callback = DoesNothing(version = 3)))

        assertEquals(3, wrapped.callback.version)
    }

    /**
     * Both flags, both ways round. A single case would pass just as well against a hardcoded value,
     * which is the mistake this is here to catch: `allowDataLossOnRecovery` decides whether the
     * helper may delete a database it cannot open, and losing it reverts to a default silently.
     */
    @Test
    fun `the configuration is carried across whole`() {
        listOf(false, true).forEach { noBackup ->
            listOf(false, true).forEach { allowDataLoss ->
                val case = "noBackupDirectory=$noBackup allowDataLossOnRecovery=$allowDataLoss"
                val room = DoesNothing(version = 4)
                val wrapped = wrap(
                    configuration(
                        name = "carried.db",
                        callback = room,
                        noBackupDirectory = noBackup,
                        allowDataLossOnRecovery = allowDataLoss,
                    ),
                )

                assertSame("the context changed: $case", context, wrapped.context)
                assertEquals("the name changed: $case", "carried.db", wrapped.name)
                assertEquals(
                    "useNoBackupDirectory was not carried across: $case",
                    noBackup,
                    wrapped.useNoBackupDirectory,
                )
                assertEquals(
                    "allowDataLossOnRecovery was not carried across: $case",
                    allowDataLoss,
                    wrapped.allowDataLossOnRecovery,
                )
            }
        }
    }

    /** A nameless database is in memory: there is no file to copy, and nothing may go wrong here. */
    @Test
    fun `an in-memory database is wrapped without a name, and can still go unreadable`() {
        val log = Remembers()
        val room = DoesNothing(version = 1)
        val wrapped = wrap(
            configuration(name = null, callback = room),
            recoveryLog = log,
            now = 55L,
        )

        assertNull(wrapped.name)

        wrapped.callback.onCorruption(database)

        assertNull(log.recorded?.preservedFileName)
        assertEquals(55L, log.recorded?.atEpochMs)
        assertEquals(listOf("onCorruption"), room.calls)
    }

    // --- and keeps the file before the platform takes it away ------------------------------------

    @Test
    fun `the database is copied aside before the platform deletes it`() {
        val source = writeDatabaseFile(databasesDir, "kept.db", "training history")
        val log = Remembers()
        val room = DeletesTheDatabase(version = 1, source = source)
        val wrapped = wrap(
            configuration(name = "kept.db", callback = room),
            recoveryLog = log,
            now = 4_242L,
        )

        wrapped.callback.onCorruption(database)

        // The original is gone, as it would be in the real thing -- so anything still here was
        // copied before that happened, which is the whole point of the class.
        assertFalse("the platform's own handling did not run", source.exists())
        assertEquals(listOf("onCorruption"), room.calls)

        val copyName = log.recorded?.preservedFileName
        assertEquals("kept.db.unreadable-4242", copyName)
        val copy = File(databasesDir, copyName!!)
        assertEquals("training history", copy.readText())
        assertEquals("training history wal", File(copy.path + "-wal").readText())
        assertEquals("training history shm", File(copy.path + "-shm").readText())
        assertEquals(4_242L, log.recorded?.atEpochMs)
        assertEquals(1, log.records)
    }

    /**
     * The dormant half of the same question. Room does not set this flag today, but the wrapper
     * carries it across, and if it is ever set the database moves to another directory — where this
     * has to follow it, or it would find nothing, copy nothing, and say a copy could not be kept.
     */
    @Test
    fun `a database kept out of backups is copied from where it actually lives`() {
        val noBackupDir = context.noBackupFilesDir
        val source = writeDatabaseFile(noBackupDir, "nobackup.db", "also training history")
        val log = Remembers()
        val wrapped = wrap(
            configuration(
                name = "nobackup.db",
                callback = DeletesTheDatabase(version = 1, source = source),
                noBackupDirectory = true,
            ),
            recoveryLog = log,
            now = 99L,
        )

        wrapped.callback.onCorruption(database)

        assertEquals("nobackup.db.unreadable-99", log.recorded?.preservedFileName)
        assertEquals(
            "also training history",
            File(noBackupDir, "nobackup.db.unreadable-99").readText(),
        )
        assertFalse(
            "it copied out of the databases directory, where this database never was",
            File(databasesDir, "nobackup.db.unreadable-99").exists(),
        )
    }

    @Test
    fun `a loss is still reported when there was no file to copy`() {
        File(databasesDir, "missing.db").delete()
        val log = Remembers()
        val room = DoesNothing(version = 1)
        val wrapped = wrap(
            configuration(name = "missing.db", callback = room),
            recoveryLog = log,
            now = 12L,
        )

        wrapped.callback.onCorruption(database)

        assertNotNull("the loss went unreported", log.recorded)
        assertNull(log.recorded?.preservedFileName)
        assertEquals(12L, log.recorded?.atEpochMs)
        assertEquals(
            "the app was left unable to open its database",
            listOf("onCorruption"),
            room.calls,
        )
    }

    @Test
    fun `the app still gets a database when the loss cannot be written down`() {
        writeDatabaseFile(databasesDir, "unwritable.db", "history")
        val room = DoesNothing(version = 1)
        val wrapped = wrap(
            configuration(name = "unwritable.db", callback = room),
            recoveryLog = Throws(),
        )

        wrapped.callback.onCorruption(database)

        assertTrue(
            "a failure to note the loss stopped the database being replaced",
            room.calls == listOf("onCorruption"),
        )
    }
}
