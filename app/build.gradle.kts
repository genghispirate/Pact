plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pact.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pact.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "5.8"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/pact-release.keystore")
            storePassword = "pact-release"
            keyAlias = "pact"
            keyPassword = "pact-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Trust network: Ed25519/X25519 crypto, relay transport, background sync
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
}
