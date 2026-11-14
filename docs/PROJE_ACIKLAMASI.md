# Blast Block — Proje Açıklaması

## Proje nedir?
**Blast Block**, Android telefonlar için Java ile geliştirilmiş bir blok bulmaca oyunudur. Oyuncu 8x8'lik bir
tahtaya, kendisine verilen üç parçayı parmağıyla sürükleyip bırakır. Dolan satır ve sütunlar silinir, puan kazanılır.
Elindeki parçaların hiçbiri tahtaya sığmadığında oyun biter. Oyunun adı, görselleri ve kodu bu projeye özgüdür.

## Oyun kuralları
- Tahta 8x8 hücredir. Oyuncuya her seferinde rastgele **3 parça** verilir (tekli kare, 2–5'lik çubuklar,
  2x2 ve 3x3 kareler, köşeler, L/J, T, S/Z — toplam 31 şekil). Parçalar düşmez ve döndürülmez.
- Parça tahtada boş bir yere bırakılır. Dolan **satır ve sütunlar aynı anda** bulunur ve birlikte silinir.
- Üç parça da yerleşince yeni üç parça gelir. Yeni üçlüden **en az biri tahtaya sığacak** şekilde seçilir
  (adil üretim), böylece oyun haksız biçimde hemen bitmez.
- Elde kalan parçaların hiçbiri sığmıyorsa **oyun biter**.

## Puanlama
| Durum | Puan |
|---|---|
| Parça yerleştirme | Parçadaki her hücre için 10 |
| Satır/sütun silme | Her çizgi için 100 |
| Kombo (aynı hamlede birden fazla çizgi) | İlk çizgiden sonraki her çizgi için +50 |
| Seri (art arda silme yapan hamleler) | Silme puanı ×1, ×2, ×3 … en fazla ×5; silme yapmayan hamlede sıfırlanır |

Örnek: 4 hücreli bir parça 2 çizgi silerse ilk seferde `40 + (200 + 50) × 1 = 290`, bir sonraki hamle de silme
yaparsa `40 + 250 × 2 = 540` puan kazanılır.

## Özellikler
- Yumuşak sürükle-bırak: parça alınınca büyüyerek parmağın biraz üstüne kalkar, bırakılınca yerine oturur;
  bırakılacağı yer önceden gösterilir, dolacak satır/sütunlar vurgulanır.
- **Yerleştirme yardımı:** parça dolu bir yere bırakılırsa, 1,5 hücre çevresindeki en yakın uygun yere konur
  (nereye konacağı sürüklerken gösterilir). Yakında uygun yer yoksa parça tepsiye döner.
- Efektler: silinen hücrelerden renkli parçacıklar, çizgi boyunca ışık geçişi, büyük silmelerde hafif sarsıntı,
  "2 Çizgi Kombo!", "Muhteşem!", "İnanılmaz!", "Seri ×N", "+puan", "Tahta Temiz!", "Yeni rekor!" yazıları,
  kısa titreşim (menüden açılıp kapatılabilir).
- Rekor ve bitirilen oyunlar telefonda saklanır. "Geçmiş" ekranı oyunları puan, tarih ve mod bilgisiyle gösterir.
- Uygulama kapatılıp açılınca yarım kalan oyun **"Devam Et"** ile aynen geri gelir.
- Oyun bitti ekranı, "Yeni rekor!" bildirimi ve "Tekrar Oyna".
- Arayüz Türkçe ve İngilizce kullanılabilir.

## Mimari: katmanlı yapı + MVVM
Proje, Android Studio'da üç ayrı **Gradle modülü** olarak görünen üç katmana ayrılmıştır:

| Modül | Katman | İçerik |
|---|---|---|
| `:domain` | İş kuralları | Tahta, parçalar, oyun motoru, puan hesabı, parça üretimi. **Saf Java**; Android'e bağımlı değildir. |
| `:data` | Veri | Yarım oyunun JSON olarak dosyaya kaydı, rekor ve ayarların saklanması. |
| `:app` | Sunum | Ekranlar (Activity), oyun alanını çizen `GameView`, `ViewModel`'ler. APK bu modülden üretilir. |

