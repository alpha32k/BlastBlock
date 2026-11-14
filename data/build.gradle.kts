// :data modülü — kayıt katmanı.
// :domain modülündeki depo (repository) arayüzlerini Android'in dosya sistemi ve
// SharedPreferences'ı kullanarak gerçekleştirir.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.blastblock.game.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.junit)
    // Android'in içindeki org.json, bilgisayarda çalışan testlerde bulunmadığı için
    // birim testlerine aynı kütüphanenin bağımsız sürümü eklenir.
    testImplementation(libs.org.json)
}
