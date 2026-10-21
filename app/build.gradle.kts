plugins {
    alias(libs.plugins.android.application)
}

// Подпись release-сборки из переменных окружения (задаются в GitHub Actions).
// Локально переменных нет — APK собирается неподписанным.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.sitikplay.foxhole"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sitikplay.foxhole"
        // Android 8 (API 26): через ПК сервер запускается на любой поддерживаемой
        // версии. Спаривание по Wi-Fi (adb pair) есть только с Android 11 (API 30).
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Сервер (com.sitikplay.foxhole.Server) запускается через app_process по имени
            // класса — обфускация/стриппинг сломает запуск. Правила: keepRules/rules.keep.
            optimization {
                enable = false
            }
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}