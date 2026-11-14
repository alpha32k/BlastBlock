// :domain modülü — oyunun kuralları.
// Saf bir Java kütüphanesidir: Android sınıflarını göremez. Böylece oyun mantığı
// ekrandan tamamen bağımsız kalır ve bilgisayarda JUnit ile test edilebilir.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // JDK 21 ile derlense bile yalnızca Java 11'de bulunan API'lerin kullanılmasını garanti eder.
    options.release.set(11)
}

dependencies {
    testImplementation(libs.junit)
}
