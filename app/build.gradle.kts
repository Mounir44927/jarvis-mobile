plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jarvis.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    buildTypes {
        release {
            // R8/shrinkling is intentionally deferred to Phase 25 (Release candidate).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // منع ضغط أصول TTS (ONNX/بيانات espeak) — يجب أن يقرأها sherpa-onnx مباشرة من الـ APK
    androidResources {
        noCompress += listOf("onnx", "bin", "json", "txt")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // sherpa-onnx v1.13.8 (Apache-2.0) — TTS Neural محلي (Phase 7 — ADR-7/11)
    // AAR محلي موقّع بـSHA-256: 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
    // (تم فحص الـ AAR: بلا صلاحيات، minSdk 21، حزم JNI للمعمارات الأربع — DEPENDENCIES.md)
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
