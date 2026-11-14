# Blast Block — teslim için temiz kaynak kod ZIP'i hazırlar (Windows PowerShell)
#
# Kullanım (proje klasöründe):
#   powershell -ExecutionPolicy Bypass -File scripts\package-source.ps1
#
# ZIP yalnızca git'in takip ettiği dosyaları içerir ("git archive"). Böylece şunlar ZIP'e GİRMEZ:
#   .git (geçmiş), .ai (iç notlar), build/ ve .gradle/ (derleme çıktıları),
#   local.properties (bilgisayara özel SDK yolu), keystore.properties ve imza anahtarı (gizli bilgiler).
# Gradle wrapper (gradlew, gradle-wrapper.jar) dahildir; proje Android Studio'da doğrudan açılabilir.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$status = git status --porcelain
if ($status) { Write-Warning "Commit edilmemiş değişiklikler var; ZIP yalnızca son commit'i içerir." }

New-Item -ItemType Directory -Force "dist" | Out-Null
$zip = Join-Path $root "dist\BlastBlock-kaynak-1.0.0.zip"
if (Test-Path $zip) { Remove-Item $zip }

git archive --format=zip --prefix=BlastBlock/ -o $zip HEAD
if ($LASTEXITCODE -ne 0) { throw "git archive başarısız oldu." }

Write-Host "Hazır: $zip"
