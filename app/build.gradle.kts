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
        versionCode = 13
        versionName = "0.1.0-alpha.12"
    }

    // Signed alpha (Step 4, 2026-11-15): the release keystore is
    // fresh for Poing! (never the HowlingDog, Mariner or MainMenu
    // signer), minted with keytool (PKCS12, alias "poing", RSA
    // 4096, 10,000 days, CN=Poing!). It is NOT in the repo: CI
    // restores it from the POING_KEYSTORE_BASE64 secret, and the
    // backup lives at ~/workspace/poing/keystore/ (outside the
    // repo, never committed). The same key signs every release —
    // the in-app updater depends on it, so losing it breaks
    // future updates. The signing config only exists when all
    // four values are present (environment variables, or Gradle
    // properties of the same names for a local signed build), so
    // ordinary debug builds need nothing.
    signingConfigs {
        val keystorePath = System.getenv("POING_KEYSTORE_PATH")
            ?: project.findProperty("poingKeystorePath")?.toString()
        val keystorePassword = System.getenv("POING_KEYSTORE_PASSWORD")
            ?: project.findProperty("poingKeystorePassword")?.toString()
        val keyAlias = System.getenv("POING_KEY_ALIAS")
            ?: project.findProperty("poingKeyAlias")?.toString()
        val keyPassword = System.getenv("POING_KEY_PASSWORD")
            ?: project.findProperty("poingKeyPassword")?.toString()
        if (keystorePath != null && keystorePassword != null &&
            keyAlias != null && keyPassword != null
        ) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
