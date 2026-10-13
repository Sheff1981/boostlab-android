plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.boostlab.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.boostlab.app"
        minSdk = 26
        targetSdk = 35
        // CI run numbers increase on each build, enabling in-place upgrades with the same signing key.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.1.0-dev." + versionCode
    }
    signingConfigs {
        create("distribution") {
            val storePath = System.getenv("BOOSTLAB_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("BOOSTLAB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BOOSTLAB_KEY_ALIAS")
                keyPassword = System.getenv("BOOSTLAB_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("BOOSTLAB_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("distribution")
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
