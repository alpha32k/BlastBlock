package com.blastblock.game.presentation.state;

/**
 * Tek seferlik bir olayı saran kap ("Event wrapper" deseni).
 *
 * <p>Neden gerekli? LiveData son değeri hatırlar ve yeni bir gözlemciye (ör. ekran döndürülünce
 * yeniden oluşan Activity'ye) hemen tekrar verir. Ekran durumu için bu istenen davranıştır, ama
 * "titreşim ver", "Oyun bitti penceresini aç" gibi olaylar için değildir: döndürmeden sonra aynı
 * pencere ikinci kez açılmamalıdır. Bu sınıf olayın içeriğini yalnızca BİR KEZ teslim eder;
 * sonraki çağrılarda {@code null} döner, böylece yeniden gözlemde olay tekrar işlenmez.
 *
 * @param <T> olayın içeriği
 */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** İçeriği ilk çağrıda döndürür ve olayı "işlendi" olarak işaretler; sonraki çağrılarda null döner. */
    public synchronized T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    /** İçeriği, işlenmiş olsa bile, işaretlemeden döndürür (ör. testlerde ya da kayıt için). */
    public T peekContent() {
        return content;
    }
}
