// :app modülü — sunum katmanı (ekranlar, oyun alanı çizimi, ViewModel'ler).
// APK bu modülden üretilir; :domain ve :data modüllerini kullanır.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// İmza bilgileri proje kökündeki "keystore.properties" dosyasından okunur.
// Bu dosya ve imza anahtarı git'e ve teslim ZIP'ine KONULMAZ (bkz. .gitignore).
// Dosya yoksa release APK imzasız üretilir; nasıl oluşturulacağı docs/DERLEME_VE_KURULUM.md'de anlatılır.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.blastblock.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blastblock.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    testImplementation(libs.junit)
    testImplementation(libs.core.testing)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
