package com.blastblock.game.data;

import android.content.Context;
import android.util.AtomicFile;

import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.repository.GameLoadResult;
import com.blastblock.game.domain.repository.GameRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Yarım kalan oyunu uygulamanın özel klasöründe {@code savegame.json} dosyasında saklar.
 *
 * <p><b>Neden {@link AtomicFile}?</b> Oyun her hamleden sonra kaydedilir. Telefon tam yazma
 * sırasında kapanırsa (pil biter, uygulama öldürülür) sıradan bir dosya yarım kalır ve oyun
 * kaybolur. AtomicFile önce yeni içeriği ayrı bir dosyaya yazar, yazma başarıyla bitince
 * ({@code finishWrite}) eski dosyanın yerine koyar. Bu yüzden diskteki kayıt ya eski hâliyle
 * ya da yeni hâliyle tamdır; hiçbir zaman ikisinin karışımı olmaz.
 *
 * <p><b>Bozuk kayıt davranışı:</b> {@link #load()} dosya yoksa {@code EMPTY}, dosya okundu ama
 * JSON çözülemiyor ya da {@link GameJsonCodec} denetimlerinden geçmiyorsa {@code INVALID}
 * döndürür (uygulama çökmez; üst katman kullanıcıyı bilgilendirip yeni oyun açar). Bitmiş bir
 * oyun ({@code gameOver=true}) devam ettirilemeyeceği için {@code EMPTY} sayılır. Dosyanın
 * okunması sırasındaki gerçek G/Ç hataları ise {@link IOException} olarak yukarı fırlatılır;
 * çünkü bu hatalar geçici olabilir ve kaydın kendisi sağlamdır, silinmemelidir.
 *
 * <p><b>İş parçacıkları:</b> Metotlar senkron (bloklayan) çalışır ve ana iş parçacığında
 * çağrılmamalıdır. Sıralamayı üst katmandaki tek sıralı executor sağlar; yine de aynı dosyaya
 * iki yazma çakışmasın diye tüm metotlar {@code synchronized}'dır.
 */
public final class FileGameRepository implements GameRepository {

    /** Kayıt dosyasının adı ({@code Context.getFilesDir()} içinde). */
    public static final String FILE_NAME = "savegame.json";

    private final AtomicFile atomicFile;
    private final GameJsonCodec codec = new GameJsonCodec();

    /**
     * @param context herhangi bir Context; Activity sızıntısını önlemek için applicationContext alınır
     */
    public FileGameRepository(Context context) {
        this(new File(context.getApplicationContext().getFilesDir(), FILE_NAME));
    }

    /** Belirli bir dosya yolu ile kurar (ör. cihaz üzerindeki testler için). */
    FileGameRepository(File file) {
        this.atomicFile = new AtomicFile(file);
    }

    @Override
    public synchronized GameLoadResult load() throws IOException {
        // AtomicFile okurken yarım kalmış bir yazmadan geriye kalan yedeği de dikkate alır;
        // bu yüzden varlık denetimini getBaseFile().exists() yerine readFully() ile yapıyoruz:
        // dosya yoksa FileNotFoundException gelir.
        byte[] bytes;
        try {
            bytes = atomicFile.readFully();
        } catch (FileNotFoundException e) {
            return GameLoadResult.empty(); // hiç kayıt yok
        }
        // Diğer IOException'lar (geçici disk/okuma hatası) bilerek yukarı fırlatılır: bunları
        // INVALID saymak, üst katmanın yeni oyun kaydedip aslında sağlam olan kaydı silmesine yol açar.

        GameState state;
        try {
            state = codec.decode(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            // InvalidSaveException dahil: bozuk/kesik/uyumsuz kayıt.
            return GameLoadResult.invalid();
        }
        if (state.isGameOver()) {
            return GameLoadResult.empty(); // bitmiş oyun devam ettirilmez
        }
        return GameLoadResult.active(state);
    }

    @Override
    public synchronized void save(GameState state) throws IOException {
        // Durum değişmez (immutable) olduğundan dosyaya yazarken başka iş parçacığı onu bozamaz.
        byte[] bytes = codec.encode(state).getBytes(StandardCharsets.UTF_8);
        FileOutputStream out = atomicFile.startWrite();
        try {
            out.write(bytes);
        } catch (IOException | RuntimeException e) {
            // Yazma başarısız: yarım yeni dosya silinir, eski kayıt olduğu gibi kalır.
            atomicFile.failWrite(out);
            throw e;
        }
        // finishWrite fsync yapıp yeni dosyayı asıl dosyanın yerine koyar. Not: Android'in
        // AtomicFile.finishWrite'ı fsync/close hatalarını kendi içinde loglayıp yutar; yani
        // bu adımdaki her hata çağırana IOException olarak ulaşmaz.
        atomicFile.finishWrite(out);
    }

    @Override
    public synchronized void clearSavedGame() throws IOException {
        // AtomicFile.delete hem asıl dosyayı hem de olası yedek/yeni dosyayı siler.
        atomicFile.delete();
        if (atomicFile.getBaseFile().exists()) {
            throw new IOException("Kayıt dosyası silinemedi");
        }
    }
}
