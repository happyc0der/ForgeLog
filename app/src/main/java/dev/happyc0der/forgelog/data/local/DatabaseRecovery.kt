package dev.happyc0der.forgelog.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File

/**
 * A database SQLite would not open, and the copy kept of it.
 *
 * [preservedFileName] is null when the copy could not be made — the disk being full is a plausible
 * reason for the corruption in the first place — and the loss is still worth reporting either way.
 */
data class UnreadableDatabase(
    val preservedFileName: String?,
    val atEpochMs: Long,
)

/**
 * Remembers that the database could not be read, so the app can say so once.
 *
 * Kept in SharedPreferences for the obvious reason: the thing being reported is the database.
 */
interface DatabaseRecoveryLog {
    fun record(unreadable: UnreadableDatabase)

    /** The loss the user has not been told about yet, or null. */
    fun unreported(): UnreadableDatabase?

    fun markReported()
}

/** Remembers nothing, for tests and for anything with no need to report. */
object NoDatabaseRecoveryLog : DatabaseRecoveryLog {
    override fun record(unreadable: UnreadableDatabase) = Unit
    override fun unreported(): UnreadableDatabase? = null
    override fun markReported() = Unit
}

class SharedPreferencesDatabaseRecoveryLog(context: Context) : DatabaseRecoveryLog {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun record(unreadable: UnreadableDatabase) {
        // commit, not apply: this is written while the database is failing to open, and the process
        // may not survive long enough to flush an asynchronous write.
        prefs.edit(commit = true) {
            putLong(KEY_AT, unreadable.atEpochMs)
            if (unreadable.preservedFileName != null) {
                putString(KEY_FILE, unreadable.preservedFileName)
            } else {
                remove(KEY_FILE)
            }
        }
    }

    /**
     * Whether a record exists is asked of the key, not of the time in it.
     *
     * Reading "no timestamp" out of a zero used to double as "nothing recorded", which is wrong on
     * a phone whose clock has not been set yet — a factory reset before it reaches the network
     * starts at the epoch. That is exactly a first boot, which is exactly when a database might be
     * unreadable, and the loss would have gone unmentioned.
     */
    override fun unreported(): UnreadableDatabase? {
        if (!prefs.contains(KEY_AT)) return null
        return UnreadableDatabase(
            preservedFileName = prefs.getString(KEY_FILE, null),
            atEpochMs = prefs.getLong(KEY_AT, 0L),
        )
    }

    override fun markReported() {
        prefs.edit { clear() }
    }

    private companion object {
        const val FILE_NAME = "forgelog_db_recovery"
        const val KEY_AT = "unreadableAtEpochMs"
        const val KEY_FILE = "preservedFileName"
    }
}

/**
 * Keeps a copy of a database SQLite refuses to open, before the framework deletes it.
 *
 * The platform's own corruption handler deletes the file and lets Room build an empty one in its
 * place, which is silent total loss of someone's training history — the same thing this project
 * refuses to do on a missing migration, reached through a different door. It cannot simply be
 * blocked: refusing to recover leaves the app unable to start, and starting is what it takes to
 * reach Settings and restore a backup.
 *
 * So the file is copied aside first. The user is told, gets an app that works, and keeps the bytes
 * in case they are worth anything later. The journal files go with it, since a database is not
 * necessarily readable without them.
 */
internal class CorruptionPreservingFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val recoveryLog: DatabaseRecoveryLog,
    private val now: () -> Long,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration,
    ): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val context = configuration.context
        val name = configuration.name

        // Every field of the configuration has to be carried across, not just the ones that look
        // relevant: what is built here replaces Room's own, and anything left out silently reverts
        // to a default. allowDataLossOnRecovery is the one that matters most -- it decides whether
        // the helper may delete a database it cannot open, which is the very thing being handled.
        val wrapped = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(name)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(original.version) {
                    override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)

                    override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = original.onUpgrade(db, oldVersion, newVersion)

                    override fun onDowngrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = original.onDowngrade(db, oldVersion, newVersion)

                    override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)

                    override fun onCorruption(db: SupportSQLiteDatabase) = recordThenRecover(
                        record = {
                            val at = now()
                            val preserved = name?.let { preserve(context, it, at) }
                            recoveryLog.record(
                                UnreadableDatabase(preservedFileName = preserved, atEpochMs = at),
                            )
                        },
                        // The platform's own handling, which deletes the file and lets Room start
                        // again. Without it the app cannot open at all.
                        recover = { original.onCorruption(db) },
                    )
                },
            )
            .build()

        return delegate.create(wrapped)
    }

    /**
     * Copies the database and its journals aside. Returns the copy's name, or null if it failed.
     *
     * Copies are not capped or cleaned up, deliberately. A second corruption would want preserving
     * too -- by then the database has a fresh history in it -- and there is no way to tell from here
     * which copy is the one worth keeping, so throwing any of them away would be a guess. What that
     * costs is a file of a megabyte or two per event, in a directory only the app can read, with no
     * way for the user to delete it short of clearing the app's data. That is a poor trade only if
     * corruption happens repeatedly, which is not the failure being planned for. Code running while
     * the database is already failing is the wrong place for logic that could fail on its own.
     */
    private fun preserve(context: Context, name: String, atEpochMs: Long): String? = runCatching {
        val source = context.getDatabasePath(name)
        if (!source.exists()) return null
        val copyName = "$name.unreadable-$atEpochMs"
        val target = File(source.parentFile, copyName)
        source.copyTo(target, overwrite = true)
        listOf("-wal", "-shm").forEach { suffix ->
            val journal = File(source.path + suffix)
            if (journal.exists()) {
                journal.copyTo(File(target.path + suffix), overwrite = true)
            }
        }
        copyName
    }.getOrNull()
}

/**
 * Records what happened, then lets the platform recover — and recovers even if recording fails.
 *
 * The order matters and so does the swallowing. Preserving the file and noting the loss both touch
 * a disk that may well be the reason the database is unreadable in the first place, and neither is
 * worth failing the open for: an exception here would stop the database being replaced, so Room
 * could not start, so the app could not start, and the user could never reach the backup that would
 * have saved them. Losing the notice is a bad outcome. Losing the app is the one this exists to
 * prevent.
 */
internal fun recordThenRecover(record: () -> Unit, recover: () -> Unit) {
    runCatching(record)
    recover()
}
