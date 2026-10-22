import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties in the project root (git-ignored, never committed).
val signing = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }

android {
    namespace = "app.pocket"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.pocket"
        minSdk = 33 // back-gesture callback (33) and takeScreenshot (30) both available
        targetSdk = 36
        versionCode = 1
        versionName = "0.3"
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // ./gradlew assembleRelease -PidSuffix=.rel installs next to the debug app (to test R8 without touching its data)
            (findProperty("idSuffix") as String?)?.let { applicationIdSuffix = it }
        }
    }
    buildFeatures { viewBinding = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.2")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}
