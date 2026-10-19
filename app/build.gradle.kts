import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.yuji.quiz"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.yuji.quiz"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Same key for debug and release builds, so any build can update any other in place and keep app data.
    // The key lives outside the repo; without this file the build falls back to the default debug key.
    val signingFile = File(System.getProperty("user.home"), ".android/german-quiz-signing.properties")
    val ownSigning = if (signingFile.exists()) {
        val props = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("own") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else null
    // Two release channels, each following its own branch of the public feed repo:
    // stable (the real app, branch "main") and beta (a separate app next to it, branch "bleeding-edge").
    flavorDimensions += "channel"
    productFlavors {
        create("stable") {
            dimension = "channel"
            buildConfigField("String", "UPDATE_REF", "\"main\"")
            buildConfigField("String", "CHANNEL_LABEL", "\"stable\"")
            manifestPlaceholders["appLabel"] = "Deutsch B2"
        }
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "UPDATE_REF", "\"bleeding-edge\"")
            buildConfigField("String", "CHANNEL_LABEL", "\"bleeding edge (test)\"")
            manifestPlaceholders["appLabel"] = "Deutsch B2 beta"
        }
    }
    buildTypes {
        debug { ownSigning?.let { signingConfig = it } }
        release {
            ownSigning?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(bom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
