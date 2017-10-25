package asia.eyekandi.emw.di;

import android.os.Handler;
import android.os.Looper;

import com.squareup.otto.Bus;

/**
 * Created by mitja on 13/02/16.
 * Copyright(C) AMOK Products ApS
 */
public class EventBus extends Bus {

    private static final Handler mainThread = new Handler(Looper.getMainLooper());

    public EventBus() {

    }

    public void postOnMain(final Object event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            this.post(event);
        } else {
            mainThread.post(new Runnable() {
                @Override
                public void run() {
                    EventBus.this.post(event);

                }
            });
        }
    }
}
