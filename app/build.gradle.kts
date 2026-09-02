import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// --- Firma de la app (para actualizar sin desinstalar, y subir a Play Console) ---
// Estos valores llegan como variables de entorno desde GitHub Actions (ver build-apk.yml).
// Si no están presentes (por ejemplo, si alguien compila el proyecto localmente sin
// configurarlas), la app "release" simplemente queda sin firmar en vez de fallar.
val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank()

// --- Número de versión (para que cada compilación sea "más nueva" que la anterior) ---
// GitHub Actions pasa estos valores automáticamente (-PversionCode=... -PversionName=...).
// Si compilas localmente sin pasarlos, se usan estos valores por defecto.
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = project.findProperty("versionName") as String? ?: "1.0"

android {
    namespace = "com.smalegon.scanpdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smalegon.scanpdf"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Escáner de documentos de Google ML Kit (detección de bordes, recorte, PDF)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
