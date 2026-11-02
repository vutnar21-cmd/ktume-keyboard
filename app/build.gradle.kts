// Файл сборки модуля :app — здесь настраивается версия SDK, релизная подпись и библиотеки Jetpack Compose
import java.util.Properties
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Опциональное чтение локального ключа подписи релиза из keystore.properties или переменных окружения.
// Если ключа нет (например, при сборке из исходников на чужой машине), assembleRelease собирает
// неподписанный app-release-unsigned.apk.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStoreFilePath: String? =
    keystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESJE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val releaseStorePassword: String? =
    keystoreProperties.getProperty("storePassword")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESJE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias: String? =
    keystoreProperties.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESJE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword: String? =
    keystoreProperties.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KESJE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
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
        versionCode = 5
        versionName = "1.4.0"
    }

    // В APK для GitHub Releases не вшиваем блок зависимостей, зашифрованный ключом Google (прочитать его
    // может только Google Play). В AAB для Google Play блок остаётся: Play проверяет по нему SDK.
    dependenciesInfo {
        includeInApk = false
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
            // Не вшиваем META-INF/version-control-info.textproto: APK из одного коммита собирается побайтово одинаковым
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
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.savedstate:savedstate-ktx:1.5.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    testImplementation("junit:junit:4.13.2")
}
