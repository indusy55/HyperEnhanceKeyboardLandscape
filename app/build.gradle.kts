import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName) ?: keystoreProperties.getProperty(propertyName)

val storeFileValue = System.getenv("KEYSTORE_FILE")
    ?: keystoreProperties.getProperty("storeFile")
    ?: "keystore/hyperenhance.jks"
val storePasswordValue = signingValue("KEYSTORE_PASSWORD", "storePassword")
val keyAliasValue = System.getenv("KEY_ALIAS")
    ?: keystoreProperties.getProperty("keyAlias")
    ?: "hyperenhance"
val keyPasswordValue = System.getenv("KEY_PASSWORD")
    ?: keystoreProperties.getProperty("keyPassword")
    ?: storePasswordValue

android {
    namespace = "io.github.indusy55.hyperenhancekeyboardlandscape"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "io.github.indusy55.hyperenhancekeyboardlandscape"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (storePasswordValue != null && rootProject.file(storeFileValue).exists()) {
            create("release") {
                storeFile = rootProject.file(storeFileValue)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation:1.9.4")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("androidx.compose.animation:animation:1.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.6.1")
    implementation("io.github.libxposed:service:102.0.0")
    compileOnly("io.github.libxposed:api:102.0.0")
}
