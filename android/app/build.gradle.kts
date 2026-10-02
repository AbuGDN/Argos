plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.abugdn.wid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abugdn.wid"
        minSdk = 26
        targetSdk = 35
        // O CI passa o número da execução, para cada APK ser uma atualização válida.
        versionCode = (System.getenv("WID_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.${System.getenv("WID_VERSION_CODE") ?: "0"}"
        // Só processadores de celular: corta as bibliotecas nativas x86 do ML Kit.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Chave fixa no repositório: app de uso pessoal, e assim toda build (local ou CI)
    // tem a mesma assinatura e instala por cima da anterior.
    signingConfigs {
        create("wid") {
            storeFile = file("wid.jks")
            storePassword = "widnews"
            keyAlias = "wid"
            keyPassword = "widnews"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("wid")
        }
        debug {
            signingConfig = signingConfigs.getByName("wid")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.browser:browser:1.8.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
