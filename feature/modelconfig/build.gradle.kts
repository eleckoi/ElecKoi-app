plugins {
    id("eleckoi.android.library")
    id("eleckoi.android.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.eleckoi.android.feature.modelconfig"
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":foundation:design"))

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
