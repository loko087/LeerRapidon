import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Release signing material never lives in the repo. Local builds read
// keystore.properties (gitignored — see keystore.properties.example); CI reads
// the same four values from the environment. When neither is present the
// release build is left unsigned rather than failing, so `assembleRelease`
// still works on a clean clone.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseStorePath = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStoreFile = releaseStorePath?.let { rootProject.file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.rapidreader.app"
    // Play requires new uploads to target a recent API level (36 as of the
    // Aug 2026 deadline), so compile and target move together here.
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.rapidreader.app"
        minSdk = 26
        targetSdk = 36
        // Bump versionCode on EVERY upload — Play rejects a reused one, and it
        // must keep rising across both the GitHub APK and the Play bundle.
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            // The bundled ML Kit OCR model and PdfBox make for a large
            // binary; R8 plus resource shrinking is worth the keep rules.
            // See app/proguard-rules.pro for what has to survive.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // PDF text extraction (pure-Kotlin/Java port of Apache PDFBox for Android)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // OCR fallback for scanned PDFs with no text layer. Bundled (not Play
    // Services-downloaded) model, so it works fully offline like the rest of the app.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
