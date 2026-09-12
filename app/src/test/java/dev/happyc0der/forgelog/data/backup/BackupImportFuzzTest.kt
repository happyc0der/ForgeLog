package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.backup.BackupCheck
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Whatever the user picks, the importer answers instead of crashing.
 *
 * The file comes from a system picker, so it can be anything on the phone: a photo, a half-written
 * download, someone else's export, a backup this build is too old to read. Every one of those has to
 * come back as a refusal with a reason. A crash here is the worst case in the app -- it lands on
 * someone who is restoring, which is to say someone who has already lost their data once.
 *
 * These are the cases a hand-written test does not think of: bytes flipped in the middle, the file
 * cut off partway, nesting deep enough to exhaust the stack, numbers past what a Double holds.
 */
class BackupImportFuzzTest {

    private val valid = BackupSerializer.encode(
        BackupEnvelope(
            appVersion = "1.0",
            databaseVersion = 3,
            exportedAtEpochMs = 1_700_000_000_000L,
            exercises = listOf(
                ExerciseBackup(id = 1, name = "Bench Press", category = "push", defaultUnit = "lb"),
            ),
            sessions = listOf(
                SessionBackup(id = 1, sessionName = "Push", startedAt = 1_000, status = "completed"),
            ),
            sessionExercises = listOf(
                SessionExerciseBackup(
                    id = 1,
                    sessionId = 1,
                    exerciseId = 1,
                    displayNameSnapshot = "Bench Press",
                ),
            ),
            setLogs = listOf(
                SetLogBackup(
                    id = 1,
                    sessionExerciseId = 1,
                    setNumber = 1,
                    setType = "working",
                    reps = 5,
                    weight = 185.0,
                    weightUnit = "lb",
                ),
            ),
        ),
    )

    /** Decoding answers; it never throws. What the answer is belongs to the tests around this one. */
    private fun answers(label: String, raw: String) {
        val result = try {
            BackupSerializer.decode(raw)
        } catch (throwable: Throwable) {
            throw AssertionError(
                "$label threw ${throwable::class.simpleName}: ${throwable.message}",
                throwable,
            )
        }
        assertNotNull("$label produced no answer", result)
    }

    @Test
    fun aFileCutOffAnywhereIsRefusedRatherThanThrown() {
        for (length in 0 until valid.length step 7) {
            answers("truncated to $length", valid.take(length))
        }
    }

    @Test
    fun bytesFlippedAnywhereAreRefusedRatherThanThrown() {
        val random = Random(20260912)
        repeat(600) { attempt ->
            val chars = valid.toCharArray()
            repeat(random.nextInt(1, 6)) {
                chars[random.nextInt(chars.size)] = random.nextInt(32, 127).toChar()
            }
            answers("mutation $attempt", String(chars))
        }
    }

    @Test
    fun aFileOfAnythingAtAllIsRefusedRatherThanThrown() {
        val random = Random(4242)
        listOf(
            "",
            " ",
            "\t\n\r",
            "null",
            "[]",
            "{}",
            "[[[[",
            "not json at all",
            "💪",
            "%PDF-1.4\n%âã",
            "<html><body>hi</body></html>",
            "id,name\n1,Bench Press\n",
            "{\"formatVersion\":",
            String(CharArray(200_000) { 'a' }),
            (1..2000).joinToString("") { "[" },
            "{\"formatVersion\":1,\"setLogs\":" + (1..2000).joinToString("") { "[" },
        ).forEachIndexed { index, raw -> answers("literal $index", raw) }

        repeat(300) { attempt ->
            val bytes = ByteArray(random.nextInt(0, 4096)) { random.nextInt(256).toByte() }
            answers("random bytes $attempt", String(bytes, Charsets.UTF_8))
        }
    }

    /** Structurally valid JSON, with values chosen to be awkward rather than malformed. */
    @Test
    fun awkwardValuesAreAnsweredRatherThanThrown() {
        val set = "{\"id\":1,\"sessionExerciseId\":1,\"setNumber\":1,\"setType\":\"working\",\"weightUnit\":\"lb\""
        listOf(
            "{\"formatVersion\":1,\"setLogs\":[$set,\"weight\":1e400}]}",
            "{\"formatVersion\":1,\"setLogs\":[$set,\"weight\":-1e400}]}",
            "{\"formatVersion\":1,\"setLogs\":[$set,\"weight\":-5}]}",
            "{\"formatVersion\":1,\"setLogs\":[$set,\"reps\":2147483647}]}",
            "{\"formatVersion\":1,\"sessions\":[{\"id\":9223372036854775807,\"sessionName\":\"x\"," +
                "\"startedAt\":9223372036854775807,\"status\":\"completed\"}]}",
            "{\"formatVersion\":1,\"sessions\":[{\"id\":-9223372036854775808,\"sessionName\":\"x\"," +
                "\"startedAt\":-1,\"status\":\"completed\"}]}",
            "{\"formatVersion\":2147483647}",
            "{\"formatVersion\":-1}",
            "{\"formatVersion\":1,\"exercises\":[{\"id\":1,\"name\":\" \",\"category\":\"push\"," +
                "\"defaultUnit\":\"lb\"}]}",
            "{\"formatVersion\":1,\"exercises\":\"not a list\"}",
            "{\"formatVersion\":\"one\"}",
            "{\"formatVersion\":1,\"exercises\":[null]}",
            "{\"formatVersion\":1,\"exercises\":[{\"id\":1,\"name\":null,\"category\":\"push\"," +
                "\"defaultUnit\":\"lb\"}]}",
        ).forEachIndexed { index, raw -> answers("awkward $index", raw) }
    }

    /** A file the importer accepts is one the app could have written: re-encoding it works. */
    @Test
    fun anythingAcceptedCanBeWrittenBackOut() {
        val random = Random(99)
        repeat(600) {
            val chars = valid.toCharArray()
            repeat(random.nextInt(1, 4)) {
                chars[random.nextInt(chars.size)] = random.nextInt(32, 127).toChar()
            }
            val result = BackupSerializer.decode(String(chars))
            if (result is BackupCheck.Valid) {
                val again = BackupSerializer.decode(BackupSerializer.encode(result.value))
                assertTrue("a file it accepted could not be written back out", again is BackupCheck.Valid)
            }
        }
    }
}
