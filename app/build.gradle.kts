import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}
val metaEnabled = providers.gradleProperty("metaEnabled").orNull == "true"
val metaProperties =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

android {
    namespace = "fr.speedvision"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.speedvision"
        minSdk = if (metaEnabled) 29 else 28
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0"
        buildConfigField("boolean", "META_ENABLED", metaEnabled.toString())
        manifestPlaceholders["metaApplicationId"] = metaProperties.getProperty("meta.applicationId", "")
        manifestPlaceholders["metaClientToken"] = metaProperties.getProperty("meta.clientToken", "")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].java.srcDir(if (metaEnabled) "src/meta/java" else "src/noMeta/java")
    if (metaEnabled) {
        sourceSets["main"].manifest.srcFile("src/meta/AndroidManifest.xml")
        sourceSets["androidTest"].java.srcDir("src/metaAndroidTest/java")
    }
    // OpenCV and DAT's fbjni bundle libc++; retain one runtime and exercise both integrations in CI.
    if (metaEnabled) packaging.jniLibs.pickFirsts += "**/libc++_shared.so"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kapt { correctErrorTypes = true }
dependencies {
    implementation(project(":domain"))
    if (metaEnabled) {
        implementation("com.meta.wearable:mwdat-core:1.0.0")
        implementation("com.meta.wearable:mwdat-camera:1.0.0")
    }
    implementation("org.opencv:opencv:4.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-compiler:2.55")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Lint indexes generated test stubs too. Do not let kapt replace them during analysis.
tasks.matching { it.name.startsWith("lintAnalyzeDebug") }.configureEach {
    dependsOn("kaptGenerateStubsDebugKotlin", "kaptGenerateStubsDebugUnitTestKotlin", "kaptGenerateStubsDebugAndroidTestKotlin")
}
