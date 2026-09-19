import groovy.json.JsonSlurper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class StageRustJniLibrary @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputLibrary: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        fileSystemOperations.sync {
            from(inputLibrary)
            into(outputDirectory.dir("arm64-v8a"))
        }
    }
}

plugins {
    id("eleckoi.android.application")
    id("eleckoi.android.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val elecKoiNdkVersion = "27.2.12479018"
val elecKoiPackagedAbis = setOf("arm64-v8a")
val generatedEmbeddedRuntimeAssets = layout.buildDirectory.dir("generated/embeddedRuntimeAssets")
val roleplayVirtualAsset = file("src/main/assets/web-runtime/tanstack-virtual-core-3.17.8.min.js")
val roleplayVirtualAssetSha256 = "79af7c30c1855800cf60f0675a3e9f6affbaa038eb950d270647b28597772fc4"

fun releaseSecret(name: String): String? = providers.environmentVariable(name)
    .orElse(providers.gradleProperty(name))
    .orNull
    ?.trim()
    ?.takeIf(String::isNotBlank)

val releaseSigningValues = mapOf(
    "ELECKOI_RELEASE_STORE_FILE" to releaseSecret("ELECKOI_RELEASE_STORE_FILE"),
    "ELECKOI_RELEASE_STORE_PASSWORD" to releaseSecret("ELECKOI_RELEASE_STORE_PASSWORD"),
    "ELECKOI_RELEASE_KEY_ALIAS" to releaseSecret("ELECKOI_RELEASE_KEY_ALIAS"),
    "ELECKOI_RELEASE_KEY_PASSWORD" to releaseSecret("ELECKOI_RELEASE_KEY_PASSWORD"),
)
val releaseSigningConfigured = releaseSigningValues.values.all { it != null }

val verifyRoleplayVirtualAsset by tasks.registering {
    group = "verification"
    description = "Verifies the pinned TanStack Virtual browser bundle used by the roleplay WebView."
    inputs.file(roleplayVirtualAsset)

    doLast {
        check(roleplayVirtualAsset.isFile) {
            "Missing $roleplayVirtualAsset. Run npm ci and npm run build in web-runtime/roleplay-virtual."
        }
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(roleplayVirtualAsset.readBytes())
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        check(actualSha256 == roleplayVirtualAssetSha256) {
            "TanStack Virtual asset SHA-256 mismatch: $actualSha256"
        }
    }
}

ksp {
    // Room owns the schema in foundation/storage; this app module does not define a second
    // database schema.
    arg("room.schemaLocation", file("schemas").absolutePath)
}

abstract class StageEmbeddedRuntimeAssets @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val catalog = JsonSlurper().parse(catalogFile.get().asFile) as? Map<*, *>
            ?: error("Runtime catalog is not a JSON object: ${catalogFile.get().asFile}")
        val rootfs = catalog["rootfs"] as? Map<*, *>
            ?: error("Runtime catalog is missing rootfs")
        val harnesses = catalog["harnesses"] as? Map<*, *>
            ?: error("Runtime catalog is missing harnesses")
        val assetPaths = buildList {
            add(rootfs["assetPath"] as? String ?: error("Runtime catalog rootfs has no assetPath"))
            harnesses.forEach { (id, rawSpec) ->
                val spec = rawSpec as? Map<*, *>
                    ?: error("Runtime catalog harness $id is not an object")
                add(spec["assetPath"] as? String ?: error("Runtime catalog harness $id has no assetPath"))
            }
        }
        fileSystemOperations.sync {
            from(bundleDirectory) {
                include(assetPaths)
            }
            into(outputDirectory)
        }
    }
}

abstract class InvalidatePackagedApksOnRuntimeCatalogChange @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFile: RegularFileProperty

    @get:Internal
    abstract val apkOutputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val markerFile: RegularFileProperty

    @TaskAction
    fun invalidate() {
        fileSystemOperations.delete {
            delete(apkOutputDirectory)
        }
        val marker = markerFile.get().asFile
        marker.parentFile.mkdirs()
        marker.writeText(catalogFile.get().asFile.readText())
    }
}

