plugins {
    id("eleckoi.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.eleckoi.android.engine"
}

dependencies {
    implementation(project(":foundation:network"))
    implementation(project(":foundation:serialization"))
    implementation(project(":foundation:storage"))

    implementation("io.github.dokar3:quickjs-kt:1.0.15")
    implementation("androidx.paging:paging-runtime:3.5.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.github.mwiede:jsch:2.28.0")
    implementation("com.github.luben:zstd-jni:1.5.7-16@aar")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("org.json:json:20240303")
    testImplementation("com.github.luben:zstd-jni:1.5.7-16")
}
