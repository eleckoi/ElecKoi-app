import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency

val allowedProjectDependencies = mapOf(
    ":foundation:design" to emptySet(),
    ":foundation:diagnostics" to emptySet(),
    ":foundation:network" to emptySet(),
    ":foundation:paging" to emptySet(),
    ":foundation:serialization" to emptySet(),
    ":foundation:storage" to emptySet(),
    ":engine" to setOf(
        ":foundation:network",
        ":foundation:serialization",
        ":foundation:storage",
    ),
    ":sdk:author" to setOf(
        ":engine",
        ":foundation:serialization",
        ":foundation:storage",
    ),
    ":compatibility:mvu" to setOf(
        ":engine",
        ":foundation:storage",
    ),
    ":feature:appfont" to emptySet(),
    ":feature:characters" to setOf(
        ":compatibility:mvu",
        ":engine",
        ":feature:modelconfig",
        ":foundation:design",
        ":foundation:serialization",
        ":foundation:storage",
    ),
    ":feature:conversation" to setOf(
        ":compatibility:mvu",
        ":engine",
        ":feature:appfont",
        ":feature:characters",
        ":feature:modelconfig",
        ":feature:preferences",
        ":sdk:author",
        ":foundation:design",
        ":foundation:diagnostics",
        ":foundation:network",
        ":foundation:paging",
        ":foundation:serialization",
        ":foundation:storage",
    ),
    ":feature:modelconfig" to setOf(
        ":engine",
        ":foundation:design",
    ),
    ":feature:preferences" to setOf(
        ":foundation:design",
        ":foundation:serialization",
    ),
    ":feature:settings" to setOf(
        ":engine",
        ":feature:studio",
        ":feature:appfont",
        ":feature:characters",
        ":feature:conversation",
        ":feature:modelconfig",
        ":feature:preferences",
        ":foundation:design",
        ":foundation:diagnostics",
        ":foundation:storage",
    ),
    ":feature:studio" to setOf(
        ":compatibility:mvu",
        ":engine",
        ":feature:appfont",
        ":feature:characters",
        ":feature:conversation",
        ":feature:modelconfig",
        ":feature:preferences",
        ":sdk:author",
        ":foundation:design",
        ":foundation:diagnostics",
        ":foundation:network",
        ":foundation:paging",
        ":foundation:serialization",
        ":foundation:storage",
    ),
    ":app" to setOf(
        ":compatibility:mvu",
        ":feature:studio",
        ":feature:appfont",
        ":feature:characters",
        ":feature:conversation",
        ":feature:modelconfig",
        ":feature:preferences",
        ":feature:settings",
        ":engine",
        ":sdk:author",
        ":foundation:design",
        ":foundation:diagnostics",
        ":foundation:network",
        ":foundation:paging",
        ":foundation:serialization",
        ":foundation:storage",
    ),
)

val ownedPackages = mapOf(
    ":foundation:design" to setOf("com.eleckoi.android.foundation.design"),
    ":foundation:diagnostics" to setOf("com.eleckoi.android.foundation.diagnostics"),
    ":foundation:network" to setOf("com.eleckoi.android.foundation.network"),
    ":foundation:paging" to setOf("com.eleckoi.android.foundation.paging"),
    ":foundation:serialization" to setOf("com.eleckoi.android.foundation.serialization"),
    ":foundation:storage" to setOf("com.eleckoi.android.foundation.storage"),
    ":engine" to setOf("com.eleckoi.android.engine"),
    ":sdk:author" to setOf("com.eleckoi.android.sdk.author"),
    ":compatibility:mvu" to setOf("com.eleckoi.android.compatibility.mvu"),
    ":feature:appfont" to setOf("com.eleckoi.android.feature.appfont"),
    ":feature:characters" to setOf("com.eleckoi.android.feature.characters"),
    ":feature:conversation" to setOf(
        "com.eleckoi.android.feature.chat",
        "com.eleckoi.android.feature.conversation",
    ),
    ":feature:modelconfig" to setOf("com.eleckoi.android.feature.modelconfig"),
    ":feature:preferences" to setOf("com.eleckoi.android.feature.preferences"),
    ":feature:settings" to setOf("com.eleckoi.android.feature.settings"),
    ":feature:studio" to setOf("com.eleckoi.android.feature.studio"),
    ":app" to setOf("com.eleckoi.android.app"),
)

val isolatedMvuProtocolTokens = listOf(
    "StatusPlaceHolderImpl",
    "format_message_variable",
    "get_message_variable",
    "getAllVariables",
    "waitGlobalInitialized",
    "Mvu.getMvuData",
    "mag_variable_",
    "[mvu_update]",
    "[initvar]",
)

