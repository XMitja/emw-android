package asia.eyekandi.emw.di;

import javax.inject.Singleton;

import asia.eyekandi.emw.BLEScanner2;
import asia.eyekandi.emw.MainActivity;
import asia.eyekandi.emw.MainActivity2;
import asia.eyekandi.emw.MyApplication;
import asia.eyekandi.emw.WaveFormSurfaceView;
import asia.eyekandi.emw.WaveFormTextureView;
import dagger.Component;

/**
 * Created by mitja on 11/02/16.
 * Copyright AMOK Products ApS
 */
@Singleton
@Component(
        modules = {
                BusModule.class,
                AppModule.class
        }
)
public interface AppComponent {
    void inject(MainActivity activity);
    void inject(MainActivity2 activity);
    void inject(MyApplication application);
    //void inject(BLEScanner scanner);
    void inject(BLEScanner2 scanner);

    void inject(WaveFormTextureView view);
    void inject(WaveFormSurfaceView view);
}
