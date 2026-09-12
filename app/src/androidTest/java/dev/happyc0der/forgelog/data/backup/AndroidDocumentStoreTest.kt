package dev.happyc0der.forgelog.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The one piece of the backup flow that never runs on a desktop JVM: the ContentResolver.
 *
 * Everything either side of it -- what goes into the file and what comes back out of it -- is
 * covered by BackupRoundTripTest under Robolectric. This is the part that only a real Android
 * runtime can answer: whether the bytes the app writes to a document the user picked are the bytes
 * it reads back, and whether a document it cannot read fails rather than crashes.
 *
 * It writes through MediaStore rather than the Storage Access Framework because driving the system
 * file picker is not something these tests do. The provider differs; the resolver, the stream
 * handling and the character encoding are the same code.
 */
@RunWith(AndroidJUnit4::class)
class AndroidDocumentStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AndroidDocumentStore(context, Dispatchers.IO)
    private val written = mutableListOf<Uri>()

    @After
    fun tearDown() {
        written.forEach { runCatching { forget(it) } }
        written.clear()
    }

    /**
     * A real document, of the kind the picker would hand back.
     *
     * MediaStore's Downloads collection arrived in Android 10, and minSdk here is Android 8, so on
     * older releases this falls back to a file the resolver opens directly. Both go through
     * ContentResolver, which is the code under test.
     */
    private fun newDocument(name: String): UriDocumentHandle {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            requireNotNull(
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
            ) { "The test could not create a document to write to" }
        } else {
            Uri.fromFile(File(context.cacheDir, name).apply { createNewFile() })
        }
        written += uri
        return UriDocumentHandle(uri)
    }

    private fun forget(uri: Uri) {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() }
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun everyByteWrittenComesBackUnchanged() = runTest {
        // The awkward ones: a rupee sign, an em dash, an emoji, a quote, a newline. A backup carries
        // whatever the user typed into a note, and a wrong charset would mangle it silently.
        val content = """{"note":"₹1,200 — \"heavy\" 💪\nsecond line","n":1}"""
        val handle = newDocument("forgelog-encoding-${System.nanoTime()}.json")

        assertTrue(store.writeText(handle, content).isSuccess)

        assertEquals(content, store.readText(handle).getOrThrow())
    }

    @Test
    fun aShorterExportDoesNotLeaveTheEndOfTheLongerOneBehind() = runTest {
        val handle = newDocument("forgelog-truncate-${System.nanoTime()}.json")
        val long = """{"sessions":[${"{\"id\":1},".repeat(500)}{"id":0}]}"""
        val short = """{"sessions":[]}"""

        store.writeText(handle, long).getOrThrow()
        store.writeText(handle, short).getOrThrow()

        // Without the "wt" mode this is the tail of the first export, and the file no longer parses.
        assertEquals(short, store.readText(handle).getOrThrow())
    }

    @Test
    fun aBackupTheSizeOfYearsOfTrainingSurvivesTheRoundTrip() = runTest {
        val handle = newDocument("forgelog-large-${System.nanoTime()}.json")
        // ~4 MB: comfortably more than a decade of daily training, and past any stream buffer.
        val content = buildString {
            append("""{"setLogs":[""")
            repeat(40_000) { append("""{"id":$it,"notes":"felt strong"},""") }
            append("""{"id":-1}]}""")
        }

        store.writeText(handle, content).getOrThrow()

        assertEquals(content.length, store.readText(handle).getOrThrow().length)
        assertEquals(content, store.readText(handle).getOrThrow())
    }

    @Test
    fun aDocumentThatIsGoneFailsRatherThanCrashes() = runTest {
        val handle = newDocument("forgelog-missing-${System.nanoTime()}.json")
        store.writeText(handle, "{}").getOrThrow()
        forget(handle.uri)

        // A user can restore a backup, delete the file, then rotate the screen. Nothing here may
        // throw out of the coroutine.
        assertTrue(store.readText(handle).isFailure)
        if (handle.uri.scheme != "file") {
            // A document provider cannot resurrect a row it has dropped. A plain file can be
            // recreated by opening it, which is the fallback's behaviour, not the app's.
            assertTrue(store.writeText(handle, "{}").isFailure)
        }
    }

    @Test
    fun aUriFromNowhereFailsRatherThanCrashes() = runTest {
        val handle = UriDocumentHandle(Uri.parse("content://dev.happyc0der.no.such.provider/42"))

        assertTrue(store.readText(handle).isFailure)
        assertTrue(store.writeText(handle, "{}").isFailure)
    }

    @Test
    fun aHandleFromSomewhereElseIsRefused() = runTest {
        val foreign = object : DocumentHandle {}

        assertTrue(store.readText(foreign).isFailure)
        assertTrue(store.writeText(foreign, "{}").isFailure)
    }

    @Test
    fun aFileOfRandomBytesIsReadWithoutThrowing() = runTest {
        // What the user gets when they pick a photo instead of their backup. It must reach the
        // importer as text so the importer can say the file is not a backup.
        val handle = newDocument("forgelog-binary-${System.nanoTime()}.json")
        val bytes = ByteArray(4096) { (it * 31 and 0xFF).toByte() }
        context.contentResolver.openOutputStream(handle.uri, "wt")!!.use { it.write(bytes) }

        val read = store.readText(handle)

        assertTrue(read.isSuccess)
        assertFalse(read.getOrThrow().isEmpty())
    }
}
