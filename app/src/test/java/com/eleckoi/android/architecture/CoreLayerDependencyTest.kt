package com.eleckoi.android.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLayerDependencyTest {
    @Test
    fun `core packages never depend back on outer packages`() {
        val sourceRoot = sourceRoot()
        val rules = listOf(
            LayerRule(
                packageName = "foundation",
                forbiddenImport = Regex(
                    """^import com\.eleckoi\.android\.(app|engine|feature|sdk)\.""",
                ),
            ),
            LayerRule(
                packageName = "engine",
                forbiddenImport = Regex(
                    """^import com\.eleckoi\.android\.(app|feature|sdk)\.""",
                ),
            ),
            LayerRule(
                packageName = "sdk",
                forbiddenImport = Regex(
                    """^import com\.eleckoi\.android\.(app|feature)\.""",
                ),
            ),
            LayerRule(
                packageName = "feature",
                forbiddenImport = Regex(
                    """^import com\.eleckoi\.android\.app\.""",
                ),
            ),
        )
        val violations = rules.flatMap { rule ->
            File(sourceRoot, rule.packageName)
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .flatMap { file ->
                    file.useLines { lines ->
                        lines.mapIndexedNotNull { index, line ->
                            line.trim().takeIf(rule.forbiddenImport::matches)?.let { importLine ->
                                "${file.relativeTo(sourceRoot).invariantSeparatorsPath}:${index + 1} $importLine"
                            }
                        }.toList().asSequence()
                    }
                }
                .toList()
        }

        assertTrue(
            "Core dependency direction was violated:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `application services never bridge suspend persistence with runBlocking`() {
        val serviceRoot = File(sourceRoot(), "app/service")
        val violations = serviceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file ->
                file.useLines { lines -> lines.any { line -> "runBlocking" in line } }
            }
            .map { file -> file.relativeTo(sourceRoot()).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Application services must stay suspend end to end:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun sourceRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return listOf(
            File(workingDirectory, "src/main/java/com/eleckoi/android"),
            File(workingDirectory, "app/src/main/java/com/eleckoi/android"),
        ).firstOrNull(File::isDirectory)
            ?: error("Cannot locate ElecKoi source root from $workingDirectory")
    }

    private data class LayerRule(
        val packageName: String,
        val forbiddenImport: Regex,
    )
}
