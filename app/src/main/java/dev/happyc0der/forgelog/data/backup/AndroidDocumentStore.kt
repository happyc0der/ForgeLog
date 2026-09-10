package dev.happyc0der.forgelog.data.backup

import android.content.Context
import android.net.Uri
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A document the user picked through the Storage Access Framework. */
data class UriDocumentHandle(val uri: Uri) : DocumentHandle

@Singleton
class AndroidDocumentStore @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentStore {

    override suspend fun readText(handle: DocumentHandle): Result<String> =
        withContext(ioDispatcher) {
            val uri = (handle as? UriDocumentHandle)?.uri
                ?: return@withContext Result.failure(IOException("Unsupported document"))
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.reader().readText()
                } ?: throw IOException("Could not open the selected file")
            }
        }

    /**
     * Truncates before writing. Without "wt" the platform keeps whatever was in an existing file
     * beyond the new content's length, which would leave trailing garbage on a shorter export and
     * produce a file that no longer parses.
     */
    override suspend fun writeText(handle: DocumentHandle, content: String): Result<Unit> =
        withContext(ioDispatcher) {
            val uri = (handle as? UriDocumentHandle)?.uri
                ?: return@withContext Result.failure(IOException("Unsupported document"))
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.writer().use { writer -> writer.write(content) }
                } ?: throw IOException("Could not write to the selected file")
            }
        }
}
