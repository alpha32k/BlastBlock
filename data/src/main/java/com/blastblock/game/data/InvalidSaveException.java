package com.blastblock.game.data;

/**
 * Kayıt dosyası okunabildi ama içeriği geçerli bir oyun durumu değilse fırlatılır.
 *
 * <p>Örnekler: JSON yarıda kesilmiş (telefon kayıt sırasında kapanmış olabilir), bir alan eksik,
 * tahta 64 hücre değil, tepside bilinmeyen bir şekil var veya dosya ileride çıkacak, bu sürümün
 * anlamadığı bir {@code schemaVersion} ile yazılmış. {@link FileGameRepository} bu hatayı yakalar
 * ve {@code GameLoadResult.invalid()} döndürür; böylece uygulama çökmeden yeni oyun başlatabilir.
 *
 * <p>{@link IllegalArgumentException} alt sınıfıdır: "verilen girdi geçersiz" anlamını taşır ve
 * denetimsiz (unchecked) olduğu için çağıranı gereksiz try/catch yazmaya zorlamaz.
 */
public final class InvalidSaveException extends IllegalArgumentException {

    public InvalidSaveException(String message) {
        super(message);
    }

    public InvalidSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
