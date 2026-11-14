plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.poing"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.poing"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Phase 1: no release signing. The release keystore is minted
    // at the first signed release (fresh keystore, never the
    // HowlingDog, Mariner or MainMenu signer). Until then only the
    // debug APK exists, as a CI artifact.
    buildTypes {
        release {
            isMinifyEnabled = false
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // Performance fix 9 — ui-tooling-preview is a
    // compile-time-only preview annotation library; it was in
    // implementation, so it shipped in the APK. debug only.
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Performance fix 9 — lifecycle-runtime-ktx REMOVED: no
    // KTX API use anywhere in app/src (no lifecycleScope,
    // repeatOnLifecycle, ProcessLifecycleOwner or
    // flowWithLifecycle); GameScreen uses only Lifecycle /
    // LifecycleEventObserver, which live in lifecycle-common
    // and lifecycle-runtime, not -ktx. CI is the check that
    // nothing else needed it.
    // Phase 3: folder import walks a picked document tree.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Phase 1: startup-path gate (Robolectric). The gate exists
    // because green CI and ordinary APK checks missed a launch death
    // before (AutoSpace r159, Notscape beta.48, HowlingDog v1.3.0
    // VerifyError): the test launches MainActivity and asserts the
    // home screen renders without crashing — with no native code
    // present, since the core is only ever loaded on game start.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