// Package-level dependency contract for the Studio domain in :feature:studio. Chat and reusable
// Agent conversation presentation are owned by :feature:conversation, so this boundary is now
// directional: Studio may consume conversation APIs, never the reverse.
val allowedFeatureDomainDependencies = mapOf(
    "studio" to setOf("characters", "chat", "conversation", "modelconfig", "preferences"),
)

data class SourceSizeReviewNote(
    val reason: String,
)

data class ProductionSourceSize(
    val path: String,
    val lines: Int,
    val bytes: Long,
)

// Source size is a review signal, not an architecture decision. The thresholds below only
// determine which files are included in the report; they must not fail the build by themselves.
val sourceSizeReviewLines = 500
val sourceSizeReviewBytes = 24L * 1_024L

// These notes provide context for known large or byte-dense files. They are not exemptions,
// ratchets, or approval to split; the linked guidance document defines the human review.
val sourceSizeReviewNotes = mapOf(
    "engine/src/main/java/com/eleckoi/android/engine/workspace/storage/CreatorWorkspaceRepository.kt" to
        SourceSizeReviewNote(
            reason = "atomic owner for workspace catalog, project files, media, and checkpoints",
        ),
    "feature/studio/src/main/java/com/eleckoi/android/feature/studio/ui/assistant/session/CreationAgentSessionCoordinator.kt" to
        SourceSizeReviewNote(
            reason = "single owner of the volatile Agent turn and shutdown lifecycle",
        ),
    "foundation/design/src/main/java/com/eleckoi/android/foundation/design/components/ElecKoiIconPaths.kt" to
        SourceSizeReviewNote(
            reason = "byte-dense declarative SVG path catalog without control flow",
        ),
)

gradle.projectsEvaluated {
    allowedProjectDependencies.forEach { (sourcePath, allowedTargets) ->
        val sourceProject = project(sourcePath)
        val illegalDependencies = sourceProject.configurations
            .flatMap { configuration ->
                configuration.dependencies
                    .withType(ProjectDependency::class.java)
                    .map { dependency -> configuration.name to dependency.path }
            }
            .filterNot { (_, targetPath) -> targetPath == sourcePath || targetPath in allowedTargets }
            .distinct()

        if (illegalDependencies.isNotEmpty()) {
            val details = illegalDependencies.joinToString(separator = "\n") { (configuration, target) ->
                "  $sourcePath [$configuration] -> $target"
            }
            throw GradleException(
                "Forbidden module dependencies detected:\n$details\n" +
                    "Update the architecture contract only when the ownership model intentionally changes.",
            )
        }
    }
}

