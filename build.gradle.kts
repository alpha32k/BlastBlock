// Kök derleme dosyası: modüllerin kullandığı eklentileri (plugin) tek yerde tanımlar.
// Not: Bu dosyalar Kotlin DSL ile yazılmış derleme ayarlarıdır; oyunun kodu tamamen Java'dır.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
