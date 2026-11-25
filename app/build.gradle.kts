import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mcpackbuilder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mcpackbuilder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("mcpack.keystore")
            storePassword = providers.environmentVariable("MCPACK_PASSWORD").orElse(providers.gradleProperty("mcpackPassword")).get()
            keyAlias = "mcpack"
            keyPassword = storePassword
            val ks = KeyStore.getInstance("PKCS12")
            storeFile!!.inputStream().use { ks.load(it, storePassword!!.toCharArray()) }
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(ks.getCertificate("mcpack").encoded).joinToString("") { "%02x".format(it) }
            check(fingerprint == "3fe60313bfe1df89d337ef56f124f45adc7023f9628baf0ae85033a99df15f93") { "Не тот сертификат подписи" }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("fixed")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
