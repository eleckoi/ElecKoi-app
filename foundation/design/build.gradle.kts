plugins {
    id("eleckoi.android.library")
    id("eleckoi.android.compose")
}

android {
    namespace = "com.eleckoi.android.foundation.design"
}

dependencies {
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("com.materialkolor:material-color-utilities:2.0.2")
    implementation("com.caverock:androidsvg-aar:1.4")
}