val verifyArchitecture by tasks.registering {
    group = "verification"
    description = "Verifies production package ownership for every Gradle module."

    val sourceRoots = ownedPackages.keys.flatMap { modulePath ->
        val module = project(modulePath)
        listOf(
            module.layout.projectDirectory.dir("src/main/java"),
            module.layout.projectDirectory.dir("src/main/kotlin"),
        )
    }
    inputs.files(sourceRoots)

    doLast {
        val packagePattern = Regex("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)")
        val violations = mutableListOf<String>()

        ownedPackages.forEach { (modulePath, packagePrefixes) ->
            val module = project(modulePath)
            listOf("src/main/java", "src/main/kotlin").forEach { relativeRoot ->
                val root = module.file(relativeRoot)
                if (!root.exists()) return@forEach

                root.walkTopDown()
                    .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
                    .forEach { sourceFile ->
                        val declaredPackage = packagePattern.find(sourceFile.readText())?.groupValues?.get(1)
                        when {
                            declaredPackage == null -> violations +=
                                "$modulePath: ${sourceFile.relativeTo(module.projectDir)} has no package declaration"

                            packagePrefixes.none { packagePrefix ->
                                declaredPackage == packagePrefix || declaredPackage.startsWith("$packagePrefix.")
                            } ->
                                violations +=
                                    "$modulePath: ${sourceFile.relativeTo(module.projectDir)} declares $declaredPackage; " +
                                    "expected one of ${packagePrefixes.sorted().joinToString()}"
                }
            }
        }

        val featurePackagePrefix = "com.eleckoi.android.feature"
        val featureImportPattern = Regex(
            "(?m)^\\s*import\\s+com\\.eleckoi\\.android\\.feature\\.([A-Za-z0-9_]+)(?:\\.|$)",
        )
        val studioModule = project(":feature:studio")
        listOf(
            "src/main/java/com/eleckoi/android/feature/studio",
            "src/main/kotlin/com/eleckoi/android/feature/studio",
        ).forEach { relativeRoot ->
            val featureRoot = studioModule.file(relativeRoot)
            if (!featureRoot.exists()) return@forEach

            featureRoot.walkTopDown()
                .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
                .forEach { sourceFile ->
                    val relativePath = sourceFile.relativeTo(featureRoot).invariantSeparatorsPath
                    val sourceDomain = "studio"
                    val source = sourceFile.readText()
                    val declaredPackage = packagePattern.find(source)?.groupValues?.get(1)
                    val expectedDomainPackage = "$featurePackagePrefix.$sourceDomain"

                    if (sourceDomain !in allowedFeatureDomainDependencies) {
                        violations +=
                            ":feature:studio: $relativePath belongs to unregistered feature domain '$sourceDomain'"
                    }
                    if (
                        declaredPackage != expectedDomainPackage &&
                        declaredPackage?.startsWith("$expectedDomainPackage.") != true
                    ) {
                        violations +=
                            ":feature:studio: $relativePath declares $declaredPackage; its top-level directory owns " +
                                "$expectedDomainPackage"
                    }

                    val allowedTargets = allowedFeatureDomainDependencies[sourceDomain].orEmpty()
                    featureImportPattern.findAll(source)
                        .map { match -> match.groupValues[1] }
                        .filter { targetDomain -> targetDomain != sourceDomain }
                        .distinct()
                        .filterNot { targetDomain -> targetDomain in allowedTargets }
                        .forEach { targetDomain ->
                            violations +=
                                ":feature:studio: $relativePath imports forbidden domain $sourceDomain -> $targetDomain"
                        }
                }
        }
        }

        ownedPackages.keys
            .filterNot { modulePath -> modulePath == ":compatibility:mvu" }
            .forEach { modulePath ->
                val module = project(modulePath)
                listOf("src/main/java", "src/main/kotlin").forEach { relativeRoot ->
                    val root = module.file(relativeRoot)
                    if (!root.exists()) return@forEach
                    root.walkTopDown()
                        .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
                        .forEach { sourceFile ->
                            val source = sourceFile.readText()
                            isolatedMvuProtocolTokens.forEach { token ->
                                if (token in source) {
                                    violations +=
                                        "$modulePath: ${sourceFile.relativeTo(module.projectDir)} contains " +
                                            "isolated MVU protocol token '$token'"
                                }
                            }
                        }
                }
            }

        val productionSources = ownedPackages.keys
            .flatMap { modulePath ->
                val module = project(modulePath)
                listOf("src/main/java", "src/main/kotlin").flatMap { relativeRoot ->
                    val root = module.file(relativeRoot)
                    if (!root.exists()) {
                        emptyList()
                    } else {
                        root.walkTopDown()
                            .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
                            .toList()
                    }
                }
            }
            .distinctBy { sourceFile -> sourceFile.canonicalPath }

        val sourceSizes = productionSources.map { sourceFile ->
            ProductionSourceSize(
                path = sourceFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
                lines = sourceFile.useLines { lines -> lines.count() },
                bytes = sourceFile.length(),
            )
        }

        val softHotspots = sourceSizes
            .filter { size -> size.lines > sourceSizeReviewLines || size.bytes > sourceSizeReviewBytes }
            .sortedWith(
                compareByDescending<ProductionSourceSize> { it.lines }
                    .thenByDescending { it.bytes },
            )
        val sizeReport = rootProject.layout.buildDirectory
            .file("reports/architecture/source-size-report.txt")
            .get()
            .asFile
        sizeReport.parentFile.mkdirs()
        sizeReport.writeText(
            buildString {
                appendLine(
                    "Production source-size review threshold: >$sourceSizeReviewLines lines " +
                        "or >$sourceSizeReviewBytes bytes",
                )
                appendLine("Source size is advisory only; this task does not fail on line or byte count.")
                appendLine("Read docs/architecture/source-size-guidance.md before deciding whether to split a file.")
                appendLine()
                softHotspots.forEach { size ->
                    val reason = sourceSizeReviewNotes[size.path]?.reason
                        ?: "no recorded context; review cohesion, coupling, and file organization"
                    appendLine("${size.lines} lines | ${size.bytes} bytes | ${size.path} | $reason")
                }
            },
        )
        logger.lifecycle(
            "Architecture source-size report: ${sizeReport.relativeTo(rootProject.projectDir)} " +
                "(${softHotspots.size} review hotspots; see docs/architecture/source-size-guidance.md)",
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Production source ownership violations:\n" + violations.joinToString("\n") { "  $it" },
            )
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitecture"))
    }
}
