package asia.eyekandi.emw;

import android.app.Application;
import android.content.SharedPreferences;

import com.crashlytics.android.Crashlytics;

import javax.inject.Inject;

import asia.eyekandi.emw.di.AppComponent;
import asia.eyekandi.emw.di.AppModule;
import asia.eyekandi.emw.di.DaggerAppComponent;
import io.fabric.sdk.android.Fabric;

/**
 * Created by mitja on 12/02/16.
 * Copyright(C) AMOK Products ApS Ltd
 */
public class MyApplication extends Application {
    @Inject
    SharedPreferences sharedPreferences;
    private AppComponent component;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        component = DaggerAppComponent.builder()
                .appModule(new AppModule(this))
                .build();
        component().inject(this);
    }

    public AppComponent component() {
        return component;
    }
}