android {
    namespace = "com.eleckoi.android"

    ndkVersion = elecKoiNdkVersion

    defaultConfig {
        applicationId = "com.eleckoi.android"
        targetSdk = 37
        versionCode = 11
        versionName = "0.1.10"
        ndk {
            // The embedded workspace runtime is arm64-only; keep the Rust Markdown bridge and
            // packaged runtime on the same explicit ABI instead of producing unusable APK slices.
            abiFilters += elecKoiPackagedAbis
        }
    }

    packaging {
        // PRoot and its loader are executable ELF files intentionally shipped through
        // nativeLibraryDir so Android 10+ never executes writable app-data files.
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        // Runtime payloads are already gzip-compressed. Store them as raw APK entries so the
        // installer can stream directly from AssetManager without a second on-disk copy.
        noCompress += "egruntime"
    }

    sourceSets.getByName("main").assets.directories.addAll(
        listOf(
            rootProject.file("runtime/catalog").path,
            rootProject.file("runtime/licenses").path,
        ),
    )

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(checkNotNull(releaseSigningValues["ELECKOI_RELEASE_STORE_FILE"]))
                storePassword = checkNotNull(releaseSigningValues["ELECKOI_RELEASE_STORE_PASSWORD"])
                keyAlias = checkNotNull(releaseSigningValues["ELECKOI_RELEASE_KEY_ALIAS"])
                keyPassword = checkNotNull(releaseSigningValues["ELECKOI_RELEASE_KEY_PASSWORD"])
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // The private key and credentials live outside source control. Release tasks below
            // reject missing configuration instead of silently publishing a debug-signed APK.
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
        }
    }

}

val verifyReleaseSigningConfigured by tasks.registering {
    group = "verification"
    description = "Rejects Release packaging unless the private ElecKoi signing key is configured."
    doLast {
        val missing = releaseSigningValues.filterValues { it == null }.keys
        check(missing.isEmpty()) {
            "Release signing is not configured. Set: ${missing.joinToString()}. " +
                "Never commit the .jks file or its passwords."
        }
        val keyStore = file(checkNotNull(releaseSigningValues["ELECKOI_RELEASE_STORE_FILE"]))
        check(keyStore.isFile) {
            "ELECKOI_RELEASE_STORE_FILE does not point to a readable .jks file: $keyStore"
        }
    }
}

val rustMarkdownManifest = file("src/main/rust/grok_markdown_android/Cargo.toml")
val rustMarkdownTarget = layout.buildDirectory.dir("rust/grok-markdown")
val generatedRustJniRoot = layout.buildDirectory.dir("generated/rustJniLibs")

fun resolveAndroidSdkDirectory(): File {
    val environmentPath = sequenceOf("ANDROID_SDK_ROOT", "ANDROID_HOME")
        .mapNotNull(System::getenv)
        .firstOrNull(String::isNotBlank)
    if (environmentPath != null) return file(environmentPath)

    val localProperties = rootProject.file("local.properties")
    if (localProperties.isFile) {
        val properties = Properties().apply {
            localProperties.inputStream().use(::load)
        }
        properties.getProperty("sdk.dir")?.takeIf(String::isNotBlank)?.let(::file)?.let {
            return it
        }
    }
    error("Android SDK not found. Set ANDROID_SDK_ROOT or sdk.dir in local.properties.")
}

val buildGrokMarkdownRustArm64 by tasks.registering(Exec::class) {
    group = "build"
    description = "Cross-compiles the Grok-aligned headless Markdown bridge for Android arm64."
    inputs.file(rustMarkdownManifest)
    inputs.file(file("src/main/rust/grok_markdown_android/Cargo.lock"))
    inputs.dir(file("src/main/rust/grok_markdown_android/src"))
    inputs.dir(file("src/main/rust/grok_markdown_android/assets"))
    inputs.files(
        fileTree("src/main/rust/vendor") {
            exclude("**/target/**", "**/Cargo.lock")
        },
    )
    outputs.file(
        rustMarkdownTarget.map {
            it.file("aarch64-linux-android/release/libeleckoi_markdown_rust.so")
        },
    )

    doFirst {
        val toolchainBin = resolveAndroidSdkDirectory()
            .resolve("ndk/$elecKoiNdkVersion/toolchains/llvm/prebuilt/windows-x86_64/bin")
        val linker = toolchainBin.resolve("aarch64-linux-android27-clang.cmd")
        val cxx = toolchainBin.resolve("aarch64-linux-android27-clang++.cmd")
        val archiver = toolchainBin.resolve("llvm-ar.exe")
        check(linker.isFile) { "Android NDK linker not found: $linker" }
        check(cxx.isFile) { "Android NDK C++ compiler not found: $cxx" }
        check(archiver.isFile) { "Android NDK archiver not found: $archiver" }
        environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", linker.absolutePath)
        // Cargo's final linker setting is not inherited by cc-rs build scripts. Bind the same
        // pinned NDK toolchain explicitly so native dependencies such as onig_sys cannot fall
        // back to a host compiler or a non-API-qualified `aarch64-linux-android-clang` binary.
        environment("CC_aarch64_linux_android", linker.absolutePath)
        environment("CXX_aarch64_linux_android", cxx.absolutePath)
        environment("AR_aarch64_linux_android", archiver.absolutePath)
        environment(
            "CARGO_ENCODED_RUSTFLAGS",
            listOf(
                "-Clink-arg=-Wl,-z,max-page-size=16384",
                "-Clink-arg=-Wl,-z,common-page-size=16384",
            ).joinToString("\u001f"),
        )
        commandLine(
            "cargo",
            "build",
            "--manifest-path",
            rustMarkdownManifest.absolutePath,
            "--target",
            "aarch64-linux-android",
            "--release",
            "--target-dir",
            rustMarkdownTarget.get().asFile.absolutePath,
            "--locked",
        )
    }
}

