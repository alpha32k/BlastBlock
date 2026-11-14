# Blast Block — imzalı release APK derleme betiği (Windows PowerShell)
#
# Kullanım (proje klasöründe):
#   powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
#
# Ne yapar?
#   1) JAVA_HOME tanımlı değilse Android Studio'nun kendi Java'sını (JBR) kullanır.
#   2) Testleri çalıştırır ve imzalı release APK'yı derler.
#   3) APK'yı dist\BlastBlock-1.0.0.apk olarak kopyalar ve imzasını doğrular.
# İmza için proje kökünde keystore.properties dosyası gerekir (bkz. docs\DERLEME_VE_KURULUM.md).

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $env:JAVA_HOME) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr } else { throw "JAVA_HOME bulunamadı. Android Studio kurulu mu?" }
}

if (-not (Test-Path "keystore.properties")) {
    throw "keystore.properties bulunamadı. İmza anahtarı olmadan imzalı APK üretilemez (rehbere bakın)."
}

& .\gradlew.bat test assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle derlemesi başarısız oldu." }

New-Item -ItemType Directory -Force "dist" | Out-Null
$apk = Join-Path $root "dist\BlastBlock-1.0.0.apk"
Copy-Item "app\build\outputs\apk\release\app-release.apk" $apk -Force

# APK imzasını Android SDK'daki apksigner ile doğrula.
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$apksigner = Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName "apksigner.bat" } | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($apksigner) {
    & $apksigner verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw "APK imzası doğrulanamadı." }
}

Write-Host "Hazır: $apk"
