package com.blastblock.game.domain.repository;

import com.blastblock.game.domain.model.GameState;

/**
 * {@link GameRepository#load()} sonucunu taşıyan değişmez nesne.
 *
 * <ul>
 *   <li>{@code EMPTY}: kayıtlı oyun yok.</li>
 *   <li>{@code ACTIVE}: geçerli bir kayıt var; {@link #getState()} doludur.</li>
 *   <li>{@code INVALID}: kayıt bozuk veya uyumsuz; kullanıcı bilgilendirilip yeni oyun başlatılır.</li>
 * </ul>
 */
public final class GameLoadResult {

    public enum Status { EMPTY, ACTIVE, INVALID }

    private static final GameLoadResult EMPTY = new GameLoadResult(Status.EMPTY, null);
    private static final GameLoadResult INVALID = new GameLoadResult(Status.INVALID, null);

    private final Status status;
    private final GameState state;

    private GameLoadResult(Status status, GameState state) {
        this.status = status;
        this.state = state;
    }

    public static GameLoadResult empty() {
        return EMPTY;
    }

    /** @throws IllegalArgumentException durum null ise */
    public static GameLoadResult active(GameState state) {
        if (state == null) {
            throw new IllegalArgumentException("ACTIVE sonuç bir durum içermelidir");
        }
        return new GameLoadResult(Status.ACTIVE, state);
    }

    public static GameLoadResult invalid() {
        return INVALID;
    }

    public Status getStatus() {
        return status;
    }

    /** Yalnızca ACTIVE durumunda dolu; diğerlerinde null. */
    public GameState getState() {
        return state;
    }
}
