package com.blastblock.game.domain.repository;

import com.blastblock.game.domain.model.GameState;

import java.io.IOException;

/**
 * Yarım kalan oyunun kaydedilip geri yüklenmesi için arayüz.
 *
 * <p>Domain katmanı yalnızca bu arayüzü bilir; dosyaya nasıl yazıldığı (JSON, AtomicFile)
 * :data modülündeki gerçekleştirimin işidir. Böylece oyun kuralları kayıt biçiminden bağımsız kalır.
 */
public interface GameRepository {

    /** Kayıtlı oyunu okur: kayıt yoksa EMPTY, bozuksa INVALID, sağlamsa ACTIVE döner. */
    GameLoadResult load() throws IOException;

    /** Verilen durumu kalıcı olarak kaydeder (öncekinin üzerine yazar). */
    void save(GameState state) throws IOException;

    /** Kayıtlı oyunu siler (oyun bittiğinde veya yeni oyun başlatıldığında). */
    void clearSavedGame() throws IOException;
}
