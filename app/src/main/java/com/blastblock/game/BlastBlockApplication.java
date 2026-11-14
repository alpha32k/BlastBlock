package com.blastblock.game;

import android.app.Application;

import com.blastblock.game.di.AppContainer;

/**
 * Uygulamanın kendisi. Android, herhangi bir ekran açılmadan önce bu sınıfı bir kez oluşturur
 * (AndroidManifest.xml'de {@code android:name=".BlastBlockApplication"} ile tanıtılmıştır).
 *
 * <p>Görevi tek: uygulama boyunca yaşayacak ortak nesneleri ({@link AppContainer}) bir kez kurmak.
 * Ekranlar bu nesnelere {@code ((BlastBlockApplication) getApplication()).getContainer()} ile ulaşır.
 */
public final class BlastBlockApplication extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        // Application nesnesi bir Context'tir ve uygulama kapanana kadar yaşar; bu yüzden
        // repository'lere vermek Activity vermekten farklı olarak bellek sızıntısı yaratmaz.
        container = new AppContainer(this);
    }

    /** Uygulama genelindeki bağımlılık kabı. */
    public AppContainer getContainer() {
        return container;
    }
}
