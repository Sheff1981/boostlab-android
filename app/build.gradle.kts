val defaultControlUrl = providers.environmentVariable("BOOSTLAB_DEFAULT_CONTROL_URL").orElse("").get()
val defaultGatewayHost = providers.environmentVariable("BOOSTLAB_DEFAULT_GATEWAY_HOST").orElse("").get()
val defaultGatewayPort = providers.environmentVariable("BOOSTLAB_DEFAULT_GATEWAY_PORT").orElse("51821").get()
val defaultWireGuardPublicKey = providers.environmentVariable("BOOSTLAB_DEFAULT_WG_PUBLIC_KEY").orElse("").get()
val defaultWireGuardPort = providers.environmentVariable("BOOSTLAB_DEFAULT_WG_PORT").orElse("51820").get()
val defaultTunnelAddress = providers.environmentVariable("BOOSTLAB_DEFAULT_TUNNEL_ADDRESS").orElse("10.77.0.2/32").get()
val defaultDnsServer = providers.environmentVariable("BOOSTLAB_DEFAULT_DNS_SERVER").orElse("1.1.1.1").get()
val ciVersionCode = providers.environmentVariable("BOOSTLAB_VERSION_CODE").orNull?.toIntOrNull()
val ciVersionName = providers.environmentVariable("BOOSTLAB_VERSION_NAME").orNull
val signingStorePath = providers.environmentVariable("BOOSTLAB_SIGNING_STORE_FILE").orNull
val signingPassword = providers.environmentVariable("BOOSTLAB_SIGNING_PASSWORD").orNull

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.boostlab.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.boostlab.app"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode ?: 10
        versionName = ciVersionName ?: "0.10.0-gateway-picker"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BOOSTLAB_DEFAULT_CONTROL_URL", "\"$defaultControlUrl\"")
        buildConfigField("String", "BOOSTLAB_DEFAULT_GATEWAY_HOST", "\"$defaultGatewayHost\"")
        buildConfigField("int", "BOOSTLAB_DEFAULT_GATEWAY_PORT", defaultGatewayPort)
        buildConfigField("String", "BOOSTLAB_DEFAULT_WG_PUBLIC_KEY", "\"$defaultWireGuardPublicKey\"")
        buildConfigField("int", "BOOSTLAB_DEFAULT_WG_PORT", defaultWireGuardPort)
        buildConfigField("String", "BOOSTLAB_DEFAULT_TUNNEL_ADDRESS", "\"$defaultTunnelAddress\"")
        buildConfigField("String", "BOOSTLAB_DEFAULT_DNS_SERVER", "\"$defaultDnsServer\"")
    }

    signingConfigs {
        if (!signingStorePath.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
            create("stable") {
                storeFile = file(signingStorePath)
                storePassword = signingPassword
                keyAlias = "boostlab"
                keyPassword = signingPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("io.github.webrtc-sdk:android:150.7871.01")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
}
