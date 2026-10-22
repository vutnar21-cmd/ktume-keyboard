// Файл сборки модуля :app — здесь настраивается версия SDK и подключаются библиотеки Jetpack Compose
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.chuvash.keske"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.chuvash.keske"
        minSdk = 26 // Поддержка всех телефонов с Android 8.0 и новее
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }

    buildFeatures {
        compose = true // Включаем современный UI-фреймворк Jetpack Compose
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
}
