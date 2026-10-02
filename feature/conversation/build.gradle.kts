plugins {
    id("eleckoi.android.library")
    id("eleckoi.android.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.eleckoi.android.feature.conversation"
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("app/src/main/assets"))
}

dependencies {
    implementation(project(":feature:appfont"))
    implementation(project(":feature:characters"))
    implementation(project(":feature:modelconfig"))
    implementation(project(":feature:preferences"))
    implementation(project(":compatibility:mvu"))
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.paging:paging-runtime:3.5.0")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.github.huarangmeng:latex-renderer-android:1.4.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