Bağımlılık yönü tek yönlüdür: `app → data → domain`. `domain` modülü bir *Java kütüphanesi* olduğundan Android
sınıflarını göremez; bu kural derleyici tarafından korunur. Böylece oyunun kuralları ekrandan bağımsızdır ve
bilgisayarda birim testleriyle doğrulanabilir.

**MVVM (Model–View–ViewModel):**
- *Model*: `domain` katmanındaki oyun durumu (`GameState`) ve kurallar (`GameEngine`).
- *View*: `MenuActivity`, `GameActivity`, `GameView` — yalnızca çizer ve dokunmayı iletir.
- *ViewModel*: `GameViewModel`, `MenuViewModel` — ekranın durumunu `LiveData` ile yayınlar, ekran döndürmede
  kaybolmaz, kayıt işlerini arka planda yaptırır.

Bir hamlenin yolculuğu: parmak kalkar → `GameView` hücreyi hesaplar → `GameViewModel.place()` →
`GameEngine.place()` kuralları uygular → yeni durum `LiveData` ile ekrana gelir → durum arka planda diske yazılır.
Ayrıntılı şema ve sınıf listesi: [MIMARI.md](MIMARI.md).

## Önemli algoritmalar
- **Satır/sütun silme**: Parça yerleştirildikten sonra aynı tahta üzerinde dolu satırlar ve dolu sütunlar ayrı ayrı
  bulunur, ardından hepsi birlikte silinir. Kesişen hücre bir kez silinir ama iki çizgi sayılır.
- **Adil parça üretimi**: Yeni üçlüde bir parça, o anda tahtaya sığan şekiller arasından; diğer ikisi tüm katalogdan
  seçilir ve yuvalar karıştırılır.
- **Yakın yere yerleştirme** (`PlacementResolver`): parmağın gösterdiği kesirli tahta konumu hesaplanır.
  O konum uygunsa kullanılır; değilse 1,5 hücre yarıçapındaki tüm başlangıç noktaları denenir ve parçanın
  tamamen sığdığı, kesirli konuma Öklid uzaklığı en küçük olan seçilir.
- **Geçmiş**: SQLite her tamamlanan oyunu ayrı bir satırda saklar. Ekran puana göre azalan sırayla
  50 kayıt getirir. Eşit puanda eski oyun önde yer alır.
- **Güvenli kayıt**: Oyun `AtomicFile` ile yazılır; uygulama yazma sırasında kapanırsa eski sağlam kayıt korunur.
  Kayıt dosyasında sürüm numarası vardır; bozuk kayıt tespit edilirse oyuncuya bilgi verilip yeni oyun açılır.

## Testler
- `domain`: oyun motoru, puanlama, parça kataloğu ve adil üretim için JUnit testleri.
- `data`: kayıt dosyasının JSON biçimine çevrilmesi ve bozuk kayıtların reddedilmesi için testler.
- `app`: ViewModel'lerin davranışı (yeni oyun, devam, kayıt hataları, oyun sonu) için testler.
Çalıştırmak için: `gradlew.bat test`

## Kullanılan teknolojiler
Java 11 · Android SDK 36 (en düşük Android 7.0) · Android Gradle Plugin 8.13 · Gradle 8.13 ·
AndroidX AppCompat, Material, Lifecycle (ViewModel/LiveData) · JUnit 4.
Derleme ayar dosyaları (`build.gradle.kts`) Android Studio'nun varsayılanı olan Kotlin DSL ile yazılmıştır;
**oyunun tüm kodu Java'dır.**

## Yapay zekâ kullanımı
Proje; planlama, kodlama, görsel üretimi ve kod incelemesi aşamalarında yapay zekâ araçlarıyla birlikte
geliştirilmiştir. Logo, uygulama ikonu, menü arka planı ve blok dokusu yapay zekâ ile özgün olarak üretilmiştir.
