package asia.eyekandi.emw.di;

import android.bluetooth.BluetoothAdapter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import javax.inject.Singleton;

import asia.eyekandi.emw.BLEScanner2;
import asia.eyekandi.emw.MyApplication;
import dagger.Module;
import dagger.Provides;

/**
 * Created by mitja on 11/02/16.
 * Copyright(C) AMOK Products ApS
 */

@Module
public class AppModule {

    MyApplication mApplication;

    public AppModule(MyApplication application) {
        mApplication = application;
    }

    @Provides
    @Singleton
    MyApplication providesApplication() {
        return mApplication;
    }

    @Provides
    @Singleton
        // Application reference must come from AppModule.class
    SharedPreferences providesSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mApplication);
    }

    @Provides
    @Singleton
    BluetoothAdapter providesBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    @Provides
    @Singleton
    BLEScanner2 providesBLEScanner2() {
        return new BLEScanner2(mApplication);
    }
}
