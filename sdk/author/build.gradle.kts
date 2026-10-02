plugins {
    id("eleckoi.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.eleckoi.android.sdk.author"
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("app/src/main/assets"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("examples/minimal-memory"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("examples"))
}

dependencies {
    api(project(":engine"))
    implementation(project(":foundation:serialization"))
    implementation(project(":foundation:storage"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

}
