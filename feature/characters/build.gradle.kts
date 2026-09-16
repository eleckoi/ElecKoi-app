plugins {
    id("eleckoi.android.library")
    id("eleckoi.android.compose")
}

android {
    namespace = "com.eleckoi.android.feature.characters"
}

dependencies {
    implementation(project(":compatibility:mvu"))
    implementation(project(":engine"))
    implementation(project(":feature:modelconfig"))
    implementation(project(":foundation:design"))
    implementation(project(":foundation:serialization"))
    implementation(project(":foundation:storage"))

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
