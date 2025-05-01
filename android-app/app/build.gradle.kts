plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.billvision"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.billvision"
        minSdk = 33
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        mlModelBinding = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Android KTX, Lifecycle, Activity Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7") // Looks correct

    // CameraX + Accompanist Permissions
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.accompanist.permissions)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil (Optional: Remove if not used)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // --- Unit Testing ---
    testImplementation(libs.junit)
    testImplementation(kotlin("test")) // Keep kotlin.test
    // Additions for Unit Tests:
    testImplementation(libs.kotlinx.coroutines.test) // For testing coroutines/flows
    testImplementation(libs.androidx.arch.core.testing) // For InstantTaskExecutorRule
    testImplementation(libs.mockito.kotlin)         // For mocking
    testImplementation(libs.mockito.inline)          // For mocking final classes
    testImplementation(libs.turbine)                 // For testing Flows (e.g., app.cash.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)


    // --- Instrumentation Testing ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Test BOM
    androidTestImplementation(libs.androidx.ui.test.junit4)     // Compose UI Tests
    androidTestImplementation(libs.androidx.test.rules)         // Test Rules (like GrantPermissionRule)
    // androidTestImplementation(libs.kotlinx.coroutines.test) // Optional: If needed in instrumentation tests too

    // Debugging Tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}