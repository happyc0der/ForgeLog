package dev.happyc0der.forgelog.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one architectural rule worth enforcing mechanically: the domain layer stays pure.
 *
 * It is a test rather than a lint rule or a CI script so it runs on every
 * `./gradlew testDebugUnitTest` with no extra setup. Keeping `domain/` free of Android and of the
 * outer layers is what keeps the business rules unit-testable without a device, and it is also
 * what would make a future `:core:domain` module extraction a move rather than a rewrite.
 */
class LayerBoundaryTest {

    private val sourceRoot = File("src/main/java/dev/happyc0der/forgelog")
    private val domainRoot = File(sourceRoot, "domain")

    @Test
    fun `the source tree is where this test thinks it is`() {
        // A silent false pass is worse than no test: if the layout moves, fail loudly here.
        assertTrue("Missing $domainRoot — fix this test's path", domainRoot.isDirectory)
        assertTrue("Expected domain sources", kotlinFiles(domainRoot).isNotEmpty())
    }

    @Test
    fun `domain does not depend on data or ui`() {
        val offenders = kotlinFiles(domainRoot).flatMap { file ->
            importsIn(file)
                .filter { it.startsWith("dev.happyc0der.forgelog.data.") ||
                    it.startsWith("dev.happyc0der.forgelog.ui.") }
                .map { "${file.relativeToSourceRoot()} imports $it" }
        }
        assertTrue(
            "Domain must not depend on outer layers:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `domain does not depend on the Android framework`() {
        val allowed = setOf("androidx.annotation.")
        val offenders = kotlinFiles(domainRoot).flatMap { file ->
            importsIn(file)
                .filter { import ->
                    (import.startsWith("android.") || import.startsWith("androidx.")) &&
                        allowed.none { import.startsWith(it) }
                }
                .map { "${file.relativeToSourceRoot()} imports $it" }
        }
        assertTrue(
            "Domain must stay pure Kotlin so it is testable off-device:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `repository implementations live in data, not domain`() {
        val offenders = kotlinFiles(domainRoot)
            .filter { it.name.endsWith("RepositoryImpl.kt") }
            .map { it.relativeToSourceRoot() }
        assertTrue("Implementations belong in data/: $offenders", offenders.isEmpty())
    }

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun importsIn(file: File): List<String> = file.readLines()
        .mapNotNull { line ->
            line.trim().removePrefix("import ").takeIf { line.trimStart().startsWith("import ") }
        }
        .map { it.substringBefore(" as ").trim() }

    private fun File.relativeToSourceRoot(): String = relativeTo(sourceRoot).path
}
