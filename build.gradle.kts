plugins {
    id("eleckoi.architecture")
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    id("com.android.test") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}

// Lock every project dependency graph that Gradle resolves.  The checked-in
// lock state is refreshed deliberately with `./gradlew :app:dependencies --write-locks`.
// Plugin artifacts are covered separately by Gradle dependency verification.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("io.github.dokar3:quickjs-kt-android"))
                .using(module("io.github.dokar3:quickjs-kt-jvm:1.0.15"))
        }
    }
}
