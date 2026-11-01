// Файл сборки модуля :app — здесь настраивается версия SDK, релизная подпись и библиотеки Jetpack Compose
import java.util.Properties
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Опциональное чтение локального ключа подписи релиза из keystore.properties или переменных окружения.
// Если ключа нет (например, при сборке на сервере F-Droid из исходников), assembleRelease собирает
// чистый детерминированный APK для сверки Reproducible Builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStoreFilePath: String? =
    keystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESKE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val releaseStorePassword: String? =
    keystoreProperties.getProperty("storePassword")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESKE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias: String? =
    keystoreProperties.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESKE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword: String? =
    keystoreProperties.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESKE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseStoreFile = releaseStoreFilePath?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrEmpty() &&
    !releaseKeyAlias.isNullOrEmpty() &&
    !releaseKeyPassword.isNullOrEmpty()

android {
    namespace = "org.chuvash.ktume"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.chuvash.ktume"
        minSdk = 26 // Поддержка всех телефонов с Android 8.0 и новее
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"
    }

    // Требования F-Droid и воспроизводимых сборок (Reproducible Builds):
    // не вшиваем зашифрованный ключом Google блок зависимостей.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Не вшиваем META-INF/version-control-info.textproto для побайтовой воспроизводимости в F-Droid
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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
