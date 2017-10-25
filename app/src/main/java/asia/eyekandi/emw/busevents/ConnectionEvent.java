package asia.eyekandi.emw.busevents;

/**
 * Created by mitja on 26/02/16.
 * Copyright AMOK Products ApS
 */
public class ConnectionEvent {
    public final int event;
    public static final int EVENT_CONNECTED = 1;
    public static final int EVENT_CONNECTING = 2;
    public static final int EVENT_SEARCHING = 3;

    public ConnectionEvent(int event) {
        this.event = event;
    }
}
