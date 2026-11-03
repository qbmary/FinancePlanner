plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

/**
 * Включать ли GGUF-модель (≈1.1 ГБ) внутрь APK.
 *
 * По умолчанию модель НЕ пакуется в APK: приложение читает GGUF-файл,
 * который пользователь выбирает на устройстве (SAF / OpenDocument) или
 * который лежит в приватной папке приложения. Это даёт APK ~30 МБ вместо ~1.1 ГБ.
 *
 * Собрать «всё-в-одном» APK с моделью:
 *   ./gradlew :app:assembleDebug -PbundleModel=true
 */
val bundleModel: Boolean =
    providers.gradleProperty("bundleModel").orNull?.toBooleanStrictOrNull() ?: false

android {
    namespace = "com.example.financeplanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.financeplanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // GGUF — это уже сжатый формат: deflate только замедляет сборку и установку.
        noCompress += "gguf"
        noCompress += "bin"
    }

}

androidComponents {
    onVariants { variant ->
        if (bundleModel) {
            // model-assets/models/<файл>.gguf -> assets/models/<файл>.gguf внутри APK
            variant.sources.assets?.addStaticSourceDirectory("model-assets")
        }
    }
}

dependencies {
    // --- Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // --- Навигация ---
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // --- ViewModel для Compose ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // --- Koin ---
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")

    // --- Room ---
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // --- Jetpack DataStore (настройки AI/голоса, URI модели) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Retrofit + OkHttp ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- kotlinx.serialization ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- Локальная GGUF-модель для лабораторной №5 (On-Device AI) ---
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.2.0")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --- CameraX ---
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    // --- Тесты ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
