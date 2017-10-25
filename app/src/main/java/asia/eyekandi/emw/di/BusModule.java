package asia.eyekandi.emw.di;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by mitja on 11/02/16.
 * Copyright(C) AMOK Products ApS
 */

@Module
public class BusModule {


    @Provides
    @Singleton
    EventBus provideBus() {
        return new EventBus();
    }
}
