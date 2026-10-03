import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Релизная подпись (ключ загрузки для Google Play): путь и пароли — в keystore.properties в корне проекта,
// файл и сам ключ в git не попадают (.gitignore). Нет файла — релиз собирается неподписанным.
val keystoreProps: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "com.djmetry.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.djmetry.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
            // R8: сжатие и оптимизация кода (Play: «Оптимизация DEX — низкий»), удаление неиспользуемых ресурсов
            isMinifyEnabled = true
            isShrinkResources = true
            // Отладочные символы нативного кода (карта MapLibre) — в App Bundle: Play расшифрует стек сбоев
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }


    buildFeatures {
        compose = true
    }

}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":shared"))
    
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging")
}

