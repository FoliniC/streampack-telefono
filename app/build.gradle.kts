import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.thibaultbee.streampack.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.thibaultbee.streampack.app"
        minSdk = 21
        targetSdk = 33
        val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
        versionCode = if (runNumber.toInt() > 0) runNumber.toInt() else 496752 
        versionName = if (runNumber.toInt() > 0) "1.0.${runNumber}" else "1.0.dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_18)
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.streampack.core)
    // For the `PreviewView`
    implementation(libs.streampack.ui)
    // TODO: Only needed for RTMP live streaming: remove if you don't need it
    implementation(libs.streampack.rtmp)
    // TODO: Only needed for SRT live streaming: remove if you don't need it
    implementation(libs.streampack.srt)

    // Timber - clean logging API
    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.runtime.ktx)
    implementation("androidx.multidex:multidex:2.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}