val stageGrokMarkdownRustArm64 by tasks.registering(StageRustJniLibrary::class) {
    dependsOn(buildGrokMarkdownRustArm64)
    inputLibrary.set(
        rustMarkdownTarget.map {
            it.file("aarch64-linux-android/release/libeleckoi_markdown_rust.so")
        },
    )
    outputDirectory.set(generatedRustJniRoot)
}

androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(stageGrokMarkdownRustArm64) {
        it.outputDirectory
    }
}

dependencies {
    implementation(project(":compatibility:mvu"))
    implementation(project(":feature:studio"))
    implementation(project(":feature:appfont"))
    implementation(project(":feature:characters"))
    implementation(project(":feature:conversation"))
    implementation(project(":feature:modelconfig"))
    implementation(project(":feature:preferences"))
    implementation(project(":feature:settings"))
    implementation(project(":engine"))
    implementation(project(":sdk:author"))
    implementation(project(":foundation:design"))
    implementation(project(":foundation:diagnostics"))
    implementation(project(":foundation:network"))
    implementation(project(":foundation:paging"))
    implementation(project(":foundation:serialization"))
    implementation(project(":foundation:storage"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // A Kotlin port of Google's material-color-utilities: the Celebi quantizer, the Score ranking,
    // HCT and the dynamic schemes — the same algorithms Android runs to theme itself from a
    // wallpaper. Preferred over MDC-Android, which carries the whole View-system component library
    // along with it for the sake of the same few classes.
    implementation("com.materialkolor:material-color-utilities:2.0.2")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("androidx.navigation3:navigation3-ui:1.1.4")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-paging:2.8.4")
    implementation("androidx.paging:paging-runtime:3.5.0")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("io.github.huarangmeng:latex-renderer-android:1.4.7")
    testImplementation("org.json:json:20240303")
}

val runtimeCatalogFile = rootProject.file("runtime/catalog/runtime-catalog.json")
val runtimeBundleDirectory = rootProject.file("runtime/bundles")

val verifyEmbeddedRuntimeAssets by tasks.registering {
    group = "verification"
    description = "Verifies the Ubuntu and Agent Harness APK assets against the pinned runtime catalog."

    inputs.file(runtimeCatalogFile)
    inputs.dir(runtimeBundleDirectory)

    doLast {
        val catalog = JsonSlurper().parse(runtimeCatalogFile) as? Map<*, *>
            ?: error("Runtime catalog is not a JSON object: $runtimeCatalogFile")
        val rootfs = catalog["rootfs"] as? Map<*, *>
            ?: error("Runtime catalog is missing rootfs")
        val harnesses = catalog["harnesses"] as? Map<*, *>
            ?: error("Runtime catalog is missing harnesses")
        val components = buildList<Pair<String, Map<*, *>>> {
            add("rootfs" to rootfs)
            harnesses.forEach { (id, rawSpec) ->
                add(
                    "harness:$id" to (rawSpec as? Map<*, *>
                        ?: error("Runtime catalog harness $id is not an object")),
                )
            }
        }
        components.forEach { (section, spec) ->
            val assetPath = spec["assetPath"] as? String
                ?: error("Runtime catalog $section has no assetPath")
            val expectedSha256 = spec["sha256"] as? String
                ?: error("Runtime catalog $section has no sha256")
            val byteLimit = (spec["archiveBytesLimit"] as? Number)?.toLong()
                ?: error("Runtime catalog $section has no archiveBytesLimit")
            val asset = runtimeBundleDirectory.resolve(assetPath)
            check(asset.isFile) {
                "Missing embedded runtime asset $asset. Run runtime/prepare-embedded-runtime.ps1."
            }
            check(asset.length() <= byteLimit) {
                "Embedded runtime asset exceeds its catalog byte limit: $asset"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            asset.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualSha256 = digest.digest().joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            check(actualSha256 == expectedSha256) {
                "Embedded runtime asset SHA-256 mismatch for $asset: $actualSha256"
            }
        }
    }
}

val stageEmbeddedRuntimeAssets by tasks.registering(StageEmbeddedRuntimeAssets::class) {
    group = "build"
    description = "Stages only the runtime assets selected by the pinned catalog."
    dependsOn(verifyEmbeddedRuntimeAssets)
    catalogFile.set(runtimeCatalogFile)
    bundleDirectory.set(runtimeBundleDirectory)
    outputDirectory.set(generatedEmbeddedRuntimeAssets)
}

val invalidatePackagedApksOnRuntimeCatalogChange by tasks.registering(
    InvalidatePackagedApksOnRuntimeCatalogChange::class,
) {
    group = "build"
    description = "Drops stale APK containers when the embedded runtime catalog changes."
    catalogFile.set(runtimeCatalogFile)
    apkOutputDirectory.set(layout.buildDirectory.dir("outputs/apk"))
    markerFile.set(layout.buildDirectory.file("embeddedRuntime/apk-catalog.txt"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(stageEmbeddedRuntimeAssets) {
        it.outputDirectory
    }
}

val verifyReleaseNativeElfPageAlignment by tasks.registering {
    group = "verification"
    description = "Fails release packaging when a merged native library is not 16 KiB page compatible."
    dependsOn("mergeReleaseNativeLibs")

    doLast {
        val mergedRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
            .get()
            .asFile
        val libraries = fileTree(mergedRoot) { include("**/*.so") }
            .files
            .filter { library ->
                library.relativeTo(mergedRoot)
                    .invariantSeparatorsPath
                    .substringBefore('/') in elecKoiPackagedAbis
            }
            .sortedBy { it.path }
        check(libraries.isNotEmpty()) { "No merged release native libraries found under $mergedRoot" }

        fun unsignedShort(buffer: ByteBuffer, offset: Int): Int =
            buffer.getShort(offset).toInt() and 0xffff
        fun unsignedInt(buffer: ByteBuffer, offset: Int): Long =
            buffer.getInt(offset).toLong() and 0xffff_ffffL

        val failures = mutableListOf<String>()
        libraries.forEach { library ->
            val bytes = library.readBytes()
            if (
                bytes.size < 16 ||
                bytes[0] != 0x7f.toByte() ||
                bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'L'.code.toByte() ||
                bytes[3] != 'F'.code.toByte()
            ) {
                failures += "${library.relativeTo(mergedRoot)}: invalid ELF header"
                return@forEach
            }
            val elfClass = bytes[4].toInt() and 0xff
            val littleEndian = (bytes[5].toInt() and 0xff) == 1
            if (!littleEndian || elfClass !in setOf(1, 2)) {
                failures += "${library.relativeTo(mergedRoot)}: unsupported ELF encoding"
                return@forEach
            }
            val minimumElfHeaderSize = if (elfClass == 2) 64 else 52
            val minimumProgramHeaderSize = if (elfClass == 2) 56 else 32
            if (bytes.size < minimumElfHeaderSize) {
                failures += "${library.relativeTo(mergedRoot)}: truncated ELF header"
                return@forEach
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val programHeaderOffset = if (elfClass == 2) buffer.getLong(32) else unsignedInt(buffer, 28)
            val programHeaderSize = unsignedShort(buffer, if (elfClass == 2) 54 else 42)
            val programHeaderCount = unsignedShort(buffer, if (elfClass == 2) 56 else 44)
            if (
                programHeaderOffset < minimumElfHeaderSize ||
                programHeaderSize < minimumProgramHeaderSize ||
                programHeaderCount <= 0 ||
                programHeaderCount > 4_096
            ) {
                failures += "${library.relativeTo(mergedRoot)}: invalid program header table"
                return@forEach
            }
            if (
                programHeaderOffset > bytes.size.toLong() ||
                programHeaderCount.toLong() >
                    (bytes.size.toLong() - programHeaderOffset) / programHeaderSize.toLong()
            ) {
                failures += "${library.relativeTo(mergedRoot)}: truncated program header table"
                return@forEach
            }

            var loadSegmentCount = 0
            for (index in 0 until programHeaderCount) {
                val offsetLong = programHeaderOffset + index.toLong() * programHeaderSize
                val offset = offsetLong.toInt()
                val type = unsignedInt(buffer, offset)
                if (type == 1L) {
                    loadSegmentCount += 1
                    val fileOffset =
                        if (elfClass == 2) buffer.getLong(offset + 8) else unsignedInt(buffer, offset + 4)
                    val virtualAddress =
                        if (elfClass == 2) buffer.getLong(offset + 16) else unsignedInt(buffer, offset + 8)
                    val fileSize =
                        if (elfClass == 2) buffer.getLong(offset + 32) else unsignedInt(buffer, offset + 16)
                    val alignment =
                        if (elfClass == 2) buffer.getLong(offset + 48) else unsignedInt(buffer, offset + 28)
                    val segmentLabel = "${library.relativeTo(mergedRoot)}: PT_LOAD[$index]"

                    if (
                        fileOffset < 0 ||
                        fileSize < 0 ||
                        fileOffset > bytes.size.toLong() ||
                        fileSize > bytes.size.toLong() - fileOffset
                    ) {
                        failures += "$segmentLabel has a truncated file range"
                    }
                    if (virtualAddress < 0) {
                        failures += "$segmentLabel has an unsupported virtual address"
                    }
                    if (alignment < 16_384L) {
                        failures += "$segmentLabel alignment is 0x${alignment.toString(16)}, expected at least 0x4000"
                    } else if ((alignment and (alignment - 1L)) != 0L) {
                        failures += "$segmentLabel alignment 0x${alignment.toString(16)} is not a power of two"
                    } else if (
                        virtualAddress >= 0 &&
                        fileOffset >= 0 &&
                        virtualAddress % alignment != fileOffset % alignment
                    ) {
                        failures +=
                            "$segmentLabel violates (p_vaddr - p_offset) % p_align == 0"
                    }
                }
            }
            if (loadSegmentCount == 0) {
                failures += "${library.relativeTo(mergedRoot)}: ELF contains no PT_LOAD segments"
            }
        }
        check(failures.isEmpty()) {
            "Release contains native libraries incompatible with 16 KiB Android pages:\n" +
                failures.joinToString("\n")
        }
    }
}

val verifyReleaseApkPageAlignment by tasks.registering {
    group = "verification"
    description = "Runs Android zipalign's final APK 16 KiB page-alignment verification."
    dependsOn("packageRelease")

    doLast {
        val apkRoot = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = fileTree(apkRoot) { include("**/*.apk") }.files.sortedBy { it.path }
        check(apks.isNotEmpty()) {
            "No release APK was produced under $apkRoot; run :app:packageRelease and inspect its output."
        }

        val sdkDirectory = androidComponents.sdkComponents.sdkDirectory.get().asFile
        val buildToolsDirectory = sdkDirectory.resolve("build-tools")
        val executableName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "zipalign.exe"
        } else {
            "zipalign"
        }
        fun numericVersionParts(name: String): List<Int> =
            Regex("\\d+").findAll(name).map { it.value.toIntOrNull() ?: 0 }.toList()
        val versionComparator = Comparator<File> { left, right ->
            val leftParts = numericVersionParts(left.name)
            val rightParts = numericVersionParts(right.name)
            val partCount = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until partCount) {
                val comparison = (leftParts.getOrElse(index) { 0 })
                    .compareTo(rightParts.getOrElse(index) { 0 })
                if (comparison != 0) return@Comparator comparison
            }
            left.name.compareTo(right.name)
        }
        val zipalign = buildToolsDirectory
            .listFiles { directory -> directory.isDirectory && directory.resolve(executableName).isFile }
            ?.maxWithOrNull(versionComparator)
            ?.resolve(executableName)
        check(zipalign != null) {
            "Android SDK zipalign was not found under $buildToolsDirectory. " +
                "Install Android SDK Build-Tools and retry :app:verifyReleaseApkPageAlignment."
        }

        apks.forEach { apk ->
            val process = ProcessBuilder(
                zipalign.absolutePath,
                "-c",
                "-P",
                "16",
                "-v",
                "4",
                apk.absolutePath,
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "zipalign rejected ${apk.relativeTo(apkRoot)} for 16 KiB page alignment " +
                    "(exit $exitCode):\n$output"
            }
            logger.lifecycle(
                "Verified final release APK with zipalign -c -P 16 -v 4: {}",
                apk.relativeTo(apkRoot),
            )
        }
    }
}

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn(verifyRoleplayVirtualAsset)
    }
    if (name == "packageDebug" || name == "packageRelease") {
        dependsOn(invalidatePackagedApksOnRuntimeCatalogChange)
    }
    when (name) {
        "packageRelease", "bundleRelease" -> {
            dependsOn(verifyReleaseSigningConfigured)
            dependsOn(verifyReleaseNativeElfPageAlignment)
        }
        "assembleRelease" -> {
            dependsOn(verifyReleaseSigningConfigured)
            dependsOn(verifyReleaseNativeElfPageAlignment)
            dependsOn(verifyReleaseApkPageAlignment)
        }
    }
}
