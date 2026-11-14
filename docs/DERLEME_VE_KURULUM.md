# Blast Block — Derleme ve Telefona Kurulum Rehberi

Bu rehber APK'nın nasıl derleneceğini ve Android telefona nasıl kurulacağını anlatır.
Hazır imzalı APK teslim klasöründe `BlastBlock-1.1.apk` adıyla bulunur; yalnızca kurmak istiyorsanız doğrudan
**3. bölüme** geçebilirsiniz.

## 1. Gerekenler
- **Android Studio** (projede kullanılan sürüm: AGP 8.13 / Gradle 8.13 destekleyen güncel sürüm).
  Android Studio kendi Java'sını (JBR) ve Android SDK'yı kurar; ayrıca Java veya Gradle kurmak gerekmez.
- **Android SDK 36** (Android Studio ilk açılışta eksikse indirmeyi teklif eder).
- İlk derlemede internet bağlantısı (Gradle bağımlılıkları indirilir).

## 2. APK'yı derlemek

### 2a. Android Studio ile
1. Android Studio → **Open** → proje klasörünü (`BlastBlock`) seçin. Gradle senkronizasyonunun bitmesini bekleyin.
2. Proje panelinde üç modül görünür: `app`, `domain`, `data`.
3. Denemek için: telefonu USB ile bağlayın (aşağıdaki "USB hata ayıklama" adımı) veya bir emülatör seçip ▶ **Run**.
4. İmzalı APK için: **Build → Generate Signed App Bundle / APK… → APK** → var olan bir anahtar seçin ya da
   **Create new…** ile yeni bir anahtar (keystore) oluşturun → **release** → **Create**.
   APK, `app/release/` klasörüne yazılır.

### 2b. Komut satırından (Windows PowerShell)
Proje klasöründe:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # Android Studio'nun Java'sı
.\gradlew.bat test              # tüm birim testlerini çalıştırır
.\gradlew.bat assembleDebug     # imza anahtarı gerektirmeyen deneme APK'sı
```
Deneme APK'sı: `app\build\outputs\apk\debug\app-debug.apk` (telefona kurulabilir, Android Studio'nun hata ayıklama
anahtarıyla imzalıdır).

**İmzalı release APK** için önce bir imza anahtarı gerekir (bir kez yapılır):

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore "$env:USERPROFILE\.blastblock\blastblock-release.jks" `
  -alias blastblock -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Blast Block, O=Blast Block, C=TR"
```
(Klasör yoksa önce `mkdir "$env:USERPROFILE\.blastblock"`.) Sorulan parolayı not edin. Sonra proje kökünde
`keystore.properties` adında bir dosya oluşturun:

```properties
storeFile=C:/Users/<kullanıcı>/.blastblock/blastblock-release.jks
storePassword=<parola>
keyAlias=blastblock
keyPassword=<parola>
```
Bu dosya ve anahtar **git'e ve paylaşılan kaynak ZIP'ine girmez** (`.gitignore`). Anahtarın yedeğini saklayın:
aynı uygulamanın güncellemesi ancak aynı anahtarla imzalanabilir.

Ardından:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
```
Betik testleri çalıştırır, `assembleRelease` ile imzalı APK'yı üretir, `dist\BlastBlock-1.1.apk` olarak kopyalar ve
`apksigner` ile imzayı doğrular. (Elle: `.\gradlew.bat assembleRelease` → `app\build\outputs\apk\release\app-release.apk`.
`keystore.properties` yoksa release APK imzasız üretilir ve telefona kurulamaz.)

## 3. Telefona kurmak

### Yol A — APK dosyasını telefona kopyalayarak (en kolayı)
1. `BlastBlock-1.1.apk` dosyasını telefona gönderin (USB kablosu, Google Drive, e-posta, Bluetooth vb.).
2. Telefonda **Dosyalar** uygulamasından APK'ya dokunun.
3. "Bilinmeyen uygulamaları yükle" uyarısı çıkarsa: **Ayarlar**'a gidip APK'yı açtığınız uygulamaya
   (Dosyalar / Chrome / Drive) **"Bu kaynaktan izin ver"** iznini verin, geri dönün.
4. **Yükle**'ye dokunun. Play Protect "tanınmayan geliştirici" uyarısı gösterirse **Yine de yükle** seçin
   (uygulama mağazadan değil, doğrudan dosyadan kurulduğu için bu normaldir).
5. Uygulama listesinde **Blast Block** simgesi görünür.

### Yol B — USB ve adb ile
1. Telefonda **Ayarlar → Telefon hakkında → Yapım numarası**'na 7 kez dokunun (Geliştirici seçenekleri açılır).
2. **Ayarlar → Sistem → Geliştirici seçenekleri → USB hata ayıklama**'yı açın; telefonu USB ile bağlayıp izin verin.
3. Bilgisayarda:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\BlastBlock-1.1.apk
   ```

## 4. Sık karşılaşılan sorunlar
| Sorun | Çözüm |
|---|---|
| `JAVA_HOME is not set` | `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` |
| `SDK location not found` | Android Studio'da projeyi bir kez açın (`local.properties` oluşur) ya da `ANDROID_HOME` ayarlayın. |
| SDK 36 eksik | Android Studio → SDK Manager → Android 16 (API 36) kurun. |
| "Uygulama yüklenmedi" | Telefonda aynı uygulamanın farklı anahtarla imzalanmış eski sürümünü kaldırıp tekrar deneyin. |
| İmzasız APK | `keystore.properties` dosyasını oluşturun (2b). |

Uygulama en az **Android 7.0** gerektirir.
